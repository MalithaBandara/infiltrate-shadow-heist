package com.infiltrate.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android click via SoundPool, which is the API meant for exactly this: short, frequently
 * repeated, overlapping UI sounds held decoded in memory. MediaPlayer - what [MenuMusic] uses -
 * would be wrong here, since one instance cannot overlap itself and creating one per tap would
 * allocate and decode on every press.
 *
 * Two sources are tried, in the same order and for the same reason as [MenuMusic]:
 *
 *  1. `res/raw`, where an Android app module would put it.
 *  2. `assets/sfx/ui_click.wav`, which is where KorGE's Gradle plugin copies the repo's
 *     `resources/sfx/` directory when it builds the Android app - so the single copy in
 *     `resources/sfx/ui_click.wav` is reachable without a second one checked in under `res/raw`.
 *
 * Neither present is silence, not a crash.
 */
private object AndroidClickPlayer {
    private var pool: SoundPool? = null
    private var soundId = 0
    private var ready = false

    fun prepare(context: Context) {
        if (pool != null) return
        try {
            val created = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            // SoundPool loads asynchronously; playing before this fires is a silent no-op,
            // which for a click is the right failure - it only affects taps in the first frames.
            created.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }

            val rawId = context.resources.getIdentifier("ui_click", "raw", context.packageName)
            soundId = if (rawId != 0) {
                created.load(context, rawId, 1)
            } else {
                context.assets.openFd("sfx/ui_click.wav").use { fd ->
                    created.load(fd.fileDescriptor, fd.startOffset, fd.length, 1)
                }
            }
            pool = created
        } catch (t: Throwable) {
            println("[MenuSfx] Android load failed: ${t.message}")
            pool = null
        }
    }

    fun play(volume: Float) {
        if (!ready) return
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0.001f) return
        try {
            pool?.play(soundId, v, v, 1, 0, 1.0f)
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try { pool?.release() } catch (_: Throwable) {}
        pool = null
        ready = false
        soundId = 0
    }
}

@Composable
actual fun rememberUiClick(volume: Float): () -> Unit {
    val context = LocalContext.current

    DisposableEffect(context) {
        AndroidClickPlayer.prepare(context)
        onDispose { AndroidClickPlayer.release() }
    }

    return remember(volume) { { AndroidClickPlayer.play(volume) } }
}
