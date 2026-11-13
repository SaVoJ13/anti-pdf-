package com.savoj.volumpdf.core.pdf.model

/**
 * Специфікація перетворення сторінки для візуального організатора (ТОП-3).
 *
 * @property sourcePageIndex 0-базований індекс вихідної сторінки в початковому документі.
 * @property rotationDegrees Додатковий кут повороту за годинниковою стрілкою (0, 90, 180, 270).
 */
data class PageTransform(
    val sourcePageIndex: Int,
    val rotationDegrees: Int = 0
)
