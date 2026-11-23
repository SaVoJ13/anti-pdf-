package com.savoj.volumpdf.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OssLicenseParserTest {
    @Test
    fun `metadata is parsed and sorted without accepting malformed rows`() {
        val entries = OssLicenseParser.parseMetadata("5:4 Zebra\n0:5 alpha\nbad\n3:0 Empty")
        assertEquals(listOf("alpha", "Zebra"), entries.map { it.name })
        assertEquals(OssLicenseEntry("alpha", 0, 5), entries.first())
    }

    @Test
    fun `license slice is bounded and decoded as utf8`() {
        val blob = "helloLICENCEtail".toByteArray(Charsets.UTF_8)
        assertEquals("LICENCE", OssLicenseParser.licenseText(blob, OssLicenseEntry("x", 5, 7)))
        assertNull(OssLicenseParser.licenseText(blob, OssLicenseEntry("x", -1, 2)))
        assertNull(OssLicenseParser.licenseText(blob, OssLicenseEntry("x", 5, 99)))
    }
}
