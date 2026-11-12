/*
 * 首次打开提示（2.0.0）—— 一次性说明「这个版本删掉了什么」。
 *
 * 为什么需要它：2.0.0 把模块自带的拦截规则引擎与用户脚本整块撤掉了。用户升级后会发现
 * 「规则」Tab 消失、以前导入的规则与脚本不再生效，而界面上没有任何解释 —— 那看起来
 * 就像功能坏了。这条提示只在首次打开该版本时出现一次，之后不再打扰。
 *
 * 为什么不用系统 Dialog：见 AGENTS 的 UI 铁律 —— miuix 的 WindowDialog 缺
 * NavigationEventDispatcherOwner 会崩，浮层一律自绘 Box 叠层。
 */
package com.hupan.hookbrowser.ui.component

import android.content.Context
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.DsColor
import com.hupan.hookbrowser.ui.DsElevation
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「首次打开」标记的读写（纯模块侧，不进 Config —— 它不需要跨进程给宿主读）。
 *
 * <p>单独一个 SP 组、单独一个 key：模块升级到这里只弹一次，之后再打开本应用不再出现。
 */
internal object FirstRunNotice {

    private const val PREFS = "notice"
    private const val KEY_SEEN_200 = "seen_200"

    /** 是否还没看过 2.0.0 的说明 */
    fun shouldShow(context: Context): Boolean = runCatching {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        !sp.getBoolean(KEY_SEEN_200, false)
    }.getOrDefault(false)

    fun markSeen(context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SEEN_200, true).apply()
        }
    }
}

/** 一条小标题 + 正文（本浮层内部用，与详情页那套 DetailSection 分开） */
@Composable
private fun NoticeSection(title: String, body: String, mono: Boolean = false) {
    val scheme = MiuixTheme.colorScheme
    Text(
        text = title,
        fontSize = DsType.caption,
        fontWeight = FontWeight.Medium,
        color = scheme.onBackgroundVariant,
        modifier = Modifier.padding(top = DsSpace.lg),
    )
    Text(
        text = body,
        fontSize = DsType.body,
        lineHeight = DsType.lineBody,
        fontFamily = if (mono) FontFamily.Monospace else null,
        color = scheme.onBackground,
        modifier = Modifier.padding(top = DsSpace.xs),
    )
}

/**
 * 2.0.0 的改动说明浮层。
 *
 * @param bottomInset 底栏高度（详见 [rememberDialogBottomReserve]）
 * @param onDismiss 用户点「知道了」/ 遮罩 / 返回键
 */
@Composable
internal fun FirstRunNoticeDialog(
    onDismiss: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
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
        val reserve = rememberDialogBottomReserve(bottomInset)
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
                .heightIn(max = DsDialogMaxHeight)
                .shadow(DsElevation.dialog, RoundedCornerShape(DsRadius.card))
                .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
                .padding(DsSpace.dialogPadH),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "2.0.0：拦截规则与用户脚本已撤掉",
                    fontSize = DsType.dialogTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "小米浏览器 20.32 起支持安装扩展，去广告和加功能交给扩展做更合适 —— " +
                        "模块里那套规则引擎与脚本通道因此整块删除：安装包小约 0.5 MB，" +
                        "浏览器进程里也不再常驻十几 MB 的规则索引。",
                    fontSize = DsType.body,
                    lineHeight = DsType.lineBody,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = DsSpace.md),
                )
                NoticeSection(
                    title = "你现在需要知道的",
                    body = "以前导入的规则集与用户脚本不再生效，也不占空间了；" +
                        "要它们请到浏览器里装扩展（uBlock Origin 这类）。",
                )
                NoticeSection(
                    title = "没变的部分",
                    body = "开屏广告、首页推广位、搜索推荐广告、错误页热搜、下载推广、UA、搜索引擎、" +
                        "快捷方式行数这些仍然照旧，开关一个没少。",
                )
                NoticeSection(
                    title = "为什么这样更稳",
                    body = "规则引擎要同时管解析、分片、跨进程镜像与内存占用，是最容易出问题的一块；" +
                        "删掉之后模块只剩「改宿主判定值」这类轻量 hook，行为更可预期。",
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DsSpace.xl),
                horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
            ) {
                TextButton(text = "知道了", onClick = onDismiss)
            }
        }
    }
}

/** 浮层卡片最大高度：留出底栏与顶部安全距离，内容超出时卡片内部滚动 */
private val DsDialogMaxHeight = 520.dp
