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
    val cameras: List<Camera> = emptyList(),
    val boxes: List<Rect> = listOf(crate),
    val worldWidth: Double = 800.0,
    val activePowerups: ActivePowerups = ActivePowerups(),
    val fence1: Rect? = null,
    val fence2: Rect? = null,
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

    /**
     * The guards and cameras that have eyes on the player right now. The alert itself is a single
     * world-level number (the closest detector fills it), but the HUD draws its detection meter on
     * whoever is doing the detecting, so it needs to know which entities those are - not just that
     * someone is. Both are empty whenever the player is unseen.
     */
    var detectingGuards: List<Guard> = emptyList()
        private set
    var detectingCameras: List<Camera> = emptyList()
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

    fun activatePowerup(type: PowerupType): Boolean {
        if (isLevelComplete || isGameOver) return false
        activePowerups.activate(type)
        return true
    }

    fun getDetectionTimeToCatch(distance: Double, range: Double = guard.visionRange): Double {
        val r = range.coerceAtLeast(1.0)
        val normalizedDist = (distance / r).coerceIn(0.0, 1.0)
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

        // Update active powerup timers
        activePowerups.update(dt)

        // Update cameras (continuous sweep) - paused while Smoke Screen is active
        if (!activePowerups.isSmokeScreenActive) {
            for (c in cameras) {
                c.update(dt)
            }
        }

        // Check every guard and camera's vision cone; the closest one with eyes on the player fills the alert.
        val previousAlert = alertProgress
        val seeingGuards = ArrayList<Guard>(allGuards.size)
        val seeingCameras = ArrayList<Camera>(cameras.size)
        var spottedDist: Double? = null
        var detectorRange: Double = guard.visionRange

        // Invisibility: player cannot be spotted by any guard or camera
        if (!activePowerups.isInvisibilityActive) {
            // Guards vision checks - skipped if Phantom Cloak puts guards to sleep
            if (!activePowerups.isPhantomCloakActive) {
                for (g in allGuards) {
                    val d = VisionSystem.getPlayerSpottedDistance(g, player, occluders)
                    if (d != null) {
                        seeingGuards.add(g)
                        if (spottedDist == null || d < spottedDist) {
                            spottedDist = d
                            detectorRange = g.visionRange
                        }
                    }
                }
            }

            // Cameras vision checks - skipped if Smoke Screen disables cameras
            if (!activePowerups.isSmokeScreenActive) {
                for (c in cameras) {
                    val d = VisionSystem.getPlayerSpottedDistance(c, player, occluders)
                    if (d != null) {
                        seeingCameras.add(c)
                        if (spottedDist == null || d < spottedDist) {
                            spottedDist = d
                            detectorRange = c.visionRange
                        }
                    }
                }
            }
        }

        val inVision = spottedDist != null
        isPlayerInVision = inVision
        detectingGuards = if (inVision) seeingGuards.toList() else emptyList()
        detectingCameras = if (inVision) seeingCameras.toList() else emptyList()

        if (inVision && spottedDist != null) {
            recentlySeeingGuards.addAll(seeingGuards)
            // A guard with eyes on the player tracks them instead of resuming its route
            for (g in seeingGuards) {
                if (g.state == GuardState.INVESTIGATING) {
                    g.onPlayerSpottedWhileInvestigating(player.x)
                }
            }
            val timeToCatch = getDetectionTimeToCatch(spottedDist, detectorRange)
            alertProgress = (alertProgress + dt / timeToCatch).coerceAtMost(1.0)
            if (alertProgress >= 1.0) {
                isSpotted = true
                spottedCount++
                wasDetected = true
                alertProgress = 0.0
                println("[SPOTTED] Player caught at (${player.x.toInt()}, ${player.y.toInt()}) (distance: ${spottedDist.toInt()}px)! Total alerts: $spottedCount. Resetting to start...")
                onSpotted?.invoke(seeingGuards.firstOrNull() ?: allGuards.first(), player)
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
            if (previousAlert > 0.0 && recentlySeeingGuards.isNotEmpty() && !activePowerups.isPhantomCloakActive) {
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

        // Guards without eyes on the player keep walking their route (unless asleep from Phantom Cloak)
        if (!isGameOver && !activePowerups.isPhantomCloakActive) {
            for (g in allGuards) {
                if (g !in seeingGuards) g.update(dt, occluders)
            }
        }

        player.update(dt, moveInput, jumpInput, crouchInput, platforms + allGuards.map { it.bounds }, boxes)

        // Check Exit / Win condition
        if (player.bounds.intersects(exitZone)) {
            isLevelComplete = true
            isPlayerInVision = false
            alertProgress = 0.0
            detectingGuards = emptyList()
            detectingCameras = emptyList()
            onLevelComplete?.invoke()
            onLevelCompleteResult?.invoke(getLevelResult())
            return
        }

        // Check Movement Noise Detection (blocked by solid occluders, same as vision line-of-sight)
        // Level-duration Noise Suppression keeps movement completely silent regardless of walk/crouch
        val effectiveNoiseRadius = if (activePowerups.isNoiseSuppressed) 0.0 else player.currentNoiseRadius
        if (effectiveNoiseRadius > 0.0 && !activePowerups.isPhantomCloakActive) {
            for (g in allGuards) {
                val distToGuard = player.center.distanceTo(g.center)
                if (distToGuard <= effectiveNoiseRadius &&
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

            val worldWidth = 3200.0
            val groundY = 410.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)
            val leftWall = Rect(x = -30.0, y = 0.0, width = 30.0, height = 520.0)
            val rightWall = Rect(x = worldWidth, y = 0.0, width = 30.0, height = 520.0)

            // 0. Security perimeter fences at the start of each level (solid impassable boundary blocking leftward movement)
            val fenceHeight = 140.0
            val fence2Width = 172.0
            val fence1Width = 151.0
            val fence2 = Rect(x = -80.0, y = groundY - fenceHeight, width = fence2Width, height = fenceHeight)
            val fence1 = Rect(x = 70.0, y = groundY - fenceHeight, width = fence1Width, height = fenceHeight)

            // 1. Initial ground walking area (x = 235 to 580)
            // 2. Step crate (tightly cropped to visual bounds: 48px high, 68px wide)
            val crateHeight = 48.0
            val crateWidth = 68.0
            val stepCrate = Rect(x = 580.0, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // 3. Long elevated platform (96px high, starting right at crate edge)
            val longPlatform = Rect(x = stepCrate.right, y = groundY - 96.0, width = 900.0, height = 96.0)

            // 4. Chained crate hanging above long platform (blocks standing player at x = 1050, crouch to pass under)
            // Clearance above the platform is 66px. It has to clear the crouch SILHOUETTE, not the 56px
            // crouch collision height: the tallest crouch-walk frame measures 57.4px on screen, so 66
            // leaves ~9px of daylight - enough that nothing clips, tight enough that the squeeze reads.
            // (80px, the previous value, left 23px of headroom and looked like the player could walk
            // under it standing; 54px, the value before that, was shorter than the crouch itself.)
            val hangingChainedCrate = Rect(x = 1050.0, y = 0.0, width = 174.0, height = (groundY - 96.0) - 66.0)

            // Further terrain blocks along the 3200px infiltration corridor
            val block2 = Rect(x = 1800.0, y = groundY - 95.0, width = 340.0, height = 95.0)
            val block3 = Rect(x = 2350.0, y = groundY - 95.0, width = 300.0, height = 95.0)
            val boxes = listOf(fence2, fence1, stepCrate, longPlatform, hangingChainedCrate, block2, block3)
            val exitZone = Rect(x = 3130.0, y = groundY - 60.0, width = 40.0, height = 60.0)

            val platforms = listOf(ground, leftWall, rightWall) + boxes
            val occluders = boxes

            val player = Player(
                x = 235.0,
                y = groundY - 96.0,
                startX = 235.0,
                startY = groundY - 96.0
            )

            val guard = Guard(
                x = (levelData.guardPatrolMaxX - 20.0).coerceIn(levelData.guardPatrolMinX, levelData.guardPatrolMaxX),
                y = groundY - 48.0,
                patrolMinX = levelData.guardPatrolMinX,
                patrolMaxX = levelData.guardPatrolMaxX,
                speed = levelData.guardSpeed,
                facing = -1.0 // Start facing left towards the corridor
            )

            val cameras = levelData.cameras.map { spawn ->
                Camera(
                    x = spawn.x,
                    y = spawn.y,
                    minAngle = spawn.minAngle,
                    maxAngle = spawn.maxAngle,
                    currentAngle = spawn.startAngle,
                    sweepSpeed = spawn.sweepSpeed,
                    visionRange = spawn.visionRange,
                    visionFov = spawn.visionFov,
                    sweepDirection = spawn.sweepDirection
                )
            }

            return GameWorld(
                player = player,
                guard = guard,
                crate = stepCrate,
                platforms = platforms,
                occluders = occluders,
                exitZone = exitZone,
                levelData = levelData,
                cameras = cameras,
                boxes = boxes,
                worldWidth = worldWidth,
                fence1 = fence1,
                fence2 = fence2
            )
        }

        /** Builds a world from an explicit multi-tier LevelLayout (see LevelData.layout). */
        fun createFromLayout(levelData: LevelData, layout: LevelLayout): GameWorld {
            val leftWall = Rect(x = -30.0, y = -400.0, width = 30.0, height = 1200.0)
            val rightWall = Rect(x = layout.worldWidth, y = -400.0, width = 30.0, height = 1200.0)

            val groundY = layout.platforms.firstOrNull { it.y > 300.0 }?.y ?: 440.0
            val fenceHeight = 140.0
            val fence2Width = 172.0
            val fence1Width = 151.0
            val fence2 = layout.fence2 ?: Rect(x = -80.0, y = groundY - fenceHeight, width = fence2Width, height = fenceHeight)
            val fence1 = layout.fence1 ?: Rect(x = 70.0, y = groundY - fenceHeight, width = fence1Width, height = fenceHeight)

            val allBoxes = if (layout.boxes.contains(fence1) || layout.boxes.contains(fence2)) {
                layout.boxes
            } else {
                listOf(fence2, fence1) + layout.boxes
            }

            val platforms = layout.platforms + allBoxes + listOf(leftWall, rightWall)
            // Floors and boxes both block sight, so no guard can see through a storey.
            val occluders = layout.platforms + allBoxes

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

            val cameras = (layout.cameras.ifEmpty { levelData.cameras }).map { spawn ->
                Camera(
                    x = spawn.x,
                    y = spawn.y,
                    minAngle = spawn.minAngle,
                    maxAngle = spawn.maxAngle,
                    currentAngle = spawn.startAngle,
                    sweepSpeed = spawn.sweepSpeed,
                    visionRange = spawn.visionRange,
                    visionFov = spawn.visionFov,
                    sweepDirection = spawn.sweepDirection
                )
            }

            return GameWorld(
                player = player,
                guard = guards.first(),
                crate = layout.boxes.firstOrNull() ?: Rect(0.0, 0.0, 0.0, 0.0),
                platforms = platforms,
                occluders = occluders,
                exitZone = layout.exitZone,
                levelData = levelData,
                extraGuards = guards.drop(1),
                cameras = cameras,
                boxes = allBoxes,
                worldWidth = layout.worldWidth,
                fence1 = fence1,
                fence2 = fence2
            )
        }
    }
}
