package test

import game.model.DeviceScreen
import game.model.SafeAreaInsets
import game.model.ScreenLayout
import game.model.ScreenMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The viewport contract, pinned against the real devices this game has to cover.
 *
 * The one rule every case below is really checking: whatever canvas a device gets, the authored
 * 1040x480 design rect fits inside it, and the canvas has the device's own aspect so nothing is
 * letterboxed. See `ScreenLayout`'s doc comment for why that rule and not another.
 */
class ScreenLayoutTest {

    /** Landscape sizes in dp/points for the devices the store listing targets. */
    private val devices = listOf(
        // label to (width, height)
        "iPhone 15 (2.17)" to (852.0 to 393.0),
        "Galaxy S25 Ultra reference (2.17)" to (1040.0 to 480.0),
        "iPhone SE / 16:9 phone" to (667.0 to 375.0),
        "21:9 phone" to (932.0 to 400.0),
        "iPad 10.9 (1.44)" to (1180.0 to 820.0),
        "iPad Pro 12.9 (1.33)" to (1366.0 to 1024.0),
        "iPad mini (1.5)" to (1133.0 to 744.0),
        "16:10 Android tablet" to (1280.0 to 800.0),
    )

    @Test
    fun testDesignRectAlwaysFitsAndAspectAlwaysMatchesTheDevice() {
        for ((label, size) in devices) {
            val (w, h) = size
            val vp = ScreenLayout.viewportFor(w, h)

            assertTrue(
                vp.width >= ScreenLayout.DESIGN_WIDTH - 0.5,
                "$label: canvas ${vp.width} is narrower than the authored ${ScreenLayout.DESIGN_WIDTH} - " +
                    "levels would lose horizontal field of view"
            )
            assertTrue(
                vp.height >= ScreenLayout.DESIGN_HEIGHT - 0.5,
                "$label: canvas ${vp.height} is shorter than the authored ${ScreenLayout.DESIGN_HEIGHT}"
            )
            // Sharing the device aspect is what leaves SHOW_ALL nothing to letterbox. Rounding to
            // whole units costs at most half a unit on each axis.
            val deviceAspect = w / h
            assertEquals(
                deviceAspect, vp.aspect, 0.004,
                "$label: canvas aspect ${vp.aspect} does not match the device's $deviceAspect - it would be letterboxed"
            )
        }
    }

    @Test
    fun testReferencePhoneStillGetsTheExactAuthoredCanvas() {
        // The whole game was tuned against this one. It must come out bit-for-bit unchanged, or
        // every HUD inset and camera constant in GameplayScene is being re-tuned by accident.
        val vp = ScreenLayout.viewportFor(1040.0, 480.0)
        assertEquals(1040.0, vp.width, 0.001)
        assertEquals(480.0, vp.height, 0.001)
    }

    @Test
    fun testWideScreensKeepTheAuthoredHeightAndSquareOnesKeepTheAuthoredWidth() {
        // 21:9 held sideways: taller than the design aspect, so height is pinned and width grows.
        val wide = ScreenLayout.viewportFor(932.0, 400.0)
        assertEquals(ScreenLayout.DESIGN_HEIGHT, wide.height, 0.001, "a wide screen must not change the vertical framing")
        assertTrue(wide.width > ScreenLayout.DESIGN_WIDTH, "a wide screen should see a little more level width")

        // 4:3 iPad: squarer than the design aspect, so width is pinned and the extra goes to sky.
        val tablet = ScreenLayout.viewportFor(1366.0, 1024.0)
        assertEquals(ScreenLayout.DESIGN_WIDTH, tablet.width, 0.001, "a tablet must see exactly the phone's level width")
        assertEquals(780.0, tablet.height, 0.5, "4:3 works out to 1040x780")
        assertTrue(tablet.height > ScreenLayout.DESIGN_HEIGHT)
    }

    @Test
    fun testOrientationOfTheInputDoesNotMatter() {
        // iOS reports portrait-shaped bounds for the first moments of launch, before rotation
        // settles. Both hosts lock landscape, so the long side is the width either way.
        val asReported = ScreenLayout.viewportFor(393.0, 852.0)
        val settled = ScreenLayout.viewportFor(852.0, 393.0)
        assertEquals(settled.width, asReported.width, 0.001)
        assertEquals(settled.height, asReported.height, 0.001)
    }

    @Test
    fun testDegenerateAndPortraitInputsStayUsable() {
        // Nothing measured yet.
        assertEquals(ScreenLayout.DESIGN_WIDTH, ScreenLayout.viewportFor(0.0, 0.0).width, 0.001)
        assertEquals(ScreenLayout.DESIGN_HEIGHT, ScreenLayout.viewportFor(0.0, 0.0).height, 0.001)

        // A genuinely portrait window (Android 16 can ignore the orientation lock on a large
        // screen): a very tall canvas, still at least the design rect, never inverted.
        val portrait = ScreenLayout.viewportFor(800.0, 1280.0)
        assertTrue(portrait.width >= ScreenLayout.DESIGN_WIDTH - 0.5)
        assertTrue(portrait.height >= ScreenLayout.DESIGN_HEIGHT - 0.5)
    }

    @Test
    fun testSafeInsetsConvertFromDeviceUnitsIntoSceneUnits() {
        // iPhone 15 landscape: the Dynamic Island eats 59pt off the leading edge and the home
        // indicator 21pt off the bottom.
        val metrics = ScreenMetrics(
            widthDp = 852.0,
            heightDp = 393.0,
            safeArea = SafeAreaInsets(left = 59.0, top = 0.0, right = 0.0, bottom = 21.0),
        )
        val vp = ScreenLayout.viewportFor(metrics)
        val insets = ScreenLayout.safeInsetsInVirtualUnits(metrics)

        val unitsPerDp = vp.width / 852.0
        assertEquals(59.0 * unitsPerDp, insets.left, 0.01)
        assertEquals(21.0 * unitsPerDp, insets.bottom, 0.01)
        assertEquals(0.0, insets.right, 0.001)

        // The notch is worth more scene units than the old hard-coded 46-unit edge inset, which
        // is the bug this plumbing exists to fix: the left D-pad sat partly under it.
        assertTrue(insets.left > 46.0, "the notch really is wider than the old fixed inset (${insets.left})")
    }

    @Test
    fun testTabletDetectionUsesTheShortSide() {
        assertTrue(ScreenMetrics(1366.0, 1024.0).isTablet)
        assertTrue(ScreenMetrics(1133.0, 744.0).isTablet, "iPad mini is a tablet")
        assertTrue(!ScreenMetrics(1040.0, 480.0).isTablet, "the biggest phone is not")
        assertTrue(!ScreenMetrics(932.0, 430.0).isTablet)
    }

    @Test
    fun testDeviceScreenFallsBackToTheDesignCanvasWhenNoHostHasPublished() {
        val saved = DeviceScreen.metrics
        try {
            DeviceScreen.metrics = null
            assertEquals(ScreenLayout.DESIGN_WIDTH, DeviceScreen.viewport.width, 0.001)
            assertEquals(ScreenLayout.DESIGN_HEIGHT, DeviceScreen.viewport.height, 0.001)
            assertTrue(DeviceScreen.safeInsets.isEmpty)
            assertTrue(!DeviceScreen.isTablet)

            DeviceScreen.publish(1366.0, 1024.0, safeBottomDp = 20.0)
            assertEquals(780.0, DeviceScreen.viewport.height, 0.5)
            assertTrue(DeviceScreen.isTablet)
            assertTrue(DeviceScreen.safeInsets.bottom > 0.0)

            // Size published first, insets later - the iOS ordering.
            DeviceScreen.publishSafeArea(59.0, 0.0, 0.0, 21.0)
            assertEquals(1366.0, DeviceScreen.metrics!!.widthDp, 0.001)
            assertTrue(DeviceScreen.safeInsets.left > 0.0)
        } finally {
            DeviceScreen.metrics = saved
        }
    }
}
