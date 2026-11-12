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
 * view - Android (see src@android/LevelExitBridge.android.kt and android-shell/.../
 * LevelExitBridge.kt) and iOS (see src@ios/LevelExitBridge.ios.kt's GameLevelExitBridge). Every
 * other target gets a no-op stub, same convention as ContinueAdBridge.
 */
interface LevelExitBridge {
    fun requestReturnToMenu()

    /**
     * NEXT MISSION, RETRY, RESTART: lets the host show an interstitial before play starts again.
     * GameplayScene then polls [consumeTransitionAdFinished] each frame and carries on once it
     * returns true, so nothing loads underneath the ad. The host decides whether an ad is due, and reports
     * finished straight away when it is not.
     *
     * The defaults are the no-host behaviour (desktop and web): nothing to show, carry straight on.
     * A host that overrides [requestTransitionAd] must always end in finished, or the level never
     * starts.
     */
    fun requestTransitionAd() {}
    fun consumeTransitionAdFinished(): Boolean = true
}

expect fun getLevelExitBridge(): LevelExitBridge
