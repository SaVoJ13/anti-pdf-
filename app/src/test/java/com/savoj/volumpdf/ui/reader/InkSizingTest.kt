package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkSizingTest {
    @Test
    fun presetsAreInPointsWithHighlighterAlpha() {
        assertEquals(2f, InkPreset.PEN.widthPt, 0f)
        assertEquals(5f, InkPreset.MARKER.widthPt, 0f)
        assertEquals(12f, InkPreset.HIGHLIGHTER.widthPt, 0f)
        assertEquals(0xFF, InkPreset.PEN.alpha)
        assertEquals(0x66, InkPreset.HIGHLIGHTER.alpha)
    }

    @Test
    fun inkWidthStepsGrowWithWidthAndCoverTheWholeRange() {
        var w = PlaceMarkupLogic.MIN_INK_WIDTH_PT
        var steps = 0
        while (w < PlaceMarkupLogic.MAX_INK_WIDTH_PT && steps < 200) {
            val n = PlaceMarkupLogic.stepSize(PlaceTool.INK, w, +1)
            assertTrue(n > w)
            w = n
            steps++
        }
        assertEquals(PlaceMarkupLogic.MAX_INK_WIDTH_PT, w, 0f)
        assertTrue(steps < 40)
        // «−» обернений до «+»
        assertEquals(4f, PlaceMarkupLogic.stepSize(PlaceTool.INK, 5f, -1), 0f)
        assertEquals(3.5f, PlaceMarkupLogic.stepSize(PlaceTool.INK, 4f, -1), 0f)
    }

    @Test
    fun formatSizeShowsHalvesOnlyWhenNeeded() {
        assertEquals("12", PlaceMarkupLogic.formatSize(12f))
        assertTrue(PlaceMarkupLogic.formatSize(2.5f).startsWith("2"))
        assertTrue(PlaceMarkupLogic.formatSize(2.5f).length == 3)
    }

    @Test
    fun inkAndSignatureMapToPageMarkup() {
        val ink = PendingMarkup("i", PlaceTool.INK, 4, 1f, 2f, "", 5f, 0x66FF0000, ink = InkPoints(floatArrayOf(1f, 2f, 3f, 4f)))
        val m = PlaceMarkupLogic.toPageMarkup(ink) as com.savoj.volumpdf.core.pdf.model.PageMarkup.Ink
        assertEquals(4, m.pageIndex)
        assertEquals(1, m.strokes.size)
        assertEquals(2, m.strokes[0].points.size)
        assertEquals(5f, m.strokes[0].strokeWidth, 0f)
        assertEquals(0x66FF0000, m.strokes[0].color)
    }

    @Test(expected = IllegalStateException::class)
    fun signatureWithoutBitmapCannotBeConverted() {
        PlaceMarkupLogic.toPageMarkup(PendingMarkup("g", PlaceTool.SIGNATURE, 0, 0f, 0f, "", 120f, 0, aspect = 0.5f))
    }
}
