package com.hupan.hookbrowser.features

import android.os.Build
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks
import java.util.Locale

/**
 * UA 伪装：抹掉 UA 里的 `XiaoMi/MiuiBrowser/x.y.z` 标识。
 *
 * 还原自 base.apk：hook `WebViewSettingConfig` 的三个方法，返回自行拼接的 UA。
 * 原版把 UA 串硬编码在 dex 里（Chrome/135、Chrome/116），浏览器或系统升级后就会失真，
 * 这里改为可选模式，真机信息从 Build / Locale 实时取。
 */
internal object UaFeature : Feature(Config.UA_PATCH) {

    private const val CLS = "com.android.browser.util.WebViewSettingConfig"

    override fun install(cl: ClassLoader) {
        Hooks.hook(cl, CLS, "getDefaultUserAgent") { p ->
            if (on()) p.result = UaBuilder.build(Config.uaMode())
        }
        // 不带「天鹅」（SearchCraft）标识的那份 UA，同样一起换掉
        Hooks.hook(cl, CLS, "getUserAgentStringWithoutSwan") { p ->
            if (on()) p.result = UaBuilder.build(Config.uaMode())
        }
        // 后缀 getter：置空等于彻底不追加 XiaoMi/MiuiBrowser 段
        Hooks.hook(cl, CLS, "getMiuiBrowserUseragentSuffix") { p ->
            if (on()) p.result = ""
        }
    }
}

internal object UaBuilder {

    const val MODE_CHROME = "chrome"
    const val MODE_MULTI = "multi"
    const val MODE_DESKTOP = "desktop"

    /** Chrome 移动版：真机信息 + 固定 Chrome 版本，不含任何小米浏览器标识 */
    private fun chromeMobile(): String {
        val lang = Locale.getDefault().language
        val country = Locale.getDefault().country
        return "Mozilla/5.0 (Linux; U; Android " + Build.VERSION.RELEASE +
            "; " + lang + "-" + country +
            " Build/" + Build.MODEL +
            ") AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/135.0.7049.79 Mobile Safari/537.36"
    }

    /** 多 App 伪装：与 base.apk 中的硬编码串一致 */
    private fun multiApp(): String =
        "Mozilla/5.0 (Linux; U; Android " + Build.VERSION.RELEASE +
            ") AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/135.0.7049.79 Mobile Safari/537.36 " +
            "SearchCraft/2.8.2 baiduboxapp/3.2.5.10 BingWeb/9.1 ALiSearchApp/2.4 WeChat/arm64"

    /** 桌面版：强制站点返回 PC 页面 */
    private fun desktop(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

    fun build(mode: String?): String = when (mode) {
        MODE_MULTI -> multiApp()
        MODE_DESKTOP -> desktop()
        else -> chromeMobile()
    }
}
