/*
 * 路由表（与番茄 HookFanqie 的 ui/navigation/Route.kt 同构）。
 *
 * 每个页面 = 一个 @Serializable 的 Route；navigation3 靠可序列化这一点做返回栈的保存与恢复
 * （旋转屏幕、进程被回收后重建都能回到原页面）。
 *
 * 三 Tab 主界面（功能 / 规则 / 关于）不是路由 —— 它是 MainPage 内部横向分页，
 * 切 Tab 不进返回栈、返回键先回第一个 Tab（见 MainPage）。二级页面才用路由。
 */
package com.hupan.hookbrowser.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    /** 主界面：三 Tab（功能 / 规则 / 关于）+ 底部导航。 */
    @Serializable
    data object Main : Route

    /** 应用设置页：把全部开关恢复默认等模块自身偏好。入口在关于页。 */
    @Serializable
    data object Settings : Route

    /** 项目信息页：完整的信息表（版本 / 包名 / 作用域 / 运行环境 / 框架服务）。 */
    @Serializable
    data object ProjectInfo : Route

    /** 更新日志页：关于页只留当前版本一条，历史版本全部在这里（默认收起）。 */
    @Serializable
    data object Changelog : Route

    /** 诊断页：框架服务连接状态、开关如何同步、一键复制当前状态。 */
    @Serializable
    data object Diagnostics : Route

    /** 规则管理页：导入 / 启停 / 删除自定义拦截规则集。入口在「规则」Tab。 */
    @Serializable
    data object RuleManager : Route
}
