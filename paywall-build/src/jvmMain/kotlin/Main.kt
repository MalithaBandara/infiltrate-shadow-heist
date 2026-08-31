package com.infiltrate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.infiltrate.ui.DesktopVideoPlayerManager
import com.infiltrate.ui.NavigationRoot
import java.io.File
import kotlin.concurrent.thread

fun launchKorgeGame(levelId: String, onFinished: () -> Unit) {
    thread(name = "Korge-Runner", isDaemon = true) {
        try {
            println("[Desktop] Launching KorGE game for '$levelId' (switching window)...")
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val rootDir = File(".").canonicalFile.let {
                if (it.name == "paywall-build") it.parentFile else it
            }
            val gradlew = if (isWindows) "gradlew.bat" else "./gradlew"
            val cmd = if (isWindows) {
                listOf("cmd.exe", "/c", "$rootDir\\$gradlew", ":runJvm", "-PstartLevel=$levelId")
            } else {
                listOf("$rootDir/$gradlew", ":runJvm", "-PstartLevel=$levelId")
            }

            val process = ProcessBuilder(cmd)
                .directory(rootDir)
                .inheritIO()
                .start()

            val exitCode = process.waitFor()
            println("[Desktop] KorGE game exited with code $exitCode. Restoring menu window...")
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            javax.swing.SwingUtilities.invokeLater {
                onFinished()
            }
        }
    }
}

fun main() {
    // Eagerly pre-warm video decoder in background so it's ready on the very first frame
    val candidates = listOf(
        File("resources/bg1080p.mp4"),
        File("../resources/bg1080p.mp4"),
        File("C:/Users/USER/Downloads/charAnimations/assets/bg1080p.mp4")
    )
    val videoFile = candidates.firstOrNull { it.exists() }
    if (videoFile != null) {
        DesktopVideoPlayerManager.initialize(videoFile)
    }

    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        val windowState = rememberWindowState(width = 1560.dp, height = 720.dp)

        // Galaxy S25 Ultra landscape aspect ratio (3120x1440 at half-scale)
        Window(
            onCloseRequest = ::exitApplication,
            title = "Infiltrate: Shadow Heist",
            state = windowState,
            visible = isWindowVisible
        ) {
            NavigationRoot(
                onStartLevel = { levelId ->
                    isWindowVisible = false
                    DesktopVideoPlayerManager.pause()
                    launchKorgeGame(levelId) {
                        isWindowVisible = true
                        DesktopVideoPlayerManager.resume()
                    }
                }
            )
        }
    }
}
