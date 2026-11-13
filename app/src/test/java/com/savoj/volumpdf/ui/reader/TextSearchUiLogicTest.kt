package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тести логіки керування станом пошуку тексту в читалці (ТОП-6).
 */
class TextSearchUiLogicTest {

    private class SearchUiStateCoordinator {
        var isTextSearchActive: Boolean = false
        var isPlaceModeActive: Boolean = false
        var hasUnsavedChanges: Boolean = false

        fun onSearchActionToggled() {
            isTextSearchActive = !isTextSearchActive
        }

        fun onEnterPlaceMode() {
            isTextSearchActive = false
            isPlaceModeActive = true
        }

        /**
         * Обробка кнопки «Назад»:
         * 1) Якщо відкритий пошук — закриваємо пошук, повертаємо true (подія оброблена).
         * 2) Якщо активний режим розміщення — закриваємо режим розміщення, повертаємо true.
         * 3) Якщо є незбережені зміни — показуємо діалог (оброблено).
         * 4) Інакше повертаємо false (стандартний вихід).
         */
        fun handleBackPressed(): BackNavigationResult {
            if (isTextSearchActive) {
                isTextSearchActive = false
                return BackNavigationResult.DISMISSED_SEARCH
            }
            if (isPlaceModeActive) {
                isPlaceModeActive = false
                return BackNavigationResult.LEAVE_PLACE_MODE
            }
            if (hasUnsavedChanges) {
                return BackNavigationResult.SHOW_UNSAVED_DIALOG
            }
            return BackNavigationResult.EXIT_SCREEN
        }
    }

    enum class BackNavigationResult {
        DISMISSED_SEARCH,
        LEAVE_PLACE_MODE,
        SHOW_UNSAVED_DIALOG,
        EXIT_SCREEN
    }

    @Test
    fun testSearchToggle() {
        val coordinator = SearchUiStateCoordinator()
        assertFalse(coordinator.isTextSearchActive)

        coordinator.onSearchActionToggled()
        assertTrue(coordinator.isTextSearchActive)

        coordinator.onSearchActionToggled()
        assertFalse(coordinator.isTextSearchActive)
    }

    @Test
    fun testBackPressedDismissesSearchFirst() {
        val coordinator = SearchUiStateCoordinator()
        coordinator.isTextSearchActive = true
        coordinator.hasUnsavedChanges = true

        val result = coordinator.handleBackPressed()
        assertEquals(BackNavigationResult.DISMISSED_SEARCH, result)
        assertFalse(coordinator.isTextSearchActive)
        assertTrue(coordinator.hasUnsavedChanges)

        val nextResult = coordinator.handleBackPressed()
        assertEquals(BackNavigationResult.SHOW_UNSAVED_DIALOG, nextResult)
    }

    @Test
    fun testEnterPlaceModeDismissesSearch() {
        val coordinator = SearchUiStateCoordinator()
        coordinator.isTextSearchActive = true

        coordinator.onEnterPlaceMode()
        assertFalse(coordinator.isTextSearchActive)
        assertTrue(coordinator.isPlaceModeActive)
    }
}
