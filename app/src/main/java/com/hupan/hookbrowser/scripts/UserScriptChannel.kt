/*
 * 脚本库的**宿主进程**读取入口（与 AdRuleChannel 同构）。
 *
 * 模块进程把脚本写进 `userscripts` 组（见 [UserScriptStore]），宿主进程经
 * remote preferences 读出来。保留 [TTL_MS] 节流：热路径高频调用最多每 5 秒
 * 真问一次框架。
 *
 * 返回的是「指纹 + 解码后的脚本表」：指纹取自原始串的 length + hashCode，
 * 调用方（UserScriptFeature 的轮询线程）靠它判断要不要重编译匹配表，
 * 不解码也能比出「没变」。
 */
package com.hupan.hookbrowser.scripts

import com.hupan.hookbrowser.Config

internal object UserScriptChannel {

    /** 组名，必须与模块侧 [UserScriptStore.PREFS] 一致（verify_static.py 会核对） */
    const val PREFS = "userscripts"

    private const val KEY_INDEX = "index"
    private const val KEY_PREFIX = "s_"

    /** 缓存窗口：最多每 5 秒真读一次 */
    private const val TTL_MS = 5000L

    @Volatile
    private var lastRead = 0L

    @Volatile
    private var cached: Pair<String, List<UserScript>> = "" to emptyList()

    /** 指纹 + 脚本表；读不到时指纹为空串、表为空（调用方当作「没有脚本」） */
    fun read(): Pair<String, List<UserScript>> {
        val now = System.currentTimeMillis()
        if (now - lastRead < TTL_MS) return cached
        lastRead = now
        val fresh = load()
        cached = fresh
        return fresh
    }

    private fun load(): Pair<String, List<UserScript>> {
        val h = Config.remotePrefs(PREFS) ?: return ("" to emptyList())
        val all = runCatching { h.all }.getOrNull() ?: return ("" to emptyList())
        val index = (all[KEY_INDEX] as? String).orEmpty()
        val ids = UserScriptCodec.decodeIndex(index)
        val stamp = StringBuilder(index.length + 16).append(index.hashCode())
        val out = ArrayList<UserScript>(ids.size)
        for (id in ids) {
            val raw = all[KEY_PREFIX + id] as? String ?: continue
            stamp.append('|').append(raw.length).append(':').append(raw.hashCode())
            UserScriptCodec.decode(raw)?.let { out.add(it) }
        }
        return stamp.toString() to out
    }

    /** 诊断用：一句话说明脚本库到底读到了没有（与规则库的诊断同款） */
    fun describeOnce(): String {
        val h = Config.remotePrefs(PREFS) ?: return "拿不到 $PREFS 组（remote preferences 不可用）"
        val all = runCatching { h.all }.getOrNull() ?: return "读 $PREFS 组失败"
        if (all.isEmpty()) return "组 $PREFS 为空（模块进程还没写过脚本库）"
        val ids = UserScriptCodec.decodeIndex((all[KEY_INDEX] as? String).orEmpty())
        return "组 $PREFS 有 ${all.size} 个 key，索引 ${ids.size} 个脚本"
    }
}
