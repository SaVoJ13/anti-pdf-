package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/** QA PR #7, спостереження (б): Enter користувача (одиничний '\n') мусить лишатися переносом рядка, а не пробілом. */
class PlaceTextHardBreakTest {
    private val measure: (String) -> Float = { it.length * 10f }

    @Test
    fun singleNewlineTypedByUserIsKept() {
        assertEquals("ALPHA\nBETA", PlaceMarkupLogic.wrapTextToWidth("ALPHA\nBETA", 500f, 14f, measure))
    }

    @Test
    fun blankLinesAreKept() {
        assertEquals("A\n\nB", PlaceMarkupLogic.wrapTextToWidth("A\n\nB", 500f, 14f, measure))
    }

    @Test
    fun eachLineStillWrapsToWidth() {
        // 'AAAA BBBB' = 90 > 60 → переноситься; другий рядок користувача лишається окремим
        assertEquals("AAAA\nBBBB\nCC", PlaceMarkupLogic.wrapTextToWidth("AAAA BBBB\nCC", 60f, 14f, measure))
    }

    @Test
    fun legacyWrappedTextIsUnwrappedExplicitlyByCaller() {
        assertEquals("AAAA BBBB", PlaceMarkupLogic.unwrapText("AAAA\nBBBB"))
    }
}
