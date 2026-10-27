package com.infiltrate.test

import androidx.compose.ui.unit.dp
import com.infiltrate.ui.INTRO_SHOTS
import com.infiltrate.ui.IntroShot
import com.infiltrate.ui.IntroVideoBox
import com.infiltrate.ui.introShotAt
import com.infiltrate.ui.introVideoBoxFor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The intro's per-shot crop (ui/IntroVideo.kt): every shot's subject stays on screen on every
 * shape of screen, and black only shows where keeping the subject needs it.
 */
class IntroVideoTest {

    private val eps = 0.01f

    /** Landscape phones and tablets this ships on, plus the odd shapes a windowed iPad can take. */
    private val screens = listOf(
        "16:9 phone" to (640f to 360f),
        "19.5:9 phone" to (844f to 390f),
        "20:9 phone" to (915f to 412f),
        "21:9 phone" to (960f to 411f),
        "4:3 iPad" to (1024f to 768f),
        "iPad Pro 11" to (1194f to 834f),
        "16:10 tablet" to (1280f to 800f),
        "square window" to (700f to 700f),
        "portrait window" to (768f to 1024f),
        "7:6 foldable" to (841f to 720f),
    )

    private fun IntroVideoBox.leftOf(f: Float) = offsetX.value + f * width.value
    private fun IntroVideoBox.topOf(f: Float) = offsetY.value + f * height.value

    @Test
    fun everyShotsSafeRectIsOnScreenOnEveryScreen() {
        for ((name, size) in screens) {
            val (w, h) = size
            for (shot in INTRO_SHOTS) {
                val box = introVideoBoxFor(w.dp, h.dp, shot)
                assertEquals(16f / 9f, box.width.value / box.height.value, 0.001f, "$name: aspect kept")
                assertTrue(box.leftOf(shot.left) >= -eps, "$name @${shot.start}: left of subject cut")
                assertTrue(box.leftOf(shot.right) <= w + eps, "$name @${shot.start}: right of subject cut")
                assertTrue(box.topOf(shot.top) >= -eps, "$name @${shot.start}: top of subject cut")
                assertTrue(box.topOf(shot.bottom) <= h + eps, "$name @${shot.start}: bottom of subject cut")
            }
        }
    }

    @Test
    fun noBlackOnAnAxisTheVideoCovers() {
        for ((name, size) in screens) {
            val (w, h) = size
            for (shot in INTRO_SHOTS) {
                val box = introVideoBoxFor(w.dp, h.dp, shot)
                if (box.width.value >= w) {
                    assertTrue(box.offsetX.value <= eps && box.offsetX.value + box.width.value >= w - eps, "$name @${shot.start}: side bar")
                } else {
                    // Narrower than the screen: even bars either side.
                    assertEquals((w - box.width.value) / 2f, box.offsetX.value, eps, "$name @${shot.start}")
                }
                if (box.height.value >= h) {
                    assertTrue(box.offsetY.value <= eps && box.offsetY.value + box.height.value >= h - eps, "$name @${shot.start}: top/bottom bar")
                } else {
                    assertEquals((h - box.height.value) / 2f, box.offsetY.value, eps, "$name @${shot.start}")
                }
            }
        }
    }

    @Test
    fun theScreensThisShipsOnAreFilledEdgeToEdgeForTheFullFrameShots() {
        // A 16:9 screen shows the whole frame, nothing cropped and nothing black.
        for (shot in INTRO_SHOTS) {
            val box = introVideoBoxFor(640.dp, 360.dp, shot)
            assertEquals(640f, box.width.value, eps)
            assertEquals(0f, box.offsetX.value, eps)
            assertEquals(0f, box.offsetY.value, eps)
        }
        // A 4:3 iPad and a 19.5:9 phone: every shot fills the screen, or comes within 4% of it.
        for ((w, h) in listOf(1024f to 768f, 844f to 390f)) {
            for (shot in INTRO_SHOTS) {
                val box = introVideoBoxFor(w.dp, h.dp, shot)
                val coverage = minOf(box.width.value / w, 1f) * minOf(box.height.value / h, 1f)
                assertTrue(coverage >= 0.96f, "${w}x$h @${shot.start}: only $coverage of the screen is video")
            }
        }
    }

    @Test
    fun aSquareScreenCropsTowardTheSubjectNotTheFrameCentre() {
        // The desk shot's subject sits left of centre (x 0.14..0.74); a square screen must slide
        // the frame to it rather than centring the frame and cutting the man at the desk.
        val desk = INTRO_SHOTS[introShotAt(46.0)]
        val box = introVideoBoxFor(700.dp, 700.dp, desk)
        val subjectCentre = (box.leftOf(desk.left) + box.leftOf(desk.right)) / 2f
        assertTrue(abs(subjectCentre - 350f) < 1f, "subject centred, was $subjectCentre")
    }

    @Test
    fun shotsSwitchAtTheirStartTimes() {
        assertEquals(0, introShotAt(0.0))
        assertEquals(0, introShotAt(10.0))
        assertEquals(1, introShotAt(10.03))
        assertEquals(1, introShotAt(15.2))
        assertEquals(2, introShotAt(15.27))
        assertEquals(INTRO_SHOTS.lastIndex, introShotAt(59.5))
        assertTrue(INTRO_SHOTS.zipWithNext().all { (a, b) -> a.start < b.start })
        assertTrue(INTRO_SHOTS.all { it.left < it.right && it.top < it.bottom && it.left >= 0f && it.right <= 1f && it.top >= 0f && it.bottom <= 1f })
    }

    @Test
    fun degenerateScreenDoesNotCrash() {
        val box = introVideoBoxFor(0.dp, 0.dp, IntroShot(0.0, 0f, 0f, 1f, 1f))
        assertEquals(0f, box.width.value)
    }
}
