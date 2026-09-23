package com.hupan.hookbrowser.features

import android.view.View
import android.view.ViewGroup
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.HookedCall
import com.hupan.hookbrowser.Hooks

/**
 * 简洁版主页「快捷方式」最多显示几行（1.15.0 起，1.15.2 定稿）。
 *
 * 三处改动缺一不可：`getShowSiteCount` 放开上限（1.15.1）→ `onLayout` 里把「更多」格
 * 从写死的位置 9 挪回网格末尾（1.15.2）→ 界面上母开关卡与它的下拉行合成一张卡片组（1.15.2，在 ui/ 侧）。
 *
 * ## 宿主实际怎么算的（20.27.1010901 反汇编核对）
 *
 * `BrowserQuickLinksPage$QuickLinksPanel#updateQuickLinks()` 的分工很干净：
 * 1. `total  = getNoMoveSites().size + getSites().size`；
 * 2. `show = this$0.getShowSiteCount(total)` —— **这个返回值就是网格实际铺的站点格数**；
 * 3. 按 `show` 个格子铺视图，每行 `mNumsPerRow` 个（手机 5 列 → 2 行 10 格）；
 * 4. `mMoreSiteCount = total − show`，多出来的站点归「更多」。
 *
 * ## 1.15.0 为什么完全没效果（两个错，都得改）
 *
 * **错一：hook 错了类。** `getShowSiteCount` 有两个实现：
 *
 * | 类 | 实现 | 说明 |
 * |---|---|---|
 * | `BrowserQuickLinksPage`（父类，classes6） | `return v1` —— **恒等**，入参原样返回 | 全 APK 里**没有任何 `new-instance`**，也没有除下面那一个之外的子类 |
 * | `homepage.SimpleVersionHomePage`（classes7） | `min(total, 9)` | `extends BrowserQuickLinksPage`，**简洁版主页就是它** |
 *
 * 调用点写的是 `invoke-virtual {this$0, total}, BrowserQuickLinksPage.getShowSiteCount:(I)I`，
 * 运行时**虚拟派发到子类重写**，父类那个 hook 永远走不到 → 开关开了也毫无反应。
 * 那个 9 是父类的静态常量 `SIMPLE_HOME_MAX_SITE_COUNT`，被**内联**进子类字节码（`const/16 v0, #int 9`），
 * 所以改字段没用，只能 hook 方法。
 *
 * **错二：只在上限被突破时才改返回值（这才是最坑的）。**
 * 子类原本返回 `min(total, 9)`。1.15.0 写的是「`total > 上限` 才覆盖」，
 * 于是 `9 < total ≤ 上限` 这一段（比如 10 个站点、上限 20）**根本不覆盖**，原方法跑完还是返回 9 →
 * 依旧 2 行。正确做法是：开关开着时**无条件**给结果，返回 `min(total, 上限)`。
 *
 * ## 上限 = 行数 × 每行个数 − 1（不是 −0）
 *
 * 网格总格数 = 站点格数 + 1 个「更多」/「+」格。宿主自己的默认值就是
 * `SIMPLE_HOME_MAX_SITE_COUNT = 9 = 2 行 × 5 − 1`，即**最后一行第一格留给「更多」**。
 * 所以 N 行对应站点格数上限 `N × perRow − 1`，多出来的那一格才塞得进第 N 行。
 *
 * 注意 `isShowingMoreQuickLink` 是宿主拿**原始 total** 和常量 9 比的（在我们这个 hook 之前就算好了），
 * 只要 `total > 9` 它就会补那一格；我们只在 `total > 上限` 时才截断，而 `上限 ≥ 14 > 9`，
 * 两者不会打架。
 *
 * ## 1.15.2 修：光改 `show` 还不够，「更多」格会和站点格重叠
 *
 * `QuickLinksPanel#onLayout` 里对「更多」格有一个**硬编码位置**：
 *
 * ```
 * for (i = 0; i < childCount; i++) {
 *     child = getChildAt(i)
 *     pos = (child == mShowMoreQuickLink && isInSimpleHome()) ? 9 : i   // ← 写死 9
 *     child.layout(... 按 pos 折算行列 ...)
 * }
 * ```
 *
 * 那个 9 = `SIMPLE_HOME_MAX_SITE_COUNT − 1`，同样是**内联常量**。宿主的假设是
 * 「简洁版主页最多 9 个站点格（位置 0~8），第 10 格留给『更多』」，所以在 `show ≤ 9` 时
 * 位置正好、看不出问题；一旦我们把 `show` 提到 24，第 10 个站点格和「更多」格**同时**落在位置 9 →
 * 界面上就是「好几个图标/文字糊在一起」，同时网格最后空一格。
 *
 * 修法：`onLayout` 之后（after）把「更多」格单独按它**真实的 child 下标**重新 `layout` 一次，
 * 于是它回到末尾（位置 = childCount − 1），站点格各就各位。宿主自己的公式照抄：
 * 行列由 `pos / mNumsPerRow`、`pos % mNumsPerRow` 折算，间距取该面板的几个 `mSpacing*` 字段。
 *
 * 只在开关开着时动手；原生场景（`show ≤ 9`）算出来的位置和宿主一致，天然幂等。
 *
 * ## 边界都很保守
 *
 * - 开关关着 / 行数读不出来 → 直接放行，宿主怎么排就怎么排；
 * - `每行个数` 读不到 → 按实测的 5 列算；
 * - **这是上限不是补齐**：站点不足 N 行时就是自然行数，不补空格子。
 *   也不能补 —— 宿主的铺格子循环是 `for (i in 0 until show) sites[i - offset]`，
 *   返回比 `total` 大的值会直接 `IndexOutOfBoundsException` 崩主页。
 */
internal object HomeQuickLinkRowsFeature : Feature(Config.UI_QUICKLINK_ROWS) {

    /** 简洁版主页（`extends BrowserQuickLinksPage`）—— 真正生效的那个重写 */
    private const val CLS_SIMPLE_HOME = "com.android.browser.homepage.SimpleVersionHomePage"

    /** 父类。20.27 上它自己不会被实例化（只为兜底：换了机型 / 别的入口直接用父类时还能生效） */
    private const val CLS_BASE = "com.android.browser.BrowserQuickLinksPage"

    private const val METHOD = "getShowSiteCount"

    /** 站点网格（内部类 QuickLinksPanel，自绘网格，不是 RecyclerView） */
    private const val FIELD_PANEL = "mQuickLinksPanel"

    /** 应用快捷方式网格（内部类 AppQuickLinksPanel，`updateAppQuickLinks` 也走同一个方法） */
    private const val FIELD_APP_PANEL = "mAppQuickLinksPanel"

    /** 网格自己的「每行几个」字段 */
    private const val FIELD_NUMS_PER_ROW = "mNumsPerRow"

    /** 站点网格内部类 —— 负责摆放格子的 `onLayout` 在这里 */
    private const val CLS_PANEL = "com.android.browser.BrowserQuickLinksPage\$QuickLinksPanel"

    private const val METHOD_ON_LAYOUT = "onLayout"

    /** 「更多」格。`onLayout` 里被特判成固定位置 9，就是重叠的来源 */
    private const val FIELD_MORE_CELL = "mShowMoreQuickLink"

    // onLayout 折算行列用到的间距字段（照抄宿主公式，缺一个就退化成不动手）
    private const val FIELD_SPACING_H = "mSpacingBetweenItemH"
    private const val FIELD_SPACING_V = "mSpacingBetweenItemV"
    private const val FIELD_MARGIN_ITEM = "mSpacingMarginItem"
    private const val FIELD_MARGIN_TOP = "mSpacingMarginTopInit"
    private const val FIELD_RTL = "mIsLayoutRtl"

    /** 读不到字段时的兜底：实测手机 5 列 */
    private const val FALLBACK_PER_ROW = 5

    override fun install(cl: ClassLoader) {
        Hooks.hook(cl, CLS_SIMPLE_HOME, METHOD) { p -> cap(p) }
        Hooks.hook(cl, CLS_BASE, METHOD) { p -> cap(p) }
        // 「更多」格的位置修正：宿主把它写死在位置 9，站点格一超过 9 个就会和它叠在一起
        Hooks.hookAfter(cl, CLS_PANEL, METHOD_ON_LAYOUT) { p -> realignMoreCell(p) }
    }

    /**
     * 把返回值压到「行数 × 每行个数 − 1」。
     *
     * ⚠ 开关开着时必须**无条件**写 `p.result`：宿主自己的 `min(total, 9)` 在
     * `9 < total ≤ 上限` 这一段同样会生效，不覆盖就等于没改（1.15.0 的坑）。
     */
    private fun cap(p: HookedCall) {
        if (!on()) return
        val rows = Config.quickLinkRows()
        if (rows <= 0) return
        val total = p.args.firstOrNull() as? Int ?: return
        val limit = rows * perRow(p.thisObject) - 1
        if (limit <= 0) return
        p.result = total.coerceAtMost(limit)
    }

    /**
     * `onLayout` 之后把「更多」格按它真实的 child 下标重新摆一次。
     *
     * 宿主原生公式里 `pos = 9` 是给「最多 9 个站点格」用的；我们放开上限以后，
     * 位置 9 会同时被第 10 个站点格和「更多」格占用 → 视觉重叠。这里只重排它一个 view，
     * 其余格子的位置本来就是对的，不碰。
     */
    private fun realignMoreCell(p: HookedCall) {
        if (!on()) return
        val panel = p.thisObject as? ViewGroup ?: return
        val more = Hooks.field(panel, FIELD_MORE_CELL) as? View ?: return
        val pos = panel.indexOfChild(more)
        if (pos < 0) return

        val w = more.measuredWidth
        val h = more.measuredHeight
        if (w <= 0 || h <= 0) return

        val perRow = (Hooks.field(panel, FIELD_NUMS_PER_ROW) as? Int)?.takeIf { it > 0 }
            ?: FALLBACK_PER_ROW
        val spacingH = Hooks.field(panel, FIELD_SPACING_H) as? Int ?: return
        val spacingV = Hooks.field(panel, FIELD_SPACING_V) as? Int ?: return
        val marginItem = Hooks.field(panel, FIELD_MARGIN_ITEM) as? Int ?: return
        val marginTop = Hooks.field(panel, FIELD_MARGIN_TOP) as? Int ?: return
        val rtl = Hooks.field(panel, FIELD_RTL) as? Boolean ?: false

        val col = pos % perRow
        val top = (pos / perRow) * (h + spacingV) + marginTop
        val left = if (rtl) {
            panel.width - marginItem - col * (w + spacingH) - w
        } else {
            col * (w + spacingH) + marginItem
        }
        // 位置没变就别折腾（原生 show ≤ 9 时算出来和宿主一致，天然幂等）
        if (more.left == left && more.top == top) return
        more.layout(left, top, left + w, top + h)
    }

    /** 每行几个：读宿主网格自己的 `mNumsPerRow`；读了两个面板都拿不到就按实测的 5 列 */
    private fun perRow(page: Any?): Int {
        for (field in arrayOf(FIELD_PANEL, FIELD_APP_PANEL)) {
            val panel = Hooks.field(page, field) ?: continue
            val n = Hooks.field(panel, FIELD_NUMS_PER_ROW) as? Int
            if (n != null && n > 0) return n
        }
        return FALLBACK_PER_ROW
    }
}

/**
 * 行数的可选项（与 `ui/FeatureCatalog.kt` 的 QUICKLINK_ROWS_OPTIONS 一一对应）。
 *
 * 存字符串而不是整数：remote preferences 那一组只按 String / Boolean 镜像，
 * 整数落进去宿主侧拿到的是字符串，两边解析规则不一致就会各说各话。
 */
internal object QuickLinkRows {

    const val ROWS_3 = "3"
    const val ROWS_4 = "4"
    const val ROWS_5 = "5"

    const val DEFAULT = ROWS_4
}
