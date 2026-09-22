/*
 * 主界面（与番茄 HookFanqie 的 ui/page/MainPage.kt 同构）。
 *
 * 结构：
 *   Scaffold(bottomBar = miuix NavigationBar)   ← 底部三 Tab
 *     └ HorizontalPager                          ← 横向分页，左右滑也能切 Tab
 *         ├ FeaturesPage  功能
 *         ├ RulesPage     规则
 *         └ AboutPage     关于
 *   顶部没有共用顶栏：每个 Tab 自带 TopAppBar（标题随页不同、折叠行为互不干扰），
 *   本页只把「底栏内边距」通过 outerPadding 传下去，两者相加才是内容安全区。
 *
 * 返回键：不在第一个 Tab 时先回第一个 Tab，回不去才交给系统。
 *
 * 开关状态不在这里持有，而是提到 [ToggleState]（跨页面共享）—— 设置页里的「恢复默认」
 * 要能立刻反映到本页列表上，状态关在 remember 里就做不到。
 *
 * 三个浮层（详情 / 单选 / 风险确认）也统一在本页持有：切 Tab、进二级页再回来时状态一致，
 * 不需要每个 Tab 各带一份。
 */
package com.hupan.hookbrowser.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hupan.hookbrowser.ui.Detail
import com.hupan.hookbrowser.ui.OptionItem
import com.hupan.hookbrowser.ui.RISKY_CONFIRMS
import com.hupan.hookbrowser.ui.ShownDetail
import com.hupan.hookbrowser.ui.component.ConfirmDialog
import com.hupan.hookbrowser.ui.component.DetailDialog
import com.hupan.hookbrowser.ui.component.OptionDialog
import com.hupan.hookbrowser.ui.navigation.LocalNavigator
import com.hupan.hookbrowser.ui.navigation.Route
import com.hupan.hookbrowser.ui.page.about.AboutPage
import com.hupan.hookbrowser.ui.page.features.FeaturesPage
import com.hupan.hookbrowser.ui.page.rules.RulesPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold

/** 单选浮层当前要展示的那一组（三个下拉共用一份状态） */
private class OptionTarget(
    val title: String,
    val options: List<OptionItem>,
    val selected: String,
    val onPick: (String) -> Unit,
)

@Composable
internal fun MainPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    // 开关状态：装载一次，之后由 ToggleState 统一持有（设置页重置后本页自动跟着变）
    val values = remember(context) {
        ToggleState.ensure(context)
        ToggleState.values
    }
    val strings = remember(context) { ToggleState.strings }

    /**
     * 改开关：命中风险开关且是「打开」时先弹确认，用户点头才落盘。
     * 关掉风险开关不需要确认 —— 那是回到更安全的一侧。
     */
    var confirmKey by remember { mutableStateOf<String?>(null) }
    val update: (String, Boolean) -> Unit = { key, value ->
        if (value && RISKY_CONFIRMS.containsKey(key)) {
            confirmKey = key
        } else {
            ToggleState.set(context, key, value)
        }
    }
    val updateString: (String, String) -> Unit = { key, value ->
        ToggleState.setString(context, key, value)
    }

    // --- 详情浮层：显隐与内容分开存，关闭动画期间内容才不空 ---
    var detailShown by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<ShownDetail?>(null) }
    val showDetail: (String, String, Detail) -> Unit = { key, title, detail ->
        detailTarget = ShownDetail(key, title, detail)
        detailShown = true
    }

    // --- 单选浮层（UA 模式 / 默认搜索引擎）---
    var optionTarget by remember { mutableStateOf<OptionTarget?>(null) }
    val showOption: (String, List<OptionItem>, String, (String) -> Unit) -> Unit =
        { title, options, selected, onPick ->
            optionTarget = OptionTarget(title, options, selected, onPick)
        }

    // --- 分页 ---
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val currentPage = pagerState.currentPage
    val goToTab: (Int) -> Unit = { index ->
        if (index != currentPage) scope.launch { pagerState.animateScrollToPage(index) }
    }

    BackHandler(enabled = currentPage != 0) { goToTab(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentPage == index,
                        onClick = { goToTab(index) },
                        icon = tab.icon,
                        label = tab.label,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // 相邻页预组合一页：左右滑动时不会看到空白页
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (tabs.getOrNull(page)) {
                    MainTab.Features -> FeaturesPage(
                        outerPadding = innerPadding,
                        values = values,
                        strings = strings,
                        onUpdate = update,
                        onPickString = updateString,
                        onShowDetail = showDetail,
                        onShowOption = showOption,
                    )

                    MainTab.Rules -> RulesPage(
                        outerPadding = innerPadding,
                        values = values,
                        onUpdate = update,
                        onShowDetail = showDetail,
                    )

                    MainTab.About -> AboutPage(
                        outerPadding = innerPadding,
                        onOpenSettings = { navigator.navigate(Route.Settings) },
                        onOpenProjectInfo = { navigator.navigate(Route.ProjectInfo) },
                        onOpenChangelog = { navigator.navigate(Route.Changelog) },
                        onOpenDiagnostics = { navigator.navigate(Route.Diagnostics) },
                    )

                    null -> Unit
                }
            }

            DetailDialog(
                shown = detailShown,
                target = detailTarget,
                isOn = { key -> values[key] ?: false },
                onDismiss = { detailShown = false },
                onToggle = { key, value -> update(key, value) },
            )

            val option = optionTarget
            OptionDialog(
                shown = option != null,
                title = option?.title.orEmpty(),
                options = option?.options.orEmpty(),
                selected = option?.selected.orEmpty(),
                onPick = { key ->
                    option?.onPick?.invoke(key)
                    optionTarget = null
                },
                onDismiss = { optionTarget = null },
            )

            val risky = confirmKey
            val confirm = RISKY_CONFIRMS[risky]
            ConfirmDialog(
                shown = confirm != null,
                title = confirm?.first.orEmpty(),
                message = confirm?.second.orEmpty(),
                onConfirm = {
                    if (risky != null) ToggleState.set(context, risky, true)
                    confirmKey = null
                },
                onDismiss = { confirmKey = null },
            )
        }
    }
}
