package com.savoj.volumpdf.core.pdf.model

import android.graphics.PointF

/**
 * Рукописний штрих для малювання на сторінці PDF (/Ink анотація ISO 32000-1).
 *
 * @param points Список точок штриха в системі координат екрана/полотна
 * @param color Колір штриха у форматі ARGB
 * @param strokeWidth Товщина лінії в точках (pt)
 */
data class InkStroke(
    val points: List<PointF>,
    val color: Int,
    val strokeWidth: Float
)
