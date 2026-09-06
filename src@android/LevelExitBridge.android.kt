package com.sample.demo.nav

// Real Android bridge, same plain-shared-object shape as AndroidContinueAdBridgeState (both
// :game and android-shell's MainActivity run in the same JVM/APK, so no Swift-style poll loop is
// needed here). android-shell/MainActivity sets [onReturnToMenuRequested] at startup.
object AndroidLevelExitBridgeState {
    var onReturnToMenuRequested: (() -> Unit)? = null

    fun requestReturnToMenu() {
        onReturnToMenuRequested?.invoke()
    }
}

private class AndroidLevelExitBridge : LevelExitBridge {
    override fun requestReturnToMenu() = AndroidLevelExitBridgeState.requestReturnToMenu()
}

actual fun getLevelExitBridge(): LevelExitBridge = AndroidLevelExitBridge()
