package com.savoj.volumpdf.ui.reader

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ReadingThemeLogic {

    /**
     * Матриця колірної трансформації 4x5 для нічного режиму.
     * Забезпечує контрастність тексту понад 14:1 (вище стандарту WCAG AAA),
     * перетворюючи сліпучий білий фон (255) на м'який графітовий (#101010),
     * а чорний текст (0) — на приємний для читання білий (#E1E1E1).
     */
    private val NIGHT_MATRIX = floatArrayOf(
        -0.82f,  0.00f,  0.00f, 0.00f, 225.0f,
         0.00f, -0.82f,  0.00f, 0.00f, 225.0f,
         0.00f,  0.00f, -0.82f, 0.00f, 225.0f,
         0.00f,  0.00f,  0.00f, 1.00f,   0.0f
    )

    /**
     * Матриця для режиму «Сепія» (теплий вінтажний папір).
     * Білий фон трансформується у теплий айворі (#F5ECD7),
     * а чорний текст — у темно-коричневе чорнило (#2D2214) із коефіцієнтом контрасту >5.4:1.
     */
    private val SEPIA_MATRIX = floatArrayOf(
        0.784f, 0.000f, 0.000f, 0.00f, 45.0f,
        0.000f, 0.792f, 0.000f, 0.00f, 34.0f,
        0.000f, 0.000f, 0.765f, 0.00f, 20.0f,
        0.000f, 0.000f, 0.000f, 1.00f,  0.0f
    )

    fun matrixForTheme(theme: ReadingTheme): FloatArray? = when (theme) {
        ReadingTheme.DAY -> null
        ReadingTheme.NIGHT -> NIGHT_MATRIX.copyOf()
        ReadingTheme.SEPIA -> SEPIA_MATRIX.copyOf()
    }

    fun createPaint(theme: ReadingTheme): Paint? {
        val matrix = matrixForTheme(theme) ?: return null
        return Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
    }

    /**
     * Колір тла робочої області навколо сторінок переглядача.
     */
    fun containerBackgroundColor(theme: ReadingTheme): Int = when (theme) {
        ReadingTheme.DAY -> 0xFFF2F4F7.toInt()
        ReadingTheme.NIGHT -> 0xFF121212.toInt()
        ReadingTheme.SEPIA -> 0xFFEAE0CC.toInt()
    }

    /**
     * Математичне перетворення RGB для верифікації та тестів без необхідності завантаження Android UI стеків.
     */
    fun transformRgb(theme: ReadingTheme, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val m = matrixForTheme(theme) ?: return Triple(r, g, b)
        val newR = kotlin.math.round(m[0] * r + m[1] * g + m[2] * b + m[4]).toInt().coerceIn(0, 255)
        val newG = kotlin.math.round(m[5] * r + m[6] * g + m[7] * b + m[9]).toInt().coerceIn(0, 255)
        val newB = kotlin.math.round(m[10] * r + m[11] * g + m[12] * b + m[14]).toInt().coerceIn(0, 255)
        return Triple(newR, newG, newB)
    }
}
