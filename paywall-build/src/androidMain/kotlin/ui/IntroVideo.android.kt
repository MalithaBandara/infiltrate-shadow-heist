package com.infiltrate.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** One MediaPlayer for the intro, started once it is both prepared and has a surface. */
private class IntroPlayback(val player: MediaPlayer) {
    var prepared = false
    var hasSurface = false
    var finished = false
    var paused = false
    var surface: Surface? = null

    fun startIfReady() {
        if (prepared && hasSurface && !finished && !paused) {
            try { if (!player.isPlaying) player.start() } catch (_: Throwable) {}
        }
    }
}

@Composable
actual fun IntroVideoPlayer(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    volume: Float,
    onProgress: (seconds: Double) -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val finish by rememberUpdatedState(onFinished)
    val progress by rememberUpdatedState(onProgress)

    val playback = remember {
        val rawId = context.resources.getIdentifier(videoName, "raw", context.packageName)
        if (rawId == 0) {
            null
        } else {
            try {
                val mp = MediaPlayer()
                context.resources.openRawResourceFd(rawId).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                IntroPlayback(mp)
            } catch (t: Throwable) {
                println("[Intro] Android video failed to open: ${t.message}")
                null
            }
        }
    }

    if (playback == null) {
        LaunchedEffect(Unit) { finish() }
        return
    }

    DisposableEffect(playback) {
        val mp = playback.player
        mp.setOnPreparedListener {
            playback.prepared = true
            playback.startIfReady()
        }
        mp.setOnCompletionListener {
            playback.finished = true
            finish()
        }
        mp.setOnErrorListener { _, what, extra ->
            println("[Intro] Android playback error $what/$extra")
            playback.finished = true
            finish()
            true
        }
        try {
            mp.prepareAsync()
        } catch (t: Throwable) {
            playback.finished = true
            finish()
        }
        onDispose {
            playback.finished = true
            try { mp.stop() } catch (_: Throwable) {}
            try { mp.release() } catch (_: Throwable) {}
            playback.surface?.release()
            playback.surface = null
        }
    }

    LaunchedEffect(playback, volume) {
        val v = volume.coerceIn(0f, 1f)
        try { playback.player.setVolume(v, v) } catch (_: Throwable) {}
    }

    // Backgrounded mid-intro: pause, and pick up where it was on the way back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    playback.paused = true
                    try { if (playback.player.isPlaying) playback.player.pause() } catch (_: Throwable) {}
                }
                Lifecycle.Event.ON_RESUME -> {
                    playback.paused = false
                    playback.startIfReady()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playback) {
        while (!playback.finished) {
            withFrameNanos { }
            if (playback.prepared && !playback.finished) {
                val ms = try { playback.player.currentPosition } catch (_: Throwable) { 0 }
                progress(ms / 1000.0)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        if (playback.finished) return
                        try {
                            val s = Surface(texture)
                            playback.surface = s
                            playback.player.setSurface(s)
                            playback.hasSurface = true
                            playback.startIfReady()
                        } catch (_: Throwable) {}
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        playback.hasSurface = false
                        if (!playback.finished) {
                            try { playback.player.setSurface(null) } catch (_: Throwable) {}
                        }
                        playback.surface?.release()
                        playback.surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                }
            }
        }
    )
}
