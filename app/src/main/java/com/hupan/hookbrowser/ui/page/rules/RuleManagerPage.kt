/*
 * 规则管理（二级页）—— 简介：导入 / 启停 / 删除自定义拦截规则集。
 *
 * 1.12.0 起本页是 Compose 二级页（原 `RuleManagerActivity`，View + Material3 实现，已删除）。
 * 为什么必须搬：模块的其余界面都是 Compose + miuix，只有这一页是 AppCompatActivity +
 * `MaterialAlertDialogBuilder`，进去观感立刻破功；而且 XML 版式那份设计令牌副本随 1.11.0
 * 的界面重建删掉了，这一页实际已经脱离三工程统一设计规范。
 *
 * 与旧实现的对应关系：
 *   - 列表 + 勾选框          → [RuleSetCard]（miuix Switch）
 *   - 「查看」MaterialAlertDialog → [RuleDetailDialog]（自绘浮层，长文本内部滚动）
 *   - 「从 URL 导入」输入框    → [UrlInputDialog]（自绘浮层 + BasicTextField）
 *   - 「删除」确认框          → 复用 ui/component/ConfirmDialog
 *   - 下载用的后台 Thread     → 协程 + `Dispatchers.IO`
 *
 * 为什么自绘浮层而不用 miuix 的 WindowDialog：详见 ui/component/DetailDialog.kt 的说明 ——
 * 模块自己的 Activity 没有 `NavigationEventDispatcherOwner`，用库里的弹窗一点就崩。
 *
 * 落盘后宿主进程会在 8 秒内自动重载（见 `CustomAdBlockFeature` 的轮询线程），
 * 所以任何改动都不需要重启浏览器 —— 页面上会明确这么告诉用户。
 */
package com.hupan.hookbrowser.ui.page.rules

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.adblock.AdRuleParser
import com.hupan.hookbrowser.adblock.AdRuleSet
import com.hupan.hookbrowser.adblock.AdRuleStore
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@Composable
internal fun RuleManagerPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    var sets by remember { mutableStateOf<List<AdRuleSet>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    /** 正在下载 / 导入：期间禁用两个导入入口，避免并发写同一个规则库 */
    var busy by remember { mutableStateOf(false) }

    var urlDialog by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<AdRuleSet?>(null) }
    var deleteTarget by remember { mutableStateOf<AdRuleSet?>(null) }

    // 首次进入读一次规则库（IO 线程，见 readLibrary）
    LaunchedEffect(Unit) {
        sets = readLibrary(context)
        loaded = true
    }

    /** 改完规则库统一收口：重读列表 + 递增修订号（让「规则」Tab 的概况卡跟着变） */
    suspend fun afterChange(message: String) {
        toast(context, message)
        sets = readLibrary(context)
        RuleLibraryState.bump()
    }

    // 系统文件选择器。用 SAF（OpenDocument）而不是路径：零存储权限，只拿到这一个 uri 的读权限
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val name = displayName(context, uri)
                val text = readText(context, uri)
                val msg = if (text == null) {
                    "导入失败：无法读取该文件"
                } else {
                    importText(context, name, "file:$name", text)
                }
                busy = false
                afterChange(msg)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "规则管理",
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
                        onImportUrl = { urlDialog = true },
                        onImportFile = {
                            // `*/*`：规则文件的 mime 五花八门（text/plain、octet-stream…），别把用户挡在外面
                            pickFile.launch(arrayOf("*/*"))
                        },
                    )
                }

                if (!loaded) {
                    item { NoteCard("正在读取规则库…", "") }
                } else if (sets.isEmpty()) {
                    item {
                        NoteCard(
                            title = "还没有导入任何规则",
                            body = "导入后浏览器会在数秒内自动生效，不需要重启，也不需要再开别的开关。",
                        )
                    }
                } else {
                    items(items = sets, key = { it.id }) { set ->
                        RuleSetCard(
                            set = set,
                            onToggle = { enabled ->
                                scope.launch {
                                    setEnabled(context, set, enabled)
                                    afterChange("已保存，浏览器将在数秒内生效")
                                }
                            },
                            onView = { detailTarget = set },
                            onDelete = { deleteTarget = set },
                        )
                    }
                }
            }

            UrlInputDialog(
                shown = urlDialog,
                busy = busy,
                onConfirm = { url ->
                    urlDialog = false
                    busy = true
                    scope.launch {
                        val (text, err) = fetchText(url)
                        val msg = if (text == null) {
                            "导入失败：$err"
                        } else {
                            importText(context, nameOfUrl(url), url, text)
                        }
                        busy = false
                        afterChange(msg)
                    }
                },
                onDismiss = { urlDialog = false },
            )

            RuleDetailDialog(
                set = detailTarget,
                onDismiss = { detailTarget = null },
            )

            val deleting = deleteTarget
            ConfirmDialog(
                shown = deleting != null,
                title = "删除规则集",
                message = deleting?.let {
                    "确认删除「${it.name}」？该规则集的 ${it.rules.size} 条规则会一并移除，不可撤销。"
                }.orEmpty(),
                confirmText = "删除",
                onConfirm = {
                    val set = deleteTarget ?: return@ConfirmDialog
                    deleteTarget = null
                    scope.launch {
                        remove(context, set)
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

/** 导入卡：两个入口 + 一句「重复导入是覆盖」的说明。 */
@Composable
private fun ImportCard(
    busy: Boolean,
    onImportUrl: () -> Unit,
    onImportFile: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Text(
            text = "导入规则集",
            fontSize = DsType.title,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Text(
            text = "支持 Adblock 语法（含 ## 元素隐藏）。同一个 URL / 文件名重复导入是覆盖，" +
                "不是堆积 —— 规则源改版后重新导入一次即可。",
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
                // 下载期间换成一行状态文字而不是两个灰掉的按钮：「正在下载并导入」
                // 信息量更大，也点不动
                Text(
                    text = "正在下载并导入…",
                    fontSize = DsType.body,
                    color = scheme.primary,
                )
            } else {
                TextButton(text = "从 URL 导入", onClick = onImportUrl)
                TextButton(text = "从本地文件导入", onClick = onImportFile)
            }
        }
    }
}

/** 一个规则集：名称 + 元信息 + 启用开关，下面一行「查看 / 删除」。 */
@Composable
private fun RuleSetCard(
    set: AdRuleSet,
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
                    text = set.name,
                    fontSize = DsType.title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (set.enabled) scheme.onSurface else scheme.onBackgroundVariant,
                )
                Text(
                    text = ruleMeta(set),
                    fontSize = DsType.label,
                    lineHeight = DsType.lineLabel,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = DsSpace.xs),
                )
            }
            Switch(
                checked = set.enabled,
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

/** 纯说明卡（空态 / 读取中），与其余卡片同一套圆角与内边距。 */
@Composable
private fun NoteCard(title: String, body: String) {
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

// ---------------------------------------------------------------------------
// 浮层
// ---------------------------------------------------------------------------

/** 从 URL 导入的输入浮层。 */
@Composable
private fun UrlInputDialog(
    shown: Boolean,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!shown) return

    BackHandler(enabled = true) { onDismiss() }

    val scheme = MiuixTheme.colorScheme
    // 每次打开都从空开始：记住上次那个 URL 只会让人误以为「这次是再导入一遍」
    var url by remember(shown) { mutableStateOf("") }

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

        // 二级页没有底栏，只避开系统导航条；卡片整体限高，内容再长也不会把按钮顶出屏幕
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
                text = "从 URL 导入规则",
                fontSize = DsType.dialogTitle,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = "粘贴 Adblock 规则文本的直链（easylist 之类的 .txt）。",
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.sm),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpace.md)
                    .heightIn(min = DsSpace.buttonHeight)
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(DsRadius.block))
                    .padding(horizontal = DsSpace.md, vertical = DsSpace.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (url.isEmpty()) {
                    Text(
                        text = "https://…/rules.txt",
                        fontSize = DsType.value,
                        color = scheme.onBackgroundVariant,
                    )
                }
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = DsType.value, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DsSpace.xl),
                horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
            ) {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(
                    text = "下载并导入",
                    onClick = {
                        val trimmed = url.trim()
                        if (trimmed.isNotEmpty()) onConfirm(trimmed)
                    },
                )
            }
        }
    }
}

/**
 * 规则明细浮层：长文本自己限高并内部滚动，否则会把卡片顶出屏幕。
 *
 * <p>这里用「[set] 为 null 即不渲染」而不是把显隐与内容分开存 —— 本页的浮层没有退场
 * 动画（不是 `AnimatedVisibility`），关掉就是关掉，不需要 ui/component/DetailDialog.kt
 * 那套「shown 与内容分开存以免关闭动画期间卡片空白」的处理。
 */
@Composable
private fun RuleDetailDialog(
    set: AdRuleSet?,
    onDismiss: () -> Unit,
) {
    if (set == null) return

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
                text = set.name,
                fontSize = DsType.dialogTitle,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = "${set.rules.size} 条拦截 · ${set.elementRules.size} 条隐藏 · ${set.source}",
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
                    text = ruleDetailText(set),
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
// 纯逻辑（全部在 Dispatchers.IO 上跑，别在主线程调）
// ---------------------------------------------------------------------------

/** 读规则库。 */
private suspend fun readLibrary(context: Context): List<AdRuleSet> =
    withContext(Dispatchers.IO) { AdRuleStore.load(context) }

/** 整组启用 / 停用。 */
private suspend fun setEnabled(context: Context, set: AdRuleSet, enabled: Boolean) {
    withContext(Dispatchers.IO) {
        val all = AdRuleStore.load(context)
        for (s in all) if (s.id == set.id) s.enabled = enabled
        AdRuleStore.save(context, all)
    }
    XLog.i("规则集「${set.name}」${if (enabled) "启用" else "停用"}")
}

/** 删除整组。 */
private suspend fun remove(context: Context, set: AdRuleSet) {
    withContext(Dispatchers.IO) {
        val all = AdRuleStore.load(context)
        all.removeAll { it.id == set.id }
        AdRuleStore.save(context, all)
    }
    XLog.i("删除规则集「${set.name}」（${set.rules.size} 条）")
}

/** 读本地文件（SAF uri）的文本内容；读不到返回 null。 */
private suspend fun readText(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val bytes = ins.readBytes()
                val cut = bytes.size.coerceAtMost(MAX_FILE_BYTES)
                String(bytes, 0, cut, Charsets.UTF_8)
            }
        }.getOrNull()
    }

/**
 * 解析 + 落盘。规则集按**来源**去重：同一个 URL / 文件重复导入是覆盖，不是堆积。
 *
 * 返回给用户看的结果文案（成功与失败都走同一条返回路径，调用方只管弹出来）。
 */
private suspend fun importText(
    context: Context,
    name: String,
    source: String,
    text: String,
): String = withContext(Dispatchers.IO) {
    val result = AdRuleParser.parse(text)
    if (result.rules.isEmpty() && result.elementRules.isEmpty()) {
        return@withContext "没有解析出可用规则：元素隐藏 ${result.elementLines} 条、不合规 ${result.invalidLines} 条。"
    }
    val all = AdRuleStore.load(context)
    all.removeAll { it.source == source }
    all.add(
        0,
        AdRuleSet(
            id = UUID.randomUUID().toString().substring(0, 8),
            name = name,
            source = source,
            updatedAt = System.currentTimeMillis(),
            enabled = true,
            rules = result.rules,
            elementRules = result.elementRules,
        ),
    )
    if (!AdRuleStore.save(context, all)) {
        return@withContext "导入失败：规则库写入失败"
    }
    val skipped = if (result.invalidLines > 0 || result.truncatedLines > 0) {
        "（另有 ${result.invalidLines} 条不合规、${result.truncatedLines} 条超限被丢弃）"
    } else {
        ""
    }
    XLog.i(
        "导入规则集「$name」：请求拦截 ${result.rules.size} 条 / 元素隐藏 ${result.elementRules.size} 条" +
            "（原文元素行 ${result.elementLines} / 不合规 ${result.invalidLines} / 超限截断 ${result.truncatedLines}）"
    )
    "已导入「$name」：请求拦截 ${result.rules.size} 条 · 元素隐藏 ${result.elementRules.size} 条$skipped"
}

/**
 * 下载规则文本。返回 `(正文, 错误描述)`，二选一为 null。
 *
 * 用 `runCatching` 而不是 try/catch 给同一个 val 赋值 —— Kotlin 的确定赋值分析不覆盖
 * try/catch，那样写会报 `Val cannot be reassigned`（这个坑工程里踩过，见 verify_static.py 第 8 项）。
 */
private suspend fun fetchText(url: String): Pair<String?, String> = withContext(Dispatchers.IO) {
    runCatching<Pair<String?, String>> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val bytes = conn.inputStream.use { it.readBytes() }
            val cut = bytes.size.coerceAtMost(MAX_FILE_BYTES)
            String(bytes, 0, cut, Charsets.UTF_8) to ""
        } finally {
            runCatching { conn.disconnect() }
        }
    }.getOrElse { e -> null to (e.message ?: e.javaClass.simpleName) }
}

/** 「查看」浮层的正文：两类规则各列前 [MAX_SHOW_RULES] 条，超出部分只报数。 */
private fun ruleDetailText(set: AdRuleSet): String = buildString {
    val show = minOf(set.rules.size, MAX_SHOW_RULES)
    for (i in 0 until show) append(set.rules[i]).append('\n')
    if (set.rules.size > show) {
        append("…\n（还有 ${set.rules.size - show} 条未显示）\n")
    }
    if (set.elementRules.isNotEmpty()) {
        append("\n—— 元素隐藏（注入 CSS）${set.elementRules.size} 条 ——\n")
        val es = minOf(set.elementRules.size, MAX_SHOW_RULES)
        for (i in 0 until es) append(set.elementRules[i]).append('\n')
        if (set.elementRules.size > es) {
            append("…\n（还有 ${set.elementRules.size - es} 条未显示）\n")
        }
    }
}

/** 从 URL 末段取个名字，取不到就用 URL 前 48 个字符。 */
private fun nameOfUrl(url: String): String {
    val seg = url.substringAfterLast('/').substringBefore('?')
    return if (seg.isNotEmpty() && seg.length <= 48) seg else url.take(48)
}

/** 从 SAF uri 取显示名，取不到就退回路径末段。 */
private fun displayName(context: Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null
            }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment ?: "本地规则文件"
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/** 单份规则文本的读取上限：超出部分截断（规则条数另受 AdRuleParser.MAX_RULES 约束） */
private const val MAX_FILE_BYTES = 512 * 1024

/** 「查看」浮层每类最多列多少条 */
private const val MAX_SHOW_RULES = 300

private const val CONNECT_TIMEOUT_MS = 15000
private const val READ_TIMEOUT_MS = 20000
private const val UA = "Mozilla/5.0 (Linux; Android) HookBrowser"
