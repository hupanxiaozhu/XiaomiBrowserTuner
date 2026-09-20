package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.XLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * 显示宿主设置页里被隐藏的条目。
 *
 * 还原自 base.apk：hook `androidx.preference.Preference#isVisible`。
 * 原版回调里是 `const/4 v1#1 → setResult(Boolean(true))`，即**强制返回可见**，
 * 目的是把小米浏览器自己藏起来的设置项放出来。
 *
 * ⚠ 这条 hook 是全局的：它作用于宿主设置页里**每一个** Preference，
 * 所以极性写反（返回 false）会让整个设置页变成空白 —— 1.3.0 就是这么翻车的。
 * 本版把它改名成 misc_unlock_pref、语义明确为「解锁隐藏项」且默认关闭。
 * 原版是「装上即生效」，本工程按「默认值必须是保守值」的原则默认关闭
 * （会暴露宿主隐藏项，属于降低安全性的开关）。
 *
 * ## 1.6.5：一次运行就能分清「没效果」的四种成因
 *
 * 1.6.3 的首帧诊断只打一次、只报开关值 —— 「你明明打开了开关，可那次日志是改之前跑的」
 * 这种最常见的情况根本区分不出来。现在改成 after hook + 值变化重打 + 采样：
 *
 * | 日志（TAG `HookBrowser`） | 结论 |
 * |---|---|
 * | `【诊断】开关文件 …/settings.xml 存在=true 字节=N` | 宿主进程读到了模块的 SP；`存在=false`/字节=0 → 开关从来没写进去 |
 * | `【诊断】读不到开关文件 → 全部走默认值` | XSharedPreferences 读不到 SP → **默认 false 的开关永远开不起来**，与 hook 无关 |
 * | `【诊断】默认关的开关现值：misc_unlock_pref=…` | 三项并排看，直接分辨「没写」还是「写了没读到」 |
 * | `【诊断】…isVisible：开关=开/关` | **值变化时重打** → 打开开关后立刻能看到它变了（SP 有 1s 缓存，约 1 秒生效，不用重启宿主） |
 * | `【诊断】…评估到 key=xxx 宿主原本=false` | 宿主**确实在评估**这个 Preference 且原本判它不可见 → 开关一开就该出来 |
 * | `【诊断】采样满 12 个：共评估 N 个，其中 M 个宿主原本判不可见` | **`M=0` 就是关键结论**：宿主没有隐藏任何 Preference → 你看不到的项不是被 isVisible 藏起来的，这条路解不开 |
 * | 完全没有 `isVisible` 相关日志 | 宿主没走这个方法 → 设置页不是 androidx Preference 体系 |
 *
 * 潜在副作用：某些条目被隐藏是因为在当前机型上不受支持，放出来后点击可能异常。
 */
internal object UnlockPrefFeature : Feature(Config.MISC_UNLOCK_PREF) {

    private const val CLS = "androidx.preference.Preference"

    private const val SAMPLE_MAX = 12

    /** 上次打印过的开关值；**变化时重打**，用来确认「改完开关有没有生效」 */
    @Volatile
    private var lastLogged: Boolean? = null

    /** 采样过的 Preference key（去重） */
    private val evaluatedKeys = LinkedHashSet<String>()

    /** 宿主总共评估了多少个 Preference、其中多少个原本判为不可见 */
    private val evaluated = AtomicInteger()

    private val originallyHidden = AtomicInteger()

    /** 摘要只打一次 */
    @Volatile
    private var summaryLogged = false

    /**
     * 用 **after** hook：先让宿主算出它自己的答案，再决定要不要翻成 true。
     * 这样既改了行为，又能拿到「宿主原本判它可见还是不可见」这个关键读数 ——
     * `原先不可见 = 0` 就证明宿主压根没在隐藏东西，问题不在开关。
     */
    override fun install(cl: ClassLoader) {
        Hooks.hookAfter(cl, CLS, "isVisible") { p ->
            val enabled = on()
            val orig = p.result as? Boolean
            evaluated.incrementAndGet()
            if (orig == false) originallyHidden.incrementAndGet()
            if (enabled) p.result = true
            diagnose(enabled, p.thisObject, orig)
        }
    }

    private fun diagnose(enabled: Boolean, obj: Any?, orig: Boolean?) {
        if (lastLogged != enabled) {
            lastLogged = enabled
            XLog.v(
                "【诊断】宿主在读 Preference#isVisible：开关=" + if (enabled) "开" else "关" +
                    if (enabled) "（已强制可见，隐藏项会显示出来）"
                    else "（保持宿主原样；要解锁隐藏项请在模块设置里打开该开关）"
            )
        }

        val key = Hooks.call(obj, "getKey") as? String ?: return
        val first = synchronized(evaluatedKeys) {
            if (evaluatedKeys.size >= SAMPLE_MAX) false else evaluatedKeys.add(key)
        }
        if (first) {
            XLog.v("【诊断】isVisible 评估到 key=$key 宿主原本=${orig ?: "?"}（开关=${if (enabled) "开" else "关"}）")
        }

        if (!summaryLogged && synchronized(evaluatedKeys) { evaluatedKeys.size } >= SAMPLE_MAX) {
            summaryLogged = true
            val total = evaluated.get()
            val hidden = originallyHidden.get()
            XLog.v(
                "【诊断】采样满 $SAMPLE_MAX 个：共评估 $total 个 Preference，" +
                    "其中 $hidden 个宿主原本判不可见" +
                    if (hidden == 0) "（=0 → 宿主没在用 isVisible 隐藏东西，开关开不开都一样）" else ""
            )
        }
    }
}
