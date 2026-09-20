package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks

/**
 * 浏览器调试模式。
 *
 * 还原自 base.apk：hook `com.android.browser.BrowserSettings` 的
 * `getDebugMode` / `getFormalDebugMode`。
 * 返回值的具体布尔由方法语义推定（原 APK 该组回调同样只是 setResult 一个布尔），
 * 若与新版本浏览器行为不符，先关掉这个开关再排查。
 */
internal object DebugModeFeature : Feature(Config.MISC_DEBUG) {

    private const val CLS = "com.android.browser.BrowserSettings"

    override fun install(cl: ClassLoader) {
        Hooks.hook(cl, CLS, "getDebugMode") { p ->
            if (on()) p.result = true
        }
        // 「正式包是否开调试」——置 false，避免宿主走正式版的调试上报分支
        Hooks.hook(cl, CLS, "getFormalDebugMode") { p ->
            if (on()) p.result = false
        }
    }
}
