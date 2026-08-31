package com.infiltrate

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.infiltrate.ui.NavigationRoot

fun main() = application {
    // Galaxy S25 Ultra landscape aspect ratio (3120x1440 at half-scale)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Infiltrate: Shadow Heist",
        state = rememberWindowState(width = 1560.dp, height = 720.dp)
    ) {
        NavigationRoot(onStartLevel = {
            println("DESKTOP: Start level triggered")
        })
    }
}
