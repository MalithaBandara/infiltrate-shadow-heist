package game.model

import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * One place that answers "how big is the canvas on THIS device", for both halves of the app:
 * KorGE's virtual viewport (`:game`) and Compose's menu scale (`paywall-build`, which compiles
 * this same file from source - see the model-sharing rule in `.junie/guidelines.md`).
 *
 * ## The problem this replaces
 *
 * Every host used to hard-code `virtualSize = Size(480 * (1560 / 720), 480)` - a fixed 1040x480
 * canvas at the Galaxy S25 Ultra's exact landscape aspect (2.167), fitted with
 * [korlibs.korge.view.ScaleMode.SHOW_ALL]. SHOW_ALL letterboxes anything that is not that aspect,
 * so the game ran inside black bars on every device that wasn't the reference phone: ~8% of the
 * screen lost on a 16:9 phone and **38% on a 4:3 iPad**, where gameplay sat in a thin band across
 * the middle.
 *
 * ## The rule: the design rect is always fully visible, and nothing else is ever cropped
 *
 * [viewportFor] returns a virtual size that has the *device's* aspect ratio (so SHOW_ALL has
 * nothing left to letterbox) and that always **contains** the [DESIGN_WIDTH] x [DESIGN_HEIGHT]
 * rect every scene was authored against:
 *
 * - **Wider than the design** (tall-and-narrow phones held sideways, 20:9 / 21:9): height stays
 *   [DESIGN_HEIGHT] and the width grows. The player sees a little more of the level left and
 *   right; the vertical framing every level was tuned against is untouched.
 * - **Narrower than the design, down to [FULL_WIDTH_ASPECT]** (every phone: 16:9 is 1.778): width
 *   stays [DESIGN_WIDTH] and the height grows. The horizontal field of view is *identical* to the
 *   reference phone's, which matters because level pacing is tuned against it (level 6's "the
 *   crane fills the frame from the lever" and level 3's overwatch pair are both statements about
 *   how much of the level fits on screen at once). The extra height becomes sky above the action:
 *   `GameplayScene` already pins the ground near the bottom of whatever canvas it is given
 *   (`worldViewY = canvasH - (groundY + 70) * zoom`) and tiles the background to `canvasH`, so
 *   nothing new has to be drawn to fill it.
 * - **Squarer than [FULL_WIDTH_ASPECT]** (3:2, 16:10 and 4:3 tablets, unfolded foldables): the
 *   canvas stops growing at [MAX_CANVAS_HEIGHT] and the width starts to give instead. This is the
 *   zoom cap, and it is the one place the game deliberately shows less of a level than the
 *   reference phone - see below.
 *
 * ## The zoom cap, and why the "never crop" rule has an exception (2026-09-25)
 *
 * Containing the design rect at every aspect was the original rule, and the consequence was meant
 * to be reassuring: no device ever sees less of a level than the reference phone. On a 4:3 iPad it
 * read very differently. The canvas came out 1040x780, the ground is pinned near the bottom, and
 * the world draws at a fixed `worldZoom` of 1.35 - so the action occupied the bottom 62% of a big
 * expensive screen and the top 38% was empty sky. The owner's words after playing it on their own
 * iPad: "don't show too much extra sky in tablets, instead zoom in onto the game."
 *
 * There is no free lunch here. The ground has nothing below it to reveal, so the only way to make
 * the action fill more of a squarer screen is to magnify it, and magnifying it necessarily shows
 * less level width. The cap picks where to stop: the canvas grows to at most [MAX_CANVAS_HEIGHT]
 * (the height at which [DESIGN_WIDTH] exactly fills a [FULL_WIDTH_ASPECT] screen) and past that
 * the width shrinks with the aspect - until it reaches [MIN_CANVAS_WIDTH], which is where a 4:3
 * screen actually lands. So a 4:3 iPad goes from 1040x780 to a round 800x600: the player now
 * stands 21% of the canvas height tall instead of 16% (26% on the reference phone), at the cost of
 * 593 visible world units instead of 770, a 23% narrower view.
 *
 * [FULL_WIDTH_ASPECT] is 16:9 on purpose - the squarest a phone in landscape gets - so
 * **no phone loses a single unit of horizontal field of view** and the cap is a
 * tablet-and-foldable concession only, which is what keeps the level-pacing decisions above intact
 * on the devices they were reasoned out against. [MIN_CANVAS_WIDTH] is the backstop under it, so a
 * genuinely square or portrait window cannot zoom in without limit; it is the same 800 units
 * `GameplayScene` already clamps its own `canvasW` to, so the two floors cannot disagree.
 *
 * ## Landscape is assumed, defensively
 *
 * Both shipping hosts lock landscape (`UISupportedInterfaceOrientations`, `screenOrientation=
 * "landscape"`), but the size a host reads at startup is not always the settled one: iOS reports
 * `UIScreen.mainScreen.bounds` portrait-shaped for the first moments of launch before rotation
 * resolves, and Android 16 ignores orientation locks outright on large screens. [viewportFor]
 * therefore normalises its two inputs with max/min rather than trusting which one is "width", and
 * clamps the aspect to [MIN_ASPECT]..[MAX_ASPECT] so a genuinely portrait window degrades into a
 * very tall canvas (playable, HUD still pinned to the real bottom) instead of something absurd.
 */
object ScreenLayout {
    /**
     * The authored canvas. Every scene, HUD inset and overlay in `game.scene` was laid out
     * against these numbers, and on the reference device (Galaxy S25 Ultra, 3120x1440 px at
     * density 3.0 = 1040x480 dp) one virtual unit is exactly one dp.
     */
    const val DESIGN_WIDTH: Double = 1040.0
    const val DESIGN_HEIGHT: Double = 480.0

    /** The design aspect, 2.1667 - the break-even point between the two regimes above. */
    const val DESIGN_ASPECT: Double = DESIGN_WIDTH / DESIGN_HEIGHT

    /**
     * Aspect guards. The low end is below any real landscape device (4:3 is 1.333) and exists
     * only so a portrait window - which only Android 16's large-screen orientation override can
     * produce here - still yields a usable canvas. Past it, SHOW_ALL letterboxes again, which is
     * the right answer for a window that shape. The high end is past any shipping phone (21:9 is
     * 2.33).
     */
    const val MIN_ASPECT: Double = 0.75
    const val MAX_ASPECT: Double = 3.0

    /**
     * The squarest screen that still gets the whole [DESIGN_WIDTH]: exactly 16:9, which is the
     * squarest a phone in landscape gets. Setting it there rather than lower makes the zoom cap
     * below as strong as it can be while still costing no phone a single unit of horizontal field
     * of view.
     */
    const val FULL_WIDTH_ASPECT: Double = 16.0 / 9.0

    /**
     * The tallest canvas the zoom cap hands out: the height at which [DESIGN_WIDTH] exactly fills
     * a [FULL_WIDTH_ASPECT] screen, 585 units. Past that the canvas zooms in rather than adding
     * more sky above the action.
     */
    const val MAX_CANVAS_HEIGHT: Double = DESIGN_WIDTH / FULL_WIDTH_ASPECT

    /**
     * The narrowest canvas the zoom cap may produce. It binds below aspect 1.37, which is to say
     * on 4:3 tablets and anything squarer. Deliberately the same 800 units `GameplayScene` clamps
     * its own `canvasW` to - a canvas narrower than that would put the HUD it pins to `canvasW`
     * off the edge of the screen.
     */
    const val MIN_CANVAS_WIDTH: Double = 800.0

    /**
     * The virtual canvas for a screen of [screenWidthDp] x [screenHeightDp] density-independent
     * units (dp on Android, points on iOS, logical pixels on desktop). Order does not matter -
     * the larger of the two is taken as the landscape width.
     *
     * Returns whole units: KorGE stores `virtualWidth`/`virtualHeight` as `Int` and truncates,
     * and a half-unit of truncation is a half-unit of letterboxing.
     */
    fun viewportFor(screenWidthDp: Double, screenHeightDp: Double): VirtualViewport {
        val long = max(screenWidthDp, screenHeightDp)
        val short = min(screenWidthDp, screenHeightDp)
        // A host that has not measured its window yet (0, NaN, or a single pixel) gets the
        // design canvas rather than a division by zero.
        if (!long.isFinite() || !short.isFinite() || short <= 1.0 || long <= 1.0) {
            return VirtualViewport(DESIGN_WIDTH, DESIGN_HEIGHT)
        }
        val aspect = (long / short).coerceIn(MIN_ASPECT, MAX_ASPECT)
        // What containing the whole design rect would ask for on its own.
        val containHeight = max(DESIGN_HEIGHT, DESIGN_WIDTH / aspect)
        // The zoom cap: stop growing the canvas at MAX_CANVAS_HEIGHT so a squarer screen magnifies
        // the action instead of stacking sky on top of it, but never let that shrink the canvas
        // past MIN_CANVAS_WIDTH.
        val cappedHeight = max(MAX_CANVAS_HEIGHT, MIN_CANVAS_WIDTH / aspect)
        val height = min(containHeight, cappedHeight)
        return VirtualViewport(round(height * aspect), round(height))
    }

    /** [viewportFor] for a whole [ScreenMetrics] reading. */
    fun viewportFor(metrics: ScreenMetrics): VirtualViewport =
        viewportFor(metrics.widthDp, metrics.heightDp)

    /**
     * The device's safe-area insets expressed in the virtual units the scene lays out in.
     *
     * Converted as a **fraction of the screen** rather than through a units-per-dp factor,
     * deliberately: the hosts report their screen size in whatever units they have to hand (dp on
     * Android, points on iOS, logical pixels on desktop) and the canvas may have been sized from
     * a different measurement of the same screen, so a fraction is the one thing that survives
     * both. An inset that covers 7% of the screen's width covers 7% of the canvas's width, in
     * whatever units either of them is counted in.
     */
    fun safeInsetsInVirtualUnits(
        metrics: ScreenMetrics,
        viewport: VirtualViewport = viewportFor(metrics),
    ): SafeAreaInsets {
        val safe = metrics.safeArea
        if (safe.isEmpty) return SafeAreaInsets.NONE
        // Landscape-normalised the same way viewportFor is: the long side is the horizontal one.
        val screenLong = metrics.longSideDp
        val screenShort = metrics.shortSideDp
        if (!screenLong.isFinite() || !screenShort.isFinite() || screenLong <= 1.0 || screenShort <= 1.0) {
            return SafeAreaInsets.NONE
        }
        return SafeAreaInsets(
            left = safe.left / screenLong * viewport.width,
            top = safe.top / screenShort * viewport.height,
            right = safe.right / screenLong * viewport.width,
            bottom = safe.bottom / screenShort * viewport.height,
        )
    }
}

/** A virtual canvas size in scene units. */
data class VirtualViewport(val width: Double, val height: Double) {
    val aspect: Double get() = if (height > 0.0) width / height else ScreenLayout.DESIGN_ASPECT
}

/**
 * Screen area the OS keeps for itself - a notch or Dynamic Island (on a *side* in landscape), the
 * home-indicator strip, a display cutout, gesture strips. Same units as whatever produced it:
 * device dp on a [ScreenMetrics], virtual scene units once run through
 * [ScreenLayout.safeInsetsInVirtualUnits].
 */
data class SafeAreaInsets(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
) {
    val isEmpty: Boolean get() = left <= 0.0 && top <= 0.0 && right <= 0.0 && bottom <= 0.0

    companion object {
        val NONE = SafeAreaInsets()
    }
}

/**
 * What a platform host measured about the screen it is running on, in density-independent units.
 * Hosts publish this to [DeviceScreen] before the UI is built; everything downstream reads it
 * from there rather than reaching for a platform API of its own.
 */
data class ScreenMetrics(
    val widthDp: Double,
    val heightDp: Double,
    val safeArea: SafeAreaInsets = SafeAreaInsets.NONE,
) {
    /** Landscape-normalised, since both hosts lock landscape (see [ScreenLayout]). */
    val longSideDp: Double get() = max(widthDp, heightDp)
    val shortSideDp: Double get() = min(widthDp, heightDp)

    /**
     * True for a screen with a tablet's short side. 600dp is Android's own
     * `sw600dp` break point and also separates every iPhone (a landscape iPhone's short side is
     * 320-440pt) from every iPad (744pt and up).
     */
    val isTablet: Boolean get() = shortSideDp >= 600.0
}

/**
 * The live [ScreenMetrics] for this process, published by the platform host.
 *
 * `@Volatile` (from `kotlin.concurrent`, NOT `kotlin.jvm` - that one does not exist on
 * Kotlin/Native and has broken the iOS build before; see `.junie/guidelines.md`) because the
 * host writes it on the UI thread and KorGE reads it on its own GL thread.
 *
 * Unset is a supported state: everything that reads this falls back to the design canvas, which
 * is exactly the behaviour the app had before any of this existed.
 */
object DeviceScreen {
    @Volatile
    var metrics: ScreenMetrics? = null

    /** The virtual canvas for the current device, or the design canvas if nothing is published. */
    val viewport: VirtualViewport
        get() = metrics?.let { ScreenLayout.viewportFor(it) }
            ?: VirtualViewport(ScreenLayout.DESIGN_WIDTH, ScreenLayout.DESIGN_HEIGHT)

    /** Safe-area insets in virtual scene units, or zero if nothing is published. */
    val safeInsets: SafeAreaInsets
        get() = metrics?.let { ScreenLayout.safeInsetsInVirtualUnits(it) } ?: SafeAreaInsets.NONE

    /**
     * Safe-area insets scaled to a canvas whose size the caller already knows - what a scene
     * should use, since its own `sceneWidth`/`sceneHeight` is the canvas actually being drawn
     * into and may differ by a unit of rounding from [viewport].
     */
    fun safeInsetsForCanvas(canvasWidth: Double, canvasHeight: Double): SafeAreaInsets {
        val m = metrics ?: return SafeAreaInsets.NONE
        return ScreenLayout.safeInsetsInVirtualUnits(m, VirtualViewport(canvasWidth, canvasHeight))
    }

    val isTablet: Boolean get() = metrics?.isTablet ?: false

    fun publish(
        widthDp: Double,
        heightDp: Double,
        safeLeftDp: Double = 0.0,
        safeTopDp: Double = 0.0,
        safeRightDp: Double = 0.0,
        safeBottomDp: Double = 0.0,
    ) {
        metrics = ScreenMetrics(
            widthDp = widthDp,
            heightDp = heightDp,
            safeArea = SafeAreaInsets(safeLeftDp, safeTopDp, safeRightDp, safeBottomDp),
        )
    }

    /**
     * Replaces only the insets, keeping the size already published. iOS learns its safe area
     * later than its screen size (the window has to lay out first), so the two arrive separately
     * there.
     */
    fun publishSafeArea(
        leftDp: Double,
        topDp: Double,
        rightDp: Double,
        bottomDp: Double,
    ) {
        val current = metrics ?: return
        metrics = current.copy(safeArea = SafeAreaInsets(leftDp, topDp, rightDp, bottomDp))
    }
}
