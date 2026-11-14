package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import com.savoj.volumpdf.R

/**
 * Кружок кольору для картки параметрів: зона дотику ≥ 48 dp, видимий кружок 32 dp, вибраний — кільце.
 * [rainbow] — кнопка «власний колір» (райдужний кружок).
 */
class SwatchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = 0x66FFFFFF
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = context.getColor(R.color.place_card_ring)
    }
    private var sweep: SweepGradient? = null

    var swatchColor: Int = 0xFF000000.toInt()
        set(value) { field = value; invalidate() }

    var rainbow: Boolean = false
        set(value) { field = value; sweep = null; invalidate() }

    /** Для вибраного — кільце. Окрім виду, читається TalkBack як «вибрано». */
    var picked: Boolean = false
        set(value) {
            field = value
            isSelected = value
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val min = (resources.getDimension(R.dimen.place_swatch_size)).toInt()
        setMeasuredDimension(resolveSize(min, widthMeasureSpec), resolveSize(min, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = 16f * density
        if (rainbow) {
            val g = sweep ?: SweepGradient(
                cx, cy,
                intArrayOf(0xFFFF5252.toInt(), 0xFFFFEB3B.toInt(), 0xFF69F0AE.toInt(), 0xFF40C4FF.toInt(), 0xFFE040FB.toInt(), 0xFFFF5252.toInt()),
                null
            ).also { sweep = it }
            fill.shader = g
        } else {
            fill.shader = null
            fill.color = swatchColor
        }
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, edge)
        if (picked) canvas.drawCircle(cx, cy, r + 4f * density, ring)
    }
}
