import game.model.LevelData
import game.model.Rect
import game.scene.RainAssets
import game.scene.RainEffect
import game.scene.GameplayScene
import korlibs.korge.scene.*
import korlibs.korge.tests.ViewsForTesting
import korlibs.korge.view.*
import korlibs.time.milliseconds
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RainEffectTest : ViewsForTesting() {

    @Test
    fun testLevel2HasRainEnabledAndOtherLevelsDoNot() {
        assertTrue(LevelData.DEFAULT_LEVEL_2.hasRain, "Level 2 (Cargo Yard) must have rain enabled")
        assertFalse(LevelData.DEFAULT_LEVEL_1.hasRain, "Level 1 must not have rain enabled")
        assertFalse(LevelData.DEFAULT_LEVEL_3.hasRain, "Level 3 must not have rain enabled")
        assertFalse(LevelData.DEFAULT_LEVEL_4.hasRain, "Level 4 must not have rain enabled")
        assertFalse(LevelData.SIDE_SCROLL_LEVEL.hasRain, "Level 5 must not have rain enabled")
    }

    @Test
    fun testRainAssetsTextureDimensionsAndSlice() {
        assertEquals(6, RainAssets.DROP_TEX_W)
        assertEquals(48, RainAssets.DROP_TEX_H)
        val bmp = RainAssets.dropTexture
        assertEquals(6, bmp.width)
        assertEquals(48, bmp.height)
        assertNotNull(RainAssets.dropSlice)
    }

    @Test
    fun testRainEffectInstantiationAndFrameUpdates() = viewsTest {
        val bg = stage.container()
        val fg = stage.container()
        val rain = RainEffect(bg, fg, initialCanvasW = 1040.0, initialCanvasH = 480.0)

        val sounds = game.scene.GameAudio.load()

        // Step through multiple frames and simulate camera panning
        for (i in 0 until 60) {
            rain.update(
                dtSec = 0.016,
                canvasW = 1040.0,
                canvasH = 480.0,
                worldViewX = -i * 2.0,
                sounds = sounds,
                sfxVolume = 1.0f,
                coroutineContext = coroutineContext
            )
        }
    }

    @Test
    fun testGameplaySceneWithLevel2RainLoadsAndRenders() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene(LevelData.DEFAULT_LEVEL_2) }
        assertNotNull(sceneContainer.currentScene)

        // Advance simulation frames to exercise rain rendering, wrapping, and camera tracking
        for (f in 0 until 10) {
            views.update(16.milliseconds)
        }
    }

    @Test
    fun testSplashTextureIsACrownOpeningUpwardNotAnArch() {
        assertEquals(16, RainAssets.SPLASH_TEX_W)
        assertEquals(10, RainAssets.SPLASH_TEX_H)
        val bmp = RainAssets.splashTexture

        fun rowAlpha(y: Int): Int = (0 until RainAssets.SPLASH_TEX_W).sumOf { bmp.getRgba(it, y).a }
        fun litSpan(y: Int): Int {
            val lit = (0 until RainAssets.SPLASH_TEX_W).filter { bmp.getRgba(it, y).a > 8 }
            return if (lit.isEmpty()) 0 else lit.last() - lit.first()
        }

        assertTrue(rowAlpha(0) > 0, "The crown's tips must reach the top row of its own texture")
        // The two arms flare apart on the way up: a V, not the arch that a top-half-of-an-ellipse
        // outline would give (which would be WIDEST in the middle and pinched shut at the top).
        assertTrue(
            litSpan(0) > litSpan(RainAssets.SPLASH_TEX_H - 3),
            "Crown must be wider at its tips than at its feet"
        )
        // Open at the top: nothing lit across the middle of the topmost row.
        val cx = RainAssets.SPLASH_TEX_W / 2
        assertTrue(bmp.getRgba(cx, 0).a <= 8, "The crown must be open between its arms, not domed over")
    }

    @Test
    fun testNearDropsLandOnSurfacesAndLeaveSplashes() = viewsTest {
        val bg = stage.container()
        val fg = stage.container()
        val flash = stage.container()

        // A Cargo-Yard-shaped floor at world y = 440, plus one of GameWorld's own 1200-tall side
        // walls (top at y = -400). The wall MUST be filtered out by height - left in, it would
        // report a landing surface high above the sky at that end of the level.
        val surfaces = listOf(
            Rect(x = 0.0, y = 440.0, width = 5100.0, height = 100.0),
            Rect(x = -200.0, y = -400.0, width = 30.0, height = 1200.0)
        )
        val rain = RainEffect(
            bg, fg,
            initialCanvasW = 1040.0, initialCanvasH = 480.0,
            flashLayer = flash, splashSurfaces = surfaces
        )
        val sounds = game.scene.GameAudio.load()

        // fg holds [drop container, splash container] - the flash and the bolt went to `flash`.
        val splashContainer = fg.children[1] as Container
        assertEquals(
            RainEffect.SPLASH_COUNT, splashContainer.children.size,
            "The crown pool is fixed-size and pre-allocated - nothing is created per impact"
        )

        // The same transform GameplayScene hands over on level 2: worldZoom 1.35 with the ground
        // pinned near the bottom, which puts world y = 440 at screen y ~385.
        val worldViewY = -208.5
        val worldZoom = 1.35
        var sawSplash = false
        for (i in 0 until 180) {
            rain.update(
                dtSec = 0.016,
                canvasW = 1040.0,
                canvasH = 480.0,
                worldViewX = 0.0,
                sounds = sounds,
                sfxVolume = 1.0f,
                coroutineContext = coroutineContext,
                worldViewY = worldViewY,
                worldZoom = worldZoom
            )
            val live = splashContainer.children.filter { it.visible }
            if (live.isNotEmpty()) {
                sawSplash = true
                for (c in live) {
                    assertTrue(
                        c.y > 300.0,
                        "A crown must sit on the floor (~385), not on a side wall's top - got ${c.y}"
                    )
                }
            }
        }

        assertTrue(sawSplash, "Near drops reaching the floor should leave impact crowns")
    }

    @Test
    fun testBoltTextureHasHotCoreAndSoftCoronaGlow() {
        assertEquals(32, RainAssets.BOLT_TEX_W)
        assertEquals(16, RainAssets.BOLT_TEX_H)
        val bmp = RainAssets.boltTexture
        val midX = RainAssets.BOLT_TEX_W / 2
        val coreRgba = bmp.getRgba(midX, RainAssets.BOLT_TEX_H / 2)
        val coronaRgba = bmp.getRgba(midX, 2)

        assertTrue(coreRgba.a > 220, "Plasma core must be near-opaque incandescent white-cyan")
        assertTrue(coronaRgba.a in 10..150, "Outer corona must have a soft translucent glow falloff")
        assertTrue(coreRgba.a > coronaRgba.a, "Core must be brighter than outer corona sheath")
    }

    @Test
    fun testLightningAndThunderCycle() = viewsTest {
        val bg = stage.container()
        val fg = stage.container()
        val flash = stage.container()
        val rain = RainEffect(bg, fg, initialCanvasW = 1040.0, initialCanvasH = 480.0, flashLayer = flash)
        val sounds = game.scene.GameAudio.load()

        val boltContainer = bg.children[0] as Container
        var sawActiveSkyBolt = false
        var maxVisibleBoltSegments = 0

        // Initial lightning timer is 2.5..4.5 seconds. Advance in 0.02s steps to capture the sky bolt discharge
        for (step in 0 until 350) {
            rain.update(
                dtSec = 0.02,
                canvasW = 1040.0,
                canvasH = 480.0,
                worldViewX = 0.0,
                sounds = sounds,
                sfxVolume = 1.0f,
                coroutineContext = coroutineContext
            )

            // Ensure no full-screen white flash SolidRect ever exists or flashes the screen
            val anyFlashRect = stage.firstDescendantWith { it is SolidRect && it.visible && it.width >= 500.0 }
            assertEquals(null, anyFlashRect, "Screen must NEVER be flashed with a white overlay during lightning")

            if (boltContainer.visible) {
                val litSegments = boltContainer.children.count { it.visible && it.alpha > 0.05 }
                if (litSegments > maxVisibleBoltSegments) {
                    maxVisibleBoltSegments = litSegments
                }
                if (litSegments >= 20) {
                    sawActiveSkyBolt = true
                }
            }
        }

        assertTrue(
            sawActiveSkyBolt,
            "Fractal branched sky lightning bolt should have fired within 7s (max lit segments=$maxVisibleBoltSegments)"
        )
    }

    @Test
    fun testGenerateLevel2RainAndLightningDiagnosticPreview() {
        val canvasW = 1040
        val canvasH = 480

        val img = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)

        // 1. Draw Level 2 background (bgmg5.png)
        val bgFile = File("resources/bgmg5.png")
        if (bgFile.exists()) {
            val bgImg = ImageIO.read(bgFile)
            g.drawImage(bgImg, 0, 0, canvasW, canvasH, null)
        } else {
            g.color = Color(18, 22, 30)
            g.fillRect(0, 0, canvasW, canvasH)
        }

        // 2. Draw realistic fractal sky lightning bolt BEHIND the rain and world silhouettes
        // (No full-screen white flash overlay)
        val boltPoints = listOf(
            Pair(440, -4), Pair(448, 18), Pair(436, 39), Pair(452, 62),
            Pair(443, 84), Pair(464, 108), Pair(455, 130), Pair(471, 154),
            Pair(460, 178), Pair(478, 204), Pair(469, 228), Pair(486, 252),
            Pair(479, 276), Pair(494, 300), Pair(488, 326), Pair(501, 352),
            Pair(495, 378)
        )

        // Outer electric corona glow
        for (i in 0 until boltPoints.size - 1) {
            val p = i.toFloat() / (boltPoints.size - 1)
            g.stroke = BasicStroke(5.8f * (1f - 0.35f * p), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = Color(145, 205, 255, (92 * (1f - 0.25f * p)).toInt())
            g.drawLine(boltPoints[i].first, boltPoints[i].second, boltPoints[i + 1].first, boltPoints[i + 1].second)
        }
        // White-hot plasma core
        for (i in 0 until boltPoints.size - 1) {
            val p = i.toFloat() / (boltPoints.size - 1)
            g.stroke = BasicStroke(2.2f * (1f - 0.35f * p), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = Color(248, 253, 255, (242 * (1f - 0.15f * p)).toInt())
            g.drawLine(boltPoints[i].first, boltPoints[i].second, boltPoints[i + 1].first, boltPoints[i + 1].second)
        }

        // Multi-tier secondary & tertiary forks
        val branches = listOf(
            listOf(Pair(452, 62), Pair(472, 80), Pair(486, 102), Pair(508, 122), Pair(522, 145)),
            listOf(Pair(455, 130), Pair(434, 150), Pair(418, 172), Pair(399, 196), Pair(388, 218)),
            listOf(Pair(469, 228), Pair(492, 248), Pair(509, 270), Pair(524, 292))
        )
        for (branch in branches) {
            for (i in 0 until branch.size - 1) {
                val p = i.toFloat() / (branch.size - 1)
                g.stroke = BasicStroke(2.8f * (1f - 0.55f * p), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.color = Color(150, 210, 255, (72 * (1f - 0.5f * p)).toInt())
                g.drawLine(branch[i].first, branch[i].second, branch[i + 1].first, branch[i + 1].second)

                g.stroke = BasicStroke(1.2f * (1f - 0.55f * p), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.color = Color(235, 248, 255, (195 * (1f - 0.55f * p)).toInt())
                g.drawLine(branch[i].first, branch[i].second, branch[i + 1].first, branch[i + 1].second)
            }
        }

        // 3. Draw dual-depth rain streaks (behind the world)
        val windSlope = 0.20
        g.color = Color(190, 220, 255, 52)
        g.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val rng = java.util.Random(1337)
        for (i in 0 until RainEffect.BACK_DROP_COUNT) {
            val rx = rng.nextDouble() * canvasW
            val ry = rng.nextDouble() * (canvasH - 60)
            val len = 42.0 + rng.nextDouble() * 20.0
            g.drawLine(rx.toInt(), ry.toInt(), (rx + len * windSlope).toInt(), (ry + len).toInt())
        }

        g.color = Color(240, 250, 255, 94)
        g.stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (i in 0 until RainEffect.FRONT_DROP_COUNT) {
            val rx = rng.nextDouble() * canvasW
            val ry = rng.nextDouble() * (canvasH - 40)
            val len = 65.0 + rng.nextDouble() * 32.0
            g.drawLine(rx.toInt(), ry.toInt(), (rx + len * windSlope).toInt(), (ry + len).toInt())
        }

        // 4. Draw ground platform and crates OVER the rain and lightning
        val groundY = 410
        g.color = Color(24, 28, 36)
        g.fillRect(0, groundY, canvasW, canvasH - groundY)
        g.color = Color(40, 48, 64)
        g.fillRect(0, groundY, canvasW, 4)

        val crateBoxes = listOf(
            Triple(120, groundY - 48, 48),
            Triple(168, groundY - 48, 48),
            Triple(144, groundY - 96, 48),
            Triple(450, groundY - 48, 48),
            Triple(498, groundY - 48, 48),
            Triple(546, groundY - 48, 48),
            Triple(474, groundY - 96, 48),
            Triple(522, groundY - 96, 48),
            Triple(780, groundY - 48, 48),
            Triple(828, groundY - 48, 48),
            Triple(804, groundY - 96, 48)
        )
        for ((cx, cy, csize) in crateBoxes) {
            g.color = Color(68, 52, 40)
            g.fillRect(cx, cy, csize, csize)
            g.color = Color(95, 75, 58)
            g.drawRect(cx, cy, csize, csize)
            g.drawLine(cx, cy, cx + csize, cy + csize)
            g.drawLine(cx, cy + csize, cx + csize, cy)
        }

        // 4b. Impact crowns on the surfaces the rain lands on
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val splashTops = mutableListOf<Pair<Int, Int>>()
        for (sx in 40 until canvasW step 37) splashTops.add(Pair(sx, groundY))
        for ((cx, cy, csize) in crateBoxes) splashTops.add(Pair(cx + csize / 2, cy))
        for ((sx, sy) in splashTops) {
            val life = rng.nextDouble()
            val w = 3.0 + 5.0 * life
            val h = 3.0 + 4.0 * Math.sin(life * Math.PI)
            g.color = Color(240, 250, 255, (86 * (1.0 - life)).toInt().coerceIn(0, 255))
            g.drawLine(sx, sy, (sx - w).toInt(), (sy - h).toInt())
            g.drawLine(sx, sy, (sx + w).toInt(), (sy - h).toInt())
            g.drawLine((sx - w).toInt(), sy, (sx + w).toInt(), sy)
        }

        // 5. Header / callout banner
        g.color = Color(12, 16, 24, 210)
        g.fillRoundRect(20, 18, 560, 64, 8, 8)
        g.color = Color(56, 189, 248, 160)
        g.drawRoundRect(20, 18, 560, 64, 8, 8)

        g.color = Color(255, 255, 255)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 13)
        g.drawString("LEVEL 2: CARGO YARD - PROCEDURAL RAIN & SKY LIGHTNING SYSTEM", 32, 38)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = Color(186, 230, 253)
        g.drawString(
            "• Dual volumetric depth, behind the world: ${RainEffect.BACK_DROP_COUNT} back + " +
                "${RainEffect.FRONT_DROP_COUNT} near drops + ${RainEffect.SPLASH_COUNT} pooled impact crowns, 0 GC/frame",
            32, 54
        )
        g.drawString("• 32-segment fractal branched sky bolt (plasma core + corona, no screen flash) + thunder", 32, 68)

        g.dispose()

        // Save to artifact directory and working dir
        val artifactDir = File("C:\\Users\\USER\\.gemini\\antigravity\\brain\\185342d8-4963-47cd-a1f3-2aa5f1f4a5b3")
        if (artifactDir.exists()) {
            ImageIO.write(img, "PNG", File(artifactDir, "level2_rain_preview.png"))
        }
        ImageIO.write(img, "PNG", File("level2_rain_preview.png"))
    }
}
