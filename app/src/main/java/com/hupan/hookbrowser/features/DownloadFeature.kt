package com.hupan.hookbrowser.features

import android.view.View
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks

/**
 * 下载推广精简（**不再拦下载弹窗本身**）。
 *
 * 宿主基线：小米浏览器 20.27.1010901。
 *
 * ## ⚠ 别再挂 `CommonDownloadDialogImpl#onCreateDialog` 的 before-null（1.15.3 撤掉）
 *
 * 1.9.x 起这里是「before + `p.result = null`」，意图是「根本不建下载弹窗」。这个做法有两个
 * 致命问题，1.15.3 实测（用浏览器打开任意非 APK 下载链接必闪退）后撤掉：
 *
 * 1. **宿主对返回值不判空。** `DownloadDialogFragment#onCreateDialog` 拿到接口返回值后**直接**调用
 *    `dialog.setCanceledOnTouchOutside(true)`（20.27 反汇编：`move-result-object v2` 之后没有
 *    任何 `if-eqz` 分支），返回 null 就是主线程 NPE；再往上一层 androidx `DialogFragment` 也是
 *    `mDialog = onCreateDialog(...)` 之后立刻 `setupDialog(mDialog, style)`，同样不判空。
 *    这个异常抛在 hook 回调**之外**，模块自己的 try 接不住 —— 宿主当场闪退。
 * 2. **下载根本没法开始。** 弹窗是下载的确认环节（真正发起下载的 `onDownloadBtnClick()` 由弹窗
 *    按钮触发），弹窗不建就永远等不到用户点「下载」。所以功能说明里那句「下载功能本身不受影响」
 *    在这个实现下并不成立。
 *
 * ## 「商店版」两条推广链路（1.15.4 补齐）
 *
 * APK 下载跟普通文件下载走的是**同一个** `CommonDownloadDialogImpl`（`DownloadDialogFragment`
 * 只在「有 riskInfo」时改用 `WarnDownloadDialogImpl`，其余一律 Common）。它内部按
 * `BaseDownloadDialogImpl#mDownloadFromMarket` 二选一 inflate：
 *
 * | `mDownloadFromMarket` | 布局 | 特征 |
 * |---|---|---|
 * | true | `download_dialog.xml` | 「应用商店安装包」标题 + 「官方检测 / 安全」标签 + 「原安装包」 |
 * | false | `download_dialog_normal.xml` | 同上一套商店区（初始 `INVISIBLE`，有商店版时才显示）+ 小游戏推荐卡 |
 *
 * 这就是「APK 下载时冒出来的小米应用商店审核版本」—— 商店区（`tvStoreTitle` / `rlStore`）
 * 整块归本开关管：只改可见性、不动任何按钮，所以「原安装包 / 下载」入口照旧可用。
 *
 * 另一条链路是 `com.android.browser.guidecard`（下载引导卡，贴屏幕底部的浮层）：
 * `BrowserTab#setTab` 时 new 一个 `GuideCardManager`，页面加载完成后 `fetchGuideInfoOnPageFinished` /
 * `prefetchGuideInfo` 预取「该页面官方版应用」信息（请求里带 oaid / miuiVersion 等设备字段），
 * 预取回来后 `lambda$doRequestGuideInfo$1` 直接 `tryShowCard()` —— 弹出「识别到你可能在找的资源：」
 * 那张卡，底部文案就是「"安全守护"已提供应用商店上架版本」。其中：
 *
 * - `startGuideInfoRequest()` 是**所有**预取请求的唯一出口（两个 fetch 方法都调它）→ 跳过它既不请求也不上报；
 * - `tryShowCard()` 是**唯一**的显示入口 → 跳过它卡永远不出（连 `checkFrequencyControl` 的冷却逻辑都不必碰）。
 *
 * ## 现在保留的五处
 *
 * - `CommonDownloadDialogImpl#requestGameRecommend()`：下载弹窗里的「小游戏推荐」，会真发网络请求。
 *   方法返回 void，before 阶段给一个结果即跳过原实现。**不去请求 → 列表永远为空 → 推荐卡保持
 *   布局里的初始 `INVISIBLE`，不显示任何内容。**
 * - `CommonDownloadDialogImpl#onCreateDialog` 的 **after**：兜底把推广控件置 `GONE`
 *   （推荐卡 + 商店区标题 + 商店区容器）。布局里它们是 `INVISIBLE`（仍参与测量、占位），
 *   数据为空时高度虽接近 0，但不等于「确定不占位」；字段读不到就放弃（宿主改名 = 静默退化，
 *   不影响下载）。
 * - `GuideCardManager#{startGuideInfoRequest, tryShowCard}` + `GuideDownloadCardView#show`：
 *   不预取、不弹卡（见上）。前两条是 private 方法，而卡的 `show()` 是 public void 无参 ——
 *   多挂一道保险，三条里任一条挂不上都不会漏。
 * - `DownloadHandler$1#call()`：APK 下载走应用市场的「拉取应用信息」链路，返回 null 即不跳市场。
 *   宿主侧对 null **有**判空分支（`lambda$getAppUrlInSuperMarket$10` 里 `if-eqz` + try/catch 包着），
 *   与上面那条 null 的性质完全不同。**这条保留。**
 *
 * 注：`mDownloadFromMarket` 是**字段**（在 `BaseDownloadDialogImpl` 上，1.15.3 的注释误判为
 * 「20.27 已不存在」；实测它正是 `onCreateDialog` 选布局的依据），hook 方法的方式挂不上，
 * 也不该去改它 —— 改它等于替宿主决定「这次下载从哪来」。这里只做可见性收敛。
 */
internal object DownloadFeature : Feature(Config.UI_DOWNLOAD) {

    private const val CLS = "com.android.browser.download.CommonDownloadDialogImpl"
    private const val DH = "com.android.browser.DownloadHandler\$1"
    private const val GUIDE = "com.android.browser.guidecard.GuideCardManager"
    private const val GUIDE_CARD = "com.android.browser.guidecard.GuideDownloadCardView"

    /** 下载弹窗里那张「小游戏推荐」卡（布局 download_dialog_normal 里初始为 INVISIBLE） */
    private const val FIELD_GAME_CARD = "mGameRecommendCard"

    /** 「应用商店安装包」区块的容器（含「官方检测 / 安全」标签、商店版版本与大小） */
    private const val FIELD_STORE_LAYOUT = "rlStore"

    /** 商店区块的标题（文案 = download_dialog_mi_market_package「应用商店安装包」） */
    private const val FIELD_STORE_TITLE = "tvStoreTitle"

    override fun install(cl: ClassLoader) {
        // 推荐数据不再请求（方法返回 void，before 阶段给结果即跳过原实现）
        Hooks.hook(cl, CLS, "requestGameRecommend") { p ->
            if (on()) p.result = null
        }

        // 弹窗建好之后，把推广控件置 GONE（只改可见性，不动宿主流程与返回值）
        Hooks.hookAfter(cl, CLS, "onCreateDialog") { p ->
            if (on()) hideDownloadPromo(p.thisObject)
        }

        Hooks.hook(cl, DH, "call") { p ->
            if (on()) p.result = null
        }

        // 下载引导卡：不预取商店版应用信息、不弹卡
        Hooks.hook(cl, GUIDE, "startGuideInfoRequest") { p ->
            if (on()) p.result = null
        }
        Hooks.hook(cl, GUIDE, "tryShowCard") { p ->
            if (on()) p.result = null
        }
        // 兜底：上面两条是 private 方法；卡自己的 show() 是 public void 无参，多挂一道保险
        Hooks.hook(cl, GUIDE_CARD, "show") { p ->
            if (on()) p.result = null
        }
    }

    /** 把下载弹窗里的推广控件置 GONE；拿不到字段 / 已经是 GONE 就什么都不做 */
    private fun hideDownloadPromo(dialog: Any?) {
        hideView(dialog, FIELD_GAME_CARD)
        hideView(dialog, FIELD_STORE_LAYOUT)
        hideView(dialog, FIELD_STORE_TITLE)
    }

    private fun hideView(owner: Any?, field: String) {
        val v = Hooks.field(owner, field) as? View ?: return
        if (v.visibility != View.GONE) v.visibility = View.GONE
    }
}
