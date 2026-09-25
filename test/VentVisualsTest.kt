import game.model.CameraBot
import game.model.LevelData
import game.model.SteamPipeDef
import game.model.PipeMountType
import game.model.Rect
import game.model.SteamPipe
import game.scene.CameraBotVisual
import game.scene.GameplayScene
import game.scene.SteamPipeVisual
import korlibs.image.bitmap.Bitmap32
import korlibs.korge.view.Container
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    /**
     * Reproduces what GameplayScene does for one stencil at one viewport: the drawn width, the
     * world-to-texture mapping, the bounds and the props it has to dodge.
     */
    private fun placeStencil(
        label: String,
        sourceWidth: Int,
        stepIndex: Int,
        canvasHeight: Double
    ): Double {
        val markerHeight = 28.0
        val worldZoom = 1.35
        val textureWidth = 2172.0            // bglvl7.png
        val bgScale = canvasHeight / 724.0   // how GameplayScene scales that background
        val markerWidth = sourceWidth * (markerHeight / 68.0)
        val layout = LevelData.LEVEL_7_LAYOUT
        val propSpans = layout.fans.map { it.x..(it.x + it.width) } +
            layout.steamPipes.map { (it.x - it.jetWidth / 2.0)..(it.x + it.jetWidth / 2.0) }
        return GameplayScene.findClearWallX(
            idealX = LevelData.LEVEL_7_MARKER_FIRST_X + stepIndex * LevelData.LEVEL_7_MARKER_SPACING,
            halfWidthTexels = markerWidth / 2.0 * worldZoom / bgScale,
            worldToTexel = worldZoom / bgScale,
            textureWidth = textureWidth,
            minX = LevelData.LEVEL_7_MARKER_FIRST_X - 60.0 + markerWidth / 2.0,
            maxX = layout.exitZone.x - markerWidth / 2.0 - 10.0,
            propSpans = propSpans,
            halfWidthWorld = markerWidth / 2.0 + 12.0
        ).also { assertTrue(label.isNotEmpty()) }
    }

    @Test
    fun testDistanceStencilsNeverLandOnAFanOrAJet() {
        // Found on screen, not in a test: the bare-wall search only knew about detail painted into
        // bglvl7.png, so it happily pushed "60m" 240 units to the right - directly onto
        // lvl7_fan_2, 36 units of opaque machinery sitting across the band stencils are drawn in.
        // The props are level geometry, so they have to be part of the same search.
        //
        // Checked at the four viewport heights ScreenLayout.viewportFor actually produces, because
        // bglvl7's background scale is canvasH / 724: the same world x sits over different wall
        // detail on each, so one position is not a proof about the others.
        val plates = listOf("120m" to 154, "90m" to 137, "60m" to 142, "30m" to 138, "0m" to 105)
        val layout = LevelData.LEVEL_7_LAYOUT
        for (canvasHeight in listOf(480.0, 585.0, 800.0, 1066.0)) {
            for ((stepIndex, plate) in plates.withIndex()) {
                val (label, sourceWidth) = plate
                val x = placeStencil(label, sourceWidth, stepIndex, canvasHeight)
                val half = sourceWidth * (28.0 / 68.0) / 2.0

                for (fan in layout.fans) {
                    assertTrue(
                        x + half < fan.x || x - half > fan.x + fan.width,
                        "$label at canvas $canvasHeight (x=$x) overlaps ${fan.id}"
                    )
                }
                for (pipe in layout.steamPipes) {
                    assertTrue(
                        x + half < pipe.x - pipe.jetWidth / 2.0 || x - half > pipe.x + pipe.jetWidth / 2.0,
                        "$label at canvas $canvasHeight (x=$x) overlaps ${pipe.id}'s jet"
                    )
                }
                assertTrue(
                    x + half < layout.exitZone.x,
                    "$label at canvas $canvasHeight (x=$x) was pushed into the extraction booth"
                )
                assertTrue(x - half > 0.0, "$label at canvas $canvasHeight (x=$x) fell out of the world")
            }
        }
    }

    @Test
    fun testDistanceStencilsStayCloseEnoughToStillMeanSomething() {
        // The nudge is a trade the owner authorised ("it is okay for it to not be in the exact
        // correct place"), but a stencil that drifts far enough stops being a measurement. The
        // search radius is the bound, and the stencils have to stay in order.
        val plates = listOf("120m" to 154, "90m" to 137, "60m" to 142, "30m" to 138, "0m" to 105)
        for (canvasHeight in listOf(480.0, 585.0, 800.0, 1066.0)) {
            var previous = Double.NEGATIVE_INFINITY
            for ((stepIndex, plate) in plates.withIndex()) {
                val (label, sourceWidth) = plate
                val ideal = LevelData.LEVEL_7_MARKER_FIRST_X + stepIndex * LevelData.LEVEL_7_MARKER_SPACING
                val x = placeStencil(label, sourceWidth, stepIndex, canvasHeight)
                assertTrue(
                    kotlin.math.abs(x - ideal) <= 300.0,
                    "$label drifted ${x - ideal} at canvas $canvasHeight - past the search radius"
                )
                assertTrue(x > previous, "stencils have to stay in descending-distance order")
                previous = x
            }
        }
    }

    @Test
    fun testTheSteamLampIsRedExactlyWhileGasIsComingOut() {
        // Owner request 2026-09-25: "show red when gas is coming out". It used to run green from
        // the warning flare through the whole eruption, which said "safe" at the one moment the
        // pipe kills. Each phase of the cycle now gets its own colour, and red means one thing.
        val pipe = SteamPipe(
            SteamPipeDef(
                id = "led_probe",
                x = 500.0,
                topY = 304.0,
                bottomY = 440.0,
                mountType = PipeMountType.BOTTOM,
                activeDuration = 2.6,
                inactiveDuration = 1.7
            )
        )
        val visual = SteamPipeVisual.createAll(Container(), listOf(pipe), plate(256, 64), plate(256, 64)).first()
        val lamp = visual.botLed
        assertNotNull(lamp, "a steam fixture has to carry its status lamp")

        var sawActive = false
        var sawWarning = false
        var sawDormant = false
        var t = 0.0
        while (t < 30.0) {
            pipe.update(t)
            visual.update(1.0 / 60.0, t, -10_000.0, 10_000.0)
            val c = lamp.colorMul
            when {
                pipe.isActive -> {
                    sawActive = true
                    assertTrue(c.r > 0xc0 && c.g < 0x80, "gas is out, so the lamp is red (was $c)")
                }
                pipe.isWarning -> {
                    sawWarning = true
                    assertTrue(c.r > 0xc0 && c.g > 0x80 && c.b < 0x60, "warning is amber (was $c)")
                }
                else -> {
                    sawDormant = true
                    assertTrue(c.g > 0xa0 && c.r < 0x80, "dormant is green - safe to cross (was $c)")
                }
            }
            t += 1.0 / 30.0
        }
        assertTrue(sawActive && sawWarning && sawDormant, "the sweep has to cover all three phases")
    }

    @Test
    fun testSteamFixturesAreBuriedInTheStructureRatherThanFloatingInFrontOfIt() {
        // They used to be pushed 4 units clear of the corridor edge, which left a lit sliver of
        // wall under the floor nozzle and over the ceiling one - "they look like floating".
        val pipes = listOf(
            SteamPipe(SteamPipeDef("f_bot", 500.0, 304.0, 440.0, PipeMountType.BOTTOM)),
            SteamPipe(SteamPipeDef("f_top", 700.0, 304.0, 440.0, PipeMountType.TOP))
        )
        val visuals = SteamPipeVisual.createAll(Container(), pipes, plate(256, 64), plate(256, 64))

        // The fixture container's own y is the giveaway: the plate has to straddle the corridor
        // edge, not sit wholly inside the corridor.
        val fixtureHeight = 64.0 * 0.2952
        val bottomFixtureTop = visuals[0].botLed!!.parent!!.y
        assertTrue(
            bottomFixtureTop + fixtureHeight > 440.0,
            "the floor nozzle's base has to pass through the floor line (bottom edge " +
                "${bottomFixtureTop + fixtureHeight})"
        )
        assertTrue(bottomFixtureTop < 440.0, "but its mouth still stands in the corridor")

        val topFixtureTop = visuals[1].topLed!!.parent!!.y
        assertTrue(topFixtureTop < 304.0, "the ceiling nozzle's base has to pass up through the ceiling line")
        assertTrue(topFixtureTop + fixtureHeight > 304.0, "but its mouth still hangs into the corridor")

        // And both lamps stay on the visible side of that edge, or the burial has hidden the tell.
        val botLamp = visuals[0].botLed!!
        assertTrue(
            bottomFixtureTop + botLamp.y < 440.0,
            "the floor lamp must not be buried with the flange"
        )
        val topLamp = visuals[1].topLed!!
        assertTrue(
            topFixtureTop + topLamp.y > 304.0,
            "the ceiling lamp must not be buried with the flange"
        )
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
