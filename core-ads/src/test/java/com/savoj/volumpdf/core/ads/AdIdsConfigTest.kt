package com.savoj.volumpdf.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Захист від випадкових бойових ID у debug. Unit-тести виконуються на debug-варіанті, тож
 * BuildConfig тут завжди збирається з гілки debug (rootProject build.gradle.kts, блок «AdMob ID»).
 * Release без admob.properties перевіряється командами в docs/release/ADMOB_SETUP.md (розділ для розробника)
 * та Gradle-задачею :app:verifyAdmobReleaseManifest.
 */
class AdIdsConfigTest {

    private val testPublisher = "ca-app-pub-3940256099942544"

    @Test
    fun debugBuildUsesOnlyGoogleTestAdUnitIds() {
        assertTrue("debug: ADMOB_USING_TEST_IDS має бути true", BuildConfig.ADMOB_USING_TEST_IDS)
        assertEquals(AdMobAdsManager.TEST_BANNER_AD_UNIT_ID, BuildConfig.ADMOB_BANNER_AD_UNIT_ID)
        assertEquals(AdMobAdsManager.TEST_INTERSTITIAL_AD_UNIT_ID, BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID)
        assertTrue(BuildConfig.ADMOB_BANNER_AD_UNIT_ID.startsWith("$testPublisher/"))
        assertTrue(BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.startsWith("$testPublisher/"))
    }

    @Test
    fun testIdsHaveExpectedFormat() {
        assertTrue(Regex("""^ca-app-pub-\d{16}~\d{10}$""").matches(AdMobAdsManager.TEST_APP_ID))
        assertTrue(Regex("""^ca-app-pub-\d{16}/\d{10}$""").matches(AdMobAdsManager.TEST_BANNER_AD_UNIT_ID))
        assertTrue(Regex("""^ca-app-pub-\d{16}/\d{10}$""").matches(AdMobAdsManager.TEST_INTERSTITIAL_AD_UNIT_ID))
    }
}
