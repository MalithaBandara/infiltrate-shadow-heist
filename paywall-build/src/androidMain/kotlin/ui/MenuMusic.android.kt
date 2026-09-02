package com.infiltrate.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Android music via android.media.MediaPlayer.
 *
 * Two sources are tried, in this order, because this repo bundles the same asset two different
 * ways depending on who packaged the APK:
 *
 *  1. `res/raw`, which is where [LoopingVideoBackground] looks and where an Android app module
 *     would put it.
 *  2. `assets/`, which is where KorGE's Gradle plugin copies the repo's `resources/` directory
 *     when it builds the Android app - so the single copy in `resources/mainmenu.mp3` is
 *     reachable without a second one checked in under `res/raw`.
 *
 * Neither present is silence, not a crash.
 */
private object AndroidMusicPlayer {
    private var player: MediaPlayer? = null

    fun start(context: Context, trackName: String, trackExtension: String, volume: Float) {
        if (player != null) {
            setVolume(volume)
            try { if (player?.isPlaying == false) player?.start() } catch (_: Throwable) {}
            return
        }
        try {
            val rawId = context.resources.getIdentifier(trackName, "raw", context.packageName)
            val created = if (rawId != 0) {
                MediaPlayer.create(context, rawId)
            } else {
                MediaPlayer().apply {
                    context.assets.openFd("$trackName.$trackExtension").use { fd ->
                        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                    prepare()
                }
            }
            created?.apply {
                isLooping = true
                setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
                start()
            }
            player = created
        } catch (t: Throwable) {
            println("[MenuMusic] Android playback failed: ${t.message}")
            player = null
        }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        try { player?.setVolume(v, v) } catch (_: Throwable) {}
    }

    fun stop() {
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.release() } catch (_: Throwable) {}
        player = null
    }
}

@Composable
actual fun MenuMusic(
    trackName: String,
    trackExtension: String,
    volume: Float
) {
    val context = LocalContext.current

    DisposableEffect(trackName, trackExtension) {
        AndroidMusicPlayer.start(context, trackName, trackExtension, volume)
        onDispose { AndroidMusicPlayer.stop() }
    }

    LaunchedEffect(volume) { AndroidMusicPlayer.setVolume(volume) }
}
