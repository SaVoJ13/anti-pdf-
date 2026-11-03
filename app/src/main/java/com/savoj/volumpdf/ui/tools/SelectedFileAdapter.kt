package com.savoj.volumpdf.ui.tools

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ItemSelectedFileBinding

data class SelectedFileItem(
    val uri: Uri,
    val displayName: String,
    val isImage: Boolean = false
)

class SelectedFileAdapter(
    private val onRemoveClicked: (Int) -> Unit
) : RecyclerView.Adapter<SelectedFileAdapter.FileViewHolder>() {

    private val items = mutableListOf<SelectedFileItem>()

    fun setItems(newItems: List<SelectedFileItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addItem(item: SelectedFileItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size - position)
        }
    }

    fun getItems(): List<SelectedFileItem> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemSelectedFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(
        private val binding: ItemSelectedFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SelectedFileItem, position: Int) {
            binding.tvFileName.text = item.displayName
            if (item.isImage) {
                binding.ivItemTypeIcon.setImageResource(R.drawable.ic_image_to_pdf)
            } else {
                binding.ivItemTypeIcon.setImageResource(R.drawable.ic_pdf_document)
            }

            binding.btnRemoveFile.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onRemoveClicked(currentPos)
                }
            }
        }
    }
}
