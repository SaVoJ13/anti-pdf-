package com.savoj.volumpdf.ui

import com.savoj.volumpdf.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Пункти меню конфіденційності й URL політики (одна константа в BuildConfig). */
class PrivacyMenuTest {

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    @Test
    fun `main menu has privacy policy and privacy settings items`() {
        val xml = File(resDir, "menu/menu_main.xml").readText()
        assertTrue(xml.contains("@+id/action_privacy_policy"))
        assertTrue(xml.contains("@+id/action_privacy_settings"))
    }

    @Test
    fun `privacy policy url is an https link`() {
        assertTrue(BuildConfig.PRIVACY_POLICY_URL.startsWith("https://"))
    }

    @Test
    fun `manifest disables backup and adds no new permissions`() {
        val xml = File(resDir, "../AndroidManifest.xml").readText()
        assertTrue(xml.contains("android:allowBackup=\"false\""))
        val perms = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()
        assertTrue("лише INTERNET: $perms", perms == listOf("android.permission.INTERNET"))
    }
}
