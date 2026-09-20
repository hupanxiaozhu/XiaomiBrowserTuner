package com.hupan.hookbrowser.adblock

import com.hupan.hookbrowser.HostContext
import com.hupan.hookbrowser.XLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 宿主（小米浏览器）**自带的广告规则库**。
 *
 * ## 它是什么
 *
 * 反汇编 `com.android.browser.util.AdBlockDataUpdator` 得到的事实（不是猜测）：
 *
 * ```
 * sParentFilePath = context.getFilesDir() + "/data/adblock"
 *   ├─ miui_blacklist.json     黑名单
 *   ├─ miui_whitelist.json     白名单
 *   ├─ miui_watchlist.json     监控名单
 *   ├─ miui_privacylist.json   隐私名单
 *   └─ miui_businesslist.json  商业名单
 * ```
 *
 * 每个文件是 `{"data": [ … ]}`。这份数据交给 chromium 内核的 **native** 匹配器
 * （日志 tag `<AdBlock>`，类 `BlockingRuleMatcher`）去跑，真机日志里能直接看到它在解析
 * `||57577.live^$csp=script-src`、`@@||acfun.cn^$document` 这类**标准 Adblock 语法**的规则。
 *
 * 也就是说：宿主早就有一套比本模块**语法更完整**的 URL 规则引擎（`$csp` / `$third-party` /
 * `$document` / `$generichide` 它全认，本模块的解析器把这些整条丢弃），只是它的规则**只覆盖
 * URL 维度**，管不了搜索结果页里的 DOM 推广位。
 *
 * ## 为什么我们还要读它
 *
 * 三个理由，都不是"重复劳动"：
 *
 * 1. 宿主那套引擎的开关在服务端配置里（`PREF_ENABLE_ADBLOCK` / `disable_webview_adblock_business`），
 *    普通用户改不了，真机上未必是开着的；
 * 2. 规则里的 `##` 元素隐藏条目只有注入 CSS 才能生效，native 那套不碰 DOM ——
 *    这部分**只有本模块能兑现**（见 [AdElementCss]）；
 * 3. 用户不导入任何规则时，本模块也不该是空转的。
 *
 * ## 只读，不写
 *
 * 绝不往那个目录写东西：宿主自己有一套 `watermark` 增量更新协议
 * （`checkUpdateMode` / `addDiffAdBlockData` / `writeOTAFile`），掺进去会让它的差分对账错乱。
 *
 * ## 读到的原始文本要再过一遍本模块的解析器
 *
 * 不直接塞进引擎 —— 走 [AdRuleParser.parse]，让宿主的规则和用户导入的规则**受同一套护栏约束**
 * （长度、ReDoS 形状、全局选择器的正文塌陷闸），顺带完成 `##` 与 URL 两路分流。
 */
internal object HostAdRules {

    /** `context.getFilesDir()` 下的相对目录（反汇编 `AdBlockDataUpdator::init` 得到） */
    private const val SUB_DIR = "data/adblock"

    /** 重扫间隔。宿主规则库动辄几 MB，没必要跟着 [com.hupan.hookbrowser.features.CustomAdBlockFeature] 的 8 秒轮询一起读 */
    private const val RESCAN_MS = 60_000L

    /** 单次最多取多少条（喂给引擎的上限，超过的部分引擎也装不下） */
    private const val MAX_TOTAL = 60_000

    /** 单个规则文件的大小上限，超过就不读（防畸形文件把内存打爆） */
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024

    @Volatile
    private var cache: List<String> = emptyList()

    @Volatile
    private var nextScanAt = 0L

    /** 上次打过的日志，内容没变就不重复打 */
    @Volatile
    private var lastReport = ""

    /**
     * 取宿主内置规则（原始文本，未解析）。带 60 秒缓存，后台线程调用。
     *
     * 拿不到 Context / 目录不存在 / 文件坏 —— 一律返回空表，绝不抛。
     */
    fun load(): List<String> {
        val now = System.currentTimeMillis()
        if (now < nextScanAt) return cache
        nextScanAt = now + RESCAN_MS

        val list = runCatching { scan() }.getOrElse {
            XLog.v("【内置规则】读取异常：${it.javaClass.simpleName} ${it.message}")
            return cache
        }
        cache = list
        return list
    }

    /** 下次 [load] 强制重扫（设置页改完规则后可以调，这里留着备用） */
    fun invalidate() {
        nextScanAt = 0L
    }

    private fun scan(): List<String> {
        val ctx = HostContext.app()
        if (ctx == null) {
            report("【内置规则】拿不到宿主 Context（钩子未就绪），本次跳过")
            return emptyList()
        }

        val dir = File(ctx.filesDir, SUB_DIR)
        if (!dir.isDirectory) {
            report("【内置规则】宿主规则目录不存在：${dir.absolutePath}")
            return emptyList()
        }

        // 注意 listFiles 返回的是 Java 数组 Array<File>?，不是 List —— 兜底必须用 emptyArray()
        val files: Array<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()
        if (files.isEmpty()) {
            report("【内置规则】宿主规则目录里没有 .json：${dir.absolutePath}")
            return emptyList()
        }

        val out = ArrayList<String>()
        val detail = StringBuilder()
        for (f in files) {
            val before = out.size
            runCatching {
                if (f.length() > MAX_FILE_BYTES) {
                    detail.append(f.name).append("=超大跳过 ")
                    return@runCatching
                }
                collect(JSONObject(f.readText()).opt("data"), out)
            }.onFailure {
                XLog.v("【内置规则】${f.name} 解析失败：${it.javaClass.simpleName} ${it.message}")
            }
            detail.append(f.name).append('=').append(out.size - before).append(' ')
            if (out.size >= MAX_TOTAL) break
        }

        val trimmed = if (out.size > MAX_TOTAL) out.subList(0, MAX_TOTAL).toList() else out
        report("【内置规则】宿主规则库 ${trimmed.size} 条（$detail）")
        return trimmed
    }

    /**
     * 从 JSON 里把所有像规则的字符串挖出来。
     *
     * 刻意**不假设**元素的结构：`data` 可能是 `["||ads.com^", …]`，也可能是
     * `[{"rule": "||ads.com^", …}, …]`，甚至再嵌一层。递归把字符串捞干净最稳 ——
     * 代价只是多一点启发式过滤，收益是宿主改结构后不用改代码。
     */
    private fun collect(node: Any?, out: MutableList<String>) {
        when (node) {
            is String -> if (looksLikeRule(node)) out.add(node)
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) collect(node.opt(keys.next()), out)
            }
        }
    }

    /** 只看形状，不做语义判断 —— 真正的护栏交给 [AdRuleParser] */
    private fun looksLikeRule(s: String): Boolean {
        if (s.length < 4 || s.length > 512) return false
        val c = s[0]
        if (c == '!' || c == '[' || c == '{') return false
        return s.contains('.') || s.contains("||") || s.contains("##") || s.contains('/')
    }

    private fun report(line: String) {
        if (line == lastReport) return
        lastReport = line
        XLog.i(line)
    }
}
