package game.model

/**
 * The prisoner level 10 found behind bars, as level 11 starts him: sitting in his cell at [x] on
 * the floor at [surfaceY], until the door [freedByDoorId] opens.
 *
 * "the [prisoner] will move forward whenever possible. he cant climb or parkour" (2026-09-30):
 * once free he walks towards [facing] at [speed] on his own and never stops to be told - he stops
 * only where he cannot go on: a closed door, a wall, or the lip of any drop deeper than
 * [Prisoner.MAX_STEP_DOWN]. He never jumps, climbs, crouches or swings. Doors are therefore the
 * only way to hold him, and the whole level is about where and when to hold him.
 */
data class PrisonerDef(
    val x: Double,
    val surfaceY: Double,
    val freedByDoorId: String,
    val facing: Double = 1.0,
    /**
     * 110, under the player's 132 so he can still be overtaken. It was 72 until 2026-10-03 ("in
     * both level 11 and 12 the prisoner is moving very slow").
     */
    val speed: Double = 110.0
)

class Prisoner(val def: PrisonerDef) {
    /** His body: the player's own physics (gravity, collision), driven by the rule above instead of input. */
    val body: Player = Player(x = def.x, y = def.surfaceY - 96.0).also { it.moveSpeed = def.speed }

    /** Out of the cell. Nothing about him moves before this. */
    var isFree: Boolean = false
        private set

    /** Seconds since he was freed - the scene plays him getting up over [STAND_UP_SECONDS]. */
    var freedFor: Double = 0.0
        private set

    /** He has reached the exit, and waits there. */
    var hasEscaped: Boolean = false
        private set

    /** True on a tick he actually moved along the floor - what the scene keys his walk on. */
    var isWalking: Boolean = false
        private set

    /** True while he wants to go on but cannot: a door, a wall or a drop in front of him. */
    var isHeld: Boolean = false
        private set

    /** Distance walked, for the scene's distance-driven gait. */
    var walkedDistance: Double = 0.0
        private set

    val bounds: Rect get() = body.bounds

    fun free() {
        if (isFree) return
        isFree = true
        freedFor = 0.0
    }

    /**
     * One tick. [platforms] is everything solid to him this tick (floors, walls, closed door
     * panels, lifts); [exitZone] is where he is going.
     */
    fun update(dt: Double, platforms: List<Rect>, exitZone: Rect) {
        isWalking = false
        isHeld = false
        if (isFree) freedFor += dt
        if (!hasEscaped && bounds.intersects(exitZone)) hasEscaped = true
        val arrived = hasEscaped && body.centerX * def.facing >= exitZone.centerX * def.facing
        val ready = isFree && freedFor >= STAND_UP_SECONDS && !arrived
        val atLip = ready && body.isGrounded && isAtLip(platforms)
        val move = if (ready && !atLip) def.facing else 0.0
        val before = body.x
        body.update(dt, move, jumpInput = false, crouchInput = false, platforms = platforms)
        val moved = kotlin.math.abs(body.x - before)
        if (moved > 0.05 && body.isGrounded) {
            isWalking = true
            walkedDistance += moved
        }
        isHeld = ready && moved <= 0.05
    }

    /**
     * Nothing to step down onto just ahead of the foot that is about to leave the edge. A body
     * walks off an edge once the centre of its feet is past it (Player's walk-off), so the probe
     * is a little ahead of that centre.
     */
    private fun isAtLip(platforms: List<Rect>): Boolean {
        val feet = body.y + body.height
        val px = body.centerX + def.facing * LIP_PROBE_AHEAD
        return platforms.none { p ->
            px >= p.left && px <= p.right && p.top >= feet - 1.0 && p.top <= feet + MAX_STEP_DOWN
        }
    }

    fun reset() {
        body.resetTo(def.x, def.surfaceY - 96.0)
        isFree = false
        freedFor = 0.0
        hasEscaped = false
        isWalking = false
        isHeld = false
        walkedDistance = 0.0
    }

    /** For a checkpoint: he is put back where he was standing when it was taken. */
    fun restore(x: Double, y: Double) {
        body.resetTo(x, y)
        isFree = true
        freedFor = STAND_UP_SECONDS
        hasEscaped = false
        isWalking = false
        isHeld = false
    }

    companion object {
        /** Getting up off the cell floor once the door is open, before the first step. */
        const val STAND_UP_SECONDS = 0.8
        /** The deepest drop he will step down. Anything deeper and he stops at the lip. */
        const val MAX_STEP_DOWN = 30.0
        /** How far ahead of his feet' centre the lip is looked for. */
        const val LIP_PROBE_AHEAD = 10.0
    }
}
