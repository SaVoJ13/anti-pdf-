package com.savoj.volumpdf.core.pdf

/**
 * Чистий парсер EXIF Orientation (тег 0x0112) з байтів JPEG (APP1/Exif, TIFF IFD0).
 * Без android.media і без винятків: для відсутніх, не-JPEG, пошкоджених чи обрізаних даних повертає 1.
 */
internal object ExifOrientation {
    const val NORMAL = 1

    /** Значення Orientation 1..8; будь-яка проблема дає 1. */
    fun read(bytes: ByteArray): Int = try {
        parse(bytes)
    } catch (_: Throwable) {
        NORMAL
    }

    /**
     * Кут за годинниковою стрілкою для автоповороту. Дзеркальні значення (2,4,5,7) тлумачимо
     * як лише поворот, дзеркало ігноруємо (як android.media.ExifInterface для кута): 2->0, 4->180, 5->270, 7->90.
     */
    fun rotationDegrees(orientation: Int): Int = when (orientation) {
        3, 4 -> 180
        6, 7 -> 90
        8, 5 -> 270
        else -> 0
    }

    private fun u8(b: ByteArray, i: Int): Int {
        if (i < 0 || i >= b.size) throw IndexOutOfBoundsException()
        return b[i].toInt() and 0xFF
    }

    private fun parse(b: ByteArray): Int {
        if (b.size < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) return NORMAL
        var i = 2
        while (i + 4 <= b.size) {
            if (u8(b, i) != 0xFF) { i++; continue }
            val marker = u8(b, i + 1)
            if (marker == 0xFF) { i++; continue }
            if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01) { i += 2; continue }
            if (marker == 0xDA || marker == 0xD9) return NORMAL
            val len = (u8(b, i + 2) shl 8) or u8(b, i + 3)
            if (len < 2) return NORMAL
            if (marker == 0xE1 && len >= 8 &&
                u8(b, i + 4) == 'E'.code && u8(b, i + 5) == 'x'.code &&
                u8(b, i + 6) == 'i'.code && u8(b, i + 7) == 'f'.code &&
                u8(b, i + 8) == 0 && u8(b, i + 9) == 0
            ) {
                return parseTiff(b, i + 10, minOf(b.size, i + 2 + len))
            }
            i += 2 + len
        }
        return NORMAL
    }

    private fun parseTiff(b: ByteArray, tiff: Int, end: Int): Int {
        if (tiff + 8 > end) return NORMAL
        val little = when {
            u8(b, tiff) == 0x49 && u8(b, tiff + 1) == 0x49 -> true
            u8(b, tiff) == 0x4D && u8(b, tiff + 1) == 0x4D -> false
            else -> return NORMAL
        }
        fun u16(p: Int): Int {
            if (p + 2 > end) throw IndexOutOfBoundsException()
            return if (little) u8(b, p) or (u8(b, p + 1) shl 8) else (u8(b, p) shl 8) or u8(b, p + 1)
        }
        fun u32(p: Int): Long {
            if (p + 4 > end) throw IndexOutOfBoundsException()
            return if (little) {
                (u8(b, p).toLong()) or (u8(b, p + 1).toLong() shl 8) or
                    (u8(b, p + 2).toLong() shl 16) or (u8(b, p + 3).toLong() shl 24)
            } else {
                (u8(b, p).toLong() shl 24) or (u8(b, p + 1).toLong() shl 16) or
                    (u8(b, p + 2).toLong() shl 8) or u8(b, p + 3).toLong()
            }
        }
        if (u16(tiff + 2) != 42) return NORMAL
        val ifd = u32(tiff + 4)
        if (ifd < 8 || ifd > Int.MAX_VALUE - tiff) return NORMAL
        val ifdPos = tiff + ifd.toInt()
        val count = u16(ifdPos)
        for (n in 0 until count) {
            val e = ifdPos + 2 + n * 12
            if (u16(e) == 0x0112) {
                val type = u16(e + 2)
                if (type != 3) return NORMAL
                val v = u16(e + 8)
                return if (v in 1..8) v else NORMAL
            }
        }
        return NORMAL
    }
}
