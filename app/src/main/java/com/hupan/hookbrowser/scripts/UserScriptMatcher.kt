/*
 * 用户脚本的 URL 匹配（宿主进程热路径：每个页面加载完成都要过一遍）。
 *
 * 两种语法的原始行分开编译：
 * - @match（chrome match pattern）：`scheme://host/path` 三段式，`*` 是通配；
 * - @include / @exclude（油猴语法）：整行就是一个正则表达式。
 *
 * 编译只在脚本库变化时做一次（UserScriptFeature 的轮询线程），回调里只读
 * [Compiled] 的不可变结构 —— 与规则引擎「编译好的不可变对象，换引用即生效」同款。
 *
 * 没有 @match 也没有 @include 的脚本（含没写 metadata 块的）按油猴默认语义
 * **匹配所有页面** —— 是否接受这一点由导入时的确认浮层交给用户判断。
 */
package com.hupan.hookbrowser.scripts

internal object UserScriptMatcher {

    /** 编译产物：脚本 + 预编译正则。全部不可变。 */
    internal class Compiled(
        val script: UserScript,
        val includes: List<Regex>,
        val excludes: List<Regex>,
        /** 没有任何匹配条件 = 所有 http/https 页面 */
        val matchAll: Boolean,
    )

    fun compile(s: UserScript): Compiled {
        val inc = ArrayList<Regex>()
        for (p in s.matches) matchPatternToRegex(p)?.let { inc.add(it) }
        for (p in s.includes) runCatching { Regex(p) }.getOrNull()?.let { inc.add(it) }
        val exc = s.excludes.mapNotNull { p -> runCatching { Regex(p) }.getOrNull() }
        return Compiled(s, inc, exc, matchAll = inc.isEmpty())
    }

    /** 命中判定：先排 @exclude，再看任意一条 include 命中；无条件即全域 */
    fun matches(c: Compiled, url: String): Boolean {
        for (re in c.excludes) if (re.containsMatchIn(url)) return false
        if (c.matchAll) return true
        for (re in c.includes) if (re.containsMatchIn(url)) return true
        return false
    }

    /**
     * @match 三段式转正则；不是合法 match pattern 返回 null（该行作废）。
     *
     * 语义按 chrome match pattern：`*` scheme = http 或 https；host `*.example.com`
     * 匹配自身与子域；path 里 `*` = 任意串。file/ftp 等 scheme 不接（本模块只注入网页）。
     */
    private fun matchPatternToRegex(p: String): Regex? {
        val schemeEnd = p.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = when (p.substring(0, schemeEnd)) {
            "*" -> "https?"
            "http" -> "http"
            "https" -> "https"
            else -> return null
        }
        val rest = p.substring(schemeEnd + 3)
        val slash = rest.indexOf('/')
        if (slash < 0) return null
        val host = rest.substring(0, slash)
        if (host.isEmpty()) return null
        val h = when {
            host == "*" -> "[^/]+"
            host.startsWith("*.") -> "([^/]+\\.)?" + Regex.escape(host.substring(2))
            else -> Regex.escape(host)
        }
        val path = buildString {
            for (ch in rest.substring(slash)) {
                append(if (ch == '*') ".*" else Regex.escape(ch.toString()))
            }
        }
        return runCatching { Regex("^$scheme://$h$path$") }.getOrNull()
    }
}
