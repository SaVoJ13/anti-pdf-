package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTextPaginationTest {

    @Test
    fun shortTextFitsOnSinglePage() {
        val page = PageSizePt(595f, 842f)
        val text = "Короткий текст, який повністю вміщується на одній сторінці."
        val chunks = PlaceMarkupLogic.paginateText(
            initialPageIndex = 0,
            startX = 40f,
            startY = 100f,
            rawText = text,
            fontSizePt = 14f,
            page = page
        )

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].pageIndex)
        assertEquals(40f, chunks[0].x, 0.01f)
        assertEquals(100f, chunks[0].y, 0.01f)
        assertEquals(text, chunks[0].text)
    }

    @Test
    fun longTextSpillsOverToMultiplePages() {
        val page = PageSizePt(595f, 842f)
        // Створюємо довгий текст на 100 рядків
        val longText = (1..100).joinToString("\n") { "Це рядок номер $it для тестування переносу тексту між сторінками." }

        val chunks = PlaceMarkupLogic.paginateText(
            initialPageIndex = 0,
            startX = 40f,
            startY = 400f, // починаємо майже посередині сторінки
            rawText = longText,
            fontSizePt = 14f,
            page = page
        )

        assertTrue("Текст на 100 рядків повинен розбитися щонайменше на 2 сторінки", chunks.size >= 2)
        assertEquals(0, chunks[0].pageIndex)
        assertEquals(1, chunks[1].pageIndex)

        // Перший шматок починається на 400 pt
        assertEquals(400f, chunks[0].y, 0.01f)
        // Другий шматок починається згори сторінки (40 pt)
        assertEquals(40f, chunks[1].y, 0.01f)

        // Загальна кількість рядків у всіх шматочках має зберегти всі 100 рядків
        val totalLines = chunks.sumOf { chunk -> chunk.text.split('\n').size }
        assertTrue("Усі рядки мають зберегтися (загалом $totalLines)", totalLines >= 100)
    }

    @Test
    fun paginationStartsOnNonZeroPage() {
        val page = PageSizePt(595f, 842f)
        val longText = (1..80).joinToString("\n") { "Текст на сторінці 3 і далі: рядок $it" }

        val chunks = PlaceMarkupLogic.paginateText(
            initialPageIndex = 3,
            startX = 50f,
            startY = 700f, // біля самого низу сторінки 3
            rawText = longText,
            fontSizePt = 14f,
            page = page
        )

        assertTrue(chunks.size >= 2)
        assertEquals(3, chunks[0].pageIndex)
        assertEquals(4, chunks[1].pageIndex)
    }
}
