package game.model

import kotlin.math.*

/**
 * One drawn cone's vision polygon, rebuilt only when something it is drawn from changed: the lens,
 * the facing, the range, the field of view, or a sight blocker within its reach. A crate moving
 * through a cone reshapes it; a crate moving across the level does not cost a rebuild. The scene
 * holds one per guard, camera, rover and echo and feeds it GameWorld.sightBlockers - the list the
 * detection rays test - so the drawn cone is the detection's cone.
 */
class VisionCone {
    /** The last polygon built ([VisionSystem.computeVisionPolygon]'s shape); empty until the first [update]. */
    var polygon: List<Vec2d> = emptyList()
        private set

    private var originX = Double.NaN
    private var originY = Double.NaN
    private var facing = Double.NaN
    private var range = Double.NaN
    private var fov = Double.NaN
    private var inReach: List<Rect> = emptyList()

    /** True once built and not [reset] since. */
    val isBuilt: Boolean get() = !originX.isNaN()

    /** Rebuilds [polygon] if any input changed; true when it did. */
    fun update(origin: Vec2d, facingAngle: Double, range: Double, fov: Double, occluders: List<Rect>): Boolean {
        if (origin.x == originX && origin.y == originY && facingAngle == facing && range == this.range &&
            fov == this.fov && sameInReach(origin, range, occluders)
        ) return false
        originX = origin.x
        originY = origin.y
        facing = facingAngle
        this.range = range
        this.fov = fov
        // The same occluders in the same order computeVisionPolygon would pick itself, so the
        // polygon is the one it builds from the whole list, to the last bit.
        inReach = ArrayList(GeometryUtils.occludersInReach(origin, range, occluders))
        polygon = VisionSystem.computeVisionPolygon(origin, facingAngle, range, fov, inReach)
        return true
    }

    /** Forget the last build: the next [update] rebuilds whatever it is given. */
    fun reset() {
        originX = Double.NaN
        polygon = emptyList()
    }

    private fun sameInReach(origin: Vec2d, range: Double, occluders: List<Rect>): Boolean {
        var n = 0
        for (i in occluders.indices) {
            val o = occluders[i]
            if (!GeometryUtils.isInReach(origin, range, o)) continue
            if (n >= inReach.size || inReach[n] != o) return false
            n++
        }
        return n == inReach.size
    }
}

object VisionSystem {

    fun computeVisionPolygon(
        origin: Vec2d,
        facingAngle: Double,
        range: Double,
        fov: Double,
        occluders: List<Rect>,
        sampleCount: Int = 36
    ): List<Vec2d> {
        val halfFov = fov / 2.0
        val startAngle = facingAngle - halfFov
        val endAngle = facingAngle + halfFov

        val angles = mutableListOf<Double>()

        // Uniform ray samples
        for (i in 0..sampleCount) {
            val t = i.toDouble() / sampleCount
            angles.add(startAngle + t * fov)
        }

        // Only what a ray of this length can reach - the same hits and the same corners as the
        // whole list (anything dropped is out of range), without every ray testing every box in
        // the level. See GeometryUtils.occludersInReach.
        val nearby = GeometryUtils.occludersInReach(origin, range, occluders)

        // Ray samples aimed at occluder corners for crisp shadow silhouettes
        val epsilon = 0.0001
        fun aimAt(angle: Double) {
            // Normalize relative to startAngle
            val diff = GeometryUtils.angleDifference(angle, facingAngle)
            if (abs(diff) <= halfFov) {
                val normalizedAngle = facingAngle + diff
                angles.add(normalizedAngle - epsilon)
                angles.add(normalizedAngle)
                angles.add(normalizedAngle + epsilon)
            }
        }
        for (occluder in nearby) {
            for (c in 0 until 4) {
                val corner = when (c) {
                    0 -> occluder.topLeft
                    1 -> occluder.topRight
                    2 -> occluder.bottomLeft
                    else -> occluder.bottomRight
                }
                val dist = origin.distanceTo(corner)
                if (dist <= range && dist > 1.0) {
                    aimAt(atan2(corner.y - origin.y, corner.x - origin.x))
                }
            }
        }
        // ...and at every point where the end of the range crosses an occluder's side, where the
        // outline turns from the arc onto the face. Without these the outline cut straight across
        // from the last ray reaching full range to the first one stopped by the face, leaving
        // undrawn a sliver the eye sees (a body standing on a crate at the end of an overwatch
        // guard's reach - level 3 - was spotted there).
        val rangeSq = range * range
        for (o in nearby) {
            for (y in doubleArrayOf(o.top, o.bottom)) {
                val dy = y - origin.y
                val h2 = rangeSq - dy * dy
                if (h2 < 0.0) continue
                val h = sqrt(h2)
                for (x in doubleArrayOf(origin.x - h, origin.x + h)) {
                    if (x >= o.left && x <= o.right) aimAt(atan2(dy, x - origin.x))
                }
            }
            for (x in doubleArrayOf(o.left, o.right)) {
                val dx = x - origin.x
                val h2 = rangeSq - dx * dx
                if (h2 < 0.0) continue
                val h = sqrt(h2)
                for (y in doubleArrayOf(origin.y - h, origin.y + h)) {
                    if (y >= o.top && y <= o.bottom) aimAt(atan2(y - origin.y, dx))
                }
            }
        }

        // Sort angles in increasing order
        val sortedAngles = angles.sorted()

        val hitPoints = mutableListOf<Vec2d>()
        for (angle in sortedAngles) {
            hitPoints.add(GeometryUtils.castRay(origin, angle, range, nearby))
        }

        return listOf(origin) + hitPoints
    }

    fun getPlayerSpottedDistance(
        eye: Vec2d,
        facingAngle: Double,
        visionRange: Double,
        visionFov: Double,
        player: Player,
        occluders: List<Rect>
    ): Double? {
        val halfFov = visionFov / 2.0
        val maxDistSq = visionRange * visionRange
        var closestDist: Double? = null

        for (targetPoint in player.keyPoints) {
            val distSq = eye.distanceSquaredTo(targetPoint)
            if (distSq > maxDistSq) continue

            val angleToTarget = atan2(targetPoint.y - eye.y, targetPoint.x - eye.x)
            val angleDiff = abs(GeometryUtils.angleDifference(angleToTarget, facingAngle))
            if (angleDiff > halfFov) continue

            if (GeometryUtils.hasLineOfSight(eye, targetPoint, occluders)) {
                val dist = sqrt(distSq)
                if (closestDist == null || dist < closestDist) {
                    closestDist = dist
                }
            }
        }
        return closestDist
    }

    fun getPlayerSpottedDistance(
        guard: Guard,
        player: Player,
        occluders: List<Rect>
    ): Double? = getPlayerSpottedDistance(
        eye = guard.eyePosition,
        facingAngle = guard.facingAngle,
        visionRange = guard.visionRange,
        visionFov = guard.visionFov,
        player = player,
        occluders = occluders
    )

    fun getPlayerSpottedDistance(
        camera: Camera,
        player: Player,
        occluders: List<Rect>
    ): Double? = getPlayerSpottedDistance(
        eye = camera.eyePosition,
        facingAngle = camera.facingAngle,
        visionRange = camera.visionRange,
        visionFov = camera.visionFov,
        player = player,
        occluders = occluders
    )

    fun getPlayerSpottedDistance(
        bot: CameraBot,
        player: Player,
        occluders: List<Rect>
    ): Double? {
        val eye = bot.eyePosition
        val halfFov = bot.visionFov / 2.0
        val maxDistSq = bot.visionRange * bot.visionRange
        var closestDist: Double? = null

        val points = getBotDetectionPoints(bot, player)
        for (targetPoint in points) {
            val distSq = eye.distanceSquaredTo(targetPoint)
            if (distSq > maxDistSq) continue

            val angleToTarget = atan2(targetPoint.y - eye.y, targetPoint.x - eye.x)
            val angleDiff = abs(GeometryUtils.angleDifference(angleToTarget, bot.facingAngle))
            if (angleDiff > halfFov) continue

            if (GeometryUtils.hasLineOfSight(eye, targetPoint, occluders)) {
                val dist = sqrt(distSq)
                if (closestDist == null || dist < closestDist) {
                    closestDist = dist
                }
            }
        }
        return closestDist
    }

    fun isPlayerSpotted(
        guard: Guard,
        player: Player,
        occluders: List<Rect>
    ): Boolean = getPlayerSpottedDistance(guard, player, occluders) != null

    fun isPlayerSpotted(
        camera: Camera,
        player: Player,
        occluders: List<Rect>
    ): Boolean = getPlayerSpottedDistance(camera, player, occluders) != null

    fun isPlayerSpotted(
        bot: CameraBot,
        player: Player,
        occluders: List<Rect>
    ): Boolean = getPlayerSpottedDistance(bot, player, occluders) != null

    private fun getBotDetectionPoints(bot: CameraBot, player: Player): List<Vec2d> {
        val top = player.currentTopY
        val bottom = player.y + player.height
        val h = player.currentHeight
        val cx = player.centerX

        val xs = if (player.braceLean != 0.0) {
            val leanHeadX = cx + player.braceLean * Player.PUSH_HEAD_POINT_X * player.visualHeight
            val leanTorsoX = cx + player.braceLean * Player.PUSH_TORSO_POINT_X * player.visualHeight
            listOf(
                player.x + 1.0,
                player.x + player.width * 0.25,
                cx,
                player.x + player.width * 0.75,
                player.x + player.width - 1.0,
                leanHeadX,
                leanTorsoX
            )
        } else {
            listOf(
                player.x + 1.0,
                player.x + player.width * 0.25,
                cx,
                player.x + player.width * 0.75,
                player.x + player.width - 1.0
            )
        }

        val clampedEyeY = bot.eyePosition.y.coerceIn(top + 4.0, bottom - 4.0)
        val ys = listOf(
            top + 10.0,
            top + h * 0.25,
            top + h * 0.50,
            top + h * 0.75,
            clampedEyeY,
            bottom - 8.0
        )

        val points = ArrayList<Vec2d>(xs.size * ys.size)
        for (x in xs) {
            for (y in ys) {
                points.add(Vec2d(x, y))
            }
        }
        return points
    }
}
