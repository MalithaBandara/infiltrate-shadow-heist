package test

import game.model.*
import kotlin.test.*

/** The intro video's subtitles and their setting - see IntroSubtitles, GameProfile.subtitlesOn. */
class IntroSubtitlesTest {

    private val allLanguages = listOf(
        "en", "fr", "it", "es", "pt-BR", "de", "id", "tr",
        "vi", "ru", "ja", "ko", "zh-TW", "hi", "ar", "fa", "th"
    )

    @Test
    fun everyLanguageHasEveryLine() {
        assertEquals(allLanguages.toSet(), IntroSubtitles.LINES.keys, "one entry per supported language")
        for (lang in allLanguages) {
            val lines = IntroSubtitles.LINES.getValue(lang)
            assertEquals(IntroSubtitles.CUES.size, lines.size, "[$lang] one line per cue")
            lines.forEachIndexed { i, line -> assertTrue(line.isNotBlank(), "[$lang] line $i") }
            assertTrue(Localization.subtitlesSetting(lang).isNotBlank(), "[$lang] setting label")
            assertTrue(Localization.subtitlesDesc(lang).isNotBlank(), "[$lang] setting description")
            assertTrue(Localization.settingOn(lang).isNotBlank(), "[$lang] on")
            assertTrue(Localization.settingOff(lang).isNotBlank(), "[$lang] off")
        }
    }

    @Test
    fun translationsAreNotEnglishLeftovers() {
        val english = IntroSubtitles.LINES.getValue("en")
        for (lang in allLanguages - "en") {
            val lines = IntroSubtitles.LINES.getValue(lang)
            lines.forEachIndexed { i, line -> assertNotEquals(english[i], line, "[$lang] line $i is still English") }
        }
    }

    @Test
    fun cuesRunInOrderInsideTheVideoWithoutOverlapping() {
        var lastEnd = 0.0
        for ((i, c) in IntroSubtitles.CUES.withIndex()) {
            assertTrue(c.start >= lastEnd, "cue $i starts after the one before ends")
            assertTrue(c.end > c.start + 1.0, "cue $i is on screen for over a second")
            lastEnd = c.end
        }
        assertTrue(lastEnd <= 59.5, "the last cue ends inside the 59.5s video")
    }

    @Test
    fun cueAtFindsTheLineBeingSpoken() {
        assertNull(IntroSubtitles.cueAt(1.0), "nothing before the narration starts")
        assertEquals(0, IntroSubtitles.cueAt(5.0))
        assertNull(IntroSubtitles.cueAt(10.0), "nothing in a pause")
        assertEquals("They called it the Copier.", IntroSubtitles.text(IntroSubtitles.cueAt(18.5)!!, "en"))
        assertEquals("On l'appelait le Copieur.", IntroSubtitles.text(IntroSubtitles.cueAt(18.5)!!, "fr"))
        assertEquals(13, IntroSubtitles.cueAt(58.0))
        assertEquals(IntroSubtitles.text(0, "en"), IntroSubtitles.text(0, "xx"), "an unknown language reads English")
    }

    @Test
    fun subtitlesAreOffForEnglishAndOnForEveryOtherLanguageByDefault() {
        assertFalse(GameProfile(language = "en").subtitlesOn())
        for (lang in allLanguages - "en") assertTrue(GameProfile(language = lang).subtitlesOn(), "[$lang] on by default")
    }

    @Test
    fun aChoiceIsSavedAndBeatsTheLanguageDefault() {
        val store = HashMap<String, String>()
        fun storage() = MapBackedGameProfileStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v }, systemLanguageTags = { emptyList() })

        val first = storage()
        first.setLanguage("fr")
        assertTrue(first.getProfile().subtitlesOn(), "French starts on")
        assertNull(storage().getProfile().subtitles, "no choice saved until one is made")

        first.setSubtitles(false)
        assertFalse(storage().getProfile().subtitlesOn(), "turned off, and it survives a relaunch")

        val again = storage()
        again.setLanguage("en")
        again.setSubtitles(true)
        assertTrue(storage().getProfile().subtitlesOn(), "turned on in English")
    }

    @Test
    fun resetProgressGoesBackToTheLanguageDefault() {
        val store = HashMap<String, String>()
        val s = MapBackedGameProfileStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v }, systemLanguageTags = { emptyList() })
        s.setSubtitles(true)
        s.resetProgress()
        assertNull(s.getProfile().subtitles)
        assertFalse(s.getProfile().subtitlesOn(), "English after a reset: off")
    }
}
