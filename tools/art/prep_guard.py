"""
Cut the guard's raw plates down to the frames the game loads.

Usage:  python tools/art/prep_guard.py [<art-source-guard-dir>]

Default source is art-source/guard (gitignored, like the player's plates), holding the owner's
drops: idle/ (Downloads/charAnimations/guardidle, 144 frames) and walk/ (.../guardwalk, 192
frames), all 360x640. Writes resources/guard/idle and resources/guard/walk and prints the
constants GuardAnimations.kt needs - MEASURED off the output, not derived, so re-run this and
paste the numbers whenever the plates change rather than editing the constants by hand.

Same recipe as the player's clips (see the header comment on PlayerAnimations):

  * One shared crop box per clip, symmetric about the character's centre column (180.5 on these
    plates), so a horizontal flip (the sprite faces left by negating scaleX) does not shift him.
  * Feet on the bottom row. Idle's plates all have their lowest opaque row at 602, so one fixed
    cut pins them. Walk's plates do NOT hold a ground line - the lowest row wanders 583..594 with
    the stride, 10px of rig drift with both feet visibly planted - so each walk frame is cut at
    its own lowest row, the way the player's crouch-walk had to be.
  * Scale so the standing silhouette is ~244 frame pixels, the player's density. The walk plates
    are shot 6.5% smaller than the idle plates (542px tall at the passing pose vs 577 standing),
    so they get that extra scale on the way in or the guard would shrink the moment he moves.
  * Premultiplied LANCZOS (pot_resize.py's), so edges pick up no halo from transparent RGB.

Idle: every other frame - 144 raw frames of a very slow sway, adjacent plates ~0.12 mean alpha
apart, so half of them at the player idle's 45ms step is smooth and costs half the atlas.

Walk: raw 66..105, every frame - one gait cycle of a walk-in-place plate. Autocorrelation puts
the period at 40 frames, and of every 40-frame window this one has the tightest seam (frame 106
differs from 66 by 1.2 mean alpha against ~5.7 between adjacent frames), so the wrap does not
read. The planted foot tracks backwards ~7.5 plate px per frame, so a cycle covers ~300 plate px
against the 542px silhouette - WALK_STRIDE_PER_HEIGHT below - and GameplayScene drives the loop
by distance travelled so the feet stay planted at any patrol speed. Raw 1-65 and 106-192 are
just more cycles of the same walk (no wind-up, no settle), not loaded.
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pot_resize import resize_premultiplied

PLATE = (360, 640)
CENTRE_X = 180.5                 # the idle plates' union bbox centre; walk's is 180 - same rig
SCALE = 246.0 / 581.0            # idle: 581 crop rows -> 246 frame rows, silhouette 577 -> ~244

# idle
IDLE_KEEP_EVERY = 2
IDLE_X0, IDLE_X1 = 92, 269       # width 177, symmetric about 180.5
IDLE_Y0, IDLE_Y1 = 22, 603       # height 581, feet (plate row 602) on the bottom row
IDLE_OUT = (75, 246)

# walk
WALK_FIRST, WALK_LAST = 66, 105  # inclusive raw file numbers, one gait cycle
WALK_PLATE_SCALE = 577.0 / 542.0 # walk plates are framed smaller than idle's
WALK_X0, WALK_X1 = 48, 313       # width 265, symmetric about 180.5, the stride's full reach
WALK_ROWS = 567                  # rows kept above and including each frame's own feet row
WALK_OUT = (120, 256)            # 265 * SCALE * WALK_PLATE_SCALE = 119.5; 567 * same = 255.7
WALK_PLATE_STRIDE_PX = 300.0     # planted foot travel per 40-frame cycle, plate px
WALK_PLATE_SILHOUETTE = 542.0    # standing height on the walk plates

def clear(dst):
    os.makedirs(dst, exist_ok=True)
    for old in os.listdir(dst):
        os.remove(os.path.join(dst, old))

def measure(out):
    a = np.asarray(out)[..., 3] > 10
    rows = np.where(a.any(axis=1))[0]
    return int(rows.min()), int(rows.max())

def prep_idle(src, dst):
    clear(dst)
    files = sorted(f for f in os.listdir(src) if f.lower().endswith(".png"))[::IDLE_KEEP_EVERY]
    heads, feet = [], []
    for i, name in enumerate(files, start=1):
        im = Image.open(os.path.join(src, name)).convert("RGBA")
        assert im.size == PLATE, "%s is %r, expected %r" % (name, im.size, PLATE)
        out = resize_premultiplied(im.crop((IDLE_X0, IDLE_Y0, IDLE_X1, IDLE_Y1)), IDLE_OUT)
        out.save(os.path.join(dst, "%04d.png" % i), "PNG", optimize=True)
        h, f = measure(out)
        heads.append(h); feet.append(f)
    print("idle: %d frames of %dx%d" % (len(files), IDLE_OUT[0], IDLE_OUT[1]))
    print("  feet row %d..%d, head row %d..%d, silhouette (frame 1) %d px"
          % (min(feet), max(feet), min(heads), max(heads), feet[0] - heads[0] + 1))
    print("  atlas %.2f Mpx" % (len(files) * IDLE_OUT[0] * IDLE_OUT[1] / 1e6))

def prep_walk(src, dst):
    clear(dst)
    heads, feet = [], []
    for i, raw in enumerate(range(WALK_FIRST, WALK_LAST + 1), start=1):
        name = "%04d.png" % raw
        im = Image.open(os.path.join(src, name)).convert("RGBA")
        assert im.size == PLATE, "%s is %r, expected %r" % (name, im.size, PLATE)
        alpha = np.asarray(im)[..., 3] > 10
        feet_row = int(np.where(alpha.any(axis=1))[0].max())
        y1 = feet_row + 1
        y0 = y1 - WALK_ROWS
        assert y0 >= 0, "%s: feet at %d leave no room for %d rows" % (name, feet_row, WALK_ROWS)
        out = resize_premultiplied(im.crop((WALK_X0, y0, WALK_X1, y1)), WALK_OUT)
        out.save(os.path.join(dst, "%04d.png" % i), "PNG", optimize=True)
        h, f = measure(out)
        heads.append(h); feet.append(f)
    n = WALK_LAST - WALK_FIRST + 1
    print("walk: %d frames of %dx%d (raw %d..%d)" % (n, WALK_OUT[0], WALK_OUT[1], WALK_FIRST, WALK_LAST))
    print("  feet row %d..%d, head row %d..%d" % (min(feet), max(feet), min(heads), max(heads)))
    print("  WALK_STRIDE_PER_HEIGHT = %.3f  (%.0f plate px per cycle / %.0f px standing silhouette)"
          % (WALK_PLATE_STRIDE_PX / WALK_PLATE_SILHOUETTE, WALK_PLATE_STRIDE_PX, WALK_PLATE_SILHOUETTE))
    print("  atlas %.2f Mpx" % (n * WALK_OUT[0] * WALK_OUT[1] / 1e6))

def main(argv):
    src = argv[0] if argv else os.path.join("art-source", "guard")
    prep_idle(os.path.join(src, "idle"), os.path.join("resources", "guard", "idle"))
    prep_walk(os.path.join(src, "walk"), os.path.join("resources", "guard", "walk"))
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
