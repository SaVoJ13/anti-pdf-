package com.savoj.volumpdf.core.ads

import android.app.Activity
import android.view.ViewGroup
import kotlinx.coroutines.flow.StateFlow

interface AdsManager {
    // Потік стану згоди UMP
    val consentStatus: StateFlow<ConsentStatus>

    // Ініціалізація та показ діалогу згоди Google UMP
    fun requestConsentAndInit(activity: Activity, onComplete: () -> Unit)

    // Прив'язка адаптивного банера до контейнера у списку (безпечно згортається при помилці/офлайн)
    fun bindAdaptiveBanner(activity: Activity, container: ViewGroup)

    // Показ Interstitial реклами після завершення дії з обмеженням частоти (cooldown, напр. 5 хв)
    fun showActionInterstitial(activity: Activity, onDismissed: () -> Unit)
}
