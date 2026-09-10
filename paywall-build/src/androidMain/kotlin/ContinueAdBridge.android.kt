package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberRewardedAd

/**
 * Real (non-spike) "watch ad to continue" trigger - Android side. Same shape as the iOS
 * ContinueAdBridge.kt, but plain Kotlin: android-shell's MainActivity, this Compose content, and
 * :game's GameplayScene all run in the one JVM/APK, so there's no Swift-style poll-loop
 * middleman needed here - MainActivity calls requestShow()/consumeOutcomeFinished() directly.
 */
object ContinueAdTrigger {
    internal val showRequested: MutableState<Boolean> = mutableStateOf(false)

    private var outcomeFinished: Boolean = false

    var rewardEarned: Boolean = false
        private set

    fun requestShow() {
        outcomeFinished = false
        rewardEarned = false
        showRequested.value = true
    }

    // Reward earned mid-ad is not the same moment as the ad actually closing: AdMob's rewarded
    // ad runs as its own separate full-screen Activity on top of MainActivity, and
    // onRewardEarned can fire well before the player taps to close it. GameplayScene's own
    // update loop keeps running the whole time this ad Activity is in front (the KorGE view is
    // never hidden - see MainActivity.kt), so if this set outcomeFinished/showRequested here (it
    // used to), that loop would reload the scene immediately, while MainActivity itself is still
    // backgrounded behind the ad's Activity - a real, confirmed cause of the reload landing on a
    // grey screen instead of the resumed level. Only record that the reward was earned; finishing
    // the outcome is onAdClosed()'s job now, called from onDismissed/onFailure below, which don't
    // fire until the ad Activity is actually gone and MainActivity is foreground again.
    fun markRewardEarned() {
        rewardEarned = true
    }

    fun onAdClosed() {
        outcomeFinished = true
        showRequested.value = false
    }

    fun consumeOutcomeFinished(): Boolean {
        if (!outcomeFinished) return false
        outcomeFinished = false
        return true
    }
}

/**
 * Preloads the watch-ad-to-continue rewarded ad and shows it on request.
 *
 * The [rememberRewardedAd] call sits OUTSIDE the [ContinueAdTrigger.showRequested] gate on
 * purpose. It used to be inside it, via basic-ads' one-shot `RewardedAd(...)` composable, so
 * nothing existed until the player had already died and asked to continue - and the network
 * fetch happened while they sat looking at the prompt. Hoisting the handler out starts the fetch
 * when this content first composes, and `rememberRewardedAd` re-loads whenever the handler is
 * `NONE` or `DISMISSED`, so the next one begins loading as soon as the previous is closed. This
 * is the placement where the wait was worst: it lands at a failure moment, on the one prompt the
 * game most wants the player to accept.
 *
 * Two hazards come with preloading, and neither exists in the load-then-show shape:
 *
 * 1. **A background failure must not resolve a request the player never made.** [onAdClosed]
 *    sets `outcomeFinished`, which GameplayScene polls via `consumeOutcomeFinished()` and treats
 *    as "the ad flow ended" - firing it from a preload that failed while nothing was pending
 *    would skip the player straight past an offer they were never shown. Hence the
 *    `showRequested` guard in the load-failure callback below.
 *
 * 2. **`FAILING` is a dead end.** `rememberRewardedAd` only re-loads from `NONE` or `DISMISSED`;
 *    it does nothing at all from `FAILING`. Before preloading that was harmless, because the
 *    handler was created on demand and its failure immediately reached `onFailure`. Now a preload
 *    that failed early would leave the handler dead for the rest of the process, and a later
 *    request would get no ad AND no resolution - the continue prompt would hang with the player
 *    unable to either watch or decline. The `FAILING` branch resolves it exactly as a load
 *    failure resolved it before.
 *
 * [markRewardEarned] deliberately still does not resolve the outcome - see its own comment above
 * for the grey-screen bug that caused. Preloading does not change that ordering.
 *
 * Note that test ad units (see [AdUnitIds] - `USE_TEST_ADS`) always fill instantly and never
 * fail, so neither hazard can be reproduced while testing against them. Both paths have to be
 * forced by hand (airplane mode is the easy one).
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun ContinueAdContent() {
    val ad by rememberRewardedAd(
        adUnitId = AdUnitIds.REWARDED_CONTINUE,
        onFailure = {
            if (ContinueAdTrigger.showRequested.value) ContinueAdTrigger.onAdClosed()
        },
    )
    if (ContinueAdTrigger.showRequested.value) {
        when (ad.state) {
            AdState.READY -> {
                ad.setListeners(
                    onFailure = { ContinueAdTrigger.onAdClosed() },
                    onDismissed = { ContinueAdTrigger.onAdClosed() },
                )
                ad.show { ContinueAdTrigger.markRewardEarned() }
            }
            // See hazard 2 above - without this the prompt hangs on an ad that will never load.
            AdState.FAILING -> ContinueAdTrigger.onAdClosed()
            // NONE/LOADING: the preload has not finished yet, which is the same wait as the old
            // behaviour and no worse. The handler is a MutableState, so reaching READY recomposes
            // this and shows the ad. SHOWING/SHOWN/DISMISSED: already in flight, leave it alone.
            else -> Unit
        }
    }
}
