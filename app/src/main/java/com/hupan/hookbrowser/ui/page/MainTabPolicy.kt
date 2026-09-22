/*
 * 主界面的 Tab 定义（与番茄 HookFanqie 的 ui/page/MainTabPolicy.kt 同构）。
 *
 * 三个 Tab 各管一件事：
 *   - 功能：去广告 / 界面精简 / 高级三个分区的功能开关；
 *   - 规则：拦截规则与用户脚本的开关 + 概况卡 + 规则管理 / 脚本管理入口；
 *   - 关于：项目信息与更新日志（长内容全部下沉到二级页）。
 *
 * 与「功能 / 日志 / 关于」那套（番茄）的差别：本模块的运行日志在 LSPosed 里看，
 * 没有自己的日志页；而自定义拦截规则是一整块业务，值得单独占一个 Tab。
 */
package com.hupan.hookbrowser.ui.page

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Tune

/** 主界面的三个 Tab；顺序 = 展示顺序 = 底栏顺序 = 分页顺序。 */
internal enum class MainTab(
    /** 底栏文字 */
    val label: String,
    /** 顶栏副标题（随 Tab 变化） */
    val subtitle: String,
    val icon: ImageVector,
) {
    Features("功能", "去广告 · 界面精简 · 高级", MiuixIcons.Tune),
    Rules("规则", "自定义拦截规则与用户脚本", MiuixIcons.ListView),
    About("关于", "项目信息与更新日志", MiuixIcons.Info),
}
