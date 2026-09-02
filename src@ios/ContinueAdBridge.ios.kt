package com.sample.demo.ads

import kotlin.native.ObjCName

// Real (non-spike) bridge exposed to Swift. Swift's poll loop consumes
// consumeContinueAdRequest() while KorGE gameplay is visible, and when true, switches the shell
// to the Compose scene and shows a real rewarded ad (PaywallModule.framework's
// ContinueAdTrigger). Once the reward is earned, Swift calls grantContinue(), which
// GameplayScene's own update loop picks up via consumeContinueGranted() to restart the level -
// see .junie/guidelines.md "AdMob (basic-ads) feasibility spike".
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "GameContinueAdBridge", exact = true)
object GameContinueAdBridge {
    var continueAdRequested: Boolean = false
        private set
    var continueGranted: Boolean = false
        private set

    fun requestContinueAd() {
        continueAdRequested = true
    }

    fun consumeContinueAdRequest(): Boolean {
        if (!continueAdRequested) return false
        continueAdRequested = false
        return true
    }

    fun grantContinue() {
        continueGranted = true
    }

    fun consumeContinueGranted(): Boolean {
        if (!continueGranted) return false
        continueGranted = false
        return true
    }
}

private class IosContinueAdBridge : ContinueAdBridge {
    override fun requestContinueAd() = GameContinueAdBridge.requestContinueAd()
    override fun consumeContinueGranted(): Boolean = GameContinueAdBridge.consumeContinueGranted()
}

actual fun getContinueAdBridge(): ContinueAdBridge = IosContinueAdBridge()
