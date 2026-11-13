package com.savoj.volumpdf.ui.organizer

import com.savoj.volumpdf.core.pdf.model.PageTransform

/**
 * Чиста бізнес-логіка операцій над візуальною сіткою сторінок (ТОП-3).
 * Легко тестується без Android runtime.
 */
object PageOrganizerLogic {

    fun createInitialItems(pageCount: Int): List<PageOrganizerItem> {
        return (0 until pageCount).map { i ->
            PageOrganizerItem(id = i.toLong(), originalPageIndex = i, rotationDegrees = 0)
        }
    }

    fun rotateItem(item: PageOrganizerItem, byDegrees: Int = 90): PageOrganizerItem {
        val newRot = (item.rotationDegrees + byDegrees) % 360
        return item.copy(rotationDegrees = if (newRot < 0) newRot + 360 else newRot)
    }

    fun rotateAll(items: List<PageOrganizerItem>, byDegrees: Int = 90): List<PageOrganizerItem> {
        return items.map { rotateItem(it, byDegrees) }
    }

    fun moveItem(items: List<PageOrganizerItem>, fromPosition: Int, toPosition: Int): List<PageOrganizerItem> {
        if (fromPosition == toPosition || fromPosition !in items.indices || toPosition !in items.indices) {
            return items
        }
        val mutable = items.toMutableList()
        val item = mutable.removeAt(fromPosition)
        mutable.add(toPosition, item)
        return mutable
    }

    fun deleteItem(items: List<PageOrganizerItem>, position: Int): List<PageOrganizerItem> {
        if (position !in items.indices) return items
        val mutable = items.toMutableList()
        mutable.removeAt(position)
        return mutable
    }

    fun restoreItem(items: List<PageOrganizerItem>, position: Int, item: PageOrganizerItem): List<PageOrganizerItem> {
        val safePos = position.coerceIn(0, items.size)
        val mutable = items.toMutableList()
        mutable.add(safePos, item)
        return mutable
    }

    fun toPageTransforms(items: List<PageOrganizerItem>): List<PageTransform> {
        return items.map { it.toPageTransform() }
    }
}
