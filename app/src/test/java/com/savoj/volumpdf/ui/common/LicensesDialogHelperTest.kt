package com.savoj.volumpdf.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensesDialogHelperTest {

    private val assetsDir = File("src/main/assets/${LicensesDialogHelper.ASSETS_DIR}")

    private val text: String by lazy {
        LicensesDialogHelper.buildLicensesText { name -> File(assetsDir, name).inputStream() }
    }

    @Test
    fun assetListMatchesFilesOnDisk() {
        val onDisk = assetsDir.list().orEmpty().sorted()
        assertEquals(onDisk, LicensesDialogHelper.ASSET_FILES.sorted())
    }

    @Test
    fun headerSaysAntiPdfOnly() {
        assertTrue(text.startsWith("Anti PDF is built using open-source software."))
        assertFalse("Стара шапка «Volum PDF / Anti PDF» не потрібна", text.contains("Volum PDF / Anti PDF"))
    }

    @Test
    fun containsFullBouncyCastleLicence() {
        assertTrue(text.contains("Copyright (c) 2000 - 2026 The Legion of the Bouncy Castle Inc."))
        assertTrue(text.contains("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND"))
        assertFalse("Переказ «standard MIT license terms» замінено повним текстом", text.contains("standard MIT license terms"))
    }

    @Test
    fun containsPdfBoxNoticeAndExternalComponents() {
        assertTrue(text.contains("Apache PDFBox\nCopyright 2014 The Apache Software Foundation"))
        assertTrue(text.contains("Copyright (c) 2002-2007, www.pdfbox.org"))
        assertTrue(text.contains("Atos Worldline SAS"))
        assertTrue(text.contains("Includes the Adobe Glyph List"))
        assertTrue(text.contains("Includes the Zapf Dingbats Glyph List"))
        assertTrue(text.contains("Includes the Script Property (Scripts-10.0.0.txt)"))
        assertTrue(text.contains("Adobe Font Metrics (AFM) for PDF Core 14 Fonts"))
        assertTrue(text.contains("CMaps for PDF Fonts"))
    }

    @Test
    fun containsLiberationSansWithReservedFontName() {
        assertTrue(text.contains("Liberation Sans"))
        assertTrue(text.contains("Copyright (c) 2012 Red Hat, Inc."))
        assertTrue(text.contains("Reserved Font Name Liberation"))
    }

    @Test
    fun containsFullOflAndFullApacheOnce() {
        assertEquals(1, Regex("SIL OPEN FONT LICENSE Version 1[.]1").findAll(text).count())
        assertTrue(text.contains("DEFINITIONS\n\"Font Software\" refers to the set of files"))
        assertEquals(1, Regex("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION").findAll(text).count())
        assertTrue(text.contains("END OF TERMS AND CONDITIONS"))
    }

    @Test
    fun oflCopyMatchesFontAssetInCorePdf() {
        val coreOfl = File("../core-pdf/src/main/assets/fonts/OFL.txt").readText().replace("\r\n", "\n").trimEnd()
        assertTrue("Повний OFL у ліцензіях має збігатися з core-pdf/.../OFL.txt", text.contains(coreOfl))
    }

    @Test
    fun libraryLicensesUseOwnedEdgeToEdgeScreens() {
        val source = File("src/main/java/com/savoj/volumpdf/ui/common/LicensesDialogHelper.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val build = File("build.gradle.kts").readText()

        assertTrue(source.contains("LibraryLicensesActivity::class.java"))
        assertFalse(source.contains("OssLicensesMenuActivity"))
        assertTrue(manifest.contains(".ui.common.LibraryLicensesActivity"))
        assertTrue(manifest.contains(".ui.common.LibraryLicenseDetailActivity"))
        assertFalse("Стороння Activity з L7 не повинна лишатися runtime-залежністю", build.contains("play-services-oss-licenses:"))
    }
}
