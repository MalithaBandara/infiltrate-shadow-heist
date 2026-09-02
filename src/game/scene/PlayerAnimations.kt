package game.scene

import korlibs.image.atlas.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.view.*
import korlibs.time.*

class PlayerAnimationSet(
    val idle: SpriteAnimation,
    val walk: SpriteAnimation,
    val jump: SpriteAnimation,
    val crouch: SpriteAnimation,
    val crouchwalk: SpriteAnimation,
    val climb: SpriteAnimation
)

/**
 * Loads the player sprite sheets and describes what each stretch of frames means.
 *
 * The files under `resources/player/<clip>` are a processed version of the raw 720x1280 plates
 * kept in `art-source/player`: cropped to a shared 592x1080 box (symmetric about the character
 * centre so horizontal flipping does not shift them), the feet pinned to the bottom edge, and
 * scaled to 140x256. The frame indices below were measured off the silhouettes rather than
 * guessed - see the notes on each clip.
 *
 * "Feet pinned" means each frame's own lowest opaque row, not one ground line measured once and
 * reused. The distinction only shows up on crouch and crouchwalk, whose plates are half-resolution
 * 360x640 (so the same box is 296x540 there) and whose rig lifts the character ~22 full-res units
 * off the standing ground line the moment it squats: cut against a fixed line, the crouch hovered
 * ~4px clear of the floor and the crouch-walk bobbed between 4px and 9px of clearance. Every other
 * clip already had its feet on the bottom row, which is why only these two were wrong.
 */
object PlayerAnimations {

    // ---- idle ---------------------------------------------------------------------------
    // The raw plate holds one breathing cycle over 90 frames (frame 91 lands back on frame 1 to
    // within a fifth of a single frame step, so it loops cleanly). Motion per frame is tiny, so
    // every other frame is kept: 45 frames at 100ms = a 4.5s breath, matching the old pacing.
    private const val IDLE_FRAMES = 45

    // ---- walk ---------------------------------------------------------------------------
    // Raw frames 1-8 are a weight-shift anticipation that only reads correctly if the character
    // accelerates from rest; Player snaps straight to full speed, so those are dropped - keeping
    // them would slide the feet. What is kept is raw 9-48, renumbered from 0:
    private const val WALK_FRAMES = 40

    /** Raw 9-26: leaning in and building to a full stride. Played once when walking starts. */
    const val WALK_TRANSITION_START = 0
    const val WALK_TRANSITION_END = 17

    /**
     * Raw 27-48: one complete gait cycle (both steps). Autocorrelation over the plate puts the
     * period at 22 frames, and this window has the tightest seam of any - frame 49 differs from
     * frame 27 by about a quarter of one frame step, so the wrap is invisible.
     */
    const val WALK_LOOP_START = 18
    const val WALK_LOOP_END = WALK_FRAMES - 1
    const val WALK_LOOP_LENGTH = WALK_LOOP_END - WALK_LOOP_START + 1

    /**
     * Ground covered by one gait cycle, as a multiple of the character's on-screen height.
     * Measured from the plate: during stance the planted foot tracks backwards at ~38.75px per
     * frame, so 22 frames advance the body ~853px against a 1031px-tall character. Driving the
     * loop by distance travelled at exactly this rate keeps the feet planted.
     */
    const val WALK_STRIDE_PER_HEIGHT = 0.83

    // ---- jump ---------------------------------------------------------------------------
    // Raw 1-11 are a crouching wind-up. Player gets its upward velocity in a single step with no
    // wind-up (the movement test pins that), so the jump starts at the push-off. Kept: raw 12-55.
    private const val JUMP_FRAMES = 44

    /** Raw 12-14: body extending, feet still down. Very short - physics is already lifting. */
    const val JUMP_LAUNCH_START = 0

    /** Raw 15: first frame with the feet clear of the ground. */
    const val JUMP_RISE_START = 3

    /** Raw 25: highest point, legs tucked. */
    const val JUMP_APEX = 13

    /** Raw 38: feet back down. */
    const val JUMP_TOUCHDOWN = 26

    /** Raw 39-55: absorbing the landing and standing back up. Frame 55 matches idle frame 1. */
    const val JUMP_LAND_START = 27
    const val JUMP_LAND_END = JUMP_FRAMES - 1

    // ---- crouch ---------------------------------------------------------------------------
    // The raw plate is 96 frames of standing lowering into a crouch. A feet-aligned scan of the
    // silhouette puts the descent at raw 1-33 (head top travels 11 -> 117 in frame rows, strictly
    // monotonic) and finds nothing after it: raw 34-96 hold the same pose and drift within a few
    // rows without ever returning to a seam, so there is no crouched idle to loop. Those 62 frames
    // are dropped the way walk's anticipation and jump's wind-up were, leaving raw 1-34.
    //
    // Raw frame 1 is the standing pose and matches idle frame 1 to within a pixel of bounding box,
    // so entering the crouch from idle does not pop; raw 34 matches crouchwalk's frame 1 to within
    // one frame step of motion, so the crouch -> crouch-walk handoff does not either.
    //
    // Played once on entering crouch, held on the last frame while crouched, and played in reverse
    // when standing back up (see GameplayScene).
    private const val CROUCH_FRAMES = 34
    const val CROUCH_LAST = CROUCH_FRAMES - 1

    // ---- crouchwalk -----------------------------------------------------------------------
    // 192 raw frames, shot at twice the frame rate of the standing clips (they are 360x1280-scale
    // plates at half resolution, so a cycle here spans about twice as many frames as walk's).
    private const val CROUCHWALK_FRAMES = 192

    /**
     * Raw 1-91: leaning out of the settled crouch and building to a full stride. Raw frame 1 is
     * the crouch clip's held pose, so this starts exactly where the crouch leaves off. Driven by
     * distance travelled, not by a fixed duration - scrubbing 91 frames through a fixed 0.4s is
     * both a blur and a foot-slide, since the footage is already walking from frame 1.
     */
    const val CROUCHWALK_TRANSITION_START = 0
    const val CROUCHWALK_TRANSITION_END = 90

    /**
     * Raw 92-144: one complete gait cycle. Autocorrelation over the plate puts the period at 53
     * frames, and of every (start, period) pair in the clip this window has the tightest seam:
     * frame 145 differs from frame 92 by about two thirds of one frame step, so the wrap does not
     * read. The window also runs on from the transition's last frame, so that handover is a plain
     * adjacent-frame step.
     */
    const val CROUCHWALK_LOOP_START = 91
    const val CROUCHWALK_LOOP_END = 143
    const val CROUCHWALK_LOOP_LENGTH = CROUCHWALK_LOOP_END - CROUCHWALK_LOOP_START + 1

    /**
     * Ground covered by one crouch gait cycle, as a multiple of the character's on-screen height -
     * same units as WALK_STRIDE_PER_HEIGHT, i.e. the STANDING silhouette, since the sprite is
     * scaled off that whatever the stance. Measured the same way: the planted foot tracks backwards
     * at 2.37 frame-px per frame across its stance phases (2.21 / 2.56 / 2.33 on the three clean
     * ones), so 53 frames advance the body ~125px against a 244px-tall character. The old 0.65 was
     * an unmeasured guess and ran the cycle ~30% too slow, which slid the feet forward.
     */
    const val CROUCHWALK_STRIDE_PER_HEIGHT = 0.51

    // ---- climb ---------------------------------------------------------------------------
    // Raw 1-224 (225 is a stray blank frame, dropped): windup, run-up, leap, ledge grab, mantle,
    // then standing up on top.
    //
    // Unlike the other clips this one's camera re-frames mid-shot: a per-frame scan of the
    // silhouette puts the feet anywhere between row 384 and row 588 with no stable ground or
    // ledge line, and the standing silhouette is 13% taller at the end than at the start. So the
    // footage's own pixel positions cannot be trusted to place the character. The processed
    // frames instead pin every frame's feet to a constant row, and Player drives the actual
    // world-space rise (see Player.advanceClimb) - which also means one clip serves boxes of any
    // climbable height, not just one that happens to match the footage.
    //
    // Two more corrections are baked into these frames, both needed because the camera moves:
    //  - Scale: the 13% growth is cancelled by a ramp over frames 93-205, so the character holds
    //    one size and its last frame is exactly as tall as idle's - otherwise the handoff back to
    //    idle pops.
    //  - Contact: the wall-phase frames are nudged so the character's leading edge reaches the
    //    player's own collision edge, i.e. the face of the box it is climbing. Left as shot it
    //    stands ~6 units clear of the box and looks stuck to nothing. Frames past the lip blend
    //    back to idle's centring. Overlapping into the box is harmless (both are black
    //    silhouettes); a gap is not. This is why the frames are 200 wide when idle's are 140.
    //
    // Phase boundaries below were read off that same scan (feet row + silhouette height per
    // frame) and are what Player's climb curves are tuned against, so the two must move together:
    //   44-52   foot plants on the face, hands going up
    //   53-69   push off and catch the lip
    //   70-99   hanging off the lip. Measured on the plate, the fingertips sit ~96 units above
    //           the character's own feet here, so on a 100-unit box the hands are level with the
    //           top edge while the feet are still down at the ground - the climb must NOT lift
    //           the body during this stretch or the character reads as levitating.
    //   100-144 the actual pull-up, where all the height is gained
    //   145-175 settled crouching on top
    //   176-224 standing up
    private const val CLIMB_FRAMES = 224

    /**
     * Raw 44. Frames 1-43 are standing time plus a run-up stride, and the stride is the problem:
     * in game the player is already flush against the box when they press jump, so those frames
     * play a full running gait against a wall the character cannot move through - it reads as
     * running on the spot. Starting at the foot plant skips it and makes the move responsive.
     */
    const val CLIMB_START = 43

    /** Raw 224: fully upright again, ready to hand back to idle. */
    const val CLIMB_END = CLIMB_FRAMES - 1

    // ---- source geometry ----------------------------------------------------------------
    /** Frames are 256 tall. */
    const val SOURCE_FRAME_HEIGHT = 256.0

    /** Where the ground line sits inside a frame: the crop pins it to the bottom edge. */
    const val SOURCE_FEET_Y = 255.76

    /** Where the higher leg rests in idle stance (row 247.0), so both feet connect with the ground. */
    const val IDLE_FEET_Y = 247.0

    /** Height of the standing silhouette in frame pixels, used to scale to the hitbox. */
    const val SOURCE_SILHOUETTE_HEIGHT = 244.36

    suspend fun load(): PlayerAnimationSet {
        // Pack every frame into a shared atlas. Read individually they become one GPU texture
        // each, which forces a rebind on every animation frame and shows up as stutter; packed,
        // an animation sits on one page.
        val atlas = MutableAtlas<Unit>(2048, 2048, growMethod = MutableAtlas.GrowMethod.NEW_IMAGES)

        // Only idle runs on its own timer. GameplayScene drives walk frame-by-frame from distance
        // travelled, jump from the physics arc, crouch from the stance transition, and climb from
        // the climb move's own timer, so those frame times are inert fallbacks.
        return PlayerAnimationSet(
            idle = loadAnimation(atlas, "idle", IDLE_FRAMES, frameTimeMs = 100),
            walk = loadAnimation(atlas, "walk", WALK_FRAMES, frameTimeMs = 40),
            jump = loadAnimation(atlas, "jump", JUMP_FRAMES, frameTimeMs = 33),
            crouch = loadAnimation(atlas, "crouch", CROUCH_FRAMES, frameTimeMs = 33),
            crouchwalk = loadAnimation(atlas, "crouchwalk", CROUCHWALK_FRAMES, frameTimeMs = 40),
            climb = loadAnimation(atlas, "climb", CLIMB_FRAMES, frameTimeMs = 33)
        )
    }

    private suspend fun loadAnimation(
        atlas: MutableAtlas<Unit>,
        folder: String,
        frameCount: Int,
        frameTimeMs: Int
    ): SpriteAnimation {
        val frames = (1..frameCount).map { index ->
            val name = index.toString().padStart(4, '0')
            resourcesVfs["player/$folder/$name.png"].readBitmapSlice(atlas = atlas)
        }
        return SpriteAnimation(sprites = frames, defaultTimePerFrame = frameTimeMs.milliseconds)
    }
}
