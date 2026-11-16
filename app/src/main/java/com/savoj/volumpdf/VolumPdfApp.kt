package com.savoj.volumpdf

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.savoj.volumpdf.core.pdf.PdfBoxPageOperationsEngine
import com.savoj.volumpdf.data.db.AppDatabase
import com.savoj.volumpdf.data.repository.DocumentRepository
import com.savoj.volumpdf.data.repository.DocumentRepositoryImpl
import com.savoj.volumpdf.ui.common.AppearancePreferences

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
        // Material You: лише якщо користувач сам увімкнув «Кольори зі шпалер» (за замовчуванням вимкнено).
        // Умова перевіряється при створенні кожної Activity, тож перемикач діє після recreate() без втрати стану.
        val appearance = AppearancePreferences(this)
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> appearance.dynamicColorsEnabled }
                .build()
        )
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
