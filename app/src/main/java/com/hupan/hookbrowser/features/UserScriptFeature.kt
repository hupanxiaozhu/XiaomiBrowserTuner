/*
 * 用户脚本 —— 在匹配的网页里执行用户导入的 JavaScript（1.14.0 新增）。
 *
 * ## 与自定义拦截规则的关系
 *
 * 规则管「拦与藏」（URL 拦截 + CSS 隐藏），脚本管「加功能」（自动展开、去跳转中间页
 * 这类 CSS 做不到的事）。两者共用同一条注入链路（[PageInjection]：onPageFinished →
 * evaluateJavascript），本功能只做三件事：读脚本库、按 URL 匹配、逐脚本注入。
 *
 * ## 注入语义（与油猴的差别，导入时已向用户说清）
 *
 * - 时机只有 document-end：onPageFinished 才注入。@run-at document-start 的脚本
 *   **降级**为加载完成后执行（不做 HTML 流改写，理由见可行性分析文档）；
 * - GM_* API 不支持：@grant 声明过的脚本照常注入（很多脚本有降级路径），
 *   但 GM_ 调用会抛错 —— 导入确认里已提示；
 * - 只注入 http/https 页面：错误页（nativechannel://）等宿主内部页同样会触发
 *   onPageFinished，必须先按 scheme 过滤，否则脚本会注入进宿主 UI。
 *
 * ## 脚本库加载
 *
 * 与规则库同一个节奏：独立线程每 [WATCH_INTERVAL_MS] 读一次 [UserScriptChannel]，
 * 指纹变了才重编译匹配表（[UserScriptMatcher.compile]），回调里只读 [compiled]
 * 这个 @Volatile 引用，不在主线程做任何解析。
 */
package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.scripts.UserScript
import com.hupan.hookbrowser.scripts.UserScriptChannel
import com.hupan.hookbrowser.scripts.UserScriptMatcher
import com.hupan.hookbrowser.webpage.PageConsumer
import com.hupan.hookbrowser.webpage.PageInjection

internal object UserScriptFeature : Feature(Config.SCRIPT_USERSCRIPTS) {

    /** 脚本库轮询间隔（与规则库一致） */
    private const val WATCH_INTERVAL_MS = 8000L

    /** 已编译的匹配表（仅启用中的脚本）；空表 = 回调直接 return */
    @Volatile
    private var compiled: List<UserScriptMatcher.Compiled> = emptyList()

    /** 上次编译用的指纹（UserScriptChannel 给的原始串指纹） */
    @Volatile
    private var loadedStamp: String? = null

    @Volatile
    private var watchStarted = false

    override fun install(cl: ClassLoader) {
        PageInjection.register(object : PageConsumer {
            override fun active(): Boolean = on()
            override fun onPageFinished(view: Any, url: String?) = inject(view, url)
        })
        PageInjection.install(cl)
        startWatcher()
    }

    /** 页面加载完成：过滤内部页 → 逐脚本匹配 → 命中即注入 */
    private fun inject(view: Any, url: String?) {
        val u = url ?: return
        if (!u.startsWith("http://") && !u.startsWith("https://")) return
        val list = compiled
        if (list.isEmpty()) return
        for (c in list) {
            if (!UserScriptMatcher.matches(c, u)) continue
            val ok = PageInjection.injectJs(view, wrap(c.script))
            if (ok) {
                XLog.v("【脚本】已注入「${c.script.name}」→ ${u.take(120)}")
            } else {
                XLog.v("【脚本】注入失败「${c.script.name}」（${view.javaClass.simpleName} 无 evaluateJavascript）")
            }
        }
    }

    /**
     * 包一层 try/catch + IIFE：一个脚本抛错不影响同页其它脚本，也不打断页面自身逻辑。
     * 不把脚本名嵌进 JS 字符串（名字里的引号会截断字面量），出错的排查信息走 console。
     */
    private fun wrap(s: UserScript): String = buildString(s.code.length + 128) {
        append("(function(){\"use strict\";try{")
        append(s.code)
        append("\n}catch(e){console.error(\"[HookBrowser 用户脚本]\",e);}})();")
    }

    // ===================== 脚本库加载（与 CustomAdBlockFeature 同节奏） =====================

    private fun startWatcher() {
        if (watchStarted) return
        watchStarted = true
        val t = Thread {
            while (true) {
                runCatching { reload() }.onFailure {
                    XLog.v("脚本库刷新异常：${it.javaClass.simpleName}")
                }
                runCatching { Thread.sleep(WATCH_INTERVAL_MS) }
            }
        }
        t.isDaemon = true
        t.name = "hb-userscripts"
        t.priority = Thread.MIN_PRIORITY
        t.start()
    }

    private fun reload() {
        if (!on()) {
            if (compiled.isNotEmpty() || loadedStamp != null) {
                compiled = emptyList()
                loadedStamp = null
                XLog.i("用户脚本已关闭（总开关或功能开关其中之一）")
            }
            return
        }

        val (stamp, scripts) = UserScriptChannel.read()
        if (stamp == loadedStamp) return
        loadedStamp = stamp

        val enabled = scripts.filter { it.enabled }
        compiled = enabled.map { UserScriptMatcher.compile(it) }

        if (scripts.isEmpty()) {
            XLog.i("【脚本】脚本库为空：${UserScriptChannel.describeOnce()}")
        } else {
            XLog.i("【脚本】脚本库重载：共 ${scripts.size} 个，启用 ${enabled.size} 个")
        }
    }
}
