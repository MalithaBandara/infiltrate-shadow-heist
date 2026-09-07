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
    val visionRange: Double = 220.0
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
    val barrels: List<Rect> = emptyList()
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
    val backgroundImage: String? = null
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
     * Levels 1–5 (Easy): 1★ = 100, 2★ = 200, 3★ = 350 (Lifetime 3★ = 1,750)
     * Levels 6–12 (Hard): 1★ = 200, 2★ = 400, 3★ = 700 (Lifetime 3★ = 4,900)
     * Total lifetime earn across 12 levels = 6,650 coins.
     */
    fun getCoinReward(starCount: Int): Int {
        if (starCount <= 0) return 0
        val levelNum = id.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1
        val isHard = levelNum in 6..12 || id.contains("hard") || id.contains("dlc")
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
            guardEnabled = false
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
            val ground = Rect(x = 0.0, y = groundY, width = 2600.0, height = 100.0)

            // 1. First step: ground -> crate1, a plain jump (same 68x48 footprint as the small
            // crate in GameWorld.createDefault(), so it renders and behaves the same way).
            val crate1 = Rect(x = 400.0, y = 392.0, width = 68.0, height = 48.0)

            // 2. The climb: crate1's top -> the elevated terrain block. Terrain height (144) is
            // 3 times the crate height (48), putting the top edge at y=296.0 - exactly at the player's
            // head level (392 - 96 = 296) when standing on crate1, so the climb/vault feels natural.
            val terrain = Rect(x = 468.0, y = 296.0, width = 400.0, height = 144.0)

            // 3. Rescue barrel: sits on the ground flush against the terrain block's right face.
            // Sized at 32x48 (matching barrel.png aspect ratio), sitting on ground at y=392.0.
            // Players who fall into the gap can jump onto the barrel and climb back up onto the terrain.
            val rescueBarrel = Rect(x = 868.0, y = 392.0, width = 32.0, height = 48.0)

            // 4. The gap crossing: three crates suspended over open air, landing at y=296.0 (matching terrain).
            // Only the rectangular crate body is interactable (collidable).
            // hangingCrate1 is chainedcrate.png (width 174, rectangular crate height 38).
            // hangingCrate2/3 are chainedcrate2.png at smaller size (width 76, height 38, matching crate1 height).
            val hangingCrate1 = Rect(x = 946.0, y = 296.0, width = 174.0, height = 38.0)
            val hangingCrate2 = Rect(x = 1190.0, y = 296.0, width = 76.0, height = 38.0)
            val hangingCrate3 = Rect(x = 1336.0, y = 296.0, width = 76.0, height = 38.0)

            // 5. Far side: landing terrain block at y=296.0, same height as the near one.
            val farTerrain = Rect(x = 1482.0, y = 296.0, width = 480.0, height = 144.0)

            val boxes = listOf(crate1, terrain, rescueBarrel, hangingCrate1, hangingCrate2, hangingCrate3, farTerrain)

            LevelLayout(
                worldWidth = 2600.0,
                playerStartX = 236.0,
                playerStartY = groundY - 96.0,
                exitZone = Rect(x = 2440.0, y = 340.0, width = 44.0, height = 100.0),
                platforms = listOf(ground),
                boxes = boxes,
                guards = listOf(
                    GuardSpawn(
                        startX = 2360.0, surfaceY = groundY,
                        patrolMinX = 2050.0, patrolMaxX = 2380.0,
                        speed = 75.0, facing = -1.0, visionRange = 220.0
                    )
                ),
                hangingCrateVariant1 = listOf(hangingCrate1),
                hangingCrateVariant2 = listOf(hangingCrate2, hangingCrate3),
                barrels = listOf(rescueBarrel)
            )
        }

        val DEFAULT_LEVEL_2 = LevelData(
            id = "level_2",
            name = "02: Cargo Yard",
            timeTargetSeconds = 32.0f,
            description = "Search the outer yard for clues and find a route toward the areas connected to the stolen cargo.",
            objectiveHint = "Find a Way Through the Yard",
            layout = LEVEL_2_LAYOUT
        )

        val DEFAULT_LEVEL_3 = LevelData(
            id = "level_3",
            name = "03: Blind Spot",
            timeTargetSeconds = 25.0f,
            description = "The shipyard is guarded. Slip through security and continue searching for signs of your old crew.",
            objectiveHint = "Get Past the Guards",
            guardSpeed = 95.0,
            guardPatrolMinX = 2600.0,
            guardPatrolMaxX = 3100.0
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
            id = "level_4",
            name = "04: Restricted Zone",
            timeTargetSeconds = 60.0f,
            description = "The trail leads into a guarded cargo section. Get inside and discover what they are protecting.",
            objectiveHint = "Get Into the Restricted Area",
            coinRewardBase = 90,
            coinRewardPerStar = 40,
            layout = SIDE_SCROLL_LEVEL_LAYOUT
        )

        // Levels 5-12 continue the shipyard story on the same single-screen arena
        // (GameWorld.createDefault) levels 1-3 already use - no layout of their own yet, just a
        // progressively faster/tighter guard per level for a difficulty curve. timeTargetSeconds
        // is an estimate carried forward from that same pattern, not device/playtest-verified.
        val DEFAULT_LEVEL_5 = LevelData(
            id = "level_5",
            name = "05: Missing Container",
            timeTargetSeconds = 26.0f,
            description = "Container 17 appears in the records from your crew's final job. Find it and learn where it went.",
            objectiveHint = "Find Container 17",
            guardSpeed = 80.0,
            guardPatrolMinX = 2700.0,
            guardPatrolMaxX = 3150.0
        )

        val DEFAULT_LEVEL_6 = LevelData(
            id = "level_6",
            name = "06: Stolen Manifest",
            timeTargetSeconds = 25.0f,
            description = "The container is missing. Search the offices for records that reveal who moved it and where it went.",
            objectiveHint = "Find the Shipping Records",
            guardSpeed = 85.0,
            guardPatrolMinX = 2650.0,
            guardPatrolMaxX = 3120.0
        )

        val DEFAULT_LEVEL_7 = LevelData(
            id = "level_7",
            name = "07: Cold Trail",
            timeTargetSeconds = 24.0f,
            description = "The records point deeper into the shipyard. Follow the trail and uncover evidence of recent activity.",
            objectiveHint = "Follow the Cargo Trail",
            guardSpeed = 90.0,
            guardPatrolMinX = 2600.0,
            guardPatrolMaxX = 3100.0
        )

        val DEFAULT_LEVEL_8 = LevelData(
            id = "level_8",
            name = "08: Old Signature",
            timeTargetSeconds = 23.0f,
            description = "You find your crew's signature at the shipyard. Follow the clues to prove someone from the crew survived.",
            objectiveHint = "Find Your Crew's Mark",
            guardSpeed = 95.0,
            guardPatrolMinX = 2600.0,
            guardPatrolMaxX = 3080.0
        )

        val DEFAULT_LEVEL_9 = LevelData(
            id = "level_9",
            name = "09: Open Yard",
            timeTargetSeconds = 24.0f,
            description = "The trail continues across an exposed yard. Cross it unseen and stay close to the evidence.",
            objectiveHint = "Cross the Yard Undetected",
            guardSpeed = 100.0,
            guardPatrolMinX = 2550.0,
            guardPatrolMaxX = 3050.0
        )

        val DEFAULT_LEVEL_10 = LevelData(
            id = "level_10",
            name = "10: Ghost Chase",
            timeTargetSeconds = 23.0f,
            description = "A mysterious figure appears ahead, moving like one of your old crew. Follow them before they vanish.",
            objectiveHint = "Follow the Stranger",
            guardSpeed = 105.0,
            guardPatrolMinX = 2550.0,
            guardPatrolMaxX = 3030.0
        )

        val DEFAULT_LEVEL_11 = LevelData(
            id = "level_11",
            name = "11: Hidden Cargo",
            timeTargetSeconds = 22.0f,
            description = "You finally reach Container 17. Open it and uncover what links the cargo to your crew's disappearance.",
            objectiveHint = "Open Container 17",
            guardSpeed = 110.0,
            guardPatrolMinX = 2500.0,
            guardPatrolMaxX = 3000.0
        )

        val DEFAULT_LEVEL_12 = LevelData(
            id = "level_12",
            name = "12: Final Proof",
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
            SIDE_SCROLL_LEVEL,
            DEFAULT_LEVEL_5,
            DEFAULT_LEVEL_6,
            DEFAULT_LEVEL_7,
            DEFAULT_LEVEL_8,
            DEFAULT_LEVEL_9,
            DEFAULT_LEVEL_10,
            DEFAULT_LEVEL_11,
            DEFAULT_LEVEL_12
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
            for (id in storedIds) {
                removeRaw?.invoke("level_result_$id")
            }
            removeRaw?.invoke("level_results_ids")
        } catch (_: Throwable) {
        }
    }
}
