package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Реалізація [PageOperationsEngine] на основі бібліотеки PdfBox-Android.
 * Виконує операції зі сторінками: злиття, спліт (вилучення), поворот, видалення та конвертацію зображень у PDF.
 * Використовує оптимізацію пам'яті (MemoryUsageSetting) та безпечне атомарне збереження (saveSafely).
 */
class PdfBoxPageOperationsEngine : PageOperationsEngine {

    companion object {
        /**
         * Ініціалізація ресурсів PdfBox (безпечно викликати багаторазово).
         */
        fun init(context: Context) {
            if (!PDFBoxResourceLoader.isReady()) {
                try {
                    PDFBoxResourceLoader.init(context.applicationContext ?: context)
                } catch (_: Exception) {
                    // У mock/JVM-середовищі Context може не мати реальних assets
                }
            }
        }
    }

    override fun mergePdfs(
        context: Context,
        sourceUris: List<Uri>,
        outputUri: Uri
    ): Flow<PdfOperationResult> = flow {
        init(context)

        require(sourceUris.size >= 2) {
            "Для злиття потрібно щонайменше 2 PDF-документи (отримано: ${sourceUris.size})"
        }

        val targetDoc = PDDocument(PdfIo.defaultMemorySetting(context))
        try {
            val merger = PDFMergerUtility()
            for ((index, uri) in sourceUris.withIndex()) {
                emit(PdfOperationResult.Progress(currentStep = index + 1, totalSteps = sourceUris.size))

                PdfIo.loadDocument(context, uri, null).use { srcDoc ->
                    merger.appendDocument(targetDoc, srcDoc)
                }
            }

            PdfIo.saveSafely(context, targetDoc, outputUri)

            val totalPages = targetDoc.numberOfPages
            emit(PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages))
        } finally {
            targetDoc.close()
        }
    }.catch { cause ->
        if (cause is CancellationException) throw cause
        emit(mapToPdfError(cause, "Помилка злиття PDF-файлів"))
    }.flowOn(Dispatchers.IO)

    override fun extractPages(
        context: Context,
        sourceUri: Uri,
        pageIndices: List<Int>,
        outputUri: Uri,
        password: String?
    ): Flow<PdfOperationResult> = flow {
        init(context)

        require(pageIndices.isNotEmpty()) {
            "Список сторінок для вилучення не може бути порожнім"
        }

        PdfIo.loadDocument(context, sourceUri, password).use { doc ->
            val totalPages = doc.numberOfPages
            for (index in pageIndices) {
                if (index !in 0 until totalPages) {
                    throw IndexOutOfBoundsException(
                        "Індекс сторінки $index виходить за межі [0, ${totalPages - 1}]"
                    )
                }
            }

            val targetDoc = PDDocument(PdfIo.defaultMemorySetting(context))
            try {
                for ((step, index) in pageIndices.withIndex()) {
                    emit(PdfOperationResult.Progress(currentStep = step + 1, totalSteps = pageIndices.size))
                    val page = doc.getPage(index)
                    targetDoc.importPage(page)
                }

                PdfIo.copyEncryption(doc, targetDoc, password)
                PdfIo.saveSafely(context, targetDoc, outputUri)

                val savedPages = targetDoc.numberOfPages
                emit(PdfOperationResult.Success(outputUri = outputUri, pagesCount = savedPages))
            } finally {
                targetDoc.close()
            }
        }
    }.catch { cause ->
        if (cause is CancellationException) throw cause
        emit(mapToPdfError(cause, "Помилка вилучення сторінок із PDF"))
    }.flowOn(Dispatchers.IO)

    override suspend fun rotatePages(
        context: Context,
        sourceUri: Uri,
        pageIndices: List<Int>,
        degrees: Int,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)

            require(pageIndices.isNotEmpty()) {
                "Список сторінок для повороту не може бути порожнім"
            }
            require(degrees % 90 == 0) {
                "Кут повороту має бути кратним 90 градусам (отримано: $degrees)"
            }

            val doc = PdfIo.loadDocument(context, sourceUri, password)
            doc.use {
                val totalPages = it.numberOfPages
                for (index in pageIndices) {
                    if (index !in 0 until totalPages) {
                        throw IndexOutOfBoundsException(
                            "Індекс сторінки $index виходить за межі [0, ${totalPages - 1}]"
                        )
                    }
                    val page = it.getPage(index)
                    val currentRotation = page.rotation
                    var newRotation = (currentRotation + degrees) % 360
                    if (newRotation < 0) newRotation += 360
                    page.rotation = newRotation
                }

                PdfIo.keepEncryption(it, password)
                PdfIo.saveSafely(context, it, outputUri)

                PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка повороту сторінок")
        }
    }

    override suspend fun deletePages(
        context: Context,
        sourceUri: Uri,
        pageIndicesToDelete: List<Int>,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)

            require(pageIndicesToDelete.isNotEmpty()) {
                "Список сторінок для видалення не може бути порожнім"
            }

            val doc = PdfIo.loadDocument(context, sourceUri, password)
            doc.use {
                val totalPages = it.numberOfPages
                val distinctToDelete = pageIndicesToDelete.distinct().sortedDescending()

                for (index in distinctToDelete) {
                    if (index !in 0 until totalPages) {
                        throw IndexOutOfBoundsException(
                            "Індекс сторінки $index виходить за межі [0, ${totalPages - 1}]"
                        )
                    }
                }

                if (distinctToDelete.size >= totalPages) {
                    throw IllegalArgumentException(
                        "Неможливо видалити всі сторінки. Документ має містити щонайменше 1 сторінку (всього: $totalPages, до видалення: ${distinctToDelete.size})"
                    )
                }

                for (index in distinctToDelete) {
                    it.removePage(index)
                }

                val remainingPages = it.numberOfPages
                PdfIo.keepEncryption(it, password)
                PdfIo.saveSafely(context, it, outputUri)

                PdfOperationResult.Success(outputUri = outputUri, pagesCount = remainingPages)
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка видалення сторінок")
        }
    }

    override suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputUri: Uri
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)

            require(imageUris.isNotEmpty()) {
                "Список зображень для конвертації не може бути порожнім"
            }

            val doc = PDDocument(PdfIo.defaultMemorySetting(context))
            try {
                for ((index, imageUri) in imageUris.withIndex()) {
                    val imageBytes = PdfIo.openInputStream(context, imageUri).use { it.readBytes() }
                    require(imageBytes.isNotEmpty()) {
                        "Зображення за індексом $index ($imageUri) порожнє"
                    }

                    val imageXObject = PDImageXObject.createFromByteArray(
                        doc,
                        imageBytes,
                        "img_${index}_${System.currentTimeMillis()}"
                    )

                    val parsedDim = extractImageDimensions(imageBytes)
                    val imgWidth = when {
                        imageXObject.width > 0 -> imageXObject.width
                        parsedDim != null && parsedDim.first > 0 -> {
                            imageXObject.width = parsedDim.first
                            parsedDim.first
                        }
                        else -> PDRectangle.A4.width.toInt()
                    }

                    val imgHeight = when {
                        imageXObject.height > 0 -> imageXObject.height
                        parsedDim != null && parsedDim.second > 0 -> {
                            imageXObject.height = parsedDim.second
                            parsedDim.second
                        }
                        else -> PDRectangle.A4.height.toInt()
                    }

                    val width = imgWidth.toFloat()
                    val height = imgHeight.toFloat()
                    val page = PDPage(PDRectangle(width, height))
                    doc.addPage(page)

                    PDPageContentStream(doc, page).use { contentStream ->
                        contentStream.drawImage(imageXObject, 0f, 0f, width, height)
                    }
                }

                PdfIo.saveSafely(context, doc, outputUri)

                val pagesCount = doc.numberOfPages
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = pagesCount)
            } finally {
                doc.close()
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка створення PDF із зображень")
        }
    }

    private fun extractImageDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size > 8 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            // JPEG: пошук маркера SOF
            var i = 2
            while (i < bytes.size - 8) {
                if (bytes[i] != 0xFF.toByte()) {
                    i++
                    continue
                }
                val marker = bytes[i + 1].toInt() and 0xFF
                if (marker in 0xC0..0xC3 || marker in 0xC5..0xC7 || marker in 0xC9..0xCB || marker in 0xCD..0xCF) {
                    val h = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                    val w = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                    if (w > 0 && h > 0) return Pair(w, h)
                }
                val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                if (length < 2) break
                i += 2 + length
            }
        } else if (bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            // PNG: заголовок IHDR (width at 16..19, height at 20..23)
            val w = ((bytes[16].toInt() and 0xFF) shl 24) or
                    ((bytes[17].toInt() and 0xFF) shl 16) or
                    ((bytes[18].toInt() and 0xFF) shl 8) or
                    (bytes[19].toInt() and 0xFF)
            val h = ((bytes[20].toInt() and 0xFF) shl 24) or
                    ((bytes[21].toInt() and 0xFF) shl 16) or
                    ((bytes[22].toInt() and 0xFF) shl 8) or
                    (bytes[23].toInt() and 0xFF)
            if (w > 0 && h > 0) return Pair(w, h)
        }
        return null
    }

    private fun mapToPdfError(cause: Throwable, defaultMessage: String): PdfOperationResult.Error =
        PdfIo.mapToPdfError(cause, defaultMessage)
}
