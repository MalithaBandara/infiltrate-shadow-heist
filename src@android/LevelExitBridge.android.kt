package com.sample.demo.nav

// Real Android bridge, same plain-shared-object shape as AndroidContinueAdBridgeState (both
// :game and android-shell's MainActivity run in the same JVM/APK, so no Swift-style poll loop is
// needed here). android-shell/MainActivity sets [onReturnToMenuRequested] at startup.
object AndroidLevelExitBridgeState {
    var onReturnToMenuRequested: (() -> Unit)? = null
    var onTransitionAdRequested: (() -> Unit)? = null

    // Written on the UI thread, read from KorGE's GL thread.
    @Volatile
    private var transitionAdFinished: Boolean = false

    fun requestReturnToMenu() {
        onReturnToMenuRequested?.invoke()
    }

    fun requestTransitionAd() {
        transitionAdFinished = false
        val host = onTransitionAdRequested
        if (host != null) host() else finishTransitionAd()
    }

    fun finishTransitionAd() {
        transitionAdFinished = true
    }

    fun consumeTransitionAdFinished(): Boolean {
        if (!transitionAdFinished) return false
        transitionAdFinished = false
        return true
    }
}

private class AndroidLevelExitBridge : LevelExitBridge {
    override fun requestReturnToMenu() = AndroidLevelExitBridgeState.requestReturnToMenu()
    override fun requestTransitionAd() = AndroidLevelExitBridgeState.requestTransitionAd()
    override fun consumeTransitionAdFinished() = AndroidLevelExitBridgeState.consumeTransitionAdFinished()
}

actual fun getLevelExitBridge(): LevelExitBridge = AndroidLevelExitBridge()
