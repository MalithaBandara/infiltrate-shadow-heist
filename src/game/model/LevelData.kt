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
    val patrolPauseDuration: Double = 0.0
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
    val sweepDirection: Double = 1.0
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
    val swingHooks: List<Rect> = emptyList()
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
    val handwrittenCallout: String? = null
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
    val tutorialSteps: List<TutorialStep> = emptyList()
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
                    handwrittenCallout = "Reach the objective!"
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
         * Work in progress. First section built: a two-tier barrel staircase blocking the ground
         * path - an 8-wide bottom layer with a 4-wide top layer sitting on its far half, so the
         * near half of the bottom layer is left exposed as a real landing spot. That matters
         * physically, not just visually: the engine's climb/jump check only ever looks at one
         * box's own bottom/top face (see Player.findClimbTarget), so a second layer sitting
         * directly above the first IN THE SAME COLUMNS would occupy the only spot the player could
         * land on to reach it (the player's own height, 96, equals two stacked 48-tall layers),
         * leaving no way up at all. Offsetting the top layer sideways instead of stacking it
         * in-place turns the climb into two ordinary adjacent-box jumps (ground -> bottom layer's
         * exposed half -> top layer -> terrain1), the same proven pattern as LEVEL_2_LAYOUT's
         * crate1 (48 units, comfortably under Player.maxJumpHeight 51.2).
         *
         * Past the barrel stack, terrain1 sits one more 48-unit jump higher (296, matching this
         * game's established "high tier" height - see SIDE_SCROLL_LEVEL_LAYOUT/LEVEL_2_LAYOUT) and
         * is a solid block reaching all the way down to the ground (144 tall, like LEVEL_2's
         * terrain blocks) rather than a thin floating platform with open air beneath it.
         *
         * The gap after terrain1 is crossed by swinging from the hook hanging over it: walk into
         * it and press JUMP, which is the same button the climb already uses. It is the only way
         * across - the gap is twice a running jump - and it is the one place in the game the swing
         * exists at all, so the geometry here and Player's swing constants are a matched pair.
         */
        val LEVEL_4_LAYOUT = run {
            val groundY = 440.0
            val ground = Rect(x = 0.0, y = groundY, width = 1800.0, height = 100.0)

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

            // The gap is crossed by swinging from the hook below, and its width is derived from
            // that move rather than chosen: the swing is a fixed shape (Player.swingLandAhead),
            // so the level is sized to the swing, not the other way round. 150 is also well past
            // a running jump - the arc covers about 84 units - so the hook is the only way over,
            // which is the point of the section.
            val gapWidth = 150.0
            val terrain2 = Rect(x = terrain1.right + gapWidth, y = terrainTopY, width = 300.0, height = terrainHeight)

            // The chain-and-hook (hook.png) the player swings from. Positioned by its GRIP - the
            // point inside the bend the hand closes on, see Player.HOOK_GRIP_X/Y_FRACTION - with
            // the art hung off that, not the other way round. Three numbers, all constrained:
            //
            //  - The grip sits at the centre of the gap, which is where a crane hook over a hole
            //    in a dock belongs. That makes the leap to it the long half of the move - about 90
            //    units from the lip, against 112 out of the release - so SWING_PACING_CURVE gives
            //    the launch 0.45s to cover it at a believable ~200 units/sec rather than the rate
            //    the clip itself runs at. Player.swingLandAhead is then set so the touchdown lands
            //    34 units onto terrain2, so the two are a matched pair: moving one moves both.
            //  - The grip hangs 112 above the ledge, which puts the hook's own business end - the
            //    bottom ~12% of hook.png, the rest is chain - a few units clear of a standing
            //    player's head (they are 96 tall), so it reads as something to jump for. It cannot
            //    go much higher: this game's camera shows only about 140 units above a high tier,
            //    and hanging the grip where the leap would gain real height puts the hook itself
            //    off the top of the screen. See the swing notes in .junie/guidelines.md.
            //  - Width 16 keeps the chain noticeably thinner than the player, and leaves a dozen
            //    or so units of it visible running off-screen above the hook.
            val hookWidth = 16.0
            val hookHeight = hookWidth * (2136.0 / 154.0) // hook.png's own cropped aspect ratio
            val hookGripX = terrain1.right + gapWidth / 2.0
            val hookGripY = terrainTopY - 112.0
            val swingHook = Rect(
                x = hookGripX - hookWidth * Player.HOOK_GRIP_X_FRACTION,
                y = hookGripY - hookHeight * Player.HOOK_GRIP_Y_FRACTION,
                width = hookWidth,
                height = hookHeight
            )

            LevelLayout(
                worldWidth = 1800.0,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = terrain2.right + 100.0, y = groundY - 100.0, width = 44.0, height = 100.0),
                platforms = listOf(ground),
                boxes = barrelWall + listOf(terrain1, terrain2),
                guards = emptyList(),
                barrels = barrelWall,
                swingHooks = listOf(swingHook)
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
         * proven climb up from a single crate - the exact shape of LEVEL_2_LAYOUT's crate1 ->
         * terrain step (crate flush against the target box's left face, climb rise 96 - inside
         * Player's climbMinHeight..climbMaxHeight window of 51.2..115.0). Unlike the barrel-wall
         * climbs elsewhere, which stay reachable by plain jump, this one is a real mantle: the
         * table's own collision is solid from its top down to the ground (see LevelLayout.tables),
         * matching what Player.findClimbTarget requires - a real face to brace against, not a
         * floating ledge - and coincidentally means the art (drawn at exactly this box's own
         * width/height) needs no separate "draw past the box to the ground" logic; the leg's own
         * foot already lands exactly on the ground because the box does. Being solid, the table
         * also blocks the ground path entirely - the climb isn't a shortcut here, it's the only
         * way past. Past the table, the player crosses it and drops back to the ground (a fall is
         * never fatal) to reach the exit.
         *
         * The table's collision is two boxes, not one (see [LevelLayout.tableParts]): the plank
         * along the top, 30 deep to match the art's own slab, and a leg column at the left end
         * from the plank top to the ground. The leg is what the climb braces against (its top is
         * the plank top, so the mantle lands on the roof; its bottom reaches the crate top, which
         * Player.findClimbTarget requires of a face), and it still blocks the ground path from
         * the left. What that buys is the open underside, which is where the guard stands.
         *
         * The guard. One, pacing the underside: he stands at the far-end post (860, facing RIGHT,
         * out past the roof's end) for [Guard.patrolPauseDuration], walks to the near post by the
         * leg (560), stands there facing left, walks back, and repeats - the owner's spec, "stay
         * idle -> walk -> stay idle -> come back". The leg and plank are occluders, so wherever he
         * is, the climb and the crossing above are blind to him. The beat is the drop off the far
         * end: from the far post facing right his cone (220) covers the landing zone out to ~1100,
         * so the player on the roof watches the beam poke out past the plank's end and drops
         * while he is away at the near post - a timing read, with the cone itself as the tell.
         * Dropping while he stands at 860 lands in the beam at point-blank. He is 30 wide by 96
         * tall, the player's own height, so the two silhouettes read at one scale.
         *
         * table.png's crop is now the STRICT alpha bbox (threshold >10), not PIL's own getbbox() -
         * an earlier pass used getbbox() directly and it turned out to include ~55px of nearly
         * (but not fully) transparent fringe below the leg's real foot, invisible in the source
         * but a visible sliver of "floating" once that fringe got stretched across the full box
         * height in-game. Re-derive with the strict threshold if this asset is ever rebuilt.
         */
        val LEVEL_3_LAYOUT = run {
            val groundY = 440.0
            val worldWidth = 1550.0
            val ground = Rect(x = 0.0, y = groundY, width = worldWidth, height = 100.0)

            val crateWidth = 68.0
            val crateHeight = 48.0
            val crateX = 420.0 // short run-up from the start fence, matching this file's own "400" convention
            val crate = Rect(x = crateX, y = groundY - crateHeight, width = crateWidth, height = crateHeight)

            val tableWidth = 450.0 // much longer cantilevered plank
            val tableElevation = 144.0 // crate top (392) to table top (296) = 96, inside the 51.2..115.0 climb window
            val table = Rect(x = crate.right, y = groundY - tableElevation, width = tableWidth, height = tableElevation)
            // Measured off table.png (2048x512): the slab is rows 0..102 = 28.7 of 144 units, with
            // hanging brackets to row ~118; the leg is columns 36..79 = 8..17 units in from the
            // left edge, its brace reaching ~31 units in. The leg box starts at the table's own
            // left edge so the climb's lip is where the art's lip is, and is wide enough to cover
            // the brace so nothing pokes out of it.
            val tablePlankDepth = 30.0
            val tableLegWidth = 30.0
            val tableLeg = Rect(x = table.x, y = table.y, width = tableLegWidth, height = table.height)
            val tablePlank = Rect(x = tableLeg.right, y = table.y, width = table.width - tableLegWidth, height = tablePlankDepth)

            val guardWidth = 30.0
            val guardHeight = 96.0
            val guardFarPost = table.right - 78.0 // 860: under the roof, 48 short of its end, 30 wide
            val guardNearPost = tableLeg.right + 42.0 // 560: a stride clear of the leg's brace
            val roofGuard = GuardSpawn(
                startX = guardFarPost, surfaceY = groundY,
                patrolMinX = guardNearPost, patrolMaxX = guardFarPost,
                speed = 55.0, facing = 1.0, visionRange = 220.0,
                width = guardWidth, height = guardHeight,
                patrolPauseDuration = 3.0
            )

            LevelLayout(
                worldWidth = worldWidth,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = table.right + 300.0, y = groundY - 100.0, width = 44.0, height = 100.0),
                platforms = listOf(ground),
                boxes = listOf(crate, tableLeg, tablePlank),
                guards = listOf(roofGuard),
                tables = listOf(table),
                tableParts = listOf(tableLeg, tablePlank)
            )
        }

        val DEFAULT_LEVEL_3 = LevelData(
            id = "level_3",
            name = "03: New Level",
            timeTargetSeconds = 25.0f,
            layout = LEVEL_3_LAYOUT
        )

        val DEFAULT_LEVEL_4 = LevelData(
            id = "level_4",
            name = "04: Blind Spot",
            timeTargetSeconds = 25.0f,
            description = "The shipyard is guarded. Slip through security and continue searching for signs of your old crew.",
            objectiveHint = "Get Past the Guards",
            layout = LEVEL_4_LAYOUT,
            backgroundImage = "bgmg6.png"
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
