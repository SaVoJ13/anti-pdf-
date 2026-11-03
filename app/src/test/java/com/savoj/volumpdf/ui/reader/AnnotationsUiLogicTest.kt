package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.model.FreeTextData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationsUiLogicTest {

    private data class TestPoint(val x: Float, val y: Float)
    private data class TestRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    @Test
    fun testInkCoordinatesScalingCalculation() {
        // Полотно на екрані 500 x 1000 px
        val canvasWidth = 500f
        val canvasHeight = 1000f

        // Сторінка PDF А4: 595 x 842 pt
        val pageWidthPt = 595f
        val pageHeightPt = 842f

        val scaleX = pageWidthPt / canvasWidth
        val scaleY = pageHeightPt / canvasHeight
        val scaleStroke = (scaleX + scaleY) / 2f

        val screenPoints = listOf(
            TestPoint(0f, 0f),
            TestPoint(250f, 500f),
            TestPoint(500f, 1000f)
        )
        val initialStrokeWidth = 6f

        val scaledPoints = screenPoints.map { pt ->
            TestPoint(pt.x * scaleX, pt.y * scaleY)
        }
        val scaledWidth = initialStrokeWidth * scaleStroke

        // Перевірка початку
        assertEquals(0f, scaledPoints[0].x, 0.001f)
        assertEquals(0f, scaledPoints[0].y, 0.001f)

        // Перевірка середини: 250 * (595/500) = 297.5 pt; 500 * (842/1000) = 421 pt
        assertEquals(297.5f, scaledPoints[1].x, 0.001f)
        assertEquals(421f, scaledPoints[1].y, 0.001f)

        // Перевірка правого нижнього кута
        assertEquals(595f, scaledPoints[2].x, 0.001f)
        assertEquals(842f, scaledPoints[2].y, 0.001f)

        // Перевірка масштабованої товщини
        val expectedScaleStroke = (1.19f + 0.842f) / 2f
        assertEquals(6f * expectedScaleStroke, scaledWidth, 0.001f)
    }

    @Test
    fun testSignatureTargetRectPlacementCalculation() {
        val pageWidthPt = 600f
        val pageHeightPt = 800f
        val bitmapWidth = 400
        val bitmapHeight = 200 // Співвідношення сторін 2:1

        val stampWidthPt = minOf(160f, pageWidthPt * 0.35f) // 160f
        val stampHeightPt = stampWidthPt * (bitmapHeight.toFloat() / bitmapWidth.toFloat()) // 80f

        val left = pageWidthPt - stampWidthPt - 40f // 600 - 160 - 40 = 400f
        val top = pageHeightPt - stampHeightPt - 60f // 800 - 80 - 60 = 660f
        val rect = TestRect(left, top, left + stampWidthPt, top + stampHeightPt)

        assertEquals(400f, rect.left, 0.001f)
        assertEquals(660f, rect.top, 0.001f)
        assertEquals(560f, rect.right, 0.001f)
        assertEquals(740f, rect.bottom, 0.001f)
        assertEquals(160f, rect.width, 0.001f)
        assertEquals(80f, rect.height, 0.001f)
    }

    @Test
    fun testFreeTextDataCreation() {
        val textData = FreeTextData(
            text = "Затверджено директором",
            x = 54f,
            y = 72f,
            fontSize = 18f,
            color = 0xFFD32F2F.toInt()
        )

        assertEquals("Затверджено директором", textData.text)
        assertEquals(54f, textData.x, 0.001f)
        assertEquals(72f, textData.y, 0.001f)
        assertEquals(18f, textData.fontSize, 0.001f)
        assertEquals(0xFFD32F2F.toInt(), textData.color)
    }
}
