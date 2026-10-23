package test

import game.model.*
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.*

/**
 * Level 12 ("12: Final Escape"): the prisoner walked out of the shipyard along the container row -
 * each beat of LEVEL_12_LAYOUT on its own, the ways a beat can be done wrong and come back from,
 * the standing layout rules, and a scripted run of the whole level that the three-star target is
 * taken from.
 */
class Level12EscapeTest {

    private val layout = LevelData.LEVEL_12_LAYOUT

    /** Drives level 12 by script: walk somewhere, throw a switch, wait for something. */
    class Escape {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_12)
        val p = world.player
        val pr = world.prisoner!!
        val dt = 1.0 / 60.0
        var t = 0.0
        var deaths = 0
        var report = ""
        /** Called after every tick - for a test that watches the whole run. */
        var onStep: ((Escape) -> Unit)? = null

        init {
            world.onGameOver = {
                deaths++
                report = "t=${"%.1f".format(t)} player x=${p.x.toInt()} feet=${feet.toInt()} prisoner x=${pr.body.x.toInt()} " +
                    "feet=${prFeet.toInt()} prisonerLost=${world.prisonerLost} seen=${world.isPrisonerSeen} " +
                    "guards=${world.allGuards.map { "${it.x.toInt()}/${it.facing.toInt()}" }} bot=${bot.x.toInt()}/${bot.facing.toInt()}"
            }
        }

        val feet: Double get() = p.y + p.height
        val prFeet: Double get() = pr.body.y + pr.body.height
        fun lift(id: String) = world.lifts.first { it.id == id }
        fun switch(id: String) = world.doorSwitches.first { it.id == id }
        val bot: CameraBot get() = world.cameraBots.first { it.id == "lvl12_bot" }
        val yardBot: CameraBot get() = world.cameraBots.first { it.id == "lvl12_yard_bot" }
        val lever: Lever get() = world.levers.single()
        /** The guards left to right: 0 over the way in, 1 over the road to the gate. */
        fun guard(i: Int) = world.allGuards.sortedBy { it.patrolMinX }[i]
        fun gate(n: Int) = world.lasers.first { it.id == "lvl12_gate_$n" }
        val curtain: List<Laser> get() = world.lasers.filter { it.id.startsWith("lvl12_curtain_") }

        fun step(move: Double = 0.0, jump: Boolean = false, interact: Boolean = false, crouch: Boolean = false) {
            check(deaths == 0) { "died: $report" }
            world.update(dt, move, jump, crouch, interact)
            t += dt
            onStep?.invoke(this)
        }

        fun waitUntil(what: String, maxSeconds: Double = 60.0, crouch: Boolean = false, cond: () -> Boolean) {
            val until = t + maxSeconds
            while (!cond()) {
                assertTrue(t < until, "timed out waiting for $what at t=${"%.1f".format(t)} (player ${p.x.toInt()},${feet.toInt()}, prisoner ${pr.body.x.toInt()},${prFeet.toInt()})")
                step(crouch = crouch)
            }
        }

        fun wait(seconds: Double) {
            val until = t + seconds
            while (t < until) step()
        }

        /** Walks the body's left edge to [x] on whatever it is standing on (drops off a lip on the way). */
        fun walkTo(x: Double, maxSeconds: Double = 30.0, crouch: Boolean = false) {
            val until = t + maxSeconds
            while (abs(p.x - x) > 0.8 || !p.isGrounded) {
                assertTrue(t < until, "could not walk to x=${x.toInt()} (at ${p.x.toInt()},${feet.toInt()}, t=${"%.1f".format(t)})")
                val d = x - p.x
                val move = if (abs(d) <= 0.8) 0.0 else sign(d) * minOf(1.0, abs(d) / 6.0 + 0.05)
                step(move, crouch = crouch)
            }
        }

        /** Throws [switchId] from where the body stands now (on a lift, say). */
        fun pressHere(switchId: String) {
            assertTrue(switch(switchId).isPlayerInRange(p), "$switchId in reach from x=${p.x.toInt()},${feet.toInt()}")
            step(interact = true)
            step()
        }

        /** A jump press against the face ahead: the climb, ridden out. */
        fun climb(dir: Double = 1.0) {
            step(dir, jump = true)
            assertTrue(p.isClimbing, "a climb at x=${p.x.toInt()},${feet.toInt()}")
            val until = t + 4.0
            while (p.isClimbing || !p.isGrounded) {
                assertTrue(t < until, "climb did not finish")
                step(0.0)
            }
        }

        /** Runs at [hook] and jumps when its grip is [ahead] in front, riding the swing out. */
        fun swing(hook: Rect, dir: Double, ahead: Double = 86.0) {
            val gripX = Player.hookGripX(hook)
            val until = t + 10.0
            while ((gripX - p.centerX) * dir > ahead) {
                assertTrue(t < until, "never got to the hook")
                step(dir)
            }
            step(dir, jump = true)
            assertTrue(p.isSwinging, "the swing took (grip ${((gripX - p.centerX) * dir).toInt()} ahead)")
            while (p.isSwinging || !p.isGrounded) {
                assertTrue(t < until, "the swing did not land")
                step(0.0)
            }
        }

        /** Crosses a timed gate [dir]wards: waits clear of it for its beam to go out, then goes. */
        fun crossGate(gate: Laser, dir: Double) {
            val x = gate.bounds.centerX
            // Clear of the beam by a little more than half a body either side.
            walkTo(x - dir * (p.width / 2.0 + 24.0) - p.width / 2.0)
            waitUntil("${gate.id} to come on", 10.0) { gate.isActive }
            waitUntil("${gate.id} to go out", 10.0) { !gate.isActive }
            walkTo(x + dir * (p.width / 2.0 + 24.0) - p.width / 2.0)
        }

        /** A running jump onto [box] (48 tall at most), across it and off the far side. */
        fun hopOver(box: Rect) {
            walkTo(box.left - p.width - 16.0)
            step(1.0, jump = true)
            val until = t + 4.0
            while (p.x < box.right + 2.0 || !p.isGrounded) {
                assertTrue(t < until, "the hop over ${box.x.toInt()} did not land")
                step(1.0)
            }
        }

        /** Gate 1 of the fence by the gate: where it is in its on/off cycle (0 = just switched on). */
        val fencePhase: Double get() {
            val f = world.lasers.first { it.id == "lvl12_fence_1" }
            val c = f.activeDuration + f.inactiveDuration
            return (((world.totalElapsedSeconds + f.phaseOffsetSeconds) % c) + c) % c
        }

        /** Catches [bot] up from behind and switches it off. */
        fun switchOff(bot: CameraBot) {
            var n = 0
            while (!bot.canDeactivate(p)) {
                step(bot.facing)
                assertTrue(++n < 600, "caught up with the rover")
            }
            step(interact = true)
            step()
            assertTrue(bot.isDeactivated)
        }

        /** He is standing still at the lip whose edge is [right]. */
        fun heldAtX(right: Double): Boolean = pr.isHeld && abs(pr.bounds.right - right) < 12.0

        /** Puts him down free on the row with his right edge at [prRight], and the player at [px] on the row. */
        fun placeOnRow(prRight: Double, px: Double) {
            pr.restore(prRight - pr.body.width, ROW - 96.0)
            p.resetTo(px, ROW - 96.0)
        }
    }

    // ---- the whole level -------------------------------------------------------------------

    /**
     * The scripted clean run: every beat done the way the layout's doc describes, with no deaths
     * and nobody seen. The time it takes is the floor the three-star target is set from.
     */
    fun cleanRun(log: Boolean = false, onStep: ((Escape) -> Unit)? = null): Escape {
        val e = Escape()
        e.onStep = onStep
        val w = e.world
        fun mark(what: String) {
            if (log) println("  ${"%6.1f".format(e.t)}s  $what  (player ${e.p.x.toInt()},${e.feet.toInt()}, prisoner ${e.pr.body.x.toInt()},${e.prFeet.toInt()})")
        }
        val quay = layout.boxes.first { it.height == 96.0 }
        val chained = layout.boxes.first { LevelLayout.isChainedCrate(it) }
        val overwatch2 = overwatchCrates().last()
        val lift1 = e.lift("lift_1").def
        val lift2 = e.lift("lift_2").def
        val lift3 = e.lift("lift_3").def
        val lift4 = e.lift("lift_4").def

        // 1. The pickup: in under the first guard's crate behind his back, out the other side once
        // he has turned round, up the quay, under the chained crate, down beside the prisoner.
        // Footsteps carry 180 (a crouch is silent): the hop's landing is made with him up the far
        // end of his crate, and the rest of the way past him is crept.
        val g1 = e.guard(0)
        e.waitUntil("the first guard up the far end of his crate", 20.0) { g1.x >= g1.patrolMaxX - 0.5 }
        e.hopOver(layout.woodCrates.single().let { Rect(it.x, it.y, layout.barrels.single().right - it.x, it.height) })
        e.walkTo(overwatchCrates().first().left + 40.0, crouch = true)
        mark("under the first overwatch")
        e.waitUntil("the first guard to turn back", 20.0, crouch = true) {
            g1.state == GuardState.PATROL && g1.facing < 0.0 && g1.x < g1.patrolMaxX - 10.0
        }
        e.walkTo(quay.left - e.p.width - 1.0, crouch = true)
        e.wait(0.2)
        e.climb()
        mark("up on the quay")
        e.walkTo(chained.left - e.p.width - 4.0)
        e.walkTo(chained.right + 4.0, crouch = true)
        e.wait(0.3)
        e.walkTo(lift1.x + 70.0)
        assertEquals(GROUND, e.feet, 0.5, "down the shaft beside him")
        e.pressHere("sw_lift_1")
        mark("lift_1 going up")
        e.waitUntil("lift_1 up", 5.0) { e.lift("lift_1").topY == lift1.upperY }

        // 2. The rover: over the shaft on the hook while the rover is up the far end, back to the
        // lip until it turns, the rover off from behind, back down the shaft and up with him.
        e.walkTo(lift2.x - 300.0)
        val bot = e.bot
        e.waitUntil("the rover up the far end", 30.0) { bot.x > bot.patrolMaxX - 120.0 }
        e.swing(layout.swingHooks.single(), 1.0)
        mark("swung over lift_2's shaft")
        e.walkTo(lift2.x + lift2.width + 6.0)
        e.waitUntil("the rover heading away", 30.0) { bot.facing > 0.0 && bot.x < bot.patrolMinX + 40.0 }
        e.switchOff(bot)
        mark("rover off")
        e.walkTo(lift2.x + 70.0)
        assertEquals(GROUND, e.feet, 0.5, "down lift_2's shaft")
        e.waitUntil("him at lift_2's shaft", 30.0) { e.heldAtX(lift2.x) }
        e.pressHere("sw_lift_2")
        mark("lift_2 going up")
        e.waitUntil("lift_2 up", 5.0) { e.lift("lift_2").topY == lift2.upperY }

        // 3. The curtain: ahead to lift_3's shaft, down, through the gates to the lever and back.
        e.walkTo(lift3.x + 40.0)
        assertEquals(GROUND, e.feet, 0.5, "down lift_3's shaft")
        mark("in the yard")
        // Gate 3 crossed once the yard's bot has turned away, the bot caught past it.
        val yardBot = e.yardBot
        e.walkTo(e.gate(3).bounds.centerX + 24.0)
        e.waitUntil("the yard's bot heading away with gate 3 out", 30.0) {
            yardBot.facing < 0.0 && yardBot.x > yardBot.patrolMaxX - 40.0 && yardBot.pauseTimer == 0.0 &&
                e.gate(3).remainingInactiveTime(e.world.totalElapsedSeconds) > 0.5
        }
        e.walkTo(e.gate(3).bounds.centerX - e.p.width - 24.0)
        e.switchOff(yardBot)
        mark("yard bot off")
        e.crossGate(e.gate(2), -1.0)
        e.crossGate(e.gate(1), -1.0)
        e.walkTo(e.lever.centerX - e.p.width / 2.0)
        e.step(interact = true)
        e.step()
        assertTrue(e.curtain.all { it.isDisabled }, "the curtain is cut")
        mark("curtain cut")
        e.crossGate(e.gate(1), 1.0)
        e.crossGate(e.gate(2), 1.0)
        e.crossGate(e.gate(3), 1.0)
        e.walkTo(lift3.x + 70.0)
        e.waitUntil("him at lift_3's shaft", 30.0) { e.heldAtX(lift3.x) }
        e.pressHere("sw_lift_3")
        mark("lift_3 going up")
        e.waitUntil("lift_3 up", 5.0) { e.lift("lift_3").topY == lift3.upperY }

        // 4. The descent: off the end of the row onto the road, and the lift lowered on the fence's
        // beat while the second guard looks ahead.
        e.waitUntil("him on lift_4", 30.0) { e.heldAtX(lift4.x + lift4.width) }
        e.walkTo(lift4.x + lift4.width + 8.0)
        assertEquals(GROUND, e.feet, 0.5, "down on the road")
        val sw4 = e.switch("sw_lift_4")
        e.walkTo(sw4.centerX - e.p.width / 2.0)
        mark("at lift_4's switch")
        val g2 = e.guard(1)
        // On the fence's beat (he walks through each gap as it opens) and the guard's (he comes in
        // under the crate while the guard looks ahead and goes out after he has turned back).
        e.waitUntil("the fence's and the guard's beats to line up", 30.0) {
            secondsUntilHeTurnsBack(g2) in PRESS_BEFORE_TURN && e.fencePhase in PRESS_AT_FENCE_PHASE
        }
        e.pressHere("sw_lift_4")
        mark("lift_4 going down")
        // Close behind him through the fence, on the same wave, and in under the crate.
        e.waitUntil("him walking the road", 5.0) { e.prFeet > GROUND - 0.5 && e.pr.isWalking }
        val behind = e.t + 20.0
        while (e.pr.bounds.left <= overwatch2.right) {
            assertTrue(e.t < behind, "him past the guard")
            e.step(if (e.pr.body.x - e.p.x > TRAIL) 1.0 else 0.0)
        }
        mark("he is past the second overwatch")

        // 5. The gate.
        val until = e.t + 10.0
        while (!w.isLevelComplete) {
            assertTrue(e.t < until, "out through the gate")
            e.step(if (e.p.bounds.intersects(w.exitZone)) 0.0 else 1.0)
        }
        mark("out")
        return e
    }

    @Test
    fun testLevel12IsBeatableAsAnEscape() {
        val e = cleanRun(log = true)
        assertEquals(0, e.deaths, e.report)
        assertEquals(ObjectiveState.MET, e.world.bonusObjectiveState, "a clean run is never seen")
        println("WALKTHROUGH level_12: ${"%.1f".format(e.t)}s (target ${LevelData.DEFAULT_LEVEL_12.timeTargetSeconds}s)")
        val ratio = LevelData.DEFAULT_LEVEL_12.timeTargetSeconds / e.t
        assertTrue(ratio in 1.25..2.0, "target should be 1.25..2.0x the clean run (${"%.1f".format(e.t)}s, ratio ${"%.2f".format(ratio)})")
    }

    @Test
    fun testHisRoadGoesThroughNothing() {
        // "make it so that prisoner doesnt have to go through stuff the player cannot go"
        // (2026-10-21). All along the clean run he passes through no box, pole, hook or load - and
        // no laser that is on (that would have ended the run anyway).
        val solid = layout.boxes + layout.poles + layout.swingHooks
        val hit = LinkedHashSet<String>()
        val e = cleanRun(onStep = { esc ->
            if (esc.pr.isFree && !esc.pr.hasEscaped) {
                val b = esc.pr.bounds
                for (r in solid + esc.world.movingPlatforms.map { it.bounds }) {
                    // Through it: the box across his middle, not a corner his edge sinks past as he
                    // steps down onto a lift still coming up (a body walking off an edge does that).
                    if (b.centerX > r.left && b.centerX < r.right && minOf(b.bottom, r.bottom) - maxOf(b.top, r.top) > 3.0) {
                        hit += "${r.x.toInt()},${r.y.toInt()} ${r.width.toInt()}x${r.height.toInt()} (him at ${b.x.toInt()},${b.bottom.toInt()})"
                    }
                }
            }
        })
        assertTrue(e.world.isLevelComplete)
        assertTrue(hit.isEmpty(), "he passes through $hit")
    }

    // ---- the beats ---------------------------------------------------------------------------

    @Test
    fun testHeSitsOnTheFirstLiftUntilItMovesAndRidesItUp() {
        val e = Escape()
        e.wait(5.0)
        assertFalse(e.pr.isFree, "hidden on the lift until it moves")
        val lift1 = e.lift("lift_1")
        assertTrue(e.pr.bounds.centerX in lift1.def.x..(lift1.def.x + lift1.def.width), "sitting on lift_1")
        // In the shaft, on the lift beside him: its switch is in reach from there.
        e.p.resetTo(lift1.def.x + 70.0, GROUND - 96.0)
        e.wait(0.2)
        e.pressHere("sw_lift_1")
        e.waitUntil("him free", 1.0) { e.pr.isFree }
        e.waitUntil("up", 5.0) { lift1.topY == lift1.def.upperY }
        assertEquals(ROW, e.feet, 0.5, "the player rode it up with him")
        e.waitUntil("him walking the row", 5.0) { e.pr.bounds.left > lift1.def.x + lift1.def.width }
        assertEquals(ROW, e.prFeet, 0.5, "on the containers")
    }

    @Test
    fun testEachShaftHoldsHimUntilItsLiftComesUp() {
        for (id in listOf("lift_2", "lift_3")) {
            val e = Escape()
            val l = e.lift(id)
            e.bot.deactivate()
            e.curtain.forEach { it.disable() }
            e.placeOnRow(l.def.x - 150.0, l.def.x - 400.0)
            e.waitUntil("him held at $id's shaft", 5.0) { e.heldAtX(l.def.x) }
            e.wait(2.0)
            assertTrue(e.heldAtX(l.def.x), "waits there while $id is down")
            l.toggle()
            e.waitUntil("him across $id", 6.0) { e.pr.bounds.left > l.def.x + l.def.width }
            assertEquals(ROW, e.prFeet, 0.5, "never dropped")
        }
    }

    @Test
    fun testTheSwingCrossesLift2sShaftBothWays() {
        val e = Escape()
        e.bot.deactivate()
        val hook = layout.swingHooks.single()
        val lift2 = e.lift("lift_2").def
        e.p.resetTo(lift2.x - 300.0, ROW - 96.0)
        e.swing(hook, 1.0)
        assertEquals(ROW, e.feet, 0.5)
        assertTrue(e.p.x > lift2.x + lift2.width, "over on the rover's container")
        e.walkTo(lift2.x + lift2.width + 300.0)
        e.swing(hook, -1.0)
        assertEquals(ROW, e.feet, 0.5)
        assertTrue(e.p.bounds.right < lift2.x, "back on the first container")
        assertEquals(0, e.deaths, e.report)
    }

    @Test
    fun testRaisingLift2BeforeTheRoverIsOffGetsHimSeen() {
        val e = Escape()
        val l = e.lift("lift_2")
        e.placeOnRow(l.def.x - 100.0, l.def.x - 600.0)
        e.waitUntil("him held at lift_2's shaft", 5.0) { e.heldAtX(l.def.x) }
        l.toggle()
        var failed = false
        e.world.onGameOver = { failed = true }
        repeat(60 * 20) { if (!failed) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(failed && e.world.prisonerLost, "he walks into the rover's sight")
    }

    @Test
    fun testRaisingLift3BeforeTheCurtainIsCutLosesHim() {
        val e = Escape()
        e.bot.deactivate()
        val l = e.lift("lift_3")
        e.placeOnRow(l.def.x - 100.0, l.def.x - 600.0)
        e.waitUntil("him held at lift_3's shaft", 5.0) { e.heldAtX(l.def.x) }
        l.toggle()
        var failed = false
        e.world.onGameOver = { failed = true }
        repeat(60 * 10) { if (!failed) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(failed && e.world.prisonerLost, "he walks into the curtain")
        assertTrue(e.pr.bounds.right >= e.curtain.minOf { it.bounds.left }, "at the curtain")
    }

    @Test
    fun testEveryShaftLetsThePlayerOut() {
        // Each shaft's one switch is reached from its lift standing at the bottom.
        for (id in listOf("lift_1", "lift_2", "lift_3")) {
            val e = Escape()
            val l = e.lift(id)
            e.p.resetTo(l.def.x + 70.0, GROUND - 96.0)
            e.wait(0.3)
            e.pressHere("sw_$id")
            e.waitUntil("$id up", 5.0) { l.topY == l.def.upperY }
            assertEquals(ROW, e.feet, 0.5, "rode $id up")
        }
        // ...and a raised lift closes its shaft from above: a body walking off the lip lands on it.
        val e = Escape()
        val l = e.lift("lift_2")
        l.restore(true, l.def.upperY)
        e.p.resetTo(l.def.x - 60.0, ROW - 96.0)
        e.walkTo(l.def.x + 40.0)
        assertEquals(ROW, e.feet, 0.5, "walked onto the raised lift")
    }

    @Test
    fun testTheYardIsNeverATrap() {
        // lift_3 already up (he has crossed) and the player still down in the yard: the switch is in
        // reach from under the lift; the lift comes down onto nobody and takes the player back up.
        val e = Escape()
        e.yardBot.deactivate()
        val l = e.lift("lift_3")
        l.restore(true, l.def.upperY)
        e.p.resetTo(l.def.x + 70.0, GROUND - 96.0)
        e.wait(0.3)
        e.pressHere("sw_lift_3")
        e.wait(1.0)
        assertTrue(l.bounds.bottom <= e.p.bounds.top + 0.5, "it stops over the head under it")
        e.walkTo(l.def.x - 60.0)
        e.waitUntil("lift_3 down", 5.0) { l.topY == l.def.lowerY }
        e.walkTo(l.def.x + 70.0)
        e.pressHere("sw_lift_3")
        e.waitUntil("lift_3 up", 5.0) { l.topY == l.def.upperY }
        assertEquals(ROW, e.feet, 0.5, "back up")

        // Lowered with him on it, it carries him down and he waits on it against the next
        // container's face; raised, it carries him back up and he walks on.
        val f = Escape()
        f.bot.deactivate()
        f.yardBot.deactivate()
        f.curtain.forEach { it.disable() }
        val l3 = f.lift("lift_3")
        l3.restore(true, l3.def.upperY)
        f.pr.restore(l3.def.x + 20.0, ROW - 96.0)
        f.p.resetTo(l3.def.x - 222.0, GROUND - 96.0)
        l3.toggle()
        f.waitUntil("lift_3 down with him", 5.0) { l3.topY == l3.def.lowerY }
        f.wait(1.5)
        assertEquals(GROUND, f.prFeet, 0.5)
        assertTrue(f.pr.isHeld && f.pr.bounds.centerX in l3.def.x..(l3.def.x + l3.def.width), "waits on the lift")
        l3.toggle()
        f.waitUntil("him up and on", 6.0) { f.pr.bounds.left > l3.def.x + l3.def.width }
        assertEquals(ROW, f.prFeet, 0.5)
    }

    @Test
    fun testLoweringTheLastLiftEarlyStrandsNobody() {
        val e = Escape()
        e.bot.deactivate()
        e.curtain.forEach { it.disable() }
        val l = e.lift("lift_4")
        // Sent down before he gets there: he waits at the end of the row.
        l.toggle()
        e.waitUntil("lift_4 down", 5.0) { l.topY == l.def.lowerY }
        e.placeOnRow(l.def.x - 200.0, l.def.x - 600.0)
        e.waitUntil("him at the row's end", 5.0) { e.heldAtX(l.def.x) }
        e.p.resetTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0, GROUND - 96.0)
        e.wait(0.2)
        e.pressHere("sw_lift_4")
        e.waitUntil("lift_4 up", 5.0) { l.topY == l.def.upperY }
        e.waitUntil("him on it", 5.0) { e.heldAtX(l.def.x + l.def.width) }
        // The player at the switch is clear of the shaft, so down it comes.
        e.pressHere("sw_lift_4")
        e.waitUntil("lift_4 down", 5.0) { l.topY == l.def.lowerY }
        e.waitUntil("him off it", 5.0) { e.pr.bounds.left > l.def.x + l.def.width }
        assertEquals(GROUND, e.prFeet, 0.5)
    }

    @Test
    fun testTheFenceKillsHimOffItsBeat() {
        // Lowered while gate 1 is a beat early, he walks into a beam that is still on.
        val e = Escape()
        e.bot.deactivate()
        val l = e.lift("lift_4")
        e.pr.restore(l.def.x + l.def.width - 40.0, ROW - 96.0)
        e.p.resetTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0, GROUND - 96.0)
        e.waitUntil("gate 1 just switched on", 10.0) { e.fencePhase in 0.0..0.1 }
        var failed = false
        e.world.onGameOver = { failed = true }
        e.pressHere("sw_lift_4")
        repeat(60 * 8) { if (!failed) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(failed && e.world.prisonerLost, "a beam was on when he got to it")
        assertTrue(e.pr.bounds.right < e.world.lasers.filter { it.id.startsWith("lvl12_fence_") }.maxOf { it.bounds.right } + 1.0, "in the fence")
    }

    @Test
    fun testTheSecondGuardOnlyGlimpsesHimOffHisBeat() {
        // On the fence's beat but just after the second guard has turned back towards the lift:
        // he is seen coming (the optional objective goes) but gone under the crate before the alert
        // fills - the guard is the beat's stealth, the fence its lock.
        val e = Escape()
        e.bot.deactivate()
        val l = e.lift("lift_4")
        e.pr.restore(l.def.x + l.def.width - 40.0, ROW - 96.0)
        e.p.resetTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0, GROUND - 96.0)
        val g2 = e.guard(1)
        e.waitUntil("the guard turned back and the fence on its beat", 40.0) {
            g2.facing < 0.0 && g2.x > g2.patrolMaxX - 40.0 && e.fencePhase in PRESS_AT_FENCE_PHASE
        }
        var failed = false
        e.world.onGameOver = { failed = true }
        e.pressHere("sw_lift_4")
        var seen = false
        repeat(60 * 10) {
            if (!failed) {
                e.world.update(e.dt, 0.0, false, false, false)
                if (e.world.isPrisonerSeen) seen = true
            }
        }
        assertFalse(failed, "not caught")
        assertTrue(seen, "but seen")
        assertTrue(e.pr.hasEscaped, "and out")
    }

    @Test
    fun testNothingSeesHimWhereHeIsHeld() {
        // Each place he is left waiting is out of every guard's and the rover's sight, however long
        // the wait (a guard's whole beat and more).
        val e0 = Escape()
        val holds = listOf(e0.lift("lift_2").def.x, e0.lift("lift_3").def.x)
        for (lip in holds) {
            val e = Escape()
            e.placeOnRow(lip - 60.0, lip - 300.0)
            e.p.resetTo(236.0, GROUND - 96.0)
            e.wait(30.0)
            assertTrue(e.heldAtX(lip), "held at $lip")
            assertEquals(0, e.deaths, e.report)
        }
        val e = Escape()
        val l4 = e.lift("lift_4").def
        e.pr.restore(l4.x + 40.0, ROW - 96.0)
        e.p.resetTo(150.0, GROUND - 96.0)
        e.wait(30.0)
        assertTrue(e.heldAtX(l4.x + l4.width), "held on lift_4")
        assertEquals(0, e.deaths, e.report)
    }

    @Test
    fun testARespawnPutsHimAndTheLiftBackAsTheCheckpointHadThem() {
        val e = Escape()
        val l = e.lift("lift_2")
        e.placeOnRow(l.def.x - 100.0, l.def.x - 400.0)
        e.waitUntil("his hold at lift_2 to be a checkpoint", 10.0) { e.world.hasAdvancedCheckpoint }
        l.toggle()
        e.world.onGameOver = { }
        repeat(60 * 20) { if (!e.world.isGameOver) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(e.world.isGameOver && e.world.prisonerLost, "seen by the rover")
        assertTrue(e.world.respawnAtCheckpoint())
        assertEquals(l.def.lowerY, l.topY, 0.01, "lift_2 back down")
        assertTrue(abs(e.pr.bounds.right - l.def.x) < 12.0, "him back at the lip")
        assertFalse(e.bot.isDeactivated)
        assertEquals(ROW, e.feet, 0.5, "the player back on the row")
        assertTrue(e.p.bounds.right < l.def.x, "on his side of the shaft")
    }

    @Test
    fun testTheLevelEndsOnlyWithBothOfThemOut() {
        val e = Escape()
        val exit = e.world.exitZone
        e.p.resetTo(exit.x + 4.0, GROUND - 96.0)
        e.wait(1.0)
        assertFalse(e.world.isLevelComplete, "not without him")
        e.pr.restore(exit.x + 2.0, GROUND - 96.0)
        e.wait(0.2)
        assertTrue(e.world.isLevelComplete)
    }

    // ---- the layout ----------------------------------------------------------------------

    @Test
    fun testEveryCheckpointIsWhereALiftHoldsHim() {
        // Each zone ends at the lip of a shaft whose lift is down, or at lift_4's far lip.
        val lips = layout.lifts.filter { !it.startsUp }.map { it.x } + layout.lifts.filter { it.startsUp }.map { it.x + it.width }
        for (cp in layout.manualCheckpoints) {
            val z = cp.triggerZone!!
            assertTrue(lips.any { abs(it - z.right) < 0.5 }, "${cp.id} ends at a lip")
            assertEquals(ROW, z.bottom, 0.5, "${cp.id} is on the row")
            assertEquals(ROW, cp.y + 96.0, 0.5, "${cp.id} respawns the player on the row")
        }
        assertEquals(3, layout.manualCheckpoints.size)
    }

    @Test
    fun testUsesTheOutdoorLevelsMechanisms() {
        // "try to use as many mechanisms from other levels as possible" (2026-10-23).
        assertTrue(layout.hasStartFences, "the two start fences")
        assertEquals(2, layout.guards.size, "two overwatch guards")
        for (g in layout.guards) {
            assertTrue(g.visionTilt > 0.0, "torch tilted down, like levels 3 and 6")
            assertTrue(layout.hangingCrateVariant1.any { abs(it.top - g.surfaceY) < 0.5 && g.patrolMinX >= it.left && g.patrolMaxX + g.width <= it.right }, "on a hung crate")
        }
        assertEquals(1, layout.boxes.count { LevelLayout.isChainedCrate(it) }, "level 1's chained crate")
        assertEquals(1, layout.swingHooks.size, "level 5's swing hook")
        assertEquals(2, layout.cameraBots.size, "level 8's rovers")
        val lever = layout.levers.single()
        val cut = layout.lasers.filter { it.mechanismId == lever.targetMechanismId }
        assertEquals(3, cut.size, "level 6's lever and its curtain")
        assertTrue(cut.all { it.isAlwaysActive })
        assertTrue(layout.lasers.count { !it.isAlwaysActive } >= 3, "level 8's timed gates")
        assertTrue(layout.hangingCrateVariant1.size >= 5, "level 2's hung crates")
        assertTrue(layout.woodCrates.isNotEmpty() && layout.barrels.isNotEmpty(), "a wood crate and a barrel")
        assertEquals(4, layout.lifts.size, "freight lifts")
        assertTrue(layout.doorSwitches.size <= 4, "few switch panels - one per lift")
        assertTrue(LevelData.DEFAULT_LEVEL_12.hasRain, "level 2's rain")
        // Only the outdoor levels' mechanisms, plus lifts (2026-10-07).
        assertTrue(layout.doors.isEmpty() && layout.steamPipes.isEmpty() && layout.fans.isEmpty() && layout.conveyors.isEmpty())
        assertTrue(layout.roomBackdrops.isEmpty() && layout.hangingClimbTargets.isEmpty() && layout.prisonCell == null)
        // Above ground: nothing below the yard floor.
        assertTrue(layout.boxes.all { it.bottom <= GROUND + 0.5 } && layout.lifts.all { it.lowerY <= GROUND })
        assertEquals(null, layout.exitStructure, "out through the yard's own booth and fence")
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
        // "these crates on level 12 are in the wrong orientation. it should be turned 90 degrees".
        val crates = layout.boxes.flatMap { drawnCrates(it) } + layout.hookCrates.map { it.initialBounds.width to it.initialBounds.height }
        assertTrue(crates.isNotEmpty())
        for ((w, h) in crates) assertTrue(w > h, "a crate drawn ${w.toInt()} wide and ${h.toInt()} tall")
    }

    /**
     * Every size [boxes] etc. draw something at, as "what:size" - GameplayScene's box rules (crate
     * tiles, barrels, the chained crate, hung crates, blocks by height), and each prop's own drawn
     * size.
     */
    private fun drawnSizes(l: LevelLayout): Set<String> {
        val out = drawnBoxSizes(l.boxes, l)
        fun f(d: Double) = "%.1f".format(d)
        fun sz(r: Rect) = "${f(r.width)}x${f(r.height)}"
        l.hangingCrateVariant1.forEach { out += "hung1:${sz(it)}" }
        l.hangingCrateVariant2.forEach { out += "hung2:${sz(it)}" }
        l.poles.forEach { out += "pole:${sz(it)}" }
        l.movingPlatforms.forEach { out += "moving:${f(it.width)}x${f(it.height)}:${it.isVariant1}" }
        l.swingHooks.forEach { out += "hook:${sz(it)}" }
        l.hookCrates.forEach { out += "hookCrate:${sz(it.initialBounds)}" }
        l.pushCarts.forEach { out += "cart:${f(it.width)}x${f(it.height)}" }
        l.levers.forEach { out += "lever:${f(it.width)}x${f(it.height)}" }
        l.guards.forEach { out += "guard:${f(it.width)}x${f(it.height)}" }
        l.lifts.forEach { out += "lift:${f(it.width)}x${f(it.thickness)}" }
        l.cameraBots.forEach { out += "bot:${f(it.width)}x${f(it.height)}:${f(it.visionRange)}" }
        l.lasers.forEach { out += "emitter:${f(it.emitterScale)}" }
        l.exitStructure?.let { out += "exit:${l.exitStructureImage}:${sz(Rect(0.0, 0.0, it.width, it.height))}" }
        return out
    }

    private fun drawnBoxSizes(boxes: List<Rect>, l: LevelLayout?): MutableSet<String> {
        fun f(d: Double) = "%.1f".format(d)
        fun sz(r: Rect) = "${f(r.width)}x${f(r.height)}"
        val out = HashSet<String>()
        val special = l?.let { (it.barrels + it.woodCrates + it.tables + it.tableParts + it.tableDecorations + it.plainPlatforms +
            it.hangingCrateVariant1 + it.hangingCrateVariant2).toSet() }.orEmpty()
        for (b in boxes) {
            when {
                l != null && b in l.barrels -> out += "barrel:${sz(b)}"
                l != null && b in l.woodCrates -> generateSequence(b.height) { it - 48.0 }.takeWhile { it > 0.01 }
                    .forEach { out += "crate:${f(b.width)}x${f(minOf(48.0, it))}" }
                l != null && b in l.tables -> out += "table:${f(b.height)}"
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
        return out
    }

    @Test
    fun testEverythingIsDrawnAtASizeAnEarlierLevelDrawsIt() {
        // "any visual element should be identical to previous levels" (2026-10-08).
        val earlier = HashSet<String>()
        for (lvl in LevelData.DEFAULT_LEVELS.filter { it.id != "level_12" }) lvl.layout?.let { earlier += drawnSizes(it) }
        // Level 1 has no layout: its boxes are built in code (the chained crate among them).
        earlier += drawnBoxSizes(GameWorld.createDefault(LevelData.DEFAULT_LEVEL_1).boxes, null)
        val mine = drawnSizes(layout)
        assertEquals(emptySet(), mine - earlier, "drawn at a size no earlier level uses")
    }

    @Test
    fun testNothingToWatchIsAboveThePhoneScreensTop() {
        // "the camera and hook and other things are out of screen" (2026-10-08): a phone's screen
        // top is world y ~124 (GameplayScene's fixed framing).
        val screenTop = (410.0 + 70.0) - 480.0 / 1.35
        val floor = screenTop + 25.0
        layout.swingHooks.forEach { h ->
            val gripY = h.y + h.height * Player.HOOK_GRIP_Y_FRACTION
            assertTrue(gripY >= floor, "a hook's grip at $gripY")
        }
        layout.levers.forEach { assertTrue(it.y >= floor, "a lever at ${it.y}") }
        // The chained crate hangs from off the top like every load's chain; what shows is its foot.
        for (b in layout.boxes) assertTrue((if (LevelLayout.isChainedCrate(b)) b.bottom else b.top) >= floor, "a box top at ${b.top}")
        layout.guards.forEach { assertTrue(it.surfaceY - it.height >= floor, "a guard's head at ${it.surfaceY - it.height}") }
        // The row is the highest anyone stands: a head there is on screen.
        assertTrue(ROW - 96.0 >= floor)
        layout.lifts.forEach { assertTrue(it.upperY - 96.0 >= floor) }
    }

    companion object {
        const val GROUND = 440.0
        /** The container row's top - his road. */
        const val ROW = 296.0

        /** How long before the second guard turns back lift_4 is lowered in the clean run. */
        val PRESS_BEFORE_TURN = 2.7..5.0

        /** Where gate 1 of the fence is in its cycle when lift_4 is lowered in the clean run. */
        val PRESS_AT_FENCE_PHASE = 1.70..2.15

        /** How far behind him the player follows him through the fence. */
        const val TRAIL = 30.0

        /** The two overwatch guards' hung crates, left to right. */
        fun overwatchCrates(): List<Rect> =
            LevelData.LEVEL_12_LAYOUT.hangingCrateVariant1.filter { c -> LevelData.LEVEL_12_LAYOUT.guards.any { abs(it.surfaceY - c.top) < 0.5 && it.patrolMinX >= c.left && it.patrolMinX <= c.right } }
                .sortedBy { it.x }

        /**
         * Seconds until an overwatch guard walking right (or standing his dwell out at the right
         * end) turns to face left; 0 if he already does. Only for an undisturbed patrol.
         */
        fun secondsUntilHeTurnsBack(g: Guard): Double = when {
            g.state != GuardState.PATROL -> -1.0
            g.facing < 0.0 -> 0.0
            g.patrolPauseTimer > 0.0 -> g.patrolPauseTimer
            else -> (g.patrolMaxX - g.x) / g.speed + g.patrolPauseDuration
        }
    }
}
