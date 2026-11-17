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
     * 详细日志的闸门（2.0.1：加了一层 1 秒 TTL 的本地缓存）。
     *
     * 原来每次 `v()` 都要过一遍 `Config.on(MISC_DEBUG)`（TTL 判断 + 两次 map 查找），
     * 而 `v()` 恰恰是热路径上被调得最多的方法（字段读取失败、挂载细节、注入诊断）。
     * 现在先用一个 volatile 布尔挡一下，秒级粒度与 [Config] 自己的 TTL 对齐 ——
     * 用户在宿主运行期间打开「浏览器调试模式」，最多 1 秒后日志就变详细，仍然不需要重启浏览器。
     * 读不到配置时 `Config.on` 回退默认值 `false`，也就是继续静默，方向是安全的。
     */
    @Volatile
    private var verboseFlag = false

    @Volatile
    private var verboseAt = 0L

    private const val VERBOSE_TTL_MS = 1000L

    internal fun verboseEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (now - verboseAt < VERBOSE_TTL_MS) return verboseFlag
        verboseAt = now
        val v = runCatching { Config.on(Config.MISC_DEBUG) }.getOrDefault(false)
        verboseFlag = v
        return v
    }

    /**
     * 详细日志：默认丢弃，**参数传 lambda**。
     *
     * 直接传字符串的老写法 `v("读字段 $name 失败")` 会在**调用方**就把字符串拼好，
     * 闸门关着也白造一串对象。热路径一律用 `v { "..." }`：闸门关着时 lambda 根本不执行。
     */
    inline fun v(msg: () -> String) {
        if (!verboseEnabled()) return
        raw(msg())
    }

    /** 兼容直接传字符串的调用点（字符串已在调用方构造，热路径别用这个重载） */
    fun v(msg: String) {
        if (!verboseEnabled()) return
        raw(msg)
    }

    /** 关键生命周期：始终输出，数量要控制住 */
    fun i(msg: String) = raw(msg)

    fun e(msg: String, t: Throwable? = null) {
        val full = if (t == null) msg else "$msg\n${android.util.Log.getStackTraceString(t)}"
        raw("[E] $full")
    }

    /** 真正落地的那一层：框架日志接口优先；拿不到框架就退回 logcat */
    @PublishedApi
    internal fun raw(msg: String) = Log.write(android.util.Log.INFO, "$TAG: $msg")
}
