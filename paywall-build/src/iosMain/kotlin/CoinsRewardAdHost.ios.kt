package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
actual fun CoinsRewardAdHost(
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit
) {
    DisposableEffect(Unit) {
        AdAudioCoordinator.onAdStarted()
        onDispose { AdAudioCoordinator.onAdDismissed() }
    }
    // Not basic-ads' plain RewardedAd(): one failed load used to be the final answer, which is
    // what produced "Ad not ready. Please try again later." on the first tap. See
    // RetryingRewardedAd.kt for why a retry needs a fresh handler rather than another load call.
    RetryingRewardedAd(
        adUnitId = AdUnitIds.REWARDED_COINS,
        onRewardEarned = onRewardEarned,
        onDismissed = onDismissed,
        onFailure = onFailure,
    )
}
