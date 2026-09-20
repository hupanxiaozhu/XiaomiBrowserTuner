plugins {
    // AGP 9.1.1：libxposed api/service 102 的 AAR 元数据 minCompileSdk=37，
    // 官方要求编译 API 37.0 最低 AGP 9.1.1（配 Gradle 9.3.1）。
    // AGP 9.0 起内置 Kotlin 支持，org.jetbrains.kotlin.android 插件必须移除（apply 会直接报错）
    id("com.android.application") version "9.1.1" apply false
}
