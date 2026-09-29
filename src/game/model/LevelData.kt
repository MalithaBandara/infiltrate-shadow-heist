package game.model

import kotlin.math.PI
import kotlin.math.atan2

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
/**
 * A prison cell seen from the front, at the end of a level (LEVEL_10_LAYOUT's last room): a
 * barred front over [bars] (floor to ceiling, drawn over the cell and behind the player) and a
 * figure sitting on the cell floor at [prisonerX], facing [prisonerFacing]. Purely decorative -
 * the level's exitZone is what ends it, and a level with a cell draws no extraction booth.
 */
data class PrisonCellDef(
    val bars: Rect,
    val prisonerX: Double,
    val prisonerFacing: Double = -1.0,
    /**
     * False where the cell's occupant is a live [Prisoner] drawn on his own (level 11): the cell
     * then draws only its gloom and bars.
     */
    val drawsFigure: Boolean = true
)

/**
 * A picture hung on the back wall (drawn behind everything that moves, never collides) - a sign
 * or poster, [image] a file in resources/ stretched to [bounds]. Level 11's exit sign.
 * [brightness] multiplies its colour (1 = as painted): art lit for daylight is too bright on a
 * dim wall.
 */
data class WallDecal(val bounds: Rect, val image: String, val brightness: Double = 1.0)

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
     * Table legs a body passes straight through: drawn with the leg's art crop, washed out the way
     * a camera pole is (GameplayScene's translucentEffectAlpha) so they read as not-solid, and in
     * nothing else - not [boxes], not a sight blocker. LEVEL_8_LAYOUT's stacked tables: the upper
     * table's legs stand right in the lower table's laser course.
     */
    val passThroughLegs: List<Rect> = emptyList(),
    /**
     * Tables (also in [tables] and [boxes]) drawn as ONE continuous slab of any length: table.png's
     * repeating slab unit tiled end to end between its two end caps, instead of [tableParts]'
     * stretched pieces with a seam at every join. LEVEL_8_LAYOUT's long table.
     */
    val seamlessTables: List<Rect> = emptyList(),
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
    /**
     * The art [exitStructure] is drawn with (a file in resources/). LEVEL_8_LAYOUT ends at
     * container17.png - "Container 17", the thing levels 5-8 have been chasing.
     */
    val exitStructureImage: String = "exitlvl7.png",
    // A box the player can mantle onto directly despite Player.findClimbTarget's usual rule
    // against floating ledges (a box whose underside sits well above the climber's feet) - for a
    // ledge that's meant to be mounted with nothing bracing it underneath. Must also be in
    // [boxes]. See Player.findClimbTarget and LEVEL_3_LAYOUT.
    val floatingClimbTargets: List<Rect> = emptyList(),
    /**
     * Floating climb targets (also in [floatingClimbTargets]) the player can walk under, so
     * nothing stops him at the edge where the grab works - they are grabbed from a wider window
     * (Player.hangingClimbTargets). LEVEL_11_LAYOUT's shaft catwalks.
     */
    val hangingClimbTargets: List<Rect> = emptyList(),
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
    val pushStanceDemo: Boolean = false,
    /**
     * Raised platforms that count as "the ground" for [LevelData.stayOffTheGround] - standing on
     * one fails the mission exactly as standing on the floor does. Crates, barrels, carts and
     * hanging loads are never on this list: those are what a stay-off-the-ground run is made of.
     */
    val offLimitFootholds: List<Rect> = emptyList(),
    // Interior back walls drawn with room.png (tiled at the rect's height) behind everything in
    // it - LEVEL_10_LAYOUT's upper room. Purely decorative; the room's floor, walls and ceiling
    // are ordinary [boxes].
    val roomBackdrops: List<Rect> = emptyList(),
    val prisonCell: PrisonCellDef? = null,
    /** Doors across corridors, thrown by [doorSwitches] - see [DoorDef]. Level 11. */
    val doors: List<DoorDef> = emptyList(),
    /** Wall switches working [doors] and [lifts] by id - see [DoorSwitchDef]. */
    val doorSwitches: List<DoorSwitchDef> = emptyList(),
    /** Freight lifts between the duct and the rooms over it - see [LiftDef]. */
    val lifts: List<LiftDef> = emptyList(),
    /**
     * Someone who has to be got out alive (level 11): he walks on his own, and the level is only
     * complete once he and the player are both in [exitZone]. Guards, cameras and bots that see
     * him fill the same alert meter as seeing the player; steam kills him. See [PrisonerDef].
     */
    val prisoner: PrisonerDef? = null,
    /** Signs on the back wall - see [WallDecal]. */
    val wallDecals: List<WallDecal> = emptyList()
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
    val hasRain: Boolean = false,
    /**
     * "Don't touch the ground" is the MAIN objective: standing on the floor, or on any of the
     * layout's [LevelLayout.offLimitFootholds], is Mission Failed. Level 9's rule - "make the main
     * objective of level 9 to not touch the ground and mission will fail if he touches ground /
     * platforms". Star 3 stays the clock.
     */
    val stayOffTheGround: Boolean = false,
    /**
     * No hanging load can be got onto at all - static hanging crates, moving loads and a hook crate
     * still on its rope each carry GameWorld's invisible boarding lid (BOARDING_LID_HEIGHT) and
     * are never climb targets, whatever the body is standing on. Level 8's road is the ground
     * ("artifically block getting on the hanging platform if player somehow tries to in level 8",
     * 2026-09-30); level 9, the same yard, is run along those loads and leaves this off.
     */
    val hangingLoadsOffLimits: Boolean = false,
    /**
     * The run is recorded ([RunRecorder]) and, when the level is completed without a continue,
     * saved for a later level to replay - level 8's, which level 9 follows ([replaysRunOf]).
     */
    val recordsRun: Boolean = false,
    /**
     * The id of the level whose recorded run is played back here as an [EchoRunner] that sees and
     * hears like a guard - level 9 replays level 8's ("he is following behind his previous run").
     */
    val replaysRunOf: String? = null,
    /**
     * The level's own optional objective - star 2 and the HUD's middle objective row. Every
     * shipped level 1-9 has one; null (levels 10-12) keeps the old star 2, "no alerts raised"
     * ([LevelResult.wasDetected]).
     */
    val bonusObjective: BonusObjective? = null
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
            // Autopilot clean run 28.8s (3900 units is 28s of pure walking); star 3 is that x1.5 -
            // a person needs the extra to read the crouch-under and the climbs. LevelWalkthroughTest.
            timeTargetSeconds = 45.0f,
            description = "Reach the shipyard under cover of darkness and find a way inside.",
            objectiveHint = "Find the Shipyard Entrance",
            bonusObjective = BonusObjective.BASIC_MOVES,
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
            // Autopilot clean run 42.2s, including the wait for each moving container to come within
            // reach; x1.4. LevelWalkthroughTest.
            timeTargetSeconds = 60.0f,
            description = "Cross the empty container yard and reach the restricted section.",
            objectiveHint = "Find a Way Through the Yard",
            bonusObjective = BonusObjective.NO_DROP_FROM_HANGING_CRATES,
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
            val finalWorldWidth = finalExitX + 460.0
            val ground = Rect(x = 0.0, y = groundY, width = finalWorldWidth, height = 100.0)

            // The level ends at level 4's conveyor belt ("Reach the Conveyor Belt") instead of the
            // shared booth + fence: the same 26-tall belt on the ground at level 4's -45, running from
            // here off the right edge of the world. A conveyor level draws no booth (GameplayScene).
            // The trigger starts [exitApproach] short of the belt - the level ends as the body nears
            // it, the same way level 8 ends at Container 17 - at the old trigger's x, so the route
            // and its clean-run time are unchanged. 120 tall so no jump passes over it.
            val exitApproach = 70.0
            val endConveyorHeight = 26.0
            val endConveyorRect = Rect(
                x = finalExitX + exitApproach, y = groundY - endConveyorHeight,
                width = finalWorldWidth - (finalExitX + exitApproach) + 200.0, height = endConveyorHeight
            )
            val endConveyor = ConveyorDef(bounds = endConveyorRect, speed = -45.0)

            LevelLayout(
                worldWidth = finalWorldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = finalExitX, y = groundY - 120.0, width = exitApproach + 44.0, height = 120.0),
                platforms = listOf(ground, endConveyorRect),
                conveyors = listOf(endConveyor),
                boxes = listOf(
                    crate, tablePlank, hideCrate, rightLeg, longCrate1, longCrate2,
                    stepCrate2, cameraBeam, cameraLeg, finalHangingCrate, hangingEndCrate, finalPlatform, endConveyorRect
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
            // Autopilot clean run 46.1s, 17s of which is standing crouched waiting for the roof guard,
            // the overwatch pair and the cameras to look away; x1.4. LevelWalkthroughTest.
            timeTargetSeconds = 65.0f,
            description = "Security is active. Avoid guards and cameras to reach the conveyor belt.",
            objectiveHint = "Reach the Conveyor Belt",
            bonusObjective = BonusObjective.STAY_UNSEEN,
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
            // Autopilot clean run 116.9s (7680 units against a belt that nets 87 u/s is 88s before a
            // single laser is waited on); x1.4. Was 115, which no clean run could meet.
            timeTargetSeconds = 165.0f,
            description = "Search the moving conveyor belt for Container 17 while avoiding lasers.",
            objectiveHint = "Cross the Conveyor Line",
            bonusObjective = BonusObjective.USE_A_GADGET,
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
            // A physical body, like level 8's load (HookCrate.physical): cut loose it falls into
            // the gap, bounces off the terrain faces and the floor, and stays where it lands.
            val hookCrate = HookCrate(
                id = "hook_crate_1",
                hook = swingHook3,
                bounds = Rect(
                    x = hookEdgeX - hookCrateWidth / 2.0,
                    y = hook3GripY + hookRopeLength,
                    width = hookCrateWidth,
                    height = hookCrateHeight
                ),
                ropeLength = hookRopeLength,
                physical = true
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
            // Autopilot clean run 19.2s (two swings, one lever); short enough that a person needs a
            // flat 15s more, not x1.4, to line up the swing run-ups. LevelWalkthroughTest.
            timeTargetSeconds = 35.0f,
            description = "Container 17 is gone. Search the crane yard for signs of where it went.",
            objectiveHint = "Cross the Crane Yard",
            bonusObjective = BonusObjective.SWING_FROM_A_HOOK,
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
            // Autopilot clean run 64.5s: swing, pit under the overwatch crate, the boom, the gantry
            // climb timed to the swinging crate and the plank guard, the plank lever; x1.45.
            // LevelWalkthroughTest.
            timeTargetSeconds = 95.0f,
            description = "The records revealing Container 17’s location are kept in the security building. Find a way in.",
            objectiveHint = "Reach the Security Building",
            bonusObjective = BonusObjective.STAY_UNSEEN,
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
            val worldWidth = 6490.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            // Whole middle section corridor: top black beam in bglvl7.png is at Y=212.0
            // Height = (488.0 - 212.0) * (480.0 / 724.0) / 1.35 ≈ 136.0 world units
            val ceilingBottomY = 304.0
            val ventCeiling = Rect(x = 0.0, y = ceilingBottomY - 28.0, width = 6160.0, height = 28.0)
            // 80 further on than it was, to hold the security desk (see securityDesk below).
            val chamberBackWall = Rect(x = 6450.0, y = 200.0, width = 40.0, height = groundY - 200.0)

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

            // The level ends at the security desk ("Reach the Security Desk", 2026-09-30): desk.png,
            // the asset drop's silhouette of a desk under a 2x2 bank of monitors, cropped to its
            // alpha (2.075:1). 95 tall puts the desk top (47% down the art) at 45 - a desk beside
            // a 96-tall man - and it stands on the chamber floor past the duct's ceiling (6160),
            // clear of the "0m" stencil (6190, 43 wide) on its left. The trigger is in front of
            // the monitors' left half: walking up to the screens is what ends the level.
            val securityDesk = Rect(x = 6240.0, y = groundY - 95.0, width = 95.0 * 2.075, height = 95.0)
            val exitX = 6300.0

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
                manualCheckpoints = checkpoints,
                exitStructure = securityDesk,
                exitStructureImage = "desk.png"
            )
        }

        val DEFAULT_LEVEL_7 = LevelData(
            id = "level_7",
            name = "07: Service Tunnel",
            // Pace for a person, not the autopilot: its cautious walkthrough clears this in 107.7s
            // (testLevel7SimulationPlayableWalkthrough - waiting out every doubtful window and
            // holding for any drone that has spotted it), and star 3 is that x1.4. 6160 units is
            // 46.7s of pure walking. LevelWalkthroughTest pins the ratio for every shipped level.
            timeTargetSeconds = 155.0f,
            description = "Avoid the heavily guarded security room through the underground service tunnel.",
            objectiveHint = "Reach the Security Desk",
            bonusObjective = BonusObjective.DISABLE_ALL_SECURITY_BOTS,
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
         * **The hang line ([hangY]).** Three crates share one height: [overheadCrate],
         * [sweepCrate] and [platformCrate] all hang with their undersides exactly level with
         * [platform]'s own surface - "take these crates down to a level where their bottom is at
         * the level of the top of the platform" (2026-09-28). It used to be 62 above it, a
         * crouch-under gap; at 0 nothing ducks under any of the three any more, and what that
         * changes, load by load:
         *   - [platformCrate] was then lifted 24 back off that line (same day) so it cannot be
         *     jumped onto from the platform, only from the top of [sweepCrate] - see Section 3.
         *     It is still a solid block to anyone walking the platform, never a crouch gap.
         *   - [sweepCrate] still crushes a climb that it catches, and now also any JUMP taken
         *     under it: from the floor or from the cart deck a jumping head reaches the underside.
         *     Standing still under it is safe - a body on the cart deck is exactly 96 below it, so
         *     it passes flush over the head without touching.
         *   - [overheadCrate] is still 144 above the floor, far out of reach.
         *
         * **Section 1 - the plane.** Default start fences (`hasStartFences`, no explicit rects -
         * the same pair every other level gets), then the two loads from the original build,
         * pulled in hard toward the spawn on request: [overheadCrate] (long, stationary) now
         * starts at 430 instead of 760, and [platform] at 900 instead of 1560. Both loads hang on
         * level 2's own `chainedcrate.png` rigging. Neither can be stood on: `findClimbTarget`
         * refuses any box whose underside sits more than 4 above the climber's feet (a "floating
         * ledge" with no face to brace against), and with their undersides 144 above the floor
         * their tops are far past [Player.maxJumpHeight] (51.2) as well - so "player cant get on
         * top of these two" still needs no `unclimbableBoxes` entry.
         *
         * **Section 2 - the climb, and the crush.** [stepCrate] (68x48) stands flush against
         * [platform]'s left face: ground -> crate is a 48 jump (inside the 51.2 arc, so it is
         * jumped and never mantled), crate -> platform is exactly 96, this game's canonical climb.
         * 144 from the floor to the platform top is past [Player.climbMaxHeight] (115), so there
         * is no way up that skips the crate.
         *
         * **The squeeze (2026-09-28).** "he has to climb onto that and crouch and just as the
         * crate moves left from his position he has to climb up and quickly drop down from other
         * side. he should have just enough time to do that. the two crates should come very close
         * to each other and if he is in between them at that point mission should fail."
         * Then (same day): "raise this moving crate to the same level as the stationary one. make
         * that moving one and the next moving one meet at somewhat more to the right above the
         * platform" and "make level fail only if the cart or person touch the bottom side of the
         * crate".
         *   - [sweepCrate] hangs on the hang line with [overheadCrate]: flush over a standing head
         *     on the cart, flush on the platform's surface, and out 60 over the platform at its
         *     right end. It kills only through its underside (`crushesOnlyFromBelow`) - a jump or
         *     climb into it; a body standing flush under it, or walked into its side, is fine.
         *   - [platformCrate] (lifted 24, so not boardable from the platform) crosses the platform
         *     from 4 past sweepCrate's right end to 160 past the right lip.
         *   - Both run on one 8s period half a cycle apart: they meet 4 apart, 60 in over the
         *     platform, and anyone level with both between them is Mission Failed
         *     (`MovingPlatformDef.squeezes`). Then they part: climb as the sweep crate swings back
         *     past you, run 240 behind the crossing crate and walk off the right lip before it
         *     comes back - measured at 0.3s to spare (testLevel8TheSqueezeLeavesJustEnoughTime).
         *     There is nowhere on the platform to wait.
         * [platform] itself is 240 wide, down from 520 - "make the platform more shorter". For
         * level 9, the sweep crate -> crossing crate step is a short hop up.
         *
         * **Section 4 - the barrels and the bobbing chain (2026-09-28).** Three `barrel.png`
         * barrels (32x48) stand in a row on the ground against [platform]'s right face, and the
         * empty cart ([catchCart]) is parked 50 past them. Past that three short loads bob UP and
         * DOWN, from a top of 250 down to 6 off the floor, and all three crush through their
         * undersides - the player, and a cart they come down on; met side-on they are walls. Each peaks 3.39s after the one before it, the time a pushed cart
         * takes from one to the next, so the chain is a wave moving at cart speed: the cart has to
         * be pushed under all three in one go, started in a ~0.25s window of each 8s cycle. For
         * level 9 they are stepping stones - boarded only from another load, never from floor
         * level (see `noGroundBoarding` / GameWorld.isBoardingFromFloorLevel).
         *
         * **Section 5 - the long load.** [highCrate] is long and stationary, lifted 54 above
         * [highPlatform]'s top (242) with the swing hook and the deck load - "lift this long crate,
         * swing and the short moving crate after that". 160 under it: a standing body walks
         * underneath. It hangs 70 clear of bob 3 - a timed hop near the top of bob 3's stroke -
         * so the loads are a road from end to end; [LEVEL_9_LAYOUT] (the same yard, a different
         * start) runs the whole of it.
         *
         * **Section 6 - the lever, the travelling load, and the catch.** [dropLever] stands on the
         * floor under the long load. [dropCrate] hangs on a rope from [dropHook], and the whole rig
         * travels left and right between the long load and [highPlatform] (`HookCrate.sweepX`).
         * The load is a physical body ([HookCrate.physical], [BoxPhysics]): on the rope it swings
         * as the rig speeds up and slows; cut loose by the lever it leaves with the rig's speed
         * and its own swing, tumbles, and bounces off whatever it strikes - the cart's posts, the
         * deck's edge, the floor. The empty cart ([catchCart], `startsEmpty`) - a deck between two
         * handle posts, no step at all; a body can jump into it but not walk through it
         * (`PushCart.playerSolids`) - arrives under it from the barrels (Section 4). A
         * load that comes to rest flat on the deck between the posts is caught and rides in the
         * cart, which is then the same full 48 step as the first cart: walk it right, flush against
         * [highPlatform], and it is 48 onto it and the canonical 96 up. A miss stays wherever it
         * comes to rest - no re-hang, no reset; a player who cannot get round it restarts. The
         * cart takes a hit too: struck, it rolls and stops, and a loose load lying in its way stops
         * it. The rig's right end stops 68 short of the platform, so a cart already parked there
         * can never catch - "after catching player has to move it to right". A falling load kills
         * whoever it comes down on. Once empty, the hook is a swing hook: off the long load, the
         * swing comes down on the deck load (level 9's road).
         *
         * **Section 7 - the pole camera.** [poleCamera] stands on [pole], on the floor against
         * [highPlatform]'s left face, and parks, ~19s at a time (a 40s cycle), on the lever and on the long load's
         * top. Its 20-degree cone never covers both, and while it watches the long load the floor
         * is dark: the cart, the lever and the loaded cart are each worked in one of those parks.
         * Its range ends just past the lever, so everything left of the long load is out of reach.
         *
         * **Section 8 - across the high platform.** [deckCrate] sweeps from out over the gap (where
         * the swing lands) to 60 past the platform's far lip, at chest height on the platform - a
         * solid block with nowhere to wait it out. Level 8 climbs up just after it has left, follows
         * it out, and drops off the lip to the floor, where it passes overhead.
         *
         * **Section 9 - one long table, a laser course above and below.** Level 3's table on the
         * lifted line, 3050 long, drawn as one continuous slab (`LevelLayout.seamlessTables`) with
         * a single washed-out, pass-through leg at its far corner. Level 9 runs the top course on
         * it; level 8 runs the bottom course on the floor under it. Every beam runs surface to
         * surface (floor to underside, top to off the top of the screen), so every beam is a gate
         * on a cycle - upright or slanted, alone or in staggered runs - and each course has three
         * of level 7's security bots between the runs: take the beams before one while it has its
         * back turned, then catch it from behind and switch it off. The level ends at Container 17
         * (container17.png), on the floor under the table's far end with the table running on past
         * it; its trigger reaches from the floor up over the table, so level 8 walks into it below
         * and level 9 above.
         *
         * Falling off costs nothing anywhere here - the ground runs the level's full width - so
         * the ways to fail are the crushers (the sweep load and the bobbing chain, on the player or
         * on the cart), the squeeze, a falling load, the lasers, the bots and the camera.
         */
        val LEVEL_8_LAYOUT = run {
            val groundY = 440.0

            // --- The shared hang line: see the class doc. Every crate that hangs over the first
            // half of the level puts its UNDERSIDE here, level with the platform's own surface.
            val hangingCrateHeight = 38.0
            val platformTop = 296.0
            val hangY = platformTop - hangingCrateHeight
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
            // Flush against the platform. Reached from cartRestX, this is ~148 units of pushing -
            // under three seconds of the braced gait at GameWorld.PUSH_MOVE_FACTOR's ~53 u/s.
            val cartMaxX = platformLeft - cartWidth
            // Parked in clear ground just past the plane's hanging load (430..604 - well overhead,
            // but this is where the player is looking), so the cart is read as an object in the way
            // of nothing until the platform explains it. It was 560, half under that load, then
            // 610, and 660 on request ("move the cart at beginning little more to the right",
            // 2026-09-28) - under the sweep crate's span, which passes over it at crouch height.
            val cartRestX = 660.0
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

            // "he has to climb onto that and crouch and just as the crate moves left from his
            // position he has to climb up and quickly drop down from other side ... the two crates
            // should come very close to each other and if he is in between them at that point
            // mission should fail" (2026-09-28), then "raise this moving crate to the same level
            // as the stationary one. make that moving one and the next moving one meet at somewhat
            // more to the right above the platform". On the hang line, like the overhead crate: its
            // underside is flush with a standing head on the cart, and with the platform's surface,
            // so it runs out [sweepCrateReach] over the platform, where the two meet. The climb
            // goes the moment it has swung back left past the body on the cart.
            //
            // 260 of travel, not the 200 it had: from 60 over the platform it has 96 to go to clear
            // a body at the cart's far end, and the faster it swings the sooner that is - which is
            // what leaves the run across the platform its time. Its left end stops 20 short of the
            // overhead crate, which hangs on the same line.
            val sweepCrateReach = 60.0
            val sweepCrateMaxX = platformLeft + sweepCrateReach - shortCrateWidth
            val sweepCrateSweep = 260.0
            val sweepCrateMinX = sweepCrateMaxX - sweepCrateSweep
            // Long enough that the guaranteed-clear phase outlasts a 1.95s climb with room to step
            // out from under afterwards (see the class doc), and slow enough to be read from back
            // down the plane. platformCrate runs on this same period - see Section 3.
            val sweepCratePeriod = 8.0
            val sweepCrate = MovingPlatformDef(
                id = "lvl8_sweep_crate",
                initialX = sweepCrateMinX,
                y = hangY,
                width = shortCrateWidth,
                height = hangingCrateHeight,
                minX = sweepCrateMinX,
                maxX = sweepCrateMaxX,
                periodSeconds = sweepCratePeriod,
                // Free-running off the level clock (no startsInactive - the only lever here is
                // the one on dropCrate, unlike level 6's gantry), starting at the far LEFT end.
                phaseOffsetSeconds = 0.0,
                // The short chainedcrate2.png crop, matching level 2's own short containers -
                // "first crate is a long one and then next is a short one that moves".
                isVariant1 = false,
                // "touching the bottom side of that crate when it is near the platform ends the
                // level". See MovingPlatformDef.crushesOnContact - it only bites from below.
                crushesOnContact = true,
                crushesOnlyFromBelow = true,
                noGroundBoarding = true,
                squeezes = true
            )

            // --- SECTION 3: the crossing. The level's second short moving load, riding just
            // above the platform's surface.
            //
            // Lifted 24 off the hang line (2026-09-28): "lift this crate up a little bit so that
            // player cant jump from the platform to the crate but can jump from the previous crate
            // to this crate". From the platform its top is 38 + 24 = 62 up - past maxJumpHeight
            // (51.2) - and its underside is 24 above the feet, so findClimbTarget's floating-ledge
            // rule refuses the mantle too. From the top of sweepCrate it is a 24-unit hop up. 24
            // rather than the ~14 that would already do it, so the refusal has margin. Its
            // underside (272) still sits far under a standing head on the platform (200) - it is
            // still a block, never a crouch gap - and 120 over the barrels.
            val platformCrateLift = 24.0
            // Left end: the squeeze. Both crates run on ONE period, half a cycle apart, so they
            // close on each other exactly once per cycle - sweepCrate at its right end as this one
            // reaches its left - and at that moment they are 4 apart, corner to corner over the
            // landing: anyone between them is Mission Failed (MovingPlatformDef.squeezes). Then
            // they part, this one heading out past the right lip, and that is the window: the
            // climb (1.95s), the 240 across, and off the right lip before it comes back.
            // For level 9 it is a 24-unit hop up from sweepCrate's top.
            val sweepCrateMaxRight = sweepCrateMaxX + shortCrateWidth
            val crateToCrateGap = 4.0
            val platformCrateMinX = sweepCrateMaxRight + crateToCrateGap
            // Right end: far past the lip, so it clears the platform for long enough that a body
            // that climbed the moment sweepCrate let it can walk off the right lip before this
            // comes back - with a few tenths of a second to spare, no more ("he should have just
            // enough time to do that"). Measured by testLevel8TheSqueezeLeavesJustEnoughTime.
            val platformCrateOverswing = 160.0
            val platformCrateMaxX = platform.right + platformCrateOverswing
            val platformCrate = MovingPlatformDef(
                id = "lvl8_platform_crate",
                // Half a cycle out of step with sweepCrate, which starts at its far left - so
                // this one starts at its far right.
                initialX = platformCrateMaxX,
                y = hangY - platformCrateLift,
                width = shortCrateWidth,
                height = hangingCrateHeight,
                minX = platformCrateMinX,
                maxX = platformCrateMaxX,
                periodSeconds = sweepCratePeriod,
                phaseOffsetSeconds = sweepCratePeriod / 2.0,
                isVariant1 = false,
                // Not a crusher from below; the squeeze against sweepCrate is what ends a run.
                crushesOnContact = false,
                noGroundBoarding = true,
                squeezes = true
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

            // --- SECTION 4b: the bobbing chain (2026-09-28). Three short loads going UP and DOWN,
            // which are now the high road rather than a crouch gauntlet: "make these two crates
            // lift up and down more and it is possible to jump from the previous moving crate to
            // this but not too easy ... add another similar crate moving up and down before the
            // long crate after that and space those three so that it is possible to jump from one
            // to other and finally to the long crate. also make those 3 crates move very close to
            // the ground when they move down".
            //
            // Each top travels from [bobTopHighest] (level with nothing in particular - just high
            // enough that platformCrate's own 234 is a short drop onto it) down to [bobTopLowest],
            // which leaves [bobLowClearance] between the underside and the floor: nothing fits
            // under a load at the bottom of its stroke, and all three crush. The ground road
            // under them is a timed walk, not a crawl.
            //
            // "dont let them jump onto them. if they are in jumpable height, artificially block
            // getting onto them": at the bottom of the stroke the tops are 44 above the floor,
            // inside a jump, so every one is `noGroundBoarding` - see GameWorld's
            // isBoardingFromFloorLevel. They are boarded only from another load.
            //
            // One shared period, commensurate with the sweep pair's 8s, and a fixed lag between
            // neighbours - so every hop opens at the same point of every cycle. The gaps and the
            // lag were chosen by simulating each hop at 160 phases of a cycle, standing and
            // running - see testLevel8EveryHopOnTheHighRoadOpensForAShortWindowEveryCycle.
            val bobLowClearance = 6.0
            val bobTopHighest = 250.0
            val bobTopLowest = groundY - bobLowClearance - hangingCrateHeight
            val bobPeriod = sweepCratePeriod
            // Each load peaks a quarter-cycle after the one before it, so for part of every cycle
            // the next one is enough lower to be reached - and the gaps are wide enough that only
            // that part works. Measured with a running jump taken from the very edge while still
            // standing on it: platformCrate -> bob 1 ~0.95s
            // per 8s cycle, bob -> bob ~1.0s, bob 3 -> long crate ~1.5s. Every one is steep - bob
            // -> bob is open 3.3s at 90 and 1.65s at 100 - so move these only with a re-run of
            // testLevel8EveryHopOnTheHighRoadOpensForAShortWindowEveryCycle.
            // Each load peaks exactly as long after the one before it as a pushed cart takes to go
            // from one to the next ((76 + 103) at the braced gait's ~53 u/s = 3.39s), so the chain
            // is a wave that travels at cart speed: a cart started under bob 1 at the right moment
            // meets bob 2 and bob 3 at the same point of their strokes (level 8 now pushes the
            // empty cart the whole way from the barrels - see Section 6). It was 2.0; with that
            // there was nowhere under the chain for a cart to stop and no single start that worked.
            val bobGap = 103.0
            val bobLag = (shortCrateWidth + bobGap) / (132.0 * GameWorld.PUSH_MOVE_FACTOR)
            val firstBobGap = 88.0
            fun bobCrate(id: String, x: Double, highAt: Double) = MovingPlatformDef(
                id = id,
                initialX = x,
                y = bobTopHighest,
                width = shortCrateWidth,
                height = hangingCrateHeight,
                minY = bobTopHighest,
                maxY = bobTopLowest,
                periodSeconds = bobPeriod,
                // t = 0 (the top of the stroke) falls at level time [highAt].
                phaseOffsetSeconds = (bobPeriod - highAt % bobPeriod) % bobPeriod,
                isVariant1 = false,
                crushesOnContact = true,
                crushesOnlyFromBelow = true,
                noGroundBoarding = true
            )
            val bob1X = platformCrateMaxX + shortCrateWidth + firstBobGap
            val bobCrate1 = bobCrate("lvl8_bob_crate_1", bob1X, 0.0)
            val bobCrate2 = bobCrate("lvl8_bob_crate_2", bob1X + shortCrateWidth + bobGap, bobLag)
            val bobCrate3 = bobCrate("lvl8_bob_crate_3", bob1X + 2.0 * (shortCrateWidth + bobGap), 2.0 * bobLag)

            // --- SECTION 5: the long load, the swing, and the deck load all hang [liftedLine] up:
            // "lift this long crate, swing and the short moving crate after that and after the
            // swing the player should land on that short moving crate" (2026-09-28). The swing
            // lands at exactly the height it leaves from (Player.findSwingTarget), so the long
            // load's top and the deck load's top share one line.
            //
            // 242 - 54 above the high platform - and no higher, because a phone shows the world
            // only down from about y 130: a body standing up here has its head at 146. And no
            // lower either: 54 is past a jump (Player.maxJumpHeight 51.2), so the deck load
            // cannot be hopped onto from the high platform - only the swing boards it. Up there it
            // is a block at chest height that level 8 has to follow out (Section 8); over the
            // lower table, 84 further down, it clears a standing head by 4.
            //
            // Under the long load is 160 of headroom: the ground road to the lever walks under.
            //
            // 70 clear of bob 3 (2026-09-29: "it is not possible to make this jump. move the rest
            // of the level to left to fix this" - it was 150, with a crate stack in the gap on
            // level 9 only). Everything from here on is placed off the long load, so the whole
            // rest of the yard moved left with it. The hop off bob 3 is 8 up and only lands near
            // the top of bob 3's stroke - a ~1.75s window per 8s cycle, like the bob-to-bob hops
            // (testLevel8EveryHopOnTheHighRoadOpensForAShortWindowEveryCycle); at 80 it never lands.
            val liftedLine = platformTop - 54.0
            val highCrateGap = 70.0
            val highCrate = Rect(
                x = bob1X + 2.0 * (shortCrateWidth + bobGap) + shortCrateWidth + highCrateGap,
                y = liftedLine,
                width = longCrateWidth,
                height = hangingCrateHeight
            )

            // --- SECTION 6: the lever, the travelling load, and the empty cart that catches it.
            // "move this hanging crate up a little bit and make it move left and right. remove the
            // crate at the bottom and replace it with a empty cart. move the lever to under the
            // long crate" - and, asked afterwards, the cart "catches the crate. after catching
            // player has to move it to right to get on the platform".
            // Under the long load's right half, not its middle (2026-09-29): the camera's reach
            // ends just past the lever, so this is what leaves the load's near end out of it - the
            // one place on level 9's road to wait for the camera to look away before the swing.
            // 70 in from the far end, not less: any nearer and it stands where the body lets go
            // of the catch cart, and the one INTERACT would do both.
            val leverWidth = 22.0
            val leverHeight = 12.0
            val dropLever = Lever(
                id = "lvl8_drop_lever",
                x = highCrate.right - 70.0 - leverWidth / 2.0,
                y = groundY - leverHeight,
                width = leverWidth,
                height = leverHeight,
                targetMechanismId = "lvl8_hook_crate"
            )

            // --- SECTION 7's platform first: the swing and the catch are both placed against it.
            // 180 clear of the long crate: far past a plain jump (~78), so the only way across up
            // top is the hook.
            val highPlatformGap = 180.0
            val highPlatformLeft = highCrate.right + highPlatformGap
            val highPlatform = Rect(
                x = highPlatformLeft,
                y = platformTop,
                width = 300.0,
                height = groundY - platformTop
            )

            // The load is as tall as the cart's deck is deep (PushCart.DECK_TOP_FRACTION), so once
            // caught the cart stands exactly like the loaded one at the start of the level: 48 up
            // onto it, then the same 96 mantle. It is 56 wide against the 71 between the handle
            // posts - a real crate has to fit through the gap it falls into. Measured by dropping
            // it at every frame of the rig's cycle over a parked cart: two windows a cycle land it
            // clean; anything else strikes a post or the deck's edge and goes over. See
            // HookCrate.physical.
            val dropCrateWidth = 56.0
            val dropCrateHeight = cartHeight * PushCart.DECK_TOP_FRACTION
            // The hook is lifted with the long crate, so its grip point (176) stays 66 above feet
            // on the long crate - inside the swing's 60..150 grip window. The load is NOT: it
            // hangs where it always did (top 270) on a rope 54 longer. Lifted with the hook it
            // fell 54 further, drifted further, and started landing in a cart already flush
            // against the platform; the same fall as before keeps the catch as measured.
            val dropCrateTop = 270.0
            val dropRopeLength = 40.0 + (platformTop - liftedLine)
            // The rig's travel. Left end just clear of the long crate; right end short enough that
            // the load can never come down in a cart already parked flush against the platform -
            // the catch has to happen further left, and the cart then walked the rest of the way.
            // 68 short, not the 30 a hanging load needs: a load cut loose mid-sweep carries the
            // rig's speed and its swing, and lands well right of where it hung. Measured by
            // dropping it at every frame of a cycle over a flush cart: on the 94 rope, 60 short
            // still let 3 frames in 240 land in it; 68, none (on the old 40 rope 60 was enough).
            val dropCrateMinX = highCrate.right + 8.0
            val dropCrateMaxX = highPlatformLeft - 68.0 - dropCrateWidth
            val dropCrateBounds = Rect(
                x = dropCrateMinX,
                y = dropCrateTop,
                width = dropCrateWidth,
                height = dropCrateHeight
            )
            // hook.png, hung so its grip point sits exactly one rope-length above the crate - the
            // same construction level 5 uses for its own hook crate.
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
                ropeLength = dropRopeLength,
                sweepX = dropCrateMaxX - dropCrateMinX,
                sweepPeriodSeconds = 4.0,
                noGroundBoarding = true,
                physical = true
            )

            // The empty cart. It starts by the barrels, 70 clear of them so a body braced in
            // behind it has its back foot clear of the last barrel ("move that empty cart near to
            // the barrels to the far left", then "a little forward otherwise foot seems to go in
            // front of barrels", 2026-09-29), and has
            // to be pushed under the whole bobbing chain to the load - a bob that comes down on it
            // is Mission Failed (GameWorld's cart check), which is why the chain is a wave at cart
            // speed. Its forward limit is flush against highPlatform, the same as the first cart's
            // against the mid platform. Empty, only its deck and the lower half of its handle posts
            // are solid to a body (PushCart.playerSolids): it cannot be walked through, and can be
            // jumped into.
            val catchCartMinX = barrels.maxOf { it.right }
            val catchCartMaxX = highPlatformLeft - cartWidth
            val catchCart = PushCartDef(
                id = "lvl8_catch_cart",
                initialX = catchCartMinX + 70.0,
                surfaceY = groundY,
                width = cartWidth,
                height = cartHeight,
                minX = catchCartMinX,
                maxX = catchCartMaxX,
                startsEmpty = true
            )

            // --- SECTION 8's deck load first: the camera's pole is placed clear of it.
            // A short load on the lifted line. Its near end reaches back out over the gap to where
            // the swing off the rig comes down (Player.swingLandAhead past the grip); its far end
            // is 60 past the high platform's lip. Level 9 swings onto it at the near end and rides
            // it out to the upper table. Level 8 never boards it (54 up is past a jump), and on
            // the high platform it is a solid block at chest height that sweeps the whole
            // platform - there is nowhere up there to wait it out - so level 8 climbs up just
            // after it has left the near end, follows it out, and drops off the lip onto the lower
            // table (84 down, where it then passes overhead) before it comes back. 20s, not the 8 the other loads use:
            // at 8, and at 12, it came back before a body following it could get off the lip
            // (walking off an edge drops at Player.dropSpeed, 30 across); at 20 the climb can start
            // anywhere in a ~4.7s window after it leaves. A multiple of the rig's 4s, so the
            // swing's timing recurs - and half the pole camera's 40s, so every other time it comes
            // in the camera is watching the long load (level 8's climb behind it) and every other
            // time it is watching the lever (level 9's swing onto it) - see poleCamera.
            val deckCrateMinX = highPlatformLeft - 10.0
            val deckCrateMaxX = highPlatform.right + 60.0
            val deckCratePeriod = 20.0
            val deckCrate = MovingPlatformDef(
                id = "lvl8_deck_crate",
                initialX = deckCrateMinX,
                y = liftedLine,
                width = shortCrateWidth,
                height = hangingCrateHeight,
                minX = deckCrateMinX,
                maxX = deckCrateMaxX,
                periodSeconds = deckCratePeriod,
                // t = 0 (the near end) at level time 6 - when the rig (4s, starting at its near
                // end) is at its far end, which is where the swing onto it is taken from. 6, not
                // the first such moment at 2: it puts the near end at 6 + 20k, so the climb behind
                // it (level 8, ~9-13s) falls in the camera's park on the long load (1-20s of each
                // 40) and the swing onto it at 26 in its park on the lever (21-40s).
                phaseOffsetSeconds = deckCratePeriod - 6.0,
                isVariant1 = false,
                crushesOnContact = false,
                noGroundBoarding = true
            )

            // --- SECTION 7: the pole and its camera ---
            // The pole stands on the FLOOR, against the high platform's left face and just left of
            // the deck load's near end: on the platform (where it used to stand) the lifted deck
            // load would sweep straight through it. Its top is 34 over the head of a body standing
            // on the loaded cart, so the lens looks out over it.
            val poleWidth = 18.0
            val poleTop = 262.0
            val pole = Rect(
                x = deckCrateMinX - 18.0 - poleWidth,
                y = poleTop,
                width = poleWidth,
                height = groundY - poleTop
            )
            // Same mount convention as LEVEL_3's pole camera: lens hung at the pole's own top
            // cap, nudged 10 left of the pole's centre so the bracket sits on the collar.
            val cameraEyeX = pole.x + poleWidth / 2.0 - 10.0
            val cameraEyeY = pole.y
            // It parks, in turn, on the lever and on the long crate's top. The ground road works
            // the lever and both carts while it is parked on the crate - it then looks up and
            // left, and everything under its lens is dark. (Two other mounts were tried and
            // dropped: parked on two spots of the floor, a camera sweeps everything between them,
            // and the lever and the catch are both in that stretch - the ground road had nowhere
            // to stand.) Level 9 has the same camera - see LEVEL_9_LAYOUT.
            val leverAngle = atan2(dropLever.centerY - cameraEyeY, dropLever.centerX - cameraEyeX)
            val crateTarget = Vec2d(highCrate.right - 60.0, highCrate.top - 48.0)
            // Up and to the left: atan2 says -162 degrees; +360 keeps the sweep a short arc up
            // from the lever's angle rather than the long way round.
            val crateAngle = atan2(crateTarget.y - cameraEyeY, crateTarget.x - cameraEyeX)
                .let { if (it < leverAngle) it + 2.0 * PI else it }
            val poleCamera = CameraSpawn(
                x = cameraEyeX,
                y = cameraEyeY,
                minAngle = leverAngle,
                maxAngle = crateAngle,
                startAngle = leverAngle,
                sweepSpeed = 0.9,
                // Just past the lever - the farther of its two targets - and no further, so the
                // plane and the bobbing chain stay out of its reach altogether, and so does the
                // long load's near end (see dropLever).
                visionRange = kotlin.math.hypot(dropLever.centerX - cameraEyeX, dropLever.centerY - cameraEyeY) + 6.0,
                // 20, not the 40-50 the other levels use: narrow enough to leave the floor dark
                // while it watches the crate.
                visionFov = 20.0 * (PI / 180.0),
                // Each park is 20s less the ~1s sweep, so a whole cycle is exactly 40s - twice the
                // deck load's 20 (2026-09-29). It was 7s (a ~16.1s cycle), which drifted against
                // the deck load and left level 9 standing on the long load's dark end for up to
                // ~100s waiting for the camera on the lever and the swing's slot to coincide; now
                // they coincide every 40s. Long parks: the ground road's jobs each get ~19s.
                sweepPauseDuration = 20.0 - (crateAngle - leverAngle) / 0.9
            )

            // --- SECTION 9: the table and its two laser courses (2026-09-28).
            // "remove the whole section after that platform. add the platform type in level 3
            // begging to that section and make it much longer ...", then (same day) "remove the
            // bottom platform here. he should walk on ground at the bottom platform ... only add 1
            // vertical pole at rightmost corner ... make it one continuos platform ... make the
            // laser course double the length".
            //
            // One long table (level 3's table.png) on the lifted line (242), starting 30 past the
            // deck load's far end: level 9 steps across onto it from the deck load and runs the
            // top course on it; level 8 drops off the high platform to the floor and runs the
            // bottom course UNDER it (168 of headroom). It is drawn as one continuous slab - the
            // art's repeating unit tiled end to end, capped at both ends (GameplayScene,
            // LevelLayout.seamlessTables) - with its one leg at the far corner, washed out like the
            // camera's pole and solid to nothing (LevelLayout.passThroughLegs).
            //
            // Every beam runs from one surface to another: floor to the table's underside below,
            // the table's top to off the top of the screen above ("lasers should be connected to
            // ground or the top or bottom of the platform"). So every beam is a gate on a cycle -
            // upright or slanted, alone or in a staggered run whose gaps have to be chased - and
            // each course has three of level 7's security bots on laser-free stretches between the
            // runs: take the last beams before one while it has its back turned, then catch it
            // from behind and switch it off (INTERACT). Each patrol starts 122+ past where a body
            // stands clear of the beam before it - somewhere out of its 120 cone to wait.
            val tableDepth = 30.0
            val upperTable = Rect(
                x = deckCrateMaxX + shortCrateWidth + 30.0,
                y = liftedLine,
                width = 3050.0,
                height = tableDepth
            )
            // table.png's leg crop is 178 of the art's 102-tall slab band wide: at a 30-deep slab
            // that is 52.4, the width the scene gives the slab's right-hand end cap it sits under.
            val tableLeg = Rect(
                x = upperTable.right - tableDepth * 178.0 / 102.0,
                y = upperTable.bottom,
                width = tableDepth * 178.0 / 102.0,
                height = groundY - upperTable.bottom
            )
            // The level ends at Container 17 ("replace the level end asset in that level with
            // container17.png", 2026-09-29), standing on the floor under the table's far end - the
            // table runs on 30 past it ("increase the length of that platform to go beyond the
            // level end asset", then "reduce the size ... move the container more right", then "too
            // small now. use a size only a little amount smaller than what was before"). 105 tall
            // (it was 120), 3.575:1 as the art is (2020x565 after cropping to its alpha). The
            // trigger is a strip running from the floor up past the table's top, so level 8 walks
            // into it along the floor and level 9 along the table - neither has to leave the
            // surface it is on - and it starts [exitApproach] short of the container: the level
            // ends as the body gets close to it, not when it touches it.
            val containerHeight = 105.0
            val exitApproach = 70.0
            val container17 = Rect(
                x = upperTable.right - 30.0 - containerHeight * (2020.0 / 565.0),
                y = groundY - containerHeight,
                width = containerHeight * (2020.0 / 565.0),
                height = containerHeight
            )
            val exitZone = Rect(
                x = container17.x - exitApproach,
                y = upperTable.top - 110.0,
                width = exitApproach + 44.0,
                height = groundY - (upperTable.top - 110.0)
            )

            // A gate: on for [on]s, off for [off]s, [phase] into its cycle; [slant] units across.
            val lowC = upperTable.bottom
            val lowS = groundY
            val lowX = upperTable.x + 60.0
            fun lowGate(id: String, dx: Double, on: Double, off: Double, phase: Double, slant: Double = 0.0) =
                LaserDef(id = "lvl8_low_$id", topX = lowX + dx, topY = lowC, bottomX = lowX + dx + slant,
                    bottomY = lowS, activeDuration = on, inactiveDuration = off, phaseOffsetSeconds = phase, emitterScale = 0.6)
            fun courseBot(id: String, x0: Double, x1: Double, surface: Double, facing: Double) = CameraBotDef(
                id = id,
                startX = if (facing > 0.0) x0 else x1,
                surfaceY = surface,
                patrolMinX = x0,
                patrolMaxX = x1,
                facing = facing
            )
            // Four runs of beams, a bot after each of the first three.
            val bottomCourse = listOf(
                lowGate("g1", 0.0, 1.6, 1.2, 0.0),
                lowGate("g2", 70.0, 1.6, 1.2, -0.4),
                lowGate("g3", 140.0, 1.6, 1.2, -0.8),
                lowGate("t1", 240.0, 1.4, 1.4, -0.7, slant = 50.0),
                lowGate("g4", 360.0, 1.0, 1.4, 0.0),
                lowGate("g5", 680.0, 0.9, 0.9, 0.0),
                lowGate("g6", 730.0, 0.9, 0.9, -0.3),
                lowGate("g7", 780.0, 0.9, 0.9, -0.6),
                lowGate("t2", 880.0, 1.2, 1.4, 0.0, slant = 60.0),
                lowGate("g8", 1010.0, 1.2, 1.0, 0.0),
                lowGate("g9", 1060.0, 1.2, 1.0, -0.5),
                lowGate("g10", 1380.0, 0.8, 0.9, 0.0),
                lowGate("g11", 1430.0, 0.8, 0.9, -0.35),
                lowGate("t3", 1520.0, 1.2, 1.2, -0.4, slant = 50.0),
                lowGate("g12", 1650.0, 1.6, 1.0, 0.0),
                lowGate("g13", 1700.0, 1.6, 1.0, -0.5),
                lowGate("g14", 1750.0, 1.6, 1.0, -1.0),
                lowGate("g15", 2070.0, 0.8, 0.8, 0.0),
                lowGate("g16", 2120.0, 0.8, 0.8, -0.3),
                lowGate("t4", 2210.0, 1.3, 1.3, -0.6, slant = 50.0),
                lowGate("g17", 2340.0, 1.2, 1.0, 0.0)
            )
            val bottomBots = listOf(
                courseBot("lvl8_low_bot1", lowX + 530.0, lowX + 600.0, lowS, -1.0),
                courseBot("lvl8_low_bot2", lowX + 1230.0, lowX + 1300.0, lowS, 1.0),
                courseBot("lvl8_low_bot3", lowX + 1920.0, lowX + 1990.0, lowS, -1.0)
            )

            // The top course - level 9's - on the table, each beam from its top up off the top of
            // the screen.
            val topS = upperTable.top
            // 140 in: the step across from the deck load lands ~50 onto the table.
            val topX = upperTable.x + 140.0
            fun topGate(id: String, dx: Double, on: Double, off: Double, phase: Double, slant: Double = 0.0) =
                LaserDef(id = "lvl9_top_$id", topX = topX + dx, topY = -300.0, bottomX = topX + dx + slant,
                    bottomY = topS, activeDuration = on, inactiveDuration = off, phaseOffsetSeconds = phase, emitterScale = 0.6)
            val topCourse = listOf(
                topGate("g1", 0.0, 1.4, 1.0, 0.0),
                topGate("g2", 60.0, 1.4, 1.0, -0.35),
                topGate("t1", 150.0, 1.2, 1.4, 0.0, slant = 80.0),
                topGate("g3", 300.0, 0.9, 1.0, -0.5),
                topGate("g4", 620.0, 1.2, 1.2, 0.0),
                topGate("g5", 670.0, 1.2, 1.2, -0.4),
                topGate("g6", 720.0, 1.2, 1.2, -0.8),
                topGate("t2", 820.0, 1.2, 1.4, 0.0, slant = 80.0),
                topGate("g7", 990.0, 0.8, 0.9, 0.0),
                topGate("g8", 1040.0, 0.8, 0.9, -0.35),
                topGate("g9", 1360.0, 1.0, 1.0, 0.0),
                topGate("t3", 1450.0, 1.2, 1.2, -0.6, slant = 80.0),
                topGate("g10", 1600.0, 0.9, 0.9, -0.3),
                topGate("g11", 1650.0, 0.9, 0.9, -0.6),
                topGate("g12", 1700.0, 0.9, 0.9, -0.9),
                topGate("g13", 2030.0, 1.4, 1.0, 0.0),
                topGate("t4", 2120.0, 1.2, 1.4, -0.7, slant = 80.0),
                topGate("g14", 2270.0, 0.8, 0.8, 0.0),
                topGate("g15", 2320.0, 0.8, 0.8, -0.3)
            )
            val topBots = listOf(
                courseBot("lvl9_top_bot1", topX + 470.0, topX + 540.0, topS, 1.0),
                courseBot("lvl9_top_bot2", topX + 1210.0, topX + 1280.0, topS, -1.0),
                courseBot("lvl9_top_bot3", topX + 1880.0, topX + 1950.0, topS, 1.0)
            )

            val worldWidth = exitZone.right + 300.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            // The carts are deliberately absent: their footprints move, so GameWorld adds them to
            // the solid/climbable/sight-blocking sets per tick from wherever they currently stand.
            val boxes = listOf(overheadCrate, platform) +
                barrels +
                listOf(highCrate, highPlatform, upperTable)

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = exitZone,
                platforms = listOf(ground),
                boxes = boxes,
                guards = emptyList(),
                cameras = listOf(poleCamera),
                // The long stationary loads take level 2's long chainedcrate.png crop; the moving
                // ones pick their art up from MovingPlatformDef.isVariant1 instead.
                hangingCrateVariant1 = listOf(overheadCrate, highCrate),
                barrels = barrels,
                poles = listOf(pole),
                exitStructure = container17,
                exitStructureImage = "container17.png",
                tables = listOf(upperTable),
                seamlessTables = listOf(upperTable),
                passThroughLegs = listOf(tableLeg),
                movingPlatforms = listOf(sweepCrate, platformCrate, bobCrate1, bobCrate2, bobCrate3, deckCrate),
                pushCarts = listOf(
                    PushCartDef(
                        id = "lvl8_step_cart",
                        initialX = cartRestX,
                        surfaceY = groundY,
                        width = cartWidth,
                        height = cartHeight,
                        minX = cartMinX,
                        maxX = cartMaxX
                    ),
                    catchCart
                ),
                // The hook is a swing hook now (once the lever has emptied it), drawn with the
                // travelling rig rather than in the static hook pass.
                swingHooks = listOf(dropHook),
                levers = listOf(dropLever),
                hookCrates = listOf(dropCrate),
                lasers = bottomCourse + topCourse,
                cameraBots = bottomBots + topBots,
                // "you cant touch the ground / platforms" - the floor, and the two platforms. The
                // tables are not on it: level 9 runs its course on the upper one.
                offLimitFootholds = listOf(platform, highPlatform)
            )
        }

        val DEFAULT_LEVEL_8 = LevelData(
            id = "level_8",
            name = "08: Relocation",
            // The ground road's walkthrough (testLevel8IsBeatableOnTheGroundRoad) finishes in ~130s:
            // the crouch-and-climb under the sweep crate, the cart pushed under the bobbing chain,
            // the lever and the loaded cart each worked only while the camera is parked on the long
            // crate (up to a 16s camera cycle apiece), the climb behind the deck load, and the
            // double-length laser course with its three bots. 180 (three minutes, the owner's call
            // for both 8 and 9, 2026-09-29) is that x1.38 - room for a misread or two.
            timeTargetSeconds = 180.0f,
            description = "The guards moved Container 17. Follow the trail to its new location.",
            objectiveHint = "Track Down Container 17",
            bonusObjective = BonusObjective.NEVER_TOUCH_A_HANGING_CRATE,
            layout = LEVEL_8_LAYOUT,
            hangingLoadsOffLimits = true,
            // Level 9 replays this run - see DEFAULT_LEVEL_9.
            recordsRun = true,
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
                    triggerMaxX = 700.0,
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

        /**
         * Level 9's yard: [LEVEL_8_LAYOUT] exactly - camera, pole and all - with only the start
         * moved ("make level 8 and level 9 100% identical. only the starting position is changed",
         * 2026-09-29). The spawn is ON the long load over the plane - "start on the hanging crate
         * on the top" - and the road runs along the loads: the bobbing chain, the hop off bob 3 onto
         * the long load, the swing off the travelling hook onto the deck load, the ride out to the
         * table, its laser course, and the exit. Level 9 fails the moment the floor or a platform
         * is touched ([LevelData.stayOffTheGround]).
         *
         * The travelling hook carries its load until the lever on the floor drops it - which the
         * echo does: level 8's recorded run, played back (DEFAULT_LEVEL_9.replaysRunOf,
         * EchoRunner). The swing opens when his earlier self pulls the lever.
         */
        val LEVEL_9_LAYOUT = run {
            val base = LEVEL_8_LAYOUT
            val overheadCrate = base.hangingCrateVariant1.minBy { it.x }
            base.copy(
                playerStartX = overheadCrate.left + 40.0,
                playerStartY = overheadCrate.top - 96.0
            )
        }

        /**
         * Level 9: the same yard as level 8, run along the roof of it - see [LEVEL_9_LAYOUT].
         *
         * The main objective is "never touch the ground" ([stayOffTheGround]): the floor or either
         * platform is Mission Failed. Star 3 is the clock, as everywhere else.
         */
        val DEFAULT_LEVEL_9 = LevelData(
            id = "level_9",
            name = "09: Déjà Vu",
            // The walkthrough (testLevel9IsBeatableWithoutTouchingTheGround) finishes in ~114s:
            // the timed hops, a wait on the long load's dark end for the camera to look away with
            // the swing's slot coming up, the swing onto the deck load, the ride out, and the
            // double-length top course with its three bots. 180 - three minutes, the same as level 8
            // (owner, 2026-09-29) - is that x1.58: it also has to follow the figure, not race it.
            timeTargetSeconds = 180.0f,
            description = "The trail feels strangely familiar, as if you’ve done this before.",
            objectiveHint = "Follow the Figure Without Touching the Ground",
            bonusObjective = BonusObjective.STAY_OUT_OF_THE_FIGURES_SIGHT,
            layout = LEVEL_9_LAYOUT,
            stayOffTheGround = true,
            // "When the player completes level 8, keep record of the movements and play that in
            // level 9. the story is he is following behind his previous run" (2026-09-29): the
            // saved level 8 run (or the bundled one, level8_run.txt, when there is none) walks the
            // yard as an EchoRunner - a guard's cone and ears, and it pulls the lever that empties
            // the swing hook when the recording does.
            replaysRunOf = "level_8",
            // Level 2's rain and thunder (RainEffect: the rain, the sky lightning behind the
            // silhouettes and the delayed thunderclap) - "add rain and thunder to level 9 (copy it
            // from level 2)", 2026-09-29.
            hasRain = true,
            tutorialSteps = listOf(
                TutorialStep(
                    id = "step_stay_off_the_ground",
                    triggerMinX = 400.0,
                    triggerMaxX = 700.0,
                    title = "STAY OFF THE GROUND",
                    instructionTouch = "Cross the yard on the hanging loads. Touching the floor or a platform fails the mission.",
                    instructionDesktop = "Cross the yard on the hanging loads. Touching the floor or a platform fails the mission.",
                    targetAction = TutorialAction.JUMP_VAULT,
                    autoDismissSeconds = 6.0,
                    highlight = TutorialControlHighlight.JUMP,
                    handwrittenCallout = "Load to load - never the floor!"
                )
            ),
            guardSpeed = 95.0,
            guardPatrolMinX = 2600.0,
            guardPatrolMaxX = 3080.0
        )

        /**
         * Level 10: the tunnels under the yard ("10: Below the Yard").
         *
         * Built on level 7's vocabulary - "level 10 will have the same mechanics as level 7"
         * (2026-09-29): the same standing-height duct (304..440, its own `bglvl10.png`), headwind
         * fans beaten by spam-tapping, timed steam jets and patrol drones switched off from
         * behind - plus, since "block the main path in random places and add room.png on top and
         * add blocks and guards ... player should climb up and get this room and get out from
         * other end", rooms over the duct's beam with guards in them.
         *
         *   1. Twin intakes       two gales back to back, a jet in the calm pocket between them.
         *   2. The small room     two walls and one guard - the room's rule, taught once.
         *   3. Drone in the gale  a drone patrolling INSIDE a wind zone. It is caught from behind
         *                         at spam-tap pace (96 u/s against its 44), and when it turns the
         *                         way out is to stop tapping and let the gale carry you back out
         *                         of its sight - the wind is the retreat. Its near end is kept far
         *                         enough in that the zone's own lip is past its reach.
         *   4. Steam on patrol    a drone whose patrol runs across a jet: catching it from behind
         *                         means crossing the jet on the same run.
         *   5. The big room       three walls, two guards, a jet and a drone in the duct below.
         *   6. The gale gate      two jets in a wind zone, where there is no standing still -
         *                         holding back means drifting back.
         *   7. The lock           a short gust, four jets in a row on the tightest windows in the
         *                         game, then the fastest drone on the door.
         *   8. The cell           the duct ends at a shut door, and past it a freight lift up into
         *                         a room whose far end is a barred cell with the figure you were
         *                         tracking sitting inside it ([PrisonCellDef]). Reaching the bars
         *                         ends the level. The door and the lift are worked from wall
         *                         switches - the level's one tutorial - so level 11, which is
         *                         built on them, needs none ("introduce those switches and
         *                         elevators in level 10 at one place", 2026-09-30).
         *
         * ## The rooms
         *
         * The duct's ceiling slab is a room's floor (its top, [LEVEL_10_ROOM_FLOOR_Y], is where
         * bglvl10.png's beam is painted black - see GameplayScene.LEVEL_10_BG_*), and it has gaps
         * in it. Floor-to-ceiling walls close the duct, and guards patrol the rooms. The two
         * layers take turns:
         *
         *   - A WALL is passed above. In front of each one is a shaft: one crate (48, a jump from
         *     the floor - "player should jump onto 1 box and then climb up"), then a mantle of 111
         *     onto the slab past the gap - the slab's edge is a floating climb target, since
         *     nothing braces it from below. The wall's own top is no foothold: standing on it
         *     would put a head in the slab.
         *   - A GUARD is passed below. Past each wall the room floor has a drop hole, so the way
         *     on is back down into the duct, under the guard, where the slab hides you. The next
         *     wall then sends you up again BEHIND that guard - time it for when he is walking the
         *     other way, and be down the next hole before he comes back.
         *
         * Holes are 100 wide (a body is 36; a jump carries ~84, so none is jumped by accident),
         * shafts 140 (72 of open air in front of the crate to jump from), and no guard's patrol
         * reaches either. A room is 141 tall: standing room (96) with a jump's
         * headroom short of the ceiling, and all of it on screen on the reference phone, whose
         * top edge is world y ~114.
         *
         * Harder than level 7 across the board ("make it harder ... with less time to go through
         * steam", 2026-09-29): every jet's dormancy is at or under 1.1s, and past the intakes the
         * dormancy FLOOR drops under level 7's 0.8 ([SteamPipeDef.minDormantDuration], down to
         * 0.55 on the lock); drones are faster and pause 0.5s at each turn instead of 1.0s; the
         * gales push harder; and there are five checkpoints for seven beats. The one jet kept at
         * a 1.0 dormancy is the gale gate's far one - it is crossed at tap pace, not walking pace.
         *
         * Sorted by x everywhere, for the same reason as level 7 (the walkthrough's next-pipe scan).
         * It carries no distance stencils: those are level 7's own statement of its 120m.
         */
        val LEVEL_10_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 8600.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            // The same duct height as level 7 - bglvl10.png's corridor is pinned to 304..440, and
            // the slab over it is exactly the beam painted black above that (281..304).
            val ceilingBottomY = 304.0
            val slabTopY = LEVEL_10_ROOM_FLOOR_Y
            val slabEndX = 8600.0
            val roomCeilingY = LEVEL_10_ROOM_CEILING_Y
            val wallW = 40.0
            // A shaft is wider than a hole: the crate sits at its far end, and the jump onto it
            // has to start under open air - at 100 a body pressed against the crate still had its
            // head under the slab and bonked it on every jump.
            val shaftW = 140.0
            val holeW = 100.0

            /** One room: its x span, where its shafts up and holes down open, and its guards. */
            class Room(val left: Double, val right: Double, val shaftsUp: List<Double>, val holesDown: List<Double>)
            val rooms = listOf(
                // Beat 2 - the small room: wall, hole, one guard overhead, wall, hole.
                Room(1520.0, 2440.0, shaftsUp = listOf(1560.0, 2100.0), holesDown = listOf(1780.0, 2300.0)),
                // Beat 5 - the big room: three walls, two guards.
                Room(4520.0, 6040.0, shaftsUp = listOf(4560.0, 5120.0, 5680.0), holesDown = listOf(4800.0, 5360.0, 5900.0)),
                // The end - "at the end of level 10, he should climb up again and it should look
                // like a prison cell and the figure should be there sitting": the way up is the
                // freight lift (lvl10_lift) through this room's floor, and there is no way down.
                Room(7960.0, 8600.0, shaftsUp = emptyList(), holesDown = emptyList())
            )
            // The end of the duct: a shut door ("lvl10_door", its switch in front of it), then a
            // freight lift standing in the duct floor against the duct's end wall. Its switch is on
            // that wall, reached from the lift - press it and ride up into the cell room. Level
            // 11's whole vocabulary, met once here where nothing else is going on: the drone on
            // the door (lvl10_bot_4) has to be dealt with first, then it is just switches.
            val endDoor = DoorDef(id = "lvl10_door", x = 7990.0, top = ceilingBottomY, bottom = groundY)
            val endLift = LiftDef(id = "lvl10_lift", x = 8030.0, width = 120.0, upperY = slabTopY, lowerY = groundY, startsUp = false)
            val endWall = Rect(endLift.x + endLift.width, ceilingBottomY, wallW, groundY - ceilingBottomY)
            val endSwitches = listOf(
                DoorSwitchDef("lvl10_sw_door", 7956.0, groundY, listOf(endDoor.id)),
                // On the end wall's face: a body standing on the lift against the wall is in reach,
                // and wholly over the lift (clear of the slab beside the shaft) as it rises.
                DoorSwitchDef("lvl10_sw_lift", endWall.x - 20.0, groundY, listOf(endLift.id))
            )
            val shaftsUp = rooms.flatMap { it.shaftsUp }
            val openings = (shaftsUp.map { it to it + shaftW } + rooms.flatMap { it.holesDown }.map { it to it + holeW } +
                listOf(endLift.x to endLift.x + endLift.width))
                .sortedBy { it.first }
            val slabs = ArrayList<Rect>()
            var from = 0.0
            for ((l, r) in openings) {
                slabs.add(Rect(from, slabTopY, l - from, ceilingBottomY - slabTopY))
                from = r
            }
            slabs.add(Rect(from, slabTopY, slabEndX - from, ceilingBottomY - slabTopY))
            // Each wall stands right under the far edge of a shaft, so the shaft is the only way on.
            val corridorWalls = shaftsUp.map { l -> Rect(l + shaftW, ceilingBottomY, wallW, groundY - ceilingBottomY) }
            // One crate per shaft, flush against the wall and wholly under the gap.
            val shaftCrates = shaftsUp.map { l -> Rect(l + shaftW - 68.0, groundY - 48.0, 68.0, 48.0) }
            // The slab piece past each shaft is mounted from the crate: nothing under its edge.
            val shaftLedges = shaftsUp.map { l -> slabs.first { it.x == l + shaftW } }
            val roomCeilings = rooms.map { Rect(it.left, roomCeilingY - 600.0, it.right - it.left, 600.0) }
            val roomWalls = rooms.flatMap {
                listOf(
                    Rect(it.left, roomCeilingY, wallW, slabTopY - roomCeilingY),
                    Rect(it.right - wallW, roomCeilingY, wallW, slabTopY - roomCeilingY)
                )
            }

            fun roomGuard(min: Double, max: Double, startX: Double, facing: Double) = GuardSpawn(
                startX = startX, surfaceY = slabTopY, patrolMinX = min, patrolMaxX = max,
                speed = 55.0, facing = facing, visionRange = 180.0,
                width = 30.0, height = 96.0, patrolPauseDuration = 1.0
            )
            // On the slab pieces between a hole and the next shaft, never reaching either gap.
            val guards = listOf(
                roomGuard(1910.0, 2040.0, startX = 2040.0, facing = -1.0),
                roomGuard(4930.0, 5070.0, startX = 5070.0, facing = -1.0),
                roomGuard(5490.0, 5630.0, startX = 5490.0, facing = 1.0)
            )

            fun fan(id: String, x: Double, range: Double, push: Double) = VentFanDef(
                id = id, x = x, y = 338.0, width = 36.0, height = 68.0,
                windRange = range, windPushSpeed = push, windDirection = -1.0, fanImpulse = 10.0
            )

            fun pipe(
                id: String, x: Double, mount: PipeMountType, active: Double, inactive: Double, phase: Double,
                floor: Double = 0.8
            ) = SteamPipeDef(
                id = id, x = x, topY = ceilingBottomY, bottomY = groundY, mountType = mount,
                activeDuration = active, inactiveDuration = inactive, phaseOffsetSeconds = phase,
                minDormantDuration = floor
            )

            // Drones here stop for half the time level 7's do at each end of their beat (0.5s
            // against 1.0s), so a turn comes sooner than a level 7 player has learned to expect.
            fun bot(id: String, startX: Double, min: Double, max: Double, speed: Double, facing: Double, range: Double) =
                CameraBotDef(
                    id = id, startX = startX, surfaceY = groundY, patrolMinX = min, patrolMaxX = max,
                    speed = speed, facing = facing, visionRange = range, pauseDuration = 0.5
                )

            val fans = listOf(
                // Beat 1 - twin intakes: zones 480..820 and 1100..1400.
                fan("lvl10_fan_1", x = 820.0, range = 340.0, push = 150.0),
                fan("lvl10_fan_2", x = 1400.0, range = 300.0, push = 155.0),
                // Beat 3 - the drone's gale: zone 2860..3300, with lvl10_bot_1 patrolling in it.
                fan("lvl10_fan_3", x = 3300.0, range = 440.0, push = 150.0),
                // Beat 6 - the gale gate: zone 6140..6500, a jet at each end of it.
                fan("lvl10_fan_4", x = 6500.0, range = 360.0, push = 160.0),
                // Beat 7 - a short, violent gust ahead of the lock.
                fan("lvl10_fan_5", x = 6920.0, range = 140.0, push = 170.0)
            )

            // Every window is tighter than level 7's tightest (0.9 dormant), and past the intakes
            // the dormancy floor itself drops below level 7's 0.8 (SteamPipeDef.minDormantDuration).
            val steamPipes = listOf(
                // Beat 1 - one just inside the first gale (holding back is being blown clear of
                // it), one in the calm pocket between the two gales.
                pipe("lvl10_pipe_1", 600.0, PipeMountType.BOTTOM, active = 3.0, inactive = 1.1, phase = 0.6),
                pipe("lvl10_pipe_2", 960.0, PipeMountType.TOP, active = 3.0, inactive = 1.0, phase = 0.0),
                // Beat 4 - one on the way in, one across the drone's patrol.
                pipe("lvl10_pipe_3", 3700.0, PipeMountType.BOTTOM, active = 3.2, inactive = 0.9, phase = 0.9, floor = 0.7),
                pipe("lvl10_pipe_4", 4150.0, PipeMountType.TOP, active = 3.2, inactive = 0.9, phase = 0.4, floor = 0.7),
                // Beat 5 - in the duct under the big room's first guard, between hole 1 and shaft 2.
                pipe("lvl10_pipe_5", 5000.0, PipeMountType.BOTTOM, active = 3.4, inactive = 0.8, phase = 1.2, floor = 0.65),
                // Beat 6 - the gale gate: one near the zone's lip, one 60 short of the fan, taken
                // at spam-tap pace with no standing still between them. The far one keeps a
                // longer window than its neighbours: at 96 u/s from a safe distance a ~1.1-1.6s
                // window almost never came round (the walker stalled 53s on it at 0.75/0.6).
                pipe("lvl10_pipe_6", 6200.0, PipeMountType.TOP, active = 3.4, inactive = 0.8, phase = 2.0, floor = 0.65),
                pipe("lvl10_pipe_7", 6440.0, PipeMountType.TOP, active = 3.4, inactive = 1.0, phase = 0.0),
                // Beat 7 - the lock: four jets 110 apart on the shortest windows in the game.
                pipe("lvl10_pipe_8", 7100.0, PipeMountType.TOP, active = 3.8, inactive = 0.7, phase = 0.0, floor = 0.55),
                pipe("lvl10_pipe_9", 7210.0, PipeMountType.BOTTOM, active = 3.8, inactive = 0.7, phase = 1.3, floor = 0.55),
                pipe("lvl10_pipe_10", 7320.0, PipeMountType.TOP, active = 3.8, inactive = 0.7, phase = 2.6, floor = 0.55),
                pipe("lvl10_pipe_11", 7430.0, PipeMountType.BOTTOM, active = 3.8, inactive = 0.7, phase = 3.4, floor = 0.55)
            )

            val cameraBots = listOf(
                // Beat 3 - inside fan 3's zone. Its near end (3010, eye 3008) reaches 2893, past
                // the zone's lip at 2860, so a body the gale has carried out is out of its sight.
                // 44 against spam-tap pace's 96: caught, but with little to spare.
                bot("lvl10_bot_1", 3010.0, 3010.0, 3200.0, speed = 44.0, facing = 1.0, range = 115.0),
                // Beat 4 - its patrol runs across lvl10_pipe_4 (4150).
                bot("lvl10_bot_2", 4000.0, 4000.0, 4300.0, speed = 48.0, facing = 1.0, range = 130.0),
                // Beat 5 - in the duct under the big room's second guard, between hole 2 and
                // shaft 3: drop in while it heads away, and it has to be dealt with before the climb.
                bot("lvl10_bot_3", 5480.0, 5480.0, 5600.0, speed = 40.0, facing = 1.0, range = 110.0),
                // Beat 7 - on the door, the fastest in the game. Caught from its reach's edge in
                // ~1.6s against a ~2.5s run to its turn; at 58 over 120 it was 1.74s against
                // 2.07s, which no autopilot and few thumbs would make.
                bot("lvl10_bot_4", 7800.0, 7800.0, 7940.0, speed = 56.0, facing = 1.0, range = 140.0)
            )

            // Five for seven beats (level 7 has seven for six): the intakes and the gale gate have
            // none, so a death there costs the beat before it too. As on level 7, none sits under
            // anything, in a gale, on a jet or in a drone's reach.
            fun cp(id: String, x: Double) = Checkpoint(
                id = id, x = x, y = groundY - 96.0,
                triggerZone = Rect(x - 20.0, ceilingBottomY, 60.0, groundY - ceilingBottomY)
            )
            val checkpoints = listOf(
                cp("lvl10_cp1_small_room", 1480.0),
                cp("lvl10_cp2_gale_drone", 3420.0),
                cp("lvl10_cp3_steam_drone", 4480.0),
                cp("lvl10_cp4_big_room", 6060.0),
                cp("lvl10_cp5_lock", 7500.0)
            )

            // The cell fills the last room's far end; the level ends at its bars.
            val cellBars = Rect(8380.0, roomCeilingY, 8560.0 - 8380.0, slabTopY - roomCeilingY)
            val exitX = cellBars.x - 70.0
            val boxes = slabs + corridorWalls + endWall + shaftCrates + roomCeilings + roomWalls

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 100.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = exitX, y = roomCeilingY, width = 60.0, height = slabTopY - roomCeilingY),
                platforms = listOf(ground) + boxes,
                boxes = boxes,
                guards = guards,
                fans = fans,
                cameraBots = cameraBots,
                steamPipes = steamPipes,
                hasStartFences = false,
                canClimb = true,
                floatingClimbTargets = shaftLedges,
                prisonCell = PrisonCellDef(bars = cellBars, prisonerX = 8490.0, prisonerFacing = -1.0),
                // Tall and starting above y 0, a ceiling would otherwise be drawn as level 1's
                // hanging chained crate (GameplayScene's rule 5).
                plainPlatforms = roomCeilings,
                playerStartCrouched = false,
                manualCheckpoints = checkpoints,
                roomBackdrops = rooms.map { Rect(it.left, roomCeilingY, it.right - it.left, slabTopY - roomCeilingY) },
                doors = listOf(endDoor),
                doorSwitches = endSwitches,
                lifts = listOf(endLift)
            )
        }

        /** Level 10's rooms: their floor (the duct slab's top, where the painted beam starts) and its ceiling. */
        const val LEVEL_10_ROOM_FLOOR_Y: Double = 281.0
        const val LEVEL_10_ROOM_CEILING_Y: Double = 140.0

        val DEFAULT_LEVEL_10 = LevelData(
            id = "level_10",
            name = "10: Below the Yard",
            // LevelWalkthroughTest's rule (1.25..2.0x a clean autopilot run), at its tight end:
            // testLevel10SimulationPlayableWalkthrough clears it in 151.1s since the prison-cell
            // ending, most of that waiting on drones, room guards and the shorter steam windows.
            timeTargetSeconds = 190.0f,
            description = "Follow the underground tunnels in search of the person you were tracking.",
            objectiveHint = "Find the Prisoner in the Holding Cells",
            bonusObjective = BonusObjective.USE_EACH_SWITCH_ONCE,
            layout = LEVEL_10_LAYOUT,
            // Its own art: a concrete wall whose lower, lamp-lit corridor under a beam is the duct
            // (GameplayScene's LEVEL_10_BG_* constants pin it to 304..440).
            backgroundImage = "bglvl10.png",
            hasDarknessVignette = false,
            // Its only tutorial: everything else here is level 7's, taught there. Switches are
            // new, and level 11 is built on them with no tutorial of its own.
            tutorialSteps = listOf(
                TutorialStep(
                    id = "step_use_switches",
                    triggerMinX = 7950.0,
                    triggerMaxX = 8150.0,
                    title = "SWITCHES",
                    instructionTouch = "Use the switch to activate mechanisms.",
                    instructionDesktop = "Use the switch to activate mechanisms.",
                    targetAction = TutorialAction.INTERACT,
                    // Pointing at the switch on the wall, not at the INTERACT button: the thing to
                    // learn is that the panel works the door ("make the text point at the switch").
                    highlight = TutorialControlHighlight.NONE,
                    handwrittenCallout = "Use the switch to activate mechanisms",
                    worldTextX = 7620.0,
                    worldTextY = 245.0,
                    // lvl10_sw_door's panel (7956..7968, hung 58 over the floor).
                    worldAnchorX = 7960.0,
                    worldAnchorY = 376.0
                )
            )
        )

        /**
         * Level 11: "11: The Prisoner" - getting level 10's prisoner out of the same tunnels
         * (2026-09-30: "in level 11 you have to help the prisoner you freed in level 10 escape. it
         * will be inside the same place as level 10. [he] will move forward whenever possible. he
         * cant climb or parkour. you can use doors as a new mechanism for this level").
         *
         * An escort level. The prisoner ([PrisonerDef]) walks right on his own at 72 u/s and only
         * stops where he cannot go on - a shut door, a wall, a drop. He is seen like the player is
         * (any guard, camera or bot filling the meter on him is Mission Failed) and steam kills
         * him. So a DOOR is the only way to hold him, and the level is about where and when to
         * hold him: doors ([DoorDef]) are thrown from wall switches ([DoorSwitchDef]), stop guards
         * (a guard turns back at a shut door, so doors decide where his beat runs) and block
         * sight. Freight lifts ([LiftDef]) move him between the duct and the rooms over it. The
         * player can do what he cannot - climb a shaft, drop through a hole - and that is the
         * other half: getting to the far side of a door he is waiting at.
         *
         * Same place as level 10: bglvl10.png, the duct (304..440) under the rooms (140..281) on
         * the slab between them. Left to right:
         *
         *   1. The cell       level 10's cell. Throw the switch by the barred gate and he gets up
         *                     and walks out - onto a freight lift that fills the room's floor,
         *                     where the room's end wall stops him. Lower it: into the duct.
         *   2. Steam gate     a shut door just short of a jet. Open it so he meets the jet dead.
         *   3. Far switch     the next door's switch is on its FAR side. Up the shaft (a hung
         *                     catwalk, then a mantle), across the room, down the hole beyond it.
         *   4. Control room   a guard walks the pen between two shut doors. From the control room
         *                     overhead (he cannot see through the slab) open the pen's far door,
         *                     let him walk out over the lift into the bay, and shut the bay door
         *                     behind him - locked in, his beat is the bay. Then let the prisoner
         *                     through the empty pen onto the lift, which the shut bay door stops
         *                     him on, and take him up.
         *   5. Close it behind you   the room door ahead of him is open and a bot works the room
         *                     past it. Ride up with him, outrun him (132 against his 72), shut the
         *                     door from its far side before he reaches it, switch the bot off from
         *                     behind, let him through. Down the last lift.
         *   6. Over the top   a door holds him, and a bot works the duct past it - where the door's
         *                     only switch is. Up the shaft into a room with two jets of its own
         *                     (the player's steam, not his), down its hole behind the bot while it
         *                     heads away, switch it off from behind, let him through.
         *   7. Steam lock     two jets with a pocket between them, a door in front of each - one
         *                     window at a time, the player in the pocket with him.
         *   8. The way out    past the last jet the duct ends on a freight lift, which the end
         *                     wall stops him on. Ride it up together into the hatch room: the
         *                     level is complete once both of you are in it.
         *
         * Each beat ends with him held behind a shut door, and that is where its checkpoint is -
         * an escort level's checkpoints are the prisoner's (GameWorld: taken when he is held in
         * the zone, unseen), and a respawn puts back doors, lifts and guards as they were then.
         */
        val LEVEL_11_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 6100.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)
            val ceilingBottomY = 304.0
            val slabTopY = LEVEL_10_ROOM_FLOOR_Y
            val roomCeilingY = LEVEL_10_ROOM_CEILING_Y
            val wallW = 40.0

            fun ductDoor(id: String, x: Double, open: Boolean = false) =
                DoorDef(id = id, x = x, top = ceilingBottomY, bottom = groundY, startsOpen = open)
            fun roomDoor(id: String, x: Double, open: Boolean = false, style: DoorStyle = DoorStyle.SHUTTER, width: Double = 20.0) =
                DoorDef(id = id, x = x, top = roomCeilingY, bottom = slabTopY, width = width, startsOpen = open, style = style)
            fun lift(id: String, x: Double, width: Double, up: Boolean) =
                LiftDef(id = id, x = x, width = width, upperY = slabTopY, lowerY = groundY, startsUp = up)
            fun roomSwitch(id: String, x: Double, vararg targets: String) = DoorSwitchDef(id, x, slabTopY, targets.toList())
            fun ductSwitch(id: String, x: Double, vararg targets: String) = DoorSwitchDef(id, x, groundY, targets.toList())

            val lifts = listOf(
                // Beat 1: fills the cell room's floor at its far end, against the room's end wall.
                lift("lift_1", 540.0, 120.0, up = true),
                // Beat 4: sits in the duct floor between the pen and the bay; the bay door stops
                // him on it.
                lift("lift_2", 3130.0, 90.0, up = false),
                // Beat 5: at the far end of the control room's floor, against its end wall.
                lift("lift_3", 4240.0, 120.0, up = true),
                // Beat 8: the way out, at the duct's end - up into the hatch room.
                lift("lift_4", 5780.0, 120.0, up = false)
            )

            val doors = listOf(
                roomDoor("cell_gate", 240.0, style = DoorStyle.BARS, width = 12.0),
                ductDoor("door_1", 880.0),
                ductDoor("door_2", 1500.0),
                ductDoor("door_3", 2440.0),
                ductDoor("door_4", 3000.0),
                ductDoor("door_5", 3220.0, open = true),
                roomDoor("door_6", 3640.0, open = true),
                ductDoor("door_7", 4700.0),
                ductDoor("door_8", 5460.0),
                ductDoor("door_9", 5640.0)
            )

            val switches = listOf(
                roomSwitch("sw_cell", 270.0, "cell_gate"),
                roomSwitch("sw_lift_1", 500.0, "lift_1"),
                ductSwitch("sw_lift_1_below", 690.0, "lift_1"),
                ductSwitch("sw_door_1", 846.0, "door_1"),
                // Beat 3: past the door, not in front of it.
                ductSwitch("sw_door_2", 1560.0, "door_2"),
                // Beat 4: the control room, over the pen - one panel per door, and the lift's
                // by its shaft, where it is reached from the room floor or from on the lift.
                roomSwitch("sw_door_3", 2460.0, "door_3"),
                roomSwitch("sw_door_4", 2960.0, "door_4"),
                roomSwitch("sw_door_5", 3060.0, "door_5"),
                roomSwitch("sw_lift_2", 3110.0, "lift_2"),
                // Beat 5: both sides of the room door.
                roomSwitch("sw_door_6_near", 3600.0, "door_6"),
                roomSwitch("sw_door_6_far", 3680.0, "door_6"),
                // Over the slab's end, in reach from the room floor and from on the lift clear of the
                // duct wall under its near side (x >= 4240).
                roomSwitch("sw_lift_3", 4232.0, "lift_3"),
                ductSwitch("sw_lift_3_below", 4380.0, "lift_3"),
                // Beat 6: past the door only - the side the bot is on.
                ductSwitch("sw_door_7", 4742.0, "door_7"),
                // Beat 7: one in front of the lock, one in the pocket.
                ductSwitch("sw_door_8", 5426.0, "door_8"),
                ductSwitch("sw_door_9", 5580.0, "door_9"),
                // Beat 8: reached from the duct floor or from the lift's near end.
                ductSwitch("sw_lift_4", 5768.0, "lift_4")
            )

            /** A room over the duct: its x span, and the gaps in its floor (shafts up, holes down, lift shafts). */
            class Room(val left: Double, val right: Double)
            val rooms = listOf(
                Room(0.0, 700.0),      // the cell
                Room(1180.0, 1900.0),  // beat 3's room
                Room(2160.0, 4400.0),  // the control room and the upper floor past it
                Room(4440.0, 5300.0),  // beat 6's room, over the bot
                Room(5740.0, 5940.0)   // the hatch room, the way out
            )
            // Every gap in the slab, left to right: lift shafts, shafts up (a catwalk in each),
            // holes down.
            val shaftW = 140.0
            val shaftsUp = listOf(1240.0, 2220.0, 4480.0)
            val holesDown = listOf(1600.0 to 1700.0, 5060.0 to 5160.0)
            val openings = (lifts.map { it.x to it.x + it.width } + shaftsUp.map { it to it + shaftW } + holesDown)
                .sortedBy { it.first }
            val slabs = ArrayList<Rect>()
            var from = 0.0
            for ((l, r) in openings) {
                slabs.add(Rect(from, slabTopY, l - from, ceilingBottomY - slabTopY))
                from = r
            }
            slabs.add(Rect(from, slabTopY, worldWidth - from, ceilingBottomY - slabTopY))

            // The way up a shaft for the player alone. A crate on the duct floor (level 10's step)
            // would stop the prisoner dead, so the step is a catwalk hung across the shaft's far
            // end instead: top 326 (a climb of 114 from the floor, under the 115 cap), 6 thick so
            // its underside (332) clears a standing head (344) - he walks under it, which is why
            // it is a hanging climb target (grabbed from a wide window, not one exact spot). From
            // it, the slab past the shaft is a hop of 45 (under a jump's 51.2): "that next part
            // should be jumpable".
            val catwalks = shaftsUp.map { l -> Rect(l + shaftW - 70.0, 326.0, 70.0, 6.0) }
            val shaftLedges = shaftsUp.map { l -> slabs.first { it.x == l + shaftW } }

            val roomCeilings = rooms.map { Rect(it.left, roomCeilingY - 600.0, it.right - it.left, 600.0) }
            val roomWalls = rooms.flatMap {
                listOf(
                    Rect(it.left, roomCeilingY, wallW, slabTopY - roomCeilingY),
                    Rect(it.right - wallW, roomCeilingY, wallW, slabTopY - roomCeilingY)
                )
            }
            // The duct's own walls: its start (the lift shaft's near side), the bay's end, the
            // near side of lift 3's shaft (between the two is sealed duct), and its end.
            val ductWalls = listOf(500.0, 3560.0, 4200.0, 5900.0).map { Rect(it, ceilingBottomY, wallW, groundY - ceilingBottomY) }

            // Beat 4's guard: his route runs pen to bay (2470..3520), but shut doors turn him, so
            // what he actually walks is whatever the doors leave him - the pen, at the start.
            val penGuard = GuardSpawn(
                startX = 2700.0, surfaceY = groundY, patrolMinX = 2470.0, patrolMaxX = 3520.0,
                speed = 55.0, facing = 1.0, visionRange = 200.0, width = 30.0, height = 96.0,
                patrolPauseDuration = 0.8
            )

            val cameraBots = listOf(
                // Beat 5: on the control room's floor past door_6. Its near end (3860) reaches
                // 3738, so the respawn spot past the door (3690) is out of its sight.
                CameraBotDef(
                    id = "lvl11_bot_1", startX = 4000.0, surfaceY = slabTopY, patrolMinX = 3860.0, patrolMaxX = 4100.0,
                    speed = 40.0, facing = 1.0, visionRange = 120.0, pauseDuration = 0.8
                ),
                // Beat 6: in the duct past door_7, working up to where the door's switch is. Its
                // far end (5000) reaches 5154 facing right, so the drop from the hole (5060..5160)
                // is only safe while it heads back towards the door.
                CameraBotDef(
                    id = "lvl11_bot_2", startX = 4900.0, surfaceY = groundY, patrolMinX = 4800.0, patrolMaxX = 5000.0,
                    speed = 45.0, facing = -1.0, visionRange = 120.0, pauseDuration = 0.8
                )
            )

            fun pipe(id: String, x: Double, mount: PipeMountType, phase: Double) = SteamPipeDef(
                id = id, x = x, topY = ceilingBottomY, bottomY = groundY, mountType = mount,
                // The longest dormancy the jets allow (1.8, plus the 0.5 flare): he takes ~1.6s
                // from a door 28-48 short of a jet to clear it, door included.
                activeDuration = 2.4, inactiveDuration = 1.8, phaseOffsetSeconds = phase
            )
            // Beat 6's room jets: floor to ceiling of the room, the player's alone to cross.
            fun roomPipe(id: String, x: Double, mount: PipeMountType, phase: Double) =
                pipe(id, x, mount, phase).copy(topY = roomCeilingY, bottomY = slabTopY)
            val steamPipes = listOf(
                pipe("lvl11_pipe_1", 940.0, PipeMountType.BOTTOM, phase = 0.0),
                roomPipe("lvl11_pipe_4", 4800.0, PipeMountType.TOP, phase = 0.0),
                roomPipe("lvl11_pipe_5", 4960.0, PipeMountType.BOTTOM, phase = 1.6),
                pipe("lvl11_pipe_2", 5500.0, PipeMountType.TOP, phase = 0.0),
                // Near anti-phase with the one before it, so the lock is two windows, not one.
                pipe("lvl11_pipe_3", 5680.0, PipeMountType.BOTTOM, phase = 2.2)
            )

            // Where he is held at the end of each beat (see the class doc), and where the player
            // comes back: on his side of the door where the next thing to do is on that side,
            // past it where it is not.
            fun cp(id: String, zoneLeft: Double, zoneRight: Double, zoneTop: Double, zoneBottom: Double, spawnX: Double, spawnFeet: Double) =
                Checkpoint(x = spawnX, y = spawnFeet - 96.0, triggerZone = Rect(zoneLeft, zoneTop, zoneRight - zoneLeft, zoneBottom - zoneTop), id = id)
            val checkpoints = listOf(
                cp("lvl11_cp1_steam_gate", 820.0, 880.0, ceilingBottomY, groundY, spawnX = 790.0, spawnFeet = groundY),
                cp("lvl11_cp2_far_switch", 1440.0, 1500.0, ceilingBottomY, groundY, spawnX = 1420.0, spawnFeet = groundY),
                cp("lvl11_cp3_pen", 2380.0, 2440.0, ceilingBottomY, groundY, spawnX = 2380.0, spawnFeet = groundY),
                cp("lvl11_cp4_lift", 3150.0, 3220.0, ceilingBottomY, groundY, spawnX = 3040.0, spawnFeet = slabTopY),
                cp("lvl11_cp5_room_door", 3580.0, 3640.0, roomCeilingY, slabTopY, spawnX = 3690.0, spawnFeet = slabTopY),
                cp("lvl11_cp6_last_lift", 4280.0, 4360.0, roomCeilingY, slabTopY, spawnX = 4190.0, spawnFeet = slabTopY),
                cp("lvl11_cp7_over_the_top", 4640.0, 4700.0, ceilingBottomY, groundY, spawnX = 4420.0, spawnFeet = groundY),
                cp("lvl11_cp8_lock", 5400.0, 5460.0, ceilingBottomY, groundY, spawnX = 5370.0, spawnFeet = groundY),
                cp("lvl11_cp9_pocket", 5580.0, 5640.0, ceilingBottomY, groundY, spawnX = 5536.0, spawnFeet = groundY)
            )

            val cellBars = Rect(60.0, roomCeilingY, 240.0 - 60.0, slabTopY - roomCeilingY)
            val boxes = slabs + roomCeilings + roomWalls + ductWalls + catwalks

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 320.0,
                playerStartY = slabTopY - 96.0,
                // The hatch room over lift_4: reached only by riding it up.
                exitZone = Rect(x = 5780.0, y = roomCeilingY, width = 120.0, height = slabTopY - roomCeilingY),
                platforms = listOf(ground) + boxes,
                boxes = boxes,
                guards = listOf(penGuard),
                cameraBots = cameraBots,
                steamPipes = steamPipes,
                hasStartFences = false,
                canClimb = true,
                floatingClimbTargets = shaftLedges + catwalks,
                hangingClimbTargets = catwalks,
                prisonCell = PrisonCellDef(bars = cellBars, prisonerX = 150.0, prisonerFacing = 1.0, drawsFigure = false),
                plainPlatforms = roomCeilings + catwalks,
                manualCheckpoints = checkpoints,
                roomBackdrops = rooms.map { Rect(it.left, roomCeilingY, it.right - it.left, slabTopY - roomCeilingY) },
                doors = doors,
                doorSwitches = switches,
                lifts = lifts,
                prisoner = PrisonerDef(x = 110.0, surfaceY = slabTopY, freedByDoorId = "cell_gate"),
                // exit_sign.png (the asset drop's exit.png, cropped to its alpha, 2.54:1): on the
                // duct wall past the last jet, over lift_4's near end, above head height (a
                // standing head is at 344). Dimmed to the lamp-lit wall it hangs on - as painted,
                // its white letters were the brightest thing in the tunnels.
                wallDecals = listOf(WallDecal(Rect(5710.0, 310.0, 64.0, 64.0 / 2.54), "exit_sign.png", brightness = 0.5))
            )
        }

        val DEFAULT_LEVEL_11 = LevelData(
            id = "level_11",
            name = "11: The Prisoner",
            // Level11EscortTest's scripted clean run takes 148.7s, most of it waiting on him (he
            // walks at 72 against the player's 132), on the jets and on the bots; x1.45, rounded
            // to 5 - a person also has to work out each beat's doors, which the script knows.
            timeTargetSeconds = 215.0f,
            description = "Rescue the prisoner and escort him to safety. Something about him feels familiar.",
            objectiveHint = "Escort the Prisoner to the Exit",
            bonusObjective = BonusObjective.KEEP_THE_PRISONER_OUT_OF_SIGHT,
            layout = LEVEL_11_LAYOUT,
            backgroundImage = "bglvl10.png"
            // No tutorial: level 10's end teaches switches, doors and lifts, and the rest is
            // for the player to work out ("dont add any tutorial in level 11", 2026-09-30).
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

    // Star 2: the level's own optional objective (LevelData.bonusObjective) was met - or, on a
    // level without one, no alert was raised. The field keeps its old name so saves written
    // before the per-level objectives read back unchanged; it means "star 2 missed".
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
