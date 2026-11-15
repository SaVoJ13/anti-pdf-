package com.savoj.volumpdf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Захист від регресії К1 (PR #2): AppCompatDialog падає з IllegalArgumentException «AppCompat does not support the
 * current theme features», якщо в темі діалогу немає windowNoTitle (або windowActionBar=false) БЕЗ префікса android:.
 * Тут розбирається themes.xml (усі values*): кожен стиль `Theme.*` з «Dialog» у батьківському стилі (або в імені)
 * має мати обидва атрибути без префікса.
 *
 * ОБМЕЖЕННЯ: це перевірка розмітки, а не відкриття діалогу. Вона не ловить інші причини падіння вікна
 * (вставка в Window, insets, клавіатура); для цього лишається перевірка на пристрої. Robolectric у проєкті немає,
 * залежності не додаються.
 */
class ThemesDialogTest {

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private data class Style(val file: String, val name: String, val parent: String?, val items: Map<String, String>)

    private fun styles(): List<Style> {
        val out = ArrayList<Style>()
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.forEach { dir ->
            val f = File(dir, "themes.xml")
            if (!f.isFile) return@forEach
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.getElementsByTagName("style")
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                val items = LinkedHashMap<String, String>()
                val ch = n.childNodes
                for (j in 0 until ch.length) {
                    val c = ch.item(j)
                    if (c.nodeName == "item") items[c.attributes.getNamedItem("name").nodeValue] = c.textContent.trim()
                }
                out += Style(
                    "${dir.name}/themes.xml",
                    n.attributes.getNamedItem("name").nodeValue,
                    n.attributes.getNamedItem("parent")?.nodeValue,
                    items
                )
            }
        }
        return out
    }

    private fun isDialogTheme(s: Style, all: Map<String, Style>): Boolean {
        var cur: Style? = s
        var guard = 0
        while (cur != null && guard++ < 10) {
            if (cur.name.startsWith("Theme") && (cur.name.contains("Dialog") || cur.parent?.contains("Dialog") == true)) return true
            cur = cur.parent?.let { all[it] }
        }
        return false
    }

    @Test
    fun `dialog themes have unprefixed windowNoTitle and windowActionBar false`() {
        val list = styles()
        val all = list.associateBy { it.name }
        val dialogs = list.filter { isDialogTheme(it, all) }
        assertTrue("очікується хоча б Theme.VolumPdf.TextCard", dialogs.any { it.name == "Theme.VolumPdf.TextCard" })
        dialogs.forEach { s ->
            assertEquals("${s.file}: ${s.name} без windowNoTitle (не android:)", "true", s.items["windowNoTitle"])
            assertEquals("${s.file}: ${s.name} без windowActionBar=false (не android:)", "false", s.items["windowActionBar"])
        }
    }

    @Test
    fun `no theme sets only the android-prefixed windowNoTitle`() {
        styles().filter { it.items.containsKey("android:windowNoTitle") }.forEach { s ->
            assertTrue("${s.file}: ${s.name} має лише android:windowNoTitle", s.items.containsKey("windowNoTitle"))
        }
    }
}
