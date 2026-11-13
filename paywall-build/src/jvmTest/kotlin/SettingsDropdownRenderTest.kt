package com.infiltrate.test

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.infiltrate.ui.LocalAppLanguage
import com.infiltrate.ui.SettingsScreen
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Settings on a landscape phone (844x390dp at 2.5x) with the language dropdown opened by a tap,
 * rendered to PNGs. Opt-in: `RENDER_SETTINGS_DROPDOWN=<dir>`; `RENDER_TAP=x,y` (pixels) is where
 * the language pill is - find it in the first, closed render.
 */
class SettingsDropdownRenderTest {

    @Test
    fun renderLanguageDropdownOnAPhone() {
        val dir = System.getenv("RENDER_SETTINGS_DROPDOWN") ?: return
        val tap = System.getenv("RENDER_TAP")?.split(',')?.map { it.trim().toFloat() }
        val scene = ImageComposeScene(width = 2110, height = 975, density = Density(2.5f)) {
            CompositionLocalProvider(LocalAppLanguage provides "en") {
                SettingsScreen(musicVolume = 0.8f, sfxVolume = 1f, language = "en", onMusicVolumeChange = {}, onSfxVolumeChange = {}, onBackClicked = {})
            }
        }
        var t = 0L
        fun frames(n: Int): org.jetbrains.skia.Image {
            var img = scene.render(t)
            repeat(n) { Thread.sleep(40); t += 40_000_000L; img = scene.render(t) }
            return img
        }
        File(dir, "settings_phone_closed.png").writeBytes(frames(25).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        if (tap != null) {
            val at = Offset(tap[0], tap[1])
            scene.sendPointerEvent(PointerEventType.Press, at)
            scene.sendPointerEvent(PointerEventType.Release, at)
            File(dir, "settings_phone_dropdown.png").writeBytes(frames(15).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        scene.close()
    }
}
