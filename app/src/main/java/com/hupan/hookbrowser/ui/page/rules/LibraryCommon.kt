/*
 * 规则管理页与脚本管理页（1.14.0）共用的零件。
 *
 * 两个管理页同构（列表 + 导入卡 + 启停 + 删除 + 明细浮层），1.14.0 写脚本管理页时
 * 把逐字重复的几段收拢到这里：说明卡、SAF 读文本、SAF 取显示名、toast。
 * 浮层骨架不抽 —— 两页的明细内容差异大，硬抽只会多一层参数。
 */
package com.hupan.hookbrowser.ui.page.rules

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 纯说明卡（空态 / 读取中），与其余卡片同一套圆角与内边距。 */
@Composable
internal fun LibraryNoteCard(title: String, body: String) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Text(
            text = title,
            fontSize = DsType.title,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        if (body.isNotEmpty()) {
            Text(
                text = body,
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.sm),
            )
        }
    }
}

/** 读 SAF uri 的文本内容（截到上限）；读不到返回 null。 */
internal suspend fun readSafText(context: Context, uri: Uri, maxBytes: Int): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val bytes = ins.readBytes()
                val cut = bytes.size.coerceAtMost(maxBytes)
                String(bytes, 0, cut, Charsets.UTF_8)
            }
        }.getOrNull()
    }

/** 从 SAF uri 取显示名，取不到就退回路径末段。 */
internal fun safDisplayName(context: Context, uri: Uri, fallback: String): String {
    val fromProvider = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null
            }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment ?: fallback
}

/** 管理页统一的结果提示（导入 / 启停 / 删除都走长 toast） */
internal fun libraryToast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
