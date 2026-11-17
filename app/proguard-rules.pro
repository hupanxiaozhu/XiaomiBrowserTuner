# libxposed API 102 —— 官方给出的三条规则（README「Module Developer Installation」）
# 1.10.0 起全工程改用 libxposed，不再用 de.robv.android.xposed
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 模块被框架反射实例化，入口类必须保留（2.0.1 起 release 真的开了 R8，这条不再是摆设）
-keep class com.hupan.hookbrowser.MainHook { *; }

# 写穿通道：framework 通过 XposedServiceHelper 回调进来，入口不能被裁 / 改名
-keep class com.hupan.hookbrowser.ModuleService { *; }

# 各功能实现：类名会出现在日志与 `Features.ALL` 里，保留可读性也便于线上排查
-keep class com.hupan.hookbrowser.features.** { *; }

# 检查更新的数据模型：@Serializable 的序列化器是生成的，字段名靠 @SerialName 映射，
# 但反射入口（serializer()）在混淆后必须仍能定位到伴生对象
-keep class com.hupan.hookbrowser.update.** { *; }

# kotlinx-serialization 的伴生对象与注解
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
