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
import javafx.scene.SnapshotParameters
import javafx.scene.image.WritableImage
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.image.BufferedImage
import java.io.File

internal object DesktopVideoPlayerManager {
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null
    private var mediaView: MediaView? = null
    private var animTimer: AnimationTimer? = null

    var currentFrame: ImageBitmap? by mutableStateOf(null)
    var videoWidth: Int by mutableStateOf(1920)
    var videoHeight: Int by mutableStateOf(1080)

    init {
        try {
            System.setProperty("prism.order", "d3d,sw")
            System.setProperty("prism.vsync", "false")
            System.setProperty("prism.allowhidpi", "false")
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun initialize(videoFile: File) {
        try {
            JFXPanel()
        } catch (t: Throwable) {
            // ignore
        }

        if (isInitialized && mediaPlayer != null) {
            resume()
            return
        }

        Platform.runLater {
            try {
                val media = Media(videoFile.toURI().toString())
                val mp = MediaPlayer(media).apply {
                    cycleCount = MediaPlayer.INDEFINITE
                    isMute = true
                    isAutoPlay = true
                    setOnEndOfMedia {
                        seek(javafx.util.Duration.ZERO)
                        play()
                    }
                }
                mediaPlayer = mp

                val mv = MediaView(mp).apply {
                    isPreserveRatio = true
                }
                mediaView = mv

                val root = Group(mv)
                val scene = Scene(root)

                val snapParams = SnapshotParameters().apply {
                    fill = javafx.scene.paint.Color.TRANSPARENT
                }

                var writableImage: WritableImage? = null
                var bufferedImage: BufferedImage? = null
                var lastMediaTime: javafx.util.Duration? = null
                var lastSnapshotNanos = 0L
                val minFrameIntervalNanos = 16_000_000L // Cap to ~60fps max

                fun captureFrame(): Boolean {
                    val w = if (media.width > 0) media.width else 1920
                    val h = if (media.height > 0) media.height else 1080
                    if (w <= 0 || h <= 0) return false

                    if (videoWidth != w || videoHeight != h) {
                        videoWidth = w
                        videoHeight = h
                    }
                    if (writableImage == null || writableImage?.width?.toInt() != w || writableImage?.height?.toInt() != h) {
                        writableImage = WritableImage(w, h)
                    }
                    if (bufferedImage == null || bufferedImage?.width != w || bufferedImage?.height != h) {
                        bufferedImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
                    }

                    mv.snapshot(snapParams, writableImage)
                    bufferedImage = SwingFXUtils.fromFXImage(writableImage, bufferedImage)
                    bufferedImage?.let {
                        currentFrame = it.toComposeImageBitmap()
                    }
                    return true
                }

                val timer = object : AnimationTimer() {
                    override fun handle(now: Long) {
                        try {
                            if (now - lastSnapshotNanos < minFrameIntervalNanos) return
                            val currentTime = mp.currentTime
                            if (currentTime == lastMediaTime) return
                            lastMediaTime = currentTime
                            lastSnapshotNanos = now

                            captureFrame()
                        } catch (t: Throwable) {
                            // snapshot catch
                        }
                    }
                }
                animTimer = timer

                fun start() {
                    val w = if (media.width > 0) media.width else 1920
                    val h = if (media.height > 0) media.height else 1080
                    mv.fitWidth = w.toDouble()
                    mv.fitHeight = h.toDouble()
                    videoWidth = w
                    videoHeight = h

                    // JIT warm-up pass
                    try {
                        captureFrame()
                    } catch (t: Throwable) {
                        // ignore
                    }

                    timer.start()
                    mp.play()
                }

                if (mp.status == MediaPlayer.Status.READY || mp.status == MediaPlayer.Status.PLAYING) {
                    start()
                } else {
                    mp.setOnReady { start() }
                }

                isInitialized = true
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun pause() {
        Platform.runLater {
            try {
                animTimer?.stop()
                mediaPlayer?.pause()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    fun resume() {
        Platform.runLater {
            try {
                animTimer?.start()
                mediaPlayer?.let { player ->
                    if (player.status == MediaPlayer.Status.STOPPED || player.status == MediaPlayer.Status.HALTED) {
                        player.seek(javafx.util.Duration.ZERO)
                    }
                    player.play()
                }
            } catch (t: Throwable) {
                // ignore
            }
        }
    }
}

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

    DisposableEffect(videoFile) {
        if (videoFile != null) {
            DesktopVideoPlayerManager.initialize(videoFile)
        }
        onDispose {
            DesktopVideoPlayerManager.pause()
        }
    }

    val currentFrame = DesktopVideoPlayerManager.currentFrame
    val videoWidthPx = DesktopVideoPlayerManager.videoWidth
    val videoHeightPx = DesktopVideoPlayerManager.videoHeight

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
