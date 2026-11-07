package com.savoj.volumpdf.core.pdf

import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdmodel.PDPage

/** Точка у просторі користувача PDF (pt, початок унизу ліворуч, y вгору). */
data class UserPoint(val x: Float, val y: Float)

/** Прямокутник у просторі користувача PDF; завжди нормалізований (llx <= urx, lly <= ury). */
data class UserRect(val llx: Float, val lly: Float, val urx: Float, val ury: Float) {
    val width: Float get() = urx - llx
    val height: Float get() = ury - lly
}

/**
 * Геометрія сторінки PDF: перетворює точки й прямокутники з простору `PageViewPoint`
 * (pt; початок — лівий верхній кут видимої області сторінки, тобто CropBox після /Rotate;
 * x вправо, y вниз) у простір користувача PDF (CONTRACTS v0.4, §1.5).
 *
 * Не залежить від android.*, тому тестується на JVM.
 *
 * @param llx,lly,urx,ury CropBox (до повороту) у просторі користувача
 * @param rawRotation значення /Rotate як у файлі (можуть бути -90, 450 тощо); нормалізується до 0/90/180/270
 */
class PageGeometry(
    val llx: Float,
    val lly: Float,
    val urx: Float,
    val ury: Float,
    rawRotation: Int
) {
    /** Нормалізований поворот: 0, 90, 180 або 270. */
    val rotation: Int = normalizeRotation(rawRotation)

    /** Розміри CropBox до повороту. */
    val boxWidth: Float = urx - llx
    val boxHeight: Float = ury - lly

    /** Розміри видимої області сторінки після повороту (це розмір простору PageViewPoint). */
    val visibleWidth: Float = if (rotation == 90 || rotation == 270) boxHeight else boxWidth
    val visibleHeight: Float = if (rotation == 90 || rotation == 270) boxWidth else boxHeight

    fun toUser(xv: Float, yv: Float): UserPoint = when (rotation) {
        0 -> UserPoint(llx + xv, ury - yv)
        90 -> UserPoint(llx + yv, lly + xv)
        180 -> UserPoint(llx + boxWidth - xv, lly + yv)
        else -> UserPoint(llx + boxWidth - yv, lly + boxHeight - xv) // 270
    }

    /** Прямокутник із простору PageViewPoint (left, top, right, bottom) -> нормалізований прямокутник у просторі користувача. */
    fun toUserRect(left: Float, top: Float, right: Float, bottom: Float): UserRect {
        val a = toUser(left, top)
        val b = toUser(right, bottom)
        return UserRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
    }

    /**
     * Матриця [a b c d e f] для потоку вмісту: локальна система з початком у view-точці (xv, yv),
     * вісь x — уздовж «праворуч» на видимій сторінці, вісь y — уздовж «угору» на видимій сторінці.
     * Малювання в такій системі стоїть рівно відносно видимої сторінки за будь-якого /Rotate.
     */
    fun contentMatrix(xv: Float, yv: Float): FloatArray {
        val o = toUser(xv, yv)
        val right = toUser(xv + 1f, yv)
        val up = toUser(xv, yv - 1f)
        return floatArrayOf(right.x - o.x, right.y - o.y, up.x - o.x, up.y - o.y, o.x, o.y)
    }

    companion object {
        /** Геометрія сторінки: CropBox (за відсутності — MediaBox; обидва успадковуються) і успадкований /Rotate. */
        fun of(page: PDPage): PageGeometry {
            // PDPage.getCropBox(): успадковує, при відсутності повертає MediaBox, обрізає по MediaBox.
            val box = page.cropBox
            return PageGeometry(
                box.lowerLeftX,
                box.lowerLeftY,
                box.upperRightX,
                box.upperRightY,
                readInheritedRotation(page)
            )
        }

        fun normalizeRotation(raw: Int): Int {
            if (raw % 90 != 0) return 0 // як у PDFBox: не кратне 90 ігнорується
            return ((raw % 360) + 360) % 360
        }

        private fun readInheritedRotation(page: PDPage): Int {
            var node: COSDictionary? = page.cosObject
            var depth = 0
            while (node != null && depth++ < 64) {
                val value: COSBase? = node.getDictionaryObject(COSName.ROTATE)
                if (value is COSNumber) return value.intValue()
                node = node.getDictionaryObject(COSName.PARENT) as? COSDictionary
            }
            return 0
        }
    }
}
