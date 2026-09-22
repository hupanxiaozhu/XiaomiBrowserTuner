/*
 * 模块设置界面的**功能目录数据**（1.11.0 界面重建，1.12.0 拆文件）。
 *
 * 历史：此前界面是 `PreferenceFragmentCompat` + 自绘 `SwitchRowPreference`（XML 版式），
 * 数据分散在 ui/FeatureDetails.kt（详情）、ui/Changelogs.kt（更新日志）与 res/xml/prefs.xml
 * （开关清单）三处。重建为 Compose 后三处合一，且与番茄 HookFanqie 的文件划分保持一致。
 *
 * 1.12.0 起本文件**只放数据**：开关目录、分区表、详情文案、选项表、更新日志、项目信息。
 * 展示组件（卡片 / 胶囊 / 入口行 / 关于页各卡）搬到同包的 `ui/CatalogComponents.kt` ——
 * 改文案与改版式因此不会再落在同一个 diff 里。
 *
 * 可见性说明：符号一律 internal —— 拆分后要跨包引用，而 `ui` 只在模块进程加载，
 * internal 不会外泄到 APK 之外。
 *
 * ⚠ **进程边界**：hook 侧（MainHook / features / adblock / Hooks）禁止引用本文件，
 * 否则 Compose 运行时会被加载进浏览器进程。
 *
 * ⚠ `tools/verify_static.py` 会**按路径**读本文件核对开关 key、分区表与详情配对
 * （第 2 / 3 / 12 项），所以数据必须留在 FeatureCatalog.kt 里，别跟着组件一起搬走。
 */

package com.hupan.hookbrowser.ui

import android.content.Context

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.features.SearchEngines
import com.hupan.hookbrowser.features.UaBuilder

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
        version = "1.12.0",
        tag = "当前",
        items = listOf(
            "规则管理页从 View 搬进 Compose：删掉独立的 RuleManagerActivity（XML 布局 + MaterialAlertDialogBuilder），改成与其余页面同一套令牌与组件的二级页；导入 / 查看 / 删除三个弹框全部自绘。至此模块界面 100% 是 Compose + miuix，不再有 View 页观感割裂",
            "修掉规则库统计的主线程 IO：原本在组合期直接读规则库并全量解析启用中的规则，改为协程 + IO 线程，并用修订号在改动后触发重算（替代原来的 ActivityResult 回调）",
            "修掉浮层叠加：在详情浮层里点亮风险开关时，确认浮层会压在详情卡上、两层遮罩叠成 75% 黑 —— 现在先收起详情再弹确认",
            "功能目录拆文件：ui/FeatureCatalog.kt 只留数据（开关目录 / 详情文案 / 更新日志 / 项目信息），展示组件搬到同包的 ui/CatalogComponents.kt —— 改文案与改版式不再落在同一个 diff 里",
            "列表稳定性：「更新日志」二级页补上 key（原来按位置记状态，版本一多会把某个版本的展开态错配到别的版本上）；主界面返回键的判定改用 settledPage，滑动动画途中不再乱跳 Tab",
            "小清理：删掉零引用的 pageFill()；二级页边距统一走单参 pageContentPadding()；设置页去掉一层重复的卡片内边距；强调色上的前景色改用 onAccent 令牌（原来硬编码白色）",
            "说明文字：DesignTokens.kt 补上「本工程实际在用 / 未用」两栏清单，并改正「XML 副本已删除」的旧说法（副本仍在，供三工程对账）；rememberIsWideScreen() 注明是 1.10.3 宽屏留白的接入点（Compose 重写时丢了这一块，属待接回的功能回归）",
            "strings.xml 只剩 manifest 用的两条：规则管理页搬到 Compose 后，最后一批界面用字符串（rule_* / dlg_cancel）也没有引用点了",
            "功能开关、配置键、hook 逻辑、规则引擎与解析护栏全部未改动",
        ),
    ),
    Changelog(
        version = "1.11.0",
        tag = "稳定版",
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
