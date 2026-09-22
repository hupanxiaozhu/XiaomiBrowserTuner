/*
 * 返回栈操作入口（与番茄 HookFanqie 的 ui/navigation/Navigator.kt 同构）。
 *
 * 页面不直接摸 MutableList 返回栈，而是通过 CompositionLocal 拿到 Navigator 调 navigate/pop，
 * 这样页面与「栈是怎么实现的」解耦 —— 以后换回 navigation2 或改双栏场景，页面不用动。
 */
package com.hupan.hookbrowser.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

class Navigator(val backStack: MutableList<NavKey>) {

    /** 压栈。已在栈顶的同一路由不重复压（避免连点出现两层相同页面）。 */
    fun navigate(route: NavKey) {
        if (backStack.lastOrNull() == route) return
        backStack.add(route)
    }

    /** 出栈。栈底（主界面）永不弹出 —— 否则会出现空白页。 */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /** 一路弹到指定路由（inclusive = 连它一起弹掉）。 */
    fun popUpTo(route: NavKey, inclusive: Boolean = false) {
        val index = backStack.indexOf(route)
        if (index == -1) return
        val keep = if (inclusive) index else index + 1
        while (backStack.size > keep) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

internal val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No navigator found! 请确认页面挂在 AppNavigation 的 NavDisplay 之下。")
}
