package com.hupan.hookbrowser.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.R

/**
 * 底部导航右栏「关于」：版本 / 简介 / 通道状态 / 更新日志。
 *
 * 版本号是**运行时读**的（`PackageManager`），不写死在 strings 里 —— 免得改了
 * `build.gradle` 忘了同步文案。
 *
 * <p>1.10.0 起这里不再展示「开关文件权限」：迁到 API 102 的 remote preferences 之后，
 * 跨进程读写走框架数据库，**不存在文件权限这个失败点**了（详见 `Config` 的版本对照表）。
 */
class AboutFragment : Fragment(R.layout.view_about) {

    private var tvVersion: TextView? = null
    private var tvScope: TextView? = null
    private var tvStatus: TextView? = null
    private var llChangelog: LinearLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvVersion = view.findViewById(R.id.tv_about_version)
        tvScope = view.findViewById(R.id.tv_about_scope)
        tvStatus = view.findViewById(R.id.tv_about_status)
        llChangelog = view.findViewById(R.id.ll_changelog)

        tvVersion?.text = versionText()
        tvScope?.text = getString(R.string.about_scope)
        bindChangelog()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroyView() {
        tvVersion = null
        tvScope = null
        tvStatus = null
        llChangelog = null
        super.onDestroyView()
    }

    /**
     * 状态行只说一件事 —— **模块进程有没有连上框架服务**。
     *
     * <p>≤1.9.9 这里展示的是「开关文件权限」（`0660` 之类），那是旧的文件通道时代的必需读数。
     * 1.10.0 起走框架数据库，文件权限这个失败点不存在了；换成了新的单点：
     * 框架服务没连上时，界面上改的开关**写不进**宿主读的那份数据，症状同样是「改了不生效」。
     * 所以这里直接把连接状态摊开，省得再去翻日志。
     */
    private fun refreshStatus() {
        tvStatus?.text = if (ModuleService.isBound) {
            getString(
                R.string.status_svc_bound,
                ModuleService.boundFramework ?: getString(R.string.status_svc_unknown)
            )
        } else {
            getString(R.string.status_svc_unbound)
        }
    }

    private fun versionText(): String = runCatching {
        val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        getString(R.string.about_version, info.versionName ?: "?", code)
    }.getOrElse { getString(R.string.about_version, "?", 0L) }

    /** 更新日志：数据在 [Changelogs]，与工程根目录的 CHANGELOG.md 同源，只留最近几版 */
    private fun bindChangelog() {
        val box = llChangelog ?: return
        box.removeAllViews()
        Changelogs.ALL.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_changelog, box, false)
            row.findViewById<TextView>(R.id.tv_cl_title).text =
                getString(R.string.about_cl_title, entry.version, entry.code)
            row.findViewById<TextView>(R.id.tv_cl_items).text =
                entry.items.joinToString("\n") { "· $it" }
            box.addView(row)
        }
        box.addView(
            TextView(requireContext()).apply {
                text = getString(R.string.about_cl_more)
                setTextColor(ContextCompat.getColor(context, R.color.mx_text_hint))
                textSize = 11f
                setPadding(0, dp(10), 0, 0)
            }
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
