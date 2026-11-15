package game.scene

import korlibs.image.bitmap.Bitmap
import korlibs.image.bitmap.slice
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.image.font.BaseTtfFont
import korlibs.image.font.DefaultTtfFontAsBitmap
import korlibs.image.font.Font
import korlibs.image.font.FontMetrics
import korlibs.image.font.LazyBitmapFont
import korlibs.image.text.TextAlignment
import korlibs.image.text.VerticalAlign
import korlibs.io.resources.Resourceable
import korlibs.korge.render.RenderContext
import korlibs.korge.view.Container
import korlibs.korge.view.Image
import korlibs.korge.view.Text
import korlibs.korge.view.ViewDslMarker
import korlibs.math.geom.Rectangle
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * One string drawn by the platform's own text engine ([renderPlatformText]): white, premultiplied,
 * with [pad] transparent pixels on every side of a [textWidth] x [textHeight] pixel text box.
 */
class PlatformTextImage(val bitmap: Bitmap, val pad: Int, val textWidth: Double, val textHeight: Double)

/** What [GameText] tells the platform's text engine besides the string itself. */
object PlatformText {
    /**
     * The game's language (a BCP 47 tag, "" for none) - GameplayScene sets it. Chinese and Japanese
     * share code points, and which shape a character is drawn in follows the language.
     */
    var languageTag: String = ""

    /** The scale the last [GameText] was drawn at: the first guess for one not yet drawn. */
    internal var lastScale: Double = 3.0
}

/**
 * Every `text(...)` in this package: KorGE's own call, building a [GameText]. A function in the
 * package wins over the star-imported `korlibs.korge.view.text`, so no call site names it.
 */
fun Container.text(
    text: String,
    textSize: Number = Text.DEFAULT_TEXT_SIZE,
    color: RGBA = Colors.WHITE,
    font: Resourceable<out Font> = DefaultTtfFontAsBitmap,
    alignment: TextAlignment = TextAlignment.TOP_LEFT,
    block: @ViewDslMarker Text.() -> Unit = {}
): Text {
    val view = GameText(text, textSize.toDouble(), color, font, alignment)
    addChild(view)
    view.block()
    return view
}

/**
 * A [Text] that can draw languages the game's fonts have no letters for.
 *
 * Bebas Neue and the handwritten font cover Latin script only, and KorGE draws a character a font
 * lacks as nothing - so Japanese, Korean, Chinese, Hindi, Arabic, Persian, Thai and (in Bebas)
 * Russian showed no in-game text at all, and Vietnamese lost some letters. A bundled font would
 * not be enough either: KorGE places one glyph per character, which cannot join Arabic letters or
 * build Hindi and Thai syllables.
 *
 * So a string with any character its font lacks is drawn whole by the platform's text engine
 * ([renderPlatformText]: the phone's own fonts, shaping and right-to-left handling) into a bitmap,
 * and shown as an image tinted with the text's colour. A string the font covers - every Latin
 * language, every number - takes KorGE's path exactly as before. Where the platform has no
 * renderer the old path is kept too.
 *
 * Measured like [Text]: the width is the drawn text's, the height the FONT's line height, so a
 * layout written around Bebas keeps its rows whatever the script.
 */
class GameText(
    text: String, textSize: Double, color: RGBA, font: Resourceable<out Font>, alignment: TextAlignment
) : Text(text, textSize, color, font, alignment) {

    private var platformView: Image? = null
    private var platformBounds = Rectangle(0, 0, 0, 0)
    private val fontMetrics = FontMetrics()

    // What the coverage answer below was worked out for.
    private var checkedText: String? = null
    private var checkedFont: Font? = null
    private var needsPlatform = false

    // What the bitmap on screen was drawn from; `drawn` is false when the platform could not draw it.
    private var drawnText: String? = null
    private var drawnSize = 0.0
    private var drawnScale = 0.0
    private var drawnAlignment: TextAlignment? = null
    private var drawn = false

    /** Device pixels per unit of text size the bitmap is drawn at. */
    private var rasterScale = PlatformText.lastScale

    private fun needsPlatformText(): Boolean {
        val f = font.getOrNull() ?: return false
        if (checkedText != text || checkedFont !== f) {
            checkedText = text
            checkedFont = f
            needsPlatform = !f.covers(text)
        }
        return needsPlatform
    }

    /** Draws the bitmap if what it shows has changed. False: the platform cannot draw this. */
    private fun ensureDrawn(): Boolean {
        if (drawnText == text && drawnSize == textSize && drawnScale == rasterScale && drawnAlignment == alignment) return drawn
        drawnText = text
        drawnSize = textSize
        drawnScale = rasterScale
        drawnAlignment = alignment
        val f = font.getOrNull()
        val made = renderPlatformText(
            text, textSize * PLATFORM_SIZE_RATIO * rasterScale, bold = !SceneAssets.isHandwritten(f), PlatformText.languageTag
        )
        drawn = made != null
        if (made == null || f == null) {
            drawn = false
            return false
        }
        val view = platformView ?: Image(made.bitmap).also {
            addChild(it)
            platformView = it
        }
        view.bitmap = made.bitmap.slice()
        val w = made.textWidth / rasterScale
        val h = made.textHeight / rasterScale
        val boxHeight = f.getFontMetrics(textSize, fontMetrics).lineHeight * lineCount
        val left = -w * alignment.horizontal.ratio
        val top = when (alignment.vertical) {
            VerticalAlign.TOP -> 0.0
            VerticalAlign.MIDDLE -> -boxHeight / 2.0
            VerticalAlign.BOTTOM -> -boxHeight
            else -> -boxHeight * BASELINE_RATIO
        }
        platformBounds = Rectangle(left, top, w, boxHeight)
        view.scaleX = 1.0 / rasterScale
        view.scaleY = 1.0 / rasterScale
        view.x = left - made.pad / rasterScale
        view.y = top + (boxHeight - h) / 2.0 - made.pad / rasterScale
        return true
    }

    override fun getLocalBoundsInternal(): Rectangle =
        if (needsPlatformText() && ensureDrawn()) platformBounds else super.getLocalBoundsInternal()

    override fun renderInternal(ctx: RenderContext) {
        val view = platformView
        if (!needsPlatformText()) {
            view?.visible = false
            return super.renderInternal(ctx)
        }
        val m = globalMatrix
        val wanted = (ceil(hypot(m.a, m.b) * 2.0) / 2.0).coerceIn(MIN_SCALE, MAX_SCALE)
        // Sharper when it is drawn bigger than it was rasterised for; not redrawn for every step
        // of a shrink, which a text tweening in or out would otherwise pay for frame by frame.
        if (wanted > rasterScale || wanted < rasterScale * 0.5) {
            rasterScale = wanted
            PlatformText.lastScale = wanted
        }
        if (!ensureDrawn()) {
            platformView?.visible = false
            return super.renderInternal(ctx)
        }
        val shown = platformView ?: return
        shown.visible = true
        shown.colorMul = color
        shown.smoothing = smoothing
        shown.render(ctx)
    }

    companion object {
        /**
         * The platform font's size against the text's. Bebas is all capitals, 0.7 of its size
         * tall; a Japanese or Chinese character fills nearly the whole of it, so at the same size
         * it would stand a third taller than the Latin text the layouts were drawn around.
         */
        const val PLATFORM_SIZE_RATIO = 0.8

        /** Where the baseline sits in a line box, for baseline-aligned text. */
        private const val BASELINE_RATIO = 0.8
        private const val MIN_SCALE = 1.0
        private const val MAX_SCALE = 6.0
    }
}

/** Whether this font has a glyph for every character of [text]; fonts it cannot ask count as yes. */
private fun Font.covers(text: String): Boolean {
    val ttf = when (this) {
        is BaseTtfFont -> this
        is LazyBitmapFont -> font as? BaseTtfFont
        else -> null
    } ?: return true
    for (ch in text) {
        if (ch <= ' ') continue
        if (ttf.getGlyphByCodePoint(ch.code) == null) return false
    }
    return true
}
