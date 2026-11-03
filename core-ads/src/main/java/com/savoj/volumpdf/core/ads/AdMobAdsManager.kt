package com.savoj.volumpdf.core.ads

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Реалізація [AdsManager] на базі Google AdMob SDK та Google UMP (User Messaging Platform).
 * Відповідає правилам Google Play та вимогам конфіденційності GDPR/EEA:
 * - Завантажує рекламу виключно після отримання згоди користувача або якщо згода не потрібна.
 * - При помилці завантаження чи в офлайн-режимі контейнер реклами ховається (View.GONE) без білих дірок.
 * - Повноекранна реклама (Interstitial) обмежується інтервалом кулдауну 5 хвилин між показами.
 */
class AdMobAdsManager(
    private val context: Context,
    private val cooldownDurationMs: Long = DEFAULT_COOLDOWN_MS,
    private val consentInfoProvider: (Context) -> ConsentInformation? = {
        try {
            UserMessagingPlatform.getConsentInformation(it)
        } catch (_: Throwable) {
            null
        }
    }
) : AdsManager {

    private val _consentStatus = MutableStateFlow(ConsentStatus.UNKNOWN)
    override val consentStatus: StateFlow<ConsentStatus> = _consentStatus.asStateFlow()

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private val isPreloadingInterstitial = AtomicBoolean(false)

    @Volatile
    private var cachedInterstitialAd: InterstitialAd? = null

    @Volatile
    var lastInterstitialShowTimestamp: Long = 0L
        private set

    init {
        // Оновити початковий статус згоди з локального сховища UMP
        consentInfoProvider(context)?.let { updateConsentStatus(it) }
    }

    override fun requestConsentAndInit(activity: Activity, onComplete: () -> Unit) {
        val consentInformation = consentInfoProvider(activity)
        if (consentInformation == null) {
            onComplete()
            return
        }

        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    updateConsentStatus(consentInformation)
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds(activity.applicationContext ?: activity)
                    }
                    onComplete()
                }
            },
            { _ ->
                updateConsentStatus(consentInformation)
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds(activity.applicationContext ?: activity)
                }
                onComplete()
            }
        )
    }

    fun initializeMobileAds(appContext: Context) {
        if (isMobileAdsInitialized.compareAndSet(false, true)) {
            try {
                MobileAds.initialize(appContext) {
                    preloadInterstitial(appContext)
                }
            } catch (_: Throwable) {
                // Запобігання падінню, якщо Play Services недоступні
            }
        }
    }

    override fun bindAdaptiveBanner(activity: Activity, container: ViewGroup) {
        val consentInformation = consentInfoProvider(activity)
        if (consentInformation == null || !consentInformation.canRequestAds()) {
            container.visibility = View.GONE
            return
        }

        // Завжди ховаємо контейнер до успішного завантаження, щоб уникнути білих плям в офлайні
        container.visibility = View.GONE
        container.removeAllViews()

        val adView = AdView(activity).apply {
            adUnitId = TEST_BANNER_AD_UNIT_ID
        }

        // Обчислюємо адаптивний розмір банера
        val displayMetrics = activity.resources.displayMetrics
        val widthPixels = if (container.width > 0) container.width.toFloat() else displayMetrics.widthPixels.toFloat()
        val density = displayMetrics.density
        val adWidth = (widthPixels / density).toInt()
        val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        adView.setAdSize(adSize)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                container.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                container.visibility = View.GONE
                container.removeAllViews()
            }
        }

        container.addView(adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    override fun showActionInterstitial(activity: Activity, onDismissed: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShowTimestamp < cooldownDurationMs) {
            onDismissed()
            return
        }

        val ad = cachedInterstitialAd
        val appContext = activity.applicationContext ?: activity
        if (ad == null) {
            // Якщо оголошення ще не завантажилося або пристрій офлайн — не затримуємо користувача
            preloadInterstitial(appContext)
            onDismissed()
            return
        }

        cachedInterstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastInterstitialShowTimestamp = System.currentTimeMillis()
                preloadInterstitial(appContext)
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                lastInterstitialShowTimestamp = System.currentTimeMillis()
                preloadInterstitial(appContext)
                onDismissed()
            }
        }
        ad.show(activity)
    }

    fun preloadInterstitial(appContext: Context) {
        if (isPreloadingInterstitial.get() || cachedInterstitialAd != null) {
            return
        }
        val consentInformation = consentInfoProvider(appContext)
        if (consentInformation == null || !consentInformation.canRequestAds()) {
            return
        }

        isPreloadingInterstitial.set(true)
        val adRequest = AdRequest.Builder().build()
        try {
            InterstitialAd.load(
                appContext,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        cachedInterstitialAd = interstitialAd
                        isPreloadingInterstitial.set(false)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        cachedInterstitialAd = null
                        isPreloadingInterstitial.set(false)
                    }
                }
            )
        } catch (_: Throwable) {
            isPreloadingInterstitial.set(false)
        }
    }

    private fun updateConsentStatus(info: ConsentInformation) {
        _consentStatus.value = when (info.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentStatus.NOT_REQUIRED
            ConsentInformation.ConsentStatus.OBTAINED -> ConsentStatus.OBTAINED
            ConsentInformation.ConsentStatus.REQUIRED -> ConsentStatus.REQUIRED_NOT_OBTAINED
            else -> ConsentStatus.UNKNOWN
        }
    }

    // Допоміжний метод для тестів
    fun setLastInterstitialShowTimestampForTesting(timestamp: Long) {
        this.lastInterstitialShowTimestamp = timestamp
    }

    // Допоміжний метод для перевірки наявності кешу в тестах
    fun hasCachedInterstitial(): Boolean = cachedInterstitialAd != null

    companion object {
        // Офіційні тестові ідентифікатори Google AdMob
        const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        // Обмеження частоти між показами повноекранної реклами: 5 хвилин
        const val DEFAULT_COOLDOWN_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: AdMobAdsManager? = null

        fun getInstance(context: Context): AdMobAdsManager {
            return instance ?: synchronized(this) {
                instance ?: AdMobAdsManager(context.applicationContext ?: context).also {
                    instance = it
                }
            }
        }
    }
}
