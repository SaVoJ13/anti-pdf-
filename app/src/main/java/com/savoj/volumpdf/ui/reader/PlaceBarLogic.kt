package com.savoj.volumpdf.ui.reader

import kotlin.math.hypot

/** Слот нижнього рядка інструментів. Маркер у A1 не реалізовано (потрібне ядро): місце під нього зарезервоване у розмітці. */
enum class BarSlot { TEXT, PEN, SELECT, ACTION }

/** Секції картки параметрів. */
enum class CardSection { ACTION_CHOOSER, SIZE, COLOR, PRESETS, STRAIGHTEN, IMAGE_CONTROLS }

/**
 * Чиста логіка нижнього рядка й картки параметрів (без Android): який слот активний, що робить повторний тап,
 * які секції показує картка. Формат збереження й ядро не торкаються: слоти лише вибирають наявні [PlaceTool].
 */
object PlaceBarLogic {
    /** Інструменти слота «дія»: ✓, ✗, підпис, зображення. */
    val ACTION_TOOLS: List<PlaceTool> =
        listOf(PlaceTool.CHECK, PlaceTool.CROSS, PlaceTool.SIGNATURE, PlaceTool.IMAGE)

    /** Палітра: 9 кольорів (RGB, без альфи). Власний колір поза палітрою. */
    val PALETTE: IntArray = intArrayOf(
        0xFF000000.toInt(), 0xFFD32F2F.toInt(), 0xFFF57C00.toInt(), 0xFFFBC02D.toInt(), 0xFF388E3C.toInt(),
        0xFF1976D2.toInt(), 0xFF3949AB.toInt(), 0xFF7B1FA2.toInt(), 0xFF757575.toInt()
    )

    fun slotOf(tool: PlaceTool): BarSlot = when (tool) {
        PlaceTool.TEXT -> BarSlot.TEXT
        PlaceTool.INK -> BarSlot.PEN
        PlaceTool.HAND -> BarSlot.SELECT
        PlaceTool.CHECK, PlaceTool.CROSS, PlaceTool.SIGNATURE, PlaceTool.IMAGE -> BarSlot.ACTION
    }

    /** Інструмент, який вмикає тап по неактивному слоту; для «дії» — останній вибраний із її інструментів. */
    fun toolFor(slot: BarSlot, lastActionTool: PlaceTool): PlaceTool = when (slot) {
        BarSlot.TEXT -> PlaceTool.TEXT
        BarSlot.PEN -> PlaceTool.INK
        BarSlot.SELECT -> PlaceTool.HAND
        BarSlot.ACTION -> if (lastActionTool in ACTION_TOOLS) lastActionTool else PlaceTool.CHECK
    }

    /** Останній інструмент слота «дія»: поточний, якщо він із цього слота, інакше попередній. */
    fun rememberActionTool(current: PlaceTool, previous: PlaceTool): PlaceTool =
        if (current in ACTION_TOOLS) current else if (previous in ACTION_TOOLS) previous else PlaceTool.CHECK

    /** Результат тапу по слоту: інструмент і чи показана картка після тапу. */
    data class SlotTapResult(val tool: PlaceTool, val cardOpen: Boolean)

    /**
     * Тап по слоту: неактивний → вмикається (картка закрита); активний → повторний тап перемикає картку,
     * якщо для цілі є що налаштовувати ([hasOptions]).
     */
    fun onSlotTap(
        currentTool: PlaceTool, cardOpen: Boolean, tapped: BarSlot, lastActionTool: PlaceTool, hasOptions: Boolean
    ): SlotTapResult {
        if (tapped != slotOf(currentTool)) return SlotTapResult(toolFor(tapped, lastActionTool), false)
        return SlotTapResult(currentTool, if (hasOptions) !cardOpen else false)
    }

    /** Кнопка «параметри»: перемикає картку, якщо є що показати. */
    fun onOptionsTap(cardOpen: Boolean, hasOptions: Boolean): Boolean = if (hasOptions) !cardOpen else false

    /** Ціль параметрів: виділений об'єкт, інакше поточний інструмент. */
    fun target(tool: PlaceTool, selected: PlaceTool?): PlaceTool = selected ?: tool

    /** Секції картки для цілі; [hasSelection] — ціль це виділений об'єкт (тоді інструмент не міняється). */
    fun sections(target: PlaceTool, hasSelection: Boolean): List<CardSection> = when (target) {
        PlaceTool.CHECK, PlaceTool.CROSS ->
            if (hasSelection) listOf(CardSection.SIZE, CardSection.COLOR)
            else listOf(CardSection.ACTION_CHOOSER, CardSection.SIZE, CardSection.COLOR)
        PlaceTool.TEXT -> listOf(CardSection.SIZE, CardSection.COLOR)
        PlaceTool.INK ->
            if (hasSelection) listOf(CardSection.SIZE, CardSection.COLOR, CardSection.PRESETS)
            else listOf(CardSection.SIZE, CardSection.COLOR, CardSection.PRESETS, CardSection.STRAIGHTEN)
        PlaceTool.SIGNATURE ->
            if (hasSelection) listOf(CardSection.SIZE) else listOf(CardSection.ACTION_CHOOSER)
        PlaceTool.IMAGE ->
            if (hasSelection) listOf(CardSection.SIZE, CardSection.IMAGE_CONTROLS) else listOf(CardSection.ACTION_CHOOSER)
        PlaceTool.HAND -> emptyList()
    }

    fun hasOptions(tool: PlaceTool, selected: PlaceTool?): Boolean =
        sections(target(tool, selected), selected != null).isNotEmpty()

    /**
     * Сторінки налаштувань: «пресети» й «випрямлення» — друга сторінка, якщо є ще й інші секції; інакше одна сторінка.
     * Індикатор-крапки показується лише за більше ніж однієї сторінки.
     */
    fun pages(sections: List<CardSection>): List<List<CardSection>> {
        val second = sections.filter { it == CardSection.PRESETS || it == CardSection.STRAIGHTEN }
        val first = sections - second.toSet()
        return if (first.isEmpty() || second.isEmpty()) listOf(sections) else listOf(first, second)
    }

    /** Картка лишається відкритою лише в режимі, поки для цілі є параметри. */
    fun cardStillValid(cardOpen: Boolean, placeModeActive: Boolean, hasOptions: Boolean): Boolean =
        cardOpen && placeModeActive && hasOptions

    /** Чи підсвічений слот: лише слот поточного інструмента. */
    fun isActive(slot: BarSlot, tool: PlaceTool): Boolean = slotOf(tool) == slot

    /** true, якщо колір поза палітрою (показується позначка «власний»). */
    fun isCustomColor(rgb: Int): Boolean = PALETTE.none { (it and 0xFFFFFF) == (rgb and 0xFFFFFF) }
}

/** Випрямлення ліній при малюванні: майже пряма лінія стає відрізком із двох точок. Формат штриха не змінюється. */
object InkStraighten {
    /** Менше цього штрих не випрямляється (крапка, коротка риска), pt. */
    const val MIN_LENGTH_PT = 12f

    /** Допустиме відхилення від прямої: частка довжини, не менше [MIN_DEVIATION_PT]. */
    const val MAX_DEVIATION_RATIO = 0.06f
    const val MIN_DEVIATION_PT = 1.5f

    /** [xy] — x0,y0,x1,y1,…; повертає [xy] без змін або відрізок (x0,y0,xn,yn). */
    fun straighten(xy: FloatArray): FloatArray {
        val n = xy.size / 2
        if (n < 3) return xy
        val ax = xy[0]
        val ay = xy[1]
        val bx = xy[2 * (n - 1)]
        val by = xy[2 * (n - 1) + 1]
        val len = hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
        if (len < MIN_LENGTH_PT) return xy
        var maxDev = 0f
        for (i in 1 until n - 1) {
            val px = xy[2 * i]
            val py = xy[2 * i + 1]
            val dev = Math.abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / len
            if (dev > maxDev) maxDev = dev
        }
        val limit = maxOf(MIN_DEVIATION_PT, len * MAX_DEVIATION_RATIO)
        return if (maxDev <= limit) floatArrayOf(ax, ay, bx, by) else xy
    }
}
