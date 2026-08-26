package game.model

data class Player(
    var x: Double,
    var y: Double,
    val width: Double = 26.0,
    val height: Double = 48.0,
    val startX: Double = x,
    val startY: Double = y
) {
    var vx: Double = 0.0
    var vy: Double = 0.0
    var isGrounded: Boolean = false

    var moveSpeed: Double = 180.0
    var jumpSpeed: Double = -420.0
    var gravity: Double = 1000.0
    var maxFallSpeed: Double = 600.0

    val bounds: Rect get() = Rect(x, y, width, height)
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0
    val center: Vec2d get() = Vec2d(centerX, centerY)

    val keyPoints: List<Vec2d>
        get() = listOf(
            Vec2d(centerX, y + 8.0),             // Head
            Vec2d(centerX, centerY),             // Torso
            Vec2d(centerX, y + height - 6.0)     // Feet
        )

    fun resetToStart() {
        x = startX
        y = startY
        vx = 0.0
        vy = 0.0
        isGrounded = false
    }

    fun update(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        platforms: List<Rect>
    ) {
        var remaining = dt
        val maxStep = 1.0 / 60.0
        var firstStep = true

        while (remaining > 1e-6) {
            val step = minOf(remaining, maxStep)
            updateStep(step, moveInput, if (firstStep) jumpInput else false, platforms)
            firstStep = false
            remaining -= step
        }
    }

    private fun updateStep(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        platforms: List<Rect>
    ) {
        // Horizontal velocity
        vx = moveInput.coerceIn(-1.0, 1.0) * moveSpeed

        // Jump & Vertical acceleration
        if (jumpInput && isGrounded) {
            vy = jumpSpeed
            isGrounded = false
        }
        vy = (vy + gravity * dt).coerceAtMost(maxFallSpeed)

        // Integrate X movement & check horizontal collisions
        val targetX = x + vx * dt
        val hRect = Rect(targetX, y, width, height)
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

        // Integrate Y movement & check vertical collisions
        val targetY = y + vy * dt
        val vRect = Rect(x, targetY, width, height)
        var newY = targetY
        var landed = false

        for (platform in platforms) {
            if (vRect.intersects(platform)) {
                if (vy > 0.0) {
                    newY = minOf(newY, platform.top - height)
                    vy = 0.0
                    landed = true
                } else if (vy < 0.0) {
                    newY = maxOf(newY, platform.bottom)
                    vy = 0.0
                } else {
                    val playerMidY = newY + height / 2.0
                    val platformMidY = platform.y + platform.height / 2.0
                    if (playerMidY < platformMidY) {
                        newY = minOf(newY, platform.top - height)
                        landed = true
                    } else {
                        newY = maxOf(newY, platform.bottom)
                    }
                }
            }
        }
        y = newY
        isGrounded = landed
    }
}
