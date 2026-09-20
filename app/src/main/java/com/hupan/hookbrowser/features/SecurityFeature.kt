package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks

/**
 * 拦截网址安全检测 —— **默认关闭**。
 *
 * 还原自 base.apk：hook `com.android.browser.Tab$GetSecurityFlagAsyncTask#onPostExecute`，
 * 原实现为 `setResult(null)`，即在回调前拦下、不让结果落到 UI 上。
 *
 * 后果：钓鱼站 / 恶意站点的安全提示不再出现，等于主动降低上网安全性。
 * 设置页在开启时会弹确认框，不接受「默认打开」。
 */
internal object SecurityFeature : Feature(Config.MISC_SECURITY) {

    private const val CLS = "com.android.browser.Tab\$GetSecurityFlagAsyncTask"

    override fun install(cl: ClassLoader) {
        Hooks.hook(cl, CLS, "onPostExecute") { p ->
            if (on()) p.result = null
        }
    }
}
