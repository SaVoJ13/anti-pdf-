package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.CompressionLevel
import com.savoj.volumpdf.core.pdf.model.PageTransform
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import kotlinx.coroutines.flow.Flow

interface PageOperationsEngine {
    // Злиття кількох PDF у новий файл
    fun mergePdfs(context: Context, sourceUris: List<Uri>, outputUri: Uri): Flow<PdfOperationResult>

    // Розділення: експорт обраних сторінок у новий файл
    fun extractPages(
        context: Context,
        sourceUri: Uri,
        pageIndices: List<Int>,
        outputUri: Uri,
        password: String? = null
    ): Flow<PdfOperationResult>

    // Поворот сторінок на заданий кут (90, 180, 270)
    suspend fun rotatePages(
        context: Context,
        sourceUri: Uri,
        pageIndices: List<Int>,
        degrees: Int,
        outputUri: Uri,
        password: String? = null
    ): PdfOperationResult

    // Видалення сторінок
    suspend fun deletePages(
        context: Context,
        sourceUri: Uri,
        pageIndicesToDelete: List<Int>,
        outputUri: Uri,
        password: String? = null
    ): PdfOperationResult

    // Створення PDF із зображень (JPEG/PNG)
    suspend fun imagesToPdf(context: Context, imageUris: List<Uri>, outputUri: Uri): PdfOperationResult

    // Додавання водяного знака на сторінки (ТОП-4)
    suspend fun addWatermark(
        context: Context,
        sourceUri: Uri,
        watermarkText: String,
        outputUri: Uri,
        password: String? = null,
        pageIndices: List<Int>? = null
    ): PdfOperationResult

    // Додавання нумерації сторінок (ТОП-4)
    suspend fun addPageNumbers(
        context: Context,
        sourceUri: Uri,
        formatPattern: String = "Сторінка %d з %d",
        outputUri: Uri,
        password: String? = null,
        pageIndices: List<Int>? = null
    ): PdfOperationResult

    // Офлайн-стиснення та оптимізація розміру PDF (ТОП-5)
    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        level: CompressionLevel = CompressionLevel.MEDIUM,
        outputUri: Uri,
        password: String? = null
    ): PdfOperationResult

    // Візуальний організатор сторінок: перестановка, поворот та видалення (ТОП-3)
    suspend fun organizePages(
        context: Context,
        sourceUri: Uri,
        pageTransforms: List<PageTransform>,
        outputUri: Uri,
        password: String? = null
    ): PdfOperationResult

    // Створення нового порожнього PDF із заданою кількістю сторінок (типово 1 сторінка A4)
    suspend fun createBlankPdf(
        context: Context,
        outputUri: Uri,
        pageCount: Int = 1
    ): PdfOperationResult

    // Додавання порожніх сторінок до наявного документа
    suspend fun appendBlankPages(
        context: Context,
        sourceUri: Uri,
        count: Int = 1,
        outputUri: Uri,
        password: String? = null
    ): PdfOperationResult

    // Додавання файлів (PDF або зображень) до наявного документа
    suspend fun appendFiles(
        context: Context,
        sourceUri: Uri,
        additionalUris: List<Uri>,
        outputUri: Uri,
        password: String? = null
    ): PdfOperationResult
}
