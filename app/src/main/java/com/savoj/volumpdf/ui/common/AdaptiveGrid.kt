package com.savoj.volumpdf.ui.common

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Підключає до [RecyclerView] сітку, яка сама перераховує кількість колонок при зміні ширини списку
 * (поворот, складання, split-screen, зміна розміру вікна) без перестворення Activity і втрати стану.
 * Щоб не повторити падіння К2, змінюється лише spanCount у RecyclerView (а не GridLayout.columnCount).
 */
object AdaptiveGrid {

    fun attach(rv: RecyclerView, minItemDp: Float, maxColumns: Int, gapDp: Int = 0): GridLayoutManager {
        val density = rv.resources.displayMetrics.density
        // Колонки вираховуються в onMeasure менеджера: до розкладки елементів і при кожній зміні ширини
        // (поворот, складання, split-screen) без перестворення Activity.
        val lm = object : GridLayoutManager(rv.context, 1) {
            override fun onMeasure(
                recycler: RecyclerView.Recycler,
                state: RecyclerView.State,
                widthSpec: Int,
                heightSpec: Int
            ) {
                val widthPx = View.MeasureSpec.getSize(widthSpec) - rv.paddingLeft - rv.paddingRight
                val columns = AdaptiveLayoutLogic.columnsFor(widthPx / density, minItemDp, maxColumns)
                if (spanCount != columns) spanCount = columns
                super.onMeasure(recycler, state, widthSpec, heightSpec)
            }
        }
        rv.layoutManager = lm
        if (gapDp > 0) rv.addItemDecoration(GapDecoration((gapDp * density).toInt()))
        return lm
    }

    /** Рівномірний горизонтальний проміжок між колонками; крайні колонки впритул до країв. */
    private class GapDecoration(private val gapPx: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val lm = parent.layoutManager as? GridLayoutManager ?: return
            val span = lm.spanCount
            if (span <= 1) return
            val lp = view.layoutParams as? GridLayoutManager.LayoutParams ?: return
            val index = lp.spanIndex
            outRect.left = index * gapPx / span
            outRect.right = gapPx - (index + 1) * gapPx / span
        }
    }
}
