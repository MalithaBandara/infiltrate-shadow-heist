package com.infiltrate.ads

// Desktop dev preview only - no AdMob SDK here (see AdUnitIds.jvm.kt), so never an ad to wait for.
actual fun requestMenuInterstitial(totalLevelsCompleted: Int, isPremium: Boolean): Boolean = false

actual fun isMenuInterstitialActive(): Boolean = false

actual fun cancelMenuInterstitialIfNotDisplayed() = Unit
