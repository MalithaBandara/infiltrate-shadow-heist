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
 * here). [tileCount] only ever ADDS extra truss beyond the asset's own natural boom length (161 +
 * tileCount*126 is compared against the natural truss-to-cab boundary at 685px in
 * GameplayScene.kt) - anything less would just reconstruct the original image's own width and
 * read as no longer at all, which is exactly what an earlier attempt got wrong.
 *
 * [bounds] is the crane's whole visual extent, for positioning/measurement only - it is NOT one
 * solid collision box. [boomBounds] and [cabBounds] are the two real collision pieces: the boom
 * (tip cap + tiles) is only as thick as its own real art (a thin band near the top of the crane's
 * height), so an overhang past the cab/tracked-base end reads as open headroom underneath, not a
 * solid wall - e.g. LEVEL_6_LAYOUT's own crane overhangs above endTerrain so the player can walk
 * underneath it there, while the cab/tracked-base end (a real, full-height block, since that part
 * of the art really does reach the ground) still rests solidly on cranePlatform. Both are meant to
 * go in [LevelLayout.boxes] - real and climbable/walkable, not just decoration, on request ("make
 * sure all parts of the crane is interactable").
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
        private const val BOOM_CROP_TOP = 7.0
        private const val BOOM_CROP_BOTTOM = 88.0
    }

    private val scale: Double get() = height / CROP_HEIGHT
    private val boomWidth: Double get() = scale * (TIP_CAP_CROP_WIDTH + tileCount * TILE_CROP_WIDTH)
    private val cabWidth: Double get() = scale * CAB_CROP_WIDTH

    val width: Double get() = boomWidth + cabWidth

    val bounds: Rect get() = Rect(x, y, width, height)

    val boomBounds: Rect
        get() = Rect(x, y + scale * BOOM_CROP_TOP, boomWidth, scale * (BOOM_CROP_BOTTOM - BOOM_CROP_TOP))

    val cabBounds: Rect get() = Rect(x + boomWidth, y, cabWidth, height)
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
    // interactable") each one's own [CraneDef.bounds] must ALSO be in [boxes] - solid and
    // climbable/walkable across its whole footprint, not just decoration.
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
    // A box the player can mantle onto directly despite Player.findClimbTarget's usual rule
    // against floating ledges (a box whose underside sits well above the climber's feet) - for a
    // ledge that's meant to be mounted with nothing bracing it underneath. Must also be in
    // [boxes]. See Player.findClimbTarget and LEVEL_3_LAYOUT.
    val floatingClimbTargets: List<Rect> = emptyList(),
    val movingPlatforms: List<MovingPlatformDef> = emptyList(),
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
    val manualCheckpoints: List<Checkpoint> = emptyList()
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
    val playerCrouchForwardSpeedMultiplier: Double = 1.0
) {
    val resolvedBackgroundImage: String
        get() {
            if (backgroundImage != null) return backgroundImage
            val levelNum = id.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
            return when ((levelNum - 1) % 3) {
                0 -> "bgmg2.png"
                1 -> "bgmg3.png"
                else -> "bgmg4.png"
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
            description = "Follow the robbery trail to the shipyard and find a way inside to begin your investigation.",
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
            description = "Search the outer yard for clues and find a route toward the areas connected to the stolen cargo.",
            objectiveHint = "Find a Way Through the Yard",
            layout = LEVEL_2_LAYOUT,
            backgroundImage = "bgmg5.png"
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
            name = "03: New Level",
            timeTargetSeconds = 25.0f,
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
            name = "04: Blind Spot",
            timeTargetSeconds = 115.0f,
            description = "The cargo express conveyor is running in reverse. Vault over oncoming crates, duck under low-hanging cargo, and reach the secure facility.",
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
            name = "05: Restricted Zone",
            timeTargetSeconds = 60.0f,
            description = "The trail leads into a guarded cargo section. Get inside and discover what they are protecting.",
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
         * climb up the far block. Section 3 (past tallBlock) is a lone barrel, then a stacked pair
         * forming one 96-tall climbable column, leading up to one more platform at that same
         * groundY-96 tier (tallBlock's own height, not terrain/farTerrain's taller one) with a real
         * but deliberately non-functional lever near its left corner (no targetMechanismId - see
         * Lever), a second smaller platform just past it, and a small background crane standing on
         * that second platform - solid and climbable across its whole footprint, not just
         * decoration. See each section's own comments below for their geometry reasoning.
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
            val worldWidth = 3765.0
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

            // 9. Third section: past tallBlock's own drop, a lone ground barrel (standard 32x48,
            // same as every other barrel in this game - a plain jump clears it height-wise, but see
            // this file's own earlier note, Section 2's barrel pyramid now removed, on why any
            // walkthrough test still needs an ANTICIPATED jump for it rather than a reactive one:
            // Player.updateStep's horizontal collision pass pins the whole body at the wall,
            // regardless of the barrel's own height, whenever it's already touching, flush, with no
            // run-up), then two more barrels STACKED directly on top of each other (same x/width,
            // zero gap between them) rather than side by side - together they read as one solid
            // 96-tall column, which is a real [Player.climbMinHeight]..[climbMaxHeight] (51.2..115)
            // climb straight from the ground, not a jump. Unlike the lone barrel above, a climbable
            // obstacle like this one is fine with a REACTIVE jump (Player.findClimbTarget takes
            // over before the horizontal-collision pin ever applies) - same as every other climb in
            // this file.
            // Barrel1 sits further out from tallBlock than before (a real run-up), and the stack
            // now sits flush against its own right face - zero gap, touching - on request ("bring
            // the two barrels and the platform to left and the single barrel to right... single
            // barrel should be touching the double barrel").
            val endBarrelWidth = 32.0
            val endBarrelHeight = 48.0
            val endBarrel1 = Rect(x = tallBlock.right + 150.0, y = groundY - endBarrelHeight, width = endBarrelWidth, height = endBarrelHeight)
            val endBarrelStackX = endBarrel1.right
            val endBarrelStackBottom = Rect(x = endBarrelStackX, y = groundY - endBarrelHeight, width = endBarrelWidth, height = endBarrelHeight)
            val endBarrelStackTop = Rect(x = endBarrelStackX, y = groundY - endBarrelHeight * 2.0, width = endBarrelWidth, height = endBarrelHeight)

            // The next platform sits flush at the SAME height as the barrel stack's own top
            // (groundY - 96, the same tier tallBlock itself uses) - a level walk straight off the
            // climb, not a second climb on top of the first. Shortened from an original 300 to 120
            // once the crane moved back down to the ground beside it (see below) - it only needs
            // room for the lever and a bit of standing space now, not a large machine on top of it.
            val endPlatformTopY = groundY - endBarrelHeight * 2.0
            val endTerrain = Rect(x = endBarrelStackTop.right, y = endPlatformTopY, width = 120.0, height = groundY - endPlatformTopY)

            // Lever near the platform's LEFT corner this time (lever/lever2 both sat at their own
            // platform's right corner) - right where the player arrives, having just climbed the
            // barrel stack.
            val lever3 = Lever(
                id = "lever_3",
                x = endTerrain.left + 30.0,
                y = endPlatformTopY - leverHeight,
                width = leverWidth,
                height = leverHeight
            )

            // Background crane - see CraneDef. Sized first (before cranePlatform, below) so the
            // platform can be built to actually fit its tracked-base end - see craneWidthPreview.
            // Bumped up a little on request ("increase the size of the crane a little bit") - 108
            // to 125. It no longer has to stay under Player.climbMaxHeight the way the previous
            // version did: this one is meant to be walked UNDER (see cranePlatform's own comment
            // below), not climbed.
            val craneHeight = 125.0
            val craneTileCount = 10
            val craneBackOffset = 100.0
            val craneWidthPreview = CraneDef(x = 0.0, y = 0.0, height = craneHeight, tileCount = craneTileCount).width

            // A second platform flush against endTerrain's own right face (no gap - "connect the
            // platform it is on to the one left of it", from an earlier request) - but now raised
            // back up to EXACTLY endTerrain's own height, on request ("increase the height of the
            // platform the crane is on so the player can [walk] under the beam and [reach] the
            // platform with the lever"): both platforms share one flat, connected walking tier, and
            // the crane sits well clear of it overhead rather than sitting low enough to force a
            // climb the way an earlier, shorter version of this same platform did.
            //
            // Widened to run the crane's own full length past its own near/left edge (minus
            // craneBackOffset, since that portion overhangs endTerrain instead - see below - and
            // needs no platform under it there), plus a small margin, so the crane's tracked-base
            // end has solid ground the whole way ("the crane can't be floating", from an earlier
            // request) even though its boom now reaches back further than before.
            //
            // At a short height, this box's own dimensions could fall inside GameplayScene.kt's
            // "Step Crate" size heuristic purely by coincidence, so [LevelLayout.plainPlatforms]
            // forces it back to the plain structural-block look instead of crate art - reported
            // directly against an earlier, narrower version of this same platform.
            val cranePlatformTopY = endPlatformTopY
            val cranePlatform = Rect(
                x = endTerrain.right,
                y = cranePlatformTopY,
                width = (craneWidthPreview - craneBackOffset) + 10.0,
                height = groundY - cranePlatformTopY
            )

            // The real crane, now that cranePlatform is sized to actually hold its tracked-base end.
            // Its boom now reaches back across endTerrain's own right portion ("make this beam go
            // across to the next platform") rather than starting flush at cranePlatform's own edge -
            // craneBackOffset is how far back over endTerrain it reaches, well short of the barrel
            // stack at endTerrain's far/left end. Its tracked-base end (CraneDef.cabBounds) still
            // rests flush on cranePlatform's own surface (cabBounds.bottom == cranePlatform.top
            // exactly) - "the crane can't be floating" still holds for the one piece of it that's
            // actually meant to be grounded. The boom itself (CraneDef.boomBounds) is only as thick
            // as its own real art near the TOP of the crane's height, not the crane's full height,
            // so the open air underneath it (down to endTerrain's own surface at 344) is genuinely
            // walkable - checked directly: clearance there clears the player's own standing height
            // (96) with a real margin, not just barely.
            //
            // On request ("make sure all parts of the crane is interactable") both CraneDef.
            // boomBounds and cabBounds are real boxes - see [LevelLayout.cranes]/[LevelLayout.boxes]
            // - solid and climbable/walkable, not just decoration, the same as every other box in
            // this game. Neither needs a floating-ledge exemption: cabBounds rests flush on
            // cranePlatform from the player's own current stance there, and boomBounds is simply
            // never approached from underneath as a climb target - it's an overhead crossing.
            val crane = CraneDef(
                x = endTerrain.right - craneBackOffset,
                y = cranePlatformTopY - craneHeight,
                height = craneHeight,
                tileCount = craneTileCount
            )

            // Third section - and the level - ends here, right on cranePlatform itself, a short
            // walk past the boom's own overhang and before the crane's tracked-base end (a real,
            // full-height block - see CraneDef.cabBounds - too tall to climb on purpose, since it's
            // meant to be reached and rested against, not climbed over). Putting the exit any
            // further along would mean walking INTO that block, which the climb mechanic can't
            // clear (its own rise, craneHeight, is past Player.climbMaxHeight - deliberately, since
            // this piece is meant to look and behave like real grounded machinery, not another
            // climbable step).
            val exitX = crane.cabBounds.left - 80.0

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
                    x = endTerrain.left + 62.0,
                    y = endPlatformTopY - 96.0,
                    triggerZone = Rect(endTerrain.left, endPlatformTopY - 120.0, endTerrain.width, 140.0)
                ),
                Checkpoint(
                    id = "lvl6_cp5_cranePlatform",
                    x = cranePlatform.left + 32.0,
                    y = cranePlatformTopY - 96.0,
                    triggerZone = Rect(cranePlatform.left, cranePlatformTopY - 120.0, cranePlatform.width, 140.0)
                )
            )

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = exitX, y = groundY - 100.0, width = 44.0, height = 100.0),
                platforms = listOf(ground),
                boxes = listOf(
                    crate1, terrain, landingCrate1, farTerrain, pitCrate, pitLongCrate, tallBlock,
                    endBarrel1, endBarrelStackBottom, endBarrelStackTop, endTerrain, cranePlatform,
                    crane.boomBounds, crane.cabBounds
                ),
                guards = listOf(pitGuard),
                hangingCrateVariant1 = listOf(landingCrate1, pitLongCrate),
                barrels = listOf(endBarrel1, endBarrelStackBottom, endBarrelStackTop),
                // Player.findClimbTarget's own floating-ledge check compares a candidate box's
                // bottom against the player's CURRENT feet (here, standing on the ground below,
                // not on the bottom barrel), and endBarrelStackTop's own bottom (392) sits well
                // above that (440) even though endBarrelStackBottom is the real, solid thing
                // bracing it - the check has no notion of "something else is solid underneath",
                // only of the climbing player's own current stance. Confirmed directly: without
                // this, the player got stuck flush against the stack, unable to climb it at all
                // (same signature as the barrel-vs-height quirk documented elsewhere in this file,
                // but a different cause). Whitelisting it here is the same sanctioned exemption
                // LEVEL_3_LAYOUT's tablePlank/cameraBeam already use for the identical reason. The
                // crane itself needs no such exemption - it rests flush on cranePlatform's own
                // surface, which IS the player's current stance when approaching it.
                floatingClimbTargets = listOf(endBarrelStackTop),
                cranes = listOf(crane),
                plainPlatforms = listOf(cranePlatform),
                swingHooks = listOf(hook1),
                levers = listOf(lever, lever3),
                movingPlatforms = listOf(leverCrate),
                manualCheckpoints = checkpoints
            )
        }

        val DEFAULT_LEVEL_6 = LevelData(
            id = "level_6",
            name = "06: Missing Container",
            timeTargetSeconds = 45.0f,
            description = "Container 17 appears in the records from your crew's final job. Find it and learn where it went.",
            objectiveHint = "Find Container 17",
            layout = LEVEL_6_LAYOUT
        )

        val DEFAULT_LEVEL_7 = LevelData(
            id = "level_7",
            name = "07: Stolen Manifest",
            timeTargetSeconds = 25.0f,
            description = "The container is missing. Search the offices for records that reveal who moved it and where it went.",
            objectiveHint = "Find the Shipping Records",
            guardSpeed = 85.0,
            guardPatrolMinX = 2650.0,
            guardPatrolMaxX = 3120.0
        )

        val DEFAULT_LEVEL_8 = LevelData(
            id = "level_8",
            name = "08: Cold Trail",
            timeTargetSeconds = 24.0f,
            description = "The records point deeper into the shipyard. Follow the trail and uncover evidence of recent activity.",
            objectiveHint = "Follow the Cargo Trail",
            guardSpeed = 90.0,
            guardPatrolMinX = 2600.0,
            guardPatrolMaxX = 3100.0
        )

        val DEFAULT_LEVEL_9 = LevelData(
            id = "level_9",
            name = "09: Old Signature",
            timeTargetSeconds = 23.0f,
            description = "You find your crew's signature at the shipyard. Follow the clues to prove someone from the crew survived.",
            objectiveHint = "Find Your Crew's Mark",
            guardSpeed = 95.0,
            guardPatrolMinX = 2600.0,
            guardPatrolMaxX = 3080.0
        )

        val DEFAULT_LEVEL_10 = LevelData(
            id = "level_10",
            name = "10: Open Yard",
            timeTargetSeconds = 24.0f,
            description = "The trail continues across an exposed yard. Cross it unseen and stay close to the evidence.",
            objectiveHint = "Cross the Yard Undetected",
            guardSpeed = 100.0,
            guardPatrolMinX = 2550.0,
            guardPatrolMaxX = 3050.0
        )

        val DEFAULT_LEVEL_11 = LevelData(
            id = "level_11",
            name = "11: Ghost Chase",
            timeTargetSeconds = 23.0f,
            description = "A mysterious figure appears ahead, moving like one of your old crew. Follow them before they vanish.",
            objectiveHint = "Follow the Stranger",
            guardSpeed = 105.0,
            guardPatrolMinX = 2550.0,
            guardPatrolMaxX = 3030.0
        )

        val DEFAULT_LEVEL_12 = LevelData(
            id = "level_12",
            name = "12: Hidden Cargo",
            timeTargetSeconds = 22.0f,
            description = "You finally reach Container 17. Open it and uncover what links the cargo to your crew's disappearance.",
            objectiveHint = "Open Container 17",
            guardSpeed = 110.0,
            guardPatrolMinX = 2500.0,
            guardPatrolMaxX = 3000.0
        )

        val DEFAULT_LEVEL_13 = LevelData(
            id = "level_13",
            name = "13: Final Proof",
            timeTargetSeconds = 22.0f,
            description = "The final evidence may reveal the truth about that night and which member of your crew survived.",
            objectiveHint = "Recover the Evidence",
            guardSpeed = 115.0,
            guardPatrolMinX = 2500.0,
            guardPatrolMaxX = 2980.0
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
            DEFAULT_LEVEL_12,
            DEFAULT_LEVEL_13
        )
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
