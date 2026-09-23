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

private fun parseWindowSizeOverride(raw: String?): Pair<Float, Float>? {
    val parts = raw?.trim()?.lowercase()?.split("x") ?: return null
    if (parts.size != 2) return null
    val w = parts[0].trim().toFloatOrNull() ?: return null
    val h = parts[1].trim().toFloatOrNull() ?: return null
    if (w < 200f || h < 200f) return null
    return w to h
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

    // Responsive testing affordance: `./gradlew :paywall-build:run -PwindowSize=844x390` opens
    // the menu in an iPhone-sized dp box, `-PwindowSize=1024x768` an iPad's. The window is sized
    // in dp, so BoxWithConstraints sees exactly the constraints that device would give it - which
    // is how the menu scale was checked here, since this machine has no emulator and iOS builds
    // only in CI. Unset, nothing changes.
    val override = parseWindowSizeOverride(System.getenv("windowSize"))

    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        val windowState = rememberWindowState(
            width = (override?.first ?: 1560f).dp,
            height = (override?.second ?: 720f).dp,
        )

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
