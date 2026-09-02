package com.sample.demo.ads

// Same interface/logic as src/ContinueAdBridge.kt + src@android/ContinueAdBridge.android.kt,
// but without expect/actual: this module isn't a Kotlin Multiplatform project, so those two
// files can't be srcDir-included together (see android-shell/build.gradle.kts's sourceSets
// comment) - a single plain implementation is all this module needs.
interface ContinueAdBridge {
    fun requestContinueAd()
    fun consumeContinueGranted(): Boolean
}

object AndroidContinueAdBridgeState {
    var onContinueAdRequested: (() -> Unit)? = null

    @Volatile
    private var continueGranted: Boolean = false

    fun requestContinueAd() {
        onContinueAdRequested?.invoke()
    }

    fun grantContinue() {
        continueGranted = true
    }

    fun consumeContinueGranted(): Boolean {
        if (!continueGranted) return false
        continueGranted = false
        return true
    }
}

private class AndroidContinueAdBridge : ContinueAdBridge {
    override fun requestContinueAd() = AndroidContinueAdBridgeState.requestContinueAd()
    override fun consumeContinueGranted(): Boolean = AndroidContinueAdBridgeState.consumeContinueGranted()
}

fun getContinueAdBridge(): ContinueAdBridge = AndroidContinueAdBridge()
