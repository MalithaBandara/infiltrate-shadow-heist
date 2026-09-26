package com.infiltrate.ui

import androidx.compose.runtime.Composable
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.RequestConfiguration
import kotlin.native.ObjCName

// This is the app's real, sole BasicAds.Initialize() call site on iOS (called from
// MainMenuComposeViewController.kt) - originally written as an on-device verification spike (see
// .junie/guidelines.md "AdMob (basic-ads) feasibility spike"), it renders inside the SAME scene
// MainMenuComposeScreen already owns rather than a second ComposeUIViewController (that crashed -
// see guidelines.md for the full story). The spike's own invisible 1dp BannerAd() has been
// removed: it was a real (if visually hidden) ad impression firing on every launch in production,
// which violates AdMob's policy against ads not visible to users and put the AdMob account at
// risk. SDK init and the non-personalized configuration below are real production behavior and
// stay; only that banner load is gone.
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "AdMobVerifyBridge", exact = true)
object AdMobVerifyBridge {
    var initializeCalled: Boolean = false
        private set
    var personalizationDisabled: Boolean = false
        private set

    fun markInitializeCalled() {
        initializeCalled = true
    }

    fun markPersonalizationDisabled() {
        personalizationDisabled = true
    }
}

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun AdMobVerifyContent() {
    BasicAds.Initialize()
    // No App Tracking Transparency prompt exists anywhere in this app, so the SDK must never be
    // allowed to request the IDFA for ad personalization - doing so without first showing the ATT
    // prompt is an App Review rejection (Guideline 5.1.2). DISABLED tells GADMobileAds to serve
    // ads without personalization (equivalent to a per-request npa=1) at the SDK level, for every
    // ad requested afterward, so no per-ad-unit wiring is needed. Android is unaffected - ATT is
    // an iOS-only requirement, and this file has no androidMain counterpart.
    BasicAds.configuration = RequestConfiguration(
        maxAdContentRating = null,
        publisherPrivacyPersonalizationState = RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED,
        tagForChildDirectedTreatment = RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED,
        tagForUnderAgeOfConsent = RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED,
        testDeviceIds = null,
    )
    // Read the value back rather than trusting the assignment above didn't silently no-op -
    // GADMobileAds' own setter is a black box from this side of the binding.
    if (BasicAds.configuration.publisherPrivacyPersonalizationState ==
        RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
    ) {
        AdMobVerifyBridge.markPersonalizationDisabled()
    }
    AdMobVerifyBridge.markInitializeCalled()
}
