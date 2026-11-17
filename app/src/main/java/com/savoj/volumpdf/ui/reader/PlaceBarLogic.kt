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

    /**
     * Палітра «Фон шрифту» за зразком Samsung Notes (8 кружечків у ряд + 9-й райдужний кружечок власного кольору):
     * білий, червоний, помаранчевий, жовтий, зелений (м'ятний), синій, фіолетовий та прозорий («Без фону» = 0).
     */
    val TEXT_BG_PALETTE: IntArray = intArrayOf(
        0xFFFFFFFF.toInt(),
        0xFFFF3B30.toInt(),
        0xFFFFA726.toInt(),
        0xFFFFEE58.toInt(),
        0xFF00E676.toInt(),
        0xFF2962FF.toInt(),
        0xFFAA00FF.toInt(),
        0x00000000
    )

    /** true, якщо вибрано непрозорий власний колір фону шрифту поза [TEXT_BG_PALETTE]. */
    fun isCustomBgColor(argb: Int): Boolean {
        if ((argb ushr 24) == 0) return false
        return TEXT_BG_PALETTE.none { (it ushr 24) != 0 && (it and 0xFFFFFF) == (argb and 0xFFFFFF) }
    }

    /** Кількість колонок палітри: у портреті 5 (два рядки), у ландшафті 10 (один рядок). */
    fun gridColumns(landscape: Boolean): Int = if (landscape) 10 else 5

    /** Розміри шрифту для пресетів стилю «Aa▾» (Заголовок 1, Заголовок 2, Текст 1, Текст 2). */
    val TEXT_STYLE_SIZES_PT: FloatArray = floatArrayOf(24f, 18f, 14f, 11f)

    /** Повний список розмірів шрифту (pt) для спливаючої вертикальної панелі вибору кеглю над клавіатурою. */
    val TEXT_SIZE_CHOICES: FloatArray = floatArrayOf(
        8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f, 17f, 18f, 19f, 20f, 21f, 22f,
        24f, 26f, 28f, 32f, 36f, 40f, 44f, 48f, 64f, 72f
    )

    /**
     * Крок зміни розміру/товщини кнопками «−» ([deltaSign] < 0) та «+» ([deltaSign] > 0) обабіч слайдера.
     * Значення обмежується допустимим діапазоном [PlaceMarkupLogic.sizeMin]..[PlaceMarkupLogic.sizeMax].
     */
    fun stepSize(target: PlaceTool, current: Float, deltaSign: Int): Float {
        if (deltaSign == 0 || target == PlaceTool.HAND) return current
        val min = PlaceMarkupLogic.sizeMin(target)
        val max = PlaceMarkupLogic.sizeMax(target)
        val dir = if (deltaSign > 0) 1 else -1
        val next = when (target) {
            PlaceTool.INK -> when {
                dir < 0 && current <= 1.5f -> 0.5f
                dir > 0 && current < 1f -> 1f
                else -> kotlin.math.round(current) + dir * 1f
            }
            PlaceTool.TEXT, PlaceTool.CHECK, PlaceTool.CROSS -> kotlin.math.round(current) + dir * 2f
            PlaceTool.SIGNATURE, PlaceTool.IMAGE -> kotlin.math.round(current) + dir * 10f
            PlaceTool.HAND -> current
        }
        return next.coerceIn(min, max)
    }

    /** Розраховані межі поля вводу тексту у вмістових координатах [placeTextRoot] (після paddingTop/paddingBottom). */
    data class EditorBoxBounds(
        val leftPx: Int,
        val topPx: Int,
        val widthPx: Int,
        val maxInputHeightPx: Int,
        val maxBottomPx: Int
    )

    /**
     * Обчислює безпечне положення й розміри живого поля вводу тексту (`placeTextEditorBox`), щоб воно:
     * 1) ніколи не перекривалося панеллю редагування (`placeTextCard`) або клавіатурою знизу ([topPx] + висота ≤ [maxBottomPx]);
     * 2) не заходило під верхній тулбар ([topPx] ≥ [minTopPx]);
     * 3) залишалося в межах екрана по горизонталі з полями [sideMarginPx].
     */
    fun computeEditorBoxBounds(
        availWidthPx: Int,
        availHeightPx: Int,
        minTopPx: Int,
        toolbarReservePx: Int,
        sideMarginPx: Int,
        minBoxWidthPx: Int,
        minBoxHeightPx: Int,
        desiredLeftPx: Int,
        desiredTopPx: Int,
        boxWidthPx: Int,
        boxHeightPx: Int,
        cardTopInContentPx: Int = -1,
        cardGapPx: Int = 0,
        hardMinTopPx: Int = Int.MAX_VALUE
    ): EditorBoxBounds {
        val safeSide = sideMarginPx.coerceAtLeast(0)
        val safeMinW = minBoxWidthPx.coerceAtLeast(60)
        val safeMinH = minBoxHeightPx.coerceAtLeast(36)
        val maxAllowedWidth = (availWidthPx - 2 * safeSide).coerceAtLeast(safeMinW)
        val clampedWidth = boxWidthPx.coerceIn(minOf(safeMinW, maxAllowedWidth), maxAllowedWidth)

        var maxBottom = availHeightPx - toolbarReservePx
        if (cardTopInContentPx > 0) {
            maxBottom = minOf(maxBottom, cardTopInContentPx - cardGapPx)
        }
        // Д3: у ландшафті з клавіатурою місця під тулбаром бракує. Замість того щоб лишити поле під панеллю-пігулкою,
        // піднімаємо його вище (до [hardMinTopPx], тобто над тулбар): вікно тексту лежить поверх усього.
        val preferredMinTop = minTopPx.coerceAtLeast(0)
        val floorTop = minOf(hardMinTopPx.coerceAtLeast(0), preferredMinTop)
        val safeMinTop = if (maxBottom - preferredMinTop >= safeMinH) preferredMinTop
        else (maxBottom - safeMinH).coerceIn(floorTop, preferredMinTop)
        maxBottom = maxBottom.coerceAtLeast(safeMinTop + safeMinH)

        val maxBoxTotalHeight = (maxBottom - safeMinTop).coerceAtLeast(safeMinH)
        val maxInputHeightPx = (maxBoxTotalHeight - safeMinH / 3).coerceAtLeast(safeMinH)
        val effectiveBoxHeight = boxHeightPx.coerceIn(safeMinH, maxBoxTotalHeight)

        val maxLeft = (availWidthPx - safeSide - clampedWidth).coerceAtLeast(safeSide)
        val clampedLeft = desiredLeftPx.coerceIn(safeSide, maxLeft)

        val maxTop = (maxBottom - effectiveBoxHeight).coerceAtLeast(safeMinTop)
        val clampedTop = desiredTopPx.coerceIn(safeMinTop, maxTop)

        return EditorBoxBounds(
            leftPx = clampedLeft,
            topPx = clampedTop,
            widthPx = clampedWidth,
            maxInputHeightPx = maxInputHeightPx,
            maxBottomPx = maxBottom
        )
    }

    /**
     * Пропорційно змінює розмір шрифту (pt) відповідно до збільшення або зменшення поля для тексту:
     * від [startSpanPx] до [currentSpanPx]. Результат округлюється до цілих pt у межах [minSizePt]..[maxSizePt].
     */
    fun scaleTextSizeByBoxRatio(
        startSizePt: Float,
        startSpanPx: Float,
        currentSpanPx: Float,
        minSizePt: Float = 8f,
        maxSizePt: Float = 72f
    ): Float {
        if (startSizePt.isNaN() || startSizePt.isInfinite()) return 14f.coerceIn(minSizePt, maxSizePt)
        if (startSpanPx <= 0f || currentSpanPx.isNaN() || currentSpanPx.isInfinite()) {
            return startSizePt.coerceIn(minSizePt, maxSizePt)
        }
        val ratio = (currentSpanPx / startSpanPx).coerceIn(0.1f, 10f)
        val raw = kotlin.math.round(startSizePt * ratio)
        return raw.coerceIn(minSizePt, maxSizePt)
    }

    /** Ширина поля вводу тексту (px), підлаштована під обраний розмір шрифту [sizePt]. */
    fun editorBoxWidthForSizePt(
        sizePt: Float,
        density: Float,
        minWidthPx: Int,
        maxWidthPx: Int
    ): Int {
        val safeDensity = if (density > 0f && !density.isNaN()) density else 1f
        val clampedPt = sizePt.coerceIn(8f, 72f)
        val targetDp = 148f + (clampedPt - 8f) * 5.5f
        val targetPx = (targetDp * safeDensity).toInt()
        val upper = maxWidthPx.coerceAtLeast(minWidthPx)
        return targetPx.coerceIn(minWidthPx, upper)
    }

    /**
     * Ширина панелі-пігулки над клавіатурою (Н1): не ширше за [maxPx] (ландшафт, планшет), у вузькому вікні
     * на всю доступну ширину з боковими полями. Рахується від ПОТОЧНОЇ ширини вікна, тож після повороту,
     * складання чи split-screen панель не лишається завширшки попередньої конфігурації.
     */
    fun pillWidthPx(availWidthPx: Int, maxPx: Int, sideMarginPx: Int): Int =
        minOf(maxPx, availWidthPx - 2 * sideMarginPx).coerceAtLeast(0)

    /**
     * Обчислює відступ знизу (`bottomMarginPx`) для компактної плаваючої картки кольорів/стилів (`placeTextColorPopup`),
     * щоб вона:
     * 1) за замовчуванням була піднята вище над панеллю інструментів (як у Samsung Notes — [defaultBottomMarginPx]);
     * 2) НІКОЛИ не перекривала живе поле вводу тексту (`[editorBoxTopPx, editorBoxBottomPx]`):
     *    - якщо поле тексту внизу біля панелі, картка кольорів піднімається строго НАД полем тексту;
     *    - якщо поле тексту вгорі й перетинається з карткою, картка опускається ПІД поле тексту (але вище панелі).
     */
    fun computePopupBottomMarginPx(
        availHeightPx: Int,
        minTopPx: Int,
        toolbarReservePx: Int,
        defaultBottomMarginPx: Int,
        popupHeightPx: Int,
        editorBoxTopPx: Int,
        editorBoxBottomPx: Int,
        gapPx: Int
    ): Int {
        val safePopupH = popupHeightPx.coerceAtLeast(48)
        val safeGap = gapPx.coerceAtLeast(4)
        val minMargin = toolbarReservePx.coerceAtLeast(48)
        val maxMargin = (availHeightPx - minTopPx - safePopupH).coerceAtLeast(minMargin)
        val baseMargin = defaultBottomMarginPx.coerceIn(minMargin, maxMargin)

        val basePopupBottom = availHeightPx - baseMargin
        val basePopupTop = basePopupBottom - safePopupH

        val overlaps = editorBoxBottomPx + safeGap > basePopupTop && editorBoxTopPx - safeGap < basePopupBottom
        if (!overlaps) {
            return baseMargin
        }

        // Варіант А (як у Samsung Notes): підняти картку кольорів НАД полем тексту
        val marginAboveBox = availHeightPx - (editorBoxTopPx - safeGap)
        if (marginAboveBox <= maxMargin) {
            return marginAboveBox.coerceIn(minMargin, maxMargin)
        }

        // Варіант Б (якщо поле тексту високо вгорі): розмістити картку кольорів ПІД полем тексту, але над панеллю
        val marginBelowBox = availHeightPx - (editorBoxBottomPx + safeGap + safePopupH)
        if (marginBelowBox >= minMargin) {
            return marginBelowBox.coerceIn(minMargin, maxMargin)
        }

        return maxMargin
    }
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

/**
 * Розташування компактної панелі дій біля виділеного об'єкта (чиста логіка, піксели в системі координат контейнера).
 * Пріоритет: над об'єктом; якщо зверху немає місця, під ним; якщо не вміщується ні там, ні там (високий об'єкт),
 * панель притискається до краю видимої зони. По горизонталі центрується на об'єкті й не виходить за краї.
 */
object SelectionPill {
    data class Frame(val left: Float, val top: Float, val right: Float, val bottom: Float)
    data class Placement(val x: Float, val y: Float, val above: Boolean, val visible: Boolean)

    /** Скільки відсотків висоти контейнера може займати панель. Гарантія для розмітки (≈10% екрана). */
    const val MAX_HEIGHT_FRACTION: Float = 0.10f

    fun place(
        frame: Frame, pillW: Float, pillH: Float, containerW: Float, containerH: Float, gap: Float, margin: Float
    ): Placement {
        val visible = frame.bottom > 0f && frame.top < containerH && frame.right > 0f && frame.left < containerW
        val maxX = (containerW - pillW - margin).coerceAtLeast(margin)
        val x = ((frame.left + frame.right) / 2f - pillW / 2f).coerceIn(margin, maxX)
        val maxY = (containerH - pillH - margin).coerceAtLeast(margin)
        val aboveTop = frame.top - gap - pillH
        val belowTop = frame.bottom + gap
        return when {
            aboveTop >= margin -> Placement(x, aboveTop.coerceAtMost(maxY), true, visible)
            belowTop <= maxY -> Placement(x, belowTop, false, visible)
            else -> Placement(x, aboveTop.coerceIn(margin, maxY), true, visible)
        }
    }

    /** Чи залишається панель у межах ліміту висоти. */
    fun fitsHeight(pillH: Float, containerH: Float): Boolean = containerH > 0f && pillH <= containerH * MAX_HEIGHT_FRACTION
}
