package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.MarkupLayout
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.MarkKind
import com.savoj.volumpdf.core.pdf.model.PageMarkup

/**
 * Інструмент режиму «Поставити». Об'єкти на сторінці бувають лише CHECK, CROSS, TEXT, INK (штрих) і SIGNATURE;
 * HAND — «рука»: не створює об'єктів, лише вмикає прокрутку й зум (оверлей не перехоплює дотик повз об'єкти).
 */
enum class PlaceTool { CHECK, CROSS, TEXT, INK, SIGNATURE, HAND, IMAGE }

/** Точки штриха: плоский масив x0,y0,x1,y1,… у pt (PageViewPoint сторінки штриха). Незмінний за домовленістю. */
class InkPoints(val xy: FloatArray) {
    val count: Int get() = xy.size / 2
    override fun equals(other: Any?): Boolean = other is InkPoints && xy.contentEquals(other.xy)
    override fun hashCode(): Int = xy.contentHashCode()
}

/** Пресети пензля: товщина в pt і альфа (хайлайтер напівпрозорий, 0x66). */
enum class InkPreset(val widthPt: Float, val alpha: Int) {
    PEN(2f, 0xFF), MARKER(5f, 0xFF), HIGHLIGHTER(12f, 0x66)
}

/**
 * Незбережений об'єкт розмітки у простих полях. Координати — PageViewPoint, pt: для ✓/✗ (x, y) — центр,
 * для тексту й підпису — лівий верхній кут, для штриха — перша точка. [sizePt]: розмір ✓/✗, кегль тексту,
 * товщина штриха або ширина підпису. [color] — ARGB (у штриха альфа несе прозорість маркера).
 * [ink] — точки штриха (INK); [aspect] — висота/ширина підпису (SIGNATURE); сама картинка підпису
 * лежить окремо (за [id]), щоб редактор лишався чистим і тестувався на JVM.
 */
data class PendingMarkup(
    val id: String,
    val tool: PlaceTool,
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val sizePt: Float,
    val color: Int,
    val ink: InkPoints? = null,
    val aspect: Float = 0f,
    val rawText: String? = null,
    val rotationDeg: Int = 0
)

/** Підсумок перевірки введеного тексту (без створення об'єкта). */
sealed interface TextVerdict {
    object Ok : TextVerdict
    object Empty : TextVerdict
    data class Unsupported(val chars: String) : TextVerdict
}

/** Шматочок тексту для розміщення на окремій сторінці при пагінації довгих текстів. */
data class PageTextChunk(
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val rawText: String
)

/** Результат спроби створити текстовий об'єкт. */
sealed interface TextCheck {
    data class Ok(val markup: PendingMarkup) : TextCheck
    object Empty : TextCheck
    data class Unsupported(val chars: String) : TextCheck
}

/** Чиста логіка режиму «Поставити» (без Android): створення об'єктів, клампи, серіалізація. */
object PlaceMarkupLogic {
    const val DEFAULT_MARK_SIZE_PT = 16f
    const val DEFAULT_TEXT_SIZE_PT = 14f
    const val MIN_SIZE_PT = 4f
    const val MAX_MARK_SIZE_PT = 144f
    const val MAX_TEXT_SIZE_PT = 144f

    const val DEFAULT_INK_WIDTH_PT = 2f
    const val MIN_INK_WIDTH_PT = 0.5f
    const val MAX_INK_WIDTH_PT = 40f

    const val DEFAULT_SIGNATURE_WIDTH_PT = 120f
    const val MIN_SIGNATURE_WIDTH_PT = 30f
    const val MAX_SIGNATURE_WIDTH_PT = 400f

    const val DEFAULT_IMAGE_WIDTH_PT = 240f
    const val MIN_IMAGE_WIDTH_PT = 30f
    const val MAX_IMAGE_WIDTH_PT = 800f

    /** Точки штриха, ближчі за цю відстань (pt) до попередньої залишеної, відкидаються. */
    const val INK_MIN_POINT_DISTANCE_PT = 0.5f

    /** Штрих (його охоплювальний прямокутник) має лишатися на видимій сторінці щонайменше на стільки pt. */
    const val INK_MIN_VISIBLE_PT = 2f

    /** Опорна точка об'єкта не ближче цього до правого/нижнього краю видимої сторінки (двигун відхиляє об'єкт, що цілком поза сторінкою). */
    const val PAGE_EDGE_MARGIN_PT = 2f

    /** Мінімальна допустима ширина рядка тексту при перенесенні або зміні розміру вікна (pt). */
    const val MIN_TEXT_LINE_WIDTH_PT = 40f

    fun defaultSize(tool: PlaceTool): Float = when (tool) {
        PlaceTool.TEXT -> DEFAULT_TEXT_SIZE_PT
        PlaceTool.INK -> DEFAULT_INK_WIDTH_PT
        PlaceTool.SIGNATURE -> DEFAULT_SIGNATURE_WIDTH_PT
        PlaceTool.IMAGE -> DEFAULT_IMAGE_WIDTH_PT
        else -> DEFAULT_MARK_SIZE_PT
    }

    fun sizeMin(tool: PlaceTool): Float = when (tool) {
        PlaceTool.INK -> MIN_INK_WIDTH_PT
        PlaceTool.SIGNATURE, PlaceTool.IMAGE -> MIN_IMAGE_WIDTH_PT
        else -> MIN_SIZE_PT
    }

    fun sizeMax(tool: PlaceTool): Float = when (tool) {
        PlaceTool.TEXT -> MAX_TEXT_SIZE_PT
        PlaceTool.INK -> MAX_INK_WIDTH_PT
        PlaceTool.SIGNATURE -> MAX_SIGNATURE_WIDTH_PT
        PlaceTool.IMAGE -> MAX_IMAGE_WIDTH_PT
        else -> MAX_MARK_SIZE_PT
    }

    /** Розмір у межах діапазону інструмента; за наявності page обмежується межами сторінки. */
    fun clampSize(
        tool: PlaceTool,
        value: Float,
        x: Float = 0f,
        y: Float = 0f,
        aspect: Float = 0f,
        page: PageSizePt? = null
    ): Float {
        if (value.isNaN() || value.isInfinite()) return defaultSize(tool)
        var max = sizeMax(tool)
        if (tool == PlaceTool.IMAGE && page != null) {
            val maxWFromX = (page.widthPt - x).coerceAtLeast(sizeMin(tool))
            val maxWFromY = if (aspect > 0f) {
                ((page.heightPt - y) / aspect).coerceAtLeast(sizeMin(tool))
            } else page.heightPt
            max = minOf(max, maxWFromX, maxWFromY)
        }
        return value.coerceIn(sizeMin(tool), max)
    }

    /** Розмір для підпису в панелі: ціле без дробу, інакше один знак. */
    fun formatSize(value: Float): String =
        if (value == Math.floor(value.toDouble()).toFloat()) value.toInt().toString()
        else String.format(java.util.Locale.getDefault(), "%.1f", value)

    /**
     * Крок розміру росте з розміром: 1 pt до 20, 2 pt до 48, 4 pt до 96, далі 8 pt (від 4 до 144 pt — 48 натискань).
     * Крок «−» — обернений до «+»: від значення на межі діапазону береться крок нижчого діапазону.
     */
    private fun stepAt(size: Float): Float = when {
        size < 20f -> 1f
        size < 48f -> 2f
        size < 96f -> 4f
        else -> 8f
    }

    /** Товщина штриха: 0,5 pt до 4, 1 pt до 12, далі 2 pt. */
    private fun inkStepAt(width: Float): Float = when {
        width < 4f -> 0.5f
        width < 12f -> 1f
        else -> 2f
    }

    /** Підпис: крок 10 pt до 100, далі 20 (слайдер дає довільне значення; кроки лише для запасних кнопок). */
    private fun signatureStepAt(width: Float): Float = if (width < 100f) 10f else 20f

    fun stepSize(tool: PlaceTool, current: Float, direction: Int): Float {
        if (current.isNaN() || current.isInfinite()) return defaultSize(tool)
        val delta = when (tool) {
            PlaceTool.INK -> if (direction >= 0) inkStepAt(current) else -inkStepAt(current - 0.01f)
            PlaceTool.SIGNATURE -> if (direction >= 0) signatureStepAt(current) else -signatureStepAt(current - 0.01f)
            else -> if (direction >= 0) stepAt(current) else -stepAt(current - 0.01f)
        }
        return clampSize(tool, current + delta)
    }

    /** Опорна точка в межах видимої сторінки; [page] == null (розмір невідомий) — лише не менше 0. */
    fun clampToPage(x: Float, y: Float, page: PageSizePt?): Pair<Float, Float> {
        val sx = if (x.isNaN()) 0f else x
        val sy = if (y.isNaN()) 0f else y
        if (page == null) return Pair(sx.coerceAtLeast(0f), sy.coerceAtLeast(0f))
        val maxX = (page.widthPt - PAGE_EDGE_MARGIN_PT).coerceAtLeast(0f)
        val maxY = (page.heightPt - PAGE_EDGE_MARGIN_PT).coerceAtLeast(0f)
        return Pair(sx.coerceIn(0f, maxX), sy.coerceIn(0f, maxY))
    }

    /**
     * Максимальна ширина рядка тексту (pt), щоб не виходити за поля сторінки.
     * Якщо xPt лежить у лівій половині сторінки — зберігаються симетричні поля: праве поле дорівнює лівому (щонайменше 36 pt).
     * Якщо xPt у правій половині сторінки — праве поле береться стандартним (36 pt).
     * Якщо розмір сторінки невідомий — береться стандартна ширина сторінки A4 (595.28 pt).
     */
    fun maxTextLineWidthPt(xPt: Float, page: PageSizePt?): Float {
        val pageWidth = page?.widthPt ?: 595.28f
        val rightMargin = if (xPt <= pageWidth / 2f) maxOf(xPt, 36f) else 36f
        val rawWidth = pageWidth - xPt - rightMargin
        return rawWidth.coerceAtLeast(60f)
    }

    /**
     * Оцінка ширини рядка для Roboto-Regular (pt) без Android-класів (для JVM-тестів).
     */
    fun estimateTextWidthPt(text: String, fontSizePt: Float): Float {
        var w = 0f
        for (ch in text) {
            val factor = when (ch) {
                ' ', '.', ',', ':', ';', '!', '\'', '|', 'i', 'l', 'j' -> 0.28f
                'f', 'r', 't', '(', ')', '[', ']', 'I' -> 0.35f
                'm', 'w', 'M', 'W', 'Ж', 'Ш', 'Щ', 'Ю', 'ж', 'ш', 'щ', 'ю', '@', '%' -> 0.85f
                in 'A'..'Z', in 'А'..'Я' -> 0.65f
                else -> 0.53f
            }
            w += factor * fontSizePt
        }
        return w
    }

    /**
     * Відновлює початковий безперервний текст із раніше перенесеного тексту:
     * - Окремі переноси рядків ('\n') у межах одного абзацу об'єднуються через пробіл.
     * - Подвійні переноси ('\n\n' або більше) зберігаються як межі абзаців.
     */
    fun unwrapText(text: String): String {
        val normalized = text.replace("\r", "")
        if (!normalized.contains('\n')) return normalized
        val paragraphs = normalized.split(Regex("\n{2,}"))
        return paragraphs.joinToString("\n\n") { p ->
            p.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }
    }

    /**
     * Переносить текст на рядки під задану максимальну ширину [maxWidthPt] (pt).
     * - [rawText] — початковий або раніше перенесений текст (абзаци '\n\n' зберігаються).
     * - Розриває слова за пробілами, коли рядок перевищує [maxWidthPt].
     * - Якщо окреме слово ширше за [maxWidthPt], розбиває його по символах.
     * - [measureWidthPt] — функція вимірювання (Paint на Android); якщо null — [estimateTextWidthPt].
     */
    fun wrapTextToWidth(
        rawText: String,
        maxWidthPt: Float,
        fontSizePt: Float,
        measureWidthPt: ((String) -> Float)? = null
    ): String {
        if (rawText.isEmpty()) return rawText
        val width = maxWidthPt.coerceAtLeast(MIN_TEXT_LINE_WIDTH_PT)
        val measure: (String) -> Float = measureWidthPt ?: { str -> estimateTextWidthPt(str, fontSizePt) }

        val unwrapped = unwrapText(rawText)
        val paragraphs = unwrapped.split(Regex("\n{2,}"))
        val wrappedParagraphs = paragraphs.map { paragraph ->
            wrapParagraph(paragraph, width, measure)
        }
        return wrappedParagraphs.joinToString("\n\n")
    }

    /**
     * Переносить довгий текст на наступний рядок ('\n'), зберігаючи поля сторінки (не виходить за межі).
     * Обчислює [maxWidthPt] через [maxTextLineWidthPt] і викликає [wrapTextToWidth].
     */
    fun wrapText(
        rawText: String,
        xPt: Float,
        fontSizePt: Float,
        page: PageSizePt?,
        measureWidthPt: ((String) -> Float)? = null
    ): String {
        if (rawText.isEmpty()) return rawText
        val maxWidth = maxTextLineWidthPt(xPt, page)
        return wrapTextToWidth(rawText, maxWidth, fontSizePt, measureWidthPt)
    }

    private fun wrapParagraph(paragraph: String, maxWidthPt: Float, measure: (String) -> Float): String {
        if (paragraph.isBlank()) return paragraph
        if (measure(paragraph) <= maxWidthPt) return paragraph

        val words = paragraph.split(' ')
        val lines = ArrayList<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.isEmpty()) {
                if (measure(word) <= maxWidthPt) {
                    currentLine.append(word)
                } else {
                    var chunk = StringBuilder()
                    for (ch in word) {
                        if (measure(chunk.toString() + ch) <= maxWidthPt) {
                            chunk.append(ch)
                        } else {
                            if (chunk.isNotEmpty()) {
                                lines.add(chunk.toString())
                                chunk = StringBuilder()
                            }
                            chunk.append(ch)
                        }
                    }
                    currentLine = chunk
                }
            } else {
                val candidate = currentLine.toString() + " " + word
                if (measure(candidate) <= maxWidthPt) {
                    currentLine.append(" ").append(word)
                } else {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                    if (measure(word) <= maxWidthPt) {
                        currentLine.append(word)
                    } else {
                        var chunk = StringBuilder()
                        for (ch in word) {
                            if (measure(chunk.toString() + ch) <= maxWidthPt) {
                                chunk.append(ch)
                            } else {
                                if (chunk.isNotEmpty()) {
                                    lines.add(chunk.toString())
                                    chunk = StringBuilder()
                                }
                                chunk.append(ch)
                            }
                        }
                        currentLine = chunk
                    }
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.joinToString("\n")
    }

    /** Порожній (лише пробіли й розриви) — Empty; символи, яких немає у шрифті, — Unsupported. */
    fun checkText(rawText: String, unsupported: String): TextVerdict = when {
        rawText.isBlank() -> TextVerdict.Empty
        unsupported.isNotEmpty() -> TextVerdict.Unsupported(unsupported)
        else -> TextVerdict.Ok
    }

    /** ✓/✗: центр позначки — у точці тапу. */
    fun markFromTap(id: String, tool: PlaceTool, pageIndex: Int, x: Float, y: Float, sizePt: Float, color: Int): PendingMarkup {
        require(tool != PlaceTool.TEXT) { "Текст створюється через textFromTap" }
        return PendingMarkup(id, tool, pageIndex, x, y, "", clampSize(tool, sizePt), color)
    }

    /**
     * Текст: лівий верхній кут — у точці тапу. Порожній (лише пробіли й розриви) — не додається;
     * символи, яких немає у шрифті ([unsupported] від MarkupEngine.findUnsupportedChars), — не додається.
     * За наявності [page] довгий текст автоматично переноситься на наступні рядки зі збереженням полів сторінки.
     */
    fun textFromTap(
        id: String, pageIndex: Int, x: Float, y: Float,
        rawText: String, sizePt: Float, color: Int, unsupported: String,
        page: PageSizePt? = null,
        measureWidthPt: ((String) -> Float)? = null
    ): TextCheck {
        when (val v = checkText(rawText, unsupported)) {
            TextVerdict.Empty -> return TextCheck.Empty
            is TextVerdict.Unsupported -> return TextCheck.Unsupported(v.chars)
            TextVerdict.Ok -> Unit
        }
        val clampedSize = clampSize(PlaceTool.TEXT, sizePt)
        val finalText = if (page != null) wrapText(rawText, x, clampedSize, page, measureWidthPt) else rawText
        return TextCheck.Ok(
            PendingMarkup(
                id = id,
                tool = PlaceTool.TEXT,
                pageIndex = pageIndex,
                x = x,
                y = y,
                text = finalText,
                sizePt = clampedSize,
                color = color,
                rawText = rawText
            )
        )
    }

    /**
     * Розбиває довгий текст на шматочки для сторінок (пагінація), якщо текст не вміщується
     * по висоті на початковій сторінці.
     * Повертає список шматочків із відповідними pageIndex, x, y та перенесеним текстом.
     */
    fun paginateText(
        initialPageIndex: Int,
        startX: Float,
        startY: Float,
        rawText: String,
        fontSizePt: Float,
        page: PageSizePt?,
        measureWidthPt: ((String) -> Float)? = null
    ): List<PageTextChunk> {
        val clampedSize = clampSize(PlaceTool.TEXT, fontSizePt)
        val pHeight = page?.heightPt ?: 842f
        val pWidth = page?.widthPt ?: 595f
        val bottomMargin = 40f
        val topMargin = 40f
        val leftMargin = 36f
        val rightMargin = 36f

        val lineHeight = clampedSize * MarkupLayout.TEXT_LINE_HEIGHT_RATIO

        // Переносимо текст по ширині
        val firstPageMaxWidth = (pWidth - startX - rightMargin).coerceAtLeast(MIN_TEXT_LINE_WIDTH_PT)
        val unwrapped = unwrapText(rawText)
        val paragraphs = unwrapped.split(Regex("\n{2,}"))

        val allLines = mutableListOf<String>()
        val measure: (String) -> Float = measureWidthPt ?: { str -> estimateTextWidthPt(str, clampedSize) }

        for (p in paragraphs) {
            val wrappedP = wrapParagraph(p, firstPageMaxWidth, measure)
            val linesInP = wrappedP.split('\n')
            allLines.addAll(linesInP)
            allLines.add("")
        }
        if (allLines.isNotEmpty() && allLines.last().isEmpty()) {
            allLines.removeAt(allLines.lastIndex)
        }

        // Обчислюємо максимальну кількість рядків на першій сторінці
        val availableHeightFirst = (pHeight - startY - bottomMargin).coerceAtLeast(lineHeight)
        val maxLinesFirst = (availableHeightFirst / lineHeight).toInt().coerceAtLeast(1)

        if (allLines.size <= maxLinesFirst) {
            val text = allLines.joinToString("\n")
            return listOf(
                PageTextChunk(
                    pageIndex = initialPageIndex,
                    x = startX,
                    y = startY,
                    text = text,
                    rawText = rawText
                )
            )
        }

        // Текст виходить за межі першої сторінки -> розбиваємо на кілька сторінок
        val chunks = mutableListOf<PageTextChunk>()

        // Перша сторінка
        val firstLines = allLines.subList(0, maxLinesFirst)
        chunks.add(
            PageTextChunk(
                pageIndex = initialPageIndex,
                x = startX,
                y = startY,
                text = firstLines.joinToString("\n"),
                rawText = firstLines.joinToString("\n")
            )
        )

        // Доступна висота на повних сторінках
        val availableHeightFull = (pHeight - topMargin - bottomMargin).coerceAtLeast(lineHeight)
        val maxLinesFull = (availableHeightFull / lineHeight).toInt().coerceAtLeast(1)

        var currentLineIdx = maxLinesFirst
        var currentPageIdx = initialPageIndex + 1

        while (currentLineIdx < allLines.size) {
            val endIdx = minOf(currentLineIdx + maxLinesFull, allLines.size)
            val pageLines = allLines.subList(currentLineIdx, endIdx)
            chunks.add(
                PageTextChunk(
                    pageIndex = currentPageIdx,
                    x = leftMargin,
                    y = topMargin,
                    text = pageLines.joinToString("\n"),
                    rawText = pageLines.joinToString("\n")
                )
            )
            currentLineIdx = endIdx
            currentPageIdx++
        }

        return chunks
    }

    /** [signatureBitmap] — картинка підпису за id об'єкта (лише для SIGNATURE); без неї підпис не перетворити. */
    fun toPageMarkup(p: PendingMarkup, signatureBitmap: ((String) -> android.graphics.Bitmap?)? = null): PageMarkup = when (p.tool) {
        PlaceTool.CHECK -> PageMarkup.Mark(p.id, p.pageIndex, p.x, p.y, MarkKind.CHECK, p.sizePt, p.color)
        PlaceTool.CROSS -> PageMarkup.Mark(p.id, p.pageIndex, p.x, p.y, MarkKind.CROSS, p.sizePt, p.color)
        PlaceTool.TEXT -> PageMarkup.Text(p.id, p.pageIndex, p.x, p.y, p.text, p.sizePt, p.color)
        PlaceTool.INK -> {
            val xy = p.ink?.xy ?: FloatArray(0)
            val points = ArrayList<android.graphics.PointF>(xy.size / 2)
            for (i in 0 until xy.size / 2) points += android.graphics.PointF().also { it.x = xy[i * 2]; it.y = xy[i * 2 + 1] }
            PageMarkup.Ink(p.id, p.pageIndex, listOf(InkStroke(points, p.color, p.sizePt)))
        }
        PlaceTool.SIGNATURE, PlaceTool.IMAGE -> {
            val bitmap = signatureBitmap?.invoke(p.id)
                ?: throw IllegalStateException("Немає картинки підпису або зображення для ${p.id}")
            PageMarkup.Signature(p.id, p.pageIndex, bitmap, p.x, p.y, p.sizePt, p.sizePt * p.aspect)
        }
        PlaceTool.HAND -> throw IllegalArgumentException("«Рука» не створює об'єктів")
    }

    // --- штрих, підпис: створення, межі, рух ----------------------------------------------------

    /**
     * Новий штрих зі зібраних точок ([xy] — у просторі сторінки [pageIndex], pt). Надлишкові точки (ближчі за
     * [INK_MIN_POINT_DISTANCE_PT]) відкидаються. null — точок немає або всі за межами сторінки [page]
     * (розмір невідомий — штрих приймається).
     */
    fun inkFromStroke(
        id: String, pageIndex: Int, xy: FloatArray, widthPt: Float, color: Int, page: PageSizePt?
    ): PendingMarkup? {
        val kept = InkMath.decimate(xy, INK_MIN_POINT_DISTANCE_PT)
        if (kept.isEmpty()) return null
        if (!InkMath.anyInside(kept, page)) return null
        return PendingMarkup(
            id, PlaceTool.INK, pageIndex, kept[0], kept[1], "", clampSize(PlaceTool.INK, widthPt), color,
            ink = InkPoints(kept)
        )
    }

    /** Підпис: лівий верхній кут у точці тапу, ширина [widthPt], висота зберігає пропорції ([aspect] = висота/ширина). */
    fun signatureFromTap(
        id: String, pageIndex: Int, x: Float, y: Float, bitmapWidth: Int, bitmapHeight: Int,
        widthPt: Float, page: PageSizePt?
    ): PendingMarkup? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        val (cx, cy) = clampToPage(x, y, page)
        return PendingMarkup(
            id, PlaceTool.SIGNATURE, pageIndex, cx, cy, "", clampSize(PlaceTool.SIGNATURE, widthPt), 0,
            aspect = bitmapHeight.toFloat() / bitmapWidth.toFloat()
        )
    }

    /** Зображення: лівий верхній кут у точці тапу, ширина [widthPt], висота зберігає пропорції, обмежені сторінкою. */
    fun imageFromTap(
        id: String, pageIndex: Int, x: Float, y: Float, bitmapWidth: Int, bitmapHeight: Int,
        widthPt: Float = DEFAULT_IMAGE_WIDTH_PT, page: PageSizePt? = null
    ): PendingMarkup? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        val aspect = bitmapHeight.toFloat() / bitmapWidth.toFloat()
        val clampedWidth = clampSize(PlaceTool.IMAGE, widthPt, x, y, aspect, page)
        val (cx, cy) = if (page != null) {
            val w = clampedWidth
            val h = clampedWidth * aspect
            val maxX = (page.widthPt - w).coerceAtLeast(0f)
            val maxY = (page.heightPt - h).coerceAtLeast(0f)
            Pair(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
        } else {
            clampToPage(x, y, null)
        }
        return PendingMarkup(
            id, PlaceTool.IMAGE, pageIndex, cx, cy, "", clampedWidth, 0,
            aspect = aspect
        )
    }

    /** Охоплювальний прямокутник точок штриха, pt (без товщини). */
    fun inkBoundsPt(item: PendingMarkup): RectPx {
        val xy = item.ink?.xy
        if (xy == null || xy.size < 2) return RectPx(item.x, item.y, item.x, item.y)
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < xy.size) {
            val px = xy[i]
            val py = xy[i + 1]
            if (px < l) l = px
            if (px > r) r = px
            if (py < t) t = py
            if (py > b) b = py
            i += 2
        }
        return RectPx(l, t, r, b)
    }

    /**
     * Нова опорна точка після перетягування з урахуванням меж. Для ✓/✗, тексту й підпису — опорна точка в межах
     * сторінки ([clampToPage]); для штриха — зсув такий, щоб охоплювальний прямокутник точок лишився на сторінці
     * щонайменше на [INK_MIN_VISIBLE_PT] (двигун відхиляє об'єкт, що цілком поза сторінкою).
     * Для тексту права межа не дозволяє витягнути блок тексту за межі сторінки.
     */
    fun clampedAnchor(
        item: PendingMarkup, x: Float, y: Float, page: PageSizePt?,
        measureWidthPt: ((String) -> Float)? = null
    ): Pair<Float, Float> {
        if (item.tool == PlaceTool.TEXT && page != null) {
            val (w, h) = textBlockSizePt(item, measureWidthPt)
            val maxX = (page.widthPt - PAGE_EDGE_MARGIN_PT - w).coerceAtLeast(0f)
            val maxY = (page.heightPt - h).coerceAtLeast(0f)
            val sx = if (x.isNaN()) item.x else x
            val sy = if (y.isNaN()) item.y else y
            return Pair(sx.coerceIn(0f, maxX), sy.coerceIn(0f, maxY))
        }
        if (item.tool == PlaceTool.IMAGE && page != null) {
            val w = item.sizePt
            val h = item.sizePt * item.aspect
            val maxX = (page.widthPt - w).coerceAtLeast(0f)
            val maxY = (page.heightPt - h).coerceAtLeast(0f)
            val sx = if (x.isNaN()) item.x else x
            val sy = if (y.isNaN()) item.y else y
            return Pair(sx.coerceIn(0f, maxX), sy.coerceIn(0f, maxY))
        }
        if (item.tool != PlaceTool.INK || item.ink == null) return clampToPage(x, y, page)
        val sx = if (x.isNaN()) item.x else x
        val sy = if (y.isNaN()) item.y else y
        val b = inkBoundsPt(item)
        var dx = sx - item.x
        var dy = sy - item.y
        val m = INK_MIN_VISIBLE_PT
        var minDx = m - b.right
        var minDy = m - b.bottom
        var maxDx = if (page == null) Float.MAX_VALUE else page.widthPt - m - b.left
        var maxDy = if (page == null) Float.MAX_VALUE else page.heightPt - m - b.top
        if (minDx > maxDx) { val mid = (minDx + maxDx) / 2f; minDx = mid; maxDx = mid }
        if (minDy > maxDy) { val mid = (minDy + maxDy) / 2f; minDy = mid; maxDy = mid }
        dx = dx.coerceIn(minDx, maxDx)
        dy = dy.coerceIn(minDy, maxDy)
        return Pair(item.x + dx, item.y + dy)
    }

    data class DragPlacementResult(
        val x: Float,
        val y: Float,
        val isOutsideBounds: Boolean,
        val canFitOnPage: Boolean
    )

    /** Повертає актуальні ширину й висоту текстового блоку в pt (з урахуванням переносів і розміру шрифту). */
    fun textBlockSizePt(
        item: PendingMarkup,
        measureWidthPt: ((String) -> Float)? = null
    ): Pair<Float, Float> {
        val measure = measureWidthPt ?: { str -> estimateTextWidthPt(str, item.sizePt) }
        val lines = item.text.replace("\r", "").split('\n')
        val width = lines.maxOfOrNull { if (it.isEmpty()) 0f else measure(it) } ?: 0f
        val height = (MarkupLayout.TEXT_ASCENT_RATIO + PlaceBounds.TEXT_DESCENT_RATIO +
            (lines.size - 1) * MarkupLayout.TEXT_LINE_HEIGHT_RATIO) * item.sizePt
        return Pair(width, height)
    }

    /** Перевіряє, чи вміщується об'єкт [item] з актуальними розмірами в межах сторінки [page]. */
    fun fitsOnPage(
        item: PendingMarkup,
        page: PageSizePt,
        measureWidthPt: ((String) -> Float)? = null
    ): Boolean {
        if (item.tool == PlaceTool.TEXT) {
            val (w, h) = textBlockSizePt(item, measureWidthPt)
            return w <= page.widthPt - PAGE_EDGE_MARGIN_PT + 0.5f && h <= page.heightPt + 0.5f
        }
        if (item.tool != PlaceTool.IMAGE && item.tool != PlaceTool.SIGNATURE) return true
        val w = item.sizePt
        val h = item.sizePt * item.aspect
        return w <= page.widthPt + 0.5f && h <= page.heightPt + 0.5f
    }

    /**
     * Обчислює безпечну позицію [item] при перетягуванні на сторінку [page].
     * Якщо кандидатна позиція виходить за межі допустимої області — обмежує найближчою допустимою точкою;
     * [canFitOnPage] показує, чи об'єкт у принципі може бути розміщений на цій сторінці.
     */
    fun resolveDragPlacement(
        item: PendingMarkup,
        candidateX: Float,
        candidateY: Float,
        page: PageSizePt?,
        measureWidthPt: ((String) -> Float)? = null
    ): DragPlacementResult {
        if (page == null) {
            return DragPlacementResult(
                x = if (candidateX.isNaN()) item.x else candidateX,
                y = if (candidateY.isNaN()) item.y else candidateY,
                isOutsideBounds = true,
                canFitOnPage = false
            )
        }
        val canFit = fitsOnPage(item, page, measureWidthPt)
        val sx = if (candidateX.isNaN()) item.x else candidateX
        val sy = if (candidateY.isNaN()) item.y else candidateY
        val (cx, cy) = clampedAnchor(item, sx, sy, page, measureWidthPt)
        val outside = Math.abs(cx - sx) > 0.01f || Math.abs(cy - sy) > 0.01f
        return DragPlacementResult(
            x = cx,
            y = cy,
            isOutsideBounds = outside || !canFit,
            canFitOnPage = canFit
        )
    }

    /** Переносить опорну точку в [x], [y] (вже обмежену [clampedAnchor]); штрих зсувається цілком. */
    fun withAnchor(item: PendingMarkup, x: Float, y: Float): PendingMarkup {
        if (item.x == x && item.y == y) return item
        val ink = item.ink
        if (item.tool != PlaceTool.INK || ink == null) return item.copy(x = x, y = y)
        val dx = x - item.x
        val dy = y - item.y
        val moved = FloatArray(ink.xy.size) { i -> ink.xy[i] + if (i % 2 == 0) dx else dy }
        return item.copy(x = x, y = y, ink = InkPoints(moved))
    }

    /** Колір з палітри для об'єкта: у штриха альфа (маркер) зберігається, решта — як є. */
    fun recolor(item: PendingMarkup, rgb: Int): Int =
        if (item.tool == PlaceTool.INK) (item.color and ALPHA_MASK) or (rgb and 0x00FFFFFF) else rgb

    const val ALPHA_MASK = 0xFF000000.toInt()

    // --- рішення про доступність редагування ---------------------------------------------------

    enum class Gate { ALLOWED, ENCRYPTED, NO_MARKUP_RIGHTS, UNREADABLE }

    fun gate(isEncrypted: Boolean?, canMarkup: Boolean?): Gate = when {
        isEncrypted == null || canMarkup == null -> Gate.UNREADABLE
        isEncrypted -> Gate.ENCRYPTED
        !canMarkup -> Gate.NO_MARKUP_RIGHTS
        else -> Gate.ALLOWED
    }
}

/** Геометрія оверлея: pt → px за MarkupLayout. Чиста математика, без Android-класів. */
object MarkupOverlayMath {
    /** 1 px = 1 pt при zoom = 1. */
    fun ptToPx(pt: Float, zoom: Float): Float = pt * zoom

    fun strokePx(sizePt: Float, zoom: Float): Float = MarkupLayout.MARK_STROKE_RATIO * sizePt * zoom

    /**
     * Ламані позначки в px; [cx], [cy] — центр у px. Кожна ламана — плоский масив x0,y0,x1,y1,…
     * Квадрат 0..1 з початком зверху-ліворуч, центрований у (cx, cy), сторона = sizePt × zoom.
     */
    fun markPolylinesPx(isCheck: Boolean, cx: Float, cy: Float, sizePt: Float, zoom: Float): List<FloatArray> {
        val kind = if (isCheck) MarkKind.CHECK else MarkKind.CROSS
        val side = sizePt * zoom
        val left = cx - side / 2f
        val top = cy - side / 2f
        return MarkupLayout.markPolylines(kind).map { line ->
            val out = FloatArray(line.size * 2)
            line.forEachIndexed { i, p ->
                out[i * 2] = left + p.x * side
                out[i * 2 + 1] = top + p.y * side
            }
            out
        }
    }

    data class TextLinePx(val text: String, val x: Float, val baselineY: Float)

    /** Розрив рядків лише за '\n' (як у двигуні); порожні рядки лишають крок. [x0], [y0] — лівий верхній кут у px. */
    fun textLinesPx(text: String, x0: Float, y0: Float, fontSizePt: Float, zoom: Float): List<TextLinePx> {
        val fs = fontSizePt * zoom
        return text.replace("\r", "").split('\n').mapIndexed { i, line ->
            TextLinePx(line, x0, y0 + MarkupLayout.TEXT_ASCENT_RATIO * fs + i * MarkupLayout.TEXT_LINE_HEIGHT_RATIO * fs)
        }
    }
}

/** Перетворення пікселя PdfView у простір довільної сторінки (без Android-класів, щоб тестувати на JVM). */
object PageSpaceMath {
    /**
     * [originX], [originY] — піксель PdfView, у якому лежить точка (0, 0) сторінки (лівий верхній кут видимої області);
     * [zoom] — пікселів на pt. Повертає (x, y) у pt; працює й для точок за межами сторінки (від'ємні чи більші за розмір).
     * null — масштаб не додатний чи вхідні числа не скінченні.
     */
    fun toPageSpace(originX: Float, originY: Float, zoom: Float, viewX: Float, viewY: Float): Pair<Float, Float>? {
        if (!(zoom > 0f) || zoom.isInfinite()) return null
        val values = floatArrayOf(originX, originY, viewX, viewY)
        if (values.any { it.isNaN() || it.isInfinite() }) return null
        return Pair((viewX - originX) / zoom, (viewY - originY) / zoom)
    }
}

/** Математика штрихів: відсікання надлишкових точок, перевірка «на сторінці». Без Android-класів. */
object InkMath {
    /**
     * Залишає першу точку, далі лише ті, що не ближчі за [minDistance] до останньої залишеної; остання вхідна
     * точка завжди залишається (кінець штриха не губиться). [xy] — плоский масив пар; непарний хвіст і
     * не-скінченні точки ігноруються.
     */
    fun decimate(xy: FloatArray, minDistance: Float): FloatArray {
        val out = ArrayList<Float>()
        var lastX = 0f
        var lastY = 0f
        var lastInX = 0f
        var lastInY = 0f
        var have = false
        var pendingTail = false
        var i = 0
        while (i + 1 < xy.size) {
            val x = xy[i]
            val y = xy[i + 1]
            i += 2
            if (x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()) continue
            lastInX = x
            lastInY = y
            if (!have) {
                out += x; out += y
                lastX = x; lastY = y
                have = true
                pendingTail = false
                continue
            }
            if (Math.hypot((x - lastX).toDouble(), (y - lastY).toDouble()) >= minDistance) {
                out += x; out += y
                lastX = x; lastY = y
                pendingTail = false
            } else {
                pendingTail = true
            }
        }
        // Кінець штриха: якщо останню вхідну точку відкинуто й вона не збігається з останньою залишеною — дописуємо.
        if (have && pendingTail && (lastInX != lastX || lastInY != lastY)) {
            out += lastInX; out += lastInY
        }
        return out.toFloatArray()
    }

    /** true, якщо принаймні одна точка лежить на видимій сторінці [0, W] × [0, H]; [page] == null — так. */
    fun anyInside(xy: FloatArray, page: PageSizePt?): Boolean {
        if (page == null) return xy.size >= 2
        var i = 0
        while (i + 1 < xy.size) {
            if (xy[i] in 0f..page.widthPt && xy[i + 1] in 0f..page.heightPt) return true
            i += 2
        }
        return false
    }
}

/** Відстань від точки до ламаної (плоский масив x0,y0,x1,y1,…); для однієї точки — відстань до неї. */
object PolylineMath {
    fun distance(xy: FloatArray, px: Float, py: Float): Float {
        if (xy.size < 2) return Float.MAX_VALUE
        if (xy.size < 4) return Math.hypot((px - xy[0]).toDouble(), (py - xy[1]).toDouble()).toFloat()
        var best = Double.MAX_VALUE
        var i = 0
        while (i + 3 < xy.size) {
            val d = segmentDistance(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], px, py)
            if (d < best) best = d
            i += 2
        }
        return best.toFloat()
    }

    private fun segmentDistance(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Double {
        val abx = (bx - ax).toDouble()
        val aby = (by - ay).toDouble()
        val len2 = abx * abx + aby * aby
        if (len2 == 0.0) return Math.hypot((px - ax).toDouble(), (py - ay).toDouble())
        val t = (((px - ax) * abx + (py - ay) * aby) / len2).coerceIn(0.0, 1.0)
        return Math.hypot(px - (ax + t * abx), py - (ay + t * aby))
    }
}
