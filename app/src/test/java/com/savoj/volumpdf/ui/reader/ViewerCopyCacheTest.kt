package com.savoj.volumpdf.ui.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ViewerCopyCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `copy creates unique files in view dir with same content`() {
        val cache = tmp.newFolder("cache")
        val data = ByteArray(5000) { (it % 251).toByte() }
        val a = ViewerCopyCache.copy(cache) { ByteArrayInputStream(data) }
        val b = ViewerCopyCache.copy(cache) { ByteArrayInputStream(data) }
        assertNotEquals(a.file.name, b.file.name)
        assertEquals(ViewerCopyCache.viewDir(cache), a.file.parentFile)
        assertArrayEquals(data, a.file.readBytes())
        assertEquals(5000L, a.bytes)
    }

    @Test
    fun `purge keeps only the current copy`() {
        val cache = tmp.newFolder("cache")
        val a = ViewerCopyCache.copy(cache) { ByteArrayInputStream(ByteArray(10)) }
        val b = ViewerCopyCache.copy(cache) { ByteArrayInputStream(ByteArray(10)) }
        val c = ViewerCopyCache.copy(cache) { ByteArrayInputStream(ByteArray(10)) }
        assertEquals(2, ViewerCopyCache.purge(cache, keep = c.file))
        assertFalse(a.file.exists())
        assertFalse(b.file.exists())
        assertTrue(c.file.exists())
        assertEquals(1, ViewerCopyCache.purge(cache))
        assertFalse(c.file.exists())
    }

    @Test
    fun `purge on missing dir is harmless`() {
        assertEquals(0, ViewerCopyCache.purge(tmp.newFolder("empty")))
    }

    @Test
    fun `failed copy leaves no partial file`() {
        val cache = tmp.newFolder("cache")
        val failing = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 100) 1 else throw IOException("boom")
        }
        try {
            ViewerCopyCache.copy(cache) { failing }
            throw AssertionError("expected IOException")
        } catch (_: IOException) {
        }
        assertEquals(0, ViewerCopyCache.viewDir(cache).listFiles()?.size ?: 0)
    }
}
