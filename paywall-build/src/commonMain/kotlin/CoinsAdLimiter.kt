package com.infiltrate.ads

/**
 * Caps the free "watch ad for coins" placement (StoreScreen.kt) at [MAX_WATCHES_PER_DAY] per day,
 * backed by the same getRaw/setRaw storage bridge GameProfileStorage uses. This is the economy
 * lever - AdMob's own dashboard frequency cap on the REWARDED_COINS ad unit is a looser backstop
 * against a modified client spamming ad requests, not the thing meant to enforce the real limit
 * (see .junie/guidelines.md). Day is a plain UTC epoch-day bucket, not local calendar midnight -
 * close enough for a "free coins" cooldown, and avoids pulling in a timezone-aware date library.
 */
class CoinsAdLimiter(
    private val getRaw: (String) -> String?,
    private val setRaw: (String, String) -> Unit
) {
    private fun currentDayBucket(): Long = currentEpochSeconds() / SECONDS_PER_DAY

    private fun watchesUsedToday(): Int {
        val storedBucket = getRaw(KEY_DAY_BUCKET)?.toLongOrNull()
        if (storedBucket != currentDayBucket()) return 0
        return getRaw(KEY_WATCH_COUNT)?.toIntOrNull() ?: 0
    }

    fun watchesRemainingToday(): Int = (MAX_WATCHES_PER_DAY - watchesUsedToday()).coerceAtLeast(0)

    fun canWatch(): Boolean = watchesRemainingToday() > 0

    /** Seconds until the daily count resets, for a live "available in HH:MM:SS" countdown. */
    fun secondsUntilReset(): Long {
        val nextBucketStartSeconds = (currentDayBucket() + 1) * SECONDS_PER_DAY
        return (nextBucketStartSeconds - currentEpochSeconds()).coerceAtLeast(0L)
    }

    fun recordWatch() {
        val newCount = watchesUsedToday() + 1
        setRaw(KEY_DAY_BUCKET, currentDayBucket().toString())
        setRaw(KEY_WATCH_COUNT, newCount.toString())
    }

    companion object {
        const val MAX_WATCHES_PER_DAY = 5
        private const val SECONDS_PER_DAY = 86_400L
        private const val KEY_DAY_BUCKET = "user_coin_ad_day_bucket"
        private const val KEY_WATCH_COUNT = "user_coin_ad_watch_count"
    }
}
