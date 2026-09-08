package com.infiltrate.ads

// See AdUnitIds.android.kt for the full rationale - same flag, same reasoning, kept in sync
// manually since these are two separate actuals: Internal/Closed testers count as
// "developer-associated" traffic under AdMob policy, so use test ad unit IDs for both those Play
// Console tracks (this file's IDs are for iOS/App Store review + TestFlight, which carries the
// same policy risk). Flip to false only for Open testing or production.
private const val USE_TEST_ADS = true

actual object AdUnitIds {
    // Real iOS rewarded ad unit, created under the "Continue Game" ad unit in AdMob.
    actual val REWARDED_CONTINUE: String =
        if (USE_TEST_ADS) "ca-app-pub-3940256099942544/1712485313"
        else "ca-app-pub-7912148730700666/9506964083"

    // Real iOS rewarded ad unit, created for the Store's "watch ad for coins" placement (its
    // own ad unit, named "Coins Reward" in AdMob - not shared with REWARDED_CONTINUE).
    actual val REWARDED_COINS: String =
        if (USE_TEST_ADS) "ca-app-pub-3940256099942544/1712485313"
        else "ca-app-pub-7912148730700666/4233781051"

    // Real iOS interstitial ad unit, created for the level-exit placement (LevelExitBridge).
    // Plumbing only for now - ios-shell has no Swift poll loop wired up to actually trigger this
    // yet (LevelExitBridge.ios.kt is still a no-op stub), same status as the rest of that bridge.
    actual val INTERSTITIAL_LEVEL_EXIT: String =
        if (USE_TEST_ADS) "ca-app-pub-3940256099942544/4411468910"
        else "ca-app-pub-7912148730700666/5874999932"
}
