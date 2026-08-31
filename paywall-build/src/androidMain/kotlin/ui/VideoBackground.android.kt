package com.infiltrate.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    AndroidView(
        modifier = modifier,
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
