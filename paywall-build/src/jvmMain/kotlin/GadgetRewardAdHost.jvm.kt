package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// Desktop dev preview only - no AdMob SDK here (see AdUnitIds.jvm.kt). Report failure immediately
// so the screen falls back to its normal "ad not available" messaging, same as CoinsRewardAdHost.jvm.kt.
@Composable
actual fun GadgetRewardAdHost(
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit
) {
    LaunchedEffect(Unit) {
        onFailure()
    }
}
