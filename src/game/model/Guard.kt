package game.model

import kotlin.math.PI

data class Guard(
    var x: Double,
    var y: Double,
    val width: Double = 26.0,
    val height: Double = 48.0,
    val patrolMinX: Double,
    val patrolMaxX: Double,
    var speed: Double = 70.0,
    var facing: Double = 1.0,
    var visionRange: Double = 260.0,
    var visionFov: Double = 60.0 * (PI / 180.0) // 60 degrees in radians
) {
    val bounds: Rect get() = Rect(x, y, width, height)

    val facingAngle: Double
        get() = if (facing >= 0.0) 0.0 else PI

    val eyePosition: Vec2d
        get() = Vec2d(
            if (facing >= 0.0) x + width - 4.0 else x + 4.0,
            y + 12.0
        )

    fun update(dt: Double) {
        x += facing * speed * dt

        if (facing > 0.0 && x >= patrolMaxX) {
            x = patrolMaxX
            facing = -1.0
        } else if (facing < 0.0 && x <= patrolMinX) {
            x = patrolMinX
            facing = 1.0
        }
    }
}
