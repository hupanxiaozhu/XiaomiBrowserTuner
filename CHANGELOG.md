# Changelog

## 1.11.0 (versionCode 34) —— 设置界面重建为「三 Tab + 二级页」（Compose + miuix）

### 改了什么

界面整体重建。**功能、开关键、hook 逻辑、规则引擎一行未动**，只动界面层与构建配置。

| 类别 | 之前（1.10.3） | 现在（1.11.0） |
|---|---|---|
| 技术栈 | `PreferenceFragmentCompat` + 自绘 `SwitchRowPreference` + XML 版式 | Compose + miuix 0.9.3 + navigation3（与番茄 HookFanqie 1.2.0 同架构） |
| 页面结构 | 底部导航两栏（功能 / 关于），关于页把信息表与更新日志全铺开 | 三 Tab（功能 / 规则 / 关于）+ 横向分页；项目信息 / 更新日志 / 诊断 / 应用设置四个二级页走真正的返回栈 |
| 一级页 | 关于页两屏多 | 只放开关 + 入口，基本一屏看完（长内容全部下沉） |
| 详情弹窗 | `MaterialAlertDialogBuilder` + `dlg_feature_detail.xml` | 自绘 Box 叠层（切 Tab / 进二级页回来状态一致；切换后不关窗） |
| 下拉项 | `ListPreference`（UA 模式 / 搜索引擎） | 卡片行 + 自绘单选浮层，跟随母开关置灰 |
| 开关数据 | `res/xml/prefs.xml` + `ui/FeatureDetails.kt` + `ui/Changelogs.kt` 三处 | 合到 `ui/FeatureCatalog.kt`（目录 / 详情 / 分区 / 更新日志 / 项目信息） |
| 规则管理 | 功能页里的一个入口项 | 独立「规则」Tab：两个开关 + 规则库概况卡 + 入口（页面本身仍是 View 实现） |
| Kotlin | AGP 9 内置 2.2.10 | 覆盖到 2.4.20（miuix 0.9.3 由 Kotlin 2.4.0 编译、依赖 compose foundation 1.11.1） |

### 实现要点

- 新增 `ui/{MainActivity,DesignTokens,FeatureCatalog,SettingsPrefs}.kt`、
  `ui/{theme,navigation,utils,component}/`、`ui/page/{MainPage,MainTabPolicy,ToggleState}`
  与 `ui/page/{features,rules,about}/`、四个二级页。
- 每个 Tab 自带 `Scaffold + TopAppBar`；页面左右边距与「顶栏 + 底栏」安全区统一由
  `ui/utils/Page.kt` 的 `pageContentPadding` 给（卡片是 `fillMaxWidth()`，不给就贴屏边）。
- 开关状态提到 `ToggleState` 单点持有：`SnapshotStateMap` + `SettingsPrefs`（本地 SP 是唯一真源，
  每次写入后经 `ModuleService` 全量镜像到框架数据库）。设置页「恢复默认」后返回主界面立刻同步。
- ⛔ 三个浮层（详情 / 单选 / 风险确认）全部自绘：**不用** miuix 的 `WindowDialog` —— 它走
  navigationevent 的预测性返回，要求宿主提供 `NavigationEventDispatcherOwner`，模块自己的
  Activity 没有，一点就崩。
- ⛔ `miuix-blur` 没加（AAR 声明 minSdk 33，本模块 minSdk 26，加了 manifest 合并直接失败）。
- 删除：`SettingsActivity` / `PrefsFragment` / `AboutFragment` / `SwitchRowPreference` /
  `CardRowDecoration` / `FeatureDetails` / `Changelogs`、`res/xml/prefs.xml`、
  `res/menu`、`res/color`、七个旧 layout、`arrays.xml` 与一批不再引用的 drawable / string。
  `res/values/design_tokens.xml` 保留（三工程对账脚本仍读它）；`themes.xml` 只剩主题本身。
- `tools/verify_static.py` 跟着改：开关 key 对齐的真源从 `prefs.xml` 换成 `ui/FeatureCatalog.kt`
  （新增「分区表是否漏登记」「详情是否漏写」「选项表常量是否存在」三项），
  符号交叉检查补上顶层 `val` / 扩展函数（否则新增的 import 全被误判成无法解析）。

### 验证

`tools/verify_static.py` 通过（13 项开关 key / 分区表 / 详情 / 资源 / 括号平衡全部对齐）。
`:app:assembleRelease` 编译通过，产物 `versionCode=34` / `versionName=1.11.0`（minSdk 26 / target 34）。
首次同步需下载 Compose 编译器 2.4.20 与 miuix / navigation3 依赖。

## 1.10.3 (versionCode 33) —— 三工程统一设计规范 v2（纯样式层）

### 改了什么

与多看调谐器 1.26、番茄 HookFanqie 7.2.31 共用同一套设计令牌。页面结构、Preference 树、
开关键、跨进程写端全部未改，只动样式层。

| 类别 | 之前（1.10.2） | 现在（1.10.3） |
|---|---|---|
| 设计令牌 | 数值散落在 style / layout / Kotlin 常量里 | 新增 `res/values/design_tokens.xml`（`ds_*` 间距 / 尺寸 / 圆角 / 胶囊 / 字号 / 行距 / 动效时长 / 阴影），三工程同名同值 |
| 大屏 | 固定 16dp 页边距 | `values-sw600dp` 96dp、`values-sw720dp` 160dp，正文最大宽度 640dp |
| 行距 | 只给 fontSize，靠字体默认行高 | 正文 19sp、标签 17sp、脚注 16sp、等宽 18sp（与另两工程一致） |
| 色 | 缺 `field` / `outline` / `arrow` / `danger` / 主色上的涟漪色 | 补 `mx_field` `mx_outline` `mx_arrow` `mx_danger` `mx_ripple_on_accent`，明暗各一套 |
| 图标 | 16dp 视口、2.2 描边箭头；功能页 Tab 是实心 | 24dp 视口、1.9 描边、圆头圆角（`ic_mx_arrow` / `ic_mx_tab_features`） |
| 阴影 | 底栏 10dp | 卡片 0 / 底栏 8dp / 弹窗 16dp（`ds_elevation_*`） |
| 组件样式 | 每个 layout 各写各的属性 | `themes.xml` 里集中成 `MxText*` `MxCard*` `MxRow*` `MxSectionHeader` `MxCapsuleTag*` `MxNavIndicator` `MxDetail*` `MxInfo*`，全部引用 `@dimen/ds_*` |

### 实现要点

- 新增 `res/values/design_tokens.xml` + `values-sw600dp/` + `values-sw720dp/`；
  颜色仍沿用本工程的 `mx_*` 前缀（只补缺，不重命名），色值表见规范文档。
- `ui/CardRowDecoration.kt` 的圆角 / 行距 / 分区落差改从资源读；`ui/PrefsFragment.kt`
  的底栏高度、胶囊动效时长与圆角不再写 Kotlin 常量。
- 布局里的裸数值全部换成 `@dimen/ds_*`（`dlg_feature_detail.xml` 里重复的
  `layout_width` 一并清掉，之前是 mergeDebugResources 直接报错）。
- 单一真源：`三工程统一设计规范 v2.md`（本地设计规范库）；对账脚本
  `sync_check.py`（三边 154 项令牌/色值一致性，exit 0 = 通过）。

### 验证

`./gradlew :app:assembleDebug` 与 `:app:assembleRelease` 均通过；观感验证步骤见规范文档末节。

## 1.10.2 (versionCode 32) —— 设置页改「一条一卡」，关于页 hero 改版

### 改了什么

纯界面版式改版，功能、开关键、跨进程通道均未改动。

| 位置 | 之前（1.10.1） | 现在（1.10.2） |
|---|---|---|
| 功能页列表 | 一组一张卡（组内用细分隔线拼成一整张） | **一条一卡**：每个开关一张 20dp 圆角独立卡片，行间 10dp 缝隙 |
| 卡片内边距 | 行高 62dp、标题 14sp / 副文案 11sp | 18 / 16dp、标题 16sp 粗体 / 副文案 13sp |
| 分区标题 | 12sp 粗体、与卡片正文对齐 | 14sp 次要色、比卡片正文更靠左 8dp |
| 页面大标题 | 21sp / 副标题 12sp | 30sp / 副标题 13sp |
| 关于页 | 图标卡（白底居中）+ 简介卡 + 更新日志（一张卡里多段） | **hero 主卡**（主色 12% 底 + 22sp 粗体模块名 + 实心主色版本胶囊 + 定位句）+ 简介卡 + 「项目信息」独立小卡 + 「更新日志」一版一卡 |
| 更新日志条目 | 版本号（含 versionCode）一行 + 要点 | 版本号 16sp 粗体 + versionCode 小字 + 「当前 / 历史」tag 胶囊 + 要点列表 |
| 行水波纹 | 方形（在卡片圆角外溢出成方角） | `bg_mx_row_ripple`：mask 裁到 20dp 圆角 |

### 实现要点

- `ui/CardGroupDecoration.kt` → **`ui/CardRowDecoration.kt`**：不再按「分组」拼接卡片，
  而是给每一行画一张完整的 20dp 圆角卡片，`PreferenceCategory` 只当分区标题（不画卡、
  上方多留 22dp 落差）。旧文件已删除。
- 新增资源：`bg_mx_hero`（hero 底）、`bg_mx_pill_solid`（hero 里的实心版本胶囊，hero 底本身就是
  主色淡底，再用淡底胶囊会看不见）、`bg_mx_row_ripple`；新增颜色 `mx_ripple` `mx_on_accent`；
  新增 style `MxSectionHeader` / `MxInfoLabel` / `MxInfoValue`。
- 关于页结构由 XML 承担（`view_about.xml`），`AboutFragment` 只做三件事：填运行时版本号、
  填框架服务状态、按 `Changelogs.ALL` 生成「一版一卡」（首条挂「当前」tag）。

### 为什么改

三个兄弟工程（HookFanqie / DuokanTuner / HookBrowser）从 1.9.6 起共用同一套设计令牌，
但番茄 HookFanqie 7.2.30 把设置页改成了「一条一卡 + hero 关于页」的版式，这里跟进对齐 ——
三边观感一致，改一处令牌三边一起改（见 `docs/胶囊特效统一规范.md`）。

---

## 1.10.1 (versionCode 31) —— 补上写入端：开关真的能同步到宿主了

### 症状

1.10.0 装上后不再闪退、功能也注册了，但**改的开关不生效**。真机日志（TAG `HookBrowser`）：

```text
模块已加载（API 102，框架 LSPosed 2.2.0）
读取开关失败，回退到默认值（misc_debug=false）
【规则】规则库为空，未导入或宿主读不到：组 adrules 为空（模块进程还没写过规则库）
```

### 根因：只迁了一半

1.10.0 把**读取端**迁到了 `XposedInterface#getRemotePreferences` —— 它读的是**框架数据库**。
但**写入端**没动：模块 App 改开关时仍然写自己私有的 `shared_prefs/`。两端根本不是同一份数据。

关键判读是那句「**组 adrules 为空**」而不是「拿不到组」—— 句柄建起来了，
只是从来没人往里写过。所以这不是权限问题，也不是路径问题，是**压根没写**。

### 修法：libxposed service

写入端只能走框架服务：

1. manifest 声明 `io.github.libxposed.service.XposedProvider`，authorities 必须是
   `${applicationId}.XposedService`（框架按这个约定来找，写错同样是静默失效）；
2. 框架通过它把 `IXposedService` 的 binder 送进模块进程，`XposedServiceHelper` 接收；
3. 拿到 `XposedService` 后 `getRemotePreferences(group)` 取到**同一个**远端组，写入即生效；
4. `commit()` 是一次同步 Binder 调用（`updateRemotePreferences`），不需要额外等待。

新增 `ModuleService` 承担这一层，并把本地 SP **全量镜像**过去（覆盖式：先 clear 再逐条 put ——
本地是唯一真源，增量会累积出「本地删了、远端还在」的幽灵 key）。
服务还没连上时组名记进待推队列，连上后自动补推。

触发点：`SettingsActivity.onCreate` 连一次；`PrefsFragment` 监听本地 SP 变更后防抖 300ms 推；
`AdRuleStore.save` 写完推。

### 附带

- 「关于」页状态行改为显示框架服务的连接状态（≤1.9.9 显示的「开关文件权限」在数据库通道下已无意义）。
- `minSdk` 23 → 26：service 库要求 Android 8.0 起。
- 静态走查新增一项：manifest 必须声明 `XposedProvider` 且 authorities 以 `.XposedService` 结尾。

---

## 1.10.0 (versionCode 30) —— 迁移到 libxposed API 102

### 起因

1.9.8 为了消掉 LSPosed 的废弃警告，把 `xposedminversion` 从 93 降到 82 并删掉了
`xposedsharedprefs`（这两步确实是官方给出的消除方式）。但**副作用没被评估到**：

- 不再重定向后，模块的 SP 落到 `/data/user_de/0/<pkg>/shared_prefs/`；
- 该目录是 **uid 私有**，宿主进程（另一个 uid）`父目录[读=false 进入=false]` ——
  **连目录都进不去**，chmod 文件本身毫无意义；
- LSPosed 停用重定向后，原来那份 `/data/misc/apexdata/<uuid>/prefs/` 也不再更新，只剩旧值。

真机表现：**基本功能正常，但改的开关全部不生效**（每次注入都打
`读取开关失败，回退到默认值`）。默认 true 的广告类开关看起来「正常」，
默认 false 的 `misc_unlock_pref` / `misc_debug` / `misc_security` 永远开不起来。

1.9.9 曾试图「由模块进程把 XML 镜像到 `/data/local/tmp`」，但前提不成立：
`fix()` 跑在模块自己的进程（普通 app uid），**根本写不了**该目录。

### 这一版做了什么

**换掉整条跨进程通道，而不是继续修权限。**

| 版本 | 通道 | 结果 |
|---|---|---|
| ≤1.6.5 | `XSharedPreferences` | 异步加载 + 权限 0660 → 宿主导出空表 |
| 1.6.6 / 1.6.7 | 自己扫路径 + 直读 XML | 同步可靠，但靠 `chmod o+r` 硬撑 |
| 1.9.8 | 删 `XSharedPreferences` 消警告 | **功能全失效**（见上） |
| 1.9.9 | 镜像到 `/data/local/tmp` | 前提不成立，机制作废 |
| **1.10.0** | **`XposedInterface#getRemotePreferences`** | 存框架数据库，跨进程直读，**零权限 hack** |

### 具体改动

**构建与声明**

- `compileOnly(files("libs/api-82.jar"))` → `compileOnly("io.github.libxposed:api:102.0.0")`；
  删除 `app/libs/api-82.jar`（API 102 明确**禁止**调用旧 `de.robv.android.xposed` API）。
- 模块身份从 manifest meta-data 迁到 `META-INF/xposed/`：
  `module.prop`（minApiVersion=101 / targetApiVersion=102 / staticScope=true）、
  `java_init.list`、`scope.list`。删除 `assets/xposed_init` 与
  `xposedmodule` / `xposedminversion` / `xposedscope` meta-data、`@array/xposed_scope`。
- `proguard-rules.pro` 改为保留 `io.github.libxposed.api.XposedModule` 子类与入口类。

**入口与接口层**

- `MainHook`：`IXposedHookLoadPackage` → 继承 `XposedModule`，
  覆写 `onModuleLoaded`（把 `XposedInterface` 交给 `Hooks` / `Config` / `Log`）
  与 `onPackageLoaded`（原 `handleLoadPackage` 的功能注册逻辑原样保留）。
- `Hooks`：`XposedBridge.hookAllMethods` → `x.hook(Executable).intercept(Hooker)`。
  **对 features 保持旧的 `param` 外观**：适配器构造 `HookedCall`（`thisObject` / `args` /
  `result` / `method`），before 阶段若 `result` 被赋值就直接返回（= 旧的「阻断」），
  否则 `proceed()` 后把真实返回值喂给 after 回调。
  `result` 的 setter 会同步置位 `hasResult` —— 否则 `p.result = x` 这种写法会被静默忽略。
- 新增 `Xp`：自带反射层（`findClass` / `field` / `setField` / `call` / `callStatic` /
  `newInstance`），取代不再提供的 `XposedHelpers`。失败只记日志、绝不外抛。
- `XLog`：`XposedBridge.log` → `XposedInterface#log(priority, tag, msg)`（框架未就绪时退回 logcat）。

**开关读取**

- `Config` 重写：`getRemotePreferences("settings")` 取 `SharedPreferences`，
  句柄按组名缓存（一次跨进程往返），加 1 秒 TTL 值缓存。
  **删除** `candidateFiles()` / `parsePrefsXml` / `describe` / `fileMode` / `selfCheck`
  整套文件扫描与 XML 正则解析。
- **删除 `PrefsFileAccess.kt`**（整个文件权限机制）；`AdRuleStore.save` 不再 chmod；
  `PrefsFragment` 不再在 SP 写入后延迟修权限；`AboutFragment` 不再显示权限串。
- `AdRuleChannel` 重写：规则库改从 `getRemotePreferences("adrules")` 读，
  删除 `/data/misc/apexdata` 逐层扫描与 XML 反转义。

**文档与工具**

- `tools/verify_static.py` 升级到 18 项：新增「manifest 不得残留 xposed* legacy 声明」、
  「META-INF/xposed 三份注册文件与入口类/宿主包名对齐」、
  「全工程不得出现旧 API 的可执行引用」（注释里的历史说明不计）。
  原第 5 项（`assets/xposed_init` 对齐）与第 10 项（规则库名三处一致）随之调整为两处。
- README 的版本、依赖、目录树、判读日志全部同步。

### 需要用户验证

- API 102 需要 **LSPosed 2.x**；真机上确认 `getRemotePreferences` 能读到模块写的开关
  （日志里应有 `【诊断】remote preferences 已就绪（组=settings），初始 N 个开关：[…]`，
  `N>0` 即通）。
- 作用域由 `scope.list` 声明且 `staticScope=true`，LSPosed 的作用域界面里是**只读**的，
  不要按 1.9.x 的习惯去那里勾选。
- 规则库（`adrules` 组）如有已导入的规则，升级后需确认仍能读到；
  通道换了，但组名与 key 未变（`adrules` / `sets`）。

---

## 1.9.8 (versionCode 29) —— 移除 LSPosed 废弃项：XSharedPreferences

> ⚠ **本版引入缺陷（已由 1.10.0 修复）**：去掉框架重定向后，模块 SP 落进
> uid 私有目录 `/data/user_de/0/<pkg>/shared_prefs/`，宿主进程**连父目录都进不去**，
> 导致所有开关读不到（默认 false 的开关永远开不起来）。
> 教训：消除废弃警告时，必须同时确认**被删掉的那条通道原本承担了什么职责**。

LSPosed 模块页给本模块打了「使用了已废弃且即将移除的功能」标记。根因在 LSPosed 2.2.0 的改动：

> New XSharedPreferences is scheduled for official removal in version 2.3.0.
> Deprecation warnings have been added to the module page for modules at risk of
> compatibility issues. The probe logic identifies legacy modules that declare
> support for `xposedminversion` **and** contain an XML file with others readable
> permission. **To eliminate the warning, upgrade to libxposed or set
> `xposedminversion` to 82 and remove `xposedsharedprefs`.**

本模块**两条判定条件全中**：

| 条件 | 本模块 |
|---|---|
| `xposedminversion >= 93` | `93` |
| 存在 other 可读的 prefs XML | `xposedsharedprefs=true` + `PrefsFileAccess` 主动放开 other 读权限 |

### 修复（官方给的两步，改动最小）

1. `AndroidManifest.xml`：`xposedminversion` **93 → 82**。
2. `AndroidManifest.xml`：删除 `xposedsharedprefs` meta-data。

### 这两个东西本来就没在起作用

模块从 1.6.6/1.6.7 起就把读取链路换成了「自己扫路径 + 自己解析 XML」：

- `Config.raw()` 原先就是「直读结果优先，`XSharedPreferences` 兜底」，兜底那条在真机上
  从来没给出过有效读数（异步加载 + 权限不足）。
- `XSharedPreferences.makeWorldReadable()` 的调用被 `runCatching` 包着，本身就不依赖它成功。
- 权限放开真正靠的是模块进程的 `PrefsFileAccess.fix()`（chmod 文件本身 + 逐级父目录）。

所以本次删除的是**纯兜底代码**，功能行为不变。

### 路径变化（已覆盖，无需额外处理）

去掉 `xposedsharedprefs` 后 LSPosed 不再重定向模块 SP，文件从
`/data/misc/apexdata/<uuid>/prefs/<pkg>/` 落回 `/data/user_de/0/<pkg>/shared_prefs/`。
`Config.candidateFiles()`、`PrefsFileAccess.locateAll()`、`AdRuleChannel` 的扫描列表**都已包含该根**
（三个文件都显式枚举了 `/data/user_de/0/<pkg>/shared_prefs/`），因此读写链路不受影响。

### 顺带清理

- `Config.kt` 删除 `XSharedPreferences` 字段、`.makeWorldReadable()`/`.reload()` 调用、
  异步读数诊断线程；`raw()` 简化为纯直读。
- `AdRuleChannel.kt` / `PrefsFileAccess.kt` 的注释改为反映新路径。

### 未采纳的方案

「迁移到 libxposed API 101」改动面大（入口类、`META-INF/xposed/module.prop`、依赖、构建脚本全动），
且旧版 LSPosed 1.9.x 不再兼容。当前方案已能消除警告且零功能风险，故不采纳。

## 1.9.7 (versionCode 28) —— 错误页去广告：摘掉「热搜榜」

「无法访问」错误页底部挂一条**「热搜榜」**，点进去跳的是**大米搜索（DJY）** —— 跟用户要访问的
站点毫无关系，是一条纯推广榜单。本版把它摘掉。

### 错误页是混合页，不是普通网页

起初按老思路在 Java 侧找「渲染热搜榜的列表」，一无所获 —— 因为错误页真正的样子是：

```
native chromium 读 res/raw/miuichromium_error_page.html（119,682 字节，单行无换行）
  → 交给 WebView 渲染
  → 页面 JS 通过 JS 桥反向问宿主要数据
```

桥的出口是 `HybridActionDispatcher#call(String): String`（`send` 只是转调它），
在构造时以 `MiHybrid` 的名字挂成 JavaScriptInterface。JS 侧调用形式：

```js
window.MiHybrid.send("nativechannel://getDefaultPageInfo")
```

URL 的 **host 就是 action 名**，宿主按名去 `HybridActionInjector` 的注册表（`abi.smali` 里共
**166 条**）取实现类，反射 `newInstance` 后调 `dealAction(bean, webView)` —— **返回值就是 JS 拿到
的字符串**。（四个目标 action 都直接继承 `Object` 并实现 `IAction`，不是 `HybridAction` 子类，
所以在 `call()` 里走的是「当前线程直接 dealAction」那一支，而不是 `mMainHandler.post(...)` 那一支。）

### 热搜榜的唯一开关是一个布尔

模板 JS 里逐字核出来的判据：

```js
e = JSON.parse(o.excuteClientAction("getDefaultPageInfo"));
d = e.searchEnabled;                       // 搜索总开关
_ = e.isHotSearchAddImport || !1;          // ★ 热搜榜的唯一开关
if (!d) return 0;                          // 搜索整体关 → 连「重新搜索该网页」按钮都不加
0 === e.sceneType && e.realTimeHotSpotSwitch && ((_ ? c : n)(), E = 0);
1 === e.sceneType && e.guessYouWantSwitch     && ( 猜你想搜, E = 1, 空则回落到 (_ ? c : n)() );
```

渲染总条件 `d && ["a","c"].includes(type) && 0 < l.length`，三个分支：

| `_` | `E` | 渲染出来的是 |
| --- | --- | --- |
| `true` | 任意 | `hot_search_list` / 标题**「热搜榜」** / `newJump` → 跳大米搜索 |
| `false` | `0` | `search_result` / 标题「搜索发现」 / `jump` |
| `false` | `1` | `want_search` / 标题「猜你想搜」 / `jump` |

`_` 来自宿主 `GetDefaultPageInfoAction#dealAction` 里的 `KvPrefs.u5()`。**把它变成 false，
热搜榜就永远进不去真分支**，而「搜索发现」「猜你想搜」这两条真搜索建议刚好跑得到。

### 实现：改返回值，不拦调用

新增 `features/ErrorPageFeature.kt`，只挂 `HybridActionDispatcher#call(String)` 的 **after**，
在返回值字符串上把 `isHotSearchAddImport` 从 `true` / `1` / `!0` 改成假值。

三个考虑：

1. **不拦调用本身** —— 拦下来会让整个错误页拿不到开关数据。只改一个布尔，错误提示文案、
   `switchOn` 布局状态、以及 `refresh` / `check` / `localCheck` / `clean` / `detail` /
   `research` / `inKidsMode` 这些按钮全都不受影响。
2. **字符串替换而不是 `JSONObject` 重排** —— 解析失败只是「这次没拦到」，不会把整份数据变成
   `{}` 让页面白掉；也不用担心中间多包一层泛型。
3. **不用 `ErrorPageResource#getLoadErrorPageContent()` 改模板** —— 那是 static 方法，hook 得到，
   但 119 KB 单行模板一换版正则就静默失效，改错了直接白页。
   （备选方案留在 `docs/宿主20.27-hook目标核对.md` §3.5：二进制 patch 模板里
   `excuteClientAction:t}}()` 这个**全文件唯一**的锚点。）

开关 `ad_error_page_hot`，**默认开**。

### 顺手排查了错误页以外的面

把 `HybridActionAutoInjector_Browser#init()` 的 166 条注册表全量导出，`ad/` 与 `commerce/`
包下一共 6 条广告 action：

| action | 类 | 行为 |
| --- | --- | --- |
| `drawAdBanner` | `...ad.DrawAdBannerAction` | `WebAdManagerFactory.create(NovelBannerManager, WebView)` → `setData` + `start()` |
| `shortVideoADShow` | `...ad.ShortVideoADShowAction` | 发本地广播 `browser.action.js_action` / `…short.video.ad` |
| `openTextAnchorAd` | `...ad.OpenTextAnchorAdAction` | 发 RxBus 事件 `miuix…m1(link, docId)` |
| `openLandingPage` | `...commerce.OpenLandingPageAction` | 打开落地页 |
| `trackAdEvent` | `...commerce.TrackAdEventAction` | 广告埋点上报 |
| `getOuterAdData` | `...commerce.GetOuterAdDataAction` | 回 `OuterAdDataHolder.getItemVoListJson()` |

**错误页模板一条都没调**（模板里 `getDefaultPageInfo` / `getHotSearchData` /
`getNewHotSearchData` / `getGuessYouWantToSearchData` 各出现 1 次），它们服务于小说页 /
信息流等其它文档流页面 —— 不在本版改动范围内，已记录在文档里备查。

## 1.9.6 (versionCode 27) —— 统一三工程胶囊特效 + 详情弹窗原地留驻

多看 / 番茄 / 小米浏览器三个模块工程各自演化出了一套「胶囊」，形状与参数互不相同：
浏览器这边是 `bg_mx_pill.xml` 里写死的 `100dp` 圆角、卡片 `14dp`；多看是 `16f` 圆角、卡片 `20dp`；
番茄的版本标签干脆是个 `8dp` 圆角矩形（根本不是胶囊）。同时三边的配色令牌虽然名字相近，值却有出入。

本版以**多看 DuokanTuner 的令牌为基准**，把胶囊抽象成一份跨工程规范，三边逐值对齐。

### 胶囊形状：stadium（圆角 = 高度 / 2）

原先的 `100dp` 是靠「圆角远大于高度、由渲染层收敛」凑出来的全圆 —— 能work，但**不可读**：
看代码不知道最终圆角是多少，改高度时也没人知道要同步改这个数。现在按高度明算：

| 用途 | 高度 | 圆角 | 参数来源 |
| --- | --- | --- | --- |
| 标签胶囊（状态 / 版本 / 更新日志 tag） | 22dp | **11dp** | `bg_mx_pill.xml` / `bg_mx_pill_off.xml` |
| 动作胶囊（页头按钮，浏览器无此物） | 32dp | 16dp | 多看 `Ui.chip` |
| 底栏导航指示器 | 由 M3 给（64×32dp） | 16dp | `MxNavIndicator` 只改底色 |

代码里需要现画胶囊时（颜色过渡动画逐帧重画），圆角走 `CAPSULE_TAG_RADIUS_DP`，
与 XML 里那两个 drawable 必须一致，否则过渡到一半会看到圆角跳变。

### 顺手修掉一个误用

`dlg_feature_detail.xml` 的「注意事项」那一块用的是 `bg_mx_pill` —— 那是个由多行文字撑高
的**块**，套一个 `100dp` 圆角的胶囊底，渲染出来是个圆角矩形，属于把胶囊当块用。
新增 `bg_mx_block.xml`（12dp，与多看 `Ui.RADIUS_FIELD`、番茄的注意事项块同值）换掉它。

### 状态胶囊：切换时要有过渡

原先点「切换」是直接 `setBackground` 换 drawable —— 硬跳变。多看那边是自绘的
`ValueAnimator` + `ArgbEvaluator`（220ms），番茄是 `animateFloatAsState` + `lerp`（220ms）。
这里补上同参数的 `ValueAnimator`，起始色固定取「另一态」的颜色（胶囊只有开/关两态，
从对态插过来就是正确的前一帧，因此不需要给 View 挂状态）。

两个坑：

1. **`render(false)` 的两个场合**：刚打开弹窗、以及外部改了开关之后重新对齐 —— 这两种情况
   界面值已经是终态，再插值就是闪烁。用 `lastOn` 记录上一次真正渲染出去的状态，
   只有状态确实翻转时才放动画（顺带解决了「点切换但被风险确认框拦下、开关没动」也会闪的问题）。
2. **自己发起的 SP 写入要挡掉外部同步**：`SharedPreferencesImpl` 在主线程 `apply()` 时是
   **同步**回调监听器的，而我们监听着 SP 变化来同步胶囊 —— 不挡的话，刚起步的过渡动画会在
   同一帧被拍回终态，等于没动画。用 `suppressingExternalSync` 在 `applyToggle` 前后一开一关。

### 详情弹窗「切换」不再关窗

原先是 `setPositiveButton(label) { applyToggle(pref) }` —— 点一下弹窗就没了，想看效果得重新点开。
现在自己接管 positive button（`setOnShowListener` + 手动 `setOnClickListener`），切完**留在原地**
刷新胶囊和按钮文案。这与多看 `Ui.detailDialog`、番茄 `SettingsScreen.kt` 里 `WindowDialog` + `update(...)` 的交互一致：
三边都是「点一下看效果，不满意接着点」。

`OnDismissListener` 里把 `externalSync` 清掉，避免弹窗关了还持有已回收的 View。

### 配色令牌逐值对齐

以多看 `@color/miuix_*` 为基准（`values` / `values-night` 两套）：

| 令牌 | 旧值（浅 / 深） | 新值（浅 / 深） |
| --- | --- | --- |
| `mx_bg` | `#F2F3F5` / `#000000` | `#F2F2F7` / `#000000` |
| `mx_text_primary` | `#E6000000` / `#E6FFFFFF` | `#191919` / `#FFFFFF` |
| `mx_text_secondary` | `#99000000` / `#99FFFFFF` | `#858585` / `#999999` |
| `mx_accent` | `#3482FF` / `#3D8BFF` | `#3482FF` / `#4C8DFF` |
| `mx_accent_soft` | `#1F3482FF` / `#333D8BFF` | `#1F3482FF` / `#334C8DFF` |
| `mx_divider` | `#0F000000` / `#14FFFFFF` | `#15000000` / `#1AFFFFFF` |
| `mx_track_off` | `#26000000` / `#33FFFFFF` | `#E5E5E5` / `#3A3A3C` |

`mx_track_off` 是变化最大的一项：它同时是开关未选中轨道的颜色（`colorControlNormal`），
从「半透明黑」改成「实色浅灰」后与多看、以及 M3 的 `surfaceContainerHighest` 语义一致。

卡片圆角 `bg_mx_card.xml` / `CardGroupDecoration` 由 14dp 统一到 **20dp**。

### 影响面

- 静态校验 `tools/verify_static.py` 全部通过（开关 key 三处对齐无变化）。
- 配色与圆角是全局改动，三个 tab 的观感都会变；胶囊相关改动只落在设置页与关于页。
- 未新增任何权限、未改 hook 逻辑。

## 1.9.5 (versionCode 26) —— 修复「开关文件权限」误报「未放开」

用户在「关于」栏看到 `开关文件权限：rw-rw-r--（未放开：…）`，问是不是真的没放开。

### 根因：判据取错了位

`PrefsFileAccess.fix()` 里 `ok` 的判据是 `perm.endsWith("r")` —— 但九字符权限串的**最后一位是
other 的 `x` 位**，它的取值只有 `-` 或 `x`，**永远不可能等于 `r`**。于是：

- `good` 恒为 `null` → `Fix.ok` 恒为 `false`；
- 「关于」栏恒显示「未放开」，不管权限实际是什么；
- 更烦的是 `PrefsFragment.fixPermsQuietly()` 在**每次改动开关后**都会弹一次这个长 Toast。

也就是说：**权限早就放开了，只有判定在说谎。**

### 修法

1. **判据改成 other 的 `r` 位** —— 权限串的**第 7 位**（末三位是 other 的 `rwx`），掩码 `0x004`；
2. **缺省值取 0 而不是 -1**：`modeBits()` 在 `stat` 失败时返回 -1，而 `-1 and 0x004 == 4`，
   拿 -1 去判会把「读不到权限位」误判成「可读」；
3. **判据口径收紧到「宿主真正读的那份」**：LSPosed 把模块 SP 重定向到
   `/data/misc/apexdata/<uuid>/prefs/<pkg>/…`，宿主读的就是它。本地那份
   （`/data/user_de/0/<pkg>/shared_prefs/…`）模块进程自己就是 owner，`chmod` 必然「成功」，
   拿它当判据会**假阳性** —— 宿主明明读不到，界面却报「读得到」；
4. 展示用的 `path` / `mode` 与 `ok` 取自**同一份文件**，三者不会再互相矛盾。

没打开（`rw-rw----` 之类）时仍旧报「未放开」，`note` 里带每个候选文件的权限与 `(chmod被拒)` 标记，
继续按老办法分流：**0660/0600 = 权限问题；0664/0666 仍读不到 = SELinux**。

## 1.9.4 (versionCode 25) —— 底部导航两栏 + 「点开关即切换 / 点行看详情」

用户要求：「设计一个包含两个底部导航栏可切换的界面。左侧为功能操作区：点击按钮切换对应功能的
开启/关闭状态；点击该区域的其他位置则展示该项目的详细信息。界面再美化一下」

### 设置页：底部导航两个 tab

- **骨架**换成 `activity_settings.xml`：顶部标题区（`bg_mx_header` 淡出渐变，文字随 tab 变）
  + `FrameLayout` 内容容器 + `BottomNavigationView`（菜单 `res/menu/menu_settings_nav.xml`）。
- **左 tab「功能开关」**= `PrefsFragment`（原左栏内容，现在整页宽）；
  **右 tab「关于」**= 新增 `ui/AboutFragment`（把版本 / 简介 / 权限状态 / 更新日志从 Activity 搬进来）。
- 两个 Fragment 用 **`show/hide`** 切换（不是 `replace`）：切回来时滚动位置、装饰器、SP 监听都还在。
  实例先按 tag 找（旋转屏幕后 FragmentManager 会恢复，直接新建会得到第二份）。
- 选中配色 `res/color/mx_nav_item.xml`，选中胶囊底色 `MxNavIndicator`（M3 默认走
  `colorSecondaryContainer` 的宏，这里换成自己的 `mx_accent_soft`）。

### 交互：点开关 = 切换，点行 = 详情

这一条直接否掉了库自带的 `SwitchPreferenceCompat`，两处硬伤都是查库源码确认的：

1. 它注入到行布局 `android:id/widget_frame` 的开关（`preference_widget_switch_compat.xml`）
   写死了 `android:clickable="false"` —— 点开关等于点整行，两条路径分不开；
2. 它继承的 `TwoStatePreference#onClick` 把「整行点击」直接当切换用，行点击腾不出来
   （`performClick()` 的顺序是：`onClick()` → `mOnClickListener.onPreferenceClick()`）。

所以功能行改成自绘的 **`ui/SwitchRowPreference : TwoStatePreference`**：

- `onClick()` 覆盖成**空** —— 行点击不再切换，直接落到 `setOnPreferenceClickListener`（弹详情）；
- 开关自己画在行布局里（`MaterialSwitch`，`@+id/hb_switch`）；`onBindViewHolder` 里
  **先摘 `OnCheckedChangeListener` 再回填状态**（ViewHolder 复用，否则回填会被当成用户操作写盘）；
- 点开关走 `callChangeListener(value)`：危险开关（安全检测 / 解锁隐藏项）的确认框挂在那儿，
  被否掉就把手柄弹回，界面状态与落盘值不会打架；
- 保留 `TwoStatePreference` 的家底：`isChecked` / `persistBoolean` / `notifyDependencyChange`，
  所以 `app:dependency` 的联动（UA 模式、选择引擎）照旧。
- 详情数据在 **`ui/FeatureDetails.kt`**：作用 / Hook 目标 / 生效方式 / 注意事项，用 `Config` 的
  常量当键（改 key 忘了改表时，界面上一眼能发现绑不上）；弹窗布局 `dlg_feature_detail.xml`，
  底部按钮可以直接切换该项（同样过一遍确认逻辑）。

> ⚠ 下拉行（`ua_mode` / `ui_search_engine_target`）与「规则管理」入口的**行点击是主操作**
> （开下拉 / 进页面），所以详情只挂在开关行上。

### 顺手收紧的

- 行高 54dp → 62dp，左右留白统一到 14dp（分组标题对齐到 30dp）；开关用 M3 的 `MaterialSwitch`。
- 「关于」页去掉与标题区重复的标题，改成一排「图标 + 名称 + 版本胶囊」的头卡。
- `tools/verify_static.py`：`defs` 加 `menu` 类型（并收集 `res/menu` 里的 id，Kotlin 里能引用
  `R.id.nav_*`）；开关 key / 默认值的正则认得自绘的 `SwitchRowPreference`。

## 1.9.3 (versionCode 24) —— 界面重构成 MIUI X（左右两栏）+ 日志分档

用户要求两条：「重构界面，MIUI X 风格，整体左右两栏：左栏各项功能，右栏关于（版本 / 简介 / 更新日志）」、
「尽量减少不必要的日志输出，比如之前的测试日志就不用再输出」。

### 设置页：左右两栏，同屏不切换

- **左栏 = 各项功能开关**（原 `PrefsFragment`）
  - 每行换成自定义布局 `mx_pref_switch.xml` / `mx_pref_link.xml`，分组标题用 `mx_pref_category.xml`；
  - **分组卡片由新的 `ui/CardGroupDecoration.kt` 画**：组内首行圆上角、末行圆下角、中间行直角
    （相邻行拼成一张整卡）+ 组内细分隔线 + 组间留白。行外间距靠 `getItemOffsets`，卡片靠 `onDraw`；
  - 分组判定直接问 `PreferenceGroupAdapter.getItem(pos) is PreferenceCategory`（库自己的可见顺序，
    会跳过 `app:dependency` 不满足而隐藏的项；自己去遍历 `PreferenceScreen` 会因此错位）；
  - `PreferenceFragmentCompat` 在 `onViewCreated` 里加的全宽分隔线装饰（`DividerDecoration`）
    会被整体摘掉 —— 它和卡片打架。
- **右栏 = 常驻「关于」**（`view_about.xml`）：版本（`versionName · versionCode`，运行时读
  `PackageManager`）、简介、作用域、**开关文件权限状态**（1.6.7 的 `PrefsFileAccess` 读数从原顶部
  状态栏搬到这里）、更新日志（`ui/Changelogs.kt`，与本文档同源，只留最近 5 版）。
- **MIUI X 设计令牌**：`mx_bg` / `mx_card` / `mx_text_primary|secondary|hint` / `mx_accent` /
  `mx_accent_soft` / `mx_divider` / `mx_track_off`，浅色在 `values/colors.xml`、
  深色在 `values-night/colors.xml`；主题仍是 `Theme.Material3.DayNight.NoActionBar`，
  **开关的打开色走 `colorControlActivated`**（不自绘 switch）。
- 规则管理页（`activity_rule_manager.xml` / `item_rule_set.xml`）同步换成同套卡片配色。

> ⚠ **布局里不要写 `@android:id/switch_widget`**：`SwitchPreferenceCompat` 会把它自己的
> `preference_widget_switch_compat` inflate 进行里的 `android:id/widget_frame`
> （发生在 `PreferenceGroupAdapter.onCreateViewHolder`）。我们只留一个空的 `widget_frame` 接它，
> 自己再画一个就是两个开关。
>
> 另：`TwoStatePreference#onClick` 会翻转开关，**点击整行 = 切换**，所以行上不能挂
> `setOnPreferenceClickListener`（会连带弹窗）。左栏窄（手机竖屏约 190dp），开关说明一律压成
> 一句话，完整的 hook 类名与方法名见 README「功能与开关」表。

### 日志分档（`XLog`）

| 档 | 何时输出 | 内容 |
|---|---|---|
| `XLog.v(...)` | **只在「浏览器调试模式」（`misc_debug`）打开时**，默认完全静默 | 挂载细节、`【诊断】/【采样】/【注入】/【内置规则】` 等排查信息 |
| `XLog.i(...)` | 始终，每个进程十行以内 | 注入、功能注册完成、规则库摘要、接管/还原、规则导入结果 |
| `XLog.e(...)` | 始终 | 异常（原有的「任何 hook 失败只记日志、绝不外抛」不变） |

- 闸门**每次现查开关**（`Config` 自带 1 秒缓存，开销可忽略），所以宿主运行期间打开调试模式，
  日志当场变详细，不必重启浏览器；读不到配置回退 `false`（静默），方向安全。
- 删掉了「设置页已打开」这类只在模块进程自我确认的测试日志。

## 1.9.2 (versionCode 23) —— 用自定义规则**接管宿主自己的拦截引擎**（不再只是模块自己拦）

1.9.1 把宿主自带的规则**读**进模块引擎，但规则本体还是小米下发的那套。这一版反过来：
**hook 宿主，让它按我的规则过滤**。

### 只能改文件（反汇编结论，不是选择）

宿主把规则交给 native 的路径只有一条，`com.android.webview.chromium.ad.AdBlockHelper$Updator`：

```
updateRules(context) → 线程 → updateRuleList("black") / ("white")
                             → 把 content://com.miui.browser.adblock/… 写到
                               files/data/adblock/miui_blacklist.json / miui_whitelist.json
                             → MiuiStatics.getInstance().notifyAdBlockUpdateConfig()
```

native 按**文件路径**读，Java 侧没有任何「喂规则」的接口可 hook。所以做法是：
**hook 它的写入通道 + 覆盖规则文件 + 叫它重载**。

- `miui_blacklist.json` → 整体替换成导入的请求拦截规则
- `miui_whitelist.json` → 清空（宿主的 `@@` 例外会压掉用户规则，不处理就是「导了但拦不住」）
- 其余三个名单保持原样
- 首次接管前把 5 个文件整体备份到 `.hb_backup/`，**关闭开关自动还原**

### 三条防线防止被宿主覆盖回去

1. Application 一就绪就写（赶在 native 首次读盘之前）
2. hook `AdBlockHelper$Updator#updateRuleList` 与 `AdBlockDataUpdator#{writeJSONFile, updateAdBlackist, update}` —— 宿主一写就贴回
3. 守护线程每 8 秒校验一次：**指纹 + 文件字节数**双重比对（只看指纹文件会被骗 —— 文件还在、内容早被换掉了）

写完立刻调 `notifyAdBlockUpdateConfig()`，**不用重启浏览器**。

### 顺带

- 新增开关 `ad_host_override`，**默认关**（这是唯一会改宿主自身数据的开关）
- `##` 元素隐藏规则**不往里写**：native 不认，写进去只会刷 `<AdBlock> Parse error`，
  那部分继续由模块的 CSS 通道负责
- 与模块自己的 URL 引擎**互补不冲突**：同一请求两边都命中时结果一致（都是拦掉）

## 1.9.1 (versionCode 22) —— 上限集体放宽 + 直接读宿主自带的小米规则库

用户反馈两条：「导入界面显示 **5000 条拦截 + 4000 条隐藏**」是什么意思？「小米浏览器自己应该
内置了一定量的广告规则，**直接改那个**不行吗？」

第一个数字的答案有点难看：**5000 和 4000 正好等于解析层的两个硬上限，规则被静默截断了。**
第二个问题的答案是：**能，而且宿主那套规则早就在跑。**

### 新增：把宿主自带的规则库并进来

反汇编 `com.android.browser.util.AdBlockDataUpdator` 得到的全部事实：

| 事实 | 依据（反汇编位置） |
|---|---|
| 规则库在 `files/data/adblock/`，5 个 json | `init()`：`sParentFilePath = getFilesDir() + "/data/adblock"`；文件名 `miui_{black,white,watch,privacy,business}list.json` |
| 格式是 `{"data":[…]}` | `readBlacklist()`：`new JSONObject(str).getJSONArray("data")` |
| 内容是**标准 Adblock 语法**，喂给 chromium 的 **native** 匹配器 | 真机日志：`chromium: <AdBlock> BlockingRuleMatcher::Parse error \|\|57577.live^$csp=script-src`、`@@\|acfun.cn^$document` |

也就是说宿主早就有一套**比本模块语法更完整**的 URL 规则引擎（`$csp` / `$third-party` /
`$document` / `$generichide` 全认，本模块解析器把这些整条丢弃）。但它只管 URL 维度，
且开关在服务端配置里（`PREF_ENABLE_ADBLOCK` / `disable_webview_adblock_business`）。

所以 1.9.1 起：**只读**读这 5 个文件（`HostAdRules`，60 秒缓存），过一遍本模块的解析器
（同一套护栏 + 自动完成 `##` / URL 两路分流），与用户导入的规则合并进同一个引擎。
好处：用户不导入任何规则时模块也不空转；宿主规则里的 `##` 条目还能走 CSS 通道兑现。

**带 `$` 选项的规则不接**（`||x^$csp=…`、`@@||x^$generichide` 这类）—— 那部分交给 native
引擎。理由：本模块的解析器只会把选项**剥掉**，而剥掉后语义是**变宽**的，
`@@||x^$generichide`（原意只是「不隐藏通用元素」）会变成「放行 x 的所有请求」，
反过来把用户自己导入的规则全压掉。宁漏勿误杀。

**绝不往那个目录写。** 宿主有一套 `watermark` 增量更新协议（`checkUpdateMode` /
`addDiffAdBlockData` / `writeOTAFile`），掺进去会让它的差分对账错乱。

新增 `HostContext`：hook `ContextWrapper#attachBaseContext` 拿宿主 Application ——
那个目录是宿主**私有**目录，模块进程读不到；但 hook 代码跑在宿主进程里，权限等同宿主自己。

### 上限集体放宽（这才是「拦不住」的真凶）

| 位置 | 项 | 旧 | 新 |
|---|---|---|---|
| `AdRuleParser` | `MAX_RULES` | 5000 | **30000** |
| `AdRuleParser` | `MAX_ELEMENT_RULES` | 4000 | **15000** |
| `AdRuleEngine` | `MAX_DOMAINS` | 6000 | **40000** |
| `AdRuleEngine` | `MAX_PLAINS` | 4000 | **8000** |
| `AdRuleEngine` | `MAX_REGEXES` | 1500 | **6000** |
| `AdElementCss` | `MAX_GLOBAL` / `MAX_SCOPED` / `MAX_DOMAINS` | 1500 / 3000 / 400 | **6000 / 12000 / 2000** |
| `AdRuleCodec` | `MAX_JSON_CHARS` | 2 MB | **4 MB** |

**只放宽解析层是没用的** —— 引擎三桶会接着丢，中文规则集里带路径 / 通配的规则全进正则桶，
原来只留 1500 条。三个桶按各自成本分别涨价：域名桶走 HashSet 后缀查，放最宽；
裸串桶是唯一**无条件线性 `contains`** 的桶，只翻一倍。

**⚠ 必须重新导入规则**：被截断掉的部分**从来没有存进规则库**（截断发生在导入那一刻）。
只更新 APK 不重导，规则数还是 5000 / 4000。

**日志**（TAG `HookBrowser`）现在分得开两路来源：

```
【内置规则】宿主规则库 21450 条（miui_blacklist.json=20000 miui_whitelist.json=1450 ...）
【规则】用户 2 组（启用 2 组）：URL 6023 + 隐藏 4123 条；宿主内置 21450 条
       → 分流 URL 20000 + 隐藏 1450 条；合计生效 URL 26023 条（超限丢弃 0）、
       隐藏 5573 条（全局 2400 / 限定 3173，超限丢弃 0）
```

`【内置规则】` 那行只在内容变化时打；读不到 Context / 目录不存在会明确写出来。

## 1.9.0 (versionCode 21) —— 元素隐藏规则（`##`）生效：搜索结果页那种「页面里的广告」终于能治

1.8.1 真机日志：规则 **6023 条全部编译生效**，整场只有 **9 次【拦截】**，百度搜索页依旧满屏广告。

日志把问题指得很清楚 —— 拦到的那 9 条全是百度**埋点**（`fclick.baidu.com`、`ada.baidu.com`），
而推广位本身是搜索结果页 **DOM 里的节点**，URL 清一色是 `www.baidu.com` 自家路径。
请求维度（拦 URL）对这类广告**结构性无效**，不是规则不够多。

**新增：元素隐藏（`##`）走 CSS 注入通道**

- `##selector` / `域名##selector` 不再被丢弃，编译成一段 CSS（`sel,sel{display:none!important}`），
  在 `onPageFinished` 时通过 `evaluateJavascript` 注入 `<style>`；
- CSS 是声明式的，**插一次长期生效** —— 首屏渲染的、以及 SPA 后来动态插入的广告节点都会自动隐藏；
- 域名限定的规则按 `location.hostname` 后缀匹配才拼进 CSS（与 URL 引擎的域名语义一致）；
- 三套 WebView（hyper / miui / 百度 SDK）都靠**按形状找 `evaluateJavascript`** 反射调用，
  不做任何类型强转 —— 三套内核互不相干，强转等于一个都注入不进去。

**护栏**

- 全局规则（无域名限定）要求选择器里出现 class / id / 属性 / 组合器：`##div`、`##iframe`
  这类纯标签名会藏掉正文，直接丢；
- 选择器含 `{` `}`、含 `:style(` / `+js(` / `:contains(` 等注入语法、`#@#` / `#?#` / `#$#`
  例外与扩展语法 → 丢；
- 上限：全局 1500 条 / 域名限定 3000 条 / 域名桶 400 个，超出**记数上报**不静默。

**新增诊断【采样】**

`shouldInterceptRequest` 每个 client 类首次被调用时打一行（类名 + 方法签名 + 参数类型），
之后每 200 次汇总一行。之前的盲区是：日志里只有【拦截】，分不清「hook 没被调到」还是
「URL 取到了但规则没命中」。

**界面**：规则集列表与「查看」弹窗分开显示「N 条拦截 + M 条隐藏」，导入提示同。

**⚠ 老规则集要重新导入一次**

1.8.x 里 `##` 规则是在**导入时**就被丢掉的，规则库里没留存。升级后请到「规则管理」
把原来的 URL / 文件**重新导入一遍**（同一来源是覆盖，不会堆积）。只更新 APK 不重导，
元素隐藏仍然是空的。

## 1.8.1 (versionCode 20) —— 修自定义规则的闪退与「拦不住 / 乱拦」

1.8.0 装到真机后的三个真实故障，全部有 LSPosed 日志佐证：

**① 闪退（命中规则后 21ms 崩进程）**

```
java.lang.ClassCastException: Return value's type from hook callback does not match the hooked method
  at android.app.QuaeGreelt.shouldInterceptRequest
  at com.baidu.searchbox.sailor.BdSailorWebView$BdWebViewClientProxy.shouldInterceptRequest
```

宿主里同时装着 **hyper / MIUI / 百度 SDK 三套 WebView 体系**，`shouldInterceptRequest` 的返回类型
三家各有一个互不相干的 `WebResourceResponse`：

| 类 | 返回类型 |
|---|---|
| `hyper.webkit.WebViewClient` | `android.webkit.WebResourceResponse` |
| `miui.webkit.WebViewClient` | `com.miui.webkit.WebResourceResponse` |
| `BdSailorWebView$BdWebViewClientProxy` | `com.baidu.searchbox.sailor.variant.WebResourceResponse` |

1.8.0 一律塞 `android.webkit.WebResourceResponse` → 后两条通道当场抛异常，而且这个异常抛在
hook 回调**之外**，回调里的 try/catch 接不住。**修法**：按 `p.method.returnType` 现造响应
（原生对象能用就用，否则反射三参/四参构造器）；**造不出来就放行** —— 宁可漏一条广告，不可崩一次宿主。

**② 拦不住（"编译了 5000 条规则却什么都没拦下"）**

同一段参数解析写的是 `arg is android.webkit.WebResourceRequest`，但只有 hyper 那套恰好实现了
android 接口，MIUI / 百度 SDK 的 `WebResourceRequest` 是平行体系 → 判断恒为 false → 请求直接
`return`。**修法**：一律反射取 `getUrl()` / `isForMainFrame()`，不再依赖任何一家的类型。

**③ 乱拦（`||ads.com^` 会命中 `evilads.com`）**

`||` 是**域名锚**，但 1.8.0 生成的正则没加 `^`，匹配退化成「URL 任意位置出现」。实测：

| URL | 1.8.0 | 1.8.1 |
|---|---|---|
| `https://evilads.com/x` | ❌命中（误杀） | 不命中 |
| `https://evilads.com/ads.js` | ❌命中（误杀） | 不命中 |
| `https://ads.com/x`、`https://sub.ads.com/x` | 命中 | 命中 |

**修法**：`||` 分支加 `^` 锚，匹配点强制落在 host 域名标签边界。

---

## 1.8.0 (versionCode 19) —— 自定义拦截规则：导入 Adblock 语法规则，URL 维度真拦截

**新增能力**：可以导入自己的广告过滤规则（**URL 链接** / **本地文本文件**两种来源），
按域名、URL 路径、关键词、通配、正则匹配，命中即给宿主一个空响应。全流程在模块内完成，
宿主侧零配置，改完几秒内生效、不用重启浏览器。

### hook 落点（宿主 20.27 反汇编核对，不是猜的）

| 事实 | 依据 |
|---|---|
| 宿主 WebView 是 **hyper 内核**（`hyper.webkit.WebView` / `hyper.webkit.WebViewClient`），不是 `android.webkit.*` | `miui/browser/webview/BrowserWebView` 声明了 `setWebViewClient(Lhyper/webkit/WebViewClient;)` 与 `getWebViewClient()` |
| 请求在 Java 侧的**唯一**汇聚点是 `WebViewClient#shouldInterceptRequest`（两个重载） | `hyper/webkit/WebViewClient`：`(WebView,WebResourceRequest)` 默认实现转调 `(WebView,String)`，后者默认返回 null |
| 主浏览页的 client = `com.android.browser.Tab$MainWebViewClient`，它自己就在用 shouldInterceptRequest 做预加载缓存 | classes.dex 反汇编：命中缓存时自造 `WebResourceResponse(mime, "OK", 200, …, FileInputStream)` 返回 |
| `hyper.webkit.WebResourceRequest` 继承 `android.webkit.WebResourceRequest` | 宿主拿 hyper 的 request 实例走 `invoke-interface …, Landroid/webkit/WebResourceRequest;->isForMainFrame()` |

### 三条 hook 路一起走（只 hook 一个类名必然漏）

1. **`miui.browser.webview.BrowserWebView#setWebViewClient` + `hyper.webkit.WebView#setWebViewClient`（after）**
   → 每设置一次 client，就把**那个实例的类**挂上 `hookAllMethods("shouldInterceptRequest")`。覆盖面主力。
2. 按类名直接挂 `com.android.browser.Tab$MainWebViewClient`（它可能在模块注入之前就设好了）。
3. **兜底**挂 `hyper.webkit.WebViewClient` 基类 —— 只 override String 重载的子类会把请求交给基类。

已挂类名进 Set 去重：同一个类被 hook 两次会让同一个请求走两遍判定。

### 判定跑在网络线程，所以拆桶 + 预筛

- 编译产物是**不可变**结构，`@Volatile` 换引用即可；回调里零解析、零正则编译。
- 三类分桶：纯域名 `||ads.com^` → **HashSet 后缀查**（几个标签就是几次哈希）；
  裸字符串 → 小写 `contains`；只有真带 `*`/`^`/`/re/` 的才编正则，且**先用最长字面段预筛**
  （URL 里没有那段字面量就直接跳过整个正则匹配）—— 这是上千条正则也能跑在热路径上的关键。
- 规则库由独立线程每 8 秒轮询一次重编译，**不在** hook 热路径上读文件。

### 规则语法（Adblock Plus 的一个可用子集）

支持：`!` / `#` 注释、`[Adblock Plus 1.1]` 头部、`@@` 例外、`||host^` 域名锚（含子域）、
路径/关键词的 `*` 通配与 `^` 分隔符、`/regex/` 正则、裸子串。

**明确不支持并按类上报条数**（界面上会显示「忽略 N 条元素隐藏、M 条不合规」）：
元素隐藏规则（`##` / `#@#` / `#?#`）—— 本模块是 URL 维度拦截，不碰 DOM（误藏正文的代价远大于漏广告）；
`$domain=` 选项 —— 按域名限定的规则没法在纯 URL 维度安全还原，**整条丢弃**（宁漏勿误杀）。
其余 `$script` / `$image` / `$third-party` 这类选项**剥掉选项本体、保留规则**。

### 护栏（这些规则一旦生效会拦掉半个互联网，必须在导入时就掐掉）

- 单条 > 512 字符丢；正则 > 160 字符丢；含 `)+` / `)*`（ReDoS 特征）丢
- 裸串 < 4 字符且不含 `/` 也不含 `.` 丢（`ad` / `js` / `img` 会误杀一切）
- 通配规则去掉通配符后最长字面段 < 3 丢（`*a*` 等于全匹配）
- 总量上限：单次导入 5000 条 / 域名 6000 / 子串 4000 / 正则 1500，超限丢弃并在日志里报数

### 存储通道：独立 SP + 权限必须重修

规则库落在**独立的** `adrules.xml`，**不塞进** `settings.xml`：宿主读开关走的是
「直读 XML + 1 秒缓存」，背几百 KB 会让每一次开关判断都解析一遍几百 KB 文本。
规则库由 `AdRuleChannel` 按 5 秒 TTL 独立读、独立缓存。

写入后立刻 `PrefsFileAccess.fix`（0660 → o+r）—— 和 1.6.7 是同一个坑，
不修的结果不是报错，而是**「导入了但永远不生效」**。
`tools/verify_static.py` 新增三处文件名一致性校验，把这个静默失效点钉死。

### 整页被拦时给的是说明页，不是白屏

规则集（尤其从网上下载的）很容易过度匹配。整页命中时返回一张说明页，写明**命中的 URL 与规则原文**，
用户能立刻知道该去停用哪个规则集 —— 白屏只会让人以为浏览器坏了。

### 管理界面

设置页 →「规则管理」（普通 `<Preference>` 入口，不是开关）→ `RuleManagerActivity`：

- 从 **URL 导入**（`HttpURLConnection` 下载，15s/20s 超时，512KB 上限）
- 从 **本地文件导入**（SAF `OpenDocument`，零存储权限）
- 按组**启用 / 停用 / 删除**，**查看**规则明细（最多列 300 条）
- 按**来源**去重：同一个 URL / 文件重复导入是**覆盖**，不是堆积

### 开关

`ad_custom_rules`，默认 **开**。默认开 ≠ 默认拦东西：规则库为空时引擎是 no-op，
行为与没装模块完全一致；导入规则后不需要回来再开一次开关。

### 其他改动

- `Hooks` 新增 `hookAll(Class, method, action)`：给「运行时捕获到的实例类型」挂方法。
- `PrefsFileAccess` 改为同时放开 `settings.xml` 与 `adrules.xml`（目录 × 文件名一起枚举）。
- Manifest 新增 `INTERNET` 权限（**只**用于从 URL 拉规则）、`usesCleartextTraffic`、`RuleManagerActivity`。
- `tools/verify_static.py`：① 开关 key 对齐只看 Switch/List 条目（普通入口 `<Preference>` 不参与）
  ② Manifest 声明的 Activity 必须有对应源码 ③ 规则库 SP 名三处一致。

## 1.7.0 (versionCode 18) —— 切换栏真正出现「必应 / Google / Yandex / 百度」，默认项高亮正确，装上即生效

**1.6.9 为什么栏里还是出不来**（三条真因，本次全部修掉）

1. **`searchBoxOrder` 没补**。`SearchEngineDataProvider#getSearchEngines(scene)` 是
   `遍历 searchBoxOrder → searchBox.containsKey(key) 校验`，只往 `searchBox` 里塞、不补
   `searchBoxOrder` = 等于没塞，宿主根本看不到这三个 key。
2. **`EngineTabsManager#buildDefaultFixedOrderList` 有硬编码白名单**：
   `String[]{"onesearch","baidu","douyin","red"}`，非 AI 搜索路径用它排序 → 白名单外的
   bing / google / yandex 在这一步被整批丢弃。
3. **栏内高亮读的不是「当前引擎」**：`EngineTabAdapter#initSelectedState()` 取的是
   `getDefaultSearchEngineNameByScene("browserSearchBox")` ← `mEngineSet.defaultSearchEngine`
   这张 scene→引擎名 的表。1.6.9 只写了 `SearchModuleSettings.setSearchEngineName`，
   所以即使引擎进去了，选中的也未必是默认项。

另外还有一道过滤：`getSearchEngineList` 在 `isCustomSearchEngineDisplay()` 为假时会丢掉
「自定义引擎」——而 bing / yandex 正是走自定义通道的，一并置真。

**本版改动**

| 目标 | 做法 |
|---|---|
| `getSearchEngines`（before） | 读 `searchBoxOrder` 之前先注入：`searchBox[key]` + **`searchBoxOrder += key`** |
| `buildDefaultFixedOrderList`（after） | 从入参（`getAllSearchEngines` 的完整列表）把模块引擎按模块顺序捞回来，排在宿主白名单项之前 |
| `defaultSearchEngine["browserSearchBox"]` | 写入默认引擎 → 栏内高亮 = 默认项 |
| `SearchModuleKVPrefs#isCustomSearchEngineDisplay`（after） | 置真，否则自定义引擎在 `getSearchEngineList` 里被过滤 |
| `getSearchEngines`（after） | 模块引擎排前，宿主自有项（全网 / 抖音 / 用户自建）保持原顺序跟在后面 —— 是**新增**，不是替换 |
| `getSearchUriForQuery` / `getSearchUriForQueryWitchChannel` | **删除**。1.6.8/1.6.9 这两个兜底会把宿主引擎（抖音 / 全网）的请求也改写成 bing，正是「切来切去只有 bing」的元凶；引擎数据注入后宿主自己就能拼对 URL |
| `EngineTabsManager#buildEngineList`（after） | 按 id 去重 —— 简洁首页（`isSimpleHome`）时宿主会把「自定义引擎且非当前引擎」的项再追加一遍，bing / yandex 正是自定义引擎，会重复出两个 |
| `FullSearchActivity#buildSearchUrl`（after） | 保留，但**仅对模块引擎生效**（全屏搜索页读的是服务端 QSB JSON 配置，里面不一定有 bing） |

**默认值变化**

`ui_search_engine` 默认由 **关** 改为 **开**：常用引擎全部内置，装上即用，不需要进模块手动选。
默认引擎仍是 **bing**，可在模块设置页改为 Google / Yandex / 百度。

**预期效果**

- 首页切换栏：**必应 / Google / Yandex / 百度** + 宿主原有项（全网 / 抖音等），点哪个真的用哪个。
- 必应高亮，搜索框文字与图标与实际送出的请求一致；用户在栏里切换后完全尊重用户。
- 图标：Google / 百度用宿主原生专属图标；必应 / Yandex 用宿主自带的通用「自定义引擎」图标。
- 关掉开关即完全恢复宿主原样（含自带的「搜索引擎」设置页）。

## 1.6.9 (versionCode 17) —— 修好首页「搜索引擎切换栏」：注入引擎数据，切换栏接管

**现象**：开 1.6.8 后，首页搜索框下方那排切换栏（原生是「全网 / 百度 / 抖音」）
切来切去都只有 bing；栏里的文字和图标也没跟着换。

**根因（两条，都是 1.6.8 的设计缺陷）**

1. 1.6.8 把 8 个 URL 出口**无条件**换成 bing → 在切换栏点任何一项，出口都被覆盖，所以「只有 bing」。
2. 切换栏走的是另一条链路，1.6.8 完全没碰：

```
EngineTabsManager#buildEngineList
  ← EngineTabsConfig#getAllSearchEngines          ← 按**硬编码白名单**给图标
      ← SearchEngineDataProvider#getSearchEngineList(true,"browserSearchBox")
          ← #getSearchEngines("browserSearchBox") ← searchBoxOrder + searchBox
          ← #getItemTitle(name)                   ← 项文字
```

白名单里只有 baidu / sogou / 神马 / 360 / 抖音 / 小红书 / google / 全网，**没有 bing、yandex**；
名字不在白名单又不是「自定义引擎」→ 直接丢弃。所以 1.6.8 只改名字出口，栏里的项和图标照旧。

**1.6.8 的一处判断是错的，这里更正**：当时认为 `isEngineLoad(name, "browserSearchBox")` 读的
「服务端下发的 scene map」与 `mEngineSet.searchBox` 是**两份数据**，所以不敢注入。实测反汇编：
`getSearchEngineMapByScene("browserSearchBox")` 返回的就是 `processSearchEngineData(mEngineSet.searchBox)`
—— **同一个池子**。往 `searchBox` 塞引擎即可让 `isEngineLoad` / `getSearchEngineContentByScene` /
宿主自己拼 URL 全部自洽，压根不需要替换 URL 出口。

**改动**

| # | 内容 |
|---|---|
| 1 | `SearchEngineFeature` 重写为「注入 + 跟随」：hook `initEngineSet`(after) / `getSearchEngines`(after) 幂等往 `mEngineSet.searchBox` 补 bing / google / yandex（baidu 宿主已有则保留原样），引擎项用 `SearchEnginesEntity$SearchEngine` 无参构造 + setter（`setSearchEngineName/setSearchUrl/setChannelNo/setTitle_zh_CN/setShowIcon`） |
| 2 | hook `isCustomEngine`：bing / yandex → true，走宿主的「自定义引擎」通道，栏里才显示得出来。google / baidu 留在白名单内，继续用宿主原生专属图标；bing / yandex 用宿主自带的通用图标 `ic_search_engine_tab_custom` |
| 3 | hook `getItemTitle` / `getCurrentEngineTitle` / `SearchEngineInfo#getLabel`：显示名统一成中文（必应 / Google / Yandex / 百度） |
| 4 | hook `EngineTabsConfig#getAllSearchEngines`(after)：栏里顺序 = 默认引擎打头，其余按固定次序 |
| 5 | **默认引擎落到宿主状态**：注入完成后调一次 `SearchModuleSettings#setSearchEngineName(默认引擎)`（**仅首次**，进程内只写一次）→ 切换栏高亮、文字、图标与实际请求一致；之后用户在栏里怎么切都尊重用户 |
| 6 | URL 出口降级为**兜底**：仅当宿主当前引擎**不在**模块清单里（旧数据 / 服务端改写）时才替换；当前引擎是模块引擎时一律不干预（`SearchEngineInfo#{getSearchUriForQuery, getSearchUriForQueryWitchChannel}`、`FullSearchActivity#buildSearchUrl`） |
| 7 | `SearchEngineDataProvider#{getSearchUri, getSearchUriForDesktop, getCurrentEngineUrl}` 三个模板出口 hook **删除** —— 注入后宿主自己就对，不再需要 |

**行为**

- 切换栏变成「必应 / Google / Yandex / 百度」（顺序：下拉选中的打头），点哪个真的用哪个。
- 首次生效会把宿主的当前引擎写成下拉选中的那个（默认 bing），所以栏里高亮也是它。
- 宿主自带的「搜索引擎」设置页会同步显示这四个引擎（同一份数据）。

**已知边界 / 风险**

- 注入的是宿主**内存**数据，服务端每 30 分钟刷新会重建 → 由 `getSearchEngines`(after) 每次补齐，不需要重启。
- bing / yandex 无专属图标资源，用宿主的通用「自定义引擎」图标；文字是准确的。
- 服务端若在 `searchengine.json` 里下发同名 key（如已支持 bing），我们会跳过、保留宿主版本。
- 只接管「首页搜索框」这一个场景（`browserSearchBox`）；热榜 / 搜索发现 / widget 各自独立 map，未动。

## 1.6.8 (versionCode 16) —— 新增「默认搜索引擎」：bing / google / yandex / baidu

**需求**：把默认搜索引擎换成 bing，并提供 google、yandex，最后加上百度。

**宿主搜索链路（classes.dex，20.27.1010901 反汇编核对）**

```
模板（服务端 searchengine.json，本地兜底 res/raw/local_search_engine.json，占位符 {searchTerms}）
  → SearchEngineDataProvider.mEngineSet.searchBox[引擎名].searchUrl
  → SearchEngineInfo.mSearchEngineData      ← SearchEngineDataProvider#getSearchEngineContentByScene
  → SearchEngineInfo.searchUri() = mSearchEngineData[3]
  → SearchEngineInfo#getFormattedUri(): replaceAll("{searchTerms}", URLEncoder.encode(query,"UTF-8"))
```

调用方：`BrowserActivity$2#composeSearchUrl`（地址栏回车）、`ReloadSearchAction#performSearch`（页内搜索）
都经 `OpenSearchSearchEngine#getSearchUriForQuery` → `SearchEngineInfo#getSearchUriForQuery`；
`FullSearchActivity#buildSearchUrl` 自己读 QSB 配置拼 URL，不走这条。

**为什么改「出口」而不是改引擎数据**（⚠ **1.6.9 实测更正：这段判断是错的，保留作教训**）

- ~~`isEngineLoad(name, scene)` 判的是**服务端下发的 scene map**；`mEngineSet.searchBox` 是另一份。~~
  → 实测：`getSearchEngineMapByScene("browserSearchBox")` = `processSearchEngineData(mEngineSet.searchBox)`，
  **同一个池子**。1.6.9 改为直接注入 `searchBox`，此顾虑不成立。
- ~~要让 scene map 里有 bing，得造 `SearchEngineItem`（构造函数 15 个参数）。~~
  → 实测：`SearchEnginesEntity$SearchEngine` 有无参构造 + 完整 setter，构造极简单。
- 代价：本版把出口无条件换成目标引擎，导致**首页切换栏失效**（切来切去只有 bing）——
  1.6.9 已修（改为注入 + 跟随）。

所以本版**不动宿主任何数据**，只替换「URL 出口 + 显示名出口」的返回值。宿主内部状态保持自洽。

**改动**

| # | 内容 |
|---|---|
| 1 | 新增 `features/SearchEngineFeature.kt`（key `ui_search_engine`）：hook 8 个出口 —— `SearchEngineInfo#{getSearchUriForQuery, getSearchUriForQueryWitchChannel, getLabel}`、`SearchEngineDataProvider#{getSearchUri, getSearchUriForDesktop, getCurrentEngineTitle, getCurrentEngineUrl}`、`FullSearchActivity#buildSearchUrl` |
| 2 | `SearchEngines`（同文件）内置 4 个引擎模板，占位符沿用宿主的 `{searchTerms}`：bing 用宿主自带 define（去掉写死的 `cvid`）、baidu 保留原生渠道号 `from=1012852q` |
| 3 | 设置页「界面精简」组新增开关 + 引擎下拉（`ui_search_engine` / `ui_search_engine_target`，默认 **关**、选中 **bing**） |

**已知边界**

- 宿主自己的「搜索引擎」设置页仍显示原引擎被选中 —— 本模块开关优先。
- 只切搜索请求，不切宿主内置的搜索建议（sug）接口。
- google / yandex 需要设备本身可访问。

## 1.6.7 (versionCode 15) —— 真正修好「解锁隐藏设置项」：开关文件权限（0660）在模块进程侧放开

**根因（真机日志定案，1.6.6 的诊断一次命中）**

```
【诊断】/data/misc/apexdata/<uuid>/prefs/com.hupan.hookbrowser/settings.xml 存在=true 字节=647 读取=失败
【诊断】500ms 后 XSharedPreferences 读数=[]
```

文件在、内容也在（647 字节），但宿主进程 `open` 被拒 → **other 没有读权限**。
原因：Android 的 `SharedPreferencesImpl` 每次落盘都会把文件权限重置为 **0660**；
Manifest 里的 `xposedsharedprefs` 只保证文件落在「别的 app 进得来」的目录，**不保证文件本身可读**。
于是宿主永远读不到开关 → 全部走默认值 → 默认 true 的（广告类）看起来「正常」，
默认 false 的（解锁隐藏项 / 调试模式 / 安全检测）**永远开不起来**。与那条 `isVisible` hook 无关，
原版 base.apk 之所以能显示隐藏项，是因为它**不看开关**、无条件 `setResult(true)`。

**修法**

| # | 改动 | 说明 |
|---|---|---|
| 1 | 新增 `PrefsFileAccess`（**模块进程**调用） | 反射 `SharedPreferencesImpl.mFile` + 已知根目录 + 枚举三层，把所有候选文件 `chmod o+r`，并逐级放行父目录的 `x`/`r`。chmod 只能由 owner 做 —— 所以必须在模块进程，在宿主进程调 `makeWorldReadable()` 是无效的 |
| 2 | 设置页 `onResume` 自动修一次 | 打开模块设置页就修，顺带把权限串显示在状态栏（`rw-rw-r--` 一眼可见） |
| 3 | SP 变更后延迟 400ms 再修一次 | **写入会把权限重置回 0660**，不重修就会「改一次开关又失效」 |
| 4 | 宿主侧诊断打细 | 候选文件打印 `模式=0660`（`Os.stat` 取权限位）+ `读取=失败 <异常类>:<message>`。**660/600 = 权限问题；664/666 仍失败 = SELinux，需要换通道** |

**使用**：装好后**打开一次模块设置页**（状态栏应显示 `开关文件权限：rw-rw-r--（已放开，宿主进程读得到）`），
再打开「解锁隐藏设置项」（约 1 秒后宿主重读生效）。宿主日志里 `生效来源=` 应从「无（全部走默认值）」
变成那串 apexdata 路径，`默认关的开关现值` 里 `misc_unlock_pref=true`。

## 1.6.6 (versionCode 14) —— 空壳塌缩（残留的「查看更多」/细长空白）+ 开关改为直读 XML

### ① 卡片藏干净了，残留的空壳也一起塌掉

你实测：卡片不闪了，但**偶尔会剩一行小字「查看更多」，或者一条细长空白**。
那是被藏卡片外面的空壳 —— `ul.card__bd` / `div.card` / `div.topclick-wrap` 的 padding、边框，
以及挂在 `div.card` 里的那行「查看更多」，卡片一没就露出来了。

新增 `collapse()`：容器内**至少有一张 `li.top-click` 且全部处于「将被隐藏」状态**
（已有 `data-hb-hidden`、命中广告标、或命中安装按钮）→ 塌缩该容器；
容器里一旦重新出现正常卡片 → **立刻撤销（复活）**，联想词不会跟着消失。

三道边界：① 只认 `ul.card__bd` / `.card-list` / `.topclick-wrap` / class 含 `card` 的中间层；
② 上溯 ≤3 层，遇 `html`/`body`/`#app`/`.sug-search` 停手；③ **容器内必须有卡**才塌缩
（否则列表还没渲染就会被整层藏掉）。返回串新增 `fold`（塌缩容器数）与
`more`（「查看更多」类入口的路径 + 最终可见性 `v=0/1`），一行日志就能判断生效没生效。

### ② 「解锁隐藏设置项」无效 —— 根因是宿主读不到模块的 SP（不是那条 hook）

1.6.5 的链路自检一次定案：

```
【诊断】开关文件 /data/misc/apexdata/<uuid>/prefs/com.hupan.hookbrowser/settings.xml 存在=true 字节=647
【诊断】文件里的开关：[]                     ← 文件有 647 字节，XSharedPreferences 却读不出 key
【诊断】默认关的开关现值：misc_unlock_pref=false
【诊断】采样满 12 个：共评估 12 个 Preference，其中 2 个宿主原本判不可见   ← 宿主确实在用 isVisible 藏东西
```

即 **所有开关都在回退默认值**：默认 true 的（广告类）照常生效、看起来「正常」，
默认 false 的（本项 / `misc_debug` / `misc_security`）**永远开不起来** —— 你在设置页打开也没用。

改成**宿主侧自己扫候选路径 + 自己解析 XML**（`Config.loadFromFile()`，同步、不依赖框架的
异步加载时机），`XSharedPreferences` 只作兜底。路径候选覆盖 `/data/user_de/0/`、`/data/data/`、
以及 LSPosed 重定向后的 `/data/misc/*/*/prefs/<pkg>/settings.xml`。
自检打印每个候选的「存在 / 字节 / 读取结果 / 解出哪些 key」，外加一行
`500ms 后 XSharedPreferences 读数=` 作对照 —— 下次日志能直接分清「文件读不到」和「框架加载慢」。

### 验证

- `tools/test_js_filter.js` 新增**场景 E**：ul 内全是广告卡 → 断言 `fold≥1`、
  `ul`/`div.card`/`div.topclick-wrap` 三层都塌缩、「查看更多」随父容器不可见（`more` 里 `v=0`）、
  `body`/`#app`/`.sug-search` 一个都不许动、**插入正常卡后塌缩立刻撤销**（`fold=0`、容器恢复、
  `data-hb-empty` 清掉）、反复执行不抖动不级联 → EXIT=0
- 反向回归：把当前 JS patch 回「不塌缩 + CSS 少一条规则」再跑同套断言 → **EXIT=1**
- `tools/verify_static.py` → 全部通过；抠出的 JS `node --check` 语法 OK
- SP XML 解析正则用 Python 等价实现跑过样例（boolean / string / int 三类）

## 1.6.5 (versionCode 13) —— 消灭「一闪而过」+ 把「解锁隐藏项没效果」的成因分干净

### ① 搜索栏广告一闪而过 → 改成「首次绘制前就隐藏」

20:48 会话日志把时序钉死了：

```
20:48:37.396  sugCardApi 请求发出
20:48:38.629  卡片图片开始下载           ← 卡片已经渲染上屏，用户此时看得见
20:48:38.651  第一次 hidden=2            ← 卡片可见 ~1.25s 才被藏
20:48:39.182  btn=1（补掉第三张）
```

两个原因叠加：注入只发 3 次（0/500/1600ms），且 `MutationObserver` 回调里还压了
`setTimeout(150ms)` 防抖。**卡片必然先上屏**。

修法两条：

1. **CSS 规则前置**（1.6.5 新增 `installCss()`）：注入时往页面塞一条
   `li.top-click:has(.ads-tag){display:none!important}`。规则一进 DOM 就同帧生效，
   **之后所有渲染出来的卡片在首次绘制前就被挡掉，不经过任何 JS 回调** → 不再闪。
   只放**结构确定**的选择器：`.ads-tag` / `[class*="ads-tag"]` / `[class*="ad-tag"]`（真机确认广告卡独有，零误杀），
   外加按命名规律猜的 `top-click__btn` / `top-click__install` / `top-click__download`
   —— 猜中顺带把「无广告标但有安装按钮」那张也做到零闪，猜不中等于没写，不会误杀。
   用 `CSS.supports('selector(li:has(span))')` 检测 `:has()`（Chromium 105+）；
   不支持时整条规则自动失效，退化成纯 JS 路径，不报错。
2. **observer 回调同步化**：去掉 150ms 防抖。`MutationObserver` 回调跑在 microtask，
   浏览器在**本帧绘制前**清空 microtask 队列，所以在回调里同步改 style 能赶在首次绘制之前生效。
3. 注入次数 `0/500/1600ms` → `0/120/320/700/1400/2600ms`（前密后疏），并在
   `initSugWebView` 之后额外试注一次（页面还没 loadUrl，多半落空，但一旦成立就是最早时机）。

返回串新增 `css`（CSS 是否注入成功）与 `ob`（observer 是否注册）。
回归测试加**场景 D**：断言 `<style id="hb-css">` 真的插进了 DOM、规则里有 `:has()` 和 `!important`、
**observer 回调返回时新增卡片已经是 `display:none`**（这条直接钉死「不许再 debounce」）、
同批插入的正常卡不被误杀、以及「不支持 `:has()` 时不许崩且 JS 兜底仍生效」。

### ② 「解锁隐藏项没起作用」→ 日志分三步定位

先给结论：**20:38 与 20:48 两次会话的日志里，这个开关都是「关」**，且没有任何读取失败记录 ——
即 hook 正常挂载、`isVisible` 也确实被宿主调用了，只是模块开关没打开。
但 1.6.3 的诊断只打一次、只报值，**没法区分「你没开」和「你开了但那次日志是改之前跑的」**，这次补齐：

- `Config.attach()` 后加 SP 自检：打印开关文件路径 / 是否存在 / 字节数，以及**文件里到底有哪些 key**，
  再并排打印三个默认 false 的开关现值。**默认 true 的开关哪怕一个字节都没读到也「看起来正常」**，
  只有默认 false 的才暴露「SP 读不到」这个问题。
- `UnlockPrefFeature` 从 before hook 改成 **after hook**：先让宿主算出它自己的答案，再翻成 true。
  于是能拿到关键读数 —— **宿主原本判它可见还是不可见**。
- 开关值**变化时重打**日志（底层 SP 有 1s 缓存，改完约 1 秒生效，不用重启宿主）。
- 采样宿主实际评估的 `Preference#getKey()`（前 12 个），并汇总
  「共评估 N 个、其中 M 个宿主原本判不可见」。
  **`M=0` 就是决定性结论**：宿主没在用 `isVisible` 隐藏任何东西 → 你看不到的项不是这条路径藏起来的，
  开关开不开都一样，得换 hook 点；反之 `M>0` 且开关已开却仍看不到，就是宿主另有判据。

## 1.6.4 (versionCode 12) —— 补上「没有广告标、但带安装按钮」的推荐卡

1.6.3 的 `scout` 一次就把问题钉死了，真机读数（`modules_2026-09-18T20_38_36`，搜「a」）：

```
轮次 3   n=189 marks=1 hidden=1
scout: LI m=0 b=1 "Cellular-Z小工具应用商店版"      ← 没被藏
       LI m=1 b=1 "UC浏览器极速版-领现金实用工具应用"  ← 藏了
       LI m=0 b=0 "侧妃难当卿新小说|已完结阅读"        ← 正常联想项

第二次搜索 n=190 marks=2 hidden=2
scout: LI m=0 b=1 "安兔兔评测小工具应用商店版本号:11"  ← 没被藏
       LI m=1 b=1 "甘甘云手机-云端在线虚拟手机实用工具"  ← 藏了
       LI m=1 b=1 "手心扫描王实用工具应用商店版本号:1"   ← 藏了
```

结论：页面上稳定 **3 张推荐卡 / 2 个 ads-tag**。宿主自己那类「应用推荐卡」前端**不打广告标**，
所以只按 `ads-tag` 找永远会漏一张 —— 就是用户看到「三条变一条」的那条。

修复：`JS_FILTER` 增加**判据二 —— 卡里有真实安装按钮**（只作用于 `li.top-click`，仍走
1.6.2 的全套护栏：根容器/列表容器黑名单、`data-hb-hidden` 幂等、视口 60% 高度上限）：

| 级别 | 条件 | 说明 |
|---|---|---|
| ① | `BUTTON` 标签或 class 含 `btn/button/install/download` + 文案在按钮词表 | 常规按钮结构 |
| ② | 元素自身文本恰为「安装 / 立即下载 / 去下载 …」等强按钮文案 | 按钮没 class 时兜底 |
| ③ | **排除**「整卡文本 == 该词」 | 否则搜「安装」时那条**联想词本身**会被误杀 |

`hits` 之外新增 `bhits`（按按钮命中的卡片路径）与 `btn`（按钮卡隐藏条数），一行日志即可分辨
「隐藏条数」是来自广告标还是按钮。

回归：`tools/test_js_filter.js` 新增场景 C（无 ads-tag + 有按钮卡），并把「整卡文本恰为『安装』
的纯词联想项」作为常驻负例写进所有场景。**反向回归已验证**：拿 1.6.3 那份 JS 跑同一套断言必
退出码 1（漏掉按钮卡）。

**副作用上限不变**：只写 `display:none`，不删 DOM；判据找不到就一条不动。

## 1.6.3 (versionCode 11) —— 诊断版：定位「剩下一张广告卡」与「解锁隐藏项没生效」

只加日志，**不改任何隐藏行为**（1.6.2 的定位规则与护栏原样保留）。一次运行可同时定位两件事。

### ① 搜索栏还剩一张广告卡

1.6.2 上线后实测：广告由三条变一条。从 1.6.1 的注入回传看，页面上稳定存在 **2 个**广告标记元素
（那条日志的 `hits` 数组有 2 个元素），推断是「有标记的两张已被藏掉，剩下一张**没有广告标记**」。

为一次看清那块 DOM，`JS_FILTER` 的返回串新增 `scout`（最多 4 条），摘出 top-click 区域里
每个卡片项的摘要：

```json
"scout":[
  "LI[top-click.debug.top-click_square] m=1 btn=0 \"广告 安兔兔评测\"",
  "LI[top-click.debug.top-click_square] m=1 btn=1 \"广告 甘甘云手机 安装\"",
  "LI[top-click.debug.top-click_square] m=0 btn=1 \"豌豆加速 安装\"",
  "LI[top-click.debug.top-click_square] m=0 btn=0 \"百度一下\""
]
```

`m=1` 表示这个卡片项里有广告标记，`btn=1` 表示文本里含「安装/下载/立即」。
据此可判断剩下的那张是哪种情况：

| 观察 | 结论 | 下一步 |
|---|---|---|
| 存在 `m=0 btn=1` 的项 | 那是**没有广告标的应用推荐卡**，标记判据天然抓不到 | 按结构隐藏（`li.top-click` 内带安装按钮的卡），需先确认不会误伤 `m=0 btn=0` 的联想项 |
| 剩余项也有 `m=1` | 有标记但没藏住 → 定位或护栏把它挡了 | 看同一行的 `hidden` 与 `hits`，收紧护栏 |

### ② 「解锁隐藏设置项」测试无效（原版可生效）

静态核查结论：hook 目标（`androidx.preference.Preference#isVisible`，宿主 classes5.dex 确实存在）、
极性（`before` + `result=true`）、开关四层 key、设置页保存逻辑**都没有问题**；
与原版唯一差异是 —— 原版**装上即生效**，本工程按「保守默认值」原则把它做成
`misc_unlock_pref` 且**默认关闭**。

所以 1.6.3 给这条 hook 加了首帧诊断（设置页里每个条目都会调 `isVisible`，只打一次）：

| 日志（TAG `HookBrowser`） | 结论 |
|---|---|
| 完全没有 `【诊断】宿主在读 Preference#isVisible` | 宿主这轮没走这个方法 → 问题在宿主侧判据，与开关无关 |
| `…：开关=关（保持宿主原样…）` | hook 在跑，模块开关是关的 → **去模块设置里打开「解锁隐藏设置项」** |
| `…：开关=开（已强制可见…）` | hook 与开关都生效 → 还看不到隐藏项就是宿主用了别的判据，需要再挖 |

> 也提醒一句：**卸载重装模块会清掉 `settings` SP**，所有开关回到默认值
> （`misc_unlock_pref` 又变回关）。

### 验证

`tools/verify_static.py` 退出码 0；`tools/test_js_filter.js` 退出码 0
（新增的 `scout` 字段已纳入回传，隐藏行为与断言均无变化）。

## 1.6.2 (versionCode 10) —— 修 1.6.1 的级联事故：广告没了、搜索联想也没了

### 现象与根因

1.6.1 真机读数（用户反馈）：**搜索栏广告没了 ✓，但搜索联想也没了 ✗**。

日志里的 `【注入】` 回传直接给出元凶：

```
{"hidden":1,"hits":["…SPAN.ads-tag"],"n":126}                                  ← 第一轮只藏了小标
{"hidden":2,"hits":["…UL.card__bd…"],"n":126}                                  ← 第二轮藏了列表体
{"hidden":2,"hits":["HTML>BODY>DIV#app.flex","HTML>BODY>DIV#app.flex"],"n":126} ← 第三轮把整页藏了
```

旧 `card()` 的判据是「从广告标记往上，找**最近一个高度 ≥40px 的祖先**」：

1. 第一轮藏了祖先 → **该祖先高度归零**；
2. `MutationObserver` 下一轮再跑，从标记往上**再爬一层**（因为原祖先高度已为 0 不满足条件）；
3. 逐轮上爬：`.top-click__info` → `a.top-click__sug` → `li.top-click` → `ul.card__bd`
   → `.card.debug` → `#app.flex` → **整页 `display:none`**，联想词条与广告一起消失。

即「以高度为判据 + 反复执行」= 必然级联。

### 修法（`JS_FILTER` 重写定位规则）

| 环节 | 1.6.1（旧） | 1.6.2（新） |
|---|---|---|
| 找广告标记 | 只看自身文本是否等于「广告」 | 文本判据 **+** class 判据（`ads-tag` / `ad-tag` / `advert`） |
| 定位卡片 | 从标记往上找首个 `height>=40` 的祖先 | `closest('a.top-click__sug')` → 外层 `li.top-click`（**按结构类名**） |
| 兜底 | 无 | 上溯最多 4 层，遇根容器/列表容器即停 |
| 护栏 | 无 | ① 绝不隐藏 `html`/`body`/`#app`；② 不隐藏列表容器（`card-list`/`card__bd`/`topclick-wrap`/`sug-search`）；③ 高度 > 视口 60% 的块不动；④ `data-hb-hidden` 幂等标记，处理过的不再碰 |
| 定位失败 | 退化成把标记自身或误伤祖先隐藏 | **一条都不动**（宁漏勿误） |

日志返回值多一个 `marks`（命中的广告标记数），便于判断是「没标记」还是「标记找不到卡片」。

### 验证（无 JDK / 无真机）

- `tools/test_js_filter.js` 重写：DOM mock 改成**日志里 real 的 `sug.browser.miui.com` 结构**
  （`#app > .sug-search > … > ul.card__bd > li.top-click > a.top-click__sug > … > span.ads-tag`），
  mock 的高度计算改成「祖先隐藏 → 子不可见；子全隐藏 → 父塌缩」，**能真的复现级联**。
  新增断言：广告卡被隐藏 / **同结构无标记的正常联想项不被误藏** / 容器一个都不许动 /
  **连跑 5 轮 `apply()` 仍不许级联** / 无广告时 `hidden=0`。
- 反向回归：把测试指向 1.6.1 的旧 JS（`HB_KT=<file> node tools/test_js_filter.js`）
  → **退出码 1**，准确报出「重复执行后容器 ul 被隐藏（级联回归）」。测试不是空跑。
- `tools/verify_static.py`：退出码 0。

## 1.6.1 (versionCode 9) —— H5 定位坐实，改成往网页里注入过滤脚本

### 1.6.0 诊断版带回来的读数（决定性）

```
【诊断】sugPageUse=2                          → isNativeSugPage() 为假，下拉不是原生
【诊断】sugUrl=https://sug.browser.miui.com/  → 远端 H5
【诊断】SearchSugManager.querySug()
【诊断】queryKeyword -> {"query":"a", …, "isSearchCard":false, …}
【诊断】initRecentAppList: list=0 ad=0 optional=0   ← 应用建议链路整条是空的
```

`BaseSuggestionView#onUpdate`、`SearchSuggestionManager#querySuggest` **一次都没被调用**。
结论定案：**那三张卡片是 `sug.browser.miui.com` 这个远端页面自己渲染的**，
Java 侧没有任何列表持有它 —— ①~④ 那几条 hook 一个都拦不住（都无副作用，保留）。

### 本版做法：复用宿主自己的 JS 通道

反汇编 `com.android.browser.suggestion.SearchSugManager`（classes.dex）找到现成通道：

```
.field  mWebView:Lmiui/browser/webview/BrowserWebView;                     ← private instance
.method evaluateSugJS(String js) { mWebView.evaluateJavascript(js, null); }  ← 宿主自己就在注入 JS
```

于是：

1. `initSugWebView` 之后反射取出 `mWebView` 存档（字段名取不到就在继承链上按类型兜底扫）；
2. 每次 `BrowserWebView#queryKeyword` / `SearchSugManager#querySug` 之后，
   在主线程分 **0 / 500 / 1600ms** 三次向页面注入 `JS_FILTER`；
3. 脚本给页面挂 `MutationObserver`，把「自带『广告』小字标的元素」的**最近一个高度 ≥40px 的祖先**
   `display:none`（**只隐藏、不删 DOM**），后续异步渲染的卡片同样处理；
4. 脚本返回值（隐藏条数 / 命中路径 / 节点总数 / 页面 href）用动态代理实现 `ValueCallback`
   回传到日志，前缀 `【注入】` —— 用来验证选择器是否找对。

### 安全边界

- 只隐藏、不删除、不改宿主数据；找不到「广告」字样就一条不动 → 最坏等于没生效；
- 只对 `SearchSugManager` 那个 WebView 生效（`p.thisObject !== sugWebView` 直接跳过），不碰正常网页；
- 脚本幂等，重复注入无累积；出问题刷新页面即恢复原状。

### 新增诊断（用于找数据接口）

`sug 页面里所有请求的 URL` 会被打印（`【诊断】H5 请求 <url> → null/自造响应`，去重、封顶 40 条）。
如果 DOM 方案效果不理想，下一步就按这些 URL 改成在 `shouldInterceptRequest` 里直接改响应体。

### 判读日志

| 日志（TAG `HookBrowser`） | 含义 |
|---|---|
| `已捕获搜索建议 WebView（…）` | 注入通道拿到了 |
| `【注入】{"hidden":N,"hits":[…]}` | `N>0` 说明隐藏生效；`hits` 是卡片选择器路径 |
| `【注入】{"hidden":0,"hits":[]}` | 页面上没找到「广告」字样 → 把 `【注入】…n=` 的节点数发来，下一步换成按接口过滤 |
| `未找到 WebView#evaluateJavascript` | WebView 类型不是预期，改用其他注入方式 |
| `【诊断】H5 请求 …` | 页面请求清单，用于数据层方案 |

## 1.6.0 (versionCode 8) —— 诊断版：只加日志，不改行为

### 为什么要出这一版

你实测**原版 base.apk 也拦不住**搜索栏那三张带「安装」按钮的卡片。这条直接否掉了 1.5.2 的前提
（「原版有可用的搜索拦截点」），所以回宿主 20.27 把搜索链路整条重查了一遍，结论是：

> **那些卡片不在任何 Java 侧列表里。** 模块层没有可拦的 Java hook 点。

两条排查路径（细节与命令见 `docs/搜索栏广告-根因复核.md`）：

1. **原生卡片 `app_recommend` = `RecentAppViewImpl`**，但 `RecentAppViewHolder#bindData` 只渲染
   `getApplicationInfo(pkg, 8192)` 的图标 + `getAppName()`，布局 `res/layout/item_recent_app.xml`
   只有 `item_recent_app_icon / _name / _delete` 三个 id —— **没有安装按钮**，不是截图那三张卡。
2. **搜索框下拉是 WebView（H5）**：`SearchSugManager#initViews` 建 WebView 再载
   `SearchModuleKVPrefs#getSugUrl()`；每次输入只往网页塞
   `{query, searchid, isSearchCard, secondSearchEnable}`；
   `SearchSuggestionManager#isNativeSugPage()` 要求 `getSugPageUse()==1`，而该方法的兜底值是 **2**；
   `SearchSugManager$initSugWebView$1$1#shouldInterceptRequest` **只拦 QSB 图标**，不拦数据 ——
   卡片内容由远端页面自己拉、自己渲染。

### 顺带更正 1.5.2 的两条错误判断

- **`SugCardData#a` 不是广告开关。** 反汇编 `lambda$updateSugCardData$0()`，它只写 4 个 KvPrefs key：
  `pref_show_sug_switch_view`(display) / `pref_sug_expid` / `pref_second_search_sug` /
  `pref_show_sug`（**只在 `display==true` 时才写成 false**）。且 `pref_show_sug` 的唯一写入方
  `KvPrefs.Qb(Z)` 只会写 false → `KvPrefs.g6()` 恒 false → 它作为 `isSearchCard` 传给 H5 后恒为
  false，**卡片照样出现**。所以这条短路拦不住广告（好在也无副作用，先留着）。
- **`RecentAppManager` 展示层兜底是无效的。** 全部 LSPosed 日志里**从未出现**
  `已从应用建议列表移除 N 条广告`（多轮真机、8 个日志文件），说明这条链里没有 `isAd()` 为真的项 ——
  卡片不走这条链。`HotSearchManager#getHotSearchAdList` 同理。

### 本版做了什么

**只加日志，不改任何行为**（没有新增开关，诊断跟着 `ad_search_sug` 走，默认开）。每类只打一次，
前缀 `【诊断】`。装完强停浏览器、点一次搜索框，然后看 LSPosed 日志（TAG `HookBrowser`）：

| 日志 | 说明 |
|---|---|
| `【诊断】sugPageUse=..` | `1` = 原生下拉；其他 = WebView |
| `【诊断】sugUrl=..` | 下拉网页地址，能直接看出是不是远端 H5 |
| `【诊断】queryKeyword -> {..}` | 宿主正在把 `isSearchCard` 等塞给网页 → **H5 路径坐实** |
| `【诊断】SearchSugManager.querySug(..)` | 新实现（WebView 版）在跑 |
| `【诊断】SearchSuggestionManager.querySuggest(..)` | 旧实现（原生版）在跑 |
| `【诊断】BaseSuggestionView#onUpdate()` | 原生建议视图真被刷新了 → 卡片可能出在原生层 |
| `【诊断】initRecentAppList: list=.. ad=.. optional=..` | 应用建议链路跑没跑、里面有没有广告项 |

**判读**：`queryKeyword` 有日志而 `onUpdate` 没有 → 卡片在远端 H5 里，Java hook 到此为止，
下一步只能是给该 WebView 注入 JS/CSS（或放弃）；反之回原生层继续挖。

## 1.5.2 (versionCode 7)

搜索广告的真正拦截点找到了 —— **原版 base.apk 就干这一条，而且是实测生效的**。
上一版（1.5.1）把展示层 `RecentAppManager` 过滤当主拦点，**没命中**；主拦点其实是
`SugCardData#a()`。

### 关键发现：base.apk 的搜索广告 hook 一直在，只是我认错了

把 base.apk 的全量 hook 调用点重新做了一次无遗漏的线性仿真（这次处理了「明文方法名恰好
长得像 base64」的干扰），base.apk 的 hook 目标一共 11 个类，**其中与搜索有关的只有一条**：

```
com.android.browser.quicksearchbox.data.SugCardData#a        ← 无参 static void
```

回调实现（`Lxposed$100000017$100000007#beforeHookedMethod`）：

```
const/4 v0, #0
setResult(v0)          // = setResult(null)，before 阶段短路，方法体不执行
```

而宿主 20.27 的 `SugCardData` **确实有 `static V a()`** —— 这条 hook 是完全有效的。
之前几版一直在 `SugCardData#requestGameRecommend` 上打转，那个方法在宿主里根本不存在，
所以从来没生效过。

### 主拦点原理（宿主 classes.dex 反汇编）

```java
static final String SERVER_URL = <host> + "qsb/config/browser";

static void a() {                          // ← 拦截点
    lambda$updateSugCardData$0();          //   同步请求服务端配置
}
static void updateSugCardData() {          // 唯一入口，切后台线程执行
    Le/e;->y(new Lcom/android/browser/quicksearchbox/data/g;());
}
```

`lambda$updateSugCardData$0()` 把服务端下发的配置写进 `miui.browser.util.prefs.KvPrefs`：
`KvPrefs.Rb(display)`（搜索推荐卡片显示开关）、`KvPrefs.Pb(expId)`、
`KvPrefs.vb(queryRecommend.val != "0")`、`h6()` 为 false 时 `KvPrefs.Qb(false)`。

**卡片出不出，是服务端通过这个接口下发的。** 短路 `a()` = 宿主永远拿不到 `display=true`，
KvPrefs 保持本地默认（关），卡片不出现。

### 本版改动（`ad_search_sug` 三层）

1. **① 主拦（新增）**：`SugCardData#a` before → `setResult(null)`，与原版 base.apk 等价。
   日志会打 `已短路 ...SugCardData#a（服务端搜索推荐配置不再下发）`。
2. **②③ 展示层兜底（1.5.1 保留）**：`RecentAppManager#initRecentAppList` / `#getRecentAppList`
   的 `isAd()` 过滤。
   > 保留原因：`a()` 短路只阻止**再次刷新**，若 `KvPrefs` 里 `display` 已经被写成了 `true`
   > （模块生效前落过盘），展示层这道就能兜住。只删 `isAd()` 为真的项，对正常应用零影响。
3. **④ `HotSearchManager#getHotSearchAdList()`** 空表兜底（不变）。

### 文案
- `pref_ad_search_sug_sum` 改为按真实机制描述。

## 1.5.1 (versionCode 6)

按真机截图定位（搜索框下拉里出现「安兔兔评测 / 甘甘云手机 / 豌豆加速」三张带「安装」按钮的
卡片）重做搜索广告拦截。**1.5.0 那三层里有两层打偏了**，本版全部更正。

### 更正：1.5.0 的搜索拦截方案作废
- ❌ `SearchBasicVersionData#getAdParem()` —— 实测它**只返回两个「个性化」参数**
  （`isOpenPersonalizedRec` ← `SearchModuleSettings.isPersonalizedCustomization()`、
  `isPersonalizedAdEnabled` ← `isPersonalizedAdEnabled()`），**不含 `isAdvertisingEnabled`**。
  1.5.0 把整表改写成 `false`，既拦不住广告，又篡改了宿主个性化设置的语义。**已删除该 hook。**
- ❌ `ne.y` 的 `b` / `e` / `h` —— 实测这三个方法是**设备公共参数注入**
  （udid / oaid / androidId / fakeOaid / vaid / demei / rg / hl / nt / snt / dm / di / cv / mv /
  osVersionName / vr / cn / dp …），`b(Map)` 也只是 `c(Map, false)` 的委托，
  **完全不通广告参数**。**已删除该 hook。**
- ✅ `HotSearchManager#getHotSearchAdList()` 保留（空表兜底，本来就无害）。

### 新增：真正命中的拦截（`ad_search_sug` 重写）
那三张卡片走的是「应用建议」链路，出口在 `RecentAppManager`：

```
http://qsb.browser.miui.com/qsb/getAppSuggest
  → RecentAppVersionData#updateRecentAppData()   落盘 JSON
  → RecentAppManager#initNetworkList()           解析进 mNetworkLists
  → RecentAppManager#initRecentAppList()         ★ 分流 + 固定插位
  → RecentAppManager#getRecentAppList()          UI 取数（上限 10 条）
```

`initRecentAppList()` 的分段（反汇编实测）：先用 `RecentApp#isAd()` 把广告应用从
`mOptionalAppList` 摘进 `mRecentAdAppList`，**再把后者前两项插回 `mOptionalAppList`
的第 1、第 4 位**（这就是固定广告位），最后取满 10 条填进 `mRecentAppList`。

`RecentApp#isAd()` 的判定：`ads != null && !TextUtils.isEmpty(ads.getExt())`
—— 截图里卡片上那行「广告」字样就是 `ads.ext`。

拦法（两处，都在展示层）：
1. `RecentAppManager#initRecentAppList()` **after** → 清空 `mRecentAdAppList`，
   并把 `isAd()` 为真的项从 `mRecentAppList` 里移除。
2. `RecentAppManager#getRecentAppList()` **after** → 返回前再过滤一遍（UI 取数的公开出口）。

**刻意不在请求层动手**：`getAppSuggest` 的返回值会落盘缓存，拦住请求不等于拦住展示；
展示层一处就够，不必去赌请求链路上的副作用。

### 工程
- `Hooks` 新增 `hookAfter()`（之前只有 before 回调）与 `field()` / `call()` 两个反射辅助，
  全部失败只记日志、不抛异常。

## 1.5.0 (versionCode 5)

> ⚠ 本版「搜索广告三层拦截」的第 1、2 层经 1.5.1 复核后确认是打偏的（见 1.5.1 开头），
> 下面这段保留原文以记录演进过程。

按**真机宿主 APK**（小米浏览器 20.27.1010901，`com.android.browser`，versionCode 202710100，
245 MB / 33 个 dex）逐条核对 hook 目标后的版本。上一版关于「搜索推荐广告」的判断是错的，本版更正。

### 修正（重要，推翻上一版的结论）
- **base.apk 那条 hook 不是死代码**。上一版判定「类名解出 `CommonDownloadDialogImpl`、
  方法名解出 `requestGameRecommend`，两者不同类 → 死代码」——实测宿主后确认**这个组合完全正确**：
  `requestGameRecommend()` 就声明在 `com.android.browser.download.CommonDownloadDialogImpl`
  （classes7.dex，无参、返回 void，同类的 `mGameRecommendCard` / `mGameRecommendList` 字段就是
  弹窗里的游戏推荐卡片）。它的真实语义是**下载弹窗里的游戏推荐**，与搜索无关。
  已改挂回原类，并归到「下载弹窗」开关下。
- 上一版把搜索广告改挂到 `SugCardData#requestGameRecommend` —— 那是**死 hook**：
  宿主 `SugCardData` 一共只有 5 个方法（`<clinit>` / `<init>` / `a` / `lambda$updateSugCardData$0` /
  `updateSugCardData`），根本没有 `requestGameRecommend`。已删除。
- 删除无效 hook `CommonDownloadDialogImpl#mDownloadFromMarket`：它本来就是**字段**不是方法
  （用 hook 方法的方式永远挂不上），而且该字段在宿主 20.27 里也已不存在。

### 新增
- **搜索广告三层拦截**（`ad_search_sug` 开关，重写）：
  1. `SearchBasicVersionData#getAdParem()` → 直接返回
     `{"isOpenPersonalizedRec":"false","isPersonalizedAdEnabled":"false"}`。
     原实现是从 `SearchModuleSettings` 读这两个开关（默认开）拼进请求参数，
     `qsb.browser.miui.com/qsb/getAppSuggest`（推广 App）和
     `api.browser.miui.com/v2/bsr/update/recWord`（推荐热词）都吃这组参数。
  2. `ne.y` 的 `b`/`e`/`h`（网络公共参数注入，classes3.dex）→ 把请求 Map 里硬编码的
     `isAdvertisingEnabled="true"` 改写成 `"false"`。混淆短名，宿主升级可能失效，失效只记日志。
  3. `HotSearchManager#getHotSearchAdList()` → 永远返回空表。
- **新开关 `misc_host_ad`「宿主广告开关」**（默认开）：强制宿主自己的三个广告判断返回关闭
  —— `BrowserSettings.isShowAd()` → false、`isPersonalizedAdEnabled()` → false、
  `isAdCustomDisabled()` → true。三者都是纯 getter，只改返回值、不写 SP，
  所以宿主设置页的开关显示不变，卸载模块即完全恢复。

### 工具 / 工程
- Java/Kotlin 编译级别 8 → **17**（与 AGP 8.2.2 自带的 JDK 对齐），消除
  `源值 8 已过时 / 目标值 8 已过时` 两条 javac 警告。
- 新增 `misc_host_ad` 后四层 key 已同步（`Config.kt` / `prefs.xml` / `strings.xml` / `Features.ALL`），
  静态走查退出码 0。

### 宿主核对结果（本次实测，20.27.1010901）
| hook 目标 | 宿主是否存在 |
|---|---|
| `PremiumOperationManager#{canShowPremiumChangeHint, getHasShowPremiumGuideDialog, getHasShowSimpleToPremiumGuideDialog}` | ✅ 都在 |
| `SimpleVersionHomeLayout#{chanShowPremiumChangeLayout, userClickChangeToPremiumHome}` | ✅ 都在（该类有 107 个方法） |
| `SystemSplashAd#{getAdSplashType, getIsSupportPassiveSplashAd, getServiceIntent}` | ✅ 都在 |
| `WebViewSettingConfig#{getDefaultUserAgent, getUserAgentStringWithoutSwan, getMiuiBrowserUseragentSuffix}` | ✅ 都在 |
| `BrowserSettings#{getDebugMode, getFormalDebugMode}` | ✅ 都在 |
| `Tab$GetSecurityFlagAsyncTask` | ✅ 在 |
| `DownloadHandler$1#call` | ✅ 在 |
| `CommonDownloadDialogImpl#{onCreateDialog, requestGameRecommend}` | ✅ 都在 |
| `SugCardData#requestGameRecommend` | ❌ 不存在（已删除） |
| `CommonDownloadDialogImpl.mDownloadFromMarket`（字段） | ❌ 已移除（已删除） |
| `HotSearchAdVersionData`（搜索框广告热词数据源） | ❌ 33 个 dex 里无 class_defs，只剩悬空引用 |

## 1.4.0 (versionCode 4)

### 修复
- **宿主设置界面空白（严重）**：1.3.0 的 `HidePrefFeature` 把
  `androidx.preference.Preference#isVisible` 一律改成 `false`，而这是**全局** hook ——
  宿主设置页里每一个 Preference 都被隐藏，整页变空白。
  重新核对 base.apk 后确认原版回调的语义是 `setResult(Boolean(true))`，
  即**强制可见**（把小米浏览器藏起来的设置项放出来），1.3.0 的极性整个写反了。
  本版把该功能重命名为 `misc_unlock_pref`（「解锁隐藏设置项」）、改成返回 `true`、
  默认值改为 **false**，并补上开启前的风险确认框。
- 开关默认值语义纠偏：所有「读不到 SP 就用默认值」的路径都改为回退到保守值，
  不再存在「配置读不到 = 功能全开」的情况。
- 编译错误：`PrefsFragment` 调用了 `guardUnlockPrefSwitch()` 却漏写实现，
  报 `Unresolved reference: guardUnlockPrefSwitch`。已补齐实现，
  并把「风险开关确认框」抽成公共方法 `guardRiskySwitch(key, title, msg)`。
- `tools/verify_static.py` 增补第 7 项检查：Kotlin 符号交叉检查
  （自有 object 的成员引用 + 本项目 import 解析），专门拦「改了调用点却漏写实现」这类
  只在编译期暴露的错。已用注入假引用的方式验证该检查确实会失败。

### 新增
- **总开关 `master_enabled`**（默认开）：关闭后所有 hook 立即变成 no-op，
  等于临时卸下模块。排查「是不是模块引起的」时不必再卸载重装。

### 变更
- key 变更：`ui_hide_pref` → `misc_unlock_pref`（语义与极性都变了，旧值故意不复用，
  老用户升级后该功能回到关闭状态）。
- 设置页分组调整：「解锁隐藏设置项」移入「高级（有副作用）」。

### 已知问题（未修复，需要宿主 APK）
- **搜索栏仍会出现广告**。base.apk 里这一处 hook 的类名/方法名配错了
  （类名解出 `CommonDownloadDialogImpl`、方法名解出 `requestGameRecommend`，不同类），
  `findAndHookMethod` 直接抛异常被吞，**原版这条本来就是死代码**。
  本版按方法名语义改挂 `SugCardData#requestGameRecommend`，是否命中待真机日志确认。
  详见 README「已知问题」。

## 1.3.0 (versionCode 3)

首个改造版。基线为 base.apk（作者 Jun_ao，v1.2.0 / versionCode 2），
全部 hook 点由 dex 静态还原后重写为可读 Kotlin，并加开关与设置界面。

### 新增
- **设置界面**：`SettingsActivity` + `PrefsFragment`，分「去广告 / 界面精简 / 高级」三组，
  替代原版那个只有一个 `FUCK_MIUIBROWSER` 文本的空页面。
- **8 个功能开关** + 3 种 UA 伪装模式，全部通过 `XSharedPreferences` 跨进程实时生效，
  改完即用、无需重启浏览器。
- 「拦截网址安全检测」需经风险确认对话框才能开启，且默认关闭。
- 模块日志（`XposedBridge.log`，TAG = `HookBrowser`），每个 hook 的挂载结果一目了然。

### 修复
- **原版会让宿主崩溃**：所有 hook 失败时 `throw new NoClassDefFoundError()`，
  浏览器版本一变就闪退。改为逐功能 try/catch，失败只记日志并跳过。
- **去掉多余的自身二次加载**：原版用 `PathClassLoader` 重新加载自己的 APK 再反射调用
  `handleLoadPackage`，一旦 `寻找模块apk失败` 整个模块失效。改为直接实现 `IXposedHookLoadPackage`。
- 去掉 `debuggable="true"`。
- 去掉 StringFog 字符串加密与 `r/a/`、`r/b/` 资源路径混淆。

### 变更
- 包名由伪装的 `com.android.miuibrowser` 改为 `com.hupan.hookbrowser`（避免与系统包名撞车）。
- `targetSdk` 29 → 34；`compileSdk` 33 → 34。
- Xposed API 依赖以 `app/libs/api-82.jar` 形式内置（`compileOnly`），不再依赖已停维的 xposed 仓库。

> 兼容性提醒：`DebugModeFeature` 两个方法的返回值由方法语义推定（原 APK 该组回调同样只是
> `setResult` 一个布尔），若与新版浏览器行为不符，先关掉 `misc_debug` 再排查。
