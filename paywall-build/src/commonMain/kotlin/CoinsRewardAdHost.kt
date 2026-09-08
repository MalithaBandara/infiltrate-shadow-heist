package com.infiltrate.ads

import androidx.compose.runtime.Composable

/**
 * Composes the real "watch ad for coins" rewarded ad while shown. `expect`/`actual`, not a plain
 * commonMain composable, because basic-ads (the RewardedAd composable/AdMob wrapper) only
 * publishes Android and iOS variants - no jvm() desktop artifact - so it can never be referenced
 * from commonMain directly (same constraint AdUnitIds/ContinueAdBridge already work around; see
 * .junie/guidelines.md "AdMob (basic-ads) feasibility spike"). StoreScreen.kt composes this only
 * while a watch-ad-for-coins request is in flight.
 */
@Composable
expect fun CoinsRewardAdHost(
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit
)
