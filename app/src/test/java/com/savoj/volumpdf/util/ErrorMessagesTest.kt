package com.savoj.volumpdf.util

import com.savoj.volumpdf.R
import com.savoj.volumpdf.core.pdf.model.PdfPermissionException
import com.savoj.volumpdf.util.ErrorMessages.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/** P0-1: сирі виняття перетворюються на ресурсні рядки; жодних технічних текстів у результаті. */
class ErrorMessagesTest {

    private class InvalidPasswordException(msg: String) : IOException(msg)

    @Test
    fun `security exception is read only`() {
        assertEquals(Kind.READ_ONLY, ErrorMessages.classify(SecurityException("has no access to content://media/external/file/1")))
        assertEquals(R.string.err_read_only, ErrorMessages.forFailure(SecurityException("x")).res)
    }

    @Test
    fun `permission denied inside file not found is read only not missing`() {
        val e = FileNotFoundException("/storage/emulated/0/a.pdf: open failed: EACCES (Permission denied)")
        assertEquals(Kind.READ_ONLY, ErrorMessages.classify(e))
    }

    @Test
    fun `plain file not found maps to not found`() {
        val m = ErrorMessages.forFailure(FileNotFoundException("/x/y.pdf: open failed: ENOENT (No such file or directory)"))
        assertEquals(R.string.err_not_found, m.res)
    }

    @Test
    fun `cause chain is inspected`() {
        val e = RuntimeException("wrapper", IOException("write failed: ENOSPC (No space left on device)"))
        assertEquals(Kind.NO_SPACE, ErrorMessages.classify(e))
    }

    @Test
    fun `generic io and out of memory`() {
        assertEquals(R.string.err_io, ErrorMessages.forFailure(IOException("boom")).res)
        assertEquals(R.string.err_too_big, ErrorMessages.forFailure(OutOfMemoryError()).res)
    }

    @Test
    fun `invalid password is recognised by class name`() {
        assertEquals(R.string.error_invalid_password, ErrorMessages.forFailure(InvalidPasswordException("x")).res)
    }

    @Test
    fun `pdf permission exception`() {
        assertEquals(R.string.err_no_permission, ErrorMessages.forFailure(PdfPermissionException("MODIFY")).res)
    }

    @Test
    fun `unknown exception uses the caller fallback`() {
        val m = ErrorMessages.forFailure(IllegalStateException("internal"), R.string.err_create_pdf)
        assertEquals(R.string.err_create_pdf, m.res)
        assertTrue(m.args.isEmpty())
    }

    @Test
    fun `self referencing cause does not loop`() {
        val e = object : RuntimeException("loop") {
            override val cause: Throwable get() = this
        }
        assertEquals(Kind.OTHER, ErrorMessages.classify(e))
    }

    @Test
    fun `page range reasons map to dedicated messages with numbers`() {
        val ex = try {
            PageRangeParser.parsePageIndices("7", 3); null
        } catch (e: PageRangeException) { e }
        assertEquals(PageRangeException.Reason.OUT_OF_RANGE, ex?.reason)
        val m = ErrorMessages.forFailure(ex!!)
        assertEquals(R.string.err_pages_range, m.res)
        assertEquals(listOf<Any>(7, 3), m.args)
    }

    @Test
    fun `page range parser failures are page range exceptions and illegal argument exceptions`() {
        fun reason(input: String, total: Int = 5): PageRangeException.Reason {
            try {
                PageRangeParser.parsePageIndices(input, total)
            } catch (e: IllegalArgumentException) {
                return (e as PageRangeException).reason
            }
            error("очікувалась помилка для '$input'")
        }
        assertEquals(PageRangeException.Reason.EMPTY, reason("  "))
        assertEquals(PageRangeException.Reason.BAD_FORMAT, reason("a"))
        assertEquals(PageRangeException.Reason.BAD_FORMAT, reason("1-2-3"))
        assertEquals(PageRangeException.Reason.NOT_POSITIVE, reason("0"))
        assertEquals(PageRangeException.Reason.NOT_POSITIVE, reason("0-2"))
        assertEquals(PageRangeException.Reason.OUT_OF_RANGE, reason("1-9"))
        assertEquals(PageRangeException.Reason.NONE_SELECTED, reason(",,"))
    }

    @Test
    fun `technical page range text carries no user facing language`() {
        val e = try { PageRangeParser.parsePageIndices("9", 2); null } catch (x: PageRangeException) { x }
        assertFalse(e!!.message!!.any { it in 'А'..'я' })
    }

    @Test
    fun `open failure messages`() {
        assertEquals(R.string.open_error_denied, ErrorMessages.forOpenFailure(SecurityException("x")).res)
        assertEquals(R.string.err_not_found, ErrorMessages.forOpenFailure(FileNotFoundException("x")).res)
        assertEquals(R.string.open_error_corrupt, ErrorMessages.forOpenFailure(IOException("Header doesn't contain versioninfo")).res)
        assertEquals(R.string.open_error_corrupt, ErrorMessages.forOpenFailure(IllegalStateException()).res)
    }
}
