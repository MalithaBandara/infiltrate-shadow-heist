package test

import game.model.*
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.*

/**
 * Level 12 ("12: Final Escape"): the escort out of the yard, built from the outdoor levels'
 * mechanisms and lifts only (LEVEL_12_LAYOUT's doc). A scripted run of the whole level, which the
 * three-star target is taken from, and runs that take a beat at the wrong moment, to show each
 * beat has teeth.
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
                report = "t=${"%.1f".format(t)} player x=${p.x.toInt()} y=${(p.y + p.height).toInt()} prisoner x=${pr.body.x.toInt()} " +
                    "y=${(pr.body.y + pr.body.height).toInt()} prisonerLost=${world.prisonerLost} seen=${world.isPrisonerSeen} " +
                    "guards=${world.detectingGuards.map { it.x.toInt() }} cams=${world.detectingCameras.map { world.cameras.indexOf(it) }} " +
                    "bots=${world.detectingCameraBots.map { it.id }}"
            }
        }

        fun lift(id: String) = world.lifts.first { it.id == id }
        fun switch(id: String) = world.doorSwitches.first { it.id == id }
        fun bot(id: String) = world.cameraBots.first { it.id == id }
        fun bob(i: Int) = world.movingPlatforms.first { it.id == "lvl12_bob_$i" }
        val hangB get() = world.movingPlatforms.first { it.id == "lvl12_hang_b" }
        val deckLight get() = world.cameras[0]
        val gateLight get() = world.cameras[1]
        val feet get() = p.y + p.height

        fun step(move: Double = 0.0, jump: Boolean = false, interact: Boolean = false, crouch: Boolean = false) {
            check(deaths == 0) { "died: $report" }
            world.update(dt, move, jump, crouch, interact)
            if (world.alertProgress > 0.0) {
                if (glimpses == 0 || t - lastGlimpse > 0.5) glimpseLog.add(
                    "t=${"%.1f".format(t)} player ${p.x.toInt()},${feet.toInt()} prisoner ${pr.body.x.toInt()},${(pr.body.y + pr.body.height).toInt()} " +
                        "by guards=${world.detectingGuards.map { "${it.x.toInt()}/${it.facing}" }} cams=${world.detectingCameras.map { world.cameras.indexOf(it) }} " +
                        "bots=${world.detectingCameraBots.map { it.id }} prisonerSeen=${world.isPrisonerSeen}"
                )
                lastGlimpse = t
                glimpses++
            }
            t += dt
        }

        fun waitUntil(what: String, maxSeconds: Double = 60.0, cond: () -> Boolean) {
            val until = t + maxSeconds
            while (!cond()) {
                assertTrue(t < until, "timed out waiting for $what at t=${"%.1f".format(t)} (player x=${p.x.toInt()},${feet.toInt()}, prisoner x=${pr.body.x.toInt()})")
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
                assertTrue(t < until, "could not walk to x=${x.toInt()} (at ${p.x.toInt()},${feet.toInt()}, t=${"%.1f".format(t)})")
                val d = x - p.x
                val move = if (abs(d) <= 0.8) 0.0 else sign(d) * minOf(1.0, abs(d) / 6.0 + 0.05)
                step(move, crouch = crouch)
            }
        }

        /** Jumps right from where the body stands and keeps moving until it lands; returns the feet it landed on. */
        fun hopRight(): Double {
            step(1.0, jump = true)
            val until = t + 3.0
            while (!p.isGrounded || p.isClimbing) {
                assertTrue(t < until, "the hop did not land (at ${p.x.toInt()},${feet.toInt()})")
                step(1.0)
            }
            return feet
        }

        /**
         * Rides [platform] to [ontoLeft] (the left edge of what it carries you to): keeps to its
         * leading end while it moves, steps across once the gap is a stride.
         */
        fun rideAcross(platform: MovingPlatform, ontoLeft: Double, what: String) {
            val until = t + 12.0
            while (platform.bounds.right < ontoLeft - 12.0) {
                assertTrue(t < until, "rode across to $what")
                step(if (p.x + p.width < platform.bounds.right - 8.0) 1.0 else 0.0)
            }
            while (p.x < ontoLeft + 4.0) step(1.0)
            assertTrue(p.isGrounded && abs(feet - DECK_Y) < 0.5, "stepped across onto $what (at ${p.x.toInt()},${feet.toInt()})")
        }

        /** Mantles right onto [top] from where the body stands against it. */
        fun climbRightOnto(top: Double, what: String) {
            step(1.0, jump = true)
            val until = t + 4.0
            while (p.isClimbing || !p.isGrounded) {
                assertTrue(t < until, "the climb onto $what did not finish")
                step(0.0)
            }
            assertEquals(top, feet, 0.5, "climbed onto $what (at ${p.x.toInt()})")
        }

        /** A hop right that must land on a surface whose top is [top]. */
        fun hopRightOnto(top: Double, what: String) {
            val landed = hopRight()
            assertEquals(top, landed, 0.5, "hopped onto $what (at ${p.x.toInt()})")
        }

        /** Walks right until the body has stepped off an edge and landed below. */
        fun walkOffRight(): Double {
            val start = feet
            val until = t + 5.0
            while (!(p.isGrounded && feet > start + 1.0)) {
                assertTrue(t < until, "did not walk off the edge (at ${p.x.toInt()},${feet.toInt()})")
                step(1.0)
            }
            return feet
        }

        fun pressHere(switchId: String) {
            assertTrue(switch(switchId).isPlayerInRange(p), "$switchId in reach from x=${p.x.toInt()},${feet.toInt()}")
            step(interact = true)
            step()
        }

        fun press(switchId: String) {
            val sw = switch(switchId)
            walkTo(sw.centerX - p.width / 2.0)
            pressHere(switchId)
        }

        fun laserActiveAt(l: Laser, time: Double): Boolean {
            if (l.isDisabled) return false
            if (l.isAlwaysActive) return true
            val cycle = l.activeDuration + l.inactiveDuration
            val ph = ((time + l.phaseOffsetSeconds) % cycle).let { if (it < 0.0) it + cycle else it }
            return ph < l.activeDuration
        }

        /** Whether running right at full speed from here to [x] meets every one of [lasers] dark. */
        fun clearRunTo(x: Double, lasers: List<Laser>, speed: Double): Boolean {
            val now = world.totalElapsedSeconds
            val x0 = p.x
            for (l in lasers) {
                if (l.right < x0 || l.left > x + p.width) continue
                val tIn = maxOf(0.0, (l.left - p.width - x0) / speed)
                val tOut = (l.right - x0) / speed
                var tt = tIn - 0.12
                while (tt <= tOut + 0.12) {
                    if (laserActiveAt(l, now + tt)) return false
                    tt += 0.02
                }
            }
            return true
        }

        /** Waits where it stands for a straight run to [x] through [lasers] that meets them all dark, then runs it. */
        fun runThroughBeams(x: Double, lasers: List<Laser>, crouch: Boolean = false) {
            val speed = if (crouch) p.crouchForwardSpeed else p.moveSpeed
            waitUntil("a clear run through the beams to ${x.toInt()}", 20.0) { clearRunTo(x, lasers, speed) }
            while (p.x < x) step(1.0, crouch = crouch)
        }

        /** Runs right up to [fromX] (a swing wants a run-up) and swings from the hook ahead onto the deck past [landX]. */
        fun swingAcross(fromX: Double, landX: Double, landTop: Double) {
            if (p.x > fromX - 40.0) walkTo(fromX - 40.0)
            while (p.x < fromX) step(1.0)
            step(1.0, jump = true)
            assertTrue(p.isSwinging, "caught the hook from x=${p.x.toInt()}")
            val until = t + 4.0
            while (!p.isGrounded || p.x < landX) {
                assertTrue(t < until, "the swing did not land past ${landX.toInt()} (at ${p.x.toInt()},${feet.toInt()})")
                step(1.0)
            }
            assertEquals(landTop, feet, 0.5, "swung onto the deck")
        }

        /** Comes up behind [bot] and switches it off, as it sets off away from the player. */
        fun switchOff(bot: CameraBot) {
            waitUntil("${bot.id} setting off away", 30.0) {
                bot.facing * (bot.x - p.x) > 0.0 && bot.pauseTimer <= 0.0 &&
                    (if (bot.facing > 0.0) bot.x < bot.patrolMinX + 12.0 else bot.x > bot.patrolMaxX - 12.0)
            }
            val until = t + 10.0
            while (!bot.canDeactivate(p)) {
                assertTrue(t < until, "caught up with ${bot.id}")
                step(sign(bot.x - p.x))
            }
            step(interact = true)
            step()
            assertTrue(bot.isDeactivated, "${bot.id} off")
        }

        /** He is standing held on [lift] at its far lip, or against what stops him on it. */
        fun heldOn(liftId: String): Boolean {
            val l = lift(liftId)
            return pr.isHeld && pr.body.isGrounded && abs(pr.body.y + pr.body.height - l.topY) < 1.0 &&
                pr.body.centerX > l.def.x && pr.body.centerX < l.def.x + l.def.width
        }
    }

    val layout = LevelData.LEVEL_12_LAYOUT

    companion object {
        /** The gantry decks' and deck A's top (level 3's table). */
        const val DECK_Y = 296.0
    }

    /**
     * How a run takes each beat: the clean run's choices by default, or a deliberately different
     * moment - what the tests below use to show each beat has teeth.
     */
    data class Plan(
        /** Extra seconds spent at the gantry's end after lowering him, before going for the rovers. */
        val yardDelay: Double = 0.0,
        /** Seconds after the deck light settles back on the deck's near end before sending him up onto the deck. */
        val stackDelay: Double = 2.6,
        /** Seconds after the row's first guard sets off away from the lift before lowering him. */
        val rowDelay: Double = 1.0,
        /** Seconds after the gate light settles on the far lift's top before sending him up the wall. */
        val wallDelay: Double = 1.0,
        /** Seconds after the gate light settles on the road before lowering him off the far lift. */
        val outDelay: Double = 2.9,
        /** The beat (1..5) to stop after. */
        val stopAfter: Int = 5
    )

    fun cleanRun(log: Boolean = false): Escape = run(Plan(), log)

    fun run(plan: Plan, log: Boolean = false): Escape {
        val e = Escape()
        val w = e.world
        fun mark(what: String) { if (log) println("  ${"%6.1f".format(e.t)}s  $what  (player ${e.p.x.toInt()},${e.feet.toInt()}, prisoner ${e.pr.body.x.toInt()},${(e.pr.body.y + e.pr.body.height).toInt()})") }
        fun done(beat: Int): Boolean {
            if (plan.stopAfter > beat) return false
            e.wait(4.0)
            return true
        }

        // 1. The gantry: level 1's opening up to it, over him, across the gantry to its far end;
        // lower him.
        val (d1, d2) = layout.seamlessTables.sortedBy { it.x }
        val startBlock = layout.boxes.first { it.height == 96.0 && it.width > 300.0 }
        val chained = layout.boxes.first { LevelLayout.isChainedCrate(it) }
        e.walkTo(80.0)
        e.hopRightOnto(392.0, "the crate")
        e.walkTo(startBlock.left - e.p.width)
        e.hopRightOnto(startBlock.top, "the platform")
        e.walkTo(chained.right + 10.0, crouch = true)
        e.walkTo(startBlock.right - e.p.width)
        e.hopRightOnto(DECK_Y, "the pump house lift")
        mark("on the gantry")
        val barrels = layout.barrels.minBy { it.x }
        e.walkTo(barrels.left - e.p.width)
        e.hopRightOnto(barrels.top, "the barrels")
        e.walkOffRight()
        val gates = w.lasers.filter { it.id.startsWith("lvl12_gate_") }
        e.walkTo(gates.minOf { it.left } - 3.0 - e.p.width - 20.0)
        e.runThroughBeams(gates.maxOf { it.right } + 10.0, gates)
        e.swingAcross(d1.right - e.p.width, d2.left, DECK_Y)
        mark("across the swing")
        e.waitUntil("bob 0 at the top", 10.0) { e.bob(0).bounds.top < e.bob(0).minY + 1.0 }
        e.walkTo(d2.right - e.p.width)
        mark("through the bobbing loads")
        e.hopRightOnto(DECK_Y, "the first hanging crate")
        val hangA = layout.hangingCrateVariant2.minBy { it.x }
        e.walkTo(hangA.right - e.p.width)
        e.waitUntil("the moving crate near", 10.0) { e.hangB.bounds.left < e.hangB.minX + 1.0 }
        e.hopRightOnto(DECK_Y, "the moving crate")
        val hangC = layout.hangingCrateVariant1.first { it.y == DECK_Y }
        e.rideAcross(e.hangB, hangC.left, "the long crate")
        e.walkTo(hangC.right - e.p.width)
        e.hopRightOnto(DECK_Y, "the third deck")
        mark("across the hanging crates")
        // Level 6's lever crate: pull, step on before it moves, ride it over, step off.
        val rideLever = w.levers.first { it.id == "lvl12_ride_lever" }
        val ride = w.movingPlatforms.first { it.id == "lvl12_ride" }
        e.walkTo(rideLever.centerX - e.p.width / 2.0)
        e.step(interact = true)
        e.step()
        while (e.p.x < ride.bounds.left + 30.0) e.step(1.0)
        val d3b = layout.seamlessTables.first { it.x > ride.maxX }
        e.rideAcross(ride, d3b.left, "the last deck")
        mark("across on the ride")
        val leverCrate = layout.hangingCrateVariant2.last()
        e.walkTo(leverCrate.left - e.p.width - 1.0)
        e.hopRightOnto(leverCrate.top, "the lever's crate")
        val lever = w.levers.first { it.id == "lvl12_lever" }
        e.walkTo(lever.centerX - e.p.width / 2.0)
        e.step(interact = true)
        e.step()
        assertTrue(w.lasers.filter { it.id.startsWith("lvl12_curtain") }.all { it.isDisabled }, "the curtain is down")
        mark("curtain cut")
        e.walkOffRight()
        e.press("sw_pump")
        mark("pump lift lowered")
        if (done(1)) return e

        // 2. The yard: off the gantry's end, both rovers off from behind before he gets there.
        if (plan.yardDelay > 0.0) e.wait(plan.yardDelay)
        val botA = e.bot("lvl12_bot_a")
        val botB = e.bot("lvl12_bot_b")
        val gantryEnd = d3b.right
        e.walkTo(gantryEnd - e.p.width + 10.0)
        e.waitUntil("rover A heading away", 30.0) { botA.facing > 0.0 && botA.x < gantryEnd + 250.0 }
        e.walkOffRight()
        assertEquals(440.0, e.feet, 0.5, "down in the yard")
        e.switchOff(botA)
        mark("rover A off")
        e.switchOff(botB)
        mark("rover B off")
        e.walkTo(e.switch("sw_stack").centerX - e.p.width / 2.0)
        e.waitUntil("him at the stack", 60.0) { e.heldOn("lift_stack") }
        mark("he is at the stack")
        if (done(2)) return e

        // 3. The stack: up with him, through the gap he stops at, and send him over it on the deck
        // light's cue. Then run the deck's underside.
        val swStack = e.switch("sw_stack")
        e.walkTo(swStack.centerX - e.p.width / 2.0 + 14.0)
        e.pressHere("sw_stack")
        mark("riding up the stack")
        val stackLift = e.lift("lift_stack")
        e.waitUntil("the stack lift up", 5.0) { stackLift.topY == stackLift.def.upperY }
        e.waitUntil("him at the gap", 10.0) { e.pr.isHeld && e.pr.body.x > stackLift.def.x + stackLift.def.width }
        mark("he is at the gap")
        e.walkOffRight()
        assertEquals(440.0, e.feet, 0.5, "down through the gap")
        e.walkTo(e.switch("sw_gap").centerX - e.p.width / 2.0)
        e.waitUntil("the deck light heading back", 30.0) { e.deckLight.sweepDirection < 0.0 }
        e.waitUntil("the deck light back on the near end", 30.0) { e.deckLight.sweepDirection > 0.0 }
        e.wait(plan.stackDelay)
        e.pressHere("sw_gap")
        mark("gap lift up")
        val under = w.lasers.filter { it.id.startsWith("lvl12_low_") }
        fun beamX(i: Int) = under.first { it.id == "lvl12_low_$i" }.left
        e.walkTo(beamX(0) - e.p.width - 20.0)
        e.runThroughBeams(beamX(2) + 20.0, under)
        e.switchOff(e.bot("lvl12_low_bot_0"))
        e.walkTo(beamX(3) - e.p.width - 20.0)
        e.runThroughBeams(under.first { it.id == "lvl12_low_3" }.right + 20.0, under)
        e.walkTo(beamX(4) - e.p.width - 20.0)
        e.runThroughBeams(beamX(5) + 20.0, under)
        e.switchOff(e.bot("lvl12_low_bot_1"))
        e.walkTo(beamX(6) - e.p.width - 20.0)
        e.runThroughBeams(beamX(8) + 14.0, under)
        e.switchOff(e.bot("lvl12_low_bot_2"))
        e.walkTo(beamX(9) - e.p.width - 20.0)
        val rowLift = e.lift("lift_row")
        e.runThroughBeams(rowLift.def.x + 12.0, under)
        mark("under the row lift")
        e.waitUntil("him on the row lift", 60.0) { e.heldOn("lift_row") }
        if (done(3)) return e

        // 4. The row.
        val g1 = w.allGuards.minBy { it.x }
        val g2 = w.allGuards.maxBy { it.x }
        val rowCrates = layout.hangingCrateVariant1.filter { it.y == 300.0 }.sortedBy { it.x }
        // Under the first crate as its guard sets off away from you, across the gap as he turns
        // back (the second guard is still walking away then), out from under the second as its
        // guard turns back.
        e.waitUntil("guard 1 setting off away", 30.0) { g1.facing > 0.0 && g1.patrolPauseTimer <= 0.0 && g1.x < g1.patrolMinX + 8.0 }
        e.walkTo(rowCrates[0].x + 50.0)
        mark("under the first crate")
        e.waitUntil("guard 1 turning back", 30.0) { g1.facing < 0.0 && g1.patrolPauseTimer <= 0.0 && g1.x > g1.patrolMaxX - 8.0 }
        assertTrue(g2.facing > 0.0, "guard 2 walking away as guard 1 turns back")
        e.walkTo(rowCrates[1].x + 56.0)
        e.waitUntil("guard 2 turning back", 30.0) { g2.facing < 0.0 }
        e.walkTo(e.switch("sw_row").centerX - e.p.width / 2.0)
        mark("at the row's switch")
        // As the first guard turns his back on the lift and sets off along his crate.
        e.waitUntil("guard 1 setting off away from the lift", 30.0) { g1.facing > 0.0 && g1.patrolPauseTimer <= 0.0 && g1.x < g1.patrolMinX + 8.0 }
        e.wait(plan.rowDelay)
        e.pressHere("sw_row")
        mark("row lift lowered")
        e.waitUntil("him at the wall", 30.0) { e.heldOn("lift_wall") }
        mark("he is at the wall")
        if (done(4)) return e

        // 5. The wall: send him up as the gate light settles on the far lift (he is up and over
        // once it has gone), and down as it leaves the road (he is down as it comes back round to
        // the lift). Then ride up yourself and go over the same way.
        val swWall = e.switch("sw_wall")
        e.walkTo(swWall.centerX - e.p.width / 2.0 - 10.0)
        e.waitUntil("the gate light heading back", 30.0) { e.gateLight.sweepDirection < 0.0 }
        e.waitUntil("the gate light on the far lift", 30.0) { e.gateLight.sweepDirection > 0.0 }
        e.wait(plan.wallDelay)
        e.pressHere("sw_wall")
        mark("wall lifts up")
        e.waitUntil("him on the far lift", 30.0) { e.heldOn("lift_out") }
        mark("he is on the far lift")
        e.waitUntil("the gate light on the road", 30.0) { e.gateLight.sweepDirection < 0.0 }
        e.wait(plan.outDelay)
        e.pressHere("sw_wall")
        mark("wall lifts down")
        val wl = e.lift("lift_wall")
        e.waitUntil("the wall lift down", 5.0) { wl.topY == wl.def.lowerY }
        e.walkTo(swWall.centerX - e.p.width / 2.0 + 14.0)
        e.pressHere("sw_wall")
        mark("riding up the wall")
        e.waitUntil("the wall lift up", 5.0) { wl.topY == wl.def.upperY }
        val wall = layout.boxes.first { it.top == 296.0 && it.bottom == 440.0 && it.width == 200.0 }
        e.walkTo(wall.right - 60.0)
        e.waitUntil("him out", 20.0) { e.pr.hasEscaped }
        e.waitUntil("the gate light on the road", 30.0) { e.gateLight.sweepDirection < 0.0 }
        e.wait(3.4)
        val until = e.t + 20.0
        while (!w.isLevelComplete) {
            assertTrue(e.t < until, "walked out after him (at ${e.p.x.toInt()}, prisoner ${e.pr.body.x.toInt()})")
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
            e.glimpses > 0 -> "glimpsed (${e.glimpses} frames): ${e.glimpseLog.take(3)}"
            else -> null
        }
    } catch (ex: IllegalStateException) {
        ex.message
    } catch (ex: AssertionError) {
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
    fun testDawdlingAfterLoweringHimWalksHimIntoTheYardsRovers() {
        assertNull(outcome(Plan(stopAfter = 2)), "both rovers off before he gets there")
        assertNotNull(outcome(Plan(yardDelay = 30.0, stopAfter = 2)), "still on the gantry when he walks into the yard")
    }

    @Test
    fun testSendingHimUpTheStackAtTheWrongMomentGetsHimSeen() {
        assertNull(outcome(Plan(stopAfter = 3)), "up 2.6s after the deck light settles back on the near end")
        assertNotNull(outcome(Plan(stackDelay = 0.5, stopAfter = 3)), "up as it settles there")
        assertNotNull(outcome(Plan(stackDelay = 5.5, stopAfter = 3)), "up as it leaves")
    }

    @Test
    fun testLoweringHimIntoTheRowAtTheWrongMomentGetsHimSeen() {
        assertNull(outcome(Plan(stopAfter = 4)), "lowered as the first guard sets off away")
        assertNotNull(outcome(Plan(rowDelay = 3.0, stopAfter = 4)), "lowered 3s later")
        assertNotNull(outcome(Plan(rowDelay = 7.0, stopAfter = 4)), "lowered as he turns back")
    }

    @Test
    fun testGoingOverTheWallUnderTheGateLightGetsThemSeen() {
        assertNull(outcome(Plan()), "up and down on the light's cues")
        assertNotNull(outcome(Plan(wallDelay = 6.0)), "sent up late")
        assertNotNull(outcome(Plan(outDelay = 0.5)), "lowered early")
        assertNotNull(outcome(Plan(outDelay = 5.5)), "lowered late")
    }

    // ---- the layout ----------------------------------------------------------------------

    @Test
    fun testOnlyOutdoorMechanismsAndLifts() {
        // "in level 12 dont use any indoor mechanisms. only use the outdoor mechanisms + lifts"
        // (2026-10-07): no doors, steam, fans, guard room, hung pallets, conveyor or cell.
        assertTrue(layout.doors.isEmpty() && layout.steamPipes.isEmpty() && layout.fans.isEmpty())
        assertTrue(layout.roomBackdrops.isEmpty() && layout.hangingClimbTargets.isEmpty() && layout.prisonCell == null)
        assertTrue(layout.conveyors.isEmpty() && layout.conveyorCrates.isEmpty())
        // Lifts are what hold him; every switch works one.
        assertEquals(6, layout.lifts.size)
        assertEquals(5, layout.doorSwitches.size)
        val liftIds = layout.lifts.map { it.id }.toSet()
        assertTrue(layout.doorSwitches.all { sw -> sw.targets.all { it in liftIds } })
        // The outdoor levels' mechanisms (1, 2, 3, 5, 6, 8, 9).
        assertTrue(layout.swingHooks.isNotEmpty() && layout.levers.size == 2 && layout.barrels.isNotEmpty())
        assertTrue(layout.movingPlatforms.any { it.crushesOnlyFromBelow } && layout.movingPlatforms.any { it.oneShot })
        assertTrue(layout.movingPlatforms.any { it.maxX > it.minX && !it.startsInactive }, "a hanging crate on the move")
        assertTrue(layout.hangingCrateVariant1.isNotEmpty() && layout.hangingCrateVariant2.isNotEmpty())
        assertTrue(layout.guards.all { it.visionTilt > 0.0 } && layout.cameraBots.isNotEmpty() && layout.lasers.isNotEmpty())
        assertTrue(layout.seamlessTables.isNotEmpty() && layout.woodCrates.isNotEmpty() && layout.cameras.isNotEmpty())
        assertTrue(layout.boxes.any { LevelLayout.isChainedCrate(it) }, "level 1's chained crate")
        assertTrue(LevelData.DEFAULT_LEVEL_12.hasRain)
    }

    /** The size each crate-drawn box is cut into on screen - GameplayScene's box rules 3a, 6 and 6b. */
    private fun drawnCrates(box: Rect): List<Pair<Double, Double>> {
        if (box in layout.woodCrates) {
            val out = ArrayList<Pair<Double, Double>>()
            var remaining = box.height
            while (remaining > 0.01) { val h = minOf(48.0, remaining); out += box.width to h; remaining -= h }
            return out
        }
        if (box in layout.barrels || box in layout.tables || box in layout.plainPlatforms) return emptyList()
        if (box in layout.hangingCrateVariant1 || box in layout.hangingCrateVariant2) return emptyList()
        if (LevelLayout.isChainedCrate(box)) return emptyList()
        if (box.width in 50.0..85.0 && box.height in 40.0..160.0) {
            val count = (box.height / 48.0).toInt().coerceAtLeast(1)
            return List(count) { box.width to box.height / count }
        }
        if (box.height < 70.0 && box.width < 150.0) return listOf(box.width to box.height)
        return emptyList()
    }

    @Test
    fun testEveryCrateLiesFlat() {
        // "these crates on level 12 are in the wrong orientation. it should be turned 90 degrees":
        // the wall's steps were 50 wide and up to 120 tall, drawn as crates stood on end.
        val crates = layout.boxes.flatMap { drawnCrates(it) }
        assertTrue(crates.size >= 3, "the start's crate and the stack are crates")
        for ((w, h) in crates) assertTrue(w > h, "a crate drawn ${w.toInt()} wide and ${h.toInt()} tall")
    }

    /**
     * Every size [l] draws something at, as "what:size" - GameplayScene's box rules (crate tiles,
     * barrels, the chained crate, hung crates, blocks by height), and each prop's own drawn size.
     */
    private fun drawnSizes(l: LevelLayout): Set<String> {
        fun f(d: Double) = "%.1f".format(d)
        fun sz(r: Rect) = "${f(r.width)}x${f(r.height)}"
        val out = HashSet<String>()
        val special = (l.barrels + l.woodCrates + l.tables + l.tableParts + l.tableDecorations + l.plainPlatforms +
            l.hangingCrateVariant1 + l.hangingCrateVariant2).toSet()
        for (b in l.boxes) {
            when {
                b in l.barrels -> out += "barrel:${sz(b)}"
                b in l.woodCrates -> generateSequence(b.height) { it - 48.0 }.takeWhile { it > 0.01 }
                    .forEach { out += "crate:${f(b.width)}x${f(minOf(48.0, it))}" }
                b in l.tables -> out += "table:${f(b.height)}"
                b in special -> {}
                LevelLayout.isChainedCrate(b) -> out += "chained:${sz(b)}"
                b.width in 50.0..85.0 && b.height in 40.0..160.0 -> {
                    val n = (b.height / 48.0).toInt().coerceAtLeast(1)
                    out += "crate:${f(b.width)}x${f(b.height / n)}"
                }
                b.height < 70.0 && b.width < 150.0 -> out += "crate:${sz(b)}"
                else -> out += "block:${f(b.height)}"
            }
        }
        l.plainPlatforms.forEach { out += "plain:${sz(it)}" }
        l.hangingCrateVariant1.forEach { out += "hung1:${sz(it)}" }
        l.hangingCrateVariant2.forEach { out += "hung2:${sz(it)}" }
        l.poles.forEach { out += "pole:${sz(it)}" }
        l.passThroughLegs.forEach { out += "leg:${sz(it)}" }
        l.tableDecorations.forEach { out += "tableDeco:${sz(it)}" }
        l.movingPlatforms.forEach { out += "moving:${f(it.width)}x${f(it.height)}:${it.isVariant1}" }
        l.swingHooks.forEach { out += "hook:${sz(it)}" }
        l.levers.forEach { out += "lever:${f(it.width)}x${f(it.height)}" }
        l.lasers.forEach { out += "laserHousing:${it.emitterScale}" }
        (l.cameras + l.translucentCameras).forEach { out += "cameraCone:${f(it.visionFov)}/${f(it.visionRange)}" }
        l.guards.forEach { out += "guard:${f(it.width)}x${f(it.height)}" }
        l.cameraBots.forEach { out += "rover:${f(it.width)}x${f(it.height)}:${f(it.visionFov)}/${f(it.visionRange)}" }
        l.lifts.forEach { out += "lift:${f(it.width)}x${f(it.thickness)}" }
        return out
    }

    @Test
    fun testEverythingIsDrawnAtASizeAnEarlierLevelDrawsIt() {
        // "any visual element should be identical to previous levels. you cannot change sizes of
        // crates. sizes of poles or anything ... laser emittors should be same size in every level"
        // (2026-10-08).
        val earlier = HashSet<String>()
        for (lvl in LevelData.DEFAULT_LEVELS.filter { it.id != "level_12" }) {
            val l = lvl.layout
            if (l != null) {
                earlier += drawnSizes(l)
            } else {
                // Level 1 is built in code; its chained crate is what level 12 borrows from it.
                GameWorld.createDefault(lvl).boxes.filter { LevelLayout.isChainedCrate(it) }
                    .forEach { earlier += "chained:${"%.1f".format(it.width)}x${"%.1f".format(it.height)}" }
            }
        }
        val mine = drawnSizes(layout)
        assertEquals(emptySet(), mine - earlier, "drawn at a size no earlier level uses")
        assertEquals(setOf("laserHousing:0.6"), mine.filter { it.startsWith("laserHousing") }.toSet(), "level 8/9's housings")
    }

    @Test
    fun testNothingToWatchIsAboveThePhoneScreensTop() {
        // "the camera and hook and other things are out of screen" (2026-10-08). GameplayScene
        // frames the world at a fixed height: its bottom edge at baseGroundY (410) + 70, and a
        // phone's canvas is 480 high at the world zoom of 1.35 - so its top edge is world y ~124.
        // Everything to see or reach sits at least a HUD's depth below that, as in levels 3 and 5
        // (pole camera 214, hook grip 184). A hook's rope, a chain and a beam go off the top by design.
        val screenTop = (410.0 + 70.0) - 480.0 / 1.35
        val floor = screenTop + 25.0
        layout.poles.forEach { assertTrue(it.top >= floor, "a pole top at ${it.top}") }
        layout.cameras.forEach { assertTrue(it.y >= floor, "a camera at ${it.y}") }
        layout.swingHooks.forEach { h ->
            val gripY = h.y + h.height * Player.HOOK_GRIP_Y_FRACTION
            assertTrue(gripY >= floor, "a hook's grip at $gripY")
        }
        layout.movingPlatforms.forEach { assertTrue(minOf(it.minY, it.maxY) >= floor, "${it.id} rises to ${it.minY}") }
        layout.levers.forEach { assertTrue(it.y >= floor, "a lever at ${it.y}") }
        layout.guards.forEach { assertTrue(it.surfaceY - it.height >= floor, "a guard's head at ${it.surfaceY - it.height}") }
        for (b in layout.boxes.filterNot { LevelLayout.isChainedCrate(it) }) assertTrue(b.top >= floor, "a box top at ${b.top}")
        // Standing on a deck, the player's head is on screen too.
        assertTrue(layout.seamlessTables.all { it.top - 96.0 >= floor })
    }

    @Test
    fun testOneSwitchWorksALiftUpEachFaceOfTheWall() {
        val wall = layout.boxes.first { it.top == 296.0 && it.bottom == 440.0 && it.width == 200.0 }
        val up = layout.lifts.first { it.id == "lift_wall" }
        val down = layout.lifts.first { it.id == "lift_out" }
        assertEquals(wall.left, up.x + up.width, 0.01)
        assertEquals(wall.right, down.x, 0.01)
        assertTrue(up.upperY == wall.top && down.upperY == wall.top && !up.startsUp && !down.startsUp)
        assertEquals(setOf("lift_wall", "lift_out"), layout.doorSwitches.first { it.id == "sw_wall" }.targets.toSet())
    }

    @Test
    fun testNothingOfThePlayersRoadOverTheGantryReachesHisRoad() {
        // He walks the ground under the whole gantry: everything hung across its gaps, moving or
        // not, stays over his head.
        val gantryEnd = layout.seamlessTables.filter { it.x < 4000.0 }.maxOf { it.right }
        val head = 440.0 - 96.0
        for (m in layout.movingPlatforms) assertTrue(m.maxY + m.height < head, "${m.id} stays over his head")
        val pumpLift = layout.lifts.first { it.id == "lift_pump" }
        for (b in layout.boxes.filter { it.right <= gantryEnd && it.bottom < 440.0 && it.x > pumpLift.x }) {
            assertTrue(b.bottom < head, "a box at ${b.x.toInt()} over his head (bottom ${b.bottom.toInt()})")
        }
    }

    @Test
    fun testHeWaitsOnThePumpLiftUntilItMoves() {
        val e = Escape()
        e.wait(10.0)
        assertFalse(e.pr.isFree, "sitting on the lift")
        e.lift("lift_pump").toggle()
        e.wait(0.1)
        assertTrue(e.pr.isFree, "up as it starts down")
        val pumpLift = e.lift("lift_pump")
        e.waitUntil("him off the lift and walking under the gantry", 10.0) { e.pr.body.x > pumpLift.def.x + pumpLift.def.width && e.pr.body.isGrounded }
        assertEquals(440.0, e.pr.body.y + e.pr.body.height, 0.5, "on the ground")
    }

    @Test
    fun testTheStackHoldsHimAndTheGapHoldsHimAgain() {
        // Three crates, a wall to him and too tall to climb: only the lift beside them takes either
        // of them up. Past them, the gap's lift is the only way onto deck A for him, and the way
        // down under it for the player.
        val stack = layout.boxes.first { it.width == 68.0 && it.height == 144.0 }
        assertTrue(stack.height > Player(0.0, 0.0).climbMaxHeight, "not a climb")
        val up = layout.lifts.first { it.id == "lift_stack" }
        val gap = layout.lifts.first { it.id == "lift_gap" }
        val deckA = layout.seamlessTables.first { it.left == gap.x + gap.width }
        assertEquals(stack.left, up.x + up.width, 0.01)
        assertEquals(stack.right, gap.x, 0.01)
        assertTrue(up.upperY == stack.top && gap.upperY == stack.top && deckA.top == stack.top)
        assertEquals(440.0, layout.doorSwitches.first { it.id == "sw_gap" }.surfaceY, "worked from under the deck")
    }

    @Test
    fun testFallingOffTheGantryIsNeverADeadEnd() {
        // The ground under the gantry runs back to the start's platform and up onto the pump house lift.
        val e = Escape()
        val startBlock = layout.boxes.first { it.height == 96.0 && it.width > 300.0 }
        e.p.resetTo(1400.0, 440.0 - 96.0)
        e.wait(0.5)
        e.walkTo(startBlock.right + 6.0)
        // A climb up the platform's far end (under the pump house lift), then the hop onto the lift.
        repeat(20) { e.step(-1.0) }
        e.step(-1.0, jump = true)
        e.waitUntil("up on the platform", 4.0) { e.p.isGrounded && !e.p.isClimbing && abs(e.feet - startBlock.top) < 0.5 }
        e.walkTo(startBlock.right - e.p.width)
        e.hopRightOnto(DECK_Y, "the pump house lift")
    }

    @Test
    fun testEveryCheckpointIsWhereSomethingHoldsHim() {
        // On a lift, or on the stack's top at the gap.
        val stack = layout.boxes.first { it.width == 68.0 && it.height == 144.0 }
        val holds = layout.lifts.map { Rect(it.x, 0.0, it.width, 440.0) } + stack
        for (cp in layout.manualCheckpoints) {
            val z = cp.triggerZone!!
            assertTrue(holds.any { z.left >= it.left - 0.5 && z.right <= it.right + 0.5 }, "${cp.id} is where he is held")
        }
    }

    /** The clean run to the yard with both rovers off - he is still walking out under the gantry. */
    private fun inTheYard(): Escape {
        val e = run(Plan(stopAfter = 1))
        val gantryEnd = layout.seamlessTables.filter { it.x < 4000.0 }.maxOf { it.right }
        val botA = e.bot("lvl12_bot_a")
        e.walkTo(gantryEnd - e.p.width + 10.0)
        e.waitUntil("rover A heading away", 30.0) { botA.facing > 0.0 && botA.x < gantryEnd + 250.0 }
        e.walkOffRight()
        e.switchOff(botA)
        e.switchOff(e.bot("lvl12_bot_b"))
        return e
    }

    @Test
    fun testALiftSentUpBeforeHeGetsThereCanStillFetchHim() {
        // "make sure the level has a solution" (2026-10-19): the stack's lift sent up empty
        // before he got there used to let him walk in under it, and a lift never comes down on a
        // body - stuck for good. He waits at the shaft's foot now, and the lift comes back for him.
        val e = inTheYard()
        val up = e.lift("lift_stack")
        e.press("sw_stack")
        e.waitUntil("him waiting at the raised lift", 60.0) { e.pr.isHeld && e.pr.body.isGrounded }
        assertTrue(e.pr.body.x + e.pr.body.width <= up.def.x, "short of the shaft, not under the lift")
        e.pressHere("sw_stack")
        e.waitUntil("him held on the stack's lift", 15.0) { e.heldOn("lift_stack") }
        assertEquals(up.def.lowerY, up.topY, "fetched")
    }

    @Test
    fun testGoingUpTheStackAloneIsNotADeadEnd() {
        // Ride up without him and drop through the gap: the gap's switch also throws the stack's
        // lift, so from under the deck it brings that lift down for him, then takes him up to
        // the gap - the run's own state there.
        val e = inTheYard()
        val up = e.lift("lift_stack")
        val gap = e.lift("lift_gap")
        val swStack = e.switch("sw_stack")
        e.walkTo(swStack.centerX - e.p.width / 2.0 + 14.0)
        e.pressHere("sw_stack")
        e.waitUntil("the stack lift up", 5.0) { up.topY == up.def.upperY }
        e.walkOffRight()
        assertEquals(440.0, e.feet, 0.5, "down through the gap")
        e.walkTo(e.switch("sw_gap").centerX - e.p.width / 2.0)
        e.waitUntil("him waiting at the raised lift", 60.0) { e.pr.isHeld && e.pr.body.isGrounded }
        e.pressHere("sw_gap")
        e.waitUntil("him held on the stack's lift", 15.0) { e.heldOn("lift_stack") }
        e.pressHere("sw_gap")
        e.waitUntil("him at the gap", 15.0) { e.pr.isHeld && e.pr.body.x > up.def.x + up.def.width }
        assertTrue(gap.topY == gap.def.lowerY && abs(e.pr.body.y + e.pr.body.height - DECK_Y) < 0.5, "held at the gap, as in the run")
    }

    @Test
    fun testTheWallsLiftsSentUpEarlyCanStillFetchHim() {
        // The same at the wall: both its lifts thrown up before he is lowered off the row.
        val e = Escape()
        val wl = e.lift("lift_wall")
        wl.toggle()
        e.lift("lift_out").toggle()
        e.pr.restore(wl.def.x - 100.0, 440.0 - 96.0)
        e.waitUntil("him waiting at the raised lift", 10.0) { e.pr.isHeld && e.pr.body.isGrounded }
        assertTrue(e.pr.body.x + e.pr.body.width <= wl.def.x, "short of the shaft, not under the lift")
        wl.toggle()
        e.lift("lift_out").toggle()
        e.waitUntil("him held on the wall's lift", 15.0) { e.heldOn("lift_wall") }
    }

    @Test
    fun testTheLevelEndsOnlyWithBothOfThemOut() {
        val e = Escape()
        val exit = e.world.exitZone
        e.p.resetTo(exit.x + 10.0, exit.bottom - 96.0)
        e.wait(1.0)
        assertFalse(e.world.isLevelComplete, "not without him")
        e.pr.restore(exit.x + 40.0, exit.bottom - 96.0)
        e.wait(0.2)
        assertTrue(e.world.isLevelComplete)
    }
}
