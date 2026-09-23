/*
 * 开关状态的唯一持有者（跨页面共享）。
 *
 * 为什么不让 MainPage 自己持有：设置页（二级页）要能「把全部开关恢复默认」，
 * 重置后主界面的列表必须立刻同步 —— 状态若关在 MainPage 的 remember 里，
 * 从设置页返回时那份 remember 不会重跑，界面就会停在旧值上。
 *
 * 两组状态：
 * - [values]：布尔开关（界面上的绝大多数项）；
 * - [strings]：字符串开关（UA 模式、默认搜索引擎），与布尔分开存是为了读值时不用做类型判断。
 *
 * 本对象只在模块进程（ui 包）里使用，hook 侧不引用。
 */
package com.hupan.hookbrowser.ui.page

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.features.QuickLinkRows
import com.hupan.hookbrowser.features.SearchEngines
import com.hupan.hookbrowser.features.UaBuilder
import com.hupan.hookbrowser.ui.FUNCTION_TOGGLES
import com.hupan.hookbrowser.ui.SettingsPrefs

internal object ToggleState {

    private var prefs: SettingsPrefs? = null

    /** 开关键 → 当前值。读取即参与组合，值变了相关页面自动重组。 */
    val values = mutableStateMapOf<String, Boolean>()

    /** 字符串开关键 → 当前值（UA 模式 / 搜索引擎） */
    val strings = mutableStateMapOf<String, String>()

    /** 当前总闸状态（各页面都要判它来决定子项是否可点） */
    val masterOn: Boolean
        get() = values[Config.MASTER] ?: Config.defaultOf(Config.MASTER)

    /** 首次进入主界面时装载。幂等：已装载过就直接返回。 */
    fun ensure(context: Context) {
        val store = store(context).also { it.ensureDefaults() }
        if (values.isNotEmpty()) return
        load(store)
    }

    fun set(context: Context, key: String, value: Boolean) {
        values[key] = value
        store(context).setBoolean(key, value)
    }

    fun setString(context: Context, key: String, value: String) {
        strings[key] = value
        store(context).setString(key, value)
    }

    /** 全部开关（含两个下拉）恢复默认值，并把新值同步回内存状态。 */
    fun resetToDefaults(context: Context) {
        val store = store(context)
        store.resetToDefaults()
        values.clear()
        strings.clear()
        load(store)
    }

    /** 诊断页用：当前全部开关的一行文本 */
    fun dump(context: Context): String {
        val store = store(context)
        val lines = mutableListOf<String>()
        lines += "模块开关（组 ${Config.PREFS_NAME}）"
        values.keys.sorted().forEach { key ->
            lines += "$key=${values[key]}"
        }
        strings.keys.sorted().forEach { key ->
            lines += "$key=${strings[key]}"
        }
        lines += "本地 SP 共 ${store.all().size} 个键"
        return lines.joinToString("\n")
    }

    private fun store(context: Context): SettingsPrefs =
        prefs ?: SettingsPrefs(context.applicationContext).also { prefs = it }

    private fun load(store: SettingsPrefs) {
        FUNCTION_TOGGLES.forEach { values[it.key] = store.getBoolean(it.key, it.default) }
        values[Config.MASTER] = store.getBoolean(Config.MASTER, Config.defaultOf(Config.MASTER))
        strings[Config.UA_MODE] = store.getString(Config.UA_MODE, UaBuilder.MODE_CHROME)
        strings[Config.SEARCH_ENGINE_TARGET] =
            store.getString(Config.SEARCH_ENGINE_TARGET, SearchEngines.DEFAULT)
        strings[Config.UI_QUICKLINK_ROWS_VALUE] =
            store.getString(Config.UI_QUICKLINK_ROWS_VALUE, QuickLinkRows.DEFAULT)
    }
}
