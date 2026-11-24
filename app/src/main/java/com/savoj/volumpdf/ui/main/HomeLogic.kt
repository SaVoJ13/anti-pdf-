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

    /** Бібліотечні дії toolbar не повинні з'являтися на вкладці «Інструменти». */
    data class ToolbarState(
        val showLibraryActions: Boolean,
        val showLibraryFab: Boolean
    )

    fun toolbarState(position: Int): ToolbarState = ToolbarState(
        showLibraryActions = position == 0,
        showLibraryFab = fabVisibleOnPage(position)
    )

    /** Дії розкриття, що показуються в порядку зверху вниз: «Створити PDF», «Сканувати». Основна дія (відкрити) окремо. */
    enum class SpeedDialAction { CREATE_PDF, SCAN }

    val speedDialActions: List<SpeedDialAction> = listOf(SpeedDialAction.CREATE_PDF, SpeedDialAction.SCAN)

    /**
     * Порожній стан відрізняє справді порожню бібліотеку від нульового результату
     * пошуку або фільтра «Обране». Інакше користувачеві помилково пропонується
     * створити PDF, хоча його документи просто приховані фільтром.
     */
    enum class EmptyState { LIBRARY_EMPTY, SEARCH_NO_RESULTS, FAVORITES_NO_RESULTS }

    fun emptyState(hasSearch: Boolean, favoritesOnly: Boolean): EmptyState = when {
        hasSearch -> EmptyState.SEARCH_NO_RESULTS
        favoritesOnly -> EmptyState.FAVORITES_NO_RESULTS
        else -> EmptyState.LIBRARY_EMPTY
    }

    /** Кнопки порожнього стану без дублю глобальних «Відкрити PDF» та «Сканувати». */
    data class EmptyStateButtons(val create: Boolean, val reset: Boolean)

    fun emptyStateButtons(state: EmptyState): EmptyStateButtons = when (state) {
        EmptyState.LIBRARY_EMPTY -> EmptyStateButtons(create = true, reset = false)
        EmptyState.SEARCH_NO_RESULTS,
        EmptyState.FAVORITES_NO_RESULTS -> EmptyStateButtons(create = false, reset = true)
    }

    /**
     * Меню потрібно оновити лише коли змінилася нефільтрована наявність історії
     * («Очистити історію»). Зміна відфільтрованого списку під час пошуку не повинна
     * перебудовувати меню: це згортає SearchView (дефект D2-1).
     */
    fun clearHistoryStateChanged(previous: Boolean?, current: Boolean): Boolean = previous != current
}
