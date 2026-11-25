package com.savoj.volumpdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * «Release без admob.properties»: у репозиторії не має бути бойових AdMob ID, а кореневий build.gradle.kts
 * має лишатися політикою «немає файла => release з тестовими ID Google; -Pproduction=true => збірка падає».
 * Тест читає вихідний код (сканер), а не запускає Gradle; фактичний release-маніфест перевіряє задача
 * :app:verifyAdmobReleaseManifest (див. запис у docs/project_log.md).
 */
class AdmobReleaseFallbackPolicyTest {

    private val testPublisher = "ca-app-pub-3940256099942544"

    private val repoRoot: File = listOf(File(".."), File(".")).first { File(it, "settings.gradle.kts").isFile }

    private fun text(path: String): String {
        val f = File(repoRoot, path)
        assertTrue("нема ${f.path}", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun `without admob properties release falls back to Google test ids`() {
        val gradle = text("build.gradle.kts")
        val noFile = gradle.substringAfter("if (!file.isFile) {").substringBefore("} else {")
        assertTrue("fallback має брати тестові ID", noFile.contains("release = test"))
        assertTrue("fallback має позначати releaseIsTest", noFile.contains("releaseIsTest = true"))
        assertFalse("fallback не має читати бойових значень", noFile.contains("getProperty"))
    }

    @Test
    fun `production flag fails the build when admob properties is missing or has test ids`() {
        val gradle = text("build.gradle.kts")
        val noFile = gradle.substringAfter("if (!file.isFile) {").substringBefore("release = test")
        assertTrue(noFile.contains("if (production)") && noFile.contains("fail("))
        assertTrue(gradle.contains("if (releaseIsTest && production) fail("))
    }

    @Test
    fun `admob properties is ignored by git and only the example is tracked`() {
        val ignored = text(".gitignore").lines().map { it.trim() }
        assertTrue("admob.properties має бути в .gitignore", ignored.contains("admob.properties"))
        assertFalse("admob.properties не має лежати в репозиторії", File(repoRoot, "admob.properties").isFile && !ignored.contains("admob.properties"))
        val example = text("admob.properties.example")
        assertTrue(example.lines().filter { it.startsWith("admob.") }.all { it.substringAfter('=').isBlank() })
    }

    @Test
    fun `source code has no non-test ad mob ids`() {
        val idRe = Regex("""ca-app-pub-(\d{16})[~/]\d{10}""")
        val dirs = listOf("app/src", "core-ads/src", "core-pdf/src")
        val offenders = ArrayList<String>()
        dirs.forEach { d ->
            File(repoRoot, d).walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java", "xml", "kts", "properties", "json") }
                .forEach { f ->
                    idRe.findAll(f.readText(Charsets.UTF_8)).forEach { m ->
                        if (m.groupValues[1] != testPublisher.removePrefix("ca-app-pub-")) offenders += "${f.path}: ${m.value}"
                    }
                }
        }
        assertEquals("бойові ID у коді: $offenders", emptyList<String>(), offenders)
        val gradle = text("build.gradle.kts") + text("app/build.gradle.kts") + text("core-ads/build.gradle.kts")
        idRe.findAll(gradle).forEach { assertEquals(testPublisher.removePrefix("ca-app-pub-"), it.groupValues[1]) }
    }
}
