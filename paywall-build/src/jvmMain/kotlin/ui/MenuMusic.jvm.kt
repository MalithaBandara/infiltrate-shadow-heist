package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.io.File

/**
 * Desktop music via JavaFX Media - the same stack [LoopingVideoBackground] already brings in on
 * this target, so MP3 playback costs no new dependency.
 */
private object DesktopMusicPlayer {
    private var player: MediaPlayer? = null
    private var loadedPath: String? = null

    fun start(file: File, volume: Float) {
        try {
            if (loadedPath != file.absolutePath) {
                stop()
                val media = Media(file.toURI().toString())
                player = MediaPlayer(media).apply {
                    // JavaFX has no gapless loop of its own; seeking back to zero on each cycle
                    // is the documented way to do it and is sample-accurate enough for a menu bed.
                    cycleCount = MediaPlayer.INDEFINITE
                    setOnEndOfMedia { seek(Duration.ZERO) }
                }
                loadedPath = file.absolutePath
            }
            player?.volume = volume.toDouble().coerceIn(0.0, 1.0)
            player?.play()
        } catch (t: Throwable) {
            println("[MenuMusic] desktop playback failed: ${t.message}")
        }
    }

    fun setVolume(volume: Float) {
        try { player?.volume = volume.toDouble().coerceIn(0.0, 1.0) } catch (_: Throwable) {}
    }

    fun stop() {
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.dispose() } catch (_: Throwable) {}
        player = null
        loadedPath = null
    }
}

@Composable
actual fun MenuMusic(
    trackName: String,
    trackExtension: String,
    volume: Float
) {
    // JavaFX needs its platform up before any Media is constructed; the video background does the
    // same thing, and a second JFXPanel is harmless if it already ran.
    remember {
        try { JFXPanel() } catch (_: Throwable) {}
    }

    // Same candidate list as the video: the desktop build runs out of the repo root in some
    // configurations and out of a subdirectory in others.
    val trackFile = remember(trackName, trackExtension) {
        listOf(
            File("resources/$trackName.$trackExtension"),
            File("../resources/$trackName.$trackExtension")
        ).firstOrNull { it.exists() }
    }

    DisposableEffect(trackFile) {
        if (trackFile != null) DesktopMusicPlayer.start(trackFile, volume)
        onDispose { DesktopMusicPlayer.stop() }
    }

    // Volume changes come from the settings slider while the track is already playing, so they
    // are applied in place rather than by restarting it.
    LaunchedEffect(volume) { DesktopMusicPlayer.setVolume(volume) }
}
