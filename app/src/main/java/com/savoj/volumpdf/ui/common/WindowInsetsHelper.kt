package com.savoj.volumpdf.ui.common

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat

/** Відступи від системних елементів у пікселях. */
data class SystemEdges(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        val NONE = SystemEdges(0, 0, 0, 0)

        /**
         * Лівий/верхній/правий — рядок стану, виріз, бічні панелі навігації; нижній — навігаційна панель
         * або клавіатура (більший із двох).
         */
        fun combine(
            barsLeft: Int, barsTop: Int, barsRight: Int, barsBottom: Int,
            imeBottom: Int
        ) = SystemEdges(barsLeft, barsTop, barsRight, maxOf(barsBottom, imeBottom))

        fun from(insets: WindowInsetsCompat): SystemEdges {
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            return combine(bars.left, bars.top, bars.right, bars.bottom, ime.bottom)
        }
    }
}

object WindowInsetsHelper {
    /** Висота стандартного тулбара поточної теми, px. */
    fun actionBarSizePx(view: View): Int {
        val tv = TypedValue()
        return if (view.context.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
            TypedValue.complexToDimensionPixelSize(tv.data, view.resources.displayMetrics)
        } else {
            (56 * view.resources.displayMetrics.density).toInt()
        }
    }

    /** Тулбар: верхній відступ = рядок стану/виріз, висота = actionBarSize + відступ (кнопки лишаються 48 dp). */
    fun applyToolbarTopInset(toolbar: View, topPx: Int) {
        toolbar.setPadding(toolbar.paddingLeft, topPx, toolbar.paddingRight, toolbar.paddingBottom)
        val lp = toolbar.layoutParams
        if (lp != null) {
            lp.height = actionBarSizePx(toolbar) + topPx
            toolbar.layoutParams = lp
        }
    }

    /** AppBarLayout (тулбар + вкладки): верхній відступ = рядок стану/виріз. */
    fun applyAppBarTopInset(appBar: View, topPx: Int) {
        appBar.setPadding(appBar.paddingLeft, topPx, appBar.paddingRight, appBar.paddingBottom)
    }

    /** Нижній відступ елемента-контейнера [baseMarginPx] + [bottomPx] (для плаваючих карток у FrameLayout). */
    fun applyMargins(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.leftMargin != left || lp.topMargin != top || lp.rightMargin != right || lp.bottomMargin != bottom) {
            lp.setMargins(left, top, right, bottom)
            view.layoutParams = lp
        }
    }
}
