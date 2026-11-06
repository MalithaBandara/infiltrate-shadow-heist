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
    // Tuned from vendor guidance (Meta Instant Games, Unity, ironSource): natural breaks only, a
    // 60-90s floor for casual games with 1-3 min between ads as the usual target, and 3-8 per
    // session. These are starting points - tune against D1 retention and ARPDAU once live.
    const val MIN_LEVELS_COMPLETED = 1
    const val COOLDOWN_SECONDS = 120L
    const val MAX_PER_SESSION = 8

    private var lastShownAtEpochSeconds: Long = currentEpochSeconds()
    private var sessionShowCount: Int = 0

    fun canShow(totalLevelsCompleted: Int): Boolean {
        if (totalLevelsCompleted < MIN_LEVELS_COMPLETED) return false
        if (sessionShowCount >= MAX_PER_SESSION) return false
        return currentEpochSeconds() - lastShownAtEpochSeconds >= COOLDOWN_SECONDS
    }

    /**
     * Call when the ad is actually on screen, not when it is asked for. Counting at the request
     * spent the cooldown and a session slot on ads that never appeared (no fill, failed display),
     * which is the next [COOLDOWN_SECONDS] of opportunities lost for no impression.
     */
    fun recordShown() {
        lastShownAtEpochSeconds = currentEpochSeconds()
        sessionShowCount++
    }

    /**
     * How long to wait before loading again after [consecutiveFailures] failed loads in a row. The
     * first retry is quick because the usual cause is a request racing SDK start at launch; after
     * that it backs off, since an instant re-request just meets the same empty inventory.
     */
    fun retryDelayMillis(consecutiveFailures: Int): Long = when {
        consecutiveFailures <= 1 -> FIRST_RETRY_DELAY_MS
        else -> {
            val doublings = (consecutiveFailures - 2).coerceAtMost(MAX_RETRY_DOUBLINGS)
            (BACKOFF_BASE_DELAY_MS shl doublings).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
    }

    private const val FIRST_RETRY_DELAY_MS = 2_000L
    private const val BACKOFF_BASE_DELAY_MS = 30_000L
    private const val MAX_RETRY_DELAY_MS = 300_000L
    private const val MAX_RETRY_DOUBLINGS = 8
}
