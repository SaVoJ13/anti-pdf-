package com.savoj.volumpdf.ui.reader

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Копії збереженого результату для переглядача (R12, спосіб b): cacheDir/view/view_<nano>.pdf.
 * Унікальне ім'я робить URI «новим», тож PdfViewerFragment точно перезавантажує документ.
 * Живе лише поточна копія.
 */
object ViewerCopyCache {

    const val DIR_NAME = "view"
    private const val PREFIX = "view_"
    private const val SUFFIX = ".pdf"

    data class CopyResult(val file: File, val bytes: Long, val elapsedMs: Long)

    fun viewDir(cacheDir: File): File = File(cacheDir, DIR_NAME)

    fun isViewCopy(cacheDir: File, uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "file") return false
        val path = uri.path ?: return false
        return File(path).parentFile?.canonicalPath == viewDir(cacheDir).canonicalPath
    }

    /** Копіює [open] у нову унікальну копію. Може кинути IOException. */
    fun copy(cacheDir: File, open: () -> InputStream): CopyResult {
        val dir = viewDir(cacheDir)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            throw java.io.IOException("Не вдалося створити каталог $dir")
        }
        val target = File(dir, "$PREFIX${System.nanoTime()}$SUFFIX")
        val startNs = System.nanoTime()
        try {
            open().use { input -> target.outputStream().use { out -> input.copyTo(out, 256 * 1024) } }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        return CopyResult(target, target.length(), elapsedMs)
    }

    fun openSource(resolver: ContentResolver, uri: Uri): InputStream =
        if (uri.scheme == "file") {
            val path = uri.path ?: throw IllegalArgumentException("Некоректний file URI: $uri")
            FileInputStream(File(path))
        } else {
            resolver.openInputStream(uri) ?: throw FileNotFoundException("Неможливо відкрити: $uri")
        }

    /** Видаляє всі копії, крім [keep] (null — усі). Повертає кількість видалених. */
    fun purge(cacheDir: File, keep: File? = null): Int {
        val keepPath = keep?.let { runCatching { it.canonicalPath }.getOrNull() }
        var removed = 0
        viewDir(cacheDir).listFiles()?.forEach { f ->
            if (f.isFile && (keepPath == null || f.canonicalPath != keepPath) && f.delete()) removed++
        }
        return removed
    }
}
