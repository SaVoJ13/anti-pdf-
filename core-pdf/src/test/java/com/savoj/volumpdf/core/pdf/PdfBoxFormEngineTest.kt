package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File

class PdfBoxFormEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var engine: PdfBoxFormEngine
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        engine = PdfBoxFormEngine()
        mockContext = Mockito.mock(Context::class.java)
        val mockAssets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.assets).thenReturn(mockAssets)
        Mockito.`when`(mockContext.cacheDir).thenReturn(tempFolder.root)

        Mockito.doAnswer { invocation ->
            val path = invocation.getArgument<String>(0)
            openAssetStreamForTest(path) ?: throw java.io.FileNotFoundException("Asset not found: $path")
        }.`when`(mockAssets).open(Mockito.anyString())

        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(mockContext)
    }

    private fun openAssetStreamForTest(path: String): java.io.InputStream? {
        val streamFromCl = javaClass.classLoader?.getResourceAsStream(path)
            ?: javaClass.classLoader?.getResourceAsStream("assets/$path")
        if (streamFromCl != null) return streamFromCl

        val userHome = System.getProperty("user.home")
        val cacheDirs = listOf(
            File(userHome, ".gradle/caches/modules-2/files-2.1/com.tom-roush/pdfbox-android/2.0.27.0"),
            File(userHome, ".gradle/caches")
        )

        for (dir in cacheDirs) {
            if (!dir.exists()) continue
            val aar = dir.walkTopDown().firstOrNull { it.name.endsWith(".aar") && it.name.contains("pdfbox") }
            if (aar != null && aar.exists()) {
                val zip = java.util.zip.ZipFile(aar)
                val entry = zip.getEntry("assets/$path") ?: zip.getEntry(path)
                if (entry != null) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    zip.close()
                    return java.io.ByteArrayInputStream(bytes)
                }
                zip.close()
            }
        }
        return null
    }

    private fun createMockUri(file: File): Uri {
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.scheme).thenReturn("file")
        Mockito.`when`(uri.path).thenReturn(file.absolutePath)
        Mockito.`when`(uri.toString()).thenReturn("file://${file.absolutePath}")
        return uri
    }

    private fun createPdfWithForm(fileName: String): File {
        val file = tempFolder.newFile(fileName)
        val doc = PDDocument()
        try {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            val acroForm = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acroForm

            val defaultResources = PDResources()
            defaultResources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            acroForm.defaultResources = defaultResources
            acroForm.defaultAppearance = "/Helv 12 Tf 0 g"

            // 1. PDTextField
            val textField = PDTextField(acroForm)
            textField.partialName = "fullName"
            textField.alternateFieldName = "Full Name"
            textField.value = "John Doe"
            textField.widgets[0].rectangle = PDRectangle(50f, 700f, 200f, 30f)
            textField.widgets[0].page = page
            page.annotations.add(textField.widgets[0])
            acroForm.fields.add(textField)

            // 2. PDCheckBox
            val checkBox = PDCheckBox(acroForm)
            checkBox.partialName = "subscribe"
            checkBox.alternateFieldName = "Subscribe to Newsletter"
            val checkWidget = checkBox.widgets[0]
            checkWidget.rectangle = PDRectangle(50f, 650f, 20f, 20f)
            checkWidget.page = page

            val apDict = PDAppearanceDictionary()
            val subDict = COSDictionary()
            subDict.setItem(COSName.getPDFName("Yes"), PDAppearanceStream(doc))
            subDict.setItem(COSName.Off, PDAppearanceStream(doc))
            val normalEntry = PDAppearanceEntry(subDict)
            apDict.normalAppearance = normalEntry
            checkWidget.appearance = apDict

            page.annotations.add(checkWidget)
            acroForm.fields.add(checkBox)
            checkBox.unCheck()

            // 3. PDComboBox
            val comboBox = PDComboBox(acroForm)
            comboBox.partialName = "country"
            comboBox.alternateFieldName = "Country Selection"
            comboBox.options = listOf("Ukraine", "USA", "Germany", "Poland")
            comboBox.setValue("Ukraine")
            comboBox.widgets[0].rectangle = PDRectangle(50f, 600f, 200f, 30f)
            comboBox.widgets[0].page = page
            page.annotations.add(comboBox.widgets[0])
            acroForm.fields.add(comboBox)

            doc.save(file)
        } finally {
            doc.close()
        }
        return file
    }

    private fun createEmptyPdf(fileName: String): File {
        val file = tempFolder.newFile(fileName)
        val doc = PDDocument()
        try {
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(file)
        } finally {
            doc.close()
        }
        return file
    }

    @Test
    fun testExtractFormFieldsFromDocumentWithoutAcroForm() = runBlocking {
        val file = createEmptyPdf("no_form.pdf")
        val uri = createMockUri(file)

        val fields = engine.extractFormFields(mockContext, uri)
        assertTrue("Документ без AcroForm повинен повертати emptyList", fields.isEmpty())
    }

    @Test
    fun testExtractFormFields() = runBlocking {
        val file = createPdfWithForm("form_extract.pdf")
        val uri = createMockUri(file)

        val fields = engine.extractFormFields(mockContext, uri)
        assertEquals("Очікується 3 поля форми", 3, fields.size)

        // 1. Текстове поле
        val textField = fields.find { it.fullyQualifiedName == "fullName" }
        assertNotNull(textField)
        assertTrue(textField is FormField.Text)
        val text = textField as FormField.Text
        assertEquals("fullName", text.partialName)
        assertEquals("Full Name", text.alternateTitle)
        assertEquals("John Doe", text.value)
        assertFalse(text.isMultiline)
        assertFalse(text.isReadOnly)

        // 2. Чекбокс
        val checkField = fields.find { it.fullyQualifiedName == "subscribe" }
        assertNotNull(checkField)
        assertTrue(checkField is FormField.CheckBox)
        val check = checkField as FormField.CheckBox
        assertEquals("subscribe", check.partialName)
        assertEquals("Subscribe to Newsletter", check.alternateTitle)
        assertFalse("Початково чекбокс знятий", check.isChecked)
        assertEquals("Yes", check.onValue)

        // 3. Випадаючий список
        val choiceField = fields.find { it.fullyQualifiedName == "country" }
        assertNotNull(choiceField)
        assertTrue(choiceField is FormField.Choice)
        val choice = choiceField as FormField.Choice
        assertEquals("country", choice.partialName)
        assertEquals("Country Selection", choice.alternateTitle)
        assertEquals(listOf("Ukraine", "USA", "Germany", "Poland"), choice.options)
        assertEquals("Ukraine", choice.selectedValue)
        assertFalse(choice.isEditableCombo)
    }

    @Test
    fun testFillFormFields() = runBlocking {
        val srcFile = createPdfWithForm("form_source.pdf")
        val outFile = tempFolder.newFile("form_filled.pdf")
        val srcUri = createMockUri(srcFile)
        val outUri = createMockUri(outFile)

        val updates = listOf(
            FormFieldUpdate(fullyQualifiedName = "fullName", value = "Jane Smith"),
            FormFieldUpdate(fullyQualifiedName = "subscribe", value = true),
            FormFieldUpdate(fullyQualifiedName = "country", value = "Germany")
        )

        val result = engine.fillFormFields(mockContext, srcUri, updates, outUri)
        assertTrue("Операція повинна бути успішною", result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(1, success.pagesCount)

        // Перевірка через повторний extractFormFields на результуючому файлі
        val reExtracted = engine.extractFormFields(mockContext, outUri)
        assertEquals(3, reExtracted.size)

        val text = reExtracted.find { it.fullyQualifiedName == "fullName" } as FormField.Text
        assertEquals("Jane Smith", text.value)

        val check = reExtracted.find { it.fullyQualifiedName == "subscribe" } as FormField.CheckBox
        assertTrue("Чекбокс повинен бути відмічений", check.isChecked)

        val choice = reExtracted.find { it.fullyQualifiedName == "country" } as FormField.Choice
        assertEquals("Germany", choice.selectedValue)

        // Також перевірка безпосередньо через PDDocument
        PDDocument.load(outFile).use { doc ->
            val acroForm = doc.documentCatalog.acroForm
            assertNotNull(acroForm)
            val pdText = acroForm.getField("fullName") as PDTextField
            assertEquals("Jane Smith", pdText.value)

            val pdCheck = acroForm.getField("subscribe") as PDCheckBox
            assertTrue(pdCheck.isChecked)

            val pdCombo = acroForm.getField("country") as PDComboBox
            assertEquals("Germany", pdCombo.value.firstOrNull())
        }
    }

    @Test
    fun testFillFormFieldsUncheck() = runBlocking {
        val srcFile = createPdfWithForm("form_uncheck_src.pdf")
        val outFile = tempFolder.newFile("form_uncheck_out.pdf")
        val srcUri = createMockUri(srcFile)
        val outUri = createMockUri(outFile)

        // Спочатку відмічаємо
        engine.fillFormFields(
            mockContext,
            srcUri,
            listOf(FormFieldUpdate("subscribe", true)),
            outUri
        )

        val reExtracted1 = engine.extractFormFields(mockContext, outUri)
        val check1 = reExtracted1.find { it.fullyQualifiedName == "subscribe" } as FormField.CheckBox
        assertTrue(check1.isChecked)

        // Тепер знімаємо відмітку
        val outFile2 = tempFolder.newFile("form_uncheck_out2.pdf")
        val outUri2 = createMockUri(outFile2)
        val result = engine.fillFormFields(
            mockContext,
            outUri,
            listOf(FormFieldUpdate("subscribe", false)),
            outUri2
        )
        assertTrue(result is PdfOperationResult.Success)

        val reExtracted2 = engine.extractFormFields(mockContext, outUri2)
        val check2 = reExtracted2.find { it.fullyQualifiedName == "subscribe" } as FormField.CheckBox
        assertFalse("Чекбокс повинен бути знятий після unCheck", check2.isChecked)
    }

    @Test
    fun testFillFormFieldsOnDocumentWithoutAcroFormReturnsError() = runBlocking {
        val srcFile = createEmptyPdf("empty_no_form.pdf")
        val outFile = tempFolder.newFile("empty_out.pdf")
        val srcUri = createMockUri(srcFile)
        val outUri = createMockUri(outFile)

        val updates = listOf(FormFieldUpdate("nonExisting", "Value"))
        val result = engine.fillFormFields(mockContext, srcUri, updates, outUri)

        assertTrue(result is PdfOperationResult.Error)
        val error = result as PdfOperationResult.Error
        assertTrue(error.userMessage.contains("не містить форми"))
    }
}
