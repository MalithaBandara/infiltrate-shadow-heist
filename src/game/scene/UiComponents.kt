package game.scene

import korlibs.image.color.*
import korlibs.image.format.readBitmap
import korlibs.image.vector.*
import korlibs.io.async.*
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.input.*
import korlibs.korge.view.*
import korlibs.korge.view.vector.*
import korlibs.math.geom.*
import korlibs.math.geom.vector.*
import korlibs.time.*
import kotlinx.coroutines.*
import korlibs.image.font.*
import kotlin.math.*

object UiComponents {
    val COLOR_PRIMARY = Colors["#ffffff"]
    val COLOR_DARK_BG = Colors["#0e1115"].withAd(0.95)
    val COLOR_HOVER_BG = Colors["#e8eaed"]
    val COLOR_HOVER_TEXT = Colors["#0e1115"]
    val COLOR_TEXT_LIGHT = Colors["#f0f0f5"]
    val COLOR_TEXT_MUTED = Colors["#8a95a5"]
    
    val COLOR_ACCENT_CYAN = Colors["#00e5ff"]
    val COLOR_ACCENT_RED = Colors["#ff2a55"]
    val COLOR_ACCENT_GOLD = Colors["#ffd700"]
    val COLOR_ACCENT_GREEN = Colors["#00e676"]

    val COLOR_BORDER_CYAN = COLOR_ACCENT_CYAN
    val COLOR_BORDER_RED = COLOR_ACCENT_RED
    val COLOR_BORDER_GOLD = COLOR_ACCENT_GOLD
    val COLOR_BORDER_GREEN = COLOR_ACCENT_GREEN

    /**
     * All UI vectors go through the GPU renderer.
     *
     * The default (GraphicsRenderer.SYSTEM) rasterises a shape into a bitmap sized in virtual
     * units, and the 800x480 stage is then scaled up to the real window - so icons and panel
     * borders arrive on screen as a stretched bitmap and look soft. The GPU renderer tessellates
     * the vector and draws it at the device resolution instead, which stays crisp at any scale.
     */
    fun Container.uiGraphics(): Graphics = graphics(renderer = GraphicsRenderer.GPU)

    suspend fun Container.drawAtmosphericBackdrop(width: Double = 800.0, height: Double = 480.0) {
        val bgBitmap = resourcesVfs["bg_menu.jpg"].readBitmap()
        image(bgBitmap) {
            size(width, height)
        }
    }

    /**
     * Left-aligned menu button.
     *
     * `heistStyle = true` is for the main menu's four fixed buttons: a plain white rectangle
     * (matching the logo's white) with the real `iconRenderer` icon and `text` drawn on top in
     * solid ink. Every other caller leaves this `false` and gets a plain paper rect sized exactly
     * to `width`/`height` with `text` on top. An earlier version baked the icon and label into a
     * `btn_bg_N.png` texture as a near-white emboss - faint enough to look blank on its own, but
     * visible as a ghost double-image once real text was drawn on top of it - this draws a flat
     * rect instead, so there's nothing underneath to ghost.
     */
    suspend fun Container.createMenuButton(
        text: String,
        width: Double = 410.0,
        height: Double = 75.0,
        iconPath: String? = null,
        heistStyle: Boolean = false,
        iconRenderer: (ShapeBuilder.() -> Unit)? = null,
        onClick: suspend () -> Unit
    ): Container {
        val btn = container()

        if (heistStyle) {
            // Matches the logo's white paper.
            val ink = Colors["#17140F"]
            val paper = Colors["#F6F4EE"]
            val paperHover = Colors.WHITE
            val paperPress = Colors["#DEDACE"]

            val g = btn.uiGraphics()
            fun draw(fillColor: RGBA) {
                g.updateShape {
                    clear()
                    fill(fillColor) { rect(0.0, 0.0, width, height) }
                    stroke(ink, StrokeInfo(thickness = 3.0)) { rect(0.0, 0.0, width, height) }
                }
            }
            draw(paper)

            if (iconRenderer != null) {
                btn.uiGraphics().xy(38.0, height / 2.0).updateShape { iconRenderer() }
            }
            val font = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (e: Exception) { DefaultTtfFont }
            val textSize = height * 0.44
            val label = btn.text(text.uppercase(), textSize = textSize, font = font, color = ink)
            // Text defaults to the same bitmap-cache-then-scale path Graphics used to (see
            // uiGraphics() above) - it looks soft at this size unless routed through the GPU
            // renderer too.
            label.graphicsRenderer = GraphicsRenderer.GPU
            label.xy(if (iconRenderer != null) 72.0 else 20.0, (height - textSize) / 2.0 - textSize * 0.05)

            btn.onOut { draw(paper) }
            btn.onOver { draw(paperHover) }
            btn.onDown { draw(paperPress) }
            btn.onUp { draw(paperHover) }
            btn.mouse { onClick { onClick() } }
            return btn
        }

        val bg = btn.solidRect(width, height, Colors["#e8e3d8"])
        fun tint(c: RGBA) { bg.colorMul = c }

        val font = try { resourcesVfs["BebasNeue-Regular.ttf"].readTtfFont() } catch (e: Exception) { DefaultTtfFont }
        val textSize = min(26.0, height * 0.46)
        val label = btn.text(text.uppercase(), textSize = textSize, font = font, color = Colors.BLACK)

        var iconImg: Image? = null
        if (iconPath != null) {
            val iconBmp = try { resourcesVfs[iconPath].readBitmap() } catch (e: Exception) { null }
            if (iconBmp != null) {
                iconImg = btn.image(iconBmp).xy(14.0, (height - iconBmp.height) / 2.0)
            }
        }
        label.xy(if (iconImg != null) 40.0 else 16.0, (height - textSize) / 2.0)

        btn.onOut { tint(Colors["#e8e3d8"]) }
        btn.onOver { tint(Colors.WHITE) }
        btn.onDown { tint(Colors.WHITE) }
        btn.onUp { tint(Colors.WHITE) }
        btn.mouse { onClick { onClick() } }

        return btn
    }

    // Alias for legacy scenes
    suspend fun Container.createButton(
        text: String,
        width: Double,
        height: Double,
        textSize: Double = 14.0,
        primary: Boolean = false,
        onClick: suspend () -> Unit
    ) = createMenuButton(text, width, height, iconPath = null, onClick = onClick)

    fun Container.createInfoBox(
        x: Double,
        y: Double,
        title: String,
        subtitle: String,
        iconRenderer: (ShapeBuilder.() -> Unit)
    ) {
        val box = container().xy(x, y)
        val g = box.uiGraphics()
        g.updateShape {
            fill(Colors.BLACK.withAd(0.6)) {
                rect(0.0, 0.0, 220.0, 48.0)
            }
        }
        
        val iconG = box.uiGraphics().xy(24.0, 24.0)
        iconG.updateShape { iconRenderer() }

        box.text(title.uppercase(), textSize = 13.0, color = COLOR_ACCENT_CYAN).xy(50.0, 10.0)
        box.text(subtitle, textSize = 11.0, color = COLOR_TEXT_MUTED).xy(50.0, 26.0)
    }

    fun Container.createIconButton(
        x: Double,
        y: Double,
        iconRenderer: (ShapeBuilder.() -> Unit),
        onClick: suspend () -> Unit
    ) {
        val btn = container().xy(x, y)
        val g = btn.uiGraphics()
        fun drawBg(isHover: Boolean) {
            val color = if(isHover) Colors.WHITE else Colors.BLACK.withAd(0.8)
            g.updateShape {
                clear()
                fill(color) {
                    roundRect(0.0, 0.0, 40.0, 40.0, 8.0, 8.0)
                }
            }
        }
        drawBg(false)
        val iconG = btn.uiGraphics().xy(20.0, 20.0)
        iconG.updateShape { iconRenderer() }
        
        btn.onOut { drawBg(false) }
        btn.onOver { drawBg(true) }
        btn.mouse { onClick { onClick() } }
    }

    fun Container.createTacticalCard(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        accentColor: RGBA = COLOR_ACCENT_CYAN
    ): Container {
        val card = container().xy(x, y)
        val g = card.uiGraphics()
        g.updateShape {
            fill(COLOR_DARK_BG) {
                moveTo(0.0, 0.0)
                lineTo(width, 0.0)
                lineTo(width - 20.0, height)
                lineTo(0.0, height)
                close()
            }
        }
        card.solidRect(4.0, height - 24.0, accentColor).xy(0.0, 12.0)
        return card
    }

    fun ShapeBuilder.drawPlayIcon(isHover: Boolean) {
        val color = Colors.BLACK // Buttons are white now, so icon is black
        fill(color) {
            moveTo(-8.0, -10.0)
            lineTo(10.0, 0.0)
            lineTo(-8.0, 10.0)
            close()
        }
    }

    fun ShapeBuilder.drawStoreIcon(isHover: Boolean) {
        val color = Colors.BLACK
        fill(color) {
            rect(-6.0, -2.0, 12.0, 10.0)
            stroke(color, StrokeInfo(thickness = 2.0)) {
                moveTo(-4.0, -2.0)
                lineTo(-4.0, -6.0)
                lineTo(4.0, -6.0)
                lineTo(4.0, -2.0)
            }
        }
    }

    fun ShapeBuilder.drawSettingsIcon(isHover: Boolean) {
        val color = Colors.BLACK
        fill(color) {
            circle(Point(0.0, 0.0), 4.0)
            for (i in 0 until 8) {
                val angle = i * PI / 4.0
                rect(cos(angle) * 5.0 - 1.5, sin(angle) * 5.0 - 1.5, 3.0, 3.0)
            }
        }
    }

    fun ShapeBuilder.drawMissionsIcon(isHover: Boolean) {
        val color = Colors.BLACK
        stroke(color, StrokeInfo(thickness = 2.0)) {
            rect(-6.0, -8.0, 12.0, 16.0)
            moveTo(-3.0, -3.0); lineTo(3.0, -3.0)
            moveTo(-3.0, 1.0); lineTo(3.0, 1.0)
            moveTo(-3.0, 5.0); lineTo(1.0, 5.0)
        }
    }

    fun ShapeBuilder.drawQuitIcon(isHover: Boolean) {
        val color = Colors.BLACK
        stroke(color, StrokeInfo(thickness = 2.0)) {
            moveTo(0.0, -6.0)
            lineTo(-6.0, -6.0)
            lineTo(-6.0, 6.0)
            lineTo(0.0, 6.0)
            moveTo(-2.0, 0.0); lineTo(6.0, 0.0)
            moveTo(3.0, -3.0); lineTo(6.0, 0.0); lineTo(3.0, 3.0)
        }
    }

    fun ShapeBuilder.drawStar(cx: Double, cy: Double, outerR: Double = 10.0, innerR: Double = 4.0, fillColor: RGBA = COLOR_ACCENT_GOLD) {
        fill(fillColor) {
            moveTo(cx, cy - outerR)
            for (i in 0 until 5) {
                val angle1 = PI / 2.0 + i * (PI * 2.0 / 5.0)
                val angle2 = PI / 2.0 + (i + 0.5) * (PI * 2.0 / 5.0)
                lineTo(cx - cos(angle1) * outerR, cy - sin(angle1) * outerR)
                lineTo(cx - cos(angle2) * innerR, cy - sin(angle2) * innerR)
            }
            close()
        }
    }

    fun ShapeBuilder.drawDiamond(cx: Double, cy: Double, width: Double = 8.0, height: Double = 10.0, fillColor: RGBA = COLOR_ACCENT_CYAN) {
        fill(fillColor) {
            moveTo(cx, cy - height)
            lineTo(cx + width, cy)
            lineTo(cx, cy + height)
            lineTo(cx - width, cy)
            close()
        }
    }

    fun Container.createToast(message: String, width: Double = 400.0, color: RGBA = COLOR_ACCENT_CYAN): Container {
        val toast = container().xy((800.0 - width) / 2.0, 20.0)
        val g = toast.uiGraphics()
        g.updateShape {
            fill(COLOR_DARK_BG) {
                roundRect(0.0, 0.0, width, 44.0, 8.0, 8.0)
            }
            stroke(color, StrokeInfo(thickness = 2.0)) {
                roundRect(0.0, 0.0, width, 44.0, 8.0, 8.0)
            }
        }
        val t = toast.text(message, textSize = 14.0, color = COLOR_PRIMARY)
        t.xy((width - t.width) / 2.0, 14.0)

        this.stage?.launchImmediately {
            delay(3.seconds)
            toast.removeFromParent()
        }
        return toast
    }
}
