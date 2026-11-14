package com.savoj.volumpdf.ui.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Гарантує, що прапорець «незбережене прочитано» стає true в усіх випадках: навіть якщо розбір відновленого стану
 * кинув виняток. Інакше «чекаю відновлення» (наприклад, збереження копії після am kill) зависло б назавжди.
 * Чиста логіка без Android: тестується в JVM. Деталі винятку не логуються (лише тип), вміст документів не потрапляє в лог.
 */
object PendingLoadGuard {
    suspend fun run(onFailure: (Throwable) -> Unit, markLoaded: () -> Unit, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onFailure(e)
        } finally {
            markLoaded()
        }
    }
}

/** Очікування відновлення незбереженого з обмеженням у часі: збереження копії не має висіти вічно. */
object PendingWait {
    /** Скільки максимум чекаємо відновлення незбереженого, перш ніж чесно припинити збереження копії. */
    const val TIMEOUT_MS = 10_000L

    /** true, якщо [loaded] став true вчасно; false після таймауту. */
    suspend fun awaitLoaded(loaded: Flow<Boolean>, timeoutMs: Long = TIMEOUT_MS): Boolean =
        withTimeoutOrNull(timeoutMs) { loaded.first { it } } != null
}
