package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.XLog

/**
 * 「无法访问」错误页的去广告。
 *
 * ## 这条链路长什么样
 *
 * 错误页不是普通网页：它是**混合页** —— native chromium 把 HTML 模板
 * （`res/raw/miuichromium_error_page.html`，119 KB 单行）读出来交给 WebView 渲染，
 * 页面里的 JS 再通过 JavaScriptInterface 回调宿主拿数据：
 *
 * ```
 * JS: window.MiHybrid.send("nativechannel://getDefaultPageInfo")
 *      ↓ JavaScriptInterface 注册名 "MiHybrid"
 * 宿主: HybridActionDispatcher#send(String)  →  转调 call(String)
 *      →  按 URL host 查 HybridActionInjector 注册表拿到 IAction
 *      →  action.dealAction(bean, webView)  → 返回值就是 JS 拿到的字符串
 * ```
 *
 * 四个 action 全部**直接继承 Object 并实现 IAction**（不是 `HybridAction` 子类），
 * 所以在 `call()` 里走的是「**当前线程直接 dealAction 并返回其结果**」那一支，
 * 而不是 `mMainHandler.post(...)` 那一支 —— 返回值即 JS `JSON.parse` 的输入。
 *
 * ## 热搜榜是怎么冒出来的
 *
 * 模板 JS 先取开关数据，再决定渲染什么：
 *
 * ```js
 * e = JSON.parse(o.excuteClientAction("getDefaultPageInfo"));
 * d = e.searchEnabled;                                  // 搜索总开关
 * _ = e.isHotSearchAddImport || !1;                     // ← 热搜榜专属开关
 * if (!d) return 0;                                     // 搜索整体关闭则连按钮都不加
 * ...
 * 0 === e.sceneType && e.realTimeHotSpotSwitch && ((_ ? c : n)(), E = 0);
 * 1 === e.sceneType && e.guessYouWantSwitch     && (猜你想搜数据, E = 1, 空则回落到 (_ ? c : n)());
 * ```
 *
 * 渲染分支（`d && ["a","c"].includes(type) && 0 < l.length` 才 append）：
 *
 * | `_`     | `E` | 分支                                             |
 * |---------|-----|--------------------------------------------------|
 * | `true`  | 任意 | `hot_search_list` / 标题「热搜榜」/ `newJump`  → 跳大米搜索 |
 * | `false` | `0`  | `search_result` / 标题「搜索发现」/ `jump`       |
 * | `false` | `1`  | `want_search` / 标题「猜你想搜」/ `jump`         |
 *
 * 也就是说 **`_` 是「热搜榜」的唯一开关**：它由宿主
 * `GetDefaultPageInfoAction#dealAction` 里的 `KvPrefs.u5()` 决定，而 `u5()` 读的是宿主
 * 为「热搜榜导入」下发的配置，默认开 —— 于是每一张网络错误页底部都会挂一条推广榜单。
 *
 * ## 这里怎么做
 *
 * 不拦 action 调用本身（那会连带 UI 一起废掉），而是走**第 2 条兜底链路**：
 * 挂 `HybridActionDispatcher#call(String)`，只在返回值上把 `isHotSearchAddImport` 改成
 * false，其余字段原样保留。之后模板 JS 自己就会：
 *
 * - 渲染「搜索发现」（`searchbarkeywords`）/「猜你想搜」（`predictWords`）—— 这两条是
 *   真正的搜索建议，属于错误页的基础功能，不动；
 * - 「热搜榜」永远进不去 `_ ? …` 的真分支。
 *
 * **为什么不用 `ErrorPageResource#getLoadErrorPageContent()` 改写 HTML 更省事**：那是
 * `webviewchromium` 里的**静态**方法（`access=0x9`），LSPosed 对非 boot 类的方法 hook
 * 同样有效，但那份 HTML 是 119 KB 单行模板，正则改字符串一旦宿主换版就是静默失效；
 * 而且模板里三个渲染分支的文字是硬编码中文，改错了直接白页。改返回值只动一个布尔。
 *
 * 这一层是**兜底**：只要 `getDefaultPageInfo` 的返回值变了，JS 就渲染不出热搜榜，
 * 与「宿主是不是把 action 交给主线程处理」无关，所以不依赖 `HybridAction` 的判定分支。
 */
internal object ErrorPageFeature : Feature(Config.AD_ERROR_PAGE_HOT) {

    private const val CLS_DISPATCHER = "com.android.browser.hybrid.HybridActionDispatcher"

    /** 取开关数据的 action：它返回的 `isHotSearchAddImport` 就是热搜榜总闸 */
    private const val ACTION_DEFAULT_PAGE = "getDefaultPageInfo"

    /** 字段名与宿主 `GetDefaultPageInfoAction$Result` 一致，见 docs/宿主20.27-hook目标核对.md */
    private const val FIELD_HOT_IMPORT = "isHotSearchAddImport"

    override fun install(cl: ClassLoader) {
        Hooks.hookAfter(cl, CLS_DISPATCHER, "call") { p ->
            if (on()) patch(p.args, p.result)?.let { p.result = it }
        }
    }

    /**
     * 判定并改写。返回 null 表示「不插手」：
     * 不是这个 action、返回值不是字符串、没有目标字段、或本来就已经是 false。
     *
     * 单独抽出来是为了让 install 里的回调只剩一行 —— `Hooks.SafeHook` 会把回调异常吞掉，
     * 逻辑越短越不容易在宿主换版后静默走错分支。
     */
    private fun patch(args: Array<Any?>?, result: Any?): String? {
        // call(String): String，只有一个参数，认准它免得被将来新增的重载误伤
        if (args == null || args.size != 1) return null
        val action = args[0] as? String ?: return null
        if (!action.contains(ACTION_DEFAULT_PAGE)) return null

        val json = result as? String ?: return null
        val patched = stripHotSearchImport(json) ?: return null
        XLog.v("错误页：已摘掉热搜榜开关（${json.length} → ${patched.length} 字节）")
        return patched
    }

    /**
     * 把 `isHotSearchAddImport` 改成 `false`。
     *
     * 用字符串替换而不是 `JSONObject` 重排：宿主返回的是 `yc/b#k()` 序列化的 JSON，
     * 字段顺序与 JS 侧的读取顺序无关，但**不重排**能把风险压到最低 —— 解析失败了
     * 也只是「这一次没拦到」，不会把整份数据变成 `{}` 让页面白掉。
     *
     * 返回 null 表示不改（没这个字段 / 本来就已经是 false）。
     */
    private fun stripHotSearchImport(json: String): String? {
        val key = "\"$FIELD_HOT_IMPORT\""
        val i = json.indexOf(key)
        if (i < 0) return null

        var j = i + key.length
        // 跳过 key 与 value 之间的空白与冒号
        while (j < json.length && (json[j] == ' ' || json[j] == ':' || json[j] == '\t')) j++
        val rest = json.substring(j)
        return when {
            rest.startsWith("!0") -> json.substring(0, j) + "!1" + rest.substring(2)
            rest.startsWith("true") -> json.substring(0, j) + "false" + rest.substring(4)
            rest.startsWith("1,") || rest.startsWith("1}") ->
                json.substring(0, j) + "0" + rest.substring(1)
            else -> null
        }
    }
}
