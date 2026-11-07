package com.savoj.volumpdf.core.pdf

import android.graphics.Bitmap
import android.graphics.PointF
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix

/**
 * Спільний код малювання в потоці вмісту сторінки (PDPageContentStream, APPEND, resetContext=true).
 * Усі вхідні координати — у просторі PageViewPoint; у простір користувача їх переводить [PageGeometry].
 * Кожен об'єкт малюється в ізольованому графічному стані (q ... Q).
 */
internal object MarkupDrawing {

    private const val DEFAULT_INK_WIDTH = 2f

    /** Прямокутник у PageViewPoint (left, top, right, bottom). */
    data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun newStream(doc: PDDocument, page: com.tom_roush.pdfbox.pdmodel.PDPage): PDPageContentStream =
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)

    private fun red(argb: Int) = ((argb shr 16) and 0xFF) / 255f
    private fun green(argb: Int) = ((argb shr 8) and 0xFF) / 255f
    private fun blue(argb: Int) = (argb and 0xFF) / 255f
    private fun alpha(argb: Int) = ((argb ushr 24) and 0xFF) / 255f

    /** Напівпрозорість через ExtGState (CA для ліній, ca для заливки/тексту). */
    private fun applyAlpha(cs: PDPageContentStream, argb: Int) {
        val a = alpha(argb)
        if (a < 1f) {
            val gs = PDExtendedGraphicsState()
            gs.strokingAlphaConstant = a
            gs.nonStrokingAlphaConstant = a
            cs.setGraphicsStateParameters(gs)
        }
    }

    // ---------- Ink ----------

    fun drawInk(cs: PDPageContentStream, g: PageGeometry, strokes: List<InkStroke>) {
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val width = if (stroke.strokeWidth > 0f) stroke.strokeWidth else DEFAULT_INK_WIDTH
            val pts = if (stroke.points.size == 1) {
                val s = stroke.points[0]
                listOf(s, pt(s.x + 0.1f, s.y + 0.1f)) // мінімальний відрізок: з круглими кінцями це крапка
            } else stroke.points
            cs.saveGraphicsState()
            applyAlpha(cs, stroke.color)
            cs.setStrokingColor(red(stroke.color), green(stroke.color), blue(stroke.color))
            cs.setLineWidth(width)
            cs.setLineCapStyle(1)
            cs.setLineJoinStyle(1)
            tracePath(cs, g, pts)
            cs.stroke()
            cs.restoreGraphicsState()
        }
    }

    /**
     * Той самий контур, що в InkCanvasView: moveTo(p0); для кожної наступної точки
     * quadTo(попередня, середина(попередня, поточна)); наприкінці lineTo(остання).
     * Квадратична крива переводиться в кубічну (афінне перетворення не змінює форму).
     */
    private fun tracePath(cs: PDPageContentStream, g: PageGeometry, pts: List<PointF>) {
        val u = pts.map { g.toUser(it.x, it.y) }
        cs.moveTo(u[0].x, u[0].y)
        var start = u[0] // початок поточної кривої = кінець попередньої
        var prev = u[0]
        for (i in 1 until u.size - 1) {
            val cur = u[i]
            val mid = UserPoint((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
            quadTo(cs, start, prev, mid)
            start = mid
            prev = cur
        }
        val last = u[u.size - 1]
        cs.lineTo(last.x, last.y)
    }

    private fun quadTo(cs: PDPageContentStream, p0: UserPoint, c: UserPoint, p2: UserPoint) {
        val c1x = p0.x + 2f / 3f * (c.x - p0.x)
        val c1y = p0.y + 2f / 3f * (c.y - p0.y)
        val c2x = p2.x + 2f / 3f * (c.x - p2.x)
        val c2y = p2.y + 2f / 3f * (c.y - p2.y)
        cs.curveTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }

    // ---------- Mark ----------

    fun drawMark(cs: PDPageContentStream, g: PageGeometry, mark: PageMarkup.Mark) {
        val size = mark.sizePt
        cs.saveGraphicsState()
        applyAlpha(cs, mark.color)
        cs.setStrokingColor(red(mark.color), green(mark.color), blue(mark.color))
        cs.setLineWidth(MarkupLayout.MARK_STROKE_RATIO * size)
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)
        for (line in MarkupLayout.markPolylines(mark.kind)) {
            line.forEachIndexed { i, p ->
                val u = g.toUser(mark.x - size / 2f + p.x * size, mark.y - size / 2f + p.y * size)
                if (i == 0) cs.moveTo(u.x, u.y) else cs.lineTo(u.x, u.y)
            }
            cs.stroke()
        }
        cs.restoreGraphicsState()
    }

    // ---------- Text ----------

    /** Рядки тексту: розрив лише за '\n' ('\r' відкидається). */
    fun textLines(text: String): List<String> = text.replace("\r", "").split('\n')

    fun drawText(cs: PDPageContentStream, g: PageGeometry, font: PDFont, t: PageMarkup.Text) {
        val fs = t.fontSizePt
        cs.saveGraphicsState()
        applyAlpha(cs, t.color)
        cs.beginText()
        cs.setFont(font, fs)
        cs.setNonStrokingColor(red(t.color), green(t.color), blue(t.color))
        textLines(t.text).forEachIndexed { i, line ->
            if (line.isEmpty()) return@forEachIndexed
            val baseline = t.y + MarkupLayout.TEXT_ASCENT_RATIO * fs + i * MarkupLayout.TEXT_LINE_HEIGHT_RATIO * fs
            val m = g.contentMatrix(t.x, baseline)
            cs.setTextMatrix(Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
            cs.showText(line)
        }
        cs.endText()
        cs.restoreGraphicsState()
    }

    // ---------- Signature ----------

    fun drawSignature(
        cs: PDPageContentStream, doc: PDDocument, g: PageGeometry,
        bitmap: Bitmap, left: Float, top: Float, width: Float, height: Float
    ) {
        val pdImage = try {
            LosslessFactory.createFromImage(doc, bitmap)
        } catch (_: Throwable) {
            JPEGFactory.createFromImage(doc, bitmap)
        }
        val w = maxOf(width, 1f)
        val h = maxOf(height, 1f)
        // Лівий нижній кут зображення; осі — «праворуч» і «угору» видимої сторінки
        val m = g.contentMatrix(left, top + h)
        cs.saveGraphicsState()
        cs.drawImage(pdImage, Matrix(m[0] * w, m[1] * w, m[2] * h, m[3] * h, m[4], m[5]))
        cs.restoreGraphicsState()
    }

    private fun pt(x: Float, y: Float): PointF = PointF().also { it.x = x; it.y = y }
}
