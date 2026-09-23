/*
 * 「功能」Tab —— 去广告 / 界面精简 / 高级三个分区的功能开关。
 *
 * 每页自带 Scaffold + TopAppBar：标题区域随本页列表滚动而把大标题收起（MiuixScrollBehavior）。
 * 内容由 MainPage 传下来的 values / strings 驱动 —— 本页不自己读 SP，避免「切 Tab 后值不同步」。
 *
 * 页面结构（自上而下）：
 *   1. 运行状态卡 —— 一眼确认宿主版本是否与模块适配版本一致（放最上方，不用翻到底）；
 *   2. 总闸 hero 卡 —— 主色底，与其余功能项拉开层级；
 *   3. 三个分区的开关列表（「界面精简」里的两项各自跟一条下拉行）。
 */
package com.hupan.hookbrowser.ui.page.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.ui.Detail
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.EntryDivider
import com.hupan.hookbrowser.ui.EntryGroup
import com.hupan.hookbrowser.ui.FEATURE_SECTIONS
import com.hupan.hookbrowser.ui.MASTER_DETAIL
import com.hupan.hookbrowser.ui.OptionItem
import com.hupan.hookbrowser.ui.OptionRow
import com.hupan.hookbrowser.ui.QUICKLINK_ROWS_OPTIONS
import com.hupan.hookbrowser.ui.SEARCH_ENGINE_OPTIONS
import com.hupan.hookbrowser.ui.SectionHeader
import com.hupan.hookbrowser.ui.TargetStatusCard
import com.hupan.hookbrowser.ui.ToggleCard
import com.hupan.hookbrowser.ui.UA_OPTIONS
import com.hupan.hookbrowser.ui.page.MainTab
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
internal fun FeaturesPage(
    outerPadding: PaddingValues,
    values: Map<String, Boolean>,
    strings: Map<String, String>,
    onUpdate: (String, Boolean) -> Unit,
    onPickString: (String, String) -> Unit,
    onShowDetail: (String, String, Detail) -> Unit,
    onShowOption: (String, List<OptionItem>, String, (String) -> Unit) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val masterOn = values[Config.MASTER] ?: Config.defaultOf(Config.MASTER)

    // 两个下拉各自的母开关：母开关关着时下拉置灰（改它没有意义）
    val uaOn = masterOn && (values[Config.UA_PATCH] ?: Config.defaultOf(Config.UA_PATCH))
    val engineOn = masterOn &&
        (values[Config.UI_SEARCH_ENGINE] ?: Config.defaultOf(Config.UI_SEARCH_ENGINE))
    val quickLinkRowsOn = masterOn &&
        (values[Config.UI_QUICKLINK_ROWS] ?: Config.defaultOf(Config.UI_QUICKLINK_ROWS))

    fun labelOf(options: List<OptionItem>, key: String, fallback: String): String =
        options.firstOrNull { it.key == key }?.label ?: fallback

    Scaffold(
        topBar = {
            TopAppBar(
                title = MainTab.Features.label,
                subtitle = MainTab.Features.subtitle,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .pageScroll(scrollBehavior),
            contentPadding = pageContentPadding(innerPadding, outerPadding),
            verticalArrangement = Arrangement.spacedBy(DsSpace.cardGap),
        ) {
            // 运行状态：页面第一屏信息，进页面即可判断是不是版本问题
            item { TargetStatusCard() }

            // 总闸：主色 hero 大卡片，视觉上与其余功能项区分层级
            item {
                ToggleCard(
                    title = "启用模块",
                    summary = "总闸 —— 关闭后所有功能立即失效，不用重启浏览器",
                    checked = masterOn,
                    enabled = true,
                    onCheckedChange = { onUpdate(Config.MASTER, it) },
                    onShowDetail = { onShowDetail(Config.MASTER, "启用模块", MASTER_DETAIL) },
                    emphasized = true,
                )
            }

            FEATURE_SECTIONS.forEach { (sectionTitle, toggles) ->
                item { SectionHeader(sectionTitle) }
                items(items = toggles, key = { it.key }) { toggle ->
                    // UA / 默认搜索引擎 / 快捷方式行数三项各带一条下拉。母卡 + 下拉行必须
                    // 合到一张卡里（EntryGroup + EntryDivider）：两张同色圆角卡零间距贴在一起时，
                    // 圆角处会连成一片，看起来像两条内容挤在同一张卡里（1.15.2 修）。
                    val grouped = toggle.key == Config.UA_PATCH ||
                        toggle.key == Config.UI_QUICKLINK_ROWS ||
                        toggle.key == Config.UI_SEARCH_ENGINE

                    val card: @Composable () -> Unit = {
                        ToggleCard(
                            title = toggle.title,
                            summary = toggle.summary,
                            checked = values[toggle.key] ?: toggle.default,
                            enabled = masterOn,
                            onCheckedChange = { onUpdate(toggle.key, it) },
                            onShowDetail = { onShowDetail(toggle.key, toggle.title, toggle.detail) },
                            flat = grouped,
                        )
                    }

                    if (!grouped) {
                        card()
                    } else {
                        EntryGroup {
                            card()
                            EntryDivider()
                            when (toggle.key) {
                                Config.UA_PATCH -> {
                                    val current = strings[Config.UA_MODE] ?: ""
                                    OptionRow(
                                        title = "UA 伪装模式",
                                        value = labelOf(UA_OPTIONS, current, current),
                                        enabled = uaOn,
                                        flat = true,
                                        onClick = {
                                            onShowOption("UA 伪装模式", UA_OPTIONS, current) { key ->
                                                onPickString(Config.UA_MODE, key)
                                            }
                                        },
                                    )
                                }

                                Config.UI_QUICKLINK_ROWS -> {
                                    val current = strings[Config.UI_QUICKLINK_ROWS_VALUE] ?: ""
                                    OptionRow(
                                        title = "行数",
                                        value = labelOf(QUICKLINK_ROWS_OPTIONS, current, current),
                                        enabled = quickLinkRowsOn,
                                        flat = true,
                                        onClick = {
                                            onShowOption(
                                                "主页快捷方式行数",
                                                QUICKLINK_ROWS_OPTIONS,
                                                current,
                                            ) { key -> onPickString(Config.UI_QUICKLINK_ROWS_VALUE, key) }
                                        },
                                    )
                                }

                                Config.UI_SEARCH_ENGINE -> {
                                    val current = strings[Config.SEARCH_ENGINE_TARGET] ?: ""
                                    OptionRow(
                                        title = "选择引擎",
                                        value = labelOf(SEARCH_ENGINE_OPTIONS, current, current),
                                        enabled = engineOn,
                                        flat = true,
                                        onClick = {
                                            onShowOption(
                                                "选择引擎",
                                                SEARCH_ENGINE_OPTIONS,
                                                current,
                                            ) { key -> onPickString(Config.SEARCH_ENGINE_TARGET, key) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
