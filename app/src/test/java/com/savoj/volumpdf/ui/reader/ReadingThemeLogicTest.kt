package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingThemeLogicTest {

    @Test
    fun `DAY theme returns null matrix and preserves RGB unchanged`() {
        assertNull(ReadingThemeLogic.matrixForTheme(ReadingTheme.DAY))

        val white = ReadingThemeLogic.transformRgb(ReadingTheme.DAY, 255, 255, 255)
        assertEquals(Triple(255, 255, 255), white)

        val black = ReadingThemeLogic.transformRgb(ReadingTheme.DAY, 0, 0, 0)
        assertEquals(Triple(0, 0, 0), black)

        val color = ReadingThemeLogic.transformRgb(ReadingTheme.DAY, 120, 180, 240)
        assertEquals(Triple(120, 180, 240), color)
    }

    @Test
    fun `NIGHT theme inverts and softens contrast to OLED dark graphite`() {
        val matrix = ReadingThemeLogic.matrixForTheme(ReadingTheme.NIGHT)
        assertNotNull(matrix)
        assertEquals(20, matrix!!.size)

        // Білий аркуш (255) перетворюється на темний графіт ~16 (#101010)
        val bg = ReadingThemeLogic.transformRgb(ReadingTheme.NIGHT, 255, 255, 255)
        assertTrue("Dark background should be deep graphite (<= 25)", bg.first <= 25 && bg.second <= 25 && bg.third <= 25)
        assertEquals(bg.first, bg.second)
        assertEquals(bg.second, bg.third)

        // Чорний текст (0) перетворюється на м'який білий ~225 (#E1E1E1)
        val text = ReadingThemeLogic.transformRgb(ReadingTheme.NIGHT, 0, 0, 0)
        assertTrue("Text should be soft white (>= 220)", text.first >= 220 && text.second >= 220 && text.third >= 220)
        assertEquals(text.first, text.second)
        assertEquals(text.second, text.third)

        // Коефіцієнт контрасту перевищує стандарт WCAG AAA (7:1)
        val contrast = text.first.toFloat() / bg.first.toFloat()
        assertTrue("Contrast ratio should exceed 10:1 (actual $contrast)", contrast > 10.0f)
    }

    @Test
    fun `SEPIA theme transforms white page into warm parchment and black text into warm ink`() {
        val matrix = ReadingThemeLogic.matrixForTheme(ReadingTheme.SEPIA)
        assertNotNull(matrix)
        assertEquals(20, matrix!!.size)

        // Білий фон (255) перетворюється на теплий айворі / пергамент (#F5ECD7)
        val bg = ReadingThemeLogic.transformRgb(ReadingTheme.SEPIA, 255, 255, 255)
        assertEquals(245, bg.first)
        assertEquals(236, bg.second)
        assertEquals(215, bg.third)

        // Чорний текст (0) перетворюється на глибоке коричневе чорнило (#2D2214)
        val text = ReadingThemeLogic.transformRgb(ReadingTheme.SEPIA, 0, 0, 0)
        assertEquals(45, text.first)
        assertEquals(34, text.second)
        assertEquals(20, text.third)

        // Контрастність комфортна та відповідає стандарту WCAG AA (>4.5:1)
        val contrast = bg.first.toFloat() / text.first.toFloat()
        assertTrue("Contrast ratio should be >= 5.0 (actual $contrast)", contrast >= 5.0f)
    }

    @Test
    fun `ReadingTheme fromStorageKey correctly parses valid and invalid keys`() {
        assertEquals(ReadingTheme.DAY, ReadingTheme.fromStorageKey("day"))
        assertEquals(ReadingTheme.NIGHT, ReadingTheme.fromStorageKey("night"))
        assertEquals(ReadingTheme.SEPIA, ReadingTheme.fromStorageKey("sepia"))

        assertEquals(ReadingTheme.NIGHT, ReadingTheme.fromStorageKey("NIGHT"))
        assertEquals(ReadingTheme.SEPIA, ReadingTheme.fromStorageKey("Sepia"))

        // Безпечні фолбеки
        assertEquals(ReadingTheme.DAY, ReadingTheme.fromStorageKey(null))
        assertEquals(ReadingTheme.DAY, ReadingTheme.fromStorageKey(""))
        assertEquals(ReadingTheme.DAY, ReadingTheme.fromStorageKey("invalid_theme"))
    }

    @Test
    fun `containerBackgroundColor returns distinct non-zero colors for all themes`() {
        val dayBg = ReadingThemeLogic.containerBackgroundColor(ReadingTheme.DAY)
        val nightBg = ReadingThemeLogic.containerBackgroundColor(ReadingTheme.NIGHT)
        val sepiaBg = ReadingThemeLogic.containerBackgroundColor(ReadingTheme.SEPIA)

        assertTrue(dayBg != 0)
        assertTrue(nightBg != 0)
        assertTrue(sepiaBg != 0)

        assertTrue("Themes should have distinct background colors", dayBg != nightBg && nightBg != sepiaBg)
    }
}
