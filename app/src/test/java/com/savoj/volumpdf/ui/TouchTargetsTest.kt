package com.savoj.volumpdf.ui

import com.savoj.volumpdf.util.Haptics
import com.savoj.volumpdf.ui.reader.SwatchView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * P0-4: інтерактивні елементи мають ціль натискання не менше 48 dp (висота/ширина або minHeight/minWidth).
 * Розмітка розбирається як XML. P1-7: відгук вібрацією підбирається за рівнем API.
 */
class TouchTargetsTest {

    private val layoutDir: File = listOf(File("src/main/res/layout"), File("app/src/main/res/layout")).first { it.isDirectory }

    private fun dp(value: String?): Float? = value?.takeIf { it.endsWith("dp") }?.removeSuffix("dp")?.toFloatOrNull()

    private fun find(layout: String, id: String): Element {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(layoutDir, "$layout.xml"))
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val e = all.item(i) as Element
            if (e.getAttributeNS("http://schemas.android.com/apk/res/android", "id") == "@+id/$id") return e
        }
        error("нема @+id/$id у $layout")
    }

    private fun side(e: Element, size: String, min: String): Float {
        val ns = "http://schemas.android.com/apk/res/android"
        val explicit = dp(e.getAttributeNS(ns, size))
        val minimum = dp(e.getAttributeNS(ns, min))
        return maxOf(explicit ?: 0f, minimum ?: 0f)
    }

    private fun assertTarget(layout: String, id: String) {
        val e = find(layout, id)
        val h = side(e, "layout_height", "minHeight")
        val w = side(e, "layout_width", "minWidth")
        val wrapW = e.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_width").let { it == "wrap_content" || it == "match_parent" || it.endsWith("dp") }
        assertTrue("$layout/$id: висота $h < 48", h >= 48f)
        // wrap_content/match_parent без minWidth: ширину задає вміст, але для кнопок-іконок ширина має бути заданою
        if (e.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_width").endsWith("dp")) {
            assertTrue("$layout/$id: ширина $w < 48", w >= 48f)
        }
        assertTrue("$layout/$id: layout_width не задано або невідоме значення", wrapW)
    }

    @Test
    fun `reader page pill buttons are at least 48dp`() {
        listOf("btnHudPage", "btnHudZoom", "btnHudSearch").forEach { assertTarget("activity_reader", it) }
    }

    @Test
    fun `reader edit fab is a standard 56dp target`() {
        // М1: FloatingActionButton fabSize=normal = 56 dp (wrap_content), не нижче 48 dp
        val e = find("activity_reader", "fabReaderEdit")
        assertEquals("normal", e.getAttributeNS("http://schemas.android.com/apk/res-auto", "fabSize"))
    }

    @Test
    fun `recents row and clear button are at least 48dp`() {
        assertTarget("item_recent_document", "ivFavorite")
        assertTarget("item_recent_document", "ivMoreMenu")
        assertTarget("fragment_recents", "btnClearHistory")
    }

    @Test
    fun `organizer and selected file buttons are at least 48dp`() {
        assertTarget("item_page_organizer", "btnDelete")
        assertTarget("item_page_organizer", "btnRotate")
        assertTarget("item_selected_file", "btnRemoveFile")
    }

    @Test
    fun `text toolbar buttons are at least 48dp`() {
        listOf(
            "btnKbToolPen", "btnKbToolCheck", "btnKbToolText", "btnTextColor", "btnTextBgColor", "btnTextStyle",
            "btnTextSize", "btnKbSizeMinus", "btnTextUndo", "btnTextRedo", "btnTextConfirm", "btnTextCancel",
            "btnTextBoxResize",
        ).forEach { assertTarget("dialog_place_text", it) }
    }

    @Test
    fun `compact swatches keep a 48dp accessibility target`() {
        assertEquals(48, SwatchView.minimumTargetPx(1f))
        assertEquals(96, SwatchView.minimumTargetPx(2f))
    }

    @Test
    fun `haptic constants differ by purpose and fall back on old api levels`() {
        val old = 28
        val new = 34
        assertNotEquals(Haptics.constantFor(Haptics.Kind.TICK, new), Haptics.constantFor(Haptics.Kind.REJECT, new))
        assertNotEquals(Haptics.constantFor(Haptics.Kind.CONFIRM, new), Haptics.constantFor(Haptics.Kind.REJECT, new))
        // minSdk 28: CONFIRM/REJECT (API 30) не використовуються
        assertNotEquals(android.view.HapticFeedbackConstants.CONFIRM, Haptics.constantFor(Haptics.Kind.CONFIRM, old))
        assertNotEquals(android.view.HapticFeedbackConstants.REJECT, Haptics.constantFor(Haptics.Kind.REJECT, old))
        assertEquals(android.view.HapticFeedbackConstants.CONFIRM, Haptics.constantFor(Haptics.Kind.CONFIRM, new))
    }
}
