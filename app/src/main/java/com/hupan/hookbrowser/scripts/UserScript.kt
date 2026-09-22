/*
 * 用户脚本的数据模型 + metadata 解析 + 编解码（两个进程共用，不依赖 Compose）。
 *
 * ## 存储模型（与规则库不同的取舍）
 *
 * 规则库是「整库一个 JSON value」；脚本是**每脚本一个 key**（`s_<id>` → 单个脚本的 JSON），
 * 外加一个索引 key（`index` → id 数组）。原因：
 *
 * - ModuleService.push 对**单 value** 有 400,000 字符的硬上限（超出静默跳过），
 *   而单脚本几百 KB 都正常 —— 整库一个 value 的话，两三个大脚本就把整库顶到静默不同步；
 * - 每脚本一个 key 后，上限变成「单脚本 ≤ 380k 字符」（见 [UserScriptCodec.MAX_SCRIPT_CHARS]），
 *   导入时超限明确拒绝，不会出现「导入成功但宿主永远看不到」。
 *
 * 代价是远端组里 key 变多；[UserScriptCodec.MAX_SCRIPTS] 限制脚本总数，
 * 把整组镜像的 Binder 事务量也压住。
 */
package com.hupan.hookbrowser.scripts

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个用户脚本（油猴式 .user.js）。
 *
 * [matches] / [includes] 保留**原始行**（@match 与 @include 语法不同，编译见
 * [UserScriptMatcher]），存储与展示都用原文，避免「编译后丢信息」。
 */
internal class UserScript(
    val id: String,
    val name: String,
    /** 来源描述：`file:xxx.user.js` */
    val source: String,
    val updatedAt: Long,
    var enabled: Boolean,
    /** @match 行（chrome match pattern：scheme://host/path） */
    val matches: List<String>,
    /** @include 行（油猴语法：直接是正则） */
    val includes: List<String>,
    /** @exclude 行（正则，优先判） */
    val excludes: List<String>,
    /** 脚本代码体（去掉 ==UserScript== 头之后的原文也含头部，存全量原文） */
    val code: String,
    /** 声明了 @run-at document-start（本模块在 onPageFinished 注入，属降级执行） */
    val runAtStart: Boolean,
    /** 声明了 GM_* API（本模块不支持；界面提示用，不拦导入） */
    val hasGmApi: Boolean,
)

/**
 * ==UserScript== 头部解析。
 *
 * 宽松策略：没有 metadata 块也允许导入（视为「所有 http/https 页面都执行」），
 * 由界面在导入确认里把这一点说清 —— 一刀切拒绝会误伤手写的小脚本。
 */
internal object UserScriptMeta {

    internal class Parsed(
        val name: String?,
        val matches: List<String>,
        val includes: List<String>,
        val excludes: List<String>,
        val runAtStart: Boolean,
        val hasGmApi: Boolean,
        /** 是否存在 ==UserScript== 块（没有 = 全域执行，导入确认要提示） */
        val hasBlock: Boolean,
    )

    fun parse(text: String): Parsed {
        val start = text.indexOf("==UserScript==")
        val end = text.indexOf("==/UserScript==")
        if (start < 0 || end <= start) {
            return Parsed(null, emptyList(), emptyList(), emptyList(),
                runAtStart = false, hasGmApi = false, hasBlock = false)
        }
        var name: String? = null
        val matches = ArrayList<String>()
        val includes = ArrayList<String>()
        val excludes = ArrayList<String>()
        var runAtStart = false
        var hasGmApi = false
        for (raw in text.substring(start, end).lines()) {
            val line = raw.trim()
            if (!line.startsWith("@")) continue
            // 键与值之间可能是空格也可能是 tab（油猴文件两种都有）
            val sep = line.indexOfFirst { it == ' ' || it == '\t' }
            if (sep <= 1) continue
            val key = line.substring(1, sep).lowercase()
            val v = line.substring(sep + 1).trim()
            if (v.isEmpty()) continue
            when (key) {
                "name" -> name = v
                "match" -> matches.add(v)
                "include" -> includes.add(v)
                "exclude" -> excludes.add(v)
                "run-at" -> if (v.equals("document-start", ignoreCase = true)) runAtStart = true
                "grant" -> if (!v.equals("none", ignoreCase = true)) hasGmApi = true
            }
        }
        return Parsed(name, matches, includes, excludes, runAtStart, hasGmApi, hasBlock = true)
    }
}

/** 单个脚本与索引的 JSON 编解码（org.json，平台自带，零依赖） */
internal object UserScriptCodec {

    /**
     * 单脚本 JSON 的字符上限：ModuleService 单 value 硬上限是 400,000，
     * 留一档余量给 JSON 转义膨胀。超限在导入时拒绝，不静默截断。
     */
    const val MAX_SCRIPT_CHARS = 380_000

    /** 脚本总数上限：压住整组镜像的 Binder 事务量，也防止误把整个脚本目录倒进来 */
    const val MAX_SCRIPTS = 64

    private const val VERSION = 1

    fun encodeIndex(ids: List<String>): String {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        return arr.toString()
    }

    /** 解索引；坏了返回空表（绝不抛） */
    fun decodeIndex(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotEmpty()) out.add(s)
            }
            out as List<String>
        }.getOrElse { emptyList() }
    }

    fun encode(s: UserScript): String {
        val o = JSONObject()
        runCatching {
            o.put("v", VERSION)
            o.put("id", s.id)
            o.put("n", s.name)
            o.put("src", s.source)
            o.put("ts", s.updatedAt)
            o.put("on", s.enabled)
            o.put("m", JSONArray().apply { s.matches.forEach { put(it) } })
            o.put("i", JSONArray().apply { s.includes.forEach { put(it) } })
            o.put("e", JSONArray().apply { s.excludes.forEach { put(it) } })
            o.put("c", s.code)
            o.put("rs", s.runAtStart)
            o.put("gm", s.hasGmApi)
        }
        return o.toString()
    }

    /** 解单个脚本；坏了返回 null（调用方跳过，不让一条坏数据拖垮整库） */
    fun decode(json: String): UserScript? {
        if (json.isBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            UserScript(
                id = o.optString("id"),
                name = o.optString("n"),
                source = o.optString("src"),
                updatedAt = o.optLong("ts"),
                enabled = o.optBoolean("on", true),
                matches = stringsOf(o.optJSONArray("m")),
                includes = stringsOf(o.optJSONArray("i")),
                excludes = stringsOf(o.optJSONArray("e")),
                code = o.optString("c"),
                runAtStart = o.optBoolean("rs"),
                hasGmApi = o.optBoolean("gm"),
            )
        }.getOrNull()
    }

    private fun stringsOf(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
