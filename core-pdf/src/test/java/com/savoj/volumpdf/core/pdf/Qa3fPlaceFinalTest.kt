package com.savoj.volumpdf.core.pdf

import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.MarkKind
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Фінальна верифікація Етапу 3f (`feature/qa-place-final`):
 * 1. Кламп чотирьох країв (лівий, правий, верхній, нижній) на всіх обертаннях (/Rotate 0, 90, 180, 270)
 *    зі зсунутим CropBox [50, 60, 400, 500] та повний цикл збереження (✓, ✗, текст, штрих, підпис).
 * 2. Документ на 120 сторінок (`long120`): розміщення ✓, тексту й підпису на сторінці 110 (pageIndex = 109).
 * 3. Генерація тестових PDF (`build/qa_3f/`) для живої перевірки на Galaxy S24 Ultra.
 */
class Qa3fPlaceFinalTest : MarkupTestSupport() {

    private val engine by lazy { newMarkupEngine() }
    private val red = 0xFFD32F2F.toInt()
    private val blue = 0xFF1976D2.toInt()
    private val green = 0xFF388E3C.toInt()
    private val tol = 0.5f

    @Test
    fun `four edges clamping and signature across Rotate 0 90 180 270 with shifted CropBox`() {
        val media = PDRectangle.LETTER
        val crop = PDRectangle(50f, 60f, 350f, 440f) // llx=50, lly=60, w=350, h=440

        for (rot in listOf(0, 90, 180, 270)) {
            val visW = if (rot == 90 || rot == 270) crop.height else crop.width
            val visH = if (rot == 90 || rot == 270) crop.width else crop.height

            val src = saveNew({ doc ->
                val page = PDPage(media)
                page.cropBox = crop
                page.cosObject.setInt(COSName.ROTATE, rot)
                doc.addPage(page)
            }, "edge_rot_$rot.pdf")
            val dst = tempFolder.newFile("edge_rot_${rot}_out.pdf")

            // 4 кути / межі видимої області сторінки (з відступом 2 pt для правого/нижнього краю):
            val tlCheck = PageMarkup.Mark("tl", 0, 0f, 0f, MarkKind.CHECK, 16f, red)
            val trCross = PageMarkup.Mark("tr", 0, visW - 2f, 0f, MarkKind.CROSS, 16f, blue)
            val blText = PageMarkup.Text("bl", 0, 0f, visH - 20f, "Edge-BL Привіт", 14f, green)
            val brInk = PageMarkup.Ink(
                "br_ink", 0,
                listOf(InkStroke(listOf(pt(visW - 30f, visH - 2f), pt(visW - 2f, visH - 2f)), red, 2f))
            )
            val sig = PageMarkup.Signature("sig", 0, mockBitmap(), visW - 90f, visH - 40f, 80f, 32f)

            val res = runBlocking {
                engine.applyEdits(
                    mockContext,
                    uri(src),
                    listOf(tlCheck, trCross, blText, brInk, sig),
                    emptyList(),
                    uri(dst)
                )
            }
            assertTrue("rot=$rot failed: $res", res is PdfOperationResult.Success)

            PDDocument.load(dst).use { doc ->
                val page = doc.getPage(0)
                val strokes = probeStrokes(page)
                // Перевіряємо: 1 штрих ✓ + 2 штрихи ✗ + 1 штрих Ink = 4 векторні штрихи
                assertEquals("rot=$rot stroke count", 4, strokes.size)

                // Перевіряємо опорну точку штриха на нижньому правому краї (visW - 2, visH - 2)
                val expectedEnd = expectedUser(rot, crop, visW - 2f, visH - 2f)
                val inkVerts = strokes.last().vertices
                val lastVert = inkVerts.last()
                assertTrue(
                    "rot=$rot ink end mismatch: expected=$expectedEnd actual=$lastVert",
                    abs(expectedEnd.first - lastVert.first) <= tol && abs(expectedEnd.second - lastVert.second) <= tol
                )

                // Перевіряємо наявність тексту "Edge-BL Привіт"
                val extractedText = PDFTextStripper().getText(doc).trim()
                assertTrue("rot=$rot text missing: $extractedText", extractedText.contains("Edge-BL Привіт"))

                // Перевіряємо наявність XObject зображення підпису в ресурсах сторінки
                val xObjects = page.resources.xObjectNames.toList()
                assertTrue("rot=$rot signature XObject missing", xObjects.isNotEmpty())
            }
        }
    }

    @Test
    fun `long 120 page document saves check text and signature on page 110 without touching other pages`() {
        val src = saveNew({ doc ->
            repeat(120) {
                doc.addPage(PDPage(PDRectangle.A4))
            }
        }, "long120.pdf")
        val dst = tempFolder.newFile("long120_out.pdf")

        val markups = listOf(
            PageMarkup.Mark("m110", 109, 120f, 200f, MarkKind.CHECK, 18f, red),
            PageMarkup.Text("t110", 109, 120f, 240f, "Far110 Перевірено", 14f, green),
            PageMarkup.Signature("s110", 109, mockBitmap(), 120f, 280f, 120f, 48f)
        )

        val res = runBlocking {
            engine.applyEdits(mockContext, uri(src), markups, emptyList(), uri(dst))
        }
        assertTrue("long120 failed: $res", res is PdfOperationResult.Success)

        PDDocument.load(dst).use { doc ->
            assertEquals(120, doc.numberOfPages)
            assertEquals(0, probeStrokes(doc.getPage(0)).size)
            assertEquals(0, probeStrokes(doc.getPage(108)).size)
            assertEquals(1, probeStrokes(doc.getPage(109)).size)
            assertEquals(0, probeStrokes(doc.getPage(110)).size)
            assertTrue(doc.getPage(109).resources.xObjectNames.toList().isNotEmpty())

            val stripper = PDFTextStripper().apply {
                startPage = 110
                endPage = 110
            }
            assertTrue(stripper.getText(doc).contains("Far110 Перевірено"))
        }
    }

    @Test
    fun `generate device test PDFs for 3f live verification`() {
        val outDir = File("build/qa_3f").apply { mkdirs() }

        // 1. qa_3f_owner.pdf: 3 сторінки, GoTo-посилання на стор. 3, поле форми, сторінка 2 з /Rotate 90 + CropBox
        PDDocument().use { doc ->
            val p1 = PDPage(PDRectangle.A4)
            val p2 = PDPage(PDRectangle.A4).apply {
                cropBox = PDRectangle(40f, 50f, 500f, 700f)
                cosObject.setInt(COSName.ROTATE, 90)
            }
            val p3 = PDPage(PDRectangle.A4)
            doc.addPage(p1)
            doc.addPage(p2)
            doc.addPage(p3)

            PDPageContentStream(doc, p1).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                cs.newLineAtOffset(50f, 770f)
                cs.showText("Anti PDF - Test 3f (Page 1 of 3)")
                cs.endText()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 13f)
                cs.newLineAtOffset(50f, 735f)
                cs.showText("1. S Pen / Draw / Signature in Place mode")
                cs.newLineAtOffset(0f, -22f)
                cs.showText("2. Pinch zoom with two fingers & 2nd finger while dragging a mark")
                cs.newLineAtOffset(0f, -22f)
                cs.showText("3. Type unsupported symbol/emoji in Text dialog")
                cs.endText()

                // Рамка GoTo-посилання [50, 580, 300, 620] (у PDF-координатах від низу)
                cs.setStrokingColor(0.08f, 0.40f, 0.75f)
                cs.setLineWidth(2f)
                cs.addRect(50f, 580f, 250f, 40f)
                cs.stroke()
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                cs.newLineAtOffset(65f, 595f)
                cs.showText("[GoTo Link -> Jump to Page 3]")
                cs.endText()
            }

            // GoTo Annotation на сторінці 1 -> перехід на сторінку 3
            val link = PDAnnotationLink().apply {
                rectangle = PDRectangle(50f, 580f, 250f, 40f)
                action = PDActionGoTo().apply {
                    destination = PDPageFitDestination().apply { page = p3 }
                }
            }
            p1.annotations.add(link)

            // AcroForm текстове поле на сторінці 1
            val acroForm = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acroForm
            val res = PDResources()
            res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            acroForm.defaultResources = res
            val tf = PDTextField(acroForm).apply {
                partialName = "full_name"
                defaultAppearance = "/Helv 12 Tf 0 g"
            }
            tf.widgets[0].apply {
                rectangle = PDRectangle(50f, 500f, 250f, 30f)
                page = p1
            }
            p1.annotations.add(tf.widgets[0])
            acroForm.fields.add(tf)
            tf.value = "Test Form"

            PDPageContentStream(doc, p3).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                cs.newLineAtOffset(50f, 770f)
                cs.showText("Page 3 (Target of GoTo Link)")
                cs.endText()
            }

            doc.save(File(outDir, "qa_3f_owner.pdf"))
        }

        // 2. qa_3f_nomod.pdf: порожній user-пароль, заборонено змінювати документ і заповнювати форми
        PDDocument().use { doc ->
            val p = PDPage(PDRectangle.A4)
            doc.addPage(p)
            val acroForm = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acroForm
            val res = PDResources()
            res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            acroForm.defaultResources = res
            val tf = PDTextField(acroForm).apply {
                partialName = "readonly_field"
                defaultAppearance = "/Helv 12 Tf 0 g"
            }
            tf.widgets[0].apply {
                rectangle = PDRectangle(50f, 700f, 200f, 30f)
                page = p
            }
            p.annotations.add(tf.widgets[0])
            acroForm.fields.add(tf)
            tf.value = "ReadOnly"

            val ap = AccessPermission().apply {
                setCanModify(false)
                setCanFillInForm(false)
            }
            val policy = StandardProtectionPolicy("owner-secret", "", ap).apply {
                encryptionKeyLength = 128
                isPreferAES = true
            }
            doc.protect(policy)
            doc.save(File(outDir, "qa_3f_nomod.pdf"))
        }

        // 3. qa_3f_user.pdf: захищений user-паролем "usr"
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            val policy = StandardProtectionPolicy("owner-secret", "usr", AccessPermission()).apply {
                encryptionKeyLength = 128
                isPreferAES = true
            }
            doc.protect(policy)
            doc.save(File(outDir, "qa_3f_user.pdf"))
        }

        assertTrue(File(outDir, "qa_3f_owner.pdf").exists())
        assertTrue(File(outDir, "qa_3f_nomod.pdf").exists())
        assertTrue(File(outDir, "qa_3f_user.pdf").exists())
    }
}
