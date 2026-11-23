package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import com.savoj.volumpdf.R
import kotlin.math.roundToInt

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

    /**
     * Режим іконки «Фон шрифту» для панелі над клавіатурою (як у Samsung Notes):
     * малює літеру «T» у заокругленій рамці зверху та кольорову смужку [swatchColor] під нею.
     */
    var textBgIconMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Компактний режим кружечка для однорядкової плаваючої картки кольорів над клавіатурою (Samsung Notes):
     * зменшує лише видимий кружечок (радіус 11 dp). Accessibility/touch-вузол лишається 48 dp;
     * якщо ряд не вміщується, його контейнер прокручується горизонтально.
     */
    var compactMode: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        color = 0xFFF3F4F6.toInt()
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val min = maxOf(minimumTargetPx(density), resources.getDimensionPixelSize(R.dimen.place_swatch_size))
        setMeasuredDimension(resolveSize(min, widthMeasureSpec), resolveSize(min, heightMeasureSpec))
    }

    companion object {
        internal const val MIN_TOUCH_TARGET_DP = 48f
        internal fun minimumTargetPx(density: Float): Int = (MIN_TOUCH_TARGET_DP * density).roundToInt()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (textBgIconMode) {
            fill.shader = null
            val boxHalf = 6.8f * density
            val boxCenterY = cy - 2.6f * density
            val boxTop = boxCenterY - boxHalf
            val boxBottom = boxCenterY + boxHalf
            // Заокруглена рамка навколо T (як у Samsung Notes для «Фон шрифту»)
            boxStrokePaint.color = 0xFFF3F4F6.toInt()
            canvas.drawRoundRect(
                cx - boxHalf, boxTop,
                cx + boxHalf, boxBottom,
                2.4f * density, 2.4f * density, boxStrokePaint
            )
            // Літера T всередині рамки
            fill.color = 0xFFF3F4F6.toInt()
            val tTop = boxCenterY - 4.2f * density
            val tBottom = boxCenterY + 4.4f * density
            canvas.drawRoundRect(
                cx - 4.2f * density, tTop,
                cx + 4.2f * density, tTop + 1.8f * density,
                0.8f * density, 0.8f * density, fill
            )
            canvas.drawRoundRect(
                cx - 1.0f * density, tTop,
                cx + 1.0f * density, tBottom,
                0.8f * density, 0.8f * density, fill
            )
            // Кольорова смужка поточного фону шрифту знизу (або контурна, якщо фон прозорий)
            val barTop = cy + 6.2f * density
            val barBottom = cy + 9.6f * density
            val barRadius = 1.8f * density
            val hasBg = (swatchColor ushr 24) != 0
            if (hasBg) {
                fill.color = swatchColor or 0xFF000000.toInt()
                canvas.drawRoundRect(
                    cx - 8.0f * density, barTop,
                    cx + 8.0f * density, barBottom,
                    barRadius, barRadius, fill
                )
            }
            canvas.drawRoundRect(
                cx - 8.0f * density, barTop,
                cx + 8.0f * density, barBottom,
                barRadius, barRadius, edge
            )
            return
        }
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
        val r = if (compactMode) 11f * density else 16f * density
        val isTransparent = !rainbow && (swatchColor ushr 24) == 0
        if (rainbow) {
            val g = sweep ?: SweepGradient(
                cx, cy,
                intArrayOf(0xFFFF5252.toInt(), 0xFFFFEB3B.toInt(), 0xFF69F0AE.toInt(), 0xFF40C4FF.toInt(), 0xFFE040FB.toInt(), 0xFFFF5252.toInt()),
                null
            ).also { sweep = it }
            fill.shader = g
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, edge)
        } else if (isTransparent) {
            // Кружечок «Без фону» (як у Samsung Notes — порожній темний кружечок із тонким сірим обідком)
            fill.shader = null
            fill.color = 0xFF2B2D31.toInt()
            canvas.drawCircle(cx, cy, r, fill)
            boxStrokePaint.color = 0xFF8E9199.toInt()
            canvas.drawCircle(cx, cy, r, boxStrokePaint)
        } else {
            fill.shader = null
            fill.color = swatchColor
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, edge)
        }
        if (picked) {
            val ringOffset = if (compactMode) 2.2f * density else 4f * density
            ring.strokeWidth = if (compactMode) 1.8f * density else 2.5f * density
            canvas.drawCircle(cx, cy, r + ringOffset, ring)
            val rgb = swatchColor and 0xFFFFFF
            val isLight = !rainbow && !isTransparent && (
                rgb == 0xFBC02D || rgb == 0xFFFFFF || rgb == 0xFFEE58 || rgb == 0x00E676 || rgb == 0xFFA726
            )
            checkPaint.color = if (isLight) 0xFF1E1F22.toInt() else 0xFFF3F4F6.toInt()
            checkPaint.strokeWidth = if (compactMode) 1.8f * density else 2.2f * density
            val scale = if (compactMode) 0.72f else 1f
            checkPath.reset()
            checkPath.moveTo(cx - 5.5f * scale * density, cy + 0.5f * scale * density)
            checkPath.lineTo(cx - 1.5f * scale * density, cy + 4.5f * scale * density)
            checkPath.lineTo(cx + 6.0f * scale * density, cy - 3.5f * scale * density)
            canvas.drawPath(checkPath, checkPaint)
        }
    }
}
