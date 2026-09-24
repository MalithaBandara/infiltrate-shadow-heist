"""
Cut the level 7 steam nozzle's plate into the two textures SteamPipeVisual draws.

Usage:  python tools/art/prep_steam.py [<art-source-vent-dir>]

Default source is art-source/vent (gitignored, like every other raw plate), holding the owner's
drop: steam.png (2172x724, one emitter as a flat silhouette). Writes resources/steam_nozzle_up.png
and resources/steam_nozzle_down.png and prints the constants SteamPipeVisual needs - MEASURED off
this plate, not derived, so re-run this and paste the numbers whenever the art changes rather than
editing the constants by hand.

WHICH WAY UP: the plate is drawn with its bolted mounting flange along the BOTTOM and the nozzle
head at the top, so as-is it is a floor-mounted emitter firing upward - `PipeMountType.BOTTOM`.
The ceiling version is the same plate flipped vertically.

WHY TWO FILES RATHER THAN ONE AND A NEGATIVE SCALE: bug #8 in `.junie/guidelines.md` - a negative
scale on a detailed KorGE `Image` tears on this GL backend at large downscale, and these are drawn
at about a quarter of their stored size. `truck.png` and `entrance.png` are pre-mirrored on disk
for exactly this reason; so are these. The second copy costs 16K px.

The LED keeps its job. The old rect-built nozzle carried a status pip - red while dormant, green
from the 0.45s warning flare through the whole eruption - and that is a gameplay tell, not
decoration, so it survives the art swap. It is placed on the LEFT bolt block, whose centre this
script measures, so it sits on solid metal rather than floating in the taper.

Sizes are the POT rule from pot_resize.py: drawn size in virtual units x3, rounded up. The fixture
is drawn 64 units wide (2.5x the 26-unit rect nozzle it replaces, which is what the flange's own
proportions want), so 192 -> 256 by 57 -> 64. The stored aspect is therefore 4.0 against the art's
3.39; that squash is undone by the draw rect and is cheaper than padding, which would stretch into
the box along with the art.
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pot_resize import resize_premultiplied

ALPHA_FLOOR = 16
OUT_W, OUT_H = 256, 64
DRAWN_WIDTH = 64.0        # virtual units; see the header


def main(argv):
    src = argv[0] if argv else os.path.join("art-source", "vent")
    path = os.path.join(src, "steam.png")
    if not os.path.exists(path):
        print("error: %s not found (pass the source dir as argv[1])" % path)
        return 1

    im = Image.open(path).convert("RGBA")
    m = np.asarray(im)[:, :, 3] > ALPHA_FLOOR
    ys, xs = np.nonzero(m)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    w, h = x1 - x0 + 1, y1 - y0 + 1

    crop = im.crop((x0, y0, x1 + 1, y1 + 1))
    up = resize_premultiplied(crop, (OUT_W, OUT_H))
    up.save(os.path.join("resources", "steam_nozzle_up.png"), "PNG", optimize=True)
    up.transpose(Image.FLIP_TOP_BOTTOM).save(
        os.path.join("resources", "steam_nozzle_down.png"), "PNG", optimize=True)

    print("steam.png %dx%d -> bbox %dx%d at (%d, %d), aspect %.3f"
          % (im.size[0], im.size[1], w, h, x0, y0, w / h))

    # The nozzle mouth: the widest run in the top fifth, which is the cap the jet leaves from.
    cap = m[y0:y0 + h // 5, :]
    cxs = np.nonzero(cap.any(axis=0))[0]
    mouth = cxs.max() - cxs.min() + 1
    print("  nozzle mouth %d raw px = %.3f of width (%.1f units at a %.0f-unit fixture)"
          % (mouth, mouth / w, DRAWN_WIDTH * mouth / w, DRAWN_WIDTH))

    # The left bolt block, for the LED. Look in the band below the taper but above the base plate:
    # rows where the silhouette breaks into THREE runs are exactly the two blocks plus the trunk.
    led = None
    for y in range(y0, y1 + 1):
        idx = np.nonzero(m[y])[0]
        if len(idx) == 0:
            continue
        breaks = np.nonzero(np.diff(idx) > 1)[0]
        if len(breaks) == 2:
            block = idx[:breaks[0] + 1]
            led = ((block.min() + block.max()) / 2.0, y)
    if led is None:
        print("  WARNING: no three-run scanline - the bolt blocks were not found, LED left centred")
        led = (x0 + w * 0.16, y0 + h * 0.67)

    print("\n--- paste into SteamPipeVisual (fractions of the drawn fixture box) ---")
    print("        private const val NOZZLE_ASPECT = %.4f   // %d x %d" % (h / w, w, h))
    print("        private const val NOZZLE_WIDTH = %.1f" % DRAWN_WIDTH)
    print("        private const val LED_X = %.4f            // of width, from the left" % ((led[0] - x0) / w))
    print("        private const val LED_Y_UP = %.4f         // of height, from the TOP of the up plate"
          % ((led[1] - y0) / h))
    print("        private const val LED_Y_DOWN = %.4f       // the flipped plate's mirror of it"
          % (1.0 - (led[1] - y0) / h))
    print("wrote resources/steam_nozzle_up.png and steam_nozzle_down.png, %dx%d each" % (OUT_W, OUT_H))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
