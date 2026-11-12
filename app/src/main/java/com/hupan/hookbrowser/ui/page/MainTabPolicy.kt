/*
 * 主界面的 Tab 定义（与番茄 HookFanqie 的 ui/page/MainTabPolicy.kt 同构）。
 *
 * 两个 Tab：
 *   - 功能：去广告 / 界面精简 / 高级三个分区的功能开关；
 *   - 关于：项目信息与更新日志（长内容全部下沉到二级页）。
 *
 * 2.0.0 撤掉「规则」Tab：拦截规则与用户脚本交给宿主自己装的扩展（原版 20.32 起支持），
 * 模块不再自带规则引擎，也就不需要这一整块入口。
 */
package com.hupan.hookbrowser.ui.page

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Tune

/** 主界面的两个 Tab；顺序 = 展示顺序 = 底栏顺序 = 分页顺序。 */
internal enum class MainTab(
    /** 底栏文字 */
    val label: String,
    /** 顶栏副标题（随 Tab 变化） */
    val subtitle: String,
    val icon: ImageVector,
) {
    Features("功能", "去广告 · 界面精简 · 高级", MiuixIcons.Tune),
    About("关于", "项目信息与更新日志", MiuixIcons.Info),
}
