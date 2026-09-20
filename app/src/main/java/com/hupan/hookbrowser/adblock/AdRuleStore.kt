package com.hupan.hookbrowser.adblock

import android.content.Context
import android.content.SharedPreferences
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.XLog

/**
 * 规则库的**模块进程**读写入口（导入 / 启停 / 删除都走这里）。
 *
 * ## 为什么单独开一个 SharedPreferences，而不是塞进 `settings`
 *
 * 业务上一个组装一种东西更清楚：开关表小、规则库动辄几百 KB，分成两组后宿主侧
 * 可以给它们不同的刷新节奏（开关 1 秒、规则 5 秒），不必互相拖累。
 *
 * ## 1.10.0：写入后不再需要 chmod
 *
 * ≤1.9.9 时这一层要背一个「写完立刻放开文件权限」的包袱（`SharedPreferencesImpl` 落盘会把
 * 权限重置成 0660，宿主随后就读不到）。API 102 的 remote preferences 存在框架数据库里，
 * 读写都经框架的 Binder 通道，**没有任何文件权限参与** —— 这个包袱整个消失。
 */
internal object AdRuleStore {

    /** 组名，必须与宿主侧 [AdRuleChannel.PREFS] 一致（verify_static.py 会核对） */
    const val PREFS = "adrules"

    /** SP 里的唯一 key：整个规则库的 JSON */
    const val KEY = "sets"

    /** 读出并解码规则库；任何异常都退化成空表 */
    fun load(ctx: Context): MutableList<AdRuleSet> =
        AdRuleCodec.decode(read(ctx)).toMutableList()

    /** 读出原始 JSON（模块进程自用，界面不展示） */
    fun read(ctx: Context): String = runCatching {
        sp(ctx).getString(KEY, "") ?: ""
    }.getOrDefault("")

    /**
     * 落盘。返回是否成功。
     *
     * 用 `commit()` 而不是 `apply()`：调用方（规则导入）需要立刻知道写成功没有，
     * 失败时要给用户明确提示，不能静默吞掉。
     */
    fun save(ctx: Context, sets: List<AdRuleSet>): Boolean {
        val json = AdRuleCodec.encode(sets)
        val ok = runCatching {
            sp(ctx).edit().putString(KEY, json).commit()
        }.getOrDefault(false)
        if (!ok) XLog.e("规则库写入失败（${json.length} 字符）")
        // 本地 XML 只是模块进程的真源；宿主读的是框架数据库那一组，必须同步过去。
        // 少了这行就是「规则导入成功但永不生效」（1.10.1）。
        ModuleService.push(ctx, PREFS)
        return ok
    }

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
