package game.scene

import game.model.*
import game.scene.UiComponents.COLOR_PRIMARY
import game.scene.UiComponents.createMenuButton
import game.scene.UiComponents.drawAtmosphericBackdropBitmap
import game.scene.UiComponents.uiGraphics
import korlibs.image.bitmap.Bitmap
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.readBitmap
import korlibs.image.vector.*
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.input.*
import korlibs.korge.scene.*
import korlibs.korge.service.storage.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.math.geom.vector.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*

class MainMenuScene : Scene() {

    private val ink = Colors["#17140F"]

    private fun ShapeBuilder.drawInkPlay() {
        fill(ink) { moveTo(-7.0, -10.0); lineTo(10.0, 0.0); lineTo(-7.0, 10.0); close() }
    }

    // Bold strokes (2.8+) with filled centers - thin ~2px strokes anti-alias down to a faint
    // gray at typical scale and read as barely-visible next to the solid-filled play triangle.
    private fun ShapeBuilder.drawInkTarget() {
        stroke(ink, StrokeInfo(thickness = 3.0)) {
            circle(Point(0.0, 0.0), 11.0)
            moveTo(0.0, -16.0); lineTo(0.0, -11.0)
            moveTo(0.0, 11.0); lineTo(0.0, 16.0)
            moveTo(-16.0, 0.0); lineTo(-11.0, 0.0)
            moveTo(11.0, 0.0); lineTo(16.0, 0.0)
        }
        fill(ink) { circle(Point(0.0, 0.0), 3.2) }
    }

    private fun ShapeBuilder.drawInkCart() {
        stroke(ink, StrokeInfo(thickness = 2.8)) {
            moveTo(-12.0, -9.0); lineTo(-9.0, -9.0); lineTo(-5.5, 5.0); lineTo(9.0, 5.0); lineTo(11.5, -3.5)
            lineTo(-8.0, -3.5)
        }
        fill(ink) {
            circle(Point(-4.0, 10.0), 2.4)
            circle(Point(7.0, 10.0), 2.4)
        }
    }

    // A filled disc with radiating teeth reads as a sun/flower, not a gear - a real cog needs a
    // hollow center. The ring's stroke naturally leaves the center transparent (no fill-hole
    // trick needed), with trapezoidal teeth attached to its rim.
    private fun ShapeBuilder.drawInkGear() {
        val ringR = 7.5
        stroke(ink, StrokeInfo(thickness = 3.2)) {
            circle(Point(0.0, 0.0), ringR)
        }
        fill(ink) {
            val toothInnerR = 6.3; val toothOuterR = 12.0
            val baseHalfW = 2.6; val tipHalfW = 1.6
            for (i in 0 until 8) {
                val a = i * PI / 4.0
                val ux = cos(a); val uy = sin(a)
                val px = -uy; val py = ux
                moveTo(ux * toothInnerR + px * baseHalfW, uy * toothInnerR + py * baseHalfW)
                lineTo(ux * toothOuterR + px * tipHalfW, uy * toothOuterR + py * tipHalfW)
                lineTo(ux * toothOuterR - px * tipHalfW, uy * toothOuterR - py * tipHalfW)
                lineTo(ux * toothInnerR - px * baseHalfW, uy * toothInnerR - py * baseHalfW)
                close()
            }
        }
    }

    private fun ShapeBuilder.drawFolderIcon() {
        val c = Colors["#ECE7DA"]
        fill(c) {
            moveTo(-12.0, -7.0); lineTo(-4.0, -7.0); lineTo(-1.0, -4.0); lineTo(12.0, -4.0)
            lineTo(12.0, 9.0); lineTo(-12.0, 9.0)
            close()
        }
        stroke(Colors.BLACK.withAd(0.25), StrokeInfo(thickness = 1.0)) {
            moveTo(-12.0, -7.0); lineTo(-4.0, -7.0); lineTo(-1.0, -4.0); lineTo(12.0, -4.0)
            lineTo(12.0, 9.0); lineTo(-12.0, 9.0)
            close()
        }
    }

    // Loaded once in sceneMain and reused by every rebuild, so a live resize (see onSizeChanged)
    // redraws immediately from what's already in memory instead of re-hitting the VFS for the
    // same three assets on every frame of a window drag.
    private var bgBitmap: Bitmap? = null
    private var logoBmp: Bitmap? = null
    private var bebasFont: Font = DefaultTtfFont

    // onSizeChanged can't itself be suspend (it overrides a plain Scene callback), so a resize
    // launches this and cancels whatever previous rebuild was still in flight - createMenuButton
    // is suspend (it does its own asset reads), so buildMenuLayout can't be made fully
    // synchronous without duplicating that loading logic here.
    private var rebuildJob: Job? = null

    override suspend fun SContainer.sceneMain() {
        val levelStorage: LevelStorage = MapBackedLevelStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )

        bgBitmap = try { resourcesVfs["bg12.png"].readBitmap() } catch (e: Exception) { null }
        logoBmp = try { resourcesVfs["logo_main.png"].readBitmap() } catch (e: Exception) { null }
        bebasFont = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (e: Exception) { DefaultTtfFont }

        buildMenuLayout()
    }

    // Re-entered on every live window resize (see onSizeChanged) as well as once from sceneMain.
    private suspend fun SContainer.buildMenuLayout() {
        // Canvas fills whatever the actual window/device is (virtualSize in main.kt matches the
        // window's aspect ratio) - read it at runtime rather than assuming a fixed 800x480, so
        // the backdrop and right-anchored elements below reach the real edges on any screen.
        val canvasW = sceneWidth.toDouble()
        val canvasH = sceneHeight.toDouble()

        bgBitmap?.let { drawAtmosphericBackdropBitmap(it, canvasW, canvasH) }

        val btnH = 56.0
        val startY = 150.0
        val btnSpacing = btnH + 12.0

        // Every measurement below is a canvasW fraction with a hard floor, not a fixed pixel
        // count, so the whole stack shrinks together on a narrow/portrait aspect ratio instead of
        // overflowing past the edge of the canvas (canvasW itself gets much smaller than the
        // 1040-wide reference on those screens, since the engine keeps height fixed at 480 and
        // varies width to match the real device aspect - see the canvasW/canvasH comment above).
        val leftX = (canvasW * 0.053).coerceIn(14.0, 55.0)
        val rightMargin = 16.0
        val maxContentW = (canvasW - leftX - rightMargin).coerceAtLeast(60.0)
        val btnW = (canvasW * 0.32).coerceIn(60.0, 320.0).coerceAtMost(maxContentW)

        // No drawn divider panel here - bg12.png has its own dark silhouette on the left fading
        // into a misty, lightly-lit skyline on the right, so that natural boundary in the artwork
        // itself reads as the split on a wide/landscape canvas. But on a narrow/portrait one the
        // backdrop (anchored to the right edge, see drawAtmosphericBackdrop) gets cropped down to
        // a sliver of its own right side, so the artwork's left-side darkness may not even be on
        // screen - the fade below is sized to guarantee full black behind the button/logo box
        // regardless (not just a canvasW fraction), starting fully opaque and easing out, so
        // legibility never depends on what the cropped backdrop happens to show there.
        val contentRightEdge = leftX + btnW + 20.0
        val fadeWidth = max(canvasW * 0.34, contentRightEdge).coerceAtMost(canvasW)
        val fadeBands = 14
        uiGraphics().updateShape {
            for (i in 0 until fadeBands) {
                val t0 = i.toDouble() / fadeBands
                val t1 = (i + 1).toDouble() / fadeBands
                val alpha = (1.0 - t0).pow(1.4)
                if (alpha <= 0.01) continue
                fill(Colors.BLACK.withAd(alpha)) {
                    rect(fadeWidth * t0, 0.0, fadeWidth * (t1 - t0) + 1.0, canvasH)
                }
            }
        }

        // Logo
        val logoBmp = logoBmp
        if (logoBmp != null) {
            val logoTargetW = (canvasW * 0.3).coerceIn(140.0, 312.0).coerceAtMost(maxContentW)
            val scale = logoTargetW / logoBmp.width
            // logo_main.png has its drawn content inset from the raw canvas edge by a transparent
            // margin (measured directly from the source PNG: ~1.8% of its width) - the button
            // textures below were cropped tight to their content (see createMenuButton's
            // heistTexture asset prep), so their visible left edge sits exactly at leftX. Shift
            // the logo left by its own inset so its VISIBLE edge (not its image origin) lines up
            // with the button stack's.
            val logoLeftInsetFrac = 39.0 / 2172.0
            val logoX = leftX - logoLeftInsetFrac * logoTargetW
            image(logoBmp) { size(logoBmp.width * scale, logoBmp.height * scale) }.xy(logoX, 30.0)
        } else {
            val logoContainer = container().xy(leftX, 40.0)
            logoContainer.text("INFILTRATE", textSize = 54.0, color = COLOR_PRIMARY)
            logoContainer.text("SHADOW HEIST", textSize = 14.0, color = COLOR_PRIMARY).xy(4.0, 60.0)
        }

        // Main Menu Stack: each button gets its own worn-poster texture variant so the four
        // don't read as one graphic stamped four times in a row.
        createMenuButton("PLAY", btnW, btnH, leftX, startY, heistStyle = true, heistTexture = "button1.png", iconRenderer = { drawInkPlay() }) {
            sceneContainer.changeTo { LevelSelectScene() }
        }

        createMenuButton("MISSIONS", btnW, btnH, leftX, startY + btnSpacing, heistStyle = true, heistTexture = "button2.png", iconRenderer = { drawInkTarget() }) {
            sceneContainer.changeTo { LevelSelectScene() }
        }

        createMenuButton("STORE", btnW, btnH, leftX, startY + btnSpacing * 2, heistStyle = true, heistTexture = "button3.png", iconRenderer = { drawInkCart() }) {
            sceneContainer.changeTo { StoreScene() }
        }

        createMenuButton("SETTINGS", btnW, btnH, leftX, startY + btnSpacing * 3, heistStyle = true, heistTexture = "button4.png", iconRenderer = { drawInkGear() }) {
            sceneContainer.changeTo { SettingsScene() }
        }

        // Bottom-right mission preview card. Vector-drawn rather than a baked bitmap - card_bg.png
        // was a fixed 310x140 source that pixelated once scaled up to fill a larger canvas, the
        // same problem the button textures had (see createMenuButton's doc comment).
        val cardW = 300.0
        val cardH = 118.0
        val card = container().xy(canvasW - cardW - 20.0, canvasH - cardH - 20.0)
        val cardG = card.uiGraphics()
        fun drawCardBg(fillColor: RGBA, borderAlpha: Double) {
            cardG.updateShape {
                clear()
                fill(fillColor) { roundRect(0.0, 0.0, cardW, cardH, 10.0, 10.0) }
                stroke(Colors.WHITE.withAd(borderAlpha), StrokeInfo(thickness = 1.0)) {
                    roundRect(0.0, 0.0, cardW, cardH, 10.0, 10.0)
                }
            }
        }
        drawCardBg(Colors["#0A0A0B"].withAd(0.92), 0.1)

        card.uiGraphics().xy(34.0, 40.0).updateShape { drawFolderIcon() }

        card.text("MISSION 03", textSize = 12.0, color = Colors["#9A9A9E"]).xy(62.0, 16.0)
            .also { it.graphicsRenderer = GraphicsRenderer.GPU }
        card.text("THE WAREHOUSE", textSize = 22.0, font = bebasFont, color = Colors.WHITE).xy(60.0, 30.0)
            .also { it.graphicsRenderer = GraphicsRenderer.GPU }
        card.text("Infiltrate the warehouse and\nretrieve the stolen files.", textSize = 11.0, color = Colors["#B7B7BC"]).xy(62.0, 68.0)
            .also { it.graphicsRenderer = GraphicsRenderer.GPU }

        card.onOver { drawCardBg(Colors["#17171A"], 0.2) }
        card.onOut { drawCardBg(Colors["#0A0A0B"].withAd(0.92), 0.1) }
        card.mouse { onClick { sceneContainer.changeTo { LevelSelectScene() } } }
    }

    // Fires on every live window/device resize (Scene's own hook - see korlibs.korge.scene.Scene)
    // with sceneView already resized to the new size, so a full rebuild picks up the new
    // canvasW/canvasH immediately instead of the menu staying frozen at whatever size it was
    // first shown at. Not suspend itself (it overrides a plain callback), so it launches the
    // suspend rebuild and cancels any still-running one from a prior resize in the same drag.
    override fun onSizeChanged(size: Size) {
        super.onSizeChanged(size)
        rebuildJob?.cancel()
        rebuildJob = launch {
            sceneView.removeChildren()
            sceneView.buildMenuLayout()
        }
    }
}
