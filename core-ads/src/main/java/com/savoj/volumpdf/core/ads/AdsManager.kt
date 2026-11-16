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

    // Чи Google вимагає показати користувачу пункт «Налаштування конфіденційності» (UMP privacy options)
    fun isPrivacyOptionsRequired(): Boolean

    // Показ форми UMP privacy options (змінити/відкликати згоду). onDone викликається після закриття форми
    fun showPrivacyOptionsForm(activity: Activity, onDone: () -> Unit)

    // Показ Interstitial реклами після завершення дії з обмеженням частоти (cooldown, напр. 5 хв)
    fun showActionInterstitial(activity: Activity, onDismissed: () -> Unit)
}
