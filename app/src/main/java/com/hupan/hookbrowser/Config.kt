package com.hupan.hookbrowser

import android.content.SharedPreferences
import com.hupan.hookbrowser.features.QuickLinkRows
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * 模块开关的宿主侧读取入口。
 *
 * - 写在模块进程：`getSharedPreferences("settings", MODE_PRIVATE)`（见 ui/SettingsPrefs）
 * - 读在宿主进程：**`XposedInterface#getRemotePreferences("settings")`**（1.10.0 起）
 * - 宿主进程读不到界面的默认值声明，所以这里必须维护一份与设置页（ui/FeatureCatalog.kt）一致的默认值表
 *
 * ## 1.10.0：跨进程开关终于走官方通道
 *
 * 这条路走过三段弯路，值得记一笔，避免以后再往回退：
 *
 * | 版本 | 通道 | 结果 |
 * |---|---|---|
 * | ≤1.6.5 | `XSharedPreferences` | 异步加载 + 权限 0660 → 宿主进程**读出来是空表**，默认 false 的开关永远开不起来 |
 * | 1.6.6 / 1.6.7 | 自己扫路径 + 直读 XML | 同步可靠；但靠**文件权限**（0660 → o+r）硬撑 |
 * | 1.9.8 | 删 `XSharedPreferences` 消除废弃警告 | LSPosed 不再重定向 → SP 落进 `/data/user_de/0/<pkg>/shared_prefs/`（uid 私有，宿主连父目录都进不去）→ **功能全失效** |
 * | 1.9.9 | 试图镜像到 `/data/local/tmp` | **前提不成立**：`fix()` 跑在模块进程（普通 app uid），根本写不了该目录 |
 * | **1.10.0** | **`getRemotePreferences`（API 102）** | 存框架数据库，跨进程直读，**不需要任何权限 hack** |
 *
 * 所以 1.10.0 把 `candidateFiles()` / `parsePrefsXml` / `describe` / `fileMode` / `PrefsFileAccess`
 * 这一整套文件机制**全部删除** —— 它们存在的唯一理由就是绕开旧 API 的读写鸿沟。
 *
 * 默认值的取舍原则：**默认值必须等于「装了模块但什么都不改」的保守行为**。
 * 凡是会削弱宿主功能、暴露隐藏项、降低安全性的开关，一律默认 false。
 * **唯一例外**是 `ui_search_engine`（1.7.0 起默认 true）：这是用户明确要求的「常用引擎内置、
 * 装上即用」，且只注入引擎数据 + 改默认搜索引擎，不削弱宿主任何能力。
 */
internal object Config {

    const val MODULE_PKG = "com.hupan.hookbrowser"

    /** remote preferences 组名，与模块设置页写入时用的名字必须一致 */
    const val PREFS_NAME = "settings"

    // ---- 开关 key，必须与 ui/FeatureCatalog.kt 的功能目录完全一致 ----
    /** 总开关：关掉后所有 hook 直接变成 no-op，等于临时卸下模块 */
    const val MASTER = "master_enabled"
    const val AD_SPLASH = "ad_splash"
    const val AD_HOME_PROMO = "ad_home_promo"
    const val AD_SEARCH_SUG = "ad_search_sug"

    /** 1.9.7：错误页（「无法访问」）底部的「热搜榜」推广榜单 */
    const val AD_ERROR_PAGE_HOT = "ad_error_page_hot"
    const val UI_DOWNLOAD = "ui_download"
    const val UA_PATCH = "ua_patch"
    const val UA_MODE = "ua_mode"
    const val MISC_UNLOCK_PREF = "misc_unlock_pref"
    const val MISC_DEBUG = "misc_debug"
    const val MISC_SECURITY = "misc_security"
    const val MISC_HOST_AD = "misc_host_ad"
    const val AD_CUSTOM_RULES = "ad_custom_rules"

    /** 1.9.2：用自定义规则接管宿主 native 拦截引擎（改它的规则文件），破坏性操作，默认关 */
    const val AD_HOST_OVERRIDE = "ad_host_override"

    /** 1.14.0：用户脚本（油猴式 .user.js），执行的是用户自选代码，默认关 */
    const val SCRIPT_USERSCRIPTS = "script_userscripts"
    const val UI_SEARCH_ENGINE = "ui_search_engine"
    const val SEARCH_ENGINE_TARGET = "ui_search_engine_target"

    /** 1.15.0：简洁版主页「快捷方式」最多几行（主开关，默认关 = 完全不干预宿主的排布） */
    const val UI_QUICKLINK_ROWS = "ui_quicklink_rows"

    /** 1.15.0：上面那个开关的行数取值（"3" / "4" / "5"，见 features/QuickLinkRows） */
    const val UI_QUICKLINK_ROWS_VALUE = "ui_quicklink_rows_value"

    /** 默认值表：与 ui/FeatureCatalog.kt 的 `Toggle.default` 同源（界面读 [defaultOf]） */
    private val DEFAULTS = linkedMapOf(
        MASTER to true,
        AD_SPLASH to true,
        AD_HOME_PROMO to true,
        AD_SEARCH_SUG to true,
        // 1.9.7：错误页热搜榜。默认开 —— 拦的是一条纯推广榜单（跳大米搜索，不是用户原本要访问的
        // 站点），关掉不影响错误提示、重试 / 回首页 / 网络检查 / 网页诊断等按钮，也不影响
        // 「搜索发现」「猜你想搜」两条真正的搜索建议。
        AD_ERROR_PAGE_HOT to true,
        UI_DOWNLOAD to true,
        UA_PATCH to true,
        MISC_HOST_AD to true,
        // 1.8.0：自定义拦截规则。默认开**不等于**默认拦东西 —— 规则库为空时引擎是 no-op，
        // 行为与没装模块完全一致；用户导入规则后无需再回来开一次开关。
        AD_CUSTOM_RULES to true,
        // 1.9.2：接管宿主拦截引擎（改写它的 files/data/adblock 规则文件 + 清空小米白名单）。
        // **默认 false**：这是唯一会改动宿主自身数据的开关，且做的是"换掉小米下发的规则"这种
        // 破坏性动作。用户明确要求时才开；关闭后自动从备份还原。
        AD_HOST_OVERRIDE to false,
        // 1.14.0：用户脚本。**默认 false**：脚本在页面上下文执行的是用户自选代码，
        // 能力等同网页自身；保守起步，开启前走风险确认。
        SCRIPT_USERSCRIPTS to false,
        // 1.7.0：用户明确要求「常用引擎内置、无需在模块里手动开」→ 装上即生效（默认引擎 bing）
        UI_SEARCH_ENGINE to true,
        // 1.15.0：主页快捷方式行数。默认关 —— 关着时一个 hook 都不生效，宿主怎么排就怎么排，
        // 只有用户主动开（并选了行数）才接管。
        UI_QUICKLINK_ROWS to false,
        MISC_UNLOCK_PREF to false,
        MISC_DEBUG to false,
        MISC_SECURITY to false
    )

    private const val UA_MODE_DEFAULT = "chrome"

    /** 默认搜索引擎 key；与 arrays.xml 的 search_engine_values 一致 */
    private const val SEARCH_ENGINE_DEFAULT = "bing"

    /** 框架接口；由 [MainHook] 注入 */
    @Volatile
    private var iface: XposedInterface? = null

    /**
     * 按组名取框架的 remote preferences。
     *
     * 返回的就是 `android.content.SharedPreferences` —— 框架把模块数据库包装成标准接口，
     * 所以直接用 `getBoolean` / `getString` / `all`，不需要任何私有类型。
     *
     * [on] 走默认组 [PREFS_NAME]；规则库（`adblock.AdRuleStore`）用另一个组 [AdRuleChannel.PREFS]。
     * 句柄按组名缓存 —— 每次 `getRemotePreferences` 都是一次跨进程往返。
     *
     * ⚠ 嵌入式框架（embedded framework）下此方法会抛 `UnsupportedOperationException`；
     * 这里统一吞成 null，功能退化为「全部开关用默认值」，宿主照常可用。
     */
    private val handles = ConcurrentHashMap<String, SharedPreferences>()

    fun remotePrefs(group: String): SharedPreferences? {
        handles[group]?.let { return it }
        val x = iface ?: return null
        return try {
            val h = x.getRemotePreferences(group)
            handles[group] = h
            h
        } catch (t: Throwable) {
            XLog.e("getRemotePreferences($group) 失败（嵌入式框架不支持远程首选项？）", t)
            null
        }
    }

    /**
     * 框架的开关句柄（组 [PREFS_NAME]）。
     *
     * 惰性建立并**只建一次** —— `getRemotePreferences` 每次调用都是一次跨进程往返，
     * 而 [on] 会被 hook 回调高频调用（宿主启动阶段尤其密集）。建立失败也不再重试，
     * 免得每次回调都去吃一次异常。
     */
    @Volatile
    private var remote: SharedPreferences? = null

    private var remoteFailed = false

    /**
     * 值缓存。
     *
     * 框架给的 `SharedPreferences` 虽然支持变更监听，但回调线程不确定、且每次读都是一次
     * 跨进程往返；这里加一层短 TTL 缓存，把「一次页面渲染里几十次 `on()`」压成一次读取。
     */
    private val boolCache = ConcurrentHashMap<String, Boolean>()
    private val strCache = ConcurrentHashMap<String, String>()
    private var lastRefresh = 0L
    private const val CACHE_TTL_MS = 1000L

    private var readFailureLogged = false
    private var failureTicks = 0

    /** 由 [MainHook] 在 `onModuleLoaded` / `onPackageLoaded` 里调用；重复调用无副作用 */
    fun attach(x: XposedInterface) {
        iface = x
    }

    private fun handle(): SharedPreferences? {
        remote?.let { return it }
        if (remoteFailed) return null
        val x = iface ?: return null
        synchronized(this) {
            remote?.let { return it }
            if (remoteFailed) return null
            return try {
                x.getRemotePreferences(PREFS_NAME).also {
                    remote = it
                    XLog.v(
                        "【诊断】remote preferences 已就绪（组=$PREFS_NAME），" +
                            "初始 ${it.all.size} 个开关：${it.all.keys.sorted()}"
                    )
                }
            } catch (t: Throwable) {
                remoteFailed = true
                XLog.e("getRemotePreferences($PREFS_NAME) 失败，全部开关回退默认值", t)
                null
            }
        }
    }

    /** 当前读到的全部开关（诊断用） */
    private fun snapshot(): Map<String, *>? =
        runCatching { handle()?.all }.getOrNull()

    private fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastRefresh < CACHE_TTL_MS) return
        lastRefresh = now
        val snap = snapshot()
        if (snap == null) {
            noteFailure(null)
            return
        }
        failureTicks = 0
        boolCache.clear()
        strCache.clear()
        for ((k, v) in snap) {
            when (v) {
                is Boolean -> boolCache[k] = v
                is String -> {
                    strCache[k] = v
                    // 宿主读不到 XML 的 app:defaultValue，字符串形式的真假值也认一下
                    v.toBooleanStrictOrNull()?.let { boolCache[k] = it }
                }
            }
        }
    }

    /** 读不到配置时的降噪计数：连续几次才打一行，避免热路径刷屏 */
    private fun noteFailure(key: String?) {
        if (readFailureLogged) return
        if (++failureTicks < 3) return
        readFailureLogged = true
        XLog.i(
            "读取开关失败，回退到默认值" +
                (key?.let { "（$it=${DEFAULTS[it] ?: false}）" } ?: "（remote preferences 不可用）")
        )
    }

    private fun raw(key: String): Any? {
        refreshIfStale()
        boolCache[key]?.let { return it }
        strCache[key]?.let { return it }
        return null
    }

    /**
     * 读开关；未写入过的 key 用 [DEFAULTS] 的默认值。
     *
     * 注意：这里**不会**因为读不到配置就放宽行为 —— 读不到就按默认值走，
     * 而默认值一律是保守值，绝不允许出现「读不到配置 = 全功能打开」。
     */
    fun on(key: String): Boolean {
        val def = DEFAULTS[key] ?: false
        val r = raw(key)
        val v = when (r) {
            is Boolean -> r
            is String -> r.toBooleanStrictOrNull() ?: def
            else -> def
        }
        if (r == null) noteFailure(key)
        return v
    }

    /** 总开关单独走一条：任何单功能开关都不能绕开它 */
    fun masterOn(): Boolean = on(MASTER)

    fun uaMode(): String =
        (raw(UA_MODE) as? String)?.takeIf { it.isNotBlank() } ?: UA_MODE_DEFAULT

    /** 目标搜索引擎 key（bing/google/yandex/baidu）；没写入或值非法时回退默认 */
    fun searchEngineTarget(): String =
        (raw(SEARCH_ENGINE_TARGET) as? String)?.takeIf { it.isNotBlank() } ?: SEARCH_ENGINE_DEFAULT

    /**
     * 主页快捷方式的行数上限（1.15.0）。
     *
     * 返回 0 表示「不限制」：开关关着、或值读不出来时都是 0，宿主维持自己的排布。
     * 读不到配置不会放宽行为 —— 这里宁可不管，也不猜一个行数去截断。
     */
    fun quickLinkRows(): Int {
        if (!on(UI_QUICKLINK_ROWS)) return 0
        val v = (raw(UI_QUICKLINK_ROWS_VALUE) as? String)?.trim().orEmpty()
        return v.toIntOrNull()?.takeIf { it > 0 } ?: QuickLinkRows.DEFAULT.toIntOrNull() ?: 0
    }

    /** 供设置页首次运行时铺默认值，保证模块进程与宿主进程看到同一套值 */
    fun defaultEntries(): Map<String, Boolean> = DEFAULTS

    /**
     * 某个开关的默认值（设置页展示用）。
     *
     * 1.11.0 起界面不再读 `prefs.xml` 的 `app:defaultValue`（那份 XML 随界面重建删除），
     * 默认值只能有一个真源 —— 就是这里。未登记的 key 按保守值 false 处理。
     */
    fun defaultOf(key: String): Boolean = DEFAULTS[key] ?: false
}
