package com.savoj.volumpdf.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
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
import com.savoj.volumpdf.databinding.ActivityToolsBinding
import com.savoj.volumpdf.ui.reader.ReaderActivity
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.launch

class ToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolsBinding
    private val viewModel: ToolsViewModel by viewModels()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolName = intent.getStringExtra(EXTRA_TOOL_TYPE) ?: ToolType.MERGE.name
        toolType = try {
            ToolType.valueOf(toolName)
        } catch (_: Exception) {
            ToolType.MERGE
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupToolUi()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolUi() {
        when (toolType) {
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
            when (toolType) {
                ToolType.MERGE -> openMultiplePdfLauncher.launch(arrayOf("application/pdf"))
                ToolType.SPLIT, ToolType.ROTATE, ToolType.DELETE_PAGES -> openSinglePdfLauncher.launch(arrayOf("application/pdf"))
                ToolType.IMAGES_TO_PDF -> openMultipleImagesLauncher.launch(arrayOf("image/jpeg", "image/png", "image/*"))
            }
        }

        binding.btnProcessAndSave.setOnClickListener {
            validateAndRequestOutputFile()
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
                            showSuccessDialog(state.outputUri, state.pagesCount)
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

    private fun showSuccessDialog(outputUri: Uri, pagesCount: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.operation_success_title)
            .setMessage(getString(R.string.operation_success_message, pagesCount))
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

        fun createIntent(context: Context, toolType: ToolType): Intent {
            return Intent(context, ToolsActivity::class.java).apply {
                putExtra(EXTRA_TOOL_TYPE, toolType.name)
            }
        }
    }
}
