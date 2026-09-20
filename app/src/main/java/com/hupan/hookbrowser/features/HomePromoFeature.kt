package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks

/**
 * 首页推广位屏蔽。
 *
 * 还原自 base.apk：`PremiumOperationManager`（尊享版推广管理器）+
 * `SimpleVersionHomeLayout`（简洁版首页布局）两组 hook。
 */
internal object HomePromoFeature : Feature(Config.AD_HOME_PROMO) {

    private const val POM = "com.android.browser.homepage.PremiumOperationManager"
    private const val SVH = "com.android.browser.homepage.SimpleVersionHomeLayout"

    override fun install(cl: ClassLoader) {
        // 「尊享版」变更提示
        Hooks.hook(cl, POM, "canShowPremiumChangeHint") { p ->
            if (on()) p.result = false
        }
        // 是否已弹过尊享版引导
        Hooks.hook(cl, POM, "getHasShowPremiumGuideDialog") { p ->
            if (on()) p.result = false
        }
        // 是否已弹过「简洁版 → 尊享版」引导
        Hooks.hook(cl, POM, "getHasShowSimpleToPremiumGuideDialog") { p ->
            if (on()) p.result = false
        }
        // 首页布局里的推广位（原 APK 里方法名拼写就是 chanShow...）
        Hooks.hook(cl, SVH, "chanShowPremiumChangeLayout") { p ->
            if (on()) p.result = false
        }
        // 用户点了「切换到尊享版首页」：拦掉，不执行
        Hooks.hook(cl, SVH, "userClickChangeToPremiumHome") { p ->
            if (on()) p.result = null
        }
    }
}
