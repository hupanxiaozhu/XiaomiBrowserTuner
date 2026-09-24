package com.hupan.hookbrowser

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * 所有 hook 的统一入口（libxposed API 102 版）。
 *
 * 三条硬规则（都是对原版 base.apk 缺陷的修正）：
 * 1. 挂载失败只跳过当前这一处，不抛异常给框架 —— 宿主版本不匹配时浏览器照常可用；
 * 2. 回调内任何异常都被吞掉并记日志 —— 绝不让去广告模块把宿主打崩；
 * 3. 一律挂同名的**全部重载**，天然覆盖，避免漏 hook。
 *
 * ## 与旧 API 的差别（重要）
 *
 * 旧 `XposedBridge` 的模型是「回调里改 `param.result`」；API 102 的 `Hooker.intercept(Chain)`
 * 是 **OkHttp 拦截器式**：想改结果就 **`return` 新值**，想放行就 `chain.proceed()`。
 *
 * 为了不动 features 的写法，这里做了一层适配：适配器以 **before 语义**挂上，构造一个
 * [HookedCall]（保留 `thisObject` / `args` / `result` / `method` 这套旧外观，代码是从旧的
 * `XC_MethodHook.MethodHookParam` 平移过来的），回调跑完以后：
 * - `result` 被显式赋值过 → 直接返回它（= 旧的「阻断 + 改结果」）；
 * - 没赋值过 → `proceed` 拿真实返回值，再喂给 after 回调。
 *
 * 于是 `param.result = x` 这种旧写法**原样可用**，各 feature 无需重写双回调逻辑。
 */
internal object Hooks {

    /**
     * 框架接口。由 `MainHook.attachFramework` 间接注入（`XposedModule` 继承的 wrapper
     * 内部持有），模块加载后必然可用。
     */
    @Volatile
    private var iface: XposedInterface? = null

    /** 由 [MainHook] 在 `onModuleLoaded` 里调用；重复调用无副作用 */
    internal fun attach(x: XposedInterface) {
        iface = x
    }

    // ===================== 挂载（按类名） =====================

    /** 在目标方法执行**之前**回调。阻断请用 `param.result = ...`。 */
    internal fun hook(cl: ClassLoader, cls: String, method: String, action: HookAction) {
        install(cl, cls, method, before = action, after = null)
    }

    /** 在目标方法执行**之后**回调。改结果请用 `param.result = ...`。 */
    internal fun hookAfter(cl: ClassLoader, cls: String, method: String, action: HookAction) {
        install(cl, cls, method, before = null, after = action)
    }

    private fun install(cl: ClassLoader, cls: String, method: String, before: HookAction?, after: HookAction?) {
        val target = Xp.findClass(cls, cl)
        if (target == null) {
            XLog.v("未找到类 $cls，跳过 #$method")
            return
        }
        val n = attachAll(target, method, SafeHook(before, after))
        if (n == 0) XLog.v("未找到 $cls#$method，跳过") else XLog.v("已挂载 $cls#$method（$n 个重载）")
    }

    // ===================== 挂载（按已捕获的 Class） =====================

    /**
     * 直接对**已拿到的 Class 对象**挂方法。
     *
     * 和上面两个的区别是入口不同：那两个按「类名 + ClassLoader」找类，适合一开始就知道目标的场景；
     * 这个收的是运行时**捕获到的实例类型**（比如从 `setWebViewClient(client)` 里拿到 client，
     * 再挂它自己的类）。返回实际挂上的重载数，0 表示这个类没这个方法。
     */
    internal fun hookAll(target: Class<*>, method: String, action: HookAction): Int =
        attachAll(target, method, SafeHook(action, null))

    /**
     * 同 [hookAll]，但回调挂在方法**执行之后**。
     *
     * 这里**只挂该类自己声明的方法**（`getDeclaredMethods`，不递归父类），与旧行为一致 ——
     * 所以调用方需要自己沿继承链找目标方法在哪一层声明：例如只在子类重写了 `onPageFinished`
     * 时，直接挂子类即可；没重写就得往上找。
     */
    internal fun hookAllAfter(target: Class<*>, method: String, action: HookAction): Int =
        attachAll(target, method, SafeHook(null, action))

    private fun attachAll(target: Class<*>, method: String, hooker: Hooker): Int {
        val x = iface ?: run {
            XLog.v("框架接口未就绪，跳过 ${target.name}#$method")
            return 0
        }
        var n = 0
        for (m in declaredMethodsNamed(target, method)) {
            try {
                x.hook(m).intercept(hooker)
                n++
            } catch (t: Throwable) {
                XLog.v("挂载 ${target.name}#$method 失败：${t.javaClass.simpleName} ${t.message}")
            }
        }
        return n
    }

    /** 本类自己声明的方法（不递归父类）；按「类名#方法名」缓存，热路径上反复挂同一目标时不重复反射 */
    private val methodCache = java.util.concurrent.ConcurrentHashMap<String, List<Method>>()
    private fun declaredMethodsNamed(target: Class<*>, name: String): List<Method> =
        methodCache.getOrPut("${target.name}#$name") {
            target.declaredMethods.filter { it.name == name }
        }

    // ===================== 字段 =====================

    /** 读宿主对象的字段；字段被改名/改类型时只记日志，不抛。 */
    internal fun field(obj: Any?, name: String): Any? = Xp.field(obj, name)

    /** 直接改宿主对象的字段用这个 */
    internal fun setField(obj: Any?, name: String, value: Any?) = Xp.setField(obj, name, value)

    internal fun setBoolField(obj: Any?, name: String, value: Boolean) = Xp.setBoolField(obj, name, value)

    /** 调宿主的无参方法（如 `RecentApp#isAd()`），失败返回 null */
    internal fun call(obj: Any?, method: String): Any? = Xp.call(obj, method)
}

/**
 * 回调签名，**刻意保留旧的 param 外观**。
 *
 * 这不是 `XC_MethodHook.MethodHookParam`（API 102 下不能引旧包），而是 [HookedCall] ——
 * 字段名与语义一一对应，所以从旧代码平移过来的回调体一行都不用改。
 */
internal typealias HookAction = (HookedCall) -> Unit

/**
 * 一次被拦截调用的视图。字段语义与旧 `MethodHookParam` 对齐：
 *
 * - [thisObject]：被调用方法的接收者（静态方法为 null）
 * - [args]：实参表，**类型与旧 `MethodHookParam.args` 一致（`Object[]`）**，可直接改元素
 * - [result]：返回值。**写入即视为「已设置」** —— 见下面的 setter 说明
 * - [method]：被调用的方法，`method.declaringClass` 即「声明它的类」
 * - [hasResult]：是否显式设置过返回值（拦截判定用）
 *
 * ## 为什么 result 要做成只读 + 自定义 setter
 *
 * 旧代码里 `p.result = x` 是最常见的写法。如果把它做成普通 `var`，赋值动作不会翻转 [hasResult]，
 * 适配器就不知道「用户想阻断了」，于是照样 `proceed()` 跑完原方法 —— 功能静默失效，且不报任何错。
 * 所以这里把 setter 重写：**任何对 result 的赋值都同步置位 hasResult**。
 *
 * 注意旧 API 的 `param.setResult(x)` 在这里**没有**对应方法（同名会和属性的 JVM 访问器撞签名），
 * 平移旧代码时统一改成赋值写法即可，语义等价。
 */
internal class HookedCall(
    val thisObject: Any?,
    val args: Array<Any?>,
    val method: Method,
    private var value: Any? = null
) {
    /** 返回值；赋值即表示「本次调用由我给出结果」 */
    var result: Any?
        get() = value
        set(v) {
            value = v
            hasResult = true
        }

    /**
     * 是否显式设置过返回值
     *
     * ⚠ **不能**再补 `fun getResult()` / `fun setResult(v)` 这两个旧 API 的同名方法：
     * Kotlin 属性 `result` 生成的 JVM 访问器就叫 `getResult` / `setResult`，
     * 再手写同名函数会报 `Platform declaration clash: the same JVM signature`。
     * 旧代码平移过来时，`param.setResult(x)` 一律改写为 `p.result = x`（`p.result` 读取同理）。
     */
    var hasResult: Boolean = false
        private set

    /**
     * 适配器专用：把 `proceed()` 的真实返回值写进来，**不**置位 [hasResult]。
     *
     * 这是 after 阶段的关键：after 回调要能读到真实返回值（`p.result as? List<*>`），
     * 但如果它不修改，返回的仍应是原值 —— 置位了就会把「读」误判成「改」。
     */
    internal fun setRaw(v: Any?) {
        value = v
    }

    /** proceed 抛出的异常（诊断用） */
    internal var throwable: Throwable? = null
}

/** API 102 的拦截器接口（内部类型，features 不接触） */
private fun interface Hooker : XposedInterface.Hooker

/**
 * 把新旧两套语义缝在一起。
 *
 * 挂载时机固定为 **before**，after 部分在适配器内部手动跑 —— 因为 API 102 的 `intercept`
 * 只想让你 `return` 一个值，没有独立的 after 阶段。这样做的代价是 after 回调抛异常时
 * 会**吞掉真实返回值**（记日志后返回 null），与旧 `afterHookedMethod` 抛异常的行为不同；
 * 但 features 的 after 回调（改字段、改返回集合）本来就不会抛，且旧行为下抛异常同样会毁掉调用。
 */
private class SafeHook(
    private val before: HookAction?,
    private val after: HookAction?
) : Hooker {

    /**
     * `throws Throwable` **不能省**：Java 侧的 `Hooker.intercept` 声明了 `throws Throwable`，
     * Kotlin 覆写时不写 `@Throws` 也能编译，但字节码里就没有异常表项，框架反射调用时
     * 会对声明外的受检异常额外包装一层。这里显式声明，保持与 Java 签名严格一致。
     *
     * 注意本工程**没有**构造器 hook（`install`/`hookAll` 都只挂普通方法），
     * 所以 `chain.executable` 不是 `Method` 时直接放行，不做处理。
     */
    @Throws(Throwable::class)
    override fun intercept(chain: XposedInterface.Chain): Any? {
        val exec = chain.executable
        if (exec !is Method) return chain.proceed()
        // chain.args 是**只读**列表，且形参就是 Object[]（旧 API 的 param.args 同型）：
        // 拷成数组交给回调，语义与旧代码完全一致（包括 `p.args[0] = x` 这种写元素）。
        val size = chain.args.size
        val call = HookedCall(chain.thisObject, Array(size) { chain.args[it] }, exec)
        // 只有 before 阶段可能改参数，纯 after 的 hook 也就不用留快照。
        // 这类 hook 常在帧内反复触发（如 `QuickLinksPanel#onLayout` 的重排），省掉这次
        // 数组拷贝与逐元素比较，少一份每帧的无谓分配。
        val originalArgs = if (before != null) call.args.copyOf() else null

        fire(before, call)
        if (call.hasResult) return call.result
        val value: Any? = try {
            // 旧 API 里改 param.args 会真的换掉实参；只在确实被改过时才把新数组传进去。
            // ⚠ proceed(Object[]) **不是 vararg**（Kotlin 里不能用 `*` 展开），直接传数组。
            if (originalArgs == null || call.args.contentEquals(originalArgs)) chain.proceed()
            else chain.proceed(call.args)
        } catch (t: Throwable) {
            call.throwable = t
            throw t
        }
        // 把真实返回值喂给 after 回调；若 after 没改它，就原样返回
        call.setRaw(value)
        fire(after, call)
        return call.result
    }

    private fun fire(action: HookAction?, call: HookedCall) {
        if (action == null) return
        try {
            action(call)
        } catch (t: Throwable) {
            XLog.e("hook 回调异常，已忽略", t)
        }
    }
}
