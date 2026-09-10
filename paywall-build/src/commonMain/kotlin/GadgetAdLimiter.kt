package com.infiltrate.ads

/**
 * Caps the Store's "watch ad for a random gadget" placement (StoreScreen.kt) at
 * [MAX_WATCHES_PER_DAY] per day. Same shape as [CoinsAdLimiter] (own storage keys, own daily
 * bucket) rather than a shared base class - this project already keeps each ad placement's limiter
 * as its own small, independent class (see InterstitialAdLimiter), so a third one follows the same
 * precedent instead of introducing a generic abstraction for two callers.
 *
 * Capped lower than [CoinsAdLimiter.MAX_WATCHES_PER_DAY] (5): a random gadget is worth more on
 * average than the flat coin payout (the five gadgets cost 150-750 coins to buy outright, averaging
 * 400), so a matching daily cap would make free ad-gadgets a bigger economy lever than intended.
 * Tune this constant in place if that balance ever needs to change.
 */
class GadgetAdLimiter(
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
        const val MAX_WATCHES_PER_DAY = 3
        private const val SECONDS_PER_DAY = 86_400L
        private const val KEY_DAY_BUCKET = "user_gadget_ad_day_bucket"
        private const val KEY_WATCH_COUNT = "user_gadget_ad_watch_count"
    }
}
