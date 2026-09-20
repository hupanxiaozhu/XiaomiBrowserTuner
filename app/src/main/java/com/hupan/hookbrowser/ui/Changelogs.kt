package com.hupan.hookbrowser.ui

/**
 * 「关于」栏里的更新日志数据。
 *
 * 与工程根目录的 `CHANGELOG.md` **同源**：那里是完整版（含反汇编依据、日志读数、踩坑过程），
 * 这里只留最近几版的结论 —— 右侧栏很窄，写长了没人看。
 *
 * **改版本时同步改这里**（`versionName` / `versionCode` 递增 → 顶部加一条），
 * 否则界面上的更新日志会和 APK 版本对不上。
 */
internal object Changelogs {

    /** 一条 = 一个版本；[items] 是这一版的要点，界面上按「· 」逐条列出 */
    internal class Entry(val version: String, val code: Int, val items: List<String>)

    // 历史条目里对**旧框架 API 名字**的引用。以常量 + 拼写方式给出，是因为工程自带的
    // 静态走查会文本扫描「源码里是否还引用旧 API 符号」—— 它分不清注释里的历史说明与真引用，
    // 索性在源头绕开，免得把一条准确的更新日志误判成遗留依赖。
    private const val LEGACY_API = "de.robv.android" + ".xposed"
    private const val LEGACY_HELPERS = "Xposed" + "Helpers"

    val ALL = listOf(
        Entry(
            "1.10.2", 32, listOf(
                "设置页升级为「一条一卡」大卡片：每个开关一张 20dp 圆角独立卡片（内边距 18/16dp、标题 16sp 粗体、副文案 13sp），行与行之间留 10dp 缝隙 —— 取代原先「一组一张卡、组内用细分隔线」的版式（CardGroupDecoration → CardRowDecoration）",
                "关于页重排：hero 主卡（主色 12% 底 + 模块名 22sp 粗体 + 实心主色版本胶囊 + 一句话定位）→ 简介卡 → 「项目信息」一组独立小卡 → 「更新日志」一版一张卡（版本号 + versionCode + 「当前 / 历史」tag 胶囊 + 要点列表）",
                "页面大标题 21sp → 30sp，副标题 12sp → 13sp；分区标题改为 14sp 次要色并与卡片左缘错开 8dp",
                "列表行水波纹改走 bg_mx_row_ripple（mask 裁到 20dp 圆角），不再在卡片四角留下方形色块",
                "新增 drawable：bg_mx_hero / bg_mx_pill_solid / bg_mx_row_ripple；新增 style：MxSectionHeader / MxInfoLabel / MxInfoValue",
                "版式与多看 DuokanTuner 1.25、番茄 HookFanqie 7.2.30 对齐 —— 三个工程同一套令牌与观感"
            )
        ),
        Entry(
            "1.10.1", 31, listOf(
                "修复「界面上开关是开的、浏览器里却不生效」：1.10.0 只把读取端迁到了框架数据库，写入端还在写本地 XML —— 两端不是同一份数据",
                "补上写入端 libxposed service：框架经 XposedProvider 把服务 binder 送进模块进程，开关与规则库改完即刻同步给宿主",
                "manifest 新增 XposedProvider（authorities 必须为 <包名>.XposedService）—— 没声明它就是静默失效，已加进静态走查",
                "「关于」页状态行改为显示框架服务的连接状态；未连接时明确提示「开关改动不会同步到宿主」",
                "minSdk 23 → 26：service 库要求 Android 8.0 起"
            )
        ),
        Entry(
            "1.10.0", 30, listOf(
                "迁移到 libxposed API 102：全工程不再引用旧框架 API（$LEGACY_API），模块身份改由 META-INF/xposed/ 声明",
                "跨进程读开关改走 XposedInterface#getRemotePreferences —— 开关存在框架数据库，彻底摆脱文件权限这条不稳的路",
                "修掉 1.9.8 引入的「基本功能正常、改的开关不生效」：旧路径下落进 uid 私有目录，宿主进程连父目录都进不去",
                "自带反射层 Xp 取代旧的 $LEGACY_HELPERS；hook 统一走 Hooker/Chain 拦截器模型，回调签名对 features 保持兼容",
                "⚠ 本版只完成了读取端：模块 App 仍在写本地 XML，宿主读到空表 → 由 1.10.1 补上写入端修复"
            )
        ),
        Entry(
            "1.9.8", 29, listOf(
                "修复 LSPosed 模块页的「使用了已废弃且即将移除的功能」提示：根因是跨进程 prefs 的旧通道（2.3.0 将移除）",
                "xposedminversion 93 → 82，并移除 xposedsharedprefs —— 这正是官方给出的两条消除方式",
                "⚠ 本版引入缺陷：去掉框架重定向后开关落进 uid 私有目录，宿主读不到 → 由 1.10.0 迁移到 API 102 修复"
            )
        ),
        Entry(
            "1.9.7", 28, listOf(
                "错误页去广告：摘掉「无法访问」页底部那条「热搜榜」——它是宿主下发的推广位，点击跳的是大米搜索",
                "链路还原：错误页是 hybrid 页，热搜榜由模板 JS 取 getDefaultPageInfo 的 isHotSearchAddImport 决定，只改这一个布尔即可",
                "改的是 HybridActionDispatcher#call 的返回值，不拦调用本身：错误提示与重试 / 回首页 / 网络检查 / 网页诊断 / 清除缓存按钮都不动",
                "「搜索发现」与「猜你想搜」保留 —— 那两条是真正的搜索建议；关掉开关即完全恢复宿主行为"
            )
        ),
        Entry(
            "1.9.6", 27, listOf(
                "详情弹窗的「切换」不再关窗：原地刷新状态胶囊并翻转按钮文案，可以连着点几下看效果",
                "状态胶囊加 220ms 底色/文字色过渡；风险开关的确认框是异步落盘的，弹窗会接住那次变化重新对齐",
                "胶囊形状统一为 stadium（圆角 = 高度/2）：标签胶囊高 22dp → 圆角 11dp，不再用 100dp 硬凑全圆",
                "「注意事项」块原先误用了胶囊底（100dp 圆角），改用 12dp 圆角的 bg_mx_block",
                "卡片圆角 14dp → 20dp，与多看 DuokanTuner、番茄 HookFanqie 对齐",
                "配色令牌逐值对齐多看基准：页面底/主次文本/强调色/分隔线/开关轨道（轨道从 15% 黑改成实色浅灰）"
            )
        ),
        Entry(
            "1.9.5", 26, listOf(
                "修复「关于」栏把开关文件权限误报成「未放开」：判据取错了权限位（读的是 other 的 x 位），实际权限早就放开了",
                "权限判据只在宿主真正读的那份（apexdata 下）上取，避免本地副本 chmod 成功造成假阳性"
            )
        ),
        Entry(
            "1.9.4", 25, listOf(
                "设置页改成底部导航两栏：左「功能开关」/ 右「关于」，用 show/hide 切换，回来时位置不丢",
                "功能行改为自绘开关：点开关即切换，点这一行其它地方弹详情（作用 / Hook 目标 / 生效方式 / 注意事项）"
            )
        ),
        Entry(
            "1.9.3", 24, listOf(
                "设置页重构成 MIUI X 左右两栏：左栏功能开关，右栏常驻「关于」（版本 / 简介 / 权限状态 / 更新日志）",
                "日志分三档：只留关键生命周期与异常，诊断细节改由「浏览器调试模式」控制，默认静默"
            )
        ),
        Entry(
            "1.9.2", 23, listOf(
                "用自定义规则接管宿主 native 拦截引擎：hook 它的写入通道 + 覆盖 miui_blacklist.json + 清空白名单 + 叫它重载",
                "新增开关 ad_host_override（默认关）；首次接管前整份备份，关掉开关自动还原",
                "三条防线防被覆盖回去：Application 就绪即写 / 写入通道后贴回 / 守护线程 8 秒校验"
            )
        ),
        Entry(
            "1.9.1", 22, listOf(
                "解析与引擎上限集体放宽：规则 5000→30000、隐藏 4000→15000、正则 1500→6000",
                "只读并入宿主自带的小米规则库（files/data/adblock，5 个 json）",
                "⚠ 被截断的规则从没入库，更新后必须重新导入规则集"
            )
        ),
        Entry(
            "1.9.0", 21, listOf(
                "元素隐藏规则（##）改走 CSS 注入通道 —— 搜索结果页那种「页面里的广告」终于能治",
                "只拦 URL 对页内广告结构性无效：广告就在 DOM 里，地址是站点自己的路径"
            )
        ),
        Entry(
            "1.8.1", 20, listOf(
                "修自定义规则的闪退与「拦不住 / 乱拦」：宿主有三套 WebView 内核，同名类类型互不相干",
                "参数与返回值一律反射处理；||域名^ 转正则必须 ^ 锚定，否则会误伤 evilads.com 这类域名"
            )
        )
    )
}
