package com.hupan.hookbrowser

import android.content.Context
import android.content.SharedPreferences
import com.hupan.hookbrowser.adblock.AdRuleStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块进程侧的**框架服务通道**（`io.github.libxposed:service`）—— 开关的写入端。
 *
 * ## 为什么必须有这一层（1.10.1）
 *
 * 1.10.0 只把**读取端**迁到了 API 102：宿主进程走
 * `XposedInterface#getRemotePreferences(group)`，读的是**框架数据库**。
 * 但模块 App 写开关时仍然落在自己私有的 shared_prefs 目录里 ——
 * 两端根本不是同一份数据。表现就是日志里那两行：
 *
 * ```text
 * HookBrowser: 读取开关失败，回退到默认值（misc_debug=false）
 * HookBrowser: 【规则】规则库为空，未导入或宿主读不到：组 adrules 为空（模块进程还没写过规则库）
 * ```
 *
 * 「组为空」而不是「拿不到组」：说明远端句柄建起来了，只是从来没人往里写过。
 * 写入端只能靠框架服务：框架通过 `XposedProvider` 把 `IXposedService` 的 binder 送进模块进程，
 * [bind] 之后拿到 [XposedService]，就能取到**同一个**远端组并写入，宿主侧立刻可见。
 *
 * ## 用法
 *
 * - `MainActivity.onCreate` 调一次 [bind]（框架要求「只注册一次」，内部已做幂等）。
 * - 本地 SP 每次变更后调 [push] 把整组镜像过去；防抖由调用方负责。
 * - 服务还没连上时 [push] 会把组名记进 [pending]，连上后自动补推。
 */
internal object ModuleService {

    /** 单个 value 超过这个字符数就不推：远端写入是一次 Binder 事务，塞太大整体会失败 */
    private const val MAX_VALUE_CHARS = 400_000

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var frameworkName: String? = null

    private val registered = AtomicBoolean(false)

    /** 组名 → 远端句柄。`getRemotePreferences` 每次都是一次跨进程往返且会拉全量 map，必须缓存 */
    private val handles = ConcurrentHashMap<String, SharedPreferences>()

    /** 服务未连接时先记下要推的组，连上后补推 */
    private val pending = Collections.synchronizedList(ArrayList<String>())

    /** 是否已连上框架服务 */
    val isBound: Boolean get() = service != null

    /** 连上的框架名（未连接为 null） */
    val boundFramework: String? get() = frameworkName

    /**
     * 注册框架服务监听。框架侧 `XposedProvider` 收到 binder 后会回调 [onServiceBind]。
     *
     * 幂等：框架明确要求「只调用一次」，而 Activity 可能因旋转重建。
     */
    fun bind(context: Context) {
        appCtx = context.applicationContext
        if (!registered.compareAndSet(false, true)) return
        // 首次连接时把两组都推一遍：用户可能是装完模块直接开浏览器，从没进过设置页
        pending.add(Config.PREFS_NAME)
        pending.add(AdRuleStore.PREFS)
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(s: XposedService) {
                    service = s
                    handles.clear()
                    frameworkName = runCatching { s.frameworkName }.getOrNull()
                    XLog.i("框架服务已连接：${frameworkName ?: "未知框架"}")
                    drain()
                }

                override fun onServiceDied(s: XposedService) {
                    if (service === s) {
                        service = null
                        frameworkName = null
                        handles.clear()
                        XLog.i("框架服务已断开，开关改动暂时不会同步到宿主")
                    }
                }
            })
        }.onFailure {
            registered.set(false)
            XLog.e("注册框架服务监听失败，开关将无法同步到宿主", it)
        }
    }

    /**
     * 取远端组句柄；服务未连接返回 null。
     *
     * 框架不支持 remote 能力时会抛 `UnsupportedOperationException`，这里统一吞掉并返回 null ——
     * 调用方（镜像）拿不到就记进 [pending]，等下次再试。
     */
    fun remote(group: String): SharedPreferences? {
        handles[group]?.let { return it }
        val s = service ?: return null
        return try {
            s.getRemotePreferences(group).also { handles[group] = it }
        } catch (t: Throwable) {
            XLog.e("getRemotePreferences($group) 失败", t)
            null
        }
    }

    /**
     * 把本地同名 SP **全量镜像**到远端组（覆盖式：先 clear 再逐条 put）。
     *
     * 走全量而不是增量，是因为本地 SP 是唯一真源，而远端只可能被本进程写 ——
     * 增量会累积出「本地删了、远端还在」的幽灵 key。
     */
    fun push(ctx: Context, group: String): Boolean {
        val r = remote(group)
        if (r == null) {
            if (!pending.contains(group)) pending.add(group)
            return false
        }
        val local = ctx.getSharedPreferences(group, Context.MODE_PRIVATE)
        val ed = r.edit().clear()
        var n = 0
        var skipped = 0
        for ((k, v) in local.all) {
            when (v) {
                null -> ed.remove(k)
                is Boolean -> ed.putBoolean(k, v)
                is Int -> ed.putInt(k, v)
                is Long -> ed.putLong(k, v)
                is Float -> ed.putFloat(k, v)
                is String -> if (v.length > MAX_VALUE_CHARS) skipped++ else ed.putString(k, v)
                is Set<*> -> ed.putStringSet(k, v.filterIsInstance<String>().toSet())
                else -> continue
            }
            n++
        }
        val ok = runCatching { ed.commit() }.onFailure {
            XLog.e("同步组 $group 到框架失败", it)
        }.getOrDefault(false)
        if (!ok) {
            XLog.e("同步组 $group 失败（$n 个 key）")
        } else if (skipped > 0) {
            XLog.e("同步组 $group 完成，但 $skipped 个超大 value 被跳过（>$MAX_VALUE_CHARS 字符）")
        } else {
            XLog.v("【同步】组 $group → $n 个 key")
        }
        return ok
    }

    /** 服务刚连上时补推积压的组 */
    private fun drain() {
        val ctx = appCtx ?: return
        val groups = synchronized(pending) { ArrayList(pending).also { pending.clear() } }
        groups.forEach { push(ctx, it) }
    }
}
