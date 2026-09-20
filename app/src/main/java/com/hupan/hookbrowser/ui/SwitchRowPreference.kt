package com.hupan.hookbrowser.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.TwoStatePreference
import com.google.android.material.materialswitch.MaterialSwitch
import com.hupan.hookbrowser.R

/**
 * 功能列表里的一行开关：**点开关 = 切换，点行里开关以外的区域 = 看详情**。
 *
 * 为什么不用库自带的 `SwitchPreferenceCompat`（两条都是硬伤，查过库源码）：
 *
 * 1. 它给行布局注入的开关（`PreferenceGroupAdapter#onCreateViewHolder` 把 widget 布局
 *    inflate 进 `android:id/widget_frame`）在 `preference_widget_switch_compat.xml` 里写死了
 *    `android:clickable="false"` —— 点开关等于点整行，两条路径分不开；
 * 2. 它继承的 `TwoStatePreference#onClick` 会把「整行点击」直接当切换用，行点击腾不出来。
 *
 * 所以这里直接坐 `TwoStatePreference`（保留 `isChecked` / `persistBoolean` /
 * `notifyDependencyChange`，`app:dependency` 的联动照旧），开关自己在 `mx_pref_switch.xml` 里画。
 */
class SwitchRowPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TwoStatePreference(context, attrs, defStyleAttr) {

    /**
     * 覆盖成**空**：库的实现（`TwoStatePreference#onClick`）在这里翻转开关，
     * 而「点整行」在我们这儿是「看详情」。
     *
     * 空实现不影响点击监听：`Preference#performClick()` 的顺序是
     * 先 `onClick()`，再 `mOnClickListener.onPreferenceClick(this)`（源码顺序如此），
     * 所以行点击照样会落到 `setOnPreferenceClickListener` 上。
     */
    override fun onClick() {
        // 故意不调 super —— 调了就会连带切换开关
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // 从 itemView 上找（View.findViewById 是泛型的）；PreferenceViewHolder 自己也声明了
        // 一个返回 View 的同名方法，别在 holder 上带类型参数调用
        val sw = holder.itemView.findViewById<MaterialSwitch>(R.id.hb_switch) ?: return
        // ViewHolder 是复用的：先把监听摘掉再回填状态，否则回填会被当成用户操作写盘
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = isChecked
        sw.setOnCheckedChangeListener { _, value -> onSwitchToggled(value, sw) }
    }

    /**
     * 点开关 = 切换。先过 `setOnPreferenceChangeListener` ——
     * 「拦截网址安全检测」「解锁隐藏设置项」这两个开关的风险确认框就挂在那儿；
     * 被否掉时把手柄弹回原状，免得界面状态和落盘值不一致。
     */
    private fun onSwitchToggled(value: Boolean, sw: MaterialSwitch) {
        if (callChangeListener(value)) {
            isChecked = value
        } else {
            sw.isChecked = isChecked
        }
    }
}
