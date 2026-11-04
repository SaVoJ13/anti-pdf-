package com.savoj.volumpdf.core.pdf.model

/**
 * Моделі для інтерактивних форм AcroForm (v0.3).
 */
sealed interface FormField {
    val fullyQualifiedName: String
    val partialName: String
    val alternateTitle: String? // Опис/підказка поля (TU запис)
    val isReadOnly: Boolean

    data class Text(
        override val fullyQualifiedName: String,
        override val partialName: String,
        override val alternateTitle: String?,
        override val isReadOnly: Boolean,
        val value: String,
        val isMultiline: Boolean
    ) : FormField

    data class CheckBox(
        override val fullyQualifiedName: String,
        override val partialName: String,
        override val alternateTitle: String?,
        override val isReadOnly: Boolean,
        val isChecked: Boolean,
        val onValue: String = "Yes"
    ) : FormField

    data class Choice(
        override val fullyQualifiedName: String,
        override val partialName: String,
        override val alternateTitle: String?,
        override val isReadOnly: Boolean,
        val options: List<String>,
        val selectedValue: String?,
        val isEditableCombo: Boolean
    ) : FormField
}

data class FormFieldUpdate(
    val fullyQualifiedName: String,
    val value: Any // String для Text/Choice, Boolean для CheckBox
)
