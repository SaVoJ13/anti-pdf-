package com.savoj.volumpdf.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-3: заголовок читача — ім'я файлу, а не числовий ідентифікатор запису. */
class DisplayNameTest {
    @Test
    fun `numeric media id is not a name`() {
        assertNull(SafUtils.nameFromPathLike("1000012345"))
        assertTrue(SafUtils.isOpaqueName("42"))
        assertTrue(SafUtils.isOpaqueName(""))
        assertTrue(SafUtils.isOpaqueName(null))
    }

    @Test
    fun `document id with folders yields the file name`() {
        assertEquals("a.pdf", SafUtils.nameFromPathLike("primary:Documents/a.pdf"))
        assertEquals("b.pdf", SafUtils.nameFromPathLike("b.pdf"))
        assertEquals("Звіт 2026.pdf", SafUtils.nameFromPathLike("raw:/storage/Звіт 2026.pdf"))
    }

    @Test
    fun `colon id with digits only is opaque`() {
        assertNull(SafUtils.nameFromPathLike("image:1234"))
        assertNull(SafUtils.nameFromPathLike(null))
    }

    @Test
    fun `read only decision by scheme`() {
        assertTrue(ReadOnlyCheck.decide("content", writeGranted = false, fileCanWrite = null))
        assertFalse(ReadOnlyCheck.decide("content", writeGranted = true, fileCanWrite = null))
        assertTrue(ReadOnlyCheck.decide("file", writeGranted = false, fileCanWrite = false))
        assertFalse(ReadOnlyCheck.decide("file", writeGranted = false, fileCanWrite = true))
        assertFalse(ReadOnlyCheck.decide("file", writeGranted = false, fileCanWrite = null))
        assertFalse(ReadOnlyCheck.decide(null, writeGranted = false, fileCanWrite = null))
    }

    @Test
    fun `copy name`() {
        assertEquals("a_copy.pdf", ReadOnlyCheck.copyName("a.pdf"))
        assertEquals("document_copy.pdf", ReadOnlyCheck.copyName(null))
        assertEquals("Звіт_copy.pdf", ReadOnlyCheck.copyName(" Звіт.PDF "))
    }

    @Test
    fun `names with digits and letters are real names`() {
        assertFalse(SafUtils.isOpaqueName("2026.pdf"))
        assertFalse(SafUtils.isOpaqueName("scan1"))
    }
}
