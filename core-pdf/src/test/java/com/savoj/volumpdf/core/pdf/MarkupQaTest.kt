package com.savoj.volumpdf.core.pdf

import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.PdfPermissionException
import com.savoj.volumpdf.core.pdf.model.MarkKind
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * QA етапу 1b: шифрування (RC4/AES-128/AES-256, обмежені права), крайні випадки, Choice з кирилицею.
 * Дефекти QA 1b виправлено в етапі 1c (політика R15, Choice, розміри).
 */
class MarkupQaTest : MarkupTestSupport() {

    private val engine by lazy { newMarkupEngine() }
    private val blue = 0xFF0000FF.toInt()

    private fun run(src: File, dst: File, m: List<PageMarkup>, f: List<FormFieldUpdate> = emptyList(), pw: String? = null) =
        runBlocking { engine.applyEdits(mockContext, uri(src), m, f, uri(dst), pw) }

    private fun encrypted(name: String, keyLen: Int, aes: Boolean, ap: AccessPermission = AccessPermission()): File {
        val f = tempFolder.newFile(name)
        PDDocument().use { d ->
            d.addPage(PDPage(PDRectangle.A4))
            val p = StandardProtectionPolicy("own", "usr", ap); p.encryptionKeyLength = keyLen; p.isPreferAES = aes
            d.protect(p); d.save(f)
        }
        return f
    }

    private fun cfm(d: PDDocument): String? =
        ((d.encryption.cosObject.getDictionaryObject(COSName.getPDFName("CF")) as? COSDictionary)
            ?.getDictionaryObject(COSName.getPDFName("StdCF")) as? COSDictionary)?.getNameAsString("CFM")

    private val text = listOf(PageMarkup.Text("t", 0, 40f, 60f, "Секрет Secret", 14f, blue))

    @Test
    fun aes256KeepsKeyLengthAndCipherAndNeedsPassword() {
        val src = encrypted("a256.pdf", 256, true)
        val dst = tempFolder.newFile("a256_out.pdf")
        assertTrue(run(src, dst, text, pw = "usr") is PdfOperationResult.Success)
        assertTrue(runCatching { PDDocument.load(dst) }.isFailure)
        assertTrue(runCatching { PDDocument.load(dst, "wrong") }.isFailure)
        PDDocument.load(dst, "usr").use { d ->
            assertEquals(5, d.encryption.version); assertEquals(6, d.encryption.revision)
            assertEquals(256, d.encryption.length); assertEquals("AESV3", cfm(d))
        }
    }

    @Test
    fun aes128StaysAes128AndRc4_128IsUpgradedToAes128() {
        val a = tempFolder.newFile("a128_out.pdf"); val r = tempFolder.newFile("r128_out.pdf")
        assertTrue(run(encrypted("a128.pdf", 128, true), a, text, pw = "usr") is PdfOperationResult.Success)
        assertTrue(run(encrypted("r128.pdf", 128, false), r, text, pw = "usr") is PdfOperationResult.Success)
        PDDocument.load(a, "usr").use { assertEquals("AESV2", cfm(it)); assertEquals(128, it.encryption.length) }
        // зафіксовано: RC4-128 (V2/R3) стає AES-128 (V4/R4)
        PDDocument.load(r, "usr").use { assertEquals("AESV2", cfm(it)); assertEquals(4, it.encryption.version) }
    }

    // Політика R15: обмеження прав НЕ переносяться; файл лише з обмеженням друку редагується, копія має повні права
    @Test
    fun printOnlyRestrictionIsNotCarriedOver() {
        val ap = AccessPermission().also { it.setCanPrint(false); it.setCanExtractContent(false) }
        val src = encrypted("pr.pdf", 256, true, ap); val dst = tempFolder.newFile("pr_out.pdf")
        assertTrue(run(src, dst, text, pw = "usr") is PdfOperationResult.Success)
        PDDocument.load(dst, "usr").use { assertTrue(it.currentAccessPermission.canPrint()) }
    }

    @Test
    fun modifyForbiddenGivesPermissionErrorAndKeepsOutput() {
        val ap = AccessPermission().also { it.setCanModify(false) }
        val src = encrypted("res.pdf", 256, true, ap); val dst = tempFolder.newFile("res_out.pdf"); dst.writeText("SENTINEL")
        val before = src.readBytes()
        val r = run(src, dst, text, pw = "usr")
        assertTrue(r.toString(), r is PdfOperationResult.Error)
        val cause = (r as PdfOperationResult.Error).cause
        assertTrue(cause is PdfPermissionException); assertEquals("MODIFY", (cause as PdfPermissionException).deniedAction)
        assertEquals("SENTINEL", dst.readText()); assertTrue(before.contentEquals(src.readBytes()))
        val e = runBlocking { engine.checkEditability(mockContext, uri(src), "usr") }!!
        assertTrue(e.isEncrypted); assertFalse(e.canMarkup)
    }

    @Test
    fun openedWithOwnerPasswordAllRightsGranted() {
        val ap = AccessPermission().also { it.setCanModify(false); it.setCanFillInForm(false); it.setCanModifyAnnotations(false) }
        val src = encrypted("own.pdf", 256, true, ap); val dst = tempFolder.newFile("own_out.pdf")
        val e = runBlocking { engine.checkEditability(mockContext, uri(src), "own") }!!
        assertTrue(e.canMarkup); assertTrue(e.canFillForms)
        assertTrue(run(src, dst, text, pw = "own") is PdfOperationResult.Success)
        // копія відкривається паролем, яким відкрито джерело (власника); старий user-пароль не діє
        PDDocument.load(dst, "own").close()
        assertTrue(runCatching { PDDocument.load(dst, "usr") }.isFailure)
    }

    @Test
    fun userPasswordFullRightsSavesCopyOpenableWithSourcePassword() {
        val src = encrypted("full.pdf", 256, true); val dst = tempFolder.newFile("full_out.pdf")
        assertTrue(run(src, dst, text, pw = "usr") is PdfOperationResult.Success)
        PDDocument.load(dst, "usr").close()
        assertTrue(runCatching { PDDocument.load(dst, "own") }.isFailure) // пароль власника не переноситься
    }

    @Test
    fun encryptedSourceSameUriIsErrorAndUntouched() {
        val src = encrypted("same.pdf", 256, true); val before = src.readBytes()
        val r = run(src, src, text, pw = "usr")
        assertTrue(r.toString(), r is PdfOperationResult.Error)
        assertTrue(before.contentEquals(src.readBytes()))
        // незашифрований у той самий URI лишається дозволеним
        val p = plain()
        assertTrue(run(p, p, text) is PdfOperationResult.Success)
    }

    @Test
    fun checkEditabilityNullOnWrongPassword() {
        val src = encrypted("chk.pdf", 256, true)
        assertEquals(null, runBlocking { engine.checkEditability(mockContext, uri(src), "wrong") })
        val e = runBlocking { engine.checkEditability(mockContext, uri(plain()), null) }!!
        assertFalse(e.isEncrypted); assertTrue(e.canMarkup); assertTrue(e.canFillForms)
    }

    @Test
    fun formFillForbiddenGivesPermissionErrorViaFormEngine() {
        val ap = AccessPermission().also { it.setCanFillInForm(false); it.setCanModifyAnnotations(false) }
        val src = tempFolder.newFile("ff.pdf")
        PDDocument().use { d ->
            val p = PDPage(PDRectangle.A4); d.addPage(p)
            val acro = PDAcroForm(d); d.documentCatalog.acroForm = acro
            val res = PDResources(); res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            acro.defaultResources = res; acro.defaultAppearance = "/Helv 12 Tf 0 g"
            val tf = com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField(acro); tf.partialName = "t1"
            tf.widgets[0].rectangle = PDRectangle(50f, 700f, 200f, 24f); tf.widgets[0].page = p
            p.annotations.add(tf.widgets[0]); acro.fields.add(tf)
            val pol = StandardProtectionPolicy("own", "usr", ap); pol.encryptionKeyLength = 256; d.protect(pol); d.save(src)
        }
        val dst = tempFolder.newFile("ff_out.pdf"); dst.writeText("SENTINEL")
        val r = runBlocking { PdfBoxFormEngine().fillFormFields(mockContext, uri(src), listOf(FormFieldUpdate("t1", "x")), uri(dst), "usr") }
        assertTrue(r.toString(), r is PdfOperationResult.Error)
        assertEquals("FILL_FORM", ((r as PdfOperationResult.Error).cause as PdfPermissionException).deniedAction)
        assertEquals("SENTINEL", dst.readText())
    }

    @Test
    fun wrongOrMissingPasswordLeavesOutputUntouched() {
        val src = encrypted("neg.pdf", 256, true); val dst = tempFolder.newFile("neg_out.pdf"); dst.writeText("SENTINEL")
        for (pw in listOf(null, "wrong")) {
            assertTrue(run(src, dst, text, pw = pw) is PdfOperationResult.Error)
            assertEquals("SENTINEL", dst.readText())
        }
    }

    private fun plain(): File = saveNew({ it.addPage(PDPage(PDRectangle.A4)) }, "plain_${System.nanoTime()}.pdf")

    @Test
    fun edgeCasesGiveErrorAndKeepOutputUntouched() {
        val bad = mapOf(
            "check glyph" to listOf(PageMarkup.Text("t", 0, 50f, 50f, "ok ✓", 20f, blue)),
            "emoji" to listOf(PageMarkup.Text("t", 0, 50f, 50f, "hi 😀", 20f, blue)),
            "mark left" to listOf(PageMarkup.Mark("m", 0, -500f, 100f, MarkKind.CHECK, 20f, blue)),
            "mark below" to listOf(PageMarkup.Mark("m", 0, 100f, 5000f, MarkKind.CROSS, 20f, blue)),
            "NaN" to listOf(PageMarkup.Mark("m", 0, Float.NaN, 100f, MarkKind.CHECK, 20f, blue)),
            "page 5" to listOf(PageMarkup.Mark("m", 5, 100f, 100f, MarkKind.CHECK, 20f, blue)),
            "good then off-page" to listOf(
                PageMarkup.Mark("a", 0, 100f, 100f, MarkKind.CHECK, 20f, blue),
                PageMarkup.Mark("b", 0, -500f, 100f, MarkKind.CROSS, 20f, blue)
            )
        )
        for ((label, m) in bad) {
            val src = plain(); val dst = tempFolder.newFile("e_${System.nanoTime()}.pdf"); dst.writeText("SENTINEL")
            val before = src.readBytes()
            assertTrue(label, run(src, dst, m) is PdfOperationResult.Error)
            assertEquals(label, "SENTINEL", dst.readText()); assertTrue(label, before.contentEquals(src.readBytes()))
        }
    }

    @Test
    fun emptyOrWhitespaceTextAndPartialOverhangSucceed() {
        for (t in listOf("", "\n", "   ")) {
            val dst = tempFolder.newFile("w_${System.nanoTime()}.pdf")
            assertTrue(t, run(plain(), dst, listOf(PageMarkup.Text("t", 0, 50f, 50f, t, 20f, blue))) is PdfOperationResult.Success)
        }
        val dst = tempFolder.newFile("o_${System.nanoTime()}.pdf")
        assertTrue(run(plain(), dst, listOf(PageMarkup.Mark("m", 0, 2f, 100f, MarkKind.CHECK, 40f, blue))) is PdfOperationResult.Success)
    }

    // Форма: нередагований combo, редагований combo, список, два текстові поля (одне з неіснуючим шрифтом у DA)
    private fun choiceForm(): File = saveNew({ doc ->
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        val acro = PDAcroForm(doc); doc.documentCatalog.acroForm = acro
        val res = PDResources(); res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
        acro.defaultResources = res; acro.defaultAppearance = "/Helv 12 Tf 0 g"
        var y = 700f
        fun place(f: com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText, name: String) {
            f.partialName = name; f.defaultAppearance = "/Helv 12 Tf 0 g"
            f.widgets[0].rectangle = PDRectangle(50f, y, 200f, 24f); f.widgets[0].page = page; y -= 40f
            page.annotations.add(f.widgets[0]); acro.fields.add(f)
        }
        val opts = listOf("Ukraine", "Польща", "Україна")
        place(PDComboBox(acro).also { it.options = opts }, "cmb")
        place(PDComboBox(acro).also { it.options = opts; it.isEdit = true }, "ecmb")
        place(com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox(acro).also { it.options = opts }, "lst")
        place(com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField(acro), "t1")
        place(com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField(acro), "t2")
    }, "choices_${System.nanoTime()}.pdf")

    private fun fv(f: File, name: String): Pair<String?, Any?> = PDDocument.load(f).use { d ->
        val fld = d.documentCatalog.acroForm.getField(name)
        Pair(fld.cosObject.getString(COSName.V), fld.cosObject.getDictionaryObject(COSName.I))
    }

    @Test
    fun cyrillicChoiceComboAndListSucceedAndStored() {
        val dst = tempFolder.newFile("c1.pdf")
        val r = run(choiceForm(), dst, emptyList(), listOf(FormFieldUpdate("cmb", "Польща"), FormFieldUpdate("lst", "Україна")))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        assertEquals("Польща", fv(dst, "cmb").first)
        assertEquals("Україна", fv(dst, "lst").first)
        assertEquals(2, ((fv(dst, "lst").second) as com.tom_roush.pdfbox.cos.COSArray).getInt(0))
    }

    @Test
    fun choiceOutsideOptionsIsErrorAndAtomic() {
        val src = choiceForm(); val dst = tempFolder.newFile("c2.pdf"); dst.writeText("SENTINEL")
        val r = run(src, dst, emptyList(), listOf(
            FormFieldUpdate("t1", "ok"), FormFieldUpdate("cmb", "Польща"), FormFieldUpdate("lst", "Нема")
        ))
        assertTrue(r.toString(), r is PdfOperationResult.Error)
        assertTrue((r as PdfOperationResult.Error).userMessage.contains("lst"))
        assertEquals("SENTINEL", dst.readText())
        // той самий виклик через FormEngine
        val r2 = runBlocking { PdfBoxFormEngine().fillFormFields(mockContext, uri(src), listOf(FormFieldUpdate("cmb", "Нема")), uri(dst), null) }
        assertTrue(r2 is PdfOperationResult.Error); assertEquals("SENTINEL", dst.readText())
    }

    @Test
    fun editableComboAcceptsAnyValue() {
        val dst = tempFolder.newFile("c3.pdf")
        assertTrue(run(choiceForm(), dst, emptyList(), listOf(FormFieldUpdate("ecmb", "Щось своє"))) is PdfOperationResult.Success)
        assertEquals("Щось своє", fv(dst, "ecmb").first)
    }

    @Test
    fun textCyrillicStillWorksAndOneFieldAppearanceFailureDoesNotCancelRest() {
        val dst = tempFolder.newFile("c4.pdf")
        val r = run(choiceForm(), dst, emptyList(), listOf(
            FormFieldUpdate("t1", "Привіт"), FormFieldUpdate("t2", "Hello"), FormFieldUpdate("cmb", "Ukraine")
        ))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        assertEquals("Привіт", fv(dst, "t1").first); assertEquals("Hello", fv(dst, "t2").first)
        assertEquals("Ukraine", fv(dst, "cmb").first)
        PDDocument.load(dst).use { assertTrue(it.documentCatalog.acroForm.needAppearances) }
    }

    @Test
    fun nonPositiveAndNonFiniteSizesAreErrors() {
        val bad = mapOf(
            "font 0" to PageMarkup.Text("t", 0, 50f, 50f, "x", 0f, blue),
            "font neg" to PageMarkup.Text("t", 0, 50f, 50f, "x", -3f, blue),
            "font NaN" to PageMarkup.Text("t", 0, 50f, 50f, "x", Float.NaN, blue),
            "empty text font 0" to PageMarkup.Text("t", 0, 50f, 50f, "", 0f, blue),
            "mark 0" to PageMarkup.Mark("m", 0, 100f, 100f, MarkKind.CHECK, 0f, blue),
            "mark neg" to PageMarkup.Mark("m", 0, 100f, 100f, MarkKind.CROSS, -1f, blue),
            "mark inf" to PageMarkup.Mark("m", 0, 100f, 100f, MarkKind.CROSS, Float.POSITIVE_INFINITY, blue),
            "ink 0" to PageMarkup.Ink("i", 0, listOf(InkStroke(listOf(pt(10f, 10f), pt(50f, 50f)), blue, 0f))),
            "ink neg" to PageMarkup.Ink("i", 0, listOf(InkStroke(listOf(pt(10f, 10f), pt(50f, 50f)), blue, -2f))),
            "ink NaN" to PageMarkup.Ink("i", 0, listOf(InkStroke(listOf(pt(10f, 10f), pt(50f, 50f)), blue, Float.NaN)))
        )
        for ((label, m) in bad) {
            val dst = tempFolder.newFile("s_${System.nanoTime()}.pdf"); dst.writeText("SENTINEL")
            assertTrue(label, run(plain(), dst, listOf(m)) is PdfOperationResult.Error)
            assertEquals(label, "SENTINEL", dst.readText())
        }
    }
}
