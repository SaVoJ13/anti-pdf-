package com.savoj.volumpdf.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import com.savoj.volumpdf.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Асинхронне завантаження мініатюр сторінок PDF для блоку «Продовжити читання» та списку «Нещодавні»
 * з LRU-кешем у пам'яті й безпечним фолбеком на іконку PDF (для зашифрованих чи недоступних файлів).
 */
object PdfThumbnailLoader {

    private val memoryCache = object : LruCache<String, Bitmap>(24) {}

    fun bindThumbnail(
        context: Context,
        scope: CoroutineScope,
        imageView: ImageView,
        uriString: String,
        pageIndex: Int = 0,
        targetWidthPx: Int = 144
    ) {
        val cacheKey = "$uriString#$pageIndex"
        imageView.tag = cacheKey

        val cached = memoryCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            applyBitmap(imageView, cached)
            return
        }

        // Поки вантажиться або якщо недоступно — стильна PDF-іконка в кольорі індіго
        applyFallbackIcon(context, imageView)

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                renderPageThumbnail(context, uriString, pageIndex, targetWidthPx)
            }
            if (bitmap != null && imageView.tag == cacheKey) {
                memoryCache.put(cacheKey, bitmap)
                applyBitmap(imageView, bitmap)
            }
        }
    }

    private fun applyBitmap(imageView: ImageView, bitmap: Bitmap) {
        ImageViewCompat.setImageTintList(imageView, null)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setPadding(0, 0, 0, 0)
        imageView.setImageBitmap(bitmap)
    }

    private fun applyFallbackIcon(context: Context, imageView: ImageView) {
        val pad = (10 * context.resources.displayMetrics.density).toInt()
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setPadding(pad, pad, pad, pad)
        imageView.setImageResource(R.drawable.ic_pdf_document)
        val indigo = ContextCompat.getColor(context, R.color.pdf_badge_color)
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(indigo))
    }

    private fun renderPageThumbnail(
        context: Context,
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int
    ): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    val safePage = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(safePage).use { page ->
                        val w = targetWidthPx.coerceIn(96, 320)
                        val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                        val h = (w * ratio).toInt().coerceIn(96, 480)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
