package com.savoj.volumpdf.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentDocumentTest {

    @Test
    fun recentDocument_creationAndGetters() {
        val doc = RecentDocument(
            "content://test/doc.pdf",
            "Doc.pdf",
            12,
            1024L,
            5000L,
            3,
            true
        )

        assertEquals("content://test/doc.pdf", doc.uri)
        assertEquals("Doc.pdf", doc.title)
        assertEquals(12, doc.pageCount)
        assertEquals(1024L, doc.fileSizeBytes)
        assertEquals(5000L, doc.lastOpenedTimestamp)
        assertEquals(3, doc.lastPageRead)
        assertTrue(doc.isFavorite)
    }

    @Test
    fun recentDocument_copyExtension() {
        val original = RecentDocument(
            "content://test/doc.pdf",
            "Doc.pdf",
            12,
            1024L,
            5000L,
            3,
            false
        )

        val updated = original.copy(isFavorite = true, lastPageRead = 5)

        assertEquals(original.uri, updated.uri)
        assertEquals(original.title, updated.title)
        assertTrue(updated.isFavorite)
        assertEquals(5, updated.lastPageRead)
        assertFalse(original.isFavorite)
    }
}
