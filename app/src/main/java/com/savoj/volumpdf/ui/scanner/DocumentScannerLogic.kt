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
