package com.savoj.volumpdf.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.InvalidationTracker
import com.savoj.volumpdf.data.db.AppDatabase
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.data.db.RecentDocumentDao
import com.savoj.volumpdf.data.model.SortOrder
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface DocumentRepository {
    fun getRecentDocuments(
        query: String = "",
        sortOrder: SortOrder = SortOrder.DATE_DESC,
        favoritesOnly: Boolean = false
    ): Flow<List<RecentDocument>>

    suspend fun getDocument(uri: String): RecentDocument?

    suspend fun saveDocument(document: RecentDocument)

    suspend fun recordDocumentOpened(context: Context, uri: Uri, pageCountOverride: Int = 0): RecentDocument

    suspend fun toggleFavorite(uri: String): Boolean

    suspend fun updateReadingProgress(uri: String, page: Int)

    suspend fun deleteDocument(uri: String)

    suspend fun clearHistory()
}

class DocumentRepositoryImpl(
    private val appDatabase: AppDatabase
) : DocumentRepository {

    private val recentDocumentDao: RecentDocumentDao = appDatabase.recentDocumentDao()

    private val dbUpdates: Flow<Long> = callbackFlow {
        val observer = object : InvalidationTracker.Observer(arrayOf("recent_documents")) {
            override fun onInvalidated(tables: Set<String>) {
                trySend(System.currentTimeMillis())
            }
        }
        try {
            appDatabase.invalidationTracker.addObserver(observer)
        } catch (_: Exception) {
            // Mock environment in tests
        }
        trySend(System.currentTimeMillis())
        awaitClose {
            try {
                appDatabase.invalidationTracker.removeObserver(observer)
            } catch (_: Exception) {}
        }
    }

    override fun getRecentDocuments(
        query: String,
        sortOrder: SortOrder,
        favoritesOnly: Boolean
    ): Flow<List<RecentDocument>> = dbUpdates.map {
        withContext(Dispatchers.IO) {
            val list = when {
                favoritesOnly && query.isBlank() -> recentDocumentDao.favorites
                favoritesOnly && query.isNotBlank() -> recentDocumentDao.searchFavorites(query.trim())
                !favoritesOnly && query.isNotBlank() -> recentDocumentDao.searchDocuments(query.trim())
                else -> recentDocumentDao.allRecent
            }
            when (sortOrder) {
                SortOrder.DATE_DESC -> list.sortedByDescending { it.lastOpenedTimestamp }
                SortOrder.NAME_ASC -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                SortOrder.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getDocument(uri: String): RecentDocument? = withContext(Dispatchers.IO) {
        recentDocumentDao.getByUri(uri)
    }

    override suspend fun saveDocument(document: RecentDocument) = withContext(Dispatchers.IO) {
        recentDocumentDao.upsert(document)
    }

    override suspend fun recordDocumentOpened(
        context: Context,
        uri: Uri,
        pageCountOverride: Int
    ): RecentDocument = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        val existing = recentDocumentDao.getByUri(uriString)

        val title = existing?.title?.takeIf { it.isNotBlank() }
            ?: SafUtils.queryDisplayName(context, uri)

        val size = if (existing != null && existing.fileSizeBytes > 0) {
            existing.fileSizeBytes
        } else {
            SafUtils.queryFileSize(context, uri)
        }

        val pages = when {
            pageCountOverride > 0 -> pageCountOverride
            existing != null && existing.pageCount > 0 -> existing.pageCount
            else -> SafUtils.queryPageCount(context, uri)
        }

        val doc = RecentDocument(
            uriString,
            title,
            pages,
            size,
            System.currentTimeMillis(),
            existing?.lastPageRead ?: 0,
            existing?.isFavorite ?: false
        )

        recentDocumentDao.upsert(doc)
        doc
    }

    override suspend fun toggleFavorite(uri: String): Boolean = withContext(Dispatchers.IO) {
        val existing = recentDocumentDao.getByUri(uri) ?: return@withContext false
        val newFavorite = !existing.isFavorite
        recentDocumentDao.setFavorite(uri, newFavorite)
        newFavorite
    }

    override suspend fun updateReadingProgress(uri: String, page: Int) = withContext(Dispatchers.IO) {
        recentDocumentDao.updateReadingProgress(
            uri,
            page,
            System.currentTimeMillis()
        )
    }

    override suspend fun deleteDocument(uri: String) = withContext(Dispatchers.IO) {
        recentDocumentDao.deleteByUri(uri)
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        recentDocumentDao.clearAll()
    }
}
