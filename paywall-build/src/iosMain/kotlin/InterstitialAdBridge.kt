package com.infiltrate.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.InterstitialAdHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.native.ObjCName

/**
 * Level-exit interstitial trigger, matching ContinueAdTrigger's exported shape. Polled from
 * ios-shell/Sources/AppDelegate.swift's `startObservingLevelEnd()` alongside `GameLevelExitBridge`
 * (`src@ios/LevelExitBridge.ios.kt`) - when that bridge reports a real QUIT/RETURN TO
 * MENU/MAIN MENU/ALL CLEAR request, Swift calls [maybeRequestShow] before switching to the
 * Compose menu, following the exact @ObjCName(exact = true) export pattern ContinueAdTrigger
 * already proved out (without it, the linked ObjC symbol keeps the framework-name prefix and
 * Swift can't resolve it). Also serves NEXT MISSION / RETRY / RESTART, via
 * `GameLevelExitBridge.consumeTransitionAdRequest()`.
 *
 * **The handler lives here, not in the composition.** Those buttons have to know whether an ad is
 * ready before Swift leaves KorGE, and the Compose scene is off screen at that moment - the shell
 * removes it from the window during gameplay, so anything `remember`ed there cannot be relied on
 * to be alive or to reload (`rememberInterstitialAd` only reloads when it recomposes). Load and
 * retry here do not depend on the scene being up; showing still happens from
 * [InterstitialAdContent], once Swift has brought the scene back.
 */
@OptIn(
    kotlin.experimental.ExperimentalObjCName::class,
    kotlin.experimental.ExperimentalObjCRefinement::class,
    DependsOnGoogleMobileAds::class,
)
@ObjCName(name = "InterstitialAdTrigger", exact = true)
object InterstitialAdTrigger {
    internal val showRequested: MutableState<Boolean> = mutableStateOf(false)

    private val retryScope = MainScope()
    private var handler: InterstitialAdHandler? = null
    private var consecutiveFailures: Int = 0
    private var retryPending: Boolean = false

    // Whether the ad reached the screen, for [cancelIfNotDisplayed] and the limiter.
    private var displayed: Boolean = false

    /** True from the request until the ad has closed or failed. */
    val isAdFlowActive: Boolean get() = showRequested.value

    /**
     * Starts loading unless an ad is already loading, ready or on screen. Only called once consent
     * has settled (see [InterstitialAdContent]), and again whenever the previous ad is finished.
     * Each ad gets a new handler, since a finished or failed one is never reused.
     */
    internal fun preload() {
        if (retryPending) return
        when (handler?.state) {
            AdState.LOADING, AdState.READY, AdState.SHOWING, AdState.SHOWN -> return
            else -> Unit
        }
        val next = InterstitialAdHandler(null)
        handler = next
        next.load(
            adUnitId = AdUnitIds.INTERSTITIAL_LEVEL_EXIT,
            onLoad = { consecutiveFailures = 0 },
            onFailure = {
                consecutiveFailures += 1
                retryPending = true
                retryScope.launch {
                    delay(InterstitialAdLimiter.retryDelayMillis(consecutiveFailures))
                    retryPending = false
                    preload()
                }
            },
        )
    }

    /** Called from the composition, once this scene is back on screen. */
    internal fun presentLoadedAd() {
        val ad = handler
        if (ad == null || ad.state != AdState.READY) {
            onAdClosed()
            return
        }
        ad.setListeners(
            onFailure = {
                onAdClosed()
                preload()
            },
            onDismissed = {
                onAdClosed()
                preload()
            },
            onShown = {
                displayed = true
                InterstitialAdLimiter.recordShown()
            },
        )
        ad.show()
    }

    /** Safety net for Swift's wait on the ad: never strands the player if it did not appear. */
    fun cancelIfNotDisplayed() {
        if (!displayed) onAdClosed()
    }

    // The ad counts as up (AdAudioCoordinator: menu music silent) from the request, not from when
    // the ad composes: the request comes in the same tick the menu comes back, so the menu's
    // music would otherwise start under the ad and only stop a frame later.
    fun requestShow() {
        if (showRequested.value) return
        displayed = false
        showRequested.value = true
        AdAudioCoordinator.onAdStarted()
    }

    fun onAdClosed() {
        if (!showRequested.value) return
        showRequested.value = false
        AdAudioCoordinator.onAdDismissed()
    }

    /**
     * The gate for every interstitial, checking [InterstitialAdLimiter] itself rather than
     * duplicating its constants/state on the Swift side. An ad that has not finished loading is
     * skipped rather than waited for: it would otherwise pop up seconds later, over the menu or
     * over the next level. The limiter is only charged once the ad is really on screen.
     */
    fun canRequestShow(totalLevelsCompleted: Int, isPremium: Boolean): Boolean {
        if (isPremium || showRequested.value) return false
        if (!InterstitialAdLimiter.canShow(totalLevelsCompleted)) return false
        return handler?.state == AdState.READY
    }

    /** [canRequestShow] + [requestShow], for callers already on the Compose scene. */
    fun maybeRequestShow(totalLevelsCompleted: Int, isPremium: Boolean): Boolean {
        if (!canRequestShow(totalLevelsCompleted, isPremium)) return false
        requestShow()
        return true
    }
}

/**
 * Starts the preload once consent has settled, and shows the ad on request. The handler itself is
 * on [InterstitialAdTrigger] - see the note there for why, unlike Android, it cannot be remembered
 * in this composition.
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun InterstitialAdContent() {
    // Nothing may be requested until the consent flow settles (see AdPrivacy) - a request made in
    // the meantime is simply dropped, as an unfilled interstitial already is.
    if (!AdPrivacy.canRequestAds) {
        if (InterstitialAdTrigger.showRequested.value) {
            LaunchedEffect(Unit) { InterstitialAdTrigger.onAdClosed() }
        }
        return
    }
    LaunchedEffect(Unit) { InterstitialAdTrigger.preload() }
    val showRequested = InterstitialAdTrigger.showRequested.value
    LaunchedEffect(showRequested) {
        if (showRequested) InterstitialAdTrigger.presentLoadedAd()
    }
}
