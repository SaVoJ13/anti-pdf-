package com.savoj.volumpdf.core.pdf

import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.MarkKind
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** JVM-тести [PdfBoxMarkupEngine] (CONTRACTS v0.4, §1.5) на програмно створених PDF. */
class PdfBoxMarkupEngineTest : MarkupTestSupport() {

    private lateinit var engine: PdfBoxMarkupEngine
    private val tol = 0.5f
    private var counter = 0

    @Before
    fun setUp() {
        engine = newMarkupEngine()
    }

    // ---------- допоміжне ----------

    private data class Geo(val media: PDRectangle, val crop: PDRectangle?) {
        val box: PDRectangle get() = crop ?: media
        val label: String get() = "media=${media.lowerLeftX},${media.lowerLeftY},${media.width}x${media.height} crop=${crop?.let { "${it.lowerLeftX},${it.lowerLeftY},${it.width}x${it.height}" }}"
    }

    private val geos = listOf(
        Geo(PDRectangle.A4, PDRectangle(100f, 200f, 395f, 500f)),
        Geo(PDRectangle.LETTER, PDRectangle(50f, 60f, 350f, 440f)),
        Geo(PDRectangle(-50f, -20f, 400f, 400f), null)
    )

    private fun pdfWithPages(geometries: List<Geo>, rotate: Int, name: String): File =
        saveNew({ doc ->
            for (g in geometries) {
                val page = PDPage(g.media)
                if (g.crop != null) page.cropBox = g.crop
                page.cosObject.setInt(COSName.ROTATE, rotate)
                doc.addPage(page)
            }
        }, "${counter++}_$name")

    private fun simplePdf(pages: Int = 2, name: String = "simple.pdf"): File =
        pdfWithPages(List(pages) { Geo(PDRectangle.A4, null) }, 0, name)

    private fun out(): File = tempFolder.newFile("out_${counter++}.pdf")

    private fun apply(src: File, dst: File, markups: List<PageMarkup>, forms: List<FormFieldUpdate> = emptyList(), password: String? = null) =
        runBlocking { engine.applyEdits(mockContext, uri(src), markups, forms, uri(dst), password) }

    private fun textPositions(doc: PDDocument, pageNo: Int): List<TextPosition> {
        val all = ArrayList<TextPosition>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
                all.addAll(textPositions)
                super.writeString(string, textPositions)
            }
        }
        stripper.startPage = pageNo + 1
        stripper.endPage = pageNo + 1
        stripper.getText(doc)
        return all.filter { !it.unicode.isBlank() }
    }

    private fun lastImageCtm(page: PDPage): FloatArray {
        val tokens = PDFStreamParser(page).also { it.parse() }.tokens
        var ctm = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        val stack = ArrayDeque<FloatArray>()
        val operands = ArrayList<Any>()
        var result: FloatArray? = null
        for (t in tokens) {
            if (t is Operator) {
                when (t.name) {
                    "q" -> stack.addLast(ctm.copyOf())
                    "Q" -> if (stack.isNotEmpty()) ctm = stack.removeLast()
                    "cm" -> {
                        val n = operands.map { (it as COSNumber).floatValue() }
                        val a = n[0]; val b = n[1]; val c = n[2]; val d = n[3]; val e = n[4]; val f = n[5]
                        ctm = floatArrayOf(
                            a * ctm[0] + b * ctm[2], a * ctm[1] + b * ctm[3],
                            c * ctm[0] + d * ctm[2], c * ctm[1] + d * ctm[3],
                            e * ctm[0] + f * ctm[2] + ctm[4], e * ctm[1] + f * ctm[3] + ctm[5]
                        )
                    }
                    "Do" -> result = ctm.copyOf()
                }
                operands.clear()
            } else operands.add(t)
        }
        return result ?: throw AssertionError("Оператор Do не знайдено")
    }

    // ---------- MarkupLayout, шрифт, кирилиця ----------

    @Test
    fun layoutConstantsAndPolylinesMatchContract() {
        assertEquals(0.93f, MarkupLayout.TEXT_ASCENT_RATIO, 0f)
        assertEquals(1.2f, MarkupLayout.TEXT_LINE_HEIGHT_RATIO, 0f)
        assertEquals(0.12f, MarkupLayout.MARK_STROKE_RATIO, 0f)
        assertEquals("fonts/Roboto-Regular.ttf", MarkupLayout.FONT_ASSET_PATH)
        val check = MarkupLayout.markPolylines(MarkKind.CHECK)
        assertEquals(1, check.size)
        assertEquals(listOf(0.15f to 0.55f, 0.40f to 0.80f, 0.85f to 0.20f), check[0].map { it.x to it.y })
        val cross = MarkupLayout.markPolylines(MarkKind.CROSS)
        assertEquals(listOf(0.2f to 0.2f, 0.8f to 0.8f), cross[0].map { it.x to it.y })
        assertEquals(listOf(0.8f to 0.2f, 0.2f to 0.8f), cross[1].map { it.x to it.y })
    }

    @Test
    fun findUnsupportedCharsReportsOnlyMissingGlyphs() {
        assertEquals("", engine.findUnsupportedChars("Привіт, світе! Ґанок Єва ї Ѣ? Hello 123\nдруга"))
        val bad = engine.findUnsupportedChars("a✓b✗c✓日")
        assertEquals("✓✗日", bad)
        assertEquals("", engine.findUnsupportedChars(""))
    }

    @Test
    fun cyrillicTextIsExtractedAndEmbeddedAsSubset() {
        val src = simplePdf(1)
        val dst = out()
        val text = "Привіт, світе!\nҐанок Єва їж"
        val r = apply(src, dst, listOf(PageMarkup.Text("t1", 0, 40f, 60f, text, 14f, 0xFF000000.toInt())))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(dst).use { doc ->
            val extracted = PDFTextStripper().getText(doc)
            assertTrue("витягнуто: $extracted", extracted.contains("Привіт, світе!"))
            assertTrue("витягнуто: $extracted", extracted.contains("Ґанок Єва їж"))
            val fonts = doc.getPage(0).resources.fontNames.map { doc.getPage(0).resources.getFont(it) }
            val font = fonts.single()
            assertTrue(font.name, font.name.contains("+Roboto", ignoreCase = true) && font.isEmbedded)
            // підмножина: файл набагато менший за повний шрифт (171 КБ)
            assertTrue("розмір ${dst.length()}", dst.length() < 100_000)
        }
        // перший і другий рядки: базові лінії y+0,93·fs і +1,2·fs
        PDDocument.load(dst).use { doc ->
            val letters = textPositions(doc, 0)
            val first = letters.first()
            assertEquals(40f, first.x, tol)
            assertEquals(60f + 0.93f * 14f, first.y, tol)
            val secondLine = letters.first { it.unicode == "Ґ" }
            assertEquals(60f + 0.93f * 14f + 1.2f * 14f, secondLine.y, tol)
            assertEquals(40f, secondLine.x, tol)
        }
    }

    @Test
    fun unsupportedCharsInTextGiveErrorAndWriteNothing() {
        val src = simplePdf(1)
        val dst = tempFolder.root.resolve("never.pdf")
        val r = apply(src, dst, listOf(PageMarkup.Text("t", 0, 10f, 10f, "ok ✓", 14f, 0xFF000000.toInt())))
        assertTrue(r is PdfOperationResult.Error)
        assertTrue((r as PdfOperationResult.Error).userMessage.contains("✓"))
        assertFalse(dst.exists())
    }

    // ---------- Геометрія: 4 повороти × зсунутий CropBox × 2 сторінки × 4 типи ----------

    @Test
    fun allFourTypesPlacedCorrectlyOnAllRotationsBoxesAndPages() {
        val textX = 40f; val textY = 30f; val fs = 12f
        val markX = 70f; val markY = 90f; val markSize = 20f
        val inkPts = listOf(pt(10f, 20f), pt(60f, 90f), pt(30f, 50f), pt(80f, 40f))
        val sigL = 20f; val sigT = 100f; val sigW = 60f; val sigH = 30f
        for (rot in listOf(0, 90, 180, 270)) {
            for (k in geos.indices) {
                val pair = listOf(geos[k], geos[(k + 1) % geos.size])
                val src = pdfWithPages(pair, rot, "geo.pdf")
                val dst = out()
                val markups = pair.indices.flatMap { p ->
                    listOf(
                        PageMarkup.Text("t$p", p, textX, textY, "Hello", fs, 0xFF0000FF.toInt()),
                        PageMarkup.Mark("c$p", p, markX, markY, MarkKind.CHECK, markSize, 0xFF00AA00.toInt()),
                        PageMarkup.Mark("x$p", p, markX + 40f, markY, MarkKind.CROSS, markSize, 0xFFAA0000.toInt()),
                        PageMarkup.Ink("i$p", p, listOf(InkStroke(inkPts, 0xFFFF00FF.toInt(), 3f))),
                        PageMarkup.Signature("s$p", p, mockBitmap(), sigL, sigT, sigW, sigH)
                    )
                }
                val r = apply(src, dst, markups)
                assertTrue("rot=$rot k=$k: $r", r is PdfOperationResult.Success)
                PDDocument.load(dst).use { doc ->
                    for (p in pair.indices) {
                        val label = "rot=$rot page=$p ${pair[p].label}"
                        val box = pair[p].box
                        val page = doc.getPage(p)

                        // Text
                        val letters = textPositions(doc, p)
                        assertEquals("$label text letters", 5, letters.size)
                        assertEquals("$label text x", textX, letters.first().x, tol)
                        assertEquals("$label text baseline", textY + 0.93f * fs, letters.first().y, tol)
                        assertTrue("$label текст не йде вправо", letters.last().x > letters.first().x + 15f)
                        assertEquals("$label baseline last", letters.first().y, letters.last().y, tol)

                        // Mark + Ink: вершини штрихів
                        val strokes = probeStrokes(page)
                        // порядок: 2 позначки (✓: 1 ламана, ✗: 2 ламані), потім Ink (підпис не штрих)
                        assertEquals("$label кількість штрихів", 1 + 2 + 1, strokes.size)
                        fun markVertices(cx: Float, cy: Float, kind: MarkKind) =
                            MarkupLayout.markPolylines(kind).map { line ->
                                line.map { expectedUser(rot, box, cx - markSize / 2 + it.x * markSize, cy - markSize / 2 + it.y * markSize) }
                            }
                        val expectedMarkLines = markVertices(markX, markY, MarkKind.CHECK) + markVertices(markX + 40f, markY, MarkKind.CROSS)
                        for ((i, line) in expectedMarkLines.withIndex()) {
                            val s = strokes[i]
                            assertEquals("$label mark[$i] vertices", line.size, s.vertices.size)
                            line.forEachIndexed { j, e ->
                                assertEquals("$label mark[$i].x[$j]", e.first, s.vertices[j].first, tol)
                                assertEquals("$label mark[$i].y[$j]", e.second, s.vertices[j].second, tol)
                            }
                            assertEquals("$label mark width", MarkupLayout.MARK_STROKE_RATIO * markSize, s.lineWidth, 0.001f)
                            assertEquals("$label mark cap", "ROUND", s.lineCap)
                            assertEquals("$label mark join", "ROUND", s.lineJoin)
                        }
                        val ink = strokes.last()
                        // очікувані вершини: p0, середини сусідніх, остання точка
                        val ev = ArrayList<Pair<Float, Float>>()
                        ev.add(inkPts[0].x to inkPts[0].y)
                        for (i in 1 until inkPts.size - 1) ev.add((inkPts[i - 1].x + inkPts[i].x) / 2 to (inkPts[i - 1].y + inkPts[i].y) / 2)
                        ev.add(inkPts.last().x to inkPts.last().y)
                        assertEquals("$label ink vertices", ev.size, ink.vertices.size)
                        assertEquals("$label ink кількість кривих", inkPts.size - 2, ink.curveCount)
                        ev.forEachIndexed { j, v ->
                            val e = expectedUser(rot, box, v.first, v.second)
                            assertEquals("$label ink.x[$j]", e.first, ink.vertices[j].first, tol)
                            assertEquals("$label ink.y[$j]", e.second, ink.vertices[j].second, tol)
                        }
                        assertEquals("$label ink width", 3f, ink.lineWidth, 0.001f)
                        assertEquals("$label ink alpha", 1f, ink.strokeAlpha, 0.001f)

                        // Signature
                        val ctm = lastImageCtm(page)
                        val (ex0, ey0) = expectedUser(rot, box, sigL, sigT + sigH)
                        val (ex1, ey1) = expectedUser(rot, box, sigL + sigW, sigT)
                        assertEquals("$label sig (0,0).x", ex0, ctm[4], tol)
                        assertEquals("$label sig (0,0).y", ey0, ctm[5], tol)
                        assertEquals("$label sig (1,1).x", ex1, ctm[0] + ctm[2] + ctm[4], tol)
                        assertEquals("$label sig (1,1).y", ey1, ctm[1] + ctm[3] + ctm[5], tol)
                    }
                }
            }
        }
    }

    @Test
    fun inkSmoothingEndpointsAreMidpointsAndSinglePointStrokeMakesDot() {
        val src = simplePdf(1)
        val dst = out()
        val pts = listOf(pt(100f, 100f), pt(120f, 100f), pt(140f, 120f))
        val single = listOf(pt(200f, 200f))
        val r = apply(src, dst, listOf(PageMarkup.Ink("i", 0, listOf(
            InkStroke(pts, 0xFF000000.toInt(), 2f), InkStroke(single, 0xFF000000.toInt(), 6f), InkStroke(emptyList(), 0xFF000000.toInt(), 6f)
        ))))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(dst).use { doc ->
            val strokes = probeStrokes(doc.getPage(0))
            assertEquals(2, strokes.size) // порожній штрих пропущено
            // A4: ury = 841.89; (100,100) -> (100, ury-100); середина (110,100); кінець (140,120)
            val ury = PDRectangle.A4.upperRightY
            val v = strokes[0].vertices
            assertEquals(3, v.size)
            assertEquals(100f, v[0].first, tol); assertEquals(ury - 100f, v[0].second, tol)
            assertEquals(110f, v[1].first, tol); assertEquals(ury - 100f, v[1].second, tol)
            assertEquals(140f, v[2].first, tol); assertEquals(ury - 120f, v[2].second, tol)
            assertEquals(2, strokes[1].vertices.size) // крапка = мінімальний відрізок з круглими кінцями
            assertEquals("ROUND", strokes[1].lineCap)
        }
    }

    // ---------- Альфа ----------

    @Test
    fun alphaFromArgbIsAppliedThroughExtGState() {
        val src = simplePdf(1)
        val dst = out()
        val half = 0x80FF0000.toInt() // ~0,5 альфа, червоний
        val r = apply(src, dst, listOf(
            PageMarkup.Ink("i", 0, listOf(InkStroke(listOf(pt(50f, 50f), pt(150f, 80f)), half, 12f))),
            PageMarkup.Text("t", 0, 50f, 200f, "Alpha", 20f, 0x40000000),
            PageMarkup.Ink("opaque", 0, listOf(InkStroke(listOf(pt(50f, 300f), pt(150f, 300f)), 0xFF00FF00.toInt(), 4f)))
        ))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(dst).use { doc ->
            val strokes = probeStrokes(doc.getPage(0))
            assertEquals(2, strokes.size)
            assertEquals(128f / 255f, strokes[0].strokeAlpha, 0.005f)
            assertArrayEquals(floatArrayOf(1f, 0f, 0f), strokes[0].rgb, 0.005f)
            assertEquals(12f, strokes[0].lineWidth, 0.001f)
            assertEquals("непрозорий штрих не має зазнавати впливу попереднього", 1f, strokes[1].strokeAlpha, 0.001f)
            // ExtGState у ресурсах: CA/ca ≈ 0,5 і ≈ 0,25
            val res = doc.getPage(0).resources
            val alphas = res.extGStateNames.map { res.getExtGState(it) }
            assertTrue(alphas.any { Math.abs((it.strokingAlphaConstant ?: 0f) - 128f / 255f) < 0.005f })
            assertTrue(alphas.any { Math.abs((it.nonStrokingAlphaConstant ?: 0f) - 64f / 255f) < 0.005f })
        }
    }

    // ---------- Зашифрований PDF ----------

    private fun encryptedPdf(password: String): File {
        val f = tempFolder.newFile("enc_${counter++}.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            val policy = StandardProtectionPolicy("owner-secret", password, AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(f)
        }
        return f
    }

    @Test
    fun encryptedPdfNeedsPasswordAndStaysEncrypted() {
        val src = encryptedPdf("pw123")
        val markups = listOf(PageMarkup.Text("t", 0, 40f, 60f, "Секрет", 14f, 0xFF000000.toInt()))

        val noPw = apply(src, out(), markups)
        assertTrue(noPw is PdfOperationResult.Error)
        assertTrue((noPw as PdfOperationResult.Error).userMessage.contains("пароль"))

        val wrong = apply(src, out(), markups, password = "nope")
        assertTrue(wrong is PdfOperationResult.Error)

        val dst = out()
        val ok = apply(src, dst, markups, password = "pw123")
        assertTrue(ok.toString(), ok is PdfOperationResult.Success)
        // результат лишається зашифрованим і відкривається з паролем
        assertTrue(runCatching { PDDocument.load(dst) }.isFailure)
        PDDocument.load(dst, "pw123").use { doc ->
            assertTrue(doc.isEncrypted)
            val s = PDFTextStripper().getText(doc)
            assertTrue(s, s.contains("Секрет"))
        }
    }

    // ---------- Помилки ----------

    @Test
    fun wrongPageIndexGivesErrorAndNothingIsWritten() {
        for (bad in listOf(-1, 2, 99)) {
            val src = simplePdf(2)
            val dst = tempFolder.root.resolve("none_$bad.pdf")
            val r = apply(src, dst, listOf(PageMarkup.Mark("m", bad, 50f, 50f, MarkKind.CHECK, 16f, 0xFF000000.toInt())))
            assertTrue("index $bad: $r", r is PdfOperationResult.Error)
            assertTrue((r as PdfOperationResult.Error).userMessage.isNotBlank())
            assertFalse(dst.exists())
        }
    }

    @Test
    fun originalStaysIntactAfterFailure() {
        val src = simplePdf(2)
        val before = src.readBytes()
        // перший об'єкт валідний, другий — помилковий індекс: ніщо не записується
        val r1 = apply(src, src, listOf(
            PageMarkup.Mark("ok", 0, 50f, 50f, MarkKind.CHECK, 16f, 0xFF000000.toInt()),
            PageMarkup.Mark("bad", 7, 50f, 50f, MarkKind.CROSS, 16f, 0xFF000000.toInt())
        ))
        assertTrue(r1 is PdfOperationResult.Error)
        assertArrayEquals(before, src.readBytes())
        // повністю поза сторінкою
        val r2 = apply(src, src, listOf(PageMarkup.Mark("out", 1, 5000f, 5000f, MarkKind.CHECK, 16f, 0xFF000000.toInt())))
        assertTrue(r2 is PdfOperationResult.Error)
        assertArrayEquals(before, src.readBytes())
        // невідомий пароль до незашифрованого — не помилка; а до шифрованого — помилка й оригінал цілий
        val enc = encryptedPdf("a")
        val encBefore = enc.readBytes()
        val r3 = apply(enc, enc, listOf(PageMarkup.Mark("m", 0, 50f, 50f, MarkKind.CHECK, 16f, 0xFF000000.toInt())), password = "wrong")
        assertTrue(r3 is PdfOperationResult.Error)
        assertArrayEquals(encBefore, enc.readBytes())
        // жодних проміжних файлів
        assertEquals(emptyList<String>(), src.parentFile!!.list()!!.filter { it.endsWith(".tmp") })
    }

    @Test
    fun overwritingSourceInPlaceWorksOnSuccess() {
        val src = simplePdf(1)
        val r = apply(src, src, listOf(PageMarkup.Mark("m", 0, 50f, 50f, MarkKind.CHECK, 16f, 0xFF000000.toInt())))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(src).use { assertEquals(1, probeStrokes(it.getPage(0)).size) }
        assertEquals(emptyList<String>(), src.parentFile!!.list()!!.filter { it.endsWith(".tmp") })
    }

    @Test
    fun objectsFullyOutsidePageGiveErrorButPartialOverhangIsAllowed() {
        val src = pdfWithPages(listOf(geos[0]), 90, "out.pdf") // видима область 500 × 395
        val colour = 0xFF000000.toInt()
        val outside = listOf(
            PageMarkup.Text("t", 0, 600f, 10f, "Hi", 14f, colour),
            PageMarkup.Text("t2", 0, 10f, -50f, "Hi", 14f, colour),
            PageMarkup.Mark("m", 0, -30f, 100f, MarkKind.CHECK, 16f, colour),
            PageMarkup.Ink("i", 0, listOf(InkStroke(listOf(pt(-10f, -10f), pt(-5f, -20f)), colour, 2f))),
            PageMarkup.Signature("s", 0, mockBitmap(), 10f, 500f, 50f, 20f)
        )
        for (m in outside) {
            val dst = tempFolder.root.resolve("o_${counter++}.pdf")
            val r = apply(src, dst, listOf(m))
            assertTrue("${m.id}: $r", r is PdfOperationResult.Error)
            assertTrue((r as PdfOperationResult.Error).userMessage.contains(m.id))
            assertFalse(dst.exists())
        }
        val partial = listOf(
            PageMarkup.Text("t", 0, 480f, 10f, "Wide text", 14f, colour),
            PageMarkup.Mark("m", 0, 2f, 100f, MarkKind.CHECK, 16f, colour),
            PageMarkup.Ink("i", 0, listOf(InkStroke(listOf(pt(-10f, 50f), pt(30f, 60f)), colour, 2f))),
            PageMarkup.Signature("s", 0, mockBitmap(), 470f, 380f, 100f, 50f)
        )
        val r = apply(src, out(), partial)
        assertTrue(r.toString(), r is PdfOperationResult.Success)
    }

    @Test
    fun emptyEditsStillSaveAndEmptyTextIsSkipped() {
        val src = simplePdf(1)
        val r = apply(src, out(), listOf(PageMarkup.Text("e", 0, 10f, 10f, "", 14f, 0xFF000000.toInt()), PageMarkup.Ink("n", 0, emptyList())))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        val r2 = apply(src, out(), emptyList())
        assertTrue(r2 is PdfOperationResult.Success)
    }

    // ---------- Форми ----------

    private fun formPdf(): File = saveNew({ doc ->
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        val acro = PDAcroForm(doc); doc.documentCatalog.acroForm = acro
        val res = PDResources(); res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
        acro.defaultResources = res; acro.defaultAppearance = "/Helv 12 Tf 0 g"

        val text = PDTextField(acro); text.partialName = "name"
        text.widgets[0].rectangle = PDRectangle(50f, 700f, 200f, 30f); text.widgets[0].page = page
        page.annotations.add(text.widgets[0]); acro.fields.add(text)

        val check = PDCheckBox(acro); check.partialName = "agree"
        val cw = check.widgets[0]; cw.rectangle = PDRectangle(50f, 650f, 20f, 20f); cw.page = page
        val ap = PDAppearanceDictionary(); val states = COSDictionary()
        states.setItem(COSName.getPDFName("Yes"), PDAppearanceStream(doc)); states.setItem(COSName.Off, PDAppearanceStream(doc))
        ap.normalAppearance = PDAppearanceEntry(states); cw.appearance = ap
        page.annotations.add(cw); acro.fields.add(check); check.unCheck()

        val combo = PDComboBox(acro); combo.partialName = "country"
        combo.options = listOf("Ukraine", "USA", "Poland")
        combo.widgets[0].rectangle = PDRectangle(50f, 600f, 200f, 30f); combo.widgets[0].page = page
        page.annotations.add(combo.widgets[0]); acro.fields.add(combo)

        // типи поза контрактом
        val radio = PDRadioButton(acro); radio.partialName = "choice"; radio.exportValues = listOf("A", "B")
        radio.widgets[0].rectangle = PDRectangle(50f, 550f, 20f, 20f); radio.widgets[0].page = page
        page.annotations.add(radio.widgets[0]); acro.fields.add(radio)
        val push = PDPushButton(acro); push.partialName = "submit"
        push.widgets[0].rectangle = PDRectangle(50f, 500f, 80f, 30f); push.widgets[0].page = page
        page.annotations.add(push.widgets[0]); acro.fields.add(push)
        val sig = PDSignatureField(acro); sig.partialName = "sig"
        sig.widgets[0].rectangle = PDRectangle(50f, 450f, 100f, 40f); sig.widgets[0].page = page
        page.annotations.add(sig.widgets[0]); acro.fields.add(sig)
    }, "${counter++}_form.pdf")

    @Test
    fun formUpdatesOfEachTypeAreSavedWithAppearances() {
        val src = formPdf()
        val dst = out()
        val r = apply(src, dst, emptyList(), listOf(
            FormFieldUpdate("name", "Іван Петренко"),
            FormFieldUpdate("agree", true),
            FormFieldUpdate("country", "Poland")
        ))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(dst).use { doc ->
            val form = doc.documentCatalog.acroForm
            assertEquals("Іван Петренко", (form.getField("name") as PDTextField).value)
            assertTrue((form.getField("agree") as PDCheckBox).isChecked)
            assertEquals("Poland", (form.getField("country") as PDComboBox).value.first())
            // значення мають бути видимі без NeedAppearances-залежності: у текстового поля є потік /AP /N
            val ap = (form.getField("name") as PDTextField).widgets[0].appearance?.normalAppearance?.appearanceStream
            assertNotNull("немає /AP /N у текстового поля", ap)
        }
    }

    @Test
    fun unsupportedFieldTypesAreSkippedWithoutError() {
        val src = formPdf()
        val dst = out()
        val r = apply(src, dst, emptyList(), listOf(
            FormFieldUpdate("choice", "B"),      // радіо
            FormFieldUpdate("submit", "x"),      // кнопка
            FormFieldUpdate("sig", "x"),         // підпис
            FormFieldUpdate("no-such-field", "x"),
            FormFieldUpdate("name", "ok")
        ))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(dst).use { doc ->
            val form = doc.documentCatalog.acroForm
            assertEquals("ok", (form.getField("name") as PDTextField).value)
            assertTrue(form.getField("choice") is PDRadioButton)
            assertTrue(form.getField("submit") is PDPushButton)
            assertTrue(form.getField("sig") is PDSignatureField)
        }
    }

    @Test
    fun formAndMarkupInOneCallFormFirst() {
        val src = formPdf()
        val dst = out()
        val r = apply(src, dst,
            listOf(PageMarkup.Text("t", 0, 300f, 100f, "Підпис", 14f, 0xFF000000.toInt()), PageMarkup.Mark("m", 0, 300f, 150f, MarkKind.CROSS, 16f, 0xFF000000.toInt())),
            listOf(FormFieldUpdate("name", "Form value")))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(dst).use { doc ->
            assertEquals("Form value", (doc.documentCatalog.acroForm.getField("name") as PDTextField).value)
            assertTrue(PDFTextStripper().getText(doc).contains("Підпис"))
            assertEquals(2, probeStrokes(doc.getPage(0)).size)
        }
    }

    @Test
    fun formUpdatesOnEncryptedDocumentWithPassword() {
        val f = tempFolder.newFile("encform.pdf")
        PDDocument.load(formPdf()).use { doc ->
            doc.protect(StandardProtectionPolicy("o", "u", AccessPermission()).also { it.encryptionKeyLength = 128 })
            doc.save(f)
        }
        val dst = out()
        val r = apply(f, dst, emptyList(), listOf(FormFieldUpdate("name", "Secret")), password = "u")
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        PDDocument.load(dst, "u").use { assertEquals("Secret", (it.documentCatalog.acroForm.getField("name") as PDTextField).value) }
        // FormEngine теж приймає пароль
        val fe = PdfBoxFormEngine()
        val fields = runBlocking { fe.extractFormFields(mockContext, uri(dst), "u") }
        assertTrue(fields.any { it.fullyQualifiedName == "name" })
        assertTrue(runCatching { runBlocking { fe.extractFormFields(mockContext, uri(dst)) } }.isFailure)
        val fr = runBlocking { fe.fillFormFields(mockContext, uri(dst), listOf(FormFieldUpdate("name", "Again")), uri(out()), "u") }
        assertTrue(fr is PdfOperationResult.Success)
    }
}
