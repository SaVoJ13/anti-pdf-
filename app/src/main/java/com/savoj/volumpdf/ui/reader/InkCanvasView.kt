package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.savoj.volumpdf.core.pdf.model.InkStroke
import kotlin.math.abs

/**
 * Прозоре інтерактивне полотно для рукописного малювання (S Pen стилус або палець).
 * Підтримує плавне малювання кривими Безьє (QuadTo), скасування (Undo), очищення
 * та конвертацію координат в систему координат сторінки PDF.
 */
class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class StrokeData(
        val path: Path,
        val points: List<PointF>,
        val color: Int,
        val strokeWidth: Float
    )

    private val strokes = mutableListOf<StrokeData>()
    private var currentPath = Path()
    private val currentPoints = mutableListOf<PointF>()
    private var lastX = 0f
    private var lastY = 0f

    var currentColor: Int = Color.RED
    var currentStrokeWidth: Float = 6f
    var onStrokeCountChangeListener: ((count: Int) -> Unit)? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Малювання збережених штрихів
        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.strokeWidth
            canvas.drawPath(stroke.path, paint)
        }

        // Малювання поточного активного штриха
        if (!currentPath.isEmpty) {
            paint.color = currentColor
            paint.strokeWidth = currentStrokeWidth
            canvas.drawPath(currentPath, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) {
            return false
        }

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPoints.clear()
                currentPath.moveTo(x, y)
                currentPoints.add(PointF(x, y))
                lastX = x
                lastY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)
                if (dx >= 2f || dy >= 2f) {
                    val midX = (lastX + x) / 2f
                    val midY = (lastY + y) / 2f
                    currentPath.quadTo(lastX, lastY, midX, midY)
                    currentPoints.add(PointF(x, y))
                    lastX = x
                    lastY = y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                currentPoints.add(PointF(x, y))

                if (currentPoints.isNotEmpty()) {
                    strokes.add(
                        StrokeData(
                            path = Path(currentPath),
                            points = ArrayList(currentPoints),
                            color = currentColor,
                            strokeWidth = currentStrokeWidth
                        )
                    )
                }

                currentPath.reset()
                currentPoints.clear()
                invalidate()
                onStrokeCountChangeListener?.invoke(strokes.size)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                currentPath.reset()
                currentPoints.clear()
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * Скасування останнього штриха (Undo).
     */
    fun undo(): Boolean {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.lastIndex)
            invalidate()
            onStrokeCountChangeListener?.invoke(strokes.size)
            return true
        }
        return false
    }

    /**
     * Повне очищення полотна.
     */
    fun clear() {
        strokes.clear()
        currentPath.reset()
        currentPoints.clear()
        invalidate()
        onStrokeCountChangeListener?.invoke(strokes.size)
    }

    fun hasStrokes(): Boolean = strokes.isNotEmpty()

    fun getStrokeCount(): Int = strokes.size

    /**
     * Повертає штрихи в системі координат екрану.
     */
    fun getStrokes(): List<InkStroke> {
        return strokes.map { stroke ->
            InkStroke(
                points = ArrayList(stroke.points),
                color = stroke.color,
                strokeWidth = stroke.strokeWidth
            )
        }
    }

    /**
     * Масштабує точки штрихів з розмірів полотна (width, height)
     * до розмірів PDF сторінки у пунктах (pageWidthPt, pageHeightPt).
     */
    fun getScaledStrokes(pageWidthPt: Float, pageHeightPt: Float): List<InkStroke> {
        if (width <= 0 || height <= 0 || strokes.isEmpty()) {
            return emptyList()
        }

        val scaleX = pageWidthPt / width.toFloat()
        val scaleY = pageHeightPt / height.toFloat()
        val scaleStroke = (scaleX + scaleY) / 2f

        return strokes.map { stroke ->
            InkStroke(
                points = stroke.points.map { pt ->
                    PointF(pt.x * scaleX, pt.y * scaleY)
                },
                color = stroke.color,
                strokeWidth = maxOf(1f, stroke.strokeWidth * scaleStroke)
            )
        }
    }
}
