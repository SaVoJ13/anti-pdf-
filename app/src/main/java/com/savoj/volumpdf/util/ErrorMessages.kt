package com.savoj.volumpdf.util

import android.util.Log
import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.pdf.model.PdfPermissionException
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Єдиний перекладач виняток -> людський текст («що сталося» і «що робити»). Сире `localizedMessage` людині не показуємо:
 * воно може містити шляхи, URI та англійську технічну мову. Подробиці йдуть лише в лог ([report]) і без персональних даних
 * (вид помилки та ім'я класу виняття; жодних URI, імен файлів чи паролів).
 */
object ErrorMessages {

    enum class Kind { PASSWORD, NO_PERMISSION, READ_ONLY, NOT_FOUND, NO_SPACE, TOO_BIG, PAGE_RANGE, IO, OTHER }

    private const val MAX_CAUSE_DEPTH = 6

    /** Вид помилки за ланцюжком причин; чиста функція (тестується в JVM). */
    fun classify(cause: Throwable): Kind {
        var t: Throwable? = cause
        var depth = 0
        var seenNotFound = false
        var seenIo = false
        while (t != null && depth < MAX_CAUSE_DEPTH) {
            when {
                t is OutOfMemoryError -> return Kind.TOO_BIG
                t is PageRangeException -> return Kind.PAGE_RANGE
                t is PdfPermissionException -> return Kind.NO_PERMISSION
                t.javaClass.simpleName == "InvalidPasswordException" -> return Kind.PASSWORD
                t is SecurityException -> return Kind.READ_ONLY
            }
            val text = t.message.orEmpty()
            when {
                text.contains("ENOSPC", true) || text.contains("No space left", true) -> return Kind.NO_SPACE
                text.contains("EACCES", true) || text.contains("EPERM", true) || text.contains("EROFS", true) ||
                    text.contains("Permission denied", true) || text.contains("Read-only file system", true) ||
                    text.contains("has no access", true) -> return Kind.READ_ONLY
            }
            if (t is FileNotFoundException) seenNotFound = true
            if (t is IOException) seenIo = true
            val next = t.cause
            t = if (next === t) null else next
            depth++
        }
        return when {
            seenNotFound -> Kind.NOT_FOUND
            seenIo -> Kind.IO
            else -> Kind.OTHER
        }
    }

    /**
     * Текст для людини. [fallback] — рядок для невпізнаних помилок (що саме не вдалося, напр. «Не вдалося створити PDF»).
     */
    fun forFailure(cause: Throwable, fallback: Int = R.string.err_generic): UserMessage {
        if (cause is PageRangeException) return pageRange(cause)
        return when (classify(cause)) {
            Kind.PASSWORD -> UserMessage(R.string.error_invalid_password)
            Kind.NO_PERMISSION -> UserMessage(R.string.err_no_permission)
            Kind.READ_ONLY -> UserMessage(R.string.err_read_only)
            Kind.NOT_FOUND -> UserMessage(R.string.err_not_found)
            Kind.NO_SPACE -> UserMessage(R.string.err_no_space)
            Kind.TOO_BIG -> UserMessage(R.string.err_too_big)
            Kind.PAGE_RANGE -> UserMessage(R.string.err_pages_format)
            Kind.IO -> UserMessage(R.string.err_io)
            Kind.OTHER -> UserMessage(fallback)
        }
    }

    /** Текст під заголовком екрана «Не вдалося відкрити файл». */
    fun forOpenFailure(cause: Throwable): UserMessage = when (classify(cause)) {
        Kind.READ_ONLY -> UserMessage(R.string.open_error_denied)
        Kind.NOT_FOUND -> UserMessage(R.string.err_not_found)
        Kind.NO_SPACE -> UserMessage(R.string.err_no_space)
        Kind.TOO_BIG -> UserMessage(R.string.err_too_big)
        else -> UserMessage(R.string.open_error_corrupt)
    }

    /**
     * Чи прибирати запис з «Останніх» після невдалого ПЕРШОГО відкриття: коли файл пошкоджено/порожній (IO, інше)
     * або доступу до нього немає (READ_ONLY тут = відмова в доступі при відкритті; QA2 Д5: мертвий рядок, який не відкрити).
     * Файл, відкритий нормально, але без права запису, сюди не потрапляє: це не помилка відкриття.
     * Для «не знайдено», «мало місця», «завеликий» файл може бути справним, запис лишаємо.
     */
    fun shouldDropFromRecents(cause: Throwable): Boolean = when (classify(cause)) {
        Kind.IO, Kind.OTHER, Kind.READ_ONLY -> true
        else -> false
    }

    fun pageRange(e: PageRangeException): UserMessage = when (e.reason) {
        PageRangeException.Reason.EMPTY -> UserMessage(R.string.err_pages_empty)
        PageRangeException.Reason.BAD_FORMAT -> UserMessage(R.string.err_pages_format)
        PageRangeException.Reason.NOT_POSITIVE -> UserMessage(R.string.err_pages_positive)
        PageRangeException.Reason.OUT_OF_RANGE -> UserMessage(R.string.err_pages_range, listOf(e.page, e.totalPages))
        PageRangeException.Reason.NONE_SELECTED -> UserMessage(R.string.err_pages_none)
    }

    /** Технічні подробиці лише в лог: вид і клас виняття, без повідомлення (воно може містити URI чи ім'я файлу). */
    fun report(tag: String, cause: Throwable) {
        if (cause is CancellationException) return
        Log.w(tag, "failure kind=${classify(cause)} type=${cause.javaClass.simpleName}")
    }
}
