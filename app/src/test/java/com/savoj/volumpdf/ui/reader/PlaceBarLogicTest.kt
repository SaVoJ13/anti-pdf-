package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceBarLogicTest {

    private val last = PlaceTool.CHECK

    // --- слоти й повторний тап --------------------------------------------------------------------

    @Test
    fun `every tool belongs to exactly one slot and slot round trips for non-action tools`() {
        PlaceTool.values().forEach { tool ->
            val slot = PlaceBarLogic.slotOf(tool)
            assertTrue(PlaceBarLogic.isActive(slot, tool))
            BarSlot.values().filter { it != slot }.forEach { assertFalse(PlaceBarLogic.isActive(it, tool)) }
        }
        assertEquals(PlaceTool.TEXT, PlaceBarLogic.toolFor(BarSlot.TEXT, last))
        assertEquals(PlaceTool.INK, PlaceBarLogic.toolFor(BarSlot.PEN, last))
        assertEquals(PlaceTool.HAND, PlaceBarLogic.toolFor(BarSlot.SELECT, last))
    }

    @Test
    fun `tap on inactive slot activates it and keeps card closed even if it was open`() {
        val r = PlaceBarLogic.onSlotTap(PlaceTool.TEXT, cardOpen = true, tapped = BarSlot.PEN, lastActionTool = last, hasOptions = true)
        assertEquals(PlaceTool.INK, r.tool)
        assertFalse(r.cardOpen)
    }

    @Test
    fun `repeat tap on active slot toggles the card`() {
        var open = false
        repeat(3) {
            val r = PlaceBarLogic.onSlotTap(PlaceTool.INK, open, BarSlot.PEN, last, hasOptions = true)
            assertEquals(PlaceTool.INK, r.tool)
            assertEquals(!open, r.cardOpen)
            open = r.cardOpen
        }
        assertTrue(open)
    }

    @Test
    fun `repeat tap without options never opens a card`() {
        val r = PlaceBarLogic.onSlotTap(PlaceTool.HAND, false, BarSlot.SELECT, last, hasOptions = false)
        assertFalse(r.cardOpen)
        assertFalse(PlaceBarLogic.onOptionsTap(cardOpen = true, hasOptions = false))
    }

    @Test
    fun `action slot restores the last action tool and ignores junk`() {
        assertEquals(PlaceTool.SIGNATURE, PlaceBarLogic.toolFor(BarSlot.ACTION, PlaceTool.SIGNATURE))
        assertEquals(PlaceTool.CHECK, PlaceBarLogic.toolFor(BarSlot.ACTION, PlaceTool.INK))
        assertEquals(PlaceTool.CROSS, PlaceBarLogic.rememberActionTool(PlaceTool.CROSS, PlaceTool.CHECK))
        assertEquals(PlaceTool.IMAGE, PlaceBarLogic.rememberActionTool(PlaceTool.TEXT, PlaceTool.IMAGE))
        assertEquals(PlaceTool.CHECK, PlaceBarLogic.rememberActionTool(PlaceTool.TEXT, PlaceTool.TEXT))
        // тап по слоту дії з тексту вмикає останню дію
        val r = PlaceBarLogic.onSlotTap(PlaceTool.TEXT, false, BarSlot.ACTION, PlaceTool.CROSS, true)
        assertEquals(PlaceTool.CROSS, r.tool)
    }

    @Test
    fun `options button toggles when there is something to show`() {
        assertTrue(PlaceBarLogic.onOptionsTap(cardOpen = false, hasOptions = true))
        assertFalse(PlaceBarLogic.onOptionsTap(cardOpen = true, hasOptions = true))
    }

    // --- що показує картка ------------------------------------------------------------------------

    @Test
    fun `selection decides the target of the card`() {
        assertEquals(PlaceTool.TEXT, PlaceBarLogic.target(PlaceTool.HAND, PlaceTool.TEXT))
        assertEquals(PlaceTool.INK, PlaceBarLogic.target(PlaceTool.INK, null))
        assertFalse(PlaceBarLogic.hasOptions(PlaceTool.HAND, null))
        assertTrue(PlaceBarLogic.hasOptions(PlaceTool.HAND, PlaceTool.CHECK))
        assertTrue(PlaceBarLogic.hasOptions(PlaceTool.INK, null))
    }

    @Test
    fun `pen card has size colour presets and straighten, split into two pages`() {
        val sections = PlaceBarLogic.sections(PlaceTool.INK, hasSelection = false)
        assertEquals(
            listOf(CardSection.SIZE, CardSection.COLOR, CardSection.PRESETS, CardSection.STRAIGHTEN), sections
        )
        val pages = PlaceBarLogic.pages(sections)
        assertEquals(2, pages.size)
        assertEquals(listOf(CardSection.SIZE, CardSection.COLOR), pages[0])
        assertEquals(listOf(CardSection.PRESETS, CardSection.STRAIGHTEN), pages[1])
    }

    @Test
    fun `selected stroke has no straighten and other cards are single page`() {
        assertFalse(PlaceBarLogic.sections(PlaceTool.INK, true).contains(CardSection.STRAIGHTEN))
        assertEquals(1, PlaceBarLogic.pages(PlaceBarLogic.sections(PlaceTool.TEXT, false)).size)
        assertEquals(1, PlaceBarLogic.pages(PlaceBarLogic.sections(PlaceTool.CHECK, false)).size)
        assertEquals(1, PlaceBarLogic.pages(emptyList()).size)
    }

    @Test
    fun `action chooser only without selection, image controls only for selected image`() {
        assertTrue(PlaceBarLogic.sections(PlaceTool.SIGNATURE, false).contains(CardSection.ACTION_CHOOSER))
        assertFalse(PlaceBarLogic.sections(PlaceTool.SIGNATURE, true).contains(CardSection.ACTION_CHOOSER))
        assertTrue(PlaceBarLogic.sections(PlaceTool.IMAGE, true).contains(CardSection.IMAGE_CONTROLS))
        assertFalse(PlaceBarLogic.sections(PlaceTool.IMAGE, false).contains(CardSection.IMAGE_CONTROLS))
        assertTrue(PlaceBarLogic.sections(PlaceTool.CHECK, false).contains(CardSection.ACTION_CHOOSER))
        assertFalse(PlaceBarLogic.sections(PlaceTool.CHECK, true).contains(CardSection.ACTION_CHOOSER))
    }

    @Test
    fun `card closes when mode ends or nothing left to configure`() {
        assertTrue(PlaceBarLogic.cardStillValid(true, placeModeActive = true, hasOptions = true))
        assertFalse(PlaceBarLogic.cardStillValid(true, placeModeActive = false, hasOptions = true))
        assertFalse(PlaceBarLogic.cardStillValid(true, placeModeActive = true, hasOptions = false))
        assertFalse(PlaceBarLogic.cardStillValid(false, true, true))
    }

    @Test
    fun `palette has nine distinct opaque colours and detects custom colours`() {
        assertEquals(9, PlaceBarLogic.PALETTE.size)
        assertEquals(9, PlaceBarLogic.PALETTE.toSet().size)
        PlaceBarLogic.PALETTE.forEach { assertEquals(0xFF, it ushr 24) }
        assertFalse(PlaceBarLogic.isCustomColor(0xFFD32F2F.toInt()))
        assertFalse(PlaceBarLogic.isCustomColor(0x66D32F2F)) // альфа маркера не робить колір «власним»
        assertTrue(PlaceBarLogic.isCustomColor(0xFF123456.toInt()))
    }

    // --- випрямлення ліній ------------------------------------------------------------------------

    @Test
    fun `nearly straight stroke becomes a two point segment`() {
        val xy = floatArrayOf(0f, 0f, 25f, 1f, 50f, -1f, 75f, 0.5f, 100f, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 100f, 0f), InkStraighten.straighten(xy), 0f)
    }

    @Test
    fun `curved stroke is left untouched`() {
        val xy = floatArrayOf(0f, 0f, 25f, 20f, 50f, 30f, 75f, 20f, 100f, 0f)
        assertSame(xy, InkStraighten.straighten(xy))
    }

    @Test
    fun `dots, short strokes and closed loops are left untouched`() {
        val dot = floatArrayOf(5f, 5f, 5.2f, 5f, 5f, 5.1f)
        assertSame(dot, InkStraighten.straighten(dot))
        val two = floatArrayOf(0f, 0f, 50f, 0f)
        assertSame(two, InkStraighten.straighten(two))
        val loop = floatArrayOf(0f, 0f, 40f, 0f, 40f, 40f, 0f, 40f, 0f, 0f)
        assertSame(loop, InkStraighten.straighten(loop))
    }

    @Test
    fun `diagonal straight stroke keeps its end points`() {
        val xy = floatArrayOf(10f, 10f, 40f, 40.5f, 70f, 69.5f, 110f, 110f)
        assertArrayEquals(floatArrayOf(10f, 10f, 110f, 110f), InkStraighten.straighten(xy), 0f)
    }

    @Test
    fun `straightened stroke is still accepted as a normal ink object`() {
        val xy = floatArrayOf(50f, 50f, 100f, 51f, 150f, 49f, 200f, 50f)
        val stroke = PlaceMarkupLogic.inkFromStroke(
            "s", 0, InkStraighten.straighten(xy), 2f, 0xFF000000.toInt(), PageSizePt(600f, 800f)
        )
        org.junit.Assert.assertNotNull(stroke)
        assertEquals(PlaceTool.INK, stroke!!.tool)
        assertEquals(4, stroke.ink!!.xy.size)
    }

    // --- повтор (redo) ----------------------------------------------------------------------------

    private fun check(id: String) = PendingMarkup(id, PlaceTool.CHECK, 0, 10f, 10f, "", 16f, 0xFFD32F2F.toInt())

    @Test
    fun `undo then redo restores the list and a new action clears redo`() {
        var s = PlaceEditor.add(PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a")), check("b"))
        assertFalse(s.canRedo)
        s = PlaceEditor.undo(s)
        assertEquals(listOf("a"), s.items.map { it.id })
        assertTrue(s.canRedo)
        s = PlaceEditor.redo(s)
        assertEquals(listOf("a", "b"), s.items.map { it.id })
        assertFalse(s.canRedo)
        assertTrue(s.canUndo)

        s = PlaceEditor.undo(s)
        s = PlaceEditor.add(s, check("c"))
        assertFalse(s.canRedo) // нова дія скасовує «повторити»
        assertEquals(listOf("a", "c"), s.items.map { it.id })
    }

    @Test
    fun `redo on empty stack is a no-op and undo-redo is symmetric over many steps`() {
        val s0 = PlaceEditor.initial(emptyList())
        assertSame(s0, PlaceEditor.redo(s0))
        var s = s0
        listOf("a", "b", "c").forEach { s = PlaceEditor.add(s, check(it)) }
        repeat(3) { s = PlaceEditor.undo(s) }
        assertTrue(s.items.isEmpty())
        repeat(3) { s = PlaceEditor.redo(s) }
        assertEquals(listOf("a", "b", "c"), s.items.map { it.id })
        assertFalse(s.canRedo)
    }

    @Test
    fun `redo drops a selection that is not in the restored list`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a"))
        s = PlaceEditor.undo(s)
        s = PlaceEditor.redo(s)
        s = PlaceEditor.select(s, "a")
        s = PlaceEditor.undo(s) // «a» зникає
        assertEquals(null, s.selectedId)
    }
}
