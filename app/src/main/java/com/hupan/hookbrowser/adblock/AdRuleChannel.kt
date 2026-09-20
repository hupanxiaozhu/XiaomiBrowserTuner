package com.hupan.hookbrowser.adblock

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.XLog

/**
 * 规则库的**宿主进程**读取入口。
 *
 * 模块进程把规则写成 `adrules.xml`（见 [AdRuleStore]），宿主进程在这里通过
 * **remote preferences** 读出来（1.10.0 起）。
 *
 * ## 为什么从「扫文件」改成 remote preferences
 *
 * 1.9.x 那套「扫 `/data/misc/apexdata` + 自己解析 XML」是因为旧 API 的跨进程读写只有
 * `XSharedPreferences` 一条路，而它在 LSPosed 上异步 + 权限受限。API 102 的
 * `XposedInterface#getRemotePreferences(group)` 直接给出宿主可读的键值表，没有文件、
 * 没有权限、没有转义 —— 所以整段文件扫描与正则解析都删掉了。
 *
 * 这里保留 [TTL_MS] 节流：调用方在 hook 热路径上可以放心高频调，
 * 实际最多每 5 秒问一次框架。
 */
internal object AdRuleChannel {

    /** 组名，必须与模块侧 [AdRuleStore.PREFS] 一致（verify_static.py 会核对） */
    const val PREFS = "adrules"

    /** key，与 [AdRuleStore.KEY] 一致 */
    private const val KEY = "sets"

    /** 缓存窗口：hook 热路径高频调用，实际最多每 5 秒读一次 */
    private const val TTL_MS = 5000L

    @Volatile
    private var lastRead = 0L

    @Volatile
    private var lastJson = ""

    /** 缓存的规则 JSON；读不到返回空串（调用方当作「没有规则」） */
    fun read(): String {
        val now = System.currentTimeMillis()
        if (now - lastRead < TTL_MS) return lastJson
        lastRead = now
        val fresh = load()
        lastJson = fresh
        return fresh
    }

    private fun load(): String {
        val h = Config.remotePrefs(PREFS) ?: return ""
        // getString(key, defValue)：框架的 SharedPreferences 是标准接口，必须给默认值
        return runCatching { h.getString(KEY, "") ?: "" }.getOrDefault("")
    }

    /**
     * 诊断用：一句话说明规则库到底读到了没有。
     *
     * 规则「导入了但不生效」几乎只有两种原因 —— 模块没写进去，或者组名/key 对不上。
     * 这一行日志能直接区分，省得去猜。
     */
    fun describeOnce(): String {
        val h = Config.remotePrefs(PREFS) ?: return "拿不到 $PREFS 组（remote preferences 不可用）"
        val all = runCatching { h.all }.getOrNull() ?: return "读 $PREFS 组失败"
        if (all.isEmpty()) return "组 $PREFS 为空（模块进程还没写过规则库）"
        val len = (all[KEY] as? String)?.length ?: 0
        return "组 $PREFS 有 ${all.size} 个 key，$KEY 长度=$len"
    }

    /** 供调用方在自检里打一行摘要，避免每次都构造字符串 */
    fun logOnce() = XLog.v("【诊断】规则库：${describeOnce()}")
}
