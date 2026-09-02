package com.infiltrate.ads

// The desktop target exists only as the dev preview for the Compose menus - it has no AdMob and
// never will, since basic-ads publishes androidJvm and ios variants only (see build.gradle.kts on
// why it cannot live in commonMain). This actual exists purely so the expect in commonMain has a
// match on JVM and `run` can launch the menus.
//
// Google's own rewarded test unit, hardcoded rather than taken from AdUnitId.REWARDED_DEFAULT
// because basic-ads is not on this target's classpath. A real ad unit must never appear here: the
// desktop build is developer-run, so any impression it produced would be invalid traffic against a
// live unit - the exact risk .junie/guidelines.md warns about.
actual object AdUnitIds {
    actual val REWARDED_CONTINUE: String = "ca-app-pub-3940256099942544/5224354917"
}
