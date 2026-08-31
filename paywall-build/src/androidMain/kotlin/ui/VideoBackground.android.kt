package com.infiltrate.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun LoopingVideoBackground(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    fallbackDrawable: DrawableResource
) {
    BoxWithConstraints(
        modifier = modifier.background(Color.Black)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val videoAspect = 16f / 9f
        val screenAspect = if (screenH.value > 0) screenW.value / screenH.value else videoAspect

        val (targetW, targetH) = if (screenAspect > videoAspect) {
            (screenH * videoAspect) to screenH
        } else {
            screenW to (screenW / videoAspect)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            AndroidView(
                modifier = Modifier
                    .width(targetW)
                    .height(targetH),
                factory = { context ->
                    val videoView = VideoView(context)
                    videoView.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    val rawResId = context.resources.getIdentifier(videoName, "raw", context.packageName)
                    if (rawResId != 0) {
                        val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
                        videoView.setVideoURI(uri)
                        videoView.setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            videoView.start()
                        }
                        videoView.setOnCompletionListener {
                            videoView.start()
                        }
                    }
                    videoView
                },
                update = { videoView ->
                    if (!videoView.isPlaying) {
                        videoView.start()
                    }
                }
            )
        }
    }
}
