package com.savoj.volumpdf.core.pdf.model

/**
 * Дані для нанесення вільного тексту поверх сторінки PDF (/FreeText).
 *
 * @param text Текстовий рядок для нанесення
 * @param x Початкова горизонтальна координата X (pt)
 * @param y Початкова вертикальна координата Y від верхнього краю сторінки (pt)
 * @param fontSize Розмір шрифту в пунктах (за замовчуванням 14f)
 * @param color Колір тексту у форматі ARGB
 */
data class FreeTextData(
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float = 14f,
    val color: Int
)
