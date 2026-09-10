package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberInterstitialAd

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

/**
 * Preloads the level-exit interstitial and shows it on request.
 *
 * The [rememberInterstitialAd] call sits OUTSIDE the [InterstitialAdTrigger.showRequested] gate
 * on purpose. It used to be inside it, via basic-ads' one-shot `InterstitialAd(...)` composable,
 * which is simply `rememberInterstitialAd()` + `setListeners()` + `show()` - so nothing existed
 * until the player finished a level, and the network fetch happened while they waited. Hoisting
 * the handler out means the fetch starts when this content first composes and, because
 * `rememberInterstitialAd` re-loads whenever the handler is `NONE` or `DISMISSED`, the next ad
 * begins loading as soon as the previous one is closed.
 *
 * Two hazards come with preloading, and neither exists in the load-then-show shape:
 *
 * 1. **A background failure must not resolve a request the player never made.** [onAdClosed] is
 *    what tells the rest of the app the ad flow is over, so firing it from a preload that failed
 *    while nothing was pending would be a phantom "ad finished". Hence the `showRequested` guard
 *    in the load-failure callback below.
 *
 * 2. **`FAILING` is a dead end.** `rememberInterstitialAd` only re-loads from `NONE` or
 *    `DISMISSED`; it does nothing at all from `FAILING`. Before preloading that was harmless,
 *    because the handler was created on demand and its failure immediately reached `onFailure`.
 *    Now a preload that failed early would leave the handler dead for the rest of the process,
 *    and a later request would get no ad AND no resolution - a level-exit that never completes.
 *    The `FAILING` branch resolves it the same way a load failure resolved it before.
 *
 * Note that test ad units (see [AdUnitIds] - `USE_TEST_ADS`) always fill instantly and never
 * fail, so neither hazard can be reproduced while testing against them. Both paths have to be
 * forced by hand (airplane mode is the easy one).
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun InterstitialAdContent() {
    val ad by rememberInterstitialAd(
        adUnitId = AdUnitIds.INTERSTITIAL_LEVEL_EXIT,
        onFailure = {
            if (InterstitialAdTrigger.showRequested.value) InterstitialAdTrigger.onAdClosed()
        },
    )
    if (InterstitialAdTrigger.showRequested.value) {
        when (ad.state) {
            AdState.READY -> {
                ad.setListeners(
                    onFailure = { InterstitialAdTrigger.onAdClosed() },
                    onDismissed = { InterstitialAdTrigger.onAdClosed() },
                )
                ad.show()
            }
            // See hazard 2 above - without this the player waits on an ad that will never load.
            AdState.FAILING -> InterstitialAdTrigger.onAdClosed()
            // NONE/LOADING: the preload has not finished yet, which is the same wait as the old
            // behaviour and no worse. The handler is a MutableState, so reaching READY recomposes
            // this and shows the ad. SHOWING/SHOWN/DISMISSED: already in flight, leave it alone.
            else -> Unit
        }
    }
}
