package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class PdfIoSameTargetTest {

    private val mockContext = Mockito.mock(Context::class.java)

    private fun mockContentUri(auth: String, path: String): Uri {
        val u = Mockito.mock(Uri::class.java)
        Mockito.`when`(u.scheme).thenReturn("content")
        Mockito.`when`(u.authority).thenReturn(auth)
        Mockito.`when`(u.path).thenReturn(path)
        Mockito.`when`(u.toString()).thenReturn("content://$auth$path")
        return u
    }

    private fun mockFileUri(path: String): Uri {
        val u = Mockito.mock(Uri::class.java)
        Mockito.`when`(u.scheme).thenReturn("file")
        Mockito.`when`(u.authority).thenReturn(null)
        Mockito.`when`(u.path).thenReturn(path)
        Mockito.`when`(u.toString()).thenReturn("file://$path")
        return u
    }

    @Test
    fun exactSameUriStringsAreIdentifiedAsSameTarget() {
        val u1 = mockContentUri("com.android.providers.media.documents", "/document/document%3A100")
        val u2 = mockContentUri("com.android.providers.media.documents", "/document/document%3A100")
        assertTrue(PdfIo.isSameTarget(mockContext, u1, u2))
    }

    @Test
    fun treeUriAndDocumentUriForSameDocumentAreIdentifiedAsSameTarget() {
        val auth = "com.android.externalstorage.documents"
        // Tree-based document URI produced by buildDocumentUriUsingTree:
        val treeDocUri = mockContentUri(auth, "/tree/primary%3ADownload/document/primary%3ADownload%2Freport.pdf")
        // Standard document URI produced by buildDocumentUri:
        val docUri = mockContentUri(auth, "/document/primary%3ADownload%2Freport.pdf")

        assertTrue(
            "tree-URI та document-URI з однаковим documentId мають визначатися як один цільовий файл",
            PdfIo.isSameTarget(mockContext, treeDocUri, docUri)
        )
        assertTrue(
            "Симетричність isSameTarget: docUri проти treeDocUri",
            PdfIo.isSameTarget(mockContext, docUri, treeDocUri)
        )
    }

    @Test
    fun differentDocumentIdsUnderSameAuthorityAreNotSameTarget() {
        val auth = "com.android.externalstorage.documents"
        val u1 = mockContentUri(auth, "/tree/primary%3ADownload/document/primary%3ADownload%2Ffile1.pdf")
        val u2 = mockContentUri(auth, "/document/primary%3ADownload%2Ffile2.pdf")

        assertFalse(PdfIo.isSameTarget(mockContext, u1, u2))
    }

    @Test
    fun differentAuthoritiesWithSameDocumentIdAreNotSameTarget() {
        val u1 = mockContentUri("com.android.externalstorage.documents", "/document/raw%3A%2Fdoc.pdf")
        val u2 = mockContentUri("com.android.providers.downloads.documents", "/document/raw%3A%2Fdoc.pdf")

        assertFalse(PdfIo.isSameTarget(mockContext, u1, u2))
    }

    @Test
    fun differentSchemesAreNotSameTarget() {
        val uContent = mockContentUri("com.android.externalstorage.documents", "/document/raw%3A%2Fdoc.pdf")
        val uFile = mockFileUri("/sdcard/doc.pdf")

        assertFalse(PdfIo.isSameTarget(mockContext, uContent, uFile))
    }
}
