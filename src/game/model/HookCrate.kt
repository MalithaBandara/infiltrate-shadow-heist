package game.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A crate attached to a hanging hook that blocks the player from swinging from that hook.
 * When detached (e.g. via a lever), it drops with gravity, freeing the hook for swinging.
 *
 * Optionally the whole rig travels: with a non-zero [sweepX] the hook eases right by up to
 * [sweepX] and back over [sweepPeriodSeconds] (the same cosine easing as [MovingPlatform]), and
 * keeps travelling after the crate has dropped. [hook] and [initialBounds] are the rest
 * positions; [currentHook] is where the hook is now. The sweep runs on the crate's own clock
 * rather than the level's, so GameWorld can hold it still while the player swings from it.
 *
 * With [physical] the crate is a real body (LEVEL_8_LAYOUT's rig):
 *   - hanging, it is a pendulum on its rope under the moving hook - it lags when the rig sets off
 *     and swings on when it stops ([swingAngle], [swingRate]);
 *   - cut loose, it keeps the velocity it had at that instant (the rig's and the swing's) and
 *     tumbles as a [RigidBox] - bouncing off whatever it hits, carts included, and handing a cart
 *     the impulse. It comes to rest wherever that leaves it and STAYS there: nothing re-hangs it.
 *   - come to rest flat between a cart's handle posts, on its deck, it is caught and rides in
 *     that cart from then on ([carriedByCartId]). GameWorld drives the collisions.
 * Level 5's crate is physical too. Without it a crate just drops straight down onto the floor.
 */
data class HookCrate(
    val id: String,
    val hook: Rect,
    var bounds: Rect,
    val ropeLength: Double = 0.0,
    var isDetached: Boolean = false,
    var vy: Double = 0.0,
    var isLanded: Boolean = false,
    val initialBounds: Rect = bounds,
    /** How far right of its rest position the rig travels; 0 = a fixed hook. */
    val sweepX: Double = 0.0,
    val sweepPeriodSeconds: Double = 0.0,
    val sweepPhaseOffsetSeconds: Double = 0.0,
    /** See [MovingPlatformDef.noGroundBoarding] - applies while the crate still hangs. */
    val noGroundBoarding: Boolean = false,
    /** See the class doc. On for every shipped hook crate (levels 5, 8 and 9). */
    val physical: Boolean = false
) {
    /** The rig's own clock - see the class doc. */
    var sweepClock: Double = 0.0
        private set

    /** Current offset of the rig from its rest position. */
    var offsetX: Double = sweepOffsetAt(0.0)
        private set

    /** The rig's own horizontal velocity, from the easing curve. */
    var hookVx: Double = 0.0
        private set

    /** Pendulum angle of the rope from straight down, positive swung to the RIGHT (radians). */
    var swingAngle: Double = 0.0
        private set

    var swingRate: Double = 0.0
        private set

    /** The loose body once cut - null while it hangs, or for a non-[physical] crate. */
    var body: RigidBox? = null
        private set

    /** The id of the cart this crate came to rest in, once caught. */
    var carriedByCartId: String? = null

    /** Where in the cart it came to rest, from the cart's own x - it rides at this offset. */
    var carryOffsetX: Double = 0.0

    val isHanging: Boolean get() = !isDetached

    val currentHook: Rect get() = if (offsetX == 0.0) hook else Rect(hook.x + offsetX, hook.y, hook.width, hook.height)

    /** The rope's top: where it is tied on under the hook. */
    val ropeTopX: Double get() = initialBounds.centerX + offsetX
    val ropeTopY: Double get() = initialBounds.top - ropeLength

    /** Pivot to the crate's centre - the pendulum's length. */
    val pendulumLength: Double get() = ropeLength + initialBounds.height / 2.0

    /** Centre and rotation to draw it at (rotation clockwise-positive on screen). */
    val drawCenterX: Double get() = body?.cx ?: hangingCenterX()
    val drawCenterY: Double get() = body?.cy ?: hangingCenterY()
    val drawAngle: Double get() = body?.angle ?: -swingAngle

    init {
        if (!isDetached) bounds = hangingBounds()
    }

    private fun hangingCenterX(): Double =
        if (physical) ropeTopX + pendulumLength * sin(swingAngle) else bounds.centerX

    private fun hangingCenterY(): Double =
        if (physical) ropeTopY + pendulumLength * cos(swingAngle) else bounds.centerY

    private fun sweepOffsetAt(clock: Double): Double {
        if (sweepX == 0.0 || sweepPeriodSeconds <= 0.0) return 0.0
        return sweepX * (0.5 - 0.5 * cos(2.0 * PI * sweepPhase(clock)))
    }

    private fun sweepPhase(clock: Double): Double {
        val normalized = ((clock + sweepPhaseOffsetSeconds) % sweepPeriodSeconds) / sweepPeriodSeconds
        return if (normalized < 0.0) normalized + 1.0 else normalized
    }

    private fun sweepAccelAt(clock: Double): Double {
        if (sweepX == 0.0 || sweepPeriodSeconds <= 0.0) return 0.0
        val w = 2.0 * PI / sweepPeriodSeconds
        return sweepX * 0.5 * w * w * cos(2.0 * PI * sweepPhase(clock))
    }

    private fun sweepVelAt(clock: Double): Double {
        if (sweepX == 0.0 || sweepPeriodSeconds <= 0.0) return 0.0
        val w = 2.0 * PI / sweepPeriodSeconds
        return sweepX * 0.5 * w * sin(2.0 * PI * sweepPhase(clock))
    }

    private fun hangingBounds(): Rect {
        if (!physical) {
            return if (offsetX == 0.0) initialBounds
            else Rect(initialBounds.x + offsetX, initialBounds.y, initialBounds.width, initialBounds.height)
        }
        val w = initialBounds.width
        val h = initialBounds.height
        val c = kotlin.math.abs(cos(swingAngle))
        val s = kotlin.math.abs(sin(swingAngle))
        val bw = w * c + h * s
        val bh = w * s + h * c
        return Rect(hangingCenterX() - bw / 2.0, hangingCenterY() - bh / 2.0, bw, bh)
    }

    /**
     * Moves the rig along its sweep; a crate still on the hook goes with it - swinging, for a
     * [physical] one, as a damped pendulum driven by the hook's own acceleration.
     */
    fun advanceSweep(dt: Double, toClock: Double? = null) {
        if (sweepX != 0.0) {
            // [toClock]: level 9 plays the rig back from level 8's recording (GameWorld's world
            // tracks) rather than running it on its own.
            sweepClock = toClock ?: (sweepClock + dt)
            offsetX = sweepOffsetAt(sweepClock)
            hookVx = sweepVelAt(sweepClock)
        }
        if (isDetached) return
        if (physical) {
            // theta'' = -(g sin theta + a cos theta) / L - damping * theta', sub-stepped.
            val steps = 4
            val h = dt / steps
            val a = sweepAccelAt(sweepClock)
            repeat(steps) {
                val acc = -(BoxPhysics.GRAVITY * sin(swingAngle) + a * cos(swingAngle)) / pendulumLength -
                    SWING_DAMPING * swingRate
                swingRate += acc * h
                swingAngle += swingRate * h
            }
        }
        bounds = hangingBounds()
    }

    /** Cuts the rope. A [physical] crate leaves with the velocity it had on the rope. */
    fun detach() {
        if (isDetached) return
        isDetached = true
        if (!physical) return
        val l = pendulumLength
        body = RigidBox(
            width = initialBounds.width,
            height = initialBounds.height,
            cx = hangingCenterX(),
            cy = hangingCenterY(),
            angle = -swingAngle,
            // The hook's own motion plus the swing's tangential velocity at the centre.
            vx = hookVx + swingRate * l * cos(swingAngle),
            vy = -swingRate * l * sin(swingAngle),
            omega = -swingRate
        )
    }

    /** Called by GameWorld after moving [body]: keeps [bounds] on the body's box. */
    fun syncBodyBounds() {
        body?.let { bounds = it.aabb() }
    }

    /**
     * Cut loose and not lying flat - stood on its end, or still tumbling. Such a load cannot be
     * climbed onto (2026-09-29: "when the crate is in this side, he should not be able to climb
     * onto that" - stood on end in the empty cart it was a way up that skipped the catch).
     */
    val isLooseAndNotFlat: Boolean
        get() {
            if (!physical || !isDetached || carriedByCartId != null) return false
            val b = body ?: return false
            val square = kotlin.math.round(b.angle / kotlin.math.PI) * kotlin.math.PI
            return kotlin.math.abs(b.angle - square) > CATCH_MAX_TILT
        }

    /**
     * Back on its hook, empty-handed of everything that happened. [atSweepClock] is where the rig's
     * travel picks up: 0 (its near end) for a fresh level; a respawn passes the current clock so the
     * rig keeps its phase against the level clock - level 8/9's swing needs it at its far end
     * exactly as the deck load (on the level clock) comes in.
     */
    fun reset(atSweepClock: Double = 0.0) {
        sweepClock = atSweepClock
        offsetX = sweepOffsetAt(atSweepClock)
        hookVx = sweepVelAt(atSweepClock)
        swingAngle = 0.0
        swingRate = 0.0
        body = null
        isDetached = false
        vy = 0.0
        isLanded = false
        carriedByCartId = null
        carryOffsetX = 0.0
        bounds = hangingBounds()
    }

    fun update(dt: Double, gravity: Double, groundY: Double) {
        if (!isDetached || isLanded) return
        vy += gravity * dt
        val nextY = bounds.y + vy * dt
        if (nextY + bounds.height >= groundY) {
            bounds = Rect(bounds.x, groundY - bounds.height, bounds.width, bounds.height)
            vy = 0.0
            isLanded = true
        } else {
            bounds = Rect(bounds.x, nextY, bounds.width, bounds.height)
        }
    }

    companion object {
        /** Air drag and rope friction on the hanging swing, per second. */
        const val SWING_DAMPING = 0.6

        /**
         * A caught crate may come to rest at most this far off square (radians) - any more and it
         * is wedged against a post rather than sitting on the deck.
         */
        const val CATCH_MAX_TILT = 0.12
    }
}
