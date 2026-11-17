package com.hupan.hookbrowser.update

/**
 * 版本号比较 —— 纯 Kotlin，不碰 Android API。
 *
 * 单独成文件的唯一理由是**可测**：[UpdateChecker] 是个 object，构造里就 `new Handler(...)`，
 * 在 JVM 单测里连初始化都做不到；而这段比较逻辑恰恰是最容易写错、又最值得钉住的部分
 * （1.9.0 与 1.10.0 的字典序陷阱就在这里）。
 *
 * 规则：按 `.` 拆段、逐段比数字；解析不动的段当 0；缺的段也当 0。
 * `v` 前缀由调用方先去掉（GitHub 的 tag 带 `v`，本地 versionName 不带）。
 */
internal fun compareVersions(a: String, b: String): Int {
    val pa = a.split(".").map { seg -> seg.trim().filter { it.isDigit() }.toIntOrNull() ?: 0 }
    val pb = b.split(".").map { seg -> seg.trim().filter { it.isDigit() }.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return if (x > y) 1 else -1
    }
    return 0
}
