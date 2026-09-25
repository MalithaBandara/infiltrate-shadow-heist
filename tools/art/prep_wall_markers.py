"""Recolour level 4's wall distance stencils to white for level 7.

Level 4's markers (`resources/wall_<label>.png`) are a worn ochre stencil - a single hue
(~#E1C672) carrying all of its shape in the alpha channel, speckle and all. Level 7 asked for
the same markers in white ("for level 7 make that text white").

`colorMul` cannot do this at runtime: it multiplies, so an ochre source can only ever be
darkened by it, never lifted to white. The recolour has to happen on disk.

Because every pixel's shape information already lives in alpha, the conversion is exact: keep
the alpha channel untouched (so the stencil's worn edges, grain and the loose speckle around the
glyphs all survive) and replace RGB with pure white. Nothing is resampled, so there is no new
softness and no halo to premultiply around.

Sizes are left alone. These load with `minified = false` (they are stencils drawn at roughly 1:1,
not minified props), so the power-of-two rule in SceneAssets.warnIfNotPowerOfTwo does not apply
and padding them to POT would only waste texture memory.
"""

import os

import numpy as np
from PIL import Image

SRC_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "resources")

# Level 7 counts down from 120m at the same 30m spacing level 4 uses.
LABELS = ["120m", "90m", "60m", "30m", "0m"]


# --- Where the level 7 duct wall is bare -----------------------------------------------------
# A stencil has to land on plain panel, not on a louvred vent, a junction box, a conduit or a
# standpipe ("put the name only on places where background is empty. no other objects.").
#
# Columns are scored by the worst vertical luminance step inside the band the stencils are drawn
# in. That test passes a panel seam, which is a vertical line and so has no vertical step, and
# fails a vent's louvres, a box's rim and a pipe's shading - which is exactly the distinction
# wanted. GameplayScene.LEVEL_7_CLEAR_WALL_WINDOWS is this output pasted in.
#
# Rows 305..380 is where a 480-unit canvas puts the stencils. That is the demanding case: it needs
# the widest footprint in texture pixels AND has the narrowest windows, and every window it finds
# is contained in the equivalent window for a taller canvas, so one position works everywhere.
BG = "bglvl7.png"
BAND = (305, 380)
EDGE_THRESHOLD = 22.0
MIN_WINDOW = 55


def clear_wall_windows() -> None:
    path = os.path.normpath(os.path.join(SRC_DIR, BG))
    lum = np.asarray(Image.open(path).convert("L")).astype(float)
    width = lum.shape[1]
    # Doubled so a window running off the right edge and continuing at the left is found as one:
    # the tile repeats, so that is a single continuous panel.
    doubled = np.concatenate([lum, lum], axis=1)
    step = np.abs(np.diff(doubled[BAND[0]:BAND[1], :], axis=0)).max(axis=0)
    smoothed = np.convolve(step, np.ones(9) / 9.0, mode="same")

    runs, start = [], None
    for x, clear in enumerate(smoothed < EDGE_THRESHOLD):
        if clear and start is None:
            start = x
        elif not clear and start is not None:
            runs.append((start, x))
            start = None
    if start is not None:
        runs.append((start, len(smoothed)))

    keep = [r for r in runs if r[1] - r[0] >= MIN_WINDOW and r[0] < width and r[1] <= width + 200]
    print("")
    print("clear wall windows in %s, rows %d..%d (texture px):" % (BG, BAND[0], BAND[1]))
    for lo, hi in keep:
        note = "  <- crosses the tile seam" if hi > width else ""
        print("    %7.1f..%-7.1f  (%3d wide)%s" % (lo, hi, hi - lo, note))


def main() -> None:
    for label in LABELS:
        src = os.path.normpath(os.path.join(SRC_DIR, "wall_%s.png" % label))
        dst = os.path.normpath(os.path.join(SRC_DIR, "wall7_%s.png" % label))
        im = Image.open(src).convert("RGBA")
        alpha = im.getchannel("A")
        white = Image.new("RGBA", im.size, (255, 255, 255, 0))
        white.putalpha(alpha)
        white.save(dst)
        opaque = sum(1 for a in alpha.get_flattened_data() if a > 200)
        print("%-16s -> %-17s %dx%d  %d opaque px" % (
            os.path.basename(src), os.path.basename(dst), im.width, im.height, opaque
        ))


if __name__ == "__main__":
    main()
    clear_wall_windows()
