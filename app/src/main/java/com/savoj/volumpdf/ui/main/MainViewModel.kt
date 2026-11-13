package com.savoj.volumpdf.ui.main

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savoj.volumpdf.VolumPdfApp
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.data.model.SortOrder
import com.savoj.volumpdf.data.repository.DocumentRepository
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository = (application as VolumPdfApp).repository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val recentDocuments: StateFlow<List<RecentDocument>> = combine(
        _searchQuery,
        _sortOrder,
        _favoritesOnly
    ) { query, sort, favOnly ->
        Triple(query, sort, favOnly)
    }.flatMapLatest { (query, sort, favOnly) ->
        repository.getRecentDocuments(query, sort, favOnly)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun toggleFavoritesFilter(): Boolean {
        val next = !_favoritesOnly.value
        _favoritesOnly.value = next
        return next
    }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch {
            repository.toggleFavorite(uri)
        }
    }

    fun deleteDocument(uri: String) {
        viewModelScope.launch {
            repository.deleteDocument(uri)
        }
    }

    fun clearHistory(keepFavorites: Boolean = false) {
        viewModelScope.launch {
            repository.clearHistory(keepFavorites)
        }
    }

    fun onDocumentSelected(context: Context, uri: Uri, onReady: (RecentDocument) -> Unit) {
        viewModelScope.launch {
            SafUtils.takePersistableUriPermissionSafe(context, uri)
            val doc = repository.recordDocumentOpened(context, uri)
            onReady(doc)
        }
    }
}
