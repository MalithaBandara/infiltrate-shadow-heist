package com.sample.demo.nav

// Desktop JVM no-op stub - no menu shell to switch to on this platform. See LevelExitBridge.kt.
class JvmLevelExitBridge : LevelExitBridge {
    override fun requestReturnToMenu() {
        println("[JvmLevelExitBridge] Desktop JVM no-op stub called")
    }
}

actual fun getLevelExitBridge(): LevelExitBridge = JvmLevelExitBridge()
