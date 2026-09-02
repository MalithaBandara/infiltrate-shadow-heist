package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import javafx.embed.swing.JFXPanel
import javafx.scene.media.AudioClip
import java.io.File

/**
 * Desktop click via JavaFX AudioClip - the same stack [MenuMusic] and [LoopingVideoBackground]
 * already bring in on this target, so it costs no new dependency.
 *
 * AudioClip rather than MediaPlayer because it is JavaFX's own answer to short repeated sounds:
 * it holds the samples decoded in memory and overlaps its own playback, where a MediaPlayer
 * would have to be seeked back to zero and could not sound twice at once.
 */
private object DesktopClickPlayer {
    private var clip: AudioClip? = null
    private var loadedPath: String? = null

    fun prepare(file: File) {
        try {
            if (loadedPath == file.absolutePath && clip != null) return
            clip = AudioClip(file.toURI().toString())
            loadedPath = file.absolutePath
        } catch (t: Throwable) {
            println("[MenuSfx] desktop load failed: ${t.message}")
            clip = null
            loadedPath = null
        }
    }

    fun play(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0.001f) return
        try { clip?.play(v.toDouble()) } catch (_: Throwable) {}
    }
}

@Composable
actual fun rememberUiClick(volume: Float): () -> Unit {
    // JavaFX needs its platform up before any media object is constructed; the video background
    // and the menu music both do the same, and a second JFXPanel is harmless if one already ran.
    remember {
        try { JFXPanel() } catch (_: Throwable) {}
    }

    // Same candidate list as the video and the music: the desktop build runs out of the repo
    // root in some configurations and out of a subdirectory in others.
    val clickFile = remember {
        listOf(
            File("resources/sfx/ui_click.wav"),
            File("../resources/sfx/ui_click.wav")
        ).firstOrNull { it.exists() }
    }

    remember(clickFile) {
        if (clickFile != null) DesktopClickPlayer.prepare(clickFile)
        Unit
    }

    return remember(volume) { { DesktopClickPlayer.play(volume) } }
}
