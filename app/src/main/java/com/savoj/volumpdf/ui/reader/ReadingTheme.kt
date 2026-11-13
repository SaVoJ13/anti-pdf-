package com.savoj.volumpdf.ui.reader

/**
 * Тема відображення сторінок документа під час читання (ТОП-2):
 * - [DAY]: Оригінальні кольори документа (білий аркуш, чорний текст).
 * - [NIGHT]: Комфортний нічний режим (темний аркуш, м'який білий текст для читання в темряві).
 * - [SEPIA]: Теплий папір (пастельне тепле тло з глибоким темним чорнилом, що знімає втому очей).
 */
enum class ReadingTheme(val storageKey: String) {
    DAY("day"),
    NIGHT("night"),
    SEPIA("sepia");

    companion object {
        fun fromStorageKey(key: String?): ReadingTheme =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DAY
    }
}
