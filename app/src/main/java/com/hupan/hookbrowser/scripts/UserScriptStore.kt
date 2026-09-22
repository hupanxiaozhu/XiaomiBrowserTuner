/*
 * 脚本库的**模块进程**读写入口（导入 / 启停 / 删除都走这里）。
 *
 * 与规则库（AdRuleStore）同构但**独立成组**：脚本与规则互不挤占对方的上限，
 * 一边写坏了也不拖累另一边。组名必须与宿主侧 [UserScriptChannel.PREFS] 一致
 * （verify_static.py 会核对）。
 *
 * 写完必须 ModuleService.push —— 本地 XML 只是真源，宿主读的是框架数据库，
 * 少了推送就是「导入成功但永不生效」。
 */
package com.hupan.hookbrowser.scripts

import android.content.Context
import android.content.SharedPreferences
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.XLog

internal object UserScriptStore {

    /** 组名，必须与宿主侧 [UserScriptChannel.PREFS] 一致（verify_static.py 会核对） */
    const val PREFS = "userscripts"

    /** 索引 key：id 数组（JSON） */
    private const val KEY_INDEX = "index"

    /** 单脚本 key 前缀：`s_<id>` → 单个脚本的 JSON */
    private const val KEY_PREFIX = "s_"

    /** 读出整个脚本库（索引顺序）；坏条目跳过，不拖垮整库 */
    fun load(ctx: Context): MutableList<UserScript> {
        val sp = sp(ctx)
        val ids = UserScriptCodec.decodeIndex(
            runCatching { sp.getString(KEY_INDEX, "") }.getOrNull() ?: ""
        )
        val out = ArrayList<UserScript>(ids.size)
        for (id in ids) {
            val raw = runCatching { sp.getString(KEY_PREFIX + id, null) }.getOrNull() ?: continue
            UserScriptCodec.decode(raw)?.let { out.add(it) }
        }
        return out
    }

    /**
     * 全量重写脚本库（导入 / 启停 / 删除统一收口）。返回是否成功。
     *
     * 先清掉所有旧 `s_*` key 再写新的 —— 索引之外的孤儿 key 不会被索引引用，
     * 但会白白占着远端镜像的体积。
     */
    fun save(ctx: Context, scripts: List<UserScript>): Boolean {
        val sp = sp(ctx)
        val ed = sp.edit()
        for (k in sp.all.keys) {
            if (k.startsWith(KEY_PREFIX)) ed.remove(k)
        }
        ed.putString(KEY_INDEX, UserScriptCodec.encodeIndex(scripts.map { it.id }))
        for (s in scripts) ed.putString(KEY_PREFIX + s.id, UserScriptCodec.encode(s))
        val ok = runCatching { ed.commit() }.getOrDefault(false)
        if (!ok) XLog.e("脚本库写入失败（${scripts.size} 个脚本）")
        ModuleService.push(ctx, PREFS)
        return ok
    }

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
