package com.infiltrate.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.infiltrate.ads.ContinueAdContent
import com.infiltrate.ads.InterstitialAdContent
import kotlin.native.ObjCName
import platform.UIKit.UIViewController

@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "MainMenuComposeScreen", exact = true)
object MainMenuComposeScreen {
    fun makeViewController(onStartLevel: (String) -> Unit): UIViewController =
        ComposeUIViewController {
            NavigationRoot(onStartLevel = onStartLevel)
            // AdMob on-device verification spike (see .junie/guidelines.md) - deliberately
            // rendered in the SAME scene as NavigationRoot, not a second ComposeUIViewController
            // (that crashed - see AdMobVerifyScreen.kt for the full story). Tiny/invisible,
            // does not affect the real menu UI.
            AdMobVerifyContent()
            // Real "watch ad to continue" trigger (see ContinueAdBridge.kt) - inert until Swift
            // calls ContinueAdTrigger.requestShow() after a mid-game death.
            ContinueAdContent()
            // Real level-exit interstitial (see InterstitialAdBridge.kt) - inert until Swift
            // calls InterstitialAdTrigger.maybeRequestShow(...) after QUIT/RETURN TO MENU/
            // MAIN MENU/ALL CLEAR, matching Android's InterstitialAdContent() call site.
            InterstitialAdContent()
        }

    fun makeViewController(onStartLevel: () -> Unit): UIViewController =
        makeViewController(onStartLevel = { _ -> onStartLevel() })
}
