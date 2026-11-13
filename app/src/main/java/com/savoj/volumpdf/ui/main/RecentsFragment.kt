package com.savoj.volumpdf.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.savoj.volumpdf.R
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.databinding.FragmentRecentsBinding
import com.savoj.volumpdf.ui.reader.ReaderActivity
import com.savoj.volumpdf.ui.reader.ReadingProgressLogic
import com.savoj.volumpdf.ui.scanner.ScanDocumentActivity
import com.savoj.volumpdf.util.PdfThumbnailLoader
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.launch

class RecentsFragment : Fragment() {

    private var _binding: FragmentRecentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: RecentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()

        binding.btnEmptyOpen.setOnClickListener {
            (activity as? MainActivity)?.launchSafOpenDocument()
        }
        binding.btnEmptyScan.setOnClickListener {
            ScanDocumentActivity.start(requireContext())
        }
        binding.btnClearSearch.setOnClickListener {
            (activity as? MainActivity)?.clearSearchQuery()
        }
    }

    private fun setupRecyclerView() {
        adapter = RecentsAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onItemClicked = { doc ->
                openDocumentInReader(doc.uri, doc.title)
            },
            onFavoriteToggled = { doc ->
                viewModel.toggleFavorite(doc.uri)
            },
            onShareClicked = { doc ->
                shareDocument(doc.uri)
            },
            onDetailsClicked = { doc ->
                showDocumentDetails(doc)
            },
            onDeleteClicked = { doc ->
                viewModel.deleteDocument(doc.uri)
            }
        )

        binding.recyclerViewRecents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewRecents.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentDocuments.collect { documents ->
                    adapter.submitList(documents)
                    val isEmpty = documents.isEmpty()
                    val hasSearch = viewModel.searchQuery.value.isNotBlank()

                    binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.scrollLibraryContent.visibility = if (isEmpty) View.GONE else View.VISIBLE

                    if (!isEmpty) {
                        binding.tvSectionRecentsCount.text = documents.size.toString()
                        val continueDoc = if (!hasSearch) {
                            documents.maxByOrNull { it.lastOpenedTimestamp }
                        } else {
                            null
                        }
                        bindContinueReadingCard(continueDoc)
                    } else if (hasSearch) {
                        binding.ivEmptyIcon.setImageResource(R.drawable.ic_search)
                        binding.tvEmptyTitle.setText(R.string.empty_search_title)
                        binding.tvEmptyDesc.setText(R.string.empty_search_desc)
                        binding.btnEmptyOpen.visibility = View.GONE
                        binding.btnEmptyScan.visibility = View.GONE
                        binding.btnClearSearch.visibility = View.VISIBLE
                    } else {
                        binding.ivEmptyIcon.setImageResource(R.drawable.ic_empty_recents)
                        binding.tvEmptyTitle.setText(R.string.empty_recents_title)
                        binding.tvEmptyDesc.setText(R.string.empty_recents_desc)
                        binding.btnEmptyOpen.visibility = View.VISIBLE
                        binding.btnEmptyScan.visibility = View.VISIBLE
                        binding.btnClearSearch.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun bindContinueReadingCard(doc: RecentDocument?) {
        if (doc == null) {
            binding.cardContinueReading.visibility = View.GONE
            return
        }
        binding.cardContinueReading.visibility = View.VISIBLE
        binding.tvContinueTitle.text = doc.title

        val context = requireContext()
        val totalPages = doc.pageCount.coerceAtLeast(1)
        val current1Based = (doc.lastPageRead + 1).coerceIn(1, totalPages)
        val percent = ReadingProgressLogic.progressPercent(
            lastPageRead = doc.lastPageRead,
            pageCount = totalPages,
            alwaysShowFirstPage = true
        )

        val pageLabel = if (doc.pageCount > 0) {
            context.getString(R.string.document_page_progress, current1Based, doc.pageCount) + " • $percent%"
        } else {
            SafUtils.formatTimestamp(doc.lastOpenedTimestamp)
        }
        binding.tvContinuePageInfo.text = pageLabel
        binding.progressContinueReading.progress = percent

        PdfThumbnailLoader.bindThumbnail(
            context = context,
            scope = viewLifecycleOwner.lifecycleScope,
            imageView = binding.ivContinueThumbnail,
            uriString = doc.uri,
            pageIndex = doc.lastPageRead.coerceAtLeast(0),
            targetWidthPx = 180
        )

        val openAction = View.OnClickListener {
            openDocumentInReader(doc.uri, doc.title)
        }
        binding.cardContinueReading.setOnClickListener(openAction)
        binding.btnContinueReading.setOnClickListener(openAction)
    }

    private fun openDocumentInReader(uriString: String, title: String) {
        val intent = Intent(requireContext(), ReaderActivity::class.java).apply {
            data = Uri.parse(uriString)
            putExtra(ReaderActivity.EXTRA_DOCUMENT_TITLE, title)
        }
        startActivity(intent)
    }

    private fun shareDocument(uriString: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    private fun showDocumentDetails(doc: RecentDocument) {
        val sb = StringBuilder()
            .append(getString(R.string.details_title, doc.title)).append("\n\n")
            .append(getString(R.string.details_pages, doc.pageCount)).append("\n")
        if (doc.pageCount > 0 && doc.lastPageRead > 0) {
            val displayPage = (doc.lastPageRead + 1).coerceIn(1, doc.pageCount)
            sb.append(getString(R.string.document_page_progress, displayPage, doc.pageCount)).append("\n")
        }
        val message = sb
            .append(getString(R.string.details_size, SafUtils.formatFileSize(doc.fileSizeBytes))).append("\n")
            .append(getString(R.string.details_last_opened, SafUtils.formatTimestamp(doc.lastOpenedTimestamp))).append("\n\n")
            .append(getString(R.string.details_uri, doc.uri))
            .toString()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_details_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = RecentsFragment()
    }
}
