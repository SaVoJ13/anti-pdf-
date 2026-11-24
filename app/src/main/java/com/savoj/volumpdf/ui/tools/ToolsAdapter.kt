package com.savoj.volumpdf.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.savoj.volumpdf.databinding.ItemToolGroupHeaderBinding
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ItemToolCardBinding

enum class ToolType {
    CREATE_BLANK,
    SCAN_DOCUMENT,
    ORGANIZE_PAGES,
    MERGE,
    SPLIT,
    ROTATE,
    DELETE_PAGES,
    IMAGES_TO_PDF,
    WATERMARK,
    PAGE_NUMBERS,
    COMPRESS
}

data class ToolDefinition(
    val type: ToolType,
    @get:StringRes val titleRes: Int,
    @get:StringRes val descRes: Int,
    @get:DrawableRes val iconRes: Int
)

class ToolsAdapter(
    private val onToolClicked: (ToolType) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val definitions = listOf(
        ToolDefinition(
            type = ToolType.CREATE_BLANK,
            titleRes = R.string.tool_create_blank_title,
            descRes = R.string.tool_create_blank_desc,
            iconRes = R.drawable.ic_note_add
        ),
        ToolDefinition(
            type = ToolType.SCAN_DOCUMENT,
            titleRes = R.string.tool_scan_title,
            descRes = R.string.tool_scan_desc,
            iconRes = R.drawable.ic_camera_scan
        ),
        ToolDefinition(
            type = ToolType.ORGANIZE_PAGES,
            titleRes = R.string.tool_organize_title,
            descRes = R.string.tool_organize_desc,
            iconRes = R.drawable.ic_organize_pages
        ),
        ToolDefinition(
            type = ToolType.MERGE,
            titleRes = R.string.tool_merge_title,
            descRes = R.string.tool_merge_desc,
            iconRes = R.drawable.ic_merge
        ),
        ToolDefinition(
            type = ToolType.SPLIT,
            titleRes = R.string.tool_split_title,
            descRes = R.string.tool_split_desc,
            iconRes = R.drawable.ic_split
        ),
        ToolDefinition(
            type = ToolType.ROTATE,
            titleRes = R.string.tool_rotate_title,
            descRes = R.string.tool_rotate_desc,
            iconRes = R.drawable.ic_rotate
        ),
        ToolDefinition(
            type = ToolType.DELETE_PAGES,
            titleRes = R.string.tool_delete_title,
            descRes = R.string.tool_delete_desc,
            iconRes = R.drawable.ic_delete_page
        ),
        ToolDefinition(
            type = ToolType.IMAGES_TO_PDF,
            titleRes = R.string.tool_images_to_pdf_title,
            descRes = R.string.tool_images_to_pdf_desc,
            iconRes = R.drawable.ic_image_to_pdf
        ),
        ToolDefinition(
            type = ToolType.WATERMARK,
            titleRes = R.string.tool_watermark_title,
            descRes = R.string.tool_watermark_desc,
            iconRes = R.drawable.ic_watermark
        ),
        ToolDefinition(
            type = ToolType.PAGE_NUMBERS,
            titleRes = R.string.tool_page_numbers_title,
            descRes = R.string.tool_page_numbers_desc,
            iconRes = R.drawable.ic_page_numbers
        ),
        ToolDefinition(
            type = ToolType.COMPRESS,
            titleRes = R.string.tool_compress_title,
            descRes = R.string.tool_compress_desc,
            iconRes = R.drawable.ic_compress
        )
    )

    private val toolsByType = definitions.associateBy { it.type }
    private val items = ToolGroupingLogic.buildItems()

    /** Заголовок групи займає всю ширину сітки. */
    fun isHeader(position: Int): Boolean = items[position] is ToolListItem.Header

    override fun getItemViewType(position: Int): Int = if (isHeader(position)) VIEW_HEADER else VIEW_TOOL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_HEADER) {
            HeaderViewHolder(ItemToolGroupHeaderBinding.inflate(inflater, parent, false))
        } else {
            ToolViewHolder(ItemToolCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ToolListItem.Header -> (holder as HeaderViewHolder).bind(item.group)
            is ToolListItem.Tool -> (holder as ToolViewHolder).bind(toolsByType.getValue(item.type))
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(
        private val binding: ItemToolGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: ToolGroup) {
            binding.tvToolGroupTitle.setText(group.titleRes)
        }
    }

    inner class ToolViewHolder(
        private val binding: ItemToolCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tool: ToolDefinition) {
            binding.tvToolTitle.setText(tool.titleRes)
            binding.tvToolDescription.setText(tool.descRes)
            binding.ivToolIcon.setImageResource(tool.iconRes)

            binding.root.setOnClickListener {
                onToolClicked(tool.type)
            }
        }
    }

    private companion object {
        const val VIEW_HEADER = 0
        const val VIEW_TOOL = 1
    }
}
