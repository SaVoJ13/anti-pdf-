package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.tom_roush.fontbox.ttf.CmapLookup
import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * [MarkupEngine] на базі PdfBox-Android: текст (Roboto Regular, підмножина), ✓/✗, рукописне малювання
 * і підпис малюються в потоці вмісту сторінки; форми заповнюються в тому ж завантаженні документа.
 *
 * @param fontStream джерело байтів шрифту (кожен виклик — новий потік). Для застосунку — asset
 * [MarkupLayout.FONT_ASSET_PATH]; у JVM-тестах — файл.
 */
class PdfBoxMarkupEngine(private val fontStream: () -> InputStream) : MarkupEngine {

    constructor(context: Context) : this({ context.applicationContext.assets.open(MarkupLayout.FONT_ASSET_PATH) })

    // Таблиця символів шрифту читається один раз
    private val cmap: CmapLookup by lazy {
        fontStream().use { stream ->
            TTFParser().parse(stream).use { ttf -> ttf.unicodeCmapLookup }
        }
    }

    override fun findUnsupportedChars(text: String): String {
        val out = StringBuilder()
        val seen = HashSet<Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == '\n'.code || cp == '\r'.code) continue // розрив рядка обробляє двигун
            if (cmap.getGlyphId(cp) == 0 && seen.add(cp)) out.appendCodePoint(cp)
        }
        return out.toString()
    }

    override suspend fun applyEdits(
        context: Context,
        sourceUri: Uri,
        markups: List<PageMarkup>,
        formUpdates: List<FormFieldUpdate>,
        outputUri: Uri,
        password: String?
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        try {
            PdfBoxPageOperationsEngine.init(context)

            // Перевірки, що не потребують документа: нічого не завантажуємо й не пишемо
            for (m in markups) if (m is PageMarkup.Text) {
                val bad = findUnsupportedChars(m.text)
                if (bad.isNotEmpty()) {
                    throw IllegalArgumentException("Текст «${m.id}» містить символи, яких немає у шрифті: $bad")
                }
            }

            val doc = PdfIo.loadDocument(context, sourceUri, password)
            try {
                val totalPages = doc.numberOfPages

                // 1. Форми — першими, у тому самому завантаженні
                FormFiller.apply(doc, formUpdates)

                // 2. Розмітка — у заданому порядку; усе перевіряється й малюється до збереження
                val font: PDType0Font? =
                    if (markups.any { it is PageMarkup.Text && it.text.isNotEmpty() }) {
                        fontStream().use { PDType0Font.load(doc, it, true) } // підмножина
                    } else null

                for (m in markups) {
                    if (m.pageIndex !in 0 until totalPages) {
                        throw IndexOutOfBoundsException(
                            "Індекс сторінки ${m.pageIndex} виходить за межі документа (0..${totalPages - 1})"
                        )
                    }
                    val page = doc.getPage(m.pageIndex)
                    val g = PageGeometry.of(page)
                    val bounds = boundsOf(m, font) ?: continue // порожній об'єкт: нічого малювати
                    if (bounds.right <= 0f || bounds.left >= g.visibleWidth ||
                        bounds.bottom <= 0f || bounds.top >= g.visibleHeight
                    ) {
                        throw IllegalArgumentException(
                            "Об'єкт «${m.id}» повністю за межами сторінки ${m.pageIndex + 1}"
                        )
                    }
                    MarkupDrawing.newStream(doc, page).use { cs ->
                        when (m) {
                            is PageMarkup.Text -> MarkupDrawing.drawText(cs, g, font!!, m)
                            is PageMarkup.Mark -> MarkupDrawing.drawMark(cs, g, m)
                            is PageMarkup.Ink -> MarkupDrawing.drawInk(cs, g, m.strokes)
                            is PageMarkup.Signature -> MarkupDrawing.drawSignature(
                                cs, doc, g, m.bitmap, m.left, m.top, m.width, m.height
                            )
                        }
                    }
                }

                PdfIo.keepEncryption(doc, password)
                PdfIo.saveSafely(context, doc, outputUri)
                PdfOperationResult.Success(outputUri = outputUri, pagesCount = totalPages)
            } finally {
                doc.close()
            }
        } catch (cause: Throwable) {
            if (cause is CancellationException) throw cause
            PdfIo.mapToPdfError(cause, "Помилка збереження розмітки")
        }
    }

    /** Охоплювальний прямокутник об'єкта в PageViewPoint; null — об'єкт порожній і пропускається. */
    private fun boundsOf(m: PageMarkup, font: PDType0Font?): MarkupDrawing.ViewRect? {
        val r: MarkupDrawing.ViewRect? = when (m) {
            is PageMarkup.Text -> {
                if (m.text.isEmpty()) return null
                val fs = m.fontSizePt
                val lines = MarkupDrawing.textLines(m.text)
                val w = lines.maxOf { font!!.getStringWidth(it) / 1000f * fs }
                MarkupDrawing.ViewRect(
                    m.x, m.y, m.x + w,
                    m.y + (MarkupLayout.TEXT_ASCENT_RATIO + (lines.size - 1) * MarkupLayout.TEXT_LINE_HEIGHT_RATIO) * fs
                )
            }
            is PageMarkup.Mark -> {
                val h = m.sizePt / 2f
                MarkupDrawing.ViewRect(m.x - h, m.y - h, m.x + h, m.y + h)
            }
            is PageMarkup.Ink -> {
                val pts = m.strokes.flatMap { s ->
                    val half = (if (s.strokeWidth > 0f) s.strokeWidth else 2f) / 2f
                    s.points.flatMap { listOf(it.x - half to it.y - half, it.x + half to it.y + half) }
                }
                if (pts.isEmpty()) return null
                MarkupDrawing.ViewRect(
                    pts.minOf { it.first }, pts.minOf { it.second },
                    pts.maxOf { it.first }, pts.maxOf { it.second }
                )
            }
            is PageMarkup.Signature ->
                MarkupDrawing.ViewRect(m.left, m.top, m.left + m.width, m.top + m.height)
        }
        if (r != null && listOf(r.left, r.top, r.right, r.bottom).any { it.isNaN() || it.isInfinite() }) {
            throw IllegalArgumentException("Об'єкт «${m.id}» має некоректні координати")
        }
        return r
    }
}
