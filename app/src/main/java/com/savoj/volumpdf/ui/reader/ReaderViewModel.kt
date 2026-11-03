package com.savoj.volumpdf.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.savoj.volumpdf.VolumPdfApp
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.data.db.copy
import com.savoj.volumpdf.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository: DocumentRepository = (application as VolumPdfApp).repository

    private val _document = MutableStateFlow<RecentDocument?>(null)
    val document: StateFlow<RecentDocument?> = _document.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    fun initDocument(uri: Uri, titleHint: String?) {
        val uriString = uri.toString()
        savedStateHandle[KEY_URI] = uriString

        viewModelScope.launch {
            val doc = repository.recordDocumentOpened(getApplication(), uri)
            _document.value = doc
            _isFavorite.value = doc.isFavorite
        }
    }

    fun toggleFavorite() {
        val currentDoc = _document.value ?: return
        viewModelScope.launch {
            val newFav = repository.toggleFavorite(currentDoc.uri)
            _isFavorite.value = newFav
            _document.value = currentDoc.copy(isFavorite = newFav)
        }
    }

    fun updatePageProgress(page: Int) {
        val currentDoc = _document.value ?: return
        viewModelScope.launch {
            repository.updateReadingProgress(currentDoc.uri, page)
            _document.value = currentDoc.copy(lastPageRead = page)
        }
    }

    fun updatePageCount(totalCount: Int) {
        val currentDoc = _document.value ?: return
        if (currentDoc.pageCount != totalCount && totalCount > 0) {
            viewModelScope.launch {
                val updated = currentDoc.copy(pageCount = totalCount)
                repository.saveDocument(updated)
                _document.value = updated
            }
        }
    }

    companion object {
        private const val KEY_URI = "saved_document_uri"
    }
}
