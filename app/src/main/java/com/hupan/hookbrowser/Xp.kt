package com.hupan.hookbrowser

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * 反射工具 —— 替代旧版 `XposedHelpers`。
 *
 * libxposed API 102 **不再提供** `XposedHelpers`（官方说明：*We no longer provide interfaces like
 * XposedHelpers in the framework anymore*），而且 API 102 明确**禁止**模块调用
 * `de.robv.android.xposed` 旧 API。所以本工程自带一套最小的反射封装。
 *
 * 设计原则与旧 `Hooks` 一致：**失败只记日志、绝不抛异常** —— 宿主版本变动时浏览器照常可用。
 * 所有方法都返回可空值或默认值，调用方不需要 try/catch。
 */
internal object Xp {

    /** 按类名找类；找不到返回 null（找不到是常态，不记 error） */
    fun findClass(name: String, cl: ClassLoader?): Class<*>? =
        runCatching {
            when {
                cl != null -> Class.forName(name, false, cl)
                else -> Class.forName(name)
            }
        }.getOrNull()

    // ===================== 字段 =====================

    /**
     * 字段查找缓存（1.15.5）。
     *
     * `getDeclaredField` 每次都要在类的字段表里查一遍，找不到还得构造 `NoSuchFieldException`
     * （填栈不便宜），沿继承链找更要把父类逐个走一遍。而 hook 回调里读字段是高频动作 ——
     * `QuickLinksPanel#onLayout` 的 after 一次要读 7 个字段，这个方法在主页滚动时每帧都会跑 ——
     * 所以按「运行时类 + 字段名」缓存查找结果，`setAccessible(true)` 也随之只做一次。
     *
     * 用 [MISS] 哨兵把「找不到」一并缓存：字段不存在同样要遍历整条继承链，不该每帧重来。
     * 宿主换版后走的是新的 ClassLoader，缓存自然失效，不会读到旧版本的字段。
     */
    private val fieldCache = ConcurrentHashMap<String, Any>()
    private val MISS = Any()

    private fun findFieldCached(cls: Class<*>, name: String): Field? {
        val key = cls.name + '#' + name
        fieldCache[key]?.let { return if (it === MISS) null else it as Field }
        var c: Class<*>? = cls
        while (true) {
            val cur: Class<*> = c ?: break
            val f = runCatching { cur.getDeclaredField(name) }.getOrNull()
            if (f != null) {
                runCatching { f.isAccessible = true }
                fieldCache[key] = f
                return f
            }
            c = cur.superclass
        }
        fieldCache[key] = MISS
        return null
    }

    /** 读实例字段（沿继承链向上找）；失败返回 null */
    fun field(obj: Any?, name: String): Any? {
        if (obj == null) return null
        val f = findFieldCached(obj.javaClass, name) ?: return null
        return runCatching { f.get(obj) }
            .onFailure { XLog.v { "读字段 $name 失败: ${it.javaClass.simpleName}" } }
            .getOrNull()
    }

    /** 读静态字段 */
    fun staticField(cls: Class<*>?, name: String): Any? {
        if (cls == null) return null
        val f = findFieldCached(cls, name) ?: return null
        return runCatching { f.get(null) }
            .onFailure { XLog.v { "读静态字段 $name 失败: ${it.javaClass.simpleName}" } }
            .getOrNull()
    }

    /** 写实例字段 */
    fun setField(obj: Any?, name: String, value: Any?) {
        if (obj == null) return
        val f = findFieldCached(obj.javaClass, name) ?: return
        runCatching { f.set(obj, value) }
            .onFailure { XLog.v { "setField $name 失败: ${it.javaClass.simpleName}" } }
    }

    /** 写布尔字段（`Field.set` 装箱，Kotlin 的 Boolean 自动装箱，无需单独实现） */
    fun setBoolField(obj: Any?, name: String, value: Boolean) = setField(obj, name, value)

    // ===================== 方法调用 =====================

    /**
     * 方法查找缓存（2.0.1）。
     *
     * [findFieldCached] 早就缓存了字段查找，方法这边却**每次**都要沿整条继承链
     * `getDeclaredMethods()` —— ART 上这个方法每次返回一份**新拷贝的数组**，
     * 再两次 `filterTo` 建候选表。而 `call()` 在热路径上并不稀疏：
     * `RecentAppManager#getRecentAppList` 的 after 对列表里每一项调两次 `isAd()`，
     * `shouldInterceptRequest` 每个 H5 请求调两次取值器。
     *
     * 缓存键把「实参类型序列 + 静态性」编进去：同一组实参形态必然落到同一个 Method，
     * 不会把同名的重载串味。找不到的同样用 [MISS] 缓存 —— 找不到也是走完整条继承链。
     */
    private val methodCache = ConcurrentHashMap<String, Any>()

    private fun methodKey(cls: Class<*>, name: String, args: Array<out Any?>, wantStatic: Boolean): String {
        val sb = StringBuilder(cls.name.length + name.length + 16 + args.size * 24)
        sb.append(cls.name).append('#').append(name).append('/').append(wantStatic)
        for (a in args) sb.append('/').append(a?.javaClass?.name ?: "null")
        return sb.toString()
    }

    private fun findMethodCached(
        cls: Class<*>,
        name: String,
        args: Array<out Any?>,
        wantStatic: Boolean
    ): Method? {
        val key = methodKey(cls, name, args, wantStatic)
        methodCache[key]?.let { return if (it === MISS) null else it as Method }
        val m = findMethod(cls, name, args, wantStatic)
        if (m == null) {
            methodCache[key] = MISS
        } else {
            runCatching { m.isAccessible = true }
            methodCache[key] = m
        }
        return m
    }

    /** 调实例方法 */
    fun call(obj: Any?, name: String, vararg args: Any?): Any? {
        if (obj == null) return null
        return invoke(obj.javaClass, obj, name, wantStatic = false, args = args)
    }

    /** 调静态方法 */
    fun callStatic(cls: Class<*>?, name: String, vararg args: Any?): Any? {
        if (cls == null) return null
        return invoke(cls, null, name, wantStatic = true, args = args)
    }

    private fun invoke(
        cls: Class<*>,
        receiver: Any?,
        name: String,
        wantStatic: Boolean,
        args: Array<out Any?>
    ): Any? = runCatching {
        val m = findMethodCached(cls, name, args, wantStatic) ?: return@runCatching null
        m.invoke(receiver, *args)
    }.onFailure { XLog.v { "调用 $name 失败: ${it.javaClass.simpleName}" } }.getOrNull()

    /**
     * 找方法：先按「静态性 + 参数个数 + 类型可赋值」精确匹配，
     * 再放宽到「静态性 + 参数个数」（宿主混淆后参数类型可能不精确）。
     */
    private fun findMethod(cls: Class<*>, name: String, args: Array<out Any?>, wantStatic: Boolean): Method? {
        val candidates = ArrayList<Method>()
        var c: Class<*>? = cls
        while (c != null) {
            c.declaredMethods.filterTo(candidates) { it.name == name }
            c = c.superclass
        }
        val byStatic = candidates.filter { Modifier.isStatic(it.modifiers) == wantStatic }
        if (byStatic.isEmpty()) return null
        byStatic.firstOrNull { m ->
            m.parameterCount == args.size &&
                m.parameterTypes.withIndex().all { (i, p) -> isAssignable(p, args[i]) }
        }?.let { return it }
        return byStatic.firstOrNull { it.parameterCount == args.size }
    }

    private fun isAssignable(param: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !param.isPrimitive
        val p = if (param.isPrimitive) boxed(param) else param
        return p.isInstance(arg)
    }

    private fun boxed(p: Class<*>): Class<*> = when (p) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> p
    }

    // ===================== 构造 =====================

    /** 造实例：按参数个数与可赋值性挑构造器；失败返回 null */
    fun newInstance(cls: Class<*>?, vararg args: Any?): Any? {
        if (cls == null) return null
        return runCatching {
            val ctor = cls.declaredConstructors
                .filter { it.parameterCount == args.size }
                .firstOrNull { c -> c.parameterTypes.withIndex().all { (i, p) -> isAssignable(p, args[i]) } }
                ?: cls.declaredConstructors.firstOrNull { it.parameterCount == args.size }
                ?: return@runCatching null
            ctor.isAccessible = true
            ctor.newInstance(*args)
        }.onFailure { XLog.v("newInstance ${cls.simpleName} 失败: ${it.javaClass.simpleName}") }.getOrNull()
    }

    /** 兼容旧调用点：`newInstance(findClass(n, cl))` 形态 */
    fun newInstance(cls: Class<*>?): Any? = newInstance(cls, *emptyArray())
}
