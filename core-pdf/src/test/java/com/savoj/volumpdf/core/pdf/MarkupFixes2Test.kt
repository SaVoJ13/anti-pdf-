package com.savoj.volumpdf.core.pdf

import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
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
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files

/** Етап 1e: нормалізація URI в забороні перезапису зашифрованого (п. 8в) і selectedValue = null (п. 10). */
class MarkupFixes2Test : MarkupTestSupport() {

    private val engine by lazy { newMarkupEngine() }
    private val text = listOf(PageMarkup.Text("t", 0, 40f, 60f, "Hi", 14f, 0xFF000000.toInt()))

    /** file:// URI з довільним (ненормалізованим) шляхом. */
    private fun rawUri(path: String): Uri {
        val u = Mockito.mock(Uri::class.java)
        Mockito.`when`(u.scheme).thenReturn("file")
        Mockito.`when`(u.path).thenReturn(path)
        Mockito.`when`(u.toString()).thenReturn("file://$path")
        return u
    }

    private fun encryptedIn(dir: File, name: String): File {
        dir.mkdirs()
        val f = File(dir, name)
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.protect(StandardProtectionPolicy("own", "usr", AccessPermission()).also { it.encryptionKeyLength = 128 })
            doc.save(f)
        }
        return f
    }

    private fun edit(src: Uri, dst: Uri) =
        runBlocking { engine.applyEdits(mockContext, src, text, emptyList(), dst, "usr") }

    private fun assertRejectedUntouched(src: File, alias: String) {
        val before = src.readBytes()
        val r = edit(uri(src), rawUri(alias))
        assertTrue(r.toString(), r is PdfOperationResult.Error)
        assertTrue(before.contentEquals(src.readBytes()))
    }

    // ---------- п. 8в ----------

    @Test
    fun encryptedOverwriteViaDotSegmentIsErrorAndUntouched() {
        val src = encryptedIn(tempFolder.newFolder("d1"), "a.pdf")
        assertRejectedUntouched(src, "${src.parentFile!!.absolutePath}/./${src.name}")
    }

    @Test
    fun encryptedOverwriteViaDotDotSegmentIsErrorAndUntouched() {
        val src = encryptedIn(tempFolder.newFolder("d2"), "a.pdf")
        File(src.parentFile, "sub").mkdirs()
        assertRejectedUntouched(src, "${src.parentFile!!.absolutePath}/sub/../${src.name}")
    }

    @Test
    fun encryptedOverwriteViaSymlinkIsErrorAndUntouched() {
        val src = encryptedIn(tempFolder.newFolder("d3"), "a.pdf")
        val link = File(src.parentFile, "link.pdf")
        try {
            Files.createSymbolicLink(link.toPath(), src.toPath())
        } catch (e: Exception) { // Windows без прав / файлова система без symlink
            assumeNoException(e)
        }
        assertRejectedUntouched(src, link.absolutePath)
    }

    @Test
    fun sameNameInDifferentDirectoriesIsNotTheSameFile() {
        val src = encryptedIn(tempFolder.newFolder("d4a"), "same.pdf")
        val dst = File(tempFolder.newFolder("d4b"), "same.pdf")
        val before = src.readBytes()
        val r = edit(uri(src), uri(dst))
        assertTrue(r.toString(), r is PdfOperationResult.Success)
        assertTrue(before.contentEquals(src.readBytes()))
        PDDocument.load(dst, "usr").use { assertTrue(it.isEncrypted) }
    }

    @Test
    fun plainSameFileWithDotSegmentStillAllowed() {
        val f = saveNew({ it.addPage(PDPage(PDRectangle.A4)) }, "plain_${System.nanoTime()}.pdf")
        val r = runBlocking {
            engine.applyEdits(mockContext, uri(f), text, emptyList(), rawUri("${f.parentFile!!.absolutePath}/./${f.name}"), null)
        }
        assertTrue(r.toString(), r is PdfOperationResult.Success)
    }

    // ---------- п. 10: selectedValue ----------

    private fun choiceForm(): File = saveNew({ doc ->
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        val acro = PDAcroForm(doc); doc.documentCatalog.acroForm = acro
        val res = PDResources(); res.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
        acro.defaultResources = res; acro.defaultAppearance = "/Helv 12 Tf 0 g"
        var y = 700f
        fun place(f: PDVariableText, name: String) {
            f.partialName = name; f.defaultAppearance = "/Helv 12 Tf 0 g"
            f.widgets[0].rectangle = PDRectangle(50f, y, 200f, 24f); f.widgets[0].page = page; y -= 40f
            page.annotations.add(f.widgets[0]); acro.fields.add(f)
        }
        val opts = listOf("Ukraine", "Польща", "Україна")
        place(PDComboBox(acro).also { it.options = opts }, "cmb")
        place(PDComboBox(acro).also { it.options = opts; it.isEdit = true }, "ecmb")
        place(PDListBox(acro).also { it.options = opts }, "lst")
    }, "choices_${System.nanoTime()}.pdf")

    private fun selected(f: File, name: String): String? {
        val fields = runBlocking { PdfBoxFormEngine().extractFormFields(mockContext, uri(f), null) }
        return (fields.single { it.fullyQualifiedName == name } as FormField.Choice).selectedValue
    }

    private fun fill(src: File, dst: File, vararg u: FormFieldUpdate) {
        val r = runBlocking { PdfBoxFormEngine().fillFormFields(mockContext, uri(src), u.toList(), uri(dst), null) }
        assertTrue(r.toString(), r is PdfOperationResult.Success)
    }

    @Test
    fun selectedValueIsNullForNeverFilledChoices() {
        val f = choiceForm()
        for (n in listOf("cmb", "ecmb", "lst")) assertNull(n, selected(f, n))
    }

    @Test
    fun selectedValueIsNullAfterClearingComboAndList() {
        val filled = tempFolder.newFile("f1.pdf"); val cleared = tempFolder.newFile("f2.pdf")
        fill(choiceForm(), filled, FormFieldUpdate("cmb", "Польща"), FormFieldUpdate("lst", "Україна"))
        assertEquals("Польща", selected(filled, "cmb"))
        assertEquals("Україна", selected(filled, "lst"))
        fill(filled, cleared, FormFieldUpdate("cmb", ""), FormFieldUpdate("lst", ""))
        assertNull(selected(cleared, "cmb"))
        assertNull(selected(cleared, "lst"))
        // поведінка 1c: порожнє значення знімає вибір (/V і /I видалено)
        PDDocument.load(cleared).use { d ->
            for (n in listOf("cmb", "lst")) {
                val o = d.documentCatalog.acroForm.getField(n).cosObject
                assertNull(n, o.getDictionaryObject(COSName.V))
                assertNull(n, o.getDictionaryObject(COSName.I))
            }
        }
    }

    @Test
    fun selectedValueIsNullForEditableComboWithEmptyString() {
        val filled = tempFolder.newFile("e1.pdf"); val cleared = tempFolder.newFile("e2.pdf")
        fill(choiceForm(), filled, FormFieldUpdate("ecmb", "Щось своє"))
        assertEquals("Щось своє", selected(filled, "ecmb"))
        fill(filled, cleared, FormFieldUpdate("ecmb", ""))
        assertNull(selected(cleared, "ecmb"))
        // 1c: порожній /V записується (не видаляється)
        PDDocument.load(cleared).use { d ->
            assertEquals("", d.documentCatalog.acroForm.getField("ecmb").cosObject.getString(COSName.V))
        }
    }
}
