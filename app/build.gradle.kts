plugins {
    // AGP 9 内置 Kotlin：不要再加 org.jetbrains.kotlin.android（会直接报错）
    id("com.android.application")
}

android {
    namespace = "com.hupan.hookbrowser"
    // libxposed api/service 102 的 AAR 元数据声明 minCompileSdk=37（service 101 也要 36），
    // checkReleaseAarMetadata 会硬卡，降不回去 —— 只能跟着抬到 37。
    // 这只影响编译期可见的 API，targetSdk 34 不动，运行时行为不变。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hupan.hookbrowser"
        // service 库要求 26（它用到 Android 8.0 起的 API）；模块本身不跑在低版本设备上
        minSdk = 26
        targetSdk = 34
        versionCode = 32
        versionName = "1.10.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // JDK 17：AGP 8.x 自带的 jbr 就是 17，跟 javac 对齐后不再有「源值 8 已过时」警告
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            // AGP 9 内置 Kotlin 的新 DSL（替代 kotlinOptions）；顶层 kotlin {} 块
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        // 只保留真正的错误，压掉 targetSdk/依赖版本之类的噪音警告
        warningsAsErrors = false
        abortOnError = false
    }
}

dependencies {
    // libxposed API 102：现代 Xposed 模块 API（取代 de.robv.android.xposed 旧 API）
    // compileOnly —— 运行时由框架提供，绝不打包进 APK
    // 1.10.0 起全工程不再依赖 api-82.jar（旧 API 在 API 102 下被禁止调用）
    compileOnly("io.github.libxposed:api:102.0.0")

    // 模块进程侧的写入端：宿主读开关走框架数据库，模块 App 必须经这个服务才能写进去。
    // 没有它就会出现「界面上开关是开的、宿主读到空表」（1.10.1 修的就是这个）。
    // 与 api 不同，这个是 implementation —— 它要真打进 APK（内含 XposedProvider）。
    implementation("io.github.libxposed:service:102.0.0")

    // service 库的方法签名上带 androidx.annotation.@NonNull，显式声明以免依赖传递断了时编不过
    implementation("androidx.annotation:annotation:1.7.1")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}
