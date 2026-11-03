package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FreeTextData
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import java.io.File

class PdfBoxAnnotationEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var engine: PdfBoxAnnotationEngine
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        engine = PdfBoxAnnotationEngine()
        mockContext = Mockito.mock(Context::class.java)
        val mockAssets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.assets).thenReturn(mockAssets)
        Mockito.`when`(mockContext.cacheDir).thenReturn(tempFolder.root)

        Mockito.doAnswer { invocation ->
            val path = invocation.getArgument<String>(0)
            openAssetStreamForTest(path) ?: throw java.io.FileNotFoundException("Asset not found: $path")
        }.`when`(mockAssets).open(Mockito.anyString())

        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(mockContext)
    }

    private fun openAssetStreamForTest(path: String): java.io.InputStream? {
        val streamFromCl = javaClass.classLoader?.getResourceAsStream(path)
            ?: javaClass.classLoader?.getResourceAsStream("assets/$path")
        if (streamFromCl != null) return streamFromCl

        val userHome = System.getProperty("user.home")
        val cacheDirs = listOf(
            File(userHome, ".gradle/caches/modules-2/files-2.1/com.tom-roush/pdfbox-android/2.0.27.0"),
            File(userHome, ".gradle/caches")
        )

        for (dir in cacheDirs) {
            if (!dir.exists()) continue
            val aar = dir.walkTopDown().firstOrNull { it.name.endsWith(".aar") && it.name.contains("pdfbox") }
            if (aar != null && aar.exists()) {
                val zip = java.util.zip.ZipFile(aar)
                val entry = zip.getEntry("assets/$path") ?: zip.getEntry(path)
                if (entry != null) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    zip.close()
                    return java.io.ByteArrayInputStream(bytes)
                }
                zip.close()
            }
        }
        return null
    }

    private fun createMockUri(file: File): Uri {
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.scheme).thenReturn("file")
        Mockito.`when`(uri.path).thenReturn(file.absolutePath)
        Mockito.`when`(uri.toString()).thenReturn("file://${file.absolutePath}")
        return uri
    }

    private fun createTestPdf(pageCount: Int, fileName: String): File {
        val file = tempFolder.newFile(fileName)
        PDDocument().use { doc ->
            repeat(pageCount) {
                doc.addPage(PDPage(PDRectangle.A4))
            }
            doc.save(file)
        }
        return file
    }

    private fun createPoint(x: Float, y: Float): PointF {
        val pt = PointF(x, y)
        pt.x = x
        pt.y = y
        return pt
    }

    private fun createRectF(left: Float, top: Float, right: Float, bottom: Float): RectF {
        val rect = RectF(left, top, right, bottom)
        rect.left = left
        rect.top = top
        rect.right = right
        rect.bottom = bottom
        return rect
    }

    private fun createMockBitmap(width: Int = 10, height: Int = 10): Bitmap {
        val mockBitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(mockBitmap.width).thenReturn(width)
        Mockito.`when`(mockBitmap.height).thenReturn(height)
        Mockito.`when`(mockBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
        Mockito.`when`(mockBitmap.hasAlpha()).thenReturn(false)

        Mockito.doAnswer { invocation ->
            val pixels = invocation.getArgument<IntArray>(0)
            pixels.fill(0xFF0000FF.toInt())
            null
        }.`when`(mockBitmap).getPixels(
            any(IntArray::class.java),
            anyInt(),
            anyInt(),
            anyInt(),
            anyInt(),
            anyInt(),
            anyInt()
        )

        return mockBitmap
    }

    @Test
    fun testAddInkAnnotation() = runBlocking {
        // Підготовка: 1-сторінковий PDF
        val srcFile = createTestPdf(1, "source_ink.pdf")
        val outFile = tempFolder.newFile("output_ink.pdf")
        val srcUri = createMockUri(srcFile)
        val outUri = createMockUri(outFile)

        // Створення тестового рукописного штриха
        val points = listOf(
            createPoint(50f, 100f),
            createPoint(100f, 150f),
            createPoint(150f, 120f)
        )
        val stroke = InkStroke(
            points = points,
            color = 0xFFFF0000.toInt(), // Red
            strokeWidth = 3f
        )

        // Виконання операції
        val result = engine.addInkAnnotations(
            context = mockContext,
            sourceUri = srcUri,
            pageIndex = 0,
            strokes = listOf(stroke),
            pageWidthPt = 595.2756f,
            pageHeightPt = 841.8898f,
            outputUri = outUri
        )

        // Перевірка результату операції
        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(1, success.pagesCount)

        // Перевірка наявності анотації типу /Ink у збереженому PDF
        PDDocument.load(outFile).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val page = doc.getPage(0)
            val annotations = page.annotations
            assertEquals(1, annotations.size)

            val annotation = annotations[0]
            assertEquals(PDAnnotationMarkup.SUB_TYPE_INK, annotation.subtype)
            assertTrue(annotation is PDAnnotationMarkup)

            val markup = annotation as PDAnnotationMarkup
            val inkList = markup.inkList
            assertNotNull(inkList)
            assertEquals(1, inkList.size)
            assertEquals(points.size * 2, inkList[0].size)

            // Перевірка інверсії осі Y: Y_pdf = pageHeightPt - Y_canvas
            val expectedFirstPdfY = 841.8898f - 100f
            assertEquals(expectedFirstPdfY, inkList[0][1], 0.01f)
        }
    }

    @Test
    fun testAddSignatureStamp() = runBlocking {
        // Підготовка: 1-сторінковий PDF
        val srcFile = createTestPdf(1, "source_stamp.pdf")
        val outFile = tempFolder.newFile("output_stamp.pdf")
        val srcUri = createMockUri(srcFile)
        val outUri = createMockUri(outFile)

        val signatureBitmap = createMockBitmap(20, 20)
        val targetRect = createRectF(50f, 100f, 150f, 150f)

        // Виконання операції вставки підпису
        val result = engine.addSignatureStamp(
            context = mockContext,
            sourceUri = srcUri,
            pageIndex = 0,
            signatureBitmap = signatureBitmap,
            targetRectPt = targetRect,
            pageWidthPt = 595.2756f,
            pageHeightPt = 841.8898f,
            outputUri = outUri
        )

        // Перевірка успішного результату
        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(1, success.pagesCount)

        // Перевірка валідності результуючого PDF
        PDDocument.load(outFile).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val page = doc.getPage(0)
            assertNotNull(page.resources)
        }
    }

    @Test
    fun testAddFreeText() = runBlocking {
        // Підготовка: 1-сторінковий PDF
        val srcFile = createTestPdf(1, "source_text.pdf")
        val outFile = tempFolder.newFile("output_text.pdf")
        val srcUri = createMockUri(srcFile)
        val outUri = createMockUri(outFile)

        val textToInsert = "Hello Volum PDF FreeText"
        val textData = FreeTextData(
            text = textToInsert,
            x = 72f,
            y = 100f,
            fontSize = 16f,
            color = 0xFF0000FF.toInt()
        )

        // Виконання операції нанесення тексту
        val result = engine.addFreeText(
            context = mockContext,
            sourceUri = srcUri,
            pageIndex = 0,
            textData = textData,
            pageWidthPt = 595.2756f,
            pageHeightPt = 841.8898f,
            outputUri = outUri
        )

        // Перевірка успішного результату
        assertTrue(result is PdfOperationResult.Success)
        val success = result as PdfOperationResult.Success
        assertEquals(1, success.pagesCount)

        // Перевірка нанесення тексту через вилучення тексту зі сторінки
        PDDocument.load(outFile).use { doc ->
            val stripper = PDFTextStripper()
            val extractedText = stripper.getText(doc)
            assertTrue(
                "Текст '$textToInsert' повинен бути присутній на сторінці (отримано: '$extractedText')",
                extractedText.contains(textToInsert)
            )
        }
    }

    @Test
    fun testInvalidPageIndex() = runBlocking {
        val srcFile = createTestPdf(1, "source_bounds.pdf")
        val outFile = tempFolder.newFile("output_bounds.pdf")
        val srcUri = createMockUri(srcFile)
        val outUri = createMockUri(outFile)

        val textData = FreeTextData("Test", 50f, 50f, 12f, 0)
        val result = engine.addFreeText(mockContext, srcUri, 5, textData, 595f, 842f, outUri)

        assertTrue(result is PdfOperationResult.Error)
        val error = result as PdfOperationResult.Error
        assertTrue(error.userMessage.contains("виходить за межі"))
    }
}
