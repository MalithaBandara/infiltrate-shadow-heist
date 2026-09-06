package com.sample.demo.nav

// No-op stub - ios-shell has no equivalent poll loop wired up for this yet (only the "watch ad to
// continue" flow does, see ContinueAdBridge.ios.kt). See LevelExitBridge.kt.
class IosLevelExitBridge : LevelExitBridge {
    override fun requestReturnToMenu() {}
}

actual fun getLevelExitBridge(): LevelExitBridge = IosLevelExitBridge()
