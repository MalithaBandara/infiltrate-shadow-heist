package test

import game.model.GameWorld
import game.model.LevelData
import game.scene.AmbientLoops
import game.scene.GameSounds
import game.scene.MechanismSounds
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

/**
 * The hanging loads' chain one-shots (MechanismSounds.syncChains, "the moving crate sounds are very
 * unnatural", 2026-10-27): a swinging crate clinks at every end of its run and never jolts or
 * settles; a lever crate jolts as it starts off, clinks once as it turns at the far end, and
 * settles as it comes home to rest. No sound is played (the clips are null) - only the events.
 */
class ChainSoundsTest {
    private val dt = 1.0 / 60.0

    private fun listen(world: GameWorld, seconds: Double, each: (Double) -> Unit = {}): List<String> {
        val heard = ArrayList<String>()
        val sounds = GameSounds(null, null, null, null, null, null, null, null, null)
        val m = MechanismSounds(world, sounds, EmptyCoroutineContext, 60.0)
        m.onChainSound = { kind, _ -> heard += kind }
        val ambient = AmbientLoops(EmptyCoroutineContext)
        var t = 0.0
        while (t < seconds) {
            each(t)
            world.update(dt, 0.0, false, false, false)
            m.sync(ambient, dt, 1.0, 1f, paused = false)
            t += dt
        }
        return heard
    }

    @Test
    fun testASwingingCrateClinksAtEveryEndAndNeverJoltsOrSettles() {
        // Level 12's two moving crates, 3.6s each way and back: two ends a period each.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_12)
        val swinging = world.movingPlatforms.count { !it.startsInactive }
        assertEquals(2, swinging)
        val heard = listen(world, 36.0)
        val ends = swinging * (36.0 / 3.6 * 2).toInt()
        assertTrue(heard.count { it == "clink" } in (ends - swinging * 2)..ends, "a clink at each end: ${heard.count { it == "clink" }} of $ends")
        assertEquals(0, heard.count { it == "clank" }, "no jolt - they never stop")
        assertEquals(0, heard.count { it == "settle" }, "and never settle")
    }

    @Test
    fun testALeverCrateJoltsOffClinksAtTheFarEndAndSettlesHome() {
        // Level 12's ferry: parked until its lever, then one run out and back over 9.5s.
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_12)
        val ferry = world.movingPlatforms.first { it.startsInactive }
        val quiet = listen(GameWorld.createDefault(LevelData.DEFAULT_LEVEL_12), 14.0)
        val heard = listen(world, 14.0) { t -> if (t in 1.0..(1.0 + dt)) ferry.activate() }
        assertEquals(1, heard.count { it == "clank" }, "one jolt as it starts off")
        assertEquals(1, heard.count { it == "settle" }, "one settle as it comes home")
        assertEquals(quiet.count { it == "clink" } + 1, heard.count { it == "clink" }, "one clink as it turns at the far end")
        assertTrue(heard.indexOf("clank") < heard.indexOf("settle"))
    }

    @Test
    fun testNoLevelRattlesItsChainsOutOfTurn() {
        // Every level for half a minute: whatever hangs and moves there, it jolts and settles only
        // when it starts or stops - never in a stream.
        for (level in LevelData.DEFAULT_LEVELS) {
            val heard = listen(GameWorld.createDefault(level), 30.0)
            assertTrue(heard.count { it == "clank" } <= 3, "${level.id}: ${heard.count { it == "clank" }} jolts")
            assertTrue(heard.count { it == "settle" } <= 3, "${level.id}: ${heard.count { it == "settle" }} settles")
        }
    }
}
