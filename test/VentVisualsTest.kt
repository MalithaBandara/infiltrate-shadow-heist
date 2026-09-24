import game.model.CameraBot
import game.model.LevelData
import game.model.PipeMountType
import game.model.Rect
import game.model.SteamPipe
import game.scene.CameraBotVisual
import game.scene.SteamPipeVisual
import korlibs.image.bitmap.Bitmap32
import korlibs.korge.view.Container
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The level 7 fixtures' presentation, after the 2026-09-25 pass.
 *
 * Three of the four things asked for there are removals, and a removal is exactly the kind of
 * change that quietly comes back: someone re-adds a status lamp "so you can see it patrolling", or
 * reinstates a hit flash while chasing some other feedback problem. These pin the absences.
 */
class VentVisualsTest {

    private fun plate(w: Int, h: Int) = Bitmap32(w, h, premultiplied = true)

    private fun bot(facing: Double = 1.0) = CameraBot(
        id = "test_bot",
        x = 1000.0,
        surfaceY = 440.0,
        patrolMinX = 900.0,
        patrolMaxX = 1200.0,
        facing = facing
    )

    private fun visualFor(b: CameraBot) =
        CameraBotVisual.createAll(Container(), listOf(b), plate(128, 128), plate(64, 64)).first()

    private fun tick(v: CameraBotVisual, detecting: Boolean) {
        v.update(1.0 / 60.0, 0.0, -10_000.0, 10_000.0, emptyList<Rect>(), isDetecting = detecting)
    }

    @Test
    fun testAPatrollingRoverCarriesNoRunningLight() {
        // Owner request 2026-09-25: no blue lights. The rover used to carry an additive cyan glow
        // that pulsed at 12 rad/s plus a blue lens and a white pip, lit the whole time it was
        // alive. Nothing is lit while it is merely patrolling.
        val b = bot()
        val v = visualFor(b)
        repeat(30) { b.update(1.0 / 60.0); tick(v, detecting = false) }
        assertFalse(v.alertLens.visible, "A patrolling rover shows no lamp at all")
    }

    @Test
    fun testRoversCarryNoBulbsOrLampsEvenWhenDetecting() {
        // User request: remove any bulbs from the robots.
        // Detection alert is communicated via the surveillance cone and detection pip above head, not chassis bulbs.
        val b = bot()
        val v = visualFor(b)
        tick(v, detecting = false)
        assertFalse(v.alertLens.visible, "A patrolling rover shows no lamp at all")

        tick(v, detecting = true)
        assertFalse(v.alertLens.visible, "Robots carry no bulbs even when detecting")

        tick(v, detecting = false)
        assertFalse(v.alertLens.visible, "No bulbs present")
    }

    @Test
    fun testADeactivatedRoverShowsOnlyItsSparks() {
        val b = bot()
        val v = visualFor(b)
        b.deactivate()
        // Sparks blink on a 1.5s cycle; the lens must be out on every frame of it either way.
        for (i in 0 until 120) {
            v.update(1.0 / 60.0, i / 60.0, -10_000.0, 10_000.0, emptyList<Rect>(), isDetecting = false)
            assertFalse(v.alertLens.visible, "A dead rover never lights its lens")
        }
    }

    @Test
    fun testSteamNozzlesTakeTheArtForBothMountsAndKeepTheirStatusLed() {
        // The LED is a gameplay tell, not decoration - red while dormant, green from the warning
        // flare through the eruption - so it has to survive the art swap that replaced the stacked
        // rects around it.
        val up = plate(256, 64)
        val down = plate(256, 64)
        val pipes = listOf(
            SteamPipe(id = "p_top", x = 500.0, topY = 304.0, bottomY = 440.0, mountType = PipeMountType.TOP),
            SteamPipe(id = "p_bot", x = 700.0, topY = 304.0, bottomY = 440.0, mountType = PipeMountType.BOTTOM)
        )
        val visuals = SteamPipeVisual.createAll(Container(), pipes, up, down)
        assertEquals(2, visuals.size)
        for (v in visuals) {
            repeat(30) { v.update(1.0 / 60.0, 0.0, -10_000.0, 10_000.0) }
        }
    }

    @Test
    fun testSteamNozzlesStillWorkWithNoArtAtAll() {
        // Same nullable-bitmap contract as every other asset here: a failed load degrades to the
        // rect-built nozzle instead of taking the level down.
        val pipes = listOf(
            SteamPipe(id = "p_top", x = 500.0, topY = 304.0, bottomY = 440.0, mountType = PipeMountType.TOP),
            SteamPipe(id = "p_bot", x = 700.0, topY = 304.0, bottomY = 440.0, mountType = PipeMountType.BOTTOM),
            SteamPipe(id = "p_pair", x = 900.0, topY = 304.0, bottomY = 440.0, mountType = PipeMountType.PAIR)
        )
        val visuals = SteamPipeVisual.createAll(Container(), pipes, null, null)
        assertEquals(3, visuals.size)
        for (v in visuals) {
            repeat(30) { v.update(1.0 / 60.0, 0.0, -10_000.0, 10_000.0) }
        }
    }

    @Test
    fun testEveryLevel7SteamPipeGetsAFixture() {
        val world = game.model.GameWorld.createDefault(LevelData.DEFAULT_LEVEL_7)
        assertTrue(world.steamPipes.isNotEmpty())
        val visuals = SteamPipeVisual.createAll(
            Container(), world.steamPipes, plate(256, 64), plate(256, 64)
        )
        assertEquals(world.steamPipes.size, visuals.size)
    }
}
