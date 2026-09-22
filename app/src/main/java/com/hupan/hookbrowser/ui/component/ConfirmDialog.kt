/*
 * 风险开关的确认浮层（安全检测 / 解锁隐藏项这类「不接受一键打开」的开关）。
 *
 * 与 [DetailDialog] 同一套做法：自绘 Box 叠层，不用系统 Dialog，也不碰 navigationevent。
 *
 * 为什么还要它而不直接在 onCheckedChange 里拦：这类开关的语义是「用户明确知道自己要什么」，
 * 一次误触不该直接改写宿主行为。旧实现（`PrefsFragment.guardRiskySwitch`）也是这套逻辑，
 * 只是载体从 MaterialAlertDialogBuilder 换成了浮层。
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import com.hupan.hookbrowser.ui.DsColor
import com.hupan.hookbrowser.ui.DsElevation
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * @param shown 是否显示
 * @param title 确认标题
 * @param message 正文（说明风险是什么）
 * @param onConfirm 用户点头
 * @param onDismiss 取消 / 点遮罩 / 返回键
 */
@Composable
internal fun ConfirmDialog(
    shown: Boolean,
    title: String,
    message: String,
    confirmText: String = "确认",
    onConfirm: () -> Unit,
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
                .padding(DsSpace.dialogPadH),
        ) {
            Text(
                text = title,
                fontSize = DsType.dialogTitle,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.md),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DsSpace.xl),
                horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
            ) {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(text = confirmText, onClick = onConfirm)
            }
        }
    }
}
