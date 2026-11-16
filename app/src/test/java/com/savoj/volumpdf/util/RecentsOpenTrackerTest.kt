package com.savoj.volumpdf.util

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.PdfPermissionException
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.data.model.SortOrder
import com.savoj.volumpdf.data.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Зв'язок «запис в Останні -> прапорець нового відкриття -> прибирання після помилки» (дефект QA: прапорець завжди був false,
 * бо запис робився раніше за перевірку). Репозиторій тут справжній за логікою (пам'ять), без Android.
 */
class RecentsOpenTrackerTest {

    private class FakeRepo : DocumentRepository {
        val docs = linkedMapOf<String, RecentDocument>()
        override fun getRecentDocuments(query: String, sortOrder: SortOrder, favoritesOnly: Boolean): Flow<List<RecentDocument>> = emptyFlow()
        override suspend fun getDocument(uri: String): RecentDocument? = docs[uri]
        override suspend fun saveDocument(document: RecentDocument) { docs[document.uri] = document }
        override suspend fun recordDocumentOpened(context: Context, uri: Uri, pageCountOverride: Int): RecentDocument {
            val doc = docs[uri.toString()] ?: RecentDocument(uri.toString(), "f.pdf", pageCountOverride, 0L, 1L, 0, false)
            docs[doc.uri] = doc
            return doc
        }
        override suspend fun toggleFavorite(uri: String): Boolean = false
        override suspend fun updateReadingProgress(uri: String, page: Int) {}
        override suspend fun deleteDocument(uri: String) { docs.remove(uri) }
        override suspend fun clearHistory(keepFavorites: Boolean) { docs.clear() }
    }

    private val context: Context = mock(Context::class.java)
    private fun uri(s: String): Uri = mock(Uri::class.java).also { `when`(it.toString()).thenReturn(s) }

    /** Той самий порядок, що в MainViewModel.onDocumentSelected -> ReaderViewModel.initDocument(openedAsNewHint). */
    private suspend fun selectThenReaderInit(repo: FakeRepo, u: Uri): Boolean {
        val fromMain = RecentsOpenTracker.record(repo, context, u) // екран відкриття: запис ДО читача
        val inReader = RecentsOpenTracker.record(repo, context, u, knownNew = fromMain.openedAsNew) // читач бере прапорець
        return inReader.openedAsNew
    }

    @Test
    fun `new document is flagged as new even though the screen already recorded it`() = runTest {
        val repo = FakeRepo()
        assertTrue(selectThenReaderInit(repo, uri("content://pdf/bad")))
        assertNotNull(repo.docs["content://pdf/bad"])
    }

    @Test
    fun `without the hint the reader would see an existing record (the original defect)`() = runTest {
        val repo = FakeRepo()
        val u = uri("content://pdf/bad")
        RecentsOpenTracker.record(repo, context, u)
        assertFalse(RecentsOpenTracker.record(repo, context, u).openedAsNew)
    }

    @Test
    fun `already known document is not flagged as new`() = runTest {
        val repo = FakeRepo()
        val u = uri("content://pdf/old")
        RecentsOpenTracker.record(repo, context, u)
        assertFalse(selectThenReaderInit(repo, u))
    }

    @Test
    fun `corrupt first open removes the record`() = runTest {
        val repo = FakeRepo()
        val u = uri("content://pdf/bad")
        val asNew = selectThenReaderInit(repo, u)
        assertTrue(RecentsOpenTracker.dropIfFirstOpenFailed(repo, u.toString(), asNew, IOException("Cannot read PDF header")))
        assertNull(repo.docs["content://pdf/bad"])
    }

    @Test
    fun `old record is kept after a failed reopen`() = runTest {
        val repo = FakeRepo()
        val u = uri("content://pdf/old")
        RecentsOpenTracker.record(repo, context, u)
        val asNew = selectThenReaderInit(repo, u)
        assertFalse(RecentsOpenTracker.dropIfFirstOpenFailed(repo, u.toString(), asNew, IOException("Cannot read PDF header")))
        assertNotNull(repo.docs["content://pdf/old"])
    }

    @Test
    fun `read only no permission not found no space and too big keep a new record`() = runTest {
        val causes = listOf(
            SecurityException("has no access to content://x"),
            PdfPermissionException("no permission"),
            FileNotFoundException("/x: ENOENT"),
            IOException("write failed: ENOSPC (No space left on device)"),
            OutOfMemoryError()
        )
        for (cause in causes) {
            val repo = FakeRepo()
            val u = uri("content://pdf/ok")
            val asNew = selectThenReaderInit(repo, u)
            assertFalse(cause.toString(), RecentsOpenTracker.dropIfFirstOpenFailed(repo, u.toString(), asNew, cause))
            assertEquals(cause.toString(), 1, repo.docs.size)
        }
    }

    @Test
    fun `retry keeps the new flag so a second failure still removes the record`() = runTest {
        val repo = FakeRepo()
        val u = uri("content://pdf/bad")
        val asNew = selectThenReaderInit(repo, u)
        RecentsOpenTracker.dropIfFirstOpenFailed(repo, u.toString(), asNew, IOException("x"))
        assertNull(repo.docs["content://pdf/bad"])
        // «Спробувати ще»: читач відкривається знову з тим самим прапорцем і знову створює запис
        val retry = RecentsOpenTracker.record(repo, context, u, knownNew = asNew)
        assertTrue(retry.openedAsNew)
        assertNotNull(repo.docs["content://pdf/bad"])
        assertTrue(RecentsOpenTracker.dropIfFirstOpenFailed(repo, u.toString(), retry.openedAsNew, IOException("x")))
        assertNull(repo.docs["content://pdf/bad"])
    }
}
