package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.XLog
import com.hupan.hookbrowser.Xp
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 搜索引擎接管：把 bing / google / yandex 注入宿主引擎数据，接管首页切换栏。
 *
 * ## 宿主的搜索链路（classes.dex，20.27.1010901 实测反汇编）
 *
 * ```
 * 模板（服务端 searchengine.json / 本地兜底 res/raw/local_search_engine.json，占位符 {searchTerms}）
 *   → SearchEngineDataProvider.mEngineSet.searchBox[引擎名].searchUrl
 *   → SearchEngineInfo.mSearchEngineData  ← getSearchEngineContentByScene(名字, 场景)
 *   → SearchEngineInfo.searchUri() = mSearchEngineData[3]
 *   → getFormattedUri(): template.replaceAll("{searchTerms}", URLEncoder.encode(query,"UTF-8"))
 * ```
 *
 * ## 首页切换栏（搜索框下方那排）的真实链路
 *
 * ```
 * EngineTabsManager#buildEngineList
 *   ├─ getAllSearchEngines()  ← EngineTabsConfig#getAllSearchEngines
 *   │     ← getSearchEngineList(true, "browserSearchBox")
 *   │         ├─ getSearchEngines(scene)      ← 遍历 searchBoxOrder，逐个查 searchBox 是否命中
 *   │         ├─ getRealSearchEngineLabels()  ← 每个名字过 getItemTitle(name)
 *   │         └─ 过滤：displayCustom && isCustomSearchEngineDisplay() 为假时丢掉"自定义引擎"
 *   ├─ [AI 搜索开] sortWithDefaultFirst(list, currentTitleTabEngineName)
 *   └─ [AI 搜索关] buildDefaultFixedOrderList(list)
 *         ← **硬编码顺序数组 {onesearch, baidu, douyin, red}**，白名单外的引擎全部丢弃
 *   → EngineTabAdapter#setEngines() → initSelectedState()
 *         ← getDefaultSearchEngineNameByScene("browserSearchBox")
 *             ← mEngineSet.defaultSearchEngine["browserSearchBox"]   ← **栏内高亮看这个**
 * ```
 *
 * 图标：`EngineTabsConfig` 里硬编码白名单（baidu / sogou / sm / 360 / toutiao / red / douyin /
 * onesearch / google / ai_search 各有专属 res）。白名单里**没有 bing / yandex**，
 * 所以这两个走宿主的「自定义引擎」通道（通用图标 `ic_search_engine_tab_custom`）。
 *
 * ## 1.7.0 的三条关键修正（1.6.9 栏里出不来的真因）
 *
 * 1. **`searchBoxOrder` 必须一起补**。`getSearchEngines()` 是 `searchBoxOrder` 驱动 + `searchBox`
 *    校验（`containsKey` 不通过就跳过），只往 `searchBox` 里塞、不补 `searchBoxOrder` = 等于没塞。
 * 2. **`buildDefaultFixedOrderList` 有硬编码白名单**，非 AI 搜索时用它排序 → bing/google/yandex
 *    到这里被整批丢掉。挂 after 把模块引擎捞回来（宿主自有项顺序不变）。
 * 3. **栏内高亮读的是 `mEngineSet.defaultSearchEngine["browserSearchBox"]`**，不是「当前引擎」。
 *    只写 `SearchModuleSettings.setSearchEngineName` 不会让栏里选中默认项，两个都要写。
 *
 * 另外 `isCustomSearchEngineDisplay()` 为假时，`getSearchEngineList` 会把自定义引擎过滤掉
 * （bing/yandex 正是自定义引擎），所以一并置真。
 *
 * ## 为什么不替换 URL 出口
 *
 * 引擎数据注入 `searchBox` 后，`isEngineLoad` / `getSearchEngineContentByScene` / 宿主自己拼 URL
 * 全部自洽（`getSearchEngineMapByScene("browserSearchBox")` 返回的就是 `searchBox` 那份），
 * **任何出口都不需要改**。1.6.8 那种「无条件替换 8 个出口」会让切换栏怎么点都只出 bing，
 * 1.7.0 已彻底删除；只在「全屏搜索页」保留一个仅对模块引擎生效的兜底
 * （`FullSearchActivity#buildSearchUrl` 读的是服务端 QSB JSON 配置，里面不一定有 bing）。
 */
internal object SearchEngineFeature : Feature(Config.UI_SEARCH_ENGINE) {

    private const val PROVIDER = "com.android.browser.search.SearchEngineDataProvider"
    private const val ENTITY_ENGINE = "com.android.browser.search.SearchEnginesEntity\$SearchEngine"
    private const val MODULE_SETTINGS = "com.android.browser.search.interaction.settings.SearchModuleSettings"
    private const val KV_PREFS = "com.android.browser.search.interaction.settings.SearchModuleKVPrefs"
    private const val ENGINE_INFO = "com.android.browser.search.SearchEngineInfo"
    private const val FULL_SEARCH = "com.android.browser.fullsearch.FullSearchActivity"
    private const val TABS_CONFIG = "com.android.browser.toolbar.EngineTabsConfig"
    private const val TABS_MANAGER = "com.android.browser.toolbar.EngineTabsManager"

    /** 切换栏所属场景；宿主其它场景（热榜 / 搜索发现 / widget）各有独立 map，不在此处接管 */
    private const val SCENE_BOX = "browserSearchBox"

    @Volatile
    private var loader: ClassLoader? = null

    /** 每个进程只把「默认引擎」写进宿主当前引擎一次，之后完全交给用户的切换 */
    private val seeded = AtomicBoolean(false)

    override fun install(cl: ClassLoader) {
        loader = cl

        // ① 数据重建（服务端 30 分钟刷新 / 首次加载）后立刻补齐
        Hooks.hookAfter(cl, PROVIDER, "initEngineSet") { p ->
            inject(p.thisObject)
        }

        // ② 出口兜底：宿主读 searchBoxOrder 之前先把我们的 key 补进去（否则本轮拿不到）
        Hooks.hook(cl, PROVIDER, "getSearchEngines") { p ->
            if (on() && arg(p.args, 0) == SCENE_BOX) inject(p.thisObject)
        }

        // ③ 顺序：模块引擎排前面，宿主自有项（全网 / 抖音 / 用户自建）原顺序跟在后面
        Hooks.hookAfter(cl, PROVIDER, "getSearchEngines") { p ->
            if (!on() || arg(p.args, 0) != SCENE_BOX) return@hookAfter
            val arr = p.result as? Array<*> ?: return@hookAfter
            p.result = orderArray(arr)
        }

        // ④ bing / yandex 走宿主「自定义引擎」通道，栏里才会出现
        //    用 after（不是 before）：宿主原逻辑里带 initEngineSet()，不能短路掉
        Hooks.hookAfter(cl, PROVIDER, "isCustomEngine") { p ->
            if (on() && SearchEngines.needsCustomSlot(arg(p.args, 0))) p.result = true
        }

        // ⑤ 自定义引擎开关：为假时 getSearchEngineList 会把自定义引擎整批过滤掉
        Hooks.hookAfter(cl, KV_PREFS, "isCustomSearchEngineDisplay") { p ->
            if (on()) p.result = true
        }

        // ⑥ 文字出口
        Hooks.hookAfter(cl, PROVIDER, "getItemTitle") { p ->
            val label = SearchEngines.labelOf(arg(p.args, 0)) ?: return@hookAfter
            p.result = label
        }
        Hooks.hookAfter(cl, PROVIDER, "getCurrentEngineTitle") { p ->
            val key = Hooks.call(p.thisObject, "getSearchEngine") as? String
            val label = SearchEngines.labelOf(key) ?: return@hookAfter
            p.result = label
        }
        Hooks.hookAfter(cl, ENGINE_INFO, "getLabel") { p ->
            val key = Hooks.field(p.thisObject, "mName") as? String
            val label = SearchEngines.labelOf(key) ?: return@hookAfter
            p.result = label
        }

        // ⑦ 栏里顺序：模块引擎排第一
        Hooks.hookAfter(cl, TABS_CONFIG, "getAllSearchEngines") { p ->
            if (!on()) return@hookAfter
            val list = p.result as? List<*> ?: return@hookAfter
            p.result = reorder(list)
        }

        // ⑧ 真正卡住 bing/google/yandex 的地方：宿主硬编码 {onesearch,baidu,douyin,red} 的白名单
        Hooks.hookAfter(cl, TABS_MANAGER, "buildDefaultFixedOrderList") { p ->
            if (!on()) return@hookAfter
            val all = p.args.getOrNull(0) as? List<*> ?: return@hookAfter
            val host = p.result as? List<*> ?: emptyList<Any?>()
            p.result = mergeMine(all, host)
        }

        // ⑨ 简洁首页（isSimpleHome）时，宿主会把「自定义引擎且非当前引擎」的项**再追加一遍**
        //    （bing/yandex 正是自定义引擎）→ 这里按 id 去重，保留先出现的位置
        Hooks.hookAfter(cl, TABS_MANAGER, "buildEngineList") { p ->
            if (!on()) return@hookAfter
            val list = p.result as? List<*> ?: return@hookAfter
            if (list.size < 2) return@hookAfter
            val seen = HashSet<String>()
            val out = ArrayList<Any?>(list.size)
            for (e in list) {
                val id = Hooks.call(e, "getId") as? String
                if (id != null && !seen.add(id)) continue
                out.add(e)
            }
            if (out.size != list.size) {
                p.result = out
                XLog.v("切换栏去重：${list.size} → ${out.size}")
            }
        }

        // ⑩ 唯一保留的 URL 兜底：全屏搜索页读的是服务端 QSB JSON，里面不一定有模块引擎
        Hooks.hookAfter(cl, FULL_SEARCH, "buildSearchUrl") { p ->
            val key = arg(p.args, 0)
            if (!on() || !SearchEngines.isModule(key)) return@hookAfter
            val def = SearchEngines.exact(key) ?: return@hookAfter
            p.result = def.url(arg(p.args, 1))
        }
    }

    // ---------------------------------------------------------------- 注入

    /**
     * 幂等补齐 `searchBox` + `searchBoxOrder` + `defaultSearchEngine`，
     * 并在首次成功时把默认引擎写进宿主「当前引擎」。
     */
    private fun inject(provider: Any?) {
        if (!on() || provider == null) return
        val set = Hooks.field(provider, "mEngineSet") ?: return
        @Suppress("UNCHECKED_CAST")
        val box = Hooks.field(set, "searchBox") as? MutableMap<Any?, Any?> ?: return

        for (def in SearchEngines.all()) {
            if (box.containsKey(def.key)) continue
            val item = newEntity(def) ?: continue
            box[def.key] = item
            XLog.i("注入搜索引擎 ${def.key}（${def.label}）")
        }
        ensureOrder(set)
        setSceneDefault(set, provider)
        seedCurrentEngine(provider)
    }

    /** `getSearchEngines()` 是 searchBoxOrder 驱动 + searchBox 校验，两个都要有 */
    private fun ensureOrder(set: Any?) {
        val cur = Hooks.field(set, "searchBoxOrder") as? List<*>
        val ids = ArrayList<String>()
        cur?.forEach { (it as? String)?.takeIf { s -> s.isNotBlank() }?.let(ids::add) }
        var changed = cur == null
        for (def in SearchEngines.all()) {
            if (ids.contains(def.key)) continue
            ids.add(def.key)
            changed = true
        }
        if (!changed) return
        // 宿主传进来多半是 ArrayList；不确定是否可变，直接换成一个我们自己的 ArrayList 更稳
        Hooks.setField(set, "searchBoxOrder", ids)
        XLog.v("补齐 searchBoxOrder = $ids")
    }

    /**
     * 栏内高亮读 `defaultSearchEngine["browserSearchBox"]`，与「当前引擎」是两份状态。
     *
     * 取值规则：当前引擎本身是模块引擎 → 就用它（用户/模块选定的那个高亮）；
     * 否则（还没 seed 成功、或用户选了宿主自带引擎）→ 用模块的默认引擎。
     * 服务端每次重建数据会把这张表重置，所以这里每次都对齐一次。
     */
    private fun setSceneDefault(set: Any?, provider: Any) {
        val cur = currentKey(provider)
        val target = if (SearchEngines.isModule(cur)) cur else SearchEngines.defaultKey()
        if (target == null) return
        @Suppress("UNCHECKED_CAST")
        val map = Hooks.field(set, "defaultSearchEngine") as? MutableMap<Any?, Any?> ?: return
        if (map[SCENE_BOX] == target) return
        map[SCENE_BOX] = target
        XLog.v("默认搜索引擎 $SCENE_BOX → $target（切换栏高亮）")
    }

    /** 构造 `SearchEnginesEntity$SearchEngine`（无参构造 + setter，字段语义与本地 JSON 一致） */
    private fun newEntity(def: SearchEngines.Def): Any? {
        val cl = loader ?: return null
        val item = Xp.newInstance(Xp.findClass(ENTITY_ENGINE, cl)) ?: return null
        return runCatching {
            Xp.call(item, "setSearchEngineName", def.key)
            Xp.call(item, "setSearchUrl", def.template)
            Xp.call(item, "setChannelNo", def.channel)
            Xp.call(item, "setIconUrl", def.iconUrl)
            Xp.call(item, "setTitle_zh_CN", def.label)
            Xp.call(item, "setTitle_zh_TW", def.label)
            Xp.call(item, "setTitle_en_US", def.label)
            Xp.call(item, "setShowIcon", true)
            item
        }.onFailure {
            XLog.v("构造引擎项 ${def.key} 失败：${it.javaClass.simpleName} ${it.message}")
        }.getOrNull()
    }

    /** 把「默认引擎」落到宿主当前引擎上（仅首次；之后尊重用户在切换栏里的选择） */
    private fun seedCurrentEngine(provider: Any) {
        val target = SearchEngines.defaultKey()
        if (currentKey(provider) == target) return
        if (!seeded.compareAndSet(false, true)) return
        val cl = loader
        val cls = if (cl != null) Xp.findClass(MODULE_SETTINGS, cl) else null
        val settings = Xp.staticField(cls, "INSTANCE")
        val ok = settings != null && runCatching {
            Xp.call(settings, "setSearchEngineName", target)
        }.isSuccess
        if (ok) {
            XLog.v("默认搜索引擎=$target（已写入宿主当前引擎，实际搜索与切换栏一致）")
        } else {
            seeded.set(false)
            XLog.v("写入宿主当前引擎失败，稍后重试")
        }
    }

    private fun currentKey(provider: Any?): String? =
        Hooks.call(provider, "getSearchEngine") as? String

    // ---------------------------------------------------------------- 列表

    /** 引擎 id 数组：模块引擎按模块顺序排前，宿主自有项保持原相对顺序跟在后面 */
    private fun orderArray(arr: Array<*>): Array<String> {
        val ids = ArrayList<String>(arr.size)
        for (e in arr) {
            val s = e as? String ?: continue
            if (s.isNotBlank() && !ids.contains(s)) ids.add(s)
        }
        val mine = SearchEngines.orderedKeys().filter { ids.contains(it) }
        val rest = ids.filter { !SearchEngines.isModule(it) }
        return (mine + rest).toTypedArray()
    }

    /** 栏里顺序：模块引擎按模块顺序排前，宿主项目保持原顺序 */
    private fun reorder(list: List<*>): List<Any?> {
        val order = SearchEngines.orderedKeys().toList()
        val mine = ArrayList<Any?>()
        val rest = ArrayList<Any?>()
        for (e in list) {
            val id = Hooks.call(e, "getId") as? String
            if (id != null && order.contains(id)) mine.add(e) else rest.add(e)
        }
        mine.sortBy { order.indexOf(Hooks.call(it, "getId") as? String ?: "") }
        return mine + rest
    }

    /**
     * `buildDefaultFixedOrderList` 的白名单只认 {onesearch,baidu,douyin,red}，
     * 模块引擎会在这里被整批丢掉 —— 从**入参**（getAllSearchEngines 的完整列表）里捞回来。
     */
    private fun mergeMine(all: List<*>, host: List<*>): List<Any?> {
        val hostIds = host.mapNotNull { Hooks.call(it, "getId") as? String }
        val out = ArrayList<Any?>()
        for (id in SearchEngines.orderedKeys()) {
            if (hostIds.contains(id)) continue
            all.firstOrNull { (Hooks.call(it, "getId") as? String) == id }?.let(out::add)
        }
        return out + host
    }

    private fun arg(args: Array<Any?>, index: Int): String =
        (args.getOrNull(index) as? String).orEmpty()
}

/**
 * 可选引擎清单。
 *
 * 模板必须沿用宿主的占位符 `{searchTerms}` —— 宿主拿到模板后自己
 * `URLEncoder.encode(query,"UTF-8")` 再 `replaceAll`（见 `SearchEngineInfo#getFormattedUri`）。
 * 模板里出现多次 `{searchTerms}` 也没问题（bing 的 `pq` 就是第二处），`String.replace` 会全替换。
 */
internal object SearchEngines {

    const val BING = "bing"
    const val GOOGLE = "google"
    const val YANDEX = "yandex"
    const val BAIDU = "baidu"

    /** 默认值，与 prefs.xml 的 `app:defaultValue` + arrays.xml 的 `search_engine_values` 保持一致 */
    const val DEFAULT = BING

    /**
     * 单个引擎定义。
     *
     * @param key      引擎 id（= `searchBox` 的 map key，也是宿主写进「当前引擎」的值）
     * @param label    显示名（栏里、标题栏、设置页都用它）
     * @param template URL 模板，`{searchTerms}` 占位
     * @param channel  渠道号（写进 `channelNo`，只影响埋点）
     * @param iconUrl  图标地址（宿主只在初始化时抓一次，通常抓不到，留空即可）
     */
    class Def(
        val key: String,
        val label: String,
        val template: String,
        val channel: String,
        val iconUrl: String = ""
    ) {
        /** query → 最终可打开的 URL（编码方式与宿主一致：UTF-8 的 URLEncoder） */
        fun url(query: String): String =
            template.replace("{searchTerms}", URLEncoder.encode(query, "UTF-8"))
    }

    /**
     * bing 模板取自宿主自带的 `searchEngineDefines`（`res/raw/local_search_engine.json`），
     * 只去掉了里面写死的会话号 `cvid`。baidu 保留宿主原生渠道号 `from=1012852q`。
     */
    private val ALL: Map<String, Def> = linkedMapOf(
        BING to Def(
            BING,
            "必应",
            "https://cn.bing.com/search?q={searchTerms}&pq={searchTerms}&qs=n&sc=5-4&sp=-1&form=QBLH",
            "0"
        ),
        GOOGLE to Def(GOOGLE, "Google", "https://www.google.com/search?q={searchTerms}", "0"),
        YANDEX to Def(YANDEX, "Yandex", "https://yandex.com/search/?text={searchTerms}", "0"),
        BAIDU to Def(BAIDU, "百度", "https://m.baidu.com/s?from=1012852q&word={searchTerms}", "1012852q")
    )

    /**
     * 宿主 `EngineTabsConfig` 白名单里已有专属图标资源的引擎 —— 留在白名单内，别抢到 custom 通道。
     * bing / yandex 宿主没有资源，只能借宿主自带的通用「自定义引擎」图标。
     */
    private val NATIVE_ICON_SLOT = setOf(BAIDU, GOOGLE)

    fun all(): List<Def> = ALL.values.toList()

    fun exact(key: String?): Def? = ALL[key]

    fun of(key: String?): Def? = ALL[key] ?: ALL[DEFAULT]

    fun labelOf(key: String?): String? = ALL[key]?.label

    fun isModule(key: String?): Boolean = key != null && ALL.containsKey(key)

    /** 是否需要走宿主的「自定义引擎」通道（栏里才显示得出来） */
    fun needsCustomSlot(key: String?): Boolean = isModule(key) && key !in NATIVE_ICON_SLOT

    fun defaultKey(): String = ALL[Config.searchEngineTarget()]?.key ?: DEFAULT

    /** 栏里顺序：默认引擎打头，其余保持固定次序 */
    fun orderedKeys(): Array<String> {
        val def = defaultKey()
        return (listOf(def) + ALL.keys.filter { it != def }).toTypedArray()
    }
}
