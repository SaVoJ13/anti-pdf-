package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressLogicTest {

    // --- primaryPage ----------------------------------------------------------------------------

    @Test
    fun `primaryPage - empty bounds or zero viewHeight falls back to firstVisiblePage`() {
        assertEquals(3, ReadingProgressLogic.primaryPage(3, 2800, emptyList()))
        assertEquals(3, ReadingProgressLogic.primaryPage(3, 0, listOf(PageVerticalBounds(3, 0f, 1000f))))
        assertEquals(0, ReadingProgressLogic.primaryPage(-1, 0, emptyList()))
    }

    @Test
    fun `primaryPage - at top of document selects page 0`() {
        val bounds = listOf(
            PageVerticalBounds(0, 0f, 2037f),
            PageVerticalBounds(1, 2057f, 4094f)
        )
        assertEquals(0, ReadingProgressLogic.primaryPage(0, 2800, bounds))
    }

    @Test
    fun `primaryPage - ignores thin strip of previous page at top of screen`() {
        // На екрані 2800 px угорі лишилася смужка 40 px сторінки 11, а сторінка 12 займає 2037 px
        val bounds = listOf(
            PageVerticalBounds(11, -1997f, 40f),
            PageVerticalBounds(12, 60f, 2097f),
            PageVerticalBounds(13, 2117f, 4154f)
        )
        assertEquals(12, ReadingProgressLogic.primaryPage(11, 2800, bounds))
    }

    @Test
    fun `primaryPage - reaches last page on tall screen when previous page is still partially visible`() {
        // Galaxy S24 Ultra (viewHeight = 2800, висота сторінки A4 = 2037): у самому низу документа
        // firstVisiblePage дорівнює передостанній сторінці (18), але остання (19) займає більшу частину екрана
        val bounds = listOf(
            PageVerticalBounds(18, -1300f, 737f),
            PageVerticalBounds(19, 757f, 2800f)
        )
        assertEquals(19, ReadingProgressLogic.primaryPage(18, 2800, bounds))
    }

    @Test
    fun `primaryPage - tie between two fully visible short pages after scroll picks page closest to center`() {
        // Дві короткі сторінки (по 800 px) повністю у вікні 2800 px після scrollToPage(5), що центрує сторінку 5 (y=1000..1800)
        val bounds = listOf(
            PageVerticalBounds(4, 180f, 980f),
            PageVerticalBounds(5, 1000f, 1800f),
            PageVerticalBounds(6, 1820f, 2620f)
        )
        assertEquals(5, ReadingProgressLogic.primaryPage(4, 2800, bounds))
    }

    // --- shouldRecordPage -----------------------------------------------------------------------

    @Test
    fun `shouldRecordPage - deduplicates same page and rejects out-of-range pages`() {
        assertFalse(ReadingProgressLogic.shouldRecordPage(-1, 0, 10))
        assertFalse(ReadingProgressLogic.shouldRecordPage(10, 0, 10))
        assertFalse(ReadingProgressLogic.shouldRecordPage(15, 0, 10))
        assertFalse(ReadingProgressLogic.shouldRecordPage(5, 5, 10))

        assertTrue(ReadingProgressLogic.shouldRecordPage(5, 4, 10))
        assertTrue(ReadingProgressLogic.shouldRecordPage(0, 4, 10))
        assertTrue(ReadingProgressLogic.shouldRecordPage(9, 0, 10))
        // Коли pageCount ще 0 (невідомий), валідні невід'ємні сторінки дозволені
        assertTrue(ReadingProgressLogic.shouldRecordPage(3, 0, 0))
    }

    // --- targetRestorePage ----------------------------------------------------------------------

    @Test
    fun `targetRestorePage - returns 0 for first page or single-page doc and clamps to last page`() {
        assertEquals(0, ReadingProgressLogic.targetRestorePage(0, 20))
        assertEquals(0, ReadingProgressLogic.targetRestorePage(-2, 20))
        assertEquals(0, ReadingProgressLogic.targetRestorePage(5, 1))
        assertEquals(0, ReadingProgressLogic.targetRestorePage(5, 0))

        assertEquals(12, ReadingProgressLogic.targetRestorePage(12, 20))
        assertEquals(19, ReadingProgressLogic.targetRestorePage(19, 20))
        // Якщо файл було скорочено з 20 до 8 сторінок, відновлюємо на останню (індекс 7)
        assertEquals(7, ReadingProgressLogic.targetRestorePage(12, 8))
    }

    // --- formatPagesSubtitle --------------------------------------------------------------------

    @Test
    fun `formatPagesSubtitle - shows total pages at page 0 and 1-based progress when lastPageRead greater than 0`() {
        val countFmt: (Int) -> String = { "$it стор." }
        val progFmt: (Int, Int) -> String = { cur, total -> "Стор. $cur з $total" }

        assertEquals("", ReadingProgressLogic.formatPagesSubtitle(0, 0, countFmt, progFmt))
        assertEquals("42 стор.", ReadingProgressLogic.formatPagesSubtitle(42, 0, countFmt, progFmt))
        assertEquals("Стор. 13 з 42", ReadingProgressLogic.formatPagesSubtitle(42, 12, countFmt, progFmt))
        assertEquals("Стор. 42 з 42", ReadingProgressLogic.formatPagesSubtitle(42, 41, countFmt, progFmt))
        // Захист від виходу за межі, якщо lastPageRead >= pageCount
        assertEquals("Стор. 10 з 10", ReadingProgressLogic.formatPagesSubtitle(10, 25, countFmt, progFmt))
    }

    @Test
    fun `progressPercent - computes percentage for Continue Reading card and list items`() {
        assertEquals(0, ReadingProgressLogic.progressPercent(0, 0, alwaysShowFirstPage = true))
        assertEquals(0, ReadingProgressLogic.progressPercent(-1, 10, alwaysShowFirstPage = true))
        // List item at page 0 of 10 -> 0% (no progress bar needed yet)
        assertEquals(0, ReadingProgressLogic.progressPercent(0, 10, alwaysShowFirstPage = false))
        // Continue Reading card at page 0 of 10 -> 10% (page 1 of 10)
        assertEquals(10, ReadingProgressLogic.progressPercent(0, 10, alwaysShowFirstPage = true))
        // Page 5 (index 4) of 20 -> 25%
        assertEquals(25, ReadingProgressLogic.progressPercent(4, 20, alwaysShowFirstPage = false))
        assertEquals(25, ReadingProgressLogic.progressPercent(4, 20, alwaysShowFirstPage = true))
        // Last page (index 19) of 20 -> 100%
        assertEquals(100, ReadingProgressLogic.progressPercent(19, 20, alwaysShowFirstPage = false))
        // Single-page document -> 100%
        assertEquals(100, ReadingProgressLogic.progressPercent(0, 1, alwaysShowFirstPage = false))
    }

    // --- PageRestorePlanner ---------------------------------------------------------------------

    @Test
    fun `PageRestorePlanner - waits for viewReady and stable window before matching`() {
        val p = PageRestorePlanner(targetPage = 12, startMs = 1000, timeoutMs = 3000, stableMs = 100)
        // в'ю ще не розкладене
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1000, currentPage = 12, viewReady = false, userTouched = false))
        // в'ю готове, але ще на сторінці 0
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1016, currentPage = 0, viewReady = true, userTouched = false))
        // перейшло на 12, чекаємо стабільності 100 мс
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1032, currentPage = 12, viewReady = true, userTouched = false))
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1100, currentPage = 12, viewReady = true, userTouched = false))
        assertEquals(RestoreDecision.MATCHED, p.onTick(1132, currentPage = 12, viewReady = true, userTouched = false))
    }

    @Test
    fun `PageRestorePlanner - reset to page 0 restarts stability window`() {
        val p = PageRestorePlanner(targetPage = 12, startMs = 1000, timeoutMs = 3000, stableMs = 100)
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1000, currentPage = 12, viewReady = true, userTouched = false))
        // бібліотека скинула скрол у 0
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1050, currentPage = 0, viewReady = true, userTouched = false))
        // повторне застосування
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1066, currentPage = 12, viewReady = true, userTouched = false))
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1150, currentPage = 12, viewReady = true, userTouched = false))
        assertEquals(RestoreDecision.MATCHED, p.onTick(1166, currentPage = 12, viewReady = true, userTouched = false))
    }

    @Test
    fun `PageRestorePlanner - settledAtTarget also stabilizes and matches`() {
        val p = PageRestorePlanner(targetPage = 2, startMs = 1000, timeoutMs = 3000, stableMs = 100)
        assertEquals(RestoreDecision.APPLY_AND_RETRY, p.onTick(1000, currentPage = 1, viewReady = true, userTouched = false, settledAtTarget = true))
        assertEquals(RestoreDecision.MATCHED, p.onTick(1100, currentPage = 1, viewReady = true, userTouched = false, settledAtTarget = true))
    }

    @Test
    fun `PageRestorePlanner - user touch interrupts and timeout ends after 3s`() {
        val p = PageRestorePlanner(targetPage = 12, startMs = 1000, timeoutMs = 3000, stableMs = 100)
        assertEquals(RestoreDecision.INTERRUPTED, p.onTick(1050, currentPage = 0, viewReady = true, userTouched = true))

        val q = PageRestorePlanner(targetPage = 12, startMs = 1000, timeoutMs = 3000, stableMs = 100)
        assertEquals(RestoreDecision.APPLY_AND_RETRY, q.onTick(3999, currentPage = 0, viewReady = true, userTouched = false))
        assertEquals(RestoreDecision.TIMED_OUT, q.onTick(4000, currentPage = 0, viewReady = true, userTouched = false))
    }
}
