plugins {
    // AGP 9 内置 Kotlin：不要再加 org.jetbrains.kotlin.android（会直接报错）
    id("com.android.application")
    // Compose 编译器（版本由根工程钉死，等于 KGP 2.4.20）
    id("org.jetbrains.kotlin.plugin.compose")
    // Route 是 @Serializable 的 NavKey（navigation3 的状态保存依赖它）
    id("org.jetbrains.kotlin.plugin.serialization")
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
        // 1.15.2：修「主页快捷方式行数」放开上限后的两处界面问题 ——
        // ① 宿主的「更多」格在 onLayout 里被写死在位置 9，站点格超过 9 个就和它重叠；
        //    现在 onLayout 之后单独把「更多」格按真实 child 下标重排一次；
        // ② 「母开关卡 + 下拉行」原先各自画圆角背景且零间距，视觉上连成一片，
        //    改为一张卡片组（EntryGroup + EntryDivider）。
        // 1.15.1：修「主页快捷方式行数」——hook 目标改到真正生效的
        // homepage.SimpleVersionHomePage#getShowSiteCount（父类那个是恒等实现，虚拟派发不会走到），
        // 且开关打开时无条件接管返回值；上限改成「行数 × 每行个数 − 1」（末行第一格留给「更多」）。
        // 1.15.0：新增「主页快捷方式行数」（未发布，实现有误，内容并入 1.15.1）。
        // 1.14.0：新增「用户脚本」（油猴式 .user.js，复用 onPageFinished 注入通道），
        // 注入通道抽成共享件 webpage/PageInjection；其余功能与 hook 逻辑未动。
        versionCode = 41
        versionName = "1.15.2"
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
        // 设置页（1.11.0 起）是 Compose；规则管理页仍是 View（AppCompatActivity + RecyclerView）
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        // 只保留真正的错误，压掉 targetSdk/依赖版本之类的噪音警告
        warningsAsErrors = false
        abortOnError = false
        checkReleaseBuilds = false
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

    implementation("androidx.core:core-ktx:1.15.0")

    // ---- 界面 ----
    // 设置页（Compose）需要；也供规则管理页的 AppCompatActivity 使用
    implementation("androidx.activity:activity-compose:1.13.0")
    // 规则管理页仍是 View 实现：MaterialAlertDialogBuilder + 布局
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // miuix（HyperOS 设计语言的 Compose 实现）。三个模块都来自 Maven Central，无需私仓凭据。
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    // 图标集（MiuixIcons.extended.*）：底栏 / 入口行 / 顶栏返回统一走它，不再自绘 vector
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.3")
    // navigation3 的 miuix 风格转场（NavDisplay 由它传递提供，含 navigation3-ui 依赖）
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3")
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ⛔ 不要加 miuix-blur：它的 AAR 声明 minSdk 33，本模块 minSdk 26，
    //    加进来会让 manifest 合并直接失败（界面也没有模糊背板）。
    // ⛔ 不要再加 androidx.preference：prefs.xml 已随 1.11.0 的界面重建删除。
}
