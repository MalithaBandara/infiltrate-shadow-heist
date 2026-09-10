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
    val barrels: List<Rect> = emptyList(),
    // Jump-crate gap crossings, tagged by which of the two hanging-crate art variants each box
    // renders with - see LevelLayout.hangingCrateVariant1/2 and GameplayScene.kt's box loop.
    val hangingCrateVariant1: List<Rect> = emptyList(),
    val hangingCrateVariant2: List<Rect> = emptyList(),
    val movingPlatforms: List<MovingPlatform> = emptyList(),
    /** Overhead hooks the player can swing from - see LevelLayout.swingHooks and Player's swing. */
    val swingHooks: List<Rect> = emptyList(),
    /** Union of [truckParts] (front+middle+back) - the footprint the truck image is drawn into. */
    val truck: Rect? = null,
    /** Truck collision split into 3 tiers matching its silhouette: hood (front, low), cab roof
     *  (middle) and tarp-covered bed (back) - see GameWorld.createDefault for the measurements. */
    val truckParts: List<Rect> = emptyList(),
    var minDetectionTime: Double = 0.3,     // Seconds to catch at point-blank range (~0.3s)
    var maxDetectionTime: Double = 1.5,     // Seconds to catch at outer edge of vision cone (~1.5s)
    var alertDecayRate: Double = 0.6,       // Progress drained per second when outside vision
    var onSpotted: ((Guard, Player) -> Unit)? = null,
    var onLevelComplete: (() -> Unit)? = null,
    var onLevelCompleteResult: ((LevelResult) -> Unit)? = null,
    var onGameOver: (() -> Unit)? = null,
    val hasNoGuards: Boolean = false
) {
    /** Every guard in the level. Single-guard levels simply have no [extraGuards]. Guardless levels set [hasNoGuards] = true. */
    val allGuards: List<Guard>
        get() = if (hasNoGuards) emptyList() else if (extraGuards.isEmpty()) listOf(guard) else listOf(guard) + extraGuards

    /**
     * Reused buffer for the platform list handed to [Player.update] - the level's platforms plus
     * every guard's current bounds. Guards move, so this genuinely has to be rebuilt each frame,
     * but rebuilding it into one buffer beats allocating two fresh lists (`map`, then `+`) sixty
     * times a second. Safe to reuse because [Player] only iterates it and never retains it.
     */
    private val playerPlatformsScratch = ArrayList<Rect>()

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
        internal set
    var totalElapsedSeconds: Double = 0.0
        private set

    var lastCheckpointX: Double = player.startX
        private set
    var lastCheckpointY: Double = player.startY
        private set
    var hasAdvancedCheckpoint: Boolean = false
        private set
    var onCheckpointSecured: ((Double, Double) -> Unit)? = null

    fun respawnAtCheckpoint(): Boolean {
        isGameOver = false
        isSpotted = false
        alertProgress = 0.0
        detectingGuards = emptyList()
        detectingCameras = emptyList()
        recentlySeeingGuards.clear()
        player.resetTo(lastCheckpointX, lastCheckpointY)
        for (g in allGuards) g.returnToPatrol()
        activePowerups.invisibilityTimer = 2.0
        return true
    }

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

        // Safe checkpoint recording: update checkpoint when operative is safe on solid ground
        if (!isGameOver && player.isGrounded && !inVision && alertProgress == 0.0) {
            if (player.x > lastCheckpointX + 250.0) {
                lastCheckpointX = player.x
                lastCheckpointY = player.y
                hasAdvancedCheckpoint = true
                onCheckpointSecured?.invoke(lastCheckpointX, lastCheckpointY)
            }
        }

        totalElapsedSeconds += dt

        // Update moving platforms and translate grounded player if riding one
        for (mp in movingPlatforms) {
            val oldBounds = mp.bounds
            val delta = mp.update(dt, totalElapsedSeconds)
            val playerFeetY = player.y + player.height
            val footCenter = player.x + player.width / 2.0
            val onThisPlatform = player.isGrounded &&
                kotlin.math.abs(playerFeetY - oldBounds.top) < 4.5 &&
                (footCenter >= oldBounds.left && footCenter <= oldBounds.right)
            if (onThisPlatform) {
                player.x += delta.dx
                player.y += delta.dy
            }
        }

        // A level with no moving platforms - which is most of them, level 1 included - reuses its
        // own immutable lists rather than copying all three every frame. `platforms + movingBounds`
        // builds a fresh ArrayList even when movingBounds is empty, so this was three full list
        // copies per frame handing back identical contents.
        val hasMovingPlatforms = movingPlatforms.isNotEmpty()
        val movingBounds = if (hasMovingPlatforms) movingPlatforms.map { it.bounds } else emptyList()
        val currentPlatforms = if (hasMovingPlatforms) platforms + movingBounds else platforms
        val currentBoxes = if (hasMovingPlatforms) boxes + movingBounds else boxes
        val currentOccluders = if (hasMovingPlatforms) occluders + movingBounds else occluders

        // Guards without eyes on the player keep walking their route (unless asleep from Phantom Cloak)
        if (!isGameOver && !activePowerups.isPhantomCloakActive) {
            for (g in allGuards) {
                if (g !in seeingGuards) g.update(dt, currentOccluders)
            }
        }

        playerPlatformsScratch.clear()
        playerPlatformsScratch.addAll(currentPlatforms)
        for (g in allGuards) playerPlatformsScratch.add(g.bounds)
        player.update(dt, moveInput, jumpInput, crouchInput, playerPlatformsScratch, currentBoxes, swingHooks)

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

            val worldWidth = 3900.0
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

            // 1. Ground walk past the gates to a small step crate (tightly cropped to visual bounds:
            // 48px high, 68px wide) parked next to a flatbed truck.
            val crateHeight = 48.0
            val crateWidth = 68.0
            val smallCrate = Rect(x = 550.0, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // 2. Truck, starting right at the crate's edge, split into 3 collision tiers matching
            // truck.png's actual silhouette (tight-cropped to 1683x617) instead of one flat box:
            // the hood (front, the low point right after the crate - climbable step up), the cab
            // roof (middle) and the tarp-covered bed (back) - the latter two flush with the long
            // platform's height (96px) so walking across bed+cab onto the platform is a level walk,
            // not another jump. The image itself is drawn flipped (see GameplayScene.kt) so the
            // hood - the low, climbable end - faces the crate the player is coming from, with the
            // tall cab+bed stretching away towards the long platform.
            val truckBedHeight = 96.0
            val truckFrontHeight = 66.0 // hood sits at ~68% of the cab/bed roofline height
            val truckFront = Rect(x = smallCrate.right, y = groundY - truckFrontHeight, width = 38.0, height = truckFrontHeight)
            val truckMiddle = Rect(x = truckFront.right, y = groundY - truckBedHeight, width = 45.0, height = truckBedHeight)
            val truckBack = Rect(x = truckMiddle.right, y = groundY - truckBedHeight, width = 179.0, height = truckBedHeight)
            val truckParts = listOf(truckFront, truckMiddle, truckBack)
            val truck = Rect(x = truckFront.x, y = groundY - truckBedHeight, width = truckFront.width + truckMiddle.width + truckBack.width, height = truckBedHeight)

            // 3. Long elevated platform (96px high, starting right at the truck's far edge)
            val longPlatform = Rect(x = truck.right, y = groundY - 96.0, width = 900.0, height = 96.0)

            // 4. Chained crate hanging above the long platform (blocks standing player, crouch to pass
            // under). Clearance above the platform is 58px (lowered down for a tight, thrilling squeeze
            // that closely hugs the crouching silhouette while cleanly clearing the 56px crouch collision height).
            val hangingChainedCrate = Rect(x = longPlatform.x + 400.0, y = 0.0, width = 174.0, height = (groundY - 96.0) - 58.0)

            // 5. Step-down crate right at the platform's far edge (same 48x68 dims as the step-up
            // crate at the start) - the player climbs DOWN off the 96px platform in two 48px steps
            // (platform -> crate top -> ground) instead of one big drop. Plain ground walk after it,
            // no barrels here anymore - they've moved to bridge the block2->block3 gap below instead.
            val stepDownCrate = Rect(x = longPlatform.right, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // Further terrain blocks along the infiltration corridor
            val block2 = Rect(x = stepDownCrate.right + 200.0, y = groundY - 95.0, width = 340.0, height = 95.0)

            // 6. Barrels tiled edge-to-edge across the entire block2 -> block3 gap (48px high - same
            // jumpable rise as the crates above, comfortably inside the ~51 unit max jump height) -
            // jump up onto the first one, walk across the whole row, jump down onto block3 at the far
            // end. block3 starts exactly where the last barrel ends, so there's no bare ground left
            // in the gap at all.
            val barrelHeight = 48.0
            val barrelWidth = 32.0 // NOT derived from barrel.png - the image is stretched to fit this box
            val barrelCount = 7 // 7 * 32 = 224, comfortably spanning the ~210 unit gap this replaces
            val barrels = (0 until barrelCount).map { i ->
                Rect(x = block2.right + i * barrelWidth, y = groundY - barrelHeight, width = barrelWidth, height = barrelHeight)
            }
            val block3 = Rect(x = barrels.last().right, y = groundY - 95.0, width = 300.0, height = 95.0)
            val boxes = listOf(fence2, fence1, smallCrate) + truckParts +
                listOf(longPlatform, hangingChainedCrate, stepDownCrate, block2) +
                barrels + listOf(block3)
            // Wide enough to span almost the entire entrance.png checkpoint booth visual
            // (GameplayScene.kt renders it at this same x, left-aligned) rather than a narrow
            // strip somewhere inside it - so touching any part of the visible structure ends the
            // level immediately, instead of needing to find one small precisely-aligned spot.
            // 160 matches entrance.png's own tight-cropped aspect ratio (531x612) at the height
            // GameplayScene.kt renders it, minus a small margin.
            val exitZone = Rect(x = levelData.guardPatrolMaxX + 90.0, y = groundY - 60.0, width = 160.0, height = 60.0)

            val platforms = listOf(ground, leftWall, rightWall) + boxes
            val occluders = boxes

            val player = Player(
                x = 235.0,
                y = groundY - 96.0,
                startX = 235.0,
                startY = groundY - 96.0
            )

            // guardPatrolMinX..guardPatrolMaxX (open ground, no boxes) is also where several unit
            // tests in GameplayModelTest.kt place a player/guard pair that needs generic open
            // ground for collision/vision math unrelated to this level's actual story - reusing it
            // means those tests don't have to fight over space with the story geometry above.
            //
            // guard is never null - it's a mandatory GameWorld field, and a lot of existing tests
            // call GameWorld.createDefault() and then reuse this exact object for generic guard-
            // mechanic testing, either repositioning only part of it (just .x, relying on a normal
            // .y/.visionRange/.facing) or not touching it at all (e.g. `world.player.x =
            // world.guard.x - 100.0`, expecting a real, sensibly-oriented guard on the other end).
            // So when a level opts out via guardEnabled = false, everything about the guard stays
            // normal EXCEPT its starting x, which goes to -500 - behind the level's own leftWall
            // (x = -30) and the start fences, i.e. permanently unreachable/off-screen during real
            // play, but still real open ground with no occluders nearby for the vision math the
            // dynamic tests exercise. speed = 0 keeps it from ever drifting into the visible corridor
            // over a long session; patrolMinX/MaxX are widened (not left at the level's own, narrow
            // guardPatrolMinX/MaxX) so a test that repositions .x anywhere in the real corridor
            // doesn't get silently clamped back to -500 by Guard's own patrol-bounds logic the next
            // time update() runs.
            val guard = if (levelData.guardEnabled) {
                Guard(
                    x = (levelData.guardPatrolMaxX - 20.0).coerceIn(levelData.guardPatrolMinX, levelData.guardPatrolMaxX),
                    y = groundY - 48.0,
                    patrolMinX = levelData.guardPatrolMinX,
                    patrolMaxX = levelData.guardPatrolMaxX,
                    speed = levelData.guardSpeed,
                    facing = -1.0 // Start facing left towards the corridor
                )
            } else {
                Guard(
                    x = -500.0,
                    y = groundY - 48.0,
                    patrolMinX = -10000.0,
                    patrolMaxX = 10000.0,
                    speed = 0.0,
                    facing = -1.0
                )
            }

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
                crate = smallCrate,
                platforms = platforms,
                occluders = occluders,
                exitZone = exitZone,
                levelData = levelData,
                cameras = cameras,
                boxes = boxes,
                worldWidth = worldWidth,
                fence1 = fence1,
                fence2 = fence2,
                barrels = barrels,
                truck = truck,
                truckParts = truckParts
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
            val primaryGuard = guards.firstOrNull() ?: Guard(
                x = -500.0,
                y = groundY - 48.0,
                patrolMinX = -10000.0,
                patrolMaxX = 10000.0,
                speed = 0.0,
                facing = -1.0,
                visionRange = 0.0
            )
            val extraGuards = if (guards.isNotEmpty()) guards.drop(1) else emptyList()

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

            val movingPlatforms = layout.movingPlatforms.map { def ->
                MovingPlatform(
                    id = def.id,
                    width = def.width,
                    height = def.height,
                    minX = def.minX,
                    maxX = def.maxX,
                    minY = def.minY,
                    maxY = def.maxY,
                    periodSeconds = def.periodSeconds,
                    phaseOffsetSeconds = def.phaseOffsetSeconds,
                    isVariant1 = def.isVariant1,
                    initialX = def.initialX,
                    initialY = def.initialY
                )
            }

            return GameWorld(
                player = player,
                guard = primaryGuard,
                crate = layout.boxes.firstOrNull() ?: Rect(0.0, 0.0, 0.0, 0.0),
                platforms = platforms,
                occluders = occluders,
                exitZone = layout.exitZone,
                levelData = levelData,
                extraGuards = extraGuards,
                cameras = cameras,
                boxes = allBoxes,
                worldWidth = layout.worldWidth,
                fence1 = fence1,
                fence2 = fence2,
                hangingCrateVariant1 = layout.hangingCrateVariant1,
                hangingCrateVariant2 = layout.hangingCrateVariant2,
                barrels = layout.barrels,
                movingPlatforms = movingPlatforms,
                swingHooks = layout.swingHooks,
                hasNoGuards = guards.isEmpty()
            )
        }
    }
}
