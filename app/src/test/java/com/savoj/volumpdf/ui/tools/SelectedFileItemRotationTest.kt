package com.savoj.volumpdf.ui.tools

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

/** O1-3: логіка стану повороту елемента списку вибраних зображень. */
class SelectedFileItemRotationTest {
    private val uri: Uri = Mockito.mock(Uri::class.java)

    @Test
    fun defaultRotationIsZero() {
        assertEquals(0, SelectedFileItem(uri, "a.jpg", true).rotationDeg)
    }

    @Test
    fun rotatesClockwiseInSteps() {
        var item = SelectedFileItem(uri, "a.jpg", true)
        val seen = (1..4).map { item = item.rotatedClockwise(); item.rotationDeg }
        assertEquals(listOf(90, 180, 270, 0), seen)
    }

    @Test
    fun rotationKeepsOtherFields() {
        val item = SelectedFileItem(uri, "a.jpg", true).rotatedClockwise()
        assertEquals("a.jpg", item.displayName)
        assertEquals(true, item.isImage)
        assertEquals(uri, item.uri)
    }
}
