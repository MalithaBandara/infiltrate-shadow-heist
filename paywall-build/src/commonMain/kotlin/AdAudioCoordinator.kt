package com.infiltrate.ads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Coordinates pausing and resuming background menu music while full-screen ads
 * (rewarded or interstitial) are active.
 */
object AdAudioCoordinator {
    private var activeAdCount = 0

    var isAdActive: Boolean by mutableStateOf(false)
        private set

    fun onAdStarted() {
        activeAdCount++
        isAdActive = true
    }

    fun onAdDismissed() {
        activeAdCount = (activeAdCount - 1).coerceAtLeast(0)
        isAdActive = activeAdCount > 0
    }
}
