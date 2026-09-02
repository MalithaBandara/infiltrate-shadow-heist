package game.scene

import korlibs.audio.sound.*
import korlibs.io.file.std.*
import kotlin.coroutines.CoroutineContext

/**
 * The movement foley, loaded once per gameplay scene.
 *
 * Every movement clip here was cut from the audio track of the animation plate it belongs to
 * (`walk_new.mp4`, `crouch_new.mp4`, `jump_new.mp4`, `climb.mp4`), so the sound and the pose it
 * plays under come from the same take. `ui_click` is the single exception - it is Kenney's
 * `click3` (CC0), because a button press is not a thing the character does and there is no plate
 * it could have come from. The cuts were made against the waveform rather than by
 * ear: each source has long silent runs around the action, and the trims sit on the measured
 * onsets. One shared gain was applied across all five instead of normalising each one, because
 * the source set is already balanced the way the game wants it - the crouch foley is genuinely
 * quiet and the jump impact is genuinely the loudest thing in the set, and per-clip
 * normalisation would have flattened exactly that difference.
 *
 * Deliberately absent: a crouch-walk sound. `Player.currentNoiseRadius` reports SILENT while
 * crouched, guards cannot hear the player in that stance, and the whole reason to crouch-walk is
 * that it makes no noise - so putting a footstep on it would contradict the mechanic it exists
 * to serve. The crouch clip plays on the stance change into a crouch and then nothing until the
 * player stands back up. `crouchwalk.mp4` does carry an audio track; it is not used.
 */
class GameSounds(
    val stepA: Sound?,
    val stepB: Sound?,
    val crouch: Sound?,
    val impact: Sound?,
    val climb: Sound?,
    val takeoff: Sound?,
    val uiClick: Sound?
)

object GameAudio {

    /**
     * Relative levels, applied on top of the player's SFX volume setting.
     *
     * The take-off and the landing are now two different cuts of `jump_new.mp4`, not one sample
     * used twice. An earlier pass read that source as holding a single transient and had the
     * push-off replay the landing at low gain; a per-frame scan of the waveform shows the
     * push-off is there from 0.49s to 1.10s, it just sits about 34 dB under the landing at
     * 1.625s (which is itself two impacts, 1.625s and 1.665s - both feet).
     *
     * That 34 dB is why each clip is peak-normalised on its own rather than sharing one gain the
     * way the movement foley does: at its recorded level the push-off is inaudible on a phone
     * speaker. The balance between them therefore lives here, in the gain table, where it can be
     * retuned without recutting the assets - the take-off stays clearly the lighter of the two,
     * because landing is still the loud, guard-attracting half of a jump.
     */
    const val STEP_GAIN = 0.55
    const val CROUCH_GAIN = 0.9
    const val TAKEOFF_GAIN = 0.45
    const val LANDING_GAIN = 0.85
    const val CLIMB_GAIN = 0.7

    /**
     * One click serves every button and tap surface in the game, at two different weights.
     *
     * [UI_CLICK_GAIN] is for deliberate presses - the pause button, the pause-menu strips, the
     * Mission Failed buttons. [HUD_TAP_GAIN] is for the on-screen D-pad, jump and crouch
     * controls, which fire continuously while the player is moving; the same sample at the same
     * level would become the loudest recurring thing in a level, so it is mixed well under.
     */
    const val UI_CLICK_GAIN = 0.6
    const val HUD_TAP_GAIN = 0.3

    /**
     * Phases within one gait cycle at which a foot reaches the ground, measured off the walk
     * plate rather than assumed to be 0.0 and 0.5: a per-frame scan of the bottom of the
     * silhouette puts the front foot's contact at loop frames 3 and 15 of 22, and the footage's
     * gait is very slightly uneven, so the two steps are 0.54 apart rather than an even half.
     * Driving the sound off the same distance-based cycle progress the animation uses keeps the
     * footstep on the frame the foot actually lands.
     */
    val STEP_PHASES = doubleArrayOf(0.16, 0.70)

    suspend fun load(): GameSounds {
        suspend fun clip(name: String): Sound? =
            try { resourcesVfs["sfx/$name.wav"].readSound() } catch (_: Throwable) { null }
        return GameSounds(
            stepA = clip("step_a"),
            stepB = clip("step_b"),
            crouch = clip("crouch"),
            impact = clip("impact"),
            climb = clip("climb"),
            takeoff = clip("takeoff"),
            uiClick = clip("ui_click")
        )
    }
}

/**
 * Fire-and-forget one-shot, called from the update loop rather than a coroutine - hence the
 * non-suspend `play(context, params)` overload rather than the suspending one.
 *
 * Missing clips are a no-op rather than a crash: the sounds are loaded defensively, since a
 * stripped build or a bad asset path should cost the player their audio, not the level.
 */
fun Sound?.playSfx(context: CoroutineContext, gain: Double, sfxVolume: Float) {
    val sound = this ?: return
    val volume = gain * sfxVolume.toDouble()
    if (volume <= 0.001) return
    sound.play(context, PlaybackParameters(volume = volume.coerceIn(0.0, 1.0)))
}
