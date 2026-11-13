package test

import game.model.*
import kotlin.test.*

/** The automatic store-review request - see ReviewPrompt. */
class ReviewPromptTest {

    private val store = HashMap<String, String>()
    private val levels = MapBackedLevelStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v })
    private val prompt = ReviewPrompt(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v })

    /** What GameplayScene does on a win: save the result, then ask the prompt. */
    private fun complete(levelId: String): Boolean {
        levels.saveResult(LevelResult(levelId, completed = true, wasDetected = false, timeTaken = 30f, timeTargetSeconds = 60f))
        return prompt.claimAfterLevelComplete(levels)
    }

    @Test
    fun aNewPlayerIsAskedOnFinishingLevel3AndNeverAgain() {
        assertFalse(complete("level_1"))
        assertFalse(complete("level_2"))
        assertTrue(complete("level_3"), "asked on level 3")
        assertFalse(complete("level_4"))
        assertFalse(complete("level_3"), "a replay of level 3 does not ask again")
    }

    @Test
    fun aPlayerWhoFinishedLevel3OnAnOlderBuildIsAskedOnTheirNextFinishedLevel() {
        // Saved by an older build, which asked on level 4 only: level 3 done, no flag.
        levels.saveResult(LevelResult("level_3", completed = true, wasDetected = false, timeTaken = 50f, timeTargetSeconds = 85f))
        assertTrue(complete("level_4"), "asked on level 4")
        assertFalse(complete("level_5"))
    }

    @Test
    fun replayingAnEarlierLevelAlsoCounts() {
        levels.saveResult(LevelResult("level_3", completed = true, wasDetected = false, timeTaken = 50f, timeTargetSeconds = 85f))
        assertTrue(complete("level_1"), "any finished level, once level 3 is done")
        assertFalse(complete("level_2"))
    }

    @Test
    fun resetProgressDoesNotAskAgain() {
        assertFalse(complete("level_1"))
        assertFalse(complete("level_2"))
        assertTrue(complete("level_3"))
        MapBackedGameProfileStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v }, systemLanguageTags = { emptyList() })
            .resetProgress()
        levels.clear()
        assertFalse(complete("level_3"), "the flag survives Reset Progress")
    }
}
