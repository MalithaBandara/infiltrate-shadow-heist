package test

import game.model.*
import kotlin.test.*

class LocalizationTest {

    @Test
    fun testAllLevelsHaveFrenchTranslations() {
        val levels = LevelData.DEFAULT_LEVELS
        assertEquals(12, levels.size)

        for (level in levels) {
            val enName = level.localizedName("en")
            val frName = level.localizedName("fr")
            val enDesc = level.localizedDescription("en")
            val frDesc = level.localizedDescription("fr")
            val enObj = level.localizedObjectiveHint("en")
            val frObj = level.localizedObjectiveHint("fr")

            assertTrue(enName.isNotEmpty(), "Level ${level.id} English name must not be empty")
            assertTrue(frName.isNotEmpty(), "Level ${level.id} French name must not be empty")
            if (level.id != "level_9") {
                assertNotEquals(enName, frName, "Level ${level.id} French name should differ from English name")
            }

            assertTrue(enDesc.isNotEmpty(), "Level ${level.id} English description must not be empty")
            assertTrue(frDesc.isNotEmpty(), "Level ${level.id} French description must not be empty")
            assertNotEquals(enDesc, frDesc, "Level ${level.id} French description must differ from English")

            assertTrue(enObj.isNotEmpty(), "Level ${level.id} English objective must not be empty")
            assertTrue(frObj.isNotEmpty(), "Level ${level.id} French objective must not be empty")
            assertNotEquals(enObj, frObj, "Level ${level.id} French objective must differ from English")
        }
    }

    @Test
    fun testFrenchLanguageCodeVariants() {
        assertTrue(Localization.isFrench("fr"))
        assertTrue(Localization.isFrench("FR"))
        assertTrue(Localization.isFrench("fr-FR"))
        assertTrue(Localization.isFrench("fr-CA"))
        assertFalse(Localization.isFrench("en"))
        assertFalse(Localization.isFrench("de"))
        assertFalse(Localization.isFrench("es"))
    }

    @Test
    fun testPowerupTranslations() {
        val types = listOf(
            PowerupType.INVISIBILITY,
            PowerupType.NOISE_SUPPRESSION,
            PowerupType.LASER_SHIELD,
            PowerupType.REMOTE_TRIGGER,
            PowerupType.CHECKPOINTS
        )
        for (type in types) {
            val enName = Localization.powerupName(type, "en")
            val frName = Localization.powerupName(type, "fr")
            val enDesc = Localization.powerupDescription(type, "en")
            val frDesc = Localization.powerupDescription(type, "fr")

            assertTrue(enName.isNotEmpty())
            assertTrue(frName.isNotEmpty())
            assertNotEquals(enName, frName)

            assertTrue(enDesc.isNotEmpty())
            assertTrue(frDesc.isNotEmpty())
            assertNotEquals(enDesc, frDesc)
        }
    }

    @Test
    fun testStoreAndMenuTranslations() {
        assertEquals("PLAY", Localization.play("en"))
        assertEquals("JOUER", Localization.play("fr"))

        assertEquals("STORE", Localization.store("en"))
        assertEquals("BOUTIQUE", Localization.store("fr"))

        assertEquals("SETTINGS", Localization.settings("en"))
        assertEquals("PARAMÈTRES", Localization.settings("fr"))

        assertEquals("THE SHIPYARD", Localization.theShipyard("en"))
        assertEquals("LE CHANTIER NAVAL", Localization.theShipyard("fr"))

        assertEquals("POWER-UPS", Localization.powerupsTab("en"))
        assertEquals("GADGETS", Localization.powerupsTab("fr"))

        assertEquals("COINS", Localization.coinsTab("en"))
        assertEquals("PIÈCES", Localization.coinsTab("fr"))

        assertEquals("REMOVE ADS", Localization.removeAdsTab("en"))
        assertEquals("SUPPRIMER LES PUBS", Localization.removeAdsTab("fr"))

        assertEquals("PAUSED", Localization.paused("en"))
        assertEquals("PAUSE", Localization.paused("fr"))

        assertEquals("CONTINUE", Localization.continueGame("en"))
        assertEquals("CONTINUER", Localization.continueGame("fr"))

        assertEquals("RETRY", Localization.retry("en"))
        assertEquals("RÉESSAYER", Localization.retry("fr"))

        assertEquals("MAIN MENU", Localization.mainMenu("en"))
        assertEquals("MENU PRINCIPAL", Localization.mainMenu("fr"))

        assertEquals("NEXT MISSION", Localization.nextMission("en"))
        assertEquals("MISSION SUIVANTE", Localization.nextMission("fr"))

        assertEquals("ALL CLEAR!", Localization.allClear("en"))
        assertEquals("TOUT EST CLAIR !", Localization.allClear("fr"))
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
    }
}
