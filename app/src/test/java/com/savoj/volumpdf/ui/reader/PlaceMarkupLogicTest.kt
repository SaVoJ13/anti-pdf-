package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.MarkupLayout
import com.savoj.volumpdf.core.pdf.model.MarkKind
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMarkupLogicTest {

    private val red = 0xFFD32F2F.toInt()

    // --- створення з тапу ------------------------------------------------------------------------

    @Test
    fun `mark from tap - center is the tap point, default size 16`() {
        val m = PlaceMarkupLogic.markFromTap("a", PlaceTool.CHECK, 7, 100.5f, 200.25f, 16f, red)
        assertEquals(PlaceTool.CHECK, m.tool)
        assertEquals(7, m.pageIndex)
        assertEquals(100.5f, m.x, 0f)
        assertEquals(200.25f, m.y, 0f)
        val pm = PlaceMarkupLogic.toPageMarkup(m) as PageMarkup.Mark
        assertEquals(MarkKind.CHECK, pm.kind)
        assertEquals(16f, pm.sizePt, 0f)
        assertEquals(100.5f, pm.x, 0f)
    }

    @Test
    fun `cross maps to Mark CROSS`() {
        val m = PlaceMarkupLogic.markFromTap("a", PlaceTool.CROSS, 0, 1f, 2f, 16f, red)
        assertEquals(MarkKind.CROSS, (PlaceMarkupLogic.toPageMarkup(m) as PageMarkup.Mark).kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `check and cross are never created through the text path`() {
        PlaceMarkupLogic.markFromTap("a", PlaceTool.TEXT, 0, 0f, 0f, 14f, red)
    }

    @Test
    fun `text from tap - top-left corner is the tap point, default size 14`() {
        val r = PlaceMarkupLogic.textFromTap("t", 3, 54f, 72f, "Привіт", 14f, red, unsupported = "")
        val m = (r as TextCheck.Ok).markup
        val pm = PlaceMarkupLogic.toPageMarkup(m) as PageMarkup.Text
        assertEquals(3, pm.pageIndex)
        assertEquals(54f, pm.x, 0f)
        assertEquals(72f, pm.y, 0f)
        assertEquals("Привіт", pm.text)
        assertEquals(14f, pm.fontSizePt, 0f)
    }

    @Test
    fun `text with unsupported chars is rejected and reports them`() {
        val r = PlaceMarkupLogic.textFromTap("t", 0, 0f, 0f, "a✓b", 14f, red, unsupported = "✓")
        assertEquals("✓", (r as TextCheck.Unsupported).chars)
    }

    @Test
    fun `empty or blank text is not added`() {
        assertTrue(PlaceMarkupLogic.textFromTap("t", 0, 0f, 0f, "", 14f, red, "") is TextCheck.Empty)
        assertTrue(PlaceMarkupLogic.textFromTap("t", 0, 0f, 0f, "  \n \n", 14f, red, "") is TextCheck.Empty)
    }

    // --- клампи ----------------------------------------------------------------------------------

    @Test
    fun `text size is clamped to at least 4 pt`() {
        assertEquals(4f, PlaceMarkupLogic.clampSize(PlaceTool.TEXT, 0f), 0f)
        assertEquals(4f, PlaceMarkupLogic.clampSize(PlaceTool.TEXT, -3f), 0f)
        assertEquals(4f, PlaceMarkupLogic.clampSize(PlaceTool.TEXT, 3.9f), 0f)
        assertEquals(144f, PlaceMarkupLogic.clampSize(PlaceTool.TEXT, 500f), 0f)
        val r = PlaceMarkupLogic.textFromTap("t", 0, 0f, 0f, "x", 1f, red, "") as TextCheck.Ok
        assertEquals(4f, r.markup.sizePt, 0f)
    }

    @Test
    fun `mark size is clamped and NaN falls back to default`() {
        assertEquals(4f, PlaceMarkupLogic.clampSize(PlaceTool.CHECK, 1f), 0f)
        assertEquals(144f, PlaceMarkupLogic.clampSize(PlaceTool.CROSS, 1000f), 0f)
        assertEquals(16f, PlaceMarkupLogic.clampSize(PlaceTool.CHECK, Float.NaN), 0f)
        assertEquals(14f, PlaceMarkupLogic.clampSize(PlaceTool.TEXT, Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun `size stepping stays in range`() {
        assertEquals(17f, PlaceMarkupLogic.stepSize(PlaceTool.CHECK, 16f, +1), 0f)
        assertEquals(4f, PlaceMarkupLogic.stepSize(PlaceTool.TEXT, 5f, -1), 0f)
        assertEquals(4f, PlaceMarkupLogic.stepSize(PlaceTool.TEXT, 4f, -1), 0f)
        assertEquals(76f, PlaceMarkupLogic.stepSize(PlaceTool.TEXT, 72f, +1), 0f)
    }

    // --- рішення про доступність -----------------------------------------------------------------

    @Test
    fun `gate - encrypted, no rights, unreadable, allowed`() {
        assertEquals(PlaceMarkupLogic.Gate.ALLOWED, PlaceMarkupLogic.gate(false, true))
        assertEquals(PlaceMarkupLogic.Gate.ENCRYPTED, PlaceMarkupLogic.gate(true, true))
        assertEquals(PlaceMarkupLogic.Gate.NO_MARKUP_RIGHTS, PlaceMarkupLogic.gate(false, false))
        assertEquals(PlaceMarkupLogic.Gate.UNREADABLE, PlaceMarkupLogic.gate(null, null))
    }

    // --- серіалізація ----------------------------------------------------------------------------

    @Test
    fun `serialization round trip keeps every field, including awkward text`() {
        val tricky = "рядок 1\nрядок\t2 \\ кінець\r\n✓?"
        val items = listOf(
            PendingMarkup("id-1", PlaceTool.TEXT, 119, 12.345f, -3.5f, tricky, 14f, red),
            PendingMarkup("id-2", PlaceTool.CROSS, 0, 0f, 0f, "", 96f, 0xFF000000.toInt())
        )
        val back = PlaceMarkupLogic.deserializeAll(PlaceMarkupLogic.serializeAll(items))
        assertEquals(items, back)
    }

    @Test
    fun `corrupted records are skipped`() {
        val good = PlaceMarkupLogic.serialize(PendingMarkup("a", PlaceTool.CHECK, 1, 1f, 2f, "", 16f, red))
        val back = PlaceMarkupLogic.deserializeAll(listOf("garbage", good, "a\tNOPE\t1\t1\t1\t\t1\t1"))
        assertEquals(1, back.size)
        assertNull(PlaceMarkupLogic.deserialize(""))
        assertTrue(PlaceMarkupLogic.deserializeAll(null).isEmpty())
    }

    // --- pt → px ---------------------------------------------------------------------------------

    @Test
    fun `one px equals one pt at zoom 1 and scales linearly`() {
        assertEquals(16f, MarkupOverlayMath.ptToPx(16f, 1f), 0f)
        assertEquals(40f, MarkupOverlayMath.ptToPx(16f, 2.5f), 1e-4f)
        assertEquals(0.12f * 16f, MarkupOverlayMath.strokePx(16f, 1f), 1e-5f)
        assertEquals(0.12f * 16f * 3f, MarkupOverlayMath.strokePx(16f, 3f), 1e-5f)
    }

    @Test
    fun `check polyline is placed around the center by MarkupLayout`() {
        val lines = MarkupOverlayMath.markPolylinesPx(isCheck = true, cx = 100f, cy = 100f, sizePt = 20f, zoom = 1f)
        assertEquals(1, lines.size)
        // квадрат 20×20 з лівим верхнім кутом (90, 90); перша точка (0.15, 0.55)
        assertEquals(90f + 0.15f * 20f, lines[0][0], 1e-4f)
        assertEquals(90f + 0.55f * 20f, lines[0][1], 1e-4f)
        assertEquals(6, lines[0].size)
        // zoom 2: сторона 40 px, той самий центр
        val z2 = MarkupOverlayMath.markPolylinesPx(true, 100f, 100f, 20f, 2f)
        assertEquals(80f + 0.15f * 40f, z2[0][0], 1e-4f)
    }

    @Test
    fun `cross has two segments`() {
        val lines = MarkupOverlayMath.markPolylinesPx(false, 0f, 0f, 10f, 1f)
        assertEquals(2, lines.size)
        assertEquals(-5f + 0.2f * 10f, lines[0][0], 1e-4f)
    }

    @Test
    fun `text baseline follows MarkupLayout ratios and breaks only on newline`() {
        val lines = MarkupOverlayMath.textLinesPx("ab\ncd\r\n\nef", x0 = 10f, y0 = 20f, fontSizePt = 10f, zoom = 2f)
        assertEquals(4, lines.size)
        val fs = 20f
        assertEquals(20f + MarkupLayout.TEXT_ASCENT_RATIO * fs, lines[0].baselineY, 1e-4f)
        assertEquals(20f + MarkupLayout.TEXT_ASCENT_RATIO * fs + MarkupLayout.TEXT_LINE_HEIGHT_RATIO * fs, lines[1].baselineY, 1e-4f)
        assertEquals("", lines[2].text)
        assertEquals("ef", lines[3].text)
        assertEquals(10f, lines[3].x, 0f)
    }

    @Test
    fun `long line is not wrapped`() {
        val lines = MarkupOverlayMath.textLinesPx("a".repeat(500), 0f, 0f, 14f, 1f)
        assertEquals(1, lines.size)
    }
}
