package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.util.Base64

/** O-1: сторінка із зображення вписується в A4 зі збереженням пропорцій. */
class ImagePageGeometryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context

    private fun size(w: Int, h: Int) = ImagePageGeometry.pageSizePt(w, h)

    private fun assertInsideA4(p: Pair<Float, Float>) {
        val long = maxOf(p.first, p.second)
        val short = minOf(p.first, p.second)
        assertTrue("довга сторона ${long}", long <= 842.01f)
        assertTrue("коротка сторона ${short}", short <= 595.01f)
    }

    @Test
    fun portraitLargeFitsA4Portrait() {
        val p = size(3000, 4000)
        assertInsideA4(p)
        assertTrue(p.second > p.first)
        assertEquals(3000f / 4000f, p.first / p.second, 0.001f)
        assertEquals(793.33f, p.second, 0.01f)
    }

    @Test
    fun landscapeLargeFitsA4Landscape() {
        val p = size(4000, 3000)
        assertInsideA4(p)
        assertTrue(p.first > p.second)
        assertEquals(4000f / 3000f, p.first / p.second, 0.001f)
        assertEquals(793.33f, p.first, 0.01f)
    }

    @Test
    fun squareLargeFitsShortSide() {
        val p = size(2000, 2000)
        assertInsideA4(p)
        assertEquals(595f, p.first, 0.01f)
        assertEquals(595f, p.second, 0.01f)
    }

    @Test
    fun veryLargeEightThousandBySixThousand() {
        val p = size(8000, 6000)
        assertInsideA4(p)
        assertEquals(8000f / 6000f, p.first / p.second, 0.001f)
        assertEquals(793.33f, p.first, 0.01f)
        assertEquals(595f, p.second, 0.01f)
    }

    @Test
    fun veryTallPanoramaStaysWithinBounds() {
        val p = size(100, 20000)
        assertInsideA4(p)
        assertEquals(100f / 20000f, p.first / p.second, 0.0001f)
    }

    @Test
    fun verySmallKeepsNaturalSize() {
        val p = size(10, 10)
        assertEquals(10f, p.first, 0.001f)
        assertEquals(10f, p.second, 0.001f)
    }

    @Test
    fun smallerThanA4KeepsNaturalSize() {
        val p = size(400, 300)
        assertEquals(400f, p.first, 0.001f)
        assertEquals(300f, p.second, 0.001f)
    }

    @Test
    fun exactlyA4NotScaled() {
        val p = size(595, 842)
        assertEquals(595f, p.first, 0.001f)
        assertEquals(842f, p.second, 0.001f)
    }

    @Test
    fun portraitBetweenShortAndLongNotRotated() {
        // 700x600: ширше за коротку сторону A4, але альбомне -> вписується в 842x595
        val p = size(700, 600)
        assertInsideA4(p)
        assertTrue(p.first > p.second)
        assertEquals(700f / 600f, p.first / p.second, 0.001f)
    }

    @Test
    fun degenerateSizesDoNotCrash() {
        val p = size(0, 0)
        assertEquals(1f, p.first, 0.001f)
        assertEquals(1f, p.second, 0.001f)
    }

    // ---- Інтеграція через рушій ----

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        val mockAssets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.assets).thenReturn(mockAssets)
        PdfBoxPageOperationsEngine.init(mockContext)
    }

    private fun uriOf(file: File): Uri {
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.scheme).thenReturn("file")
        Mockito.`when`(uri.path).thenReturn(file.absolutePath)
        Mockito.`when`(uri.toString()).thenReturn("file://${file.absolutePath}")
        return uri
    }

    private val sampleJpeg = Base64.getDecoder().decode(
        SAMPLE_JPEG_BASE64
    )

    /** Це 16x16 JPEG, у якому в заголовку SOF підмінено розмір (рушій бере розмір із заголовка). */
    private fun jpeg(name: String, w: Int, h: Int): File {
        val f = tempFolder.newFile(name)
        val b = sampleJpeg.copyOf()
        var i = 2
        while (i < b.size - 9) {
            if (b[i] == 0xFF.toByte() && b[i + 1] == 0xC0.toByte()) {
                b[i + 5] = (h shr 8).toByte(); b[i + 6] = h.toByte()
                b[i + 7] = (w shr 8).toByte(); b[i + 8] = w.toByte()
                break
            }
            i++
        }
        f.writeBytes(b)
        return f
    }

    private companion object {
        const val SAMPLE_JPEG_BASE64 =
            "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsK" +
            "CwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQU" +
            "FBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAAQABADASIA" +
            "AhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQA" +
            "AAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3" +
            "ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWm" +
            "pqipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEA" +
            "AwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSEx" +
            "BhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElK" +
            "U1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3" +
            "uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD8qqKK" +
            "KAP/2Q=="
    }

    @Test
    fun engineImagesToPdfScalesPagesAndKeepsPixels() = runBlocking {
        val engine = PdfBoxPageOperationsEngine()
        val files = listOf(
            jpeg("p.jpg", 1500, 2000),
            jpeg("l.jpg", 2000, 1500),
            jpeg("s.jpg", 1000, 1000),
            jpeg("t.jpg", 10, 10),
        )
        val out = tempFolder.newFile("out.pdf")
        val result = engine.imagesToPdf(mockContext, files.map(::uriOf), uriOf(out))
        assertTrue("$result", result is PdfOperationResult.Success)

        PDDocument.load(out).use { doc ->
            assertEquals(4, doc.numberOfPages)
            val dims = (0 until 4).map { doc.getPage(it).mediaBox.let { b -> b.width to b.height } }
            // портрет 1500x2000 -> ширина 595
            assertEquals(595f, dims[0].first, 0.1f); assertEquals(793.33f, dims[0].second, 0.1f)
            // альбом 2000x1500 -> висота 595
            assertEquals(793.33f, dims[1].first, 0.1f); assertEquals(595f, dims[1].second, 0.1f)
            // квадрат -> 595x595
            assertEquals(595f, dims[2].first, 0.1f); assertEquals(595f, dims[2].second, 0.1f)
            // мале -> природний розмір
            assertEquals(10f, dims[3].first, 0.1f); assertEquals(10f, dims[3].second, 0.1f)

            // піксельні дані не змінені: XObject зберігає оригінальні розміри
            val xo = doc.getPage(0).resources.getXObject(
                doc.getPage(0).resources.xObjectNames.first()
            ) as com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
            assertEquals(1500, xo.width)
            assertEquals(2000, xo.height)
        }
    }

    @Test
    fun engineAppendFilesScalesImagePage() = runBlocking {
        val engine = PdfBoxPageOperationsEngine()
        val base = tempFolder.newFile("base.pdf")
        PDDocument().use { d ->
            d.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4))
            d.save(base)
        }
        val img = jpeg("big.jpg", 4000, 3000)
        val out = tempFolder.newFile("appended.pdf")
        val result = engine.appendFiles(mockContext, uriOf(base), listOf(uriOf(img)), uriOf(out))
        assertTrue("$result", result is PdfOperationResult.Success)
        PDDocument.load(out).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val b = doc.getPage(1).mediaBox
            assertEquals(793.33f, b.width, 0.1f)
            assertEquals(595f, b.height, 0.1f)
        }
    }
}
