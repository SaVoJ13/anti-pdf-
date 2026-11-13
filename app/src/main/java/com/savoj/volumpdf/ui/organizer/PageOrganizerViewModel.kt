package com.savoj.volumpdf.ui.organizer

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savoj.volumpdf.VolumPdfApp
import com.savoj.volumpdf.core.pdf.PageOperationsEngine
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.data.repository.DocumentRepository
import com.savoj.volumpdf.ui.tools.OperationUiState
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque

/**
 * ViewModel для візуального організатора сторінок (ТОП-3).
 * Завантажує сторінки, генерує мініатюри через системний [PdfRenderer],
 * підтримує поворот, перестановку, видалення/скасування та експорт у новий PDF.
 */
class PageOrganizerViewModel @JvmOverloads constructor(
    application: Application,
    private val engine: PageOperationsEngine = PdfBoxPageOperationsEngine(),
    private val repository: DocumentRepository = (application as VolumPdfApp).repository
) : AndroidViewModel(application) {

    private val _items = MutableStateFlow<List<PageOrganizerItem>>(emptyList())
    val items: StateFlow<List<PageOrganizerItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveState = MutableStateFlow<OperationUiState>(OperationUiState.Idle)
    val saveState: StateFlow<OperationUiState> = _saveState.asStateFlow()

    private var initialItems: List<PageOrganizerItem> = emptyList()
    private val undoStack = ArrayDeque<Pair<Int, PageOrganizerItem>>()

    private var pdfRenderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var tempSourceFile: File? = null

    // Кеш мініатюр для швидкого скролу (індекс оригінальної сторінки -> Bitmap)
    private val thumbnailCache = object : LruCache<Int, Bitmap>(60) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Не викликаємо recycle одразу, оскільки bitmap може відображатися
        }
    }

    /**
     * Завантаження документа та ініціалізація списку сторінок.
     */
    fun loadDocument(sourceUri: Uri) {
        if (_items.value.isNotEmpty()) return // вже завантажено
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    initRenderer(sourceUri)
                }
                val pageCount = pdfRenderer?.pageCount ?: 0
                val created = PageOrganizerLogic.createInitialItems(pageCount)
                initialItems = created
                _items.value = created
            } catch (e: Exception) {
                _saveState.value = OperationUiState.Error(e.localizedMessage ?: "Помилка відкриття PDF")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun initRenderer(sourceUri: Uri) {
        val context = getApplication<Application>()
        try {
            val descriptor = context.contentResolver.openFileDescriptor(sourceUri, "r")
            if (descriptor != null) {
                pfd = descriptor
                pdfRenderer = PdfRenderer(descriptor)
                return
            }
        } catch (_: Exception) {}

        // Fallback: копіюємо у тимчасовий файл для гарантії seekable PFD
        val temp = File.createTempFile("organizer_src_", ".pdf", context.cacheDir).apply {
            deleteOnExit()
        }
        tempSourceFile = temp
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(temp).use { output ->
                input.copyTo(output)
            }
        }
        val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd = descriptor
        pdfRenderer = PdfRenderer(descriptor)
    }

    /**
     * Отримання або асинхронний рендеринг мініатюри сторінки.
     */
    fun loadThumbnail(originalPageIndex: Int, callback: (Bitmap?) -> Unit) {
        thumbnailCache.get(originalPageIndex)?.let { cached ->
            callback(cached)
            return
        }

        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                renderPageBitmap(originalPageIndex)
            }
            if (bitmap != null) {
                thumbnailCache.put(originalPageIndex, bitmap)
            }
            callback(bitmap)
        }
    }

    private fun renderPageBitmap(pageIndex: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        return synchronized(renderer) {
            try {
                val page = renderer.openPage(pageIndex)
                val targetWidth = 300
                val targetHeight = (targetWidth * page.height / page.width.coerceAtLeast(1)).coerceIn(100, 600)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    fun rotateItemAt(position: Int) {
        val current = _items.value
        if (position !in current.indices) return
        val item = current[position]
        val rotated = PageOrganizerLogic.rotateItem(item, 90)
        val mutable = current.toMutableList()
        mutable[position] = rotated
        _items.value = mutable
    }

    fun rotateAll(byDegrees: Int = 90) {
        _items.value = PageOrganizerLogic.rotateAll(_items.value, byDegrees)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        _items.value = PageOrganizerLogic.moveItem(_items.value, fromPosition, toPosition)
    }

    fun deleteItemAt(position: Int): PageOrganizerItem? {
        val current = _items.value
        if (position !in current.indices || current.size <= 1) return null
        val item = current[position]
        undoStack.push(position to item)
        _items.value = PageOrganizerLogic.deleteItem(current, position)
        return item
    }

    fun undoDelete(): Boolean {
        if (undoStack.isEmpty()) return false
        val (pos, item) = undoStack.pop()
        _items.value = PageOrganizerLogic.restoreItem(_items.value, pos, item)
        return true
    }

    fun resetToOriginal() {
        undoStack.clear()
        _items.value = initialItems
    }

    fun saveOrganizedPdf(sourceUri: Uri, outputUri: Uri) {
        val transforms = PageOrganizerLogic.toPageTransforms(_items.value)
        if (transforms.isEmpty()) {
            _saveState.value = OperationUiState.Error("Список сторінок порожній")
            return
        }

        _saveState.value = OperationUiState.Loading(1, 1)
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                engine.organizePages(
                    context = context,
                    sourceUri = sourceUri,
                    pageTransforms = transforms,
                    outputUri = outputUri
                )
            }
            when (result) {
                is PdfOperationResult.Success -> {
                    SafUtils.takePersistableUriPermissionSafe(context, result.outputUri)
                    repository.recordDocumentOpened(context, result.outputUri, result.pagesCount)
                    _saveState.value = OperationUiState.Success(
                        outputUri = result.outputUri,
                        pagesCount = result.pagesCount
                    )
                }
                is PdfOperationResult.Error -> {
                    _saveState.value = OperationUiState.Error(result.userMessage)
                }
                is PdfOperationResult.Progress -> {
                    _saveState.value = OperationUiState.Loading(result.currentStep, result.totalSteps)
                }
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = OperationUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        try {
            pfd?.close()
        } catch (_: Exception) {}
        tempSourceFile?.delete()
        thumbnailCache.evictAll()
    }
}
