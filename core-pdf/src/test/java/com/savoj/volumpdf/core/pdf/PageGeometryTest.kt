package com.savoj.volumpdf.core.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {

    private val eps = 0.001f

    private fun assertPoint(expX: Float, expY: Float, actual: UserPoint) {
        assertEquals(expX, actual.x, eps)
        assertEquals(expY, actual.y, eps)
    }

    // CropBox [100 200 495 700]: W=395, H=500, ураховано жорстко задані очікування (не формули з коду).
    @Test
    fun hardcodedPointsForShiftedCropBox() {
        assertPoint(130f, 650f, PageGeometry(100f, 200f, 495f, 700f, 0).toUser(30f, 50f))
        assertPoint(150f, 230f, PageGeometry(100f, 200f, 495f, 700f, 90).toUser(30f, 50f))
        assertPoint(465f, 250f, PageGeometry(100f, 200f, 495f, 700f, 180).toUser(30f, 50f))
        assertPoint(445f, 670f, PageGeometry(100f, 200f, 495f, 700f, 270).toUser(30f, 50f))
    }

    @Test
    fun visibleSizeSwapsFor90And270() {
        val g0 = PageGeometry(100f, 200f, 495f, 700f, 0)
        val g90 = PageGeometry(100f, 200f, 495f, 700f, 90)
        val g180 = PageGeometry(100f, 200f, 495f, 700f, 180)
        val g270 = PageGeometry(100f, 200f, 495f, 700f, 270)
        assertEquals(395f, g0.visibleWidth, eps); assertEquals(500f, g0.visibleHeight, eps)
        assertEquals(500f, g90.visibleWidth, eps); assertEquals(395f, g90.visibleHeight, eps)
        assertEquals(395f, g180.visibleWidth, eps); assertEquals(500f, g180.visibleHeight, eps)
        assertEquals(500f, g270.visibleWidth, eps); assertEquals(395f, g270.visibleHeight, eps)
    }

    @Test
    fun visibleCornersMapToCropBoxCorners() {
        // лівий верхній кут видимої області -> відомий кут CropBox
        assertPoint(100f, 700f, PageGeometry(100f, 200f, 495f, 700f, 0).toUser(0f, 0f))
        assertPoint(100f, 200f, PageGeometry(100f, 200f, 495f, 700f, 90).toUser(0f, 0f))
        assertPoint(495f, 200f, PageGeometry(100f, 200f, 495f, 700f, 180).toUser(0f, 0f))
        assertPoint(495f, 700f, PageGeometry(100f, 200f, 495f, 700f, 270).toUser(0f, 0f))
    }

    @Test
    fun rectIsNormalized() {
        val r = PageGeometry(100f, 200f, 495f, 700f, 90).toUserRect(10f, 20f, 40f, 90f)
        assertEquals(120f, r.llx, eps); assertEquals(210f, r.lly, eps)
        assertEquals(190f, r.urx, eps); assertEquals(240f, r.ury, eps)
    }

    @Test
    fun normalizeRotationValues() {
        assertEquals(0, PageGeometry.normalizeRotation(0))
        assertEquals(270, PageGeometry.normalizeRotation(-90))
        assertEquals(180, PageGeometry.normalizeRotation(-180))
        assertEquals(90, PageGeometry.normalizeRotation(-270))
        assertEquals(0, PageGeometry.normalizeRotation(-360))
        assertEquals(90, PageGeometry.normalizeRotation(450))
        assertEquals(0, PageGeometry.normalizeRotation(360))
        assertEquals(180, PageGeometry.normalizeRotation(900))
        assertEquals(0, PageGeometry.normalizeRotation(45)) // не кратне 90 — ігнорується
    }

    @Test
    fun negativeRotateOnPage() {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(0f, 0f, 300f, 400f))
            page.cosObject.setInt(COSName.ROTATE, -90)
            doc.addPage(page)
            assertEquals(270, PageGeometry.of(doc.getPage(0)).rotation)
        }
    }

    @Test
    fun rotate450OnPage() {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(0f, 0f, 300f, 400f))
            page.cosObject.setInt(COSName.ROTATE, 450)
            doc.addPage(page)
            val g = PageGeometry.of(doc.getPage(0))
            assertEquals(90, g.rotation)
            assertEquals(400f, g.visibleWidth, eps)
            assertEquals(300f, g.visibleHeight, eps)
        }
    }

    @Test
    fun rotateInheritedFromPagesNode() {
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(0f, 0f, 300f, 400f)))
            doc.documentCatalog.pages.cosObject.setInt(COSName.ROTATE, 180)
            assertEquals(180, PageGeometry.of(doc.getPage(0)).rotation)
        }
    }

    @Test
    fun ownRotateOverridesInherited() {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(0f, 0f, 300f, 400f))
            page.cosObject.setInt(COSName.ROTATE, 90)
            doc.addPage(page)
            doc.documentCatalog.pages.cosObject.setInt(COSName.ROTATE, 180)
            assertEquals(90, PageGeometry.of(doc.getPage(0)).rotation)
        }
    }

    @Test
    fun inheritedNegativeRotate() {
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(0f, 0f, 300f, 400f)))
            doc.documentCatalog.pages.cosObject.setInt(COSName.ROTATE, -90)
            assertEquals(270, PageGeometry.of(doc.getPage(0)).rotation)
        }
    }

    @Test
    fun missingRotateIsZero() {
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(0f, 0f, 300f, 400f)))
            assertEquals(0, PageGeometry.of(doc.getPage(0)).rotation)
        }
    }

    @Test
    fun cropBoxFallsBackToMediaBox() {
        PDDocument().use { doc ->
            // PDRectangle(x, y, width, height) = MediaBox [-50 -20 300 360]
            doc.addPage(PDPage(PDRectangle(-50f, -20f, 350f, 380f)))
            val g = PageGeometry.of(doc.getPage(0))
            assertEquals(-50f, g.llx, eps); assertEquals(-20f, g.lly, eps)
            assertEquals(300f, g.urx, eps); assertEquals(360f, g.ury, eps)
        }
    }

    @Test
    fun cropBoxUsedWhenPresent() {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            page.cropBox = PDRectangle(100f, 200f, 395f, 500f) // [100 200 495 700]
            doc.addPage(page)
            val g = PageGeometry.of(doc.getPage(0))
            assertEquals(100f, g.llx, eps); assertEquals(200f, g.lly, eps)
            assertEquals(495f, g.urx, eps); assertEquals(700f, g.ury, eps)
        }
    }
}
