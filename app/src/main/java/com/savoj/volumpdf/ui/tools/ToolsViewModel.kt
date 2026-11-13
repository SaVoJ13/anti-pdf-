package com.savoj.volumpdf.ui.tools

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savoj.volumpdf.VolumPdfApp
import com.savoj.volumpdf.core.pdf.PageOperationsEngine
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.core.pdf.model.CompressionLevel
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.data.repository.DocumentRepository
import com.savoj.volumpdf.util.PageRangeParser
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OperationUiState {
    data object Idle : OperationUiState
    data class Loading(val currentStep: Int, val totalSteps: Int) : OperationUiState
    data class Success(
        val outputUri: Uri,
        val pagesCount: Int,
        val originalSizeBytes: Long = 0L,
        val newSizeBytes: Long = 0L
    ) : OperationUiState
    data class Error(val message: String) : OperationUiState
}

class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: PageOperationsEngine = PdfBoxPageOperationsEngine()
    private val repository: DocumentRepository = (application as VolumPdfApp).repository

    private val _selectedFiles = MutableStateFlow<List<SelectedFileItem>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFileItem>> = _selectedFiles.asStateFlow()

    private val _uiState = MutableStateFlow<OperationUiState>(OperationUiState.Idle)
    val uiState: StateFlow<OperationUiState> = _uiState.asStateFlow()

    var sourceDocumentPages: Int = 0
        private set

    fun setSelectedFiles(files: List<SelectedFileItem>, context: Context? = null) {
        _selectedFiles.value = files
        if (files.size == 1 && context != null) {
            sourceDocumentPages = SafUtils.queryPageCount(context, files.first().uri)
        } else {
            sourceDocumentPages = 0
        }
    }

    fun addFiles(files: List<SelectedFileItem>) {
        _selectedFiles.value = _selectedFiles.value + files
    }

    fun removeFileAt(position: Int) {
        val current = _selectedFiles.value.toMutableList()
        if (position in current.indices) {
            current.removeAt(position)
            _selectedFiles.value = current
        }
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
        sourceDocumentPages = 0
    }

    fun resetState() {
        _uiState.value = OperationUiState.Idle
    }

    fun executeMerge(context: Context, outputUri: Uri) {
        val sourceUris = _selectedFiles.value.map { it.uri }
        if (sourceUris.size < 2) {
            _uiState.value = OperationUiState.Error("Для злиття потрібно щонайменше 2 файли")
            return
        }

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, sourceUris.size)
            engine.mergePdfs(context, sourceUris, outputUri).collect { result ->
                handleEngineResult(context, result)
            }
        }
    }

    fun executeSplit(context: Context, pagesInput: String, outputUri: Uri) {
        val source = _selectedFiles.value.firstOrNull()
        if (source == null) {
            _uiState.value = OperationUiState.Error("Виберіть документ")
            return
        }

        val pageIndices = try {
            PageRangeParser.parsePageIndices(pagesInput, sourceDocumentPages)
        } catch (e: Exception) {
            _uiState.value = OperationUiState.Error(e.localizedMessage ?: "Помилка введення номерів сторінок")
            return
        }

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, pageIndices.size)
            engine.extractPages(context, source.uri, pageIndices, outputUri).collect { result ->
                handleEngineResult(context, result)
            }
        }
    }

    fun executeRotate(context: Context, degrees: Int, pagesInput: String?, outputUri: Uri) {
        val source = _selectedFiles.value.firstOrNull()
        if (source == null) {
            _uiState.value = OperationUiState.Error("Виберіть документ")
            return
        }

        val pageIndices = if (pagesInput.isNullOrBlank()) {
            val total = if (sourceDocumentPages > 0) sourceDocumentPages else 1
            (0 until total).toList()
        } else {
            try {
                PageRangeParser.parsePageIndices(pagesInput, sourceDocumentPages)
            } catch (e: Exception) {
                _uiState.value = OperationUiState.Error(e.localizedMessage ?: "Помилка номерів сторінок")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, 1)
            val result = engine.rotatePages(context, source.uri, pageIndices, degrees, outputUri)
            handleEngineResult(context, result)
        }
    }

    fun executeDelete(context: Context, pagesInput: String, outputUri: Uri) {
        val source = _selectedFiles.value.firstOrNull()
        if (source == null) {
            _uiState.value = OperationUiState.Error("Виберіть документ")
            return
        }

        val pageIndicesToDelete = try {
            PageRangeParser.parsePageIndices(pagesInput, sourceDocumentPages)
        } catch (e: Exception) {
            _uiState.value = OperationUiState.Error(e.localizedMessage ?: "Помилка номерів сторінок")
            return
        }

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, 1)
            val result = engine.deletePages(context, source.uri, pageIndicesToDelete, outputUri)
            handleEngineResult(context, result)
        }
    }

    fun executeImagesToPdf(context: Context, outputUri: Uri) {
        val imageUris = _selectedFiles.value.map { it.uri }
        if (imageUris.isEmpty()) {
            _uiState.value = OperationUiState.Error("Виберіть щонайменше одне зображення")
            return
        }

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, imageUris.size)
            val result = engine.imagesToPdf(context, imageUris, outputUri)
            handleEngineResult(context, result)
        }
    }

    fun executeWatermark(context: Context, watermarkText: String, pagesInput: String?, outputUri: Uri) {
        val source = _selectedFiles.value.firstOrNull()
        if (source == null) {
            _uiState.value = OperationUiState.Error("Виберіть документ")
            return
        }
        if (watermarkText.isBlank()) {
            _uiState.value = OperationUiState.Error("Введіть текст водяного знака")
            return
        }

        val pageIndices = if (!pagesInput.isNullOrBlank()) {
            try {
                PageRangeParser.parsePageIndices(pagesInput, sourceDocumentPages)
            } catch (e: Exception) {
                _uiState.value = OperationUiState.Error(e.localizedMessage ?: "Помилка введення номерів сторінок")
                return
            }
        } else null

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, 1)
            val result = engine.addWatermark(context, source.uri, watermarkText, outputUri, pageIndices = pageIndices)
            handleEngineResult(context, result)
        }
    }

    fun executePageNumbers(context: Context, formatPattern: String, pagesInput: String?, outputUri: Uri) {
        val source = _selectedFiles.value.firstOrNull()
        if (source == null) {
            _uiState.value = OperationUiState.Error("Виберіть документ")
            return
        }

        val pageIndices = if (!pagesInput.isNullOrBlank()) {
            try {
                PageRangeParser.parsePageIndices(pagesInput, sourceDocumentPages)
            } catch (e: Exception) {
                _uiState.value = OperationUiState.Error(e.localizedMessage ?: "Помилка введення номерів сторінок")
                return
            }
        } else null

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, 1)
            val result = engine.addPageNumbers(context, source.uri, formatPattern, outputUri, pageIndices = pageIndices)
            handleEngineResult(context, result)
        }
    }

    fun executeCompress(context: Context, level: CompressionLevel, outputUri: Uri) {
        val source = _selectedFiles.value.firstOrNull()
        if (source == null) {
            _uiState.value = OperationUiState.Error("Виберіть документ")
            return
        }

        viewModelScope.launch {
            _uiState.value = OperationUiState.Loading(1, 1)
            val result = engine.compressPdf(context, source.uri, level, outputUri)
            handleEngineResult(context, result)
        }
    }

    private suspend fun handleEngineResult(context: Context, result: PdfOperationResult) {
        when (result) {
            is PdfOperationResult.Progress -> {
                _uiState.value = OperationUiState.Loading(result.currentStep, result.totalSteps)
            }
            is PdfOperationResult.Success -> {
                SafUtils.takePersistableUriPermissionSafe(context, result.outputUri)
                repository.recordDocumentOpened(context, result.outputUri, result.pagesCount)
                _uiState.value = OperationUiState.Success(
                    result.outputUri,
                    result.pagesCount,
                    result.originalSizeBytes,
                    result.newSizeBytes
                )
            }
            is PdfOperationResult.Error -> {
                _uiState.value = OperationUiState.Error(result.userMessage)
            }
        }
    }
}
