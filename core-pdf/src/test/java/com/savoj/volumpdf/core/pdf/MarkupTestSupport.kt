package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import java.io.File

/** Спільна інфраструктура JVM-тестів двигунів розмітки: фіктивний Context/Uri, розбір потоку вмісту. */
abstract class MarkupTestSupport {

    @get:Rule
    val tempFolder = TemporaryFolder()

    protected lateinit var mockContext: Context

    @Before
    fun setUpContext() {
        mockContext = Mockito.mock(Context::class.java)
        val mockAssets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.assets).thenReturn(mockAssets)
        Mockito.`when`(mockContext.cacheDir).thenReturn(tempFolder.root)
        Mockito.doAnswer { inv ->
            val path = inv.getArgument<String>(0)
            if (path == MarkupLayout.FONT_ASSET_PATH) fontFile().inputStream()
            else pdfboxAsset(path) ?: throw java.io.FileNotFoundException("Asset not found: $path")
        }.`when`(mockAssets).open(Mockito.anyString())
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(mockContext)
    }

    /** Той самий файл, що йде в asset застосунку. */
    protected fun fontFile(): File = File("src/main/assets/fonts/Roboto-Regular.ttf").also {
        check(it.exists()) { "Немає ${it.absolutePath}" }
    }

    protected fun newMarkupEngine() = PdfBoxMarkupEngine { fontFile().inputStream() }

    private fun pdfboxAsset(path: String): java.io.InputStream? {
        javaClass.classLoader?.getResourceAsStream(path)?.let { return it }
        javaClass.classLoader?.getResourceAsStream("assets/$path")?.let { return it }
        val home = System.getProperty("user.home")
        val dir = File(home, ".gradle/caches/modules-2/files-2.1/com.tom-roush/pdfbox-android/2.0.27.0")
        val aar = dir.takeIf { it.exists() }?.walkTopDown()
            ?.firstOrNull { it.name.endsWith(".aar") && it.name.contains("pdfbox") } ?: return null
        java.util.zip.ZipFile(aar).use { zip ->
            val e = zip.getEntry("assets/$path") ?: zip.getEntry(path) ?: return null
            return java.io.ByteArrayInputStream(zip.getInputStream(e).use { it.readBytes() })
        }
    }

    protected fun uri(file: File): Uri {
        val u = Mockito.mock(Uri::class.java)
        Mockito.`when`(u.scheme).thenReturn("file")
        Mockito.`when`(u.path).thenReturn(file.absolutePath)
        Mockito.`when`(u.toString()).thenReturn("file://${file.absolutePath}")
        return u
    }

    protected fun pt(x: Float, y: Float): PointF = PointF().also { it.x = x; it.y = y }

    protected fun mockBitmap(): Bitmap {
        val bmp = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bmp.width).thenReturn(10)
        Mockito.`when`(bmp.height).thenReturn(10)
        Mockito.`when`(bmp.config).thenReturn(Bitmap.Config.ARGB_8888)
        Mockito.`when`(bmp.hasAlpha()).thenReturn(false)
        Mockito.doAnswer { inv -> inv.getArgument<IntArray>(0).fill(0xFF0000FF.toInt()); null }
            .`when`(bmp).getPixels(any(IntArray::class.java), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt())
        return bmp
    }

    /** Незалежна від PageGeometry таблиця очікувань (формули CONTRACTS v0.4 / MARKUP_STAGES, 1a). */
    protected fun expectedUser(rot: Int, box: PDRectangle, xv: Float, yv: Float): Pair<Float, Float> {
        val llx = box.lowerLeftX; val lly = box.lowerLeftY; val ury = box.upperRightY
        val w = box.width; val h = box.height
        return when (rot) {
            0 -> Pair(llx + xv, ury - yv)
            90 -> Pair(llx + yv, lly + xv)
            180 -> Pair(llx + w - xv, lly + yv)
            else -> Pair(llx + w - yv, lly + h - xv)
        }
    }

    /** Один викликаний stroke(): вершини контуру в просторі користувача (з урахуванням CTM) і стан. */
    data class StrokeInfo(
        val vertices: List<Pair<Float, Float>>, // moveTo, кінці lineTo/curveTo
        val curveCount: Int,
        val lineWidth: Float,
        val rgb: FloatArray,
        val strokeAlpha: Float,
        val lineCap: String,
        val lineJoin: String
    )

    /**
     * Розбирає потік вмісту сторінки власним інтерпретатором (m, l, c, S, w, J, j, RG, gs, q, Q, cm):
     * PDFGraphicsStreamEngine у JVM-тестах непридатний, бо android.jar-заглушка PointF не зберігає координат.
     */
    fun probeStrokes(page: PDPage): List<StrokeInfo> {
        class St(var ctm: FloatArray, var w: Float, var cap: Int, var join: Int, var rgb: FloatArray, var alpha: Float) {
            fun copy() = St(ctm.copyOf(), w, cap, join, rgb.copyOf(), alpha)
        }
        var st = St(floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f), 1f, 0, 0, floatArrayOf(0f, 0f, 0f), 1f)
        val stack = ArrayDeque<St>()
        val out = ArrayList<StrokeInfo>()
        val verts = ArrayList<Pair<Float, Float>>()
        var curves = 0
        val ops = ArrayList<com.tom_roush.pdfbox.cos.COSBase>()
        fun num(i: Int) = (ops[i] as COSNumber).floatValue()
        fun tx(x: Float, y: Float) = Pair(st.ctm[0] * x + st.ctm[2] * y + st.ctm[4], st.ctm[1] * x + st.ctm[3] * y + st.ctm[5])
        for (t in PDFStreamParser(page).also { it.parse() }.tokens) {
            if (t !is Operator) { ops.add(t as com.tom_roush.pdfbox.cos.COSBase); continue }
            when (t.name) {
                "q" -> stack.addLast(st.copy())
                "Q" -> if (stack.isNotEmpty()) st = stack.removeLast()
                "cm" -> {
                    val a = num(0); val b = num(1); val c = num(2); val d = num(3); val e = num(4); val f = num(5)
                    val m = st.ctm
                    st.ctm = floatArrayOf(
                        a * m[0] + b * m[2], a * m[1] + b * m[3],
                        c * m[0] + d * m[2], c * m[1] + d * m[3],
                        e * m[0] + f * m[2] + m[4], e * m[1] + f * m[3] + m[5]
                    )
                }
                "w" -> st.w = num(0)
                "J" -> st.cap = num(0).toInt()
                "j" -> st.join = num(0).toInt()
                "RG" -> st.rgb = floatArrayOf(num(0), num(1), num(2))
                "gs" -> {
                    val gs = page.resources.getExtGState(ops[0] as COSName)
                    gs.strokingAlphaConstant?.let { st.alpha = it }
                }
                "m" -> { verts.clear(); curves = 0; verts.add(tx(num(0), num(1))) }
                "l" -> verts.add(tx(num(0), num(1)))
                "c" -> { curves++; verts.add(tx(num(4), num(5))) }
                "S" -> {
                    val capName = arrayOf("BUTT", "ROUND", "SQUARE")[st.cap]
                    val joinName = arrayOf("MITER", "ROUND", "BEVEL")[st.join]
                    // ширина лінії в одиницях користувача (масштаб CTM у наших штрихах = 1)
                    out.add(StrokeInfo(ArrayList(verts), curves, st.w, st.rgb.copyOf(), st.alpha, capName, joinName))
                    verts.clear(); curves = 0
                }
                "f", "F", "f*", "n", "B", "b" -> { verts.clear(); curves = 0 }
            }
            ops.clear()
        }
        return out
    }

    protected fun saveNew(build: (PDDocument) -> Unit, name: String): File {
        val f = tempFolder.newFile(name)
        PDDocument().use { doc -> build(doc); doc.save(f) }
        return f
    }
}

/** Розбір штрихів без інфраструктури Context (для старих тестів). */
object MarkupProbe {
    private val support = object : MarkupTestSupport() {}
    fun probeStrokes(page: PDPage) = support.probeStrokes(page)
}
