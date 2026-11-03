package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Спеціалізоване полотно для створення підпису (рукописний розпис пальцем або стилусом).
 * Надає метод експорту [getTransparentSignatureBitmap] з прозорим фоном та авто-обрізкою країв.
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class SignatureStroke(
        val path: Path,
        val points: List<PointF>
    )

    private val strokes = mutableListOf<SignatureStroke>()
    private var currentPath = Path()
    private val currentPoints = mutableListOf<PointF>()
    private var lastX = 0f
    private var lastY = 0f

    var strokeColor: Int = Color.BLACK
    var strokeWidthPx: Float = 5f

    private val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = strokeColor
        paint.strokeWidth = strokeWidthPx

        for (stroke in strokes) {
            canvas.drawPath(stroke.path, paint)
        }

        if (!currentPath.isEmpty) {
            canvas.drawPath(currentPath, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

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
                        SignatureStroke(
                            path = Path(currentPath),
                            points = ArrayList(currentPoints)
                        )
                    )
                }

                currentPath.reset()
                currentPoints.clear()
                invalidate()
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

    fun isEmpty(): Boolean = strokes.isEmpty() && currentPoints.isEmpty()

    fun clear() {
        strokes.clear()
        currentPath.reset()
        currentPoints.clear()
        invalidate()
    }

    /**
     * Створює чистий Bitmap підпису на прозорому фоні (ARGB_8888)
     * із автоматичною обрізкою порожніх полів довкола штрихів.
     */
    fun getTransparentSignatureBitmap(paddingPx: Int = 20): Bitmap? {
        if (isEmpty() || width <= 0 || height <= 0) return null

        val fullBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)

        val renderPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = strokeColor
            strokeWidth = strokeWidthPx
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (stroke in strokes) {
            canvas.drawPath(stroke.path, renderPaint)
            for (pt in stroke.points) {
                if (pt.x < minX) minX = pt.x
                if (pt.x > maxX) maxX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.y > maxY) maxY = pt.y
            }
        }

        if (minX > maxX || minY > maxY) {
            return fullBitmap
        }

        val cropLeft = maxOf(0, (minX - paddingPx - strokeWidthPx).toInt())
        val cropTop = maxOf(0, (minY - paddingPx - strokeWidthPx).toInt())
        val cropRight = minOf(width, (maxX + paddingPx + strokeWidthPx).toInt())
        val cropBottom = minOf(height, (maxY + paddingPx + strokeWidthPx).toInt())

        val cropWidth = maxOf(1, cropRight - cropLeft)
        val cropHeight = maxOf(1, cropBottom - cropTop)

        val croppedBitmap = Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
        if (croppedBitmap != fullBitmap) {
            fullBitmap.recycle()
        }
        return croppedBitmap
    }
}
