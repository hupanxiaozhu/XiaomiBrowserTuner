package com.hupan.hookbrowser.adblock

/**
 * 广告过滤规则解析器 —— Adblock Plus 语法的一个**可用子集**。
 *
 * ## 支持
 *
 * | 写法 | 含义 | 例 |
 * |---|---|---|
 * | `!` / `#` 开头、`[Adblock]` | 注释、列表头 | `! Title: EasyList` |
 * | `@@` 前缀 | **例外**规则（命中即放行，优先级最高） | `@@||example.com/ok` |
 * | `\|\|host^` | 域名锚（含子域） | `\|\|ads.example.com^` |
 * | `/path/x` + `*`、`*keyword*` | 路径 / 关键词（`*` 通配、`^` 分隔符） | `/ads/banner/x*` |
 * | `/regex/` | 正则 | `/ads?\d+\.js/` |
 * | 裸字符串 | URL 子串包含 | `adserver.` |
 * | `##selector` | **元素隐藏**（域名限定 / 全局） | `baidu.com##.ec_result` |
 *
 * 前五类进**请求拦截**（`AdRuleEngine`），最后一类进**页面 CSS 隐藏**（`AdElementCss`）——
 * 两条通道各管一半：请求拦截管「资源拉不拉得到」，元素隐藏管「拉到了也不显示」。
 * 百度搜索结果页的推广位属于后者（URL 全是自家路径，请求维度无从下手）。
 *
 * ## 不处理（导入时按类丢弃**并上报条数**，不静默）
 *
 * - **`#@#` / `#?#` / `#$#`**（例外 / uBO 扩展语法）：语义依赖 uBO 的选择器引擎，
 *   简化实现会变形，一律丢。
 * - **`:style()` / `+js()` / `:contains()`** 这类会注入样式的选择器：直接丢（只留纯选择器）。
 * - **`$domain=` 选项**：按来源/目标域名限定的规则没法在纯 URL 维度安全还原 → **整条丢弃**
 *   （宁可漏，不可误杀）。
 * - 其余 `$script` / `$image` / `$third-party` 这类资源类型、加载方选项：**剥掉选项本体保留规则**。
 *   代价是比原规则更宽（对所有类型生效）—— 要按类型过滤得读请求头，收益不抵成本，已在文档里写明。
 *
 * ## 护栏（这些规则一旦生效会拦掉半个互联网，必须在导入时就掐掉）
 *
 * - 单条长度 > [MAX_RULE_LEN] → 丢
 * - 正则规则长度 > [MAX_REGEX_LEN]、含 `)+` / `)*` 嵌套量词（ReDoS 特征）→ 丢
 * - 裸字符串长度 < [MIN_PLAIN_LEN] 且既不含 `/` 也不含 `.` → 丢（`ad`、`js`、`img` 这种会误杀一切）
 * - 通配规则去掉 `*`/`^` 后最长字面段 < [MIN_LITERAL_LEN] → 丢（`*a*` 等于全匹配）
 * - 单次导入总条数 > [MAX_RULES] → 截断并上报
 * - 元素隐藏规则：选择器长度 > [MAX_SELECTOR_LEN]、含 `{` / `}`、含 `:style(` 这类注入语法、
 *   以通配符或非法字符开头 → 丢；**全局**规则（不带域名限定）另加一道
 *   [isSafeGlobalSelector]，纯标签名选择器（`div`、`iframe`）会藏掉正文，必须掐掉。
 */
internal object AdRuleParser {

    /** 单条规则文本长度上限 */
    private const val MAX_RULE_LEN = 512

    /** 正则规则长度上限 */
    private const val MAX_REGEX_LEN = 160

    /** 裸字符串规则的最小长度（不含 `/` 和 `.` 时） */
    private const val MIN_PLAIN_LEN = 4

    /** 通配规则去掉通配符后，最长字面段的最小长度 */
    private const val MIN_LITERAL_LEN = 3

    /**
     * 单次导入的规则条数上限。
     *
     * 1.9.1 从 5000 放宽到 30000：真机上「导入界面显示 5000 条」正是撞在这个上限上
     * （一份中文规则集的 URL 规则远超 5000），后面的规则被静默截断 —— 用户看到的
     * 是「导了 3 万条只生效 5000 条」。解析是导入时的**一次性**动作，没有运行时成本，
     * 真正的运行时代价在 [AdRuleEngine] 的分桶上限里单独管。
     */
    const val MAX_RULES = 30000

    /**
     * 单次导入的元素隐藏规则条数上限。
     *
     * 1.9.1 从 4000 放宽到 15000，理由同上（宿主内置规则库里 `##` 条目就有几千条）。
     * 注入到页面的实际体积由 [AdElementCss] 的三道上限再收一次。
     */
    const val MAX_ELEMENT_RULES = 15000

    /** 单条元素隐藏选择器的长度上限 */
    private const val MAX_SELECTOR_LEN = 200

    /** 一条元素隐藏规则最多限定多少个域名 */
    private const val MAX_ELEMENT_DOMAINS = 12

    /** `/pattern/`（整条就是一条正则） */
    private val RE_FULL_REGEX = Regex("^/(.+)/$")

    /** `)+` / `)*` —— ReDoS 最常见的形状 */
    private val RE_NESTED_QUANT = Regex("\\)[+*]")

    /** 解析结果：规则 + 各类丢弃计数（界面要把丢弃数原样告诉用户） */
    internal class ParseResult(
        val rules: List<String>,
        /** 元素隐藏规则（归一化成 `域名##选择器` / `##选择器`） */
        val elementRules: List<String>,
        /** 非空行总数 */
        val lines: Int,
        val commentLines: Int,
        /** 文本里出现的 `##` 行数（含被护栏丢掉的） */
        val elementLines: Int,
        /** 命中护栏被丢弃的 */
        val invalidLines: Int,
        /** 超出条数上限被截断的 */
        val truncatedLines: Int
    )

    /**
     * 把一份规则文本（来自 URL 或本地文件）解析成标准化的规则列表。
     *
     * 幂等：同一份文本重复解析结果一致；重复规则自动去重（不计入 invalid）。
     */
    fun parse(text: String): ParseResult {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        val elems = ArrayList<String>()
        val elemSeen = HashSet<String>()
        var lines = 0
        var comment = 0
        var element = 0
        var invalid = 0
        var truncated = 0

        for (line in text.lineSequence()) {
            val raw = line.trim()
            if (raw.isEmpty()) continue
            lines++
            // 元素隐藏规则必须排在「# 注释」之前判：`##.ad` 同时以 # 开头
            if (isElementRule(raw)) {
                element++
                val el = normalizeElement(raw)
                if (el == null) {
                    invalid++
                } else if (elems.size >= MAX_ELEMENT_RULES) {
                    truncated++
                } else if (elemSeen.add(el)) {
                    elems.add(el)
                }
                continue
            }
            if (out.size >= MAX_RULES) {
                truncated++
                continue
            }
            if (raw.startsWith("!") || raw.startsWith("[") || raw.startsWith("#")) {
                comment++
                continue
            }
            val normalized = normalize(raw)
            if (normalized == null) {
                invalid++
                continue
            }
            if (seen.add(normalized)) out.add(normalized)
        }
        return ParseResult(out, elems, lines, comment, element, invalid, truncated)
    }

    /**
     * `##` / `#@#` / `#?#` / `#$#` 全是元素隐藏（含 uBO 扩展语法）。
     *
     * `!` 开头的一律先排除：那是 Adblock 的注释行，正文里写个 `##` 很常见
     * （`! Title: xxx ## 广告规则`），不排除就会被当规则解析。
     */
    private fun isElementRule(s: String): Boolean {
        if (s.startsWith("!")) return false
        return s.contains("##") || s.contains("#@#") || s.contains("#?#") || s.contains("#$#")
    }

    /**
     * 元素隐藏规则归一化成 `域名##选择器`（全局规则就是 `##选择器`）。返回 null 表示这条该丢。
     *
     * 只认最朴素的 `[域名,域名]##纯选择器` 形状：uBO 的例外/程序化/样式注入语法一律丢，
     * 因为我们最终是把选择器拼进一段 `sel,sel{display:none!important}` —— 任何带动作的
     * 语法到这里都会变成「要么没效果、要么把不想藏的也藏了」。
     */
    private fun normalizeElement(raw: String): String? {
        if (raw.length > MAX_RULE_LEN) return null
        if (raw.contains("#@#") || raw.contains("#?#") || raw.contains("#$#")) return null

        val i = raw.indexOf("##")
        if (i < 0) return null
        val domPart = raw.substring(0, i).trim()
        val sel = raw.substring(i + 2).trim()

        if (sel.isEmpty() || sel.length > MAX_SELECTOR_LEN) return null
        if (sel.indexOf('{') >= 0 || sel.indexOf('}') >= 0) return null
        if (sel.contains(":style(") || sel.contains(":contains(") || sel.contains(":-abp-") ||
            sel.contains(":xpath(") || sel.contains("+js(")
        ) return null
        // 只留「元素选择器」形状：`tag` / `.class` / `#id` / `[attr]` / `*` / `:pseudo` / 组合器
        val c = sel[0]
        val head = c == '.' || c == '#' || c == '[' || c == '*' || c == ':' || c == '>' || c in 'a'..'z' || c in 'A'..'Z'
        if (!head) return null

        if (domPart.isEmpty()) return if (isSafeGlobalSelector(sel)) "##$sel" else null

        val domains = parseElementDomains(domPart) ?: return null
        if (domains.isEmpty()) return null
        return domains.joinToString(",") + "##" + sel
    }

    /** `a.com,b.com` → 域名表；含排除（`~`）、通配、正则等复杂语法的整条丢 */
    private fun parseElementDomains(s: String): List<String>? {
        val parts = s.split(',')
        if (parts.isEmpty() || parts.size > MAX_ELEMENT_DOMAINS) return null
        val out = ArrayList<String>(parts.size)
        for (p0 in parts) {
            val p = p0.trim().lowercase()
            if (p.length < 4 || p.startsWith("~") || p.startsWith("/")) return null
            for (ch in p) {
                if (!(ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '-')) return null
            }
            if (p.indexOf('.') <= 0 || p.startsWith(".") || p.endsWith(".")) return null
            out.add(p)
        }
        return out
    }

    /**
     * **全局**元素隐藏规则的安全闸（没有域名限定的那批）。
     *
     * 全局规则会注入进每一个页面，其中最危险的是纯标签名选择器 —— `div`、`span`、`iframe`
     * 这类一旦进 CSS，全站正文直接塌。所以要求选择器里出现 `class` / `id` / 属性 / 组合器
     * 之一，且长度足够，才允许进全局桶。带域名限定的规则不受这条约束（有站点边界兜着）。
     */
    private fun isSafeGlobalSelector(sel: String): Boolean {
        if (sel.length < 3) return false
        if (sel.contains(' ')) return true
        if (sel.contains('.') || sel.contains('#') || sel.contains('[') || sel.contains('>')) return true
        return false
    }

    /**
     * 单条规则规范化：返回 null 表示这条该丢。
     *
     * 输出保留了 `@@` 前缀和 `||`/`|` 锚点 —— 归一化成 `AdRuleEngine` 好认的形状，
     * 引擎侧不再做语法判定（解析与判定只在一处，避免两边规则漂移）。
     */
    private fun normalize(raw: String): String? {
        if (raw.length > MAX_RULE_LEN) return null

        var s = raw
        var exception = false
        if (s.startsWith("@@")) {
            exception = true
            s = s.substring(2)
        }
        if (s.isEmpty()) return null

        val pre = if (exception) "@@" else ""

        // ① 整条就是一条正则
        val m = RE_FULL_REGEX.matchEntire(s)
        if (m != null) {
            val p = m.groupValues[1]
            if (p.length < 3 || p.length > MAX_REGEX_LEN) return null
            if (RE_NESTED_QUANT.containsMatchIn(p)) return null
            return pre + "/" + p + "/"
        }

        // ② 选项：$domain= 直接丢，其余剥掉
        val dollar = s.indexOf('$')
        if (dollar == 0) return null
        if (dollar > 0) {
            if (s.substring(dollar + 1).contains("domain=")) return null
            s = s.substring(0, dollar)
        }
        if (s.isEmpty()) return null

        // ③ 结尾锚 `|`（URL 必须到此结束）
        var anchorEnd = false
        if (s.endsWith("|")) {
            anchorEnd = true
            s = s.dropLast(1)
        }
        if (s.isEmpty()) return null

        // ④ 去掉开头的锚点，只看本体
        var core = s
        if (core.startsWith("||")) core = core.substring(2) else if (core.startsWith("|")) core = core.substring(1)
        if (core.isEmpty()) return null

        if (core.indexOf('*') < 0 && core.indexOf('^') < 0) {
            // 裸字符串 / 纯域名：`ad`、`js` 这类太短的会误杀一切
            if (core.length < 3) return null
            if (core.indexOf('/') < 0 && core.indexOf('.') < 0 && core.length < MIN_PLAIN_LEN) return null
        } else {
            // 通配规则：`*a*` 等于全匹配
            if (longestLiteral(core) < MIN_LITERAL_LEN) return null
        }

        return pre + s + if (anchorEnd) "|" else ""
    }

    /** 去掉 `*`/`^`/`|` 之后最长的连续字面段长度 */
    private fun longestLiteral(s: String): Int {
        var best = 0
        var cur = 0
        for (c in s) {
            if (c == '*' || c == '^' || c == '|') {
                cur = 0
            } else {
                cur++
                if (cur > best) best = cur
            }
        }
        return best
    }
}
