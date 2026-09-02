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
        assertTrue(profile.unlockedLevelIds.contains("level_4"), "level_4 should be unlocked by default")
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
        assertTrue(reloadedProfile.unlockedLevelIds.containsAll(listOf("level_1", "level_4", "level_2")))
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
    }
}
