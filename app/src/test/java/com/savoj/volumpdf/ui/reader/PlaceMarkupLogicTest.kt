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
    fun `long line without newlines stays as one line in textLinesPx`() {
        val lines = MarkupOverlayMath.textLinesPx("a".repeat(500), 0f, 0f, 14f, 1f)
        assertEquals(1, lines.size)
    }

    @Test
    fun `maxTextLineWidthPt computes symmetrical margins in left half of page`() {
        val page = PageSizePt(595.28f, 841.89f)
        val w50 = PlaceMarkupLogic.maxTextLineWidthPt(50f, page)
        // 595.28 - 50 (left) - 50 (right) = 495.28
        assertEquals(495.28f, w50, 0.01f)

        val w72 = PlaceMarkupLogic.maxTextLineWidthPt(72f, page)
        // 595.28 - 72 (left) - 72 (right) = 451.28
        assertEquals(451.28f, w72, 0.01f)
    }

    @Test
    fun `maxTextLineWidthPt computes standard margin in right half of page`() {
        val page = PageSizePt(595.28f, 841.89f)
        val w400 = PlaceMarkupLogic.maxTextLineWidthPt(400f, page)
        // 595.28 - 400 (left) - 36 (standard right margin) = 159.28
        assertEquals(159.28f, w400, 0.01f)
    }

    @Test
    fun `wrapText keeps short text on single line`() {
        val page = PageSizePt(595.28f, 841.89f)
        val shortText = "Короткий текст"
        val wrapped = PlaceMarkupLogic.wrapText(shortText, 50f, 14f, page)
        assertEquals(shortText, wrapped)
    }

    @Test
    fun `wrapText wraps long paragraph across multiple lines preserving margins`() {
        val page = PageSizePt(595.28f, 841.89f)
        val longText = "Під час редагування документів користувач часто вставляє великі фрагменти тексту, " +
                "які мають акуратно переноситися на наступний рядок зі збереженням розміру полів сторінки без виходу за краї."
        val wrapped = PlaceMarkupLogic.wrapText(longText, 50f, 14f, page)
        val lines = wrapped.split('\n')
        assertTrue("Довгий текст має бути розбитий на кілька рядків, отримано рядків: ${lines.size}", lines.size >= 2)
        val maxWidth = PlaceMarkupLogic.maxTextLineWidthPt(50f, page)
        for (line in lines) {
            val w = PlaceMarkupLogic.estimateTextWidthPt(line, 14f)
            assertTrue("Рядок «$line» (ширина $w pt) не повинен перевищувати $maxWidth pt", w <= maxWidth + 5f)
        }
    }

    @Test
    fun `wrapText preserves explicit paragraphs and empty lines`() {
        val page = PageSizePt(595.28f, 841.89f)
        val input = "Перший абзац тексту.\n\nДругий дуже довгий абзац тексту, який обов'язково має бути перенесений на кілька рядків нижче."
        val wrapped = PlaceMarkupLogic.wrapText(input, 50f, 14f, page)
        val paragraphs = wrapped.split("\n\n")
        assertEquals(2, paragraphs.size)
        assertEquals("Перший абзац тексту.", paragraphs[0])
        assertTrue(paragraphs[1].contains('\n'))
    }

    @Test
    fun `wrapText breaks long unbroken words without spaces`() {
        val page = PageSizePt(595.28f, 841.89f)
        val unbrokenWord = "a".repeat(300)
        val wrapped = PlaceMarkupLogic.wrapText(unbrokenWord, 50f, 14f, page)
        val lines = wrapped.split('\n')
        assertTrue("Суцільне довге слово має розбиватися на частини, отримано: ${lines.size}", lines.size >= 3)
    }

    @Test
    fun `textFromTap with page wraps text automatically`() {
        val page = PageSizePt(595.28f, 841.89f)
        val text = "Один два три чотири п'ять шість сім вісім дев'ять десять одинадцять дванадцять тринадцять чотирнадцять"
        val check = PlaceMarkupLogic.textFromTap(
            "t1", 0, 50f, 50f, text, 14f, 0xFF000000.toInt(), "", page = page
        ) as TextCheck.Ok
        assertTrue(check.markup.text.contains('\n'))
    }

    @Test
    fun `clampedAnchor clamps text so right edge stays within page margins`() {
        val page = PageSizePt(600f, 800f)
        val item = PendingMarkup("t", PlaceTool.TEXT, 0, 50f, 50f, "Hello World Wide Text Block", 14f, 0xFF000000.toInt())
        val (cx, cy) = PlaceMarkupLogic.clampedAnchor(item, 9999f, 50f, page)
        val textWidth = PlaceMarkupLogic.estimateTextWidthPt(item.text, item.sizePt)
        assertTrue("Текст не повинен виходити за правий край: cx ($cx) + textWidth ($textWidth) <= 600", cx + textWidth <= 600f)
        assertEquals(50f, cy, 0f)
    }

    @Test
    fun `long line is not wrapped without page`() {
        val lines = MarkupOverlayMath.textLinesPx("a".repeat(500), 0f, 0f, 14f, 1f)
        assertEquals(1, lines.size)
    }

    @Test
    fun `unwrapText merges single newlines into spaces and preserves double newlines`() {
        val wrapped = "Це перший рядок\nтексту який перенесли.\n\nА це другий абзац\nз двома рядками."
        val unwrapped = PlaceMarkupLogic.unwrapText(wrapped)
        val paragraphs = unwrapped.split("\n\n")
        assertEquals(2, paragraphs.size)
        assertEquals("Це перший рядок тексту який перенесли.", paragraphs[0])
        assertEquals("А це другий абзац з двома рядками.", paragraphs[1])
    }

    @Test
    fun `wrapTextToWidth adapts text dynamically to narrower and wider box widths`() {
        val raw = "Швидка бура лисиця перестрибує через ледачого собаку в лісі."
        // Вузьке вікно: багато рядків
        val narrow = PlaceMarkupLogic.wrapTextToWidth(raw, maxWidthPt = 100f, fontSizePt = 14f)
        val narrowLines = narrow.split('\n')
        assertTrue("Вузьке вікно повинно створити щонайменше 3 рядки, отримано: ${narrowLines.size}", narrowLines.size >= 3)

        // Розширюємо вікно: рядки перетікають у меншу кількість рядків
        val wide = PlaceMarkupLogic.wrapTextToWidth(narrow, maxWidthPt = 400f, fontSizePt = 14f)
        val wideLines = wide.split('\n')
        assertTrue("Широке вікно повинно зменшити кількість рядків (отримано ${wideLines.size} проти ${narrowLines.size})", wideLines.size < narrowLines.size)
    }

    @Test
    fun `textFromTap preserves rawText in PendingMarkup`() {
        val page = PageSizePt(595.28f, 841.89f)
        val raw = "Текст для перевірки rawText збереження"
        val check = PlaceMarkupLogic.textFromTap(
            "t1", 0, 50f, 50f, raw, 14f, 0xFF000000.toInt(), "", page = page
        ) as TextCheck.Ok
        assertEquals(raw, check.markup.rawText)
    }

    @Test
    fun `resolveDragPlacement keeps image at drop point when inside page bounds`() {
        val page = PageSizePt(595f, 842f)
        val img = PendingMarkup(
            id = "img1", tool = PlaceTool.IMAGE, pageIndex = 0,
            x = 20f, y = 20f, sizePt = 200f, color = 0, text = "", aspect = 0.5f
        )
        val placed = PlaceMarkupLogic.resolveDragPlacement(img, 180f, 400f, page)
        assertTrue(placed.canFitOnPage)
        assertTrue(!placed.isOutsideBounds)
        assertEquals(180f, placed.x, 0.01f)
        assertEquals(400f, placed.y, 0.01f)
    }

    @Test
    fun `resolveDragPlacement clamps to nearest valid point when dragged past page edge`() {
        val page = PageSizePt(595f, 842f)
        val img = PendingMarkup(
            id = "img1", tool = PlaceTool.IMAGE, pageIndex = 0,
            x = 50f, y = 50f, sizePt = 200f, color = 0, text = "", aspect = 0.5f // w=200, h=100
        )
        val placedRightBottom = PlaceMarkupLogic.resolveDragPlacement(img, 500f, 800f, page)
        assertTrue(placedRightBottom.canFitOnPage)
        assertTrue(placedRightBottom.isOutsideBounds)
        assertEquals(395f, placedRightBottom.x, 0.01f) // 595 - 200
        assertEquals(742f, placedRightBottom.y, 0.01f) // 842 - 100

        val placedLeftTop = PlaceMarkupLogic.resolveDragPlacement(img, -40f, -25f, page)
        assertTrue(placedLeftTop.canFitOnPage)
        assertTrue(placedLeftTop.isOutsideBounds)
        assertEquals(0f, placedLeftTop.x, 0.01f)
        assertEquals(0f, placedLeftTop.y, 0.01f)
    }

    @Test
    fun `resolveDragPlacement accounts for 90 180 270 rotation and scaled size`() {
        val page = PageSizePt(595f, 842f)
        val img = PendingMarkup(
            id = "img1", tool = PlaceTool.IMAGE, pageIndex = 0,
            x = 100f, y = 100f, sizePt = 200f, color = 0, text = "", aspect = 0.5f // w=200, h=100
        )
        var state = PlaceEditor.add(PlaceEditorState(), img)
        // Поворот на 90°: w=100, h=200 (aspect=2.0)
        state = PlaceEditor.rotate(state, "img1", 90, page)
        val rot90 = state.items.single()
        val p90 = PlaceMarkupLogic.resolveDragPlacement(rot90, 520f, 700f, page)
        assertEquals(495f, p90.x, 0.01f) // 595 - 100
        assertEquals(642f, p90.y, 0.01f) // 842 - 200

        // Поворот ще на 90° (180°): w=200, h=100
        state = PlaceEditor.rotate(state, "img1", 90, page)
        val rot180 = state.items.single()
        val p180 = PlaceMarkupLogic.resolveDragPlacement(rot180, 520f, 700f, page)
        assertEquals(395f, p180.x, 0.01f) // 595 - 200
        assertEquals(700f, p180.y, 0.01f) // 700 <= 842 - 100

        // Поворот ще на 90° (270°) і збільшення ширини щипком до 300 (h=600)
        state = PlaceEditor.rotate(state, "img1", 90, page)
        state = PlaceEditor.setSizeAndPosition(state, "img1", 300f, 100f, 100f, page)
        val rot270Scaled = state.items.single()
        val p270 = PlaceMarkupLogic.resolveDragPlacement(rot270Scaled, 400f, 400f, page)
        assertEquals(295f, p270.x, 0.01f) // 595 - 300
        assertEquals(242f, p270.y, 0.01f) // 842 - 600
    }

    @Test
    fun `resolveDragPlacement reports canFitOnPage false when image cannot fit on page`() {
        val smallPage = PageSizePt(300f, 400f)
        val oversized = PendingMarkup(
            id = "img1", tool = PlaceTool.IMAGE, pageIndex = 0,
            x = 10f, y = 10f, sizePt = 350f, color = 0, text = "", aspect = 1f
        )
        val placed = PlaceMarkupLogic.resolveDragPlacement(oversized, 20f, 20f, smallPage)
        assertTrue(!placed.canFitOnPage)
        assertTrue(placed.isOutsideBounds)
    }

    @Test
    fun `resolveDragPlacement keeps text at drop point when inside page bounds`() {
        val page = PageSizePt(595f, 842f)
        val txt = PendingMarkup(
            id = "t1", tool = PlaceTool.TEXT, pageIndex = 0,
            x = 36f, y = 40f, text = "Привіт\nДругий рядок", sizePt = 16f, color = 0xFF000000.toInt()
        )
        val placed = PlaceMarkupLogic.resolveDragPlacement(txt, 140f, 320f, page)
        assertTrue(placed.canFitOnPage)
        assertTrue(!placed.isOutsideBounds)
        assertEquals(140f, placed.x, 0.01f)
        assertEquals(320f, placed.y, 0.01f)
    }

    @Test
    fun `resolveDragPlacement clamps text to nearest valid point using block width and height`() {
        val page = PageSizePt(595f, 842f)
        val txt = PendingMarkup(
            id = "t1", tool = PlaceTool.TEXT, pageIndex = 0,
            x = 50f, y = 50f, text = "Рядок 1\nДовший рядок тексту 2\nРядок 3", sizePt = 18f, color = 0xFF000000.toInt()
        )
        val (w, h) = PlaceMarkupLogic.textBlockSizePt(txt)
        val placedRB = PlaceMarkupLogic.resolveDragPlacement(txt, 580f, 830f, page)
        assertTrue(placedRB.canFitOnPage)
        assertTrue(placedRB.isOutsideBounds)
        assertEquals(595f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT - w, placedRB.x, 0.05f)
        assertEquals(842f - h, placedRB.y, 0.05f)

        val placedLT = PlaceMarkupLogic.resolveDragPlacement(txt, -30f, -15f, page)
        assertTrue(placedLT.canFitOnPage)
        assertTrue(placedLT.isOutsideBounds)
        assertEquals(0f, placedLT.x, 0.01f)
        assertEquals(0f, placedLT.y, 0.01f)
    }

    @Test
    fun `resolveDragPlacement reports canFitOnPage false when text block is wider than page`() {
        val narrowPage = PageSizePt(120f, 400f)
        val wideText = PendingMarkup(
            id = "t1", tool = PlaceTool.TEXT, pageIndex = 0,
            x = 10f, y = 10f, text = "Дуже довгий нерозривний рядок тексту", sizePt = 20f, color = 0xFF000000.toInt()
        )
        val placed = PlaceMarkupLogic.resolveDragPlacement(wideText, 20f, 20f, narrowPage)
        assertTrue(!placed.canFitOnPage)
        assertTrue(placed.isOutsideBounds)
    }
}
