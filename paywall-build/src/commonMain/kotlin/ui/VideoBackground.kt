package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.DrawableResource

@Composable
expect fun LoopingVideoBackground(
    modifier: Modifier = Modifier,
    videoName: String = "bg1080p",
    videoExtension: String = "mp4",
    fallbackDrawable: DrawableResource
)

/** The size to give the video surface, in the screen's own dp. May be wider than the screen. */
data class VideoBox(val width: Dp, val height: Dp)

/**
 * Where the 16:9 menu video sits on a screen that is not 16:9. Shared by all three hosts so the
 * menu background cannot drift between them - unit-tested in ResponsiveTest.
 *
 * The surface always fills the height and keeps the video's own aspect, and the caller pins it to
 * the trailing edge. Two things follow from that:
 *
 * - On a screen **wider** than 16:9 (desktop, a 19.5:9 phone) the surface is narrower than the
 *   screen and leaves a band on the left. That band is not visible: the main menu lays a dark
 *   horizontal gradient over that whole side to give the logo and the button stack their
 *   contrast, and the band sits under its opaque end.
 * - On a screen **squarer** than 16:9 (every iPad) the surface is wider than the screen and the
 *   overflow runs off the LEFT, so the right-hand side of the frame - where the composition's
 *   subject is - stays on screen at full height.
 *
 * The second case is the fix. Fitting the width instead, as this did before, left the bottom
 * fifth of a 4:3 iPad as a black bar under the video with the menu drawn over it.
 *
 * The caller must size with `requiredWidth`/`requiredHeight` (the plain `width`/`height`
 * modifiers clamp to the incoming constraints and would silently fit instead of overflow) and
 * clip, so the overflow cannot paint outside the background's bounds.
 */
fun videoBoxFor(screenWidth: Dp, screenHeight: Dp, videoAspect: Float = 16f / 9f): VideoBox {
    if (screenHeight.value <= 0f || videoAspect <= 0f) {
        return VideoBox(screenWidth, screenHeight)
    }
    return VideoBox(width = screenHeight * videoAspect, height = screenHeight)
}
