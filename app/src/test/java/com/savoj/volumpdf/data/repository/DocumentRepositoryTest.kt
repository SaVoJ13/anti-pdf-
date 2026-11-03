package com.savoj.volumpdf.data.repository

import androidx.room.InvalidationTracker
import com.savoj.volumpdf.data.db.AppDatabase
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.data.db.RecentDocumentDao
import com.savoj.volumpdf.data.model.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class DocumentRepositoryTest {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: RecentDocumentDao
    private lateinit var mockTracker: InvalidationTracker
    private lateinit var repository: DocumentRepositoryImpl

    private val sampleDocs = listOf(
        RecentDocument(
            "content://pdf/1",
            "Zebra Guide.pdf",
            10,
            5_000_000L,
            1000L,
            0,
            false
        ),
        RecentDocument(
            "content://pdf/2",
            "Alpha Manual.pdf",
            5,
            10_000_000L,
            3000L,
            0,
            true
        ),
        RecentDocument(
            "content://pdf/3",
            "Beta Spec.pdf",
            20,
            1_000_000L,
            2000L,
            0,
            false
        )
    )

    @Before
    fun setup() {
        mockDb = mock(AppDatabase::class.java)
        mockDao = mock(RecentDocumentDao::class.java)
        mockTracker = mock(InvalidationTracker::class.java)

        `when`(mockDb.recentDocumentDao()).thenReturn(mockDao)
        `when`(mockDb.invalidationTracker).thenReturn(mockTracker)

        repository = DocumentRepositoryImpl(mockDb)
    }

    @Test
    fun getRecentDocuments_sortedByDateDesc() = runTest {
        `when`(mockDao.allRecent).thenReturn(sampleDocs)

        val result = repository.getRecentDocuments(sortOrder = SortOrder.DATE_DESC).first()

        assertEquals("content://pdf/2", result[0].uri) // 3000L
        assertEquals("content://pdf/3", result[1].uri) // 2000L
        assertEquals("content://pdf/1", result[2].uri) // 1000L
    }

    @Test
    fun getRecentDocuments_sortedByNameAsc() = runTest {
        `when`(mockDao.allRecent).thenReturn(sampleDocs)

        val result = repository.getRecentDocuments(sortOrder = SortOrder.NAME_ASC).first()

        assertEquals("Alpha Manual.pdf", result[0].title)
        assertEquals("Beta Spec.pdf", result[1].title)
        assertEquals("Zebra Guide.pdf", result[2].title)
    }

    @Test
    fun getRecentDocuments_sortedBySizeDesc() = runTest {
        `when`(mockDao.allRecent).thenReturn(sampleDocs)

        val result = repository.getRecentDocuments(sortOrder = SortOrder.SIZE_DESC).first()

        assertEquals(10_000_000L, result[0].fileSizeBytes)
        assertEquals(5_000_000L, result[1].fileSizeBytes)
        assertEquals(1_000_000L, result[2].fileSizeBytes)
    }

    @Test
    fun toggleFavorite_togglesStateInDao() = runTest {
        val doc = sampleDocs[0]
        `when`(mockDao.getByUri("content://pdf/1")).thenReturn(doc)

        val newFav = repository.toggleFavorite("content://pdf/1")

        assertTrue(newFav)
        verify(mockDao).setFavorite("content://pdf/1", true)
    }

    @Test
    fun deleteDocument_callsDaoDelete() = runTest {
        repository.deleteDocument("content://pdf/1")
        verify(mockDao).deleteByUri("content://pdf/1")
    }

    @Test
    fun clearHistory_callsDaoClear() = runTest {
        repository.clearHistory()
        verify(mockDao).clearAll()
    }
}
