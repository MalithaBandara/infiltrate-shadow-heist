package com.infiltrate.ads

// Real iOS rewarded ad unit, created under the "Continue Game" ad unit in AdMob.
actual object AdUnitIds {
    actual val REWARDED_CONTINUE: String = "ca-app-pub-7912148730700666/9506964083"

    // Real iOS rewarded ad unit, created for the Store's "watch ad for coins" placement (its
    // own ad unit, named "Coins Reward" in AdMob - not shared with REWARDED_CONTINUE).
    actual val REWARDED_COINS: String = "ca-app-pub-7912148730700666/4233781051"

    // Real iOS interstitial ad unit, created for the level-exit placement (LevelExitBridge).
    // Plumbing only for now - ios-shell has no Swift poll loop wired up to actually trigger this
    // yet (LevelExitBridge.ios.kt is still a no-op stub), same status as the rest of that bridge.
    actual val INTERSTITIAL_LEVEL_EXIT: String = "ca-app-pub-7912148730700666/5874999932"
}
