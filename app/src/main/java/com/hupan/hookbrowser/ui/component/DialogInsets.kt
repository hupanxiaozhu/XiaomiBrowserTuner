/*
 * 贴底浮层（详情 / 单选 / 确认）要避开的底部高度。
 *
 * 为什么需要它：三个浮层都是「自绘 Box 叠层」，铺在主界面 Scaffold 的**内容区**里。
 * Scaffold 的内容区是整个屏幕（底栏是后来画上去的），所以浮层底部会被底部导航栏压住 ——
 * 表现为「卡片太靠下、正文看不全、按钮点不到」。原先只加了 `navigationBarsPadding()`
 * （只避开系统导航条），没算上底栏这一层。
 *
 * 这里取「宿主底栏高度」与「系统导航条」的**较大者**而不是相加：
 * miuix 的 NavigationBar 自身就带了导航条内边距，两者相加会多留一层空白。
 * 取大者则两边都安全 —— 不叠加，也不会被压住。
 */
package com.hupan.hookbrowser.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.DsSpace

/**
 * 贴底浮层的底部让位（含一层页面边距，卡片不贴着底栏）。
 *
 * @param hostBottom 宿主容器给浮层留的底部高度（主界面 = Scaffold 的 `innerPadding.calculateBottomPadding()`，
 *                   即底部导航栏高度）；二级页没有底栏，用默认的 0
 */
// 刻意不加 @ReadOnlyComposable：WindowInsets.navigationBars 这个扩展属性只有
// `@Composable get`（foundation-layout 1.11.2 的声明就没有 ReadOnly），
// 标了会直接编译不过 ——「Composables marked with @ReadOnlyComposable can only call
// other @ReadOnlyComposable composables」。
@Composable
internal fun rememberDialogBottomReserve(hostBottom: Dp = 0.dp): Dp =
    maxOf(hostBottom, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()) +
        DsSpace.pagePadH

/**
 * 卡片能占的最大高度 = 屏幕高 - 底部让位 - 顶部留白。
 *
 * <p>窗口被压得很矮时（分屏、横屏小窗）结果可能是负数，`heightIn` 不接受负值，这里兜底为 0。
 *
 * @param screenHeight `BoxWithConstraints` 的 `maxHeight`
 * @param bottomReserve [rememberDialogBottomReserve] 的返回值
 */
internal fun dialogCardMaxHeight(screenHeight: Dp, bottomReserve: Dp): Dp =
    (screenHeight - bottomReserve - DsSpace.pagePadH).coerceAtLeast(0.dp)
