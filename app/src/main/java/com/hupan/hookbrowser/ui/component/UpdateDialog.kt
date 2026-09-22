/*
 * 「检查更新」浮层：发现新版本时弹更新日志 + 下载入口；手动检查的
 * 「已是最新 / 检查失败」结果也用同一张卡片说明。
 *
 * 与详情 / 单选 / 确认浮层同一套自绘 Box 叠层（不用 miuix WindowDialog，
 * 原因见 DetailDialog 顶注），底部让位与卡片限高复用 DialogInsets 的两个计算。
 *
 * 下载不做应用内下载：按钮只负责跳浏览器（APK 直链优先，退回 Releases 页），
 * 下载、校验、安装全交给系统，省掉存储权限与安装会话一整条链路。
 */
package com.hupan.hookbrowser.ui.component

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.ui.DsColor
import com.hupan.hookbrowser.ui.DsElevation
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.UpdateDialogData
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UpdateDialog(
    data: UpdateDialogData?,
    onDismiss: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val d = data ?: return
    val context = LocalContext.current

    BackHandler(enabled = true) { onDismiss() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 遮罩：点击空白处关闭（与 DetailDialog 一致，不要整屏水波纹）
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

        val scheme = MiuixTheme.colorScheme
        val reserve = rememberDialogBottomReserve(bottomInset)
        val cardMaxHeight = dialogCardMaxHeight(maxHeight, reserve)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = DsSpace.contentMaxWidth)
                .fillMaxWidth()
                .padding(
                    start = DsSpace.pagePadH,
                    end = DsSpace.pagePadH,
                    top = DsSpace.pagePadH,
                    bottom = reserve,
                )
                .heightIn(max = cardMaxHeight)
                .shadow(DsElevation.dialog, RoundedCornerShape(DsRadius.card))
                .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
                .padding(DsSpace.dialogPadH),
        ) {
            Text(
                text = d.title,
                fontSize = DsType.dialogTitle,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )

            if (d.release != null) {
                Text(
                    text = "当前 v${d.currentVersion} → 新版本 v${d.release.version}",
                    fontSize = DsType.caption,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = DsSpace.xs),
                )
                // 更新日志：跟随卡片剩余空间压缩并内部滚动（fill = false 的老规矩）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(top = DsSpace.md)
                        .verticalScroll(rememberScrollState()),
                ) {
                    d.release.notes.forEach { line ->
                        Row(modifier = Modifier.padding(top = DsSpace.xs)) {
                            Text(
                                text = "·",
                                fontSize = DsType.body,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                modifier = Modifier.padding(end = DsSpace.sm),
                            )
                            Text(
                                text = line,
                                fontSize = DsType.body,
                                lineHeight = DsType.lineBody,
                                color = scheme.onBackgroundVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Text(
                    text = "下载与历史版本见 GitHub Releases（github.com，国内网络可能较慢）。",
                    fontSize = DsType.caption,
                    lineHeight = DsType.lineCaption,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = DsSpace.md),
                )
            } else {
                Text(
                    text = d.message.orEmpty(),
                    fontSize = DsType.body,
                    lineHeight = DsType.lineBody,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier
                        .padding(top = DsSpace.md)
                        .weight(1f, fill = false),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpace.xl),
                horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
            ) {
                TextButton(text = "关闭", onClick = onDismiss)
                if (d.release != null) {
                    TextButton(text = "前往下载", onClick = {
                        val url = d.release.apkUrl ?: d.release.releaseUrl
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure {
                            XLog.e("打开下载链接失败：$url", it)
                        }
                    })
                }
            }
        }
    }
}
