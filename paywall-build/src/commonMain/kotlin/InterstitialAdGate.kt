package com.infiltrate.ads

/**
 * What the shared Compose menu needs from each platform's InterstitialAdTrigger, which is a separate
 * object per platform (and absent on desktop). Same gate as every other placement: limiter, premium,
 * and only if an ad is already loaded.
 *
 * @return whether an ad was requested.
 */
expect fun requestMenuInterstitial(totalLevelsCompleted: Int, isPremium: Boolean): Boolean

/** True from a request until the ad has closed or failed. */
expect fun isMenuInterstitialActive(): Boolean

/** For a caller waiting on the ad: never strands the player if it did not appear. */
expect fun cancelMenuInterstitialIfNotDisplayed()
