package com.savoj.volumpdf.core.pdf

import com.savoj.volumpdf.core.pdf.model.CompressionLevel
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PdfBoxCompressTest : MarkupTestSupport() {

    private lateinit var engine: PdfBoxPageOperationsEngine

    @Before
    fun setUpEngine() {
        engine = PdfBoxPageOperationsEngine()
    }

    private fun createTestPdf(pageCount: Int, password: String? = null): File {
        val file = tempFolder.newFile("test_compress_${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            for (i in 0 until pageCount) {
                doc.addPage(PDPage(PDRectangle.A4))
            }
            if (password != null) {
                val ap = AccessPermission()
                val policy = StandardProtectionPolicy(password, password, ap).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(policy)
            }
            doc.save(file)
        }
        return file
    }

    @Test
    fun `compressPdf successfully optimizes document and reports size stats`() = runBlocking {
        val srcFile = createTestPdf(3)
        val outFile = tempFolder.newFile("out_compressed.pdf")

        val srcUri = uri(srcFile)
        val outUri = uri(outFile)

        val result = engine.compressPdf(mockContext, srcUri, CompressionLevel.MEDIUM, outUri)
        assertTrue("Expected Success, got $result", result is PdfOperationResult.Success)

        val success = result as PdfOperationResult.Success
        assertEquals(3, success.pagesCount)
        assertTrue("originalSizeBytes should be > 0", success.originalSizeBytes > 0)
        assertTrue("newSizeBytes should be > 0", success.newSizeBytes > 0)

        PDDocument.load(outFile).use { doc ->
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun `compressPdf works with HIGH, MEDIUM and LOW compression levels`() = runBlocking {
        for (level in CompressionLevel.entries) {
            val srcFile = createTestPdf(2)
            val outFile = tempFolder.newFile("out_compressed_${level.name}.pdf")

            val result = engine.compressPdf(mockContext, uri(srcFile), level, uri(outFile))
            assertTrue(result is PdfOperationResult.Success)

            PDDocument.load(outFile).use { doc ->
                assertEquals(2, doc.numberOfPages)
            }
        }
    }

    @Test
    fun `compressPdf preserves encryption password`() = runBlocking {
        val srcFile = createTestPdf(2, password = "MySecurePassword123")
        val outFile = tempFolder.newFile("out_enc_compressed.pdf")

        val result = engine.compressPdf(mockContext, uri(srcFile), CompressionLevel.MEDIUM, uri(outFile), password = "MySecurePassword123")
        assertTrue(result is PdfOperationResult.Success)

        PDDocument.load(outFile, "MySecurePassword123").use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertTrue("Document should still be encrypted", doc.isEncrypted)
        }
    }

    @Test
    fun `CompressionLevel parameters have monotonic tiers`() {
        assertTrue(CompressionLevel.HIGH.maxDimension < CompressionLevel.MEDIUM.maxDimension)
        assertTrue(CompressionLevel.MEDIUM.maxDimension < CompressionLevel.LOW.maxDimension)

        assertTrue(CompressionLevel.HIGH.jpegQuality < CompressionLevel.MEDIUM.jpegQuality)
        assertTrue(CompressionLevel.MEDIUM.jpegQuality < CompressionLevel.LOW.jpegQuality)
    }
}
