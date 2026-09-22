/*
 * 界面主题层（与番茄 HookFanqie 的 ui/theme/Theme.kt 同构）。
 *
 * 只做一件事：把「模块自己的深浅两套色板」注入 miuix 的 MiuixTheme，让后续所有页面
 * 都从 MiuixTheme.colorScheme 取色，而不是各自写死颜色。
 *
 * 为什么用**显式色板**而不是 miuix 默认值：miuix 自带默认有三处与本模块设计令牌不同
 * （surface 偏白 #F7F7F7、次要文本偏蓝灰 #8C93B0、深色下 surfaceContainerHigh 与卡片同色
 * 导致「注意事项」块看不见），不覆盖就会出现深浅色不一致。
 *
 * 令牌值来自 ui/DesignTokens.kt（三工程统一设计规范 v2 的 Compose 落地副本），
 * 改值请改令牌，不要在页面里写裸色值。
 */
package com.hupan.hookbrowser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hupan.hookbrowser.ui.DsColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal val BrowserLightScheme = lightColorScheme(
    primary = DsColor.accentLight,
    background = DsColor.bgLight,
    onBackground = DsColor.textPrimaryLight,
    onBackgroundVariant = DsColor.textSecondaryLight,
    surface = DsColor.bgLight,
    onSurface = DsColor.textPrimaryLight,
    surfaceContainer = DsColor.cardLight,
    surfaceContainerHigh = DsColor.fieldLight,
    surfaceContainerHighest = DsColor.trackOffLight,
    dividerLine = DsColor.dividerLight,
    outline = DsColor.outlineLight,
)

internal val BrowserDarkScheme = darkColorScheme(
    primary = DsColor.accentDark,
    background = DsColor.bgDark,
    onBackground = DsColor.textPrimaryDark,
    onBackgroundVariant = DsColor.textSecondaryDark,
    surface = DsColor.bgDark,
    onSurface = DsColor.textPrimaryDark,
    surfaceContainer = DsColor.cardDark,
    surfaceContainerHigh = DsColor.fieldDark,
    surfaceContainerHighest = DsColor.trackOffDark,
    dividerLine = DsColor.dividerDark,
    outline = DsColor.outlineDark,
)

/**
 * 全局主题包装：跟随系统深浅色。
 *
 * 页面的根节点都套这一层（见 ui/navigation/AppNavigation.kt 与 MainActivity），
 * 页面内部不要再自己建 MiuixTheme，否则会出现半页换主题的割裂感。
 */
@Composable
internal fun BrowserTheme(content: @Composable () -> Unit) {
    MiuixTheme(
        colors = if (isSystemInDarkTheme()) BrowserDarkScheme else BrowserLightScheme,
        content = content,
    )
}

/** 强调色淡底：亮 12% / 暗 20%（与番茄、多看两边 accent_soft 同组值）。 */
@Composable
internal fun accentSoft(): Color =
    if (isSystemInDarkTheme()) DsColor.accentSoftDark else DsColor.accentSoftLight

/**
 * 强调色**实心底**上的文字 / 图标色（恒为白）。
 *
 * <p>用它而不是直接写 `Color.White`：三边约定「accent 上的前景色」是独立令牌
 * （`ds_color_on_accent`），哪天某个工程把它调成暖白或淡灰，界面上所有压在
 * 主色底上的元素只有这一处要跟着改。
 */
@Composable
internal fun onAccent(): Color =
    if (isSystemInDarkTheme()) DsColor.onAccentDark else DsColor.onAccentLight
