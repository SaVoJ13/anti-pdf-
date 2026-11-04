package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Реалізація [FormEngine] на основі бібліотеки PdfBox-Android (Apache-2.0).
 * Забезпечує читання інтерактивних полів форми AcroForm (Text, CheckBox, Choice/ComboBox)
 * та запис оновлених значень з безпечним збереженням для SAF.
 */
class PdfBoxFormEngine : FormEngine {

    override suspend fun extractFormFields(
        context: Context,
        sourceUri: Uri
    ): List<FormField> = withContext(Dispatchers.IO) {
        PdfBoxPageOperationsEngine.init(context)

        openInputStream(context, sourceUri).use { inStream ->
            val doc = PDDocument.load(inStream)
            try {
                val acroForm = doc.documentCatalog.acroForm ?: return@withContext emptyList()
                val fieldsList = mutableListOf<FormField>()

                val iterator = try {
                    acroForm.fieldTree.iterator()
                } catch (_: Throwable) {
                    acroForm.fields?.iterator() ?: emptyList<PDField>().iterator()
                }

                while (iterator.hasNext()) {
                    val field = try {
                        iterator.next()
                    } catch (_: Throwable) {
                        break
                    }
                    val fqName = field.fullyQualifiedName ?: continue
                    val partialName = field.partialName ?: fqName
                    val alternateTitle = field.alternateFieldName
                    val isReadOnly = field.isReadOnly

                    when (field) {
                        is PDTextField -> {
                            fieldsList.add(
                                FormField.Text(
                                    fullyQualifiedName = fqName,
                                    partialName = partialName,
                                    alternateTitle = alternateTitle,
                                    isReadOnly = isReadOnly,
                                    value = field.value ?: "",
                                    isMultiline = field.isMultiline
                                )
                            )
                        }
                        is PDCheckBox -> {
                            fieldsList.add(
                                FormField.CheckBox(
                                    fullyQualifiedName = fqName,
                                    partialName = partialName,
                                    alternateTitle = alternateTitle,
                                    isReadOnly = isReadOnly,
                                    isChecked = field.isChecked,
                                    onValue = field.onValue?.takeIf { it.isNotEmpty() } ?: "Yes"
                                )
                            )
                        }
                        is PDChoice -> {
                            val selected = field.value?.firstOrNull() ?: field.valueAsString?.takeIf { it.isNotEmpty() }
                            val isEditableCombo = field is PDComboBox && field.isEdit
                            fieldsList.add(
                                FormField.Choice(
                                    fullyQualifiedName = fqName,
                                    partialName = partialName,
                                    alternateTitle = alternateTitle,
                                    isReadOnly = isReadOnly,
                                    options = field.options ?: emptyList(),
                                    selectedValue = selected,
                                    isEditableCombo = isEditableCombo
                                )
                            )
                        }
                    }
                }

                fieldsList
            } finally {
                doc.close()
            }
        }
    }

    override suspend fun fillFormFields(
        context: Context,
        sourceUri: Uri,
        updates: List<FormFieldUpdate>,
        outputUri: Uri
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfBoxPageOperationsEngine.init(context)

            openInputStream(context, sourceUri).use { inStream ->
                val doc = PDDocument.load(inStream)
                try {
                    val totalPages = doc.numberOfPages
                    val acroForm = doc.documentCatalog.acroForm
                    if (acroForm == null && updates.isNotEmpty()) {
                        throw IllegalArgumentException("Документ не містить форми AcroForm для заповнення")
                    }

                    if (acroForm != null) {
                        for (update in updates) {
                            val field = acroForm.getField(update.fullyQualifiedName) ?: continue

                            when (field) {
                                is PDTextField -> {
                                    field.setValue(update.value.toString())
                                }
                                is PDCheckBox -> {
                                    val shouldCheck = isTruthy(update.value, field.onValue)
                                    if (shouldCheck) {
                                        try {
                                            field.check()
                                        } catch (_: Throwable) {
                                            val onVal = field.onValue?.takeIf { it.isNotEmpty() } ?: "Yes"
                                            field.setValue(onVal)
                                        }
                                    } else {
                                        try {
                                            field.unCheck()
                                        } catch (_: Throwable) {
                                            field.setValue(COSName.Off.name)
                                        }
                                    }
                                }
                                is PDChoice -> {
                                    field.setValue(update.value.toString())
                                }
                            }
                        }

                        try {
                            acroForm.needAppearances = true
                        } catch (_: Throwable) {
                            // Не критично
                        }

                        try {
                            acroForm.refreshAppearances()
                        } catch (_: Throwable) {
                            // Якщо не вдалося згенерувати AP через специфічні ресурси середовища
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
            mapToPdfError(cause, "Помилка заповнення форми PDF")
        }
    }

    private fun isTruthy(value: Any, onValue: String?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) ||
                    value.equals("yes", ignoreCase = true) ||
                    value.equals("1", ignoreCase = true) ||
                    value.equals("on", ignoreCase = true) ||
                    (!onValue.isNullOrEmpty() && value.equals(onValue, ignoreCase = true))
            else -> false
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
     * Безпечне збереження документа через тимчасовий файл (SAF in-place friendly).
     */
    private fun saveDocumentSafely(context: Context, doc: PDDocument, outputUri: Uri) {
        if (outputUri.scheme == "file") {
            val path = outputUri.path ?: throw IllegalArgumentException("Некоректний file URI: $outputUri")
            val targetFile = File(path)
            val parentDir = targetFile.parentFile ?: context.cacheDir
            parentDir.mkdirs()

            val tempFile = File.createTempFile("volum_form_", ".tmp", parentDir)
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
            val tempFile = File.createTempFile("volum_form_saf_", ".tmp", context.cacheDir)
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
