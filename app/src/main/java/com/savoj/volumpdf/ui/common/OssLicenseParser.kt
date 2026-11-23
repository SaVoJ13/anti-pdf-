package com.savoj.volumpdf.ui.common

import java.nio.charset.StandardCharsets

data class OssLicenseEntry(val name: String, val offset: Int, val length: Int)

/** Читає формат ресурсів, які генерує com.google.android.gms.oss-licenses-plugin. */
object OssLicenseParser {
    private val metadataLine = Regex("^(\\d+):(\\d+) (.+)$")

    fun parseMetadata(text: String): List<OssLicenseEntry> =
        text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                val match = metadataLine.matchEntire(line) ?: return@mapNotNull null
                val offset = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val length = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val name = match.groupValues[3].trim()
                if (length <= 0 || name.isEmpty()) null else OssLicenseEntry(name, offset, length)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()

    fun licenseText(blob: ByteArray, entry: OssLicenseEntry): String? {
        if (entry.offset < 0 || entry.length <= 0) return null
        val end = entry.offset.toLong() + entry.length.toLong()
        if (end > blob.size.toLong()) return null
        return String(blob, entry.offset, entry.length, StandardCharsets.UTF_8).trim()
    }
}
