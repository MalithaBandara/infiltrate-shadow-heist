package test

import game.model.*
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.test.*

/**
 * Every shipped level has checkpoints along the road a player really takes - its clean walkthrough
 * (the same runs PlayerClipGroupsTest follows), followed tick by tick through
 * [GameWorld.tickObserver]. A level with hand-placed checkpoints must take every one of them; an
 * automatic level must take at least one; and a death anywhere on the clean run may cost at most
 * [MAX_REPLAY_SECONDS] - the time since the player last stood near where he would come back.
 * (Time between checkpoints is not the measure: level 8's autopilot waits ~40s for its camera a
 * few steps from its last one.)
 */
class CheckpointCoverageTest {

    private class Follow {
        var checkpoints = 0
        var cpX = Double.NaN
        var cpIndex = -1
        var lastNear = 0.0f
        var worstReplay = 0.0f
        var worstAt = ""
        val log = ArrayList<String>()
    }

    @Test
    fun everyLevelTakesCheckpointsAlongItsCleanRun() {
        val follows = IdentityHashMap<GameWorld, Follow>()
        val finished = ArrayList<GameWorld>()
        GameWorld.tickObserver = { w ->
            val f = follows.getOrPut(w) { Follow().also { it.cpX = w.lastCheckpointX } }
            if (w.lastCheckpointX != f.cpX || w.currentManualCheckpointIndex != f.cpIndex) {
                f.checkpoints++
                f.cpX = w.lastCheckpointX
                f.cpIndex = w.currentManualCheckpointIndex
                f.log += "x${w.lastCheckpointX.toInt()}@${"%.1f".format(w.timeTaken)}s"
            }
            if (abs(w.player.x - f.cpX) <= NEAR) f.lastNear = w.timeTaken
            val replay = w.timeTaken - f.lastNear
            if (replay > f.worstReplay) {
                f.worstReplay = replay
                f.worstAt = "x${w.player.x.toInt()}@${"%.1f".format(w.timeTaken)}s from x${f.cpX.toInt()}"
            }
            if (w.isLevelComplete) finished += w
        }
        try {
            val model = GameplayModelTest()
            model.testLevel4SimulationPlayable()
            model.testLevel7SimulationPlayableWalkthrough()
            model.testLevel8IsBeatableOnTheGroundRoad()
            model.testLevel9IsBeatableWithoutTouchingTheGround()
            model.testLevel10SimulationPlayableWalkthrough()
            Level11EscortTest().testLevel11IsBeatableAsAnEscort()
            Level12EscapeTest().testLevel12IsBeatableAsAnEscape()
            val walk = LevelWalkthroughTest()
            walk.testLevel1CleanRunTime()
            walk.testLevel2CleanRunTime()
            walk.testLevel3CleanRunTime()
            walk.testLevel5CleanRunTime()
            walk.testLevel6CleanRunTime()
        } finally {
            GameWorld.tickObserver = null
        }

        val problems = ArrayList<String>()
        for (level in LevelData.DEFAULT_LEVELS) {
            val world = finished.lastOrNull { it.levelData.id == level.id }
            if (world == null) { problems += "${level.id}: no finished walkthrough seen"; continue }
            val f = follows.getValue(world)
            println(
                "[Checkpoints] ${level.id}: ${f.checkpoints} taken over ${"%.1f".format(world.timeTaken)}s " +
                    "(manual ${world.manualCheckpoints.size}), worst replay ${"%.1f".format(f.worstReplay)}s (${f.worstAt}): " +
                    f.log.joinToString()
            )
            if (f.checkpoints == 0) problems += "${level.id}: the clean run takes no checkpoint"
            if (world.manualCheckpoints.isNotEmpty() && world.currentManualCheckpointIndex != world.manualCheckpoints.lastIndex) {
                problems += "${level.id}: the clean run reaches manual checkpoint ${world.currentManualCheckpointIndex + 1} of ${world.manualCheckpoints.size}"
            }
            if (f.worstReplay > MAX_REPLAY_SECONDS) {
                problems += "${level.id}: a death at ${f.worstAt} replays ${"%.1f".format(f.worstReplay)}s of the clean run"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    companion object {
        /** Within this of the respawn spot a death costs next to nothing. */
        const val NEAR = 120.0
        /** The most of a clean run one death may make a player play again (a person takes ~1.6x). */
        const val MAX_REPLAY_SECONDS = 45.0f
    }
}
