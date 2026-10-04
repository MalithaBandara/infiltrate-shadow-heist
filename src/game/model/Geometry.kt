package game.model

import kotlin.math.*

data class Vec2d(val x: Double, val y: Double) {
    operator fun plus(other: Vec2d): Vec2d = Vec2d(x + other.x, y + other.y)
    operator fun minus(other: Vec2d): Vec2d = Vec2d(x - other.x, y - other.y)
    operator fun times(scalar: Double): Vec2d = Vec2d(x * scalar, y * scalar)
    operator fun div(scalar: Double): Vec2d = Vec2d(x / scalar, y / scalar)
    operator fun unaryMinus(): Vec2d = Vec2d(-x, -y)

    fun length(): Double = sqrt(x * x + y * y)
    fun lengthSquared(): Double = x * x + y * y
    fun distanceTo(other: Vec2d): Double = (this - other).length()
    fun distanceSquaredTo(other: Vec2d): Double = (this - other).lengthSquared()

    fun normalized(): Vec2d {
        val len = length()
        return if (len > 1e-9) Vec2d(x / len, y / len) else Vec2d(0.0, 0.0)
    }

    companion object {
        val ZERO = Vec2d(0.0, 0.0)
    }
}

data class Segment2d(val p1: Vec2d, val p2: Vec2d) {
    fun intersects(other: Segment2d): Vec2d? {
        val d1 = p2 - p1
        val d2 = other.p2 - other.p1
        val cross = d1.x * d2.y - d1.y * d2.x
        if (abs(cross) < 1e-9) return null // Parallel or collinear

        val d3 = other.p1 - p1
        val t = (d3.x * d2.y - d3.y * d2.x) / cross
        val u = (d3.x * d1.y - d3.y * d1.x) / cross

        if (t in 0.0..1.0 && u in 0.0..1.0) {
            return Vec2d(p1.x + t * d1.x, p1.y + t * d1.y)
        }
        return null
    }
}

data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val left: Double get() = x
    val top: Double get() = y
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0

    val topLeft: Vec2d get() = Vec2d(left, top)
    val topRight: Vec2d get() = Vec2d(right, top)
    val bottomLeft: Vec2d get() = Vec2d(left, bottom)
    val bottomRight: Vec2d get() = Vec2d(right, bottom)

    fun intersects(other: Rect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    fun contains(point: Vec2d): Boolean {
        return point.x in left..right && point.y in top..bottom
    }

    fun edges(): List<Segment2d> = listOf(
        Segment2d(topLeft, topRight),       // Top edge
        Segment2d(topRight, bottomRight),   // Right edge
        Segment2d(bottomRight, bottomLeft), // Bottom edge
        Segment2d(bottomLeft, topLeft)      // Left edge
    )

    fun intersectsSegment(seg: Segment2d): Boolean {
        if (contains(seg.p1) || contains(seg.p2)) return true
        for (edge in edges()) {
            if (seg.intersects(edge) != null) return true
        }
        return false
    }
}

data class Ray2d(val origin: Vec2d, val direction: Vec2d, val maxDistance: Double)

data class RaycastHit(
    val point: Vec2d,
    val distance: Double,
    val normal: Vec2d = Vec2d.ZERO
)

object GeometryUtils {
    fun normalizeAngle(angle: Double): Double {
        var a = angle % (2.0 * PI)
        while (a > PI) a -= 2.0 * PI
        while (a < -PI) a += 2.0 * PI
        return a
    }

    fun angleDifference(angle1: Double, angle2: Double): Double {
        return normalizeAngle(angle1 - angle2)
    }

    /**
     * Slack around the broad-phase boxes below. A segment can only touch an edge inside both of
     * their bounding boxes, so skipping an occluder whose box is clear of the segment's cannot
     * change a result; the extra unit keeps floating-point rounding at a shared boundary on the
     * side of testing the occluder.
     */
    private const val BROAD_PHASE_SLACK = 1.0

    /**
     * The occluders [castRay] could possibly hit from [origin] within [range], in their original
     * order (ties between equally close hits go to the earlier occluder, as before). Every ray of a
     * vision polygon shares this, so the level's far-off boxes are dropped once per cone instead of
     * being tested by every ray.
     */
    fun occludersInReach(origin: Vec2d, range: Double, occluders: List<Rect>): List<Rect> {
        val reach = range + BROAD_PHASE_SLACK
        var out: ArrayList<Rect>? = null
        for (i in occluders.indices) {
            val o = occluders[i]
            val inReach = o.x + o.width >= origin.x - reach && o.x <= origin.x + reach &&
                o.y + o.height >= origin.y - reach && o.y <= origin.y + reach
            if (inReach) {
                out?.add(o)
            } else if (out == null) {
                out = ArrayList(occluders.size)
                for (j in 0 until i) out.add(occluders[j])
            }
        }
        return out ?: occluders
    }

    // castRay and hasLineOfSight below are Segment2d.intersects run against Rect.edges() (top, right,
    // bottom, left - in that order) with the same arithmetic in the same order, written out so a
    // ray allocates nothing per edge. The results are the same to the last bit; vision cones
    // were tuned against exact angles (LEVEL_3_LAYOUT's beam camera), so keep it that way.

    fun castRay(origin: Vec2d, angle: Double, range: Double, occluders: List<Rect>): Vec2d {
        val ox = origin.x
        val oy = origin.y
        val dirX = cos(angle)
        val dirY = sin(angle)
        val targetX = ox + dirX * range
        val targetY = oy + dirY * range
        val d1x = targetX - ox
        val d1y = targetY - oy

        var closestX = targetX
        var closestY = targetY
        var closestDistanceSq = range * range

        for (i in occluders.indices) {
            val o = occluders[i]
            val left = o.x
            val top = o.y
            val right = o.x + o.width
            val bottom = o.y + o.height
            for (e in 0 until 4) {
                val qx1: Double; val qy1: Double; val qx2: Double; val qy2: Double
                when (e) {
                    0 -> { qx1 = left; qy1 = top; qx2 = right; qy2 = top }
                    1 -> { qx1 = right; qy1 = top; qx2 = right; qy2 = bottom }
                    2 -> { qx1 = right; qy1 = bottom; qx2 = left; qy2 = bottom }
                    else -> { qx1 = left; qy1 = bottom; qx2 = left; qy2 = top }
                }
                val d2x = qx2 - qx1
                val d2y = qy2 - qy1
                val cross = d1x * d2y - d1y * d2x
                if (abs(cross) < 1e-9) continue
                val d3x = qx1 - ox
                val d3y = qy1 - oy
                val t = (d3x * d2y - d3y * d2x) / cross
                val u = (d3x * d1y - d3y * d1x) / cross
                if (t >= 0.0 && t <= 1.0 && u >= 0.0 && u <= 1.0) {
                    val hx = ox + t * d1x
                    val hy = oy + t * d1y
                    val dx = ox - hx
                    val dy = oy - hy
                    val distSq = dx * dx + dy * dy
                    if (distSq < closestDistanceSq) {
                        closestDistanceSq = distSq
                        closestX = hx
                        closestY = hy
                    }
                }
            }
        }

        return Vec2d(closestX, closestY)
    }

    fun hasLineOfSight(from: Vec2d, to: Vec2d, occluders: List<Rect>): Boolean {
        val fx = from.x
        val fy = from.y
        val d1x = to.x - fx
        val d1y = to.y - fy
        val tdx = fx - to.x
        val tdy = fy - to.y
        val distTotal = sqrt(tdx * tdx + tdy * tdy)
        val segLeft = min(fx, to.x) - BROAD_PHASE_SLACK
        val segRight = max(fx, to.x) + BROAD_PHASE_SLACK
        val segTop = min(fy, to.y) - BROAD_PHASE_SLACK
        val segBottom = max(fy, to.y) + BROAD_PHASE_SLACK
        for (i in occluders.indices) {
            val o = occluders[i]
            val left = o.x
            val top = o.y
            val right = o.x + o.width
            val bottom = o.y + o.height
            if (right < segLeft || left > segRight || bottom < segTop || top > segBottom) continue
            for (e in 0 until 4) {
                val qx1: Double; val qy1: Double; val qx2: Double; val qy2: Double
                when (e) {
                    0 -> { qx1 = left; qy1 = top; qx2 = right; qy2 = top }
                    1 -> { qx1 = right; qy1 = top; qx2 = right; qy2 = bottom }
                    2 -> { qx1 = right; qy1 = bottom; qx2 = left; qy2 = bottom }
                    else -> { qx1 = left; qy1 = bottom; qx2 = left; qy2 = top }
                }
                val d2x = qx2 - qx1
                val d2y = qy2 - qy1
                val cross = d1x * d2y - d1y * d2x
                if (abs(cross) < 1e-9) continue
                val d3x = qx1 - fx
                val d3y = qy1 - fy
                val t = (d3x * d2y - d3y * d2x) / cross
                val u = (d3x * d1y - d3y * d1x) / cross
                if (t >= 0.0 && t <= 1.0 && u >= 0.0 && u <= 1.0) {
                    // Check if hit point is strictly between from and to (not just origin)
                    val dx = fx - (fx + t * d1x)
                    val dy = fy - (fy + t * d1y)
                    val distFrom = sqrt(dx * dx + dy * dy)
                    if (distFrom > 1e-4 && distFrom < distTotal - 1e-4) {
                        return false
                    }
                }
            }
        }
        return true
    }
}
