package com.hupan.hookbrowser.adblock

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一组导入的规则（一个「规则集」）。
 *
 * 管理界面的最小操作单元就是它：启用 / 停用 / 删除整组，查看组内每一条规则。
 * 单条规则不单独开关 —— 组级粒度已经够用，而且省掉一整套「单条状态」的存储与界面。
 */
internal class AdRuleSet(
    val id: String,
    val name: String,
    /** 来源描述：`https://…/easylist.txt` 或 `file:my-rules.txt` */
    val source: String,
    val updatedAt: Long,
    var enabled: Boolean,
    val rules: List<String>,
    /** 元素隐藏规则（`域名##选择器` / `##选择器`），走 CSS 注入通道，见 [AdElementCss] */
    val elementRules: List<String> = emptyList()
)

/**
 * 规则集的 JSON 编解码。
 *
 * 用 `org.json`（Android 平台自带，零依赖），存进模块的**独立** SharedPreferences
 * （`adrules`，见 [AdRuleStore]）—— 刻意不复用 `settings`，原因见 [AdRuleChannel] 的注释。
 *
 * key 全部取短名（`n` / `src` / `ts` / `on` / `r` / `e`）：几万条规则的 JSON 会被写进 XML
 * 并在宿主进程按需解析，每省一个字节都是净赚。
 */
internal object AdRuleCodec {

    private const val VERSION = 1

    /**
     * 单份规则库的 JSON 上限（防止导入超长列表把 SP 撑爆）。
     *
     * 1.9.1 从 2MB 放宽到 4MB：解析层的条数上限放开到 3 万条 URL + 1.5 万条元素隐藏后，
     * 单组规则集的序列化体积会显著变大，2MB 会让整组被 [encode] 丢掉。
     * 再往上加要慎重 —— 这个字符串每次变化都要在宿主进程被读出来比较一次（8 秒轮询）。
     */
    const val MAX_JSON_CHARS = 4 * 1024 * 1024

    /** 解出一组规则集；JSON 坏了就返回空表（绝不抛） */
    fun decode(json: String): List<AdRuleSet> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("sets") ?: JSONArray()
            val out = ArrayList<AdRuleSet>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    AdRuleSet(
                        id = o.optString("id"),
                        name = o.optString("n"),
                        source = o.optString("src"),
                        updatedAt = o.optLong("ts"),
                        enabled = o.optBoolean("on", true),
                        rules = stringsOf(o.optJSONArray("r")),
                        elementRules = stringsOf(o.optJSONArray("e"))
                    )
                )
            }
            out as List<AdRuleSet>
        }.getOrElse { emptyList() }
    }

    /**
     * 编码成 JSON。
     *
     * 超过 [MAX_JSON_CHARS] 时**从尾部整组丢弃**，绝不 `substring` 硬切 ——
     * 切一半的 JSON 解不回来，`decode` 会静默返回空表，用户看到的现象是
     * 「规则全都消失了」。宁少一组，不整库丢。
     *
     * （1.9.0 加元素隐藏规则后单库体积翻倍，原来那条 `substring` 截断线才变得够得着。）
     */
    fun encode(sets: List<AdRuleSet>): String {
        val full = render(sets)
        if (full.length <= MAX_JSON_CHARS) return full
        val kept = ArrayList(sets)
        while (kept.size > 1) {
            kept.removeAt(kept.size - 1)
            val t = render(kept)
            if (t.length <= MAX_JSON_CHARS) return t
        }
        return render(kept)
    }

    private fun render(sets: List<AdRuleSet>): String {
        val root = JSONObject()
        val arr = JSONArray()
        runCatching {
            root.put("v", VERSION)
            for (s in sets) {
                val o = JSONObject()
                o.put("id", s.id)
                o.put("n", s.name)
                o.put("src", s.source)
                o.put("ts", s.updatedAt)
                o.put("on", s.enabled)
                val rs = JSONArray()
                for (r in s.rules) rs.put(r)
                o.put("r", rs)
                if (s.elementRules.isNotEmpty()) {
                    val es = JSONArray()
                    for (e in s.elementRules) es.put(e)
                    o.put("e", es)
                }
                arr.put(o)
            }
            root.put("sets", arr)
        }
        return root.toString()
    }

    /** 所有**启用**的规则集里的规则，拍平成一张表交给 [AdRuleEngine.compile] */
    fun enabledRules(sets: List<AdRuleSet>): List<String> {
        val out = ArrayList<String>()
        for (s in sets) if (s.enabled) out.addAll(s.rules)
        return out
    }

    /** 总规则条数（含未启用的，界面要分开显示） */
    fun totalRules(sets: List<AdRuleSet>): Int {
        var n = 0
        for (s in sets) n += s.rules.size
        return n
    }

    /** 所有**启用**的规则集里的元素隐藏规则，交给 [AdElementCss.compile] */
    fun enabledElements(sets: List<AdRuleSet>): List<String> {
        val out = ArrayList<String>()
        for (s in sets) if (s.enabled) out.addAll(s.elementRules)
        return out
    }

    /** 元素隐藏规则总条数（含未启用的） */
    fun totalElements(sets: List<AdRuleSet>): Int {
        var n = 0
        for (s in sets) n += s.elementRules.size
        return n
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
