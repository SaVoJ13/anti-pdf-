package com.savoj.volumpdf.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test

class LicensesDialogHelperTest {

    @Test
    fun licensesTextContainsAllRequiredComponentsAndLicenses() {
        val text = LicensesDialogHelper.licensesText
        assertTrue("Текст ліцензій не має бути порожнім", text.isNotBlank())
        assertTrue("Має містити androidx.pdf", text.contains("androidx.pdf:pdf-viewer-fragment"))
        assertTrue("Має містити PdfBox-Android", text.contains("PdfBox-Android"))
        assertTrue("Має містити Roboto Font", text.contains("Roboto Font"))
        assertTrue("Має містити Apache-2.0", text.contains("Apache License, Version 2.0"))
        assertTrue("Має містити SIL OFL 1.1", text.contains("SIL Open Font License, Version 1.1"))
        assertTrue("Має містити згадку Bouncy Castle", text.contains("Bouncy Castle"))
        assertTrue("Має містити KotlinX Coroutines", text.contains("KotlinX Coroutines"))
    }
}
