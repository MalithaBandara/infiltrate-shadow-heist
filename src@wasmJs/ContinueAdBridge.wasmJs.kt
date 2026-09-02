package com.sample.demo.ads

// No-op stub - no ad SDK on this platform. See ContinueAdBridge.android.kt.
class WasmJsContinueAdBridge : ContinueAdBridge {
    override fun requestContinueAd() {}
    override fun consumeContinueGranted(): Boolean = false
}

actual fun getContinueAdBridge(): ContinueAdBridge = WasmJsContinueAdBridge()
