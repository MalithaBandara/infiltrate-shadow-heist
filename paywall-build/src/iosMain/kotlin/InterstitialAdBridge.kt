package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.InterstitialAd
import kotlin.native.ObjCName

/**
 * Level-exit interstitial trigger - iOS plumbing only, matching ContinueAdTrigger's exported
 * shape. Nothing polls this yet: LevelExitBridge.ios.kt is still a no-op stub (ios-shell has no
 * Swift poll loop for "leaving gameplay" the way it does for the watch-ad-to-continue flow - see
 * .junie/guidelines.md). This exists so that future Swift wiring has a real object to call against,
 * following the exact @ObjCName(exact = true) export pattern ContinueAdTrigger already proved out
 * (without it, the linked ObjC symbol keeps the framework-name prefix and Swift can't resolve it).
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "InterstitialAdTrigger", exact = true)
object InterstitialAdTrigger {
    internal val showRequested: MutableState<Boolean> = mutableStateOf(false)

    fun requestShow() {
        showRequested.value = true
    }

    fun onAdClosed() {
        showRequested.value = false
    }
}

/**
 * NOT preloaded, deliberately - unlike the Android [InterstitialAdContent] and both platforms'
 * ContinueAdContent, which now hoist their handler out of the `showRequested` gate.
 *
 * Preloading only pays off for a placement that actually gets shown, and this one never is:
 * LevelExitBridge.ios.kt is still a no-op stub, so nothing on iOS ever calls [requestShow]. A
 * hoisted `rememberInterstitialAd` would therefore fetch an ad on every composition and reload
 * after each expiry, forever, for zero impressions - which is exactly the pattern AdMob's
 * invalid-traffic policy flags, and it would quietly ruin this ad unit's fill-rate reporting.
 *
 * Preload this at the same time as wiring the Swift poll loop, not before. Copy the Android
 * version when you do; its two hazard notes (a background failure must not resolve an unmade
 * request, and `FAILING` is a dead end `rememberInterstitialAd` never retries) apply here too.
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun InterstitialAdContent() {
    if (InterstitialAdTrigger.showRequested.value) {
        InterstitialAd(
            adUnitId = AdUnitIds.INTERSTITIAL_LEVEL_EXIT,
            onDismissed = { InterstitialAdTrigger.onAdClosed() },
            onFailure = { InterstitialAdTrigger.onAdClosed() },
        )
    }
}
