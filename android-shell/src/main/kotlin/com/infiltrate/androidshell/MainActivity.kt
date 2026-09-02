package com.infiltrate.androidshell

import android.os.Bundle
import android.view.View
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
import com.infiltrate.ui.NavigationRoot
import com.sample.demo.ads.AndroidContinueAdBridgeState
import game.model.LevelData
import game.scene.GameplayScene
import korlibs.image.color.Colors
// KorgeConfig, not Korge: Korge.kt defines both a `data class Korge(...)` (what loadModule
// actually needs) AND several top-level `suspend fun Korge(...)` overloads sharing the exact
// same name, including one that also accepts a `main` parameter - so even a fully-named `Korge(
// main = {...})` call still resolves ambiguously to either. `KorgeConfig` is Korge.kt's own
// typealias for just the data class, with no such overload to collide with.
import korlibs.korge.KorgeConfig
import korlibs.korge.android.KorgeAndroidView
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
 * owns gameplay; both stay alive the whole time, only visibility toggles - matching the
 * "Android Warm Engine Symmetry" candidate architecture in .junie/guidelines.md and the
 * already-proven-on-iOS "never destroy the warm engine" pattern from the switch-spike.
 *
 * UNVERIFIED: whether View.GONE actually stops KorgeAndroidView's internal GLSurfaceView render
 * thread (as opposed to merely hiding it while it keeps rendering invisibly) has not been
 * measured here the way the iOS switch-spike measured it for KorGE's iOS GLKViewController -
 * see .junie/guidelines.md.
 */
class MainActivity : ComponentActivity() {
    private var korgeView: KorgeAndroidView? = null
    private val showingGameplay = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        // GameplayScene's update loop runs on KorGE's own GL thread, not the UI thread - hop
        // back before touching Compose state (ContinueAdTrigger's MutableState, showingGameplay).
        AndroidContinueAdBridgeState.onContinueAdRequested = {
            runOnUiThread { showContinueAd() }
        }

        setContent {
            // Google Mobile Ads SDK requires this before any ad request will succeed - matches
            // where AdMobVerifyContent() calls it once on iOS. Without it, RewardedAd(...) below
            // would compile and run fine but every real load would fail.
            @OptIn(DependsOnGoogleMobileAds::class)
            BasicAds.Initialize()

            val gameplayVisible by showingGameplay
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> KorgeAndroidView(ctx).also { korgeView = it } },
                    update = { view -> view.visibility = if (gameplayVisible) View.VISIBLE else View.GONE }
                )
                if (!gameplayVisible) {
                    NavigationRoot(onStartLevel = { levelId -> startLevel(levelId) })
                }
                // Inert until Swift-equivalent (showContinueAd, below) sets
                // ContinueAdTrigger.requestShow() - see paywall-build/src/androidMain/kotlin/ContinueAdBridge.android.kt.
                ContinueAdContent()
            }
        }
    }

    private fun startLevel(levelId: String) {
        val view = korgeView ?: return
        showingGameplay.value = true
        if (view.moduleLoaded) {
            // Engine's already warm from an earlier level. Matches ios-shell's current behavior
            // exactly (AppDelegate.swift only ever calls the no-arg makeViewController overload,
            // discarding the level id too) - not a new limitation introduced here, a pre-existing
            // one on both platforms. GameplayScene's own RETRY/continue-granted restart path
            // still works correctly against whichever level is already loaded.
            return
        }
        val levelData = LevelData.DEFAULT_LEVELS.firstOrNull { it.id == levelId } ?: LevelData.DEFAULT_LEVEL_1
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
                        val sceneContainer = sceneContainer()
                        sceneContainer.changeTo { GameplayScene(levelData) }
                    }
                )
            )
        }
    }

    private fun showContinueAd() {
        showingGameplay.value = false
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
            showingGameplay.value = true
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
}
