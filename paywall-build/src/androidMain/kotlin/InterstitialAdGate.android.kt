package com.infiltrate.ads

actual fun requestMenuInterstitial(totalLevelsCompleted: Int, isPremium: Boolean): Boolean =
    InterstitialAdTrigger.maybeRequestShow(totalLevelsCompleted, isPremium)

actual fun isMenuInterstitialActive(): Boolean = InterstitialAdTrigger.isAdFlowActive

actual fun cancelMenuInterstitialIfNotDisplayed() = InterstitialAdTrigger.cancelIfNotDisplayed()
