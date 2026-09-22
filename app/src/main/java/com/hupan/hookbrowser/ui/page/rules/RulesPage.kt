/*
 * 「规则」Tab —— 自定义拦截规则。
 *
 * 页面结构（自上而下）：
 *   1. 规则库概况卡 —— 一眼确认「导入了什么、当前启用多少条 / 几个脚本」（关键状态放顶部）；
 *   2. 三个开关：模块自己的规则引擎、接管宿主 native 引擎（有副作用，默认关）、
 *      用户脚本（执行用户自选代码，默认关）；
 *   3. 「规则管理」「脚本管理」入口 —— 导入 / 启停 / 删除都在二级页里做，本页只放入口。
 *
 * 概况怎么保持最新：规则库存在另一个 SP 组（adrules），改动只发生在规则管理页。
 * 1.11.0 靠 `ActivityResult` 回调重读（那时管理页是独立 Activity）；1.12.0 管理页变成
 * 同进程的二级页、那条回调没有了，改用 [RuleLibraryState.revision] 修订号触发重算。
 * 计算本身走 `Dispatchers.IO` —— 它会把启用中的规则全量拍平成表，规则库上规模后
 * 不能放在主线程（原实现是在组合期直接算的）。
 */
package com.hupan.hookbrowser.ui.page.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.ui.Detail
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.EntryDivider
import com.hupan.hookbrowser.ui.EntryGroup
import com.hupan.hookbrowser.ui.NavEntryRow
import com.hupan.hookbrowser.ui.RULES_TOGGLES
import com.hupan.hookbrowser.ui.StatusPill
import com.hupan.hookbrowser.ui.ToggleCard
import com.hupan.hookbrowser.ui.navigation.LocalNavigator
import com.hupan.hookbrowser.ui.navigation.Route
import com.hupan.hookbrowser.ui.page.MainTab
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RulesPage(
    outerPadding: PaddingValues,
    values: Map<String, Boolean>,
    onUpdate: (String, Boolean) -> Unit,
    onShowDetail: (String, String, Detail) -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    // 总闸状态与 FeaturesPage 用同一种来源：MainPage 传下来的那份 map（它就是 ToggleState.values）
    val masterOn = values[Config.MASTER] ?: Config.defaultOf(Config.MASTER)

    // 概况：修订号变一次就重算一次（进页面时、以及每次从规则管理页改完回来）
    val revision = RuleLibraryState.revision
    val summary by produceState<RuleSummary?>(initialValue = null, revision) {
        value = withContext(Dispatchers.IO) { loadSummary(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = MainTab.Rules.label,
                subtitle = MainTab.Rules.subtitle,
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
            item { RulesSummaryCard(summary) }

            items(items = RULES_TOGGLES, key = { it.key }) { toggle ->
                ToggleCard(
                    title = toggle.title,
                    summary = toggle.summary,
                    checked = values[toggle.key] ?: toggle.default,
                    enabled = masterOn,
                    onCheckedChange = { onUpdate(toggle.key, it) },
                    onShowDetail = { onShowDetail(toggle.key, toggle.title, toggle.detail) },
                )
            }

            item {
                EntryGroup {
                    NavEntryRow(
                        title = "规则管理",
                        summary = "从 URL / 本地文件导入，逐条启停与删除",
                        onClick = { navigator.navigate(Route.RuleManager) },
                    )
                    EntryDivider()
                    NavEntryRow(
                        title = "脚本管理",
                        summary = "导入 .user.js，逐条启停与删除",
                        onClick = { navigator.navigate(Route.ScriptManager) },
                    )
                    EntryDivider()
                    NavEntryRow(
                        title = "刷新概况",
                        summary = "重新读取规则库与脚本库（从别处改动后点一下）",
                        onClick = { RuleLibraryState.bump() },
                    )
                }
            }
        }
    }
}

/**
 * 规则库概况卡：标题 + 一行取值 + 右下状态胶囊。
 *
 * <p>与功能页的「运行状态」卡同一形态（紧凑横排，不是带长说明的大卡）：
 * 概况是「一眼确认」的信息，说明留给规则管理页与详情。
 *
 * <p>[summary] 为 null = 还在读（首次进页面那一两帧），此时给一行「正在读取规则库…」，
 * 不要用「还没有导入规则」占位 —— 那会让人以为自己的规则丢了。
 */
@Composable
private fun RulesSummaryCard(summary: RuleSummary?) {
    val scheme = MiuixTheme.colorScheme
    val s = summary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    s == null -> "正在读取规则库…"
                    s.empty -> "还没有导入规则或脚本"
                    else -> "规则集 ${s.sets} 个（启用 ${s.enabledSets}）· " +
                        "脚本 ${s.scripts} 个（启用 ${s.enabledScripts}）"
                },
                fontSize = DsType.title,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = when {
                    s == null -> "统计启用中的条数"
                    s.empty -> "导入后浏览器会在数秒内自动生效，不需要重启"
                    else -> "启用中：请求拦截 ${s.enabledRules} 条 · 元素隐藏 ${s.enabledElements} 条" +
                        "（共 ${s.totalRules} / ${s.totalElements} 条）"
                },
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.xs),
            )
        }
        StatusPill(
            on = s != null && !s.empty && (s.enabledSets > 0 || s.enabledScripts > 0),
            onText = "生效中",
            offText = if (s == null || s.empty) "未导入" else "已停用",
        )
    }
}
