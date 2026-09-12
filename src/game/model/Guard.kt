package game.model

import kotlin.math.PI
import kotlin.math.abs

enum class GuardState {
    PATROL,
    INVESTIGATING
}

data class Guard(
    var x: Double,
    var y: Double,
    val width: Double = 26.0,
    val height: Double = 48.0,
    val patrolMinX: Double,
    val patrolMaxX: Double,
    var speed: Double = 70.0,
    var facing: Double = 1.0,
    var visionRange: Double = 260.0,
    var visionFov: Double = 60.0 * (PI / 180.0), // 60 degrees in radians
    var investigateDuration: Double = 2.5,
    var investigatePauseDuration: Double = 2.0,
    /**
     * How long the guard stands at each end of his patrol before turning back. 0 (every guard
     * before level 3's) turns on the spot the frame he arrives. With a pause he holds his
     * arriving facing for the whole dwell, so a post at the right end of the route is watched
     * to the right - see LEVEL_3_LAYOUT for the beat that builds on this.
     */
    val patrolPauseDuration: Double = 0.0
) {
    var state: GuardState = GuardState.PATROL
        private set

    var patrolFacing: Double = facing
        private set

    var targetInvestigateX: Double = x
        private set

    var isAtInvestigateTarget: Boolean = true
        private set

    var investigateTimer: Double = 0.0
        private set

    var investigatePauseTimer: Double = 0.0
        private set

    /** Time left standing at a patrol post before turning back; 0 while walking. */
    var patrolPauseTimer: Double = 0.0
        private set

    /** The facing to take when the current post dwell ends. */
    private var facingAfterPause: Double = facing

    /**
     * True on a frame the guard actually moved along his route - what GameplayScene keys the
     * walk animation on. False while dwelling at a post, investigating, blocked, or at speed 0.
     */
    var isWalking: Boolean = false
        private set

    val bounds: Rect get() = Rect(x, y, width, height)
    val center: Vec2d get() = Vec2d(x + width / 2.0, y + height / 2.0)

    val facingAngle: Double
        get() = if (facing >= 0.0) 0.0 else PI

    val eyePosition: Vec2d
        get() = Vec2d(
            if (facing >= 0.0) x + width - 4.0 else x + 4.0,
            y + 12.0
        )

    fun startInvestigating(targetX: Double, moveTowards: Boolean = false) {
        if (state == GuardState.PATROL) {
            patrolFacing = facing
        }
        state = GuardState.INVESTIGATING
        targetInvestigateX = targetX
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        investigatePauseTimer = 0.0
        if (targetInvestigateX > center.x) {
            facing = 1.0
        } else if (targetInvestigateX < center.x) {
            facing = -1.0
        }
    }

    fun onNoiseHeard(noiseX: Double) {
        if (state == GuardState.PATROL) {
            patrolFacing = facing
        }
        state = GuardState.INVESTIGATING
        targetInvestigateX = noiseX
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        investigatePauseTimer = 0.0
        if (noiseX > center.x) {
            facing = 1.0
        } else if (noiseX < center.x) {
            facing = -1.0
        }
    }

    fun onVisualLost(lastSeenX: Double) {
        startInvestigating(lastSeenX, moveTowards = false)
    }

    fun onPlayerSpottedWhileInvestigating(playerX: Double) {
        targetInvestigateX = playerX
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        if (targetInvestigateX > center.x) {
            facing = 1.0
        } else if (targetInvestigateX < center.x) {
            facing = -1.0
        }
    }

    fun returnToPatrol() {
        state = GuardState.PATROL
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        investigatePauseTimer = 0.0
        patrolPauseTimer = 0.0

        // Resume original route
        when {
            x <= patrolMinX -> {
                facing = 1.0
                patrolFacing = 1.0
            }
            x >= patrolMaxX -> {
                facing = -1.0
                patrolFacing = -1.0
            }
            else -> {
                facing = patrolFacing
            }
        }
    }

    fun update(dt: Double, obstacles: List<Rect> = emptyList()) {
        isWalking = false
        when (state) {
            GuardState.PATROL -> updatePatrol(dt, obstacles)
            GuardState.INVESTIGATING -> updateInvestigating(dt)
        }
    }

    private fun updatePatrol(dt: Double, obstacles: List<Rect>) {
        if (patrolPauseTimer > 0.0) {
            patrolPauseTimer -= dt
            if (patrolPauseTimer <= 0.0) {
                patrolPauseTimer = 0.0
                facing = facingAfterPause
                patrolFacing = facing
            }
            return
        }

        val before = x
        val targetX = x + facing * speed * dt
        val blocker = obstacles.firstOrNull { it.intersects(Rect(targetX, y, width, height)) }

        x = if (blocker != null) {
            val clampedX = if (facing > 0.0) blocker.left - width else blocker.right
            facing = -facing
            clampedX
        } else {
            targetX
        }
        patrolFacing = facing

        if (facing > 0.0 && x >= patrolMaxX) {
            x = patrolMaxX
            turnAtPost(-1.0)
        } else if (facing < 0.0 && x <= patrolMinX) {
            x = patrolMinX
            turnAtPost(1.0)
        }
        isWalking = x != before
    }

    /** Reached the end of the route: turn now, or stand the dwell out first and turn after. */
    private fun turnAtPost(newFacing: Double) {
        if (patrolPauseDuration > 0.0) {
            patrolPauseTimer = patrolPauseDuration
            facingAfterPause = newFacing
        } else {
            facing = newFacing
            patrolFacing = newFacing
        }
    }

    private fun updateInvestigating(dt: Double) {
        investigateTimer += dt

        if (investigateTimer >= investigateDuration) {
            returnToPatrol()
        }
    }
}
