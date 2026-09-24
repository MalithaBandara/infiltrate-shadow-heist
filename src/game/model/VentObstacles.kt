package game.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Declarative definition of an industrial ventilation exhaust fan.
 *
 * Inside the fan's wind zone, high-velocity air blows backwards against the player
 * ([windDirection] = -1.0). Continuous forward crawling cannot overcome the pushback speed
 * ([windPushSpeed]), so holding forward causes the player to drift slowly backward.
 * Rapidly pressing (spam-tapping) forward delivers discrete stride impulses ([fanImpulse])
 * that overcome the wind resistance, allowing the player to push through.
 */
data class VentFanDef(
    val id: String,
    val x: Double,
    val y: Double,
    val width: Double = 36.0,
    val height: Double = 68.0,
    val windRange: Double = 300.0,
    val windPushSpeed: Double = 120.0,
    val windDirection: Double = -1.0,
    val fanImpulse: Double = 10.0
) {
    val bounds: Rect get() = Rect(x, y, width, height)

    val windMinX: Double get() = if (windDirection < 0.0) x - windRange else x + width
    val windMaxX: Double get() = if (windDirection < 0.0) x else x + width + windRange

    fun isPlayerInWind(player: Player): Boolean {
        val minX = minOf(windMinX, windMaxX)
        val maxX = maxOf(windMinX, windMaxX)
        val minY = minOf(y - 10.0, 300.0)
        val maxY = maxOf(y + height + 10.0, 450.0)
        val p = player.bounds
        return p.right >= minX && p.left <= maxX && p.bottom >= minY && p.top <= maxY
    }
}

/**
 * Runtime simulation instance for an exhaust fan.
 */
class VentFan(
    val id: String,
    val x: Double,
    val y: Double,
    val width: Double = 36.0,
    val height: Double = 68.0,
    val windRange: Double = 300.0,
    val windPushSpeed: Double = 120.0,
    val windDirection: Double = -1.0,
    val fanImpulse: Double = 10.0
) {
    var bladeRotationAngle: Double = 0.0
        private set

    val bounds: Rect get() = Rect(x, y, width, height)

    val windMinX: Double get() = if (windDirection < 0.0) x - windRange else x + width
    val windMaxX: Double get() = if (windDirection < 0.0) x else x + width + windRange

    fun isPlayerInWind(player: Player): Boolean {
        val minX = minOf(windMinX, windMaxX)
        val maxX = maxOf(windMinX, windMaxX)
        val minY = minOf(y - 10.0, 300.0)
        val maxY = maxOf(y + height + 10.0, 450.0)
        val p = player.bounds
        return p.right >= minX && p.left <= maxX && p.bottom >= minY && p.top <= maxY
    }

    fun update(dt: Double) {
        // Measured industrial turbine rotation (8.0 rad/s ≈ 1.27 rev/s)
        bladeRotationAngle = (bladeRotationAngle + 8.0 * dt) % (2.0 * PI)
    }

    fun reset() {
        bladeRotationAngle = 0.0
    }

    constructor(def: VentFanDef) : this(
        id = def.id,
        x = def.x,
        y = def.y,
        width = def.width,
        height = def.height,
        windRange = def.windRange,
        windPushSpeed = def.windPushSpeed,
        windDirection = def.windDirection,
        fanImpulse = def.fanImpulse
    )
}

/**
 * Declarative definition of an autonomous patrolling camera bot.
 *
 * A compact wheeled/tracked drone that patrols back and forth along the vent floor.
 * It casts a forward-facing surveillance light cone. If the player enters its vision cone,
 * an alert is triggered. When the player sneaks up behind the bot within [deactivationRange],
 * the player can press INTERACT to permanently disable it.
 */
data class CameraBotDef(
    val id: String,
    val startX: Double,
    val surfaceY: Double,
    val patrolMinX: Double,
    val patrolMaxX: Double,
    val speed: Double = 36.0,
    val facing: Double = 1.0,
    val width: Double = 32.0,
    val height: Double = 26.0,
    val pauseDuration: Double = 1.0,
    val visionRange: Double = 120.0,
    val visionFov: Double = 40.0 * (PI / 180.0),
    val deactivationRange: Double = 52.0
)

/**
 * Runtime simulation instance for a camera bot.
 */
class CameraBot(
    val id: String,
    var x: Double,
    val surfaceY: Double,
    val patrolMinX: Double,
    val patrolMaxX: Double,
    var speed: Double = 36.0,
    var facing: Double = 1.0,
    val width: Double = 32.0,
    val height: Double = 26.0,
    val pauseDuration: Double = 1.0,
    var visionRange: Double = 120.0,
    var visionFov: Double = 40.0 * (PI / 180.0),
    val deactivationRange: Double = 52.0
) {
    val y: Double = surfaceY - height

    var isDeactivated: Boolean = false
        private set

    var pauseTimer: Double = 0.0
        private set

    val bounds: Rect get() = Rect(x, y, width, height)

    val eyePosition: Vec2d
        get() {
            val eyeX = if (facing > 0.0) x + width + 2.0 else x - 2.0
            val eyeY = y + height * EYE_HEIGHT_FRACTION
            return Vec2d(eyeX, eyeY)
        }

    val facingAngle: Double
        get() = if (facing > 0.0) 0.0 else PI

    fun canDeactivate(player: Player): Boolean {
        if (isDeactivated) return false
        val playerCenterX = player.centerX
        val botCenterX = x + width / 2.0
        val dist = abs(playerCenterX - botCenterX)
        if (dist > deactivationRange) return false

        // Player must be behind the bot:
        // When facing right (> 0), player must approach from the left (player.x <= x + 8.0).
        // When facing left (< 0), player must approach from the right (player.x + player.width >= x + width - 8.0).
        val isBehind = if (facing > 0.0) {
            player.x <= x + 8.0
        } else {
            player.x + player.width >= x + width - 8.0
        }
        return isBehind
    }

    fun deactivate() {
        isDeactivated = true
    }

    val startX: Double = x
    val startFacing: Double = facing

    fun reset() {
        x = startX
        facing = startFacing
        pauseTimer = 0.0
        isDeactivated = false
    }

    fun update(dt: Double) {
        if (isDeactivated) return

        if (pauseTimer > 0.0) {
            pauseTimer = (pauseTimer - dt).coerceAtLeast(0.0)
            return
        }

        x += facing * speed * dt

        if (facing > 0.0 && x >= patrolMaxX) {
            x = patrolMaxX
            facing = -1.0
            if (pauseDuration > 0.0) pauseTimer = pauseDuration
        } else if (facing < 0.0 && x <= patrolMinX) {
            x = patrolMinX
            facing = 1.0
            if (pauseDuration > 0.0) pauseTimer = pauseDuration
        }
    }

    constructor(def: CameraBotDef) : this(
        id = def.id,
        x = def.startX,
        surfaceY = def.surfaceY,
        patrolMinX = def.patrolMinX,
        patrolMaxX = def.patrolMaxX,
        speed = def.speed,
        facing = def.facing,
        width = def.width,
        height = def.height,
        pauseDuration = def.pauseDuration,
        visionRange = def.visionRange,
        visionFov = def.visionFov,
        deactivationRange = def.deactivationRange
    )

    companion object {
        /**
         * How far below the top of the bot's box its lens sits, as a fraction of [height], and so
         * where the surveillance cone starts.
         *
         * Was 0.45 - mid-box - for the old procedural crawler, whose turret was a stub on top of a
         * squat hull. The rover art that replaced it carries its sensor on a boom held out over the
         * front wheel, and the front of the box at mid-height is empty air, so the cone used to
         * leave from beside the machine rather than from anything on it. This lifts it to the
         * boom's tip. The cone is horizontal and 40 degrees wide over 120 units, so at full range
         * it still covers most of a standing player either way; what changes is that a player
         * crouched right under the boom is a little safer and one on a crate a little less so.
         */
        const val EYE_HEIGHT_FRACTION: Double = 0.20
    }
}

/**
 * Mounting location for pressurized steam / hot air pipes.
 */
enum class PipeMountType {
    TOP,
    BOTTOM,
    PAIR
}

/**
 * Declarative definition of a pressurized steam/air pipe hazard in the vent.
 *
 * Cycles between active (blasting lethal pressurized steam), dormant (inactive), and warning.
 * Warning window is exactly 1.0s where the green sign LED is displayed before steam erupts.
 * Gas emission active durations and rest intervals vary per cycle (non-constant, non-periodic).
 * Touching active steam triggers instant Mission Failed (blockable once with Laser Shield).
 */
data class SteamPipeDef(
    val id: String,
    val x: Double,
    val topY: Double = 372.0,
    val bottomY: Double = 440.0,
    val mountType: PipeMountType = PipeMountType.TOP,
    val jetWidth: Double = 24.0,
    val activeDuration: Double = 1.5,
    val inactiveDuration: Double = 2.0,
    val phaseOffsetSeconds: Double = 0.0
)

/**
 * Runtime simulation instance for a steam/air pipe.
 */
class SteamPipe(
    val id: String,
    val x: Double,
    val topY: Double = 372.0,
    val bottomY: Double = 440.0,
    val mountType: PipeMountType = PipeMountType.TOP,
    val jetWidth: Double = 24.0,
    val activeDuration: Double = 1.5,
    val inactiveDuration: Double = 2.0,
    val phaseOffsetSeconds: Double = 0.0
) {
    data class Cycle(
        val start: Double,
        val activeEnd: Double,
        val dormantEnd: Double,
        val end: Double
    ) {
        val activeDuration: Double get() = activeEnd - start
        val dormantDuration: Double get() = dormantEnd - activeEnd
        val warningDuration: Double get() = end - dormantEnd // exactly 1.0s
    }

    val cycles: List<Cycle> = generateCycles()
    val loopDuration: Double = cycles.last().end

    private fun generateCycles(): List<Cycle> {
        val list = ArrayList<Cycle>(64)
        var t = 0.0
        // Deterministic pseudo-random seed unique to this pipe instance
        var rng = id.hashCode() xor 0x5a5a5a5a

        fun nextRandom(): Double {
            rng = rng * 1664525 + 1013904223
            return (((rng ushr 8) and 0xFFFFFF).toDouble()) / 16777216.0
        }

        for (i in 0 until 64) {
            val act = if (i == 0) {
                activeDuration
            } else {
                // Non-constant gas emission duration: varies around activeDuration (~2.2s - 3.8s)
                val variation = 0.85 + 0.65 * nextRandom()
                (activeDuration * variation).coerceIn(2.2, 3.8)
            }
            val dormant = if (i == 0) {
                inactiveDuration.coerceIn(0.8, 1.8)
            } else {
                // Non-constant dormant rest duration (shorter deactive interval: ~0.8s - 1.8s + 1s warning)
                val variation = 0.80 + 0.70 * nextRandom()
                (inactiveDuration * variation).coerceIn(0.8, 1.8)
            }
            val warn = 1.0 // Warning phase with green sign is exactly 1.0 second
            val activeEnd = t + act
            val dormantEnd = activeEnd + dormant
            val cycleEnd = dormantEnd + warn
            list.add(Cycle(t, activeEnd, dormantEnd, cycleEnd))
            t = cycleEnd
        }
        return list
    }

    var isActive: Boolean = false
        private set

    var isWarning: Boolean = false
        private set

    /** 0..1 warning indicator progress right before steam erupts (green sign on for 1.0s). */
    var warningProgress: Double = 0.0
        private set

    val bounds: Rect
        get() = Rect(x - jetWidth / 2.0, topY, jetWidth, bottomY - topY)

    fun update(totalElapsedSeconds: Double) {
        if (loopDuration <= 0.0) {
            isActive = true
            isWarning = false
            warningProgress = 0.0
            return
        }
        val effectiveTime = totalElapsedSeconds + phaseOffsetSeconds
        val normalizedTime = if (effectiveTime >= 0.0) {
            effectiveTime % loopDuration
        } else {
            val mod = effectiveTime % loopDuration
            if (mod < 0.0) mod + loopDuration else mod
        }

        val cycle = findCycle(normalizedTime)
        when {
            normalizedTime < cycle.activeEnd -> {
                isActive = true
                isWarning = false
                warningProgress = 0.0
            }
            normalizedTime < cycle.dormantEnd -> {
                isActive = false
                isWarning = false
                warningProgress = 0.0
            }
            else -> {
                isActive = false
                isWarning = true
                val warnTime = normalizedTime - cycle.dormantEnd
                warningProgress = (warnTime / 1.0).coerceIn(0.0, 1.0)
            }
        }
    }

    private fun findCycle(t: Double): Cycle {
        var low = 0
        var high = cycles.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val c = cycles[mid]
            if (t < c.start) {
                high = mid - 1
            } else if (t >= c.end) {
                low = mid + 1
            } else {
                return c
            }
        }
        return cycles[low.coerceIn(0, cycles.size - 1)]
    }

    fun intersectsPlayer(playerBounds: Rect): Boolean {
        if (!isActive) return false
        return bounds.intersects(playerBounds)
    }

    /** Seconds until steam erupts again if currently inactive, or 0.0 if currently active. */
    fun remainingInactiveTime(totalElapsedSeconds: Double): Double {
        if (loopDuration <= 0.0) return 0.0
        val effectiveTime = totalElapsedSeconds + phaseOffsetSeconds
        val normalizedTime = if (effectiveTime >= 0.0) {
            effectiveTime % loopDuration
        } else {
            val mod = effectiveTime % loopDuration
            if (mod < 0.0) mod + loopDuration else mod
        }

        val cycle = findCycle(normalizedTime)
        return if (normalizedTime < cycle.activeEnd) {
            0.0
        } else {
            cycle.end - normalizedTime
        }
    }

    fun reset() {
        isActive = false
        isWarning = false
        warningProgress = 0.0
    }

    constructor(def: SteamPipeDef) : this(
        id = def.id,
        x = def.x,
        topY = def.topY,
        bottomY = def.bottomY,
        mountType = def.mountType,
        jetWidth = def.jetWidth,
        activeDuration = def.activeDuration,
        inactiveDuration = def.inactiveDuration,
        phaseOffsetSeconds = def.phaseOffsetSeconds
    )
}
