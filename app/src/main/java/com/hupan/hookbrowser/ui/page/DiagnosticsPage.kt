/*
 * 诊断（二级页）—— 模块跑起来对不对，来这一页看。
 *
 * 三件事：
 *   1. 框架服务连上没有（**没连上 = 界面上改的开关写不进宿主读的那份数据**，
 *      症状同样是「改了不生效」，但不报错，所以必须摊开）；
 *   2. 开关是怎么跨进程过去的（本地 SP → 框架服务 → 框架数据库 → 宿主 getRemotePreferences）；
 *   3. 一键把当前状态复制到剪贴板，反馈问题时直接粘给作者。
 *
 * 运行日志不在这里：模块自己的日志打在 Xposed / LSPosed 的日志里（过滤 HookBrowser），
 * 本模块没有能力把宿主日志捞进自己的进程。
 */
package com.hupan.hookbrowser.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.ui.DsRadius
import com.hupan.hookbrowser.ui.DsSpace
import com.hupan.hookbrowser.ui.DsType
import com.hupan.hookbrowser.ui.HOST_PACKAGE
import com.hupan.hookbrowser.ui.HOST_VERSION_NAME
import com.hupan.hookbrowser.ui.StatusPill
import com.hupan.hookbrowser.ui.moduleVersion
import com.hupan.hookbrowser.ui.navigation.LocalNavigator
import com.hupan.hookbrowser.ui.utils.pageContentPadding
import com.hupan.hookbrowser.ui.utils.pageScroll
import kotlinx.coroutines.delay
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
internal fun DiagnosticsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val version = remember(context) { moduleVersion(context) }

    // 服务是异步连上的（框架把 binder 送进模块进程），所以进来后轮询几秒把状态刷成终值
    var bound by remember { mutableStateOf(ModuleService.isBound) }
    var framework by remember { mutableStateOf(ModuleService.boundFramework) }
    LaunchedEffect(Unit) {
        repeat(10) {
            bound = ModuleService.isBound
            framework = ModuleService.boundFramework
            if (bound) return@LaunchedEffect
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "诊断",
                subtitle = "框架服务与开关同步",
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
                    ServiceCard(
                        bound = bound,
                        framework = framework,
                    )
                }

                item {
                    NoteCard(
                        title = "开关是怎么同步到浏览器的",
                        body = "界面上改开关 → 写进模块自己的 SP（组 settings）→ 经框架服务" +
                            "全量镜像到框架数据库 → 浏览器进程用 XposedInterface#getRemotePreferences" +
                            "直读那份数据。全程没有文件权限这一环，所以「改了不生效」只有两种可能：" +
                            "框架服务没连上（看上面那张卡），或者开关值本身没改。",
                    )
                }

                item {
                    NoteCard(
                        title = "日志在哪看",
                        body = "模块的日志打在 Xposed / LSPosed 的日志里，过滤 HookBrowser。" +
                            "详细日志（挂载细节、【诊断】【采样】【注入】明细）由「浏览器调试模式」" +
                            "开关控制，默认静默；异常与关键生命周期始终输出。",
                    )
                }

                item {
                    CopyCard(
                        onClick = { copyDiagnostics(context, version, bound, framework) },
                    )
                }
            }
        }
    }
}

/** 框架服务连接状态：一眼确认「界面上改的开关能不能写进宿主读的那份数据」。 */
@Composable
private fun ServiceCard(bound: Boolean, framework: String?) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (bound) {
                    "框架服务已连接（${framework ?: "未知框架"}）"
                } else {
                    "框架服务未连接"
                },
                fontSize = DsType.title,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = if (bound) {
                    "开关改动会即时同步到浏览器"
                } else {
                    "开关改动不会同步到宿主 —— 确认 LSPosed 已启用本模块并勾选作用域"
                },
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.xs),
            )
        }
        StatusPill(on = bound, onText = "已连接", offText = "未连接")
    }
}

/** 一段说明（标题 + 正文），与卡片同一套圆角与内边距。 */
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
        Text(
            text = body,
            fontSize = DsType.body,
            lineHeight = DsType.lineBody,
            color = scheme.onBackgroundVariant,
            modifier = Modifier.padding(top = DsSpace.sm),
        )
    }
}

/** 一键复制：版本 + 服务状态 + 全部开关当前值。 */
@Composable
private fun CopyCard(onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Text(
            text = "复制当前状态",
            fontSize = DsType.title,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Text(
            text = "把模块版本、框架服务状态与全部开关值拼成一段文本放到剪贴板，反馈问题时直接粘贴。",
            fontSize = DsType.body,
            lineHeight = DsType.lineBody,
            color = scheme.onBackgroundVariant,
            modifier = Modifier.padding(top = DsSpace.sm),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DsSpace.md),
            horizontalArrangement = Arrangement.spacedBy(DsSpace.md, Alignment.End),
        ) {
            TextButton(text = "复制", onClick = onClick)
        }
    }
}

private fun copyDiagnostics(
    context: Context,
    version: String,
    bound: Boolean,
    framework: String?,
) {
    val hostVersion = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(HOST_PACKAGE, 0).versionName
    }.getOrNull() ?: "未安装"

    val text = buildString {
        appendLine("模块版本：$version")
        appendLine("适配宿主：$HOST_VERSION_NAME")
        appendLine("已装宿主：$hostVersion")
        appendLine("框架服务：${if (bound) "已连接（${framework ?: "未知"}）" else "未连接"}")
        appendLine()
        append(ToggleState.dump(context))
    }

    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (cm == null) {
        Toast.makeText(context, "复制失败：拿不到剪贴板服务", Toast.LENGTH_SHORT).show()
        return
    }
    cm.setPrimaryClip(ClipData.newPlainText("HookBrowser 诊断", text))
    Toast.makeText(context, "已复制当前状态", Toast.LENGTH_SHORT).show()
}
