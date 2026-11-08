package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.MarkupLayout

/** Розмір видимої області сторінки, pt (простір PageViewPoint). */
data class PageSizePt(val widthPt: Float, val heightPt: Float)

/** Прямокутник у пікселях (без Android-класів, щоб тестувати на JVM). */
data class RectPx(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun inflate(d: Float) = RectPx(left - d, top - d, right + d, bottom + d)

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

    /** Відстань від точки до прямокутника; 0 — усередині. */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }
}

data class HitTarget(val id: String, val rect: RectPx)

/** Вибір об'єкта під пальцем. Зона дотику — межі об'єкта плюс [SLOP_DP] з усіх боків. */
object PlaceHitTest {
    const val SLOP_DP = 24f

    /**
     * id найближчого об'єкта, чия зона дотику (межі + [slopPx]) містить (x, y); null — дотик повз усе.
     * Якщо кілька — той, до чиїх меж ближче; за рівності — верхній (пізніший у списку).
     */
    fun hit(targets: List<HitTarget>, x: Float, y: Float, slopPx: Float): String? {
        var best: String? = null
        var bestDist = Float.MAX_VALUE
        for (t in targets) {
            if (!t.rect.inflate(slopPx).contains(x, y)) continue
            val d = t.rect.distanceTo(x, y)
            if (d <= bestDist) {
                bestDist = d
                best = t.id
            }
        }
        return best
    }
}

/** Стан редактора незбережених об'єктів: список, виділення й історія скасування (лише в пам'яті). */
data class PlaceEditorState(
    val items: List<PendingMarkup> = emptyList(),
    val selectedId: String? = null,
    /** Знімки списку до кожної дії; найстаріший першим. */
    val history: List<List<PendingMarkup>> = emptyList(),
    /** Ключ останньої змінювальної дії; однакові поспіль (розмір/колір того ж об'єкта) — один крок скасування. */
    val lastEditKey: String? = null
) {
    val canUndo: Boolean get() = history.isNotEmpty()
    val selected: PendingMarkup? get() = selectedId?.let { id -> items.firstOrNull { it.id == id } }
}

/** Чисті переходи стану: додавання, рух, зміна, видалення, скасування, скидання. */
object PlaceEditor {
    /** Глибина історії (вимога ≥ 20). */
    const val MAX_HISTORY = 50

    /** Початковий стан із відновлених об'єктів (без виділення й без історії). */
    fun initial(items: List<PendingMarkup>) = PlaceEditorState(items = items)

    /** Після успішного збереження чи відкидання: нічого, ні виділення, ні історії. */
    fun reset() = PlaceEditorState()

    fun add(s: PlaceEditorState, item: PendingMarkup): PlaceEditorState =
        commit(s, s.items + item, key = null, selectedId = s.selectedId)

    fun select(s: PlaceEditorState, id: String?): PlaceEditorState {
        val valid = id?.takeIf { wanted -> s.items.any { it.id == wanted } }
        return if (valid == s.selectedId && s.lastEditKey == null) s else s.copy(selectedId = valid, lastEditKey = null)
    }

    /** Переносить опорну точку; [page] — розмір сторінки об'єкта для кламп (null — невідомий). */
    fun move(s: PlaceEditorState, id: String, x: Float, y: Float, page: PageSizePt?): PlaceEditorState {
        val (cx, cy) = PlaceMarkupLogic.clampToPage(x, y, page)
        return update(s, id, key = null) { if (it.x == cx && it.y == cy) it else it.copy(x = cx, y = cy) }
    }

    fun setSize(s: PlaceEditorState, id: String, sizePt: Float): PlaceEditorState =
        update(s, id, key = "size:$id") { it.copy(sizePt = PlaceMarkupLogic.clampSize(it.tool, sizePt)) }

    fun setColor(s: PlaceEditorState, id: String, color: Int): PlaceEditorState =
        update(s, id, key = "color:$id") { it.copy(color = color) }

    /** Нове значення тексту для текстового об'єкта; для ✓/✗ — без змін. Перевірку тексту робить викликач. */
    fun setText(s: PlaceEditorState, id: String, text: String): PlaceEditorState =
        update(s, id, key = null) { if (it.tool == PlaceTool.TEXT) it.copy(text = text) else it }

    /** ✓ ↔ ✗; для тексту — без змін. */
    fun toggleMarkKind(s: PlaceEditorState, id: String): PlaceEditorState =
        update(s, id, key = null) {
            when (it.tool) {
                PlaceTool.CHECK -> it.copy(tool = PlaceTool.CROSS)
                PlaceTool.CROSS -> it.copy(tool = PlaceTool.CHECK)
                PlaceTool.TEXT -> it
            }
        }

    fun delete(s: PlaceEditorState, id: String): PlaceEditorState {
        if (s.items.none { it.id == id }) return s
        return commit(s, s.items.filterNot { it.id == id }, key = null, selectedId = null)
    }

    /** Повертає список до стану перед останньою дією; зникле виділення знімається. */
    fun undo(s: PlaceEditorState): PlaceEditorState {
        val prev = s.history.lastOrNull() ?: return s
        val selected = s.selectedId?.takeIf { id -> prev.any { it.id == id } }
        return PlaceEditorState(items = prev, selectedId = selected, history = s.history.dropLast(1), lastEditKey = null)
    }

    private fun update(
        s: PlaceEditorState, id: String, key: String?, transform: (PendingMarkup) -> PendingMarkup
    ): PlaceEditorState {
        val old = s.items.firstOrNull { it.id == id } ?: return s
        val new = transform(old)
        if (new == old) return s
        return commit(s, s.items.map { if (it.id == id) new else it }, key, s.selectedId)
    }

    private fun commit(s: PlaceEditorState, items: List<PendingMarkup>, key: String?, selectedId: String?): PlaceEditorState {
        val coalesce = key != null && key == s.lastEditKey && s.history.isNotEmpty()
        val history = if (coalesce) s.history else (s.history + listOf(s.items)).takeLast(MAX_HISTORY)
        return PlaceEditorState(items = items, selectedId = selectedId, history = history, lastEditKey = key)
    }
}

/** Межі об'єктів у пікселях — для малювання рамки й для зони дотику (одні й ті самі числа). */
object PlaceBounds {
    /** Нижче бази: Roboto descent ≈ 0.21 em. */
    const val TEXT_DESCENT_RATIO = 0.22f

    /**
     * Межі об'єкта в пікселях за положенням опорної точки ([originX], [originY]) і масштабом [zoom].
     * [lineWidthPx] — ширина рядка тексту в пікселях, виміряна тим самим Paint, що малює текст.
     */
    fun boundsPx(
        item: PendingMarkup, originX: Float, originY: Float, zoom: Float, lineWidthPx: (String) -> Float
    ): RectPx = when (item.tool) {
        PlaceTool.CHECK, PlaceTool.CROSS -> {
            val half = item.sizePt * zoom / 2f + MarkupOverlayMath.strokePx(item.sizePt, zoom) / 2f
            RectPx(originX - half, originY - half, originX + half, originY + half)
        }
        PlaceTool.TEXT -> {
            val fs = item.sizePt * zoom
            val lines = item.text.replace("\r", "").split('\n')
            val width = lines.maxOf { if (it.isEmpty()) 0f else lineWidthPx(it) }
            val height = (MarkupLayout.TEXT_ASCENT_RATIO + TEXT_DESCENT_RATIO +
                (lines.size - 1) * MarkupLayout.TEXT_LINE_HEIGHT_RATIO) * fs
            RectPx(originX, originY, originX + width, originY + height)
        }
    }
}
