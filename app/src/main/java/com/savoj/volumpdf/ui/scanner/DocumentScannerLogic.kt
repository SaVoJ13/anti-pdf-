package com.savoj.volumpdf.ui.scanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Логіка конфігурації та збереження документів для вбудованого сканера (ТОП-1).
 */
object DocumentScannerLogic {

    /** Джерело для побудови PDF із результату сканера. */
    sealed class ScanSource {
        /** JPEG-сторінки сканера: PDF будується через imagesToPdf (A4, O-1). */
        data class Images(val imageUris: List<Uri>, val fallbackPdfUri: Uri?) : ScanSource()

        /** Готовий PDF від ML Kit (немає JPEG-сторінок). */
        data class MlKitPdf(val pdfUri: Uri) : ScanSource()
    }

    /**
     * Чиста логіка вибору: є JPEG-сторінки -> [ScanSource.Images] (ML Kit PDF лишається запасним),
     * інакше PDF від ML Kit; якщо нема нічого -> null.
     */
    fun chooseScanSource(pageImageUris: List<Uri>, pdfUri: Uri?): ScanSource? = when {
        pageImageUris.isNotEmpty() -> ScanSource.Images(pageImageUris, pdfUri)
        pdfUri != null -> ScanSource.MlKitPdf(pdfUri)
        else -> null
    }

    fun generateScannedFileName(timestampMs: Long = System.currentTimeMillis()): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val formattedDate = dateFormat.format(Date(timestampMs))
        return "Scan_$formattedDate.pdf"
    }

    fun buildScannerOptions(pageLimit: Int = 100): GmsDocumentScannerOptions {
        return GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    /**
     * Копіює тимчасовий PDF відсканованого документа із кешу Google Play Services
     * у внутрішнє постійне сховище додатка, запобігаючи втраті файлу після закриття сканера.
     */
    fun persistScannedPdf(context: Context, sourcePdfUri: Uri, targetFileName: String): File {
        val scannedDir = File(context.filesDir, "scanned").apply {
            if (!exists()) mkdirs()
        }
        val targetFile = File(scannedDir, targetFileName)
        context.contentResolver.openInputStream(sourcePdfUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Не вдалося відкрити вхідний потік для $sourcePdfUri")
        return targetFile
    }
}
