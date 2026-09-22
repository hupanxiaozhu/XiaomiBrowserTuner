/*
 * 应用设置（二级页）—— 模块自身的偏好，与「功能开关」分开。
 *
 * 为什么单独开一页：主界面的三个 Tab 都属于「用浏览器时会看的」，而这里是
 * 「配模块时才看的」（重置）。放进关于页会让关于页越堆越长，放进功能页又会被日常开关淹没。
 *
 * 入口：「关于」Tab 的「应用设置」行。返回：顶栏返回箭头 或 系统返回键，都走 Navigator.pop()。
 */
package com.hupan.hookbrowser.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.EntryGroup
import com.hupan.hookbrowser.ui.InfoItem
import com.hupan.hookbrowser.ui.InfoRow
import com.hupan.hookbrowser.ui.moduleVersion
import com.hupan.hookbrowser.ui.navigation.LocalNavigator
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val version = remember(context) { moduleVersion(context) }

    // 重置是破坏性操作：先点「重置」把确认按钮露出来，再点「确认重置」才真写盘。
    // 不用弹窗：模块进程里 miuix 的 WindowDialog 会因为缺 NavigationEventDispatcherOwner 而崩
    // （详见 ui/component/DetailDialog.kt 的说明），内联确认既安全又少一次窗口切换。
    var confirming by remember { mutableStateOf(false) }
    var lastResetHint by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                subtitle = "模块自身的偏好",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.pageScroll(scrollBehavior),
                contentPadding = pageContentPadding(innerPadding, PaddingValues(0.dp)),
                verticalArrangement = Arrangement.spacedBy(DsSpace.cardGap),
            ) {
                item {
                    SettingCard(title = "恢复默认开关") {
                        Text(
                            text = "把全部开关（含 UA 模式与默认搜索引擎）写回模块默认值。" +
                                "默认值偏保守：去广告类默认开启，接管宿主引擎、解锁隐藏项、" +
                                "调试模式、安全检测默认关闭。",
                            fontSize = DsType.body,
                            lineHeight = DsType.lineBody,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        lastResetHint?.let { hint ->
                            Text(
                                text = hint,
                                fontSize = DsType.body,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = DsSpace.sm),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = DsSpace.md),
                            horizontalArrangement = Arrangement.spacedBy(
                                DsSpace.md, Alignment.End,
                            ),
                        ) {
                            if (confirming) {
                                TextButton(text = "取消", onClick = { confirming = false })
                                TextButton(
                                    text = "确认重置",
                                    onClick = {
                                        ToggleState.resetToDefaults(context)
                                        confirming = false
                                        lastResetHint =
                                            "已恢复默认值，返回功能页即可看到全部开关已更新"
                                    },
                                )
                            } else {
                                TextButton(
                                    text = "重置",
                                    onClick = {
                                        lastResetHint = null
                                        confirming = true
                                    },
                                )
                            }
                        }
                    }
                }

                // 版本信息在这里只留一行「当前版本」：完整信息表在「项目信息」页，
                // 两处重复只会让两边都得维护。
                item {
                    EntryGroup {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = DsSpace.cardPadH,
                                    vertical = DsSpace.cardPadV,
                                ),
                        ) {
                            InfoRow(
                                item = InfoItem(
                                    label = "当前版本",
                                    value = version,
                                    detail = "深浅色与主题跟随系统，模块不自带主题切换 —— " +
                                        "与多看、番茄两边一致",
                                ),
                                version = version,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 设置页的一张卡：标题 + 说明 + 可选的按钮行。 */
@Composable
private fun SettingCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MiuixTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(DsRadius.card),
            )
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Text(
            text = title,
            fontSize = DsType.title,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}
