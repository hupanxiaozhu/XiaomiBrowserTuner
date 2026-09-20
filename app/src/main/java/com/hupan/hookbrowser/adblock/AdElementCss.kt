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
 * ```js
 * (function(){ … var c="全局选择器们"; var m={"baidu.com":"选择器们"}; … })()
 * ```
 *
 * - **全局桶**（`##sel`）：无条件拼进去；
 * - **域名桶**（`baidu.com##sel`）：按 `location.hostname` 做**后缀匹配**才拼 ——
 *   `h === d || h.endsWith("." + d)`，与 [AdRuleEngine] 的域名语义保持一致。
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

    /** 域名桶个数上限。1.9.1：400 → 2000（真实规则集覆盖的站点数远超 400） */
    private const val MAX_DOMAINS = 2000

    /** `<style>` 的 id：注入前先查它，避免同一页面重复插入 */
    private const val STYLE_ID = "hbx_ad_css"

    /** 编译产物 */
    internal class Compiled(
        /** 要注入的 JS；空串表示没有可用的元素隐藏规则 */
        val js: String,
        val globalCount: Int,
        val scopedCount: Int,
        /** 撞上限 / 被闸掉的条数 */
        val dropped: Int
    ) {
        val total: Int get() = globalCount + scopedCount
        val isEmpty: Boolean get() = total == 0
    }

    val EMPTY = Compiled("", 0, 0, 0)

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
        return Compiled(buildJs(globals, scoped), globals.size, scopedCount, dropped)
    }

    private fun buildJs(globals: Set<String>, scoped: Map<String, List<String>>): String {
        val map = JSONObject()
        for ((d, sels) in scoped) {
            if (sels.isEmpty()) continue
            map.put(d, join(sels))
        }
        val sb = StringBuilder(256 + globals.size * 24)
        sb.append("(function(){try{")
        sb.append("if(document.getElementById('").append(STYLE_ID).append("'))return;")
        sb.append("var h=(location.hostname||'').toLowerCase();")
        sb.append("var c=").append(JSONObject.quote(join(globals))).append(';')
        sb.append("var m=").append(map.toString()).append(';')
        // 域名后缀匹配：host 本身或它的父域命中才拼接（与 URL 引擎的域名语义一致）
        sb.append("for(var d in m){if(h===d||(h.length>d.length&&h.substring(h.length-d.length-1)==='.'+d)){c+=(c?',':'')+m[d];}}")
        sb.append("if(!c)return;")
        sb.append("var e=document.createElement('style');e.id='").append(STYLE_ID).append("';")
        sb.append("e.textContent=c+'{display:none !important}';")
        sb.append("(document.head||document.documentElement).appendChild(e);")
        sb.append("}catch(x){}})()")
        return sb.toString()
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
