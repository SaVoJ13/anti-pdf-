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

    @Test
    fun `license slice is bounded and decoded as utf8`() {
        val blob = "helloLICENCEtail".toByteArray(Charsets.UTF_8)
        assertEquals("LICENCE", OssLicenseParser.licenseText(blob, OssLicenseEntry("x", 5, 7)))
        assertNull(OssLicenseParser.licenseText(blob, OssLicenseEntry("x", -1, 2)))
        assertNull(OssLicenseParser.licenseText(blob, OssLicenseEntry("x", 5, 99)))
        assertNull(OssLicenseParser.licenseText("   ".toByteArray(), OssLicenseEntry("x", 0, 3)))
    }
}
