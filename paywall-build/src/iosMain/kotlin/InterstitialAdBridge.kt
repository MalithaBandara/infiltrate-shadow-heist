package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.InterstitialAd
import kotlin.native.ObjCName

/**
 * Level-exit interstitial trigger, matching ContinueAdTrigger's exported shape. Polled from
 * ios-shell/Sources/AppDelegate.swift's `startObservingLevelEnd()` alongside `GameLevelExitBridge`
 * (`src@ios/LevelExitBridge.ios.kt`) - when that bridge reports a real QUIT/RETURN TO
 * MENU/MAIN MENU/ALL CLEAR request, Swift calls [maybeRequestShow] before switching to the
 * Compose menu, following the exact @ObjCName(exact = true) export pattern ContinueAdTrigger
 * already proved out (without it, the linked ObjC symbol keeps the framework-name prefix and
 * Swift can't resolve it).
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

    /**
     * Real (non-spike) gate for Swift: mirrors MainActivity.kt's maybeShowLevelExitInterstitial()
     * exactly, checking [InterstitialAdLimiter] itself rather than duplicating its constants/state
     * on the Swift side. Returns whether an ad was actually requested, purely for logging - Swift
     * doesn't need to branch on it, since [InterstitialAdContent] is already inert until
     * [requestShow] actually fires.
     */
    fun maybeRequestShow(totalLevelsCompleted: Int, isPremium: Boolean): Boolean {
        if (isPremium) return false
        if (!InterstitialAdLimiter.canShow(totalLevelsCompleted)) return false
        InterstitialAdLimiter.recordShown()
        requestShow()
        return true
    }
}

/**
 * NOT preloaded, deliberately - unlike the Android [InterstitialAdContent] and both platforms'
 * ContinueAdContent, which now hoist their handler out of the `showRequested` gate. [requestShow]
 * is now real (see [InterstitialAdTrigger.maybeRequestShow] and `GameLevelExitBridge`'s Swift poll
 * loop in AppDelegate.swift), but this placement's own fetch is still load-on-demand rather than
 * preloaded - level exits are infrequent enough (gated by [InterstitialAdLimiter]'s cooldown/
 * session cap) that the load-latency gap is a real but minor cost, not worth the preload hazards
 * below yet.
 *
 * If preloading this is ever done, copy the Android version; its two hazard notes (a background
 * failure must not resolve an unmade request, and `FAILING` is a dead end `rememberInterstitialAd`
 * never retries) apply here too.
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
