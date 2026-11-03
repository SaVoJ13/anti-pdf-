package com.savoj.volumpdf.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafUtilsTest {

    @Test
    fun formatFileSize_zeroOrNegative_returnsZeroB() {
        assertEquals("0 B", SafUtils.formatFileSize(0))
        assertEquals("0 B", SafUtils.formatFileSize(-100))
    }

    @Test
    fun formatFileSize_bytes_returnsExactBytes() {
        assertEquals("500 B", SafUtils.formatFileSize(500))
    }

    @Test
    fun formatFileSize_kilobytes_returnsFormattedKb() {
        val formatted = SafUtils.formatFileSize(1536) // 1.5 KB
        assertTrue(formatted.contains("1") && formatted.contains("KB"))
    }

    @Test
    fun formatFileSize_megabytes_returnsFormattedMb() {
        val formatted = SafUtils.formatFileSize(2 * 1024 * 1024 + 512 * 1024) // 2.5 MB
        assertTrue(formatted.contains("2") && formatted.contains("MB"))
    }

    @Test
    fun formatTimestamp_validTimestamp_returnsNonEmptyString() {
        val formatted = SafUtils.formatTimestamp(System.currentTimeMillis())
        assertTrue(formatted.isNotEmpty())
    }

    @Test
    fun formatTimestamp_zeroOrNegative_returnsEmptyString() {
        assertEquals("", SafUtils.formatTimestamp(0))
        assertEquals("", SafUtils.formatTimestamp(-1))
    }
}
