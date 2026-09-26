package com.infiltrate.ads

import android.app.Activity
import app.lexilabs.basic.ads.Consent
import app.lexilabs.basic.ads.DependsOnGoogleUserMessagingPlatform

/**
 * Android half of the consent flow described on [AdPrivacy]: refresh Google's consent status,
 * show its consent message if this user needs one, then report. There is no ATT equivalent on
 * Android - the advertising ID needs no runtime prompt, and a user who opted out of ad
 * personalization in system settings is honoured by the SDK itself.
 *
 * Called from MainActivity's onCreate (first launch) and onResume (a retry if the first attempt
 * failed offline - without it ads would stay off for the rest of the process). `basic-ads`'
 * [Consent] holds the Activity it was built with, so the privacy options presenter is rebuilt
 * against every Activity handed in, even once the flow itself has already finished.
 */
@OptIn(DependsOnGoogleUserMessagingPlatform::class)
object AdConsent {
    private var inFlight = false

    fun gather(activity: Activity) {
        val consent = Consent(activity)
        AdPrivacy.setPrivacyOptionsPresenter {
            consent.showPrivacyOptionsForm(onDismissed = { report(consent) })
        }
        if (inFlight || AdPrivacy.canRequestAds) return
        inFlight = true
        consent.requestConsentInfoUpdate(
            onCompletion = {
                // Shows the form only when UMP says this user needs it; otherwise completes at once.
                consent.loadAndShowConsentForm(
                    onLoaded = { finish(consent) },
                    onError = { finish(consent) },
                )
            },
            // Google's own pattern: an update failure still falls through to canRequestAds, which
            // then reflects the consent this user gave in an earlier session.
            onError = { finish(consent) },
        )
    }

    private fun finish(consent: Consent) {
        inFlight = false
        report(consent)
    }

    private fun report(consent: Consent) {
        AdPrivacy.onConsentUpdated(
            canRequestAds = consent.canRequestAds,
            privacyOptionsRequired = consent.privacyOptionsRequired,
        )
    }
}
