package com.savoj.volumpdf.core.pdf.model

/**
 * Рівень оптимізації та стиснення зображень і потоків PDF (ТОП-5).
 */
enum class CompressionLevel(val maxDimension: Int, val jpegQuality: Float) {
    LOW(1800, 0.85f),       // Мінімальне стиснення, максимальна якість
    MEDIUM(1400, 0.70f),    // Збалансоване (рекомендоване)
    HIGH(1000, 0.50f)       // Максимальне стиснення для мінімального розміру
}
