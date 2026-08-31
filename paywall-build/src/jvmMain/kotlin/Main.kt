package com.infiltrate

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.infiltrate.ui.NavigationRoot
import java.io.File
import kotlin.concurrent.thread

fun launchKorgeGame(levelId: String) {
    thread(name = "Korge-Runner", isDaemon = true) {
        try {
            println("[Desktop] Starting KorGE game engine for level '$levelId'...")
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
            println("[Desktop] KorGE game process exited with code $exitCode")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

fun main() = application {
    // Galaxy S25 Ultra landscape aspect ratio (3120x1440 at half-scale)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Infiltrate: Shadow Heist",
        state = rememberWindowState(width = 1560.dp, height = 720.dp)
    ) {
        NavigationRoot(
            onStartLevel = { levelId ->
                launchKorgeGame(levelId)
            }
        )
    }
}
