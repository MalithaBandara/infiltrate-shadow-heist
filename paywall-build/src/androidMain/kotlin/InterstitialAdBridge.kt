package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberInterstitialAd
import kotlinx.coroutines.delay

/**
 * Level-exit interstitial trigger - Android side, same plain-shared-object shape as
 * ContinueAdTrigger (android-shell's MainActivity and :game's GameplayScene run in the same JVM/
 * APK, so no Swift-style poll loop is needed here). Every interstitial placement goes through
 * [maybeRequestShow].
 */
object InterstitialAdTrigger {
    internal val showRequested: MutableState<Boolean> = mutableStateOf(false)

    // Answers from the live handler, set by [InterstitialAdContent]. False until consent settles.
    internal var adReadyProbe: () -> Boolean = { false }

    // Whether the ad reached the screen, for [cancelIfNotDisplayed] and the limiter.
    private var displayed: Boolean = false

    /** True from the request until the ad has closed or failed. */
    val isAdFlowActive: Boolean get() = showRequested.value

    /**
     * The gate for every interstitial. An ad that has not finished loading is skipped rather than
     * waited for: it would otherwise pop up seconds later, over the menu or over the next level.
     * The limiter is only charged once the ad is really on screen - see [onAdDisplayed].
     */
    fun maybeRequestShow(totalLevelsCompleted: Int, isPremium: Boolean): Boolean {
        if (isPremium || showRequested.value) return false
        if (!InterstitialAdLimiter.canShow(totalLevelsCompleted)) return false
        if (!adReadyProbe()) return false
        requestShow()
        return true
    }

    // The ad counts as up (AdAudioCoordinator: menu music silent) from the request, not from when
    // the ad composes: the request comes in the same tick the menu comes back, so the menu's
    // music would otherwise start under the ad and only stop a frame later.
    fun requestShow() {
        if (showRequested.value) return
        displayed = false
        showRequested.value = true
        AdAudioCoordinator.onAdStarted()
    }

    internal fun onAdDisplayed() {
        displayed = true
        InterstitialAdLimiter.recordShown()
    }

    /** Safety net for a caller waiting on the ad: never strands the player if it did not appear. */
    fun cancelIfNotDisplayed() {
        if (!displayed) onAdClosed()
    }

    fun onAdClosed() {
        if (!showRequested.value) return
        showRequested.value = false
        AdAudioCoordinator.onAdDismissed()
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
 * Resolving was not enough on its own: one failed load (a momentary no-fill, a network blip) also
 * ended interstitials for the rest of the process. `rememberInterstitialAd` cannot be restarted,
 * but a new handler begins at `NONE`, so [key] replaces the dead one after
 * [InterstitialAdLimiter.retryDelayMillis]. A failed DISPLAY lands in `FAILING` too and recovers
 * the same way.
 *
 * Note that test ad units (see [AdUnitIds] - `USE_TEST_ADS`) always fill instantly and never
 * fail, so neither hazard can be reproduced while testing against them. Both paths have to be
 * forced by hand (airplane mode is the easy one).
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun InterstitialAdContent() {
    // Nothing may be requested until the consent flow settles (see AdPrivacy) - no preload, and a
    // request made in the meantime is simply dropped, as an unfilled interstitial already is.
    if (!AdPrivacy.canRequestAds) {
        if (InterstitialAdTrigger.showRequested.value) {
            LaunchedEffect(Unit) { InterstitialAdTrigger.onAdClosed() }
        }
        return
    }
    var attempt by remember { mutableStateOf(0) }
    var consecutiveFailures by remember { mutableStateOf(0) }

    key(attempt) {
        val ad by rememberInterstitialAd(
            adUnitId = AdUnitIds.INTERSTITIAL_LEVEL_EXIT,
            onFailure = {
                if (InterstitialAdTrigger.showRequested.value) InterstitialAdTrigger.onAdClosed()
            },
        )
        SideEffect { InterstitialAdTrigger.adReadyProbe = { ad.state == AdState.READY } }

        // Replaces a dead handler - see the note under hazard 2. Bumping `attempt` discards this
        // whole group, this effect included, which is the end of its work anyway.
        LaunchedEffect(ad.state) {
            when (ad.state) {
                AdState.READY -> consecutiveFailures = 0
                AdState.FAILING -> {
                    consecutiveFailures += 1
                    delay(InterstitialAdLimiter.retryDelayMillis(consecutiveFailures))
                    attempt += 1
                }
                else -> Unit
            }
        }

        if (InterstitialAdTrigger.showRequested.value) {
            when (ad.state) {
                AdState.READY -> {
                    ad.setListeners(
                        onFailure = { InterstitialAdTrigger.onAdClosed() },
                        onDismissed = { InterstitialAdTrigger.onAdClosed() },
                        onShown = { InterstitialAdTrigger.onAdDisplayed() },
                    )
                    ad.show()
                }
                // See hazard 2 above - without this the player waits on an ad that will never load.
                AdState.FAILING -> InterstitialAdTrigger.onAdClosed()
                // NONE/LOADING: can only mean the handler was replaced since the request was made;
                // the caller's cancelIfNotDisplayed() deadline covers it. SHOWING/SHOWN/DISMISSED:
                // already in flight, leave it alone.
                else -> Unit
            }
        }
    }
}
