package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * Малює незбережені ✓/✗, текст, штрихи й підписи над PdfView (положення — щокадру з моста) і, у режимі
 * редагування, виділяє та переносить їх. Дотик перехоплюється ЛИШЕ коли DOWN потрапив на доданий об'єкт
 * (✓/✗/підпис/текст — межі + 24 dp, штрих — відстань до ламаної ≤ 24 dp); інакше [onTouchEvent] повертає false,
 * і жест іде у PdfView (скрол, зум, подвійний тап, тап для нового об'єкта). Другий палець під час перетягування
 * ігнорується. Коли активний інструмент «Малювати» ([drawingEnabled]), оверлей забирає кожен дотик (один палець
 * чи S Pen = один штрих); прокрутку й зум тоді дає інструмент «Рука».
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

        /**
         * Завершено штрих (від дотику до відпускання). [xy] — точки в просторі сторінки [pageIndex] (сторінка
         * першої точки), pt, плоский масив пар; відсікання надлишку й перевірку «на сторінці» робить виклик.
         */
        fun onInkDrawn(pageIndex: Int, xy: FloatArray)

        /** Зміна тексту або розміру вікна з текстом (ресайз маркерів). */
        fun onItemTextChanged(id: String, newText: String, rawText: String? = null)
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
            if (!value) {
                cancelGesture()
                cancelStroke()
            }
        }

    /** true — інструмент «Малювати»: кожен дотик малює штрих. */
    var drawingEnabled: Boolean = false
        set(value) {
            if (field && !value) cancelStroke()
            field = value
            invalidate()
        }

    /** Колір (ARGB) і товщина (pt) штриха, що малюється зараз. */
    var inkColor: Int = 0xFFD32F2F.toInt()
    var inkWidthPt: Float = PlaceMarkupLogic.DEFAULT_INK_WIDTH_PT

    /** Картинки підписів за id об'єкта. */
    var signatureBitmaps: ((String) -> Bitmap?)? = null

    var editListener: EditListener? = null

    private val viewportListener: (Viewport) -> Unit = { invalidate() }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // D2: у PDF ширина рядка = сума горизонтальних просувів шрифту при розмірі в pt без кернінгу й
    // без округлення; тому лінійний текст, субпіксельне позиціонування та вимкнений кернінг.
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        style = Paint.Style.FILL
        typeface = loadTypeface()
        isLinearText = true
        isSubpixelText = true
        fontFeatureSettings = "'kern' 0, 'liga' 0"
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
    private val rectHandle = RectF()

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = FRAME_COLOR
    }
    private val handleGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
        color = FRAME_COLOR
    }
    private val handleHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x263949AB.toInt()
    }

    /** Стан ресайзу вікна тексту за маркери ширини. */
    private var isResizing = false
    private var resizeId: String? = null
    private var startWidthPt = 0f
    private var originalText = ""
    private var dragText: String? = null

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

    /** Межі всіх об'єктів у пікселях: одне джерело для рамки виділення й зони дотику (штрих — ще й ламана). */
    private fun targetsNow(): List<HitTarget> {
        val b = bridge ?: return emptyList()
        val offset = b.offsetTo(this) ?: return emptyList()
        val zoom = b.zoom
        val out = ArrayList<HitTarget>(items.size)
        for (item in items) {
            val o = originPx(item, offset) ?: continue
            val rect = PlaceBounds.boundsPx(item, o.first, o.second, zoom, lineWidthPx(item.sizePt, zoom))
            if (item.tool == PlaceTool.INK) {
                out += HitTarget(
                    item.id, rect, PlaceBounds.inkPointsPx(item, o.first, o.second, zoom), item.sizePt * zoom / 2f
                )
            } else {
                out += HitTarget(item.id, rect)
            }
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
        if (drawingEnabled) return onDrawTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Перевірка натискання на маркери зміни ширини для виділеного тексту
                val sel = selectedId?.let { id -> items.firstOrNull { it.id == id } }
                if (sel != null && sel.tool == PlaceTool.TEXT) {
                    val b = bridge
                    val zoom = b?.zoom?.takeIf { it > 0f } ?: 1f
                    val offset = b?.offsetTo(this)
                    if (offset != null) {
                        val o = originPx(sel, offset)
                        if (o != null) {
                            val r = PlaceBounds.boundsPx(sel, o.first, o.second, zoom, lineWidthPx(sel.sizePt, zoom))
                            val pad = 4f * density
                            val hx = r.right + pad
                            val hy = (r.top + r.bottom) / 2f
                            val cx = r.right + pad
                            val cy = r.bottom + pad
                            val slop = 28f * density
                            val hitRight = Math.hypot((event.x - hx).toDouble(), (event.y - hy).toDouble()) <= slop
                            val hitCorner = Math.hypot((event.x - cx).toDouble(), (event.y - cy).toDouble()) <= slop
                            if (hitRight || hitCorner) {
                                gestureActive = true
                                isResizing = true
                                resizeId = sel.id
                                activePointerId = event.getPointerId(0)
                                downX = event.x
                                downY = event.y
                                startZoom = zoom
                                startWidthPt = (r.right - r.left) / zoom
                                originalText = sel.rawText ?: PlaceMarkupLogic.unwrapText(sel.text)
                                dragText = sel.text
                                parent?.requestDisallowInterceptTouchEvent(true)
                                return true
                            }
                        }
                    }
                }

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
                if (idx < 0) return true
                if (isResizing) {
                    val sel = items.firstOrNull { it.id == resizeId }
                    if (sel != null) {
                        val dxPx = event.getX(idx) - downX
                        val dxPt = dxPx / startZoom
                        val page = bridge?.pageSizePt(sel.pageIndex)
                        val minWidthPt = PlaceMarkupLogic.MIN_TEXT_LINE_WIDTH_PT
                        val maxWidthPt = if (page != null) {
                            (page.widthPt - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT - sel.x).coerceAtLeast(minWidthPt)
                        } else {
                            2000f
                        }
                        val targetWidthPt = (startWidthPt + dxPt).coerceIn(minWidthPt, maxWidthPt)
                        val measure: (String) -> Float = { str ->
                            textPaint.textSize = MarkupOverlayMath.ptToPx(sel.sizePt, startZoom)
                            textPaint.measureText(str) / startZoom
                        }
                        val newWrapped = PlaceMarkupLogic.wrapTextToWidth(
                            originalText, targetWidthPt, sel.sizePt, measure
                        )
                        dragText = newWrapped
                        invalidate()
                    }
                    return true
                }
                if (dragId == null) return true // другий палець або кінець жесту: ігноруємо
                val dx = event.getX(idx) - downX
                val dy = event.getY(idx) - downY
                if (!moved && Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) moved = true
                if (moved) {
                    val item = items.firstOrNull { it.id == dragId }
                    if (item != null) {
                        val page = bridge?.pageSizePt(item.pageIndex)
                        val c = PlaceMarkupLogic.clampedAnchor(item, startX + dx / startZoom, startY + dy / startZoom, page)
                        dragX = c.first
                        dragY = c.second
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return gestureActive // другий палець ігноруємо
            MotionEvent.ACTION_POINTER_UP -> {
                if (!gestureActive) return false
                // Якщо підняли саме активний палець — завершуємо жест; решта пальців ігнорується до кінця.
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    if (isResizing) finishResize(commit = true) else finishGesture(commit = true)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureActive) return false
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    if (isResizing) finishResize(commit = true) else finishGesture(commit = true)
                }
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

    private fun finishResize(commit: Boolean) {
        val id = resizeId ?: return
        val textToCommit = dragText
        val raw = originalText
        isResizing = false
        resizeId = null
        dragText = null
        invalidate()
        if (commit && textToCommit != null) {
            editListener?.onItemTextChanged(id, textToCommit, raw)
        }
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
        isResizing = false
        resizeId = null
        dragText = null
        activePointerId = -1
        dragId = null
        moved = false
    }

    private fun cancelGesture() {
        isResizing = false
        resizeId = null
        dragText = null
        dragId = null
        moved = false
        gestureActive = false
        activePointerId = -1
        invalidate()
    }

    // --- малювання штриха пальцем / S Pen ---------------------------------------------------------

    private var liveXy = FloatArray(256)
    private var liveCount = 0
    private var drawPointerId = -1

    private fun addLivePoint(x: Float, y: Float) {
        if (liveCount > 0) {
            val dx = x - liveXy[(liveCount - 1) * 2]
            val dy = y - liveXy[(liveCount - 1) * 2 + 1]
            if (dx * dx + dy * dy < MIN_LIVE_STEP_PX * MIN_LIVE_STEP_PX) return
        }
        if ((liveCount + 1) * 2 > liveXy.size) liveXy = liveXy.copyOf(liveXy.size * 2)
        liveXy[liveCount * 2] = x
        liveXy[liveCount * 2 + 1] = y
        liveCount++
    }

    private fun onDrawTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) return false
                liveCount = 0
                drawPointerId = event.getPointerId(0)
                addLivePoint(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (drawPointerId < 0) return false
                val idx = event.findPointerIndex(drawPointerId)
                if (idx < 0) return true
                for (h in 0 until event.historySize) addLivePoint(event.getHistoricalX(idx, h), event.getHistoricalY(idx, h))
                addLivePoint(event.getX(idx), event.getY(idx))
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return drawPointerId >= 0 // другий палець ігноруємо
            MotionEvent.ACTION_POINTER_UP -> {
                if (drawPointerId >= 0 && event.getPointerId(event.actionIndex) == drawPointerId) {
                    val idx = event.actionIndex
                    addLivePoint(event.getX(idx), event.getY(idx))
                    finishStroke()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (drawPointerId < 0) return false
                val idx = event.findPointerIndex(drawPointerId)
                if (idx >= 0) addLivePoint(event.getX(idx), event.getY(idx))
                finishStroke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drawPointerId < 0) return false
                cancelStroke()
                return true
            }
        }
        return drawPointerId >= 0
    }

    private fun cancelStroke() {
        liveCount = 0
        drawPointerId = -1
        invalidate()
    }

    /** Перетворює зібрані точки на простір сторінки першої точки й віддає слухачу. */
    private fun finishStroke() {
        val count = liveCount
        val xy = liveXy.copyOf(count * 2)
        liveCount = 0
        drawPointerId = -1
        invalidate()
        val b = bridge ?: return
        if (count == 0) return
        val offset = b.offsetTo(this) ?: return
        // Координати PdfView = координати оверлея мінус зсув початку PdfView відносно оверлея.
        val viewXy = FloatArray(xy.size) { i -> xy[i] - offset[i % 2] }
        val first = b.viewToPage(viewXy[0], viewXy[1]) ?: return // перша точка не на сторінці: штрих не додається
        val pageXy = b.viewToPageSpace(first.pageIndex, viewXy) ?: return
        editListener?.onInkDrawn(first.pageIndex, pageXy)
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
        val live = liveCount > 0
        if (list.isEmpty() && !live) return
        val offset = b.offsetTo(this) ?: return
        val zoom = b.zoom
        for (item in list) {
            val o = originPx(item, offset) ?: continue
            val activeItem = if (isResizing && item.id == resizeId && dragText != null) {
                item.copy(text = dragText!!)
            } else {
                item
            }
            when (activeItem.tool) {
                PlaceTool.CHECK, PlaceTool.CROSS -> drawMark(canvas, activeItem, o.first, o.second, zoom)
                PlaceTool.TEXT -> drawText(canvas, activeItem, o.first, o.second, zoom)
                PlaceTool.INK -> drawInk(canvas, activeItem, o.first, o.second, zoom)
                PlaceTool.SIGNATURE -> drawSignature(canvas, activeItem, o.first, o.second, zoom)
                PlaceTool.HAND -> Unit
            }
        }
        if (live) drawLiveStroke(canvas, zoom)
        val sel = selectedId?.let { id -> list.firstOrNull { it.id == id } }
        if (sel != null) {
            val activeSel = if (isResizing && sel.id == resizeId && dragText != null) {
                sel.copy(text = dragText!!)
            } else {
                sel
            }
            val o = originPx(activeSel, offset)
            if (o != null) {
                val r = PlaceBounds.boundsPx(activeSel, o.first, o.second, zoom, lineWidthPx(activeSel.sizePt, zoom))
                val pad = 4f * density
                rectF.set(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
                canvas.drawRoundRect(rectF, 4f * density, 4f * density, frameHaloPaint)
                canvas.drawRoundRect(rectF, 4f * density, 4f * density, framePaint)

                // Маркери зміни ширини для текстового об'єкта
                if (activeSel.tool == PlaceTool.TEXT) {
                    val hx = rectF.right
                    val hy = (rectF.top + rectF.bottom) / 2f
                    val cx = rectF.right
                    val cy = rectF.bottom

                    // Правий маркер ширини (вертикальна пігулка з насічками)
                    val pillW = 6f * density
                    val pillH = 22f * density
                    rectHandle.set(hx - pillW / 2f, hy - pillH / 2f, hx + pillW / 2f, hy + pillH / 2f)
                    canvas.drawRoundRect(rectHandle, pillW / 2f, pillW / 2f, handleHaloPaint)
                    canvas.drawRoundRect(rectHandle, pillW / 2f, pillW / 2f, handlePaint)
                    canvas.drawRoundRect(rectHandle, pillW / 2f, pillW / 2f, handleStrokePaint)
                    canvas.drawLine(hx - 1.2f * density, hy - 4.5f * density, hx - 1.2f * density, hy + 4.5f * density, handleGripPaint)
                    canvas.drawLine(hx + 1.2f * density, hy - 4.5f * density, hx + 1.2f * density, hy + 4.5f * density, handleGripPaint)

                    // Нижній правий кутовий маркер (кружечок)
                    val cornerR = 5f * density
                    canvas.drawCircle(cx, cy, cornerR + 2f * density, handleHaloPaint)
                    canvas.drawCircle(cx, cy, cornerR, handlePaint)
                    canvas.drawCircle(cx, cy, cornerR, handleStrokePaint)
                }
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

    private fun drawInk(canvas: Canvas, item: PendingMarkup, ox: Float, oy: Float, zoom: Float) {
        val pts = PlaceBounds.inkPointsPx(item, ox, oy, zoom)
        if (pts.size < 2) return
        buildInkPath(pts, zoom)
        strokePaint.color = item.color
        strokePaint.strokeWidth = item.sizePt * zoom
        canvas.drawPath(path, strokePaint)
    }

    /**
     * Контур штриха — той самий, що малює двигун (MarkupDrawing.tracePath): moveTo(p0); для кожної наступної точки
     * квадратична крива з контролем у попередній точці до середини відрізка; наприкінці lineTo(остання).
     * Одна точка — відрізок 0,1 pt (з круглими кінцями це крапка).
     */
    private fun buildInkPath(pts: FloatArray, zoom: Float) {
        path.rewind()
        val n = pts.size / 2
        if (n == 0) return
        if (n == 1) {
            path.moveTo(pts[0], pts[1])
            path.lineTo(pts[0] + 0.1f * zoom, pts[1] + 0.1f * zoom)
            return
        }
        path.moveTo(pts[0], pts[1])
        var prevX = pts[0]
        var prevY = pts[1]
        for (i in 1 until n - 1) {
            val cx = pts[i * 2]
            val cy = pts[i * 2 + 1]
            path.quadTo(prevX, prevY, (prevX + cx) / 2f, (prevY + cy) / 2f)
            prevX = cx
            prevY = cy
        }
        path.lineTo(pts[(n - 1) * 2], pts[(n - 1) * 2 + 1])
    }

    private fun drawLiveStroke(canvas: Canvas, zoom: Float) {
        buildInkPath(liveXy.copyOf(liveCount * 2), zoom)
        strokePaint.color = inkColor
        strokePaint.strokeWidth = inkWidthPt * zoom
        canvas.drawPath(path, strokePaint)
    }

    private fun drawSignature(canvas: Canvas, item: PendingMarkup, x: Float, y: Float, zoom: Float) {
        val bitmap = signatureBitmaps?.invoke(item.id) ?: return
        val w = item.sizePt * zoom
        val h = item.sizePt * item.aspect * zoom
        rectF.set(x, y, x + w, y + h)
        canvas.drawBitmap(bitmap, null, rectF, bitmapPaint)
    }

    private companion object {
        const val FRAME_COLOR = 0xFF3949AB.toInt()
        /** Дотики ближчі за це (px) до попередньої точки живого штриха пропускаємо. */
        const val MIN_LIVE_STEP_PX = 0.5f
    }
}
