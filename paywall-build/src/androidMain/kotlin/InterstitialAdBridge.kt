package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.InterstitialAd

/**
 * Level-exit interstitial trigger - Android side, same plain-shared-object shape as
 * ContinueAdTrigger (android-shell's MainActivity and :game's GameplayScene run in the same JVM/
 * APK, so no Swift-style poll loop is needed here). MainActivity only calls [requestShow] after
 * InterstitialAdLimiter.canShow(...) has already passed - this object itself enforces nothing,
 * it's purely load-then-show plumbing.
 */
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
