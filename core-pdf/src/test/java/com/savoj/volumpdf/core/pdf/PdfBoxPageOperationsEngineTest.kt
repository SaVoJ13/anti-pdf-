package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.util.Base64

class PdfBoxPageOperationsEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var engine: PdfBoxPageOperationsEngine
    private lateinit var mockContext: Context

    companion object {
        // Дійсний 16x16 JPEG у форматі Base64
        private const val SAMPLE_JPEG_BASE64 =
            "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsK" +
            "CwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQU" +
            "FBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAAQABADASIA" +
            "AhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQA" +
            "AAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3" +
            "ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWm" +
            "pqipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEA" +
            "AwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSEx" +
            "BhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElK" +
            "U1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3" +
            "uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD8qqKK" +
            "KAP/2Q=="
    }

    @Before
    fun setUp() {
        engine = PdfBoxPageOperationsEngine()
        mockContext = Mockito.mock(Context::class.java)
        val mockAssets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.assets).thenReturn(mockAssets)
        PdfBoxPageOperationsEngine.init(mockContext)
    }

    private fun createMockUri(file: File): Uri {
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.scheme).thenReturn("file")
        Mockito.`when`(uri.path).thenReturn(file.absolutePath)
        Mockito.`when`(uri.toString()).thenReturn("file://${file.absolutePath}")
        return uri
    }

    private fun createTestPdf(pageCount: Int, fileName: String): File {
        val file = tempFolder.newFile(fileName)
        PDDocument().use { doc ->
            repeat(pageCount) {
                doc.addPage(PDPage(PDRectangle.A4))
            }
            doc.save(file)
        }
        return file
    }

    private fun createTestJpeg(fileName: String): File {
        val file = tempFolder.newFile(fileName)
        val bytes = Base64.getDecoder().decode(SAMPLE_JPEG_BASE64)
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun testMergePdfs() = runBlocking {
        // Підготовка: 2 PDF файли (2 сторінки + 3 сторінки)
        val pdf1 = createTestPdf(2, "pdf1.pdf")
        val pdf2 = createTestPdf(3, "pdf2.pdf")
        val outputFile = tempFolder.newFile("merged.pdf")

        val uri1 = createMockUri(pdf1)
        val uri2 = createMockUri(pdf2)
        val outputUri = createMockUri(outputFile)

        // Дія: злиття
        val results = engine.mergePdfs(mockContext, listOf(uri1, uri2), outputUri).toList()

        // Перевірка
        val progressEvents = results.filterIsInstance<PdfOperationResult.Progress>()
        assertEquals(2, progressEvents.size)
        assertEquals(1, progressEvents[0].currentStep)
        assertEquals(2, progressEvents[0].totalSteps)
        assertEquals(2, progressEvents[1].currentStep)
        assertEquals(2, progressEvents[1].totalSteps)

        val lastResult = results.last()
        assertTrue("Результат має бути Success, але отримано: $lastResult", lastResult is PdfOperationResult.Success)
        val success = lastResult as PdfOperationResult.Success
        assertEquals(5, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(5, doc.numberOfPages)
        }
    }

    @Test
    fun testExtractPages() = runBlocking {
        // Підготовка: PDF з 4 сторінками, вилучаємо сторінки 0 і 2 (1-шу і 3-тю)
        val pdf = createTestPdf(4, "source_4pages.pdf")
        val outputFile = tempFolder.newFile("extracted.pdf")

        val sourceUri = createMockUri(pdf)
        val outputUri = createMockUri(outputFile)

        // Дія: вилучення сторінок
        val results = engine.extractPages(mockContext, sourceUri, listOf(0, 2), outputUri).toList()

        // Перевірка
        val progressEvents = results.filterIsInstance<PdfOperationResult.Progress>()
        assertEquals(2, progressEvents.size)

        val lastResult = results.last()
        assertTrue("Результат має бути Success, але отримано: $lastResult", lastResult is PdfOperationResult.Success)
        val success = lastResult as PdfOperationResult.Success
        assertEquals(2, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }
    }

    @Test
    fun testRotatePages() = runBlocking {
        // Підготовка: PDF з 2 сторінками (початковий поворот 0)
        val pdf = createTestPdf(2, "rotate_source.pdf")
        val outputFile = tempFolder.newFile("rotated.pdf")

        val sourceUri = createMockUri(pdf)
        val outputUri = createMockUri(outputFile)

        // Дія: поворот сторінки 0 на 90 градусів
        val result = engine.rotatePages(mockContext, sourceUri, listOf(0), 90, outputUri)

        // Перевірка
        assertTrue("Результат має бути Success, але отримано: $result", result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(2, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(90, doc.getPage(0).rotation)
            assertEquals(0, doc.getPage(1).rotation)
        }
    }

    @Test
    fun testDeletePages() = runBlocking {
        // Підготовка: PDF з 3 сторінками
        val pdf = createTestPdf(3, "delete_source.pdf")
        val outputFile = tempFolder.newFile("deleted.pdf")

        val sourceUri = createMockUri(pdf)
        val outputUri = createMockUri(outputFile)

        // Дія: видалення сторінки з індексом 1
        val result = engine.deletePages(mockContext, sourceUri, listOf(1), outputUri)

        // Перевірка
        assertTrue("Результат має бути Success, але отримано: $result", result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(2, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(2, doc.numberOfPages)
        }
    }

    @Test
    fun testImagesToPdf() = runBlocking {
        // Підготовка: створюємо тестове JPEG зображення (16x16)
        val imageFile = createTestJpeg("test_image.jpg")
        val outputFile = tempFolder.newFile("image_result.pdf")

        val imageUri = createMockUri(imageFile)
        val outputUri = createMockUri(outputFile)

        // Дія: конвертація в PDF
        val result = engine.imagesToPdf(mockContext, listOf(imageUri), outputUri)

        // Перевірка
        assertTrue("Результат має бути Success, але отримано: $result", result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(1, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val page = doc.getPage(0)
            assertEquals(16f, page.mediaBox.width, 0.01f)
            assertEquals(16f, page.mediaBox.height, 0.01f)
        }
    }

    @Test
    fun testValidationErrors() = runBlocking {
        val pdf = createTestPdf(2, "validation.pdf")
        val output = tempFolder.newFile("val_out.pdf")
        val uri = createMockUri(pdf)
        val outUri = createMockUri(output)

        // Злиття менше 2 файлів
        val mergeResults = engine.mergePdfs(mockContext, listOf(uri), outUri).toList()
        assertTrue(mergeResults.last() is PdfOperationResult.Error)

        // Вилучення з порожнім списком
        val extractResults = engine.extractPages(mockContext, uri, emptyList(), outUri).toList()
        assertTrue(extractResults.last() is PdfOperationResult.Error)

        // Поворот на некоректний кут
        val rotateResult = engine.rotatePages(mockContext, uri, listOf(0), 45, outUri)
        assertTrue(rotateResult is PdfOperationResult.Error)

        // Спроба видалити всі сторінки
        val deleteResult = engine.deletePages(mockContext, uri, listOf(0, 1), outUri)
        assertTrue(deleteResult is PdfOperationResult.Error)

        // Конвертація порожнього списку зображень
        val imageResult = engine.imagesToPdf(mockContext, emptyList(), outUri)
        assertTrue(imageResult is PdfOperationResult.Error)
    }

    @Test
    fun testCreateBlankPdf() = runBlocking {
        val outputFile = tempFolder.newFile("blank_output.pdf")
        val outputUri = createMockUri(outputFile)

        val result = engine.createBlankPdf(mockContext, outputUri, pageCount = 2)

        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(2, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(PDRectangle.A4.width, doc.getPage(0).mediaBox.width, 0.1f)
            assertEquals(PDRectangle.A4.height, doc.getPage(0).mediaBox.height, 0.1f)
        }
    }

    @Test
    fun testAppendBlankPages() = runBlocking {
        val pdf = createTestPdf(1, "source_1page.pdf")
        val outputFile = tempFolder.newFile("appended_blank.pdf")
        val sourceUri = createMockUri(pdf)
        val outputUri = createMockUri(outputFile)

        val result = engine.appendBlankPages(mockContext, sourceUri, count = 2, outputUri)

        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(3, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun testAppendFiles() = runBlocking {
        val basePdf = createTestPdf(1, "base.pdf")
        val extraPdf = createTestPdf(2, "extra.pdf")
        val extraImg = createTestJpeg("extra.jpg")
        val outputFile = tempFolder.newFile("appended_files.pdf")

        val baseUri = createMockUri(basePdf)
        val extraPdfUri = createMockUri(extraPdf)
        val extraImgUri = createMockUri(extraImg)
        val outputUri = createMockUri(outputFile)

        val result = engine.appendFiles(mockContext, baseUri, listOf(extraPdfUri, extraImgUri), outputUri)

        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        // 1 base + 2 extra pdf + 1 image = 4 pages
        assertEquals(4, success.pagesCount)

        PDDocument.load(outputFile).use { doc ->
            assertEquals(4, doc.numberOfPages)
        }
    }
}
