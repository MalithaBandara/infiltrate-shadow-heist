package game.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class Camera(
    var x: Double,
    var y: Double,
    val width: Double = 20.0,
    val height: Double = 20.0,
    var minAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    var maxAngle: Double = (90.0 + 30.0) * (PI / 180.0),
    var currentAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    var sweepSpeed: Double = 0.7, // radians per second
    var visionRange: Double = 240.0,
    var visionFov: Double = 45.0 * (PI / 180.0), // 45 degrees in radians
    var sweepDirection: Double = 1.0,
    /**
     * Seconds spent holding at each end of the sweep before reversing - see
     * Guard.patrolPauseDuration, the same idea applied to a camera's side-to-side sweep instead
     * of a guard's route. 0 (every camera before level 3's beam-mounted one) reverses the instant
     * it reaches an end, so it is always mid-sweep and never actually settled looking either way.
     */
    val sweepPauseDuration: Double = 0.0
) {
    val bounds: Rect get() = Rect(x, y, width, height)
    val center: Vec2d get() = Vec2d(x + width / 2.0, y + height / 2.0)

    /**
     * The ball-joint pivot cameranew.png swivels around, in world space - fixed relative to (x, y)
     * (the mount's own attach point, flush against whatever it's bolted to) regardless of
     * currentAngle, since only the body beyond the joint rotates. [NECK_LENGTH] is measured
     * straight down from the attach point, matching the art's own vertical neck (see
     * GameplayScene.kt's cameraMountAttachRaw/cameraPivotRaw).
     */
    val pivotPosition: Vec2d get() = Vec2d(x + width / 2.0, y + NECK_LENGTH)

    /**
     * The lens tip - [LENS_LENGTH] out from the joint along the CURRENT facing direction, not a
     * fixed offset, since the body is a rigid piece rotating around the joint: whatever the sweep
     * angle, the tip stays the same distance from the joint. Vision starts here, not at the joint
     * or the mount, so what's lit on screen matches exactly where the camera's own lens is drawn -
     * same principle as Guard.eyePosition being the torch lens, not the guard's centre.
     */
    val eyePosition: Vec2d get() {
        val pivot = pivotPosition
        return Vec2d(pivot.x + cos(currentAngle) * LENS_LENGTH, pivot.y + sin(currentAngle) * LENS_LENGTH)
    }
    val facingAngle: Double get() = currentAngle

    /** Time left holding at a sweep end before reversing; 0 while actively sweeping. */
    var sweepPauseTimer: Double = 0.0
        private set

    fun update(dt: Double) {
        if (minAngle >= maxAngle || sweepSpeed <= 0.0) {
            currentAngle = minAngle
            return
        }

        if (sweepPauseTimer > 0.0) {
            sweepPauseTimer = (sweepPauseTimer - dt).coerceAtLeast(0.0)
            return
        }

        currentAngle += sweepDirection * sweepSpeed * dt

        if (sweepDirection > 0.0 && currentAngle >= maxAngle) {
            currentAngle = maxAngle
            sweepDirection = -1.0
            if (sweepPauseDuration > 0.0) sweepPauseTimer = sweepPauseDuration
        } else if (sweepDirection < 0.0 && currentAngle <= minAngle) {
            currentAngle = minAngle
            sweepDirection = 1.0
            if (sweepPauseDuration > 0.0) sweepPauseTimer = sweepPauseDuration
        }
    }

    fun reset() {
        currentAngle = minAngle
        sweepDirection = 1.0
        sweepPauseTimer = 0.0
    }

    companion object {
        /**
         * See [pivotPosition]. Checked directly against LEVEL_3_LAYOUT (the only camera in the
         * game): stepCrate2's own top sits only 66 units below the beam mount (cameraBeam.bottom
         * 326 to stepCrate2.top 392), and the eye must stay above it at every angle the sweep
         * actually reaches near the crate, or "look further along the same ray" starts pointing
         * past the crate instead of at it. This also drives the render scale in GameplayScene.kt -
         * two earlier passes (23/50, then 16/34) each still read as a visibly oversized camera body
         * on a screenshot report, and 16/34's own wider 55..140 sweep let the cone's shallow FOV
         * edge miss the crate's silhouette and sail past it into open ground beyond - see
         * beamCamera's own doc comment in LevelData.kt. 13/27 is small enough to look like a
         * compact ceiling unit and, combined with a narrower sweep (beamCamera's own maxAngle) and
         * a shorter visionRange, keeps the cone's reach disciplined - confirmed by sweeping every
         * angle AND several heights past the crate, checking both that stepCrate2 is still lit
         * somewhere in the sweep and that no point past its far edge ever is.
         */
        const val NECK_LENGTH = 13.0

        /** See [eyePosition]. */
        const val LENS_LENGTH = 27.0

        fun createSweeping(
            x: Double,
            y: Double,
            centerAngle: Double = PI / 2.0,
            sweepAngleDelta: Double = 30.0 * (PI / 180.0),
            sweepSpeed: Double = 0.7,
            visionRange: Double = 240.0,
            visionFov: Double = 45.0 * (PI / 180.0),
            width: Double = 20.0,
            height: Double = 20.0
        ): Camera {
            val min = centerAngle - sweepAngleDelta
            val max = centerAngle + sweepAngleDelta
            return Camera(
                x = x,
                y = y,
                width = width,
                height = height,
                minAngle = min,
                maxAngle = max,
                currentAngle = min,
                sweepSpeed = sweepSpeed,
                visionRange = visionRange,
                visionFov = visionFov,
                sweepDirection = 1.0
            )
        }
    }
}
