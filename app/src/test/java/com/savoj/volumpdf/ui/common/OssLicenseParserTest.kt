package com.savoj.volumpdf.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OssLicenseParserTest {
    @Test
    fun `valid metadata is parsed and sorted`() {
        val entries = OssLicenseParser.parseMetadata("5:4 Zebra\n0:5 alpha")
        assertEquals(listOf("alpha", "Zebra"), entries.map { it.name })
        assertEquals(OssLicenseEntry("alpha", 0, 5), entries.first())
    }

    @Test
    fun `one malformed metadata row fails the complete index closed`() {
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.parseMetadata("0:5 Valid\nbad"))
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.parseMetadata("0:5 Valid\n3:0 Empty"))
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.parseMetadata("999999999999:5 Overflow"))
    }

    private val goodMetadata = "0:5 alpha\n5:4 Beta"
    private val goodBlob = "helloLICE".toByteArray(Charsets.UTF_8)

    @Test
    fun `loadIndex returns sorted entries when resources are readable`() {
        val entries = OssLicenseParser.loadIndex({ goodMetadata }, { goodBlob })
        assertEquals(listOf("alpha", "Beta"), entries.map { it.name })
    }

    @Test
    fun `L8 loadIndex returns empty when metadata resource cannot be read`() {
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.loadIndex({ throw java.io.IOException("нема ресурсу") }, { goodBlob }))
    }

    @Test
    fun `L8 loadIndex returns empty when text blob cannot be read`() {
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.loadIndex({ goodMetadata }, { throw java.io.IOException("нема blob") }))
    }

    @Test
    fun `L8 loadIndex returns empty for corrupt or empty index and for entries outside blob`() {
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.loadIndex({ "0:5 ok\nbad" }, { goodBlob }))
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.loadIndex({ "" }, { goodBlob }))
        assertEquals(emptyList<OssLicenseEntry>(), OssLicenseParser.loadIndex({ "0:5 ok\n5:99 outside" }, { goodBlob }))
    }

    @Test
    fun `L8 loadText returns null when blob cannot be read or slice is invalid`() {
        val entry = OssLicenseEntry("x", 0, 5)
        assertEquals("hello", OssLicenseParser.loadText({ goodBlob }, entry))
        assertNull(OssLicenseParser.loadText({ throw java.io.IOException("нема blob") }, entry))
        assertNull(OssLicenseParser.loadText({ goodBlob }, OssLicenseEntry("x", 5, 99)))
    }

    @Test
    fun `license slice is bounded and decoded as utf8`() {
        val blob = "helloLICENCEtail".toByteArray(Charsets.UTF_8)
        assertEquals("LICENCE", OssLicenseParser.licenseText(blob, OssLicenseEntry("x", 5, 7)))
        assertNull(OssLicenseParser.licenseText(blob, OssLicenseEntry("x", -1, 2)))
        assertNull(OssLicenseParser.licenseText(blob, OssLicenseEntry("x", 5, 99)))
        assertNull(OssLicenseParser.licenseText("   ".toByteArray(), OssLicenseEntry("x", 0, 3)))
    }
}
