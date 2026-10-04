"""Tile a folder of rendered screenshots into one review image."""
import sys, glob, os
from PIL import Image, ImageDraw
d, out, cols, tw = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
fs = sorted(glob.glob(os.path.join(d, '*.png')))
ims = []
for f in fs:
    im = Image.open(f).convert('RGB'); im = im.resize((tw, round(im.height * tw / im.width)))
    ImageDraw.Draw(im).text((6, 6), os.path.basename(f), fill=(255, 255, 0)); ims.append(im)
rows = (len(ims) + cols - 1) // cols; th = ims[0].height
sheet = Image.new('RGB', (cols * (tw + 8), rows * (th + 8)), (60, 60, 60))
for i, im in enumerate(ims): sheet.paste(im, ((i % cols) * (tw + 8), (i // cols) * (th + 8)))
sheet.save(out, quality=88); print(sheet.size)
