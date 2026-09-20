package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks

/**
 * 强制关闭宿主自带的广告开关。
 *
 * 宿主基线：小米浏览器 20.27.1010901。三个目标都是 `BrowserSettings` 里的 getter，
 * 实测实现分别是：
 *
 * ```
 * isShowAd()                  → mIsSimpleHome ? KVPrefs.getSimpleHomeAdSwitch() : true
 * isPersonalizedAdEnabled()   → KVPrefs.getBooleanPref("pref_ad_personalized", true)
 * isAdCustomDisabled()        → KVPrefs.getBooleanPref("pref_is_ad_custom_disabled", false)
 * ```
 *
 * 三个都是纯读取，hook 返回值即可，不需要写回任何 SP —— 所以**宿主设置页里开关的显示不变**，
 * 只是广告判断一律走「关」。这也是它比改 SP 更干净的地方：卸载模块即完全恢复。
 *
 * 为什么不做 `isAdBlockEnable()` → true：它在宿主里默认就是 true（`security_switch_adblock`
 * 和 `enable_adblock` 默认都开），hook 没有实际收益。
 * 为什么不做 `isWebAdBlockAvailable()` → true：那一条还依赖
 * `WebInjectionJsManager.isAdBlockAvailable()` 的真实 JS 注入能力，强改 true 只会让界面显示
 * 「可用」而实际不生效，属于欺骗用户，不做。
 */
internal object HostAdSwitchFeature : Feature(Config.MISC_HOST_AD) {

    private const val CLS = "com.android.browser.BrowserSettings"

    override fun install(cl: ClassLoader) {
        Hooks.hook(cl, CLS, "isShowAd") { p ->
            if (on()) p.result = false
        }

        Hooks.hook(cl, CLS, "isPersonalizedAdEnabled") { p ->
            if (on()) p.result = false
        }

        Hooks.hook(cl, CLS, "isAdCustomDisabled") { p ->
            if (on()) p.result = true
        }
    }
}
