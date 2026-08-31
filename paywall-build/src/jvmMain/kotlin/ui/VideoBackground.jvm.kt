package com.infiltrate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.image.WritableImage
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.image.BufferedImage
import java.io.File

@Composable
actual fun LoopingVideoBackground(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    fallbackDrawable: DrawableResource
) {
    // Ensure JavaFX platform is initialized
    remember {
        try {
            JFXPanel()
        } catch (t: Throwable) {
            // ignore
        }
    }

    val videoFile = remember(videoName, videoExtension) {
        val candidates = listOf(
            File("resources/$videoName.$videoExtension"),
            File("../resources/$videoName.$videoExtension"),
            File("C:/Users/USER/Downloads/charAnimations/assets/$videoName.$videoExtension")
        )
        candidates.firstOrNull { it.exists() }
    }

    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var videoWidthPx by remember { mutableStateOf(1920) }
    var videoHeightPx by remember { mutableStateOf(1080) }

    DisposableEffect(videoFile) {
        if (videoFile == null) return@DisposableEffect onDispose {}

        var player: MediaPlayer? = null
        var timer: AnimationTimer? = null

        Platform.runLater {
            try {
                val media = Media(videoFile.toURI().toString())
                val mp = MediaPlayer(media).apply {
                    cycleCount = MediaPlayer.INDEFINITE
                    isMute = true
                }
                player = mp

                val mediaView = MediaView(mp).apply {
                    isPreserveRatio = true
                }

                val root = Group(mediaView)
                val scene = Scene(root)

                var writableImage: WritableImage? = null
                var bufferedImage: BufferedImage? = null

                val animTimer = object : AnimationTimer() {
                    override fun handle(now: Long) {
                        try {
                            val w = media.width
                            val h = media.height
                            if (w > 0 && h > 0) {
                                if (videoWidthPx != w || videoHeightPx != h) {
                                    videoWidthPx = w
                                    videoHeightPx = h
                                }
                                if (writableImage == null || writableImage?.width?.toInt() != w || writableImage?.height?.toInt() != h) {
                                    writableImage = WritableImage(w, h)
                                }
                                mediaView.snapshot(null, writableImage)
                                bufferedImage = SwingFXUtils.fromFXImage(writableImage, bufferedImage)
                                bufferedImage?.let {
                                    currentFrame = it.toComposeImageBitmap()
                                }
                            }
                        } catch (t: Throwable) {
                            // snapshot catch
                        }
                    }
                }
                timer = animTimer

                mp.setOnReady {
                    mediaView.fitWidth = media.width.toDouble()
                    mediaView.fitHeight = media.height.toDouble()
                    animTimer.start()
                    mp.play()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        onDispose {
            Platform.runLater {
                try {
                    timer?.stop()
                    player?.stop()
                    player?.dispose()
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.background(Color.Black)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val videoAspect = if (videoHeightPx > 0) videoWidthPx.toFloat() / videoHeightPx.toFloat() else (16f / 9f)
        val screenAspect = if (screenH.value > 0) screenW.value / screenH.value else videoAspect

        // Sizing rules:
        // 1. If screen is wider than video (screenAspect > videoAspect):
        //    Height = screenH, Width = screenH * videoAspect.
        //    Aligned to TopEnd (right side), left side has extra black.
        // 2. If screen is narrower than video (screenAspect <= videoAspect):
        //    Width = screenW, Height = screenW / videoAspect.
        //    Aligned to TopEnd, bottom overflows.
        val (targetW, targetH) = if (screenAspect > videoAspect) {
            (screenH * videoAspect) to screenH
        } else {
            screenW to (screenW / videoAspect)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            val frame = currentFrame
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .width(targetW)
                        .height(targetH)
                )
            } else {
                Image(
                    painter = painterResource(fallbackDrawable),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
