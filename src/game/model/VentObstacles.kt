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
        val minY = y - 10.0
        val maxY = y + height + 10.0
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
        val minY = y - 10.0
        val maxY = y + height + 10.0
        val p = player.bounds
        return p.right >= minX && p.left <= maxX && p.bottom >= minY && p.top <= maxY
    }

    fun update(dt: Double) {
        // Fast spinning visual rotation (e.g. 18 rad/s)
        bladeRotationAngle = (bladeRotationAngle + 18.0 * dt) % (2.0 * PI)
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
            val eyeY = y + height * 0.45
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
 * Cycles periodically between active (blasting lethal pressurized steam) and inactive.
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
    var isActive: Boolean = false
        private set

    /** 0..1 warning indicator progress right before steam erupts (last 0.4s of inactive phase). */
    var warningProgress: Double = 0.0
        private set

    val isWarning: Boolean get() = warningProgress > 0.0

    val bounds: Rect
        get() = Rect(x - jetWidth / 2.0, topY, jetWidth, bottomY - topY)

    fun update(totalElapsedSeconds: Double) {
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) {
            isActive = true
            warningProgress = 0.0
            return
        }
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase

        isActive = normalizedPhase < activeDuration

        val warningWindow = 0.45
        if (!isActive && normalizedPhase >= (cycle - warningWindow)) {
            warningProgress = ((normalizedPhase - (cycle - warningWindow)) / warningWindow).coerceIn(0.0, 1.0)
        } else {
            warningProgress = 0.0
        }
    }

    fun intersectsPlayer(playerBounds: Rect): Boolean {
        if (!isActive) return false
        return bounds.intersects(playerBounds)
    }

    /** Seconds until steam erupts again if currently inactive, or 0.0 if currently active. */
    fun remainingInactiveTime(totalElapsedSeconds: Double): Double {
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) return 0.0
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase
        return if (normalizedPhase >= activeDuration) {
            cycle - normalizedPhase
        } else {
            0.0
        }
    }

    fun reset() {
        isActive = false
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
