package com.savoj.volumpdf.ui.reader

import android.content.Intent
import android.graphics.Bitmap
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
import com.savoj.volumpdf.core.pdf.FormEngine
import com.savoj.volumpdf.core.pdf.MarkupEngine
import com.savoj.volumpdf.core.pdf.MarkupLayout
import com.savoj.volumpdf.core.pdf.PdfBoxFormEngine
import com.savoj.volumpdf.core.pdf.PdfBoxMarkupEngine
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.databinding.ActivityReaderBinding
import com.savoj.volumpdf.databinding.DialogDocumentPasswordBinding
import com.savoj.volumpdf.databinding.DialogPlaceTextBinding
import com.savoj.volumpdf.ui.common.SystemEdges
import com.savoj.volumpdf.ui.common.WindowInsetsHelper
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()

    private var documentUri: Uri? = null
    private var documentTitle: String? = null
    private var viewerFragment: VolumPdfViewerFragment? = null

    private val formEngine: FormEngine = PdfBoxFormEngine()

    private val markupEngine: MarkupEngine =
        PdfBoxMarkupEngine({ applicationContext.assets.open(MarkupLayout.FONT_ASSET_PATH) })
    private var enteringPlaceMode = false
    private var savingPlace = false

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
            viewModel.viewerOpened = true
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
                    openFillFormDialog()
                    false
                }
                R.id.action_place -> {
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
            viewModel.setFormFields(fields)
            updateFormsActionButton(fields.size)
        }
    }

    private fun updateFormsActionButton(count: Int) {
        binding.btnPlaceForms.visibility = if (count > 0) View.VISIBLE else View.GONE
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

    /**
     * Шторка «Форми» лише редагує незбережені значення. Перед відкриттям — checkEditability: захищені документи
     * (до 3e) — нейтральне повідомлення; canFillForms = false — поля лише для читання з поясненням у шторці.
     */
    private fun openFillFormDialog() {
        val uri = documentUri ?: return
        if (supportFragmentManager.findFragmentByTag(FillFormBottomSheetDialogFragment.TAG) != null) return
        lifecycleScope.launch {
            val access = obtainAccess() ?: return@launch
            when (access) {
                is EditAccess.Open ->
                    FillFormBottomSheetDialogFragment.newInstance(access.canFillForms)
                        .show(supportFragmentManager, FillFormBottomSheetDialogFragment.TAG)
                else -> showMessage(getString(R.string.place_blocked_encrypted), long = true)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Доступ до редагування (етап 3e): checkEditability → EditAccess; пароль лише в пам'яті ViewModel.
    // ---------------------------------------------------------------------------------------------

    private suspend fun queryAccess(password: String?): EditAccess {
        val uri = documentUri ?: return EditAccess.Unreadable
        val editability = withContext(Dispatchers.IO) {
            try {
                markupEngine.checkEditability(applicationContext, uri, password)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                null
            }
        }
        return EditAccessModel.resolve(editability, passwordEntered = password != null, viewerOpened = viewModel.viewerOpened)
    }

    /**
     * Доступ до редагування або null, якщо його нема (користувач скасував пароль чи документ не відкрито; для
     * Unreadable показано повідомлення). Стан «потрібен пароль»: власний діалог із повтором при помилці; успішний
     * пароль живе лише у ViewModel; після нього поля форм читаються знову (з паролем).
     */
    private suspend fun obtainAccess(): EditAccess? {
        var access = queryAccess(viewModel.documentPassword)
        while (EditAccessModel.needsPassword(access)) {
            val entered = askPassword(showError = access === EditAccess.WrongPassword) ?: return null
            val next = queryAccess(entered)
            if (next !== EditAccess.WrongPassword) {
                viewModel.documentPassword = entered
                if (next is EditAccess.Restricted || next is EditAccess.Open) documentUri?.let { checkForFormFields(it) }
            }
            access = next
        }
        if (access === EditAccess.Unreadable) {
            showMessage(getString(R.string.place_blocked_unreadable), long = true)
            return null
        }
        return access
    }

    /** Діалог пароля (textPassword). null — скасовано. Пароль нікуди не пишеться й не логується. */
    private suspend fun askPassword(showError: Boolean): String? = suspendCancellableCoroutine { cont ->
        val dialogBinding = DialogDocumentPasswordBinding.inflate(layoutInflater)
        if (showError) dialogBinding.passwordInputLayout.error = getString(R.string.password_wrong)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.password_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.password_continue, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        var answered = false
        fun answer(value: String?) {
            if (answered) return
            answered = true
            if (cont.isActive) cont.resume(value)
        }
        dialog.setOnShowListener {
            val min = dp(48)
            listOf(dialog.getButton(AlertDialog.BUTTON_POSITIVE), dialog.getButton(AlertDialog.BUTTON_NEGATIVE)).forEach {
                it.minHeight = min; it.minimumHeight = min; it.minWidth = min; it.minimumWidth = min
            }
            val input = dialogBinding.passwordInput
            input.requestFocus()
            input.post {
                dialog.window?.let { w -> WindowCompat.getInsetsController(w, input).show(WindowInsetsCompat.Type.ime()) }
            }
            fun submit() {
                val text = input.text?.toString().orEmpty()
                if (text.isEmpty()) return
                answer(text)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            input.setOnEditorActionListener { _, _, _ -> submit(); true }
        }
        dialog.setOnDismissListener { answer(null) }
        cont.invokeOnCancellation { if (dialog.isShowing) dialog.dismiss() }
        dialog.show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.placeModeActive) {
                    leavePlaceThen { }
                } else if (viewModel.hasUnsaved.value) {
                    showUnsavedDialog {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /** Полотно підпису: результат — лише картинка для розміщення (запису в документ немає). */
    private fun openSignatureDialog() {
        val dialog = (supportFragmentManager.findFragmentByTag(SignatureBottomSheetDialogFragment.TAG)
            as? SignatureBottomSheetDialogFragment) ?: SignatureBottomSheetDialogFragment.newInstance().also {
            it.show(supportFragmentManager, SignatureBottomSheetDialogFragment.TAG)
        }
        dialog.onSignatureConfirmed = { bitmap -> armSignature(bitmap) }
    }

    private fun armSignature(bitmap: Bitmap) {
        viewModel.armedSignature = bitmap
        viewModel.placeTool = PlaceTool.SIGNATURE
        if (viewModel.placeModeActive) {
            selectToolButton(PlaceTool.SIGNATURE)
            dismissSignatureHint()
            signatureHint = showMessage(getString(R.string.place_signature_tap_hint), long = false, indefinite = true)
        } else {
            enterPlaceMode()
        }
    }

    private fun selectToolButton(tool: PlaceTool) {
        syncingPanel = true
        try {
            binding.placeToolGroup.check(toolButtons.getValue(tool))
        } finally {
            syncingPanel = false
        }
        renderEditor(viewModel.editor.value)
    }

    // ---------------------------------------------------------------------------------------------
    // Режим «Поставити» (етап 3a): ✓, ✗, текст; незбережене живе у ViewModel; одне «Зберегти».
    // ---------------------------------------------------------------------------------------------

    private val toolButtons by lazy {
        mapOf(
            PlaceTool.CHECK to R.id.btnToolCheck,
            PlaceTool.CROSS to R.id.btnToolCross,
            PlaceTool.TEXT to R.id.btnToolText,
            PlaceTool.INK to R.id.btnToolInk,
            PlaceTool.SIGNATURE to R.id.btnToolSignature,
            PlaceTool.HAND to R.id.btnToolHand
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

            override fun onInkDrawn(pageIndex: Int, xy: FloatArray) {
                if (savingPlace || viewerFragment?.bridge?.isRestoring == true) return
                val page = viewerFragment?.bridge?.pageSizePt(pageIndex)
                val stroke = PlaceMarkupLogic.inkFromStroke(
                    UUID.randomUUID().toString(), pageIndex, xy,
                    viewModel.placeSize(PlaceTool.INK), viewModel.newInkColor(), page
                ) ?: return // усі точки поза сторінкою або штрих порожній
                viewModel.addPending(stroke)
            }
        }

        binding.placeToolGroup.check(toolButtons.getValue(viewModel.placeTool))
        val colorId = colorButtons.entries.firstOrNull { it.value == viewModel.placeColor }?.key ?: R.id.btnPlaceColorRed
        binding.placeColorGroup.check(colorId)
        updateSizeLabel()
        applyPlacePanelOrientation()

        binding.placeToolGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !syncingPanel) {
                val tool = toolButtons.entries.firstOrNull { it.value == checkedId }?.key ?: return@addOnButtonCheckedListener
                viewModel.placeTool = tool
                if (tool != PlaceTool.SIGNATURE) dismissSignatureHint()
                // Поки малюємо, виділення не потрібне: кожен дотик — штрих.
                if (tool == PlaceTool.INK) viewModel.edit { PlaceEditor.select(it, null) }
                renderEditor(viewModel.editor.value)
            }
        }
        binding.btnToolSignature.setOnClickListener {
            if (viewModel.armedSignature == null) openSignatureDialog()
        }
        binding.placeSignatureSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || syncingPanel) return@addOnChangeListener
            val sel = viewModel.editor.value.selected ?: return@addOnChangeListener
            if (sel.tool == PlaceTool.SIGNATURE) viewModel.edit { PlaceEditor.setSize(it, sel.id, value) }
        }
        binding.markupOverlay.signatureBitmaps = { id -> viewModel.signatureBitmaps[id] }
        binding.placeInkPresetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingPanel) return@addOnButtonCheckedListener
            val preset = when (checkedId) {
                R.id.btnInkMarker -> InkPreset.MARKER
                R.id.btnInkHighlighter -> InkPreset.HIGHLIGHTER
                else -> InkPreset.PEN
            }
            val selected = viewModel.editor.value.selected
            if (selected != null && selected.tool == PlaceTool.INK) {
                viewModel.edit { PlaceEditor.setInkStyle(it, selected.id, preset.widthPt, preset.alpha) }
            } else {
                viewModel.inkPreset = preset
                viewModel.setPlaceSize(PlaceTool.INK, preset.widthPt)
                renderEditor(viewModel.editor.value)
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
        binding.btnPlaceForms.setOnClickListener { openFillFormDialog() }
        binding.btnUnsavedSave.setOnClickListener { savePending() }
        binding.btnUnsavedDiscard.setOnClickListener { viewModel.clearAllPending() }
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasUnsaved.collect { renderUnsavedUi() }
            }
        }

        // Відновлення (поворот, завершення процесу, новий запуск): режим і незбережене повертаються з файлу.
        lifecycleScope.launch {
            viewModel.pendingLoaded.first { it }
            if (viewModel.placeModeActive || viewModel.editor.value.items.isNotEmpty()) enterPlaceMode()
        }
    }

    /** «Зберегти» у панелі режиму і панель «Зберегти / Відкинути» поза режимом: показуються при будь-яких незбережених змінах. */
    private fun renderUnsavedUi() {
        val unsaved = viewModel.hasUnsaved.value
        binding.btnPlaceSave.visibility = if (unsaved) View.VISIBLE else View.GONE
        binding.unsavedBar.visibility = if (unsaved && !viewModel.placeModeActive) View.VISIBLE else View.GONE
    }

    /** Малює стан редактора: оверлей, кнопки «Зберегти»/«Скасувати», панель дій виділеного, параметри за типом цілі. */
    private fun renderEditor(state: PlaceEditorState) {
        binding.markupOverlay.items = state.items
        binding.markupOverlay.selectedId = state.selectedId
        renderUnsavedUi()
        binding.btnPlaceUndo.isEnabled = state.canUndo

        val sel = state.selected
        val tool = viewModel.placeTool
        binding.markupOverlay.drawingEnabled = viewModel.placeModeActive && tool == PlaceTool.INK
        binding.markupOverlay.inkColor = viewModel.newInkColor()
        binding.markupOverlay.inkWidthPt = viewModel.placeSize(PlaceTool.INK)

        binding.selectionBarCard.visibility =
            if (sel != null && viewModel.placeModeActive) View.VISIBLE else View.GONE
        if (sel != null) {
            val editLabel = when (sel.tool) {
                PlaceTool.TEXT -> R.string.place_edit_text
                PlaceTool.CHECK -> R.string.place_change_to_cross
                PlaceTool.CROSS -> R.string.place_change_to_check
                else -> null
            }
            binding.btnSelEdit.visibility = if (editLabel == null) View.GONE else View.VISIBLE
            if (editLabel != null) binding.btnSelEdit.setText(editLabel)
        }

        // Параметри залежать від цілі: виділений об'єкт, інакше поточний інструмент.
        val target = sel?.tool ?: tool
        val hasColor = target == PlaceTool.CHECK || target == PlaceTool.CROSS || target == PlaceTool.TEXT || target == PlaceTool.INK
        binding.placeColorGroup.visibility = if (hasColor) View.VISIBLE else View.GONE
        binding.placeSizeBlock.visibility = if (hasColor) View.VISIBLE else View.GONE
        binding.placeInkPresetGroup.visibility = if (target == PlaceTool.INK) View.VISIBLE else View.GONE
        val hasSignature = sel?.tool == PlaceTool.SIGNATURE
        binding.placeSignatureBlock.visibility = if (hasSignature) View.VISIBLE else View.GONE
        binding.placeOptionsScroll.visibility = if (hasColor || hasSignature) View.VISIBLE else View.GONE

        syncingPanel = true
        try {
            val color = (sel?.color ?: viewModel.placeColor) or PlaceMarkupLogic.ALPHA_MASK
            colorButtons.entries.firstOrNull { it.value == color }?.key?.let { binding.placeColorGroup.check(it) }
            val preset = if (sel?.tool == PlaceTool.INK) presetOf(sel) else viewModel.inkPreset
            binding.placeInkPresetGroup.clearChecked()
            when (preset) {
                InkPreset.PEN -> binding.placeInkPresetGroup.check(R.id.btnInkPen)
                InkPreset.MARKER -> binding.placeInkPresetGroup.check(R.id.btnInkMarker)
                InkPreset.HIGHLIGHTER -> binding.placeInkPresetGroup.check(R.id.btnInkHighlighter)
                null -> Unit
            }
            updateSizeLabel()
            if (sel != null && sel.tool == PlaceTool.SIGNATURE) {
                binding.placeSignatureSlider.value = sel.sizePt.coerceIn(
                    PlaceMarkupLogic.MIN_SIGNATURE_WIDTH_PT, PlaceMarkupLogic.MAX_SIGNATURE_WIDTH_PT
                )
                binding.tvSignatureWidth.text = getString(R.string.place_size_format, PlaceMarkupLogic.formatSize(sel.sizePt))
            }
        } finally {
            syncingPanel = false
        }
    }

    /** Пресет, якому відповідає виділений штрих (за альфою й товщиною); null — власна товщина. */
    private fun presetOf(stroke: PendingMarkup): InkPreset? = when {
        (stroke.color ushr 24) < 0xFF -> InkPreset.HIGHLIGHTER
        stroke.sizePt == InkPreset.PEN.widthPt -> InkPreset.PEN
        stroke.sizePt == InkPreset.MARKER.widthPt -> InkPreset.MARKER
        else -> null
    }

    /** «Змінити»: текст — повторне введення, ✓/✗ — перемикання виду. */
    private fun editSelected() {
        val sel = viewModel.editor.value.selected ?: return
        when (sel.tool) {
            PlaceTool.TEXT -> showTextDialog(
                initial = sel.text, titleRes = R.string.place_edit_text, confirmRes = R.string.place_text_apply
            ) { raw -> viewModel.edit { PlaceEditor.setText(it, sel.id, raw) } }
            PlaceTool.CHECK, PlaceTool.CROSS -> viewModel.edit { PlaceEditor.toggleMarkKind(it, sel.id) }
            else -> Unit
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
        binding.tvPlaceSize.text = getString(R.string.place_size_format, PlaceMarkupLogic.formatSize(size))
    }

    /** Перевірка прав → показ панелі й увімкнення тапу в мості. Для захищених/заборонених — лише пояснення. */
    private fun enterPlaceMode() {
        if (enteringPlaceMode) return
        if (viewModel.placeModeActive && binding.placePanelCard.visibility == View.VISIBLE) return
        documentUri ?: return
        enteringPlaceMode = true
        lifecycleScope.launch {
            try {
                val access = obtainAccess()
                if (access == null) {
                    // Пароль скасовано або документ не відкрито: режим не вмикаємо, незбережене НЕ чіпаємо
                    // (воно лишається на панелі «Зберегти / Відкинути» й у сховищі).
                    viewModel.placeModeActive = false
                    return@launch
                }
                if (access !is EditAccess.Open) {
                    viewModel.clearPending()
                    viewModel.placeModeActive = false
                    showMessage(getString(R.string.place_blocked_encrypted), long = true)
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
        binding.unsavedBar.visibility = View.GONE
        viewerFragment?.bridge?.setPlaceMode(true) { point -> onPageTapped(point) }
        binding.markupOverlay.editingEnabled = !savingPlace
        renderEditor(viewModel.editor.value)
    }

    private fun hidePlaceUi() {
        dismissSignatureHint()
        viewModel.placeModeActive = false
        binding.markupOverlay.editingEnabled = false
        viewModel.clearPending() // виділення й історія скасування не переживають вихід із режиму
        binding.selectionBarCard.visibility = View.GONE
        viewerFragment?.bridge?.setPlaceMode(false)
        binding.placePanelCard.visibility = View.GONE
        binding.bottomActionBar.visibility = View.VISIBLE
        renderUnsavedUi()
    }

    /**
     * Виходить із режиму «Поставити» й виконує [action]. Якщо в режимі є незбережені об'єкти — діалог
     * «Зберегти / Відкинути / Скасувати» (він враховує й зміни форм). Лише зміни форм виходу з режиму не блокують:
     * вони лишаються в незбереженому і видимі на панелі «Зберегти / Відкинути».
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
        showUnsavedDialog(action)
    }

    /** Вихід із читача (кнопка «назад» у тулбарі): будь-які незбережені зміни — об'єкти чи форми — запитують. */
    private fun exitReaderThen(action: () -> Unit) {
        if (viewModel.hasUnsaved.value) {
            showUnsavedDialog(action)
        } else {
            if (viewModel.placeModeActive) hidePlaceUi()
            action()
        }
    }

    private fun showUnsavedDialog(action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.place_unsaved_title)
            .setMessage(R.string.place_unsaved_message)
            .setPositiveButton(R.string.action_save) { _, _ ->
                savePending(onSuccess = {
                    if (viewModel.placeModeActive) hidePlaceUi()
                    action()
                })
            }
            .setNegativeButton(R.string.place_discard) { _, _ ->
                viewModel.clearAllPending()
                if (viewModel.placeModeActive) hidePlaceUi()
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
            PlaceTool.SIGNATURE -> placeArmedSignature(point)
            PlaceTool.INK, PlaceTool.HAND -> Unit // штрих малює оверлей; «рука» нічого не ставить
        }
    }

    /** Тап із готовим підписом: лівий верхній кут у точці тапу, ширина 120 pt, пропорції збережено. */
    private fun placeArmedSignature(point: PageViewPoint) {
        val bitmap = viewModel.armedSignature
        if (bitmap == null) {
            openSignatureDialog()
            return
        }
        val page = viewerFragment?.bridge?.pageSizePt(point.pageIndex)
        val item = PlaceMarkupLogic.signatureFromTap(
            UUID.randomUUID().toString(), point.pageIndex, point.x, point.y,
            bitmap.width, bitmap.height, PlaceMarkupLogic.DEFAULT_SIGNATURE_WIDTH_PT, page
        ) ?: return
        viewModel.signatureBitmaps[item.id] = bitmap
        dismissSignatureHint() // підказка зникає за станом: підпис розміщено
        viewModel.armedSignature = null
        viewModel.addPending(item)
        // Далі підпис можна рухати й міняти: «рука» + виділення.
        viewModel.placeTool = PlaceTool.HAND
        viewModel.edit { PlaceEditor.select(it, item.id) }
        selectToolButton(PlaceTool.HAND)
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
     * Єдине збереження: checkEditability → ОДИН applyEdits (той самий URI; formUpdates зі змін форм) → reload.
     * Після успіху незбережене (об'єкти, форми, файл у pending) очищується, поля читаються знову; при Error усе
     * лишається, повідомлення містить userMessage (для Choice — назву поля). Реклама в читачі не показується (R4).
     */
    private fun savePending(onSuccess: () -> Unit = {}) {
        if (savingPlace) return
        val uri = documentUri ?: return
        val items = viewModel.editor.value.items
        val request = SaveLogic.request(
            items.map { PlaceMarkupLogic.toPageMarkup(it) { id -> viewModel.signatureBitmaps[id] } },
            viewModel.formFields.value, viewModel.formEdits.value
        )
        if (request.markups.isEmpty() && request.formUpdates.isEmpty()) {
            // Лише записи без поля у файлі (застарілі) — зберігати нічого.
            if (viewModel.hasUnsaved.value) viewModel.clearAllPending()
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
                val gate = SaveLogic.gate(
                    editability?.isEncrypted, editability?.canMarkup, editability?.canFillForms,
                    hasItems = request.markups.isNotEmpty(), hasForms = request.formUpdates.isNotEmpty()
                )
                if (gate != PlaceMarkupLogic.Gate.ALLOWED) {
                    showMessage(gateMessage(gate), long = true)
                    return@launch
                }
                val outcome = SaveLogic.run(request) { markups, formUpdates ->
                    markupEngine.applyEdits(
                        context = applicationContext,
                        sourceUri = uri,
                        markups = markups,
                        formUpdates = formUpdates,
                        outputUri = uri,
                        password = null
                    )
                }
                when (outcome) {
                    SaveOutcome.Saved -> {
                        reloadViewerDocument()
                        viewModel.clearAllPending()
                        checkForFormFields(uri) // поля читаємо знову, бейдж оновлюється
                        showMessage(getString(R.string.place_saved), long = false)
                        onSuccess()
                    }
                    is SaveOutcome.Failed -> showError(outcome.message.ifBlank { getString(R.string.error_title) })
                    SaveOutcome.NothingToSave -> onSuccess()
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                showError(e.localizedMessage ?: getString(R.string.error_title))
            } finally {
                hideLoadingOverlay()
                savingPlace = false
                binding.markupOverlay.editingEnabled = viewModel.placeModeActive
            }
        }
    }

    private var signatureHint: Snackbar? = null

    private fun dismissSignatureHint() {
        signatureHint?.dismiss()
        signatureHint = null
    }

    private var errorSnackbar: Snackbar? = null

    /** L7: повідомлення про помилку лишається, доки користувач не закриє («ОК»); успіх — короткий [showMessage]. */
    private fun showError(text: String): Snackbar {
        errorSnackbar?.dismiss()
        val snackbar = Snackbar.make(binding.root, text, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.action_ok) { }
        snackbar.view.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = 6
        if (binding.placePanelCard.visibility == View.VISIBLE) snackbar.setAnchorView(binding.placePanelCard)
        snackbar.show()
        errorSnackbar = snackbar
        return snackbar
    }

    private fun showMessage(text: String, long: Boolean, indefinite: Boolean = false): Snackbar {
        val snackbar = Snackbar.make(binding.root, text, if (indefinite) Snackbar.LENGTH_INDEFINITE else if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
        if (binding.placePanelCard.visibility == View.VISIBLE) snackbar.setAnchorView(binding.placePanelCard)
        snackbar.show()
        return snackbar
    }

    private var lastEdges = SystemEdges.NONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * Розкладка панелі «Поставити»: смуга інструментів і смуга параметрів прокручуються по горизонталі, а рядок
     * «Скасувати / Готово / Зберегти» завжди видимий. Портрет: рядок дій — окремий рядок знизу; ландшафт: він
     * переноситься в кінець верхнього рядка (картка ≤ 640 dp, щоб не займати пів екрана по висоті).
     */
    private fun applyPlacePanelOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val actions = binding.placeActionsRow
        val target: ViewGroup = if (landscape) binding.placeTopRow else binding.placePanelContent
        if (actions.parent !== target) {
            (actions.parent as? ViewGroup)?.removeView(actions)
            target.addView(
                actions,
                if (landscape) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(8) }
                else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(4) }
            )
        }
        // У ландшафті рядок дій має природну ширину, у портреті «Готово» й «Зберегти» ділять рядок порівну.
        listOf(binding.btnPlaceDone, binding.btnPlaceSave).forEach { b ->
            val lp = b.layoutParams as LinearLayout.LayoutParams
            lp.width = if (landscape) ViewGroup.LayoutParams.WRAP_CONTENT else 0
            lp.weight = if (landscape) 0f else 1f
            b.layoutParams = lp
        }
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
                // M2: «вгору» — той самий вихід, що й «Назад»: за незбережених змін (об'єкти чи форми) діалог.
                exitReaderThen { finish() }
                true
            }
            R.id.action_place -> {
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

        private const val COLOR_BLACK = 0xFF000000.toInt()
        private const val COLOR_RED = 0xFFD32F2F.toInt()
        private const val COLOR_BLUE = 0xFF1976D2.toInt()
        private const val COLOR_GREEN = 0xFF388E3C.toInt()
        private const val COLOR_YELLOW = 0xFFFBC02D.toInt()
    }
}
