package game.scene

import com.sample.demo.audio.GameSfxOutput
import com.sample.demo.audio.getGameSfxOutput
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
 * The movement foley, loaded once per gameplay scene.
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
    val bgMusic: Sound?
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
        for (sound in listOf(stepA, stepB, impact, climb, uiClick, guardInvestigate, toastSuccess, cameraDetect)) {
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
     * to -3dBFS. Fires once on the rising edge of `GuardState.PATROL -> INVESTIGATING` - see the
     * `guardWasInvestigating` edge-detection in `GameplayScene.kt`'s update loop - not on every
     * frame a guard stays investigating, and not on the frame it returns to patrol.
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
        val ALL = listOf(STEP_A, STEP_B, IMPACT, CLIMB, UI_CLICK, GUARD_INVESTIGATE, TOAST_SUCCESS, CAMERA_DETECT)
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

    suspend fun load(): GameSounds {
        suspend fun clip(name: String): Sound? =
            try { resourcesVfs["sfx/$name.wav"].readSound() } catch (_: Throwable) { null }
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
            bgMusic = music("bgmusic")
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
    sound.play(context, PlaybackParameters(volume = volume.coerceIn(0.0, 1.0)))
}
