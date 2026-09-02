package com.infiltrate.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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

    // Pause only (not stop/release) - the app can come right back via ON_RESUME, and start()
    // already resumes an existing player in place rather than recreating it.
    fun pause() {
        try { if (player?.isPlaying == true) player?.pause() } catch (_: Throwable) {}
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

    // The composable staying in composition (this Activity is never destroyed while
    // backgrounded, only stopped/resumed - see MainActivity's single-Activity design) doesn't
    // mean the app is in the foreground: DisposableEffect's onDispose above only fires on
    // recomposition/leaving the screen, not on the user backgrounding the app, which is why the
    // track kept playing after Home/close. Pausing/resuming on the real Activity lifecycle fixes
    // that independently of Compose's own composition lifecycle.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> AndroidMusicPlayer.pause()
                Lifecycle.Event.ON_RESUME -> AndroidMusicPlayer.start(context, trackName, trackExtension, volume)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(volume) { AndroidMusicPlayer.setVolume(volume) }
}
