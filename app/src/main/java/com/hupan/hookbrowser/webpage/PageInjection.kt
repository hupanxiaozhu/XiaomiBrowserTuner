/*
 * 「页面加载完成 → 注入 JS」的共享通道（1.14.0 从 CustomAdBlockFeature 抽出）。
 *
 * 抽出来的原因：自定义拦截规则的元素隐藏 CSS 与用户脚本（UserScriptFeature）走的是
 * **同一条**注入链路（WebViewClient#onPageFinished → evaluateJavascript）。原来这条链路
 * 的「client 捕获 + 挂载 + 反射调用」全部长在 CustomAdBlockFeature 私有方法里，且
 * setWebViewClient 的捕获回调以它自己的开关为闸 —— 用户只开脚本、不开自定义规则时，
 * 通道根本不会被挂上。现在通道独立，谁注册谁消费，开关各自判断。
 *
 * 覆盖面的三条路（与原实现一致）：
 * 1. hook `miui.browser.webview.BrowserWebView` 与 `hyper.webkit.WebView` 的
 *    setWebViewClient：每设置一次 client 就捕获它的类（主力，新闻详情 / 搜索建议 /
 *    自定义 Tab 都会经过 setter）；
 * 2. 直接挂已知主 client `com.android.browser.Tab$MainWebViewClient`
 *    （可能在模块注入前就设好了，setter 那条路会错过）；
 * 3. onPageFinished 沿继承链往上找声明层挂载 —— 绝大多数 client 不重写它，
 *    直接挂子类是 0 条，挂到声明层后所有子类实例都走到。
 *
 * evaluateJavascript 按「类里有没有 evaluateJavascript(String, ?)」反射查找并缓存：
 * hyper / miui / 百度三套内核的 WebView 互不相干，写死强转一个都注入不进去。
 * 第二个参数是各内核自己的 ValueCallback 接口，传 null 即可（不关心回调）。
 */
package com.hupan.hookbrowser.webpage

import com.hupan.hookbrowser.HookedCall
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.Xp
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

/** 通道的消费者（实现方：CustomAdBlockFeature / UserScriptFeature） */
internal interface PageConsumer {

    /** 该功能的开关是否开（捕获与回调前都问一次，关掉即静默） */
    fun active(): Boolean

    /** 每捕获到一个新的 WebViewClient 类（消费者拿去挂自己的东西，如 shouldInterceptRequest） */
    fun onClientClass(cls: Class<*>) {}

    /**
     * 页面加载完成（主线程）。
     *
     * [url] 取自 onPageFinished 的第二个参数；个别内核回调形态不同拿不到时为 null，
     * 需要 URL 的消费者（脚本匹配）直接跳过即可。
     */
    fun onPageFinished(view: Any, url: String?) {}
}

internal object PageInjection {

    /** 宿主的主 WebView（hyper 内核的封装类） */
    private const val CLS_BROWSER_WEBVIEW = "miui.browser.webview.BrowserWebView"

    /** 内核 WebView 基类：所有 setWebViewClient 调用都要经过它（或它的子类） */
    private const val CLS_HYPER_WEBVIEW = "hyper.webkit.WebView"

    /** 主浏览页的 WebViewClient（反汇编确认；可能在模块注入前就设好） */
    private const val CLS_MAIN_CLIENT = "com.android.browser.Tab\$MainWebViewClient"

    private val consumers = CopyOnWriteArrayList<PageConsumer>()

    /** 已捕获并通知过的 WebViewClient 类名（去重） */
    private val hookedClients = HashSet<String>()

    /** 已挂载 `onPageFinished` 的类名（去重；沿继承链找，命中的可能是父类） */
    private val hookedFinished = HashSet<String>()

    /** `evaluateJavascript` 的查找缓存（按 WebView 实现类；null 表示这个类没有该方法） */
    private val evalMethods = HashMap<Class<*>, Method?>()

    @Volatile
    private var installed = false

    fun register(c: PageConsumer) {
        consumers.addIfAbsent(c)
    }

    /** 挂捕获点。幂等：多个 feature 各调一次也只装一遍。 */
    fun install(cl: ClassLoader) {
        if (installed) return
        installed = true
        // ① 每次设置 WebViewClient 都把那个实例的类挂上 —— 覆盖面主力
        for (name in listOf(CLS_BROWSER_WEBVIEW, CLS_HYPER_WEBVIEW)) {
            Hooks.hookAfter(cl, name, "setWebViewClient") { p ->
                if (!anyActive()) return@hookAfter
                val client = p.args.getOrNull(0) ?: return@hookAfter
                hookClient(client.javaClass)
            }
        }
        // ② 主浏览页的 client 可能在模块注入前就设好了，按类名补一刀
        val main = Xp.findClass(CLS_MAIN_CLIENT, cl)
        if (main == null) {
            XLog.v("未找到 $CLS_MAIN_CLIENT，改由 setWebViewClient 动态捕获")
        } else {
            hookClient(main)
        }
    }

    private fun anyActive(): Boolean = consumers.any { runCatching { it.active() }.getOrDefault(false) }

    /** 捕获一个 client 类：通知消费者 + 挂 onPageFinished；同类只处理一次 */
    private fun hookClient(cls: Class<*>) {
        synchronized(hookedClients) {
            if (!hookedClients.add(cls.name)) return
        }
        for (c in consumers) {
            runCatching { c.onClientClass(cls) }
                .onFailure { XLog.e("onClientClass 通知失败（${cls.name}）", it) }
        }
        hookPageFinished(cls)
    }

    /**
     * 给 client 挂 `onPageFinished`（沿继承链找声明层，见文件顶注）。
     *
     * 回调在主线程，evaluateJavascript 也要求主线程 —— 时机正好。
     */
    private fun hookPageFinished(cls: Class<*>) {
        var cur: Class<*> = cls
        while (true) {
            val first = synchronized(hookedFinished) { hookedFinished.add(cur.name) }
            if (!first) return
            val n = Hooks.hookAllAfter(cur, "onPageFinished") { p -> dispatch(p) }
            if (n > 0) return
            cur = cur.superclass ?: return
        }
    }

    private fun dispatch(p: HookedCall) {
        val view = p.args.getOrNull(0) ?: return
        val url = p.args.getOrNull(1) as? String
        for (c in consumers) {
            if (!runCatching { c.active() }.getOrDefault(false)) continue
            runCatching { c.onPageFinished(view, url) }
                .onFailure { XLog.e("onPageFinished 回调异常", it) }
        }
    }

    /**
     * 往页面里注入一段 JS。返回是否调出去了（找不到 evaluateJavascript / 调用抛异常都算失败，
     * 日志由调用方按需记 —— 不同消费者想要的文案不同）。
     */
    fun injectJs(view: Any, js: String): Boolean {
        if (js.isEmpty()) return false
        val m = evalMethod(view.javaClass) ?: return false
        // 第二个参数是各内核自己的 ValueCallback 接口，传 null（不关心回调）。
        // 显式 `null as Any?`：Kotlin 对 vararg 位的裸 null 有歧义告警。
        return runCatching { m.invoke(view, js, null as Any?); true }.getOrDefault(false)
    }

    /** 找 `evaluateJavascript(String, ValueCallback)` 并缓存；找不到缓存 null（不重复扫） */
    private fun evalMethod(cls: Class<*>): Method? = synchronized(evalMethods) {
        if (evalMethods.containsKey(cls)) return evalMethods[cls]
        val m = runCatching {
            cls.methods.firstOrNull {
                it.name == "evaluateJavascript" &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == String::class.java
            }?.apply { isAccessible = true }
        }.getOrNull()
        evalMethods[cls] = m
        m
    }
}
