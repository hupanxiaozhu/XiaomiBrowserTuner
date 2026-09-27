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
 *
 * ## 1.15.9：分片，绕开单 value 上限
 *
 * [ModuleService.push] 镜像时对**单个 value** 有 400,000 字符的硬上限（`MAX_VALUE_CHARS`），
 * 超出就跳过、只在模块进程 logcat 留一行 e 级日志。规则库是**一个** JSON 装下所有规则集，
 * 导入两三个 easylist 就是几百 KB，超限后整条被丢，远端组只剩 `clear()` 之后的空表。
 * 所以把 JSON 切片存储（每片 [CHUNK_CHARS]），宿主侧 [AdRuleChannel] 按片拼回。
 *
 * ## 1.15.10：先压缩再分片
 *
 * 分片只挡住了「单 value 400,000」这堵墙，还有更硬的一堵：**Binder 事务 1 MB**。
 * 框架 `commit()` 把整个组序列化过 Binder，字符串按 UTF-16 计费 ——
 * 一份两万多条的规则集（实测 571,510 字符 = **1.14 MB**）切几片都没用，
 * 只要同组就必然失败，而且 LSPosed 吞掉 `TransactionTooLargeException`、
 * 只让 `commit()` 返回 false（症状与 1.15.8 一模一样：界面正常、导入成功、规则不生效）。
 *
 * 写出前先 gzip（见 [AdRulePack]）：域名规则高度重复，实测压到 31.6%，
 * 571,510 → 240,988 字符。三万字（解析层上限）压完 602 KB，稳稳过墙。
 *
 * ## 1.15.11：跨组切分，容量与单组事务解耦
 *
 * 压缩解决了「一份规则库」，但想让 anti-AD 全量 + 中文规则集共存（> 1 MB）还是会撞同一堵墙。
 * 现在 blob 按 [AdRulePack.GROUP_CHARS] 切段，**每段进一个独立的组** ——
 * 容量 = 组数 × 1 MB。组名派生统一在 [AdRulePack.groupName]，读写两侧共用。
 *
 * key 布局（[AdRuleChannel] 里有一份必须逐字相同的拷贝，verify_static.py 会核对）：
 *
 * | key | 类型 | 含义 |
 * |---|---|---|
 * | [KEY_VER] | Int | [AdRulePack.VER_PLAIN] / [AdRulePack.VER_PACKED]；缺省 = 1.15.9 及更早写的 |
 * | [KEY_GROUP_COUNT] | Int | **组数**（每组写一份，冗余但省得为此多读一组）；缺省 = 1，兼容旧格式 |
 * | [KEY_COUNT] | Int | 本组的片数，0 表示这组没有内容 |
 * | `sets.p0` / `sets.p1` … | String | 本组的各片文本，按序拼接即为本段 blob |
 * | [KEY] | String | ≤1.15.8 的单 value，只读兼容用，新写入不再产生 |
 */
internal object AdRuleStore {

    /** 首组组名，必须与宿主侧 [AdRuleChannel.PREFS] 一致（verify_static.py 会核对） */
    const val PREFS = "adrules"

    /** ≤1.15.8 的单 value key。读出时作为「没有分片」的兼容回退 */
    const val KEY = "sets"

    /** 打包格式版本，取值见 [AdRulePack] */
    const val KEY_VER = "sets.v"

    /** 分片数（Int），指**本组**内的片数 */
    const val KEY_COUNT = "sets.n"

    /** 分片前缀，本组第 i 片的 key 是 `sets.p` + i */
    const val KEY_CHUNK = "sets.p"

    /** 组数（Int）。1.15.11 起才有；读出时缺省当 1 组（兼容 1.15.10 及更早） */
    const val KEY_GROUP_COUNT = "sets.g"

    /** 单组内单片字符数。硬上限是 400,000，留 25% 余量避免贴边 */
    const val CHUNK_CHARS = 300_000

    /**
     * 打包后**总**字符数的提示线。
     *
     * 1.15.10 之前这里是个硬墙（400,000，1.15.11 起跨组后可装到「组数 × 300,000」）。
     * 现在只用于提示：总量特别大时宿主侧每 5 秒要解压一次、内存占用也上去，
     * 值得在日志里说一句。**不是**写入失败的理由 —— 本地照常保存。
     */
    const val MAX_TOTAL_CHARS = 2_000_000

    /** 组数上限：防某个畸形输入切出几百组（4 MB 的 JSON 也只要 14 组） */
    private const val MAX_GROUPS = 64

    /** 读出并解码规则库；任何异常都退化成空表 */
    fun load(ctx: Context): MutableList<AdRuleSet> =
        AdRuleCodec.decode(read(ctx)).toMutableList()

    /** 读出原始 JSON（模块进程自用，界面不展示） */
    fun read(ctx: Context): String = runCatching {
        val ver = sp(ctx, AdRulePack.groupName(0)).getInt(KEY_VER, 0)
        val g = groupCount(ctx)
        val blob = buildString {
            for (i in 0 until g) append(readGroup(ctx, i))
        }
        if (ver == AdRulePack.VER_PACKED) AdRulePack.unpack(blob) ?: "" else blob
    }.getOrDefault("")

    /**
     * 落盘。返回是否成功。
     *
     * 用 `commit()` 而不是 `apply()`：调用方（规则导入）需要立刻知道写成功没有，
     * 失败时要给用户明确提示，不能静默吞掉。
     */
    fun save(ctx: Context, sets: List<AdRuleSet>): Boolean {
        val json = AdRuleCodec.encode(sets)
        // 压缩失败（内存不足之类的极端情况）退回明文：本地至少还能读，
        // 只是同步大概率会撞 Binder 墙 —— 由下面那条提示日志说明。
        val packed = if (json.isEmpty()) "" else AdRulePack.pack(json)
        val ver = if (packed.isEmpty()) AdRulePack.VER_PLAIN else AdRulePack.VER_PACKED
        val blob = packed.ifEmpty { json }
        // 空库也要写一组空内容：不写的话 SP 里还留着上一次的规则，
        // 宿主读到的就是「删干净了却还在拦」。
        val segments = if (blob.isEmpty()) listOf("") else blob.chunked(AdRulePack.GROUP_CHARS)
        if (segments.size > MAX_GROUPS) {
            XLog.e("规则库切出 ${segments.size} 组，超过上限 $MAX_GROUPS —— 写入中止，请减少规则集")
            return false
        }

        val oldGroups = groupCount(ctx)
        var ok = true
        for (i in segments.indices) {
            if (!writeGroup(ctx, i, segments[i], ver, segments.size)) ok = false
        }
        // 本次组数变少时清掉尾组：读端按 sets.g 取组，残留组本来就不会被读到，
        // 但留着占空间、也让下一个人看日志时困惑。远端也要推一遍才算真清掉。
        for (i in segments.size until oldGroups) {
            runCatching { sp(ctx, AdRulePack.groupName(i)).edit().clear().commit() }
            ModuleService.push(ctx, AdRulePack.groupName(i))
        }

        if (!ok) XLog.e("规则库写入失败（${json.length} 字符 / ${segments.size} 组）")
        if (blob.length > MAX_TOTAL_CHARS) {
            XLog.i(
                "规则库较大：打包后 ${blob.length} 字符 / ${segments.size} 组。" +
                    "宿主侧每 5 秒解压一次，内存占用随之上浮 —— 规则集过多时建议精简"
            )
        }
        // 本地 XML 只是模块进程的真源；宿主读的是框架数据库那几组，必须同步过去。
        // 少了这行就是「规则导入成功但永不生效」（1.10.1）。
        for (i in segments.indices) ModuleService.push(ctx, AdRulePack.groupName(i))
        return ok
    }

    /**
     * 升级后把**旧格式**的规则库重写一遍（1.15.10）。
     *
     * 1.15.9 及更早写下的数据要么是单 value、要么是明文分片 —— 内容没错，但推送时同样会撞
     * Binder 事务上限。这里在模块启动时重打包：只有 [KEY_VER] 缺失（旧格式）时才动手，
     * 写完就变成压缩格式，之后不再触发。
     *
     * 调用方请放到后台线程：规则库可能几百 KB，读 + 压缩 + 写都是阻塞 I/O。
     */
    fun repack(ctx: Context) {
        if (sp(ctx, AdRulePack.groupName(0)).getInt(KEY_VER, 0) != 0) return
        val sets = load(ctx)
        if (sets.isEmpty()) return
        XLog.i("规则库是旧格式（${sets.size} 组），重打包为压缩格式后同步")
        save(ctx, sets)
    }

    // ===================== 组内读写 =====================

    /**
     * 当前本地已存在的组名（1.15.11 起规则库可能跨多组）。
     *
     * 给 [ModuleService] 首次连上框架时整组补推用 —— 只推首组的话，
     * 「用户装完模块直接开浏览器、从没进过设置页」这条路径上会漏掉第 2 组之后的规则。
     */
    fun groupNames(ctx: Context): List<String> =
        (0 until groupCount(ctx)).map { AdRulePack.groupName(it) }

    /** 当前分了几组；缺 [KEY_GROUP_COUNT] 就是 1（1.15.10 及更早只写一组） */
    private fun groupCount(ctx: Context): Int {
        val g = sp(ctx, AdRulePack.groupName(0)).getInt(KEY_GROUP_COUNT, 1)
        return if (g in 1..MAX_GROUPS) g else 1
    }

    private fun readGroup(ctx: Context, i: Int): String {
        val p = sp(ctx, AdRulePack.groupName(i))
        val n = p.getInt(KEY_COUNT, 0)
        if (n > 0) {
            return buildString { for (j in 0 until n) append(p.getString(KEY_CHUNK + j, "").orEmpty()) }
        }
        // 没有分片：只可能是 1.15.8 及更早的单 value（那时 [KEY_VER] 也还没诞生）
        return if (i == 0) p.getString(KEY, "").orEmpty() else ""
    }

    private fun writeGroup(ctx: Context, i: Int, segment: String, ver: Int, groups: Int): Boolean {
        val chunks = if (segment.isEmpty()) emptyList() else segment.chunked(CHUNK_CHARS)
        return runCatching {
            // 先 clear 再写：切片数变少时（比如删掉一个大规则集）不清会留下旧尾片，
            // 拼出来就是「尾部多一段」的畸形数据 —— 解码失败 → 规则库整个变空。
            val ed = sp(ctx, AdRulePack.groupName(i)).edit().clear()
            ed.putInt(KEY_VER, ver)
            ed.putInt(KEY_GROUP_COUNT, groups)
            ed.putInt(KEY_COUNT, chunks.size)
            chunks.forEachIndexed { j, c -> ed.putString(KEY_CHUNK + j, c) }
            ed.commit()
        }.getOrDefault(false)
    }

    private fun sp(ctx: Context, group: String): SharedPreferences =
        ctx.getSharedPreferences(group, Context.MODE_PRIVATE)
}
