package com.savoj.volumpdf.util

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.data.db.RecentDocument
import com.savoj.volumpdf.data.repository.DocumentRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Запис відкриття в «Останні» разом із відповіддю «документа там ще не було?».
 * Питання треба ставити ДО запису: після нього запис є завжди (так і ламалось прибирання після невдалого першого відкриття).
 */
object RecentsOpenTracker {

    data class OpenRecord(val document: RecentDocument, val openedAsNew: Boolean)

    /**
     * Записує відкриття. [knownNew] — відповідь, визначена раніше (передана читачу з екрана, що вже записав документ);
     * `null` — визначити тут, до запису.
     */
    suspend fun record(
        repository: DocumentRepository,
        context: Context,
        uri: Uri,
        pageCountOverride: Int = 0,
        knownNew: Boolean? = null
    ): OpenRecord {
        val asNew = knownNew ?: (repository.getDocument(uri.toString()) == null)
        val doc = repository.recordDocumentOpened(context, uri, pageCountOverride)
        return OpenRecord(doc, asNew)
    }

    /**
     * Після помилки відкриття прибирає запис, лише якщо це було ПЕРШЕ відкриття і файл пошкоджено/порожній
     * ([ErrorMessages.shouldDropFromRecents]). Видалення не скасовується разом з екраном («Закрити» одразу після помилки).
     */
    suspend fun dropIfFirstOpenFailed(
        repository: DocumentRepository,
        uriString: String,
        openedAsNew: Boolean,
        cause: Throwable
    ): Boolean {
        if (!openedAsNew || !ErrorMessages.shouldDropFromRecents(cause)) return false
        withContext(NonCancellable) { repository.deleteDocument(uriString) }
        return true
    }
}
