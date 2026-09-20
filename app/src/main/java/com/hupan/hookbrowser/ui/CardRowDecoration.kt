package com.hupan.hookbrowser.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hupan.hookbrowser.R

/**
 * MIUI X 风格的**一条一卡**列表。
 *
 * androidx.preference 的列表是**一维平铺**的，库自己不画任何容器。这里在 RecyclerView 上挂一个
 * `ItemDecoration`，给**每一行**画一张 20dp 圆角卡片，行与行之间留 10dp 缝隙；分区标题
 * （`PreferenceCategory`）不画卡，只在它上方多留一段落差。
 *
 * 与旧版 `CardGroupDecoration`（一组一张大卡、组内用细分隔线）的区别：番茄 HookFanqie 7.2.30
 * 起每个功能都是**独立大卡片**，三边观感要一致，所以这里改成一行一卡、不再画组内分隔线。
 *
 * 两个刻意的取舍：
 * 1. **判断分组不看 `isGroupFirst` 之类的自研标记**，而是直接问 `PreferenceGroupAdapter.getItem`
 *    是不是 `PreferenceCategory` —— 这是库自己的可见顺序（会跳过 `app:dependency` 不满足而隐藏的项），
 *    自己去遍历 `PreferenceScreen` 会因为这个跳过而错位。
 * 2. **不碰 `PreferenceFragmentCompat` 自带的分隔线装饰**：那是它在 `onViewCreated` 里加的
 *    `DividerDecoration`（全宽横线，和卡片打架），由 `PrefsFragment` 在挂上本装饰前整体摘掉。
 *
 * 卡片只画在**行自己的边界内**，所以行布局里的左右 margin 就是卡片的左右留白（现为 16dp，
 * 与番茄 `SettingTab` 的 `contentPadding` 同值）。
 */
internal class CardRowDecoration(context: Context) : RecyclerView.ItemDecoration() {

    /** 卡片圆角：20dp，与多看 `Ui.RADIUS_CARD`、番茄 `ToggleCard` 同一值（三工程统一） */
    private val radius = dpFloat(context, 20f)

    /** 卡片之间的缝隙（番茄 LazyColumn 的 verticalArrangement.spacedBy(12.dp)，取整后为 10dp 更紧凑） */
    private val rowGap = dp(context, 10f)

    /** 分区标题上方的落差：比卡片间缝隙大，才能看出「这是新的一组」 */
    private val sectionGap = dp(context, 22f)

    /** 列表首行上方、尾行下方的最小留白 */
    private val edgeGap = dp(context, 4f)

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.mx_card)
    }

    private val rect = RectF()
    private val radii = FloatArray(8)
    private val path = Path()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val pos = parent.getChildAdapterPosition(view)
        if (pos < 0) return
        // 分区标题：上方多留一段；普通行：卡片之间的缝隙
        outRect.top = when {
            isHeader(adapter, pos) -> if (pos == 0) edgeGap else sectionGap
            pos == 0 -> edgeGap
            else -> rowGap
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.childCount == 0) return
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            // 分区标题不画卡：它是一行小字，做成卡片就没有「标题」的层次了
            if (pos < 0 || child.height == 0 || isHeader(adapter, pos)) continue
            drawCard(c, child)
        }
    }

    /** 一行一张完整的圆角卡片（四角都是 20dp —— 不再有「首行圆上角、末行圆下角」那种拼接） */
    private fun drawCard(c: Canvas, child: View) {
        rect.set(
            child.left.toFloat(), child.top.toFloat(),
            child.right.toFloat(), child.bottom.toFloat()
        )
        if (rect.isEmpty) return
        radii.fill(radius)
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        c.drawPath(path, cardPaint)
    }

    private fun isHeader(adapter: PreferenceGroupAdapter, pos: Int): Boolean =
        adapter.getItem(pos) is PreferenceCategory

    /** 间距用整数值（`Rect` 只收 Int），取整后至少 1px，避免低密度屏算出 0 */
    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    /** 圆角半径要 Float */
    private fun dpFloat(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
