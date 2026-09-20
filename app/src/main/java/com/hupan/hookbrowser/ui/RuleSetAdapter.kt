package com.hupan.hookbrowser.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.hupan.hookbrowser.R
import com.hupan.hookbrowser.adblock.AdRuleSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 规则集列表。一行 = 一个导入的规则集：启用勾选 + 名称/元信息 + 查看 / 删除。
 *
 * 三个操作全部回抛给 Activity 处理（适配器不碰存储）—— 界面只管展示，
 * 落盘、去重、提示这些都在 [RuleManagerActivity] 里一处完成。
 */
internal class RuleSetAdapter(
    private val context: Context,
    private val onToggle: (AdRuleSet, Boolean) -> Unit,
    private val onView: (AdRuleSet) -> Unit,
    private val onDelete: (AdRuleSet) -> Unit
) : BaseAdapter() {

    private val items = ArrayList<AdRuleSet>()

    fun submit(list: List<AdRuleSet>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_rule_set, parent, false)

        val set = items[position]

        view.findViewById<TextView>(R.id.tv_rule_name).text = set.name
        view.findViewById<TextView>(R.id.tv_rule_meta).text = meta(set)

        // 复用 convertView 时必须先摘掉旧监听，否则复用的勾选框会把上一行的状态写回本行
        val cb = view.findViewById<CheckBox>(R.id.cb_rule_on)
        cb.setOnCheckedChangeListener(null)
        cb.isChecked = set.enabled
        cb.setOnCheckedChangeListener { _, checked -> onToggle(set, checked) }

        view.findViewById<Button>(R.id.btn_rule_view).setOnClickListener { onView(set) }
        view.findViewById<Button>(R.id.btn_rule_delete).setOnClickListener { onDelete(set) }

        return view
    }

    /** `12 条拦截 + 30 条隐藏 · 2026-09-18 22:10 · https://…/easylist.txt` */
    private fun meta(set: AdRuleSet): String {
        val time = if (set.updatedAt > 0) FMT.format(Date(set.updatedAt)) else "-"
        val src = if (set.source.length > 56) set.source.take(56) + "…" else set.source
        val counts = if (set.elementRules.isEmpty()) {
            "${set.rules.size} 条拦截"
        } else {
            "${set.rules.size} 条拦截 + ${set.elementRules.size} 条隐藏"
        }
        return "$counts · $time · $src"
    }

    private companion object {
        val FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}
