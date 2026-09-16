package com.sample.demo.ads

// Desktop JVM bridge for local dev/testing - simulates rewarded ad completion immediately
// so the continue flow can be tested on desktop without a mobile ad SDK.
class JvmContinueAdBridge : ContinueAdBridge {
    private var continueGranted: Boolean = false

    override fun requestContinueAd() {
        println("[JvmContinueAdBridge] Simulated rewarded ad completed; granting continue")
        continueGranted = true
    }

    override fun consumeContinueGranted(): Boolean {
        if (!continueGranted) return false
        continueGranted = false
        return true
    }
}

actual fun getContinueAdBridge(): ContinueAdBridge = JvmContinueAdBridge()
