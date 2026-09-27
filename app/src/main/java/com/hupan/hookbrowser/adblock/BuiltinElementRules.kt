package com.hupan.hookbrowser.adblock

/**
 * 内置的**通用类名**元素隐藏规则（1.15.11）。
 *
 * ## 为什么必须内置
 *
 * 元素隐藏这条通道原来只有两个来源：用户导入的规则集 + **小米宿主下发**的规则库。
 * 真机实测（TryBlock 广告测试页 + 百度搜索页）宿主那 1,533 条 `##` 全是**长尾站点专用**：
 *
 * ```text
 * a[href*=".cvezsacxz.com/"] , .ggw , #bottomAD , .google-ad2_fixed , .vodads , .lyad …
 * ```
 *
 * 覆盖的是博彩 / 悬浮 / 小说站，**`.ad-container` / `.adsbygoogle` / `[id^="div-gpt-ad"]`
 * 这类标准广告类名一条都没有** —— 结果就是「宿主自带广告拦得住、正规站与所有广告测试页全漏」，
 * 用户必须自己找规则导入才有效果，这是产品缺陷。
 *
 * ## 形状
 *
 * 直接写成 [AdElementCss] 接受的**归一化形状**（`##选择器`），不经 [AdRuleParser] ——
 * 这里不需要域名限定、不需要例外语法，过一遍解析器只是多一次往返。
 * 全部是全局规则，会注入**每一个**页面。
 *
 * ## 误杀的边界
 *
 * 选的都是「类名/id 里明确带 ad / sponsored / 广告网络品牌」的形状，不碰 `div` / `span` 这类
 * 纯标签名（那会把正文藏掉，解析器的 `isSafeGlobalSelector` 也正是为此设闸）。
 * 真机在广告测试页实测：4 个广告位全部 `0×0 none`，被隐藏的 14 个元素类名全含 ad，零误杀。
 *
 * ⚠ 改这个清单要同步 `AdElementCss` 的日志口径 —— 界面上的「隐藏 N 条」会把它算进去。
 */
internal object BuiltinElementRules {

    /**
     * 通用广告容器类名。
     *
     * 覆盖 Bootstrap / 各类主题与自建站的常见命名，以及广告测试页的标准 demo 类名。
     */
    private val CONTAINERS = listOf(
        ".ad-container",
        ".ad-wrapper",
        ".ad-banner",
        ".ad-label",
        ".ad-placeholder",
        ".ad-slot",
        ".ad-unit",
        ".ad-box",
        ".ad-frame",
        ".ad-mask",
        ".banner-ad",
        ".sidebar-ad",
        ".demo-ad",
        ".sponsor-ad",
        ".sponsored-content",
        ".inline-ad-container",
        ".inline-ad-wrapper"
    )

    /**
     * Google 广告 SDK（AdSense / Ad Manager / AdMob 网页版）的容器。
     *
     * `adsbygoogle` 是 AdSense 的固定类名；`div-gpt-ad` 是 Ad Manager 的自动生成 id 前缀；
     * 三个 iframe 属性选择器命中 Google 自己的广告域名 —— 这些是**广告载体**，
     * 藏掉不会影响页面其余内容。
     */
    private val GOOGLE_AD_SDK = listOf(
        ".adsbygoogle",
        ".adsbox",
        "ins.adsbygoogle",
        "[id^=\"div-gpt-ad\"]",
        "[id^=\"google_ads_\"]",
        "[class^=\"ads-\"]",
        "iframe[src*=\"doubleclick\"]",
        "iframe[src*=\"googlesyndication\"]",
        "iframe[src*=\"googleadservices\"]",
        "iframe[src*=\"adservice\"]"
    )

    /** 其余常见第三方广告网络的 iframe（Criteo / Taboola / Outbrain / Media.net / AppNexus / PubMatic） */
    private val THIRD_PARTY_NETWORKS = listOf(
        "iframe[src*=\"criteo\"]",
        "iframe[src*=\"taboola\"]",
        "iframe[src*=\"outbrain\"]",
        "iframe[src*=\"media.net\"]",
        "iframe[src*=\"adnxs\"]",
        "iframe[src*=\"pubmatic\"]"
    )

    /** 归一化形状（`##选择器`），直接并进 [AdRuleCodec.enabledElements] 的结果交给 [AdElementCss] */
    val ALL: List<String> =
        (CONTAINERS + GOOGLE_AD_SDK + THIRD_PARTY_NETWORKS).map { "##$it" }
}
