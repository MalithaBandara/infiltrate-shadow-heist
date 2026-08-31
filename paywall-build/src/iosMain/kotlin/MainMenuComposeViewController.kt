package com.infiltrate.ui

import androidx.compose.ui.window.ComposeUIViewController
import kotlin.native.ObjCName
import platform.UIKit.UIViewController

@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "MainMenuComposeScreen", exact = true)
object MainMenuComposeScreen {
    fun makeViewController(onStartLevel: () -> Unit): UIViewController =
        ComposeUIViewController {
            NavigationRoot(onStartLevel = onStartLevel)
        }
}
