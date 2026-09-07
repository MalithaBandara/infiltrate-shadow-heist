package com.infiltrate.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
        modifier = modifier.background(Color(0xFF0E1115))
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

        // Always render fallback drawable first so there is never a blank/black frame
        // or a transparent hole punched to views underneath.
        Image(
            painter = painterResource(fallbackDrawable),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            AndroidView(
                modifier = Modifier
                    .width(targetW)
                    .height(targetH),
                factory = { context ->
                    val textureView = TextureView(context)
                    textureView.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    var mediaPlayer: MediaPlayer? = null
                    var surface: Surface? = null

                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            val rawResId = context.resources.getIdentifier(videoName, "raw", context.packageName)
                            if (rawResId != 0) {
                                try {
                                    val s = Surface(surfaceTexture)
                                    surface = s
                                    val mp = MediaPlayer()
                                    mediaPlayer = mp
                                    val afd = context.resources.openRawResourceFd(rawResId)
                                    if (afd != null) {
                                        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                        afd.close()
                                    } else {
                                        val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
                                        mp.setDataSource(context, uri)
                                    }
                                    mp.setSurface(s)
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f)
                                    mp.setOnPreparedListener {
                                        try {
                                            it.start()
                                        } catch (_: Throwable) {}
                                    }
                                    mp.prepareAsync()
                                } catch (_: Throwable) {
                                }
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            try {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                            } catch (_: Throwable) {}
                            mediaPlayer = null
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                    textureView
                }
            )
        }
    }
}
