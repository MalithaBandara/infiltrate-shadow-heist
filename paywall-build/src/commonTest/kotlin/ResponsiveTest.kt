package com.infiltrate.test

import com.infiltrate.ui.MenuMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The menu scale, pinned against the devices it has to cover. `MenuMetrics.scaleFor` is a pure
 * function of the box Compose hands the screen, so it is testable without a UI harness (there
 * isn't one here) - which is the whole reason it lives on the companion rather than inline in a
 * `BoxWithConstraints`.
 */
class ResponsiveTest {

    @Test
    fun testReferenceDesktopBoxIsUnchangedAtScaleOne() {
        // The desktop window and the reference phone. These screens have been through many rounds
        // of the owner's own feedback at this size; the responsive work must not move them.
        assertEquals(1.0f, MenuMetrics.scaleFor(1560f, 720f), 0.001f)
        assertEquals(1.0f, MenuMetrics.scaleFor(1280f, 720f), 0.001f)
    }

    @Test
    fun testLandscapePhoneNoLongerGetsInflatedPastTheRoomItHas() {
        // 844x390 (iPhone 15 held sideways). The old rule was (390/720).coerceIn(0.75, 1.4) =
        // 0.75 - a 39% inflation over the true ratio, which is what pushed the mission grid and
        // the store's bottom row off the screen.
        val scale = MenuMetrics.scaleFor(844f, 390f)
        assertTrue(scale < 0.75f, "must be under the old 0.75 floor, was $scale")
        assertEquals(MenuMetrics.MIN_SCALE, scale, 0.001f)
    }

    @Test
    fun testMainMenusOwnFloorIsLowerBecauseItsButtonStackIsTaller() {
        // Measured, not estimated: logo 158 + spacer 32 + four 84dp buttons + three 16dp gaps,
        // all scaled, plus 40dp of padding. At 0.62 that is ~396dp against a 390dp screen and
        // SETTINGS falls off the bottom; at 0.55 it fits with room to spare.
        val stackHeight = { s: Float -> (158 + 32 + 4 * 84 + 3 * 16) * s + 40f }
        assertTrue(stackHeight(MenuMetrics.MIN_SCALE) > 390f)
        assertTrue(stackHeight(MenuMetrics.MAIN_MENU_MIN_SCALE) < 390f)

        val menuScale = MenuMetrics.scaleFor(844f, 390f, MenuMetrics.MAIN_MENU_MIN_SCALE)
        assertEquals(MenuMetrics.MAIN_MENU_MIN_SCALE, menuScale, 0.001f)
        // A 84dp button at that scale still clears the 44dp touch minimum.
        assertTrue(84f * menuScale >= 44f, "button height ${84f * menuScale} is under the touch minimum")
    }

    @Test
    fun testTabletsAreLimitedByWidthNotHeight() {
        // This is the case a height-only scale got wrong: a 4:3 iPad is 768dp TALL, so
        // height/720 = 1.07 and a menu laid out horizontally was scaled UP on the axis that had
        // the least room.
        val heightOnly = 768f / 720f
        val twoAxis = MenuMetrics.scaleFor(1024f, 768f)
        assertTrue(twoAxis < heightOnly, "two-axis ($twoAxis) must be under height-only ($heightOnly)")
        assertEquals(1024f / 1280f, twoAxis, 0.001f)

        // A 12.9" iPad has room on both axes and gets to grow.
        val big = MenuMetrics.scaleFor(1366f, 1024f)
        assertTrue(big > 1.0f, "a 12.9in iPad should scale up, was $big")
        assertEquals(1366f / 1280f, big, 0.001f)
    }

    @Test
    fun testScaleIsClampedAtBothEnds() {
        assertEquals(MenuMetrics.MIN_SCALE, MenuMetrics.scaleFor(400f, 200f), 0.001f)
        assertEquals(MenuMetrics.MAX_SCALE, MenuMetrics.scaleFor(4000f, 3000f), 0.001f)
        // A box with no size yet (first composition) must not produce 0 or NaN.
        assertEquals(1f, MenuMetrics.scaleFor(0f, 0f), 0.001f)
    }
}
