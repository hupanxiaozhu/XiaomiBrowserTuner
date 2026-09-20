# base.apk 静态分析报告

- 目标文件：`base.apk`（原作者发布的模块）（49,727 字节 / 100,221 字节解压后）
- 分析方式：纯静态。本机无 JDK，未使用 apktool/jadx；走 python AXML 反编译 + 自研 DEX 反汇编 + StringFog 解密还原
- 分析时间：2026-09-18

---

## 一、结论

**这是一个针对小米浏览器（`com.android.browser`）的「去广告」Xposed 模块，不是浏览器本体，也不是 LSPatch 注入包。**

| 项 | 值 | 来源 |
|---|---|---|
| `package=` | `com.android.miuibrowser`（伪装成小米浏览器系包名） | AndroidManifest |
| versionName / versionCode | `1.2.0` / `2` | AndroidManifest |
| minSdk / targetSdk / compileSdk | 23 / 29 / 33 | 反编译 uses-sdk |
| Application 类 | 无（未声明 android:name） | AndroidManifest |
| 入口类 | `xposed`（默认包，`assets/xposed_init` 内容 = `xposed`） | assets/xposed_init |
| 是否 Xposed 模块 | **是**。manifest 含 `xposedmodule=true` / `xposedminversion=54` / `xposedscope=com.android.browser` | AndroidManifest |
| 是否 LSPatch 注入 | 否（无 `assets/lspatch/`、无 `META-INF/lspatch`） | 结构清单 |
| 是否加固 | 否（单 dex 89KB，无壳 so，无 StubApp） | 结构清单 + dex |
| 作者标识 | `干掉小米浏览器广告    Q群1094704567    By Jun_ao` | xposeddescription |
| `debuggable` | **true**（生产模块不该开） | AndroidManifest |
| UI | `MainActivity` → `LinearLayout` + `TextView text="FUCK_MIUIBROWSER"` | 反编译 `r/b/a.xml` |

### 保护/混淆

- **StringFog（XOR）**：`com.github.megatronking.stringfog.xor.StringFogImpl`，密钥硬编码为 `"UTF-8"`，流程 `Base64.decode(s,2)` → 逐字节 XOR 密钥 → UTF-8 解码。静态可完全还原（本报告全部字符串已还原）。
- **控制流平坦化**：每个原方法被拆成 `access$N` 转发 + `invokeHandleHookMethod` 间接调用。
- **类名数字化**：hook 回调类为 `Lxposed$100000003$100000000` 这类自动生成名，共 20 组。
- **资源路径混淆**：`r/a/a.png`、`r/b/a.xml`。且 `r/a/a.png` 的文件头不是合法 PNG（读出宽高为垃圾值），属伪造/防提取资源。
- **签名**：仅 v1（`META-INF/ANDROID.RSA`），无 v2/v3 块。

### 一个值得单独说的设计

`Lxposedmain.invokeHandleHookMethod()` 里做了这么一件事：

```
findApkFile(ctx, "com.android.miuibrowser")            // 用 createPackageContext 拿模块自己的 apk 路径
→ new PathClassLoader(apkPath, systemClassLoader)      // 新建一个 ClassLoader
→ Class.forName("xposed", true, loader)                // 在自己的 apk 里再加载一次自己
→ newInstance().getDeclaredMethod("handleLoadPackage", XC_LoadPackage$LoadPackageParam).invoke(...)
```

即：**模块把自己的入口类当"外部代码"重新加载一遍再反射调用**。同时用 `hook Application#attach` 拿到 Context，再回填 `loadPackageParam.classLoader`。

这层设计解决的是「宿主 ClassLoader 里没有 Xposed 类、直接在宿主编译期引用会 NoClassDefFoundError」的老问题，代价是每个类名/方法名都要运行时解密 + 反射，属于**用复杂度换兼容性**，是后续最大的优化空间（详见第四节）。

---

## 二、Hook 清单（已全部还原，共 13 组 / 21 个方法）

| # | 目标类 | 目标方法 | 语义 | 建议开关名 |
|---|---|---|---|---|
| 0 | `android.app.Application` | `attach` | 拿宿主 Context / 兜底 classLoader | 不可关（框架层） |
| 1 | `com.msa.sdk.core.splash.SystemSplashAd` | `getAdSplashType`、`getIsSupportPassiveSplashAd`、`getServiceIntent` | **MSA 联盟开屏广告拦截** | `ad_splash` |
| 2 | `com.android.browser.homepage.PremiumOperationManager` | `canShowPremiumChangeHint`、`getHasShowPremiumGuideDialog`、`getHasShowSimpleToPremiumGuideDialog` | **首页「尊享版」推广位屏蔽** | `ad_home_promo` |
| 3 | `com.android.browser.homepage.SimpleVersionHomeLayout` | `chanShowPremiumChangeLayout`、`userClickChangeToPremiumHome` | 简洁版首页的「切到尊享版」引导 | `ad_home_promo` |
| 4 | `com.android.browser.download.CommonDownloadDialogImpl` | `requestGameRecommend` | **下载弹窗内的游戏推荐卡片** | `ui_download` |
| 5 | `com.android.browser.download.CommonDownloadDialogImpl` | `onCreateDialog`；`mDownloadFromMarket`（**字段**，非方法） | 下载弹窗 / 跳应用市场 | `ui_download` |
| 6 | `com.android.browser.DownloadHandler$1` | `call` | 下载处理器拦截 | `ui_download` |
| 7 | `com.android.browser.Tab$GetSecurityFlagAsyncTask` | `onPostExecute` | **网址安全检测结果篡改** ⚠ | `misc_security` |
| 8 | `com.android.browser.util.WebViewSettingConfig` | `getDefaultUserAgent`、`getMiuiBrowserUseragentSuffix`、`getUserAgentStringWithoutSwan` | **UA 改造：抹掉小米浏览器标识** | `ua_patch` |
| 9 | `com.android.browser.BrowserSettings` | `getDebugMode`、`getFormalDebugMode` | 调试模式开关 | `misc_debug` |
| 10 | `androidx.preference.Preference` | `isVisible` | 显示宿主隐藏的设置项（回调是 `setResult(true)`，**不是隐藏**） | `misc_unlock_pref`（默认关） |
| 11 | `android.app.Activity` | `onCreate(Bundle)` | 通用 Activity 入口（回调空实现，只 `invoke-super`，无实际作用） | 不可关（框架层） |
| 12 | `com.android.browser.quicksearchbox.data.SugCardData` | `a` | **搜索框下拉推荐广告拦截**：短路服务端配置下发（见 4.1 节） | `ad_search_sug` |

> ⚠ 第 12 行是 1.5.2 才补上的 —— 前几次解析被「明文方法名恰好满足 base64 字符集」的
> 启发式干扰，导致类名与方法名错位，一度误判成 `SugCardData#requestGameRecommend`
> （宿主里没这个方法）。**全量重核过程与正确清单见 `宿主20.27-hook目标核对.md` 第 4 节。**

### UA 相关硬编码常量（已还原）

- 伪装 UA（多 App 拼接）：
  `Mozilla/5.0 (Linux; U; Android %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.7049.79 Mobile Safari/537.36 SearchCraft/2.8.2 baiduboxapp/3.2.5.10 BingWeb/9.1 ALiSearchApp/2.4 WeChat/arm64`
- 桌面 UA：
  `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36`
- 正则剥离：` XiaoMi/MiuiBrowser/[^ ]*` 以及 `" Build/"`、`") AppleWebKit/537.36 (KHTML, like Gecko) "` 等拼接片段

### 现成 UI 文案（已还原）

- `xposed模块:已激活` / `xposed模块:未激活`
- `未被hook` / `寻找模块apk失败`

---

## 三、现有调用链

```
LSPosed
 └─ assets/xposed_init → 类 xposed
     └─ handleLoadPackage(LoadPackageParam)         [Lxposed 与 Lxposedmain 各有一份，逻辑重复]
         ├─ 过滤 packageName != "com.android.browser" → return
         ├─ hook Application#attach                  [拿 Context + 重设 classLoader]
         └─ afterHooked:
             └─ invokeHandleHookMethod()             [PathClassLoader 重新加载自己 → 反射 handleLoadPackage]
                 └─ 12 组 findAndHookMethod（无开关、无条件执行）
```

问题点：`Lxposed` 与 `Lxposedmain` 两个类各实现了一遍 `handleLoadPackage`，且入口只用了其中一条链，另一半是死代码。

---

## 四、优化空间（按收益排序）

### P0 — 会让宿主崩溃的缺陷
1. **异常处理方向错误**：`Lxposed.handleLoadPackage` 里所有 `catch` 最终 `throw new NoClassDefFoundError(...)`。小米浏览器版本一变、某个内部类改名 → **宿主进程直接崩**，而不是静默跳过该 hook。
   → 改为 per-hook `try/catch`，吞异常并写模块日志。

2. **自身重复加载**：`invokeHandleHookMethod` 用 `PathClassLoader` 二次加载自己的 apk 并反射调用。纯属多余（自己的类就在自己的 ClassLoader 里），却带来：
   - 每个类名/方法名一次 Base64+XOR 解密
   - 一次完整反射链（`getDeclaredMethod` + `invoke`）
   - `寻找模块apk失败` 时直接把整个模块干掉
   → 直接静态引用入口方法即可。

### P1 — 功能层
3. **零开关**：12 组 hook 全部无条件生效，用户无法单独关闭（例如只想保留去广告、不想要 UA 伪装）。
4. **UA 硬编码版本号**（Chrome/116、Chrome/135）：会随浏览器升级而失效或造成站点误判，应做成可配置项。
5. **`Tab$GetSecurityFlagAsyncTask#onPostExecute`**：抹掉网址安全检测结果，属"降低宿主安全性"的改动，建议默认关闭并明确提示风险。
6. **死代码**：`Lxposed` / `Lxposedmain` 双份 `handleLoadPackage`，只保留一条。

### P2 — 工程与体验
7. **UI 等于没有**：只有一个 `FUCK_MIUIBROWSER` 文本 + 一个 Toast。缺设置页、缺激活状态可视化。
8. **去掉 StringFog / 控制流平坦化**：当前混淆只防"抄"，不防"看"（密钥硬编码，10 行脚本即可全还原），却显著提高维护成本。自用模块建议直接写明文。
9. **`debuggable="true"`**：应关闭。
10. **仅 v1 签名**：现代 ROM 应补 v2/v3。
11. **资源路径混淆（`r/a/`、`r/b/`）**：无实际价值，且伪 PNG 在某些资源扫描工具下会异常，去掉。
12. **minSdk/targetSdk 偏低**（23/29）：targetSdk 29 在新系统上会受兼容性限制，建议提到 33+（按你实际运行环境定）。

---

## 五、改造落地（已完成）

本工程位于本仓库根目录（Kotlin + XML + androidx.preference，包名 `com.hupan.hookbrowser`，v1.3.0 / versionCode 3）。
本报告第二节的 12 组 hook 已全部还原并重写，改为 8 个独立开关 + 设置界面。

### 目标形态
```
HookBrowser/
├─ app/libs/api-82.jar                       Xposed API（compileOnly，随工程提供）
├─ app/src/main/
│  ├─ AndroidManifest.xml                    xposedmodule / xposedscope / xposedsharedprefs=true
│  ├─ assets/xposed_init                     入口：com.hupan.hookbrowser.MainHook
│  ├─ java/com/hupan/hookbrowser/
│  │   ├─ MainHook.kt                        入口，逐功能 try/catch 注册
│  │   ├─ Config.kt                          开关读取（XSharedPreferences + 1s 缓存 + 默认值表）
│  │   ├─ Hooks.kt                           hook 封装（per-hook 容错）
│  │   ├─ XLog.kt                            日志（TAG=HookBrowser）
│  │   ├─ features/                          8 个功能，每功能一个文件
│  │   │   SplashAdFeature / HomePromoFeature / SearchSugFeature / DownloadFeature
│  │   │   UaFeature / HidePrefFeature / DebugModeFeature / SecurityFeature
│  │   └─ ui/                                SettingsActivity + PrefsFragment
│  └─ res/xml/prefs.xml                      开关定义
└─ CHANGELOG.md / README.md
```

### 开关机制
- 模块进程写 `getSharedPreferences("settings", MODE_PRIVATE)`，宿主进程用
  `XSharedPreferences("com.hupan.hookbrowser", "settings")` 读，靠 manifest 的 `xposedsharedprefs=true` 放开可读权限。
- 宿主侧维护一份与 `prefs.xml` 的 `defaultValue` 完全一致的默认值表（XSharedPreferences 读不到 XML 默认值），
  并带 1 秒缓存窗口，避免每次回调都去 stat 文件。
- 每个 hook 回调第一行 `if (on()) ...`，关掉开关时行为与「没装模块」完全等价；改造开关即时生效，无需重启浏览器。

### UI
- `SettingsActivity` 顶部状态条 + `PreferenceFragmentCompat` 分组开关列表（去广告 / 界面精简 / 高级）。
- 「拦截网址安全检测」不接受一键打开，必须先过风险确认对话框。
- UA 伪装提供 3 种模式下拉：Chrome 移动版（真机信息）／多 App 伪装／桌面版 Chrome。

### 四层 key 必须同步
新增功能时，`Config.kt` 常量值、`res/xml/prefs.xml` 的 `app:key`、`strings.xml` 文案、
`Features.ALL` 注册表四处缺一不可，否则开关静默失效。工程内已附一份静态一致性校验脚本可复用。

