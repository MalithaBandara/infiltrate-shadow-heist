"""
Cut the guard's raw plates down to the frames the game loads.

Usage:  python tools/art/prep_guard.py [<art-source-guard-dir>]

Default source is art-source/guard (gitignored, like the player's plates), holding the owner's
drops: idle/ (Downloads/charAnimations/guardidle, 144 frames) and walk/ (.../guardwalk, 144
frames), all 360x640. Writes resources/guard/idle and resources/guard/walk and prints the
constants GuardAnimations.kt and Guard.kt need - MEASURED off the output, not derived, so re-run
this and paste the numbers whenever the plates change rather than editing the constants by hand.

Same recipe as the player's clips (see the header comment on PlayerAnimations), with one twist
these plates force:

  * The guard holds a torch out in front of him at arm's length (the 2026-09-14 plates; the
    earlier set had his arms down). The crop is still symmetric about the character - so a
    horizontal flip (the sprite faces left by negating scaleX) does not shift him - but "the
    character" here means the BODY (measured as the head's centre column, which is what stays put
    while he sways or strides), not the union bounding box, whose centre would sit halfway out
    along the arm. The plate is not symmetric about the body (the torch reaches ~180px ahead, the
    back is ~85px behind), so the crop box runs off the left edge of the plate and is padded with
    transparent columns to make up the difference. That padding is atlas space spent on nothing,
    but both clips still fit one 2048 page with room over, so it is the cheapest way to keep the
    flip trivially correct.
  * Feet on the bottom row. Idle's plates all have their lowest opaque row at 615, so one fixed
    cut pins them. Walk's plates do NOT hold a ground line - the lowest row wanders 573..581 with
    the stride - so each walk frame is cut at its own lowest row, the way the player's crouch-walk
    had to be.
  * Scale so the standing silhouette is ~244 frame pixels, the player's density. The walk plates
    are shot smaller than the idle plates (517px tall at the passing pose vs 604 standing; the
    head alone measures ~12% smaller), so they get WALK_PLATE_SCALE on the way in or the guard
    would shrink the moment he moves. 530 is the walk plates' standing height once the walking
    lean is allowed for - between the raw 517 and the head-only estimate - and is the one number
    here that is a judgement rather than a measurement.
  * Premultiplied LANCZOS (pot_resize.py's), so edges pick up no halo from transparent RGB.

Idle: every third frame - 144 raw frames of a very slow sway (adjacent plates 0.23 mean alpha
apart, every third 0.66 - still a quarter of the walk's adjacent-frame step), so 48 of them at
the player idle's 45ms step is smooth and costs a third of the atlas. The clip does not loop
(the last plate sits ~3 mean alpha from the first, a dozen steps) - it is played out and back,
see GuardAnimations.

Walk: raw 42..79, every frame - one gait cycle of a walk-in-place plate. Autocorrelation puts the
period at 38-39 frames, and of every window that long this one has the tightest seam (frame 80
differs from 42 by 1.9 mean alpha against ~5.4 between adjacent frames), so the wrap does not
read. The planted foot tracks backwards ~7.3 plate px per frame, so a cycle covers ~277 plate px
against the 530px silhouette - WALK_STRIDE_PER_HEIGHT below - and GameplayScene drives the loop
by distance travelled so the feet stay planted at any patrol speed. Raw 1-41 and 80-144 are just
more cycles of the same walk (no wind-up, no settle), not loaded.

The torch: the vision cone starts at the lens, so the script also measures where the torch's tip
is (the rightmost opaque column, and the row band at it) relative to the body centre and the
feet, in units of the standing silhouette height, and prints the two Guard.kt constants.
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pot_resize import resize_premultiplied

PLATE = (360, 640)
MARGIN = 4

# idle
IDLE_KEEP_EVERY = 3
IDLE_BODY_CENTRE_X = 146         # head centre column, 140..152 across the sway
IDLE_REACH = 326 - IDLE_BODY_CENTRE_X + MARGIN   # torch tip at column 326 at the fullest
IDLE_X0, IDLE_X1 = IDLE_BODY_CENTRE_X - IDLE_REACH, IDLE_BODY_CENTRE_X + IDLE_REACH  # -38..330, width 368
IDLE_Y0, IDLE_Y1 = 12 - MARGIN, 616             # head top at row 12, feet (plate row 615) on the bottom row
IDLE_OUT = (149, 246)            # 368 * (246 / 608) = 148.9
IDLE_SILHOUETTE = 604.0          # rows 12..615

# walk
WALK_FIRST, WALK_LAST = 42, 79   # inclusive raw file numbers, one gait cycle
WALK_PLATE_SILHOUETTE = 530.0    # standing height on the walk plates (see header)
WALK_PLATE_SCALE = IDLE_SILHOUETTE / WALK_PLATE_SILHOUETTE
WALK_BODY_CENTRE_X = 150         # head centre column, 147..154 across the cycle
WALK_REACH = max(WALK_BODY_CENTRE_X - 28, 311 - WALK_BODY_CENTRE_X) + MARGIN   # union x 28..311
WALK_X0, WALK_X1 = WALK_BODY_CENTRE_X - WALK_REACH, WALK_BODY_CENTRE_X + WALK_REACH  # -15..315, width 330
WALK_ROWS = 530                  # rows kept above and including each frame's own feet row
WALK_OUT = (153, 245)            # 330 * (246/608) * WALK_PLATE_SCALE = 152.2; 530 * same = 244.5
WALK_PLATE_STRIDE_PX = 277.0     # planted foot travel per 38-frame cycle, plate px

def clear(dst):
    os.makedirs(dst, exist_ok=True)
    for old in os.listdir(dst):
        os.remove(os.path.join(dst, old))

def crop_padded(im, box):
    """PIL's crop pads with transparent pixels wherever the box runs outside the image; named so
    the negative x0 in the crop boxes above reads as intentional."""
    return im.crop(box)

def measure(out):
    a = np.asarray(out)[..., 3] > 10
    rows = np.where(a.any(axis=1))[0]
    return int(rows.min()), int(rows.max())

def torch_tip(alpha, feet_row, silhouette):
    """(tip column, centre row of the torch head) on a plate's alpha mask: the rightmost opaque
    column, and the middle of the rows lit within the last dozen columns before it. Only the
    upper half of the figure is searched - a striding front leg can reach under the torch and
    would otherwise be read as part of it."""
    top = int(feet_row - 0.5 * silhouette)
    cols = np.where(alpha[:top].any(axis=0))[0]
    tip = int(cols.max())
    rows = np.where(alpha[:top, tip - 12:tip + 1].any(axis=1))[0]
    return tip, (rows.min() + rows.max()) / 2.0

def back_heel_row(out):
    """Row of the back (left) boot's sole in an output frame: the lowest opaque row in the left
    third of the frame's foot region, where only the rear boot reaches."""
    a = np.asarray(out)[..., 3] > 10
    h, w = a.shape
    cols = np.where(a[h - 30:, :].any(axis=0))[0]
    left = cols.min()
    band = a[:, left:left + 18]
    return int(np.where(band.any(axis=1))[0].max())

def prep_idle(src, dst):
    clear(dst)
    files = sorted(f for f in os.listdir(src) if f.lower().endswith(".png"))[::IDLE_KEEP_EVERY]
    heads, feet, heels, torches = [], [], [], []
    for i, name in enumerate(files, start=1):
        im = Image.open(os.path.join(src, name)).convert("RGBA")
        assert im.size == PLATE, "%s is %r, expected %r" % (name, im.size, PLATE)
        alpha = np.asarray(im)[..., 3] > 10
        tip, trow = torch_tip(alpha, 615, IDLE_SILHOUETTE)
        torches.append(((tip - IDLE_BODY_CENTRE_X) / IDLE_SILHOUETTE, (615 - trow) / IDLE_SILHOUETTE))
        out = resize_premultiplied(crop_padded(im, (IDLE_X0, IDLE_Y0, IDLE_X1, IDLE_Y1)), IDLE_OUT)
        out.save(os.path.join(dst, "%04d.png" % i), "PNG", optimize=True)
        h, f = measure(out)
        heads.append(h); feet.append(f); heels.append(back_heel_row(out))
    print("idle: %d frames of %dx%d (every %d of %d)" % (len(files), IDLE_OUT[0], IDLE_OUT[1], IDLE_KEEP_EVERY, len(files) * IDLE_KEEP_EVERY))
    print("  feet row %d..%d, back heel row %d..%d, head row %d..%d, silhouette (frame 1) %d px"
          % (min(feet), max(feet), min(heels), max(heels), min(heads), max(heads), feet[0] - heads[0] + 1))
    print("  atlas %.2f Mpx" % (len(files) * IDLE_OUT[0] * IDLE_OUT[1] / 1e6))
    ahead = [t[0] for t in torches]; above = [t[1] for t in torches]
    print("  torch: ahead of body centre %.3f..%.3f, above feet %.3f..%.3f  (x silhouette height)"
          % (min(ahead), max(ahead), min(above), max(above)))
    return sum(ahead) / len(ahead), sum(above) / len(above)

def prep_walk(src, dst):
    clear(dst)
    heads, feet, torches = [], [], []
    for i, raw in enumerate(range(WALK_FIRST, WALK_LAST + 1), start=1):
        name = "%04d.png" % raw
        im = Image.open(os.path.join(src, name)).convert("RGBA")
        assert im.size == PLATE, "%s is %r, expected %r" % (name, im.size, PLATE)
        alpha = np.asarray(im)[..., 3] > 10
        feet_row = int(np.where(alpha.any(axis=1))[0].max())
        tip, trow = torch_tip(alpha, feet_row, WALK_PLATE_SILHOUETTE)
        torches.append(((tip - WALK_BODY_CENTRE_X) / WALK_PLATE_SILHOUETTE, (feet_row - trow) / WALK_PLATE_SILHOUETTE))
        y1 = feet_row + 1
        y0 = y1 - WALK_ROWS
        assert y0 >= 0, "%s: feet at %d leave no room for %d rows" % (name, feet_row, WALK_ROWS)
        out = resize_premultiplied(crop_padded(im, (WALK_X0, y0, WALK_X1, y1)), WALK_OUT)
        out.save(os.path.join(dst, "%04d.png" % i), "PNG", optimize=True)
        h, f = measure(out)
        heads.append(h); feet.append(f)
    n = WALK_LAST - WALK_FIRST + 1
    print("walk: %d frames of %dx%d (raw %d..%d)" % (n, WALK_OUT[0], WALK_OUT[1], WALK_FIRST, WALK_LAST))
    print("  feet row %d..%d, head row %d..%d" % (min(feet), max(feet), min(heads), max(heads)))
    print("  WALK_STRIDE_PER_HEIGHT = %.3f  (%.0f plate px per cycle / %.0f px standing silhouette)"
          % (WALK_PLATE_STRIDE_PX / WALK_PLATE_SILHOUETTE, WALK_PLATE_STRIDE_PX, WALK_PLATE_SILHOUETTE))
    print("  atlas %.2f Mpx" % (n * WALK_OUT[0] * WALK_OUT[1] / 1e6))
    ahead = [t[0] for t in torches]; above = [t[1] for t in torches]
    print("  torch: ahead of body centre %.3f..%.3f, above feet %.3f..%.3f  (x silhouette height)"
          % (min(ahead), max(ahead), min(above), max(above)))
    return sum(ahead) / len(ahead), sum(above) / len(above)

def main(argv):
    src = argv[0] if argv else os.path.join("art-source", "guard")
    ia, ib = prep_idle(os.path.join(src, "idle"), os.path.join("resources", "guard", "idle"))
    wa, wb = prep_walk(os.path.join(src, "walk"), os.path.join("resources", "guard", "walk"))
    print("Guard.kt (mean of both clips):")
    print("  TORCH_AHEAD_PER_HEIGHT = %.2f" % ((ia + wa) / 2.0))
    print("  TORCH_ABOVE_FEET_PER_HEIGHT = %.2f" % ((ib + wb) / 2.0))
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
