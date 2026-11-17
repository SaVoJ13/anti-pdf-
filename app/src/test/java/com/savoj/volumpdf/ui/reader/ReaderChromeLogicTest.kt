package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** М1/М2/М3/М5: видимість кнопки «Редагувати», пунктів меню ⋮ та розмітка читача без нижньої панелі. */
class ReaderChromeLogicTest {

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(resDir, path))

    private fun ids(path: String): List<String> {
        val all = parse(path).getElementsByTagName("*")
        return (0 until all.length).map { (all.item(it) as Element).getAttributeNS(android, "id") }.filter { it.isNotEmpty() }
    }

    @Test
    fun `edit fab is shown only in plain reading`() {
        assertTrue(ReaderChromeLogic.showEditFab(placeMode = false, openFailed = false, searchActive = false))
        assertFalse(ReaderChromeLogic.showEditFab(placeMode = true, openFailed = false, searchActive = false))
        assertFalse(ReaderChromeLogic.showEditFab(placeMode = false, openFailed = true, searchActive = false))
        assertFalse(ReaderChromeLogic.showEditFab(placeMode = false, openFailed = false, searchActive = true))
    }

    @Test
    fun `reading menu has no search no add and no edit duplicates`() {
        val m = ReaderChromeLogic.menuState(placeMode = false, hasUnsaved = true, formFieldCount = 0, readOnlyDocument = false, openFailed = false)
        assertFalse("пошук лише лупа в пігулці", m.searchInOverflow)
        assertFalse("«+» перенесено в «Сторінки й файл»", m.add)
        assertFalse(m.placeSave)
        assertFalse(m.placeDone)
        assertTrue("тема читання в меню ⋮", m.readingTheme)
        assertTrue(m.documentActions)
        assertFalse(m.forms)
        assertFalse(m.saveCopy)
    }

    @Test
    fun `forms stay reachable when the document has fields`() {
        assertTrue(ReaderChromeLogic.menuState(false, false, 3, false, false).forms)
        assertTrue(ReaderChromeLogic.menuState(true, false, 1, false, false).forms)
        assertFalse(ReaderChromeLogic.menuState(false, false, 0, false, false).forms)
    }

    @Test
    fun `place mode keeps save done add and search in the top bar`() {
        val clean = ReaderChromeLogic.menuState(true, false, 0, false, false)
        assertTrue(clean.placeDone)
        assertTrue(clean.add)
        assertTrue("у режимі «Редагувати» пігулки немає, пошук у меню ⋮", clean.searchInOverflow)
        assertFalse("«Зберегти» лише за незбережених змін", clean.placeSave)
        assertTrue(ReaderChromeLogic.menuState(true, true, 0, false, false).placeSave)
        assertTrue("тема читання не зникає в режимі «Редагувати»", clean.readingTheme)
    }

    @Test
    fun `save copy only for read only document outside place mode`() {
        assertTrue(ReaderChromeLogic.menuState(false, false, 0, true, false).saveCopy)
        assertFalse(ReaderChromeLogic.menuState(true, false, 0, true, false).saveCopy)
    }

    @Test
    fun `open failure hides every document action`() {
        val m = ReaderChromeLogic.menuState(placeMode = false, hasUnsaved = true, formFieldCount = 5, readOnlyDocument = true, openFailed = true)
        assertEquals(ReaderChromeLogic.MenuState(false, false, false, false, false, false, false, false), m)
    }

    @Test
    fun `reader layout has no bottom bar and one edit fab with a description`() {
        val layoutIds = ids("layout/activity_reader.xml")
        assertFalse("нижньої панелі читача більше немає", layoutIds.contains("@+id/bottomActionBar"))
        assertEquals("одна плаваюча кнопка", 1, layoutIds.count { it == "@+id/fabReaderEdit" })
        assertFalse("меню нижньої панелі прибрано", File(resDir, "menu/menu_reader_bottom.xml").exists())
        val all = parse("layout/activity_reader.xml").getElementsByTagName("*")
        val fab = (0 until all.length).map { all.item(it) as Element }.first { it.getAttributeNS(android, "id") == "@+id/fabReaderEdit" }
        assertEquals("@string/reader_edit_fab_desc", fab.getAttributeNS(android, "contentDescription"))
        assertEquals("@string/action_place", fab.getAttributeNS(android, "tooltipText"))
    }

    @Test
    fun `top menu has theme once and neither bottom duplicates nor edit item`() {
        val menuIds = ids("menu/menu_reader.xml")
        assertEquals(1, menuIds.count { it == "@+id/action_reading_theme" })
        assertEquals(0, menuIds.count { it == "@+id/action_reading_theme_overflow" })
        assertEquals("«Редагувати» — плаваюча кнопка", 0, menuIds.count { it == "@+id/action_place" })
        assertEquals(1, menuIds.count { it == "@+id/action_search" })
        assertEquals(1, menuIds.count { it == "@+id/action_tools" })
        assertEquals(1, menuIds.count { it == "@+id/action_forms" })
        val all = parse("menu/menu_reader.xml").getElementsByTagName("item")
        val byId = (0 until all.length).map { all.item(it) as Element }.associateBy { it.getAttributeNS(android, "id") }
        val theme = byId.getValue("@+id/action_reading_theme")
        assertEquals("тема читання тільки в ⋮", "never", theme.getAttributeNS("http://schemas.android.com/apk/res-auto", "showAsAction"))
        assertNotNull(byId["@+id/action_add"])
        assertNull(byId["@+id/action_place"])
        assertEquals("false", byId.getValue("@+id/action_add").getAttributeNS(android, "visible"))
    }

    @Test
    fun `sheet still offers add page image file and scan`() {
        val text = File(listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory },
            "com/savoj/volumpdf/ui/reader/ReaderToolsBottomSheetDialogFragment.kt").readText()
        listOf("ADD_PAGE", "ADD_IMAGE", "INSERT_FILE", "SCAN", "ORGANIZE_PAGES").forEach {
            assertTrue("шторка має $it", text.contains("action = ReaderToolAction.$it"))
        }
    }

    @Test
    fun `closing search from any source brings the fab and pill back`() {
        // відкрито пошук: ховаються; закрито (кнопка «Закрити» панелі, Back, хрестик): повертаються
        var applied = false
        fun step(current: Boolean): Boolean {
            val refresh = ReaderChromeLogic.needsChromeRefresh(applied, current)
            if (refresh) applied = current
            return refresh
        }
        assertFalse("нічого не змінилось", step(false))
        assertTrue("пошук відкрито", step(true))
        assertFalse(ReaderChromeLogic.showEditFab(placeMode = false, openFailed = false, searchActive = applied))
        assertFalse("стан стабільний", step(true))
        assertTrue("пошук закрито власною кнопкою панелі", step(false))
        assertTrue(ReaderChromeLogic.showEditFab(placeMode = false, openFailed = false, searchActive = applied))
    }

    @Test
    fun `search state never revives the fab in place mode or after open failure`() {
        assertFalse(ReaderChromeLogic.showEditFab(placeMode = true, openFailed = false, searchActive = false))
        assertFalse(ReaderChromeLogic.showEditFab(placeMode = false, openFailed = true, searchActive = false))
    }
}
