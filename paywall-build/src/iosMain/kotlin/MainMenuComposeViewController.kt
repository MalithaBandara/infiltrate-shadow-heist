package com.infiltrate.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.ComposeUIViewController
import com.infiltrate.ads.ContinueAdContent
import com.infiltrate.ads.InterstitialAdContent
import game.model.SystemLanguage
import kotlin.native.ObjCName
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UIKit.UIViewController

@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "MainMenuComposeScreen", exact = true)
object MainMenuComposeScreen {
    fun makeViewController(onStartLevel: (String) -> Unit): UIViewController {
        // AppDelegate's first call into PaywallModule, so this runs before NavigationRoot creates
        // any profile storage: a first launch picks the device language if the game is
        // translated into it (see SystemLanguage / MapBackedGameProfileStorage). The KorGE game
        // framework has its own copy of SystemLanguage, never set - it reads the saved choice.
        SystemLanguage.preferredTags = { NSLocale.preferredLanguages.mapNotNull { it as? String } }
        return ComposeUIViewController {
            // Left-to-right always, like Android (no supportsRtl): Info.plist declares Arabic, so on
            // an Arabic device iOS would otherwise hand Compose RTL and mirror every menu.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                NavigationRoot(onStartLevel = onStartLevel)
                // Real BasicAds.Initialize() + RequestConfiguration call site (see
                // AdMobVerifyScreen.kt), inert until AppDelegate.swift's consent flow settles -
                // deliberately rendered in the SAME scene as NavigationRoot, not a second
                // ComposeUIViewController (that crashed - see AdMobVerifyScreen.kt for the full
                // story). Renders no UI of its own; does not affect the real menu.
                AdMobVerifyContent()
                // Real "watch ad to continue" trigger (see ContinueAdBridge.kt) - inert until Swift
                // calls ContinueAdTrigger.requestShow() after a mid-game death.
                ContinueAdContent()
                // Real interstitial (see InterstitialAdBridge.kt) - preloads, then inert until Swift
                // or the menu's own buttons request it (see InterstitialAdGate.kt), matching Android's
                // InterstitialAdContent() call site.
                InterstitialAdContent()
            }
        }
    }

    fun makeViewController(onStartLevel: () -> Unit): UIViewController =
        makeViewController(onStartLevel = { _ -> onStartLevel() })
}
