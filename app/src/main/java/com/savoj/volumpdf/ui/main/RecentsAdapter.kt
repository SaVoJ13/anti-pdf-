package com.savoj.volumpdf.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.savoj.volumpdf.R
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.databinding.ItemRecentDocumentBinding
import com.savoj.volumpdf.ui.reader.ReadingProgressLogic
import com.savoj.volumpdf.util.PdfThumbnailLoader
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.CoroutineScope

class RecentsAdapter(
    private val scope: CoroutineScope,
    private val onItemClicked: (RecentDocument) -> Unit,
    private val onFavoriteToggled: (RecentDocument) -> Unit,
    private val onShareClicked: (RecentDocument) -> Unit,
    private val onDetailsClicked: (RecentDocument) -> Unit,
    private val onDeleteClicked: (RecentDocument) -> Unit
) : ListAdapter<RecentDocument, RecentsAdapter.RecentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding = ItemRecentDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecentViewHolder(
        private val binding: ItemRecentDocumentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(doc: RecentDocument) {
            val context = binding.root.context
            binding.tvDocumentTitle.text = doc.title

            val sizeStr = SafUtils.formatFileSize(doc.fileSizeBytes)
            val dateStr = SafUtils.formatTimestamp(doc.lastOpenedTimestamp)
            val subtitleParts = listOf(sizeStr, dateStr).filter { it.isNotBlank() }
            binding.tvDocumentSubtitle.text = subtitleParts.joinToString(" • ")

            val pagesStr = ReadingProgressLogic.formatPagesSubtitle(
                pageCount = doc.pageCount,
                lastPageRead = doc.lastPageRead,
                formatCount = { count -> context.getString(R.string.document_pages_count, count) },
                formatProgress = { current, total -> context.getString(R.string.document_page_progress, current, total) }
            )
            if (pagesStr.isNotBlank()) {
                binding.layoutReadingProgress.visibility = View.VISIBLE
                binding.tvReadingProgress.text = pagesStr
                val percent = ReadingProgressLogic.progressPercent(
                    lastPageRead = doc.lastPageRead,
                    pageCount = doc.pageCount,
                    alwaysShowFirstPage = false
                )
                if (percent > 0) {
                    binding.progressReading.visibility = View.VISIBLE
                    binding.progressReading.progress = percent
                } else {
                    binding.progressReading.visibility = View.GONE
                }
            } else {
                binding.layoutReadingProgress.visibility = View.GONE
            }

            // Асинхронне рендерення мініатюри 1-ї сторінки (фолбек — іконка PDF)
            PdfThumbnailLoader.bindThumbnail(
                context = context,
                scope = scope,
                imageView = binding.ivPdfIcon,
                uriString = doc.uri,
                pageIndex = 0,
                targetWidthPx = 144
            )

            if (doc.isFavorite) {
                binding.ivFavorite.setImageResource(R.drawable.ic_star_filled)
            } else {
                binding.ivFavorite.setImageResource(R.drawable.ic_star_outline)
            }

            binding.root.setOnClickListener {
                onItemClicked(doc)
            }

            binding.ivFavorite.setOnClickListener {
                onFavoriteToggled(doc)
            }

            binding.ivMoreMenu.setOnClickListener { view ->
                showPopupMenu(view, doc)
            }
        }

        private fun showPopupMenu(anchor: View, doc: RecentDocument) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.inflate(R.menu.menu_document_item)
            val favItem = popup.menu.findItem(R.id.action_favorite)
            favItem?.setTitle(if (doc.isFavorite) R.string.action_unfavorite else R.string.action_favorite)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_favorite -> {
                        onFavoriteToggled(doc)
                        true
                    }
                    R.id.action_share -> {
                        onShareClicked(doc)
                        true
                    }
                    R.id.action_details -> {
                        onDetailsClicked(doc)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClicked(doc)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<RecentDocument>() {
            override fun areItemsTheSame(oldItem: RecentDocument, newItem: RecentDocument): Boolean {
                return oldItem.uri == newItem.uri
            }

            override fun areContentsTheSame(oldItem: RecentDocument, newItem: RecentDocument): Boolean {
                return oldItem == newItem
            }
        }
    }
}
