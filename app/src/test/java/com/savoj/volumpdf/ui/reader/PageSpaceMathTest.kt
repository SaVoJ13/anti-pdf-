package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PageSpaceMathTest {
    private val eps = 1e-4f

    @Test
    fun originMapsToZero() {
        val p = PageSpaceMath.toPageSpace(120f, 340f, 2.5f, 120f, 340f)!!
        assertEquals(0f, p.first, eps)
        assertEquals(0f, p.second, eps)
    }

    @Test
    fun scalesByZoom() {
        val p = PageSpaceMath.toPageSpace(100f, 200f, 2f, 140f, 260f)!!
        assertEquals(20f, p.first, eps)
        assertEquals(30f, p.second, eps)
    }

    @Test
    fun pointsOutsideThePageKeepTheirSign() {
        val left = PageSpaceMath.toPageSpace(100f, 200f, 2f, 40f, 80f)!!
        assertEquals(-30f, left.first, eps)
        assertEquals(-60f, left.second, eps)
        val far = PageSpaceMath.toPageSpace(100f, 200f, 2f, 5000f, 9000f)!!
        assertEquals(2450f, far.first, eps)
        assertEquals(4400f, far.second, eps)
    }

    /** Модель PdfView: піксель = origin(page) + pt × zoom; зворотне перетворення з 0,5 pt — для будь-якої сторінки, також > 100. */
    @Test
    fun roundTripWithinHalfPointOnAnyPage() {
        val zoom = 2.3529413f
        val pageTopPx = FloatArray(150) { it * (792f * zoom + 16f) } // сторінки одна під одною з проміжком
        for (page in listOf(0, 1, 56, 109, 110, 149)) {
            val ox = 33f
            val oy = pageTopPx[page] - 260000f // докручено далеко: сторінка може бути за екраном
            for ((ptX, ptY) in listOf(0f to 0f, 300.4f to 700.9f, 611f to 791f, -20f to -35f, 640f to 820f)) {
                val vx = ox + ptX * zoom
                val vy = oy + ptY * zoom
                val back = PageSpaceMath.toPageSpace(ox, oy, zoom, vx, vy)
                assertNotNull(back)
                assertEquals(ptX, back!!.first, 0.5f)
                assertEquals(ptY, back.second, 0.5f)
            }
        }
    }

    @Test
    fun invalidInputGivesNull() {
        assertNull(PageSpaceMath.toPageSpace(0f, 0f, 0f, 1f, 1f))
        assertNull(PageSpaceMath.toPageSpace(0f, 0f, -1f, 1f, 1f))
        assertNull(PageSpaceMath.toPageSpace(0f, 0f, Float.NaN, 1f, 1f))
        assertNull(PageSpaceMath.toPageSpace(Float.NaN, 0f, 1f, 1f, 1f))
        assertNull(PageSpaceMath.toPageSpace(0f, 0f, 1f, Float.POSITIVE_INFINITY, 1f))
    }
}
