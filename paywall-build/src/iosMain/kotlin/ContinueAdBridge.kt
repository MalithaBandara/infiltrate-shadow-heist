package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.RewardedAd
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

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun ContinueAdContent() {
    if (ContinueAdTrigger.showRequested.value) {
        RewardedAd(
            adUnitId = AdUnitIds.REWARDED_CONTINUE,
            onRewardEarned = { ContinueAdTrigger.markRewardEarned() },
            onDismissed = { ContinueAdTrigger.cancelShow() },
            onFailure = { ContinueAdTrigger.cancelShow() },
        )
    }
}
