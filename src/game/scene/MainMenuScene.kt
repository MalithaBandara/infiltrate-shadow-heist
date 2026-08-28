package game.scene

import game.model.*
import game.scene.UiComponents.COLOR_PRIMARY
import game.scene.UiComponents.createMenuButton
import game.scene.UiComponents.drawAtmosphericBackdrop
import game.scene.UiComponents.uiGraphics
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

    override suspend fun SContainer.sceneMain() {
        val levelStorage: LevelStorage = MapBackedLevelStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )
        val profileStorage: GameProfileStorage = MapBackedGameProfileStorage(
            getRaw = { views.storage[it] },
            setRaw = { k, v -> views.storage[k] = v }
        )

        // Canvas fills whatever the actual window/device is (virtualSize in main.kt matches the
        // window's aspect ratio) - read it at runtime rather than assuming a fixed 800x480, so
        // the backdrop and right-anchored elements below reach the real edges on any screen.
        val canvasW = sceneWidth.toDouble()
        val canvasH = sceneHeight.toDouble()

        drawAtmosphericBackdrop(canvasW, canvasH)

        val btnH = 58.0
        val startY = 150.0
        val btnSpacing = btnH + 16.0
        val leftX = 30.0

        // Left panel: matte black poster stock with a jagged diagonal right edge, centered on the
        // actual canvas so the split lands at screen-center on any aspect ratio, not a fixed offset
        // tuned for one width.
        val splitHalfSpread = 39.0
        val panelTopX = canvasW / 2.0 + splitHalfSpread
        val panelBotX = canvasW / 2.0 - splitHalfSpread
        uiGraphics().updateShape {
            fill(Colors["#0A0A0B"]) {
                moveTo(0.0, 0.0); lineTo(panelTopX, 0.0); lineTo(panelBotX, canvasH); lineTo(0.0, canvasH); close()
            }
        }
        // Buttons stay a comfortable 380px on a wide canvas but shrink on a narrower one so their
        // right edge can never cross the (now screen-centered) split.
        val btnW = (panelBotX - leftX - 30.0).coerceIn(240.0, 380.0)

        // Logo
        val logoBmp = try { resourcesVfs["logo_main.png"].readBitmap() } catch (e: Exception) { null }
        if (logoBmp != null) {
            val scale = 312.0 / logoBmp.width
            image(logoBmp) { size(logoBmp.width * scale, logoBmp.height * scale) }.xy(30.0, 30.0)
        } else {
            val logoContainer = container().xy(40.0, 40.0)
            logoContainer.text("INFILTRATE", textSize = 54.0, color = COLOR_PRIMARY)
            logoContainer.text("SHADOW HEIST", textSize = 14.0, color = COLOR_PRIMARY).xy(4.0, 60.0)
        }

        // Main Menu Stack: rectangular worn-poster buttons, real icon + text, no baked texture.
        createMenuButton("PLAY", btnW, btnH, heistStyle = true, iconRenderer = { drawInkPlay() }) {
            sceneContainer.changeTo { LevelSelectScene() }
        }.xy(leftX, startY)

        createMenuButton("MISSIONS", btnW, btnH, heistStyle = true, iconRenderer = { drawInkTarget() }) {
            sceneContainer.changeTo { LevelSelectScene() }
        }.xy(leftX, startY + btnSpacing)

        createMenuButton("STORE", btnW, btnH, heistStyle = true, iconRenderer = { drawInkCart() }) {
            sceneContainer.changeTo { StoreScene() }
        }.xy(leftX, startY + btnSpacing * 2)

        createMenuButton("SETTINGS", btnW, btnH, heistStyle = true, iconRenderer = { drawInkGear() }) {
            sceneContainer.changeTo { SettingsScene() }
        }.xy(leftX, startY + btnSpacing * 3)

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

        val bebas = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (e: Exception) { DefaultTtfFont }
        card.text("MISSION 03", textSize = 12.0, color = Colors["#9A9A9E"]).xy(62.0, 16.0)
            .also { it.graphicsRenderer = GraphicsRenderer.GPU }
        card.text("THE WAREHOUSE", textSize = 22.0, font = bebas, color = Colors.WHITE).xy(60.0, 30.0)
            .also { it.graphicsRenderer = GraphicsRenderer.GPU }
        card.text("Infiltrate the warehouse and\nretrieve the stolen files.", textSize = 11.0, color = Colors["#B7B7BC"]).xy(62.0, 68.0)
            .also { it.graphicsRenderer = GraphicsRenderer.GPU }

        card.onOver { drawCardBg(Colors["#17171A"], 0.2) }
        card.onOut { drawCardBg(Colors["#0A0A0B"].withAd(0.92), 0.1) }
        card.mouse { onClick { sceneContainer.changeTo { LevelSelectScene() } } }
    }
}
