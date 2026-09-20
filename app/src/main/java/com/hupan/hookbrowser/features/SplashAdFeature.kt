package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks

/**
 * 开屏广告拦截。
 *
 * 还原自 base.apk：hook `com.msa.sdk.core.splash.SystemSplashAd` 的三个方法。
 * MSA（移动安全联盟）开屏广告 SDK 被国内大量 App 复用，小米浏览器走的就是它。
 */
internal object SplashAdFeature : Feature(Config.AD_SPLASH) {

    private const val CLS = "com.msa.sdk.core.splash.SystemSplashAd"

    override fun install(cl: ClassLoader) {
        // 广告类型：0 = 无开屏
        Hooks.hook(cl, CLS, "getAdSplashType") { p ->
            if (on()) p.result = 0
        }
        // 是否支持被动（后台唤起）开屏
        Hooks.hook(cl, CLS, "getIsSupportPassiveSplashAd") { p ->
            if (on()) p.result = false
        }
        // 开屏服务 Intent：返回 null 即不拉起广告服务
        Hooks.hook(cl, CLS, "getServiceIntent") { p ->
            if (on()) p.result = null
        }
    }
}
