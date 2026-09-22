/*
 * 「关于」Tab —— 模块信息与更新日志。
 *
 * 信息架构（1.11.0 定的铁律）：**一级页只放开关 + 入口**，长内容全部下沉二级页。
 * 所以这里只有五样东西：hero（版本）、一句话简介、自动检查开关、五个二级页 / 动作入口、
 * 当前版本这一条更新日志。「检查更新」是动作入口：点按即查，结果浮层由主界面持有。
 * 完整信息表与全部历史日志分别在「项目信息」「更新日志」两个二级页里。
 *
 * 设置入口放在关于页而不是功能页顶栏 —— 顶栏只留一个标题，顶部就一个视觉焦点。
 */
package com.hupan.hookbrowser.ui.page.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hupan.hookbrowser.ui.AboutHero
import com.hupan.hookbrowser.ui.CHANGELOGS
import com.hupan.hookbrowser.ui.ChangelogCard
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.EntryDivider
import com.hupan.hookbrowser.ui.EntryGroup
import com.hupan.hookbrowser.ui.NavEntryRow
import com.hupan.hookbrowser.ui.ToggleCard
import com.hupan.hookbrowser.ui.UpdateState
import com.hupan.hookbrowser.ui.moduleVersion
import com.hupan.hookbrowser.ui.page.MainTab
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutPage(
    outerPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenProjectInfo: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val version = remember(context) { moduleVersion(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = MainTab.About.label,
                subtitle = MainTab.About.subtitle,
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
            item { AboutHero(version) }

            item { IntroCard() }

            // 自动检查开关是纯模块侧设置（本地 SP），不进宿主开关表，也不吃总闸
            item {
                ToggleCard(
                    title = "自动检查更新",
                    summary = "打开应用时每 24 小时静默查一次 GitHub Releases，" +
                        "发现新版本才提示；检查失败不打扰",
                    checked = UpdateState.autoCheckEnabled,
                    enabled = true,
                    onCheckedChange = { UpdateState.setAutoCheckEnabled(context, it) },
                    onShowDetail = {},
                )
            }

            item {
                EntryGroup {
                    NavEntryRow(
                        title = "检查更新",
                        summary = UpdateState.summary(),
                        onClick = { UpdateState.checkManually(context) },
                    )
                    EntryDivider()
                    NavEntryRow(
                        title = "项目信息",
                        summary = "适配版本 · 包名 · 作用域 · 运行环境",
                        onClick = onOpenProjectInfo,
                    )
                    EntryDivider()
                    NavEntryRow(
                        title = "更新日志",
                        summary = "全部版本的改动（历史默认收起）",
                        onClick = onOpenChangelog,
                    )
                    EntryDivider()
                    NavEntryRow(
                        title = "诊断",
                        summary = "框架服务连接状态与开关同步说明",
                        onClick = onOpenDiagnostics,
                    )
                    EntryDivider()
                    NavEntryRow(
                        title = "应用设置",
                        summary = "把全部开关恢复默认",
                        onClick = onOpenSettings,
                    )
                }
            }

            // 只留当前版本这一条（恒定展开），历史全部收进「更新日志」二级页
            item {
                val latest = CHANGELOGS.first()
                ChangelogCard(entry = latest, current = true)
            }
        }
    }
}

/**
 * 简介卡：一句话说清模块做什么、不做什么。
 *
 * <p>刻意不长：完整原理在 README 与各开关的详情里，关于页只负责「这是什么」。
 */
@Composable
private fun IntroCard() {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Text(
            text = "针对小米浏览器（com.android.browser）的 Xposed / LSPosed 模块：把写死在代码里的" +
                "去广告逻辑改成「一个功能一个开关」，每个开关都能单独关掉。",
            fontSize = DsType.body,
            lineHeight = DsType.lineBody,
            color = scheme.onBackgroundVariant,
        )
    }
}
