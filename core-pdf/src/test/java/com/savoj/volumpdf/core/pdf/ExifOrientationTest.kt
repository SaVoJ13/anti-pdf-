package com.savoj.volumpdf.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/** Парсер EXIF Orientation (чистий Kotlin). */
class ExifOrientationTest {

    private fun jpegWithExif(orientation: Int, little: Boolean, type: Int = 3): ByteArray {
        fun u16(v: Int) = if (little) byteArrayOf(v.toByte(), (v shr 8).toByte()) else byteArrayOf((v shr 8).toByte(), v.toByte())
        fun u32(v: Int) = if (little) byteArrayOf(v.toByte(), (v shr 8).toByte(), 0, 0) else byteArrayOf(0, 0, (v shr 8).toByte(), v.toByte())
        val tiff = (if (little) byteArrayOf(0x49, 0x49) else byteArrayOf(0x4D, 0x4D)) +
            u16(42) + u32(8) + u16(1) +
            u16(0x0112) + u16(type) + u32(1) + u16(orientation) + byteArrayOf(0, 0) + u32(0)
        val payload = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0) + tiff
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) +
            payload + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    @Test
    fun allEightValuesBothEndian() {
        for (little in listOf(true, false)) {
            for (o in 1..8) {
                assertEquals("o=$o little=$little", o, ExifOrientation.read(jpegWithExif(o, little)))
            }
        }
    }

    @Test
    fun rotationMapping() {
        val expected = mapOf(1 to 0, 2 to 0, 3 to 180, 4 to 180, 5 to 270, 6 to 90, 7 to 90, 8 to 270, 0 to 0, 9 to 0)
        for ((o, d) in expected) assertEquals("o=$o", d, ExifOrientation.rotationDegrees(o))
    }

    @Test
    fun missingExifReturnsNormal() {
        val noExif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0, 2)
        assertEquals(1, ExifOrientation.read(noExif))
        assertEquals(1, ExifOrientation.read(ByteArray(0)))
        assertEquals(1, ExifOrientation.read(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test
    fun truncatedDataNeverThrows() {
        val full = jpegWithExif(6, true)
        // значення Orientation лежить за 8 байтів до кінця сегмента APP1 (перед ним ще 2 байти маркера кінця)
        val valueEnd = full.size - 2 - 6
        for (n in 0..full.size) {
            val v = ExifOrientation.read(full.copyOf(n))
            if (n < valueEnd) assertEquals("n=$n", 1, v) else assertEquals("n=$n", 6, v)
        }
    }

    @Test
    fun invalidValueAndWrongTypeReturnNormal() {
        assertEquals(1, ExifOrientation.read(jpegWithExif(9, true)))
        assertEquals(1, ExifOrientation.read(jpegWithExif(0, false)))
        assertEquals(1, ExifOrientation.read(jpegWithExif(6, true, type = 4)))
    }
}
