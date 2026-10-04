package test

import game.model.*
import kotlin.math.abs
import kotlin.test.*

/**
 * End-to-end autopilot runs of levels 1, 2, 3, 5 and 6 (4, 7, 8 and 9 have their own walkthroughs
 * in GameplayModelTest), and the one place that ties every shipped level's three-star
 * `timeTargetSeconds` to how long a clean run actually takes.
 *
 * **What a target means:** the autopilot never hesitates, misjudges a jump or fumbles a timing
 * window, so its time is the floor a person can approach, not match. A target is
 * [MIN_TARGET_OVER_CLEAN_RUN]..[MAX_TARGET_OVER_CLEAN_RUN] times that floor - tight enough that star 3
 * still asks for a clean run, loose enough that a real player who knows the level can make it.
 * Re-run the walkthroughs and re-pick the target whenever a level's route or timing changes.
 */
class LevelWalkthroughTest {

    /** The autopilot's own clock and death count, next to the world it drives. */
    class Runner(val level: LevelData) {
        val world = GameWorld.createDefault(level)
        val p = world.player
        val dt = 1.0 / 60.0
        var t = 0.0
        var deaths = 0
        var frameNo = 0
        /** How long the body has failed to move forward while asked to (drives climb / jump-when-blocked). */
        var stuck = 0.0
        var maxX = p.x
        var lastProgressT = 0.0
        val feet get() = p.y + p.height
        var deathReport = ""

        init {
            world.onGameOver = {
                deaths++
                deathReport = "x=${p.x.toInt()} y=${p.y.toInt()} crouch=${p.isCrouching} " +
                    "seenBy=${world.detectingGuards.size} guards/${world.detectingCameras.size} cameras"
            }
        }

        private fun surfaces(): List<Rect> =
            world.platforms + world.movingPlatforms.map { it.bounds } + world.hookCrates.map { it.bounds }

        fun step(move: Double, jump: Boolean = false, crouch: Boolean = false, interact: Boolean = false) {
            val before = p.x
            world.update(dt, move, jump, crouch, interact)
            stuck = if (abs(p.x - before) < 0.2) stuck + dt else 0.0
            if (p.x > maxX + 1.0) { maxX = p.x; lastProgressT = t }
            t += dt
            frameNo++
        }

        /**
         * One frame of the general run-right policy: crouch under a ceiling that only the head
         * meets, jump when walking into a face (which is also the climb), jump a gap at its lip,
         * hold at a lip for a moving crate to come within reach, swing from a hook in reach, and
         * throw any lever the body is standing at.
         */
        fun runRight() {
            val feetY = feet
            val surf = surfaces()
            val head = Rect(p.x + 4.0, feetY - 96.0, p.width + 40.0, 36.0)
            val low = Rect(p.x + 4.0, feetY - 52.0, p.width + 40.0, 46.0)
            // A floating ledge is climbed, not ducked under (level 3's table).
            val floaters = world.floatingClimbTargets
            val crouch = surf.any { it.intersects(head) && it.bottom < feetY - 20.0 && it !in floaters } &&
                surf.none { it.intersects(low) && it.bottom < feetY - 20.0 }
            var holdForMover = false
            var gapJump = false
            var jump = stuck > 0.05 && !crouch && (frameNo % 10 == 0)
            if (p.isGrounded && !crouch) {
                val front = p.x + p.width * 0.75
                fun supported(x: Double) = surf.any { x in it.x..it.right && abs(it.top - feetY) < 2.0 }
                fun reachable(q: Rect) = q.x > front - 4.0 && q.top > feetY - 44.0 && q.top < feetY + 70.0 && q.width >= 24.0
                fun reachableMover(q: Rect) = q.x > front - 4.0 && q.top > feetY - 28.0 && q.top < feetY + 22.0
                val sideways = world.movingPlatforms.filter { it.maxX > it.minX }.map { it.bounds }
                val lifts = world.movingPlatforms.filter { it.maxX == it.minX }.map { it.bounds }
                val onSideways = sideways.any { front - 6.0 in it.x..it.right && abs(it.top - feetY) < 2.0 }
                val sidewaysNear = sideways.any { q ->
                    q.x > front - 4.0 && q.x - front < 130.0 && q.top > feetY - 90.0 && q.top < feetY + 90.0
                }
                val targets = (world.platforms + world.hookCrates.map { it.bounds }).filter { reachable(it) } +
                    (sideways + lifts).filter { reachableMover(it) }
                val gap = targets.minOfOrNull { it.x - front } ?: 9999.0
                if (onSideways || sidewaysNear) {
                    // Crossing sideways-moving crates: stand near the lip, go when the next one is close.
                    if (!supported(front + 8.0)) {
                        if (gap <= 60.0) { jump = true; gapJump = true } else holdForMover = true
                    }
                } else if (!supported(front + 3.0)) {
                    // Static ledge, or a lift: go at the lip; off a lift, only once its level matches the next ledge.
                    val onLift = lifts.any { front - 6.0 in it.x..it.right && abs(it.top - feetY) < 2.0 }
                    val levelGap = if (onLift) targets.filter { it.top >= feetY - 14.0 }.minOfOrNull { it.x - front } ?: 9999.0 else gap
                    if (onLift && levelGap >= 84.0 && gap < 84.0) holdForMover = true
                    else if (levelGap < 84.0) { jump = true; gapJump = true }
                    else if (lifts.any { q -> q.x > front - 4.0 && q.x - front < 130.0 && q.top > feetY - 120.0 && q.top < feetY + 120.0 }) holdForMover = true
                }
            }
            if (p.isGrounded && p.runUpDistance >= p.minSwingRunUp) {
                for (hook in world.swingHooks) {
                    val ahead = Player.hookGripX(hook) - (p.x + p.width / 2.0)
                    if (ahead in (p.swingMinReach + 1.5)..(p.swingMaxReach - 1.5)) jump = true
                }
            }
            val atLever = world.levers.any { !it.isActivated && abs(it.centerX - (p.x + p.width / 2.0)) < it.interactRadius * 0.6 }
            if (holdForMover && !(jump && gapJump)) jump = false
            step(if (holdForMover) 0.0 else 1.0, jump, crouch, interact = atLever)
        }

        /** Runs [policy] until the exit, a death, or [maxSeconds]; a body that stops making progress for 25s has failed. */
        fun runToTheExit(maxSeconds: Double = 300.0, policy: () -> Unit = ::runRight) {
            while (t < maxSeconds && !world.isLevelComplete && deaths == 0 && t - lastProgressT < 25.0) policy()
        }

        fun assertCleanRun(what: String) {
            assertEquals(0, deaths, "$what should clear with no deaths ($deathReport)")
            assertTrue(world.isLevelComplete, "$what must reach the exit (got to x=${maxX.toInt()} of ${world.exitZone.x.toInt()} at t=${"%.1f".format(t)}s)")
        }
    }

    private fun cleanRun(level: LevelData): Double {
        val run = Runner(level)
        run.runToTheExit()
        run.assertCleanRun(level.name)
        println("WALKTHROUGH ${level.id}: ${"%.1f".format(run.t)}s (target ${level.timeTargetSeconds}s)")
        return run.t
    }

    // ---- The five walkthroughs --------------------------------------------------------------

    @Test
    fun testLevel1CleanRunTime() {
        assertPace(LevelData.DEFAULT_LEVEL_1, cleanRun(LevelData.DEFAULT_LEVEL_1))
    }

    @Test
    fun testLevel2CleanRunTime() {
        // Climb the crate and terrain, jump the three hanging crates, ride the moving containers
        // across (each hop taken when the next one is in reach), ride the vertical lifts, and
        // drop off the last block to the exit.
        assertPace(LevelData.DEFAULT_LEVEL_2, cleanRun(LevelData.DEFAULT_LEVEL_2))
    }

    @Test
    fun testLevel5CleanRunTime() {
        // Swing the first two hooks, throw the lever so the third hook's crate falls into the gap,
        // and walk out.
        assertPace(LevelData.SIDE_SCROLL_LEVEL, cleanRun(LevelData.SIDE_SCROLL_LEVEL))
    }

    /**
     * Level 3 is the timing level: the roof guard, the two overwatch guards and the cameras decide
     * when each stretch is safe. [LEVEL_3_STAGE_STARTS] cuts the level into stretches and
     * [LEVEL_3_HOLDS] is how long the body stands still (crouched) on reaching each one - found by
     * [testReplanLevel3], a backtracking search that replays the whole run for each candidate wait and
     * keeps the smallest that lives to the next stretch. Two crouches release the roof guard, as
     * the level's own tutorial does at the start.
     */
    private fun level3Run(holds: List<Double>, stopX: Double = Double.MAX_VALUE): Runner {
        val run = Runner(LevelData.DEFAULT_LEVEL_3)
        run.step(0.0, false, true) // the tutorial's first crouch releases the roof guard
        val done = BooleanArray(LEVEL_3_STAGE_STARTS.size)
        var holdUntil = -1.0
        while (!run.world.isLevelComplete && run.deaths == 0 && run.p.x < stopX &&
            run.t < 400.0 && run.t - run.lastProgressT < 20.0
        ) {
            if (run.t < holdUntil) { run.step(0.0, false, true); continue }
            val next = LEVEL_3_STAGE_STARTS.indices.firstOrNull { !done[it] && run.p.x >= LEVEL_3_STAGE_STARTS[it] && it < holds.size }
            if (next != null && run.p.isGrounded) {
                done[next] = true
                if (holds[next] > 0.0) { holdUntil = run.t + holds[next]; continue }
            }
            run.runRight()
        }
        return run
    }

    @Test
    fun testLevel3CleanRunTime() {
        val run = level3Run(LEVEL_3_HOLDS)
        run.assertCleanRun("03: First Contact")
        println("WALKTHROUGH level_3: ${"%.1f".format(run.t)}s (target ${LevelData.DEFAULT_LEVEL_3.timeTargetSeconds}s)")
        assertPace(LevelData.DEFAULT_LEVEL_3, run.t)
    }

    /**
     * Each level's optional objective (LevelData.bonusObjective) is met by its own clean run - 1, 2,
     * 5 and 6 here; 7, 8 and 9 in their own walkthroughs in GameplayModelTest; 4's (deploy a gadget)
     * needs stock, so BonusObjectiveTest. **Level 3 (stay unseen) is not proven**: its autopilot
     * survives but the roof guard notices it standing up at the first crate, even after a crouched
     * approach. An unseen route needs [LEVEL_3_HOLDS] re-searched ([testReplanLevel3] now rejects
     * any wait that raises the alert) with the opening wait at the crate added.
     */
    @Test
    fun testTheOptionalObjectivesOfLevels1To6AreMetByACleanRun() {
        val runs = listOf(
            Runner(LevelData.DEFAULT_LEVEL_1).also { it.runToTheExit() },
            Runner(LevelData.DEFAULT_LEVEL_2).also { it.runToTheExit() },
            Runner(LevelData.SIDE_SCROLL_LEVEL).also { it.runToTheExit() },
            level6Run()
        )
        for (run in runs) {
            run.assertCleanRun(run.level.name)
            assertEquals(ObjectiveState.MET, run.world.bonusObjectiveState, "${run.level.name}: optional objective")
            assertTrue(run.world.getLevelResult().star2, "${run.level.name}: star 2")
        }
    }

    /**
     * "after i press continue and the ad plays, i do not get respawned in the correct place"
     * (2026-09-30): level 2 took two automatic checkpoints the body could not stand on after a
     * respawn - half on a lift, and on a hanging crate's corner - so a continue dropped it to the
     * floor below. Every checkpoint a clean run takes on the automatic-checkpoint levels here is
     * died past and continued from, and the body has to stay exactly where it was put.
     */
    @Test
    fun testEveryAutomaticCheckpointHoldsTheBodyAfterAContinue() {
        for (level in listOf(LevelData.DEFAULT_LEVEL_1, LevelData.DEFAULT_LEVEL_2, LevelData.SIDE_SCROLL_LEVEL)) {
            var taken = 0
            Runner(level).also { r ->
                r.world.onCheckpointSecured = { _, _ -> taken++ }
                r.runToTheExit()
            }
            assertTrue(taken > 0, "${level.name}: a clean run takes checkpoints")
            for (k in 1..taken) {
                val r = Runner(level)
                var n = 0
                r.world.onCheckpointSecured = { _, _ -> n++ }
                while (n < k && !r.world.isLevelComplete) r.runRight()
                val t0 = r.t
                while (r.t < t0 + 1.0 && !r.world.isLevelComplete && r.deaths == 0) r.runRight()
                if (r.world.isLevelComplete) continue
                r.world.isGameOver = true
                assertTrue(r.world.respawnAtCheckpoint(), "${level.name}: the continue is granted")
                val x = r.p.x
                val y = r.p.y
                val t1 = r.t
                val d0 = r.deaths
                while (r.t < t1 + 4.0) r.step(0.0)
                assertEquals(x, r.p.x, 3.0, "${level.name} checkpoint $k: the body stays where it was respawned (x)")
                assertEquals(y, r.p.y, 3.0, "${level.name} checkpoint $k: the body stays where it was respawned (y)")
                assertEquals(d0, r.deaths, "${level.name} checkpoint $k: and survives standing there")
            }
        }
    }

    /** Re-derive [LEVEL_3_HOLDS] after a level 3 change: `REPLAN_LEVEL3=1 ./gradlew jvmTest --tests '*testReplanLevel3'`. */
    @Test
    fun testReplanLevel3() {
        if (System.getenv("REPLAN_LEVEL3") == null) return
        val candidates = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0)
        fun go(holds: List<Double>): List<Double>? {
            val k = holds.size
            val stop = if (k + 1 < LEVEL_3_STAGE_STARTS.size) LEVEL_3_STAGE_STARTS[k + 1] else LevelData.DEFAULT_LEVEL_3.layout!!.exitZone.x
            for (w in candidates) {
                val trial = holds + w
                val r = level3Run(trial, stopX = stop)
                // Level 3's optional objective is Ghost: a wait that lets the alert meter rise is no good.
                if (r.deaths > 0 || r.world.bonusObjectiveState == ObjectiveState.FAILED ||
                    (!r.world.isLevelComplete && r.p.x < stop)) continue
                if (k + 1 >= LEVEL_3_STAGE_STARTS.size) return trial
                go(trial)?.let { return it }
            }
            return null
        }
        println("LEVEL3 HOLDS = ${go(emptyList())}")
    }

    @Test
    fun testLevel6CleanRunTime() {
        val run = level6Run()
        assertEquals(0, run.deaths, "Level 6 should clear with no deaths (${run.deathReport})")
        assertTrue(run.world.isLevelComplete, "Level 6 must reach the exit (x=${run.p.x.toInt()}, t=${run.t})")
        println("WALKTHROUGH level_6: ${"%.1f".format(run.t)}s (target ${LevelData.DEFAULT_LEVEL_6.timeTargetSeconds}s)")
        assertPace(LevelData.DEFAULT_LEVEL_6, run.t)
    }

    /**
     * Level 6 end to end: the swing section (hook, lever crate), then the pit under the overwatch
     * crate, the boom, the gantry climb, the plank lever and the corridor - the same legs
     * `testLevel6SecondSectionCrossesPitAndReachesExit` drives, chained so the guards' clocks are
     * the real ones. The half second's pause on the far terrain is what puts the pit's overwatch
     * guard's beat right for the crossing; anything from 0.5s to 9.5s works, none at all does not.
     */
    private fun level6Run(): Runner {
        val layout = LevelData.LEVEL_6_LAYOUT
        val terrain = layout.boxes.first { it.width == 300.0 }
        val landingCrate = layout.boxes.first { it.x == 1370.0 }
        val farTerrain = layout.boxes.first { it.x == 1592.0 }
        val tallBlock = layout.boxes.first { it.width == 300.0 && it.height == 96.0 }
        val pitLongCrate = layout.boxes.first { it.width == 174.0 && it.height == 38.0 && it.y == 300.0 }
        val endTerrain = layout.boxes.first { it.width == 70.0 && it.x > tallBlock.right }
        val crane = layout.cranes.single()
        val cranePlatform = layout.boxes.first { it.height == 48.0 && it.width > 300.0 }
        val gateBlock = layout.boxes.first { it.x == cranePlatform.right }
        val plank = layout.boxes.first { it.height == 30.0 }
        val endCrate = layout.boxes.first { it.height == 48.0 && it.width == 68.0 && it.x > gateBlock.left }
        val barrelWalls = layout.barrels.map { it.left }.distinct()

        val run = Runner(LevelData.DEFAULT_LEVEL_6)
        val world = run.world
        val p = run.p
        var stalledFor = 0.0

        // Section 1: run, swing the hook, throw the lever crate's lever on the way, land on the far terrain.
        while (run.t < 60.0 && run.deaths == 0 &&
            !(p.isGrounded && p.x > farTerrain.left + 20.0 && abs(p.y + p.height - farTerrain.top) < 1.0)
        ) {
            val before = p.x
            val hookInReach = world.swingHooks.any { Player.hookGripX(it) - p.centerX in p.swingMinReach..p.swingMaxReach }
            val atLedge = listOf(terrain.right, landingCrate.right).any { (it - (p.x + p.width)) in 0.0..18.0 }
            run.step(1.0, hookInReach || (p.isGrounded && (stalledFor > 0.05 || atLedge)), false, true)
            stalledFor = if (abs(p.x - before) < 0.5) stalledFor + run.dt else 0.0
        }
        val pauseUntil = run.t + 0.5
        while (run.t < pauseUntil && run.deaths == 0) run.step(0.0)

        // Sections 2-5, in five legs: the ground out to lever_3, back west to the boom, over the
        // machine and up the gantry block to the plank lever, then down and out along the corridor.
        stalledFor = 0.0
        var leg = 0
        val lever3 = world.levers.first { it.id == "lever_3" }
        val lever4 = world.levers.first { it.id == "lever_4" }
        val plankGuard = world.allGuards.first { it.x > plank.left - 100.0 }
        val boom = crane.boomBounds
        while (run.t < 200.0 && !world.isLevelComplete && run.deaths == 0) {
            val before = p.x
            val feetY = p.y + p.height
            val onTallBlock = p.isGrounded && abs(feetY - tallBlock.top) < 1.0
            val beforeTheCrate = p.isGrounded && abs(feetY - gateBlock.top) < 1.0 && (endCrate.left - (p.x + p.width)) in 0.0..24.0
            val atTheLip = p.isGrounded && abs(feetY - endCrate.top) < 1.0 && (endCrate.right - (p.x + p.width)) <= 3.0
            val underTheBoom = onTallBlock && p.x + p.width > boom.left - 60.0 && p.x < boom.right
            if (leg == 0 && lever3.isActivated) leg = 1
            if (leg == 1 && onTallBlock && p.x + p.width < boom.left - 20.0) leg = 2
            if (leg == 2 && lever4.isActivated) leg = 3
            if (leg == 3 && p.isGrounded && feetY >= 439.0) leg = 4
            // Only cross the plank's crate while its guard is walking away, AND far enough out on
            // his beat (near post plank.left + 110) that the landing on the plank is outside his
            // hearing (NoiseLevel.NORMAL, 180) - going as he turns away survives, but he hears
            // the landing, turns and sees you, which costs the optional objective (stay unseen).
            val guardLooking = !(plankGuard.facing > 0.0 &&
                plankGuard.x >= plank.left + 140.0 && plankGuard.x < plank.left + 200.0)
            val holdForTheGuard = leg == 2 && beforeTheCrate && guardLooking
            val move = when { leg == 1 -> -1.0; leg == 3 -> -1.0; holdForTheGuard -> 0.0; else -> 1.0 }
            val nearLedge = leg != 1 && leg < 3 &&
                listOf(farTerrain.right, tallBlock.right, endTerrain.right).any { (it - (p.x + p.width)) in 0.0..18.0 }
            val nearWall = leg != 1 && leg != 3 && barrelWalls.any { (it - (p.x + p.width)) in 0.0..18.0 }
            val ledgeJump = nearLedge && !(leg == 0 && onTallBlock)
            val jump = p.isGrounded && leg < 3 && !holdForTheGuard &&
                (stalledFor > 0.05 || ledgeJump || nearWall || (leg == 2 && atTheLip))
            val underOverwatch = p.x + p.width > pitLongCrate.left - 60.0 && p.x < pitLongCrate.right + 60.0
            run.step(move, jump, underOverwatch || (leg < 2 && underTheBoom), leg == 0 || leg == 2)
            stalledFor = if (abs(p.x - before) < 0.5) stalledFor + run.dt else 0.0
        }
        return run
    }

    // ---- The target times -------------------------------------------------------------------

    /**
     * Levels whose clean run is timed by their own walkthrough in GameplayModelTest, as measured
     * there (seconds): 4 = testLevel4SimulationPlayable, 7 = testLevel7SimulationPlayableWalkthrough,
     * 8 / 9 = the "LEVEL8 ground road" / "LEVEL9 high road" lines of their walkthroughs.
     */
    private val cleanRunsMeasuredElsewhere = mapOf(
        "level_4" to 116.9,
        "level_7" to 107.7,
        "level_8" to 130.2,
        "level_9" to 106.1
    )

    private fun assertPace(level: LevelData, cleanRunSeconds: Double) {
        val target = level.timeTargetSeconds.toDouble()
        assertTrue(
            target >= cleanRunSeconds * MIN_TARGET_OVER_CLEAN_RUN,
            "${level.name}: a target of ${target}s leaves a person under ${MIN_TARGET_OVER_CLEAN_RUN}x the autopilot's " +
                "${"%.1f".format(cleanRunSeconds)}s clean run - not reachable by hand"
        )
        assertTrue(
            target <= cleanRunSeconds * MAX_TARGET_OVER_CLEAN_RUN,
            "${level.name}: a target of ${target}s is over ${MAX_TARGET_OVER_CLEAN_RUN}x the autopilot's " +
                "${"%.1f".format(cleanRunSeconds)}s clean run - star 3 would not ask for a clean run"
        )
    }

    @Test
    fun testTargetsForLevelsTimedByTheirOwnWalkthroughs() {
        val levels = listOf(LevelData.DEFAULT_LEVEL_4, LevelData.DEFAULT_LEVEL_7, LevelData.DEFAULT_LEVEL_8, LevelData.DEFAULT_LEVEL_9)
        for (level in levels) assertPace(level, cleanRunsMeasuredElsewhere.getValue(level.id))
    }

    @Test
    fun testTargetsAreWholeFiveSecondSteps() {
        val shipped = listOf(
            LevelData.DEFAULT_LEVEL_1, LevelData.DEFAULT_LEVEL_2, LevelData.DEFAULT_LEVEL_3, LevelData.DEFAULT_LEVEL_4,
            LevelData.SIDE_SCROLL_LEVEL, LevelData.DEFAULT_LEVEL_6, LevelData.DEFAULT_LEVEL_7, LevelData.DEFAULT_LEVEL_8,
            LevelData.DEFAULT_LEVEL_9, LevelData.DEFAULT_LEVEL_10, LevelData.DEFAULT_LEVEL_11, LevelData.DEFAULT_LEVEL_12
        )
        for (level in shipped) {
            assertEquals(0.0f, level.timeTargetSeconds % 5.0f, 1e-4f, "${level.name}'s target reads as a round number on the win card")
        }
    }

    companion object {
        /** A target below this many times the clean run cannot be met by a person. */
        const val MIN_TARGET_OVER_CLEAN_RUN = 1.25
        /** ...and above this it no longer asks for a clean run. */
        const val MAX_TARGET_OVER_CLEAN_RUN = 2.0

        /** Where level 3's hold points begin: every 100 units from the crate to just short of the exit. */
        val LEVEL_3_STAGE_STARTS: List<Double> = (300..2800 step 100).map { it.toDouble() }

        /** Seconds stood still (crouched) on reaching each of [LEVEL_3_STAGE_STARTS] - see [testReplanLevel3]. */
        val LEVEL_3_HOLDS: List<Double> = listOf(
            0.0, 3.0, 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            4.0, 0.0, 0.0, 2.0, 1.0, 4.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        )
    }
}
