// 根工程：只在两个地方做决定 —— Kotlin 编译器版本、以及各插件的版本声明。
//
// ## 为什么要显式声明 Kotlin（AGP 9 内置 Kotlin 的版本不够用）
//
// AGP 9.0 起 Kotlin 编译器随 AGP 一起分发（"内置 Kotlin"），实测 9.1.1 与 9.4.1 内置的
// 都是 **Kotlin 2.2.10**。但本工程要用的 miuix 0.9.3 有两个硬要求它满足不了：
//   - 它的 AAR 由 Kotlin **2.4.0** 编译（见其 pom 的 kotlin-stdlib），编译器读更高版本的
//     metadata 会直接失败；
//   - 它依赖 compose foundation **1.11.1**，需要同代的 Compose 编译器。
//
// 内置版本只能**向上**覆盖，方式是往 buildscript 的 classpath 里放一个更高版本的 KGP
// （版本目录在 buildscript 块里读不到，所以这里写字符串，改版本时两处一起改）。
// 覆盖生效后 Compose 编译器插件的版本必须与它相等，故下面两个插件都钉 2.4.20。
//
// 想反过来（降到比内置更低的版本）就得到 gradle.properties 里写
// `android.builtInKotlin=false` 再自己 apply kotlin-android —— 本项目不需要。
buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
    }
    dependencies {
        // 与 [versions] 里的 kotlin 无关（AGP 9 内置 Kotlin 不再读它），这是真正的编译器来源
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    // AGP 9.1.1：libxposed api/service 102 的 AAR 元数据 minCompileSdk=37，
    // 官方要求编译 API 37.0 最低 AGP 9.1.1（配 Gradle 9.3.1）。
    // AGP 9.0 起内置 Kotlin 支持，org.jetbrains.kotlin.android 插件必须移除（apply 会直接报错）
    id("com.android.application") version "9.1.1" apply false

    // Compose 编译器插件。**版本必须等于上面的 KGP 版本**（2.4.20），否则编译期报版本不一致。
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false

    // navigation3 的 NavKey 靠 @Serializable 做返回栈的保存与恢复
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
