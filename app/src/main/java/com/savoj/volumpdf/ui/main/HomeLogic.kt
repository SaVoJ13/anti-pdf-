package com.savoj.volumpdf.ui.main

/** Чиста логіка головного екрана (М9–М11), без Android-залежностей. */
object HomeLogic {
    /** Нижче цієї висоти екрана (dp, ландшафт) розкриті дії розкладаються в ряд, а не в колонку. */
    const val COMPACT_HEIGHT_DP = 480

    fun speedDialHorizontal(screenHeightDp: Int): Boolean = screenHeightDp < COMPACT_HEIGHT_DP

    /** Розкриття дій видиме лише на вкладці «Бібліотека» (позиція 0). */
    fun fabVisibleOnPage(position: Int): Boolean = position == 0

    /** Розкриття можливе лише коли головна кнопка видима. */
    fun effectiveExpanded(expanded: Boolean, position: Int): Boolean = expanded && fabVisibleOnPage(position)

    /** Дії розкриття, що показуються в порядку зверху вниз: «Створити PDF», «Сканувати». Основна дія (відкрити) окремо. */
    enum class SpeedDialAction { CREATE_PDF, SCAN }

    val speedDialActions: List<SpeedDialAction> = listOf(SpeedDialAction.CREATE_PDF, SpeedDialAction.SCAN)

    /** Кнопки порожнього стану (без дублю головної кнопки «Відкрити PDF» та «Сканувати» з розкриття). */
    data class EmptyStateButtons(val create: Boolean, val clearSearch: Boolean)

    fun emptyStateButtons(hasSearch: Boolean): EmptyStateButtons =
        if (hasSearch) EmptyStateButtons(create = false, clearSearch = true)
        else EmptyStateButtons(create = true, clearSearch = false)
}
