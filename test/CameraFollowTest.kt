import game.model.CameraFollow
import game.model.GameWorld
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gameplay camera's horizontal follow.
 *
 * The headline test here is [theCameraNeverChangesScrollRateInOneFrameTheWayTheOldLerpDid], which
 * drives the real `GameWorld` loop rather than a synthetic target, because the thing that was
 * wrong ("it moves smoothly and then for landing part the screen suddenly moves forward") is a
 * property of how [CameraFollow] reacts to the player's own velocity steps, not of the spring in
 * isolation. It measures the old first-order filter alongside the new one on the identical run,
 * so the assertion is a comparison rather than a magic number that drifts with level tuning.
 */
class CameraFollowTest {

    private companion object {
        /** `GameplayScene`'s own fixed world zoom and authored canvas width. */
        const val ZOOM = 1.35
        const val CANVAS_W = 1040.0
        const val FPS = 60.0
    }

    /** The follow `GameplayScene` used before [CameraFollow], for the side-by-side comparisons. */
    private fun oldLerp(current: Double, target: Double, dt: Double): Double =
        current + (target - current) * (1.0 - exp(-16.0 * dt)).coerceIn(0.0, 1.0)

    /**
     * Runs a forward jump on level 1's open ground and returns, for each of the two follows, the
     * worst single-frame change in scroll rate over the landing - the jolt the eye actually reads.
     */
    private fun worstLandingJolt(): Pair<Double, Double> {
        val dt = 1.0 / FPS
        fun run(useSpring: Boolean): Double {
            val world = GameWorld.createDefault()
            // Generic open ground, derived rather than hardcoded - see the test-coordinate
            // lesson in the guidelines.
            world.player.x = world.levelData.guardPatrolMinX + 75.0
            fun target() = CANVAS_W / 2.0 - (world.player.x + world.player.width / 2.0) * ZOOM
            repeat(20) { world.update(dt, 1.0, false, false, false) }
            val spring = CameraFollow()
            spring.snapTo(target())
            var lerp = target()
            var prev = target()
            var prevVel = -world.player.moveSpeed * ZOOM
            var worst = 0.0
            repeat(60) { i ->
                world.update(dt, 1.0, i == 8, false, false)
                val t = target()
                val cam = if (useSpring) spring.update(t, dt) else oldLerp(lerp, t, dt).also { lerp = it }
                val vel = (cam - prev) / dt
                prev = cam
                // Only the landing half of the arc: the take-off is a jump-button frame, the
                // landing is where the report is.
                if (i >= 40) worst = maxOf(worst, abs(vel - prevVel))
                prevVel = vel
            }
            return worst
        }
        return run(false) to run(true)
    }

    @Test
    fun theCameraNeverChangesScrollRateInOneFrameTheWayTheOldLerpDid() {
        val (old, spring) = worstLandingJolt()
        assertTrue(old > 8.0, "the old filter is supposed to jolt here; measured $old px/s")
        assertTrue(
            spring < old * 0.6,
            "the spring should more than halve the landing jolt: old=$old spring=$spring px/s"
        )
    }

    @Test
    fun aBodyPinnedAgainstAPlatformFaceStopsTheCameraGentlyRatherThanDead() {
        // Walking into level 1's first crate takes the player's vx from 132 to 0 in one frame -
        // the hardest velocity step anything in this game produces.
        val dt = 1.0 / FPS
        fun run(useSpring: Boolean): Double {
            val world = GameWorld.createDefault()
            world.player.x = 400.0
            fun target() = CANVAS_W / 2.0 - (world.player.x + world.player.width / 2.0) * ZOOM
            repeat(20) { world.update(dt, 1.0, false, false, false) }
            val spring = CameraFollow()
            spring.snapTo(target())
            var lerp = target()
            var prev = target()
            var prevVel = -world.player.moveSpeed * ZOOM
            var worst = 0.0
            repeat(55) { i ->
                world.update(dt, 1.0, i == 8, false, false)
                val t = target()
                val cam = if (useSpring) spring.update(t, dt) else oldLerp(lerp, t, dt).also { lerp = it }
                val vel = (cam - prev) / dt
                prev = cam
                if (i >= 30) worst = maxOf(worst, abs(vel - prevVel))
                prevVel = vel
            }
            return worst
        }
        val old = run(false)
        val spring = run(true)
        assertTrue(old > 30.0, "the old filter is supposed to stop dead here; measured $old px/s")
        assertTrue(spring < old * 0.6, "the spring should soften the stop: old=$old spring=$spring px/s")
    }

    @Test
    fun theWalkingLagIsSmoothTimesSpeedAndCostsUnderTwoPercentOfTheCanvas() {
        val follow = CameraFollow()
        val dt = 1.0 / FPS
        val speed = -132.0 * ZOOM // the camera scrolls the opposite way to the player
        var target = 0.0
        follow.snapTo(target)
        repeat(240) {
            target += speed * dt
            follow.update(target, dt)
        }
        val lag = abs(target - follow.position)
        // Tolerance covers the one-frame discretisation: the target is held still inside a step,
        // which shaves a little under a frame of travel off the analytic v * smoothTime.
        assertEquals(abs(speed) * CameraFollow.DEFAULT_SMOOTH_TIME, lag, 2.0, "steady lag is v * smoothTime")
        assertTrue(lag < CANVAS_W * 0.02, "lag $lag px must stay under 2% of the canvas")
    }

    @Test
    fun itSettlesExactlyOnTargetSoStandingStillFramesTheSameAsBefore() {
        val follow = CameraFollow()
        follow.snapTo(-500.0)
        repeat(120) { follow.update(-500.0, 1.0 / FPS) }
        assertEquals(-500.0, follow.position, 1e-9)
        assertEquals(0.0, follow.velocity, 1e-9)
    }

    @Test
    fun criticalDampingMeansItNeverOvershoots() {
        val follow = CameraFollow()
        follow.snapTo(0.0)
        var maxSeen = 0.0
        repeat(300) {
            follow.update(100.0, 1.0 / FPS)
            maxSeen = maxOf(maxSeen, follow.position)
        }
        assertTrue(maxSeen <= 100.0 + 1e-9, "overshot to $maxSeen - the damping ratio is wrong")
        assertEquals(100.0, follow.position, 0.01)
    }

    @Test
    fun theSameMotionLandsInTheSamePlaceAtSixtyAndAtTwoHundredAndFortyFps() {
        fun drive(fps: Double): Double {
            val follow = CameraFollow()
            follow.snapTo(0.0)
            val dt = 1.0 / fps
            var t = 0.0
            var elapsed = 0.0
            while (elapsed < 1.0) {
                t += -200.0 * dt
                follow.update(t, dt)
                elapsed += dt
            }
            return follow.position
        }
        assertEquals(drive(60.0), drive(240.0), 1.0, "the follow must not depend on the frame rate")
    }

    @Test
    fun aTeleportCutsInsteadOfPanningAcrossTheLevel() {
        val follow = CameraFollow()
        follow.snapTo(0.0)
        val cut = CANVAS_W / 8.0
        follow.update(-2000.0, 1.0 / FPS, snapIfFartherThan = cut)
        assertEquals(-2000.0, follow.position, 1e-9, "a respawn-sized jump must be a cut")
        assertEquals(0.0, follow.velocity, 1e-9, "a cut must not leave momentum behind")
        // ...and an ordinary frame of running is nowhere near that threshold, even at the 0.1s
        // dt clamp: 132 units of walk * 0.1s * 1.35 zoom is under 18px against a 130px cut.
        assertTrue(132.0 * 0.1 * ZOOM < cut, "a clamped-dt frame must never be mistaken for a teleport")
    }

    @Test
    fun aZeroOrNonFiniteStepIsIgnoredRatherThanExploding() {
        val follow = CameraFollow()
        follow.snapTo(-10.0)
        follow.update(500.0, 0.0)
        assertEquals(-10.0, follow.position, 1e-9)
        follow.update(Double.NaN, 1.0 / FPS)
        assertEquals(-10.0, follow.position, 1e-9)
    }
}
