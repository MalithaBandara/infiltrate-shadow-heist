package com.infiltrate.androidshell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import game.model.DeviceScreen
import game.model.LoadingScreenLayout
import game.model.Localization
import game.scene.GameText
import game.scene.PlatformText
import game.scene.renderPlatformText
import korlibs.image.format.AndroidNativeImage
import korlibs.image.font.TtfFont
import korlibs.korge.render.BoundsProvider
import korlibs.korge.view.setBoundsInfo
import korlibs.math.geom.Anchor
import korlibs.math.geom.ScaleMode
import korlibs.math.geom.Size
import korlibs.image.font.getTextBounds
import kotlin.concurrent.thread

/**
 * The level loading screen, drawn by the Android host over KorGE's view from the instant a level
 * is started from the menu until KorGE's own loading screen is on screen.
 *
 * Why: the menu goes away the moment PLAY is pressed, but KorGE has nothing drawn yet - on the
 * first level of a session it has to boot, and the loading screen then waits for its art - so the
 * player looked at KorGE's empty grey view, for as long as the phone needed. This is that same
 * loading screen, standing in until the real one arrives (game.model.LoadingScreenSignal).
 *
 * It has to be the SAME picture, so nothing is seen to change when it is taken away:
 * - the same files (loadingbg.png, logo_main.png, BebasNeue-Regular.ttf, from the APK's assets);
 * - the same positions (game.model.LoadingScreenLayout, which GameplayScene's loading screen also
 *   draws from) on the same canvas (DeviceScreen.viewport, fitted and centred as KorGE fits it);
 * - the same label: Bebas where it has the letters, else GameText's platform-drawn bitmap, made
 *   by the same renderer at the same raster scale and placed by the same rule.
 * The bar is empty, as the real one is on its first frame.
 */
@Composable
fun LoadingCover(language: String) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { LoadingCoverArt.draw(it.nativeCanvas, size.width, size.height, language) }
    }
}

object LoadingCoverArt {
    @Volatile private var background: Bitmap? = null
    @Volatile private var logo: Bitmap? = null
    @Volatile private var bebas: Typeface? = null
    @Volatile private var bebasGlyphs: TtfFont? = null
    @Volatile private var started = false

    /** KorGE's clear colour (MainActivity's KorgeConfig), around a canvas that does not fill the view. */
    private const val KORGE_BACKGROUND = 0xFF16161D.toInt()

    /** Pixels of full-white ink across the real bar outline, times the scale - see [draw]. */
    private const val BAR_INK = 0.57

    /**
     * Decodes the art off the UI thread, once, at app start - long before a level can be started.
     * Anything that fails to load is simply not drawn, which is also what the game does without it.
     */
    fun load(context: Context) {
        if (started) return
        started = true
        val assets = context.applicationContext.assets
        thread(name = "LoadingCoverArt", isDaemon = true) {
            try { background = assets.open("loadingbg.png").use { BitmapFactory.decodeStream(it) } } catch (_: Throwable) {}
            try { logo = assets.open("logo_main.png").use { BitmapFactory.decodeStream(it) } } catch (_: Throwable) {}
            try { bebas = Typeface.createFromAsset(assets, "BebasNeue-Regular.ttf") } catch (_: Throwable) {}
            // The same parser the game reads the font with, to ask the same question GameText asks:
            // does Bebas have every letter of this label?
            try { bebasGlyphs = TtfFont(assets.open("BebasNeue-Regular.ttf").use { it.readBytes() }) } catch (_: Throwable) {}
        }
    }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, viewWidth: Float, viewHeight: Float, language: String) {
        canvas.drawColor(KORGE_BACKGROUND)
        val viewport = DeviceScreen.viewport
        // KorGE's virtual size is the viewport in whole units (DeviceViewport.apply); the scene's
        // own canvas keeps the fraction and GameplayScene's floors.
        val virtualW = viewport.width.toInt()
        val virtualH = viewport.height.toInt()
        if (virtualW <= 0 || virtualH <= 0) return
        // Where that canvas lands in the view is asked of KorGE itself - the function Views.resized
        // calls - because it rounds on the way (whole target pixels, whole units of offset, a
        // slightly different scale on each axis), and a cover half a pixel off would show.
        val bounds = BoundsProvider.Base()
        bounds.setBoundsInfo(
            Size(virtualW, virtualH), Size(viewWidth.toInt(), viewHeight.toInt()), ScaleMode.SHOW_ALL, Anchor.CENTER
        )
        val toWindow = bounds.globalToWindowMatrix
        val scale = bounds.globalToWindowScaleAvg.toFloat()
        val canvasW = viewport.width.coerceAtLeast(800.0)
        val canvasH = viewport.height.coerceAtLeast(480.0)
        fun x(v: Double): Float = (toWindow.a * v + toWindow.tx).toFloat()
        fun y(v: Double): Float = (toWindow.d * v + toWindow.ty).toFloat()

        canvas.save()
        canvas.clipRect(x(0.0), y(0.0), x(virtualW.toDouble()), y(virtualH.toDouble()))

        val bg = background
        if (bg != null) {
            canvas.drawBitmap(bg, null, RectF(x(0.0), y(0.0), x(canvasW), y(canvasH)), bitmapPaint)
        } else {
            canvas.drawColor(Color.BLACK)
        }

        val logoBitmap = logo
        val layout = LoadingScreenLayout(canvasW, canvasH, logoBitmap?.width ?: 0, logoBitmap?.height ?: 0)
        if (logoBitmap != null) {
            canvas.drawBitmap(
                logoBitmap, null,
                RectF(x(layout.logoX), y(layout.logoY), x(layout.logoX + layout.logoWidth), y(layout.logoY + layout.logoHeight)),
                bitmapPaint
            )
        }

        // The bar's outline. KorGE's GPU stroke does not come out BAR_STROKE units thick: measured
        // on screen at 1.5, 2.25 and 3 pixels per unit it is a hairline carrying BAR_INK / scale
        // pixels of ink on every side (0.36, 0.26, 0.19). Drawn here as four filled strips of
        // exactly that width - a filled antialiased rect covers what its area says on any GPU,
        // which a sub-pixel stroke does not - or the cover's bar would be a brighter frame that
        // dims when the real one takes over.
        val ink = Paint(Paint.ANTI_ALIAS_FLAG)
        ink.style = Paint.Style.FILL
        ink.color = Color.WHITE
        ink.alpha = (LoadingScreenLayout.BAR_STROKE_ALPHA * 255).toInt()
        val half = (BAR_INK / LoadingScreenLayout.BAR_STROKE_ALPHA / scale / 2.0).toFloat()
        val barLeft = x(layout.barX)
        val barTop = y(layout.barY)
        val barRight = x(layout.barX + layout.barWidth)
        val barBottom = y(layout.barY + layout.barHeight)
        canvas.drawRect(barLeft - half, barTop - half, barRight + half, barTop + half, ink)
        canvas.drawRect(barLeft - half, barBottom - half, barRight + half, barBottom + half, ink)
        canvas.drawRect(barLeft - half, barTop + half, barLeft + half, barBottom - half, ink)
        canvas.drawRect(barRight - half, barTop + half, barRight + half, barBottom - half, ink)

        drawLabel(canvas, Localization.loading(language), language, layout, canvasW, scale, ::x, ::y)
        canvas.restore()
    }

    private fun drawLabel(
        canvas: Canvas, text: String, language: String, layout: LoadingScreenLayout,
        canvasW: Double, scale: Float, x: (Double) -> Float, y: (Double) -> Float
    ) {
        val glyphs = bebasGlyphs
        val typeface = bebas
        if (glyphs == null || typeface == null) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.color = Color.WHITE
        val covered = text.all { it <= ' ' || glyphs.getGlyphByCodePoint(it.code) != null }
        if (covered) {
            // KorGE's path: Bebas at the label's size, one unhinted glyph after another with no
            // kerning, the pen starting where the measured width centres it, the baseline an ascent
            // below the top.
            paint.typeface = typeface
            paint.textSize = (layout.labelHeight * scale).toFloat()
            paint.fontFeatureSettings = "'kern' 0, 'liga' 0"
            paint.isLinearText = true
            paint.hinting = Paint.HINTING_OFF
            val width = glyphs.getTextBounds(layout.labelHeight, text).width
            val ascent = glyphs.getFontMetrics(layout.labelHeight).ascent
            canvas.drawText(text, x((canvasW - width) / 2.0), y(layout.labelY + ascent), paint)
        } else {
            // GameText's path, step for step: the same bitmap from the same renderer at the same
            // raster scale, placed in Bebas's line box and scaled down the same way.
            val rasterScale = GameText.rasterScaleFor(PlatformText.lastScale, scale.toDouble())
            val drawn = renderPlatformText(
                text, layout.labelHeight * GameText.PLATFORM_SIZE_RATIO * rasterScale, bold = true, language
            ) ?: return
            val bitmap = (drawn.bitmap as? AndroidNativeImage)?.androidBitmap ?: return
            val width = drawn.textWidth / rasterScale
            val height = drawn.textHeight / rasterScale
            val boxHeight = glyphs.getFontMetrics(layout.labelHeight).lineHeight
            val left = (canvasW - width) / 2.0 - drawn.pad / rasterScale
            val top = layout.labelY + (boxHeight - height) / 2.0 - drawn.pad / rasterScale
            canvas.drawBitmap(
                bitmap, null,
                RectF(x(left), y(top), x(left + bitmap.width / rasterScale), y(top + bitmap.height / rasterScale)),
                bitmapPaint
            )
        }
    }
}
