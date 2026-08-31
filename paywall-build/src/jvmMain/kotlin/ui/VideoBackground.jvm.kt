package com.infiltrate.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.layout.ContentScale
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.io.File

@Composable
actual fun LoopingVideoBackground(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    fallbackDrawable: DrawableResource
) {
    val videoFile = remember(videoName, videoExtension) {
        val candidates = listOf(
            File("resources/$videoName.$videoExtension"),
            File("../resources/$videoName.$videoExtension"),
            File("C:/Users/USER/Downloads/charAnimations/assets/$videoName.$videoExtension")
        )
        candidates.firstOrNull { it.exists() }
    }

    if (videoFile == null) {
        Image(
            painter = painterResource(fallbackDrawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = modifier
        )
        return
    }

    var mediaPlayer: MediaPlayer? = null

    DisposableEffect(videoFile) {
        onDispose {
            Platform.runLater {
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.dispose()
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }
    }

    SwingPanel(
        modifier = modifier,
        factory = {
            val jfxPanel = JFXPanel()
            Platform.runLater {
                try {
                    val media = Media(videoFile.toURI().toString())
                    val player = MediaPlayer(media).apply {
                        cycleCount = MediaPlayer.INDEFINITE
                        isMute = true
                    }
                    mediaPlayer = player
                    val mediaView = MediaView(player).apply {
                        isPreserveRatio = false
                    }

                    val root = Group(mediaView)
                    val scene = Scene(root, javafx.scene.paint.Color.BLACK)
                    jfxPanel.scene = scene

                    jfxPanel.addComponentListener(object : java.awt.event.ComponentAdapter() {
                        override fun componentResized(e: java.awt.event.ComponentEvent?) {
                            mediaView.fitWidth = jfxPanel.width.toDouble()
                            mediaView.fitHeight = jfxPanel.height.toDouble()
                        }
                    })

                    player.play()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            jfxPanel
        }
    )
}
