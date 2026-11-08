package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.savoj.volumpdf.core.pdf.MarkupLayout

/**
 * Малює незбережені ✓/✗ і текст над PdfView. Положення — щокадру з моста (pageToView), масштаб — zoom
 * з моста. Дотики не перехоплює ніколи (R14).
 */
class MarkupOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var bridge: PdfViewerBridge? = null
        set(value) {
            field?.removeViewportListener(viewportListener)
            field = value
            if (isAttachedToWindow) value?.addViewportListener(viewportListener)
            invalidate()
        }

    var items: List<PendingMarkup> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val viewportListener: (Viewport) -> Unit = { invalidate() }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = loadTypeface()
    }
    private val path = Path()

    private fun loadTypeface(): Typeface = try {
        Typeface.createFromAsset(context.assets, MarkupLayout.FONT_ASSET_PATH)
    } catch (_: Exception) {
        Typeface.DEFAULT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onDetachedFromWindow() {
        bridge?.removeViewportListener(viewportListener)
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bridge?.addViewportListener(viewportListener)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bridge ?: return
        val list = items
        if (list.isEmpty()) return
        val offset = b.offsetTo(this) ?: return
        val zoom = b.zoom
        for (item in list) {
            val p = b.pageToView(PageViewPoint(item.pageIndex, item.x, item.y)) ?: continue
            val px = p.x + offset[0]
            val py = p.y + offset[1]
            when (item.tool) {
                PlaceTool.CHECK, PlaceTool.CROSS -> drawMark(canvas, item, px, py, zoom)
                PlaceTool.TEXT -> drawText(canvas, item, px, py, zoom)
            }
        }
        // Перерахунок у кожному кадрі, поки є об'єкти (як у спайку, режим frame).
        postInvalidateOnAnimation()
    }

    private fun drawMark(canvas: Canvas, item: PendingMarkup, cx: Float, cy: Float, zoom: Float) {
        strokePaint.color = item.color
        strokePaint.strokeWidth = MarkupOverlayMath.strokePx(item.sizePt, zoom)
        for (line in MarkupOverlayMath.markPolylinesPx(item.tool == PlaceTool.CHECK, cx, cy, item.sizePt, zoom)) {
            path.rewind()
            path.moveTo(line[0], line[1])
            var i = 2
            while (i < line.size) {
                path.lineTo(line[i], line[i + 1]); i += 2
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawText(canvas: Canvas, item: PendingMarkup, x: Float, y: Float, zoom: Float) {
        textPaint.color = item.color
        textPaint.textSize = MarkupOverlayMath.ptToPx(item.sizePt, zoom)
        for (line in MarkupOverlayMath.textLinesPx(item.text, x, y, item.sizePt, zoom)) {
            if (line.text.isNotEmpty()) canvas.drawText(line.text, line.x, line.baselineY, textPaint)
        }
    }
}
