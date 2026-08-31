package game.model

import kotlin.math.PI

data class Camera(
    var x: Double,
    var y: Double,
    val width: Double = 20.0,
    val height: Double = 20.0,
    var minAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    var maxAngle: Double = (90.0 + 30.0) * (PI / 180.0),
    var currentAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    var sweepSpeed: Double = 0.7, // radians per second
    var visionRange: Double = 240.0,
    var visionFov: Double = 45.0 * (PI / 180.0), // 45 degrees in radians
    var sweepDirection: Double = 1.0
) {
    val bounds: Rect get() = Rect(x, y, width, height)
    val center: Vec2d get() = Vec2d(x + width / 2.0, y + height / 2.0)
    val eyePosition: Vec2d get() = center
    val facingAngle: Double get() = currentAngle

    fun update(dt: Double) {
        if (minAngle >= maxAngle || sweepSpeed <= 0.0) {
            currentAngle = minAngle
            return
        }

        currentAngle += sweepDirection * sweepSpeed * dt

        if (sweepDirection > 0.0 && currentAngle >= maxAngle) {
            currentAngle = maxAngle
            sweepDirection = -1.0
        } else if (sweepDirection < 0.0 && currentAngle <= minAngle) {
            currentAngle = minAngle
            sweepDirection = 1.0
        }
    }

    companion object {
        fun createSweeping(
            x: Double,
            y: Double,
            centerAngle: Double = PI / 2.0,
            sweepAngleDelta: Double = 30.0 * (PI / 180.0),
            sweepSpeed: Double = 0.7,
            visionRange: Double = 240.0,
            visionFov: Double = 45.0 * (PI / 180.0),
            width: Double = 20.0,
            height: Double = 20.0
        ): Camera {
            val min = centerAngle - sweepAngleDelta
            val max = centerAngle + sweepAngleDelta
            return Camera(
                x = x,
                y = y,
                width = width,
                height = height,
                minAngle = min,
                maxAngle = max,
                currentAngle = min,
                sweepSpeed = sweepSpeed,
                visionRange = visionRange,
                visionFov = visionFov,
                sweepDirection = 1.0
            )
        }
    }
}
