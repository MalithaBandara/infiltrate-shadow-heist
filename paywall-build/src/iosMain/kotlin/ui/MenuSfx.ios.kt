package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * iOS click via a small pool of AVAudioPlayers, reading `ui_click.wav` from the app bundle the
 * same way [MenuMusic] reads `mainmenu.mp3` - the file is shipped by `ios-shell/Resources`,
 * which project.yml already adds as a resources build phase.
 *
 * A pool rather than one player because AVAudioPlayer cannot overlap itself: calling `play()`
 * again while it is still sounding restarts it, which clips the previous tap. Four voices is
 * enough for any realistic tap rate and costs four decoded copies of an 0.08s clip.
 *
 * As in [MenuMusic], no AVAudioSession category is set - the default already does what UI
 * feedback wants (silent with the ringer switch, out of the way of calls), and this file cannot
 * be compiled from a Windows dev machine, so it sticks to the smallest API surface that works.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private object IosClickPlayer {
    private const val VOICES = 4
    private var players: List<AVAudioPlayer> = emptyList()
    private var next = 0
    private var loadedKey: String? = null

    fun prepare(url: NSURL, key: String) {
        if (loadedKey == key && players.isNotEmpty()) return
        players = (0 until VOICES).mapNotNull {
            try {
                AVAudioPlayer(contentsOfURL = url, error = null).also { it.prepareToPlay() }
            } catch (_: Throwable) {
                null
            }
        }
        loadedKey = if (players.isEmpty()) null else key
    }

    fun play(volume: Float) {
        if (players.isEmpty()) return
        val p = players[next % players.size]
        next = (next + 1) % players.size
        try {
            p.volume = volume.coerceIn(0f, 1f)
            // Rewind explicitly: a voice that finished still reports its play head at the end,
            // and play() alone would then produce nothing.
            p.currentTime = 0.0
            p.play()
        } catch (_: Throwable) {
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberUiClick(volume: Float): () -> Unit {
    val url = remember {
        NSBundle.mainBundle.URLForResource(name = "ui_click", withExtension = "wav")
    }
    remember(url) {
        if (url != null) IosClickPlayer.prepare(url, "ui_click.wav")
        Unit
    }
    // volume is read through the returned lambda, not captured into the pool, so the Settings
    // slider affects the next tap without this composable having to recompose the pool.
    return { IosClickPlayer.play(volume) }
}

/**
 * Same pooled-AVAudioPlayer approach as [IosClickPlayer], generalised to hold a pool per named
 * clip instead of always "ui_click.wav".
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private object IosMenuClipPlayer {
    private const val VOICES = 4
    private val pools = mutableMapOf<String, List<AVAudioPlayer>>()
    private val nextIndex = mutableMapOf<String, Int>()

    fun prepare(clip: String, url: NSURL) {
        if (pools.containsKey(clip)) return
        val players = (0 until VOICES).mapNotNull {
            try {
                AVAudioPlayer(contentsOfURL = url, error = null).also { it.prepareToPlay() }
            } catch (_: Throwable) {
                null
            }
        }
        if (players.isNotEmpty()) {
            pools[clip] = players
            nextIndex[clip] = 0
        }
    }

    fun play(clip: String, volume: Float) {
        val players = pools[clip] ?: return
        val i = nextIndex[clip] ?: 0
        val p = players[i % players.size]
        nextIndex[clip] = (i + 1) % players.size
        try {
            p.volume = volume.coerceIn(0f, 1f)
            p.currentTime = 0.0
            p.play()
        } catch (_: Throwable) {
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberMenuClip(clip: MenuClip, volume: Float): () -> Unit {
    val url = remember(clip) {
        NSBundle.mainBundle.URLForResource(name = clip.fileBaseName, withExtension = "wav")
    }
    remember(url) {
        if (url != null) IosMenuClipPlayer.prepare(clip.fileBaseName, url)
        Unit
    }
    return { IosMenuClipPlayer.play(clip.fileBaseName, volume) }
}
