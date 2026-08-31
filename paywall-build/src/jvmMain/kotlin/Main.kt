package com.infiltrate

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.infiltrate.ui.NavigationRoot

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Infiltrate: Shadow Heist (Compose Multiplatform)",
        state = rememberWindowState(width = 1280.dp, height = 720.dp)
    ) {
        NavigationRoot(onStartLevel = {
            println("DESKTOP: Start level triggered")
        })
    }
}
