package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberRewardedAd
import kotlinx.coroutines.delay

private const val MAX_LOAD_ATTEMPTS = 3

/** Long enough for a transient no-fill to clear, short enough that the player keeps waiting. */
private const val RETRY_DELAY_MS = 1200L

/**
 * A rewarded ad that survives a load failure, for the Store's tap-to-watch placements
 * ([CoinsRewardAdHost], [GadgetRewardAdHost]).
 *
 * These are load-on-demand: nothing is fetched until the player taps WATCH AD, so the very first
 * answer AdMob gives is also the only one the old code ever looked at - `basic-ads`' plain
 * `RewardedAd()` composable reports `onFailure` and stops, which is where the player's
 * "Ad not ready. Please try again later." came from. A single failed request is a weak reason to
 * refuse: a cold request soon after `GADMobileAds.start()`, a momentary no-fill, or a network blip
 * all land there, and iOS meets all three more often than Android does because the SDK is only
 * started when the Compose scene first composes (see AdMobVerifyScreen.kt) rather than in the
 * Activity's own onCreate.
 *
 * Retrying is not simply "call load again", because of the dead end documented in
 * .junie/guidelines.md ("Ad preloading and its two hazards", hazard 2): `rememberRewardedAd` only
 * starts a load when its handler reads `NONE` or `DISMISSED`, and a failed handler sits in
 * `FAILING` for good. The way back is a *new* handler, which is what [key] gives us - bumping
 * `attempt` discards the composition group the old handler was remembered in and builds a fresh
 * one that starts from `NONE`.
 *
 * [onFailure] still fires, exactly once, when every attempt is spent, so the Store's toast and its
 * `showXRewardAd = false` bookkeeping behave as before - just after a real effort rather than one
 * request. The daily ad-watch limiters are untouched: they record a *watch*, and nothing here can
 * grant a reward more than once.
 *
 * Android deliberately keeps `basic-ads`' plain composable: its rewarded placements were reported
 * working, and the same retry there would be an unrequested change to a path that fills first time.
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
internal fun RetryingRewardedAd(
    adUnitId: String,
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit,
) {
    // The attempt currently composing, and the attempt a failure has queued up. They are separate
    // so the backoff below owns the moment the new handler is built: `pendingAttempt` is written
    // from the load callback (off the composition), and `attempt` only moves once the delay is up.
    var attempt by remember { mutableStateOf(0) }
    var pendingAttempt by remember { mutableStateOf(0) }
    // Whichever of dismissal / give-up happens first ends this host. Guards against a second
    // callback - basic-ads reports dismissal and display failure through the same delegate -
    // reopening something the Store has already torn down.
    var resolved by remember { mutableStateOf(false) }

    LaunchedEffect(pendingAttempt) {
        if (pendingAttempt > attempt) {
            delay(RETRY_DELAY_MS)
            attempt = pendingAttempt
        }
    }

    if (resolved) return

    key(attempt) {
        val ad by rememberRewardedAd(
            adUnitId = adUnitId,
            onFailure = {
                val next = attempt + 1
                if (next < MAX_LOAD_ATTEMPTS) {
                    pendingAttempt = next
                } else if (!resolved) {
                    resolved = true
                    onFailure()
                }
            },
        )
        if (ad.state == AdState.READY) {
            ad.setListeners(
                // A failure once the ad is in hand is not a load problem and gets no retry - the
                // player is looking at a half-opened ad, so hand control straight back.
                onFailure = {
                    if (!resolved) {
                        resolved = true
                        onFailure()
                    }
                },
                onDismissed = {
                    if (!resolved) {
                        resolved = true
                        onDismissed()
                    }
                },
            )
            ad.show { onRewardEarned() }
        }
    }
}
