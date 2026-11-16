package com.savoj.volumpdf.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Готовий до показу людині текст: ресурсний рядок + аргументи. Не містить технічних подробиць виключення.
 * ViewModel-и тримають саме це (а не рядок), щоб текст брався з ресурсів поточної мови під час показу.
 */
data class UserMessage(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String =
        if (args.isEmpty()) context.getString(res) else context.getString(res, *args.toTypedArray())
}
