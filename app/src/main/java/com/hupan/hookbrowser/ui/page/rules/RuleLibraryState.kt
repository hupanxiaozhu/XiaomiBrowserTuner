/*
 * 规则库的**界面侧**状态与统计（1.12.0 新增）。
 *
 * 原来这些散在 RuleManagerActivity（统计）与 RulesPage（概况卡）两处：
 * 管理页是独立 Activity，RulesPage 靠 `ActivityResult` 回调在返回时重读一次规则库。
 * 1.12.0 把管理页改成同进程的二级页之后那条回调没有了，改用一个进程内可见的
 * [RuleLibraryState.revision] 修订号 —— 管理页每改一次 +1，概况卡据此重算。
 *
 * 只在模块进程（ui 包）使用，hook 侧不引用。
 */
package com.hupan.hookbrowser.ui.page.rules

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hupan.hookbrowser.adblock.AdRuleCodec
import com.hupan.hookbrowser.adblock.AdRuleSet
import com.hupan.hookbrowser.adblock.AdRuleStore
import com.hupan.hookbrowser.scripts.UserScriptStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 规则库概况：规则集数 / 启用中的条数 / 总条数 / 脚本数。 */
internal class RuleSummary(
    val sets: Int,
    val enabledSets: Int,
    val enabledRules: Int,
    val enabledElements: Int,
    val totalRules: Int,
    val totalElements: Int,
    val scripts: Int,
    val enabledScripts: Int,
) {
    val empty: Boolean get() = sets == 0 && scripts == 0
}

/** 规则库修订号：管理页的每一次增删改都会 +1，让「规则」Tab 的概况卡重算。 */
internal object RuleLibraryState {

    var revision by mutableStateOf(0)
        private set

    fun bump() {
        revision++
    }
}

/**
 * 读规则库并算出概况。
 *
 * ⚠ 这是**磁盘 IO + 全量规则解析**（[AdRuleCodec.enabledRules] 会把启用中的规则拍平成表），
 * 只在 `Dispatchers.IO` 上调用 —— 规则库上规模后这个开销不能放在主线程。
 */
internal fun loadSummary(context: Context): RuleSummary {
    val sets = runCatching { AdRuleStore.load(context) }.getOrDefault(mutableListOf())
    val scripts = runCatching { UserScriptStore.load(context) }.getOrDefault(mutableListOf())
    return RuleSummary(
        sets = sets.size,
        enabledSets = sets.count { it.enabled },
        enabledRules = AdRuleCodec.enabledRules(sets).size,
        enabledElements = AdRuleCodec.enabledElements(sets).size,
        totalRules = AdRuleCodec.totalRules(sets),
        totalElements = AdRuleCodec.totalElements(sets),
        scripts = scripts.size,
        enabledScripts = scripts.count { it.enabled },
    )
}

/**
 * 规则集一行的元信息：`12 条拦截 + 30 条隐藏 · 2026-09-18 22:10 · https://…/easylist.txt`。
 *
 * 列表行与「查看」浮层共用一份格式化，免得两处对同一条规则的描述不一致。
 */
internal fun ruleMeta(set: AdRuleSet): String {
    val time = if (set.updatedAt > 0) {
        // SimpleDateFormat 不是线程安全的，套一层同步 —— 列表与浮层可能在不同重组里同时调
        synchronized(META_FMT) { META_FMT.format(Date(set.updatedAt)) }
    } else {
        "-"
    }
    val src = if (set.source.length > 56) set.source.take(56) + "…" else set.source
    val counts = if (set.elementRules.isEmpty()) {
        "${set.rules.size} 条拦截"
    } else {
        "${set.rules.size} 条拦截 + ${set.elementRules.size} 条隐藏"
    }
    return "$counts · $time · $src"
}

private val META_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
