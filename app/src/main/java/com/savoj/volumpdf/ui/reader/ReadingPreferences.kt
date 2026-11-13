package com.savoj.volumpdf.ui.reader

import android.content.Context
import android.content.SharedPreferences

/**
 * Збереження та відновлення налаштувань режиму читання.
 */
class ReadingPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var theme: ReadingTheme
        get() = ReadingTheme.fromStorageKey(prefs.getString(KEY_THEME, ReadingTheme.DAY.storageKey))
        set(value) {
            prefs.edit().putString(KEY_THEME, value.storageKey).apply()
        }

    companion object {
        private const val PREFS_NAME = "reading_preferences"
        private const val KEY_THEME = "reading_theme"
    }
}
