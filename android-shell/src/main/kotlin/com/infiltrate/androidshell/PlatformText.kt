package game.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import korlibs.image.format.AndroidNativeImage
import java.util.Locale
import kotlin.math.ceil

/** Transparent pixels around the text, so antialiasing and overhanging strokes are not clipped. */
private const val PAD = 2

/**
 * android-shell's plain (non-`expect`) copy of `renderPlatformText` - see src/PlatformText.kt for
 * what it is for, and src@android/PlatformText.android.kt for the same code on :game's own Android
 * target. This is the one the shipped Android app runs; keep the two in step.
 *
 * Android's Paint draws with the phone's own fonts, falling back per character across every script
 * it has, and shapes and orders the text (Arabic joins, Hindi and Thai syllables, right to left).
 */
fun renderPlatformText(text: String, pixelSize: Double, bold: Boolean, languageTag: String): PlatformTextImage? = try {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    paint.textSize = pixelSize.toFloat()
    paint.color = Color.WHITE
    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    if (languageTag.isNotEmpty()) paint.textLocale = Locale.forLanguageTag(languageTag)
    val lines = text.split('\n')
    val metrics = paint.fontMetrics
    val lineHeight = metrics.descent - metrics.ascent
    val width = lines.maxOf { paint.measureText(it) }
    val height = lineHeight * lines.size
    val bitmap = Bitmap.createBitmap(
        ceil(width).toInt().coerceAtLeast(1) + PAD * 2,
        ceil(height).toInt().coerceAtLeast(1) + PAD * 2,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    // drawText shapes the run and orders right-to-left text itself (Arabic, Persian).
    lines.forEachIndexed { i, line -> canvas.drawText(line, PAD.toFloat(), PAD - metrics.ascent + i * lineHeight, paint) }
    // The native bitmap as it is: premultiplied, off the Java heap, uploaded from there by KorGE.
    PlatformTextImage(AndroidNativeImage(bitmap), PAD, width.toDouble(), height.toDouble())
} catch (_: Throwable) {
    null
}
