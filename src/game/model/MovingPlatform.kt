package game.model

import kotlin.math.PI
import kotlin.math.cos

/**
 * Declarative definition of a horizontally moving platform (such as a crane-suspended shipping container).
 */
data class MovingPlatformDef(
    val id: String,
    val initialX: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val minX: Double = initialX,
    val maxX: Double = initialX,
    val minY: Double = y,
    val maxY: Double = y,
    val periodSeconds: Double,
    val phaseOffsetSeconds: Double = 0.0,
    val isVariant1: Boolean = false,
    val initialY: Double = minY,
    // When true, the platform sits parked at (initialX, initialY) - not oscillating at all -
    // until something calls MovingPlatform.activate() on it (see Lever.targetMechanismId and
    // GameWorld's lever-activation handling, matched by [id]). Its own cycle clock only starts
    // ticking from that moment, from t=0, so the first excursion always plays out the same way
    // regardless of how long the player took to reach the lever. See LEVEL_6_LAYOUT.
    val startsInactive: Boolean = false,
    // Only meaningful with startsInactive = true: how long the platform stays parked at rest
    // AFTER activate() is called before it starts easing. Without this, "activate on lever pull"
    // and "reach the platform's rest position and jump onto it" are the same instant only for a
    // frame-perfect player - any real walk from the lever to the edge lets the platform ease away
    // before the jump even leaves the ground, so a same-height gap jump (sized for the REST gap)
    // sails clean over where the platform used to be. This is the mechanism's own wind-up, giving
    // a real (if brief) window to close that gap before it starts moving. See LEVEL_6_LAYOUT.
    val activationDelaySeconds: Double = 0.0,
    // When true, one activate() call plays out exactly ONE full period (one cosine excursion out
    // and back to rest) and then the platform re-arms itself: isActive drops back to false and
    // its own clock resets to zero, exactly as if [startsInactive] had never been triggered. A
    // single sinusoidal period already traces the whole "move to maxX/maxY and ease back to
    // minX/minY" round trip (t goes 0 -> 1 -> 0 across one period), so it's always parked back at
    // rest the instant it re-arms. Meant for a repeatable one-attempt mechanism (see
    // LEVEL_6_LAYOUT's lever crate): missing the timing window costs that attempt, but once the
    // platform has eased all the way back to rest, whatever throws the matching lever (see
    // GameWorld's per-tick lever-reset check) can throw it again for another identical attempt.
    val oneShot: Boolean = false
)

data class PlatformDisplacement(
    val dx: Double,
    val dy: Double
)

/**
 * Runtime state and motion simulator for a moving platform.
 *
 * Employs smooth sinusoidal acceleration/deceleration matching a heavy gantry-suspended load,
 * slowing down gently near the turning endpoints and moving swiftest through the center.
 */
class MovingPlatform(
    val id: String,
    val width: Double,
    val height: Double,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val periodSeconds: Double,
    val phaseOffsetSeconds: Double = 0.0,
    val isVariant1: Boolean = false,
    val initialX: Double = minX,
    val initialY: Double = minY,
    val startsInactive: Boolean = false,
    val activationDelaySeconds: Double = 0.0,
    val oneShot: Boolean = false
) {
    constructor(
        id: String,
        y: Double,
        width: Double,
        height: Double,
        minX: Double,
        maxX: Double,
        periodSeconds: Double,
        phaseOffsetSeconds: Double = 0.0,
        isVariant1: Boolean = false,
        initialX: Double = minX,
        minY: Double = y,
        maxY: Double = y,
        initialY: Double = minY,
        startsInactive: Boolean = false,
        activationDelaySeconds: Double = 0.0,
        oneShot: Boolean = false
    ) : this(
        id = id,
        width = width,
        height = height,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        periodSeconds = periodSeconds,
        phaseOffsetSeconds = phaseOffsetSeconds,
        isVariant1 = isVariant1,
        initialX = initialX,
        initialY = initialY,
        startsInactive = startsInactive,
        activationDelaySeconds = activationDelaySeconds,
        oneShot = oneShot
    )

    var x: Double = initialX
        private set

    var y: Double = initialY
        private set

    var vx: Double = 0.0
        private set

    var vy: Double = 0.0
        private set

    /** See [MovingPlatformDef.startsInactive]. Ignored (always true) when that flag is false. */
    var isActive: Boolean = !startsInactive
        private set

    /** Own local clock, only ticking while [isActive] - see [MovingPlatformDef.startsInactive]. */
    private var activeElapsedSeconds: Double = 0.0

    /** Arms a gated platform so it starts oscillating from its own t=0 on the next [update]. No-op if not gated or already active. */
    fun activate() {
        isActive = true
    }

    val bounds: Rect get() = Rect(x, y, width, height)

    val top: Double get() = y
    val bottom: Double get() = y + height
    val left: Double get() = x
    val right: Double get() = x + width

    /**
     * Advances platform position based on the global elapsed time.
     * Returns the 2D displacement (dx, dy) applied during this tick.
     */
    fun update(dt: Double, totalElapsedSeconds: Double): PlatformDisplacement {
        if (startsInactive && !isActive) {
            return PlatformDisplacement(0.0, 0.0)
        }
        val oldX = x
        val oldY = y
        // A gated platform uses its own clock, started fresh at activation, instead of the
        // level's global elapsed time - see [MovingPlatformDef.startsInactive].
        val raw = if (startsInactive) {
            activeElapsedSeconds += dt
            (activeElapsedSeconds - activationDelaySeconds).coerceAtLeast(0.0)
        } else {
            totalElapsedSeconds
        }
        // A one-shot mechanism plays exactly one full period per activation - clamping the clock
        // (rather than the resulting phase) means it settles exactly back at t=0, its own rest
        // position, once that period has elapsed.
        val clock = if (startsInactive && oneShot) raw.coerceAtMost(periodSeconds) else raw
        val phase = if (periodSeconds > 0.0) {
            val normalized = ((clock + phaseOffsetSeconds) % periodSeconds) / periodSeconds
            if (normalized < 0.0) normalized + 1.0 else normalized
        } else {
            0.0
        }
        val t = 0.5 - 0.5 * cos(2.0 * PI * phase)

        if (minX != maxX) {
            x = minX + (maxX - minX) * t
        }
        if (minY != maxY) {
            y = minY + (maxY - minY) * t
        }

        vx = if (dt > 1e-6) (x - oldX) / dt else 0.0
        vy = if (dt > 1e-6) (y - oldY) / dt else 0.0

        // Re-arm: once a one-shot's single period has fully played out, drop back to inactive and
        // zero the clock so the next activate() call reproduces the exact same attempt from
        // scratch, instead of staying frozen active forever - see [MovingPlatformDef.oneShot].
        if (startsInactive && oneShot && raw >= periodSeconds) {
            isActive = false
            activeElapsedSeconds = 0.0
        }

        return PlatformDisplacement(x - oldX, y - oldY)
    }

    fun reset() {
        x = initialX
        y = initialY
        vx = 0.0
        vy = 0.0
        isActive = !startsInactive
        activeElapsedSeconds = 0.0
    }
}
