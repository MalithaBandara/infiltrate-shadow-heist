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
    /** Washed-out table legs with no collision - see LevelLayout.passThroughLegs. */
    val passThroughLegs: List<Rect> = emptyList(),
    /** Tables drawn as one tiled slab - see LevelLayout.seamlessTables. */
    val seamlessTables: List<Rect> = emptyList(),
    /** Boxes climbable despite being a floating ledge - see LevelLayout.floatingClimbTargets. */
    val floatingClimbTargets: List<Rect> = emptyList(),
    /** Boxes the player may not mantle onto at all - see LevelLayout.unclimbableBoxes. */
    val unclimbableBoxes: List<Rect> = emptyList(),
    // Jump-crate gap crossings, tagged by which of the two hanging-crate art variants each box
    // renders with - see LevelLayout.hangingCrateVariant1/2 and GameplayScene.kt's box loop.
    val staticHangingCrateVariant1: List<Rect> = emptyList(),
    val staticHangingCrateVariant2: List<Rect> = emptyList(),
    val movingPlatforms: List<MovingPlatform> = emptyList(),
    /** Flatbed carts the player can brace against and walk along - see LevelLayout.pushCarts. */
    val pushCarts: List<PushCart> = emptyList(),
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
                (pushStanceDemo && !isGameOver && !isLevelComplete) ||
                // A cart within arm's reach, or one already in hand - letting go has to stay
                // possible even after the grab has carried the body out of its own grab range.
                grippedCart != null ||
                pushCarts.any { it.canGrip(player) } ||
                doorSwitches.any { it.isPlayerInRange(player) }
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
        passThroughLegs: List<Rect> = emptyList(),
        seamlessTables: List<Rect> = emptyList(),
        floatingClimbTargets: List<Rect> = emptyList(),
        unclimbableBoxes: List<Rect> = emptyList(),
        hangingCrateVariant1: List<Rect> = emptyList(),
        hangingCrateVariant2: List<Rect> = emptyList(),
        movingPlatforms: List<MovingPlatform> = emptyList(),
        pushCarts: List<PushCart> = emptyList(),
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
        passThroughLegs = passThroughLegs,
        seamlessTables = seamlessTables,
        floatingClimbTargets = floatingClimbTargets,
        unclimbableBoxes = unclimbableBoxes,
        staticHangingCrateVariant1 = hangingCrateVariant1,
        staticHangingCrateVariant2 = hangingCrateVariant2,
        movingPlatforms = movingPlatforms,
        pushCarts = pushCarts,
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

    // ---- level 11: doors, lifts and the prisoner (LevelLayout.doors/doorSwitches/lifts/prisoner) ----
    //
    // Assigned by createFromLayout rather than taken through the constructors - nothing but a
    // layout ever has them.

    var doors: List<Door> = emptyList()
        internal set
    var doorSwitches: List<DoorSwitchDef> = emptyList()
        internal set
    var lifts: List<Lift> = emptyList()
        internal set
    var prisoner: Prisoner? = null
        internal set

    /** True for the frames something with eyes has the prisoner in sight - the scene's pip over him. */
    var isPrisonerSeen: Boolean = false
        private set

    /** Set when the run ended because the prisoner was seen or killed, for the fail card's reason. */
    var prisonerLost: Boolean = false
        private set

    /** Fired whenever a switch is thrown (the scene's clunk). */
    var onSwitchThrown: ((DoorSwitchDef) -> Unit)? = null

    /** Switches thrown this attempt - what completes an INTERACT tutorial on a level with switches. */
    var switchThrowCount: Int = 0
        private set

    /** Times each switch has been thrown this attempt, by id (BonusObjective.USE_EACH_SWITCH_ONCE). */
    private val switchThrows = HashMap<String, Int>()

    /** Some switch has been thrown a second time this attempt. */
    var hasReusedASwitch: Boolean = false
        private set

    private var switchInteractWasDown = false

    /**
     * What blocks sight this tick: the level's own [occluders] plus every door panel still hanging
     * and every lift platform. Just [occluders] on a level without doors or lifts. The scene draws
     * cones against this, so a guard's beam stops at a shut door exactly where his sight does.
     */
    var visionOccluders: List<Rect> = occluders
        private set

    /** Bumped whenever [visionOccluders] changes shape - the scene rebuilds cached cones on it. */
    var visionOccluderVersion: Int = 0
        private set

    private fun dynamicDoorSolids(): List<Rect> {
        if (doors.isEmpty() && lifts.isEmpty()) return emptyList()
        val out = ArrayList<Rect>(doors.size + lifts.size)
        for (d in doors) d.panel?.let { out.add(it) }
        for (l in lifts) out.add(l.bounds)
        return out
    }

    internal fun refreshVisionOccluders() {
        if (doors.isEmpty() && lifts.isEmpty()) return
        val next = occluders + dynamicDoorSolids()
        if (next != visionOccluders) {
            visionOccluders = next
            visionOccluderVersion++
        }
    }

    /** A body standing on [r]'s top, feet centre over it - riding it, the way a moving platform is ridden. */
    private fun standsOn(body: Player, r: Rect): Boolean {
        if (!body.isGrounded) return false
        val feet = body.y + body.height
        val fc = body.x + body.width / 2.0
        return kotlin.math.abs(feet - r.top) < 4.5 && fc >= r.left && fc <= r.right
    }

    /** Switches, doors and lifts - before anyone moves this tick. */
    private fun updateDoorsAndLifts(dt: Double, interactInput: Boolean) {
        if (doorSwitches.isEmpty() && doors.isEmpty() && lifts.isEmpty()) return
        val pressed = interactInput && !switchInteractWasDown
        switchInteractWasDown = interactInput
        if (pressed) {
            for (sw in doorSwitches) {
                if (!sw.isPlayerInRange(player)) continue
                for (id in sw.targets) {
                    doors.firstOrNull { it.id == id }?.toggle()
                    lifts.firstOrNull { it.id == id }?.toggle()
                }
                switchThrowCount++
                val n = (switchThrows[sw.id] ?: 0) + 1
                switchThrows[sw.id] = n
                if (n > 1) hasReusedASwitch = true
                onSwitchThrown?.invoke(sw)
                break
            }
        }
        val p = prisoner
        val bodies = ArrayList<Rect>(allGuards.size + 2)
        bodies.add(player.bounds)
        if (p != null) bodies.add(p.bounds)
        for (g in allGuards) bodies.add(g.bounds)
        for (d in doors) {
            d.update(dt, bodies)
            if (p != null && !p.isFree && d.id == p.def.freedByDoorId && d.openness >= PRISONER_FREED_AT) p.free()
        }
        for (l in lifts) {
            val before = l.bounds
            val playerRides = standsOn(player, before)
            val prisonerRides = p != null && standsOn(p.body, before)
            // A guard in the shaft stops it: the lift is not for carrying guards (he has no
            // gravity to follow it down with, and nowhere to walk off it at the top).
            val guardInShaft = allGuards.any { it.bounds.intersects(l.shaft) }
            val blockers = ArrayList<Rect>(2)
            if (!playerRides) blockers.add(player.bounds)
            if (p != null && !prisonerRides) blockers.add(p.bounds)
            val dy = l.update(dt, blockers, frozen = guardInShaft)
            if (dy != 0.0) {
                if (playerRides) player.y += dy
                if (prisonerRides) p!!.body.y += dy
            }
        }
    }

    /** Level 11: the prisoner has walked into steam - Mission Failed. */
    private fun losePrisoner() {
        prisonerLost = true
        isGameOver = true
        onGameOver?.invoke()
    }

    /** Everything a checkpoint on a prisoner level puts back as it was when taken. */
    private class EscortSnapshot(
        val prisonerX: Double,
        val prisonerY: Double,
        val doors: List<Pair<Boolean, Double>>,
        val lifts: List<Pair<Boolean, Double>>,
        val guards: List<Pair<Double, Double>>
    )

    private var escortSnapshot: EscortSnapshot? = null

    private fun takeEscortSnapshot(p: Prisoner) {
        escortSnapshot = EscortSnapshot(
            prisonerX = p.body.x,
            prisonerY = p.body.y,
            doors = doors.map { it.isOpen to it.openness },
            lifts = lifts.map { it.isUp to it.topY },
            guards = allGuards.map { it.x to it.facing }
        )
    }

    /** Doors, lifts, guards and the prisoner back to the level's start (a restart) or the last checkpoint. */
    private fun resetEscort(toCheckpoint: Boolean) {
        val p = prisoner
        if (p == null) {
            // Level 10's door and lift: back to shut and down on a restart; a respawn (from before
            // them) leaves them as they are.
            if (!toCheckpoint) {
                for (d in doors) d.reset()
                for (l in lifts) l.reset()
                switchInteractWasDown = false
                refreshVisionOccluders()
            }
            return
        }
        val snap = if (toCheckpoint) escortSnapshot else null
        if (snap == null) {
            for (d in doors) d.reset()
            for (l in lifts) l.reset()
            for (g in allGuards) g.resetToSpawn()
            p.reset()
        } else {
            doors.forEachIndexed { i, d -> snap.doors.getOrNull(i)?.let { (o, v) -> d.restore(o, v) } }
            lifts.forEachIndexed { i, l -> snap.lifts.getOrNull(i)?.let { (u, y) -> l.restore(u, y) } }
            allGuards.forEachIndexed { i, g -> snap.guards.getOrNull(i)?.let { (x, f) -> g.placeAt(x, f) } }
            p.restore(snap.prisonerX, snap.prisonerY)
        }
        isPrisonerSeen = false
        prisonerLost = false
        switchInteractWasDown = false
        refreshVisionOccluders()
    }

    /**
     * Reused buffer for the platform list handed to [Player.update] - the level's platforms plus
     * every guard's current bounds. Guards move, so this genuinely has to be rebuilt each frame,
     * but rebuilding it into one buffer beats allocating two fresh lists (`map`, then `+`) sixty
     * times a second. Safe to reuse because [Player] only iterates it and never retains it.
     */
    private val playerPlatformsScratch = ArrayList<Rect>()

    /**
     * True while the player last stood at floor level - the ground, or anything standing on it
     * within [FLOOR_LEVEL_BAND] (barrels, carts, a dropped crate) - rather than on a hanging load or
     * a raised platform. While it holds, every [MovingPlatformDef.noGroundBoarding] surface is
     * capped with an invisible lid and left out of the climb targets, so it cannot be got onto.
     * Updated on every grounded frame and held through the air, so it describes the surface the
     * current jump left from.
     */
    var isBoardingFromFloorLevel: Boolean = true
        private set

    /** The surfaces [isBoardingFromFloorLevel] applies to, where they are this tick. */
    private fun groundBoardingBlockedSurfaces(): List<Rect> {
        val out = ArrayList<Rect>()
        for (mp in movingPlatforms) if (mp.noGroundBoarding) out.add(mp.bounds)
        for (hc in hookCrates) if (hc.noGroundBoarding && hc.isHanging) out.add(hc.bounds)
        return out
    }

    private fun updateBoardingFromFloorLevel(groundY: Double) {
        if (!player.isGrounded) return
        val feet = player.y + player.height
        val footCenter = player.x + player.width / 2.0
        val onHangingLoad = groundBoardingBlockedSurfaces().any {
            kotlin.math.abs(feet - it.top) < 4.5 && footCenter >= it.left && footCenter <= it.right
        }
        isBoardingFromFloorLevel = feet >= groundY - FLOOR_LEVEL_BAND && !onHangingLoad
    }

    /**
     * Hook crates: the rig's sweep (held still while the player is swinging from it) and, for a
     * [HookCrate.physical] one, the swing on the rope, the tumble once cut, the catch in a cart and
     * the ride in it afterwards. A plain one just drops to the floor.
     */
    private fun updateHookCrates(dt: Double, groundY: Double) {
        for (hc in hookCrates) {
            // A body standing on a hanging crate rides it, the way one rides a moving platform.
            val riding = hc.isHanging && player.isGrounded && run {
                val feet = player.y + player.height
                val fc = player.x + player.width / 2.0
                kotlin.math.abs(feet - hc.bounds.top) < 4.5 && fc >= hc.bounds.left && fc <= hc.bounds.right
            }
            val beforeTop = hc.bounds.top
            val beforeCx = hc.bounds.centerX
            val hookIndex = hookCrates.indexOf(hc)
            // Swinging from it takes this rig off the recording for good - see followRecordedWorld.
            if (player.isSwinging && followedHooks.getOrElse(hookIndex) { false }) followedHooks[hookIndex] = false
            if (!player.isSwinging) hc.advanceSweep(dt, recordedHookClock(hookIndex))
            if (riding) {
                player.x += hc.bounds.centerX - beforeCx
                player.y += hc.bounds.top - beforeTop
            }
            if (!hc.physical) {
                hc.update(dt, 1000.0, groundY)
                continue
            }
            val carrierId = hc.carriedByCartId
            if (carrierId != null) {
                val cart = pushCarts.firstOrNull { it.id == carrierId } ?: continue
                hc.bounds = Rect(cart.x + hc.carryOffsetX, cart.deckY - hc.bounds.height, hc.bounds.width, hc.bounds.height)
                hc.body?.let { it.cx = hc.bounds.centerX; it.cy = hc.bounds.centerY }
                continue
            }
            val body = hc.body ?: continue
            if (body.isAsleep) continue
            updateLooseCrate(hc, body, dt, groundY)
            if (isGameOver) return
        }
        rollCarts(dt)
    }

    /** Scratch for [updateLooseCrate]. */
    private val crateSupports = HashSet<BoxObstacle>()

    /** One tick of a cut [HookCrate.physical] crate: tumble, hit things, maybe land in a cart. */
    private fun updateLooseCrate(hc: HookCrate, body: RigidBox, dt: Double, groundY: Double) {
        val obstacles = ArrayList<BoxObstacle>()
        obstacles.add(BoxObstacle(Rect(-1000.0, groundY, worldWidth + 2000.0, 200.0)))
        for (b in boxes) obstacles.add(BoxObstacle(b))
        val cartDecks = HashMap<BoxObstacle, PushCart>()
        for (cart in pushCarts) {
            // A cart the echo is playing back goes where the recording says, whatever hits it.
            val held = grippedCart === cart || isEchoCart(cart)
            val inv = if (held) 0.0 else 1.0 / PushCart.MASS
            val vx = if (held) 0.0 else cart.vx
            if (cart.isLoaded) {
                obstacles.add(BoxObstacle(cart.bounds, vx, inv, cart))
                continue
            }
            val deck = BoxObstacle(cart.deckRect, vx, inv, cart)
            cartDecks[deck] = cart
            obstacles.add(deck)
            for (post in cart.postRects) obstacles.add(BoxObstacle(post, vx, inv, cart))
        }
        BoxPhysics.step(body, dt, obstacles, crateSupports) { owner, jx ->
            if (owner is PushCart && owner !== grippedCart && !isEchoCart(owner)) owner.vx += jx / PushCart.MASS
        }
        hc.syncBodyBounds()

        // Resting on the top of a handle post and nothing else - balanced on a stick, a thing no
        // real load does for long, and it had nothing to ever knock it off: it sat up there for
        // good, looking as if the lever had not dropped it. It tips off to the side its middle
        // is over (outward, when dead centre).
        if (crateSupports.isNotEmpty() && crateSupports.all { it.owner is PushCart && it.rect.width < body.width / 2.0 } &&
            kotlin.math.abs(body.omega) < BoxPhysics.REST_SPIN
        ) {
            val post = crateSupports.first()
            val cart = post.owner as PushCart
            val off = body.cx - post.rect.centerX
            val side = if (kotlin.math.abs(off) > 1.0) kotlin.math.sign(off)
                else if (post.rect.centerX < cart.bounds.centerX) -1.0 else 1.0
            body.omega += side * PERCH_TIP_SPIN * dt
        }

        // Coming down on the player is Mission Failed, like any other falling load.
        if (!isGameOver && !isLevelComplete && body.vy > 150.0) {
            val pb = player.bounds
            if (pb.intersects(hc.bounds) && pb.bottom > hc.bounds.bottom - 0.5) {
                isGameOver = true
                onGameOver?.invoke()
                onHangingCrateHit?.invoke()
                return
            }
        }

        // Caught: at rest, near square, sitting on an empty cart's deck between its posts.
        val deck = crateSupports.firstOrNull { it in cartDecks }
        if (deck != null) {
            val cart = cartDecks.getValue(deck)
            val slow = kotlin.math.hypot(body.vx - cart.vx, body.vy) < BoxPhysics.REST_SPEED * 2.0 &&
                kotlin.math.abs(body.omega) < BoxPhysics.REST_SPIN
            val square = kotlin.math.round(body.angle / kotlin.math.PI) * kotlin.math.PI
            val tilt = kotlin.math.abs(body.angle - square)
            val bb = body.aabb()
            val between = bb.left >= cart.loadBounds.left - 1.0 && bb.right <= cart.loadBounds.right + 1.0
            if (slow && tilt < HookCrate.CATCH_MAX_TILT && between) {
                body.angle = square
                body.vx = 0.0
                body.vy = 0.0
                body.omega = 0.0
                body.cy = cart.deckY - body.height / 2.0
                BoxPhysics.settleIfResting(body, BoxPhysics.REST_TIME, supported = true, supportTop = null)
                hc.syncBodyBounds()
                hc.carriedByCartId = cart.id
                hc.carryOffsetX = hc.bounds.x - cart.x
                hc.isLanded = true
                cart.isLoaded = true
                cart.vx = 0.0
                runRecorder?.event(runClock, RunEventKind.CATCH, "${hc.id}>${cart.id}")
                return
            }
        }
        // Otherwise it goes to sleep only on something that cannot move - never on a cart.
        val onCart = crateSupports.any { it.owner is PushCart }
        val staticTop = crateSupports.filter { it.owner == null }.minOfOrNull { it.rect.top }
        if (!onCart && BoxPhysics.settleIfResting(body, dt, supported = staticTop != null, supportTop = staticTop)) {
            hc.syncBodyBounds()
            hc.isLanded = true
        }
    }

    /**
     * MovingPlatformDef.squeezes: two such loads closer together than a body is wide, with the
     * body's centre between theirs and the body level with BOTH of them - so standing on top of
     * one of them (level 9's hop from one to the other) is not being caught between them.
     */
    private fun isSqueezedBetweenLoads(p: Rect): Boolean {
        for (a in movingPlatforms) {
            if (!a.squeezes) continue
            val ab = a.bounds
            for (b in movingPlatforms) {
                if (b === a || !b.squeezes) continue
                val bb = b.bounds
                if (bb.centerX <= ab.centerX) continue
                if (bb.left - ab.right >= player.width) continue
                val c = p.centerX
                if (c <= ab.centerX || c >= bb.centerX) continue
                val levelWithA = p.top < ab.bottom && p.bottom > ab.top
                val levelWithB = p.top < bb.bottom && p.bottom > bb.top
                if (levelWithA && levelWithB) return true
            }
        }
        return false
    }

    /**
     * A crushing load (MovingPlatformDef.crushesOnContact) that comes down on a cart - handles
     * included, loaded or not - is Mission Failed too: LEVEL_8_LAYOUT's bobbing chain over the
     * empty cart being pushed under it.
     */
    private fun crushingLoadOnACart(): Boolean {
        // With an echo the carts are its, played back where its run had them - not the player's.
        if (pushCarts.isEmpty() || echo != null) return false
        for (mp in movingPlatforms) {
            if (!mp.crushesOnContact) continue
            val mb = mp.bounds
            for (cart in pushCarts) {
                // The parts a load can hit (PushCart.crushParts), not the art rect: an empty cart
                // is an open frame, and a load in the gap between its handles touches nothing.
                for (part in cart.crushParts) {
                    val hit = if (mp.crushesOnlyFromBelow) touchesUnderside(part, mb, 0.0) else mb.intersects(part)
                    if (hit) return true
                }
            }
        }
        return false
    }

    /**
     * The body as a load coming down meets it: the drawn character is ~21 units across inside the
     * 36-unit collision box (Player.footWidth), so a crusher is judged on that centre span - one
     * coming down in front of the chest, clear of the drawn body, is a miss.
     */
    private fun crushBox(): Rect {
        val b = player.bounds
        val w = player.footWidth
        return Rect(b.x + (b.width - w) / 2.0, b.y, w, b.height)
    }

    /**
     * The body as a load coming down meets it, as the parts actually drawn. Standing, that is
     * [crushBox]. Braced into a cart the figure leans forward with the trailing leg well back, so
     * over the box it is a slope - tall at the fists, knee-high at the back boot - nowhere near
     * the 96-unit box: a bob coming down just behind a pushing body (one he has walked on past)
     * touched nothing drawn and still ended the run (2026-09-29: "i think it happens when the
     * person has passed the crate ... its like the bounding box of the person is larger than his
     * actual self when he is in this position"). The slope is [PUSH_CRUSH_PROFILE], the push
     * clips' own silhouette.
     */
    private fun crushParts(): List<Rect> {
        if (grippedCart == null || pushStanceBlend < 0.5 || pushCartSide == 0.0) return listOf(crushBox())
        val b = player.bounds
        val n = PUSH_CRUSH_PROFILE.size
        val w = b.width / n
        val vh = player.visualHeight
        return List(n) { i ->
            // Profile index 0 is the trailing end; facing left the body is mirrored.
            val k = if (pushCartSide > 0.0) i else n - 1 - i
            val h = minOf(b.height, PUSH_CRUSH_PROFILE[k] * vh)
            Rect(b.x + i * w, b.bottom - h, w, h)
        }
    }

    /** How far a body box standing at [x] overlaps [r] across. */
    private fun overlapX(x: Double, r: Rect): Double =
        (minOf(x + player.width, r.right) - maxOf(x, r.left)).coerceAtLeast(0.0)

    /**
     * [touchesUnderside] against the body's drawn [parts] taken together: the parts the load
     * reaches, their tallest top against its underside and their joint span across it.
     */
    private fun comesDownOn(parts: List<Rect>, crate: Rect, reach: Double): Boolean {
        if (parts.size == 1) return touchesUnderside(parts[0], crate, reach)
        var top = Double.MAX_VALUE
        var left = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        for (o in parts) {
            if (!Rect(o.x, o.y - reach, o.width, o.height + reach).intersects(crate)) continue
            top = minOf(top, o.top)
            left = minOf(left, o.left)
            right = maxOf(right, o.right)
        }
        if (top == Double.MAX_VALUE || top <= crate.centerY) return false
        val across = minOf(right, crate.right) - maxOf(left, crate.left)
        return crate.bottom - top <= across
    }

    /** A cart's whole drawn rect, handles included, loaded or not. */
    private fun cartArt(cart: PushCart): Rect = Rect(cart.x, cart.y, cart.width, cart.height)

    /**
     * [o] is touching [crate]'s underside, not a side: overlapping it (or within [reach] under
     * it), with its own top in the crate's lower half and the contact shallower through the
     * bottom face than across. See MovingPlatformDef.crushesOnlyFromBelow.
     */
    private fun touchesUnderside(o: Rect, crate: Rect, reach: Double): Boolean {
        if (!Rect(o.x, o.y - reach, o.width, o.height + reach).intersects(crate)) return false
        if (o.top <= crate.centerY) return false
        val across = minOf(o.right, crate.right) - maxOf(o.left, crate.left)
        val up = crate.bottom - o.top
        return up <= across
    }

    /**
     * A crushing load hanging low enough to be in a cart's way stops it from the side, like a
     * wall - running a cart into one is not being crushed (only its underside does that).
     */
    private fun crusherBlocksCart(mb: Rect, cart: PushCart): Boolean {
        val art = cartArt(cart)
        return mb.bottom > art.top + 0.5 && mb.top < art.bottom
    }

    /** A loose crate lying still low enough to stand in a cart's way. */
    private fun blocksCart(hc: HookCrate, cart: PushCart): Boolean =
        hc.physical && hc.carriedByCartId == null && hc.body?.isAsleep == true && hc.bounds.bottom > cart.y + 1.0

    /**
     * How far [cart] can go right now: its own travel limits, narrowed by any crate lying on the
     * floor in its path - a cart stops against a dropped load rather than passing through it.
     */
    private fun cartMinX(cart: PushCart): Double {
        var m = cart.minX
        for (hc in hookCrates) {
            if (blocksCart(hc, cart) && hc.bounds.centerX < cart.x + cart.width / 2.0) m = maxOf(m, hc.bounds.right)
        }
        for (mp in movingPlatforms) {
            if (!mp.crushesOnContact || !mp.crushesOnlyFromBelow) continue
            val mb = mp.bounds
            if (mb.right <= cart.x + 0.5 && crusherBlocksCart(mb, cart)) m = maxOf(m, mb.right)
        }
        return m
    }

    private fun cartMaxX(cart: PushCart): Double {
        var m = cart.maxX
        for (hc in hookCrates) {
            if (blocksCart(hc, cart) && hc.bounds.centerX >= cart.x + cart.width / 2.0) m = minOf(m, hc.bounds.left - cart.width)
        }
        for (mp in movingPlatforms) {
            if (!mp.crushesOnContact || !mp.crushesOnlyFromBelow) continue
            val mb = mp.bounds
            if (mb.left >= cart.x + cart.width - 0.5 && crusherBlocksCart(mb, cart)) m = minOf(m, mb.left - cart.width)
        }
        return m
    }

    /** A cart nobody is holding rolls on whatever a crate's impact gave it, and slows to a stop. */
    private fun rollCarts(dt: Double) {
        for (cart in pushCarts) {
            if (cart === grippedCart || isEchoCart(cart)) {
                cart.vx = 0.0
                continue
            }
            if (cart.vx == 0.0) continue
            val decel = PushCart.ROLLING_FRICTION * dt
            cart.vx = if (kotlin.math.abs(cart.vx) <= decel) 0.0 else cart.vx - decel * kotlin.math.sign(cart.vx)
            val nx = cart.x + cart.vx * dt
            val lo = cartMinX(cart)
            val hi = cartMaxX(cart)
            if (nx < lo || nx > hi) {
                cart.x = nx.coerceIn(minOf(lo, cart.x), maxOf(hi, cart.x))
                cart.vx = 0.0
            } else {
                cart.x = nx
            }
        }
    }

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

    // ---- what is being pushed (LevelLayout.pushCarts) --------------------------------------
    //
    // The demo stage braces against thin air. A shipped level braces against one of these, and
    // the difference is entirely in how the stance starts and ends: range instead of a bare
    // button press, exactly as the lever and camera-bot interactions gate themselves.
    //
    // While a cart is held the player is PINNED to it at the offset he grabbed it at. That one
    // decision settles everything else: the cart cannot shove him, he cannot walk into it, the
    // cart's own travel limits become limits on him, and push and pull are the same code with
    // the sign of his movement flipped.

    /** The cart currently in hand, or null. */
    var grippedCart: PushCart? = null
        private set

    /** +1 when the held cart is to the player's right, -1 when it is to his left. */
    var pushCartSide: Double = 0.0
        private set

    /**
     * +1 while the load is being shoved away from the body, -1 while it is being dragged back.
     *
     * The scene plays the one push clip at this sign, so a pull is that gait run backwards - the
     * footage is a body leaning into a load, and a body leaning into a load that is retreating
     * looks the same played in reverse. Holds its last value while standing still, so stopping
     * mid-pull does not snap the pose.
     */
    var pushGaitDirection: Double = 1.0
        private set

    /**
     * Player x + this = cart x. Eased from wherever the grab happened to the offset that puts
     * the hands ON the cart - see [pushCartContactOffsetX] and [settleIntoCart].
     */
    private var pushCartGripOffsetX: Double = 0.0

    /** The offset at the instant of the grab, i.e. how far short of contact he reached from. */
    private var pushCartGripStartOffsetX: Double = 0.0

    /** Where the cart stood when it was taken hold of - it does not move during the settle. */
    private var pushCartAnchorX: Double = 0.0

    /**
     * Where the braced fist is, in world x. Only meaningful while a cart is held.
     *
     * The push pose reaches [PushCart.BRACED_FIST_REACH_PER_HEIGHT] of the body's height forward
     * of its own centre, and "forward" is whichever side the cart is on - the sprite is mirrored
     * for a left-hand grab, so [pushCartSide] is the sign.
     */
    val bracedFistX: Double
        get() = player.x + player.width / 2.0 +
            pushCartSide * player.visualHeight * PushCart.BRACED_FIST_REACH_PER_HEIGHT

    /**
     * The offset that lands the braced fist on the cart's near handle.
     *
     * It used to put his leading EDGE on the cart's face, which is a different thing: the hand
     * reaches well past the body's box, so flush-to-the-face left the fist about three units
     * inside the cart, over the load rather than on the handle. Solving for the hand instead of
     * the box leaves a small gap between the two collision boxes, which is correct - a man
     * pushing a trolley stands off it by the length of his arms.
     */
    private fun pushCartContactOffsetX(cart: PushCart): Double {
        val fromLeft = pushCartSide > 0.0
        val gripFromCartX = cart.handleGripX(fromLeft) - cart.x
        val reach = player.visualHeight * PushCart.BRACED_FIST_REACH_PER_HEIGHT
        return player.width / 2.0 + pushCartSide * reach - gripFromCartX
    }

    private fun gripCart(cart: PushCart) {
        grippedCart = cart
        pushCartSide = if (cart.bounds.left >= player.bounds.right - 2.0) 1.0 else -1.0
        pushCartGripStartOffsetX = cart.x - player.x
        pushCartGripOffsetX = pushCartGripStartOffsetX
        pushCartAnchorX = cart.x
        pushGaitDirection = 1.0
        isPushStanceHeld = true
    }

    /**
     * Walks the body the last few units into contact while it bends, over the same clock as the
     * lean-in.
     *
     * PushCart.GRIP_REACH lets the grab happen up to 22 units short of the cart, and pinning the
     * offset AS GRABBED froze that gap in for the whole push - press from a step back and the
     * hands stayed a step back, visibly not touching what he was pushing. The gap is closed here
     * instead of by narrowing the reach, because a reach tight enough to guarantee contact is a
     * reach the player has to line up by hand.
     *
     * Driven by [pushStanceBlend] rather than by a speed, so the arrival and the settled pose
     * land on the same frame by construction, however far he reached from. The cart is held at
     * [pushCartAnchorX] throughout: he is stepping up to it, not shoving it while still bending.
     */
    private fun settleIntoCart(cart: PushCart) {
        // [pushCartGripOffsetX] is re-derived from the blend by the caller.
        player.x = pushCartAnchorX - pushCartGripOffsetX
        cart.x = pushCartAnchorX
    }

    private fun releaseCart() {
        grippedCart = null
        pushCartSide = 0.0
        isPushStanceHeld = false
    }

    /** True while the body is still bending into a cart it has hold of - see [settleIntoCart]. */
    val isSettlingIntoCart: Boolean get() = grippedCart != null && pushStanceBlend < 1.0

    private fun resetPushStance() {
        isPushStanceHeld = false
        pushStanceBlend = 0.0
        pushInteractWasDown = false
        grippedCart = null
        pushCartSide = 0.0
        pushGaitDirection = 1.0
        pushCartGripOffsetX = 0.0
        pushCartGripStartOffsetX = 0.0
        pushCartAnchorX = 0.0
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
        for (cart in pushCarts) cart.reset()
        for (crate in conveyorCrates) crate.reset()
        for (laser in lasers) laser.reset()
        for (lever in levers) lever.reset()
        for (hc in hookCrates) hc.reset(atSweepClock = hc.sweepClock)
        for (b in cameraBots) b.reset()
        for (f in fans) f.reset()
        for (p in steamPipes) p.reset()
        // Level 8: the failed attempt is cut from the recording (RunRecorder.rewindToCheckpoint).
        runRecorder?.let { rec ->
            rec.rewindToCheckpoint()
            // Mechanisms reset: the replay does the same, so its lever and its catch play again.
            rec.event(runClock, RunEventKind.RESET, "")
        }
        // Level 9: the echo is not his to reset - it carries on, and what it has already done to
        // the level (the lever, the catch, the bots) stands.
        echo?.let { e ->
            for (ev in e.passedEvents()) applyEchoEvent(ev)
            applyEchoCarts(e.current)
        }
        followRecordedWorld()
        isEchoDetecting = false
        // Level 9: the fall that ended the attempt is behind him - the ground rule applies again.
        touchedGround = false
        bonusTracker?.onRespawn()
        resetEscort(toCheckpoint = true)
        activePowerups.invisibilityTimer = 3.0
        laserGraceTimer = 3.0
        spawnGraceTimer = 3.0
        resetPushStance()
        resetWindStance()
        isBoardingFromFloorLevel = true
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
        touchedGround = false
        timeTaken = 0.0f
        totalElapsedSeconds = 0.0
        runStartSeconds = null
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
        for (cart in pushCarts) cart.reset()
        for (crate in conveyorCrates) crate.reset()
        for (laser in lasers) laser.reset()
        for (lever in levers) lever.reset()
        for (hc in hookCrates) hc.reset()
        for (b in cameraBots) b.reset()
        for (f in fans) f.reset()
        for (p in steamPipes) p.reset()
        runRecorder?.clear()
        echo?.let {
            it.reset()
            applyEchoCarts(it.current)
            if (it.recording.hasWorld) totalElapsedSeconds = it.recording.worldStart
        }
        followRecordedWorld()
        isEchoDetecting = false
        bonusTracker?.reset()
        escortSnapshot = null
        switchThrowCount = 0
        switchThrows.clear()
        hasReusedASwitch = false
        resetEscort(toCheckpoint = false)
        activePowerups.invisibilityTimer = 0.0
        laserGraceTimer = 0.0
        resetPushStance()
        resetWindStance()
        isBoardingFromFloorLevel = true
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
        if (!pushStanceDemo && pushCarts.isEmpty()) return
        val pressed = interactInput && !pushInteractWasDown
        pushInteractWasDown = interactInput

        if (pushStanceDemo) {
            // The bare stage: no object, so the button alone is the whole gate.
            if (pressed) isPushStanceHeld = !isPushStanceHeld
        } else if (pressed) {
            val held = grippedCart
            if (held != null) {
                releaseCart()
            } else {
                // Nearest face wins when two carts somehow overlap one reach; in practice there
                // is one, and firstOrNull is what every other in-range interaction here uses.
                pushCarts.firstOrNull { it.canGrip(player) }?.let { gripCart(it) }
            }
        }

        val rate = if (isPushStanceHeld) dt / PUSH_STANCE_ENTER_SECONDS else -dt / PUSH_STANCE_EXIT_SECONDS
        pushStanceBlend = (pushStanceBlend + rate).coerceIn(0.0, 1.0)
    }

    /**
     * Carries the held cart along with the body, and clamps the body to the cart's own travel.
     *
     * Called straight after [Player.update], because the cart follows what the player ACTUALLY
     * did rather than what he asked for - he may have been stopped by a wall, or by the platform
     * the cart is being walked towards. Clamping him (rather than the cart) is what makes the
     * limits read as the cart running out of room: he is pinned to it, so the two are the same
     * constraint, and doing it this way leaves the stop with the player's own collision feel
     * instead of the cart sliding out of his hands at the end of its rail.
     */
    private fun followGrippedCart(cart: PushCart, playerXBefore: Double) {
        // Re-derive the offset from the blend EVERY tick, including at a full 1.0 - not only
        // while the settle is running. The blend is advanced earlier in the same update than
        // this runs, so on the frame it crosses into 1.0 the branch below is already the pinned
        // one; without recomputing here that frame would pin whatever partial offset the last
        // settle tick happened to leave behind, and the hands would stop a fraction of a unit
        // short for the rest of the push. That fraction is the whole bug this was fixing.
        val t = pushStanceBlend.coerceIn(0.0, 1.0)
        val target = pushCartContactOffsetX(cart)
        pushCartGripOffsetX = pushCartGripStartOffsetX + (target - pushCartGripStartOffsetX) * t

        // Still bending: the settle owns both positions, and nothing is being pushed yet.
        if (t < 1.0) {
            settleIntoCart(cart)
            return
        }
        // The first fully-braced frame. Land exactly on contact before the pin below takes over:
        // the pin derives the CART's position from the body's, so arriving a fraction short here
        // would shove the cart by that fraction instead of closing the gap. Collapsing the start
        // offset onto the target both does that and retires the lerp - from here the offset is
        // simply the contact offset, and this branch is a no-op.
        if (pushCartGripStartOffsetX != target) {
            pushCartGripStartOffsetX = target
            player.x = cart.x - target
        }
        val lo = cartMinX(cart)
        val hi = cartMaxX(cart)
        val minPlayerX = lo - pushCartGripOffsetX
        val maxPlayerX = hi - pushCartGripOffsetX
        if (minPlayerX <= maxPlayerX) {
            player.x = player.x.coerceIn(minPlayerX, maxPlayerX)
        }
        cart.x = (player.x + pushCartGripOffsetX).coerceIn(minOf(lo, cart.x), maxOf(hi, cart.x))

        val dx = player.x - playerXBefore
        if (dx != 0.0) {
            // Moving towards the cart is a push, away from it is a pull - see [pushGaitDirection].
            pushGaitDirection = if (dx * pushCartSide > 0.0) 1.0 else -1.0
        }
    }

    private val recentlySeeingGuards = LinkedHashSet<Guard>()

    // ---- the recorded run (LevelData.recordsRun) and its replay (LevelData.replaysRunOf) ----

    /** Records this run for a later level to replay - level 8's, which level 9 follows. */
    val runRecorder: RunRecorder? = if (levelData.recordsRun) RunRecorder(pushCarts.map { it.id }, worldTrackNames()) else null

    /**
     * The parts of the level that keep their own time rather than following the level clock -
     * each camera's sweep, each bot's patrol, each hook rig's travel. The lasers and the moving
     * loads need nothing: they are a function of the clock, which the replay starts at
     * [RunRecording.worldStart]. See [RunRecording.worldTracks] for the names.
     */
    private fun worldTrackNames(): List<String> =
        cameras.indices.map { "cam$it" } + cameraBots.map { "bot:${it.id}" } + hookCrates.map { "hook:${it.id}" }

    private fun worldTrackValues(): DoubleArray {
        val out = DoubleArray(cameras.size + cameraBots.size + hookCrates.size)
        var i = 0
        for (c in cameras) out[i++] = c.currentAngle
        for (b in cameraBots) out[i++] = if (b.facing < 0.0) -b.x else b.x
        for (hc in hookCrates) out[i++] = hc.sweepClock
        return out
    }

    // ---- level 9: the level played back with the run (RunRecording.worldTracks) ----
    //
    // "make sure the starting position of everything is recorded at level 8 when the player starts
    // and ... exactly playbacked. otherwise it looks like he goes through objects and lasers in
    // level 9". Each camera, bot and rig here follows level 8's recording, so what the echo walked
    // past is where it was - UNTIL this level's player gets involved with it: a camera or bot that
    // spots him, a bot he switches off, a rig he swings from carries on live from where it is. A
    // respawn puts every one back on the recording.

    private val followedCameras = BooleanArray(cameras.size)
    private val followedBots = BooleanArray(cameraBots.size)
    private val followedHooks = BooleanArray(hookCrates.size)

    /** Index of each world track in the replayed recording, or -1 - see [followRecordedWorld]. */
    private var cameraTrack = IntArray(0)
    private var botTrack = IntArray(0)
    private var hookTrack = IntArray(0)

    /** Puts every camera, bot and rig back on the echo's recording (a no-op without one). */
    private fun followRecordedWorld() {
        val rec = echo?.recording ?: return
        if (!rec.hasWorld) return
        cameraTrack = IntArray(cameras.size) { rec.worldTracks.indexOf("cam$it") }
        botTrack = IntArray(cameraBots.size) { rec.worldTracks.indexOf("bot:${cameraBots[it].id}") }
        hookTrack = IntArray(hookCrates.size) { rec.worldTracks.indexOf("hook:${hookCrates[it].id}") }
        for (i in cameras.indices) followedCameras[i] = cameraTrack[i] >= 0
        for (i in cameraBots.indices) followedBots[i] = botTrack[i] >= 0
        for (i in hookCrates.indices) followedHooks[i] = hookTrack[i] >= 0
        applyRecordedWorld(echo!!.current)
    }

    /** Sets every followed camera, bot and rig to [sample]'s recorded state. */
    private fun applyRecordedWorld(sample: RunSample) {
        val w = sample.world
        for (i in cameras.indices) if (followedCameras[i] && cameraTrack[i] < w.size) cameras[i].currentAngle = w[cameraTrack[i]]
        for (i in cameraBots.indices) {
            if (!followedBots[i] || botTrack[i] >= w.size) continue
            val v = w[botTrack[i]]
            cameraBots[i].x = kotlin.math.abs(v)
            cameraBots[i].facing = if (v < 0.0) -1.0 else 1.0
        }
    }

    /** A camera that has spotted this level's player stops following the recording. */
    private fun releaseCamera(c: Camera) {
        val i = cameras.indexOf(c)
        if (i < 0 || !followedCameras[i]) return
        followedCameras[i] = false
        // Carry on the way the recording was turning.
        val rec = echo?.recording
        if (rec != null && cameraTrack[i] >= 0) {
            val now = rec.sampleAt(echo!!.clock).world.getOrNull(cameraTrack[i])
            val before = rec.sampleAt(echo!!.clock - rec.step).world.getOrNull(cameraTrack[i])
            if (now != null && before != null && now != before) c.sweepDirection = if (now > before) 1.0 else -1.0
        }
    }

    private fun releaseBot(b: CameraBot) {
        val i = cameraBots.indexOf(b)
        if (i >= 0) followedBots[i] = false
    }

    /** The recorded rig clock for hook crate [i] this tick, or null when it runs on its own. */
    private fun recordedHookClock(i: Int): Double? {
        if (!followedHooks.getOrElse(i) { false }) return null
        return echo?.current?.world?.getOrNull(hookTrack[i])
    }

    /**
     * The recorded run, only once it has succeeded - null until the exit is reached, and null again
     * after a restart. "make sure only successful runs are recorded. if he completes the level
     * again new one should replace the old one": this is the only thing the scene saves, and it
     * saves it over the last one every time.
     */
    val completedRun: RunRecording?
        get() = if (isLevelComplete) runRecorder?.finish() else null

    /** The recorded run being played back as a watcher that sees and hears like a guard. */
    var echo: EchoRunner? = null
        private set

    /** True for the frames the [echo] has eyes on the player - the scene's pip over it. */
    var isEchoDetecting: Boolean = false
        private set

    /** Level 9: plays [recording] back from the start of the run. */
    fun attachEcho(recording: RunRecording) {
        echo = EchoRunner(recording).also { applyEchoCarts(it.current) }
        // The level's clock starts where the run's did, so the lasers and loads are in the phase
        // they were in when it passed them.
        if (recording.hasWorld) totalElapsedSeconds = recording.worldStart
        followRecordedWorld()
    }

    private fun isEchoCart(cart: PushCart): Boolean = echo?.recording?.cartIds?.contains(cart.id) == true

    /** The carts go where the recording had them, unless the player has one in hand. */
    private fun applyEchoCarts(sample: RunSample) {
        val rec = echo?.recording ?: return
        for (i in rec.cartIds.indices) {
            if (i >= sample.cartXs.size) break
            val cart = pushCarts.firstOrNull { it.id == rec.cartIds[i] } ?: continue
            if (cart === grippedCart) continue
            cart.x = sample.cartXs[i]
            cart.vx = 0.0
        }
    }

    private fun updateEcho(dt: Double) {
        val e = echo ?: return
        val sample = e.update(dt) { applyEchoEvent(it) }
        applyEchoCarts(sample)
    }

    private fun applyEchoEvent(ev: RunEvent) {
        when (ev.kind) {
            RunEventKind.LEVER -> levers.firstOrNull { it.id == ev.id && !it.isActivated }?.let { triggerLever(it) }
            // Only a bot the recorded body could actually reach - a run saved before
            // CameraBot.isReachableFrom holds switch-offs made from under the table.
            RunEventKind.BOT -> cameraBots.firstOrNull { it.id == ev.id && !it.isDeactivated }
                ?.takeIf { b -> echo?.let { b.isReachableFrom(it.bounds.bottom) } != false }
                ?.deactivate()
            RunEventKind.CATCH -> echoCatch(ev.id)
            // Level 8 respawned here and its mechanisms went back to the start. Level 9 keeps an
            // emptied hook empty - the swing is the player's road, and his run pulls the lever
            // again anyway - so nothing is undone; the carts already follow the recording.
            RunEventKind.RESET -> Unit
        }
    }

    /**
     * The recorded run caught the load in a cart here. The replay's own drop is live physics, and
     * the level's frame times are not the recording's, so it can come down a little differently -
     * the catch is made good: the load is put in the cart the run caught it in.
     */
    private fun echoCatch(id: String) {
        val (crateId, cartId) = id.split('>', limit = 2).takeIf { it.size == 2 } ?: return
        val hc = hookCrates.firstOrNull { it.id == crateId } ?: return
        val cart = pushCarts.firstOrNull { it.id == cartId } ?: return
        if (hc.carriedByCartId == cartId) return
        if (!hc.isDetached) hc.detach()
        hc.body?.let { b ->
            b.angle = 0.0
            b.omega = 0.0
            b.vx = 0.0
            b.vy = 0.0
        }
        hc.carryOffsetX = cart.loadBounds.centerX - cart.x - hc.bounds.width / 2.0
        hc.bounds = Rect(cart.x + hc.carryOffsetX, cart.deckY - hc.bounds.height, hc.bounds.width, hc.bounds.height)
        hc.body?.let { it.cx = hc.bounds.centerX; it.cy = hc.bounds.centerY }
        hc.carriedByCartId = cartId
        hc.isLanded = true
        cart.isLoaded = true
    }

    /**
     * The level clock when the player first walked, or null until then. Level 8's recording starts
     * there, not at the level's start ("starting to record in level 8 should only start when
     * starting to walk"), so a run holds no idle lead-in and level 9's echo - which plays from
     * level 9's own start - sets off at once.
     */
    var runStartSeconds: Double? = null
        private set

    /** Seconds of the recorded run so far - see [runStartSeconds]. */
    private val runClock: Double get() = totalElapsedSeconds - (runStartSeconds ?: totalElapsedSeconds)

    private fun captureRunSample() {
        val rec = runRecorder ?: return
        if (runStartSeconds == null) return
        rec.capture(runClock) {
            val p = player
            var flags = 0
            if (p.facing < 0.0) flags = flags or RunSample.FACING_LEFT
            if (p.isGrounded) flags = flags or RunSample.GROUNDED
            if (p.isCrouching) flags = flags or RunSample.CROUCHING
            if (p.isClimbing) flags = flags or RunSample.CLIMBING
            if (p.isSwinging) flags = flags or RunSample.SWINGING
            if (!isPushStanceIdle) flags = flags or RunSample.PUSHING
            if (p.vy < 0.0) flags = flags or RunSample.RISING
            RunSample(
                x = p.x,
                y = p.y,
                height = p.currentHeight,
                flags = flags,
                phase = if (p.isClimbing) p.climbProgress else 0.0,
                cartXs = DoubleArray(rec.cartIds.size) { i -> pushCarts.firstOrNull { it.id == rec.cartIds[i] }?.x ?: 0.0 },
                spriteX = p.width / 2.0,
                spriteY = p.height,
                rotation = if (p.isSwinging) p.swingRotationDegrees else 0.0,
                world = worldTrackValues()
            )
        }
    }

    /** Activates a lever exactly as walking up and pressing interact would - shared by the normal
     *  in-range interact path and REMOTE_TRIGGER's remote one below. */
    private fun triggerLever(lever: Lever) {
        lever.isActivated = true
        if (lever.targetMechanismId != null) {
            for (hc in hookCrates) {
                if (hc.id == lever.targetMechanismId) {
                    hc.detach()
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
            // A lever the run threw from afar is still a lever level 9's echo has to throw.
            runRecorder?.event(runClock, RunEventKind.LEVER, target.id)
            bonusTracker?.onGadgetUsed()
            return true
        }
        activePowerups.activate(type)
        if (type == PowerupType.SMOKE_SCREEN) {
            for (c in cameras) c.resetDetectionPause()
        }
        bonusTracker?.onGadgetUsed()
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
            // Star 2: the level's own optional objective where it has one - see LevelResult.star2.
            wasDetected = bonusTracker?.let { bonusObjectiveState != ObjectiveState.MET } ?: wasDetected,
            timeTaken = timeTaken,
            timeTargetSeconds = levelData.timeTargetSeconds
        )
    }

    /**
     * True while the body stands on something that will still be there after a respawn: not a
     * moving load, a hanging hook crate or a cart (all of which a respawn puts back somewhere
     * else), and on a [LevelData.stayOffTheGround] level not the floor either. An automatic
     * checkpoint is only taken here - level 9 is nearly all moving loads, and a checkpoint on one
     * respawned the body in mid-air, to fall to the floor and fail again.
     */
    private fun isOnFixedFooting(): Boolean {
        val feet = player.y + player.height
        val footCenter = player.x + player.width / 2.0
        fun under(r: Rect) = kotlin.math.abs(feet - r.top) < 4.5 && footCenter >= r.left && footCenter <= r.right
        if (movingPlatforms.any { under(it.bounds) }) return false
        if (hookCrates.any { under(it.bounds) }) return false
        if (pushCarts.any { under(it.bounds) }) return false
        if (levelData.stayOffTheGround && offLimitFootholds.any(::under)) return false
        return true
    }

    /** The level's optional objective ([LevelData.bonusObjective]); null on a level without one. */
    val bonusTracker: BonusObjectiveTracker? = levelData.bonusObjective?.let { BonusObjectiveTracker(it) }

    val bonusObjectiveState: ObjectiveState
        get() = bonusTracker?.state(isLevelComplete) ?: ObjectiveState.OPEN

    /** Every hanging load's rect this tick: static hanging crates, moving loads, hanging hook crates. */
    private fun hangingCrateRects(): List<Rect> {
        val out = ArrayList<Rect>()
        out.addAll(hangingCrateVariant1)
        out.addAll(hangingCrateVariant2)
        for (mp in movingPlatforms) out.add(mp.bounds)
        for (hc in hookCrates) if (hc.isHanging) out.add(hc.bounds)
        return out
    }

    private fun observeBonusObjective(groundY: Double) {
        val tracker = bonusTracker ?: return
        val objective = tracker.objective
        var onHangingCrate = false
        var atFloorLevel = false
        var touchingHangingCrate = false
        if (objective == BonusObjective.NO_DROP_FROM_HANGING_CRATES && player.isGrounded) {
            val feet = player.y + player.height
            val footCenter = player.x + player.width / 2.0
            onHangingCrate = hangingCrateRects().any {
                kotlin.math.abs(feet - it.top) < 4.5 && footCenter >= it.left && footCenter <= it.right
            }
            atFloorLevel = feet >= groundY - FLOOR_LEVEL_BAND
        }
        if (objective == BonusObjective.NEVER_TOUCH_A_HANGING_CRATE) {
            // Collision leaves a body exactly flush against what it stands on or walks into, so
            // "touching" is overlapping a box grown by a hair on every side - not a whole unit,
            // which level 8's ground road passes under the bobbing chain with to spare.
            val b = player.bounds
            val reach = Rect(b.x - TOUCH_MARGIN, b.y - TOUCH_MARGIN, b.width + 2 * TOUCH_MARGIN, b.height + 2 * TOUCH_MARGIN)
            touchingHangingCrate = hangingCrateRects().any { it.intersects(reach) }
        }
        tracker.observe(
            isJumping = player.isJumping,
            isClimbing = player.isClimbing,
            isCrouching = player.isCrouching,
            isSwinging = player.isSwinging,
            isGrounded = player.isGrounded,
            onHangingCrate = onHangingCrate,
            touchingHangingCrate = touchingHangingCrate,
            atFloorLevel = atFloorLevel,
            alertRaised = alertProgress > 0.0 || wasDetected || spottedCount > 0,
            seenByFigure = isEchoDetecting,
            switchReused = hasReusedASwitch,
            prisonerSeen = isPrisonerSeen,
            deactivatedBots = if (objective == BonusObjective.DISABLE_ALL_SECURITY_BOTS)
                cameraBots.filter { it.isDeactivated }.map { it.id } else emptyList(),
            botCount = cameraBots.size
        )
    }

    /**
     * Level 9's rule (LevelData.stayOffTheGround): set the moment the player stands on the floor or
     * on a LevelLayout.offLimitFootholds platform, which is Mission Failed.
     */
    var touchedGround: Boolean = false
        private set

    /** Fired once, on the frame [touchedGround] first becomes true. */
    var onTouchedGround: (() -> Unit)? = null

    // The floor is the layout's own platforms list - GameWorld's [platforms] also carries every
    // box, crates included, which are exactly what a stay-off-the-ground run stands on.
    private val offLimitFootholds: List<Rect> =
        levelData.layout?.let { it.platforms + it.offLimitFootholds }.orEmpty()

    private fun checkStayOffTheGround() {
        if (!levelData.stayOffTheGround || touchedGround || isGameOver || isLevelComplete || !player.isGrounded) return
        val feet = player.y + player.height
        val footCenter = player.x + player.width / 2.0
        val onGround = offLimitFootholds.any {
            kotlin.math.abs(feet - it.top) < 1.5 && footCenter >= it.left && footCenter <= it.right
        }
        if (onGround) {
            touchedGround = true
            onTouchedGround?.invoke()
            isGameOver = true
            onGameOver?.invoke()
        }
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
        if (runStartSeconds == null && moveInput != 0.0) {
            runStartSeconds = totalElapsedSeconds
            runRecorder?.worldStart = totalElapsedSeconds
        }

        if (interactInput) {
            for (lever in levers) {
                if (!lever.isActivated && lever.isPlayerInRange(player)) {
                    triggerLever(lever)
                    runRecorder?.event(runClock, RunEventKind.LEVER, lever.id)
                }
            }
            for (bot in cameraBots) {
                if (!bot.isDeactivated && bot.canDeactivate(player)) {
                    bot.deactivate()
                    releaseBot(bot)
                    runRecorder?.event(runClock, RunEventKind.BOT, bot.id)
                    onCameraBotDeactivated?.invoke(bot)
                }
            }
        }

        updateDoorsAndLifts(dt, interactInput)
        refreshVisionOccluders()

        updatePushStance(dt, interactInput)
        // Where eyes look for him (Player.keyPoints) follows the braced lean - see braceLean.
        player.braceLean = if (grippedCart != null && pushStanceBlend >= 0.5) pushCartSide else 0.0

        updateEcho(dt)

        updateFans(dt, forwardTap, moveInput)

        // A bot with eyes on the player holds where it is, the way a guard does (see the guard
        // loop below: a seeing guard is not walked on) - "robots should also stay at one place if
        // they start to spot you". It picks its patrol up again once it loses sight.
        for ((i, bot) in cameraBots.withIndex()) {
            if (followedBots[i]) continue
            if (bot in detectingCameraBots) continue
            bot.update(dt)
        }
        echo?.let { applyRecordedWorld(it.current) }

        val groundY = platforms.firstOrNull { it.y > 300.0 }?.y ?: 440.0
        updateHookCrates(dt, groundY)
        if (isGameOver) return

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
                    val d = VisionSystem.getPlayerSpottedDistance(g, player, visionOccluders)
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
                    val d = VisionSystem.getPlayerSpottedDistance(c, player, visionOccluders)
                    if (d != null) {
                        c.onPlayerSpotted()
                        releaseCamera(c)
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
                            occluders = visionOccluders
                        )
                        if (d != null) {
                            seeingCameraBots.add(b)
                            releaseBot(b)
                            if (spottedDist == null || d < spottedDist) {
                                spottedDist = d
                                detectorRange = b.visionRange
                            }
                        }
                    }
                }
            }
        }

        // The echo watches like a guard (Phantom Cloak puts it to sleep too).
        var echoSees = false
        val e = echo
        if (e != null && !activePowerups.isInvisibilityActive && spawnGraceTimer <= 0.0 && !activePowerups.isPhantomCloakActive) {
            val d = VisionSystem.getPlayerSpottedDistance(e.eyePosition, e.facingAngle, e.visionRange, e.visionFov, player, visionOccluders)
            if (d != null) {
                echoSees = true
                if (spottedDist == null || d < spottedDist) {
                    spottedDist = d
                    detectorRange = e.visionRange
                }
            }
        }
        e?.see(echoSees)
        isEchoDetecting = echoSees

        // Level 11: the prisoner is looked for by the same eyes, into the same meter. The player's
        // own gadgets do not hide him (invisibility is the operative's), but a sleeping guard or a
        // blinded camera sees nobody.
        var prisonerSeen = false
        val pr = prisoner
        if (pr != null && pr.isFree && !pr.hasEscaped && spawnGraceTimer <= 0.0) {
            // Every eye that could see him, as (distance or null, its range).
            val looks = ArrayList<Pair<Double?, Double>>()
            if (!activePowerups.isPhantomCloakActive) {
                for (g in allGuards) {
                    val d = VisionSystem.getPlayerSpottedDistance(g, pr.body, visionOccluders)
                    if (d != null && g !in seeingGuards) seeingGuards.add(g)
                    looks.add(d to g.visionRange)
                }
            }
            if (!activePowerups.isSmokeScreenActive) {
                for (c in cameras) {
                    val d = VisionSystem.getPlayerSpottedDistance(c, pr.body, visionOccluders)
                    if (d != null && c !in seeingCameras) {
                        c.onPlayerSpotted()
                        seeingCameras.add(c)
                    }
                    looks.add(d to c.visionRange)
                }
                for (b in cameraBots) {
                    if (b.isDeactivated) continue
                    val d = VisionSystem.getPlayerSpottedDistance(b.eyePosition, b.facingAngle, b.visionRange, b.visionFov, pr.body, visionOccluders)
                    if (d != null && b !in seeingCameraBots) seeingCameraBots.add(b)
                    looks.add(d to b.visionRange)
                }
            }
            for ((d, range) in looks) {
                if (d == null) continue
                prisonerSeen = true
                val best = spottedDist
                if (best == null || d < best) {
                    spottedDist = d
                    detectorRange = range
                }
            }
        }
        isPrisonerSeen = prisonerSeen

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
                if (prisonerSeen) prisonerLost = true
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
            val esc = prisoner
            if (esc != null) {
                // An escort level's checkpoints are the prisoner's: taken when HE is standing held
                // in one (behind a shut door), unseen, so a respawn puts both of them back somewhere
                // safe. The player comes back at the checkpoint's own spot.
                for (i in manualCheckpoints.indices) {
                    val cp = manualCheckpoints[i]
                    val zone = cp.triggerZone ?: continue
                    if (i > currentManualCheckpointIndex && esc.isFree && esc.isHeld && esc.body.isGrounded &&
                        !isPrisonerSeen && esc.bounds.intersects(zone)
                    ) {
                        currentManualCheckpointIndex = i
                        lastCheckpointX = cp.x
                        lastCheckpointY = cp.y
                        hasAdvancedCheckpoint = true
                        takeEscortSnapshot(esc)
                        onCheckpointSecured?.invoke(lastCheckpointX, lastCheckpointY)
                    }
                }
            } else if (manualCheckpoints.isEmpty()) {
                if (player.x > lastCheckpointX + 250.0 && isOnFixedFooting()) {
                    lastCheckpointX = player.x
                    lastCheckpointY = player.y
                    hasAdvancedCheckpoint = true
                    runRecorder?.markCheckpoint()
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
                        runRecorder?.markCheckpoint()
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
            val pCrush = crushParts()
            for (mp in movingPlatforms) {
                if (!mp.crushesOnContact) continue
                // A body standing flush under one is not touching it; one rising into it is
                // stopped flush by the collision pass, so off the ground a unit's gap counts.
                val crushed = if (mp.crushesOnlyFromBelow) comesDownOn(pCrush, mp.bounds, if (player.isGrounded) 0.0 else 1.0)
                else pCrush.any { it.intersects(mp.bounds) && it.bottom > mp.bounds.bottom }
                if (crushed) {
                    isGameOver = true
                    onGameOver?.invoke()
                    onHangingCrateHit?.invoke()
                    return
                }
            }
            if (isSqueezedBetweenLoads(pBounds) || crushingLoadOnACart()) {
                isGameOver = true
                onGameOver?.invoke()
                onHangingCrateHit?.invoke()
                return
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
        // A crate riding in a cart is inside the loaded cart's own footprint - nothing to add.
        val hookCrateBounds = if (hasHookCrates) hookCrates.filter { it.carriedByCartId == null }.map { it.bounds } else emptyList()
        // A cart is solid wherever it stands - EXCEPT the one in the player's hands, which is
        // left out of his own platform/climb lists (see followGrippedCart: he is pinned to it, so
        // it would be a wall travelling with him). It still blocks sight and still blocks guards.
        val hasPushCarts = pushCarts.isNotEmpty()
        // An empty cart is its deck and the lower half of its two handle posts (PushCart.playerSolids).
        val freePushCartBounds = when {
            !hasPushCarts -> emptyList()
            grippedCart == null -> pushCarts.flatMap { it.playerSolids }
            else -> pushCarts.filter { it !== grippedCart }.flatMap { it.playerSolids }
        }
        val dynamicBounds = if (hasMovingPlatforms || hasConveyorCrates || hasHookCrates || hasPushCarts) {
            movingBounds + crateBounds + hookCrateBounds + freePushCartBounds
        } else emptyList()
        val doorSolids = dynamicDoorSolids()
        val currentPlatforms = (if (dynamicBounds.isNotEmpty()) platforms + dynamicBounds else platforms)
            .let { if (doorSolids.isEmpty()) it else it + doorSolids }
        val currentBoxes = if (dynamicBounds.isNotEmpty()) {
            val dynamicBoxes = movingBounds + floorCrateBounds + hookCrateBounds + freePushCartBounds
            if (dynamicBoxes.isNotEmpty()) boxes + dynamicBoxes else boxes
        } else boxes
        // Sight is blocked by every cart, held or not - the body behind one is behind it either way.
        val currentOccluders = if (dynamicBounds.isNotEmpty() || hasPushCarts) {
            visionOccluders + dynamicBounds + (if (grippedCart != null) listOf(grippedCart!!.bounds) else emptyList())
        } else visionOccluders

        // Guards without eyes on the player keep walking their route (unless asleep from Phantom Cloak)
        if (!isGameOver && !activePowerups.isPhantomCloakActive) {
            for (g in allGuards) {
                val heldAtPost = g.holdUntilPlayerCrouches && !hasPlayerCrouchedOnce
                if (g !in seeingGuards && !heldAtPost) g.update(dt, currentOccluders)
            }
        }

        // Update cameras - paused while Smoke Screen is active
        if (!activePowerups.isSmokeScreenActive) {
            for ((i, c) in cameras.withIndex()) {
                c.update(dt)
                // One on the recording keeps its own timers running (a pause at the end of its
                // sweep reads the same either way) but points where the recording says.
                if (followedCameras[i]) echo?.current?.world?.getOrNull(cameraTrack[i])?.let { c.currentAngle = it }
            }
        }

        playerPlatformsScratch.clear()
        playerPlatformsScratch.addAll(currentPlatforms)
        // A crushing load that has come down into a braced body's box from above is not a wall to
        // it: the box is far taller than the leaning figure (crushParts), and the collision pass
        // shoved the body - and the cart with it - clean out from under the load, the load's whole
        // width in a frame, so one coming down right on top of him never reached anything drawn
        // (2026-09-29: "now the mission does not fail even if the crate comes on top of the person
        // when he is pushing / pulling cart"). It is left out, so it carries on down onto the
        // figure and the crush check judges it; walking further in under it is held off below.
        val overheadLoads = ArrayList<Rect>(0)
        if (grippedCart != null && crushParts().size > 1) {
            val pb = player.bounds
            for (mp in movingPlatforms) {
                if (!mp.crushesOnContact) continue
                val mb = mp.bounds
                if (mb.intersects(pb) && mb.bottom < pb.bottom && mb.top < pb.top) {
                    overheadLoads.add(mb)
                    playerPlatformsScratch.remove(mb)
                }
            }
        }
        for (g in allGuards) playerPlatformsScratch.add(g.bounds)
        // See isBoardingFromFloorLevel: a lid on every hanging load the player may not get onto
        // from where they last stood. Tall enough that its own top is out of any jump from the
        // floor band, so it can be walked into but never stood on.
        val boardingBlocked = if (isBoardingFromFloorLevel) groundBoardingBlockedSurfaces() else emptyList()
        for (b in boardingBlocked) {
            playerPlatformsScratch.add(Rect(b.x, b.top - BOARDING_LID_HEIGHT, b.width, BOARDING_LID_HEIGHT))
        }
        // A cut load stood on its end (HookCrate.isLooseAndNotFlat) can not be got onto at all -
        // not mantled (climbRefused below) and not jumped onto either (from the cart's handle post
        // it is a 44-unit hop): a lid like the one above, tall enough that its own top is out of
        // reach from anything near it.
        for (hc in hookCrates) {
            if (!hc.isLooseAndNotFlat) continue
            val b = hc.bounds
            playerPlatformsScratch.add(Rect(b.x, b.top - LOOSE_LOAD_LID_HEIGHT, b.width, LOOSE_LOAD_LID_HEIGHT))
        }
        val climbTargets = when {
            !canClimb -> emptyList()
            boardingBlocked.isEmpty() -> currentBoxes
            else -> currentBoxes.filter { it !in boardingBlocked }
        }
        // A cut load stood on its end (or still tumbling) is refused the mantle - HookCrate.isLooseAndNotFlat.
        val climbRefused = if (hasHookCrates && hookCrates.any { it.isLooseAndNotFlat }) {
            unclimbableBoxes + hookCrates.filter { it.isLooseAndNotFlat }.map { it.bounds }
        } else unclimbableBoxes
        val climbFloatingTargets = when {
            !canClimb -> emptyList()
            boardingBlocked.isEmpty() -> floatingClimbTargets + hookCrateBounds
            else -> (floatingClimbTargets + hookCrateBounds).filter { it !in boardingBlocked }
        }
        // A hook still carrying its crate cannot be swung from. A travelling rig's hook is wherever
        // it has got to; a fixed one is exactly its declared rect, as before.
        val activeSwingHooks = if (hookCrates.isEmpty()) swingHooks else swingHooks.mapNotNull { hook ->
            val hc = hookCrates.firstOrNull { it.hook == hook }
            when {
                hc == null -> hook
                hc.isDetached -> hc.currentHook
                else -> null
            }
        }
        // A braced body cannot jump, duck or sprint. Suppressing the other two inputs outright
        // (rather than letting them cancel the stance) is what keeps the scene's animation
        // machine honest: every other stance change would otherwise be able to start on a frame
        // where the push clip is still on screen. INTERACT is the only way out.
        // Not gated on pushStanceDemo any more: the blend is only ever non-zero on a level that
        // has a stance to be in at all, so the flag would be saying the same thing twice - and
        // since a cart level reaches here too, it would be saying it wrongly.
        val pushActive = !isPushStanceIdle
        val effectiveMoveInput = when {
            // Bending into a cart: the settle is walking him into contact (see settleIntoCart),
            // and letting him steer at the same time would fight it for the same 0.85s.
            isSettlingIntoCart -> 0.0
            pushActive -> moveInput * PUSH_MOVE_FACTOR
            // In air flow parts normal mechanics should not work.
            // When he presses forward once or long presses it, nothing happens.
            // Backward retreat (moveInput < 0.0) remains allowed.
            isInWindZone -> if (moveInput < 0.0) moveInput else 0.0
            else -> moveInput
        }
        val effectiveJumpInput = jumpInput && !pushActive
        val effectiveCrouchInput = crouchInput && !pushActive
        val playerXBeforeMove = player.x
        player.update(
            dt, effectiveMoveInput, effectiveJumpInput, effectiveCrouchInput, playerPlatformsScratch,
            climbTargets, activeSwingHooks, climbFloatingTargets, climbRefused
        )
        // ...and he may back out from under an overhead load (above) but not push on in under it.
        for (mb in overheadLoads) {
            val before = overlapX(playerXBeforeMove, mb)
            val after = overlapX(player.x, mb)
            if (after > before) player.x += if (player.x > playerXBeforeMove) -(after - before) else after - before
        }
        grippedCart?.let { followGrippedCart(it, playerXBeforeMove) }
        // A jump or climb into an underside-only crusher (MovingPlatformDef.crushesOnlyFromBelow)
        // is stopped by the move just made - flush, or knocked a few units back down - so it is
        // caught here, within a short reach under it.
        if (!isGameOver && !isLevelComplete && !player.isGrounded) {
            for (mp in movingPlatforms) {
                if (!mp.crushesOnContact || !mp.crushesOnlyFromBelow) continue
                if (comesDownOn(crushParts(), mp.bounds, 4.0)) {
                    isGameOver = true
                    onGameOver?.invoke()
                    onHangingCrateHit?.invoke()
                    return
                }
            }
        }
        prisoner?.update(dt, currentPlatforms, exitZone)
        updateBoardingFromFloorLevel(groundY)
        observeBonusObjective(groundY)
        checkStayOffTheGround()
        captureRunSample()

        // Check Exit / Win condition
        // With a prisoner to get out, the level ends only once he is in the exit as well.
        if (player.bounds.intersects(exitZone) && prisoner?.hasEscaped != false) {
            // The recording ends in the exit, not on the last whole sample short of it.
            runRecorder?.let { rec -> rec.capture(rec.sampleCount * rec.step) { rec.lastSampleNow() } }
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
            val pb = prisoner?.takeIf { it.isFree }?.bounds
            if (pb != null && steamPipes.any { it.intersectsPlayer(pb) }) {
                onSteamPipeHit?.invoke()
                losePrisoner()
                return
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
                    GeometryUtils.hasLineOfSight(player.center, g.center, visionOccluders)
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

        /** How far a cell door has to be up before its prisoner gets up to leave (see Prisoner.free). */
        const val PRISONER_FREED_AT = 0.5

        /**
         * How far above the ground a standing surface still counts as floor level for
         * [isBoardingFromFloorLevel] - covers 48-tall barrels and carts, not a raised platform.
         */
        const val FLOOR_LEVEL_BAND = 60.0
        /** How close counts as touching a hanging crate for [BonusObjective.NEVER_TOUCH_A_HANGING_CRATE]. */
        const val TOUCH_MARGIN = 0.25

        /**
         * The braced push figure's height over its collision box, trailing end first, in six
         * equal columns, as fractions of Player.visualHeight: the tallest point of each column
         * over every frame of the push loop and the settled end of the lean-in (measured from
         * resources/player/push and pushtransition's alpha at the drawn scale). See crushParts.
         */
        val PUSH_CRUSH_PROFILE = doubleArrayOf(0.45, 0.57, 0.65, 0.71, 0.78, 0.78)

        /** Spin per second given to a cut load balanced on a handle post's top (updateLooseCrate). */
        const val PERCH_TIP_SPIN = 12.0

        /** Height of the invisible lid on a load that may not be boarded from floor level. */
        const val BOARDING_LID_HEIGHT = 60.0

        /**
         * Height of the lid on a cut load stood on its end: its top is then well out of a jump even
         * from the high platform beside the cart (296), so there is nothing to land on.
         */
        const val LOOSE_LOAD_LID_HEIGHT = 200.0

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
                    crushesOnContact = def.crushesOnContact,
                    noGroundBoarding = def.noGroundBoarding,
                    squeezes = def.squeezes,
                    crushesOnlyFromBelow = def.crushesOnlyFromBelow
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
                passThroughLegs = layout.passThroughLegs,
                seamlessTables = layout.seamlessTables,
                floatingClimbTargets = layout.floatingClimbTargets,
                unclimbableBoxes = layout.unclimbableBoxes,
                movingPlatforms = movingPlatforms,
                // Fresh instances per world, for the same reason levers/hook crates are copied
                // below: a PushCart holds a mutable x, and layout.pushCarts is a process-wide
                // singleton, so a cart shoved to the platform in one playthrough would already be
                // sitting there in the next.
                pushCarts = layout.pushCarts.map { PushCart(it) },
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
            world.doors = layout.doors.map { Door(it) }
            world.doorSwitches = layout.doorSwitches
            world.lifts = layout.lifts.map { Lift(it) }
            world.prisoner = layout.prisoner?.let { Prisoner(it) }
            world.refreshVisionOccluders()
            if (levelData.playerCrouchForwardSpeedMultiplier != 1.0) {
                world.player.crouchForwardSpeed = world.player.crouchSpeed * levelData.playerCrouchForwardSpeedMultiplier
            }
            return world
        }
    }
}
