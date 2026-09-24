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

    /** 开关状态（会按 TTL 刷新快照，低频调用点用这个） */
    protected fun on(): Boolean = Config.masterOn() && Config.on(key)

    /**
     * 热路径专用的开关判断（1.15.5）：只读本地快照，**不发起跨进程刷新**。
     *
     * 布局 / 绘制回调里不要用 [on] —— 它在快照过期时会把一次跨进程读压进当前帧，
     * 直接表现为掉帧（`QuickLinksPanel#onLayout` 的重排就是这么被发现的）。
     * 快照里还没有该 key 时返回 false，即保守地不干预宿主。
     */
    protected fun onCached(): Boolean =
        Config.onCached(Config.MASTER) == true && Config.onCached(key) == true
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
        HomeBounceFeature,
        UaFeature,
        SearchEngineFeature,
        UnlockPrefFeature,
        DebugModeFeature,
        SecurityFeature
    )
}
