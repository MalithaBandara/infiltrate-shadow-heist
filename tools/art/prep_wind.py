"""
Cut the wind-stance clips from the raw plates and print the constants PlayerAnimations needs.

Usage:  python tools/art/prep_wind.py            (writes resources/player/wind{transition,walk})
        python tools/art/prep_wind.py --measure  (print the measurements only, write nothing)

Run from the repo root. Same recipe as prep_guard.py / prep_push.py: everything below is
measured off the plates, so re-run and paste the printed block rather than hand-editing
the Kotlin.

SOURCE: C:\\Users\\USER\\Downloads\\charAnimations\\windtransition (96 frames) and
\\windwalk (144 frames), both 360x640 half-resolution plates, 24fps.

  The windwalk FOLDER ARRIVED EMPTY - only windwalk.mp4 was there. The frames were
  extracted with the same rule the shipped windtransition plates were made by, which was
  reverse-engineered from them rather than assumed: the video is a black silhouette on a
  white ground, and the plate is `alpha = 255 - luma, rgb = 0`. Reapplying that rule to
  windtransition.mp4 reproduces the shipped windtransition plates to a mean alpha error of
  2.15/255, which is h.264 noise. To redo it:
      ffmpeg -v error -i windwalk.mp4 -fps_mode passthrough out/%04d.png
      then alpha = 255 - rgb.mean(axis=2), rgb = 0

THE FRAMING IS PUSH'S, EXACTLY. windtransition frame 1 measures bbox x 119..227 (centre
173.0), y 61..544 - a 484-row standing silhouette centred on raw column 173.0, which is
the same pair of numbers prep_push.py measured on its own plates. So these clips take
push's scale (244.36 / 484) and body centre unchanged, and frame 0 of the transition IS
the standing pose, which makes the handover in and out of idle free. Do not re-derive
this from windwalk - the walk clip never contains a standing pose.

WHICH FRAMES ARE KEPT, and why (every cut measured, not chosen):

  windtransition, raw 10..96 every 3rd (29 frames) - upright, leaning into the gale,
  planting the feet, settling into a braced forward lean.
    * Raw 1-9 are an ease-in so slow it is a hold: frame 10 differs from frame 1 by 0.29
      of one mean adjacent step, against a clip that travels 26.0 steps in total. Nine
      frames of atlas buying a third of one step.
    * Step 3 rather than push's step 2. The blend runs over WIND_STANCE_ENTER_SECONDS
      (0.5s), so 29 frames is already 58fps of display rate - past what the device can
      show. Step 2 would cost 15 more frames for nothing. Consecutive kept frames are
      0.9 of a mean adjacent step apart, tighter than the push loop's own 1.09 seam.
    * Raw 96 is the settled lean and is where the walk loop's footage lives: transition
      frame 96 against windwalk frame 1 is 0.76 of a windwalk step, so the two clips are
      genuinely continuous footage.

  windwalk, raw 55..104 every 2nd (25 frames) - one complete wind-stride gait cycle.
    * The period is 50 raw frames, from self-similarity over the whole plate
      (mean |f(t) - f(t+p)| minimised at p=50, 2.26 steps; 26 is the half-cycle - one
      step - and looping on it would make both legs the same leg).
    * Of every start at that period, 66/67 have the tightest seam (0.62) but a terrible
      entry from the braced lean (6.2 steps). Start 55 is the pick: seam 0.92 - the best
      of everything with a usable entry - entry 2.18, and no plate-edge clipping anywhere
      in the window. Start 1 has the best entry of all (0.76, it is literally the frame
      after the transition ends) but a 2.55 seam AND raw frame 6 runs 2 rows off the left
      edge of the plate, so it is out.
    * HALVED, unlike push's loop. Push's own note says to redo this arithmetic rather
      than reuse its conclusion, so: the loop is distance-driven from the player's NET
      ground speed and one cycle covers 32 world units (see measure_stride). Tapping at a
      human 4 Hz nets ~45 u/s through a fan zone (GameWorld.FAN_TAP_*), so a cycle runs
      ~0.71s - 35fps at 25 frames, which is walk's own 36, against 71fps at 50, which is
      past the panel and past every other clip in this game.
    * For scale: the plate itself is far slower than the game. 32 units per 50 frames at
      24fps is 15.4 u/s, a third of the player's net speed, so the loop displays at about
      3x the rate it was shot at. That reads correctly because the struggle in this clip
      lives in the pose, not the tempo - but it is also why the transition next door is
      sampled at step 3 and this is not: sampling a clip you are already speeding up 3x
      is where a gait starts to strobe.
    * Raw frame 6 is the ONLY edge-clipped frame in the whole plate (2 rows) and it is
      nowhere near this window. The script fails loudly if that ever stops being true.

GEOMETRY: one shared crop box per clip, symmetric about the character's STANDING body
centre (raw column 173.0), exactly as push does it, so entering the stance from idle does
not shift the character sideways by a pixel.

FRAMES ARE 192 WIDE, NOT PUSH'S 180, and that is forced rather than stylistic. The wind
walk throws the leading arm much further forward than the push stance does: the loop
window reaches raw x = 351 against push's crop box right edge of 351.26, i.e. a quarter
of one raw pixel of margin - the leading fist's antialiased edge would sit exactly on the
frame boundary. 192 puts the box at raw -17.1..363.1, leaving ~6 sprite px of clearance,
the same margin push leaves on its own reach. Width is free to differ per clip (climb is
200 wide, push 180): the sprite is anchored at the frame's horizontal centre and the crop
is symmetric about the body centre, so the character lands in the same place either way.

Feet are pinned per frame - each frame's own lowest opaque row goes to the bottom edge,
the same rule crouch/crouchwalk/push use. The lowest row wanders 540..548 across the walk
plate as the swing foot lifts; pinning kills that ~1.2-world-unit bob.
"""
import os, sys
import numpy as np
from PIL import Image

RAW = r"C:\Users\USER\Downloads\charAnimations"
OUT = os.path.join("resources", "player")

# Measured on the plates - see the module docstring. Identical to prep_push.py's pair.
STANDING_SILHOUETTE_RAW = 484.0   # windtransition/0001.png, rows 61..544
TARGET_SILHOUETTE = 244.36        # PlayerAnimations.SOURCE_SILHOUETTE_HEIGHT
BODY_CENTRE_RAW = 173.0           # bbox centre of the standing pose
OUT_W, OUT_H = 192, 256

SCALE = TARGET_SILHOUETTE / STANDING_SILHOUETTE_RAW
CROP_W = OUT_W / SCALE
CROP_H = OUT_H / SCALE

# (source folder, output folder, first, last, step) in raw 1-based file numbering.
CLIPS = [
    ("windtransition", "windtransition", 10, 96, 3),
    ("windwalk", "windwalk", 55, 104, 2),
]


def alpha(path):
    return np.asarray(Image.open(path).convert("RGBA"))[:, :, 3]


def bbox(a, thr=8):
    ys, xs = np.nonzero(a > thr)
    return xs.min(), xs.max(), ys.min(), ys.max()


def cut(src_path, dst_path, write):
    """Crop the shared box out of one plate and resample it to OUT_W x OUT_H.

    Premultiply -> resize -> unpremultiply, same reasoning as tools/art/pot_resize.py and
    prep_push.py: straight RGBA downsampling averages the RGB sitting under fully
    transparent pixels into every edge pixel, which on black-on-transparent silhouette art
    shows up as a grey fringe. The crop box is fractional, so it goes to resize() as a
    float `box` rather than being rounded first.
    """
    im = Image.open(src_path).convert("RGBA")
    a = np.asarray(im)[:, :, 3]
    _, _, _, y1 = bbox(a)
    # Feet pinned: the bottom edge of the lowest opaque row becomes the frame's bottom
    # edge, so the ground line lands on PlayerAnimations.SOURCE_FEET_Y.
    bottom = y1 + 1.0
    left = BODY_CENTRE_RAW - CROP_W / 2.0

    # The box runs ~17 raw px off the left edge of the plate; pad so that becomes
    # transparent margin instead of being clamped back inside the art.
    pad = 24
    padded = Image.new("RGBA", (im.width + 2 * pad, im.height + 2 * pad), (0, 0, 0, 0))
    padded.paste(im, (pad, pad))

    f = np.asarray(padded).astype(np.float64) / 255.0
    al = f[..., 3:4]
    pm = Image.fromarray(
        (np.concatenate([f[..., :3] * al, al], axis=-1) * 255.0 + 0.5).astype(np.uint8), "RGBA"
    )
    box = (left + pad, bottom - CROP_H + pad, left + CROP_W + pad, bottom + pad)
    small = pm.resize((OUT_W, OUT_H), Image.LANCZOS, box=box)

    b = np.asarray(small).astype(np.float64) / 255.0
    oa = b[..., 3:4]
    rgb = np.clip(np.divide(b[..., :3], oa, out=np.zeros_like(b[..., :3]), where=(oa > 1e-6)), 0.0, 1.0)
    out = Image.fromarray((np.concatenate([rgb, oa], axis=-1) * 255.0 + 0.5).astype(np.uint8), "RGBA")
    if write:
        out.save(dst_path, "PNG", optimize=True, compress_level=9)
    return out


def frame_shifts(frames):
    """Sub-pixel per-frame body travel, by phase correlation of the ground-contact profile.

    The method prep_push.py settled on after three others disagreed: take the column
    occupancy of the bottom rows (the feet in contact with the ground), cross-correlate
    consecutive frames in the frequency domain and read the sub-pixel peak.
    """
    band = [np.asarray(f)[:, :, 3][-26:, :].astype(np.float64).sum(axis=0) for f in frames]
    shifts = []
    for i in range(len(band) - 1):
        a, b = band[i], band[i + 1]
        if a.sum() < 1e-6 or b.sum() < 1e-6:
            continue
        a = a - a.mean()
        b = b - b.mean()
        n = 1 << (len(a) * 2 - 1).bit_length()
        c = np.fft.irfft(np.fft.rfft(b, n) * np.conj(np.fft.rfft(a, n)), n)
        k = int(np.argmax(c))
        # parabolic sub-pixel refinement around the integer peak
        y0, y1_, y2 = c[(k - 1) % n], c[k], c[(k + 1) % n]
        denom = (y0 - 2 * y1_ + y2)
        frac = 0.0 if abs(denom) < 1e-12 else 0.5 * (y0 - y2) / denom
        sft = k + frac
        if sft > n / 2:
            sft -= n
        shifts.append(-sft)  # the body moves forward, so the contact profile shifts backward
    return np.array(shifts)


def measure_stride():
    """Ground covered by one full gait cycle, as a fraction of the standing silhouette.

    MEASURED AT THE PLATE'S OWN FULL RATE (raw 55..105 every frame, one whole period plus
    the wrap pair), never on the halved output: at 2-frame spacing the correlation locks
    onto the wrong foot on several pairs and the answer comes out ~45% high with a 10x
    worse spread (4.749 +/- 0.529 px/frame against 1.61 +/- 0.19).

    The estimator is the SUM of the per-frame shifts over exactly one period - i.e. the
    net displacement of the body over a cycle, which is the quantity actually wanted.
    Frame-by-frame the series runs -1.6 to +3.3 px, and a handful of those pairs really are
    negative, which a body cannot be: they are the swing-phase pairs where the contact
    band changes shape rather than translating (the same artefact prep_push.py's note
    describes). Summing over a whole period lets those phase artefacts cancel, where
    mean-of-stance-only (0.313) and median-of-stance-only (0.386) disagree by 18% depending
    purely on which pairs get cut. The three estimators bracket 0.31..0.39.

    ONE DIFFERENCE FROM WALK AND PUSH, and it changes how much this number has to be
    trusted: those two drive their loops from ground distance so that a planted foot does
    NOT slide, and being wrong there shows up immediately as skating. Here the feet are
    meant to slide - the wind is dragging the character backwards while he strides forward
    - so this is a cadence knob, not a foot-planting constraint. Anywhere in the measured
    bracket reads fine; it is tuned on screen, not to the third decimal.
    """
    frames = [cut(os.path.join(RAW, "windwalk", "%04d.png" % r), None, False)
              for r in range(55, 106)]
    s = frame_shifts(frames)
    total = s.sum()
    return total / TARGET_SILHOUETTE, s


def main(argv):
    measure_only = "--measure" in argv

    print("scale (raw -> sprite px): %.6f   crop box %.1f x %.1f raw -> %dx%d"
          % (SCALE, CROP_W, CROP_H, OUT_W, OUT_H))
    print("crop box spans raw x %.1f .. %.1f" % (BODY_CENTRE_RAW - CROP_W / 2.0, BODY_CENTRE_RAW + CROP_W / 2.0))
    print("standing silhouette: %.0f raw -> %.2f sprite px (target %.2f)"
          % (STANDING_SILHOUETTE_RAW, STANDING_SILHOUETTE_RAW * SCALE, TARGET_SILHOUETTE))
    print()

    counts = {}
    for src_folder, dst_folder, first, last, step in CLIPS:
        src_dir = os.path.join(RAW, src_folder)
        dst_dir = os.path.join(OUT, dst_folder)
        if not measure_only:
            os.makedirs(dst_dir, exist_ok=True)
            for stale in os.listdir(dst_dir):
                if stale.endswith(".png"):
                    os.remove(os.path.join(dst_dir, stale))
        frames = list(range(first, last + 1, step))
        counts[dst_folder] = len(frames)
        clipped = []
        tops = []
        for out_index, raw in enumerate(frames, start=1):
            src = os.path.join(src_dir, "%04d.png" % raw)
            a = alpha(src)
            if (a[:, 0] > 8).sum() > 0 or (a[:, -1] > 8).sum() > 0:
                clipped.append(raw)
            dst = os.path.join(dst_dir, "%04d.png" % out_index)
            img = cut(src, dst, write=not measure_only)
            b = np.asarray(img)[:, :, 3]
            ys, xs = np.nonzero(b > 8)
            tops.append((out_index, raw, xs.min(), xs.max(), ys.min(), ys.max()))
        print("%-16s raw %d..%d step %d -> %d frames at %dx%d"
              % (src_folder, first, last, step, len(frames), OUT_W, OUT_H))
        print("   silhouette bbox: x %d..%d   y %d..%d   (bottom row must be %d)"
              % (min(t[2] for t in tops), max(t[3] for t in tops),
                 min(t[4] for t in tops), max(t[5] for t in tops), OUT_H - 1))
        margin_l = min(t[2] for t in tops)
        margin_r = OUT_W - 1 - max(t[3] for t in tops)
        print("   frame margin: %d px left, %d px right" % (margin_l, margin_r))
        if clipped:
            print("   WARNING: plate-edge clipping on raw frames %s - repair before use" % clipped)
        else:
            print("   no plate-edge clipping in this window")

    stride, shifts = measure_stride()
    print()
    print("windwalk stride, measured at the plate's own rate over one whole period:")
    print("   per-frame shift %.3f +/- %.3f sprite px  (median %.3f, range %.2f..%.2f)"
          % (shifts.mean(), shifts.std() / np.sqrt(len(shifts)), np.median(shifts),
             shifts.min(), shifts.max()))
    print("   one 50-raw-frame cycle advances %.1f sprite px = %.1f world units on a 96-unit body"
          % (stride * TARGET_SILHOUETTE, stride * 96.0))
    print("   at the plate's 24fps that cycle is %.2fs, i.e. the character strains along at %.1f u/s"
          % (50 / 24.0, stride * 96.0 / (50 / 24.0)))
    print("   WIND_STRIDE_PER_HEIGHT = %.2f" % stride)

    print()
    print("---- paste into PlayerAnimations.kt ----")
    print("    private const val WIND_TRANSITION_FRAMES = %d" % counts["windtransition"])
    print("    private const val WIND_WALK_FRAMES = %d" % counts["windwalk"])
    print("    const val WIND_TRANSITION_LAST = WIND_TRANSITION_FRAMES - 1")
    print("    const val WIND_WALK_LOOP_LENGTH = WIND_WALK_FRAMES")
    print("    const val WIND_STRIDE_PER_HEIGHT = %.2f" % stride)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
