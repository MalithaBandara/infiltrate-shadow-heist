package game.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A finished run, recorded so a later level can play it back - level 8's run, replayed in level 9
 * as the operative's own earlier pass through the yard ("he is following behind his previous run
 * in level 8 at level 9"). See [RunRecorder] for how it is taken and [EchoRunner] for the replay.
 *
 * One [RunSample] every [step] seconds of level time, plus the handful of things the run did to
 * the level that the replay has to do again at the same moment ([RunEvent]: the lever, the bots
 * switched off). The carts it pushed ride in every sample ([cartIds] names them), so the replay
 * moves them exactly as they moved.
 *
 * Each sample carries the body's model state (position, collision height, facing, stance) and,
 * when the scene was there to see it, the sprite frame it was drawn with - so what plays back is
 * what the player watched, frame for frame. A run recorded without a scene (the bundled default,
 * made from the level 8 walkthrough test) has no frames; the scene picks them from the stance.
 */
class RunRecording(
    val step: Double,
    val cartIds: List<String>,
    val samples: List<RunSample>,
    val events: List<RunEvent>,
    /**
     * The level clock when the run started (its first step - see GameWorld.runStartSeconds). The
     * lasers and the moving loads run on that clock, so the replay's level has to start there too
     * or the body walks through beams and crates that were somewhere else when it passed.
     */
    val worldStart: Double = 0.0,
    /**
     * What each value of [RunSample.world] is: the parts of the level that keep their own time
     * (a camera's sweep, a bot's patrol, a hook rig's travel) and so cannot be worked out from the
     * clock. Named by GameWorld.worldTrackNames: `cam<i>` (radians), `bot:<id>` (x, negative
     * while it faces left), `hook:<id>` (the rig's sweep clock).
     */
    val worldTracks: List<String> = emptyList()
) {
    val duration: Double get() = if (samples.isEmpty()) 0.0 else (samples.size - 1) * step

    /** True for a run that carries the level's own state - what an exact replay needs. */
    val hasWorld: Boolean get() = worldTracks.isNotEmpty()

    /** The sample at level time [t], position interpolated with the next one; clamped to the ends. */
    fun sampleAt(t: Double): RunSample {
        require(samples.isNotEmpty())
        val f = (t / step).coerceIn(0.0, (samples.size - 1).toDouble())
        val i = floor(f).toInt().coerceAtMost(samples.size - 1)
        val a = samples[i]
        if (i + 1 >= samples.size) return a
        val b = samples[i + 1]
        val k = f - i
        // A jump of more than a stride between two samples is a teleport (a climb finishing, a
        // respawn) - never smear the body across it.
        if (abs(b.x - a.x) > 40.0 || abs(b.y - a.y) > 40.0) return if (k < 0.5) a else b
        val near = if (k < 0.5) a else b
        return near.copy(
            x = a.x + (b.x - a.x) * k,
            y = a.y + (b.y - a.y) * k,
            cartXs = if (a.cartXs.size == b.cartXs.size) DoubleArray(a.cartXs.size) { a.cartXs[it] + (b.cartXs[it] - a.cartXs[it]) * k } else near.cartXs,
            spriteX = a.spriteX + (b.spriteX - a.spriteX) * k,
            spriteY = a.spriteY + (b.spriteY - a.spriteY) * k,
            world = if (a.world.size == b.world.size) DoubleArray(a.world.size) { j ->
                val va = a.world[j]
                val vb = b.world[j]
                // A bot turning round flips its sign - nothing to blend across that.
                if (va * vb < 0.0) (if (k < 0.5) va else vb) else va + (vb - va) * k
            } else near.world
        )
    }

    /**
     * Text form, for storage: `R2;step;cartIds;events;samples;worldStart;worldTracks`. Numbers are
     * scaled integers (body tenths; see [trackScale] for the world tracks), which keeps a
     * three-minute run to a few hundred KB.
     */
    fun encode(): String {
        val sb = StringBuilder()
        val scales = worldTracks.map { trackScale(it) }
        sb.append("R2;").append(step).append(';')
        sb.append(cartIds.joinToString(",")).append(';')
        sb.append(events.joinToString(",") { "${(it.t * 100).roundToInt()}:${it.kind.code}:${it.id}" }).append(';')
        var first = true
        for (s in samples) {
            if (!first) sb.append('|')
            first = false
            sb.append(t10(s.x)).append(' ').append(t10(s.y)).append(' ').append(t10(s.height)).append(' ')
                .append(s.flags).append(' ').append((s.phase * 100).roundToInt()).append(' ')
                .append(s.cartXs.joinToString(",") { t10(it).toString() }).append(' ')
                .append(s.frame).append(' ').append(t10(s.spriteX)).append(' ').append(t10(s.spriteY)).append(' ')
                .append(t10(s.rotation)).append(' ')
                .append(s.world.indices.joinToString(",") { (s.world[it] * scales.getOrElse(it) { 100.0 }).roundToInt().toString() })
        }
        sb.append(';').append(worldStart).append(';').append(worldTracks.joinToString(","))
        return sb.toString()
    }

    companion object {
        private fun t10(v: Double): Int = (v * 10.0).roundToInt()

        /** Fixed-point scale a world track is stored at: a camera angle to the milliradian. */
        private fun trackScale(track: String): Double = if (track.startsWith("cam")) 1000.0 else 100.0

        /**
         * Parses [encode]'s output - and the older R1 form, which has no [worldStart] or world
         * tracks ([hasWorld] false). Null for anything unreadable, so a bad save costs only the replay.
         */
        fun decode(text: String): RunRecording? = try {
            val parts = text.split(';')
            require((parts.size == 5 && parts[0] == "R1") || (parts.size == 7 && parts[0] == "R2"))
            val worldStart = if (parts.size == 7) parts[5].toDouble() else 0.0
            val worldTracks = if (parts.size == 7 && parts[6].isNotEmpty()) parts[6].split(',') else emptyList()
            val scales = worldTracks.map { trackScale(it) }
            val step = parts[1].toDouble()
            val cartIds = if (parts[2].isEmpty()) emptyList() else parts[2].split(',')
            val events = if (parts[3].isEmpty()) emptyList() else parts[3].split(',').map { e ->
                val (t, k, id) = e.split(':', limit = 3)
                RunEvent(t.toInt() / 100.0, RunEventKind.values().first { it.code == k }, id)
            }
            val samples = if (parts[4].isEmpty()) emptyList() else parts[4].split('|').map { line ->
                val f = line.split(' ')
                RunSample(
                    x = f[0].toInt() / 10.0,
                    y = f[1].toInt() / 10.0,
                    height = f[2].toInt() / 10.0,
                    flags = f[3].toInt(),
                    phase = f[4].toInt() / 100.0,
                    cartXs = if (f[5].isEmpty()) DoubleArray(0) else f[5].split(',').map { it.toInt() / 10.0 }.toDoubleArray(),
                    frame = f[6].toInt(),
                    spriteX = f[7].toInt() / 10.0,
                    spriteY = f[8].toInt() / 10.0,
                    rotation = f[9].toInt() / 10.0,
                    world = if (f.size > 10 && f[10].isNotEmpty()) f[10].split(',').mapIndexed { j, v -> v.toInt() / scales.getOrElse(j) { 100.0 } }.toDoubleArray() else DoubleArray(0)
                )
            }
            require(samples.isNotEmpty())
            RunRecording(step, cartIds, samples, events, worldStart, worldTracks)
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * The body at one moment of a recorded run: [x]/[y] are the standing box's top-left (the player's
 * own x/y), [height] the collision height it had (crouched or not). [flags] is a bit set of
 * [RunSample.FACING_LEFT] and the stance bits; [phase] is the climb's progress (0..1) while climbing. [frame] is the sprite
 * frame the scene drew (a global index over every player clip, -1 when there was no scene), and
 * [spriteX]/[spriteY]/[rotation] where it drew it inside the body's box.
 */
data class RunSample(
    val x: Double,
    val y: Double,
    val height: Double,
    val flags: Int,
    val phase: Double = 0.0,
    val cartXs: DoubleArray = DoubleArray(0),
    val frame: Int = -1,
    val spriteX: Double = 0.0,
    val spriteY: Double = 0.0,
    val rotation: Double = 0.0,
    /** The level's own state at this moment, one value per [RunRecording.worldTracks] entry. */
    val world: DoubleArray = DoubleArray(0)
) {
    val facingLeft: Boolean get() = flags and FACING_LEFT != 0
    val isGrounded: Boolean get() = flags and GROUNDED != 0
    val isCrouching: Boolean get() = flags and CROUCHING != 0
    val isClimbing: Boolean get() = flags and CLIMBING != 0
    val isSwinging: Boolean get() = flags and SWINGING != 0
    val isPushing: Boolean get() = flags and PUSHING != 0
    val isRising: Boolean get() = flags and RISING != 0

    companion object {
        const val FACING_LEFT = 1
        const val GROUNDED = 2
        const val CROUCHING = 4
        const val CLIMBING = 8
        const val SWINGING = 16
        const val PUSHING = 32
        const val RISING = 64
    }
}

enum class RunEventKind(val code: String) { LEVER("L"), BOT("B"), CATCH("C"), RESET("R") }

/**
 * Something the run did to the level at level time [t]: pulled lever [id], switched off bot [id],
 * caught a falling load in a cart ([id] is `crateId>cartId`), or respawned at a checkpoint (RESET,
 * the level's mechanisms back to the start).
 */
data class RunEvent(val t: Double, val kind: RunEventKind, val id: String)

/**
 * Takes a [RunRecording] while a level is played: GameWorld calls [capture] every tick and
 * [event] for a lever or a bot, and the scene calls [annotate] once it has picked the frame.
 */
class RunRecorder(val cartIds: List<String>, val worldTracks: List<String> = emptyList(), val step: Double = STEP) {
    /** The level clock at the run's first step - see [RunRecording.worldStart]. */
    var worldStart: Double = 0.0

    private val samples = ArrayList<RunSample>()
    private val events = ArrayList<RunEvent>()
    private var unannotatedFrom = 0

    val sampleCount: Int get() = samples.size

    /** Adds a sample for every [step] of level time up to [t] that has not got one yet. */
    fun capture(t: Double, sample: () -> RunSample) {
        if (samples.size.toDouble() * step > t + 1e-9) return
        val s = sample()
        lastTaken = sample
        while (samples.size.toDouble() * step <= t + 1e-9) samples.add(s)
    }

    private var lastTaken: (() -> RunSample)? = null

    /** The body right now, the way the last [capture] reads it - for a closing sample. */
    fun lastSampleNow(): RunSample = lastTaken?.invoke() ?: samples.last()

    fun event(t: Double, kind: RunEventKind, id: String) {
        events.add(RunEvent(t, kind, id))
    }

    private var checkpointSamples = 0

    /** A checkpoint was secured: a respawn comes back to here ([rewindToCheckpoint]). */
    fun markCheckpoint() {
        checkpointSamples = samples.size
    }

    /**
     * A respawn: the attempt since the last checkpoint never happened as far as the replay is
     * concerned. Its events are dropped, and the body in every sample since the checkpoint becomes
     * [now] - the body standing where it respawned, with the carts where the respawn put them - so
     * the replay waits there as long as the failed attempt took. Each sample's [RunSample.world]
     * stays as recorded: the level itself did run on through the failed attempt (cameras sweeping,
     * bots patrolling, then the respawn's own reset), and the replay's level has to match it.
     */
    fun rewindToCheckpoint(now: RunSample? = null) {
        val keep = checkpointSamples.coerceAtMost(samples.size)
        if (keep < samples.size) {
            val body = now ?: lastSampleNow()
            for (i in keep until samples.size) samples[i] = body.copy(world = samples[i].world)
        }
        val cutoff = keep * step
        events.removeAll { it.t > cutoff + 1e-9 }
        unannotatedFrom = unannotatedFrom.coerceAtMost(keep)
    }

    /** Gives every sample captured since the last call the sprite frame the scene has just drawn. */
    fun annotate(frame: Int, spriteX: Double, spriteY: Double, rotation: Double, facingLeft: Boolean) {
        for (i in unannotatedFrom until samples.size) {
            val s = samples[i]
            val flags = if (facingLeft) s.flags or RunSample.FACING_LEFT else s.flags and RunSample.FACING_LEFT.inv()
            samples[i] = s.copy(frame = frame, spriteX = spriteX, spriteY = spriteY, rotation = rotation, flags = flags)
        }
        unannotatedFrom = samples.size
    }

    fun clear() {
        samples.clear()
        events.clear()
        unannotatedFrom = 0
        checkpointSamples = 0
        worldStart = 0.0
    }

    fun finish(): RunRecording = RunRecording(step, cartIds, samples.toList(), events.toList(), worldStart, worldTracks)

    companion object {
        /** 20 samples a second: smooth once interpolated, and a two-minute run stays small. */
        const val STEP = 0.05
    }
}

enum class EchoState { PLAYING, FINISHED }

/**
 * Level 9's echo: the operative's own level 8 run, played back in the same yard a step ahead of
 * him, which he is following ("keep record of the movements and play that in level 9").
 *
 * A pure replay: it never stops, turns or reacts to anything ("make him actually not turn around.
 * he will still have the vision cone and if the player is in that vision cone he will get caught
 * but he does not run or stop", 2026-09-29). Its cone looks the way the recorded body faced, and
 * a player inside it fills the alert meter like a guard's - a catch is Mission Failed. Sound means
 * nothing to it. "When the recording reaches the end, it just stops there and waits"
 * ([EchoState.FINISHED]). GameWorld drives it: [update] each tick, [see] from the detection pass.
 */
class EchoRunner(val recording: RunRecording) {
    var clock: Double = 0.0
        private set
    var state: EchoState = EchoState.PLAYING
        private set
    var current: RunSample = recording.sampleAt(0.0)
        private set
    private var nextEvent = 0
    /** True for the frames it has eyes on the player - the scene's pip. */
    var isSeeingPlayer: Boolean = false
        private set

    /** +1 / -1, the way the recorded body faced. */
    val facing: Double get() = if (current.facingLeft) -1.0 else 1.0
    val width: Double get() = PLAYER_WIDTH
    /** The standing box's top-left, as the player's own x/y - where the sprite is drawn from. */
    val x: Double get() = current.x
    val y: Double get() = current.y
    /** The collision box: [RunSample.height] tall (crouched or not), feet at y + standing height. */
    val bounds: Rect get() = Rect(current.x, current.y + PLAYER_HEIGHT - current.height, width, current.height)
    val centerX: Double get() = current.x + width / 2.0
    val center: Vec2d get() = bounds.let { Vec2d(it.centerX, it.centerY) }

    /**
     * Where the drawn figure's eye is this frame, in world space - set by the scene from the frame
     * it actually drew (GameplayScene, PlayerEyePoints), so the cone and what the figure sees both
     * start at the head on screen through every pose. Null where nothing draws it (tests), which
     * falls back to the estimate below.
     */
    var drawnEye: Vec2d? = null

    /**
     * The eyes: the drawn eye ([drawnEye]) when there is one. Otherwise an estimate from the box:
     * head height, a little ahead of the body's centre line - except braced into a cart,
     * where the drawn figure leans forward and its head is ~15 ahead and ~18 lower than a standing
     * one. There the cone starts at the push pose's head, the same point [Player.keyPoints] uses
     * for a braced player (Player.PUSH_HEAD_POINT_*), or it hangs in the air behind the figure.
     */
    val eyePosition: Vec2d
        get() = drawnEye ?: if (current.isPushing && current.isGrounded) {
            val visualHeight = PLAYER_HEIGHT * Player.VISUAL_HEIGHT_SCALE
            Vec2d(
                centerX + facing * Player.PUSH_HEAD_POINT_X * visualHeight,
                current.y + PLAYER_HEIGHT - Player.PUSH_HEAD_POINT_Y * visualHeight
            )
        } else Vec2d(centerX + facing * 6.0, bounds.top + 12.0)
    val facingAngle: Double get() = if (facing >= 0.0) 0.0 else PI
    val visionRange: Double get() = VISION_RANGE
    val visionFov: Double get() = VISION_FOV

    fun reset() {
        drawnEye = null
        clock = 0.0
        state = EchoState.PLAYING
        current = recording.sampleAt(0.0)
        nextEvent = 0
        isSeeingPlayer = false
    }

    /**
     * Advances the replay by [dt] and hands every recorded event it passes to [onEvent]. Returns
     * the sample it now stands at.
     */
    fun update(dt: Double, onEvent: (RunEvent) -> Unit): RunSample {
        if (state == EchoState.PLAYING) {
            clock = (clock + dt).coerceAtMost(recording.duration)
            current = recording.sampleAt(clock)
            if (clock >= recording.duration) state = EchoState.FINISHED
        }
        while (nextEvent < recording.events.size && recording.events[nextEvent].t <= clock + 1e-9) {
            onEvent(recording.events[nextEvent])
            nextEvent++
        }
        return current
    }

    /** The events it has already played - re-applied when a respawn resets the level's mechanisms. */
    fun passedEvents(): List<RunEvent> = recording.events.subList(0, nextEvent)

    /** The detection pass found the player in its cone, or did not. Changes nothing but the pip. */
    fun see(seen: Boolean) {
        isSeeingPlayer = seen
    }

    companion object {
        /** The player's collision box - the echo is the same body. */
        const val PLAYER_WIDTH = 36.0
        const val PLAYER_HEIGHT = 96.0
        /** A little under a guard's 260: no torch, just eyes. */
        const val VISION_RANGE = 230.0
        const val VISION_FOV = 60.0 * PI / 180.0
    }
}
