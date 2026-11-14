package com.savoj.volumpdf.ui.reader

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ActivityReaderBinding

/**
 * Нижній рядок інструментів і темна картка параметрів режиму «Редагувати» (етап A1, лише вигляд і взаємодія).
 * Рішення «що активне / що робить повторний тап / які секції» приймає чиста [PlaceBarLogic]; тут лише Android-прив'язка.
 * Дані (інструмент, колір, розмір) лишаються у [ReaderViewModel] і [PlaceEditor]: формат збереження не змінюється.
 */
class PlaceBarController(
    private val b: ActivityReaderBinding,
    private val vm: ReaderViewModel,
    private val host: Host
) {
    interface Host {
        /** Користувач вибрав інструмент (слот чи вибір дії в картці). */
        fun chooseTool(tool: PlaceTool)
        fun openSignature()
        fun pickImage()
        fun applySize(value: Float)
        fun applyColor(rgb: Int)
        fun applyPreset(preset: InkPreset)
        fun rotateImage(deltaDeg: Int)
        fun fitImage()
        fun deleteSelected()
        fun pickCustomColor(currentRgb: Int, onPicked: (Int) -> Unit)
        /** Перемалювати панель після зміни, яку зробив контролер. */
        fun refresh()
    }

    private val res = b.root.resources
    private val animMs = res.getInteger(R.integer.place_anim_ms).toLong()
    private val density = res.displayMetrics.density

    private var cardOpen = false
    private var page = 0
    private var syncing = false
    private var lastActionTool = PlaceTool.CHECK
    private var customRgb: Int? = null
    private var swatches: List<SwatchView> = emptyList()
    private var customSwatch: SwatchView? = null

    private val paletteNames = intArrayOf(
        R.string.color_black, R.string.color_red, R.string.color_orange, R.string.color_yellow, R.string.color_green,
        R.string.color_blue, R.string.color_indigo, R.string.color_purple, R.string.color_gray
    )

    private val slotButtons: Map<BarSlot, ImageButton> = mapOf(
        BarSlot.TEXT to b.btnSlotText,
        BarSlot.PEN to b.btnSlotPen,
        BarSlot.SELECT to b.btnSlotSelect,
        BarSlot.ACTION to b.btnSlotAction
    )

    init {
        slotButtons.forEach { (slot, button) -> button.setOnClickListener { onSlotTap(slot) } }
        b.btnSlotOptions.setOnClickListener {
            val sel = vm.editor.value.selected
            setCard(PlaceBarLogic.onOptionsTap(cardOpen, PlaceBarLogic.hasOptions(vm.placeTool, sel?.tool)))
            render(vm.editor.value)
        }
        b.btnSlotDelete.setOnClickListener { host.deleteSelected() }
        b.btnPlaceUndo.setOnClickListener { vm.edit { PlaceEditor.undo(it) } }
        b.btnPlaceRedo.setOnClickListener { vm.edit { PlaceEditor.redo(it) } }
        b.btnOptionsClose.setOnClickListener { setCard(false) }
        b.placeCardScrim.setOnClickListener { setCard(false) }

        b.btnToolCheck.setOnClickListener { chooseAction(PlaceTool.CHECK) }
        b.btnToolCross.setOnClickListener { chooseAction(PlaceTool.CROSS) }
        b.btnToolSignature.setOnClickListener {
            chooseAction(PlaceTool.SIGNATURE)
            if (vm.armedSignature == null) host.openSignature()
        }
        b.btnToolImage.setOnClickListener {
            chooseAction(PlaceTool.IMAGE)
            host.pickImage()
        }

        b.placeSizeSlider.setLabelFormatter { v -> res.getString(R.string.place_size_format, PlaceMarkupLogic.formatSize(v)) }
        b.placeSizeSlider.addOnChangeListener { _, value, fromUser -> if (fromUser && !syncing) host.applySize(value) }

        b.placeInkPresetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            host.applyPreset(
                when (checkedId) {
                    R.id.btnInkMarker -> InkPreset.MARKER
                    R.id.btnInkHighlighter -> InkPreset.HIGHLIGHTER
                    else -> InkPreset.PEN
                }
            )
        }
        b.switchStraighten.setOnCheckedChangeListener { _, checked -> if (!syncing) vm.straightenInk = checked }
        b.btnOptRotateLeft.setOnClickListener { host.rotateImage(-90) }
        b.btnOptRotateRight.setOnClickListener { host.rotateImage(90) }
        b.btnOptFitPage.setOnClickListener { host.fitImage() }

        buildSwatches()
    }

    // --- взаємодія --------------------------------------------------------------------------------

    private fun onSlotTap(slot: BarSlot) {
        val sel = vm.editor.value.selected
        val r = PlaceBarLogic.onSlotTap(
            vm.placeTool, cardOpen, slot, lastActionTool, PlaceBarLogic.hasOptions(vm.placeTool, sel?.tool)
        )
        if (r.tool != vm.placeTool) host.chooseTool(r.tool)
        setCard(r.cardOpen)
        render(vm.editor.value)
    }

    private fun chooseAction(tool: PlaceTool) {
        if (vm.placeTool != tool) host.chooseTool(tool)
        render(vm.editor.value)
    }

    /** Закриває картку (наприклад, при виході з режиму); без анімації, якщо [animate] = false. */
    fun closeCard(animate: Boolean = true) = setCard(false, animate)

    private fun setCard(open: Boolean, animate: Boolean = true) {
        if (open == cardOpen && (open == (b.placeOptionsCard.visibility == View.VISIBLE))) return
        cardOpen = open
        val card = b.placeOptionsCard
        card.animate().cancel()
        if (open) {
            page = 0
            bindCard(vm.editor.value)
            b.placeCardScrim.visibility = View.VISIBLE
            card.visibility = View.VISIBLE
            if (animate) {
                card.alpha = 0f
                card.translationY = 16f * density
                card.animate().alpha(1f).translationY(0f).setDuration(animMs).start()
            } else {
                card.alpha = 1f
                card.translationY = 0f
            }
        } else {
            b.placeCardScrim.visibility = View.GONE
            if (animate && card.visibility == View.VISIBLE) {
                card.animate().alpha(0f).translationY(16f * density).setDuration(animMs)
                    .withEndAction { if (!cardOpen) card.visibility = View.GONE }.start()
            } else {
                card.visibility = View.GONE
            }
        }
    }

    // --- відображення -----------------------------------------------------------------------------

    /** Узгоджує рядок і картку зі станом редактора та поточним інструментом. */
    fun render(state: PlaceEditorState) {
        val tool = vm.placeTool
        val sel = state.selected
        if (PlaceBarLogic.slotOf(tool) == BarSlot.ACTION) lastActionTool = tool
        slotButtons.forEach { (slot, button) -> button.isSelected = PlaceBarLogic.isActive(slot, tool) }
        b.btnSlotAction.setImageResource(
            when (if (PlaceBarLogic.slotOf(tool) == BarSlot.ACTION) tool else lastActionTool) {
                PlaceTool.CROSS -> R.drawable.ic_close
                PlaceTool.SIGNATURE -> R.drawable.ic_signature
                PlaceTool.IMAGE -> R.drawable.ic_image
                else -> R.drawable.ic_check
            }
        )
        b.btnPlaceUndo.isEnabled = state.canUndo
        b.btnPlaceRedo.isEnabled = state.canRedo
        b.btnSlotDelete.isEnabled = sel != null
        val hasOptions = PlaceBarLogic.hasOptions(tool, sel?.tool)
        b.btnSlotOptions.isEnabled = hasOptions
        b.btnSlotOptions.isSelected = cardOpen && hasOptions
        if (!PlaceBarLogic.cardStillValid(cardOpen, vm.placeModeActive, hasOptions)) {
            if (cardOpen) setCard(false)
        } else if (cardOpen) {
            bindCard(state)
        }
    }

    private fun titleOf(tool: PlaceTool): Int = when (tool) {
        PlaceTool.INK -> R.string.place_tool_ink
        PlaceTool.TEXT -> R.string.place_tool_text
        PlaceTool.CHECK -> R.string.place_tool_check
        PlaceTool.CROSS -> R.string.place_tool_cross
        PlaceTool.SIGNATURE -> R.string.place_tool_signature
        PlaceTool.IMAGE -> R.string.place_tool_image
        PlaceTool.HAND -> R.string.place_slot_select
    }

    private fun bindCard(state: PlaceEditorState) {
        val sel = state.selected
        val target = PlaceBarLogic.target(vm.placeTool, sel?.tool)
        val sections = PlaceBarLogic.sections(target, sel != null)
        val pages = PlaceBarLogic.pages(sections)
        page = page.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        val visible = pages.getOrElse(page) { emptyList() }.toSet()
        syncing = true
        try {
            b.tvOptionsTitle.setText(titleOf(target))
            b.placeSecChooser.visibility = vis(CardSection.ACTION_CHOOSER in visible)
            b.placeSecSize.visibility = vis(CardSection.SIZE in visible)
            b.placeColorGrid.visibility = vis(CardSection.COLOR in visible)
            b.placeInkPresetGroup.visibility = vis(CardSection.PRESETS in visible)
            b.placeSecStraighten.visibility = vis(CardSection.STRAIGHTEN in visible)
            b.placeImageBlock.visibility = vis(CardSection.IMAGE_CONTROLS in visible)

            val toolNow = vm.placeTool
            b.btnToolCheck.isSelected = toolNow == PlaceTool.CHECK
            b.btnToolCross.isSelected = toolNow == PlaceTool.CROSS
            b.btnToolSignature.isSelected = toolNow == PlaceTool.SIGNATURE
            b.btnToolImage.isSelected = toolNow == PlaceTool.IMAGE

            if (CardSection.SIZE in visible) bindSize(target, sel)
            if (CardSection.COLOR in visible) bindColors(sel?.color ?: vm.placeColor)
            if (CardSection.PRESETS in visible) {
                val preset = if (sel?.tool == PlaceTool.INK) presetOf(sel) else vm.inkPreset
                b.placeInkPresetGroup.clearChecked()
                when (preset) {
                    InkPreset.PEN -> b.placeInkPresetGroup.check(R.id.btnInkPen)
                    InkPreset.MARKER -> b.placeInkPresetGroup.check(R.id.btnInkMarker)
                    InkPreset.HIGHLIGHTER -> b.placeInkPresetGroup.check(R.id.btnInkHighlighter)
                    null -> Unit
                }
            }
            if (CardSection.STRAIGHTEN in visible) b.switchStraighten.isChecked = vm.straightenInk
            if (CardSection.IMAGE_CONTROLS in visible && sel != null) {
                b.tvImageRotationAngle.text = res.getString(R.string.image_rotation_angle, sel.rotationDeg)
            }
            bindDots(pages.size)
        } finally {
            syncing = false
        }
    }

    private fun vis(v: Boolean) = if (v) View.VISIBLE else View.GONE

    private fun bindSize(target: PlaceTool, sel: PendingMarkup?) {
        val min = PlaceMarkupLogic.sizeMin(target)
        val max = PlaceMarkupLogic.sizeMax(target)
        val value = (sel?.sizePt ?: vm.placeSize(target)).coerceIn(min, max)
        val s = b.placeSizeSlider
        s.stepSize = 0f
        // Спершу розширюємо межі, щоб значення не опинилось поза діапазоном, потім виставляємо точні.
        s.valueFrom = minOf(min, s.valueFrom, value)
        s.valueTo = maxOf(max, s.valueTo, value)
        s.value = value
        s.valueFrom = min
        s.valueTo = max
        b.tvSizeLabel.setText(if (target == PlaceTool.INK) R.string.place_width_label else R.string.place_size_label)
        s.contentDescription = res.getString(if (target == PlaceTool.INK) R.string.place_width_label else R.string.place_size_label)
    }

    // --- кольори ----------------------------------------------------------------------------------

    private fun buildSwatches() {
        val grid = b.placeColorGrid
        grid.removeAllViews()
        val list = ArrayList<SwatchView>()
        PlaceBarLogic.PALETTE.forEachIndexed { i, color ->
            val v = SwatchView(grid.context).apply {
                swatchColor = color
                contentDescription = res.getString(paletteNames[i])
                setOnClickListener { host.applyColor(color); host.refresh() }
            }
            grid.addView(v, ViewGroup.LayoutParams(res.getDimensionPixelSize(R.dimen.place_swatch_size), res.getDimensionPixelSize(R.dimen.place_swatch_size)))
            list += v
        }
        val custom = SwatchView(grid.context).apply {
            rainbow = true
            contentDescription = res.getString(R.string.color_custom)
            setOnClickListener {
                host.pickCustomColor(vm.editor.value.selected?.color ?: vm.placeColor) { rgb ->
                    customRgb = rgb and 0xFFFFFF
                    host.applyColor(0xFF000000.toInt() or rgb)
                    host.refresh()
                }
            }
        }
        grid.addView(custom, ViewGroup.LayoutParams(res.getDimensionPixelSize(R.dimen.place_swatch_size), res.getDimensionPixelSize(R.dimen.place_swatch_size)))
        swatches = list
        customSwatch = custom
        applyGridColumns()
    }

    /** Портрет: 5 колонок (два рядки), ландшафт: 10 (один рядок). */
    fun applyGridColumns() {
        val landscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        b.placeColorGrid.columnCount = if (landscape) 10 else 5
    }

    private fun bindColors(argb: Int) {
        val rgb = argb and 0xFFFFFF
        val custom = PlaceBarLogic.isCustomColor(rgb)
        swatches.forEachIndexed { i, v -> v.picked = (PlaceBarLogic.PALETTE[i] and 0xFFFFFF) == rgb }
        customSwatch?.picked = custom
    }

    /** Пресет, якому відповідає виділений штрих (за альфою й товщиною); null — власна товщина. */
    private fun presetOf(stroke: PendingMarkup): InkPreset? = when {
        (stroke.color ushr 24) < 0xFF -> InkPreset.HIGHLIGHTER
        stroke.sizePt == InkPreset.PEN.widthPt -> InkPreset.PEN
        stroke.sizePt == InkPreset.MARKER.widthPt -> InkPreset.MARKER
        else -> null
    }

    // --- крапки сторінок --------------------------------------------------------------------------

    private fun bindDots(count: Int) {
        val dots = b.placeDots
        dots.removeAllViews()
        if (count <= 1) {
            dots.visibility = View.GONE
            return
        }
        dots.visibility = View.VISIBLE
        val touch = res.getDimensionPixelSize(R.dimen.place_slot_min_size)
        for (i in 0 until count) {
            val frame = LinearLayout(dots.context).apply {
                gravity = android.view.Gravity.CENTER
                contentDescription = res.getString(R.string.place_options_page, i + 1, count)
                isClickable = true
                setOnClickListener {
                    if (page != i) {
                        page = i
                        bindCard(vm.editor.value)
                    }
                }
            }
            val dot = View(dots.context).apply {
                val d = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(d, d)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(res.getColor(if (i == page) R.color.place_card_on else R.color.place_card_muted, null))
                    alpha = if (i == page) 255 else 120
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            frame.addView(dot)
            dots.addView(frame, LinearLayout.LayoutParams(touch, touch))
        }
    }

    // --- розмір картки ----------------------------------------------------------------------------

    /** Ширина картки: портрет — на всю ширину (з полями), ландшафт і планшет — не більше [R.dimen.place_card_max_width]. */
    fun updateCardWidth(availablePx: Int) {
        val lp = b.placeOptionsCard.layoutParams
        val margin = 2 * res.getDimensionPixelSize(R.dimen.place_card_margin)
        val max = res.getDimensionPixelSize(R.dimen.place_card_max_width)
        val landscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val limit = if (landscape) (max * 4 / 3) else max
        val width = minOf(limit, availablePx - margin)
        if (lp.width != width) {
            lp.width = width
            b.placeOptionsCard.layoutParams = lp
        }
    }
}
