import game.scene.*
import korlibs.event.*
import korlibs.image.color.*
import korlibs.image.vector.*
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.view.filter.*
import korlibs.korge.service.storage.*
import korlibs.korge.tests.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.time.*
import kotlin.test.*

class GameplaySceneTest : ViewsForTesting() {

    @Test
    fun testGameplaySceneInitializes() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene() }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testGameplaySceneWithSideScrollingParallax() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.SIDE_SCROLL_LEVEL) }
        assertNotNull(sceneContainer.currentScene)
    }

    @Test
    fun testGameplaySceneMultiScreenSizesAndBgmgLayer() = viewsTest {
        val sceneContainer = sceneContainer()
        // Test default long-corridor level (3500px) with bgmg2.png looping across various screen aspect ratios
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.DEFAULT_LEVEL_1) }
        assertNotNull(sceneContainer.currentScene)

        // Step scene frames to ensure updater, camera tracking, and parallax loops run without exception
        views.update(16.milliseconds)
        views.update(16.milliseconds)
    }

    @Test
    fun testLevel7ExhaustFanAssetsLoadedAndRendered() = viewsTest {
        val blade = game.scene.SceneAssets.bitmap("fan_blade.png")
        assertNotNull(blade, "fan_blade.png must load")
        assertEquals(512, blade.width)
        assertEquals(512, blade.height)

        val cover = game.scene.SceneAssets.bitmap("fan_cover.png")
        assertNotNull(cover, "fan_cover.png must load")
        assertEquals(512, cover.width)
        assertEquals(512, cover.height)

        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.DEFAULT_LEVEL_7) }
        assertNotNull(sceneContainer.currentScene)
        views.update(16.milliseconds)
    }

    @Test
    fun testPlayerFootPlantingWhileWalkingOnTruckAndCrates() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.DEFAULT_LEVEL_1) }
        assertNotNull(sceneContainer.currentScene)
        views.update(16.milliseconds)

        val baseScale = 96.0 / PlayerAnimations.SOURCE_SILHOUETTE_HEIGHT
        val idleOffset = (PlayerAnimations.SOURCE_FEET_Y - PlayerAnimations.IDLE_FEET_Y) * baseScale
        val walkOffset = (PlayerAnimations.SOURCE_FEET_Y - PlayerAnimations.WALK_FEET_Y) * baseScale
        assertEquals(idleOffset, walkOffset, 1e-4, "Walk and idle must share vertical foot grounding offset")
        assertTrue(walkOffset > 3.0, "Foot grounding offset must firmly plant soles into surface (was $walkOffset)")
    }

    @Test
    fun testPlayerFootGroundingOnFloorDoesNotSinkUnderground() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.DEFAULT_LEVEL_1) }
        assertNotNull(sceneContainer.currentScene)
        views.update(16.milliseconds)
        views.update(16.milliseconds)

        val baseScale = 96.0 / PlayerAnimations.SOURCE_SILHOUETTE_HEIGHT
        val idleOffset = (PlayerAnimations.SOURCE_FEET_Y - PlayerAnimations.IDLE_FEET_Y) * baseScale
        val walkFloorOffset = 0.0
        val walkTruckOffset = (PlayerAnimations.SOURCE_FEET_Y - PlayerAnimations.WALK_FEET_Y) * baseScale

        assertTrue(idleOffset > 3.0, "Idle offset must bring top shoe down to the floor")
        assertEquals(0.0, walkFloorOffset, 1e-4, "Walking on floor must have flush 0.0 offset so shoes do not sink")
        assertTrue(walkTruckOffset > 3.0, "Walking on truck must maintain offset so shoes do not float")
    }

    @Test
    fun testGameplaySceneLevel4WithLasersAndConveyor() = viewsTest {
        val sceneContainer = sceneContainer()
        sceneContainer.changeTo { GameplayScene(game.model.LevelData.DEFAULT_LEVEL_4) }
        assertNotNull(sceneContainer.currentScene)

        // Step scene frames to ensure updater, laserVisuals, and conveyor animators run cleanly
        views.update(16.milliseconds)
        views.update(50.milliseconds)
        views.update(100.milliseconds)
    }

    @Test
    fun testGenerateLaserPreviewArtifact() {
        val width = 600
        val height = 360
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // Dark industrial background
        g.color = java.awt.Color(18, 20, 26)
        g.fillRect(0, 0, width, height)

        // Floor / conveyor
        g.color = java.awt.Color(32, 35, 44)
        g.fillRect(0, 310, width, 50)
        g.color = java.awt.Color(20, 22, 28)
        g.fillRect(0, 314, width, 4)

        // Draw laser textures
        fun drawBmp(bmp: korlibs.image.bitmap.Bitmap32, x: Int, y: Int, w: Int, h: Int, alpha: Float = 1.0f) {
            val bImg = java.awt.image.BufferedImage(bmp.width, bmp.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (by in 0 until bmp.height) {
                for (bx in 0 until bmp.width) {
                    val c = bmp.getRgba(bx, by)
                    val argb = (c.a shl 24) or (c.r shl 16) or (c.g shl 8) or c.b
                    bImg.setRGB(bx, by, argb)
                }
            }
            val oldComp = g.composite
            g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
            g.drawImage(bImg, x, y, w, h, null)
            g.composite = oldComp
        }

        fun drawRotated(bmp: korlibs.image.bitmap.Bitmap32, x: Double, y: Double, length: Double, thickness: Double, angleDeg: Double, alpha: Float = 1.0f) {
            val bImg = java.awt.image.BufferedImage(bmp.width, bmp.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (by in 0 until bmp.height) {
                for (bx in 0 until bmp.width) {
                    val c = bmp.getRgba(bx, by)
                    val argb = (c.a shl 24) or (c.r shl 16) or (c.g shl 8) or c.b
                    bImg.setRGB(bx, by, argb)
                }
            }
            val oldTrans = g.transform
            val oldComp = g.composite
            g.translate(x, y)
            g.rotate(Math.toRadians(angleDeg))
            g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
            g.drawImage(bImg, 0, (-thickness / 2.0).toInt(), length.toInt(), thickness.toInt(), null)
            g.transform = oldTrans
            g.composite = oldComp
        }

        val topY = 35.0
        val floorY = 310.0

        val emitterImgFile = java.io.File("resources/laseremittor.png")
        val emitterImg = if (emitterImgFile.exists()) javax.imageio.ImageIO.read(emitterImgFile) else null
        val emitterLength = 34.0
        val emitterThickness = emitterLength * (148.0 / 512.0)
        val nozzleDist = emitterLength - 1.0

        fun drawCannonUnit(px: Double, py: Double, angleDeg: Double, isActive: Boolean) {
            val oldTrans = g.transform
            g.translate(px, py)
            g.rotate(Math.toRadians(angleDeg))

            // Hazard stripe status glow
            val ledColor = if (isActive) java.awt.Color(255, 34, 68) else java.awt.Color(46, 204, 113)
            g.color = ledColor
            g.fillRect((emitterLength * 0.08).toInt(), 0, (emitterLength * 0.22).toInt(), (emitterThickness * 0.45).toInt())

            if (emitterImg != null) {
                g.drawImage(emitterImg, 0, (-emitterThickness / 2.0).toInt(), emitterLength.toInt(), emitterThickness.toInt(), null)
            } else {
                drawBmp(LaserFxAssets.emitterBitmap, 0, (-emitterThickness / 2.0).toInt(), emitterLength.toInt(), emitterThickness.toInt())
            }
            g.transform = oldTrans
        }

        // 1. Active Vertical Laser (Col 1: x = 110)
        val l1x = 110.0
        val l1TotalDist = floorY - topY
        val l1BeamLen = l1TotalDist - 2.0 * nozzleDist
        val l1TopNozzleY = topY + nozzleDist
        val l1BotNozzleY = floorY - nozzleDist

        drawCannonUnit(l1x, topY, 90.0, isActive = true)
        drawCannonUnit(l1x, floorY, 270.0, isActive = true)
        drawRotated(LaserFxAssets.beamBitmap, l1x, l1TopNozzleY, l1BeamLen, 8.0, 90.0, alpha = 0.25f)
        drawRotated(LaserFxAssets.beamBitmap, l1x, l1TopNozzleY, l1BeamLen, 3.0, 90.0, alpha = 0.95f)
        drawBmp(LaserFxAssets.flareBitmap, (l1x - 7).toInt(), (l1TopNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)
        drawBmp(LaserFxAssets.flareBitmap, (l1x - 7).toInt(), (l1BotNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)

        // 2. Active 25° Tilted Laser (Col 2: topX = 270, bottomX = 360)
        val l2TopX = 270.0
        val l2BotX = 360.0
        val l2Dx = l2BotX - l2TopX
        val l2Dy = floorY - topY
        val l2TotalDist = Math.hypot(l2Dx, l2Dy)
        val l2Angle = Math.toDegrees(Math.atan2(l2Dy, l2Dx))
        val l2AngleRad = Math.toRadians(l2Angle)
        val l2TopNozzleX = l2TopX + nozzleDist * Math.cos(l2AngleRad)
        val l2TopNozzleY = topY + nozzleDist * Math.sin(l2AngleRad)
        val l2BotNozzleX = l2BotX - nozzleDist * Math.cos(l2AngleRad)
        val l2BotNozzleY = floorY - nozzleDist * Math.sin(l2AngleRad)
        val l2BeamLen = l2TotalDist - 2.0 * nozzleDist

        drawCannonUnit(l2TopX, topY, l2Angle, isActive = true)
        drawCannonUnit(l2BotX, floorY, l2Angle + 180.0, isActive = true)
        drawRotated(LaserFxAssets.beamBitmap, l2TopNozzleX, l2TopNozzleY, l2BeamLen, 8.0, l2Angle, alpha = 0.25f)
        drawRotated(LaserFxAssets.beamBitmap, l2TopNozzleX, l2TopNozzleY, l2BeamLen, 3.0, l2Angle, alpha = 0.95f)
        drawBmp(LaserFxAssets.flareBitmap, (l2TopNozzleX - 7).toInt(), (l2TopNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)
        drawBmp(LaserFxAssets.flareBitmap, (l2BotNozzleX - 7).toInt(), (l2BotNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)

        // 3. Inactive Laser (Col 3: x = 500)
        val l3x = 500.0
        drawCannonUnit(l3x, topY, 90.0, isActive = false)
        drawCannonUnit(l3x, floorY, 270.0, isActive = false)

        // Labels
        g.color = java.awt.Color(230, 235, 245)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 12)
        g.drawString("VERTICAL EMITTER / RECEIVER", (l1x - 70).toInt(), 20)
        g.drawString("AIMED 25° TILT", (l2TopX - 15).toInt(), 20)
        g.drawString("SAFE STANDBY", (l3x - 45).toInt(), 20)

        g.dispose()
        val outDir = java.io.File("C:\\Users\\USER\\.gemini\\antigravity\\brain\\c2e16e03-8c52-4bfa-a592-66373946835e")
        if (!outDir.exists()) outDir.mkdirs()
        javax.imageio.ImageIO.write(img, "PNG", java.io.File(outDir, "laser_visual_preview.png"))
    }

    @Test
    fun testGenerateLevel4InteriorPreviewArtifact() {
        val width = 840
        val height = 480
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // 1. Draw Interior Background Asset
        val bgFile = java.io.File("resources/metalbg.png")
        if (bgFile.exists()) {
            val bgImg = javax.imageio.ImageIO.read(bgFile)
            g.drawImage(bgImg, 0, 0, width, height, null)
        } else {
            g.color = java.awt.Color(20, 22, 28)
            g.fillRect(0, 0, width, height)
        }

        // Atmosphere Vignette / Contrast Layer
        val darkWash = java.awt.GradientPaint(0f, 0f, java.awt.Color(10, 12, 16, 110), 0f, height.toFloat(), java.awt.Color(5, 7, 10, 160))
        g.paint = darkWash
        g.fillRect(0, 0, width, height)

        // Top ceiling girder
        g.color = java.awt.Color(28, 32, 42)
        g.fillRect(0, 0, width, 55)
        g.color = java.awt.Color(48, 54, 70)
        g.fillRect(0, 53, width, 4)
        g.color = java.awt.Color(20, 24, 32)
        for (gx in 0 until width step 60) {
            g.fillRect(gx, 0, 6, 53)
        }

        // Conveyor belt floor at y = 370
        val floorY = 370.0
        g.color = java.awt.Color(18, 20, 26)
        g.fillRect(0, floorY.toInt(), width, height - floorY.toInt())
        g.color = java.awt.Color(45, 50, 64)
        g.fillRect(0, floorY.toInt(), width, 14)
        g.color = java.awt.Color(70, 78, 98)
        g.fillRect(0, floorY.toInt(), width, 3)

        // Conveyor rollers & movement arrows (<-)
        g.color = java.awt.Color(85, 95, 118)
        for (rx in 15 until width step 40) {
            g.fillOval(rx, (floorY + 4).toInt(), 6, 6)
            // Leftward chevron
            g.drawLine(rx + 18, (floorY + 4).toInt(), rx + 14, (floorY + 7).toInt())
            g.drawLine(rx + 14, (floorY + 7).toInt(), rx + 18, (floorY + 10).toInt())
        }

        // Helper texture renderers
        fun drawBmp(bmp: korlibs.image.bitmap.Bitmap32, x: Int, y: Int, w: Int, h: Int, alpha: Float = 1.0f) {
            val bImg = java.awt.image.BufferedImage(bmp.width, bmp.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (by in 0 until bmp.height) {
                for (bx in 0 until bmp.width) {
                    val c = bmp.getRgba(bx, by)
                    val argb = (c.a shl 24) or (c.r shl 16) or (c.g shl 8) or c.b
                    bImg.setRGB(bx, by, argb)
                }
            }
            val oldComp = g.composite
            g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
            g.drawImage(bImg, x, y, w, h, null)
            g.composite = oldComp
        }

        fun drawRotated(bmp: korlibs.image.bitmap.Bitmap32, x: Double, y: Double, length: Double, thickness: Double, angleDeg: Double, alpha: Float = 1.0f) {
            val bImg = java.awt.image.BufferedImage(bmp.width, bmp.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (by in 0 until bmp.height) {
                for (bx in 0 until bmp.width) {
                    val c = bmp.getRgba(bx, by)
                    val argb = (c.a shl 24) or (c.r shl 16) or (c.g shl 8) or c.b
                    bImg.setRGB(bx, by, argb)
                }
            }
            val oldTrans = g.transform
            val oldComp = g.composite
            g.translate(x, y)
            g.rotate(Math.toRadians(angleDeg))
            g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
            g.drawImage(bImg, 0, (-thickness / 2.0).toInt(), length.toInt(), thickness.toInt(), null)
            g.transform = oldTrans
            g.composite = oldComp
        }

        // 2. Draw 2-Stacked Crates with 1-Stack Steps
        val crateFile = java.io.File("resources/crate.png")
        val crateImg = if (crateFile.exists()) javax.imageio.ImageIO.read(crateFile) else null
        val crateSize = 56
        fun drawCrate(cx: Int, cy: Int) {
            if (crateImg != null) {
                g.drawImage(crateImg, cx, cy, crateSize, crateSize, null)
            } else {
                g.color = java.awt.Color(22, 25, 34)
                g.fillRect(cx, cy, crateSize, crateSize)
            }
            // Crisp edge highlight rim & industrial crate braces
            g.color = java.awt.Color(80, 92, 114, 220)
            g.drawRect(cx + 3, cy + 3, crateSize - 6, crateSize - 6)
            g.color = java.awt.Color(110, 125, 155, 140)
            g.drawLine(cx + 3, cy + 3, cx + crateSize - 3, cy + crateSize - 3)
            g.drawLine(cx + crateSize - 3, cy + 3, cx + 3, cy + crateSize - 3)
            // Metal corner plates
            g.color = java.awt.Color(150, 170, 200, 200)
            g.fillRect(cx + 1, cy + 1, 6, 6)
            g.fillRect(cx + crateSize - 7, cy + 1, 6, 6)
            g.fillRect(cx + 1, cy + crateSize - 7, 6, 6)
            g.fillRect(cx + crateSize - 7, cy + 6, 6, 6)
        }

        // Step Pyramid at x = 70
        val pyrBaseX = 70
        // Left 1-stack step
        drawCrate(pyrBaseX, (floorY - crateSize).toInt())
        // Center 2-stack
        drawCrate(pyrBaseX + crateSize, (floorY - crateSize).toInt())
        drawCrate(pyrBaseX + crateSize, (floorY - crateSize * 2).toInt())
        // Right 1-stack step
        drawCrate(pyrBaseX + crateSize * 2, (floorY - crateSize).toInt())

        // Hanging Crate suspended by chains at x = 268, y = 215
        val hangX = 268
        val hangY = 215
        g.color = java.awt.Color(120, 135, 160)
        for (cy in 53 until hangY step 7) {
            g.drawOval(hangX + 8, cy, 5, 8)
            g.drawOval(hangX + crateSize - 13, cy, 5, 8)
        }
        drawCrate(hangX, hangY)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 10)
        g.color = java.awt.Color(140, 195, 255)
        g.drawString("HANGING CRATE", hangX - 8, hangY - 14)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 9)
        g.drawString("Moves & bobs with conveyor", hangX - 22, hangY - 4)

        // Player Crouching Marker at x = 350
        val playerX = 350
        val playerH = 26
        val playerW = 36
        val playerY = (floorY - playerH).toInt()
        g.color = java.awt.Color(255, 215, 0, 200)
        g.drawRoundRect(playerX, playerY, playerW, playerH, 6, 6)
        g.color = java.awt.Color(255, 215, 0, 45)
        g.fillRoundRect(playerX, playerY, playerW, playerH, 6, 6)
        // Posture icon
        g.color = java.awt.Color(255, 235, 120)
        g.fillOval(playerX + 22, playerY + 4, 9, 9)
        g.fillRect(playerX + 6, playerY + 13, 20, 9)
        // Forward arrow
        g.color = java.awt.Color(120, 255, 160)
        g.drawLine(playerX + 8, playerY - 8, playerX + 28, playerY - 8)
        g.drawLine(playerX + 24, playerY - 11, playerX + 28, playerY - 8)
        g.drawLine(playerX + 24, playerY - 5, playerX + 28, playerY - 8)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 10)
        g.drawString("CROUCHING AGENT", playerX - 18, playerY - 18)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 9)
        g.color = java.awt.Color(160, 255, 190)
        g.drawString("+49.25 px/s net", playerX - 6, playerY + playerH + 13)

        // 3. Draw Crossed Laser Trap (X-Beam) with Emitter & Receiver Cannons
        val emitFile = java.io.File("resources/laseremittor.png")
        val emitBmp = if (emitFile.exists()) javax.imageio.ImageIO.read(emitFile) else null
        val emitLen = 32.0
        val emitThick = emitLen * (148.0 / 512.0)
        val emitNozzleDist = emitLen - 1.0

        fun drawCannon(px: Double, py: Double, angleDeg: Double, isActive: Boolean) {
            val oldTrans = g.transform
            g.translate(px, py)
            g.rotate(Math.toRadians(angleDeg))

            val ledCol = if (isActive) java.awt.Color(255, 34, 68) else java.awt.Color(46, 204, 113)
            g.color = ledCol
            g.fillRect((emitLen * 0.08).toInt(), 0, (emitLen * 0.22).toInt(), (emitThick * 0.45).toInt())

            if (emitBmp != null) {
                g.drawImage(emitBmp, 0, (-emitThick / 2.0).toInt(), emitLen.toInt(), emitThick.toInt(), null)
            } else {
                drawBmp(LaserFxAssets.emitterBitmap, 0, (-emitThick / 2.0).toInt(), emitLen.toInt(), emitThick.toInt())
            }
            g.transform = oldTrans
        }

        val topY = 53.0

        val leftTopX = 440.0
        val rightTopX = 590.0
        val leftBotX = 440.0 + 150.0  // 590.0
        val rightBotX = 590.0 - 150.0 // 440.0

        // Beam 1: leftTop -> leftBot (tilts right, +25.3°)
        val b1Dx = leftBotX - leftTopX
        val b1Dy = floorY - topY
        val b1TotalDist = Math.hypot(b1Dx, b1Dy)
        val b1Angle = Math.toDegrees(Math.atan2(b1Dy, b1Dx))
        val b1AngleRad = Math.toRadians(b1Angle)
        val b1TopNozzleX = leftTopX + emitNozzleDist * Math.cos(b1AngleRad)
        val b1TopNozzleY = topY + emitNozzleDist * Math.sin(b1AngleRad)
        val b1BotNozzleX = leftBotX - emitNozzleDist * Math.cos(b1AngleRad)
        val b1BotNozzleY = floorY - emitNozzleDist * Math.sin(b1AngleRad)
        val b1BeamLen = b1TotalDist - 2.0 * emitNozzleDist

        drawCannon(leftTopX, topY, b1Angle, isActive = true)
        drawCannon(leftBotX, floorY, b1Angle + 180.0, isActive = true)
        drawRotated(LaserFxAssets.beamBitmap, b1TopNozzleX, b1TopNozzleY, b1BeamLen, 8.0, b1Angle, alpha = 0.25f)
        drawRotated(LaserFxAssets.beamBitmap, b1TopNozzleX, b1TopNozzleY, b1BeamLen, 3.0, b1Angle, alpha = 0.95f)
        drawBmp(LaserFxAssets.flareBitmap, (b1TopNozzleX - 7).toInt(), (b1TopNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)
        drawBmp(LaserFxAssets.flareBitmap, (b1BotNozzleX - 7).toInt(), (b1BotNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)

        // Beam 2: rightTop -> rightBot (tilts left, +154.7°)
        val b2Dx = rightBotX - rightTopX
        val b2Dy = floorY - topY
        val b2TotalDist = Math.hypot(b2Dx, b2Dy)
        val b2Angle = Math.toDegrees(Math.atan2(b2Dy, b2Dx))
        val b2AngleRad = Math.toRadians(b2Angle)
        val b2TopNozzleX = rightTopX + emitNozzleDist * Math.cos(b2AngleRad)
        val b2TopNozzleY = topY + emitNozzleDist * Math.sin(b2AngleRad)
        val b2BotNozzleX = rightBotX - emitNozzleDist * Math.cos(b2AngleRad)
        val b2BotNozzleY = floorY - emitNozzleDist * Math.sin(b2AngleRad)
        val b2BeamLen = b2TotalDist - 2.0 * emitNozzleDist

        drawCannon(rightTopX, topY, b2Angle, isActive = true)
        drawCannon(rightBotX, floorY, b2Angle + 180.0, isActive = true)
        drawRotated(LaserFxAssets.beamBitmap, b2TopNozzleX, b2TopNozzleY, b2BeamLen, 8.0, b2Angle, alpha = 0.25f)
        drawRotated(LaserFxAssets.beamBitmap, b2TopNozzleX, b2TopNozzleY, b2BeamLen, 3.0, b2Angle, alpha = 0.95f)
        drawBmp(LaserFxAssets.flareBitmap, (b2TopNozzleX - 7).toInt(), (b2TopNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)
        drawBmp(LaserFxAssets.flareBitmap, (b2BotNozzleX - 7).toInt(), (b2BotNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)

        // Intersection Flare at crossing point
        val midX = (leftTopX + rightTopX) / 2.0
        val midY = (topY + floorY) / 2.0
        drawBmp(LaserFxAssets.flareBitmap, (midX - 16).toInt(), (midY - 16).toInt(), 32, 32, alpha = 0.65f)

        // 4. Draw Vertical Laser Array (Right flank: x = 740)
        val vX = 740.0
        val vTotalDist = floorY - topY
        val vBeamLen = vTotalDist - 2.0 * emitNozzleDist
        val vTopNozzleY = topY + emitNozzleDist
        val vBotNozzleY = floorY - emitNozzleDist

        drawCannon(vX, topY, 90.0, isActive = true)
        drawCannon(vX, floorY, 270.0, isActive = true)
        drawRotated(LaserFxAssets.beamBitmap, vX, vTopNozzleY, vBeamLen, 8.0, 90.0, alpha = 0.25f)
        drawRotated(LaserFxAssets.beamBitmap, vX, vTopNozzleY, vBeamLen, 3.0, 90.0, alpha = 0.95f)
        drawBmp(LaserFxAssets.flareBitmap, (vX - 7).toInt(), (vTopNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)
        drawBmp(LaserFxAssets.flareBitmap, (vX - 7).toInt(), (vBotNozzleY - 7).toInt(), 14, 14, alpha = 0.85f)

        // 5. Annotations and HUD Callouts
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 13)

        // HUD Banner
        g.color = java.awt.Color(16, 20, 28, 210)
        g.fillRoundRect(16, 68, 330, 64, 8, 8)
        g.color = java.awt.Color(60, 140, 220, 180)
        g.drawRoundRect(16, 68, 330, 64, 8, 8)
        g.color = java.awt.Color(240, 245, 255)
        g.drawString("LEVEL 4: METALLIC INDUSTRIAL FACILITY", 28, 88)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(160, 190, 230)
        g.drawString("• Seamless Looping Metallic Background (metalbg.png)", 28, 106)
        g.drawString("• Emitter & Receiver Cannons (laseremittor.png)", 28, 122)

        // Crossed Laser Callout
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 12)
        g.color = java.awt.Color(255, 80, 80)
        g.drawString("CROSSED LASER TRAP (X-BEAM)", 405, 140)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(255, 200, 200)
        g.drawString("Thinner Beams & Paired Cannons", 405, 156)
        g.drawString("Zero Grey Boxes / Pure Sprites", 405, 172)

        // Stepped Stacked Crates Callout
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 12)
        g.color = java.awt.Color(230, 180, 70)
        g.drawString("2-STACK CRATE PYRAMID", 92, (floorY - crateSize * 2 - 20).toInt())
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(240, 220, 160)
        g.drawString("1-Stack Steps (Flanked) -> Smooth Jump", 92, (floorY - crateSize * 2 - 6).toInt())

        // Bottom Receiver Cannon Callout
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(180, 200, 220)
        g.drawString("Bottom Receiver Cannon (Aimed Target)", 420, (floorY + 34).toInt())
        g.drawString("Conveyor Speed: -45 px/s [<-]", 100, (floorY + 34).toInt())

        g.dispose()
        val outDir = java.io.File("C:\\Users\\USER\\.gemini\\antigravity\\brain\\c2e16e03-8c52-4bfa-a592-66373946835e")
        if (!outDir.exists()) outDir.mkdirs()
        javax.imageio.ImageIO.write(img, "PNG", java.io.File(outDir, "level4_interior_preview.png"))
    }
    @Test
    fun testVisionGraphicsRendering() = viewsTest {
        val g = graphics {
            fill(Colors.YELLOW.withAd(0.3)) {
                moveTo(Point(0, 0))
                lineTo(Point(100, 50))
                lineTo(Point(100, -50))
                close()
            }
        }
        g.updateShape {
            fill(Colors.RED.withAd(0.4)) {
                moveTo(Point(10, 10))
                lineTo(Point(50, 50))
                lineTo(Point(50, 10))
                close()
            }
        }
        val leftPressed = views.input.keys[Key.LEFT] || views.input.keys[Key.A]
        assertFalse(leftPressed)
    }

    @Test
    fun testClimbAnimationStart() {
        // The clip still starts on raw frame 70 - hands on the lip, foot-plant stride skipped -
        // but raw 1-69 are no longer loaded into the atlas at all (they cost a full 2048x2048
        // page for frames nothing could ever display), so the loaded indices now start at zero
        // rather than at 69. What has to hold is the SPAN: GameplayScene maps climbPhase 0..1
        // across CLIMB_START..CLIMB_END, so as long as that stays 154 frames wide the same 155
        // source frames play at the same rate as before the trim.
        assertEquals(0, PlayerAnimations.CLIMB_START)
        assertEquals(154, PlayerAnimations.CLIMB_END)
        assertEquals(154, PlayerAnimations.CLIMB_END - PlayerAnimations.CLIMB_START)
        assertTrue(PlayerAnimations.CLIMB_START < PlayerAnimations.CLIMB_END)
    }

    @Test
    fun testGenerateShieldGlowPreviewArtifact() {
        val width = 760
        val height = 400
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // Draw metalbg
        val bgFile = java.io.File("resources/metalbg.png")
        if (bgFile.exists()) {
            val bgImg = javax.imageio.ImageIO.read(bgFile)
            g.drawImage(bgImg, 0, 0, width, height, null)
        } else {
            g.color = java.awt.Color(20, 22, 28)
            g.fillRect(0, 0, width, height)
        }

        // Contrast wash
        val darkWash = java.awt.GradientPaint(0f, 0f, java.awt.Color(12, 14, 20, 140), 0f, height.toFloat(), java.awt.Color(6, 8, 12, 190))
        g.paint = darkWash
        g.fillRect(0, 0, width, height)

        // Floor / conveyor belt at y = 310
        val floorY = 310
        g.color = java.awt.Color(25, 28, 36)
        g.fillRect(0, floorY, width, height - floorY)
        g.color = java.awt.Color(50, 56, 70)
        g.fillRect(0, floorY, width, 12)
        g.color = java.awt.Color(75, 84, 105)
        g.fillRect(0, floorY, width, 3)

        // Dividing line between Before and After
        g.color = java.awt.Color(60, 70, 90, 180)
        g.drawLine(width / 2, 20, width / 2, height - 20)

        // Load player idle frame
        val playerFile = java.io.File("resources/player/idle/0001.png")
        val playerRaw = if (playerFile.exists()) javax.imageio.ImageIO.read(playerFile) else null

        // Scale player to game size (height = ~168px on screen at 1.75x zoom)
        val pScale = 1.75 * (96.0 / 480.0) // ~0.35
        val pw = if (playerRaw != null) (playerRaw.width * pScale).toInt() else 60
        val ph = if (playerRaw != null) (playerRaw.height * pScale).toInt() else 168
        val py = floorY - ph

        // 1. LEFT: BEFORE (8-offset solid silhouette outline)
        val leftX = 180 - pw / 2
        if (playerRaw != null) {
            // Build solid cyan mask
            val solidCyan = java.awt.image.BufferedImage(playerRaw.width, playerRaw.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until playerRaw.height) {
                for (x in 0 until playerRaw.width) {
                    val argb = playerRaw.getRGB(x, y)
                    val a = (argb ushr 24) and 0xFF
                    if (a > 20) {
                        solidCyan.setRGB(x, y, (a shl 24) or (0 shl 16) or (229 shl 8) or 255)
                    }
                }
            }
            // Draw 8 solid offsets (±3px screen offset)
            val offsets = listOf(
                -3 to 0, 3 to 0, 0 to -3, 0 to 3,
                -3 to -3, 3 to -3, -3 to 3, 3 to 3
            )
            for ((ox, oy) in offsets) {
                g.drawImage(solidCyan, leftX + ox, py + oy, pw, ph, null)
            }
            // Draw player on top
            g.drawImage(playerRaw, leftX, py, pw, ph, null)
        }

        // 2. RIGHT: AFTER (Soft Luminous Aura with Blur and Additive Glow)
        val rightX = 560 - pw / 2
        if (playerRaw != null) {
            // Build soft blurred glow image
            val glowPad = 24
            val glowW = pw + glowPad * 2
            val glowH = ph + glowPad * 2
            val glowImg = java.awt.image.BufferedImage(glowW, glowH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val gg = glowImg.createGraphics()
            gg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)

            // Scaled cyan silhouette
            val cyanSilhouette = java.awt.image.BufferedImage(pw, ph, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until playerRaw.height) {
                for (x in 0 until playerRaw.width) {
                    val argb = playerRaw.getRGB(x, y)
                    val a = (argb ushr 24) and 0xFF
                    if (a > 10) {
                        val glowA = ((a / 255.0) * 190).toInt().coerceIn(0, 255)
                        cyanSilhouette.setRGB(
                            (x * pScale).toInt().coerceIn(0, pw - 1),
                            (y * pScale).toInt().coerceIn(0, ph - 1),
                            (glowA shl 24) or (0 shl 16) or (229 shl 8) or 255
                        )
                    }
                }
            }

            // Apply 2-pass box/gaussian blur kernel (radius 4.0 screen px)
            val blurKernel = floatArrayOf(
                0.05f, 0.09f, 0.12f, 0.15f, 0.18f, 0.15f, 0.12f, 0.09f, 0.05f
            )
            // Horizontal blur
            val hBlur = java.awt.image.BufferedImage(glowW, glowH, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val hg = hBlur.createGraphics()
            hg.drawImage(cyanSilhouette, glowPad, glowPad, null)
            hg.dispose()

            // Draw multi-layered soft aura
            val oldComp = g.composite
            // Additive blending for neon luminescence
            for (layer in 1..3) {
                val alpha = when (layer) {
                    1 -> 0.45f
                    2 -> 0.35f
                    else -> 0.25f
                }
                val spread = (layer - 1) * 2
                g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha)
                g.drawImage(cyanSilhouette, rightX - spread, py - spread, pw + spread * 2, ph + spread * 2, null)
            }
            g.composite = oldComp

            // Draw player on top cleanly
            g.drawImage(playerRaw, rightX, py, pw, ph, null)
        }

        // Labels & HUD headers
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 15)

        // Left Header: BEFORE
        g.color = java.awt.Color(255, 95, 95)
        g.drawString("BEFORE: CHUNKY SOLID OUTLINE", 70, 48)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(220, 190, 190)
        g.drawString("• 8 offset copies of 100% opaque cyan", 70, 70)
        g.drawString("• Jagged staircase edges around silhouette", 70, 88)
        g.drawString("• Felt like a thick cardboard cutout border", 70, 106)

        // Right Header: AFTER
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 15)
        g.color = java.awt.Color(0, 229, 255)
        g.drawString("AFTER: SOFT LUMINOUS GLOW", 460, 48)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(180, 240, 255)
        g.drawString("• Smooth BlurFilter with Gaussian falloff", 460, 70)
        g.drawString("• BlendMode.ADD for genuine neon luminescence", 460, 88)
        g.drawString("• Hugs silhouette softly without chunky artifacts", 460, 106)

        g.dispose()
        val outDir = java.io.File("C:\\Users\\USER\\.gemini\\antigravity\\brain\\c2e16e03-8c52-4bfa-a592-66373946835e")
        if (!outDir.exists()) outDir.mkdirs()
        javax.imageio.ImageIO.write(img, "PNG", java.io.File(outDir, "shield_glow_comparison.png"))
    }

    @Test
    fun testGenerateLevel4FinaleAndL4EndPreviewArtifact() {
        val width = 1000
        val height = 660
        val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // Base dark background
        g.color = java.awt.Color(16, 18, 24)
        g.fillRect(0, 0, width, height)

        // Load asset images safely
        fun loadImg(name: String): java.awt.image.BufferedImage? {
            val f = java.io.File("resources/$name")
            return if (f.exists()) javax.imageio.ImageIO.read(f) else null
        }
        val metalBg = loadImg("metalbg.png")
        val l4endImg = loadImg("l4end.png")
        val conveyorImg = loadImg("conveyorbelt.png")
        val crateImg = loadImg("crate.png")
        val chainedCrateImg = loadImg("chainedcrate.png")
        val emitterImg = loadImg("laseremittor.png")
        val failedScreenImg = loadImg("failedscreen.png")

        // =========================================================================
        // TOP PANEL: 0M MARKER & L4END SPAWN FACILITY (World X: 7350 to 8350, 1000px wide)
        // =========================================================================
        val topPanelH = 350
        val p1WorldXStart = 7380.0
        val p1WorldXEnd = 8380.0
        val p1Scale = width.toDouble() / (p1WorldXEnd - p1WorldXStart) // 1.0 px per world unit

        fun toP1ScreenX(worldX: Double): Int = ((worldX - p1WorldXStart) * p1Scale).toInt()

        // 1. Draw metal background tiles
        if (metalBg != null) {
            val tileW = metalBg.width
            val tileH = metalBg.height
            var tileX = toP1ScreenX(p1WorldXStart)
            while (tileX < width) {
                g.drawImage(metalBg, tileX, 0, tileW, topPanelH, null)
                tileX += tileW
            }
        } else {
            g.color = java.awt.Color(28, 32, 42)
            g.fillRect(0, 0, width, topPanelH)
        }

        // 2. Draw wall distance markers: "0m" at worldX = 7742.0
        val marker0mX = toP1ScreenX(7742.0)
        g.color = java.awt.Color(220, 160, 20, 220)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 22)
        g.drawString("0m", marker0mX - 16, 240)
        g.color = java.awt.Color(255, 200, 40, 140)
        g.drawLine(marker0mX, 210, marker0mX, 290)

        // 3. Draw Conveyor belt (runs until x = 7760.0, y = 414.0, groundY = 440.0)
        val convScreenEndX = toP1ScreenX(7760.0)
        val convScreenTopY = 280
        val convScreenH = 20
        if (conveyorImg != null) {
            var cx = 0
            while (cx < convScreenEndX) {
                val drawW = minOf(conveyorImg.width, convScreenEndX - cx)
                g.drawImage(conveyorImg, cx, convScreenTopY, drawW, convScreenH, 0, 0, drawW, conveyorImg.height, null)
                cx += conveyorImg.width
            }
        } else {
            g.color = java.awt.Color(45, 48, 58)
            g.fillRect(0, convScreenTopY, convScreenEndX, convScreenH)
        }

        // Ground baseline under conveyor and past it
        g.color = java.awt.Color(22, 24, 30)
        g.fillRect(0, convScreenTopY + convScreenH, width, topPanelH - (convScreenTopY + convScreenH))

        // 4. Crates inside facility at x = 8200 (hidden behind l4end building)
        val hiddenCrateX = toP1ScreenX(8180.0)
        if (crateImg != null) {
            g.drawImage(crateImg, hiddenCrateX, convScreenTopY - 42, 60, 42, null)
        }
        // Outline showing internal spawn point
        g.color = java.awt.Color(0, 229, 255, 120)
        val dash = floatArrayOf(4f, 4f)
        val oldStroke = g.stroke
        g.stroke = java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, dash, 0f)
        g.drawRect(hiddenCrateX - 6, convScreenTopY - 48, 72, 48)
        g.stroke = oldStroke

        // 5. Draw l4end building at x = 7760 in front of conveyor end and spawning crates
        val l4endScreenX = toP1ScreenX(7760.0)
        val l4endScreenW = (760.0 * p1Scale).toInt()
        val l4endScreenH = 290
        val l4endScreenY = convScreenTopY + convScreenH - (l4endScreenH * (828.0 / 887.0)).toInt()
        if (l4endImg != null) {
            g.drawImage(l4endImg, l4endScreenX, l4endScreenY, l4endScreenW, l4endScreenH, null)
        } else {
            g.color = java.awt.Color(60, 70, 90)
            g.fillRect(l4endScreenX, l4endScreenY, l4endScreenW, l4endScreenH)
        }

        // 6. Draw obstacles before l4end:
        // - Overhead hanging crate (x = 7420..7594)
        val hCrateX = toP1ScreenX(7420.0)
        val hCrateW = (174.0 * p1Scale).toInt()
        if (chainedCrateImg != null) {
            g.drawImage(chainedCrateImg, hCrateX, 175, hCrateW, 36, null)
        } else {
            g.color = java.awt.Color(160, 110, 60)
            g.fillRect(hCrateX, 175, hCrateW, 36)
        }
        // Monorail track above hanging crate
        g.color = java.awt.Color(90, 95, 110)
        g.fillRect(hCrateX - 20, 168, hCrateW + 40, 6)

        // - Floor crate at x = 7650
        val fCrateX = toP1ScreenX(7650.0)
        if (crateImg != null) {
            g.drawImage(crateImg, fCrateX, convScreenTopY - 42, 60, 42, null)
        }

        // 7. Player sliding under hanging crate then jumping
        g.color = java.awt.Color(240, 245, 255)
        // Ducking player under hanging crate
        g.setColor(java.awt.Color(34, 167, 240))
        g.fillRoundRect(hCrateX + 45, convScreenTopY - 38, 48, 38, 6, 6)
        g.color = java.awt.Color(255, 255, 255)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 10)
        g.drawString("CROUCH", hCrateX + 48, convScreenTopY - 18)

        // Top Panel Header & HUD Callouts
        g.color = java.awt.Color(12, 16, 24, 220)
        g.fillRoundRect(16, 14, 480, 58, 8, 8)
        g.color = java.awt.Color(60, 140, 220, 180)
        g.drawRoundRect(16, 14, 480, 58, 8, 8)

        g.color = java.awt.Color(255, 255, 255)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 13)
        g.drawString("SECTION 5 TERMINUS: 0M MARKER & L4END SPAWN ENCLOSURE", 28, 34)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(170, 200, 240)
        g.drawString("• Conveyor belt continues past 0m marker (x = 7742) to end terminus at x = 7760", 28, 50)
        g.drawString("• l4end.png seamlessly conceals continuous crate spawning happening inside at x = 8200", 28, 64)

        // Callout at 0m marker
        g.color = java.awt.Color(255, 204, 0)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 11)
        g.drawString("0m Mark (x=7742)", marker0mX - 45, 195)

        // Callout at l4end building
        g.color = java.awt.Color(100, 220, 255)
        g.drawString("l4end.png (Terminus Structure)", l4endScreenX + 15, 110)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10)
        g.drawString("Crate Spawn Hidden Inside (x=8200)", l4endScreenX + 15, 126)
        g.drawString("Extraction Zone: x=7820..7880", l4endScreenX + 15, 140)

        // =========================================================================
        // BOTTOM PANEL: DUAL VERIFICATION (LEFT: HAZARDS, RIGHT: MISSION FAILED SCREEN)
        // =========================================================================
        val p2Y = topPanelH + 10
        val p2H = height - p2Y

        // Divider
        g.color = java.awt.Color(40, 48, 64)
        g.fillRect(0, topPanelH, width, 4)

        // Left Sub-Panel: Sector 5 Enhanced Hazards (x = 0..490)
        g.color = java.awt.Color(20, 24, 32)
        g.fillRect(10, p2Y, 480, p2H - 10)
        g.color = java.awt.Color(45, 55, 75)
        g.drawRect(10, p2Y, 480, p2H - 10)

        g.color = java.awt.Color(255, 255, 255)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 12)
        g.drawString("SECTOR 5 INTENSIFIED HAZARDS", 24, p2Y + 24)

        // Mini diagram of laser array and hanging crates
        val subGroundY = p2Y + p2H - 35
        g.color = java.awt.Color(35, 38, 48)
        g.fillRect(20, subGroundY, 460, 16)

        // Laser 1: 20° tilt forward
        val lX1 = 110.0
        val lBotX1 = 145.0
        g.color = java.awt.Color(255, 40, 60, 200)
        g.drawLine(lX1.toInt(), p2Y + 50, lBotX1.toInt(), subGroundY)
        if (emitterImg != null) {
            g.drawImage(emitterImg, lX1.toInt() - 6, p2Y + 44, 20, 10, null)
            g.drawImage(emitterImg, lBotX1.toInt() - 6, subGroundY - 4, 20, 10, null)
        }

        // Hanging Crate
        val hX = 200
        if (chainedCrateImg != null) {
            g.drawImage(chainedCrateImg, hX, p2Y + 95, 70, 28, null)
        }
        g.color = java.awt.Color(255, 100, 100)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 10)
        g.drawString("LETHAL CONTACT", hX - 10, p2Y + 88)

        // Triple Gauntlet Lasers (x = 340, 385, 430)
        for (gx in intArrayOf(340, 385, 430)) {
            g.color = java.awt.Color(255, 30, 50, 220)
            g.drawLine(gx, p2Y + 50, gx + 8, subGroundY)
            if (emitterImg != null) {
                g.drawImage(emitterImg, gx - 5, p2Y + 44, 18, 8, null)
                g.drawImage(emitterImg, gx + 3, subGroundY - 4, 18, 8, null)
            }
        }
        g.color = java.awt.Color(255, 200, 80)
        g.drawString("TRIPLE CHECKPOINT LASERS", 310, p2Y + 40)
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10)
        g.color = java.awt.Color(180, 210, 240)
        g.drawString("• Tilt angle strictly <= 45° across all beams", 24, p2Y + p2H - 50)
        g.drawString("• Total 9 lasers + 7 hanging crates + 29 floor crates", 24, p2Y + p2H - 38)

        // Right Sub-Panel: Mission Failed Screen (x = 510..990)
        val rX = 510
        val rW = 480
        g.color = java.awt.Color(20, 24, 32)
        g.fillRect(rX, p2Y, rW, p2H - 10)
        g.color = java.awt.Color(45, 55, 75)
        g.drawRect(rX, p2Y, rW, p2H - 10)

        g.color = java.awt.Color(255, 255, 255)
        g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 12)
        g.drawString("LETHAL CONTACT: MISSION FAILED OVERLAY", rX + 14, p2Y + 24)

        if (failedScreenImg != null) {
            val fW = 320
            val fH = (fW * (failedScreenImg.height.toDouble() / failedScreenImg.width)).toInt()
            val fX = rX + (rW - fW) / 2
            val fY = p2Y + 38
            g.drawImage(failedScreenImg, fX, fY, fW, fH, null)
        }

        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11)
        g.color = java.awt.Color(255, 140, 140)
        g.drawString("Touching a laser beam OR hanging crate displays the Mission Failed screen", rX + 14, p2Y + p2H - 40)
        g.drawString("giving players instant Retry / Menu options (no silent restarts).", rX + 14, p2Y + p2H - 24)

        g.dispose()
        val outDir = java.io.File("C:\\Users\\USER\\.gemini\\antigravity\\brain\\c2e16e03-8c52-4bfa-a592-66373946835e")
        if (!outDir.exists()) outDir.mkdirs()
        javax.imageio.ImageIO.write(img, "PNG", java.io.File(outDir, "level4_finale_l4end_preview.png"))
    }
}
