/*
 * 功能目录的**展示组件**（1.12.0 从 ui/FeatureCatalog.kt 拆出）。
 *
 * 与 FeatureCatalog.kt 同一包（`com.hupan.hookbrowser.ui`）—— 拆文件不拆包，
 * 所有调用点的 import 保持不变。
 *
 * 内容：开关卡 / 下拉行 / 分区标题 / 入口行 / 关于页 hero / 项目信息 / 更新日志卡 /
 * 详情四段 / 目标版本状态卡 / 状态胶囊。
 *
 * ⚠ **进程边界**：同 FeatureCatalog.kt，hook 侧禁止引用本文件。
 */

package com.hupan.hookbrowser.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hupan.hookbrowser.ui.theme.accentSoft
import com.hupan.hookbrowser.ui.theme.onAccent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊形状一律 stadium（圆角 = 高度一半），高度取 [DsCapsule] */
internal val CAPSULE_TAG_SHAPE = RoundedCornerShape(percent = 50)

// ---------------------------------------------------------------------------
// 展示组件
// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------

/**
 * **点开关 = 切换**，**点卡片其它地方 = 看详情**。
 *
 * <p>两条路径本来就是分开的：`Switch` 内部是 `toggleable`，会消费掉点击事件，
 * 点开关只切开关、不会冒泡到外层的 `clickable`；点卡片其它区域才走 `onShowDetail`。
 * （XML 时代用 androidx.preference 做不到这一点 —— 它整行的 `onClick` 就是
 * `onCheckedChange(!checked)`，1.9.4 起那套就是自绘的。）
 *
 * <p>[emphasized] 用于总开关：主色 12% 底 + 更大的标题，与普通功能卡拉开层级。
 *
 * <p>[flat] 用于「卡片组」：本卡后面还跟着自己的下拉行时，背景与圆角交给外层的
 * [EntryGroup] 统一画，本卡只出内容 —— 否则两张同色圆角卡零间距贴在一起，
 * 圆角处会连成一片，看着像两条内容挤在同一张卡里（1.15.2 修）。
 */
@Composable
internal fun ToggleCard(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onShowDetail: () -> Unit,
    emphasized: Boolean = false,
    flat: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (flat) Modifier
                else Modifier.background(
                    color = if (emphasized) accentSoft() else scheme.surfaceContainer,
                    shape = RoundedCornerShape(DsRadius.card),
                )
            )
            .clickable(enabled = enabled, onClick = onShowDetail)
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = if (emphasized) DsType.dialogTitle else DsType.title,
                    fontWeight = FontWeight.SemiBold,
                    // 停用时标题降为次要色，与 Switch 的 disabled 观感一致
                    color = if (enabled) scheme.onSurface else scheme.onBackgroundVariant,
                )
                if (!summary.isNullOrEmpty()) {
                    Text(
                        text = summary,
                        fontSize = DsType.body,
                        lineHeight = DsType.lineBody,
                        color = scheme.onBackgroundVariant,
                        modifier = Modifier.padding(top = DsSpace.xs),
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.padding(start = DsSpace.md),
            )
        }
    }
}

/**
 * 下拉项卡片行（UA 模式 / 搜索引擎）：标题 + 当前值 + 箭头，整行点击弹单选浮层。
 *
 * <p>[enabled] 为假时整行置灰且不可点 —— 母开关关着时改它没有意义。
 *
 * <p>[flat] 同 [ToggleCard]：作为卡片组的第二行时不自己画背景，
 * 由外层 [EntryGroup] + [EntryDivider] 统一成「一张卡 + 中间一条细线」。
 */
@Composable
internal fun OptionRow(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    flat: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (flat) Modifier
                else Modifier.background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = DsType.title,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) scheme.onSurface else scheme.onBackgroundVariant,
            )
            Text(
                text = value,
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.xs),
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = if (enabled) scheme.outline else scheme.onBackgroundVariant,
            modifier = Modifier.padding(start = DsSpace.sm).size(DsSpace.iconChevron),
        )
    }
}

/**
 * 大卡片布局下的分区标题：无卡片、仅一行小字。
 *
 * <p>左边缘与卡片对齐（不加水平缩进）—— 页面左右边距已由 `pageContentPadding` 统一给到，
 * 这里再缩进就会比卡片多出 8dp，看着像没对齐。
 */
@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = DsType.section,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier.padding(top = DsSpace.md, bottom = DsSpace.sectionPadBottom),
    )
}

/**
 * 入口行分组容器：一张卡片装若干 [NavEntryRow]，行与行之间用 [EntryDivider]。
 *
 * <p>存在的意义是让**一级页面只出现入口、不出现内容** —— 关于页因此不必把信息表
 * 和整份更新日志铺开，页面长度恒定。
 */
@Composable
internal fun EntryGroup(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(DsRadius.card)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** 入口行之间的细分割线：左侧从文字起始处开始，不贯穿整行。 */
@Composable
internal fun EntryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DsSpace.cardPadH)
            .height(DsSpace.dividerHeight)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

/**
 * 二级页入口行：标题（+ 可选说明）+ 右侧箭头，整行可点。
 *
 * <p>自绘而不用 miuix 的 `ArrowPreference`：那个在 miuix-preference 模块里，
 * 本工程不依赖它，不为一行入口引入整个模块。
 */
@Composable
internal fun NavEntryRow(title: String, summary: String?, onClick: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = DsType.title,
                color = scheme.onSurface,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    fontSize = DsType.body,
                    lineHeight = DsType.lineBody,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(top = DsSpace.xs),
                )
            }
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = scheme.outline,
            modifier = Modifier.padding(start = DsSpace.sm).size(DsSpace.iconChevron),
        )
    }
}

/**
 * 关于页主卡：主色 12% 底 + 模块标记 + 名称 + 实心版本胶囊 + 定位语 + 特性标签。
 *
 * <p>标记用 52dp 主色方底内嵌 26dp 白色图标，与功能页「启用模块」那张主色 hero 卡
 * 同一层级、同一种视觉语言。
 */
@Composable
internal fun AboutHero(version: String) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = accentSoft(),
                shape = RoundedCornerShape(DsRadius.card),
            )
            .padding(horizontal = DsSpace.heroPadH, vertical = DsSpace.heroPadV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(DsSpace.heroIconSize)
                    .background(scheme.primary, RoundedCornerShape(DsRadius.block)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    imageVector = MiuixIcons.Tune,
                    // 装饰性：旁边的模块名已经表达了同样的信息
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(onAccent()),
                    modifier = Modifier.size(DsSpace.heroIconSize / 2),
                )
            }
            Column(modifier = Modifier.padding(start = DsSpace.md)) {
                Text(
                    text = "小米浏览器净化",
                    fontSize = DsType.hero,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = DsSpace.sm),
                ) {
                    // 实心胶囊：hero 底本身就是 primary 12%，再用同色淡底就看不见了
                    Box(
                        modifier = Modifier
                            .height(DsCapsule.tagHeight)
                            .background(color = scheme.primary, shape = CAPSULE_TAG_SHAPE)
                            .padding(horizontal = DsCapsule.tagPadH),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "v" + version,
                            fontSize = DsType.caption,
                            fontWeight = FontWeight.Medium,
                            color = onAccent(),
                        )
                    }
                    Text(
                        text = "适配小米浏览器 $HOST_VERSION_NAME",
                        fontSize = DsType.caption,
                        color = scheme.onBackgroundVariant,
                        modifier = Modifier.padding(start = DsSpace.sm),
                    )
                }
            }
        }
        Text(
            text = "只做浏览体验的 Xposed 模块 —— 去广告、界面精简；不联网、不申请权限，" +
                "全部逻辑在本地完成。",
            fontSize = DsType.body,
            lineHeight = DsType.lineBody,
            color = scheme.onBackgroundVariant,
            modifier = Modifier.padding(top = DsSpace.md),
        )
        Row(
            modifier = Modifier.padding(top = DsSpace.md),
            horizontalArrangement = Arrangement.spacedBy(DsSpace.sm),
        ) {
            HeroChip("一个功能一个开关")
            HeroChip("改完即时生效")
            HeroChip("纯本地")
        }
    }
}

/** hero 卡里的特性标签：卡片底色胶囊，压在 accentSoft 底上刚好分层。 */
@Composable
internal fun HeroChip(text: String) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .height(DsCapsule.tagHeight)
            .background(scheme.surfaceContainer, CAPSULE_TAG_SHAPE)
            .padding(horizontal = DsCapsule.tagPadH),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = DsType.caption,
            fontWeight = FontWeight.Medium,
            color = scheme.primary,
        )
    }
}

/** 项目信息：一张卡装下全部条目，行与行之间用 1dp 细线分隔。 */
@Composable
internal fun ProjectInfoCard(version: String) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        PROJECT_INFO.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = DsSpace.md)
                        .height(DsSpace.dividerHeight)
                        .background(scheme.dividerLine),
                )
            }
            InfoRow(item, version)
        }
    }
}

/** 项目信息的一行。 */
@Composable
internal fun InfoRow(item: InfoItem, version: String) {
    val scheme = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.label,
            fontSize = DsType.label,
            color = scheme.onBackgroundVariant,
        )
        Text(
            text = item.value.replace("{moduleVersion}", version),
            fontSize = DsType.value,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = DsSpace.xs),
        )
        item.detail?.let {
            Text(
                text = it,
                fontSize = DsType.label,
                lineHeight = DsType.lineLabel,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.xs),
            )
        }
    }
}

/**
 * 更新日志的一条。
 *
 * <p>[current] 为真时（列表第一条 = 当前安装的版本）用主色底 + 主色描边
 * 与历史条目拉开层级，标题前的主色圆点同步点亮。
 *
 * <p>[collapsible] 为真时是「历史版本」：默认只显示版本号、状态胶囊与条目数，
 * 点标题行才展开正文 —— 更新日志二级页因此不会被十几条历史正文撑成几千像素长。
 * 当前版本（[current]）恒定展开且不可折叠。
 */
@Composable
internal fun ChangelogCard(
    entry: Changelog,
    current: Boolean,
    collapsible: Boolean = false,
) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(DsRadius.card)
    val foldable = collapsible && !current
    var expanded by remember(entry, foldable) { mutableStateOf(!foldable) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (current) accentSoft() else scheme.surfaceContainer,
                shape = shape,
            )
            .border(
                width = if (current) DsSpace.dividerHeight else 0.dp,
                color = if (current) scheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                shape = shape,
            )
            .clickable(enabled = foldable) { expanded = !expanded }
            .padding(horizontal = DsSpace.cardPadH, vertical = DsSpace.cardPadV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(DsSpace.sm)
                    .background(
                        color = if (current) scheme.primary else scheme.outline,
                        shape = CAPSULE_TAG_SHAPE,
                    ),
            )
            Text(
                text = entry.version,
                fontSize = DsType.title,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = DsSpace.sm),
            )
            // 收起状态下用条目数补上「里面有多少内容」的信息，避免看起来像空卡片
            if (foldable && !expanded) {
                Text(
                    text = "${entry.items.size} 项",
                    fontSize = DsType.caption,
                    color = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(end = DsSpace.sm),
                )
            }
            VersionTag(entry.tag, current)
            if (foldable) {
                Icon(
                    imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = scheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = DsSpace.xs).size(DsSpace.iconChevron),
                )
            }
        }
        if (expanded) {
            entry.items.forEach { line ->
                Row(modifier = Modifier.padding(top = DsSpace.sm)) {
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
    }
}

/**
 * 更新日志条目的状态胶囊。
 *
 * <p>[current] 为真时用实心主色 + 白字（与 hero 卡的版本胶囊同款），其余用主色淡底 + 主色字。
 */
@Composable
internal fun VersionTag(tag: String, current: Boolean) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .height(DsCapsule.tagHeight)
            .background(
                color = if (current) scheme.primary else accentSoft(),
                shape = CAPSULE_TAG_SHAPE,
            )
            .padding(horizontal = DsCapsule.tagPadH),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tag,
            fontSize = DsType.caption,
            color = if (current) onAccent() else scheme.primary,
        )
    }
}

/**
 * 详情四段。内容偏长，这里自己限高并允许纵向滚动 —— 详情浮层不滚动的话
 * 长文本会把卡片顶出屏幕。
 *
 * @param modifier 由浮层传入（通常是 `Modifier.weight(1f, fill = false)`）：
 *        让本段的限高跟着卡片剩余空间走，而不是固定 320dp。
 */
@Composable
internal fun DetailBody(detail: Detail, modifier: Modifier = Modifier) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 详情正文最高 320dp（约一屏），超出内部滚动；这不是间距令牌，是内容约束。
            // 浮层给的 weight 限制更紧时以那份为准（heightIn 取两者中的小值）。
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        DetailSection("作用", detail.purpose)
        DetailSection("Hook 目标", detail.target, mono = true)
        DetailSection("生效方式", detail.effect)
        if (detail.caveat.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DsSpace.lg)
                    // 块状内容用固定圆角（12dp），不是胶囊：块高由文字撑开，套 stadium 会变形
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(DsRadius.block))
                    .padding(DsSpace.md),
            ) {
                Text(
                    text = "注意事项",
                    fontSize = DsType.caption,
                    fontWeight = FontWeight.Medium,
                    color = scheme.primary,
                )
                Text(
                    text = detail.caveat,
                    fontSize = DsType.body,
                    lineHeight = DsType.lineBody,
                    color = scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = DsSpace.xs),
                )
            }
        }
    }
}

/** 详情里的一段：小号标签 + 正文（[mono] 用于类名 / 方法名） */
@Composable
internal fun DetailSection(label: String, body: String, mono: Boolean = false) {
    val scheme = MiuixTheme.colorScheme
    Text(
        text = label,
        fontSize = DsType.caption,
        fontWeight = FontWeight.Medium,
        color = scheme.onBackgroundVariant,
        modifier = Modifier.padding(top = DsSpace.lg),
    )
    Text(
        text = body,
        fontSize = if (mono) DsType.label else DsType.body,
        lineHeight = if (mono) DsType.lineMono else DsType.lineBody,
        fontFamily = if (mono) FontFamily.Monospace else null,
        color = if (mono) scheme.onBackgroundVariant else scheme.onBackground,
        modifier = Modifier.padding(top = DsSpace.xs),
    )
}

/**
 * 目标应用状态 —— 功能页的第一屏信息（跑起来对不对，先看这张卡）。
 *
 * <p>宿主发版频繁，而所有目标都依赖 R8 混淆名，一旦版本对不上就会「静默失效」。
 * 把版本一致性直接摆在页面顶部，用户进「功能」页第一眼就能判断是不是版本原因；
 * 这里刻意做**紧凑横排**（版本 + 状态胶囊），而不是一张带长文案的说明卡 ——
 * 状态区是「一眼确认」的，不是「需要阅读」的，说明留给详情与项目信息页。
 */
@Composable
internal fun TargetStatusCard() {
    val context = LocalContext.current
    // 读不到 ≠ 没装：Android 11+ 的包可见性（或隐藏列表类模块）都会让查询抛 NameNotFoundException，
    // 所以这里按「未检测到」呈现，见 readHostVersion 的说明。
    val targetVersion = remember(context) { readHostVersion(context) }
    val matched = targetVersion == HOST_VERSION_NAME
    val unknown = targetVersion == null
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
                text = "小米浏览器 ${targetVersion ?: "未检测到"}",
                fontSize = DsType.title,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = when {
                    matched -> "与模块适配版本一致"
                    unknown -> "可能未安装，也可能是系统限制了模块查看其它应用（不影响功能生效）"
                    else -> "模块适配 $HOST_VERSION_NAME，该版本下部分功能可能不生效"
                },
                fontSize = DsType.body,
                lineHeight = DsType.lineBody,
                color = scheme.onBackgroundVariant,
                modifier = Modifier.padding(top = DsSpace.xs),
            )
        }
        StatusPill(
            on = matched,
            onText = "版本匹配",
            offText = if (unknown) "未检测到" else "版本不符",
        )
    }
}

// ---------------------------------------------------------------------------
// 胶囊：状态标签
// ---------------------------------------------------------------------------

/**
 * 状态胶囊：开 = accent 的 12% 底 + accent 字，关 = 中性底 + 次要字。
 *
 * <p>底色与文字色在 220ms 内一起插值 —— 与番茄 `StatusPill`、多看 `Ui.setStatusPill`
 * 的 `ValueAnimator` + `ArgbEvaluator` 同一时长。
 *
 * <p>用 `animateFloatAsState` + `lerp` 而不是 `animateColorAsState`：后者要额外引
 * compose-animation 依赖，而这里只是两个颜色之间的线性插值，没必要。
 * 另外 `animateFloatAsState` 首次组合直接落到目标值，正好满足「刚打开弹窗不闪」。
 */
@Composable
internal fun StatusPill(on: Boolean, onText: String = "已开启", offText: String = "已关闭") {
    val scheme = MiuixTheme.colorScheme
    val t by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = tween(DsMotion.state, easing = LinearOutSlowInEasing),
        label = "statusPill",
    )
    val accent = scheme.primary
    val bg = lerp(scheme.surfaceContainerHighest, accentSoft(), t)
    val fg = lerp(scheme.onBackgroundVariant, accent, t)

    Box(
        modifier = Modifier
            .height(DsCapsule.tagHeight)
            .background(bg, CAPSULE_TAG_SHAPE)
            .padding(horizontal = DsCapsule.tagPadH),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = if (on) onText else offText, fontSize = DsType.caption, color = fg)
    }
}
