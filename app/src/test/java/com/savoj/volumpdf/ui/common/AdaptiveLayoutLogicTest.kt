package com.savoj.volumpdf.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdaptiveLayoutLogicTest {

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    @Test
    fun `list columns follow window width`() {
        val min = AdaptiveLayoutLogic.LIST_ITEM_MIN_DP
        val max = AdaptiveLayoutLogic.LIST_MAX_COLUMNS
        assertEquals(1, AdaptiveLayoutLogic.columnsFor(360f, min, max))   // телефон, портрет
        assertEquals(1, AdaptiveLayoutLogic.columnsFor(412f - 32f, min, max))
        assertEquals(2, AdaptiveLayoutLogic.columnsFor(748f, min, max))   // телефон, ландшафт / split на планшеті
        assertEquals(2, AdaptiveLayoutLogic.columnsFor(808f, min, max))   // планшет з обмеженою шириною 840-32
        assertEquals(3, AdaptiveLayoutLogic.columnsFor(1400f, min, max))
        assertEquals(3, AdaptiveLayoutLogic.columnsFor(5000f, min, max))  // не більше максимуму
    }

    @Test
    fun `page organizer columns grow from 2 on phone to 6 on tablet`() {
        val min = AdaptiveLayoutLogic.PAGE_THUMB_MIN_DP
        val max = AdaptiveLayoutLogic.PAGE_MAX_COLUMNS
        assertEquals(2, AdaptiveLayoutLogic.columnsFor(360f, min, max))
        assertEquals(2, AdaptiveLayoutLogic.columnsFor(411f, min, max))
        assertTrue(AdaptiveLayoutLogic.columnsFor(780f, min, max) >= 4)
        assertEquals(6, AdaptiveLayoutLogic.columnsFor(1600f, min, max))
    }

    @Test
    fun `columns never below one even for degenerate input`() {
        assertEquals(1, AdaptiveLayoutLogic.columnsFor(0f, 340f, 3))
        assertEquals(1, AdaptiveLayoutLogic.columnsFor(-5f, 340f, 3))
        assertEquals(1, AdaptiveLayoutLogic.columnsFor(100f, 340f, 0))
    }

    @Test
    fun `content width is capped only above the limit`() {
        assertEquals(1000, AdaptiveLayoutLogic.cappedWidth(1000, 0))
        assertEquals(800, AdaptiveLayoutLogic.cappedWidth(800, 2100))
        assertEquals(2100, AdaptiveLayoutLogic.cappedWidth(4000, 2100))
    }

    @Test
    fun `dimen content_max_width matches logic constant and activities are resizable`() {
        val dimen = File(resDir, "values/adaptive.xml").readText()
        assertTrue(dimen.contains("<dimen name=\"content_max_width\">${AdaptiveLayoutLogic.CONTENT_MAX_WIDTH_DP}dp</dimen>"))
        val manifest = File(resDir, "../AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:resizeableActivity=\"true\""))
        assertTrue(!manifest.contains("android:screenOrientation"))
    }
}
