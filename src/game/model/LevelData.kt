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
    val cameras: List<CameraSpawn> = emptyList()
)

data class LevelData(
    val id: String = "level_1",
    val name: String = "Infiltration",
    val timeTargetSeconds: Float = 15.0f,
    val description: String = "Infiltrate the perimeter and reach the extraction zone undetected.",
    val guardSpeed: Double = 60.0,
    val guardPatrolMinX: Double = 300.0,
    val guardPatrolMaxX: Double = 600.0,
    val coinRewardBase: Int = 50,
    val coinRewardPerStar: Int = 25,
    val layout: LevelLayout? = null,
    val cameras: List<CameraSpawn> = emptyList()
) {
    companion object {
        val DEFAULT_LEVEL_1 = LevelData(
            id = "level_1",
            name = "01: Warehouse Infiltration",
            timeTargetSeconds = 15.0f,
            description = "Infiltrate the warehouse perimeter, bypass the guard patrol, and reach extraction.",
            guardSpeed = 60.0,
            guardPatrolMinX = 300.0,
            guardPatrolMaxX = 600.0,
            cameras = listOf(
                CameraSpawn(
                    x = 660.0,
                    y = 180.0,
                    minAngle = (90.0 - 30.0) * (PI / 180.0),
                    maxAngle = (90.0 + 30.0) * (PI / 180.0),
                    startAngle = (90.0 - 30.0) * (PI / 180.0),
                    sweepSpeed = 0.7,
                    visionRange = 240.0,
                    visionFov = 45.0 * (PI / 180.0)
                )
            )
        )

        val DEFAULT_LEVEL_2 = LevelData(
            id = "level_2",
            name = "02: Office Heist",
            timeTargetSeconds = 14.0f,
            description = "Navigate through tight security corridors. Stay crouched to avoid noise detection.",
            guardSpeed = 75.0,
            guardPatrolMinX = 260.0,
            guardPatrolMaxX = 640.0
        )

        val DEFAULT_LEVEL_3 = LevelData(
            id = "level_3",
            name = "03: Vault Security",
            timeTargetSeconds = 12.0f,
            description = "High alert vault sector with rapid guard sweeps. Quick timing is critical.",
            guardSpeed = 95.0,
            guardPatrolMinX = 220.0,
            guardPatrolMaxX = 660.0
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
            playerStartX = 60.0,
            playerStartY = 440.0 - 96.0,
            exitZone = Rect(x = 2700.0, y = 340.0, width = 44.0, height = 100.0),
            platforms = listOf(
                Rect(x = 0.0, y = 440.0, width = 2800.0, height = 60.0),   // ground
                Rect(x = 330.0, y = 368.0, width = 800.0, height = 14.0),  // mid tier 1
                Rect(x = 1210.0, y = 296.0, width = 390.0, height = 14.0), // high tier (optional)
                Rect(x = 1830.0, y = 368.0, width = 830.0, height = 14.0)  // mid tier 2
            ),
            boxes = listOf(
                Rect(x = 200.0, y = 404.0, width = 70.0, height = 36.0),  // step up from ground
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
            name = "04: Rooftop Approach",
            timeTargetSeconds = 60.0f,
            description = "A long perimeter run. Use the upper walkways to cross over the patrols below.",
            coinRewardBase = 90,
            coinRewardPerStar = 40,
            layout = SIDE_SCROLL_LEVEL_LAYOUT
        )

        val DEFAULT_LEVELS: List<LevelData> = listOf(
            DEFAULT_LEVEL_1,
            DEFAULT_LEVEL_2,
            DEFAULT_LEVEL_3,
            SIDE_SCROLL_LEVEL
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
        return inMemoryFallback.getAllResults()
    }

    override fun clear() {
        inMemoryFallback.clear()
    }
}
