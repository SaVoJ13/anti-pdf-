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

    /**
     * Поки переглядач відновлює положення після збереження, він на мить показує сторінку 0;
     * такий стан не можна записувати як «остання прочитана сторінка».
     */
    @Volatile
    var progressSuppressed: Boolean = false

    fun updatePageProgress(page: Int) {
        if (progressSuppressed) return
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

    // --- режим «Поставити»: незбережені об'єкти й вибір інструмента (переживають процес) ----------

    private val _pending = MutableStateFlow(
        PlaceMarkupLogic.deserializeAll(savedStateHandle.get<ArrayList<String>>(KEY_PENDING))
    )
    val pending: StateFlow<List<PendingMarkup>> = _pending.asStateFlow()

    fun addPending(item: PendingMarkup) = setPending(_pending.value + item)

    fun clearPending() = setPending(emptyList())

    private fun setPending(list: List<PendingMarkup>) {
        _pending.value = list
        savedStateHandle[KEY_PENDING] = PlaceMarkupLogic.serializeAll(list)
    }

    var placeModeActive: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_PLACE_MODE) ?: false
        set(value) { savedStateHandle[KEY_PLACE_MODE] = value }

    var placeTool: PlaceTool
        get() = savedStateHandle.get<String>(KEY_TOOL)
            ?.let { name -> PlaceTool.values().firstOrNull { it.name == name } } ?: PlaceTool.CHECK
        set(value) { savedStateHandle[KEY_TOOL] = value.name }

    var placeColor: Int
        get() = savedStateHandle.get<Int>(KEY_COLOR) ?: DEFAULT_PLACE_COLOR
        set(value) { savedStateHandle[KEY_COLOR] = value }

    /** Розмір окремо для ✓/✗ і для тексту. */
    fun placeSize(tool: PlaceTool): Float {
        val key = if (tool == PlaceTool.TEXT) KEY_TEXT_SIZE else KEY_MARK_SIZE
        return PlaceMarkupLogic.clampSize(tool, savedStateHandle.get<Float>(key) ?: PlaceMarkupLogic.defaultSize(tool))
    }

    fun setPlaceSize(tool: PlaceTool, value: Float) {
        val key = if (tool == PlaceTool.TEXT) KEY_TEXT_SIZE else KEY_MARK_SIZE
        savedStateHandle[key] = PlaceMarkupLogic.clampSize(tool, value)
    }

    companion object {
        private const val KEY_URI = "saved_document_uri"
        private const val KEY_PENDING = "place_pending"
        private const val KEY_PLACE_MODE = "place_mode"
        private const val KEY_TOOL = "place_tool"
        private const val KEY_COLOR = "place_color"
        private const val KEY_TEXT_SIZE = "place_text_size"
        private const val KEY_MARK_SIZE = "place_mark_size"
        const val DEFAULT_PLACE_COLOR = 0xFFD32F2F.toInt()
    }
}
