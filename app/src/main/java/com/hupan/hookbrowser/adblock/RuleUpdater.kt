package com.hupan.hookbrowser.adblock

import android.content.Context
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 「从 URL 导入」的规则集的**更新器**（1.15.13）：手动重下 + 定时自动重下。
 *
 * ## 为什么要把 URL 记住
 *
 * 规则集本来就用 `AdRuleSet.source` 存了来源 —— URL 导入的那一组，`source` 就是那个 URL
 * （见 `ui/page/rules/RuleManagerPage`）。所以「能不能更新」不需要新字段：**来源能解析成
 * http/https 就能更新**。内置规则集的 `source` 是 `builtin:recommended#…`，天然被排除。
 *
 * ## 自动更新的触发点：打开模块界面时
 *
 * 模块进程**不常驻** —— 宿主（浏览器）进程只读 remote preferences，没有写权限；
 * 想让规则库变化，就必须有模块进程活着。而模块进程只在两种时刻存在：用户打开模块界面、
 * 框架拉起模块服务。所以自动更新挂在更可靠的那个上：`MainActivity.onCreate`
 * （界面上会明确写出来，不让用户以为它是后台常驻任务）。
 *
 * 节流 24 小时（按上次**成功**更新时间算，失败下次打开重试），与 `update/UpdateChecker`
 * 同一套策略；时间戳存在**独立** SP（组 `ruleupdate`，不进 remote preferences —— 宿主不需要看到）。
 *
 * ## 自动更新不许毁掉现有规则
 *
 * 远端 404、返回 HTML 错误页、被运营商劫持成广告页，都会让解析结果为 0 条或极少。
 * 所以替换前有两道闸：**解析结果为空直接放弃**；**新条数不足现有的 1/10（且现有 ≥ 200 条）
 * 也放弃**。宁可这次没更新，也不能把用户手上的规则换成垃圾。
 *
 * ## ⚠ 进程边界
 *
 * 本对象写规则库（[AdRuleStore]）+ 读写模块本地 SP，只在**模块进程**调用。
 * 宿主进程不引用它；内置规则与更新后的结果都通过 `adrules` 组流向宿主。
 */
internal object RuleUpdater {

    /** 自动更新相关的本地设置（独立 SP，不镜像给宿主） */
    private const val PREFS = "ruleupdate"
    private const val KEY_AUTO_LAST_AT = "auto_last_at"

    /** 自动更新的节流间隔：24 小时 */
    private const val THROTTLE_MS = 24L * 60 * 60 * 1000

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val UA = "Mozilla/5.0 (Linux; Android) HookBrowser"

    /** 「远端条数断崖」的保护阈值：现有 ≥ 此值时，新条数低于现有的 1/10 就拒绝覆盖 */
    private const val GUARD_MIN_CURRENT = 200

    /**
     * 单份规则文本的读取上限。
     *
     * 1.15.12 及更早是 **512 KB** —— 这是个静默的坑：`anti-ad-adguard.txt` 有 2.2 MB，
     * 截到 512 KB 恰好剩 23,590 条，界面显示「已导入 23590 条」看着挺正常，
     * 实际丢了 77% 的规则，用户完全没有线索。现在放宽到 8 MB（够所有主流列表），
     * 并且**截断时明确上报**（[FetchResult.truncated]）。
     */
    internal const val MAX_FILE_BYTES = 8 * 1024 * 1024

    /** 一次下载的结果：`text` 与 `error` 二选一为 null */
    internal class FetchResult(val text: String?, val truncated: Boolean, val error: String)

    /** 一个规则集的更新结果（界面逐条展示） */
    internal class Outcome(val name: String, val ok: Boolean, val message: String)

    /** 同一时刻只允许一轮更新在飞（手动与自动共用同一把闸） */
    private val inFlight = AtomicBoolean(false)

    // ===================== 网络 =====================

    /** 下载规则文本（协程版，切到 IO）。 */
    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) { fetchBlocking(url) }

    /**
     * 下载规则文本。用 `runCatching` 而不是 try/catch 给同一个 val 赋值 ——
     * Kotlin 的确定赋值分析不覆盖 try/catch，那样写会报 `Val cannot be reassigned`
     * （工程里踩过，见 `verify_static.py` 第 9 项）。
     */
    internal fun fetchBlocking(url: String): FetchResult {
        if (!isUpdatable(url)) return FetchResult(null, false, "只支持 http / https 来源")
        val r = runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val bytes = conn.inputStream.use { it.readBytes() }
                val cut = bytes.size.coerceAtMost(MAX_FILE_BYTES)
                FetchResult(String(bytes, 0, cut, Charsets.UTF_8), bytes.size > cut, "")
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        return r.getOrElse { e -> FetchResult(null, false, friendly(e)) }
    }

    /** 这份来源能不能更新（URL 导入的才能；内置规则集与本地文件不能） */
    fun isUpdatable(source: String): Boolean =
        source.startsWith("http://") || source.startsWith("https://")

    // ===================== 更新 =====================

    /**
     * 更新全部「可更新」的规则集（规则管理页的「立即全部更新」）。
     *
     * 逐个**串行**下载 —— 并发下几个大文件同时吃带宽，在国内网络环境里成功率更低。
     * 某一组失败只记录它自己的结果，不影响后面的组。
     */
    suspend fun updateAll(ctx: Context): List<Outcome> {
        if (!inFlight.compareAndSet(false, true)) {
            return listOf(Outcome("", false, "上一次更新还没结束，请稍候"))
        }
        return try {
            val targets = updatableSets(ctx)
            if (targets.isEmpty()) {
                listOf(Outcome("", false, "没有可更新的规则集（只有从 URL 导入的才能更新）"))
            } else {
                targets.map { applyUpdate(ctx, it) }
            }
        } finally {
            inFlight.set(false)
        }
    }

    /** 更新单独一组（规则管理页每行的「更新」按钮）。 */
    suspend fun updateOne(ctx: Context, id: String): Outcome {
        if (!inFlight.compareAndSet(false, true)) return Outcome("", false, "上一次更新还没结束，请稍候")
        return try {
            val target = runCatching { AdRuleStore.load(ctx) }.getOrDefault(mutableListOf())
                .firstOrNull { it.id == id }
                ?: return Outcome("", false, "规则集不存在（可能已被删除）")
            applyUpdate(ctx, target)
        } finally {
            inFlight.set(false)
        }
    }

    /**
     * 下载 → 解析 → 替换该组。**保留 id / 名称 / 来源 / 启停状态**，只换规则内容与时间戳。
     *
     * 不碰 [inFlight] —— 由调用方负责加锁（[maybeAutoUpdate] 一轮要更新多组，只抢一次）。
     */
    private suspend fun applyUpdate(ctx: Context, target: AdRuleSet): Outcome = withContext(Dispatchers.IO) {
        val fetched = fetchBlocking(target.source)
        val text = fetched.text
        if (text == null) {
            XLog.i("【更新】「${target.name}」失败：${fetched.error}")
            return@withContext Outcome(target.name, false, fetched.error)
        }

        val result = AdRuleParser.parse(text)
        val fresh = result.rules.size + result.elementRules.size
        val current = target.rules.size + target.elementRules.size
        val guard = guardReason(fresh, current)
        if (guard != null) {
            XLog.i("【更新】「${target.name}」放弃覆盖：$guard（现有 $current 条保留）")
            return@withContext Outcome(target.name, false, "$guard，已保留原规则")
        }

        val all = AdRuleStore.load(ctx)
        val idx = all.indexOfFirst { it.id == target.id }
        if (idx < 0) return@withContext Outcome(target.name, false, "规则集已被删除")
        all[idx] = AdRuleSet(
            id = target.id,
            name = target.name,
            source = target.source,
            updatedAt = System.currentTimeMillis(),
            enabled = target.enabled,
            rules = result.rules,
            elementRules = result.elementRules,
        )
        if (!AdRuleStore.save(ctx, all)) {
            return@withContext Outcome(target.name, false, "写入规则库失败")
        }
        val suffix = if (fetched.truncated) "（远端超过 8 MB，已截断）" else ""
        XLog.i(
            "【更新】「${target.name}」已更新：拦截 ${result.rules.size} 条（原 ${target.rules.size}）" +
                " / 隐藏 ${result.elementRules.size} 条（原 ${target.elementRules.size}）$suffix"
        )
        Outcome(
            target.name,
            true,
            "拦截 ${result.rules.size} 条 · 隐藏 ${result.elementRules.size} 条$suffix",
        )
    }

    /** 返回「不该覆盖」的理由；可以覆盖时返回 null。 */
    private fun guardReason(fresh: Int, current: Int): String? = when {
        fresh == 0 -> "远端内容解析不出规则"
        current >= GUARD_MIN_CURRENT && fresh < current / 10 ->
            "远端内容异常（$fresh 条 vs 现有 $current 条）"
        else -> null
    }

    // ===================== 自动更新 =====================

    /**
     * 打开模块界面时的自动更新。
     *
     * **阻塞**：调用方放在后台线程（`MainActivity.onCreate` 那条启动线程）。
     * 三种情况直接返回：开关关着、距上次成功更新不足 24 小时、没有可更新的规则集。
     */
    fun maybeAutoUpdate(ctx: Context) {
        if (!autoEnabled(ctx)) return
        if (System.currentTimeMillis() - lastAutoAt(ctx) < THROTTLE_MS) return
        if (!inFlight.compareAndSet(false, true)) return
        try {
            val targets = updatableSets(ctx)
            if (targets.isEmpty()) return
            var ok = 0
            for (t in targets) {
                if (runCatching { applyUpdateBlocking(ctx, t) }.getOrDefault(false)) ok++
            }
            XLog.i("【更新】自动更新完成：${targets.size} 个规则集，成功 $ok 个")
            // 只在真的更新成功过之后刷新节流时间：全失败下次打开重试（与 UpdateChecker 一致）
            if (ok > 0) prefs(ctx).edit().putLong(KEY_AUTO_LAST_AT, System.currentTimeMillis()).apply()
        } finally {
            inFlight.set(false)
        }
    }

    /** [applyUpdate] 的阻塞包装（自动更新跑在普通后台线程上，没有协程作用域）。 */
    private fun applyUpdateBlocking(ctx: Context, target: AdRuleSet): Boolean =
        kotlinx.coroutines.runBlocking { applyUpdate(ctx, target) }.ok

    /** 当前可更新的规则集：启用中 + 来源是 http(s) */
    private fun updatableSets(ctx: Context): List<AdRuleSet> =
        runCatching { AdRuleStore.load(ctx) }.getOrDefault(mutableListOf())
            .filter { it.enabled && isUpdatable(it.source) }

    /** 上次成功自动更新的时间（0 = 从未；规则管理页显示给用户看） */
    fun lastAutoAt(ctx: Context): Long = prefs(ctx).getLong(KEY_AUTO_LAST_AT, 0L)

    /**
     * 自动更新开关。读本地 SP —— 与 `ui/SettingsPrefs` 同一份数据（组名 [Config.PREFS_NAME]）。
     * 本对象在 `adblock` 包，**不能**引 `ui`（会把 Compose 运行时带进浏览器进程）。
     */
    private fun autoEnabled(ctx: Context): Boolean = runCatching {
        ctx.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Config.AD_RULE_AUTO_UPDATE, Config.defaultOf(Config.AD_RULE_AUTO_UPDATE))
    }.getOrDefault(Config.defaultOf(Config.AD_RULE_AUTO_UPDATE))

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 把底层异常翻成人能看懂的一句话 */
    private fun friendly(t: Throwable): String = when (t) {
        is UnknownHostException -> "无法解析域名：网络不可用或被拦截"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        else -> t.message ?: t.javaClass.simpleName
    }
}
