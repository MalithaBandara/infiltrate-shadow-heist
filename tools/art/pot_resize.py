"""
Resample over-resolution art down to power-of-two sizes.

Usage:  python tools/art/pot_resize.py <file.png> <W> <H>  [<file.png> <W> <H> ...]
        python tools/art/pot_resize.py --check <file.png>   (report size + POT status only)

Run from the repo root. Rewrites the file in place, so commit or copy first - the
high-resolution original is not kept anywhere else.

WHY POWER OF TWO: KorGE only builds mipmaps for textures that are POT in BOTH
dimensions (`AGTexture.doMipmaps`). It does not warn when that fails; `mipmaps(true)`
just silently does nothing. Without mipmaps, art drawn far smaller than it is stored
gets point-sampled every frame, which is both the shimmer on fine detail and a large
amount of texture memory held for pixels that are never seen.

HOW TO PICK W AND H: take the size the asset is DRAWN at in virtual units, multiply
by 3 (the virtual canvas is 1040x480 but a 1440p phone renders it at 3x), then round
UP to a power of two. Resample to that - never PAD to it; padding becomes part of the
image and gets stretched into the draw box along with the art.

See ".junie/guidelines.md" -> "Adding new art" for the full rule and the exclusion list.

Downsampling straight (non-premultiplied) RGBA is the classic way to get dark or
white halos: fully transparent pixels still carry RGB values, usually black or
white garbage left by the painting tool, and a plain resize averages that garbage
into the colour of every neighbouring edge pixel. So: premultiply -> resize ->
unpremultiply, which averages only the colour that is actually visible.
"""
import os, sys
import numpy as np
from PIL import Image

def resize_premultiplied(im, size):
    im = im.convert("RGBA")
    a = np.asarray(im).astype(np.float64) / 255.0
    alpha = a[..., 3:4]
    rgb_p = a[..., :3] * alpha                      # premultiply
    pm = np.concatenate([rgb_p, alpha], axis=-1)
    pm_img = Image.fromarray((pm * 255.0 + 0.5).astype(np.uint8), "RGBA")
    out = pm_img.resize(size, Image.LANCZOS)
    b = np.asarray(out).astype(np.float64) / 255.0
    oa = b[..., 3:4]
    # unpremultiply; where alpha is 0 the colour is meaningless, leave it black
    rgb = np.divide(b[..., :3], oa, out=np.zeros_like(b[..., :3]), where=(oa > 1e-6))
    rgb = np.clip(rgb, 0.0, 1.0)
    final = np.concatenate([rgb, oa], axis=-1)
    return Image.fromarray((final * 255.0 + 0.5).astype(np.uint8), "RGBA")

def main(argv):
    if not argv:
        print(__doc__)
        return 1

    if argv[0] == "--check":
        for name in argv[1:]:
            im = Image.open(name)
            w, h = im.size
            pot = (w & (w - 1)) == 0 and (h & (h - 1)) == 0
            print("%-40s %5dx%-5d  %6.2f Mpx  %s" %
                  (name, w, h, w * h / 1e6, "POT" if pot else "NOT POT"))
        return 0

    if len(argv) % 3 != 0:
        print("error: expected triples of <file> <W> <H>")
        return 1

    total_old = total_new = 0
    for i in range(0, len(argv), 3):
        path, tw, th = argv[i], int(argv[i + 1]), int(argv[i + 2])
        for v, label in ((tw, "width"), (th, "height")):
            if v & (v - 1) != 0:
                print("error: %s %s %d is not a power of two - mipmaps would be "
                      "silently skipped" % (path, label, v))
                return 1
        im = Image.open(path)
        ow, oh = im.size
        ob = os.path.getsize(path)
        resize_premultiplied(im, (tw, th)).save(path, "PNG", optimize=True)
        nb = os.path.getsize(path)
        total_old += ow * oh
        total_new += tw * th
        print("%-40s %5dx%-5d -> %4dx%-4d   %7.0f KB -> %6.0f KB" %
              (path, ow, oh, tw, th, ob / 1024, nb / 1024))

    print()
    print("pixels: %.2f Mpx -> %.2f Mpx" % (total_old / 1e6, total_new / 1e6))
    print("GPU   : %.1f MB -> %.1f MB (saved %.1f MB; %.1f MB with mipmaps)" % (
        total_old * 4 / 1e6, total_new * 4 / 1e6,
        (total_old - total_new) * 4 / 1e6,
        (total_old - total_new * 1.34) * 4 / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
