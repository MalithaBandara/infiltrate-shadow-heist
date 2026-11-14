package test

import game.model.*
import kotlin.test.*

class LocalizationTest {

    private val allLanguages = listOf(
        "en", "fr", "it", "es", "pt-BR", "de", "id", "tr",
        "vi", "ru", "ja", "ko", "zh-TW", "hi", "ar", "fa", "th"
    )

    @Test
    fun testAllLanguagesSupportedInLevels() {
        val levels = LevelData.DEFAULT_LEVELS
        assertEquals(12, levels.size)

        for (lang in allLanguages) {
            for (level in levels) {
                val name = level.localizedName(lang)
                val desc = level.localizedDescription(lang)
                val obj = level.localizedObjectiveHint(lang)

                assertTrue(name.isNotEmpty(), "Level ${level.id} [$lang] name must not be empty")
                assertTrue(desc.isNotEmpty(), "Level ${level.id} [$lang] description must not be empty")
                assertTrue(obj.isNotEmpty(), "Level ${level.id} [$lang] objective must not be empty")

                if (lang != "en") {
                    assertTrue(desc.isNotEmpty(), "Level ${level.id} [$lang] description must exist")
                    assertTrue(obj.isNotEmpty(), "Level ${level.id} [$lang] objective must exist")
                }
            }
        }
    }

    @Test
    fun testAllLanguagesSupportedInTutorials() {
        val allSteps = LevelData.DEFAULT_LEVELS.flatMap { it.tutorialSteps } + LevelData.PUSH_STANCE_DEMO.tutorialSteps
        assertTrue(allSteps.isNotEmpty(), "There must be tutorial steps to verify")

        for (lang in allLanguages) {
            for (step in allSteps) {
                val callout = step.localizedCallout(lang)
                val title = step.localizedTitle(lang)
                val touch = step.localizedInstructionTouch(lang)
                val desktop = step.localizedInstructionDesktop(lang)

                assertTrue(callout.isNotEmpty(), "Step ${step.id} [$lang] callout must not be empty")
                assertTrue(title.isNotEmpty(), "Step ${step.id} [$lang] title must not be empty")
                assertTrue(touch.isNotEmpty(), "Step ${step.id} [$lang] touch instruction must not be empty")
                assertTrue(desktop.isNotEmpty(), "Step ${step.id} [$lang] desktop instruction must not be empty")
            }
        }
    }

    @Test
    fun testLanguageCodeVariants() {
        assertEquals("fr", Localization.langCode("fr"))
        assertEquals("fr", Localization.langCode("FR"))
        assertEquals("fr", Localization.langCode("fr-FR"))
        assertEquals("fr", Localization.langCode("fr-CA"))

        assertEquals("es", Localization.langCode("es"))
        assertEquals("es", Localization.langCode("es-ES"))
        assertEquals("es", Localization.langCode("es-419"))

        assertEquals("pt-BR", Localization.langCode("pt"))
        assertEquals("pt-BR", Localization.langCode("pt-BR"))
        assertEquals("pt-BR", Localization.langCode("pt_PT"))

        assertEquals("zh-TW", Localization.langCode("zh"))
        assertEquals("zh-TW", Localization.langCode("zh-TW"))
        assertEquals("zh-TW", Localization.langCode("zh-CN"))

        assertEquals("ja", Localization.langCode("ja"))
        assertEquals("ko", Localization.langCode("ko"))
        assertEquals("ru", Localization.langCode("ru"))
        assertEquals("de", Localization.langCode("de"))
        assertEquals("id", Localization.langCode("id"))
        assertEquals("tr", Localization.langCode("tr"))
        assertEquals("vi", Localization.langCode("vi"))
        assertEquals("hi", Localization.langCode("hi"))
        assertEquals("ar", Localization.langCode("ar"))
        assertEquals("fa", Localization.langCode("fa"))
        assertEquals("fa", Localization.langCode("fa-IR"))
        assertEquals("fa", Localization.langCode("fa_IR"))
        assertEquals("th", Localization.langCode("th"))

        assertEquals("en", Localization.langCode("en"))
        assertEquals("en", Localization.langCode("unknown"))

        assertTrue(Localization.isFrench("fr"))
        assertTrue(Localization.isFrench("FR"))
        assertTrue(Localization.isFrench("fr-FR"))
        assertFalse(Localization.isFrench("en"))
        assertFalse(Localization.isFrench("es"))
    }

    @Test
    fun testPowerupTranslationsAllLanguages() {
        val types = listOf(
            PowerupType.INVISIBILITY,
            PowerupType.NOISE_SUPPRESSION,
            PowerupType.LASER_SHIELD,
            PowerupType.REMOTE_TRIGGER,
            PowerupType.CHECKPOINTS
        )
        for (lang in allLanguages) {
            for (type in types) {
                val name = Localization.powerupName(type, lang)
                val desc = Localization.powerupDescription(type, lang)

                assertTrue(name.isNotEmpty(), "Powerup ${type.name} [$lang] name must not be empty")
                assertTrue(desc.isNotEmpty(), "Powerup ${type.name} [$lang] description must not be empty")
            }
            val mysteryName = Localization.mysteryGadgetName(lang)
            val mysteryDesc = Localization.mysteryGadgetDescription(lang)
            assertTrue(mysteryName.isNotEmpty(), "Mystery gadget [$lang] name must not be empty")
            assertTrue(mysteryDesc.isNotEmpty(), "Mystery gadget [$lang] description must not be empty")
        }
    }

    @Test
    fun testStoreAndMenuTranslationsAllLanguages() {
        for (lang in allLanguages) {
            assertTrue(Localization.play(lang).isNotEmpty(), "[$lang] play must not be empty")
            assertTrue(Localization.missions(lang).isNotEmpty(), "[$lang] missions must not be empty")
            assertTrue(Localization.store(lang).isNotEmpty(), "[$lang] store must not be empty")
            assertTrue(Localization.settings(lang).isNotEmpty(), "[$lang] settings must not be empty")
            assertTrue(Localization.theShipyard(lang).isNotEmpty(), "[$lang] theShipyard must not be empty")
            assertTrue(Localization.powerupsTab(lang).isNotEmpty(), "[$lang] powerupsTab must not be empty")
            assertTrue(Localization.coinsTab(lang).isNotEmpty(), "[$lang] coinsTab must not be empty")
            assertTrue(Localization.removeAdsTab(lang).isNotEmpty(), "[$lang] removeAdsTab must not be empty")
            assertTrue(Localization.paused(lang).isNotEmpty(), "[$lang] paused must not be empty")
            assertTrue(Localization.continueGame(lang).isNotEmpty(), "[$lang] continueGame must not be empty")
            assertTrue(Localization.retry(lang).isNotEmpty(), "[$lang] retry must not be empty")
            assertTrue(Localization.mainMenu(lang).isNotEmpty(), "[$lang] mainMenu must not be empty")
            assertTrue(Localization.nextMission(lang).isNotEmpty(), "[$lang] nextMission must not be empty")
            assertTrue(Localization.allClear(lang).isNotEmpty(), "[$lang] allClear must not be empty")
            assertTrue(Localization.connecting(lang).isNotEmpty(), "[$lang] connecting must not be empty")
            assertTrue(Localization.waitPlease(lang).isNotEmpty(), "[$lang] waitPlease must not be empty")
            assertTrue(Localization.bgMusicLevel(lang).isNotEmpty(), "[$lang] bgMusicLevel must not be empty")
            assertTrue(Localization.tacticalSfx(lang).isNotEmpty(), "[$lang] tacticalSfx must not be empty")
            assertTrue(Localization.defaultLeft(lang).isNotEmpty(), "[$lang] defaultLeft must not be empty")
            assertTrue(Localization.swappedRight(lang).isNotEmpty(), "[$lang] swappedRight must not be empty")
            assertTrue(Localization.aboutInfiltrate(lang).isNotEmpty(), "[$lang] aboutInfiltrate must not be empty")
            assertTrue(Localization.adPrivacyChoices(lang).isNotEmpty(), "[$lang] adPrivacyChoices must not be empty")
        }
    }

    @Test
    fun testProfileStorageLanguageSwitching() {
        val map = mutableMapOf<String, String>()
        val storage = MapBackedGameProfileStorage(
            getRaw = { map[it] },
            setRaw = { k, v -> map[k] = v }
        )

        assertEquals("en", storage.getProfile().language)
        storage.setLanguage("fr")
        assertEquals("fr", storage.getProfile().language)
        assertEquals("fr", map["user_language"])

        val level = LevelData.DEFAULT_LEVEL_1
        assertEquals("01: Arrivée nocturne", level.localizedName(storage.getProfile().language))

        storage.setLanguage("es")
        assertEquals("01: Llegada nocturna", level.localizedName(storage.getProfile().language))

        storage.setLanguage("ja")
        assertEquals("01: 夜陰の到着", level.localizedName(storage.getProfile().language))
    }

    @Test
    fun testSupportedLanguageForDeviceTags() {
        assertEquals("zh-TW", Localization.supportedLanguageFor("zh-Hant-TW"))
        assertEquals("zh-TW", Localization.supportedLanguageFor("zh-TW"))
        assertEquals("zh-TW", Localization.supportedLanguageFor("zh-Hans-CN"))
        assertEquals("ja", Localization.supportedLanguageFor("ja-JP"))
        assertEquals("ko", Localization.supportedLanguageFor("ko_KR"))
        assertEquals("pt-BR", Localization.supportedLanguageFor("pt-PT"))
        assertEquals("id", Localization.supportedLanguageFor("in-ID"))
        assertEquals("en", Localization.supportedLanguageFor("en-CA"))
        assertEquals("it", Localization.supportedLanguageFor("it-IT"))
        assertEquals("fa", Localization.supportedLanguageFor("fa"))
        assertEquals("fa", Localization.supportedLanguageFor("fa-IR"))
        assertEquals("fa", Localization.supportedLanguageFor("fa_IR"))
        assertEquals("fa", Localization.firstSupportedLanguage(listOf("fa-IR", "en-US")))
        assertEquals(null, Localization.supportedLanguageFor("hr-HR"))
        assertEquals(null, Localization.supportedLanguageFor(""))
        // Falls through unsupported preferences to the next one the game has.
        assertEquals("de", Localization.firstSupportedLanguage(listOf("nl-NL", "de-DE", "en-US")))
        assertEquals(null, Localization.firstSupportedLanguage(listOf("nl-NL", "sv-SE")))
    }

    /**
     * Persian is written with its own yeh (U+06CC) and keheh (U+06A9), not the Arabic ones
     * (U+064A, U+0643) that look alike in the middle of a word but not at its end.
     */
    @Test
    fun testPersianUsesPersianLettersNotArabicOnes() {
        val steps = LevelData.DEFAULT_LEVELS.flatMap { it.tutorialSteps }
        val strings = LevelData.DEFAULT_LEVELS.flatMap {
            listOf(it.localizedName("fa"), it.localizedDescription("fa"), it.localizedObjectiveHint("fa"))
        } + steps.flatMap {
            listOf(it.localizedCallout("fa"), it.localizedTitle("fa"), it.localizedInstructionTouch("fa"), it.localizedInstructionDesktop("fa"))
        } + IntroSubtitles.LINES.getValue("fa") + PowerupType.entries.flatMap {
            listOf(Localization.powerupName(it, "fa"), Localization.powerupDescription(it, "fa"))
        } + listOf(
            Localization.play("fa"), Localization.store("fa"), Localization.settings("fa"),
            Localization.powerupsTab("fa"), Localization.confirmResetDesc("fa"), Localization.loading("fa")
        )
        for (s in strings) {
            assertFalse(s.contains('\u064A') || s.contains('\u0643'), "Arabic yeh/kaf in Persian: $s")
        }
        assertTrue(strings.any { it.contains('\u06CC') } && strings.any { it.contains('\u06A9') })
        assertTrue(Localization.store("fa") != Localization.store("ar"))
    }

    @Test
    fun testFirstLaunchUsesSupportedDeviceLanguageAndSavesIt() {
        val map = mutableMapOf<String, String>()
        val storage = MapBackedGameProfileStorage(
            getRaw = { map[it] },
            setRaw = { k, v -> map[k] = v },
            systemLanguageTags = { listOf("zh-Hant-TW", "en-US") }
        )
        assertEquals("zh-TW", storage.getProfile().language)
        assertEquals("zh-TW", map["user_language"])

        // A later launch with a different device language keeps the saved one.
        val reloaded = MapBackedGameProfileStorage(
            getRaw = { map[it] },
            setRaw = { k, v -> map[k] = v },
            systemLanguageTags = { listOf("ja-JP") }
        )
        assertEquals("zh-TW", reloaded.getProfile().language)
    }

    @Test
    fun testDeviceLanguageNeverOverridesSavedChoice() {
        val map = mutableMapOf("user_language" to "en")
        val storage = MapBackedGameProfileStorage(
            getRaw = { map[it] },
            setRaw = { k, v -> map[k] = v },
            systemLanguageTags = { listOf("ko-KR") }
        )
        assertEquals("en", storage.getProfile().language)
    }

    @Test
    fun testUnsupportedDeviceLanguageStaysEnglishWithoutSaving() {
        val map = mutableMapOf<String, String>()
        val storage = MapBackedGameProfileStorage(
            getRaw = { map[it] },
            setRaw = { k, v -> map[k] = v },
            systemLanguageTags = { listOf("hr-HR") }
        )
        assertEquals("en", storage.getProfile().language)
        assertEquals(null, map["user_language"])
    }

    @Test
    fun testFirstClearOfLevel1SendsTutorialComplete() {
        val events = GameAnalytics.levelCompleteEvents("level_1", stars = 3, distinctLevelsCleared = 1)
        assertEquals(listOf("level_complete", "tutorial_complete", "levels_cleared_1"), events.map { it.name })
        assertEquals(
            mapOf("level_id" to "level_1", "level_number" to 1L, "stars" to 3L, "first_clear" to 1L),
            events[0].params
        )
        // Replaying level 1 is just a level_complete.
        assertEquals(listOf("level_complete"), GameAnalytics.levelCompleteEvents("level_1", 2, null).map { it.name })
    }

    @Test
    fun testEveryNewDistinctClearSendsItsCount() {
        assertEquals(
            listOf("level_complete", "levels_cleared_3"),
            GameAnalytics.levelCompleteEvents("level_3", 1, distinctLevelsCleared = 3).map { it.name }
        )
        assertEquals(
            listOf("level_complete", "levels_cleared_5"),
            GameAnalytics.levelCompleteEvents("level_6", 1, distinctLevelsCleared = 5).map { it.name }
        )
        assertEquals(
            listOf("level_complete", "levels_cleared_4"),
            GameAnalytics.levelCompleteEvents("level_4", 1, distinctLevelsCleared = 4).map { it.name }
        )
        assertEquals(
            listOf("level_complete", "levels_cleared_12"),
            GameAnalytics.levelCompleteEvents("level_12", 3, distinctLevelsCleared = 12).map { it.name }
        )
        // A replay - the count did not change - sends no count.
        assertEquals(listOf("level_complete"), GameAnalytics.levelCompleteEvents("level_3", 1, null).map { it.name })
    }

    @Test
    fun testAnalyticsSinkReceivesEventsAndNeverThrows() {
        val logged = mutableListOf<String>()
        GameAnalytics.sink = object : GameAnalyticsSink {
            override fun logEvent(name: String, params: Map<String, Any>) {
                logged += name
                error("sink failure must not reach gameplay")
            }
        }
        try {
            GameAnalytics.levelStart("level_2")
            GameAnalytics.levelFail("level_2", 12.7)
        } finally {
            GameAnalytics.sink = null
        }
        assertEquals(listOf("level_start", "level_fail"), logged)
        assertEquals(12L, GameAnalytics.levelFailEvents("level_2", 12.7)[0].params["seconds_played"])
    }

    @Test
    fun testSpecificFrenchTranslations() {
        assertEquals("PLAY", Localization.play("en"))
        assertEquals("JOUER", Localization.play("fr"))
        assertEquals("STORE", Localization.store("en"))
        assertEquals("BOUTIQUE", Localization.store("fr"))
        assertEquals("SETTINGS", Localization.settings("en"))
        assertEquals("PARAMÈTRES", Localization.settings("fr"))
        assertEquals("+500 COINS GRANTED", Localization.coinsGranted(500, "en"))
        assertEquals("+500 PIÈCES ACCORDÉES", Localization.coinsGranted(500, "fr"))
        assertEquals("UNABLE TO OPEN LINK", Localization.unableToOpenLink("en"))
        assertEquals("IMPOSSIBLE D'OUVRIR LE LIEN", Localization.unableToOpenLink("fr"))
    }

    @Test
    fun testSpecificSpanishTranslations() {
        assertEquals("JUGAR", Localization.play("es"))
        assertEquals("TIENDA", Localization.store("es"))
        assertEquals("AJUSTES", Localization.settings("es"))
        assertEquals("01: Llegada nocturna", Localization.levelName("level_1", "es"))
    }

    @Test
    fun testSpecificItalianTranslations() {
        assertEquals("it", Localization.langCode("it"))
        assertEquals("it", Localization.langCode("it-IT"))
        assertEquals("it", Localization.langCode("IT_ch"))
        assertEquals("GIOCA", Localization.play("it"))
        assertEquals("NEGOZIO", Localization.store("it"))
        assertEquals("IMPOSTAZIONI", Localization.settings("it"))
        assertEquals("01: Arrivo notturno", Localization.levelName("level_1", "it"))
        assertEquals("+500 MONETE ACCREDITATE", Localization.coinsGranted(500, "it"))
        assertEquals("ACQUISTA IL PASS A VITA — 2,99 $", Localization.purchaseLifetimePass("it"))
    }

    /** Every string with an Italian entry differs from English, so none fell through to `else`. */
    @Test
    fun testItalianIsNotEnglish() {
        assertNotEquals(Localization.play("en"), Localization.play("it"))
        assertNotEquals(Localization.powerupName(PowerupType.INVISIBILITY, "en"), Localization.powerupName(PowerupType.INVISIBILITY, "it"))
        for (level in LevelData.DEFAULT_LEVELS) {
            assertNotEquals(level.localizedDescription("en"), level.localizedDescription("it"), level.id)
            assertNotEquals(level.localizedObjectiveHint("en"), level.localizedObjectiveHint("it"), level.id)
        }
    }

    @Test
    fun testSpecificJapaneseTranslations() {
        assertEquals("プレイ", Localization.play("ja"))
        assertEquals("ショップ", Localization.store("ja"))
        assertEquals("設定", Localization.settings("ja"))
        assertEquals("01: 夜陰の到着", Localization.levelName("level_1", "ja"))
    }
}
