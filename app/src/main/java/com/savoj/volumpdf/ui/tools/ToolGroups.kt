package com.savoj.volumpdf.ui.tools

import androidx.annotation.StringRes
import com.savoj.volumpdf.R

/** Група інструментів на вкладці «Інструменти». Порядок груп і елементів стабільний (D3). */
enum class ToolGroup(@get:StringRes val titleRes: Int, val tools: List<ToolType>) {
    CREATE(
        R.string.tools_section_create,
        listOf(ToolType.CREATE_BLANK, ToolType.SCAN_DOCUMENT, ToolType.IMAGES_TO_PDF)
    ),
    PAGES(
        R.string.tools_section_pages,
        listOf(
            ToolType.ORGANIZE_PAGES, ToolType.MERGE, ToolType.SPLIT,
            ToolType.ROTATE, ToolType.DELETE_PAGES
        )
    ),
    ENHANCE(
        R.string.tools_section_enhance,
        listOf(ToolType.WATERMARK, ToolType.PAGE_NUMBERS, ToolType.COMPRESS)
    )
}

/** Рядок плоского списку вкладки: заголовок групи або інструмент. */
sealed class ToolListItem {
    data class Header(val group: ToolGroup) : ToolListItem()
    data class Tool(val type: ToolType) : ToolListItem()
}

object ToolGroupingLogic {
    /** Плоский список: заголовок групи, далі її інструменти, групи в порядку [ToolGroup.values]. */
    fun buildItems(): List<ToolListItem> = ToolGroup.values().flatMap { group ->
        listOf<ToolListItem>(ToolListItem.Header(group)) + group.tools.map { ToolListItem.Tool(it) }
    }
}
