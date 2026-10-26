package game.scene

import com.sample.demo.audio.GameSfxOutput
import com.sample.demo.audio.getGameSfxOutput
import game.model.GameWorld
import korlibs.audio.sound.*
import korlibs.io.file.std.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Android-only native path for one-shots, alongside korlibs' own per-call `AudioTrack` - see
 * [GameSfxOutput]'s own doc comment for why. Lazy so platforms that return null (everything but
 * Android) never pay even the lookup cost more than once.
 */
private val gameSfxOutput: GameSfxOutput? by lazy { getGameSfxOutput() }

/**
 * The movement foley, handed to every gameplay scene (the clips are decoded once per process -
 * see GameAudio.clipCache - and the music once per scene).
 *
 * Every movement clip here was cut from the audio track of the animation plate it belongs to
 * (`walk_new.mp4`, `jump_new.mp4`, `climb.mp4`), so the sound and the pose it plays under come
 * from the same take. `ui_click` is the single exception - it is Kenney's `click3` (CC0), because
 * a button press is not a thing the character does and there is no plate it could have come from.
 * The cuts were made against the waveform rather than by ear: each source has long silent runs
 * around the action, and the trims sit on the measured onsets. One shared gain is applied across
 * all four instead of normalising each one, because the source set is already balanced the way
 * the game wants it, and per-clip normalisation would flatten that.
 *
 * Deliberately silent: crouch and crouch-walk both have no sound at all, by design - not a
 * missing asset. `Player.currentNoiseRadius` reports SILENT in both stances, guards cannot hear
 * the player, and the whole reason to crouch is that it makes no noise, so playing anything on
 * the stance change or the crouch-walk cycle would contradict the mechanic it exists to serve.
 * There used to be a crouch-entry/exit clip here (cut from `crouch_new.mp4`); removed once that
 * contradiction was pointed out, not because the clip itself was broken.
 */
class GameSounds(
    val stepA: Sound?,
    val stepB: Sound?,
    val impact: Sound?,
    val climb: Sound?,
    val uiClick: Sound?,
    val guardInvestigate: Sound?,
    val toastSuccess: Sound?,
    val cameraDetect: Sound?,
    val bgMusic: Sound?,
    val thunder: Sound? = null,
    val crateDrop: Sound? = null,
    /** Looping ambience beds: seamless PCM loops in `sfx/loop_*.wav`, played through [AmbientLoops]. */
    val rain: Sound? = null,
    val fan: Sound? = null,
    val laserHum: Sound? = null,
    val steam: Sound? = null,
    val robot: Sound? = null,
    val cameraMotor: Sound? = null,
    val cartRoll: Sound? = null,
    val elevator: Sound? = null,
    /** Wall-switch click and lever clack, one-shots. */
    val switchPress: Sound? = null,
    val leverThrow: Sound? = null,
    /** Chain-dragging bed for a travelling hanging crate ([AmbientLoops]), and the three event stingers. */
    val chain: Sound? = null,
    val missionFail: Sound? = null,
    val missionSuccess: Sound? = null,
    val powerupActivate: Sound? = null
) {
    /**
     * Plays every loaded clip once at zero volume, immediately, then stops it - this is a
     * warm-up, not a sound. On Android, `Sound.play()` (`SoundAudioData.play()` in
     * korlibs-audio-core-android) constructs a brand new `android.media.AudioTrack` on every
     * single call - confirmed by decompiling the dependency, not assumed - and a freshly
     * constructed `AudioTrack` has real, device-dependent startup latency before it actually
     * produces sound, worst on the first one a process ever creates (cold audio HAL/mixer
     * thread). Paying that cost once here, silently, during the scene's own loading (before the
     * player can do anything that would trigger a real sound) means the first real jump/landing
     * in a level isn't the one that eats it. Volume 0 still exercises the real construction path
     * - `SoundAudioData.play()` creates and starts the platform output unconditionally; volume
     * only scales the samples written into it - so this isn't a no-op.
     *
     * Each clip is primed inside its own `try`/`catch`: this runs again on every RESTART/RETRY
     * (a fresh `GameplayScene` means a fresh `GameAudio.load()`, so there's nothing to prime
     * ahead of time the way the very first load could), and constructing several `AudioTrack`s in
     * quick succession is exactly the kind of thing that can fail on a real device even though it
     * didn't on the first, cold call - if it does, this must not take the rest of scene setup down
     * with it. Priming is a latency optimization; the scene loading at all is not optional.
     */
    fun primeAll(context: CoroutineContext) {
        gameSfxOutput?.prepare(GameAudio.SfxFile.ALL)
        for (sound in listOf(stepA, stepB, impact, climb, uiClick, guardInvestigate, toastSuccess, cameraDetect, thunder, crateDrop, switchPress, leverThrow, missionFail, missionSuccess, powerupActivate)) {
            try {
                sound?.play(context, PlaybackParameters(volume = 0.0))?.stop()
            } catch (_: Throwable) {
            }
        }
    }
}

object GameAudio {

    /**
     * Relative levels, applied on top of the player's SFX volume setting.
     *
     * [LANDING_GAIN] is peak-normalised on its own rather than sharing [STEP_GAIN]'s single
     * level, the way the rest of the movement foley does, because landing is the loud,
     * guard-attracting half of a jump and needs its own headroom.
     *
     * There used to be a take-off/push-off clip and gain here too, cut from the same
     * `jump_new.mp4` source as the landing. Removed (not just muted) after it turned out to be
     * unusable: measuring its waveform showed sustained high energy across the whole clip with no
     * attack-decay shape anywhere, the signature of broadband noise rather than a real transient -
     * it played back as static, not a sound effect. A future replacement would need a real recut
     * from source, listened to before landing back in this table.
     */
    const val STEP_GAIN = 0.55
    const val LANDING_GAIN = 0.85
    const val CLIMB_GAIN = 0.7

    /**
     * One click for deliberate presses - the pause button, the pause-menu strips, the Mission
     * Failed buttons. The on-screen D-pad/jump/crouch/interact touch controls do NOT play this -
     * they fire continuously while the player is moving, and the owner's explicit call (2026-09-11,
     * on real-device feedback) was that a click on every movement tap reads as noise, not
     * feedback. There used to be a quieter `HUD_TAP_GAIN` weight wired into those controls, but on
     * korlibs' old per-call `AudioTrack` path (see GameSfxOutput's own doc comment) real device
     * latency largely swallowed it, so it went unnoticed for a long time - switching gameplay SFX
     * onto a pooled, always-ready output (fixing an unrelated static/glitch bug) made it play
     * cleanly and audibly for the first time, which is what actually surfaced the complaint.
     * Removed outright rather than just lowered further - don't re-add a tap sound to
     * `createTouchBtn`/`createImgBtn` without the owner asking again.
     *
     * Was 0.6, raised to 0.85 after real-device feedback that presses on the pause/death-menu
     * buttons weren't audible - then brought back down to 0.65 after further feedback that 0.85
     * overshot into too loud. The wiring (`playClick(UI_CLICK_GAIN)` in this file's
     * `createPaperMenuBtn`/`createTacticalMenuBtn`/pause-button `onDown` handlers) has not changed
     * across any of these three values - the pause and Mission Failed buttons have played this
     * exact click since it was first added; only the level has moved.
     */
    const val UI_CLICK_GAIN = 0.65

    /**
     * Owner-supplied clip (Downloads/charAnimations/music/guard_investigate.wav), peak-normalised
     * to -3dBFS. Fires once on the rising edge of a guard hearing noise or beginning visual detection
     * of the player - see the `guardWasInvestigating` and `guardWasDetecting` edge-detection in
     * `GameplayScene.kt`'s update loop - not on every frame a guard stays alert, and not on the frame
     * it returns to patrol.
     */
    const val GUARD_INVESTIGATE_GAIN = 0.8

    /**
     * Same clip and file as the menu bus's success toast (`toast_success.wav`, shared out of
     * `resources/sfx/` - the menu bus and this one just reach it through different loaders). Used
     * for the "continue granted" moment when a rewarded ad is watched successfully - the owner's
     * call was not to source a distinct reward stinger, and reuse the one success sound everywhere
     * instead.
     */
    const val TOAST_SUCCESS_GAIN = 0.8

    /**
     * One-beat alert sound cut from Downloads/charAnimations/music/camera.mp3, played when a
     * security camera's vision cone detects the player.
     */
    const val CAMERA_DETECT_GAIN = 0.85

    /**
     * Level background music track (Downloads/charAnimations/music/bgmusic.mp3).
     */
    const val BG_MUSIC_GAIN = 0.65

    /**
     * Atmospheric thunder rumble/crack gain, played with realistic acoustic delay after lightning.
     */
    const val THUNDER_GAIN = 0.90

    /** A cut hanging load hitting something; scaled by how hard it landed (see the scene). */
    const val CRATE_DROP_GAIN = 0.9

    /** Level 2/9 rain bed - a constant backdrop, deliberately well under the music. */
    const val RAIN_LOOP_GAIN = 0.32

    /**
     * Ceiling of the vent-fan hum, reached at the fan itself; it fades to nothing at
     * its own hearing range (see the *_HEARING_RANGE constants) away.
     */
    const val FAN_LOOP_GAIN = 0.55

    /** Steam-jet hiss at the nozzle while it is erupting. */
    const val STEAM_LOOP_GAIN = 0.7

    /**
     * Laser hum. One shared voice for every beam, however many are lit: a second beam in earshot
     * adds [LASER_LOOP_EXTRA_PER_BEAM] on top of [LASER_LOOP_GAIN], capped at [LASER_LOOP_MAX_GAIN],
     * so a bank of beams is a little louder than one but never a pile of overlapping hums.
     */
    const val LASER_LOOP_GAIN = 0.32
    const val LASER_LOOP_EXTRA_PER_BEAM = 0.04
    const val LASER_LOOP_MAX_GAIN = 0.44

    /** Patrol-rover motor hum while a rover is rolling. */
    const val ROBOT_LOOP_GAIN = 0.75

    /** Security-camera servo whine while a camera head is actually turning. */
    const val CAMERA_LOOP_GAIN = 0.32

    /** Push-cart rolling, scaled by how fast the cart is moving. */
    const val CART_LOOP_GAIN = 0.55

    /** Freight-lift hum while a lift is actually travelling. */
    const val ELEVATOR_LOOP_GAIN = 0.7

    /** Door / lift wall switch click, and the lever clack. */
    const val SWITCH_PRESS_GAIN = 0.5
    const val LEVER_THROW_GAIN = 0.68

    /** Chain rattle while a hanging crate's rig is travelling (scaled by its speed and distance). */
    const val CHAIN_LOOP_GAIN = 0.75

    /**
     * Guard and escorted-prisoner footsteps: the player's own step clips, a little under
     * [STEP_GAIN] and scaled by distance so a far-off patrol is faint rather than absent.
     */
    const val NPC_STEP_GAIN = 1.0

    /** Mission Failed / level-complete stingers. */
    const val MISSION_FAIL_GAIN = 0.5
    const val MISSION_SUCCESS_GAIN = 0.6

    /** Powerup activation blip - deliberately very quiet (the owner asked for it much lower). */
    const val POWERUP_ACTIVATE_GAIN = 0.1

    /**
     * How far each source can be heard, in world units (straight-line, so a duct above or a floor
     * below counts). The visible field is ~770+ wide, so these keep a sound to what is on or just
     * off screen; volume falls off with the square of the remaining distance, so it is faint well
     * before the edge rather than a flat bed that simply stops.
     */
    const val FAN_HEARING_RANGE = 300.0
    const val STEAM_HEARING_RANGE = 240.0
    const val LASER_HEARING_RANGE = 380.0
    const val ROBOT_HEARING_RANGE = 340.0
    const val CAMERA_HEARING_RANGE = 300.0
    const val CART_HEARING_RANGE = 230.0
    const val ELEVATOR_HEARING_RANGE = 300.0
    const val LEVER_HEARING_RANGE = 480.0
    const val CRATE_HEARING_RANGE = 480.0
    const val CHAIN_HEARING_RANGE = 380.0
    const val NPC_STEP_HEARING_RANGE = 420.0

    /** Volume-units per second an ambient bed ramps at, so a bed never clicks on or off. */
    const val AMBIENT_RAMP_PER_SEC = 2.5

    /**
     * How fast `GameplayScene.kt`'s `syncBgMusicVolume` ramps toward a changed target volume,
     * in volume-units-per-second (full 0..1 sweep in 1/5s = 0.2s). See that function's own doc
     * comment - this replaced an instant step, which on pause's baseVol -> baseVol*0.35 change
     * was an audible click (a waveform discontinuity) on real hardware.
     */
    const val BG_MUSIC_VOLUME_RAMP_PER_SEC = 5.0

    /**
     * Asset paths for [gameSfxOutput]'s Android-only pooled path, mirrored against [GameAudio.load]'s
     * `clip(name)` calls below so both sides name the same file. `bgMusic` is deliberately absent -
     * it's a long, continuously-streamed track, not a short decoded-in-memory one-shot, so it stays
     * on korlibs' own output the way it always has; only the one-shots move.
     */
    object SfxFile {
        const val STEP_A = "sfx/step_a.wav"
        const val STEP_B = "sfx/step_b.wav"
        const val IMPACT = "sfx/impact.wav"
        const val CLIMB = "sfx/climb.wav"
        const val UI_CLICK = "sfx/ui_click.wav"
        const val GUARD_INVESTIGATE = "sfx/guard_investigate.wav"
        const val TOAST_SUCCESS = "sfx/toast_success.wav"
        const val CAMERA_DETECT = "sfx/camera_detect.wav"
        const val THUNDER = "sfx/thunder.wav"
        const val CRATE_DROP = "sfx/crate_drop.wav"
        const val SWITCH_PRESS = "sfx/switch_press.wav"
        const val LEVER_THROW = "sfx/lever_throw.wav"
        const val MISSION_FAIL = "sfx/mission_fail.wav"
        const val MISSION_SUCCESS = "sfx/mission_success.wav"
        const val POWERUP_ACTIVATE = "sfx/powerup_activate.wav"
        val ALL = listOf(STEP_A, STEP_B, IMPACT, CLIMB, UI_CLICK, GUARD_INVESTIGATE, TOAST_SUCCESS, CAMERA_DETECT, THUNDER, CRATE_DROP, SWITCH_PRESS, LEVER_THROW, MISSION_FAIL, MISSION_SUCCESS, POWERUP_ACTIVATE)
    }

    /**
     * Phases within one gait cycle at which a foot reaches the ground, measured off the walk
     * plate rather than assumed to be 0.0 and 0.5: a per-frame scan of the bottom of the
     * silhouette puts the front foot's contact at loop frames 3 and 15 of 22, and the footage's
     * gait is very slightly uneven, so the two steps are 0.54 apart rather than an even half.
     * Driving the sound off the same distance-based cycle progress the animation uses keeps the
     * footstep on the frame the foot actually lands.
     */
    val STEP_PHASES = doubleArrayOf(0.16, 0.70)

    /**
     * The same thing for the braced push gait, measured on the shipped 20-frame loop rather than
     * reused from [STEP_PHASES]: a contact-band scan of every frame under `art/frames/player/push`
     * (columns within 3px of each frame's lowest row) puts the two touchdowns at 13 and 36 of 40.
     * The push cycle is not the walk cycle rotated - its stance/swing split is different - so
     * borrowing walk's phases here would fire both steps visibly off the feet.
     */
    val PUSH_STEP_PHASES = doubleArrayOf(0.33, 0.90)

    // Real-device regression, not just theory: after primeAll() started running on every load,
    // "restart", "quit then play again", and "watch ad to continue" all started grey-screening
    // instead of loading the level. RESTART's crash (an uncaught exception from priming, since a
    // fresh GameplayScene means a fresh round of AudioTrack construction on every single reload,
    // not just the first) is fixed by primeAll()'s own try/catch above - but a hang, as opposed to
    // a thrown exception, wouldn't be caught by that, and the other two reports came from reload
    // paths where the try/catch fix alone didn't seem to be enough. Gating priming to the first
    // load only removes the repetition that made it risky, while keeping the one case the whole
    // optimization actually targets: the cold, first-ever AudioTrack a process constructs, which
    // is the expensive one. Every load after the first already benefits from whatever priming did
    // for the audio HAL/mixer thread - that's a process-wide effect, not tied to which specific
    // Sound object triggered it - so repeating it on every subsequent level (re)load was always
    // redundant work, not just occasionally risky work.
    @Volatile
    private var audioPrimed = false

    /**
     * Every `sfx/` clip decoded so far, by name - kept for the process, like SceneAssets' bitmaps.
     * [load] runs on every RESTART, RETRY, next level and continue, and used to read and decode all
     * seventeen WAVs from disk each time for the same unchanging PCM. A Sound is only data plus
     * the process-wide audio provider (each play() opens its own channel; nothing ever closes
     * one), so every scene can share the same objects. Only successful loads are kept, so a
     * missing clip is retried next time exactly as before. The music is NOT cached: readMusic()
     * streams from its file and is rebuilt per scene, as it always was.
     */
    private val clipCache = HashMap<String, Sound>()

    /** `resourcesVfs`-relative path korlibs' own `music("bgmusic")` loader reads - see [load]. */
    const val MUSIC_FILE = "music/bgmusic.mp3"

    /**
     * Whether [gameSfxOutput]'s native mixer took over bgmusic for this process - see
     * GameplayScene.kt's `syncBgMusicVolume` for how this changes its own behavior. Process-wide,
     * matching [audioPrimed]: the native engine (when present) is one continuous stream for the
     * app's whole life, not something each scene restarts.
     */
    @Volatile
    private var nativeMusicActive = false

    /** Best-effort; returns whether the native path is handling music from here on. */
    fun startNativeMusic(): Boolean {
        if (!nativeMusicActive) {
            nativeMusicActive = gameSfxOutput?.prepareMusic(MUSIC_FILE) == true
        }
        return nativeMusicActive
    }

    fun setNativeMusicVolume(volume: Float) {
        gameSfxOutput?.setMusicVolume(volume)
    }

    fun stopNativeMusic() {
        gameSfxOutput?.stopMusic()
        nativeMusicActive = false
    }

    /**
     * Test switch: [load] hands back no sounds at all - the path a missing clip already takes
     * everywhere (silent no-op). GameplaySceneArtTest builds every level in quick succession in
     * one JVM, and on desktop korlibs plays through a native backend (waveOut on Windows) that is
     * not meant to have scenes torn down under it every few frames.
     */
    internal var silentForTesting = false

    suspend fun load(): GameSounds {
        if (silentForTesting) {
            return GameSounds(
                stepA = null, stepB = null, impact = null, climb = null, uiClick = null,
                guardInvestigate = null, toastSuccess = null, cameraDetect = null, bgMusic = null
            )
        }
        suspend fun clip(name: String): Sound? {
            clipCache[name]?.let { return it }
            val loaded = try { resourcesVfs["sfx/$name.wav"].readSound() } catch (_: Throwable) { null }
            if (loaded != null) clipCache[name] = loaded
            return loaded
        }
        suspend fun music(name: String): Sound? =
            try { resourcesVfs["music/$name.mp3"].readMusic() } catch (_: Throwable) { null }
        val sounds = GameSounds(
            stepA = clip("step_a"),
            stepB = clip("step_b"),
            impact = clip("impact"),
            climb = clip("climb"),
            uiClick = clip("ui_click"),
            guardInvestigate = clip("guard_investigate"),
            toastSuccess = clip("toast_success"),
            cameraDetect = clip("camera_detect"),
            bgMusic = music("bgmusic"),
            thunder = clip("thunder"),
            crateDrop = clip("crate_drop"),
            rain = clip("loop_rain"),
            fan = clip("loop_fan"),
            laserHum = clip("loop_laser"),
            steam = clip("loop_steam"),
            robot = clip("loop_robot"),
            cameraMotor = clip("loop_camera"),
            cartRoll = clip("loop_cart"),
            elevator = clip("loop_elevator"),
            switchPress = clip("switch_press"),
            leverThrow = clip("lever_throw"),
            chain = clip("loop_chain"),
            missionFail = clip("mission_fail"),
            missionSuccess = clip("mission_success"),
            powerupActivate = clip("powerup_activate")
        )
        if (!audioPrimed) {
            audioPrimed = true
            sounds.primeAll(coroutineContext)
        }
        return sounds
    }
}

/**
 * Fire-and-forget one-shot, called from the update loop rather than a coroutine - hence the
 * non-suspend `play(context, params)` overload rather than the suspending one.
 *
 * Missing clips are a no-op rather than a crash: the sounds are loaded defensively, since a
 * stripped build or a bad asset path should cost the player their audio, not the level.
 *
 * [clipFile] (one of [GameAudio.SfxFile]) routes through [gameSfxOutput] first when present -
 * see that property's doc comment for why Android needs this. Any other platform, or a null
 * clipFile, falls straight through to the line below exactly as before this existed.
 */
fun Sound?.playSfx(context: CoroutineContext, gain: Double, sfxVolume: Float, clipFile: String? = null) {
    val sound = this ?: return
    val volume = gain * sfxVolume.toDouble()
    if (volume <= 0.001) return
    if (clipFile != null && gameSfxOutput?.play(clipFile, volume.toFloat()) == true) return
    try {
        sound.play(context, PlaybackParameters(volume = volume.coerceIn(0.0, 1.0)))
    } catch (_: Throwable) {
        // Defensive: headless test runners or environments lacking an audio backend (e.g. ALSA on Linux)
        // must never crash the game or test suite when attempting to play a sound effect.
    }
}


/**
 * The looping ambience beds (rain, vent fans, laser hum, steam). Each is one long-lived korlibs
 * channel started on first need and left running - only its volume moves, ramped, so a bed fades
 * in and out with distance instead of clicking. One channel per bed, never per source: a room of
 * five lasers is one hum, not five.
 */
class AmbientLoops(private val context: CoroutineContext) {
    private class Bed(val sound: Sound?) {
        var channel: SoundChannel? = null
        var applied = 0.0
    }

    private val beds = HashMap<String, Bed>()

    /** [target] is the wanted final volume (0 = silent); [dtSec] paces the ramp. */
    fun set(name: String, sound: Sound?, target: Double, dtSec: Double) {
        if (sound == null) return
        val bed = beds.getOrPut(name) { Bed(sound) }
        val goal = target.coerceIn(0.0, 1.0)
        val step = GameAudio.AMBIENT_RAMP_PER_SEC * dtSec
        val next = when {
            bed.applied < goal -> minOf(goal, bed.applied + step)
            bed.applied > goal -> maxOf(goal, bed.applied - step)
            else -> bed.applied
        }
        if (bed.channel == null) {
            if (next <= 0.001) return
            try {
                bed.channel = sound.playForever(context).also { it.volume = next }
                bed.applied = next
            } catch (_: Throwable) {
            }
            return
        }
        if (kotlin.math.abs(next - bed.applied) <= 0.0005) return
        try {
            bed.channel?.volume = next
            bed.applied = next
        } catch (_: Throwable) {
        }
    }

    fun stopAll() {
        for (bed in beds.values) {
            try {
                bed.channel?.stop()
            } catch (_: Throwable) {
            }
            bed.channel = null
            bed.applied = 0.0
        }
    }
}


/**
 * Lift hum, lever clack, hanging-crate chain rattle and the guards' / prisoner's footsteps, kept out
 * of the scene's giant `sceneMain` (the JVM's 64KB method limit).
 * [sync] runs once per frame beside the other ambience: the hum is one shared voice at the nearest
 * travelling lift's distance (a lift held by something in its shaft, or at rest, is silent), and a
 * lever's clack fires on the frame it is thrown - by hand or by REMOTE_TRIGGER alike. The wall
 * switches' click is not here: the world's `onSwitchThrown` callback plays it.
 *
 * The chain bed runs while a hanging crate's rig is travelling. Footsteps are the player's own two
 * clips fired on the player's contact phases ([GameAudio.STEP_PHASES]) of each walker's own
 * distance-driven gait - the prisoner walks on the player's frames, so his phases are exact; the
 * guards' contact frames were not measured separately, so theirs reuse the same two phases.
 * [walkCycleDistance] is the player's stride length (the prisoner's gait is driven by it).
 */
class MechanismSounds(
    private val world: GameWorld,
    private val sounds: GameSounds,
    private val context: CoroutineContext,
    private val walkCycleDistance: Double
) {
    private val leverWasOn = BooleanArray(world.levers.size) { world.levers[it].isActivated }
    private val liftLastY = DoubleArray(world.lifts.size) { world.lifts[it].topY }
    private val platformLast = DoubleArray(world.movingPlatforms.size * 2) { if (it % 2 == 0) world.movingPlatforms[it / 2].x else world.movingPlatforms[it / 2].y }
    private val hangingConveyor = world.conveyorCrates.filter { it.isHanging }
    private val conveyorLast = DoubleArray(hangingConveyor.size * 2) { if (it % 2 == 0) hangingConveyor[it / 2].x else hangingConveyor[it / 2].y }
    private val guardPrevX = DoubleArray(world.allGuards.size) { world.allGuards[it].x }
    private val guardPhase = DoubleArray(world.allGuards.size)
    private var prisonerPrevWalked = 0.0
    private var prisonerPrevPhase = 0.0
    private var stepAlternate = false

    private fun crossedStep(prev: Double, now: Double): Boolean {
        for (phase in GameAudio.STEP_PHASES) {
            val crossed = if (now >= prev) phase > prev && phase <= now else phase > prev || phase <= now
            if (crossed) return true
        }
        return false
    }

    private fun playStep(heard: Double, sfxVolume: Float) {
        if (heard <= 0.0) return
        stepAlternate = !stepAlternate
        if (stepAlternate) sounds.stepA.playSfx(context, GameAudio.NPC_STEP_GAIN * heard, sfxVolume, GameAudio.SfxFile.STEP_A)
        else sounds.stepB.playSfx(context, GameAudio.NPC_STEP_GAIN * heard, sfxVolume, GameAudio.SfxFile.STEP_B)
    }

    private fun proximity(sourceX: Double, sourceY: Double, range: Double): Double {
        val dx = (world.player.x + world.player.width / 2.0) - sourceX
        val dy = (world.player.y + world.player.height / 2.0) - sourceY
        val left = 1.0 - kotlin.math.sqrt(dx * dx + dy * dy) / range
        return if (left <= 0.0) 0.0 else left * left
    }

    fun sync(ambient: AmbientLoops, dtSec: Double, master: Double, sfxVolume: Float, paused: Boolean) {
        if (world.lifts.isNotEmpty()) {
            val step = maxOf(dtSec, 1e-4)
            var hum = 0.0
            for (i in world.lifts.indices) {
                val lift = world.lifts[i]
                val moved = kotlin.math.abs(lift.topY - liftLastY[i]) / step > 4.0
                liftLastY[i] = lift.topY
                if (moved) hum = maxOf(hum, proximity(lift.def.x + lift.def.width / 2.0, lift.topY, GameAudio.ELEVATOR_HEARING_RANGE))
            }
            ambient.set("elevator", sounds.elevator, GameAudio.ELEVATOR_LOOP_GAIN * hum * master, dtSec)
        }
        // One chain voice for every hanging crate that is moving this frame: the chained platforms
        // (`movingPlatforms`, all drawn as hanging crates), the conveyor-carried hanging crates, and
        // a hook crate whose rig is travelling. Crates at rest, or still hanging in place, are silent.
        val chainStep = maxOf(dtSec, 1e-4)
        var chain = 0.0
        fun hear(cx: Double, cy: Double, speed: Double) {
            if (speed > 4.0 && speed < 600.0) {
                chain = maxOf(chain, proximity(cx, cy, GameAudio.CHAIN_HEARING_RANGE) * (speed / 60.0).coerceIn(0.4, 1.0))
            }
        }
        for (i in world.movingPlatforms.indices) {
            val mp = world.movingPlatforms[i]
            val moved = kotlin.math.hypot(mp.x - platformLast[i * 2], mp.y - platformLast[i * 2 + 1])
            platformLast[i * 2] = mp.x
            platformLast[i * 2 + 1] = mp.y
            hear(mp.x + mp.width / 2.0, mp.y + mp.height / 2.0, moved / chainStep)
        }
        for (i in hangingConveyor.indices) {
            val c = hangingConveyor[i]
            val moved = kotlin.math.hypot(c.x - conveyorLast[i * 2], c.y - conveyorLast[i * 2 + 1])
            conveyorLast[i * 2] = c.x
            conveyorLast[i * 2 + 1] = c.y
            hear(c.x + c.width / 2.0, c.y + c.height / 2.0, moved / chainStep)
        }
        for (crate in world.hookCrates) {
            if (crate.isHanging) hear(crate.drawCenterX, crate.drawCenterY, kotlin.math.abs(crate.hookVx))
        }
        if (world.movingPlatforms.isNotEmpty() || hangingConveyor.isNotEmpty() || world.hookCrates.isNotEmpty()) {
            ambient.set("chain", sounds.chain, GameAudio.CHAIN_LOOP_GAIN * chain * master, dtSec)
        }
        for (i in world.allGuards.indices) {
            val g = world.allGuards[i]
            val moved = kotlin.math.abs(g.x - guardPrevX[i])
            guardPrevX[i] = g.x
            if (!g.isWalking || moved > 30.0 || paused) {
                if (!g.isWalking) guardPhase[i] = 0.0
                continue
            }
            val before = guardPhase[i]
            val after = (before + moved / (g.height * GuardAnimations.WALK_STRIDE_PER_HEIGHT)) % 1.0
            guardPhase[i] = after
            if (crossedStep(before, after)) {
                playStep(proximity(g.x + g.width / 2.0, g.y + g.height / 2.0, GameAudio.NPC_STEP_HEARING_RANGE), sfxVolume)
            }
        }
        val prisoner = world.prisoner
        if (prisoner != null) {
            val walked = prisoner.walkedDistance
            val phase = (walked / walkCycleDistance) % 1.0
            if (prisoner.isFree && prisoner.isWalking && walked >= prisonerPrevWalked && walked - prisonerPrevWalked < 30.0 && !paused &&
                crossedStep(prisonerPrevPhase, phase)
            ) {
                playStep(proximity(prisoner.body.centerX, prisoner.body.y + prisoner.body.height / 2.0, GameAudio.NPC_STEP_HEARING_RANGE), sfxVolume)
            }
            prisonerPrevWalked = walked
            prisonerPrevPhase = phase
        }
        for (i in world.levers.indices) {
            val lever = world.levers[i]
            val on = lever.isActivated
            if (on && !leverWasOn[i] && !paused) {
                val heard = proximity(lever.centerX, lever.centerY, GameAudio.LEVER_HEARING_RANGE)
                if (heard > 0.0) sounds.leverThrow.playSfx(context, GameAudio.LEVER_THROW_GAIN * heard, sfxVolume, GameAudio.SfxFile.LEVER_THROW)
            }
            leverWasOn[i] = on
        }
    }
}
