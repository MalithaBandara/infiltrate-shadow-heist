package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import org.jetbrains.compose.resources.DrawableResource

@Composable
expect fun LoopingVideoBackground(
    modifier: Modifier = Modifier,
    videoName: String = "bg1080p",
    videoExtension: String = "mp4",
    fallbackDrawable: DrawableResource
)

/**
 * Where to put the video surface: a size in the screen's own dp, which may be wider than the
 * screen, and the x of its left edge relative to the screen's (negative when it overflows).
 */
data class VideoBox(val width: Dp, val height: Dp, val offsetX: Dp)

/**
 * How much of the frame's trailing edge is scenery rather than subject.
 *
 * Measured off `bg1080p.mp4`: the silhouette's head and torso span 0.78..0.87 of the frame width,
 * and everything past that is the rooftop mast and satellite dish. Pinning the frame's own right
 * edge to the screen would spend that whole strip of scenery on screen and pay for it by cutting
 * more off the left - the owner's note was "the whole right side doesn't have to be there, it's
 * just the person that should be". So the trailing 9% is allowed to run off the right when the
 * screen is square enough to need the room, which leaves the silhouette about 4% of the frame
 * width clear of the screen edge.
 */
private const val SUBJECT_TRAILING_EDGE = 0.91f

/**
 * Where the 16:9 menu video sits on a screen that is not 16:9. Shared by all three hosts so the
 * menu background cannot drift between them - unit-tested in ResponsiveTest.
 *
 * The surface always fills the height and keeps the video's own aspect. What changes with the
 * screen is where it sits horizontally:
 *
 * - On a screen **wider** than 16:9 (desktop, a 19.5:9 phone) the surface is narrower than the
 *   screen, and it sits flush against the trailing edge, leaving a band on the leading side. That
 *   band is not visible: the main menu lays a dark horizontal gradient over that whole side to
 *   give the logo and the button stack their contrast, and the band sits under its opaque end.
 * - On a screen **squarer** than 16:9 (every iPad, an unfolded foldable) the surface is wider than
 *   the screen and has to overflow. It overflows the leading edge first, because that is the half
 *   the menu covers anyway, and only spills past the trailing edge once the leading overflow has
 *   eaten the [SUBJECT_TRAILING_EDGE] scenery strip - so the silhouette never reaches the screen
 *   edge, whatever the aspect.
 *
 * That second case is the fix. Fitting the width instead, as this did originally, left the bottom
 * fifth of a 4:3 iPad as a black bar under the video with the menu drawn over it.
 *
 * Three things the caller has to get right, all of them ways Compose quietly refuses to overflow:
 *
 * 1. Size with `requiredWidth`/`requiredHeight`. The plain `width`/`height` modifiers clamp to the
 *    incoming constraints and would silently fit instead of overflow.
 * 2. Position the child with `wrapContentSize(Alignment.TopStart, unbounded = true)` plus
 *    `offset(x = box.offsetX)`, never with the parent's `contentAlignment`. A `Box` measures
 *    itself as `max(minConstraint, childSize)`, so a child wider than the box makes the **box**
 *    that wide too - and a box exactly as wide as its child has nothing left to align. The
 *    oversized size then propagates up through `fillMaxSize()` (which reports its child's size,
 *    not the constraint) to the menu root, and the video ends up wherever the window happens to
 *    place that oversized root: centred, with the silhouette sliced off by the screen edge. That
 *    is exactly the bug the owner hit on a 7:6 foldable. `wrapContentSize` pins the wrapper back
 *    to the incoming constraints and places the overflowing child inside it.
 * 3. Clip, so the overflow cannot paint outside the background's bounds.
 */
fun videoBoxFor(screenWidth: Dp, screenHeight: Dp, videoAspect: Float = 16f / 9f): VideoBox {
    if (screenHeight.value <= 0f || videoAspect <= 0f) {
        return VideoBox(screenWidth, screenHeight, 0.dp)
    }
    val width = screenHeight.value * videoAspect
    val overflow = (width - screenWidth.value).coerceAtLeast(0f)
    // Spend the leading edge first; only let the trailing scenery strip go once it has run out.
    val trailingOverhang = min((1f - SUBJECT_TRAILING_EDGE) * width, overflow)
    return VideoBox(
        width = width.dp,
        height = screenHeight,
        offsetX = (screenWidth.value - width + trailingOverhang).dp,
    )
}
