package game.model

/**
 * How a [DoorDef] is drawn. Both behave the same: a panel that slides up into the lintel.
 * [SHUTTER] is a steel roller door across the duct or a room; [BARS] is a barred cell gate.
 */
enum class DoorStyle { SHUTTER, BARS }

/**
 * A door across a corridor (level 11, "11: The Prisoner"): floor to ceiling between [top] and
 * [bottom], [width] thick, at [x]. Closed it is a wall to everyone - the player, the prisoner, a
 * guard (who turns back at it, see Guard.update's blocker) - and to sight. It opens by sliding its
 * panel up into the lintel, so while it moves only the part still hanging is solid: a body passes
 * once the panel's lower edge is over its head.
 *
 * Doors are thrown from [DoorSwitchDef]s, never walked open - "you can use doors as a new
 * mechanism for this level".
 */
data class DoorDef(
    val id: String,
    val x: Double,
    val top: Double,
    val bottom: Double,
    val width: Double = 24.0,
    val startsOpen: Boolean = false,
    val style: DoorStyle = DoorStyle.SHUTTER
)

class Door(val def: DoorDef) {
    val id: String get() = def.id
    /** The whole doorway, open or not. */
    val frame: Rect = Rect(def.x, def.top, def.width, def.bottom - def.top)

    /** Where the door is going: true = open. Thrown by a switch; the panel follows at its own pace. */
    var isOpen: Boolean = def.startsOpen
        private set

    /** 0 = shut, 1 = fully up in the lintel. */
    var openness: Double = if (def.startsOpen) 1.0 else 0.0
        private set

    /** Held part-way down because something is standing in the doorway - see [update]. */
    var isHeldBySensor: Boolean = false
        private set

    val panelHeight: Double get() = frame.height * (1.0 - openness)

    /** The solid part: the panel still hanging in the doorway, or null once it is clear of it. */
    val panel: Rect? get() = if (panelHeight <= 0.5) null else Rect(frame.x, frame.y, frame.width, panelHeight)

    val isFullyShut: Boolean get() = openness <= 0.0

    fun toggle() {
        isOpen = !isOpen
    }

    fun set(open: Boolean) {
        isOpen = open
    }

    /**
     * Moves the panel towards [isOpen]. Closing, it will not come down on anything standing in the
     * doorway ([bodies] - the player, the prisoner, guards): like a real shutter's safety edge it
     * stops and waits, so a door shut on the prisoner's back never crushes him, it just holds
     * until he is through.
     */
    fun update(dt: Double, bodies: List<Rect>) {
        isHeldBySensor = false
        if (isOpen) {
            openness = (openness + dt / OPEN_SECONDS).coerceAtMost(1.0)
            return
        }
        if (openness <= 0.0) return
        val next = (openness - dt / CLOSE_SECONDS).coerceAtLeast(0.0)
        val nextBottom = frame.y + frame.height * (1.0 - next)
        val blocked = bodies.any { b ->
            b.right > frame.left && b.left < frame.right && b.bottom > frame.top && b.top < nextBottom
        }
        if (blocked) {
            isHeldBySensor = true
            return
        }
        openness = next
    }

    fun reset() {
        isOpen = def.startsOpen
        openness = if (def.startsOpen) 1.0 else 0.0
        isHeldBySensor = false
    }

    /** For a checkpoint: puts the door exactly as it was when the checkpoint was taken. */
    fun restore(open: Boolean, openness: Double) {
        isOpen = open
        this.openness = openness
        isHeldBySensor = false
    }

    companion object {
        /** Full travel, shut to open. A 136-tall duct door is passable (panel over a head) at ~0.7. */
        const val OPEN_SECONDS = 0.55
        const val CLOSE_SECONDS = 0.45
    }
}

/**
 * A platform that rides a shaft between two stops (level 11's freight lifts): its top is at
 * [upperY] or [lowerY], [x]..[x]+[width] across. At the lower stop it sinks flush into the floor
 * ([lowerY] is the floor's top), so it is walked over like floor; at the upper stop it fills its
 * gap in the room floor. Bodies standing on it ride it.
 */
data class LiftDef(
    val id: String,
    val x: Double,
    val width: Double,
    val upperY: Double,
    val lowerY: Double,
    val startsUp: Boolean,
    val speed: Double = 80.0,
    val thickness: Double = 14.0
)

class Lift(val def: LiftDef) {
    val id: String get() = def.id
    var isUp: Boolean = def.startsUp
        private set
    var topY: Double = if (def.startsUp) def.upperY else def.lowerY
        private set
    val bounds: Rect get() = Rect(def.x, topY, def.width, def.thickness)
    val isMoving: Boolean get() = topY != (if (isUp) def.upperY else def.lowerY)

    /** The whole shaft the platform travels, for "is anything in its way". */
    val shaft: Rect get() = Rect(def.x, def.upperY, def.width, def.lowerY - def.upperY + def.thickness)

    fun toggle() {
        isUp = !isUp
    }

    /**
     * Moves the platform towards its stop and returns how far it moved (negative = up). It holds
     * where it is while anything in [blockers] is under it in the shaft - it never comes down on
     * a body - or while [frozen] (a guard in the shaft: the lift is not for carrying guards).
     */
    fun update(dt: Double, blockers: List<Rect>, frozen: Boolean): Double {
        val target = if (isUp) def.upperY else def.lowerY
        if (topY == target || frozen) return 0.0
        val step = def.speed * dt
        val next = if (target > topY) minOf(target, topY + step) else maxOf(target, topY - step)
        if (next > topY) {
            val nextBottom = next + def.thickness
            val under = blockers.any { b ->
                b.right > def.x && b.left < def.x + def.width && b.top >= topY + def.thickness - 1.0 && b.top < nextBottom
            }
            if (under) return 0.0
        }
        val dy = next - topY
        topY = next
        return dy
    }

    fun reset() {
        isUp = def.startsUp
        topY = if (def.startsUp) def.upperY else def.lowerY
    }

    fun restore(up: Boolean, topY: Double) {
        isUp = up
        this.topY = topY
    }
}

/**
 * A wall switch: INTERACT in reach throws every door and lift in [targets] (by id) the other way.
 * One switch can work several things - LEVEL_11_LAYOUT's control room works the duct under it
 * from above, where the prisoner cannot follow and the guards cannot see.
 *
 * [surfaceY] is the floor it is mounted over; it is reached from that floor only, like a lever.
 */
data class DoorSwitchDef(
    val id: String,
    val x: Double,
    val surfaceY: Double,
    val targets: List<String>,
    val interactRadius: Double = 30.0
) {
    val centerX: Double get() = x + WIDTH / 2.0

    fun isPlayerInRange(player: Player): Boolean {
        val feet = player.y + player.height
        return kotlin.math.abs(player.centerX - centerX) <= interactRadius && kotlin.math.abs(feet - surfaceY) <= 6.0
    }

    companion object {
        /** The panel's drawn size; it hangs on the wall at chest height over [surfaceY]. */
        const val WIDTH = 12.0
        const val HEIGHT = 18.0
        const val MOUNT_HEIGHT = 58.0
    }
}
