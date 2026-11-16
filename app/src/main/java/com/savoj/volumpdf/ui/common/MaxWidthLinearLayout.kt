package com.savoj.volumpdf.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.savoj.volumpdf.R

/**
 * Вертикальний вміст, обмежений по ширині (атрибут maxContentWidth) і відцентрований батьком
 * (layout_gravity="center_horizontal"). На телефоні нічого не змінює, на планшеті/складаному не розтягує рядки.
 */
class MaxWidthLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var maxContentWidthPx: Int = 0

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.MaxWidthLinearLayout, defStyleAttr, 0)
        try {
            maxContentWidthPx = a.getDimensionPixelSize(R.styleable.MaxWidthLinearLayout_maxContentWidth, 0)
        } finally {
            a.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = View.MeasureSpec.getSize(widthMeasureSpec)
        val mode = View.MeasureSpec.getMode(widthMeasureSpec)
        val capped = AdaptiveLayoutLogic.cappedWidth(size, maxContentWidthPx)
        val spec = if (mode != View.MeasureSpec.UNSPECIFIED && capped != size) {
            View.MeasureSpec.makeMeasureSpec(capped, mode)
        } else {
            widthMeasureSpec
        }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
