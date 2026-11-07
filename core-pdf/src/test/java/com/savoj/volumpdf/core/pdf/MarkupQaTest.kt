package com.savoj.volumpdf.core.pdf

import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
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
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * QA етапу 1b: шифрування (RC4/AES-128/AES-256, обмежені права), крайні випадки, Choice з кирилицею.
 * Тести з @Ignore фіксують знайдені дефекти (див. docs/project_log.md); їх знімають, коли Архітектор вирішить.
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

    @Test
    fun restrictedPermissionBitsArePreserved() {
        val ap = AccessPermission().also { it.setCanModify(false); it.setCanPrint(false); it.setCanExtractContent(false) }
        val src = encrypted("res.pdf", 256, true, ap); val dst = tempFolder.newFile("res_out.pdf")
        assertTrue(run(src, dst, text, pw = "usr") is PdfOperationResult.Success)
        val before = PDDocument.load(src, "usr").use { it.encryption.permissions }
        val after = PDDocument.load(dst, "usr").use { it.encryption.permissions }
        assertEquals(before, after)
    }

    // ДЕФЕКТ: власник = користувач, тому відкриття користувацьким паролем дає права власника (обмеження неефективні)
    @Ignore("QA 1b: keepEncryption ставить owner=user пароль; відкриття user-паролем дає isOwnerPermission=true")
    @Test
    fun restrictedPdfOpenedWithUserPasswordStaysRestricted() {
        val ap = AccessPermission().also { it.setCanModify(false); it.setCanPrint(false) }
        val src = encrypted("res2.pdf", 256, true, ap); val dst = tempFolder.newFile("res2_out.pdf")
        assertTrue(run(src, dst, text, pw = "usr") is PdfOperationResult.Success)
        PDDocument.load(dst, "usr").use { assertFalse(it.currentAccessPermission.isOwnerPermission) }
    }

    // ДЕФЕКТ: початковий пароль власника втрачено
    @Ignore("QA 1b: пароль власника 'own' після збереження більше не відкриває документ")
    @Test
    fun ownerPasswordStillWorksAfterSave() {
        val src = encrypted("own.pdf", 256, true); val dst = tempFolder.newFile("own_out.pdf")
        assertTrue(run(src, dst, text, pw = "usr") is PdfOperationResult.Success)
        PDDocument.load(dst, "own").close()
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

    private fun comboForm(): File = saveNew({ doc ->
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        val acro = PDAcroForm(doc); doc.documentCatalog.acroForm = acro
        val res = PDResources(); res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
        acro.defaultResources = res; acro.defaultAppearance = "/Helv 12 Tf 0 g"
        val cb = PDComboBox(acro); cb.partialName = "cmb"; cb.options = listOf("Ukraine", "Польща"); cb.defaultAppearance = "/Helv 12 Tf 0 g"
        cb.widgets[0].rectangle = PDRectangle(50f, 700f, 200f, 24f); cb.widgets[0].page = page
        page.annotations.add(cb.widgets[0]); acro.fields.add(cb)
    }, "combo.pdf")

    // ДЕФЕКТ: Cyrillic-значення Choice (DA Helvetica) валить увесь applyEdits, а для Text це терпиться
    @Ignore("QA 1b: Choice з кирилицею → Error 'U+041F is not available in the font Helvetica' (setValueTolerant порівнює valueAsString '[Польща]' з 'Польща')")
    @Test
    fun cyrillicChoiceValueDoesNotFailWholeCall() {
        val dst = tempFolder.newFile("combo_out.pdf")
        val r = run(comboForm(), dst, emptyList(), listOf(FormFieldUpdate("cmb", "Польща")))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
    }
}
