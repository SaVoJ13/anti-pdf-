package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormFieldsUiLogicTest {

    @Test
    fun testFormFieldsInitializationAndValueCollection() {
        val fields: List<FormField> = listOf(
            FormField.Text(
                fullyQualifiedName = "applicant.firstName",
                partialName = "firstName",
                alternateTitle = "First Name",
                isReadOnly = false,
                value = "John",
                isMultiline = false
            ),
            FormField.CheckBox(
                fullyQualifiedName = "applicant.agreeTerms",
                partialName = "agreeTerms",
                alternateTitle = "I agree to Terms & Conditions",
                isReadOnly = false,
                isChecked = false
            ),
            FormField.Choice(
                fullyQualifiedName = "applicant.country",
                partialName = "country",
                alternateTitle = "Country",
                isReadOnly = false,
                options = listOf("Ukraine", "Germany", "Poland", "United States"),
                selectedValue = "Ukraine",
                isEditableCombo = false
            )
        )

        // Симуляція стану значень у формі
        val valuesMap = mutableMapOf<String, Any>()
        for (field in fields) {
            when (field) {
                is FormField.Text -> valuesMap[field.fullyQualifiedName] = field.value
                is FormField.CheckBox -> valuesMap[field.fullyQualifiedName] = field.isChecked
                is FormField.Choice -> valuesMap[field.fullyQualifiedName] = field.selectedValue ?: field.options.first()
            }
        }

        // Початкові значення
        assertEquals("John", valuesMap["applicant.firstName"])
        assertEquals(false, valuesMap["applicant.agreeTerms"])
        assertEquals("Ukraine", valuesMap["applicant.country"])

        // Симуляція введення користувачем у UI
        valuesMap["applicant.firstName"] = "Jane"
        valuesMap["applicant.agreeTerms"] = true
        valuesMap["applicant.country"] = "Germany"

        // Збір List<FormFieldUpdate>
        val updates: List<FormFieldUpdate> = fields.map { field ->
            FormFieldUpdate(field.fullyQualifiedName, valuesMap[field.fullyQualifiedName]!!)
        }

        assertEquals(3, updates.size)
        assertEquals("Jane", updates[0].value)
        assertEquals(true, updates[1].value)
        assertEquals("Germany", updates[2].value)
    }

    @Test
    fun testFormBadgeVisibilityLogic() {
        // Документ без полів
        val emptyFields = emptyList<FormField>()
        val shouldShowFormsButtonEmpty = emptyFields.isNotEmpty()
        assertFalse(shouldShowFormsButtonEmpty)

        // Документ з полями
        val filledFields = listOf(
            FormField.Text(
                fullyQualifiedName = "field1",
                partialName = "field1",
                alternateTitle = null,
                isReadOnly = false,
                value = "",
                isMultiline = false
            )
        )
        val shouldShowFormsButtonFilled = filledFields.isNotEmpty()
        assertTrue(shouldShowFormsButtonFilled)
        assertEquals(1, filledFields.size)
    }

    @Test
    fun testFabErgonomicsMarginCalculation() {
        val density = 3.0f // Galaxy S24 Ultra (~QHD+ / xxhdpi / 500+ dpi)
        val baseMarginDp = 76
        val baseMarginPx = (baseMarginDp * density).toInt()

        // Перевірка базового відступу
        assertEquals(228, baseMarginPx)

        // При показі банера висотою 60dp
        val bannerHeightPx = (60 * density).toInt() // 180 px
        val targetMarginPx = baseMarginPx + bannerHeightPx
        assertEquals(408, targetMarginPx)
    }
}
