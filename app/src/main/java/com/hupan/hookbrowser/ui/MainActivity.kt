package com.hupan.hookbrowser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.ui.navigation.AppNavigation
import com.hupan.hookbrowser.ui.page.ToggleState
import com.hupan.hookbrowser.ui.theme.BrowserTheme

/**
 * 模块设置入口（原 SettingsActivity，1.11.0 起按「三 Tab + 二级页」重建为 Compose 界面）。
 *
 * <p>用 `ComponentActivity` 而不是 `AppCompatActivity`：1.12.0 起本模块界面**全部**是
 * Compose（规则管理页也从 View 搬过来了），不需要 AppCompat 的任何能力，
 * 而 AppCompat 1.6.1 与 activity-compose 1.13 的组合会平白多一层版本耦合。
 * 主题仍是 manifest 里的 `Theme.HookBrowser`（Material3.DayNight）。
 *
 * <p>LSPosed 会把这里声明的 launcher activity 作为模块卡片的「打开」按钮目标。
 *
 * <p><b>进程边界</b>：本 Activity 运行在模块自己的进程，与 hook 侧完全隔离。
 * 千万不要让 `MainHook` 或 `hook` / `features` / `adblock` 包里的任何类引用 `ui` 包，
 * 否则 Compose / Kotlin 运行时会跟着被加载进浏览器进程。
 *
 * <p><b>写端</b>：onCreate 里连接框架服务（内部幂等），之后开关改动才能
 * 经 remote preferences 同步到宿主。
 *
 * <p><b>层次</b>：整个 Activity 只套一层 [BrowserTheme]（主题）；
 * 页面栈由 [AppNavigation] 负责（navigation3 返回栈 + miuix 转场）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 必须早于任何开关读写：写完本地 SP 后要靠框架服务把它镜像进框架数据库，
        // 宿主进程（Config）读的是那份数据。晚一步就会出现「改了不生效」。
        ModuleService.bind(applicationContext)
        // 开关装载从 MainPage 的 `remember { }` 里提到这里：那块会在**组合期**读
        // SharedPreferences（主线程磁盘 IO）并写 snapshot state。放这里装载一次，
        // 页面只管读 ToggleState.values / strings。
        ToggleState.ensure(applicationContext)
        setContent {
            BrowserTheme {
                AppNavigation()
            }
        }
    }
}
