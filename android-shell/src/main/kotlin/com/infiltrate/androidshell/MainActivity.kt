package com.infiltrate.androidshell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import com.infiltrate.ads.ContinueAdContent
import com.infiltrate.ads.ContinueAdTrigger
import com.infiltrate.ads.InterstitialAdContent
import com.infiltrate.ads.InterstitialAdLimiter
import com.infiltrate.ads.InterstitialAdTrigger
import com.infiltrate.billing.StoreBilling
import com.infiltrate.storage.PlatformStorage
import com.infiltrate.ui.NavigationRoot
import com.sample.demo.ads.AndroidContinueAdBridgeState
import com.sample.demo.audio.AndroidGameSfxOutputState
import com.sample.demo.nav.AndroidLevelExitBridgeState
import game.model.GameProfileStorage
import game.model.LevelData
import game.model.MapBackedGameProfileStorage
import game.scene.GameplayScene
import korlibs.image.color.Colors
import korlibs.io.async.launchImmediately
// KorgeConfig, not Korge: Korge.kt defines both a `data class Korge(...)` (what loadModule
// actually needs) AND several top-level `suspend fun Korge(...)` overloads sharing the exact
// same name, including one that also accepts a `main` parameter - so even a fully-named `Korge(
// main = {...})` call still resolves ambiguously to either. `KorgeConfig` is Korge.kt's own
// typealias for just the data class, with no such overload to collide with.
import korlibs.korge.KorgeConfig
import korlibs.korge.android.KorgeAndroidView
import korlibs.korge.scene.SceneContainer
import korlibs.korge.scene.sceneContainer
import korlibs.math.geom.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Duplicated from src/main.kt (the desktop/JVM entry point), not imported: main.kt has no
// package declaration, and Kotlin cannot import unnamed-package symbols from a file that does
// have one. Keep these two in sync with main.kt if they ever change.
private val windowSize = Size(1560, 720)
private val virtualSize = Size(480.0 * (windowSize.width / windowSize.height), 480.0)

/**
 * Real Android host - the Android equivalent of ios-shell/Sources/AppDelegate.swift. Single
 * Activity, Compose (paywall-build's NavigationRoot) owns the menu, an embedded KorgeAndroidView
 * owns gameplay; both stay alive the whole time - matching the "Android Warm Engine Symmetry"
 * candidate architecture in .junie/guidelines.md and the already-proven-on-iOS "never destroy the
 * warm engine" pattern from the switch-spike.
 *
 * RESOLVED (was UNVERIFIED): an earlier version toggled the KorgeAndroidView's own
 * `View.visibility` between VISIBLE and GONE to show/hide it behind the Compose menu. Confirmed
 * on a real device this does NOT just pause rendering the way the iOS switch-spike's
 * `window.rootViewController` swap does - going GONE tears down the GLSurfaceView-backed render
 * surface, and coming back to VISIBLE does not reliably bring it back: symptom was gameplay
 * staying a blank grey screen after returning from the "watch ad to continue" flow, since the
 * scene's own update loop (which is what notices a granted continue and restarts the level -
 * GameplayScene.kt's addUpdater) never got to run again either. Fixed by never hiding the view at
 * all: it stays permanently attached/rendering, and the Compose menu is layered opaquely on top
 * of it instead. Trade-off, not yet measured: KorGE's render loop keeps ticking while covered by
 * the menu, unlike the iOS side where hiding via rootViewController swap was measured to
 * genuinely stop frames - so this may cost more battery while the menu is showing. Correctness
 * over that unmeasured cost was the right call here since the GONE path was observably broken.
 */
class MainActivity : ComponentActivity() {
    private var korgeView: KorgeAndroidView? = null
    private val showingGameplay = mutableStateOf(false)
    private var activeSceneContainer: SceneContainer? = null
    private val profileStorage: GameProfileStorage by lazy {
        MapBackedGameProfileStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformStorage.init(this)
        // applicationContext, not this Activity - AndroidGameSfxOutputState is a long-lived
        // singleton (see GameSfxOutput's own doc comment), and this Activity is never recreated
        // in practice (single-Activity app, KorGE view never torn down), but there is no reason
        // for a static holder to pin an Activity when the application Context does everything
        // SoundPool's asset loading needs.
        AndroidGameSfxOutputState.context = applicationContext
        StoreBilling.setApplication(application)
        StoreBilling.initialize(BuildConfig.REVENUECAT_GOOGLE_KEY)
        hideSystemBars()

        // GameplayScene's update loop runs on KorGE's own GL thread, not the UI thread - hop
        // back before touching Compose state (ContinueAdTrigger's MutableState, showingGameplay).
        AndroidContinueAdBridgeState.onContinueAdRequested = {
            runOnUiThread { showContinueAd() }
        }

        // QUIT / RETURN TO MENU / MAIN MENU / ALL CLEAR in GameplayScene.kt all call this.
        // Previously nothing was wired up at all - those buttons wrote to a storage key nothing
        // ever read, so the game just silently stayed on the KorGE view. Since the KorGE view is
        // never hidden (see the class doc comment), showing the menu again is exactly this one
        // flag flip - no surface teardown/recreation involved.
        AndroidLevelExitBridgeState.onReturnToMenuRequested = {
            runOnUiThread {
                showingGameplay.value = false
                maybeShowLevelExitInterstitial()
            }
        }

        setContent {
            // Google Mobile Ads SDK requires this before any ad request will succeed - matches
            // where AdMobVerifyContent() calls it once on iOS. Without it, RewardedAd(...) below
            // would compile and run fine but every real load would fail.
            @OptIn(DependsOnGoogleMobileAds::class)
            BasicAds.Initialize()

            val gameplayVisible by showingGameplay
            Box(Modifier.fillMaxSize()) {
                // Always attached and rendering - see the class doc comment above for why this
                // is never set to View.GONE. The Compose menu below draws opaquely over it
                // instead of it being hidden.
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> KorgeAndroidView(ctx).also { korgeView = it } }
                )
                if (!gameplayVisible) {
                    NavigationRoot(onStartLevel = { levelId -> startLevel(levelId) })
                }
                // Inert until Swift-equivalent (showContinueAd, below) sets
                // ContinueAdTrigger.requestShow() - see paywall-build/src/androidMain/kotlin/ContinueAdBridge.android.kt.
                ContinueAdContent()
                // Inert until maybeShowLevelExitInterstitial() below sets InterstitialAdTrigger.requestShow().
                InterstitialAdContent()
            }
        }
    }

    // Called every time GameplayScene.kt leaves gameplay back to the menu (QUIT / RETURN TO MENU /
    // MAIN MENU / ALL CLEAR, all via LevelExitBridge). Gated by InterstitialAdLimiter so this is a
    // rare event, not a call attached to every menu return - see .junie/guidelines.md for the
    // level-2 / 180s-cooldown / 5-per-session reasoning.
    private fun maybeShowLevelExitInterstitial() {
        val profile = profileStorage.getProfile()
        if (profile.isPremium) return
        if (!InterstitialAdLimiter.canShow(profile.totalLevelsCompleted)) return
        InterstitialAdLimiter.recordShown()
        InterstitialAdTrigger.requestShow()
    }

    private fun startLevel(levelId: String) {
        val view = korgeView ?: return
        val levelData = LevelData.DEFAULT_LEVELS.firstOrNull { it.id == levelId } ?: LevelData.DEFAULT_LEVEL_1
        showingGameplay.value = true
        if (view.moduleLoaded) {
            val sc = activeSceneContainer
            if (sc != null) {
                sc.stage?.launchImmediately {
                    sc.changeTo { GameplayScene(levelData) }
                }
                return
            }
        }
        lifecycleScope.launch {
            view.loadModule(
                KorgeConfig(
                    windowSize = windowSize,
                    virtualSize = virtualSize,
                    // displayMode's own default (KorgeDisplayMode.DEFAULT = CENTER) is already
                    // ScaleMode.SHOW_ALL + Anchor.CENTER + clipBorders=true, matching main.kt's
                    // explicit scaleMode = ScaleMode.SHOW_ALL - that parameter name only exists
                    // on Korge.kt's deprecated suspend-function overload, not the data class.
                    backgroundColor = Colors["#16161d"],
                    title = "Infiltrate: Shadow Heist",
                    main = {
                        val sc = sceneContainer()
                        activeSceneContainer = sc
                        sc.changeTo { GameplayScene(levelData) }
                    }
                )
            )
        }
    }

    private fun showContinueAd() {
        // Deliberately does not touch showingGameplay: gameplay is never hidden (see the class
        // doc comment), and ContinueAdContent() is composed unconditionally below, so the ad can
        // load in the background and its own full-screen Activity will cover whatever's on
        // screen once it's ready. Flipping to the main menu here used to be what the player saw
        // for the few seconds the ad spent loading, before it appeared.
        ContinueAdTrigger.requestShow()
        lifecycleScope.launch {
            val deadlineMs = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadlineMs) {
                if (ContinueAdTrigger.consumeOutcomeFinished()) {
                    if (ContinueAdTrigger.rewardEarned) {
                        AndroidContinueAdBridgeState.grantContinue()
                    }
                    break
                }
                delay(100)
            }
        }
    }

    // Immersive fullscreen - a swipe from the edge can still reveal the system bars transiently
    // (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE), but they auto-hide again rather than staying
    // shown, and onWindowFocusChanged re-applies this after any such reveal or app-switch return.
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // GameSfxOutput.kt's mixer thread/AudioTrack (see its own doc comment) runs forever once
    // started, with no lifecycle awareness of its own - without this it kept playing gameplay
    // audio (music + any in-flight one-shots) after leaving the app entirely, not just returning
    // to the in-app menu (which already stops music via stopBgMusic/stopMusic on its own).
    override fun onPause() {
        super.onPause()
        AndroidGameSfxOutputState.pauseEngine()
    }

    override fun onResume() {
        super.onResume()
        AndroidGameSfxOutputState.resumeEngine()
    }
}
