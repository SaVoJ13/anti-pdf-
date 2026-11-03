package com.savoj.volumpdf.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun parsePageIndices_singlePage_returnsZeroBasedIndex() {
        val indices = PageRangeParser.parsePageIndices("1")
        assertEquals(listOf(0), indices)
    }

    @Test
    fun parsePageIndices_range_returnsContinuousZeroBasedIndices() {
        val indices = PageRangeParser.parsePageIndices("1-4")
        assertEquals(listOf(0, 1, 2, 3), indices)
    }

    @Test
    fun parsePageIndices_mixedRangesAndIndividual_returnsSortedUniqueIndices() {
        val indices = PageRangeParser.parsePageIndices("1, 3-5, 2, 8")
        assertEquals(listOf(0, 1, 2, 3, 4, 7), indices)
    }

    @Test
    fun parsePageIndices_invertedRange_handlesCorrectly() {
        val indices = PageRangeParser.parsePageIndices("5-3")
        assertEquals(listOf(2, 3, 4), indices)
    }

    @Test
    fun parsePageIndices_withTotalPagesLimit_succeedsWithinLimit() {
        val indices = PageRangeParser.parsePageIndices("1-5", totalPages = 5)
        assertEquals(listOf(0, 1, 2, 3, 4), indices)
    }

    @Test
    fun parsePageIndices_exceedingTotalPages_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRangeParser.parsePageIndices("1-6", totalPages = 5)
        }
    }

    @Test
    fun parsePageIndices_zeroOrNegative_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRangeParser.parsePageIndices("0, 1, 2")
        }
    }

    @Test
    fun parsePageIndices_emptyOrBlank_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRangeParser.parsePageIndices("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PageRangeParser.parsePageIndices("   ")
        }
    }

    @Test
    fun parsePageIndices_invalidFormat_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            PageRangeParser.parsePageIndices("abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PageRangeParser.parsePageIndices("1-2-3")
        }
    }
}
