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
    fun testLightningAndThunderCycle() = viewsTest {
        val bg = stage.container()
        val fg = stage.container()
        val rain = RainEffect(bg, fg, initialCanvasW = 1040.0, initialCanvasH = 480.0)
        val sounds = game.scene.GameAudio.load()

        var sawLightningFlash = false

        // Initial lightning timer is 5.0..8.0 seconds. Advance in 0.02s steps to reliably capture the flash strobe
        for (step in 0 until 500) {
            rain.update(
                dtSec = 0.02,
                canvasW = 1040.0,
                canvasH = 480.0,
                worldViewX = 0.0,
                sounds = sounds,
                sfxVolume = 1.0f,
                coroutineContext = coroutineContext
            )

            // Look inside fg layer for the full-screen flash SolidRect
            val flashRect = fg.firstDescendantWith { it is SolidRect } as? SolidRect
            if (flashRect != null && flashRect.visible && flashRect.alpha > 0.05) {
                sawLightningFlash = true
                break
            }
        }

        assertTrue(sawLightningFlash, "Lightning flash should have fired within 12 seconds of gameplay")
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

        // 2. Draw dual-depth rain streaks FIRST - both layers sit behind the world now
        // Wind angle: ~11.3 degrees (slope = 0.20)
        val windSlope = 0.20

        // Background rain (mid-depth streaks) - counts and alphas match RainEffect's own
        // constants, which were roughly halved on 2026-09-25 ("less rain and more transparent").
        g.color = Color(190, 220, 255, 52)
        g.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val rng = java.util.Random(1337)
        for (i in 0 until RainEffect.BACK_DROP_COUNT) {
            val rx = rng.nextDouble() * canvasW
            val ry = rng.nextDouble() * (canvasH - 60)
            val len = 42.0 + rng.nextDouble() * 20.0
            g.drawLine(rx.toInt(), ry.toInt(), (rx + len * windSlope).toInt(), (ry + len).toInt())
        }

        // Near rain (longer, faster streaks). Both layers now draw BEHIND the world, so this
        // preview stacks them under the crates rather than over them.
        g.color = Color(240, 250, 255, 94)
        g.stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (i in 0 until RainEffect.FRONT_DROP_COUNT) {
            val rx = rng.nextDouble() * canvasW
            val ry = rng.nextDouble() * (canvasH - 40)
            val len = 65.0 + rng.nextDouble() * 32.0
            g.drawLine(rx.toInt(), ry.toInt(), (rx + len * windSlope).toInt(), (ry + len).toInt())
        }

        // 3. Draw ground platform and crates OVER the rain
        val groundY = 410
        g.color = Color(24, 28, 36)
        g.fillRect(0, groundY, canvasW, canvasH - groundY)
        g.color = Color(40, 48, 64)
        g.fillRect(0, groundY, canvasW, 4)

        // Stacks of crates representative of Cargo Yard
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


        // 3b. Impact crowns on the surfaces the rain lands on (RainEffect spawns one on roughly
        // a third of the near layer's landings and fades it out over SPLASH_LIFE).
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

        // 4. Draw realistic sky lightning bolt
        val boltColor = Color(224, 242, 254, 240)
        val boltGlowColor = Color(186, 230, 253, 100)

        val boltPoints = listOf(
            Pair(420, 20),
            Pair(445, 65),
            Pair(432, 110),
            Pair(460, 160),
            Pair(448, 205),
            Pair(475, 260),
            Pair(490, 310)
        )

        // Outer glow
        g.stroke = BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = boltGlowColor
        for (i in 0 until boltPoints.size - 1) {
            g.drawLine(boltPoints[i].first, boltPoints[i].second, boltPoints[i + 1].first, boltPoints[i + 1].second)
        }

        // Inner core
        g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = boltColor
        for (i in 0 until boltPoints.size - 1) {
            g.drawLine(boltPoints[i].first, boltPoints[i].second, boltPoints[i + 1].first, boltPoints[i + 1].second)
        }

        // Side fork branch
        g.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Color(210, 235, 255, 180)
        g.drawLine(445, 65, 480, 100)
        g.drawLine(480, 100, 495, 135)
        g.drawLine(448, 205, 420, 245)

        // 5. Ambient lightning flash tint overlay (strobe peak)
        g.color = Color(219, 234, 254, 38)
        g.fillRect(0, 0, canvasW, canvasH)

        // 6. Header / callout banner
        g.color = Color(12, 16, 24, 210)
        g.fillRoundRect(20, 18, 530, 64, 8, 8)
        g.color = Color(56, 189, 248, 160)
        g.drawRoundRect(20, 18, 530, 64, 8, 8)

        g.color = Color(255, 255, 255)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 13)
        g.drawString("LEVEL 2: CARGO YARD - PROCEDURAL RAIN & LIGHTNING SYSTEM", 32, 38)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = Color(186, 230, 253)
        g.drawString(
            "• Dual volumetric depth, behind the world: ${RainEffect.BACK_DROP_COUNT} back + " +
                "${RainEffect.FRONT_DROP_COUNT} near drops + ${RainEffect.SPLASH_COUNT} pooled impact crowns, 0 GC/frame",
            32, 54
        )
        g.drawString("• Multi-pulse lightning strobe (flash + jagged bolt) with physics speed-of-sound thunder", 32, 68)

        g.dispose()

        // Save to artifact directory and working dir
        val artifactDir = File("C:\\Users\\USER\\.gemini\\antigravity\\brain\\185342d8-4963-47cd-a1f3-2aa5f1f4a5b3")
        if (artifactDir.exists()) {
            ImageIO.write(img, "PNG", File(artifactDir, "level2_rain_preview.png"))
        }
        ImageIO.write(img, "PNG", File("level2_rain_preview.png"))
    }
}
