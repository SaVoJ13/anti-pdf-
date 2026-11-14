package com.savoj.volumpdf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Паритет рядків у 4 мовах (uk, en, de, ru): однакові ключі, непорожні значення, однакові аргументи формату
 * (%1$s, %2$d …). XML розбирається як XML, а не шукається текстом.
 */
class StringsParityTest {

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun load(folder: String): Map<String, String> {
        val file = File(resDir, "$folder/strings.xml")
        assertTrue("нема ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            val name = n.attributes.getNamedItem("name").nodeValue
            assertTrue("дубль ключа $name у $folder", out.put(name, n.textContent) == null)
        }
        return out
    }

    private val base by lazy { load("values") }
    private val locales by lazy { mapOf("uk" to load("values-uk"), "de" to load("values-de"), "ru" to load("values-ru")) }

    @Test
    fun `every language has exactly the same keys as the default`() {
        locales.forEach { (lang, map) ->
            assertEquals("відсутні в $lang", emptySet<String>(), base.keys - map.keys)
            assertEquals("зайві в $lang", emptySet<String>(), map.keys - base.keys)
        }
    }

    @Test
    fun `no empty strings`() {
        (locales + ("en" to base)).forEach { (lang, map) ->
            val empty = map.filterValues { it.isBlank() }.keys
            assertTrue("порожні в $lang: $empty", empty.isEmpty())
        }
    }

    @Test
    fun `format arguments match across languages`() {
        val arg = Regex("%(\\d+)\\$[sdfx]")
        fun args(s: String) = arg.findAll(s).map { it.groupValues[1] }.toSortedSet()
        locales.forEach { (lang, map) ->
            val bad = base.filter { (k, v) -> map[k]?.let { args(it) != args(v) } ?: false }.keys
            assertTrue("різні аргументи формату в $lang: $bad", bad.isEmpty())
        }
    }

    @Test
    fun `new place-mode strings exist in all four languages`() {
        val keys = listOf(
            "action_redo", "action_add", "place_slot_action", "place_slot_select", "place_options", "place_options_close",
            "place_options_page", "place_width_label", "place_size_label", "place_straighten", "color_orange", "color_indigo",
            "color_purple", "color_gray", "color_custom", "place_custom_color_title", "place_text_size_menu",
            "place_text_color_toggle", "place_menu_reading_mode"
        )
        (locales + ("en" to base)).forEach { (lang, map) ->
            keys.forEach { assertTrue("$it відсутній у $lang", map.containsKey(it)) }
        }
    }
}
