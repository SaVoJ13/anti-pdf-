package com.savoj.volumpdf.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.savoj.volumpdf.VolumPdfApp
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.data.db.copy
import com.savoj.volumpdf.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
        loadPending(uriString)

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

    /**
     * Пароль документа (етап 3e): ЛИШЕ в пам'яті цього ViewModel. Ніколи не потрапляє в SavedStateHandle, PendingStore,
     * логи, файли чи Bundle; очищається, коли ViewModel знищується (документ закрито); після завершення процесу
     * запитується знову.
     */
    @Volatile
    var documentPassword: String? = null

    /** Переглядач уже відкрив документ (потрібно моделі доступу: null без пароля після відкриття = файл вимагає пароль). */
    @Volatile
    var viewerOpened: Boolean = false

    override fun onCleared() {
        documentPassword = null
        super.onCleared()
    }

    // --- режим «Поставити»: незбережені об'єкти й вибір інструмента ---------------------------------
    // Об'єкти переживають поворот і завершення процесу (SavedStateHandle). Виділення та історія
    // скасування живуть лише в пам'яті й скидаються після успішного збереження.

    // Штрихи й підписи завеликі для Bundle (TransactionTooLarge), тому ВЕСЬ стан незбережених об'єктів лежить у
    // файлі приватного сховища (PendingStore); у SavedStateHandle лишається лише ключ документа й дрібні налаштування.
    private val store = PendingStore(java.io.File(application.filesDir, "pending"))
    private var documentKey: String? = null

    private val _editor = MutableStateFlow(PlaceEditor.initial(emptyList()))
    val editor: StateFlow<PlaceEditorState> = _editor.asStateFlow()

    /**
     * Незбережені значення полів форми (ім'я → String/Boolean): лише відмінні від файла; частина того самого
     * незбереженого стану, що й об'єкти (переживає поворот і am kill), але НЕ входить в історію undo.
     */
    private val _formEdits = MutableStateFlow<Map<String, Any>>(emptyMap())
    val formEdits: StateFlow<Map<String, Any>> = _formEdits.asStateFlow()

    /** Поля форми з файла (extractFormFields); порожній список — немає форми або ще не прочитано. */
    private val _formFields = MutableStateFlow<List<FormField>>(emptyList())
    val formFields: StateFlow<List<FormField>> = _formFields.asStateFlow()

    /** Є що зберігати: об'єкти АБО форми. */
    val hasUnsaved: StateFlow<Boolean> = combine(_editor, _formEdits) { e, f ->
        FormEditing.hasUnsaved(e.items.size, f)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Нові поля з файла; записи, що збіглися з файлом, прибираються. */
    fun setFormFields(fields: List<FormField>) {
        _formFields.value = fields
        if (fields.isNotEmpty()) {
            val pruned = FormEditing.prune(fields, _formEdits.value)
            if (pruned != _formEdits.value) { _formEdits.value = pruned; persistNow() }
        }
    }

    /** Шторка «Застосувати»: [entered] — усі значення зі шторки; у незбережене потрапляє лише відмінне від файла. */
    fun applyFormEntered(entered: Map<String, Any>) {
        val next = FormEditing.applyEntered(_formFields.value, _formEdits.value, entered)
        if (next == _formEdits.value) return
        _formEdits.value = next
        persistNow()
    }

    fun resetFormEdits() {
        if (_formEdits.value.isEmpty()) return
        _formEdits.value = emptyMap()
        persistNow()
    }

    /** Усе незбережене: об'єкти, виділення, історія й форми (після збереження чи «Відкинути»). */
    fun clearAllPending() {
        _formEdits.value = emptyMap()
        clearPending()
        persistNow()
    }

    /** true, коли незбережене з файлу прочитано (або його немає). */
    private val _pendingLoaded = MutableStateFlow(false)
    val pendingLoaded: StateFlow<Boolean> = _pendingLoaded.asStateFlow()

    private fun loadPending(key: String) {
        if (documentKey == key) return
        documentKey = key
        _pendingLoaded.value = false
        viewModelScope.launch {
            var restoredForms: List<FormFieldUpdate> = emptyList()
            val items = withContext(Dispatchers.IO) {
                val state = try {
                    store.loadState(key) { version, shortKey ->
                        android.util.Log.w(TAG, PendingStore.rejectMessage(version, shortKey))
                    }
                } catch (_: Exception) { null }
                val loaded = state?.items ?: emptyList()
                restoredForms = state?.forms ?: emptyList()
                for (p in loaded) {
                    if (p.tool != PlaceTool.SIGNATURE) continue
                    val f = store.imageFile(key, p.id)
                    val bmp = try { android.graphics.BitmapFactory.decodeFile(f.path) } catch (_: Throwable) { null }
                    if (bmp != null) signatureBitmaps[p.id] = bmp
                }
                // Підпис без картинки показати й зберегти не вдасться — такий об'єкт відкидаємо.
                loaded.filter { it.tool != PlaceTool.SIGNATURE || signatureBitmaps.containsKey(it.id) }
            }
            if (_editor.value.items.isEmpty() && items.isNotEmpty()) _editor.value = PlaceEditor.initial(items)
            if (_formEdits.value.isEmpty() && restoredForms.isNotEmpty()) {
                // Типи значень збережено у файлі; відновлені записи без поля у файлі відкинемо в setFormFields.
                _formEdits.value = FormEditing.toMap(restoredForms)
                if (_formFields.value.isNotEmpty()) _formEdits.value = FormEditing.prune(_formFields.value, _formEdits.value)
            }
            _pendingLoaded.value = true
        }
    }

    /** Застосовує перехід стану; зберігає список об'єктів у файл, якщо він змінився. */
    fun edit(transform: (PlaceEditorState) -> PlaceEditorState) {
        val old = _editor.value
        val new = transform(old)
        if (new == old) return
        _editor.value = new
        if (new.items != old.items) persistNow()
    }

    private val persistLock = Any()
    private class Snapshot(val items: List<PendingMarkup>, val forms: List<FormFieldUpdate>)
    private var latestSnapshot: Snapshot? = null
    private var writer: kotlinx.coroutines.Job? = null

    /** Запис у фоні; кілька швидких змін (слайдер, перетягування) зливаються в останній знімок. */
    private fun persistNow() {
        val key = documentKey ?: return
        synchronized(persistLock) {
            latestSnapshot = Snapshot(_editor.value.items, FormEditing.toList(_formEdits.value))
            if (writer?.isActive == true) return
            writer = viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    val snap = synchronized(persistLock) { latestSnapshot.also { latestSnapshot = null } } ?: break
                    try {
                        store.save(key, snap.items, snap.forms) { id, target ->
                            val bmp = signatureBitmaps[id]
                            if (bmp != null) target.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    } catch (_: Exception) {
                        // Диск повний чи інша помилка: незбережене лишається в пам'яті, застосунок не падає.
                    }
                }
            }
        }
    }

    fun addPending(item: PendingMarkup) = edit { PlaceEditor.add(it, item) }

    /** Очищає все: об'єкти, виділення, історію (після збереження чи відкидання). */
    fun clearPending() {
        edit { PlaceEditor.reset() }
        signatureBitmaps.clear()
    }

    /** Картинки підписів поставлених об'єктів за id (лише в пам'яті; на диск — окремо, див. сховище незбереженого). */
    val signatureBitmaps = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

    /** Створений, але ще не поставлений підпис: наступний тап по сторінці ставить його (лише в пам'яті). */
    var armedSignature: android.graphics.Bitmap? = null

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
        private const val TAG = "ReaderViewModel"
        private const val KEY_URI = "saved_document_uri"
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
