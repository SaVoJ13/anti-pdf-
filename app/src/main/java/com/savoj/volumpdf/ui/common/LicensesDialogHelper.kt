package com.savoj.volumpdf.ui.common

import android.content.Context
import android.content.Intent
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.savoj.volumpdf.R
import java.io.InputStream

/**
 * Екран ліцензій відкритого ПЗ.
 *
 * Тексти, яких немає в POM-файлах залежностей (Apache-2.0, Bouncy Castle, нотатки PdfBox-Android,
 * Liberation Sans, Roboto, SIL OFL 1.1), лежать в `assets/licenses` (файли .txt) (англійською, як опубліковано
 * авторами) і читаються з фонового потоку. Ліцензії бібліотек з POM, зокрема SDK Google Play services,
 * показує `OssLicensesMenuActivity` (плагін com.google.android.gms.oss-licenses-plugin).
 */
object LicensesDialogHelper {

    const val ASSETS_DIR = "licenses"

    /** Порядок розділів = порядок імен файлів. */
    val ASSET_FILES: List<String> = listOf(
        "00_intro.txt",
        "10_bouncycastle.txt",
        "20_pdfbox_android.txt",
        "30_liberation_sans.txt",
        "40_ofl_roboto.txt",
        "50_apache2.txt",
    )

    /** Склеює розділи; [open] віддає потік за іменем файла. Чиста функція (без Android) для тестів. */
    fun buildLicensesText(open: (String) -> InputStream): String =
        ASSET_FILES.joinToString(separator = "\n\n") { name ->
            open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }.trimEnd()
        }

    fun showLicensesDialog(context: Context) {
        val density = context.resources.displayMetrics.density
        val padH = (20 * density).toInt()
        val padV = (12 * density).toInt()

        val scrollView = ScrollView(context)
        val textView = TextView(context).apply {
            setPadding(padH, padV, padH, padV)
            text = "…"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        }
        scrollView.addView(textView)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_licenses_title)
            .setView(scrollView)
            .setPositiveButton(R.string.action_ok, null)
            .setNeutralButton(R.string.licenses_libraries_button) { _, _ -> showLibraryLicenses(context) }
            .show()

        // Читання assets не на головному потоці; текст виставляється після завантаження.
        val appContext = context.applicationContext
        Thread({
            val loaded = try {
                buildLicensesText { name -> appContext.assets.open("$ASSETS_DIR/$name") }
            } catch (e: java.io.IOException) {
                null
            }
            textView.post {
                textView.text = loaded ?: context.getString(R.string.licenses_load_failed)
                textView.setTextIsSelectable(loaded != null)
            }
        }, "licenses-loader").start()
    }

    private fun showLibraryLicenses(context: Context) {
        OssLicensesMenuActivity.setActivityTitle(context.getString(R.string.licenses_libraries_button))
        context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
    }
}
