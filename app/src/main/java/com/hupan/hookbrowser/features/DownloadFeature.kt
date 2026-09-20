package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks

/**
 * 下载弹窗与下载链精简。
 *
 * 宿主基线：小米浏览器 20.27.1010901。三个目标已在该版本 dex 上核对：
 *
 * - `CommonDownloadDialogImpl#onCreateDialog(Bundle)` → 返回 null，不创建下载弹窗。
 * - `CommonDownloadDialogImpl#requestGameRecommend()` → 不请求「游戏推荐」数据。
 *   这是下载弹窗里的推广卡片（同类的 `mGameRecommendCard` / `mGameRecommendList` 字段就在
 *   `onCreateDialog` 里初始化）。方法返回 void，before 阶段 setResult 即跳过原实现。
 * - `DownloadHandler$1#call()` → 拦截下载处理链，不做市场跳转。
 *
 * 注：base.apk 原版还有一条 `mDownloadFromMarket` 的处理，但那是**字段**不是方法，
 * 用 hook 方法的方式挂必然挂不上；而且该字段在宿主 20.27 里已经不存在，
 * 所以这里直接删掉，不再保留一条永远失效的 hook。
 */
internal object DownloadFeature : Feature(Config.UI_DOWNLOAD) {

    private const val CLS = "com.android.browser.download.CommonDownloadDialogImpl"
    private const val DH = "com.android.browser.DownloadHandler\$1"

    override fun install(cl: ClassLoader) {
        Hooks.hook(cl, CLS, "onCreateDialog") { p ->
            if (on()) p.result = null
        }

        Hooks.hook(cl, CLS, "requestGameRecommend") { p ->
            if (on()) p.result = null
        }

        Hooks.hook(cl, DH, "call") { p ->
            if (on()) p.result = null
        }
    }
}
