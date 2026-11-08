@file:OptIn(ExperimentalPdfApi::class)

package com.savoj.volumpdf.ui.reader

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.SparseArray
import android.graphics.PointF
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPoint
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment

// ЄДИНИЙ файл в app, де дозволено @ExperimentalPdfApi (MASTER_SPEC R10). Публічні сигнатури нижче
// використовують лише власні типи й типи Android; androidx.pdf назовні не виходить.

/** Точка в просторі PageViewPoint (pt; початок — лівий верхній кут видимої області сторінки). */
data class PageViewPoint(val pageIndex: Int, val x: Float, val y: Float)

/** Знімок положення переглядача. */
data class Viewport(val zoom: Float, val scrollX: Int, val scrollY: Int, val firstVisiblePage: Int)

enum class RestoreOutcome { MATCHED, TIMED_OUT, INTERRUPTED, LOAD_FAILED }

/**
 * Обгортка над PdfView: viewport, перетворення координат, режим «поставити» (R14),
 * перезавантаження з відновленням положення (R12). Тримається у [VolumPdfViewerFragment].
 */
class PdfViewerBridge internal constructor(private val host: VolumPdfViewerFragment) {

    private val handler = Handler(Looper.getMainLooper())
    private var pdfView: PdfView? = null

    // --- viewport -------------------------------------------------------------------------------

    val viewport: Viewport?
        get() = pdfView?.let { Viewport(it.zoom, it.scrollX, it.scrollY, it.firstVisiblePage) }

    private val viewportListeners = mutableListOf<(Viewport) -> Unit>()
    private val viewportListener = object : PdfView.OnViewportChangedListener {
        override fun onViewportChanged(
            firstVisiblePage: Int,
            visiblePagesCount: Int,
            pageLocations: SparseArray<RectF>,
            zoomLevel: Float
        ) {
            val v = viewport ?: return
            viewportListeners.toList().forEach { it(v) }
        }
    }

    fun addViewportListener(listener: (Viewport) -> Unit) { viewportListeners += listener }
    fun removeViewportListener(listener: (Viewport) -> Unit) { viewportListeners -= listener }

    // --- координати -----------------------------------------------------------------------------

    /** Точка сторінки → піксель у PdfView (null, якщо переглядача ще немає). */
    fun pageToView(point: PageViewPoint): PointF? =
        pdfView?.pdfToViewPoint(PdfPoint(point.pageIndex, point.x, point.y))

    /** Масштаб: пікселів на pt (1 px = 1 pt при zoom = 1); 1 — якщо переглядача ще немає. */
    val zoom: Float
        get() = pdfView?.zoom ?: 1f

    /**
     * Зсув початку PdfView відносно [target] у пікселях (x, y): додається до [pageToView], щоб
     * малювати в системі координат іншого в'ю (оверлей). null, якщо переглядача ще немає.
     */
    fun offsetTo(target: View): IntArray? {
        val v = pdfView ?: return null
        val a = IntArray(2)
        val b = IntArray(2)
        v.getLocationOnScreen(a)
        target.getLocationOnScreen(b)
        return intArrayOf(a[0] - b[0], a[1] - b[1])
    }

    /** Піксель PdfView → точка сторінки; pageIndex береться з PdfPoint.pageNum, не з firstVisiblePage. */
    fun viewToPage(x: Float, y: Float): PageViewPoint? =
        pdfView?.viewToPdfPoint(x, y)?.let { PageViewPoint(it.pageNum, it.x, it.y) }

    // --- режим «поставити» (R14) ----------------------------------------------------------------

    private var placeEnabled = false
    private var onPlaceTap: ((PageViewPoint) -> Unit)? = null
    private var gestureDetector: GestureDetector? = null
    private var multiTouchSeen = false

    /**
     * enabled=true: тап (single tap confirmed) → [onTap] з точкою сторінки. Слухач дотику завжди
     * повертає false, тож скрол/зум/подвійний тап бібліотеки живі; довгий тап, рух і мультидотик
     * об'єкт не ставлять; кліки по посиланнях споживаються.
     */
    fun setPlaceMode(enabled: Boolean, onTap: ((PageViewPoint) -> Unit)? = null) {
        placeEnabled = enabled && onTap != null
        onPlaceTap = if (placeEnabled) onTap else null
        applyLinkPolicy()
    }

    private fun applyLinkPolicy() {
        val v = pdfView ?: return
        v.setLinkClickListener(
            object : PdfView.LinkClickListener {
                // true = клік по посиланню спожито (у режимі «поставити» посилання не відкривається);
                // false = поведінка за замовчуванням.
                override fun onLinkClicked(externalLink: androidx.pdf.content.ExternalLink): Boolean = placeEnabled
            }
        )
    }

    // --- дотик користувача (для переривання відновлення й режиму «поставити») ------------------

    @Volatile private var userTouched = false

    @SuppressLint("ClickableViewAccessibility")
    private fun installTouchListener(view: PdfView) {
        gestureDetector = GestureDetector(
            view.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (placeEnabled && !multiTouchSeen) {
                        viewToPage(e.x, e.y)?.let { onPlaceTap?.invoke(it) }
                    }
                    return false
                }
            }
        )
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { multiTouchSeen = false; userTouched = true }
                MotionEvent.ACTION_POINTER_DOWN -> multiTouchSeen = true
            }
            if (placeEnabled) gestureDetector?.onTouchEvent(e)
            false // ніколи не true: інакше бібліотека втрачає скрол, зум і посилання (спайк-2, 9.3)
        }
    }

    // --- перезавантаження з відновленням (R12) -------------------------------------------------

    /** true, поки переглядач прихований і йде відновлення. */
    var isRestoring: Boolean = false
        private set

    private var onRestoreFinished: ((RestoreOutcome) -> Unit)? = null
    private var pendingTarget: ViewportState? = null
    private var reloadStartedMs = 0L
    private var planner: ViewportRestorePlanner? = null
    private var tickRunnable: Runnable? = null
    private var watchdog: Runnable? = null

    /**
     * Завантажує [newUri] (має бути НОВИМ URI, інакше бібліотека не перезавантажить) і відновлює
     * сторінку, zoom і скрол. На час відновлення PdfView має alpha 0.
     */
    fun reload(newUri: Uri, onFinished: ((RestoreOutcome) -> Unit)? = null) {
        val v = pdfView
        if (v == null) {
            host.documentUri = newUri
            onFinished?.invoke(RestoreOutcome.LOAD_FAILED)
            return
        }
        // Повторний reload посеред відновлення: ціль лишається першою (стан зараз неточний).
        val target = pendingTarget ?: ViewportState(v.firstVisiblePage, v.zoom, v.scrollX, v.scrollY)
        cancelRestore(reveal = false)
        pendingTarget = target
        onRestoreFinished = onFinished
        isRestoring = true
        reloadStartedMs = SystemClock.uptimeMillis()
        userTouched = false
        v.alpha = 0f
        watchdog = Runnable { finishRestore(RestoreOutcome.TIMED_OUT) }.also {
            handler.postDelayed(it, LOAD_WAIT_MS)
        }
        host.documentUri = newUri
    }

    internal fun onDocumentLoaded() {
        val target = pendingTarget ?: return
        if (planner != null) return
        handler.removeCallbacks(watchdog ?: return)
        planner = ViewportRestorePlanner(target, startMs = SystemClock.uptimeMillis())
        // Дотики до цього моменту (наприклад, той, що спричинив збереження) не рахуються.
        userTouched = false
        tick()
    }

    internal fun onDocumentFailed() {
        if (pendingTarget != null) finishRestore(RestoreOutcome.LOAD_FAILED)
    }

    private fun tick() {
        val v = pdfView ?: return
        val p = planner ?: return
        val current = ViewportState(v.firstVisiblePage, v.zoom, v.scrollX, v.scrollY)
        when (p.onTick(SystemClock.uptimeMillis(), current, userTouched)) {
            RestoreDecision.MATCHED -> finishRestore(RestoreOutcome.MATCHED)
            RestoreDecision.TIMED_OUT -> finishRestore(RestoreOutcome.TIMED_OUT)
            RestoreDecision.INTERRUPTED -> finishRestore(RestoreOutcome.INTERRUPTED)
            RestoreDecision.APPLY_AND_RETRY -> {
                val t = p.target
                v.zoom = t.zoom
                v.scrollTo(t.scrollX, t.scrollY)
                tickRunnable = Runnable { tick() }.also { handler.postDelayed(it, ViewportRestorePlanner.TICK_MS) }
            }
        }
    }

    private fun finishRestore(outcome: RestoreOutcome) {
        val callback = onRestoreFinished
        val target = pendingTarget
        cancelRestore(reveal = true)
        Log.i(TAG, "restore finished: $outcome after=${SystemClock.uptimeMillis() - reloadStartedMs}ms target=$target now=$viewport")
        callback?.invoke(outcome)
    }

    private fun cancelRestore(reveal: Boolean) {
        tickRunnable?.let { handler.removeCallbacks(it) }
        watchdog?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        watchdog = null
        planner = null
        pendingTarget = null
        onRestoreFinished = null
        isRestoring = false
        if (reveal) pdfView?.alpha = 1f
    }

    // --- життєвий цикл --------------------------------------------------------------------------

    internal fun attach(view: PdfView) {
        pdfView = view
        installTouchListener(view)
        applyLinkPolicy()
        view.addOnViewportChangedListener(viewportListener)
    }

    internal fun detach() {
        pdfView?.removeOnViewportChangedListener(viewportListener)
        pdfView?.alpha = 1f
        cancelRestore(reveal = false)
        pdfView = null
        gestureDetector = null
    }

    companion object {
        private const val TAG = "PdfViewerBridge"
        /** Скільки чекати на onLoadDocumentSuccess, перш ніж показати переглядач як є. */
        private const val LOAD_WAIT_MS = 10_000L
    }
}

/** Фрагмент-переглядач. Єдина точка входу в androidx.pdf для решти застосунку — [bridge]. */
class VolumPdfViewerFragment : PdfViewerFragment() {

    var onDocumentLoadedListener: ((pageCount: Int) -> Unit)? = null
    var onDocumentErrorListener: ((cause: Throwable) -> Unit)? = null

    val bridge: PdfViewerBridge = PdfViewerBridge(this)

    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        bridge.attach(pdfView)
    }

    override fun onDestroyView() {
        bridge.detach()
        super.onDestroyView()
    }

    val currentPageIndex: Int
        get() = bridge.viewport?.firstVisiblePage ?: 0

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        onDocumentLoadedListener?.invoke(document.pageCount)
        bridge.onDocumentLoaded()
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        onDocumentErrorListener?.invoke(error)
        bridge.onDocumentFailed()
    }
}
