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
 * `tools/art/prep_guard.py`: one shared crop box per clip, symmetric about the character so a
 * scaleX flip does not shift him, feet pinned to the bottom row, scaled so the standing
 * silhouette is ~244 frame pixels - the same on-screen density as the player's frames. The
 * constants below are printed by that script from the frames it wrote; re-run it and paste
 * rather than editing them by hand. The script's header carries the per-clip reasoning (which
 * raw frames, why, and how the stride was measured).
 *
 * Guards on levels without a sprite-sized hitbox still draw this scaled to their own 48-unit
 * box; see GameplayScene's guard setup.
 */
object GuardAnimations {

    // ---- idle ---------------------------------------------------------------------------
    // 144 raw frames of a very slow sway, kept every other one: 72 frames at the player idle's
    // 45ms step is ~3.2s each way. The plates do NOT loop - the last frame sits ~30 adjacent-frame
    // steps away from the first (chest edge drifts 3-4 plate pixels over the clip), which would
    // read as a small pop every few seconds - so the animation is played out and back
    // (ping-pong) instead. That is done in load() by listing the same atlas slices twice; it
    // costs no atlas memory, only the list.
    const val IDLE_FRAME_TIME_MS = 45
    private const val IDLE_FRAMES = 72

    // ---- walk ---------------------------------------------------------------------------
    // Raw 66-105 of a walk-in-place plate: one gait cycle, every frame, chosen as the 40-frame
    // window with the tightest wrap (see prep_guard.py). GameplayScene drives it by distance
    // travelled, like the player's walk, so the frame time here is an inert fallback and the
    // feet stay planted whatever the patrol speed.
    const val WALK_FRAMES = 40

    /**
     * Ground covered by one gait cycle as a multiple of the standing silhouette height - the
     * same units as PlayerAnimations.WALK_STRIDE_PER_HEIGHT. Measured on the plate: the planted
     * foot tracks backwards ~7.5px per frame, so 40 frames cover ~300px against a 542px
     * silhouette. Shorter than the player's 0.83 - this is a stroll, not a march.
     */
    const val WALK_STRIDE_PER_HEIGHT = 0.554

    // ---- source geometry ----------------------------------------------------------------
    /**
     * Idle frames are 246 tall, walk frames 256; the sprite anchors at SOURCE_FEET_Y over this
     * height, i.e. the bottom row, and every clip has its feet there, so the taller walk frames
     * (the stride's reach) need no separate anchor - same as the player's climb/swing clips.
     */
    const val SOURCE_FRAME_HEIGHT = 246.0

    /** The ground line inside a frame: the crop pins the front foot's sole to the bottom row. */
    const val SOURCE_FEET_Y = 245.76

    /**
     * Where the back (left) boot's sole rests in this stance - a per-column scan of frame 1 puts
     * the front boot on row 245 and the back boot's sole on rows 240-242, the same raised-heel
     * stance the player's idle has. Set a row past the measured value rather than on it, per the
     * rule PlayerAnimations.IDLE_FEET_Y records: a back foot a hair short of the ground reads as
     * floating on a real phone, a front foot sunk a couple of units into dark ground does not.
     */
    const val IDLE_FEET_Y = 241.0

    /** Height of the standing silhouette in frame pixels (rows 1..245 of frame 1). */
    const val SOURCE_SILHOUETTE_HEIGHT = 245.0

    // Cached for the process's life for the same reason PlayerAnimations.cached exists: a scene
    // reload must not allocate a second atlas.
    @Volatile
    private var cached: GuardAnimationSet? = null

    suspend fun load(): GuardAnimationSet {
        cached?.let { return it }

        // Own atlas rather than the player's: idle's 72 frames at 75x246 plus walk's 40 at
        // 120x256 are 2.56M pixels, which one 2048x2048 page holds with room to spare, and it
        // keeps the player's pages from tipping over into another 16.8MB one. Grows if it must.
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
