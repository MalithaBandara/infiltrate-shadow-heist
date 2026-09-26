package com.infiltrate.ui

import androidx.compose.runtime.Composable
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.RequestConfiguration
import com.infiltrate.ads.AdPrivacy
import kotlin.native.ObjCName

// This is the app's real, sole BasicAds.Initialize() call site on iOS (called from
// MainMenuComposeViewController.kt) - originally written as an on-device verification spike (see
// .junie/guidelines.md "AdMob (basic-ads) feasibility spike"), it renders inside the SAME scene
// MainMenuComposeScreen already owns rather than a second ComposeUIViewController (that crashed -
// see guidelines.md for the full story). The spike's own invisible 1dp BannerAd() has been
// removed: it was a real (if visually hidden) ad impression firing on every launch in production,
// which violates AdMob's policy against ads not visible to users and put the AdMob account at
// risk. SDK init and the configuration below are real production behavior and stay; only that
// banner load is gone.
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "AdMobVerifyBridge", exact = true)
object AdMobVerifyBridge {
    var initializeCalled: Boolean = false
        private set
    var personalizationEnabled: Boolean = false
        private set

    fun markInitializeCalled() {
        initializeCalled = true
    }

    fun markPersonalizationEnabled() {
        personalizationEnabled = true
    }
}

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun AdMobVerifyContent() {
    // Not before the consent flow (UMP, then App Tracking Transparency - see AdPrivacy and
    // AdConsentBridge) has settled: starting the SDK is what lets the ad hosts begin preloading,
    // and a request sent before the ATT answer goes out without the IDFA even for a user who is
    // about to allow tracking.
    if (!AdPrivacy.canRequestAds) return
    BasicAds.Initialize()
    // Personalized ads. DEFAULT, not ENABLED: DEFAULT is the SDK's own normal behaviour - it
    // personalizes wherever the user's answers allow and not otherwise - where the old DISABLED
    // forced every request non-personalized regardless (the stand-in for having no ATT prompt).
    // The IDFA itself is only ever readable after the user allows tracking in the ATT prompt;
    // GDPR consent from UMP reaches the SDK on its own, through the TCF string UMP stores.
    BasicAds.configuration = RequestConfiguration(
        maxAdContentRating = null,
        publisherPrivacyPersonalizationState = RequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT,
        tagForChildDirectedTreatment = RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
        tagForUnderAgeOfConsent = RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED,
        testDeviceIds = null,
    )
    // Read the value back rather than trusting the assignment above didn't silently no-op -
    // GADMobileAds' own setter is a black box from this side of the binding.
    if (BasicAds.configuration.publisherPrivacyPersonalizationState !=
        RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
    ) {
        AdMobVerifyBridge.markPersonalizationEnabled()
    }
    AdMobVerifyBridge.markInitializeCalled()
}
