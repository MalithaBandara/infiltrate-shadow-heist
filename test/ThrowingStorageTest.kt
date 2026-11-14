package test

import game.model.*
import kotlin.test.*

/**
 * The game reads saves through KorGE's `views.storage[key]`, which THROWS for a key never written
 * (korge-core 6.0.0 IStorage.get -> KeyNotFoundException) - unlike the HashMap the other storage
 * tests use. Builds 32 and 33 shipped two bugs that only a store like this shows:
 * - ReviewPrompt read user_review_milestone (unwritten until the first ask) and threw out of the
 *   level-complete handler before the win card;
 * - the profile load gave up at build 33's new user_subtitles key and the next save wrote the
 *   default profile over the real one.
 */
class ThrowingStorageTest {

    private class KorgeLikeStore {
        val map = HashMap<String, String>()
        val getRaw: (String) -> String? = { key -> map[key] ?: throw NoSuchElementException(key) }
        val setRaw: (String, String) -> Unit = { k, v -> map[k] = v }
    }

    @Test
    fun theReviewPromptAsksAtTheMilestonesWhenTheStoreThrowsForMissingKeys() {
        val store = KorgeLikeStore()
        val levels = MapBackedLevelStorage(getRaw = store.getRaw, setRaw = store.setRaw)
        val prompt = ReviewPrompt(getRaw = store.getRaw, setRaw = store.setRaw)
        val asked = (1..12).filter { n ->
            levels.saveResult(LevelResult("level_$n", completed = true, wasDetected = false, timeTaken = 30f, timeTargetSeconds = 60f))
            prompt.claimAfterLevelComplete(levels)
        }
        assertEquals(listOf(3, 6, 9, 12), asked)
    }

    @Test
    fun aProfileSavedBeforeSubtitlesExistedLoadsWhole() {
        val store = KorgeLikeStore()
        // Everything a build-32 persist() wrote - no user_subtitles.
        store.map += mapOf(
            "user_coins" to "740",
            "user_is_premium" to "true",
            "user_music_vol" to "0.5",
            "user_sfx_vol" to "0.9",
            "user_controls_swapped" to "false",
            "user_language" to "fr",
            "user_total_levels_completed" to "6",
            "user_unlocked_levels" to "level_1;level_2;level_3;level_4;level_5;level_6;level_7",
            "user_powerups" to "checkpoints:2"
        )
        val profiles = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw, systemLanguageTags = { emptyList() })
        val p = profiles.getProfile()
        assertEquals(740, p.coins)
        assertTrue(p.isPremium)
        assertEquals("fr", p.language)
        assertEquals(6, p.totalLevelsCompleted)
        assertTrue("level_7" in p.unlockedLevelIds)
        assertEquals(2, p.powerupInventory["checkpoints"])
        assertNull(p.subtitles, "never chosen")

        // A coin award persists - and must persist the real profile, not defaults.
        profiles.addCoins(10)
        assertEquals("750", store.map["user_coins"])
        assertEquals("true", store.map["user_is_premium"])
        assertTrue("level_7" in store.map["user_unlocked_levels"]!!)
        assertTrue("checkpoints:2" in store.map["user_powerups"]!!)
    }

    // Every screen, the game scene and the Android activity hold their own profile instance over
    // one store, and each save writes the whole profile.

    @Test
    fun aSliderMovedInTheMenuKeepsCoinsAndPremiumBoughtInTheStore() {
        val store = KorgeLikeStore()
        val menu = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw, systemLanguageTags = { emptyList() })
        menu.setMusicVolume(0.8f)
        val shop = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw, systemLanguageTags = { emptyList() })
        shop.addCoins(1000)
        shop.activatePremium()
        menu.setMusicVolume(0.3f) // NavigationRoot's instance, loaded before the purchase
        val now = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw).getProfile()
        assertEquals(50 + 1000 + 2000, now.coins) // Remove Ads comes with 2000 coins
        assertTrue(now.isPremium)
        assertEquals(0.3f, now.musicVolume)
    }

    @Test
    fun theMenuKeepsWhatALevelEarnedWhenItSavesAfterwards() {
        val store = KorgeLikeStore()
        val menu = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw, systemLanguageTags = { emptyList() })
        menu.setSfxVolume(1f)
        val game = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw, systemLanguageTags = { emptyList() })
        game.incrementLevelsCompleted()
        game.addCoins(30)
        game.unlockLevel("level_2")
        assertTrue(game.consumePowerup(PowerupType.INVISIBILITY))
        assertEquals(80, menu.getProfile().coins, "a read sees the level's coins")
        menu.setLanguage("de")
        val now = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw).getProfile()
        assertEquals(80, now.coins)
        assertEquals(1, now.totalLevelsCompleted)
        assertTrue("level_2" in now.unlockedLevelIds)
        assertEquals(0, now.getPowerupCount(PowerupType.INVISIBILITY))
        assertEquals("de", now.language)
    }

    @Test
    fun aResetMadeOnOneScreenIsNotUndoneByAnother() {
        val store = KorgeLikeStore()
        val menu = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw, systemLanguageTags = { emptyList() })
        menu.unlockLevel("level_4")
        menu.buyPowerup("checkpoints", 0)
        MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw).resetProgress(preservePremium = true)
        menu.setMusicVolume(0.5f)
        val now = MapBackedGameProfileStorage(getRaw = store.getRaw, setRaw = store.setRaw).getProfile()
        assertEquals(setOf("level_1"), now.unlockedLevelIds)
        assertEquals(0, now.getPowerupCount(PowerupType.CHECKPOINTS))
    }
}
