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
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
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
import com.savoj.volumpdf.core.pdf.PdfBoxFormEngine
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FreeTextData
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.databinding.ActivityReaderBinding
import com.savoj.volumpdf.util.SafUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModels()

    private var documentUri: Uri? = null
    private var documentTitle: String? = null
    private var viewerFragment: VolumPdfViewerFragment? = null

    private val annotationEngine: AnnotationEngine = PdfBoxAnnotationEngine()
    private val formEngine: FormEngine = PdfBoxFormEngine()
    private var detectedFormFields: List<FormField> = emptyList()

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
                    openFillFormDialog()
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
                R.id.action_add_text -> {
                    if (isDrawingMode) toggleDrawingMode(false)
                    openAddTextDialog()
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

    private fun openAddTextDialog() {
        val dialog = AddTextBottomSheetDialogFragment.newInstance()
        dialog.onTextConfirmed = { text, fontSize, color ->
            insertTextToDocument(text, fontSize, color)
        }
        dialog.show(supportFragmentManager, AddTextBottomSheetDialogFragment.TAG)
    }

    private fun insertTextToDocument(text: String, fontSize: Float, color: Int) {
        val uri = documentUri ?: return
        val pageIndex = viewerFragment?.currentPageIndex ?: 0

        showLoadingOverlay(getString(R.string.saving_annotations))

        lifecycleScope.launch {
            try {
                val (pageWidthPt, pageHeightPt) = getPageDimensions(uri, pageIndex)

                val textData = FreeTextData(
                    text = text,
                    x = 54f,
                    y = 72f,
                    fontSize = fontSize,
                    color = color
                )

                val result = annotationEngine.addFreeText(
                    context = applicationContext,
                    sourceUri = uri,
                    pageIndex = pageIndex,
                    textData = textData,
                    pageWidthPt = pageWidthPt,
                    pageHeightPt = pageHeightPt,
                    outputUri = uri
                )

                hideLoadingOverlay()

                when (result) {
                    is PdfOperationResult.Success -> {
                        reloadViewerDocument()
                        checkForFormFields(uri)
                        Snackbar.make(binding.root, R.string.text_success, Snackbar.LENGTH_SHORT).show()
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
                } else {
                    finish()
                }
                true
            }
            R.id.action_draw -> {
                toggleDrawingMode()
                true
            }
            R.id.action_signature -> {
                if (isDrawingMode) toggleDrawingMode(false)
                openSignatureDialog()
                true
            }
            R.id.action_add_text -> {
                if (isDrawingMode) toggleDrawingMode(false)
                openAddTextDialog()
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
