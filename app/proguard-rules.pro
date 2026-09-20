# libxposed API 102 —— 官方给出的三条规则（README「Module Developer Installation」）
# 1.10.0 起全工程改用 libxposed，不再用 de.robv.android.xposed
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 模块被框架反射实例化，入口类必须保留（虽然本工程未开启混淆）
-keep class com.hupan.hookbrowser.MainHook { *; }
-keep class com.hupan.hookbrowser.features.** { *; }
