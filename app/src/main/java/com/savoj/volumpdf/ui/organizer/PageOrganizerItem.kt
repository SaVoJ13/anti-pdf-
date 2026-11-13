package com.savoj.volumpdf.ui.organizer

import com.savoj.volumpdf.core.pdf.model.PageTransform

/**
 * Елемент візуальної сітки сторінок (ТОП-3).
 *
 * @property id Унікальний ідентифікатор елемента для стабільного DiffUtil у RecyclerView.
 * @property originalPageIndex 0-базований індекс сторінки у вихідному PDF-файлі.
 * @property rotationDegrees Поточний додатковий кут повороту за годинниковою стрілкою (0, 90, 180, 270).
 */
data class PageOrganizerItem(
    val id: Long,
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0
) {
    fun toPageTransform(): PageTransform = PageTransform(
        sourcePageIndex = originalPageIndex,
        rotationDegrees = rotationDegrees
    )
}
