package com.savoj.volumpdf.ui.organizer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ItemPageOrganizerBinding
import java.util.Collections

/**
 * Адаптер для сітки сторінок з підтримкою drag-and-drop перестановки (ТОП-3).
 */
class PageOrganizerAdapter(
    private val onRotateClicked: (position: Int) -> Unit,
    private val onDeleteClicked: (position: Int) -> Unit,
    private val onThumbnailNeeded: (pageIndex: Int, callback: (Bitmap?) -> Unit) -> Unit
) : RecyclerView.Adapter<PageOrganizerAdapter.PageViewHolder>() {

    private val items = mutableListOf<PageOrganizerItem>()

    fun getItems(): List<PageOrganizerItem> = items.toList()

    fun submitList(newItems: List<PageOrganizerItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size || toPosition >= items.size) {
            return false
        }
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        // Оновлюємо номери сторінок на карточках, що змінили позиції
        val start = minOf(fromPosition, toPosition)
        val count = Math.abs(fromPosition - toPosition) + 1
        notifyItemRangeChanged(start, count)
        return true
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPageOrganizerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    inner class PageViewHolder(
        private val binding: ItemPageOrganizerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PageOrganizerItem, position: Int) {
            val context = binding.root.context
            binding.tvPageNumber.text = context.getString(R.string.organize_page_badge, position + 1)

            if (item.originalPageIndex != position) {
                binding.tvOriginalPage.visibility = View.VISIBLE
                binding.tvOriginalPage.text = context.getString(R.string.organize_page_original, item.originalPageIndex + 1)
            } else {
                binding.tvOriginalPage.visibility = View.GONE
            }

            binding.ivThumbnail.rotation = item.rotationDegrees.toFloat()

            // Запит на завантаження мініатюри сторінки
            binding.ivThumbnail.tag = item.originalPageIndex
            binding.ivThumbnail.setImageBitmap(null)
            onThumbnailNeeded(item.originalPageIndex) { bitmap ->
                if (binding.ivThumbnail.tag == item.originalPageIndex) {
                    binding.ivThumbnail.setImageBitmap(bitmap)
                }
            }

            binding.btnRotate.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onRotateClicked(pos)
                }
            }

            binding.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDeleteClicked(pos)
                }
            }
        }
    }
}
