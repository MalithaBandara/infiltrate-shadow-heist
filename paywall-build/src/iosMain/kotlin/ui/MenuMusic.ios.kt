package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * iOS music via AVAudioPlayer, reading from the app bundle the same way
 * [LoopingVideoBackground] reads `bg1080p.mp4` - the track is shipped by `ios-shell/Resources`,
 * which project.yml already adds as a resources build phase.
 *
 * No AVAudioSession category is set. The default (SoloAmbient) already does what menu music
 * wants - it goes quiet with the ringer switch and stops for phone calls - and this file cannot
 * be compiled from a Windows dev machine, so it deliberately sticks to the smallest API surface
 * that does the job rather than spending unverifiable calls on a category the default covers.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private object IosMusicPlayer {
    private var player: AVAudioPlayer? = null

    fun start(url: NSURL, volume: Float) {
        if (player != null) {
            setVolume(volume)
            player?.play()
            return
        }
        val created = AVAudioPlayer(contentsOfURL = url, error = null)
        // -1 loops forever. Converted rather than written as a literal because numberOfLoops is
        // an NSInteger, whose Kotlin width differs by architecture.
        created.numberOfLoops = (-1).convert()
        created.volume = volume.coerceIn(0f, 1f)
        created.prepareToPlay()
        created.play()
        player = created
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    fun stop() {
        player?.stop()
        player = null
    }
}

@Composable
actual fun MenuMusic(
    trackName: String,
    trackExtension: String,
    volume: Float
) {
    val url = remember(trackName, trackExtension) {
        NSBundle.mainBundle.URLForResource(name = trackName, withExtension = trackExtension)
    }

    DisposableEffect(url) {
        if (url != null) IosMusicPlayer.start(url, volume)
        onDispose { IosMusicPlayer.stop() }
    }

    LaunchedEffect(volume) { IosMusicPlayer.setVolume(volume) }
}
