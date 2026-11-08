package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.MarkupLayout
import com.savoj.volumpdf.core.pdf.model.MarkKind
import com.savoj.volumpdf.core.pdf.model.PageMarkup

/** Інструмент режиму «Поставити». */
enum class PlaceTool { CHECK, CROSS, TEXT }

/**
 * Незбережений об'єкт розмітки у простих полях (для SavedStateHandle і оверлея).
 * Координати — PageViewPoint, pt: для ✓/✗ (x, y) — центр, для тексту — лівий верхній кут.
 */
data class PendingMarkup(
    val id: String,
    val tool: PlaceTool,
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val sizePt: Float,
    val color: Int
)

/** Підсумок перевірки введеного тексту (без створення об'єкта). */
sealed interface TextVerdict {
    object Ok : TextVerdict
    object Empty : TextVerdict
    data class Unsupported(val chars: String) : TextVerdict
}

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

    /** Опорна точка об'єкта не ближче цього до правого/нижнього краю видимої сторінки (двигун відхиляє об'єкт, що цілком поза сторінкою). */
    const val PAGE_EDGE_MARGIN_PT = 2f

    fun defaultSize(tool: PlaceTool): Float =
        if (tool == PlaceTool.TEXT) DEFAULT_TEXT_SIZE_PT else DEFAULT_MARK_SIZE_PT

    /** Розмір у межах [MIN_SIZE_PT, max]; NaN/∞ → значення за замовчуванням. */
    fun clampSize(tool: PlaceTool, value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return defaultSize(tool)
        val max = if (tool == PlaceTool.TEXT) MAX_TEXT_SIZE_PT else MAX_MARK_SIZE_PT
        return value.coerceIn(MIN_SIZE_PT, max)
    }

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

    fun stepSize(tool: PlaceTool, current: Float, direction: Int): Float {
        if (current.isNaN() || current.isInfinite()) return defaultSize(tool)
        val delta = if (direction >= 0) stepAt(current) else -stepAt(current - 0.01f)
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
     */
    fun textFromTap(
        id: String, pageIndex: Int, x: Float, y: Float,
        rawText: String, sizePt: Float, color: Int, unsupported: String
    ): TextCheck {
        when (val v = checkText(rawText, unsupported)) {
            TextVerdict.Empty -> return TextCheck.Empty
            is TextVerdict.Unsupported -> return TextCheck.Unsupported(v.chars)
            TextVerdict.Ok -> Unit
        }
        return TextCheck.Ok(PendingMarkup(id, PlaceTool.TEXT, pageIndex, x, y, rawText, clampSize(PlaceTool.TEXT, sizePt), color))
    }

    fun toPageMarkup(p: PendingMarkup): PageMarkup = when (p.tool) {
        PlaceTool.CHECK -> PageMarkup.Mark(p.id, p.pageIndex, p.x, p.y, MarkKind.CHECK, p.sizePt, p.color)
        PlaceTool.CROSS -> PageMarkup.Mark(p.id, p.pageIndex, p.x, p.y, MarkKind.CROSS, p.sizePt, p.color)
        PlaceTool.TEXT -> PageMarkup.Text(p.id, p.pageIndex, p.x, p.y, p.text, p.sizePt, p.color)
    }

    // --- серіалізація (SavedStateHandle: список рядків) ----------------------------------------

    fun serialize(p: PendingMarkup): String = listOf(
        escape(p.id), p.tool.name, p.pageIndex.toString(),
        p.x.toBits().toString(), p.y.toBits().toString(),
        escape(p.text), p.sizePt.toBits().toString(), p.color.toString()
    ).joinToString("\t")

    /** null — рядок пошкоджений (такий об'єкт пропускається). */
    fun deserialize(s: String): PendingMarkup? = try {
        val f = s.split('\t')
        if (f.size != 8) null else PendingMarkup(
            id = unescape(f[0]),
            tool = PlaceTool.valueOf(f[1]),
            pageIndex = f[2].toInt(),
            x = Float.fromBits(f[3].toInt()),
            y = Float.fromBits(f[4].toInt()),
            text = unescape(f[5]),
            sizePt = Float.fromBits(f[6].toInt()),
            color = f[7].toInt()
        )
    } catch (_: Exception) {
        null
    }

    fun serializeAll(items: List<PendingMarkup>): ArrayList<String> = ArrayList(items.map(::serialize))
    fun deserializeAll(raw: List<String>?): List<PendingMarkup> = raw.orEmpty().mapNotNull(::deserialize)

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '\t' -> sb.append("\\t")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

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
