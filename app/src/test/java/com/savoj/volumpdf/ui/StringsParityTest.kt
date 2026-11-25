package com.savoj.volumpdf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Паритет рядків у 5 мовах (uk, en, de, ru, es): однакові ключі, непорожні значення, однакові аргументи формату
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
    private val locales by lazy { mapOf(
        "uk" to load("values-uk"), "de" to load("values-de"), "ru" to load("values-ru"), "es" to load("values-es")
    ) }

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

    /** Специфікатори формату разом з типом: `%1$s`, `%d`, `%%`. Порядок аргументів може відрізнятись, склад ні. */
    private val spec = Regex("%(?:(\\d+)\\$)?([sdfx%])")
    private fun specs(s: String) = spec.findAll(s).map { "${it.groupValues[1]}:${it.groupValues[2]}" }.sorted().toList()

    @Test
    fun `format arguments match across languages in count and type`() {
        locales.forEach { (lang, map) ->
            val bad = base.filter { (k, v) -> map[k]?.let { specs(it) != specs(v) } ?: false }.keys
            assertTrue("різні специфікатори формату в $lang: $bad", bad.isEmpty())
        }
    }

    @Test
    fun `html tags and markup are preserved`() {
        val tag = Regex("</?[a-zA-Z]+[^>]*>")
        locales.forEach { (lang, map) ->
            val bad = base.filter { (k, v) -> map[k]?.let { tag.findAll(it).map { m -> m.value }.toList() != tag.findAll(v).map { m -> m.value }.toList() } ?: false }.keys
            assertTrue("різні HTML-теги в $lang: $bad", bad.isEmpty())
        }
    }

    @Test
    fun `app name stays Anti PDF in every language`() {
        (locales + ("en" to base)).forEach { (lang, map) -> assertEquals("app_name у $lang", "Anti PDF", map["app_name"]) }
    }

    @Test
    fun `locales_config lists exactly the five shipped languages and each has a resource folder`() {
        val file = File(resDir, "xml/locales_config.xml")
        assertTrue("нема ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("locale")
        val declared = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }.toSet()
        assertEquals(setOf("en", "uk", "de", "ru", "es"), declared)
        listOf("uk", "de", "ru", "es").forEach { assertTrue("нема values-$it", File(resDir, "values-$it/strings.xml").isFile) }
        // Мова за замовчуванням (values/) англійська: резервна мова для непідтримуваних системних мов.
        assertEquals("Open PDF", base["action_open_pdf"])
    }

    @Test
    fun `manifest declares localeConfig`() {
        val manifest = File(resDir.parentFile, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
    }

    @Test
    fun `new place-mode strings exist in all languages`() {
        val keys = listOf(
            "action_redo", "action_add", "place_slot_action", "place_slot_select", "place_options", "place_options_close",
            "place_options_page", "place_width_label", "place_size_label", "place_straighten", "color_orange", "color_indigo",
            "color_purple", "color_gray", "color_custom", "place_custom_color_title", "place_text_size_menu",
            "place_text_color_toggle", "place_menu_reading_mode", "place_size_decrease", "place_size_increase",
            "place_text_style_menu", "place_text_style_h1", "place_text_style_h2", "place_text_style_body1",
            "place_text_style_body2"
        )
        (locales + ("en" to base)).forEach { (lang, map) ->
            keys.forEach { assertTrue("$it відсутній у $lang", map.containsKey(it)) }
        }
    }

    @Test
    fun readOnlySaveOfferStringsExistInAllFourLanguages() {
        (locales + ("en" to base)).forEach { (lang, map) ->
            listOf("save_readonly_message", "copy_save").forEach {
                assertTrue("$it відсутній або порожній у $lang", !map[it].isNullOrBlank())
            }
        }
    }

    private fun loadPlurals(folder: String): Map<String, Set<String>> {
        val file = File(resDir, "$folder/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, Set<String>>()
        val nodes = doc.getElementsByTagName("plurals")
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            val items = n.childNodes
            val quantities = LinkedHashSet<String>()
            for (j in 0 until items.length) {
                val it = items.item(j)
                if (it.nodeName == "item") {
                    quantities += it.attributes.getNamedItem("quantity").nodeValue
                    assertTrue("порожній елемент plurals у $folder", it.textContent.isNotBlank())
                }
            }
            out[n.attributes.getNamedItem("name").nodeValue] = quantities
        }
        return out
    }

    @Test
    fun `every plural item keeps the number placeholder`() {
        listOf("values", "values-uk", "values-de", "values-ru", "values-es").forEach { folder ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "$folder/strings.xml"))
            val items = doc.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val text = items.item(i).textContent
                assertTrue("plural без %d у $folder: $text", Regex("%(\\d+\\$)?d").containsMatchIn(text))
            }
        }
    }

    @Test
    fun `plurals exist in all languages with the quantities each language needs`() {
        val en = loadPlurals("values")
        assertTrue(en.containsKey("pages_count"))
        val required = mapOf(
            "values" to setOf("one", "other"),
            "values-de" to setOf("one", "other"),
            "values-uk" to setOf("one", "few", "many", "other"),
            "values-ru" to setOf("one", "few", "many", "other"),
            "values-es" to setOf("one", "many", "other")
        )
        required.forEach { (folder, quantities) ->
            val map = loadPlurals(folder)
            assertEquals("ключі plurals у $folder", en.keys, map.keys)
            map.forEach { (name, q) -> assertTrue("$name у $folder: $q має містити $quantities", q.containsAll(quantities)) }
        }
    }

    @Test
    fun `a11y and unified-name strings exist in all languages and no hardcoded english page hint remains`() {
        val keys = listOf("action_back", "a11y_page_jump", "a11y_zoom", "action_tools", "tools_sheet_title", "err_pages_range", "reader_edit_fab_desc")
        (locales + ("en" to base)).forEach { (lang, map) ->
            keys.forEach { assertTrue("$it відсутній у $lang", map.containsKey(it)) }
        }
        // «Назад» у читачі не «Скасувати»
        assertEquals("Назад", locales.getValue("uk").getValue("action_back"))
        // Назва дії «Інструменти документа» у читачі одна в меню й у шторці
        (locales + ("en" to base)).forEach { (lang, map) ->
            assertEquals("action_tools і tools_sheet_title мають збігатися в $lang", map["action_tools"], map["tools_sheet_title"])
        }
        // Тема читання: однакова назва у верхній і нижній панелях
        (locales + ("en" to base)).forEach { (lang, map) ->
            assertEquals("тема читання в $lang", map["action_reading_theme"], map["place_menu_reading_mode"])
        }
    }
}
