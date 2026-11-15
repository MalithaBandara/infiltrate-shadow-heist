package test

import game.model.LevelData
import game.model.Localization
import game.model.localizedCallout
import game.model.localizedName
import game.model.localizedObjectiveHint
import game.scene.GameText
import game.scene.SceneAssets
import game.scene.renderPlatformText
import game.scene.text
import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.image.font.BaseTtfFont
import korlibs.image.font.Font
import korlibs.image.format.PNG
import korlibs.image.format.writeBitmap
import korlibs.io.async.suspendTest
import korlibs.io.file.std.localVfs
import korlibs.io.lang.Environment
import korlibs.korge.view.Container
import korlibs.korge.view.Text
import kotlin.test.*

/**
 * In-game text in every language. The game's two fonts are Latin only and KorGE draws a character a
 * font lacks as nothing, so before GameText eight languages showed no in-game text at all - a
 * zero-width, empty view. Here: a string the font covers is measured exactly as KorGE's own Text
 * (nothing changes for Latin languages), and every other string comes out with a width, the font's
 * own line height, and pixels.
 *
 * `RENDER_GAME_TEXT=<dir> ./gradlew jvmTest --tests '*GameTextTest'` also writes each language's
 * strings as PNGs (white on black), to look at what the platform drew.
 */
class GameTextTest {

    private val allLanguages = listOf(
        "en", "fr", "it", "es", "pt-BR", "de", "id", "tr",
        "vi", "ru", "ja", "ko", "zh-TW", "hi", "ar", "fa", "th"
    )

    private fun Font.covers(text: String): Boolean =
        text.all { it <= ' ' || (this as BaseTtfFont).getGlyphByCodePoint(it.code) != null }

    /** The strings a level shows in [lang], with the font and size the scene gives each. */
    private fun samples(lang: String, bebas: Font, handwritten: Font): List<Triple<String, Font, Double>> {
        val level = LevelData.DEFAULT_LEVELS.first()
        return listOf(
            Triple(Localization.loading(lang), bebas, 14.0),
            Triple(level.localizedName(lang).uppercase(), bebas, 14.0),
            Triple(level.localizedObjectiveHint(lang).uppercase(), bebas, 12.5),
            Triple(Localization.paused(lang), bebas, 52.0),
            Triple(level.tutorialSteps.first().localizedCallout(lang), handwritten, 28.0),
        )
    }

    @Test
    fun textTheFontCoversIsMeasuredExactlyAsKorgesOwn() = suspendTest {
        val bebas = SceneAssets.font("BebasNeue-Regular.ttf")
        val handwritten = SceneAssets.font("handwritten.ttf")
        for (lang in listOf("en", "fr", "it", "es", "pt-BR", "de", "id", "tr")) {
            for ((string, font, size) in samples(lang, bebas, handwritten)) {
                assertTrue(font.covers(string), "[$lang] '$string' should be plain Latin")
                val mine = Container().text(string, textSize = size, font = font)
                val korges = Text(string, size, Colors.WHITE, font)
                assertTrue(mine is GameText)
                assertEquals(korges.getLocalBounds(), mine.getLocalBounds(), "[$lang] '$string'")
            }
        }
    }

    @Test
    fun textTheFontLacksIsDrawnByThePlatform() = suspendTest {
        val bebas = SceneAssets.font("BebasNeue-Regular.ttf")
        val handwritten = SceneAssets.font("handwritten.ttf")
        val dumpDir = Environment["RENDER_GAME_TEXT"]
        var platformDrawn = 0
        for (lang in allLanguages) {
            for ((index, sample) in samples(lang, bebas, handwritten).withIndex()) {
                val (string, font, size) = sample
                val view = Container().text(string, textSize = size, font = font)
                val bounds = view.getLocalBounds()
                val lineBox = Text("X", size, Colors.WHITE, font).getLocalBounds().height
                assertTrue(bounds.width > 0.0, "[$lang] '$string' has no width")
                assertEquals(lineBox, bounds.height, 0.001, "[$lang] '$string' keeps the font's line height")
                if (font.covers(string)) continue

                platformDrawn++
                val drawn = assertNotNull(renderPlatformText(string, size * 3.0, bold = font === bebas, lang), "[$lang] '$string'")
                val pixels = drawn.bitmap.toBMP32()
                assertTrue(pixels.ints.any { RGBA(it).a > 128 }, "[$lang] '$string' drew nothing")
                // What KorGE's own path measured for the same string: nothing, which is the bug.
                assertTrue(bounds.width > Text(string, size, Colors.WHITE, font).getLocalBounds().width, "[$lang] '$string'")
                if (dumpDir != null) {
                    val out = Bitmap32(pixels.width, pixels.height, premultiplied = false)
                    for (i in out.ints.indices) {
                        val a = RGBA(pixels.ints[i]).a
                        out.ints[i] = RGBA(a, a, a, 255).value
                    }
                    localVfs(dumpDir)["${lang}_$index.png"].writeBitmap(out, PNG)
                }
            }
        }
        // ja, ko, zh-TW, hi, ar, fa, th and ru at the least have strings Bebas cannot draw.
        assertTrue(platformDrawn >= 8 * 3, "only $platformDrawn strings went to the platform")
    }
}
