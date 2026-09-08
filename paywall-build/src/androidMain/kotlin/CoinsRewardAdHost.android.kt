package com.infiltrate.ads

import androidx.compose.runtime.Composable
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.RewardedAd

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
actual fun CoinsRewardAdHost(
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit
) {
    RewardedAd(
        adUnitId = AdUnitIds.REWARDED_COINS,
        onRewardEarned = { onRewardEarned() },
        onDismissed = onDismissed,
        onFailure = { onFailure() },
    )
}
