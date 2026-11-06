package com.sample.demo.nav

// Same interface/logic as src/LevelExitBridge.kt + src@android/LevelExitBridge.android.kt, but
// without expect/actual: this module isn't a Kotlin Multiplatform project, so those two files
// can't be srcDir-included together (see android-shell/build.gradle.kts's sourceSets comment) -
// a single plain implementation is all this module needs. Mirrors ContinueAdBridge.kt here.
interface LevelExitBridge {
    fun requestReturnToMenu()
    fun requestTransitionAd()
    fun consumeTransitionAdFinished(): Boolean
}

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

fun getLevelExitBridge(): LevelExitBridge = AndroidLevelExitBridge()
