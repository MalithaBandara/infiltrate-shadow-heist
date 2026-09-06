package com.sample.demo.nav

// Same interface/logic as src/LevelExitBridge.kt + src@android/LevelExitBridge.android.kt, but
// without expect/actual: this module isn't a Kotlin Multiplatform project, so those two files
// can't be srcDir-included together (see android-shell/build.gradle.kts's sourceSets comment) -
// a single plain implementation is all this module needs. Mirrors ContinueAdBridge.kt here.
interface LevelExitBridge {
    fun requestReturnToMenu()
}

object AndroidLevelExitBridgeState {
    var onReturnToMenuRequested: (() -> Unit)? = null

    fun requestReturnToMenu() {
        onReturnToMenuRequested?.invoke()
    }
}

private class AndroidLevelExitBridge : LevelExitBridge {
    override fun requestReturnToMenu() = AndroidLevelExitBridgeState.requestReturnToMenu()
}

fun getLevelExitBridge(): LevelExitBridge = AndroidLevelExitBridge()
