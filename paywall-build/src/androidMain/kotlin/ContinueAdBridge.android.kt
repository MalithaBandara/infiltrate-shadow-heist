package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.RewardedAd

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

    fun markRewardEarned() {
        rewardEarned = true
        outcomeFinished = true
        showRequested.value = false
    }

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
