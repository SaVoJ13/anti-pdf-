package com.savoj.volumpdf.util

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
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

        return nameFromPathLike(uri.lastPathSegment) ?: "document.pdf"
    }

    /**
     * Назва з «шляхоподібного» сегмента URI (`primary:Documents/a.pdf` -> `a.pdf`). Числовий ідентифікатор
     * (`content://media/external/file/1000012345`) назвою не є: повертаємо null, щоб у заголовку не було числа.
     */
    fun nameFromPathLike(segment: String?): String? {
        val tail = segment?.substringAfterLast('/')?.substringAfterLast(':')?.trim().orEmpty()
        return tail.takeIf { !isOpaqueName(it) }
    }

    /** Порожня назва або лише цифри: це ідентифікатор запису, а не ім'я файлу. */
    fun isOpaqueName(name: String?): Boolean = name.isNullOrBlank() || name.all { it.isDigit() }

    /**
     * P1-8: чи відкрито документ лише для читання (немає права запису). Не відкриває файл для запису (щоб не зачепити
     * хмарні провайдери): `file://` перевіряє `canWrite`, `content://` — наявність права запису на URI.
     */
    fun isReadOnly(context: Context, uri: Uri): Boolean {
        return try {
            val writeGranted = uri.scheme == "content" &&
                context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val fileWritable = if (uri.scheme == "file") uri.path?.let { File(it).canWrite() } else null
            ReadOnlyCheck.decide(uri.scheme, writeGranted, fileWritable)
        } catch (_: Throwable) {
            false // невідомо: не лякаємо підписом
        }
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
     * Розмір файлу або null, якщо його не вдалося дізнатись (колонки SIZE немає, значення null, запит не вдався).
     * На відміну від [queryFileSize], невідомий розмір не видає за 0: за ним не можна вирішувати про видалення.
     */
    fun queryFileSizeOrNull(context: Context, uri: Uri): Long? {
        try {
            when (uri.scheme) {
                "content" -> context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (colIndex != -1 && !cursor.isNull(colIndex)) return cursor.getLong(colIndex)
                    }
                }
                "file" -> uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) return file.length()
                }
            }
        } catch (_: Throwable) {
            // розмір невідомий
        }
        return null
    }

    /**
     * Прибирає порожній (0 байтів) файл, який щойно створив системний діалог «Зберегти як…» для цієї дії, коли дія
     * не вдалась (QA2 Д1). Файл із вмістом і файл невідомого розміру не чіпає. true, якщо видалено.
     */
    fun deleteIfEmptyQuietly(context: Context, uri: Uri): Boolean =
        queryFileSizeOrNull(context, uri) == 0L && deleteDocumentQuietly(context, uri)

    /** Видаляє документ SAF; true, якщо видалено. Помилки не кидає (best effort). */
    fun deleteDocumentQuietly(context: Context, uri: Uri): Boolean = try {
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).delete() } ?: false
            else -> DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    } catch (_: Throwable) {
        false
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

/** Чисте рішення «лише читання» (тестується без Android). */
object ReadOnlyCheck {
    /** Пропонована назва копії лише-для-читання файлу: «<ім'я>_copy.pdf». */
    fun copyName(title: String?): String {
        val base = (title ?: "").trim().let { if (it.endsWith(".pdf", ignoreCase = true)) it.dropLast(4) else it }.trim()
        return (if (base.isEmpty()) "document" else base) + "_copy.pdf"
    }

    /**
     * @param writeGranted для `content://`: чи є право запису на URI
     * @param fileCanWrite для `file://`: результат `File.canWrite` (null — невідомо)
     */
    fun decide(scheme: String?, writeGranted: Boolean, fileCanWrite: Boolean?): Boolean = when (scheme) {
        "content" -> !writeGranted
        "file" -> fileCanWrite == false
        else -> false
    }
}
