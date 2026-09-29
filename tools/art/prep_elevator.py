"""
Cut resources/elevator.png (level 10/11's freight lifts) from the asset drop's elevator.png.

Usage:  python tools/art/prep_elevator.py [source.png]
        (default source: C:/Users/USER/Downloads/charAnimations/assets/elevator.png)

The source is a black silhouette of a lift cage: a railing (top rail, mid rail, chain-link mesh,
four posts) standing on a deck slab with two small feet under it, on a transparent canvas.

1. Crop to ALPHA (> 8), not getbbox() - see ".junie/guidelines.md" -> "Adding new art".
2. Measure, in the CROPPED art's own pixels, what GameplayScene needs to cut it up:
   - DECK_TOP / DECK_BOTTOM: the deck slab, the first and last rows opaque across nearly the
     whole width below the mesh (the feet hang under DECK_BOTTOM). DECK_TOP is the lift's
     walking surface; the railing is everything above it.
   - RIGHT_CAP_LEFT: where the right end cap starts (a little left of the last post). The scene
     draws any width as the left end cap (0..LEFT_CAP_RIGHT, the first post), the first bay's mesh
     (LEFT_CAP_RIGHT..BAY_RIGHT) repeated, then the right end cap - posts only at the ends.
3. Resample (premultiplied, never padded) to 1024x256: the art is drawn ~14 units of deck deep,
   i.e. ~267x79 units for the whole thing, x3 for a 1440p phone, rounded up to POT.

GameplayScene.ELEVATOR_* hold the printed numbers - re-run and paste if the art changes.
"""
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, "tools/art")
from pot_resize import resize_premultiplied  # noqa: E402

src = sys.argv[1] if len(sys.argv) > 1 else "C:/Users/USER/Downloads/charAnimations/assets/elevator.png"
im = Image.open(src).convert("RGBA")
a = np.asarray(im)
ys, xs = np.nonzero(a[..., 3] > 8)
crop = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
c = np.asarray(crop)
alpha = c[..., 3] > 128
h, w = alpha.shape

row_fill = alpha.mean(axis=1)
# The mesh never fills a row past ~0.3; the rails and the deck do (>0.95). The deck is the last
# such run, the lowest one.
full = np.nonzero(row_fill > 0.95)[0]
runs = np.split(full, np.nonzero(np.diff(full) > 1)[0] + 1)
deck = runs[-1]
deck_top, deck_bottom = int(deck[0]), int(deck[-1]) + 1

# Posts: columns solid through the railing's height.
col_fill = alpha[:deck_top].mean(axis=0)
posts = []
inside = False
for x, v in enumerate(col_fill):
    if v > 0.9 and not inside:
        start, inside = x, True
    elif v <= 0.9 and inside:
        posts.append((start, x))
        inside = False
if inside:
    posts.append((start, w))
right_cap_left = posts[-1][0] - 12
left_cap_right = posts[0][1] + 6
bay_right = posts[1][0] - 6

out = resize_premultiplied(crop, (1024, 256))
out.save("resources/elevator.png", optimize=True, compress_level=9)

print(f"cropped {w}x{h}, posts {posts}")
print(f"const val ELEVATOR_SRC_WIDTH = {w}.0")
print(f"const val ELEVATOR_SRC_HEIGHT = {h}.0")
print(f"const val ELEVATOR_DECK_TOP = {deck_top}.0")
print(f"const val ELEVATOR_DECK_BOTTOM = {deck_bottom}.0")
print(f"const val ELEVATOR_RIGHT_CAP_LEFT = {right_cap_left}.0")
print(f"const val ELEVATOR_POST_CENTER = {(posts[0][0] + posts[0][1]) / 2.0}")
print(f"const val ELEVATOR_LEFT_CAP_RIGHT = {left_cap_right}.0")
print(f"const val ELEVATOR_BAY_RIGHT = {bay_right}.0")
