package game.model

import kotlin.math.*

/**
 * Declarative definition of a laser hazard in the game world.
 *
 * Lasers originate from the top (ceiling, default [topY] = 150.0) and aim at the bottom
 * (conveyor surface, default [bottomY] = 414.0).
 * Tilt angle from vertical strictly satisfies: abs(tiltAngleDegrees) <= 45.0.
 *
 * [id]: Unique identifier for the laser.
 * [topX], [topY]: Origin point of the laser beam at the ceiling.
 * [bottomX], [bottomY]: Target point on the conveyor belt where the bottom cylinder is positioned.
 * [beamThickness]: Visual and collision thickness of the laser beam in pixels.
 * [activeDuration]: Duration (in seconds) that the laser beam remains energized and dangerous.
 * [inactiveDuration]: Duration (in seconds) that the laser beam de-energizes and is safe to traverse.
 * [phaseOffsetSeconds]: Offset in seconds into the active/inactive cycle.
 * [isAlwaysActive]: If true, laser is continuously energized and never deactivates.
 */
data class LaserDef(
    val id: String,
    val topX: Double,
    val topY: Double = 150.0,
    val bottomX: Double = topX,
    val bottomY: Double = 414.0,
    val beamThickness: Double = 6.0,
    val activeDuration: Double = 2.0,
    val inactiveDuration: Double = 1.5,
    val phaseOffsetSeconds: Double = 0.0,
    val isAlwaysActive: Boolean = false,
    /**
     * Scales the drawn emitter/receiver housings at either end of the beam (1.0 is the stock 32x9
     * cannon). Purely visual - the beam's own collision is [beamThickness] and does not move. For
     * a bank of beams standing close together, where full-size housings crowd each other and the
     * structure they hang off: LEVEL_6_LAYOUT's exit curtain runs at 0.55.
     */
    val emitterScale: Double = 1.0,
    /**
     * Ties this beam to a switch: a [Lever] whose targetMechanismId matches kills every laser
     * carrying it, permanently (see GameWorld's lever handling and [Laser.disable]). Null - the
     * default, and every laser before LEVEL_6_LAYOUT's exit curtain - means nothing can turn it
     * off and the only way past is its own on/off cycle.
     */
    val mechanismId: String? = null
) {
    /**
     * Tilt angle measured from vertical (0° is straight down along +y axis).
     * Forward tilt (towards exit/+x) is positive; backward tilt (-x) is negative.
     */
    val tiltAngleRadians: Double
        get() = atan2(bottomX - topX, bottomY - topY)

    val tiltAngleDegrees: Double
        get() = tiltAngleRadians * 180.0 / PI

    init {
        require(abs(tiltAngleDegrees) <= 45.0 + 1e-4) {
            "LaserDef '$id' tilt angle (${tiltAngleDegrees}°) exceeds maximum allowed 45° from vertical."
        }
    }

    val bounds: Rect
        get() {
            val minX = min(topX, bottomX) - beamThickness / 2.0
            val maxX = max(topX, bottomX) + beamThickness / 2.0
            val minY = min(topY, bottomY)
            val maxY = max(topY, bottomY)
            return Rect(minX, minY, maxX - minX, maxY - minY)
        }

    val x: Double get() = bounds.x
    val y: Double get() = bounds.y
    val width: Double get() = bounds.width
    val height: Double get() = bounds.height

    /**
     * Backwards-compatible secondary constructor for axis-aligned bounding rectangles.
     */
    constructor(
        id: String,
        x: Double,
        y: Double,
        width: Double,
        height: Double = 6.0,
        activeDuration: Double = 2.0,
        inactiveDuration: Double = 1.5,
        phaseOffsetSeconds: Double = 0.0,
        isAlwaysActive: Boolean = false
    ) : this(
        id = id,
        topX = x + width / 2.0,
        topY = y,
        bottomX = x + width / 2.0,
        bottomY = y + height,
        beamThickness = min(width, height).coerceAtLeast(4.0),
        activeDuration = activeDuration,
        inactiveDuration = inactiveDuration,
        phaseOffsetSeconds = phaseOffsetSeconds,
        isAlwaysActive = isAlwaysActive
    )
}

/**
 * Runtime simulator and collision state for a periodic laser hazard.
 */
class Laser(
    val id: String,
    val topX: Double,
    val topY: Double = 150.0,
    val bottomX: Double = topX,
    val bottomY: Double = 414.0,
    val beamThickness: Double = 6.0,
    val activeDuration: Double = 2.0,
    val inactiveDuration: Double = 1.5,
    val phaseOffsetSeconds: Double = 0.0,
    val isAlwaysActive: Boolean = false,
    /** See [LaserDef.emitterScale] - the drawn size of the housings, nothing to do with collision. */
    val emitterScale: Double = 1.0,
    /** See [LaserDef.mechanismId] - the switch, if any, that can kill this beam for good. */
    val mechanismId: String? = null
) {
    var isActive: Boolean = true
        private set

    /**
     * Cut by its switch and staying cut. Unlike the active/inactive cycle this is one-way for the
     * rest of the run (until [reset]), because it is a lever being thrown, not a timing window.
     */
    var isDisabled: Boolean = false
        private set

    /** Throws the switch. No-op on a laser that has no [mechanismId] - see GameWorld.triggerLever. */
    fun disable() {
        isDisabled = true
        isActive = false
    }

    /**
     * Tilt angle measured from vertical (0° is straight down along +y axis).
     * Forward tilt (towards exit/+x) is positive; backward tilt (-x) is negative.
     */
    val tiltAngleRadians: Double
        get() = atan2(bottomX - topX, bottomY - topY)

    val tiltAngleDegrees: Double
        get() = tiltAngleRadians * 180.0 / PI

    init {
        require(abs(tiltAngleDegrees) <= 45.0 + 1e-4) {
            "Laser '$id' tilt angle (${tiltAngleDegrees}°) exceeds maximum allowed 45° from vertical."
        }
    }

    val beamSegment: Segment2d
        get() = Segment2d(Vec2d(topX, topY), Vec2d(bottomX, bottomY))

    val bounds: Rect
        get() {
            val minX = min(topX, bottomX) - beamThickness / 2.0
            val maxX = max(topX, bottomX) + beamThickness / 2.0
            val minY = min(topY, bottomY)
            val maxY = max(topY, bottomY)
            return Rect(minX, minY, maxX - minX, maxY - minY)
        }

    val x: Double get() = bounds.x
    val y: Double get() = bounds.y
    val width: Double get() = bounds.width
    val height: Double get() = bounds.height

    val top: Double get() = bounds.top
    val bottom: Double get() = bounds.bottom
    val left: Double get() = bounds.left
    val right: Double get() = bounds.right

    /**
     * Backwards-compatible secondary constructor for axis-aligned bounding rectangles.
     */
    constructor(
        id: String,
        x: Double,
        y: Double,
        width: Double,
        height: Double = 6.0,
        activeDuration: Double = 2.0,
        inactiveDuration: Double = 1.5,
        phaseOffsetSeconds: Double = 0.0,
        isAlwaysActive: Boolean = false
    ) : this(
        id = id,
        topX = x + width / 2.0,
        topY = y,
        bottomX = x + width / 2.0,
        bottomY = y + height,
        beamThickness = min(width, height).coerceAtLeast(4.0),
        activeDuration = activeDuration,
        inactiveDuration = inactiveDuration,
        phaseOffsetSeconds = phaseOffsetSeconds,
        isAlwaysActive = isAlwaysActive
    )

    /** The bright core the scene draws (LaserFxAssets) - what a body has to touch to be hit. */
    val coreThickness: Double get() = (beamThickness * 0.55).coerceIn(2.0, 4.0)

    /**
     * Whether [body] touches the drawn core: the beam's centre line comes within half of
     * [coreThickness] of the rect. GameWorld hands in the body as drawn ([GameWorld.laserBody]),
     * not the 36-wide collision box.
     *
     * Before 2026-10-27 ("even though i am not touching the laser, it counts it as touching if i
     * am very close to it ... especially in lasers that are diagonal") it was the whole box
     * grown by half of [beamThickness] (6, nearly twice the 3.3 core) on every side - a square
     * grown at the corners too, so a slanted beam passing a corner of the box, where nothing of
     * the figure is drawn, killed from several units off.
     */
    fun intersectsPlayer(body: Rect): Boolean {
        if (!isActive) return false
        return distanceToSegment(body, Vec2d(topX, topY), Vec2d(bottomX, bottomY)) <= coreThickness / 2.0
    }

    /**
     * Updates laser energized state based on [totalElapsedSeconds].
     */
    fun update(totalElapsedSeconds: Double) {
        if (isDisabled) {
            isActive = false
            return
        }
        if (isAlwaysActive) {
            isActive = true
            return
        }
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) {
            isActive = true
            return
        }
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase
        isActive = normalizedPhase < activeDuration
    }

    fun remainingInactiveTime(totalElapsedSeconds: Double): Double {
        if (isAlwaysActive) return 0.0
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) return 0.0
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase
        return if (normalizedPhase >= activeDuration) cycle - normalizedPhase else 0.0
    }

    fun timeUntilInactive(totalElapsedSeconds: Double): Double {
        if (isAlwaysActive) return Double.POSITIVE_INFINITY
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) return Double.POSITIVE_INFINITY
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase
        return if (normalizedPhase < activeDuration) activeDuration - normalizedPhase else 0.0
    }

    fun reset() {
        isActive = true
        isDisabled = false
    }
}

/** Shortest distance from [r] to the segment [a]..[b] (0 when they touch or cross). */
internal fun distanceToSegment(r: Rect, a: Vec2d, b: Vec2d): Double {
    if (r.intersectsSegment(Segment2d(a, b))) return 0.0
    fun pointToSegment(p: Vec2d): Double {
        val d = b - a
        val len2 = d.lengthSquared()
        val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / len2).coerceIn(0.0, 1.0)
        return p.distanceTo(Vec2d(a.x + d.x * t, a.y + d.y * t))
    }
    fun pointToRect(p: Vec2d): Double {
        val dx = max(max(r.left - p.x, 0.0), p.x - r.right)
        val dy = max(max(r.top - p.y, 0.0), p.y - r.bottom)
        return sqrt(dx * dx + dy * dy)
    }
    return minOf(
        minOf(pointToRect(a), pointToRect(b)),
        minOf(minOf(pointToSegment(r.topLeft), pointToSegment(r.topRight)), minOf(pointToSegment(r.bottomLeft), pointToSegment(r.bottomRight)))
    )
}
