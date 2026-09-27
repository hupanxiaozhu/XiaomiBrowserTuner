package com.hupan.hookbrowser.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hupan.hookbrowser.ModuleService
import com.hupan.hookbrowser.adblock.AdRuleStore
import com.hupan.hookbrowser.adblock.BuiltinRuleSet
import com.hupan.hookbrowser.adblock.RuleUpdater
import com.hupan.hookbrowser.ui.UpdateState
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
        // 1.15.10：1.15.9 及更早写下的规则库是未压缩格式，直接推送会撞 Binder 事务上限
        // （见 AdRuleStore.repack）。只在旧格式时动手，且读 + 压缩 + 写都是阻塞 I/O，
        // 所以丢到后台线程 —— 升级后只要打开一次设置界面，规则库就自动修好。
        //
        // 1.15.13：同一条线程上接着做两件事 ——
        //   ① 按「内置推荐规则」开关把内置规则集装上 / 移除（版本变了会自动替换）；
        //   ② 触发一次规则自动更新（24 小时节流、失败静默都在 RuleUpdater 里）。
        //   模块进程不常驻，**打开本应用是唯一可靠的触发点**，所以这两步只能挂在这里；
        //   界面上也照实写了出来，不让用户以为它是后台常驻任务。
        //   顺序不能反：repack 先把旧格式修好，后面两步才写到正确的格式上。
        Thread {
            runCatching { AdRuleStore.repack(applicationContext) }
            runCatching { BuiltinRuleSet.sync(applicationContext) }
            runCatching { RuleUpdater.maybeAutoUpdate(applicationContext) }
        }.apply {
            isDaemon = true
            name = "hb-rule-boot"
        }.start()
        // 开关装载从 MainPage 的 `remember { }` 里提到这里：那块会在**组合期**读
        // SharedPreferences（主线程磁盘 IO）并写 snapshot state。放这里装载一次，
        // 页面只管读 ToggleState.values / strings。
        ToggleState.ensure(applicationContext)
        // 检查更新：装载本地设置 + 自动检查（24 小时节流在 UpdateChecker 里，失败静默）。
        // 只跑在模块进程，后台线程发请求，不碰宿主；本进程只查一次（旋转重建不重复）。
        UpdateState.init(applicationContext)
        UpdateState.autoCheck(applicationContext)
        setContent {
            BrowserTheme {
                AppNavigation()
            }
        }
    }
}
