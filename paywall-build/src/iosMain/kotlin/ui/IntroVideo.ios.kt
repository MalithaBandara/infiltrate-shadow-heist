package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.setVolume
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IntroPlayerView @OverrideInit constructor(
    frame: CValue<CGRect>
) : UIView(frame) {
    var playerLayer: AVPlayerLayer? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = bounds
    }
}

private class IntroFlags {
    var finished = false
}

@OptIn(ExperimentalForeignApi::class)
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

    val bundleUrl = remember(videoName, videoExtension) {
        NSBundle.mainBundle.URLForResource(name = videoName, withExtension = videoExtension)
    }
    if (bundleUrl == null) {
        LaunchedEffect(Unit) { finish() }
        return
    }

    val playerItem = remember(bundleUrl) { AVPlayerItem(uRL = bundleUrl) }
    val player = remember(playerItem) { AVPlayer(playerItem = playerItem) }
    val flags = remember { IntroFlags() }

    fun end() {
        if (flags.finished) return
        flags.finished = true
        player.pause()
        finish()
    }

    DisposableEffect(player, playerItem) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(
            center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, playerItem, null) { _ -> end() },
            center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, playerItem, null) { _ -> end() },
            // Backgrounded mid-intro: pause, and pick up where it was on the way back.
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, null) { _ ->
                player.pause()
            },
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, null) { _ ->
                if (!flags.finished) player.play()
            }
        )
        player.play()
        onDispose {
            flags.finished = true
            player.pause()
            observers.forEach { center.removeObserver(it) }
        }
    }

    LaunchedEffect(player, volume) {
        player.setVolume(volume.coerceIn(0f, 1f))
    }

    LaunchedEffect(player) {
        while (!flags.finished) {
            withFrameNanos { }
            if (playerItem.status == AVPlayerItemStatusFailed) {
                end()
            } else if (!flags.finished) {
                val seconds = CMTimeGetSeconds(player.currentTime())
                if (!seconds.isNaN()) progress(seconds)
            }
        }
    }

    UIKitView(
        factory = {
            IntroPlayerView(frame = CGRectZero.readValue()).apply {
                // The box introVideoBoxFor gives this view already has the video's aspect.
                val layer = AVPlayerLayer.playerLayerWithPlayer(player).apply {
                    videoGravity = AVLayerVideoGravityResize
                    frame = bounds
                }
                this.playerLayer = layer
                this.layer.addSublayer(layer)
            }
        },
        update = { view ->
            view.playerLayer?.frame = view.bounds
        },
        modifier = modifier,
        // A native view takes the touches that land on it, and the video covers the whole screen,
        // so IntroSequence's tap-to-skip would never fire. Leave the touches to Compose.
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false)
    )
}
