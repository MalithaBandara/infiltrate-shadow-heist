package game.model

data class GameWorld(
    val player: Player,
    val guard: Guard,
    val crate: Rect,
    val platforms: List<Rect>,
    val occluders: List<Rect>,
    val exitZone: Rect = Rect(x = 730.0, y = 320.0, width = 40.0, height = 60.0),
    val levelData: LevelData = LevelData(),
    val extraGuards: List<Guard> = emptyList(),
    val boxes: List<Rect> = listOf(crate),
    val worldWidth: Double = 800.0,
    var minDetectionTime: Double = 0.3,     // Seconds to catch at point-blank range (~0.3s)
    var maxDetectionTime: Double = 1.5,     // Seconds to catch at outer edge of vision cone (~1.5s)
    var alertDecayRate: Double = 0.6,       // Progress drained per second when outside vision
    var onSpotted: ((Guard, Player) -> Unit)? = null,
    var onLevelComplete: (() -> Unit)? = null,
    var onLevelCompleteResult: ((LevelResult) -> Unit)? = null,
    var onGameOver: (() -> Unit)? = null
) {
    /** Every guard in the level. Single-guard levels simply have no [extraGuards]. */
    val allGuards: List<Guard> = if (extraGuards.isEmpty()) listOf(guard) else listOf(guard) + extraGuards

    var isPlayerInVision: Boolean = false
        private set
    var alertProgress: Double = 0.0 // 0.0 (unnoticed) to 1.0 (caught)
        private set
    var isSpotted: Boolean = false
        private set
    var spottedCount: Int = 0
        private set
    var wasDetected: Boolean = false
        private set
    var timeTaken: Float = 0.0f
        private set
    var isLevelComplete: Boolean = false
        private set
    var isGameOver: Boolean = false
        private set

    private val recentlySeeingGuards = LinkedHashSet<Guard>()

    fun getDetectionTimeToCatch(distance: Double): Double {
        val range = guard.visionRange.coerceAtLeast(1.0)
        val normalizedDist = (distance / range).coerceIn(0.0, 1.0)
        return minDetectionTime + normalizedDist * (maxDetectionTime - minDetectionTime)
    }

    fun setUniformDetectionTime(time: Double) {
        minDetectionTime = time
        maxDetectionTime = time
    }

    fun getLevelResult(): LevelResult {
        return LevelResult(
            levelId = levelData.id,
            completed = isLevelComplete,
            wasDetected = wasDetected,
            timeTaken = timeTaken,
            timeTargetSeconds = levelData.timeTargetSeconds
        )
    }

    fun update(dt: Double, moveInput: Double, jumpInput: Boolean) {
        update(dt, moveInput, jumpInput, crouchInput = false)
    }

    fun update(dt: Double, moveInput: Double, jumpInput: Boolean, crouchInput: Boolean) {
        if (isLevelComplete || isGameOver) return

        timeTaken += dt.toFloat()

        // Check every guard's vision cone; the closest one with eyes on the player fills the alert.
        val previousAlert = alertProgress
        val seeingGuards = ArrayList<Guard>(allGuards.size)
        var spottedDist: Double? = null
        for (g in allGuards) {
            val d = VisionSystem.getPlayerSpottedDistance(g, player, occluders)
            if (d != null) {
                seeingGuards.add(g)
                if (spottedDist == null || d < spottedDist) spottedDist = d
            }
        }
        val inVision = spottedDist != null
        isPlayerInVision = inVision

        if (inVision && spottedDist != null) {
            recentlySeeingGuards.addAll(seeingGuards)
            // A guard with eyes on the player tracks them instead of resuming its route
            for (g in seeingGuards) {
                if (g.state == GuardState.INVESTIGATING) {
                    g.onPlayerSpottedWhileInvestigating(player.x)
                }
            }
            val timeToCatch = getDetectionTimeToCatch(spottedDist)
            alertProgress = (alertProgress + dt / timeToCatch).coerceAtMost(1.0)
            if (alertProgress >= 1.0) {
                isSpotted = true
                spottedCount++
                wasDetected = true
                alertProgress = 0.0
                println("[SPOTTED] Guard caught player at (${player.x.toInt()}, ${player.y.toInt()}) (distance: ${spottedDist.toInt()}px)! Total alerts: $spottedCount. Resetting to start...")
                onSpotted?.invoke(seeingGuards.first(), player)
                isGameOver = true
                onGameOver?.invoke()
                for (g in allGuards) g.returnToPatrol()
                recentlySeeingGuards.clear()
                player.resetToStart()
            } else {
                isSpotted = false
            }
        } else {
            isSpotted = false
            // Lost visual mid-alert -> only the guards who saw the player investigate where the player last was
            if (previousAlert > 0.0 && recentlySeeingGuards.isNotEmpty()) {
                for (g in recentlySeeingGuards) {
                    if (g.state == GuardState.PATROL) g.onVisualLost(player.x)
                }
                recentlySeeingGuards.clear()
            }
            alertProgress = (alertProgress - alertDecayRate * dt).coerceAtLeast(0.0)
            if (alertProgress == 0.0) {
                recentlySeeingGuards.clear()
            }
        }

        // Guards without eyes on the player keep walking their route
        if (!isGameOver) {
            for (g in allGuards) {
                if (g !in seeingGuards) g.update(dt, occluders)
            }
        }

        player.update(dt, moveInput, jumpInput, crouchInput, platforms + allGuards.map { it.bounds })

        // Check Exit / Win condition
        if (player.bounds.intersects(exitZone)) {
            isLevelComplete = true
            isPlayerInVision = false
            alertProgress = 0.0
            onLevelComplete?.invoke()
            onLevelCompleteResult?.invoke(getLevelResult())
            return
        }

        // Check Movement Noise Detection (blocked by solid occluders, same as vision line-of-sight)
        if (player.currentNoiseRadius > 0.0) {
            for (g in allGuards) {
                val distToGuard = player.center.distanceTo(g.center)
                if (distToGuard <= player.currentNoiseRadius &&
                    GeometryUtils.hasLineOfSight(player.center, g.center, occluders)
                ) {
                    g.onNoiseHeard(player.x)
                }
            }
        }
    }

    companion object {
        fun createDefault(levelData: LevelData = LevelData.DEFAULT_LEVEL_1): GameWorld {
            val layout = levelData.layout
            if (layout != null) return createFromLayout(levelData, layout)

            val ground = Rect(x = 0.0, y = 380.0, width = 800.0, height = 100.0)
            val leftWall = Rect(x = -30.0, y = 0.0, width = 30.0, height = 480.0)
            val rightWall = Rect(x = 800.0, y = 0.0, width = 30.0, height = 480.0)
            val crate = Rect(x = 320.0, y = 300.0, width = 60.0, height = 80.0)
            val exitZone = Rect(x = 730.0, y = 320.0, width = 40.0, height = 60.0)

            val platforms = listOf(ground, leftWall, rightWall, crate)
            val occluders = listOf(crate)

            val player = Player(
                x = 60.0,
                y = 380.0 - 96.0,
                startX = 60.0,
                startY = 380.0 - 96.0
            )

            val guard = Guard(
                x = (levelData.guardPatrolMaxX - 20.0).coerceIn(levelData.guardPatrolMinX, levelData.guardPatrolMaxX),
                y = 380.0 - 48.0,
                patrolMinX = levelData.guardPatrolMinX,
                patrolMaxX = levelData.guardPatrolMaxX,
                speed = levelData.guardSpeed,
                facing = -1.0 // Start facing left towards the crate
            )

            return GameWorld(
                player = player,
                guard = guard,
                crate = crate,
                platforms = platforms,
                occluders = occluders,
                exitZone = exitZone,
                levelData = levelData
            )
        }

        /** Builds a world from an explicit multi-tier LevelLayout (see LevelData.layout). */
        fun createFromLayout(levelData: LevelData, layout: LevelLayout): GameWorld {
            val leftWall = Rect(x = -30.0, y = -400.0, width = 30.0, height = 1200.0)
            val rightWall = Rect(x = layout.worldWidth, y = -400.0, width = 30.0, height = 1200.0)

            val platforms = layout.platforms + layout.boxes + listOf(leftWall, rightWall)
            // Floors and boxes both block sight, so no guard can see through a storey.
            val occluders = layout.platforms + layout.boxes

            val player = Player(
                x = layout.playerStartX,
                y = layout.playerStartY,
                startX = layout.playerStartX,
                startY = layout.playerStartY
            )

            val guards = layout.guards.map { spawn ->
                Guard(
                    x = spawn.startX,
                    y = spawn.surfaceY - 48.0,
                    patrolMinX = spawn.patrolMinX,
                    patrolMaxX = spawn.patrolMaxX,
                    speed = spawn.speed,
                    facing = spawn.facing,
                    visionRange = spawn.visionRange
                )
            }
            require(guards.isNotEmpty()) { "Level layout must define at least one guard" }

            return GameWorld(
                player = player,
                guard = guards.first(),
                crate = layout.boxes.firstOrNull() ?: Rect(0.0, 0.0, 0.0, 0.0),
                platforms = platforms,
                occluders = occluders,
                exitZone = layout.exitZone,
                levelData = levelData,
                extraGuards = guards.drop(1),
                boxes = layout.boxes,
                worldWidth = layout.worldWidth
            )
        }
    }
}
