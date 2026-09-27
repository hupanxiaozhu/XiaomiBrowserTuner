package com.hupan.hookbrowser.adblock

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 规则库 JSON 的**压缩打包**（1.15.10）。
 *
 * ## 为什么必须压缩
 *
 * 1.15.9 把规则库切片，绕开了框架镜像对**单个 value** 的 400,000 字符上限 ——
 * 但漏了另一堵更硬的墙：**Binder 事务上限 1 MB**。
 *
 * 框架的 remote preferences 在 `commit()` 时把整个组序列化过 Binder，字符串按 UTF-16 计费。
 * 一份两万多条的规则集（anti-AD 实测 571,510 字符 = 1.14 MB）无论切几片，
 * 只要还在同一个组里，`commit()` 就会失败 —— 而且 LSPosed 内部把
 * `TransactionTooLargeException` 吞掉，只让 `commit()` 返回 `false`，
 * 模块侧只能看到一行「同步组 adrules 失败（N 个 key）」，看不出是体积问题。
 *
 * 域名规则是 `||xxx.com^` 这种高度重复的文本，gzip 实测压缩到 **31.6%**：
 * 571,510 → 240,988 字符，事务降到 471 KB，安全。
 * 三万字（解析层上限，见 `AdRuleParser.MAX_RULES`）压完是 308,052 字符 / 602 KB，同样安全 ——
 * 也就是说**只要规则能被解析层接受，压缩后就一定塞得进去**。
 *
 * ## 1.15.11：跨组分片（容量不再受单组事务限制）
 *
 * 压缩把「一份规则库」压到几十万字符，够用了 —— 但**一个组**的天花板就是 1 MB 事务，
 * 想要 anti-AD 全量 + 中文规则集同时装下（> 1 MB）就撞墙，而且撞得毫无提示
 * （见上面那段：LSPosed 把异常吞了，只让 `commit()` 返回 false）。
 *
 * 所以把 blob 切成 [GROUP_CHARS] 一段，**每段进一个独立的 remote preferences 组**
 * （`adrules` / `adrules2` / `adrules3` …）—— 组与组之间是独立事务，容量上限就变成了
 * 「组数 × 1 MB」。组名的派生规则放在这里（[groupName]），读端写端共用同一份实现，
 * 不存在「两边拼得不一样」的可能。
 *
 * ## 格式
 *
 * 压缩结果再走 1.15.9 的分片，大多数情况一组只有 1 片。gzip 输出是二进制，转 Base64 存成字符串。
 *
 * 解压成本：宿主侧每 5 秒（`AdRuleChannel` 的 TTL）最多一次，
 * 240 KB → 571 KB 的 gunzip 是毫秒级，可接受。
 */
internal object AdRulePack {

    /** 明文 JSON（压缩不可用时的退路） */
    const val VER_PLAIN = 1

    /** Base64(gzip(json)) */
    const val VER_PACKED = 2

    /**
     * 首个 remote preferences 组名，也是组名派生的基名。
     *
     * 与 `AdRuleStore.PREFS` / `AdRuleChannel.PREFS` 必须**逐字相同**
     * （verify_static.py 会核对）—— 写错不报错，只会让宿主读到空表。
     */
    const val GROUP_BASE = "adrules"

    /**
     * 单个组的 blob 上限（字符数）。
     *
     * 一组 = 一次 `commit()` = 一个 Binder 事务，上限 1 MB 且字符串按 UTF-16 计费 ⇒
     * 300,000 字符 = 600 KB，留 40% 余量给 key 名与序列化开销。
     */
    const val GROUP_CHARS = 300_000

    /**
     * 第 [i] 组的组名：第 0 组就是 [GROUP_BASE]，其余加数字后缀（`adrules2`、`adrules3` …）。
     *
     * 刻意写成函数而不是两个常量数组 —— 读写两侧共用同一份实现，不可能拼错。
     */
    fun groupName(i: Int): String = if (i == 0) GROUP_BASE else GROUP_BASE + (i + 1)

    /** Base64 变体：不换行 */
    private const val FLAGS = Base64.NO_WRAP

    /** 压缩。失败返回空串，调用方退回 [VER_PLAIN] */
    fun pack(json: String): String = runCatching {
        val out = ByteArrayOutputStream(json.length / 3 + 64)
        GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        Base64.encodeToString(out.toByteArray(), FLAGS)
    }.getOrDefault("")

    /** 解压。不是合法 Base64 / 不是 gzip / 解出来不是 UTF-8 都返回 null */
    fun unpack(packed: String): String? = runCatching {
        val bytes = Base64.decode(packed, FLAGS)
        GZIPInputStream(bytes.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }
    }.getOrNull()
}
