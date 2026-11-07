package com.savoj.volumpdf.core.pdf.model

enum class MarkKind { CHECK, CROSS }

/**
 * Об'єкт розмітки на сторінці (CONTRACTS v0.4, §1.5). Усі координати — у просторі `PageViewPoint`:
 * pt; початок — лівий верхній кут видимої області сторінки (CropBox після /Rotate); x вправо, y вниз.
 */
sealed interface PageMarkup {
    val id: String
    val pageIndex: Int // 0-based; збігається з PdfPoint.pageNum

    // (x, y) — лівий верхній кут рамки тексту
    data class Text(
        override val id: String, override val pageIndex: Int,
        val x: Float, val y: Float,
        val text: String, val fontSizePt: Float = 14f, val color: Int
    ) : PageMarkup

    // (x, y) — центр позначки; малюється векторно, без шрифту
    data class Mark(
        override val id: String, override val pageIndex: Int,
        val x: Float, val y: Float,
        val kind: MarkKind, val sizePt: Float = 16f, val color: Int
    ) : PageMarkup

    // точки штрихів — у PageViewPoint-просторі сторінки pageIndex
    data class Ink(
        override val id: String, override val pageIndex: Int,
        val strokes: List<InkStroke>
    ) : PageMarkup

    // (left, top, width, height) — у PageViewPoint-просторі
    data class Signature(
        override val id: String, override val pageIndex: Int,
        val bitmap: android.graphics.Bitmap,
        val left: Float, val top: Float, val width: Float, val height: Float
    ) : PageMarkup
}
