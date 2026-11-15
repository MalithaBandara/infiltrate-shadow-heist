package game.model

import kotlin.concurrent.Volatile

/**
 * Where everything on the level loading screen sits, in canvas units - the one set of numbers both
 * GameplayScene's loading screen and the Android host's loading cover (android-shell's
 * LoadingCover.kt) draw from, so the two cannot drift apart.
 *
 * The cover exists because the menu disappears the instant PLAY is pressed, while KorGE needs a
 * moment (its own boot on the first level, then the loading screen's art) before it has drawn
 * anything: until then the player saw KorGE's empty grey view. The host now shows this same screen
 * over it and takes it away when [LoadingScreenSignal] says the real one is up.
 *
 * [logoPixelWidth] x [logoPixelHeight] is logo_main.png's size (0 x 0 when it did not load).
 */
class LoadingScreenLayout(canvasW: Double, canvasH: Double, logoPixelWidth: Int, logoPixelHeight: Int) {

    // The splash is sized off the canvas HEIGHT, unlike everything else on this screen.
    //
    // The gameplay rule (ScreenLayout) keeps the authored 1040x480 rect and grows the canvas
    // around it, so a squarer screen gets extra sky rather than a bigger world. That is right for
    // a level and wrong for a title card: sized as a share of width, the logo holds 34% of the
    // width everywhere but falls from a quarter of the reference phone's height to a seventh of a
    // 4:3 iPad's, with the difference left as dead margin - which is the "logo too small, bar too
    // short on iPad" report. Scaling by canvasH / DESIGN_HEIGHT instead holds the share of HEIGHT
    // constant, which is what the eye is actually measuring against.
    //
    // The ceiling guards a canvas squarer than any real device (ScreenLayout clamps at 1:1, where
    // this would otherwise reach 2.17). A 4:3 iPad lands at 1.625 and gives up 1.5%.
    //
    // Written as an aspect ratio rather than as canvasH / DESIGN_HEIGHT, which is the same number
    // only while the canvas is exactly the design rect grown to fit. ScreenLayout's zoom cap broke
    // that identity - a 4:3 canvas is now 800x600, not 1040x780 - and the height-only form
    // silently shrank the logo back to 43% of the screen on a tablet, undoing the fix above. This
    // form holds the share of height constant whatever canvas it is handed.
    val splashScale: Double = (ScreenLayout.DESIGN_ASPECT * canvasH / canvasW).coerceIn(1.0, 1.6)

    val logoWidth: Double = canvasW * 0.34 * splashScale
    val logoHeight: Double =
        if (logoPixelWidth > 0) logoWidth * logoPixelHeight / logoPixelWidth else 0.0

    val barWidth: Double = canvasW * 0.30 * splashScale

    // The bar's width drives its height so it keeps the flat sliver proportion it was drawn with
    // instead of thickening on a taller canvas. 14.4 is the reference 312 x 21.6.
    val barHeight: Double = barWidth / 14.4
    val logoGap: Double = 35.0 * splashScale
    val labelGap: Double = 17.0 * splashScale

    /** The "LOADING" label's text size. */
    val labelHeight: Double = barHeight * 0.62

    // Centre the stack as one block instead of pinning each piece to its own fraction of the
    // height - fractions push the pieces apart as the canvas grows, which is the other half of
    // why this read as small and scattered on an iPad. 0.474 is where the reference stack's centre
    // already sat, so a 1040x480 canvas still renders what it always did (logo top 125.0 vs 124.8,
    // bar top 277.9 vs 278.4).
    private val blockHeight: Double = logoHeight + logoGap + barHeight + labelGap + labelHeight
    private val blockTop: Double = canvasH * 0.474 - blockHeight / 2.0

    val logoX: Double = (canvasW - logoWidth) / 2.0
    val logoY: Double = blockTop

    val barX: Double = (canvasW - barWidth) / 2.0
    val barY: Double = blockTop + logoHeight + logoGap

    /** Top of the label's line box; it is centred on the canvas by its measured width. */
    val labelY: Double = barY + barHeight + labelGap

    companion object {
        /** The bar's outline: a white line this thick, at this opacity. */
        const val BAR_STROKE: Double = 1.5
        const val BAR_STROKE_ALPHA: Double = 0.85
    }
}

/**
 * Raised by GameplayScene once its loading screen has been on screen for a frame, so a host that
 * covered KorGE while it had nothing drawn (Android's loading cover) can uncover it. Called on the
 * game's own thread - a listener that touches UI state must hop to its UI thread.
 */
object LoadingScreenSignal {
    @Volatile
    var onShown: (() -> Unit)? = null

    fun shown() {
        onShown?.invoke()
    }
}
