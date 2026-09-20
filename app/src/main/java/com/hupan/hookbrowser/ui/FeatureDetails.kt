package com.hupan.hookbrowser.ui

import com.hupan.hookbrowser.Config

/**
 * 每个功能开关的「详细信息」，点开关行里开关以外的区域时弹出来。
 *
 * 与工程根目录 README 的「功能与开关」表同源：这里是给用户在手机上看的一句话版本，
 * **改 hook 目标时两边一起改**（表里是完整依据，这里有取舍）。
 *
 * 数据用 [Config] 的常量当键，这样将来改 key 时不会出现「详情挂在了一个不存在的开关上」
 * 这种静默失效 —— 键对不上时 `PrefsFragment` 直接绑不上，界面上一眼就能发现。
 */
internal object FeatureDetails {

    /** 一个功能的详情：四段。 [caveat] 留空就不显示「注意事项」那一块 */
    class Detail(
        val purpose: String,
        val target: String,
        val effect: String,
        val caveat: String = ""
    )

    val ALL: Map<String, Detail> = mapOf(

        Config.MASTER to Detail(
            purpose = "总闸。关掉后下面所有功能立刻失效，模块不再干预浏览器任何行为 —— " +
                "不拦请求、不改界面、不动宿主设置，等于临时把模块摘下来。",
            target = "没有具体 hook 目标。每个功能的回调第一行都会先判断这个开关，不满足就直接放行。",
            effect = "开关值随时可改：浏览器进程底层有 1 秒缓存，改完 1 秒内生效，不用重启浏览器。",
            caveat = "关掉它不等于停用：想彻底停用请在 LSPosed 里取消勾选本模块的作用域。"
        ),

        Config.AD_SPLASH to Detail(
            purpose = "拦掉 MSA 联盟的开屏广告（启动时那张全屏广告图）。",
            target = "com.msa.sdk.core.splash.SystemSplashAd#{getAdSplashType, " +
                "getIsSupportPassiveSplashAd, getServiceIntent}",
            effect = "把判定返回值改成「没有可播广告」，宿主自己就不播了 —— " +
                "只影响开屏，不碰别的广告位。",
            caveat = "某次启动后短暂白屏是宿主在等同一个广告位超时，不是模块出错。"
        ),

        Config.AD_HOME_PROMO to Detail(
            purpose = "屏蔽首页推广位与「切换引导」：升级引导弹窗、简洁版与完整版之间的切换提示。",
            target = "...homepage.PremiumOperationManager#{canShowPremiumChangeHint, " +
                "getHasShowPremiumGuideDialog, getHasShowSimpleToPremiumGuideDialog}；" +
                "...homepage.SimpleVersionHomeLayout#{chanShowPremiumChangeLayout, " +
                "userClickChangeToPremiumHome}",
            effect = "让这几个判定一律返回「不展示」，首页不再出现推广卡片与引导条。",
            caveat = "只隐藏推广位本身，首页其它内容与布局不动。"
        ),

        Config.AD_SEARCH_SUG to Detail(
            purpose = "藏掉搜索下拉里的推广卡片（那几张「安装」卡是 H5 页面渲染的，" +
                "宿主的 Java 列表里根本没有它们）。",
            target = "反射取 SearchSugManager#mWebView，在 queryKeyword / querySug 之后往页面注入 JS_FILTER；" +
                "页内先用 CSS 前置规则（首帧前就拦住，不闪），再按判据隐藏，最后把只剩空壳的容器塌缩。",
            effect = "按「带广告标」与「卡内有真实安装按钮」两条判据识别，命中即 display:none；" +
                "不认高度，也不动正常搜索建议。",
            caveat = "宿主改版换了卡片结构时需要更新注入脚本。这条走的是页内脚本，" +
                "和 URL 拦截（自定义规则）是两条独立通道。"
        ),

        Config.AD_ERROR_PAGE_HOT to Detail(
            purpose = "摘掉「无法访问」错误页底部那条「热搜榜」—— 点进去跳的是大米搜索，" +
                "跟你要访问的站点没关系，纯推广位。",
            target = "...hybrid.HybridActionDispatcher#call；在其返回值里把 " +
                "getDefaultPageInfo 的 isHotSearchAddImport 置 false。",
            effect = "错误页的开关数据里热搜榜总闸变「关」，模板 JS 就不会渲染热搜榜，" +
                "改而渲染「搜索发现」——那两条真正的搜索建议仍然保留。",
            caveat = "错误提示本身、以及重试 / 回首页 / 网络检查 / 网页诊断 / 清除缓存这些按钮" +
                "完全不受影响。"
        ),

        Config.MISC_HOST_AD to Detail(
            purpose = "强制关闭宿主自己的广告判断，从源头让它认为自己不该出广告。",
            target = "...BrowserSettings#{isShowAd, isPersonalizedAdEnabled, isAdCustomDisabled}",
            effect = "宿主里凡是问「要不要显示广告」的地方都得到「不要」，含个性化广告。",
            caveat = "这是宿主自己的设置项被改写，属于「替宿主做决定」，建议保持开启。"
        ),

        Config.AD_CUSTOM_RULES to Detail(
            purpose = "用你自己导入的 Adblock 规则过滤资源：URL 拦截 + 元素隐藏两条通道。",
            target = "URL 那一半挂在 hyper.webkit.WebViewClient#shouldInterceptRequest 上" +
                "（外加 setWebViewClient 动态捕获每个 client 类 + 基类兜底）；" +
                "元素隐藏那一半（## 规则）走页内 CSS 注入。",
            effect = "规则库为空时整个引擎是 no-op，行为与没装模块一致；" +
                "导入规则后数秒内自动生效，不用重启浏览器。",
            caveat = "三套 WebView 内核（hyper / miui / baidu）共用同一条拦截链；" +
                "规则集改版后需要重新导入一次。"
        ),

        Config.AD_HOST_OVERRIDE to Detail(
            purpose = "不只是模块自己拦，而是把宿主 native 引擎的规则库换成你导入的规则。",
            target = "hook 宿主的规则写入通道 AdBlockHelper\$Updator#updateRuleList、" +
                "AdBlockDataUpdator#{writeJSONFile, updateAdBlackist, update}，" +
                "覆盖 files/data/adblock/miui_blacklist.json 并清空它的白名单，" +
                "最后调 MiuiStatics#notifyAdBlockUpdateConfig() 让它立即重载。",
            effect = "首次接管前会把整份宿主规则备份到 .hb_backup/，关掉开关即从备份还原；" +
                "另外有三道防线防止被宿主覆盖回去。",
            caveat = "这是唯一会修改宿主自身数据的开关，默认关闭。" +
                "关闭状态下模块只用自己那套规则，不碰宿主文件。"
        ),

        Config.UI_DOWNLOAD to Detail(
            purpose = "下载完成后的推广弹窗一律不出：不建弹窗、不推应用、不跳应用市场。",
            target = "...download.CommonDownloadDialogImpl#{onCreateDialog, requestGameRecommend}；" +
                "...DownloadHandler\$1#call",
            effect = "既拦弹窗本身，也拦它的推荐请求，下载完成的提示保持干净。",
            caveat = "下载功能本身不受影响，文件照常下载。"
        ),

        Config.UA_PATCH to Detail(
            purpose = "抹掉 User-Agent 里的小米浏览器标识，降低被站点降级或拦在外面的概率。",
            target = "...util.WebViewSettingConfig#{getDefaultUserAgent, " +
                "getUserAgentStringWithoutSwan, getMiuiBrowserUseragentSuffix}",
            effect = "UA 由下一条「UA 伪装模式」决定：Chrome 移动版（真机信息）/ 多 App 伪装 / 桌面版。",
            caveat = "「多 App 伪装」会把自己说成微信、QQ 等内置浏览器，个别站点可能给出不同页面。"
        ),

        Config.UI_SEARCH_ENGINE to Detail(
            purpose = "把必应 / Google / Yandex 内置进宿主切换栏并设为默认，装上即用。",
            target = "...search.SearchEngineDataProvider#{initEngineSet, getSearchEngines, " +
                "isCustomEngine, getItemTitle, getCurrentEngineTitle}、" +
                "...toolbar.EngineTabsManager#buildDefaultFixedOrderList、" +
                "...fullsearch.FullSearchActivity#buildSearchUrl（仅模块引擎）等一组只读出口。",
            effect = "走「往引擎列表里注入数据」，不替换搜索的 URL 出口 —— " +
                "宿主怎么打开链接完全不受影响；具体引擎在下一项里选。",
            caveat = "选择「百度」等于回到宿主原生渠道，不注入任何自定义引擎数据。"
        ),

        Config.MISC_UNLOCK_PREF to Detail(
            purpose = "强制让宿主设置页里每一项都可见（宿主原本用 isVisible 藏起来的项会被放出来）。",
            target = "androidx.preference.Preference#isVisible 恒返回 true",
            effect = "作用范围为整个宿主设置页；本机实测宿主原本判不可见的有 2 项，" +
                "开关一开就能看到。",
            caveat = "若某条目因机型不受支持而本应隐藏，放出来后点击可能异常。" +
                "这是默认关闭的项，开启前会弹风险确认。"
        ),

        Config.MISC_DEBUG to Detail(
            purpose = "放开宿主自己的调试模式，同时它是本模块详细日志的总闸。",
            target = "...BrowserSettings#{getDebugMode, getFormalDebugMode}",
            effect = "打开后模块才会输出挂载细节与【诊断】/【采样】/【注入】明细（XLog 的 v 档）；" +
                "默认完全安静，只留关键生命周期与异常。",
            caveat = "排查问题时才需要开；日常使用建议关闭，免得日志刷屏。"
        ),

        Config.MISC_SECURITY to Detail(
            purpose = "让浏览器不再拦截「网址安全检测」的结果 —— 打开后不会再提示钓鱼站、恶意站点。",
            target = "...Tab\$GetSecurityFlagAsyncTask#onPostExecute",
            effect = "该提示链路被整体跳过，访问可疑站点时不再有警告页。",
            caveat = "这是降低安全性的改动，默认关闭，开启前会弹风险确认。" +
                "只在明确知道自己要什么时开。"
        )
    )
}
