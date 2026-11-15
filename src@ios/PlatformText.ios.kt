package game.scene

import korlibs.image.format.ui.toBitmap32
import platform.UIKit.UIImage

/**
 * iOS's `renderPlatformText` (see src/PlatformText.kt). The drawing itself is Swift's -
 * ios-shell/Sources/SystemTextRasterizer.swift, registered on [GameTextBridge] before KorGE boots
 * - for the reason ScreenMetricsBridge.ios.kt gives: UIKit text drawing from Kotlin/Native is a
 * page of cinterop that cannot be compile-checked on the dev machine, and in Swift it is a few
 * plain lines. What crosses is a UIImage (scale 1, so its points are pixels), which korlibs' own
 * `UIImage.toBitmap32()` turns into a premultiplied Bitmap32.
 *
 * With no rasterizer registered (KorGE's own generated iOS app, which has no Swift shell) this
 * answers `null` and GameText keeps KorGE's path.
 */
actual fun renderPlatformText(text: String, pixelSize: Double, bold: Boolean, languageTag: String): PlatformTextImage? {
    val rasterizer = GameTextBridge.rasterizer ?: return null
    return try {
        val drawn = rasterizer.rasterize(text, pixelSize, bold, languageTag) ?: return null
        PlatformTextImage(drawn.image.toBitmap32(), drawn.pad, drawn.textWidth, drawn.textHeight)
    } catch (_: Throwable) {
        null
    }
}

/** What Swift hands back: the drawn image and where the text sits in it (see PlatformTextImage). */
class GameTextRaster(val image: UIImage, val pad: Int, val textWidth: Double, val textHeight: Double)

/** Implemented in Swift (SystemTextRasterizer), like GameAnalyticsSink is by FirebaseGameAnalyticsSink. */
interface GameTextRasterizer {
    fun rasterize(text: String, pixelSize: Double, bold: Boolean, languageTag: String): GameTextRaster?
}

/** Where the Swift shell registers its rasterizer: `GameTextBridge.shared.rasterizer = ...`. */
object GameTextBridge {
    var rasterizer: GameTextRasterizer? = null
}
