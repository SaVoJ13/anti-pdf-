package com.savoj.volumpdf.ui.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Тести для [DocumentScannerLogic] (ТОП-1).
 */
class DocumentScannerLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testGenerateScannedFileName() {
        val timestamp = 1700000000000L
        val name = DocumentScannerLogic.generateScannedFileName(timestamp)
        assertTrue(name.startsWith("Scan_"))
        assertTrue(name.endsWith(".pdf"))
        assertEquals(24, name.length) // Scan_yyyyMMdd_HHmmss.pdf
    }

    @Test
    fun testBuildScannerOptions() {
        val options = DocumentScannerLogic.buildScannerOptions(pageLimit = 50)
        assertNotNull(options)
    }

    @Test
    fun testPersistScannedPdf() {
        val mockContext = Mockito.mock(Context::class.java)
        val mockResolver = Mockito.mock(ContentResolver::class.java)
        val filesDir = tempFolder.newFolder("files")
        Mockito.`when`(mockContext.filesDir).thenReturn(filesDir)
        Mockito.`when`(mockContext.contentResolver).thenReturn(mockResolver)

        val sampleData = "PDF_SCANNED_TEST_DATA".toByteArray(Charsets.UTF_8)
        val mockUri = Mockito.mock(Uri::class.java)
        Mockito.`when`(mockResolver.openInputStream(mockUri)).thenReturn(ByteArrayInputStream(sampleData))

        val savedFile = DocumentScannerLogic.persistScannedPdf(mockContext, mockUri, "TestScan.pdf")
        assertTrue(savedFile.exists())
        assertEquals("TestScan.pdf", savedFile.name)
        assertEquals(sampleData.size.toLong(), savedFile.length())
    }
}
