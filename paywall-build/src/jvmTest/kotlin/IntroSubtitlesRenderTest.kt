package com.infiltrate.test

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import com.infiltrate.ui.INTRO_SHOTS
import com.infiltrate.ui.IntroSubtitleLine
import com.infiltrate.ui.LocalAppLanguage
import com.infiltrate.ui.SettingsScreen
import com.infiltrate.ui.introShotAt
import com.infiltrate.ui.introVideoBoxFor
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the intro's subtitle overlay over stills of the video, and the Settings screen, to PNGs
 * - a look without launching the app. Opt-in: `RENDER_INTRO_SUBS=<dir>` where `<dir>/frames` holds
 * `frame_<seconds>.png` stills of intro.mp4 (ffmpeg -ss <s> -frames:v 1). A landscape phone box
 * (844x390dp, 2.5x) for the intro, the reference box (1560x720dp, 1.5x) for Settings.
 */
class IntroSubtitlesRenderTest {

    private fun render(scene: ImageComposeScene, out: File) {
        var image = scene.render(0L)
        for (frame in 1..30) {
            Thread.sleep(40)
            image = scene.render(frame * 40_000_000L)
        }
        out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    @Test
    fun renderIntroSubtitles() {
        val dir = System.getenv("RENDER_INTRO_SUBS") ?: return
        val cases = listOf("en" to 5.0, "fr" to 18.5, "de" to 50.0, "ja" to 5.0, "ar" to 18.5, "hi" to 50.0,
            // Persian: پ ی ک, the «» quotes, چ, گ
            "fa" to 5.0, "fa" to 18.5, "fa" to 50.0, "fa" to 46.5)
        for ((lang, t) in cases) {
            val bytes = File(dir, "frames/frame_$t.png").readBytes()
            val bitmap = org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            val scene = ImageComposeScene(width = 2110, height = 975, density = Density(2.5f)) {
                BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                    val w = maxWidth
                    val h = maxHeight
                    val box = introVideoBoxFor(w, h, INTRO_SHOTS[introShotAt(t)])
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .offset(x = box.offsetX, y = box.offsetY)
                                .requiredWidth(box.width)
                                .requiredHeight(box.height)
                        )
                        IntroSubtitleLine(t, lang, w, h, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
            render(scene, File(dir, "subs_${lang}_$t.png"))
        }
    }

    @Test
    fun renderSettingsWithSubtitlesRow() {
        val dir = System.getenv("RENDER_INTRO_SUBS") ?: return
        for (lang in listOf("en", "fr", "fa")) {
            val scene = ImageComposeScene(width = 2340, height = 1080, density = Density(1.5f)) {
                CompositionLocalProvider(LocalAppLanguage provides lang) {
                    SettingsScreen(
                        musicVolume = 0.8f,
                        sfxVolume = 1f,
                        language = lang,
                        onMusicVolumeChange = {},
                        onSfxVolumeChange = {},
                        onBackClicked = {}
                    )
                }
            }
            render(scene, File(dir, "settings_$lang.png"))
        }
    }
}
