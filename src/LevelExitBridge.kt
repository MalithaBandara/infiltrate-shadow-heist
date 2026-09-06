package com.sample.demo.nav

/**
 * Bridge for leaving gameplay back to the menu. GameplayScene.kt calls [requestReturnToMenu] from
 * QUIT (pause menu), RETURN TO MENU (game-over overlay), and MAIN MENU / ALL CLEAR (level-complete
 * overlay) - same "fire and let the host react" shape as ContinueAdBridge.kt, but one-way: unlike
 * the ad flow, GameplayScene has nothing further to wait on afterward, since the host reacting
 * (showing its menu instead of the KorGE view) is the entire effect.
 *
 * These call sites previously wrote `views.storage["nav_target"] = "menu"` (or `"level_select"`)
 * with nothing anywhere ever reading that key back out - a no-op that looked like navigation but
 * wasn't, which is why QUIT/RETURN TO MENU only ever reloaded the current level in place instead
 * of actually leaving it.
 *
 * Real implementation only exists where a native shell can actually switch away from the KorGE
 * view - currently Android only (see src@android/LevelExitBridge.android.kt and
 * android-shell/.../LevelExitBridge.kt). Every other target gets a no-op stub, same convention as
 * PurchasesBridge/ContinueAdBridge.
 */
interface LevelExitBridge {
    fun requestReturnToMenu()
}

expect fun getLevelExitBridge(): LevelExitBridge
