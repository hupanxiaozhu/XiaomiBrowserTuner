package com.hupan.hookbrowser

import android.app.Application
import android.content.Context
import android.content.ContextWrapper

/**
 * 宿主进程的 Application Context。
 *
 * ## 为什么需要它
 *
 * 模块的大部分功能是「改宿主的行为」，只在回调参数里拿对象就够了。但有一类需求必须落到
 * **文件系统**上：读宿主自己的广告规则库（在 `context.getFilesDir()/data/adblock/` 下）。
 * 那个目录是宿主的私有目录，模块进程（`com.hupan.hookbrowser`）没有权限 —— 但 hook 代码
 * 跑在**宿主进程**里，用宿主的 Context 去读，权限完全等同宿主自己。
 *
 * ## 为什么 hook `ContextWrapper#attachBaseContext`
 *
 * `Application` 继承 `ContextWrapper` 且**没有重写** `attachBaseContext`（Android 源码里
 * `Application#attach(Context)` 转手就调了它），所以挂 `ContextWrapper` 上不会被任何子类
 * 绕过去。回调里用 `thisObject is Application` 过滤 —— 否则每个 Activity / Service 创建
 * 时的 ContextWrapper 都会进来，把引用覆盖成一个短命的实例。
 *
 * 失败只记日志：拿不到 Context 的后果仅仅是不读宿主规则库，其余功能照常。
 */
internal object HostContext {

    @Volatile
    private var ref: Context? = null

    /** Application 就绪前注册的等待者（见 [onApplication]） */
    private val waiters = java.util.concurrent.CopyOnWriteArrayList<(Context) -> Unit>()

    /** 在宿主进程里调用一次；重复调用无副作用 */
    fun install() {
        runCatching {
            val n = Hooks.hookAllAfter(ContextWrapper::class.java, "attachBaseContext") { call ->
                if (ref != null) return@hookAllAfter
                val self = call.thisObject
                if (self is Application) {
                    ref = self
                    fire(self)
                }
            }
            XLog.v("宿主 Context 钩子已挂（$n 个重载）")
        }.onFailure { XLog.v("宿主 Context 钩子失败：${it.javaClass.simpleName} ${it.message}") }
    }

    /**
     * 「Application 创建好之后执行一次」。
     *
     * `install()` 是在 `handleLoadPackage` 里调的，那时进程刚起来、Application 还没 attach，
     * 直接 [app] 只会拿到 null。需要碰文件系统的功能（改宿主规则库）不能再等 —— 越早越好，
     * 所以要一个回调而不是轮询。
     *
     * 已经就绪就直接**同步**执行（调用方拿到的是当前线程）；否则排队等 attach 回调。
     * 回调里的异常由 [fire] 吞掉：它跑在 Application 初始化路径上，抛出去会直接崩宿主启动。
     */
    fun onApplication(cb: (Context) -> Unit) {
        ref?.let { ctx ->
            // 已经就绪：同步执行，调用方拿到的是当前线程
            runCatching { cb(ctx) }.onFailure { e -> logCbError(e) }
            return
        }
        waiters.add(cb)
    }

    private fun fire(ctx: Context) {
        val list = waiters.toList()
        waiters.clear()
        for (cb in list) runCatching { cb(ctx) }.onFailure { logCbError(it) }
    }

    private fun logCbError(e: Throwable) {
        XLog.v("宿主 Context 回调异常（已忽略）：${e.javaClass.simpleName} ${e.message}")
    }

    /** 拿不到（还没走到 Application 创建时机）时返回 null，调用方自己退化 */
    fun app(): Context? = ref
}
