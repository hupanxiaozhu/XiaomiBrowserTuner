/*
 * 功能详情浮层（「点行进详情」的那一层）。
 *
 * 为什么不用 miuix 的 WindowDialog：它的内容区走 navigationevent 的预测性返回
 * （rememberNavigationEventState / NavigationEventHandler），要求宿主提供
 * NavigationEventDispatcherOwner —— 模块自己的 Activity 没有，一点卡片即崩
 * 「No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner」。
 * 这里改成 Box 叠层：不新建窗口、不碰 navigationevent，没有崩溃面。
 *
 * 弹窗状态由 MainPage 持有（不在各 Tab 页面内部），切 Tab、进二级页再回来时状态一致。
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
import com.hupan.hookbrowser.ui.DetailBody
import com.hupan.hookbrowser.ui.DsColor
import com.hupan.hookbrowser.ui.DsElevation
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.ShownDetail
import com.hupan.hookbrowser.ui.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 详情浮层：底部大卡片 + 半透明遮罩。
 *
 * @param shown 是否显示。**显隐与内容分开存**（内容用可空变量另存）——只用一个可空变量的话，
 *              关闭瞬间内容就空了，关闭动画期间会看到空白卡片。
 * @param target 当前展示的那一条（存 key 是为了回读实时状态：开关可能在别处被改过）
 * @param isOn 读某开关键当前值
 * @param onDismiss 关闭
 * @param onToggle 在浮层里直接切换本项（**不关窗**，可连着点几下看效果）
 */
@Composable
internal fun DetailDialog(
    shown: Boolean,
    target: ShownDetail?,
    isOn: (String) -> Boolean,
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    if (!shown) return

    BackHandler(enabled = true) { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：点击空白处关闭（去掉水波纹，整屏涟漪很丑）
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

        val item = target ?: return@Box
        val on = isOn(item.key)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = DsSpace.contentMaxWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = DsSpace.pagePadH, vertical = DsSpace.pagePadH)
                .shadow(DsElevation.dialog, RoundedCornerShape(DsRadius.card))
                .background(
                    MiuixTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(DsRadius.card),
                )
                .padding(DsSpace.dialogPadH),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    fontSize = DsType.dialogTitle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 状态胶囊切换时带 220ms 底色 / 文字色过渡
                StatusPill(on)
            }

            DetailBody(item.detail)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DsSpace.xl),
                horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
            ) {
                TextButton(text = "关闭", onClick = onDismiss)
                TextButton(
                    text = if (on) "关闭本项" else "开启本项",
                    onClick = { onToggle(item.key, !on) },
                )
            }
        }
    }
}
