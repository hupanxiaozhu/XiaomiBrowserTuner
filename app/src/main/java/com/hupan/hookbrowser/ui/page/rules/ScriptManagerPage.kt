/*
 * 脚本管理（二级页）—— 导入 / 启停 / 删除用户脚本（1.14.0 新增）。
 *
 * 与规则管理页（RuleManagerPage）同构，差异只有四处：
 *   1. **只支持本地文件导入**（不做 URL 导入：远程脚本 = 远程代码，供应链风险不值得）；
 *   2. 导入前有一道**确认浮层**：metadata 检出 GM_* API / document-start / 全域匹配时，
 *      把差别摆出来让用户点头（「提示式放行」，不是一刀切拒绝）；
 *   3. 脚本按**来源文件名**覆盖（同一个 .user.js 重复导入 = 更新，不是堆积）；
 *   4. 数据层是 scripts 包（UserScriptStore），与规则库（adblock 包）完全隔离。
 *
 * 落盘后宿主进程会在数秒内自动重载（UserScriptFeature 的轮询线程），不用重启浏览器。
 */
package com.hupan.hookbrowser.ui.page.rules

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.scripts.UserScript
import com.hupan.hookbrowser.scripts.UserScriptCodec
import com.hupan.hookbrowser.scripts.UserScriptMeta
import com.hupan.hookbrowser.scripts.UserScriptStore
import com.hupan.hookbrowser.ui.DsColor
import com.hupan.hookbrowser.ui.DsElevation
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.component.ConfirmDialog
import com.hupan.hookbrowser.ui.component.dialogCardMaxHeight
import com.hupan.hookbrowser.ui.component.rememberDialogBottomReserve
import com.hupan.hookbrowser.ui.navigation.LocalNavigator
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 待确认的导入：脚本已解析好，等用户在确认浮层里点头 */
private class PendingImport(
    val script: UserScript,
    val warnings: List<String>,
)

@Composable
internal fun ScriptManagerPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    var scripts by remember { mutableStateOf<List<UserScript>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    /** 正在读取 / 导入：期间禁用导入入口，避免并发写同一个脚本库 */
    var busy by remember { mutableStateOf(false) }

    var pending by remember { mutableStateOf<PendingImport?>(null) }
    var detailTarget by remember { mutableStateOf<UserScript?>(null) }
    var deleteTarget by remember { mutableStateOf<UserScript?>(null) }

    LaunchedEffect(Unit) {
        scripts = readLibrary(context)
        loaded = true
    }

    /** 改完脚本库统一收口：重读列表 + 递增修订号（让「规则」Tab 的概况卡跟着变） */
    suspend fun afterChange(message: String) {
        toast(context, message)
        scripts = readLibrary(context)
        RuleLibraryState.bump()
    }

    /** 解析完成后收口：有警告先弹确认，没警告直接落盘 */
    suspend fun offerImport(script: UserScript?, warnings: List<String>, error: String?) {
        when {
            error != null -> toast(context, error)
            script == null -> toast(context, "导入失败：脚本内容为空")
            warnings.isNotEmpty() -> pending = PendingImport(script, warnings)
            else -> afterChange(commitImport(context, script))
        }
    }

    // 系统文件选择器（SAF）：零存储权限，只拿到这一个 uri 的读权限
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val name = displayName(context, uri)
                val text = readText(context, uri)
                if (text == null) {
                    busy = false
                    toast(context, "导入失败：无法读取该文件")
                } else {
                    val (script, warnings, error) = buildScript(context, name, text)
                    busy = false
                    offerImport(script, warnings, error)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "脚本管理",
                subtitle = "导入 · 启停 · 删除",
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .pageScroll(scrollBehavior),
                contentPadding = pageContentPadding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(DsSpace.cardGap),
            ) {
                item {
                    ImportCard(
                        busy = busy,
                        onImportFile = {
                            // `*/*`：.user.js 的 mime 不固定（text/plain、octet-stream…），别挡用户
                            pickFile.launch(arrayOf("*/*"))
                        },
                    )
                }

                if (!loaded) {
                    item { LibraryNoteCard("正在读取脚本库…", "") }
                } else if (scripts.isEmpty()) {
                    item {
                        LibraryNoteCard(
                            title = "还没有导入任何脚本",
                            body = "导入油猴式 .user.js 后，打开「用户脚本」开关即可生效，" +
                                "不需要重启浏览器。",
                        )
                    }
                } else {
                    items(items = scripts, key = { it.id }) { script ->
                        ScriptCard(
                            script = script,
                            onToggle = { enabled ->
                                scope.launch {
                                    setEnabled(context, script, enabled)
                                    afterChange("已保存，浏览器将在数秒内生效")
                                }
                            },
                            onView = { detailTarget = script },
                            onDelete = { deleteTarget = script },
                        )
                    }
                }
            }

            // 导入确认：metadata 检出的差别摆清楚，用户点头才落盘
            val offer = pending
            ConfirmDialog(
                shown = offer != null,
                title = "导入脚本前确认",
                message = offer?.let {
                    "「${it.script.name}」：\n" +
                        it.warnings.joinToString("\n") { w -> "· $w" } +
                        "\n\n脚本将在你浏览的网页里执行，能力等同网页自身代码，请确认来源可信。"
                }.orEmpty(),
                confirmText = "仍要导入",
                onConfirm = {
                    val p = pending ?: return@ConfirmDialog
                    pending = null
                    busy = true
                    scope.launch {
                        val msg = commitImport(context, p.script)
                        busy = false
                        afterChange(msg)
                    }
                },
                onDismiss = { pending = null },
            )

            ScriptDetailDialog(
                script = detailTarget,
                onDismiss = { detailTarget = null },
            )

            val deleting = deleteTarget
            ConfirmDialog(
                shown = deleting != null,
                title = "删除脚本",
                message = deleting?.let {
                    "确认删除「${it.name}」？删除后浏览器数秒内停止注入，不可撤销。"
                }.orEmpty(),
                confirmText = "删除",
                onConfirm = {
                    val s = deleteTarget ?: return@ConfirmDialog
                    deleteTarget = null
                    scope.launch {
                        remove(context, s)
                        afterChange("已删除，浏览器将在数秒内生效")
                    }
                },
                onDismiss = { deleteTarget = null },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 卡片
// ---------------------------------------------------------------------------

/** 导入卡：一个入口 + 支持范围说明（把「不支持什么」也说清，少一轮误解）。 */
@Composable
private fun ImportCard(busy: Boolean, onImportFile: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Text(
            text = "导入用户脚本",
            fontSize = DsType.title,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Text(
            text = "支持油猴式 .user.js：按 @match / @include 匹配页面，页面加载完成后注入执行。" +
                "document-start 会降级为加载完成后执行；GM_* API 不支持。" +
                "同一个文件重复导入是覆盖，不是堆积。",
            fontSize = DsType.body,
            lineHeight = DsType.lineBody,
            color = scheme.onBackgroundVariant,
            modifier = Modifier.padding(top = DsSpace.sm),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DsSpace.md),
            horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                Text(
                    text = "正在读取并导入…",
                    fontSize = DsType.body,
                    color = scheme.primary,
                )
            } else {
                TextButton(text = "从本地文件导入", onClick = onImportFile)
            }
        }
    }
}

/** 一个脚本：名称 + 元信息 + 启用开关，下面一行「查看 / 删除」。 */
@Composable
private fun ScriptCard(
    script: UserScript,
    onToggle: (Boolean) -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script.name,
                    fontSize = DsType.title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (script.enabled) scheme.onSurface else scheme.onBackgroundVariant,
                )
                Text(
                    text = scriptMeta(script),
                    fontSize = DsType.label,
                    lineHeight = DsType.lineLabel,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = DsSpace.xs),
                )
            }
            Switch(
                checked = script.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(start = DsSpace.md),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DsSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
        ) {
            TextButton(text = "查看", onClick = onView)
            TextButton(text = "删除", onClick = onDelete)
        }
    }
}

// ---------------------------------------------------------------------------
// 浮层
// ---------------------------------------------------------------------------

/** 脚本明细浮层：元信息 + 源码（等宽，限高滚动）。 */
@Composable
private fun ScriptDetailDialog(
    script: UserScript?,
    onDismiss: () -> Unit,
) {
    if (script == null) return

    BackHandler(enabled = true) { onDismiss() }

    val scheme = MiuixTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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

        val reserve = rememberDialogBottomReserve()
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
                text = script.name,
                fontSize = DsType.dialogTitle,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = scriptMeta(script),
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.xs),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpace.md)
                    // 与详情浮层同一套约束：最高 320dp，超出内部滚动；
                    // fill = false 让它在卡片剩余空间不足时先被压缩，按钮不会被顶出去
                    .weight(1f, fill = false)
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = script.code,
                    fontSize = DsType.label,
                    lineHeight = DsType.lineMono,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onBackgroundVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DsSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
            ) {
                TextButton(text = "关闭", onClick = onDismiss)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 纯逻辑（IO 的都在 Dispatchers.IO 上跑，别在主线程调）
// ---------------------------------------------------------------------------

/** 读脚本库。 */
private suspend fun readLibrary(context: Context): List<UserScript> =
    withContext(Dispatchers.IO) { UserScriptStore.load(context) }

/**
 * 解析 + 体检。返回 `(脚本, 警告列表, 错误文案)`：
 * error 非空 = 拒绝导入；warnings 非空 = 弹确认后放行；两者都空 = 直接落盘。
 */
private suspend fun buildScript(
    context: Context,
    fileName: String,
    text: String,
): Triple<UserScript?, List<String>, String?> = withContext(Dispatchers.IO) {
    if (text.isBlank()) {
        return@withContext Triple(null, emptyList(), "导入失败：文件内容为空")
    }
    if (text.length > UserScriptCodec.MAX_SCRIPT_CHARS) {
        return@withContext Triple(
            null, emptyList(),
            "导入失败：脚本超过 ${UserScriptCodec.MAX_SCRIPT_CHARS / 1000}K 字符上限"
        )
    }
    val existing = UserScriptStore.load(context)
    val isUpdate = existing.any { it.source == "file:$fileName" }
    if (!isUpdate && existing.size >= UserScriptCodec.MAX_SCRIPTS) {
        return@withContext Triple(
            null, emptyList(),
            "导入失败：脚本数已达上限 ${UserScriptCodec.MAX_SCRIPTS} 个，请先删除不用的"
        )
    }
    val meta = UserScriptMeta.parse(text)
    val warnings = ArrayList<String>()
    if (!meta.hasBlock) {
        warnings.add("未找到 ==UserScript== 头：该脚本将在所有 http/https 网页执行")
    } else if (meta.matches.isEmpty() && meta.includes.isEmpty()) {
        warnings.add("没有 @match / @include：该脚本将在所有网页执行")
    }
    if (meta.runAtStart) {
        warnings.add("声明了 document-start：本模块在页面加载完成后注入（降级执行，时序与油猴不同）")
    }
    if (meta.hasGmApi) {
        warnings.add("声明了 GM_* API：本模块不支持，相关调用会报错（脚本若有降级路径可正常用）")
    }
    val script = UserScript(
        id = UUID.randomUUID().toString().substring(0, 8),
        name = meta.name ?: fileName,
        source = "file:$fileName",
        updatedAt = System.currentTimeMillis(),
        enabled = true,
        matches = meta.matches,
        includes = meta.includes,
        excludes = meta.excludes,
        code = text,
        runAtStart = meta.runAtStart,
        hasGmApi = meta.hasGmApi,
    )
    Triple(script, warnings, null)
}

/** 落盘（按来源覆盖：同一个文件重复导入 = 更新）。 */
private suspend fun commitImport(context: Context, script: UserScript): String =
    withContext(Dispatchers.IO) {
        val all = UserScriptStore.load(context)
        val replaced = all.count { it.source == script.source }
        all.removeAll { it.source == script.source }
        all.add(0, script)
        if (!UserScriptStore.save(context, all)) {
            return@withContext "导入失败：脚本库写入失败"
        }
        XLog.i("导入脚本「${script.name}」（${script.code.length} 字符，匹配 ${script.matches.size}+${script.includes.size} 条）")
        (if (replaced > 0) "已更新「${script.name}」" else "已导入「${script.name}」") +
            "，打开「用户脚本」开关后生效"
    }

/** 启用 / 停用。 */
private suspend fun setEnabled(context: Context, script: UserScript, enabled: Boolean) {
    withContext(Dispatchers.IO) {
        val all = UserScriptStore.load(context)
        for (s in all) if (s.id == script.id) s.enabled = enabled
        UserScriptStore.save(context, all)
    }
    XLog.i("脚本「${script.name}」${if (enabled) "启用" else "停用"}")
}

/** 删除。 */
private suspend fun remove(context: Context, script: UserScript) {
    withContext(Dispatchers.IO) {
        val all = UserScriptStore.load(context)
        all.removeAll { it.id == script.id }
        UserScriptStore.save(context, all)
    }
    XLog.i("删除脚本「${script.name}」")
}

/** 读本地文件（SAF uri）的文本内容；读不到返回 null。 */
private suspend fun readText(context: Context, uri: Uri): String? =
    readSafText(context, uri, MAX_FILE_BYTES)

/** 从 SAF uri 取显示名，取不到就退回路径末段。 */
private fun displayName(context: Context, uri: Uri): String =
    safDisplayName(context, uri, "本地脚本文件")

/** 列表与浮层共用的元信息行。 */
private fun scriptMeta(script: UserScript): String {
    val matchDesc = when {
        script.matches.isEmpty() && script.includes.isEmpty() -> "所有网页"
        else -> "匹配 ${script.matches.size + script.includes.size} 条"
    }
    val tags = buildList {
        if (script.hasGmApi) add("⚠ GM API")
        if (script.runAtStart) add("⚠ 降级注入")
    }
    val time = synchronized(META_FMT) { META_FMT.format(Date(script.updatedAt)) }
    val src = if (script.source.length > 48) script.source.take(48) + "…" else script.source
    return listOf(matchDesc, time, src)
        .plus(tags)
        .joinToString(" · ")
}

private fun toast(context: Context, msg: String) = libraryToast(context, msg)

private val META_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** 单份脚本文件的读取上限（与单脚本存储上限对齐，略留余量） */
private const val MAX_FILE_BYTES = 512 * 1024
