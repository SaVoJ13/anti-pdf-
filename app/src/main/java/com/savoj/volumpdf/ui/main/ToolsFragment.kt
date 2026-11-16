package com.savoj.volumpdf.ui.main

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.savoj.volumpdf.databinding.FragmentToolsBinding
import com.savoj.volumpdf.ui.organizer.PageOrganizerActivity
import com.savoj.volumpdf.ui.scanner.ScanDocumentActivity
import com.savoj.volumpdf.ui.tools.ToolType
import com.savoj.volumpdf.ui.tools.ToolsActivity
import com.savoj.volumpdf.ui.tools.ToolsAdapter
import com.savoj.volumpdf.util.DocumentCreationHelper
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.launch
import com.savoj.volumpdf.R
import com.savoj.volumpdf.util.ErrorMessages

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

        // Колонки рахуються за шириною списку й оновлюються при зміні розміру вікна (поворот, split-screen).
        com.savoj.volumpdf.ui.common.AdaptiveGrid.attach(
            binding.recyclerViewTools,
            com.savoj.volumpdf.ui.common.AdaptiveLayoutLogic.LIST_ITEM_MIN_DP,
            com.savoj.volumpdf.ui.common.AdaptiveLayoutLogic.LIST_MAX_COLUMNS
        )
        binding.recyclerViewTools.adapter = adapter
    }

    private fun onToolSelected(toolType: ToolType) {
        if (toolType == ToolType.CREATE_BLANK) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val file = DocumentCreationHelper.createBlankDocument(requireContext())
                    DocumentCreationHelper.openNewDocument(requireActivity(), file)
                } catch (e: Exception) {
                    ErrorMessages.report("ToolsFragment", e)
                    Toast.makeText(requireContext(), ErrorMessages.forFailure(e, R.string.err_create_pdf).resolve(requireContext()), Toast.LENGTH_LONG).show()
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
