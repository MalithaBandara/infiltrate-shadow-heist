package com.infiltrate.ads

import androidx.compose.runtime.Composable

/**
 * Composes the real "watch ad for a random gadget" rewarded ad while shown. Same expect/actual
 * shape as [CoinsRewardAdHost] and for the same reason: basic-ads only publishes Android/iOS
 * variants, so it can never be referenced from commonMain directly. StoreScreen.kt composes this
 * only while a watch-ad-for-gadget request is in flight; which gadget is granted is decided by the
 * caller (a uniform-random pick among the five real gadgets), not by this host.
 */
@Composable
expect fun GadgetRewardAdHost(
    onRewardEarned: () -> Unit,
    onDismissed: () -> Unit,
    onFailure: () -> Unit
)
