package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FreeTextData
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Реалізація [AnnotationEngine] на базі бібліотеки PdfBox-Android (Apache-2.0).
 * Забезпечує нанесення векторного рукописного малювання (у потоці вмісту сторінки),
 * вставку штампів/підписів з прозорістю та нанесення текстових блоків.
 *
 * Координати (x, y, targetRectPt, точки штрихів) — у просторі PageViewPoint (CONTRACTS v0.4 §1.5);
 * у простір користувача PDF їх переводить [PageGeometry] (CropBox + /Rotate). Параметри
 * pageWidthPt/pageHeightPt лишаються в сигнатурі (контракт v0.3), але для координат не використовуються.
 */
@Deprecated("Замінено на PdfBoxMarkupEngine (CONTRACTS v0.4, §1.5); видалення після переходу UI на MarkupEngine")
@Suppress("DEPRECATION")
class PdfBoxAnnotationEngine : AnnotationEngine {

    override suspend fun addInkAnnotations(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        strokes: List<InkStroke>,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfBoxPageOperationsEngine.init(context)

            openInputStream(context, sourceUri).use { inStream ->
                val doc = PDDocument.load(inStream)
                try {
                    val totalPages = doc.numberOfPages
                    if (pageIndex !in 0 until totalPages) {
                        throw IndexOutOfBoundsException(
                            "Індекс сторінки $pageIndex виходить за межі документа (0..${totalPages - 1})"
                        )
                    }

                    if (strokes.any { it.points.isNotEmpty() }) {
                        // Ink малюється векторно в потоці вмісту (PdfView не показує /Ink-анотацій, спайк-2)
                        val page = doc.getPage(pageIndex)
                        val geometry = PageGeometry.of(page)
                        MarkupDrawing.newStream(doc, page).use { cs ->
                            MarkupDrawing.drawInk(cs, geometry, strokes)
                        }
                    }

                    saveDocumentSafely(context, doc, outputUri)
                    PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
                } finally {
                    doc.close()
                }
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка збереження рукописного малювання")
        }
    }

    override suspend fun addSignatureStamp(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        signatureBitmap: Bitmap,
        targetRectPt: RectF,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfBoxPageOperationsEngine.init(context)

            openInputStream(context, sourceUri).use { inStream ->
                val doc = PDDocument.load(inStream)
                try {
                    val totalPages = doc.numberOfPages
                    if (pageIndex !in 0 until totalPages) {
                        throw IndexOutOfBoundsException(
                            "Індекс сторінки $pageIndex виходить за межі документа (0..${totalPages - 1})"
                        )
                    }

                    val page = doc.getPage(pageIndex)

                    // Конвертація підпису з підтримкою прозорості (LosslessFactory) або fallback на JPEGFactory
                    val pdImage = try {
                        LosslessFactory.createFromImage(doc, signatureBitmap)
                    } catch (_: Throwable) {
                        JPEGFactory.createFromImage(doc, signatureBitmap)
                    }

                    // Лівий нижній кут зображення на видимій сторінці; осі зображення — «праворуч» і «угору»
                    // видимої сторінки, тому підпис стоїть рівно за будь-якого /Rotate.
                    val geometry = PageGeometry.of(page)
                    val width = maxOf(targetRectPt.right - targetRectPt.left, 1f)
                    val height = maxOf(targetRectPt.bottom - targetRectPt.top, 1f)
                    val m = geometry.contentMatrix(targetRectPt.left, targetRectPt.top + height)
                    val matrix = Matrix(m[0] * width, m[1] * width, m[2] * height, m[3] * height, m[4], m[5])

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { cs ->
                        cs.drawImage(pdImage, matrix)
                    }

                    saveDocumentSafely(context, doc, outputUri)
                    PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
                } finally {
                    doc.close()
                }
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка вставки підпису")
        }
    }

    override suspend fun addFreeText(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        textData: FreeTextData,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfBoxPageOperationsEngine.init(context)

            openInputStream(context, sourceUri).use { inStream ->
                val doc = PDDocument.load(inStream)
                try {
                    val totalPages = doc.numberOfPages
                    if (pageIndex !in 0 until totalPages) {
                        throw IndexOutOfBoundsException(
                            "Індекс сторінки $pageIndex виходить за межі документа (0..${totalPages - 1})"
                        )
                    }

                    val page = doc.getPage(pageIndex)
                    val fontSize = if (textData.fontSize > 0f) textData.fontSize else 14f

                    // Базова лінія — на fontSize нижче верхнього краю рамки (у просторі PageViewPoint);
                    // локальна система тексту повернута разом зі сторінкою.
                    val geometry = PageGeometry.of(page)
                    val m = geometry.contentMatrix(textData.x, textData.y + fontSize)

                    val r = ((textData.color shr 16) and 0xFF) / 255.0f
                    val g = ((textData.color shr 8) and 0xFF) / 255.0f
                    val b = (textData.color and 0xFF) / 255.0f

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { cs ->
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, fontSize)
                        cs.setNonStrokingColor(r, g, b)
                        cs.setTextMatrix(Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
                        try {
                            cs.showText(textData.text)
                        } catch (_: IllegalArgumentException) {
                            // Fallback для символів поза WinAnsi кодуванням: заміна непідтримуваних
                            val sanitized = textData.text.map { ch ->
                                if (ch.code in 32..255) ch else '?'
                            }.joinToString("")
                            cs.showText(sanitized)
                        }
                        cs.endText()
                    }

                    saveDocumentSafely(context, doc, outputUri)
                    PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
                } finally {
                    doc.close()
                }
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка додавання тексту")
        }
    }

    private fun openInputStream(context: Context, uri: Uri): InputStream {
        return if (uri.scheme == "file") {
            val path = uri.path ?: throw IllegalArgumentException("Некоректний file URI: $uri")
            FileInputStream(File(path))
        } else {
            context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Неможливо відкрити файл: $uri")
        }
    }

    /**
     * Безпечне збереження документа через проміжний файл, щоб уникнути блокувань
     * дескрипторів та пошкодження оригінального файлу при перезаписі (in-place save).
     */
    private fun saveDocumentSafely(context: Context, doc: PDDocument, outputUri: Uri) {
        if (outputUri.scheme == "file") {
            val path = outputUri.path ?: throw IllegalArgumentException("Некоректний file URI: $outputUri")
            val targetFile = File(path)
            val parentDir = targetFile.parentFile ?: context.cacheDir
            parentDir.mkdirs()

            val tempFile = File.createTempFile("volum_annot_", ".tmp", parentDir)
            try {
                doc.save(tempFile)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.inputStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.delete()
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        } else {
            val tempFile = File.createTempFile("volum_annot_saf_", ".tmp", context.cacheDir)
            try {
                doc.save(tempFile)
                val outStream = context.contentResolver.openOutputStream(outputUri, "wt")
                    ?: context.contentResolver.openOutputStream(outputUri)
                    ?: throw FileNotFoundException("Неможливо відкрити потік для запису: $outputUri")

                tempFile.inputStream().use { input ->
                    outStream.use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    private fun mapToPdfError(cause: Throwable, defaultMessage: String): PdfOperationResult.Error {
        val userMessage = when (cause) {
            is InvalidPasswordException -> "Невірний пароль для захищеного PDF-документа"
            is IllegalArgumentException -> cause.localizedMessage ?: defaultMessage
            is IndexOutOfBoundsException -> cause.localizedMessage ?: defaultMessage
            is FileNotFoundException -> cause.localizedMessage ?: defaultMessage
            else -> "$defaultMessage: ${cause.localizedMessage ?: cause.javaClass.simpleName}"
        }
        return PdfOperationResult.Error(cause = cause, userMessage = userMessage)
    }
}
