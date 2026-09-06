package com.sample.demo.nav

// No-op stub - no menu shell to switch to on this platform. See LevelExitBridge.kt.
class JsLevelExitBridge : LevelExitBridge {
    override fun requestReturnToMenu() {}
}

actual fun getLevelExitBridge(): LevelExitBridge = JsLevelExitBridge()
