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
    val sweepPauseDuration: Double = 0.0
)

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
    val fence1: Rect? = null,
    val fence2: Rect? = null,
    // Jump-crate gap crossings: each rect here must also be included in [boxes] (so it collides
    // and can be landed on) - this just tags which boxes get the hanging-crate look (a decorative
    // grey chain down to a normal-colored box) and which of the two crate art variants to use.
    // See GameplayScene.kt's box-rendering loop.
    val hangingCrateVariant1: List<Rect> = emptyList(),
    val hangingCrateVariant2: List<Rect> = emptyList(),
    val barrels: List<Rect> = emptyList(),
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
    val conveyors: List<ConveyorDef> = emptyList(),
    val conveyorCrates: List<ConveyorCrateDef> = emptyList(),
    val hasStartFences: Boolean = true,
    val restartOnConveyorFallOff: Boolean = false,
    val conveyorsStartOnMove: Boolean = false,
    val canClimb: Boolean = true,
    val lasers: List<LaserDef> = emptyList()
)

enum class TutorialAction {
    MOVE, JUMP_VAULT, CROUCH, CLIMB, REACH_OBJECTIVE
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
            val worldWidth = 5500.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            val conveyorHeight = 26.0
            val conveyorWidth = 5000.0
            val conveyorRect = Rect(x = 0.0, y = groundY - conveyorHeight, width = conveyorWidth, height = conveyorHeight)
            val conveyor = ConveyorDef(bounds = conveyorRect, speed = -45.0)

            val crateWidth = 68.0
            val crateHeight = 48.0
            val conveyorTopY = conveyorRect.top // 414.0

            // =========================================================================
            // APPROACH 1: KINETIC RHYTHM GAUNTLET (Zero-Clipping Physics)
            // =========================================================================
            // - Lowered hanging monorail containers at floor-crouch height (y = 302.0, height = 38.0, bottom at 340.0).
            // - Clearance above conveyor (414.0 - 340.0 = 74.0px):
            //   - Crouching player (height 56.0, head at 358.0) clears with 18px headroom and passes cleanly.
            //   - Standing player (height 96.0, head at 318.0) hits container and is blocked.
            // - All floor crates are single 1-stacks (height = 48.0, top at y = 366.0).
            // - ZERO CLIPPING GUARANTEE:
            //   - Physical gap between floor crate top (366.0) and hanging crate bottom (340.0) is 26.0px!
            //   - 1-stack crates glide smoothly under hanging cargo without any visual collision.
            //   - Standing or crouching atop a 1-stack crate under hanging cargo hits (head at 270/310 < 340),
            //     so the player cannot bypass ducking by riding crates—they must drop to the belt and slide!
            // =========================================================================

            val hangingCrateSmall1 = ConveyorCrateDef(
                initialX = 1100.0,
                y = 302.0,
                width = 76.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = false,
                shouldLoop = true,
                loopMinX = -76.0,
                loopMaxX = conveyorWidth,
                speedMultiplier = 1.0
            )
            val hangingCrateLong1 = ConveyorCrateDef(
                initialX = 2250.0,
                y = 302.0,
                width = 174.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = true,
                shouldLoop = true,
                loopMinX = -174.0,
                loopMaxX = conveyorWidth,
                speedMultiplier = 1.0,
                minY = 220.0,
                maxY = 302.0,
                verticalPeriodSeconds = 3.5,
                verticalPhaseOffsetSeconds = 0.0
            )
            val hangingCrateSmall2 = ConveyorCrateDef(
                initialX = 3400.0,
                y = 302.0,
                width = 76.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = false,
                shouldLoop = true,
                loopMinX = -76.0,
                loopMaxX = conveyorWidth,
                speedMultiplier = 1.0,
                minY = 220.0,
                maxY = 302.0,
                verticalPeriodSeconds = 4.0,
                verticalPhaseOffsetSeconds = 2.0
            )
            val hangingCrateLong2 = ConveyorCrateDef(
                initialX = 4480.0,
                y = 302.0,
                width = 174.0,
                height = 38.0,
                isHanging = true,
                isVariant1 = true,
                shouldLoop = true,
                loopMinX = -174.0,
                loopMaxX = conveyorWidth,
                speedMultiplier = 1.0
            )
            val hangingCrates = listOf(hangingCrateSmall1, hangingCrateLong1, hangingCrateSmall2, hangingCrateLong2)

            val crateHeight2 = 96.0 // 2-stack crate height

            // Dynamic floor crates (mix of single 1-stacks and stepped 2-stack pyramids):
            // Zero-clipping guarantee: 2-stack crates are exclusively located in open-sky conveyor zones
            // well clear of low hanging monorail cargo, while 1-stacks clear under hanging crates with 26px headroom.
            val movingConveyorCrates = listOf(
                // Section 1: Intro Jump Gauntlet (x = 300..900)
                ConveyorCrateDef(initialX = 350.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 550.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 720.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 880.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),

                // Section 2: Duck Zone 1 (x = 900..1350, hanging crate small at 1100)
                ConveyorCrateDef(initialX = 1300.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),

                // Section 3: Stepped 2-Stack Crate Pyramid 1 (x = 1380..1550, open conveyor zone)
                ConveyorCrateDef(initialX = 1380.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 1448.0, y = conveyorTopY - crateHeight2, width = crateWidth, height = crateHeight2, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 1516.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 1680.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),

                // Section 4: Jump Gauntlet & Duck Zone 2 (hanging crate long at 2250)
                ConveyorCrateDef(initialX = 2050.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 2480.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),

                // Section 5: Stepped 2-Stack Crate Pyramid 2 (x = 2700..2860, open conveyor zone)
                ConveyorCrateDef(initialX = 2700.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 2768.0, y = conveyorTopY - crateHeight2, width = crateWidth, height = crateHeight2, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 2836.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 3020.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),

                // Section 6: Duck Zone 3 (hanging crate small at 3400)
                ConveyorCrateDef(initialX = 3620.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),

                // Section 7: Stepped 2-Stack Crate Pyramid 3 (x = 3850..4010, open conveyor zone)
                ConveyorCrateDef(initialX = 3850.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 3918.0, y = conveyorTopY - crateHeight2, width = crateWidth, height = crateHeight2, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 3986.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 4300.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),

                // Section 8: Final Sprint & Gauntlet (hanging crate long at 4480..4654)
                ConveyorCrateDef(initialX = 4720.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 5120.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth),
                ConveyorCrateDef(initialX = 5240.0, y = conveyorTopY - crateHeight, width = crateWidth, height = crateHeight, loopMaxX = conveyorWidth)
            )

            // Dynamic laser hazards:
            // All lasers originate from ceiling (topY = 150.0) and aim at conveyor surface (bottomY = 414.0).
            // Includes single vertical warning beams, 2 crossed lasers (X-Beam traps), and multi-laser arrays.
            // Tilt angles strictly <= 45 degrees from vertical.
            val lasers = listOf(
                // Laser 1: Pure vertical beam (0°) in Section 1 (x = 670.0). Times between conveyor crates.
                LaserDef(
                    id = "lvl4_laser_vert_1",
                    topX = 670.0,
                    topY = 150.0,
                    bottomX = 670.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.8,
                    inactiveDuration = 2.0,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 2A & 2B: Crossed Lasers Trap 1 (X-Beam) in Section 3 (x = 1820 <-> 1960).
                // Tilt = ±27.9° <= 45°. Synchronized pulse creates a glowing 'X' energy gate.
                LaserDef(
                    id = "lvl4_laser_cross_1a",
                    topX = 1820.0,
                    topY = 150.0,
                    bottomX = 1960.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.6,
                    inactiveDuration = 3.2,
                    phaseOffsetSeconds = 0.0
                ),
                LaserDef(
                    id = "lvl4_laser_cross_1b",
                    topX = 1960.0,
                    topY = 150.0,
                    bottomX = 1820.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.6,
                    inactiveDuration = 3.2,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 3: Backward-tilted security gate (-25°) in Section 4/5 (bottomX = 2580.0).
                LaserDef(
                    id = "lvl4_laser_tilt_back",
                    topX = 2703.0,
                    topY = 150.0,
                    bottomX = 2580.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.6,
                    inactiveDuration = 2.8,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 4A & 4B: Crossed Lasers Trap 2 (Scissors X) in Section 5 (x = 3160 <-> 3300).
                // Tilt = ±27.9° <= 45°. Synchronized pulse creates an open gauntlet window.
                LaserDef(
                    id = "lvl4_laser_cross_2a",
                    topX = 3160.0,
                    topY = 150.0,
                    bottomX = 3300.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.6,
                    inactiveDuration = 3.2,
                    phaseOffsetSeconds = 0.0
                ),
                LaserDef(
                    id = "lvl4_laser_cross_2b",
                    topX = 3300.0,
                    topY = 150.0,
                    bottomX = 3160.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.6,
                    inactiveDuration = 3.2,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 5A & 5B: Convergent V-Trap in Section 7 (aimed at bottomX = 4180.0 and 4240.0).
                // Tilt = +20.7° and -12.8° <= 45°.
                LaserDef(
                    id = "lvl4_laser_sweep_a",
                    topX = 4080.0,
                    topY = 150.0,
                    bottomX = 4180.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.0,
                    phaseOffsetSeconds = 0.0
                ),
                LaserDef(
                    id = "lvl4_laser_sweep_b",
                    topX = 4300.0,
                    topY = 150.0,
                    bottomX = 4240.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.0,
                    phaseOffsetSeconds = 0.0
                ),
                // Laser 6A, 6B, 6C: Final extraction gauntlet triple-laser array (x = 4800..5060).
                // Synchronized extraction pulse: 1.4s active, 4.8s inactive.
                LaserDef(
                    id = "lvl4_laser_gauntlet_1",
                    topX = 4800.0,
                    topY = 150.0,
                    bottomX = 4860.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.8,
                    phaseOffsetSeconds = 0.0
                ),
                LaserDef(
                    id = "lvl4_laser_gauntlet_2",
                    topX = 4930.0,
                    topY = 150.0,
                    bottomX = 4930.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.8,
                    phaseOffsetSeconds = 0.0
                ),
                LaserDef(
                    id = "lvl4_laser_gauntlet_3",
                    topX = 5060.0,
                    topY = 150.0,
                    bottomX = 5000.0,
                    bottomY = conveyorTopY,
                    beamThickness = 6.0,
                    activeDuration = 1.4,
                    inactiveDuration = 4.8,
                    phaseOffsetSeconds = 0.0
                )
            )

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 100.0,
                playerStartY = conveyorRect.top - 96.0,
                exitZone = Rect(x = 5380.0, y = groundY - 100.0, width = 44.0, height = 100.0),
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
            // camera mount (near the start, cameraBeam.x + 40) and the climb up from stepCrate2 at
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

            // Mounted flush to the beam's own underside near its start - pulled in from
            // cameraBeam.x + 40 to + 20, a little further left/closer to stepCrate2, on request.
            // Aimed straight down (90 degrees) at rest, sweeping right (toward the open corridor)
            // to 55 and left (toward stepCrate2) to 135 - NOT a symmetric +/-35 either side of
            // vertical. The angle range has been pulled back twice before (from screenshot reports,
            // not the geometry proof alone: 165, then 140, both let the cone's shallow FOV edge
            // miss the crate's own silhouette and sail past it), landing on 135. Moving the mount
            // left, then asked again to make the cone bigger, meant re-deriving visionFov/
            // visionRange together rather than just scaling the old ones up: closer to the crate,
            // the OLD 55-degree FOV can't safely go past ~90 range before its shallow edge overshoots
            // again (checked directly - swapping in a wider cone at the old FOV shrinks the safe
            // range, it doesn't grow it). A narrower 45-degree FOV, swept through the same 55..135
            // range, buys back much more room before overshooting - up to 160 checked clean, landed
            // on 150 for a small margin. Net effect against the old 55-degree/110-range cone: a
            // meaningfully bigger cone (~70% more swept area) that still never lights a point past
            // stepCrate2's own far edge - reverified the same way as every round before (a dense
            // grid of points/heights past the crate, across the whole sweep, not one probe or a
            // paper estimate). Camera.eyePosition is the lens tip at the end of a rotating arm, not
            // a fixed point the cone merely swivels around like a guard's torch, so the mount
            // position, sweep range, visionFov AND visionRange all have to be re-tuned together
            // against LEVEL_3_LAYOUT directly any time one of them changes, not just the one that
            // was actually asked for. Climbing onto the beam itself puts the player above the cone
            // entirely regardless: a downward-tilted cone can never include a point directly above
            // its own mount.
            val beamCamera = CameraSpawn(
                x = cameraBeam.x + 20.0,
                y = cameraBeam.bottom,
                minAngle = 55.0 * (PI / 180.0),
                maxAngle = 135.0 * (PI / 180.0),
                startAngle = 55.0 * (PI / 180.0),
                sweepSpeed = 0.6,
                // 150, up from 110 (and this game's usual 220) - a genuinely bigger reach than
                // before, not just a bigger number: paired with the narrower 45-degree FOV below,
                // it's the biggest cone that still respects the "never past stepCrate2's far edge"
                // rule with the mount at its new, more-left position (see the maxAngle comment
                // above for the full trade-off).
                visionRange = 150.0,
                // 45, down from 55 - narrower on purpose, not a shrink for its own sake: it's what
                // buys the bigger range above room to work with with the mount moved left. See the
                // maxAngle comment above.
                visionFov = 45.0 * (PI / 180.0),
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
            val fillerBarrelWidth = 32.0
            val fillerBarrelHeight = 48.0
            val fillerBarrels = listOf(
                Rect(x = cameraBeam.x + 30.0, y = groundY - fillerBarrelHeight, width = fillerBarrelWidth, height = fillerBarrelHeight),
                Rect(x = cameraBeam.x + 64.0, y = groundY - fillerBarrelHeight, width = fillerBarrelWidth, height = fillerBarrelHeight)
            )
            val fillerCrate = Rect(x = cameraBeam.x + 106.0, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // Two crates stacked right after the single one - same footprint as the single crate,
            // twice the height (GameplayScene.kt's box-rendering tiles crateBitmap in real 48-unit
            // increments for anything in the "tactical crate" size window, so height = 2*48 draws
            // as two crates on top of each other, not one stretched image). Ends at cameraBeam.x +
            // 252, 18 units clear of cameraLeg's own left edge (cameraBeam.right - 30 = + 270) -
            // comfortable clearance, not a hairline fit.
            val stackedCrateWidth = crateWidth
            val stackedCrateHeight = crateHeight * 2.0
            val stackedCrates = Rect(x = fillerCrate.right + 10.0, y = groundY - stackedCrateHeight, width = stackedCrateWidth, height = stackedCrateHeight)

            // One last hanging crate past the beam - same shape and look as the ground gauntlet's
            // own pair (hangingCrateVariant1), but no guard standing on this one, just an obstacle
            // to cross. Top flush with the beam's own top, so it's a same-height jump across (like
            // hideCrate -> longCrate1 back at the start of the gauntlet), not a climb.
            val finalHangingCrate = Rect(x = cameraBeam.right + 120.0, y = cameraBeam.top, width = longCrateWidth, height = longCrateHeight)

            // A crate right at the hanging crate's own far end, on request - lands the player back
            // on solid ground the instant the jump across finalHangingCrate ends, rather than open
            // ground.
            val hangingEndCrate = Rect(x = finalHangingCrate.right, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            // A plain elevated block after the hanging crate, same idea as GameWorld.createDefault's
            // own block2/block3 in level 1 (95 tall, no crate/table art - falls through to
            // GameplayScene.kt's generic rough-block render, the same "normal platform" look): the
            // player drops back to the ground after the hanging-crate jump, climbs this like any
            // other terrain block, then walks on to the exit.
            val finalPlatformWidth = 260.0
            val finalPlatformHeight = 95.0
            val finalPlatform = Rect(x = hangingEndCrate.right + 80.0, y = groundY - finalPlatformHeight, width = finalPlatformWidth, height = finalPlatformHeight)

            // A crate and a two-stacked pair sitting on TOP of finalPlatform, centred along its
            // width - on request, "in the middle of the platform". Resting directly on the
            // platform's own surface (bottom flush with finalPlatform.top), not floating above it.
            val platformCrateGroupWidth = crateWidth + 20.0 + stackedCrateWidth
            val platformCrateGroupStartX = finalPlatform.x + (finalPlatformWidth - platformCrateGroupWidth) / 2.0
            val platformCrate = Rect(x = platformCrateGroupStartX, y = finalPlatform.top - crateHeight, width = crateWidth, height = crateHeight)
            val platformStackedCrates = Rect(x = platformCrate.right + 20.0, y = finalPlatform.top - stackedCrateHeight, width = stackedCrateWidth, height = stackedCrateHeight)

            val finalExitX = finalPlatform.right + 150.0
            val finalWorldWidth = finalExitX + 200.0
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
                ) + fillerBarrels + listOf(fillerCrate, stackedCrates, platformCrate, platformStackedCrates),
                guards = listOf(roofGuard, overwatchGuard1, overwatchGuard2),
                cameras = listOf(beamCamera),
                barrels = fillerBarrels,
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
            timeTargetSeconds = 90.0f,
            description = "The cargo express conveyor is running in reverse. Vault over oncoming crates, duck under low-hanging cargo, and reach the secure facility.",
            objectiveHint = "Traverse the Conveyor Line",
            layout = LEVEL_4_LAYOUT,
            backgroundImage = "metalbg.png",
            hasDarknessVignette = true,
            playerCrouchForwardSpeedMultiplier = 1.45
        )

        /**
         * A long side-scrolling level built from three storeys.
         *
         * Surfaces (top edge): ground 440, mid tiers 368, high tier 296. Every step up is 36 -
         * comfortably inside the 45 unit jump the player physics allow (jumpSpeed 300, gravity
         * 1000), and no gap exceeds the ~72 units covered during a full 0.6s jump arc.
         *
         * Intended route, which never enters a guard cone (see the walkthrough test in
         * GameplayModelTest.testSideScrollLevelIsBeatable):
         *   1. Ground start, climb the step boxes at x=200/270 up onto mid tier 1.
         *   2. Walk mid tier 1 (330..1130) straight over guard 1, who patrols the ground
         *      below - the tier floor blocks his line of sight.
         *   3. Step onto the box at x=1070 and walk off its end, dropping to the ground at
         *      x~1200, which is past guard 1 reach (he turns at 880, and sees 200 ahead).
         *   4. Cross the open ground (1130..1700), which no guard patrols.
         *   5. Climb the step boxes at x=1700/1770 onto mid tier 2 (1830..2660), passing
         *      over guard 3 the same way.
         *   6. Drop off the far end at x~2660, beyond guard 3 reach, and walk into the exit.
         *
         * That same box at x=1070 doubles as the springboard to the optional high tier
         * (1170..1600): jumping from it clears the 40 unit gap, while simply walking off it
         * falls short and passes underneath. Guard 2 patrols up there and can be skipped.
         */
        val SIDE_SCROLL_LEVEL_LAYOUT = LevelLayout(
            worldWidth = 2800.0,
            playerStartX = 236.0,
            playerStartY = 440.0 - 96.0,
            exitZone = Rect(x = 2700.0, y = 340.0, width = 44.0, height = 100.0),
            platforms = listOf(
                Rect(x = 0.0, y = 440.0, width = 2800.0, height = 60.0),   // ground
                Rect(x = 330.0, y = 368.0, width = 800.0, height = 14.0),  // mid tier 1
                Rect(x = 1210.0, y = 296.0, width = 390.0, height = 14.0), // high tier (optional)
                Rect(x = 1830.0, y = 368.0, width = 830.0, height = 14.0)  // mid tier 2
            ),
            boxes = listOf(
                Rect(x = 270.0, y = 368.0, width = 60.0, height = 72.0),  // step onto mid tier 1
                Rect(x = 1070.0, y = 332.0, width = 60.0, height = 36.0), // end of mid tier 1
                Rect(x = 1280.0, y = 260.0, width = 60.0, height = 36.0), // cover on high tier
                Rect(x = 1700.0, y = 404.0, width = 70.0, height = 36.0), // step up from ground
                Rect(x = 1770.0, y = 368.0, width = 60.0, height = 72.0)  // step onto mid tier 2
            ),
            guards = listOf(
                GuardSpawn(
                    startX = 860.0, surfaceY = 440.0,
                    patrolMinX = 480.0, patrolMaxX = 880.0,
                    speed = 55.0, facing = -1.0, visionRange = 200.0
                ),
                GuardSpawn(
                    startX = 1400.0, surfaceY = 296.0,
                    patrolMinX = 1350.0, patrolMaxX = 1550.0,
                    speed = 55.0, facing = 1.0, visionRange = 200.0
                ),
                GuardSpawn(
                    startX = 2350.0, surfaceY = 440.0,
                    patrolMinX = 2100.0, patrolMaxX = 2380.0,
                    speed = 65.0, facing = -1.0, visionRange = 240.0
                )
            )
        )

        val SIDE_SCROLL_LEVEL = LevelData(
            id = "level_5",
            name = "05: Restricted Zone",
            timeTargetSeconds = 60.0f,
            description = "The trail leads into a guarded cargo section. Get inside and discover what they are protecting.",
            objectiveHint = "Get Into the Restricted Area",
            coinRewardBase = 90,
            coinRewardPerStar = 40,
            layout = SIDE_SCROLL_LEVEL_LAYOUT
        )

        // Levels 6-13 continue the shipyard story on the same single-screen arena
        // (GameWorld.createDefault) levels 1 and 3 already use - no layout of their own yet, just a
        // progressively faster/tighter guard per level for a difficulty curve. timeTargetSeconds
        // is an estimate carried forward from that same pattern, not device/playtest-verified.
        val DEFAULT_LEVEL_6 = LevelData(
            id = "level_6",
            name = "06: Missing Container",
            timeTargetSeconds = 26.0f,
            description = "Container 17 appears in the records from your crew's final job. Find it and learn where it went.",
            objectiveHint = "Find Container 17",
            guardSpeed = 80.0,
            guardPatrolMinX = 2700.0,
            guardPatrolMaxX = 3150.0
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
