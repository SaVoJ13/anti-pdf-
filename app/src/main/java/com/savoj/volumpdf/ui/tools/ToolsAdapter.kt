package com.savoj.volumpdf.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ItemToolCardBinding

enum class ToolType {
    MERGE,
    SPLIT,
    ORGANIZE_PAGES,
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
) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    private val tools = listOf(
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val binding = ItemToolCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ToolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position])
    }

    override fun getItemCount(): Int = tools.size

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
}
