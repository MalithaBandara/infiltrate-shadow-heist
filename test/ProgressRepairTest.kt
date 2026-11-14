package test

import game.model.*
import kotlin.test.*

/** Recovery from build 33's overwritten saves - see ProgressRepair. */
class ProgressRepairTest {

    private val store = HashMap<String, String>()
    private val levels = MapBackedLevelStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v })
    private fun profiles() = MapBackedGameProfileStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v }, systemLanguageTags = { emptyList() })

    private fun complete(id: String) =
        levels.saveResult(LevelResult(id, completed = true, wasDetected = false, timeTaken = 30f, timeTargetSeconds = 60f))

    @Test
    fun aWipedProfileGetsBackTheUnlocksAndCountItsResultsProve() {
        for (n in 1..7) complete("level_$n")
        // What build 33 saved over it: the default profile plus one level-complete's changes.
        profiles().resetProgress(preservePremium = false)
        val p = profiles()
        p.incrementLevelsCompleted()

        assertTrue(ProgressRepair.repair(p, levels))
        val now = profiles().getProfile()
        assertEquals((1..8).map { "level_$it" }.toSet(), now.unlockedLevelIds, "every completed level and the next")
        assertEquals(7, now.totalLevelsCompleted)
        assertFalse(ProgressRepair.repair(profiles(), levels), "nothing left to put back")
    }

    @Test
    fun itNeverTakesAnythingAway() {
        complete("level_1")
        val p = profiles()
        p.unlockLevel("level_9")
        p.saveProfile(p.getProfile().also { it.totalLevelsCompleted = 11 })
        ProgressRepair.repair(p, levels)
        val now = profiles().getProfile()
        assertTrue("level_9" in now.unlockedLevelIds)
        assertTrue("level_2" in now.unlockedLevelIds)
        assertEquals(11, now.totalLevelsCompleted)
    }

    @Test
    fun aFailedRunUnlocksNothing() {
        levels.saveResult(LevelResult("level_3", completed = false, wasDetected = true, timeTaken = 30f, timeTargetSeconds = 60f))
        assertFalse(ProgressRepair.repair(profiles(), levels))
        assertEquals(setOf("level_1"), profiles().getProfile().unlockedLevelIds)
    }
}
