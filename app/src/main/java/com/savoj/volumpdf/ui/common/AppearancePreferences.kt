package com.savoj.volumpdf.ui.common

import android.content.Context
import android.content.SharedPreferences

/**
 * Налаштування вигляду застосунку. Поки одне: «Кольори зі шпалер» (Material You, Android 12+).
 * За замовчуванням ВИМКНЕНО: палітра Індіго #3949AB лишається основною (DECISIONS.md).
 * Пароль і вміст документів тут не зберігаються.
 */
class AppearancePreferences(context: Context) {

    // Лениво: створення об'єкта не торкається сховища. В ізольованому процесі (сервіс androidx.pdf,
    // isolatedProcess=true) getSharedPreferences кидає IllegalStateException; об'єкт там має бути безпечним.
    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var dynamicColorsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLORS, DYNAMIC_COLORS_DEFAULT)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLORS, value).apply()
        }

    companion object {
        const val PREFS_NAME = "appearance_preferences"
        const val KEY_DYNAMIC_COLORS = "dynamic_colors"
        const val DYNAMIC_COLORS_DEFAULT = false
    }
}
