package com.savoj.volumpdf.ui.reader

import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.savoj.volumpdf.R
import com.savoj.volumpdf.ui.scanner.DocumentScannerLogic
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
import com.savoj.volumpdf.util.ImageRotationHelper
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var initialPageRestored = false
    private var loadedPageCount = 0
    private lateinit var readingPreferences: ReadingPreferences
    private val progressViewportListener: (Viewport) -> Unit = { v ->
        viewModel.updatePageProgress(v.currentPage)
        updateReaderHud(v.currentPage, v.zoom)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastConfig = Configuration(resources.configuration)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applyWindowInsets()

        // Після відновлення (поворот, завершення процесу) — URI поточного документа (це може бути збережена копія).
        val uri = savedInstanceState?.getString(KEY_SAVED_URI)?.let { Uri.parse(it) } ?: intent.data
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

        readingPreferences = ReadingPreferences(this)
        applyReadingTheme(readingPreferences.theme)

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

        fragment.bridge.removeViewportListener(progressViewportListener)
        fragment.bridge.addViewportListener(progressViewportListener)

        fragment.onDocumentLoadedListener = { count ->
            loadedPageCount = count
            viewModel.viewerOpened = true
            viewModel.updatePageCount(count)
            val curPage = fragment.bridge.viewport?.currentPage ?: 0
            val curZoom = fragment.bridge.zoom
            updateReaderHud(curPage, curZoom)
            maybeRestoreInitialPage()
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

    /**
     * Відновлює останню прочитану сторінку при відкритті документа, коли і переглядач завантажив PDF,
     * і ViewModel отримав запис із Room. До завершення відновлення `progressSuppressed = true`, щоб
     * стартові події зі сторінкою 0 не затерли збережений прогрес.
     */
    private fun maybeRestoreInitialPage() {
        if (initialPageRestored) return
        if (!viewModel.viewerOpened) return
        val doc = viewModel.document.value ?: return
        if (doc.uri != documentUri?.toString()) return
        val fragment = viewerFragment ?: return

        initialPageRestored = true
        val pageCount = if (loadedPageCount > 0) loadedPageCount else doc.pageCount
        val targetPage = ReadingProgressLogic.targetRestorePage(doc.lastPageRead, pageCount)
        if (targetPage <= 0) {
            viewModel.progressSuppressed = false
            if (doc.lastPageRead != 0) {
                viewModel.updatePageProgress(0)
            }
            return
        }
        viewModel.progressSuppressed = true
        fragment.bridge.restorePage(targetPage) {
            viewModel.progressSuppressed = false
            val actualPage = fragment.bridge.viewport?.currentPage ?: targetPage
            if (actualPage != doc.lastPageRead) {
                viewModel.updatePageProgress(actualPage)
            }
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
                R.id.action_search -> {
                    toggleSearch()
                    false
                }
                R.id.action_reading_theme -> {
                    showReadingThemeDialog()
                    false
                }
                R.id.action_tools -> {
                    showToolsBottomSheet()
                    false
                }
                R.id.action_organize_pages -> {
                    val uri = documentUri ?: return@setOnItemSelectedListener false
                    com.savoj.volumpdf.ui.organizer.PageOrganizerActivity.start(this, uri)
                    false
                }
                else -> false
            }
        }

        binding.btnHudPage.setOnClickListener {
            showJumpToPageDialog()
        }
        binding.btnHudZoom.setOnClickListener {
            val nextZoom = viewerFragment?.bridge?.cycleOrResetZoom() ?: 1f
            val curPage = viewerFragment?.bridge?.viewport?.currentPage ?: 0
            updateReaderHud(curPage, nextZoom)
        }
        binding.btnHudSearch.setOnClickListener {
            toggleSearch()
        }
    }

    private fun toggleSearch() {
        val bridge = viewerFragment?.bridge ?: return
        val next = !bridge.isTextSearchActive
        bridge.isTextSearchActive = next
        if (!viewModel.placeModeActive) {
            binding.cardReaderHud.visibility = if (next) View.GONE else View.VISIBLE
        }
    }

    private fun updateReaderHud(currentPageZeroBased: Int, zoom: Float) {
        val total = if (loadedPageCount > 0) loadedPageCount else (viewModel.document.value?.pageCount ?: 0)
        if (total > 0) {
            val cur1Based = (currentPageZeroBased + 1).coerceIn(1, total)
            binding.btnHudPage.text = getString(R.string.document_page_progress, cur1Based, total)
        } else {
            binding.btnHudPage.text = "${currentPageZeroBased + 1}"
        }
        val zoomPct = (zoom * 100f).toInt().coerceIn(25, 500)
        binding.btnHudZoom.text = "$zoomPct%"
    }

    private fun showJumpToPageDialog() {
        val total = if (loadedPageCount > 0) loadedPageCount else (viewModel.document.value?.pageCount ?: 0)
        if (total <= 1) return
        val cur1Based = ((viewerFragment?.bridge?.viewport?.currentPage ?: 0) + 1).coerceIn(1, total)

        val pad = dp(20)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, 0)
        }
        val til = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.dialog_jump_to_page_hint, total)
        }
        val et = com.google.android.material.textfield.TextInputEditText(til.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(cur1Based.toString())
            setSelection(text?.length ?: 0)
        }
        til.addView(et)
        container.addView(til)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_jump_to_page_title)
            .setView(container)
            .setPositiveButton(R.string.action_go) { _, _ ->
                val target1Based = et.text?.toString()?.trim()?.toIntOrNull()
                if (target1Based != null) {
                    val target0Based = (target1Based - 1).coerceIn(0, total - 1)
                    viewerFragment?.bridge?.restorePage(target0Based) {
                        viewModel.updatePageProgress(target0Based)
                        updateReaderHud(target0Based, viewerFragment?.bridge?.zoom ?: 1f)
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .showTracked()
    }

    private fun checkForFormFields(uri: Uri) {
        lifecycleScope.launch {
            val fields = withContext(Dispatchers.IO) {
                try {
                    formEngine.extractFormFields(applicationContext, uri, viewModel.documentPassword)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }
            viewModel.setFormFields(fields)
            updateFormsActionButton(fields.size)
        }
    }

    /** Кількість полів форм: «Форми» у верхньому меню (⋮) показується, лише якщо поля є. */
    private var formFieldCount = 0

    private fun updateFormsActionButton(count: Int) {
        formFieldCount = count
        invalidateOptionsMenu()
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
            // L1: права автора керують окремо — без права заповнення шторка лише для читання з поясненням.
            FillFormBottomSheetDialogFragment.newInstance(EditAccessModel.formsMode(access) == EditAccessModel.FormsMode.EDITABLE)
                .show(supportFragmentManager, FillFormBottomSheetDialogFragment.TAG)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Доступ до редагування (етап 3e): checkEditability → EditAccess; пароль лише в пам'яті ViewModel.
    // ---------------------------------------------------------------------------------------------

    private suspend fun queryAccess(password: String?, assumeViewerOpened: Boolean = false): EditAccess {
        val uri = documentUri ?: return EditAccess.Unreadable
        val editability = withContext(Dispatchers.IO) {
            try {
                markupEngine.checkEditability(applicationContext, uri, password)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                null
            }
        }
        return EditAccessModel.resolve(editability, passwordEntered = password != null, viewerOpened = viewModel.viewerOpened || assumeViewerOpened)
    }

    /**
     * Доступ до редагування або null, якщо його нема (користувач скасував пароль чи документ не відкрито; для
     * Unreadable показано повідомлення). Стан «потрібен пароль»: власний діалог із повтором при помилці; успішний
     * пароль живе лише у ViewModel; після нього поля форм читаються знову (з паролем).
     */
    private suspend fun obtainAccess(assumeViewerOpened: Boolean = false): EditAccess? {
        var access = queryAccess(viewModel.documentPassword, assumeViewerOpened)
        while (EditAccessModel.needsPassword(access)) {
            val entered = askPassword(showError = access === EditAccess.WrongPassword) ?: return null
            val next = queryAccess(entered, assumeViewerOpened)
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
        trackDialog(dialog)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val bridge = viewerFragment?.bridge
                if (bridge != null && bridge.isTextSearchActive) {
                    bridge.isTextSearchActive = false
                    if (!viewModel.placeModeActive) binding.cardReaderHud.visibility = View.VISIBLE
                    return
                }
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

    /** Інструмент уже записано у ViewModel: рядок і картка перемальовуються зі стану. */
    private fun selectToolButton(tool: PlaceTool) {
        check(viewModel.placeTool == tool)
        renderEditor(viewModel.editor.value)
    }

    // ---------------------------------------------------------------------------------------------
    // Режим «Поставити» (етап 3a): ✓, ✗, текст; незбережене живе у ViewModel; одне «Зберегти».
    // ---------------------------------------------------------------------------------------------

    private lateinit var placeBar: PlaceBarController

    private val placeBarHost = object : PlaceBarController.Host {
        override fun chooseTool(tool: PlaceTool) {
            viewModel.placeTool = tool
            if (tool != PlaceTool.SIGNATURE) dismissSignatureHint()
            // Поки малюємо, виділення не потрібне: кожен дотик — штрих.
            if (tool == PlaceTool.INK) viewModel.edit { PlaceEditor.select(it, null) }
            renderEditor(viewModel.editor.value)
        }

        override fun openSignature() {
            if (viewModel.armedSignature == null) openSignatureDialog()
        }

        override fun pickImage() {
            pickImageForReaderLauncher.launch(arrayOf("image/*"))
        }

        override fun applySize(value: Float) = applyPlaceSize(value)

        override fun applyColor(rgb: Int) {
            val selectedId = viewModel.editor.value.selectedId
            if (selectedId != null) viewModel.edit { PlaceEditor.setColor(it, selectedId, rgb) }
            else viewModel.placeColor = rgb
            renderEditor(viewModel.editor.value)
        }

        override fun applyPreset(preset: InkPreset) {
            val selected = viewModel.editor.value.selected
            if (selected != null && selected.tool == PlaceTool.INK) {
                viewModel.edit { PlaceEditor.setInkStyle(it, selected.id, preset.widthPt, preset.alpha) }
            } else {
                viewModel.inkPreset = preset
                viewModel.setPlaceSize(PlaceTool.INK, preset.widthPt)
                renderEditor(viewModel.editor.value)
            }
        }

        override fun rotateImage(deltaDeg: Int) = rotateSelectedImage(deltaDeg)

        override fun fitImage() = fitSelectedImageToPage()

        override fun deleteSelected() {
            viewModel.editor.value.selectedId?.let { id -> viewModel.edit { PlaceEditor.delete(it, id) } }
        }

        override fun pickCustomColor(currentRgb: Int, onPicked: (Int) -> Unit) = showCustomColorDialog(currentRgb, onPicked)

        override fun refresh() = renderEditor(viewModel.editor.value)
    }

    private fun setupPlaceMode() {
        val fragment = viewerFragment
        binding.markupOverlay.bridge = fragment?.bridge
        binding.markupOverlay.pageCountProvider = { loadedPageCount }
        binding.markupOverlay.editListener = object : MarkupOverlayView.EditListener {
            override fun onItemTapped(id: String) = viewModel.edit { PlaceEditor.select(it, id) }

            override fun onItemMoved(id: String, x: Float, y: Float) {
                val pageIndex = viewModel.editor.value.items.firstOrNull { it.id == id }?.pageIndex ?: return
                val page = viewerFragment?.bridge?.pageSizePt(pageIndex)
                viewModel.edit { PlaceEditor.move(PlaceEditor.select(it, id), id, x, y, page) }
            }

            override fun onItemMoved(id: String, pageIndex: Int, x: Float, y: Float) {
                val page = viewerFragment?.bridge?.pageSizePt(pageIndex)
                viewModel.edit { PlaceEditor.move(PlaceEditor.select(it, id), id, pageIndex, x, y, page) }
            }

            override fun onItemMovedBelowDocument(id: String, x: Float, y: Float) {
                addNewBlankPage { success ->
                    if (success) {
                        val newPageIndex = (loadedPageCount - 1).coerceAtLeast(0)
                        val page = viewerFragment?.bridge?.pageSizePt(newPageIndex)
                        viewModel.edit {
                            PlaceEditor.move(PlaceEditor.select(it, id), id, newPageIndex, x.coerceAtLeast(36f), 40f, page)
                        }
                        showMessage(getString(R.string.page_added_and_moved), long = false)
                    }
                }
            }

            override fun onItemSizeChanged(id: String, newSizePt: Float) {
                val sel = viewModel.editor.value.items.firstOrNull { it.id == id } ?: return
                val page = viewerFragment?.bridge?.pageSizePt(sel.pageIndex)
                viewModel.edit { PlaceEditor.setSize(it, id, newSizePt, page) }
            }

            override fun onItemSizeAndPositionChanged(id: String, newSizePt: Float, x: Float, y: Float) {
                val sel = viewModel.editor.value.items.firstOrNull { it.id == id } ?: return
                val page = viewerFragment?.bridge?.pageSizePt(sel.pageIndex)
                viewModel.edit { PlaceEditor.setSizeAndPosition(it, id, newSizePt, x, y, page) }
            }

            override fun onItemDragSnappedBack(id: String) {
                showMessage(getString(R.string.place_drag_outside_snapped_back), long = false)
            }

            override fun onInkDrawn(pageIndex: Int, xy: FloatArray) {
                if (savingPlace || viewerFragment?.bridge?.isRestoring == true) return
                val page = viewerFragment?.bridge?.pageSizePt(pageIndex)
                val points = if (viewModel.straightenInk) InkStraighten.straighten(xy) else xy
                val stroke = PlaceMarkupLogic.inkFromStroke(
                    UUID.randomUUID().toString(), pageIndex, points,
                    viewModel.placeSize(PlaceTool.INK), viewModel.newInkColor(), page
                ) ?: return // усі точки поза сторінкою або штрих порожній
                viewModel.addPending(stroke)
            }

            override fun onItemTextChanged(id: String, newText: String, rawText: String?) {
                viewModel.edit { PlaceEditor.setText(it, id, newText, rawText) }
            }
        }

        placeBar = PlaceBarController(binding, viewModel, placeBarHost)
        applyPlacePanelOrientation()

        binding.btnSelMovePage.setOnClickListener {
            val sel = viewModel.editor.value.selected ?: return@setOnClickListener
            showMoveToPageDialog(sel)
        }
        binding.markupOverlay.signatureBitmaps = { id -> viewModel.signatureBitmaps[id] }
        binding.btnUnsavedSave.setOnClickListener { savePending() }
        binding.btnUnsavedDiscard.setOnClickListener { viewModel.clearAllPending() }
        binding.btnSelDelete.setOnClickListener { placeBarHost.deleteSelected() }
        binding.btnSelEdit.setOnClickListener { editSelected() }
        binding.btnSelRotateLeft.setOnClickListener { rotateSelectedImage(-90) }
        binding.btnSelRotateRight.setOnClickListener { rotateSelectedImage(90) }
        binding.btnSelFitPage.setOnClickListener { fitSelectedImageToPage() }

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
        binding.unsavedBar.visibility = if (unsaved && !viewModel.placeModeActive) View.VISIBLE else View.GONE
        invalidateOptionsMenu() // «Зберегти» у верхній панелі показується лише за незбережених змін
    }

    /** Малює стан редактора: оверлей, кнопки «Зберегти»/«Скасувати», панель дій виділеного, параметри за типом цілі. */
    private fun renderEditor(state: PlaceEditorState) {
        binding.markupOverlay.items = state.items
        binding.markupOverlay.selectedId = state.selectedId
        renderUnsavedUi()

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
            binding.btnSelMovePage.visibility = View.VISIBLE
        }

        val isImage = sel?.tool == PlaceTool.IMAGE
        binding.btnSelRotateLeft.visibility = if (isImage) View.VISIBLE else View.GONE
        binding.btnSelRotateRight.visibility = if (isImage) View.VISIBLE else View.GONE
        binding.btnSelFitPage.visibility = if (isImage) View.VISIBLE else View.GONE

        placeBar.render(state)
    }

    /** «Змінити»: текст — повторне введення, ✓/✗ — перемикання виду. */
    private fun editSelected() {
        val sel = viewModel.editor.value.selected ?: return
        when (sel.tool) {
            PlaceTool.TEXT -> {
                viewModel.setPlaceSize(PlaceTool.TEXT, sel.sizePt)
                viewModel.placeColor = sel.color
                viewModel.placeTextBgColor = sel.bgColor
                showTextDialog(
                    initial = sel.rawText ?: sel.text,
                    titleRes = R.string.place_edit_text,
                    confirmRes = R.string.place_text_apply,
                    allowStyle = true,
                    anchorPoint = PageViewPoint(sel.pageIndex, sel.x, sel.y)
                ) { raw, resolvedAnchor, userMovedBox ->
                    val newSize = viewModel.placeSize(PlaceTool.TEXT)
                    val newColor = viewModel.placeColor
                    val newBgColor = viewModel.placeTextBgColor
                    val targetPageIdx = if (userMovedBox && resolvedAnchor != null) resolvedAnchor.pageIndex else sel.pageIndex
                    val targetX = if (userMovedBox && resolvedAnchor != null) resolvedAnchor.x else sel.x
                    val targetY = if (userMovedBox && resolvedAnchor != null) resolvedAnchor.y else sel.y
                    val page = viewerFragment?.bridge?.pageSizePt(targetPageIdx)
                    val wrapped = PlaceMarkupLogic.wrapText(
                        raw, targetX, newSize, page,
                        measureWidthPt = { line -> measureTextPt(line, newSize) }
                    )
                    viewModel.edit { s ->
                        val s0 = if (userMovedBox && resolvedAnchor != null) {
                            PlaceEditor.move(s, sel.id, targetPageIdx, targetX, targetY, page)
                        } else s
                        val s1 = PlaceEditor.setSize(s0, sel.id, newSize)
                        val s2 = PlaceEditor.setColor(s1, sel.id, newColor)
                        val s3 = PlaceEditor.setBgColor(s2, sel.id, newBgColor)
                        PlaceEditor.setText(s3, sel.id, wrapped, raw)
                    }
                }
            }
            PlaceTool.CHECK, PlaceTool.CROSS -> viewModel.edit { PlaceEditor.toggleMarkKind(it, sel.id) }
            else -> Unit
        }
    }

    /** Нове значення розміру/товщини зі слайдера картки: виділеному об'єкту або поточному інструменту. */
    private fun applyPlaceSize(value: Float) {
        val sel = viewModel.editor.value.selected
        if (sel != null) {
            val page = viewerFragment?.bridge?.pageSizePt(sel.pageIndex)
            when (sel.tool) {
                PlaceTool.TEXT -> {
                    val wrapped = PlaceMarkupLogic.wrapText(
                        sel.rawText ?: sel.text, sel.x, value, page,
                        measureWidthPt = { line -> measureTextPt(line, value) }
                    )
                    viewModel.edit { s ->
                        val s1 = PlaceEditor.setSize(s, sel.id, value)
                        PlaceEditor.setText(s1, sel.id, wrapped, sel.rawText)
                    }
                }
                PlaceTool.SIGNATURE, PlaceTool.IMAGE -> viewModel.edit { PlaceEditor.setSize(it, sel.id, value, page) }
                else -> viewModel.edit { PlaceEditor.setSize(it, sel.id, value) }
            }
            return
        }
        val tool = viewModel.placeTool
        viewModel.setPlaceSize(tool, value)
        renderEditor(viewModel.editor.value)
    }

    /** Перевірка прав → показ панелі й увімкнення тапу в мості. Для захищених/заборонених — лише пояснення. */
    private fun enterPlaceMode() {
        if (enteringPlaceMode) return
        if (viewModel.placeModeActive && binding.placeToolBar.isVisible) return
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
                if (!EditAccessModel.canMarkup(access)) {
                    // Автор заборонив зміну: інструменти «Поставити» вимкнені з поясненням (значення форм, якщо
                    // їх дозволено, редагуються у шторці «Форми»).
                    viewModel.clearPending()
                    viewModel.placeModeActive = false
                    showMessage(getString(R.string.place_blocked_rights), long = true)
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
        viewerFragment?.bridge?.isTextSearchActive = false
        Thread { safeUnsupported("a") }.start() // прогрів кешу шрифту: перша перевірка тексту без затримки
        binding.placeToolBar.visibility = View.VISIBLE
        binding.cardReaderHud.visibility = View.GONE
        binding.bottomActionBar.visibility = View.GONE
        binding.unsavedBar.visibility = View.GONE
        viewerFragment?.bridge?.setPlaceMode(true) { point -> onPageTapped(point) }
        binding.markupOverlay.editingEnabled = !savingPlace
        renderEditor(viewModel.editor.value)
        invalidateOptionsMenu()
    }

    private fun hidePlaceUi() {
        dismissSignatureHint()
        viewModel.placeModeActive = false
        binding.markupOverlay.editingEnabled = false
        viewModel.clearPending() // виділення й історія скасування не переживають вихід із режиму
        binding.selectionBarCard.visibility = View.GONE
        viewerFragment?.bridge?.setPlaceMode(false)
        placeBar.closeCard(animate = false)
        binding.placeToolBar.visibility = View.GONE
        binding.cardReaderHud.visibility = View.VISIBLE
        binding.bottomActionBar.visibility = View.VISIBLE
        renderUnsavedUi()
        invalidateOptionsMenu()
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
            .showTracked()
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
            PlaceTool.INK, PlaceTool.HAND, PlaceTool.IMAGE -> Unit // штрих малює оверлей; «рука» нічого не ставить
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
        showTextDialog(
            initial = "",
            titleRes = R.string.place_text_title,
            confirmRes = R.string.place_text_add,
            allowStyle = true,
            anchorPoint = point
        ) { raw, resolvedAnchor, _ ->
            val unsupported = safeUnsupported(raw)
            if (!unsupported.isNullOrEmpty()) {
                showError(getString(R.string.place_text_unsupported, unsupported))
                return@showTextDialog
            }
            val activePoint = resolvedAnchor ?: point
            val page = viewerFragment?.bridge?.pageSizePt(activePoint.pageIndex)
            val fontSize = viewModel.placeSize(PlaceTool.TEXT)
            val chunks = PlaceMarkupLogic.paginateText(
                initialPageIndex = activePoint.pageIndex,
                startX = activePoint.x,
                startY = activePoint.y,
                rawText = raw,
                fontSizePt = fontSize,
                page = page,
                measureWidthPt = { line -> measureTextPt(line, fontSize) }
            )

            if (chunks.isEmpty()) return@showTextDialog

            if (chunks.size == 1) {
                val check = PlaceMarkupLogic.textFromTap(
                    UUID.randomUUID().toString(), chunks[0].pageIndex, chunks[0].x, chunks[0].y,
                    chunks[0].rawText, fontSize, viewModel.placeColor, "",
                    page = page,
                    measureWidthPt = { line -> measureTextPt(line, fontSize) },
                    bgColor = viewModel.placeTextBgColor
                )
                if (check is TextCheck.Ok) {
                    viewModel.addPending(check.markup)
                    viewModel.placeTool = PlaceTool.HAND
                    viewModel.edit { PlaceEditor.select(it, check.markup.id) }
                    selectToolButton(PlaceTool.HAND)
                }
                return@showTextDialog
            }

            // Текст виходить за межі однієї сторінки -> додаємо сторінки за потреби та розміщуємо шматки
            val maxPageNeeded = chunks.maxOf { it.pageIndex }
            val currentTotalPages = loadedPageCount.coerceAtLeast(viewModel.document.value?.pageCount ?: 1)

            val addChunksAction = {
                var firstAddedId: String? = null
                chunks.forEach { chunk ->
                    val id = UUID.randomUUID().toString()
                    if (firstAddedId == null) firstAddedId = id
                    val markup = PendingMarkup(
                        id = id,
                        tool = PlaceTool.TEXT,
                        pageIndex = chunk.pageIndex,
                        x = chunk.x,
                        y = chunk.y,
                        text = chunk.text,
                        sizePt = fontSize,
                        color = viewModel.placeColor,
                        rawText = chunk.rawText,
                        bgColor = viewModel.placeTextBgColor
                    )
                    viewModel.addPending(markup)
                }
                firstAddedId?.let { id ->
                    viewModel.placeTool = PlaceTool.HAND
                    viewModel.edit { PlaceEditor.select(it, id) }
                    selectToolButton(PlaceTool.HAND)
                }
            }

            if (maxPageNeeded >= currentTotalPages) {
                val pagesToAdd = maxPageNeeded - currentTotalPages + 1
                val docUri = documentUri
                if (docUri != null) {
                    lifecycleScope.launch {
                        showLoadingOverlay(getString(R.string.saving_annotations))
                        val engine = com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine()
                        val result = engine.appendBlankPages(
                            context = this@ReaderActivity,
                            sourceUri = docUri,
                            count = pagesToAdd,
                            outputUri = docUri
                        )
                        hideLoadingOverlay()
                        if (result is com.savoj.volumpdf.core.pdf.model.PdfOperationResult.Success) {
                            showMessage(getString(R.string.text_pages_added, pagesToAdd), long = false)
                            reloadViewerDocument()
                            addChunksAction()
                        } else {
                            val first = chunks.first()
                            val id = UUID.randomUUID().toString()
                            viewModel.addPending(
                                PendingMarkup(
                                    id = id,
                                    tool = PlaceTool.TEXT,
                                    pageIndex = first.pageIndex,
                                    x = first.x,
                                    y = first.y,
                                    text = first.text,
                                    sizePt = fontSize,
                                    color = viewModel.placeColor,
                                    rawText = first.rawText,
                                    bgColor = viewModel.placeTextBgColor
                                )
                            )
                            viewModel.placeTool = PlaceTool.HAND
                            viewModel.edit { PlaceEditor.select(it, id) }
                            selectToolButton(PlaceTool.HAND)
                        }
                    }
                    return@showTextDialog
                }
            }

            addChunksAction()
        }
    }

    private val textMeasurePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = try {
                Typeface.createFromAsset(assets, MarkupLayout.FONT_ASSET_PATH)
            } catch (_: Throwable) {
                Typeface.DEFAULT
            }
        }
    }

    private fun measureTextPt(text: String, fontSizePt: Float): Float {
        textMeasurePaint.textSize = fontSizePt
        return textMeasurePaint.measureText(text)
    }

    /** Непідтримувані символи; null — перевірку виконати не вдалося (шрифт недоступний). */
    private fun safeUnsupported(raw: String): String? = try {
        markupEngine.findUnsupportedChars(raw)
    } catch (e: Throwable) {
        null
    }

    /**
     * Введення тексту (додавання й повторне введення):
     * 1) Поле вводу (`placeTextEditorBox`) завжди автоматично розташовується вище панелі редагування (`placeTextCard`)
     *    та клавіатури (з урахуванням реальної висоти поля, кеглю і кількості рядків).
     * 2) Поле можна вільно переміщувати тапом по будь-якій точці сторінки або перетягуванням.
     * 3) Розмір тексту динамічно підлаштовується під розмір поля при його збільшенні чи зменшенні (кутовим маркером
     *    `btnTextBoxResize` або щипком двома пальцями).
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun showTextDialog(
        initial: String,
        titleRes: Int,
        confirmRes: Int,
        allowStyle: Boolean = true,
        anchorPoint: PageViewPoint? = null,
        onAccept: (raw: String, resolvedAnchor: PageViewPoint?, userMovedBox: Boolean) -> Unit
    ) {
        val dialogBinding = DialogPlaceTextBinding.inflate(layoutInflater)
        dialogBinding.placeTextTitle.setText(titleRes)
        dialogBinding.btnTextConfirm.contentDescription = getString(confirmRes)
        val dialog = androidx.appcompat.app.AppCompatDialog(this, R.style.Theme_VolumPdf_TextCard)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            setDimAmount(0f)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // Панель-пігулка сама піднімається над клавіатурою за ime-відступом нижче.
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }

        val density = resources.displayMetrics.density
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        val sideMarginPx = (12f * density).toInt()
        val minBoxWidthPx = (132f * density).toInt()
        val minBoxHeightPx = (48f * density).toInt()
        val cardGapPx = (12f * density).toInt()
        val toolbarReservePx = (70f * density).toInt()

        val initialViewPt = anchorPoint?.let { viewerFragment?.bridge?.pageToView(it) }
        val hostInsets = ViewCompat.getRootWindowInsets(binding.root)
        val initBars = hostInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        var currentTopInset = initBars?.top ?: (24f * density).toInt()
        var currentLeftInset = initBars?.left ?: 0
        var currentRightInset = initBars?.right ?: 0
        var currentBottomPad = hostInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        var realImeBottom = currentBottomPad

        val screenWInit = resources.displayMetrics.widthPixels
        val initMaxW = (screenWInit - 2 * sideMarginPx).coerceAtLeast(minBoxWidthPx)
        var currentBoxWidthPx = PlaceBarLogic.editorBoxWidthForSizePt(
            viewModel.placeSize(PlaceTool.TEXT),
            density,
            minBoxWidthPx,
            initMaxW
        )

        var userDesiredLeft = if (initialViewPt != null) {
            (binding.viewerWrapper.left + initialViewPt.x.toInt() - currentLeftInset)
        } else {
            sideMarginPx
        }
        var userDesiredTop = if (initialViewPt != null) {
            (binding.viewerWrapper.top + initialViewPt.y.toInt() - currentTopInset)
        } else {
            Int.MAX_VALUE / 2
        }
        var stickAboveToolbar = initialViewPt == null
        var userMovedBox = false
        var wasClampedUpward = false
        var inLayoutUpdate = false

        fun applyEditorBoxLayout() {
            if (inLayoutUpdate) return
            inLayoutUpdate = true
            try {
                val rootW = dialogBinding.placeTextRoot.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val rootH = dialogBinding.placeTextRoot.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
                val padLeft = dialogBinding.placeTextRoot.paddingLeft.takeIf { it > 0 } ?: currentLeftInset
                val padRight = dialogBinding.placeTextRoot.paddingRight.takeIf { it > 0 } ?: currentRightInset
                val padTop = dialogBinding.placeTextRoot.paddingTop.takeIf { it > 0 } ?: currentTopInset

                // До моменту, поки клавіатура не завершила анімацію відкриття, резервуємо безпечну висоту під неї,
                // щоб поле вводу одразу ставало НАД панеллю редагування і ніколи не перекривалося клавіатурою.
                val fallbackKeyboardReserve = (rootH * 0.42f).toInt()
                val effectiveBottomPad = if (realImeBottom > 0) {
                    maxOf(dialogBinding.placeTextRoot.paddingBottom, realImeBottom)
                } else {
                    maxOf(dialogBinding.placeTextRoot.paddingBottom, currentBottomPad, fallbackKeyboardReserve)
                }

                val availW = (rootW - padLeft - padRight).coerceAtLeast(minBoxWidthPx)
                val availH = (rootH - padTop - effectiveBottomPad).coerceAtLeast(minBoxHeightPx + toolbarReservePx)
                val minTopPx = (binding.viewerWrapper.top - padTop).coerceAtLeast((8f * density).toInt())

                val maxAllowedW = (availW - 2 * sideMarginPx).coerceAtLeast(minBoxWidthPx)
                val targetW = currentBoxWidthPx.coerceIn(minOf(minBoxWidthPx, maxAllowedW), maxAllowedW)

                // Вимірюємо реальну висоту поля з поточним текстом та кеглем при ширині targetW
                dialogBinding.placeTextEditorBox.measure(
                    View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val measuredBoxH = maxOf(dialogBinding.placeTextEditorBox.measuredHeight, dialogBinding.placeTextEditorBox.height, minBoxHeightPx)

                val cardTopInContent = if (realImeBottom > 0 && dialogBinding.placeTextCard.height > 0 && dialogBinding.placeTextCard.top > 0) {
                    dialogBinding.placeTextCard.top - padTop
                } else {
                    -1
                }

                val desiredTopForCalc = if (stickAboveToolbar) Int.MAX_VALUE / 2 else userDesiredTop
                val bounds = PlaceBarLogic.computeEditorBoxBounds(
                    availWidthPx = availW,
                    availHeightPx = availH,
                    minTopPx = minTopPx,
                    toolbarReservePx = toolbarReservePx,
                    sideMarginPx = sideMarginPx,
                    minBoxWidthPx = minBoxWidthPx,
                    minBoxHeightPx = minBoxHeightPx,
                    desiredLeftPx = userDesiredLeft,
                    desiredTopPx = desiredTopForCalc,
                    boxWidthPx = targetW,
                    boxHeightPx = measuredBoxH,
                    cardTopInContentPx = cardTopInContent,
                    cardGapPx = cardGapPx
                )

                if (!stickAboveToolbar && userDesiredTop > bounds.topPx + 2) {
                    wasClampedUpward = true
                    stickAboveToolbar = true
                }

                currentBoxWidthPx = bounds.widthPx
                dialogBinding.placeTextInput.maxHeight = bounds.maxInputHeightPx

                val lp = dialogBinding.placeTextEditorBox.layoutParams as FrameLayout.LayoutParams
                if (lp.width != bounds.widthPx || lp.topMargin != bounds.topPx || lp.marginStart != bounds.leftPx) {
                    lp.width = bounds.widthPx
                    lp.topMargin = bounds.topPx
                    lp.marginStart = bounds.leftPx
                    lp.marginEnd = sideMarginPx
                    dialogBinding.placeTextEditorBox.layoutParams = lp
                }

                // Не перекривати поле вводу карткою кольорів / фону шрифту:
                // картка за замовчуванням піднята вище (як у Samsung Notes — 112 dp), а при перетині з полем вводу
                // автоматично піднімається НАД полем вводу (або опускається ПІД нього, якщо поле вгорі екрана).
                val defaultPopupBottomMarginPx = (112f * density).toInt()
                val popupGapPx = (10f * density).toInt()
                dialogBinding.placeTextColorPopup.measure(
                    View.MeasureSpec.makeMeasureSpec((304f * density).toInt().coerceAtMost(availW), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val colorPopupH = maxOf(dialogBinding.placeTextColorPopup.measuredHeight, dialogBinding.placeTextColorPopup.height, (82f * density).toInt())
                val colorPopupMargin = PlaceBarLogic.computePopupBottomMarginPx(
                    availHeightPx = availH,
                    minTopPx = minTopPx,
                    toolbarReservePx = toolbarReservePx,
                    defaultBottomMarginPx = defaultPopupBottomMarginPx,
                    popupHeightPx = colorPopupH,
                    editorBoxTopPx = bounds.topPx,
                    editorBoxBottomPx = bounds.topPx + measuredBoxH,
                    gapPx = popupGapPx
                )
                val colorPopupLp = dialogBinding.placeTextColorPopup.layoutParams as FrameLayout.LayoutParams
                if (colorPopupLp.bottomMargin != colorPopupMargin) {
                    colorPopupLp.bottomMargin = colorPopupMargin
                    dialogBinding.placeTextColorPopup.layoutParams = colorPopupLp
                }
            } finally {
                inLayoutUpdate = false
            }
        }

        fun resolveEditorBoxPagePoint(): PageViewPoint? {
            if (!userMovedBox && !wasClampedUpward && anchorPoint != null) {
                return anchorPoint
            }
            val b = viewerFragment?.bridge ?: return anchorPoint
            val inputLoc = IntArray(2)
            val wrapperLoc = IntArray(2)
            dialogBinding.placeTextInput.getLocationOnScreen(inputLoc)
            binding.viewerWrapper.getLocationOnScreen(wrapperLoc)
            if (inputLoc[0] == 0 && inputLoc[1] == 0) return anchorPoint
            val viewX = (inputLoc[0] - wrapperLoc[0] + dialogBinding.placeTextInput.paddingLeft).toFloat()
            val viewY = (inputLoc[1] - wrapperLoc[1] + dialogBinding.placeTextInput.paddingTop).toFloat()
            val direct = try {
                b.viewToPage(viewX, viewY)
            } catch (_: Throwable) {
                null
            }
            if (direct != null) {
                val page = b.pageSizePt(direct.pageIndex)
                val (cx, cy) = PlaceMarkupLogic.clampToPage(direct.x, direct.y, page)
                return PageViewPoint(direct.pageIndex, cx, cy)
            }
            val fallbackPageIdx = anchorPoint?.pageIndex ?: 0
            val projected = try {
                b.viewToPageSpace(fallbackPageIdx, viewX, viewY)
            } catch (_: Throwable) {
                null
            }
            if (projected != null) {
                val page = b.pageSizePt(fallbackPageIdx)
                val (cx, cy) = PlaceMarkupLogic.clampToPage(projected.x, projected.y, page)
                return PageViewPoint(fallbackPageIdx, cx, cy)
            }
            return anchorPoint
        }

        ViewCompat.setOnApplyWindowInsetsListener(dialogBinding.placeTextRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            currentTopInset = bars.top
            currentLeftInset = bars.left
            currentRightInset = bars.right
            if (ime.bottom > 0) realImeBottom = ime.bottom
            val bottomPad = maxOf(bars.bottom, ime.bottom)
            currentBottomPad = bottomPad
            v.setPadding(bars.left, bars.top, bars.right, bottomPad)
            applyEditorBoxLayout()
            insets
        }

        dialogBinding.placeTextEditorBox.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if ((bottom - top) != (oldBottom - oldTop) || top != oldTop) {
                val cardTop = dialogBinding.placeTextCard.top
                if (cardTop > 0 && bottom > cardTop - cardGapPx) {
                    dialogBinding.placeTextEditorBox.post { applyEditorBoxLayout() }
                }
            }
        }
        dialogBinding.placeTextCard.addOnLayoutChangeListener { _, _, top, _, _, _, oldTop, _, _ ->
            if (top != oldTop) {
                dialogBinding.placeTextEditorBox.post { applyEditorBoxLayout() }
            }
        }
        applyEditorBoxLayout()

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var validate: Runnable
        val confirm = dialogBinding.btnTextConfirm

        // Ширина панелі-пігулки над клавіатурою: не ширше за max (ландшафт/планшет), портрет — на всю ширину з полями 8dp.
        val maxW = resources.getDimensionPixelSize(R.dimen.place_card_max_width) * 4 / 3
        val sideMargin = (8f * density).toInt()
        dialogBinding.placeTextCard.layoutParams = (dialogBinding.placeTextCard.layoutParams as FrameLayout.LayoutParams).apply {
            width = minOf(maxW, resources.displayMetrics.widthPixels - 2 * sideMargin)
        }

        fun closeAllPopups(): Boolean {
            val anyOpen = dialogBinding.placeTextColorPopup.isVisible ||
                dialogBinding.placeTextSizePopup.isVisible ||
                dialogBinding.placeTextStylePopup.isVisible
            dialogBinding.placeTextColorPopup.visibility = View.GONE
            dialogBinding.placeTextSizePopup.visibility = View.GONE
            dialogBinding.placeTextStylePopup.visibility = View.GONE
            return anyOpen
        }

        fun render(raw: String): Boolean {
            val unsupported = safeUnsupported(raw)
            if (unsupported == null) {
                dialogBinding.placeTextInputLayout.error = getString(R.string.error_title)
                confirm.isEnabled = false
                confirm.alpha = 0.38f
                return false
            }
            val verdict = PlaceMarkupLogic.checkText(raw, unsupported)
            dialogBinding.placeTextInputLayout.error = when (verdict) {
                TextVerdict.Ok -> null
                TextVerdict.Empty -> if (raw.isEmpty()) null else getString(R.string.text_error_empty)
                is TextVerdict.Unsupported -> getString(R.string.place_text_unsupported, verdict.chars)
            }
            val ok = verdict == TextVerdict.Ok
            confirm.isEnabled = ok
            confirm.alpha = if (ok) 1f else 0.38f
            return ok
        }

        val accentColor = getColor(R.color.place_card_accent)
        val onCardColor = getColor(R.color.place_card_on)
        val styleLabels = intArrayOf(
            R.string.place_text_style_h1,
            R.string.place_text_style_h2,
            R.string.place_text_style_body1,
            R.string.place_text_style_body2
        )

        lateinit var rebuildSizeList: () -> Unit
        lateinit var rebuildStyleList: () -> Unit

        // Живий попередній перегляд: число розміру (напр. «15▾», «21▾», «64▾»), іконка «T» (колір тексту), іконка «[T]» (фон шрифту) та реальний кегль 8..72sp на сторінці.
        fun refreshStyle(updateWidthFromSize: Boolean = false) {
            val size = viewModel.placeSize(PlaceTool.TEXT)
            dialogBinding.btnTextSize.text = PlaceMarkupLogic.formatSize(size)
            dialogBinding.btnTextColor.textIconMode = true
            dialogBinding.btnTextColor.swatchColor = viewModel.placeColor or PlaceMarkupLogic.ALPHA_MASK
            dialogBinding.btnTextBgColor.textBgIconMode = true
            dialogBinding.btnTextBgColor.swatchColor = viewModel.placeTextBgColor
            dialogBinding.placeTextInput.setTextColor(viewModel.placeColor or PlaceMarkupLogic.ALPHA_MASK)
            val bg = viewModel.placeTextBgColor
            if ((bg ushr 24) != 0) {
                dialogBinding.placeTextInput.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 4f * density
                    setColor(bg or PlaceMarkupLogic.ALPHA_MASK)
                }
            } else {
                dialogBinding.placeTextInput.background = null
            }
            val clampedSp = size.coerceIn(8f, 72f)
            dialogBinding.placeTextInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, clampedSp)
            dialogBinding.placeTextInput.minHeight = (clampedSp * 1.8f * density).toInt().coerceAtLeast((36f * density).toInt())
            if (updateWidthFromSize) {
                val rootW = dialogBinding.placeTextRoot.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val maxBoxW = (rootW - 2 * sideMarginPx).coerceAtLeast(minBoxWidthPx)
                currentBoxWidthPx = PlaceBarLogic.editorBoxWidthForSizePt(clampedSp, density, minBoxWidthPx, maxBoxW)
            }
            applyEditorBoxLayout()
        }

        rebuildSizeList = {
            val list = dialogBinding.placeTextSizeList
            list.removeAllViews()
            val curSize = viewModel.placeSize(PlaceTool.TEXT)
            var selectedRowIndex = 0
            PlaceBarLogic.TEXT_SIZE_CHOICES.forEachIndexed { idx, choice ->
                val isSelected = kotlin.math.abs(choice - curSize) < 0.4f
                if (isSelected) selectedRowIndex = idx
                val itemRow = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = (42f * density).toInt()
                    setPadding((16f * density).toInt(), (6f * density).toInt(), (14f * density).toInt(), (6f * density).toInt())
                    val outVal = android.util.TypedValue()
                    if (theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)) {
                        setBackgroundResource(outVal.resourceId)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        viewModel.setPlaceSize(PlaceTool.TEXT, choice)
                        refreshStyle(updateWidthFromSize = true)
                        dialogBinding.placeTextSizePopup.visibility = View.GONE
                    }
                }
                val tvNum = android.widget.TextView(this).apply {
                    text = PlaceMarkupLogic.formatSize(choice)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(if (isSelected) accentColor else onCardColor)
                    setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvCheck = android.widget.TextView(this).apply {
                    text = if (isSelected) "✓" else ""
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(accentColor)
                    setTypeface(typeface, Typeface.BOLD)
                }
                itemRow.addView(tvNum)
                itemRow.addView(tvCheck)
                list.addView(itemRow)
            }
            dialogBinding.placeTextSizePopup.post {
                val rowH = (42f * density).toInt()
                val targetScrollY = (selectedRowIndex * rowH - (72f * density).toInt()).coerceAtLeast(0)
                dialogBinding.placeTextSizePopup.scrollTo(0, targetScrollY)
            }
        }

        rebuildStyleList = {
            val list = dialogBinding.placeTextStyleList
            list.removeAllViews()
            val curSize = viewModel.placeSize(PlaceTool.TEXT)
            styleLabels.forEachIndexed { idx, labelRes ->
                val presetSize = PlaceBarLogic.TEXT_STYLE_SIZES_PT.getOrElse(idx) { 14f }
                val isSelected = kotlin.math.abs(presetSize - curSize) < 0.4f
                val itemRow = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = (44f * density).toInt()
                    setPadding((16f * density).toInt(), (8f * density).toInt(), (16f * density).toInt(), (8f * density).toInt())
                    val outVal = android.util.TypedValue()
                    if (theme.resolveAttribute(android.R.attr.selectableItemBackground, outVal, true)) {
                        setBackgroundResource(outVal.resourceId)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        viewModel.setPlaceSize(PlaceTool.TEXT, presetSize)
                        refreshStyle(updateWidthFromSize = true)
                        dialogBinding.placeTextStylePopup.visibility = View.GONE
                    }
                }
                val tvLabel = android.widget.TextView(this).apply {
                    text = getString(labelRes)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(if (isSelected) accentColor else onCardColor)
                    setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvCheck = android.widget.TextView(this).apply {
                    text = if (isSelected) "✓" else ""
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(accentColor)
                    setTypeface(typeface, Typeface.BOLD)
                }
                itemRow.addView(tvLabel)
                itemRow.addView(tvCheck)
                list.addView(itemRow)
            }
        }

        dialogBinding.btnKbToolText.isSelected = true
        dialogBinding.btnKbToolPen.setOnClickListener {
            handler.removeCallbacks(validate)
            val raw = dialogBinding.placeTextInput.text?.toString().orEmpty()
            if (raw.isNotBlank() && render(raw)) onAccept(raw, resolveEditorBoxPagePoint(), userMovedBox)
            dialog.dismiss()
            placeBarHost.chooseTool(PlaceTool.INK)
        }
        dialogBinding.btnKbToolCheck.setOnClickListener {
            handler.removeCallbacks(validate)
            val raw = dialogBinding.placeTextInput.text?.toString().orEmpty()
            if (raw.isNotBlank() && render(raw)) onAccept(raw, resolveEditorBoxPagePoint(), userMovedBox)
            dialog.dismiss()
            placeBarHost.chooseTool(PlaceTool.CHECK)
        }

        if (allowStyle) {
            refreshStyle(updateWidthFromSize = false)
            dialogBinding.btnTextStyle.setOnClickListener {
                val show = !dialogBinding.placeTextStylePopup.isVisible
                closeAllPopups()
                if (show) {
                    rebuildStyleList()
                    dialogBinding.placeTextStylePopup.visibility = View.VISIBLE
                }
            }
            val toggleSizePopup = View.OnClickListener {
                val show = !dialogBinding.placeTextSizePopup.isVisible
                closeAllPopups()
                if (show) {
                    rebuildSizeList()
                    dialogBinding.placeTextSizePopup.visibility = View.VISIBLE
                }
            }
            dialogBinding.btnTextSize.setOnClickListener(toggleSizePopup)
            dialogBinding.btnKbSizeMinus.setOnClickListener(toggleSizePopup)

            val row = dialogBinding.placeTextColorRow
            var isBgColorPopupMode = false

            fun populateColorPopup(bgMode: Boolean) {
                isBgColorPopupMode = bgMode
                dialogBinding.tvTextColorPopupTitle.setText(
                    if (bgMode) R.string.place_text_bg_color_toggle else R.string.place_text_color_toggle
                )
                row.removeAllViews()
                if (bgMode) {
                    val curBg = viewModel.placeTextBgColor
                    PlaceBarLogic.TEXT_BG_PALETTE.forEach { preset ->
                        row.addView(SwatchView(this).apply {
                            compactMode = true
                            swatchColor = preset
                            picked = if ((preset ushr 24) == 0) {
                                (curBg ushr 24) == 0
                            } else {
                                (curBg ushr 24) != 0 && (curBg and 0xFFFFFF) == (preset and 0xFFFFFF)
                            }
                            contentDescription = if ((preset ushr 24) == 0) {
                                getString(R.string.color_none)
                            } else {
                                getString(R.string.place_text_bg_color_toggle)
                            }
                            setOnClickListener {
                                viewModel.placeTextBgColor = preset
                                populateColorPopup(true)
                                refreshStyle(updateWidthFromSize = false)
                            }
                        })
                    }
                    // 9-й кружечок у тому самому ряду — райдужний вибір власного кольору фону (як у Samsung Notes)
                    row.addView(SwatchView(this).apply {
                        compactMode = true
                        rainbow = true
                        picked = PlaceBarLogic.isCustomBgColor(curBg)
                        contentDescription = getString(R.string.color_custom)
                        setOnClickListener {
                            val startCol = if ((curBg ushr 24) != 0) curBg else 0xFFFFEE58.toInt()
                            showCustomColorDialog(startCol) { rgb ->
                                val full = PlaceMarkupLogic.ALPHA_MASK or (rgb and 0xFFFFFF)
                                viewModel.placeTextBgColor = full
                                populateColorPopup(true)
                                refreshStyle(updateWidthFromSize = false)
                            }
                        }
                    })
                } else {
                    val curColor = viewModel.placeColor
                    PlaceBarLogic.PALETTE.forEach { color ->
                        row.addView(SwatchView(this).apply {
                            compactMode = true
                            swatchColor = color
                            picked = (curColor and 0xFFFFFF) == (color and 0xFFFFFF)
                            contentDescription = getString(R.string.place_text_color_toggle)
                            setOnClickListener {
                                viewModel.placeColor = color
                                populateColorPopup(false)
                                refreshStyle(updateWidthFromSize = false)
                            }
                        })
                    }
                    // Райдужний кружечок власного кольору в кінці того самого ряду
                    row.addView(SwatchView(this).apply {
                        compactMode = true
                        rainbow = true
                        picked = PlaceBarLogic.isCustomColor(curColor)
                        contentDescription = getString(R.string.color_custom)
                        setOnClickListener {
                            showCustomColorDialog(curColor) { rgb ->
                                val full = PlaceMarkupLogic.ALPHA_MASK or (rgb and 0xFFFFFF)
                                viewModel.placeColor = full
                                populateColorPopup(false)
                                refreshStyle(updateWidthFromSize = false)
                            }
                        }
                    })
                }
                applyEditorBoxLayout()
            }

            dialogBinding.btnTextColorClose.setOnClickListener {
                dialogBinding.placeTextColorPopup.visibility = View.GONE
            }
            dialogBinding.btnTextColor.setOnClickListener {
                val show = !dialogBinding.placeTextColorPopup.isVisible || isBgColorPopupMode
                closeAllPopups()
                if (show) {
                    populateColorPopup(bgMode = false)
                    dialogBinding.placeTextColorPopup.visibility = View.VISIBLE
                    applyEditorBoxLayout()
                }
            }
            dialogBinding.btnTextBgColor.setOnClickListener {
                val show = !dialogBinding.placeTextColorPopup.isVisible || !isBgColorPopupMode
                closeAllPopups()
                if (show) {
                    populateColorPopup(bgMode = true)
                    dialogBinding.placeTextColorPopup.visibility = View.VISIBLE
                    applyEditorBoxLayout()
                }
            }
        } else {
            dialogBinding.btnTextStyle.visibility = View.GONE
            dialogBinding.btnTextSize.visibility = View.GONE
            dialogBinding.btnTextColor.visibility = View.GONE
            dialogBinding.btnTextBgColor.visibility = View.GONE
        }

        // Історія Undo / Redo для введеного тексту безпосередньо з панелі над клавіатурою.
        val undoStack = ArrayDeque<String>().apply { addLast(initial) }
        val redoStack = ArrayDeque<String>()
        var suppressHistory = false

        fun updateUndoRedoUi() {
            val canUndo = undoStack.size > 1
            val canRedo = redoStack.isNotEmpty()
            dialogBinding.btnTextUndo.isEnabled = canUndo
            dialogBinding.btnTextUndo.alpha = if (canUndo) 1f else 0.38f
            dialogBinding.btnTextRedo.isEnabled = canRedo
            dialogBinding.btnTextRedo.alpha = if (canRedo) 1f else 0.38f
        }
        updateUndoRedoUi()

        dialogBinding.btnTextUndo.setOnClickListener {
            if (undoStack.size > 1) {
                val cur = undoStack.removeLast()
                redoStack.addLast(cur)
                val prev = undoStack.last()
                suppressHistory = true
                dialogBinding.placeTextInput.setText(prev)
                dialogBinding.placeTextInput.setSelection(prev.length)
                suppressHistory = false
                render(prev)
                updateUndoRedoUi()
            }
        }
        dialogBinding.btnTextRedo.setOnClickListener {
            if (redoStack.isNotEmpty()) {
                val next = redoStack.removeLast()
                undoStack.addLast(next)
                suppressHistory = true
                dialogBinding.placeTextInput.setText(next)
                dialogBinding.placeTextInput.setSelection(next.length)
                suppressHistory = false
                render(next)
                updateUndoRedoUi()
            }
        }

        validate = Runnable { render(dialogBinding.placeTextInput.text?.toString().orEmpty()) }
        dialogBinding.placeTextInput.setText(initial)
        dialogBinding.placeTextInput.setSelection(initial.length)
        dialogBinding.placeTextInput.setOnClickListener { closeAllPopups() }
        dialogBinding.placeTextInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val cur = s?.toString().orEmpty()
                if (!suppressHistory && undoStack.lastOrNull() != cur) {
                    if (undoStack.size >= 40) undoStack.removeFirst()
                    undoStack.addLast(cur)
                    redoStack.clear()
                    updateUndoRedoUi()
                }
                applyEditorBoxLayout()
                handler.removeCallbacks(validate)
                if (cur.isBlank()) {
                    validate.run()
                } else {
                    handler.postDelayed(validate, TEXT_VALIDATE_DEBOUNCE_MS)
                }
            }
        })

        // Вимога 3: збільшення або зменшення поля для тексту автоматично змінює розмір тексту під поле.
        var resizeStartRawX = 0f
        var resizeStartRawY = 0f
        var resizeStartWidthPx = currentBoxWidthPx
        var resizeStartSizePt = viewModel.placeSize(PlaceTool.TEXT)

        dialogBinding.btnTextBoxResize.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    closeAllPopups()
                    resizeStartRawX = event.rawX
                    resizeStartRawY = event.rawY
                    resizeStartWidthPx = dialogBinding.placeTextEditorBox.width.takeIf { it > 0 } ?: currentBoxWidthPx
                    resizeStartSizePt = viewModel.placeSize(PlaceTool.TEXT)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - resizeStartRawX
                    val dy = event.rawY - resizeStartRawY
                    val targetSpanPx = (resizeStartWidthPx + dx + dy * 0.85f).coerceAtLeast(minBoxWidthPx * 0.5f)
                    val newSizePt = PlaceBarLogic.scaleTextSizeByBoxRatio(
                        startSizePt = resizeStartSizePt,
                        startSpanPx = resizeStartWidthPx.toFloat(),
                        currentSpanPx = targetSpanPx,
                        minSizePt = 8f,
                        maxSizePt = 72f
                    )
                    val rootW = dialogBinding.placeTextRoot.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                    val maxBoxW = (rootW - 2 * sideMarginPx).coerceAtLeast(minBoxWidthPx)
                    currentBoxWidthPx = targetSpanPx.toInt().coerceIn(minBoxWidthPx, maxBoxW)
                    viewModel.setPlaceSize(PlaceTool.TEXT, newSizePt)
                    refreshStyle(updateWidthFromSize = false)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        // Масштабування поля та кеглю щипком двома пальцями, а також перетягування поля за рамку.
        var boxDragStartRawX = 0f
        var boxDragStartRawY = 0f
        var boxDragStartLeft = 0
        var boxDragStartTop = 0
        var isBoxDragging = false
        var isBoxPinching = false
        var boxPinchStartDist = 0f
        var boxPinchStartWidthPx = currentBoxWidthPx
        var boxPinchStartSizePt = viewModel.placeSize(PlaceTool.TEXT)

        val pinchAndDragListener = View.OnTouchListener { v, event ->
            if (event.pointerCount >= 2) {
                val dx = event.getX(1) - event.getX(0)
                val dy = event.getY(1) - event.getY(0)
                val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(10f)
                if (!isBoxPinching) {
                    isBoxPinching = true
                    isBoxDragging = false
                    boxPinchStartDist = dist
                    boxPinchStartWidthPx = dialogBinding.placeTextEditorBox.width.takeIf { it > 0 } ?: currentBoxWidthPx
                    boxPinchStartSizePt = viewModel.placeSize(PlaceTool.TEXT)
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                } else if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                    val scale = dist / boxPinchStartDist
                    val targetSpanPx = boxPinchStartWidthPx * scale
                    val newSizePt = PlaceBarLogic.scaleTextSizeByBoxRatio(
                        startSizePt = boxPinchStartSizePt,
                        startSpanPx = boxPinchStartWidthPx.toFloat(),
                        currentSpanPx = targetSpanPx,
                        minSizePt = 8f,
                        maxSizePt = 72f
                    )
                    val rootW = dialogBinding.placeTextRoot.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                    val maxBoxW = (rootW - 2 * sideMarginPx).coerceAtLeast(minBoxWidthPx)
                    currentBoxWidthPx = targetSpanPx.toInt().coerceIn(minBoxWidthPx, maxBoxW)
                    viewModel.setPlaceSize(PlaceTool.TEXT, newSizePt)
                    refreshStyle(updateWidthFromSize = false)
                }
                return@OnTouchListener true
            }
            if (isBoxPinching) {
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                    event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
                ) {
                    isBoxPinching = false
                }
                return@OnTouchListener true
            }
            if (v === dialogBinding.placeTextEditorBox) {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        closeAllPopups()
                        val lp = dialogBinding.placeTextEditorBox.layoutParams as FrameLayout.LayoutParams
                        boxDragStartRawX = event.rawX
                        boxDragStartRawY = event.rawY
                        boxDragStartLeft = lp.marginStart
                        boxDragStartTop = lp.topMargin
                        isBoxDragging = false
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - boxDragStartRawX
                        val dy = event.rawY - boxDragStartRawY
                        if (!isBoxDragging && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            isBoxDragging = true
                        }
                        if (isBoxDragging) {
                            userDesiredLeft = boxDragStartLeft + dx.toInt()
                            userDesiredTop = boxDragStartTop + dy.toInt()
                            stickAboveToolbar = false
                            userMovedBox = true
                            applyEditorBoxLayout()
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        isBoxDragging = false
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        dialogBinding.placeTextEditorBox.setOnTouchListener(pinchAndDragListener)
        dialogBinding.placeTextInput.setOnTouchListener(pinchAndDragListener)

        // Вимога 2: поле можна переміщувати тапом по сторінці (або перетягуванням по сторінці).
        dialogBinding.placeTextRoot.setOnTouchListener { v, event ->
            val cardTop = dialogBinding.placeTextCard.top
            if (cardTop > 0 && event.y >= cardTop) return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> true
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!dialogBinding.placeTextColorPopup.isVisible &&
                        !dialogBinding.placeTextSizePopup.isVisible &&
                        !dialogBinding.placeTextStylePopup.isVisible
                    ) {
                        val relX = (event.x - dialogBinding.placeTextRoot.paddingLeft).toInt()
                        val relY = (event.y - dialogBinding.placeTextRoot.paddingTop).toInt()
                        val boxH = dialogBinding.placeTextEditorBox.height.takeIf { it > 0 } ?: minBoxHeightPx
                        userDesiredLeft = relX - (18f * density).toInt()
                        userDesiredTop = relY - (boxH / 2).coerceAtLeast((20f * density).toInt())
                        stickAboveToolbar = false
                        userMovedBox = true
                        applyEditorBoxLayout()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (closeAllPopups()) {
                        v.performClick()
                        return@setOnTouchListener true
                    }
                    val relX = (event.x - dialogBinding.placeTextRoot.paddingLeft).toInt()
                    val relY = (event.y - dialogBinding.placeTextRoot.paddingTop).toInt()
                    val boxH = dialogBinding.placeTextEditorBox.height.takeIf { it > 0 } ?: minBoxHeightPx
                    userDesiredLeft = relX - (18f * density).toInt()
                    userDesiredTop = relY - (boxH / 2).coerceAtLeast((20f * density).toInt())
                    stickAboveToolbar = false
                    userMovedBox = true
                    applyEditorBoxLayout()
                    dialogBinding.placeTextInput.requestFocus()
                    dialog.window?.let { w ->
                        WindowCompat.getInsetsController(w, dialogBinding.placeTextInput).show(WindowInsetsCompat.Type.ime())
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        dialogBinding.btnTextCancel.setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            handler.removeCallbacks(validate)
            val raw = dialogBinding.placeTextInput.text?.toString().orEmpty()
            if (render(raw)) {
                onAccept(raw, resolveEditorBoxPagePoint(), userMovedBox)
                dialog.dismiss()
            }
        }
        dialog.setOnShowListener {
            render(dialogBinding.placeTextInput.text?.toString().orEmpty())
            applyEditorBoxLayout()
            val input = dialogBinding.placeTextInput
            input.requestFocus()
            input.post {
                dialog.window?.let { w -> WindowCompat.getInsetsController(w, input).show(WindowInsetsCompat.Type.ime()) }
                applyEditorBoxLayout()
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(validate) }
        dialog.show()
        trackDialog(dialog)
    }

    /**
     * Єдине збереження. Незашифрований документ — як у 3d: ОДИН applyEdits у той самий URI. Зашифрований — лише нова
     * копія (R15): попередження → CreateDocument → applyEdits(outputUri = копія, password = введений пароль або null).
     * Після успіху незбережене очищується; при Error усе лишається, повідомлення містить userMessage.
     */
    private fun savePending(onSuccess: () -> Unit = {}) {
        if (savingPlace) return
        documentUri ?: return
        val request = buildSaveRequest()
        if (request.markups.isEmpty() && request.formUpdates.isEmpty()) {
            // Лише записи без поля у файлі (застарілі) — зберігати нічого.
            if (viewModel.hasUnsaved.value) viewModel.clearAllPending()
            onSuccess()
            return
        }
        lifecycleScope.launch {
            val access = obtainAccess() ?: return@launch
            when (EditAccessModel.saveTarget(access, request.markups.isNotEmpty(), request.formUpdates.isNotEmpty())) {
                EditAccessModel.SaveTarget.SAME_URI -> runSave(documentUri ?: return@launch, copy = false, onSuccess = onSuccess)
                EditAccessModel.SaveTarget.NEW_COPY -> confirmCopy(onSuccess)
                EditAccessModel.SaveTarget.DENIED -> showMessage(getString(R.string.place_blocked_rights), long = true)
                EditAccessModel.SaveTarget.UNAVAILABLE -> showMessage(getString(R.string.place_blocked_unreadable), long = true)
            }
        }
    }

    private fun buildSaveRequest(): SaveLogic.Request<PageMarkup> {
        val items = viewModel.editor.value.items
        val solidBitmaps = HashMap<Int, Bitmap>()
        return SaveLogic.request(
            items.flatMap { item ->
                PlaceMarkupLogic.toPageMarkups(
                    p = item,
                    signatureBitmap = { id -> viewModel.signatureBitmaps[id] },
                    measureTextWidthPt = { line, fs -> measureTextPt(line, fs) },
                    bgBitmapFactory = { color ->
                        solidBitmaps.getOrPut(color) {
                            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                        }
                    }
                )
            },
            viewModel.formFields.value, viewModel.formEdits.value
        )
    }

    /** Дія після успішного збереження копії; тримається поки відкрито системний вибір місця (поворот його не губить, процес — так). */
    private var copyOnSuccess: (() -> Unit)? = null

    /** URI копії, який повернув наш CreateDocument на час поточного збереження. Лише в пам'яті: не пароль, не стан форм. */
    private var ownedCopyUri: Uri? = null

    private val createCopyLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        val onSuccess = copyOnSuccess
        copyOnSuccess = null
        when (CopyPick.resolve(uriChosen = out != null, awaitingFlag = viewModel.awaitingCopyLocation)) {
            CopyPickAction.CANCELLED -> viewModel.awaitingCopyLocation = false // нічого не втрачено, незбережене на місці
            CopyPickAction.SAVE -> {
                ownedCopyUri = out // Д1: лише цей URI (його створив наш CreateDocument) дозволено прибрати, якщо він лишиться порожнім
                lifecycleScope.launch {
                    try { saveIntoChosenCopy(out ?: return@launch, onSuccess ?: {}) } finally { viewModel.awaitingCopyLocation = false }
                }
            }
            CopyPickAction.DISCARD_EMPTY -> {
                viewModel.awaitingCopyLocation = false
                ownedCopyUri = out
                lifecycleScope.launch { discardUnfinishedCopy(out ?: return@launch) }
            }
        }
    }

    /**
     * Результат вибору місця, коли стан є. Після am kill процес новий: чекаємо відновлення незбереженого, за потреби
     * просимо пароль нашим вікном (у пам'яті його вже нема), потім зберігаємо в цей URI. Скасування пароля, права, що
     * зникли, або порожній запит — порожній файл прибираємо за правилами Д1.
     */
    private suspend fun saveIntoChosenCopy(out: Uri, onSuccess: () -> Unit) {
        if (!PendingWait.awaitLoaded(viewModel.pendingLoaded)) {
            // стан не відновився вчасно: не зависаємо, прибираємо порожній файл і чесно повідомляємо
            discardUnfinishedCopy(out)
            return
        }
        // Після am kill переглядач ще показує свій запит пароля (viewerOpened=false): це не «не відкривається», а «потрібен пароль».
        val access = obtainAccess(assumeViewerOpened = true)
        val target = access?.let { EditAccessModel.saveTarget(it, viewModel.editor.value.items.isNotEmpty(), viewModel.formEdits.value.isNotEmpty()) }
        if (access == null || (target != EditAccessModel.SaveTarget.NEW_COPY && target != EditAccessModel.SaveTarget.SAME_URI)) {
            discardUnfinishedCopy(out)
            return
        }
        val uri = documentUri
        if (uri != null && viewModel.formFields.value.isEmpty() && viewModel.formEdits.value.isNotEmpty()) {
            // після перезапуску поля форм ще не прочитані: без них значення форм не потраплять у копію
            val fields = withContext(Dispatchers.IO) {
                try { formEngine.extractFormFields(applicationContext, uri, viewModel.documentPassword) }
                catch (e: Throwable) { if (e is CancellationException) throw e; emptyList() }
            }
            viewModel.setFormFields(fields)
        }
        val request = buildSaveRequest()
        if (request.markups.isEmpty() && request.formUpdates.isEmpty()) {
            discardUnfinishedCopy(out)
            return
        }
        runSave(out, copy = true, onSuccess = onSuccess)
    }

    /** Збереження в обраний URI не відбулося: прибрати порожній файл (Д1) і чесно повідомити. */
    private fun discardUnfinishedCopy(out: Uri) {
        val deleted = removeEmptyCopy(out, copy = true)
        ownedCopyUri = null
        showNotices(null, SaveNotices.forUnfinished(deleted))
    }

    private val pickImageForReaderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            SafUtils.takePersistableUriPermissionSafe(this, uri)
            handleImagePickedForReader(uri)
        }
    }

    private val scanDocumentForReaderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUri = scanResult?.pages?.firstOrNull()?.imageUri ?: scanResult?.pdf?.uri
            if (pageUri != null) {
                handleScanResultForReader(pageUri)
            }
        }
    }

    private val scanFallbackForReaderLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleScanResultForReader(uri)
        }
    }

    private fun startScanForReader() {
        try {
            val options = DocumentScannerLogic.buildScannerOptions(pageLimit = 1)
            val scanner = GmsDocumentScanning.getClient(options)
            scanner.getStartScanIntent(this)
                .addOnSuccessListener { intentSender ->
                    scanDocumentForReaderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    scanFallbackForReaderLauncher.launch("image/*")
                }
        } catch (_: Throwable) {
            scanFallbackForReaderLauncher.launch("image/*")
        }
    }

    private fun handleImagePickedForReader(uri: Uri) {
        lifecycleScope.launch {
            try {
                showLoadingOverlay(getString(R.string.saving_annotations))
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        val sampleSize = calculateInSampleSize(options, 2048, 2048)
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    }
                }
                hideLoadingOverlay()
                if (bitmap == null) {
                    showError(getString(R.string.error_title))
                    return@launch
                }

                val currPage = (viewerFragment?.currentPageIndex ?: 0).coerceIn(0, (loadedPageCount - 1).coerceAtLeast(0))
                placeImageOnPage(bitmap, currPage)
            } catch (e: Exception) {
                hideLoadingOverlay()
                showError(e.localizedMessage ?: getString(R.string.error_title))
            }
        }
    }

    private fun handleScanResultForReader(imageUri: Uri) {
        lifecycleScope.launch {
            try {
                showLoadingOverlay(getString(R.string.scan_processing))
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(imageUri)?.use { stream ->
                        val bytes = stream.readBytes()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        val sampleSize = calculateInSampleSize(options, 2048, 2048)
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    }
                }
                hideLoadingOverlay()
                if (bitmap == null) {
                    showError(getString(R.string.error_title))
                    return@launch
                }

                // Додаємо нову чисту сторінку в документ і розміщуємо на ній скан
                addNewBlankPage { success ->
                    if (success) {
                        val newPageIndex = (loadedPageCount - 1).coerceAtLeast(0)
                        placeImageOnPage(bitmap, newPageIndex)
                        viewerFragment?.bridge?.restorePage(newPageIndex)
                        showMessage(getString(R.string.scan_added_to_new_page), long = false)
                    }
                }
            } catch (e: Exception) {
                hideLoadingOverlay()
                showError(e.localizedMessage ?: getString(R.string.error_title))
            }
        }
    }

    private fun placeImageOnPage(bitmap: Bitmap, pageIndex: Int) {
        val page = viewerFragment?.bridge?.pageSizePt(pageIndex)
        val pageWidth = page?.widthPt ?: 595.28f
        val pageHeight = page?.heightPt ?: 842f
        val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
        // За замовчуванням розміщувати зображення на всю сторінку, зберігаючи пропорції
        val fitWidth = if (aspect > 0f) {
            minOf(pageWidth, pageHeight / aspect)
        } else {
            pageWidth
        }
        val clampedWidth = PlaceMarkupLogic.clampSize(PlaceTool.IMAGE, fitWidth, 0f, 0f, aspect, page)
        val clampedHeight = clampedWidth * aspect
        val x = ((pageWidth - clampedWidth) / 2f).coerceAtLeast(0f)
        val y = ((pageHeight - clampedHeight) / 2f).coerceAtLeast(0f)

        val itemId = UUID.randomUUID().toString()
        val item = PendingMarkup(
            id = itemId,
            tool = PlaceTool.IMAGE,
            pageIndex = pageIndex,
            x = x,
            y = y,
            text = "",
            sizePt = clampedWidth,
            color = 0,
            aspect = aspect,
            rotationDeg = 0
        )
        viewModel.originalBitmaps[itemId] = bitmap
        viewModel.signatureBitmaps[itemId] = bitmap
        viewModel.addPending(item)
        if (!viewModel.placeModeActive) {
            enterPlaceMode()
        }
        viewModel.placeTool = PlaceTool.HAND
        viewModel.edit { PlaceEditor.select(it, itemId) }
        renderEditor(viewModel.editor.value)
    }

    private fun rotateSelectedImage(deltaDeg: Int) {
        val sel = viewModel.editor.value.selected ?: return
        if (sel.tool != PlaceTool.IMAGE) return
        val page = viewerFragment?.bridge?.pageSizePt(sel.pageIndex) ?: return
        val original = viewModel.originalBitmaps[sel.id] ?: viewModel.signatureBitmaps[sel.id] ?: return
        val nextDeg = ImageRotationHelper.normalize(sel.rotationDeg + deltaDeg)
        val rotatedBmp = ImageRotationHelper.rotate(original, nextDeg)
        viewModel.signatureBitmaps[sel.id] = rotatedBmp
        viewModel.edit { PlaceEditor.rotate(it, sel.id, deltaDeg, page) }
    }

    private fun fitSelectedImageToPage() {
        val sel = viewModel.editor.value.selected ?: return
        if (sel.tool != PlaceTool.IMAGE) return
        val page = viewerFragment?.bridge?.pageSizePt(sel.pageIndex) ?: return
        viewModel.edit { PlaceEditor.fitToPage(it, sel.id, page) }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun showMoveToPageDialog(sel: PendingMarkup) {
        val totalPages = loadedPageCount.coerceAtLeast(1)
        val items = ArrayList<String>()
        for (i in 0 until totalPages) {
            val label = getString(R.string.page_number_item, i + 1)
            val currentMark = if (i == sel.pageIndex) " (поточна)" else ""
            items.add(label + currentMark)
        }
        items.add(getString(R.string.place_create_new_page_option))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.place_move_to_page_title)
            .setItems(items.toTypedArray()) { _, which ->
                if (which < totalPages) {
                    val targetPage = viewerFragment?.bridge?.pageSizePt(which)
                    viewModel.edit {
                        PlaceEditor.move(PlaceEditor.select(it, sel.id), sel.id, which, sel.x, sel.y, targetPage)
                    }
                    viewerFragment?.bridge?.restorePage(which)
                } else {
                    addNewBlankPage { success ->
                        if (success) {
                            val newPageIndex = (loadedPageCount - 1).coerceAtLeast(0)
                            val targetPage = viewerFragment?.bridge?.pageSizePt(newPageIndex)
                            viewModel.edit {
                                PlaceEditor.move(PlaceEditor.select(it, sel.id), sel.id, newPageIndex, sel.x, sel.y, targetPage)
                            }
                            viewerFragment?.bridge?.restorePage(newPageIndex)
                            showMessage(getString(R.string.page_added_and_moved), long = false)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showTracked()
    }

    private val insertFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            appendSelectedFiles(uris)
        }
    }

    private fun appendSelectedFiles(uris: List<Uri>) {
        val docUri = documentUri ?: return
        uris.forEach { SafUtils.takePersistableUriPermissionSafe(this, it) }

        val doAppend = {
            lifecycleScope.launch {
                showLoadingOverlay(getString(R.string.saving_annotations))
                val engine = com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine()
                val result = engine.appendFiles(
                    context = this@ReaderActivity,
                    sourceUri = docUri,
                    additionalUris = uris,
                    outputUri = docUri
                )
                hideLoadingOverlay()
                when (result) {
                    is com.savoj.volumpdf.core.pdf.model.PdfOperationResult.Success -> {
                        showMessage(getString(R.string.files_added_successfully), long = false)
                        reloadViewerDocument()
                    }
                    is com.savoj.volumpdf.core.pdf.model.PdfOperationResult.Error -> {
                        showError(result.userMessage)
                    }
                    else -> Unit
                }
            }
        }

        if (viewModel.hasUnsaved.value) {
            savePending { doAppend() }
        } else {
            doAppend()
        }
    }

    private fun addNewBlankPage(onComplete: ((Boolean) -> Unit)? = null) {
        val docUri = documentUri ?: return

        val doAdd = {
            lifecycleScope.launch {
                showLoadingOverlay(getString(R.string.saving_annotations))
                val engine = com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine()
                val result = engine.appendBlankPages(
                    context = this@ReaderActivity,
                    sourceUri = docUri,
                    count = 1,
                    outputUri = docUri
                )
                hideLoadingOverlay()
                when (result) {
                    is com.savoj.volumpdf.core.pdf.model.PdfOperationResult.Success -> {
                        showMessage(getString(R.string.page_added_successfully), long = false)
                        reloadViewerDocument()
                        onComplete?.invoke(true)
                    }
                    is com.savoj.volumpdf.core.pdf.model.PdfOperationResult.Error -> {
                        showError(result.userMessage)
                        onComplete?.invoke(false)
                    }
                    else -> onComplete?.invoke(false)
                }
            }
        }

        if (viewModel.hasUnsaved.value) {
            savePending { doAdd() }
        } else {
            doAdd()
        }
    }

    private fun showToolsBottomSheet() {
        val sheet = ReaderToolsBottomSheetDialogFragment.newInstance()
        sheet.onActionSelected = { action ->
            val uri = documentUri
            when (action) {
                ReaderToolAction.ADD_PAGE -> addNewBlankPage()
                ReaderToolAction.ADD_IMAGE -> pickImageForReaderLauncher.launch(arrayOf("image/*"))
                ReaderToolAction.INSERT_FILE -> insertFilesLauncher.launch(arrayOf("application/pdf", "image/*"))
                ReaderToolAction.ORGANIZE_PAGES -> {
                    if (uri != null) com.savoj.volumpdf.ui.organizer.PageOrganizerActivity.start(this, uri)
                }
                ReaderToolAction.ROTATE -> {
                    if (uri != null) startActivity(com.savoj.volumpdf.ui.tools.ToolsActivity.createIntent(this, com.savoj.volumpdf.ui.tools.ToolType.ROTATE, uri))
                }
                ReaderToolAction.DELETE_PAGES -> {
                    if (uri != null) startActivity(com.savoj.volumpdf.ui.tools.ToolsActivity.createIntent(this, com.savoj.volumpdf.ui.tools.ToolType.DELETE_PAGES, uri))
                }
                ReaderToolAction.SPLIT -> {
                    if (uri != null) startActivity(com.savoj.volumpdf.ui.tools.ToolsActivity.createIntent(this, com.savoj.volumpdf.ui.tools.ToolType.SPLIT, uri))
                }
                ReaderToolAction.COMPRESS -> {
                    if (uri != null) startActivity(com.savoj.volumpdf.ui.tools.ToolsActivity.createIntent(this, com.savoj.volumpdf.ui.tools.ToolType.COMPRESS, uri))
                }
                ReaderToolAction.WATERMARK -> {
                    if (uri != null) startActivity(com.savoj.volumpdf.ui.tools.ToolsActivity.createIntent(this, com.savoj.volumpdf.ui.tools.ToolType.WATERMARK, uri))
                }
                ReaderToolAction.PAGE_NUMBERS -> {
                    if (uri != null) startActivity(com.savoj.volumpdf.ui.tools.ToolsActivity.createIntent(this, com.savoj.volumpdf.ui.tools.ToolType.PAGE_NUMBERS, uri))
                }
                ReaderToolAction.MERGE -> {
                    if (uri != null) startActivity(com.savoj.volumpdf.ui.tools.ToolsActivity.createIntent(this, com.savoj.volumpdf.ui.tools.ToolType.MERGE, uri))
                }
                ReaderToolAction.SCAN -> startScanForReader()
                ReaderToolAction.PRINT -> printCurrentDocument()
                ReaderToolAction.SHARE -> shareCurrentDocument()
                ReaderToolAction.DETAILS -> showDetailsDialog()
            }
        }
        sheet.show(supportFragmentManager, ReaderToolsBottomSheetDialogFragment.TAG)
    }

    private fun printCurrentDocument() {
        val uri = documentUri ?: return
        try {
            val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
            if (printManager != null) {
                val title = documentTitle ?: getString(R.string.reader_title)
                val printAdapter = com.savoj.volumpdf.util.PdfPrintDocumentAdapter(this, uri, title)
                printManager.print(title, printAdapter, null)
            }
        } catch (e: Exception) {
            showError(e.localizedMessage ?: getString(R.string.error_title))
        }
    }

    private fun confirmCopy(onSuccess: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.copy_warning_title)
            .setMessage(R.string.copy_warning_message)
            .setPositiveButton(R.string.copy_save) { _, _ -> startCopyPick(onSuccess) }
            .setNegativeButton(R.string.action_cancel, null)
            .showTracked()
    }

    private fun startCopyPick(onSuccess: () -> Unit) {
        copyOnSuccess = onSuccess
        viewModel.awaitingCopyLocation = true // переживе am kill; після будь-якого завершення скидається
        createCopyLauncher.launch(CopyNaming.suggestedName(documentTitle))
    }

    /** Записує зміни: [copy] = false — у документ ([output] = той самий URI); true — у нову копію [output]. */
    private suspend fun runSave(output: Uri, copy: Boolean, onSuccess: () -> Unit) {
        if (savingPlace) return
        val source = documentUri ?: return
        val request = buildSaveRequest()
        savingPlace = true
        binding.markupOverlay.editingEnabled = false
        showLoadingOverlay(getString(R.string.saving_annotations))
        try {
            val password = if (copy) viewModel.documentPassword else null
            val outcome = SaveLogic.run(request) { markups, formUpdates ->
                markupEngine.applyEdits(
                    context = applicationContext,
                    sourceUri = source,
                    markups = markups,
                    formUpdates = formUpdates,
                    outputUri = output,
                    password = password
                )
            }
            when (outcome) {
                SaveOutcome.Saved -> {
                    if (copy) {
                        SafUtils.takePersistableUriPermissionSafe(this, output)
                        viewModel.clearAllPending() // незбережене оригіналу: його вже записано в копію
                        val savedNotices = SaveNotices.forCopySaved(passwordUsed = password != null)
                        if (NoticePresentation.deferViewerUntilDismissed(savedNotices)) {
                            // Д2: переглядач попросить пароль системним вікном і закрив би підказку; спершу діалог, потім копія.
                            showNoticeDialog(withNotices(null, savedNotices)) {
                                if (!isDestroyed && !isFinishing) { switchToDocument(output); onSuccess() }
                            }
                            return
                        }
                        switchToDocument(output) // копія — поточний документ (читач, «Останні»)
                        showNotices(null, savedNotices)
                    } else {
                        reloadViewerDocument()
                        viewModel.clearAllPending()
                        checkForFormFields(source) // поля читаємо знову, бейдж оновлюється
                        showMessage(getString(R.string.place_saved), long = false)
                    }
                    onSuccess()
                }
                is SaveOutcome.Failed -> {
                    val deleted = removeEmptyCopy(output, copy)
                    if (!copy && SaveFailureKind.isNoWriteAccess(outcome.message)) {
                        showReadOnlyOffer(onSuccess)
                    } else {
                        showFailure(outcome.message.ifBlank { getString(R.string.error_title) }, SaveNotices.forFailed(deleted))
                    }
                }
                SaveOutcome.NothingToSave -> {
                    val deleted = removeEmptyCopy(output, copy)
                    val notices = SaveNotices.forNothingToSave(copy, deleted)
                    if (notices.isNotEmpty()) showNotices(null, notices)
                    onSuccess()
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val deleted = removeEmptyCopy(output, copy)
            if (!copy && SaveFailureKind.isNoWriteAccess(e.localizedMessage, e)) {
                showReadOnlyOffer(onSuccess)
            } else {
                showFailure(e.localizedMessage ?: getString(R.string.error_title), SaveNotices.forFailed(deleted))
            }
        } finally {
            if (copy) ownedCopyUri = null
            hideLoadingOverlay()
            savingPlace = false
            binding.markupOverlay.editingEnabled = viewModel.placeModeActive
        }
    }

    /** Д2: документ відкрито лише на читання. Пояснення простою мовою й пропозиція зберегти копію (незбережене лишається). */
    private fun showReadOnlyOffer(onSuccess: () -> Unit) {
        if (isDestroyed || isFinishing) return
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.save_readonly_message)
            .setPositiveButton(R.string.copy_save) { _, _ -> startCopyPick(onSuccess) }
            .setNegativeButton(R.string.action_cancel, null)
            .showTracked()
    }

    private fun noticeText(notice: SaveNotice): String = getString(
        when (notice) {
            SaveNotice.NO_CHANGES_COPY_NOT_CREATED -> R.string.save_no_changes_copy_not_created
            SaveNotice.NO_CHANGES -> R.string.save_no_changes
            SaveNotice.EMPTY_FILE_LEFT -> R.string.save_empty_file_left
            SaveNotice.SAVE_NOT_COMPLETED -> R.string.save_not_completed
            SaveNotice.COPY_SAVED -> R.string.place_copy_saved
            SaveNotice.COPY_SAVED_PASSWORD_HINT -> R.string.place_copy_saved_password_hint
        }
    )

    /** Основний текст (якщо є) і повідомлення про підсумок: одним повідомленням, щоб наступне не перекрило попереднє. */
    private fun withNotices(base: String?, notices: List<SaveNotice>): String =
        (listOfNotNull(base) + NoticePresentation.visible(notices).map { noticeText(it) }).joinToString("\n")

    /** Підсумок збереження: діалог з «ОК» для всього, окрім простого «Копію збережено» (його показує короткий Snackbar). */
    private fun showNotices(base: String?, notices: List<SaveNotice>) {
        val text = withNotices(base, notices)
        when (NoticePresentation.displayFor(notices)) {
            NoticeDisplay.DIALOG -> showNoticeDialog(text)
            NoticeDisplay.SNACKBAR -> showMessage(text, long = false)
        }
    }

    /** Збій збереження: текст помилки + додатки; з додатками (файл лишився) це діалог, без них — звичайний Snackbar помилки. */
    private fun showFailure(message: String, notices: List<SaveNotice>) {
        if (notices.isEmpty()) showError(message) else showNoticeDialog(withNotices(message, notices))
    }

    /** Діалог, який не зникає без дії користувача й не ховається під системним запитом пароля переглядача. */
    private fun showNoticeDialog(text: String, onDismiss: (() -> Unit)? = null) {
        if (isDestroyed || isFinishing) return
        val builder = MaterialAlertDialogBuilder(this)
            .setMessage(text)
            .setPositiveButton(R.string.action_ok, null)
        if (onDismiss != null) builder.setOnDismissListener { onDismiss() }
        builder.showTracked()
    }

    /**
     * Д1: збій збереження копії не має лишати порожній файл у вибраній папці. Видаляємо лише URI, який повернув наш
     * CreateDocument у цьому потоці ([ownedCopyUri]), і лише якщо розмір відомо й він 0.
     */
    private fun removeEmptyCopy(output: Uri, copy: Boolean): DeleteResult {
        val ours = copy && output == ownedCopyUri
        if (!EmptyCopyCleanup.shouldDelete(copy, SafUtils.queryFileSizeOrNull(this, output), createdByThisFlow = ours)) {
            return DeleteResult.NOT_ATTEMPTED
        }
        return if (SafUtils.deleteDocumentQuietly(this, output)) DeleteResult.DELETED else DeleteResult.FAILED
    }

    /** Копія стає поточним документом: URI, назва, запис у «Останніх», переглядач (з відновленням позиції), поля форм. */
    private fun switchToDocument(newUri: Uri) {
        documentUri = newUri
        documentTitle = SafUtils.queryDisplayName(this, newUri)
        intent.putExtra(EXTRA_DOCUMENT_TITLE, documentTitle)
        binding.toolbar.title = documentTitle
        initialPageRestored = true // позицію копії відновлює reloadViewerDocument() нижче
        viewModel.initDocument(newUri, documentTitle) // запис у «Останніх» і порожнє незбережене нового ключа
        reloadViewerDocument()
        checkForFormFields(newUri)
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
        if (binding.placeToolBar.isVisible) snackbar.setAnchorView(binding.placeToolBar)
        snackbar.show()
        errorSnackbar = snackbar
        return snackbar
    }

    private fun showMessage(text: String, long: Boolean, indefinite: Boolean = false): Snackbar {
        val snackbar = Snackbar.make(binding.root, text, if (indefinite) Snackbar.LENGTH_INDEFINITE else if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
        if (binding.placeToolBar.isVisible) snackbar.setAnchorView(binding.placeToolBar)
        snackbar.show()
        return snackbar
    }

    private var lastEdges = SystemEdges.NONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Орієнтація: кількість колонок кольорів і ширина картки параметрів. */
    private fun applyPlacePanelOrientation() {
        if (!::placeBar.isInitialized) return
        placeBar.applyGridColumns()
        updatePlacePanelWidth()
    }

    private fun updatePlacePanelWidth() {
        if (!::placeBar.isInitialized) return
        placeBar.updateCardWidth(resources.displayMetrics.widthPixels - lastEdges.left - lastEdges.right)
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
            // Нижній рядок інструментів лежить над системною навігацією (жести й три кнопки).
            binding.placeToolBar.setPadding(0, 0, 0, e.bottom)
            updatePlacePanelWidth()
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPlacePanelOrientation()
        val changed = lastConfig?.diff(newConfig) ?: 0
        lastConfig = Configuration(newConfig)
        if (ConfigRecreateLogic.needsRecreate(changed)) {
            // Масштаб шрифту, тема, мова: ресурси треба перечитати (recreate), але не поки відкрито вікно/картку,
            // інакше вони зникають разом із введеним текстом (Д1). Відкладаємо до їх закриття.
            configRecreatePending = true
            scheduleDeferredRecreate()
        }
    }

    private val trackedDialogs = ArrayList<java.lang.ref.WeakReference<android.app.Dialog>>()
    private var lastConfig: Configuration? = null
    private var configRecreatePending = false
    private var deferredRecreateJob: Job? = null

    private fun trackDialog(d: android.app.Dialog) {
        trackedDialogs.removeAll { it.get()?.isShowing != true }
        trackedDialogs.add(java.lang.ref.WeakReference(d))
    }

    private fun MaterialAlertDialogBuilder.showTracked(): AlertDialog = show().also { trackDialog(it) }

    /** Є відкрите вікно чи картка, яка не переживе recreate() разом зі своїм станом. */
    private fun isUiBusyForRecreate(): Boolean =
        trackedDialogs.any { it.get()?.isShowing == true } ||
            (::placeBar.isInitialized && placeBar.isCardOpen) ||
            supportFragmentManager.fragments.any { it is androidx.fragment.app.DialogFragment && it.isAdded }

    private fun scheduleDeferredRecreate() {
        if (deferredRecreateJob?.isActive == true) return
        deferredRecreateJob = lifecycleScope.launch {
            while (configRecreatePending && isUiBusyForRecreate()) delay(ConfigRecreateLogic.POLL_MS)
            if (configRecreatePending && !isFinishing && !isDestroyed) {
                configRecreatePending = false
                recreate()
            }
        }
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
                fragment.bridge.viewport?.let { v -> viewModel.updatePageProgress(v.currentPage) }
                // Попередні копії видаляємо лише після того, як нова завантажена (або спроба провалена).
                lifecycleScope.launch(Dispatchers.IO) {
                    ViewerCopyCache.purge(cacheDir, keep = copy.file)
                }
            }
        }
    }

    override fun onDestroy() {
        viewerFragment?.bridge?.removeViewportListener(progressViewportListener)
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
                    if (doc != null) {
                        maybeRestoreInitialPage()
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val place = viewModel.placeModeActive
        menu.findItem(R.id.action_place_save)?.isVisible = place && viewModel.hasUnsaved.value
        menu.findItem(R.id.action_place_done)?.isVisible = place
        // Режим читання: окремою іконкою; у режимі «Редагувати» місце потрібне для «Зберегти»/«Готово» — тоді в меню ⋮.
        menu.findItem(R.id.action_reading_theme)?.isVisible = !place
        menu.findItem(R.id.action_reading_theme_overflow)?.isVisible = place
        menu.findItem(R.id.action_place)?.isVisible = !place
        menu.findItem(R.id.action_forms)?.isVisible = formFieldCount > 0
        return super.onPrepareOptionsMenu(menu)
    }

    /** «+»: додати сторінку, зображення, файл або скан. Дії ті самі, що й у шторці інструментів. */
    private fun showAddMenu() {
        val anchor = binding.toolbar.findViewById<View>(R.id.action_add) ?: binding.toolbar
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.place_add_page_short)
        popup.menu.add(0, 2, 1, R.string.action_add_image)
        popup.menu.add(0, 3, 2, R.string.tool_insert_file)
        popup.menu.add(0, 4, 3, R.string.tool_scan_title)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> addNewBlankPage()
                2 -> pickImageForReaderLauncher.launch(arrayOf("image/*"))
                3 -> insertFilesLauncher.launch(arrayOf("application/pdf", "image/*"))
                4 -> startScanForReader()
            }
            true
        }
        popup.show()
    }

    /** Власний колір: три слайдери R, G, B з попереднім переглядом; результат — RGB без альфи. */
    private fun showCustomColorDialog(currentRgb: Int, onPicked: (Int) -> Unit) {
        val pad = dp(20)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, 0)
        }
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        }
        box.addView(preview)
        val values = intArrayOf((currentRgb shr 16) and 0xFF, (currentRgb shr 8) and 0xFF, currentRgb and 0xFF)
        fun refresh() {
            preview.setBackgroundColor(0xFF000000.toInt() or (values[0] shl 16) or (values[1] shl 8) or values[2])
        }
        val labels = intArrayOf(R.string.color_red, R.string.color_green, R.string.color_blue)
        for (i in 0..2) {
            box.addView(android.widget.TextView(this).apply {
                setText(labels[i])
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                setPadding(0, dp(8), 0, 0)
            })
            box.addView(com.google.android.material.slider.Slider(this).apply {
                valueFrom = 0f
                valueTo = 255f
                stepSize = 1f
                value = values[i].toFloat()
                contentDescription = getString(labels[i])
                addOnChangeListener { _, v, _ -> values[i] = v.toInt(); refresh() }
            })
        }
        refresh()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.place_custom_color_title)
            .setView(box)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                onPicked((values[0] shl 16) or (values[1] shl 8) or values[2])
            }
            .setNegativeButton(R.string.action_cancel, null)
            .showTracked()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_place_save -> {
                savePending()
                true
            }
            R.id.action_place_done -> {
                leavePlaceThen { }
                true
            }
            R.id.action_add -> {
                showAddMenu()
                true
            }
            R.id.action_forms -> {
                openFillFormDialog()
                true
            }
            R.id.action_reading_theme_overflow -> {
                showReadingThemeDialog()
                true
            }
            android.R.id.home -> {
                // M2: «вгору» — той самий вихід, що й «Назад»: за незбережених змін (об'єкти чи форми) діалог.
                exitReaderThen { finish() }
                true
            }
            R.id.action_place -> {
                enterPlaceMode()
                true
            }
            R.id.action_search -> {
                toggleSearch()
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
            R.id.action_reading_theme -> {
                showReadingThemeDialog()
                true
            }
            R.id.action_tools -> {
                showToolsBottomSheet()
                true
            }
            R.id.action_details -> {
                showDetailsDialog()
                true
            }
            R.id.action_organize_pages -> {
                val uri = documentUri ?: return true
                com.savoj.volumpdf.ui.organizer.PageOrganizerActivity.start(this, uri)
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
            .apply {
                val lastRead = doc?.lastPageRead ?: 0
                if (pages > 0 && lastRead > 0) {
                    val displayPage = (lastRead + 1).coerceIn(1, pages)
                    append(getString(R.string.document_page_progress, displayPage, pages)).append("\n")
                }
            }
            .append(getString(R.string.details_size, SafUtils.formatFileSize(size))).append("\n")
            .append(getString(R.string.details_last_opened, SafUtils.formatTimestamp(lastOpened))).append("\n\n")
            .append(getString(R.string.details_uri, uri.toString()))
            .toString()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_details_title)
            .setMessage(msg)
            .setPositiveButton(R.string.action_ok, null)
            .showTracked()
    }

    private fun showReadingThemeDialog() {
        val items = arrayOf(
            getString(R.string.reading_theme_day),
            getString(R.string.reading_theme_night),
            getString(R.string.reading_theme_sepia)
        )
        val currentTheme = readingPreferences.theme
        val currentIndex = currentTheme.ordinal

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reading_theme_title)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                val chosen = ReadingTheme.entries.getOrElse(which) { ReadingTheme.DAY }
                if (chosen != readingPreferences.theme) {
                    readingPreferences.theme = chosen
                    applyReadingTheme(chosen)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .showTracked()
    }

    private fun applyReadingTheme(theme: ReadingTheme) {
        val paint = ReadingThemeLogic.createPaint(theme)
        if (paint != null) {
            binding.pdfViewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            binding.pdfViewerContainer.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        val bgColor = ReadingThemeLogic.containerBackgroundColor(theme)
        binding.viewerWrapper.setBackgroundColor(bgColor)
        binding.pdfViewerContainer.setBackgroundColor(bgColor)
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
