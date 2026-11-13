package com.savoj.volumpdf.ui.organizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ActivityPageOrganizerBinding
import com.savoj.volumpdf.ui.common.SystemEdges
import com.savoj.volumpdf.ui.common.WindowInsetsHelper
import com.savoj.volumpdf.ui.reader.ReaderActivity
import com.savoj.volumpdf.ui.tools.OperationUiState
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.launch

/**
 * Екран візуального організатора сторінок (ТОП-3).
 * Надає візуальну сітку з можливістю:
 * - Drag-and-drop перестановки сторінок місцями за допомогою [ItemTouchHelper]
 * - Повороту окремих сторінок на 90° в один клік
 * - Видалення непотрібних сторінок із можливістю скасування (Undo)
 * - Повороту всіх сторінок одночасно
 * - Експорту в новий PDF-документ
 */
class PageOrganizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageOrganizerBinding
    private val viewModel: PageOrganizerViewModel by viewModels()

    private var documentUri: Uri? = null
    private lateinit var organizerAdapter: PageOrganizerAdapter

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri: Uri? ->
        if (outputUri != null) {
            val src = documentUri ?: return@registerForActivityResult
            viewModel.saveOrganizedPdf(src, outputUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageOrganizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriExtra: Uri? = IntentCompat.getParcelableExtra(intent, EXTRA_DOCUMENT_URI, Uri::class.java) ?: intent.data
        if (uriExtra == null) {
            Toast.makeText(this, R.string.error_select_source_file, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        documentUri = uriExtra

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        applyWindowInsets()
        setupRecyclerView()
        setupListeners()
        setupObservers()
        setupBackNavigation()

        viewModel.loadDocument(uriExtra)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOrganizer) { _, insets ->
            val edges = SystemEdges.from(insets)
            binding.rootOrganizer.setPadding(edges.left, 0, edges.right, 0)
            WindowInsetsHelper.applyToolbarTopInset(binding.toolbar, edges.top)

            val barPadH = (16 * resources.displayMetrics.density).toInt()
            val barPadV = (12 * resources.displayMetrics.density).toInt()
            binding.bottomBar.setPadding(barPadH, barPadV, barPadH, barPadV + edges.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupRecyclerView() {
        val spanCount = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        binding.rvPageGrid.layoutManager = GridLayoutManager(this, spanCount)

        organizerAdapter = PageOrganizerAdapter(
            onRotateClicked = { position ->
                viewModel.rotateItemAt(position)
            },
            onDeleteClicked = { position ->
                val deleted = viewModel.deleteItemAt(position)
                if (deleted != null) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.organize_page_deleted, deleted.originalPageIndex + 1),
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.organize_undo) {
                        viewModel.undoDelete()
                    }.show()
                } else {
                    Toast.makeText(this, R.string.organize_min_pages_warning, Toast.LENGTH_SHORT).show()
                }
            },
            onThumbnailNeeded = { pageIndex, callback ->
                viewModel.loadThumbnail(pageIndex, callback)
            }
        )
        binding.rvPageGrid.adapter = organizerAdapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION) {
                    organizerAdapter.onItemMove(from, to)
                    viewModel.moveItem(from, to)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.rvPageGrid)
    }

    private fun setupListeners() {
        binding.btnRotateAll.setOnClickListener {
            viewModel.rotateAll(90)
        }

        binding.btnSave.setOnClickListener {
            val src = documentUri ?: return@setOnClickListener
            val originalName = SafUtils.queryDisplayName(this, src)
            val baseName = originalName.substringBeforeLast(".")
            val defaultName = "${baseName}_${getString(R.string.filename_organized)}"
            createDocumentLauncher.launch(defaultName)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        organizerAdapter.submitList(items)
                        binding.toolbar.subtitle = getString(R.string.organize_pages_count, items.size)
                        binding.btnSave.isEnabled = items.isNotEmpty()
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
                        if (loading) {
                            binding.tvLoadingMessage.text = getString(R.string.loading_document)
                        }
                    }
                }

                launch {
                    viewModel.saveState.collect { state ->
                        handleSaveState(state)
                    }
                }
            }
        }
    }

    private fun handleSaveState(state: OperationUiState) {
        when (state) {
            is OperationUiState.Idle -> {
                binding.loadingOverlay.visibility = View.GONE
            }
            is OperationUiState.Loading -> {
                binding.loadingOverlay.visibility = View.VISIBLE
                binding.tvLoadingMessage.text = getString(R.string.organize_save_button) + "…"
            }
            is OperationUiState.Success -> {
                binding.loadingOverlay.visibility = View.GONE
                viewModel.resetSaveState()
                showSuccessDialog(state.outputUri)
            }
            is OperationUiState.Error -> {
                binding.loadingOverlay.visibility = View.GONE
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(state.message)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
                viewModel.resetSaveState()
            }
        }
    }

    private fun showSuccessDialog(outputUri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.operation_success_title)
            .setMessage(R.string.operation_success_message)
            .setIcon(R.drawable.ic_check_circle)
            .setPositiveButton(R.string.action_open_result) { _, _ ->
                val readerIntent = Intent(this, ReaderActivity::class.java).apply {
                    data = outputUri
                    putExtra(ReaderActivity.EXTRA_DOCUMENT_TITLE, SafUtils.queryDisplayName(this@PageOrganizerActivity, outputUri))
                }
                startActivity(readerIntent)
                finish()
            }
            .setNegativeButton(R.string.action_ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_page_organizer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_reset -> {
                viewModel.resetToOriginal()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_URI = "extra_document_uri"

        fun start(context: Context, documentUri: Uri) {
            val intent = Intent(context, PageOrganizerActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT_URI, documentUri)
            }
            context.startActivity(intent)
        }
    }
}
