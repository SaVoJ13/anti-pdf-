package com.savoj.volumpdf.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.ads.AdMobAdsManager
import com.savoj.volumpdf.core.ads.AdsManager
import com.savoj.volumpdf.databinding.ActivityToolsBinding
import com.savoj.volumpdf.ui.common.SystemEdges
import com.savoj.volumpdf.ui.common.WindowInsetsHelper
import com.savoj.volumpdf.ui.reader.ReaderActivity
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.launch

class ToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolsBinding
    private val viewModel: ToolsViewModel by viewModels()
    private lateinit var adsManager: AdsManager

    private lateinit var toolType: ToolType
    private lateinit var filesAdapter: SelectedFileAdapter

    // SAF Launchers
    private val openMultiplePdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                SafUtils.takePersistableUriPermissionSafe(this, uri)
                SelectedFileItem(uri = uri, displayName = SafUtils.queryDisplayName(this, uri))
            }
            viewModel.addFiles(items)
        }
    }

    private val openSinglePdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            SafUtils.takePersistableUriPermissionSafe(this, uri)
            if (toolType == ToolType.ORGANIZE_PAGES) {
                com.savoj.volumpdf.ui.organizer.PageOrganizerActivity.start(this, uri)
                finish()
                return@registerForActivityResult
            }
            val item = SelectedFileItem(uri = uri, displayName = SafUtils.queryDisplayName(this, uri))
            viewModel.setSelectedFiles(listOf(item), this)
        }
    }

    private val openMultipleImagesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                SafUtils.takePersistableUriPermissionSafe(this, uri)
                SelectedFileItem(uri = uri, displayName = SafUtils.queryDisplayName(this, uri), isImage = true)
            }
            viewModel.addFiles(items)
        }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri: Uri? ->
        if (outputUri != null) {
            startOperation(outputUri)
        }
    }

    /** Edge-to-edge: тулбар під рядком стану, нижня панель дій над навігаційною панеллю/клавіатурою. */
    private fun applyWindowInsets() {
        val barPad = (16 * resources.displayMetrics.density).toInt()
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.toolsRoot) { _, insets ->
            val e = SystemEdges.from(insets)
            binding.toolsRoot.setPadding(e.left, 0, e.right, 0)
            WindowInsetsHelper.applyToolbarTopInset(binding.toolbar, e.top)
            binding.bottomSaveBar.setPadding(barPad, barPad, barPad, barPad + e.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.toolsRoot)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adsManager = AdMobAdsManager.getInstance(this)

        val toolName = intent.getStringExtra(EXTRA_TOOL_TYPE) ?: ToolType.MERGE.name
        toolType = try {
            ToolType.valueOf(toolName)
        } catch (_: Exception) {
            ToolType.MERGE
        }

        if (toolType == ToolType.CREATE_BLANK) {
            lifecycleScope.launch {
                try {
                    val file = com.savoj.volumpdf.util.DocumentCreationHelper.createBlankDocument(this@ToolsActivity)
                    com.savoj.volumpdf.util.DocumentCreationHelper.openNewDocument(this@ToolsActivity, file)
                } catch (e: Exception) {
                    Toast.makeText(this@ToolsActivity, e.localizedMessage ?: "Помилка створення PDF", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            return
        }

        if (toolType == ToolType.SCAN_DOCUMENT) {
            com.savoj.volumpdf.ui.scanner.ScanDocumentActivity.start(this)
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applyWindowInsets()

        setupToolUi()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // Прив'язка банера внизу ToolsActivity
        adsManager.bindAdaptiveBanner(this, binding.bannerContainer)

        // Якщо передано початковий файл (наприклад, з режиму перегляду), використовуємо його без запиту
        val sourceUriExtra: Uri? = androidx.core.content.IntentCompat.getParcelableExtra(intent, EXTRA_SOURCE_URI, Uri::class.java)
            ?: intent.getParcelableExtra(EXTRA_SOURCE_URI)

        if (sourceUriExtra != null && savedInstanceState == null && viewModel.selectedFiles.value.isEmpty()) {
            SafUtils.takePersistableUriPermissionSafe(this, sourceUriExtra)
            val displayName = SafUtils.queryDisplayName(this, sourceUriExtra)
            val item = SelectedFileItem(uri = sourceUriExtra, displayName = displayName)
            viewModel.setSelectedFiles(listOf(item), this)
        } else if (savedInstanceState == null && viewModel.selectedFiles.value.isEmpty()) {
            // Дії, для яких потрібен файл, одразу пропонують вибрати PDF при вході
            launchFilePickerForCurrentTool()
        }
    }

    private fun setupToolUi() {
        when (toolType) {
            ToolType.SCAN_DOCUMENT -> {
                binding.toolbar.setTitle(R.string.tool_scan_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_scan_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_scan_desc)
                binding.btnSelectFiles.setText(R.string.scan_empty_action)
            }
            ToolType.MERGE -> {
                binding.toolbar.setTitle(R.string.tool_merge_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_merge_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_merge_desc)
                binding.btnSelectFiles.setText(R.string.action_select_files)
                binding.recyclerViewSelectedFiles.visibility = View.VISIBLE
            }
            ToolType.SPLIT -> {
                binding.toolbar.setTitle(R.string.tool_split_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_split_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_split_desc)
                binding.btnSelectFiles.setText(R.string.action_select_file)
                binding.containerSplitParams.visibility = View.VISIBLE
            }
            ToolType.ORGANIZE_PAGES -> {
                binding.toolbar.setTitle(R.string.tool_organize_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_organize_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_organize_desc)
                binding.btnSelectFiles.setText(R.string.action_select_file)
            }
            ToolType.ROTATE -> {
                binding.toolbar.setTitle(R.string.tool_rotate_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_rotate_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_rotate_desc)
                binding.btnSelectFiles.setText(R.string.action_select_file)
                binding.containerRotateParams.visibility = View.VISIBLE
            }
            ToolType.DELETE_PAGES -> {
                binding.toolbar.setTitle(R.string.tool_delete_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_delete_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_delete_desc)
                binding.btnSelectFiles.setText(R.string.action_select_file)
                binding.containerDeleteParams.visibility = View.VISIBLE
            }
            ToolType.IMAGES_TO_PDF -> {
                binding.toolbar.setTitle(R.string.tool_images_to_pdf_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_images_to_pdf_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_images_to_pdf_desc)
                binding.btnSelectFiles.setText(R.string.action_select_images)
                binding.recyclerViewSelectedFiles.visibility = View.VISIBLE
            }
            ToolType.WATERMARK -> {
                binding.toolbar.setTitle(R.string.tool_watermark_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_watermark_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_watermark_desc)
                binding.btnSelectFiles.setText(R.string.action_select_file)
                binding.containerWatermarkParams.visibility = View.VISIBLE
            }
            ToolType.PAGE_NUMBERS -> {
                binding.toolbar.setTitle(R.string.tool_page_numbers_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_page_numbers_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_page_numbers_desc)
                binding.btnSelectFiles.setText(R.string.action_select_file)
                binding.containerPageNumbersParams.visibility = View.VISIBLE
            }
            ToolType.COMPRESS -> {
                binding.toolbar.setTitle(R.string.tool_compress_title)
                binding.tvToolHeaderTitle.setText(R.string.tool_compress_title)
                binding.tvToolHeaderDesc.setText(R.string.tool_compress_desc)
                binding.btnSelectFiles.setText(R.string.action_select_file)
                binding.containerCompressParams.visibility = View.VISIBLE
            }
            ToolType.CREATE_BLANK -> Unit
        }
    }

    private fun setupRecyclerView() {
        filesAdapter = SelectedFileAdapter { pos ->
            viewModel.removeFileAt(pos)
        }
        binding.recyclerViewSelectedFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSelectedFiles.adapter = filesAdapter
    }

    private fun setupListeners() {
        binding.btnSelectFiles.setOnClickListener {
            launchFilePickerForCurrentTool()
        }

        binding.chipCopy.setOnClickListener { binding.etWatermarkText.setText(getString(R.string.watermark_preset_copy)) }
        binding.chipSample.setOnClickListener { binding.etWatermarkText.setText(getString(R.string.watermark_preset_sample)) }
        binding.chipConfidential.setOnClickListener { binding.etWatermarkText.setText(getString(R.string.watermark_preset_confidential)) }
        binding.chipDraft.setOnClickListener { binding.etWatermarkText.setText(getString(R.string.watermark_preset_draft)) }

        binding.btnProcessAndSave.setOnClickListener {
            validateAndRequestOutputFile()
        }
    }

    private fun launchFilePickerForCurrentTool() {
        when (toolType) {
            ToolType.SCAN_DOCUMENT -> {
                com.savoj.volumpdf.ui.scanner.ScanDocumentActivity.start(this)
                finish()
            }
            ToolType.MERGE -> openMultiplePdfLauncher.launch(arrayOf("application/pdf"))
            ToolType.ORGANIZE_PAGES, ToolType.SPLIT, ToolType.ROTATE, ToolType.DELETE_PAGES, ToolType.WATERMARK, ToolType.PAGE_NUMBERS, ToolType.COMPRESS ->
                openSinglePdfLauncher.launch(arrayOf("application/pdf"))
            ToolType.IMAGES_TO_PDF -> openMultipleImagesLauncher.launch(arrayOf("image/jpeg", "image/png", "image/*"))
            ToolType.CREATE_BLANK -> Unit
        }
    }

    private fun validateAndRequestOutputFile() {
        val files = viewModel.selectedFiles.value
        val defaultName = when (toolType) {
            ToolType.MERGE -> {
                if (files.size < 2) {
                    showError(getString(R.string.error_select_min_two_files))
                    return
                }
                getString(R.string.filename_merged)
            }
            ToolType.SPLIT -> {
                if (files.isEmpty()) {
                    showError(getString(R.string.error_select_source_file))
                    return
                }
                val input = binding.etSplitPages.text?.toString().orEmpty()
                if (input.isBlank()) {
                    showError(getString(R.string.error_invalid_page_range))
                    return
                }
                getString(R.string.filename_split)
            }
            ToolType.ROTATE -> {
                if (files.isEmpty()) {
                    showError(getString(R.string.error_select_source_file))
                    return
                }
                getString(R.string.filename_rotated)
            }
            ToolType.DELETE_PAGES -> {
                if (files.isEmpty()) {
                    showError(getString(R.string.error_select_source_file))
                    return
                }
                val input = binding.etDeletePages.text?.toString().orEmpty()
                if (input.isBlank()) {
                    showError(getString(R.string.error_invalid_page_range))
                    return
                }
                getString(R.string.filename_deleted)
            }
            ToolType.IMAGES_TO_PDF -> {
                if (files.isEmpty()) {
                    showError(getString(R.string.error_select_images))
                    return
                }
                getString(R.string.filename_images)
            }
            ToolType.WATERMARK -> {
                if (files.isEmpty()) {
                    showError(getString(R.string.error_select_source_file))
                    return
                }
                val text = binding.etWatermarkText.text?.toString().orEmpty().trim()
                if (text.isBlank()) {
                    showError(getString(R.string.error_empty_watermark))
                    return
                }
                getString(R.string.filename_watermarked)
            }
            ToolType.PAGE_NUMBERS -> {
                if (files.isEmpty()) {
                    showError(getString(R.string.error_select_source_file))
                    return
                }
                getString(R.string.filename_numbered)
            }
            ToolType.COMPRESS -> {
                if (files.isEmpty()) {
                    showError(getString(R.string.error_select_source_file))
                    return
                }
                getString(R.string.filename_compressed)
            }
            ToolType.ORGANIZE_PAGES -> {
                val single = files.firstOrNull()?.uri
                if (single != null) {
                    com.savoj.volumpdf.ui.organizer.PageOrganizerActivity.start(this, single)
                    finish()
                }
                return
            }
            ToolType.SCAN_DOCUMENT -> {
                com.savoj.volumpdf.ui.scanner.ScanDocumentActivity.start(this)
                finish()
                return
            }
            ToolType.CREATE_BLANK -> "document.pdf"
        }

        createDocumentLauncher.launch(defaultName)
    }

    private fun startOperation(outputUri: Uri) {
        when (toolType) {
            ToolType.MERGE -> viewModel.executeMerge(this, outputUri)
            ToolType.SPLIT -> {
                val input = binding.etSplitPages.text?.toString().orEmpty()
                viewModel.executeSplit(this, input, outputUri)
            }
            ToolType.ROTATE -> {
                val degrees = when (binding.rgRotationAngle.checkedRadioButtonId) {
                    R.id.rbRotate180 -> 180
                    R.id.rbRotate270 -> 270
                    else -> 90
                }
                val pagesInput = binding.etRotatePages.text?.toString()
                viewModel.executeRotate(this, degrees, pagesInput, outputUri)
            }
            ToolType.DELETE_PAGES -> {
                val input = binding.etDeletePages.text?.toString().orEmpty()
                viewModel.executeDelete(this, input, outputUri)
            }
            ToolType.IMAGES_TO_PDF -> viewModel.executeImagesToPdf(this, outputUri)
            ToolType.WATERMARK -> {
                val text = binding.etWatermarkText.text?.toString().orEmpty().trim()
                val pagesInput = binding.etWatermarkPages.text?.toString()
                viewModel.executeWatermark(this, text, pagesInput, outputUri)
            }
            ToolType.PAGE_NUMBERS -> {
                val formatPattern = when (binding.rgPageNumbersFormat.checkedRadioButtonId) {
                    R.id.rbFormat2 -> "%d / %d"
                    R.id.rbFormat3 -> "%d"
                    else -> getString(R.string.page_numbers_format_1)
                }
                val pagesInput = binding.etPageNumbersPages.text?.toString()
                viewModel.executePageNumbers(this, formatPattern, pagesInput, outputUri)
            }
            ToolType.COMPRESS -> {
                val level = when (binding.rgCompressLevel.checkedRadioButtonId) {
                    R.id.rbCompressHigh -> com.savoj.volumpdf.core.pdf.model.CompressionLevel.HIGH
                    R.id.rbCompressLow -> com.savoj.volumpdf.core.pdf.model.CompressionLevel.LOW
                    else -> com.savoj.volumpdf.core.pdf.model.CompressionLevel.MEDIUM
                }
                viewModel.executeCompress(this, level, outputUri)
            }
            ToolType.ORGANIZE_PAGES -> {
                // Відкривається напряму через PageOrganizerActivity
            }
            ToolType.SCAN_DOCUMENT -> {
                // Відкривається напряму через ScanDocumentActivity
            }
            ToolType.CREATE_BLANK -> Unit
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedFiles.collect { files ->
                    filesAdapter.setItems(files)

                    if (files.isNotEmpty()) {
                        binding.tvSelectedSummary.visibility = View.VISIBLE
                        val count = files.size
                        if (toolType == ToolType.IMAGES_TO_PDF) {
                            binding.tvSelectedSummary.text = getString(R.string.selected_images_count, count)
                        } else if (toolType == ToolType.MERGE) {
                            binding.tvSelectedSummary.text = getString(R.string.selected_files_count, count)
                        } else {
                            val single = files.first()
                            val totalPages = viewModel.sourceDocumentPages
                            val pagesHint = if (totalPages > 0) " ($totalPages pages)" else ""
                            binding.tvSelectedSummary.text = single.displayName + pagesHint
                        }
                        binding.btnProcessAndSave.isEnabled = true
                    } else {
                        binding.tvSelectedSummary.visibility = View.GONE
                        binding.btnProcessAndSave.isEnabled = false
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is OperationUiState.Idle -> {
                            binding.containerProgress.visibility = View.GONE
                            binding.btnProcessAndSave.isEnabled = viewModel.selectedFiles.value.isNotEmpty()
                            binding.btnSelectFiles.isEnabled = true
                        }
                        is OperationUiState.Loading -> {
                            binding.containerProgress.visibility = View.VISIBLE
                            binding.tvProgressStatus.text = getString(R.string.status_step, state.currentStep, state.totalSteps)
                            binding.btnProcessAndSave.isEnabled = false
                            binding.btnSelectFiles.isEnabled = false
                        }
                        is OperationUiState.Success -> {
                            binding.containerProgress.visibility = View.GONE
                            binding.btnProcessAndSave.isEnabled = true
                            binding.btnSelectFiles.isEnabled = true
                            // Показ Interstitial з кулдауном 5 хвилин перед показом діалогу результату
                            adsManager.showActionInterstitial(this@ToolsActivity) {
                                showSuccessDialog(
                                    state.outputUri,
                                    state.pagesCount,
                                    state.originalSizeBytes,
                                    state.newSizeBytes
                                )
                            }
                        }
                        is OperationUiState.Error -> {
                            binding.containerProgress.visibility = View.GONE
                            binding.btnProcessAndSave.isEnabled = true
                            binding.btnSelectFiles.isEnabled = true
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun showSuccessDialog(
        outputUri: Uri,
        pagesCount: Int,
        originalBytes: Long = 0L,
        newBytes: Long = 0L
    ) {
        val baseMsg = getString(R.string.operation_success_message, pagesCount)
        val finalMsg = if (originalBytes > 0L && newBytes > 0L) {
            val savedRatio = (originalBytes - newBytes).toFloat() / originalBytes
            val savedPercent = (savedRatio * 100).toInt().coerceAtLeast(0)
            val stats = getString(
                R.string.compress_success_stats,
                savedPercent,
                SafUtils.formatFileSize(originalBytes),
                SafUtils.formatFileSize(newBytes)
            )
            "$baseMsg\n\n$stats"
        } else {
            baseMsg
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.operation_success_title)
            .setMessage(finalMsg)
            .setIcon(R.drawable.ic_check_circle)
            .setPositiveButton(R.string.action_open_result) { _, _ ->
                val readerIntent = Intent(this, ReaderActivity::class.java).apply {
                    data = outputUri
                    putExtra(ReaderActivity.EXTRA_DOCUMENT_TITLE, SafUtils.queryDisplayName(this@ToolsActivity, outputUri))
                }
                startActivity(readerIntent)
                finish()
            }
            .setNegativeButton(R.string.action_ok) { _, _ ->
                viewModel.resetState()
            }
            .setCancelable(false)
            .show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_TOOL_TYPE = "extra_tool_type"
        const val EXTRA_SOURCE_URI = "extra_source_uri"

        fun createIntent(context: Context, toolType: ToolType, sourceUri: Uri? = null): Intent {
            return Intent(context, ToolsActivity::class.java).apply {
                putExtra(EXTRA_TOOL_TYPE, toolType.name)
                sourceUri?.let { putExtra(EXTRA_SOURCE_URI, it) }
            }
        }
    }
}
