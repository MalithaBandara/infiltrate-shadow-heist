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

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun ContinueAdContent() {
    if (ContinueAdTrigger.showRequested.value) {
        RewardedAd(
            adUnitId = AdUnitIds.REWARDED_CONTINUE,
            onRewardEarned = { ContinueAdTrigger.markRewardEarned() },
            onDismissed = { ContinueAdTrigger.onAdClosed() },
            onFailure = { ContinueAdTrigger.onAdClosed() },
        )
    }
}
