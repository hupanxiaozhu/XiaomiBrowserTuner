<div align="center">

# XiaomiBrowserTuner

**小米浏览器（Xiaomi Browser）净化模块 · Xposed / LSPosed**

[![Release](https://img.shields.io/github/v/release/hupanxiaozhu/XiaomiBrowserTuner?style=flat-square&label=release&color=3482FF)](https://github.com/hupanxiaozhu/XiaomiBrowserTuner/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#安装)
[![LSPosed](https://img.shields.io/badge/LSPosed-2.3%2B-FF6B6B?style=flat-square)](#安装)
[![libxposed API](https://img.shields.io/badge/libxposed_API-102-4C8DFF?style=flat-square)](#兼容性)
[![Host](https://img.shields.io/badge/host-20.27.1010901-FF8C00?style=flat-square)](#兼容性)
[![License](https://img.shields.io/badge/license-proprietary-red?style=flat-square)](#许可与免责)

</div>

> ## 🧑💻 原作者：Jun_ao
>
> **原模块**：`干掉小米浏览器广告`（原作者发布的 `base.apk`，v1.2.0 / versionCode 2）
>
> 本工程由原作者的 `base.apk` **反编译还原后重写** —— **功能思路与实现全部来自原作者**，
> 我只是把原先写死在代码里的去广告逻辑，改成了「**每组功能一个开关 + 一个设置界面**」。
>
> ### 📢 原作者 QQ 群：**1094704567**
>
> 想找原版模块、反馈原模块的问题、或支持一下原作者，**请加这个群**。
> 本重写版自己引入的 bug 请提到本仓库 Issues，**别去打扰原作者**。
>
> 详细来源说明与致谢 → [**原作者与致谢**](#原作者与致谢)

> ⚠️ **目标宿主是小米浏览器**（`com.android.browser`，MIUI / HyperOS 内置）。
> 不是 Chrome、不是 AOSP Browser，也不是任何第三方套壳浏览器 —— 装到别的浏览器上不会有任何效果。

- 包名：`com.hupan.hookbrowser`
- 作用域：`com.android.browser`
- 当前版本：**1.10.1**（versionCode 31）

---

## 特性

每一个功能都是**独立开关**，改完**即时生效**，不需要重启浏览器。

| 分组 | 功能 | 做什么 | 默认 |
|---|---|---|---|
| 去广告 | **开屏广告** | 拦 MSA 联盟开屏广告 | ✅ 开 |
| 去广告 | **首页推广位** | 屏蔽首页推广位与切换引导 | ✅ 开 |
| 去广告 | **搜索推荐广告** | 藏掉搜索下拉里的推广卡片 | ✅ 开 |
| 去广告 | **错误页热搜榜** | 摘掉「无法访问」页底部的热搜榜单 | ✅ 开 |
| 去广告 | **宿主广告开关** | 强制关闭宿主自己的广告判断 | ✅ 开 |
| 自定义规则 | **自定义拦截规则** | 导入 Adblock 语法规则：URL 拦截 + 元素隐藏 | ✅ 开 |
| 自定义规则 | **接管宿主拦截引擎** | ⚠️ 用你的规则替换宿主 native 规则库 | ⬜ 关 |
| 界面精简 | **下载弹窗** | 不建下载弹窗、不推应用、不跳市场 | ✅ 开 |
| 界面精简 | **UA 伪装** | 抹掉 UA 里的小米浏览器标识 | ✅ 开 |
| 界面精简 | **默认搜索引擎** | 把必应 / Google / Yandex 内置进切换栏 | ✅ 开 |
| 界面精简 | **解锁隐藏设置项** | ⚠️ 强制放出宿主设置页的隐藏项 | ⬜ 关 |
| 界面精简 | **浏览器调试模式** | 放开宿主调试模式 + 输出模块详细日志 | ⬜ 关 |
| 界面精简 | **拦截网址安全检测** | ⚠️ 不再提示钓鱼 / 恶意站点 | ⬜ 关 |

- **总闸**：关掉「启用模块」后所有 hook 立即变 no-op，不用卸载。
- ⚠️ 标记的三项会改变宿主的默认行为，开启时会**弹风险确认**。
- 除「接管宿主拦截引擎」外，所有功能都不修改宿主自身的数据。

---

## 安装

### 前置要求

| 项 | 要求 |
|---|---|
| 宿主 | **小米浏览器 20.27.1010901**（`com.android.browser`，versionCode 202710100） |
| 框架 | **LSPosed 2.3 及以上**（模块基于 libxposed API 102） |
| 系统 | Android 8.0+（minSdk 26） |

### 步骤

1. 从 [Releases](https://github.com/hupanxiaozhu/XiaomiBrowserTuner/releases) 下载 `XiaomiBrowserTuner-vX.Y.Z.apk` 并安装。
2. 打开 **LSPosed** → 模块 → 勾选 **XiaomiBrowserTuner** → 启用。
3. 进入模块详情，**作用域勾选「小米浏览器」**。
4. **强制停止**小米浏览器，然后重新打开。
5. 打开 **XiaomiBrowserTuner** 设置页，看顶部状态行：

   | 状态行 | 含义 |
   |---|---|
   | 「框架服务已连接」 | ✅ 一切正常，开关改动会即时同步到宿主 |
   | 「模块未激活」 | ❌ 回到第 2 步，确认 LSPosed 里已启用本模块 |
   | 「框架服务未连接」 | ❌ 框架不支持或模块未生效，见 [常见问题](#常见问题) |

> **第 4 步不能省**：LSPosed 的作用域变更只在目标进程**重启之后**才生效。

---

## 使用方法

### 设置页

底部导航两栏：

- **功能开关** —— 全部开关，按「去广告 / 界面精简 / 高级」分组
- **关于** —— 版本、作用域、更新日志

### 两套手势（每个功能都自带说明）

| 操作 | 效果 |
|---|---|
| 点**开关**本身 | 立刻切换，**改完即时生效** |
| 点**行的其他区域** | 弹出**功能详情**：作用 / Hook 目标 / 生效方式 / 注意事项 |

所以每个功能该怎么用、有什么副作用，在设置页里就能看到，不用回来翻这篇 README。

### 导入自定义拦截规则

1. 设置页 → **自定义拦截规则** → **规则管理**
2. 点 **从 URL 导入**（填规则文件地址）或 **从本地文件导入**
3. 导入后**数秒内自动生效** —— 不需要重启浏览器，也不需要再开别的开关

规则语法为 Adblock 风格（`||domain^` 拦请求、`##selector` 隐藏元素），
支持多套规则集的启停 / 查看 / 删除。需要连宿主自己的拦截引擎一起换掉时，
再打开 **接管宿主拦截引擎**（默认关闭，说明见下方 FAQ）。

### 出问题时怎么自查

1. 打开 **浏览器调试模式**（会同时放出宿主自身的调试开关）
2. 复现问题，然后到 **LSPosed → 日志** 搜 **TAG `HookBrowser`**
3. 每个 hook 都会打「已挂载 / 未找到」，一眼就能看出是哪一条断的

---

## 常见问题

<details>
<summary><b>装了之后一点变化都没有？</b></summary>

按顺序检查三件事：

1. **LSPosed 里启用了吗** —— 模块列表里 XiaomiBrowserTuner 的开关要是打开状态。
2. **作用域勾选了吗** —— 必须是「小米浏览器」（`com.android.browser`）。
3. **强制停止过宿主吗** —— 作用域变更只在重启目标进程后生效。

设置页顶部的状态行是最快的判据：「框架服务已连接」说明链路是通的。
功能没生效还可以在 LSPosed 日志里搜 TAG `HookBrowser`，看对应功能的 hook 是「已挂载」还是「未找到」。

</details>

<details>
<summary><b>开关改了但没生效？</b></summary>

**1.10.1 之前必现**：那时只做了开关的**读取端**，模块 App 写的是本地文件，宿主读的是框架数据库，两端不是同一份数据。
1.10.1 补上写入端后即时同步。

如果用的是 1.10.1 及以上仍然不生效，看设置页状态行：

- 「框架服务未连接」→ 框架不支持跨进程写入，检查 LSPosed 版本（需 2.3+）。
- 日志里出现 `[E] getRemotePreferences(settings) 失败` → 框架抛异常，全部开关退化为默认值。
  此时**默认关闭**的三项（解锁隐藏设置项 / 浏览器调试模式 / 拦截网址安全检测）永远开不起来，
  而默认开启的广告类看起来「正常」，容易误判成「只有某几个开关坏了」。

</details>

<details>
<summary><b>能用在其他浏览器上吗？</b></summary>

不能。本模块的所有 hook 点都是针对小米浏览器（`com.android.browser`，MIUI / HyperOS 内置）的类名与方法名，
装到 Chrome、Edge、夸克等浏览器上不会有任何效果 —— 也不会崩溃，只是纯 no-op。

</details>

<details>
<summary><b>宿主升级后失效了怎么办？</b></summary>

hook 目标是对着宿主 **20.27.1010901** 逐个核对过的。小米浏览器升级后类名可能变动，
届时对应的 hook 会挂不上 —— **模块只记日志、不会崩**，表现为该功能静默失效。

排查：打开「浏览器调试模式」，在 LSPosed 日志里搜 TAG `HookBrowser`，找到打「未找到」的那条，
再对照 `docs/宿主20.27-hook目标核对.md` 的核对方法重新定位。完整核对结果见 [兼容性](#兼容性) 一节。

</details>

<details>
<summary><b>「接管宿主拦截引擎」是什么？为什么默认关闭？</b></summary>

小米浏览器自带一套 **native 拦截引擎**（规则库在 `files/data/adblock/miui_blacklist.json`）。
默认情况下模块只拦自己能拦到的位置；打开这个开关后，模块会把宿主的规则库**换成你导入的规则**、
清空它的白名单，再让 native 引擎立即重载 —— 效果是**全浏览器范围**都按你的规则走。

它是唯一会修改宿主自身数据的开关（其余功能都不碰宿主数据），所以默认关闭，并且：

- 修改前会**自动备份**原规则库；
- 关闭开关即从备份**还原**；
- 想手动恢复也可以重新导入规则，或清除应用数据。

</details>

<details>
<summary><b>会不会影响登录状态 / 被服务端检测？</b></summary>

不会。模块只修改**本机客户端**的行为（界面元素、返回值、注入的过滤脚本），
**不涉及任何服务端内容**，也不破解付费内容。

唯一的对外网络请求是「从 URL 导入规则」时下载你指定的规则文件，此外模块自身不发起任何网络通信。

</details>

<details>
<summary><b>这个模块和原作者 Jun_ao 是什么关系？</b></summary>

本工程是**基于原作者成果的重写版**，不是官方后续版本。

- 原模块：`干掉小米浏览器广告`，作者 **Jun_ao**，产物 `base.apk`（v1.2.0）。
- 原作者 QQ 群：**`1094704567`** —— 找原版模块、反馈原模块问题请加群。
- 本工程只是把原模块反编译还原成可维护的源码，并把去广告逻辑改成「一功能一开关」，
  **功能思路与实现全部来自原作者**（详见 [原作者与致谢](#原作者与致谢)）。
- 本重写版引入的 bug 请提到[本仓库 Issues](https://github.com/hupanxiaozhu/XiaomiBrowserTuner/issues)，
  **不要打扰原作者**；本工程也未获原作者授权或背书。

</details>

---

## 更新记录

<details>
<summary><b>展开查看版本历史</b>（完整记录见 <a href="CHANGELOG.md">CHANGELOG.md</a>）</summary>

| 版本 | 主题 |
|---|---|
| **1.10.1** | 补上写入端：开关真的能同步到宿主了 |
| 1.10.0 | 迁移到 libxposed API 102 |
| 1.9.8 | 移除 LSPosed 废弃项：XSharedPreferences |
| 1.9.7 | 错误页去广告：摘掉「热搜榜」 |
| 1.9.6 | 统一三工程胶囊特效 + 详情弹窗原地留驻 |
| 1.9.4 | 底部导航两栏 +「点开关即切换 / 点行看详情」 |
| 1.9.3 | 界面重构成 MIUI X + 日志分档 |
| 1.9.2 | 用自定义规则接管宿主自己的拦截引擎 |
| 1.9.1 | 上限集体放宽 + 直接读宿主自带的小米规则库 |
| 1.9.0 | 元素隐藏规则（`##`）生效 |
| 1.8.0 / 1.8.1 | 自定义拦截规则：导入 Adblock 语法规则 + 修闪退与误拦 |
| 1.7.0 | 切换栏真正出现「必应 / Google / Yandex / 百度」 |
| 1.6.1 ~ 1.6.9 | 搜索广告定位到 H5 并改为注入过滤脚本 |
| 1.3.0 ~ 1.5.x | 早期迭代（界面与基础 hook） |

</details>

---

## 兼容性
本工程的 hook 目标是对着宿主 **20.27.1010901** 核对的，核对结果：

| 目标 | 结果 |
|---|---|
| 首页推广 2 个类的 5 个方法 | ✅ 全在 |
| MSA 开屏广告 3 个方法 | ✅ 全在 |
| UA 3 个方法 | ✅ 全在 |
| `BrowserSettings` 调试/广告系列 | ✅ 全在（该类 362 个方法） |
| `Tab$GetSecurityFlagAsyncTask`、`DownloadHandler$1#call` | ✅ 在 |
| `CommonDownloadDialogImpl#{onCreateDialog, requestGameRecommend}` | ✅ 在 |
| `SugCardData#a` | ✅ 在（原版 base.apk 的搜索 hook，⚠ 1.6.0 复核判定**与广告无关**） |
| `RecentAppManager#{initRecentAppList, getRecentAppList}` | ✅ 在（但三个列表长度恒 0，与卡片无关） |
| `SearchSugManager#mWebView` / `#initSugWebView` / `#querySug` | ✅ 在（**1.6.1 主拦点**：反射取 `mWebView` + 注入 JS） |
| `SearchSugManager#evaluateSugJS(String)` | ✅ 在（内部就是 `mWebView.evaluateJavascript(js, null)`，佐证注入通道有效） |
| `SugCardData#requestGameRecommend` | ❌ 不存在 → 已从代码中删除 |
| `CommonDownloadDialogImpl.mDownloadFromMarket`（字段） | ❌ 已移除 → 已删除 |
| `ne.y#{b,e,h}`（原以为是广告参数注入点） | ✅ 方法在，但**与广告无关** → 1.5.1 已删除该 hook |
| `HotSearchAdVersionData` | ❌ 无定义 → 仅作兜底 |

所有 hook 都是「挂不上就跳过 + 打日志」，宿主升级不会崩。想看实际命中情况：
抓 LSPosed 日志搜 TAG `HookBrowser`，每个 hook 都会打「已挂载 / 未找到」。

---

## 从源码构建

### 工程元数据

- 版本：**1.10.1**（versionCode 31）—— 本版补上的是**开关的写入端**：
  `compileOnly io.github.libxposed:api:102.0.0` + `implementation io.github.libxposed:service:102.0.0`；
  模块身份由 `META-INF/xposed/` 声明；跨进程**读**开关走 `XposedInterface#getRemotePreferences`，
  **写**走 `XposedService`。
- 构建配置：minSdk 26 / targetSdk 34 / **compileSdk 37**（Java 17）。
  libxposed API 102 的 AAR 元数据要求 compileSdk ≥ 37；AGP 8.2.2 的「未测试」提示用
  `android.suppressUnsupportedCompileSdk=37` 压掉。
- 承接来源：`base.apk`（**原作者 Jun_ao**，原模块 `干掉小米浏览器广告`，v1.2.0 / versionCode 2；
  原作者 QQ 群 **1094704567**）—— 详见 [原作者与致谢](#原作者与致谢)。
- hook 目标核对基线：小米浏览器 **20.27.1010901**（`com.android.browser`，versionCode 202710100，33 个 dex）——
  本工程所有 hook 点都是在这个版本上实测核对过的。
1. 用 Android Studio 打开**本工程根目录**（Hedgehog 或更新版本，需要 JDK 17；AS 自带 JBR 即可）。
2. 直接 `Build > Build APK(s)`。

工程已按国内网络环境配置好：

- Gradle 分发包走腾讯云镜像（`gradle/wrapper/gradle-wrapper.properties`）
- 依赖仓库 `阿里云 → google → mavenCentral` 依次兜底（`settings.gradle.kts`）
- **libxposed 两个库都走 Maven Central**：
  - `compileOnly("io.github.libxposed:api:102.0.0")` —— 宿主进程读开关，运行时由框架提供，不打进 APK。
  - `implementation("io.github.libxposed:service:102.0.0")` —— 模块进程写开关（1.10.1 起必需），
    内含 `XposedProvider`，**必须打进 APK**。manifest 里还要声明它：
    `android:authorities="${applicationId}.XposedService"`（写错 = 静默失效）。
  - 不再需要 `app/libs/api-82.jar`（1.10.0 已删除）。
  阿里云 public 仓库有同名 artifact，拉不到时优先查镜像是否同步。

装好后在 LSPosed 里：**勾选「小米浏览器净化」→ 作用域勾 `com.android.browser` → 强行停止并重启小米浏览器**。

> ⚠ **API 102 需要 LSPosed 2.x**（2.2.0 及以上已实测可用）。作用域由
> `META-INF/xposed/scope.list` 声明（`staticScope=true` 时用户无法在 LSPosed 里额外加包），
> 所以**不要去 LSPosed 的作用域界面找它**——那里显示的是只读的。

编译级别是 **Java 17**（`sourceCompatibility`/`targetCompatibility`/`jvmTarget` 全为 17），
与 AGP 8.2.2 自带的 JDK 对齐，因此不会出现 `源值 8 已过时` 这类 javac 警告。
如果换成 Java 8 反而会重新引出它们 —— 那个警告本身无害，但没必要留着。

---

## 项目结构
```
XiaomiBrowserTuner/
├─ app/
│  └─ src/main/
│     ├─ AndroidManifest.xml             Activity + XposedProvider；xposed* meta-data 1.10.0 起全部移除
│     ├─ resources/META-INF/xposed/      API 102 模块声明（模块身份的唯一来源）
│     │  ├─ module.prop                  minApiVersion=101 / targetApiVersion=102 / staticScope=true
│     │  ├─ java_init.list               入口类：com.hupan.hookbrowser.MainHook
│     │  └─ scope.list                   作用域：com.android.browser
│     ├─ java/com/hupan/hookbrowser/
│     │  ├─ MainHook.kt                  入口，继承 XposedModule，逐功能注册
│     │  ├─ Config.kt                    宿主侧开关读取（remote preferences + 缓存 + 默认值表）
│     │  ├─ ModuleService.kt             模块侧开关**写入**（框架服务 + 本地 SP 全量镜像）
│     │  ├─ Xp.kt                        自带的反射层（取代旧 XposedHelpers）
│     │  ├─ Hooks.kt                     hook 封装（Hooker/Chain 拦截器模型 + per-hook 容错）
│     │  ├─ HostContext.kt               拿宿主 Application（读写宿主私有目录要用）
│     │  ├─ XLog.kt                      日志（v 详细 / i 关键 / e 异常）+ 框架日志转发层
│     │  ├─ features/                    11 个功能，一个一文件
│     │  ├─ adblock/                     规则引擎与宿主规则读写：解析 / 引擎 / CSS / 存储
│     │  │                               + HostAdRules（读宿主自带规则库）
│     │  │                               + HostRuleInstaller（改写宿主规则库，接管用）
│     │  └─ ui/                          SettingsActivity（底部导航容器）+ PrefsFragment（功能栏）
│     │                                 + AboutFragment（关于栏）+ SwitchRowPreference（自绘开关行）
│     │                                 + FeatureDetails（每个功能的详情）+ CardGroupDecoration
│     │                                 + Changelogs（关于页更新日志）+ RuleManagerActivity
│     ├─ res/xml/prefs.xml               开关定义（key 必须与 Config.kt 一致）
│     ├─ res/layout/mx_pref_*            功能行布局（switch / link / category）
│     ├─ res/layout/view_about.xml        「关于」栏内容
│     ├─ res/layout/dlg_feature_detail.xml 功能详情弹窗
│     ├─ res/menu/menu_settings_nav.xml   底部导航菜单
│     ├─ res/color/mx_switch_track.xml    开关滑轨配色
│     ├─ res/color/mx_nav_item.xml        底部导航选中配色
│     └─ res/values{,-night}/colors.xml   MIUI X 设计令牌（浅色 / 深色）
├─ tools/verify_static.py                静态走查（无 JDK 也能跑，交付前必过）
├─ tools/test_js_filter.js               JS_FILTER 回归测试（node 跑，改注入脚本后必过）
├─ docs/                                 逆向分析文档
│  ├─ base.apk-分析报告.md               原 APK 的完整静态分析
│  ├─ 宿主20.27-hook目标核对.md          hook 目标核对结果（第 2 节含 1.6.0 更正）
│  ├─ 搜索栏广告-根因复核.md             搜索栏卡片不在 Java 侧的完整证据链
│  ├─ 胶囊特效统一规范.md                MIUI X 胶囊令牌与动效约定（跨工程通用）
│  └─ 解锁隐藏设置项-无效根因.md         隐藏设置项解锁失败的原因链
├─ LICENSE                               许可与免责声明（非开源许可，见文末）
└─ CHANGELOG.md
```

---

## 实现细节（开发者向）

下面每一节都是「这个模块为什么这么做」的完整推导：反汇编核对结果、失败尝试的记录、每个判定依据的边界。想改代码、适配新版宿主、或只是好奇某个功能怎么实现的，展开对应小节即可。

<details>
<summary><b>全部开关与 Hook 目标（含每个 hook 的具体类名与方法）</b></summary>

### 全部开关与 Hook 目标

| 开关 key | 名称 | Hook 目标（已在宿主 20.27 上核对） | 默认 |
|---|---|---|---|
| `master_enabled` | 启用模块 | 无（总闸：关闭后所有 hook 变 no-op） | 开 |
| `ad_splash` | 开屏广告 | `com.msa.sdk.core.splash.SystemSplashAd#{getAdSplashType, getIsSupportPassiveSplashAd, getServiceIntent}` | 开 |
| `ad_home_promo` | 首页推广位 | `...homepage.PremiumOperationManager#{canShowPremiumChangeHint, getHasShowPremiumGuideDialog, getHasShowSimpleToPremiumGuideDialog}`、`...homepage.SimpleVersionHomeLayout#{chanShowPremiumChangeLayout, userClickChangeToPremiumHome}` | 开 |
| `ad_search_sug` | 搜索推荐广告 | ① `...SugCardData#a`（保留，非广告开关）② `...RecentAppManager#{initRecentAppList, getRecentAppList}`（保留，链路空）③ `...HotSearchManager#getHotSearchAdList` ④ **主拦点（1.6.1）：反射取 `SearchSugManager#mWebView` → 往 sug 页面注入 `JS_FILTER`**。页内先铺 CSS 规则（1.6.5，首帧前拦截、不闪），再由判据一「带广告标的卡片」+ 判据二（1.6.4）「卡内有真实安装按钮的推荐卡」兜底 `display:none`，最后**塌缩只剩空壳的卡片容器**（1.6.6，消掉残留的「查看更多」/细长空白） | 开 |
| `ad_error_page_hot` | 错误页热搜榜 | `...hybrid.HybridActionDispatcher#call`（after）→ 在其返回值里把 `getDefaultPageInfo` 的 `isHotSearchAddImport` 置 false。错误页是 hybrid 页：native 载 `res/raw/miuichromium_error_page.html`，模板 JS 用 `MiHybrid.send("nativechannel://getDefaultPageInfo")` 问宿主要开关数据，**`isHotSearchAddImport` 是「热搜榜」的唯一开关**（它决定走 `hot_search_list` 还是 `search_result` / `want_search` 分支）。详见「错误页去广告」一节 | 开 |
| `ad_custom_rules` | 自定义拦截规则 | **导入自己的过滤规则**（Adblock 语法），挂在 `hyper.webkit.WebViewClient#shouldInterceptRequest` 上：再加 `BrowserWebView#setWebViewClient` / `hyper.WebView#setWebViewClient` 动态捕获每个 client 类 + 基类兜底。详见「自定义拦截规则」一节 | 开（规则库为空时引擎是 no-op） |
| `ad_host_override` | 接管宿主拦截引擎 | **不再只是模块自己拦**：hook 宿主的规则写入通道（`AdBlockHelper$Updator#updateRuleList`、`AdBlockDataUpdator#{writeJSONFile, updateAdBlackist, update}`），把 `files/data/adblock/miui_blacklist.json` 换成导入的规则、清空它的白名单，再调 `MiuiStatics#notifyAdBlockUpdateConfig()` 让 native 立即重载。详见「接管宿主拦截引擎」一节 | **关**（唯一会改宿主自身数据的开关；关闭即从备份还原） |
| `misc_host_ad` | 宿主广告开关 | `...BrowserSettings#{isShowAd, isPersonalizedAdEnabled, isAdCustomDisabled}` | 开 |
| `ui_download` | 下载弹窗 | `...download.CommonDownloadDialogImpl#{onCreateDialog, requestGameRecommend}`、`...DownloadHandler$1#call` | 开 |
| `ua_patch` | UA 伪装 | `...util.WebViewSettingConfig#{getDefaultUserAgent, getUserAgentStringWithoutSwan, getMiuiBrowserUseragentSuffix}` | 开 |
| `ui_search_engine` | 默认搜索引擎 / 切换栏接管 | **注入引擎数据（不改任何 URL 出口）**：hook `...search.SearchEngineDataProvider#{initEngineSet, getSearchEngines, isCustomEngine, getItemTitle, getCurrentEngineTitle}`、`...search.interaction.settings.SearchModuleKVPrefs#isCustomSearchEngineDisplay`、`...search.SearchEngineInfo#getLabel`、`...toolbar.EngineTabsConfig#getAllSearchEngines`、`...toolbar.EngineTabsManager#buildDefaultFixedOrderList`、`...fullsearch.FullSearchActivity#buildSearchUrl`（仅模块引擎） | **开**（下拉可选 bing / google / yandex / baidu，默认 bing） |
| `misc_unlock_pref` | 解锁隐藏设置项 | `androidx.preference.Preference#isVisible` → **true** | **关**（开启时弹风险确认） |
| `misc_debug` | 调试模式 | `...BrowserSettings#{getDebugMode, getFormalDebugMode}`；**同时是模块自身详细日志（`XLog.v`）的总闸**——打开后挂载细节与 `【诊断】/【采样】/【注入】` 才会输出 | 关 |
| `misc_security` | 拦截网址安全检测 | `...Tab$GetSecurityFlagAsyncTask#onPostExecute` | **关**（开启时弹风险确认） |

> ⚠ **1.10.0 定案：开关读取已不再依赖文件权限，整段历史到此为止。**
>
> 这条线踩了三年的坑，值得完整体留档（详见 `Config.kt` 顶部的版本对照表）：
>
> | 版本 | 通道 | 结果 |
> |---|---|---|
> | ≤1.6.5 | `XSharedPreferences` | 异步加载 + 权限 0660 → 宿主进程读出来是空表，默认 false 的开关永远开不起来 |
> | 1.6.6 / 1.6.7 | 自己扫路径 + 直读 XML | 同步可靠；但靠模块进程 `chmod o+r` 硬撑 |
> | 1.9.8 | 删 `XSharedPreferences` 消废弃警告 | LSPosed 不再重定向 → SP 落进 uid 私有目录，**宿主连父目录都进不去** → 功能全失效 |
> | 1.9.9 | 试图镜像到 `/data/local/tmp` | 前提不成立：`fix()` 跑在模块进程（普通 app uid），根本写不了该目录 |
> | **1.10.0** | **`getRemotePreferences`（API 102）** | 开关存在框架数据库，跨进程直读，**零权限 hack** |
> | **1.10.1** | **加 `XposedService` 写入端** | 1.10.0 只迁了读取端，模块 App 还在写本地 XML → 两端不是同一份数据；补上后即时同步 |
>
> 所以：`PrefsFileAccess` 已删除，「关于」页不再显示权限串，`candidateFiles()` / XML 正则解析
> 那一整套文件机制全部移除。**再遇到「开关读不到」时不要再往权限方向排查** ——
> 新通道下不存在这个失败点，该看两件事：
>
> ① 模块 App 有没有连上框架服务（「关于」页状态行会明说；日志 `框架服务已连接：…`）；
> ② `getRemotePreferences` 是否抛异常（嵌入式框架会抛 `UnsupportedOperationException`，日志里会有 `[E]` 行）。
>
> ⚠ **读和写是两个不同的库**：`api` 是宿主进程读（`compileOnly`），`service` 是模块进程写
> （`implementation`，内含 `XposedProvider`，必须打进 APK）。只加 `api` 不加 `service` 就会出现
> 「界面正常、宿主读到空表」。
>
> 判读（TAG `HookBrowser`）：
> ① `【诊断】remote preferences 已就绪（组=settings），初始 N 个开关：[…]` —— `N>0` = 读到了；
> ② `[E] getRemotePreferences(settings) 失败…` —— 框架不支持，全部开关退化为默认值
> （默认 true 的广告类看起来「正常」，默认 false 的 `misc_unlock_pref` / `misc_debug` /
> `misc_security` 永远开不起来）；
> ④ `【诊断】宿主在读 Preference#isVisible：开关=开/关`，**值变化时重打**；
> ⑤ `【诊断】采样满 12 个：… 其中 M 个宿主原本判不可见` —— 本机实测 **M=2**
> （`pref_header_desktop_search` / `pref_incentive_task_center`），
> 说明宿主**确实在用 `isVisible` 藏东西**，开关一开就该出来。

UA 有 3 种可选模式：Chrome 移动版（真机信息，默认）／多 App 伪装（与 base.apk 硬编码串一致）／桌面版 Chrome。

</details>


<details>
<summary><b>设置页 UI 与「点开关切换 / 点行看详情」</b></summary>

### 设置页 UI

底部导航两个 tab：**左「功能开关」/ 右「关于」**，用 `show/hide` 切换（不是 `replace`），
切回来时滚动位置、装饰器、SP 监听都还在。

- **骨架**：`res/layout/activity_settings.xml` = 顶部标题区（`bg_mx_header` 淡出渐变，
  标题/副标题随 tab 变）+ `FrameLayout` 内容容器 + `BottomNavigationView`；
  菜单在 `res/menu/menu_settings_nav.xml`，选中配色 `res/color/mx_nav_item.xml`，
  选中胶囊底色 `MxNavIndicator`（`themes.xml`，M3 默认走 `colorSecondaryContainer`，这里用自己的令牌）。
- **左 tab「功能」= `PrefsFragment`**（androidx.preference）：
  - 每行用自定义布局（`mx_pref_switch.xml` / `mx_pref_link.xml`，分组标题 `mx_pref_category.xml`），
    **分组卡片**由 `ui/CardGroupDecoration.kt` 画在 RecyclerView 上：组内首行圆上角、末行圆下角、
    中间行直角（相邻行拼成一张整卡）+ 组内细分隔线 + 组间留白。
    `PreferenceFragmentCompat` 自带的全宽分隔线装饰会被整体摘掉（它和卡片打架）。
  - 分组判定用 `PreferenceGroupAdapter.getItem(pos) is PreferenceCategory` —— 那是**库自己的
    可见顺序**（会跳过 `app:dependency` 不满足而隐藏的项），自己遍历 `PreferenceScreen` 会错位。
- **交互：点开关 = 切换，点行里其它地方 = 看详情**。这一条决定了不能用库自带的
  `SwitchPreferenceCompat`：
  - 它注入进行布局 `android:id/widget_frame` 的开关（`preference_widget_switch_compat.xml`）写死了
    `android:clickable="false"` —— 点开关等于点整行，两条路径分不开；
  - 它继承的 `TwoStatePreference#onClick` 把「整行点击」直接当切换，行点击腾不出来。
  所以功能行改用自绘的 **`ui/SwitchRowPreference`（继承 `TwoStatePreference`）**：
  - `onClick()` 覆盖成**空**（行点击不再切换），`Preference#performClick()` 的顺序是
    先 `onClick()` 再 `onPreferenceClickListener`，所以行点击照样落到「看详情」上；
  - 开关自己在行布局里画（`MaterialSwitch`，`@+id/hb_switch`），`onBindViewHolder` 里
    **先摘 `OnCheckedChangeListener` 再回填状态**（ViewHolder 复用，否则回填会被当成用户操作写盘）；
  - 点开关走 `callChangeListener(value)` —— 危险开关的确认框挂在那儿，被否就把手柄弹回。
  - 详情内容在 `ui/FeatureDetails.kt`（作用 / Hook 目标 / 生效方式 / 注意事项），弹窗用
    `dlg_feature_detail.xml`，底部按钮可以直接切换该项。数据用 `Config` 的常量当键，
    改 key 时对不上会立刻发现。
  - **1.9.6 起点「切换」不关窗**：原地刷新顶部状态胶囊（220ms 底色/文字色过渡）并翻转按钮文案，
    可以连着点几下看效果。两个风险开关的确认框是异步落盘的，弹窗会在 SP 变化时重新对齐
    （自己发起的写入要挡掉，否则动画会被同一帧拍回终态）。
  - ⚠ 下拉行（`ListPreference`）与「规则管理」入口的**行点击是主操作**（开下拉 / 进页面），
    所以详情只挂在开关行上。
- **右 tab「关于」= `AboutFragment`**：版本（`versionName · versionCode`，运行时读 `PackageManager`）/
  简介 / 作用域 / **框架服务连接状态**（1.10.1 起：未连接 = 开关改动不会同步到宿主）/ 更新日志
  （`ui/Changelogs.kt`，与本仓库 `CHANGELOG.md` 同源，只留最近几版）。
  1.10.0 起**不再显示「开关文件权限」** —— remote preferences 通道下不存在这个失败点。
- **设计令牌**：`mx_bg` `mx_card` `mx_text_primary|secondary|hint` `mx_accent` `mx_accent_soft`
  `mx_divider` `mx_track_off`；浅色 `values/colors.xml`、深色 `values-night/colors.xml`。
  **1.9.6 起这组值以多看调谐器的 `@color/miuix_*` 为基准逐值对齐** ——
  改任何一个值都要同时改 DuokanTuner 与 HookFanqie，详见 `docs/胶囊特效统一规范.md`。
  主题 `Theme.HookBrowser` 是 Material3.DayNight.NoActionBar。**状态栏图标明暗不写死** ——
  Material3.DayNight 自己按日夜给值，写死 true 会让深色模式下状态栏白底白字看不见。
- **胶囊**（1.9.6 统一）：形状一律 stadium（圆角 = 高度 / 2）。
  标签胶囊高 22dp / 圆角 11dp（`bg_mx_pill` + `bg_mx_pill_off`）；
  底栏选中态走 M3 的 `itemActiveIndicatorStyle`（`MxNavIndicator`，64×32dp 全圆角）。
  块的圆角是另一套：卡片 20dp（`bg_mx_card` + `CardGroupDecoration`）、内容块 12dp（`bg_mx_block`）。

#### 这套 UI 已固化为跨栈规范

同一套 MIUI X 观感在三种技术栈上的落地方式，统一记在 skill **`miuix-module-settings-ui`**：
设计令牌（色值 / 尺寸）、「点开关切换、点行看详情」交互约定、功能详情弹窗与关于页结构、坑清单。

三个兄弟工程之间的**胶囊特效规范**（形状 / 配色 / 动效参数、各工程落点、对账清单、踩坑）
单独记在 `docs/胶囊特效统一规范.md`。

| 栈 | 参考实现 |
|---|---|
| Kotlin + XML + androidx.preference | **本工程**（`ui/SwitchRowPreference` + `ui/CardGroupDecoration` + `ui/FeatureDetails`） |
| Kotlin + Compose + `top.yukonga.miuix` | `HookFanqie`（番茄小说模块）→ `ui/SettingsScreen.kt` |
| Java 全自绘（`Ui.java` 工具集） | `DuokanTuner`（多看阅读模块）→ `Ui.java` + `FeatureDetails.java` |

三份代码**不能互相复制**（组件模型完全不同），能复用的是令牌、尺寸与交互约定。
各栈在「点行看详情」上的做法差异见 skill 第 4 节 —— 本工程要自绘 Preference 才是因为
`SwitchPreferenceCompat` 注入的开关写死了 `clickable=false`。

#### 日志分档

| 档 | 何时输出 | 内容 |
|---|---|---|
| `XLog.v(...)` | 开关 `misc_debug` 打开时；**默认完全静默** | 挂载细节、`【诊断】/【采样】/【注入】/【内置规则】` 明细 |
| `XLog.i(...)` | 始终，每进程十行以内 | 注入、注册完成、规则库摘要、接管/还原、规则导入结果 |
| `XLog.e(...)` | 始终 | 异常 |

闸门**每次现查开关**（`Config` 自带 1 秒缓存），所以宿主运行期间打开「浏览器调试模式」，
日志当场变详细，不用重启浏览器；读不到配置回退 `false`（静默）。

</details>


<details>
<summary><b>默认搜索引擎 / 切换栏接管</b></summary>

### 默认搜索引擎 / 切换栏接管

把 **bing（默认）／Google／Yandex／百度** 注入宿主的引擎数据，首页搜索框下方那排切换栏
（原生「全网 / 百度 / 抖音」）里就**多出**这三个引擎：**点哪个真的用哪个**，文字、图标、高亮同步。
开关 key `ui_search_engine`（**默认开**），引擎 key `ui_search_engine_target`（默认 bing）。

#### 宿主链路（在 20.27.1010901 上反汇编核对）

```
模板（服务端 searchengine.json；本地兜底 res/raw/local_search_engine.json，占位符 {searchTerms}）
  → SearchEngineDataProvider.mEngineSet.searchBox[引擎名].searchUrl
  → SearchEngineInfo.mSearchEngineData     ← SearchEngineDataProvider#getSearchEngineContentByScene
  → SearchEngineInfo.searchUri() = mSearchEngineData[3]
  → SearchEngineInfo#getFormattedUri(): replaceAll("{searchTerms}", URLEncoder.encode(query,"UTF-8"))
```

调用方只有两条路：`BrowserActivity$2#composeSearchUrl`（地址栏回车）与 `ReloadSearchAction#performSearch`
（页内搜索）→ 都经 `OpenSearchSearchEngine#getSearchUriForQuery`；
`FullSearchActivity#buildSearchUrl`（全屏搜索页）自己读 QSB 配置拼 URL。

切换栏走的是**另一条链路**（1.7.0 补全，缺任一环栏里就看不到新引擎）：

```
EngineTabsManager#buildEngineList
  ├─ EngineTabsConfig#getAllSearchEngines            ← 按硬编码白名单给图标
  │   ← SearchEngineDataProvider#getSearchEngineList(true,"browserSearchBox")
  │       ├─ #getSearchEngines("browserSearchBox")   ← **遍历 searchBoxOrder**，逐个查 searchBox
  │       ├─ #getRealSearchEngineLabels(names)       ← 每个名字过 getItemTitle(name)
  │       └─ 过滤：isCustomSearchEngineDisplay() 为假时丢掉「自定义引擎」
  ├─ [AI 搜索开] sortWithDefaultFirst(list, currentTitleTabEngineName)
  └─ [AI 搜索关] buildDefaultFixedOrderList(list)
        ← **硬编码顺序数组 {onesearch, baidu, douyin, red}**，白名单外的引擎整批丢弃
  → EngineTabAdapter#setEngines() → initSelectedState()
        ← getDefaultSearchEngineNameByScene("browserSearchBox")
            ← mEngineSet.defaultSearchEngine["browserSearchBox"]   ← **栏内高亮看这个**
```

图标白名单（`EngineTabsConfig` 的 sparse-switch）：`baidu / sogou / sm / 360 / toutiao / red /
douyin / onesearch / google / ai_search` 各有专属 res，**没有 bing / yandex** ——
所以这两个走宿主的「自定义引擎」通道（通用图标 `ic_search_engine_tab_custom`）。

#### 关键事实（1.7.0 实测更正 1.6.8/1.6.9 的误判）

`getSearchEngineMapByScene("browserSearchBox")` 返回的就是 `processSearchEngineData(mEngineSet.searchBox)`
—— 切换栏场景的 map 与 `searchBox` **是同一个池子**，不是两份数据。
所以把引擎塞进 `searchBox` 之后，`isEngineLoad` / `getSearchEngineContentByScene` / 宿主自己拼 URL
全部自洽，**任何 URL 出口都不需要替换**。（1.6.8 误判成「两份数据」，退而只换出口，
副作用正是「切换栏失效、切来切去只有 bing」；1.6.9 改对了方向但漏了三处：`searchBoxOrder` 没补、
`buildDefaultFixedOrderList` 白名单没绕、栏内高亮读的 `defaultSearchEngine` 没写。）

#### 实现

| # | hook | 作用 |
|---|---|---|
| 1 | `SearchEngineDataProvider#initEngineSet`（after） | 数据被服务端重建后立刻补齐引擎项 |
| 2 | `SearchEngineDataProvider#getSearchEngines`（**before**） | 宿主遍历 `searchBoxOrder` 之前先把 key 补进去（`searchBox` + **`searchBoxOrder`**） |
| 3 | `SearchEngineDataProvider#getSearchEngines`（after） | 模块引擎排前、宿主自有项保持原顺序 —— **新增**而非替换 |
| 4 | `SearchEngineDataProvider#isCustomEngine`（after） | bing / yandex → true，走宿主「自定义引擎」通道，栏里才显示得出来 |
| 5 | `SearchModuleKVPrefs#isCustomSearchEngineDisplay`（after） | 置真；为假时 `getSearchEngineList` 会把自定义引擎整批过滤掉 |
| 6 | `SearchEngineDataProvider#getItemTitle`（after） | 项文字 → 中文名（也喂给 `getRealSearchEngineLabels`） |
| 7 | `SearchEngineDataProvider#getCurrentEngineTitle`（after） | 标题栏 / 搜索框引擎名 → 中文名 |
| 8 | `SearchEngineInfo#getLabel`（after） | 同上（`mName` 字段） |
| 9 | `EngineTabsConfig#getAllSearchEngines`（after） | 栏里顺序：模块引擎打头 |
| 10 | `EngineTabsManager#buildDefaultFixedOrderList`（after） | **本版核心**：从入参把白名单外的模块引擎捞回来，排在宿主项之前 |
| 11 | `EngineTabsManager#buildEngineList`（after） | 按 id 去重：简洁首页（`isSimpleHome`）时宿主会把「自定义引擎且非当前」的项再追加一遍，bing/yandex 正是自定义引擎 |
| 12 | `FullSearchActivity#buildSearchUrl`（after） | 全屏搜索页兜底，**仅对模块引擎生效**（它读的是服务端 QSB JSON，不一定有 bing） |

`mEngineSet` 里三处要一起写（1.7.0 结论）：

- `searchBox[key] = 引擎项`（`SearchEnginesEntity$SearchEngine` 无参构造 + setter：`setSearchEngineName` / `setSearchUrl` / `setChannelNo` / `setTitle_zh_CN` / `setShowIcon(true)`）
- `searchBoxOrder += key` —— **不加这个等于没加**，`getSearchEngines()` 只看这张顺序表
- `defaultSearchEngine["browserSearchBox"] = 默认引擎` —— 栏内高亮读它

**默认引擎怎么「生效」**：注入完成后调一次 `SearchModuleSettings#setSearchEngineName(默认引擎)`
（进程内**只写一次**）→ 宿主的当前引擎就是它，实际请求、搜索框文字、栏内高亮三者一致。
之后你在栏里切到任何一项都尊重你；重开浏览器回到下拉选定的默认值。

**图标**：google / baidu 留在 `EngineTabsConfig` 白名单内 → 用宿主原生专属图标；
bing / yandex 白名单里没有 → 走自定义引擎通道，用宿主自带的通用图标 `ic_search_engine_tab_custom`。
（模块不能把自绘图标塞进宿主资源表，所以这两个共用通用图标；文字是准确的。）

#### 判读日志（TAG `HookBrowser`）

```
注入搜索引擎 bing（必应）｜模板=https://cn.bing.com/search?q={searchTerms}&…
补齐 searchBoxOrder = [onesearch, baidu, douyin, bing, google, yandex]
默认搜索引擎 browserSearchBox → bing（切换栏高亮）
默认搜索引擎=bing（已写入宿主当前引擎，实际搜索与切换栏一致）
```

没有「注入」行 = 开关没读上（先看 `【诊断】生效来源=` 是否为空）或宿主已改类名/方法名。
有「注入」行但栏里没有 = `buildDefaultFixedOrderList` 这个 hook 没挂上（日志里会有「未找到 …，跳过」）。
开关关掉后所有 hook 立即 no-op，宿主回到原行为。

#### 边界

- **服务端每 30 分钟重建数据** → 靠 `initEngineSet`(after) + `getSearchEngines`(before) 双重补齐，不用重启宿主。
- 宿主自带的「搜索引擎」设置页会同步显示这四个引擎（同一份数据），且自定义引擎变为可见。
- 只接管首页搜索框场景（`browserSearchBox`）；热榜 / 搜索发现 / widget 各用独立 map，未动。
- 只切搜索请求，不切宿主内置的搜索建议（sug）接口。
- Google / Yandex 需要设备本身可访问。
- 宿主新增/改名的引擎项（白名单数组、`searchBoxOrder`）在版本升级后可能需要重新核对。

</details>


<details>
<summary><b>自定义拦截规则（Adblock 语法、引擎、护栏、接管宿主引擎）</b></summary>

### 自定义拦截规则

用户导入自己的过滤规则，**两条通道各管一半**：

| 通道 | 规则形状 | 做法 | 管什么 |
|---|---|---|---|
| 请求拦截 | 域名 / 路径 / 关键词 / 通配 / 正则 | `shouldInterceptRequest` 命中即返回空响应 | 资源**拉不拉得到** |
| 元素隐藏 | `##selector`、`域名##selector` | 编译成 CSS，`onPageFinished` 时注入 `<style>` | 拉到了也**不显示** |

两条都需要：百度搜索结果页的推广位属于后者 —— 广告条目是**页面 DOM 里的节点**，URL 全是
`www.baidu.com` 自家路径，请求维度再怎么加规则也拦不到（1.8.1 真机实测：6023 条规则全部
编译生效，整场只拦到 9 次，全是百度埋点）。

#### 为什么落在 `shouldInterceptRequest`

宿主 20.27 的 WebView 是 **hyper 内核**（`hyper.webkit.WebView` / `hyper.webkit.WebViewClient`），
不是 `android.webkit.*`。请求在 Java 侧**唯一**会经过的汇聚点就是
`WebViewClient#shouldInterceptRequest` —— 宿主自己也在用它（`Tab$MainWebViewClient`
拿它做预加载缓存，命中就在方法里自造 `WebResourceResponse` 返回，反汇编确认）。

返回非 null 的响应 = 该请求不再走网络；返回 null = 交回宿主/内核照常加载。
所以命中时**不阻断宿主逻辑，直接给它一个响应**，其余一律不插手。

#### 三条 hook 路（只挂一个类名必然漏）

`shouldInterceptRequest` 是**子类 override** 的方法 —— hook 基类拦不到不调 super 的子类：

1. **动态捕获（主力）**：hook `miui.browser.webview.BrowserWebView#setWebViewClient` 与
   `hyper.webkit.WebView#setWebViewClient`（after），每设置一次 client 就把**那个实例的类**
   挂上 `hookAllMethods("shouldInterceptRequest")`。新闻详情、搜索建议、自定义 Tab 里的
   WebView 全都经过 setter。
2. **已知主 client**：按类名直接挂 `com.android.browser.Tab$MainWebViewClient`（它可能在
   模块注入之前就设好了，第 1 条会错过）。
3. **基类兜底**：挂 `hyper.webkit.WebViewClient` 基类 —— 只 override 了 String 重载的子类
   会把请求交给基类。

已挂类名进 Set 去重：同一个类被 hook 两次会让同一个请求走两遍判定。

#### 元素隐藏：为什么是注入 CSS 而不是「拦节点」

`##` 规则编译成一段 CSS，注入 `<style>`：

```js
var c = "全局选择器们";
var m = { "baidu.com": "选择器们" };          // 域名限定桶
if (h === d || h 以 "." + d 结尾) c += "," + m[d];   // 后缀匹配，与 URL 引擎语义一致
style.textContent = c + "{display:none !important}";
```

- **声明式，插一次长期生效** —— 首屏渲染的广告和 SPA 后来动态插入的，一起隐掉，不需要
  MutationObserver 去追；
- 注入时机是 `onPageFinished`（主线程，`evaluateJavascript` 也要求主线程，时机正好）；
- 注入对象靠**按形状找方法**拿到：`cls.methods` 里第一个 `evaluateJavascript(String, ?)`，
  第二个参数传 `null`（各内核的 `ValueCallback` 是各自的接口，我们根本不碰）。
  **不能**强转 `android.webkit.WebView` —— `hyper.webkit.WebView` 有 212 个自己的方法，
  是内核自带的独立实现，不是 android 那个的子类，强转一个都注入不进去；
- `onPageFinished` 沿**继承链**找：`hookAllMethods` 只挂该类自己声明的方法，而绝大多数 client
  并不重写 `onPageFinished`，直接在子类上找会返回 0 条。

全局规则（无域名限定）另有一道闸：选择器里必须出现 class / id / 属性 / 组合器之一。
`##div`、`##iframe` 这类纯标签名进 CSS 就是全站正文塌陷，直接丢。

> **升级后必须重新导入规则**：1.8.x 的 `##` 规则在导入时就被丢了，规则库里没留存。
> 到「规则管理」把原来的 URL / 文件重新导入一遍（同一来源覆盖，不堆积）。

#### 引擎：分桶 + 字面量预筛

`shouldInterceptRequest` 在网络线程、每个资源请求都调一次，规则动辄上千条，能 O(1) 的绝不走正则：

| 规则形状 | 匹配方式 |
|---|---|
| `\|\|ads.com^` 纯域名 | **HashSet 后缀查**：`a.b.ads.com` 依次查 `a.b.ads.com` / `b.ads.com` / `ads.com` / `com` |
| `adserver.`、`/ads/banner/` 裸串 | 小写 `contains` |
| 带 `*` / `^` / `/re/` | 编正则，**先用最长字面段预筛**（URL 里没有那段字面量就整条跳过） |

编译产物完全不可变，`@Volatile` 换引用即可，回调里零解析；规则库由独立线程每 8 秒
轮询一次重编译，**不在**热路径上读文件。

> 域名锚转正则有两个坑，缺一个都会误杀：
> ① 前缀不能写成 `(?:[^/?#]*\.)?` —— `evilads.com` 的 `evilads.` 会被吃进前缀，
>    要用 `(?:[^/?#.@]+\.)*`（前缀必须是**完整域名标签**）；
> ② **整条正则必须 `^` 锚定到 URL 开头**。少了 `^` 就退化成「URL 任意位置出现」，
>    `||ads.com^` 照样命中 `evilads.com`（子串里就有 `ads.com`）—— 1.8.0 的真实 bug。
> 纯域名走 HashSet 不受影响，但 `||ads.com/ads.js` 这种复合规则必须走正则，两个细节都关键。

#### 参数与返回值：三套内核 = 三套同名类型（1.8.1 的教训）

宿主进程里同时装着 **hyper**（`hyper.webkit.*`）、**MIUI**（`com.miui.webkit.*`）和 **百度 SDK**
（`com.baidu.searchbox.sailor.*`）的 WebView 体系，同名类的类型**互不相干**：

| 类 | `shouldInterceptRequest` 返回 |
|---|---|
| `hyper.webkit.WebViewClient` | `android.webkit.WebResourceResponse` |
| `miui.webkit.WebViewClient` | `com.miui.webkit.WebResourceResponse` |
| `BdSailorWebView$BdWebViewClientProxy` | `com.baidu.searchbox.sailor.variant.WebResourceResponse` |

于是两条铁律：

- **参数一律反射取**（`getUrl()` / `isForMainFrame()`）。写 `arg is android.webkit.WebResourceRequest`
  会让 MIUI / 百度 SDK 的请求全部漏掉 —— 日志上「规则加载 5000 条」但什么都拦不住。
- **返回值按 `p.method.returnType` 现造**：android 原生对象能用就用，否则反射
  `(String, String, InputStream)` 构造目标类型；**造不出来就放行**。
  塞错类型会让 LSPosed 在 `proceed` 里抛 `ClassCastException`，而异常抛在 hook 回调**之外**，
  回调里的 try/catch 接不住 → 宿主闪退（1.8.0 真机：命中后 21ms 崩）。


#### 语法支持

支持 `!` / `#` 注释、`[Adblock Plus 1.1]` 头、`@@` 例外、`||host^`、`*` 通配、`^` 分隔符、
`/regex/`、裸子串。

**不支持并如实上报条数**（界面显示「忽略 N 条元素隐藏、M 条不合规」）：

- **元素隐藏**（`##` / `#@#` / `#?#`）—— 要注入 CSS 才生效，本模块不碰 DOM。误藏正文的代价远大于漏广告。
- **`$domain=` 选项** —— 按域名限定的规则没法在纯 URL 维度安全还原，**整条丢弃**（宁漏勿误杀）。
- 其余 `$script` / `$image` / `$third-party` 选项：**剥掉选项、保留规则本体**（比原规则更宽；
  要按类型过滤得额外读请求头，收益不抵成本）。

#### 护栏

导入时就掐掉危险规则（这些一旦生效会拦掉半个互联网）：

- 单条 > 512 字符丢；正则 > 160 字符丢；含 `)+` / `)*`（ReDoS 特征）丢
- 裸串 < 4 字符且不含 `/`、`.` 丢（`ad` / `js` / `img`）
- 通配规则去掉通配符后最长字面段 < 3 丢（`*a*` 等于全匹配）
- 上限见下表

##### 各层上限（1.9.1 集体放宽）

**为什么必须放宽**：真机上「导入界面显示 5000 条拦截 + 4000 条隐藏」—— 两个数正好等于解析层的
两个硬上限，**规则被静默截断了**。而且只放宽解析层没用：引擎三桶会接着丢，中文规则集里带路径 /
通配的规则全进正则桶，原来只留 1500 条。

| 位置 | 项 | 旧 | 1.9.1 |
|---|---|---|---|
| `AdRuleParser` | 单次导入 URL 规则 | 5000 | **30000** |
| `AdRuleParser` | 单次导入元素隐藏 | 4000 | **15000** |
| `AdRuleEngine` | 域名桶 | 6000 | **40000** |
| `AdRuleEngine` | 子串桶 | 4000 | **8000** |
| `AdRuleEngine` | 正则桶 | 1500 | **6000** |
| `AdElementCss` | 全局 / 限定 / 域名桶 | 1500 / 3000 / 400 | **6000 / 12000 / 2000** |
| `AdRuleCodec` | 规则库 JSON | 2 MB | **4 MB** |

三个引擎桶按各自成本分别涨价，不是等比例拍脑袋：**域名桶**走 HashSet 后缀查（几次哈希）→
放最宽；**正则桶**每条都有最长字面段预筛（URL 里没那段字面量就整条跳过）→ 放到 6000；
**子串桶**是唯一**无条件线性 `contains`**、没有预筛可用的 → 只翻一倍。

⚠ **放宽上限不会补回已经丢失的规则** —— 截断发生在导入那一刻，被砍掉的条目从来没写进规则库。
要拿全必须在「规则管理」里**重新导入一次**。

#### 宿主自带规则库（1.9.1 起只读读取，1.9.2 起可选接管）

小米浏览器**自己就有一套广告规则**，反汇编 `com.android.browser.util.AdBlockDataUpdator` 的事实：

| 事实 | 依据 |
|---|---|
| 目录 `files/data/adblock/`，5 个 json | `init()`：`sParentFilePath = getFilesDir() + "/data/adblock"`；`miui_{black,white,watch,privacy,business}list.json` |
| 格式 `{"data":[…]}` | `readBlacklist()`：`new JSONObject(str).getJSONArray("data")` |
| 内容是标准 Adblock 语法，喂给 chromium 的 **native** 匹配器 | 真机日志 `chromium: <AdBlock> BlockingRuleMatcher::Parse error \|\|57577.live^$csp=script-src`、`@@\|acfun.cn^$document` |

宿主那套语法**比本模块完整**（`$csp` / `$third-party` / `$document` / `$generichide` 全认，
本模块解析器把这些整条丢弃），但只管 URL 维度，且开关在服务端配置里
（`PREF_ENABLE_ADBLOCK` / `disable_webview_adblock_business`）—— 普通用户改不了，真机未必开着。

所以 1.9.1 把它读出来并进同一个引擎：`HostAdRules` 每 60 秒扫一次，原始文本**再走一遍本模块的
解析器**（同一套护栏 + 自动分流 `##` / URL），与用户导入的规则合并。收益：用户不导入任何规则时
模块也不空转；宿主规则里的 `##` 条目还能走 CSS 通道兑现。

**带 `$` 选项的不接**（`$csp` / `$generichide` 这类交给 native）—— 本模块的解析器只会把选项
**剥掉**，而剥掉后语义**变宽**：`@@||x^$generichide`（原意只是「不隐藏通用元素」）会变成
「放行 x 的所有请求」，反过来把用户自己导入的规则全压掉。宁漏勿误杀。

- 1.9.1 默认**只读**，绝不写回那个目录：宿主自己有 `watermark` 增量更新协议
  （`checkUpdateMode` / `addDiffAdBlockData` / `writeOTAFile`），掺进去会让它的差分对账错乱。
- 宿主私有目录模块进程读不到，靠 `HostContext`（hook `ContextWrapper#attachBaseContext`
  拿宿主 Application）在宿主进程里读 —— 权限等同宿主自己。

**1.9.2 起多了一个选项**：打开 `ad_host_override` 就不再是"读它的规则"，而是**让宿主跑我的规则**
（写回那个目录 + 通知重载，并在每次宿主自己写盘后贴回）。见下一节。

#### 接管宿主拦截引擎（1.9.2，开关 `ad_host_override`）

上一节是「把宿主的规则读进来」，这一节反过来：**让宿主用它自己的 native 引擎跑我的规则**。

##### 为什么只能改文件（反汇编结论，不是偷懒）

宿主除了 Java 侧能 hook 的 `shouldInterceptRequest`，**还同时**在 native 层用
`BlockingRuleMatcher` 做 URL 过滤（日志 tag `<AdBlock>`）。后者语法更全
（`$csp` / `$third-party` / `$document` / `$generichide`），跑在 native、零 Java 开销。
把规则喂给它的路径**只有一条**：

```
AdBlockHelper#updateRules(context)
  └─ 线程 → Updator#updateRuleList("black") / ("white")
       → 把 content://com.miui.browser.adblock/… 写到
         files/data/adblock/miui_blacklist.json / miui_whitelist.json
       → MiuiStatics.getInstance().notifyAdBlockUpdateConfig()   ← native 重载
```

native 按**文件路径**读，Java 侧不存在「注入规则」的接口。所以做法是
**hook 它的写入通道 + 覆盖规则文件 + 叫它重载**（`HostRuleInstaller` / `HostAdOverrideFeature`）。

##### 换哪些、留哪些

| 文件 | 处理 | 为什么 |
|---|---|---|
| `miui_blacklist.json` | 整体替换为导入的请求拦截规则 | 这才是 native 实际过滤用的那份 |
| `miui_whitelist.json` | **清空** | 宿主的 `@@` 例外优先级最高，留着会把用户规则压掉 —— 现象正是「导了、编译了、就是拦不住」 |
| `miui_watchlist` / `miui_privacylist` / `miui_businesslist` | 保持原样 | 用途不同，动它们没有收益只有风险 |

`##` 元素隐藏规则**不往里写**：native 不认 `##`，写进去只会刷 `<AdBlock> Parse error`，
那部分继续由本模块的 CSS 注入通道负责。两条通道互补，同一请求两边都命中时结果一致。

##### 三条防线（防止被宿主覆盖回去）

1. **Application 一就绪就写**（`HostContext.onApplication`）—— 赶在 native 首次读盘之前
2. **hook 宿主的写入通道**：`AdBlockHelper$Updator#updateRuleList`（服务端下发）+
   `AdBlockDataUpdator#{writeJSONFile, updateAdBlackist, update}`（OTA 差分更新）—— 它一写就贴回
3. **守护线程每 8 秒校验一次**：`规则指纹 + 文件字节数` 双条件。只看指纹文件会被骗 ——
   文件还在、内容早被换掉了，必须同时确认黑名单文件的长度与写入时一致

写完立刻 `notifyAdBlockUpdateConfig()`，**不用重启浏览器**。

##### 备份与还原

首次接管前把 5 个文件整体复制到同目录的 `.hb_backup/`；关闭开关自动还原。
**备份目录存在就不再覆盖** —— 保证里面永远是"接管前"的原件，
「开→关→开」循环不会把改过的内容当成原件存下来。

##### 判读日志（TAG `HookBrowser`）

| 日志 | 含义 |
|---|---|
| `【宿主规则】已备份宿主规则库 N 个文件 → …/.hb_backup` | 首次接管，备份成功 |
| `【宿主规则】已接管宿主拦截引擎：写入 N 条自定义规则，清空小米白名单，其余名单保持原样` | 生效了 |
| `【宿主规则】⚠ 自定义规则为空，宿主引擎当前处于「不过滤」状态` | 开关开着但没导入规则 |
| `【宿主规则】通知 native 重载失败（规则已落盘，重启浏览器后生效）` | 重载口没调通，规则已写但要重启才读 |
| `【宿主规则】开关已关闭，宿主规则库已还原为接管前的版本` | 还原成功 |

#### 存储：独立组

规则库落在**独立的** group `adrules`，**不塞进** `settings`：开关表小、规则库动辄几百 KB，
分开后宿主侧可以给它们不同的刷新节奏（开关 1 秒、规则 5 秒，各自缓存）。

1.10.0 起写入后**不需要任何权限动作** —— remote preferences 存框架数据库，
读写都走框架通道，没有文件权限这个环节（≤1.9.9 那步 `chmod o+r` 已删除）。

⚠ 但**写入必须经 `ModuleService`**：模块进程写的是本地 XML，宿主读的是框架数据库，
`AdRuleStore.save` 写完要 `ModuleService.push(ctx, "adrules")` 同步过去，
否则就是「导入成功但永不生效」（1.10.0 就是漏了这步）。
单个 value 超过 40 万字符会被跳过并打 `[E]`（远端写入是一次 Binder 事务，塞太大整体会失败）。
`verify_static.py` 第 10 项把「模块侧写 / 宿主侧读」两处的组名钉死。

#### 判读日志（TAG `HookBrowser`）

| 日志 | 含义 |
|---|---|
| `自定义规则：已挂 X#shouldInterceptRequest（2 个重载），累计 N 个 client 类` | hook 装上了；`N` 应随浏览逐渐增加 |
| `【内置规则】宿主规则库 N 条（miui_blacklist.json=… miui_whitelist.json=…）` | 宿主自带规则读到了，只在内容变化时打。**这行缺失**说明 Context 钩子没就绪或目录不存在 |
| `【规则】用户 A 组…宿主内置 X 条 → 分流 URL Y + 隐藏 Z 条；合计生效 URL … 隐藏 …` | 两路来源分开显示。`生效` 明显小于总数时看是不是被护栏或各层上限拦掉 |
| `【诊断】规则库：组 adrules 有 … 个 key，sets 长度=…` | remote preferences 读到了组。**这行显示「组为空」**= 模块进程还没导入过规则 |
| `【拦截】#N 规则=… URL=…` | 真拦到了。前 30 条全打，之后每 200 条打一次 |

#### 边界

- 只接管 `http` / `https` 请求，且 URL 长度 ≤ 4096。
- 字符串重载（`shouldInterceptRequest(WebView, String)`）拿不到 `isForMainFrame`，
  一律按**子资源**处理（返回空 body）—— 冷门 WebView 的整页导航宁可变空白，
  也不误判成整页替换。
- 整页命中返回的是**说明页**（含 URL 与命中的规则原文），不是白屏：网上下载的规则集很容易
  过度匹配，白屏只会让人以为浏览器坏了。
- 改动几秒内生效，不用重启浏览器；关掉开关立即恢复原样（不写任何宿主 SP）。

</details>


<details>
<summary><b>错误页去广告</b></summary>

### 错误页去广告

「无法访问」错误页底部那一条**「热搜榜」**（点进去跳**大米搜索 DJY**，跟用户要访问的站点毫无关系）
是宿主下发的推广位。它由模板 JS 决定渲染，位置在**混合页**里。

#### 链路

```
native chromium 载 res/raw/miuichromium_error_page.html（119 KB，单行）
  ↓ 页面 JS
utils.excuteClientAction("getDefaultPageInfo")
  = window.MiHybrid.send("nativechannel://getDefaultPageInfo")
  ↓ JavaScriptInterface（注册名 "MiHybrid"，在 HybridActionDispatcher 构造时挂上）
HybridActionDispatcher#send(String) → 转调 call(String)
  → host = action 名 → 查 HybridActionInjector 注册表（166 条）
  → 四个目标 action 都直接继承 Object、实现 IAction（不是 HybridAction 子类）
  → 当前线程 dealAction() 并返回其结果 ← 这个字符串就是 JS JSON.parse 的输入
```

#### 判据（模板里逐字核过）

```js
e = JSON.parse(o.excuteClientAction("getDefaultPageInfo"));
d = e.searchEnabled;                       // 搜索总开关
_ = e.isHotSearchAddImport || !1;          // ★ 热搜榜的唯一开关（模板里只出现 1 次）
if (!d) return 0;                          // 搜索整体关 → 连「重新搜索该网页」按钮都不加
0 === e.sceneType && e.realTimeHotSpotSwitch && ((_ ? c : n)(), E = 0);
1 === e.sceneType && e.guessYouWantSwitch     && ( 猜你想搜, E = 1, 空则回落到 (_ ? c : n)() );
// 渲染：d && ["a","c"].includes(type) && 0 < l.length
```

| `_` | `E` | 渲染 | 数据来源 / 点击行为 |
|---|---|---|---|
| `true` | 任意 | **「热搜榜」** `hot_search_list` | `getNewHotSearchData` / `getHotSearchData`；`data-action="newJump"` → `recommendWordJumpToDJYSearch` |
| `false` | `0` | 「搜索发现」 `search_result` | 同上；`data-action="jump"` → `recommendedWordClick` |
| `false` | `1` | 「猜你想搜」 `want_search` | `getGuessYouWantToSearchData`；`data-action="jump"` |

#### 实现

`features/ErrorPageFeature.kt`：只挂 `HybridActionDispatcher#call(String)` 的 **after**，在返回值上
把 `isHotSearchAddImport` 从 `true` / `1` / `!0` 改成假值。**不拦调用本身**，所以：

- 错误提示文案、`switchOn` 布局状态、以及 `refresh` / `check` / `localCheck` / `clean` / `detail` /
  `research` / `inKidsMode` 这些按钮**全都不受影响**；
- 「搜索发现」「猜你想搜」保留（它们本就要求 `_ === false`，正是我们制造出的状态）；
- 用字符串替换而不是 `JSONObject` 重排：解析失败只是「这次没拦到」，不会把整份数据变成 `{}` 让页面白掉。

**为什么不用 `ErrorPageResource#getLoadErrorPageContent()` 改模板**：那是 **static** 方法，hook 得到，
但 119 KB 单行模板一换版正则就静默失效，改错了直接白页。改返回值只动一个布尔。

> 备选方案（留给宿主换版应急）：二进制 patch 模板里的 `excuteClientAction:t}}()`
> （**全文件唯一**，已用 APK 内原文 sha1 校验）。见 `docs/宿主20.27-hook目标核对.md` §3.5。

#### 已排查：错误页里没有别的广告

模板全文里 `getDefaultPageInfo` / `getHotSearchData` / `getNewHotSearchData` /
`getGuessYouWantToSearchData` 各出现 **1 次**，`"热搜榜"` / `"搜索发现"` / `"猜你想搜"` 各 **1 次**。
注册表 `ad/` 与 `commerce/` 包下共 6 条广告 action（`drawAdBanner` / `shortVideoADShow` /
`openTextAnchorAd` / `openLandingPage` / `trackAdEvent` / `getOuterAdData`），**错误页模板一条都没调**
—— 它们服务于小说页 / 信息流等其它文档流页面，不是错误页的面。

</details>


<details>
<summary><b>搜索广告：为什么最终在 H5 页面里拦</b></summary>

### 搜索广告（在 H5 页面里拦）

搜索框下拉里那三张带「安装」按钮的卡片（「安兔兔评测 / 甘甘云手机 / 豌豆加速」），
**不在任何 Java 侧列表里** —— 你实测**原版 base.apk 也拦不住**，这条否掉了 1.5.2 的前提。

回宿主 20.27 整条重查 + 1.6.0 诊断版实测读数后的结论：

1. **卡片不在任何 Java 侧列表里。** 原生卡片 `app_recommend` 映射到 `RecentAppViewImpl`，
   但 `RecentAppViewHolder#bindData` 只渲染图标 + 应用名 + 编辑按钮，
   布局 `res/layout/item_recent_app.xml` 只有 `item_recent_app_icon / _name / _delete` 三个 id，
   **没有安装按钮**，与截图对不上。
2. **下拉就是远端 H5。** 1.6.0 诊断读数（决定性）：

   ```
   【诊断】sugPageUse=2                          → isNativeSugPage() 为假
   【诊断】sugUrl=https://sug.browser.miui.com/
   【诊断】SearchSugManager.querySug()
   【诊断】queryKeyword -> {"query":"a", …, "isSearchCard":false, …}
   【诊断】initRecentAppList: list=0 ad=0 optional=0
   ```

   `BaseSuggestionView#onUpdate` / `SearchSuggestionManager#querySuggest` **一次都没被调用** ——
   卡片由 `sug.browser.miui.com` 的页面自己渲染。
3. **历史三层 Java 拦截全部无效**（无副作用，保留）：
   - `SugCardData#a` 反汇编 `lambda$updateSugCardData$0()` 只写 4 个 KvPrefs key
     （`pref_show_sug_switch_view` / `pref_sug_expid` / `pref_second_search_sug` / `pref_show_sug`），
     **没有广告开关**；且 `pref_show_sug` 唯一写入方 `KvPrefs.Qb(Z)` 只写 false → `KvPrefs.g6()` 恒 false
     → 作为 `isSearchCard` 传给 H5 后恒为 false，**卡片照样出现**；
   - `RecentAppManager` 兜底层无效：多轮真机日志里**从未出现**
     `已从应用建议列表移除 N 条广告`，且 `initRecentAppList` 三个列表长度全是 0；
   - `HotSearchManager#getHotSearchAdList` 同理（数据源类在 33 个 dex 里根本不存在，是废代码）。

细节、反汇编证据与可复用命令：**`docs/搜索栏广告-根因复核.md`**。

#### 怎么拦的：复用宿主自己的 JS 通道

反汇编 `com.android.browser.suggestion.SearchSugManager`（classes.dex）：

```
.field  mWebView:Lmiui/browser/webview/BrowserWebView;                      ← private instance
.method evaluateSugJS(String js) { mWebView.evaluateJavascript(js, null); } ← 宿主自己就在注入 JS
```

1. `initSugWebView` 之后反射取 `mWebView` 存档（字段名取不到就在继承链上按类型兜底扫）；
2. 每次 `BrowserWebView#queryKeyword` / `SearchSugManager#querySug` 之后，在主线程分
   **0 / 120 / 320 / 700 / 1400 / 2600ms** 六次向页面注入 `JS_FILTER`；
3. 脚本**第一件事就铺 CSS 规则**（`li.top-click:has(.ads-tag){display:none!important}`）——
   规则一进 DOM 就同帧生效，之后渲染出来的卡片在**首次绘制前**就被挡住，不经过 JS 回调 → **不闪**；
4. 再挂 `MutationObserver`，回调里**同步** apply（不 debounce）：microtask 在本帧绘制前清空，
   所以这一步也能赶在首次绘制之前 → 异步渲染的卡片同样不闪；
5. 隐藏方式一律 `display:none`（**只隐藏、不删 DOM**）；返回值（隐藏条数 / 标记数 / 命中路径 /
   CSS 是否生效 / observer 是否注册 / 节点总数 / href）通过动态代理实现 `ValueCallback` 回传到日志。

> **1.6.5 时序结论（20:48 真机日志）**：`sugCardApi` 37.396 发出 → 卡片图片 38.629 开始下载
> （= 卡片已上屏）→ 38.651 才第一次隐藏。**卡片可见约 1.25s**。原因是注入只有 3 次、
> 且 observer 回调里有 `setTimeout(150ms)` 防抖。CSS 前置 + 同步 observer 就是针对这两点。

**卡片定位规则（1.6.2 重写、1.6.4 加第二判据、1.6.5 加 CSS 前置、1.6.6 加空壳塌缩）**：

| 环节 | 做法 |
|---|---|
| **前置 · CSS 规则（1.6.5）** | 注入时先铺 `li.top-click:has(…)` 规则（`!important`），**首次绘制前就挡掉**，这是「不闪」的主力；`:has()` 不支持时自动失效，退化到 JS |
| 判据一 · 广告标记 | 自身直接文本恰为「广告/推广/赞助」（≤6 字）**或** class 含 `ads-tag`/`ad-tag`/`advert` |
| 判据二 · 安装按钮（1.6.4） | 卡片（`li.top-click`）内存在按钮：① 按钮特征（`BUTTON` 标签 / class 含 `btn`/`button`/`install`/`download`）+ 按钮词表 ② 自身文本恰为「安装 / 立即下载 / 去下载…」等强按钮文案；**排除「整卡文本 == 该词」**（否则搜「安装」会把那条联想词一起杀掉） |
| **空壳塌缩（1.6.6）** | 卡片藏了，外层 `ul.card__bd` / `div.card` / `div.topclick-wrap` 的 padding、边框和里面那行「查看更多」会露出空壳 → 容器内**≥1 张 `li.top-click` 且全部「将被隐藏」**时塌缩该容器；容器里重新出现可见卡片就立刻撤销（复活） |
| 定位卡片 | `closest()` 按 H5 的实际结构：标记 → `a.top-click__sug` → 外层 `li.top-click` |
| 兜底 | 上溯最多 4 层，遇根容器或列表容器即停 |
| 触发时机 | `MutationObserver` 回调里**同步** apply（microtask，赶在本帧绘制前）+ 6 次定时注入 |
| 护栏 | ① 绝不隐藏 `html`/`body`/`#app`；② 卡片本身不落在列表容器上（`card-list` / `card__bd` / `topclick-wrap` / `sug-search`）；③ 高度 > 视口 60% 的块不动；④ `data-hb-hidden` 幂等，处理过不再碰；⑤ **两条判据都不命中就一条不动**；⑥ 塌缩只认 `ul.card__bd` / `.card-list` / `.topclick-wrap` / class 含 `card` 的中间层，上溯 ≤3 层，且**容器内必须有卡**（否则会把还没渲染的列表整层藏掉） |
| 反面教材 | ❌ 别用「最近一个高度 ≥ N 的祖先」当卡片判据；❌ 别只看 `ads-tag`（宿主自推卡没有它）；❌ observer 回调里别加 debounce（这就是「闪一下」的来源）；❌ 塌缩容器别丢掉「容器内至少 1 张卡」这个前提 |

> ⚠ **1.6.1 的事故（真机验证）**：旧规则用「高度 ≥40px 的最近祖先」定位卡片。
> 祖先一旦被 `display:none` 高度就归零，`MutationObserver` 下一轮从标记往上**再爬一层**，
> 逐轮上爬 `… › ul.card__bd › .card.debug › #app.flex` → **整页隐藏，广告和搜索联想一起消失**。
> 「高度判据 + 反复执行」必然级联。1.6.2 改为按结构类名 `closest()` + 上述护栏。

**安全边界**：只隐藏不删除、不改宿主数据；两条判据都不命中就一条不动（最坏等于没生效）；
只对 `SearchSugManager` 那个 WebView 生效，不碰正常网页；脚本幂等，重复注入无累积。

> **1.6.3 scout 实测定案（`modules_2026-09-18T20_38_36`）**：页面稳定 **3 张推荐卡 / 2 个 `ads-tag`**，
> 每次都是 `m=0` 的那张没被藏 —— 宿主自推的应用卡不打广告标。1.6.4 用判据二补上。

> **1.6.6 用户实测**：卡片不闪了，但**偶尔还会剩一行小字「查看更多」，或者一条细长空白** ——
> 那是被藏卡片外面的空壳容器（`ul.card__bd` / `div.card` / `div.topclick-wrap`，
> 以及挂在 `div.card` 里的那行「查看更多」）。1.6.6 用 `collapse()` 把这类空壳一起塌掉，
> 并在返回串里新增 `fold`（塌缩了几个容器）和 `more`（「查看更多」入口的路径与最终可见性）两个读数。

#### 判读日志

| 日志（TAG `HookBrowser`） | 说明 |
|---|---|
| `已捕获搜索建议 WebView（…）` | 注入通道拿到了 |
| `【注入】{"hidden":N,"btn":B,"marks":M,…}` | `N` 按广告标隐藏条数、`B` 按安装按钮隐藏条数、`M` 命中标记数 |
| `【注入】…"css":1,"ob":true` | `css=1` = 前置 CSS 规则已生效（这是**不闪**的依据）；`ob=true` = observer 已注册。`css=0` 说明 WebView 不支持 `:has()`，会退化成纯 JS（可能仍有极短闪现） |
| `【注入】…"scout":[…]` | 每张 top-click 卡片项的摘要：`m=`有没有广告标、`b=`有没有安装按钮、后面是文本开头。**剩下一张卡没被藏时先看这个** |
| `【注入】…"bhits":[…]` | 按按钮判据命中的卡片路径（1.6.4），与 `hits`（广告标判据）分开看 |
| `【注入】…"fold":F` | **塌缩掉的空壳容器数**（1.6.6）。卡片全被藏后 `F≥1` 才正常；恒为 0 = 空壳还在（就是那行「查看更多」或细长空白） |
| `【注入】…"more":["…SPAN.more \"查看更多\" v=0"]` | 页面里「查看更多」类小字入口 + **最终可见性**（1.6.6）。`v=0` = 已不显示；`v=1` = 还有残留，按路径看它挂在哪一层 |
| `【诊断】remote preferences 已就绪（组=settings），初始 N 个开关：[…]` | **读到开关了**，`N>0` 时开关立刻有效（底层 1s 缓存，改完约 1 秒生效，不用重启宿主）。这行缺失或 `N=0` = 没读到 |
| `[E] getRemotePreferences(settings) 失败…` | 框架不支持远程首选项（嵌入式框架会抛 `UnsupportedOperationException`）。此时全部开关退化为默认值：默认 true 的广告类看起来「正常」，默认 false 的（`misc_unlock_pref` / `misc_debug` / `misc_security`）永远开不起来 |
| `【诊断】规则库：组 adrules 有 … 个 key，sets 长度=…` | 规则库通道通了。显示「组为空」= 模块进程还没导入过规则 |
| `【诊断】isVisible 评估到 key=… 宿主原本=false` | 宿主确实在评估这个 Preference 且原本判它不可见 → 开关一开就该出来 |
| `【诊断】采样满 12 个：… 其中 M 个宿主原本判不可见` | **`M=0` = 宿主没在用 `isVisible` 隐藏东西**，这条路径解不开你看不到的项；`M>0` 且开关已开仍看不到 = 宿主另有判据 |
| `【注入】{"hidden":0,"marks":0,…}` | 页面上没有广告标记 → 页面改版了，按 `【诊断】H5 请求` 走数据层 |
| hits 里带 `[未定位]` | 找到了标记但结构变了、定不到位 → 补 `CARDSEL` 选择器 |
| `未找到 WebView#evaluateJavascript` | WebView 类型不符预期，需要换注入方式 |
| `【诊断】H5 请求 <url> → …` | sug 页面请求清单（去重、封顶 40 条），数据层方案的入口 |

#### 历史尝试（全部无效，保留作记录）

> ⚠ **以下为 1.5.x 的原始设计记录，结论已被 1.6.x 推翻**，仅用于追溯「当时为什么这么写」。

#### ① 曾被当成主拦点：短路 `SugCardData#a()`（无效）

宿主 `SugCardData`（classes.dex）全文反汇编：

```java
static String SERVER_URL = <host> + "qsb/config/browser";

static void a() {                       // ← 拦截点，无参 static void
    lambda$updateSugCardData$0();       //   同步请求服务端配置
}
static void updateSugCardData() {       // 唯一入口，切后台线程执行
    Le/e;->y(new Lcom/android/browser/quicksearchbox/data/g;());
}
// lambda$updateSugCardData$0() 的落盘动作：
//   KvPrefs.Rb(display)                     ← 「搜索推荐卡片」显示开关
//   KvPrefs.Pb(expId)                       ← 实验号
//   KvPrefs.vb(queryRecommend.val != "0")   ← queryRecommend 开关
//   KvPrefs.Qb(false)                       ← h6() 为 false 时复位
```

**卡片出不出，是服务端通过 `qsb/config/browser` 下发的。** 在 before 阶段
`setResult(null)` 短路它，宿主就永远拿不到 `display=true`，KvPrefs 保持本地默认（关）。

原版回调就是这个动作：

```
const/4 v0, #0
setResult(v0)      // = setResult(null)，方法体不执行
```

#### ②③ 曾被当成兜底：`RecentAppManager`（无效）

短路 `a()` 有个盲区：**KvPrefs 里的 `display` 可能在模块生效之前就已经被写成了 `true`**，
此时短路只阻止「再次刷新」，不会把开关关回去。所以再补两道展示层过滤：

```
搜索框下拉 → http://qsb.browser.miui.com/qsb/getAppSuggest
  → RecentAppVersionData#updateRecentAppData()   落盘 JSON
  → RecentAppManager#initNetworkList()           解析进 mNetworkLists
  → RecentAppManager#initRecentAppList()         ★ 分流 + 固定插位
  → RecentAppManager#getRecentAppList()          UI 取数（上限 10 条）
```

`initRecentAppList()` 里的分段（反汇编实测）：

1. 遍历网络应用，`RecentApp#isAd()` 为真的一律从 `mOptionalAppList` 摘出、塞进 `mRecentAdAppList`；
2. **再把 `mRecentAdAppList` 的前两项插回 `mOptionalAppList` 的第 1、第 4 位** —— 固定广告位；
3. 从 `mOptionalAppList` 依次取满 10 条填进 `mRecentAppList`，广告项已经在里面了。

`RecentApp#isAd()` 的判定（classes9.dex）：

```java
return ads != null && !TextUtils.isEmpty(ads.getExt());
```

即服务端给该应用下发了 `ads.ext` 字段就算广告 —— 截图卡片上那行「广告」字样就是它。

拦法：`initRecentAppList()` 之后清空 `mRecentAdAppList` 并把 `isAd()` 为真的项从
`mRecentAppList` 移除；`getRecentAppList()` 之后（UI 取数出口）再过滤一遍。
只删 `isAd()` 为真的项，对正常应用零影响。

#### ④ `HotSearchManager#getHotSearchAdList()` 空表兜底（无效，但留着不亏）

宿主 20.27 里「广告热词」这条路径**已经是废代码**（数据源类 `HotSearchAdVersionData`
在全部 33 个 dex 的 class_defs 里都不存在，只剩悬空引用），留着不亏。

#### 排查

| 日志（TAG `HookBrowser`） | 含义 |
|---|---|
| `已挂载 ...SugCardData#a（1 个重载）` | hook 在位（不代表能拦广告） |
| `已短路 ...SugCardData#a（服务端搜索推荐配置不再下发）` | 每次搜索框初始化打一次，**对广告无影响** |
| `已从应用建议列表移除 N 条广告` | 从未出现 → 该链路里没有广告项 |
| `【诊断】...` | 1.6.0 新增，见上表；用它判 H5 / 原生层 |
| `未找到 ...` | 宿主改了类名/方法名，按 `docs/宿主20.27-hook目标核对.md` 重跑核对 |

> ⚠ 历史踩坑记录（都已在 1.5.1 / 1.5.2 / 1.6.0 处理）：
> - `SearchBasicVersionData#getAdParem()` 只返回 `isOpenPersonalizedRec` / `isPersonalizedAdEnabled`
>   两个个性化参数，**不含** `isAdvertisingEnabled`；
> - `ne.y#{b,e,h}` 是设备公共参数注入（udid/oaid/androidId/demei…），与广告无关；
> - `SugCardData#requestGameRecommend` 在宿主里不存在。
>
> 教训：**方法存在 ≠ 语义对**；**原版有 hook ≠ 原版有效**。
> `classmethods` 只能证明方法在不在，判断依据必须是反汇编看方法体。

> **下一步（1.6.0 之后）**：装 1.6.0 → 强停浏览器 → 点一次搜索框 → 抓日志看 `【诊断】getSugPageUse=`。
> - 非 1（走 H5）→ 卡片由远端页面渲染，Java hook 无解；要么按 `getSugUrl` 做 JS/CSS 注入，
>   要么收手把无效 hook 清理掉（保底）。
> - 等于 1（走原生）→ 说明卡片另有原生渲染路径，按 `BaseSuggestionView#onUpdate` 日志继续往上追。

</details>


<details>
<summary><b>与原版 base.apk 的差异</b></summary>

### 与原版 base.apk 的差异

| 项 | base.apk（原版） | 本工程 |
|---|---|---|
| 开关 | 无，全部无条件生效 | 1 个总开关 + 9 个功能开关 + 3 种 UA 模式 |
| 界面 | 一个 `TextView text="FUCK_MIUIBROWSER"` | Preference 设置页，分「去广告 / 界面精简 / 高级」三组 |
| 异常处理 | hook 失败 `throw new NoClassDefFoundError()` → **宿主崩溃** | 逐个功能 try/catch，失败只记日志 |
| 入口 | `PathClassLoader` 二次加载自己的 APK + 反射调用 | 继承 `XposedModule`（libxposed API 102） |
| 字符串 | StringFog（Base64 + XOR，密钥硬编码 `UTF-8`）全量加密 | 明文，可读可维护 |
| 控制流 | 平坦化展开成 20 层 `Lxposed$1000000NN` 嵌套类，含大量重复逻辑 | 一个功能一个文件 |
| 资源 | 混淆成 `r/a/a.png`、`r/b/a.xml`（伪 PNG） | 正常 `res/` 结构 |
| `debuggable` | `true` | 未开启 |
| 体积 | 100 KB（含 89 KB 混淆 dex） | 无混淆，主要体积来自 androidx |

</details>


<details>
<summary><b>交付前静态走查（tools/verify_static.py 覆盖了什么）</b></summary>

### 交付前静态走查

本机没有 JDK、不跑构建，所以改动后先跑这个：

```bash
python tools/verify_static.py     # 退出码 0 = 通过
```

覆盖 18 项：资源引用、开关 key 四层一致、默认值表一致、Manifest 资源、
**manifest 里不得残留 xposed\* legacy 声明**、**`META-INF/xposed/` 三份注册文件与入口类/宿主包名对齐**、
**全工程不得再出现旧 API（`de.robv`）的可执行引用**、`Features.ALL` 注册表完整、
**Kotlin 符号交叉检查**（防「改了调用点漏写实现」这类编译期才暴露的错）、
try/catch 双分支给同一个 `val` 赋值（`Val cannot be reassigned`）、
Manifest 声明的 Activity 有源码、**规则库组名两处一致**（跨进程的组名写错不会报错，
只会「规则导入了但永远不生效」，必须静态钉死）、**粗粒度语法体检**（剥掉注释/字符串后
kt 的 `() [] {}` 是否平衡 + **块注释嵌套深度**（Kotlin 块注释可嵌套：KDoc 表格里写
`` `/path/*` `` 就等于多开一层，编译器报的却是文件末尾的 `Unclosed comment`，
回不到真凶行）+ 每个 xml 能否被解析 —— 没有 JDK，这两类错以前只有到
Android Studio 里才暴露）。

改了注入脚本（`SearchSugFeature.JS_FILTER`）再跑一次它的回归测试 —— 那段 JS 是字符串，
编译器管不到，这个脚本会把它抠出来塞进 mock DOM（mock 高度会随 `display:none` 归零/塌缩，
所以能真复现级联）验证：

```bash
node tools/test_js_filter.js                          # 退出码 0 = 通过
HB_KT=<别的 kt 文件> node tools/test_js_filter.js     # 反向回归：换一份 JS 跑同一套断言
```

断言覆盖：广告卡被隐藏 / **同结构无标记的正常建议项不被误藏** / 容器一个都不许动 /
**连跑 5 轮仍不级联** / 无广告时 `hidden=0`。
用 1.6.1 的旧 JS 跑会**退出码 1** 并报出「容器 ul 被隐藏（级联回归）」—— 测试不是空跑。

</details>


<details>
<summary><b>加新功能的姿势（贡献代码看这节）</b></summary>

### 加新功能的姿势

1. `features/` 下新建 `XxxFeature : Feature(Config.XXX)`，在 `install()` 里调 `Hooks.hook(...)`，
   回调第一行写 `if (on()) ...`。
2. `Config.kt` 加 key 常量 + 默认值。
3. `res/xml/prefs.xml` 加一条 `<com.hupan.hookbrowser.ui.SwitchRowPreference>`（开关行，
   `android:layout="@layout/mx_pref_switch"`；工具的正则认这个类名）。
4. `res/values/strings.xml` 加标题与说明；`ui/FeatureDetails.kt` 补一条详情
   （不补也能跑，只是点行不弹详情）。
5. `Features.ALL` 里注册。
6. `versionCode` / `versionName` 递增，`CHANGELOG.md` 补条目。

四处的 key 字符串必须完全一致，否则开关不生效。

</details>

---

## 原作者与致谢

> ### 🧑💻 原作者：Jun_ao ｜ 📢 QQ 群：**1094704567**

| 项 | 内容 |
|---|---|
| 原作者 | **Jun_ao** |
| 原模块名 | `干掉小米浏览器广告` |
| 原模块标识 | `干掉小米浏览器广告    Q群1094704567    By Jun_ao` |
| 原版版本 | v1.2.0（versionCode 2），产物 `base.apk` |
| 原作者 QQ 群 | **`1094704567`** |

**本项目的全部功能，源头都是原作者的工作。** 本工程做的事只有两件：

1. 把这个 `base.apk` 反编译还原成可读、可维护的源码工程；
2. 把原先**写死在代码里**的去广告逻辑，改造成「**每组功能一个开关 + 一个设置界面**」，
   并补齐 Adblock 规则导入、详情说明、状态自检等外围能力。

功能思路、hook 目标、去广告手法 —— **均来自原作者**，本工程没有发明新的破解路径。

### 请怎么对待这个群号

- ✅ **加群**：找原版模块、反馈原模块的问题、或单纯支持一下原作者。
- ❌ **别去打扰原作者**：本重写版新增的 bug、构建失败、开关不生效之类的问题，
  请提到[本仓库 Issues](https://github.com/hupanxiaozhu/XiaomiBrowserTuner/issues)，
  原模块作者没有义务为本工程的改动负责。
- ⚠️ 本工程**不是**原作者的官方后续版本，也**未获原作者授权或背书**；
  如原作者对分发方式有异议，请联系本仓库删除。

去广告思路的完整来源分析见 [`docs/base.apk-分析报告.md`](docs/base.apk-分析报告.md)。

---

## 许可与免责
**本项目不是开源项目。** 完整条款见 [`LICENSE`](LICENSE)，要点：

- ✅ 允许：阅读代码、为个人学习修改、在自己设备上编译自用。
- ❌ 禁止：商业使用、再分发编译产物（APK）、上传到任何应用商店或模块仓库、移除版权标识。
- ⚠️ 本项目目标是**小米浏览器（`com.android.browser`）**，与小米公司**无任何关联**，未获其授权、赞助或认可；「小米」「MIUI」「HyperOS」等商标归小米科技有限责任公司所有。
- ⚠️ 本项目按「现状」提供，不附带任何担保。修改宿主运行时行为可能影响宿主稳定性，请先备份再试。
- 📄 本项目不含宿主应用的任何原始代码或资源；宿主相关结论均来自对公开安装包的静态分析，记录在 `docs/`。
