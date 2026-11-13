package com.savoj.volumpdf.ui.reader

import kotlin.math.abs

/** Вертикальні межі сторінки у системі координат переглядача (px). Без залежностей від androidx.pdf. */
data class PageVerticalBounds(
    val pageIndex: Int,
    val topPx: Float,
    val bottomPx: Float
)

/**
 * Чиста логіка запам'ятовування й відображення прогресу читання (без залежностей від Android View та androidx.pdf).
 */
object ReadingProgressLogic {

    /**
     * Визначає поточну сторінку, яку читає користувач, за видимими межами сторінок у переглядачі.
     *
     * Чому не просто `firstVisiblePage`: `PdfView.firstVisiblePage` повертає першу навіть частково видиму сторінку
     * (наприклад, смужку 5 px попередньої сторінки вгорі екрана або передостанню сторінку в кінці документа на
     * високому екрані Galaxy S24 Ultra, де одна сторінка A4 нижча за висоту екрана). Тому обирається сторінка,
     * яка займає найбільшу видиму вертикальну висоту у вікні `[0, viewHeight]`. При рівній видимій висоті
     * (наприклад, дві низькі сторінки одночасно повністю на екрані після прокрутки) перемагає та, чий центр
     * ближчий до середини екрана, якщо перша сторінка вже зрушена з верху документа.
     */
    fun primaryPage(
        firstVisiblePage: Int,
        viewHeight: Int,
        pageBounds: List<PageVerticalBounds>
    ): Int {
        val fallback = firstVisiblePage.coerceAtLeast(0)
        if (viewHeight <= 0 || pageBounds.isEmpty()) return fallback

        val vh = viewHeight.toFloat()
        val viewCenter = vh / 2f
        val atTopOfDocument = pageBounds.any { it.pageIndex == 0 && it.topPx >= -1f }

        var bestPage = fallback
        var bestVisiblePx = -1f
        var bestCenterDist = Float.MAX_VALUE

        for (b in pageBounds) {
            if (b.pageIndex < 0) continue
            val visibleTop = maxOf(b.topPx, 0f)
            val visibleBottom = minOf(b.bottomPx, vh)
            val visiblePx = (visibleBottom - visibleTop).coerceAtLeast(0f)
            if (visiblePx <= 0f) continue

            val centerDist = abs((b.topPx + b.bottomPx) / 2f - viewCenter)
            if (visiblePx > bestVisiblePx + TIE_EPSILON_PX) {
                bestVisiblePx = visiblePx
                bestPage = b.pageIndex
                bestCenterDist = centerDist
            } else if (!atTopOfDocument && abs(visiblePx - bestVisiblePx) <= TIE_EPSILON_PX) {
                if (centerDist < bestCenterDist) {
                    bestPage = b.pageIndex
                    bestCenterDist = centerDist
                }
            }
        }

        return if (bestVisiblePx > 0f) bestPage else fallback
    }

    /**
     * Чи потрібно записувати [page] у Room як новий прогрес читання.
     * Відсікає некоректні індекси та дублікати тієї самої сторінки під час плавного скролу (60–120 кадрів/с).
     */
    fun shouldRecordPage(page: Int, currentLastPageRead: Int, pageCount: Int): Boolean {
        if (page < 0) return false
        if (pageCount > 0 && page >= pageCount) return false
        return page != currentLastPageRead
    }

    /**
     * Обчислює 0-based сторінку для відновлення при відкритті документа.
     * Повертає 0, якщо відновлення скролу не потрібне (документ на першій сторінці або односторінковий).
     * Якщо файл був скорочений ззовні і `savedPage >= pageCount`, обмежує до останньої сторінки (`pageCount - 1`).
     */
    fun targetRestorePage(savedPage: Int, pageCount: Int): Int {
        if (savedPage <= 0 || pageCount <= 1) return 0
        return savedPage.coerceAtMost(pageCount - 1)
    }

    /**
     * Форматує підпис кількості сторінок / прогресу для списку «Останні документи».
     * Якщо `lastPageRead > 0` — повертає «Стор. X з N» (1-based), інакше — «N стор.».
     */
    fun formatPagesSubtitle(
        pageCount: Int,
        lastPageRead: Int,
        formatCount: (Int) -> String,
        formatProgress: (Int, Int) -> String
    ): String {
        if (pageCount <= 0) return ""
        return if (lastPageRead > 0) {
            val displayPage = (lastPageRead + 1).coerceIn(1, pageCount)
            formatProgress(displayPage, pageCount)
        } else {
            formatCount(pageCount)
        }
    }

    /**
     * Обчислює відсоток прогресу читання (0..100) для візуального індикатора:
     * - для картки «Продовжити читання» (`alwaysShowFirstPage = true`): навіть на сторінці 0 показує прогрес 1-ї сторінки;
     * - для елементів списку (`alwaysShowFirstPage = false`): показує > 0 лише якщо користувач просунувся далі 1-ї сторінки
     *   або якщо документ односторінковий.
     */
    fun progressPercent(
        lastPageRead: Int,
        pageCount: Int,
        alwaysShowFirstPage: Boolean = false
    ): Int {
        if (pageCount <= 0 || lastPageRead < 0) return 0
        if (lastPageRead == 0 && !alwaysShowFirstPage && pageCount > 1) return 0
        val current1Based = (lastPageRead + 1).coerceIn(1, pageCount)
        return ((current1Based * 100f) / pageCount).toInt().coerceIn(1, 100)
    }

    private const val TIE_EPSILON_PX = 0.5f
}

/**
 * Планувальник відновлення збереженої сторінки при відкритті документа (аналог [ViewportRestorePlanner] для
 * початкового переходу до `lastPageRead`). Чекає стабільного збігу впродовж [stableMs], бо одразу після
 * `onLoadDocumentSuccess` переглядач може скидати скрол у 0 (SPIKE_PAGE_COORDS 9.4).
 */
class PageRestorePlanner(
    val targetPage: Int,
    private val startMs: Long,
    private val timeoutMs: Long = ViewportRestorePlanner.DEFAULT_TIMEOUT_MS,
    private val stableMs: Long = ViewportRestorePlanner.DEFAULT_STABLE_MS
) {
    private var matchingSinceMs: Long = NOT_MATCHING

    fun onTick(
        nowMs: Long,
        currentPage: Int,
        viewReady: Boolean,
        userTouched: Boolean,
        settledAtTarget: Boolean = false
    ): RestoreDecision {
        if (userTouched) return RestoreDecision.INTERRUPTED

        if (viewReady && (currentPage == targetPage || settledAtTarget)) {
            if (matchingSinceMs == NOT_MATCHING) matchingSinceMs = nowMs
            if (nowMs - matchingSinceMs >= stableMs) return RestoreDecision.MATCHED
        } else {
            matchingSinceMs = NOT_MATCHING
        }

        return if (nowMs - startMs >= timeoutMs) RestoreDecision.TIMED_OUT
        else RestoreDecision.APPLY_AND_RETRY
    }

    companion object {
        private const val NOT_MATCHING = -1L
    }
}
