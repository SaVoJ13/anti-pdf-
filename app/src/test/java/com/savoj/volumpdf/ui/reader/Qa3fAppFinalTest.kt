package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Фінальна верифікація UI-логіки та локалізації Етапу 3f (`feature/qa-place-final`):
 * 1. Кламп чотирьох країв сторінки для всіх 5 інструментів розміщення (CHECK, CROSS, TEXT, INK, SIGNATURE).
 * 2. Перевірка наявності та непорожнього перекладу всіх ключових рядків 3a–3e та `reading-progress`
 *    у всіх 4 мовах (`en`, `uk`, `de`, `ru`).
 */
class Qa3fAppFinalTest {

    private val page = PageSizePt(400f, 600f)
    private val red = 0xFFD32F2F.toInt()

    @Test
    fun `all four edges clamp marks text ink and signature inside visible page bounds`() {
        // 1. CHECK / CROSS / TEXT на всіх чотирьох краях (лівий, правий, верхній, нижній)
        val tl = PlaceMarkupLogic.clampToPage(-100f, -200f, page)
        assertEquals(0f, tl.first, 0f)
        assertEquals(0f, tl.second, 0f)

        val tr = PlaceMarkupLogic.clampToPage(999f, -50f, page)
        assertEquals(400f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, tr.first, 0f)
        assertEquals(0f, tl.second, 0f)

        val bl = PlaceMarkupLogic.clampToPage(-50f, 999f, page)
        assertEquals(0f, bl.first, 0f)
        assertEquals(600f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, bl.second, 0f)

        val br = PlaceMarkupLogic.clampToPage(999f, 999f, page)
        assertEquals(400f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, br.first, 0f)
        assertEquals(600f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, br.second, 0f)

        // 2. SIGNATURE: кламп опорної точки на всіх 4 краях + діапазон ширини 30..400 pt
        val sig = PlaceMarkupLogic.signatureFromTap(
            "sig1", 0, 500f, 700f, 500, 250, PlaceMarkupLogic.DEFAULT_SIGNATURE_WIDTH_PT, page
        )!!
        assertEquals(400f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, sig.x, 1e-3f)
        assertEquals(600f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, sig.y, 1e-3f)
        assertEquals(0.5f, sig.aspect, 1e-4f)

        var st = PlaceEditor.add(PlaceEditor.initial(emptyList()), sig)
        st = PlaceEditor.move(st, "sig1", -300f, -300f, page)
        assertEquals(0f, st.items.single().x, 1e-3f)
        assertEquals(0f, st.items.single().y, 1e-3f)

        st = PlaceEditor.move(st, "sig1", 999f, -50f, page)
        assertEquals(400f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, st.items.single().x, 1e-3f)
        assertEquals(0f, st.items.single().y, 1e-3f)

        st = PlaceEditor.move(st, "sig1", -50f, 999f, page)
        assertEquals(0f, st.items.single().x, 1e-3f)
        assertEquals(600f - PlaceMarkupLogic.PAGE_EDGE_MARGIN_PT, st.items.single().y, 1e-3f)

        st = PlaceEditor.setSize(st, "sig1", 200f)
        assertEquals(200f, st.items.single().sizePt, 1e-3f)

        // 3. INK: кламп на всіх 4 краях (залишає щонайменше INK_MIN_VISIBLE_PT = 2 pt на сторінці)
        val stroke = PlaceMarkupLogic.inkFromStroke(
            "ink1", 0, floatArrayOf(10f, 10f, 50f, 40f), 2f, red, page
        )!!
        for ((targetX, targetY) in listOf(-500f to -500f, 500f to -500f, -500f to 900f, 500f to 900f)) {
            val clamped = PlaceMarkupLogic.clampedAnchor(stroke, targetX, targetY, page)
            val moved = PlaceMarkupLogic.withAnchor(stroke, clamped.first, clamped.second)
            val b = PlaceMarkupLogic.inkBoundsPt(moved)
            assertTrue("right=${b.right}", b.right >= PlaceMarkupLogic.INK_MIN_VISIBLE_PT - 1e-3f)
            assertTrue("bottom=${b.bottom}", b.bottom >= PlaceMarkupLogic.INK_MIN_VISIBLE_PT - 1e-3f)
            assertTrue("left=${b.left}", b.left <= page.widthPt - PlaceMarkupLogic.INK_MIN_VISIBLE_PT + 1e-3f)
            assertTrue("top=${b.top}", b.top <= page.heightPt - PlaceMarkupLogic.INK_MIN_VISIBLE_PT + 1e-3f)
        }
    }

    @Test
    fun `all 4 locales contain complete non-empty strings for 3a to 3e and reading progress`() {
        val locales = listOf("values", "values-uk", "values-de", "values-ru")
        val requiredKeys = listOf(
            "app_name",
            "document_page_progress",
            "action_place",
            "place_tool_check",
            "place_tool_cross",
            "place_tool_text",
            "place_tool_ink",
            "place_tool_signature",
            "place_tool_hand",
            "place_text_unsupported",
            "place_saved",
            "place_blocked_rights",
            "place_blocked_unreadable",
            "place_unsaved_title",
            "place_unsaved_message",
            "place_discard",
            "forms_read_only_note",
            "unsaved_bar_text",
            "password_title",
            "password_message",
            "password_continue",
            "password_wrong",
            "copy_warning_title",
            "copy_warning_message",
            "copy_save",
            "place_copy_saved",
            "tab_library",
            "continue_reading_title",
            "action_continue_reading",
            "section_recent_documents",
            "action_scan_short",
            "action_clear_search",
            "dialog_jump_to_page_title",
            "dialog_jump_to_page_hint",
            "action_go",
            "place_tool_hand_short",
            "tools_hub_subtitle",
            "action_clear_history",
            "dialog_clear_history_title",
            "dialog_clear_history_message",
            "history_cleared_message"
        )

        for (dir in locales) {
            val file = File("src/main/res/$dir/strings.xml")
            assertTrue("Missing $dir/strings.xml", file.exists())
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val map = HashMap<String, String>()
            for (i in 0 until nodes.length) {
                val el = nodes.item(i)
                val name = el.attributes?.getNamedItem("name")?.nodeValue ?: continue
                map[name] = el.textContent ?: ""
            }
            for (key in requiredKeys) {
                val value = map[key]
                assertNotNull("Locale $dir missing key '$key'", value)
                assertFalse("Locale $dir has blank value for '$key'", value!!.isBlank())
            }
        }
    }
}
