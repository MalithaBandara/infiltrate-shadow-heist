package com.infiltrate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import java.awt.image.BufferedImage
import java.io.File

/**
 * Where the desktop finds the intro. Not in the repo's `resources/`: both app builds bundle that
 * whole folder, so a copy there would ship twice on Android and collide with
 * `ios-shell/Resources/intro.mp4` in the iOS bundle root. The Android copy is read instead.
 */
private fun introVideoFile(videoName: String, videoExtension: String): File? {
    val name = "$videoName.$videoExtension"
    return listOf(
        File("android-shell/src/main/res/raw/$name"),
        File("../android-shell/src/main/res/raw/$name"),
        File("ios-shell/Resources/$name"),
        File("../ios-shell/Resources/$name"),
    ).firstOrNull { it.exists() }
}

/** Written on the JavaFX thread, read by the Compose frame loop. */
private class DesktopIntroPlayback {
    @Volatile var ended = false
    @Volatile var seconds = 0.0
    @Volatile var disposed = false
    var player: MediaPlayer? = null
    var timer: AnimationTimer? = null
}

/** Frames are snapshotted at 720p - plenty for a window and far cheaper than 1080p per frame. */
private const val SNAPSHOT_WIDTH = 1280.0
private const val SNAPSHOT_HEIGHT = 720.0

/**
 * Desktop intro via JavaFX Media, the same route [LoopingVideoBackground] takes on JVM: a
 * MediaView rendered off-screen and snapshotted into an [ImageBitmap] every new video frame.
 */
@Composable
actual fun IntroVideoPlayer(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    volume: Float,
    onProgress: (seconds: Double) -> Unit,
    onFinished: () -> Unit
) {
    val finish by rememberUpdatedState(onFinished)
    val progress by rememberUpdatedState(onProgress)
    val file = remember(videoName, videoExtension) { introVideoFile(videoName, videoExtension) }
    if (file == null) {
        println("[Intro] $videoName.$videoExtension not found from ${File(".").absolutePath}")
        LaunchedEffect(Unit) { finish() }
        return
    }

    val playback = remember(file) { DesktopIntroPlayback() }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val startVolume = remember { volume.coerceIn(0f, 1f).toDouble() }

    DisposableEffect(playback) {
        try { JFXPanel() } catch (_: Throwable) {}
        Platform.runLater {
            if (playback.disposed) return@runLater
            try {
                val mp = MediaPlayer(Media(file.toURI().toString())).apply {
                    this.volume = startVolume
                    setOnEndOfMedia { playback.ended = true }
                    setOnError {
                        println("[Intro] Desktop playback error: ${error?.message}")
                        playback.ended = true
                    }
                }
                playback.player = mp
                val view = MediaView(mp).apply {
                    fitWidth = SNAPSHOT_WIDTH
                    fitHeight = SNAPSHOT_HEIGHT
                    isPreserveRatio = false
                }
                Scene(Group(view))
                val params = SnapshotParameters()
                val fxImage = WritableImage(SNAPSHOT_WIDTH.toInt(), SNAPSHOT_HEIGHT.toInt())
                var awtImage: BufferedImage? = null
                var lastTime: javafx.util.Duration? = null
                val timer = object : AnimationTimer() {
                    override fun handle(now: Long) {
                        try {
                            val t = mp.currentTime
                            if (t == lastTime) return
                            lastTime = t
                            playback.seconds = t.toSeconds()
                            view.snapshot(params, fxImage)
                            awtImage = SwingFXUtils.fromFXImage(fxImage, awtImage)
                            awtImage?.let { frame = it.toComposeImageBitmap() }
                        } catch (_: Throwable) {}
                    }
                }
                playback.timer = timer
                timer.start()
                mp.play()
            } catch (t: Throwable) {
                println("[Intro] Desktop video failed to open: ${t.message}")
                playback.ended = true
            }
        }
        onDispose {
            playback.disposed = true
            Platform.runLater {
                try { playback.timer?.stop() } catch (_: Throwable) {}
                try { playback.player?.stop() } catch (_: Throwable) {}
                try { playback.player?.dispose() } catch (_: Throwable) {}
            }
        }
    }

    LaunchedEffect(playback, volume) {
        val v = volume.coerceIn(0f, 1f).toDouble()
        Platform.runLater { try { playback.player?.volume = v } catch (_: Throwable) {} }
    }

    LaunchedEffect(playback) {
        while (true) {
            withFrameNanos { }
            if (playback.ended) {
                finish()
                break
            }
            progress(playback.seconds)
        }
    }

    Box(modifier = modifier) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
