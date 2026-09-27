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
 * 这里保留 [TTL_MS] 节流：调用方在 hook 热路径上可以放心高频调，实际最多每 5 秒问一次框架。
 *
 * ## 规则库在跨进程前被包了三层
 *
 * 每一层都是为了过框架的 Binder 通道，缺一层就是「界面显示导入成功、宿主读到空表」：
 *
 * 1. **压缩**（1.15.10）—— `commit()` 的事务上限 1 MB 且按 UTF-16 计费；gzip 把域名规则
 *    压到 31.6%（见 [AdRulePack]）。[KEY_VER] 标记当前是明文还是压缩。
 * 2. **组内分片**（1.15.9）—— 单 value 上限 400,000 字符（`ModuleService.MAX_VALUE_CHARS`）。
 *    按本组的 [KEY_COUNT] 给定片数逐片取回再拼接。
 * 3. **跨组切分**（1.15.11）—— 一组就是一次 `commit()`，容量上限 1 MB。blob 按
 *    [AdRulePack.GROUP_CHARS] 切段、每段一个独立的组（`adrules` / `adrules2` / …），
 *    容量变成「组数 × 1 MB」。组数写在每组的 [KEY_GROUP_COUNT] 里。
 *
 * key 名必须与 [AdRuleStore] 里的定义**逐字相同**（verify_static.py 会核对）：
 * 写错不会报错，只会拼出半截数据 → 解码失败 → 规则库静默变空。
 *
 * 三态兼容：带 [KEY_VER] 的按标记解；没有标记但有 [KEY_COUNT] 的是 1.15.9 明文分片；
 * 只有 [KEY] 的是 1.15.8 及更早的单 value。
 *
 * 解压成本：最多每 [TTL_MS] 一次，240 KB 的 gunzip 是毫秒级。
 */
internal object AdRuleChannel {

    /** 首组组名，必须与模块侧 [AdRuleStore.PREFS]、[AdRulePack.GROUP_BASE] 一致（verify_static.py 会核对） */
    const val PREFS = "adrules"

    /** ≤1.15.8 的单 value key，与 [AdRuleStore.KEY] 一致 */
    private const val KEY = "sets"

    /** 打包格式版本 key，与 [AdRuleStore.KEY_VER] 一致；取值见 [AdRulePack] */
    private const val KEY_VER = "sets.v"

    /** 本组分片数 key，与 [AdRuleStore.KEY_COUNT] 一致 */
    private const val KEY_COUNT = "sets.n"

    /** 分片前缀，与 [AdRuleStore.KEY_CHUNK] 一致。本组第 i 片 = 前缀 + i */
    private const val KEY_CHUNK = "sets.p"

    /** 组数 key，与 [AdRuleStore.KEY_GROUP_COUNT] 一致 */
    private const val KEY_GROUP_COUNT = "sets.g"

    /** 组数上限，与 [AdRuleStore] 里那道闸同步（畸形数据不至于让我们去读几十个组） */
    private const val MAX_GROUPS = 64

    /** 单个 value 的理论上限，仅用于诊断文案；真正的分片阈值在 [AdRuleStore.CHUNK_CHARS] */
    private const val MAX_VALUE_CHARS = 400_000

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
        // 一次 all() 取全表：这里本来就要读版本标记与组数，逐 key 取只会多几次跨进程调用
        val all0 = groupAll(0) ?: return ""
        if (all0.isEmpty()) return ""
        val ver = all0[KEY_VER] as? Int
        val groups = groupCount(all0)

        val sb = StringBuilder()
        for (i in 0 until groups) {
            val all = if (i == 0) all0 else groupAll(i)
            if (all == null || all.isEmpty()) continue
            val n = (all[KEY_COUNT] as? Int) ?: 0
            if (n > 0) {
                for (j in 0 until n) sb.append((all[KEY_CHUNK + j] as? String).orEmpty())
            } else if (i == 0) {
                sb.append((all[KEY] as? String).orEmpty())   // 1.15.8 及更早的单 value
            }
        }
        val blob = sb.toString()
        return if (ver == AdRulePack.VER_PACKED) AdRulePack.unpack(blob) ?: "" else blob
    }

    /**
     * 诊断用：一句话说明规则库到底读到了没有。
     *
     * 规则「导入了但不生效」几乎只有几种原因 —— 模块没写进去、组名/key 对不上、
     * 体积超限没同步过来（1.15.8 的 400,000 单 value 上限、1.15.10 之前的 1 MB 事务上限）。
     * 这一行日志能直接区分，省得去猜。
     */
    fun describeOnce(): String {
        val all = groupAll(0) ?: return "读 ${AdRulePack.GROUP_BASE} 组失败（remote preferences 不可用？）"
        if (all.isEmpty()) return "组 ${AdRulePack.GROUP_BASE} 为空（模块进程还没写过规则库）"
        val groups = groupCount(all)
        var len = 0
        for (i in 0 until groups) {
            val a = if (i == 0) all else groupAll(i)
            if (a == null) continue
            val n = (a[KEY_COUNT] as? Int) ?: 0
            if (n > 0) {
                for (j in 0 until n) len += (a[KEY_CHUNK + j] as? String)?.length ?: 0
            } else if (i == 0) {
                len += (all[KEY] as? String)?.length ?: 0
            }
        }
        if (len == 0) return "组 ${AdRulePack.GROUP_BASE} 有 ${all.size} 个 key，但内容为空"
        val kind = if ((all[KEY_VER] as? Int) == AdRulePack.VER_PACKED) "压缩" else "明文"
        if (groups > 1) return "规则库跨 $groups 组，共 $len 字符（$kind）"
        if (len > MAX_VALUE_CHARS) {
            // 只可能是老模块写的（新版一律压缩 + 跨组分片）——顺带点明这条库其实同步不过来
            return "组 ${AdRulePack.GROUP_BASE} 的 $KEY 长度=$len，超过框架单 value 上限（$MAX_VALUE_CHARS）"
        }
        return "组 ${AdRulePack.GROUP_BASE} 有 ${all.size} 个 key，合计 $len 字符（未分组的旧版写入）"
    }

    /** 供调用方在自检里打一行摘要，避免每次都构造字符串 */
    fun logOnce() = XLog.v("【诊断】规则库：${describeOnce()}")

    /** 取某组的全部键值；拿不到句柄 / 读失败返回 null */
    private fun groupAll(i: Int): Map<String, *>? {
        val h = Config.remotePrefs(AdRulePack.groupName(i)) ?: return null
        return runCatching { h.all }.getOrNull()
    }

    /** 组数；缺 key 或值畸形都当 1 组（1.15.10 及更早只写一组） */
    private fun groupCount(all0: Map<String, *>): Int {
        val g = (all0[KEY_GROUP_COUNT] as? Int) ?: 1
        return if (g in 1..MAX_GROUPS) g else 1
    }
}
