package com.savoj.volumpdf.ui.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.savoj.volumpdf.R
import com.savoj.volumpdf.databinding.ActivityLibraryLicenseDetailBinding

/** Деталі однієї згенерованої OSS-ліцензії з контрольованими системними відступами. */
class LibraryLicenseDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryLicenseDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityLibraryLicenseDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { getString(R.string.licenses_detail_title) }
        val entry = OssLicenseEntry(
            name = name,
            offset = intent.getIntExtra(EXTRA_OFFSET, -1),
            length = intent.getIntExtra(EXTRA_LENGTH, -1),
        )
        binding.toolbar.title = name
        binding.toolbar.setNavigationOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val edges = SystemEdges.from(insets)
            WindowInsetsHelper.applyToolbarTopInset(binding.toolbar, edges.top)
            WindowInsetsHelper.applyRootInsets(binding.root, edges)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        loadText(entry)
    }

    private fun loadText(entry: OssLicenseEntry) {
        binding.loading.visibility = View.VISIBLE
        binding.licenseText.visibility = View.GONE
        Thread({
            val text = runCatching {
                val bytes = resources.openRawResource(R.raw.third_party_licenses).use { it.readBytes() }
                OssLicenseParser.licenseText(bytes, entry)
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.loading.visibility = View.GONE
                binding.licenseText.visibility = View.VISIBLE
                binding.licenseText.text = text ?: getString(R.string.licenses_load_failed)
                binding.licenseText.setTextIsSelectable(text != null)
            }
        }, "oss-license-detail").start()
    }

    companion object {
        private const val EXTRA_NAME = "license_name"
        private const val EXTRA_OFFSET = "license_offset"
        private const val EXTRA_LENGTH = "license_length"

        fun intent(context: Context, entry: OssLicenseEntry): Intent =
            Intent(context, LibraryLicenseDetailActivity::class.java)
                .putExtra(EXTRA_NAME, entry.name)
                .putExtra(EXTRA_OFFSET, entry.offset)
                .putExtra(EXTRA_LENGTH, entry.length)
    }
}
