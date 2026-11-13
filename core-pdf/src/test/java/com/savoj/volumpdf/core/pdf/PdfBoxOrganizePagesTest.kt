package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.PageTransform
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File

/**
 * Тести для операції візуального впорядкування сторінок [PdfBoxPageOperationsEngine.organizePages] (ТОП-3).
 */
class PdfBoxOrganizePagesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var engine: PdfBoxPageOperationsEngine
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        engine = PdfBoxPageOperationsEngine()
        mockContext = Mockito.mock(Context::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
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

    @Test
    fun testReorderPages() = runBlocking {
        // 3 сторінки: переставляємо у порядок [2, 0, 1]
        val srcFile = createTestPdf(3, "source_3pages.pdf")
        val outFile = tempFolder.newFile("reordered.pdf")

        val transforms = listOf(
            PageTransform(sourcePageIndex = 2),
            PageTransform(sourcePageIndex = 0),
            PageTransform(sourcePageIndex = 1)
        )

        val result = engine.organizePages(
            context = mockContext,
            sourceUri = createMockUri(srcFile),
            pageTransforms = transforms,
            outputUri = createMockUri(outFile)
        )

        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(3, success.pagesCount)

        PDDocument.load(outFile).use { doc ->
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun testRotateIndividualPages() = runBlocking {
        // 3 сторінки: сторінка 0 повертається на 90°, сторінка 1 — на 180°, сторінка 2 — без повороту
        val srcFile = createTestPdf(3, "source_rotate.pdf")
        val outFile = tempFolder.newFile("rotated.pdf")

        val transforms = listOf(
            PageTransform(sourcePageIndex = 0, rotationDegrees = 90),
            PageTransform(sourcePageIndex = 1, rotationDegrees = 180),
            PageTransform(sourcePageIndex = 2, rotationDegrees = 0)
        )

        val result = engine.organizePages(
            context = mockContext,
            sourceUri = createMockUri(srcFile),
            pageTransforms = transforms,
            outputUri = createMockUri(outFile)
        )

        assertTrue(result is PdfOperationResult.Success)

        PDDocument.load(outFile).use { doc ->
            assertEquals(3, doc.numberOfPages)
            assertEquals(90, doc.getPage(0).rotation)
            assertEquals(180, doc.getPage(1).rotation)
            assertEquals(0, doc.getPage(2).rotation)
        }
    }

    @Test
    fun testDeleteAndDuplicatePages() = runBlocking {
        // 4 сторінки: вибираємо [0, 0, 3] (дублюємо першу, видаляємо 1 і 2, зберігаємо 3)
        val srcFile = createTestPdf(4, "source_del_dup.pdf")
        val outFile = tempFolder.newFile("organized_del_dup.pdf")

        val transforms = listOf(
            PageTransform(sourcePageIndex = 0),
            PageTransform(sourcePageIndex = 0),
            PageTransform(sourcePageIndex = 3)
        )

        val result = engine.organizePages(
            context = mockContext,
            sourceUri = createMockUri(srcFile),
            pageTransforms = transforms,
            outputUri = createMockUri(outFile)
        )

        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(3, success.pagesCount)

        PDDocument.load(outFile).use { doc ->
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun testValidationErrors() = runBlocking {
        val srcFile = createTestPdf(2, "source_val.pdf")
        val outFile = tempFolder.newFile("val_out.pdf")

        // 1. Порожній список трансформацій
        val emptyResult = engine.organizePages(
            context = mockContext,
            sourceUri = createMockUri(srcFile),
            pageTransforms = emptyList(),
            outputUri = createMockUri(outFile)
        )
        assertTrue(emptyResult is PdfOperationResult.Error)

        // 2. Індекс виходить за межі
        val outOfBoundsResult = engine.organizePages(
            context = mockContext,
            sourceUri = createMockUri(srcFile),
            pageTransforms = listOf(PageTransform(sourcePageIndex = 5)),
            outputUri = createMockUri(outFile)
        )
        assertTrue(outOfBoundsResult is PdfOperationResult.Error)
    }
}
