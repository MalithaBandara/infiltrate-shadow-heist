package com.infiltrate.test

import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.LevelData
import game.model.LevelResult
import game.model.LevelStorage
import game.model.MapBackedGameProfileStorage
import game.model.MapBackedLevelStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MainMenuModelTest {

    @Test
    fun testGameProfileInitialValuesAndMapStorageRoundTrip() {
        val rawStore = mutableMapOf<String, String>()

        val initialStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v }
        )

        val profile = initialStorage.getProfile()
        // Verify standard defaults
        assertEquals(100, profile.coins, "Default coins should be 100")
        assertTrue(profile.unlockedLevelIds.contains("level_1"), "level_1 should be unlocked by default")
        assertTrue(profile.unlockedLevelIds.contains("level_5"), "level_5 should be unlocked by default")
        assertEquals(false, profile.isPremium, "Default isPremium should be false")
        assertEquals(false, profile.controlsSwapped, "Default controlsSwapped should be false")
        assertEquals("en", profile.language, "Default language should be 'en'")

        // Modify and save profile
        initialStorage.addCoins(250)
        initialStorage.unlockLevel("level_2")
        initialStorage.setMusicVolume(0.5f)
        initialStorage.setControlsSwapped(true)
        initialStorage.setLanguage("en")

        // Verify discrete keys were written to rawStore
        assertEquals("350", rawStore["user_coins"])
        assertTrue(rawStore["user_unlocked_levels"]?.contains("level_2") == true)
        assertEquals("0.5", rawStore["user_music_vol"])
        assertEquals("true", rawStore["user_controls_swapped"])
        assertEquals("en", rawStore["user_language"])

        // Construct a fresh storage instance simulating an app restart
        val reloadedStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v }
        )
        val reloadedProfile = reloadedStorage.getProfile()
        assertEquals(350, reloadedProfile.coins)
        assertTrue(reloadedProfile.unlockedLevelIds.containsAll(listOf("level_1", "level_5", "level_2")))
        assertEquals(0.5f, reloadedProfile.musicVolume)
        assertEquals(true, reloadedProfile.controlsSwapped)
        assertEquals("en", reloadedProfile.language)
    }

    @Test
    fun testLevelStorageSerializationRoundTrip() {
        val rawStore = mutableMapOf<String, String>()
        val storage: LevelStorage = MapBackedLevelStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v }
        )

        val result = LevelResult(
            levelId = "level_1",
            completed = true,
            wasDetected = false,
            timeTaken = 11.8f,
            timeTargetSeconds = 15.0f
        )
        storage.saveResult(result)

        // Verify discrete level result key
        val rawResult = rawStore["level_result_level_1"]
        assertNotNull(rawResult)
        assertEquals("level_1,true,false,11.8,15.0", rawResult)

        // Recreate storage and read back
        val reloadedStorage: LevelStorage = MapBackedLevelStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v }
        )
        val best = reloadedStorage.getBestResult("level_1")
        assertNotNull(best)
        assertTrue(best.completed)
        assertTrue(!best.wasDetected)
        assertEquals(3, best.starCount)

        val allResults = reloadedStorage.getAllResults()
        assertTrue(allResults.containsKey("level_1"), "getAllResults must include level_1 from persistent storage")
        assertEquals(3, allResults["level_1"]?.starCount)
        assertTrue(allResults["level_1"]?.completed == true)
    }

    @Test
    fun testResetProgressPreservesPremiumAndRestoresDefaults() {
        val rawStore = mutableMapOf<String, String>()
        val storage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v }
        )

        // Make progress and purchases
        storage.addCoins(500)
        storage.activatePremium()
        storage.unlockLevel("level_2")
        storage.unlockLevel("level_3")
        storage.setMusicVolume(0.2f)
        storage.setSfxVolume(0.4f)
        storage.setControlsSwapped(true)
        storage.setLanguage("ja")
        storage.buyPowerup("camera_jammer", 0)
        storage.incrementLevelsCompleted()

        val modified = storage.getProfile()
        assertEquals(2600, modified.coins) // 100 + 500 + 2000 (premium)
        assertTrue(modified.isPremium)
        assertEquals(1, modified.totalLevelsCompleted)

        // Perform reset with preservePremium = true
        storage.resetProgress(preservePremium = true)

        val resetProfile = storage.getProfile()
        assertEquals(100, resetProfile.coins, "Coins should be reset to default 100")
        assertTrue(resetProfile.isPremium, "Premium status should be preserved")
        assertEquals(0, resetProfile.totalLevelsCompleted, "Total levels completed should be 0")
        assertEquals(mutableSetOf("level_1", "level_5"), resetProfile.unlockedLevelIds, "Unlocked levels should reset to defaults")
        assertEquals(0.8f, resetProfile.musicVolume, "Music volume should reset to 0.8")
        assertEquals(1.0f, resetProfile.sfxVolume, "SFX volume should reset to 1.0")
        assertEquals(false, resetProfile.controlsSwapped, "Controls should reset to false")
        assertEquals("en", resetProfile.language, "Language should reset to en")
        assertEquals(2, resetProfile.powerupInventory["camera_jammer"], "Powerup inventory should reset to starter values")

        // Also test fresh load from persistent rawStore
        val reloadedStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v }
        )
        val reloaded = reloadedStorage.getProfile()
        assertEquals(100, reloaded.coins)
        assertTrue(reloaded.isPremium)
        assertEquals(0, reloaded.totalLevelsCompleted)
    }

    @Test
    fun testLevelStorageClearWipesResults() {
        val rawStore = mutableMapOf<String, String>()
        val storage: LevelStorage = MapBackedLevelStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v },
            removeRaw = { rawStore.remove(it) }
        )

        storage.saveResult(LevelResult(levelId = "level_1", completed = true, wasDetected = false, timeTaken = 10f, timeTargetSeconds = 15f))
        storage.saveResult(LevelResult(levelId = "level_2", completed = true, wasDetected = true, timeTaken = 20f, timeTargetSeconds = 25f))

        assertEquals(3, storage.getBestResult("level_1")?.starCount)
        assertEquals(2, storage.getBestResult("level_2")?.starCount)
        assertEquals(2, storage.getAllResults().size)

        storage.clear()

        assertEquals(null, storage.getBestResult("level_1"))
        assertEquals(null, storage.getBestResult("level_2"))
        assertTrue(storage.getAllResults().isEmpty(), "All results should be empty after clear")

        // Reload from rawStore
        val reloaded: LevelStorage = MapBackedLevelStorage(
            getRaw = { rawStore[it] },
            setRaw = { k, v -> rawStore[k] = v },
            removeRaw = { rawStore.remove(it) }
        )
        assertEquals(null, reloaded.getBestResult("level_1"))
        assertTrue(reloaded.getAllResults().isEmpty(), "Reloaded storage should also see zero results")
    }
}
