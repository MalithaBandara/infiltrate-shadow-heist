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
 * Two rules, and every case below is checking one of them. **Always:** the canvas has the device's
 * own aspect, so SHOW_ALL has nothing left to letterbox, and it is never shorter than the authored
 * 480. **On anything a phone can be** (16:9 and wider) it is also never narrower than the authored
 * 1040, so no phone loses horizontal field of view. Tablets and foldables trade some of that width
 * for magnification under the zoom cap - see `ScreenLayout`'s doc comment for why.
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

            // Phones keep the authored width exactly; tablets may give some of it back to the
            // zoom cap, but never below the floor GameplayScene pins its HUD against.
            val floor = if (w / h >= ScreenLayout.FULL_WIDTH_ASPECT) {
                ScreenLayout.DESIGN_WIDTH
            } else {
                ScreenLayout.MIN_CANVAS_WIDTH
            }
            assertTrue(
                vp.width >= floor - 0.5,
                "$label: canvas ${vp.width} is narrower than $floor - levels would lose too much " +
                    "horizontal field of view"
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

        // 16:9, the squarest a phone gets: width is pinned and the extra goes to sky.
        val phone = ScreenLayout.viewportFor(667.0, 375.0)
        assertEquals(ScreenLayout.DESIGN_WIDTH, phone.width, 0.5, "a phone must see exactly the reference width")
        assertTrue(phone.height > ScreenLayout.DESIGN_HEIGHT, "a 16:9 phone gets some sky above the action")
    }

    @Test
    fun testTabletsZoomInInsteadOfStackingSkyAboveTheAction() {
        // The owner's report from their own iPad: 1040x780 put the action in the bottom 62% of the
        // screen with empty sky above it. The cap trades width for magnification instead.
        val ipad = ScreenLayout.viewportFor(1366.0, 1024.0)
        // 4:3 is square enough that the MIN_CANVAS_WIDTH floor, not the height cap, decides:
        // the canvas stops at exactly the 800 units GameplayScene pins its HUD against.
        assertEquals(600.0, ipad.height, 0.5, "4:3 must stop well short of the old 780")
        assertEquals(ScreenLayout.MIN_CANVAS_WIDTH, ipad.width, 0.5, "and the width gives instead")
        assertTrue(
            ScreenLayout.DESIGN_HEIGHT / ipad.height >= 0.79,
            "the action should fill most of the canvas height, was ${ScreenLayout.DESIGN_HEIGHT / ipad.height}"
        )

        // The cap is a tablet concession only: no phone aspect may lose any width to it.
        for (aspect in listOf(2.33, 2.22, 2.167, 2.0, 16.0 / 9.0)) {
            val vp = ScreenLayout.viewportFor(aspect * 400.0, 400.0)
            assertTrue(
                vp.width >= ScreenLayout.DESIGN_WIDTH - 0.5,
                "aspect $aspect is a phone and must keep the full ${ScreenLayout.DESIGN_WIDTH}, got ${vp.width}"
            )
        }

        // Right at the break point the two branches have to agree, or the canvas jumps.
        val atBreak = ScreenLayout.viewportFor(ScreenLayout.FULL_WIDTH_ASPECT * 500.0, 500.0)
        assertEquals(ScreenLayout.DESIGN_WIDTH, atBreak.width, 1.0)
        assertEquals(ScreenLayout.MAX_CANVAS_HEIGHT, atBreak.height, 1.0)

        // The squarest real device, a Z Fold 6 unfolded, hits the width floor rather than the
        // height cap - it must not zoom in past what the HUD can survive.
        val fold = ScreenLayout.viewportFor(823.0, 707.0)
        assertEquals(ScreenLayout.MIN_CANVAS_WIDTH, fold.width, 0.5)
        assertTrue(fold.height > ScreenLayout.MAX_CANVAS_HEIGHT)
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
        assertTrue(portrait.width >= ScreenLayout.MIN_CANVAS_WIDTH - 0.5)
        assertTrue(portrait.height >= ScreenLayout.DESIGN_HEIGHT - 0.5)

        // A truly square window clamps on width, not height, and stays the right way up.
        val square = ScreenLayout.viewportFor(900.0, 900.0)
        assertEquals(ScreenLayout.MIN_CANVAS_WIDTH, square.width, 0.5)
        assertEquals(ScreenLayout.MIN_CANVAS_WIDTH, square.height, 0.5)
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
            assertEquals(600.0, DeviceScreen.viewport.height, 0.5)
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
