package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.ui.reader.SelectionPill.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Розташування компактної панелі дій біля виділеного об'єкта: верх, низ, край, ландшафт, високий об'єкт. */
class SelectionPillTest {
    private val pillW = 300f
    private val pillH = 100f
    private val gap = 24f
    private val margin = 24f

    private fun place(f: Frame, w: Float = 1440f, h: Float = 2200f) = SelectionPill.place(f, pillW, pillH, w, h, gap, margin)

    @Test
    fun placedAboveObjectWhenThereIsRoom() {
        val p = place(Frame(500f, 800f, 700f, 900f))
        assertTrue(p.above)
        assertEquals(800f - gap - pillH, p.y, 0.01f)
        assertEquals(600f - pillW / 2f, p.x, 0.01f)
        assertTrue(p.y + pillH + gap <= 800f) // не накриває об'єкт
    }

    @Test
    fun placedBelowWhenObjectNearTop() {
        val p = place(Frame(500f, 40f, 700f, 140f))
        assertFalse(p.above)
        assertEquals(140f + gap, p.y, 0.01f)
    }

    @Test
    fun clampedHorizontallyAtLeftAndRightEdges() {
        val left = place(Frame(0f, 800f, 60f, 900f))
        assertEquals(margin, left.x, 0.01f)
        val right = place(Frame(1380f, 800f, 1440f, 900f))
        assertEquals(1440f - pillW - margin, right.x, 0.01f)
        assertTrue(left.x >= 0f && right.x + pillW <= 1440f)
    }

    @Test
    fun alwaysFullyInsideContainerEvenForTallObject() {
        val p = place(Frame(100f, 10f, 1300f, 2190f)) // об'єкт на всю висоту
        assertTrue(p.y >= margin)
        assertTrue(p.y + pillH <= 2200f - margin)
    }

    @Test
    fun landscapeShortContainerStaysInside() {
        val p = place(Frame(900f, 300f, 1100f, 380f), w = 2200f, h = 900f)
        assertTrue(p.above)
        assertTrue(p.y >= margin && p.y + pillH <= 900f - margin)
        assertTrue(p.x >= margin && p.x + pillW <= 2200f - margin)
    }

    @Test
    fun invisibleWhenObjectScrolledOutOfView() {
        assertFalse(place(Frame(100f, -500f, 300f, -300f)).visible)
        assertFalse(place(Frame(100f, 2300f, 300f, 2400f)).visible)
        assertTrue(place(Frame(100f, 800f, 300f, 900f)).visible)
    }

    @Test
    fun pillHeightWithinTenPercentOfScreen() {
        // 52 dp на екрані ≈ 2340 dp заввишки (S24 Ultra ≈ 6%); ліміт 10%.
        assertTrue(SelectionPill.fitsHeight(pillH = 52f, containerH = 700f))
        assertFalse(SelectionPill.fitsHeight(pillH = 200f, containerH = 700f))
    }
}
