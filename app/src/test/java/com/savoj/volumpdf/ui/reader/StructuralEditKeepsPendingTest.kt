package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Регресія дефекту власника 2026-11-17: «написав текст, зробив скан, текст більше не редагується».
 * Причина: перед додаванням сторінки/файла/скану читач викликав savePending() і вшивав усе додане в PDF.
 * Вимога: до явного «Зберегти» додані елементи лишаються редагованими (PendingStore/редактор), а додавання
 * сторінок у кінець не зсуває індексів наявних об'єктів.
 */
class StructuralEditKeepsPendingTest {

    private fun readerSource(): String = listOf(
        File("src/main/java/com/savoj/volumpdf/ui/reader/ReaderActivity.kt"),
        File("app/src/main/java/com/savoj/volumpdf/ui/reader/ReaderActivity.kt")
    ).first { it.isFile }.readText()

    /** Тіло приватної функції від сигнатури до наступної `private fun`. */
    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("не знайдено $signature", start >= 0)
        val next = src.indexOf("\n    private ", start + signature.length)
        return src.substring(start, if (next < 0) src.length else next)
    }

    @Test
    fun addBlankPageDoesNotBakePendingIntoPdf() {
        assertFalse(body(readerSource(), "private fun addNewBlankPage").contains("savePending"))
    }

    @Test
    fun appendFilesDoesNotBakePendingIntoPdf() {
        assertFalse(body(readerSource(), "private fun appendSelectedFiles").contains("savePending"))
    }

    @Test
    fun scanPathReusesBlankPageWithoutSaving() {
        val src = readerSource()
        assertFalse(body(src, "private fun handleScanResultForReader").contains("savePending"))
        assertTrue(body(src, "private fun handleScanResultForReader").contains("addNewBlankPage"))
    }

    @Test
    fun pendingTextStaysEditableAfterAddingItemsAndRestoreFromStore() {
        val text = PendingMarkup(id = "t1", tool = PlaceTool.TEXT, pageIndex = 0, x = 50f, y = 60f, text = "Привіт", sizePt = 14f, color = 0)
        val image = PendingMarkup(id = "i1", tool = PlaceTool.IMAGE, pageIndex = 3, x = 0f, y = 0f, text = "", sizePt = 400f, color = 0, aspect = 1.4f)
        val dir = kotlin.io.path.createTempDirectory("pending").toFile()
        try {
            val store = PendingStore(dir)
            store.save("doc", listOf(text, image)) { _, f -> f.writeBytes(byteArrayOf(1)) }
            val back = store.load("doc")!!
            assertEquals(listOf("t1", "i1"), back.map { it.id })
            assertEquals("Привіт", back.first { it.id == "t1" }.text)
            assertEquals(0, back.first { it.id == "t1" }.pageIndex)
        } finally {
            dir.deleteRecursively()
        }
    }
}
