package com.savoj.volumpdf.ui.common

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.io.File

/**
 * Регресійний захист К-1: сервіс androidx.pdf працює в ізольованому процесі, де
 * Context.getSharedPreferences кидає IllegalStateException. Створення AppearancePreferences
 * (воно відбувається в Application.onCreate) не повинно торкатися сховища, а Application має
 * пропускати Material You в ізольованому процесі, інакше сервіс падає і читач вічно показує спіннер.
 */
class AppearancePreferencesIsolatedProcessTest {

    @Test
    fun constructionDoesNotTouchStorage() {
        val ctx = mock(Context::class.java)
        AppearancePreferences(ctx)
        verifyNoInteractions(ctx)
    }

    @Test
    fun applicationSkipsDynamicColorsInIsolatedProcess() {
        val src = listOf(
            File("src/main/java/com/savoj/volumpdf/VolumPdfApp.kt"),
            File("app/src/main/java/com/savoj/volumpdf/VolumPdfApp.kt")
        ).first { it.isFile }.readText()
        val guard = src.indexOf("Process.isIsolated()")
        val create = src.indexOf("AppearancePreferences(this)")
        assertTrue("VolumPdfApp має перевіряти Process.isIsolated()", guard >= 0)
        assertTrue("AppearancePreferences створюється лише під перевіркою isIsolated", create > guard)
    }
}
