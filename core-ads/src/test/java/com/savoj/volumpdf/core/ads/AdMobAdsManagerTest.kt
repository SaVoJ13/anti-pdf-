package com.savoj.volumpdf.core.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class AdMobAdsManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockConsentInformation: ConsentInformation

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockActivity = mock(Activity::class.java)
        mockConsentInformation = mock(ConsentInformation::class.java)

        `when`(mockActivity.applicationContext).thenReturn(mockContext)
        `when`(mockConsentInformation.consentStatus).thenReturn(ConsentInformation.ConsentStatus.NOT_REQUIRED)
        `when`(mockConsentInformation.canRequestAds()).thenReturn(true)
    }

    @Test
    fun testTestAdUnitIdsAreConfiguredCorrectly() {
        // Офіційні тестові ідентифікатори Google AdMob повинні бути встановлені
        assertTrue(AdMobAdsManager.TEST_APP_ID.startsWith("ca-app-pub-3940256099942544"))
        assertTrue(AdMobAdsManager.TEST_BANNER_AD_UNIT_ID.startsWith("ca-app-pub-3940256099942544"))
        assertTrue(AdMobAdsManager.TEST_INTERSTITIAL_AD_UNIT_ID.startsWith("ca-app-pub-3940256099942544"))
        assertEquals(300000L, AdMobAdsManager.DEFAULT_COOLDOWN_MS)
    }

    @Test
    fun testConsentStatusEnumValues() {
        val statuses = ConsentStatus.values()
        assertTrue(statuses.contains(ConsentStatus.UNKNOWN))
        assertTrue(statuses.contains(ConsentStatus.REQUIRED_NOT_OBTAINED))
        assertTrue(statuses.contains(ConsentStatus.OBTAINED))
        assertTrue(statuses.contains(ConsentStatus.NOT_REQUIRED))
    }

    @Test
    fun testShowActionInterstitialCooldownActiveDismissesImmediately() {
        val manager = AdMobAdsManager(
            mockContext,
            cooldownDurationMs = 5000L,
            consentInfoProvider = { mockConsentInformation }
        )
        // Встановлюємо останній показ на теперішній момент
        manager.setLastInterstitialShowTimestampForTesting(System.currentTimeMillis())

        var dismissedCalled = false
        manager.showActionInterstitial(mockActivity) {
            dismissedCalled = true
        }

        assertTrue("Якщо кулдаун активний, onDismissed має викликатися негайно", dismissedCalled)
    }

    @Test
    fun testShowActionInterstitialNoAdCachedDismissesImmediately() {
        val manager = AdMobAdsManager(
            mockContext,
            cooldownDurationMs = 5000L,
            consentInfoProvider = { mockConsentInformation }
        )
        // Встановлюємо останній показ 10 секунд тому (кулдаун пройшов)
        manager.setLastInterstitialShowTimestampForTesting(System.currentTimeMillis() - 10000L)

        var dismissedCalled = false
        manager.showActionInterstitial(mockActivity) {
            dismissedCalled = true
        }

        assertTrue("Якщо оголошення ще не закешоване, onDismissed має викликатися негайно", dismissedCalled)
        assertFalse(manager.hasCachedInterstitial())
    }
}
