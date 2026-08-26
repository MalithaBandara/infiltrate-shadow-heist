package game.model

data class GameWorld(
    val player: Player,
    val guard: Guard,
    val crate: Rect,
    val platforms: List<Rect>,
    val occluders: List<Rect>,
    val exitZone: Rect = Rect(x = 730.0, y = 320.0, width = 40.0, height = 60.0),
    var minDetectionTime: Double = 0.3,     // Seconds to catch at point-blank range (~0.3s)
    var maxDetectionTime: Double = 1.5,     // Seconds to catch at outer edge of vision cone (~1.5s)
    var alertDecayRate: Double = 0.6,       // Progress drained per second when outside vision
    var onSpotted: ((Guard, Player) -> Unit)? = null,
    var onLevelComplete: (() -> Unit)? = null
) {
    var isPlayerInVision: Boolean = false
        private set
    var alertProgress: Double = 0.0 // 0.0 (unnoticed) to 1.0 (caught)
        private set
    var isSpotted: Boolean = false
        private set
    var spottedCount: Int = 0
        private set
    var isLevelComplete: Boolean = false
        private set

    fun getDetectionTimeToCatch(distance: Double): Double {
        val range = guard.visionRange.coerceAtLeast(1.0)
        val normalizedDist = (distance / range).coerceIn(0.0, 1.0)
        return minDetectionTime + normalizedDist * (maxDetectionTime - minDetectionTime)
    }

    fun setUniformDetectionTime(time: Double) {
        minDetectionTime = time
        maxDetectionTime = time
    }

    fun update(dt: Double, moveInput: Double, jumpInput: Boolean) {
        if (isLevelComplete) return

        guard.update(dt)
        player.update(dt, moveInput, jumpInput, platforms + guard.bounds)

        // Check Exit / Win condition
        if (player.bounds.intersects(exitZone)) {
            isLevelComplete = true
            isPlayerInVision = false
            alertProgress = 0.0
            onLevelComplete?.invoke()
            return
        }

        // Check Guard Vision Detection with distance-scaled fill rate
        val spottedDist = VisionSystem.getPlayerSpottedDistance(guard, player, occluders)
        val inVision = spottedDist != null
        isPlayerInVision = inVision

        if (inVision && spottedDist != null) {
            val timeToCatch = getDetectionTimeToCatch(spottedDist)
            alertProgress = (alertProgress + dt / timeToCatch).coerceAtMost(1.0)
            if (alertProgress >= 1.0) {
                isSpotted = true
                spottedCount++
                alertProgress = 0.0
                println("[SPOTTED] Guard caught player at (${player.x.toInt()}, ${player.y.toInt()}) (distance: ${spottedDist.toInt()}px)! Total alerts: $spottedCount. Resetting to start...")
                onSpotted?.invoke(guard, player)
                player.resetToStart()
            } else {
                isSpotted = false
            }
        } else {
            isSpotted = false
            alertProgress = (alertProgress - alertDecayRate * dt).coerceAtLeast(0.0)
        }
    }

    companion object {
        fun createDefault(): GameWorld {
            val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 100.0)
            val leftWall = Rect(x = -30.0, y = 0.0, width = 30.0, height = 480.0)
            val rightWall = Rect(x = 800.0, y = 0.0, width = 30.0, height = 480.0)
            val crate = Rect(x = 320.0, y = 300.0, width = 60.0, height = 80.0)
            val exitZone = Rect(x = 730.0, y = 320.0, width = 40.0, height = 60.0)

            val platforms = listOf(ground, leftWall, rightWall, crate)
            val occluders = listOf(crate)

            val player = Player(
                x = 60.0,
                y = 380.0 - 48.0,
                startX = 60.0,
                startY = 380.0 - 48.0
            )

            val guard = Guard(
                x = 560.0,
                y = 380.0 - 48.0,
                patrolMinX = 460.0,
                patrolMaxX = 680.0,
                speed = 75.0,
                facing = -1.0 // Start facing left towards the crate
            )

            return GameWorld(
                player = player,
                guard = guard,
                crate = crate,
                platforms = platforms,
                occluders = occluders,
                exitZone = exitZone
            )
        }
    }
}
