package com.sample.demo.ads

// Desktop JVM no-op stub - no ad SDK on this platform. See ContinueAdBridge.android.kt.
class JvmContinueAdBridge : ContinueAdBridge {
    override fun requestContinueAd() {
        println("[JvmContinueAdBridge] Desktop JVM no-op stub called")
    }

    override fun consumeContinueGranted(): Boolean = false
}

actual fun getContinueAdBridge(): ContinueAdBridge = JvmContinueAdBridge()
