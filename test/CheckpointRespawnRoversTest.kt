package test

import game.model.*
import kotlin.test.*

/**
 * Every rover that is on keeps patrolling after a checkpoint respawn, on every level - early in
 * the run and long after it (level 9's figure has finished by 140s, which used to pin the top
 * course's rovers where its recording ended - see GameWorld.releaseRecordedWorld).
 */
class CheckpointRespawnRoversTest {

    private val dt = 1.0 / 60.0

    @Test
    fun testEveryLevelsRoversKeepPatrollingAfterACheckpointRespawn() {
        var checked = 0
        for (level in LevelData.DEFAULT_LEVELS) {
            for (wait in listOf(20.0, 140.0)) {
                val world = GameWorld.createDefault(level)
                if (level.replaysRunOf != null) {
                    world.attachEcho(RunRecording.decode(java.io.File("resources/level8_run.txt").readText())!!)
                }
                // Nothing sees him: this is about what moves, not about being caught.
                world.spawnGraceTimer = 1e9
                assertTrue(world.activatePowerup(PowerupType.CHECKPOINTS))
                repeat((wait / dt).toInt()) { world.update(dt, 0.0, false) }
                world.restartLevel() // with Checkpoints on, a restart is the checkpoint respawn
                world.spawnGraceTimer = 1e9
                val xs = world.cameraBots.map { mutableListOf<Double>() }
                repeat(900) {
                    world.update(dt, 0.0, false)
                    world.cameraBots.forEachIndexed { i, b -> xs[i].add(b.x) }
                }
                assertFalse(world.isGameOver, "${level.id} after ${wait}s")
                for ((i, bot) in world.cameraBots.withIndex()) {
                    if (bot.isDeactivated) continue
                    val span = xs[i].max() - xs[i].min()
                    assertTrue(span > 20.0, "${level.id}, respawned after ${wait}s: ${bot.id} patrols (covered $span)")
                    checked++
                }
            }
        }
        assertTrue(checked >= 30, "checked $checked rovers")
    }
}
