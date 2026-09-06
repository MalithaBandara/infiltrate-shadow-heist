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
 *
 * `AudioAttributes.setUsage(USAGE_GAME)`, not `USAGE_ASSISTANCE_SONIFICATION` (this pool's
 * original setting): `USAGE_ASSISTANCE_SONIFICATION` is Android's tag for system/accessibility
 * feedback (keyboard clicks, screen-reader cues), which many phones route through a system-sounds
 * stream that's muted or turned down independently of the media volume the player actually
 * controls - and does not respond to it, so the click/toast sounds went silent even with SFX
 * volume turned up. `GameAudio.kt`'s gameplay bus (`AndroidNativeSoundProvider` in
 * korlibs-audio-core-android, decompiled to check - see `.junie/guidelines.md`'s "delay between
 * landing and the landing sound" entry) uses `USAGE_GAME` for its own `AudioTrack`, which is why
 * jump/landing/footsteps were always audible while this pool wasn't - matching it here routes
 * every SFX bus through the same stream and the same volume control.
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
                        .setUsage(AudioAttributes.USAGE_GAME)
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

/**
 * Same SoundPool approach as [AndroidClickPlayer], generalised to hold several named clips in one
 * pool instead of always "ui_click" - a toast success and a toast error are both loaded here, keyed
 * by clip name, rather than each getting a dedicated pool. Not released on dispose: both clips are
 * provided once from [ui.NavigationRoot] for the app's lifetime, same as the click, so there is no
 * point in the pool's life where one clip's composable unmounts while the other is still needed.
 */
private object AndroidMenuClipPlayer {
    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()
    private val readyIds = mutableSetOf<Int>()

    fun prepare(context: Context, clip: String) {
        if (soundIds.containsKey(clip)) return
        try {
            val p = pool ?: SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
                .also { created ->
                    created.setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) readyIds += sampleId
                    }
                    pool = created
                }

            val rawId = context.resources.getIdentifier(clip, "raw", context.packageName)
            val id = if (rawId != 0) {
                p.load(context, rawId, 1)
            } else {
                context.assets.openFd("sfx/$clip.wav").use { fd ->
                    p.load(fd.fileDescriptor, fd.startOffset, fd.length, 1)
                }
            }
            soundIds[clip] = id
        } catch (t: Throwable) {
            println("[MenuSfx] Android load failed for $clip: ${t.message}")
        }
    }

    fun play(clip: String, volume: Float) {
        val id = soundIds[clip] ?: return
        if (id !in readyIds) return
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0.001f) return
        try {
            pool?.play(id, v, v, 1, 0, 1.0f)
        } catch (_: Throwable) {
        }
    }
}

@Composable
actual fun rememberMenuClip(clip: MenuClip, volume: Float): () -> Unit {
    val context = LocalContext.current

    DisposableEffect(context, clip) {
        AndroidMenuClipPlayer.prepare(context, clip.fileBaseName)
        onDispose { }
    }

    return remember(volume, clip) { { AndroidMenuClipPlayer.play(clip.fileBaseName, volume) } }
}
