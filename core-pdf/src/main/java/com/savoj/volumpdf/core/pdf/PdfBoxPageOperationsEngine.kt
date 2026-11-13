package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.CompressionLevel
import com.savoj.volumpdf.core.pdf.model.PageTransform
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

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

    private fun loadFont(doc: PDDocument, context: Context): PDFont {
        try {
            val stream = try {
                context.assets?.open(MarkupLayout.FONT_ASSET_PATH)
            } catch (_: Throwable) {
                null
            } ?: run {
                val f1 = File("src/main/assets/${MarkupLayout.FONT_ASSET_PATH}")
                val f2 = File("core-pdf/src/main/assets/${MarkupLayout.FONT_ASSET_PATH}")
                when {
                    f1.exists() -> f1.inputStream()
                    f2.exists() -> f2.inputStream()
                    else -> null
                }
            }
            if (stream != null) {
                return stream.use { PDType0Font.load(doc, it) }
            }
        } catch (_: Throwable) {}
        return PDType1Font.HELVETICA_BOLD
    }

    override suspend fun addWatermark(
        context: Context,
        sourceUri: Uri,
        watermarkText: String,
        outputUri: Uri,
        password: String?,
        pageIndices: List<Int>?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)
            require(watermarkText.isNotBlank()) {
                "Текст водяного знака не може бути порожнім"
            }

            val doc = PdfIo.loadDocument(context, sourceUri, password)
            doc.use { targetDoc ->
                val totalPages = targetDoc.numberOfPages
                val font = loadFont(targetDoc, context)
                val targetPages = pageIndices ?: (0 until totalPages).toList()

                val cos45 = 0.70710678f
                val sin45 = 0.70710678f

                for (pageIndex in targetPages) {
                    if (pageIndex !in 0 until totalPages) continue
                    val page = targetDoc.getPage(pageIndex)
                    val g = PageGeometry.of(page)

                    val centerX = g.visibleWidth / 2f
                    val centerY = g.visibleHeight / 2f

                    val fontSize = (minOf(g.visibleWidth, g.visibleHeight) * 0.10f).coerceIn(24f, 56f)
                    val textWidth = try {
                        font.getStringWidth(watermarkText) / 1000f * fontSize
                    } catch (_: Throwable) {
                        watermarkText.length * fontSize * 0.5f
                    }

                    val x0 = centerX - (textWidth / 2f) * cos45
                    val y0 = centerY + (textWidth / 2f) * sin45

                    val p0 = g.toUser(x0, y0)
                    val px = g.toUser(x0 + cos45, y0 - sin45)
                    val py = g.toUser(x0 - sin45, y0 - cos45)

                    val a = px.x - p0.x
                    val b = px.y - p0.y
                    val c = py.x - p0.x
                    val d = py.y - p0.y
                    val e = p0.x
                    val f = p0.y

                    val cs = PDPageContentStream(targetDoc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    cs.use { stream ->
                        stream.saveGraphicsState()

                        val gs = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = 0.25f
                        }
                        stream.setGraphicsStateParameters(gs)
                        stream.setNonStrokingColor(128, 128, 128)

                        stream.beginText()
                        stream.setFont(font, fontSize)
                        stream.setTextMatrix(Matrix(a, b, c, d, e, f))
                        stream.showText(watermarkText)
                        stream.endText()

                        stream.restoreGraphicsState()
                    }
                }

                PdfIo.keepEncryption(targetDoc, password)
                PdfIo.saveSafely(context, targetDoc, outputUri)
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка додавання водяного знака")
        }
    }

    override suspend fun addPageNumbers(
        context: Context,
        sourceUri: Uri,
        formatPattern: String,
        outputUri: Uri,
        password: String?,
        pageIndices: List<Int>?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)
            val doc = PdfIo.loadDocument(context, sourceUri, password)
            doc.use { targetDoc ->
                val totalPages = targetDoc.numberOfPages
                val font = loadFont(targetDoc, context)
                val targetPages = pageIndices ?: (0 until totalPages).toList()
                val fontSize = 10f

                for (pageIndex in targetPages) {
                    if (pageIndex !in 0 until totalPages) continue
                    val page = targetDoc.getPage(pageIndex)
                    val g = PageGeometry.of(page)

                    val pageNum = pageIndex + 1
                    val label = try {
                        String.format(formatPattern, pageNum, totalPages)
                    } catch (_: Exception) {
                        "$pageNum / $totalPages"
                    }

                    val textWidth = try {
                        font.getStringWidth(label) / 1000f * fontSize
                    } catch (_: Throwable) {
                        label.length * fontSize * 0.5f
                    }

                    val centerX = g.visibleWidth / 2f
                    val baselineY = g.visibleHeight - 24f
                    val textX = centerX - textWidth / 2f

                    val m = g.contentMatrix(textX, baselineY)

                    val cs = PDPageContentStream(targetDoc, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    cs.use { stream ->
                        stream.saveGraphicsState()
                        stream.setNonStrokingColor(64, 64, 64)

                        stream.beginText()
                        stream.setFont(font, fontSize)
                        stream.setTextMatrix(Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
                        stream.showText(label)
                        stream.endText()

                        stream.restoreGraphicsState()
                    }
                }

                PdfIo.keepEncryption(targetDoc, password)
                PdfIo.saveSafely(context, targetDoc, outputUri)
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка додавання нумерації сторінок")
        }
    }

    override suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        level: CompressionLevel,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)
            val originalSizeBytes = getFileSize(context, sourceUri)

            val doc = PdfIo.loadDocument(context, sourceUri, password)
            doc.use { targetDoc ->
                val totalPages = targetDoc.numberOfPages

                for (pageIndex in 0 until totalPages) {
                    val page = targetDoc.getPage(pageIndex)
                    val resources = page.resources ?: continue
                    val names = try {
                        resources.xObjectNames.toList()
                    } catch (_: Throwable) {
                        emptyList()
                    }

                    for (name in names) {
                        try {
                            if (!resources.isImageXObject(name)) continue
                            val imageXObject = resources.getXObject(name) as? PDImageXObject ?: continue
                            val bmp = imageXObject.image ?: continue

                            val origWidth = bmp.width
                            val origHeight = bmp.height
                            if (origWidth <= 0 || origHeight <= 0) continue

                            val maxDim = maxOf(origWidth, origHeight)
                            val targetMaxDim = level.maxDimension

                            val scaledBmp = if (maxDim > targetMaxDim) {
                                val scale = targetMaxDim.toFloat() / maxDim
                                val targetW = (origWidth * scale).toInt().coerceAtLeast(1)
                                val targetH = (origHeight * scale).toInt().coerceAtLeast(1)
                                Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                            } else {
                                bmp
                            }

                            val newXObject = JPEGFactory.createFromImage(targetDoc, scaledBmp, level.jpegQuality)
                            resources.put(name, newXObject)

                            if (scaledBmp !== bmp) {
                                scaledBmp.recycle()
                            }
                        } catch (_: Throwable) {
                            // Пропускаємо окремі зображення, якщо вони не підлягають перестисненню або на JVM
                        }
                    }
                }

                PdfIo.keepEncryption(targetDoc, password)
                PdfIo.saveSafely(context, targetDoc, outputUri)
                val newSizeBytes = getFileSize(context, outputUri)

                PdfOperationResult.Success(
                    outputUri = outputUri,
                    pagesCount = totalPages,
                    originalSizeBytes = originalSizeBytes,
                    newSizeBytes = newSizeBytes
                )
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка стиснення PDF")
        }
    }

    override suspend fun organizePages(
        context: Context,
        sourceUri: Uri,
        pageTransforms: List<PageTransform>,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)
            if (pageTransforms.isEmpty()) {
                return@withContext PdfOperationResult.Error(
                    IllegalArgumentException("Список сторінок для збереження порожній"),
                    "Список сторінок для збереження порожній"
                )
            }

            val sourceDoc = PdfIo.loadDocument(context, sourceUri, password)
            sourceDoc.use { doc ->
                val totalPages = doc.numberOfPages
                for (transform in pageTransforms) {
                    if (transform.sourcePageIndex < 0 || transform.sourcePageIndex >= totalPages) {
                        return@withContext PdfOperationResult.Error(
                            IndexOutOfBoundsException(
                                "Індекс сторінки ${transform.sourcePageIndex + 1} виходить за межі документа (всього $totalPages)"
                            ),
                            "Індекс сторінки виходить за межі документа"
                        )
                    }
                }

                val targetDoc = PDDocument()
                targetDoc.use { target ->
                    for (transform in pageTransforms) {
                        val srcPage = doc.getPage(transform.sourcePageIndex)
                        val importedPage = target.importPage(srcPage)
                        if (transform.rotationDegrees % 360 != 0) {
                            val currentRotation = importedPage.rotation
                            val newRotation = (currentRotation + transform.rotationDegrees) % 360
                            importedPage.rotation = if (newRotation < 0) newRotation + 360 else newRotation
                        }
                    }

                    PdfIo.copyEncryption(doc, target, password)
                    PdfIo.saveSafely(context, target, outputUri)
                    PdfOperationResult.Success(
                        outputUri = outputUri,
                        pagesCount = pageTransforms.size
                    )
                }
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка впорядкування сторінок PDF")
        }
    }

    override suspend fun createBlankPdf(
        context: Context,
        outputUri: Uri,
        pageCount: Int
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)
            val doc = PDDocument(PdfIo.defaultMemorySetting(context))
            try {
                val count = pageCount.coerceAtLeast(1)
                for (i in 0 until count) {
                    doc.addPage(PDPage(PDRectangle.A4))
                }
                PdfIo.saveSafely(context, doc, outputUri)
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = count)
            } finally {
                doc.close()
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка створення нового PDF")
        }
    }

    override suspend fun appendBlankPages(
        context: Context,
        sourceUri: Uri,
        count: Int,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)
            val doc = PdfIo.loadDocument(context, sourceUri, password)
            try {
                val pagesToAdd = count.coerceAtLeast(1)
                for (i in 0 until pagesToAdd) {
                    doc.addPage(PDPage(PDRectangle.A4))
                }
                PdfIo.saveSafely(context, doc, outputUri)
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = doc.numberOfPages)
            } finally {
                doc.close()
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка додавання сторінки до PDF")
        }
    }

    override suspend fun appendFiles(
        context: Context,
        sourceUri: Uri,
        additionalUris: List<Uri>,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            init(context)
            if (additionalUris.isEmpty()) {
                val msg = "Не вибрано жодного файлу для додавання"
                return@withContext PdfOperationResult.Error(IllegalArgumentException(msg), msg)
            }
            val targetDoc = PdfIo.loadDocument(context, sourceUri, password)
            try {
                val merger = PDFMergerUtility()
                for ((index, uri) in additionalUris.withIndex()) {
                    val mimeType = try { context.contentResolver?.getType(uri).orEmpty() } catch (_: Throwable) { "" }
                    val uriStr = uri.toString().lowercase()
                    val uriPath = uri.path?.lowercase().orEmpty()
                    val isImage = mimeType.startsWith("image/") ||
                            listOf(".jpg", ".jpeg", ".png", ".webp").any { uriStr.endsWith(it) || uriPath.endsWith(it) }

                    if (isImage) {
                        val imageBytes = PdfIo.openInputStream(context, uri).use { it.readBytes() }
                        if (imageBytes.isNotEmpty()) {
                            val parsedDim = extractImageDimensions(imageBytes)
                            val imgX = PDImageXObject.createFromByteArray(
                                targetDoc,
                                imageBytes,
                                "append_img_${index}_${System.currentTimeMillis()}"
                            )
                            val imgWidth = when {
                                imgX.width > 0 -> imgX.width
                                parsedDim != null && parsedDim.first > 0 -> {
                                    imgX.width = parsedDim.first
                                    parsedDim.first
                                }
                                else -> PDRectangle.A4.width.toInt()
                            }.toFloat()
                            val imgHeight = when {
                                imgX.height > 0 -> imgX.height
                                parsedDim != null && parsedDim.second > 0 -> {
                                    imgX.height = parsedDim.second
                                    parsedDim.second
                                }
                                else -> PDRectangle.A4.height.toInt()
                            }.toFloat()

                            val page = PDPage(PDRectangle(imgWidth, imgHeight))
                            targetDoc.addPage(page)
                            PDPageContentStream(targetDoc, page).use { cs ->
                                cs.drawImage(imgX, 0f, 0f, imgWidth, imgHeight)
                            }
                        }
                    } else {
                        // PDF документ
                        PdfIo.loadDocument(context, uri, null).use { srcDoc ->
                            merger.appendDocument(targetDoc, srcDoc)
                        }
                    }
                }
                PdfIo.saveSafely(context, targetDoc, outputUri)
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = targetDoc.numberOfPages)
            } finally {
                targetDoc.close()
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            mapToPdfError(cause, "Помилка додавання файлів до PDF")
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            uri.path?.let { p ->
                val f = File(p)
                if (f.exists()) return f.length()
            }
        } else if (uri.scheme == "content") {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    return pfd.statSize
                }
            } catch (_: Throwable) {}
        }
        return 0L
    }

    private fun mapToPdfError(cause: Throwable, defaultMessage: String): PdfOperationResult.Error =
        PdfIo.mapToPdfError(cause, defaultMessage)
}
