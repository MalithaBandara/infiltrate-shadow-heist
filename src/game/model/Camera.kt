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
    val sweepPauseDuration: Double = 0.0,
    /**
     * Seconds camera stops rotating when the player is detected, matching guard investigateDuration (2.5s).
     */
    var detectionPauseDuration: Double = 2.5
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

    /** Time left holding paused due to player detection; 0 while sweeping normally. */
    var detectionPauseTimer: Double = 0.0
        private set

    /** True if camera currently has eyes on player. */
    var isDetectingPlayer: Boolean = false
        private set

    val isPausedFromDetection: Boolean get() = isDetectingPlayer || detectionPauseTimer > 0.0

    fun onPlayerSpotted() {
        isDetectingPlayer = true
        if (detectionPauseDuration > 0.0) {
            detectionPauseTimer = detectionPauseDuration
        }
    }

    fun onVisualLost() {
        isDetectingPlayer = false
    }

    fun resetDetectionPause() {
        isDetectingPlayer = false
        detectionPauseTimer = 0.0
    }

    fun update(dt: Double) {
        if (minAngle >= maxAngle || sweepSpeed <= 0.0) {
            currentAngle = minAngle
            return
        }

        if (isDetectingPlayer) {
            return
        }

        if (detectionPauseTimer > 0.0) {
            detectionPauseTimer = (detectionPauseTimer - dt).coerceAtLeast(0.0)
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
        detectionPauseTimer = 0.0
        isDetectingPlayer = false
    }

    companion object {
        /**
         * See [pivotPosition]. Checked directly against LEVEL_3_LAYOUT - beamCamera and poleCamera
         * are the only cameras in the game, and both share these same constants (there is no
         * per-instance override): stepCrate2's own top sits only 66 units below beamCamera's mount
         * (cameraBeam.bottom 326 to stepCrate2.top 392), and the eye must stay above it at every
         * angle the sweep actually reaches near the crate, or "look further along the same ray"
         * starts pointing past the crate instead of at it. This also drives the render scale in
         * GameplayScene.kt. Four sizes tried so far: 23/50 and 16/34 both read as visibly oversized
         * next to the player; 6/13 (the very next step down) overshot the other way and read as too
         * small; 9.5 -> ~20 (splitting the difference, same ~0.475 neck:lens ratio throughout) is
         * the current middle-ground pick. A shorter lens only makes the "stays above the crate"
         * check in the paragraph above EASIER, not harder (smaller LENS_LENGTH means less vertical
         * excursion at any given angle, so the eye stays closer to the mount/pivot regardless of
         * currentAngle) - re-confirmed by sweeping every angle after every resize, this one
         * included. poleCamera (added later, mounted on a freestanding pole rather than a beam) was
         * positioned to work with these same constants rather than requesting its own - see its own
         * comment in LevelData.kt.
         */
        const val NECK_LENGTH = 9.5

        /** See [eyePosition]. */
        const val LENS_LENGTH = 20.0

        fun createSweeping(
            x: Double,
            y: Double,
            centerAngle: Double = PI / 2.0,
            sweepAngleDelta: Double = 30.0 * (PI / 180.0),
            sweepSpeed: Double = 0.7,
            visionRange: Double = 240.0,
            visionFov: Double = 45.0 * (PI / 180.0),
            width: Double = 20.0,
            height: Double = 20.0,
            detectionPauseDuration: Double = 2.5
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
                sweepDirection = 1.0,
                detectionPauseDuration = detectionPauseDuration
            )
        }
    }
}
