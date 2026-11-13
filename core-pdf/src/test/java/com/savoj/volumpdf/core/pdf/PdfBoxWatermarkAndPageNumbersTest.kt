package com.savoj.volumpdf.core.pdf

import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfBoxWatermarkAndPageNumbersTest : MarkupTestSupport() {

    private lateinit var engine: PdfBoxPageOperationsEngine

    @Before
    fun setUpEngine() {
        engine = PdfBoxPageOperationsEngine()
    }

    private fun createTestPdf(pageCount: Int, rotation: Int = 0): File {
        val file = tempFolder.newFile("test_${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            for (i in 0 until pageCount) {
                val page = PDPage(PDRectangle.A4)
                if (rotation != 0) page.rotation = rotation
                doc.addPage(page)
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun `addWatermark creates valid watermarked PDF on all pages`() = runBlocking {
        val srcFile = createTestPdf(3)
        val outFile = tempFolder.newFile("out_watermark.pdf")

        val srcUri = uri(srcFile)
        val outUri = uri(outFile)

        val result = engine.addWatermark(mockContext, srcUri, "КОПІЯ", outUri)
        assertTrue("Result should be Success (was: $result)", result is PdfOperationResult.Success)

        val success = result as PdfOperationResult.Success
        assertEquals(3, success.pagesCount)

        PDDocument.load(outFile).use { doc ->
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun `addWatermark fails on blank watermark text`() = runBlocking {
        val srcFile = createTestPdf(1)
        val outFile = tempFolder.newFile("out_blank.pdf")

        val srcUri = uri(srcFile)
        val outUri = uri(outFile)

        val result = engine.addWatermark(mockContext, srcUri, "   ", outUri)
        assertTrue("Result should be Error for blank text", result is PdfOperationResult.Error)
    }

    @Test
    fun `addPageNumbers numbers pages with format pattern`() = runBlocking {
        val srcFile = createTestPdf(4)
        val outFile = tempFolder.newFile("out_numbered.pdf")

        val srcUri = uri(srcFile)
        val outUri = uri(outFile)

        val result = engine.addPageNumbers(mockContext, srcUri, "Сторінка %d з %d", outUri)
        assertTrue("Result should be Success (was: $result)", result is PdfOperationResult.Success)

        val success = result as PdfOperationResult.Success
        assertEquals(4, success.pagesCount)

        PDDocument.load(outFile).use { doc ->
            assertEquals(4, doc.numberOfPages)
        }
    }

    @Test
    fun `addPageNumbers applies to specific page range`() = runBlocking {
        val srcFile = createTestPdf(5)
        val outFile = tempFolder.newFile("out_range_numbered.pdf")

        val srcUri = uri(srcFile)
        val outUri = uri(outFile)

        val result = engine.addPageNumbers(
            mockContext,
            srcUri,
            "%d / %d",
            outUri,
            pageIndices = listOf(0, 2, 4)
        )
        assertTrue("Result should be Success", result is PdfOperationResult.Success)

        val success = result as PdfOperationResult.Success
        assertEquals(5, success.pagesCount)

        PDDocument.load(outFile).use { doc ->
            assertEquals(5, doc.numberOfPages)
        }
    }

    @Test
    fun `addWatermark preserves page rotation on rotated pages`() = runBlocking {
        val srcFile = createTestPdf(2, rotation = 90)
        val outFile = tempFolder.newFile("out_rotated_watermark.pdf")

        val srcUri = uri(srcFile)
        val outUri = uri(outFile)

        val result = engine.addWatermark(mockContext, srcUri, "CONFIDENTIAL", outUri)
        assertTrue(result is PdfOperationResult.Success)

        PDDocument.load(outFile).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(90, doc.getPage(0).rotation)
            assertEquals(90, doc.getPage(1).rotation)
        }
    }
}
