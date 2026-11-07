package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Реалізація [FormEngine] на основі бібліотеки PdfBox-Android (Apache-2.0).
 * Забезпечує читання інтерактивних полів форми AcroForm (Text, CheckBox, Choice/ComboBox)
 * та запис оновлених значень з безпечним збереженням для SAF.
 * Типи полів поза контрактом (радіо, кнопки, підпис) пропускаються без помилки.
 */
class PdfBoxFormEngine : FormEngine {

    override suspend fun extractFormFields(
        context: Context,
        sourceUri: Uri,
        password: String?
    ): List<FormField> = withContext(Dispatchers.IO) {
        PdfBoxPageOperationsEngine.init(context)

        val doc = PdfIo.loadDocument(context, sourceUri, password)
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

    override suspend fun fillFormFields(
        context: Context,
        sourceUri: Uri,
        updates: List<FormFieldUpdate>,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfBoxPageOperationsEngine.init(context)

            val doc = PdfIo.loadDocument(context, sourceUri, password)
            try {
                val totalPages = doc.numberOfPages
                FormFiller.apply(doc, updates)
                PdfIo.keepEncryption(doc, password)
                PdfIo.saveSafely(context, doc, outputUri)
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
            } finally {
                doc.close()
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            PdfIo.mapToPdfError(cause, "Помилка заповнення форми PDF")
        }
    }
}

/** Заповнення полів AcroForm у вже завантаженому документі (спільне для FormEngine і MarkupEngine). */
internal object FormFiller {

    /**
     * Застосовує оновлення до полів Text, CheckBox, Choice; радіо, кнопки, підпис
     * і невідомі імена пропускаються без помилки.
     */
    fun apply(doc: PDDocument, updates: List<FormFieldUpdate>) {
        val acroForm = doc.documentCatalog.acroForm
        if (acroForm == null) {
            if (updates.isNotEmpty()) {
                throw IllegalArgumentException("Документ не містить форми AcroForm для заповнення")
            }
            return
        }

        for (update in updates) {
            val field = acroForm.getField(update.fullyQualifiedName) ?: continue

            when (field) {
                is PDTextField -> setValueTolerant(field, update.value.toString()) { field.setValue(it) }
                is PDCheckBox -> {
                    if (isTruthy(update.value, field.onValue)) {
                        try {
                            field.check()
                        } catch (_: Throwable) {
                            field.setValue(field.onValue?.takeIf { it.isNotEmpty() } ?: "Yes")
                        }
                    } else {
                        try {
                            field.unCheck()
                        } catch (_: Throwable) {
                            field.setValue(COSName.Off.name)
                        }
                    }
                }
                is PDChoice -> setValueTolerant(field, update.value.toString()) { field.setValue(it) }
                else -> Unit // радіо, кнопки, підпис: поза контрактом
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

    /**
     * PDFBox ставить значення, а потім будує appearance шрифтом з DA поля; якщо в тому шрифті немає
     * символа (кирилиця в Helvetica), кидає IllegalArgumentException уже ПІСЛЯ запису значення.
     * Тоді значення лишається (appearance генерує двигун), а справжні помилки значення (наприклад,
     * варіант поза списком) передаються далі.
     */
    private fun setValueTolerant(field: PDField, value: String, set: (String) -> Unit) {
        try {
            set(value)
        } catch (e: IllegalArgumentException) {
            if (field.valueAsString != value) throw e
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
}
