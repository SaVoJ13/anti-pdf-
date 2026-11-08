package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkSignatureEditorTest {
    private val page = PageSizePt(200f, 300f)

    private fun stroke(id: String = "s1", vararg xy: Float, color: Int = 0xFFD32F2F.toInt(), width: Float = 2f) =
        PendingMarkup(id, PlaceTool.INK, 0, xy[0], xy[1], "", width, color, ink = InkPoints(xy))

    private fun signature(id: String = "g1", x: Float = 20f, y: Float = 30f, width: Float = 120f) =
        PendingMarkup(id, PlaceTool.SIGNATURE, 0, x, y, "", width, 0, aspect = 0.4f)

    // --- відсікання точок -------------------------------------------------------------------

    @Test
    fun decimateDropsClosePointsAndKeepsFirstAndLast() {
        val xy = floatArrayOf(0f, 0f, 0.1f, 0f, 0.2f, 0f, 1f, 0f, 1.1f, 0f)
        val out = InkMath.decimate(xy, 0.5f)
        assertEquals(listOf(0f, 0f, 1f, 0f, 1.1f, 0f), out.toList())
    }

    @Test
    fun decimateKeepsSinglePointAndSkipsGarbage() {
        assertEquals(listOf(5f, 6f), InkMath.decimate(floatArrayOf(5f, 6f), 0.5f).toList())
        assertEquals(0, InkMath.decimate(floatArrayOf(), 0.5f).size)
        assertEquals(listOf(1f, 1f), InkMath.decimate(floatArrayOf(Float.NaN, 0f, 1f, 1f, 9f), 0.5f).toList())
    }

    @Test
    fun strokeFullyOutsideIsNotAdded() {
        assertNull(PlaceMarkupLogic.inkFromStroke("a", 0, floatArrayOf(-50f, -50f, -10f, -20f), 2f, 0, page))
        assertNull(PlaceMarkupLogic.inkFromStroke("a", 0, floatArrayOf(250f, 10f, 300f, 20f), 2f, 0, page))
    }

    @Test
    fun strokePartlyOutsideIsKeptWithAnchorAtFirstPoint() {
        val s = PlaceMarkupLogic.inkFromStroke("a", 3, floatArrayOf(-20f, 50f, 10f, 60f, 40f, 70f), 5f, 0x66112233, page)!!
        assertEquals(3, s.pageIndex)
        assertEquals(-20f, s.x, 0f)
        assertEquals(50f, s.y, 0f)
        assertEquals(3, s.ink!!.count)
        assertEquals(5f, s.sizePt, 0f)
        assertEquals(0x66112233, s.color)
    }

    @Test
    fun strokeWithUnknownPageSizeIsAccepted() {
        assertNotNull(PlaceMarkupLogic.inkFromStroke("a", 0, floatArrayOf(-50f, -50f, -10f, -20f), 2f, 0, null))
    }

    // --- ламана й hit-test ------------------------------------------------------------------

    @Test
    fun polylineDistance() {
        val line = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f)
        assertEquals(10f, PolylineMath.distance(line, 50f, 10f), 1e-4f)
        assertEquals(0f, PolylineMath.distance(line, 100f, 40f), 1e-4f)
        assertEquals(5f, PolylineMath.distance(line, 105f, 40f), 1e-4f)
        assertEquals(Math.hypot(30.0, 40.0).toFloat(), PolylineMath.distance(line, -30f, -40f), 1e-3f)
        assertEquals(5f, PolylineMath.distance(floatArrayOf(10f, 10f), 13f, 14f), 1e-4f)
    }

    @Test
    fun hitZoneFollowsThePolylineNotTheBoundingBox() {
        val diagonal = floatArrayOf(0f, 0f, 400f, 400f)
        val t = HitTarget("d", RectPx(0f, 0f, 400f, 400f), diagonal, 2f)
        val slop = 24f * 3f // 24 dp при 3 px/dp
        assertEquals("d", PlaceHitTest.hit(listOf(t), 200f, 200f, slop))
        assertEquals("d", PlaceHitTest.hit(listOf(t), 200f, 200f + 60f, slop)) // ≈42 px від лінії < 72 − 2
        assertNull(PlaceHitTest.hit(listOf(t), 390f, 10f, slop)) // кут рамки, далеко від лінії
        assertNull(PlaceHitTest.hit(listOf(t), 200f, 200f + 140f, slop))
    }

    @Test
    fun hitZoneIsAtLeast24dp() {
        val line = floatArrayOf(100f, 100f, 300f, 100f)
        val t = HitTarget("l", RectPx(100f, 99f, 300f, 101f), line, 1f)
        val dp = 2.5f
        assertEquals("l", PlaceHitTest.hit(listOf(t), 200f, 100f + 24f * dp, 24f * dp + 1f))
        assertNull(PlaceHitTest.hit(listOf(t), 200f, 100f + 24f * dp + 5f, 24f * dp))
    }

    // --- рух, клампи ------------------------------------------------------------------------

    @Test
    fun moveTranslatesTheWholeStroke() {
        val s0 = PlaceEditor.add(PlaceEditorState(), stroke("s", 10f, 10f, 20f, 15f, 30f, 12f))
        val s1 = PlaceEditor.move(s0, "s", 60f, 110f, page)
        val moved = s1.items.single()
        assertEquals(60f, moved.x, 1e-4f)
        assertEquals(110f, moved.y, 1e-4f)
        assertEquals(listOf(60f, 110f, 70f, 115f, 80f, 112f), moved.ink!!.xy.toList().map { Math.round(it * 1000f) / 1000f })
        assertTrue(s1.canUndo)
        assertEquals(listOf(10f, 10f, 20f, 15f, 30f, 12f), PlaceEditor.undo(s1).items.single().ink!!.xy.toList())
    }

    @Test
    fun strokeCannotLeaveThePageCompletely() {
        val s = stroke("s", 10f, 10f, 40f, 30f)
        val far = PlaceMarkupLogic.clampedAnchor(s, 5000f, 5000f, page)
        val moved = PlaceMarkupLogic.withAnchor(s, far.first, far.second)
        val b = PlaceMarkupLogic.inkBoundsPt(moved)
        assertTrue(b.left <= page.widthPt - PlaceMarkupLogic.INK_MIN_VISIBLE_PT + 1e-3f)
        assertTrue(b.top <= page.heightPt - PlaceMarkupLogic.INK_MIN_VISIBLE_PT + 1e-3f)
        val near = PlaceMarkupLogic.clampedAnchor(s, -5000f, -5000f, page)
        val moved2 = PlaceMarkupLogic.withAnchor(s, near.first, near.second)
        val b2 = PlaceMarkupLogic.inkBoundsPt(moved2)
        assertTrue(b2.right >= PlaceMarkupLogic.INK_MIN_VISIBLE_PT - 1e-3f)
        assertTrue(b2.bottom >= PlaceMarkupLogic.INK_MIN_VISIBLE_PT - 1e-3f)
        // розмір штриха не змінився
        assertEquals(30f, b2.right - b2.left, 1e-3f)
    }

    @Test
    fun strokeMoveWithinPageIsNotClamped() {
        val s = stroke("s", 10f, 10f, 40f, 30f)
        val c = PlaceMarkupLogic.clampedAnchor(s, 50f, 60f, page)
        assertEquals(50f, c.first, 1e-4f)
        assertEquals(60f, c.second, 1e-4f)
    }

    @Test
    fun signatureAnchorIsClampedToThePage() {
        val s0 = PlaceEditor.add(PlaceEditorState(), signature())
        val s1 = PlaceEditor.move(s0, "g1", 9999f, -50f, page)
        val g = s1.items.single()
        assertEquals(page.widthPt - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, g.x, 1e-4f)
        assertEquals(0f, g.y, 1e-4f)
    }

    // --- колір, товщина, розмір, видалення, undo -----------------------------------------------

    @Test
    fun recolorKeepsHighlighterAlpha() {
        val s0 = PlaceEditor.add(PlaceEditorState(), stroke("s", 1f, 1f, 5f, 5f, color = 0x66D32F2F))
        val s1 = PlaceEditor.setColor(s0, "s", 0xFF1976D2.toInt())
        assertEquals(0x661976D2, s1.items.single().color)
        val g = PlaceEditor.add(PlaceEditorState(), stroke("o", 1f, 1f, 5f, 5f, color = 0xFFD32F2F.toInt()))
        assertEquals(0xFF388E3C.toInt(), PlaceEditor.setColor(g, "o", 0xFF388E3C.toInt()).items.single().color)
    }

    @Test
    fun inkWidthIsClampedAndCoalescedInOneUndoStep() {
        var s = PlaceEditor.add(PlaceEditorState(), stroke("s", 1f, 1f, 5f, 5f))
        s = PlaceEditor.select(s, "s")
        val before = s.history.size
        repeat(10) { s = PlaceEditor.setSize(s, "s", PlaceMarkupLogic.stepSize(PlaceTool.INK, s.items.single().sizePt, +1)) }
        assertEquals(before + 1, s.history.size)
        assertTrue(s.items.single().sizePt > 2f)
        s = PlaceEditor.setSize(s, "s", 999f)
        assertEquals(PlaceMarkupLogic.MAX_INK_WIDTH_PT, s.items.single().sizePt, 0f)
        s = PlaceEditor.setSize(s, "s", 0f)
        assertEquals(PlaceMarkupLogic.MIN_INK_WIDTH_PT, s.items.single().sizePt, 0f)
        assertEquals(PlaceMarkupLogic.MIN_INK_WIDTH_PT, PlaceMarkupLogic.stepSize(PlaceTool.INK, 0.5f, -1), 0f)
    }

    @Test
    fun inkPresetSetsWidthAndAlpha() {
        val s0 = PlaceEditor.add(PlaceEditorState(), stroke("s", 1f, 1f, 5f, 5f, color = 0xFF112233.toInt()))
        val s1 = PlaceEditor.setInkStyle(s0, "s", InkPreset.HIGHLIGHTER.widthPt, InkPreset.HIGHLIGHTER.alpha)
        assertEquals(12f, s1.items.single().sizePt, 0f)
        assertEquals(0x66112233, s1.items.single().color)
        assertEquals(s0.items, PlaceEditor.undo(s1).items)
    }

    @Test
    fun signatureWidthRangeAndAspect() {
        var s = PlaceEditor.add(PlaceEditorState(), signature())
        s = PlaceEditor.setSize(s, "g1", 10f)
        assertEquals(30f, s.items.single().sizePt, 0f)
        s = PlaceEditor.setSize(s, "g1", 1000f)
        assertEquals(400f, s.items.single().sizePt, 0f)
        s = PlaceEditor.setSize(s, "g1", 250f)
        assertEquals(250f, s.items.single().sizePt, 0f)
        assertEquals(0.4f, s.items.single().aspect, 0f) // пропорції не змінюються
    }

    @Test
    fun signatureFromTapUses120ptAndKeepsAspect() {
        val g = PlaceMarkupLogic.signatureFromTap("g", 2, 30f, 40f, 500, 200, PlaceMarkupLogic.DEFAULT_SIGNATURE_WIDTH_PT, page)!!
        assertEquals(120f, g.sizePt, 0f)
        assertEquals(0.4f, g.aspect, 1e-6f)
        assertEquals(30f, g.x, 0f)
        assertEquals(40f, g.y, 0f)
        assertNull(PlaceMarkupLogic.signatureFromTap("g", 2, 30f, 40f, 0, 200, 120f, page))
    }

    @Test
    fun deleteAndUndoForInkAndSignature() {
        var s = PlaceEditor.add(PlaceEditorState(), stroke("s", 1f, 1f, 5f, 5f))
        s = PlaceEditor.add(s, signature())
        s = PlaceEditor.select(s, "g1")
        s = PlaceEditor.delete(s, "g1")
        assertEquals(listOf("s"), s.items.map { it.id })
        assertNull(s.selectedId)
        s = PlaceEditor.undo(s)
        assertEquals(listOf("s", "g1"), s.items.map { it.id })
        s = PlaceEditor.delete(s, "s")
        assertFalse(s.items.any { it.id == "s" })
        s = PlaceEditor.undo(PlaceEditor.undo(s))
        assertEquals(listOf("s"), s.items.map { it.id })
    }
}
