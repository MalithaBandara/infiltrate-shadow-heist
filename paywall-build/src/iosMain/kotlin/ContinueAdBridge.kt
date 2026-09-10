package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberRewardedAd
import kotlin.native.ObjCName

/**
 * Real (non-spike) "watch ad to continue" trigger. Swift calls [requestShow] once it has
 * switched the shell's rootViewController to this Compose scene (a rewarded ad can only present
 * reliably from the currently-visible view controller - see .junie/guidelines.md "AdMob
 * (basic-ads) feasibility spike" for why the earlier banner spike deliberately avoided a second,
 * detached ComposeUIViewController). [ContinueAdContent] observes [showRequested] and, while
 * true, composes basic-ads' RewardedAd trigger, which loads + shows the ad itself.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "ContinueAdTrigger", exact = true)
object ContinueAdTrigger {
    internal val showRequested: MutableState<Boolean> = mutableStateOf(false)

    // Set the moment a definitive outcome is known (reward earned, or the ad was dismissed/
    // failed without one) - Swift polls consumeOutcomeFinished() rather than waiting out a fixed
    // timeout, so declining the ad hands control back to KorGE immediately instead of stalling.
    private var outcomeFinished: Boolean = false

    var rewardEarned: Boolean = false
        private set

    fun requestShow() {
        outcomeFinished = false
        rewardEarned = false
        showRequested.value = true
    }

    fun markRewardEarned() {
        rewardEarned = true
        outcomeFinished = true
        showRequested.value = false
    }

    // Ad failed to load/show, or the player closed it before earning the reward.
    fun cancelShow() {
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
 * Preloads the watch-ad-to-continue rewarded ad and shows it on request. Mirror of the Android
 * [ContinueAdContent]; see that file for the full reasoning, which applies identically here.
 *
 * In short: the [rememberRewardedAd] call sits OUTSIDE the [ContinueAdTrigger.showRequested] gate
 * so the fetch starts when this content first composes rather than after the player has already
 * died and asked to continue. Two hazards come with that, neither of which exists in the
 * load-then-show shape it replaces:
 *
 * 1. A preload that fails while nothing is pending must not call [ContinueAdTrigger.cancelShow] -
 *    Swift polls `consumeOutcomeFinished()`, so a phantom outcome would hand control back to
 *    KorGE for an offer the player was never shown. Hence the `showRequested` guard below.
 * 2. `rememberRewardedAd` only re-loads from `NONE` or `DISMISSED`, never from `FAILING`, so an
 *    early preload failure would otherwise leave the handler dead for the rest of the process and
 *    hang the continue prompt. The `FAILING` branch resolves it as a load failure did before.
 *
 * The one iOS-specific difference is unchanged by this: [markRewardEarned] here resolves the
 * outcome and drops `showRequested` immediately, where Android deliberately defers that to
 * `onAdClosed()` (see the Android file's note on the grey-screen reload bug). The handler now
 * lives outside the gate, so the ad still presents correctly after that flag flips.
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun ContinueAdContent() {
    val ad by rememberRewardedAd(
        adUnitId = AdUnitIds.REWARDED_CONTINUE,
        onFailure = {
            if (ContinueAdTrigger.showRequested.value) ContinueAdTrigger.cancelShow()
        },
    )
    if (ContinueAdTrigger.showRequested.value) {
        when (ad.state) {
            AdState.READY -> {
                ad.setListeners(
                    onFailure = { ContinueAdTrigger.cancelShow() },
                    onDismissed = { ContinueAdTrigger.cancelShow() },
                )
                ad.show { ContinueAdTrigger.markRewardEarned() }
            }
            AdState.FAILING -> ContinueAdTrigger.cancelShow()
            else -> Unit
        }
    }
}
