package com.savoj.volumpdf.ui.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.savoj.volumpdf.R
import com.savoj.volumpdf.VolumPdfApp
import com.savoj.volumpdf.core.pdf.PageOperationsEngine
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
import com.savoj.volumpdf.databinding.ActivityScanDocumentBinding
import com.savoj.volumpdf.ui.common.SystemEdges
import com.savoj.volumpdf.ui.common.WindowInsetsHelper
import com.savoj.volumpdf.ui.reader.ReaderActivity
import com.savoj.volumpdf.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Екран вбудованого документ-сканера (ТОП-1).
 * Запускає сканер Google Play Services із автовизначенням меж, вирівнюванням кутів та покращенням контрасту.
 * Зберігає готовий чистий PDF без водяних знаків та відкриває його в читалці.
 */
class ScanDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanDocumentBinding
    private val engine: PageOperationsEngine = PdfBoxPageOperationsEngine()

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleScanSuccess(result.data)
        } else {
            // Користувач скасував сканування
            finish()
        }
    }

    private val selectImagesFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            convertImagesToPdfAndOpen(uris)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        applyWindowInsets()
        setupListeners()

        startGmsDocumentScanner()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootScanner) { _, insets ->
            val edges = SystemEdges.from(insets)
            binding.rootScanner.setPadding(edges.left, 0, edges.right, 0)
            WindowInsetsHelper.applyToolbarTopInset(binding.toolbar, edges.top)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupListeners() {
        binding.btnFallbackPhotos.setOnClickListener {
            selectImagesFallbackLauncher.launch(arrayOf("image/jpeg", "image/png", "image/*"))
        }
    }

    private fun startGmsDocumentScanner() {
        binding.layoutProcessing.visibility = View.VISIBLE
        binding.layoutFallback.visibility = View.GONE

        val options = DocumentScannerLogic.buildScannerOptions(pageLimit = 100)
        val scanner = GmsDocumentScanning.getClient(options)

        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                // Play Services недоступні або сталася помилка
                binding.layoutProcessing.visibility = View.GONE
                binding.layoutFallback.visibility = View.VISIBLE
            }
    }

    private fun handleScanSuccess(intentData: Intent?) {
        binding.layoutProcessing.visibility = View.VISIBLE
        binding.layoutFallback.visibility = View.GONE
        binding.tvStatus.text = getString(R.string.scan_processing)

        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(intentData)
        val pdfUri = scanResult?.pdf?.uri

        if (pdfUri == null) {
            Toast.makeText(this, R.string.error_title, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val pageCount = scanResult.pages?.size ?: 1
        lifecycleScope.launch {
            try {
                val fileName = DocumentScannerLogic.generateScannedFileName()
                val persistedFile = withContext(Dispatchers.IO) {
                    DocumentScannerLogic.persistScannedPdf(this@ScanDocumentActivity, pdfUri, fileName)
                }

                val persistentUri = Uri.fromFile(persistedFile)
                val repository = (application as VolumPdfApp).repository
                repository.recordDocumentOpened(this@ScanDocumentActivity, persistentUri, pageCount)

                openScannedDocument(persistentUri, persistedFile.name)
            } catch (e: Exception) {
                Toast.makeText(this@ScanDocumentActivity, e.localizedMessage ?: "Помилка збереження", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun convertImagesToPdfAndOpen(imageUris: List<Uri>) {
        binding.layoutProcessing.visibility = View.VISIBLE
        binding.layoutFallback.visibility = View.GONE
        binding.tvStatus.text = getString(R.string.scan_processing)

        lifecycleScope.launch {
            try {
                imageUris.forEach { uri ->
                    SafUtils.takePersistableUriPermissionSafe(this@ScanDocumentActivity, uri)
                }
                val fileName = DocumentScannerLogic.generateScannedFileName()
                val targetFile = File(File(filesDir, "scanned").apply { mkdirs() }, fileName)
                val outputUri = Uri.fromFile(targetFile)

                val result = withContext(Dispatchers.IO) {
                    engine.imagesToPdf(this@ScanDocumentActivity, imageUris, outputUri)
                }

                when (result) {
                    is PdfOperationResult.Success -> {
                        val repository = (application as VolumPdfApp).repository
                        repository.recordDocumentOpened(this@ScanDocumentActivity, outputUri, result.pagesCount)
                        openScannedDocument(outputUri, targetFile.name)
                    }
                    is PdfOperationResult.Error -> {
                        Toast.makeText(this@ScanDocumentActivity, result.userMessage, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    else -> finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ScanDocumentActivity, e.localizedMessage ?: "Помилка конвертації", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun openScannedDocument(uri: Uri, title: String) {
        val readerIntent = Intent(this, ReaderActivity::class.java).apply {
            data = uri
            putExtra(ReaderActivity.EXTRA_DOCUMENT_TITLE, title)
        }
        startActivity(readerIntent)
        finish()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ScanDocumentActivity::class.java)
            context.startActivity(intent)
        }
    }
}
