package game.scene

import korlibs.image.bitmap.Bitmap32
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil

/** Transparent pixels around the text, so antialiasing and overhanging strokes are not clipped. */
private const val PAD = 2

/**
 * Desktop's `renderPlatformText` (see src/PlatformText.kt), for development and tests: Java2D's
 * logical sans-serif font, which falls back across the scripts the machine has fonts for (the
 * language tag is not used: Java2D has no per-language glyph choice). Not a
 * ship target - the phones draw with their own engines (android-shell's PlatformText.kt, and
 * ios-shell's SystemTextRasterizer.swift through src@ios/PlatformText.ios.kt).
 */
actual fun renderPlatformText(text: String, pixelSize: Double, bold: Boolean, languageTag: String): PlatformTextImage? = try {
    val font = java.awt.Font(java.awt.Font.SANS_SERIF, if (bold) java.awt.Font.BOLD else java.awt.Font.PLAIN, 1)
        .deriveFont(pixelSize.toFloat())
    val lines = text.split('\n')
    val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE).createGraphics()
    val metrics = probe.getFontMetrics(font)
    val lineHeight = metrics.ascent + metrics.descent
    val width = lines.maxOf { metrics.stringWidth(it) }
    val height = lineHeight * lines.size
    probe.dispose()
    val image = BufferedImage(width.coerceAtLeast(1) + PAD * 2, height.coerceAtLeast(1) + PAD * 2, BufferedImage.TYPE_INT_ARGB_PRE)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g.font = font
    g.color = Color.WHITE
    lines.forEachIndexed { i, line -> g.drawString(line, PAD, PAD + metrics.ascent + i * lineHeight) }
    g.dispose()
    // ARGB (premultiplied) to KorGE's RGBA order: red and blue change places.
    val raster = (image.raster.dataBuffer as java.awt.image.DataBufferInt).data
    val rgba = IntArray(raster.size) { i ->
        val p = raster[i]
        (p and 0xFF00FF00.toInt()) or ((p shr 16) and 0xFF) or ((p and 0xFF) shl 16)
    }
    PlatformTextImage(Bitmap32(image.width, image.height, rgba, premultiplied = true), PAD, width.toDouble(), height.toDouble())
} catch (_: Throwable) {
    null
}
