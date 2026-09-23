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
import com.hupan.hookbrowser.features.QuickLinkRows
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
        title = "下载推广",
        summary = "下载弹窗内推广与应用商店引导全部不出",
        default = Config.defaultOf(Config.UI_DOWNLOAD),
        detail = Detail(
            purpose = "下载弹窗里的推广与商店引导全部不出：不请求「小游戏推荐」数据、" +
                "不显示「应用商店安装包」区块、不弹「下载引导卡」、不跳应用市场。",
            target = "...download.CommonDownloadDialogImpl#{requestGameRecommend, onCreateDialog}" +
                "（后者在 after 隐藏 mGameRecommendCard / tvStoreTitle / rlStore）；" +
                "...guidecard.GuideCardManager#{startGuideInfoRequest, tryShowCard}、" +
                "...guidecard.GuideDownloadCardView#show；" +
                "...DownloadHandler\$1#call",
            effect = "下载弹窗只剩文件信息与「原安装包 / 下载」按钮；" +
                "「识别到你可能在找的资源」那张贴底引导卡既不预取也不再弹出。",
            caveat = "1.15.3 起**不再拦弹窗本身** —— 之前返回 null 会让宿主对返回值判空失败，" +
                "下载任意非 APK 文件必闪退；而弹窗也是下载的确认入口，不建就等于下载不了。" +
                "1.15.4 只做可见性收敛，按钮与下载流程一律不动。",
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
        key = Config.UI_QUICKLINK_ROWS,
        title = "主页快捷方式行数",
        summary = "给简洁版主页的快捷方式设行数上限",
        default = Config.defaultOf(Config.UI_QUICKLINK_ROWS),
        detail = Detail(
            purpose = "给简洁版主页的快捷方式网格设一个行数上限（3 / 4 / 5 行）。" +
                "官方没有这项设置：简洁版主页写死了「站点格数 ≤ 9」，也就是最多 2 行（5 列）" +
                "再多就只剩一个「更多」入口。",
            target = "com.android.browser.homepage.SimpleVersionHomePage#getShowSiteCount(int) —— " +
                "它返回网格实际铺几个站点格，20.27 上是 min(总数, 9)（那个 9 是父类静态常量 " +
                "SIMPLE_HOME_MAX_SITE_COUNT，被内联进字节码）。每行几个取网格自己的 " +
                "mNumsPerRow（手机 5 列，读不到时按 5 算）。" +
                "另外还有一处 BrowserQuickLinksPage\$QuickLinksPanel#onLayout：宿主把「更多」格的位置" +
                "写死成 9（原生假设站点格不超过 9 个、第 10 格留给它），放开上限后它会和站点格撞在同一格上，" +
                "所以布局结束后要把「更多」格单独按真实下标重排一次。",
            effect = "按「行数 × 每行个数 − 1」截断（末行第一格留给「更多」）：站点多于上限时，" +
                "多出来的自动归进宿主的「更多」入口，不丢也不删数据。",
            caveat = "这是上限不是补齐 —— 快捷方式不足 N 行时只显示自然行数，不会补空格子。" +
                "想真的排到 4 / 5 行，得先有那么多快捷方式（长按主页或走「更多」添加）；" +
                "编辑 / 拖动排序时格子变多的布局表现以真机为准。",
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
        key = Config.SCRIPT_USERSCRIPTS,
        title = "用户脚本",
        summary = "⚠ 在匹配的网页里执行导入的 JavaScript",
        default = Config.defaultOf(Config.SCRIPT_USERSCRIPTS),
        detail = Detail(
            purpose = "执行你导入的用户脚本（油猴式 .user.js）：按脚本头部的 @match / @include " +
                "匹配页面，页面加载完成后注入执行。管「加功能」——自动展开、去跳转中间页这类 " +
                "CSS 做不到的事；与自定义拦截规则是互补关系。",
            target = "复用自定义规则的注入通道：WebViewClient#onPageFinished → " +
                "evaluateJavascript（webpage/PageInjection），不新增 hook 点。",
            effect = "脚本库存在独立组 userscripts，改完数秒内自动生效，不用重启浏览器。" +
                "document-start 声明降级为加载完成后执行；GM_* API 不支持（脚本照常注入，" +
                "有降级路径的能正常用）；只注入 http/https 页面，宿主内部页不碰。",
            caveat = "脚本能力等同网页自身代码，只导入来源可信的脚本。默认关闭，" +
                "开启前会弹风险确认。",
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

/** 分区：界面精简（UA / 搜索引擎 / 快捷方式行数三项各自带一条下拉）。 */
internal val UI_TOGGLES = FUNCTION_TOGGLES.filter {
    it.key in setOf(
        Config.UI_DOWNLOAD,
        Config.UI_QUICKLINK_ROWS,
        Config.UA_PATCH,
        Config.UI_SEARCH_ENGINE,
    )
}

/** 「规则」Tab：自定义规则、宿主引擎接管与用户脚本。 */
internal val RULES_TOGGLES = FUNCTION_TOGGLES.filter {
    it.key in setOf(Config.AD_CUSTOM_RULES, Config.AD_HOST_OVERRIDE, Config.SCRIPT_USERSCRIPTS)
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
    Config.SCRIPT_USERSCRIPTS to (
        "确认开启「用户脚本」？" to
            "开启后，你导入的脚本会在匹配的网页里执行，能力等同网页自身代码。" +
            "请只导入来源可信的脚本；不需要时建议保持关闭。"
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
 * 主页快捷方式行数的可选项（key 与 [QuickLinkRows] 的常量一致）。
 *
 * 文案只写「最多 N 行」，不折算站点个数：每行几个是宿主网格的 `mNumsPerRow`
 * （手机 5 列、大屏 7 列），写死个数迟早对不上。
 */
internal val QUICKLINK_ROWS_OPTIONS = listOf(
    OptionItem(QuickLinkRows.ROWS_3, "最多 3 行"),
    OptionItem(QuickLinkRows.ROWS_4, "最多 4 行"),
    OptionItem(QuickLinkRows.ROWS_5, "最多 5 行"),
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
        version = "1.15.4",
        tag = "当前",
        items = listOf(
            "补齐 APK 下载时的商店版推广。APK 与普通文件下载共用同一个 download.CommonDownloadDialogImpl 弹窗（只有「带风险信息」才换成 WarnDownloadDialogImpl），它按 BaseDownloadDialogImpl#mDownloadFromMarket 二选一加载布局，两份布局里都有「应用商店安装包」标题 + 「官方检测 / 安全」标签 + 「原安装包」——这就是「APK 下载时冒出来的小米应用商店审核版本」",
            "弹窗内的商店推广区（tvStoreTitle 标题、rlStore 容器）现在一并置 GONE。只改可见性、不动任何按钮，所以「原安装包 / 下载」入口照旧可用 —— 上次把整个弹窗拦掉的教训（那是下载的确认入口）不再重犯",
            "新增拦掉第二条链路「下载引导卡」（guidecard.GuideCardManager）：它由 BrowserTab#setTab 创建，页面加载完成后预取该页「官方版应用」信息（请求里带 oaid / miuiVersion 等设备字段），预取回来直接弹一张贴屏幕底部的卡 —— 标题「识别到你可能在找的资源：」，底部文案「“安全守护”已提供应用商店上架版本」。现在 startGuideInfoRequest 不预取、不上报，tryShowCard 不弹卡，GuideDownloadCardView#show 再兜一道（前两条是 private 方法，show() 是 public void 无参）",
            "为什么只挂这两处：startGuideInfoRequest 是所有预取请求的唯一出口（fetchGuideInfoOnPageFinished 与 prefetchGuideInfo 都调它），tryShowCard 是唯一的显示入口 —— 挂这两处连频控冷却那套逻辑都不必碰，也不会漏",
            "顺带更正 1.15.3 注释里的一处误判：mDownloadFromMarket 在宿主 20.27 里**存在**，而且正是下载弹窗选布局的依据（base.apk 里那条针对它的处理不是失效残留）。本模块不去改它，只收敛可见性",
            "感谢酷安用户 @闲云野鹤悠游林 反馈下载时的闪退与推广弹窗问题 —— 1.15.3 与 1.15.4 两版修复都源于他的反馈",
        ),
    ),
    Changelog(
        version = "1.15.3",
        tag = "稳定版",
        items = listOf(
            "修「用浏览器打开下载链接直接闪退」：这是**普通文件下载必崩**，不是某个链接的问题。「下载弹窗」原本把 download.CommonDownloadDialogImpl#onCreateDialog 的返回值置成 null，而宿主拿到它之后不做判空就调 dialog.setCanceledOnTouchOutside(...)（往上一层 androidx DialogFragment 同样直接 setupDialog）→ 主线程 NPE。异常抛在 hook 回调之外，模块接不住",
            "为什么只有部分链接崩：下载弹窗按条件三选一 —— 普通文件走 CommonDownloadDialogImpl（本模块有 hook，必崩）；APK 走 ApkDownloadDialogImpl、带风险信息走 WarnDownloadDialogImpl（模块从未碰过，正常）。平时下的多是 APK，所以直到下载一个 .zip 才撞上",
            "顺带修掉一个更根上的问题：弹窗是下载的确认入口（真正发起下载由弹窗按钮触发），把它整条拦掉等于下载永远开始不了 —— 原来的功能说明「下载功能本身不受影响」并不成立",
            "功能更名「下载弹窗」→「下载推广」（配置键 ui_download 不变，已有设置不受影响）：弹窗保留，只拦里面的推广 —— 不再请求「小游戏推荐」数据（方法返回 void，before 给结果即跳过），并在弹窗建好后兜底把那张推荐卡置 GONE",
            "APK 下载的「不跳应用市场」（DownloadHandler\$1#call 返回 null）保持不变：宿主侧对 null 有判空分支 + try/catch，与上面那条 null 性质完全不同",
        ),
    ),
    Changelog(
        version = "1.15.2",
        tag = "稳定版",
        items = listOf(
            "新增「主页快捷方式行数」：给简洁版主页的快捷方式网格设上限（最多 3 / 4 / 5 行），默认关闭。官方没有这项设置——简洁版主页写死了「站点格数不超过 9」，也就是 5 列下最多 2 行，再多只看得到「更多」",
            "hook 目标是 homepage.SimpleVersionHomePage#getShowSiteCount（返回网格实际铺几个站点格）。它覆写了父类 BrowserQuickLinksPage 的同名方法：父类是恒等返回，简洁版主页是 min(总数, 9)，而调用点走虚拟派发，只会落到子类那个重写上",
            "上限按「行数 × 每行个数 − 1」算（末行第一格留给「更多」，宿主自己的默认值 9 就是 2 行 × 5 − 1）；每行几个读宿主网格自己的 mNumsPerRow，读不到时按实测 5 列兜底。多出来的站点归「更多」，不删不丢",
            "开关打开时无条件接管返回值——宿主原本的 min(总数, 9) 在「9 到上限之间」这一段同样会生效，只在越界时才改等于没改",
            "修「行数放开后第 2 行末格图标文字叠在一起」：宿主 onLayout 把「更多」格的位置写死成 9（原生假设「站点格不超过 9 个」，第 10 格留给它），站点格一超过 9 个就撞在同一格上。现在 onLayout 之后单独把「更多」格按它真实的 child 下标重排一次，回到网格末尾",
            "修「母开关卡与它的下拉行看着挤成一张卡」：两者原先各自画同色圆角背景且零间距，圆角处连成一片。改为一张卡片组（组内一条细分割线），与其它卡片一样留出组间距",
            "这是上限不是补齐：站点不足 N 行时就是自然行数，不补空格子（补会撞宿主的铺格子循环越界）。其余功能开关、配置键、规则引擎与注入通道全部未改动",
        ),
    ),
    Changelog(
        version = "1.14.0",
        tag = "稳定版",
        items = listOf(
            "新增「用户脚本」：导入油猴式 .user.js，按 @match / @include 匹配页面，页面加载完成后注入执行——自动展开、去跳转中间页这类 CSS 做不到的事终于能做。脚本管理并入「规则」Tab",
            "注入通道抽成共享件（webpage/PageInjection）：自定义规则的元素隐藏 CSS 与用户脚本共用同一条 onPageFinished → evaluateJavascript 链路，捕获逻辑单例化，不再各自挂一遍",
            "注入语义：只注入 http/https 页面（宿主内部页不碰）；document-start 降级为加载完成后执行；GM_* API 不支持——导入时检出这些差别会弹确认，点头才放行",
            "安全边界：默认关闭 + 开启前风险确认；只支持本地文件导入（不做 URL 导入）；单脚本 380K 字符 / 共 64 个上限；脚本库独立 SP 组 userscripts，与规则库互不挤占",
            "「规则」Tab 概况卡与副标题同步覆盖脚本统计；verify_static 增加脚本库组名两处一致检查",
        ),
    ),
    Changelog(
        version = "1.13.0",
        tag = "稳定版",
        items = listOf(
            "新增「检查更新」：关于页点按即查 GitHub Releases，发现新版本时弹出更新日志与下载入口（跳浏览器下载 APK，不做应用内下载）",
            "打开应用时自动检查：默认开启、每 24 小时至多一次，失败静默不打扰；关于页新增「自动检查更新」开关（本地设置，不进宿主开关表）",
            "零新增依赖：网络用 HttpURLConnection，JSON 解析复用已有的 kotlinx-serialization-json；请求只在模块进程发出，hook 侧无感知",
            "版本号按段比较（1.9.0 < 1.10.0），不按字符串；接口超时 5 秒快速放弃，GitHub 限流 / 无网时手动检查给出可读的失败说明",
            "功能开关、配置键、hook 逻辑、规则引擎全部未改动",
        ),
    ),
    Changelog(
        version = "1.12.1",
        tag = "稳定版",
        items = listOf(
            "修掉「点开功能卡片后浮层太靠下」：详情 / 单选 / 确认三个浮层都铺在主界面 Scaffold 的内容区里，而底部导航栏是 Scaffold 后画上去的 —— 卡片底部被底栏盖住，正文看不全、关闭按钮点不到。原来只避开系统导航条，现在把底栏高度也让出来（与系统导航条取大者，不叠加成两层空白）",
            "浮层卡片整体限高：以前只给正文限 320dp、卡片本身不限，屏幕一矮就顶出屏幕外。现在按屏幕高算出卡片上限，中段（详情正文 / 选项列表）跟随剩余空间压缩并内部滚动 —— 标题与底部按钮始终在屏幕内；屏幕够高时卡片仍然收着长",
            "同一处理覆盖规则管理页的「从 URL 导入」与「规则明细」两个浮层，以及风险开关的确认浮层；底部让位与限高两处计算集中在 ui/component/DialogInsets.kt",
            "功能开关、配置键、hook 逻辑、规则引擎与解析护栏全部未改动",
        ),
    ),
    Changelog(
        version = "1.12.0",
        tag = "稳定版",
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
