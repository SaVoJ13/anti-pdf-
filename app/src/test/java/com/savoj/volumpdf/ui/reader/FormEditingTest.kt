package com.savoj.volumpdf.ui.reader

import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormEditingTest {

    private val uri: Uri = org.mockito.Mockito.mock(Uri::class.java)

    private fun text(name: String, v: String, ro: Boolean = false) =
        FormField.Text(name, name, null, ro, v, isMultiline = false)

    private fun check(name: String, on: Boolean) = FormField.CheckBox(name, name, null, false, on)

    private fun choice(name: String, sel: String?, opts: List<String> = listOf("A", "B"), editable: Boolean = false) =
        FormField.Choice(name, name, null, false, opts, sel, editable)

    private val fields = listOf(
        text("name", "John"),
        text("імʼя", "Іван"),
        check("agree", false),
        choice("country", "A"),
        choice("empty", null),
        choice("combo", "x", editable = true),
        text("locked", "L", ro = true)
    )

    // --- значення з файла проти введеного ------------------------------------------------------

    @Test
    fun unchangedValuesDoNotEnterPendingState() {
        val entered = fields.associate { it.fullyQualifiedName to FormEditing.fileValue(it) }
        assertTrue(FormEditing.applyEntered(fields, emptyMap(), entered).isEmpty())
    }

    @Test
    fun onlyDifferingFieldsAreKept_andRevertingRemovesTheEntry() {
        val first = FormEditing.applyEntered(fields, emptyMap(), mapOf("name" to "Jane", "agree" to false, "country" to "A"))
        assertEquals(mapOf<String, Any>("name" to "Jane"), first)
        val second = FormEditing.applyEntered(fields, first, mapOf("agree" to true))
        assertEquals(mapOf<String, Any>("name" to "Jane", "agree" to true), second)
        // повернення до значення з файла прибирає запис
        val third = FormEditing.applyEntered(fields, second, mapOf("name" to "John"))
        assertEquals(mapOf<String, Any>("agree" to true), third)
        assertTrue(FormEditing.applyEntered(fields, third, mapOf("agree" to false)).isEmpty())
    }

    @Test
    fun readOnlyUnknownAndWrongTypedEntriesAreDropped() {
        val r = FormEditing.applyEntered(
            fields, emptyMap(),
            mapOf("locked" to "hack", "nope" to "x", "agree" to "true", "name" to true)
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun choiceWithoutSelectionEqualsEmptyStringFromFile() {
        // selectedValue = null у файлі і "" у шторці — те саме: запису немає
        assertTrue(FormEditing.applyEntered(fields, emptyMap(), mapOf("empty" to "")).isEmpty())
        assertEquals(mapOf<String, Any>("empty" to "B"), FormEditing.applyEntered(fields, emptyMap(), mapOf("empty" to "B")))
        // зняття вибору — "" (відрізняється від "A" з файла)
        assertEquals(mapOf<String, Any>("country" to ""), FormEditing.applyEntered(fields, emptyMap(), mapOf("country" to "")))
    }

    @Test
    fun effectiveValueShowsPendingOverFile() {
        val edits = mapOf<String, Any>("name" to "Jane", "agree" to true)
        assertEquals("Jane", FormEditing.effectiveValue(fields[0], edits))
        assertEquals("Іван", FormEditing.effectiveValue(fields[1], edits))
        assertEquals(true, FormEditing.effectiveValue(fields[2], edits))
        assertEquals("", FormEditing.effectiveValue(fields[4], edits))
        // запис хибного типу ігнорується
        assertEquals(false, FormEditing.effectiveValue(fields[2], mapOf("agree" to "yes")))
    }

    @Test
    fun pruneDropsEntriesThatNowMatchTheFileOrHaveNoField() {
        val edits = mapOf<String, Any>("name" to "John", "agree" to true, "gone" to "z")
        assertEquals(mapOf<String, Any>("agree" to true), FormEditing.prune(fields, edits))
    }

    @Test
    fun cyrillicAndMultilineTextIsKeptVerbatim() {
        val multi = FormField.Text("m", "m", null, false, "", isMultiline = true)
        val value = "Привіт, світе!\nДругий рядок\r\n  з відступом ЄЇҐ "
        val r = FormEditing.applyEntered(listOf(multi), emptyMap(), mapOf("m" to value))
        assertEquals(value, r["m"])
        assertEquals(value, FormEditing.toUpdates(listOf(multi), r).single().value)
    }

    @Test
    fun choiceOptionsNonEditableGetNoneEntryEditableDoesNot() {
        assertEquals(listOf("", "A", "B"), FormEditing.choiceOptionsWithNone(choice("c", "A")))
        assertEquals(listOf("A", "B"), FormEditing.choiceOptionsWithNone(choice("c", "A", editable = true)))
    }

    // --- порядок і типи FormFieldUpdate ----------------------------------------------------------

    @Test
    fun updatesFollowFileOrderWithBooleanForCheckBoxAndStringForTextAndChoice() {
        val edits = linkedMapOf<String, Any>(
            "country" to "", "agree" to true, "імʼя" to "Петро", "combo" to "вільний"
        )
        val updates = FormEditing.toUpdates(fields, edits)
        assertEquals(
            listOf(
                FormFieldUpdate("імʼя", "Петро"),
                FormFieldUpdate("agree", true),
                FormFieldUpdate("country", ""),
                FormFieldUpdate("combo", "вільний")
            ),
            updates
        )
        assertTrue(updates[1].value is Boolean)
        assertTrue(updates[0].value is String && updates[2].value is String)
    }

    @Test
    fun toMapAndToListKeepOrder() {
        val list = listOf(FormFieldUpdate("b", "1"), FormFieldUpdate("a", true))
        assertEquals(list, FormEditing.toList(FormEditing.toMap(list)))
    }

    // --- «Зберегти» -------------------------------------------------------------------------------

    @Test
    fun hasUnsavedCountsObjectsOrForms() {
        assertFalse(FormEditing.hasUnsaved(0, emptyMap()))
        assertTrue(FormEditing.hasUnsaved(1, emptyMap()))
        assertTrue(FormEditing.hasUnsaved(0, mapOf("a" to "b")))
    }

    @Test
    fun saveSendsObjectsAndFormUpdatesInOneCall() = runBlocking {
        var calls = 0
        var gotMarkups: List<String>? = null
        var gotForms: List<FormFieldUpdate>? = null
        val req = SaveLogic.request(listOf("mark", "text"), fields, mapOf("agree" to true, "name" to "Jane"))
        val out = SaveLogic.run(req) { m, f ->
            calls++; gotMarkups = m; gotForms = f
            PdfOperationResult.Success(uri, 1)
        }
        assertEquals(SaveOutcome.Saved, out)
        assertEquals(1, calls)
        assertEquals(listOf("mark", "text"), gotMarkups)
        assertEquals(listOf(FormFieldUpdate("name", "Jane"), FormFieldUpdate("agree", true)), gotForms)
    }

    @Test
    fun formsOnlyAndObjectsOnlySavesWork_andNothingToSaveSkipsTheEngine() = runBlocking {
        var calls = 0
        val ok: suspend (List<String>, List<FormFieldUpdate>) -> PdfOperationResult = { _, _ ->
            calls++; PdfOperationResult.Success(uri, 1)
        }
        assertEquals(SaveOutcome.Saved, SaveLogic.run(SaveLogic.request(emptyList<String>(), fields, mapOf("agree" to true)), ok))
        assertEquals(SaveOutcome.Saved, SaveLogic.run(SaveLogic.request(listOf("m"), fields, emptyMap()), ok))
        assertEquals(SaveOutcome.NothingToSave, SaveLogic.run(SaveLogic.request(emptyList<String>(), fields, emptyMap()), ok))
        assertEquals(2, calls)
    }

    @Test
    fun errorKeepsStateAndCarriesUserMessageWithFieldName() = runBlocking {
        val formEdits = mapOf<String, Any>("country" to "Z")
        val items = listOf("mark")
        val req = SaveLogic.request(items, fields, formEdits)
        val out = SaveLogic.run(req) { _, _ ->
            PdfOperationResult.Error(IllegalArgumentException("x"), "Поле «country»: значення «Z» немає у списку")
        }
        assertEquals(SaveOutcome.Failed("Поле «country»: значення «Z» немає у списку"), out)
        // ввід нічого не «з'їв»: ті самі дані дають той самий запит для повторної спроби
        val again = SaveLogic.request(items, fields, formEdits)
        assertEquals(req.markups, again.markups)
        assertEquals(req.formUpdates, again.formUpdates)
        assertEquals(1, again.formUpdates.size)
    }

    @Test
    fun gateDependsOnWhatIsBeingSaved() {
        val g = PlaceMarkupLogic.Gate.ALLOWED
        assertEquals(g, SaveLogic.gate(false, true, true, hasItems = true, hasForms = true))
        assertEquals(PlaceMarkupLogic.Gate.ENCRYPTED, SaveLogic.gate(true, true, true, true, true))
        assertEquals(PlaceMarkupLogic.Gate.UNREADABLE, SaveLogic.gate(null, null, null, true, false))
        // форми без прав на markup, але з canFillForms — дозволено
        assertEquals(g, SaveLogic.gate(false, false, true, hasItems = false, hasForms = true))
        assertEquals(PlaceMarkupLogic.Gate.NO_MARKUP_RIGHTS, SaveLogic.gate(false, false, true, hasItems = true, hasForms = true))
        assertEquals(PlaceMarkupLogic.Gate.NO_MARKUP_RIGHTS, SaveLogic.gate(false, true, false, hasItems = false, hasForms = true))
    }
}
