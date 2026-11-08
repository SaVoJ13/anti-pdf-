package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.savoj.volumpdf.core.pdf.MarkupLayout

/**
 * Малює незбережені ✓/✗ і текст над PdfView (положення — щокадру з моста) і, у режимі редагування, виділяє
 * та переносить їх. Дотик перехоплюється ЛИШЕ коли DOWN потрапив на доданий об'єкт (межі + 24 dp); інакше
 * [onTouchEvent] повертає false, і жест іде у PdfView (скрол, зум, подвійний тап, тап для нового об'єкта).
 * Другий палець під час перетягування ігнорується.
 */
class MarkupOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface EditListener {
        /** Тап по об'єкту (без перетягування). */
        fun onItemTapped(id: String)

        /** Перетягування завершено: нова опорна точка в pt (ще без кламп сторінки — їх робить редактор). */
        fun onItemMoved(id: String, x: Float, y: Float)
    }

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

    var selectedId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /** true — режим «Поставити»: об'єкти можна виділяти й переносити. */
    var editingEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) cancelGesture()
        }

    var editListener: EditListener? = null

    private val viewportListener: (Viewport) -> Unit = { invalidate() }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = loadTypeface()
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = FRAME_COLOR
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
    }
    private val frameHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = 0xCCFFFFFF.toInt()
    }
    private val path = Path()
    private val rectF = RectF()

    private fun loadTypeface(): Typeface = try {
        Typeface.createFromAsset(context.assets, MarkupLayout.FONT_ASSET_PATH)
    } catch (_: Exception) {
        Typeface.DEFAULT
    }

    // --- положення об'єктів у пікселях цього в'ю --------------------------------------------------

    /** Нове положення під час перетягування (pt); null — не перетягуємо. */
    private var dragId: String? = null
    private var dragX = 0f
    private var dragY = 0f

    /** Опорна точка об'єкта в пікселях цього в'ю з урахуванням перетягування; null — сторінка ще не розкладена. */
    private fun originPx(item: PendingMarkup, offset: IntArray): Pair<Float, Float>? {
        val b = bridge ?: return null
        val useDrag = item.id == dragId && moved
        val x = if (useDrag) dragX else item.x
        val y = if (useDrag) dragY else item.y
        val p = b.pageToView(PageViewPoint(item.pageIndex, x, y)) ?: return null
        return Pair(p.x + offset[0], p.y + offset[1])
    }

    /** Ширина рядка в пікселях — тим самим Paint і розміром, що й малювання. */
    private fun lineWidthPx(sizePt: Float, zoom: Float): (String) -> Float {
        textPaint.textSize = MarkupOverlayMath.ptToPx(sizePt, zoom)
        return { line -> textPaint.measureText(line) }
    }

    /** Межі всіх об'єктів у пікселях: одне джерело для рамки виділення й зони дотику. */
    private fun targetsNow(): List<HitTarget> {
        val b = bridge ?: return emptyList()
        val offset = b.offsetTo(this) ?: return emptyList()
        val zoom = b.zoom
        val out = ArrayList<HitTarget>(items.size)
        for (item in items) {
            val o = originPx(item, offset) ?: continue
            out += HitTarget(item.id, PlaceBounds.boundsPx(item, o.first, o.second, zoom, lineWidthPx(item.sizePt, zoom)))
        }
        return out
    }

    // --- дотик ------------------------------------------------------------------------------------

    private var gestureActive = false
    private var activePointerId = -1
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var startZoom = 1f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editingEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val id = PlaceHitTest.hit(targetsNow(), event.x, event.y, PlaceHitTest.SLOP_DP * density)
                    ?: return false // повз об'єкти: жест іде у PdfView
                val item = items.firstOrNull { it.id == id } ?: return false
                gestureActive = true
                activePointerId = event.getPointerId(0)
                moved = false
                downX = event.x
                downY = event.y
                startX = item.x
                startY = item.y
                startZoom = bridge?.zoom?.takeIf { it > 0f } ?: 1f
                dragId = id
                dragX = item.x
                dragY = item.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureActive) return false
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0 || dragId == null) return true // другий палець або кінець жесту: ігноруємо
                val dx = event.getX(idx) - downX
                val dy = event.getY(idx) - downY
                if (!moved && Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) moved = true
                if (moved) {
                    val item = items.firstOrNull { it.id == dragId }
                    val page = item?.let { bridge?.pageSizePt(it.pageIndex) }
                    val c = PlaceMarkupLogic.clampToPage(startX + dx / startZoom, startY + dy / startZoom, page)
                    dragX = c.first
                    dragY = c.second
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return gestureActive // другий палець ігноруємо
            MotionEvent.ACTION_POINTER_UP -> {
                if (!gestureActive) return false
                // Якщо підняли саме активний палець — завершуємо жест; решта пальців ігнорується до кінця.
                if (event.getPointerId(event.actionIndex) == activePointerId) finishGesture(commit = true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureActive) return false
                if (event.getPointerId(event.actionIndex) == activePointerId) finishGesture(commit = true)
                endGesture()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!gestureActive) return false
                cancelGesture()
                return true
            }
        }
        return gestureActive
    }

    private fun finishGesture(commit: Boolean) {
        val id = dragId ?: return
        val wasMoved = moved
        val x = dragX
        val y = dragY
        dragId = null
        moved = false
        invalidate()
        if (!commit) return
        if (wasMoved) editListener?.onItemMoved(id, x, y) else {
            editListener?.onItemTapped(id)
            performClick()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun endGesture() {
        gestureActive = false
        activePointerId = -1
        dragId = null
        moved = false
    }

    private fun cancelGesture() {
        dragId = null
        moved = false
        gestureActive = false
        activePointerId = -1
        invalidate()
    }

    override fun onDetachedFromWindow() {
        bridge?.removeViewportListener(viewportListener)
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bridge?.addViewportListener(viewportListener)
    }

    // --- малювання --------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val b = bridge ?: return
        val list = items
        if (list.isEmpty()) return
        val offset = b.offsetTo(this) ?: return
        val zoom = b.zoom
        for (item in list) {
            val o = originPx(item, offset) ?: continue
            when (item.tool) {
                PlaceTool.CHECK, PlaceTool.CROSS -> drawMark(canvas, item, o.first, o.second, zoom)
                PlaceTool.TEXT -> drawText(canvas, item, o.first, o.second, zoom)
            }
        }
        val sel = selectedId?.let { id -> list.firstOrNull { it.id == id } }
        if (sel != null) {
            val o = originPx(sel, offset)
            if (o != null) {
                val r = PlaceBounds.boundsPx(sel, o.first, o.second, zoom, lineWidthPx(sel.sizePt, zoom))
                val pad = 4f * density
                rectF.set(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
                canvas.drawRoundRect(rectF, 4f * density, 4f * density, frameHaloPaint)
                canvas.drawRoundRect(rectF, 4f * density, 4f * density, framePaint)
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

    private companion object {
        const val FRAME_COLOR = 0xFF1565C0.toInt()
    }
}
