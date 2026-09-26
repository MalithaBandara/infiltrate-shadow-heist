package com.infiltrate.ads

import app.lexilabs.basic.ads.Consent
import app.lexilabs.basic.ads.DependsOnGoogleUserMessagingPlatform
import kotlin.native.ObjCName

/**
 * iOS half of the consent flow described on [AdPrivacy], driven from AppDelegate.swift's
 * `applicationDidBecomeActive` in three steps:
 *
 * 1. [gatherConsent] - refresh Google's consent status and show its consent message if this user
 *    needs one (UMP presents it over the key window's current view controller).
 * 2. Swift asks for App Tracking Transparency. That step lives in Swift, not here, because
 *    AppTrackingTransparency is a system framework Swift can import directly, and Apple only
 *    shows the prompt while the app is active - which `applicationDidBecomeActive` guarantees.
 *    Google's own ordering: the GDPR message first, ATT after it.
 * 3. [finish] - report to [AdPrivacy], which is what lets the Compose ad hosts start the SDK and
 *    load. Nothing is requested before this, so the first request already carries the user's ATT
 *    answer (and, with it, the IDFA if they allowed tracking).
 *
 * UMP calls back on the main thread; Swift must hop back to main before [finish], since ATT's own
 * completion handler does not.
 */
@OptIn(
    DependsOnGoogleUserMessagingPlatform::class,
    kotlin.experimental.ExperimentalObjCName::class,
    kotlin.experimental.ExperimentalObjCRefinement::class,
)
@ObjCName(name = "AdConsentBridge", exact = true)
object AdConsentBridge {
    private val consent: Consent by lazy { Consent(null) }
    private var inFlight = false

    /** True while a flow is running or once ads may already be requested - Swift skips a new run. */
    val isSettledOrRunning: Boolean
        get() = inFlight || AdPrivacy.canRequestAds

    /** A flow has run to the end at least once (whatever it concluded). */
    var hasFinished: Boolean = false
        private set

    fun gatherConsent(onGathered: () -> Unit) {
        if (isSettledOrRunning) return
        inFlight = true
        AdPrivacy.setPrivacyOptionsPresenter {
            consent.showPrivacyOptionsForm(onDismissed = { report() })
        }
        consent.requestConsentInfoUpdate(
            onCompletion = {
                consent.loadAndShowConsentForm(
                    onLoaded = { onGathered() },
                    onError = { onGathered() },
                )
            },
            // Google's own pattern: an update failure still falls through to canRequestAds, which
            // then reflects the consent this user gave in an earlier session.
            onError = { onGathered() },
        )
    }

    fun finish() {
        inFlight = false
        hasFinished = true
        report()
    }

    private fun report() {
        AdPrivacy.onConsentUpdated(
            canRequestAds = consent.canRequestAds,
            privacyOptionsRequired = consent.privacyOptionsRequired,
        )
    }
}
