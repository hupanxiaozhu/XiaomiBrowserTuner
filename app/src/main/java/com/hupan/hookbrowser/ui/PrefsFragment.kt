package com.hupan.hookbrowser.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.R
import com.hupan.hookbrowser.features.SearchEngines
import com.hupan.hookbrowser.features.UaBuilder

class PrefsFragment : PreferenceFragmentCompat() {

    /**
     * 列表就绪后**换装饰**：摘掉 `PreferenceFragmentCompat` 自带的全宽分隔线（它和 MIUI X 的
     * 卡片打架），挂上 [CardRowDecoration] 给每一行画一张独立大卡片。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView?.apply {
            for (i in itemDecorationCount - 1 downTo 0) removeItemDecorationAt(i)
            addItemDecoration(CardRowDecoration(requireContext()))
            // 底部留白，最后一张卡片不贴屏幕边
            setPadding(paddingLeft, paddingTop, paddingRight, dp(BOTTOM_PAD_DP))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // 模块侧与宿主侧共用同一份 SP 文件名，宿主才能按路径直读到这份 XML
        preferenceManager.sharedPreferencesName = Config.PREFS_NAME
        setPreferencesFromResource(R.xml.prefs, rootKey)

        seedDefaults()
        bindListSummary(Config.UA_MODE, R.string.ua_mode_chrome)
        bindListSummary(Config.SEARCH_ENGINE_TARGET, R.string.se_bing)
        guardSecuritySwitch()
        guardUnlockPrefSwitch()
        bindRuleManager()
        bindDetails()
        watchPrefsWrite()
        // 首次进入就推一次：seedDefaults 写默认值的那次发生在监听注册之前，监听器接不到
        schedulePush()
    }

    /**
     * 1.10.1：监听本地 SP 的每一次写入，并把它**镜像到框架数据库**。
     *
     * 宿主进程读的是 `XposedInterface#getRemotePreferences`（框架数据库），不是这份本地 XML。
     * 少了这一步，界面上开关是开的、宿主侧却是空表 —— 「改了不生效」且不报错。
     *
     * `apply()` 是异步落盘的，所以延迟一点再推；连续改动会自然合并成一次。
     */
    private fun watchPrefsWrite() {
        val sp = preferenceManager.sharedPreferences ?: return
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            schedulePush()
            // 详情弹窗开着时同步一次状态胶囊：两个风险开关的确认框是异步落盘的，只有这里能接住
            val sync = externalSync
            if (sync != null && !suppressingExternalSync) handler.post { if (isAdded) sync() }
        }
        spListener = l
        sp.registerOnSharedPreferenceChangeListener(l)
    }

    /** 把整组开关推到框架远端；合并短时间内的连续改动 */
    private fun schedulePush() {
        handler.removeCallbacks(pushRunnable)
        handler.postDelayed(pushRunnable, PUSH_DEBOUNCE_MS)
    }

    private val pushRunnable = Runnable {
        val ctx = context ?: return@Runnable
        ModuleService.push(ctx, Config.PREFS_NAME)
    }

    override fun onDestroy() {
        spListener?.let { preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(it) }
        spListener = null
        // 页面退出前把最后一次改动推掉，别让它挂在延迟队列里被一起清掉
        context?.let { ModuleService.push(it, Config.PREFS_NAME) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * 宿主进程读不到 XML 里的 defaultValue，必须把默认值真实写进 SP，
     * 否则「没动过开关」和「关掉开关」在宿主侧无法区分。
     */
    private fun seedDefaults() {
        val sp = preferenceManager.sharedPreferences ?: return
        if (sp.getBoolean(KEY_SEEDED, false)) return
        val editor = sp.edit()
        Config.defaultEntries().forEach { (key, value) ->
            if (!sp.contains(key)) editor.putBoolean(key, value)
        }
        if (!sp.contains(Config.UA_MODE)) editor.putString(Config.UA_MODE, UaBuilder.MODE_CHROME)
        if (!sp.contains(Config.SEARCH_ENGINE_TARGET)) {
            editor.putString(Config.SEARCH_ENGINE_TARGET, SearchEngines.DEFAULT)
        }
        editor.putBoolean(KEY_SEEDED, true)
        editor.apply()
    }

    /** 下拉项（ListPreference）的 summary 显示当前选中值，两个下拉共用一套逻辑 */
    private fun bindListSummary(key: String, fallback: Int) {
        val pref = findPreference<ListPreference>(key) ?: return
        pref.summary = pref.entry ?: getString(fallback)
        pref.setOnPreferenceChangeListener { p, newValue ->
            val lp = p as ListPreference
            val idx = lp.findIndexOfValue(newValue as String)
            lp.summary = if (idx >= 0) lp.entries[idx] else newValue
            true
        }
    }

    /**
     * 「规则管理」入口 —— 点开进 [RuleManagerActivity]（导入 / 启停 / 删除规则集）。
     *
     * 它是个普通 `<Preference>` 而不是开关，所以走点击监听；规则本身不在这里编辑，
     * 避免和「开关页」的职责混在一起。
     */
    private fun bindRuleManager() {
        val pref = findPreference<Preference>(KEY_RULE_MANAGER) ?: return
        pref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), RuleManagerActivity::class.java))
            true
        }
    }

    // ===================== 1.9.4：点行看详情 =====================

    /**
     * 把 [FeatureDetails] 里的详情挂到每个开关行上。
     *
     * 行的点击由 [SwitchRowPreference] 交过来（它把 `onClick` 覆盖成空，不再切换开关），
     * 所以这里的监听器**只负责弹窗**；切换开关是点开关手柄那一下的事。
     *
     * 用 `findPreference` 而不是遍历 `PreferenceScreen`：它会顺带处理子分组，
     * 且键对不上时（改了 key 忘了改表）直接找不到，界面上一眼能发现。
     */
    private fun bindDetails() {
        FeatureDetails.ALL.forEach { (key, detail) ->
            val pref = findPreference<Preference>(key) as? SwitchRowPreference ?: return@forEach
            pref.setOnPreferenceClickListener {
                showDetail(pref, detail)
                true
            }
        }
    }

    /**
     * 详情弹窗：状态胶囊 + 作用 / Hook 目标 / 生效方式 / 注意事项，底部按钮可以直接切换。
     *
     * <p>1.9.6 起「切换」**不关弹窗**，原地刷新状态胶囊并翻转按钮文案 —— 与多看
     * （`Ui.detailDialog` 自己接管 positive button）和番茄（`WindowDialog` + `update`）
     * 的交互对齐：三边都是「点一下看效果，不满意接着点」。
     */
    private fun showDetail(pref: SwitchRowPreference, detail: FeatureDetails.Detail) {
        val ctx = context ?: return
        val view = layoutInflater.inflate(R.layout.dlg_feature_detail, null)
        val status = view.findViewById<TextView>(R.id.tv_detail_status)
        view.findViewById<TextView>(R.id.tv_detail_purpose).text = detail.purpose
        view.findViewById<TextView>(R.id.tv_detail_target).text = detail.target
        view.findViewById<TextView>(R.id.tv_detail_effect).text = detail.effect
        val box = view.findViewById<View>(R.id.box_detail_caveat)
        if (detail.caveat.isEmpty()) {
            box.visibility = View.GONE
        } else {
            view.findViewById<TextView>(R.id.tv_detail_caveat).text = detail.caveat
        }

        var positive: Button? = null
        /** 上一次真正渲染出去的状态：用它判断「这次到底变没变」，避免对没变的胶囊放过渡动画 */
        var lastOn = pref.isChecked

        /**
         * 状态胶囊 + 按钮文案一起刷新。
         *
         * <p>`animate` 只在**状态确实翻转**时才生效：
         * - 刚打开弹窗 → false（否则会先闪一下对态的颜色）；
         * - 外部把开关改掉后重新对齐 → false（已经是终态，再插值就是闪烁）；
         * - 点「切换」但被风险确认框拦下（开关没动）→ 由 `lastOn` 判成「没变」，同样不动画。
         */
        fun render(animate: Boolean) {
            val on = pref.isChecked
            setStatusPill(ctx, status, on, animate && on != lastOn)
            lastOn = on
            positive?.setText(
                if (on) R.string.detail_toggle_off else R.string.detail_toggle_on
            )
        }

        render(false)

        val dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle(pref.title)
            .setView(view)
            // 按钮文案要跟着开关状态翻，所以这里只给初值；点击由 onShow 里自己接管
            .setPositiveButton(if (pref.isChecked) R.string.detail_toggle_off else R.string.detail_toggle_on, null)
            .setNegativeButton(R.string.dlg_close, null)
            .create()

        dlg.setOnShowListener {
            positive = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            positive?.setOnClickListener {
                // 自己发起的这次变更不走「外部同步」通道：SP 监听是同步回调的，
                // 不挡掉的话它会在同一帧把刚起步的过渡动画直接拍回终态。
                // try/finally 保证标志一定会复位 —— 卡在 true 会让风险确认框那条路永远同步不上。
                suppressingExternalSync = true
                try {
                    applyToggle(pref)
                    render(true)
                } finally {
                    suppressingExternalSync = false
                }
            }
        }

        // 两个风险开关的「确认框」是异步的：用户在确认框里点确定之后 SP 才变，
        // 弹窗这边接住那次变化重新对齐，否则胶囊会一直停在被拦下时的状态。
        externalSync = { render(false) }
        dlg.setOnDismissListener { externalSync = null }
        dlg.show()
    }

    /**
     * 刷新状态胶囊：文字 + 底色 + 文字色一起在 220ms 内插值。
     *
     * <p>起始色固定取「另一态」的颜色 —— 胶囊只有开 / 关两态，从对态插过来就是正确的前一帧，
     * 所以不需要给 View 挂当前色（也就不会被别处的 setBackground 弄脏）。
     * 时长与多看 `Ui.DUR_CAPSULE_STATE`、番茄 `StatusPill` 的过渡一致（都是 220ms）。
     */
    private fun setStatusPill(context: Context, pill: TextView, on: Boolean, animate: Boolean) {
        pill.setText(if (on) R.string.detail_status_on else R.string.detail_status_off)
        val bgOn = ContextCompat.getColor(context, R.color.mx_accent_soft)
        val bgOff = ContextCompat.getColor(context, R.color.mx_track_off)
        val fgOn = ContextCompat.getColor(context, R.color.mx_accent)
        val fgOff = ContextCompat.getColor(context, R.color.mx_text_secondary)

        if (!animate) {
            // 静态路径直接用 XML 里的两份胶囊底（开 / 关），色值与下面的两两插值路径同源
            pill.setTextColor(if (on) fgOn else fgOff)
            pill.setBackgroundResource(if (on) R.drawable.bg_mx_pill else R.drawable.bg_mx_pill_off)
            return
        }
        val evaluator = ArgbEvaluator()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CAPSULE_STATE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                applyPill(
                    context, pill,
                    evaluator.evaluate(f, if (on) bgOff else bgOn, if (on) bgOn else bgOff) as Int,
                    evaluator.evaluate(f, if (on) fgOff else fgOn, if (on) fgOn else fgOff) as Int
                )
            }
            start()
        }
    }

    /** 一次性写成同一帧的中间态 */
    private fun applyPill(context: Context, pill: TextView, bg: Int, fg: Int) {
        pill.setTextColor(fg)
        pill.background = capsuleDrawable(context, bg)
    }

    /**
     * 胶囊底：圆角 = 高度 / 2。
     *
     * <p>XML 里的 `bg_mx_pill` 因为颜色是静态的不能改，动画路径必须在代码里现画一个；
     * 两者参数必须一致（高 22dp → 圆角 11dp），否则过渡到一半会看到圆角跳变。
     */
    private fun capsuleDrawable(context: Context, color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = CAPSULE_TAG_RADIUS_DP * context.resources.displayMetrics.density
        }

    /**
     * 弹窗里那个按钮的切换：先过一遍 `setOnPreferenceChangeListener`。
     *
     * 危险开关（安全检测 / 解锁隐藏项）的确认框就挂在那个监听器上：它返回 false 表示
     * 「已经拦下来并弹了确认框」，这里就什么都别做，等用户在确认框里点头。
     */
    private fun applyToggle(pref: SwitchRowPreference) {
        val target = !pref.isChecked
        if (!pref.callChangeListener(target)) return
        pref.isChecked = target
    }

    /** 「拦截网址安全检测」不接受一键打开，必须先过风险确认 */
    private fun guardSecuritySwitch() =
        guardRiskySwitch(Config.MISC_SECURITY, R.string.dlg_risk_title, R.string.dlg_risk_msg)

    /** 「解锁隐藏设置项」同理：它是全局改动，开启前必须过风险确认 */
    private fun guardUnlockPrefSwitch() =
        guardRiskySwitch(Config.MISC_UNLOCK_PREF, R.string.dlg_unlock_title, R.string.dlg_unlock_msg)

    /**
     * 有副作用的开关统一走这里：先拦下变更、弹确认框，用户确认后再自己落盘并回写 UI，
     * 否则返回 false 会让开关的勾选状态与 SP 不一致。
     *
     * 1.9.4 起页面上的开关是自绘的 [SwitchRowPreference]，但它切换时同样会走
     * `callChangeListener`，所以这段拦截逻辑对「点手柄」与「弹窗里的按钮」都有效。
     */
    private fun guardRiskySwitch(key: String, titleRes: Int, msgRes: Int) {
        val pref = findPreference<SwitchRowPreference>(key) ?: return
        pref.setOnPreferenceChangeListener { _, newValue ->
            if (newValue != true) return@setOnPreferenceChangeListener true
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setMessage(msgRes)
                .setNegativeButton(R.string.dlg_cancel, null)
                .setPositiveButton(R.string.dlg_ok) { _, _ ->
                    preferenceManager.sharedPreferences
                        ?.edit()?.putBoolean(key, true)?.apply()
                    pref.isChecked = true
                }
                .show()
            false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var spListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /**
     * 当前打开的详情弹窗的「重新对齐」回调（没有弹窗时为 null）。
     *
     * <p>只在弹窗存活期间非空，由 `showDetail` 挂上、`OnDismissListener` 清掉 —— 避免弹窗关了
     * 还持有已经被回收的 View。
     */
    private var externalSync: (() -> Unit)? = null

    /**
     * 自己发起的 SP 写入期间置位，用来挡掉 SP 监听里那次「外部同步」。
     *
     * <p>`SharedPreferencesImpl` 在主线程上调用 `apply()` 时是**同步**回调监听器的，
     * 所以这个标志只需要在 `applyToggle` 前后一开一关即可，不涉及时序竞态。
     */
    private var suppressingExternalSync = false

    private companion object {
        const val KEY_SEEDED = "_seeded"

        /** 列表底部留白（dp） */
        const val BOTTOM_PAD_DP = 24

        /** 普通入口项的 key（不是开关，不进 Config 的开关表） */
        const val KEY_RULE_MANAGER = "rule_manager"

        /** 推送防抖：连续点几个开关只推最后一次（远端写入是一次 Binder 往返） */
        const val PUSH_DEBOUNCE_MS = 300L

        /** 状态胶囊切换过渡时长：与多看 Ui.DUR_CAPSULE_STATE、番茄的 animateColorAsState 同值 */
        const val CAPSULE_STATE_MS = 220L

        /** 标签胶囊高 22dp → 圆角 11dp（stadium），必须与 drawable/bg_mx_pill.xml 一致 */
        const val CAPSULE_TAG_RADIUS_DP = 11f
    }
}
