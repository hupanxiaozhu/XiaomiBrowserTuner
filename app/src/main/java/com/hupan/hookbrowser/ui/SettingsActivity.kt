package com.hupan.hookbrowser.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.R

/**
 * 设置页容器（1.9.4）：**底部导航两个 tab**，左右切换。
 *
 * - 左 tab = 功能操作区（[PrefsFragment]）：点开关手柄切换开 / 关，点行里其它地方看详情；
 * - 右 tab = 关于（[AboutFragment]）：版本 / 简介 / 权限状态 / 更新日志。
 *
 * 两个 Fragment 用 `show/hide` 而不是 `replace`：切走再切回来时滚动位置、装饰器、
 * SP 监听都在，不会白重建一遍。
 *
 * 原版 base.apk 的界面只是一个居中的 `TextView text="FUCK_MIUIBROWSER"`，功能全写死在代码里；
 * 这里换成一组真实开关 + 一块能自查状态的关于页。**本页跑在模块自己的进程**，
 * 界面上的自检与日志都不影响宿主。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvSub: TextView

    /** 当前 tab（`onSaveInstanceState` 用；旋转屏幕后要回到原来那一栏） */
    private var currentTab = R.id.nav_features

    private var cachedFeatures: PrefsFragment? = null
    private var cachedAbout: AboutFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 必须早于任何开关读写：写完本地 SP 后要靠框架服务把它镜像进框架数据库，
        // 宿主进程（`Config`）读的是那份数据。晚一步就会出现「改了不生效」。
        ModuleService.bind(this)
        setContentView(R.layout.activity_settings)
        tvTitle = findViewById(R.id.tv_page_title)
        tvSub = findViewById(R.id.tv_page_sub)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        // 重复点同一个 tab 什么都不做（否则会白跑一次事务）
        nav.setOnItemReselectedListener { }

        val tab = savedInstanceState?.getInt(KEY_TAB) ?: R.id.nav_features
        nav.selectedItemId = tab
        // selectedItemId 与当前选中项相同时不会回调，所以这里再手动切一次，保证 Fragment 真的挂上
        showTab(tab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    private fun showTab(itemId: Int) {
        currentTab = itemId
        val toAbout = itemId == R.id.nav_about
        val target = if (toAbout) obtainAbout() else obtainFeatures()
        val other = if (toAbout) obtainFeatures() else obtainAbout()

        val tx = supportFragmentManager.beginTransaction()
        if (other.isAdded) tx.hide(other)
        if (target.isAdded) tx.show(target) else tx.add(R.id.tab_container, target, tagOf(itemId))
        tx.commit()

        if (toAbout) {
            tvTitle.setText(R.string.about_title)
            tvSub.setText(R.string.about_sub)
        } else {
            tvTitle.setText(R.string.pane_features)
            tvSub.setText(R.string.pane_features_sub)
        }
    }

    /**
     * 取 Fragment：**先按 tag 找已经加进来的那个，再新建**。
     * 旋转屏幕后 FragmentManager 会把两个 Fragment 自动恢复，直接用 `lateinit` 新建会得到第二份实例。
     */
    private fun obtainFeatures(): PrefsFragment {
        cachedFeatures?.let { return it }
        val f = supportFragmentManager.findFragmentByTag(TAG_FEATURES) as? PrefsFragment ?: PrefsFragment()
        cachedFeatures = f
        return f
    }

    private fun obtainAbout(): AboutFragment {
        cachedAbout?.let { return it }
        val f = supportFragmentManager.findFragmentByTag(TAG_ABOUT) as? AboutFragment ?: AboutFragment()
        cachedAbout = f
        return f
    }

    private fun tagOf(itemId: Int): String =
        if (itemId == R.id.nav_about) TAG_ABOUT else TAG_FEATURES

    private companion object {
        const val TAG_FEATURES = "tab_features"
        const val TAG_ABOUT = "tab_about"
        const val KEY_TAB = "tab"
    }
}
