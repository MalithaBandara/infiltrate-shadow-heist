"""
Cut the conveyor belt's left end (the drum) out of the original conveyor.png into
resources/conveyor_end.png - drawn by GameplayScene against a belt's left edge.

Usage:  python tools/art/prep_conveyor_end.py [path/to/conveyor.png]
        (default: C:/Users/USER/Downloads/charAnimations/assets/conveyor.png)

The belt the game draws is NOT the source's own silhouette: conveyor_top/mid.png are
1:1 crops of the source (top cleats at source rows 257..305, frame 310..493), but
conveyor_bot.png is conveyor_top.png flipped, laid under the frame - so the drawn belt
is symmetric top to bottom and 289 source rows tall, where the source is 245. The end
cap is built the same way: the source's own top half of the drum, mirrored about the
drum's centre (source row 396.5 - the axle hole spans 367..425), then drawn stretched
to the belt's height (280 -> 289 rows, 3%, which keeps the axle hole round).

Cut at source column 364: the centre of a cleat notch (355..373) on the top run, and
inside the frame's open window (the end block ends at 278, the next roller starts at
~475), so the mid band meets conveyor_mid.png's window column-for-column.

The drum is then repainted as ONE wheel ("there are two circles at the end ... the belt
should go around that to make the full circle"): the source shows the belt's inner gap
only round the drum's left half (r 82.5..87.5 about the axle at 145, 396.5), plus a
faint, off-centre arc (r ~81) on its right where the drum meets the frame's end block -
read as a second circle. The gap is cut as a full ring about the axle, and everything
inside it filled solid except the axle hole (r 29.5).

Drawn 26 tall x 30 wide -> x3 = 90 x 78 -> 128 x 128 (see "Adding new art").
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from pot_resize import resize_premultiplied

SRC = sys.argv[1] if len(sys.argv) > 1 else "C:/Users/USER/Downloads/charAnimations/assets/conveyor.png"
LEFT, CUT = 30, 364          # alpha starts at 30; see CUT above
TOP, AXIS = 257, 396.5       # belt top row; drum centre
OUT = "resources/conveyor_end.png"

src = np.asarray(Image.open(SRC).convert("RGBA"))
top_half = src[TOP:int(AXIS + 0.5), LEFT:CUT]          # rows 257..396
full = np.concatenate([top_half, top_half[::-1]], axis=0)

# One drum: a full belt ring about the axle, solid inside it bar the axle hole. Coverage is
# analytic (distance to each edge, +-0.5 px) so the edges come out anti-aliased.
AXLE_X, AXLE_Y = 145.0 - LEFT, AXIS - TOP - 0.5      # pixel-centre coordinates in the cap
RING_IN, RING_OUT, HOLE = 82.5, 87.5, 29.5
h, w = full.shape[:2]
yy, xx = np.mgrid[0:h, 0:w]
d = np.hypot(xx - AXLE_X, yy - AXLE_Y)
cov = lambda edge_minus_d: np.clip(edge_minus_d + 0.5, 0.0, 1.0)
disc = cov(RING_IN - d)                      # inside the ring
hole = cov(HOLE - d)
ring = np.minimum(cov(d - RING_IN), cov(RING_OUT - d))
alpha = full[..., 3] / 255.0
alpha = alpha * (1.0 - disc) + disc * (1.0 - hole)
alpha = alpha * (1.0 - ring)
full = full.copy()
full[..., :3] = 0
full[..., 3] = (alpha * 255.0 + 0.5).astype(np.uint8)
img = Image.fromarray(full, "RGBA")
print(f"cap {img.size[0]}x{img.size[1]} source px (belt draws 289 tall)")
resize_premultiplied(img, (128, 128)).save(OUT, optimize=True, compress_level=9)
print("wrote", OUT)
