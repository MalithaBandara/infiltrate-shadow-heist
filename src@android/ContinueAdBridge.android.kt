package com.sample.demo.ads

// No native shell hosts real ads on Android yet (see .junie/guidelines.md) - deliberate no-op,
// not an unfinished real integration: consumeContinueGranted() must return false, never true,
// so the player is never granted a continue nothing was actually watched for.
class AndroidContinueAdBridge : ContinueAdBridge {
    override fun requestContinueAd() {
    }

    override fun consumeContinueGranted(): Boolean = false
}

actual fun getContinueAdBridge(): ContinueAdBridge = AndroidContinueAdBridge()
