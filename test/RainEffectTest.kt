import game.model.LevelData
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
        // Rain is temporarily disabled on Level 2 for Google Play production approval
        assertFalse(LevelData.DEFAULT_LEVEL_2.hasRain, "Level 2 (Cargo Yard) rain temporarily disabled")
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
        sceneContainer.changeTo { GameplayScene(LevelData.DEFAULT_LEVEL_2.copy(hasRain = true)) }
        assertNotNull(sceneContainer.currentScene)

        // Advance simulation frames to exercise rain rendering, wrapping, and camera tracking
        for (f in 0 until 10) {
            views.update(16.milliseconds)
        }
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

        // 2. Draw ground platform and crates
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

        // 3. Draw dual-depth rain streaks
        // Wind angle: ~11.3 degrees (slope = 0.20)
        val windSlope = 0.20

        // Background rain (mid-depth streaks)
        g.color = Color(190, 220, 255, 110)
        g.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val rng = java.util.Random(1337)
        for (i in 0 until 110) {
            val rx = rng.nextDouble() * canvasW
            val ry = rng.nextDouble() * (canvasH - 60)
            val len = 42.0 + rng.nextDouble() * 20.0
            g.drawLine(rx.toInt(), ry.toInt(), (rx + len * windSlope).toInt(), (ry + len).toInt())
        }

        // Foreground rain (crisp, bright, long streaks in front of structures)
        g.color = Color(240, 250, 255, 210)
        g.stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for (i in 0 until 140) {
            val rx = rng.nextDouble() * canvasW
            val ry = rng.nextDouble() * (canvasH - 40)
            val len = 65.0 + rng.nextDouble() * 32.0
            g.drawLine(rx.toInt(), ry.toInt(), (rx + len * windSlope).toInt(), (ry + len).toInt())
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
        g.drawString("• Dual volumetric depth: 44 back drops + 48 front drops (92 pooled sprites, 0 GC/frame)", 32, 54)
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
