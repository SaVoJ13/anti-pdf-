# CONTRACTS — контракти між модулями
Веде лише Керівник проєкту. Версії v0.N.

## v0.1 (Затверджено для MVP, 2026-11-03)

### 1. Модуль `core-pdf` ↔ `app`

Модуль `core-pdf` ізолює роботу з PDF-бібліотеками (`androidx.pdf` та `PDFBox-Android`) і надає UI-модулю `app` чисті інтерфейси та Kotlin Data Classes/Flow.

#### 1.1 Моделі даних (`core-pdf:models`)
```kotlin
package com.pdfreader.core.pdf.model

import android.net.Uri

data class PdfDocumentMetadata(
    val uri: Uri,
    val title: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val isEncrypted: Boolean
)

sealed interface PdfLoadResult {
    data class Success(val metadata: PdfDocumentMetadata) : PdfLoadResult
    data class PasswordRequired(val isInvalidAttempt: Boolean) : PdfLoadResult
    data class Error(val cause: Throwable, val userMessage: String) : PdfLoadResult
}

sealed interface PdfOperationResult {
    data class Success(val outputUri: Uri, val pagesCount: Int) : PdfOperationResult
    data class Progress(val currentStep: Int, val totalSteps: Int) : PdfOperationResult
    data class Error(val cause: Throwable, val userMessage: String) : PdfOperationResult
}
```

#### 1.2 Інтерфейс інструментів сторінок (`PageOperationsEngine`)
```kotlin
package com.pdfreader.core.pdf

import android.content.Context
import android.net.Uri
import com.pdfreader.core.pdf.model.PdfOperationResult
import kotlinx.coroutines.flow.Flow

interface PageOperationsEngine {
    // Злиття кількох PDF у новий файл
    fun mergePdfs(context: Context, sourceUris: List<Uri>, outputUri: Uri): Flow<PdfOperationResult>

    // Розділення: експорт обраних сторінок у новий файл
    fun extractPages(context: Context, sourceUri: Uri, pageIndices: List<Int>, outputUri: Uri, password: String? = null): Flow<PdfOperationResult>

    // Поворот сторінок на заданий кут (90, 180, 270)
    suspend fun rotatePages(context: Context, sourceUri: Uri, pageIndices: List<Int>, degrees: Int, outputUri: Uri, password: String? = null): PdfOperationResult

    // Видалення сторінок
    suspend fun deletePages(context: Context, sourceUri: Uri, pageIndicesToDelete: List<Int>, outputUri: Uri, password: String? = null): PdfOperationResult

    // Створення PDF із зображень (JPEG/PNG)
    suspend fun imagesToPdf(context: Context, imageUris: List<Uri>, outputUri: Uri): PdfOperationResult
}
```

---

### 2. Модуль `core-ads` ↔ `app`

Модуль `core-ads` відповідає за стан згоди GDPR/UMP та безпечний показ реклами без порушення політик Google Play.

```kotlin
package com.pdfreader.core.ads

import android.app.Activity
import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

enum class ConsentStatus {
    UNKNOWN,
    REQUIRED_NOT_OBTAINED,
    OBTAINED,
    NOT_REQUIRED
}

interface AdsManager {
    // Потік стану згоди UMP
    val consentStatus: StateFlow<ConsentStatus>

    // Ініціалізація та показ діалогу згоди Google UMP
    fun requestConsentAndInit(activity: Activity, onComplete: () -> Unit)

    // Прив'язка адаптивного банера до контейнера у списку (безпечно згортається при помилці/офлайн)
    fun bindAdaptiveBanner(activity: Activity, container: ViewGroup)

    // Показ Interstitial реклами після завершення дії з обмеженням частоти (cooldown, напр. 5 хв)
    fun showActionInterstitial(activity: Activity, onDismissed: () -> Unit)
}
```
