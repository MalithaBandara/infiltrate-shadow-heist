package com.sample.demo.nav

import kotlin.native.ObjCName

/**
 * Real (non-spike) bridge exposed to Swift for "leaving gameplay back to the menu" - same shape
 * as ContinueAdBridge.ios.kt's GameContinueAdBridge. GameplayScene.kt's QUIT/RETURN TO MENU/
 * MAIN MENU/ALL CLEAR buttons all reach this via [getLevelExitBridge]; Swift's poll loop consumes
 * [consumeReturnToMenuRequest] while KorGE gameplay is visible and switches the shell back to the
 * Compose MainMenu scene, mirroring AndroidLevelExitBridgeState.onReturnToMenuRequested in
 * MainActivity.kt.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "GameLevelExitBridge", exact = true)
object GameLevelExitBridge {
    var returnToMenuRequested: Boolean = false
        private set

    fun requestReturnToMenu() {
        returnToMenuRequested = true
    }

    fun consumeReturnToMenuRequest(): Boolean {
        if (!returnToMenuRequested) return false
        returnToMenuRequested = false
        return true
    }

    // NEXT MISSION / RETRY / RESTART: Swift consumes the request in the same poll loop, shows the interstitial if
    // one is due, then always calls finishTransitionAd() - GameplayScene waits on that.
    private var transitionAdRequested: Boolean = false
    private var transitionAdFinished: Boolean = false

    fun requestTransitionAd() {
        transitionAdFinished = false
        transitionAdRequested = true
    }

    fun consumeTransitionAdRequest(): Boolean {
        if (!transitionAdRequested) return false
        transitionAdRequested = false
        return true
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

private class IosLevelExitBridge : LevelExitBridge {
    override fun requestReturnToMenu() = GameLevelExitBridge.requestReturnToMenu()
    override fun requestTransitionAd() = GameLevelExitBridge.requestTransitionAd()
    override fun consumeTransitionAdFinished() = GameLevelExitBridge.consumeTransitionAdFinished()
}

actual fun getLevelExitBridge(): LevelExitBridge = IosLevelExitBridge()
