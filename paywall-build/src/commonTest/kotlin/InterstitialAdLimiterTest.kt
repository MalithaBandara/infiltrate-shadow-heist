package com.infiltrate.test

import com.infiltrate.ads.InterstitialAdLimiter
import kotlin.test.Test
import kotlin.test.assertEquals

class InterstitialAdLimiterTest {
    @Test
    fun firstRetryIsQuickToRecoverFromTheStartupRace() {
        assertEquals(2_000L, InterstitialAdLimiter.retryDelayMillis(1))
    }

    @Test
    fun laterRetriesBackOffAndStopGrowingAtFiveMinutes() {
        assertEquals(30_000L, InterstitialAdLimiter.retryDelayMillis(2))
        assertEquals(60_000L, InterstitialAdLimiter.retryDelayMillis(3))
        assertEquals(240_000L, InterstitialAdLimiter.retryDelayMillis(5))
        assertEquals(300_000L, InterstitialAdLimiter.retryDelayMillis(6))
        assertEquals(300_000L, InterstitialAdLimiter.retryDelayMillis(1_000))
    }
}
