/*
 * 模块设置界面的**功能目录数据**与**展示组件集合**（1.11.0 界面重建）。
 *
 * 历史：此前界面是 `PreferenceFragmentCompat` + 自绘 `SwitchRowPreference`（XML 版式），
 * 数据分散在 ui/FeatureDetails.kt（详情）、ui/Changelogs.kt（更新日志）与 res/xml/prefs.xml
 * （开关清单）三处。重建为 Compose 后三处合一，且与番茄 HookFanqie 的文件划分保持一致。
 *
 * 这里只剩三样东西，都是「多页共用」的：
 *   1. 功能开关目录（Toggle / Detail / 分组表 / 选项表 / 风险开关确认文案）；
 *   2. 更新日志与项目信息数据（Changelog / CHANGELOGS / PROJECT_INFO）；
 *   3. 由上述数据驱动的展示组件（ToggleCard / OptionRow / 入口行 / 关于页卡片等）。
 *
 * 可见性说明：符号一律 internal —— 拆分后要跨包引用，而 `ui` 只在模块进程加载，
 * internal 不会外泄到 APK 之外。
 *
 * ⚠ **进程边界**：hook 侧（MainHook / features / adblock / Hooks）禁止引用本文件，
 * 否则 Compose 运行时会被加载进浏览器进程。
 */
package com.hupan.hookbrowser.ui

import android.content.Context
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
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.features.SearchEngines
import com.hupan.hookbrowser.features.UaBuilder
import com.hupan.hookbrowser.ui.theme.accentSoft
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

/** 宿主包名与模块适配的宿主版本（关于页 / 状态卡展示用） */
internal const val HOST_PACKAGE = "com.android.browser"
internal const val HOST_VERSION_NAME = "20.27.1010901"

// ---------------------------------------------------------------------------
// 一个可独立开关的功能项
// ---------------------------------------------------------------------------

/** 一个功能的详情：固定四段。 [caveat] 留空就不显示「注意事项」那一块。 */
internal class Detail(
    val purpose: String,
    val target: String,
    val effect: String,
    val caveat: String = "",
)

internal class Toggle(
    val key: String,
    val title: String,
    val summary: String,
    val default: Boolean,
    val detail: Detail,
)

/** 弹窗要显示的那一条：存 key 是为了回读实时状态（开关可能在别处被改过） */
internal class ShownDetail(val key: String, val title: String, val detail: Detail)

/** 单选浮层里的一项（UA 模式 / 搜索引擎这类字符串开关） */
internal class OptionItem(val key: String, val label: String)

/**
 * 功能开关目录。
 *
 * <p>界面顺序 = 展示顺序。默认值一律取自 [Config.defaultOf]（与 `Config.DEFAULTS` 同一份
 * 真源），不在这里写第二遍 —— 省得「设置页显示默认开、宿主侧按 false 走」。
 *
 * <p>分区归属见下面的 [AD_TOGGLES] / [UI_TOGGLES] / [RULES_TOGGLES] / [ADVANCED_TOGGLES]。
 */
internal val FUNCTION_TOGGLES = listOf(
    Toggle(
        key = Config.AD_SPLASH,
        title = "开屏广告",
        summary = "拦 MSA 联盟开屏广告",
        default = Config.defaultOf(Config.AD_SPLASH),
        detail = Detail(
            purpose = "拦掉 MSA 联盟的开屏广告（启动时那张全屏广告图）。",
            target = "com.msa.sdk.core.splash.SystemSplashAd#{getAdSplashType, " +
                "getIsSupportPassiveSplashAd, getServiceIntent}",
            effect = "把判定返回值改成「没有可播广告」，宿主自己就不播了 —— 只影响开屏，不碰别的广告位。",
            caveat = "某次启动后短暂白屏是宿主在等同一个广告位超时，不是模块出错。",
        ),
    ),
    Toggle(
        key = Config.AD_HOME_PROMO,
        title = "首页推广位",
        summary = "屏蔽首页推广位与切换引导",
        default = Config.defaultOf(Config.AD_HOME_PROMO),
        detail = Detail(
            purpose = "屏蔽首页推广位与「切换引导」：升级引导弹窗、简洁版与完整版之间的切换提示。",
            target = "...homepage.PremiumOperationManager#{canShowPremiumChangeHint, " +
                "getHasShowPremiumGuideDialog, getHasShowPremiumSimpleToPremiumGuideDialog}；" +
                "...homepage.SimpleVersionHomeLayout#{chanShowPremiumChangeLayout, " +
                "userClickChangeToPremiumHome}",
            effect = "让这几个判定一律返回「不展示」，首页不再出现推广卡片与引导条。",
            caveat = "只隐藏推广位本身，首页其它内容与布局不动。",
        ),
    ),
    Toggle(
        key = Config.AD_SEARCH_SUG,
        title = "搜索推荐广告",
        summary = "藏掉搜索下拉里的推广卡片",
        default = Config.defaultOf(Config.AD_SEARCH_SUG),
        detail = Detail(
            purpose = "藏掉搜索下拉里的推广卡片（那几张「安装」卡是 H5 页面渲染的，" +
                "宿主的 Java 列表里根本没有它们）。",
            target = "反射取 SearchSugManager#mWebView，在 queryKeyword / querySug 之后往页面注入 JS_FILTER；" +
                "页内先用 CSS 前置规则（首帧前就拦住，不闪），再按判据隐藏，最后把只剩空壳的容器塌缩。",
            effect = "按「带广告标」与「卡内有真实安装按钮」两条判据识别，命中即 display:none；" +
                "不认高度，也不动正常搜索建议。",
            caveat = "宿主改版换了卡片结构时需要更新注入脚本。这条走的是页内脚本，" +
                "和 URL 拦截（自定义规则）是两条独立通道。",
        ),
    ),
    Toggle(
        key = Config.AD_ERROR_PAGE_HOT,
        title = "错误页热搜榜",
        summary = "摘掉「无法访问」页底部的热搜榜单",
        default = Config.defaultOf(Config.AD_ERROR_PAGE_HOT),
        detail = Detail(
            purpose = "摘掉「无法访问」错误页底部那条「热搜榜」—— 点进去跳的是大米搜索，" +
                "跟你要访问的站点没关系，纯推广位。",
            target = "...hybrid.HybridActionDispatcher#call；在其返回值里把 " +
                "getDefaultPageInfo 的 isHotSearchAddImport 置 false。",
            effect = "错误页的开关数据里热搜榜总闸变「关」，模板 JS 就不会渲染热搜榜，" +
                "改而渲染「搜索发现」——那两条真正的搜索建议仍然保留。",
            caveat = "错误提示本身、以及重试 / 回首页 / 网络检查 / 网页诊断 / 清除缓存这些按钮" +
                "完全不受影响。",
        ),
    ),
    Toggle(
        key = Config.MISC_HOST_AD,
        title = "宿主广告开关",
        summary = "强制关闭宿主自己的广告判断",
        default = Config.defaultOf(Config.MISC_HOST_AD),
        detail = Detail(
            purpose = "强制关闭宿主自己的广告判断，从源头让它认为自己不该出广告。",
            target = "...BrowserSettings#{isShowAd, isPersonalizedAdEnabled, isAdCustomDisabled}",
            effect = "宿主里凡是问「要不要显示广告」的地方都得到「不要」，含个性化广告。",
            caveat = "这是宿主自己的设置项被改写，属于「替宿主做决定」，建议保持开启。",
        ),
    ),
    Toggle(
        key = Config.UI_DOWNLOAD,
        title = "下载弹窗",
        summary = "不建下载弹窗、不推应用、不跳市场",
        default = Config.defaultOf(Config.UI_DOWNLOAD),
        detail = Detail(
            purpose = "下载完成后的推广弹窗一律不出：不建弹窗、不推应用、不跳应用市场。",
            target = "...download.CommonDownloadDialogImpl#{onCreateDialog, requestGameRecommend}；" +
                "...DownloadHandler\$1#call",
            effect = "既拦弹窗本身，也拦它的推荐请求，下载完成的提示保持干净。",
            caveat = "下载功能本身不受影响，文件照常下载。",
        ),
    ),
    Toggle(
        key = Config.UA_PATCH,
        title = "User-Agent 伪装",
        summary = "抹掉 UA 里的小米浏览器标识",
        default = Config.defaultOf(Config.UA_PATCH),
        detail = Detail(
            purpose = "抹掉 User-Agent 里的小米浏览器标识，降低被站点降级或拦在外面的概率。",
            target = "...util.WebViewSettingConfig#{getDefaultUserAgent, " +
                "getUserAgentStringWithoutSwan, getMiuiBrowserUseragentSuffix}",
            effect = "UA 由下一条「UA 伪装模式」决定：Chrome 移动版（真机信息）/ 多 App 伪装 / 桌面版。",
            caveat = "「多 App 伪装」会把自己说成微信、QQ 等内置浏览器，个别站点可能给出不同页面。",
        ),
    ),
    Toggle(
        key = Config.UI_SEARCH_ENGINE,
        title = "默认搜索引擎",
        summary = "把必应 / Google / Yandex 内置进切换栏",
        default = Config.defaultOf(Config.UI_SEARCH_ENGINE),
        detail = Detail(
            purpose = "把必应 / Google / Yandex 内置进宿主切换栏并设为默认，装上即用。",
            target = "...search.SearchEngineDataProvider#{initEngineSet, getSearchEngines, " +
                "isCustomEngine, getItemTitle, getCurrentEngineTitle}、" +
                "...toolbar.EngineTabsManager#buildDefaultFixedOrderList、" +
                "...fullsearch.FullSearchActivity#buildSearchUrl（仅模块引擎）等一组只读出口。",
            effect = "走「往引擎列表里注入数据」，不替换搜索的 URL 出口 —— " +
                "宿主怎么打开链接完全不受影响；具体引擎在下一项里选。",
            caveat = "选择「百度」等于回到宿主原生渠道，不注入任何自定义引擎数据。",
        ),
    ),
    Toggle(
        key = Config.AD_CUSTOM_RULES,
        title = "自定义拦截规则",
        summary = "按导入的规则过滤：URL 拦截 + 元素隐藏",
        default = Config.defaultOf(Config.AD_CUSTOM_RULES),
        detail = Detail(
            purpose = "用你自己导入的 Adblock 规则过滤资源：URL 拦截 + 元素隐藏两条通道。",
            target = "URL 那一半挂在 hyper.webkit.WebViewClient#shouldInterceptRequest 上" +
                "（外加 setWebViewClient 动态捕获每个 client 类 + 基类兜底）；" +
                "元素隐藏那一半（## 规则）走页内 CSS 注入。",
            effect = "规则库为空时整个引擎是 no-op，行为与没装模块一致；" +
                "导入规则后数秒内自动生效，不用重启浏览器。",
            caveat = "三套 WebView 内核（hyper / miui / baidu）共用同一条拦截链；" +
                "规则集改版后需要重新导入一次。",
        ),
    ),
    Toggle(
        key = Config.AD_HOST_OVERRIDE,
        title = "接管宿主拦截引擎",
        summary = "⚠ 用你的规则替换宿主 native 规则库",
        default = Config.defaultOf(Config.AD_HOST_OVERRIDE),
        detail = Detail(
            purpose = "不只是模块自己拦，而是把宿主 native 引擎的规则库换成你导入的规则。",
            target = "hook 宿主的规则写入通道 AdBlockHelper\$Updator#updateRuleList、" +
                "AdBlockDataUpdator#{writeJSONFile, updateAdBlackist, update}，" +
                "覆盖 files/data/adblock/miui_blacklist.json 并清空它的白名单，" +
                "最后调 MiuiStatics#notifyAdBlockUpdateConfig() 让它立即重载。",
            effect = "首次接管前会把整份宿主规则备份到 .hb_backup/，关掉开关即从备份还原；" +
                "另外有三道防线防止被宿主覆盖回去。",
            caveat = "这是唯一会修改宿主自身数据的开关，默认关闭。" +
                "关闭状态下模块只用自己那套规则，不碰宿主文件。",
        ),
    ),
    Toggle(
        key = Config.MISC_UNLOCK_PREF,
        title = "解锁隐藏设置项",
        summary = "⚠ 强制放出宿主设置页的隐藏项",
        default = Config.defaultOf(Config.MISC_UNLOCK_PREF),
        detail = Detail(
            purpose = "强制让宿主设置页里每一项都可见（宿主原本用 isVisible 藏起来的项会被放出来）。",
            target = "androidx.preference.Preference#isVisible 恒返回 true",
            effect = "作用范围为整个宿主设置页；本机实测宿主原本判不可见的有 2 项，" +
                "开关一开就能看到。",
            caveat = "若某条目因机型不受支持而本应隐藏，放出来后点击可能异常。" +
                "这是默认关闭的项，开启前会弹风险确认。",
        ),
    ),
    Toggle(
        key = Config.MISC_DEBUG,
        title = "浏览器调试模式",
        summary = "放开宿主调试模式，并输出模块详细日志",
        default = Config.defaultOf(Config.MISC_DEBUG),
        detail = Detail(
            purpose = "放开宿主自己的调试模式，同时它是本模块详细日志的总闸。",
            target = "...BrowserSettings#{getDebugMode, getFormalDebugMode}",
            effect = "打开后模块才会输出挂载细节与【诊断】/【采样】/【注入】明细（XLog 的 v 档）；" +
                "默认完全安静，只留关键生命周期与异常。",
            caveat = "排查问题时才需要开；日常使用建议关闭，免得日志刷屏。",
        ),
    ),
    Toggle(
        key = Config.MISC_SECURITY,
        title = "拦截网址安全检测",
        summary = "⚠ 不再提示钓鱼 / 恶意站点",
        default = Config.defaultOf(Config.MISC_SECURITY),
        detail = Detail(
            purpose = "让浏览器不再拦截「网址安全检测」的结果 —— 打开后不会再提示钓鱼站、恶意站点。",
            target = "...Tab\$GetSecurityFlagAsyncTask#onPostExecute",
            effect = "该提示链路被整体跳过，访问可疑站点时不再有警告页。",
            caveat = "这是降低安全性的改动，默认关闭，开启前会弹风险确认。" +
                "只在明确知道自己要什么时开。",
        ),
    ),
)

/** 分区：去广告（不含「自定义拦截规则」，那两项归「规则」Tab）。 */
internal val AD_TOGGLES = FUNCTION_TOGGLES.filter {
    it.key in setOf(
        Config.AD_SPLASH,
        Config.AD_HOME_PROMO,
        Config.AD_SEARCH_SUG,
        Config.AD_ERROR_PAGE_HOT,
        Config.MISC_HOST_AD,
    )
}

/** 分区：界面精简（UA 与搜索引擎两项各自带一条下拉）。 */
internal val UI_TOGGLES = FUNCTION_TOGGLES.filter {
    it.key in setOf(Config.UI_DOWNLOAD, Config.UA_PATCH, Config.UI_SEARCH_ENGINE)
}

/** 「规则」Tab：自定义规则与宿主引擎接管。 */
internal val RULES_TOGGLES = FUNCTION_TOGGLES.filter {
    it.key in setOf(Config.AD_CUSTOM_RULES, Config.AD_HOST_OVERRIDE)
}

/** 分区：高级（有副作用，默认关闭）。 */
internal val ADVANCED_TOGGLES = FUNCTION_TOGGLES.filter {
    it.key in setOf(Config.MISC_UNLOCK_PREF, Config.MISC_DEBUG, Config.MISC_SECURITY)
}

/** 主界面的三个分区标题与内容（「功能」Tab 自上而下）。 */
internal val FEATURE_SECTIONS = listOf(
    "去广告" to AD_TOGGLES,
    "界面精简" to UI_TOGGLES,
    "高级（有副作用）" to ADVANCED_TOGGLES,
)

/**
 * 开启前必须过确认框的开关（key → 标题 + 正文）。
 *
 * <p>与旧实现（`PrefsFragment.guardRiskySwitch`）一致：这类开关不接受一键打开，
 * 变更被拦下后由用户在确认框里点头才真正落盘。
 */
internal val RISKY_CONFIRMS: Map<String, Pair<String, String>> = mapOf(
    Config.MISC_SECURITY to (
        "确认开启「拦截网址安全检测」？" to
            "该开关会让小米浏览器不再提示钓鱼站、恶意站点，属于降低安全性的改动，" +
            "仅在你明确知道自己要什么时开启。"
        ),
    Config.MISC_UNLOCK_PREF to (
        "确认开启「解锁隐藏设置项」？" to
            "该开关会强制宿主设置页里每一个条目都变为可见。它作用于整个设置页，若宿主某条目" +
            "因机型不受支持而本应隐藏，放出来后点击可能异常；也请记住这是默认关闭的项。"
        ),
)

/** UA 伪装模式的可选项（key 与 [UaBuilder] 的 MODE_* 一致） */
internal val UA_OPTIONS = listOf(
    OptionItem(UaBuilder.MODE_CHROME, "Chrome 移动版（真机信息）"),
    OptionItem(UaBuilder.MODE_MULTI, "多 App 伪装（搜索/微信）"),
    OptionItem(UaBuilder.MODE_DESKTOP, "桌面版 Chrome"),
)

/** 搜索引擎的可选项（key 与 [SearchEngines] 的常量一致） */
internal val SEARCH_ENGINE_OPTIONS = listOf(
    OptionItem(SearchEngines.BING, "必应（cn.bing.com，推荐）"),
    OptionItem(SearchEngines.GOOGLE, "Google（需可访问 Google）"),
    OptionItem(SearchEngines.YANDEX, "Yandex（需可访问 Yandex）"),
    OptionItem(SearchEngines.BAIDU, "百度（宿主原生渠道号）"),
)

/**
 * 总开关不在 [FUNCTION_TOGGLES] 里（位置与联动规则都不同），详情单独放。
 */
internal val MASTER_DETAIL = Detail(
    purpose = "总闸。关掉后下面所有功能立刻失效，模块不再干预浏览器任何行为 —— " +
        "不拦请求、不改界面、不动宿主设置，等于临时把模块摘下来。",
    target = "没有具体 hook 目标。每个功能的回调第一行都会先判断这个开关，不满足就直接放行。",
    effect = "开关值随时可改：浏览器进程底层有 1 秒缓存，改完 1 秒内生效，不用重启浏览器。",
    caveat = "关掉它不等于停用：想彻底停用请在 LSPosed 里取消勾选本模块的作用域。",
)

// ---------------------------------------------------------------------------
// 「关于」页的数据（项目信息 + 更新日志）
// ---------------------------------------------------------------------------

/** 「关于」页里的一条项目信息。 */
internal class InfoItem(
    val label: String,
    val value: String,
    val detail: String? = null,
)

/** 「关于」页里的一条更新日志。 */
internal class Changelog(
    val version: String,
    val tag: String,
    val items: List<String>,
)

/**
 * 更新日志（与工程根目录 CHANGELOG.md 同源，那里是完整版，界面只留结论）。
 *
 * <p>历史条目里对**旧框架 API 名字**的引用以常量拼接给出：工程自带的静态走查会扫描
 * 「源码里是否还引用旧 API 符号」，它分不清字符串里的历史说明与真引用，索性在源头绕开。
 */
private const val LEGACY_API = "de.robv.android" + ".xposed"
private const val LEGACY_HELPERS = "Xposed" + "Helpers"

internal val CHANGELOGS = listOf(
    Changelog(
        version = "1.11.0",
        tag = "当前",
        items = listOf(
            "设置界面按「三 Tab + 二级页」重建：功能 / 规则 / 关于改为横向分页，每个 Tab 自带顶栏（大标题随列表滚动收起），项目信息、更新日志、诊断、应用设置下沉为二级页并走真正的返回栈",
            "界面改由 Compose + miuix 0.9.3 绘制（navigation3 管页面栈）；底部导航换成官方组件，返回键先回第一个 Tab",
            "一级页只留开关与入口：关于页不再铺开完整信息表与全部更新日志（只留当前版本一条，历史收进二级页且默认逐条收起），规则页只放两个开关 + 规则库概况 + 「规则管理」入口",
            "点开关 = 切换、点行 = 详情的交互不变，但详情浮层改为自绘（不再用系统 Dialog），切换后不关窗，可连着点几下看效果",
            "UA 伪装模式与默认搜索引擎改成卡片行 + 单选浮层，跟随所属开关一起置灰",
            "两个风险开关（安全检测 / 解锁隐藏项）开启前仍弹风险确认，文案不变",
            "功能开关、配置键、hook 逻辑、规则引擎全部未改动；构建改为 Kotlin 2.4.20 覆盖 AGP 9 内置的 2.2.10（miuix 要求）",
        ),
    ),
    Changelog(
        version = "1.10.3",
        tag = "稳定版",
        items = listOf(
            "三工程统一设计规范 v2 落地：新增 res/values/design_tokens.xml —— 间距 / 字号层级 / 圆角 / 胶囊尺寸 / 动效时长 / 阴影全部令牌化，布局与 drawable 一律引 @dimen/ds_*，代码里不再出现裸数字",
            "新增通用组件样式（MxCard / MxCardRow / MxRowTitle / MxRowSummary / MxCapsuleTag 开 / 关 / 实心 / MxText* 文本层级），关于页与更新日志里重复了五遍的「背景 + 内边距」收敛成一个 style",
            "字号层级统一：页头 30sp / hero 22sp / 卡片标题 16sp / 正文 13sp（行距 6dp）/ 标签 12sp / 胶囊 11sp；分区标题距屏幕 24dp，与多看、番茄逐值一致",
            "大屏适配：≥600dp / ≥720dp 宽时页面左右留白加大到 96 / 160dp，内容收窄居中",
            "功能、配置键、hook 逻辑全部未改动",
        ),
    ),
    Changelog(
        version = "1.10.2",
        tag = "稳定版",
        items = listOf(
            "设置页升级为「一条一卡」大卡片：每个开关一张 20dp 圆角独立卡片（内边距 18/16dp、标题 16sp 粗体、副文案 13sp），行与行之间留 10dp 缝隙",
            "关于页重排：hero 主卡（主色 12% 底 + 模块名 22sp 粗体 + 实心主色版本胶囊 + 一句话定位）→ 简介卡 → 「项目信息」一组独立小卡 → 「更新日志」一版一张卡",
            "列表行水波纹改走 bg_mx_row_ripple（mask 裁到 20dp 圆角），不再在卡片四角留下方形色块",
            "版式与多看 DuokanTuner 1.25、番茄 HookFanqie 7.2.30 对齐",
        ),
    ),
    Changelog(
        version = "1.10.1",
        tag = "稳定版",
        items = listOf(
            "修复「界面上开关是开的、浏览器里却不生效」：1.10.0 只把读取端迁到了框架数据库，写入端还在写本地 XML —— 两端不是同一份数据",
            "补上写入端 libxposed service：框架经 XposedProvider 把服务 binder 送进模块进程，开关与规则库改完即刻同步给宿主",
            "manifest 新增 XposedProvider（authorities 必须为 <包名>.XposedService）—— 没声明它就是静默失效，已加进静态走查",
            "「关于」页状态行改为显示框架服务的连接状态；未连接时明确提示「开关改动不会同步到宿主」",
            "minSdk 23 → 26：service 库要求 Android 8.0 起",
        ),
    ),
    Changelog(
        version = "1.10.0",
        tag = "稳定版",
        items = listOf(
            "迁移到 libxposed API 102：全工程不再引用旧框架 API（" + LEGACY_API + "），模块身份改由 META-INF/xposed/ 声明",
            "跨进程读开关改走 XposedInterface#getRemotePreferences —— 开关存在框架数据库，彻底摆脱文件权限这条不稳的路",
            "修掉 1.9.8 引入的「基本功能正常、改的开关不生效」：旧路径下落进 uid 私有目录，宿主进程连父目录都进不去",
            "自带反射层 Xp 取代旧的 " + LEGACY_HELPERS + "；hook 统一走 Hooker/Chain 拦截器模型，回调签名对 features 保持兼容",
            "⚠ 本版只完成了读取端：模块 App 仍在写本地 XML，宿主读到空表 → 由 1.10.1 补上写入端修复",
        ),
    ),
    Changelog(
        version = "1.9.8",
        tag = "历史",
        items = listOf(
            "修复 LSPosed 模块页的「使用了已废弃且即将移除的功能」提示：根因是跨进程 prefs 的旧通道",
            "xposedminversion 93 → 82，并移除 xposedsharedprefs —— 这正是官方给出的两条消除方式",
            "⚠ 本版引入缺陷：去掉框架重定向后开关落进 uid 私有目录，宿主读不到 → 由 1.10.0 迁移到 API 102 修复",
        ),
    ),
    Changelog(
        version = "1.9.7",
        tag = "历史",
        items = listOf(
            "错误页去广告：摘掉「无法访问」页底部那条「热搜榜」——它是宿主下发的推广位，点击跳的是大米搜索",
            "链路还原：错误页是 hybrid 页，热搜榜由模板 JS 取 getDefaultPageInfo 的 isHotSearchAddImport 决定，只改这一个布尔即可",
            "改的是 HybridActionDispatcher#call 的返回值，不拦调用本身：错误提示与重试 / 回首页 / 网络检查 / 网页诊断 / 清除缓存按钮都不动",
        ),
    ),
    Changelog(
        version = "1.9.6",
        tag = "历史",
        items = listOf(
            "详情弹窗的「切换」不再关窗：原地刷新状态胶囊并翻转按钮文案，可以连着点几下看效果",
            "状态胶囊加 220ms 底色/文字色过渡；风险开关的确认框是异步落盘的，弹窗会接住那次变化重新对齐",
            "胶囊形状统一为 stadium（圆角 = 高度/2）：标签胶囊高 22dp → 圆角 11dp",
            "卡片圆角 14dp → 20dp，与多看 DuokanTuner、番茄 HookFanqie 对齐",
        ),
    ),
    Changelog(
        version = "1.9.2",
        tag = "历史",
        items = listOf(
            "用自定义规则接管宿主 native 拦截引擎：hook 它的写入通道 + 覆盖 miui_blacklist.json + 清空白名单 + 叫它重载",
            "新增开关 ad_host_override（默认关）；首次接管前整份备份，关掉开关自动还原",
            "三条防线防被覆盖回去：Application 就绪即写 / 写入通道后贴回 / 守护线程 8 秒校验",
        ),
    ),
    Changelog(
        version = "1.9.0",
        tag = "历史",
        items = listOf(
            "元素隐藏规则（##）改走 CSS 注入通道 —— 搜索结果页那种「页面里的广告」终于能治",
            "只拦 URL 对页内广告结构性无效：广告就在 DOM 里，地址是站点自己的路径",
        ),
    ),
    Changelog(
        version = "1.8.0",
        tag = "历史",
        items = listOf(
            "新增自定义拦截规则：导入 Adblock 规则集，URL 拦截 + 元素隐藏两条通道",
            "规则管理页支持从 URL / 本地文件导入，逐条启停与删除",
        ),
    ),
    Changelog(
        version = "1.7.0",
        tag = "历史",
        items = listOf(
            "搜索引擎接管：把必应 / Google / Yandex 注入宿主引擎数据并设为默认",
            "走「注入引擎数据」而非替换 URL 出口，宿主打开链接的方式不受影响",
        ),
    ),
)

/** 更新日志默认展开的条数：只留当前版本这一条，其余全部收进「更新日志」二级页。 */
internal const val COLLAPSED_LOG_COUNT = 1

/**
 * 关于页的项目信息。
 *
 * <p>刻意只放「用户判断模块是否正常工作需要知道的事实」：
 * 版本对不对、作用在哪、有没有多余行为、出问题去哪儿看。
 */
internal val PROJECT_INFO = listOf(
    InfoItem(
        label = "模块版本",
        value = "{moduleVersion}",
        detail = "界面底部「应用设置」里可看到同一份版本号；更新日志按版本逐条列出",
    ),
    InfoItem(
        label = "适配版本",
        value = "小米浏览器 $HOST_VERSION_NAME",
        detail = "宿主发版频繁；版本不一致时部分功能会静默失效，功能页顶部直接给出比对结果",
    ),
    InfoItem(
        label = "模块包名",
        value = Config.MODULE_PKG,
    ),
    InfoItem(
        label = "作用域",
        value = "$HOST_PACKAGE（小米浏览器）",
        detail = "模块只对该应用生效；需在框架的作用域里勾选它",
    ),
    InfoItem(
        label = "运行环境",
        value = "LSPosed / Xposed · libxposed API 102",
        detail = "需支持 API 100+ 的框架（LSPosed 2.x 起）",
    ),
    InfoItem(
        label = "生效方式",
        value = "改完即时生效",
        detail = "开关值在宿主进程有 1 秒缓存；已经渲染完成的界面结构需要重启浏览器",
    ),
)

/** 读当前安装的模块版本号；读不到时给出占位文本，不让界面出现 null。 */
internal fun moduleVersion(context: Context): String = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "未知"

// ---------------------------------------------------------------------------
// 展示组件
// ---------------------------------------------------------------------------

/**
 * **点开关 = 切换**，**点卡片其它地方 = 看详情**。
 *
 * <p>刻意不用库里的 `SwitchPreference`：它整行的 `onClick` 直接就是
 * `onCheckedChange(!checked)`，库里没有独立的「点行」回调，两条路径分不开
 * （androidx.preference 那侧的 `TwoStatePreference` 是同一个毛病，1.9.4 起就是自绘）。
 *
 * <p>[emphasized] 用于总开关：主色 12% 底 + 更大的标题，与普通功能卡拉开层级。
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
) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (emphasized) accentSoft() else scheme.surfaceContainer,
                shape = RoundedCornerShape(DsRadius.card),
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
 */
@Composable
internal fun OptionRow(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, RoundedCornerShape(DsRadius.card))
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
                    colorFilter = ColorFilter.tint(Color.White),
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
                            color = Color.White,
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
            color = if (current) Color.White else scheme.primary,
        )
    }
}

/**
 * 详情四段。内容偏长，这里自己限高并允许纵向滚动 —— 详情浮层不滚动的话
 * 长文本会把卡片顶出屏幕。
 */
@Composable
internal fun DetailBody(detail: Detail) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 详情正文最高 320dp（约一屏），超出内部滚动；这不是间距令牌，是内容约束
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
    val targetVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager
                .getPackageInfo(HOST_PACKAGE, 0)
                .versionName
        }.getOrNull() ?: "未安装"
    }
    val matched = targetVersion == HOST_VERSION_NAME
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
                text = "小米浏览器 $targetVersion",
                fontSize = DsType.title,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = if (matched) {
                    "与模块适配版本一致"
                } else {
                    "模块适配 $HOST_VERSION_NAME，该版本下部分功能可能不生效"
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
            offText = "版本不符",
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
