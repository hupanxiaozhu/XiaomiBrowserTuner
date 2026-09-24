package com.hupan.hookbrowser.features

import android.view.View
import com.hupan.hookbrowser.Config
import com.hupan.hookbrowser.HookedCall
import com.hupan.hookbrowser.Hooks
import com.hupan.hookbrowser.XLog
import java.util.Collections
import java.util.WeakHashMap

/**
 * 简洁版主页「滚到边界回弹」+「滚到底部被强制显示搜索栏」（1.15.7）。
 *
 * ## 宿主结构（20.27 反汇编核对）
 *
 * 简洁版主页的滚动容器是**自绘的** `homepage/infoflow/view/InfoFlowScrollView`
 * （`extends FrameLayout`，不是原生 `ScrollView`），外面还有一层
 * `homepage/SimpleVersionHomeLayout`（`extends InfoFlowScrollView`，带
 * `STATE_IDLE / STATE_SCROLLING / STATE_SCROLL_TO_TOP` 状态机）。
 *
 * | 关注点 | 宿主实现 |
 * |---|---|
 * | 回弹 | 走 `View#overScrollBy` 那条路：`mOverscrollDistance` / `mOverflingDistance` + `mEdgeGlowTop` / `mEdgeGlowBottom` |
 * | 下拉进搜索 | 字段 `isPullDownSearch`、常量 `PULL_DOWN_RATE`，方法 `isPullDownSearch()` / `animateScrollToSearch()` |
 * | 搜索栏本体 | `homepage/SimpleHomeInputView`（`extends LinearLayout`），显隐动画由 `SimpleHomeAnimHelper` 与 `SimpleVersionHomeLayout#homeSearchTransYAnim()` 驱动 |
 *
 * 用户报告的现象是「往上滑滚到页面最底下时回弹，并且搜索栏被强制显示」——
 * 这两件事在宿主里是**同一套逻辑**：回弹（overscroll）把搜索栏带了出来。
 *
 * ## 做法：把 overscroll 这条路关掉
 *
 * 容器首次布局时做两件事：
 * 1. `overScrollMode = OVER_SCROLL_NEVER` —— 框架层不再产生边缘光晕（edge glow）与
 *    Android 12+ 的 stretch 过冲效果；
 * 2. `mOverscrollDistance` / `mOverflingDistance` 置 0 —— 这个自绘容器传给
 *    `View#overScrollBy` 的「允许越界距离」，是回弹幅度的直接来源。
 *
 * 两道都要：前者管框架层的效果，后者管这个 View 自己的越界量。
 * 于是滚到顶 / 到底都是**硬停**，搜索栏也就不会被回弹带出来了。
 *
 * ## 边界都很保守
 *
 * - 开关关着 → 一个 hook 都不生效，宿主怎么滚就怎么滚；
 * - 宿主换版导致字段名变了 → `Hooks.setField` 静默失败，`overScrollMode` 仍是有效兜底；
 * - 只在这个滚动容器实例上动手，不碰宿主的其他滚动视图；
 * - 同一容器类在**信息流页**也复用，那里保留原生的边界手感（按 `mIsSimpleHome` 区分，
 *   字段读不到时不做区分 —— 宁可生效也不静默失效）。
 */
internal object HomeBounceFeature : Feature(Config.UI_HOME_BOUNCE) {

    /** 简洁版主页的滚动容器（信息流页复用同一个类） */
    private const val CLS_SCROLL = "com.android.browser.homepage.infoflow.view.InfoFlowScrollView"

    /**
     * 挂点：容器**自己重写**的 `onLayout`。
     *
     * ⚠ 不要改挂 `onAttachedToWindow`：20.27 上 `InfoFlowScrollView` **没有重写**它
     * （类里只有 `onDetachedFromWindow`），而 [Hooks.hookAfter] 只挂「本类声明」的方法、
     * 不递归父类 —— 挂上去一个重载都挂不上，功能静默失效且不报任何错。
     * `onLayout` 是本类重写的，首次布局时构造里的 `initScrollView()` 早已跑完，
     * 这时覆盖两个距离才生效。
     */
    private const val METHOD_ON_LAYOUT = "onLayout"

    /** 允许越界距离：回弹幅度的直接来源 */
    private const val FIELD_OVERSCROLL_DISTANCE = "mOverscrollDistance"
    private const val FIELD_OVERFLING_DISTANCE = "mOverflingDistance"

    /** 标识这个实例是不是简洁版主页那个（信息流页复用同一个类） */
    private const val FIELD_IS_SIMPLE_HOME = "mIsSimpleHome"

    /** 已处理过的实例；弱引用，不会拖住 Activity / View */
    private val done: MutableSet<View> = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    override fun install(cl: ClassLoader) {
        Hooks.hookAfter(cl, CLS_SCROLL, METHOD_ON_LAYOUT) { p -> killOverScroll(p) }
    }

    private fun killOverScroll(p: HookedCall) {
        val v = p.thisObject as? View ?: return
        // ⚠ 热路径（1.15.5 的约定）：开关只读本地快照，绝不做跨进程读。
        // 快照还没这个 key 时不动手、也**不标记 done** —— 等下一次 onLayout
        // （那时快照已被别的低频调用点刷新上来）再来。
        if (!onCached()) return
        // onLayout 在滚动 / 布局时会反复触发，一个实例只处理一次
        if (done.contains(v)) return
        // 同一容器在信息流页也复用，那里保留原生手感。
        // 只有明确读到 false 才跳过 —— 字段读不到（宿主换版改名）时宁可生效，也不静默失效。
        if (Hooks.field(v, FIELD_IS_SIMPLE_HOME) == false) {
            done.add(v)
            return
        }

        done.add(v)
        runCatching { v.overScrollMode = View.OVER_SCROLL_NEVER }
            .onFailure { XLog.v("设置 overScrollMode 失败: ${it.javaClass.simpleName}") }
        Hooks.setField(v, FIELD_OVERSCROLL_DISTANCE, 0)
        Hooks.setField(v, FIELD_OVERFLING_DISTANCE, 0)
        XLog.v("主页滚动容器已关闭 overscroll（overScrollMode=NEVER，越界距离归零）")
    }
}
