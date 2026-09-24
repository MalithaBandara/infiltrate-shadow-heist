"""
Cut the level 7 patrol rover's plates into the two textures CameraBotVisual draws.

Usage:  python tools/art/prep_robot.py [<art-source-robot-dir>]

Default source is art-source/robot (gitignored, like every other raw plate), holding the owner's
drop: robot.png (1536x1024, the whole rover as one silhouette) and robotwheel.png (1278x1230, the
cogged road wheel on its own). Writes resources/robot_body.png and resources/robot_wheel.png and
prints the constants CameraBotVisual needs - MEASURED off these plates, not derived, so re-run
this and paste the numbers whenever the art changes rather than editing the constants by hand.

WHY THE BODY PLATE IS NOT JUST robot.png: the rover is a flat black silhouette, so the ONLY part
of it that can show motion is its outline. A cog drawn inside the body is invisible; the rolling
read comes entirely from the tooth tips breaking the rim. That means the body plate must not
carry a set of static teeth of its own, or the wheel sprite rotating underneath would just union
with them into a permanently-toothy ring that shimmers instead of rolls.

So the wheels are cut OUT of the body:

  * Both road wheels are found, not assumed. `robot.png`'s chassis has a dead flat underside, so
    the last scanline that crosses the silhouette in ONE run is the chassis floor and every row
    below it is wheels-only - which gives each wheel's bounding box directly, and from it the
    centre and the outer (tooth-tip) radius. Measured: see the printout.
  * The cut is a disc of radius r + ERASE_MARGIN, i.e. slightly WIDER than the wheel, so no tooth
    tip survives in the body even at the resampled edge. Erasing slightly narrow instead would
    leave a 2px fringe of tooth tips, which is exactly the artifact this whole split exists to
    avoid.
  * ...except where the disc overlaps the CHASSIS, which has to stay. The chassis outline is
    hidden behind the wheels for its whole lower half, so it is reconstructed rather than traced:
    its floor is the flat underside above, and its left and right walls are read off the last
    scanline above the wheels' tops (where nothing is occluded yet). That rectangle is put back
    inside the discs. Everything in a disc and outside it was wheel, and is gone.

The union of body-plate + wheel-sprite is then the original silhouette, with the rim supplied
entirely by the rotating sprite.

WHICH WAY IT FACES is a judgement, not a measurement, and it is the one thing here worth
re-checking on screen. The plate is kept in its source orientation and treated as facing RIGHT,
which puts the articulated boom's slab reaching forward over the front wheel and the chassis's
sloped fender over it, and drops the vision cone out from under the slab's tip. Read the other
way round (stepped end forward) the slab overhangs the tail and the cone comes off a blank nose,
which is worse. If it turns out to be backwards, flip the plate here - do not invert the sign in
CameraBotVisual, which follows the same `facing < 0 -> scaleX = -1` convention as every other
actor in the game.

The wheel plate is cropped to its own bounding box and resized to a square, which is also what
makes it round: the raw cog is drawn 4% wider than it is tall, and rotating that wobbles the
silhouette once per turn. Cropping to the bbox and forcing a square divides that out, and it
rotates about the bbox centre so the outer extent is fixed by construction. (The centroid sits
~7 raw px lower - the cog is not quite symmetric - but rotating about the centroid would move
the rim instead, which is the more visible of the two errors.)

Sizes are the POT rule from pot_resize.py: drawn size in virtual units x3, rounded up. The body
is drawn 32 units wide so 96 -> 128; the wheel is drawn ~8.8 units so 26 -> 32, taken to 64
because it is the one asset here that is resampled at every angle rather than axis-aligned.
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pot_resize import resize_premultiplied

ALPHA_FLOOR = 16          # what counts as opaque when measuring
ERASE_MARGIN = 2.0        # raw px of slack on the wheel cut-out; see the header
BODY_OUT = 128            # POT: 32 virtual units drawn x3 = 96
WHEEL_OUT = 64            # POT: ~8.8 units x3 = 26, doubled for rotation


def mask(path):
    im = Image.open(path).convert("RGBA")
    return im, np.asarray(im)[:, :, 3] > ALPHA_FLOOR


def bbox(m):
    ys, xs = np.nonzero(m)
    return xs.min(), ys.min(), xs.max(), ys.max()


def runs(row):
    """[(start, end_inclusive), ...] of the opaque spans in one scanline."""
    idx = np.nonzero(row)[0]
    if len(idx) == 0:
        return []
    breaks = np.nonzero(np.diff(idx) > 1)[0]
    out, start = [], idx[0]
    for b in breaks:
        out.append((start, idx[b]))
        start = idx[b + 1]
    out.append((start, idx[-1]))
    return out


def find_wheels(m):
    """
    Chassis floor + the two road wheels under it.

    Walks up from the bottom to the first scanline that crosses the silhouette in a single run:
    below it the only thing left is wheels, so each wheel's bbox is unoccluded and gives its
    centre and outer radius straight off.
    """
    h = m.shape[0]
    floor = None
    for y in range(h - 1, -1, -1):
        r = runs(m[y])
        if len(r) == 1:
            floor = y
            break
    if floor is None:
        raise SystemExit("robot.png: no scanline crosses the body in one run - not this shape")

    below = m[floor + 1:, :]
    spans = runs(below.any(axis=0))
    if len(spans) != 2:
        raise SystemExit("robot.png: expected 2 wheels below the chassis floor, found %d" % len(spans))

    wheels = []
    for x0, x1 in spans:
        col = below[:, x0:x1 + 1]
        ys = np.nonzero(col.any(axis=1))[0]
        bottom = floor + 1 + ys.max()
        r = (x1 - x0) / 2.0
        wheels.append(dict(cx=(x0 + x1) / 2.0, cy=bottom - r, r=r, x0=x0, x1=x1, bottom=bottom))
    return floor, wheels


def chassis_walls(m, wheels):
    """
    The chassis's own left and right edges, read one scanline above the wheels' tops - the last
    row where the wheels occlude nothing. Below this the chassis is behind them all the way down,
    and is reconstructed as a rectangle down to the floor.
    """
    top = int(min(w["cy"] - w["r"] for w in wheels))
    y = top - 4
    r = runs(m[y])
    if not r:
        raise SystemExit("robot.png: nothing on the scanline above the wheels")
    return y, r[0][0], r[-1][1]


def main(argv):
    src = argv[0] if argv else os.path.join("art-source", "robot")
    body_src = os.path.join(src, "robot.png")
    wheel_src = os.path.join(src, "robotwheel.png")
    for p in (body_src, wheel_src):
        if not os.path.exists(p):
            print("error: %s not found (pass the source dir as argv[1])" % p)
            return 1

    im, m = mask(body_src)
    W, H = im.size
    floor, wheels = find_wheels(m)
    wall_y, wall_l, wall_r = chassis_walls(m, wheels)

    print("robot.png      %dx%d" % (W, H))
    print("  chassis floor (last single-run scanline) y = %d" % floor)
    print("  chassis walls read at y = %d: x %d .. %d" % (wall_y, wall_l, wall_r))
    for name, w in zip(("rear", "front"), wheels):
        print("  %-5s wheel  centre (%.1f, %.1f)  outer r %.1f  bottom y %d"
              % (name, w["cx"], w["cy"], w["r"], w["bottom"]))

    # Cut the wheels out of the body, putting back the chassis rectangle they cover.
    yy, xx = np.mgrid[0:H, 0:W]
    cut = np.zeros((H, W), dtype=bool)
    for w in wheels:
        cut |= (xx - w["cx"]) ** 2 + (yy - w["cy"]) ** 2 <= (w["r"] + ERASE_MARGIN) ** 2
    keep = (yy <= floor) & (xx >= wall_l) & (xx <= wall_r)
    cut &= ~keep

    rgba = np.array(im)
    rgba[:, :, 3] = np.where(cut, 0, rgba[:, :, 3])

    # Crop to the WHOLE rover's bbox, not the cut body's - the two plates have to share one
    # coordinate frame, and the wheels are what the silhouette stands on. The transparent margin
    # this leaves where the wheels were is the alignment, not waste.
    bx0, by0, bx1, by1 = bbox(m)
    bw, bh = bx1 - bx0 + 1, by1 - by0 + 1
    body = Image.fromarray(rgba, "RGBA").crop((bx0, by0, bx1 + 1, by1 + 1))
    resize_premultiplied(body, (BODY_OUT, BODY_OUT)).save(
        os.path.join("resources", "robot_body.png"), "PNG", optimize=True)

    wim, wm = mask(wheel_src)
    wx0, wy0, wx1, wy1 = bbox(wm)
    wheel = wim.crop((wx0, wy0, wx1 + 1, wy1 + 1))
    resize_premultiplied(wheel, (WHEEL_OUT, WHEEL_OUT)).save(
        os.path.join("resources", "robot_wheel.png"), "PNG", optimize=True)

    print("\nrobotwheel.png %dx%d -> bbox %dx%d (%.1f%% wider than tall; squared on the way out)"
          % (wim.size[0], wim.size[1], wx1 - wx0 + 1, wy1 - wy0 + 1,
             100.0 * ((wx1 - wx0) / (wy1 - wy0) - 1.0)))
    print("wrote resources/robot_body.png  %dx%d" % (BODY_OUT, BODY_OUT))
    print("wrote resources/robot_wheel.png %dx%d" % (WHEEL_OUT, WHEEL_OUT))

    # Constants, as fractions of the DRAWN body box, which is the cut body's own bounding box.
    print("\n--- paste into CameraBotVisual (fractions of the drawn body box) ---")
    print("        private const val BODY_ASPECT = %.4f      // %d x %d" % (bh / bw, bw, bh))
    print("        private const val WHEEL_CY = %.4f         // of body height" % ((wheels[0]["cy"] - by0) / bh))
    print("        private const val WHEEL_R = %.4f          // of body WIDTH" % (max(w["r"] for w in wheels) / bw))
    for name, w in zip(("REAR", "FRONT"), wheels):
        print("        private const val WHEEL_CX_%-5s = %.4f  // of body width" % (name, (w["cx"] - bx0) / bw))
    dy = abs(wheels[0]["cy"] - wheels[1]["cy"])
    dr = abs(wheels[0]["r"] - wheels[1]["r"])
    print("  (the two wheels agree to %.1f raw px in centre height and %.1f px in radius)" % (dy, dr))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
