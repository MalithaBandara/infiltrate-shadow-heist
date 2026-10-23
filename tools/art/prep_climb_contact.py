"""
Solve where the climbed box's top corner sits in every climb frame, and write the table to
src/game/scene/PlayerClimbContact.kt.

Usage:  python tools/art/prep_climb_contact.py [--sheet out.png]

Why: "it looks like he is going inside the platform and not touching the top part" (2026-10-23).
Player places the body off CLIMB_GRIP_CURVE, which puts the TOP of the raised hands on the deck
line - so the cupped hands were drawn inside the box's corner, and the hanging body's toes, which
reach nearly as far forward as the fingertips, went into its face. No offset from the collision box
fixes that across the clip: what touches the box changes every few frames (hands on the lip, then
a knee coming over, then hands and feet on the deck). So the contact is solved off the pixels.

The box's top corner is a point (u, v) in the frame's own pixels (frames face RIGHT; the scene
mirrors them): the box is everything right of u and below v. For every frame the solver picks the
point that costs least, where it costs
  * OVERLAP per silhouette pixel inside the box (the figure going into the platform),
  * GAP per pixel between the deck and the lowest thing over it (hands hovering over the top),
  * GRIP per pixel the leading hand reaches less than GRIP_DEPTH past the lip, and HOOK per pixel
    the deck sits more than HOOK_DEPTH under the hand's top - the fingers hook over the top on the
    hang, the palms press on it through the push-up and the crouch, never against its front. The
    hand is followed frame to frame (track_hands), because from ~raw 123 the raised knee and the
    shoulder reach as far forward as it and sit higher,
  * PRIOR per pixel away from where Player's own curves put the corner (ledge 96, mid-frame), so
    the body still moves the way the climb was tuned - growing to PRIOR_END over the mantle, so the
    path ends where Player does and the body's move onto the deck is spread over the mantle
    rather than popping when the dangling feet clear the lip,
and a path through all frames is chosen with SMOOTH_U/SMOOTH_V per squared pixel of change between frames (dynamic
programming), so the figure never jitters from one contact to the next.

The scene draws the figure with this point on the real box corner (the deck drawn at the crate's
visual top), never with the feet below where they started (a ledge under 96 leaves the hands over
the top rather than sinking him into the floor), and hands back to Player's own placement over
FADE_FROM..FADE_TO - by then he is crouched on the deck and the two agree to a few tenths.

Re-run after re-cutting the climb clip or changing Player's CLIMB_* curves (read from Player.kt).
"""
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'src', 'game', 'scene', 'PlayerClimbContact.kt')
FRAMES_DIR = os.path.join(ROOT, 'art', 'frames', 'player', 'climb')
PLAYER_KT = os.path.join(ROOT, 'src', 'game', 'model', 'Player.kt')

FIRST_RAW = 70                      # PlayerAnimations.CLIMB_FILE_START
FRAME_COUNT = 224 - FIRST_RAW + 1   # PlayerAnimations.CLIMB_FRAMES

HEIGHT, WIDTH = 96.0, 36.0           # Player's box
SCALE = HEIGHT * 1.05 / 244.36       # world units per frame px (VISUAL_HEIGHT_SCALE / SOURCE_SILHOUETTE_HEIGHT)
FEET_ANCHOR = 255.76 / 256.0         # SOURCE_FEET_Y / SOURCE_FRAME_HEIGHT
IDLE_OFFSET = (255.76 - 248.0) * SCALE
LANDING_IN = 6.0                     # Player.climbLandingX
LEDGE = 96.0

# Where the solved placement hands back to Player's, in raw frames (crouched on the deck).
FADE_FROM, FADE_TO = 156, 166

OVERLAP, GAP, GRIP, HOOK, PRIOR = 1.0, 4.0, 3.0, 4.0, 0.25
# The prior grows to PRIOR_END by FADE_FROM, so the solved path arrives where Player puts him and
# the hand-over to Player's placement is no slide. It starts growing at PRIOR_RAMP_FROM (raw).
PRIOR_END, PRIOR_RAMP_FROM = 4.0, 130
# The push-off: from PUSH_FROM (raw) the hands may leave the deck at PUSH_GAP per pixel (GAP and
# HOOK both), so the body
# can rise ahead of them and swing the feet (pinned to the frame's bottom row) over the lip.
PUSH_FROM, PUSH_GAP = 140, 0.3
SMOOTH_U, SMOOTH_V = 0.5, 0.5         # per px^2 of frame-to-frame change, across / up
GRIP_DEPTH = 4                       # px (~1.6 units) of fingertip over the lip
HOOK_DEPTH = 4                       # px under the hand's top that the deck may sit: the curled fingers
WINDOW = 90                          # px either way from Player's corner that is searched


def read_curve(name):
    src = open(PLAYER_KT, encoding='utf-8').read()
    m = re.search(name + r'\s*=\s*doubleArrayOf\(([^)]*)\)', src)
    return [float(x) for x in m.group(1).replace('\n', ' ').split(',') if x.strip()]


def curve_at(c, t):
    last = len(c) - 2
    if t <= c[0]:
        return c[1]
    if t >= c[last]:
        return c[last + 1]
    i = 0
    while i + 2 < last and t > c[i + 2]:
        i += 2
    t0, t1 = c[i], c[i + 2]
    if t1 <= t0:
        return c[i + 3]
    u = min(max((t - t0) / (t1 - t0), 0.0), 1.0)
    return c[i + 1] + (c[i + 3] - c[i + 1]) * u


GRIP_CURVE = read_curve('CLIMB_GRIP_CURVE')
RISE_CURVE = read_curve('CLIMB_RISE_CURVE')
SHIFT_CURVE = read_curve('CLIMB_SHIFT_CURVE')


def model_corner(f, fw, fh):
    """The box corner, in frame px, where Player + the scene's old placement put it (ledge 96)."""
    ph = (f + 0.5) / (FRAME_COUNT - 1)
    bx, by = 0.0, -LEDGE
    sx, sy = bx - WIDTH, -HEIGHT
    tx, ty = bx + LANDING_IN, by - HEIGHT
    total = sy - ty
    grip = max(total - curve_at(GRIP_CURVE, ph) * HEIGHT, 0.0)
    pull = curve_at(RISE_CURVE, ph) * total
    px = sx + (tx - sx) * curve_at(SHIFT_CURVE, ph)
    py = sy - max(grip, pull)
    stand = min(max((ph - 0.60) / 0.25, 0.0), 1.0) * IDLE_OFFSET
    cx, fy = px + WIDTH / 2, py + HEIGHT + stand
    ax, ay = fw * 0.5, fh * FEET_ANCHOR
    return ax + (bx - cx) / SCALE, ay + (by - fy) / SCALE


def load(f):
    a = np.asarray(Image.open(os.path.join(FRAMES_DIR, f'{FIRST_RAW + f:04d}.png')).getchannel('A')) > 128
    return a


def track_hands(masks):
    """The leading hand in every frame, followed from raw 70's fingertips: (x, y) of its front
    pixel and the top row of the hand. Followed rather than found, because from ~raw 123 the
    raised knee and the shoulder reach as far forward as the hand and sit higher."""
    hx, hy = None, None
    out = []
    for m in masks:
        ys, xs = np.nonzero(m)
        if hx is None:
            k = np.argmax(xs * 1000 - ys)
        else:
            near = (np.abs(xs - hx) <= 12) & (ys - hy >= -10) & (ys - hy <= 18)
            k = np.argmax(np.where(near, xs * 1000 - ys, -10 ** 9))
        hx, hy = int(xs[k]), int(ys[k])
        blob = (xs >= hx - 8) & (np.abs(ys - hy) <= 10)
        out.append((hx, hy, int(ys[blob].min())))
    return out


def unary(mask, raw, hand, mx, my):
    h, w = mask.shape
    # suffix counts: Q[v, u] = pixels with x >= u and y >= v
    q = np.zeros((h + 1, w + 1))
    q[:h, :w] = mask[::-1, ::-1].cumsum(0).cumsum(1)[::-1, ::-1]
    # lowest bottom edge among columns >= u
    low = np.where(mask.any(0), h - np.argmax(mask[::-1], 0), -1)
    stair = np.maximum.accumulate(np.append(low, -1)[::-1])[::-1]
    hand_x, _, hand_top = hand
    ramp = min(max((raw - PRIOR_RAMP_FROM) / (FADE_FROM - PRIOR_RAMP_FROM), 0.0), 1.0)
    prior = PRIOR + (PRIOR_END - PRIOR) * ramp
    cx0, cy0 = int(round(mx)), int(round(my))
    n = 2 * WINDOW + 1
    cost = np.full((n, n), 1e9)
    for j in range(n):          # v
        v = cy0 - WINDOW + j
        if not 0 <= v <= h:
            continue
        for i in range(n):      # u
            u = cx0 - WINDOW + i
            if not 0 <= u <= w:
                continue
            inside = q[v, u]
            gap = 0.0 if inside > 0 else (v - stair[u] if stair[u] >= 0 else h)
            c = OVERLAP * inside + (PUSH_GAP if raw >= PUSH_FROM else GAP) * gap
            c += GRIP * max(0, GRIP_DEPTH - (hand_x - u))
            c += (PUSH_GAP if raw >= PUSH_FROM else HOOK) * max(0, v - (hand_top + HOOK_DEPTH))
            c += prior * (abs(u - mx) + abs(v - my))
            cost[j, i] = c
    return cost, (cx0 - WINDOW, cy0 - WINDOW)


def relax(d, lams, origin_from, origin_to):
    """min over q of d(q) + lam_u*(pu-qu)^2 + lam_v*(pv-qv)^2, p and q in two offset windows.

    Squared, not absolute: an absolute step cost charges one big jump the same as many small
    ones, so it never spreads a move out - the body popped onto the deck in one frame."""
    n = d.shape[0]
    ox, oy = origin_to[0] - origin_from[0], origin_to[1] - origin_from[1]
    idx = np.arange(n)
    # along u (axis 1): p index i in the new window is column i + ox of the old one
    du = (idx[:, None] + ox) - idx[None, :]          # [p, q]
    a = np.min(d[:, None, :] + lams[0] * du[None, :, :] ** 2, axis=2)
    dv = (idx[:, None] + oy) - idx[None, :]
    return np.min(a.T[:, None, :] + lams[1] * dv[None, :, :] ** 2, axis=2).T


def solve():
    masks = [load(f) for f in range(FRAME_COUNT)]
    last = FADE_TO - FIRST_RAW
    hands = track_hands(masks[:last + 1])
    costs, origins = [], []
    for f in range(last + 1):
        h, w = masks[f].shape
        mx, my = model_corner(f, w, h)
        c, o = unary(masks[f], FIRST_RAW + f, hands[f], mx, my)
        costs.append(c)
        origins.append(o)
    acc = [costs[0]]
    for f in range(1, len(costs)):
        acc.append(costs[f] + relax(acc[-1], (SMOOTH_U, SMOOTH_V), origins[f - 1], origins[f]))
    n = costs[0].shape[0]
    j, i = np.unravel_index(np.argmin(acc[-1]), acc[-1].shape)
    path = [(origins[-1][0] + i, origins[-1][1] + j)]
    for f in range(len(costs) - 2, -1, -1):
        u1, v1 = path[-1]
        ox, oy = origins[f]
        us = ox + np.arange(n)
        vs = oy + np.arange(n)
        tot = acc[f] + SMOOTH_U * (us[None, :] - u1) ** 2 + SMOOTH_V * (vs[:, None] - v1) ** 2
        j, i = np.unravel_index(np.argmin(tot), tot.shape)
        path.append((ox + i, oy + j))
    path.reverse()
    return masks, path


def report(masks, path):
    print(' raw    u    v  inside  gap  grip  step')
    for f, (u, v) in enumerate(path):
        pu, pv = path[f - 1] if f else (u, v)
        m = masks[f]
        h, w = m.shape
        inside = int(m[v:, u:].sum())
        over = m[:v, u:]
        rows = np.nonzero(over.any(1))[0]
        gap = (v - (rows.max() + 1)) if inside == 0 and len(rows) else 0
        front = int(np.nonzero(m.any(0))[0].max())
        step = abs(u - pu) + abs(v - pv)
        if f % 4 == 0 or inside > 30 or step > 10:
            print(f' {FIRST_RAW + f:3d} {u:4d} {v:4d} {inside:7d} {gap:4d} {front - u:5d} {step:5d}')


def write(path, frame_size):
    flat = ', '.join(f'{u}, {v}' for u, v in path)
    lines = []
    items = flat.split(', ')
    for k in range(0, len(items), 16):
        lines.append('        ' + ', '.join(items[k:k + 16]) + ',')
    lines[-1] = lines[-1].rstrip(',')
    body = '\n'.join(lines)
    src = f'''package game.scene

// GENERATED by tools/art/prep_climb_contact.py - do not edit by hand; re-run it after re-cutting the
// climb clip or changing Player's CLIMB_* curves.

/**
 * Where the climbed box's top corner sits in each climb frame, as (u, v) pixel pairs in the frame's
 * own space (frames face right; the box is everything right of u and below v), indexed by climb
 * frame from [PlayerAnimations.CLIMB_START]. Solved off the pixels so nothing of the figure is
 * drawn inside the box and the hands - later the knees and feet - rest on its top - see the script's
 * header. Frames from [FADE_FROM] hand back to Player's own placement, finishing at [FADE_TO];
 * past it there is no entry.
 */
object PlayerClimbContact {{
    /** Every climb frame's size in pixels; the sprite anchors at (0.5, SOURCE_FEET_Y / 256) of it. */
    const val FRAME_WIDTH = {frame_size[1]}
    const val FRAME_HEIGHT = {frame_size[0]}

    const val FADE_FROM = {FADE_FROM - FIRST_RAW}
    const val FADE_TO = {FADE_TO - FIRST_RAW}

    private val UV = shortArrayOf(
{body}
    )

    /** How much of the drawn placement comes from this table at climb frame [frame]: 1 down to 0. */
    fun weight(frame: Int): Double = when {{
        frame <= FADE_FROM -> 1.0
        frame >= FADE_TO -> 0.0
        else -> (FADE_TO - frame).toDouble() / (FADE_TO - FADE_FROM)
    }}

    fun u(frame: Int): Int = UV[2 * frame.coerceIn(0, FADE_TO)].toInt()
    fun v(frame: Int): Int = UV[2 * frame.coerceIn(0, FADE_TO) + 1].toInt()
}}
'''
    with open(OUT, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(src)
    print('wrote', os.path.relpath(OUT, ROOT), len(path), 'frames')


def sheet(masks, path, out, ledge=LEDGE, every=7):
    """Each sampled frame drawn with its corner on a box of [ledge], feet clamped to the ground."""
    px = 4
    picks = list(range(0, len(path), every))
    cw, ch = 90 * px, int((ledge + 70) * px)
    img = Image.new('RGB', (cw * len(picks), ch), (235, 235, 235))
    d = ImageDraw.Draw(img)
    for k, f in enumerate(picks):
        u, v = path[f]
        m = masks[f]
        h, w = m.shape
        ax, ay = w * 0.5, h * FEET_ANCHOR
        cx = 0.0 - (u - ax) * SCALE
        fy = min(-ledge - (v - ay) * SCALE, 0.0)
        ox = k * cw
        to = lambda x, y: (ox + int((x + 50) * px), int((y + ledge + 60) * px))
        d.rectangle([to(0, -ledge), to(40, 0)], fill=(150, 120, 90))
        d.line([to(-50, 0), to(40, 0)], fill=(0, 120, 0), width=2)
        im = Image.open(os.path.join(FRAMES_DIR, f'{FIRST_RAW + f:04d}.png'))
        s = SCALE * px
        im = im.resize((int(w * s), int(h * s)), Image.LANCZOS)
        red = Image.new('RGBA', im.size, (200, 0, 0, 0))
        red.putalpha(im.getchannel('A'))
        x0, y0 = to(cx, fy)
        img.paste(red, (int(x0 - ax * s), int(y0 - ay * s)), red)
        d.text((ox + 4, 4), f'raw {FIRST_RAW + f}', fill=(0, 0, 0))
        d.line([(ox + cw - 1, 0), (ox + cw - 1, ch)], fill=(0, 0, 0))
    img.save(out)


if __name__ == '__main__':
    masks, path = solve()
    report(masks, path)
    sizes = {m.shape for m in masks}
    assert len(sizes) == 1, f'climb frames differ in size: {sizes}'
    write(path, masks[0].shape)
    if '--sheet' in sys.argv:
        out = sys.argv[sys.argv.index('--sheet') + 1]
        sheet(masks, path, out)
        sheet(masks, path, out.replace('.png', '_111.png'), ledge=111.0)
        sheet(masks, path, out.replace('.png', '_72.png'), ledge=72.0)
