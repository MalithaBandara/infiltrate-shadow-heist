package test

import game.model.*
import kotlin.test.*

/** Level 9's figure (the echo) and its checkpoints. */
class Level9EchoAndCheckpointTest {

    private val dt = 1.0 / 60.0
    private fun recording() = RunRecording.decode(java.io.File("resources/level8_run.txt").readText())!!

    @Test
    fun testTheFiguresConeStartsAtItsLeaningHeadWhilePushing() {
        // "the vision cone is at the wrong place for the character when he is in moving position".
        val echo = EchoRunner(recording())
        while (!(echo.current.isPushing && echo.current.isGrounded && echo.current.height >= 96.0) &&
            echo.state == EchoState.PLAYING
        ) echo.update(dt) {}
        assertTrue(echo.current.isPushing, "the bundled run pushes a cart")
        val standingEyeY = echo.bounds.top + 12.0
        val eye = echo.eyePosition
        val feet = echo.y + 96.0
        val visualHeight = 96.0 * Player.VISUAL_HEIGHT_SCALE
        assertEquals(feet - Player.PUSH_HEAD_POINT_Y * visualHeight, eye.y, 1e-9, "at the push pose's head")
        assertTrue(eye.y > standingEyeY + 15.0, "lower than a standing head (${eye.y} vs $standingEyeY)")
        assertTrue(
            (eye.x - echo.centerX) * echo.facing > 12.0,
            "ahead of the body, the way it leans (${eye.x - echo.centerX} facing ${echo.facing})"
        )
    }

    /** Level 9 with the figure on, stood a moment at the start so the loads' floor lids are off. */
    private fun level9(): GameWorld {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_9).also { it.attachEcho(recording()) }
        repeat(10) { world.update(dt, 0.0, false) }
        // Nothing sees him: this is about where a checkpoint may be taken, not about being seen.
        assertTrue(world.activatePowerup(PowerupType.INVISIBILITY))
        return world
    }

    private fun GameWorld.standOn(r: Rect, frames: Int = 20) {
        player.resetTo(r.x + (r.width - player.width) / 2.0, r.top - player.height)
        repeat(frames) { update(dt, 0.0, false) }
        assertTrue(player.isGrounded && !isGameOver, "stood on it (feet ${player.y + player.height} vs top ${r.top})")
    }

    /** Everything in the yard that moves on its own, as one comparable snapshot. */
    private fun snapshot(w: GameWorld): List<Double> =
        w.movingPlatforms.flatMap { listOf(it.bounds.x, it.bounds.y) } +
            w.lasers.map { if (it.isActive) 1.0 else 0.0 } +
            w.cameraBots.flatMap { listOf(it.x, it.facing) } +
            w.cameras.map { it.currentAngle } +
            w.hookCrates.map { it.sweepClock }

    @Test
    fun testLevel9PlaysLevel8sYardBackExactlyInStepWithTheFigure() {
        // "make sure the starting position of everything is recorded at level 8 when the player
        // starts and all those positions including locations of crates and laser states should be
        // recorded and exactly playbacked. otherwise it looks like he goes through objects and
        // lasers in level 9". Level 8 with an idle lead-in (the run starts at its first step, 2.5s
        // in), then a walk; the yard is logged against the run's own clock. Level 9 then plays
        // that run back, and at every moment of the echo its yard must be level 8's.
        val step = 0.05
        val l8 = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        l8.spawnGraceTimer = 1e9 // nothing sees him: this is about the yard, not about being caught
        val logged = HashMap<Int, List<Double>>()
        var t = 0.0
        while (t < 45.0) {
            val move = if (t < 2.5 || t > 6.0) 0.0 else 1.0
            l8.update(step, move, false)
            t += step
            val start = l8.runStartSeconds ?: continue
            logged[kotlin.math.round((l8.totalElapsedSeconds - start) / step).toInt()] = snapshot(l8)
        }
        l8.update(step, 0.0, false)
        val rec = l8.runRecorder!!.finish()
        assertTrue(rec.hasWorld)
        assertEquals(2.5, rec.worldStart, step + 1e-9, "the run starts at its first step")

        val l9 = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_9).also { it.attachEcho(rec) }
        l9.spawnGraceTimer = 1e9
        assertEquals(rec.worldStart, l9.totalElapsedSeconds, 1e-9, "level 9's clock starts where the run did")
        val echo = l9.echo!!
        var compared = 0
        while (echo.state == EchoState.PLAYING) {
            l9.update(step, 0.0, false)
            val k = kotlin.math.round(echo.clock / step).toInt()
            val want = logged[k] ?: continue
            val got = snapshot(l9)
            assertEquals(want.size, got.size)
            for (j in want.indices) assertEquals(want[j], got[j], 0.06, "value $j at ${echo.clock}s of the run")
            compared++
        }
        assertTrue(compared > 700, "compared $compared moments")
    }

    @Test
    fun testALevel9RespawnPutsTheYardBackOnTheRecording() {
        val rec = recording()
        assertTrue(rec.hasWorld, "the bundled run carries the yard - re-run REGEN_LEVEL8_RUN")
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_9).also { it.attachEcho(rec) }
        world.spawnGraceTimer = 1e9
        repeat(400) { world.update(dt, 0.0, false) }
        assertTrue(world.respawnAtCheckpoint())
        repeat(3) { world.update(dt, 0.0, false) }
        val want = rec.sampleAt(world.echo!!.clock)
        val bot = world.cameraBots.first()
        val track = rec.worldTracks.indexOf("bot:${bot.id}")
        assertEquals(kotlin.math.abs(want.world[track]), bot.x, 0.5, "a respawn does not send the bots back to their posts")
        assertEquals(want.world[rec.worldTracks.indexOf("cam0")], world.cameras[0].currentAngle, 0.01)
    }

    @Test
    fun testABotOnTheTableCannotBeSwitchedOffFromTheFloorUnderIt() {
        // How level 8's player switched off the top course's bots through the table, which level
        // 9's echo then replayed ("all the robots in level 9 seem to be disabled").
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_8)
        val top = world.cameraBots.first { it.id.startsWith("lvl9_top") }
        val floor = world.platforms.first { it.y >= 400.0 && it.width >= 1000.0 }
        val p = world.player
        val behind = if (top.facing > 0.0) top.x - p.width - 4.0 else top.x + top.width + 4.0
        p.resetTo(behind, floor.top - p.height)
        assertFalse(top.canDeactivate(p), "from the floor under the table")
        p.resetTo(behind, top.surfaceY - p.height)
        assertTrue(top.canDeactivate(p), "from the table it drives on")
    }

    @Test
    fun testLevel9TakesNoCheckpointOnAMovingLoadButDoesOnAFixedOne() {
        val world = level9()
        val start = world.lastCheckpointX
        val load = world.movingPlatforms.first { it.bounds.x > start + 300.0 }
        world.standOn(load.bounds)
        assertEquals(start, world.lastCheckpointX, "a respawn there would be in mid-air once the load moves on")
        val longCrate = world.staticHangingCrateVariant1.maxBy { it.x }
        world.standOn(longCrate)
        assertTrue(world.lastCheckpointX > start + 250.0, "the long crate stays put - a checkpoint is taken there")
        assertFalse(world.isGameOver)
    }

    @Test
    fun testALevel9RespawnPutsTheGroundRuleBackOn() {
        val world = level9()
        val floor = world.platforms.first { it.y >= 400.0 && it.width >= 1000.0 }
        world.player.resetTo(world.lastCheckpointX, floor.top - world.player.height)
        world.update(dt, 0.0, false)
        assertTrue(world.isGameOver && world.touchedGround, "the floor ends the run")
        assertTrue(world.respawnAtCheckpoint())
        assertFalse(world.touchedGround, "a respawn clears it")
        world.player.resetTo(world.lastCheckpointX, floor.top - world.player.height)
        repeat(3) { if (!world.isGameOver) world.update(dt, 0.0, false) }
        assertTrue(world.isGameOver, "...so touching the floor again ends the run again")
    }
}
