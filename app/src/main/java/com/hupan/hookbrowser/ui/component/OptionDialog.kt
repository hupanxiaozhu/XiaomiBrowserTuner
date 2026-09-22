/*
 * 单选浮层（UA 伪装模式、默认搜索引擎这类「下拉项」用）。
 *
 * 与 [DetailDialog] 同一套做法：**自绘 Box 叠层**，不用 miuix 的 WindowDialog / SuperDialog ——
 * 它们的内容区走 navigationevent 的预测性返回，要求宿主提供 NavigationEventDispatcherOwner，
 * 模块自己的 Activity 没有（一点就崩）。
 *
 * 刻意不用 `miuix-preference` 里的下拉组件：那个模块只为一两行入口引入不划算，
 * 且它与本工程的「点行 = 详情」交互不是一套语义。
 */
package com.hupan.hookbrowser.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.CAPSULE_TAG_SHAPE
import com.hupan.hookbrowser.ui.DsColor
import com.hupan.hookbrowser.ui.DsElevation
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.OptionItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * @param shown 是否显示
 * @param title 浮层标题
 * @param options 可选项（顺序 = 展示顺序）
 * @param selected 当前选中的 key
 * @param onPick 选中某项；实现方负责落盘
 * @param onDismiss 关闭
 */
@Composable
internal fun OptionDialog(
    shown: Boolean,
    title: String,
    options: List<OptionItem>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!shown) return

    BackHandler(enabled = true) { onDismiss() }

    val scheme = MiuixTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DsColor.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = DsSpace.contentMaxWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = DsSpace.pagePadH, vertical = DsSpace.pagePadH)
                .shadow(DsElevation.dialog, RoundedCornerShape(DsRadius.card))
                .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
                .padding(vertical = DsSpace.md),
        ) {
            Text(
                text = title,
                fontSize = DsType.dialogTitle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    horizontal = DsSpace.dialogPadH,
                    vertical = DsSpace.sm,
                ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 选项多的时候内部滚动，浮层最多占半屏多一点
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { item ->
                    val picked = item.key == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item.key) }
                            .padding(horizontal = DsSpace.dialogPadH, vertical = DsSpace.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.label,
                            fontSize = DsType.title,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (picked) scheme.primary else scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (picked) {
                            // 选中标记：主色实心圆点（8dp），与更新日志卡片里的当前版本圆点同款
                            Box(
                                modifier = Modifier
                                    .size(DsSpace.sm)
                                    .background(scheme.primary, CAPSULE_TAG_SHAPE),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DsSpace.dialogPadH, vertical = DsSpace.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "关闭", onClick = onDismiss)
            }
        }
    }
}
