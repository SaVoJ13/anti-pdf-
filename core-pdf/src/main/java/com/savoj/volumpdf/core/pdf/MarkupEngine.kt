package com.savoj.volumpdf.core.pdf

import android.content.Context
import android.graphics.PointF
import android.net.Uri
import com.savoj.volumpdf.core.pdf.model.FormFieldUpdate
import com.savoj.volumpdf.core.pdf.model.MarkKind
import com.savoj.volumpdf.core.pdf.model.PageMarkup
import com.savoj.volumpdf.core.pdf.model.PdfOperationResult

/** Розміщення тексту, ✓/✗, рукописного малювання й підпису на сторінці + заповнення форм (CONTRACTS v0.4, §1.5). */
interface MarkupEngine {
    // Повертає символи, яких немає у вбудованому шрифті (порожній рядок = усе підтримується)
    fun findUnsupportedChars(text: String): String

    // Одне завантаження, одне збереження: спершу formUpdates, потім markups у заданому порядку.
    suspend fun applyEdits(
        context: Context,
        sourceUri: Uri,
        markups: List<PageMarkup>,
        formUpdates: List<FormFieldUpdate> = emptyList(),
        outputUri: Uri,
        password: String? = null
    ): PdfOperationResult
}

// Спільні константи розкладки: двигун пише в PDF за ними, UI малює оверлей за ними.
// Умовні значення (не метрики шрифту); змінювати лише разом з UI.
object MarkupLayout {
    const val TEXT_ASCENT_RATIO = 0.93f      // базова лінія 1-го рядка = y + ratio × fontSizePt
    const val TEXT_LINE_HEIGHT_RATIO = 1.2f  // крок між рядками; розрив рядка лише за '\n', автопереносу немає
    const val MARK_STROKE_RATIO = 0.12f      // товщина лінії ✓/✗ = ratio × sizePt; кінці й стики округлені
    const val FONT_ASSET_PATH = "fonts/Roboto-Regular.ttf"

    // Ламані ✓/✗ у квадраті 0..1, початок зверху-ліворуч; позначка центрується в (x, y) і має розмір sizePt
    // CHECK: [(0.15,0.55),(0.40,0.80),(0.85,0.20)]
    // CROSS: [(0.2,0.2),(0.8,0.8)] і [(0.8,0.2),(0.2,0.8)]
    fun markPolylines(kind: MarkKind): List<List<PointF>> = when (kind) {
        MarkKind.CHECK -> listOf(listOf(p(0.15f, 0.55f), p(0.40f, 0.80f), p(0.85f, 0.20f)))
        MarkKind.CROSS -> listOf(
            listOf(p(0.2f, 0.2f), p(0.8f, 0.8f)),
            listOf(p(0.8f, 0.2f), p(0.2f, 0.8f))
        )
    }

    // Поля задаються явно: працює і в JVM-тестах із заглушкою android.jar
    private fun p(x: Float, y: Float): PointF = PointF().also { it.x = x; it.y = y }
}
