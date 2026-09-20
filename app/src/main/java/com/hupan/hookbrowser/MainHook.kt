package com.hupan.hookbrowser

import com.hupan.hookbrowser.features.Features
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 模块入口（由 `META-INF/xposed/java_init.list` 指定）。
 *
 * ## 1.10.0：从旧 API 迁到 libxposed API 102
 *
 * 旧实现是 `IXposedHookLoadPackage#handleLoadPackage(XC_LoadPackage.LoadPackageParam)`。
 * API 102 换成 `XposedModule` 子类：框架自动 `attachFramework`，然后回调
 * `onModuleLoaded`（框架就绪）与 `onPackageLoaded`（宿主进程被加载）。
 *
 * ## 为什么必须迁（而不是继续用 82 的老 API）
 *
 * 老 API 的跨进程读开关依靠 `XSharedPreferences` 或「重定向到 other 可读目录的 XML」，
 * LSPosed 2.2.0 起两者都被标记废弃、2.3.0 移除；而**不重定向**之后模块的 SP 落进
 * `/data/user_de/0/<pkg>/shared_prefs/`（uid 私有目录，宿主进程连父目录都进不去），
 * 开关会全部读不到并回退默认值 —— 表现为「基本功能正常，但改的开关没生效」。
 * API 102 给了正解：`XposedInterface#getRemotePreferences(String)`，存框架数据库、跨进程直读。
 *
 * 与原版 base.apk 的差别（沿用至今）：
 * - 不再用 PathClassLoader 二次加载自己的 APK 再反射调用；
 * - 每个功能独立 try/catch，任何一个功能挂掉不影响其它功能，更不会把宿主打崩；
 * - 功能注册一次完成，开关由每个回调在运行时按需判断。
 */
class MainHook : XposedModule() {

    /**
     * 框架就绪。把框架接口交给 [Hooks] / [Log] / [Config]，让它们从「静态旧 API」切到实例化接口。
     *
     * `this` 直接可用：`XposedModule` 继承 `XposedInterfaceWrapper`，而 wrapper 已经实现了
     * `XposedInterface` 的全部方法（`attachFramework` 由框架在构造后调用，用来给 wrapper 塞
     * 真正的框架实例）。所以这里不需要等 `attachFramework`，`this` 本身就是个合法的接口代理。
     */
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        val x: XposedInterface = this
        Hooks.attach(x)
        Config.attach(x)
        // log(priority, tag, msg) —— 三个参数缺一不可，tag 用与 logcat 一致的名字
        Log.attach { priority, msg -> runCatching { x.log(priority, TAG, msg) } }
        XLog.i("模块已加载（API ${x.apiVersion}，框架 ${x.frameworkName} ${x.frameworkVersion}）")
    }

    /**
     * 宿主进程被加载。
     *
     * `staticScope=true`（见 `META-INF/xposed/module.prop`）意味着只在 `scope.list` 里的包
     * 触发本回调，所以这里再判一次包名是冗余但便宜的保险。
     */
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE) return

        XLog.i("模块已注入 ${param.packageName}，开始注册功能")
        // 宿主进程里 attachFramework 可能还没跑完，这里再交一次 `this`（幂等）
        Config.attach(this)
        // 拿宿主 Context：读宿主自己的 adblock 规则库要用（模块进程读不到它的私有目录）
        HostContext.install()

        Features.ALL.forEach { feature ->
            try {
                feature.install(param.defaultClassLoader)
            } catch (t: Throwable) {
                // 单个功能失败只记日志，继续装后面的
                XLog.e("功能 [${feature.key}] 安装失败，已跳过", t)
            }
        }

        XLog.i("功能注册完成，共 ${Features.ALL.size} 组")
    }

    companion object {
        /** 作用域：小米浏览器 */
        const val TARGET_PACKAGE = "com.android.browser"

        private const val TAG = "HookBrowser"
    }
}
