package com.savoj.volumpdf.ui.main

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.savoj.volumpdf.databinding.FragmentToolsBinding
import com.savoj.volumpdf.ui.organizer.PageOrganizerActivity
import com.savoj.volumpdf.ui.tools.ToolType
import com.savoj.volumpdf.ui.tools.ToolsActivity
import com.savoj.volumpdf.ui.tools.ToolsAdapter
import com.savoj.volumpdf.util.SafUtils

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

        binding.recyclerViewTools.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewTools.adapter = adapter
    }

    private fun onToolSelected(toolType: ToolType) {
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
