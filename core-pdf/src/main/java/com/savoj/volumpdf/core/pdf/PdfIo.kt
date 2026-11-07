package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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

    /** Завантажує документ (PDFBox читає потік повністю, тому потік закривається тут). */
    fun loadDocument(context: Context, uri: Uri, password: String?): PDDocument =
        openInputStream(context, uri).use { stream ->
            if (password != null) PDDocument.load(stream, password) else PDDocument.load(stream)
        }

    /**
     * PDFBox не зберігає завантажений зашифрований документ без політики захисту. Щоб не знімати захист
     * мовчки, документ шифрується знову: той самий пароль (власника й користувача), ті самі дозволи й довжина ключа.
     */
    fun keepEncryption(doc: PDDocument, password: String?) {
        if (!doc.isEncrypted) return
        val pw = password ?: ""
        val keyLength = doc.encryption?.length?.takeIf { it == 40 || it == 128 || it == 256 } ?: 128
        val policy = StandardProtectionPolicy(pw, pw, doc.currentAccessPermission)
        policy.encryptionKeyLength = keyLength
        policy.isPreferAES = keyLength >= 128
        doc.protect(policy)
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
            is InvalidPasswordException -> "Невірний пароль для захищеного PDF-документа"
            is IllegalArgumentException -> cause.localizedMessage ?: defaultMessage
            is IndexOutOfBoundsException -> cause.localizedMessage ?: defaultMessage
            is FileNotFoundException -> cause.localizedMessage ?: defaultMessage
            else -> "$defaultMessage: ${cause.localizedMessage ?: cause.javaClass.simpleName}"
        }
        return PdfOperationResult.Error(cause = cause, userMessage = userMessage)
    }
}
