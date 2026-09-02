package com.infiltrate.ads

/**
 * Real AdMob ad unit IDs, per platform (Android and iOS ad units are always separate,
 * even for the same placement - see .junie/guidelines.md "AdMob (basic-ads) feasibility
 * spike"). Only for use in real, human-triggered ad placements - never from automated
 * CI/spike code, which must keep using basic-ads' own `AdUnitId.*_DEFAULT` test constants
 * to avoid generating invalid traffic (clicks/impressions with no real user) against a
 * real ad unit, which risks AdMob account suspension.
 */
expect object AdUnitIds {
    val REWARDED_CONTINUE: String
}
