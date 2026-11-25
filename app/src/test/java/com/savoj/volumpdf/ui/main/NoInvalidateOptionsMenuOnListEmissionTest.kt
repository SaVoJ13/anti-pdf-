package com.savoj.volumpdf.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Урок 22 (D2-1): invalidateOptionsMenu() на кожну емісію списку перебудовує меню і згортає SearchView під час вводу.
 * Тест-сканер вихідного коду: у RecentsFragment виклику немає взагалі, а в MainActivity він дозволений лише в
 * явних одноразових місцях (скидання фільтрів, перемикання вкладки), але не в колекторах потоків/списків.
 */
class NoInvalidateOptionsMenuOnListEmissionTest {

    private val dir: File = listOf(File("src/main/java/com/savoj/volumpdf/ui/main"), File("app/src/main/java/com/savoj/volumpdf/ui/main"))
        .first { it.isDirectory }

    /** Прибирає коментарі, щоб згадка в KDoc не давала хибного спрацювання. */
    private fun code(name: String): String {
        val raw = File(dir, name).readText(Charsets.UTF_8)
        return raw.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")
    }

    private fun enclosingFunctions(src: String, call: String): List<String> =
        Regex(Regex.escape(call)).findAll(src).map { m ->
            Regex("""fun\s+(?:[\w.]+\.)?(\w+)\s*\(""").findAll(src.substring(0, m.range.first)).lastOrNull()?.groupValues?.get(1) ?: "<top-level>"
        }.toList()

    @Test
    fun `RecentsFragment never invalidates the options menu`() {
        assertFalse(code("RecentsFragment.kt").contains("invalidateOptionsMenu"))
        assertFalse(code("RecentsFragment.kt").contains("invalidateMenu"))
        assertFalse(code("RecentsAdapter.kt").contains("invalidateOptionsMenu"))
    }

    @Test
    fun `MainActivity invalidates the menu only from allowed one-shot places`() {
        val src = code("MainActivity.kt")
        val allowed = setOf("resetLibraryFilters", "onPageSelected")
        val where = enclosingFunctions(src, "invalidateOptionsMenu()")
        assertTrue("очікуємо хоча б один виклик (контроль сканера)", where.isNotEmpty())
        where.forEach { assertTrue("invalidateOptionsMenu() у недозволеній функції $it", it in allowed) }
        assertTrue(src.contains("invalidateOptionsMenu"))
    }

    @Test
    fun `no invalidate inside collect or observe blocks`() {
        listOf("MainActivity.kt", "RecentsFragment.kt").forEach { name ->
            val src = code(name)
            Regex("""\.(collect|collectLatest|observe)\b[^{]*\{""").findAll(src).forEach { m ->
                var depth = 1
                var i = m.range.last + 1
                while (i < src.length && depth > 0) {
                    when (src[i]) { '{' -> depth++; '}' -> depth-- }
                    i++
                }
                val block = src.substring(m.range.last + 1, i)
                assertFalse("$name: invalidateOptionsMenu у блоці ${m.value.trim()}", block.contains("invalidateOptionsMenu"))
            }
        }
    }

    @Test
    fun `scanner detects a regression sample`() {
        val bad = "fun render() { viewModel.items.collect { list -> adapter.submit(list); invalidateOptionsMenu() } }"
        assertEquals(listOf("render"), enclosingFunctions(bad, "invalidateOptionsMenu()"))
    }
}
