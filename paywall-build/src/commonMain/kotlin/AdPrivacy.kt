package com.infiltrate.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where the ad consent flow's outcome lives, for the rest of the app to read.
 *
 * Ads are personalized (see AdMobVerifyScreen.kt / MainActivity.kt), which puts two gates in
 * front of the first ad request: Google's User Messaging Platform consent message (GDPR - shown
 * only in the EEA, UK and Switzerland, and only once a message is published in AdMob's "Privacy &
 * messaging" tab) on both platforms, then Apple's App Tracking Transparency prompt on iOS. Both
 * have to be answered before the SDK starts, or the first requests go out without the user's
 * answer. The platform side runs that flow (AdConsent.android.kt / AdConsentBridge.kt plus
 * AppDelegate.swift's ATT step) and reports here; every ad host checks [canRequestAds] and
 * resolves a request immediately while it is false rather than waiting on an ad that cannot load.
 *
 * Desktop/JVM never reports, so this stays false there - JVM's ad hosts grant immediately and
 * never read it, and the Settings privacy row stays hidden.
 */
object AdPrivacy {
    /** The consent flow has finished and Google's SDK may start requesting ads. */
    var canRequestAds: Boolean by mutableStateOf(false)
        private set

    /**
     * Google requires a way back into the consent message for users it applies to (the privacy
     * options entry point) - Settings shows its row only while this is true.
     */
    var privacyOptionsRequired: Boolean by mutableStateOf(false)
        private set

    private var privacyOptionsPresenter: (() -> Unit)? = null

    fun onConsentUpdated(canRequestAds: Boolean, privacyOptionsRequired: Boolean) {
        this.canRequestAds = canRequestAds
        this.privacyOptionsRequired = privacyOptionsRequired
    }

    /** Set by the platform once it holds something able to present Google's privacy options form. */
    fun setPrivacyOptionsPresenter(presenter: (() -> Unit)?) {
        privacyOptionsPresenter = presenter
    }

    fun showPrivacyOptions() {
        privacyOptionsPresenter?.invoke()
    }
}
