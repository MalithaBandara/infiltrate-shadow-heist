import game.model.*
import game.scene.FlatConeView
import game.scene.GameAudio
import game.scene.GameplayScene
import game.scene.LightConeView
import korlibs.korge.scene.*
import korlibs.korge.time.delayFrame
import korlibs.korge.view.*
import kotlin.math.*
import kotlin.test.*

/**
 * What a cone shows is what it sees. Every drawn cone (guard, camera, rover) is built against
 * GameWorld.sightBlockers, the list the detection rays test, so it stops at whatever hides the
 * player - boxes, platforms, shut doors, lifts and everything that moves - and the player is never
 * spotted in a part of a cone that is not drawn, nor missed in a part that is. And the prisoner
 * (levels 11 and 12) is looked FOR, never looks: he has no cone.
 */
class VisionConeTest {

    private val groundY = 440.0
    private val guardX = 100.0
    private val playerX = 300.0

    /** Open ground, one guard standing still at [guardX] facing right, the player at [playerX]. */
    private fun world(boxes: List<Rect> = emptyList(), moving: List<MovingPlatformDef> = emptyList()): GameWorld {
        val layout = LevelLayout(
            worldWidth = 2400.0,
            playerStartX = playerX,
            playerStartY = groundY - 96.0,
            exitZone = Rect(2200.0, groundY - 140.0, 60.0, 140.0),
            platforms = listOf(Rect(-200.0, groundY, 2800.0, 200.0)),
            boxes = boxes,
            guards = listOf(
                GuardSpawn(
                    startX = guardX, surfaceY = groundY, patrolMinX = 0.0, patrolMaxX = 10_000.0,
                    speed = 0.0, facing = 1.0, visionRange = 260.0, width = 30.0, height = 96.0
                )
            ),
            movingPlatforms = moving,
            hasStartFences = false
        )
        return GameWorld.createFromLayout(LevelData.DEFAULT_LEVEL_6.copy(layout = layout, bonusObjective = null), layout)
    }

    /** Past the spawn grace (detection is off for the first 2s at the spawn). */
    private fun GameWorld.standFor(seconds: Double) {
        repeat((seconds * 60).toInt()) { update(1.0 / 60.0, 0.0, false, false, false) }
    }

    /** The cone the scene draws for [g], against [occluders]. */
    private fun drawnCone(g: Guard, occluders: List<Rect>): List<Vec2d> =
        VisionCone().also { it.update(g.eyePosition, g.facingAngle, g.visionRange, g.visionFov, occluders) }.polygon

    @Test
    fun aGuardDoesNotSeeThePlayerThroughABlockingBox() {
        // The control: nothing between them, he is seen and the cone is drawn over him.
        val open = world()
        open.standFor(3.0)
        assertTrue(open.isPlayerInVision, "with nothing in the way the guard sees the player")
        assertTrue(open.player.keyPoints.any { inside(drawnCone(open.guard, open.sightBlockers), it) })

        // A crate taller than he is, between them: not seen, and the cone is drawn stopping at it.
        val crate = Rect(220.0, groundY - 100.0, 40.0, 100.0)
        val hidden = world(boxes = listOf(crate))
        hidden.standFor(3.0)
        assertFalse(hidden.isPlayerInVision, "the guard sees through the crate")
        assertEquals(0.0, hidden.alertProgress, "the meter moved behind the crate")
        val cone = drawnCone(hidden.guard, hidden.sightBlockers)
        for (p in hidden.player.keyPoints) assertFalse(inside(cone, p), "the cone is drawn over the hidden player at $p")
        assertTrue(cone.drop(1).all { it.x <= crate.left + 1e-6 || it.y < crate.top }, "the cone runs on past the crate")
    }

    @Test
    fun aGuardsConeStopsAtAMovingCrateWhereHisSightDoes() {
        // A load parked between them: it already hid the player from the detection rays, but the
        // cone was drawn through it (against visionOccluders, which leaves out what moves).
        val load = MovingPlatformDef(id = "load", initialX = 220.0, y = groundY - 100.0, width = 40.0, height = 100.0, periodSeconds = 1000.0)
        val w = world(moving = listOf(load))
        w.standFor(3.0)
        assertFalse(w.isPlayerInVision, "the guard sees through the load")
        assertTrue(w.sightBlockers.any { it == w.movingPlatforms.single().bounds }, "the load is not a sight blocker")
        val cone = drawnCone(w.guard, w.sightBlockers)
        for (p in w.player.keyPoints) assertFalse(inside(cone, p), "the cone is drawn through the load over the hidden player at $p")
        assertTrue(
            w.player.keyPoints.any { inside(drawnCone(w.guard, w.visionOccluders), it) },
            "the old cone (static blockers only) should have shown the mismatch this guards against"
        )
    }

    @Test
    fun theConeRebuildsWhenABlockerInReachMovesAndOnlyThen() {
        val cone = VisionCone()
        val eye = Vec2d(0.0, 0.0)
        val near = Rect(100.0, -20.0, 20.0, 40.0)
        val far = Rect(5000.0, -20.0, 20.0, 40.0)
        assertTrue(cone.update(eye, 0.0, 200.0, 1.0, listOf(near, far)))
        assertFalse(cone.update(eye, 0.0, 200.0, 1.0, listOf(near, far)), "nothing changed")
        assertFalse(cone.update(eye, 0.0, 200.0, 1.0, listOf(near, far.copy(x = 5100.0))), "only something out of reach moved")
        assertTrue(cone.update(eye, 0.0, 200.0, 1.0, listOf(near.copy(x = 120.0), far)), "a blocker in reach moved")
        assertEquals(120.0, cone.polygon[cone.polygon.size / 2].x, 1e-6, "the rebuilt cone stops at the moved blocker")
        assertEquals(
            VisionSystem.computeVisionPolygon(eye, 0.0, 200.0, 1.0, listOf(near.copy(x = 120.0), far)),
            cone.polygon,
            "the cached cone is the polygon built from the whole list"
        )
        cone.reset()
        assertFalse(cone.isBuilt)
        assertTrue(cone.update(eye, 0.0, 200.0, 1.0, listOf(near.copy(x = 120.0), far)), "a reset cone rebuilds")
    }

    /**
     * On every level, at a few moments, for every guard, camera and rover: a point is inside the
     * cone the scene draws exactly when the detection's test (in range, in the field of view, a
     * clear line to it past every sight blocker) passes for it. Points within half a unit of the
     * cone's outline or the end of its range are left out (rounding).
     */
    @Test
    fun everyDrawnConeIsExactlyWhereItsEyeSeesOnEveryLevel() {
        var checked = 0
        for (level in LevelData.DEFAULT_LEVELS) {
            val w = GameWorld.createDefault(level)
            var t = 0.0
            for (at in listOf(0.5, 4.0)) {
                while (t < at && !w.isGameOver) { w.update(1.0 / 60.0, 0.0, false, false, false); t += 1.0 / 60.0 }
                val eyes = ArrayList<Eye>()
                for (g in w.allGuards) if (g.visionRange > 0.0) eyes += Eye("guard", g.eyePosition, g.facingAngle, g.visionRange, g.visionFov)
                for (c in w.cameras) eyes += Eye("camera", c.eyePosition, c.facingAngle, c.visionRange, c.visionFov)
                for (b in w.cameraBots) if (!b.isDeactivated) eyes += Eye("rover", b.eyePosition, b.facingAngle, b.visionRange, b.visionFov)
                for (e in eyes) checked += assertConeIsSight(level.id, e, w.sightBlockers)
            }
        }
        assertTrue(checked > 100_000, "too few points checked ($checked)")
    }

    private class Eye(val what: String, val at: Vec2d, val facing: Double, val range: Double, val fov: Double)

    private fun assertConeIsSight(levelId: String, e: Eye, blockers: List<Rect>): Int {
        val cone = VisionCone().also { it.update(e.at, e.facing, e.range, e.fov, blockers) }.polygon
        var n = 0
        val step = 4.0
        var y = e.at.y - e.range
        while (y <= e.at.y + e.range) {
            var x = e.at.x - e.range
            while (x <= e.at.x + e.range) {
                val p = Vec2d(x, y)
                x += step
                val d = e.at.distanceTo(p)
                if (abs(d - e.range) < 0.5 || distanceToOutline(cone, p) < 0.5) continue
                val sees = d <= e.range &&
                    abs(GeometryUtils.angleDifference(atan2(p.y - e.at.y, p.x - e.at.x), e.facing)) <= e.fov / 2.0 &&
                    GeometryUtils.hasLineOfSight(e.at, p, blockers)
                assertEquals(sees, inside(cone, p), "$levelId: ${e.what} at ${e.at} - ${if (sees) "sees" else "does not see"} $p, but the cone drawn says otherwise")
                n++
            }
            y += step
        }
        return n
    }

    private fun inside(poly: List<Vec2d>, p: Vec2d): Boolean {
        var odd = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) odd = !odd
            j = i
        }
        return odd
    }

    private fun distanceToOutline(poly: List<Vec2d>, p: Vec2d): Double {
        var best = Double.MAX_VALUE
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[j]
            val b = poly[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
            best = min(best, hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy)))
            j = i
        }
        return best
    }

    /**
     * Levels 11 and 12 as the scene builds them: one cone per guard, camera and rover and nothing
     * else - the prisoner walks under the same eyes but carries no cone of his own.
     */
    @Test
    fun thePrisonerHasNoCone() = GameplaySceneArtTest.Harness().run {
        GameAudio.silentForTesting = true
        try {
            val container = views.stage.sceneContainer(views)
            for (level in listOf(LevelData.DEFAULT_LEVEL_11, LevelData.DEFAULT_LEVEL_12)) {
                val model = GameWorld.createDefault(level)
                assertNotNull(model.prisoner, "${level.id}: no prisoner")
                val scene = container.changeTo { GameplayScene(level) }
                val started = kotlin.time.TimeSource.Monotonic.markNow()
                while (!scene.setupComplete) {
                    assertTrue(started.elapsedNow().inWholeSeconds < 120, "${level.id}: scene setup never finished")
                    views.stage.delayFrame()
                }
                repeat(5) { views.stage.delayFrame() }
                var cones = 0
                fun walk(v: View) {
                    if (v is LightConeView || v is FlatConeView) cones++
                    if (v is Container) v.forEachChild { walk(it) }
                }
                walk(scene.sceneView)
                val eyes = model.allGuards.size + model.cameras.size + model.cameraBots.size
                assertEquals(eyes, cones, "${level.id}: a cone that belongs to no guard, camera or rover")
            }
        } finally {
            GameAudio.silentForTesting = false
        }
    }
}
