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
 * Штрихи зберігаються в нормалізованих координатах (0..1 відносно розмірів полотна) і перемасштабовуються
 * при зміні розміру (поворот екрана); bitmap генерується за поточним розміром.
 * Надає метод експорту [getTransparentSignatureBitmap] з прозорим фоном та авто-обрізкою країв.
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Точки штриха в нормалізованих координатах; [path] — кеш у пікселях поточного розміру. */
    private class SignatureStroke(val norm: MutableList<PointF>, var path: Path = Path())

    private val strokes = mutableListOf<SignatureStroke>()
    private var current: SignatureStroke? = null
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

    private fun toNormX(x: Float) = if (width > 0) x / width else 0f
    private fun toNormY(y: Float) = if (height > 0) y / height else 0f

    /** Контур штриха в пікселях за нормалізованими точками й розміром [w]×[h]. */
    private fun buildPath(norm: List<PointF>, w: Int, h: Int): Path {
        val path = Path()
        if (norm.isEmpty()) return path
        var lx = norm[0].x * w
        var ly = norm[0].y * h
        path.moveTo(lx, ly)
        for (i in 1 until norm.size) {
            val x = norm[i].x * w
            val y = norm[i].y * h
            path.quadTo(lx, ly, (lx + x) / 2f, (ly + y) / 2f)
            lx = x
            ly = y
        }
        path.lineTo(lx, ly)
        return path
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        for (s in strokes) s.path = buildPath(s.norm, w, h)
        current?.let { it.path = buildPath(it.norm, w, h) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = strokeColor
        paint.strokeWidth = strokeWidthPx
        for (stroke in strokes) canvas.drawPath(stroke.path, paint)
        current?.let { if (!it.path.isEmpty) canvas.drawPath(it.path, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = SignatureStroke(mutableListOf(PointF(toNormX(x), toNormY(y))))
                s.path.moveTo(x, y)
                current = s
                lastX = x
                lastY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val s = current ?: return true
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)
                if (dx >= 2f || dy >= 2f) {
                    s.path.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                    s.norm.add(PointF(toNormX(x), toNormY(y)))
                    lastX = x
                    lastY = y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val s = current ?: return true
                s.path.lineTo(x, y)
                s.norm.add(PointF(toNormX(x), toNormY(y)))
                strokes.add(s)
                current = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                current = null
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    fun isEmpty(): Boolean = strokes.isEmpty() && current == null

    fun clear() {
        strokes.clear()
        current = null
        invalidate()
    }

    /**
     * Створює чистий Bitmap підпису на прозорому фоні (ARGB_8888)
     * із автоматичною обрізкою порожніх полів довкола штрихів. Розмір — поточний розмір полотна.
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
            canvas.drawPath(buildPath(stroke.norm, width, height), renderPaint)
            for (pt in stroke.norm) {
                val px = pt.x * width
                val py = pt.y * height
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
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
