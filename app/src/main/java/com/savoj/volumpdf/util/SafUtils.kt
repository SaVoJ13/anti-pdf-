package com.savoj.volumpdf.util

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object SafUtils {

    /**
     * Безпечне взяття постійних дозволів на читання для SAF URI.
     */
    fun takePersistableUriPermissionSafe(context: Context, uri: Uri) {
        if (uri.scheme == "content") {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Деякі ContentProvider (наприклад, сторонні застосунки) не підтримують persistable permission
            } catch (_: Throwable) {
                // Ігноруємо для безпеки
            }
        }
    }

    /**
     * Отримання назви файлу з ContentResolver або URI.
     */
    fun queryDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIndex != -1) {
                            val name = cursor.getString(colIndex)
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fallback нижче
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                if (file.name.isNotBlank()) return file.name
            }
        }

        return uri.lastPathSegment ?: "document.pdf"
    }

    /**
     * Отримання розміру файлу в байтах.
     */
    fun queryFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (colIndex != -1 && !cursor.isNull(colIndex)) {
                            return cursor.getLong(colIndex)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fallback нижче
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                if (file.exists()) return file.length()
            }
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                return pfd.statSize
            }
        } catch (_: Throwable) {
            // Ігноруємо
        }

        return 0L
    }

    /**
     * Швидке отримання кількості сторінок без зайвого завантаження в памʼять.
     */
    fun queryPageCount(context: Context, uri: Uri): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                return renderer.pageCount
            }
        } catch (_: Throwable) {
            // Файл може бути зашифрований або не підтримуватися стандартним PdfRenderer
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
        return 0
    }

    /**
     * Форматування розміру файлу для людини (B, KB, MB, GB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${bytes.toInt()} ${units[unitIndex]}"
        } else {
            String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
        }
    }

    /**
     * Форматування дати останнього відкриття.
     */
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val date = Date(timestamp)
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return format.format(date)
    }
}
