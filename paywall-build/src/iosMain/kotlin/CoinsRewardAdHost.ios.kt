package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.RewardedAd

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
    RewardedAd(
        adUnitId = AdUnitIds.REWARDED_COINS,
        onRewardEarned = { onRewardEarned() },
        onDismissed = onDismissed,
        onFailure = { onFailure() },
    )
}
