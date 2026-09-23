package com.infiltrate.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * One scale, derived from BOTH axes, for every menu screen.
 *
 * ## What was wrong
 *
 * Each screen computed its own `scale = (maxHeight / 720.dp).coerceIn(0.75f, 1.4f)` (0.55f on the
 * main menu). Two things break with that:
 *
 * 1. **Height alone is the wrong input.** A landscape phone is about 390dp tall, so the true ratio
 *    is 0.54 - below the 0.75 floor, which then inflated every box by ~39% and pushed the mission
 *    grid, the store rows and the settings panels off the bottom of the screen. The floor was put
 *    there to keep type legible, but it was paying for that with clipped layouts.
 * 2. **Nothing scaled with it anyway.** `MenuTopBar` was a hard 74dp with 26sp type and a 180x48
 *    logo on every device: a fifth of a landscape phone's height, and a sliver on an iPad.
 *
 * ## What this does instead
 *
 * [scale] is `min(height / 720, width / 1280)` - the reference desktop/phone box - so the *smaller*
 * axis is what limits, and content can never be scaled past the room it actually has. At the
 * reference 1560x720 it comes out at exactly 1.0, so the desktop and reference-phone look is
 * unchanged, which matters because these screens have been through many rounds of the owner's own
 * feedback and this is not a redesign.
 *
 * The remaining legibility floor is [MIN_SCALE] = 0.62 rather than 0.75. Where that still is not
 * enough room - a landscape phone cannot show twelve mission cards at once at any legible size -
 * the screens trim what they show rather than shrink further: see [isShort], which collapses
 * decorative chrome (the chapter row, oversized headers, third description lines) instead of
 * squeezing the type.
 */
@Immutable
data class MenuMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    val scale: Float,
) {
    /**
     * A screen too short to spend height on decoration - every phone in landscape (390-480dp) and
     * nothing else. Tablets clear this comfortably (an iPad is 744dp+ on its short side).
     */
    val isShort: Boolean get() = heightDp < 520.dp

    /** A narrow screen where side gutters have to give way to content. */
    val isNarrow: Boolean get() = widthDp < 700.dp

    /**
     * Tablet-shaped: 600dp is Android's own `sw600dp` break point and separates every iPhone
     * (320-440dp on the short side in landscape) from every iPad (744dp+).
     */
    val isTablet: Boolean get() = min(widthDp.value, heightDp.value) >= 600f

    /**
     * Whether the top bar should carry the wordmark next to the back button.
     *
     * Off on a phone: at the scale a 390dp-tall screen gets, the 180x48 logo renders about
     * 112x30, small enough that its two lines of distressed lettering collide into a smudge -
     * and the screen title is centred right next to it saying the same thing in one word.
     */
    val showsTopBarLogo: Boolean get() = !isNarrow && !isShort

    /** Horizontal page gutter: generous on a tablet, minimal on a narrow phone. */
    val gutter: Dp get() = when {
        isNarrow -> 16.dp
        isTablet -> (40 * scale).dp
        else -> (30 * scale).dp
    }

    /** `x.scaled` reads better than `(x * scale).dp` at the fifty-odd call sites that need it. */
    val Int.scaled: Dp get() = (this * scale).dp
    val Double.scaled: Dp get() = (this * scale).dp

    companion object {
        const val REFERENCE_WIDTH = 1280f
        const val REFERENCE_HEIGHT = 720f

        /**
         * Floor and ceiling. The floor is where menu type stops being comfortably legible (body
         * copy is 11-12sp before scaling, so 0.62 puts it at ~7sp - small, but this is a game HUD
         * aesthetic and it matches what the 0.75 floor was already shipping at a smaller effective
         * size once boxes started clipping). The ceiling stops a 12.9" iPad from rendering a menu
         * built for a phone at cartoon size.
         */
        const val MIN_SCALE = 0.62f
        const val MAX_SCALE = 1.45f

        /**
         * The main menu's floor, and it has to be lower than everyone else's: its four stacked
         * 84dp buttons plus the 158dp logo are the tallest fixed content in the app, and at 0.62
         * they add up to ~396dp against a landscape phone's ~390 - SETTINGS falls off the bottom
         * (measured, not estimated: 0.62 was tried first and does exactly that). 0.55 fits with
         * ~34dp to spare and still leaves the buttons at 46dp, on the 44dp touch minimum.
         */
        const val MAIN_MENU_MIN_SCALE = 0.55f

        /** The scale for a box of this size. Pure function - unit-tested in ResponsiveTest. */
        fun scaleFor(widthDp: Float, heightDp: Float, minScale: Float = MIN_SCALE): Float {
            if (widthDp <= 0f || heightDp <= 0f) return 1f
            val byHeight = heightDp / REFERENCE_HEIGHT
            val byWidth = widthDp / REFERENCE_WIDTH
            return min(byHeight, byWidth).coerceIn(minScale, MAX_SCALE)
        }
    }
}

/** Builds the metrics for a `BoxWithConstraints`' own `maxWidth`/`maxHeight`. */
fun menuMetrics(
    maxWidth: Dp,
    maxHeight: Dp,
    minScale: Float = MenuMetrics.MIN_SCALE,
): MenuMetrics = MenuMetrics(
    widthDp = maxWidth,
    heightDp = maxHeight,
    scale = MenuMetrics.scaleFor(maxWidth.value, maxHeight.value, minScale),
)

/**
 * Horizontal padding that keeps content clear of a landscape notch / Dynamic Island.
 *
 * Deliberately NOT `WindowInsets.safeDrawing`: these screens are drawn edge to edge under a
 * hidden status bar on Android and a full-screen window on iOS, and the only inset that actually
 * matters for them is the side one in landscape. Taking it from the same
 * `game.model.DeviceScreen` the gameplay HUD uses keeps one mechanism for both halves of the app
 * and keeps this file free of a Compose insets API whose iOS behaviour cannot be checked on this
 * machine. Zero until a host publishes, which is exactly the behaviour these screens had before.
 */
fun safeAreaPadding(): PaddingValues {
    val safe = game.model.DeviceScreen.metrics?.safeArea ?: return PaddingValues(0.dp)
    return PaddingValues(
        start = safe.left.dp,
        top = safe.top.dp,
        end = safe.right.dp,
        bottom = safe.bottom.dp,
    )
}
