package com.savoj.volumpdf.ui.reader

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
