"""
Pack the player's and the guards' animation frames into a few ready-made atlas pages, so the game
reads 9 images at launch instead of ~840.

Usage (from the repo root):  python tools/art/pack_sprite_atlases.py

WHY: PlayerAnimations / GuardAnimations used to read every frame as its own PNG and pack it into a
KorGE MutableAtlas at run time - ~840 file reads and decodes, the bulk of a first level's load
(2.3s of the 3.2s GamePreload does on a desktop, measured 2026-10-19). The pages they built are
fixed content, so they are built here instead and the game just decodes them.

SAME PIXELS: each frame is copied in whole, with the 2-pixel border MutableAtlas gives it (its own
edge pixels repeated outward - KorGE's atlas extrudes, and most frames touch their edges, so the
border is what bilinear sampling reads at a frame's edge). The layout differs from KorGE's; nothing
reads it. PNG is lossless and the page is decoded by the same platform decoder each frame was, so
every pixel - and every border pixel - matches what the runtime atlas held. SpriteAtlasTest checks
exactly that, frame by frame, against atlases built the old way from these sources.

SOURCES: art/frames/player/<clip>/NNNN.png and art/frames/guard/<clip>/NNNN.png - tracked, but not
under resources/, so they are no longer bundled into the app. prep_push.py, prep_wind.py and
prep_guard.py write there; re-run this after any of them, or after changing a clip's frame range.

OUTPUT: resources/atlas/<atlas>_<page>.png and resources/atlas/<atlas>.txt - one line per frame,
in load order: "<clip> <file number> <page> <x> <y> <w> <h>" (x, y: the frame's own top-left,
inside its border).

The frame ranges below are PlayerAnimations' and GuardAnimations' *_FRAMES constants (climb starts
at CLIMB_FILE_START; crouchwalk stops at CROUCHWALK_LOOP_END) - the loader checks the counts.
"""
import os
import numpy as np
from PIL import Image

SRC = os.path.join("art", "frames")
OUT = os.path.join("resources", "atlas")
PAGE = 2048
BORDER = 2

# atlas -> [(source folder under art/frames, clip, first file, frame count)], in load order.
ATLASES = {
    "player_core": [
        ("player", "idle", 1, 45),
        ("player", "walk", 1, 40),
        ("player", "jump", 1, 44),
        ("player", "crouch", 1, 34),
        ("player", "crouchwalk", 1, 144),
        ("player", "climb", 70, 224 - 70 + 1),
    ],
    "player_swing": [("player", "swing", 1, 52)],
    "player_push": [("player", "pushtransition", 1, 32), ("player", "push", 1, 40)],
    "player_wind": [("player", "windtransition", 1, 29), ("player", "windwalk", 1, 25)],
    "guard": [("guard", "idle", 1, 48), ("guard", "walk", 1, 38)],
}


def pack(name, clips):
    """Shelf-packs the frames, in order, into PAGE x PAGE pages: rows left to right, a new row when
    one is full, a new page when a row will not fit - the cells are the frame plus its border."""
    pages = []
    lines = []
    page = None
    x = y = row_h = 0
    for root, clip, first, count in clips:
        for n in range(first, first + count):
            path = os.path.join(SRC, root, clip, "%04d.png" % n)
            frame = np.asarray(Image.open(path).convert("RGBA"))
            h, w = frame.shape[:2]
            cw, ch = w + BORDER * 2, h + BORDER * 2
            if page is not None and x + cw > PAGE:
                x, y, row_h = 0, y + row_h, 0
            if page is None or y + ch > PAGE:
                page = np.zeros((PAGE, PAGE, 4), dtype=np.uint8)
                pages.append(page)
                x = y = row_h = 0
            page[y:y + ch, x:x + cw] = np.pad(frame, ((BORDER, BORDER), (BORDER, BORDER), (0, 0)), mode="edge")
            lines.append("%s %d %d %d %d %d %d" % (clip, n, len(pages) - 1, x + BORDER, y + BORDER, w, h))
            x += cw
            row_h = max(row_h, ch)
    for stale in os.listdir(OUT):
        if stale.startswith(name + "_") and stale.endswith(".png"):
            os.remove(os.path.join(OUT, stale))
    for i, p in enumerate(pages):
        Image.fromarray(p, "RGBA").save(os.path.join(OUT, "%s_%d.png" % (name, i)), optimize=True)
    with open(os.path.join(OUT, name + ".txt"), "w", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("%-13s %3d frames -> %d page(s)" % (name, len(lines), len(pages)))


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, clips in ATLASES.items():
        pack(name, clips)


if __name__ == "__main__":
    main()
