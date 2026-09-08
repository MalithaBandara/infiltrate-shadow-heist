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
