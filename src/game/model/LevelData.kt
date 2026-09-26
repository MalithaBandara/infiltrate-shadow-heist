package game.model

import kotlin.math.PI

/** A guard placed on a specific surface of a [LevelLayout]. */
data class GuardSpawn(
    val startX: Double,
    val surfaceY: Double,      // Top of the platform this guard stands on
    val patrolMinX: Double,
    val patrolMaxX: Double,
    val speed: Double = 55.0,
    val facing: Double = 1.0,
    val visionRange: Double = 220.0,
    // Hitbox. The 26x48 default predates the guard sprite and is half the player's size - level 5's
    // guards still use it because its walkthrough test and the guards' patrol geometry are tuned
    // to it. A guard drawn with the real idle art (resources/guard/idle) wants the player's own
    // 96-unit height so the two silhouettes match on screen; see LEVEL_3_LAYOUT.
    val width: Double = 26.0,
    val height: Double = 48.0,
    /** Seconds spent standing at each end of the route before turning back; see Guard.patrolPauseDuration. */
    val patrolPauseDuration: Double = 0.0,
    /** See Guard.holdUntilPlayerCrouches. */
    val holdUntilPlayerCrouches: Boolean = false,
    /** See Guard.visionTilt. */
    val visionTilt: Double = 0.0
)

/** A security camera placed in a [LevelLayout] or [LevelData]. */
data class CameraSpawn(
    val x: Double,
    val y: Double,
    val minAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    val maxAngle: Double = (90.0 + 30.0) * (PI / 180.0),
    val startAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    val sweepSpeed: Double = 0.7,
    val visionRange: Double = 240.0,
    val visionFov: Double = 45.0 * (PI / 180.0),
    val sweepDirection: Double = 1.0,
    /** See Camera.sweepPauseDuration. */
    val sweepPauseDuration: Double = 0.0,
    /** See Camera.detectionPauseDuration. Matches Guard.investigateDuration (2.5s). */
    val detectionPauseDuration: Double = 2.5
)

/**
 * A safe respawn checkpoint in a [LevelLayout].
 * When reached by a grounded player within [triggerZone], this checkpoint is secured.
 * If the player restarts or dies while the Checkpoints powerup is active, they respawn at ([x], [y]).
 */
data class Checkpoint(
    val x: Double,
    val y: Double,
    val triggerZone: Rect? = null,
    val id: String = ""
)

/**
 * A background crane (crane.png), rendered as three pieces - a fixed boom tip cap, a repeating
 * truss segment tiled [tileCount] times, then the fixed cab/tracked-base piece - so its boom can
 * be made genuinely longer than the source art's own proportions ("cut from the middle and copy a
 * part to make it longer") without stretching/distorting the truss pattern. The truss's own real
 * repeat period, measured directly from crane.png by autocorrelating a scanline across its
 * pure-truss region, is 126px (out of the asset's own strict-alpha-bbox crop, 1708x452 - see
 * GameplayScene.kt's dedicated crane-rendering pass for the exact crop rectangles, which are fixed
 * pixel constants tied to the asset itself, not level geometry, so they live there rather than
 * here).
 *
 * [bounds] is the crane's whole visual extent, for positioning/measurement only - it is NOT one
 * solid collision box. **[collisionBoxes] is: three pieces that follow the machine's own drawn
 * silhouette**, on request ("dont just use 1 bounding box for collisions in the crane, use
 * multiple of them so walking on it doesnt feel like flying" - a single box spanning the whole
 * machine put its walkable top at the boom's own height across the entire vehicle, so crossing it
 * read as floating in mid-air above the tracks and the cab roof). Each one's edges come from a
 * per-column alpha scan of crane.png's own crop, not from eyeballed fractions:
 *
 *  - [boomBounds] - the free-hanging lattice boom, only as thick as its own real art (crop rows
 *    7..88) and reaching from the tip to where the machine's tracks actually start (crop column
 *    730). An overhang past that reads as open headroom underneath, not a solid wall.
 *  - [bodyBounds] - the machine's front section (tracks, A-frame mast, boom root: crop columns
 *    730..1390), solid from the boom's own top down to the base. Its top is deliberately flush
 *    with [boomBounds]' top rather than with the tracks' deck, because the boom art runs right
 *    over this whole section: the deck below it has only ~0.5x the player's standing height of
 *    clearance at any crane size this game uses, so the deck can never be stood on, and a box
 *    with its top down there would just let the player jump in and get wedged under the boom.
 *    Walking this stretch is walking the boom, which is exactly what the art shows.
 *  - [houseBounds] - the superstructure/counterweight house past the boom's own end (crop columns
 *    1390..1695, roof at crop row 169), the one piece of the vehicle whose roof is genuinely
 *    clear of the boom and can be stood on. Stepping off the boom onto it and then down to the
 *    ground is the staircase the multi-box request asked for.
 *
 * All three go in [LevelLayout.boxes] - real and climbable/walkable, not just decoration, on
 * request ("make sure all parts of the crane is interactable"). See LEVEL_6_LAYOUT for the level
 * geometry built around this shape.
 */
data class CraneDef(
    val x: Double,
    val y: Double,
    val height: Double,
    val tileCount: Int
) {
    companion object {
        private const val CROP_HEIGHT = 452.0
        private const val TIP_CAP_CROP_WIDTH = 161.0
        private const val TILE_CROP_WIDTH = 126.0
        private const val CAB_CROP_WIDTH = 1023.0
        // Where the fixed cab/tracked-base crop starts inside the asset's own 1708-wide crop.
        private const val CAB_CROP_X = 685.0
        private const val BOOM_CROP_TOP = 7.0
        private const val BOOM_CROP_BOTTOM = 88.0
        // The machine's own silhouette inside that same crop, measured by per-column alpha scan
        // (threshold >10): the tracks reach full height from column 730, the lattice boom's art
        // ends at column 1390, the house roof sits at row 169 and the tracks/house end at 1695.
        private const val BODY_CROP_LEFT = 730.0
        private const val BOOM_CROP_END = 1390.0
        private const val HOUSE_CROP_TOP = 169.0
        private const val BODY_CROP_RIGHT = 1695.0

        /**
         * The [height] a crane needs for [boomBounds]' own walkable top to land exactly at
         * [boomTopY] while its base rests at [baseY].
         *
         * The boom's top is a fixed fraction of the crane's height below its own top edge, so
         * "the boom has to be at this exact height" and "the crane is this tall" are the same
         * number - which makes the crane's size a derived value, not a picked one, wherever a
         * level needs the boom at a particular height (LEVEL_6_LAYOUT: "for the climbing
         * animation to work, this long beam should be his head height").
         */
        fun heightForBoomTop(baseY: Double, boomTopY: Double): Double =
            (baseY - boomTopY) / (1.0 - BOOM_CROP_TOP / CROP_HEIGHT)
    }

    private val scale: Double get() = height / CROP_HEIGHT
    private val cabWidth: Double get() = scale * CAB_CROP_WIDTH

    /**
     * Distance from [x] to where the fixed cab/tracked-base piece starts being drawn - i.e. how
     * far the boom reaches back from the machine itself. A level that needs to place the MACHINE
     * (LEVEL_6_LAYOUT: "the vehicle part of the crane should be just right of the lever") rather
     * than the boom tip positions with `x = wantedMachineX - preview.boomLength`.
     */
    val boomLength: Double get() = scale * (TIP_CAP_CROP_WIDTH + tileCount * TILE_CROP_WIDTH)

    /** World x where the cab/tracked-base crop starts - crop column [CAB_CROP_X]. */
    private val cabX: Double get() = x + boomLength

    private fun cropX(column: Double): Double = cabX + scale * (column - CAB_CROP_X)

    val width: Double get() = boomLength + cabWidth

    val bounds: Rect get() = Rect(x, y, width, height)

    val boomBounds: Rect
        get() = Rect(
            x,
            y + scale * BOOM_CROP_TOP,
            cropX(BODY_CROP_LEFT) - x,
            scale * (BOOM_CROP_BOTTOM - BOOM_CROP_TOP)
        )

    val bodyBounds: Rect
        get() = Rect(
            cropX(BODY_CROP_LEFT),
            y + scale * BOOM_CROP_TOP,
            scale * (BOOM_CROP_END - BODY_CROP_LEFT),
            height - scale * BOOM_CROP_TOP
        )

    val houseBounds: Rect
        get() = Rect(
            cropX(BOOM_CROP_END),
            y + scale * HOUSE_CROP_TOP,
            scale * (BODY_CROP_RIGHT - BOOM_CROP_END),
            height - scale * HOUSE_CROP_TOP
        )

    /** Every solid piece of the machine, left to right - see the class doc. */
    val collisionBoxes: List<Rect> get() = listOf(boomBounds, bodyBounds, houseBounds)
}

/**
 * Explicit geometry for a hand-built, wider-than-screen level. Levels without a layout fall
 * back to the single-screen arena built by [GameWorld.createDefault].
 *
 * Every platform and box also blocks line of sight, so a guard cannot see through a floor -
 * which is what makes the upper storeys usable as a bypass.
 */
data class LevelLayout(
    val worldWidth: Double,
    val playerStartX: Double,
    val playerStartY: Double,
    val exitZone: Rect,
    val platforms: List<Rect>,
    val boxes: List<Rect>,
    val guards: List<GuardSpawn>,
    val cameras: List<CameraSpawn> = emptyList(),
    // Subset of [cameras] (same CameraSpawn instances) rendered with a reduced alpha, so it reads
    // as visually distinct from a normal (fully opaque) camera - e.g. LEVEL_3_LAYOUT's poleCamera.
    // See GameWorld.createFromLayout (matches by spawn identity, not by index) and
    // GameplayScene.kt's camera-rendering loop.
    val translucentCameras: List<CameraSpawn> = emptyList(),
    val fence1: Rect? = null,
    val fence2: Rect? = null,
    // Jump-crate gap crossings: each rect here must also be included in [boxes] (so it collides
    // and can be landed on) - this just tags which boxes get the hanging-crate look (a decorative
    // grey chain down to a normal-colored box) and which of the two crate art variants to use.
    // See GameplayScene.kt's box-rendering loop.
    val hangingCrateVariant1: List<Rect> = emptyList(),
    val hangingCrateVariant2: List<Rect> = emptyList(),
    val barrels: List<Rect> = emptyList(),
    // Boxes drawn with woodcrate2.png instead of the plain crate/rough-block look - tagged the
    // same way barrels are, so a box can be given this distinct art without changing its collision
    // behaviour at all (each must also be in [boxes]). See GameplayScene.kt's box-rendering loop
    // and LEVEL_3_LAYOUT's ground dressing under the camera beam.
    val woodCrates: List<Rect> = emptyList(),
    // Freestanding poles (pole.png) - decorative mounting structure, e.g. for a camera that isn't
    // bolted to a beam/wall. NOT in [boxes] (no collision, "not interactable" on request), but
    // still real drawn geometry so it blocks sight like anything else solid - see
    // GameWorld.createFromLayout's occluders and GameplayScene.kt's dedicated pole-rendering pass.
    val poles: List<Rect> = emptyList(),
    // Background cranes - see [CraneDef]. On request ("make sure all parts of the crane is
    // interactable") every rect in each one's own [CraneDef.collisionBoxes] must ALSO be in
    // [boxes] - solid and climbable/walkable, not just decoration.
    val cranes: List<CraneDef> = emptyList(),
    // A box that would otherwise fall into one of GameplayScene.kt's crate-shaped size heuristics
    // purely by coincidence of its own dimensions, but should render as a plain structural block
    // instead (e.g. LEVEL_6_LAYOUT's own cranePlatform - short enough to trip that crate look on
    // request: "replace the crate with a platform with SAME SIZE", i.e. same Rect, different art).
    // Each entry must also be in [boxes].
    val plainPlatforms: List<Rect> = emptyList(),
    // Tables (table.png): a flat plank on a single off-center leg with a diagonal brace, tagged
    // here the same way barrels are - a solid block like any other climbable box (matches its own
    // bounding box exactly), just with this art instead of the plain crate/rough-block look. See
    // GameplayScene.kt's box-rendering loop.
    val tables: List<Rect> = emptyList(),
    // Collision boxes that belong to a table whose art rect (in [tables]) is NOT itself a box -
    // i.e. a table whose underside is open. Each must also be in [boxes]; this only tells the
    // renderer not to draw them, since the table art already covers them. Same arrangement as
    // level 1's truck (truckParts collide, the one truck image is drawn over the union).
    val tableParts: List<Rect> = emptyList(),
    // Table pieces drawn with the leg's own art crop (e.g. a support post), tagged here purely so
    // GameplayScene.kt's table-drawing pass knows to render them - separately from [boxes], which
    // is what actually decides whether one collides. A piece can be flavor only (not in [boxes]:
    // nothing collides with it, it plays no part in reaching the table) or a genuine obstacle
    // (also in [boxes]: solid, something to actually navigate around) - either way it blocks sight
    // (see GameWorld's occluders), since it's real drawn geometry a guard's cone shouldn't see
    // through, and either way the generic per-box render cascade skips it to avoid double-drawing.
    val tableDecorations: List<Rect> = emptyList(),
    /**
     * Boxes the player may not mantle onto, however climbable the rise would otherwise be. The box
     * still collides, is still landed on and is still jumped onto if it is inside jump height -
     * this denies the climb move only.
     *
     * LEVEL_6_LAYOUT's crane is the case it exists for: the machine's top sits 52.4 above its own
     * rear deck, which is inside the climb window, so the jump press against that face hauled the
     * player back up the machine ("he should be able to climb this but not to the top part from
     * the crane"). It is NOT jumpable either - a jump clears about 48.5 in practice - and the
     * geometry is not to be bent to make it so: lifting the deck's collision to buy the jump was
     * tried and left the player visibly floating above the drawn deck. So the top is simply out of
     * reach from the deck, and the deck itself stays climbable from the platform (91.6).
     */
    val unclimbableBoxes: List<Rect> = emptyList(),
    /**
     * Where the level's own extraction structure is drawn, when it has one instead of the shared
     * entrance.png booth + exitfence.png pair (LEVEL_6_LAYOUT's exitlvl7.png building). Purely
     * decorative - [exitZone] is still the trigger - but it is level geometry rather than scene
     * dressing, because it is placed against the rest of the level (see section 5's plank).
     */
    val exitStructure: Rect? = null,
    // A box the player can mantle onto directly despite Player.findClimbTarget's usual rule
    // against floating ledges (a box whose underside sits well above the climber's feet) - for a
    // ledge that's meant to be mounted with nothing bracing it underneath. Must also be in
    // [boxes]. See Player.findClimbTarget and LEVEL_3_LAYOUT.
    val floatingClimbTargets: List<Rect> = emptyList(),
    val movingPlatforms: List<MovingPlatformDef> = emptyList(),
    /**
     * Flatbed carts the player can take hold of and walk along - see [PushCartDef] and
     * GameWorld's push stance. A cart is solid and climbable wherever it happens to be standing,
     * so it is a step the player positions for himself; it is NOT listed in [boxes], because its
     * footprint moves and GameWorld feeds it in as a dynamic body each tick instead.
     *
     * This is the "real pushable prop" [pushStanceDemo]'s comment anticipates: the stance is
     * gated on being in range of one of these, the way levers and camera bots gate INTERACT,
     * rather than on the button alone.
     */
    val pushCarts: List<PushCartDef> = emptyList(),
    // Purely decorative chain-and-hook dangling from off-screen above (hook.png, a single tall
    // image, not tiled - unlike the hanging crates' chain there's no crate at the bottom needing
    // an exact height, so one asset scaled to each Rect's bounds is enough). No collision box and
    // nothing grabs onto these - for one the player can actually swing from, see [swingHooks].
    // Both lists go through GameplayScene's dedicated hook render pass, which draws them alike.
    val hangingHooks: List<Rect> = emptyList(),
    // Hooks the player can swing across a gap from. Drawn exactly like [hangingHooks] - there is
    // deliberately no badge or highlight marking one as usable, the same way nothing marks a
    // climbable box - but each also becomes a grab point: the grip is the rect's bottom-centre,
    // and Player.findSwingTarget decides from there whether a swing is on. Still no collision
    // box; the player passes through the chain like the decorative ones.
    val swingHooks: List<Rect> = emptyList(),
    val levers: List<Lever> = emptyList(),
    val hookCrates: List<HookCrate> = emptyList(),
    val conveyors: List<ConveyorDef> = emptyList(),
    val conveyorCrates: List<ConveyorCrateDef> = emptyList(),
    val hasStartFences: Boolean = true,
    val restartOnConveyorFallOff: Boolean = false,
    val conveyorsStartOnMove: Boolean = false,
    val canClimb: Boolean = true,
    val lasers: List<LaserDef> = emptyList(),
    val manualCheckpoints: List<Checkpoint> = emptyList(),
    val fans: List<VentFanDef> = emptyList(),
    val cameraBots: List<CameraBotDef> = emptyList(),
    val steamPipes: List<SteamPipeDef> = emptyList(),
    val playerStartCrouched: Boolean = false,
    // Turns INTERACT into a push-stance toggle anywhere in the level, with no object to push
    // and nothing to be in range of. Only PUSH_STANCE_DEMO_LAYOUT sets it - the bare dev stage
    // the push animation is tried out on (it held the level 8 slot until that became a real
    // level), so the trigger is deliberately the plain button rather than proximity to anything.
    // NOTHING SHIPPED may set it, and a test pins that: in a level with real geometry, INTERACT
    // would brace the player where there is nothing to push, and a braced body cannot jump or
    // crouch. The real pushable prop this anticipated is [pushCarts], which does gate on range
    // and leaves this flag alone.
    val pushStanceDemo: Boolean = false
)

enum class TutorialAction {
    MOVE, JUMP_VAULT, CROUCH, CLIMB, REACH_OBJECTIVE, SWING, INTERACT
}

enum class TutorialControlHighlight {
    NONE, MOVE, MOVE_RIGHT, JUMP, CROUCH, INTERACT
}

data class TutorialStep(
    val id: String,
    val triggerMinX: Double,
    val triggerMaxX: Double,
    val title: String,
    val instructionTouch: String,
    val instructionDesktop: String,
    val targetAction: TutorialAction,
    /**
     * Seconds the prompt may stay on screen before it dismisses itself, whether or not
     * [targetAction] was ever performed. 0.0 - the default, and every step that existed before
     * 2026-09-25 - means it waits indefinitely, which is right for a prompt whose action is the
     * only way past (crouch under the beam, swing the gap).
     *
     * It is wrong for a prompt whose action is optional. Level 7's drone can be walked past as
     * well as disabled, so "Approach from behind to disable!" camped on screen for as long as the
     * player chose not to disable it - which is the case this exists for ("stop showing this go
     * from behind after small time. dont wait until he disable robot"). Performing the action
     * still dismisses it earlier; this is only a ceiling.
     */
    val autoDismissSeconds: Double = 0.0,
    /**
     * Extra gate on top of [triggerMinX]..[triggerMaxX]: the step only opens once the player
     * actually has hold of a [PushCartDef].
     *
     * An X window cannot express "now that you are braced against it" - the player is standing in
     * the same place before and after the grab, and the whole point of this prompt is to name the
     * controls that only mean something once he is holding on. Level 8's push/pull pair is the
     * case it exists for: the INTERACT step teaches the grab, and this one waits for it to have
     * happened before pointing at the arrows.
     */
    val requiresPushCartGrip: Boolean = false,
    val highlight: TutorialControlHighlight = TutorialControlHighlight.NONE,
    val handwrittenCallout: String? = null,
    // Only read when highlight == NONE: a world-anchored callout (text position and arrow tip,
    // both in world/level coordinates, scaled by GameplayScene against the camera each frame)
    // pointing at something in the level itself rather than at a screen-fixed control button.
    val worldTextX: Double = 0.0,
    val worldTextY: Double = 0.0,
    val worldAnchorX: Double = 0.0,
    val worldAnchorY: Double = 0.0,
    // The curved arrow's control point normally bows out to the right of the straight
    // text-to-anchor line (see GameplayScene's TutorialControlHighlight.NONE case) - right for
    // step_reach_objective in level 1, whose anchor sits well to the right of its text. When the
    // anchor instead sits close to and slightly left of the text's own end (step_crouch_hide),
    // that same rightward bow sweeps the curve out past the anchor and back through it,
    // visually cutting across whatever it's pointing at. Mirrors the bow to the left instead.
    val arrowBowsLeft: Boolean = false
)

data class LevelData(
    val id: String = "level_1",
    val name: String = "Infiltration",
    val timeTargetSeconds: Float = 15.0f,
    // Long form: the mission-select card and the main menu's dossier/briefing card.
    val description: String = "Infiltrate the perimeter and reach the extraction zone undetected.",
    // Short form: the in-game objective toast and the persistent HUD objective strip.
    val objectiveHint: String = "Reach the extraction zone.",
    val guardSpeed: Double = 60.0,
    val guardPatrolMinX: Double = 300.0,
    val guardPatrolMaxX: Double = 600.0,
    // When false, GameWorld.createDefault() still constructs a Guard (the field is non-nullable
    // and dozens of existing tests read world.guard.* directly), but parks it off-map with zero
    // vision/speed so it's never visible, never blocks movement, and never detects the player -
    // guardPatrolMinX/MaxX above still take effect as plain corridor waypoints (e.g. exitZone's
    // position is still derived from guardPatrolMaxX) even though no guard actually patrols there.
    val guardEnabled: Boolean = true,
    val coinRewardBase: Int = 0,
    val coinRewardPerStar: Int = 0,
    val layout: LevelLayout? = null,
    val cameras: List<CameraSpawn> = emptyList(),
    val backgroundImage: String? = null,
    val tutorialSteps: List<TutorialStep> = emptyList(),
    val hasDarknessVignette: Boolean = false,
    val playerCrouchForwardSpeedMultiplier: Double = 1.0,
    val hasRain: Boolean = false
) {
    val resolvedBackgroundImage: String
        get() {
            if (backgroundImage != null) return backgroundImage
            val levelNum = id.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
            return when ((levelNum - 1) % 5) {
                0 -> "bgmg2.png"
                1 -> "bgmg3.png"
                2 -> "bgmg4.png"
                3 -> "bgmg5.png"
                else -> "bgmg6.png"
            }
        }

    /**
     * Calculates coin reward based on 2-tier progression:
     * Levels 1–6 (Standard): 1★ = 100, 2★ = 200, 3★ = 350 (Lifetime 3★ = 2,100)
     * Levels 7–13 (Hard/Advanced): 1★ = 200, 2★ = 400, 3★ = 700 (Lifetime 3★ = 4,900)
     * Total lifetime earn across 13 levels = 7,000 coins.
     */
    fun getCoinReward(starCount: Int): Int {
        if (starCount <= 0) return 0
        val levelNum = id.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
        val isHard = levelNum in 7..13 || id.contains("hard") || id.contains("dlc")
        return if (isHard) {
            when (starCount) {
                1 -> 200
                2 -> 400
                else -> 700
            }
        } else {
            when (starCount) {
                1 -> 100
                2 -> 200
                else -> 350
            }
        }
    }

    companion object {
        val DEFAULT_LEVEL_1 = LevelData(
            id = "level_1",
            name = "01: Night Arrival",
            timeTargetSeconds = 30.0f,
            description = "Reach the shipyard under cover of darkness and find a way inside.",
            objectiveHint = "Find the Shipyard Entrance",
            guardSpeed = 60.0,
            guardPatrolMinX = 2955.0,
            guardPatrolMaxX = 3305.0,
            guardEnabled = false,
            tutorialSteps = listOf(
                TutorialStep(
                    id = "step_move",
                    triggerMinX = 235.0,
                    triggerMaxX = 450.0,
                    title = "TACTICAL MOVEMENT",
                    instructionTouch = "Use navigation buttons to move left or right.",
                    instructionDesktop = "Use navigation keys [A / D] or [LEFT / RIGHT] to move.",
                    targetAction = TutorialAction.MOVE,
                    highlight = TutorialControlHighlight.MOVE,
                    handwrittenCallout = "Use navigation buttons to move left or right!"
                ),
                TutorialStep(
                    id = "step_jump_vault",
                    triggerMinX = 450.0,
                    triggerMaxX = 850.0,
                    title = "JUMP & VAULT",
                    instructionTouch = "Tap JUMP to hop onto crates and vault over the truck.",
                    instructionDesktop = "Press [W] or [SPACE] to hop and vault onto elevated surfaces.",
                    targetAction = TutorialAction.JUMP_VAULT,
                    highlight = TutorialControlHighlight.JUMP,
                    handwrittenCallout = "Tap to jump over obstacles!"
                ),
                TutorialStep(
                    id = "step_crouch",
                    triggerMinX = 1050.0,
                    triggerMaxX = 1450.0,
                    title = "STEALTH CROUCH",
                    instructionTouch = "Hold CROUCH to duck under low obstacles.",
                    instructionDesktop = "Hold [S], [C] or [CTRL] to duck under low obstacles.",
                    targetAction = TutorialAction.CROUCH,
                    highlight = TutorialControlHighlight.CROUCH,
                    handwrittenCallout = "Hold to crouch!"
                ),
                TutorialStep(
                    id = "step_climb",
                    triggerMinX = 1980.0,
                    triggerMaxX = 2080.0,
                    title = "MANTLE & CLIMB",
                    instructionTouch = "Tap JUMP near a high ledge to mantle and climb.",
                    instructionDesktop = "Press [W] or [SPACE] near a high ledge to mantle and climb.",
                    targetAction = TutorialAction.CLIMB,
                    highlight = TutorialControlHighlight.JUMP,
                    handwrittenCallout = "Tap jump to climb!"
                ),
                TutorialStep(
                    id = "step_reach_objective",
                    triggerMinX = 3000.0,
                    triggerMaxX = 3500.0,
                    title = "REACH THE OBJECTIVE",
                    instructionTouch = "Reach the objective to complete the mission.",
                    instructionDesktop = "Infiltrate the objective building to complete the mission.",
                    targetAction = TutorialAction.REACH_OBJECTIVE,
                    highlight = TutorialControlHighlight.NONE,
                    handwrittenCallout = "Reach the objective!",
                    worldTextX = 3270.0,
                    worldTextY = 220.0,
                    worldAnchorX = 3425.0,
                    worldAnchorY = 320.0
                )
            )
        )

        /**
         * A short vertical-then-horizontal platforming level: hop onto a crate, climb from it
         * onto an elevated terrain block, then cross a gap in that terrain by jumping between
         * three suspended crates before dropping back to the ground and reaching the exit.
         *
         * Surfaces (top edge): ground 440, crate1/rescue crate 392-400, terrain/hanging crates
         * 280. crate1 and the rescue crate use the exact same footprint as the small crate in
         * GameWorld.createDefault() (68x48) - short enough (48 < Player.maxJumpHeight 51.2) that
         * a normal jump clears them; nothing climbs here. The step up from either of them onto the
         * terrain block, though (112/120 units), sits inside Player's climbable window
         * (51.2..147.2 - maxJumpHeight..maxJumpHeight+height, from jumpSpeed=-320/gravity=1000),
         * so the engine forces the climb animation there instead of letting a jump clear it.
         *
         * The three hanging crates sit at the same 280 height as the terrain, 70 units apart edge
         * to edge - comfortably inside the ~84 unit horizontal range a full jump arc covers at
         * full run speed (moveSpeed 132 over the ~0.64s flight time), matching the "no gap
         * exceeds the ~72 units covered during a full jump arc" budget SIDE_SCROLL_LEVEL_LAYOUT
         * already established. Their collision boxes are NOT some arbitrary thin platform sitting
         * under the art - each box's height is exactly the crate's own real height as it appears
         * in that art (see GameplayScene.kt's box-rendering loop, which crops the crate out of the
         * source image and scales it by box.width alone, so box.height comes out equal to the
         * crop's real height rather than being forced to it), so the ground the player actually
         * lands on lines up with where the crate visually is. hangingCrate1 reuses chainedcrate.png
         * at the same width as level 1's ceiling-hung crate (174, the "same sizing as level 1"
         * this was explicitly asked for) - the other two use chainedcrate2.png at a visibly
         * smaller width (120), i.e. the same asset/approach, deliberately smaller. The chain above
         * each crate is not that same image stretched to whatever the drop happens to be (tried
         * first - either distorts the chain or, scaled by width alone, leaves such a long thin
         * stretch of it that the whole thing reads as floating); it's real tiled copies of a short
         * chain segment cropped from the same source art, repeated up to the ceiling - the same
         * fix as a tiled floor texture, and just as immune to the crate/gap size changing later.
         *
         * A player who falls short lands on the ground below (it runs the full width of the
         * level, so a drop is never fatal) and can climb back up via the rescue crate parked at
         * the gap's near/left edge, flush against the terrain block's own right face and clear of
         * hangingCrate1's own much taller footprint (15 units of horizontal clearance to its left
         * edge - the two boxes never overlap in x, so the crate's height above it is irrelevant):
         * ground -> rescue crate is a plain jump (40 tall), rescue crate -> terrain a 120 unit
         * climb.
         *
         * Walkthrough (see GameplayModelTest.testLevel2HangingCratesGapIsBeatable):
         *   1. Walk from the start fences to crate1 (x 400-468), jump onto it.
         *   2. Climb from crate1's top straight onto the terrain block (x 468-868).
         *   3. Walk to the terrain's right edge and jump the three hanging crates
         *      (938-1112, 1182-1302, 1372-1492) onto the far terrain block (1562-1962).
         *   4. Walk off the far terrain's end, drop to the ground, and continue to the exit.
         */
        val LEVEL_2_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 5100.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            // --- SECTION 1: Stationary Vault & Crossing ---
            // 1. First step: ground -> crate1, a plain jump
            val crate1 = Rect(x = 400.0, y = 392.0, width = 68.0, height = 48.0)

            // 2. The climb: crate1's top -> the elevated terrain block. Terrain height (144) is
            // 3 times crate height (48), putting the top edge at y=296.0 - player's head level when on crate1.
            val terrain = Rect(x = 468.0, y = 296.0, width = 400.0, height = 144.0)

            // 3. Rescue barrel 1: sits on the ground flush against terrain block's right face (x=868).
            val rescueBarrel1 = Rect(x = 868.0, y = 392.0, width = 32.0, height = 48.0)

            // 4. Section 1 gap crossing: three stationary hanging crates landing at y=296.0.
            val hangingCrate1 = Rect(x = 946.0, y = 296.0, width = 174.0, height = 38.0)
            val hangingCrate2 = Rect(x = 1190.0, y = 296.0, width = 76.0, height = 38.0)
            val hangingCrate3 = Rect(x = 1336.0, y = 296.0, width = 76.0, height = 38.0)

            // 5. Intermediate terrain platform connecting Section 1 and Section 2 (x: 1482..1862).
            val midTerrain = Rect(x = 1482.0, y = 296.0, width = 380.0, height = 144.0)

            // --- SECTION 2: Dynamic Moving Container Crossing ---
            // 6. Rescue barrel 2: sits on the ground flush against midTerrain's right face at x=1862.
            // Players who fall during the moving container section can climb back up here.
            val rescueBarrel2 = Rect(x = 1862.0, y = 392.0, width = 32.0, height = 48.0)

            // 7. 5 Containers: 2 short moving, 1 long stationary, 2 short moving.
            // Container 1 (Short, moving): oscillates between 1895 and 1975 (close to midTerrain at 1862).
            val movingCrate1 = MovingPlatformDef(
                id = "lvl2_move_1",
                initialX = 1895.0,
                y = 296.0,
                width = 76.0,
                height = 38.0,
                minX = 1895.0,
                maxX = 1975.0,
                periodSeconds = 3.6,
                phaseOffsetSeconds = 0.0,
                isVariant1 = false
            )
            // Container 2 (Short, moving): oscillates between 2075 and 2165, synchronized with Crate 1 so jump is available every cycle.
            val movingCrate2 = MovingPlatformDef(
                id = "lvl2_move_2",
                initialX = 2165.0,
                y = 296.0,
                width = 76.0,
                height = 38.0,
                minX = 2075.0,
                maxX = 2165.0,
                periodSeconds = 3.6,
                phaseOffsetSeconds = 1.8,
                isVariant1 = false
            )
            // Container 3 (Long, stationary): center safe haven / island at x=2270, width=174 (ends at 2444).
            val stationaryLongCrate = Rect(x = 2270.0, y = 296.0, width = 174.0, height = 38.0)

            // Container 4 (Short, moving): oscillates between 2475 and 2555 (safely clear of stationary crate and crate 5).
            val movingCrate4 = MovingPlatformDef(
                id = "lvl2_move_4",
                initialX = 2475.0,
                y = 296.0,
                width = 76.0,
                height = 38.0,
                minX = 2475.0,
                maxX = 2555.0,
                periodSeconds = 3.6,
                phaseOffsetSeconds = 0.0,
                isVariant1 = false
            )
            // Container 5 (Short, moving): oscillates between 2665 and 2745, synchronized with Crate 4.
            val movingCrate5 = MovingPlatformDef(
                id = "lvl2_move_5",
                initialX = 2745.0,
                y = 296.0,
                width = 76.0,
                height = 38.0,
                minX = 2665.0,
                maxX = 2745.0,
                periodSeconds = 3.6,
                phaseOffsetSeconds = 1.8,
                isVariant1 = false
            )

            // 8. Intermediate landing terrain block between Section 2 and Section 3 (x: 2855..3295).
            val midTerrain2 = Rect(x = 2855.0, y = 296.0, width = 440.0, height = 144.0)

            // --- SECTION 3: Dynamic Vertical Elevator Container Gauntlet ---
            // 9. Rescue barrel 3: sits on the ground flush against midTerrain2's right face at x=3295.
            // Players who miss a jump during the vertical elevator section can climb back up here.
            val rescueBarrel3 = Rect(x = 3295.0, y = 392.0, width = 32.0, height = 48.0)

            // 10. 5 Containers: 2 short moving vertically (seesaw pair), 1 long stationary island, 2 short moving vertically.
            // Container 1 (Short, moving Y: 250..320): oscillates up and down to catch the player from midTerrain2.
            val verticalCrate1 = MovingPlatformDef(
                id = "lvl2_vert_1",
                initialX = 3365.0,
                y = 320.0,
                width = 76.0,
                height = 38.0,
                minX = 3365.0,
                maxX = 3365.0,
                minY = 250.0,
                maxY = 320.0,
                periodSeconds = 3.6,
                phaseOffsetSeconds = 0.0,
                isVariant1 = false,
                initialY = 320.0
            )
            // Container 2 (Short, moving Y: 240..310): oscillates in counter-phase (seesaw timing) with Container 1.
            val verticalCrate2 = MovingPlatformDef(
                id = "lvl2_vert_2",
                initialX = 3511.0,
                y = 240.0,
                width = 76.0,
                height = 38.0,
                minX = 3511.0,
                maxX = 3511.0,
                minY = 240.0,
                maxY = 310.0,
                periodSeconds = 3.6,
                phaseOffsetSeconds = 1.8,
                isVariant1 = false,
                initialY = 240.0
            )
            // Container 3 (Long, stationary): center safe haven / island at x=3657, width=174 (ends at 3831).
            val stationaryLongCrate2 = Rect(x = 3657.0, y = 280.0, width = 174.0, height = 38.0)

            // Container 4 (Short, moving Y: 240..320): lifts player from the central island.
            val verticalCrate4 = MovingPlatformDef(
                id = "lvl2_vert_4",
                initialX = 3901.0,
                y = 280.0,
                width = 76.0,
                height = 38.0,
                minX = 3901.0,
                maxX = 3901.0,
                minY = 240.0,
                maxY = 320.0,
                periodSeconds = 3.8,
                phaseOffsetSeconds = 0.6,
                isVariant1 = false,
                initialY = 280.0
            )
            // Container 5 (Short, moving Y: 220..285): counter-phase elevator leading to final extraction platform.
            val verticalCrate5 = MovingPlatformDef(
                id = "lvl2_vert_5",
                initialX = 4055.0,
                y = 285.0,
                width = 76.0,
                height = 38.0,
                minX = 4055.0,
                maxX = 4055.0,
                minY = 220.0,
                maxY = 285.0,
                periodSeconds = 3.8,
                phaseOffsetSeconds = 2.5,
                isVariant1 = false,
                initialY = 285.0
            )

            // 11. Final landing terrain block past Section 3 (x: 4193..4373).
            val finalTerrain = Rect(x = 4193.0, y = 296.0, width = 180.0, height = 144.0)

            val boxes = listOf(
                crate1, terrain, rescueBarrel1,
                hangingCrate1, hangingCrate2, hangingCrate3,
                midTerrain, rescueBarrel2,
                stationaryLongCrate, midTerrain2,
                rescueBarrel3, stationaryLongCrate2, finalTerrain
            )
            val movingPlatforms = listOf(
                movingCrate1, movingCrate2, movingCrate4, movingCrate5,
                verticalCrate1, verticalCrate2, verticalCrate4, verticalCrate5
            )

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = 4680.0, y = 340.0, width = 44.0, height = 100.0),
                platforms = listOf(ground),
                boxes = boxes,
                guards = emptyList(),
                hangingCrateVariant1 = listOf(hangingCrate1, stationaryLongCrate, stationaryLongCrate2),
                hangingCrateVariant2 = listOf(hangingCrate2, hangingCrate3),
                barrels = listOf(rescueBarrel1, rescueBarrel2, rescueBarrel3),
                movingPlatforms = movingPlatforms
            )
        }

        val DEFAULT_LEVEL_2 = LevelData(
            id = "level_2",
            name = "02: Cargo Yard",
            timeTargetSeconds = 70.0f,
            description = "Cross the empty container yard and reach the restricted section.",
            objectiveHint = "Find a Way Through the Yard",
            layout = LEVEL_2_LAYOUT,
            backgroundImage = "bgmg5.png",
            // Re-enabled 2026-09-25 after the rain rework (behind the world, thinner, more
            // transparent, with impact crowns). It had been switched off for the Google Play
            // production review - see .junie/guidelines.md's temporary-gating list.
            hasRain = true
        )

        /**
         * Level 4: Conveyor Belt Run.
         * The conveyor belt spans from the left corner (x = 0.0) across the yard to x = 1620.0.
         * Conveyor belt is initially stationary and starts moving when the player starts moving.
         * Obstacles include:
         * - Non-climbable: all mantling and climbing is disabled for this level; player traverses by hopping.
         * - Only stacks of 1 (height 48) and stacks of 2 (height 96). All 3-stacks removed.
         * - Every 2-stack is flanked by a 1-stack on both its left and right so the player hops onto it.
         * - Authentic small and long hanging crates from Level 2 positioned above the 2-stacked crates.
         * - Player can move forward while crouching on the 2-stacked crate without hitting the hanging crates.
         * - Conveyor moving backwards (-45.0) against the player.
         * - Instant restart without loading screen if carried off the belt or falling off.
         */
        val LEVEL_4_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 8600.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            val conveyorHeight = 26.0
            val conveyorWidth = 7760.0
            val conveyorRect = Rect(x = 0.0, y = groundY - conveyorHeight, width = conveyorWidth, height = conveyorHeight)
            val conveyor = ConveyorDef(bounds = conveyorRect, speed = -45.0)

            val crateWidth = 68.0
            val crateHeight = 48.0
            val crateHeight2 = 96.0 // 2-stack crate height
            val conveyorTopY = conveyorRect.top // 414.0

            // =========================================================================
            // APPROACH 1: KINETIC RHYTHM GAUNTLET (Zero-Clipping Physics)
            // =========================================================================
            // - Lowered hanging monorail containers at floor-crouch height (y = 302.0, height = 38.0, bottom at 340.0).
            // - Clearance above conveyor (414.0 - 340.0 = 74.0px):
            //   - Crouching player (height 56.0, head at 358.0) clears with 18px headroom and passes cleanly.
            //   - Standing player (height 96.0, head at 318.0) hits container and triggers Mission Failed.
            // - All floor crates are single 1-stacks (height = 48.0, top at y = 366.0) and stepped 2-stacks in open zones.
            // - ZERO CLIPPING GUARANTEE:
            //   - Physical gap between floor crate top (366.0) and hanging crate bottom (340.0) is 26.0px!
            //   - 1-stack crates glide smoothly under hanging cargo without any visual collision.
            //   - Standing or crouching atop a 1-stack crate under hanging cargo hits (head at 270/310 < 340),
            //     so the player cannot bypass ducking by riding crates—they must drop to the belt and slide!
            // =========================================================================

            val crateLoopMin = 0.0
            val crateLoopMax = 8200.0

            val hangingCrateSmall1 = ConveyorCrateDef(
                initialX = 1100.0,
                y = 302.0,
                width = 76.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = false,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax,
                speedMultiplier = 1.0
            )
            // Hanging crate 1 (over 3-stack 2-stack platform at x = 2100..2440):
            // Descends from minY = 145.0 down to maxY = 212.0 (bottom = 250.0).
            // Above 2-stack crates (top at 318.0), standing player (head 222.0) gets crushed,
            // while crouching player (head 262.0) has 12px headroom and clears safely!
            val hangingCrateLong1 = ConveyorCrateDef(
                initialX = 2200.0,
                y = 212.0,
                width = 174.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = true,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax,
                speedMultiplier = 1.0,
                minY = 145.0,
                maxY = 212.0,
                verticalPeriodSeconds = 3.5,
                verticalPhaseOffsetSeconds = 0.0
            )
            val hangingCrateSmall2 = ConveyorCrateDef(
                initialX = 3800.0,
                y = 302.0,
                width = 76.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = false,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax,
                speedMultiplier = 1.0,
                minY = 220.0,
                maxY = 302.0,
                verticalPeriodSeconds = 4.0,
                verticalPhaseOffsetSeconds = 2.0
            )
            // Hanging crate 2 (over 4-stack 2-stack platform at x = 5450..5858):
            // Descends from minY = 145.0 down to maxY = 212.0 (bottom = 250.0).
            val hangingCrateLong2 = ConveyorCrateDef(
                initialX = 5540.0,
                y = 212.0,
                width = 174.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = true,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax,
                speedMultiplier = 1.0,
                minY = 145.0,
                maxY = 212.0,
                verticalPeriodSeconds = 3.8,
                verticalPhaseOffsetSeconds = 1.0
            )
            val hangingCrateSmall3 = ConveyorCrateDef(
                initialX = 6350.0,
                y = 302.0,
                width = 76.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = false,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax,
                speedMultiplier = 1.0,
                minY = 220.0,
                maxY = 302.0,
                verticalPeriodSeconds = 3.6,
                verticalPhaseOffsetSeconds = 1.0
            )
            val hangingCrateSmall4 = ConveyorCrateDef(
                initialX = 6850.0,
                y = 302.0,
                width = 76.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = false,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax,
                speedMultiplier = 1.0
            )
            val hangingCrateLong3 = ConveyorCrateDef(
                initialX = 7460.0,
                y = 302.0,
                width = 174.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = true,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax,
                speedMultiplier = 1.0
            )
            val hangingCrates = listOf(
                hangingCrateSmall1, hangingCrateLong1, hangingCrateSmall2,
                hangingCrateLong2, hangingCrateSmall3, hangingCrateSmall4, hangingCrateLong3
            )

            // Dynamic floor crates (mix of single 1-stacks, stepped 2-stack pyramids, and multi-crate climbing platforms):
            // All floor crates have shouldLoop = true with unified loop bounds so they cycle non-stop with zero drift.
            fun singleCrate(x: Double) = ConveyorCrateDef(
                initialX = x,
                y = conveyorTopY - crateHeight,
                width = crateWidth,
                height = crateHeight,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax
            )
            fun doubleCrate(x: Double) = ConveyorCrateDef(
                initialX = x,
                y = conveyorTopY - crateHeight2,
                width = crateWidth,
                height = crateHeight2,
                shouldLoop = true,
                loopMinX = crateLoopMin,
                loopMaxX = crateLoopMax
            )
            fun pyramid(x: Double) = listOf(
                singleCrate(x),
                doubleCrate(x + crateWidth),
                singleCrate(x + crateWidth * 2.0)
            )
            fun multiStack3(x: Double) = listOf(
                singleCrate(x),
                doubleCrate(x + crateWidth),
                doubleCrate(x + crateWidth * 2.0),
                doubleCrate(x + crateWidth * 3.0),
                singleCrate(x + crateWidth * 4.0)
            )
            fun multiStack4(x: Double) = listOf(
                singleCrate(x),
                doubleCrate(x + crateWidth),
                doubleCrate(x + crateWidth * 2.0),
                doubleCrate(x + crateWidth * 3.0),
                doubleCrate(x + crateWidth * 4.0),
                singleCrate(x + crateWidth * 5.0)
            )

            val movingConveyorCrates = listOf(
                // Sector 1 (x = 0..1500, remaining 150m..120m): Introductory vaulting rhythm
                listOf(
                    singleCrate(350.0),
                    singleCrate(550.0),
                    singleCrate(750.0),
                    singleCrate(950.0),
                    singleCrate(1350.0)
                ),

                // Sector 2 (x = 1500..3000, remaining 120m..90m): First stepped pyramid + 3-crate multi-stack with overhead crush
                pyramid(1600.0),
                multiStack3(2100.0),
                listOf(
                    singleCrate(2550.0),
                    singleCrate(2800.0)
                ),

                // Sector 3 (x = 3000..4500, remaining 90m..60m): Stepped pyramid + alternating obstacles
                pyramid(3100.0),
                listOf(
                    singleCrate(3650.0),
                    singleCrate(4050.0),
                    singleCrate(4300.0)
                ),

                // Sector 4 (x = 4500..6000, remaining 60m..30m): Double scissor lasers + 4-crate multi-stack with overhead crush
                pyramid(4600.0),
                listOf(
                    singleCrate(4850.0),
                    singleCrate(5250.0)
                ),
                multiStack4(5450.0),

                // Sector 5 (x = 6000..7760, remaining 30m..0m): Grand finale gauntlet
                pyramid(6100.0),
                listOf(
                    singleCrate(6580.0),
                    singleCrate(6700.0),
                    singleCrate(7000.0)
                )
            ).flatten()

            // Dynamic laser hazards across 150m (originating from ceiling topY = 150.0, tilt <= 45°):
            val lasers = listOf(
                // Laser 1: Sector 1 introductory scanner (x = 670.0)
                LaserDef(
                    id = "lvl4_laser_sec1",
                    topX = 670.0,
                    topY = 150.0,
                    bottomX = 670.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.3,
                    inactiveDuration = 4.7,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 2: Vertical security scanner in Sector 2 (x = 1950.0).
                LaserDef(
                    id = "lvl4_laser_vert_1",
                    topX = 1950.0,
                    topY = 150.0,
                    bottomX = 1950.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 5.0,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 3: Sector 2/3 tilted scanner (x = 2700..2760, tilt = +12.8° <= 45°)
                LaserDef(
                    id = "lvl4_laser_sec2",
                    topX = 2700.0,
                    topY = 150.0,
                    bottomX = 2760.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.6,
                    phaseOffsetSeconds = 1.0
                ),
                // Laser 4: Forward-tilted scanner in Sector 3 (tilt = +20.7° <= 45°).
                LaserDef(
                    id = "lvl4_laser_tilt_1",
                    topX = 3350.0,
                    topY = 150.0,
                    bottomX = 3450.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.8,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 5: Sector 4 tilted interceptor (x = 4450..4390, tilt = -12.8° <= 45°)
                LaserDef(
                    id = "lvl4_laser_sec4",
                    topX = 4450.0,
                    topY = 150.0,
                    bottomX = 4390.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.6,
                    phaseOffsetSeconds = 2.0
                ),
                // Laser 6A & 6B: Crossed scissor trap in Sector 4 (x = 5000 <-> 5140, tilt = ±27.9° <= 45°).
                LaserDef(
                    id = "lvl4_laser_cross_1a",
                    topX = 5000.0,
                    topY = 150.0,
                    bottomX = 5140.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.5,
                    inactiveDuration = 4.5,
                    phaseOffsetSeconds = 0.0
                ),
                LaserDef(
                    id = "lvl4_laser_cross_1b",
                    topX = 5140.0,
                    topY = 150.0,
                    bottomX = 5000.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.5,
                    inactiveDuration = 4.5,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 7: Backward-tilted security gate in Sector 5 (topX = 6550.0, bottomX = 6450.0, tilt = -20.7° <= 45°).
                LaserDef(
                    id = "lvl4_laser_tilt_2",
                    topX = 6550.0,
                    topY = 150.0,
                    bottomX = 6450.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.4,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 8: Forward-tilted interceptor laser in Sector 5 (tilt = +20.7° <= 45°).
                LaserDef(
                    id = "lvl4_laser_tilt_3",
                    topX = 6650.0,
                    topY = 150.0,
                    bottomX = 6750.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.4,
                    phaseOffsetSeconds = 2.2
                ),
                // Laser 9, 10, 11: Final 3 extraction gauntlet airlock lasers (x = 7120, 7240, 7360)
                // Coordinated 6s cycle (active 4s, inactive 2s) with sequential staging pockets
                LaserDef(
                    id = "lvl4_laser_gauntlet_1",
                    topX = 7120.0,
                    topY = 150.0,
                    bottomX = 7120.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 4.0,
                    inactiveDuration = 2.0,
                    phaseOffsetSeconds = 4.0
                ),
                LaserDef(
                    id = "lvl4_laser_gauntlet_2",
                    topX = 7240.0,
                    topY = 150.0,
                    bottomX = 7240.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 4.0,
                    inactiveDuration = 2.0,
                    phaseOffsetSeconds = 2.0
                ),
                LaserDef(
                    id = "lvl4_laser_gauntlet_3",
                    topX = 7360.0,
                    topY = 150.0,
                    bottomX = 7360.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 4.0,
                    inactiveDuration = 2.0,
                    phaseOffsetSeconds = 0.0
                )
            )

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 100.0,
                playerStartY = conveyorRect.top - 96.0,
                exitZone = Rect(x = 7680.0, y = groundY - 120.0, width = 80.0, height = 120.0),
                platforms = listOf(ground, conveyorRect),
                boxes = listOf(conveyorRect),
                guards = emptyList(),
                hangingCrateVariant1 = emptyList(),
                hangingCrateVariant2 = emptyList(),
                hasStartFences = false,
                fence1 = null,
                fence2 = null,
                conveyors = listOf(conveyor),
                conveyorCrates = movingConveyorCrates + hangingCrates,
                restartOnConveyorFallOff = true,
                conveyorsStartOnMove = true,
                canClimb = false,
                lasers = lasers
            )
        }

        /**
         * Work in progress - first section built. Old level_3 ("Blind Spot", the barrel-wall +
         * hook-swing layout) moved to level_4 to make room for this new one, following the same
         * "first section only" pattern LEVEL_4_LAYOUT itself started from.
         *
         * The one obstacle so far: a table (table.png - real art, cropped from the owner's
         * Downloads/charAnimations/assets/beam.png and re-composited: the source's flat tabletop
         * midsection, which is otherwise a fixed length, is repeated 8x so the plank reads as a
         * long cantilevered shelf rather than a short desk) blocking the ground path outright, one
         * climb up from the crate directly onto the table - nothing bridges the two, visibly or
         * invisibly. Ordinarily Player.findClimbTarget refuses to climb onto a floating ledge
         * (the table's underside sits nowhere near the crate's top), but this table is tagged in
         * [LevelLayout.floatingClimbTargets], which is exactly that one exception: a real climb
         * (the mantle animation, not a jump - the rise is 96, inside climbMinHeight..
         * climbMaxHeight's 51.2..115.0) straight from the crate onto it, with no face bracing the
         * gap. Every earlier shape tried there (the plank's own texture stretched into a tall
         * block, a plain solid block, an invisible collision box) got rejected on sight - the
         * owner's ask was for nothing to exist there at all, not for it to be drawn better. Since
         * the crate itself still blocks the ground path outright (68 wide, solid to the ground),
         * the climb is still the only way past - it's a floating ledge to the physics, not to the
         * level design. Past the table, the player crosses it and drops back to the ground (a fall
         * is never fatal) to reach the exit.
         *
         * The leg at the far end (table.png's own leg/brace crop, see [LevelLayout.tableDecorations])
         * is a real, solid obstacle now, not just flavor - it collides (also in [boxes]) and blocks
         * sight, so the ground gauntlet underneath the plank has something to actually navigate
         * around, not just walk through a lookalike. Clear of the guard's far post so his own
         * bounded patrol never bumps into it (see guardFarPost below - same reasoning as
         * guardNearPost's clearance from the crate: Guard.updatePatrol treats it as a physical
         * obstacle, checked before patrolMinX/MaxX, so touching it would cut his dwell short).
         *
         * The guard. One, pacing the underside: he starts at the near post (facing LEFT, toward
         * the approach and the crate - the first thing visible on arrival, though not right on top
         * of the climb itself; see guardNearPost below for why there's real distance between them)
         * for [Guard.patrolPauseDuration], walks to the far post, stands there facing right, walks
         * back, and repeats - the owner's spec, "stay idle -> walk -> stay idle -> come back".
         * Nothing occludes the climb any more (see above) - the owner's explicit call:
         * climbing right in front of him while he's dwelling at the near post, or while he's
         * walking back toward it, gets you seen, same as standing in the open in front of any
         * other guard in the game. The safe read is timing: climb once he's turned to walk toward
         * the far post or is dwelling out there facing away, and the crate-to-roof climb, the
         * crossing, and the drop off the far end are all done during that window. The drop itself
         * has its own tell regardless: from the far post facing right his cone (220) covers the
         * landing zone past the slab's end, so watch for whether he's out there before dropping.
         * He is 30 wide by 96 tall, the player's own height, so the two silhouettes read at one
         * scale.
         *
         * table.png's crop is the STRICT alpha bbox (threshold >10), not PIL's own getbbox() - an
         * earlier pass used getbbox() directly and it turned out to include ~55px of nearly (but
         * not fully) transparent fringe below the leg's real foot, invisible in the source but a
         * visible sliver of "floating" once that fringe got stretched across the full box height
         * in-game. Re-derive with the strict threshold (and re-mirror) if this asset is ever
         * rebuilt from beam.png again.
         */
        val LEVEL_3_LAYOUT = run {
            val groundY = 440.0

            val crateWidth = 68.0
            val crateHeight = 48.0
            val crateX = 420.0 // short run-up from the start fence, matching this file's own "400" convention
            val crate = Rect(x = crateX, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // table.png (2048x512) bakes the leg+brace assembly into its own rightmost ~8.7% and
            // the flat repeating slab (its own midsection, otherwise a fixed length, repeated 8x)
            // into the rest - see GameplayScene.kt's table-drawing pass for the crop. Climb rise
            // from the crate (392) to the table top (296) is 96, inside the 51.2..115.0 window -
            // see [LevelLayout.floatingClimbTargets] on why this climbs at all despite the gap.
            val tablePlankDepth = 30.0
            val tablePlankWidth = 420.0 // much longer cantilevered slab
            val tablePlank = Rect(x = crate.right, y = groundY - 144.0, width = tablePlankWidth, height = tablePlankDepth)

            // The leg at the far end - a real, solid support now, not just flavor: it collides
            // (see [boxes] below) as well as being drawn with the leg/brace crop (see
            // [tableDecorations]), so it's a genuine obstacle on the ground path under the plank,
            // not just something that looks like one. Sits flush under the plank's own right edge.
            val legWidth = 30.0
            val legLift = 16.0 // tucks the joint/brace up against the plank's own underside, closing the gap that read as it "hanging" below
            val rightLeg = Rect(
                x = tablePlank.right - legWidth,
                y = tablePlank.bottom - legLift,
                width = legWidth,
                height = groundY - (tablePlank.bottom - legLift)
            )

            val guardWidth = 30.0
            val guardHeight = 96.0
            // 135 clear of the crate, not just a few units: checked empirically (sweeping
            // clearance against a crouched player across the tutorial's own trigger zone - see the
            // crouch-hide tutorial step below) against VisionSystem.getPlayerSpottedDistance
            // directly. His sight starts at the torch lens (Guard.eyePosition), only ~54 units off
            // the ground and 28 ahead of him, so the line from it to a crouched head behind the
            // crate is nearly level: it clears the crate's 48-tall profile whenever the head is far
            // enough away, and the only thing that hides the player is that "far enough" being
            // past his 220 range. At 100 there was a ~10-unit band (x 355..366) inside the tutorial
            // zone where the line skimmed the crate's top edge by a fraction of a unit and he saw
            // over it; 135 pushes the geometry so that band is beyond his range everywhere. (An
            // earlier 6-unit post, chosen only to clear Guard.updatePatrol's own obstacle check,
            // never blocked anything; 100 was tuned for the old head-height eye.) Pulled in from
            // 135 to 120 to read as closer/more "in your face" on arrival - re-verified at 120
            // against VisionSystem the same way, still clean across the whole tutorial window (see
            // testLevel3CrouchingBehindTheCrateActuallyBreaksLineOfSight); safe regardless, now
            // that holdUntilPlayerCrouches (below) guarantees he's actually standing at this exact
            // post, not somewhere else along his route, for the player's first attempt.
            val guardNearPost = crate.right + 120.0
            // Under the slab, 90 short of its end, 30 wide. The torch he holds out sits 28 ahead of
            // his centre, and the decorative leg at the plank's end blocks everything past itself,
            // so the ground he can light from this post is the strip between his lens and the leg:
            // ~47 units here. At the old 78 the lens was within 5 units of the leg and the "drop
            // is seen from the far post" beat had nowhere to happen.
            val guardFarPost = tablePlank.right - 120.0
            val roofGuard = GuardSpawn(
                startX = guardNearPost, surfaceY = groundY,
                patrolMinX = guardNearPost, patrolMaxX = guardFarPost,
                speed = 55.0, facing = -1.0, visionRange = 220.0,
                width = guardWidth, height = guardHeight,
                patrolPauseDuration = 3.0,
                // Rooted at the near post until the player's first crouch (see
                // Guard.holdUntilPlayerCrouches) - the crouch-hide tutorial's whole premise is
                // hiding from him right here, so he needs to actually be standing at this post,
                // not off walking his route on whatever timing the player happens to arrive at.
                holdUntilPlayerCrouches = true
            )

            // On the roof itself, not the ground past it - a crate to duck behind while crossing
            // the plank, flush into the corner where the plank meets the leg.
            val hideCrate = Rect(
                x = tablePlank.right - crateWidth,
                y = tablePlank.top - crateHeight,
                width = crateWidth,
                height = crateHeight
            )

            // Two long crates (level 2's stationaryLongCrate shape and look - 174x38, the
            // hanging-chain-crate art, see hangingCrateVariant1 below and GameplayScene.kt's box
            // loop), each with a guard standing on it looking down at the ground path underneath.
            // Their torches carry a real downward tilt now (see overwatchVisionTilt below), not a
            // dead-level cone - a horizontal-only cone left a blind wedge directly below and beyond
            // it a fixed, narrow band, which read as barely watching the floor at all despite that
            // being the whole point of perching him up here. Tilted, the cone actually opens onto
            // the open ground between the two crates (checked directly against VisionSystem: the
            // far half of each gap is now his, not just a sliver past the far edge) while directly
            // underneath stays hidden regardless - the crate's own floor blocks that line of sight
            // no matter the angle, same as any other overhang.
            val longCrateWidth = 174.0
            val longCrateHeight = 38.0
            // 30 units higher than the original 330 (head-height-eye-era math, since superseded by
            // the torch lens model) - reads as more clearly perched/elevated. Raising him like this
            // stretches every line to the ground thinner and further, which on its own would shrink
            // his reach - overwatchVisionTilt buys that back deliberately, not by accident.
            val longCrateElevation = 300.0
            // 25 degrees off dead level, not 15 - a noticeably steeper look-down (re-verified
            // directly against VisionSystem at this angle: most of the gap between two crates is
            // now his, not just the far half - only a narrow band right next to his own crate stays
            // outside the cone, plus directly underneath, which stays hidden at any angle since the
            // crate's own floor blocks that line of sight regardless). Still short of reaching all
            // the way back into the table section from here (see
            // testLevel3OverwatchGuardsDoNotSeeBackIntoTheTableSection).
            val overwatchVisionTilt = 25.0 * PI / 180.0
            // 100 now, not 180 - pulled the whole ground-gauntlet pair further left again, closer
            // to the table section, instead of leaving a long dead walk between the two beats.
            val longCrate1 = Rect(x = hideCrate.right + 100.0, y = longCrateElevation, width = longCrateWidth, height = longCrateHeight)
            val longCrate2 = Rect(x = longCrate1.right + 150.0, y = longCrateElevation, width = longCrateWidth, height = longCrateHeight)
            val overwatchGuardWidth = 30.0
            val overwatchGuardMargin = 10.0 // keeps him visibly on the crate, never overhanging its edge
            // Slower (50 -> 35) and now dwelling at each end (patrolPauseDuration, like roofGuard's
            // own beat) instead of pacing the crate's length back and forth without ever stopping -
            // "moving fast" the whole time left nothing to actually time a crossing against.
            val overwatchSpeed = 35.0
            val overwatchPauseDuration = 3.0
            // Guard1 starts dwelling at his crate's end closest to the shared gap - watching the
            // middle from the first frame.
            //
            // Guard2 does NOT start at the mirror-image corner of his own crate (tried first, and
            // for a while believed to give a genuine half-lap offset - it doesn't). Both guards
            // starting at their own patrolMaxX with the same facing, speed and pause means their
            // whole motion - not just which way they face, but exactly when each one walks versus
            // dwells - is IDENTICAL in local/relative terms, just mirrored by which side of the gap
            // each crate is on. That mirroring is exactly why they never simultaneously face the
            // gap (checked directly, see testLevel3OverwatchGuardsNeverBothFaceTheMiddleAtOnce) -
            // but it also means they always move and always stop at the exact same instant, which
            // reads as a bug, not a feature, when both are on screen at once (they are - the gap
            // between the crates is only 150 units, well inside typical view width). Reported
            // directly by the owner, not something a test caught.
            //
            // Starting guard2 mid-route instead (his OWN patrolMinX + 90, not either endpoint)
            // breaks the mirror identity on purpose: proven directly (not just plausible) that ANY
            // nonzero phase shift between two guards sharing an identical route/speed/pause
            // reopens SOME window where they'd both actually detect a player standing in the gap -
            // a perfect zero-risk guarantee and a visibly staggered pair are mutually exclusive
            // here, not a bug to be fully fixed. This offset was chosen, and the owner explicitly
            // accepted the trade-off, after simulating many candidate offsets directly against
            // VisionSystem: it's a middle ground, not a risk-free pick - the fraction of time both
            // could actually spot a player in the gap drifts over a long session (this simple
            // integer-tick simulation doesn't hold a perfectly fixed relative phase forever), most
            // commonly landing in roughly the 5-10% range but occasionally higher, rather than the
            // old design's guaranteed 0%. See testLevel3OverwatchGuardsNeverBothFaceTheMiddleAtOnce
            // for the actual tolerance this settled on and why it isn't a strict zero anymore.
            val overwatchGuard1 = GuardSpawn(
                startX = longCrate1.right - overwatchGuardMargin - overwatchGuardWidth, surfaceY = longCrateElevation,
                patrolMinX = longCrate1.x + overwatchGuardMargin,
                patrolMaxX = longCrate1.right - overwatchGuardMargin - overwatchGuardWidth,
                speed = overwatchSpeed, facing = 1.0, visionRange = 220.0,
                width = overwatchGuardWidth, height = 96.0,
                visionTilt = overwatchVisionTilt,
                patrolPauseDuration = overwatchPauseDuration
            )
            val overwatchGuard2 = GuardSpawn(
                startX = longCrate2.x + overwatchGuardMargin + 90.0,
                surfaceY = longCrateElevation,
                patrolMinX = longCrate2.x + overwatchGuardMargin,
                patrolMaxX = longCrate2.right - overwatchGuardMargin - overwatchGuardWidth,
                speed = overwatchSpeed, facing = 1.0, visionRange = 220.0,
                width = overwatchGuardWidth, height = 96.0,
                visionTilt = overwatchVisionTilt,
                patrolPauseDuration = overwatchPauseDuration
            )

            // A second "climb past the watcher" beat, mirroring the level's own opening (crate ->
            // climbable beam, someone watching the ground underneath) but with a fixed camera
            // instead of a patrolling guard. Unlike the table's leg, there's no support at THIS
            // (near/climb) end - only at the beam's far end (cameraLeg, below) - so the climb path
            // itself stays exactly as open as before.
            val stepCrate2 = Rect(x = longCrate2.right + 100.0, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // Same 96-unit rise as the opening crate -> tablePlank climb (crate.top 392 to plank
            // top 296 there; stepCrate2.top 392 to here 296, identical numbers), so it climbs the
            // same way and needs the same floating-ledge exemption (LevelLayout.floatingClimbTargets)
            // - nothing braces it from below at the climb point.
            val cameraBeamWidth = 300.0
            val cameraBeamDepth = 30.0
            val cameraBeam = Rect(x = stepCrate2.right, y = groundY - 144.0, width = cameraBeamWidth, height = cameraBeamDepth)

            // A support leg at the beam's far end, mirroring tablePlank's own rightLeg above (same
            // crop, same tableDecorations/boxes wiring) - nothing in this level should visibly hang
            // in mid-air with no structure under it. Placed at the far/right end, clear of both the
            // camera mount (at the beam's own left corner, cameraBeam.x) and the climb up from stepCrate2 at
            // that same end, so it's pure background structure with nothing to time around - same
            // role rightLeg plays under the table.
            val cameraLegWidth = 30.0
            val cameraLegLift = 16.0 // tucks the joint up against the beam's own underside, same as rightLeg
            val cameraLeg = Rect(
                x = cameraBeam.right - cameraLegWidth,
                y = cameraBeam.bottom - cameraLegLift,
                width = cameraLegWidth,
                height = groundY - (cameraBeam.bottom - cameraLegLift)
            )

            // Mounted at the beam's own LEFT CORNER (cameraBeam.x exactly) - sits right at the edge
            // closest to stepCrate2, fully supported (the mount's own art is width 20, so it sits
            // flush against the corner rather than hanging off it - see Camera.width/pivotPosition).
            // Aimed straight down (90 degrees) at rest, sweeping right (toward the open corridor) to
            // 55 and left (toward stepCrate2) to 111.3 - NOT a symmetric sweep either side of
            // vertical.
            //
            // maxAngle/visionFov went through a real reckoning across several rounds (see git
            // history for the full account) - the short version: reaching stepCrate2's own FAR top
            // corner requires aiming right at the edge of the FOV, at the crate's own corner - the
            // exact condition that makes VisionSystem's shadow-casting cast one ray that clears the
            // corner by a hair and keeps going, straight past the crate to the GROUND far beyond it
            // ("light rays going out of the camera"). One round dropped the far-corner requirement
            // for safety; the next brought it back once the mount moved to the beam's own left
            // corner. Since then, maxAngle has been re-derived twice more, each time the arm length
            // (Camera.NECK_LENGTH/LENS_LENGTH) changed for an unrelated "camera looks too big/small"
            // complaint: shrinking or growing the arm moves the eye itself, which moves exactly
            // which (maxAngle, visionFov) pairs graze the corner, so this maxAngle is only valid for
            // the CURRENT arm length (9.5/20) - it is NOT a fixed, portable number.
            //
            // At the current arm length, re-running the same polygon-based search found the safe
            // margin before the "grazes the corner and spikes" cliff at ~0.5 degrees, so maxAngle =
            // 111.3 (visionFov stays 80, unchanged) - confirmed by scanning the whole sweep in
            // 0.25-degree steps (plus minAngle/maxAngle explicitly - see the floating-point-drift
            // lesson in testLevel3CameraConePolygonNeverPastCrate) for the radial-jump spike
            // signature. The cone's leftmost reach lands at stepCrate2.x + ~1.6 units - visually
            // flush against the crate's own far corner without ever crossing it, and zero stray-ray
            // spikes anywhere across the full 55..111.3 sweep (checked, not assumed - see
            // testLevel3CameraConeHasNoStrayRaySpikes and
            // testLevel3CameraLeftmostSweepReachesStepCrateFarCorner below). **Re-run this same
            // search (not a point-sampled grid, not reusing an old maxAngle) any time
            // NECK_LENGTH/LENS_LENGTH, the mount position, sweep range, visionFov or visionRange
            // change again** - this pairing has now been re-derived after every arm-length change,
            // and there's no reason to expect that to stop.
            //
            // visionRange stays close to a free variable for the "never past the crate, never a
            // stray ray" concern specifically: every ray this cone can cast is blocked by either
            // the crate or the GROUND (which runs the full level width) well within 220 units of
            // this mount, so range increases past that are a bigger number with no visual effect -
            // confirmed identical results from 120 up to 300.
            val beamCamera = CameraSpawn(
                x = cameraBeam.x,
                y = cameraBeam.bottom,
                minAngle = 55.0 * (PI / 180.0),
                maxAngle = 111.3 * (PI / 180.0),
                startAngle = 55.0 * (PI / 180.0),
                sweepSpeed = 0.6,
                // 220 - see the maxAngle comment above: the natural ceiling past which more range
                // has zero visual effect, not an arbitrary bigger number.
                visionRange = 220.0,
                // 80 - genuinely wide (nearly double the original 45), chosen a couple of rounds ago
                // from a full re-search of the (maxAngle, visionFov) space against the actual
                // rendered polygon, and kept unchanged by every re-tuning of maxAngle/x/arm-length
                // since.
                visionFov = 80.0 * (PI / 180.0),
                // Holds at each side of its sweep instead of endlessly panning - the same
                // dwell-then-move rhythm the guards use (Guard.patrolPauseDuration), so there's an
                // actual moment to read and time a crossing against, not just a constantly moving
                // beam with no stable state.
                sweepPauseDuration = 3.0
            )

            // Ground dressing right after the camera, ALL under the beam's own span now - two
            // barrels, a single crate, then two crates stacked - pulled left of cameraLeg (the
            // support post at the beam's far end) on request, not past it. Used to sit past the
            // beam entirely (right of cameraLeg), which also briefly overlapped finalHangingCrate's
            // own footprint by 48 units before that was fixed - now it's tucked entirely into the
            // gap between the camera mount and the leg instead, with real clearance on both sides.
            //
            // Only the crate/stacked-crate pair and stepCrate2 draw with woodcrate2.png
            // (LevelLayout.woodCrates) - the two barrels stay barrel.png. An earlier pass swapped
            // all four ground-dressing boxes to wood-crate art, misreading a screenshot that showed
            // all four as roughly crate-shaped; corrected on request back to barrels staying
            // barrels - LevelLayout.barrels is exactly the two fillerBarrels again. stepCrate2 (the
            // step up to the beam, defined near LEVEL_3_LAYOUT's top) was added to woodCrates on a
            // later request ("replace the solid crate left to the two barrels with these new crates
            // as well") - it's the only crate-shaped box positioned before/left of fillerBarrels in
            // this whole section, so that's read as referring to it.
            val fillerBarrelWidth = 32.0
            val fillerBarrelHeight = 48.0
            val fillerBarrels = listOf(
                Rect(x = cameraBeam.x + 30.0, y = groundY - fillerBarrelHeight, width = fillerBarrelWidth, height = fillerBarrelHeight),
                Rect(x = cameraBeam.x + 64.0, y = groundY - fillerBarrelHeight, width = fillerBarrelWidth, height = fillerBarrelHeight)
            )
            // Three separate crates under the camera beam: two full crates side by side on the ground,
            // touching (woodCrateBaseRight.x == woodCrateBaseLeft.right, zero gap), with the top crate
            // stacked directly on top of the rightmost crate on request ("stack this crate on top of
            // the right most crate"). Sunk 2 units (woodCrateStackSink) into the base crate so the
            // corner tabs/ears overlap seamlessly and sit flush without floating gaps.
            val woodCrateStackSink = 2.0
            val woodCrateBaseLeft = Rect(x = cameraBeam.x + 106.0, y = groundY - crateHeight, width = crateWidth, height = crateHeight)
            val woodCrateBaseRight = Rect(x = woodCrateBaseLeft.right, y = groundY - crateHeight, width = crateWidth, height = crateHeight)
            val woodCrateTop = Rect(x = woodCrateBaseRight.x, y = groundY - crateHeight * 2.0 + woodCrateStackSink, width = crateWidth, height = crateHeight)
            val woodCrates = listOf(stepCrate2, woodCrateBaseLeft, woodCrateBaseRight, woodCrateTop)

            // Unrelated to the ground-dressing crates above - this is the footprint for the SEPARATE
            // crate/stacked-pair sitting on TOP of finalPlatform (platformCrate/platformStackedCrates
            // below), which weren't touched by the ground-dressing rearrangement.
            val stackedCrateWidth = crateWidth
            val stackedCrateHeight = crateHeight * 2.0

            // One last hanging crate past the beam - same shape and look as the ground gauntlet's
            // own pair (hangingCrateVariant1), but no guard standing on this one, just an obstacle
            // to cross. Top flush with the beam's own top, so it's a same-height jump across, not a
            // climb.
            //
            // The gap (56, was 55, was 40, was 120 before that) went through a real reckoning: a
            // same-height running jump in this engine's actual physics (Player's moveSpeed 132,
            // jumpSpeed -320, gravity 1000) tops out at roughly 56.5-56.66 units for this exact
            // geometry (296 top, 144 tall, real player size) before the falling body starts
            // vertically overlapping the target platform's own slab while still short of it
            // horizontally - which the collision code (see Player.updateStep's horizontal pass)
            // then treats as hitting a WALL, not "still falling", pinning the player against the
            // target's near face until they've sunk well past it and simply drop into the gap. 120
            // was never actually reachable; measured directly by binary-searching the real
            // Player.update/GameWorld loop (not projectile-arc arithmetic, which overstates it at
            // ~84 units - that number ignores this same wall-collision quirk) for the exact
            // cutoff. 40 was the first fix and left a lot of that budget unused (comfortable, but a
            // trivial walk-across, not a felt jump); 55 widened it with room to spare; on request
            // for a little more, checked the jump-timing slack at several points between 55 and the
            // ~56.5 ceiling and found NO drop-off anywhere in that range - every gap from 55 up to
            // 56.5 still accepts a jump pressed anywhere from right-at-the-edge to ~22 units early,
            // so there was no reason to stay at 55. Picked 56: a genuine further widening with
            // ~0.5-0.66 units still held back from the hard ceiling (56.5 was judged too close to
            // risk, being in the same 0.25-unit search bracket as the first confirmed failure at
            // ~56.66). See testLevel3CanJumpFromCameraBeamAcrossToFinalHangingCrateAndOnToFinalPlatform,
            // the walkthrough test that drives a player through this jump for real -
            // testLevel2HangingCratesGapIsBeatable, the one test in the codebase that looks like it
            // proves a similar gap works, only asserts a target X is reached, which the level's own
            // full-width ground floor satisfies on its own even if every elevated jump in between is
            // missed - it was never actually exercising the crate-to-crate route it looks like it
            // validates. **Re-run the same binary search (not a reused number) if this jump's
            // geometry changes again** - it depends only on platform heights/player size, not on
            // platform width, so the finalPlatformWidth change below doesn't affect it.
            //
            // Raised 2 units above cameraBeam.top on request ("lift the unmanned hanging crate a
            // little bit") - no longer an EXACT same-height jump on either side, just very close to
            // one. 2 is not a stylistic choice: at the 56-unit gap fixed above, this jump's own
            // physics budget is nearly exhausted (the ceiling is ~56.5-56.66 for a true same-height
            // jump), and adding ANY required vertical rise eats into what's left - binary-searching
            // the real Player/GameWorld loop again (same method as the gap search above, just with
            // an asymmetric launch/landing height this time) found the beam -> crate jump (now
            // upward) stops clearing 56 units at a lift of ~2.03; 2.0 keeps a sliver of margin
            // (~56.48 max reach at this lift) with the exact same jump-timing slack as before
            // (0-22 units early, unchanged - the lift didn't cost anything there, only in raw
            // distance). The reverse leg (crate -> finalPlatform, now a downward jump) has no such
            // problem - falling toward a LOWER target only ever makes a same-height jump easier, so
            // it wasn't re-checked at the same precision. **A bigger lift is possible but would
            // require narrowing the 56-unit gap too - re-run the same search, don't just move this
            // number up, if that trade is ever wanted.**
            val finalHangingCrate = Rect(x = cameraBeam.right + 56.0, y = cameraBeam.top - 2.0, width = longCrateWidth, height = longCrateHeight)

            // A crate perched on TOP of the hanging crate, flush with its right corner - resting
            // directly on finalHangingCrate's own top surface, not floating above it.
            val hangingEndCrate = Rect(x = finalHangingCrate.right - crateWidth, y = finalHangingCrate.top - crateHeight, width = crateWidth, height = crateHeight)

            // A plain elevated block after the hanging crate, same idea as GameWorld.createDefault's
            // own block2/block3 in level 1 (no crate/table art - falls through to GameplayScene.kt's
            // generic rough-block render, the same "normal platform" look).
            //
            // Width bumped 260 -> 340 on request ("increase the length of the platforms right of
            // the unmanned crate") - purely a landing-zone size change; doesn't touch the jump gap
            // (only platform height/player size affect that, not width) or platformCrate's own
            // centering below (platformCrateGroupStartX re-centers automatically for whatever
            // finalPlatformWidth is).
            //
            // Height dropped 144 -> 96 on request ("make the platform on the right smaller to be
            // able to climb up") - matches this level's own established climbable rise (the same 96
            // used for crate -> tablePlank at the level's opening, and stepCrate2 -> cameraBeam),
            // which sits inside Player's own climb window (climbMinHeight = maxJumpHeight = 51.2,
            // climbMaxHeight = 115.0 - see Player.findClimbTarget) - a plain jump can't cover a rise
            // that tall, but a climb can, and finalPlatform still rests flush on the ground below
            // (bottom == groundY, not a floating ledge), so no floatingClimbTargets exemption is
            // needed for the climb to register. finalPlatform.top is no longer flush with
            // finalHangingCrate.top (was an intentional near-same-height jump before this) - crossing
            // the 56-unit gap from finalHangingCrate is now a ~50-unit DOWNWARD jump instead, which
            // only ever makes a same-height jump easier, never harder (see finalHangingCrate's own
            // comment above) - re-verified directly with the walkthrough test after this change,
            // same as every other jump-affecting edit in this level.
            val finalPlatformWidth = 340.0
            val finalPlatformHeight = 96.0
            val finalPlatform = Rect(x = finalHangingCrate.right + 56.0, y = groundY - finalPlatformHeight, width = finalPlatformWidth, height = finalPlatformHeight)

            // A crate and a two-stacked pair sitting on TOP of finalPlatform, centred along its
            // width - on request, "in the middle of the platform", touching each other (no gap
            // between them, also on request - used to have a 20-unit gap). Resting directly on the
            // platform's own surface (bottom flush with finalPlatform.top), not floating above it.
            val platformCrateGroupWidth = crateWidth + stackedCrateWidth
            val platformCrateGroupStartX = finalPlatform.x + (finalPlatformWidth - platformCrateGroupWidth) / 2.0
            val platformCrate = Rect(x = platformCrateGroupStartX, y = finalPlatform.top - crateHeight, width = crateWidth, height = crateHeight)
            val platformStackedCrates = Rect(x = platformCrate.right, y = finalPlatform.top - stackedCrateHeight, width = stackedCrateWidth, height = stackedCrateHeight)

            // A second, pole-mounted camera at finalPlatform's own LEFT corner (on request) - not
            // bolted to a beam like beamCamera, just a freestanding post (pole.png) standing on the
            // platform surface. NOT interactable (on request): not in [boxes], so the player walks
            // straight past/under it with no collision. Still real drawn geometry, so it's in
            // LevelLayout.poles and gets folded into occluders (GameWorld.createFromLayout) the
            // same as any other solid prop - it can cast its own shadow like anything else, which
            // is expected, not a bug (see poleCamera's own comment below on self-shadowing).
            val poleWidth = 18.0
            val poleHeight = 130.0
            val pole = Rect(x = finalPlatform.x, y = finalPlatform.top - poleHeight, width = poleWidth, height = poleHeight)

            // Camera mounted at the pole's own top (pole.y - its highest point), centred on the
            // pole's width - same "hangs below its mount via a fixed neck" convention as beamCamera
            // (Camera.pivotPosition/eyePosition), just pointed the other way round: instead of a
            // beam's underside looking down at the ground, this is a post's top looking out toward
            // the two things flanking it: on request, "rotates left and right ... the light cone of
            // it should go from the boxes in the right [platformCrate/platformStackedCrates, on
            // finalPlatform itself] to the box in the left [finalHangingCrate, across the jump gap]".
            //
            // minAngle 20 / maxAngle 160 (measured against VisionSystem.computeVisionPolygon, the
            // same tool used to tune beamCamera): at 20 the cone's rightmost reach lands past
            // platformStackedCrates' own far corner (~2547 vs the crate group ending at 2498); at
            // 160 its leftmost reach lands past finalHangingCrate's own near corner (~1990 vs
            // finalHangingCrate starting at 2030) - both extremes comfortably past their target,
            // not a hairline graze.
            //
            // Unlike beamCamera vs stepCrate2, this sweep does NOT try to keep the cone from ever
            // crossing a corner: this pole stands right at the edge of the same jump gap the player
            // crosses, and finalPlatform's own near corner + hangingEndCrate's own corner both sit
            // close enough to the eye that swinging the cone across them makes it dip down past
            // each one into open space beyond (the gap floor, or finalHangingCrate's own lower
            // surface) - a real, expected depth change as the cone pans across uneven terrain, the
            // same way any flashlight reveals more or less ground when swept across a ledge. That's
            // different in kind from the stray-ray-spike bug documented on beamCamera (an epsilon
            // rendering artifact where the fix was to keep a specific corner from ever being grazed
            // at all) - there is no single "must never cross this corner" requirement here, so it
            // is not tested the same way; see testLevel3PoleCameraReachesBothFlankingBoxGroups
            // instead, which checks what this camera is actually FOR (reaching both box groups).
            //
            // Mount lowered to pole.y (flush with the pole's top cap) on request ("also this camera
            // looks like its floating. make it look like its fixed to the top of the column it is
            // connected to"). Previous -5.0 offset hovered the mounting plate 5 units in the air
            // above the column; at pole.y the ceiling-plate bracket rests directly on top of the
            // pole collar as a solid cap.
            // Sweep speed increased 0.6 -> 0.85 on request ("make the rightmost corner camera a little
            // faster").
            val poleCamera = CameraSpawn(
                x = pole.x + poleWidth / 2.0 - 10.0,
                y = pole.y,
                minAngle = 20.0 * (PI / 180.0),
                maxAngle = 160.0 * (PI / 180.0),
                startAngle = 20.0 * (PI / 180.0),
                sweepSpeed = 0.85,
                visionRange = 230.0,
                visionFov = 50.0 * (PI / 180.0),
                sweepPauseDuration = 3.0
            )

            val finalExitX = finalPlatform.right + 300.0
            // Extended past the finish structure (entrance booth ~117 + exit fence ~312 = ~430 units)
            // on request ("increase the length of the ground a little at the very end to reach the finish").
            val finalWorldWidth = finalExitX + 460.0
            val ground = Rect(x = 0.0, y = groundY, width = finalWorldWidth, height = 100.0)

            LevelLayout(
                worldWidth = finalWorldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = finalExitX, y = groundY - 100.0, width = 44.0, height = 100.0),
                platforms = listOf(ground),
                boxes = listOf(
                    crate, tablePlank, hideCrate, rightLeg, longCrate1, longCrate2,
                    stepCrate2, cameraBeam, cameraLeg, finalHangingCrate, hangingEndCrate, finalPlatform
                ) + fillerBarrels + listOf(woodCrateBaseLeft, woodCrateBaseRight, woodCrateTop, platformCrate, platformStackedCrates),
                guards = listOf(roofGuard, overwatchGuard1, overwatchGuard2),
                cameras = listOf(beamCamera, poleCamera),
                translucentCameras = listOf(poleCamera),
                barrels = fillerBarrels,
                woodCrates = woodCrates,
                poles = listOf(pole),
                tables = listOf(tablePlank, cameraBeam),
                tableParts = listOf(tablePlank, cameraBeam),
                tableDecorations = listOf(rightLeg, cameraLeg),
                floatingClimbTargets = listOf(tablePlank, cameraBeam),
                hangingCrateVariant1 = listOf(longCrate1, longCrate2, finalHangingCrate)
            )
        }

        val DEFAULT_LEVEL_3 = LevelData(
            id = "level_3",
            name = "03: First Contact",
            timeTargetSeconds = 25.0f,
            description = "Security is active. Avoid guards and cameras to reach the conveyor belt.",
            layout = LEVEL_3_LAYOUT,
            tutorialSteps = listOf(
                TutorialStep(
                    id = "step_crouch_hide",
                    // From the player's very first frame (LEVEL_3_LAYOUT.playerStartX), not partway
                    // in - the guard is rooted at his post from the start too (see roofGuard's
                    // holdUntilPlayerCrouches), so there's no early window where showing this would
                    // be a lie.
                    triggerMinX = LEVEL_3_LAYOUT.playerStartX,
                    triggerMaxX = 415.0,
                    title = "STAY HIDDEN",
                    instructionTouch = "Hold CROUCH behind the crate to break the guard's line of sight.",
                    instructionDesktop = "Hold [S], [C] or [CTRL] behind the crate to break the guard's line of sight.",
                    targetAction = TutorialAction.CROUCH,
                    // World-anchored at the crate itself (like step_reach_objective in level 1)
                    // rather than at the crouch button - the crate is the thing to hide behind,
                    // not just an input to press. Fades on the same CROUCH actionDone check as
                    // any other highlight, so it stops pointing the moment the player crouches.
                    highlight = TutorialControlHighlight.NONE,
                    handwrittenCallout = "Crouch behind the crate to hide!",
                    worldTextX = 260.0,
                    worldTextY = 160.0,
                    worldAnchorX = 410.0, // just left of the crate itself (crate spans 420..488) - clear of its outline, not touching it
                    worldAnchorY = 416.0, // crate mid-height, not the top corner - reads as pointing at the side face
                    arrowBowsLeft = true // anchor sits close to/left of the text; the default rightward bow cut across the crate
                )
            )
        )

        val DEFAULT_LEVEL_4 = LevelData(
            id = "level_4",
            name = "04: Moving Target",
            timeTargetSeconds = 115.0f,
            description = "Search the moving conveyor belt for Container 17 while avoiding lasers.",
            objectiveHint = "Traverse the Conveyor Line",
            layout = LEVEL_4_LAYOUT,
            backgroundImage = "metalbg.png",
            hasDarknessVignette = true,
            playerCrouchForwardSpeedMultiplier = 1.45
        )

        /**
         * Recovered from history for level 5. Originally built as level 4's "Blind Spot", then
         * replaced there by the conveyor-belt run (see LEVEL_4_LAYOUT above) - restored here
         * verbatim rather than redesigned, guards and all (there are none - see below).
         *
         * A two-tier barrel staircase blocks the ground path outright: an 8-wide bottom layer with
         * a 4-wide top layer sitting on its far half, so the near half of the bottom layer is left
         * exposed as a real landing spot. That matters physically, not just visually: the engine's
         * climb/jump check only ever looks at one box's own bottom/top face (see
         * Player.findClimbTarget), so a second layer sitting directly above the first IN THE SAME
         * COLUMNS would occupy the only spot the player could land on to reach it (the player's own
         * height, 96, equals two stacked 48-tall layers), leaving no way up at all. Offsetting the
         * top layer sideways instead of stacking it in-place turns the climb into two ordinary
         * adjacent-box jumps (ground -> bottom layer's exposed half -> top layer -> terrain1), the
         * same proven pattern as LEVEL_2_LAYOUT's crate1 (48 units, comfortably under
         * Player.maxJumpHeight 51.2).
         *
         * Past the barrel stack, terrain1 sits one more 48-unit jump higher (296, this game's
         * established "high tier" height) and is a solid block reaching all the way down to the
         * ground (144 tall) rather than a thin floating platform with open air beneath it.
         *
         * The gap after terrain1 is crossed by swinging from the hook hanging over it: walk into it
         * and press JUMP, the same button the climb already uses. It is the only way across - the
         * gap is twice a running jump - and it is the one place in the game the swing exists at all,
         * so the geometry here and Player's swing constants are a matched pair (see "The swing move"
         * in .junie/guidelines.md). Walkthrough-verified in GameplayModelTest (the swing tests drive
         * this exact layout end to end via SIDE_SCROLL_LEVEL_LAYOUT/SIDE_SCROLL_LEVEL).
         *
         * No guards - left exactly as it was when it was cut for time, deliberately not fleshed out
         * on recovery.
         */
        val SIDE_SCROLL_LEVEL_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 3000.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            val barrelWidth = 32.0
            val barrelLayerHeight = 48.0
            val barrelWallX = 400.0 // a short run-up from the start fence, matching LEVEL_2's crate1 distance

            val bottomLayerCount = 8
            val topLayerCount = 4
            val bottomLayerY = groundY - barrelLayerHeight
            val topLayerX = barrelWallX + (bottomLayerCount - topLayerCount) * barrelWidth // top layer sits on the FAR half
            val topLayerY = groundY - barrelLayerHeight * 2.0

            val bottomBarrelLayer = (0 until bottomLayerCount).map { i ->
                Rect(x = barrelWallX + i * barrelWidth, y = bottomLayerY, width = barrelWidth, height = barrelLayerHeight)
            }
            val topBarrelLayer = (0 until topLayerCount).map { i ->
                Rect(x = topLayerX + i * barrelWidth, y = topLayerY, width = barrelWidth, height = barrelLayerHeight)
            }
            val barrelWall = bottomBarrelLayer + topBarrelLayer
            val barrelWallEndX = topLayerX + topLayerCount * barrelWidth

            // Solid elevated terrain, one more 48-unit jump above the barrel stack's top layer.
            val terrainTopY = topLayerY - barrelLayerHeight
            val terrainHeight = groundY - terrainTopY
            val terrain1 = Rect(x = barrelWallEndX, y = terrainTopY, width = 300.0, height = terrainHeight)

            // The first gap is crossed by swinging from the hook below (width 150, hook at middle 75).
            val gapWidth = 150.0
            val terrain2 = Rect(x = terrain1.right + gapWidth, y = terrainTopY, width = 300.0, height = terrainHeight)

            // The chain-and-hook (hook.png) the player swings from, positioned by its GRIP - see
            // Player.HOOK_GRIP_X/Y_FRACTION - with the art hung off that, not the other way round.
            val hookWidth = 16.0
            val hookHeight = hookWidth * (2136.0 / 154.0) // hook.png's own cropped aspect ratio
            val hook1GripX = terrain1.right + gapWidth / 2.0
            val hook1GripY = terrainTopY - 112.0
            val swingHook1 = Rect(
                x = hook1GripX - hookWidth * Player.HOOK_GRIP_X_FRACTION,
                y = hook1GripY - hookHeight * Player.HOOK_GRIP_Y_FRACTION,
                width = hookWidth,
                height = hookHeight
            )

            // Three thin platforms following terrain2, requiring continuous jumping to build/keep momentum.
            // Spanned by 48-unit gaps (well within the safe ~56 unit same-height jump window), anchored to the ground.
            val thinPlatformWidth = 40.0
            val thinPlatformGap = 48.0
            val thinPlatform1 = Rect(x = terrain2.right + thinPlatformGap, y = terrainTopY, width = thinPlatformWidth, height = terrainHeight)
            val thinPlatform2 = Rect(x = thinPlatform1.right + thinPlatformGap, y = terrainTopY, width = thinPlatformWidth, height = terrainHeight)
            val thinPlatform3 = Rect(x = thinPlatform2.right + thinPlatformGap, y = terrainTopY, width = thinPlatformWidth, height = terrainHeight)

            // Second hook swing over a 150-unit gap after the 3 thin platforms, carrying onto a long platform.
            val hook2GripX = thinPlatform3.right + gapWidth / 2.0
            val hook2GripY = terrainTopY - 112.0
            val swingHook2 = Rect(
                x = hook2GripX - hookWidth * Player.HOOK_GRIP_X_FRACTION,
                y = hook2GripY - hookHeight * Player.HOOK_GRIP_Y_FRACTION,
                width = hookWidth,
                height = hookHeight
            )

            // Long platform carrying the lever mechanism
            val terrain3 = Rect(x = thinPlatform3.right + gapWidth, y = terrainTopY, width = 340.0, height = terrainHeight)

            // Interactive lever on terrain3, positioned right at the corner
            val leverWidth = 22.0
            val leverHeight = 12.0
            val lever = Lever(
                id = "lever_1",
                x = terrain3.right - 30.0,
                y = terrainTopY - leverHeight,
                width = leverWidth,
                height = leverHeight,
                targetMechanismId = "hook_crate_1"
            )

            // Third hook swing over a 150-unit gap after terrain3
            val hook3GripX = terrain3.right + gapWidth / 2.0
            val hook3GripY = terrainTopY - 112.0
            val swingHook3 = Rect(
                x = hook3GripX - hookWidth * Player.HOOK_GRIP_X_FRACTION,
                y = hook3GripY - hookHeight * Player.HOOK_GRIP_Y_FRACTION,
                width = hookWidth,
                height = hookHeight
            )

            // Crate attached via rope to swingHook3, blocking it from being swung until the lever is pulled
            // Uses Level 4's realistic rectangular aspect ratio (68.0 x 48.0)
            val hookCrateWidth = 68.0
            val hookCrateHeight = 48.0
            val hookRopeLength = 36.0
            val hookEdgeX = hook3GripX - 4.5
            val hookCrate = HookCrate(
                id = "hook_crate_1",
                hook = swingHook3,
                bounds = Rect(
                    x = hookEdgeX - hookCrateWidth / 2.0,
                    y = hook3GripY + hookRopeLength,
                    width = hookCrateWidth,
                    height = hookCrateHeight
                ),
                ropeLength = hookRopeLength
            )

            // Landing platform past the third hook (receiving swing landing at x = 2344.0)
            val terrain4 = Rect(x = terrain3.right + gapWidth, y = terrainTopY, width = 200.0, height = terrainHeight)

            // Exit zone grounded on the floor (groundY), preceded by 240 units of flat surface after terrain4
            val exitX = 2750.0

            // Safe manual checkpoints placed strictly on elevated platforms to prevent
            // respawning in trapped pits below when Checkpoints powerup is active.
            val checkpoints = listOf(
                Checkpoint(
                    id = "lvl5_cp1_terrain1",
                    x = terrain1.left + 44.0,
                    y = terrainTopY - 96.0,
                    triggerZone = Rect(terrain1.left, terrainTopY - 120.0, terrain1.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl5_cp2_terrain2",
                    x = terrain2.left + 74.0,
                    y = terrainTopY - 96.0,
                    triggerZone = Rect(terrain2.left, terrainTopY - 120.0, terrain2.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl5_cp3_terrain3",
                    x = terrain3.left + 60.0,
                    y = terrainTopY - 96.0,
                    triggerZone = Rect(terrain3.left, terrainTopY - 120.0, terrain3.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl5_cp4_terrain4",
                    x = terrain4.left + 50.0,
                    y = terrainTopY - 96.0,
                    triggerZone = Rect(terrain4.left, terrainTopY - 120.0, terrain4.width, 140.0)
                )
            )

            LevelLayout(
                worldWidth = 3200.0,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = exitX, y = groundY - 100.0, width = 44.0, height = 100.0),
                platforms = listOf(Rect(x = 0.0, y = groundY, width = 3200.0, height = 100.0)),
                boxes = barrelWall + listOf(terrain1, terrain2, thinPlatform1, thinPlatform2, thinPlatform3, terrain3, terrain4),
                guards = emptyList(),
                barrels = barrelWall,
                swingHooks = listOf(swingHook1, swingHook2, swingHook3),
                levers = listOf(lever),
                hookCrates = listOf(hookCrate),
                manualCheckpoints = checkpoints
            )
        }

        val SIDE_SCROLL_LEVEL = LevelData(
            id = "level_5",
            name = "05: The Crane Yard",
            timeTargetSeconds = 60.0f,
            description = "Container 17 is gone. Search the crane yard for signs of where it went.",
            objectiveHint = "Get Into the Restricted Area",
            coinRewardBase = 90,
            coinRewardPerStar = 40,
            layout = SIDE_SCROLL_LEVEL_LAYOUT,
            tutorialSteps = listOf(
                TutorialStep(
                    id = "step_swing_hook",
                    // Terrain1 - the ledge reached by climbing the barrel stack - is where the
                    // player is actually running when the hook comes into view, same shape as
                    // level 1's step_climb triggering right at its own obstacle. Ends comfortably
                    // before Player.swingMaxReach's own trigger window opens (see Player
                    // .findSwingTarget), with a 10 unit margin, so the callout has already been on
                    // screen for a moment before the window where pressing jump actually matters.
                    triggerMinX = SIDE_SCROLL_LEVEL_LAYOUT.boxes.first { it.width == 300.0 }.left + 20.0,
                    triggerMaxX = Player.hookGripX(SIDE_SCROLL_LEVEL_LAYOUT.swingHooks.first()) -
                        Player(x = 0.0, y = 0.0).swingMaxReach - 10.0,
                    title = "HOOK SWING",
                    instructionTouch = "Press JUMP while running into the hanging hook to swing across the gap.",
                    instructionDesktop = "Press [W] or [SPACE] while running into the hanging hook to swing across the gap.",
                    targetAction = TutorialAction.SWING,
                    // World-anchored at the hook itself (like step_reach_objective in level 1) -
                    // it's a real grab point out in the level, not a screen-fixed control button.
                    highlight = TutorialControlHighlight.NONE,
                    handwrittenCallout = "Press jump while running to swing across!",
                    worldTextX = 540.0,
                    worldTextY = 135.0,
                    // The hook's own GRIP - the point Player.findSwingTarget actually measures
                    // reach/height from (see Player.HOOK_GRIP_X/Y_FRACTION) - not a corner of its
                    // art, so the arrow always lands on the real grab point even if the hook is
                    // ever repositioned.
                    worldAnchorX = Player.hookGripX(SIDE_SCROLL_LEVEL_LAYOUT.swingHooks.first()),
                    worldAnchorY = Player.hookGripY(SIDE_SCROLL_LEVEL_LAYOUT.swingHooks.first())
                ),
                TutorialStep(
                    id = "step_activate_lever",
                    triggerMinX = 1840.0,
                    triggerMaxX = 2170.0,
                    title = "ACTIVATE MECHANISM",
                    instructionTouch = "Click interact button near levers to activate mechanisms",
                    instructionDesktop = "Click interact button near levers to activate mechanisms",
                    targetAction = TutorialAction.INTERACT,
                    highlight = TutorialControlHighlight.NONE,
                    handwrittenCallout = "Click interact button near levers to activate mechanisms",
                    worldTextX = 1760.0,
                    worldTextY = 160.0,
                    worldAnchorX = SIDE_SCROLL_LEVEL_LAYOUT.levers.first().centerX,
                    worldAnchorY = SIDE_SCROLL_LEVEL_LAYOUT.levers.first().y
                )
            )
        )

        /**
         * Work in progress - three sections now (same "first section only" pattern
         * LEVEL_3_LAYOUT/SIDE_SCROLL_LEVEL_LAYOUT were both built with, just extended twice since).
         *
         * Section 1: crate -> platform -> lever -> timed moving-crate swing -> landing crate ->
         * platform. Section 2 (past farTerrain) was originally built with a barrel pyramid, a
         * second lever gating a blocking crate, and an undetached hook+rope crate up on the far
         * block, but all of that was removed again on request; it's now a real pit (forcing a fall
         * to the ground) with an overwatch guard on a hanging crate above it, followed by a plain
         * climb up the far block. Section 3 (past tallBlock) is the crane crossing: two ground
         * barrels side by side leading onto a low (groundY-48) platform with a real but
         * deliberately non-functional lever near its left corner (no targetMechanismId - see
         * Lever), and a crawler crane parked on a second platform of the same height immediately
         * past that lever, its lattice boom reaching back over the lever, the barrels and the
         * ground gap to overhang the last stretch of tallBlock. The machine itself is a wall from
         * the ground (see CraneDef.bodyBounds); the way past it is over the top, climbing onto the
         * boom from tallBlock on the far side of the gap and walking it down the machine's own
         * stepped silhouette to the exit. See each section's own comments below for their geometry
         * reasoning.
         *
         * Crate -> platform -> lever -> timed moving-crate swing -> landing crate -> platform.
         * Everything from crate1 onward sits at this game's usual "3x crate height" high tier
         * (terrainTopY = groundY - 144, same tier LEVEL_2/LEVEL_3/SIDE_SCROLL_LEVEL_LAYOUT all
         * use) so the swing's own landing check - which requires the departure and landing
         * surfaces to be within 4 units of the same height (see Player.findSwingTarget) - is
         * satisfied by construction rather than by coincidence.
         *
         * The lever (right corner of terrain, same positioning as SIDE_SCROLL_LEVEL_LAYOUT's own
         * lever) is wired to leverCrate purely by matching [Lever.targetMechanismId] against
         * [MovingPlatformDef.id] - the same id-matching GameWorld already uses for
         * Lever -> HookCrate, just extended (see GameWorld.update's interactInput handling) to
         * also call MovingPlatform.activate() on a matching platform. leverCrate itself is a
         * MovingPlatformDef with startsInactive = true and activationDelaySeconds = 0.0: it sits
         * parked right next to terrain (a trivial 10-unit hop, tightened from an original 20 on
         * request - "start a little more closer to the platform") and starts easing the SAME
         * instant the lever fires, no wind-up window - so the player has to already be moving
         * toward the gap, not stand at the lever and react afterward. It's also oneShot = true:
         * one cosine excursion out to maxX and back to rest, then it re-arms itself - see
         * MovingPlatformDef.oneShot and GameWorld.update's per-tick lever-reset check. A missed
         * attempt costs nothing but time: fall to the ground below (never fatal), climb back up via
         * crate1 -> terrain, and the lever is back in its original position and repressable the
         * instant leverCrate finishes easing back to rest - not spent for the rest of the run.
         *
         * periodSeconds = 5.5 (down from an earlier 8.0, on request - "make it more faster"): a
         * cosine ease starts at zero velocity, but with zero activation delay the walk from the
         * lever (30 units) plus a normal jump's own flight time (~0.6s) is real, unavoidable
         * reaction time before the player can even attempt to land on the departing crate, and the
         * period governs both how forgiving that landing is AND how quickly the ride afterward
         * reaches hook1 - the two aren't independent knobs. Periods below ~5.0 miss the boarding
         * jump outright (the crate has already outrun a normal jump's own flight time); the
         * original 3.6 was much too fast for the same reason. Periods that board fine can still
         * fail later if the player walks off the crate's own leading edge before its excursion has
         * closed enough distance to bring hook1 into swing range - not a smooth trade-off against
         * period (5.0 and 6.0-7.0 both missed in a directed sweep against the real geometry below
         * while 5.5 landed cleanly), so this was found by simulating the actual GameWorld/Player
         * loop across a grid of (gap, period) pairs, not arc math or an assumed monotonic trend.
         *
         * The push here is genuinely timed, not just aimed: leverCrate's own maximum reach
         * (maxX + width = 1214) sits a deliberate 86 units short of hook1's grip (1300) - the
         * middle of [Player.swingMinReach]..[swingMaxReach] (75..97) - so riding all the way to
         * full extension brings the player, standing at the crate's own right edge, right to the
         * middle of swing range and no further. The crate goes "just enough" for the swing to
         * become reachable from its own leading edge; it does not hand the player the hook for
         * free. (A first pass got the arithmetic backwards: a 340-unit excursion actually left
         * only 8 units of gap, not the 88 its own comment claimed, so the crate sailed almost all
         * the way to the hook - reported directly as "the crate is going too much right." Fixed by
         * shrinking the excursion itself to 232, and separately moving the hook 30 units left to
         * 1300, without re-extending the crate's own reach to chase it.) See
         * GameplayModelTest.testLevel6LeverCrateSwingCrossesToLandingCrate for the exact autopilot
         * timing that clears it - re-derived by simulation after every change here, not carried
         * over from an earlier tuning pass.
         *
         * hook1's grip sits at the usual terrainTopY - 112 height (exactly SIDE_SCROLL_LEVEL_LAYOUT's
         * own constant for all three of its hooks) - reused rather than re-derived, since it's
         * already proven to sit inside [Player.swingMinGripHeight]..[swingMaxGripHeight] for this
         * same terrainTopY tier. landingCrate1 (the second long hanging crate) sits a real 70 units
         * past hook1's grip - wider than the swing's own reach window, so it reads as genuinely
         * farther from the hook than leverCrate's own launch point, not just a hair past it - while
         * hook1's grip + [Player.swingLandAhead] (109, the swing's fixed landing distance) still
         * lands 39 units inside its span, comfortably clear of either edge.
         *
         * Both hanging crates render with the long chainedcrate.png crop (hangingCrateVariant1 /
         * MovingPlatformDef.isVariant1 = true) so they read as the same object, one static and one
         * moving - not two different props. The rescue barrel that used to sit at terrain's right
         * face was removed on request; crate1 -> terrain remains the way back up after a fall, so
         * nothing else needed to change to keep that retry path open.
         */
        val LEVEL_6_LAYOUT = run {
            val groundY = 440.0
            // Runs past the exit by the booth (~117) + exit fence (~312) art's own width, the same
            // margin LEVEL_3_LAYOUT keeps - see exitX at the end of section 4.
            // Past the extraction building's own far edge (it ends near 5220), not just past the
            // exit trigger - the camera clamps to this and the ground is drawn to it.
            val worldWidth = 5300.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            val terrainTopY = groundY - 144.0 // this game's usual "3x crate height" high tier

            // 1. Ground -> crate1, a plain jump (same 68x48 dims/positioning convention as every
            // other level's first step-up crate).
            val crateWidth = 68.0
            val crateHeight = 48.0
            val crate1 = Rect(x = 420.0, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // 2. The climb: crate1's top -> the elevated platform. Rise is 96 (392 -> 296),
            // inside Player's climbable window (51.2..115.0) - identical climb to LEVEL_3_LAYOUT's
            // crate -> table.
            val terrain = Rect(x = crate1.right, y = terrainTopY, width = 300.0, height = groundY - terrainTopY)

            // 3. Lever at the platform's right corner (same positioning as SIDE_SCROLL_LEVEL_LAYOUT's).
            val leverWidth = 22.0
            val leverHeight = 12.0
            val lever = Lever(
                id = "lever_1",
                x = terrain.right - 30.0,
                y = terrainTopY - leverHeight,
                width = leverWidth,
                height = leverHeight,
                targetMechanismId = "lvl6_swing_crate"
            )

            // 4. The leftmost hanging crate: parked 20 units from terrain (a trivial hop). It's a
            // ONE-SHOT mechanism (MovingPlatformDef.oneShot): the instant the lever fires it eases
            // 232 units right, eases back to leverCrateRestX, then freezes there for good - no
            // second lap. No activation delay either (activationDelaySeconds = 0.0) - it starts
            // moving the same instant the lever is pulled, so the player has to already be moving
            // toward the gap, not stand at the lever and react afterward.
            val hangingCrateWidth = 174.0
            val hangingCrateHeight = 38.0
            val leverCrateRestX = terrain.right + 10.0
            val leverCrate = MovingPlatformDef(
                id = "lvl6_swing_crate",
                initialX = leverCrateRestX,
                y = terrainTopY,
                width = hangingCrateWidth,
                height = hangingCrateHeight,
                minX = leverCrateRestX,
                maxX = leverCrateRestX + 232.0,
                periodSeconds = 5.5,
                isVariant1 = true,
                startsInactive = true,
                activationDelaySeconds = 0.0,
                oneShot = true
            )

            // 5. The hook leverCrate carries the player toward - grip height matches
            // SIDE_SCROLL_LEVEL_LAYOUT's own hooks exactly (terrainTopY - 112). Sits a real 76-unit
            // gap past leverCrate's own maximum reach (maxX + width = 1214) - just above the floor
            // of [Player.swingMinReach]..[swingMaxReach] (75..97) - so riding all the way to full
            // extension still brings the player, standing at the crate's own right edge, into swing
            // range without handing them the hook for free. (An earlier pass had this backwards - a
            // 340-unit excursion actually left only 8 units between the crate's max reach and the
            // hook, not the 88 its own comment claimed - reported directly as "the crate is going
            // too much right." Fixed by shrinking the excursion itself, not by pushing the hook
            // further away to compensate. The hook itself was later pulled 10 units left on request
            // - "move the hook little bit to left" - from 1300 to 1290, still comfortably inside the
            // reach window rather than right at its old midpoint.)
            val hookWidth = 16.0
            val hookHeight = hookWidth * (2136.0 / 154.0) // hook.png's own cropped aspect ratio
            val hook1GripX = 1290.0
            val hook1GripY = terrainTopY - 112.0
            val hook1 = Rect(
                x = hook1GripX - hookWidth * Player.HOOK_GRIP_X_FRACTION,
                y = hook1GripY - hookHeight * Player.HOOK_GRIP_Y_FRACTION,
                width = hookWidth,
                height = hookHeight
            )

            // 6. Landing crate: hook1's grip + Player.swingLandAhead (109) lands at x = 1399, a
            // healthy 29 units inside this crate's own span (not hugging either edge). Its own left
            // edge sits 80 units past hook1's grip - a real gap wider than the swing's own reach
            // window - so this crate reads as genuinely farther from the hook than leverCrate's own
            // launch point.
            val landingCrate1 = Rect(x = 1370.0, y = terrainTopY, width = hangingCrateWidth, height = hangingCrateHeight)

            // 7. The far platform, a plain same-height jump past landingCrate1. Kept well under
            // LEVEL_2_LAYOUT's ~70-unit hanging-crate gaps deliberately - those assume a jump
            // timed right at the lip; a player who pushes off a few units early (this game's own
            // jump-buffer window allows it) needs the same ~84-unit arc to still clear a wider
            // gap, so this one stays conservative instead of spending that whole budget.
            val farTerrain = Rect(x = landingCrate1.right + 48.0, y = terrainTopY, width = 300.0, height = groundY - terrainTopY)

            // 8. Second section: past farTerrain, a real pit down to the ground (no ledge at
            // farTerrain's own height on the far side, so there's no jump-across shortcut - the gap
            // is well past the ~72-84 unit budget a full jump arc covers, forcing an actual fall),
            // then a single climbable block (rise 96, same climb window as crate1 -> terrain above).
            // The barrel pyramid, lever_2/blocker crate, and the hook+rope crate that used to sit up
            // here were all removed on request - this is now a plain fall-and-climb, nothing gating
            // it.
            // Widened from an original 300 once the overwatch crate below was added: with only 63
            // units of clearance on each side at that width, a running jump off farTerrain's own
            // edge could reach clean across the gap and land ON the overwatch crate instead of
            // falling into the pit - confirmed directly (the player got stuck oscillating right at
            // farTerrain's edge in a walkthrough test, repeatedly launching back onto it). 500 keeps
            // a real 163-unit gap on both sides of the overwatch crate - comfortably past this
            // game's own ~84-unit running-jump budget - so the fall is unavoidable from either end.
            val pitWidth = 500.0
            val tallBlockTopY = groundY - 96.0
            val tallBlock = Rect(x = farTerrain.right + pitWidth, y = tallBlockTopY, width = 300.0, height = groundY - tallBlockTopY)

            // A crate in the pit's left corner, flush against farTerrain's own right face (same
            // 68x48 footprint/positioning convention as crate1) - the way back up for a player who
            // falls and wants to retry rather than push on: rise from its own top (392) to
            // farTerrain's top (296) is 96, the same climb window used everywhere else in this file.
            val pitCrate = Rect(x = farTerrain.right + 10.0, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // A hanging long crate over the pit with a guard patrolling its own deck, looking down
            // at the crossing below - the same overwatch pattern as LEVEL_3_LAYOUT's longCrate1/
            // overwatchGuard1 (same 174x38 shape, same y=300 elevation - this level shares that
            // level's groundY=440, so the numbers carry over directly, including the ~102-unit
            // ground clearance), just a single crate/guard here instead of a matched pair. Centered
            // over the pit's own width, safely out of jump range of either ledge (see pitWidth).
            val pitLongCrateWidth = 174.0
            val pitLongCrateHeight = 38.0
            val pitLongCrateElevation = 300.0
            val pitLongCrate = Rect(
                x = farTerrain.right + (pitWidth - pitLongCrateWidth) / 2.0,
                y = pitLongCrateElevation,
                width = pitLongCrateWidth,
                height = pitLongCrateHeight
            )
            val pitGuardWidth = 30.0
            val pitGuardMargin = 10.0 // keeps him visibly on the crate, never overhanging its edge
            val pitGuard = GuardSpawn(
                startX = pitLongCrate.x + pitGuardMargin,
                surfaceY = pitLongCrateElevation,
                patrolMinX = pitLongCrate.x + pitGuardMargin,
                patrolMaxX = pitLongCrate.right - pitGuardMargin - pitGuardWidth,
                speed = 35.0,
                facing = 1.0,
                visionRange = 220.0,
                width = pitGuardWidth,
                height = 96.0,
                visionTilt = 25.0 * PI / 180.0,
                patrolPauseDuration = 3.0
            )

            // 9. Third section: past tallBlock's own drop, two ground barrels (standard 32x48
            // each, same as every other barrel in this game) sitting side by side and touching,
            // then the lever platform. See this file's own earlier note (Section 2's barrel
            // pyramid, now removed) on why any walkthrough test crossing them on foot needs an
            // ANTICIPATED jump rather than a reactive one: Player.updateStep's horizontal
            // collision pass pins the whole body at the wall, regardless of the barrel's own
            // height, whenever it's already touching, flush, with no run-up.
            //
            // This whole end tier sits ONE barrel high (groundY - 48), not the two it used to.
            // It dropped 48 units when the crane moved left (see below): the crane's boom has to
            // pass over the lever platform to reach back across the gap, and a standing player
            // (96) only fits under the boom if the surface they're standing on is at or below the
            // crane's own base. At the old groundY - 96 tier the boom would have cut straight
            // through the player's chest on this platform - it isn't a stylistic choice. The
            // second barrel, which used to be stacked on the first to make one 96-tall climbable
            // column up to that old tier, is now beside it instead: at this height the step up is
            // a plain jump, so nothing here is a climb any more.
            val endBarrelWidth = 32.0
            val endBarrelHeight = 48.0
            val endBarrel1 = Rect(x = tallBlock.right + 150.0, y = groundY - endBarrelHeight, width = endBarrelWidth, height = endBarrelHeight)
            val endBarrel2 = Rect(x = endBarrel1.right, y = groundY - endBarrelHeight, width = endBarrelWidth, height = endBarrelHeight)

            // The lever platform, flush with the barrels' own tops - one continuous walking
            // surface from the first barrel to the machine. Kept deliberately short (70) so the
            // crane's tracked base lands just past the lever, on request ("the vehicle part of
            // the crane should be just right of the lever"); LevelLayout.plainPlatforms keeps a
            // 70x48 box from tripping GameplayScene.kt's crate-shaped size heuristic.
            val endPlatformTopY = groundY - endBarrelHeight
            val endTerrain = Rect(x = endBarrel2.right, y = endPlatformTopY, width = 70.0, height = groundY - endPlatformTopY)

            // Lever near the platform's LEFT corner this time (lever/lever2 both sat at their own
            // platform's right corner) - right where the player arrives if they come along the
            // ground, and immediately left of the crane's own tracked base. It powers section 4's
            // gantry, on request ("the lever for the hanging crate should be the one left of the
            // crane"), which is what this ground dead-end is for: the walk along the floor stops
            // at the machine's tracks, and this is what the trip was worth. The crate it starts is
            // past the cab and out of sight from here - the sweep runs continuously (see
            // gateCrate) precisely so the crossing back over the boom can take as long as it takes.
            val lever3 = Lever(
                id = "lever_3",
                x = endTerrain.left + 30.0,
                y = endPlatformTopY - leverHeight,
                width = leverWidth,
                height = leverHeight,
                targetMechanismId = "lvl6_gantry_crate"
            )

            // Background crane - see CraneDef. Moved here on request: "take the crane to left, so
            // that the player can climb onto it from the otherside of the gap. the vehicle part of
            // the crane should be just right of the lever. if it is too high to be climbable, make
            // the height of the platform after the lever shorter and put the crane there."
            //
            // Placed by its MACHINE, not by its boom tip (CraneDef.boomLength): the tracked base's
            // own crop starts exactly at endTerrain's right edge, so the vehicle stands a short
            // step past the lever with the boom reaching back over the lever, the barrels, the
            // ground gap and the last stretch of tallBlock.
            //
            // The boom's own height is the load-bearing number here, and it is pinned exactly, not
            // chosen: "for the climbing animation to work, this long beam should be his head
            // height" - the walkable top of the boom lands on the crown of a player standing on
            // tallBlock. That is also what makes the mantle onto it a 96-unit rise, this game's
            // own canonical climb height (crate -> terrain, stepCrate -> cameraBeam, the barrel
            // columns: all 96), comfortably inside Player.climbMinHeight..climbMaxHeight
            // (51.2..115) rather than the chest-height 75 an earlier pass left it at, which read
            // wrong the moment the climb animation played against it.
            //
            // With the boom's top fixed and the platform staying flush with the barrels at
            // groundY - 48, the crane's own HEIGHT is what falls out (CraneDef.heightForBoomTop) -
            // it is a derived value, so moving either the tier or the head-height target re-sizes
            // the machine automatically instead of silently breaking the climb. The headroom under
            // the boom on this tier comes along for free: a crane's base always sits a fixed
            // 0.8053 of its own height below the boom's underside, which is 118 here, well past
            // the 96 a standing player needs.
            val craneTileCount = 10
            val cranePlatformTopY = endPlatformTopY
            val craneBoomTopY = tallBlockTopY - 96.0 // the standing player's own head, on tallBlock
            val craneHeight = CraneDef.heightForBoomTop(baseY = cranePlatformTopY, boomTopY = craneBoomTopY)
            val cranePreview = CraneDef(x = 0.0, y = 0.0, height = craneHeight, tileCount = craneTileCount)
            val crane = CraneDef(
                x = endTerrain.right - cranePreview.boomLength,
                y = cranePlatformTopY - craneHeight,
                height = craneHeight,
                tileCount = craneTileCount
            )

            // 12. Fourth section, past the machine: the gantry-gated climb. "after the crane, add
            // a platform that is climbable but there is a hanging crate very close to the surface
            // level which makes it unclimbable. pressing that lever makes it move left and right
            // so the player has to time when the crate is not there to climb up." It is thrown by
            // lever3 - "the lever for the hanging crate should be the one left of the crane" - so
            // the ground dead-end that lever sits on is now the section's own trigger, pulled
            // before the boom crossing rather than after it.
            //
            // The whole section is placed from the crane's own cab outwards, as far left as the
            // sweep can go ("take the platform and the hanging crate more to the left"), because
            // ONE geometric limit sets everything: the crate hangs 30 above the block's surface,
            // and the route down from the boom walks along the machine's top and across the cab
            // roof. A load sweeping over that roof would sweep through the player on it (and over
            // bodyBounds it would pass through the machine itself), so the crate's left end stops
            // just past the cab, and the block sits a crate-sweep further right again.
            val gateCrateWidth = 174.0
            val gateCrateHeight = 38.0

            // What makes this a gate rather than an obstacle is the CLEARANCE, not the crate:
            // Player.findClimbTarget only drops a candidate whose landing has room for neither a
            // standing body (96) nor a crouched one (Player.crouchHeight, 56) - with anything over
            // 56 the climb still goes through and simply arrives crouched, the way section 3's own
            // boom does. 30 refuses it outright, and reads as "very close to the surface level"
            // from down on the platform.
            val gateCrateClearance = 30.0

            // 150 of travel, ending 8 clear of the cab's right edge. The sweep has to be big
            // enough to read as movement from a distance and to leave the landing clear for
            // longer than a climb takes: the crate must travel 68 before its right edge passes the
            // landing's left, which with cosine easing leaves it clear for ~53% of every cycle -
            // a ~3.7s window against a ~2s climb.
            val gateCrateSweep = 150.0
            val gateCrateMinX = crane.houseBounds.right + 8.0
            val gateCrateRestX = gateCrateMinX + gateCrateSweep

            // The block itself: this level's standard tier (the same 300x144 as terrain and
            // farTerrain, top at terrainTopY), stood on the ground so it is 144 from the floor -
            // past Player.climbMaxHeight, so there is no way around it - and exactly 96 from
            // cranePlatform's surface, this game's canonical climb. The 100 offset puts the parked
            // crate over the landing (Player.climbLandingX, 6 units in from the edge the player
            // comes over) with its own left end hanging clear of the block, which is also what
            // leaves room for the sweep.
            val gateBlockLeft = gateCrateRestX + 100.0
            val gateBlock = Rect(
                x = gateBlockLeft,
                y = terrainTopY,
                width = 460.0,
                height = groundY - terrainTopY
            )

            // Thrown by lever3, this one runs CONTINUOUSLY (no oneShot): one pull powers the
            // gantry for good and the crate sweeps left and back forever, which is what makes the
            // climb a timing problem rather than a one-attempt one - and it has to survive the
            // whole trip back over the boom, since the lever is on the far side of the machine.
            // It sweeps LEFT for the same reason it stops where it does: everything to the RIGHT
            // of the landing then stays permanently clear, so whoever just climbed can walk out
            // from under the gantry instead of being swept off the block.
            val gateCrate = MovingPlatformDef(
                id = "lvl6_gantry_crate",
                initialX = gateCrateRestX,
                y = gateBlock.top - gateCrateClearance - gateCrateHeight,
                width = gateCrateWidth,
                height = gateCrateHeight,
                minX = gateCrateMinX,
                maxX = gateCrateRestX,
                periodSeconds = 7.0,
                // Half a period, so the cosine starts at t = 1 - i.e. at maxX, the parked blocking
                // position - and eases away from it. Without it the crate would teleport to the
                // far end of its own sweep the instant the lever was thrown.
                phaseOffsetSeconds = 3.5,
                // Level 2's own hanging containers, on request ("use the hanging crates from
                // level 2"): same 174x38 box and the same long chainedcrate.png rigging as
                // LEVEL_2_LAYOUT's hangingCrate1, which is also what this level's landingCrate1
                // and pitLongCrate already use.
                isVariant1 = true,
                startsInactive = true,
                // "when trying to climb if he touches the bottom side of the crate it should be
                // mission failed" - a mistimed climb puts the head into the underside of a
                // swinging load, and that is fatal rather than merely wedged. See
                // MovingPlatformDef.crushesOnContact; it only bites from below.
                crushesOnContact = true
            )

            // The platform the machine stands on, flush against endTerrain's own right face and at
            // exactly its height - one flat tier, no step. It carries the whole tracked base ("the
            // crane can't be floating", from an earlier request) and then runs on to section 4's
            // block, so its far edge is that block's own left face: the approach where the player
            // lands off the cab, waits out the gantry crate's sweep and climbs.
            val cranePlatform = Rect(
                x = endTerrain.right,
                y = cranePlatformTopY,
                width = gateBlockLeft - endTerrain.right,
                height = groundY - cranePlatformTopY
            )


            // 13. Fifth section: the hanging platform, the switch and the laser curtain. "after
            // that section, continue that platform and add a crate at the end. after that add a
            // hanging platform from level 3. there should be a lever on top and a guard after that
            // moving left and right. the lever turns off 3 lasers that are there from the hanging
            // platform to the ground. the bottom is the only path out."
            //
            // gateBlock carries it: the block runs on past the gantry climb (460 wide now, not
            // 300), a step crate stands flush with its far lip ("move the crate to the edge of the
            // platform"), and the platform hangs past the gap at the CRATE's own top rather than
            // the block's ("lift the floating platform to the level of the top of the crate on the
            // edge of the platform before it"), drawn with LEVEL_3_LAYOUT's table.png slab.
            //
            // That makes the crate the whole crossing: the block is walked to its end, the crate is
            // jumped (48 is inside maxJumpHeight's 51.2), and the jump across leaves from the
            // crate's lip and lands level. Nothing holds the platform up and nothing is drawn
            // holding it up either - "remove the chain holding the floating platform".
            val gateCrateStepWidth = 68.0
            val gateCrateStepHeight = 48.0
            val endCrate = Rect(
                x = gateBlock.right - gateCrateStepWidth,
                y = gateBlock.top - gateCrateStepHeight,
                width = gateCrateStepWidth,
                height = gateCrateStepHeight
            )

            // The gap between the crate's edge and the platform does two jobs at once, and 65 is
            // how wide it can be while still doing both ("increase gap between the platform and
            // floating thing"; it was 45, then 55).
            //
            // The ceiling is physics: jumpSpeed 320 against gravity 1000 is 0.64s of flight and
            // moveSpeed 132 carries the body 84.5 units in that time, of which the landing spends
            // about 6.5 getting a foot onto the far lip - so ~78 is where the crossing becomes
            // impossible, and every unit below that is margin for pressing jump early. 65 leaves
            // about 13 units of it (a tenth of a second of walking), which is the part that
            // actually matters on a touchscreen; 70 would leave 8 and 78 none at all. The earlier
            // note here claimed a ceiling of 61 - that was wrong, taken from a stale "+18 to land"
            // figure rather than measured.
            //
            // The same gap is also wider than the player's own 36, so simply WALKING off the lip
            // drops them 192 into the corridor instead ("he should be able to drop down to reach
            // the place with lasers"). Jump across for the switch, walk off for the way out.
            //
            // testLevel6HangingPlatformIsLevelWithTheCrateAndDropsIntoTheCorridor measures that
            // margin (how many units early the jump may be taken and still land) and asserts it,
            // so widening this fails loudly rather than quietly.
            //
            // Nothing stands in the chute. A prop there would have to be climbable from the floor
            // AND leave a body-width lane beside it, and it cannot be both at 45 wide - worse, the
            // platform's own near face hangs at 296..326, so anything 48 tall in the chute pins a
            // standing body on top of it with no way down. The drop is therefore one-way, which is
            // what the checkpoint on the platform is for: a player who goes down before throwing
            // the switch walks into the curtain, and respawns up top to try again.
            val plankGap = 65.0
            val plankDepth = 30.0
            // 370 rather than the original 420 because the curtain hangs off the far end and moves
            // with it: a shorter platform is how the beams move left ("move the lasers to the
            // left") without the last one leaving the tip, which it cannot do - see below.
            val plank = Rect(x = gateBlock.right + plankGap, y = endCrate.top, width = 370.0, height = plankDepth)

            // The switch, at the end of the platform the player arrives on, and the guard pacing
            // the rest of it - "there should be a lever on top and a guard after that moving left
            // and right". Cross, throw it, and get back off before he walks into you.
            // "take the lever little more to left" - 24 from the near end rather than 46, so it is
            // under the body almost as soon as the jump lands and the exposed stretch on the
            // platform is that much shorter.
            val lever4 = Lever(
                id = "lever_4",
                x = plank.left + 24.0,
                y = plank.top - leverHeight,
                width = leverWidth,
                height = leverHeight,
                targetMechanismId = "lvl6_exit_lasers"
            )

            // "increase the length of the path of guard from either side" - the beat runs 170
            // units now instead of 70, opened up at both ends (150 -> 110 from the near end, 150 ->
            // 90 from the far one). It still stops short of the lever at the near end, because the
            // whole section is the player timing a landing against where he is looking; a guard
            // standing on the switch would make it a waiting game instead.
            val plankGuardWidth = 30.0
            val plankGuard = GuardSpawn(
                startX = plank.left + 110.0,
                surfaceY = plank.top,
                patrolMinX = plank.left + 110.0,
                patrolMaxX = plank.right - 60.0 - plankGuardWidth,
                speed = 35.0,
                facing = 1.0,
                visionRange = 220.0,
                width = plankGuardWidth,
                height = 96.0,
                visionTilt = 20.0 * PI / 180.0,
                patrolPauseDuration = 2.5
            )

            // Three beams hung off the platform's underside down to the floor, always on until
            // lever4 cuts them (LaserDef.mechanismId - one switch, three beams), and standing close
            // together on request ("put the 3 lasers close together"): 45 apart, so they read as
            // one curtain rather than three separate hazards. They are what makes the bottom the
            // only way out AND what makes it a locked door - the corridor under the platform is the
            // only route to the extraction point, and it runs through all three.
            //
            // The curtain sits at the platform's FAR END, with the last beam hanging off the tip
            // itself. Anywhere else and the platform is its own bypass: walk to the far tip, step
            // off, and land past every beam with the switch never touched. At the tip, stepping off
            // drops the player straight through it. So "move the lasers to the left" is done by
            // shortening the platform (above) - the beams travel with the tip they hang from.
            val exitLaserSpacing = 45.0
            val exitLaserTopY = plank.bottom
            val exitLasers = listOf(
                LaserDef(
                    id = "lvl6_exit_laser_1",
                    topX = plank.right - 2.0 * exitLaserSpacing,
                    topY = exitLaserTopY,
                    bottomX = plank.right - 2.0 * exitLaserSpacing,
                    bottomY = groundY,
                    beamThickness = 6.0,
                    isAlwaysActive = true,
                    emitterScale = 0.55,
                    mechanismId = "lvl6_exit_lasers"
                ),
                LaserDef(
                    id = "lvl6_exit_laser_2",
                    topX = plank.right - exitLaserSpacing,
                    topY = exitLaserTopY,
                    bottomX = plank.right - exitLaserSpacing,
                    bottomY = groundY,
                    beamThickness = 6.0,
                    isAlwaysActive = true,
                    emitterScale = 0.55,
                    mechanismId = "lvl6_exit_lasers"
                ),
                LaserDef(
                    id = "lvl6_exit_laser_3",
                    topX = plank.right,
                    topY = exitLaserTopY,
                    bottomX = plank.right,
                    bottomY = groundY,
                    beamThickness = 6.0,
                    isAlwaysActive = true,
                    emitterScale = 0.55,
                    mechanismId = "lvl6_exit_lasers"
                )
            )

            // The level ends past the gantry, not before it - the reverse of the old arrangement,
            // and the direct consequence of the vehicle now standing right beside the lever. The
            // machine's front face (CraneDef.bodyBounds, from the boom's own top down to the
            // tracks) is 123 tall: past Player.climbMaxHeight, and with the boom directly above it
            // the headroom check in Player.findClimbTarget refuses it anyway, so the ground
            // approach genuinely stops here. The way through is over the top: climb onto the boom
            // from tallBlock on the far side of the gap, walk its whole length, step down onto the
            // house roof (CraneDef.houseBounds, 44.8 below the boom) and drop from there onto this
            // platform, 78 lower again, and on along it to the gantry. The house roof is climbable
            // back up from this side too (78.3, inside the climb window, and resting on the
            // platform rather than floating), so arriving here is never one-way.
            //
            // Extraction is on the ground past the laser curtain, the same way every other
            // level's is (the booth and its fence are drawn standing on exitZone.bottom). The
            // route in is the corridor UNDER the plank - "the bottom is the only path out" - so
            // the last thing the level asks is the drop off the block's far side and the walk
            // through three cut beams.
            val exitX = plank.right + 45.0

            // The building the level ends at (exitlvl7.png), sized and placed here rather than in
            // GameplayScene because where it stands is level geometry: "increase size of the
            // building at end and make sure it is on the floor and the middle part should be
            // connected to the balcony".
            //
            // 401 tall (drawn 200, then 335, then this) standing on groundY, with its left edge
            // tucked 8 units under the platform's far tip so the two meet with no seam. The height
            // is what does the connecting: the art's own balcony deck starts 0.5215 of the way
            // down from its roof, so 440 - 0.4785 * 401 puts that deck's top surface at y=248 -
            // exactly the platform's own top - and the platform reads as a walkway running off the
            // building's balcony rather than a slab hanging in the air. Any other height either
            // steps the two apart or, past ~400, only buys roof that the camera's 356-unit window
            // cannot show while the player is down on the corridor floor.
            //
            // Purely decorative, like every other exit structure: exitZone is still the trigger, 53
            // units inside the silhouette rather than flush with its left edge, so here the player
            // genuinely walks INTO the building instead of touching its corner.
            val exitBuildingHeight = 401.0
            // 1501x780 is exitlvl7.png's authored size - the source art cropped to its ALPHA
            // bounds; the file on disk is the POT resample of that, so the aspect is written out
            // rather than read off the bitmap. (Cropping it to PIL's default getbbox instead left
            // 27 rows of invisible padding under the building and it hung 12 units off the floor.)
            val exitBuilding = Rect(
                x = plank.right - 8.0,
                y = groundY - exitBuildingHeight,
                width = exitBuildingHeight * (1501.0 / 780.0),
                height = exitBuildingHeight
            )

            val checkpoints = listOf(
                Checkpoint(
                    id = "lvl6_cp1_terrain",
                    x = terrain.left + 62.0,
                    y = terrainTopY - 96.0,
                    triggerZone = Rect(terrain.left, terrainTopY - 120.0, terrain.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl6_cp2_farTerrain",
                    x = farTerrain.left + 88.0,
                    y = terrainTopY - 96.0,
                    triggerZone = Rect(farTerrain.left, terrainTopY - 120.0, farTerrain.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl6_cp3_tallBlock",
                    x = tallBlock.left + 62.0,
                    y = tallBlockTopY - 96.0,
                    triggerZone = Rect(tallBlock.left, tallBlockTopY - 120.0, tallBlock.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl6_cp4_endTerrain",
                    x = endTerrain.left + 16.0,
                    y = endPlatformTopY - 96.0,
                    triggerZone = Rect(endTerrain.left, endPlatformTopY - 120.0, endTerrain.width, 140.0)
                ),
                // Past the machine, not in front of it: cranePlatform's own left end is under the
                // crane's tracked base now, and a respawn point inside a solid box would wedge the
                // player. Both the zone and the respawn spot sit in the clear stretch between the
                // house's right edge and the extraction point.
                Checkpoint(
                    id = "lvl6_cp7_plank",
                    x = plank.left + 20.0,
                    y = plank.top - 96.0,
                    triggerZone = Rect(plank.left, plank.top - 120.0, plank.width, 130.0)
                ),
                Checkpoint(
                    id = "lvl6_cp6_gateBlock",
                    x = gateBlock.left + 90.0,
                    y = gateBlock.top - 96.0,
                    triggerZone = Rect(gateBlock.left, gateBlock.top - 120.0, gateBlock.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl6_cp5_cranePlatform",
                    x = crane.houseBounds.right + 20.0,
                    y = cranePlatformTopY - 96.0,
                    triggerZone = Rect(
                        crane.houseBounds.right,
                        cranePlatformTopY - 120.0,
                        cranePlatform.right - crane.houseBounds.right,
                        140.0
                    )
                )
            )

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = exitX, y = groundY - 100.0, width = 44.0, height = 100.0),
                exitStructure = exitBuilding,
                // The machine is crossed one way over the top - in off the boom from tallBlock,
                // east along it, down onto the rear deck, down onto the platform - and the way back
                // up is closed: 52.4 from deck to top is a legal mantle and NOT a legal jump (~48.5
                // is what a jump really clears), so denying the mantle is the whole mechanism.
                unclimbableBoxes = listOf(crane.bodyBounds),
                platforms = listOf(ground),
                boxes = listOf(
                    crate1, terrain, landingCrate1, farTerrain, pitCrate, pitLongCrate, tallBlock,
                    endBarrel1, endBarrel2, endTerrain, cranePlatform, gateBlock, endCrate, plank
                ) + crane.collisionBoxes,
                guards = listOf(pitGuard, plankGuard),
                hangingCrateVariant1 = listOf(landingCrate1, pitLongCrate),
                barrels = listOf(endBarrel1, endBarrel2),
                // The boom is what the player climbs onto from tallBlock, on the far side of the
                // gap - and Player.findClimbTarget's own floating-ledge check compares a
                // candidate's bottom against the climbing player's CURRENT feet, with no notion of
                // "something else is solid underneath". A hanging boom fails that by construction
                // (its underside hangs ~70 above tallBlock's surface), so it needs the same sanctioned
                // exemption LEVEL_3_LAYOUT's tablePlank/cameraBeam use. It isn't visually floating:
                // the crane's own machine holds it up. Nothing else here needs it - the house roof
                // and the machine's front both rest on cranePlatform, which IS the player's stance
                // when approaching them, and the barrels now sit on the ground side by side rather
                // than stacked.
                floatingClimbTargets = listOf(crane.boomBounds, plank),
                tables = listOf(plank),
                tableParts = listOf(plank),
                // Hangs from its own rigging instead of standing on a leg - see the section note.
                lasers = exitLasers,
                cranes = listOf(crane),
                // endTerrain is 70x48 and cranePlatform is a long low slab - both land inside
                // GameplayScene.kt's crate-shaped size heuristics by coincidence of their
                // dimensions, so both are forced back to the plain structural-block look.
                plainPlatforms = listOf(endTerrain, cranePlatform),
                swingHooks = listOf(hook1),
                levers = listOf(lever, lever3, lever4),
                movingPlatforms = listOf(leverCrate, gateCrate),
                manualCheckpoints = checkpoints
            )
        }

        val DEFAULT_LEVEL_6 = LevelData(
            id = "level_6",
            name = "06: Stolen Manifest",
            timeTargetSeconds = 45.0f,
            description = "Break into the office and recover records revealing Container 17’s location.",
            objectiveHint = "Find Container 17",
            layout = LEVEL_6_LAYOUT
        )

        /**
         * Where level 7's white distance stencils stand, and therefore how long level 7 is.
         *
         * Level 4 measures itself purely with wall decals - nothing anywhere converts world units
         * to metres at runtime - at 1500 units per 30m step, i.e. 50 units to the metre. Level 7
         * counts down from 120m on that identical spacing, so these five constants and
         * [LEVEL_7_LAYOUT]'s exit have to move together: the last stencil is the exit's own centre.
         *
         * Read by GameplayScene's marker pass (which loads `wall7_<label>.png`, the white recolour
         * of level 4's ochre stencils - see tools/art/prep_wall_markers.py) and asserted against
         * the layout in GameplayModelTest.
         */
        const val LEVEL_7_MARKER_FIRST_X: Double = 190.0

        /** 1500 units = 30m, the same step level 4's stencils use. */
        const val LEVEL_7_MARKER_SPACING: Double = 1500.0

        /** Counting down, so the last one lands on the exit. */
        val LEVEL_7_MARKER_LABELS: List<String> = listOf("120m", "90m", "60m", "30m", "0m")

        /**
         * Level 7: the 120-metre service tunnel ("07: Service Tunnel").
         *
         * ## The metre scale
         *
         * 50 world units = 1 metre - the scale level 4 is measured in, and the only place either
         * level states a distance. Nothing computes metres at runtime: level 4's wall stencils run
         * 150m -> 0m across 7500 units at 1500 units per 30m step, and that spacing is the whole
         * definition. Level 7 reuses it unchanged at 120m, so the "120m" stencil stands at
         * x = 190 and "0m" at x = 6190, with one every 1500 units between. The layout is built to
         * the markers rather than the other way round, so moving the exit means moving
         * [LEVEL_7_MARKER_FIRST_X] with it (GameplayScene's level 7 marker pass reads these same
         * constants).
         *
         * "0m" stops 70 units short of the exit trigger rather than standing on it, because the
         * extraction booth (entrance.png) is drawn from `exitZone.x` rightwards and swallowed the
         * stencil's right half when the two were centred together. The last stencil now sits on
         * the last clear panel before the door, which is where such a marking would be painted
         * anyway. 70 is the clearance the 0m plate needs: it draws 49 units wide.
         *
         * ## The difficulty curve
         *
         * Six 20-metre beats. Each introduces one thing and then folds it into what came before,
         * so no 20m stretch is empty and nothing appears cold inside a combination:
         *
         *   1.   0- 20m  Intake       headwind alone (the spam-tap tutorial)
         *   2.  20- 40m  Purge line   steam alone, widely spaced, longest windows
         *   3.  40- 60m  Patrol deck  the first drone, alone, with steam either side of it
         *   4.  60- 80m  Compressor   headwind, then a two-jet gate, then a faster drone
         *   5.  80-100m  Duct crawl   a jet standing inside a wind zone
         *   6. 100-120m  Manifold     gust -> tightest jet pair -> a drone on the door
         *
         * The steam knobs do the fine tightening. [SteamPipe] clamps whatever it is handed (active
         * 2.2..3.8s, dormant 0.8..1.8s, plus a fixed 1.0s warning flare), so the declared numbers
         * choose where inside those clamps a pipe lands:
         *
         *   inactiveDuration 1.7 -> 1.3 -> 0.9  shrinks the safe window from ~2.7s to ~1.8s
         *   activeDuration   2.6 -> 3.0 -> 3.5  stretches the minimum wait from ~2.2s to ~3.0s
         *
         * A lone pipe is never the difficulty: at a 132 u/s walk even the ~1.8s window covers 238
         * units against a jet 24 wide. Pairs are. The gate at 3620/3790 and the pair at 5560/5700
         * have to be read as one crossing on one window, and the jet at 4700 has to be taken at
         * spam-tap pace because it stands inside lvl7_fan_3's gale.
         *
         * The duct is standing height for its whole length. Three crouch restrictions were built
         * here on 2026-09-25 and taken out again the same day on the owner's call ("remove the
         * crawl under things") - do not reintroduce them without asking; `canClimb = false` and a
         * flat floor mean the corridor's only vocabulary is wind, steam and drones.
         */
        val LEVEL_7_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 6410.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            // Whole middle section corridor: top black beam in bglvl7.png is at Y=212.0
            // Height = (488.0 - 212.0) * (480.0 / 724.0) / 1.35 ≈ 136.0 world units
            val ceilingBottomY = 304.0
            val ventCeiling = Rect(x = 0.0, y = ceilingBottomY - 28.0, width = 6160.0, height = 28.0)
            val chamberBackWall = Rect(x = 6370.0, y = 200.0, width = 40.0, height = groundY - 200.0)

            val fans = listOf(
                // Beat 1 - the tutorial gale: widest zone (393..753), gentlest push. Left exactly
                // as it was; step_spam_fan and several wind tests are pinned to this one's zone.
                VentFanDef(
                    id = "lvl7_fan_1",
                    x = 753.0,
                    y = 338.0,
                    width = 36.0,
                    height = 68.0,
                    windRange = 360.0,
                    windPushSpeed = 135.0,
                    windDirection = -1.0,
                    fanImpulse = 10.0
                ),
                // Beat 4 - zone 3100..3400, immediately before the two-jet gate, so the gate is
                // walked up to out of breath rather than from a standing start.
                VentFanDef(
                    id = "lvl7_fan_2",
                    x = 3400.0,
                    y = 338.0,
                    width = 36.0,
                    height = 68.0,
                    windRange = 300.0,
                    windPushSpeed = 145.0,
                    windDirection = -1.0,
                    fanImpulse = 10.0
                ),
                // Beat 5 - zone 4660..5000, with lvl7_pipe_9 at 4700 standing inside it: the one
                // place in the level where a steam window has to be taken at spam-tap pace.
                VentFanDef(
                    id = "lvl7_fan_3",
                    x = 5000.0,
                    y = 338.0,
                    width = 36.0,
                    height = 68.0,
                    windRange = 340.0,
                    windPushSpeed = 150.0,
                    windDirection = -1.0,
                    fanImpulse = 10.0
                ),
                // Beat 6 - zone 5360..5480. Short and violent rather than wide: a gust off the
                // mouth of duct3, clear of the duct itself so it is never fought while crouched.
                VentFanDef(
                    id = "lvl7_fan_4",
                    x = 5480.0,
                    y = 338.0,
                    width = 36.0,
                    height = 68.0,
                    windRange = 120.0,
                    windPushSpeed = 160.0,
                    windDirection = -1.0,
                    fanImpulse = 10.0
                )
            )

            // Sorted by x, and kept that way: the level 7 walkthrough test picks the next pipe
            // with a `firstOrNull { px < pipe.x + 20.0 }` scan that assumes ascending order.
            val steamPipes = listOf(
                // --- Beat 2: steam on its own, 300+ apart, the longest windows in the level. ----
                SteamPipeDef(
                    id = "lvl7_pipe_1",
                    x = 1150.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.TOP,
                    activeDuration = 2.6,
                    inactiveDuration = 1.7,
                    phaseOffsetSeconds = 0.0
                ),
                SteamPipeDef(
                    id = "lvl7_pipe_2",
                    x = 1450.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.BOTTOM,
                    activeDuration = 2.6,
                    inactiveDuration = 1.7,
                    phaseOffsetSeconds = 1.8
                ),
                SteamPipeDef(
                    id = "lvl7_pipe_3",
                    x = 1800.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.TOP,
                    activeDuration = 2.7,
                    inactiveDuration = 1.6,
                    phaseOffsetSeconds = 0.5
                ),
                // --- Beat 3: one either side of the first crouch duct. --------------------------
                SteamPipeDef(
                    id = "lvl7_pipe_4",
                    x = 2480.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.BOTTOM,
                    activeDuration = 2.8,
                    inactiveDuration = 1.5,
                    phaseOffsetSeconds = 1.0
                ),
                SteamPipeDef(
                    id = "lvl7_pipe_5",
                    x = 3000.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.TOP,
                    activeDuration = 2.8,
                    inactiveDuration = 1.5,
                    phaseOffsetSeconds = 0.3
                ),
                // --- Beat 4: the two-jet gate. 170 apart - close enough that the gap between them
                //     is a place to pass through rather than a place to stop, far enough that a
                //     player who misreads the first window is not already inside the second. ------
                SteamPipeDef(
                    id = "lvl7_pipe_6",
                    x = 3620.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.BOTTOM,
                    activeDuration = 3.0,
                    inactiveDuration = 1.3,
                    phaseOffsetSeconds = 0.0
                ),
                SteamPipeDef(
                    id = "lvl7_pipe_7",
                    x = 3790.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.TOP,
                    activeDuration = 3.0,
                    inactiveDuration = 1.3,
                    phaseOffsetSeconds = 1.6
                ),
                // --- Beat 5: a lone jet, then the jet standing inside fan_3's gale. ------------
                SteamPipeDef(
                    id = "lvl7_pipe_8",
                    x = 4390.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.BOTTOM,
                    activeDuration = 3.1,
                    inactiveDuration = 1.2,
                    phaseOffsetSeconds = 0.0
                ),
                SteamPipeDef(
                    id = "lvl7_pipe_9",
                    x = 4700.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.BOTTOM,
                    activeDuration = 3.2,
                    inactiveDuration = 1.1,
                    phaseOffsetSeconds = 1.4
                ),
                // --- Beat 6: the tightest pair in the level, 140 apart on ~1.8s windows. --------
                SteamPipeDef(
                    id = "lvl7_pipe_10",
                    x = 5560.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.TOP,
                    activeDuration = 3.5,
                    inactiveDuration = 0.9,
                    phaseOffsetSeconds = 0.0
                ),
                SteamPipeDef(
                    id = "lvl7_pipe_11",
                    x = 5700.0,
                    topY = ceilingBottomY,
                    bottomY = groundY,
                    mountType = PipeMountType.BOTTOM,
                    activeDuration = 3.5,
                    inactiveDuration = 0.9,
                    phaseOffsetSeconds = 1.5
                )
            )

            val cameraBots = listOf(
                // Beat 3 - the one the tutorial teaches on. Slowest, shortest sight, and the only
                // hazard in its stretch. Left exactly where it was; the deactivation test is pinned
                // to this one's start and patrol span.
                CameraBotDef(
                    id = "lvl7_bot_1",
                    startX = 2080.0,
                    surfaceY = groundY,
                    patrolMinX = 2050.0,
                    patrolMaxX = 2250.0,
                    speed = 36.0,
                    facing = 1.0,
                    visionRange = 120.0
                ),
                // Beat 4 - faster and longer-sighted, closing the beat that opened with a headwind,
                // so it is met with the gale already behind the player.
                CameraBotDef(
                    id = "lvl7_bot_2",
                    startX = 3960.0,
                    surfaceY = groundY,
                    patrolMinX = 3920.0,
                    patrolMaxX = 4140.0,
                    speed = 44.0,
                    facing = 1.0,
                    visionRange = 135.0
                ),
                // Beat 6 - on the door. Its patrol is deliberately short (90 units) so it turns
                // often: the way past is to walk in behind it on a turn and disable it, which is
                // the skill beat 3 taught, now with no room to wait it out.
                CameraBotDef(
                    id = "lvl7_bot_3",
                    startX = 6160.0,
                    surfaceY = groundY,
                    patrolMinX = 6140.0,
                    patrolMaxX = 6230.0,
                    speed = 50.0,
                    facing = 1.0,
                    visionRange = 130.0
                )
            )

            // One per beat, plus an extra inside the final gauntlet. None sits under a duct: a
            // checkpoint respawns the player standing (groundY - 96.0), which under a duct would
            // put them inside its block.
            val checkpoints = listOf(
                Checkpoint(
                    id = "lvl7_cp1_fan1",
                    x = 840.0,
                    y = groundY - 96.0,
                    triggerZone = Rect(820.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
                ),
                Checkpoint(
                    id = "lvl7_cp2_pipes",
                    x = 1600.0,
                    y = groundY - 96.0,
                    triggerZone = Rect(1580.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
                ),
                Checkpoint(
                    id = "lvl7_cp3_bot1",
                    x = 2300.0,
                    y = groundY - 96.0,
                    triggerZone = Rect(2280.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
                ),
                Checkpoint(
                    id = "lvl7_cp4_fan2",
                    x = 3480.0,
                    y = groundY - 96.0,
                    triggerZone = Rect(3460.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
                ),
                Checkpoint(
                    id = "lvl7_cp5_bot2",
                    x = 4180.0,
                    y = groundY - 96.0,
                    triggerZone = Rect(4160.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
                ),
                Checkpoint(
                    id = "lvl7_cp6_fan3",
                    x = 5080.0,
                    y = groundY - 96.0,
                    triggerZone = Rect(5060.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
                ),
                Checkpoint(
                    id = "lvl7_cp7_manifold",
                    x = 5500.0,
                    y = groundY - 96.0,
                    triggerZone = Rect(5480.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
                )
            )

            // Far enough past the last stencil (6190) that the booth drawn from here does not
            // cover it - see the class doc.
            val exitX = 6260.0

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 100.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = exitX, y = ceilingBottomY, width = 60.0, height = groundY - ceilingBottomY),
                platforms = listOf(ground, ventCeiling, chamberBackWall),
                boxes = listOf(ventCeiling, chamberBackWall),
                guards = emptyList(),
                fans = fans,
                cameraBots = cameraBots,
                steamPipes = steamPipes,
                hasStartFences = false,
                canClimb = false,
                playerStartCrouched = false,
                manualCheckpoints = checkpoints
            )
        }

        val DEFAULT_LEVEL_7 = LevelData(
            id = "level_7",
            name = "07: Service Tunnel",
            // Calibrated the way level 4's 115s is: just under what its own walkthrough sim takes
            // (85.3s here, 116.9s there), so the "finish under" objective is beaten by reading the
            // hazards rather than by waiting out every single window. 6160 units is 46.7s of pure
            // walking, so this is roughly twice the theoretical floor. It was briefly 95 while the
            // level had crouch ducts in it.
            timeTargetSeconds = 85.0f,
            description = "Avoid the heavily guarded security room through the underground service tunnel.",
            objectiveHint = "Infiltrate Facility",
            layout = LEVEL_7_LAYOUT,
            backgroundImage = "bglvl7.png",
            hasDarknessVignette = false,
            tutorialSteps = listOf(
                TutorialStep(
                    id = "step_spam_fan",
                    triggerMinX = 340.0,
                    triggerMaxX = 750.0,
                    title = "TURBINE EXHAUST",
                    instructionTouch = "Continuously tap RIGHT to push through the headwind!",
                    instructionDesktop = "Continuously tap [D] or [RIGHT] to push through the headwind!",
                    targetAction = TutorialAction.MOVE,
                    highlight = TutorialControlHighlight.MOVE_RIGHT,
                    handwrittenCallout = "Continuously tap to fight the wind!"
                ),
                TutorialStep(
                    id = "step_deactivate_bot",
                    triggerMinX = 1950.0,
                    triggerMaxX = 2280.0,
                    title = "PATROL DRONE",
                    instructionTouch = "Approach the patrol bot from behind and tap INTERACT to disable it.",
                    instructionDesktop = "Approach the patrol bot from behind and press [E] or [F] to disable it.",
                    targetAction = TutorialAction.INTERACT,
                    // Disabling the drone is one option, not the only one - so the prompt says its
                    // piece and goes, instead of waiting for a choice the player may not make.
                    autoDismissSeconds = 5.0,
                    highlight = TutorialControlHighlight.INTERACT,
                    handwrittenCallout = "Approach from behind to disable!"
                )
            )
        )

        /**
         * The push-stance stage - a bare rehearsal room, NOT a shipped level.
         *
         * Deliberately EMPTY: flat ground from wall to wall and nothing else. No guards, no
         * cameras, no boxes, no hazards, no start fences, no hanging anything. It used to be one
         * of the `GameWorld.createDefault` levels (a patrolling guard and a corridor derived from
         * guardPatrolMinX/MaxX); that was cleared out so the push animation can be watched on its
         * own with nothing walking into frame or killing the player mid-stance.
         *
         * [LevelLayout.pushStanceDemo] is what makes it a stage rather than just a bare level:
         * INTERACT anywhere in it toggles the braced push stance (GameWorld.updatePushStance).
         * Nothing else in the game sets that flag.
         *
         * `canClimb = false` and no boxes means there is nothing to climb; the ground runs the
         * full width so the player cannot fall out of the world while experimenting. The exit sits
         * at the far end so the stage is still completable - it is a long walk at the braced
         * stance's ~53 u/s, which is the point: it is enough room to watch a full gait cycle
         * several times over.
         *
         * This WAS level 8 until 2026-09-25, when level 8 became a real shipped level (see
         * [LEVEL_8_LAYOUT]) and the stage moved to its own id - it is not in [DEFAULT_LEVELS] and
         * no menu lists it. Reach it by id through [findById]: the `.debug_level` file hook on
         * desktop, or `./gradlew runJvm -PstartLevel=push_stance_demo`.
         */
        val PUSH_STANCE_DEMO_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 3600.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)
            val leftWall = Rect(x = -30.0, y = 0.0, width = 30.0, height = 520.0)
            val rightWall = Rect(x = worldWidth, y = 0.0, width = 30.0, height = 520.0)
            val exitX = 3380.0

            LevelLayout(
                worldWidth = worldWidth,
                // Far enough in that the camera is no longer clamped against the left edge of the
                // world, so the character sits mid-screen instead of behind the on-screen D-pad.
                // At a spawn of 160 the whole lean-in played underneath the left movement button,
                // which on a stage whose only job is to show the animation is the one thing that
                // must not happen. The camera window is 1040/1.35 = ~770 world units wide, so the
                // player has to start at least half of that from 0 to be centred.
                playerStartX = 560.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = exitX, y = groundY - 140.0, width = 90.0, height = 140.0),
                platforms = listOf(ground, leftWall, rightWall),
                boxes = emptyList(),
                guards = emptyList(),
                hasStartFences = false,
                canClimb = false,
                pushStanceDemo = true
            )
        }

        /** See [PUSH_STANCE_DEMO_LAYOUT] - a dev stage, kept out of [DEFAULT_LEVELS]. */
        val PUSH_STANCE_DEMO = LevelData(
            id = "push_stance_demo",
            name = "Push stance (dev stage)",
            timeTargetSeconds = 22.0f,
            description = "Bare stage for the braced push animation.",
            objectiveHint = "Push the load to extraction",
            layout = PUSH_STANCE_DEMO_LAYOUT,
            tutorialSteps = listOf(
                TutorialStep(
                    id = "step_push_stance",
                    triggerMinX = 600.0,
                    triggerMaxX = 980.0,
                    title = "BRACE AND PUSH",
                    instructionTouch = "Tap INTERACT to brace, then walk. Tap INTERACT again to stand up.",
                    instructionDesktop = "Press [E] or [F] to brace, then walk. Press it again to stand up.",
                    targetAction = TutorialAction.INTERACT,
                    highlight = TutorialControlHighlight.INTERACT,
                    handwrittenCallout = "Brace, then walk it along!"
                )
            )
        )

        /**
         * Level 8: the suspended-load yard ("08: Relocation"), built 2026-09-25, reworked the
         * same day against a nine-point rewrite.
         *
         * Replaces the bare push-stance stage that held this slot (now [PUSH_STANCE_DEMO_LAYOUT],
         * off the shipped list) - level 8 was hidden behind the `.take(7)` production gate until
         * this layout gave it something to be.
         *
         * The rework, in the order it was asked for: "remove that duck under it thing, there
         * should not be any tutorial in level 8 / make the long hanging crate and the short
         * hanging crate and the platform with crate above it much more closer to the start /
         * remove the long hanging crate above the platform, replace it with a short hanging crate
         * which moves left and right / move all three hanging crates little bit down to a level
         * where the bottom level is just above the top of the platform / make the platform more
         * shorter and add 3 barrels in a row at the bottom of the other side of the platform / in
         * that right side of the platform add 2 short hanging crates that move up and down, player
         * should crouch to avoid them, make sure that it is not possible for them to jump or climb
         * onto them / after that add a long hanging crate, person doesnt have to crouch for that /
         * after that there is a wooden crate and a lever after that, above them is a wooden box
         * connected using rope to a hook, pressing the lever drops the crate / after that there is
         * a high platform with a camera pole connected to it that switches from looking at hanging
         * crates and the lever."
         *
         * **The hang line ([hangClearance]).** Three crates share one height: [overheadCrate],
         * [sweepCrate] and [platformCrate] all hang with their undersides 62 above [platform]'s
         * own surface, which is the "just above the top of the platform" the rework asked for.
         * 62 is not free choice - it is the FLOOR of a 40-unit window and the level would break
         * outside it:
         *   - under 56 ([Player.crouchHeight]) nothing gets past [platformCrate] at all. It
         *     sweeps left and right over a 240-wide platform the player has to cross; with no
         *     duck-under there is no safe pocket to wait in, only a corridor that closes from
         *     whichever side the load happens to be returning from. Worked through on paper and
         *     it dead-ends every time - the crate always catches whoever is waiting it out.
         *   - at 96 ([Player.height]) or more a standing body walks straight under, and the
         *     crossing stops being an obstacle.
         * 62 leaves a crouched body 6 units of headroom - the tightest the geometry allows while
         * still clearing [Player.crouchHeight] - so it reads as low as the request wanted without
         * sealing the platform shut. Anything that lowers it further has to move
         * [Player.crouchHeight] first.
         *
         * **Section 1 - the plane.** Default start fences (`hasStartFences`, no explicit rects -
         * the same pair every other level gets), then the two loads from the original build,
         * pulled in hard toward the spawn on request: [overheadCrate] (long, stationary) now
         * starts at 430 instead of 760, and [platform] at 900 instead of 1560. Both loads hang on
         * level 2's own `chainedcrate.png` rigging. Neither can be stood on: `findClimbTarget`
         * refuses any box whose underside sits more than 4 above the climber's feet (a "floating
         * ledge" with no face to brace against), and at 206 above the floor they are four times
         * [Player.maxJumpHeight] (51.2) out of jump reach as well - so "player cant get on top of
         * these two" still needs no `unclimbableBoxes` entry.
         *
         * **Section 2 - the climb, and the crush.** [stepCrate] (68x48) stands flush against
         * [platform]'s left face: ground -> crate is a 48 jump (inside the 51.2 arc, so it is
         * jumped and never mantled), crate -> platform is exactly 96, this game's canonical climb.
         * 144 from the floor to the platform top is past [Player.climbMaxHeight] (115), so there
         * is no way up that skips the crate.
         *
         * [sweepCrate] is the hazard, carried over from the first build with its timing intact: a
         * [MovingPlatformDef] with `crushesOnContact`, the flag LEVEL_6_LAYOUT's gantry crate
         * introduced, which only bites from below (feet under the crate's own underside) so it is
         * never a platform that kills whoever stands on it. At the new 62 hang line it works by a
         * different route than it did at 44, and a more literal one:
         *   - `Player.bounds` uses `currentHeight`, and `isCrouching` is only set at the END of a
         *     climb ([Player.advanceClimb]), so a climbing body is 96 tall for the whole 1.95s of
         *     [Player.climbDuration]. A load over the landing therefore catches it - MISSION
         *     FAILED - which is exactly "when it is at right it can crush the person if he tries
         *     to climb".
         *   - at 44 the climb was instead REFUSED outright (neither height fit, so
         *     `findClimbTarget` returned nothing and the player was held at the bottom). 62 fits a
         *     crouched body, so the climb is allowed and the crate kills it mid-ascent. Strictly
         *     the better read of the original request.
         * The sweep is 200 long over an 8s period, covering the landing only over the last 170 of
         * that travel. The number that had to be tuned is not that window but the WORST one: a
         * player who starts climbing the instant the load swings clear still has it coming back.
         * Read off `MovingPlatform`'s cosine easing, a crate that is clear AND travelling left has
         * at least 0.3734 of a period left before it covers the landing again - 2.99s here,
         * against 1.95s of climb plus the ~0.25s walk out from under it. So "clear and swinging
         * away" is a cue that always pays off and "clear and swinging back" is the trap. A shorter
         * period, a wider crate or a rest position closer to the lip all eat that same margin.
         * It sweeps LEFT off the landing (out over the plane) rather than right, for the same
         * reason level 6's does: everything right of the landing stays permanently clear.
         *
         * **Section 3 - crossing the platform.** [platformCrate] replaces the long stationary
         * crouch crate the first build had here ("remove the long hanging crate above the
         * platform, replace it with a short hanging crate which moves left and right"). It is
         * deliberately NOT a crusher: a crouched body clears it, a standing one is simply blocked
         * by it as by any other solid, so the worst a mistimed crossing costs is a shove. Its
         * sweep starts at 950, past the landing's own right edge (`climbLandingX` + the player's
         * 36 width = 942), so the landing belongs to [sweepCrate] alone and the two hazards never
         * stack on the same tile. [platform] itself is 240 wide, down from 520 - "make the
         * platform more shorter" - which is what keeps the crouch-crawl across it short.
         *
         * **Section 4 - the barrels and the bobbing pair.** Three `barrel.png` barrels (32x48)
         * stand in a row on the ground against [platform]'s right face, and past them [bobCrate1]
         * and [bobCrate2] hang from the ceiling moving UP and DOWN on opposite phases. Their
         * clearance above the FLOOR travels between [bobLowClearance] (62) and
         * [bobHighClearance] (90), and both ends of that travel matter:
         *   - 90 at the top is still under [Player.height] (96), so a standing body never fits,
         *     at any point in the cycle. That is what makes "player should crouch to avoid them"
         *     unconditional rather than a timing puzzle - the bob is menace, not a window.
         *   - 62 at the bottom clears [Player.crouchHeight] (56) by the same 6 units the hang
         *     line uses, so a crouched body is safe through the whole cycle.
         * Both carry `crushesOnContact`, so walking in upright is fatal rather than merely
         * blocked - the crouch is the answer, as asked. "Make sure that it is not possible for
         * them to jump or climb onto them" falls out of the same numbers: at the bottom of the
         * bob their tops sit 100 above the floor, inside [Player.climbMaxHeight] (115), but
         * `findClimbTarget`'s floating-ledge rule refuses them anyway (underside 62 above the
         * feet, far past its 4-unit tolerance), and 100 is nearly double [Player.maxJumpHeight].
         * At the top of the bob the tops are 128 up, past `climbMaxHeight` outright.
         *
         * **Section 5 - the high load.** [highCrate] is long and stationary and hangs at
         * [noCrouchClearance] (130) above the floor - "person doesnt have to crouch for that".
         * 130 leaves a standing body 34 units of headroom, so it reads as an obstacle from a
         * distance and turns out to be none, which is the point of putting it right after a
         * 280-unit crouch-crawl.
         *
         * **Section 6 - the lever and the drop.** [groundWoodCrate] (the striped `woodcrate2.png`
         * look, via `LevelLayout.woodCrates`) sits on the floor, [dropLever] stands past it, and
         * [dropCrate] hangs above them on a rope from [dropHook] - the same `HookCrate` +
         * `hangingHooks` rigging level 5 uses, minus the swing (this hook is decorative; nothing
         * grabs it). `Lever.targetMechanismId` points at the crate's id, so an INTERACT in range
         * detaches it and `HookCrate.update` drops it under gravity onto the floor.
         *
         * The drop is not decoration - it is the way on. [dropCrate] lands 68x48 with its right
         * edge flush against [highPlatform]'s left face, which turns a 144-tall wall (past
         * `climbMaxHeight`, unclimbable from the ground) into the same jump-then-mantle pair
         * [stepCrate] makes of [platform]: 48 up onto the crate, then exactly 96 onto the
         * platform. **Nothing else reaches [highPlatform]**, so the lever is mandatory, and it is
         * the only mechanism in the level whose state persists - see `HookCrate.reset`, which the
         * level's own restart path already calls.
         *
         * **Section 7 - the pole camera.** [poleCamera] stands on [pole] at [highPlatform]'s left
         * corner (`pole.png`, no collision, the same arrangement as LEVEL_3's own pole camera),
         * lens at (2259, 166), and sweeps between exactly the two things the request named: at
         * [leverAngle] (108 deg, measured straight at [dropLever], 282 away) its cone lands on
         * the lever; at [crateAngle] (157 deg, straight at [highCrate]'s near corner, 365 away)
         * it points out along the hanging load instead. Angles are `atan2` in screen space,
         * where 90 deg is straight down, so the sweep is a tilt from steeply-down to nearly
         * level, all of it to the LEFT - a player who has already made it onto [highPlatform] is
         * behind the lens and safe.
         *
         * **Why this section is laid out the way it is.** Everything from [groundWoodCrate]
         * rightward had to be placed against the camera rather than for its own sake, because of
         * one fact that is easy to rediscover the hard way: from a lens up on a pole, a standing
         * body on a crate at some x and a load hanging further left occupy OVERLAPPING bearings.
         * A cone wide enough to see the load will also see anyone standing on anything between
         * the lens and it. The first cut of this section put the striped crate at 1960 with the
         * camera at 380/40deg, and the crossing of that crate was lit at the very moment the
         * camera was supposed to be looking away - a guaranteed death with no tell. Two numbers
         * fix it together:
         *   - **the crate moved right to 2075.** A body's head on it sits at bearing 144.7 deg
         *     at the worst (leftmost) point, and [crateAngle] - half the cone is 147 deg, so it
         *     clears the blind edge by 2.3 deg. Moving it left again, or widening the cone, puts
         *     it back under the lens.
         *   - **the cone narrowed to 20 deg.** At a 40 deg cone no placement separates them.
         * With `visionRange` 370 the floor itself is out of reach at [crateAngle] entirely (a
         * ray needs 274/sin(132 deg) = 369 just to touch it, and the cone's shallowest ray there
         * is 147 deg), and a standing body is out of range altogether left of about 1935 - which
         * is why the approach waits back under [highCrate] and why the walkthrough test stages at
         * 1900.
         *
         * So the blind window is the whole `sweepPauseDuration` (7s) the camera spends parked on
         * the load, against a run of roughly 5s from the staging spot through the lever, the
         * crate, the drop and both mantles. The full cycle is 2x0.95s of sweep plus 2x7s of
         * pause, 15.9s. Measured rather than argued: the walkthrough driven at 12 different
         * arrival phases finishes in 32.9-47.2s, never dies, and peaks at 0.43 of the alert bar.
         * Detection is gradual anyway (`GameWorld.alertProgress` fills over
         * `getDetectionTimeToCatch`, ~1.1s at this range), so clipping the edge of the cone costs
         * progress, not the run.
         *
         * Falling off costs nothing anywhere here - the ground runs the level's full width and
         * every climb has its step - so the only ways to fail are the two crushers and the camera.
         */
        val LEVEL_8_LAYOUT = run {
            val groundY = 440.0

            // --- The shared hang line: see the class doc. Every crate that hangs over the first
            // half of the level puts its UNDERSIDE here, 62 above the platform's own surface.
            val hangingCrateHeight = 38.0
            val platformTop = 296.0
            val hangClearance = 62.0
            val hangY = platformTop - hangClearance - hangingCrateHeight
            val longCrateWidth = 174.0
            val shortCrateWidth = 76.0

            // --- SECTION 2's geometry first: everything else is placed against the platform ---
            val platformLeft = 900.0
            // 240, down from 520 - "make the platform more shorter". Short enough that the
            // crouch-crawl under platformCrate is a few strides, long enough to hold the landing,
            // that crate's whole sweep, and a step off the right lip.
            val platformWidth = 240.0
            val platform = Rect(
                x = platformLeft,
                y = platformTop,
                width = platformWidth,
                height = groundY - platformTop
            )
            // Where Player.climbLandingX puts the body when it tops out (box.left + 6), and how
            // far right that body then reaches. platformCrate's sweep has to start past this.
            val landingRight = platformLeft + 6.0 + 36.0

            // The only way up - and, since 2026-09-26, not standing where it is needed.
            //
            // This was a fixed 68x48 crate butted against the platform's left face. It is now a
            // loaded flatbed cart (cart.png, a crate riding between its handle posts) parked well
            // short of that face, and the way up is to brace against it and walk it the rest of
            // the way - the first shipped use of the push stance. Two numbers are inherited from
            // the crate it replaced and must not drift: the cart is 48 TALL, so the hop onto it is
            // still a jump (Player.maxJumpHeight 51.2) rather than a mantle, and [cartMaxX] puts
            // its right edge exactly on platformLeft, so the climb off the top is the same 96 that
            // was already tuned against the sweep crate's window.
            //
            // 92 wide is cart.png's own aspect at that height (1.918:1 - the art is stretched to
            // the box, so a different ratio would visibly squash the wheels). It is also wide
            // enough that the body is never standing over the gap it is trying to close.
            val cartHeight = 48.0
            val cartWidth = 92.0
            // Flush against the platform. Reached from cartRestX, this is ~248 units of pushing -
            // about four and a half strides of the braced gait at GameWorld.PUSH_MOVE_FACTOR's
            // ~53 u/s, which is the length the animation was cut to be read at.
            val cartMaxX = platformLeft - cartWidth
            // Parked in clear ground: past the plane's hanging load (430..604 - well overhead, but
            // this is where the player is looking) and short of the sweep crate's own span, so the
            // cart is read as an object in the way of nothing until the platform explains it.
            val cartRestX = 560.0
            // Draggable back past its own rest position, so a pull is a real move and not just an
            // undo - see the tutorial's second step. 430 keeps it inside the plane it started on.
            val cartMinX = 430.0

            // --- SECTION 1: the plane, and the two loads hanging over it ---
            // Pulled in from 760 to 430 on request ("much more closer to the start") - the player
            // spawns at 236, so the first load is now in frame almost immediately instead of a
            // long empty walk away.
            val overheadCrate = Rect(
                x = 430.0,
                y = hangY,
                width = longCrateWidth,
                height = hangingCrateHeight
            )

            // Parked over the landing (platformLeft + 6 .. + 42): the crate's 76 spans
            // platformLeft - 40 .. + 36, so it covers all but the last 6 of the landing with its
            // left end hanging clear out over the gap the player climbs up through. It stops 40
            // SHORT of the lip on purpose - the further right it parks, the further whoever just
            // climbed has to walk to get out from under it before it swings back, and that walk
            // comes out of the same window the climb already spends 1.95s of. At -40 it is 30
            // units, a quarter of a second.
            val sweepCrateMaxX = platformLeft - 40.0
            val sweepCrateSweep = 200.0
            val sweepCrateMinX = sweepCrateMaxX - sweepCrateSweep
            val sweepCrate = MovingPlatformDef(
                id = "lvl8_sweep_crate",
                initialX = sweepCrateMinX,
                y = hangY,
                width = shortCrateWidth,
                height = hangingCrateHeight,
                minX = sweepCrateMinX,
                maxX = sweepCrateMaxX,
                // Long enough that the guaranteed-clear phase outlasts a 1.95s climb with room
                // to step out from under afterwards (see the class doc), and slow enough to be
                // read from back down the plane.
                periodSeconds = 8.0,
                // Free-running off the level clock (no startsInactive - the only lever here is
                // the one on dropCrate, unlike level 6's gantry), starting at the far LEFT end.
                phaseOffsetSeconds = 0.0,
                // The short chainedcrate2.png crop, matching level 2's own short containers -
                // "first crate is a long one and then next is a short one that moves".
                isVariant1 = false,
                // "touching the bottom side of that crate when it is near the platform ends the
                // level". See MovingPlatformDef.crushesOnContact - it only bites from below.
                crushesOnContact = true
            )

            // --- SECTION 3: the crossing. Replaces the first build's long stationary crouch
            // crate. Starts past landingRight so it never contests the landing with sweepCrate,
            // and ends flush with the platform's right lip so the whole crossing is under it.
            val platformCrateMinX = 950.0
            val platformCrateMaxX = platform.right - shortCrateWidth
            val platformCrate = MovingPlatformDef(
                id = "lvl8_platform_crate",
                initialX = platformCrateMinX,
                y = hangY,
                width = shortCrateWidth,
                height = hangingCrateHeight,
                minX = platformCrateMinX,
                maxX = platformCrateMaxX,
                // Quicker than the sweep crate: this one is crossed under, not waited out, so a
                // long period would just mean standing still.
                periodSeconds = 5.0,
                phaseOffsetSeconds = 0.0,
                isVariant1 = false,
                // Deliberately NOT a crusher - see the class doc. A mistimed crossing is a shove,
                // not a restart; the crouch is what gets past it.
                crushesOnContact = false
            )

            // --- SECTION 4a: three barrels in a row on the ground, against the platform's right
            // face ("at the bottom of the other side of the platform"). Level 3 and level 5 both
            // use this exact 32x48 barrel.
            val barrelWidth = 32.0
            val barrelHeight = 48.0
            val barrels = (0 until 3).map { i ->
                Rect(
                    x = platform.right + i * barrelWidth,
                    y = groundY - barrelHeight,
                    width = barrelWidth,
                    height = barrelHeight
                )
            }

            // --- SECTION 4b: the bobbing pair. Clearance above the FLOOR, not the platform:
            // never more than 90 (under Player.height 96, so upright never fits) and never less
            // than 62 (over Player.crouchHeight 56, so ducked always does). See the class doc -
            // both ends of that travel are load-bearing.
            val bobLowClearance = 62.0
            val bobHighClearance = 90.0
            val bobTopHighest = groundY - bobHighClearance - hangingCrateHeight
            val bobTopLowest = groundY - bobLowClearance - hangingCrateHeight
            val bobPeriod = 3.0
            fun bobCrate(id: String, x: Double, phase: Double) = MovingPlatformDef(
                id = id,
                initialX = x,
                y = bobTopHighest,
                width = shortCrateWidth,
                height = hangingCrateHeight,
                minY = bobTopHighest,
                maxY = bobTopLowest,
                periodSeconds = bobPeriod,
                phaseOffsetSeconds = phase,
                isVariant1 = false,
                // "player should crouch to avoid them" - upright contact is fatal, which is what
                // makes the crouch an answer rather than a convenience.
                crushesOnContact = true
            )
            // Opposite phases, so the pair reads as two loads working against each other rather
            // than one bar moving. 64 apart, enough for a 36-wide body to stand between them.
            val bobCrate1 = bobCrate("lvl8_bob_crate_1", 1330.0, 0.0)
            val bobCrate2 = bobCrate("lvl8_bob_crate_2", 1470.0, bobPeriod / 2.0)

            // --- SECTION 5: the long load that turns out to be nothing. 130 above the floor
            // leaves a standing body 34 units of headroom - "person doesnt have to crouch".
            val noCrouchClearance = 130.0
            val highCrate = Rect(
                x = 1750.0,
                y = groundY - noCrouchClearance - hangingCrateHeight,
                width = longCrateWidth,
                height = hangingCrateHeight
            )

            // --- SECTION 6: the striped crate, the lever, and the load they drop ---
            val woodCrateWidth = 68.0
            val woodCrateHeight = 48.0
            val groundWoodCrate = Rect(
                x = 2075.0,
                y = groundY - woodCrateHeight,
                width = woodCrateWidth,
                height = woodCrateHeight
            )

            val leverWidth = 22.0
            val leverHeight = 12.0
            val dropLever = Lever(
                id = "lvl8_drop_lever",
                x = 2160.0,
                y = groundY - leverHeight,
                width = leverWidth,
                height = leverHeight,
                targetMechanismId = "lvl8_hook_crate"
            )

            // --- SECTION 7's platform first: dropCrate is positioned against its face ---
            val highPlatformLeft = 2260.0
            val highPlatform = Rect(
                x = highPlatformLeft,
                y = platformTop,
                width = 300.0,
                height = groundY - platformTop
            )

            // The hanging load, and the only way onto highPlatform. Hung so that when it falls
            // straight down it lands with its RIGHT edge flush against the platform's left face -
            // 48 up onto it from the floor, then the same exact 96 mantle stepCrate gives.
            val dropCrateWidth = 68.0
            val dropCrateHeight = 48.0
            val dropRopeLength = 40.0
            val dropCrateBounds = Rect(
                x = highPlatformLeft - dropCrateWidth,
                y = 250.0,
                width = dropCrateWidth,
                height = dropCrateHeight
            )
            // hook.png, hung so its grip point sits exactly one rope-length above the crate -
            // the same construction level 5 uses for its own hook crate.
            val hookWidth = 16.0
            val hookHeight = hookWidth * (2136.0 / 154.0) // hook.png's own cropped aspect ratio
            val dropGripX = dropCrateBounds.x + dropCrateWidth / 2.0 + 4.5
            val dropGripY = dropCrateBounds.y - dropRopeLength
            val dropHook = Rect(
                x = dropGripX - hookWidth * Player.HOOK_GRIP_X_FRACTION,
                y = dropGripY - hookHeight * Player.HOOK_GRIP_Y_FRACTION,
                width = hookWidth,
                height = hookHeight
            )
            val dropCrate = HookCrate(
                id = "lvl8_hook_crate",
                hook = dropHook,
                bounds = dropCrateBounds,
                ropeLength = dropRopeLength
            )

            // --- SECTION 7: the pole and its camera ---
            val poleWidth = 18.0
            val poleHeight = 130.0
            val pole = Rect(
                x = highPlatformLeft,
                y = highPlatform.top - poleHeight,
                width = poleWidth,
                height = poleHeight
            )
            // Same mount convention as LEVEL_3's pole camera: lens hung at the pole's own top
            // cap, nudged 10 left of the pole's centre so the bracket sits on the collar.
            val cameraEyeX = pole.x + poleWidth / 2.0 - 10.0
            val cameraEyeY = pole.y
            // Measured straight at the two things the request named, from the lens at
            // (2259, 166), in screen space (90 deg is straight down, so both point down-LEFT):
            //   lever (2171, 434): dx  -88, dy 268 -> 108 deg, 282 away
            //   load  (1924, 310): dx -335, dy 144 -> 157 deg, 365 away
            val leverAngle = 108.0 * (PI / 180.0)
            val crateAngle = 157.0 * (PI / 180.0)
            val poleCamera = CameraSpawn(
                x = cameraEyeX,
                y = cameraEyeY,
                minAngle = leverAngle,
                maxAngle = crateAngle,
                startAngle = leverAngle,
                sweepSpeed = 0.9,
                // 370 reaches the load's near corner (365) and the lever (282), and nothing
                // further: a standing body is out of range left of about 1935, which is the
                // staging ground the approach waits on.
                visionRange = 370.0,
                // 20, not the 40-50 the other levels use. A wider cone cannot be aimed at the
                // hanging load without also covering a body standing on groundWoodCrate - see
                // the class doc, this is the number that makes the blind window exist at all.
                visionFov = 20.0 * (PI / 180.0),
                // The blind window itself: 7s parked on the load, against a ~5s run from the
                // staging ground through the lever, the drop and both mantles.
                sweepPauseDuration = 7.0
            )

            val exitX = 2680.0
            val worldWidth = 3140.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            // The cart is deliberately absent: its footprint moves, so GameWorld adds it to the
            // solid/climbable/sight-blocking sets per tick from wherever it currently stands.
            val boxes = listOf(overheadCrate, platform) +
                barrels +
                listOf(highCrate, groundWoodCrate, highPlatform)

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = exitX, y = groundY - 100.0, width = 44.0, height = 100.0),
                platforms = listOf(ground),
                boxes = boxes,
                guards = emptyList(),
                cameras = listOf(poleCamera),
                // The two long stationary loads take level 2's long chainedcrate.png crop; the
                // three moving ones pick their art up from MovingPlatformDef.isVariant1 instead.
                hangingCrateVariant1 = listOf(overheadCrate, highCrate),
                barrels = barrels,
                woodCrates = listOf(groundWoodCrate),
                poles = listOf(pole),
                movingPlatforms = listOf(sweepCrate, platformCrate, bobCrate1, bobCrate2),
                pushCarts = listOf(
                    PushCartDef(
                        id = "lvl8_step_cart",
                        initialX = cartRestX,
                        surfaceY = groundY,
                        width = cartWidth,
                        height = cartHeight,
                        minX = cartMinX,
                        maxX = cartMaxX
                    )
                ),
                hangingHooks = listOf(dropHook),
                levers = listOf(dropLever),
                hookCrates = listOf(dropCrate)
            )
        }

        val DEFAULT_LEVEL_8 = LevelData(
            id = "level_8",
            name = "08: Relocation",
            // Roughly twice the first build's 20-25s: the same read-the-sweep climb, then a
            // crouch-crawl at Player.crouchForwardSpeed (65) under the bobbing pair, then a wait
            // on the pole camera before the lever can be pulled. 80 leaves room for one misread
            // of each without the clock being the thing that beats the player.
            timeTargetSeconds = 80.0f,
            description = "The guards moved Container 17. Follow the trail to its new location.",
            objectiveHint = "Slip Under the Suspended Load",
            layout = LEVEL_8_LAYOUT,
            // Two steps, and only two: the cart is the one move in the game that no earlier level
            // has taught, and nothing about a parked trolley says "this one comes with you". The
            // crouch, the climb and the timing beats are all taught long before here and the
            // level's own geometry still says the rest.
            tutorialSteps = listOf(
                // Opens on the walk up to the cart, before the player is close enough to grab it,
                // so the button is named while the thing it acts on is in frame. The window ends
                // past the cart's own left face because he cannot walk further than that anyway -
                // the cart is solid - so in practice this dismisses on the grab, not on passing.
                TutorialStep(
                    id = "step_push_cart_grab",
                    triggerMinX = 430.0,
                    triggerMaxX = 600.0,
                    title = "TAKE THE CART",
                    instructionTouch = "Tap INTERACT to brace against the cart. Tap it again to let go.",
                    instructionDesktop = "Press [E] or [F] to brace against the cart. Press it again to let go.",
                    targetAction = TutorialAction.INTERACT,
                    highlight = TutorialControlHighlight.INTERACT,
                    handwrittenCallout = "Tap to interact with the cart"
                ),
                // Gated on the grab rather than on position - see TutorialStep.requiresPushCartGrip.
                // The X window is only a safety net around the cart's whole travel (minX 430 to a
                // body standing at the far end of maxX), never the thing that opens it.
                TutorialStep(
                    id = "step_push_cart_move",
                    triggerMinX = 380.0,
                    triggerMaxX = 900.0,
                    requiresPushCartGrip = true,
                    title = "PUSH OR PULL",
                    instructionTouch = "Hold LEFT or RIGHT to walk the cart along.",
                    instructionDesktop = "Hold [A]/[D] or the arrow keys to walk the cart along.",
                    targetAction = TutorialAction.MOVE,
                    // Both arrows, not just forward: a pull is as real a move as a push here and
                    // the prompt should not imply the cart only goes one way.
                    highlight = TutorialControlHighlight.MOVE,
                    // The screen dims behind a highlighted control, so this cannot be allowed to
                    // camp the way level 7's drone prompt did. Long enough to read, short enough
                    // that a player who lets go and walks off is not left staring through a scrim.
                    autoDismissSeconds = 6.0,
                    handwrittenCallout = "Use navigation buttons to push / pull"
                )
            )
        )

        val DEFAULT_LEVEL_9 = LevelData(
            id = "level_9",
            name = "09: Déjà Vu",
            timeTargetSeconds = 23.0f,
            description = "The trail feels strangely familiar, as if you’ve done this before.",
            objectiveHint = "Find Your Crew's Mark",
            guardSpeed = 95.0,
            guardPatrolMinX = 2600.0,
            guardPatrolMaxX = 3080.0
        )

        val DEFAULT_LEVEL_10 = LevelData(
            id = "level_10",
            name = "10: Below the Yard",
            timeTargetSeconds = 24.0f,
            description = "Follow the underground tunnels in search of the person you were tracking.",
            objectiveHint = "Cross the Yard Undetected",
            guardSpeed = 100.0,
            guardPatrolMinX = 2550.0,
            guardPatrolMaxX = 3050.0
        )

        val DEFAULT_LEVEL_11 = LevelData(
            id = "level_11",
            name = "11: The Prisoner",
            timeTargetSeconds = 23.0f,
            description = "Rescue the prisoner and escort him to safety. Something about him feels familiar.",
            objectiveHint = "Follow the Stranger",
            guardSpeed = 105.0,
            guardPatrolMinX = 2550.0,
            guardPatrolMaxX = 3030.0
        )

        val DEFAULT_LEVEL_12 = LevelData(
            id = "level_12",
            name = "12: Final Escape",
            timeTargetSeconds = 22.0f,
            description = "Guards are closing in. Get the prisoner out of the shipyard before it’s too late.",
            objectiveHint = "Open Container 17",
            guardSpeed = 110.0,
            guardPatrolMinX = 2500.0,
            guardPatrolMaxX = 3000.0
        )

        val DEFAULT_LEVELS: List<LevelData> = listOf(
            DEFAULT_LEVEL_1,
            DEFAULT_LEVEL_2,
            DEFAULT_LEVEL_3,
            DEFAULT_LEVEL_4,
            SIDE_SCROLL_LEVEL,
            DEFAULT_LEVEL_6,
            DEFAULT_LEVEL_7,
            DEFAULT_LEVEL_8,
            DEFAULT_LEVEL_9,
            DEFAULT_LEVEL_10,
            DEFAULT_LEVEL_11,
            DEFAULT_LEVEL_12
        )

        /**
         * Levels that exist but are not part of the shipped progression: dev stages, reachable by
         * id and listed in no menu. Deliberately NOT in [DEFAULT_LEVELS] - everything that walks
         * that list (the mission grid, the briefing card, "next level", the star totals) would
         * otherwise have to special-case them.
         */
        val DEV_LEVELS: List<LevelData> = listOf(PUSH_STANCE_DEMO)

        /**
         * Every level reachable by id, shipped or not - what the by-id entry points look through
         * (`-PstartLevel=`, the desktop `.debug_level` file hook), so a dev stage stays reachable
         * without being in [DEFAULT_LEVELS].
         */
        fun findById(id: String): LevelData? =
            DEFAULT_LEVELS.firstOrNull { it.id == id } ?: DEV_LEVELS.firstOrNull { it.id == id }
    }
}

data class LevelResult(
    val levelId: String = "level_1",
    val completed: Boolean,
    val wasDetected: Boolean,
    val timeTaken: Float,
    val timeTargetSeconds: Float
) {
    // Star 1: completed == true
    val star1: Boolean get() = completed

    // Star 2: wasDetected == false for the whole run
    val star2: Boolean get() = !wasDetected

    // Star 3: timeTaken <= timeTargetSeconds
    val star3: Boolean get() = timeTaken <= timeTargetSeconds

    val starsEarned: List<Boolean> get() = listOf(star1, star2, star3)
    val starCount: Int get() = starsEarned.count { it }

    fun mergedWith(other: LevelResult): LevelResult {
        require(levelId == other.levelId) { "Cannot merge results with different levelIds: $levelId vs ${other.levelId}" }
        val bestCompleted = this.completed || other.completed
        // Best undetected: if either run was completed without detection, or if neither run was detected
        val bestUndetected = if (bestCompleted) {
            (this.completed && !this.wasDetected) || (other.completed && !other.wasDetected)
        } else {
            !this.wasDetected || !other.wasDetected
        }
        val bestTime = when {
            this.completed && other.completed -> minOf(this.timeTaken, other.timeTaken)
            this.completed -> this.timeTaken
            other.completed -> other.timeTaken
            else -> minOf(this.timeTaken, other.timeTaken)
        }
        return LevelResult(
            levelId = levelId,
            completed = bestCompleted,
            wasDetected = !bestUndetected,
            timeTaken = bestTime,
            timeTargetSeconds = timeTargetSeconds
        )
    }

    fun serialize(): String {
        return "$levelId,$completed,$wasDetected,$timeTaken,$timeTargetSeconds"
    }

    companion object {
        fun deserialize(data: String): LevelResult? {
            val parts = data.split(",")
            if (parts.size != 5) return null
            val id = parts[0]
            val completed = parts[1].toBooleanStrictOrNull() ?: return null
            val wasDetected = parts[2].toBooleanStrictOrNull() ?: return null
            val timeTaken = parts[3].toFloatOrNull() ?: return null
            val timeTarget = parts[4].toFloatOrNull() ?: return null
            return LevelResult(
                levelId = id,
                completed = completed,
                wasDetected = wasDetected,
                timeTaken = timeTaken,
                timeTargetSeconds = timeTarget
            )
        }
    }
}

interface LevelStorage {
    fun saveResult(result: LevelResult)
    fun getBestResult(levelId: String): LevelResult?
    fun getAllResults(): Map<String, LevelResult>
    fun clear()
}

class InMemoryLevelStorage : LevelStorage {
    private val results = mutableMapOf<String, LevelResult>()

    override fun saveResult(result: LevelResult) {
        val existing = results[result.levelId]
        results[result.levelId] = if (existing == null) result else existing.mergedWith(result)
    }

    override fun getBestResult(levelId: String): LevelResult? {
        return results[levelId]
    }

    override fun getAllResults(): Map<String, LevelResult> {
        return results.toMap()
    }

    override fun clear() {
        results.clear()
    }
}

class MapBackedLevelStorage(
    private val getRaw: (String) -> String?,
    private val setRaw: (String, String) -> Unit,
    private val removeRaw: ((String) -> Unit)? = null
) : LevelStorage {
    private val inMemoryFallback = InMemoryLevelStorage()

    override fun saveResult(result: LevelResult) {
        val existing = getBestResult(result.levelId)
        val merged = if (existing == null) result else existing.mergedWith(result)
        inMemoryFallback.saveResult(merged)
        try {
            setRaw("level_result_${result.levelId}", merged.serialize())
            val storedIds = getRaw("level_results_ids")?.split(";")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            if (!storedIds.contains(result.levelId)) {
                setRaw("level_results_ids", (storedIds + result.levelId).joinToString(";"))
            }
        } catch (_: Throwable) {
            // Safe fallback to in-memory if raw store fails
        }
    }

    override fun getBestResult(levelId: String): LevelResult? {
        try {
            val raw = getRaw("level_result_$levelId")
            if (raw != null) {
                val deserialized = LevelResult.deserialize(raw)
                if (deserialized != null) {
                    return deserialized
                }
            }
        } catch (_: Throwable) {
            // Safe fallback
        }
        return inMemoryFallback.getBestResult(levelId)
    }

    override fun getAllResults(): Map<String, LevelResult> {
        val map = inMemoryFallback.getAllResults().toMutableMap()
        val storedIds = try {
            getRaw("level_results_ids")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        val allIds = (LevelData.DEFAULT_LEVELS.map { it.id } + storedIds).distinct()
        for (id in allIds) {
            if (!map.containsKey(id)) {
                val best = getBestResult(id)
                if (best != null) {
                    map[id] = best
                }
            }
        }
        return map
    }

    override fun clear() {
        inMemoryFallback.clear()
        try {
            val storedIds = getRaw("level_results_ids")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
            val allIds = (LevelData.DEFAULT_LEVELS.map { it.id } + storedIds).distinct()
            for (id in allIds) {
                removeRaw?.invoke("level_result_$id")
                setRaw("level_result_$id", "")
            }
            removeRaw?.invoke("level_results_ids")
            setRaw("level_results_ids", "")
        } catch (_: Throwable) {
        }
    }
}
