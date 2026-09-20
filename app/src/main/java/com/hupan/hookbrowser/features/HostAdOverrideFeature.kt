package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.HostContext
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.Xp
import com.hupan.hookbrowser.adblock.AdRuleChannel
import com.hupan.hookbrowser.adblock.AdRuleCodec
import com.hupan.hookbrowser.adblock.HostRuleInstaller
import java.util.concurrent.Executors

/**
 * 用自定义规则**接管宿主内核的广告过滤**（1.9.2 起）。
 *
 * ## 这个功能解决什么
 *
 * 前面几个版本是「模块自己拦」：`shouldInterceptRequest` 拦 URL、注入 CSS 藏 DOM 节点。
 * 但宿主**自己也有一套 URL 规则引擎**（chromium 的 native `BlockingRuleMatcher`），
 * 它认的语法比本模块完整（`$csp` / `$third-party` / `$document` / `$generichide`），
 * 而且跑在 native 层、零 Java 开销。它的规则存在
 * `files/data/adblock/` 下的 5 个 json，由服务端下发、用户改不了。
 *
 * 于是问题变成：**怎么让宿主的引擎跑我的规则，而不是小米下发的那套。**
 *
 * ## 为什么只能改文件（反汇编结论）
 *
 * 宿主把规则交给 native 的路径只有一条，`com.android.webview.chromium.ad.AdBlockHelper$Updator`：
 *
 * ```
 * updateRules(context) → 线程 → updateRuleList("black") / ("white")
 *                              → 把 content://com.miui.browser.adblock/… 的内容写到
 *                                files/data/adblock/miui_blacklist.json / miui_whitelist.json
 *                              → MiuiStatics.getInstance().notifyAdBlockUpdateConfig()
 * ```
 *
 * native 直接按**文件路径**读，Java 侧没有任何「注入规则」的接口可 hook。
 * 所以本功能的做法是：**hook 宿主的写入通道 + 覆盖它的规则文件**（见 [HostRuleInstaller]），
 * 再调 `notifyAdBlockUpdateConfig()` 让 native 立即重载 —— 不用重启浏览器。
 *
 * ## 三条防线保证「写进去的规则不会被宿主覆盖回去」
 *
 * 1. **Application 就绪即写一次**（越早越好，赶在 native 首次读盘之前）
 * 2. **hook 宿主所有会重写规则文件的路径**：`AdBlockHelper$Updator#updateRuleList`
 *    （服务端下发）与 `AdBlockDataUpdator#writeJSONFile` / `updateAdBlackist`（OTA 差分更新）
 * 3. **守护线程每 [GUARD_INTERVAL_MS] 校验一次指纹**：内容对不上就重写。
 *    这一条是兜底 —— 宿主还有别的写入点没被枚举到也不会漏。
 *
 * ## 与 [CustomAdBlockFeature] 的关系
 *
 * 两者**互补而非重复**：
 * - 本功能把**URL 维度**规则交给 native（性能好、语法全）；
 * - `##` 元素隐藏规则**不往这儿写** —— native 不认，写进去只会刷 `<AdBlock> Parse error`，
 *   那部分继续由 [CustomAdBlockFeature] 的 CSS 通道负责；
 * - 模块自己的 URL 引擎照常跑，两边命中同一个请求时结果一致（都是拦掉），不会冲突。
 *
 * ## 默认关闭
 *
 * 这是**破坏性**操作：它会清空宿主自己下发的规则（用户要的就是"不用小米那套"）。
 * 所以默认 false，且首次接管前会把 5 个规则文件整体备份，关掉开关自动还原。
 */
internal object HostAdOverrideFeature : Feature(Config.AD_HOST_OVERRIDE) {

    /** 宿主的规则写入通道（服务端下发 / OTA 更新各一个） */
    private const val CLS_HELPER_UPDATOR = "com.android.webview.chromium.ad.AdBlockHelper\$Updator"
    private const val CLS_DATA_UPDATOR = "com.android.browser.util.AdBlockDataUpdator"

    /** 通知 native 重载规则配置的入口（反汇编 `MiuiStatics$Natives` 确认存在） */
    private const val CLS_MIUI_STATICS = "com.android.webview.chromium.MiuiStatics"

    /** 守护间隔：比规则库轮询（8 秒）再慢一点没意义，保持一致 */
    private const val GUARD_INTERVAL_MS = 8000L

    /** 反射用的 ClassLoader（`MiuiStatics` 在宿主里，得用它加载） */
    @Volatile
    private var loader: ClassLoader? = null

    /** 上一行打过的状态日志，内容没变不重复打（守护线程每 8 秒跑一次，不能刷屏） */
    @Volatile
    private var lastLog = ""

    /** 串行化所有同步动作：守护线程、Application 回调、hook 回调会并发进来 */
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hb-hostrule").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    override fun install(cl: ClassLoader) {
        loader = cl
        // ① 越早越好：Application 一创建就接管，赶在 native 首次读规则之前
        HostContext.onApplication { requestSync("Application 就绪") }
        // ② 宿主自己写规则文件之后，立刻把我们的版本贴回去
        hookWritePaths(cl)
        // ③ 兜底守护
        startGuard()
    }

    // ===================== hook 安装 =====================

    private fun hookWritePaths(cl: ClassLoader) {
        Hooks.hookAfter(cl, CLS_HELPER_UPDATOR, "updateRuleList") { requestSync("宿主下发规则") }
        for (m in listOf("writeJSONFile", "updateAdBlackist", "update")) {
            Hooks.hookAfter(cl, CLS_DATA_UPDATOR, m) { requestSync("宿主更新规则($m)") }
        }
    }

    private fun startGuard() {
        val t = Thread {
            while (true) {
                runCatching { requestSync(null) }
                runCatching { Thread.sleep(GUARD_INTERVAL_MS) }
            }
        }
        t.isDaemon = true
        t.name = "hb-hostrule-guard"
        t.priority = Thread.MIN_PRIORITY
        t.start()
    }

    /** 把同步动作丢到专用线程，绝不在宿主的调用栈里写盘 */
    private fun requestSync(reason: String?) {
        runCatching { executor.execute { runCatching { sync(reason) }.onFailure { logOnce("同步异常：${it.javaClass.simpleName} ${it.message}") } } }
    }

    // ===================== 核心：接管 / 还原 =====================

    private fun sync(reason: String?) {
        if (!on()) {
            // 关掉开关 = 还原。没接管过就什么都不做（restore 自己会判断，绝不乱删宿主的文件）
            if (HostRuleInstaller.isApplied() && HostRuleInstaller.restore()) {
                notifyNative()
                logOnce("【宿主规则】开关已关闭，宿主规则库已还原为接管前的版本")
            }
            return
        }

        val sets = AdRuleCodec.decode(AdRuleChannel.read())
        // 只写 URL 维度规则。元素隐藏（##）native 不认 —— 写进去它只会报 Parse error，
        // 那部分走 CustomAdBlockFeature 的 CSS 通道。
        // 解析器本来就把 `##` 拆到 elementRules 里了，这里的过滤是**防御性**的：
        // 万一规则库是旧版本写的、或以后解析器分流改了，也不会把 native 刷满错误日志。
        val rules = AdRuleCodec.enabledRules(sets).filterNot { it.contains("##") }

        // 已经是目标状态、且文件没被宿主覆盖回去 → 不写盘（守护线程每 8 秒走一次这条路）
        if (HostRuleInstaller.inEffectFor(rules)) return

        if (!HostRuleInstaller.apply(rules)) return

        notifyNative()
        val suffix = if (reason == null) "" else "（触发：$reason）"
        logOnce(
            "【宿主规则】已接管宿主拦截引擎：写入 ${rules.size} 条自定义规则，" +
                "清空小米白名单，其余名单保持原样$suffix"
        )
        if (rules.isEmpty()) {
            logOnce("【宿主规则】⚠ 自定义规则为空，宿主引擎当前处于「不过滤」状态（这是开关的语义：不用小米那套）")
        }
    }

    /**
     * 让 native 重载规则配置。
     *
     * 反汇编 `MiuiStatics$Natives` 里有 `notifyAdBlockUpdateConfig()`（JNI），
     * Java 侧包装是实例方法 —— `AdBlockHelper$Updator#run` 换完文件后调的就是它。
     * 不调这一下，改动要等宿主进程重启才生效。
     */
    private fun notifyNative() {
        val cl = loader ?: return
        runCatching {
            val cls = Xp.findClass(CLS_MIUI_STATICS, cl)
            val instance = Xp.callStatic(cls, "getInstance")
            Xp.call(instance, "notifyAdBlockUpdateConfig")
        }.onFailure {
            logOnce("【宿主规则】通知 native 重载失败（规则已落盘，重启浏览器后生效）：${it.javaClass.simpleName}")
        }
    }

    private fun logOnce(line: String) {
        if (line == lastLog) return
        lastLog = line
        XLog.i(line)
    }
}
