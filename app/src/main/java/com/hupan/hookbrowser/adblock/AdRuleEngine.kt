package com.hupan.hookbrowser.adblock

import java.util.regex.Pattern

/**
 * 规则匹配引擎 —— **纯逻辑、无 Android 依赖**，宿主进程（拦截）和模块进程（自检）都能用。
 *
 * 编译产物是四组不可变结构，[match] 里按「先例外、后拦截」的顺序查：
 *
 * ```
 * ① 例外域名集合（HashSet，按 host 逐级后缀查）
 * ② 例外子串（List，contains）
 * ③ 例外正则（List，带字面量预筛）
 * —— 以上任一命中 → 直接放行，后面的拦截规则不再看
 * ④ 拦载：域名集合 → 子串 → 正则
 * ```
 *
 * ## 为什么拆成「域名集合 / 子串 / 正则」三类
 *
 * `shouldInterceptRequest` 是在网络线程上**每个资源请求**都会调一次的热路径，规则动辄上千条，
 * 所以能走 O(1) 的绝不走正则：
 * - `||ads.com^` 这类纯域名规则 → **HashSet 后缀查**（`a.b.ads.com` → 依次查 `a.b.ads.com` /
 *   `b.ads.com` / `ads.com` / `com`），只需几次哈希；
 * - 裸字符串（`adserver.`、`/ads/banner/`）→ 小写 `contains`，无正则编译开销；
 * - 只有真带 `*` / `^` / `/re/` 的才编正则，且**预筛**：先看 URL 里有没有该规则的最长字面段，
 *   没有就直接跳过整个正则匹配（这是让上千条正则也能跑在热路径上的关键）。
 *
 * ## 大小写
 *
 * URL 与规则统一按小写比较（`Pattern.CASE_INSENSITIVE` + 子串查前 `lowercase()`）。
 * `^` 在 Adblock 里表示「分隔符」（非 `[a-zA-Z0-9_\-.%]` 或字符串结束），这里转成
 * `(?:[^a-z0-9_.%\-]|$)` —— 因为两侧都已小写。
 *
 * ## 域名锚的坑（两个，缺一个都会误杀）
 *
 * ① 前缀**不能**用 `(?:[^/?#]*\.)?` 这种"任意前缀 + 点"的写法：`evilads.com` 的 `evilads.`
 *    会被吃进前缀，于是 `evilads.com` 被误判命中。正确写法是 `(?:[^/?#.@]+\.)*` ——
 *    前缀必须是**完整的域名标签**（标签里不含点）。
 * ② 整条正则**必须 `^` 锚定到 URL 开头**。少了 `^`，`find()` 会在 URL 任意位置起匹配，
 *    `||ads.com^` 照样命中 `evilads.com`（子串里正好有 `ads.com`）。1.8.0 就栽在这条上。
 *
 * 纯域名规则走 HashSet 路径不受影响，但带路径的复合规则（`||ads.com/ads.js`）必须走正则，
 * 所以这两个细节都很关键。
 */
internal class AdRuleEngine private constructor(
    private val domains: HashSet<String>,
    private val plains: List<String>,
    private val regexes: List<Rx>,
    private val exDomains: HashSet<String>,
    private val exPlains: List<String>,
    private val exRegexes: List<Rx>,
    /** 实际参与匹配的规则条数 */
    val ruleCount: Int,
    /** 命中护栏被丢弃的条数（域名/子串/正则三个桶加起来超上限的部分） */
    val droppedCount: Int
) {

    /** 一条编译好的正则规则：pattern + 用于预筛的字面量 + 原文（日志/提示页要显示是它拦的） */
    private class Rx(val pattern: Pattern, val literal: String, val raw: String)

    /**
     * 返回命中的规则原文；没有命中返回 null。
     *
     * 线程安全：所有字段都是不可变集合，编译一次后可被多线程并发调用。
     */
    fun match(url: String): String? {
        if (url.isEmpty() || url.length > MAX_URL_LEN) return null
        val u = url.lowercase()
        val host = hostOf(u)

        // ---- 例外优先 ----
        if (host.isNotEmpty() && domainHit(host, exDomains) != null) return null
        if (exPlains.isNotEmpty()) {
            for (p in exPlains) if (u.contains(p)) return null
        }
        if (exRegexes.isNotEmpty()) {
            for (rx in exRegexes) {
                if (rx.literal.isNotEmpty() && !u.contains(rx.literal)) continue
                if (rxHit(rx, u)) return null
            }
        }

        // ---- 拦截 ----
        if (host.isNotEmpty()) domainHit(host, domains)?.let { return "||$it^" }
        for (p in plains) if (u.contains(p)) return p
        for (rx in regexes) {
            if (rx.literal.isNotEmpty() && !u.contains(rx.literal)) continue
            if (rxHit(rx, u)) return rx.raw
        }
        return null
    }

    private fun rxHit(rx: Rx, u: String): Boolean =
        runCatching { rx.pattern.matcher(u).find() }.getOrDefault(false)

    /**
     * host 逐级后缀查：`a.b.ads.com` → `a.b.ads.com` / `b.ads.com` / `ads.com` / `com`。
     * 命中即返回命中的那个域名（日志要显示），没命中返回 null。
     */
    private fun domainHit(host: String, set: Set<String>): String? {
        if (set.isEmpty()) return null
        var i = 0
        while (i < host.length) {
            val cand = host.substring(i)
            if (set.contains(cand)) return cand
            val dot = host.indexOf('.', i)
            if (dot < 0) break
            i = dot + 1
        }
        return null
    }

    companion object {

        /** URL 长度上限：超过这个长度的 URL 基本是数据 URI / 畸形输入，直接放行 */
        private const val MAX_URL_LEN = 4096

        /**
         * 三桶上限（1.9.1 集体放宽）。
         *
         * 起因：真机上「导入界面显示 5000 条拦截」= 撞在解析层的上限，放宽解析层后
         * 这三道闸会接着丢 —— 中文规则集里带路径 / 通配的规则全进正则桶，原来只留 1500 条，
         * 等于放宽了也白放。
         *
         * 代价是各不相同的，所以三个值不按同一比例涨：
         * - 域名桶走 HashSet 后缀查，几万条也只是几次哈希 → 放最宽；
         * - 正则桶每条都有**最长字面段预筛**（URL 里没有那段字面量就直接跳过正则），
         *   实际开销近似一次 `contains` → 放到 6000 条（每次请求多扫约 4500 次 contains，
         *   在百微秒量级，网络线程可接受）；
         * - 裸串桶是**无条件线性 `contains`**，没有预筛可用，是唯一纯线性成本的桶 → 只翻一倍。
         */
        private const val MAX_DOMAINS = 40000
        private const val MAX_PLAINS = 8000
        private const val MAX_REGEXES = 6000

        private const val META = ".^$*+?()[]{}|\\"

        /** 空引擎：等价于没有导入任何规则（开关开着也不做任何事） */
        val EMPTY: AdRuleEngine = AdRuleEngine(
            HashSet(), emptyList(), emptyList(), HashSet(), emptyList(), emptyList(), 0, 0
        )

        /** 把规则原文列表编译成匹配引擎。任何一条编译失败都只跳过它，不影响整批。 */
        fun compile(rules: List<String>): AdRuleEngine {
            val domains = HashSet<String>()
            val plains = ArrayList<String>()
            val regexes = ArrayList<Rx>()

            val exDomains = HashSet<String>()
            val exPlains = ArrayList<String>()
            val exRegexes = ArrayList<Rx>()

            var used = 0
            var dropped = 0

            for (raw in rules) {
                val exception = raw.startsWith("@@")
                val body = if (exception) raw.substring(2) else raw
                if (body.isEmpty()) continue

                val d = plainDomainOf(body)
                if (d != null) {
                    val target = if (exception) exDomains else domains
                    if (target.size < MAX_DOMAINS) {
                        target.add(d)
                        used++
                    } else dropped++
                    continue
                }

                val rx = regexOf(body)
                if (rx != null) {
                    val target = if (exception) exRegexes else regexes
                    if (target.size < MAX_REGEXES) {
                        target.add(Rx(rx, literalOf(body), raw))
                        used++
                    } else dropped++
                    continue
                }

                if (body.indexOf('*') < 0 && body.indexOf('^') < 0 && body.indexOf('|') < 0) {
                    val target = if (exception) exPlains else plains
                    if (target.size < MAX_PLAINS) {
                        target.add(body.lowercase())
                        used++
                    } else dropped++
                } else {
                    val p = wildcardToRegex(body)
                    if (p == null) {
                        dropped++
                    } else {
                        val target = if (exception) exRegexes else regexes
                        if (target.size < MAX_REGEXES) {
                            target.add(Rx(p, literalOf(body), raw))
                            used++
                        } else dropped++
                    }
                }
            }
            return AdRuleEngine(domains, plains, regexes, exDomains, exPlains, exRegexes, used, dropped)
        }

        /**
         * 纯域名规则 → 返回域名本身，否则 null。
         *
         * 认这些形状（`^` 只在结尾当锚，不算通配）：
         * `example.com`、`example.com^`、`||example.com`、`||example.com^`
         */
        private fun plainDomainOf(body: String): String? {
            var s = body
            if (s.startsWith("||")) s = s.substring(2) else if (s.startsWith("|")) return null
            if (s.endsWith("|")) return null
            if (s.endsWith("^")) s = s.dropLast(1)
            if (s.isEmpty() || s.length > 253) return null
            for (c in s) {
                val ok = c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-'
                if (!ok) return null
            }
            if (s.indexOf('.') <= 0 || s.startsWith(".") || s.endsWith(".")) return null
            return s
        }

        /** `/re/` 规则 → 编译成 Pattern；不是正则规则返回 null */
        private fun regexOf(body: String): Pattern? {
            val m = Regex("^/(.+)/$").matchEntire(body) ?: return null
            return runCatching { Pattern.compile(m.groupValues[1], Pattern.CASE_INSENSITIVE) }.getOrNull()
        }

        /**
         * 长的字面段上限：够用来预筛就行，太长反而浪费内存比较。
         * 取最长的一段，长度 < 2 就返回空串（等于不预筛）。
         */
        private fun literalOf(body: String): String {
            var best = ""
            val sb = StringBuilder()
            for (c in body) {
                if (c == '*' || c == '^' || c == '|' || c == '/' || META.indexOf(c) >= 0) {
                    if (sb.length > best.length) best = sb.toString()
                    sb.setLength(0)
                } else {
                    sb.append(c)
                    if (sb.length >= 24) break
                }
            }
            if (sb.length > best.length) best = sb.toString()
            return if (best.length < 3) "" else best.lowercase()
        }

        /**
         * 通配规则 → 正则。
         *
         * - `||` 开头：可选协议 + **完整标签**子域前缀（见类注释里的 `evilads.com` 坑）
         * - `|` 开头：锚定 URL 起始
         * - `|` 结尾：锚定 URL 结束
         * - `*` → `.*`；`^` → 分隔符；其余元字符转义
         *
         * 不带 `|` 的规则用 `find()` 语义（URL 任意位置），所以不加 `^` 锚。
         */
        private fun wildcardToRegex(body: String): Pattern? {
            val sb = StringBuilder()
            var s = body
            if (s.startsWith("||")) {
                // `||` 是**域名锚**：匹配点必须落在 host 的域名标签边界上，所以必须 `^` 锚定。
                // 漏掉 `^` 就退化成「URL 任意位置出现」——真机上 `||adsh*.*^` 误杀了
                // `https://fclick.baidu.com/w.gif?...&adsh=...`（广告域名对不上也照样命中）。
                sb.append("^(?:[a-z][a-z0-9+.\\-]*://)?(?:[^/?#.@]+\\.)*")
                s = s.substring(2)
            } else if (s.startsWith("|")) {
                sb.append('^')
                s = s.substring(1)
            }
            var anchorEnd = false
            if (s.endsWith("|")) {
                anchorEnd = true
                s = s.dropLast(1)
            }
            for (c in s) {
                when (c) {
                    '*' -> sb.append(".*")
                    '^' -> sb.append("(?:[^a-z0-9_.%\\-]|$)")
                    else -> if (META.indexOf(c) >= 0) sb.append('\\').append(c) else sb.append(c)
                }
            }
            if (anchorEnd) sb.append('$')
            val src = sb.toString()
            if (src.length > 400) return null
            return runCatching { Pattern.compile(src, Pattern.CASE_INSENSITIVE) }.getOrNull()
        }

        /** 取 URL 的 host（已小写）。不依赖 android.net.Uri，模块进程也能用。 */
        internal fun hostOf(u: String): String {
            var s = u
            val scheme = s.indexOf("://")
            if (scheme >= 0) s = s.substring(scheme + 3)
            var end = s.length
            for (k in s.indices) {
                val c = s[k]
                if (c == '/' || c == '?' || c == '#') {
                    end = k
                    break
                }
            }
            s = s.substring(0, end)
            val at = s.lastIndexOf('@')
            if (at >= 0) s = s.substring(at + 1)
            val colon = s.lastIndexOf(':')
            if (colon >= 0) s = s.substring(0, colon)
            return s
        }
    }
}
