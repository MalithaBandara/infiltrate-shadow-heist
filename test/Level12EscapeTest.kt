package test

import game.model.*
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.*

/**
 * Level 12 ("12: Final Escape"): the escort out of the yard, built from earlier levels' mechanisms
 * only (LEVEL_12_LAYOUT's doc). A scripted run of the whole level, which the three-star target is
 * taken from, and runs that take a beat at the wrong moment, to show each beat has teeth.
 */
class Level12EscapeTest {

    /** Drives level 12 by script, like Level11EscortTest.Escort. */
    class Escape {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_12)
        val p = world.player
        val pr = world.prisoner!!
        val dt = 1.0 / 60.0
        var t = 0.0
        var deaths = 0
        var report = ""
        /** Frames anything had either of them partly in sight (star 2). */
        var glimpses = 0
        val glimpseLog = ArrayList<String>()
        private var lastGlimpse = -10.0

        init {
            world.onGameOver = {
                deaths++
                report = "t=${"%.1f".format(t)} player x=${p.x.toInt()} y=${p.y.toInt()} prisoner x=${pr.body.x.toInt()} " +
                    "y=${pr.body.y.toInt()} prisonerLost=${world.prisonerLost} seen=${world.isPrisonerSeen} " +
                    "guards=${world.allGuards.map { it.x.toInt() }} lasers=${world.lasers.map { it.isActive }}"
            }
        }

        fun door(id: String) = world.doors.first { it.id == id }
        fun lift(id: String) = world.lifts.first { it.id == id }
        fun switch(id: String) = world.doorSwitches.first { it.id == id }
        val yardLight get() = world.cameras[0]
        val gapLight get() = world.cameras[1]
        val gateLight get() = world.cameras[2]
        val roomGuard get() = world.allGuards.first { it.y + it.height == 290.0 }
        fun pipe(id: String) = world.steamPipes.first { it.id == id }
        fun bob(i: Int) = world.movingPlatforms.first { it.id == "lvl12_bob_$i" }

        /** Waits for [pipeId] to go quiet - the start of its window. */
        fun waitForWindow(pipeId: String) {
            val pipe = pipe(pipeId)
            waitUntil("$pipeId to fire", 15.0) { pipe.isActive }
            waitUntil("$pipeId to go quiet", 15.0) { !pipe.isActive }
        }

        /** Jumps right from where the body stands and keeps moving until it lands. */
        fun hopRight() {
            step(1.0, jump = true)
            val until = t + 3.0
            while (!p.isGrounded) {
                assertTrue(t < until, "the hop did not land")
                step(1.0)
            }
        }

        /** Spam-taps forward (level 7's way through a headwind) until the body is past [x]. */
        fun tapThroughWindTo(x: Double) {
            val until = t + 30.0
            var frame = 0
            while (p.x < x) {
                assertTrue(t < until, "could not get through the wind to x=${x.toInt()} (at ${p.x.toInt()})")
                step(1.0, tap = frame++ % 2 == 0)
            }
        }

        fun step(move: Double = 0.0, jump: Boolean = false, interact: Boolean = false, crouch: Boolean = false, tap: Boolean = false) {
            check(deaths == 0) { "died: $report" }
            world.update(dt, move, jump, crouch, interact, forwardTap = tap)
            if (world.alertProgress > 0.0) {
                if (glimpses == 0 || t - lastGlimpse > 0.5) glimpseLog.add(
                    "t=${"%.1f".format(t)} player ${p.x.toInt()},${(p.y + p.height).toInt()} prisoner ${pr.body.x.toInt()} " +
                        "by guards=${world.detectingGuards.map { "${it.x.toInt()}/${it.facing}/${it.state}" }} cams=${world.detectingCameras.map { world.cameras.indexOf(it) }} bots=${world.detectingCameraBots.size} prisonerSeen=${world.isPrisonerSeen}"
                )
                lastGlimpse = t
                glimpses++
            }
            t += dt
        }

        fun waitUntil(what: String, maxSeconds: Double = 60.0, cond: () -> Boolean) {
            val until = t + maxSeconds
            while (!cond()) {
                assertTrue(t < until, "timed out waiting for $what at t=${"%.1f".format(t)} (player x=${p.x.toInt()},${(p.y + p.height).toInt()}, prisoner x=${pr.body.x.toInt()})")
                step()
            }
        }

        fun wait(seconds: Double) {
            val until = t + seconds
            while (t < until) step()
        }

        fun walkTo(x: Double, maxSeconds: Double = 30.0, crouch: Boolean = false) {
            val until = t + maxSeconds
            while (abs(p.x - x) > 0.8 || !p.isGrounded) {
                assertTrue(t < until, "could not walk to x=${x.toInt()} (at ${p.x.toInt()},${(p.y + p.height).toInt()}, t=${"%.1f".format(t)})")
                val d = x - p.x
                val move = if (abs(d) <= 0.8) 0.0 else sign(d) * minOf(1.0, abs(d) / 6.0 + 0.05)
                step(move, crouch = crouch)
            }
        }

        fun pressHere(switchId: String) {
            assertTrue(switch(switchId).isPlayerInRange(p), "$switchId in reach from x=${p.x.toInt()},${(p.y + p.height).toInt()}")
            step(interact = true)
            step()
        }

        fun press(switchId: String) {
            val sw = switch(switchId)
            walkTo(sw.centerX - p.width / 2.0)
            pressHere(switchId)
        }

        fun heldAt(doorId: String): Boolean = pr.isHeld && abs(pr.bounds.right - door(doorId).frame.left) < 1.0

        /** Mantles onto a hung pallet from the road, facing [dir]. */
        fun climbPallet(pallet: Rect, dir: Double) {
            walkTo(if (dir > 0.0) pallet.left - p.width - 1.0 else pallet.right + 1.0)
            step(dir, jump = true)
            val until = t + 4.0
            while (p.isClimbing || !p.isGrounded) {
                assertTrue(t < until, "climb did not finish")
                step(0.0)
            }
            assertEquals(pallet.top, p.y + p.height, 0.5, "on the pallet at ${pallet.x.toInt()}")
        }

        /** A plain jump from a pallet's [dir] end onto the raised level (290) beside it. */
        fun hopUp(pallet: Rect, dir: Double) {
            walkTo(if (dir > 0.0) pallet.right - p.width else pallet.left)
            step(dir, jump = true)
            val until = t + 3.0
            while (!p.isGrounded || p.y + p.height > 290.5) {
                assertTrue(t < until, "the hop did not land (at ${p.x.toInt()},${(p.y + p.height).toInt()})")
                assertFalse(p.isClimbing, "a hop, not a climb")
                step(dir)
            }
            assertEquals(290.0, p.y + p.height, 0.5, "up top")
        }

        /** Walks on along the gantry's top to [x], waiting short of each laser gate for its window. */
        fun crossLasersTo(x: Double) {
            for (laser in world.lasers.sortedBy { it.topX }) {
                if (laser.topX < p.x || laser.topX > x) continue
                val short = laser.topX - 12.0 - p.width
                if (p.x < short) walkTo(short)
                waitUntil("${laser.id} to light", 10.0) { laser.isActive }
                waitUntil("${laser.id} to go dark", 10.0) { !laser.isActive }
                walkTo(laser.topX + 12.0)
            }
            walkTo(x)
        }
    }

    val layout = LevelData.LEVEL_12_LAYOUT
    val pallets = layout.hangingClimbTargets.sortedBy { it.x }

    /**
     * How a run takes each beat: the clean run's choices by default, or a deliberately different
     * moment - what the tests below use to show each beat has teeth.
     */
    data class Plan(
        /** Seconds to stand at the pump switch before throwing it; null: until the light leaves the yard. */
        val pumpDelay: Double? = null,
        /** Seconds after he is at the wall before sending lift_a up; null: as the light settles on the yard. */
        val liftDelay: Double? = null,
        /** Seconds after the jet goes quiet before opening gate_1; null: at once. */
        val jetDelay: Double? = null,
        /** Open gate_2 straight away instead of waiting for the gap camera to swing up. */
        val gapAtOnce: Boolean = false,
        /** Seconds in the gatehouse corner before opening the main gate; null: as the gate light leaves the road. */
        val mainGateDelay: Double? = null,
        /** The beat (1..6) to stop after. */
        val stopAfter: Int = 6
    )

    fun cleanRun(log: Boolean = false): Escape = run(Plan(), log)

    fun run(plan: Plan, log: Boolean = false): Escape {
        val e = Escape()
        val w = e.world
        fun mark(what: String) { if (log) println("  ${"%6.1f".format(e.t)}s  $what  (player ${e.p.x.toInt()},${(e.p.y + e.p.height).toInt()}, prisoner ${e.pr.body.x.toInt()})") }
        fun done(beat: Int): Boolean {
            if (plan.stopAfter > beat) return false
            e.wait(4.0)
            return true
        }

        // 1. The pump house: open the door once the light has left the yard.
        e.walkTo(e.switch("sw_pump").centerX - e.p.width / 2.0)
        if (plan.pumpDelay != null) e.wait(plan.pumpDelay)
        else e.waitUntil("the light off the yard", 15.0) { e.yardLight.sweepDirection > 0.0 && e.yardLight.currentAngle > e.yardLight.minAngle + 0.3 }
        e.pressHere("sw_pump")
        mark("pump door open")
        e.walkTo(850.0)
        e.waitUntil("him at the wall", 20.0) { e.pr.isHeld && abs(e.pr.bounds.right - 980.0) < 1.0 }
        mark("at the wall")
        if (done(1)) return e

        // 2. Over the wall: up as the light settles on the yard, then down the crate steps.
        if (plan.liftDelay != null) e.wait(plan.liftDelay)
        else e.waitUntil("the light parked on the yard", 20.0) { e.yardLight.currentAngle <= e.yardLight.minAngle + 1e-9 }
        e.pressHere("sw_lift_a")
        val liftA = e.lift("lift_a")
        e.waitUntil("lift_a up", 5.0) { liftA.topY == liftA.def.upperY }
        e.walkTo(1300.0)
        assertEquals(440.0, e.p.y + e.p.height, 0.5, "down the far side")
        e.waitUntil("him down the steps", 10.0) { e.pr.body.x > 1230.0 && e.pr.body.isGrounded }
        mark("over the wall")
        if (done(2)) return e

        // 3. The gantry: up, under the chained crate, over the barrels, through the laser gates,
        // down the shaft behind the rover, switch it off, open the gate in the jet's quiet spell.
        e.climbPallet(pallets[0], 1.0)
        e.hopUp(pallets[0], 1.0)
        e.walkTo(1764.0, crouch = true)
        e.walkTo(1760.0)
        e.hopRight()
        assertEquals(242.0, e.p.y + e.p.height, 0.5, "up on the barrels")
        mark("on the gantry, past the crouch")
        e.crossLasersTo(2060.0)
        val bot = w.cameraBots.first()
        e.waitUntil("the rover heading away", 30.0) { bot.facing < 0.0 && bot.x > 1930.0 }
        e.walkTo(2095.0)
        assertEquals(440.0, e.p.y + e.p.height, 0.5, "down the shaft onto the road")
        var guard = 0
        while (!bot.canDeactivate(e.p)) {
            e.step(-1.0)
            assertTrue(++guard < 900, "caught up with the rover")
        }
        e.step(interact = true)
        e.step()
        assertTrue(bot.isDeactivated)
        mark("rover off")
        e.walkTo(e.switch("sw_gate_1").centerX - e.p.width / 2.0)
        e.waitUntil("him at gate_1", 30.0) { e.heldAt("gate_1") }
        e.waitForWindow("lvl12_pipe_1")
        if (plan.jetDelay != null) e.wait(plan.jetDelay)
        e.pressHere("sw_gate_1")
        mark("gate_1 open")
        e.waitUntil("him past the jet", 10.0) { e.pr.bounds.left > e.pipe("lvl12_pipe_1").x + 20.0 }
        if (done(3)) return e

        // 4. The gap: back up the shaft, open gate_2 once the camera has swung up past this end
        // of the deck, and swing across while it is up there.
        e.climbPallet(pallets[1], 1.0)
        e.hopUp(pallets[1], 1.0)
        e.walkTo(2224.0)
        e.waitUntil("him at gate_2", 30.0) { e.heldAt("gate_2") }
        val gap = e.gapLight
        if (!plan.gapAtOnce) {
            e.waitUntil("the gap camera up past the deck end", 30.0) { gap.sweepDirection > 0.0 && gap.currentAngle > 3.75 && gap.currentAngle < gap.maxAngle }
        }
        e.press("sw_gate_2")
        mark("gate_2 open")
        e.walkTo(2400.0 - e.p.width)
        e.step(1.0, jump = true)
        val swingUntil = e.t + 4.0
        while (!e.p.isGrounded || e.p.x < 2550.0) {
            assertTrue(e.t < swingUntil, "the swing did not land on the far deck (at ${e.p.x.toInt()},${(e.p.y + e.p.height).toInt()})")
            e.step(1.0)
        }
        assertEquals(290.0, e.p.y + e.p.height, 0.5, "swung onto the far deck")
        // On past the camera's post before it swings back down across this end of the deck.
        e.walkTo(2644.0)
        mark("across the gap")
        if (done(4)) return e

        // 5. The far deck: through the bobbing loads with their wave, onto the belt, up onto the
        // lever's crate, the lever, past the dead curtain, down to the road.
        e.waitUntil("bob 0 at the top", 10.0) { e.bob(0).bounds.top < 101.0 }
        e.walkTo(3060.0)
        mark("through the bobbing loads")
        e.hopRight()
        assertEquals(264.0, e.p.y + e.p.height, 0.5, "on the belt")
        // Against the belt (net 87 forward) to its far end.
        while (e.p.x < 3196.0) e.step(1.0)
        e.hopRight()
        assertEquals(236.0, e.p.y + e.p.height, 0.5, "on the lever's crate")
        val lever = w.levers.first()
        e.walkTo(lever.centerX - e.p.width / 2.0)
        e.step(interact = true)
        e.step()
        assertTrue(lever.isActivated && w.lasers.filter { it.id.startsWith("lvl12_curtain") }.all { it.isDisabled }, "the curtain is down")
        mark("curtain cut")
        e.walkTo(3540.0)
        assertEquals(440.0, e.p.y + e.p.height, 0.5, "down off the far deck")
        if (done(5)) return e

        // 6. The gatehouse.
        val gh = pallets[2]
        val rg = e.roomGuard
        e.walkTo(gh.left - e.p.width - 1.0)
        e.waitUntil("him at the main gate", 40.0) { e.heldAt("main_gate") }
        e.waitUntil("the room guard walking away", 30.0) { rg.facing > 0.0 && rg.patrolPauseTimer <= 0.0 && rg.x < rg.patrolMinX + 20.0 }
        e.climbPallet(gh, 1.0)
        e.hopUp(gh, -1.0)
        e.walkTo(e.switch("sw_main").centerX - e.p.width / 2.0)
        mark("in the gatehouse corner")
        if (plan.mainGateDelay != null) e.wait(plan.mainGateDelay)
        else e.waitUntil("the gate light off the road", 30.0) { e.gateLight.sweepDirection > 0.0 && e.gateLight.currentAngle > e.gateLight.minAngle + 0.3 }
        e.pressHere("sw_main")
        mark("main gate open")
        e.waitUntil("him out", 15.0) { e.pr.hasEscaped }
        mark("he is out")
        e.waitUntil("the room guard walking away", 30.0) { rg.facing > 0.0 && rg.patrolPauseTimer <= 0.0 && rg.x < rg.patrolMinX + 20.0 }
        e.walkTo(gh.right + 4.0, crouch = true)
        assertEquals(440.0, e.p.y + e.p.height, 0.5, "down in the passage")
        val fan = w.fans.first()
        e.walkTo(fan.windMinX - e.p.width - 4.0)
        e.waitUntil("the gate light up in the sky", 30.0) { e.gateLight.sweepDirection > 0.0 && e.gateLight.currentAngle > e.gateLight.minAngle + 1.2 }
        e.tapThroughWindTo(fan.windMaxX + 10.0)
        val until = e.t + 10.0
        while (!w.isLevelComplete) {
            assertTrue(e.t < until, "walked out after him")
            e.step(1.0)
        }
        mark("OUT")
        return e
    }

    /** Runs [plan], returning null for a clean finish or how it went wrong. */
    fun outcome(plan: Plan): String? = try {
        val e = run(plan)
        when {
            e.deaths > 0 -> "died: ${e.report}"
            e.glimpses > 0 -> "glimpsed (${e.glimpses} frames)"
            else -> null
        }
    } catch (ex: IllegalStateException) {
        ex.message
    }

    @Test
    fun testLevel12IsBeatableAsAnEscape() {
        val e = cleanRun(log = true)
        assertEquals(0, e.deaths, e.report)
        assertEquals(0, e.glimpses, "nobody so much as glimpses either of them: ${e.glimpseLog}")
        assertEquals(ObjectiveState.MET, e.world.bonusObjectiveState, "a clean run stays completely unseen")
        println("WALKTHROUGH level_12: ${"%.1f".format(e.t)}s (target ${LevelData.DEFAULT_LEVEL_12.timeTargetSeconds}s)")
        val ratio = LevelData.DEFAULT_LEVEL_12.timeTargetSeconds / e.t
        assertTrue(ratio in 1.25..2.0, "target should be 1.25..2.0x the clean run (${"%.1f".format(e.t)}s, ratio ${"%.2f".format(ratio)})")
    }

    // ---- each beat has a wrong moment as well as a right one --------------------------------

    @Test
    fun testOpeningThePumpDoorUnderTheLightGetsThemSeen() {
        assertNull(outcome(Plan(stopAfter = 1)), "out once the light has swung up off the yard")
        assertNotNull(outcome(Plan(pumpDelay = 0.0, stopAfter = 1)), "out at once, under the light")
    }

    @Test
    fun testSendingHimUpTheWallIntoTheLightGetsThemSeen() {
        assertNull(outcome(Plan(stopAfter = 2)), "up as the light settles on the yard")
        assertNotNull(outcome(Plan(liftDelay = 12.0, stopAfter = 2)), "up into its park over the wall")
    }

    @Test
    fun testOpeningTheGantryGateLateWalksHimIntoTheSteam() {
        assertNull(outcome(Plan(stopAfter = 3)), "opened as the jet goes quiet")
        assertNotNull(outcome(Plan(jetDelay = 1.25, stopAfter = 3)), "opened late in its quiet spell")
    }

    @Test
    fun testCrossingTheGapUnderTheCameraGetsYouSeen() {
        assertNull(outcome(Plan(stopAfter = 4)), "across once it has swung up")
        assertNotNull(outcome(Plan(gapAtOnce = true, stopAfter = 4)), "across straight away")
    }

    @Test
    fun testOpeningTheMainGateUnderTheGateLightGetsHimSeen() {
        assertNotNull(outcome(Plan(mainGateDelay = 8.0)), "out while the light is on the road")
    }

    // ---- the layout ----------------------------------------------------------------------

    @Test
    fun testOnlyFiveSwitchesHoldHim() {
        // "there are too many button things in level 12": the pump door, the wall's lift, two
        // gates and the main gate. Everything else is levels 1-11's other mechanics.
        assertEquals(5, layout.doorSwitches.size)
        assertTrue(layout.levers.isNotEmpty() && layout.swingHooks.isNotEmpty() && layout.movingPlatforms.isNotEmpty())
        assertTrue(layout.conveyors.isNotEmpty() && layout.cameraBots.isNotEmpty() && layout.steamPipes.isNotEmpty())
        assertTrue(layout.fans.isNotEmpty() && layout.lasers.isNotEmpty() && layout.barrels.isNotEmpty())
        assertTrue(layout.seamlessTables.isNotEmpty() && layout.hangingCrateVariant2.isNotEmpty() && layout.lifts.isNotEmpty())
        assertTrue(LevelData.DEFAULT_LEVEL_12.hasRain)
    }

    @Test
    fun testNothingOfThePlayersOverheadRoadReachesHisRoad() {
        // His road is clear under the decks: every deck, pallet and bobbing load stays over his head.
        val head = 440.0 - 96.0
        for (b in layout.movingPlatforms) assertTrue(b.maxY + b.height < head, "${b.id} stays over his head")
        for (d in layout.seamlessTables + layout.hangingClimbTargets) assertTrue(d.bottom < head, "a deck at ${d.x.toInt()} over his head")
        for (l in layout.lasers) assertTrue(maxOf(l.topY, l.bottomY) <= 290.0, "${l.id} ends on a deck")
    }

    @Test
    fun testTheLaserHousingsAreSmall() {
        assertTrue(layout.lasers.all { it.emitterScale <= 0.5 }, "\"laser emittors are too large\"")
    }

    @Test
    fun testTheYardLightNeverFallsWhereHeWaitsAtTheWall() {
        val e = Escape()
        val waiting = Player(980.0 - 36.0, 440.0 - 96.0)
        repeat(60 * 30) {
            e.step()
            assertNull(VisionSystem.getPlayerSpottedDistance(e.yardLight, waiting, e.world.visionOccluders), "lit at t=${"%.1f".format(e.t)}")
        }
    }

    @Test
    fun testTheGatehouseCornerIsOutOfTheRoomGuardsSightAndHearing() {
        val e = Escape()
        val sw = e.switch("sw_main")
        val corner = Player(sw.centerX - 18.0, sw.surfaceY - 96.0)
        val g = e.roomGuard
        repeat(60 * 20) {
            e.step()
            assertNull(VisionSystem.getPlayerSpottedDistance(g, corner, e.world.visionOccluders), "seen in the corner at t=${"%.1f".format(e.t)}")
            assertTrue(corner.center.distanceTo(g.center) > NoiseLevel.NORMAL.radius, "heard in the corner at t=${"%.1f".format(e.t)}")
        }
    }

    @Test
    fun testTheCrateStepsTakeHimDownTheWallAStepAtATime() {
        val wall = layout.boxes.first { it.width == 40.0 && it.top == 290.0 && it.bottom == 440.0 }
        val steps = layout.boxes.filter { it.left >= wall.right && it.left < wall.right + 250.0 && it.bottom == 440.0 && it.width == 50.0 }.sortedBy { it.x }
        var top = wall.top
        for (st in steps) {
            assertTrue(st.top - top in 0.0..Prisoner.MAX_STEP_DOWN, "a step he takes (${st.top - top})")
            top = st.top
        }
        assertTrue(440.0 - top <= Prisoner.MAX_STEP_DOWN, "the last step reaches the road")
    }

    @Test
    fun testEveryBeatIsHeldWhereItsCheckpointIs() {
        val stoppers = layout.doors.map { it.x } + layout.boxes.filter { it.height >= 100.0 }.map { it.x }
        for (cp in layout.manualCheckpoints) {
            val z = cp.triggerZone!!
            assertTrue(stoppers.any { abs(it - z.right) < 0.5 }, "${cp.id} ends at something that holds him")
        }
    }

    @Test
    fun testTheLevelEndsOnlyWithBothOfThemOut() {
        val e = Escape()
        val exit = e.world.exitZone
        e.door("main_gate").restore(true, 1.0)
        e.p.resetTo(exit.x + 10.0, exit.bottom - 96.0)
        e.wait(1.0)
        assertFalse(e.world.isLevelComplete, "not without him")
        e.pr.restore(exit.x + 40.0, exit.bottom - 96.0)
        e.wait(0.2)
        assertTrue(e.world.isLevelComplete)
    }
}
