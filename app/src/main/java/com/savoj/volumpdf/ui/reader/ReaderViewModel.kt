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

    // --- режим «Поставити»: незбережені об'єкти й вибір інструмента ---------------------------------
    // Об'єкти переживають поворот і завершення процесу (SavedStateHandle). Виділення та історія
    // скасування живуть лише в пам'яті й скидаються після успішного збереження.

    private val _editor = MutableStateFlow(
        PlaceEditor.initial(PlaceMarkupLogic.deserializeAll(savedStateHandle.get<ArrayList<String>>(KEY_PENDING)))
    )
    val editor: StateFlow<PlaceEditorState> = _editor.asStateFlow()

    /** Застосовує перехід стану; зберігає список об'єктів, якщо він змінився. */
    fun edit(transform: (PlaceEditorState) -> PlaceEditorState) {
        val old = _editor.value
        val new = transform(old)
        if (new == old) return
        _editor.value = new
        if (new.items != old.items) savedStateHandle[KEY_PENDING] = PlaceMarkupLogic.serializeAll(new.items)
    }

    fun addPending(item: PendingMarkup) = edit { PlaceEditor.add(it, item) }

    /** Очищає все: об'єкти, виділення, історію (після збереження чи відкидання). */
    fun clearPending() = edit { PlaceEditor.reset() }

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

    /** Розмір окремо для ✓/✗, тексту й штриха (товщина, pt). */
    private fun sizeKey(tool: PlaceTool) = when (tool) {
        PlaceTool.TEXT -> KEY_TEXT_SIZE
        PlaceTool.INK -> KEY_INK_WIDTH
        PlaceTool.SIGNATURE -> KEY_SIGNATURE_WIDTH
        else -> KEY_MARK_SIZE
    }

    fun placeSize(tool: PlaceTool): Float =
        PlaceMarkupLogic.clampSize(tool, savedStateHandle.get<Float>(sizeKey(tool)) ?: PlaceMarkupLogic.defaultSize(tool))

    fun setPlaceSize(tool: PlaceTool, value: Float) {
        savedStateHandle[sizeKey(tool)] = PlaceMarkupLogic.clampSize(tool, value)
    }

    /** Пресет пензля (ручка / маркер / хайлайтер): задає альфу нових штрихів; товщина — [placeSize] (INK). */
    var inkPreset: InkPreset
        get() = savedStateHandle.get<String>(KEY_INK_PRESET)
            ?.let { name -> InkPreset.values().firstOrNull { it.name == name } } ?: InkPreset.PEN
        set(value) { savedStateHandle[KEY_INK_PRESET] = value.name }

    /** Колір нових штрихів: RGB з палітри й альфа пресета. */
    fun newInkColor(): Int = ((inkPreset.alpha and 0xFF) shl 24) or (placeColor and 0x00FFFFFF)

    companion object {
        private const val KEY_URI = "saved_document_uri"
        private const val KEY_PENDING = "place_pending"
        private const val KEY_PLACE_MODE = "place_mode"
        private const val KEY_TOOL = "place_tool"
        private const val KEY_COLOR = "place_color"
        private const val KEY_TEXT_SIZE = "place_text_size"
        private const val KEY_MARK_SIZE = "place_mark_size"
        private const val KEY_INK_WIDTH = "place_ink_width"
        private const val KEY_SIGNATURE_WIDTH = "place_signature_width"
        private const val KEY_INK_PRESET = "place_ink_preset"
        const val DEFAULT_PLACE_COLOR = 0xFFD32F2F.toInt()
    }
}
