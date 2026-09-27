package com.hupan.hookbrowser.adblock

import org.json.JSONObject

/**
 * 元素隐藏规则 → 注入页面的 JS（内核是一段 `<style>`）。
 *
 * ## 为什么必须有这条通道
 *
 * 请求维度（[AdRuleEngine]）只能拦「资源 URL」，而搜索结果页的推广位是**页面自己 DOM 里的
 * 节点** —— 百度搜索页的广告条目 URL 全是自家路径，规则再怎么加也拦不到。这类只有切掉显示
 * 才干净，也就是 Adblock 语法里的 `##`。
 *
 * ## 产物形状
 *
 * 编译成一段自执行 JS：拼好 CSS 文本 → 建 `<style>` → 塞进 `head`。
 * CSS 是**声明式**的，插一次就永久生效 —— 之后页面无论是首屏渲染还是 SPA 动态插入的
 * 广告节点，都会自动被隐藏，不需要 MutationObserver 去追。
 *
 * ## 1.15.11：按 host 预筛，逐域名后缀查（原来每页注入全量）
 *
 * 旧实现把**所有**域名桶都编进 JS，靠页面里的 `location.hostname` 循环匹配 ——
 * 一份中文规则集（chinablock 实测 8,662 条限定规则 / 4,396 个域名桶）注入体积约 **385 KB 每页**，
 * 每个页面都要 `evaluateJavascript` 送一遍这么大一坨，纯属浪费。
 *
 * 现在域名匹配**挪到宿主侧的 Java 里**（`onPageFinished(view, url)` 本来就带着页面 URL，
 * 只是原来被丢了）：编译产物保留 `域名 → 选择器串` 的表，注入前按 host 做**逐级后缀查**
 * （`a.b.ads.com` → `a.b.ads.com` / `b.ads.com` / `ads.com` / `com`，与 [AdRuleEngine] 语义一致），
 * 只拼命中的那些。实测降到 ~30 KB（**12 倍**）。
 *
 * 拿不到 URL（个别内核回调形态不同）时只注入全局桶 —— 内置的通用类名（[BuiltinElementRules]）
 * 就在全局桶里，效果仍在，不会因为少个参数就整页漏掉。
 *
 * ## 三道上限（这段 JS 要注入进每一个页面，体积就是页面的成本）
 *
 * [MAX_GLOBAL] / [MAX_SCOPED] / [MAX_DOMAINS]，超出的记进 [Compiled.dropped] 并上报，
 * **不静默**。真实规则集里全局桶通常几百条，很少触顶；触顶说明规则集本身不适用。
 */
internal object AdElementCss {

    /** 全局选择器条数上限（1.9.1 从 1500 放宽：宿主内置规则库的 `##` 条目本身就有几千条） */
    private const val MAX_GLOBAL = 6000

    /** 带域名限定的规则条数上限（按规则数算，不按域名展开后的条数）。1.9.1：3000 → 12000 */
    private const val MAX_SCOPED = 12000

    /**
     * 域名桶个数上限。
     *
     * 1.9.1：400 → 2000；1.15.11：2000 → **8000**。
     * 起因是实测 EasyList China + ADgk 合并包（28,196 条）的限定元素规则覆盖 **4,396 个域名**，
     * 2000 的上限会把其中 55% 直接丢掉 —— 用户看到的现象是「规则导入了、也编译了，
     * 但那半数站点的隐藏规则一条都没生效」。桶本身只是个 HashMap key，
     * 几千个域名占不了多少内存，没必要卡在这。
     */
    private const val MAX_DOMAINS = 8000

    /** `<style>` 的 id：注入前先查它，避免同一页面重复插入 */
    private const val STYLE_ID = "hbx_ad_css"

    /**
     * 编译产物。
     *
     * 刻意**不再持有**「全量 JS」—— 那是个几百 KB 的字符串，宿主进程常驻没有意义。
     * 注入时按页面 host 现拼（每个页面一次，几十微秒）。
     */
    internal class Compiled(
        /** 全局桶：逗号拼接好的选择器串，可为空 */
        private val globals: String,
        /** 域名桶：域名 → 逗号拼接好的选择器串 */
        private val scoped: Map<String, String>,
        val globalCount: Int,
        val scopedCount: Int,
        /** 撞上限 / 被闸掉的条数 */
        val dropped: Int
    ) {
        val total: Int get() = globalCount + scopedCount
        val isEmpty: Boolean get() = total == 0

        /**
         * 给某个页面拼注入脚本。没有任何可用选择器时返回空串（调用方据此跳过注入）。
         *
         * [url] 为 null / 非 http(s) 时只带全局桶。
         */
        fun scriptFor(url: String?): String {
            val host = hostOf(url)
            val css = StringBuilder()
            if (globals.isNotEmpty()) css.append(globals)
            if (host.isNotEmpty() && scoped.isNotEmpty()) {
                // 逐级后缀查：与 AdRuleEngine#domainHit 完全同构，几次哈希就能覆盖所有可能的域名桶
                var i = 0
                while (i < host.length) {
                    val hit = scoped[host.substring(i)]
                    if (hit != null) {
                        if (css.isNotEmpty()) css.append(',')
                        css.append(hit)
                    }
                    val dot = host.indexOf('.', i)
                    if (dot < 0) break
                    i = dot + 1
                }
            }
            return if (css.isEmpty()) "" else wrap(css.toString())
        }
    }

    val EMPTY = Compiled("", emptyMap(), 0, 0, 0)

    /**
     * 编译。入参是 [AdRuleParser] 归一化过的 `域名##选择器` / `##选择器` 列表。
     *
     * 纯逻辑、无 Android 依赖 —— 模块进程（导入时预估）和宿主进程（实际注入）都能用。
     */
    fun compile(rules: List<String>): Compiled {
        val globals = LinkedHashSet<String>()
        val scoped = LinkedHashMap<String, MutableList<String>>()
        var scopedCount = 0
        var dropped = 0

        for (raw in rules) {
            val i = raw.indexOf("##")
            if (i < 0) continue
            val sel = raw.substring(i + 2)
            if (sel.isEmpty()) continue
            if (sel.indexOf('{') >= 0 || sel.indexOf('}') >= 0) {
                dropped++
                continue
            }

            val domPart = raw.substring(0, i)
            if (domPart.isEmpty()) {
                if (globals.size >= MAX_GLOBAL) {
                    dropped++
                } else {
                    globals.add(sel)
                }
                continue
            }

            if (scopedCount >= MAX_SCOPED) {
                dropped++
                continue
            }

            // 先全部检查能不能放下（域名桶可能新增），放不下就整条丢 —— 不留半条规则进 CSS
            var newDomains = 0
            var overflow = false
            val known = ArrayList<String>(4)
            for (d in domPart.split(',')) {
                if (d.isEmpty() || known.contains(d)) continue
                known.add(d)
                if (!scoped.containsKey(d)) {
                    newDomains++
                    if (scoped.size + newDomains > MAX_DOMAINS) {
                        overflow = true
                        break
                    }
                }
            }
            if (overflow) {
                dropped++
                continue
            }
            for (d in known) scoped.getOrPut(d) { ArrayList() }.add(sel)
            scopedCount++
        }

        if (globals.isEmpty() && scoped.isEmpty()) return EMPTY

        val byDomain = LinkedHashMap<String, String>(scoped.size)
        for ((d, sels) in scoped) {
            if (sels.isEmpty()) continue
            byDomain[d] = join(sels)
        }
        return Compiled(join(globals), byDomain, globals.size, scopedCount, dropped)
    }

    /**
     * 从页面 URL 取 host（已小写）。只认 http/https —— `about:blank` 之类取出来的
     * "about" 万一撞上某个域名桶就是莫名其妙的一刀。
     */
    private fun hostOf(url: String?): String {
        if (url == null) return ""
        val u = url.lowercase()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return ""
        return AdRuleEngine.hostOf(u)
    }

    /** 把选择器串包成一段自执行 JS。选择器串里可能有双引号（`[class^="ads-"]`），必须引号转义 */
    private fun wrap(css: String): String = buildString(160 + css.length) {
        append("(function(){try{")
        append("if(document.getElementById('").append(STYLE_ID).append("'))return;")
        append("var e=document.createElement('style');e.id='").append(STYLE_ID).append("';")
        append("e.textContent=").append(JSONObject.quote(css)).append("+'{display:none !important}';")
        append("(document.head||document.documentElement).appendChild(e);")
        append("}catch(x){}})()")
    }

    /** JSON 里存的是「逗号拼接好的 CSS 选择器串」，省掉一层数组解析 */
    private fun join(sels: Collection<String>): String {
        val sb = StringBuilder()
        for (s in sels) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(s)
        }
        return sb.toString()
    }
}
