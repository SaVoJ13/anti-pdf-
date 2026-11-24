package com.savoj.volumpdf.ui

import android.content.Context
import com.savoj.volumpdf.ui.main.HomeLogic
import com.savoj.volumpdf.ui.onboarding.OnboardingLogic
import com.savoj.volumpdf.ui.onboarding.OnboardingPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** М9–М11 і онбординг першого запуску: логіка, розмітка меню/екрана, 4 мови, відсутність нових дозволів. */
class HomeOnboardingTest {

    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(resDir, path))

    private fun ids(path: String): List<String> {
        val all = parse(path).getElementsByTagName("*")
        return (0 until all.length).map { (all.item(it) as Element).getAttributeNS(androidNs, "id") }.filter { it.isNotEmpty() }
    }

    private fun element(path: String, id: String): Element {
        val all = parse(path).getElementsByTagName("*")
        for (i in 0 until all.length) {
            val e = all.item(i) as Element
            if (e.getAttributeNS(androidNs, "id") == id) return e
        }
        error("нема $id у $path")
    }

    // --- Логіка «показувати онбординг» ---

    @Test
    fun `first launch shows onboarding`() {
        assertTrue(OnboardingLogic.shouldShow(alreadyShown = false, isDocumentIntent = false))
    }

    @Test
    fun `after skip or finish onboarding is not shown again`() {
        assertFalse(OnboardingLogic.shouldShow(alreadyShown = true, isDocumentIntent = false))
    }

    @Test
    fun `external view intent bypasses onboarding on first launch`() {
        assertFalse(OnboardingLogic.shouldShow(alreadyShown = false, isDocumentIntent = true))
        assertFalse(OnboardingLogic.shouldShow(alreadyShown = true, isDocumentIntent = true))
    }

    @Test
    fun `document intent detection covers view and edit with data only`() {
        assertTrue(OnboardingLogic.isDocumentIntent("android.intent.action.VIEW", true))
        assertTrue(OnboardingLogic.isDocumentIntent("android.intent.action.EDIT", true))
        assertFalse(OnboardingLogic.isDocumentIntent("android.intent.action.VIEW", false))
        assertFalse(OnboardingLogic.isDocumentIntent("android.intent.action.MAIN", true))
        assertFalse(OnboardingLogic.isDocumentIntent(null, false))
        assertEquals(android.content.Intent.ACTION_VIEW, OnboardingLogic.ACTION_VIEW)
        assertEquals(android.content.Intent.ACTION_EDIT, OnboardingLogic.ACTION_EDIT)
    }

    @Test
    fun `onboarding has exactly three pages with next then start`() {
        assertEquals(3, OnboardingLogic.PAGE_COUNT)
        assertFalse(OnboardingLogic.isLastPage(0))
        assertFalse(OnboardingLogic.isLastPage(1))
        assertTrue(OnboardingLogic.isLastPage(2))
        assertEquals(1, OnboardingLogic.nextPage(0))
        assertEquals(2, OnboardingLogic.nextPage(1))
        assertEquals(2, OnboardingLogic.nextPage(2))
        assertEquals(0, OnboardingLogic.clampPage(-5))
        assertEquals(2, OnboardingLogic.clampPage(9))
    }

    @Test
    fun `onboarding preferences do not touch storage on construction`() {
        val ctx = mock(Context::class.java)
        OnboardingPreferences(ctx)
        verifyNoInteractions(ctx)
    }

    @Test
    fun `application does not read onboarding flag`() {
        val app = File(resDir, "../java/com/savoj/volumpdf/VolumPdfApp.kt").readText()
        assertFalse(app.contains("OnboardingPreferences"))
    }

    // --- М9: головна кнопка і розкриття ---

    @Test
    fun `speed dial lists create and scan and is only on library tab`() {
        assertEquals(listOf(HomeLogic.SpeedDialAction.CREATE_PDF, HomeLogic.SpeedDialAction.SCAN), HomeLogic.speedDialActions)
        assertTrue(HomeLogic.fabVisibleOnPage(0))
        assertFalse(HomeLogic.fabVisibleOnPage(1))
        assertFalse(HomeLogic.effectiveExpanded(true, 1))
        assertTrue(HomeLogic.effectiveExpanded(true, 0))
        assertTrue(HomeLogic.speedDialHorizontal(360))
        assertFalse(HomeLogic.speedDialHorizontal(800))
        assertEquals(HomeLogic.ToolbarState(showLibraryActions = true, showLibraryFab = true), HomeLogic.toolbarState(0))
        assertEquals(HomeLogic.ToolbarState(showLibraryActions = false, showLibraryFab = false), HomeLogic.toolbarState(1))
    }

    @Test
    fun `empty state does not duplicate open and scan`() {
        assertEquals(HomeLogic.EmptyState.LIBRARY_EMPTY, HomeLogic.emptyState(hasSearch = false, favoritesOnly = false))
        assertEquals(HomeLogic.EmptyState.SEARCH_NO_RESULTS, HomeLogic.emptyState(hasSearch = true, favoritesOnly = false))
        assertEquals(HomeLogic.EmptyState.SEARCH_NO_RESULTS, HomeLogic.emptyState(hasSearch = true, favoritesOnly = true))
        assertEquals(HomeLogic.EmptyState.FAVORITES_NO_RESULTS, HomeLogic.emptyState(hasSearch = false, favoritesOnly = true))
        assertEquals(
            HomeLogic.EmptyStateButtons(create = true, reset = false),
            HomeLogic.emptyStateButtons(HomeLogic.EmptyState.LIBRARY_EMPTY)
        )
        assertEquals(
            HomeLogic.EmptyStateButtons(create = false, reset = true),
            HomeLogic.emptyStateButtons(HomeLogic.EmptyState.SEARCH_NO_RESULTS)
        )
        assertEquals(
            HomeLogic.EmptyStateButtons(create = false, reset = true),
            HomeLogic.emptyStateButtons(HomeLogic.EmptyState.FAVORITES_NO_RESULTS)
        )
        val layoutIds = ids("layout/fragment_recents.xml")
        assertTrue(layoutIds.contains("@+id/btnEmptyCreate"))
        assertFalse(layoutIds.contains("@+id/btnEmptyOpen"))
        assertFalse(layoutIds.contains("@+id/btnEmptyScan"))
    }

    @Test
    fun `home has one extended fab for open and expandable create and scan`() {
        val main = ids("layout/activity_main.xml")
        listOf("fabOpenPdf", "fabMore", "fabActions", "fabCreatePdf", "fabScanPdf", "fabScrim", "bannerContainer", "bottomNavigation")
            .forEach { assertTrue("нема $it", main.contains("@+id/$it")) }
        val open = element("layout/activity_main.xml", "@+id/fabOpenPdf")
        assertEquals("com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton", open.tagName)
        assertEquals("@string/action_open_pdf", open.getAttributeNS(androidNs, "text"))
        // розкриті дії згорнуті за замовчуванням
        assertEquals("gone", element("layout/activity_main.xml", "@+id/fabActions").getAttributeNS(androidNs, "visibility"))
        // кнопка розкриття має опис для TalkBack
        assertEquals("@string/action_more_actions", element("layout/activity_main.xml", "@+id/fabMore").getAttributeNS(androidNs, "contentDescription"))
        // ціль натискання ≥ 48 dp
        assertEquals("48dp", element("layout/activity_main.xml", "@+id/fabMore").getAttributeNS("http://schemas.android.com/apk/res-auto", "fabCustomSize"))
    }

    // --- М10, М11: меню ---

    @Test
    fun `scanner is removed from toolbar menu`() {
        assertFalse(ids("menu/menu_main.xml").contains("@+id/action_scan"))
    }

    @Test
    fun `favorites and sort are merged into one filter and order item`() {
        val menuIds = ids("menu/menu_main.xml")
        assertTrue(menuIds.contains("@+id/action_filter_sort"))
        listOf("action_filter_favorites", "sort_by_date", "sort_by_name", "sort_by_size").forEach {
            assertTrue("нема $it", menuIds.contains("@+id/$it"))
        }
        // «Обране» і сортування — вкладені в «Фільтр і порядок», а не окремі пункти верхнього рівня
        val root = parse("menu/menu_main.xml").documentElement
        val topLevel = (0 until root.childNodes.length).map { root.childNodes.item(it) }
            .filterIsInstance<Element>().map { it.getAttributeNS(androidNs, "id") }
        assertFalse(topLevel.contains("@+id/action_filter_favorites"))
        assertFalse(topLevel.contains("@+id/action_sort"))
        assertTrue(topLevel.contains("@+id/action_filter_sort"))
        assertTrue(topLevel.contains("@+id/action_search"))
        assertEquals("true", element("menu/menu_main.xml", "@+id/action_filter_favorites").getAttributeNS(androidNs, "checkable"))
    }

    @Test
    fun `recent row has 48dp actions with truthful accessible labels`() {
        val favorite = element("layout/item_recent_document.xml", "@+id/ivFavorite")
        val more = element("layout/item_recent_document.xml", "@+id/ivMoreMenu")
        listOf(favorite, more).forEach {
            assertEquals("48dp", it.getAttributeNS(androidNs, "layout_width"))
            assertEquals("48dp", it.getAttributeNS(androidNs, "layout_height"))
        }
        assertEquals("@string/action_favorite", favorite.getAttributeNS(androidNs, "contentDescription"))
        assertEquals("@string/action_more_actions", more.getAttributeNS(androidNs, "contentDescription"))
    }

    // --- Онбординг: розмітка, тексти, дозволи ---

    @Test
    fun `onboarding has skip on the layout and next button`() {
        val layoutIds = ids("layout/activity_onboarding.xml")
        assertTrue(layoutIds.contains("@+id/btnOnboardingSkip"))
        assertTrue(layoutIds.contains("@+id/btnOnboardingNext"))
        assertTrue(layoutIds.contains("@+id/onboardingPager"))
        assertEquals("48dp", element("layout/activity_onboarding.xml", "@+id/btnOnboardingSkip").getAttributeNS(androidNs, "minHeight"))
    }

    @Test
    fun `onboarding strings exist in four languages and text screens are decorative or described`() {
        val keys = listOf(
            "onboarding_skip", "onboarding_next", "onboarding_start", "onboarding_page_indicator",
            "onboarding_1_title", "onboarding_1_body", "onboarding_2_title", "onboarding_2_body",
            "onboarding_3_title", "onboarding_3_body", "action_more_actions", "action_hide_more_actions", "action_filter_sort"
        )
        listOf("values", "values-uk", "values-de", "values-es", "values-ru").forEach { folder ->
            val nodes = parse("$folder/strings.xml").getElementsByTagName("string")
            val names = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }.toSet()
            keys.forEach { assertTrue("нема $it у $folder", it in names) }
        }
        // декоративна піктограма не читається TalkBack
        assertEquals("no", element("layout/item_onboarding_page.xml", "@+id/ivOnboardingIcon").getAttributeNS(androidNs, "importantForAccessibility"))
    }

    @Test
    fun `onboarding third screen mentions ads and privacy policy honestly`() {
        val en = File(resDir, "values/strings.xml").readText()
        val body = Regex("onboarding_3_body\">([^<]+)<").find(en)!!.groupValues[1]
        assertTrue(body.contains("AdMob"))
        assertTrue(body.contains("Privacy policy"))
        assertTrue(body.contains("no account", ignoreCase = true))
    }

    @Test
    fun `onboarding adds no permissions and is not exported`() {
        val manifest = File(resDir, "../AndroidManifest.xml").readText()
        val perms = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.INTERNET"), perms)
        val block = Regex("<activity[^>]*OnboardingActivity[^>]*/>", RegexOption.DOT_MATCHES_ALL).find(manifest)!!.value
        assertTrue(block.contains("android:exported=\"false\""))
    }

    @Test
    fun clearHistoryStateChange_doesNotRebuildMenuForFilteredListChanges() {
        // Зміна відфільтрованого списку під час пошуку не змінює hasAnyRecentDocuments.
        assertFalse(HomeLogic.clearHistoryStateChanged(previous = true, current = true))
        assertFalse(HomeLogic.clearHistoryStateChanged(previous = false, current = false))
    }

    @Test
    fun clearHistoryStateChange_requiresUpdateWhenAvailabilityChanges() {
        assertTrue(HomeLogic.clearHistoryStateChanged(previous = null, current = false))
        assertTrue(HomeLogic.clearHistoryStateChanged(previous = true, current = false))
        assertTrue(HomeLogic.clearHistoryStateChanged(previous = false, current = true))
    }
}
