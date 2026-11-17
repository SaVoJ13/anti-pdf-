package com.savoj.volumpdf.ui.onboarding

import android.content.Context
import android.content.SharedPreferences

/**
 * Чиста логіка онбордингу першого запуску (без Android-залежностей, перевіряється JVM-тестами).
 * Онбординг: 3 екрани, на кожному можна пропустити; без мережі, акаунтів і аналітики.
 */
object OnboardingLogic {
    const val PAGE_COUNT = 3

    /**
     * Показувати онбординг лише при першому запуску і лише при звичайному старті з іконки.
     * Запуск із зовнішнього застосунку для перегляду/редагування PDF ([isDocumentIntent]) онбординг мінує:
     * користувач хоче бачити документ. Прапорець у такому разі не ставимо: екран з’явиться при наступному звичайному запуску.
     */
    fun shouldShow(alreadyShown: Boolean, isDocumentIntent: Boolean): Boolean =
        !alreadyShown && !isDocumentIntent

    /** Чи є інтент запитом на відкриття документа (VIEW/EDIT з даними). */
    fun isDocumentIntent(action: String?, hasData: Boolean): Boolean =
        hasData && (action == ACTION_VIEW || action == ACTION_EDIT)

    fun isLastPage(index: Int): Boolean = index >= PAGE_COUNT - 1

    /** Наступний екран; на останньому лишається останній (кнопка там «Почати», а не «Далі»). */
    fun nextPage(index: Int): Int = (index + 1).coerceAtMost(PAGE_COUNT - 1)

    /** Безпечний індекс із відновленого стану. */
    fun clampPage(index: Int): Int = index.coerceIn(0, PAGE_COUNT - 1)

    // Значення android.content.Intent.ACTION_VIEW/ACTION_EDIT: константи, щоб логіка лишалась чистою для JVM-тестів.
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_EDIT = "android.intent.action.EDIT"
}

/**
 * Локальний прапорець «онбординг показано» (SharedPreferences, лише основний процес).
 * Створюється з Activity, НЕ з Application.onCreate (там ізольований процес androidx.pdf: див. LESSONS).
 * Ніяких даних користувача не зберігає, лише один булів прапорець.
 */
class OnboardingPreferences(context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var shown: Boolean
        get() = prefs.getBoolean(KEY_SHOWN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOWN, value).apply()
        }

    companion object {
        const val PREFS_NAME = "onboarding_preferences"
        const val KEY_SHOWN = "onboarding_shown"
    }
}
