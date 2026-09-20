package com.hupan.hookbrowser.features

import android.os.Handler
import android.os.Looper
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.XLog
import java.lang.reflect.Proxy

/**
 * 搜索框下拉里的「应用推荐」广告（截图里那三张带「安装」按钮的卡片）。
 *
 * 宿主基线：小米浏览器 20.27.1010901（`com.android.browser`，versionCode 202710100）。
 *
 * > **1.6.0 更正**：用户实测**原版 base.apk 也拦不住**这三张卡片，所以「照搬原版的
 * > `SugCardData#a` 就能去广告」这个前提是错的，本文件以前那段说辞已删。
 * > 复核过程与证据见 `docs/搜索栏广告-根因复核.md`，结论：
 * > **那些卡片不在任何 Java 侧列表里** —— 原生卡片 `app_recommend` 渲染不出「安装」按钮，
 * > 而搜索框下拉本身是 WebView（H5）承载的远端页面。所以本类现在的作用是
 * > ①保留无害的历史 hook ②**跑诊断日志，判定卡片到底出在哪一层**。
 *
 * ## 各条 hook 的实际语义（都被反汇编核对过，别再凭名字猜）
 *
 * ① `SugCardData#a()` before → `setResult(null)`
 *    它请求 `qsb/config/browser` 后只写 4 个 KvPrefs key：
 *    `pref_show_sug_switch_view`(display) / `pref_sug_expid`(expId) /
 *    `pref_second_search_sug`(queryRecommend) / `pref_show_sug`（**只在 display==true 时写 false**）。
 *    **没有一个是广告开关**；`pref_show_sug` 更是只有「写 false」这一条路径，
 *    它作为 `isSearchCard` 塞给 H5 后恒为 false，而卡片照样出现。→ 这条短路拦不住广告，
 *    但也没有副作用（不会改宿主 SP），先留着。
 *
 * ②③ `RecentAppManager#{initRecentAppList, getRecentAppList}`
 *    `initRecentAppList` 会把 `isAd()` 为真的项摘出、再把前两个插回固定广告位，
 *    理论上过滤能生效；但全部 LSPosed 日志里**从未出现**「已从应用建议列表移除 N 条广告」，
 *    说明这条链里没有 `isAd()` 为真的项 → 卡片不走这里。保留为兜底。
 *
 * ④ `HotSearchManager#getHotSearchAdList` 空表：宿主侧该路径已是废代码
 *    （数据源类 `HotSearchAdVersionData` 在 33 个 dex 里都不存在），留着不亏。
 *
 * ## 诊断（1.6.0 临时加入，只读日志、不改任何行为）
 *
 * 每类只打一次，日志前缀 `【诊断】`。看 LSPosed 日志（TAG `HookBrowser`）：
 *
 * | 日志 | 含义 |
 * |---|---|
 * | `sugPageUse=2` | 下拉**不是**原生（`isNativeSugPage()` 要求 ==1），倾向 WebView |
 * | `sugUrl=...` | 下拉网页的地址；能证明是远端 H5 |
 * | `queryKeyword -> {...}` | 宿主正把 `{query, searchid, isSearchCard, secondSearchEnable}` 喂给网页 → **H5 路径坐实** |
 * | `SearchSugManager.querySug(...)` | 新实现（WebView 版）在跑 |
 * | `SearchSuggestionManager.querySuggest(...)` | 旧实现（原生版）在跑 |
 * | `BaseSuggestionView#onUpdate()` | 原生建议视图真的被更新了 → 卡片可能出在原生层 |
 * | `initRecentAppList: list=.. ad=.. optional=..` | 应用建议链路跑没跑、里面有没有广告项 |
 *
 * ### 1.6.1 实测读数（已定案）
 *
 * ```
 * 【诊断】sugPageUse=2            → isNativeSugPage() 为假，下拉不是原生
 * 【诊断】sugUrl=https://sug.browser.miui.com/
 * 【诊断】SearchSugManager.querySug()  且 queryKeyword -> {"query":"a", …, "isSearchCard":false, …}
 * 【诊断】initRecentAppList: list=0 ad=0 optional=0   ← 应用建议链路整条是空的
 * ```
 *
 * 结论：**那三张卡片是 `sug.browser.miui.com` 这个远端页面自己渲染的**，
 * Java 侧没有任何列表持有它。①~④ 保持原样（都拦不住广告，但都无副作用）。
 *
 * ## H5 过滤（1.6.1 引入，1.6.2 修正定位规则）
 *
 * 反汇编 `SearchSugManager`（classes.dex）拿到了现成的注入通道：
 *
 * ```
 * .field  mWebView:Lmiui/browser/webview/BrowserWebView;        ← private instance 字段
 * .method evaluateSugJS(String js) { mWebView.evaluateJavascript(js, null); }   ← 宿主自己就在注入 JS
 * ```
 *
 * 所以做法：`initSugWebView` 之后反射拿 `mWebView` 存档 →
 * 每次 `querySug` / `queryKeyword` 之后，在主线程分 3 次（0/500/1600ms）向页面注入
 * `JS_FILTER`：它给 sug 页面挂一个 `MutationObserver`，把带「广告」小标的**那张卡片**
 * `display:none`（**隐藏而非删除**，出问题刷新页面即恢复），
 * 并把命中情况（隐藏条数 + 卡片选择器路径 + 节点总数）通过 `ValueCallback` 回传到日志
 * （前缀 `【注入】`），用于验证选择器是否找对。
 *
 * ⚠ 1.6.1 的定位规则（找「最近一个 height>=40 的祖先」）有致命缺陷：被隐藏的祖先高度归零，
 * observer 下一轮继续上爬，级联到 `#app` → **整页连同搜索联想一起消失**（真机已验证）。
 * 1.6.2 改为按结构类名 `closest()` 定位 + 黑名单/幂等/高度上限三重护栏，见 `JS_FILTER` 注释。
 *
 * ### 1.6.4：补上「没有广告标」的那张卡（1.6.3 的 scout 一次定案）
 *
 * 真机读数（`modules_2026-09-18T20_38_36`）：页面上稳定 **3 张推荐卡 / 2 个 `ads-tag`**，
 * 且**每次都是「`m=0` 的那张没被藏」**：
 *
 * ```
 * LI m=0 b=1 "Cellular-Z小工具应用商店版"        ← 漏网
 * LI m=1 b=1 "UC浏览器极速版-领现金实用工具应用"   ← 已藏
 * LI m=0 b=0 "侧妃难当卿新小说|已完结阅读"        ← 正常联想词
 * ```
 *
 * 即：宿主自己的「应用推荐卡」前端**不打广告标**，只按 `ads-tag` 找必然漏一张。
 * 于是加**判据二 —— 卡里有真实安装按钮**（`btnNode()`）：① 按钮特征 + 按钮词表
 * ② 自身文本恰为强按钮文案 ③ **排除「整卡文本 == 该词」**（否则搜「安装」时那条联想词
 * 会被误杀 —— 这条护栏有常驻负例测试钉着）。只作用于 `li.top-click`，护栏全套不变。
 * 返回串新增 `btn`（按钮卡隐藏条数）与 `bhits`（按钮命中路径），一眼分辨是哪条判据生效。
 *
 * 副作用上限：只会让元素不可见；找不到判据就一条不动。脚本幂等，重复注入无累积。
 *
 * ### 1.6.5：从「藏得快」到「不上屏」（消灭一闪而过）
 *
 * 20:48 真机日志的时序：`sugCardApi` 37.396 发出 → 卡片图片 38.629 开始下载（= 已上屏）
 * → 38.651 才第一次隐藏，**卡片可见约 1.25s**。两个原因：注入只发 3 次（0/500/1600ms），
 * 且 observer 回调里压了 `setTimeout(150ms)` 防抖。
 *
 * 现在：① 脚本开头先铺一条 **CSS 规则**（`li.top-click:has(.ads-tag){display:none!important}`），
 * 规则进 DOM 即同帧生效，之后渲染出的卡片在**首次绘制前**就被挡住，不经任何 JS 回调；
 * ② observer 回调**同步** apply（microtask 在本帧绘制前清空），不再 debounce；
 * ③ 注入改为 0/120/320/700/1400/2600ms 六次，并在 `initSugWebView` 后额外试注一次。
 * `:has()` 用 `CSS.supports` 探测，不支持就整条规则失效、退化成同步 JS（可能仍有极短闪现）。
 *
 * ### 1.6.6：卡片藏干净了，残留的空壳也要塌掉
 *
 * 用户实测：卡片不闪了，但**偶尔会剩一行小字「查看更多」，或者一条细长空白**。
 * 那是被藏掉的卡片外面的 `ul.card__bd` / `div.card` / `div.topclick-wrap` ——
 * padding、边框、以及里面那行「查看更多」都还在，卡片一没就露出来了。
 *
 * 于是加 `collapse()`：容器内**至少有一张 `li.top-click` 且全部处于「将被隐藏」状态**
 * （已有 `data-hb-hidden`、或命中广告标、或命中安装按钮）→ 塌缩该容器；容器里一旦重新出现
 * 正常卡片就撤销（复活）。三级限制：只作用于 `ul.card__bd` / `.card-list` / `.topclick-wrap` /
 * class 含 `card` 的中间层；上溯最多 3 层；遇根容器或 `.sug-search` 停手。
 * 返回串新增 `fold`（塌缩容器数）与 `more`（页面上「查看更多」类小字入口及其可见性），
 * 下次日志一行就能判断塌缩有没有生效、以及还有没有残留。
 */
internal object SearchSugFeature : Feature(Config.AD_SEARCH_SUG) {

    private const val CLS_SUG_CARD = "com.android.browser.quicksearchbox.data.SugCardData"
    private const val CLS_APP_MGR = "com.android.browser.quicksearchbox.data.RecentAppManager"
    private const val CLS_HOT_SEARCH = "com.android.browser.quicksearchbox.data.HotSearchManager"

    // ---- 诊断用的宿主符号（都已在上表里核对过存在于 20.27）----
    /** 搜索模块 prefs：`getSugUrl()` / `getSugPageUse()` */
    private const val CLS_SUG_PREF = "com.android.browser.search.interaction.settings.SearchModuleKVPrefs"

    /** H5 搜索建议页的 WebView：`queryKeyword(Object)` 把关键字塞给网页 */
    private const val CLS_WEBVIEW = "miui.browser.webview.BrowserWebView"

    /** 新实现（WebView 版搜索建议） */
    private const val CLS_SUG_MGR = "com.android.browser.suggestion.SearchSugManager"

    /** 旧实现（原生搜索建议） */
    private const val CLS_SUG_MGR_OLD = "com.android.browser.SearchSuggestionManager"

    /** 原生建议视图基类；`onUpdate()` 被调说明原生层在刷新 */
    private const val CLS_SUG_VIEW = "com.android.browser.suggestion.BaseSuggestionView"

    /** 广告应用列表（private List<RecentApp>） */
    private const val FIELD_AD_LIST = "mRecentAdAppList"

    /** 真正喂给 UI 的应用列表（private List<RecentApp>） */
    private const val FIELD_APP_LIST = "mRecentAppList"

    /** 候选列表（private List<RecentApp>） */
    private const val FIELD_OPTIONAL_LIST = "mOptionalAppList"

    // ---- 1.6.1：H5 过滤用的宿主符号 ----

    /** `SearchSugManager` 里那条 WebView 字段（private instance，反汇编确认存在） */
    private const val FIELD_WEBVIEW = "mWebView"

    /** sug 页面的 WebViewClient（只用来打印它拦到的请求 URL，辅助定位数据接口） */
    private const val CLS_SUG_CLIENT =
        "com.android.browser.suggestion.SearchSugManager\$initSugWebView\$1\$1"

    /** 主拦点日志只打一次，避免每次搜索框初始化都刷屏 */
    private var shortCircuitLogged = false

    /** 展示层清理只在真改动时打日志，避免每次搜索都刷屏 */
    private var filteredLogged = false

    /** 诊断日志的去重集合：同一条只打一次 */
    private val diagLogged = HashSet<String>()

    // ---- 1.6.1：H5 过滤状态 ----

    /** 已捕获的搜索建议 WebView（就是 `SearchSugManager#mWebView`） */
    @Volatile
    private var sugWebView: Any? = null

    /** 注入结果日志去重（同一条结果只打一次，避免刷屏） */
    private val injectLogged = HashSet<String>()

    /** 只用于把 evaluateJavascript 挪到主线程 */
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun install(cl: ClassLoader) {
        installHooks(cl)
        installDiagnostics(cl)
        installH5Filter(cl)
    }

    private fun installHooks(cl: ClassLoader) {
        // ① 短路 SugCardData#a()，不拉取、不落盘服务端的搜索推荐配置。
        //    1.6.0 更正：它写的那 4 个 KvPrefs key 与广告无关，这条**拦不住广告**，
        //    保留只是因为无副作用（不写宿主 SP，卸载即恢复）。
        Hooks.hook(cl, CLS_SUG_CARD, "a") { p ->
            if (!on()) return@hook
            p.result = null
            if (!shortCircuitLogged) {
                shortCircuitLogged = true
                XLog.v("已短路 $CLS_SUG_CARD#a（只是不刷新卡片配置，与广告无关）")
            }
        }

        // ② 展示层主拦：列表装配完成后，把广告项剔出展示列表
        Hooks.hookAfter(cl, CLS_APP_MGR, "initRecentAppList") { p ->
            if (!on()) return@hookAfter
            val mgr = p.thisObject ?: return@hookAfter

            (Hooks.field(mgr, FIELD_AD_LIST) as? MutableList<Any?>)?.clear()

            val shown = Hooks.field(mgr, FIELD_APP_LIST) as? MutableList<Any?> ?: return@hookAfter
            val hit = shown.count { isAd(it) }
            if (hit > 0) {
                shown.removeAll { isAd(it) }
                if (!filteredLogged) {
                    filteredLogged = true
                    XLog.v("已从应用建议列表移除 $hit 条广告")
                }
            }
        }

        // ③ 展示层兜底：UI 每次取数再过滤一遍
        //    —— 包一层 ArrayList，避免宿主侧 check-cast 到具体实现类时踩空
        Hooks.hookAfter(cl, CLS_APP_MGR, "getRecentAppList") { p ->
            if (!on()) return@hookAfter
            val list = p.result as? List<*> ?: return@hookAfter
            if (list.none { isAd(it) }) return@hookAfter
            p.result = ArrayList(list.filterNot { isAd(it) })
        }

        // ④ 广告热词出口兜底。宿主 20.27 该路径已是废代码（数据源类 HotSearchAdVersionData
        //    在全部 33 个 dex 的 class_defs 里都不存在，只剩悬空引用），留着不亏。
        Hooks.hook(cl, CLS_HOT_SEARCH, "getHotSearchAdList") { p ->
            if (on()) p.result = ArrayList<Any>()
        }
    }

    /**
     * 1.6.0 诊断：判定那三张「安装」卡片出在原生层还是 H5 层。
     * **纯日志，不动任何返回值**；每类只打一次。
     */
    private fun installDiagnostics(cl: ClassLoader) {
        // 下拉是 WebView 还是原生：isNativeSugPage() 要求 getSugPageUse()==1
        Hooks.hookAfter(cl, CLS_SUG_PREF, "getSugPageUse") { p ->
            if (on()) diag("sugPageUse", "sugPageUse=${p.result}（1=native 原生，其他=WebView）")
        }

        // 下拉网页的地址（WebView 模式加载它）
        Hooks.hookAfter(cl, CLS_SUG_PREF, "getSugUrl") { p ->
            if (on()) diag("sugUrl", "sugUrl=${p.result}")
        }

        // 宿主把关键字塞给网页：这条出现 = H5 路径在跑
        Hooks.hook(cl, CLS_WEBVIEW, "queryKeyword") { p ->
            if (on()) diag("queryKeyword", "queryKeyword -> ${p.args.firstOrNull()}")
        }

        // 新旧两套搜索建议实现，看哪个在跑
        Hooks.hook(cl, CLS_SUG_MGR, "querySug") { p ->
            if (on()) diag("querySug", "SearchSugManager.querySug(${p.args.firstOrNull()})")
        }
        Hooks.hook(cl, CLS_SUG_MGR_OLD, "querySuggest") { p ->
            if (on()) diag("querySuggest", "SearchSuggestionManager.querySuggest(${p.args.firstOrNull()})")
        }

        // 原生建议视图真的被刷新 → 卡片可能出在原生层
        Hooks.hook(cl, CLS_SUG_VIEW, "onUpdate") { _ ->
            if (on()) diag("onUpdate", "BaseSuggestionView#onUpdate()（原生建议视图在刷新）")
        }

        // 应用建议链路：跑没跑、里面有没有广告项（现有 ② 只在命中时才打日志，这里补无条件的一次）
        Hooks.hookAfter(cl, CLS_APP_MGR, "initRecentAppList") { p ->
            if (!on()) return@hookAfter
            val mgr = p.thisObject ?: return@hookAfter
            val shown = (Hooks.field(mgr, FIELD_APP_LIST) as? List<*>)?.size ?: -1
            val ad = (Hooks.field(mgr, FIELD_AD_LIST) as? List<*>)?.size ?: -1
            val optional = (Hooks.field(mgr, FIELD_OPTIONAL_LIST) as? List<*>)?.size ?: -1
            diag(
                "appList",
                "initRecentAppList: list=$shown ad=$ad optional=$optional" +
                    "（ad>0 才有兜底可拦；list<=0 说明这条链是空的）"
            )
        }

        // 1.6.1：sug 页面里所有请求的 URL —— 用来找「买卡片数据的那个接口」，
        // 找到后就能在 shouldInterceptRequest 里直接改响应体（比改 DOM 更干净）。
        Hooks.hookAfter(cl, CLS_SUG_CLIENT, "shouldInterceptRequest") { p ->
            if (!on()) return@hookAfter
            val url = p.args.getOrNull(1) as? String ?: return@hookAfter
            val res = p.result
            val extra = if (res == null) {
                "→ null（交给系统默认加载）"
            } else {
                val mime = Hooks.call(res, "getMimeType")
                val code = Hooks.call(res, "getStatusCode")
                "→ 宿主自造响应 mime=$mime code=$code"
            }
            val first = synchronized(diagLogged) { diagLogged.add("url:$url") }
            if (first && urlSeenCount() <= 40) XLog.v("【诊断】H5 请求 $url $extra")
        }
    }

    /** 统计已登记的 URL 数（用于给 URL 日志封顶，避免刷爆日志） */
    private fun urlSeenCount(): Int = synchronized(diagLogged) { diagLogged.count { it.startsWith("url:") } }

    /** 诊断日志去重器：同一条只打一次，避免每次搜索刷屏 */
    private fun diag(key: String, msg: String) {
        val first = synchronized(diagLogged) { diagLogged.add(key) }
        if (first) XLog.v("【诊断】$msg")
    }

    /** 对应宿主 `RecentApp#isAd()`；调用失败一律当作「不是广告」，宁可漏杀不可误杀。 */
    private fun isAd(app: Any?): Boolean = app != null && Hooks.call(app, "isAd") == true

    // ===================== 1.6.1：H5 层过滤 =====================

    /**
     * 捕获搜索建议 WebView，并在每次发起搜索时向页面注入过滤脚本。
     *
     * 依据（反汇编 `SearchSugManager`，classes.dex）：
     * `mWebView:Lmiui/browser/webview/BrowserWebView;` 是 private instance 字段；
     * 宿主自己的 `evaluateSugJS(String)` 就是 `mWebView.evaluateJavascript(js, null)`。
     */
    private fun installH5Filter(cl: ClassLoader) {
        // 1) 页面初始化时把 mWebView 存下来
        Hooks.hookAfter(cl, CLS_SUG_MGR, "initSugWebView") { p ->
            if (!on()) return@hookAfter
            val mgr = p.thisObject ?: return@hookAfter
            val wv = findWebView(mgr) ?: return@hookAfter
            if (sugWebView !== wv) {
                sugWebView = wv
                XLog.i("已捕获搜索建议 WebView，H5 过滤脚本就绪")
            }
            // 1.6.5：此刻页面还没 loadUrl，注入多半落空，但一旦成立 CSS 就比任何时机都早
            inject(wv)
        }

        // 2) 每次把关键字喂给网页之后注入（宿主 querySug 末尾就是 mWebView.queryKeyword(JSON)）
        Hooks.hookAfter(cl, CLS_WEBVIEW, "queryKeyword") { p ->
            if (!on()) return@hookAfter
            val wv = sugWebView ?: return@hookAfter
            if (p.thisObject !== wv) return@hookAfter   // 只处理搜索建议那个 WebView，别碰正常网页
            inject(wv)
        }

        // 3) 兜底：清空输入等路径只调 querySug、不再塞关键字
        Hooks.hookAfter(cl, CLS_SUG_MGR, "querySug") { p ->
            if (!on()) return@hookAfter
            sugWebView?.let { inject(it) }
        }
    }

    /** 分多次注入 —— 页面渲染是异步的，注入太早 observer 还没东西可观察。
     *  1.6.5：前密后疏（0.12/0.32/0.7/1.4/2.6s）—— 第一次落在页面骨架起来之前最好，
     *  CSS 规则一进 DOM，后面渲染出来的卡片就不会再上屏。 */
    private fun inject(wv: Any) {
        for (delay in longArrayOf(0L, 120L, 320L, 700L, 1400L, 2600L)) {
            if (delay == 0L) mainHandler.post { evalJs(wv) }
            else mainHandler.postDelayed({ evalJs(wv) }, delay)
        }
    }

    /** 在主线程执行 JS，并把页面回传的结果打到日志（前缀 `【注入】`） */
    private fun evalJs(wv: Any) {
        try {
            val m = wv.javaClass.methods.firstOrNull {
                it.name == "evaluateJavascript" && it.parameterTypes.size == 2
            } ?: run {
                XLog.v("未找到 WebView#evaluateJavascript，跳过 H5 注入")
                return
            }
            // 第二个参数是 hyper.webkit.ValueCallback，用动态代理实现它（不硬编码类型，避免类加载对不上）
            val cb = runCatching {
                Proxy.newProxyInstance(wv.javaClass.classLoader, arrayOf(m.parameterTypes[1])) { _, method, args ->
                    if (method.name == "onReceiveValue") logInject(args?.firstOrNull() as? String)
                    null
                }
            }.getOrNull()
            m.invoke(wv, JS_FILTER, cb)
        } catch (t: Throwable) {
            XLog.v("H5 注入失败：${t.javaClass.simpleName} ${t.message}")
        }
    }

    /**
     * 从 `SearchSugManager` 上取 WebView：先按字段名 `mWebView`，
     * 取不到就在整个类继承链上按「类型名含 WebView」扫一遍（字段被改名时兜底）。
     */
    private fun findWebView(mgr: Any): Any? {
        (Hooks.field(mgr, FIELD_WEBVIEW))?.let { return it }
        var cls: Class<*>? = mgr.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (!f.type.name.contains("WebView")) continue
                val v = runCatching { f.isAccessible = true; f.get(mgr) }.getOrNull()
                if (v != null) {
                    XLog.v("按类型兜底找到 WebView 字段 ${f.name}（$FIELD_WEBVIEW 已改名？）")
                    return v
                }
            }
            cls = cls.superclass
        }
        return null
    }

    /** 注入结果日志：同一结果只打一次，最多 12 条，避免刷屏 */
    private fun logInject(result: String?) {
        if (result.isNullOrEmpty()) return
        val msg = if (result.length > 900) result.take(900) + "…" else result
        val first = synchronized(injectLogged) {
            if (injectLogged.size >= 12) false else injectLogged.add(msg)
        }
        if (first) XLog.v("【注入】$msg")
    }

    /**
     * 注入到 sug 页面里的脚本。
     *
     * ① 找「广告标记」（class 含 `ads-tag`/`ad-tag`，或自身直接文本恰为「广告/推广/赞助」），
     * ② 用**结构类名** `closest('a.top-click__sug' → 外层 li.top-click)` 定位它所属的那张卡片，
     * ③ 隐藏这张卡；挂 `MutationObserver` 处理异步渲染出来的新卡。
     * ①' 另外独立跑**判据二**（1.6.4）：`li.top-click` 里**存在真实安装按钮**的卡同样隐藏
     *    —— 宿主自己的应用推荐卡不带 `ads-tag`，只有按钮能认出来。
     *
     * 卡片定位**不能**用「最近一个高度 ≥ N 的祖先」：被 `display:none` 的祖先高度会归零，
     * observer 下一轮就从标记往上再爬一层，逐轮级联，最后把 `#app` 整页藏掉
     * —— 1.6.1 真机上广告和搜索联想一起消失，就是这个原因。
     * 现在用 `closest()` + 根容器/列表容器黑名单 + `data-hb-hidden` 幂等标记 + 视口 60% 高度上限兜住。
     * 找不到定位就一条都不动（只隐藏、不删除 DOM），最坏等于没生效。
     * 返回值是 JSON 诊断串（隐藏条数 / 标记数 / 命中路径 / `scout` 结构摘要 / 节点总数）。
     * `scout` 摘出「top-click 区域里每个卡片项」的 class、有无广告标、有无安装/下载按钮、文本开头，
     * 用来判断「没被藏掉的那张卡」到底是**没有广告标记**还是**结构变了**（1.6.3 加）。
     *
     * 注意：这是 Kotlin 原始字符串，`\s` 不做转义，正好是 JS 正则要的形式。
     */
    private const val JS_FILTER = """(function(){try{
var AD=['广告','推广','赞助'];
var CARDSEL='a.top-click__sug,li.top-click,.top-click__sug';
var NEVER=/card-list|card__bd|topclick-wrap|sug-search/i;
function own(e){var s='';for(var i=0;i<e.childNodes.length;i++){var n=e.childNodes[i];if(n.nodeType===3)s+=n.nodeValue;}return s.replace(/\s+/g,'');}
function isAdMark(e){
  if(!e||e.nodeType!==1)return false;
  if(/ads?-?tag|ad-?tag|advert/i.test(String(e.className||'')))return true;
  var s=own(e);
  return s.length>0&&s.length<=6&&AD.indexOf(s)>=0;
}
function isRoot(n){
  if(!n)return true;
  if(n===document.body||n===document.documentElement)return true;
  if(n.id==='app')return true;
  return /^(HTML|BODY)$/i.test(String(n.tagName||''));
}
/* ---- 1.6.5：CSS 规则前置隐藏（消灭「一闪而过」）----
   时序真相：sugCardApi 回来后 Vue 渲染卡片 → 上屏 → 我们的注入才轮到隐藏，中间有空档，
   用户就看到卡片闪一下。改成**注入即铺一条 CSS 规则**：规则一旦进 DOM，同帧内生效，
   之后所有渲染出来的卡片在**首次绘制前**就被 `display:none`，不经过任何 JS 回调。
   只用**结构确定**的选择器，逐条独立（`:has()` 不被支持时只会丢那一条，不影响其它）：
   - `.ads-tag` / `[class*="ads-tag"]` / `[class*="ad-tag"]`：真机已确认广告卡独有，零误杀；
   - `top-click__btn` / `top-click__install` / `top-click__download`：按命名规律猜的按钮类名，
     猜不中就等于没写（不匹配），猜中就顺带把无广告标那张也做到零闪 —— 不会误杀联想项。*/
var CSSSEL='ul.card__bd>li.top-click:has(.ads-tag),li.top-click:has([class*="ads-tag"]),li.top-click:has([class*="ad-tag"]),li.top-click:has([class*="top-click__btn"]),li.top-click:has([class*="top-click__install"]),li.top-click:has([class*="top-click__download"])';
function cssOk(){try{return !!(window.CSS&&window.CSS.supports&&window.CSS.supports('selector(li:has(span))'));}catch(e){return false;}}
/* 第二条规则给空壳塌缩用（1.6.6）：JS 给容器打好 data-hb-empty 属性后由它兜底隐藏 */
var CSSRULE=CSSSEL+'{display:none!important}[data-hb-empty]{display:none!important}';
function installCss(){
  try{
    if(!cssOk())return 0;
    var old=document.getElementById?document.getElementById('hb-css'):null;
    if(old){if(String(old.textContent||'').indexOf('data-hb-empty')<0)old.textContent=CSSRULE;return 1;}
    var s=document.createElement('style');
    s.id='hb-css';s.type='text/css';
    s.appendChild(document.createTextNode(CSSRULE));
    (document.head||document.documentElement).appendChild(s);
    return 1;
  }catch(e){return 0;}
}
function cardOf(m){
  if(m.closest){
    var a=m.closest('a.top-click__sug');
    if(a){var li=a.closest('li.top-click');return li||a;}
    var c0=m.closest(CARDSEL);
    if(c0)return c0;
  }
  var n=m,d=0;
  while(n&&n.parentNode&&d<4){
    n=n.parentNode;d++;
    if(n.nodeType!==1)continue;
    if(isRoot(n))break;
    if(NEVER.test(String(n.className||'')))break;
    var r=n.getBoundingClientRect?n.getBoundingClientRect():null;
    if(r&&r.height>=40&&r.height<=400)return n;
  }
  return null;
}
function safe(c){
  if(isRoot(c))return false;
  if(NEVER.test(String(c.className||'')))return false;
  if(c.getAttribute&&c.getAttribute('data-hb-hidden')==='1')return false;
  var vh=window.innerHeight||0;
  var r=c.getBoundingClientRect?c.getBoundingClientRect():null;
  if(vh&&r&&r.height>vh*0.6)return false;
  return true;
}
function path(e){var a=[],n=e;for(var i=0;i<6&&n&&n.nodeType===1;i++){var c=String(n.className||'').trim().split(/\s+/).slice(0,3).join('.');a.unshift(n.tagName+(n.id?'#'+n.id:'')+(c?'.'+c:''));n=n.parentNode;}return a.join('>');}
function txtOf(e){if(!e)return '';if(e.nodeType===3)return e.nodeValue||'';var s='',k=e.childNodes||[];for(var i=0;i<k.length;i++)s+=txtOf(k[i]);return s;}
function hasMark(e){if(isAdMark(e))return true;var k=e.childNodes||[];for(var i=0;i<k.length;i++){if(k[i].nodeType===1&&hasMark(k[i]))return true;}return false;}
/* ---- 判据二：卡片里有没有「安装/下载」按钮（1.6.4）----
   真机日志证明页面稳定有 3 张推荐卡，但只有 2 张带 ads-tag：剩下那张是宿主自己的
   应用推荐卡，前端不打广告标 —— 只能按「卡内存在真实按钮」认它。
   三级从严：① 按钮特征（BUTTON 标签 / class 含 btn|install|download）+ 常见按钮文案；
             ② own-text 恰是强按钮文案（立即下载/去下载…这类不可能是联想词本身）；
             ③ 排除「整卡文本就等于该词」的情况 —— 否则搜「安装」时那条联想词会被误杀。*/
var BTNEXACT=['安装','下载','立即下载','立即安装','马上下载','去下载','去安装','获取','打开','更新','查看','安装应用','下载应用'];
var BTNSTRONG=['安装','立即下载','立即安装','马上下载','去下载','去安装','安装应用','下载应用'];
function desc(e){var o=[];if(e.getElementsByTagName){var l=e.getElementsByTagName('*');for(var i=0;i<l.length;i++)o.push(l[i]);return o;}(function w(n){var k=n.childNodes||[];for(var i=0;i<k.length;i++){var x=k[i];if(x.nodeType===1){o.push(x);w(x);}}})(e);return o;}
function btnNode(c){
  if(!c||!c.nodeType||c.nodeType!==1)return null;
  var full=txtOf(c).replace(/\s+/g,''),k=desc(c);
  for(var i=0;i<k.length;i++){
    var e=k[i],t=own(e).replace(/\s+/g,'');
    if(!t||t.length>8)continue;
    if(full===t)continue;
    var cls=String(e.className||'');
    if(((e.tagName==='BUTTON')||/btn|button|install|download/i.test(cls))&&BTNEXACT.indexOf(t)>=0)return e;
    if(BTNSTRONG.indexOf(t)>=0)return e;
  }
  return null;
}
function hasBtn(c){return !!btnNode(c);}
function scout(){var o=[],l=document.getElementsByTagName('*');for(var i=0;i<l.length&&o.length<4;i++){var e=l[i];if(!e||e.nodeType!==1)continue;if(String(e.tagName)!=='LI')continue;var c=String(e.className||'');if(!/\btop-click\b/.test(c))continue;var t=txtOf(e).replace(/\s+/g,'');o.push(e.tagName+'['+c.split(/\s+/).slice(0,3).join('.')+'] m='+(hasMark(e)?1:0)+' b='+(hasBtn(e)?1:0)+' "'+t.slice(0,18)+'"');}return o;}
function scan(){var o=[],l=document.getElementsByTagName('*');for(var i=0;i<l.length;i++){if(isAdMark(l[i]))o.push(l[i]);}return o;}
/* 1.6.6 观测：页面里「查看更多」这类小字入口（塌缩之后是否还残留），最多 3 条 */
function vis(n){var p=n;while(p){if(p.style&&p.style.display==='none')return 0;p=p.parentNode;}var r=n.getBoundingClientRect?n.getBoundingClientRect():null;return (!r||r.height>0)?1:0;}
function moreNodes(){
  var o=[],l=document.getElementsByTagName('*');
  for(var i=0;i<l.length&&o.length<3;i++){
    var e=l[i];if(!e||e.nodeType!==1)continue;
    var t=own(e).replace(/\s+/g,'').replace(/[>›»]/g,'');
    if(!t||t.length>6)continue;
    if(!/^(查看更多|查看全部|更多|展开更多)$/.test(t))continue;
    o.push(path(e)+' "'+t+'" v='+vis(e));
  }
  return o;
}
function cards(){var o=[],l=document.getElementsByTagName('*');for(var i=0;i<l.length;i++){var e=l[i];if(!e||e.nodeType!==1)continue;if(String(e.tagName)!=='LI')continue;if(!/\btop-click\b/.test(String(e.className||'')))continue;o.push(e);}return o;}
function hide(c){c.style.display='none';c.setAttribute('data-hb-hidden','1');}
/* ---- 1.6.6：空壳塌缩（残留的「查看更多」小条 / 细长空白）----
   只藏 `li.top-click` 时，外层 `ul.card__bd` / `div.card` / `.topclick-wrap` 的 padding、
   边框，以及里面那行「查看更多」，会留下一条空壳 —— 真机上有时表现为一行小字「查看更多」，
   有时就是一条细小空白。判据：容器内**至少有一张 li.top-click 且全部处于「将被隐藏」状态**
   → 塌缩；一旦容器内重新出现可见卡片 → 立即撤销（复活）。
   ⚠ 必须要求「内部至少 1 张卡」：卡片都还没渲染时就把整层藏掉，列表可能再也不出现。
   ⚠ 只认中间层容器（class 含 card__bd / card-list / topclick-wrap / card 这个词），
     遇根容器或 `.sug-search` 立即停手；上溯最多 3 层（真机结构刚好是 ul.card__bd → div.card
     → div.topclick-wrap，再上一层是匿名 div，不该动）。*/
function cls(n){return String((n&&n.className)||'');}
function isBox(n){
  if(!n||n.nodeType!==1)return false;
  var c=cls(n);
  if(/card__bd|card-list|topclick-wrap/i.test(c))return true;
  return c.split(/\s+/).indexOf('card')>=0;
}
function willHide(c){return c.getAttribute('data-hb-hidden')==='1'||!!hasMark(c)||!!hasBtn(c);}
function cardsIn(n){
  var o=[],l=desc(n);
  for(var i=0;i<l.length;i++){var e=l[i];if(!e||e.nodeType!==1)continue;if(String(e.tagName)!=='LI')continue;if(/\btop-click\b/.test(cls(e)))o.push(e);}
  return o;
}
function collapse(){
  var k=0,cs=cards();
  for(var i=0;i<cs.length;i++){
    var n=cs[i].parentNode,d=0;
    while(n&&n.nodeType===1&&d<3){
      if(isRoot(n))break;
      if(/sug-search/i.test(cls(n)))break;
      if(isBox(n)){
        var inner=cardsIn(n),all=inner.length>0;
        for(var j=0;j<inner.length;j++){if(!willHide(inner[j])){all=false;break;}}
        if(all){
          if(n.getAttribute('data-hb-empty')!=='1'){n.setAttribute('data-hb-empty','1');n.style.display='none';k++;}
        }else if(n.getAttribute('data-hb-empty')==='1'){
          n.removeAttribute('data-hb-empty');n.style.display='';
        }
      }
      n=n.parentNode;d++;
    }
  }
  return k;
}
function apply(){
  var k=0,m=scan();
  for(var i=0;i<m.length;i++){var c=cardOf(m[i]);if(c&&safe(c)){hide(c);k++;}}
  var b=0,cs=cards();
  for(var j=0;j<cs.length;j++){var c2=cs[j];if(!safe(c2))continue;if(!hasBtn(c2))continue;hide(c2);b++;}
  var f=collapse();
  return [k,b,f];
}
var cssOn=installCss();
var hits=[],bhits=[],m0=scan();
for(var i=0;i<m0.length&&hits.length<4;i++){var c=cardOf(m0[i]);hits.push((c?'':'[未定位]')+path(c||m0[i]));}
var cs0=cards();
for(var q=0;q<cs0.length&&bhits.length<4;q++){if(hasBtn(cs0[q]))bhits.push('[按钮]'+path(cs0[q]));}
var r0=apply(),hidden=r0[0],btnH=r0[1],fold=r0[2];
window.__hbApply=apply;
/* 1.6.5：observer 回调里**同步** apply，不 debounce。
   MutationObserver 回调跑在 microtask，浏览器会在**本帧绘制前**清空 microtask 队列，
   所以在这里同步改 style 能赶在首次绘制之前生效 —— 那 150ms 的防抖窗口正是「闪一下」的来源。*/
if(!window.__hbOb&&window.MutationObserver){window.__hbOb=new MutationObserver(function(recs){var a=0,k=recs?recs.length:0;for(var i=0;i<k;i++){var ns=recs[i].addedNodes||[];a+=ns.length;}if(!a)return;installCss();apply();});window.__hbOb.observe(document.documentElement,{childList:true,subtree:true});}
return JSON.stringify({hidden:hidden,btn:btnH,fold:fold,marks:m0.length,hits:hits,bhits:bhits,scout:scout(),more:moreNodes(),css:cssOn,ob:!!window.__hbOb,n:document.getElementsByTagName('*').length,href:location.href});
}catch(e){return JSON.stringify({err:String(e)});}})()"""
}
