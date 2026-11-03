package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FreeTextData
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult

/**
 * Інтерфейс для нанесення анотацій, підписів та тексту на PDF-сторінки.
 * Відповідає CONTRACTS.md v0.2.
 */
interface AnnotationEngine {
    /**
     * Запис рукописного малювання (S Pen / палець) на задану сторінку (/Ink анотація ISO 32000-1).
     *
     * @param context Android Context для доступу до ContentResolver
     * @param sourceUri URI вхідного PDF документа
     * @param pageIndex Індекс цільової сторінки (0-indexed)
     * @param strokes Список штрихів малювання
     * @param pageWidthPt Ширина сторінки в пунктах (pt)
     * @param pageHeightPt Висота сторінки в пунктах (pt)
     * @param outputUri URI для збереження зміненого PDF документа
     */
    suspend fun addInkAnnotations(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        strokes: List<InkStroke>,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult

    /**
     * Вставка підпису як штампа/зображення на сторінку.
     *
     * @param context Android Context для доступу до ContentResolver
     * @param sourceUri URI вхідного PDF документа
     * @param pageIndex Індекс цільової сторінки (0-indexed)
     * @param signatureBitmap Bitmap зображення підпису з підтримкою прозорості
     * @param targetRectPt Цільовий прямокутник розміщення підпису на полотні (pt)
     * @param pageWidthPt Ширина сторінки в пунктах (pt)
     * @param pageHeightPt Висота сторінки в пунктах (pt)
     * @param outputUri URI для збереження зміненого PDF документа
     */
    suspend fun addSignatureStamp(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        signatureBitmap: Bitmap,
        targetRectPt: RectF,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult

    /**
     * Додавання тексту поверх документа (/FreeText).
     *
     * @param context Android Context для доступу до ContentResolver
     * @param sourceUri URI вхідного PDF документа
     * @param pageIndex Індекс цільової сторінки (0-indexed)
     * @param textData Параметри тексту (текст, координати, розмір, колір)
     * @param pageWidthPt Ширина сторінки в пунктах (pt)
     * @param pageHeightPt Висота сторінки в пунктах (pt)
     * @param outputUri URI для збереження зміненого PDF документа
     */
    suspend fun addFreeText(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        textData: FreeTextData,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult
}
