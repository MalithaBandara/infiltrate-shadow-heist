package com.infiltrate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.logo_main
import kotlin.math.max
import kotlin.math.min

/**
 * The cold-launch intro: `intro.mp4` (bundled per platform like `bg1080p.mp4` - `res/raw` on
 * Android, `ios-shell/Resources` on iOS), then a cut to the logo, then the main menu. A tap
 * anywhere at any point goes straight to the menu.
 *
 * Plays once per process. [NavigationRoot] is created again every time the host brings the menu
 * back after gameplay, so "has the intro played" cannot live in its `remember` - it lives here.
 */
object IntroState {
    var played = false
}

/**
 * Plays [videoName] once with sound, reports the playback position through [onProgress] (in
 * seconds, about once a frame) and calls [onFinished] at the end - or straight away when the
 * video is missing or fails, so a broken asset costs the intro, never the menu.
 *
 * The caller sizes and places it through [modifier]; the actual draws the video stretched to that
 * box, which [introVideoBoxFor] always gives the video's own aspect.
 */
@Composable
expect fun IntroVideoPlayer(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    volume: Float,
    onProgress: (seconds: Double) -> Unit,
    onFinished: () -> Unit
)

/**
 * The part of one shot that has to stay on screen, as fractions of the 1920x1080 frame, from
 * [start] seconds until the next shot's start.
 */
data class IntroShot(
    val start: Double,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * The shots of `intro.mp4`, measured off gridded stills of every scene (2026-10-27).
 *
 * Each rect is the subject plus the context the shot needs, and is kept to at most ~0.75 of the
 * frame wide and ~0.8 tall where the shot allows. Those two numbers are what lets the video still
 * fill the screen with no black at all on the screens this game ships on: a 4:3 iPad sees 0.75
 * of the width when the video covers it, and a 19.5:9 phone sees 0.82 of the height. Anything
 * squarer or wider than that gets black bars on the short sides rather than losing the subject.
 *
 * Changes of crop are timed to the hard cuts (10.03, 15.27) or to the black between shots, so the
 * frame never visibly jumps mid-shot.
 */
val INTRO_SHOTS = listOf(
    // Briefing room: the lamp at the top, the standing man and the table centred, the two seated
    // men at x 0.12 and 0.88. The lamp's rim and the chair legs can go on a phone.
    IntroShot(0.0, 0.13f, 0.12f, 0.87f, 0.92f),
    // Blueprint close-up, morphing (11.4) into the compound at night: lamps from x 0.12, the
    // buildings and fence to 0.85.
    IntroShot(10.03, 0.11f, 0.15f, 0.86f, 0.88f),
    // Two men at x 0.14..0.36 watching the compound to their right, then the door-slit wipe.
    IntroShot(15.27, 0.12f, 0.15f, 0.87f, 0.95f),
    // Machine room (out of the black at 17.2): the men at the left, the machine at 0.39..0.86,
    // through the flash.
    IntroShot(17.1, 0.10f, 0.12f, 0.85f, 0.93f),
    // After the flash: one man at x 0.1..0.2, the glowing machine and its floor light to 0.9.
    IntroShot(30.0, 0.09f, 0.14f, 0.84f, 0.96f),
    // "FIVE YEARS LATER" on black, centred.
    IntroShot(40.5, 0.25f, 0.40f, 0.75f, 0.65f),
    // A man at a desk with a laptop and a lamp, x 0.15..0.72.
    IntroShot(42.8, 0.14f, 0.22f, 0.74f, 0.95f),
    // The port: ship and containers across the whole width, all of it in the lower 70%.
    IntroShot(53.0, 0.12f, 0.30f, 0.88f, 1.00f)
)

/** Index into [INTRO_SHOTS] of the shot playing at [seconds]. */
fun introShotAt(seconds: Double): Int {
    var index = 0
    for (i in INTRO_SHOTS.indices) {
        if (seconds >= INTRO_SHOTS[i].start) index = i
    }
    return index
}

/** The video's size on screen and the offset of its top-left corner from the screen's. */
data class IntroVideoBox(val width: Dp, val height: Dp, val offsetX: Dp, val offsetY: Dp)

/**
 * Where the intro video goes for one [shot]: as large as covering the screen, but never so large
 * that the shot's safe rect is cut, and placed with the safe rect as near the centre as the frame
 * allows. Unit-tested in ResponsiveTest.
 *
 * - **Size.** The height that covers the screen, capped by the height that still fits the safe
 *   rect. When the cap wins, the video is smaller than the screen on one axis and that axis shows
 *   black.
 * - **Position, per axis.** Where the video is larger than the screen, centre the safe rect and
 *   clamp so the frame's edge never comes inside the screen's - no black on an axis that has
 *   video to spare. Where it is smaller, centre the video itself so the bars are even.
 *
 * Laid out with the same three rules as [videoBoxFor]: requiredWidth/requiredHeight,
 * `wrapContentSize(Alignment.TopStart, unbounded = true)` plus offset on the child, and a clip.
 */
fun introVideoBoxFor(
    screenWidth: Dp,
    screenHeight: Dp,
    shot: IntroShot,
    videoAspect: Float = 16f / 9f
): IntroVideoBox {
    val sw = screenWidth.value
    val sh = screenHeight.value
    if (sw <= 0f || sh <= 0f || videoAspect <= 0f) {
        return IntroVideoBox(screenWidth, screenHeight, 0.dp, 0.dp)
    }
    val safeW = (shot.right - shot.left).coerceAtLeast(0.01f)
    val safeH = (shot.bottom - shot.top).coerceAtLeast(0.01f)
    val coverHeight = max(sh, sw / videoAspect)
    val fitHeight = min(sh / safeH, sw / (safeW * videoAspect))
    val vh = min(coverHeight, fitHeight)
    val vw = vh * videoAspect

    fun place(screen: Float, video: Float, lo: Float, hi: Float): Float =
        if (video >= screen) {
            (screen / 2f - (lo + hi) / 2f * video).coerceIn(screen - video, 0f)
        } else {
            (screen - video) / 2f
        }

    return IntroVideoBox(
        width = vw.dp,
        height = vh.dp,
        offsetX = place(sw, vw, shot.left, shot.right).dp,
        offsetY = place(sh, vh, shot.top, shot.bottom).dp
    )
}

/** How long the logo card holds before the menu. */
private const val INTRO_LOGO_HOLD_MS = 2200L

/**
 * The intro on black: the video, cropped per shot, then the logo, then [onDone]. A tap anywhere
 * calls [onDone] at once.
 */
@Composable
fun IntroSequence(volume: Float, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    var showLogo by remember { mutableStateOf(false) }
    var shotIndex by remember { mutableStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { done() }
            )
    ) {
        if (!showLogo) {
            val box = introVideoBoxFor(maxWidth, maxHeight, INTRO_SHOTS[shotIndex])
            // Placement on the child, not this box - see videoBoxFor's notes on why a Box cannot
            // align a child larger than itself.
            Box(modifier = Modifier.fillMaxSize()) {
                IntroVideoPlayer(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset(x = box.offsetX, y = box.offsetY)
                        .requiredWidth(box.width)
                        .requiredHeight(box.height),
                    videoName = "intro",
                    videoExtension = "mp4",
                    volume = volume,
                    onProgress = { seconds ->
                        val i = introShotAt(seconds)
                        if (i != shotIndex) shotIndex = i
                    },
                    onFinished = { showLogo = true }
                )
            }
        } else {
            // A hard cut to the logo, as asked, then a hard cut to the menu.
            LaunchedEffect(Unit) {
                delay(INTRO_LOGO_HOLD_MS)
                done()
            }
            // logo_main.png is 3:1. Sized off both sides so it reads the same on a phone and a
            // tablet, and never wider than 60% of the screen or taller than a third of it.
            val logoWidth = min(maxWidth.value * 0.6f, maxHeight.value).dp
            Image(
                painter = painterResource(Res.drawable.logo_main),
                contentDescription = "INFILTRATE: SHADOW HEIST",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredWidth(logoWidth)
                    .requiredHeight(logoWidth / 3f)
            )
        }
    }
}
