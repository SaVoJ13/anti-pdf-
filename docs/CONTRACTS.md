# CONTRACTS — контракти між модулями
Веде лише Керівник проєкту. Версії v0.N.

## v0.3 (Затверджено: Етап 2.2 — Інтерактивні форми AcroForm та ергономіка UI, 2026-11-04)

### 1. Модуль `core-pdf` ↔ `app`

Модуль `core-pdf` ізолює роботу з PDF-бібліотеками (`androidx.pdf` та `PDFBox-Android`) і надає UI-модулю `app` чисті інтерфейси та Kotlin Data Classes/Flow.

#### 1.1 Моделі даних (`core-pdf:models`)
```kotlin
package com.savoj.volumpdf.core.pdf.model

import android.graphics.PointF
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

// Моделі для анотацій та редагування (v0.2)
data class InkStroke(
    val points: List<PointF>,
    val color: Int, // ARGB
    val strokeWidth: Float
)

data class FreeTextData(
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float = 14f,
    val color: Int // ARGB
)

// Моделі для інтерактивних форм AcroForm (v0.3)
sealed interface FormField {
    val fullyQualifiedName: String
    val partialName: String
    val alternateTitle: String? // Опис/підказка поля (TU запис)
    val isReadOnly: Boolean

    data class Text(
        override val fullyQualifiedName: String,
        override val partialName: String,
        override val alternateTitle: String?,
        override val isReadOnly: Boolean,
        val value: String,
        val isMultiline: Boolean
    ) : FormField

    data class CheckBox(
        override val fullyQualifiedName: String,
        override val partialName: String,
        override val alternateTitle: String?,
        override val isReadOnly: Boolean,
        val isChecked: Boolean,
        val onValue: String = "Yes"
    ) : FormField

    data class Choice(
        override val fullyQualifiedName: String,
        override val partialName: String,
        override val alternateTitle: String?,
        override val isReadOnly: Boolean,
        val options: List<String>,
        val selectedValue: String?,
        val isEditableCombo: Boolean
    ) : FormField
}

data class FormFieldUpdate(
    val fullyQualifiedName: String,
    val value: Any // String для Text/Choice, Boolean для CheckBox
)
```

#### 1.2 Інтерфейс інструментів сторінок (`PageOperationsEngine`)
```kotlin
package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult
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

#### 1.3 Інтерфейс анотацій та редагування (`AnnotationEngine`) (v0.2)
```kotlin
package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FreeTextData
import com.savoj.volumpdf.core.pdf.model.InkStroke
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult

interface AnnotationEngine {
    // Запис рукописного малювання (S Pen / палець) на задану сторінку (/Ink анотація ISO 32000-1)
    suspend fun addInkAnnotations(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        strokes: List<InkStroke>,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult

    // Вставка підпису як штампа/зображення на сторінку
    suspend fun addSignatureStamp(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        signatureBitmap: Bitmap,
        targetRectPt: RectF,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult

    // Додавання тексту поверх документа (/FreeText)
    suspend fun addFreeText(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        textData: FreeTextData,
        pageWidthPt: Float,
        pageHeightPt: Float,
        outputUri: Uri
    ): PdfOperationResult
}
```

#### 1.4 Інтерфейс роботи з інтерактивними формами (`FormEngine`) (v0.3)
```kotlin
package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormField
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult

interface FormEngine {
    // Отримання списку всіх інтерактивних полів документа
    suspend fun extractFormFields(context: Context, sourceUri: Uri): List<FormField>

    // Запис оновлених значень у поля форми та збереження в новий або той самий URI
    suspend fun fillFormFields(
        context: Context,
        sourceUri: Uri,
        updates: List<FormFieldUpdate>,
        outputUri: Uri
    ): PdfOperationResult
}
```

---

### 2. Модуль `core-ads` ↔ `app`

Модуль `core-ads` відповідає за стан згоди GDPR/UMP та безпечний показ реклами без порушення політик Google Play.

```kotlin
package com.savoj.volumpdf.core.ads

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
