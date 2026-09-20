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
 * MIUI X 风格的**分组卡片**。
 *
 * androidx.preference 的列表是**一维平铺**的：分组（`PreferenceCategory`）自己也是一行，
 * 组内行与组标题之间没有任何视觉容器。MIUI X 要的是「一组 = 一张大圆角白卡片，组内用细分隔线，
 * 组间留白」。做法是在 RecyclerView 上挂一个 `ItemDecoration`：
 *
 * - `getItemOffsets` 只负责**行外的间距**（组首行上方、组标题上方），于是组与组之间自然出现缝隙；
 * - `onDraw` 分两遍画：先画卡片（组内首行圆上角、末行圆下角，中间两行直角 → 相邻行拼成一张整卡），
 *   再画组内分隔线；卡片只画在**行自己的边界内**，所以行布局里的左右 margin 就是卡片的左右留白。
 *
 * 两个刻意的取舍：
 * 1. **判断分组不看 `isGroupFirst` 之类的自研标记**，而是直接问 `PreferenceGroupAdapter.getItem`
 *    是不是 `PreferenceCategory` —— 这是库自己的可见顺序（会跳过 `app:dependency` 不满足而隐藏的项），
 *    自己去遍历 `PreferenceScreen` 会因为这个跳过而错位。
 * 2. **不碰 `PreferenceFragmentCompat` 自带的分隔线装饰**：那是它在 `onViewCreated` 里加的
 *    `DividerDecoration`（全宽横线，和卡片打架），由 `PrefsFragment` 在挂上本装饰前整体摘掉。
 */
internal class CardGroupDecoration(context: Context) : RecyclerView.ItemDecoration() {

    /** 卡片圆角：20dp，与多看 `Ui.RADIUS_CARD`、番茄 `SectionCard` 同一值（三工程统一） */
    private val radius = dpFloat(context, 20f)
    private val groupGap = dp(context, 10f)
    private val headerGap = dp(context, 14f)
    private val edgeGap = dp(context, 4f)
    private val dividerInset = dp(context, 14f)
    private val dividerHeight = dp(context, 1f)

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.mx_card)
    }
    private val dividerPaint = Paint().apply {
        color = context.getColor(R.color.mx_divider)
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
        outRect.top = when {
            isHeader(adapter, pos) -> if (pos == 0) edgeGap else headerGap
            isGroupFirst(adapter, pos) -> if (pos == 0) edgeGap else groupGap
            else -> 0
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.childCount == 0) return
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        // 第一遍卡片、第二遍分隔线：分隔线要压在卡片上，不能和卡片同轮画
        for (pass in 0..1) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos < 0 || child.height == 0 || isHeader(adapter, pos)) continue
                if (pass == 0) {
                    drawCard(c, child, isGroupFirst(adapter, pos), isGroupLast(adapter, pos))
                } else if (!isGroupLast(adapter, pos)) {
                    drawDivider(c, child)
                }
            }
        }
    }

    /** 组内一行卡片：首行圆上角、末行圆下角，中间行直角（于是相邻行拼成一张整卡） */
    private fun drawCard(c: Canvas, child: View, first: Boolean, last: Boolean) {
        rect.set(
            child.left.toFloat(), child.top.toFloat(),
            child.right.toFloat(), child.bottom.toFloat()
        )
        if (rect.isEmpty) return
        radii.fill(0f)
        if (first) {
            radii[0] = radius
            radii[1] = radius
            radii[2] = radius
            radii[3] = radius
        }
        if (last) {
            radii[4] = radius
            radii[5] = radius
            radii[6] = radius
            radii[7] = radius
        }
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        c.drawPath(path, cardPaint)
    }

    /** 组内分隔线：画在下一行的上边界上，左右各留一点，像 MIUI 那样不顶到卡片边缘 */
    private fun drawDivider(c: Canvas, child: View) {
        val left = child.left + dividerInset
        val right = child.right - dividerInset
        if (right <= left) return
        c.drawRect(
            left.toFloat(), (child.bottom - dividerHeight).toFloat(),
            right.toFloat(), child.bottom.toFloat(),
            dividerPaint
        )
    }

    private fun isHeader(adapter: PreferenceGroupAdapter, pos: Int): Boolean =
        adapter.getItem(pos) is PreferenceCategory

    private fun isGroupFirst(adapter: PreferenceGroupAdapter, pos: Int): Boolean =
        pos == 0 || isHeader(adapter, pos - 1)

    private fun isGroupLast(adapter: PreferenceGroupAdapter, pos: Int): Boolean =
        pos == adapter.itemCount - 1 || isHeader(adapter, pos + 1)

    /** 间距用整数值（`Rect` 只收 Int），取整后至少 1px，避免低密度屏算出 0 */
    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    /** 圆角半径要 Float */
    private fun dpFloat(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
