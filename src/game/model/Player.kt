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
    var jumpSpeed: Double = -300.0
    var gravity: Double = 1000.0
    var maxFallSpeed: Double = 600.0

    var isCrouching: Boolean = false
    var currentNoiseLevel: NoiseLevel = NoiseLevel.SILENT
    val currentNoiseRadius: Double get() = currentNoiseLevel.radius
    val isMoving: Boolean get() = abs(vx) > 1.0

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
        platforms: List<Rect>
    ) {
        var remaining = dt
        val maxStep = 1.0 / 60.0
        var firstStep = true

        while (remaining > 1e-6) {
            val step = minOf(remaining, maxStep)
            updateStep(step, moveInput, if (firstStep) jumpInput else false, crouchInput, platforms)
            firstStep = false
            remaining -= step
        }
    }

    private fun updateStep(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        crouchInput: Boolean,
        platforms: List<Rect>
    ) {
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
                if (vx > 0.0) {
                    newX = minOf(newX, platform.left - width)
                    vx = 0.0
                } else if (vx < 0.0) {
                    newX = maxOf(newX, platform.right)
                    vx = 0.0
                } else {
                    val playerMidX = newX + width / 2.0
                    val platformMidX = platform.x + platform.width / 2.0
                    if (playerMidX <= platformMidX) {
                        newX = minOf(newX, platform.left - width)
                    } else {
                        newX = maxOf(newX, platform.right)
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

        for (platform in platforms) {
            if (vRect.intersects(platform)) {
                if (vy > 0.0) {
                    // Feet landing on platform
                    newY = minOf(newY, platform.top - height)
                    vy = 0.0
                    landed = true
                } else if (vy < 0.0) {
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
}
