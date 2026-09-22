/*
 * 「规则」Tab —— 自定义拦截规则。
 *
 * 页面结构（自上而下）：
 *   1. 规则库概况卡 —— 一眼确认「导入了什么、当前启用多少条」（关键状态放顶部，不用翻到底）；
 *   2. 两个开关：模块自己的规则引擎、接管宿主 native 引擎（后者是有副作用的，默认关）；
 *   3. 「规则管理」入口 —— 导入 / 启停 / 删除在独立页里做（仍是 View 实现的
 *      [RuleManagerActivity]），本页只放入口，保持一屏内看完。
 *
 * 概况为什么在这里重新读一次：规则库存在另一个 SP 组（adrules），改动只发生在规则管理页，
 * 所以从那页返回时（ActivityResult 回调）重读一次即可，不需要常驻监听。
 */
package com.hupan.hookbrowser.ui.page.rules

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.hupan.hookbrowser.adblock.AdRuleCodec
import com.hupan.hookbrowser.adblock.AdRuleStore
import com.hupan.hookbrowser.ui.Detail
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.EntryDivider
import com.hupan.hookbrowser.ui.EntryGroup
import com.hupan.hookbrowser.ui.NavEntryRow
import com.hupan.hookbrowser.ui.RULES_TOGGLES
import com.hupan.hookbrowser.ui.RuleManagerActivity
import com.hupan.hookbrowser.ui.StatusPill
import com.hupan.hookbrowser.ui.ToggleCard
import com.hupan.hookbrowser.ui.page.MainTab
import com.hupan.hookbrowser.ui.page.ToggleState
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 规则库概况：规则集数 / 启用中的条数 / 总条数 */
private class RuleSummary(
    val sets: Int,
    val enabledSets: Int,
    val enabledRules: Int,
    val enabledElements: Int,
    val totalRules: Int,
    val totalElements: Int,
) {
    val empty: Boolean get() = sets == 0
}

private fun loadSummary(context: Context): RuleSummary {
    val sets = runCatching { AdRuleStore.load(context) }.getOrDefault(mutableListOf())
    return RuleSummary(
        sets = sets.size,
        enabledSets = sets.count { it.enabled },
        enabledRules = AdRuleCodec.enabledRules(sets).size,
        enabledElements = AdRuleCodec.enabledElements(sets).size,
        totalRules = AdRuleCodec.totalRules(sets),
        totalElements = AdRuleCodec.totalElements(sets),
    )
}

@Composable
internal fun RulesPage(
    outerPadding: PaddingValues,
    values: Map<String, Boolean>,
    onUpdate: (String, Boolean) -> Unit,
    onShowDetail: (String, String, Detail) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val masterOn = ToggleState.masterOn

    var summary by remember { mutableStateOf(loadSummary(context)) }
    // 从规则管理页返回时重读（那边可能刚导入或删掉了规则集）
    val openManager = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { summary = loadSummary(context) }

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
                        onClick = {
                            openManager.launch(
                                Intent(context, RuleManagerActivity::class.java)
                            )
                        },
                    )
                    EntryDivider()
                    NavEntryRow(
                        title = "刷新概况",
                        summary = "重新读取规则库（从别处改动后点一下）",
                        onClick = { summary = loadSummary(context) },
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
 */
@Composable
private fun RulesSummaryCard(summary: RuleSummary) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (summary.empty) {
                    "还没有导入规则"
                } else {
                    "${summary.sets} 个规则集 · 启用 ${summary.enabledSets} 个"
                },
                fontSize = DsType.title,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = if (summary.empty) {
                    "导入后浏览器会在数秒内自动生效，不需要重启"
                } else {
                    "启用中：请求拦截 ${summary.enabledRules} 条 · 元素隐藏 ${summary.enabledElements} 条" +
                        "（共 ${summary.totalRules} / ${summary.totalElements} 条）"
                },
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.xs),
            )
        }
        StatusPill(
            on = !summary.empty && summary.enabledSets > 0,
            onText = "生效中",
            offText = if (summary.empty) "未导入" else "已停用",
        )
    }
}
