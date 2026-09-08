package game.model

import kotlin.math.PI
import kotlin.math.cos

/**
 * Declarative definition of a horizontally moving platform (such as a crane-suspended shipping container).
 */
data class MovingPlatformDef(
    val id: String,
    val initialX: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val minX: Double = initialX,
    val maxX: Double = initialX,
    val minY: Double = y,
    val maxY: Double = y,
    val periodSeconds: Double,
    val phaseOffsetSeconds: Double = 0.0,
    val isVariant1: Boolean = false,
    val initialY: Double = minY
)

data class PlatformDisplacement(
    val dx: Double,
    val dy: Double
)

/**
 * Runtime state and motion simulator for a moving platform.
 *
 * Employs smooth sinusoidal acceleration/deceleration matching a heavy gantry-suspended load,
 * slowing down gently near the turning endpoints and moving swiftest through the center.
 */
class MovingPlatform(
    val id: String,
    val width: Double,
    val height: Double,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val periodSeconds: Double,
    val phaseOffsetSeconds: Double = 0.0,
    val isVariant1: Boolean = false,
    initialX: Double = minX,
    initialY: Double = minY
) {
    constructor(
        id: String,
        y: Double,
        width: Double,
        height: Double,
        minX: Double,
        maxX: Double,
        periodSeconds: Double,
        phaseOffsetSeconds: Double = 0.0,
        isVariant1: Boolean = false,
        initialX: Double = minX,
        minY: Double = y,
        maxY: Double = y,
        initialY: Double = minY
    ) : this(
        id = id,
        width = width,
        height = height,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        periodSeconds = periodSeconds,
        phaseOffsetSeconds = phaseOffsetSeconds,
        isVariant1 = isVariant1,
        initialX = initialX,
        initialY = initialY
    )

    var x: Double = initialX
        private set

    var y: Double = initialY
        private set

    var vx: Double = 0.0
        private set

    var vy: Double = 0.0
        private set

    val bounds: Rect get() = Rect(x, y, width, height)

    val top: Double get() = y
    val bottom: Double get() = y + height
    val left: Double get() = x
    val right: Double get() = x + width

    /**
     * Advances platform position based on the global elapsed time.
     * Returns the 2D displacement (dx, dy) applied during this tick.
     */
    fun update(dt: Double, totalElapsedSeconds: Double): PlatformDisplacement {
        val oldX = x
        val oldY = y
        val phase = if (periodSeconds > 0.0) {
            val normalized = ((totalElapsedSeconds + phaseOffsetSeconds) % periodSeconds) / periodSeconds
            if (normalized < 0.0) normalized + 1.0 else normalized
        } else {
            0.0
        }
        val t = 0.5 - 0.5 * cos(2.0 * PI * phase)

        if (minX != maxX) {
            x = minX + (maxX - minX) * t
        }
        if (minY != maxY) {
            y = minY + (maxY - minY) * t
        }

        vx = if (dt > 1e-6) (x - oldX) / dt else 0.0
        vy = if (dt > 1e-6) (y - oldY) / dt else 0.0
        return PlatformDisplacement(x - oldX, y - oldY)
    }
}
