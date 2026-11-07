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
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
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

/**
 * Перевіряє за розпарсеним збереженим PDF, що Ink, підпис і текст стоять у очікуваній точці
 * (допуск 0,5 pt) для /Rotate 0/90/180/270 × зсунутий CropBox × сторінки різного розміру.
 */
class PdfBoxPlacementGeometryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var engine: PdfBoxAnnotationEngine
    private lateinit var mockContext: Context

    private val tol = 0.5f
    private var counter = 0

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

    // ---------- допоміжне ----------

    private data class Case(
        val name: String,
        val media: PDRectangle,
        val crop: PDRectangle?, // null = CropBox відсутній
        val rotate: Int
    )

    private fun cases(): List<Case> {
        // Сторінки різного розміру; кожна з поворотами 0/90/180/270.
        val boxes = listOf(
            // A4 + зсунутий CropBox [100 200 495 700]
            Triple("A4+crop[100 200 495 700]", PDRectangle.A4, PDRectangle(100f, 200f, 395f, 500f)),
            // Letter + зсунутий CropBox [50 60 400 500]
            Triple("Letter+crop[50 60 400 500]", PDRectangle.LETTER, PDRectangle(50f, 60f, 350f, 440f)),
            // Мала сторінка зі зсунутим MediaBox і без CropBox
            Triple("media[-50 -20 300 360] без crop", PDRectangle(-50f, -20f, 350f, 380f), null)
        )
        return boxes.flatMap { (n, m, c) -> listOf(0, 90, 180, 270).map { Case("$n rot=$it", m, c, it) } }
    }

    private fun createPdf(c: Case, name: String): File {
        val file = tempFolder.newFile(name)
        PDDocument().use { doc ->
            val page = PDPage(c.media)
            if (c.crop != null) page.cropBox = c.crop
            page.cosObject.setInt(COSName.ROTATE, c.rotate)
            doc.addPage(page)
            doc.save(file)
        }
        return file
    }

    private fun uri(file: File): Uri {
        val u = Mockito.mock(Uri::class.java)
        Mockito.`when`(u.scheme).thenReturn("file")
        Mockito.`when`(u.path).thenReturn(file.absolutePath)
        return u
    }

    private fun pt(x: Float, y: Float): PointF {
        val p = PointF(x, y); p.x = x; p.y = y; return p
    }

    private fun rectF(l: Float, t: Float, r: Float, b: Float): RectF {
        val rc = RectF(l, t, r, b); rc.left = l; rc.top = t; rc.right = r; rc.bottom = b; return rc
    }

    private fun mockBitmap(): Bitmap {
        val bmp = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bmp.width).thenReturn(10)
        Mockito.`when`(bmp.height).thenReturn(10)
        Mockito.`when`(bmp.config).thenReturn(Bitmap.Config.ARGB_8888)
        Mockito.`when`(bmp.hasAlpha()).thenReturn(false)
        Mockito.doAnswer { inv ->
            inv.getArgument<IntArray>(0).fill(0xFF0000FF.toInt()); null
        }.`when`(bmp).getPixels(
            any(IntArray::class.java), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
        )
        return bmp
    }

    /**
     * Незалежна від PageGeometry таблиця очікувань: (xv, yv) -> простір користувача,
     * записана прямо за формулами з CONTRACTS v0.4 / MARKUP_STAGES (1a).
     */
    private fun expectedUser(rot: Int, box: PDRectangle, xv: Float, yv: Float): Pair<Float, Float> {
        val llx = box.lowerLeftX; val lly = box.lowerLeftY; val ury = box.upperRightY
        val w = box.width; val h = box.height
        return when (rot) {
            0 -> Pair(llx + xv, ury - yv)
            90 -> Pair(llx + yv, lly + xv)
            180 -> Pair(llx + w - xv, lly + yv)
            else -> Pair(llx + w - yv, lly + h - xv)
        }
    }

    private fun effectiveBox(c: Case): PDRectangle = c.crop ?: c.media

    // ---------- Ink ----------

    @Test
    fun inkPlacedCorrectlyOnAllRotationsAndBoxes() = runBlocking {
        val pts = listOf(pt(10f, 20f), pt(60f, 90f), pt(30f, 50f))
        for (c in cases()) {
            val src = createPdf(c, "${counter++}_ink_src.pdf")
            val out = tempFolder.newFile("out_${counter++}.pdf")
            val result = engine.addInkAnnotations(
                mockContext, uri(src), 0,
                listOf(InkStroke(pts, 0xFFFF0000.toInt(), 3f)),
                // навмисно хибні розміри: параметри не мають впливати на координати
                1f, 1f, uri(out)
            )
            assertTrue("${c.name}: $result", result is PdfOperationResult.Success)
            PDDocument.load(out).use { doc ->
                val ann = doc.getPage(0).annotations.single() as PDAnnotationMarkup
                assertEquals(PDAnnotationMarkup.SUB_TYPE_INK, ann.subtype)
                val ink = ann.inkList[0]
                assertEquals(c.name, pts.size * 2, ink.size)
                val box = effectiveBox(c)
                var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                pts.forEachIndexed { i, p ->
                    val (ex, ey) = expectedUser(c.rotate, box, p.x, p.y)
                    assertEquals("${c.name} ink x[$i]", ex, ink[i * 2], tol)
                    assertEquals("${c.name} ink y[$i]", ey, ink[i * 2 + 1], tol)
                    minX = minOf(minX, ex); maxX = maxOf(maxX, ex)
                    minY = minOf(minY, ey); maxY = maxOf(maxY, ey)
                }
                // /Rect містить усі точки штриха (з половиною товщини) і не набагато більший
                val rect = ann.rectangle
                // (PDFBox при генерації AP сам трохи розширює /Rect, тому: містить + не роздутий)
                val margin = 10f
                assertTrue("${c.name} rect llx ${rect.lowerLeftX}", rect.lowerLeftX <= minX - 1.5f + tol && rect.lowerLeftX >= minX - margin)
                assertTrue("${c.name} rect lly ${rect.lowerLeftY}", rect.lowerLeftY <= minY - 1.5f + tol && rect.lowerLeftY >= minY - margin)
                assertTrue("${c.name} rect urx ${rect.upperRightX}", rect.upperRightX >= maxX + 1.5f - tol && rect.upperRightX <= maxX + margin)
                assertTrue("${c.name} rect ury ${rect.upperRightY}", rect.upperRightY >= maxY + 1.5f - tol && rect.upperRightY <= maxY + margin)
                // appearance stream створено і його BBox збігається з /Rect
                val normal = ann.appearance?.normalAppearance?.appearanceStream
                assertNotNull("${c.name}: немає appearance stream /N", normal)
                val bbox = normal!!.bBox
                assertEquals("${c.name} bbox llx", rect.lowerLeftX, bbox.lowerLeftX, tol)
                assertEquals("${c.name} bbox lly", rect.lowerLeftY, bbox.lowerLeftY, tol)
                assertEquals("${c.name} bbox urx", rect.upperRightX, bbox.upperRightX, tol)
                assertEquals("${c.name} bbox ury", rect.upperRightY, bbox.upperRightY, tol)
            }
        }
    }

    // ---------- Підпис ----------

    @Test
    fun signatureStampPlacedCorrectlyOnAllRotationsAndBoxes() = runBlocking {
        val left = 20f; val top = 30f; val right = 80f; val bottom = 60f
        for (c in cases()) {
            val src = createPdf(c, "${counter++}_stamp_src.pdf")
            val out = tempFolder.newFile("out_${counter++}.pdf")
            val result = engine.addSignatureStamp(
                mockContext, uri(src), 0, mockBitmap(), rectF(left, top, right, bottom), 1f, 1f, uri(out)
            )
            assertTrue("${c.name}: $result", result is PdfOperationResult.Success)
            PDDocument.load(out).use { doc ->
                val ctm = lastImageCtm(doc.getPage(0))
                val box = effectiveBox(c)
                // Одиничний квадрат зображення: (0,0) — лівий нижній кут, (1,1) — правий верхній кут (у системі зображення).
                // На видимій сторінці лівий нижній кут = (left, bottom), правий верхній = (right, top).
                val (ex0, ey0) = expectedUser(c.rotate, box, left, bottom)
                val (ex1, ey1) = expectedUser(c.rotate, box, right, top)
                val ax = ctm[4]; val ay = ctm[5]
                val bx = ctm[0] + ctm[2] + ctm[4]; val by = ctm[1] + ctm[3] + ctm[5]
                assertEquals("${c.name} stamp (0,0).x", ex0, ax, tol)
                assertEquals("${c.name} stamp (0,0).y", ey0, ay, tol)
                assertEquals("${c.name} stamp (1,1).x", ex1, bx, tol)
                assertEquals("${c.name} stamp (1,1).y", ey1, by, tol)
            }
        }
    }

    /** CTM (a b c d e f) останнього оператора Do на сторінці; відстежує q/Q/cm. */
    private fun lastImageCtm(page: PDPage): FloatArray {
        val tokens = PDFStreamParser(page).also { it.parse() }.tokens
        var ctm = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        val stack = ArrayDeque<FloatArray>()
        val operands = ArrayList<Any>()
        var result: FloatArray? = null
        for (t in tokens) {
            if (t is Operator) {
                when (t.name) {
                    "q" -> stack.addLast(ctm.copyOf())
                    "Q" -> if (stack.isNotEmpty()) ctm = stack.removeLast()
                    "cm" -> {
                        val n = operands.map { (it as COSNumber).floatValue() }
                        // новий CTM = m × ctm
                        val (a, b, c, d, e, f) = n
                        ctm = floatArrayOf(
                            a * ctm[0] + b * ctm[2], a * ctm[1] + b * ctm[3],
                            c * ctm[0] + d * ctm[2], c * ctm[1] + d * ctm[3],
                            e * ctm[0] + f * ctm[2] + ctm[4], e * ctm[1] + f * ctm[3] + ctm[5]
                        )
                    }
                    "Do" -> result = ctm.copyOf()
                }
                operands.clear()
            } else {
                operands.add(t)
            }
        }
        return result ?: throw AssertionError("Оператор Do не знайдено")
    }

    private operator fun <T> List<T>.component6(): T = this[5]

    // ---------- Текст ----------

    @Test
    fun freeTextPlacedCorrectlyOnAllRotationsAndBoxes() = runBlocking {
        val x = 40f; val y = 60f; val fontSize = 12f
        val text = "Hello"
        for (c in cases()) {
            val src = createPdf(c, "${counter++}_text_src.pdf")
            val out = tempFolder.newFile("out_${counter++}.pdf")
            val result = engine.addFreeText(
                mockContext, uri(src), 0,
                FreeTextData(text, x, y, fontSize, 0xFF0000FF.toInt()), 1f, 1f, uri(out)
            )
            assertTrue("${c.name}: $result", result is PdfOperationResult.Success)
            PDDocument.load(out).use { doc ->
                val collected = ArrayList<TextPosition>()
                val stripper = object : PDFTextStripper() {
                    override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
                        collected.addAll(textPositions)
                        super.writeString(string, textPositions)
                    }
                }
                val extracted = stripper.getText(doc)
                assertTrue("${c.name}: текст не знайдено: '$extracted'", extracted.contains(text))
                val letters = collected.filter { !it.unicode.isBlank() }
                assertEquals(c.name, text.length, letters.size)
                // TextPosition.getX()/getY(): незалежна інтерпретація PDFBox — простір видимої сторінки
                // (початок угорі ліворуч, враховує CropBox і /Rotate), Y — базова лінія.
                val first = letters.first()
                assertEquals("${c.name} text x", x, first.x, tol)
                assertEquals("${c.name} text baseline y", y + fontSize, first.y, tol)
                // Текст стоїть рівно: літери йдуть вправо на видимій сторінці, на одній базовій лінії.
                val last = letters.last()
                assertTrue("${c.name}: текст не йде вправо (${first.x} -> ${last.x})", last.x > first.x + 15f)
                assertEquals("${c.name} baseline last", first.y, last.y, tol)
            }
        }
    }
}
