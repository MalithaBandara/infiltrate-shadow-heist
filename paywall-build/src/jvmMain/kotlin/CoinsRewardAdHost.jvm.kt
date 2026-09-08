package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// The desktop target is a dev-only Compose menu preview - no AdMob SDK is wired here at all (see
// AdUnitIds.jvm.kt). Rather than leaving StoreScreen.kt's showCoinsRewardAd stuck true forever
// with nothing ever calling back, report failure immediately so the screen falls back to its
// normal "ad not available" messaging, same as a real failed ad load would.
@Composable
actual fun CoinsRewardAdHost(
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit
) {
    LaunchedEffect(Unit) {
        onFailure()
    }
}
