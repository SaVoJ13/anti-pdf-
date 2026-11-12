package com.savoj.volumpdf.core.pdf

import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Тести оптимізації пам'яті (MemoryUsageSetting), безпечного збереження (saveSafely)
 * та збереження шифрування в PageOperationsEngine (F1, F2, F6, F7).
 */
class PdfBoxPageOpsMemoryAndSecurityTest : MarkupTestSupport() {

    private val engine = PdfBoxPageOperationsEngine()

    private fun createEncrypted(name: String, pages: Int = 3, pw: String = "secret"): File {
        val f = tempFolder.newFile(name)
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage(PDRectangle.A4)) }
            val ap = AccessPermission().apply { setCanModify(true) }
            val policy = StandardProtectionPolicy(pw, pw, ap).apply {
                encryptionKeyLength = 128
                isPreferAES = true
            }
            doc.protect(policy)
            doc.save(f)
        }
        return f
    }

    @Test
    fun rotatePagesPreservesEncryptionAndSavesSafely() = runBlocking {
        val src = createEncrypted("src_rotate.pdf")
        val dst = tempFolder.newFile("dst_rotate.pdf")

        val res = engine.rotatePages(mockContext, uri(src), listOf(0), 90, uri(dst), "secret")
        assertTrue("rotate failed: $res", res is PdfOperationResult.Success)

        // Перевіряємо, що збережений файл лишається зашифрованим тим самим паролем
        assertTrue(runCatching { PDDocument.load(dst) }.isFailure)
        PDDocument.load(dst, "secret").use { doc ->
            assertTrue(doc.isEncrypted)
            assertEquals(90, doc.getPage(0).rotation)
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun deletePagesPreservesEncryptionAndSavesSafely() = runBlocking {
        val src = createEncrypted("src_delete.pdf", pages = 4)
        val dst = tempFolder.newFile("dst_delete.pdf")

        val res = engine.deletePages(mockContext, uri(src), listOf(1), uri(dst), "secret")
        assertTrue("delete failed: $res", res is PdfOperationResult.Success)

        assertTrue(runCatching { PDDocument.load(dst) }.isFailure)
        PDDocument.load(dst, "secret").use { doc ->
            assertTrue(doc.isEncrypted)
            assertEquals(3, doc.numberOfPages)
        }
    }

    @Test
    fun extractPagesPreservesEncryptionOnTargetDocument() = runBlocking {
        val src = createEncrypted("src_extract.pdf", pages = 5)
        val dst = tempFolder.newFile("dst_extract.pdf")

        val results = engine.extractPages(mockContext, uri(src), listOf(0, 2), uri(dst), "secret").toList()
        val last = results.last()
        assertTrue("extract failed: $last", last is PdfOperationResult.Success)

        assertTrue(runCatching { PDDocument.load(dst) }.isFailure)
        PDDocument.load(dst, "secret").use { doc ->
            assertTrue(doc.isEncrypted)
            assertEquals(2, doc.numberOfPages)
        }
    }

    @Test
    fun saveSafelyLeavesNoCorruptedFileOnFailure() = runBlocking {
        val src = tempFolder.newFile("src_fail.pdf")
        src.writeText("Corrupted non-pdf data")
        val dst = tempFolder.newFile("dst_fail.pdf")
        dst.writeText("Original sentinel text")

        val res = engine.rotatePages(mockContext, uri(src), listOf(0), 90, uri(dst), null)
        assertTrue("Expected error on corrupt input: $res", res is PdfOperationResult.Error)

        // dst не обнулено до 0 байт — старий вміст цілий, бо saveSafely пише через .tmp
        assertEquals("Original sentinel text", dst.readText())
    }

    @Test
    fun defaultMemorySettingConfiguresTempDirFromContextCache() {
        val setting = PdfIo.defaultMemorySetting(mockContext)
        // Перевіряємо, що змішаний режим пам'яті налаштовано (setupMixed)
        assertTrue(setting.useMainMemory())
        assertTrue(setting.useTempFile())
        assertEquals(16L * 1024 * 1024, setting.maxMainMemoryBytes)
    }
}
