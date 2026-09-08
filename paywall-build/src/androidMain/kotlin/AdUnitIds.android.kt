package com.infiltrate.ads

// Internal/Closed testing must use Google's official test ad unit IDs, never real ones - testers
// on those Play Console tracks are people the developer personally invited, which AdMob's
// invalid-traffic policy treats the same as clicking your own ads (see .junie/guidelines.md).
// Real ad units are fine again for an Open testing track (genuine public opt-in users) or a
// production release.
//
// One flag for all three placements, not a per-value swap: REWARDED_CONTINUE previously had its
// own ad-hoc test-ID swap (see git history, 2026-09-03) that sat in place unnoticed for a while -
// a single, impossible-to-miss switch at the top of the file is harder to forget than six separate
// edits across two files. Flip to false before any Open testing/production build; flip back to
// true if further Internal/Closed testing is needed afterward.
private const val USE_TEST_ADS = true

actual object AdUnitIds {
    actual val REWARDED_CONTINUE: String =
        if (USE_TEST_ADS) "ca-app-pub-3940256099942544/5224354917"
        else "ca-app-pub-7912148730700666/8683118378"

    // Real Android rewarded ad unit, created for the Store's "watch ad for coins" placement
    // (its own ad unit, not shared with REWARDED_CONTINUE - see .junie/guidelines.md on why
    // AdMob placements should be split for reporting/frequency-capping granularity).
    actual val REWARDED_COINS: String =
        if (USE_TEST_ADS) "ca-app-pub-3940256099942544/5224354917"
        else "ca-app-pub-7912148730700666/8440619376"

    // Real Android interstitial ad unit, created for the level-exit placement (LevelExitBridge).
    actual val INTERSTITIAL_LEVEL_EXIT: String =
        if (USE_TEST_ADS) "ca-app-pub-3940256099942544/1033173712"
        else "ca-app-pub-7912148730700666/7390779081"
}
