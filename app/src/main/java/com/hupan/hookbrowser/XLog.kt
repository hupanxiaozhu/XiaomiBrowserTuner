package com.hupan.hookbrowser

// 刻意**不** import android.util.Log：本文件自带同名门面 `Log`，
// 显式 import 的优先级高于同包声明，会把 `Log.write(...)` 解析到 android.util.Log 上去。
// 需要 android 那套时一律写全限定名 `android.util.Log.xxx`。

/**
 * 极简日志，**三档**。
 *
 * - [v] 详细：只在「浏览器调试模式」（`Config.MISC_DEBUG`）打开时输出。挂载细节、`【诊断】`
 *   `【采样】` `【注入】` 这类排查信息全在这一档，默认**完全静默** —— 模块装上后 LSPosed 日志里
 *   只该剩 [i] 的那几行。
 * - [i] 关键生命周期：模块注入、功能注册完成、规则库摘要、接管/还原生效、规则导入结果。
 *   每个进程不超过十行。
 * - [e] 异常：永远输出。任何 hook 失败都只走这里，绝不外抛。
 *
 * ## 1.10.0 的改动
 *
 * 旧实现走 `XposedBridge.log`（静态方法，API 102 禁止再调）。现在改为**框架日志接口**：
 * `MainHook` 在 `onModuleLoaded` 里把转发函数交进来，落点是
 * `XposedInterface#log(int priority, String tag, String msg)`；拿不到框架时退回 logcat。
 * 转发层独立成 [Log] 门面，就是为了让 [XLog]（以及所有 features）不必知道这个细节。
 */
internal object Log {
    /**
     * 框架日志写入器；未就绪时回退 `android.util.Log`。
     *
     * 参数是 Android 标准优先级（`android.util.Log.DEBUG` 等）—— `XposedInterface#log` 的
     * `priority` 用的就是这一套，**不是** libxposed 自定义的常量（它没定义任何级别常量）。
     */
    @Volatile
    private var writer: ((priority: Int, msg: String) -> Unit)? = null

    /** 由 [MainHook] 注入 */
    internal fun attach(w: (priority: Int, msg: String) -> Unit) {
        writer = w
    }

    internal fun write(priority: Int, msg: String) {
        val w = writer
        if (w == null) {
            toLogcat(priority, msg)
            return
        }
        runCatching { w(priority, msg) }.onFailure { toLogcat(priority, msg) }
    }

    /** 真正打到 logcat 的那一层 */
    private fun toLogcat(priority: Int, msg: String) {
        runCatching { android.util.Log.println(priority, XLog.TAG, msg) }
    }
}

/**
 * 原版 base.apk 的做法是让每个 hook 失败时 `throw new NoClassDefFoundError()`，
 * 宿主版本一变就直接把小米浏览器打崩。本模块反过来：**任何异常只记日志，绝不外抛**。
 */
internal object XLog {

    internal const val TAG = "HookBrowser"

    /**
     * 详细日志的闸门。
     *
     * 每次现查开关，而不是缓存一个静态标志 —— `Config` 自带缓存，开销可忽略，
     * 好处是用户在宿主运行期间打开「浏览器调试模式」时日志**当场**变详细，不必重启浏览器。
     * 读不到配置时 `Config.on` 回退默认值 `false`，也就是继续静默，方向是安全的。
     */
    private fun verbose(): Boolean = runCatching { Config.on(Config.MISC_DEBUG) }.getOrDefault(false)

    /** 详细日志：默认丢弃 */
    fun v(msg: String) {
        if (!verbose()) return
        raw(msg)
    }

    /** 关键生命周期：始终输出，数量要控制住 */
    fun i(msg: String) = raw(msg)

    fun e(msg: String, t: Throwable? = null) {
        val full = if (t == null) msg else "$msg\n${android.util.Log.getStackTraceString(t)}"
        raw("[E] $full")
    }

    /** 真正落地的那一层：框架日志接口优先；拿不到框架就退回 logcat */
    private fun raw(msg: String) = Log.write(android.util.Log.INFO, "$TAG: $msg")
}
