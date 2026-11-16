package com.savoj.volumpdf.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Відгук вібрацією (P1-7). Працює через [View.performHapticFeedback], тому не потребує дозволу VIBRATE
 * і поважає системне налаштування «Вібровідгук» (прапорець IGNORE_GLOBAL_SETTING не ставимо).
 */
object Haptics {

    enum class Kind { TICK, CONFIRM, REJECT }

    /** Константа відгуку для [kind] на рівні API [sdk]; чиста функція (тестується в JVM). */
    fun constantFor(kind: Kind, sdk: Int): Int = when (kind) {
        Kind.TICK -> if (sdk >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.VIRTUAL_KEY
        Kind.CONFIRM -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        Kind.REJECT -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }

    fun perform(view: View?, kind: Kind) {
        view?.performHapticFeedback(constantFor(kind, Build.VERSION.SDK_INT))
    }
}

/** Легкий «клік» (вибір інструмента чи кольору, прив'язка до краю). */
fun View.hapticTick() = Haptics.perform(this, Haptics.Kind.TICK)

/** Підтвердження (дію виконано). */
fun View.hapticConfirm() = Haptics.perform(this, Haptics.Kind.CONFIRM)

/** Відхилення/видалення (дію не виконано чи об'єкт прибрано). */
fun View.hapticReject() = Haptics.perform(this, Haptics.Kind.REJECT)
