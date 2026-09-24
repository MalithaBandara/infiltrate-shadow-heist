package game.scene

import korlibs.image.atlas.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.korge.view.*
import korlibs.time.*
import kotlin.concurrent.Volatile

class PlayerAnimationSet(
    val idle: SpriteAnimation,
    val walk: SpriteAnimation,
    val jump: SpriteAnimation,
    val crouch: SpriteAnimation,
    val crouchwalk: SpriteAnimation,
    val climb: SpriteAnimation,
    val swing: SpriteAnimation,
    val pushTransition: SpriteAnimation,
    val push: SpriteAnimation,
    val windTransition: SpriteAnimation,
    val windWalk: SpriteAnimation
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
    // every other frame is kept: 45 frames at 45ms = smooth, natural ~2.0s tactical breathing.
    const val IDLE_FRAME_TIME_MS = 45
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
    // Only raw 1-144 are ever displayed - the transition runs to 91 and the gait loop wraps at
    // 144 - so raw 145-192 are not loaded. They were costing ~7MB of atlas for frames nothing
    // could reach; see the atlas-budget note on load() below. CROUCHWALK_FRAMES is declared
    // after CROUCHWALK_LOOP_END so it can be derived from it rather than restated as a literal.

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

    /** Raw 1 through the gait loop's last frame - everything past it is unreachable, so unloaded. */
    private const val CROUCHWALK_FRAMES = CROUCHWALK_LOOP_END + 1

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
    /**
     * First frame file actually loaded, `climb/0070.png`. Raw 1-69 are windup, run-up, and the
     * wall foot plant/push-off, none of which the climb move ever displays - it starts directly
     * with the hands on the top lip/ledge and the feet hanging naturally. They are skipped at
     * load rather than packed into the atlas, so the phase boundaries listed above are in raw
     * file numbering while the loaded indices below start from zero.
     */
    private const val CLIMB_FILE_START = 70

    /** Raw 70-224 inclusive: everything the climb move actually shows. */
    private const val CLIMB_FRAMES = 224 - CLIMB_FILE_START + 1

    /** First loaded frame, i.e. raw 70 - hands already on the lip. */
    const val CLIMB_START = 0

    /** Raw 224: fully upright again, ready to hand back to idle. */
    const val CLIMB_END = CLIMB_FRAMES - 1

    // ---- swing ---------------------------------------------------------------------------
    // 200 raw frames of walk, run-up, leap, one-handed hang off an overhead hook, a full
    // back-to-front pendulum, the release, and a landing. 52 of them are loaded - raw 59-69, raw
    // 77, and raw 114-153, every frame - because a swing across a gap is a fast, committed move
    // and most of this footage is the character hanging around waiting for one. Four cuts, each
    // measured rather than guessed:
    //
    //  - Raw 1-58 are a walk building into a run. Dropped for the same reason walk's own
    //    anticipation and jump's wind-up are: the swing is entered from whatever the player was
    //    already doing, and Player drives the world travel itself. Raw 59 is a push-off stride,
    //    which is where the move has something to say. (Raw 47-56 also run the trailing leg off
    //    the left edge of the plate, so starting later keeps the crop box narrower too.)
    //  - **Raw 70-76 are the settle, and they are cut.** The character catches the hook at the
    //    top of the leap and these are his body swinging in under his own grip - eight frames of
    //    a silhouette that barely changes. Given real time they read as him stopping to wait on
    //    the hook; given almost none they are frames the display never shows. Raw 77 alone is
    //    kept as the catch pose, and it costs nothing to skip to it: the body sits within about
    //    half a world unit of where raw 69 leaves it.
    //  - **Raw 78-113 are the backswing, and they are cut too.** In the footage the character
    //    jumps straight up, so the grab is followed by the legs swinging back before they come
    //    forward. In game he has run at the hook, so his momentum should carry him forward and
    //    that wind-up reads as wrong. Removing it means raw 77 has to hand straight over to raw
    //    114, and 114 is where it does: aligning every candidate pair on the hand (which is how
    //    these frames are actually drawn - see Player.SWING_GRIP_ABOVE_CURVE) and scoring
    //    silhouette overlap, that pair moves the body by half a pixel against 17-37 for its
    //    neighbours. There is still a pose step, about twice an adjacent frame, but it lands
    //    mid-swing at speed and it buys a swing that only ever travels forward.
    //  - Raw 154-200 are a deep landing squat that stands up and runs off. Dropped because
    //    GameplayScene already owns the touchdown: the swing ends on contact and hands over to
    //    the same landing-absorb cushion every jump uses, which resolves into walk or idle. That
    //    is both a cleaner pose handover than this clip's own run-out (it never returns to a
    //    standing pose, so it cannot meet idle frame 1) and ~25 frames of atlas not spent.
    //
    // **Every frame of what is left is kept, not every second one.** These are 360x640 half-res
    // plates shot at roughly twice the standing clips' rate, so halving them - which is what the
    // first pass did - lands the clip at walk's ~35fps and looks it, because unlike walk this is
    // one continuous fast action rather than a loop the eye already knows. Kept whole, the swing
    // through to the front runs at ~80fps, which is past what a 60Hz panel shows but not what a
    // 120Hz phone does, and it costs nothing: 52 frames at 165x264 fit inside a single 2048x2048
    // atlas page (84 of these per page), so the smoother version is free until the count passes 84.
    //
    // Two places the footage runs off its own canvas were repaired before cropping, both found by
    // reading the alpha channel: the raised fist leaves the top edge on raw 66-69, and the leading
    // boot's toe leaves the right edge on raw ~122-141 (up to 40 rows of flat cut). Each was
    // capped with a half ellipse sized from the clipped run's own thickness, so a squared-off boot
    // does not read as a squared-off boot at the ~7 device pixels it occupies.
    //
    // The frames are 165x264 rather than 140x256: the hang spans further from fingertip to dangled
    // boot than any standing pose, and the swing's front extreme is wider. The pixel scale is the
    // same as every other clip, so the character is the same size on screen - see
    // SOURCE_SILHOUETTE_HEIGHT, which is measured off the standing silhouette and is what the
    // sprite is scaled by whatever frame size a clip happens to use (climb is 200x300 already).
    private const val SWING_FRAMES = 52

    /** Raw 59: the push-off stride. The move starts here, whatever the player was doing before. */
    const val SWING_START = 0

    /**
     * Raw 77, the catch. From here to [SWING_RELEASE] the hand is a fixed point and Player hangs
     * the body off it; before it, the body is interpolated up to the hook while the frames show a
     * run and a stretch upward, which is what interpolation should be covering. Pinning any later
     * than the first hanging pose is what "he stops in mid air at the hook" looks like - the fist
     * floats above the metal while a hanging silhouette is carried towards it.
     */
    const val SWING_GRAB = 11

    /**
     * Raw 131, the last frame with the fist still overhead. Past it the arm comes down and the
     * silhouette's topmost pixel stops being the hand and becomes the head, so the grip curves
     * are only measured this far - and the release has to happen here for the same reason it
     * looks right here: it is the front of the pendulum.
     */
    const val SWING_RELEASE = 29

    /** Raw 153: feet back down. GameplayScene hands over to the landing-absorb cushion here. */
    const val SWING_END = SWING_FRAMES - 1

    // ---- push ------------------------------------------------------------------------------
    // Two clips, cut by `tools/art/prep_push.py` from `push` (144 raw frames) and
    // `pushtransition` (96) in the source drop. That script's header carries the full
    // reasoning for every cut and the crop geometry - re-run and paste, don't hand-edit these.
    //
    // The plates are 360x640 half-res like crouch/crouchwalk/swing, but the character is
    // framed ~6.4% SMALLER in them (standing measures 484 rows against crouch's 517), so
    // prep_push.py scales by this clip's own 244.36/484. Both clips still come out with a
    // 245px standing silhouette, i.e. exactly idle's, which is what lets the handover in and
    // out of idle happen without the character changing size.
    //
    // Frames are 180x256 rather than idle's 140x256: the braced stance reaches from a fist
    // planted well ahead of the body to a trailing boot well behind it, and the crop stays
    // symmetric about the STANDING body centre so entering the stance from idle does not
    // shift the character sideways. The pixel scale is the same as every other clip.

    /**
     * Raw 10..96 every 2nd: upright, leaning in, stepping the trailing leg back, settling
     * into a braced stance. Played once forward on entering the stance and once in reverse
     * on leaving it, the same way the crouch clip is used.
     *
     * Raw 1-9 are a dead hold on the standing pose (frame 10 is the same pose to within
     * 0.13 of one sampled step), so they are not loaded. That means frame 0 here IS the
     * standing pose and meets idle's own first frame - measured at 211px of silhouette
     * disagreement against 7954px of silhouette, i.e. 2.7%.
     */
    private const val PUSH_TRANSITION_FRAMES = 44

    /** Fully braced, both hands planted. The pose the push loop starts from. */
    const val PUSH_TRANSITION_LAST = PUSH_TRANSITION_FRAMES - 1

    /**
     * Raw 94..133, every frame: one complete push-stride gait cycle, both steps.
     *
     * The period is 40 raw frames, from autocorrelation over the whole plate (22 is the
     * half-cycle - one step - and looping on it would make both legs the same leg). Of every
     * start position at that period, 88 has the tightest seam (0.53 of an adjacent frame) but
     * the worst entry from the braced rest pose (5.7 frames of motion); 94 trades that for a
     * 1.09 seam and a 2.82 entry, and the entry is paid once when the player starts moving
     * while the seam is crossed on every cycle.
     *
     * EVERY frame is kept while the transition next door is halved, and that is about the
     * push's slowness, not the footage. This loop is distance-driven, so the braced move speed
     * sets its display rate: 56.6 world units of cycle at ~53 u/s is 1.07 SECONDS, which at 20
     * frames would be 19fps and read as a flick-book. Whole, it is 37fps - walk's own 36. The
     * swing clip learned the same lesson from the opposite end (halving hurt there because the
     * action was fast). Redo that arithmetic rather than reusing this conclusion if
     * GameWorld.PUSH_MOVE_FACTOR changes.
     */
    private const val PUSH_FRAMES = 40
    const val PUSH_LOOP_LENGTH = PUSH_FRAMES

    /**
     * Ground covered by one push cycle, as a multiple of the character's on-screen height -
     * same units as WALK_STRIDE_PER_HEIGHT, and used the same way (GameplayScene drives the
     * loop from distance travelled so the planted foot does not slide).
     *
     * Measured on the PROCESSED frames under `resources/player/push`, not on the raw plates,
     * by sub-pixel phase correlation of the ground-contact alpha profile between consecutive
     * frames, pooled over both feet's stances (27 frame pairs): 3.629 +/- 0.039 sprite px per
     * frame, so one 40-frame cycle advances the body 57.0 world units against a 96-unit
     * character. That is 0.594 +/- 0.006, rounded here to 0.59.
     *
     * Three ways to measure this that all disagree, which is why the method above is the one
     * quoted - each of the others was tried first and is off by more than the real tolerance:
     *  - Integer bbox edges on the RAW plates over short partial stances gave 0.58. The ends
     *    of a stance are the foot rolling heel-to-toe rather than the body translating, so a
     *    short run measures the roll as much as the travel.
     *  - A least-squares fit of one foot's contact centroid gave 0.61, and the OTHER foot's
     *    gave 0.58 on the same frames. A centroid moves with the patch's shape, and the two
     *    boots are not the same shape, so neither number is the treadmill rate on its own.
     *  - The character ACCELERATES through the raw clip: the same measurement over its
     *    opening cycle gives 2.8 px/frame against the late cycles' 7.4, because the footage
     *    starts with him leaning into a load that is not moving yet. A number taken from the
     *    wrong end of the plate is off by more than 2x.
     *
     * Checked in the running game as well, though only coarsely: with the camera locked to
     * the player, a planted foot has to slide backwards across the screen at exactly the
     * player's own speed, and a screenshot burst puts it within the +/-5% that method can
     * resolve. It cannot separate 0.58 from 0.61 - the frame measurement above is what does.
     */
    const val PUSH_STRIDE_PER_HEIGHT = 0.59

    /**
     * Raw 10..96 every 3rd: upright, leaning into the gale, planting the feet, settling into a
     * braced forward lean. Played once forward on entering a fan's wind zone and once in reverse
     * on leaving it - the crouch clip's own arrangement, off GameWorld.windStanceBlend.
     *
     * These plates are framed EXACTLY as the push plates are: a 484-row standing silhouette
     * centred on raw column 173.0, the same pair prep_push.py measured. So they share push's
     * scale and body centre, and frame 0 here IS the standing pose - 29 frames after raw 1-9,
     * which are an ease-in so slow it is a hold (0.29 of one mean step over nine frames). That
     * makes the handover in and out of idle free, the same as push's.
     *
     * Step 3 rather than push's step 2: the blend runs over GameWorld.WIND_STANCE_ENTER_SECONDS
     * (0.5s), so 29 frames is already ~58fps of display and step 2 would buy 15 more frames of
     * atlas for nothing. All the reasoning is in tools/art/prep_wind.py - re-run and paste.
     */
    private const val WIND_TRANSITION_FRAMES = 29

    /** The settled lean. The pose the wind-walk loop starts from. */
    const val WIND_TRANSITION_LAST = WIND_TRANSITION_FRAMES - 1

    /**
     * Raw 55..104 every 2nd: one complete wind-stride gait cycle, both steps.
     *
     * The period is 50 raw frames (self-similarity over the whole plate; 26 is the half-cycle,
     * and looping on it would make both legs the same leg). Start 55 was picked over the
     * tightest-seam candidates at 66/67 because those enter from the braced lean with a 6.2-step
     * pose jump against 55's 2.18, and over start 1 - which has the best entry of all, being
     * literally the frame after the transition ends - because start 1's seam is 2.55 and raw
     * frame 6 runs off the left edge of the plate.
     *
     * HALVED where push keeps every frame, and push's own note says to redo that arithmetic
     * rather than reuse the conclusion, so: this loop is distance-driven from the player's net
     * ground speed and one cycle covers 32 world units. A human 4 Hz tap nets ~40 u/s through a
     * fan zone, so a cycle is ~0.8s - 31fps at 25 frames, near walk's own 36, against 62fps at
     * 50, which is past the panel.
     */
    private const val WIND_WALK_FRAMES = 25
    const val WIND_WALK_LOOP_LENGTH = WIND_WALK_FRAMES

    /**
     * Ground covered by one wind-stride cycle, as a multiple of the character's on-screen
     * height - same units as WALK_STRIDE_PER_HEIGHT and used the same way.
     *
     * Measured by tools/art/prep_wind.py at the plate's own full rate over exactly one period:
     * the per-frame contact-profile shift sums to 81.6 sprite px, i.e. 32.1 world units on a
     * 96-unit character. At the plate's 24fps that cycle takes 2.08s, so the actor is straining
     * along at 15.4 u/s - a third of the speed the player moves, which is why the loop displays
     * at about 3x the rate it was shot at.
     *
     * ONE DIFFERENCE FROM WALK AND PUSH, and it changes how much this number has to be trusted:
     * those drive their loops from ground distance so a planted foot does NOT slide, and being
     * wrong shows up at once as skating. Here the feet are MEANT to slide - the gale drags the
     * character backwards while he strides forward - so this is a cadence knob rather than a
     * foot-planting constraint. The three estimators in prep_wind.py bracket 0.31..0.39 and
     * anywhere in there reads fine; it is tuned on screen, not to the third decimal.
     */
    const val WIND_STRIDE_PER_HEIGHT = 0.33

    // ---- source geometry ----------------------------------------------------------------
    /** Frames are 256 tall. */
    const val SOURCE_FRAME_HEIGHT = 256.0

    /** Where the ground line sits inside a frame: the crop pins it to the bottom edge. */
    const val SOURCE_FEET_Y = 255.76

    /**
     * Where the higher (back) leg rests in idle stance, so both feet connect with the ground.
     * A per-column alpha scan across all 45 idle frames (`resources/player/idle/0001..0045.png`,
     * identical in every one - this is a static two-footed stance, not something that shifts
     * during the breathing loop) puts the front foot's sole at row 255 (on `SOURCE_FEET_Y`) and
     * the back foot's at row 248, a stable 7px short of it. The value here is set a further few
     * rows below that measured 248, not exactly on it: on a real device report the back foot still
     * read as floating at the exact measured value, so this is deliberately over-corrected the
     * same direction as the fix rather than tuned to the pixel - preferred over leaving either
     * foot visibly short of the ground, per this file's own standing rule (see `CROUCH_FEET_Y`).
     */
    const val IDLE_FEET_Y = 245.0

    /**
     * Where the planted foot rests during walk strides on elevated/contoured surfaces like the
     * truck so the soles connect firmly with the surface without floating above it. Matches
     * IDLE_FEET_Y. On flat floors and platforms, GameplayScene grounds the sprite flush with the
     * surface (offset 0.0) so shoes do not sink underground.
     */
    const val WALK_FEET_Y = 245.0

    /**
     * Where the higher (back) leg rests in the held crouch pose - a per-column scan of
     * `resources/player/crouch/0034.png` (the held frame, `CROUCH_LAST`) puts the front foot's
     * lowest row at 255 (on `SOURCE_FEET_Y`, same as every other clip) and the back foot's at
     * ~250, a stable ~5-6px short of it - the settling crouch plants weight forward with the back
     * heel raised, not a flat two-footed squat. Same fix as `IDLE_FEET_Y`: shift the whole sprite
     * down by (SOURCE_FEET_Y - CROUCH_FEET_Y) so the back foot reaches the ground line too, which
     * necessarily pushes the front foot a few pixels *below* it - preferred over leaving the back
     * foot visibly floating.
     */
    const val CROUCH_FEET_Y = 250.0

    /**
     * Same phenomenon as `IDLE_FEET_Y`, measured across the jump clip's landing-absorb frames
     * (`resources/player/jump/0028.png` through `0044.png`, i.e. `JUMP_LAND_START`..`JUMP_LAND_END`):
     * the back foot's lowest row sits at a strikingly consistent ~247-248 across all seventeen
     * frames (a per-column scan of several of them, not just one, confirms it isn't a one-frame
     * fluke) while the front foot reaches the true `SOURCE_FEET_Y` line - the exact same
     * back-heel-raised stance as idle and crouch, just held throughout the whole absorb-to-
     * standing recovery instead of one frame.
     */
    const val JUMP_LAND_FEET_Y = 247.0

    /** Height of the standing silhouette in frame pixels, used to scale to the hitbox. */
    const val SOURCE_SILHOUETTE_HEIGHT = 244.36

    // The real cause of the "grey screen, nothing loads" bug chased for most of this session -
    // confirmed on-device, not guessed: a java.lang.OutOfMemoryError inside
    // MutableAtlas.growAtlas -> Bitmap32.<init>, thrown from exactly the call this function
    // makes. load() allocated a brand new 2048x2048 GPU texture atlas and re-decoded the entire
    // player spritesheet into it on *every single call* - i.e. on every scene (re)load, not just
    // the first, since GameplayScene.kt's sceneMain() calls PlayerAnimations.load() fresh each
    // time. Nothing released the previous scene's atlas before the next one was allocated, so
    // repeated reloads (RESTART, QUIT, watch-ad-to-continue) accumulated texture memory until an
    // allocation eventually failed - explaining both why it happened specifically on repeat loads
    // and why the audio-focused fixes earlier in this session's history never touched it, since
    // they were a different subsystem entirely.
    //
    // The frames themselves are static content with no per-instance state - the same character,
    // drawn the same way, in every level and every replay - so there is no reason a second
    // GameplayScene needs its own copy at all. Caching the loaded set here and returning it on
    // every call after the first removes the repeated allocation at its root, the same fix
    // already applied to GameAudio's one-time audio priming for the same class of bug.
    @Volatile
    private var cached: PlayerAnimationSet? = null

    suspend fun load(): PlayerAnimationSet {
        cached?.let { return it }

        // Pack every frame into a shared atlas. Read individually they become one GPU texture
        // each, which forces a rebind on every animation frame and shows up as stutter; packed,
        // an animation sits on one page.
        //
        // ATLAS BUDGET - this is the single biggest memory consumer in the game, so keep an eye
        // on it. GrowMethod.NEW_IMAGES adds a whole 2048x2048 page (16.8MB as a Bitmap32 on the
        // heap, and again as a GPU texture) each time the current one fills, so cost goes up in
        // 16.8MB steps, not smoothly. The clips below total 26.4M pixels - at least 7 pages, more
        // with packing waste. Before the unreachable climb run-up (raw 1-69) and crouchwalk tail
        // (raw 145-192) were dropped it was 26.2M for a strictly smaller set of clips, i.e. ~34MB
        // of heap and ~34MB of texture memory spent on frames nothing could ever display.
        //
        // Adding frames here is not free. Climb is 155 frames at 200x300 (9.3M px, over two pages
        // on its own); the two push clips are 84 frames at 180x256 (3.9M px), and they are what
        // took the total from 22.6M to 26.4M - roughly one more page of heap and one more of
        // texture. Both push clips are already cut to the minimum that reads correctly and the
        // reasoning for every cut is in tools/art/prep_push.py, so a future saving has to come
        // from somewhere else rather than from trimming them again.
        //
        // The two wind clips (54 frames at 192x256 - wider than push's 180 because the wind
        // walk's leading fist would otherwise sit on the frame edge; see prep_wind.py) add
        // 2.65M px on top, taking the total to 29.1M. They are already the tightest cut the
        // footage allows: the transition is sampled at step 3 and the gait loop is halved,
        // both justified frame by frame in prep_wind.py.
        val atlas = MutableAtlas<Unit>(2048, 2048, growMethod = MutableAtlas.GrowMethod.NEW_IMAGES)

        // Only idle runs on its own timer. GameplayScene drives walk frame-by-frame from distance
        // travelled, jump from the physics arc, crouch from the stance transition, and climb from
        // the climb move's own timer, so those frame times are inert fallbacks.
        val set = PlayerAnimationSet(
            idle = loadAnimation(atlas, "idle", IDLE_FRAMES, frameTimeMs = IDLE_FRAME_TIME_MS),
            walk = loadAnimation(atlas, "walk", WALK_FRAMES, frameTimeMs = 40),
            jump = loadAnimation(atlas, "jump", JUMP_FRAMES, frameTimeMs = 33),
            crouch = loadAnimation(atlas, "crouch", CROUCH_FRAMES, frameTimeMs = 33),
            crouchwalk = loadAnimation(atlas, "crouchwalk", CROUCHWALK_FRAMES, frameTimeMs = 40),
            climb = loadAnimation(atlas, "climb", CLIMB_FRAMES, frameTimeMs = 33, firstFile = CLIMB_FILE_START),
            swing = loadAnimation(atlas, "swing", SWING_FRAMES, frameTimeMs = 33),
            pushTransition = loadAnimation(atlas, "pushtransition", PUSH_TRANSITION_FRAMES, frameTimeMs = 33),
            push = loadAnimation(atlas, "push", PUSH_FRAMES, frameTimeMs = 40),
            windTransition = loadAnimation(atlas, "windtransition", WIND_TRANSITION_FRAMES, frameTimeMs = 33),
            windWalk = loadAnimation(atlas, "windwalk", WIND_WALK_FRAMES, frameTimeMs = 40)
        )
        cached = set
        return set
    }

    /**
     * Loads [frameCount] frames starting at file `<firstFile>.png`. [firstFile] exists so a clip
     * whose opening frames are never displayed (climb's run-up) can skip them entirely rather
     * than pack them into the atlas - the returned animation is indexed from 0 either way, so
     * the clip's own START/END constants are in loaded-index space, not raw file numbering.
     */
    private suspend fun loadAnimation(
        atlas: MutableAtlas<Unit>,
        folder: String,
        frameCount: Int,
        frameTimeMs: Int,
        firstFile: Int = 1
    ): SpriteAnimation {
        val frames = (firstFile until firstFile + frameCount).map { index ->
            val name = index.toString().padStart(4, '0')
            resourcesVfs["player/$folder/$name.png"].readBitmapSlice(atlas = atlas)
        }
        return SpriteAnimation(sprites = frames, defaultTimePerFrame = frameTimeMs.milliseconds)
    }
}
