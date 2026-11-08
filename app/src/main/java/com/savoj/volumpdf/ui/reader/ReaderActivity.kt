package com.savoj.volumpdf.ui.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.pdf.AnnotationEngine
import com.savoj.volumpdf.core.pdf.FormEngine
import com.savoj.volumpdf.core.pdf.PdfBoxAnnotationEngine
import com.savoj.volumpdf.core.pdf.MarkupEngine
import com.savoj.volumpdf.core.pdf.MarkupLayout
import com.savoj.volumpdf.core.pdf.PdfBoxFormEngine
import com.savoj.volumpdf.core.pdf.PdfBoxMarkupEngine
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.databinding.ActivityReaderBinding
import com.savoj.volumpdf.databinding.DialogPlaceTextBinding
import com.savoj.volumpdf.ui.common.SystemEdges
import com.savoj.volumpdf.ui.common.WindowInsetsHelper
import com.savoj.volumpdf.util.SafUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.UUID

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()

    private var documentUri: Uri? = null
    private var documentTitle: String? = null
    private var viewerFragment: VolumPdfViewerFragment? = null

    private val annotationEngine: AnnotationEngine = PdfBoxAnnotationEngine()
    private val formEngine: FormEngine = PdfBoxFormEngine()
    private var detectedFormFields: List<FormField> = emptyList()

    private val markupEngine: MarkupEngine =
        PdfBoxMarkupEngine({ applicationContext.assets.open(MarkupLayout.FONT_ASSET_PATH) })
    private var enteringPlaceMode = false
    private var savingPlace = false

    private var isDrawingMode = false

    private enum class BrushType {
        PEN, MARKER, HIGHLIGHTER
    }

    private var currentBrushType = BrushType.PEN
    private var baseColor = COLOR_RED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applyWindowInsets()

        val uri = intent.data ?: savedInstanceState?.getString(KEY_SAVED_URI)?.let { Uri.parse(it) }
        if (uri == null) {
            finish()
            return
        }

        if (savedInstanceState == null) {
            // Новий запуск читача: копій від попередніх сеансів (зокрема після аварійного завершення) не лишаємо.
            // Поворот і відновлення після завершення процесу (savedInstanceState != null) копію зберігають.
            val cache = cacheDir
            Thread { ViewerCopyCache.purge(cache) }.start()
        }

        documentUri = uri
        documentTitle = intent.getStringExtra(EXTRA_DOCUMENT_TITLE)
            ?: SafUtils.queryDisplayName(this, uri)

        binding.toolbar.title = documentTitle
        viewModel.initDocument(uri, documentTitle)

        setupViewerFragment(uri, savedInstanceState)
        setupDrawingToolbar()
        setupBottomActionBar()
        setupBackNavigation()
        setupPlaceMode()
        observeViewModel()

        checkForFormFields(uri)
    }

    private fun setupViewerFragment(uri: Uri, savedInstanceState: Bundle?) {
        val fragment: VolumPdfViewerFragment
        if (savedInstanceState == null) {
            fragment = VolumPdfViewerFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.pdfViewerContainer, fragment, TAG_PDF_VIEWER)
                .commitNow()
            viewerFragment = fragment
        } else {
            fragment = (supportFragmentManager.findFragmentByTag(TAG_PDF_VIEWER) as? VolumPdfViewerFragment)
                ?: VolumPdfViewerFragment().also {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.pdfViewerContainer, it, TAG_PDF_VIEWER)
                        .commitNow()
                }
            viewerFragment = fragment
        }

        fragment.onDocumentLoadedListener = { count ->
            viewModel.updatePageCount(count)
        }

        fragment.onDocumentErrorListener = { cause ->
            val msg = cause.localizedMessage ?: getString(R.string.error_open_document, cause.javaClass.simpleName)
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        }

        val shown = fragment.documentUri
        if (shown == null) {
            fragment.documentUri = uri
        } else if (ViewerCopyCache.isViewCopy(cacheDir, shown) && !File(shown.path.orEmpty()).exists()) {
            // Система прибрала кеш, поки застосунок був вивантажений: повертаємося до оригіналу.
            fragment.documentUri = uri
        }
    }

    private fun setupBottomActionBar() {
        binding.bottomActionBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_forms -> {
                    if (isDrawingMode) toggleDrawingMode(false)
                    leavePlaceThen { openFillFormDialog() }
                    false
                }
                R.id.action_draw -> {
                    toggleDrawingMode()
                    false
                }
                R.id.action_signature -> {
                    if (isDrawingMode) toggleDrawingMode(false)
                    openSignatureDialog()
                    false
                }
                R.id.action_place -> {
                    if (isDrawingMode) toggleDrawingMode(false)
                    enterPlaceMode()
                    false
                }
                else -> false
            }
        }
    }

    private fun checkForFormFields(uri: Uri) {
        lifecycleScope.launch {
            val fields = withContext(Dispatchers.IO) {
                try {
                    formEngine.extractFormFields(applicationContext, uri)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }
            detectedFormFields = fields
            updateFormsActionButton(fields.size)
        }
    }

    private fun updateFormsActionButton(count: Int) {
        val formsItem = binding.bottomActionBar.menu.findItem(R.id.action_forms) ?: return
        if (count > 0) {
            formsItem.isVisible = true
            val badge = binding.bottomActionBar.getOrCreateBadge(R.id.action_forms)
            badge.number = count
            badge.isVisible = true
        } else {
            formsItem.isVisible = false
            binding.bottomActionBar.removeBadge(R.id.action_forms)
        }
    }

    private fun openFillFormDialog() {
        val uri = documentUri ?: return
        val dialog = FillFormBottomSheetDialogFragment.newInstance(uri, detectedFormFields)
        dialog.onFormSavedListener = { savedUri ->
            documentUri = savedUri
            reloadViewerDocument()
            checkForFormFields(savedUri)
            Snackbar.make(binding.root, R.string.form_saved_success, Snackbar.LENGTH_SHORT).show()
        }
        dialog.show(supportFragmentManager, FillFormBottomSheetDialogFragment.TAG)
    }

    private fun setupDrawingToolbar() {
        binding.toggleBrushTypeGroup.check(R.id.btnBrushPen)
        binding.toggleColorPaletteGroup.check(R.id.btnColorPaletteRed)
        applyBrushSettings()

        binding.toggleBrushTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentBrushType = when (checkedId) {
                    R.id.btnBrushMarker -> BrushType.MARKER
                    R.id.btnBrushHighlighter -> BrushType.HIGHLIGHTER
                    else -> BrushType.PEN
                }
                applyBrushSettings()
            }
        }

        binding.toggleColorPaletteGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                baseColor = when (checkedId) {
                    R.id.btnColorPaletteBlack -> COLOR_BLACK
                    R.id.btnColorPaletteBlue -> COLOR_BLUE
                    R.id.btnColorPaletteGreen -> COLOR_GREEN
                    R.id.btnColorPaletteYellow -> COLOR_YELLOW
                    else -> COLOR_RED
                }
                applyBrushSettings()
            }
        }

        binding.btnUndoStroke.setOnClickListener {
            binding.inkCanvasView.undo()
        }

        binding.btnClearStrokes.setOnClickListener {
            binding.inkCanvasView.clear()
        }

        binding.btnCloseDrawing.setOnClickListener {
            toggleDrawingMode(false)
        }

        binding.btnSaveDrawing.setOnClickListener {
            saveDrawingToDocument()
        }
    }

    private fun applyBrushSettings() {
        when (currentBrushType) {
            BrushType.PEN -> {
                binding.inkCanvasView.currentColor = baseColor
                binding.inkCanvasView.currentStrokeWidth = 5f
            }
            BrushType.MARKER -> {
                binding.inkCanvasView.currentColor = baseColor
                binding.inkCanvasView.currentStrokeWidth = 12f
            }
            BrushType.HIGHLIGHTER -> {
                val r = Color.red(baseColor)
                val g = Color.green(baseColor)
                val b = Color.blue(baseColor)
                val highlighterColor = Color.argb(0x66, r, g, b)
                binding.inkCanvasView.currentColor = highlighterColor
                binding.inkCanvasView.currentStrokeWidth = 28f
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDrawingMode) {
                    toggleDrawingMode(false)
                } else if (viewModel.placeModeActive) {
                    leavePlaceThen { }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun toggleDrawingMode(enable: Boolean? = null) {
        isDrawingMode = enable ?: !isDrawingMode
        binding.inkCanvasView.visibility = if (isDrawingMode) View.VISIBLE else View.GONE
        binding.drawingToolbarCard.visibility = if (isDrawingMode) View.VISIBLE else View.GONE
        binding.bottomActionBar.visibility = if (isDrawingMode) View.GONE else View.VISIBLE

        if (!isDrawingMode) {
            binding.inkCanvasView.clear()
        }
    }

    private fun saveDrawingToDocument() {
        if (!binding.inkCanvasView.hasStrokes()) {
            toggleDrawingMode(false)
            return
        }

        val uri = documentUri ?: return
        val pageIndex = viewerFragment?.currentPageIndex ?: 0

        showLoadingOverlay(getString(R.string.saving_annotations))

        lifecycleScope.launch {
            try {
                val (pageWidthPt, pageHeightPt) = getPageDimensions(uri, pageIndex)
                val strokes = binding.inkCanvasView.getScaledStrokes(pageWidthPt, pageHeightPt)

                val result = annotationEngine.addInkAnnotations(
                    context = applicationContext,
                    sourceUri = uri,
                    pageIndex = pageIndex,
                    strokes = strokes,
                    pageWidthPt = pageWidthPt,
                    pageHeightPt = pageHeightPt,
                    outputUri = uri
                )

                hideLoadingOverlay()

                when (result) {
                    is PdfOperationResult.Success -> {
                        binding.inkCanvasView.clear()
                        toggleDrawingMode(false)
                        reloadViewerDocument()
                        checkForFormFields(uri)
                        Snackbar.make(binding.root, R.string.drawing_success, Snackbar.LENGTH_SHORT).show()
                    }
                    is PdfOperationResult.Error -> {
                        Snackbar.make(binding.root, result.userMessage, Snackbar.LENGTH_LONG).show()
                    }
                    is PdfOperationResult.Progress -> Unit
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                hideLoadingOverlay()
                val msg = e.localizedMessage ?: getString(R.string.error_title)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun openSignatureDialog() {
        val dialog = SignatureBottomSheetDialogFragment.newInstance()
        dialog.onSignatureConfirmed = { signatureBitmap ->
            insertSignatureToDocument(signatureBitmap)
        }
        dialog.show(supportFragmentManager, SignatureBottomSheetDialogFragment.TAG)
    }

    private fun insertSignatureToDocument(bitmap: Bitmap) {
        val uri = documentUri ?: return
        val pageIndex = viewerFragment?.currentPageIndex ?: 0

        showLoadingOverlay(getString(R.string.saving_annotations))

        lifecycleScope.launch {
            try {
                val (pageWidthPt, pageHeightPt) = getPageDimensions(uri, pageIndex)

                // Розміщення підпису у правій нижній частині сторінки
                val stampWidthPt = minOf(160f, pageWidthPt * 0.35f)
                val stampHeightPt = stampWidthPt * (bitmap.height.toFloat() / maxOf(1, bitmap.width).toFloat())
                val left = pageWidthPt - stampWidthPt - 40f
                val top = pageHeightPt - stampHeightPt - 60f
                val targetRectPt = RectF(left, top, left + stampWidthPt, top + stampHeightPt)

                val result = annotationEngine.addSignatureStamp(
                    context = applicationContext,
                    sourceUri = uri,
                    pageIndex = pageIndex,
                    signatureBitmap = bitmap,
                    targetRectPt = targetRectPt,
                    pageWidthPt = pageWidthPt,
                    pageHeightPt = pageHeightPt,
                    outputUri = uri
                )

                hideLoadingOverlay()

                when (result) {
                    is PdfOperationResult.Success -> {
                        reloadViewerDocument()
                        checkForFormFields(uri)
                        Snackbar.make(binding.root, R.string.signature_success, Snackbar.LENGTH_SHORT).show()
                    }
                    is PdfOperationResult.Error -> {
                        Snackbar.make(binding.root, result.userMessage, Snackbar.LENGTH_LONG).show()
                    }
                    is PdfOperationResult.Progress -> Unit
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                hideLoadingOverlay()
                val msg = e.localizedMessage ?: getString(R.string.error_title)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Режим «Поставити» (етап 3a): ✓, ✗, текст; незбережене живе у ViewModel; одне «Зберегти».
    // ---------------------------------------------------------------------------------------------

    private val toolButtons by lazy {
        mapOf(
            PlaceTool.CHECK to R.id.btnToolCheck,
            PlaceTool.CROSS to R.id.btnToolCross,
            PlaceTool.TEXT to R.id.btnToolText
        )
    }

    private val colorButtons by lazy {
        mapOf(
            R.id.btnPlaceColorRed to COLOR_RED,
            R.id.btnPlaceColorBlack to COLOR_BLACK,
            R.id.btnPlaceColorBlue to COLOR_BLUE,
            R.id.btnPlaceColorGreen to COLOR_GREEN,
            R.id.btnPlaceColorYellow to COLOR_YELLOW
        )
    }

    /** true, поки панель синхронізується з виділеним об'єктом програмно (слухачі кнопок мовчать). */
    private var syncingPanel = false

    private fun setupPlaceMode() {
        val fragment = viewerFragment
        binding.markupOverlay.bridge = fragment?.bridge
        binding.markupOverlay.editListener = object : MarkupOverlayView.EditListener {
            override fun onItemTapped(id: String) = viewModel.edit { PlaceEditor.select(it, id) }

            override fun onItemMoved(id: String, x: Float, y: Float) {
                val pageIndex = viewModel.editor.value.items.firstOrNull { it.id == id }?.pageIndex ?: return
                val page = viewerFragment?.bridge?.pageSizePt(pageIndex)
                viewModel.edit { PlaceEditor.move(PlaceEditor.select(it, id), id, x, y, page) }
            }
        }

        binding.placeToolGroup.check(toolButtons.getValue(viewModel.placeTool))
        val colorId = colorButtons.entries.firstOrNull { it.value == viewModel.placeColor }?.key ?: R.id.btnPlaceColorRed
        binding.placeColorGroup.check(colorId)
        updateSizeLabel()
        applyPlacePanelOrientation()

        binding.placeToolGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !syncingPanel) {
                toolButtons.entries.firstOrNull { it.value == checkedId }?.key?.let { viewModel.placeTool = it }
                updateSizeLabel()
            }
        }
        binding.placeColorGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingPanel) return@addOnButtonCheckedListener
            val color = colorButtons[checkedId] ?: return@addOnButtonCheckedListener
            val selectedId = viewModel.editor.value.selectedId
            if (selectedId != null) viewModel.edit { PlaceEditor.setColor(it, selectedId, color) }
            else viewModel.placeColor = color
        }
        binding.btnSizeMinus.setOnClickListener { changePlaceSize(-1) }
        binding.btnSizePlus.setOnClickListener { changePlaceSize(+1) }
        binding.btnPlaceDone.setOnClickListener { leavePlaceThen { } }
        binding.btnPlaceSave.setOnClickListener { savePending() }
        binding.btnPlaceUndo.setOnClickListener { viewModel.edit { PlaceEditor.undo(it) } }
        binding.btnSelDelete.setOnClickListener {
            viewModel.editor.value.selectedId?.let { id -> viewModel.edit { PlaceEditor.delete(it, id) } }
        }
        binding.btnSelEdit.setOnClickListener { editSelected() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editor.collect { state -> renderEditor(state) }
            }
        }

        // Відновлення після завершення процесу: режим і незбережене повертаються з ViewModel.
        if (viewModel.placeModeActive || viewModel.editor.value.items.isNotEmpty()) {
            enterPlaceMode()
        }
    }

    /** Малює стан редактора: оверлей, кнопки «Зберегти»/«Скасувати», панель дій виділеного, значення розміру й кольору. */
    private fun renderEditor(state: PlaceEditorState) {
        binding.markupOverlay.items = state.items
        binding.markupOverlay.selectedId = state.selectedId
        updatePlaceSaveVisibility(state.items.isNotEmpty())
        binding.btnPlaceUndo.isEnabled = state.canUndo

        val sel = state.selected
        binding.selectionBarCard.visibility =
            if (sel != null && viewModel.placeModeActive) View.VISIBLE else View.GONE
        if (sel != null) {
            binding.btnSelEdit.setText(
                when (sel.tool) {
                    PlaceTool.TEXT -> R.string.place_edit_text
                    PlaceTool.CHECK -> R.string.place_change_to_cross
                    PlaceTool.CROSS -> R.string.place_change_to_check
                }
            )
        }
        syncingPanel = true
        try {
            val color = sel?.color ?: viewModel.placeColor
            colorButtons.entries.firstOrNull { it.value == color }?.key?.let { binding.placeColorGroup.check(it) }
            updateSizeLabel()
        } finally {
            syncingPanel = false
        }
    }

    /** «Змінити»: текст — повторне введення, ✓/✗ — перемикання виду. */
    private fun editSelected() {
        val sel = viewModel.editor.value.selected ?: return
        when (sel.tool) {
            PlaceTool.TEXT -> showTextDialog(
                initial = sel.text, titleRes = R.string.place_edit_text, confirmRes = R.string.place_text_apply
            ) { raw -> viewModel.edit { PlaceEditor.setText(it, sel.id, raw) } }
            PlaceTool.CHECK, PlaceTool.CROSS -> viewModel.edit { PlaceEditor.toggleMarkKind(it, sel.id) }
        }
    }

    private fun changePlaceSize(direction: Int) {
        val sel = viewModel.editor.value.selected
        if (sel != null) {
            val next = PlaceMarkupLogic.stepSize(sel.tool, sel.sizePt, direction)
            viewModel.edit { PlaceEditor.setSize(it, sel.id, next) }
            return
        }
        val tool = viewModel.placeTool
        viewModel.setPlaceSize(tool, PlaceMarkupLogic.stepSize(tool, viewModel.placeSize(tool), direction))
        updateSizeLabel()
    }

    private fun updateSizeLabel() {
        val size = viewModel.editor.value.selected?.sizePt ?: viewModel.placeSize(viewModel.placeTool)
        binding.tvPlaceSize.text = getString(R.string.place_size_format, size.toInt())
    }

    /** Перевірка прав → показ панелі й увімкнення тапу в мості. Для захищених/заборонених — лише пояснення. */
    private fun enterPlaceMode() {
        if (enteringPlaceMode) return
        if (viewModel.placeModeActive && binding.placePanelCard.visibility == View.VISIBLE) return
        val uri = documentUri ?: return
        enteringPlaceMode = true
        lifecycleScope.launch {
            try {
                val editability = withContext(Dispatchers.IO) {
                    try {
                        markupEngine.checkEditability(applicationContext, uri, null)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        null
                    }
                }
                val gate = PlaceMarkupLogic.gate(editability?.isEncrypted, editability?.canMarkup)
                if (gate != PlaceMarkupLogic.Gate.ALLOWED) {
                    viewModel.clearPending()
                    viewModel.placeModeActive = false
                    showMessage(gateMessage(gate), long = true)
                    return@launch
                }
                showPlaceUi()
            } finally {
                enteringPlaceMode = false
            }
        }
    }

    private fun gateMessage(gate: PlaceMarkupLogic.Gate): String = getString(
        when (gate) {
            PlaceMarkupLogic.Gate.ENCRYPTED -> R.string.place_blocked_encrypted
            PlaceMarkupLogic.Gate.NO_MARKUP_RIGHTS -> R.string.place_blocked_rights
            else -> R.string.place_blocked_unreadable
        }
    )

    private fun showPlaceUi() {
        viewModel.placeModeActive = true
        Thread { safeUnsupported("a") }.start() // прогрів кешу шрифту: перша перевірка тексту без затримки
        binding.placePanelCard.visibility = View.VISIBLE
        binding.bottomActionBar.visibility = View.GONE
        viewerFragment?.bridge?.setPlaceMode(true) { point -> onPageTapped(point) }
        binding.markupOverlay.editingEnabled = !savingPlace
        renderEditor(viewModel.editor.value)
    }

    private fun hidePlaceUi() {
        viewModel.placeModeActive = false
        binding.markupOverlay.editingEnabled = false
        viewModel.clearPending() // виділення й історія скасування не переживають вихід із режиму
        binding.selectionBarCard.visibility = View.GONE
        viewerFragment?.bridge?.setPlaceMode(false)
        binding.placePanelCard.visibility = View.GONE
        if (!isDrawingMode) binding.bottomActionBar.visibility = View.VISIBLE
    }

    /**
     * Виходить із режиму й виконує [action]. Якщо є незбережене — діалог «Зберегти / Відкинути / Скасувати».
     * Якщо режим не активний — просто [action].
     */
    private fun leavePlaceThen(action: () -> Unit) {
        if (!viewModel.placeModeActive) {
            action()
            return
        }
        if (viewModel.editor.value.items.isEmpty()) {
            hidePlaceUi()
            action()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.place_unsaved_title)
            .setMessage(R.string.place_unsaved_message)
            .setPositiveButton(R.string.action_save) { _, _ ->
                savePending(onSuccess = {
                    hidePlaceUi()
                    action()
                })
            }
            .setNegativeButton(R.string.place_discard) { _, _ ->
                viewModel.clearPending()
                hidePlaceUi()
                action()
            }
            .setNeutralButton(R.string.action_cancel, null)
            .show()
    }

    private fun onPageTapped(point: PageViewPoint) {
        if (savingPlace || viewerFragment?.bridge?.isRestoring == true) return
        if (viewModel.editor.value.selectedId != null) { // тап повз об'єкт знімає виділення, нового об'єкта не ставить
            viewModel.edit { PlaceEditor.select(it, null) }
            return
        }
        when (val tool = viewModel.placeTool) {
            PlaceTool.CHECK, PlaceTool.CROSS -> viewModel.addPending(
                PlaceMarkupLogic.markFromTap(
                    UUID.randomUUID().toString(), tool, point.pageIndex, point.x, point.y,
                    viewModel.placeSize(tool), viewModel.placeColor
                )
            )
            PlaceTool.TEXT -> showTextInputDialog(point)
        }
    }

    private fun showTextInputDialog(point: PageViewPoint) {
        showTextDialog(initial = "", titleRes = R.string.place_text_title, confirmRes = R.string.place_text_add) { raw ->
            val check = PlaceMarkupLogic.textFromTap(
                UUID.randomUUID().toString(), point.pageIndex, point.x, point.y,
                raw, viewModel.placeSize(PlaceTool.TEXT), viewModel.placeColor, safeUnsupported(raw) ?: ""
            )
            if (check is TextCheck.Ok) viewModel.addPending(check.markup)
        }
    }

    /** Непідтримувані символи; null — перевірку виконати не вдалося (шрифт недоступний). */
    private fun safeUnsupported(raw: String): String? = try {
        markupEngine.findUnsupportedChars(raw)
    } catch (e: Throwable) {
        null
    }

    /**
     * Діалог введення тексту (додавання й повторне введення). Перевірка символів — при кожній зміні тексту
     * (TextWatcher, debounce [TEXT_VALIDATE_DEBOUNCE_MS]); «Додати» вимкнена, поки текст порожній або містить
     * непідтримувані символи. Шрифт розбирається один раз (кеш у двигуні) і прогрівається при вході в режим.
     * Кнопки діалогу лежать поза прокруткою й завжди ≥ 48 dp.
     */
    private fun showTextDialog(initial: String, titleRes: Int, confirmRes: Int, onAccept: (String) -> Unit) {
        val dialogBinding = DialogPlaceTextBinding.inflate(layoutInflater)
        dialogBinding.placeTextTitle.setText(titleRes)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(confirmRes, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var validate: Runnable

        // Z2: у ландшафті з клавіатурою видима частина діалогу низька (≈65 dp) — заголовок прибираємо, а
        // після появи помилки прокручуємо вміст так, щоб поле разом із рядком помилки було видно.
        val scroll = dialogBinding.root as ScrollView
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            dialogBinding.placeTextTitle.visibility = View.GONE
            scroll.setPadding(scroll.paddingLeft, 0, scroll.paddingRight, 0)
        }
        fun revealError() {
            scroll.post {
                val content = scroll.getChildAt(0) ?: return@post
                scroll.scrollTo(0, (content.height - scroll.height).coerceAtLeast(0))
            }
        }

        fun render(raw: String): Boolean {
            val unsupported = safeUnsupported(raw)
            if (unsupported == null) {
                dialogBinding.placeTextInputLayout.error = getString(R.string.error_title)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                return false
            }
            val verdict = PlaceMarkupLogic.checkText(raw, unsupported)
            dialogBinding.placeTextInputLayout.error = when (verdict) {
                TextVerdict.Ok -> null
                TextVerdict.Empty -> if (raw.isEmpty()) null else getString(R.string.text_error_empty)
                is TextVerdict.Unsupported -> getString(R.string.place_text_unsupported, verdict.chars)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = verdict == TextVerdict.Ok
            if (verdict != TextVerdict.Ok && dialogBinding.placeTextInputLayout.error != null) revealError()
            return verdict == TextVerdict.Ok
        }

        validate = Runnable { render(dialogBinding.placeTextInput.text?.toString().orEmpty()) }
        dialogBinding.placeTextInput.setText(initial)
        dialogBinding.placeTextInput.setSelection(initial.length)
        dialogBinding.placeTextInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                handler.removeCallbacks(validate)
                if (s.isNullOrBlank()) {
                    validate.run() // порожнє: без затримки вимикаємо «Додати»
                } else {
                    handler.postDelayed(validate, TEXT_VALIDATE_DEBOUNCE_MS)
                }
            }
        })
        dialog.setOnShowListener {
            val min = dp(48)
            val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            listOf(confirm, cancel).forEach { it.minHeight = min; it.minimumHeight = min; it.minWidth = min; it.minimumWidth = min }
            render(dialogBinding.placeTextInput.text?.toString().orEmpty())
            // Z1: SOFT_INPUT_STATE_VISIBLE сам клавіатуру не показує (Gboard на S24 Ultra): фокус + явний показ IME.
            val input = dialogBinding.placeTextInput
            input.requestFocus()
            input.post {
                dialog.window?.let { w -> WindowCompat.getInsetsController(w, input).show(WindowInsetsCompat.Type.ime()) }
            }
            confirm.setOnClickListener {
                handler.removeCallbacks(validate)
                val raw = dialogBinding.placeTextInput.text?.toString().orEmpty()
                if (render(raw)) { // остаточна перевірка без затримки
                    onAccept(raw)
                    dialog.dismiss()
                }
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(validate) }
        dialog.show()
    }

    /**
     * Єдине збереження: checkEditability → applyEdits (той самий URI) → reload. Після успіху незбережене
     * очищується; при Error лишається. Реклама в читачі не показується (R4).
     */
    private fun savePending(onSuccess: () -> Unit = {}) {
        if (savingPlace) return
        val uri = documentUri ?: return
        val items = viewModel.editor.value.items
        if (items.isEmpty()) {
            onSuccess()
            return
        }
        savingPlace = true
        binding.markupOverlay.editingEnabled = false
        showLoadingOverlay(getString(R.string.saving_annotations))
        lifecycleScope.launch {
            try {
                val editability = withContext(Dispatchers.IO) {
                    try {
                        markupEngine.checkEditability(applicationContext, uri, null)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        null
                    }
                }
                val gate = PlaceMarkupLogic.gate(editability?.isEncrypted, editability?.canMarkup)
                if (gate != PlaceMarkupLogic.Gate.ALLOWED) {
                    showMessage(gateMessage(gate), long = true)
                    return@launch
                }
                val markups: List<PageMarkup> = items.map(PlaceMarkupLogic::toPageMarkup)
                when (val result = markupEngine.applyEdits(
                    context = applicationContext,
                    sourceUri = uri,
                    markups = markups,
                    outputUri = uri,
                    password = null
                )) {
                    is PdfOperationResult.Success -> {
                        reloadViewerDocument()
                        viewModel.clearPending()
                        checkForFormFields(uri)
                        showMessage(getString(R.string.place_saved), long = false)
                        onSuccess()
                    }
                    is PdfOperationResult.Error -> showMessage(result.userMessage, long = true)
                    is PdfOperationResult.Progress -> Unit
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                showMessage(e.localizedMessage ?: getString(R.string.error_title), long = true)
            } finally {
                hideLoadingOverlay()
                savingPlace = false
                binding.markupOverlay.editingEnabled = viewModel.placeModeActive
            }
        }
    }

    private fun showMessage(text: String, long: Boolean) {
        val snackbar = Snackbar.make(binding.root, text, if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
        if (binding.placePanelCard.visibility == View.VISIBLE) snackbar.setAnchorView(binding.placePanelCard)
        snackbar.show()
    }

    private var lastEdges = SystemEdges.NONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * Розкладка панелі «Поставити» без горизонтального прокручування (D1): «Зберегти» й усі кнопки завжди
     * цілком у межах картки. Портрет: 4 рядки (інструменти / колір+скасувати / розмір+готово / зберегти);
     * ландшафт: 2 рядки у картці ≤ 640 dp. Текст кнопок може йти у 2 рядки, але не обрізається.
     */
    private fun applyPlacePanelOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rowA = binding.placeRowA
        val rowB = binding.placeRowB
        val rowC = binding.placeRowC
        val rowD = binding.placeRowD
        val items = listOf<View>(
            binding.placeToolGroup, binding.placeSizeBlock, binding.placeColorGroup,
            binding.btnPlaceUndo, binding.btnPlaceDone, binding.btnPlaceSave
        )
        items.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        listOf(rowA, rowB, rowC, rowD).forEach { it.removeAllViews() }
        binding.placeStash.visibility = View.GONE

        fun wrap(start: Int = 0) = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = start }
        fun weighted(start: Int = 0) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = start }
        val gap = dp(8)

        if (landscape) {
            rowA.addView(binding.placeToolGroup, wrap())
            rowA.addView(binding.placeSizeBlock, wrap(gap * 2))
            rowB.addView(binding.placeColorGroup, wrap())
            rowB.addView(binding.btnPlaceUndo, wrap(gap))
            rowB.addView(binding.btnPlaceDone, weighted(gap))
            rowB.addView(binding.btnPlaceSave, weighted(gap))
        } else {
            rowA.addView(binding.placeToolGroup, wrap())
            rowB.addView(binding.placeColorGroup, wrap())
            rowB.addView(binding.btnPlaceUndo, wrap(gap))
            rowC.addView(binding.placeSizeBlock, wrap())
            rowC.addView(binding.btnPlaceDone, weighted(gap))
            rowD.addView(
                binding.btnPlaceSave,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        updatePlaceSaveVisibility(viewModel.editor.value.items.isNotEmpty())
        updatePlacePanelWidth()
    }

    private fun updatePlacePanelWidth() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val lp = binding.placePanelCard.layoutParams
        val available = resources.displayMetrics.widthPixels - 2 * dp(12) - lastEdges.left - lastEdges.right
        val newWidth = if (landscape) minOf(dp(640), available) else ViewGroup.LayoutParams.MATCH_PARENT
        if (lp.width != newWidth) {
            lp.width = newWidth
            binding.placePanelCard.layoutParams = lp
        }
    }

    private fun updatePlaceSaveVisibility(hasPending: Boolean) {
        binding.btnPlaceSave.visibility = if (hasPending) View.VISIBLE else View.GONE
        binding.placeRowD.visibility =
            if (binding.placeRowD.childCount > 0 && hasPending) View.VISIBLE else View.GONE
        binding.placeRowC.visibility = if (binding.placeRowC.childCount > 0) View.VISIBLE else View.GONE
    }

    /**
     * Edge-to-edge (targetSdk 36): тулбар, нижні панелі й плаваючі картки не заходять під рядок стану,
     * виріз, навігаційні панелі та клавіатуру. Одне джерело істини — слухач на корені.
     */
    private fun applyWindowInsets() {
        val base = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val e = SystemEdges.from(insets)
            lastEdges = e
            binding.root.setPadding(e.left, 0, e.right, 0)
            WindowInsetsHelper.applyToolbarTopInset(binding.toolbar, e.top)
            binding.bottomActionBar.setPadding(0, 0, 0, e.bottom)
            WindowInsetsHelper.applyMargins(binding.placePanelCard, base, base, base, base + e.bottom)
            WindowInsetsHelper.applyMargins(binding.drawingToolbarCard, base, base, base, base + e.bottom)
            updatePlacePanelWidth()
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPlacePanelOrientation()
    }

    /**
     * Єдине місце оновлення переглядача після збереження (R12): копія результату в cacheDir/view
     * з унікальним іменем → завантаження через Uri.fromFile → відновлення сторінки/zoom/скролу.
     * Якщо копія не вдалася, переглядач лишається зі старим вмістом, користувач бачить повідомлення.
     */
    private fun reloadViewerDocument() {
        val source = documentUri ?: return
        val fragment = viewerFragment ?: return
        // Не записувати «сторінку 0» в Room, поки йде відновлення.
        viewModel.progressSuppressed = true
        lifecycleScope.launch {
            val copy = try {
                withContext(Dispatchers.IO) {
                    ViewerCopyCache.copy(cacheDir) { ViewerCopyCache.openSource(contentResolver, source) }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                viewModel.progressSuppressed = false
                val msg = e.localizedMessage ?: getString(R.string.error_title)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            Log.i(TAG, "view copy: ${copy.bytes} bytes in ${copy.elapsedMs} ms (${copy.file.name})")
            fragment.bridge.reload(Uri.fromFile(copy.file)) {
                viewModel.progressSuppressed = false
                // Попередні копії видаляємо лише після того, як нова завантажена (або спроба провалена).
                lifecycleScope.launch(Dispatchers.IO) {
                    ViewerCopyCache.purge(cacheDir, keep = copy.file)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // Переглядач закривається назавжди: поточна копія більше не потрібна.
            Thread { ViewerCopyCache.purge(cacheDir) }.start()
        }
    }

    private suspend fun getPageDimensions(uri: Uri, pageIndex: Int): Pair<Float, Float> = withContext(Dispatchers.IO) {
        PdfBoxPageOperationsEngine.init(applicationContext)
        val inStream = if (uri.scheme == "file") {
            val path = uri.path ?: throw IllegalArgumentException("Некоректний file URI: $uri")
            FileInputStream(File(path))
        } else {
            contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Неможливо відкрити файл для читання розмірів: $uri")
        }

        inStream.use { stream ->
            val doc = PDDocument.load(stream)
            try {
                val totalPages = doc.numberOfPages
                val validIndex = pageIndex.coerceIn(0, maxOf(0, totalPages - 1))
                val page = doc.getPage(validIndex)
                val box = page.cropBox ?: page.mediaBox
                Pair(box.width, box.height)
            } finally {
                doc.close()
            }
        }
    }

    private fun showLoadingOverlay(message: String) {
        binding.savingMessageText.text = message
        binding.savingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoadingOverlay() {
        binding.savingOverlay.visibility = View.GONE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.document.collect { doc ->
                    if (doc != null && !doc.title.isNullOrBlank()) {
                        binding.toolbar.title = doc.title
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isFavorite.collect {
                    invalidateOptionsMenu()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        documentUri?.let { outState.putString(KEY_SAVED_URI, it.toString()) }
        outState.putBoolean(KEY_DRAWING_MODE, isDrawingMode)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val restoredDrawing = savedInstanceState.getBoolean(KEY_DRAWING_MODE, false)
        if (restoredDrawing) {
            toggleDrawingMode(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        val favItem = menu.findItem(R.id.action_toggle_favorite)
        if (viewModel.isFavorite.value) {
            favItem.setIcon(R.drawable.ic_star_filled)
            favItem.setTitle(R.string.action_unfavorite)
        } else {
            favItem.setIcon(R.drawable.ic_star_outline)
            favItem.setTitle(R.string.action_favorite)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isDrawingMode) {
                    toggleDrawingMode(false)
                } else if (viewModel.placeModeActive) {
                    leavePlaceThen { finish() }
                } else {
                    finish()
                }
                true
            }
            R.id.action_draw -> {
                leavePlaceThen { toggleDrawingMode() }
                true
            }
            R.id.action_signature -> {
                if (isDrawingMode) toggleDrawingMode(false)
                leavePlaceThen { openSignatureDialog() }
                true
            }
            R.id.action_place -> {
                if (isDrawingMode) toggleDrawingMode(false)
                enterPlaceMode()
                true
            }
            R.id.action_toggle_favorite -> {
                viewModel.toggleFavorite()
                true
            }
            R.id.action_share -> {
                shareCurrentDocument()
                true
            }
            R.id.action_details -> {
                showDetailsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareCurrentDocument() {
        val uri = documentUri ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    private fun showDetailsDialog() {
        val doc: RecentDocument? = viewModel.document.value
        val uri = documentUri ?: return
        val title = doc?.title ?: documentTitle ?: SafUtils.queryDisplayName(this, uri)
        val pages = doc?.pageCount ?: SafUtils.queryPageCount(this, uri)
        val size = doc?.fileSizeBytes ?: SafUtils.queryFileSize(this, uri)
        val lastOpened = doc?.lastOpenedTimestamp ?: System.currentTimeMillis()

        val msg = StringBuilder()
            .append(getString(R.string.details_title, title)).append("\n\n")
            .append(getString(R.string.details_pages, pages)).append("\n")
            .append(getString(R.string.details_size, SafUtils.formatFileSize(size))).append("\n")
            .append(getString(R.string.details_last_opened, SafUtils.formatTimestamp(lastOpened))).append("\n\n")
            .append(getString(R.string.details_uri, uri.toString()))
            .toString()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_details_title)
            .setMessage(msg)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    companion object {
        const val EXTRA_DOCUMENT_TITLE = "extra_document_title"
        private const val TAG = "ReaderActivity"
        private const val TEXT_VALIDATE_DEBOUNCE_MS = 120L
        private const val TAG_PDF_VIEWER = "tag_pdf_viewer_fragment"
        private const val KEY_SAVED_URI = "key_saved_reader_uri"
        private const val KEY_DRAWING_MODE = "key_drawing_mode_active"

        private const val COLOR_BLACK = 0xFF000000.toInt()
        private const val COLOR_RED = 0xFFD32F2F.toInt()
        private const val COLOR_BLUE = 0xFF1976D2.toInt()
        private const val COLOR_GREEN = 0xFF388E3C.toInt()
        private const val COLOR_YELLOW = 0xFFFBC02D.toInt()
    }
}
