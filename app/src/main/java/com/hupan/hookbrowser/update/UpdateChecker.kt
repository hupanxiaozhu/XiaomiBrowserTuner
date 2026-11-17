/*
 * 「检查更新」的取数层（只跑在模块进程，hook 侧不引用本包）。
 *
 * 数据源：GitHub Releases 官方接口（无需 token）：
 *   GET https://api.github.com/repos/hupanxiaozhu/XiaomiBrowserTuner/releases/latest
 *
 * 刻意零依赖：
 * - 网络用 JDK 自带的 HttpURLConnection（OkHttp 要多背约 800KB，不值得）；
 * - JSON 用工程已有的 kotlinx-serialization-json 解析，不引第三方解析器。
 *
 * 为什么要一个独立的本地 SP（组名 "update"，**不进** remote preferences）：
 * - 「自动检查」开关与「上次检查时间」只有模块界面自己消费，宿主不需要看到；
 * - 走 Config.PREFS_NAME 会被 ModuleService 全量镜像进框架数据库，白占一条。
 *
 * 请求节流：自动检查 24 小时至多一次（按上次**成功**检查时间算，失败下次打开重试，
 * 失败也不弹任何提示）；手动检查绕过节流，但同一时刻只允许一个请求在飞。
 */
package com.hupan.hookbrowser.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

/** 一次成功检查得到的新版本信息（已是最新时上层拿到 null） */
internal class ReleaseInfo(
    /** 最新版本号（去掉 v 前缀，如 "1.13.0"） */
    val version: String,
    /** Release 说明里的条目（从 Markdown 正文逐行提取） */
    val notes: List<String>,
    /** Release 资产里的 APK 直链（没有则为 null，上层退回 Releases 页） */
    val apkUrl: String?,
    /** Releases 页地址（兜底的下载入口） */
    val releaseUrl: String,
)

internal object UpdateChecker {

    const val REPO = "hupanxiaozhu" + "/XiaomiBrowserTuner"

    private const val API_LATEST =
        "https://api.github.com/repos/$REPO/releases/latest"

    /** 更新相关的本地 SP（独立于开关表，不镜像给宿主） */
    private const val PREFS = "update"
    private const val KEY_AUTO = "auto_check"
    private const val KEY_LAST_AT = "last_check_at"

    /** 自动检查的节流间隔：24 小时 */
    private const val THROTTLE_MS = 24L * 60 * 60 * 1000

    /** 请求超时：连不上就快速放弃，不吊着界面（国内访问 api.github.com 不稳是常态） */
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    /** Release 说明最多展示的条数，防止超长正文把浮层撑爆 */
    private const val MAX_NOTES = 30

    /**
     * 响应体上限（2.0.1）。
     *
     * 这个接口的正常返回只有几 KB。原来直接 `readBytes()` 没有任何上限 ——
     * 代理插页 / 异常大响应会把整段读进内存。512 KB 已经非常宽裕。
     */
    private const val MAX_BODY_BYTES = 512 * 1024

    private val json = Json { ignoreUnknownKeys = true }
    private val inFlight = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    /** 检查线程（2.0.1）：单线程池替掉每次 `new Thread`，同一时刻仍只允许一个请求在飞 */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "hookbrowser-update-check").apply { isDaemon = true }
    }

    // ---- 自动检查的本地设置 ----

    fun isAutoCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoCheckEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, value).apply()
    }

    /** 当前安装的模块版本号（去 v 前缀；读不到按 0.0.0 处理，保证「有新版可发现」） */
    fun currentVersion(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.removePrefix("v").orEmpty().ifBlank { "0.0.0" }

    // ---- 两个入口 ----

    /**
     * 打开应用时的自动检查：开关开着、距上次成功检查超过 24 小时才真正发请求。
     * 结果回调到主线程；**失败对调用方不可见**（Result.failure 也照常回调，
     * 但界面层对自动检查静默处理）。
     */
    fun maybeAutoCheck(context: Context, onResult: (Result<ReleaseInfo?>) -> Unit) {
        if (!isAutoCheckEnabled(context)) return
        val last = prefs(context).getLong(KEY_LAST_AT, 0L)
        if (System.currentTimeMillis() - last < THROTTLE_MS) return
        checkAsync(context.applicationContext, onResult)
    }

    /** 关于页的手动检查：绕过节流，结果无论成败都回调（回调在主线程）。 */
    fun checkNow(context: Context, onResult: (Result<ReleaseInfo?>) -> Unit) {
        checkAsync(context.applicationContext, onResult)
    }

    private fun checkAsync(appContext: Context, onResult: (Result<ReleaseInfo?>) -> Unit) {
        // 同一时刻只飞一个请求；手动点太快时给个明确的失败，别让界面停在「检查中」
        if (!inFlight.compareAndSet(false, true)) {
            main.post {
                onResult(Result.failure(IllegalStateException("上一次检查还没结束，请稍候")))
            }
            return
        }
        worker.execute {
            val result: Result<ReleaseInfo?> = try {
                Result.success(fetchLatest(appContext))
            } catch (t: Throwable) {
                Result.failure(IOException(friendly(t)))
            }
            // 只在成功（含「已是最新」）时刷新节流时间：失败下次打开重试
            if (result.isSuccess) {
                prefs(appContext).edit()
                    .putLong(KEY_LAST_AT, System.currentTimeMillis())
                    .apply()
            }
            // 2.0.1：先复位再回调。原来放在主线程 post 里，主线程一卡，「上一次还没结束」
            // 这句提示就会一直挂着，手点检查再也发不出请求。
            inFlight.set(false)
            main.post { onResult(result) }
        }
    }

    // ---- 请求与解析 ----

    /**
     * 拉取最新 Release 并与当前版本比较。
     *
     * 返回 null 表示已是最新；抛异常表示检查失败（网络 / 限流 / 解析）。
     */
    private fun fetchLatest(context: Context): ReleaseInfo? {
        val conn = URL(API_LATEST).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "XiaomiBrowserTuner-UpdateCheck")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val hint = if (code == 403) "（GitHub 接口限流，请稍后再试）" else ""
                throw IOException("GitHub 接口返回 HTTP $code$hint")
            }
            val text = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream(8 * 1024)
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BODY_BYTES) {
                        throw IOException("响应体超过 ${MAX_BODY_BYTES / 1024} KB 上限，已放弃")
                    }
                    buf.write(chunk, 0, n)
                }
                buf.toString("UTF-8")
            }
            val release = json.decodeFromString(GhRelease.serializer(), text)
            val latest = release.tagName.removePrefix("v").trim()
            if (compareVersions(latest, currentVersion(context)) <= 0) return null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }?.url
            return ReleaseInfo(latest, parseNotes(release.body), apk, release.htmlUrl)
        } finally {
            conn.disconnect()
        }
    }

    /** GitHub Release 的正文是 Markdown；逐行提取要点，没有要点就退回原始行 */
    private fun parseNotes(body: String?): List<String> {
        if (body.isNullOrBlank()) return emptyList()
        return body.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_NOTES)
    }

    // 版本号比较已移到同包的顶层函数 compareVersions()（update/VersionCompare.kt）：
    // 本 object 构造里就要 new Handler(Looper.getMainLooper())，JVM 单测连初始化都做不到，
    // 抽出去之后那段边界逻辑才钉得住。

    /** 把底层异常翻成用户能看懂的一句话（手动检查的失败提示直接展示它） */
    private fun friendly(t: Throwable): String = when (t) {
        is UnknownHostException -> "无法解析 api.github.com：网络不可用或被拦截"
        is SocketTimeoutException -> "连接 GitHub 超时（国内网络环境下较常见），请稍后重试"
        else -> t.message ?: t.javaClass.simpleName
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** GitHub releases/latest 的响应里本模块关心的字段（其余字段忽略） */
@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
)
