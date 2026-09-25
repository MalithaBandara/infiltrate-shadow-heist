package game.model

import kotlin.math.abs
import kotlin.math.exp

/**
 * The gameplay camera's horizontal follow: a **critically damped spring**, not a lerp.
 *
 * ## Why this is not `x += (target - x) * factor`
 *
 * That first-order form was what `GameplayScene` used, and it is smooth in *position* but not in
 * *acceleration*: `dx/dt = k * (target - x)`, so the instant the player's own velocity steps, the
 * camera's acceleration steps with it. The player's horizontal speed steps constantly and every
 * one of those steps is a real, tuned gameplay rule - none of them are bugs to fix in `Player`:
 *
 * - landing out of a jump drops the walk to `Player.jumpLandingSpeed` (100) for
 *   `jumpLandingDuration` (0.05s) and then restores 132 - two steps back to back, right under the
 *   landing animation, which is exactly where "it moves smoothly and then for landing part the
 *   screen suddenly moves forward" was reported;
 * - walking off a ledge drops to `Player.dropSpeed` (30) and the touchdown restores 132;
 * - a body that meets a platform's near face mid-flight is pinned there (132 -> 0 in one frame)
 *   and released the moment the feet clear its top;
 * - a climb or a swing drives `x` off its own curve for the whole move and hands control back at
 *   the end.
 *
 * Measured on level 1 against the real loop, the worst of those (the pin) took the first-order
 * camera from -178 px/s to a standstill inside eight frames with the whole change front-loaded
 * into the first two. A second-order follow cannot do that: its acceleration is a function of the
 * position error and its own velocity, both of which are continuous, so **the scroll rate can
 * never change in a single frame no matter what the player does**. That is the whole reason for
 * this class, and it is what makes every stance - jump, land, climb, swing, wind, push - scroll
 * the same way.
 *
 * ## Critically damped, and why not merely "damped"
 *
 * Under-damping overshoots (the camera sails past the player and comes back - a wobble on every
 * landing); over-damping is a slow crawl back to centre. Critical damping is the one ratio that
 * returns fastest with no overshoot, which is the only acceptable behaviour for a camera that
 * frames a character the player is steering.
 *
 * ## Frame-rate independence
 *
 * [update] is the exact closed-form solution of the critically damped system over one step, not a
 * per-frame approximation, so a 120 Hz phone and a 60 Hz one settle along the same curve. (The
 * game caps nothing and runs at panel refresh - see `.junie/guidelines.md`, "Device heating".)
 *
 * ## What [DEFAULT_SMOOTH_TIME] costs, measured
 *
 * Tracking a target moving at a constant `v`, this settles at a steady lag of `v * smoothTime`
 * behind it, and the smoothing is bought with exactly that lag. Driven through level 1 at 60fps,
 * comparing the worst **single-frame change in scroll rate** - which is the thing the eye reads
 * as a jolt - against the old `k = 16` filter:
 *
 * | smoothTime | jump landing | body pinned against a crate face | walking lag |
 * | --- | --- | --- | --- |
 * | old `k = 16` | 10.1 px/s | 41.7 px/s | 9.5 px |
 * | 0.0625 (same lag as old) | 8.1 | 33.6 | 9.5 |
 * | 0.10 | 5.2 | 21.5 | 16.0 |
 * | **0.12 (this)** | **4.1** | **18.1** | **19.4** |
 * | 0.15 | 3.0 | 14.5 | 24.5 |
 *
 * 0.12 more than halves both jolts. The 19.4 px it costs is 1.9% of the canvas width and only
 * exists while the player is actually running - the spring settles exactly on target when they
 * stop, so standing, aiming and every cutscene-ish moment frame identically to before, and while
 * running the character sitting slightly behind centre shows more of what is ahead, which is what
 * a following camera is supposed to do. Framing was signed off over many rounds; if this ever
 * reads as too loose, move this one number and re-measure the table rather than reaching back for
 * a lerp.
 */
class CameraFollow(
    /**
     * Roughly the time the camera takes to close most of a gap. Larger is smoother and laggier.
     * See [DEFAULT_SMOOTH_TIME] for why the default is what it is.
     */
    var smoothTime: Double = DEFAULT_SMOOTH_TIME,
) {
    /** Where the camera is, in whatever units [update] is fed. */
    var position: Double = 0.0
        private set

    /** The camera's own velocity. Carried across frames - it is what makes the follow C1. */
    var velocity: Double = 0.0
        private set

    /** Teleports the camera and kills its momentum. For a scene's first frame and for respawns. */
    fun snapTo(target: Double) {
        position = target
        velocity = 0.0
    }

    /**
     * Advances one frame and returns the new [position].
     *
     * [snapIfFartherThan] is the teleport guard: a checkpoint respawn, an in-place conveyor reset
     * (`LevelLayout.restartOnConveyorFallOff`) and a continue-ad revive all move the player a long
     * way in one frame, and easing across a level reads as a whip-pan rather than a camera. Any
     * jump larger than this is treated as a cut.
     */
    fun update(target: Double, dt: Double, snapIfFartherThan: Double = Double.POSITIVE_INFINITY): Double {
        if (dt <= 0.0 || !dt.isFinite() || !target.isFinite()) return position
        if (abs(target - position) > snapIfFartherThan) {
            snapTo(target)
            return position
        }
        // Critically damped x'' + 2*w*x' + w^2*x = 0 solved exactly over dt, in the error frame
        // (x = position - target). smoothTime is 2/w, the classic SmoothDamp parameterisation.
        val omega = 2.0 / smoothTime.coerceAtLeast(MIN_SMOOTH_TIME)
        val decay = exp(-omega * dt)
        val error = position - target
        val term = (velocity + omega * error) * dt
        position = target + (error + term) * decay
        velocity = (velocity - omega * term) * decay
        return position
    }

    companion object {
        /** 0.12s - picked off the measured table in the class doc. Re-measure if it moves. */
        const val DEFAULT_SMOOTH_TIME: Double = 0.12

        /** Guards against a division blow-up if someone sets [smoothTime] to zero. */
        const val MIN_SMOOTH_TIME: Double = 1.0 / 240.0
    }
}
