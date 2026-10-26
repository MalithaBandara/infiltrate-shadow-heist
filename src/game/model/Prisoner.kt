package game.model

/**
 * The prisoner level 10 found behind bars, as level 11 starts him: sitting in his cell at [x] on
 * the floor at [surfaceY], until the door [freedByDoorId] opens - or, with no door of that id, until
 * the lift of that id he is sitting on first moves, or with [WHEN_THE_PLAYER_SETS_OFF] until the
 * player first moves (level 12, where he starts beside the player - 2026-10-26).
 *
 * "the [prisoner] will move forward whenever possible. he cant climb or parkour" (2026-09-30):
 * once free he walks towards [facing] at [speed] on his own and never stops to be told - he stops
 * only where he cannot go on: a closed door, a wall, or the lip of any drop deeper than
 * [Prisoner.MAX_STEP_DOWN]. He never jumps, climbs, crouches or swings. Doors are therefore the
 * only way to hold him, and the whole level is about where and when to hold him. Since
 * 2026-10-26 he also waits at a laser that is on or about to come on, and goes when it has a gap
 * he can cross ("it is impossible to correct time the lever for the lasers at the end"), and for
 * a lift he would step onto or off to stop moving.
 *
 * He also waits at the foot of a lift's shaft while the lift is up off his floor, as anyone waits
 * for a lift, rather than walking in under it: a lift never comes down on a body, so one sent up
 * before he got there could otherwise never come back down to fetch him (level 12, 2026-10-19:
 * "make sure the level has a solution").
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
) {
    companion object {
        /** [freedByDoorId] for a prisoner who gets up as the player sets off (GameWorld.hasClockStarted). */
        const val WHEN_THE_PLAYER_SETS_OFF = ""
    }
}

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
     * panels, lifts); [exitZone] is where he is going; [lifts] are the ones he waits for;
     * [beams] are the lasers he waits at when they are on, or will be before he is through
     * (GameWorld.beamsHeWaitsAt, [isAtBeam]).
     */
    fun update(dt: Double, platforms: List<Rect>, exitZone: Rect, lifts: List<Lift> = emptyList(), beams: List<Beam> = emptyList()) {
        isWalking = false
        isHeld = false
        if (isFree) freedFor += dt
        if (!hasEscaped && bounds.intersects(exitZone)) hasEscaped = true
        val arrived = hasEscaped && body.centerX * def.facing >= exitZone.centerX * def.facing
        val ready = isFree && freedFor >= STAND_UP_SECONDS && !arrived
        val atLip = ready && body.isGrounded &&
            (isAtLip(platforms) || isAtRaisedLift(lifts) || isAtMovingLift(lifts) || isAtBeam(beams))
        val move = if (ready && !atLip) def.facing else 0.0
        val before = body.x
        body.update(
            dt, move, jumpInput = false, crouchInput = false, platforms = platforms,
            steps = lifts.map { it.bounds }
        )
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
     * is a little ahead of that centre. A crack narrower than 2 x [CRACK] with footing on both
     * sides is no drop - his feet span it, as the player's do (level 12's dropped crate sits half
     * a unit off each face of its slot: flush, it never touches the crates under it).
     */
    private fun isAtLip(platforms: List<Rect>): Boolean {
        val feet = body.y + body.height
        val px = body.centerX + def.facing * LIP_PROBE_AHEAD
        fun footing(x: Double) = platforms.any { p ->
            x >= p.left && x <= p.right && p.top >= feet - 1.0 && p.top <= feet + MAX_STEP_DOWN
        }
        return !footing(px) && !(footing(px - CRACK) && footing(px + CRACK))
    }

    /**
     * The shaft of a lift that comes down to the floor he stands on, but is not down, starts just
     * ahead of him - he stops short of it, clear of the platform's way down. Not for a lift with
     * nothing beyond its shaft to hold him under it (LiftDef.holdsHimWhileUp).
     */
    private fun isAtRaisedLift(lifts: List<Lift>): Boolean {
        val feet = body.y + body.height
        return lifts.any { l ->
            l.def.holdsHimWhileUp && kotlin.math.abs(l.def.floorY - feet) < 1.0 && l.topY < l.def.lowerY - 1.0 &&
                if (def.facing > 0.0) {
                    body.centerX < l.def.x && body.x + body.width + LIFT_WAIT_GAP >= l.def.x
                } else {
                    body.centerX > l.def.x + l.def.width && body.x - LIFT_WAIT_GAP <= l.def.x + l.def.width
                }
        }
    }

    /**
     * A lift he would step onto or off - the one under him, or the one just ahead - is still on
     * its way: he waits for it to stop rather than step down onto it as it comes up ("prisoner
     * tries to jump down before the elevator is completely up ... make sure he waits until it is
     * at the top before going onto it", 2026-10-26).
     */
    private fun isAtMovingLift(lifts: List<Lift>): Boolean {
        val feet = body.y + body.height
        val px = body.centerX + def.facing * LIP_PROBE_AHEAD
        return lifts.any { l ->
            if (!l.isMoving) return@any false
            val left = l.def.x
            val right = l.def.x + l.def.width
            val under = body.centerX >= left && body.centerX <= right && kotlin.math.abs(l.topY - feet) < 4.5
            under || px >= left && px <= right
        }
    }

    /**
     * The beam no more than [BEAM_WAIT_GAP] ahead of him - with any beams past it too close
     * together to stand between, which he has to cross in the same go - is one he could not reach
     * off and be through before it comes on ([BEAM_MARGIN_SECONDS] to spare either side). Once he sets off he
     * never stops inside a beam, and a run he set off for stays one he can cross: the time he needs
     * shrinks as he walks exactly as fast as the time the beams stay off.
     */
    private fun isAtBeam(beams: List<Beam>): Boolean {
        if (beams.isEmpty()) return false
        val b = bounds
        fun gapTo(r: Rect) = if (def.facing > 0.0) r.left - b.right else b.left - r.right
        val level = beams.filter { it.bounds.bottom > b.top && it.bounds.top < b.bottom }
        if (level.any { gapTo(it.bounds) < 0.0 && gapTo(it.bounds) > -(b.width + it.bounds.width) }) return false
        val ahead = level.filter { gapTo(it.bounds) >= 0.0 }.sortedBy { gapTo(it.bounds) }
        val first = ahead.firstOrNull() ?: return false
        if (gapTo(first.bounds) > BEAM_WAIT_GAP) return false
        val run = arrayListOf(first)
        for (next in ahead.drop(1)) {
            val room = gapTo(next.bounds) - gapTo(run.last().bounds) - run.last().bounds.width
            if (room >= b.width + BEAM_WAIT_GAP + 1.0) break
            run.add(next)
        }
        return run.any { beam ->
            val gap = gapTo(beam.bounds)
            val reach = gap / def.speed
            val through = (gap + b.width + beam.bounds.width) / def.speed
            (beam.offIn > 0.0 && beam.offIn > reach - BEAM_MARGIN_SECONDS) ||
                through + BEAM_MARGIN_SECONDS > beam.offIn + beam.offFor
        }
    }

    /**
     * A laser as he sees it: where it is, how long until it goes off ([offIn], 0 while it is
     * off, infinite for one that never does) and how long it then stays off ([offFor]).
     */
    data class Beam(val bounds: Rect, val offIn: Double, val offFor: Double)

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
        /** How far short of a raised lift's shaft he stops. */
        const val LIFT_WAIT_GAP = 2.0
        /** Half the widest crack he walks over - see [isAtLip]. */
        const val CRACK = 2.0
        /** How far short of a beam he waits - see [isAtBeam]. */
        const val BEAM_WAIT_GAP = 4.0
        /** What he leaves himself to spare either side of a beam's gap: there after it goes out, through before it comes on - see [isAtBeam]. */
        const val BEAM_MARGIN_SECONDS = 0.12
    }
}
