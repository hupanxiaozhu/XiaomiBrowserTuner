package com.hupan.hookbrowser.features

import android.webkit.WebResourceResponse
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.HookedCall
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.Xp
import com.hupan.hookbrowser.adblock.AdElementCss
import com.hupan.hookbrowser.adblock.AdRuleChannel
import com.hupan.hookbrowser.adblock.AdRuleCodec
import com.hupan.hookbrowser.adblock.AdRuleEngine
import com.hupan.hookbrowser.adblock.AdRuleParser
import com.hupan.hookbrowser.adblock.HostAdRules
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * 自定义拦截规则 —— 把用户导入的过滤规则真正挂到宿主的网络请求上。
 *
 * ## 为什么落在 `shouldInterceptRequest`
 *
 * 小米浏览器 20.27 的 WebView 是 **hyper 内核**（`hyper.webkit.WebView` +
 * `hyper.webkit.WebViewClient`），不是 `android.webkit.*`。请求真正经过 Java 层的**唯一**
 * 汇聚点就是 `WebViewClient#shouldInterceptRequest` —— 宿主自己也在用它（`Tab$MainWebViewClient`
 * 用它做预加载缓存，反汇编确认过：命中缓存就自造 `WebResourceResponse` 返回）。
 *
 * 返回非 null 的响应 = 该请求不再走网络；返回 null = 交给宿主/内核照常加载。
 * 所以我们命中规则时**不阻断宿主逻辑，直接给它一个空响应**，其余一律不插手。
 *
 * ## 第二条通道：元素隐藏（1.9.0 起）
 *
 * 请求拦截只能拦「资源的 URL」。搜索结果页的**推广位是页面 DOM 里的节点**，URL 全是
 * 自家路径（百度搜索页的广告条目就是 `www.baidu.com/...`），请求维度再怎么加规则也拦不到 ——
 * 真机表现就是「规则编译了几千条，日志里一次拦截都没有，广告照旧满屏」。
 *
 * 所以 `##` 规则走另一条路：编译成一段 CSS（见 [AdElementCss]），在 `onPageFinished`
 * 里通过 `evaluateJavascript` 注入 `<style>`。CSS 是声明式的，插一次就长期生效，
 * 首屏渲染和后续 SPA 动态插入的广告节点都会自动被隐藏。
 *
 * ## 第三条通道：宿主自带的规则库（1.9.1 起）
 *
 * 小米浏览器**自己也有一套广告规则**（`files/data/adblock/` 下的 5 个 json，喂给 chromium 的 native
 * `BlockingRuleMatcher`，真机日志 tag `<AdBlock>`）。它语法比本模块完整（`$csp` / `$third-party`
 * 都认），但只管 URL 维度，且开关在服务端配置里。
 *
 * 所以这里把它**只读**读出来（[HostAdRules]），过一遍本模块的解析器后并进同一个引擎：
 * 用户不导入任何规则时模块也不空转，`##` 条目还能走上面的 CSS 通道兑现。
 *
 * ## 怎么保证覆盖到所有 WebView
 *
 * `shouldInterceptRequest` 是**子类 override** 的方法 —— hook 基类拦不到不调 super 的子类，
 * 所以只 hook 一个类名肯定漏。这里三条路一起走：
 *
 * 1. hook `miui.browser.webview.BrowserWebView#setWebViewClient` 与
 *    `hyper.webkit.WebView#setWebViewClient`：**每设置一次 client，就把那个实例的类挂上**。
 *    这是覆盖面的主力（新闻详情、搜索建议、自定义 Tab 里的 WebView 全都会经过 setter）。
 * 2. 直接按类名挂已知的主 client `com.android.browser.Tab$MainWebViewClient`：它可能在模块
 *    注入之前就被设置好了，setter 那条路会错过。
 * 3. 兜底挂 `hyper.webkit.WebViewClient` 基类的 `shouldInterceptRequest`：只 override 了
 *    String 重载的子类会先把请求交给基类，这条路能接住。
 *
 * 已挂载的类名进 [hookedClasses] 去重 —— 同一个 WebViewClient 类被多次 hook 会让同一个请求
 * 走多遍判定，虽然结果一样但白烧 CPU。
 *
 * ## 三套内核 = 三套同名类型（1.8.1 踩过的坑）
 *
 * 宿主里同时装着 hyper（`hyper.webkit.*`）、MIUI（`com.miui.webkit.*`）和百度 SDK
 * （`com.baidu.searchbox.sailor.*`）的 WebView 体系，**同名类的类型互不相干**：
 * 三家各有一个 `WebResourceResponse`，也各有一个 `WebResourceRequest`。所以：
 * - 参数一律**反射**取（写 `is android.webkit.WebResourceRequest` 会让 miui 体系的请求全漏掉）；
 * - 返回值按 `p.method.returnType` **现造**，造不出就放行（写死 android 的类型 → 宿主闪退）。
 *
 * ## 判定跑在网络线程上
 *
 * `shouldInterceptRequest` 不在主线程，而且每个资源请求都会调一次。所以：
 * - 引擎是**编译好的不可变对象**，[engine] 换引用即可，不在回调里做任何解析；
 * - 规则库由独立线程每 [WATCH_INTERVAL_MS] 轮询一次重编译（见 [startWatcher]），
 *   回调里只读一个 `@Volatile` 引用；
 * - 字符串重载拿不到 `isForMainFrame`，一律按**子资源**处理（返回空 body）——
 *   宁可某个冷门 WebView 的整页导航被拦成空白，也不误判成整页替换。
 */
internal object CustomAdBlockFeature : Feature(Config.AD_CUSTOM_RULES) {

    /** 宿主的主 WebView（hyper 内核的封装类） */
    private const val CLS_BROWSER_WEBVIEW = "miui.browser.webview.BrowserWebView"

    /** 内核 WebView 基类：所有 setWebViewClient 调用都要经过它（或它的子类） */
    private const val CLS_HYPER_WEBVIEW = "hyper.webkit.WebView"

    /** 内核 WebViewClient 基类（兜底挂它的 shouldInterceptRequest） */
    private const val CLS_HYPER_CLIENT = "hyper.webkit.WebViewClient"

    /** 主浏览页的 WebViewClient（反汇编确认它实现了 shouldInterceptRequest） */
    private const val CLS_MAIN_CLIENT = "com.android.browser.Tab\$MainWebViewClient"

    /** 规则库轮询间隔：宿主改完设置在几秒内生效，又不会一直读文件 */
    private const val WATCH_INTERVAL_MS = 8000L

    /** 前多少条命中打日志（之后按 [LOG_EVERY] 抽样），避免刷爆 LSPosed 日志 */
    private const val LOG_FIRST = 30
    private const val LOG_EVERY = 200

    /** 每多少次请求汇总一行【采样】诊断日志 */
    private const val LOG_CALL_EVERY = 200

    private val EMPTY_BODY = ByteArray(0)

    /** 当前生效的规则引擎；未加载 / 开关关闭时为 null，回调直接 no-op */
    @Volatile
    private var engine: AdRuleEngine? = null

    /** 当前生效的元素隐藏注入脚本（空串 = 没有元素隐藏规则，注入回调直接 return） */
    @Volatile
    private var cssJs: String = ""

    /**
     * 上次编译用的**输入指纹**：用户规则库 JSON + 宿主内置规则的条数。
     *
     * 不能再只比 JSON —— 1.9.1 起引擎里还混着宿主自己的规则库（[HostAdRules]，60 秒重扫一次），
     * 只比 JSON 会让宿主规则的变化永远触发不了重编译。
     */
    @Volatile
    private var loadedStamp: String? = null

    @Volatile
    private var watchStarted = false

    /** 已挂载的 WebViewClient 类名（去重，见类注释） */
    private val hookedClasses = HashSet<String>()

    /** 已挂载 `onPageFinished` 的类名（去重；它沿继承链找，命中的可能是父类） */
    private val hookedFinished = HashSet<String>()

    /** `evaluateJavascript` 的查找缓存（按 WebView 实现类；null 表示这个类没有该方法） */
    private val evalMethods = HashMap<Class<*>, Method?>()

    /** 累计命中数（只用于日志抽样） */
    private val blocked = AtomicInteger()

    /** 累计「命中但目标类型造不出响应」的次数（只用于日志抽样） */
    private val giveUps = AtomicInteger()

    /** `shouldInterceptRequest` 累计调用次数（诊断用：判断 hook 到底有没有被调到） */
    private val calls = AtomicInteger()

    /** 已打过首次调用日志的类名 */
    private val sampledClasses = HashSet<String>()

    override fun install(cl: ClassLoader) {
        hookClientSetters(cl)
        hookKnownClient(cl)
        hookBaseClient(cl)
        startWatcher()
    }

    // ===================== hook 安装 =====================

    /** ① 每次设置 WebViewClient 都把那个实例的类挂上 —— 覆盖面主力 */
    private fun hookClientSetters(cl: ClassLoader) {
        for (name in listOf(CLS_BROWSER_WEBVIEW, CLS_HYPER_WEBVIEW)) {
            Hooks.hookAfter(cl, name, "setWebViewClient") { p ->
                if (!on()) return@hookAfter
                val client = p.args.getOrNull(0) ?: return@hookAfter
                hookClient(client.javaClass)
            }
        }
    }

    /** ② 主浏览页的 client 可能在模块注入前就设好了，按类名补一刀 */
    private fun hookKnownClient(cl: ClassLoader) {
        val cls = Xp.findClass(CLS_MAIN_CLIENT, cl)
        if (cls == null) {
            XLog.v("未找到 $CLS_MAIN_CLIENT，改由 setWebViewClient 动态捕获")
            return
        }
        hookClient(cls)
    }

    /** ③ 基类兜底：只 override 了 String 重载的子类会把请求交给基类 */
    private fun hookBaseClient(cl: ClassLoader) {
        Hooks.hook(cl, CLS_HYPER_CLIENT, "shouldInterceptRequest") { p -> intercept(p) }
    }

    /** 给一个 WebViewClient 类挂上两个 shouldInterceptRequest 重载；同类只挂一次 */
    private fun hookClient(cls: Class<*>) {
        synchronized(hookedClasses) {
            if (!hookedClasses.add(cls.name)) return
        }
        val n = Hooks.hookAll(cls, "shouldInterceptRequest") { p -> intercept(p) }
        if (n > 0) {
            XLog.v("自定义规则：已挂 ${cls.name}#shouldInterceptRequest（$n 个重载），累计 ${hookedClasses.size} 个 client 类")
        }
        hookPageFinished(cls)
    }

    /**
     * 给同一个 client 挂 `onPageFinished`，在页面加载完成后注入元素隐藏 CSS。
     *
     * ## 为什么沿继承链往上找
     *
     * `XposedBridge.hookAllMethods` 只挂**该类自己声明**的方法。`onPageFinished` 绝大多数
     * client 并不重写（重写它没意义），所以直接在子类上找会返回 0 条；必须沿 `superclass`
     * 往上走，直到某一层真的声明了它。这一层挂上后，**所有子类的实例**都会走到 ——
     * 比逐个类挂更省，也顺带覆盖了后面才出现的匿名 client。
     *
     * 挂到 `android.webkit.WebViewClient` 也无妨（我们只在本进程内 hook），
     * 那是最好的兜底层。
     */
    private fun hookPageFinished(cls: Class<*>) {
        var cur: Class<*> = cls
        while (true) {
            val first = synchronized(hookedFinished) { hookedFinished.add(cur.name) }
            if (!first) return
            val n = Hooks.hookAllAfter(cur, "onPageFinished") { p ->
                val view = p.args.getOrNull(0)
                if (view != null) injectCss(view)
            }
            if (n > 0) return
            cur = cur.superclass ?: return
        }
    }

    // ===================== 元素隐藏注入 =====================

    /**
     * 把元素隐藏 CSS 注入页面。取不到 WebView / 没规则 / 没有 `evaluateJavascript` 都静默跳过。
     *
     * `onPageFinished` 在主线程回调，`evaluateJavascript` 也要求主线程 —— 时机正好。
     *
     * ## 为什么反射找方法而不是强转 `android.webkit.WebView`
     *
     * 宿主里 hyper / miui / 百度 SDK 三套 WebView **互不相干**（`hyper.webkit.WebView` 有
     * 212 个自己的方法，是内核自带的独立实现，不是 `android.webkit.WebView` 的子类）。
     * 写死强转会一个都注入不进去；按「类里有没有 `evaluateJavascript(String, ?)`」来找，
     * 三套内核通吃。方法的第二个参数是各自的 `ValueCallback` 接口，传 null 即可 ——
     * 不关心回调，也不引入任何一套内核的类型。
     */
    private fun injectCss(view: Any) {
        val js = cssJs
        if (js.isEmpty()) return
        val m = evalMethod(view.javaClass) ?: return
        // 第二个参数是各内核自己的 ValueCallback 接口，我们不关心回调，传 null 即可。
        // 显式写成 `null as Any?`：Kotlin 对 vararg 位的裸 null 会有歧义告警。
        runCatching { m.invoke(view, js, null as Any?) }
            .onFailure { XLog.v("【元素隐藏】注入失败：${it.javaClass.simpleName}") }
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

    // ===================== 拦截判定 =====================

    private fun intercept(p: HookedCall) {
        if (!on()) return
        noteCall(p)
        val e = engine ?: return
        val arg = p.args.getOrNull(1) ?: return

        val url: String
        val isMain: Boolean
        if (arg is String) {
            // 旧重载：拿不到 isForMainFrame，按子资源处理（见类注释）
            url = arg
            isMain = false
        } else {
            // ⚠ 这里**不能**写 `arg is android.webkit.WebResourceRequest`。
            // 宿主同时存在 hyper / miui / 百度 SDK 三套内核，各自的 WebResourceRequest 互不相干，
            // 只有 hyper 那套（恰好）实现了 android 的接口 —— 写死类型判断会让 miui 体系的请求
            // 全部落到 else 里被 return 掉，真机表现就是「规则导入了、日志说编译了，但什么都拦不住」。
            // 只认「有 getUrl()」这个事实，拿不到就放行。
            url = requestUrl(arg) ?: return
            isMain = runCatching {
                arg.javaClass.getMethod("isForMainFrame").invoke(arg) as? Boolean ?: false
            }.getOrDefault(false)
        }

        if (url.length < 10 || url.length > 4096) return
        if (!url.startsWith("http")) return

        val rule = e.match(url) ?: return
        val resp = buildResponse(p, url, rule, isMain) ?: return
        p.result = resp
        log(rule, url)
    }

    /** 反射取 `WebResourceRequest#getUrl()`；三套内核的返回类型（Uri 族）各不相同，统一 `toString()` */
    private fun requestUrl(arg: Any): String? = runCatching {
        arg.javaClass.getMethod("getUrl").invoke(arg)?.toString()
    }.getOrNull()

    /**
     * 诊断计数：每个 client 类**首次**进来打一行（带方法签名与参数类型），之后每
     * [LOG_CALL_EVERY] 次汇总一行。
     *
     * 加它的原因很具体：1.8.1 真机上「规则 6023 条全部编译生效、日志里却只有 9 次拦截」，
     * 光看【拦截】日志分不清是 ①hook 压根没被调到 ②URL 没取到 ③取到了但规则没命中。
     * 这一行把三种情况直接分开 —— 有【采样】没【拦截】= 规则不匹配；连【采样】都没有 = 通道不通。
     */
    private fun noteCall(p: HookedCall) {
        val n = calls.incrementAndGet()
        val m = p.method
        val name = m.declaringClass.name
        val first = synchronized(sampledClasses) { sampledClasses.add(name) }
        if (first) {
            val sig = runCatching {
                "ret=${m.returnType.simpleName} args=(${m.parameterTypes.joinToString(", ") { it.simpleName }})"
            }.getOrDefault("ret=? args=?")
            XLog.v("【采样】$name 有请求经过：$sig")
        } else if (n % LOG_CALL_EVERY == 0) {
            XLog.v("【采样】shouldInterceptRequest 累计 $n 次，命中 ${blocked.get()} 次")
        }
    }

    /**
     * 命中后的响应：整页给提示页（不然是一片白），子资源给空 body。
     *
     * ## 必须按「目标方法的声明返回类型」现造 —— 否则宿主直接闪退
     *
     * 宿主进程里同时有**三套互不相干的 `WebResourceResponse`**：
     * `android.webkit` / `com.miui.webkit` / `com.baidu.searchbox.sailor.variant`。
     * 给 hook 设一个类型不匹配的结果，LSPosed 会在 `proceed` 里抛
     * `ClassCastException: Return value's type from hook callback does not match the hooked method`
     * —— 这个异常抛在回调**之外**，`SafeHook` 的 try 接不住，宿主当场闪退。
     * 真机日志实测：命中规则后 21ms 崩溃，栈顶是百度 SDK 的 `BdWebViewClientProxy`。
     *
     * 所以顺序是：① android 原生对象能用就用；② 按目标类型反射造（三家的
     * `(String mime, String encoding, InputStream data)` 构造器签名一致）；
     * ③ **造不出来返回 null 放行** —— 宁可漏一条广告，不可崩一次宿主。
     */
    private fun buildResponse(
        p: HookedCall,
        url: String,
        rule: String,
        isMain: Boolean
    ): Any? {
        val rt = p.method.returnType
        val mime = if (isMain) "text/html" else "text/plain"
        val body = if (isMain) noticeHtml(url, rule).toByteArray(Charsets.UTF_8) else EMPTY_BODY

        WebResourceResponse(mime, "utf-8", ByteArrayInputStream(body)).let {
            if (rt.isInstance(it)) return it
        }

        runCatching {
            rt.getConstructor(String::class.java, String::class.java, InputStream::class.java)
                .newInstance(mime, "utf-8", ByteArrayInputStream(body))
        }.getOrNull()?.let { if (rt.isInstance(it)) return it }

        runCatching {
            rt.getConstructor(
                String::class.java, String::class.java, InputStream::class.java, Map::class.java
            ).newInstance(mime, "utf-8", ByteArrayInputStream(body), HashMap<String, String>())
        }.getOrNull()?.let { if (rt.isInstance(it)) return it }

        // 目标类型造不出来（未知内核）：记一次日志，放行。绝不让类型不匹配的对象进 LSPosed。
        val n = giveUps.incrementAndGet()
        if (n <= 3) XLog.v("【拦截】放弃：${rt.name} 无法构造（规则=$rule）")
        return null
    }

    /**
     * 整页被拦时的提示页。
     *
     * 刻意做成可解释页面而不是空白：规则集（尤其从网上下载的）很容易过度匹配，
     * 白屏会让用户以为浏览器坏了；把**命中的规则原文**摆在页面上，用户能立刻去停用那条规则集。
     */
    private fun noticeHtml(url: String, rule: String): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>已拦截</title></head>")
        append("<body style=\"margin:0;padding:28px;font-family:sans-serif;background:#fafafa;color:#222\">")
        append("<h3 style=\"margin:0 0 14px\">该网页被自定义规则拦截</h3>")
        append("<p style=\"color:#666;font-size:13px;word-break:break-all;margin:0 0 8px\">")
        append(esc(url)).append("</p>")
        append("<p style=\"color:#999;font-size:12px;margin:0 0 22px\">命中规则：")
        append(esc(rule)).append("</p>")
        append("<p style=\"color:#999;font-size:12px;line-height:1.7\">")
        append("来自「小米浏览器净化」模块导入的自定义拦截规则。<br>")
        append("要放行请打开模块设置 →「自定义拦截规则」，停用或删除对应的规则集。")
        append("</p></body></html>")
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun log(rule: String, url: String) {
        val n = blocked.incrementAndGet()
        if (n <= LOG_FIRST || n % LOG_EVERY == 0) {
            XLog.v("【拦截】#$n 规则=$rule URL=${url.take(150)}")
        }
    }

    // ===================== 规则库加载 =====================

    /**
     * 独立线程轮询规则库。
     *
     * 刻意不放在 hook 回调里读：`AdRuleChannel` 每次真读都要扫目录 + 读 XML，
     * 放在网络线程的热路径上会明显拖慢页面。这里每 [WATCH_INTERVAL_MS] 一次，
     * 内容变了才重编译，回调只读 [engine] 引用。
     */
    private fun startWatcher() {
        if (watchStarted) return
        watchStarted = true
        val t = Thread {
            while (true) {
                runCatching { reload() }.onFailure { XLog.v("规则库刷新异常：${it.javaClass.simpleName}") }
                runCatching { Thread.sleep(WATCH_INTERVAL_MS) }
            }
        }
        t.isDaemon = true
        t.name = "hb-adrules"
        t.priority = Thread.MIN_PRIORITY
        t.start()
    }

    private fun reload() {
        if (!on()) {
            if (engine != null) {
                engine = null
                cssJs = ""
                loadedStamp = ""
                XLog.i("自定义拦截规则已关闭（总开关或功能开关其中之一）")
            }
            return
        }

        val json = AdRuleChannel.read()
        // 宿主内置规则（60 秒缓存，不是每次都读盘；只读，绝不写回那个目录）
        val host = HostAdRules.load()

        // 指纹用 length + hashCode 而不是整串：这个字符串最大 4MB，没必要常驻
        val stamp = json.length.toString() + ':' + json.hashCode() + '|' + host.size
        if (stamp == loadedStamp) return
        loadedStamp = stamp

        val sets = AdRuleCodec.decode(json)
        val userRules = AdRuleCodec.enabledRules(sets)
        val userElements = AdRuleCodec.enabledElements(sets)

        // 带 `$` 选项的规则**不接**：宿主那套 native 引擎本来就认（$csp / $third-party /
        // $document / $generichide），而本模块的解析器只会把选项**剥掉** —— 剥掉后语义是
        // **变宽**的：`@@||x^$generichide`（原意只是"不隐藏通用元素"）会变成
        // 「放行 x 的所有请求」，反过来把用户自己导入的规则全压掉。宁漏勿误杀，
        // 这部分交给 native 引擎。
        val hostPlain = host.filterNot { it.indexOf('$') >= 0 }

        // 剩下的走一遍本模块的解析器：受同一套护栏约束（长度 / ReDoS 形状 / 全局选择器的
        // 正文塌陷闸），并自动完成 `##` 与 URL 两路分流。
        val hostParsed =
            if (hostPlain.isEmpty()) null else AdRuleParser.parse(hostPlain.joinToString("\n"))
        val hostRules = hostParsed?.rules ?: emptyList()
        val hostElements = hostParsed?.elementRules ?: emptyList()

        val rules = userRules + hostRules
        val compiled = if (rules.isEmpty()) AdRuleEngine.EMPTY else AdRuleEngine.compile(rules)
        engine = compiled

        // 元素隐藏规则 → 注入脚本。与 URL 引擎分开编译：两者的生效通道完全不同
        val elements = userElements + hostElements
        val css = if (elements.isEmpty()) AdElementCss.EMPTY else AdElementCss.compile(elements)
        cssJs = css.js

        if (sets.isEmpty() && host.isEmpty()) {
            XLog.i("【规则】规则库为空，未导入或宿主读不到：${AdRuleChannel.describeOnce()}")
        } else {
            XLog.i(
                "【规则】用户 ${sets.size} 组（启用 ${sets.count { it.enabled }} 组）：" +
                    "URL ${userRules.size} + 隐藏 ${userElements.size} 条；" +
                    "宿主内置 ${host.size} 条（${host.size - hostPlain.size} 条带 \$ 选项交给 native）" +
                    " → 分流 URL ${hostRules.size} + 隐藏 ${hostElements.size} 条；" +
                    "合计生效 URL ${compiled.ruleCount} 条（超限丢弃 ${compiled.droppedCount}）、" +
                    "隐藏 ${css.total} 条（全局 ${css.globalCount} / 限定 ${css.scopedCount}，" +
                    "超限丢弃 ${css.dropped}）"
            )
        }
    }
}
