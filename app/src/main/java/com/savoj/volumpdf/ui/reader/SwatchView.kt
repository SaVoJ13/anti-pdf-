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
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val checkPath = android.graphics.Path()
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

    /**
     * Режим іконки кольору тексту для панелі над клавіатурою (як у Samsung Notes):
     * малює літеру «T» зверху та кольорову смужку [swatchColor] під нею.
     */
    var textIconMode: Boolean = false
        set(value) {
            field = value
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
        if (textIconMode) {
            fill.shader = null
            fill.color = 0xFFF3F4F6.toInt()
            val topY = cy - 8.5f * density
            val stemBottomY = cy + 3.5f * density
            // Верхня перекладина літери T
            canvas.drawRoundRect(
                cx - 6.5f * density, topY,
                cx + 6.5f * density, topY + 2.3f * density,
                1f * density, 1f * density, fill
            )
            // Вертикальна ніжка літери T
            canvas.drawRoundRect(
                cx - 1.2f * density, topY,
                cx + 1.2f * density, stemBottomY,
                1f * density, 1f * density, fill
            )
            // Кольорова смужка поточного кольору шрифту знизу
            fill.color = swatchColor
            val barTop = cy + 5.5f * density
            val barBottom = cy + 9.3f * density
            val barRadius = 2f * density
            canvas.drawRoundRect(
                cx - 8.5f * density, barTop,
                cx + 8.5f * density, barBottom,
                barRadius, barRadius, fill
            )
            canvas.drawRoundRect(
                cx - 8.5f * density, barTop,
                cx + 8.5f * density, barBottom,
                barRadius, barRadius, edge
            )
            return
        }
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
        if (picked) {
            canvas.drawCircle(cx, cy, r + 4f * density, ring)
            val isLight = !rainbow && (swatchColor and 0xFFFFFF) == 0xFBC02D
            checkPaint.color = if (isLight) 0xFF1E1F22.toInt() else 0xFFF3F4F6.toInt()
            checkPath.reset()
            checkPath.moveTo(cx - 5.5f * density, cy + 0.5f * density)
            checkPath.lineTo(cx - 1.5f * density, cy + 4.5f * density)
            checkPath.lineTo(cx + 6.0f * density, cy - 3.5f * density)
            canvas.drawPath(checkPath, checkPaint)
        }
    }
}
