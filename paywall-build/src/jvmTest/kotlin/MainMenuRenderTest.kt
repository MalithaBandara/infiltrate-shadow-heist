package com.infiltrate.test

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.infiltrate.ui.LocalAppLanguage
import com.infiltrate.ui.MainMenuScreen
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the main menu to PNGs, one per language, for a look at the button labels without
 * launching the app. Opt-in: `RENDER_MAIN_MENU=<dir> RENDER_LANGS=fr,de ./gradlew jvmTest
 * --tests '*MainMenuRenderTest'`. The reference phone box (1560x720dp) at density 1.5. The
 * background video may or may not have a frame yet - the buttons are what this is for.
 */
class MainMenuRenderTest {

    @Test
    fun renderMainMenu() {
        val dir = System.getenv("RENDER_MAIN_MENU") ?: return
        val langs = (System.getenv("RENDER_LANGS") ?: "en,fr,de").split(',').map { it.trim() }
        File(dir).mkdirs()
        for (lang in langs) {
            val scene = ImageComposeScene(width = 2340, height = 1080, density = Density(1.5f)) {
                CompositionLocalProvider(LocalAppLanguage provides lang) {
                    MainMenuScreen(onPlayClicked = {}, onMissionsClicked = {}, onStoreClicked = {}, onSettingsClicked = {})
                }
            }
            // Resources (button art, font, logo) arrive over a few frames.
            var image = scene.render(0L)
            for (frame in 1..40) {
                Thread.sleep(50)
                image = scene.render(frame * 50_000_000L)
            }
            File(dir, "main_menu_$lang.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
