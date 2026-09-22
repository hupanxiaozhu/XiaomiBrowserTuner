/*
 * 「检查更新」的界面状态（Compose 可观察，模块进程独用）。
 *
 * 拆这层的理由：取数在 update/UpdateChecker（无 Compose 依赖），状态在这边；
 * 关于页的入口行读这里的说明文字，主界面（MainPage）读这里的弹窗数据，
 * MainActivity 只管在 onCreate 里点一下自动检查 —— 三处都不用关心线程。
 *
 * 自动检查与手动检查的结果走同一个弹窗，但策略不同：
 * - 自动：只在「发现新版本」时弹窗；已是最新 / 失败一律静默；
 * - 手动：无论结果如何都给反馈（已是最新、失败原因也要说出来，否则像没反应）。
 */
package com.hupan.hookbrowser.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hupan.hookbrowser.update.ReleaseInfo
import com.hupan.hookbrowser.update.UpdateChecker

/** 更新浮层要展示的一份数据：release 为空时是「结果说明」型弹窗（最新 / 失败） */
internal class UpdateDialogData(
    val title: String,
    /** 结果说明型弹窗的正文（已是最新 / 失败原因）；release 非空时忽略 */
    val message: String?,
    /** 非空 = 发现新版本：弹更新日志与下载入口 */
    val release: ReleaseInfo?,
    /** 检查发生时的当前版本（「当前 vX → 新版 vY」用） */
    val currentVersion: String,
)

internal object UpdateState {

    /** 自动检查开关（本地 SP，见 UpdateChecker 顶注；默认开） */
    var autoCheckEnabled by mutableStateOf(true)
        private set

    /** 手动检查进行中（关于页入口行显示「正在检查…」并防重入） */
    var checking by mutableStateOf(false)
        private set

    /** 非空时主界面弹更新浮层 */
    var dialog by mutableStateOf<UpdateDialogData?>(null)
        private set

    /** 最近一次检查的结论（关于页入口行的说明文字） */
    var lastSummary by mutableStateOf<String?>(null)
        private set

    /** 本进程只自动检查一次：Activity 旋转重建不再重复发请求 */
    private var autoChecked = false

    /** MainActivity.onCreate 里调：装载本地设置。幂等。 */
    fun init(context: Context) {
        autoCheckEnabled = UpdateChecker.isAutoCheckEnabled(context)
    }

    fun setAutoCheckEnabled(context: Context, value: Boolean) {
        autoCheckEnabled = value
        UpdateChecker.setAutoCheckEnabled(context, value)
    }

    /** 打开应用时的自动检查（24 小时节流在 UpdateChecker 里；失败静默） */
    fun autoCheck(context: Context) {
        if (autoChecked) return
        autoChecked = true
        val current = UpdateChecker.currentVersion(context)
        UpdateChecker.maybeAutoCheck(context) { result -> consume(current, result) }
    }

    /** 关于页「检查更新」：手动触发，任何结果都弹窗反馈 */
    fun checkManually(context: Context) {
        if (checking) return
        checking = true
        val current = UpdateChecker.currentVersion(context)
        UpdateChecker.checkNow(context) { result ->
            checking = false
            dialog = when {
                result.isFailure -> {
                    lastSummary = "上次检查失败，可重试"
                    UpdateDialogData(
                        title = "检查更新",
                        message = "检查失败：${result.exceptionOrNull()?.message ?: "未知错误"}。",
                        release = null,
                        currentVersion = current,
                    )
                }
                else -> {
                    val release = result.getOrNull()
                    if (release == null) {
                        lastSummary = "已是最新（v$current）"
                        UpdateDialogData(
                            title = "检查更新",
                            message = "已是最新版本（v$current）。",
                            release = null,
                            currentVersion = current,
                        )
                    } else {
                        lastSummary = "发现新版本 v${release.version}"
                        UpdateDialogData(
                            title = "发现新版本 v${release.version}",
                            message = null,
                            release = release,
                            currentVersion = current,
                        )
                    }
                }
            }
        }
    }

    fun dismiss() {
        dialog = null
    }

    /** 关于页「检查更新」入口行的说明文字（读它的地方会随状态自动重组） */
    fun summary(): String = when {
        checking -> "正在检查…"
        dialog?.release != null -> lastSummary ?: "发现新版本"
        lastSummary != null -> lastSummary ?: ""
        else -> "查 GitHub Releases 有没有新版本"
    }

    /** 自动检查的结果消费：只有「发现新版本」才弹窗，其余静默 */
    private fun consume(current: String, result: Result<ReleaseInfo?>) {
        val release = result.getOrNull() ?: return
        lastSummary = "发现新版本 v${release.version}"
        dialog = UpdateDialogData(
            title = "发现新版本 v${release.version}",
            message = null,
            release = release,
            currentVersion = current,
        )
    }
}
