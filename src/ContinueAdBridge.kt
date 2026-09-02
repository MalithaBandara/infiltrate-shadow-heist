package com.sample.demo.ads

/**
 * Bridge for the "watch ad to continue" flow when the player dies mid-level. GameplayScene
 * calls [requestContinueAd] when the player taps CONTINUE on the game-over overlay, then polls
 * [consumeContinueGranted] every frame; once it returns true (the player actually watched the
 * rewarded ad), GameplayScene restarts the level itself - the same code path as the existing
 * RETRY button. See .junie/guidelines.md "AdMob (basic-ads) feasibility spike".
 *
 * Real implementation only exists on iOS (see src@ios/ContinueAdBridge.ios.kt), which is the
 * only platform with a native shell that can actually show the ad. Every other target gets a
 * deliberate no-op stub, same as PurchasesBridge's iOS stub - consumeContinueGranted() must
 * never return true unless a real ad was actually watched.
 */
interface ContinueAdBridge {
    fun requestContinueAd()
    fun consumeContinueGranted(): Boolean
}

expect fun getContinueAdBridge(): ContinueAdBridge
