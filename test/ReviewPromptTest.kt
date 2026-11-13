package test

import game.model.*
import kotlin.test.*

/** The automatic store-review request - see ReviewPrompt. */
class ReviewPromptTest {

    private val store = HashMap<String, String>()
    private val levels = MapBackedLevelStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v })
    private val prompt = ReviewPrompt(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v })

    private fun save(level: Int) =
        levels.saveResult(LevelResult("level_$level", completed = true, wasDetected = false, timeTaken = 30f, timeTargetSeconds = 60f))

    /** What GameplayScene does on a win: save the result, then ask the prompt. */
    private fun complete(level: Int): Boolean {
        save(level)
        return prompt.claimAfterLevelComplete(levels)
    }

    @Test
    fun aNewPlayerIsAskedOnFinishingLevels3_6_9And12Only() {
        val asked = (1..12).filter { complete(it) }
        assertEquals(listOf(3, 6, 9, 12), asked)
        assertTrue((1..12).none { complete(it) }, "replays never ask again")
    }

    @Test
    fun aPlayerWhoFinishedLevel3OnAnOlderBuildIsAskedOnTheirNextFinishedLevel() {
        // Saved by an older build, which asked on level 4 only: levels 1-3 done, no record.
        (1..3).forEach { save(it) }
        assertTrue(complete(4), "asked on level 4")
        assertFalse(complete(5))
        assertTrue(complete(6), "and again at the next milestone")
    }

    @Test
    fun severalMilestonesSinceTheLastAskAreOneAsk() {
        (1..7).forEach { save(it) } // levels 3 and 6 both done, nothing asked yet
        assertTrue(complete(1), "a replay of any level asks once")
        assertFalse(complete(2), "not a second time for level 3")
        assertFalse(complete(8))
        assertTrue(complete(9))
    }

    @Test
    fun resetProgressDoesNotAskAgainForMilestonesAlreadyAskedFor() {
        (1..6).forEach { complete(it) }
        MapBackedGameProfileStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v }, systemLanguageTags = { emptyList() })
            .resetProgress()
        levels.clear()
        assertTrue((1..6).none { complete(it) }, "3 and 6 were already asked for")
        assertFalse(complete(7))
        assertFalse(complete(8))
        assertTrue(complete(9))
    }
}
