package com.infiltrate.ads

/**
 * Gates the level-exit interstitial (LevelExitBridge). Unlike CoinsAdLimiter's persisted daily
 * count, all state here is in-memory only and resets on a cold app launch - a "session" for this
 * placement really does mean "this process's lifetime", not a calendar day.
 *
 * [lastShownAtEpochSeconds] is seeded at first access (object init) rather than left at 0, so the
 * very first [canShow] check after launch measures against launch time, not epoch zero - this is
 * what gives "no interstitial in the first [COOLDOWN_SECONDS] of a session" for free, without a
 * separate launch-grace constant (see .junie/guidelines.md).
 */
object InterstitialAdLimiter {
    const val MIN_LEVELS_COMPLETED = 2
    const val COOLDOWN_SECONDS = 180L
    const val MAX_PER_SESSION = 5

    private var lastShownAtEpochSeconds: Long = currentEpochSeconds()
    private var sessionShowCount: Int = 0

    fun canShow(totalLevelsCompleted: Int): Boolean {
        if (totalLevelsCompleted < MIN_LEVELS_COMPLETED) return false
        if (sessionShowCount >= MAX_PER_SESSION) return false
        return currentEpochSeconds() - lastShownAtEpochSeconds >= COOLDOWN_SECONDS
    }

    fun recordShown() {
        lastShownAtEpochSeconds = currentEpochSeconds()
        sessionShowCount++
    }
}
