package com.savoj.volumpdf.util

import android.graphics.Bitmap
import android.graphics.Matrix

object ImageRotationHelper {

    /** Нормалізує кут повороту до діапазону 0, 90, 180, 270. */
    fun normalize(deg: Int): Int = ((deg % 360) + 360) % 360

    /**
     * Повертає новий повернений бітмап на нормалізований кут [deg] (0, 90, 180, 270).
     * Якщо кут 0 — повертає оригінальний бітмап без клонування.
     */
    fun rotate(bitmap: Bitmap, deg: Int): Bitmap {
        val normalized = normalize(deg)
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
