package game.model

import kotlin.math.abs

enum class NoiseLevel(val radius: Double) {
    SILENT(0.0),
    LOW(100.0),
    NORMAL(180.0),
    HIGH(280.0)
}

enum class PlayerStance {
    STAND,
    CROUCH
}

data class Player(
    var x: Double,
    var y: Double,
    val width: Double = 36.0,
    val height: Double = 96.0,
    val crouchHeight: Double = 56.0,
    val startX: Double = x,
    val startY: Double = y
) {
    var vx: Double = 0.0
    var vy: Double = 0.0
    var isGrounded: Boolean = false

    var moveSpeed: Double = 132.0
    var crouchSpeed: Double = 65.0
    var jumpSpeed: Double = -320.0
    var gravity: Double = 1000.0
    var maxFallSpeed: Double = 600.0

    var isCrouching: Boolean = false

    /** Last direction the player moved, kept while stationary so a jump-in-place still climbs. */
    var facing: Double = 1.0
        private set

    var isClimbing: Boolean = false
        private set
    val climbDuration: Double = 2.15
    private var climbElapsed: Double = 0.0

    /** 0..1 through the climb in real time. */
    val climbProgress: Double get() = (climbElapsed / climbDuration).coerceIn(0.0, 1.0)

    /**
     * 0..1 through the climb's frames. Drives the animation frame and every motion curve, so
     * pose and position cannot drift apart. Warped away from real time by CLIMB_PACING_CURVE.
     */
    val climbPhase: Double get() = curveAt(CLIMB_PACING_CURVE, climbProgress)

    private var climbStartX = 0.0
    private var climbStartY = 0.0
    private var climbTargetX = 0.0
    private var climbTargetY = 0.0

    /** Highest a normal jump can reach: v0^2 / (2g), from the standard projectile apex formula. */
    val maxJumpHeight: Double get() = (jumpSpeed * jumpSpeed) / (2.0 * gravity)

    /** A box shorter than this is just jumped over normally - no climb needed. */
    val climbMinHeight: Double get() = maxJumpHeight

    /** A box taller than this is out of reach even for a climb (nothing to grab). */
    val climbMaxHeight: Double get() = maxJumpHeight + height

    var currentNoiseLevel: NoiseLevel = NoiseLevel.SILENT
    val currentNoiseRadius: Double get() = currentNoiseLevel.radius
    val isMoving: Boolean get() = abs(vx) > 1.0

    /**
     * Ground support is judged on this narrower span rather than the full [width]. The drawn
     * character is only ~21 units across inside a 36 unit collision box, so testing the whole
     * box lets the player stand with the entire visible body hanging past a ledge - it looks
     * like floating. Walls and ceilings still use the full width.
     */
    val footWidth: Double get() = width * 0.6

    val currentHeight: Double get() = if (isCrouching) crouchHeight else height
    val currentTopY: Double get() = (y + height) - currentHeight

    val bounds: Rect get() = Rect(x, currentTopY, width, currentHeight)
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = currentTopY + currentHeight / 2.0
    val center: Vec2d get() = Vec2d(centerX, centerY)

    val keyPoints: List<Vec2d>
        get() = listOf(
            Vec2d(centerX, currentTopY + 10.0),                      // Head
            Vec2d(centerX, currentTopY + currentHeight * 0.5),       // Torso
            Vec2d(centerX, y + height - 8.0)                         // Feet
        )

    fun resetToStart() {
        x = startX
        y = startY
        vx = 0.0
        vy = 0.0
        isGrounded = false
        isCrouching = false
        isClimbing = false
        climbElapsed = 0.0
        currentNoiseLevel = NoiseLevel.SILENT
    }

    fun update(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        platforms: List<Rect>
    ) {
        update(dt, moveInput, jumpInput, crouchInput = false, platforms = platforms)
    }

    fun update(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        crouchInput: Boolean,
        platforms: List<Rect>,
        climbTargets: List<Rect> = emptyList()
    ) {
        var remaining = dt
        val maxStep = 1.0 / 60.0
        var firstStep = true

        while (remaining > 1e-6) {
            val step = minOf(remaining, maxStep)
            updateStep(step, moveInput, if (firstStep) jumpInput else false, crouchInput, platforms, climbTargets)
            firstStep = false
            remaining -= step
        }
    }

    /**
     * Finds a box immediately ahead (in [direction]) that's too tall to jump onto but short
     * enough to climb, with clear headroom on top to actually stand there. Only [climbTargets]
     * (level boxes) are considered - not the full [platforms] list, so the player can't "climb"
     * a guard or a wall - but the headroom check still uses [platforms] so a low ceiling above
     * the box correctly blocks the climb.
     */
    private fun findClimbTarget(direction: Double, climbTargets: List<Rect>, platforms: List<Rect>): Rect? {
        if (direction == 0.0) return null
        val reach = 6.0
        val feetY = y + height
        for (box in climbTargets) {
            val adjacent = if (direction > 0.0) {
                box.left >= x + width - 1.0 && box.left <= x + width + reach
            } else {
                box.right <= x + 1.0 && box.right >= x - reach
            }
            if (!adjacent) continue
            
            // The box must provide a face to brace against (cannot be a floating ledge whose bottom is above player's feet).
            if (box.bottom < feetY - 4.0) continue

            val climbHeight = feetY - box.top
            if (climbHeight <= climbMinHeight || climbHeight > climbMaxHeight) continue

            val landing = Rect(box.left, box.top - height, maxOf(box.width, width), height)
            val blocked = platforms.any { it != box && it.intersects(landing) }
            if (blocked) continue

            return box
        }
        return null
    }

    private fun startClimb(box: Rect, direction: Double) {
        isClimbing = true
        climbElapsed = 0.0
        climbStartX = x
        climbStartY = y
        climbTargetY = box.top - height
        val minX = box.left
        val maxX = (box.right - width).coerceAtLeast(minX)
        climbTargetX = if (direction > 0.0) (box.left + 6.0).coerceIn(minX, maxX)
        else (box.right - width - 6.0).coerceIn(minX, maxX)
        isGrounded = false
        vx = 0.0
        vy = 0.0
    }

    /**
     * The climb is driven by the hand, not by an eased rise. The hand is held on the box's top
     * edge and the body sits wherever that requires, so the whole ascent - catching the lip,
     * hanging, and hauling up - falls out of the animation's own geometry. Easing the height
     * instead lifts the body while it is supposed to be hanging off its hands, which reads as
     * levitating up the face. See CLIMB_GRIP_CURVE.
     */
    private fun advanceClimb(dt: Double) {
        climbElapsed += dt
        val t = climbPhase
        val totalRise = climbStartY - climbTargetY
        // While hanging, the body sits wherever it must for the hands to stay on the lip. Once
        // the pull-up lifts higher than that, it takes over.
        val gripRise = (totalRise - curveAt(CLIMB_GRIP_CURVE, t) * height).coerceAtLeast(0.0)
        val pullRise = curveAt(CLIMB_RISE_CURVE, t) * totalRise
        x = climbStartX + (climbTargetX - climbStartX) * curveAt(CLIMB_SHIFT_CURVE, t)
        y = climbStartY - maxOf(gripRise, pullRise)
        if (t >= 1.0) {
            isClimbing = false
            isGrounded = true
            vx = 0.0
            vy = 0.0
        }
    }

    private fun updateStep(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        crouchInput: Boolean,
        platforms: List<Rect>,
        climbTargets: List<Rect> = emptyList()
    ) {
        if (isClimbing) {
            advanceClimb(dt)
            return
        }

        if (moveInput > 0.0) facing = 1.0 else if (moveInput < 0.0) facing = -1.0

        val wantsToCrouch = crouchInput
        // If player wants to stand up, check if head would collide with overhead ceiling
        val mustStayCrouched = if (!wantsToCrouch && isCrouching) {
            val standRect = Rect(x, y, width, height)
            platforms.any { platform ->
                standRect.intersects(platform) && (platform.top < (y + height - crouchHeight))
            }
        } else {
            false
        }
        isCrouching = wantsToCrouch || mustStayCrouched

        val effectiveSpeed = if (isCrouching) crouchSpeed else moveSpeed

        // Horizontal velocity
        vx = moveInput.coerceIn(-1.0, 1.0) * effectiveSpeed

        // Jump & Vertical acceleration
        if (jumpInput && isGrounded && !isCrouching) {
            val climbTarget = findClimbTarget(facing, climbTargets, platforms)
            if (climbTarget != null) {
                startClimb(climbTarget, facing)
                return
            }
            vy = jumpSpeed
            isGrounded = false
        }
        vy = (vy + gravity * dt).coerceAtMost(maxFallSpeed)

        val effHeight = currentHeight
        val effTopY = (y + height) - effHeight

        // Integrate X movement & check horizontal collisions across the entire body
        val targetX = x + vx * dt
        val hRect = Rect(targetX, effTopY, width, effHeight)
        var newX = targetX

        for (platform in platforms) {
            if (hRect.intersects(platform)) {
                // Resolve towards the side the player actually came from. Going purely on the
                // sign of vx drags anyone who is already overlapping a platform all the way to
                // its far edge - which is what happens when you walk off the end of a long
                // walkway and start falling while still horizontally inside it.
                val cameFromLeft = x + width <= platform.left + 1e-6
                val cameFromRight = x >= platform.right - 1e-6
                when {
                    cameFromLeft -> {
                        newX = minOf(newX, platform.left - width)
                        vx = 0.0
                    }
                    cameFromRight -> {
                        newX = maxOf(newX, platform.right)
                        vx = 0.0
                    }
                    else -> {
                        // Already overlapping before this step: push out whichever side is nearer.
                        if (abs((platform.left - width) - newX) <= abs(platform.right - newX)) {
                            newX = minOf(newX, platform.left - width)
                        } else {
                            newX = maxOf(newX, platform.right)
                        }
                        vx = 0.0
                    }
                }
            }
        }
        x = newX

        // Integrate Y movement & check vertical collisions across the entire body
        val targetY = y + vy * dt
        val targetEffTopY = (targetY + height) - effHeight
        val vRect = Rect(x, targetEffTopY, width, effHeight)
        var newY = targetY
        var landed = false

        // Landing is tested against the feet only - see footWidth.
        val footInset = (width - footWidth) / 2.0
        val vRectFeet = Rect(x + footInset, targetEffTopY, footWidth, effHeight)

        for (platform in platforms) {
            if (vy > 0.0) {
                // Feet landing on platform
                if (vRectFeet.intersects(platform)) {
                    newY = minOf(newY, platform.top - height)
                    vy = 0.0
                    landed = true
                }
            } else if (vRect.intersects(platform)) {
                if (vy < 0.0) {
                    // Head hitting ceiling / overhead platform
                    val ceilingY = platform.bottom - (height - effHeight)
                    newY = maxOf(newY, ceilingY)
                    vy = 0.0
                } else {
                    val playerFeetY = newY + height
                    val platformMidY = platform.y + platform.height / 2.0
                    if (playerFeetY <= platformMidY) {
                        newY = minOf(newY, platform.top - height)
                        landed = true
                    } else {
                        val ceilingY = platform.bottom - (height - effHeight)
                        newY = maxOf(newY, ceilingY)
                    }
                }
            }
        }
        y = newY
        isGrounded = landed

        // Update noise level based on movement state
        currentNoiseLevel = when {
            isCrouching -> NoiseLevel.SILENT
            abs(vx) > 1.0 -> NoiseLevel.NORMAL
            else -> NoiseLevel.SILENT
        }
    }

    companion object {
        /**
         * Closes the last couple of units once the hand comes off the lip, raw frames ~156-163.
         * The grip alone gets the feet to within 2.4 units of the top, so this has very little
         * left to do - it exists only so the climb finishes exactly on the surface. Combined
         * with the grip by taking whichever is higher.
         */
        private val CLIMB_RISE_CURVE = doubleArrayOf(
            0.00, 0.00,
            0.62, 0.00,
            0.66, 1.00,
            1.00, 1.00
        )

        /**
         * Height of the character's gripping hand above its own feet, as a fraction of player
         * height, measured off every 4th processed frame from the grab to the mantle. The body
         * is placed so that hand lands on the box's top edge, which is what keeps it on the lip
         * instead of gripping thin air.
         *
         * Finding the hand needs two rules, because no single one holds across the clip. While
         * the arms are raised (to ~f126) the hand is the silhouette's right-most pixel. From the
         * tuck onwards the head leans out past it, so there the hand is the right-most pixel
         * below the head - the top 28% of the body is skipped. Measuring the plain right-most
         * throughout tracks the head through the mantle and leaves the hand ~17 units under the
         * edge, which reads as the character letting go and flying up the last stretch.
         *
         * This is also what drives the pull-up - the hand is fixed on the lip and this height
         * shrinks from ~1.0 to ~0.5 as the character tucks, which lifts the feet from the ground
         * to near the top on its own. Do not smooth these: an approximated curve leaves the hand
         * several units clear of the edge, which is exactly what it looks like.
         *
         * The first two keys are deliberately not measured. Up to the grab the raised knee is
         * the right-most point, so they are set above 100/96 to hold the feet on the ground
         * until the hand actually catches the lip.
         */
        private val CLIMB_GRIP_CURVE = doubleArrayOf(
            0.000, 1.050,
            0.067, 1.045,
            0.089, 1.019,
            0.111, 0.962,
            0.133, 0.941,
            0.156, 0.958,
            0.178, 0.982,
            0.200, 1.007,
            0.222, 1.003,
            0.244, 0.999,
            0.267, 0.986,
            0.289, 0.929,
            0.311, 0.872,
            0.333, 0.831,
            0.356, 0.806,
            0.378, 0.794,
            0.400, 0.745,
            0.422, 0.688,
            0.444, 0.638,
            0.456, 0.610,
            0.478, 0.536,
            0.500, 0.409,
            0.522, 0.336,
            0.544, 0.278,
            0.567, 0.201,
            0.589, 0.127,
            0.611, 0.053,
            0.622, 0.025
        )

        /**
         * Real time is spent unevenly across the frames. Played straight the tail rushes, because
         * motion is not spread evenly through the clip: the hang is a long stretch of a character
         * barely moving, while the mantle and the stand-up pack large pose changes into the last
         * third. Frames-per-second is the wrong thing to hold constant - motion-per-second is.
         *
         * So the back half gets roughly twice the clock the front half does. Keys, in frames:
         *   0.00-0.28  f44-94    the reach, grab and hang - little happens, so it can move
         *   0.28-0.51  f94-136   the pull-up
         *   0.51-0.66  f136-163  swinging over the lip
         *   0.66-0.75  f163-179  settling into the crouch
         *   0.75-0.92  f179-210  standing up - a big pose change, but a brisk one in life
         *   0.92-1.00  f210-224  already upright and holding, so this is flushed quickly -
         *                        dwelling here would just lock the player in place after the
         *                        climb has visibly finished
         */
        private val CLIMB_PACING_CURVE = doubleArrayOf(
            0.00, 0.00,
            0.16, 0.28,
            0.41, 0.51,
            0.64, 0.66,
            0.74, 0.75,
            0.94, 0.92,
            1.00, 1.00
        )

        /**
         * How far across onto the box the character has travelled, as a fraction of the total.
         * A curve rather than a single eased window: confined to a window at the end, the whole
         * 42 units arrive in a few frames and the character visibly snaps forward. This creeps
         * in from partway up the pull-up (raw f109) and accelerates over the lip, so the travel
         * per frame is roughly 1, 3, 5, 9, 13, 19, 26, 35, 39, 42.
         *
         * It stays near zero while the body is still well below the top edge. Pushing forward
         * there would bury the character in the box's face while its lower half is at a height
         * that still shows, which reads as clipping through the wall.
         */
        private val CLIMB_SHIFT_CURVE = doubleArrayOf(
            0.00, 0.00,
            0.36, 0.00,
            0.45, 0.05,
            0.50, 0.12,
            0.55, 0.26,
            0.60, 0.52,
            0.63, 0.80,
            0.66, 1.00,
            1.00, 1.00
        )

        /** Piecewise-linear lookup into a `t, value` pair table, held flat outside its ends. */
        private fun curveAt(curve: DoubleArray, t: Double): Double {
            val lastKey = curve.size - 2
            if (t <= curve[0]) return curve[1]
            if (t >= curve[lastKey]) return curve[lastKey + 1]
            var i = 0
            while (i + 2 < lastKey && t > curve[i + 2]) i += 2
            val t0 = curve[i]
            val t1 = curve[i + 2]
            if (t1 <= t0) return curve[i + 3]
            val u = ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
            return curve[i + 1] + (curve[i + 3] - curve[i + 1]) * u
        }
    }
}
