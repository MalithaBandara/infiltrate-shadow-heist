package game.model

import kotlin.math.abs

/**
 * An interactive in-world lever that activates mechanisms when the player uses the interact button nearby.
 */
data class Lever(
    val id: String,
    val x: Double,
    val y: Double,
    val width: Double = 22.0,
    val height: Double = 12.0,
    var isActivated: Boolean = false,
    val targetMechanismId: String? = null,
    val interactRadius: Double = 40.0
) {
    val bounds: Rect get() = Rect(x, y, width, height)
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0

    fun isPlayerInRange(player: Player): Boolean {
        val dx = abs(player.centerX - centerX)
        val feetY = player.y + player.height
        val leverBottomY = y + height
        val dy = abs(feetY - leverBottomY)
        return dx <= interactRadius && dy <= 32.0
    }

    fun reset() {
        isActivated = false
    }
}
