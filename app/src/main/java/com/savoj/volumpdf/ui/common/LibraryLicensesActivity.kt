package com.savoj.volumpdf.ui.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ActivityLibraryLicensesBinding

/** Власний edge-to-edge список OSS-ліцензій замість сторонньої Activity, яка мала дефект L7. */
class LibraryLicensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryLicensesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityLibraryLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val edges = SystemEdges.from(insets)
            WindowInsetsHelper.applyToolbarTopInset(binding.toolbar, edges.top)
            WindowInsetsHelper.applyRootInsets(binding.root, edges)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        loadEntries()
    }

    private fun loadEntries() {
        showLoading()
        Thread({
            val result = OssLicenseParser.loadIndex(
                readMetadata = {
                    resources.openRawResource(R.raw.third_party_license_metadata).bufferedReader(Charsets.UTF_8).use { it.readText() }
                },
                readBlob = { resources.openRawResource(R.raw.third_party_licenses).use { it.readBytes() } },
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isNullOrEmpty()) {
                    showError()
                } else {
                    binding.licensesList.adapter = LicenseNameAdapter(this, result)
                    binding.licensesList.setOnItemClickListener { _, _, position, _ ->
                        startActivity(LibraryLicenseDetailActivity.intent(this, result[position]))
                    }
                    binding.loading.visibility = View.GONE
                    binding.loadError.visibility = View.GONE
                    binding.licensesList.visibility = View.VISIBLE
                }
            }
        }, "oss-license-index").start()
    }

    private fun showLoading() {
        binding.loading.visibility = View.VISIBLE
        binding.loadError.visibility = View.GONE
        binding.licensesList.visibility = View.GONE
    }

    private fun showError() {
        binding.loading.visibility = View.GONE
        binding.licensesList.visibility = View.GONE
        binding.loadError.visibility = View.VISIBLE
    }

    private class LicenseNameAdapter(
        private val context: Context,
        private val entries: List<OssLicenseEntry>,
    ) : BaseAdapter() {
        private val density = context.resources.displayMetrics.density

        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): OssLicenseEntry = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val textView = (convertView as? TextView) ?: TextView(context).apply {
                minHeight = (56 * density).toInt()
                setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                maxLines = 2
            }
            textView.text = getItem(position).name
            return textView
        }
    }
}
