package com.savoj.volumpdf.ui

import com.savoj.volumpdf.ui.common.AppearancePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/**
 * P1-3: палітра Індіго лишається за замовчуванням (Material You вимкнено) і відповідає WCAG AA
 * (4,5:1 для тексту) у світлому й темному режимах; графітові картки редагування мають свої фіксовані кольори.
 * Динамічні кольори системи (Android 12+) будуються за тональною схемою Material 3 і на JVM не перевіряються.
 */
class AppearanceContrastTest {

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun colors(file: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, file))
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length).associate {
            val n = nodes.item(it)
            n.attributes.getNamedItem("name").nodeValue to n.textContent.trim()
        }
    }

    private fun lum(hex: String): Double {
        val v = hex.removePrefix("#").takeLast(6).toInt(16)
        fun ch(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(v shr 16 and 0xFF) + 0.7152 * ch(v shr 8 and 0xFF) + 0.0722 * ch(v and 0xFF)
    }

    private fun contrast(a: String, b: String): Double {
        val l1 = lum(a)
        val l2 = lum(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun checkPairs(prefix: String) {
        val c = colors("values/colors.xml")
        val pairs = listOf(
            "onPrimary" to "primary",
            "onPrimaryContainer" to "primaryContainer",
            "onSecondary" to "secondary",
            "onSecondaryContainer" to "secondaryContainer",
            "onTertiaryContainer" to "tertiaryContainer",
            "onError" to "error",
            "onErrorContainer" to "errorContainer",
            "onBackground" to "background",
            "onSurface" to "surface",
            "onSurfaceVariant" to "surface",
            "onSurfaceVariant" to "surfaceVariant",
            "primary" to "surface",
            "primary" to "background"
        )
        for ((fg, bg) in pairs) {
            val f = c["md_theme_${prefix}_$fg"] ?: error("немає md_theme_${prefix}_$fg")
            val b = c["md_theme_${prefix}_$bg"] ?: error("немає md_theme_${prefix}_$bg")
            val r = contrast(f, b)
            assertTrue("$prefix $fg на $bg: $f / $b = $r (< 4,5)", r >= 4.5)
        }
    }

    @Test
    fun `light indigo palette meets WCAG AA`() = checkPairs("light")

    @Test
    fun `dark indigo palette meets WCAG AA`() = checkPairs("dark")

    @Test
    fun `graphite edit cards keep their own fixed colors and AA contrast`() {
        val c = colors("values/place_ui.xml")
        for (bg in listOf("place_bar_bg", "place_card_bg")) {
            for (fg in listOf("place_card_on", "place_card_accent")) {
                val r = contrast(c.getValue(fg), c.getValue(bg))
                assertTrue("$fg на $bg = $r", r >= 4.5)
            }
        }
        // Колір картки не залежить від теми й від динамічних кольорів
        assertEquals("#1E1F22", c["place_bar_bg"])
        assertEquals("#2B2D31", c["place_card_bg"])
    }

    @Test
    fun `indigo primary stays default and dynamic colors are off by default`() {
        assertEquals("#3949AB", colors("values/colors.xml")["md_theme_light_primary"])
        assertFalse(AppearancePreferences.DYNAMIC_COLORS_DEFAULT)
    }

    @Test
    fun `dynamic colors toggle is a checkable overflow item and applied only through preference`() {
        val menu = File(resDir, "menu/menu_main.xml").readText()
        assertTrue(menu.contains("@+id/action_dynamic_colors"))
        assertTrue(menu.contains("android:checkable=\"true\""))
        val app = File(resDir, "../java/com/savoj/volumpdf/VolumPdfApp.kt").readText()
        assertTrue(app.contains("setPrecondition") && app.contains("dynamicColorsEnabled"))
    }
}
