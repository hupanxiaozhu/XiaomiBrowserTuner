package com.hupan.hookbrowser.features

import com.hupan.hookbrowser.Config

/**
 * 一个「功能」= 一组相关的 hook + 一个开关。
 *
 * 每个回调在动手之前先问 [on]，关掉开关时行为与「没装模块」完全一致。
 * [on] 里额外叠加了总开关：总开关一关，所有 hook 立即变成 no-op，便于二分排查。
 */
internal abstract class Feature(val key: String) {

    abstract fun install(cl: ClassLoader)

    /** 开关状态；宿主进程按路径直读模块写出的 SP XML（见 Config） */
    protected fun on(): Boolean = Config.masterOn() && Config.on(key)
}

internal object Features {
    val ALL: List<Feature> = listOf(
        SplashAdFeature,
        HomePromoFeature,
        SearchSugFeature,
        ErrorPageFeature,
        HostAdSwitchFeature,
        CustomAdBlockFeature,
        HostAdOverrideFeature,
        UserScriptFeature,
        DownloadFeature,
        HomeQuickLinkRowsFeature,
        UaFeature,
        SearchEngineFeature,
        UnlockPrefFeature,
        DebugModeFeature,
        SecurityFeature
    )
}
