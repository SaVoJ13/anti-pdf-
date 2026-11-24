package com.savoj.volumpdf.ui.common

import java.nio.charset.StandardCharsets

data class OssLicenseEntry(val name: String, val offset: Int, val length: Int)

/** Читає формат ресурсів, які генерує com.google.android.gms.oss-licenses-plugin. */
object OssLicenseParser {
    private val metadataLine = Regex("^(\\d+):(\\d+) (.+)$")

    /** Fail closed: один пошкоджений непорожній рядок робить весь індекс непридатним. */
    fun parseMetadata(text: String): List<OssLicenseEntry> {
        val entries = ArrayList<OssLicenseEntry>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val match = metadataLine.matchEntire(line) ?: return emptyList()
            val offset = match.groupValues[1].toIntOrNull() ?: return emptyList()
            val length = match.groupValues[2].toIntOrNull() ?: return emptyList()
            val name = match.groupValues[3].trim()
            if (length <= 0 || name.isEmpty()) return emptyList()
            entries += OssLicenseEntry(name, offset, length)
        }
        return entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun licenseText(blob: ByteArray, entry: OssLicenseEntry): String? {
        if (entry.offset < 0 || entry.length <= 0) return null
        val end = entry.offset.toLong() + entry.length.toLong()
        if (end > blob.size.toLong()) return null
        return String(blob, entry.offset, entry.length, StandardCharsets.UTF_8).trim().takeIf(String::isNotEmpty)
    }
}
