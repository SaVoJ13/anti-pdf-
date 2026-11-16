package com.savoj.volumpdf.ui.common

/**
 * P1-4 (S-частина): чиста математика адаптивної розкладки для великих екранів, складаних і split-screen.
 * Кількість колонок залежить від ФАКТИЧНОЇ ширини вікна/списку, а не від типу пристрою.
 */
object AdaptiveLayoutLogic {
    /** Максимальна ширина основного вмісту (dp); ширші екрани мають поля. Збігається з @dimen/content_max_width. */
    const val CONTENT_MAX_WIDTH_DP = 840

    /** Мінімальна ширина елемента списку «Останні» й карток «Інструментів» (dp). */
    const val LIST_ITEM_MIN_DP = 340f
    const val LIST_MAX_COLUMNS = 3

    /** Мінімальна ширина мініатюри в організаторі сторінок (dp). */
    const val PAGE_THUMB_MIN_DP = 170f
    const val PAGE_MAX_COLUMNS = 6

    /** Кількість колонок для ширини [widthDp]; мінімум 1, максимум [maxColumns]. */
    fun columnsFor(widthDp: Float, minItemDp: Float, maxColumns: Int): Int {
        if (widthDp <= 0f || minItemDp <= 0f) return 1
        return (widthDp / minItemDp).toInt().coerceIn(1, maxColumns.coerceAtLeast(1))
    }

    /** Ширина вмісту після обмеження: не більше [maxPx], якщо [maxPx] > 0. */
    fun cappedWidth(availablePx: Int, maxPx: Int): Int =
        if (maxPx > 0 && availablePx > maxPx) maxPx else availablePx
}
