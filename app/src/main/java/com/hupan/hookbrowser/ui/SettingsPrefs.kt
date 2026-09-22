package com.hupan.hookbrowser.ui

import android.content.Context
import android.content.SharedPreferences
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.features.SearchEngines
import com.hupan.hookbrowser.features.UaBuilder

/**
 * 设置侧的配置读写（运行在<b>模块自己的进程</b>）。
 *
 * <p>本地 SP 是**唯一真源**；每次写入后经 [ModuleService] 把整组**全量镜像**到框架的
 * remote preferences —— 宿主进程（小米浏览器）读的就是那个组（见 `Config` 的版本对照表）。
 *
 * <p>1.11.0 起界面不再用 `PreferenceFragmentCompat`，因此没有框架的 `PreferenceManager`
 * 帮我们建 SP：组名必须显式写成 [Config.PREFS_NAME]，与宿主读的那个组**同名**。
 */
internal class SettingsPrefs(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)

    fun getBoolean(key: String, def: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, def) }.getOrDefault(def)

    fun getString(key: String, def: String): String =
        runCatching { prefs.getString(key, def) }.getOrNull() ?: def

    fun setBoolean(key: String, value: Boolean) {
        runCatching { prefs.edit().putBoolean(key, value).apply() }
        push()
    }

    fun setString(key: String, value: String) {
        runCatching { prefs.edit().putString(key, value).apply() }
        push()
    }

    /**
     * 首次启动时把默认值落盘并同步到框架。
     *
     * <p>配置不存在时，hook 侧只会拿到「构造默认值」；提前落盘能让状态更可预期，
     * 也保证「装完模块 → 开浏览器」这条路径上宿主读到的开关与设置页一致。
     */
    fun ensureDefaults() {
        if (prefs.contains(Config.MASTER)) return
        writeDefaults()
    }

    /** 把全部开关写回模块默认值（设置页的「重置」用）。与首次落盘走同一条路径。 */
    fun resetToDefaults() = writeDefaults()

    /** 当前全部开关（诊断页用） */
    fun all(): Map<String, *> = runCatching { prefs.all }.getOrDefault(emptyMap<String, Any>())

    private fun writeDefaults() {
        prefs.edit().apply {
            Config.defaultEntries().forEach { (key, value) -> putBoolean(key, value) }
            putString(Config.UA_MODE, UaBuilder.MODE_CHROME)
            putString(Config.SEARCH_ENGINE_TARGET, SearchEngines.DEFAULT)
        }.apply()
        push()
    }

    private fun push() {
        ModuleService.push(appContext, Config.PREFS_NAME)
    }
}
