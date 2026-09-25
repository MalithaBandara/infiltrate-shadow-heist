package com.infiltrate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.layout.ContentScale
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.play
import platform.AVFoundation.pause
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setMuted
import platform.AVFoundation.setVolume
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PlayerContainerView @OverrideInit constructor(
    frame: CValue<CGRect>
) : UIView(frame) {
    var playerLayer: AVPlayerLayer? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer?.frame = bounds
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun LoopingVideoBackground(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    fallbackDrawable: DrawableResource
) {
    val bundleUrl = remember(videoName, videoExtension) {
        NSBundle.mainBundle.URLForResource(name = videoName, withExtension = videoExtension)
    }

    if (bundleUrl == null) {
        Image(
            painter = painterResource(fallbackDrawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = modifier
        )
        return
    }

    val playerItem = remember(bundleUrl) {
        AVPlayerItem(uRL = bundleUrl)
    }

    val player = remember(playerItem) {
        AVPlayer(playerItem = playerItem).apply {
            setVolume(0f)
            setMuted(true)
        }
    }

    DisposableEffect(player, playerItem) {
        val notificationCenter = NSNotificationCenter.defaultCenter
        val observer = notificationCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = playerItem,
            queue = null
        ) { _ ->
            player.seekToTime(time = kCMTimeZero.readValue())
            player.play()
        }

        player.play()

        onDispose {
            player.pause()
            notificationCenter.removeObserver(observer)
        }
    }

    val isAdActive = com.infiltrate.ads.AdAudioCoordinator.isAdActive
    LaunchedEffect(isAdActive) {
        if (isAdActive) {
            player.pause()
        } else {
            player.play()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds()
    ) {
        // ui/VideoBackground.kt: fill the height, keep the aspect, and slide it so the
        // silhouette stays clear of the trailing edge - a screen squarer than the video runs its
        // left side off the edge rather than leaving a black bar along the bottom.
        val box = videoBoxFor(maxWidth, maxHeight)

        // The placement lives on the child, not on this Box: a Box reports
        // max(minConstraint, childSize), so an oversized child makes the Box itself oversized and
        // there is nothing left for contentAlignment to align against. wrapContentSize pins the
        // wrapper back to the incoming constraints and places the overflowing child inside it.
        Box(modifier = Modifier.fillMaxSize()) {
            UIKitView(
                factory = {
                    PlayerContainerView(frame = CGRectZero.readValue()).apply {
                        val layer = AVPlayerLayer.playerLayerWithPlayer(player).apply {
                            videoGravity = AVLayerVideoGravityResizeAspectFill
                            frame = bounds
                        }
                        this.playerLayer = layer
                        this.layer.addSublayer(layer)
                    }
                },
                update = { view ->
                    view.playerLayer?.frame = view.bounds
                },
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset(x = box.offsetX)
                    .requiredWidth(box.width)
                    .requiredHeight(box.height)
            )
        }
    }
}
