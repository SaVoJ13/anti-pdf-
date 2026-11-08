package com.savoj.volumpdf.ui.reader

import kotlin.math.abs

/** Положення переглядача: сторінка, zoom і скрол у пікселях. Без залежностей від androidx.pdf. */
data class ViewportState(
    val firstVisiblePage: Int,
    val zoom: Float,
    val scrollX: Int,
    val scrollY: Int
)

/** Рішення планувальника на черговому кроці (кожні ~16 мс). */
enum class RestoreDecision {
    /** Ще не збіглося: застосувати збережений стан і повторити через 16 мс. */
    APPLY_AND_RETRY,

    /** Збіглося й тримається стабільно: показати переглядач. */
    MATCHED,

    /** 3 с минуло без стабільного збігу: показати переглядач як є. */
    TIMED_OUT,

    /** Користувач торкнувся екрана: не боротися з його жестом, показати переглядач. */
    INTERRUPTED
}

/**
 * Чиста логіка відновлення (R12): повтор до збігу, таймаут, переривання дотиком.
 *
 * Допуски: zoom ±0,01, скрол ±2 px (MARKUP_STAGES, етап 2). Одразу після `onLoadDocumentSuccess`
 * у переглядача ще старий стан, який за 5…150 мс скидається в 0 (SPIKE_PAGE_COORDS 9.4), тому
 * збіг має триматися [stableMs] поспіль, інакше «збіг» зі старим станом показав би початок документа.
 */
class ViewportRestorePlanner(
    val target: ViewportState,
    private val startMs: Long,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val stableMs: Long = DEFAULT_STABLE_MS,
    private val zoomTolerance: Float = ZOOM_TOLERANCE,
    private val scrollTolerancePx: Int = SCROLL_TOLERANCE_PX
) {
    private var matchingSinceMs: Long = NOT_MATCHING

    fun matches(current: ViewportState): Boolean =
        current.firstVisiblePage == target.firstVisiblePage &&
            abs(current.zoom - target.zoom) <= zoomTolerance &&
            abs(current.scrollX - target.scrollX) <= scrollTolerancePx &&
            abs(current.scrollY - target.scrollY) <= scrollTolerancePx

    fun onTick(nowMs: Long, current: ViewportState, userTouched: Boolean): RestoreDecision {
        if (userTouched) return RestoreDecision.INTERRUPTED

        if (matches(current)) {
            if (matchingSinceMs == NOT_MATCHING) matchingSinceMs = nowMs
            if (nowMs - matchingSinceMs >= stableMs) return RestoreDecision.MATCHED
        } else {
            matchingSinceMs = NOT_MATCHING
        }

        return if (nowMs - startMs >= timeoutMs) RestoreDecision.TIMED_OUT
        else RestoreDecision.APPLY_AND_RETRY
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3_000L
        const val DEFAULT_STABLE_MS = 150L
        const val ZOOM_TOLERANCE = 0.01f
        const val SCROLL_TOLERANCE_PX = 2
        const val TICK_MS = 16L
        private const val NOT_MATCHING = -1L
    }
}
