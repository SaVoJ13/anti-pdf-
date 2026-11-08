package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult

/**
 * Логіка незбережених значень форм без Android: значення з файла проти введеного, список для `applyEdits`,
 * підсумок збереження. Незбережені зміни форм НЕ входять в історію undo (undo лише для об'єктів).
 */
object FormEditing {

    /** Значення поля у файлі в тому типі, що йде в `FormFieldUpdate`: Boolean для CheckBox, String для Text/Choice. */
    fun fileValue(field: FormField): Any = when (field) {
        is FormField.Text -> field.value
        is FormField.CheckBox -> field.isChecked
        is FormField.Choice -> field.selectedValue ?: "" // null = немає вибору; "" знімає вибір
    }

    /** Поле можна змінювати. */
    fun isEditable(field: FormField, canFillForms: Boolean): Boolean = canFillForms && !field.isReadOnly

    /** Значення, яке показує шторка: незбережене, якщо є (і воно потрібного типу), інакше з файла. */
    fun effectiveValue(field: FormField, edits: Map<String, Any>): Any {
        val edited = edits[field.fullyQualifiedName]
        val ok = when (field) {
            is FormField.CheckBox -> edited is Boolean
            else -> edited is String
        }
        return if (ok) edited!! else fileValue(field)
    }

    /**
     * Нові незбережені зміни після введення: [entered] (ім'я → значення) накладається на [previous].
     * У результат потрапляють лише поля, що ВІДРІЗНЯЮТЬСЯ від файла; повернення до значення з файла прибирає запис;
     * поля, яких немає у файлі, або лише для читання, відкидаються. Порядок — як у [fields] (порядок файла).
     */
    fun applyEntered(fields: List<FormField>, previous: Map<String, Any>, entered: Map<String, Any>): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        for (field in fields) {
            if (field.isReadOnly) continue
            val name = field.fullyQualifiedName
            val value = if (entered.containsKey(name)) entered[name]!! else previous[name]
            if (value == null) continue
            val typed: Any = when (field) {
                is FormField.CheckBox -> value as? Boolean ?: continue
                else -> value as? String ?: continue
            }
            if (typed != fileValue(field)) result[name] = typed
        }
        return result
    }

    /** Лишає лише ті записи, що досі відрізняються від (можливо, оновленого) файла й мають поле. */
    fun prune(fields: List<FormField>, edits: Map<String, Any>): Map<String, Any> =
        applyEntered(fields, edits, emptyMap())

    /** Список для `MarkupEngine.applyEdits`: порядок полів файла; Boolean для CheckBox, String для Text/Choice. */
    fun toUpdates(fields: List<FormField>, edits: Map<String, Any>): List<FormFieldUpdate> =
        fields.mapNotNull { f ->
            val v = edits[f.fullyQualifiedName] ?: return@mapNotNull null
            when (f) {
                is FormField.CheckBox -> (v as? Boolean)?.let { FormFieldUpdate(f.fullyQualifiedName, it) }
                else -> (v as? String)?.let { FormFieldUpdate(f.fullyQualifiedName, it) }
            }
        }

    /** Те саме зі збереженого списку (після відновлення з файла, поки поля ще не прочитано): порядок збережено. */
    fun toMap(updates: List<FormFieldUpdate>): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        for (u in updates) m[u.fullyQualifiedName] = u.value
        return m
    }

    fun toList(edits: Map<String, Any>): List<FormFieldUpdate> = edits.map { FormFieldUpdate(it.key, it.value) }

    /** Варіанти для нередагованого Choice: «не вибрано» (дає "") + список. Редагований combo — вільний ввід. */
    fun choiceOptionsWithNone(field: FormField.Choice): List<String> =
        if (field.isEditableCombo) field.options else listOf("") + field.options.filter { it.isNotEmpty() }

    /** Чи є що зберігати: об'єкти АБО форми. */
    fun hasUnsaved(itemCount: Int, formEdits: Map<String, Any>): Boolean = itemCount > 0 || formEdits.isNotEmpty()
}

/** Підсумок «Зберегти» для UI: стан змінюється лише при [Saved]; при [Failed] нічого не губиться. */
sealed interface SaveOutcome {
    object NothingToSave : SaveOutcome
    object Saved : SaveOutcome
    data class Failed(val message: String) : SaveOutcome
}

/** Єдине збереження: один виклик [apply] з усіма об'єктами й змінами форм (formUpdates спершу — це робить рушій). */
object SaveLogic {
    /**
     * Чи можна зберігати: зашифровані — ні (до 3e); об'єкти потребують canMarkup, зміни форм — canFillForms.
     * Будь-яка з прав null (документ не відкрито) = UNREADABLE.
     */
    fun gate(
        isEncrypted: Boolean?, canMarkup: Boolean?, canFillForms: Boolean?, hasItems: Boolean, hasForms: Boolean
    ): PlaceMarkupLogic.Gate = when {
        isEncrypted == null || canMarkup == null || canFillForms == null -> PlaceMarkupLogic.Gate.UNREADABLE
        isEncrypted -> PlaceMarkupLogic.Gate.ENCRYPTED
        hasItems && !canMarkup -> PlaceMarkupLogic.Gate.NO_MARKUP_RIGHTS
        hasForms && !canFillForms -> PlaceMarkupLogic.Gate.NO_MARKUP_RIGHTS
        else -> PlaceMarkupLogic.Gate.ALLOWED
    }

    class Request<M>(val markups: List<M>, val formUpdates: List<FormFieldUpdate>)

    fun <M> request(items: List<M>, fields: List<FormField>, formEdits: Map<String, Any>): Request<M> =
        Request(items, FormEditing.toUpdates(fields, formEdits))

    suspend fun <M> run(
        request: Request<M>,
        apply: suspend (markups: List<M>, formUpdates: List<FormFieldUpdate>) -> PdfOperationResult
    ): SaveOutcome {
        if (request.markups.isEmpty() && request.formUpdates.isEmpty()) return SaveOutcome.NothingToSave
        return when (val r = apply(request.markups, request.formUpdates)) {
            is PdfOperationResult.Success -> SaveOutcome.Saved
            is PdfOperationResult.Error -> SaveOutcome.Failed(r.userMessage)
            is PdfOperationResult.Progress -> SaveOutcome.Failed("")
        }
    }
}
