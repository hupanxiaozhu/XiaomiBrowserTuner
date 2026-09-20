package com.hupan.hookbrowser.adblock

import com.hupan.hookbrowser.HostContext
import com.hupan.hookbrowser.XLog
import org.json.JSONObject
import java.io.File

/**
 * 把自定义规则**写进宿主**的规则库，让宿主自己的 native 广告引擎按我们的规则跑。
 *
 * ## 为什么必须落到文件
 *
 * 宿主的匹配器是 chromium 的 **native** `BlockingRuleMatcher`（真机日志 tag `<AdBlock>`），
 * Java 层没有"喂规则"的接口 —— 反汇编 `com.android.webview.chromium.ad.AdBlockHelper$Updator`
 * 得到的事实：
 *
 * ```
 * AdBlockHelper#updateRules(context)
 *   └─ new Thread(Updator).start()
 *        └─ updateRuleList("black")  → 把 content://com.miui.browser.adblock/black 拷到
 *        └─ updateRuleList("white")     files/data/adblock/miui_blacklist.json
 *                                       files/data/adblock/miui_whitelist.json
 *        └─ MiuiStatics.getInstance().notifyAdBlockUpdateConfig()   ← 通知 native 重载
 * ```
 *
 * 所以「让宿主按我的规则过滤」= **把那份 json 的内容换掉 + 通知它重载**。文件格式反汇编确认：
 *
 * ```
 * {"data": ["||ads.example.com^", "@@||ok.example.com^", ...]}
 * ```
 * （`AdBlockDataUpdator#readBlacklist` 就是 `new JSONObject(text).getJSONArray("data")`）
 *
 * ## 换哪些、留哪些
 *
 * | 文件 | 处理 |
 * |---|---|
 * | `miui_blacklist.json` | **整体替换**为自定义规则 |
 * | `miui_whitelist.json` | **清空** —— 宿主的 `@@` 例外会压掉用户规则，留着就会「导了但拦不住」 |
 * | `miui_watchlist` / `miui_privacylist` / `miui_businesslist` | **保持原样**（用途不同，动它们没有收益只有风险） |
 *
 * ## 备份与还原
 *
 * 首次接管前把 5 个文件整体复制到同目录下的 `.hb_backup/`（native 只按固定文件名读，
 * 不会碰到这个子目录）。还原时复制回去并删掉备份目录 —— **备份永远是接管前的原始版本**，
 * 所以「开→关→开」循环不会把改过的内容当成原件备份下来。
 *
 * ## 权限
 *
 * 写完 `setReadable(true, false)`：宿主的 webview 沙箱进程与浏览器进程 uid 不同，
 * 文件是广告规则（非隐私数据），放宽读权限没有安全代价，却能避免"浏览器读得到、沙箱读不到"。
 */
internal object HostRuleInstaller {

    /** `context.getFilesDir()` 下的相对目录（与 [HostAdRules] 同一个，反汇编确认） */
    private const val SUB_DIR = "data/adblock"

    const val BLACK = "miui_blacklist.json"
    const val WHITE = "miui_whitelist.json"

    /** 备份目录名：`.` 开头且不是 `.json`，native 的文件枚举不会命中 */
    private const val BACKUP_DIR = ".hb_backup"

    /** 应用指纹文件，内容见 [appliedStamp] */
    private const val STAMP = ".hb_stamp"

    /** 会被接管 / 需要备份的文件全集 */
    private val ALL_FILES = listOf(
        BLACK, WHITE,
        "miui_watchlist.json", "miui_privacylist.json", "miui_businesslist.json"
    )

    /**
     * 规则的**内容指纹**（条数 + 列表散列）。
     *
     * 守护线程每 8 秒要比一次，不能拿几 MB 的规则原文来算；`hashCode()` 是 O(n) 整数运算，
     * 三万条也就几十微秒，且**不需要构建字符串**。
     */
    fun ruleKey(rules: List<String>): String =
        rules.size.toString() + ':' + Integer.toHexString(rules.hashCode())

    /**
     * 当前宿主规则文件里装的是哪一版，格式 `规则指纹:写入字节数`；没接管过返回 null。
     *
     * 存字节数是为了让 [inEffectFor] 能发现「宿主又拿自己的规则把文件覆盖回去了」——
     * 只看指纹文件会被骗：文件还在，内容早被换掉了。
     */
    fun appliedStamp(): String? {
        val d = dir() ?: return null
        val f = File(d, STAMP)
        if (!f.isFile) return null
        return runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 是否处于「已接管」状态 */
    fun isApplied(): Boolean = appliedStamp() != null

    /**
     * 宿主规则文件里装的**是否正是 [rules] 且没被覆盖回去**。
     *
     * 两个条件缺一不可：
     * 1. 指纹对得上 —— 装的是这一版规则（用户改了规则要能触发重写）；
     * 2. 黑名单文件长度与写入时一致 —— 文件没被宿主换掉（否则光看指纹会以为还是我们的）。
     */
    fun inEffectFor(rules: List<String>): Boolean {
        val d = dir() ?: return false
        val s = appliedStamp() ?: return false
        val i = s.lastIndexOf(':')
        if (i <= 0) return false
        if (s.substring(0, i) != ruleKey(rules)) return false
        val bytes = s.substring(i + 1).toLongOrNull() ?: return false
        val black = File(d, BLACK)
        return runCatching { black.isFile && black.length() == bytes }.getOrDefault(false)
    }

    /**
     * 用 [rules] 接管宿主黑名单。幂等：调用方应先问 [inEffectFor]，这里不做重复判断。
     *
     * 返回 false 的三种情况：拿不到宿主 Context / 目录建不出来 / 写盘失败 —— 调用方记日志即可。
     */
    fun apply(rules: List<String>): Boolean {
        val d = dir()
        if (d == null) {
            XLog.v("【宿主规则】拿不到宿主 Context（钩子未就绪），本次不接管")
            return false
        }
        if (!d.isDirectory && !d.mkdirs()) {
            XLog.v("【宿主规则】目录建不出来：${d.absolutePath}")
            return false
        }

        backupOnce(d)

        val blackBytes = writeJson(File(d, BLACK), rules) ?: return false
        if (writeJson(File(d, WHITE), emptyList()) == null) return false

        writeStamp(d, ruleKey(rules) + ':' + blackBytes)
        return true
    }

    /**
     * 还原成接管前的样子。
     *
     * 没备份过（= 从没接管过）返回 false，调用方当作"无事可做" ——
     * **绝不能**在没有备份的情况下乱删宿主的文件。
     */
    fun restore(): Boolean {
        val d = dir() ?: return false
        val bak = File(d, BACKUP_DIR)
        if (!bak.isDirectory) return false

        var ok = true
        for (name in ALL_FILES) {
            val src = File(bak, name)
            val dst = File(d, name)
            if (src.isFile) {
                ok = copy(src, dst) && ok
            } else if (dst.isFile) {
                // 备份里没有 = 接管前本来就没这个文件，我们造出来的要删掉
                runCatching { dst.delete() }
            }
        }
        runCatching { File(d, STAMP).delete() }
        runCatching { bak.deleteRecursively() }
        return ok
    }

    // ===================== 内部 =====================

    private fun dir(): File? {
        val ctx = HostContext.app() ?: return null
        return File(ctx.filesDir, SUB_DIR)
    }

    /**
     * 首次接管时把原始文件整体备份。
     *
     * 备份目录已存在就**什么都不做** —— 这是"备份永远是最初版本"的关键：
     * 否则第二次接管会把已经改过的内容当成原件存起来，用户一还原就还原成"我们的规则"。
     */
    private fun backupOnce(d: File) {
        val bak = File(d, BACKUP_DIR)
        if (bak.isDirectory) return
        if (!bak.mkdirs()) {
            XLog.v("【宿主规则】备份目录建不出来，放弃接管：${bak.absolutePath}")
            return
        }
        var n = 0
        for (name in ALL_FILES) {
            val src = File(d, name)
            if (!src.isFile) continue
            if (copy(src, File(bak, name))) n++
        }
        XLog.i("【宿主规则】已备份宿主规则库 $n 个文件 → ${bak.absolutePath}")
    }

    /** 写 `{"data":[…]}`，返回落盘后的字节数（失败 null）。规则文本走 [JSONObject.quote] 转义 */
    private fun writeJson(f: File, rules: List<String>): Long? {
        val sb = StringBuilder(rules.size * 48 + 16)
        sb.append("{\"data\":[")
        for (i in rules.indices) {
            if (i > 0) sb.append(',')
            sb.append(JSONObject.quote(rules[i]))
        }
        sb.append("]}")
        return atomicWrite(f, sb.toString()).also {
            if (it == null) XLog.v("【宿主规则】写 ${f.name} 失败")
        }
    }

    private fun writeStamp(d: File, stamp: String) {
        atomicWrite(File(d, STAMP), stamp)
    }

    /**
     * 先写同目录临时文件再 `rename` —— Linux 上 `rename(2)` 是原子的，
     * native 那边不会读到写了一半的 JSON（半截 JSON 会让它整份规则失效）。
     * 同目录 rename 不会跨文件系统，因此必然走原子替换而不是"复制+删除"。
     *
     * 返回落盘后的字节数（失败 null）。
     */
    private fun atomicWrite(f: File, text: String): Long? = runCatching {
        val tmp = File(f.parentFile, f.name + ".hb_tmp")
        tmp.writeText(text)
        tmp.setReadable(true, false)
        if (!tmp.renameTo(f)) return@runCatching null
        f.length()
    }.getOrNull()

    private fun copy(src: File, dst: File): Boolean = runCatching {
        src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        dst.setReadable(true, false)
        true
    }.getOrElse {
        XLog.v("【宿主规则】复制 ${src.name} 失败：${it.javaClass.simpleName}")
        false
    }
}
