package com.savoj.volumpdf

import android.app.Application
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.data.db.AppDatabase
import com.savoj.volumpdf.data.repository.DocumentRepository
import com.savoj.volumpdf.data.repository.DocumentRepositoryImpl

class VolumPdfApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val repository: DocumentRepository by lazy {
        DocumentRepositoryImpl(database)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Ініціалізація ресурсів PDFBox
        PdfBoxPageOperationsEngine.init(this)
        // Незбережене старше 7 днів прибираємо (фоновий потік, без блокування запуску).
        val pendingRoot = java.io.File(filesDir, "pending")
        Thread {
            try {
                com.savoj.volumpdf.ui.reader.PendingStore(pendingRoot)
                    .purgeOlderThan(System.currentTimeMillis(), com.savoj.volumpdf.ui.reader.PendingStore.MAX_AGE_MS)
            } catch (_: Exception) {
            }
        }.start()
    }

    companion object {
        lateinit var instance: VolumPdfApp
            private set
    }
}
