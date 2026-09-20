package com.hupan.hookbrowser.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.R

/**
 * 底部导航右栏「关于」——hero 主卡 / 简介 / 项目信息 / 更新日志。
 *
 * 版式与番茄 HookFanqie 7.2.30 的 `AboutTab` 对齐：hero（主色 12% 底 + 模块名大字 +
 * 实心版本胶囊 + 一句话定位）→ 简介卡 → 「项目信息」一组独立小卡 → 「更新日志」一版一卡。
 *
 * 版本号是**运行时读**的（`PackageManager`），不写死在 strings 里 —— 免得改了
 * `build.gradle` 忘了同步文案。
 *
 * <p>1.10.0 起这里不再展示「开关文件权限」：迁到 API 102 的 remote preferences 之后，
 * 跨进程读写走框架数据库，**不存在文件权限这个失败点**了（详见 `Config` 的版本对照表）。
 */
class AboutFragment : Fragment(R.layout.view_about) {

    private var tvVersion: TextView? = null
    private var tvStatus: TextView? = null
    private var llChangelog: LinearLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvVersion = view.findViewById(R.id.tv_about_version)
        tvStatus = view.findViewById(R.id.tv_about_status)
        llChangelog = view.findViewById(R.id.ll_changelog)

        // hero 的实心版本胶囊：只放版本号，versionCode 留在每条更新日志里
        tvVersion?.text = getString(R.string.about_version, versionName())
        bindChangelog()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroyView() {
        tvVersion = null
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

    /** 当前安装的模块版本号（读不到时给占位，不让界面出现 null） */
    private fun versionName(): String = runCatching {
        requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName
    }.getOrNull() ?: "?"

    /**
     * 更新日志：数据在 [Changelogs]，与工程根目录的 CHANGELOG.md 同源，只留最近几版。
     *
     * <p>**一版一张独立卡片**（`item_changelog.xml`），标题行右侧挂一枚状态 tag 胶囊：
     * 第一条是「当前」（浅蓝底主色字），其余是「历史」（浅灰底次要字）——
     * 与详情弹窗的状态胶囊同一组令牌，改配色时两处一起改。
     */
    private fun bindChangelog() {
        val box = llChangelog ?: return
        val ctx = context ?: return
        box.removeAllViews()
        Changelogs.ALL.forEachIndexed { index, entry ->
            val row = layoutInflater.inflate(R.layout.item_changelog, box, false)
            row.findViewById<TextView>(R.id.tv_cl_title).text =
                getString(R.string.about_cl_title, entry.version)
            row.findViewById<TextView>(R.id.tv_cl_code).text =
                getString(R.string.about_cl_code, entry.code)

            val isCurrent = index == 0
            val tag = row.findViewById<TextView>(R.id.tv_cl_tag)
            tag.setText(
                if (isCurrent) R.string.about_cl_tag_current else R.string.about_cl_tag_history
            )
            tag.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (isCurrent) R.color.mx_accent else R.color.mx_text_secondary
                )
            )
            tag.setBackgroundResource(
                if (isCurrent) R.drawable.bg_mx_pill else R.drawable.bg_mx_pill_off
            )

            row.findViewById<TextView>(R.id.tv_cl_items).text =
                entry.items.joinToString("\n") { "· $it" }
            box.addView(row)
        }
    }
}
