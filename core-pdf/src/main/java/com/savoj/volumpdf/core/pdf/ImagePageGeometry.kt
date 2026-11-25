package com.savoj.volumpdf.core.pdf

/**
 * Геометрія сторінки PDF, створеної із зображення (O-1, O1-3).
 *
 * Правило: сторінка має пропорції зображення (з урахуванням повороту) і ніколи не ширша за 595 pt
 * та не вища за 842 pt (A4 у портретній орієнтації), незалежно від орієнтації зображення.
 * Масштаб = min(1, 595/ширина, 842/висота): великі зображення лише зменшуються пропорційно,
 * малі лишаються в природному розмірі (1 px = 1 pt). Піксельні дані не чіпаються: змінюється
 * тільки розмір сторінки й матриця малювання. Поворот на 90/180/270 робиться через /Rotate сторінки
 * (без перекодування пікселів); розміри для масштабу беруться ПІСЛЯ повороту.
 */
internal object ImagePageGeometry {
    const val A4_SHORT_PT = 595f
    const val A4_LONG_PT = 842f

    /** Приводить кут до 0/90/180/270; нестандартні значення дають 0. */
    fun normalizeRotation(degrees: Int): Int {
        val d = ((degrees % 360) + 360) % 360
        return if (d % 90 == 0) d else 0
    }

    private fun scale(widthPx: Int, heightPx: Int, rotation: Int): Float {
        val w = widthPx.coerceAtLeast(1).toFloat()
        val h = heightPx.coerceAtLeast(1).toFloat()
        val swap = normalizeRotation(rotation) % 180 != 0
        val dw = if (swap) h else w
        val dh = if (swap) w else h
        return minOf(1f, A4_SHORT_PT / dw, A4_LONG_PT / dh)
    }

    /**
     * Розмір MediaBox (і малювання зображення) в pt, до застосування /Rotate.
     * Для rotation = 0 збігається з видимим розміром.
     */
    fun pageSizePt(widthPx: Int, heightPx: Int, rotation: Int = 0): Pair<Float, Float> {
        val s = scale(widthPx, heightPx, rotation)
        return Pair(widthPx.coerceAtLeast(1) * s, heightPx.coerceAtLeast(1) * s)
    }

    /** Видимий розмір сторінки в pt після повороту. */
    fun displayedSizePt(widthPx: Int, heightPx: Int, rotation: Int = 0): Pair<Float, Float> {
        val (w, h) = pageSizePt(widthPx, heightPx, rotation)
        return if (normalizeRotation(rotation) % 180 != 0) Pair(h, w) else Pair(w, h)
    }
}
