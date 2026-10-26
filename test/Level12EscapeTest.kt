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
        val dockBot: CameraBot get() = world.cameraBots.first { it.id == "lvl12_dock_bot" }
        val cart: PushCart get() = world.pushCarts.single()
        fun lever(id: String): Lever = world.levers.first { it.id == id }
        val ferry: MovingPlatform get() = world.movingPlatforms.first { it.id == "lvl12_ferry" }
        /** The guards left to right: 0 the overwatch over the way in, 1 the road guard by lift_5. */
        fun guard(i: Int) = world.allGuards.sortedBy { it.patrolMinX }[i]
        val roadGuard: Guard get() = guard(1)
        fun gate(n: Int) = world.lasers.first { it.id == "lvl12_gate_$n" }
        val curtain: List<Laser> get() = world.lasers.filter { it.id.startsWith("lvl12_curtain_") }
        val roadBot: CameraBot get() = world.cameraBots.first { it.id == "lvl12_road_bot" }

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

        /** Throws the lever [id] from where the body stands now. */
        fun throwLever(id: String) {
            val l = lever(id)
            assertTrue(l.isPlayerInRange(p), "$id in reach from x=${p.x.toInt()},${feet.toInt()}")
            step(interact = true)
            step()
            assertTrue(l.isActivated, "$id thrown")
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

        /**
         * Takes hold of the cart from the side it is pushed from (its left), pushes it to [toX],
         * and lets go.
         */
        fun pushCartTo(toX: Double) {
            val c = cart
            walkTo(c.x - p.width - 4.0)
            var n = 0
            while (world.grippedCart == null) {
                assertTrue(++n < 30, "took hold of the cart from x=${p.x.toInt()}")
                step(interact = n % 2 == 1)
            }
            n = 0
            while (world.isSettlingIntoCart) { assertTrue(++n < 120, "braced"); step() }
            val until = t + 15.0
            while (c.x < toX - 0.5) {
                assertTrue(t < until, "pushed the cart to ${toX.toInt()} (at ${c.x.toInt()})")
                step(1.0)
            }
            step(interact = true)
            step()
            assertNull(world.grippedCart, "let go of the cart")
        }

        /** A running jump up onto [box] (48 tall at most), and on along it until stopped. */
        fun hopOnto(box: Rect) {
            walkTo(box.left - p.width - 16.0)
            val until = t + 6.0
            var n = 0
            while (!(p.isGrounded && abs(feet - box.top) < 0.5)) {
                assertTrue(t < until, "the hop onto ${box.x.toInt()} did not land")
                step(1.0, jump = p.isGrounded && feet > box.top + 1.0 && n++ % 12 == 0)
            }
            step(1.0)
        }

        /**
         * Level 2's way over its moving crates (LevelWalkthroughTest.runRight): walk to the lip of
         * whatever the body stands on, and jump once the next thing level with it - a crate swinging
         * in, or [target] - is within 60 of the body's front. Done standing on [target].
         */
        fun hopAlongTo(target: () -> Rect, maxSeconds: Double = 30.0) {
            val until = t + maxSeconds
            while (true) {
                val goal = target()
                if (p.isGrounded && abs(feet - goal.top) < 1.0 && p.centerX in (goal.left + 4.0)..(goal.right - 4.0)) return
                assertTrue(t < until, "never hopped across to ${goal.x.toInt()} (at ${p.x.toInt()},${feet.toInt()})")
                if (!p.isGrounded) { step(1.0); continue }
                val front = p.x + p.width * 0.75
                val surf = world.boxes + world.movingPlatforms.map { it.bounds } + world.lifts.map { it.bounds }
                fun supported(x: Double) = surf.any { x in it.x..it.right && abs(it.top - feet) < 2.0 }
                if (supported(front + 8.0)) { step(1.0); continue }
                val gap = surf.filter { it.x > front - 4.0 && it.top > feet - 28.0 && it.top < feet + 22.0 }.minOfOrNull { it.x - front } ?: 9999.0
                if (gap <= 60.0) step(1.0, jump = true) else step(0.0)
            }
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

        /**
         * Out of the way for a test about something else: on lift_1 against the first container,
         * where he waits unseen (he gets up and walks at the player's first move otherwise).
         */
        fun parkHim() {
            val l1 = lift("lift_1").def
            pr.restore(l1.x + l1.width - pr.body.width - 2.0, LIFT_DOWN - 96.0)
        }

        /** He has not got up yet: sitting where he started. */
        val sitting: Boolean get() = !pr.isFree

        /** He is standing still at the lip whose edge is [right]. */
        fun heldAtX(right: Double): Boolean = pr.isHeld && abs(pr.bounds.right - right) < 12.0

        /** Puts him down free on the row with his right edge at [prRight], and the player at [px] on the row. */
        fun placeOnRow(prRight: Double, px: Double) {
            pr.restore(prRight - pr.body.width, ROW - 96.0)
            p.resetTo(px, ROW - 96.0)
        }

        /** The road below lift_4 cleared, as the clean run leaves it: the road guard up on lift_5, the rover off. */
        fun openTheRoad() {
            val l5 = lift("lift_5")
            l5.restore(true, l5.def.upperY)
            roadGuard.placeAt(l5.def.x + 40.0, -1.0)
            roadGuard.y = ROW - roadGuard.height
            roadBot.deactivate()
        }

        /** The road guard sent off along the road to the exit - for a test about something else. */
        fun parkTheRoadGuard() {
            roadGuard.placeAt(world.exitZone.x, -1.0)
        }

        /** Up on the landing crate past lift_5, at its switch on the far corner. */
        fun atLift5sSwitch() {
            p.resetTo(switch("sw_lift_5").centerX - p.width / 2.0, ROW - 96.0)
            wait(0.2)
        }

        /** The road guard is standing on lift_5's deck. */
        val roadGuardOnLift5: Boolean get() {
            val l5 = lift("lift_5")
            val c = roadGuard.x + roadGuard.width / 2.0
            return abs(roadGuard.y + roadGuard.height - l5.topY) < 1.0 && c >= l5.def.x && c <= l5.def.x + l5.def.width
        }

        /**
         * From lift_5's switch: sent down while the road guard is past it heading on, and up once
         * he has come back onto its deck - he goes up with it, and paces it 144 over the road.
         */
        fun liftTheGuardAway() {
            val l5 = lift("lift_5")
            val g = roadGuard
            val right = l5.def.x + l5.def.width
            waitUntil("the road guard past lift_5, heading on", 30.0) { g.facing > 0.0 && g.x > right + 5.0 }
            pressHere("sw_lift_5")
            waitUntil("lift_5 down", 8.0) { l5.topY == l5.def.lowerY }
            waitUntil("the guard well onto its deck", 20.0) {
                val c = g.x + g.width / 2.0
                roadGuardOnLift5 && g.facing < 0.0 && c < right - 30.0
            }
            pressHere("sw_lift_5")
            waitUntil("lift_5 up with him", 6.0) { l5.topY == l5.def.upperY }
            assertEquals(ROW, g.y + g.height, 0.5, "the guard carried up")
        }

        /** The road's rover has just turned away at the near end of its beat. */
        val roadBotTurningAway: Boolean get() = roadBot.facing > 0.0 && roadBot.x < roadBot.patrolMinX + 10.0

        /** Off the landing crate's far end, down onto the road. */
        fun dropOffTheLandingCrate() {
            walkTo(landingCrate().right + 4.0)
            waitUntil("down on the road", 3.0) { p.isGrounded && abs(feet - GROUND) < 0.5 }
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
        val lift1 = e.lift("lift_1").def
        val lift2 = e.lift("lift_2").def
        val lift3 = e.lift("lift_3").def
        val lift4 = e.lift("lift_4").def

        // 1. Setting off: he gets up as the player first moves, so the first move is made on the
        // first guard's beat - he walks in under the crate while the guard looks ahead and is out
        // and on lift_1 unseen. Then the player creeps after him: in under the crate behind the
        // guard's back, out once he has turned round (footsteps carry 180, a crouch is silent),
        // onto lift_1, and up together.
        val g1 = e.guard(0)
        val overwatch1 = overwatchCrates().first()
        e.waitUntil("the first guard's beat to set off on", 30.0, crouch = true) { secondsUntilHeTurnsBack(g1) in SET_OFF_BEFORE_TURN }
        e.step(1.0, crouch = true)
        mark("set off")
        e.waitUntil("him on lift_1", 15.0, crouch = true) { e.heldAtX(lift1.x + lift1.width) }
        mark("he is on lift_1")
        e.waitUntil("the first guard up the far end of his crate", 30.0, crouch = true) { g1.x >= g1.patrolMaxX - 0.5 }
        e.walkTo(overwatch1.left + 40.0, crouch = true)
        mark("under the first overwatch")
        e.waitUntil("the first guard to turn back", 20.0, crouch = true) {
            g1.state == GuardState.PATROL && g1.facing < 0.0 && g1.x < g1.patrolMaxX - 10.0
        }
        e.walkTo(lift1.x + 70.0, crouch = true)
        assertEquals(LIFT_DOWN, e.feet, 0.5, "on lift_1 beside him")
        e.pressHere("sw_lift_1")
        mark("lift_1 going up")
        e.waitUntil("lift_1 up", 5.0) { e.lift("lift_1").topY == lift1.upperY }

        // 2. The rover: over the shaft on the hook while the rover is up the far end, back to the
        // lip until it turns, the rover off from behind, back down the shaft and up with him.
        e.walkTo(lift2.x - 300.0)
        val bot = e.bot
        e.waitUntil("the rover up the far end", 30.0) { bot.x > bot.patrolMaxX - 120.0 }
        e.swing(layout.swingHooks.minBy { it.x }, 1.0)
        mark("swung over lift_2's shaft")
        e.walkTo(lift2.x + lift2.width + 6.0)
        e.waitUntil("the rover heading away", 30.0) { bot.facing > 0.0 && bot.x < bot.patrolMinX + 40.0 }
        e.switchOff(bot)
        mark("rover off")
        e.walkTo(lift2.x + 70.0)
        assertEquals(LIFT_DOWN, e.feet, 0.5, "down lift_2's shaft")
        e.waitUntil("him at lift_2's shaft", 30.0) { e.heldAtX(lift2.x) }
        e.pressHere("sw_lift_2")
        mark("lift_2 going up")
        e.waitUntil("lift_2 up", 5.0) { e.lift("lift_2").topY == lift2.upperY }

        // 3. The curtain: ahead to lift_3's shaft, down, through the gates to the lever and back.
        e.walkTo(lift3.x + 40.0)
        assertEquals(LIFT_DOWN, e.feet, 0.5, "down lift_3's shaft")
        mark("in the yard")
        // Gate 3 crossed once the yard's bot has turned away, the bot caught past it.
        val yardBot = e.yardBot
        e.walkTo(e.gate(3).bounds.centerX + 24.0)
        // Gone for once gate 3 stays out long enough to cross and the bot, heading away, can be
        // caught before it turns at the far end of its beat.
        e.waitUntil("the yard's bot heading away with gate 3 out", 60.0) {
            val catchUp = (e.p.x - yardBot.bounds.right) / (e.p.moveSpeed - yardBot.speed)
            val beforeItTurns = (yardBot.x - yardBot.patrolMinX) / yardBot.speed
            yardBot.facing < 0.0 && yardBot.pauseTimer == 0.0 && catchUp < beforeItTurns - 0.3 &&
                e.gate(3).remainingInactiveTime(e.world.totalElapsedSeconds) > 0.6
        }
        // Straight through the gate after it, caught before it turns at its far end.
        e.switchOff(yardBot)
        mark("yard bot off")
        e.crossGate(e.gate(2), -1.0)
        e.crossGate(e.gate(1), -1.0)
        e.walkTo(e.lever("lvl12_curtain_lever").centerX - e.p.width / 2.0)
        e.throwLever("lvl12_curtain_lever")
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

        // 4. The ferry: off the end of the parked crate into the dock while its rover heads away,
        // the rover off from behind, the cart pushed to the far container and climbed off, and the
        // lever there thrown with him waiting on the crate.
        val ferry = ferryDef()
        val dockBot = e.dockBot
        val c4Left = dockCartDef().maxX + dockCartDef().width
        e.walkTo(ferry.initialX + ferry.width - e.p.width - 2.0)
        e.waitUntil("the dock's rover heading away", 30.0) { dockBot.facing < 0.0 && dockBot.x < dockBot.patrolMaxX - 20.0 }
        e.walkTo(ferry.initialX + ferry.width + 8.0)
        assertEquals(GROUND, e.feet, 0.5, "down in the dock")
        mark("in the dock")
        e.switchOff(dockBot)
        mark("dock rover off")
        e.pushCartTo(e.cart.maxX)
        mark("cart at the far container")
        e.hopOnto(e.cart.bounds)
        e.walkTo(c4Left - e.p.width)
        e.climb()
        assertEquals(ROW, e.feet, 0.5, "up on the far container")
        e.walkTo(e.lever("lvl12_ferry_lever").centerX - e.p.width / 2.0)
        e.waitUntil("him on the ferry", 30.0) { e.heldAtX(ferry.initialX + ferry.width) }
        e.throwLever("lvl12_ferry_lever")
        mark("ferry going")
        e.waitUntil("him off the ferry", 12.0) { e.pr.bounds.left > c4Left && e.pr.body.isGrounded }
        mark("across the dock")

        // 5. The descent: with him on lift_4, over the moving crates to the raised lift_5.
        e.waitUntil("him on lift_4", 30.0) { e.heldAtX(lift4.x + lift4.width) }
        e.walkTo(lift4.x + lift4.width - e.p.width - 1.0)
        e.hopAlongTo({ e.lift("lift_5").bounds })
        mark("over the moving crates")

        // 6. The hook: onto the landing crate and along to lift_5's switch; the road guard lifted
        // away on lift_5; off the crate's far end behind the rover and the rover off; back to
        // lift_4's post to lower him, and after him under the guard and out.
        e.swing(lastHook(), 1.0)
        assertEquals(ROW, e.feet, 0.5, "on the landing crate")
        mark("swung onto the landing crate")
        e.walkTo(e.switch("sw_lift_5").centerX - e.p.width / 2.0, crouch = true)
        e.liftTheGuardAway()
        mark("the road guard lifted away")
        e.waitUntil("the road's rover turning away", 30.0) { e.roadBotTurningAway }
        e.dropOffTheLandingCrate()
        e.switchOff(e.roadBot)
        mark("road rover off")
        e.walkTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0)
        e.pressHere("sw_lift_4")
        mark("lift_4 going down")
        val until = e.t + 25.0
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
    fun testHeStartsBesideThePlayerAndGetsUpAsTheySetOff() {
        // "make the prisoner start at the same place as the player" (2026-10-26).
        val e = Escape()
        assertEquals(e.p.x + 40.0, e.pr.body.x, 0.5, "a step ahead of the player")
        assertEquals(GROUND, e.prFeet, 0.5)
        e.wait(20.0)
        assertTrue(e.sitting, "sits until the player moves")
        assertEquals(0, e.deaths, e.report)
        assertFalse(e.world.isPrisonerSeen)
        e.step(1.0)
        e.waitUntil("him up and walking", 2.0) { e.pr.isWalking }
    }

    @Test
    fun testLift1TakesHimUpFromTheGround() {
        // He walks onto lift_1 - stepping up onto its deck - and waits against the first
        // container; its switch is in reach from the deck beside him, and up they go.
        val e = Escape()
        val lift1 = e.lift("lift_1")
        e.pr.restore(lift1.def.x - 200.0, GROUND - 96.0)
        e.waitUntil("him on lift_1", 6.0) { e.heldAtX(lift1.def.x + lift1.def.width) }
        assertEquals(LIFT_DOWN, e.prFeet, 0.5, "up on its deck")
        e.p.resetTo(lift1.def.x - 100.0, GROUND - 96.0)
        e.walkTo(lift1.def.x + 70.0)
        assertEquals(LIFT_DOWN, e.feet, 0.5, "the player stepped up onto it too")
        e.pressHere("sw_lift_1")
        e.waitUntil("up", 5.0) { lift1.topY == lift1.def.upperY }
        assertEquals(ROW, e.feet, 0.5, "the player rode it up with him")
        e.waitUntil("him walking the row", 5.0) { e.pr.bounds.left > lift1.def.x + lift1.def.width }
        assertEquals(ROW, e.prFeet, 0.5, "on the containers")
    }

    @Test
    fun testEveryLiftStandsOnTheGroundAndIsSteppedOnAndOff() {
        // "the elevators go too much below surface" (2026-10-26): down, a lift's deck stands on the
        // ground. A walker steps up onto it and down off it either way, and nobody snags on it.
        for (def in layout.lifts) {
            assertEquals(GROUND, def.floorY, 0.01, "${def.id} comes down onto the ground")
            assertEquals(LIFT_DOWN, def.lowerY, 0.01, "${def.id}'s deck shows above the ground")
        }
        val e = Escape()
        e.parkHim()
        e.parkTheRoadGuard()
        e.roadBot.deactivate()
        e.lift("lift_5").restore(false, LIFT_DOWN)
        val l5 = e.lift("lift_5").def
        e.p.resetTo(l5.x - 80.0, GROUND - 96.0)
        e.walkTo(l5.x + 40.0)
        assertEquals(LIFT_DOWN, e.feet, 0.5, "stepped up onto it")
        e.walkTo(l5.x + l5.width + 20.0)
        assertEquals(GROUND, e.feet, 0.5, "and down off it")
        e.walkTo(l5.x - 80.0)
        assertEquals(GROUND, e.feet, 0.5, "and back over it the other way")
        // He walks over it too.
        e.pr.restore(l5.x - 100.0, GROUND - 96.0)
        e.waitUntil("him over lift_5", 6.0) { e.pr.bounds.left > l5.x + l5.width + 10.0 }
        assertEquals(GROUND, e.prFeet, 0.5)
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
        e.parkHim()
        e.bot.deactivate()
        val hook = layout.swingHooks.minBy { it.x }
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
    fun testRaisingLift3BeforeTheCurtainIsCutHoldsHimAtIt() {
        // Raised before the cut, he walks up to the curtain and waits there (he waits at a beam
        // that is on since 2026-10-26, rather than walking into it); cut, he walks on.
        val e = Escape()
        e.bot.deactivate()
        val l = e.lift("lift_3")
        e.placeOnRow(l.def.x - 100.0, l.def.x - 600.0)
        e.waitUntil("him held at lift_3's shaft", 5.0) { e.heldAtX(l.def.x) }
        l.toggle()
        val curtainLeft = e.curtain.minOf { it.bounds.left }
        e.waitUntil("him at the curtain", 10.0) { e.pr.isHeld && e.pr.bounds.right > curtainLeft - 6.0 }
        e.wait(5.0)
        assertEquals(0, e.deaths, e.report)
        assertTrue(e.pr.isHeld && e.pr.bounds.right <= curtainLeft, "waiting short of it")
        e.curtain.forEach { it.disable() }
        e.waitUntil("him through", 5.0) { e.pr.bounds.left > e.curtain.maxOf { it.bounds.right } }
        assertEquals(0, e.deaths, e.report)
    }

    @Test
    fun testEveryShaftLetsThePlayerOut() {
        // Each shaft's one switch is reached from its lift standing at the bottom.
        for (id in listOf("lift_1", "lift_2", "lift_3")) {
            val e = Escape()
            e.parkHim()
            val l = e.lift(id)
            e.p.resetTo(l.def.x + 70.0, LIFT_DOWN - 96.0)
            e.wait(0.3)
            e.pressHere("sw_$id")
            e.waitUntil("$id up", 5.0) { l.topY == l.def.upperY }
            assertEquals(ROW, e.feet, 0.5, "rode $id up")
        }
        // ...and a raised lift closes its shaft from above: a body walking off the lip lands on it.
        val e = Escape()
        e.parkHim()
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
        e.parkHim()
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
        assertEquals(LIFT_DOWN, f.prFeet, 0.5)
        assertTrue(f.pr.isHeld && f.pr.bounds.centerX in l3.def.x..(l3.def.x + l3.def.width), "waits on the lift")
        l3.toggle()
        f.waitUntil("him up and on", 6.0) { f.pr.bounds.left > l3.def.x + l3.def.width }
        assertEquals(ROW, f.prFeet, 0.5)
    }

    @Test
    fun testTheFerryCarriesHimOverTheDock() {
        // He walks onto the parked crate and waits at its far end; thrown from the far container,
        // it runs out flush against that container and he walks off onto it. Home again, it
        // re-arms its lever.
        val e = Escape()
        val ferry = ferryDef()
        val c4 = farContainerLeft()
        e.placeOnRow(ferry.initialX - 60.0, c4 + 13.0)
        e.waitUntil("him at the parked ferry's far end", 6.0) { e.heldAtX(ferry.initialX + ferry.width) }
        e.wait(3.0)
        assertTrue(e.heldAtX(ferry.initialX + ferry.width), "waits there")
        assertEquals(ROW, e.feet, 0.5)
        e.throwLever("lvl12_ferry_lever")
        e.waitUntil("him off the ferry", 8.0) { e.pr.bounds.left > c4 && e.pr.body.isGrounded }
        assertEquals(ROW, e.prFeet, 0.5, "walked off level")
        e.waitUntil("the ferry home", 8.0) { !e.ferry.isActive }
        assertEquals(ferry.initialX, e.ferry.x, 0.01)
        assertFalse(e.lever("lvl12_ferry_lever").isActivated, "its lever re-armed")
        assertEquals(0, e.deaths, e.report)
    }

    @Test
    fun testThrowingTheFerryEarlyStrandsNobody() {
        // Thrown before he is on it, the crate goes and comes back empty; he waits at the end of
        // the row while it is away, walks on once it is home, and the second throw takes him.
        val e = Escape()
        val ferry = ferryDef()
        val c4 = farContainerLeft()
        e.placeOnRow(ferry.initialX - 150.0, c4 + 13.0)
        e.throwLever("lvl12_ferry_lever")
        e.waitUntil("him at the end of the row", 6.0) { e.heldAtX(ferry.initialX) }
        e.waitUntil("the ferry home", 12.0) { !e.ferry.isActive }
        e.waitUntil("him on the ferry", 6.0) { e.heldAtX(ferry.initialX + ferry.width) }
        e.throwLever("lvl12_ferry_lever")
        e.waitUntil("him across", 8.0) { e.pr.bounds.left > c4 && e.pr.body.isGrounded }
        assertEquals(ROW, e.prFeet, 0.5)
    }

    @Test
    fun testTheDockIsNeverATrap() {
        // Down in the dock, wherever the cart has been left it can be got behind and pushed to the
        // far container, and climbed out off; and from the far container the ferry, thrown and
        // boarded as it comes in, carries the player back.
        for (cartAt in listOf(null, dockCartDef().minX, dockCartDef().maxX - 60.0)) {
            val e = Escape()
            e.parkHim()
            e.dockBot.deactivate()
            val ferry = ferryDef()
            if (cartAt != null) e.cart.x = cartAt
            e.p.resetTo(ferry.initialX + 20.0, GROUND - 96.0)
            e.wait(0.3)
            assertTrue(e.p.bounds.right <= e.cart.x, "the player starts on its near side")
            e.pushCartTo(e.cart.maxX)
            e.hopOnto(e.cart.bounds)
            e.walkTo(farContainerLeft() - e.p.width)
            e.climb()
            assertEquals(ROW, e.feet, 0.5, "out of the dock (cart from ${cartAt?.toInt()})")
            assertEquals(0, e.deaths, e.report)
        }
        val e = Escape()
        e.parkHim()
        val ferry = ferryDef()
        val c4 = farContainerLeft()
        e.p.resetTo(c4 + 13.0, ROW - 96.0)
        e.wait(0.3)
        e.throwLever("lvl12_ferry_lever")
        e.walkTo(c4 + 2.0)
        e.waitUntil("the ferry in", 6.0) { e.ferry.right >= c4 - 2.0 }
        e.walkTo(c4 - 40.0)
        assertEquals(ROW, e.feet, 0.5, "on the ferry")
        e.waitUntil("the ferry home", 8.0) { !e.ferry.isActive }
        e.walkTo(ferry.initialX - 60.0)
        assertEquals(ROW, e.feet, 0.5, "back on the row")
    }

    @Test
    fun testTheDockRoverSeesTheDropButNeverHim() {
        // Dropped in while the rover looks the landing's way, the player is caught; up on the
        // parked crate over its beat, he never is, however long he waits.
        val e = Escape()
        e.parkHim()
        val ferry = ferryDef()
        val bot = e.dockBot
        e.p.resetTo(ferry.initialX + ferry.width - e.p.width - 2.0, ROW - 96.0)
        e.waitUntil("the rover at its near end looking out", 30.0) { bot.facing > 0.0 && bot.x < bot.patrolMinX + 20.0 && bot.pauseTimer == 0.0 }
        var caught = false
        e.world.onGameOver = { caught = true }
        val until = e.t + 2.0
        while (!caught && e.t < until) { e.world.update(e.dt, 1.0, false, false, false); e.t += e.dt }
        repeat(60 * 3) { if (!caught) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(caught && !e.world.prisonerLost, "the drop was seen")

        val f = Escape()
        f.placeOnRow(ferry.initialX + ferry.width - 40.0, ferry.initialX - 200.0)
        f.wait(30.0)
        assertTrue(f.heldAtX(ferry.initialX + ferry.width))
        assertEquals(0, f.deaths, f.report)
        assertFalse(f.dockBot.isDeactivated)
    }

    @Test
    fun testTheMovingCratesAndTheHookTakeThePlayerToLift5sSwitch() {
        // From lift_4's far lip over level 2's two moving crates to the raised lift_5, off its far
        // lip on the hook to the landing crate, and along it to lift_5's switch on its far corner -
        // alive and unseen, at whatever point in the crates' cycle and the road guard's beat.
        for (start in listOf(0.0, 0.9, 1.8, 2.7, 5.0, 8.0)) {
            val e = Escape()
            e.parkHim()
            val l4 = e.lift("lift_4").def
            e.wait(start)
            e.p.resetTo(l4.x + l4.width - 60.0, ROW - 96.0)
            e.walkTo(l4.x + l4.width - e.p.width - 1.0)
            e.hopAlongTo({ e.lift("lift_5").bounds })
            e.swing(lastHook(), 1.0)
            assertEquals(ROW, e.feet, 0.5, "on the landing crate")
            assertTrue(e.p.centerX in landingCrate().left..landingCrate().right)
            e.walkTo(e.switch("sw_lift_5").centerX - e.p.width / 2.0, crouch = true)
            assertTrue(e.switch("sw_lift_5").isPlayerInRange(e.p), "at the switch")
            assertEquals(0, e.deaths, e.report)
            assertEquals(0.0, e.world.alertProgress, "unseen (start $start)")
        }
    }

    @Test
    fun testLift5CarriesTheRoadGuardAwayAndHeWalksOutUnderHim() {
        // "a guard moves left and right and we have to time to take the elevator down and when the
        // guard gets over it we have to lift it up" (2026-10-26): from the switch, lift_5 down
        // while he is past it, up once he is on its deck - and he stays up there, pacing the deck.
        // Lowered then, the prisoner walks the road under him and out, never seen.
        val e = Escape()
        val l4 = e.lift("lift_4")
        val l5 = e.lift("lift_5")
        e.pr.restore(l4.def.x + l4.def.width - 40.0, ROW - 96.0)
        e.atLift5sSwitch()
        e.liftTheGuardAway()
        val g = e.roadGuard
        var left = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        repeat(60 * 20) {
            e.step()
            assertEquals(ROW, g.y + g.height, 0.5, "still up on the deck")
            left = minOf(left, g.x)
            right = maxOf(right, g.x + g.width)
        }
        assertTrue(left >= l5.def.x - 0.5 && right <= l5.def.x + l5.def.width + 0.5, "pacing the deck")
        assertTrue(right - left > l5.def.width - 10.0, "end to end")
        assertEquals(0.0, e.world.alertProgress, "the switch is out of his torch up there")
        e.roadBot.deactivate()
        var seen = false
        e.onStep = { if (it.world.isPrisonerSeen) seen = true }
        e.p.resetTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0, GROUND - 96.0)
        e.wait(0.2)
        e.pressHere("sw_lift_4")
        e.waitUntil("him out", 25.0) { e.pr.hasEscaped }
        assertFalse(seen, "never seen")
        assertEquals(0, e.deaths, e.report)
    }

    @Test
    fun testTheRoadGuardHearsTheRoadButNothingOverHim() {
        // "when i am on top of here, if i move the guard can hear me" (2026-10-27): walking (not
        // creeping) back and forth on the raised lift_5's deck, or along the landing crate, for a
        // whole beat of his and more, he never turns to listen. On the road beside him, he does.
        val l5 = layout.lifts.first { it.id == "lift_5" }
        for ((x, span) in listOf(l5.x + 10.0 to 60.0, landingCrate().left + 10.0 to 120.0)) {
            val e = Escape()
            e.parkHim()
            e.p.resetTo(x, ROW - 96.0)
            val g = e.roadGuard
            var heard = false
            val frames = (span / (e.p.moveSpeed * e.dt)).toInt()
            repeat(60 * 14) { k ->
                e.step(if ((k / frames) % 2 == 0) 1.0 else -1.0)
                if (g.state == GuardState.INVESTIGATING) heard = true
            }
            assertEquals(ROW, e.feet, 0.5, "still up there")
            assertFalse(heard, "a footstep at ${x.toInt()} was heard below")
        }
        val f = Escape()
        f.parkHim()
        val g = f.roadGuard
        f.waitUntil("the road guard by lift_5, heading on", 30.0) { g.facing > 0.0 && g.x < l5.x + l5.width && g.x > l5.x - 40.0 }
        // Behind his back, walking away along the road.
        f.p.resetTo(g.x - 150.0, GROUND - 96.0)
        var heard = false
        f.world.onGameOver = {}
        repeat(20) { if (!f.world.isGameOver) { f.world.update(f.dt, -1.0, false, false, false); if (g.state == GuardState.INVESTIGATING) heard = true } }
        assertTrue(heard, "a footstep on his own road")
    }

    @Test
    fun testLift5WaitsForTheGuardUnderItAndTakesNobodyUpButHim() {
        // Under the raised deck the guard stops it coming down (a lift never comes down on a body);
        // sent up with him not on the deck, it goes up empty and he walks on along the road.
        val e = Escape()
        e.parkHim()
        val l5 = e.lift("lift_5")
        val g = e.roadGuard
        e.atLift5sSwitch()
        e.waitUntil("the guard under the raised deck", 30.0) { g.bounds.intersects(l5.shaft) && g.x > l5.def.x + 10.0 && g.facing < 0.0 }
        e.pressHere("sw_lift_5")
        repeat(20) {
            e.step()
            if (g.bounds.intersects(l5.shaft)) assertEquals(l5.def.upperY, l5.topY, 0.01, "held up over him")
        }
        e.waitUntil("lift_5 down once he is out from under it", 10.0) { l5.topY == l5.def.lowerY }
        e.waitUntil("the guard off the deck on the road", 20.0) { !e.roadGuardOnLift5 && !g.bounds.intersects(l5.shaft) }
        e.pressHere("sw_lift_5")
        e.waitUntil("lift_5 up", 6.0) { l5.topY == l5.def.upperY }
        e.wait(3.0)
        assertEquals(GROUND, g.y + g.height, 0.5, "still on the road")
        assertEquals(0, e.deaths, e.report)
    }

    @Test
    fun testSentBeforeTheGuardIsLiftedHeIsCaught() {
        // The road guard is what holds the end: lowered with him still on the road, the prisoner
        // walks into his torch.
        val e = Escape()
        e.roadBot.deactivate()
        val l4 = e.lift("lift_4")
        e.pr.restore(l4.def.x + l4.def.width - 40.0, ROW - 96.0)
        e.p.resetTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0, GROUND - 96.0)
        e.wait(0.2)
        var caught = false
        e.world.onGameOver = { caught = true }
        e.pressHere("sw_lift_4")
        var seen = false
        repeat(60 * 20) {
            if (!caught) {
                e.world.update(e.dt, 0.0, false, false, false)
                if (e.world.isPrisonerSeen) seen = true
            }
        }
        assertTrue(seen, "seen by the road guard")
        assertFalse(e.pr.hasEscaped)
    }

    @Test
    fun testTheRoadsRoverSeesTheDropUnlessItHeadsAway() {
        // Off the crate's far end while the rover looks back along its beat, the player is caught;
        // while it heads away, it is caught from behind.
        val e = Escape()
        e.parkHim()
        e.parkTheRoadGuard()
        e.atLift5sSwitch()
        val bot = e.roadBot
        e.waitUntil("the rover at its near end looking back", 30.0) { bot.facing < 0.0 && bot.x < bot.patrolMinX + 20.0 }
        var caught = false
        e.world.onGameOver = { caught = true }
        val until = e.t + 2.0
        while (!caught && e.t < until) { e.world.update(e.dt, 1.0, false, false, false); e.t += e.dt }
        repeat(60 * 3) { if (!caught) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(caught, "the drop was seen")

        val f = Escape()
        f.parkHim()
        f.parkTheRoadGuard()
        f.atLift5sSwitch()
        f.waitUntil("the road's rover turning away", 30.0) { f.roadBotTurningAway }
        f.dropOffTheLandingCrate()
        f.switchOff(f.roadBot)
        assertEquals(0, f.deaths, f.report)
    }

    @Test
    fun testTheLandingCrateIsReachedOnlyByTheHook() {
        // A shaft's width past lift_5 (too far to jump, 84 at most), 144 over the road (too high to
        // climb), with beat 2's hook over the gap: its grip 86 out from lift_5's far lip and 120
        // over the road, the swing coming down on the crate. Lift_5's switch is on it, at its far
        // corner ("move this button to the right corner of that crate"). A body stands under it.
        val l5 = layout.lifts.first { it.id == "lift_5" }
        val lc = landingCrate()
        val h = lastHook()
        val lip = l5.x + l5.width
        val probe = Player(x = 0.0, y = 0.0)
        assertTrue(lc.left - lip > probe.moveSpeed * 2.0 * -probe.jumpSpeed / probe.gravity + 10.0, "out of a running jump's reach")
        assertTrue(GROUND - lc.top > 96.0 + 30.0, "no climb from the road")
        assertTrue(lc.bottom <= GROUND - 96.0 - 6.0, "a body stands under it")
        assertEquals(lip + 86.0, Player.hookGripX(h), 0.5)
        assertEquals(ROW - 120.0, Player.hookGripY(h), 0.5)
        val sw = layout.doorSwitches.single { it.id == "sw_lift_5" }
        assertEquals(listOf("lift_5"), sw.targets)
        assertEquals(lc.top, sw.surfaceY, 0.01)
        assertTrue(sw.x >= lc.left && sw.x + DoorSwitchDef.WIDTH <= lc.right && lc.right - sw.x < 30.0, "at the landing crate's far corner")
        assertTrue(layout.levers.none { it.targetMechanismId == "lift_5" }, "no lever works it any more")
        assertTrue(layout.lasers.none { it.mechanismId == "lift_5" }, "and no screen goes with it")
    }

    @Test
    fun testAFallBeforeTheGuardIsLiftedStrandsNobody() {
        // Down on the road with him still up on lift_4 and the road guard still on it: the only way
        // back up is to lower him, and he walks into the guard - a continue puts everyone back as
        // the checkpoint on lift_4 had them, the player up on the row again.
        val e = Escape()
        e.roadBot.deactivate()
        val l4 = e.lift("lift_4")
        e.placeOnRow(l4.def.x - 60.0, l4.def.x - 100.0)
        val cp4 = layout.manualCheckpoints.indexOfFirst { it.id == "lvl12_cp4_descent" }
        e.waitUntil("his hold on lift_4 to be a checkpoint", 10.0) { e.world.currentManualCheckpointIndex == cp4 }
        e.p.resetTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0, GROUND - 96.0)
        e.wait(0.2)
        e.world.onGameOver = {}
        e.pressHere("sw_lift_4")
        repeat(60 * 20) { if (!e.world.isGameOver) e.world.update(e.dt, 0.0, false, false, false) }
        assertTrue(e.world.isGameOver, "seen by the road guard")
        assertTrue(e.world.respawnAtCheckpoint())
        assertEquals(l4.def.upperY, l4.topY, 0.01, "lift_4 back up")
        assertTrue(e.heldAtX(l4.def.x + l4.def.width) || abs(e.pr.bounds.right - (l4.def.x + l4.def.width)) < 60.0, "him back on lift_4")
        assertEquals(ROW, e.feet, 0.5, "the player back on the row")
        assertEquals(GROUND, e.roadGuard.y + e.roadGuard.height, 0.5, "the guard on the road")
    }

    @Test
    fun testLoweringTheLastLiftEarlyStrandsNobody() {
        val e = Escape()
        e.bot.deactivate()
        e.curtain.forEach { it.disable() }
        e.openTheRoad()
        val l = e.lift("lift_4")
        // Sent down before he gets there: he waits at the end of the row.
        l.toggle()
        e.waitUntil("lift_4 down", 5.0) { l.topY == l.def.lowerY }
        e.placeOnRow(l.def.x - 150.0, l.def.x - 250.0)
        e.waitUntil("him at the row's end", 5.0) { e.heldAtX(l.def.x) }
        e.p.resetTo(e.switch("sw_lift_4").centerX - e.p.width / 2.0, GROUND - 96.0)
        e.wait(0.2)
        e.pressHere("sw_lift_4")
        e.waitUntil("lift_4 up", 5.0) { l.topY == l.def.upperY }
        e.waitUntil("him on it", 5.0) { e.heldAtX(l.def.x + l.def.width) }
        // The player at the switch is clear of the shaft, so down it comes.
        e.pressHere("sw_lift_4")
        e.waitUntil("lift_4 down", 5.0) { l.topY == l.def.lowerY }
        e.waitUntil("him off it", 5.0) { e.pr.bounds.left > l.def.x + l.def.width && e.pr.body.isGrounded }
        assertEquals(GROUND, e.prFeet, 0.5)
    }

    @Test
    fun testHeWaitsForALiftToStopBeforeSteppingOntoIt() {
        // "prisoner tries to jump down before the elevator is completely up ... make sure he waits
        // until it is at the top before going onto it" (2026-10-26): held at a shaft's lip while
        // its lift comes up, he steps on only once it is at the top - and off a lift coming down
        // only once it is at the bottom.
        val e = Escape()
        e.bot.deactivate()
        val l = e.lift("lift_2")
        e.placeOnRow(l.def.x - 100.0, l.def.x - 400.0)
        e.waitUntil("him held at lift_2's shaft", 5.0) { e.heldAtX(l.def.x) }
        l.toggle()
        while (l.isMoving) {
            assertTrue(e.pr.bounds.right <= l.def.x + 12.0 && abs(e.prFeet - ROW) < 0.5, "still on the row while it comes up (lift at ${l.topY.toInt()})")
            e.step()
        }
        e.waitUntil("him across", 6.0) { e.pr.bounds.left > l.def.x + l.def.width }
        val f = Escape()
        f.bot.deactivate()
        f.openTheRoad()
        val l4 = f.lift("lift_4")
        f.pr.restore(l4.def.x + l4.def.width - 40.0, ROW - 96.0)
        f.p.resetTo(f.switch("sw_lift_4").centerX - f.p.width / 2.0, GROUND - 96.0)
        f.wait(0.2)
        f.pressHere("sw_lift_4")
        while (l4.isMoving) {
            assertTrue(f.pr.bounds.centerX <= l4.def.x + l4.def.width, "still on lift_4 while it comes down (at ${l4.topY.toInt()})")
            f.step()
        }
        f.waitUntil("him off it", 3.0) { f.pr.bounds.left > l4.def.x + l4.def.width }
    }

    @Test
    fun testBeingCaughtLeavesThePlayerWhereHeWas() {
        // "when checkpoints are on, it first gives me a glimpse of beggining before loading the
        // last checkpoint" (2026-10-26): caught, the player is no longer put back at the start -
        // the death's beat shows where it happened, and the respawn takes him to the checkpoint.
        val e = Escape()
        e.parkHim()
        val g1 = e.guard(0)
        val crate = overwatchCrates().first()
        e.waitUntil("the first guard on the near half of his crate, looking out", 30.0) {
            g1.facing > 0.0 && g1.patrolPauseTimer == 0.0 && g1.x >= g1.patrolMinX + 90.0
        }
        e.p.resetTo(crate.right - e.p.width - 10.0, GROUND - 96.0)
        var caught = false
        e.world.onGameOver = { caught = true }
        repeat(60 * 4) { if (!caught) e.world.update(e.dt, 1.0, false, false, false) }
        assertTrue(caught)
        assertTrue(e.p.x > crate.right - 20.0, "left where he was caught, not at the start (x=${e.p.x.toInt()})")
    }

    @Test
    fun testWalkingOutIntoTheFirstGuardsTorchIsCaught() {
        // "the detection seems too slow. i just walked past this and the guards meter didnt fill"
        // (2026-10-26): out from under the first guard's crate into his torch while he looks that
        // way, walking, the player is caught, and so is he. (With him further back along his crate
        // the torch's reach falls under it, and walking out only clips its end: a glimpse.)
        val crate = overwatchCrates().first()
        for (who in listOf("player", "him")) {
            val e = Escape()
            val g1 = e.guard(0)
            if (who == "player") e.parkHim()
            // As in the report's screenshot: the guard on the near half of his crate, looking out.
            e.waitUntil("the guard on the near half of his crate, looking out", 30.0) {
                g1.facing > 0.0 && g1.patrolPauseTimer == 0.0 && g1.x >= g1.patrolMinX + 90.0
            }
            if (who == "player") e.p.resetTo(crate.right - e.p.width - 10.0, GROUND - 96.0)
            else e.pr.restore(crate.right - e.pr.body.width - 10.0, GROUND - 96.0)
            var caught = false
            e.world.onGameOver = { caught = true }
            repeat(60 * 4) { if (!caught) e.world.update(e.dt, if (who == "player") 1.0 else 0.0, false, false, false) }
            assertTrue(caught, "$who walked out into the torch and was caught")
            if (who == "him") assertTrue(e.world.prisonerLost)
        }
    }

    @Test
    fun testNothingSeesHimWhereHeIsHeld() {
        // Each place he is left waiting is out of every guard's and the rover's sight, however long
        // the wait (a guard's whole beat and more).
        val e0 = Escape()
        val ferry = ferryDef()
        // Sitting at the start, and on lift_1 against the first container.
        val s0 = Escape()
        s0.wait(30.0)
        assertTrue(s0.sitting)
        assertEquals(0, s0.deaths, s0.report)
        assertFalse(s0.world.isPrisonerSeen)
        val l1 = e0.lift("lift_1").def
        val s1 = Escape()
        s1.pr.restore(l1.x + 20.0, LIFT_DOWN - 96.0)
        s1.wait(30.0)
        assertTrue(s1.heldAtX(l1.x + l1.width), "held on lift_1")
        assertEquals(0, s1.deaths, s1.report)
        val holds = listOf(e0.lift("lift_2").def.x, e0.lift("lift_3").def.x, ferry.initialX + ferry.width)
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
    fun testARespawnBringsTheLiftedGuardBackDownWithLift5() {
        // The checkpoint on lift_4 is taken before the guard is lifted, so a continue after it puts
        // lift_5 back up empty and the guard back on the road, where his checkpoint had him.
        val e = Escape()
        val l4 = e.lift("lift_4")
        val l5 = e.lift("lift_5")
        e.placeOnRow(l4.def.x - 60.0, l4.def.x - 100.0)
        val cp4 = layout.manualCheckpoints.indexOfFirst { it.id == "lvl12_cp4_descent" }
        e.waitUntil("his hold on lift_4 to be a checkpoint", 10.0) { e.world.currentManualCheckpointIndex == cp4 }
        e.atLift5sSwitch()
        e.liftTheGuardAway()
        e.world.isGameOver = true
        assertTrue(e.world.respawnAtCheckpoint())
        assertEquals(l5.def.upperY, l5.topY, 0.01, "lift_5 up")
        e.wait(0.5)
        assertEquals(GROUND, e.roadGuard.y + e.roadGuard.height, 0.5, "the guard back on the road")
        assertFalse(e.roadGuardOnLift5)
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
    fun testEveryCheckpointIsWhereHeIsHeld() {
        // Each zone ends where something holds him - a lowered lift's shaft, the parked ferry's far
        // end, lift_4's far lip - and respawns the player on that floor.
        val lift = layout.lifts.associateBy { it.id }
        val ferry = ferryDef()
        val holds = mapOf(
            "lvl12_cp1_rover" to (lift.getValue("lift_2").x to ROW),
            "lvl12_cp2_curtain" to (lift.getValue("lift_3").x to ROW),
            "lvl12_cp3_ferry" to (ferry.initialX + ferry.width to ROW),
            "lvl12_cp4_descent" to (lift.getValue("lift_4").let { it.x + it.width } to ROW)
        )
        assertEquals(holds.keys.toList(), layout.manualCheckpoints.map { it.id }, "in order along the level")
        for (cp in layout.manualCheckpoints) {
            val (right, floor) = holds.getValue(cp.id)
            val z = cp.triggerZone!!
            assertEquals(right, z.right, 0.5, "${cp.id} ends where he is held")
            assertEquals(floor, z.bottom, 0.5, "${cp.id} is on his floor")
            assertEquals(floor, cp.y + 96.0, 0.5, "${cp.id} respawns the player on that floor")
        }
    }

    @Test
    fun testUsesTheOutdoorLevelsMechanisms() {
        // "try to use as many mechanisms from other levels as possible" (2026-10-23).
        assertTrue(layout.hasStartFences, "the two start fences")
        assertEquals(2, layout.guards.size, "level 3's overwatch and a guard on the road")
        val overwatch = layout.guards.minBy { it.patrolMinX }
        assertTrue(overwatch.visionTilt > 0.0, "torch tilted down, like levels 3 and 6")
        assertTrue(layout.hangingCrateVariant1.any { abs(it.top - overwatch.surfaceY) < 0.5 && overwatch.patrolMinX >= it.left && overwatch.patrolMaxX + overwatch.width <= it.right }, "on a hung crate")
        val road = layout.guards.maxBy { it.patrolMinX }
        val l5 = layout.lifts.first { it.id == "lift_5" }
        assertEquals(GROUND, road.surfaceY, 0.01, "on the road")
        assertTrue(road.patrolMinX < l5.x - 30.0 && road.patrolMaxX > l5.x + l5.width + 30.0, "pacing across lift_5's shaft")
        assertTrue(l5.carriesGuards && !l5.holdsHimWhileUp, "lift_5 carries him away, and the prisoner walks on under it")
        assertEquals(2, layout.swingHooks.size, "level 5's swing hooks")
        assertEquals(4, layout.cameraBots.size, "level 8's rovers")
        assertEquals(1, layout.pushCarts.size, "level 8's step cart")
        assertEquals(48.0, layout.pushCarts.single().height, 0.01)
        // Level 6's levers: one cuts the curtain, one sends its lever crate.
        assertEquals(2, layout.levers.size)
        assertTrue(layout.levers.any { it.targetMechanismId == "lvl12_curtain" })
        val cut = layout.lasers.filter { it.mechanismId == "lvl12_curtain" }
        assertEquals(3, cut.size, "level 6's curtain, three beams")
        assertTrue(cut.all { it.isAlwaysActive })
        assertTrue(layout.lifts.any { it.id == "lift_5" })
        val ferry = ferryDef()
        assertTrue(ferry.startsInactive && ferry.oneShot && ferry.isVariant1 && layout.levers.any { it.targetMechanismId == ferry.id }, "level 6's lever crate")
        val moving = layout.movingPlatforms.filter { !it.startsInactive }
        assertEquals(2, moving.size, "level 2's moving crates")
        assertTrue(moving.all { it.maxX > it.minX && it.width == 76.0 && it.periodSeconds == 3.6 })
        assertEquals(1, layout.tables.size, "level 6's plank, the yard's platform")
        assertTrue(layout.tables.single() in layout.seamlessTables, "drawn as one slab, like level 8's long table")
        assertTrue(layout.lasers.count { !it.isAlwaysActive } >= 3, "level 8's timed gates")
        assertTrue(layout.hangingCrateVariant1.size >= 2, "level 2's hung crates")
        assertEquals(5, layout.lifts.size, "freight lifts")
        assertEquals(layout.lifts.size, layout.doorSwitches.size, "few switch panels - one per lift")
        assertTrue(LevelData.DEFAULT_LEVEL_12.hasRain, "level 2's rain")
        // Only the outdoor levels' mechanisms, plus lifts (2026-10-07).
        assertTrue(layout.doors.isEmpty() && layout.steamPipes.isEmpty() && layout.fans.isEmpty() && layout.conveyors.isEmpty())
        assertTrue(layout.roomBackdrops.isEmpty() && layout.hangingClimbTargets.isEmpty() && layout.prisonCell == null)
        // Above ground: nothing below the yard floor.
        assertTrue(layout.boxes.all { it.bottom <= GROUND + 0.5 } && layout.lifts.all { it.lowerY <= GROUND })
        assertEquals(null, layout.exitStructure, "out through the yard's own booth and fence")
    }

    @Test
    fun testTheYardsHungCratesAreAPlatformNow() {
        // "replace this 3 hanging in level 12 with platform" (2026-10-26): his road over the yard is
        // one slab flush with the containers either side, and nothing hangs there any more.
        val bridge = layout.tables.single()
        val lift3 = layout.lifts.first { it.id == "lift_3" }
        assertEquals(ROW, bridge.top, 0.01)
        // "bring this floating platform part more to the left (to intersect with the platform)":
        // its end cap is inside the container before it.
        val c2 = layout.boxes.first { it.height == 144.0 && it.left < bridge.left && it.right > bridge.left }
        assertEquals(24.0, c2.right - bridge.left, 0.01, "runs 24 into the container before it")
        assertEquals(c2.top, bridge.top, 0.01)
        assertEquals(lift3.x, bridge.right, 0.01, "and with lift_3's shaft after it")
        assertTrue(layout.hangingCrateVariant1.none { it.intersects(bridge) || (it.left < bridge.right && it.right > bridge.left && abs(it.top - ROW) < 0.5) })
        assertTrue(layout.lasers.filter { it.id.startsWith("lvl12_gate_") }.all { abs(it.topY - bridge.bottom) < 0.01 }, "the yard's gates hang from its underside")
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
        val crates = layout.boxes.flatMap { drawnCrates(it) } + layout.hookCrates.map { it.initialBounds.width to it.initialBounds.height } +
            layout.movingPlatforms.map { it.width to it.height }
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
        layout.movingPlatforms.forEach { assertTrue(minOf(it.minY, it.y) >= floor, "a moving crate's top at ${it.y}") }
        layout.guards.forEach { assertTrue(it.surfaceY - it.height >= floor, "a guard's head at ${it.surfaceY - it.height}") }
        // The row is the highest anyone stands: a head there is on screen.
        assertTrue(ROW - 96.0 >= floor)
        layout.lifts.forEach { assertTrue(it.upperY - 96.0 >= floor) }
    }

    companion object {
        const val GROUND = 440.0
        /** The container row's top - his road. */
        const val ROW = 296.0
        /** A lift's deck, down: standing on the ground (LiftDef.floorY). */
        const val LIFT_DOWN = GROUND - 14.0

        /**
         * How long before the first guard turns back the player sets off (and he gets up) in the
         * clean run. Measured, since the overwatch pair catch in half the usual time: 1.0..4.7 is
         * unseen; 12.7..0.9 he is glimpsed; 4.8..12.6 he is caught (before, only 6.6..10.3 was).
         */
        val SET_OFF_BEFORE_TURN = 1.3..4.4

        fun ferryDef(): MovingPlatformDef = LevelData.LEVEL_12_LAYOUT.movingPlatforms.first { it.id == "lvl12_ferry" }

        fun dockCartDef(): PushCartDef = LevelData.LEVEL_12_LAYOUT.pushCarts.single()

        /** The hung crate past lift_5 that the last hook swings onto, lift_5's switch on it. */
        fun landingCrate(): Rect = LevelData.LEVEL_12_LAYOUT.hangingCrateVariant1.maxBy { it.x }

        /** The hook over the gap past lift_5. */
        fun lastHook(): Rect = LevelData.LEVEL_12_LAYOUT.swingHooks.maxBy { it.x }

        /** The container on the far side of the dock: the cart's last stop is against its foot. */
        fun farContainerLeft(): Double = dockCartDef().let { it.maxX + it.width }

        /** The overwatch guard's hung crate. */
        fun overwatchCrates(): List<Rect> =
            LevelData.LEVEL_12_LAYOUT.hangingCrateVariant1.filter { c -> LevelData.LEVEL_12_LAYOUT.guards.any { abs(it.surfaceY - c.top) < 0.5 && it.patrolMinX >= c.left && it.patrolMinX <= c.right } }
                .sortedBy { it.x }

        /**
         * Seconds until an overwatch guard on his undisturbed patrol next turns to face left - from
         * the right end, after his dwell there. Only for an undisturbed patrol.
         */
        fun secondsUntilHeTurnsBack(g: Guard): Double {
            if (g.state != GuardState.PATROL) return -1.0
            val walk = (g.patrolMaxX - g.patrolMinX) / g.speed
            return when {
                g.facing > 0.0 && g.patrolPauseTimer > 0.0 -> g.patrolPauseTimer
                g.facing > 0.0 -> (g.patrolMaxX - g.x) / g.speed + g.patrolPauseDuration
                g.patrolPauseTimer > 0.0 -> g.patrolPauseTimer + walk + g.patrolPauseDuration
                else -> (g.x - g.patrolMinX) / g.speed + g.patrolPauseDuration + walk + g.patrolPauseDuration
            }
        }
    }
}
