package test

import game.model.*
import kotlin.test.*

/** Level 4's one free Checkpoints and the step recommending it - see Level4CheckpointGift. */
class Level4CheckpointGiftTest {

    private val store = HashMap<String, String>()
    private val levels = MapBackedLevelStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v })
    private fun profiles() = MapBackedGameProfileStorage(getRaw = { store[it] }, setRaw = { k, v -> store[k] = v }, systemLanguageTags = { emptyList() })
    private fun start(levelId: String = "level_4") =
        Level4CheckpointGift.claimOnLevelStart(levelId, { store[it] }, { k, v -> store[k] = v }, profiles(), levels)
    private fun checkpoints() = profiles().getProfile().getPowerupCount(PowerupType.CHECKPOINTS)

    @Test
    fun theFirstStartOfLevel4GivesOneCheckpointAndNoLaterStartGivesAnother() {
        assertEquals(0, checkpoints())
        assertTrue(start())
        assertEquals(1, checkpoints())
        // "make sure to only give the powerup when he opens the level 4 for first time, otherwise
        // they can get unlimited powerups by just opening the level"
        repeat(5) { assertFalse(start()) }
        assertEquals(1, checkpoints())
    }

    @Test
    fun itIsKeptOnTopOfCheckpointsAlreadyOwned() {
        profiles().buyPowerup(PowerupType.CHECKPOINTS.id, 0)
        assertTrue(start())
        assertEquals(2, checkpoints())
    }

    @Test
    fun spendingItDoesNotEarnAnotherOnTheNextStart() {
        start()
        assertTrue(profiles().consumePowerup(PowerupType.CHECKPOINTS))
        assertFalse(start())
        assertEquals(0, checkpoints())
    }

    @Test
    fun otherLevelsAndPlayersWhoClearedLevel4GetNothing() {
        for (n in listOf(1, 2, 3, 5, 6)) assertFalse(start("level_$n"))
        assertEquals(0, checkpoints())

        levels.saveResult(LevelResult("level_4", completed = true, wasDetected = false, timeTaken = 120f, timeTargetSeconds = 190f))
        assertFalse(start())
        assertEquals(0, checkpoints())
    }

    @Test
    fun aResetDoesNotHandItOutAgain() {
        start()
        profiles().resetProgress(preservePremium = true)
        assertFalse(start())
    }

    @Test
    fun level4RecommendsTheCheckpointUntilTheFirstMove() {
        val step = LevelData.DEFAULT_LEVEL_4.tutorialSteps.single { it.id == "step_use_checkpoint" }
        assertEquals(PowerupType.CHECKPOINTS, step.requiresPowerup, "only while one is owned; turning it on ends the step")
        assertEquals(TutorialAction.FIRST_MOVE, step.targetAction)
        assertEquals(TutorialControlHighlight.GADGETS, step.highlight)
        assertEquals(0.0, step.autoDismissSeconds, "no timer - it waits for a move or the power-up")
        // "Checkpoint powerup! not just checkpoint" (2026-11-14)
        assertEquals("(Recommended) Use your free Checkpoint power-up!", step.localizedCallout("en"))
        val start = LevelData.LEVEL_4_LAYOUT.playerStartX
        assertTrue(start in step.triggerMinX..step.triggerMaxX, "open from the very first frame")
    }
}
