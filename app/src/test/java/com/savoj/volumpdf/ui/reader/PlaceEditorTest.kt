package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceEditorTest {

    private val red = 0xFFD32F2F.toInt()
    private val blue = 0xFF1976D2.toInt()
    private val page = PageSizePt(600f, 800f)

    private fun check(id: String, x: Float = 100f, y: Float = 100f) =
        PendingMarkup(id, PlaceTool.CHECK, 0, x, y, "", 16f, red)

    private fun text(id: String, s: String = "Hello", x: Float = 50f, y: Float = 60f) =
        PendingMarkup(id, PlaceTool.TEXT, 2, x, y, s, 14f, red)

    // --- додавання, виділення ---------------------------------------------------------------------

    @Test
    fun `add appends item, creates one undo step per add, keeps selection`() {
        var s = PlaceEditor.initial(emptyList())
        s = PlaceEditor.add(s, check("a"))
        s = PlaceEditor.add(s, text("b"))
        assertEquals(listOf("a", "b"), s.items.map { it.id })
        assertEquals(2, s.history.size)
        assertNull(s.selectedId)
    }

    @Test
    fun `select unknown id clears selection and selecting does not touch history`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a"))
        s = PlaceEditor.select(s, "a")
        assertEquals("a", s.selectedId)
        assertEquals(1, s.history.size)
        s = PlaceEditor.select(s, "zzz")
        assertNull(s.selectedId)
        s = PlaceEditor.select(s, null)
        assertNull(s.selectedId)
    }

    // --- рух і клампи -----------------------------------------------------------------------------

    @Test
    fun `move changes x y, keeps page and is one undo step`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), text("t"))
        s = PlaceEditor.move(s, "t", 200f, 300f, page)
        val t = s.items.single()
        assertEquals(200f, t.x, 0f)
        assertEquals(300f, t.y, 0f)
        assertEquals(2, t.pageIndex)
        assertEquals(2, s.history.size)
    }

    @Test
    fun `move clamps reference point inside the visible page`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a"))
        s = PlaceEditor.move(s, "a", -50f, -1f, page)
        assertEquals(0f, s.items.single().x, 0f)
        assertEquals(0f, s.items.single().y, 0f)
        s = PlaceEditor.move(s, "a", 9999f, 9999f, page)
        assertEquals(600f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, s.items.single().x, 0f)
        assertEquals(800f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, s.items.single().y, 0f)
    }

    @Test
    fun `clampToPage without known size only keeps coordinates non-negative and finite`() {
        assertEquals(Pair(0f, 5f), PlaceMarkupLogic.clampToPage(-3f, 5f, null))
        assertEquals(Pair(0f, 0f), PlaceMarkupLogic.clampToPage(Float.NaN, Float.NaN, null))
        assertEquals(Pair(1000f, 2000f), PlaceMarkupLogic.clampToPage(1000f, 2000f, null))
    }

    @Test
    fun `clampToPage on a tiny page never produces a negative bound`() {
        assertEquals(Pair(0f, 0f), PlaceMarkupLogic.clampToPage(10f, 10f, PageSizePt(1f, 1f)))
    }

    @Test
    fun `move to the same place is a no-op without history`() {
        val s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a", 10f, 20f))
        assertEquals(s, PlaceEditor.move(s, "a", 10f, 20f, page))
    }

    // --- зміна ------------------------------------------------------------------------------------

    @Test
    fun `setText only affects text items`() {
        var s = PlaceEditor.add(PlaceEditor.add(PlaceEditor.initial(emptyList()), text("t")), check("m"))
        s = PlaceEditor.setText(s, "t", "Привіт")
        assertEquals("Привіт", s.items.first { it.id == "t" }.text)
        assertEquals(s, PlaceEditor.setText(s, "m", "x"))
    }

    @Test
    fun `setText updates text and stores rawText`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), text("t"))
        s = PlaceEditor.setText(s, "t", "Рядок 1\nРядок 2", rawText = "Рядок 1 Рядок 2")
        val item = s.items.first { it.id == "t" }
        assertEquals("Рядок 1\nРядок 2", item.text)
        assertEquals("Рядок 1 Рядок 2", item.rawText)
    }

    @Test
    fun `toggle mark kind flips check and cross, ignores text`() {
        var s = PlaceEditor.add(PlaceEditor.add(PlaceEditor.initial(emptyList()), check("m")), text("t"))
        s = PlaceEditor.toggleMarkKind(s, "m")
        assertEquals(PlaceTool.CROSS, s.items.first { it.id == "m" }.tool)
        s = PlaceEditor.toggleMarkKind(s, "m")
        assertEquals(PlaceTool.CHECK, s.items.first { it.id == "m" }.tool)
        assertEquals(s, PlaceEditor.toggleMarkKind(s, "t"))
    }

    @Test
    fun `size changes are clamped to 4 and 144 pt`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), text("t"))
        s = PlaceEditor.setSize(s, "t", 1f)
        assertEquals(4f, s.items.single().sizePt, 0f)
        s = PlaceEditor.setSize(s, "t", 500f)
        assertEquals(144f, s.items.single().sizePt, 0f)
    }

    @Test
    fun `size stepping covers the whole range in both directions`() {
        var v = 4f
        var steps = 0
        while (v < 144f) { v = PlaceMarkupLogic.stepSize(PlaceTool.TEXT, v, +1); steps++ }
        assertEquals(144f, v, 0f)
        assertEquals(48, steps)
        while (v > 4f) { v = PlaceMarkupLogic.stepSize(PlaceTool.TEXT, v, -1); steps-- }
        assertEquals(0, steps) // «−» обернений до «+»
    }

    @Test
    fun `consecutive size taps on one object form one undo step`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), text("t"))
        repeat(10) { s = PlaceEditor.setSize(s, "t", s.items.single().sizePt + 1f) }
        assertEquals(24f, s.items.single().sizePt, 0f)
        assertEquals(2, s.history.size) // add + одна зміна розміру
        s = PlaceEditor.undo(s)
        assertEquals(14f, s.items.single().sizePt, 0f)
    }

    @Test
    fun `size then color then size are three undo steps`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a"))
        s = PlaceEditor.setSize(s, "a", 30f)
        s = PlaceEditor.setColor(s, "a", blue)
        s = PlaceEditor.setSize(s, "a", 40f)
        assertEquals(4, s.history.size)
    }

    // --- видалення ---------------------------------------------------------------------------------

    @Test
    fun `delete removes item, clears selection and can be undone`() {
        var s = PlaceEditor.add(PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a")), text("b"))
        s = PlaceEditor.select(s, "a")
        s = PlaceEditor.delete(s, "a")
        assertEquals(listOf("b"), s.items.map { it.id })
        assertNull(s.selectedId)
        s = PlaceEditor.undo(s)
        assertEquals(listOf("a", "b"), s.items.map { it.id })
    }

    @Test
    fun `delete of unknown id is a no-op`() {
        val s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a"))
        assertEquals(s, PlaceEditor.delete(s, "nope"))
    }

    // --- скасування --------------------------------------------------------------------------------

    @Test
    fun `undo walks back add move change delete`() {
        var s = PlaceEditor.initial(emptyList())
        s = PlaceEditor.add(s, text("t"))
        s = PlaceEditor.move(s, "t", 300f, 400f, page)
        s = PlaceEditor.setText(s, "t", "Changed")
        s = PlaceEditor.setColor(s, "t", blue)
        s = PlaceEditor.delete(s, "t")
        assertTrue(s.items.isEmpty())
        s = PlaceEditor.undo(s)
        assertEquals(blue, s.items.single().color)
        s = PlaceEditor.undo(s)
        assertEquals(red, s.items.single().color)
        assertEquals("Changed", s.items.single().text)
        s = PlaceEditor.undo(s)
        assertEquals("Hello", s.items.single().text)
        assertEquals(300f, s.items.single().x, 0f)
        s = PlaceEditor.undo(s)
        assertEquals(50f, s.items.single().x, 0f)
        s = PlaceEditor.undo(s)
        assertTrue(s.items.isEmpty())
        assertFalse(s.canUndo)
        assertEquals(s, PlaceEditor.undo(s))
    }

    @Test
    fun `history keeps at least 20 steps`() {
        var s = PlaceEditor.initial(emptyList())
        repeat(25) { s = PlaceEditor.add(s, check("i$it", it.toFloat(), 0f)) }
        repeat(25) { s = PlaceEditor.undo(s) }
        assertTrue(s.items.isEmpty())
    }

    @Test
    fun `history is bounded`() {
        var s = PlaceEditor.initial(emptyList())
        repeat(PlaceEditor.MAX_HISTORY + 30) { s = PlaceEditor.add(s, check("i$it")) }
        assertEquals(PlaceEditor.MAX_HISTORY, s.history.size)
    }

    @Test
    fun `undo drops selection of an object that disappears`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a"))
        s = PlaceEditor.select(s, "a")
        s = PlaceEditor.undo(s)
        assertNull(s.selectedId)
        assertTrue(s.items.isEmpty())
    }

    @Test
    fun `undo keeps selection when the object still exists`() {
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a"))
        s = PlaceEditor.select(s, "a")
        s = PlaceEditor.move(s, "a", 5f, 5f, page)
        s = PlaceEditor.undo(s)
        assertEquals("a", s.selectedId)
        assertEquals(100f, s.items.single().x, 0f)
    }

    // --- скидання після збереження -------------------------------------------------------------------

    @Test
    fun `reset after save clears items, selection and history`() {
        var s = PlaceEditor.add(PlaceEditor.add(PlaceEditor.initial(emptyList()), check("a")), text("b"))
        s = PlaceEditor.select(s, "a")
        s = PlaceEditor.reset()
        assertTrue(s.items.isEmpty())
        assertNull(s.selectedId)
        assertFalse(s.canUndo)
    }

    @Test
    fun `initial state from restored items has no selection and no history`() {
        val s = PlaceEditor.initial(listOf(check("a"), text("b")))
        assertEquals(2, s.items.size)
        assertNull(s.selectedId)
        assertFalse(s.canUndo)
    }

    // --- серіалізація після змін ----------------------------------------------------------------------

    @Test
    fun `serialization round trip after move, edit, toggle and delete`() {
        var s = PlaceEditor.initial(emptyList())
        s = PlaceEditor.add(s, text("t", "Рядок 1\nLine\t2 \\ end"))
        s = PlaceEditor.add(s, check("m", 12.5f, 99.25f))
        s = PlaceEditor.add(s, check("gone"))
        s = PlaceEditor.move(s, "t", 222.75f, 333.5f, page)
        s = PlaceEditor.setText(s, "t", "Привіт, світ\nДругий рядок")
        s = PlaceEditor.setSize(s, "t", 33f)
        s = PlaceEditor.setColor(s, "t", blue)
        s = PlaceEditor.toggleMarkKind(s, "m")
        s = PlaceEditor.delete(s, "gone")
        val restored = PendingCodec.decode(PendingCodec.encode("doc", s.items))!!.items
        assertEquals(s.items, restored)
        val initial = PlaceEditor.initial(restored)
        assertEquals(s.items, initial.items)
        assertNull(initial.selectedId)
        assertFalse(initial.canUndo)
    }

    // --- hit-testing ------------------------------------------------------------------------------------

    private fun target(id: String, l: Float, t: Float, r: Float, b: Float) = HitTarget(id, RectPx(l, t, r, b))

    @Test
    fun `hit inside bounds`() {
        assertEquals("a", PlaceHitTest.hit(listOf(target("a", 100f, 100f, 200f, 150f)), 150f, 120f, 0f))
    }

    @Test
    fun `hit within slop zone outside bounds, miss beyond it`() {
        val t = listOf(target("a", 100f, 100f, 200f, 150f))
        val slop = 24f * 3f // 24 dp при density 3
        assertEquals("a", PlaceHitTest.hit(t, 100f - slop, 100f, slop))
        assertEquals("a", PlaceHitTest.hit(t, 200f + slop, 150f + slop, slop))
        assertNull(PlaceHitTest.hit(t, 100f - slop - 1f, 125f, slop))
        assertNull(PlaceHitTest.hit(t, 150f, 150f + slop + 1f, slop))
    }

    @Test
    fun `hit on empty area returns null so the gesture goes to the viewer`() {
        assertNull(PlaceHitTest.hit(emptyList(), 10f, 10f, 72f))
        assertNull(PlaceHitTest.hit(listOf(target("a", 500f, 500f, 520f, 520f)), 10f, 10f, 72f))
    }

    @Test
    fun `hit prefers the nearest object, topmost on ties`() {
        val t = listOf(
            target("far", 100f, 100f, 140f, 140f),
            target("near", 150f, 100f, 190f, 140f)
        )
        assertEquals("near", PlaceHitTest.hit(t, 160f, 120f, 72f))
        val overlap = listOf(target("under", 0f, 0f, 100f, 100f), target("over", 0f, 0f, 100f, 100f))
        assertEquals("over", PlaceHitTest.hit(overlap, 50f, 50f, 0f))
    }

    @Test
    fun `tiny mark still gets a 24dp touch zone`() {
        val item = PendingMarkup("a", PlaceTool.CHECK, 0, 0f, 0f, "", 4f, red)
        val rect = PlaceBounds.boundsPx(item, 500f, 500f, 1f) { 0f }
        val slop = 24f * 2.75f // 66 px
        assertEquals("a", PlaceHitTest.hit(listOf(HitTarget("a", rect)), 500f + 60f, 500f, slop))
        assertNull(PlaceHitTest.hit(listOf(HitTarget("a", rect)), 500f + 70f, 500f, slop))
    }

    // --- межі об'єктів ----------------------------------------------------------------------------------

    @Test
    fun `text bounds use the supplied line width and line count`() {
        val item = text("t", "ab\nlonger line", 0f, 0f).copy(sizePt = 10f)
        val widths = mapOf("ab" to 20f, "longer line" to 70f)
        val r = PlaceBounds.boundsPx(item, 100f, 200f, 2f) { widths.getValue(it) }
        assertEquals(100f, r.left, 0f)
        assertEquals(200f, r.top, 0f)
        assertEquals(170f, r.right, 0f) // найдовший рядок
        // 2 рядки при кеглі 20 px: (0.93 + 0.22 + 1.2) × 20
        assertEquals(200f + (0.93f + PlaceBounds.TEXT_DESCENT_RATIO + 1.2f) * 20f, r.bottom, 0.001f)
    }

    @Test
    fun `mark bounds are centered on the reference point`() {
        val item = check("m").copy(sizePt = 20f)
        val r = PlaceBounds.boundsPx(item, 300f, 400f, 1.5f) { 0f }
        assertEquals(300f, (r.left + r.right) / 2f, 0.001f)
        assertEquals(400f, (r.top + r.bottom) / 2f, 0.001f)
        assertTrue(r.right - r.left >= 20f * 1.5f)
    }

    // --- перевірка тексту ------------------------------------------------------------------------------

    @Test
    fun `text verdict reflects empty, unsupported and ok`() {
        assertEquals(TextVerdict.Empty, PlaceMarkupLogic.checkText("", ""))
        assertEquals(TextVerdict.Empty, PlaceMarkupLogic.checkText("  \n ", ""))
        assertEquals(TextVerdict.Unsupported("😀"), PlaceMarkupLogic.checkText("Ab😀", "😀"))
        assertEquals(TextVerdict.Ok, PlaceMarkupLogic.checkText("Привіт", ""))
    }

    // --- інтерактивне розміщення зображення та перенесення між сторінками -------------------------------

    @Test
    fun `imageFromTap creates item with correct aspect ratio and clamped size`() {
        val item = PlaceMarkupLogic.imageFromTap("img1", 0, 100f, 150f, 800, 600, 200f, page)
        assertEquals("img1", item?.id)
        assertEquals(PlaceTool.IMAGE, item?.tool)
        assertEquals(0, item?.pageIndex)
        assertEquals(100f, item?.x ?: 0f, 0.001f)
        assertEquals(150f, item?.y ?: 0f, 0.001f)
        assertEquals(200f, item?.sizePt ?: 0f, 0.001f)
        assertEquals(0.75f, item?.aspect ?: 0f, 0.001f) // 600 / 800 = 0.75
    }

    @Test
    fun `image move is clamped strictly inside page boundaries`() {
        // page is 600x800. image is 200 wide x 150 high (aspect 0.75).
        val item = PendingMarkup("img1", PlaceTool.IMAGE, 0, 50f, 50f, "", 200f, 0, aspect = 0.75f)
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), item)

        // Спроба витягнути за правий та нижній край (x=500, y=750)
        s = PlaceEditor.move(s, "img1", 500f, 750f, page)
        val moved = s.items.single()
        // maxX = 600 - 200 = 400; maxY = 800 - 150 = 650
        assertEquals(400f, moved.x, 0.001f)
        assertEquals(650f, moved.y, 0.001f)

        // Спроба витягнути за лівий та верхній край (від'ємні координати)
        s = PlaceEditor.move(s, "img1", -50f, -30f, page)
        val movedNeg = s.items.single()
        assertEquals(0f, movedNeg.x, 0.001f)
        assertEquals(0f, movedNeg.y, 0.001f)
    }

    @Test
    fun `image moving to another page updates pageIndex and records undo history`() {
        val item = PendingMarkup("img1", PlaceTool.IMAGE, 0, 50f, 50f, "", 200f, 0, aspect = 0.75f)
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), item)
        s = PlaceEditor.move(s, "img1", targetPageIndex = 3, x = 120f, y = 180f, page = page)

        val updated = s.items.single()
        assertEquals(3, updated.pageIndex)
        assertEquals(120f, updated.x, 0.001f)
        assertEquals(180f, updated.y, 0.001f)
        assertEquals(2, s.history.size)

        // Undo повертає об'єкт на попередню сторінку
        s = PlaceEditor.undo(s)
        assertEquals(0, s.items.single().pageIndex)
        assertEquals(50f, s.items.single().x, 0.001f)
    }

    @Test
    fun `image setSize respects page bounds and cannot exceed page dimensions`() {
        // page is 600x800. Image is at (450, 100), aspect 1.0 (square).
        // Remaining width from x=450 is 600 - 450 = 150 pt.
        val item = PendingMarkup("img1", PlaceTool.IMAGE, 0, 450f, 100f, "", 100f, 0, aspect = 1.0f)
        var s = PlaceEditor.add(PlaceEditor.initial(emptyList()), item)

        // Спроба збільшити ширину до 300 pt при доступних 150 pt
        s = PlaceEditor.setSize(s, "img1", 300f, page)
        assertEquals(150f, s.items.single().sizePt, 0.001f)
    }

    @Test
    fun `image pending markup round trips through PendingCodec`() {
        val item = PendingMarkup("img1", PlaceTool.IMAGE, 1, 45f, 90f, "", 220f, 0, aspect = 1.33f)
        val encoded = PendingCodec.encode("docKey", listOf(item))
        val decoded = PendingCodec.decode(encoded)!!.items.single()

        assertEquals(item.id, decoded.id)
        assertEquals(item.tool, decoded.tool)
        assertEquals(item.pageIndex, decoded.pageIndex)
        assertEquals(item.x, decoded.x, 0.001f)
        assertEquals(item.y, decoded.y, 0.001f)
        assertEquals(item.sizePt, decoded.sizePt, 0.001f)
        assertEquals(item.aspect, decoded.aspect, 0.001f)
    }
}
