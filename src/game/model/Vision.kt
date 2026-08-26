package game.model

import kotlin.math.*

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

        // Ray samples aimed at occluder corners for crisp shadow silhouettes
        val epsilon = 0.0001
        for (occluder in occluders) {
            val corners = listOf(
                occluder.topLeft,
                occluder.topRight,
                occluder.bottomLeft,
                occluder.bottomRight
            )
            for (corner in corners) {
                val dist = origin.distanceTo(corner)
                if (dist <= range && dist > 1.0) {
                    val angle = atan2(corner.y - origin.y, corner.x - origin.x)
                    // Normalize relative to startAngle
                    val diff = GeometryUtils.angleDifference(angle, facingAngle)
                    if (abs(diff) <= halfFov) {
                        val normalizedAngle = facingAngle + diff
                        angles.add(normalizedAngle - epsilon)
                        angles.add(normalizedAngle)
                        angles.add(normalizedAngle + epsilon)
                    }
                }
            }
        }

        // Sort angles in increasing order
        val sortedAngles = angles.sorted()

        val hitPoints = mutableListOf<Vec2d>()
        for (angle in sortedAngles) {
            hitPoints.add(GeometryUtils.castRay(origin, angle, range, occluders))
        }

        return listOf(origin) + hitPoints
    }

    fun getPlayerSpottedDistance(
        guard: Guard,
        player: Player,
        occluders: List<Rect>
    ): Double? {
        val eye = guard.eyePosition
        val halfFov = guard.visionFov / 2.0
        val maxDistSq = guard.visionRange * guard.visionRange
        var closestDist: Double? = null

        for (targetPoint in player.keyPoints) {
            val distSq = eye.distanceSquaredTo(targetPoint)
            if (distSq > maxDistSq) continue

            val angleToTarget = atan2(targetPoint.y - eye.y, targetPoint.x - eye.x)
            val angleDiff = abs(GeometryUtils.angleDifference(angleToTarget, guard.facingAngle))
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
}
