package com.savoj.volumpdf.ui.main

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.savoj.volumpdf.databinding.FragmentToolsBinding
import com.savoj.volumpdf.ui.organizer.PageOrganizerActivity
import com.savoj.volumpdf.ui.scanner.ScanDocumentActivity
import com.savoj.volumpdf.ui.tools.ToolType
import com.savoj.volumpdf.ui.tools.ToolsActivity
import com.savoj.volumpdf.ui.tools.ToolsAdapter
import com.savoj.volumpdf.util.DocumentCreationHelper
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.launch

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    private val openDocumentForOrganizeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            SafUtils.takePersistableUriPermissionSafe(requireContext(), uri)
            PageOrganizerActivity.start(requireContext(), uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ToolsAdapter { toolType ->
            onToolSelected(toolType)
        }

        val config = resources.configuration
        val isWide = config.screenWidthDp >= 600 || config.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.recyclerViewTools.layoutManager = if (isWide) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }
        binding.recyclerViewTools.adapter = adapter
    }

    private fun onToolSelected(toolType: ToolType) {
        if (toolType == ToolType.CREATE_BLANK) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val file = DocumentCreationHelper.createBlankDocument(requireContext())
                    DocumentCreationHelper.openNewDocument(requireActivity(), file)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Помилка створення PDF", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        if (toolType == ToolType.SCAN_DOCUMENT) {
            ScanDocumentActivity.start(requireContext())
            return
        }
        if (toolType == ToolType.ORGANIZE_PAGES) {
            openDocumentForOrganizeLauncher.launch(arrayOf("application/pdf"))
            return
        }
        val intent = ToolsActivity.createIntent(requireContext(), toolType)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ToolsFragment()
    }
}
