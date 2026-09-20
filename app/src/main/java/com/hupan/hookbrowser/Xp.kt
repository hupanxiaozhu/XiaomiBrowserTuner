package com.hupan.hookbrowser

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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

    /** 读实例字段（沿继承链向上找）；失败返回 null */
    fun field(obj: Any?, name: String): Any? {
        if (obj == null) return null
        var c: Class<*>? = obj.javaClass
        while (true) {
            // `?: break` 而不是 `while (c != null)`：局部 var 在循环里会被重新赋值，
            // 依赖智能转换容易被编译器拒掉（"captured by a changing closure"）。
            val cls: Class<*> = c ?: break
            val f = runCatching { cls.getDeclaredField(name) }.getOrNull()
            if (f != null) {
                f.isAccessible = true
                return runCatching { f.get(obj) }
                    .onFailure { XLog.v("读字段 $name 失败: ${it.javaClass.simpleName}") }
                    .getOrNull()
            }
            c = cls.superclass
        }
        return null
    }

    /** 读静态字段 */
    fun staticField(cls: Class<*>?, name: String): Any? {
        if (cls == null) return null
        return runCatching {
            val f = findField(cls, name) ?: return@runCatching null
            f.isAccessible = true
            f.get(null)
        }.onFailure { XLog.v("读静态字段 $name 失败: ${it.javaClass.simpleName}") }.getOrNull()
    }

    /** 写实例字段 */
    fun setField(obj: Any?, name: String, value: Any?) {
        if (obj == null) return
        var c: Class<*>? = obj.javaClass
        while (true) {
            val cls: Class<*> = c ?: break
            val f = runCatching { cls.getDeclaredField(name) }.getOrNull()
            if (f != null) {
                runCatching {
                    f.isAccessible = true
                    f.set(obj, value)
                }.onFailure { XLog.v("setField $name 失败: ${it.javaClass.simpleName}") }
                return
            }
            c = cls.superclass
        }
    }

    /** 写布尔字段（`Field.set` 装箱，Kotlin 的 Boolean 自动装箱，无需单独实现） */
    fun setBoolField(obj: Any?, name: String, value: Boolean) = setField(obj, name, value)

    private fun findField(cls: Class<*>, name: String): Field? {
        var c: Class<*>? = cls
        while (true) {
            val cur: Class<*> = c ?: break
            runCatching { cur.getDeclaredField(name) }.getOrNull()?.let { return it }
            c = cur.superclass
        }
        return null
    }

    // ===================== 方法调用 =====================

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
        val m = findMethod(cls, name, args, wantStatic) ?: return@runCatching null
        m.isAccessible = true
        m.invoke(receiver, *args)
    }.onFailure { XLog.v("调用 $name 失败: ${it.javaClass.simpleName}") }.getOrNull()

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
