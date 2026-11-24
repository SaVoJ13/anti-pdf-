package com.savoj.volumpdf.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolGroupingLogicTest {

    private val tools = ToolGroupingLogic.buildItems().filterIsInstance<ToolListItem.Tool>().map { it.type }

    @Test
    fun `every tool type appears exactly once`() {
        assertEquals(ToolType.values().size, tools.size)
        assertEquals(ToolType.values().toSet(), tools.toSet())
        assertEquals(11, tools.size)
    }

    @Test
    fun `group order is stable`() {
        assertEquals(
            listOf(ToolGroup.CREATE, ToolGroup.PAGES, ToolGroup.ENHANCE),
            ToolGroup.values().toList()
        )
    }

    @Test
    fun `group contents and order are fixed`() {
        assertEquals(
            listOf(ToolType.CREATE_BLANK, ToolType.SCAN_DOCUMENT, ToolType.IMAGES_TO_PDF),
            ToolGroup.CREATE.tools
        )
        assertEquals(
            listOf(
                ToolType.ORGANIZE_PAGES, ToolType.MERGE, ToolType.SPLIT,
                ToolType.ROTATE, ToolType.DELETE_PAGES
            ),
            ToolGroup.PAGES.tools
        )
        assertEquals(
            listOf(ToolType.WATERMARK, ToolType.PAGE_NUMBERS, ToolType.COMPRESS),
            ToolGroup.ENHANCE.tools
        )
    }

    @Test
    fun `each header is followed by its own tools`() {
        val items = ToolGroupingLogic.buildItems()
        assertEquals(3 + 11, items.size)
        var i = 0
        ToolGroup.values().forEach { g ->
            assertEquals(ToolListItem.Header(g), items[i++])
            g.tools.forEach { assertEquals(ToolListItem.Tool(it), items[i++]) }
        }
        assertTrue(i == items.size)
    }

    @Test
    fun `tool type enum order is unchanged`() {
        assertEquals(
            listOf(
                "CREATE_BLANK", "SCAN_DOCUMENT", "ORGANIZE_PAGES", "MERGE", "SPLIT", "ROTATE",
                "DELETE_PAGES", "IMAGES_TO_PDF", "WATERMARK", "PAGE_NUMBERS", "COMPRESS"
            ),
            ToolType.values().map { it.name }
        )
    }
}
