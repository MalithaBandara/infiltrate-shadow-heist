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
    /** Subset of [cameras] (same instances) rendered with a reduced alpha, e.g. a pole-mounted
     *  camera meant to read as visually distinct from a beam-mounted one - see LevelLayout.translucentCameras. */
    val translucentCameras: List<Camera> = emptyList(),
    val boxes: List<Rect> = listOf(crate),
    val worldWidth: Double = 800.0,
    val activePowerups: ActivePowerups = ActivePowerups(),
    val fence1: Rect? = null,
    val fence2: Rect? = null,
    val barrels: List<Rect> = emptyList(),
    /** Boxes drawn with woodcrate2.png - see LevelLayout.woodCrates and GameplayScene.kt's box loop. */
    val woodCrates: List<Rect> = emptyList(),
    /** Freestanding mounting poles (pole.png), not in [boxes] (no collision) - see LevelLayout.poles. */
    val poles: List<Rect> = emptyList(),
    /** Background cranes (CraneDef.bounds also in [boxes]) - solid/climbable across their whole
     *  footprint - see LevelLayout.cranes. */
    val cranes: List<CraneDef> = emptyList(),
    /** A box forced to render as a plain structural block, bypassing GameplayScene.kt's
     *  crate-shaped size heuristics - see LevelLayout.plainPlatforms. */
    val plainPlatforms: List<Rect> = emptyList(),
    /** Tables (table.png) - the art rects. See LevelLayout.tables and GameplayScene.kt's box loop. */
    val tables: List<Rect> = emptyList(),
    /** Collision boxes covered by a table's art, drawn by nothing - see LevelLayout.tableParts. */
    val tableParts: List<Rect> = emptyList(),
    /** Purely decorative table pieces, no collision - see LevelLayout.tableDecorations. */
    val tableDecorations: List<Rect> = emptyList(),
    /** Boxes climbable despite being a floating ledge - see LevelLayout.floatingClimbTargets. */
    val floatingClimbTargets: List<Rect> = emptyList(),
    /** Boxes the player may not mantle onto at all - see LevelLayout.unclimbableBoxes. */
    val unclimbableBoxes: List<Rect> = emptyList(),
    // Jump-crate gap crossings, tagged by which of the two hanging-crate art variants each box
    // renders with - see LevelLayout.hangingCrateVariant1/2 and GameplayScene.kt's box loop.
    val staticHangingCrateVariant1: List<Rect> = emptyList(),
    val staticHangingCrateVariant2: List<Rect> = emptyList(),
    val movingPlatforms: List<MovingPlatform> = emptyList(),
    /** Overhead hooks the player can swing from - see LevelLayout.swingHooks and Player's swing. */
    val swingHooks: List<Rect> = emptyList(),
    val levers: List<Lever> = emptyList(),
    val hookCrates: List<HookCrate> = emptyList(),
    /** Conveyor belts in the level - see LevelLayout.conveyors. */
    val conveyors: List<ConveyorDef> = emptyList(),
    val conveyorCrates: List<ConveyorCrate> = emptyList(),
    val lasers: List<Laser> = emptyList(),
    val fans: List<VentFan> = emptyList(),
    val cameraBots: List<CameraBot> = emptyList(),
    val steamPipes: List<SteamPipe> = emptyList(),
    val playerStartCrouched: Boolean = false,
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
    val hasNoGuards: Boolean = false,
    val restartOnConveyorFallOff: Boolean = false,
    var onConveyorFallOff: (() -> Unit)? = null,
    var onLaserHit: (() -> Unit)? = null,
    var onHangingCrateHit: (() -> Unit)? = null,
    var onLaserShieldBlocked: (() -> Unit)? = null,
    var onSteamPipeHit: (() -> Unit)? = null,
    var onCameraBotDeactivated: ((CameraBot) -> Unit)? = null,
    var onCheckpointAutoRespawn: (() -> Unit)? = null,
    var spawnGraceTimer: Double = 0.0,
    val conveyorsStartOnMove: Boolean = false,
    val canClimb: Boolean = true,
    val manualCheckpoints: List<Checkpoint> = emptyList(),
    /** See LevelLayout.pushStanceDemo - the push-stance dev stage only, nothing shipped. */
    val pushStanceDemo: Boolean = false
) {
    val canInteract: Boolean
        get() = levers.any { !it.isActivated && it.isPlayerInRange(player) } ||
                cameraBots.any { !it.isDeactivated && it.canDeactivate(player) } ||
                // Nothing to be in range of in the push-stance level, so the button is simply
                // always live there; without this the scene's own `interactPressed` is gated
                // off by canInteract before the toggle below ever sees it.
                (pushStanceDemo && !isGameOver && !isLevelComplete)
    val hangingCrateVariant1: List<Rect>
        get() = staticHangingCrateVariant1 + conveyorCrates.filter { it.isHanging && it.isVariant1 }.map { it.bounds }
    val hangingCrateVariant2: List<Rect>
        get() = staticHangingCrateVariant2 + conveyorCrates.filter { it.isHanging && !it.isVariant1 }.map { it.bounds }

    constructor(
        player: Player,
        guard: Guard,
        crate: Rect,
        platforms: List<Rect>,
        occluders: List<Rect>,
        exitZone: Rect = Rect(x = 730.0, y = 320.0, width = 40.0, height = 60.0),
        levelData: LevelData = LevelData(),
        extraGuards: List<Guard> = emptyList(),
        cameras: List<Camera> = emptyList(),
        translucentCameras: List<Camera> = emptyList(),
        boxes: List<Rect> = listOf(crate),
        worldWidth: Double = 800.0,
        activePowerups: ActivePowerups = ActivePowerups(),
        fence1: Rect? = null,
        fence2: Rect? = null,
        barrels: List<Rect> = emptyList(),
        woodCrates: List<Rect> = emptyList(),
        poles: List<Rect> = emptyList(),
        cranes: List<CraneDef> = emptyList(),
        plainPlatforms: List<Rect> = emptyList(),
        tables: List<Rect> = emptyList(),
        tableParts: List<Rect> = emptyList(),
        tableDecorations: List<Rect> = emptyList(),
        floatingClimbTargets: List<Rect> = emptyList(),
        unclimbableBoxes: List<Rect> = emptyList(),
        hangingCrateVariant1: List<Rect> = emptyList(),
        hangingCrateVariant2: List<Rect> = emptyList(),
        movingPlatforms: List<MovingPlatform> = emptyList(),
        swingHooks: List<Rect> = emptyList(),
        levers: List<Lever> = emptyList(),
        hookCrates: List<HookCrate> = emptyList(),
        conveyors: List<ConveyorDef> = emptyList(),
        conveyorCrates: List<ConveyorCrate> = emptyList(),
        lasers: List<Laser> = emptyList(),
        truck: Rect? = null,
        truckParts: List<Rect> = emptyList(),
        hasNoGuards: Boolean = false,
        restartOnConveyorFallOff: Boolean = false,
        conveyorsStartOnMove: Boolean = false,
        canClimb: Boolean = true,
        manualCheckpoints: List<Checkpoint> = emptyList(),
        fans: List<VentFan> = emptyList(),
        cameraBots: List<CameraBot> = emptyList(),
        steamPipes: List<SteamPipe> = emptyList(),
        playerStartCrouched: Boolean = false,
        pushStanceDemo: Boolean = false
    ) : this(
        player = player,
        guard = guard,
        crate = crate,
        platforms = platforms,
        occluders = occluders,
        exitZone = exitZone,
        levelData = levelData,
        extraGuards = extraGuards,
        cameras = cameras,
        translucentCameras = translucentCameras,
        boxes = boxes,
        worldWidth = worldWidth,
        activePowerups = activePowerups,
        fence1 = fence1,
        fence2 = fence2,
        barrels = barrels,
        woodCrates = woodCrates,
        poles = poles,
        cranes = cranes,
        plainPlatforms = plainPlatforms,
        tables = tables,
        tableParts = tableParts,
        tableDecorations = tableDecorations,
        floatingClimbTargets = floatingClimbTargets,
        unclimbableBoxes = unclimbableBoxes,
        staticHangingCrateVariant1 = hangingCrateVariant1,
        staticHangingCrateVariant2 = hangingCrateVariant2,
        movingPlatforms = movingPlatforms,
        swingHooks = swingHooks,
        levers = levers,
        hookCrates = hookCrates,
        conveyors = conveyors,
        conveyorCrates = conveyorCrates,
        lasers = lasers,
        fans = fans,
        cameraBots = cameraBots,
        steamPipes = steamPipes,
        playerStartCrouched = playerStartCrouched,
        truck = truck,
        truckParts = truckParts,
        hasNoGuards = hasNoGuards,
        restartOnConveyorFallOff = restartOnConveyorFallOff,
        conveyorsStartOnMove = conveyorsStartOnMove,
        canClimb = canClimb,
        manualCheckpoints = manualCheckpoints,
        pushStanceDemo = pushStanceDemo
    )
    var conveyorsActive: Boolean = !conveyorsStartOnMove
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
    var detectingCameraBots: List<CameraBot> = emptyList()
        private set
    var alertProgress: Double = 0.0 // 0.0 (unnoticed) to 1.0 (caught)
        private set
    var isSpotted: Boolean = false
        private set
    var spottedCount: Int = 0
        private set
    var wasDetected: Boolean = false
        private set
    /** True from the player's first crouch onward this attempt - see Guard.holdUntilPlayerCrouches. */
    var hasPlayerCrouchedOnce: Boolean = false
        private set
    var timeTaken: Float = 0.0f
        private set
    var isLevelComplete: Boolean = false
        private set
    var isGameOver: Boolean = false
        internal set

    /**
     * True while the run is on hold and nothing about it should advance: the pause overlay is up,
     * the app has been backgrounded, or a full-screen ad is covering gameplay. [update] is a no-op
     * while it is set, so `timeTaken` (the number the win/fail card shows and the star-3 target is
     * judged against) counts only time the player could actually act on.
     *
     * GameplayScene's updater already returns early while paused, so this is deliberately
     * belt-and-braces rather than the sole mechanism: the scene is not the only caller (the native
     * shells drive the KorGE loop on their own lifecycle, and on Android the loop keeps running
     * under the Compose menu - see .junie/guidelines.md real-device bug #7), and a clock that can
     * only be advanced by a frame the player saw is much easier to keep honest than one that
     * depends on every future call site remembering to check a scene-local flag.
     */
    var isSuspended: Boolean = false

    var totalElapsedSeconds: Double = 0.0
        private set
    var laserGraceTimer: Double = 0.0

    /** Desktop-only debug cheat (see GameplayScene's F1 handler, gated on Platform.isJvm): free
     *  flight through the level, ignoring gravity/collision/detection, for level-layout inspection. */
    var noclipFlying: Boolean = false

    // ---- push stance (pushStanceDemo levels only - see LevelLayout.pushStanceDemo) ----------
    //
    // The whole stance lives here rather than in GameplayScene so it is testable without a
    // KorGE canvas, the same split every other move uses: the model owns when the character is
    // braced and how far through the brace he is, the scene owns which frame that draws as.
    //
    // pushStanceBlend is the shared clock for both directions: it runs 0 -> 1 while leaning in
    // and 1 -> 0 while standing back up, so the scene can play one clip forward and the same
    // clip in reverse off a single number. That is the crouch clip's own arrangement, and it is
    // why standing up out of a half-finished lean-in starts from where the lean actually got to
    // instead of snapping to the braced pose first.

    /** True from the moment INTERACT is pressed until it is pressed again. */
    var isPushStanceHeld: Boolean = false
        private set

    /** 0 = upright, 1 = fully braced. Drives both directions of the transition clip. */
    var pushStanceBlend: Double = 0.0
        private set

    /** Only once fully braced does the push gait run - before that he is still leaning in. */
    val isPushing: Boolean get() = isPushStanceHeld && pushStanceBlend >= 1.0

    /** Nothing at all is happening with the stance: the scene hands back to idle/walk. */
    val isPushStanceIdle: Boolean get() = !isPushStanceHeld && pushStanceBlend <= 0.0

    private var pushInteractWasDown: Boolean = false

    private fun resetPushStance() {
        isPushStanceHeld = false
        pushStanceBlend = 0.0
        pushInteractWasDown = false
    }

    // ---- wind stance (level 7's exhaust fans) ----------------------------------------------
    //
    // Same split as the push stance above and for the same reason: the model owns whether the
    // character is braced against the gale and how far into the brace he is, the scene owns
    // which frame of resources/player/wind{transition,walk} that draws as.

    /** True while the player's box overlaps any fan's wind zone. */
    var isInWindZone: Boolean = false
        private set

    /** 0 = upright, 1 = fully leaning into the wind. Drives both directions of the clip. */
    var windStanceBlend: Double = 0.0
        private set

    /** True while the player is rapidly spamming forward taps, pushing through the airflow at normal speed. */
    var isWindSpamming: Boolean = false
        private set

    private var fanTapCount: Int = 0
    private var fanTapWindowTimer: Double = 0.0
    private var fanSpamActiveTimer: Double = 0.0

    /** Fully folded into the gale. The braced pose is HELD here - the gait only runs while
     *  [isWindPushing]. */
    val isWindBraced: Boolean get() = windStanceBlend >= 1.0

    /**
     * The player is actively driving forward against the wind right now - i.e. spamming forward taps.
     * Standing in the airflow or pressing once / holding forward does nothing.
     */
    val isWindPushing: Boolean get() = isWindSpamming

    /** Nothing happening: the scene hands the sprite back to idle/walk. */
    val isWindStanceIdle: Boolean get() = !isInWindZone && !isWindSpamming && windStanceBlend <= 0.0

    /**
     * Forward speed (u/s) while spam-clicking through airflow.
     */
    var fanSurgeSpeed: Double = 0.0
        private set

    /**
     * Net ground speed (u/s, unsigned) while in a wind zone - what actually happened to `player.x` this frame.
     */
    var windGroundSpeed: Double = 0.0
        private set

    private var windPrevPlayerX: Double = 0.0
    private var windForwardDir: Double = 1.0
    private var fanIntentTimer: Double = 0.0

    private fun resetWindStance() {
        isInWindZone = false
        windStanceBlend = 0.0
        fanSurgeSpeed = 0.0
        windGroundSpeed = 0.0
        windPrevPlayerX = player.x
        windForwardDir = 1.0
        fanIntentTimer = 0.0
        isWindSpamming = false
        fanTapCount = 0
        fanTapWindowTimer = 0.0
        fanSpamActiveTimer = 0.0
    }

    var continueCount: Int = 0
        private set
    val canContinue: Boolean
        get() = activePowerups.isCheckpointsActive || continueCount < 1

    var currentManualCheckpointIndex: Int = -1
        private set

    var lastCheckpointX: Double = player.startX
        private set
    var lastCheckpointY: Double = player.startY
        private set
    var hasAdvancedCheckpoint: Boolean = false
        private set
    var onCheckpointSecured: ((Double, Double) -> Unit)? = null

    fun respawnAtCheckpoint(): Boolean {
        if (!canContinue) return false
        continueCount++
        isGameOver = false
        isSpotted = false
        alertProgress = 0.0
        detectingGuards = emptyList()
        detectingCameras = emptyList()
        detectingCameraBots = emptyList()
        recentlySeeingGuards.clear()
        player.resetTo(lastCheckpointX, lastCheckpointY)
        for (g in allGuards) g.returnToPatrol()
        for (c in cameras) c.reset()
        for (mp in movingPlatforms) mp.reset()
        for (crate in conveyorCrates) crate.reset()
        for (laser in lasers) laser.reset()
        for (lever in levers) lever.reset()
        for (hc in hookCrates) hc.reset()
        for (b in cameraBots) b.reset()
        for (f in fans) f.reset()
        for (p in steamPipes) p.reset()
        activePowerups.invisibilityTimer = 3.0
        laserGraceTimer = 3.0
        spawnGraceTimer = 3.0
        resetPushStance()
        resetWindStance()
        if (playerStartCrouched) player.isCrouching = true
        return true
    }

    /**
     * Instantly resets the level to its initial state without loading screen or scene rebuild.
     */
    fun restartLevel() {
        if (activePowerups.isCheckpointsActive) {
            respawnAtCheckpoint()
            onCheckpointAutoRespawn?.invoke()
            return
        }
        continueCount = 0
        currentManualCheckpointIndex = -1
        isGameOver = false
        isLevelComplete = false
        isSpotted = false
        alertProgress = 0.0
        spottedCount = 0
        wasDetected = false
        hasPlayerCrouchedOnce = false
        timeTaken = 0.0f
        totalElapsedSeconds = 0.0
        // A restart always resumes: whatever put the run on hold (pause overlay, backgrounded
        // app, ad) is over by the time anything asks for a fresh attempt, and a stuck flag here
        // would freeze the new run outright.
        isSuspended = false
        detectingGuards = emptyList()
        detectingCameras = emptyList()
        detectingCameraBots = emptyList()
        recentlySeeingGuards.clear()
        player.resetToStart()
        lastCheckpointX = player.startX
        lastCheckpointY = player.startY
        hasAdvancedCheckpoint = false
        for (g in allGuards) g.returnToPatrol()
        for (c in cameras) c.reset()
        for (mp in movingPlatforms) mp.reset()
        for (crate in conveyorCrates) crate.reset()
        for (laser in lasers) laser.reset()
        for (lever in levers) lever.reset()
        for (hc in hookCrates) hc.reset()
        for (b in cameraBots) b.reset()
        for (f in fans) f.reset()
        for (p in steamPipes) p.reset()
        activePowerups.invisibilityTimer = 0.0
        laserGraceTimer = 0.0
        resetPushStance()
        resetWindStance()
        if (playerStartCrouched) player.isCrouching = true
        conveyorsActive = !conveyorsStartOnMove
    }

    /**
     * INTERACT toggles the braced push stance, and [pushStanceBlend] runs between the two poses.
     *
     * Edge-detected here rather than in the scene because the scene passes the raw button LEVEL
     * (`interactPressed` is `keys[E] && canInteract`, true for every frame the key is down), the
     * same value the lever and camera-bot loops above consume - those are idempotent, a toggle
     * is not, and reading the level directly would flip the stance every frame of one press.
     */
    /**
     * Exhaust fans: the wind pushback, the tap-to-advance surge, and the wind stance clock.
     *
     * The surge is the whole reason this is not two lines any more. Tapping used to move
     * `player.x` by a flat 10 units on the frame of the press, which is a teleport - four of
     * them a second at a human tapping rate, and no amount of shrinking the number makes a jump
     * smooth. A tap now adds to [fanSurgeSpeed], a forward VELOCITY that decays exponentially,
     * so the same press is spread over the ~0.45s that follow and the player slides forward
     * instead of snapping. The decay is also what carries them across the gap between taps,
     * which is the job `fanPushbackDampenTimer` used to do by weakening the wind instead; that
     * timer is gone and the wind now blows at a constant strength, which is one fewer thing
     * making the motion lumpy.
     *
     * Speeds are deliberately lower than the old arrangement's. See [FAN_TAP_IMPULSE] for the
     * equilibrium arithmetic and `testFanTapAdvanceIsSmoothAndSlowerThanTheOldImpulse` for the
     * simulated check - this was measured against the real loop, not derived on paper.
     */
    private fun updateFans(dt: Double, forwardTap: Boolean, moveInput: Double) {
        var inWind = false
        var windDx = 0.0
        var justPassedAnyFan = false
        for (fan in fans) {
            fan.update(dt)
            if (fan.isPlayerInWind(player)) {
                inWind = true
                windForwardDir = if (fan.windDirection < 0.0) 1.0 else -1.0
                windDx += fan.windDirection * fan.windPushSpeed * dt
            }
            // When fan blows backwards (windDirection < 0.0), passing the fan means crossing player.x >= fan.x.
            // Bounded to [fan.x, fan.x + 120.0] so fans further down the level are not affected.
            if (fan.windDirection < 0.0 && player.x >= fan.x && player.x <= fan.x + 120.0) {
                justPassedAnyFan = true
            }
        }
        isInWindZone = inWind

        // Once past a fan, immediately zero out any spam state so the player does not rocket / lunge forward
        if (justPassedAnyFan) {
            isWindSpamming = false
            fanSurgeSpeed = 0.0
            fanTapCount = 0
            fanTapWindowTimer = 0.0
            fanSpamActiveTimer = 0.0
        }

        // Tap timing and spam detection:
        // A single press or long-press does NOTHING. Only rapid consecutive taps (spamming) move the player forward.
        if (fanTapWindowTimer > 0.0) {
            fanTapWindowTimer = (fanTapWindowTimer - dt).coerceAtLeast(0.0)
            if (fanTapWindowTimer <= 0.0) {
                fanTapCount = 0
            }
        }
        if (fanSpamActiveTimer > 0.0) {
            fanSpamActiveTimer = (fanSpamActiveTimer - dt).coerceAtLeast(0.0)
            if (fanSpamActiveTimer <= 0.0) {
                isWindSpamming = false
            }
        }

        if ((inWind || (windStanceBlend > 0.0 && !justPassedAnyFan)) && forwardTap) {
            fanTapCount++
            fanTapWindowTimer = FAN_SPAM_TAP_WINDOW
            if (fanTapCount >= 2) {
                isWindSpamming = true
                fanSpamActiveTimer = FAN_SPAM_ACTIVE_WINDOW
            }
        }

        if (isWindSpamming && !justPassedAnyFan) {
            fanSurgeSpeed = WIND_SPAM_SPEED
            // When spam clicking front: ignores the effect of wind and moves forward at normal speed
            player.x = (player.x + windForwardDir * WIND_SPAM_SPEED * dt)
                .coerceIn(0.0, worldWidth - player.width)
        } else {
            fanSurgeSpeed = 0.0
            // When not spamming, if in the wind zone, the wind pushback applies (moving player backward)
            if (inWind) {
                player.x = (player.x + windDx)
                    .coerceIn(0.0, worldWidth - player.width)
            }
        }

        windGroundSpeed = if (inWind && dt > 0.0) kotlin.math.abs(player.x - windPrevPlayerX) / dt else 0.0
        windPrevPlayerX = player.x

        // Braced while the gale is on them. When past the fan, blend upright quickly.
        val braced = inWind && !justPassedAnyFan
        val rate = if (braced) dt / WIND_STANCE_ENTER_SECONDS else -dt / WIND_STANCE_EXIT_SECONDS
        windStanceBlend = (windStanceBlend + rate).coerceIn(0.0, 1.0)
    }

    private fun updatePushStance(dt: Double, interactInput: Boolean) {
        if (!pushStanceDemo) return
        if (interactInput && !pushInteractWasDown) {
            isPushStanceHeld = !isPushStanceHeld
        }
        pushInteractWasDown = interactInput

        val rate = if (isPushStanceHeld) dt / PUSH_STANCE_ENTER_SECONDS else -dt / PUSH_STANCE_EXIT_SECONDS
        pushStanceBlend = (pushStanceBlend + rate).coerceIn(0.0, 1.0)
    }

    private val recentlySeeingGuards = LinkedHashSet<Guard>()

    /** Activates a lever exactly as walking up and pressing interact would - shared by the normal
     *  in-range interact path and REMOTE_TRIGGER's remote one below. */
    private fun triggerLever(lever: Lever) {
        lever.isActivated = true
        if (lever.targetMechanismId != null) {
            for (hc in hookCrates) {
                if (hc.id == lever.targetMechanismId) {
                    hc.isDetached = true
                }
            }
            for (mp in movingPlatforms) {
                if (mp.id == lever.targetMechanismId) {
                    mp.activate()
                }
            }
            // A laser carrying this mechanism id is cut for the rest of the run - see
            // LaserDef.mechanismId. Several beams share one id on purpose: LEVEL_6_LAYOUT's exit
            // curtain is three lasers and one switch.
            for (laser in lasers) {
                if (laser.mechanismId == lever.targetMechanismId) {
                    laser.disable()
                }
            }
        }
    }

    /** Whether REMOTE_TRIGGER has anything to do right now - checked by the UI before it spends
     *  the item, so a level with no levers (or one where they're all already thrown) never burns
     *  one for nothing. */
    fun hasRemoteTriggerTarget(): Boolean = levers.any { !it.isActivated }

    fun activatePowerup(type: PowerupType): Boolean {
        if (isLevelComplete || isGameOver) return false
        // Already running (a timed effect mid-countdown, or a level-duration one already on) -
        // refuse rather than reset its clock/charges, so a stray extra press/key/tap can't shave
        // time off (or silently no-op waste) an effect that's already active.
        if (activePowerups.isActive(type)) return false
        if (type == PowerupType.REMOTE_TRIGGER) {
            // One-shot, no timer/charge of its own (ActivePowerups.activate() is a no-op for it -
            // this IS its whole effect): remotely throws the nearest lever the player hasn't
            // already reached, exactly as if they'd walked up and pressed interact on it, without
            // needing to be in range. See StoreScreen.kt's own description of the item.
            val target = levers.filter { !it.isActivated }
                .minByOrNull { player.center.distanceTo(Vec2d(it.centerX, it.centerY)) }
                ?: return false
            triggerLever(target)
            return true
        }
        activePowerups.activate(type)
        if (type == PowerupType.SMOKE_SCREEN) {
            for (c in cameras) c.resetDetectionPause()
        }
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
        update(dt, moveInput, jumpInput, crouchInput = false, interactInput = false)
    }

    fun update(dt: Double, moveInput: Double, jumpInput: Boolean, crouchInput: Boolean) {
        update(dt, moveInput, jumpInput, crouchInput = crouchInput, interactInput = false)
    }

    fun update(
        dt: Double,
        moveInput: Double,
        jumpInput: Boolean,
        crouchInput: Boolean,
        interactInput: Boolean,
        forwardTap: Boolean = false
    ) {
        if (isSuspended || isLevelComplete || isGameOver) return

        if (noclipFlying) {
            val flySpeed = 420.0
            val dx = moveInput.coerceIn(-1.0, 1.0) * flySpeed * dt
            val dy = when {
                jumpInput && !crouchInput -> -flySpeed * dt
                crouchInput && !jumpInput -> flySpeed * dt
                else -> 0.0
            }
            player.vx = 0.0
            player.vy = 0.0
            player.isGrounded = false
            val groundY = platforms.firstOrNull { it.y > 300.0 }?.y ?: 440.0
            player.x = (player.x + dx).coerceIn(0.0, worldWidth - player.width)
            player.y = (player.y + dy).coerceAtMost(groundY - player.height)
            return
        }

        if (spawnGraceTimer > 0.0) {
            spawnGraceTimer = (spawnGraceTimer - dt).coerceAtLeast(0.0)
        }

        if (interactInput) {
            for (lever in levers) {
                if (!lever.isActivated && lever.isPlayerInRange(player)) {
                    triggerLever(lever)
                }
            }
            for (bot in cameraBots) {
                if (!bot.isDeactivated && bot.canDeactivate(player)) {
                    bot.deactivate()
                    onCameraBotDeactivated?.invoke(bot)
                }
            }
        }

        updatePushStance(dt, interactInput)

        updateFans(dt, forwardTap, moveInput)

        for (bot in cameraBots) {
            bot.update(dt)
        }

        val groundY = platforms.firstOrNull { it.y > 300.0 }?.y ?: 440.0
        for (hc in hookCrates) {
            hc.update(dt, 1000.0, groundY)
        }

        if (!conveyorsActive && (moveInput != 0.0 || jumpInput)) {
            conveyorsActive = true
        }

        if (crouchInput) hasPlayerCrouchedOnce = true

        timeTaken += dt.toFloat()

        // Update active powerup timers
        activePowerups.update(dt)

        // Check every guard, camera, and camera bot's vision cone; the closest one with eyes on the player fills the alert.
        val previousAlert = alertProgress
        val seeingGuards = ArrayList<Guard>(allGuards.size)
        val seeingCameras = ArrayList<Camera>(cameras.size)
        val seeingCameraBots = ArrayList<CameraBot>(cameraBots.size)
        var spottedDist: Double? = null
        var detectorRange: Double = guard.visionRange

        // Invisibility: player cannot be spotted by any guard or camera
        if (!activePowerups.isInvisibilityActive && spawnGraceTimer <= 0.0) {
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
                        c.onPlayerSpotted()
                        seeingCameras.add(c)
                        if (spottedDist == null || d < spottedDist) {
                            spottedDist = d
                            detectorRange = c.visionRange
                        }
                    }
                }
                for (b in cameraBots) {
                    if (!b.isDeactivated) {
                        val d = VisionSystem.getPlayerSpottedDistance(
                            eye = b.eyePosition,
                            facingAngle = b.facingAngle,
                            visionRange = b.visionRange,
                            visionFov = b.visionFov,
                            player = player,
                            occluders = occluders
                        )
                        if (d != null) {
                            seeingCameraBots.add(b)
                            if (spottedDist == null || d < spottedDist) {
                                spottedDist = d
                                detectorRange = b.visionRange
                            }
                        }
                    }
                }
            }
        }

        val inVision = spottedDist != null
        isPlayerInVision = inVision
        detectingGuards = if (inVision) seeingGuards.toList() else emptyList()
        detectingCameras = if (inVision) seeingCameras.toList() else emptyList()
        detectingCameraBots = if (inVision) seeingCameraBots.toList() else emptyList()
        for (c in cameras) {
            if (c !in seeingCameras) {
                c.onVisualLost()
            }
        }

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
                // firstOrNull, not first: a level can have cameras and no guards at all (see
                // LEVEL_8_LAYOUT's pole camera), and allGuards is empty on those. Kotlin's `?.`
                // short-circuits before evaluating arguments, so the old `allGuards.first()`
                // only escaped being a crash because nothing currently assigns onSpotted.
                val spotter = seeingGuards.firstOrNull() ?: allGuards.firstOrNull()
                if (spotter != null) onSpotted?.invoke(spotter, player)
                isGameOver = true
                onGameOver?.invoke()
                for (g in allGuards) g.returnToPatrol()
                for (c in cameras) c.resetDetectionPause()
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
            if (manualCheckpoints.isEmpty()) {
                if (player.x > lastCheckpointX + 250.0) {
                    lastCheckpointX = player.x
                    lastCheckpointY = player.y
                    hasAdvancedCheckpoint = true
                    onCheckpointSecured?.invoke(lastCheckpointX, lastCheckpointY)
                }
            } else {
                for (i in manualCheckpoints.indices) {
                    val cp = manualCheckpoints[i]
                    val zone = cp.triggerZone ?: Rect(cp.x - 20.0, cp.y - 20.0, 40.0, player.height + 40.0)
                    if (player.bounds.intersects(zone) && i > currentManualCheckpointIndex) {
                        currentManualCheckpointIndex = i
                        lastCheckpointX = cp.x
                        lastCheckpointY = cp.y
                        hasAdvancedCheckpoint = true
                        onCheckpointSecured?.invoke(lastCheckpointX, lastCheckpointY)
                    }
                }
            }
        }

        totalElapsedSeconds += dt
        if (laserGraceTimer > 0.0) {
            laserGraceTimer = (laserGraceTimer - dt).coerceAtLeast(0.0)
        }
        for (laser in lasers) laser.update(totalElapsedSeconds)

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

        // A swinging load kills whatever it catches under it - see
        // MovingPlatformDef.crushesOnContact. Only from below: the player's feet have to be under
        // the crate's own underside, so standing on top of a platform is still standing on a
        // platform. Same presentation as the conveyor crates' crush below.
        if (!isGameOver && !isLevelComplete) {
            val pBounds = player.bounds
            for (mp in movingPlatforms) {
                if (!mp.crushesOnContact) continue
                if (pBounds.intersects(mp.bounds) && pBounds.bottom > mp.bounds.bottom) {
                    isGameOver = true
                    onGameOver?.invoke()
                    onHangingCrateHit?.invoke()
                    return
                }
            }
        }

        // A lever whose target is a one-shot moving platform comes back up (isActivated = false,
        // repressable) the instant that platform re-arms (mp.isActive drops back to false after
        // its single period finishes) - see MovingPlatformDef.oneShot. Harmless no-op for every
        // other lever: a never-pulled lever's target is already inactive (nothing to reset), and a
        // continuously-looping (non-oneShot) platform's isActive never goes false once thrown.
        for (lever in levers) {
            if (!lever.isActivated || lever.targetMechanismId == null) continue
            val target = movingPlatforms.firstOrNull { it.id == lever.targetMechanismId }
            if (target != null && target.startsInactive && !target.isActive) {
                lever.isActivated = false
            }
        }

        // Conveyor belts carry grounded player standing on them, and move conveyor crates
        if (conveyorsActive) {
            for (conveyor in conveyors) {
                val bounds = conveyor.bounds
                val playerFeetY = player.y + player.height
                val footCenter = player.x + player.width / 2.0
                val onConveyor = player.isGrounded &&
                    kotlin.math.abs(playerFeetY - bounds.top) < 4.5 &&
                    (footCenter >= bounds.left && footCenter <= bounds.right)
                if (onConveyor) {
                    player.x += conveyor.speed * dt
                }
            }
            val conveyorSpeed = conveyors.firstOrNull()?.speed ?: -45.0
            for (crate in conveyorCrates) {
                val crateDx = conveyorSpeed * dt * crate.speedMultiplier
                val oldBounds = crate.bounds
                crate.update(crateDx, totalElapsedSeconds)
                val playerFeetY = player.y + player.height
                val footCenter = player.x + player.width / 2.0
                val onThisCrate = player.isGrounded &&
                    kotlin.math.abs(playerFeetY - oldBounds.top) < 4.5 &&
                    (footCenter >= oldBounds.left && footCenter <= oldBounds.right)
                if (!crate.isHanging && onThisCrate) {
                    player.x += crateDx
                }
            }
        }

        // Check Hanging Crate Downward Crushing Collisions before player movement resolution
        // Player only dies if the crate was moving down and touched the player
        if (!isGameOver && !isLevelComplete) {
            for (crate in conveyorCrates) {
                if (crate.isHanging && crate.isMovingDown) {
                    val cBounds = crate.bounds
                    val pBounds = player.bounds
                    val horizontalOverlap = pBounds.right > cBounds.left + 4.0 && pBounds.left < cBounds.right - 4.0
                    val verticalOverlap = pBounds.top <= cBounds.bottom && pBounds.bottom >= cBounds.bottom
                    if (horizontalOverlap && verticalOverlap) {
                        isGameOver = true
                        onGameOver?.invoke()
                        onHangingCrateHit?.invoke()
                        return
                    }
                }
            }
        }

        // A level with no moving platforms, conveyor crates, or hook crates reuses its own immutable lists
        val hasMovingPlatforms = movingPlatforms.isNotEmpty()
        val movingBounds = if (hasMovingPlatforms) movingPlatforms.map { it.bounds } else emptyList()
        val hasConveyorCrates = conveyorCrates.isNotEmpty()
        val crateBounds = if (hasConveyorCrates) conveyorCrates.map { it.bounds } else emptyList()
        val floorCrateBounds = if (hasConveyorCrates) conveyorCrates.filter { !it.isHanging }.map { it.bounds } else emptyList()
        val hasHookCrates = hookCrates.isNotEmpty()
        val hookCrateBounds = if (hasHookCrates) hookCrates.map { it.bounds } else emptyList()
        val dynamicBounds = if (hasMovingPlatforms || hasConveyorCrates || hasHookCrates) {
            movingBounds + crateBounds + hookCrateBounds
        } else emptyList()
        val currentPlatforms = if (dynamicBounds.isNotEmpty()) platforms + dynamicBounds else platforms
        val currentBoxes = if (dynamicBounds.isNotEmpty()) {
            val dynamicBoxes = movingBounds + floorCrateBounds + hookCrateBounds
            if (dynamicBoxes.isNotEmpty()) boxes + dynamicBoxes else boxes
        } else boxes
        val currentOccluders = if (dynamicBounds.isNotEmpty()) occluders + dynamicBounds else occluders

        // Guards without eyes on the player keep walking their route (unless asleep from Phantom Cloak)
        if (!isGameOver && !activePowerups.isPhantomCloakActive) {
            for (g in allGuards) {
                val heldAtPost = g.holdUntilPlayerCrouches && !hasPlayerCrouchedOnce
                if (g !in seeingGuards && !heldAtPost) g.update(dt, currentOccluders)
            }
        }

        // Update cameras - paused while Smoke Screen is active
        if (!activePowerups.isSmokeScreenActive) {
            for (c in cameras) {
                c.update(dt)
            }
        }

        playerPlatformsScratch.clear()
        playerPlatformsScratch.addAll(currentPlatforms)
        for (g in allGuards) playerPlatformsScratch.add(g.bounds)
        val climbTargets = if (canClimb) currentBoxes else emptyList()
        val climbFloatingTargets = if (canClimb) (floatingClimbTargets + hookCrateBounds) else emptyList()
        val activeSwingHooks = if (hookCrates.isEmpty()) swingHooks else swingHooks.filter { hook ->
            hookCrates.none { !it.isDetached && it.hook == hook }
        }
        // A braced body cannot jump, duck or sprint. Suppressing the other two inputs outright
        // (rather than letting them cancel the stance) is what keeps the scene's animation
        // machine honest: every other stance change would otherwise be able to start on a frame
        // where the push clip is still on screen. INTERACT is the only way out.
        val pushActive = pushStanceDemo && !isPushStanceIdle
        val effectiveMoveInput = when {
            pushActive -> moveInput * PUSH_MOVE_FACTOR
            // In air flow parts normal mechanics should not work.
            // When he presses forward once or long presses it, nothing happens.
            // Backward retreat (moveInput < 0.0) remains allowed.
            isInWindZone -> if (moveInput < 0.0) moveInput else 0.0
            else -> moveInput
        }
        val effectiveJumpInput = jumpInput && !pushActive
        val effectiveCrouchInput = crouchInput && !pushActive
        player.update(
            dt, effectiveMoveInput, effectiveJumpInput, effectiveCrouchInput, playerPlatformsScratch,
            climbTargets, activeSwingHooks, climbFloatingTargets, unclimbableBoxes
        )

        // Check Exit / Win condition
        if (player.bounds.intersects(exitZone)) {
            isLevelComplete = true
            isPlayerInVision = false
            alertProgress = 0.0
            detectingGuards = emptyList()
            detectingCameras = emptyList()
            detectingCameraBots = emptyList()
            onLevelComplete?.invoke()
            onLevelCompleteResult?.invoke(getLevelResult())
            return
        }

        // Check conveyor fall-off / out-of-bounds instant restart (e.g. Level 4)
        if (restartOnConveyorFallOff && !isLevelComplete && !isGameOver && checkConveyorFallOff()) {
            restartLevel()
            onConveyorFallOff?.invoke()
            return
        }

        // Check Hanging Crate Collisions:
        // Player only dies if the crate was moving down and touched the player
        if (!isGameOver && !isLevelComplete) {
            for (crate in conveyorCrates) {
                if (crate.isHanging && crate.isMovingDown) {
                    val cBounds = crate.bounds
                    val pBounds = player.bounds
                    // The front of a hanging crate can touch the player safely without triggering game over.
                    // Only when the crate was moving down and touched the player does it trigger game over.
                    val horizontalOverlap = pBounds.right > cBounds.left + 4.0 && pBounds.left < cBounds.right - 4.0
                    val verticalOverlap = pBounds.top <= cBounds.bottom && pBounds.bottom >= cBounds.bottom
                    if (horizontalOverlap && verticalOverlap) {
                        isGameOver = true
                        onGameOver?.invoke()
                        onHangingCrateHit?.invoke()
                        return
                    }
                }
            }
        }

        // Check Laser Collisions (touching an active laser triggers Mission Failed)
        if (!isGameOver && !isLevelComplete) {
            if (laserGraceTimer <= 0.0) {
                for (laser in lasers) {
                    if (!activePowerups.isInvisibilityActive && laser.intersectsPlayer(player.bounds)) {
                        if (activePowerups.isLaserShieldActive) {
                            activePowerups.consumeLaserShield()
                            laserGraceTimer = 1.2
                            onLaserShieldBlocked?.invoke()
                            break
                        }
                        spottedCount++
                        isSpotted = true
                        isGameOver = true
                        onGameOver?.invoke()
                        onLaserHit?.invoke()
                        return
                    }
                }
            }
        }

        // Check Steam Pipe Collisions (touching active steam triggers Mission Failed, deflectable by Laser Shield)
        if (!isGameOver && !isLevelComplete) {
            for (pipe in steamPipes) {
                pipe.update(totalElapsedSeconds)
            }
            if (laserGraceTimer <= 0.0) {
                for (pipe in steamPipes) {
                    if (!activePowerups.isInvisibilityActive && pipe.intersectsPlayer(player.bounds)) {
                        if (activePowerups.isLaserShieldActive) {
                            activePowerups.consumeLaserShield()
                            laserGraceTimer = 1.2
                            onLaserShieldBlocked?.invoke()
                            break
                        }
                        spottedCount++
                        isSpotted = true
                        isGameOver = true
                        onGameOver?.invoke()
                        onLaserHit?.invoke()
                        onSteamPipeHit?.invoke()
                        return
                    }
                }
            }
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

    private fun checkConveyorFallOff(): Boolean {
        if (!conveyorsActive || conveyors.isEmpty()) return false
        for (conveyor in conveyors) {
            val bounds = conveyor.bounds
            // If player has successfully traversed past the end of the conveyor toward the exit
            if (player.x >= bounds.right - 20.0) continue

            // Pushed off the left edge or reached the left corner of the conveyor belt
            if (player.x <= bounds.left || player.bounds.right <= bounds.left + 10.0) {
                return true
            }

            // Stepped or fallen below conveyor surface while within the conveyor span
            if (player.y + player.height > bounds.top + 8.0) {
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * How long the lean-in and the stand-up take. The raw footage runs ~1.45s at the plate's
         * own rate, which is a long time to be committed to a pose in a platformer, so it is
         * played about 1.7x faster than shot - still visibly weighty next to the crouch's 0.22s,
         * and 44 frames over 0.85s is ~52fps of display, so nothing is skipped to get there.
         * Standing back up is quicker than going down, matching crouch's own 0.22/0.18 split.
         */
        const val PUSH_STANCE_ENTER_SECONDS = 0.85
        const val PUSH_STANCE_EXIT_SECONDS = 0.60

        /**
         * Fraction of [Player.moveSpeed] a braced player travels at: 132 * 0.4 = ~53 u/s, a touch
         * under the 65 of a crouch shuffle. The push gait is driven by distance travelled
         * (PlayerAnimations.PUSH_STRIDE_PER_HEIGHT), so this is what sets the cadence too - the
         * feet plant correctly at any speed, but this is the speed the footage was shot at.
         */
        const val PUSH_MOVE_FACTOR = 0.4

        // ---- exhaust fans (level 7) --------------------------------------------------------
        /**
         * Forward speed (u/s) one tap buys, added to [fanSurgeSpeed] and then decayed.
         *
         * Not a distance: the old mechanic moved `player.x` by a flat 10 units on the frame of
         * the press, and a position jump reads as a jolt at any size - at a human 4 Hz it was
         * four snaps a second. Velocity integrates, so the same press becomes a slide.
         *
         * Equilibrium, and why these three numbers: tapping at rate r with decay tau settles at
         * an average surge of roughly `IMPULSE * r * tau`, so 75 * 4 * 0.45 = 135 u/s. Against a
         * 140 u/s gale and a touch player whose finger is down maybe a third of the time (~44
         * u/s of ordinary walk), that nets about +40 u/s - a 360-unit fan zone crossed in ~9s of
         * steady tapping. Deliberately slower than the old arrangement, which is what was asked
         * for. Holding the button without tapping still loses: 132 of walk against 140 of wind
         * drifts backwards, which is the mechanic.
         *
         * Verified by simulating the real loop (`testFanTapAdvanceIsSmoothAndSlowerThanTheOld
         * Impulse`), not from this arithmetic - the duty cycle of a real tap is a guess and the
         * simulation is not.
         */
        const val FAN_TAP_IMPULSE = 26.0

        /**
         * Time constant of the surge's exponential decay: what carries a player across the gap
         * between taps, and the main smoothness knob. Ripple around the mean goes as 1/(rate*tau),
         * so at a 3.3 Hz tap this holds the forward speed inside 21..65 u/s where 0.5 gave 10..59
         * around a lower mean. Not longer than this: the surge is momentum the player coasts on
         * after they stop tapping, and ~0.7s of drift in a corridor full of timed steam jets is
         * already as much as the level can afford.
         */
        const val FAN_TAP_DECAY_SECONDS = 0.70

        /**
         * Floor the first tap of a burst lands on, so starting to tap is not a second of losing
         * ground while the surge spins up.
         *
         * Measured, because the spin-up was real: from a standing start at a 3.3 Hz tap the first
         * second nets -4.6 u/s with no floor - the player goes BACKWARDS while they are already
         * tapping, which reads as the input not working. 40 puts that at +7.8 and leaves the
         * steady state alone (49.3 -> 49.8 u/s), because it only binds while the surge is below
         * it. 55 was tried and does bind in steady state, taking the net back up to 62 and undoing
         * the slowdown this whole rework is for.
         *
         * This is a VELOCITY step, not a position one: the body accelerates from a standstill the
         * same way a jump does. It is the position jump the old mechanic made that was the problem.
         */
        const val FAN_TAP_FLOOR = 40.0

        /**
         * How long after a tap (or a held button) the player still counts as driving forward, and
         * how fast the surge bleeds off once they do not.
         *
         * The window exists because a touch player's button is up for most of every tap cycle, so
         * "is the button down" is not a usable test of intent. The fast release exists because the
         * surge deliberately outlives the wind zone, and without it the player cannot STOP - which
         * in this corridor means drifting into a steam jet they stopped to wait out.
         */
        const val FAN_INTENT_WINDOW = 0.35
        const val FAN_SURGE_RELEASE_SECONDS = 0.12

        /** Ceiling on stacked taps. Binds only above ~9 Hz, which is its job. */
        const val FAN_SURGE_MAX = 190.0

        /**
         * How much of the gale a full surge cancels, and the surge at which it is fully earned.
         *
         * The old mechanic had this as a binary 0.45x on the wind for 0.10s after every tap - a
         * step change in the air the instant anyone touched the button. Scaling it off the surge
         * instead is continuous, and it turns out to have been load-bearing rather than cosmetic:
         * that dampen was most of what made the level passable for a player who taps rather than
         * holds, because it is paid per TAP and does not care about the button's duty cycle.
         * Dropping it without a replacement cost the 5 Hz touch case ~25 u/s.
         */
        const val FAN_SURGE_WIND_RELIEF = 0.45
        const val FAN_SURGE_RELIEF_AT = 90.0

        /**
         * Fraction of [Player.moveSpeed] the player's own walking is worth inside a wind zone.
         *
         * Added because the mechanic otherwise splits hard by input device, which the simulated
         * sweep made obvious. A keyboard player HOLDS the key and taps it, banking a full 132 u/s
         * of walk under every surge; a touch player pressing the same on-screen button can only
         * have it down for part of each tap, so they were getting a third of that. Measured on the
         * old mechanic: +62 u/s for hold-and-tap at 3.3 Hz against -16 u/s for a 4 Hz touch tap -
         * i.e. the level ran BACKWARDS on a phone at a realistic tapping rate. Cutting ordinary
         * walking to 0.6 costs the holder much more than the tapper and closes most of that gap.
         *
         * Where these four numbers landed, all from simulating the real loop
         * (`testFanTapAdvanceIsSmoothAndSlowerThanTheOldImpulse`), never from arithmetic:
         *
         *   input style                 old      now
         *   hold + tap, 3.3 Hz         +62     +43.5   (slower, which is what was asked for)
         *   touch tap 4 Hz, 35% duty   -16      +9.7   (was unplayable)
         *   touch tap 5 Hz, 50% duty   +24     +56.5
         *
         * Re-run that sweep rather than nudging one of these: they trade against each other, and
         * the smoothness of the result is set by FAN_TAP_DECAY_SECONDS against the tap rate, not
         * by the impulse alone.
         */
        const val WIND_WALK_FACTOR = 0.60

        /** Normal walking speed when spam-clicking forward against the wind, ignoring wind pushback. */
        const val WIND_SPAM_SPEED = 96.0

        /** Time window within which consecutive taps count as spamming (in seconds). */
        const val FAN_SPAM_TAP_WINDOW = 0.40

        /** Duration the spamming forward movement remains active after each spam tap (in seconds). */
        const val FAN_SPAM_ACTIVE_WINDOW = 0.35

        /**
         * How long bracing into the gale and straightening back up take. Fast reaction (0.05s)
         * so the character immediately folds into the braced forward lean upon touching the airflow.
         */
        const val WIND_STANCE_ENTER_SECONDS = 0.05
        const val WIND_STANCE_EXIT_SECONDS = 0.25

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
            // Width was 38 (a guess, not a measurement) - a per-column alpha scan of truck.png
            // found the art's own hood-to-windshield step actually lands at ~11.2% of the truck's
            // total width, not 14.5% (38/262). At the old 38 the last ~9 units of the "hood" tier
            // were already standing under the drawn windshield/cab wall rather than the flat hood,
            // which is what read as floating while walking that stretch - the feet were still
            // governed by the low hood height while the art above them had already risen to the
            // tall cab. 29 puts the tier boundary back under the art's real step (29/(29+45+179)
            // = 11.46%, matching the measured ~11.2%).
            val truckFront = Rect(x = smallCrate.right, y = groundY - truckFrontHeight, width = 29.0, height = truckFrontHeight)
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
                    sweepDirection = spawn.sweepDirection,
                    sweepPauseDuration = spawn.sweepPauseDuration,
                    detectionPauseDuration = spawn.detectionPauseDuration
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
            // Unlike MovingPlatform/ConveyorCrate/Laser (each rebuilt fresh from an immutable Def
            // below), Lever and HookCrate ARE the live mutable objects held directly on the
            // (singleton, companion-object) LevelLayout - there's no separate Def indirection for
            // them. Without this, a lever pulled in one GameWorld (e.g. quitting to the menu and
            // re-entering the level, which constructs a brand new GameWorld from the same layout
            // singleton) would still read as isActivated on the next playthrough, since it's the
            // exact same object, not a fresh one. restartLevel()/respawnAtCheckpoint() reset an
            // existing GameWorld's own levers/hookCrates already - this covers the other case, a
            // freshly constructed one.
            for (lever in layout.levers) lever.reset()
            for (hc in layout.hookCrates) hc.reset()

            val leftWallX = if (layout.hasStartFences) -30.0 else -200.0
            val leftWall = Rect(x = leftWallX, y = -400.0, width = 30.0, height = 1200.0)
            val rightWall = Rect(x = layout.worldWidth, y = -400.0, width = 30.0, height = 1200.0)

            val groundY = layout.platforms.firstOrNull { it.y > 300.0 }?.y ?: 440.0
            val fenceHeight = 140.0
            val fence2Width = 172.0
            val fence1Width = 151.0
            val fence2 = if (layout.hasStartFences) {
                layout.fence2 ?: Rect(x = -80.0, y = groundY - fenceHeight, width = fence2Width, height = fenceHeight)
            } else null
            val fence1 = if (layout.hasStartFences) {
                layout.fence1 ?: Rect(x = 70.0, y = groundY - fenceHeight, width = fence1Width, height = fenceHeight)
            } else null

            val allBoxes = if (!layout.hasStartFences) {
                layout.boxes
            } else if ((fence1 != null && layout.boxes.contains(fence1)) || (fence2 != null && layout.boxes.contains(fence2))) {
                layout.boxes
            } else {
                listOfNotNull(fence2, fence1) + layout.boxes
            }

            val platforms = layout.platforms + allBoxes + listOf(leftWall, rightWall)
            // Floors and boxes both block sight, so no guard can see through a storey. Table
            // decorations (LevelLayout.tableDecorations) have no collision - nothing stands on or
            // bumps into them - but they're real drawn geometry (a support leg), so they block
            // sight the same as anything else the player could visually read as solid.
            //
            // Poles (LevelLayout.poles) are deliberately EXCLUDED here, unlike tableDecorations -
            // reported directly against a screenshot: a camera mounted right on top of its own pole
            // (LEVEL_3_LAYOUT's poleCamera) had that same pole block its own downward view, and
            // VisionSystem's shadow-casting turned that self-occlusion into a polygon that reads as
            // a flat-edged rectangle instead of a cone (the near-vertical rays all stop at the
            // pole's own straight side, right next to the eye). A camera occluding its own mount is
            // a real geometric consequence, not a bug in the strict sense, but it looks broken on
            // screen - so a pole blocks nothing, the same as a swing hook or any other prop that's
            // real geometry but not solid enough to matter for sightlines.
            val occluders = layout.platforms + allBoxes + layout.tableDecorations

            val player = Player(
                x = layout.playerStartX,
                y = layout.playerStartY,
                startX = layout.playerStartX,
                startY = layout.playerStartY
            )

            val guards = layout.guards.map { spawn ->
                Guard(
                    x = spawn.startX,
                    y = spawn.surfaceY - spawn.height,
                    width = spawn.width,
                    height = spawn.height,
                    patrolMinX = spawn.patrolMinX,
                    patrolMaxX = spawn.patrolMaxX,
                    speed = spawn.speed,
                    facing = spawn.facing,
                    visionRange = spawn.visionRange,
                    patrolPauseDuration = spawn.patrolPauseDuration,
                    holdUntilPlayerCrouches = spawn.holdUntilPlayerCrouches,
                    visionTilt = spawn.visionTilt
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

            val cameraSpawns = layout.cameras.ifEmpty { levelData.cameras }
            val cameras = cameraSpawns.map { spawn ->
                Camera(
                    x = spawn.x,
                    y = spawn.y,
                    minAngle = spawn.minAngle,
                    maxAngle = spawn.maxAngle,
                    currentAngle = spawn.startAngle,
                    sweepSpeed = spawn.sweepSpeed,
                    visionRange = spawn.visionRange,
                    visionFov = spawn.visionFov,
                    sweepDirection = spawn.sweepDirection,
                    sweepPauseDuration = spawn.sweepPauseDuration,
                    detectionPauseDuration = spawn.detectionPauseDuration
                )
            }
            // Same Camera instances as in `cameras` above (matched by spawn identity, not
            // reconstructed) - see LevelLayout.translucentCameras.
            val translucentCameras = cameraSpawns.zip(cameras)
                .filter { (spawn, _) -> spawn in layout.translucentCameras }
                .map { (_, camera) -> camera }

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
                    initialY = def.initialY,
                    startsInactive = def.startsInactive,
                    activationDelaySeconds = def.activationDelaySeconds,
                    oneShot = def.oneShot,
                    crushesOnContact = def.crushesOnContact
                )
            }

            val conveyorCrates = layout.conveyorCrates.map { def ->
                ConveyorCrate(
                    initialX = def.initialX,
                    initialY = def.y,
                    width = def.width,
                    height = def.height,
                    loopMinX = def.loopMinX,
                    loopMaxX = def.loopMaxX,
                    shouldLoop = def.shouldLoop,
                    isHanging = def.isHanging,
                    isVariant1 = def.isVariant1,
                    speedMultiplier = def.speedMultiplier,
                    isPatrol = def.isPatrol,
                    patrolMinX = def.patrolMinX,
                    patrolMaxX = def.patrolMaxX,
                    minY = def.minY,
                    maxY = def.maxY,
                    verticalPeriodSeconds = def.verticalPeriodSeconds,
                    verticalPhaseOffsetSeconds = def.verticalPhaseOffsetSeconds
                )
            }

            val lasers = layout.lasers.map { def ->
                Laser(
                    id = def.id,
                    topX = def.topX,
                    topY = def.topY,
                    bottomX = def.bottomX,
                    bottomY = def.bottomY,
                    beamThickness = def.beamThickness,
                    activeDuration = def.activeDuration,
                    inactiveDuration = def.inactiveDuration,
                    phaseOffsetSeconds = def.phaseOffsetSeconds,
                    isAlwaysActive = def.isAlwaysActive,
                    emitterScale = def.emitterScale,
                    mechanismId = def.mechanismId
                )
            }

            val fans = layout.fans.map { VentFan(it) }
            val cameraBots = layout.cameraBots.map { CameraBot(it) }
            val steamPipes = layout.steamPipes.map { SteamPipe(it) }

            val world = GameWorld(
                player = player,
                guard = primaryGuard,
                crate = layout.boxes.firstOrNull() ?: Rect(0.0, 0.0, 0.0, 0.0),
                platforms = platforms,
                occluders = occluders,
                exitZone = layout.exitZone,
                levelData = levelData,
                extraGuards = extraGuards,
                cameras = cameras,
                translucentCameras = translucentCameras,
                boxes = allBoxes,
                worldWidth = layout.worldWidth,
                fence1 = fence1,
                fence2 = fence2,
                hangingCrateVariant1 = layout.hangingCrateVariant1,
                hangingCrateVariant2 = layout.hangingCrateVariant2,
                barrels = layout.barrels,
                woodCrates = layout.woodCrates,
                poles = layout.poles,
                cranes = layout.cranes,
                plainPlatforms = layout.plainPlatforms,
                tables = layout.tables,
                tableParts = layout.tableParts,
                tableDecorations = layout.tableDecorations,
                floatingClimbTargets = layout.floatingClimbTargets,
                unclimbableBoxes = layout.unclimbableBoxes,
                movingPlatforms = movingPlatforms,
                swingHooks = layout.swingHooks,
                // Fresh copies, not the LevelLayout singleton's own shared instances: layout.levers/
                // hookCrates are computed once per process (LEVEL_6_LAYOUT etc. are top-level `val`s)
                // and hold mutable state (Lever.isActivated, HookCrate.isDetached/vy/bounds). Passing
                // them through directly meant every GameWorld built from the same layout - a level
                // replayed without restarting the app, or two GameWorld instances alive at once in
                // tests - shared and mutated the SAME lever/hook-crate objects, so a lever pulled in
                // one playthrough stayed permanently pulled in the next. Same treatment movingPlatforms/
                // cameras/conveyorCrates/lasers already get from their Def/spawn objects just above.
                levers = layout.levers.map { it.copy() },
                hookCrates = layout.hookCrates.map { it.copy() },
                conveyors = layout.conveyors,
                conveyorCrates = conveyorCrates,
                lasers = lasers,
                fans = fans,
                cameraBots = cameraBots,
                steamPipes = steamPipes,
                hasNoGuards = guards.isEmpty(),
                restartOnConveyorFallOff = layout.restartOnConveyorFallOff,
                conveyorsStartOnMove = layout.conveyorsStartOnMove,
                canClimb = layout.canClimb,
                manualCheckpoints = layout.manualCheckpoints,
                playerStartCrouched = layout.playerStartCrouched,
                pushStanceDemo = layout.pushStanceDemo
            )
            if (layout.playerStartCrouched) {
                world.player.isCrouching = true
            }
            if (levelData.playerCrouchForwardSpeedMultiplier != 1.0) {
                world.player.crouchForwardSpeed = world.player.crouchSpeed * levelData.playerCrouchForwardSpeedMultiplier
            }
            return world
        }
    }
}
