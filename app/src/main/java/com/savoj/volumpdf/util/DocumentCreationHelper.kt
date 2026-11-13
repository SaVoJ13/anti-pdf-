package com.savoj.volumpdf.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.savoj.volumpdf.R
import com.savoj.volumpdf.VolumPdfApp
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.ui.reader.ReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Допоміжний клас для створення та відкриття нових порожніх PDF-документів.
 */
object DocumentCreationHelper {

    fun generateNewPdfFileName(timestampMs: Long = System.currentTimeMillis()): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val formattedDate = dateFormat.format(Date(timestampMs))
        return "Document_$formattedDate.pdf"
    }

    suspend fun createBlankDocument(
        context: Context,
        fileName: String? = null,
        pageCount: Int = 1
    ): File = withContext(Dispatchers.IO) {
        val docDir = File(context.filesDir, "documents").apply {
            if (!exists()) mkdirs()
        }
        val actualName = fileName ?: generateNewPdfFileName()
        val targetFile = File(docDir, actualName)
        val outputUri = Uri.fromFile(targetFile)

        val engine = PdfBoxPageOperationsEngine()
        val result = engine.createBlankPdf(context, outputUri, pageCount)
        if (result !is PdfOperationResult.Success) {
            val errorMsg = (result as? PdfOperationResult.Error)?.userMessage ?: "Помилка створення файлу"
            throw IllegalStateException(errorMsg)
        }

        try {
            val app = context.applicationContext as? VolumPdfApp
            app?.repository?.recordDocumentOpened(context, outputUri, result.pagesCount)
        } catch (_: Throwable) {}

        targetFile
    }

    fun openNewDocument(activity: Activity, file: File, title: String? = null) {
        val uri = Uri.fromFile(file)
        val displayTitle = title ?: activity.getString(R.string.new_document_default_title)
        val readerIntent = Intent(activity, ReaderActivity::class.java).apply {
            data = uri
            putExtra(ReaderActivity.EXTRA_DOCUMENT_TITLE, displayTitle)
        }
        activity.startActivity(readerIntent)
    }
}
