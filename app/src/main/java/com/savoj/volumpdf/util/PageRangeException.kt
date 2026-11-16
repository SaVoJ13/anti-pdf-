package com.savoj.volumpdf.util

/**
 * Помилка введення номерів сторінок. Нащадок [IllegalArgumentException], тож старі перехоплення працюють;
 * [reason] і числа дають змогу показати людський текст із ресурсів, а [message] лишається технічним (лише для логу).
 */
class PageRangeException(
    val reason: Reason,
    val page: Int = 0,
    val totalPages: Int = 0,
    technical: String
) : IllegalArgumentException(technical) {
    enum class Reason { EMPTY, BAD_FORMAT, NOT_POSITIVE, OUT_OF_RANGE, NONE_SELECTED }
}
