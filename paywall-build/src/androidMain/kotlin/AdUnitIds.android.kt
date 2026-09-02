package com.infiltrate.ads

// TEMPORARY diagnostic swap (2026-09-03): real ad unit is ca-app-pub-7912148730700666/8683118378
// but the continue flow was failing fast on-device (menu flashes, then straight back) even with
// an approved AdMob account - swapped to Google's guaranteed-to-serve test rewarded unit to
// isolate whether that's a real code bug or the account just not serving yet. Swap back to the
// real ID above once confirmed either way - never ship this test ID.
actual object AdUnitIds {
    actual val REWARDED_CONTINUE: String = "ca-app-pub-3940256099942544/5224354917"
}
