/*
 * 页面骨架的公共工具（与番茄 HookFanqie 的 ui/utils/Page.kt 同构）。
 *
 * 为什么需要它：主界面（MainPage）用 Scaffold 提供底部导航条，各 Tab 页面自己再套一层
 * Scaffold 提供顶栏（每个页面各自的标题与折叠行为互不干扰）。
 * 于是页面内容要同时避开「外层内边距（底栏）」与「内层内边距（顶栏）」，两边相加才是安全区。
 * 二级页不在 MainPage 之下（NavDisplay 整屏渲染），外层传默认的 0 —— 直接调单参形式即可。
 */
package com.hupan.hookbrowser.ui.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.DsSpace
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 宽屏断点（dp）：达到即按平板布局渲染（内容收窄居中），与 Material 的 medium 断点一致。 */
private const val WIDE_SCREEN_MIN_WIDTH_DP = 600

/**
 * 页面内容的左右边距。
 *
 * 卡片是 `fillMaxWidth()` 的，列表本身不给左右内边距的话，卡片会**贴着屏幕两侧**渲染，
 * 与澎湃原生设置页的观感差距就出在这里 —— 所以所有页面的内容都必须经过
 * [pageContentPadding]，由它统一补上这一层。
 */
internal val PageHorizontalPadding: Dp = DsSpace.pagePadH

/**
 * 页面内容的内边距 = 内层（顶栏）+ 外层（底栏）+ 左右页面边距 + 系统导航条。
 *
 * @param innerPadding 本页 Scaffold 给的内边距（含顶栏高度）
 * @param outerPadding 主界面 Scaffold 给的内边距（含底栏高度）；
 *        二级页不在 MainPage 之下，用默认的 0
 * @param extraTop 额外顶部留白（例如分区标题前想多留一点）
 * @param horizontal 左右边距，默认 [PageHorizontalPadding]
 */
@Composable
internal fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    extraTop: Dp = 0.dp,
    horizontal: Dp = PageHorizontalPadding,
): PaddingValues {
    val top = innerPadding.calculateTopPadding() + extraTop
    val bottom = innerPadding.calculateBottomPadding() +
        outerPadding.calculateBottomPadding() +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return remember(top, bottom, horizontal) {
        PaddingValues(top = top, bottom = bottom, start = horizontal, end = horizontal)
    }
}

/**
 * 列表的滚动修饰符：边缘回弹（miuix 的 overscroll 观感）+ 滚到底的触感反馈 + 顶栏折叠联动。
 *
 * 顺序不能换：nestedScroll 必须最后挂，否则顶栏拿不到子列表的滚动事件、大标题不会收起。
 */
internal fun Modifier.pageScroll(behavior: ScrollBehavior): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .nestedScroll(behavior.nestedScrollConnection)

/**
 * 是否宽屏（≥600dp）。
 *
 * ⚠ **当前没有任何页面调用它**，保留是有明确原因的：1.10.3 的 XML 版式里做过
 * 「宽屏左右留白加大、内容收窄居中」，1.11.0 重写成 Compose 时这一块**没有搬过来**
 * （属于功能回归）。这个函数与 `DsSpace.contentMaxWidth` 就是接回它的两个现成零件，
 * 接法：各页 `pageContentPadding(..., horizontal = if (wide) 96.dp else DsSpace.pagePadH)`。
 *
 * 不要因为「没人用」把它删掉 —— 删了下次还得把这套判断重写一遍。
 */
@Composable
internal fun rememberIsWideScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.screenWidthDp >= WIDE_SCREEN_MIN_WIDTH_DP }
}
