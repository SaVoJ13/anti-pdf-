package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.model.DocumentEditability
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.ui.reader.EditAccessModel.FormsMode
import com.savoj.volumpdf.ui.reader.EditAccessModel.SaveTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditAccessTest {

    private fun ed(enc: Boolean, markup: Boolean = true, forms: Boolean = true) = DocumentEditability(enc, markup, forms)

    // --- resolve: усі вихідні стани ---

    @Test fun unencryptedIsOpen() {
        assertEquals(EditAccess.Open(true, true), EditAccessModel.resolve(ed(false), passwordEntered = false, viewerOpened = true))
        assertEquals(EditAccess.Open(true, true), EditAccessModel.resolve(ed(false), passwordEntered = false, viewerOpened = false))
    }

    @Test fun encryptedWithEmptyPasswordIsRestrictedAndRightsAreIndependent() {
        assertEquals(EditAccess.Restricted(true, true), EditAccessModel.resolve(ed(true), false, true))
        assertEquals(EditAccess.Restricted(false, true), EditAccessModel.resolve(ed(true, markup = false), false, true))
        assertEquals(EditAccess.Restricted(true, false), EditAccessModel.resolve(ed(true, forms = false), false, true))
        assertEquals(EditAccess.Restricted(false, false), EditAccessModel.resolve(ed(true, false, false), false, true))
    }

    @Test fun encryptedOpenedWithPasswordIsRestricted() {
        assertEquals(EditAccess.Restricted(true, true), EditAccessModel.resolve(ed(true), passwordEntered = true, viewerOpened = true))
    }

    @Test fun nullWithoutPasswordAfterViewerOpenedMeansPasswordRequired() {
        assertEquals(EditAccess.PasswordRequired, EditAccessModel.resolve(null, passwordEntered = false, viewerOpened = true))
    }

    @Test fun nullWithPasswordMeansWrongPassword() {
        assertEquals(EditAccess.WrongPassword, EditAccessModel.resolve(null, passwordEntered = true, viewerOpened = true))
        assertEquals(EditAccess.WrongPassword, EditAccessModel.resolve(null, passwordEntered = true, viewerOpened = false))
    }

    @Test fun nullWithoutPasswordAndViewerNotOpenedIsUnreadable() {
        assertEquals(EditAccess.Unreadable, EditAccessModel.resolve(null, passwordEntered = false, viewerOpened = false))
    }

    @Test fun needsPasswordOnlyForPasswordStates() {
        assertTrue(EditAccessModel.needsPassword(EditAccess.PasswordRequired))
        assertTrue(EditAccessModel.needsPassword(EditAccess.WrongPassword))
        assertFalse(EditAccessModel.needsPassword(EditAccess.Unreadable))
        assertFalse(EditAccessModel.needsPassword(EditAccess.Open(true, true)))
        assertFalse(EditAccessModel.needsPassword(EditAccess.Restricted(true, true)))
    }

    // --- права ---

    @Test fun rightsAccessors() {
        assertTrue(EditAccessModel.canMarkup(EditAccess.Restricted(true, false)))
        assertFalse(EditAccessModel.canFillForms(EditAccess.Restricted(true, false)))
        assertFalse(EditAccessModel.canMarkup(EditAccess.PasswordRequired))
        assertFalse(EditAccessModel.canFillForms(EditAccess.Unreadable))
    }

    @Test fun formsModeByRights() {
        assertEquals(FormsMode.EDITABLE, EditAccessModel.formsMode(EditAccess.Open(true, true)))
        assertEquals(FormsMode.EDITABLE, EditAccessModel.formsMode(EditAccess.Restricted(false, true)))
        assertEquals(FormsMode.READ_ONLY, EditAccessModel.formsMode(EditAccess.Restricted(true, false)))
        assertEquals(FormsMode.UNAVAILABLE, EditAccessModel.formsMode(EditAccess.WrongPassword))
    }

    // --- «той самий URI чи нова копія» ---

    @Test fun saveTargetSameUriForUnencryptedNewCopyForEncrypted() {
        assertEquals(SaveTarget.SAME_URI, EditAccessModel.saveTarget(EditAccess.Open(true, true), hasItems = true, hasForms = true))
        assertEquals(SaveTarget.NEW_COPY, EditAccessModel.saveTarget(EditAccess.Restricted(true, true), true, true))
        assertEquals(SaveTarget.NEW_COPY, EditAccessModel.saveTarget(EditAccess.Restricted(true, true), true, false))
    }

    @Test fun saveTargetDeniedByAuthorRights() {
        assertEquals(SaveTarget.DENIED, EditAccessModel.saveTarget(EditAccess.Restricted(false, true), hasItems = true, hasForms = false))
        assertEquals(SaveTarget.NEW_COPY, EditAccessModel.saveTarget(EditAccess.Restricted(false, true), hasItems = false, hasForms = true))
        assertEquals(SaveTarget.DENIED, EditAccessModel.saveTarget(EditAccess.Restricted(true, false), hasItems = false, hasForms = true))
        assertEquals(SaveTarget.NEW_COPY, EditAccessModel.saveTarget(EditAccess.Restricted(true, false), hasItems = true, hasForms = false))
        assertEquals(SaveTarget.DENIED, EditAccessModel.saveTarget(EditAccess.Open(false, true), hasItems = true, hasForms = false))
    }

    @Test fun saveTargetUnavailableWithoutAccess() {
        for (a in listOf(EditAccess.PasswordRequired, EditAccess.WrongPassword, EditAccess.Unreadable)) {
            assertEquals(SaveTarget.UNAVAILABLE, EditAccessModel.saveTarget(a, true, true))
        }
    }

    // --- пароль не потрапляє в серіалізований стан ---

    @Test fun passwordIsNotPartOfPendingCodec() {
        val secret = "S3cr3t-password-XYZ"
        // Кодек не має параметра пароля; значення полів — лише дані користувача.
        val bytes = PendingCodec.encode("content://doc/1", emptyList(), listOf(FormFieldUpdate("f", "звичайне значення")))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(secret))
        val params = PendingCodec::class.java.methods.filter { it.name == "encode" }.flatMap { it.parameterTypes.toList() }
        assertTrue(params.none { it == CharArray::class.java })
    }

    @Test fun viewModelNeverStoresPasswordInSavedStateHandle() {
        val src = File("src/main/java/com/savoj/volumpdf/ui/reader/ReaderViewModel.kt")
        assertTrue("source not found: ${src.absolutePath}", src.isFile)
        val lines = src.readLines()
        // жоден рядок не поєднує savedStateHandle і пароль; ключі стану не містять слів про пароль
        assertTrue(lines.none { it.contains("savedStateHandle", true) && it.contains("password", true) })
        assertTrue(lines.none { it.contains("const val") && it.contains("KEY_") && it.contains("pass", true) })
        // сам пароль — звичайне поле в пам'яті
        assertTrue(lines.any { it.contains("var documentPassword") })
    }

    @Test fun pendingStoreNeverMentionsPasswordAndActivityDoesNotLeakIt() {
        for (name in listOf("PendingStore.kt", "FormEditing.kt")) {
            val text = File("src/main/java/com/savoj/volumpdf/ui/reader/$name").readText()
            assertFalse("$name must not mention password", text.contains("password", true))
        }
        val activity = File("src/main/java/com/savoj/volumpdf/ui/reader/ReaderActivity.kt").readLines()
        // пароль не потрапляє в Bundle, лог чи інтент
        val risky = activity.filter { l ->
            l.contains("password", true) &&
                (l.contains("outState") || l.contains("Log.") || l.contains("putExtra") || l.contains("putString"))
        }
        assertTrue("password leaks: $risky", risky.isEmpty())
    }

    @Test fun copyNameIsNameEditedPdf() {
        assertEquals("report_edited.pdf", CopyNaming.suggestedName("report.pdf"))
        assertEquals("Звіт_edited.pdf", CopyNaming.suggestedName("Звіт.PDF"))
        assertEquals("scan_edited.pdf", CopyNaming.suggestedName("scan"))
        assertEquals("document_edited.pdf", CopyNaming.suggestedName(null))
        assertEquals("document_edited.pdf", CopyNaming.suggestedName(".pdf"))
    }
}
