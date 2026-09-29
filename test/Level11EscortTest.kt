package test

import game.model.*
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.*

/**
 * Level 11 ("11: The Prisoner"): doors, lifts, switches and the prisoner who walks on his own -
 * each mechanism on its own, then a scripted run of the whole level (LEVEL_11_LAYOUT's seven
 * beats) that the three-star target is taken from.
 */
class Level11EscortTest {

    /** Drives level 11 by script: walk to a spot, throw a switch, wait for something. */
    class Escort {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_11)
        val p = world.player
        val pr = world.prisoner!!
        val dt = 1.0 / 60.0
        var t = 0.0
        var deaths = 0
        var report = ""

        init {
            world.onGameOver = {
                deaths++
                report = "t=${"%.1f".format(t)} player x=${p.x.toInt()} y=${p.y.toInt()} prisoner x=${pr.body.x.toInt()} " +
                    "y=${pr.body.y.toInt()} prisonerLost=${world.prisonerLost} seen=${world.isPrisonerSeen} " +
                    "guards=${world.allGuards.map { it.x.toInt() }}"
            }
        }

        fun door(id: String) = world.doors.first { it.id == id }
        fun lift(id: String) = world.lifts.first { it.id == id }
        fun pipe(id: String) = world.steamPipes.first { it.id == id }
        fun switch(id: String) = world.doorSwitches.first { it.id == id }

        fun step(move: Double = 0.0, jump: Boolean = false, interact: Boolean = false) {
            check(deaths == 0) { "died: $report" }
            world.update(dt, move, jump, false, interact)
            t += dt
        }

        fun waitUntil(what: String, maxSeconds: Double = 60.0, cond: () -> Boolean) {
            val until = t + maxSeconds
            while (!cond()) {
                assertTrue(t < until, "timed out waiting for $what at t=${"%.1f".format(t)} (player x=${p.x.toInt()}, prisoner x=${pr.body.x.toInt()})")
                step()
            }
        }

        fun wait(seconds: Double) {
            val until = t + seconds
            while (t < until) step()
        }

        /** Walks the body's left edge to [x] on whatever floor it is on (falls through a gap on the way). */
        fun walkTo(x: Double, maxSeconds: Double = 30.0) {
            val until = t + maxSeconds
            while (abs(p.x - x) > 0.8 || !p.isGrounded) {
                assertTrue(t < until, "could not walk to x=${x.toInt()} (at ${p.x.toInt()},${p.y.toInt()}, t=${"%.1f".format(t)})")
                val d = x - p.x
                val move = if (abs(d) <= 0.8) 0.0 else sign(d) * minOf(1.0, abs(d) / 6.0 + 0.05)
                step(move)
            }
        }

        /** Throws [switchId] from where the body stands now (on a lift, say). */
        fun pressHere(switchId: String) {
            assertTrue(switch(switchId).isPlayerInRange(p), "$switchId in reach from x=${p.x.toInt()}")
            step(interact = true)
            step()
        }

        fun press(switchId: String) {
            val sw = switch(switchId)
            walkTo(sw.centerX - p.width / 2.0)
            assertTrue(sw.isPlayerInRange(p), "$switchId in reach")
            step(interact = true)
            step()
        }

        /** Climbs a shaft: onto its catwalk from the duct floor, then onto the slab past it. */
        fun climbShaft(catwalk: Rect) {
            walkTo(catwalk.left - p.width - 1.0)
            climbRight()
            assertEquals(catwalk.top, p.y + p.height, 0.5, "on the catwalk")
            walkTo(catwalk.right - p.width)
            climbRight()
            assertEquals(LevelData.LEVEL_10_ROOM_FLOOR_Y, p.y + p.height, 0.5, "up in the room")
        }

        private fun climbRight() {
            step(1.0, jump = true)
            val until = t + 4.0
            while (p.isClimbing || !p.isGrounded) {
                assertTrue(t < until, "climb did not finish")
                step(0.0)
            }
        }

        /** Waits for [pipeId] to go quiet - the start of its window. */
        fun waitForWindow(pipeId: String) {
            val pipe = pipe(pipeId)
            waitUntil("$pipeId to fire", 15.0) { pipe.isActive }
            waitUntil("$pipeId to go quiet", 15.0) { !pipe.isActive }
        }

        fun heldAt(doorId: String): Boolean = pr.isHeld && abs(pr.bounds.right - door(doorId).frame.left) < 1.0
    }

    // ---- the mechanisms ------------------------------------------------------------------

    @Test
    fun testTheCellDoorFreesHimAndHeWalksOnHisOwnUntilSomethingStopsHim() {
        val e = Escort()
        e.wait(2.0)
        assertFalse(e.pr.isFree, "sits in the cell until the gate opens")
        val x0 = e.pr.body.x
        e.press("sw_cell")
        e.waitUntil("him to be free", 2.0) { e.pr.isFree }
        e.wait(Prisoner.STAND_UP_SECONDS + 0.5)
        assertTrue(e.pr.body.x > x0 + 5.0, "walks out of the cell without being told")
        // The lift fills the floor, and the room's end wall is what stops him - standing on it.
        e.waitUntil("him to stop", 20.0) { e.pr.isHeld }
        val lift = e.lift("lift_1").bounds
        assertTrue(e.pr.bounds.centerX in lift.left..lift.right, "held on the lift (x=${e.pr.body.x})")
    }

    @Test
    fun testHeStopsAtADropHeWouldHaveToJumpDown() {
        val e = Escort()
        // Lower the lift before he gets to it: its shaft is then a hole in the floor.
        e.press("sw_cell")
        e.press("sw_lift_1")
        e.waitUntil("him to stop", 20.0) { e.pr.isHeld }
        assertTrue(e.pr.body.isGrounded)
        assertEquals(LevelData.LEVEL_10_ROOM_FLOOR_Y, e.pr.body.y + e.pr.body.height, 0.5, "still up on the room floor")
        assertTrue(e.pr.bounds.right < e.lift("lift_1").def.x + 30.0, "at the lip of the shaft, not in it")
    }

    @Test
    fun testAShutDoorStopsHimAndOpeningItLetsHimOn() {
        val e = Escort()
        e.pr.free()
        e.pr.restore(700.0, 440.0 - 96.0)
        e.waitUntil("him at door_1", 10.0) { e.heldAt("door_1") }
        e.wait(1.0)
        assertTrue(e.heldAt("door_1"), "waits there for as long as it is shut")
        e.door("door_1").set(true)
        e.waitUntil("him through", 5.0) { e.pr.bounds.left > e.door("door_1").frame.right }
    }

    @Test
    fun testADoorWillNotComeDownOnSomeoneInTheDoorway() {
        val e = Escort()
        val d = e.door("door_1")
        d.set(true)
        e.wait(1.0)
        e.p.resetTo(d.frame.x - 6.0, 440.0 - 96.0)
        e.wait(0.2)
        d.set(false)
        e.wait(1.0)
        assertTrue(d.isHeldBySensor || d.openness > 0.5, "held open over the body")
        assertTrue(d.panel == null || d.panel!!.bottom <= e.p.bounds.top + 0.5, "never into his head")
        e.walkTo(d.frame.left - 60.0)
        e.wait(1.0)
        assertTrue(d.isFullyShut, "shuts once the doorway is clear")
    }

    @Test
    fun testAShutDoorTurnsAGuardBackAndHidesWhatIsBehindIt() {
        val e = Escort()
        val g = e.world.guard
        // The pen: both doors shut, so his beat is between them however long his route is.
        var minX = g.x
        var maxX = g.x
        repeat(60 * 40) {
            e.step()
            minX = minOf(minX, g.x)
            maxX = maxOf(maxX, g.x)
        }
        assertTrue(minX >= e.door("door_3").frame.right - 0.5, "turned back at door_3 (min $minX)")
        assertTrue(maxX + g.width <= e.door("door_4").frame.left + 0.5, "turned back at door_4 (max $maxX)")
        // Standing right behind the shut door he is walking towards: not seen.
        e.p.resetTo(e.door("door_4").frame.right + 4.0, 440.0 - 96.0)
        repeat(60 * 12) { e.step() }
        assertEquals(0, e.deaths, "a shut door blocks his sight")
    }

    @Test
    fun testAGuardWhoSeesThePrisonerIsMissionFailed() {
        val e = Escort()
        e.pr.restore(2380.0, 440.0 - 96.0)
        e.world.guard.placeAt(2600.0, -1.0)
        e.door("door_3").restore(true, 1.0)
        e.p.resetTo(2100.0, 440.0 - 96.0)
        var failed = false
        e.world.onGameOver = { failed = true }
        repeat(60 * 6) { if (!failed) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(failed, "the guard caught sight of him")
        assertTrue(e.world.prisonerLost, "and it was the prisoner he saw")
    }

    @Test
    fun testSteamKillsHim() {
        val e = Escort()
        e.pr.restore(5490.0, 440.0 - 96.0)
        var failed = false
        e.world.onGameOver = { failed = true }
        e.p.resetTo(5300.0, 440.0 - 96.0)
        repeat(60 * 10) { if (!failed) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(failed && e.world.prisonerLost, "walked into lvl11_pipe_2 while it was blowing")
    }

    @Test
    fun testTheLiftCarriesHimAndWillNotComeDownOnAnyone() {
        val e = Escort()
        e.press("sw_cell")
        e.waitUntil("him on the lift", 20.0) { e.pr.isHeld }
        // Someone underneath: it holds.
        val l = e.lift("lift_1")
        val under = Rect(l.def.x, 440.0 - 96.0, 36.0, 96.0)
        e.p.resetTo(under.x + 10.0, under.y)
        e.wait(0.5)
        e.step(interact = false)
        l.toggle()
        e.wait(3.0)
        assertTrue(l.bounds.bottom <= e.p.bounds.top + 0.5, "stopped over the body in the shaft")
        e.p.resetTo(300.0, LevelData.LEVEL_10_ROOM_FLOOR_Y - 96.0)
        e.waitUntil("the lift down", 5.0) { l.topY == l.def.lowerY }
        e.waitUntil("him to walk off it into the duct", 5.0) { e.pr.body.x > l.def.x + l.def.width }
        assertEquals(440.0, e.pr.body.y + e.pr.body.height, 0.5, "down on the duct floor")
    }

    @Test
    fun testTheLevelEndsOnlyWithBothOfThemOut() {
        val e = Escort()
        val exit = e.world.exitZone
        e.p.resetTo(exit.x + 10.0, exit.bottom - 96.0)
        e.lift("lift_4").restore(true, LevelData.LEVEL_10_ROOM_FLOOR_Y)
        e.wait(1.0)
        assertFalse(e.world.isLevelComplete, "not without him")
        e.pr.restore(exit.x + 40.0, exit.bottom - 96.0)
        e.wait(0.2)
        assertTrue(e.world.isLevelComplete)
    }

    @Test
    fun testARespawnPutsHimTheDoorsAndTheGuardBackAsTheCheckpointHadThem() {
        val e = Escort()
        e.pr.restore(2300.0, 440.0 - 96.0)
        e.p.resetTo(2200.0, 440.0 - 96.0)
        e.waitUntil("his hold at door_3 to be a checkpoint", 10.0) { e.world.hasAdvancedCheckpoint }
        assertTrue(e.heldAt("door_3"))
        val guardX = e.world.guard.x
        e.door("door_4").set(true)
        e.door("door_3").set(true)
        e.wait(3.0)
        assertTrue(e.world.respawnAtCheckpoint())
        assertEquals(2380.0, e.p.x, 0.01)
        assertTrue(e.door("door_3").isFullyShut && e.door("door_4").isFullyShut, "doors as they were")
        assertEquals(guardX, e.world.guard.x, 2.0, "guard where he was")
        assertTrue(abs(e.pr.bounds.right - e.door("door_3").frame.left) < 1.0, "prisoner back at the door")
    }

    @Test
    fun testEveryBeatIsHeldBehindADoorWhereItsCheckpointIs() {
        // Each checkpoint zone reaches the door or wall he is held at, on his side of it.
        val layout = LevelData.LEVEL_11_LAYOUT
        for (cp in layout.manualCheckpoints) {
            val z = cp.triggerZone!!
            val stopper = layout.doors.map { Rect(it.x, it.top, it.width, it.bottom - it.top) } + layout.boxes
            assertTrue(stopper.any { abs(it.left - z.right) < 0.5 && it.top < z.bottom && it.bottom > z.top }, "${cp.id} ends at something that holds him")
        }
    }

    // ---- the whole level -------------------------------------------------------------------

    /**
     * The scripted clean run: every beat done the way the layout's doc describes, with no
     * deaths. The time it takes is the floor the three-star target is set from.
     */
    fun cleanRun(): Escort {
        val e = Escort()
        val w = e.world

        // 1. The cell and the lift.
        e.press("sw_cell")
        e.walkTo(e.switch("sw_lift_1").centerX - e.p.width / 2.0)
        e.waitUntil("him on lift_1", 20.0) { e.pr.isHeld }
        e.press("sw_lift_1")
        val lift1 = e.lift("lift_1")
        e.waitUntil("lift_1 down", 5.0) { lift1.topY == lift1.def.lowerY }
        e.walkTo(lift1.def.x + 20.0)

        // 2. The steam gate.
        e.waitUntil("him at door_1", 20.0) { e.heldAt("door_1") }
        e.walkTo(e.switch("sw_door_1").centerX - e.p.width / 2.0)
        e.waitForWindow("lvl11_pipe_1")
        e.press("sw_door_1")
        e.walkTo(1100.0)

        // 3. The far switch: up the shaft, across the room, down the hole.
        val catwalks = LevelData.LEVEL_11_LAYOUT.plainPlatforms.filter { it.height < 10.0 }.sortedBy { it.x }
        e.climbShaft(catwalks[0])
        e.walkTo(1640.0)
        assertEquals(440.0, e.p.y + e.p.height, 0.5, "dropped through the hole")
        e.waitUntil("him at door_2", 30.0) { e.heldAt("door_2") }
        e.press("sw_door_2")

        // 4. The control room.
        e.climbShaft(catwalks[1])
        e.waitUntil("him at door_3", 30.0) { e.heldAt("door_3") }
        e.press("sw_door_4")
        val g = w.guard
        val door5 = e.door("door_5")
        e.walkTo(e.switch("sw_door_5").centerX - e.p.width / 2.0)
        e.waitUntil("the guard in the bay", 60.0) { g.x > door5.frame.right + 40.0 && g.facing > 0.0 }
        e.press("sw_door_5")
        e.waitUntil("the bay shut", 5.0) { door5.isFullyShut }
        e.press("sw_door_3")
        e.walkTo(e.switch("sw_lift_2").centerX - e.p.width / 2.0)
        e.waitUntil("him at door_5 on lift_2", 30.0) { e.heldAt("door_5") }
        e.press("sw_lift_2")
        val lift2 = e.lift("lift_2")
        e.waitUntil("lift_2 up", 5.0) { lift2.topY == lift2.def.upperY }

        // 5. Close it behind you, and the bot.
        e.press("sw_door_6_far")
        e.waitUntil("him at door_6", 15.0) { e.heldAt("door_6") }
        val bot = w.cameraBots.first { it.id == "lvl11_bot_1" }
        e.waitUntil("the bot heading away", 30.0) { bot.facing > 0.0 && bot.x < 3960.0 }
        var guard = 0
        while (!bot.canDeactivate(e.p)) {
            e.step(1.0)
            assertTrue(++guard < 600, "caught up with the bot")
        }
        e.step(interact = true)
        e.step()
        assertTrue(bot.isDeactivated)
        e.press("sw_door_6_far")
        e.walkTo(4242.0)
        e.waitUntil("him on lift_3", 20.0) { e.pr.isHeld && e.pr.body.x > 4240.0 }
        e.pressHere("sw_lift_3")
        e.waitUntil("riding lift_3 down", 5.0) { e.lift("lift_3").topY == e.lift("lift_3").def.lowerY }
        assertEquals(440.0, e.p.y + e.p.height, 0.5, "rode down with him")

        // 6. Over the top: up past door_7, across the room's two jets, down behind the bot.
        e.climbShaft(catwalks[2])
        e.waitUntil("him at door_7", 20.0) { e.heldAt("door_7") }
        e.walkTo(4740.0)
        e.waitForWindow("lvl11_pipe_4")
        e.walkTo(4900.0)
        e.waitForWindow("lvl11_pipe_5")
        e.walkTo(5030.0)
        val bot2 = w.cameraBots.first { it.id == "lvl11_bot_2" }
        e.waitUntil("the duct bot heading back to the door", 30.0) { bot2.facing < 0.0 && bot2.x in 4940.0..4995.0 }
        e.walkTo(5070.0)
        assertEquals(440.0, e.p.y + e.p.height, 0.5, "dropped into the duct behind it")
        guard = 0
        while (!bot2.canDeactivate(e.p)) {
            e.step(-1.0)
            assertTrue(++guard < 600, "caught up with the duct bot")
        }
        e.step(interact = true)
        e.step()
        assertTrue(bot2.isDeactivated)
        e.press("sw_door_7")

        // 7. The steam lock.
        e.walkTo(e.switch("sw_door_8").centerX - e.p.width / 2.0)
        e.waitUntil("him at door_8", 20.0) { e.heldAt("door_8") }
        e.waitForWindow("lvl11_pipe_2")
        e.press("sw_door_8")
        e.walkTo(e.switch("sw_door_9").centerX - e.p.width / 2.0)
        e.waitUntil("him at door_9", 10.0) { e.heldAt("door_9") }
        e.waitForWindow("lvl11_pipe_3")
        e.press("sw_door_9")

        // 8. The way out.
        e.walkTo(e.lift("lift_4").def.x + 2.0)
        e.waitUntil("him on lift_4", 15.0) { e.pr.isHeld && e.pr.body.x > e.lift("lift_4").def.x }
        e.pressHere("sw_lift_4")
        e.waitUntil("out", 10.0) { w.isLevelComplete }
        return e
    }

    @Test
    fun testLevel11IsBeatableAsAnEscort() {
        val e = cleanRun()
        assertEquals(0, e.deaths, e.report)
        assertEquals(ObjectiveState.MET, e.world.bonusObjectiveState, "a clean run keeps him out of sight the whole way")
        println("WALKTHROUGH level_11: ${"%.1f".format(e.t)}s (target ${LevelData.DEFAULT_LEVEL_11.timeTargetSeconds}s)")
        val ratio = LevelData.DEFAULT_LEVEL_11.timeTargetSeconds / e.t
        assertTrue(ratio in 1.25..2.0, "target should be 1.25..2.0x the clean run (${"%.1f".format(e.t)}s, ratio ${"%.2f".format(ratio)})")
    }
}
