package com.savoj.volumpdf.ui.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.BottomSheetReaderToolsBinding
import com.savoj.volumpdf.databinding.ItemToolCardBinding

enum class ReaderToolAction {
    ADD_PAGE,
    INSERT_FILE,
    ORGANIZE_PAGES,
    ROTATE,
    DELETE_PAGES,
    SPLIT,
    COMPRESS,
    WATERMARK,
    PAGE_NUMBERS,
    MERGE,
    SCAN,
    PRINT,
    SHARE,
    DETAILS
}

data class ReaderToolDefinition(
    val action: ReaderToolAction,
    @get:StringRes val titleRes: Int,
    @get:StringRes val descRes: Int,
    @get:DrawableRes val iconRes: Int
)

class ReaderToolsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReaderToolsBinding? = null
    private val binding get() = _binding!!

    var onActionSelected: ((ReaderToolAction) -> Unit)? = null

    private val toolsList = listOf(
        ReaderToolDefinition(
            action = ReaderToolAction.ADD_PAGE,
            titleRes = R.string.tool_add_page,
            descRes = R.string.tool_add_page_desc,
            iconRes = R.drawable.ic_add_page
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.INSERT_FILE,
            titleRes = R.string.tool_insert_file,
            descRes = R.string.tool_insert_file_desc,
            iconRes = R.drawable.ic_attach_file
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.ORGANIZE_PAGES,
            titleRes = R.string.tool_organize_title,
            descRes = R.string.tool_organize_desc,
            iconRes = R.drawable.ic_organize_pages
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.COMPRESS,
            titleRes = R.string.tool_compress_title,
            descRes = R.string.tool_compress_desc,
            iconRes = R.drawable.ic_compress
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.WATERMARK,
            titleRes = R.string.tool_watermark_title,
            descRes = R.string.tool_watermark_desc,
            iconRes = R.drawable.ic_watermark
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.PAGE_NUMBERS,
            titleRes = R.string.tool_page_numbers_title,
            descRes = R.string.tool_page_numbers_desc,
            iconRes = R.drawable.ic_page_numbers
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.ROTATE,
            titleRes = R.string.tool_rotate_title,
            descRes = R.string.tool_rotate_desc,
            iconRes = R.drawable.ic_rotate
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.DELETE_PAGES,
            titleRes = R.string.tool_delete_title,
            descRes = R.string.tool_delete_desc,
            iconRes = R.drawable.ic_delete_page
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.SPLIT,
            titleRes = R.string.tool_split_title,
            descRes = R.string.tool_split_desc,
            iconRes = R.drawable.ic_split
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.MERGE,
            titleRes = R.string.tool_merge_title,
            descRes = R.string.tool_merge_desc,
            iconRes = R.drawable.ic_merge
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.SCAN,
            titleRes = R.string.tool_scan_title,
            descRes = R.string.tool_scan_desc,
            iconRes = R.drawable.ic_camera_scan
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.PRINT,
            titleRes = R.string.action_print,
            descRes = R.string.action_print,
            iconRes = R.drawable.ic_pdf_document
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.SHARE,
            titleRes = R.string.action_share,
            descRes = R.string.action_share,
            iconRes = R.drawable.ic_share
        ),
        ReaderToolDefinition(
            action = ReaderToolAction.DETAILS,
            titleRes = R.string.action_details,
            descRes = R.string.action_details,
            iconRes = R.drawable.ic_info
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetReaderToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerReaderTools.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReaderTools.adapter = ReaderToolsSheetAdapter(toolsList) { action ->
            dismiss()
            onActionSelected?.invoke(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReaderToolsBottomSheet"

        fun newInstance(): ReaderToolsBottomSheetDialogFragment =
            ReaderToolsBottomSheetDialogFragment()
    }
}

class ReaderToolsSheetAdapter(
    private val items: List<ReaderToolDefinition>,
    private val onItemClick: (ReaderToolAction) -> Unit
) : RecyclerView.Adapter<ReaderToolsSheetAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemToolCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemToolCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReaderToolDefinition) {
            binding.tvToolTitle.setText(item.titleRes)
            binding.tvToolDescription.setText(item.descRes)
            binding.ivToolIcon.setImageResource(item.iconRes)
            binding.root.setOnClickListener { onItemClick(item.action) }
        }
    }
}
