package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult

/**
 * Інтерфейс взаємодії з інтерактивними формами AcroForm (v0.3).
 */
interface FormEngine {
    /**
     * Отримання списку всіх інтерактивних полів документа.
     * Якщо документ не містить AcroForm, повертає порожній список.
     */
    suspend fun extractFormFields(context: Context, sourceUri: Uri): List<FormField>

    /**
     * Запис оновлених значень у поля форми та збереження в новий або той самий URI.
     */
    suspend fun fillFormFields(
        context: Context,
        sourceUri: Uri,
        updates: List<FormFieldUpdate>,
        outputUri: Uri
    ): PdfOperationResult
}
