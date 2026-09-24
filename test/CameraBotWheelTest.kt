import game.model.CameraBot
import game.model.LevelData
import game.model.Rect
import game.scene.CameraBotVisual
import korlibs.image.bitmap.Bitmap32
import korlibs.korge.view.Container
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The level 7 rover's road wheels.
 *
 * These assert on `visual.wheels[i].rotation` - the angle that actually reaches the renderer -
 * rather than on an accumulator inside the class, because the two differ by exactly the thing
 * most likely to be wrong: the chassis container is mirrored with `scaleX = -1` when the bot
 * faces left, and mirroring reverses the sense of a child's rotation, so the angle written there
 * has to be negated to keep the wheel turning WITH the direction of travel.
 *
 * Driven by ground distance rather than by time, so they stop dead when the bot pauses at the end
 * of a patrol leg or is deactivated. See CameraBotVisual and tools/art/prep_robot.py.
 */
class CameraBotWheelTest {

    /** A stand-in for the two plates: the geometry under test is the bot's, not the art's. */
    private fun plate(size: Int) = Bitmap32(size, size, premultiplied = true)

    private fun makeVisual(bot: CameraBot): CameraBotVisual =
        CameraBotVisual.createAll(Container(), listOf(bot), plate(128), plate(64)).first()

    private fun bot(facing: Double = 1.0) = CameraBot(
        id = "test_bot",
        x = 1000.0,
        surfaceY = 440.0,
        patrolMinX = 900.0,
        patrolMaxX = 1200.0,
        speed = 36.0,
        facing = facing
    )

    private fun tick(v: CameraBotVisual, dt: Double = 1.0 / 60.0) {
        v.update(dt, 0.0, -10_000.0, 10_000.0, emptyList<Rect>())
    }

    @Test
    fun testBothWheelsExistAndStartUnrotated() {
        val v = makeVisual(bot())
        assertEquals(2, v.wheels.size, "One container per road wheel, rear then front")
        tick(v)
        for (w in v.wheels) assertEquals(0.0, w.rotation.radians, 1e-9, "No travel, no roll")
    }

    @Test
    fun testWheelsRollForwardByGroundDistanceNotByTime() {
        val b = bot()
        val v = makeVisual(b)
        tick(v)

        // One second of patrol at 36 u/s over a wheel of radius WHEEL_R * width. The class does
        // not expose the radius, so this checks the defining property instead: the angle is
        // proportional to distance, and the SAME distance covered in a different number of frames
        // gives the same angle. That is what "driven by ground distance" means, and it is what
        // keeps the wheel in step at any speed a level picks.
        repeat(60) { b.update(1.0 / 60.0); tick(v) }
        val after60 = v.wheels[0].rotation.radians
        assertTrue(after60 > 0.5, "A second of patrol must visibly roll the wheel, got $after60 rad")

        val b2 = bot()
        val v2 = makeVisual(b2)
        tick(v2)
        repeat(6) { b2.update(10.0 / 60.0); tick(v2) }
        assertEquals(
            after60, v2.wheels[0].rotation.radians, 1e-6,
            "Same ground covered in a tenth of the frames must give the same angle"
        )

        // Both wheels are the same size and turn together.
        assertEquals(v.wheels[0].rotation.radians, v.wheels[1].rotation.radians, 1e-9)
    }

    @Test
    fun testWheelsStopDeadWhilePausedAtTheEndOfALeg() {
        val b = bot()
        val v = makeVisual(b)
        tick(v)
        // Walk it into the far end of its patrol, which starts the pause.
        var guard = 0
        while (b.pauseTimer <= 0.0 && guard++ < 6000) { b.update(1.0 / 60.0); tick(v) }
        assertTrue(b.pauseTimer > 0.0, "Expected the bot to reach the end of its leg and pause")

        val held = v.wheels[0].rotation.radians
        repeat(20) { b.update(1.0 / 60.0); tick(v) }
        assertEquals(held, v.wheels[0].rotation.radians, 1e-9, "A stopped rover's wheels do not turn")
    }

    @Test
    fun testMirroringForTheLeftFacingChassisReversesTheAngleSoTheWheelRollsWithTravel() {
        // Going left, the chassis is drawn with scaleX = -1, which flips the on-screen sense of a
        // child's rotation. The local angle therefore has to come out with the OPPOSITE sign of
        // the one used facing right over the same ground, or the wheels visibly spin backwards.
        val right = bot(facing = 1.0)
        val vr = makeVisual(right)
        tick(vr)
        repeat(60) { right.update(1.0 / 60.0); tick(vr) }
        val rightAngle = vr.wheels[0].rotation.radians

        val left = bot(facing = -1.0)
        val vl = makeVisual(left)
        tick(vl)
        repeat(60) { left.update(1.0 / 60.0); tick(vl) }
        val leftAngle = vl.wheels[0].rotation.radians

        assertTrue(rightAngle > 0.0, "Travelling right rolls the wheel clockwise, got $rightAngle")
        assertTrue(leftAngle > 0.0, "Mirrored, travelling left must ALSO be a positive local angle (it renders reversed), got $leftAngle")
        assertEquals(rightAngle, leftAngle, 1e-6, "Equal distance, equal roll, whichever way it faces")
    }

    @Test
    fun testARespawnTeleportDoesNotSpinTheWheels() {
        // reset() snaps the bot back to its start. Integrating that jump as travel would whip the
        // wheels through however many turns the patrol was long.
        val b = bot()
        val v = makeVisual(b)
        tick(v)
        repeat(300) { b.update(1.0 / 60.0); tick(v) }
        val before = v.wheels[0].rotation.radians
        assertTrue(before > 0.0)

        b.reset()
        tick(v)
        assertEquals(before, v.wheels[0].rotation.radians, 1e-9, "A teleport home is not travel")
    }

    @Test
    fun testLevel7BotsAllGetWheels() {
        val world = game.model.GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        assertTrue(world.cameraBots.isNotEmpty())
        val visuals = CameraBotVisual.createAll(Container(), world.cameraBots, plate(128), plate(64))
        assertEquals(world.cameraBots.size, visuals.size)
        for (v in visuals) assertEquals(2, v.wheels.size)
    }

    @Test
    fun testTheProceduralFallbackHasNoWheelsAndDoesNotCrash() {
        // Every call site passes the bitmaps as nullables that default to null on a failed load,
        // so the rect-built crawler has to keep working with no art at all.
        val b = bot()
        val v = CameraBotVisual.createAll(Container(), listOf(b), null, null).first()
        assertTrue(v.wheels.isEmpty())
        repeat(30) { b.update(1.0 / 60.0); tick(v) }
    }

    @Test
    fun testWheelAngleStaysBoundedOverALongPatrol() {
        // rollAngle accumulates without wrapping; a rotation of a few hundred radians is fine for
        // a float matrix, but this pins that nothing is integrating garbage.
        val b = bot()
        val v = makeVisual(b)
        tick(v)
        repeat(60 * 120) { b.update(1.0 / 60.0); tick(v) }
        val a = v.wheels[0].rotation.radians
        assertTrue(a.isFinite(), "Angle went non-finite")
        assertTrue(abs(a) < 4000.0 * PI, "Angle ran away: $a")
    }
}
