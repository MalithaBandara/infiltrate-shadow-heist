package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.RewardedAd

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
actual fun CoinsRewardAdHost(
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit
) {
    // Consent flow not settled yet (see AdPrivacy): no ad may be requested, so this reads as the
    // same "ad not ready" a failed load already shows.
    if (!AdPrivacy.canRequestAds) {
        LaunchedEffect(Unit) { onFailure() }
        return
    }
    RewardedAd(
        adUnitId = AdUnitIds.REWARDED_COINS,
        onRewardEarned = { onRewardEarned() },
        onDismissed = onDismissed,
        onFailure = { onFailure() },
    )
}
