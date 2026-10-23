package test

import game.model.*
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.*

/**
 * Level 12 ("12: Final Escape"): the escort along the dock walkway (LEVEL_12_LAYOUT's doc). A
 * scripted run of the whole level, which the three-star target is taken from, and runs that take
 * a beat at the wrong moment or in the wrong order, to show each beat has teeth.
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
        private var frameNo = 0
        private var stalledFor = 0.0
        /** Called after every frame, to watch the run. */
        var onStep: ((Escape) -> Unit)? = null

        init {
            world.onGameOver = {
                deaths++
                report = "t=${"%.1f".format(t)} player x=${p.x.toInt()} y=${(p.y + p.height).toInt()} prisoner x=${pr.body.x.toInt()} " +
                    "y=${(pr.body.y + pr.body.height).toInt()} prisonerLost=${world.prisonerLost} seen=${world.isPrisonerSeen} " +
                    "guards=${world.detectingGuards.map { it.x.toInt() }}"
            }
        }

        fun mp(id: String) = world.movingPlatforms.first { it.id == id }
        fun lever(id: String) = world.levers.first { it.id == id }
        val ferry1 get() = mp("lvl12_ferry_1")
        val gate get() = mp("lvl12_gate")
        val shuttle get() = mp("lvl12_shuttle")
        val ferry3 get() = mp("lvl12_ferry_3")
        /** The pit guards, left to right. */
        fun guard(i: Int) = world.allGuards.sortedBy { it.patrolMinX }[i]
        val slotCrate get() = world.hookCrates.single()
        fun cart(i: Int) = world.pushCarts.sortedBy { it.minX }[i]
        val feet get() = p.y + p.height
        val prFeet get() = pr.body.y + pr.body.height
        val onFloor get() = p.isGrounded && feet >= GROUND - 0.5
        val stalled get() = stalledFor > 0.05
        /** A jump press that re-arms: the player only jumps again after the button is let go. */
        val pulse get() = frameNo % 10 < 5
        /** Pressed every other frame - an edge-triggered button pressed over and over. */
        val alt get() = frameNo % 2 == 0

        fun step(move: Double = 0.0, jump: Boolean = false, interact: Boolean = false, crouch: Boolean = false) {
            check(deaths == 0) { "died: $report" }
            val before = p.x
            world.update(dt, move, jump, crouch, interact)
            stalledFor = if (abs(p.x - before) < 0.5) stalledFor + dt else 0.0
            frameNo++
            if (world.alertProgress > 0.0) {
                if (glimpses == 0 || t - lastGlimpse > 0.5) glimpseLog.add(
                    "t=${"%.1f".format(t)} player ${p.x.toInt()},${feet.toInt()} prisoner ${pr.body.x.toInt()},${prFeet.toInt()} " +
                        "by guards=${world.detectingGuards.map { "${it.x.toInt()}/${it.facing}" }} prisonerSeen=${world.isPrisonerSeen}"
                )
                lastGlimpse = t
                glimpses++
            }
            t += dt
            onStep?.invoke(this)
        }

        fun waitUntil(what: String, maxSeconds: Double = 60.0, cond: () -> Boolean) {
            val until = t + maxSeconds
            while (!cond()) {
                assertTrue(t < until, "timed out waiting for $what at t=${"%.1f".format(t)} (player x=${p.x.toInt()},${feet.toInt()}, prisoner x=${pr.body.x.toInt()},${prFeet.toInt()})")
                step()
            }
        }

        /** Repeats [input] until [cond], failing after [maxSeconds]. */
        fun doUntil(what: String, maxSeconds: Double = 30.0, cond: () -> Boolean, input: () -> Unit) {
            val until = t + maxSeconds
            while (!cond()) {
                assertTrue(t < until, "timed out: $what at t=${"%.1f".format(t)} (player x=${p.x.toInt()},${feet.toInt()}, prisoner x=${pr.body.x.toInt()},${prFeet.toInt()})")
                input()
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

        /** Jumps [dir] from where the body stands and keeps moving until it lands; returns the feet it landed on. */
        fun hop(dir: Double = 1.0): Double {
            step(dir, jump = true)
            val until = t + 3.0
            while (!p.isGrounded || p.isClimbing) {
                assertTrue(t < until, "the hop did not land (at ${p.x.toInt()},${feet.toInt()})")
                step(dir)
            }
            return feet
        }

        /** Walks [dir] into a face and climbs (or hops) up it until the feet are on [top]. */
        fun upOnto(top: Double, what: String, dir: Double = 1.0) =
            doUntil("up onto $what", 8.0, { p.isGrounded && !p.isClimbing && abs(feet - top) < 0.5 }) {
                step(dir, jump = stalled && p.isGrounded && !p.isClimbing && pulse)
            }

        /** Walks [dir] until the body has stepped off an edge and landed below. */
        fun walkOff(dir: Double = 1.0, crouch: Boolean = false): Double {
            val start = feet
            val until = t + 5.0
            while (!(p.isGrounded && feet > start + 1.0)) {
                assertTrue(t < until, "did not walk off the edge (at ${p.x.toInt()},${feet.toInt()})")
                step(dir, crouch = crouch)
            }
            return feet
        }

        fun pull(leverId: String) {
            val l = lever(leverId)
            walkTo(l.centerX - p.width / 2.0)
            assertTrue(l.isPlayerInRange(p), "$leverId in reach")
            step(interact = true)
            step()
            assertTrue(l.isActivated, "$leverId pulled")
        }

        /** Takes hold of [cart] from the side the body is on, pushes it to [toX] and lets go. */
        fun pushCart(cart: PushCart, toX: Double) {
            doUntil("take the cart", 10.0, { world.grippedCart != null }) { step(sign(cart.x - p.x), interact = stalled && onFloor && alt) }
            doUntil("bend into the cart", 3.0, { !world.isSettlingIntoCart }) { step() }
            doUntil("push the cart", 20.0, { abs(cart.x - toX) < 0.5 }) { step(sign(toX - cart.x)) }
            step(interact = true)
            step()
            assertNull(world.grippedCart, "let go of the cart")
        }

        /** Rides [load] at its leading end until it has carried the body to the surface ahead, then walks on. */
        fun rideTo(load: MovingPlatform, ontoLeft: Double, what: String, maxSeconds: Double = 15.0) {
            doUntil("riding to $what", maxSeconds, { load.bounds.right >= ontoLeft - 0.5 }) {
                step(if (p.x + p.width < load.bounds.right - 8.0) 1.0 else 0.0)
            }
            doUntil("off onto $what", 3.0, { p.isGrounded && p.x > ontoLeft + 4.0 }) { step(1.0) }
        }

        /** He is standing held on [load], at its far lip. */
        fun heldOn(load: MovingPlatform): Boolean =
            pr.isHeld && pr.body.isGrounded && abs(prFeet - load.top) < 1.0 &&
                pr.body.centerX > load.left && pr.body.centerX < load.right

        /** He is standing on the walkway against the gate crate at rest. */
        val heldAtTheGate: Boolean
            get() = pr.isHeld && pr.body.isGrounded && abs(prFeet - HIGH_TOP) < 0.5 &&
                abs(pr.body.x + pr.body.width - gate.left) < 1.5

        /** The shuttle's time into its 12s cycle (0: docked at Q2, 6: docked at Q3). */
        val shuttleClock: Double
            get() {
                val period = 12.0
                return ((world.totalElapsedSeconds % period) + period) % period
            }
    }

    val layout = LevelData.LEVEL_12_LAYOUT

    companion object {
        const val GROUND = 440.0
        /** The walkway's top (144 up). */
        const val HIGH_TOP = 296.0
    }

    /**
     * How a run takes each beat: the clean run's choices by default, or a deliberately different
     * one - what the tests below use to show each beat has teeth.
     */
    data class Plan(
        /** Crouched (silent) off the ferry's end and out of the first guard's hearing. */
        val quietInThePit: Boolean = true,
        /** The shuttle's time into its cycle at which to pull the gate's lever. */
        val gateAt: Double = GATE_AT,
        /** The beat (1..4) to stop after. */
        val stopAfter: Int = 4
    )

    fun cleanRun(log: Boolean = false): Escape = run(Plan(), log)

    fun run(plan: Plan, log: Boolean = false, watch: ((Escape) -> Unit)? = null): Escape {
        val e = Escape()
        e.onStep = watch
        val w = e.world
        fun mark(what: String) { if (log) println("  ${"%6.1f".format(e.t)}s  $what  (player ${e.p.x.toInt()},${e.feet.toInt()}, prisoner ${e.pr.body.x.toInt()},${e.prFeet.toInt()})") }
        fun done(beat: Int): Boolean {
            if (plan.stopAfter > beat) return false
            e.wait(4.0)
            return true
        }
        val quays = layout.boxes.filter { it.top == HIGH_TOP && it.height == 144.0 }.sortedBy { it.x }
        val q2 = quays[2]
        val q3 = quays[3]
        val q4 = quays[4]

        // 1. The hideout: the cart against the container, up, and the slot's lever.
        val cart1 = e.cart(0)
        e.pushCart(cart1, cart1.maxX)
        e.upOnto(cart1.bounds.top, "the cart")
        e.upOnto(HIGH_TOP, "the container")
        mark("on the container")
        assertFalse(e.pr.isFree, "still sitting")
        e.pull("lvl12_slot_lever")
        e.waitUntil("the crate in the slot", 5.0) { e.slotCrate.isLanded }
        assertEquals(HIGH_TOP, e.slotCrate.bounds.top, 0.01, "flush with the walkway")
        assertTrue(e.pr.isFree, "up on his feet")
        mark("slot filled")
        e.waitUntil("him on the first ferry", 20.0) { e.heldOn(e.ferry1) }
        mark("he is on the first ferry")
        if (done(1)) return e

        // 2. The ferry: crouched off its end into the pit as the guard turns away, out of his
        // hearing, the lever (out of his reach), and out up the crates once it has gone back. The
        // ferry's own slab hides him from the pit below while he rides it.
        val g1 = e.guard(0)
        // Crouched from here on: a walking body is heard 180 away, and he is just below.
        e.walkTo(e.ferry1.right - e.p.width + 4.0, crouch = plan.quietInThePit)
        e.waitUntil("the first guard setting off left, away", 20.0) { g1.facing < 0.0 && g1.patrolPauseTimer <= 0.0 && g1.x > g1.patrolMaxX - 8.0 }
        e.walkOff(crouch = plan.quietInThePit)
        assertEquals(GROUND, e.feet, 0.5, "down in the pit")
        mark("in the first pit")
        // Crouched (silent) until out of his hearing - a walking body is heard 180 away.
        e.walkTo(g1.patrolMaxX + g1.width / 2.0 + NoiseLevel.NORMAL.radius - e.p.width / 2.0 + 4.0, crouch = plan.quietInThePit)
        val ferryLever = e.lever("lvl12_ferry_1_lever")
        e.walkTo(ferryLever.centerX - e.p.width / 2.0)
        e.pull("lvl12_ferry_1_lever")
        mark("ferry going")
        e.waitUntil("him over on Q2", 10.0) { e.pr.body.x > q2.x + 4.0 && e.pr.body.isGrounded }
        mark("he is on Q2")
        e.waitUntil("the ferry back clear of the crates", 10.0) { e.ferry1.right < q2.x - 2.0 * 68.0 - 40.0 }
        e.upOnto(GROUND - 48.0, "the step crate")
        e.upOnto(GROUND - 96.0, "the crates")
        e.upOnto(HIGH_TOP, "Q2")
        mark("up on Q2")
        if (done(2)) return e

        // 3. The gate: he waits at it; pull its lever so that he reaches the shuttle as it docks
        // and crosses while the second guard looks away. Then over the gate, and the shuttle.
        e.waitUntil("him at the gate", 20.0) { e.heldAtTheGate }
        mark("he is at the gate")
        e.walkTo(e.lever("lvl12_gate_lever").centerX - e.p.width / 2.0)
        e.waitUntil("the shuttle ${"%.1f".format(plan.gateAt)}s into its cycle", 15.0) { abs(e.shuttleClock - plan.gateAt) < e.dt }
        e.pull("lvl12_gate_lever")
        mark("gate going up")
        e.waitUntil("him on Q3", 20.0) { e.pr.body.x > q3.x + 4.0 && e.pr.body.isGrounded }
        mark("he is on Q3")
        e.waitUntil("the gate back down", 8.0) { !e.gate.isActive }
        e.walkTo(e.gate.left - e.p.width - 2.0)
        val overGate = e.hop()
        assertEquals(e.gate.top, overGate, 0.5, "onto the gate crate")
        e.walkOff()
        e.walkTo(q2.right - e.p.width - 2.0)
        e.waitUntil("the shuttle docked at Q2", 15.0) { e.shuttle.left < q2.right + 1.0 }
        e.doUntil("onto the shuttle", 3.0, { e.p.isGrounded && e.p.x > q2.right + 4.0 }) { e.step(1.0) }
        e.rideTo(e.shuttle, q3.x, "Q3")
        mark("over on Q3")
        e.waitUntil("him on the second ferry", 20.0) { e.heldOn(e.ferry3) }
        mark("he is on the second ferry")
        if (done(3)) return e

        // 4. The cart pit: off the second ferry's end, the cart pushed right across the floor to
        // Q4's face, up it, and the lever.
        e.walkTo(e.ferry3.right - e.p.width + 4.0)
        e.walkOff()
        assertEquals(GROUND, e.feet, 0.5, "down in the cart pit")
        val cart2 = e.cart(1)
        assertTrue(e.p.x + e.p.width <= cart2.x + 1.0 && e.onFloor, "landed behind the cart (at ${e.p.x.toInt()},${e.feet.toInt()})")
        e.pushCart(cart2, cart2.maxX)
        e.upOnto(cart2.bounds.top, "the cart")
        e.upOnto(HIGH_TOP, "Q4")
        mark("up on Q4")
        e.pull("lvl12_ferry_3_lever")
        mark("second ferry going")
        e.waitUntil("him out", 20.0) { e.pr.hasEscaped }
        mark("he is out")
        val until = e.t + 20.0
        while (!w.isLevelComplete) {
            assertTrue(e.t < until, "walked out after him (at ${e.p.x.toInt()}, prisoner ${e.pr.body.x.toInt()})")
            e.step(1.0)
        }
        mark("OUT")
        assertTrue(q4.right > w.exitZone.right)
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

    // ---- each beat has a wrong moment or a wrong move as well as a right one --------------

    @Test
    fun testWalkingInThePitGetsYouHeard() {
        assertNull(outcome(Plan(stopAfter = 2)), "crouched off the ferry and out of his hearing")
        assertNotNull(outcome(Plan(quietInThePit = false, stopAfter = 2)), "walking: he hears it and turns")
    }

    @Test
    fun testOpeningTheGateOffTheShuttlesBeatGetsHimSeen() {
        assertNull(outcome(Plan(stopAfter = 3)), "the gate opened ${GATE_AT}s into the shuttle's cycle")
        assertNotNull(outcome(Plan(gateAt = GATE_AT + 3.0, stopAfter = 3)), "he waits at the lip for the next trip, in the guard's sight")
        assertNotNull(outcome(Plan(gateAt = GATE_AT - 2.0, stopAfter = 3)), "he reaches the lip as it leaves")
    }

    @Test
    fun testTheGateComingDownOnHimLosesHim() {
        // Opened as he is still on his way to it, it comes back down while he is under it.
        val e = Escape()
        val gate = e.gate
        val q2 = layout.boxes.filter { it.top == HIGH_TOP && it.height == 144.0 }.sortedBy { it.x }[2]
        gate.activate()
        e.wait(1.7)
        e.pr.restore(q2.x + 10.0, HIGH_TOP - 96.0)
        e.waitUntil("him lost under it", 8.0) { e.world.isGameOver }
        assertTrue(e.world.prisonerLost, "crushed")
    }

    // ---- he goes nowhere the player cannot -----------------------------------------------

    @Test
    fun testHisRoadGoesThroughNothing() {
        // "make it so that prisoner doesnt have to go through stuff the player cannot go"
        // (2026-10-21). All along the clean run he passes through no box, pole, hook, load or cart.
        val solid = layout.boxes + layout.poles + layout.swingHooks
        val hit = LinkedHashSet<String>()
        val e = run(Plan(), watch = { esc ->
            if (esc.pr.isFree) {
                val b = esc.pr.bounds
                val moving = esc.world.movingPlatforms.map { it.bounds } + esc.world.pushCarts.map { it.bounds }
                for (r in solid + moving) {
                    if (minOf(b.right, r.right) - maxOf(b.left, r.left) > 0.5 && minOf(b.bottom, r.bottom) - maxOf(b.top, r.top) > 3.0) {
                        hit += "${r.x.toInt()},${r.y.toInt()} ${r.width.toInt()}x${r.height.toInt()} (him at ${b.x.toInt()},${b.bottom.toInt()})"
                    }
                }
            }
        })
        assertTrue(e.world.isLevelComplete)
        assertTrue(hit.isEmpty(), "he passes through $hit")
    }

    @Test
    fun testHeRidesAFerry() {
        val e = Escape()
        val f = e.ferry1
        e.pr.restore(f.right - 50.0, HIGH_TOP - 96.0)
        e.waitUntil("him held on the ferry", 5.0) { e.heldOn(f) }
        f.activate()
        e.waitUntil("him carried over", 10.0) { e.pr.body.x > f.maxX + f.width - 20.0 && e.pr.body.isGrounded }
        assertEquals(HIGH_TOP, e.prFeet, 0.5, "never dropped")
    }

    // ---- the layout ----------------------------------------------------------------------

    @Test
    fun testNothingFromTheEarlierLevel12s() {
        // "completely remove the stuff from previous version and make it a fresh level"
        // (2026-10-22): no lifts or switches, no course of hung crates over his head (so no
        // chained crate and no bobbing loads), no lasers, rovers, cameras or overwatch.
        assertTrue(layout.lifts.isEmpty() && layout.doorSwitches.isEmpty() && layout.doors.isEmpty())
        assertTrue(layout.lasers.isEmpty() && layout.cameraBots.isEmpty() && layout.cameras.isEmpty() && layout.poles.isEmpty())
        assertTrue(layout.boxes.none { LevelLayout.isChainedCrate(it) })
        assertTrue(layout.movingPlatforms.none { !it.startsInactive && it.minY != it.maxY }, "no bobbing loads")
        assertTrue(layout.guards.all { it.surfaceY == GROUND && it.visionTilt < 0.0 }, "pit guards looking up, no overwatch")
        // Only the walkway's line is hung: the ferries, the shuttle and the gate.
        assertTrue(layout.hangingCrateVariant1.isEmpty() && layout.hangingCrateVariant2.isEmpty())
        // And still only the outdoor levels' mechanisms (2026-10-07).
        assertTrue(layout.steamPipes.isEmpty() && layout.fans.isEmpty() && layout.conveyors.isEmpty())
        assertTrue(layout.roomBackdrops.isEmpty() && layout.hangingClimbTargets.isEmpty() && layout.prisonCell == null)
    }

    @Test
    fun testUsesTheOutdoorLevelsMechanisms() {
        // "try to use as many game mechanics as from previous levels" (2026-10-22).
        assertTrue(layout.hasStartFences, "the two start fences")
        assertEquals(2, layout.pushCarts.size, "level 8's carts")
        assertTrue(layout.hookCrates.single().physical, "level 5's hook crate, dropped by a lever")
        assertEquals(4, layout.levers.size, "levers")
        val ferries = layout.movingPlatforms.filter { it.startsInactive && it.oneShot && it.maxX > it.minX }
        assertEquals(2, ferries.size, "level 6's lever crates, as ferries")
        assertEquals(1, layout.movingPlatforms.count { !it.startsInactive && it.maxX > it.minX }, "level 2's moving crate, as a shuttle")
        assertEquals(1, layout.movingPlatforms.count { it.startsInactive && it.oneShot && it.minY != it.maxY && it.crushesOnContact }, "the gate crate")
        assertEquals(2, layout.guards.size, "level 3's walking guards")
        assertTrue(layout.woodCrates.isNotEmpty() && layout.barrels.isNotEmpty(), "level 3's wood crates, level 5's barrels")
        assertEquals("container17.png", layout.exitStructureImage, "level 8's Container 17")
        assertTrue(LevelData.DEFAULT_LEVEL_12.hasRain, "level 2's rain")
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
        val crates = layout.boxes.flatMap { drawnCrates(it) } +
            layout.hookCrates.map { it.initialBounds.width to it.initialBounds.height } +
            layout.pushCarts.map { it.width to it.height }
        assertTrue(crates.size >= 6, "the slot's crates, the steps, the hook crate and the carts")
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
        l.hangingCrateVariant1.forEach { out += "hung1:${sz(it)}" }
        l.hangingCrateVariant2.forEach { out += "hung2:${sz(it)}" }
        l.poles.forEach { out += "pole:${sz(it)}" }
        l.movingPlatforms.forEach { out += "moving:${f(it.width)}x${f(it.height)}:${it.isVariant1}" }
        l.swingHooks.forEach { out += "hook:${sz(it)}" }
        l.hookCrates.forEach { out += "hookCrate:${sz(it.initialBounds)}" }
        l.pushCarts.forEach { out += "cart:${f(it.width)}x${f(it.height)}" }
        l.levers.forEach { out += "lever:${f(it.width)}x${f(it.height)}" }
        l.guards.forEach { out += "guard:${f(it.width)}x${f(it.height)}" }
        l.exitStructure?.let { out += "exit:${l.exitStructureImage}:${sz(Rect(0.0, 0.0, it.width, it.height))}" }
        return out
    }

    @Test
    fun testEverythingIsDrawnAtASizeAnEarlierLevelDrawsIt() {
        // "any visual element should be identical to previous levels" (2026-10-08).
        val earlier = HashSet<String>()
        for (lvl in LevelData.DEFAULT_LEVELS.filter { it.id != "level_12" }) lvl.layout?.let { earlier += drawnSizes(it) }
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
        layout.movingPlatforms.forEach { assertTrue(minOf(it.y, it.minY, it.maxY) >= floor, "${it.id} rises to ${minOf(it.minY, it.maxY)}") }
        layout.levers.forEach { assertTrue(it.y >= floor, "a lever at ${it.y}") }
        for (b in layout.boxes) assertTrue(b.top >= floor, "a box top at ${b.top}")
        layout.hookCrates.forEach { assertTrue(it.initialBounds.top >= floor) }
        assertTrue(layout.exitStructure!!.top >= floor)
        // The walkway is the highest anyone stands: a head there is on screen.
        assertTrue(HIGH_TOP - 96.0 >= floor)
    }

    @Test
    fun testTheSlotCanOnlyBeCrossedOnTheDroppedCrate() {
        // The hook crate hangs out of a hop (51.2) over the walkway and down into the slot, so it is
        // a wall to both of them until its lever drops it - flush, half a unit off each face.
        val hc = layout.hookCrates.single().initialBounds
        val stack = layout.boxes.first { it.width == 68.0 && it.height == 96.0 && it.x < 1000.0 }
        assertTrue(HIGH_TOP - hc.top > Player(0.0, 0.0).maxJumpHeight, "out of a hop")
        assertTrue(hc.bottom > HIGH_TOP - 96.0 + 20.0, "low enough to stop a jump across")
        val e = Escape()
        e.slotCrate.detach()
        e.waitUntil("the crate in the slot", 5.0) { e.slotCrate.isLanded }
        val b = e.slotCrate.bounds
        assertEquals(HIGH_TOP, b.top, 0.01, "flush with the walkway")
        assertTrue(b.left - stack.left < 2.0 * Prisoner.CRACK, "a crack he walks over")
    }

    @Test
    fun testHeSitsUntilTheSlotCrateDrops() {
        val e = Escape()
        e.wait(5.0)
        assertFalse(e.pr.isFree, "sitting on the container")
        e.slotCrate.detach()
        e.wait(0.1)
        assertTrue(e.pr.isFree, "up as it drops")
        e.waitUntil("him on the first ferry", 20.0) { e.heldOn(e.ferry1) }
    }

    @Test
    fun testEveryCheckpointIsWhereSomethingHoldsHim() {
        // On a parked ferry's far lip, or against the gate.
        val holds = layout.movingPlatforms.filter { it.startsInactive && it.maxX > it.minX }.map { Rect(it.initialX, 0.0, it.width, 440.0) } +
            layout.movingPlatforms.filter { it.minY != it.maxY }.map { Rect(it.initialX - 60.0, 0.0, 60.0, 440.0) }
        for (cp in layout.manualCheckpoints) {
            val z = cp.triggerZone!!
            assertTrue(holds.any { z.left >= it.left - 0.5 && z.right <= it.right + 0.5 }, "${cp.id} is where he is held")
        }
    }

    @Test
    fun testHeStartsByThePlayer() {
        // "make it so that prisoner also starts at the beginning" (2026-10-21).
        val pr = layout.prisoner!!
        assertTrue(pr.x - layout.playerStartX < 400.0, "he starts ${(pr.x - layout.playerStartX).toInt()} from the player")
    }

    // ---- no dead ends ----------------------------------------------------------------------

    @Test
    fun testAPitIsNeverADeadEnd() {
        // Wherever a body ends up on a pit floor, on its side of the guard, there are crates or
        // barrels up a quay - or, in the last pit, the cart.
        val quays = layout.boxes.filter { it.top == HIGH_TOP && it.height == 144.0 }.sortedBy { it.x }
        // Pit 1, right of its guard: up the crates to Q2.
        val e1 = Escape()
        e1.p.resetTo(quays[1].right + 300.0, GROUND - 96.0)
        e1.wait(0.3)
        e1.upOnto(GROUND - 48.0, "the step crate")
        e1.upOnto(GROUND - 96.0, "the crates")
        e1.upOnto(HIGH_TOP, "Q2")
        // Pit 2, left of its guard: back up to Q2; right of it: up the barrels to Q3.
        val e2 = Escape()
        e2.p.resetTo(quays[2].right + 140.0, GROUND - 96.0)
        e2.wait(0.3)
        e2.upOnto(GROUND - 48.0, "the step crate", dir = -1.0)
        e2.upOnto(GROUND - 96.0, "the crates", dir = -1.0)
        e2.upOnto(HIGH_TOP, "Q2", dir = -1.0)
        val e3 = Escape()
        e3.p.resetTo(quays[3].x - 200.0, GROUND - 96.0)
        e3.wait(0.3)
        e3.upOnto(GROUND - 48.0, "the barrels")
        e3.upOnto(GROUND - 96.0, "the top barrels")
        e3.upOnto(HIGH_TOP, "Q3")
    }

    @Test
    fun testALeverPulledEarlyCanBePulledAgain() {
        // A ferry or the gate sent before he is on it or at it goes and comes back, and its lever
        // comes back up: pulled again, it does its job.
        val e = Escape()
        val f = e.ferry1
        val l = e.lever("lvl12_ferry_1_lever")
        f.activate()
        l.isActivated = true
        e.waitUntil("the ferry back and re-armed", 12.0) { !f.isActive && !l.isActivated }
        e.pr.restore(f.right - 50.0, HIGH_TOP - 96.0)
        e.waitUntil("him held on the ferry", 5.0) { e.heldOn(f) }
        f.activate()
        e.waitUntil("him carried over", 10.0) { e.pr.body.x > f.maxX + f.width - 20.0 && e.pr.body.isGrounded }
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

/** The clean run's gate pull: seconds into the shuttle's 12s cycle. */
const val GATE_AT = 5.0
