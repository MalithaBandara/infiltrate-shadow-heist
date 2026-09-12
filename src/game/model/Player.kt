package game.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

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
    var dropSpeed: Double = 30.0
    var jumpSpeed: Double = -320.0
    var gravity: Double = 1000.0
    var maxFallSpeed: Double = 600.0

    var isJumping: Boolean = false
    var isDropping: Boolean = false
    var dropLandingTimer: Double = 0.0
    val dropLandingDuration: Double = 0.12

    var isCrouching: Boolean = false

    /** Last direction the player moved, kept while stationary so a jump-in-place still climbs. */
    var facing: Double = 1.0
        private set

    var isClimbing: Boolean = false
        private set
    val climbDuration: Double = 1.95
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

    var isSwinging: Boolean = false
        private set

    /**
     * Real time the whole move takes, from the push-off to the feet coming back down.
     * [SWING_PACING_CURVE] spends it with a fast, committed swing through the hook.
     */
    val swingDuration: Double = 1.10
    private var swingElapsed: Double = 0.0

    /** 0..1 through the swing in real time. */
    val swingProgress: Double get() = (swingElapsed / swingDuration).coerceIn(0.0, 1.0)

    /**
     * 0..1 through the swing's frames. Drives the animation frame and every motion curve, so
     * pose and position cannot drift apart - same contract as [climbPhase].
     */
    val swingPhase: Double get() = curveAt(SWING_PACING_CURVE, swingProgress)

    private var swingGripX = 0.0
    private var swingGripY = 0.0
    private var swingStartCenterX = 0.0
    private var swingStartFeetY = 0.0
    private var swingLandCenterX = 0.0
    private var swingLandFeetY = 0.0
    private var swingDirection = 1.0

    /**
     * Angular rotation (in degrees) to apply to the character sprite during the swing,
     * turning the character to follow the true arc of motion (tilted back at catch,
     * whipping forward through nadir and release, leaning forward in ballistic flight,
     * and straightening upright for landing).
     */
    val swingRotationDegrees: Double
        get() {
            if (!isSwinging) return 0.0
            val t = swingPhase
            return when {
                t < SWING_GRAB_PHASE -> {
                    // Leap into the hook: tilts back as the hands reach and close on the hook
                    val u = (t / SWING_GRAB_PHASE).coerceIn(0.0, 1.0)
                    swingDirection * (18.0 * u)
                }
                t <= SWING_RELEASE_PHASE -> {
                    // Pendulum swing on hook: sweeps from +18 deg (trailing) to -22 deg (leading)
                    val u = ((t - SWING_GRAB_PHASE) / (SWING_RELEASE_PHASE - SWING_GRAB_PHASE)).coerceIn(0.0, 1.0)
                    swingDirection * (18.0 - 40.0 * u.pow(1.1))
                }
                t <= SWING_LAND_PHASE -> {
                    // Ballistic flight: smoothly straightens from -22 deg forward lean to 0 deg at touchdown
                    val u = ((t - SWING_RELEASE_PHASE) / (SWING_LAND_PHASE - SWING_RELEASE_PHASE)).coerceIn(0.0, 1.0)
                    swingDirection * (-22.0 * (1.0 - u))
                }
                else -> 0.0
            }
        }

    /**
     * Vertical height of the rotation pivot above the character's feet.
     * At the hook (hang), rotation pivots around the hands gripping the hook.
     * During airborne flight, rotation pivots around the torso / center of mass.
     */
    val swingPivotHeight: Double
        get() {
            if (!isSwinging) return 0.0
            val t = swingPhase
            return when {
                t < SWING_GRAB_PHASE -> {
                    val u = (t / SWING_GRAB_PHASE).coerceIn(0.0, 1.0)
                    height * (0.5 + 0.5 * u)
                }
                t <= SWING_RELEASE_PHASE -> {
                    curveAt(SWING_GRIP_ABOVE_CURVE, t) * height
                }
                t <= SWING_LAND_PHASE -> {
                    val u = ((t - SWING_RELEASE_PHASE) / (SWING_LAND_PHASE - SWING_RELEASE_PHASE)).coerceIn(0.0, 1.0)
                    height * (1.0 - 0.5 * u)
                }
                else -> height * 0.5
            }
        }

    /**
     * How far past the hook's grip column the player comes down, in world units. The move is a
     * fixed shape rather than something that reaches for whatever ledge happens to be there:
     * levels are built around the distance, not the other way round (see LEVEL_4_LAYOUT, whose
     * gap and hook position are both derived from this). [findSwingTarget] refuses to start a
     * swing that would land on nothing, so the fixed shape can never strand the player.
     *
     * Checked on screen: the touchdown frame draws the body a few units behind the collision box
     * (it lands with the legs thrown forward), so this wants enough margin that the character does
     * not come down with a heel over the lip of the far ledge.
     */
    val swingLandAhead: Double = 109.0

    /**
     * How far ahead the grip has to be for a swing to be on, measured from the player's centre.
     *
     * This is really a rule about *where the player leaves the ground*, and it is deliberately
     * tight. Level 3's grip sits at the middle of a 150 gap, so standing at the very lip puts it
     * 93 ahead: a 75..97 window means the push-off can only happen within a few units of the edge,
     * which is where it looks like a decision. Anything looser and a player holding the jump
     * button takes off with a good stretch of platform still under them, jumping at nothing.
     *
     * It is not as tight to play as it is to read, because two existing mechanics widen it at
     * both ends: [jumpBufferDuration] retries a press made up to 0.15s early (about 20 units of
     * walking), and [coyoteDuration] keeps the jump live for 0.15s after stepping off, which is
     * what the near end of the window is for. Pressing outside it is an ordinary jump, and from
     * anywhere but the lip that lands back on the same platform.
     */
    val swingMinReach: Double = 75.0
    val swingMaxReach: Double = 97.0

    /** Grip height above the player's feet that the launch can plausibly cover. */
    val swingMinGripHeight: Double = 60.0
    val swingMaxGripHeight: Double = 150.0

    /** Highest a normal jump can reach: v0^2 / (2g), from the standard projectile apex formula. */
    val maxJumpHeight: Double get() = (jumpSpeed * jumpSpeed) / (2.0 * gravity)

    /** A box shorter than this is just jumped over normally - no climb needed. */
    val climbMinHeight: Double get() = maxJumpHeight

    /** A box taller than this is out of reach even for a climb (nothing to grab). Intended climbs are 72..100 units. */
    val climbMaxHeight: Double get() = 115.0

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

    /** Coyote time grace window (in seconds). Allows jumping for a brief moment after slipping or walking off an edge. */
    val coyoteDuration: Double = 0.15

    /** Jump buffer window (in seconds). Buffers a jump input if pressed shortly before landing or right at the edge. */
    val jumpBufferDuration: Double = 0.15

    var coyoteTimer: Double = 0.0
    var jumpBufferTimer: Double = 0.0
    private var jumpConsumedAfterClimb: Boolean = false

    fun resetToStart() {
        resetTo(startX, startY)
    }

    fun resetTo(targetX: Double, targetY: Double) {
        x = targetX
        y = targetY
        vx = 0.0
        vy = 0.0
        isGrounded = false
        isCrouching = false
        isClimbing = false
        climbElapsed = 0.0
        isSwinging = false
        swingElapsed = 0.0
        currentNoiseLevel = NoiseLevel.SILENT
        coyoteTimer = 0.0
        jumpBufferTimer = 0.0
        jumpConsumedAfterClimb = false
        isJumping = false
        isDropping = false
        dropLandingTimer = 0.0
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
        climbTargets: List<Rect> = emptyList(),
        swingHooks: List<Rect> = emptyList()
    ) {
        if (jumpInput && !isClimbing && !isSwinging && !jumpConsumedAfterClimb) {
            jumpBufferTimer = jumpBufferDuration
        }
        var remaining = dt
        val maxStep = 1.0 / 60.0
        var firstStep = true

        while (remaining > 1e-6) {
            val step = minOf(remaining, maxStep)
            updateStep(step, moveInput, if (firstStep) jumpInput else false, crouchInput, platforms, climbTargets, swingHooks)
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

    /**
     * Finds an overhead hook ahead of the player that a swing could actually be completed from.
     *
     * Three things have to hold, and all three are about not stranding anyone: the grip has to be
     * ahead in [direction] at a distance the launch can cover ([swingMinReach]..[swingMaxReach]);
     * it has to be overhead rather than at chest height or out of sight
     * ([swingMinGripHeight]..[swingMaxGripHeight] above the feet); and there has to be solid
     * ground waiting at [swingLandAhead] past it, level with the ledge being left. The move is a
     * fixed shape (see [swingLandAhead]), so without that last check a hook hung over too wide a
     * gap would drop the player into it every time.
     */
    private fun findSwingTarget(direction: Double, swingHooks: List<Rect>, platforms: List<Rect>): Rect? {
        if (direction == 0.0 || swingHooks.isEmpty()) return null
        val feetY = y + height
        for (hook in swingHooks) {
            val gripX = hookGripX(hook)
            val gripY = hookGripY(hook)
            val ahead = (gripX - centerX) * direction
            if (ahead < swingMinReach || ahead > swingMaxReach) continue

            val gripHeight = feetY - gripY
            if (gripHeight < swingMinGripHeight || gripHeight > swingMaxGripHeight) continue

            val landCenterX = gripX + direction * swingLandAhead
            val landed = platforms.any { p ->
                landCenterX >= p.left && landCenterX <= p.right && abs(p.top - feetY) <= 4.0
            }
            if (!landed) continue

            return hook
        }
        return null
    }

    private fun startSwing(hook: Rect, direction: Double) {
        isSwinging = true
        swingElapsed = 0.0
        swingDirection = direction
        swingGripX = hookGripX(hook)
        swingGripY = hookGripY(hook)
        swingStartCenterX = centerX
        swingStartFeetY = y + height
        swingLandCenterX = swingGripX + direction * swingLandAhead
        swingLandFeetY = swingStartFeetY
        isGrounded = false
        isCrouching = false
        vx = 0.0
        vy = 0.0
        jumpBufferTimer = 0.0
        coyoteTimer = 0.0
        isJumping = false
        isDropping = false
        dropLandingTimer = 0.0
        jumpConsumedAfterClimb = true
    }

    /**
     * Three parts, and only the middle one is really the swing.
     *
     * Between [SWING_GRAB_PHASE] and [SWING_RELEASE_PHASE] the hand is nailed to the hook and the
     * body is placed at whatever offset the frame itself puts between hand and feet - the same
     * idea as the climb's grip curve, in two axes instead of one. That is what makes the pendulum
     * the animator's, not a sine wave's: the body rises as the knees tuck and drops as they
     * extend because the footage says so.
     *
     * Note the horizontal half of that barely moves. The plates were shot with the camera locked
     * on the hook, so within a frame the hand sits still and the body swings around it, and the
     * swing is drawn rather than travelled. Travel comes from the launch and the release, which is
     * also why the collision box stays put under the hook while the silhouette sweeps a body width
     * either side of it - fine here, because nothing collides during a scripted move.
     *
     * Outside that window the hand is not on anything, so the two ends interpolate instead: from
     * wherever the player pushed off to the grip, and from the release to the landing. Both carry
     * an arc so they read as leaving the ground rather than sliding along it.
     */
    private fun advanceSwing(dt: Double) {
        swingElapsed += dt
        val t = swingPhase
        // Held flat past its ends by curveAt, so these are the grab pose before the grab and the
        // release pose after it - which is exactly what the two interpolations want as endpoints.
        val gripCenterX = swingGripX - swingDirection * curveAt(SWING_GRIP_AHEAD_CURVE, t) * height
        val gripFeetY = swingGripY + curveAt(SWING_GRIP_ABOVE_CURVE, t) * height

        val centerXNow: Double
        val feetYNow: Double
        when {
            t < SWING_GRAB_PHASE -> {
                val u = (t / SWING_GRAB_PHASE).coerceIn(0.0, 1.0)
                val e = u.pow(0.85)   // already at running speed, so it does not ease in
                centerXNow = swingStartCenterX + (gripCenterX - swingStartCenterX) * e
                feetYNow = swingStartFeetY + (gripFeetY - swingStartFeetY) * e - SWING_LAUNCH_ARC * sin(PI * u)
            }
            t <= SWING_RELEASE_PHASE -> {
                centerXNow = gripCenterX
                feetYNow = gripFeetY
            }
            t <= SWING_LAND_PHASE -> {
                val u = ((t - SWING_RELEASE_PHASE) / (SWING_LAND_PHASE - SWING_RELEASE_PHASE)).coerceIn(0.0, 1.0)
                val e = 1.0 - (1.0 - u).pow(1.4)
                val touchdownCenterX = swingLandCenterX - swingDirection * 11.0
                centerXNow = gripCenterX + (touchdownCenterX - gripCenterX) * e
                feetYNow = gripFeetY + (swingLandFeetY - gripFeetY) * e - SWING_FLIGHT_ARC * sin(PI * u)
            }
            else -> {
                val v = ((t - SWING_LAND_PHASE) / (1.0 - SWING_LAND_PHASE)).coerceIn(0.0, 1.0)
                val touchdownCenterX = swingLandCenterX - swingDirection * 11.0
                centerXNow = touchdownCenterX + swingDirection * 11.0 * v.pow(0.8)
                feetYNow = swingLandFeetY
            }
        }
        x = centerXNow - width / 2.0
        y = feetYNow - height
        facingOverride(swingDirection)

        if (swingProgress >= 1.0) {
            isSwinging = false
            x = swingLandCenterX - width / 2.0
            y = swingLandFeetY - height
            vx = 0.0
            vy = 0.0
            isGrounded = true
            isJumping = false
            isDropping = false
            dropLandingTimer = 0.0
            jumpBufferTimer = 0.0
        }
    }

    /** The swing owns which way the character faces for its whole duration. */
    private fun facingOverride(direction: Double) {
        if (direction > 0.0) facing = 1.0 else if (direction < 0.0) facing = -1.0
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
        jumpBufferTimer = 0.0
        jumpConsumedAfterClimb = true
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
            isJumping = false
            isDropping = false
            dropLandingTimer = 0.0
            vx = 0.0
            vy = 0.0
            x = climbTargetX
            y = climbTargetY
            jumpBufferTimer = 0.0
        }
    }

    private fun updateStep(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        crouchInput: Boolean,
        platforms: List<Rect>,
        climbTargets: List<Rect> = emptyList(),
        swingHooks: List<Rect> = emptyList()
    ) {
        if (isSwinging) {
            jumpBufferTimer = 0.0
            advanceSwing(dt)
            return
        }

        if (isClimbing) {
            jumpBufferTimer = 0.0
            advanceClimb(dt)
            if (isClimbing && climbProgress >= 0.85 && (moveInput != 0.0 || (jumpInput && !jumpConsumedAfterClimb))) {
                isClimbing = false
                isGrounded = true
                isJumping = false
                isDropping = false
                dropLandingTimer = 0.0
                x = climbTargetX
                y = climbTargetY
                jumpBufferTimer = 0.0
                jumpConsumedAfterClimb = true
                if (moveInput == 0.0) return
            } else {
                return
            }
        }

        if (!jumpInput) {
            jumpConsumedAfterClimb = false
        }

        if (jumpBufferTimer > 0.0) {
            jumpBufferTimer = maxOf(0.0, jumpBufferTimer - dt)
        }
        if (dropLandingTimer > 0.0) {
            dropLandingTimer = maxOf(0.0, dropLandingTimer - dt)
        }

        // Coyote timer: active when grounded; counts down once airborne (unless in an upward jump)
        if (isGrounded) {
            coyoteTimer = coyoteDuration
            isJumping = false
            isDropping = false
        } else {
            coyoteTimer = maxOf(0.0, coyoteTimer - dt)
            if (!isJumping) {
                isDropping = true
            }
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

        val baseSpeed = when {
            isDropping || dropLandingTimer > 0.0 -> dropSpeed
            else -> moveSpeed
        }
        val effectiveSpeed = if (isCrouching) minOf(crouchSpeed, baseSpeed) else baseSpeed

        // Horizontal velocity
        vx = moveInput.coerceIn(-1.0, 1.0) * effectiveSpeed

        // Jump & Vertical acceleration
        val effectiveJumpInput = jumpInput && !jumpConsumedAfterClimb
        val wantsToJump = effectiveJumpInput || jumpBufferTimer > 0.0
        val canJump = (isGrounded || (coyoteTimer > 0.0 && vy >= 0.0)) && !isCrouching
        if (wantsToJump && canJump) {
            // A hook beats open air but not a box: if the player is stood against something
            // climbable, that is what they meant.
            val climbTarget = findClimbTarget(facing, climbTargets, platforms)
            if (climbTarget == null && moveInput != 0.0) {
                // Walking is the whole entry condition for the swing - the clip opens on a
                // push-off stride, and there is no version of it that starts from standing.
                val swingTarget = findSwingTarget(facing, swingHooks, platforms)
                if (swingTarget != null) {
                    startSwing(swingTarget, facing)
                    return
                }
            }
            if (climbTarget != null) {
                startClimb(climbTarget, facing)
                jumpBufferTimer = 0.0
                coyoteTimer = 0.0
                isJumping = false
                isDropping = false
                dropLandingTimer = 0.0
                return
            }
            vy = jumpSpeed
            isGrounded = false
            isJumping = true
            isDropping = false
            dropLandingTimer = 0.0
            coyoteTimer = 0.0
            jumpBufferTimer = 0.0
            vx = moveInput.coerceIn(-1.0, 1.0) * moveSpeed
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
                        val playerFeetY = y + height
                        val footCenter = targetX + width / 2.0

                        // If the player is on top of this platform (within landing reach of the top surface,
                        // with feet supported horizontally on the platform), this is a floor landing/standing
                        // interaction, NOT a side-wall collision. Never eject horizontally!
                        val isStandingOrLandingOnTop = footCenter >= platform.left && footCenter <= platform.right &&
                            playerFeetY <= platform.top + 30.0 && effTopY < platform.top
                        val isInsideEntireSpan = targetX >= platform.left && targetX + width <= platform.right

                        if (isStandingOrLandingOnTop || isInsideEntireSpan) {
                            continue
                        }

                        // Already overlapping before this step and straddling an edge: push out whichever side is nearer.
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

        // wasFalling is captured once, before the loop, rather than branching on the live vy:
        // the old code branched on vy itself, but the very first matching platform zeroed vy as
        // a side effect, so every platform *after* it in the list fell through to the vy<=0
        // (wide-box, ceiling-or-floor-by-midpoint) branch instead of the feet-narrow one for the
        // rest of this same step - order-dependent behaviour nothing here was meant to rely on.
        val wasFalling = vy > 0.0

        // When the feet span straddles a seam between two touching platforms of different
        // heights (e.g. a step-up crate flush against a taller platform), every one of them
        // "intersects" vRectFeet at once. Picking the tallest unconditionally (the old
        // minOf-only rule) pins the player to it until the ENTIRE foot span has cleared its far
        // edge - which, walking off the tall side onto the low one, reads as hovering at the old
        // height for a stretch past where the platform visibly ends before suddenly dropping.
        // Picking whichever candidate keeps the player closest to their current y instead keeps
        // them on whatever they were already standing on until the feet span has no overlap with
        // it left at all, so the handover happens right at the visible edge instead of one foot
        // span later, and removes the appearance of flying past the end of the platform.
        val wasGrounded = isGrounded
        var bestLandingY: Double? = null
        for (platform in platforms) {
            if (wasFalling) {
                // Feet landing on platform: the player falls if most of their feet are outside
                // the edge in the direction of movement (or either edge if stationary).
                val footCenter = x + width / 2.0
                val offEdge = when {
                    vx > 0.0 -> footCenter > platform.right
                    vx < 0.0 -> footCenter < platform.left
                    else -> footCenter > platform.right || footCenter < platform.left
                }
                val supported = !offEdge && vRectFeet.intersects(platform)
                if (supported) {
                    val candidateY = platform.top - height
                    if (bestLandingY == null || abs(candidateY - y) < abs(bestLandingY - y)) {
                        bestLandingY = candidateY
                    }
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
                        // Standing/walking on floor: the player falls if most of their feet are outside
                        // the platform edge (center of the foot stance crosses past the platform).
                        val footCenter = x + width / 2.0
                        if (platform.left <= footCenter && platform.right >= footCenter) {
                            newY = minOf(newY, platform.top - height)
                            landed = true
                        }
                    } else {
                        val ceilingY = platform.bottom - (height - effHeight)
                        newY = maxOf(newY, ceilingY)
                    }
                }
            }
        }
        if (wasFalling && bestLandingY != null) {
            newY = bestLandingY
            vy = 0.0
        }
        y = newY
        val justLanded = landed && !wasGrounded
        if (isDropping && justLanded) {
            dropLandingTimer = dropLandingDuration
            isDropping = false
        }
        isGrounded = landed
        if (isGrounded) {
            isJumping = false
            isDropping = false
        } else if (!isJumping) {
            isDropping = true
            vx = moveInput.coerceIn(-1.0, 1.0) * dropSpeed
        }

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
            0.000, 0.00,
            0.556, 0.00,
            0.603, 1.00,
            1.000, 1.00
        )

        /**
         * Height of the character's gripping hand above its own feet, as a fraction of player
         * height, measured off every 4th processed frame from the grab (raw f70) to the mantle.
         * The body is placed so that hand lands on the box's top edge, which is what keeps it
         * on the lip instead of gripping thin air.
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
         */
        private val CLIMB_GRIP_CURVE = doubleArrayOf(
            0.000, 1.000,
            0.014, 0.958,
            0.039, 0.982,
            0.065, 1.007,
            0.091, 1.003,
            0.116, 0.999,
            0.143, 0.986,
            0.169, 0.929,
            0.195, 0.872,
            0.220, 0.831,
            0.247, 0.806,
            0.273, 0.794,
            0.299, 0.745,
            0.324, 0.688,
            0.350, 0.638,
            0.364, 0.610,
            0.390, 0.536,
            0.416, 0.409,
            0.441, 0.336,
            0.467, 0.278,
            0.494, 0.201,
            0.520, 0.127,
            0.545, 0.053,
            0.558, 0.025
        )

        /**
         * Real time is spent unevenly across the frames. Played straight the tail rushes, because
         * motion is not spread evenly through the clip: the hang is a long stretch of a character
         * barely moving, while the mantle and the stand-up pack large pose changes into the last
         * third. Frames-per-second is the wrong thing to hold constant - motion-per-second is.
         *
         * Keys, in raw frames:
         *   0.000-0.100 f70-94    hanging off the lip and adjusting grip (~0.28s)
         *   0.100-0.321 f94-136   the pull-up where body rise occurs (~0.62s)
         *   0.321-0.500 f136-163  swinging over the lip (mantle, ~0.50s)
         *   0.500-0.625 f163-179  settling into the crouch (~0.35s)
         *   0.625-0.893 f179-210  standing up smoothly (~0.75s)
         *   0.893-1.000 f210-224  finishing upright into idle (~0.30s)
         */
        private val CLIMB_PACING_CURVE = doubleArrayOf(
            0.000, 0.000,
            0.100, 0.158,
            0.321, 0.427,
            0.500, 0.603,
            0.625, 0.708,
            0.893, 0.906,
            1.000, 1.000
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
            0.000, 0.00,
            0.252, 0.00,
            0.357, 0.05,
            0.416, 0.12,
            0.474, 0.26,
            0.532, 0.52,
            0.568, 0.80,
            0.603, 1.00,
            1.000, 1.00
        )

        // ---- swing --------------------------------------------------------------------------
        // Phases below are in the swing clip's own frame space (48 frames, loaded index / 47),
        // and have to move together with PlayerAnimations.SWING_GRAB / SWING_RELEASE - they are
        // the same two frames named from the other side of the engine boundary, which Player
        // cannot import because game.model stays korlibs-free (see ZeroKorlibsLintTest).

        /** Frame 11 of 51, raw 77: the fist closes on the hook. */
        private const val SWING_GRAB_PHASE = 11.0 / 51.0

        /** Frame 29 of 51, raw 131: the last frame with the fist still overhead. */
        private const val SWING_RELEASE_PHASE = 29.0 / 51.0

        /** Frame 44.5 of 51: feet plant firmly on the far ledge. */
        private const val SWING_LAND_PHASE = 44.5 / 51.0

        /**
         * Where the hand goes on a hook rect, as fractions of `resources/hook.png`'s own bounds.
         * **Not the rect's bottom-centre**, which is what this used to be and why the first pass
         * had the character dangling under the hook without ever touching it: that art is mostly
         * chain, and its lowest pixel is the outside of the bend, so a fist placed there hangs a
         * whole fist below the metal.
         *
         * Both numbers are read off the image. Scanning down the hook, the point and the shank
         * stand as two separate runs from row 2040 of 2136 to row 2089, where they merge into the
         * solid bottom of the bend - that gap is the bell, the part a load actually sits in, and
         * 0.481 is its centre column.
         *
         * 0.960 rather than the bell's own top edge (0.946), which is where this went first. The
         * fist is narrower than the opening, so parked at the top of it the silhouette left a
         * sliver of sky either side and read as a fist near a hook rather than on one. Dropping it
         * two thirds of the way down puts it where the bell has closed to about the fist's own
         * width, so it meets the point on one side and the shank on the other and overlaps the
         * bend below - all invisible, since both are black, and all the difference between
         * holding the thing and hovering by it.
         *
         * **If hook.png is ever recropped or replaced, re-measure both** (`LevelData`'s
         * `hookHeight` hardcodes the same image's aspect and needs the same care).
         */
        const val HOOK_GRIP_X_FRACTION = 0.481
        const val HOOK_GRIP_Y_FRACTION = 0.960

        fun hookGripX(hook: Rect): Double = hook.left + hook.width * HOOK_GRIP_X_FRACTION
        fun hookGripY(hook: Rect): Double = hook.top + hook.height * HOOK_GRIP_Y_FRACTION

        /**
         * How far above the straight line from push-off to grip the launch bows, in world units.
         * The grip is only so far above the ledge - this game's camera shows about 140 units above
         * a high tier, which caps how far up a hook can hang and still have its chain visible - so
         * without a bow the leap covers ground without much leaving it.
         */
        private const val SWING_LAUNCH_ARC = 16.0

        /** Same idea on the way out: released forwards and slightly up, then down to the ledge. */
        private const val SWING_FLIGHT_ARC = 14.0

        /**
         * Height of the gripping hand above the character's own feet, as a fraction of player
         * height, measured off every frame from the grab to the release. The body is placed so
         * that hand lands on the hook, which is what keeps it on the hook rather than near it.
         *
         * It runs almost flat - 1.03 down to 0.92 - because the backswing, which is where the
         * knees came up hard enough to shorten this to 0.80, is no longer in the clip. What is
         * left is the forward half of the pendulum, over which the body straightens out.
         */
        private val SWING_GRIP_ABOVE_CURVE = doubleArrayOf(
            0.2157, 1.0303,
            0.2353, 0.9812,
            0.2549, 0.9771,
            0.2745, 0.9689,
            0.2941, 0.9689,
            0.3137, 0.9648,
            0.3333, 0.9607,
            0.3529, 0.9607,
            0.3725, 0.9566,
            0.3922, 0.9525,
            0.4118, 0.9484,
            0.4314, 0.9443,
            0.4510, 0.9403,
            0.4706, 0.9362,
            0.4902, 0.9321,
            0.5098, 0.9321,
            0.5294, 0.9280,
            0.5490, 0.9239,
            0.5686, 0.9157
        )

        /**
         * The same hand, measured the other way: how far ahead of the frame's own centre it sits,
         * as a fraction of player height. Near enough a constant - the plates were shot with the
         * camera on the hook, so the hand holds still while the body sweeps past it.
         *
         * Smoothed with a three-tap mean, unlike the curve above. The wobble left in the raw
         * measurement is the fist's topmost row picking a slightly different column frame to
         * frame, not the hand moving - it is worth half a world unit, and left in it reads as the
         * body shivering on the rope.
         */
        private val SWING_GRIP_AHEAD_CURVE = doubleArrayOf(
            0.2157, 0.0215,
            0.2353, 0.0191,
            0.2549, 0.0150,
            0.2745, 0.0164,
            0.2941, 0.0171,
            0.3137, 0.0177,
            0.3333, 0.0171,
            0.3529, 0.0177,
            0.3725, 0.0191,
            0.3922, 0.0211,
            0.4118, 0.0232,
            0.4314, 0.0246,
            0.4510, 0.0252,
            0.4706, 0.0239,
            0.4902, 0.0218,
            0.5098, 0.0198,
            0.5294, 0.0184,
            0.5490, 0.0177,
            0.5686, 0.0174
        )

        /**
         * Real time against frames, shaping when each frame is shown to produce a natural, athletic
         * movement: a full weighted takeoff leap, a smooth and clearly visible pendulum swing through
         * the hook into forward flight without waiting at the apex, and a grounded landing where the
         * body rolls naturally over the planted feet:
         *
         *   0.000-0.345 frames 0-11   running push-off and weighted leap curving up to hook (~0.38s)
         *   0.345-0.615 frames 11-29  natural pendulum swing through hook into forward flight (~0.30s)
         *   0.615-0.875 frames 29-45  ballistic flight arc across gap to touchdown (~0.28s)
         *   0.875-1.000 frames 45-51  firm ground contact, body roll over feet, and snap recovery (~0.14s)
         */
        private val SWING_PACING_CURVE = doubleArrayOf(
            // Takeoff: running push-off and clear, weighted jump into the hook (~0.38s, user approved)
            0.0000, 0.0000,
            0.1150, 3.5 / 51.0,
            0.2300, 7.2 / 51.0,
            0.3450, SWING_GRAB_PHASE,
            // Natural pendulum swing: smooth, clearly visible body swing through nadir and release (~0.30s)
            0.4250, 15.5 / 51.0,
            0.5000, 20.5 / 51.0,
            0.5650, 25.5 / 51.0,
            0.6150, SWING_RELEASE_PHASE, // release into flight!
            // Flight: ballistic fling across gap directly to touchdown (~0.28s)
            0.6950, 33.5 / 51.0,
            0.7650, 38.0 / 51.0,
            0.8250, 41.5 / 51.0,
            0.8750, SWING_LAND_PHASE, // Frame 44.5: feet plant on far ledge!
            // Ground recovery: natural body roll over planted feet and stand-up on platform (~0.14s)
            0.9400, 48.0 / 51.0,
            1.0000, 1.0000
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
