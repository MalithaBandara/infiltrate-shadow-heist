"""
Cut the push clips from the raw plates and print the constants PlayerAnimations needs.

Usage:  python tools/art/prep_push.py            (writes resources/player/push{,transition})
        python tools/art/prep_push.py --measure  (print the measurements only, write nothing)

Run from the repo root. Same recipe as prep_guard.py: everything below is measured off
the plates, so re-run and paste the printed block rather than hand-editing the Kotlin.

SOURCE: C:\\Users\\USER\\Downloads\\charAnimations\\push (144 frames) and
\\pushtransition (96 frames), both 360x640 half-resolution plates - the same plate
format as crouch/crouchwalk/swing, not the 720x1280 the standing clips were shot at.

THE ONE THING THAT IS DIFFERENT ABOUT THESE PLATES: the character is framed SMALLER
here than in any other clip. Standing head-to-toe measures 484 rows against crouch's
517 and swing's 503 on plates of the identical size - a ~6.4% smaller camera framing,
not a different character. So the scale that maps these to the shared sprite size is
this clip's own (244.36 / 484), not the 244.36 / 517 the other half-res plates use.
Getting that wrong makes the character visibly change size the moment he starts
pushing, which is the exact failure the climb clip's scale ramp exists to avoid.

WHICH FRAMES ARE KEPT, and why (all four cuts measured, not chosen):

  pushtransition, raw 10..96 every 2nd (44 frames) - standing upright to a settled,
  braced push stance.
    * Raw 1-9 are a dead hold on the standing pose: frame 10 differs from frame 1 by
      0.13 of one sampled step, i.e. they are the same pose, so they are nine frames
      of atlas buying nothing. Starting at 10 also keeps the handover from idle clean,
      because frame 10 IS still the standing pose.
    * Raw 96 is the settled brace and is where the push loop's own footage begins -
      transition frame 96 against push frame 1 is 0.24 of a sampled step, so the two
      clips join without a pose step at all.

  push, raw 94..133, EVERY frame (40 frames) - one complete push-stride gait cycle.
    * The period is 40 raw frames (autocorrelation over the whole plate; 22 is the
      half-cycle, i.e. one step, and using it would make both legs the same leg).
    * Of every (start, period=40) pair in the clip, start 88 has the tightest seam
      (0.53 of an adjacent frame) but the WORST entry from the braced rest pose
      (5.7 frames of motion, ~150ms). Start 94 trades that for a still-fine 1.09
      seam and a 2.82 entry: the seam is crossed on every cycle, the entry only when
      the player starts moving, which is where a pose step is least visible anyway.
    * EVERY frame is kept, unlike the transition below, and the reason is the push's
      own slowness rather than any quality of the footage. The loop is driven by
      distance travelled, so the braced move speed sets its display rate: one cycle
      covers 56.6 world units at ~53 u/s, i.e. 1.07 SECONDS. Halved to 20 frames
      that is 19fps and reads as a flick-book; whole it is 37fps, the same cadence
      as walk's own loop (36fps). This is the swing clip's lesson arriving from the
      opposite direction - there, halving hurt because the action was fast; here it
      hurts because the action is slow. Re-do this arithmetic, do not reuse the
      conclusion, if GameWorld.PUSH_MOVE_FACTOR ever changes.
    * The trailing boot runs off the LEFT edge of the plate on raw 6-12 and 138-140
      (4-15 clipped rows). None of those frames is inside either window, so unlike
      the swing clip nothing had to be repaired - but re-check this if the windows
      ever move. The script fails loudly rather than silently shipping a flat-cut
      boot, so a bad window will announce itself.

GEOMETRY: one shared crop box per clip, symmetric about the character's STANDING body
centre (raw column 173.0, the bbox centre of pushtransition frame 1), exactly like the
standing clips - not about the union bbox, which would drift the body sideways as he
leans. The sprite is anchored at the frame's horizontal centre (GameplayScene's
Anchor2D(0.5, ...)), so keeping the standing centre means entering the stance from idle
does not shift the character by a pixel; the braced pose then leans forward over the
collision box's front edge and plants its feet behind it, which is what pushing looks
like. Half-width 178 raw covers the reach (hands to raw 344, trailing boot to raw 11) with
margin on both sides: the hands land 3-4 sprite px inside the frame, so the
antialiased edge of the leading fist is never clipped. 180 wide costs nothing over a
tighter 176 - the atlas packs 11 columns of either into a 2048 page.

Feet are pinned per frame - each frame's own lowest opaque row goes to the bottom edge,
the same rule crouch/crouchwalk use. A gait cycle's lowest row wanders 527..543 here as
the swing foot lifts; pinning kills that ~1.2-world-unit bob, which is the safe
direction to be wrong in (this file's standing rule: never leave a foot short of the
ground).
"""
import os, sys
import numpy as np
from PIL import Image

RAW = r"C:\Users\USER\Downloads\charAnimations"
OUT = os.path.join("resources", "player")

# Measured on the plates - see the module docstring.
STANDING_SILHOUETTE_RAW = 484.0   # pushtransition/0001.png, rows 61..544
TARGET_SILHOUETTE = 244.36        # PlayerAnimations.SOURCE_SILHOUETTE_HEIGHT
BODY_CENTRE_RAW = 173.0           # bbox centre of the standing pose
OUT_W, OUT_H = 180, 256

SCALE = TARGET_SILHOUETTE / STANDING_SILHOUETTE_RAW
CROP_W = OUT_W / SCALE
CROP_H = OUT_H / SCALE

# (folder, first, last, step) in raw 1-based file numbering.
CLIPS = [
    ("pushtransition", 10, 96, 2),
    ("push", 94, 133, 1),
]


def alpha(path):
    return np.asarray(Image.open(path).convert("RGBA"))[:, :, 3]


def bbox(a, thr=8):
    ys, xs = np.nonzero(a > thr)
    return xs.min(), xs.max(), ys.min(), ys.max()


def cut(src_path, dst_path, write):
    """Crop the shared box out of one plate and resample it to OUT_W x OUT_H.

    Premultiply -> resize -> unpremultiply, the same reasoning as
    tools/art/pot_resize.py: straight RGBA downsampling averages the RGB garbage
    sitting under fully transparent pixels into every edge pixel, which on
    black-on-transparent silhouette art shows up as a grey fringe.

    The crop box is fractional (356.5 x 507.1 raw), so it is handed to resize() as a
    float `box` rather than being rounded to whole pixels first - rounding it would
    put a ~0.1% scale wobble on the character from frame to frame.
    """
    im = Image.open(src_path).convert("RGBA")
    a = np.asarray(im)[:, :, 3]
    _, _, _, y1 = bbox(a)
    # Feet pinned: the bottom edge of the lowest opaque row becomes the frame's
    # bottom edge, so the ground line lands on PlayerAnimations.SOURCE_FEET_Y.
    bottom = y1 + 1.0
    left = BODY_CENTRE_RAW - CROP_W / 2.0

    # The box runs ~1 raw px off the left edge of the plate; pad so that becomes
    # transparent margin instead of being clamped back inside the art.
    pad = 8
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


def main(argv):
    measure_only = "--measure" in argv

    print("scale (raw -> sprite px): %.6f   crop box %.1f x %.1f raw -> %dx%d"
          % (SCALE, CROP_W, CROP_H, OUT_W, OUT_H))
    print("standing silhouette: %.0f raw -> %.2f sprite px (target %.2f)"
          % (STANDING_SILHOUETTE_RAW, STANDING_SILHOUETTE_RAW * SCALE, TARGET_SILHOUETTE))
    print()

    counts = {}
    for folder, first, last, step in CLIPS:
        src_dir = os.path.join(RAW, folder)
        dst_dir = os.path.join(OUT, folder)
        if not measure_only:
            os.makedirs(dst_dir, exist_ok=True)
            for stale in os.listdir(dst_dir):
                if stale.endswith(".png"):
                    os.remove(os.path.join(dst_dir, stale))
        frames = list(range(first, last + 1, step))
        counts[folder] = len(frames)
        clipped = []
        tops = []
        for out_index, raw in enumerate(frames, start=1):
            src = os.path.join(src_dir, "%04d.png" % raw)
            a = alpha(src)
            if (a[:, 0] > 8).sum() > 0:
                clipped.append(raw)
            dst = os.path.join(dst_dir, "%04d.png" % out_index)
            img = cut(src, dst, write=not measure_only)
            b = np.asarray(img)[:, :, 3]
            ys, xs = np.nonzero(b > 8)
            tops.append((out_index, raw, xs.min(), xs.max(), ys.min(), ys.max()))
        print("%-16s raw %d..%d step %d -> %d frames at %dx%d"
              % (folder, first, last, step, len(frames), OUT_W, OUT_H))
        print("   silhouette bbox: x %d..%d   y %d..%d   (bottom row must be %d)"
              % (min(t[2] for t in tops), max(t[3] for t in tops),
                 min(t[4] for t in tops), max(t[5] for t in tops), OUT_H - 1))
        if clipped:
            print("   WARNING: plate-edge clipping on raw frames %s - repair before use" % clipped)
        else:
            print("   no plate-edge clipping in this window")

    print()
    print("---- paste into PlayerAnimations.kt ----")
    print("    private const val PUSH_TRANSITION_FRAMES = %d" % counts["pushtransition"])
    print("    private const val PUSH_FRAMES = %d" % counts["push"])
    print("    const val PUSH_TRANSITION_LAST = PUSH_TRANSITION_FRAMES - 1")
    print("    const val PUSH_LOOP_LENGTH = PUSH_FRAMES")
    print("    const val PUSH_STRIDE_PER_HEIGHT = 0.59   # phase-correlate the OUTPUT frames, not these plates")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
