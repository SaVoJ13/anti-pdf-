package com.savoj.volumpdf.ui.organizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Модульні тести для [PageOrganizerLogic] (ТОП-3).
 */
class PageOrganizerLogicTest {

    @Test
    fun testCreateInitialItems() {
        val items = PageOrganizerLogic.createInitialItems(4)
        assertEquals(4, items.size)
        assertEquals(0, items[0].originalPageIndex)
        assertEquals(0, items[0].rotationDegrees)
        assertEquals(3, items[3].originalPageIndex)
        assertEquals(3L, items[3].id)
    }

    @Test
    fun testRotateItemCycles360() {
        var item = PageOrganizerItem(id = 1L, originalPageIndex = 0, rotationDegrees = 0)
        item = PageOrganizerLogic.rotateItem(item, 90)
        assertEquals(90, item.rotationDegrees)

        item = PageOrganizerLogic.rotateItem(item, 90)
        assertEquals(180, item.rotationDegrees)

        item = PageOrganizerLogic.rotateItem(item, 90)
        assertEquals(270, item.rotationDegrees)

        item = PageOrganizerLogic.rotateItem(item, 90)
        assertEquals(0, item.rotationDegrees)
    }

    @Test
    fun testRotateAll() {
        val items = listOf(
            PageOrganizerItem(id = 0, originalPageIndex = 0, rotationDegrees = 0),
            PageOrganizerItem(id = 1, originalPageIndex = 1, rotationDegrees = 90)
        )
        val rotated = PageOrganizerLogic.rotateAll(items, 90)
        assertEquals(90, rotated[0].rotationDegrees)
        assertEquals(180, rotated[1].rotationDegrees)
    }

    @Test
    fun testMoveItem() {
        val items = PageOrganizerLogic.createInitialItems(4) // [0, 1, 2, 3]

        // Переміщуємо сторінку 0 на позицію 2 -> [1, 2, 0, 3]
        val moved = PageOrganizerLogic.moveItem(items, fromPosition = 0, toPosition = 2)
        assertEquals(4, moved.size)
        assertEquals(1, moved[0].originalPageIndex)
        assertEquals(2, moved[1].originalPageIndex)
        assertEquals(0, moved[2].originalPageIndex)
        assertEquals(3, moved[3].originalPageIndex)

        // Перевірка некоректних меж
        val invalidMove = PageOrganizerLogic.moveItem(items, fromPosition = -1, toPosition = 2)
        assertEquals(items, invalidMove)
    }

    @Test
    fun testDeleteAndRestoreItem() {
        val items = PageOrganizerLogic.createInitialItems(3) // [0, 1, 2]

        // Видаляємо елемент на позиції 1
        val itemToDelete = items[1]
        val afterDelete = PageOrganizerLogic.deleteItem(items, position = 1)
        assertEquals(2, afterDelete.size)
        assertEquals(0, afterDelete[0].originalPageIndex)
        assertEquals(2, afterDelete[1].originalPageIndex)

        // Відновлюємо (Undo)
        val restored = PageOrganizerLogic.restoreItem(afterDelete, position = 1, item = itemToDelete)
        assertEquals(3, restored.size)
        assertEquals(0, restored[0].originalPageIndex)
        assertEquals(1, restored[1].originalPageIndex)
        assertEquals(2, restored[2].originalPageIndex)
    }

    @Test
    fun testToPageTransforms() {
        val items = listOf(
            PageOrganizerItem(id = 2, originalPageIndex = 2, rotationDegrees = 90),
            PageOrganizerItem(id = 0, originalPageIndex = 0, rotationDegrees = 180)
        )
        val transforms = PageOrganizerLogic.toPageTransforms(items)
        assertEquals(2, transforms.size)
        assertEquals(2, transforms[0].sourcePageIndex)
        assertEquals(90, transforms[0].rotationDegrees)
        assertEquals(0, transforms[1].sourcePageIndex)
        assertEquals(180, transforms[1].rotationDegrees)
    }
}
