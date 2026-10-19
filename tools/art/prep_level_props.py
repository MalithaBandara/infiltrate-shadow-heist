"""
Shrink the art that is stored far larger than it is ever drawn to small power-of-two images, and
check - the way the GPU actually samples them - that the result looks the same.

Usage (from the repo root):  python tools/art/prep_level_props.py           (write + verify)
                             python tools/art/prep_level_props.py --verify  (verify only)

WHY: every file here was stored 2-14x larger than its largest on-screen size, and none was a power
of two. Two kinds:
 - CROPPED PLATES (woodcrate2, pole, the camera, the stars): drawn through a hard-coded pixel crop of
   a big plate. Resizing the plate in place would have moved every crop, so each crop is cut out
   HERE, at exactly the pixel rect the scene used, and resampled on its own. The scene draws each
   file whole into the same box it drew the crop into, and every position it derives from the plate
   (the camera's pivot and attach points, the stars' aspect) stays in the plate's own pixel units.
 - WHOLE IMAGES (the win/fail buttons, hook, levers, rope + its dissolve frames): drawn whole into a
   box, so they are simply resampled; nothing in the scene reads their pixel size (hook.png's aspect
   is a literal in LevelData, and Player's grip point is a fraction of the hook rect).

LOAD THEM WITH minified = false (NO MIPMAPS) - measured, not a guess. Each target is under 2x its
largest draw box, where plain bilinear sampling is already alias-free. Trilinear mipmapping picks
its level from the MOST-minified axis and blends in a 2x2-box-filtered level, which on these sizes
measured 30-40% softer than the ideal resample on detailed art (the buttons, crate, pole, stars) -
a visible change. Bilinear on the new file lands closer to the ideal than the old oversized file
did on every device size checked (the old one, non-POT and so never mipmapped, aliased: too much
"detail" is shimmer). verify() below emulates exactly that: bilinear, clamp-to-edge, premultiplied.

SIZES (2026-10-08): each target is the largest box the scene draws it into, over every level, in
1440p-phone pixels (UI virtual units x 3; world units x worldZoom 1.35 x 3), rounded UP to a power
of two; a 2732-px iPad Pro draws ~14% bigger and still fits:
    woodcrate2.png      crop 1206x721   drawn <= 289x194  -> 512x256
    pole.png            crop 170x1496   drawn <=  73x1175 -> 128x2048
    camera_mount.png    crop 588x438    drawn <=  45x34   ->  64x64   (cameranew2.png's mount)
    camera_lens.png     crop 1134x492   drawn <=  87x38   -> 128x64   (cameranew2.png's lens)
    star1-3.png         crops of stars.png ~640x611, drawn <= 326x313 at the 2x pop -> 512x512
    victorybutton1-3    1972..2023 x 777..798, drawn 175x56 units -> 525x168 -> 1024x256
    failedbutton1-3     2172x724, drawn 175x62 units -> 525x186 -> 1024x256
    hook.png            154x2136, drawn 16x221.9 world -> 65x899 -> 128x1024 (the iPad's 1025 is
                        within the rule's couple of percent; it is also the mip level a 2048 file
                        would have been sampled from there)
    lever_bottom.png    1140x605, drawn 22x12 world   -> 89x49  -> 128x64
    lever_top.png       204x1284, drawn 3.2x20 world  -> 13x81  -> 16x128
    newrope.png, rope_dissolve_0..8.png  251x926, drawn 13 x (ropeLength + 15) world, longest
                        109 (levels 8/9) -> 53x441 -> 64x512
Not done, and why: crane.png's cab is drawn LARGER than its pixels (1341x592 px from 1023x452);
chainedcrate*.png are drawn stretched across a box up to 2240x600 world units; failedscreen.png and
success3.png (the result cards) are drawn larger than their pixels on a phone already; the
backgrounds fill the screen. rope.png is only a fallback for a missing newrope.png and never loads.

The full-resolution originals live in art-source/props/ (gitignored; also in git history). Re-run
this after changing any of them, or after the scene draws one of these bigger (re-measure first).
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from pot_resize import resize_premultiplied

SRC = "art-source/props/"
STARS = SRC + "stars.png"

# output -> (source, crop (x, y, w, h) exactly as GameplayScene.kt used it or None for the whole
#            image, target size, largest 1440p-phone draw box in px, as measured above)
PROPS = {
    "woodcrate2.png": (SRC + "woodcrate2.png", (165, 144, 1206, 721), (512, 256), (289, 194)),
    "pole.png": (SRC + "pole.png", (426, 9, 170, 1496), (128, 2048), (73, 1175)),
    "camera_mount.png": (SRC + "cameranew2.png", (89, 54, 588, 438), (64, 64), (45, 34)),
    "camera_lens.png": (SRC + "cameranew2.png", (285, 486, 1134, 492), (128, 64), (87, 38)),
    # GameplayScene's STAR_CROPS. Drawn winCardH * 0.115 tall (<= 52.2 units) at rest, popping in
    # from 2x.
    "star1.png": (STARS, (69, 33, 636, 611), (512, 512), (326, 313)),
    "star2.png": (STARS, (760, 33, 647, 611), (512, 512), (331, 313)),
    "star3.png": (STARS, (1464, 33, 641, 611), (512, 512), (328, 313)),
}
for i in (1, 2, 3):
    PROPS["victorybutton%d.png" % i] = (SRC + "victorybutton%d.png" % i, None, (1024, 256), (525, 168))
    PROPS["failedbutton%d.png" % i] = (SRC + "failedbutton%d.png" % i, None, (1024, 256), (525, 186))
PROPS["hook.png"] = (SRC + "hook.png", None, (128, 1024), (65, 899))
PROPS["lever_bottom.png"] = (SRC + "lever_bottom.png", None, (128, 64), (89, 49))
PROPS["lever_top.png"] = (SRC + "lever_top.png", None, (16, 128), (13, 81))
for rope in ["newrope.png"] + ["rope_dissolve_%d.png" % i for i in range(9)]:
    PROPS[rope] = (SRC + rope, None, (64, 512), (53, 441))

# The device sizes checked, as a factor on the 1440p-phone box: the phone itself, a 2732-px iPad
# Pro, a 1080p phone and a 720p one.
DEVICES = (("1440p", 1.0), ("ipadPro", 1.14), ("1080p", 0.75), ("720p", 0.5))


def crop(name):
    src, rect, _, _ = PROPS[name]
    im = Image.open(src).convert("RGBA")
    if rect is None:
        return im
    x, y, w, h = rect
    return im.crop((x, y, x + w, y + h))


def premul(im):
    a = np.asarray(im.convert("RGBA")).astype(np.float64)
    a[..., :3] *= a[..., 3:4] / 255.0
    return a


def gpu_bilinear(tex, bw, bh):
    """What a non-mipmapped GL_LINEAR draw of premultiplied [tex] into a bw x bh box produces:
    one bilinear tap at each pixel centre, clamped to the edge."""
    H, W = tex.shape[:2]
    xs = (np.arange(bw) + 0.5) * W / bw - 0.5
    ys = (np.arange(bh) + 0.5) * H / bh - 0.5
    x0 = np.floor(xs).astype(int); fx = (xs - x0)[None, :, None]
    y0 = np.floor(ys).astype(int); fy = (ys - y0)[:, None, None]
    x1 = np.clip(x0 + 1, 0, W - 1); x0 = np.clip(x0, 0, W - 1)
    y1 = np.clip(y0 + 1, 0, H - 1); y0 = np.clip(y0, 0, H - 1)
    top = tex[y0][:, x0] * (1 - fx) + tex[y0][:, x1] * fx
    bottom = tex[y1][:, x0] * (1 - fx) + tex[y1][:, x1] * fx
    return top * (1 - fy) + bottom * fy


def verify():
    """Draw the original (crop) and the new file into each device's box the way the GPU does -
    bilinear, no mipmaps: the old files were never POT so never got any, and the new ones are
    loaded with minified = false - and compare both to the ideal (a premultiplied LANCZOS
    downsample of the original). On every device the new file has to land no further from the
    ideal than the old one did, or within 1/255 of it anyway. (No absolute bar: one bilinear tap
    per pixel at 1.5-2x minification sits ~1.5/255 off a LANCZOS on fine paper texture like the
    buttons' however the file is sized - the old files were 3-5/255 off.)"""
    ok = True
    for name, (_, _, size, box) in PROPS.items():
        orig_im = crop(name)
        new_im = Image.open(os.path.join("resources", name)).convert("RGBA")
        assert new_im.size == size, (name, new_im.size, size)
        orig, new = premul(orig_im), premul(new_im)
        cells = []
        for label, f in DEVICES:
            bw, bh = max(1, round(box[0] * f)), max(1, round(box[1] * f))
            ideal = premul(resize_premultiplied(orig_im, (bw, bh)))
            e_old = np.abs(gpu_bilinear(orig, bw, bh) - ideal).mean()
            e_new = np.abs(gpu_bilinear(new, bw, bh) - ideal).mean()
            cells.append("%s %.2f->%.2f" % (label, e_old, e_new))
            if e_new > max(e_old, 1.0):
                ok = False
        print("%-21s %4dx%-4d  error/255 old->new  %s" % (name, size[0], size[1], "  ".join(cells)))
    print("OK" if ok else "FAILED: a new file further from the ideal than the old one, by over 1/255")
    return ok


def main(argv):
    if "--verify" not in argv:
        for name, (_, _, size, _) in PROPS.items():
            resize_premultiplied(crop(name), size).save(os.path.join("resources", name), optimize=True)
            print("wrote resources/%s %dx%d" % (name, size[0], size[1]))
    return 0 if verify() else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
