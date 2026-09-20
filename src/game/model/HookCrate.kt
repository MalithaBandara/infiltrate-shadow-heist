package game.model

/**
 * A crate attached to a hanging hook that blocks the player from swinging from that hook.
 * When detached (e.g. via a lever), it drops with gravity, freeing the hook for swinging.
 */
data class HookCrate(
    val id: String,
    val hook: Rect,
    var bounds: Rect,
    val ropeLength: Double = 0.0,
    var isDetached: Boolean = false,
    var vy: Double = 0.0,
    var isLanded: Boolean = false,
    val initialBounds: Rect = bounds
) {
    fun reset() {
        isDetached = false
        vy = 0.0
        isLanded = false
        bounds = initialBounds
    }

    fun update(dt: Double, gravity: Double, groundY: Double) {
        if (!isDetached || isLanded) return
        vy += gravity * dt
        val nextY = bounds.y + vy * dt
        if (nextY + bounds.height >= groundY) {
            bounds = Rect(bounds.x, groundY - bounds.height, bounds.width, bounds.height)
            vy = 0.0
            isLanded = true
        } else {
            bounds = Rect(bounds.x, nextY, bounds.width, bounds.height)
        }
    }
}
