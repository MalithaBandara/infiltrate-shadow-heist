package com.infiltrate.ui

import androidx.compose.ui.window.ComposeUIViewController
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
        }

    fun makeViewController(onStartLevel: () -> Unit): UIViewController =
        makeViewController(onStartLevel = { _ -> onStartLevel() })
}
