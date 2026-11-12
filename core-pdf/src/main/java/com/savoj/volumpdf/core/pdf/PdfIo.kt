package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.DocumentEditability
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.core.pdf.model.PdfPermissionException
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream

/** Спільні операції введення/виведення PDF для двигунів розмітки й форм. */
internal object PdfIo {

    fun openInputStream(context: Context, uri: Uri): InputStream {
        return if (uri.scheme == "file") {
            val path = uri.path ?: throw IllegalArgumentException("Некоректний file URI: $uri")
            FileInputStream(File(path))
        } else {
            context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Неможливо відкрити файл: $uri")
        }
    }

    /**
     * Стандартні налаштування пам'яті для завантаження документів PDFBox:
     * змішаний режим пам'яті (до 16 МБ у купі, решта — у тимчасових файлах у кеші), що зменшує
     * пікове споживання пам'яті на великих файлах у 2–6 разів.
     */
    fun defaultMemorySetting(context: Context): MemoryUsageSetting {
        val setting = MemoryUsageSetting.setupMixed(16L * 1024 * 1024)
        val tempDir = runCatching { context.cacheDir }.getOrNull()
        if (tempDir != null && tempDir.exists()) {
            setting.setTempDir(tempDir)
        }
        return setting
    }

    /**
     * Завантажує документ (PDFBox з MemoryUsageSetting.setupMixed(16MB)).
     * Для file:// завантажується безпосередньо з File (випадковий доступ до файлу без повного зчитування в пам'ять).
     */
    fun loadDocument(context: Context, uri: Uri, password: String?): PDDocument {
        val mem = defaultMemorySetting(context)
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.isFile) {
                    return if (password != null) PDDocument.load(file, password, mem)
                    else PDDocument.load(file, mem)
                }
            }
        }
        return openInputStream(context, uri).use { stream ->
            if (password != null) PDDocument.load(stream, password, mem)
            else PDDocument.load(stream, mem)
        }
    }

    /**
     * AcroForm документа. Стандартний `getAcroForm()` застосовує fixup, що перебудовує appearance полів при
     * NeedAppearances=true, і падає на деяких (багатоядкових) полях; тоді читаємо форму без fixup (F1).
     */
    fun acroForm(doc: PDDocument): PDAcroForm? = try {
        doc.documentCatalog.acroForm
    } catch (_: RuntimeException) {
        doc.documentCatalog.getAcroForm(null)
    }

    /** Права поточного відкриття: пароль власника дає все. */
    fun editability(doc: PDDocument): DocumentEditability {
        val ap = doc.currentAccessPermission
        return DocumentEditability(
            isEncrypted = doc.isEncrypted,
            canMarkup = ap.isOwnerPermission || ap.canModify(),
            canFillForms = ap.isOwnerPermission || ap.canFillInForm() || ap.canModifyAnnotations()
        )
    }

    /**
     * Політика R15 (CONTRACTS §1.5.1 п. 8): відмова при забороні зміни; зашифрований документ
     * не можна зберегти в той самий URI. Викликається одразу після завантаження, до будь-яких змін.
     */
    fun enforcePolicy(
        context: Context, doc: PDDocument, sourceUri: Uri, outputUri: Uri, needsModify: Boolean, needsFillForm: Boolean
    ) {
        val e = editability(doc)
        if (needsModify && !e.canMarkup) throw PdfPermissionException("MODIFY")
        if (needsFillForm && !e.canFillForms) throw PdfPermissionException("FILL_FORM")
        if (e.isEncrypted && isSameTarget(context, sourceUri, outputUri)) {
            throw IllegalArgumentException(
                "Зашифрований документ можна зберегти лише як нову копію, а не поверх оригіналу"
            )
        }
    }

    /**
     * Найкраща спроба визначити, чи два URI вказують на той самий файл (CONTRACTS §1.5.1 п. 8в).
     * file:// — canonical path і Files.isSameFile; content:// — authority + documentId
     * (для tree+document URI — id документа); якщо нормалізація неможлива — порівняння рядків.
     * Вихідний файл тут НЕ відкривається (відкриття SAF-виходу з truncate знищило б дані).
     */
    @Suppress("UNUSED_PARAMETER")
    fun isSameTarget(context: Context, a: Uri, b: Uri): Boolean {
        if (a == b || a.toString() == b.toString()) return true
        val sa = a.scheme
        val sb = b.scheme
        if (sa == "file" && sb == "file") {
            val pa = a.path
            val pb = b.path
            if (pa != null && pb != null) {
                try {
                    val fa = File(pa)
                    val fb = File(pb)
                    if (fa.canonicalFile == fb.canonicalFile) return true
                    if (fa.exists() && fb.exists() &&
                        java.nio.file.Files.isSameFile(fa.toPath(), fb.toPath())
                    ) return true
                    return false
                } catch (_: Exception) {
                    // нормалізація неможлива — нижче порівняння рядків
                }
            }
        } else if (sa == "content" && sb == "content") {
            try {
                val authA = a.authority?.lowercase()
                val authB = b.authority?.lowercase()
                val idA = documentIdOf(a)
                val idB = documentIdOf(b)
                if (authA != null && authB != null && idA != null && idB != null) {
                    return authA == authB && idA == idB
                }
            } catch (_: Exception) {
                // нормалізація неможлива — нижче порівняння рядків
            }
        }
        return a.toString() == b.toString()
    }

    /**
     * Для tree+document або document URI повертає id документа (не дерева).
     * Спочатку викликає системний [android.provider.DocumentsContract.getDocumentId].
     * Якщо він недоступний (наприклад, у JVM-тестах) або дає збій — розбирає шлях вручну.
     */
    fun documentIdOf(uri: Uri): String? {
        try {
            val id = android.provider.DocumentsContract.getDocumentId(uri)
            if (!id.isNullOrEmpty()) return id
        } catch (_: Throwable) {
            // системний виклик не вдався або виконується в JVM-середовищі
        }
        val path = uri.path ?: return null
        val docMarker = "/document/"
        val idx = path.indexOf(docMarker)
        if (idx != -1) {
            val rawId = path.substring(idx + docMarker.length)
            return try {
                java.net.URLDecoder.decode(rawId, "UTF-8")
            } catch (_: Throwable) {
                rawId
            }
        }
        return null
    }

    fun copyEncryption(sourceDoc: PDDocument, targetDoc: PDDocument, password: String?) {
        if (!sourceDoc.isEncrypted) return
        val pw = password ?: ""
        val keyLength = sourceDoc.encryption?.length?.takeIf { it == 40 || it == 128 || it == 256 } ?: 128
        val policy = StandardProtectionPolicy(pw, pw, AccessPermission())
        policy.encryptionKeyLength = keyLength
        policy.isPreferAES = keyLength >= 128
        targetDoc.protect(policy)
    }

    /**
     * PDFBox не зберігає завантажений зашифрований документ без політики захисту. Копія шифрується
     * паролем, яким відкрито джерело (user = owner), з повними правами: пароль власника й обмеження
     * прав не переносяться. Рівень алгоритму зберігається (RC4-128 → AES-128 допустимо).
     */
    fun keepEncryption(doc: PDDocument, password: String?) {
        copyEncryption(doc, doc, password)
    }

    /** Збереження через проміжний файл: оригінал не пошкоджується, якщо збереження не вдалося. */
    fun saveSafely(context: Context, doc: PDDocument, outputUri: Uri) {
        if (outputUri.scheme == "file") {
            val path = outputUri.path ?: throw IllegalArgumentException("Некоректний file URI: $outputUri")
            val targetFile = File(path)
            val parentDir = targetFile.parentFile ?: context.cacheDir
            parentDir.mkdirs()
            val tempFile = File.createTempFile("volum_markup_", ".tmp", parentDir)
            try {
                doc.save(tempFile)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.inputStream().use { input ->
                        FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                    }
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        } else {
            val tempFile = File.createTempFile("volum_markup_saf_", ".tmp", context.cacheDir)
            try {
                doc.save(tempFile)
                val outStream = context.contentResolver.openOutputStream(outputUri, "wt")
                    ?: context.contentResolver.openOutputStream(outputUri)
                    ?: throw FileNotFoundException("Неможливо відкрити потік для запису: $outputUri")
                tempFile.inputStream().use { input ->
                    outStream.use { output -> input.copyTo(output) }
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    fun mapToPdfError(cause: Throwable, defaultMessage: String): PdfOperationResult.Error {
        val userMessage = when (cause) {
            is OutOfMemoryError -> "Файл завеликий для редагування"
            is PdfPermissionException -> cause.message ?: defaultMessage
            is InvalidPasswordException -> "Невірний пароль для захищеного PDF-документа"
            is IllegalArgumentException -> cause.localizedMessage ?: defaultMessage
            is IndexOutOfBoundsException -> cause.localizedMessage ?: defaultMessage
            is FileNotFoundException -> cause.localizedMessage ?: defaultMessage
            else -> "$defaultMessage: ${cause.localizedMessage ?: cause.javaClass.simpleName}"
        }
        return PdfOperationResult.Error(cause = cause, userMessage = userMessage)
    }
}
