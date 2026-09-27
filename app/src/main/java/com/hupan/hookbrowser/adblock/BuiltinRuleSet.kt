package com.hupan.hookbrowser.adblock

import android.content.Context
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.XLog
import java.util.UUID

/**
 * 内置的**推荐规则集**（1.15.13）。
 *
 * ## 为什么要有它
 *
 * 1.15.11 之前，元素隐藏这条通道只有两个来源：用户导入的规则集 + 小米宿主下发的规则库。
 * 真机实测两边都不覆盖「正规网站的标准广告位」：
 *
 * - 宿主那 1,533 条 `##` 全是长尾站点专用（博彩 / 悬浮 / 小说站）；
 * - 用户不导规则时，模块等于只有 1.15.11 内置的那 33 条通用类名。
 *
 * 而中文社区里覆盖最广、最贴合「搜索页 / 资讯页推广位」的两份规则是
 * **EasyList China** 与 **ADgk**（合并去重后 2.8 万条：请求拦截 16,945 + 元素隐藏 9,677）。
 * 让每个用户自己去找、去下载、去导入才有效果，是产品缺陷 —— 所以把它们放进 APK，
 * 由一个开关控制（[Config.AD_BUILTIN_RULES]，默认关：2.8 万条会占用规则库与内存，
 * 属于「用户主动选择」而不是「默认替他决定」）。
 *
 * ## 形态：assets 文件，不是 Kotlin 常量
 *
 * 2.8 万条写成源码常量会撑成 MB 级 `.kt`，编译与 IDE 都受罪；放 `assets/` 后
 * APK 里是 gzip 存放（实测 904 KB → 331 KB），Android 读取时自动解压。
 *
 * ## 落点：写进**规则库**（`adrules` 组），而不是新开一条跨进程通道
 *
 * 内置规则最终要参与匹配的是宿主进程，而宿主读不到模块的 assets。写进规则库后：
 * 复用已经验证过的 [AdRuleStore] / [AdRuleChannel] 链路、规则管理页里能看见能启停、
 * `verify_static.py` 也不用为它加一套新的 key 校验。代价是它占规则库容量 ——
 * 这正是默认关、并且要用户自己点头的原因。
 *
 * ## 版本识别：`source` 里带上内容哈希
 *
 * [SOURCE] 形如 `builtin:recommended#eb6f5bec`。模块升级后若规则内容变了，
 * [VERSION] 跟着变 → [sync] 发现现有组的 `source` 与当前 [SOURCE] 不一致 → 替换。
 * 这样「内置规则随模块版本更新」不需要任何额外的存储字段。
 *
 * ## ⚠ 进程边界
 *
 * 本对象读模块自己的 `assets/`，**只能在模块进程调用**。宿主进程里 `ctx.assets`
 * 指向小米浏览器自己的 APK，会读不到文件（[sync] 会返回 `failed`，不会崩）。
 * 所以 hook 侧（`features/` / `MainHook`）不要引用它 —— 内置规则的生效路径是
 * 「模块进程写入规则库 → 宿主进程从 remote preferences 读」，中间不需要互相调用。
 */
internal object BuiltinRuleSet {

    /** assets 里的文件名 */
    private const val ASSET = "builtin-rules.txt"

    /**
     * 内置规则正文的 **sha1 前 8 位**。
     *
     * ⚠ 改 `assets/builtin-rules.txt` 必须同步改这里（`verify_static.py` 会核对，
     * 不一致时界面上的「已是最新」判断和实际内容会对不上）。
     */
    const val VERSION = "eb6f5bec"

    /** source 前缀；[SOURCE] = 前缀 + `#` + [VERSION]。用它判断「是不是内置那一组」 */
    private const val SOURCE_PREFIX = "builtin:recommended"

    /** 规则管理页显示的名字 */
    const val NAME = "内置推荐规则（EasyList China + ADgk）"

    /** 当前版本对应的 source 标识 */
    val SOURCE: String get() = "$SOURCE_PREFIX#$VERSION"

    /**
     * 按开关把规则库同步到「该有 / 该没有」的状态。
     *
     * - 开关开 → 没有就装、版本旧就替换（**保留用户对该组的启停状态**）；
     * - 开关关 → 有就移除。
     *
     * 幂等：无事可做时直接返回。**必须在后台线程调用**（读 assets + 解析 2.8 万条 + 落盘）。
     *
     * 返回给日志/界面用的一句话；无事可做返回空串。
     */
    fun sync(ctx: Context): String {
        val sets = runCatching { AdRuleStore.load(ctx) }.getOrNull() ?: return ""
        val existing = sets.firstOrNull { it.source.startsWith(SOURCE_PREFIX) }

        if (!wanted(ctx)) {
            if (existing == null) return ""
            sets.remove(existing)
            if (!AdRuleStore.save(ctx, sets)) return "内置规则已关闭，但规则库写入失败"
            XLog.i(
                "【内置规则】开关已关闭，已从规则库移除" +
                    "（${existing.rules.size} 条拦截 + ${existing.elementRules.size} 条隐藏）"
            )
            return "已移除内置规则集（${existing.rules.size} + ${existing.elementRules.size} 条）"
        }

        // 已是当前版本：什么都不做（用户可能停用过它，不要擅自改回启用）
        if (existing != null && existing.source == SOURCE) return ""

        val text = readAsset(ctx) ?: return "内置规则读取失败（assets/$ASSET 缺失）"
        val result = AdRuleParser.parse(text)
        if (result.rules.isEmpty() && result.elementRules.isEmpty()) {
            XLog.e("【内置规则】解析结果为空，跳过安装")
            return "内置规则解析失败（0 条可用）"
        }

        // 替换时保留上一份的 id 与启停状态：用户停用过它，升级后不该被偷偷打开
        val keepId = existing?.id ?: UUID.randomUUID().toString().substring(0, 8)
        if (existing != null) sets.remove(existing)
        sets.add(
            0,
            AdRuleSet(
                id = keepId,
                name = NAME,
                source = SOURCE,
                updatedAt = System.currentTimeMillis(),
                enabled = existing?.enabled ?: true,
                rules = result.rules,
                elementRules = result.elementRules,
            ),
        )
        if (!AdRuleStore.save(ctx, sets)) return "内置规则写入失败"

        val verb = if (existing == null) "已安装" else "已更新"
        XLog.i(
            "【内置规则】${verb}【$NAME】：请求拦截 ${result.rules.size} 条 / 元素隐藏 " +
                "${result.elementRules.size} 条（版本 $VERSION，" +
                "原文不合规 ${result.invalidLines} 条 / 超限截断 ${result.truncatedLines} 条）"
        )
        return "${verb}内置规则：拦截 ${result.rules.size} 条 · 隐藏 ${result.elementRules.size} 条"
    }

    /** 规则库里是否有内置那一组（不管版本新旧、是否启用） */
    fun isInstalled(ctx: Context): Boolean =
        runCatching { AdRuleStore.load(ctx) }.getOrNull()
            ?.any { it.source.startsWith(SOURCE_PREFIX) } == true

    /** 这一组是不是内置规则集（规则管理页据此改成「随模块版本更新」的展示、不给删除） */
    fun isBuiltin(set: AdRuleSet): Boolean = set.source.startsWith(SOURCE_PREFIX)

    // ===================== 内部 =====================

    /**
     * 读开关。
     *
     * 直接读本地 SP（而不是引 `ui/SettingsPrefs`）—— 本对象在 `adblock` 包，
     * 引 `ui` 会把 Compose 运行时带进浏览器进程，这是工程红线。默认值取
     * [Config.defaultOf]，与设置页同源。
     */
    private fun wanted(ctx: Context): Boolean =
        runCatching {
            ctx.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(Config.AD_BUILTIN_RULES, Config.defaultOf(Config.AD_BUILTIN_RULES))
        }.getOrDefault(Config.defaultOf(Config.AD_BUILTIN_RULES))

    private fun readAsset(ctx: Context): String? = runCatching {
        ctx.assets.open(ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
    }.onFailure { XLog.e("内置规则读取失败：${it.javaClass.simpleName} ${it.message}") }.getOrNull()
}
