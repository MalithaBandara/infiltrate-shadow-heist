package game.scene

import korlibs.image.atlas.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.view.*
import korlibs.time.*
import kotlin.concurrent.Volatile

class GuardAnimationSet(
    val idle: SpriteAnimation,
    val walk: SpriteAnimation
)

/**
 * The guard's sprite frames - the same recipe as [PlayerAnimations], with the same rules.
 *
 * `resources/guard/idle` and `resources/guard/walk` are cut from the raw 360x640 plates by
 * `tools/art/prep_guard.py`: one shared crop box per clip, symmetric about the character's BODY
 * (not the union of body and outstretched torch arm) so a scaleX flip does not shift him, feet
 * pinned to the bottom row, scaled so the standing silhouette is ~244 frame pixels - the same
 * on-screen density as the player's frames. The constants below are printed by that script from
 * the frames it wrote; re-run it and paste rather than editing them by hand. The script's header
 * carries the per-clip reasoning (which raw frames, why, and how the stride was measured).
 *
 * He carries a torch at arm's length; where its lens sits relative to the body is
 * [Guard.TORCH_AHEAD_PER_HEIGHT] / [Guard.TORCH_ABOVE_FEET_PER_HEIGHT] in the model, measured by
 * the same script - that is where the vision cone starts, on screen and in the detection math.
 *
 * Guards on levels without a sprite-sized hitbox still draw this scaled to their own 48-unit
 * box; see GameplayScene's guard setup.
 */
object GuardAnimations {

    // ---- idle ---------------------------------------------------------------------------
    // 144 raw frames of a very slow sway, kept every third one: 48 frames at 95ms a step is
    // ~4.6s each way (~9.1s the round trip). Slower than the player idle's own 45ms - a
    // standing guard reads as more idle/still than a standing player is supposed to, and at
    // the player's own pace the sway read as too fidgety for someone meant to be holding
    // still at a post. The plates do NOT loop - the last frame sits a dozen kept-frame steps
    // away from the first, which would read as a small pop every couple of seconds - so the
    // animation is played out and back (ping-pong) instead. That is done in load() by listing
    // the same atlas slices twice; it costs no atlas memory, only the list.
    const val IDLE_FRAME_TIME_MS = 95
    private const val IDLE_FRAMES = 48

    // ---- walk ---------------------------------------------------------------------------
    // Raw 42-79 of a walk-in-place plate: one gait cycle, every frame, chosen as the 38-frame
    // window with the tightest wrap (see prep_guard.py). GameplayScene drives it by distance
    // travelled, like the player's walk, so the frame time here is an inert fallback and the
    // feet stay planted whatever the patrol speed.
    const val WALK_FRAMES = 38

    /**
     * Ground covered by one gait cycle as a multiple of the standing silhouette height - the
     * same units as PlayerAnimations.WALK_STRIDE_PER_HEIGHT. The plate itself measures ~0.523
     * here (the planted foot tracks backwards ~7.3px per frame, 38 frames over a 530px
     * silhouette), which would keep the feet perfectly planted against the ground - but at
     * that stride the legs read as dragging rather than walking, so this is deliberately
     * pulled down to cycle the gait faster for the same ground speed. The trade is a small,
     * intentional amount of foot-slide (the animation now "steps" a bit quicker than the
     * distance covered would strictly call for); a snappier-looking walk was worth it.
     */
    const val WALK_STRIDE_PER_HEIGHT = 0.46

    // ---- source geometry ----------------------------------------------------------------
    /**
     * Idle frames are 246 tall, walk frames 245; the sprite anchors at SOURCE_FEET_Y over this
     * height, i.e. the bottom row, and every clip has its feet there, so the one-row difference
     * needs no separate anchor - same as the player's climb/swing clips.
     */
    const val SOURCE_FRAME_HEIGHT = 246.0

    /** The ground line inside a frame: the crop pins the feet's soles to the bottom row. */
    const val SOURCE_FEET_Y = 245.76

    /**
     * Where the back (left) boot's sole rests in the idle stance. On these plates both boots are
     * flat on the ground line (a per-column scan of every kept frame puts both soles on row 245,
     * the bottom row), so unlike the player's raised-heel idle there is nothing to over-correct:
     * this sits on the ground line and the idle clip draws at the same height as the walk.
     */
    const val IDLE_FEET_Y = 245.0

    /** Height of the standing silhouette in frame pixels (rows 2..245 of frame 1). */
    const val SOURCE_SILHOUETTE_HEIGHT = 244.0

    /**
     * Was a small deliberate downward sink on top of the measured feet position, added to close
     * a reported gap between his feet and the surface. It overshot - feet sinking visibly under
     * the platform instead - so it's back to 0: the source plates measure pixel-perfect against
     * chainedcrate.png's own crop (checked directly, column by column, against the actual PNG),
     * and the feet-anchor math elsewhere in this file (SOURCE_FEET_Y, IDLE_FEET_Y) is the real,
     * measured correction. If a gap turns up again, re-verify against the art directly rather
     * than nudging this blind a second time.
     */
    const val FEET_GROUND_NUDGE = 0.0

    // Cached for the process's life for the same reason PlayerAnimations.cached exists: a scene
    // reload must not allocate a second atlas.
    @Volatile
    private var cached: GuardAnimationSet? = null

    suspend fun load(): GuardAnimationSet {
        cached?.let { return it }

        // Own atlas rather than the player's: idle's 48 frames at 149x246 plus walk's 38 at
        // 153x245 are 3.18M pixels, which one 2048x2048 page holds (13 idle frames per row
        // over 4 rows, then 13 walk frames per row over 3), and it keeps the player's pages
        // from tipping over into another 16.8MB one. Grows if it must.
        val atlas = MutableAtlas<Unit>(2048, 2048, growMethod = MutableAtlas.GrowMethod.NEW_IMAGES)

        val idleFrames = loadFrames(atlas, "idle", IDLE_FRAMES)
        // Out and back, without repeating the turnaround frames, so every step is an adjacent
        // pair and the loop has no seam.
        val pingPong = idleFrames + idleFrames.asReversed().drop(1).dropLast(1)

        val set = GuardAnimationSet(
            idle = SpriteAnimation(sprites = pingPong, defaultTimePerFrame = IDLE_FRAME_TIME_MS.milliseconds),
            walk = SpriteAnimation(sprites = loadFrames(atlas, "walk", WALK_FRAMES), defaultTimePerFrame = 33.milliseconds)
        )
        cached = set
        return set
    }

    private suspend fun loadFrames(atlas: MutableAtlas<Unit>, folder: String, frameCount: Int) =
        (1..frameCount).map { index ->
            val name = index.toString().padStart(4, '0')
            resourcesVfs["guard/$folder/$name.png"].readBitmapSlice(atlas = atlas)
        }
}
