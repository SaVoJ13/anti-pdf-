package com.savoj.volumpdf.core.pdf

/**
 * Геометрія сторінки PDF, створеної із зображення (O-1).
 *
 * Правило: сторінка має пропорції зображення, орієнтація (портрет/альбом) береться з нього,
 * а сама сторінка вписується в межі A4 (595x842 pt, у альбомній орієнтації 842x595).
 * Зображення більше за A4 лише зменшується (масштаб малювання), менше або рівне за A4 лишається
 * в природному розмірі (1 px = 1 pt). Піксельні дані зображення не чіпаються: змінюється тільки
 * розмір сторінки й матриця малювання.
 */
internal object ImagePageGeometry {
    const val A4_SHORT_PT = 595f
    const val A4_LONG_PT = 842f

    /** Розмір сторінки в pt для зображення [widthPx] x [heightPx]. */
    fun pageSizePt(widthPx: Int, heightPx: Int): Pair<Float, Float> {
        val w = widthPx.coerceAtLeast(1).toFloat()
        val h = heightPx.coerceAtLeast(1).toFloat()
        val landscape = w > h
        val boxW = if (landscape) A4_LONG_PT else A4_SHORT_PT
        val boxH = if (landscape) A4_SHORT_PT else A4_LONG_PT
        val scale = minOf(1f, boxW / w, boxH / h)
        return Pair(w * scale, h * scale)
    }
}
