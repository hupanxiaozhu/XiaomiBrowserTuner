/*
 * 页面装配（与番茄 HookFanqie 的 ui/navigation/AppNavigation.kt 同构）。
 *
 * 三件事：
 *   1. 建返回栈（rememberNavBackStack，靠 Route 的 @Serializable 做状态恢复）；
 *   2. 把 Navigator 挂到 CompositionLocal，让任意页面都能 navigate/pop；
 *   3. NavDisplay 渲染「当前栈顶页面」，转场动画由 miuix-navigation3-ui 提供。
 *
 * 主题不在这里注入 —— 见 MainActivity（整个 Activity 只套一层 BrowserTheme）。
 *
 * ⚠ `entry { ... }` 是 EntryProviderScope 的成员函数，**不要 import** 它，
 *   否则会 `Unresolved reference 'entry'`。
 */
package com.hupan.hookbrowser.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hupan.hookbrowser.ui.page.ChangelogPage
import com.hupan.hookbrowser.ui.page.DiagnosticsPage
import com.hupan.hookbrowser.ui.page.MainPage
import com.hupan.hookbrowser.ui.page.ProjectInfoPage
import com.hupan.hookbrowser.ui.page.SettingsPage
import com.hupan.hookbrowser.ui.page.rules.RuleManagerPage
import com.hupan.hookbrowser.ui.page.rules.ScriptManagerPage

@Composable
internal fun AppNavigation(startRoute: Route = Route.Main) {
    val backStack = rememberNavBackStack(startRoute)
    val navigator = remember(backStack) { Navigator(backStack) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        val entryProvider = remember(backStack) {
            entryProvider<NavKey> {
                entry<Route.Main> { MainPage() }
                entry<Route.Settings> { SettingsPage() }
                entry<Route.ProjectInfo> { ProjectInfoPage() }
                entry<Route.Diagnostics> { DiagnosticsPage() }
                entry<Route.Changelog> { ChangelogPage() }
                entry<Route.RuleManager> { RuleManagerPage() }
                entry<Route.ScriptManager> { ScriptManagerPage() }
            }
        }
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider,
        )

        NavDisplay(
            entries = entries,
            onBack = { navigator.pop() },
        )
    }
}
