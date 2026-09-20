package com.hupan.hookbrowser.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hupan.hookbrowser.R
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.adblock.AdRuleCodec
import com.hupan.hookbrowser.adblock.AdRuleParser
import com.hupan.hookbrowser.adblock.AdRuleSet
import com.hupan.hookbrowser.adblock.AdRuleStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 自定义拦截规则的管理页（跑在**模块自己的进程**，与宿主无关）。
 *
 * 能做的事：
 * - 从 **URL 链接**导入（`HttpURLConnection` 下载 → 解析 → 落盘）
 * - 从 **本地文本文件**导入（SAF 文件选择器，只读打开）
 * - 查看每个规则集的规则明细、启用/停用、删除
 *
 * 落盘后宿主进程会在 8 秒内自动重载（见 `CustomAdBlockFeature` 的轮询线程），
 * 所以这里的任何改动都不需要重启浏览器 —— 界面上会明确这么告诉用户。
 *
 * 这里**不编译规则引擎**：解析结果只做数量统计与合法性过滤，
 * 真正编译成匹配结构的是宿主进程（`AdRuleEngine.compile`），两边的护栏是同一套
 * （`AdRuleParser`），不会出现「模块进程能存、宿主进程跑不起来」的错位。
 */
class RuleManagerActivity : AppCompatActivity() {

    private lateinit var adapter: RuleSetAdapter
    private lateinit var tvInfo: TextView
    private lateinit var tvEmpty: TextView

    private val sets = ArrayList<AdRuleSet>()

    /**
     * 系统文件选择器。
     *
     * 用 SAF（`OpenDocument`）而不是直接读路径：不需要任何存储权限，
     * 用户选什么文件我们就只拿到那一个 uri 的读权限，模块自身零权限扩张。
     */
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFromFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_manager)

        tvInfo = findViewById(R.id.tv_rule_info)
        tvEmpty = findViewById(R.id.tv_rule_empty)

        adapter = RuleSetAdapter(
            this,
            onToggle = { set, enabled -> toggle(set, enabled) },
            onView = { set -> showRules(set) },
            onDelete = { set -> confirmDelete(set) }
        )
        findViewById<ListView>(R.id.list_rule_sets).adapter = adapter
        findViewById<Button>(R.id.btn_import_url).setOnClickListener { askUrl() }
        findViewById<Button>(R.id.btn_import_file).setOnClickListener {
            // `*/*`：规则文件的 mime 五花八门（text/plain、application/octet-stream…），别把用户挡在外面
            pickFile.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ===================== 列表 =====================

    private fun refresh() {
        sets.clear()
        sets.addAll(AdRuleStore.load(this))
        adapter.submit(sets)
        tvEmpty.visibility = if (sets.isEmpty()) View.VISIBLE else View.GONE
        renderInfo()
    }

    private fun renderInfo() {
        tvInfo.text = if (sets.isEmpty()) {
            getString(R.string.rule_mgr_info_none)
        } else {
            getString(
                R.string.rule_mgr_info,
                sets.size,
                AdRuleCodec.totalRules(sets),
                AdRuleCodec.totalElements(sets),
                AdRuleCodec.enabledRules(sets).size + AdRuleCodec.enabledElements(sets).size
            )
        }
    }

    private fun toggle(set: AdRuleSet, enabled: Boolean) {
        val all = AdRuleStore.load(this)
        for (s in all) if (s.id == set.id) s.enabled = enabled
        AdRuleStore.save(this, all)
        toast(getString(R.string.rule_saved_hint))
        renderInfo()
    }

    private fun showRules(set: AdRuleSet) {
        val text = buildString {
            val show = minOf(set.rules.size, MAX_SHOW_RULES)
            for (i in 0 until show) append(set.rules[i]).append('\n')
            if (set.rules.size > show) append(getString(R.string.rule_view_more, set.rules.size - show)).append('\n')

            if (set.elementRules.isNotEmpty()) {
                append(getString(R.string.rule_view_elements, set.elementRules.size))
                val es = minOf(set.elementRules.size, MAX_SHOW_RULES)
                for (i in 0 until es) append(set.elementRules[i]).append('\n')
                if (set.elementRules.size > es) {
                    append(getString(R.string.rule_view_more, set.elementRules.size - es))
                }
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(
                getString(
                    R.string.rule_view_title, set.name,
                    set.rules.size, set.elementRules.size
                )
            )
            .setMessage(text)
            .setPositiveButton(R.string.rule_dlg_close, null)
            .show()
    }

    private fun confirmDelete(set: AdRuleSet) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rule_delete_title)
            .setMessage(getString(R.string.rule_delete_msg, set.name, set.rules.size))
            .setNegativeButton(R.string.dlg_cancel, null)
            .setPositiveButton(R.string.rule_mgr_delete) { _, _ ->
                val all = AdRuleStore.load(this)
                all.removeAll { it.id == set.id }
                AdRuleStore.save(this, all)
                toast(getString(R.string.rule_saved_hint))
                refresh()
            }
            .show()
    }

    // ===================== 导入 =====================

    private fun askUrl() {
        val input = EditText(this).apply {
            hint = getString(R.string.rule_dlg_url_hint)
            setSingleLine(true)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rule_dlg_url_title)
            .setView(box)
            .setNegativeButton(R.string.dlg_cancel, null)
            .setPositiveButton(R.string.rule_dlg_download) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) importFromUrl(url)
            }
            .show()
    }

    private fun importFromUrl(url: String) {
        val wait = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.rule_dlg_downloading)
            .setCancelable(false)
            .create()
        wait.show()

        Thread {
            val (text, err) = fetch(url)
            runOnUiThread {
                runCatching { wait.dismiss() }
                if (text == null) {
                    toast(getString(R.string.rule_import_fail, err))
                } else {
                    doImport(nameOfUrl(url), url, text)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * 下载规则文本。返回 `(正文, 错误描述)`，二选一为 null。
     *
     * 用 `runCatching` 而不是 try/catch 给同一个 val 赋值 —— Kotlin 的确定赋值分析不覆盖
     * try/catch，那样写会报 `Val cannot be reassigned`（这个坑工程里踩过，见 verify_static.py 第 9 项）。
     */
    private fun fetch(url: String): Pair<String?, String> = runCatching<Pair<String?, String>> {
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
            String(bytes, 0, cut, Charsets.UTF_8) to ""
        } finally {
            runCatching { conn.disconnect() }
        }
    }.getOrElse { e -> null to (e.message ?: e.javaClass.simpleName) }

    private fun importFromFile(uri: Uri) {
        val text = runCatching {
            contentResolver.openInputStream(uri)?.use { ins ->
                val bytes = ins.readBytes()
                val cut = bytes.size.coerceAtMost(MAX_FILE_BYTES)
                String(bytes, 0, cut, Charsets.UTF_8)
            }
        }.getOrNull()
        if (text == null) {
            toast(getString(R.string.rule_import_fail, "无法读取该文件"))
            return
        }
        val name = displayName(uri)
        doImport(name, "file:$name", text)
    }

    /** 解析 + 落盘。规则集按**来源**去重：同一个 URL/文件重复导入是覆盖，不是堆积。 */
    private fun doImport(name: String, source: String, text: String) {
        val result = AdRuleParser.parse(text)
        if (result.rules.isEmpty() && result.elementRules.isEmpty()) {
            toast(getString(R.string.rule_import_empty, result.elementLines, result.invalidLines))
            return
        }
        val all = AdRuleStore.load(this)
        all.removeAll { it.source == source }
        all.add(
            0,
            AdRuleSet(
                id = UUID.randomUUID().toString().substring(0, 8),
                name = name,
                source = source,
                updatedAt = System.currentTimeMillis(),
                enabled = true,
                rules = result.rules,
                elementRules = result.elementRules
            )
        )
        if (!AdRuleStore.save(this, all)) {
            toast(getString(R.string.rule_import_fail, "规则库写入失败"))
            return
        }
        val skipped = if (result.invalidLines > 0 || result.truncatedLines > 0) {
            getString(R.string.rule_import_skipped, result.invalidLines, result.truncatedLines)
        } else {
            ""
        }
        toast(
            getString(
                R.string.rule_import_ok, name,
                result.rules.size, result.elementRules.size, skipped
            )
        )
        XLog.i(
            "导入规则集「$name」：请求拦截 ${result.rules.size} 条 / 元素隐藏 ${result.elementRules.size} 条" +
                "（原文元素行 ${result.elementLines} / 不合规 ${result.invalidLines} / 超限截断 ${result.truncatedLines}）"
        )
        refresh()
    }

    // ===================== 小工具 =====================

    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && c.columnCount > 0) c.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment ?: "本地规则文件"
    }

    /** 从 URL 末段取个名字，取不到就用 URL 前 40 个字符 */
    private fun nameOfUrl(url: String): String {
        val seg = url.substringAfterLast('/').substringBefore('?')
        return if (seg.isNotEmpty() && seg.length <= 48) seg else url.take(48)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private companion object {
        /** 单份规则文本的读取上限：超出部分截断（规则条数另受 AdRuleParser.MAX_RULES 约束） */
        const val MAX_FILE_BYTES = 512 * 1024

        /** 「查看」对话框最多列多少条 */
        const val MAX_SHOW_RULES = 300

        const val CONNECT_TIMEOUT_MS = 15000
        const val READ_TIMEOUT_MS = 20000
        const val UA = "Mozilla/5.0 (Linux; Android) HookBrowser"
    }
}
