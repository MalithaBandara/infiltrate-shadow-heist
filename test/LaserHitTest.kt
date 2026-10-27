package test

import game.model.*
import kotlin.math.*
import kotlin.test.*

/**
 * A laser kills only what touches its drawn core ([Laser.coreThickness]) with the body as drawn
 * ([GameWorld.laserBody]) - 2026-10-27, "even though i am not touching the laser, it counts it as
 * touching if i am very close to it ... especially in lasers that are diagonal".
 */
class LaserHitTest {

    private fun diagonal(tiltDeg: Double) = Laser(
        id = "probe", topX = 1000.0, topY = 150.0,
        bottomX = 1000.0 + tan(tiltDeg * PI / 180.0) * 290.0, bottomY = 440.0,
        beamThickness = 6.0, isAlwaysActive = true
    ).also { it.update(0.0) }

    /** The beam's x at height [y]. */
    private fun Laser.xAt(y: Double) = topX + (bottomX - topX) * (y - topY) / (bottomY - topY)

    @Test
    fun aBodyOnlyClearOfTheCoreIsNotHitAndOneTouchingItIs() {
        for (tilt in listOf(0.0, 20.0, 35.0, 45.0)) {
            val laser = diagonal(tilt)
            val half = laser.coreThickness / 2.0
            // A 21.6-wide column standing just right of the beam: its nearest point is the
            // bottom-left corner (the beam leans right going down).
            val w = 21.6
            // Its bottom (396) above the beam's end (440), so the nearest point of the beam is on
            // the line, not its end.
            val top = 300.0
            val beamX = max(laser.xAt(top), laser.xAt(top + 96.0))
            // Horizontal gap that puts the corner exactly `gap` from the line.
            fun at(gap: Double) = Rect(beamX + gap / cos(tilt * PI / 180.0), top, w, 96.0)
            assertFalse(laser.intersectsPlayer(at(half + 0.3)), "tilt $tilt: 0.3 clear of the core is a miss")
            assertTrue(laser.intersectsPlayer(at(half - 0.3)), "tilt $tilt: 0.3 into the core is a hit")
        }
    }

    @Test
    fun theEmptyCornerOfTheCollisionBoxDoesNotCount() {
        val world = GameWorld.createDefault(LevelData.DEFAULT_LEVEL_4)
        val p = world.player
        val laser = diagonal(45.0)
        // Place the body so the beam crosses the box's top-left corner region but passes left of
        // the drawn column at every height.
        val top = 344.0
        val bodyLeftInBox = (p.width - p.footWidth) / 2.0
        val x = laser.xAt(top + p.height) - bodyLeftInBox + laser.coreThickness + 1.0
        p.resetTo(x, top)
        assertTrue(laser.intersectsPlayer(p.bounds), "the 36-wide box does reach the beam (the old test)")
        assertFalse(laser.intersectsPlayer(world.laserBody(p)), "the drawn body does not")
    }
}
