"""Contact sheet of a captured frame folder: every Nth frame, numbered."""
import sys, glob, os
from PIL import Image, ImageDraw
d = sys.argv[1]; step = int(sys.argv[2]) if len(sys.argv) > 2 else 5; out = sys.argv[3]
fs = sorted(glob.glob(os.path.join(d, 'f_*.png')))[::step]
tw = 480
ims = []
for f in fs:
    im = Image.open(f).convert('RGB'); im.thumbnail((tw, 1000))
    ImageDraw.Draw(im).text((6, 6), os.path.basename(f), fill=(255, 255, 0))
    ims.append(im)
cols = 4; rows = (len(ims) + cols - 1) // cols
th = ims[0].height
sheet = Image.new('RGB', (cols * (tw + 4), rows * (th + 4)), (30, 30, 30))
for i, im in enumerate(ims): sheet.paste(im, ((i % cols) * (tw + 4), (i // cols) * (th + 4)))
sheet.save(out, quality=85)
print(len(fs), sheet.size)
