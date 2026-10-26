"""
Time the climb clip like a real climb-up, and write Player.CLIMB_PACING_CURVE.

Usage:  python tools/art/prep_climb_pacing.py [--write]

Why: "make the timing for each frame much more realistic by first learning how that action
actually happens in real life" (2026-10-26). The old curve was six straight segments set by eye:
it rushed the pull-up and the swing over the lip (hang to crouched on top in 0.63s, the figure
moving at up to ~6 m/s on screen as the feet came over) and spent 0.4s on standing frames that
barely move.

How a climb-up from a hang actually goes (parkour climb-up and muscle-up coaching; no
peer-reviewed study times it phase by phase, so the durations are within the ranges these give;
upright is reached at 1.66s, where Player lets a held direction walk off - the same moment the old
curve allowed it, so no level's timing changes):
  * catch: the hands take the weight and the body sags until the arms are straight, then a brief
    dead hang - not a rest, a set before the pull,
  * pull: one explosive move - arms pull and a knee drives up - until the chest is at the lip,
  * transition: the slowest point of the whole move (the "sticking point" of a muscle-up), where
    the body rolls over the hands; it slows there but does not stop, then the press throws the
    feet onto the top - "in one explosive motion", pull to landing well under a second,
  * landing: the feet hit and the legs absorb it, then a short crouched balance,
  * stand: slow and controlled compared with the pull, like a brisk squat-to-stand (~0.5-0.6s),
  * settle into standing.
And human point-to-point motion follows a bell-shaped speed profile (minimum jerk): it speeds up,
peaks mid-stroke and eases out, rather than moving at a constant rate with corners between
strokes.

So each phase below is a stroke: its real duration, and the speed it joins its neighbours at - 0
where a person actually stops, part of the stroke's own speed where it flows on (the transition,
the landing). Within a stroke, time follows a quintic (minimum-jerk with those end speeds) along the
figure's on-screen motion - the mean boundary displacement of its silhouette from frame to frame,
placed against the box as the scene draws it (PlayerClimbContact) - so the eye sees speed rise and
fall smoothly instead of in whatever rhythm the source footage was generated with.

Only the pacing changes: every CLIMB_* motion curve and PlayerClimbContact are per frame (phase), so
the body stays where each frame puts it. Re-run after re-cutting the clip or re-solving the contact.
"""
import os
import re
import sys

import numpy as np
from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..'))
FRAMES_DIR = os.path.join(ROOT, 'art', 'frames', 'player', 'climb')
PLAYER_KT = os.path.join(ROOT, 'src', 'game', 'model', 'Player.kt')
CONTACT_KT = os.path.join(ROOT, 'src', 'game', 'scene', 'PlayerClimbContact.kt')

FIRST_RAW = 70                      # PlayerAnimations.CLIMB_FILE_START
FRAME_COUNT = 224 - FIRST_RAW + 1   # PlayerAnimations.CLIMB_FRAMES
DURATION = 1.95                     # Player.climbDuration
NOISE = 0.2                         # px of boundary shimmer on frames that do not move

# (end raw frame, seconds, speed at its end as a fraction of the slower neighbour's mean speed)
STROKES = [
    (82, 0.16, 0.0),   # catch: hands take the weight, body sags to straight arms
    (93, 0.08, 0.0),   # dead hang, setting for the pull
    (135, 0.46, 0.6),  # pull and knee drive until the chest is at the lip - into the sticking point
    (150, 0.25, 0.8),  # roll over the hands and press: feet thrown onto the top, still moving on impact
    (162, 0.13, 0.0),  # landing absorbed into the crouch
    (168, 0.07, 0.0),  # crouched balance
    (200, 0.51, 0.3),  # stand up to upright - Player.CLIMB_WALK_OFF_PHASE, reached at 1.66s
    (224, 0.29, 0.0),  # settle (walked out of whenever a direction is held)
]
TOLERANCE = 0.25                    # frames of error allowed when thinning the keys


def contact_corners():
    src = open(CONTACT_KT, encoding='utf-8').read()
    uv = [int(x) for x in re.findall(r'-?\d+', src.split('shortArrayOf(')[1].split(')')[0])]
    last = (uv[-2], uv[-1])
    return [(uv[2 * f], uv[2 * f + 1]) if 2 * f + 1 < len(uv) else last for f in range(FRAME_COUNT)]


def motion():
    """Per frame step: mean silhouette boundary displacement in px, placed against the box."""
    corners = contact_corners()
    pad = 200
    placed = []
    for f in range(FRAME_COUNT):
        m = np.array(Image.open(os.path.join(FRAMES_DIR, '%04d.png' % (FIRST_RAW + f))).convert('RGBA'))[:, :, 3] > 128
        h, w = m.shape
        can = np.zeros((h + 2 * pad, w + 2 * pad), bool)
        u, v = corners[f]
        ox, oy = pad + w // 2 - u, pad + h // 2 - v
        can[oy:oy + h, ox:ox + w] = m
        placed.append(can)

    def perimeter(m):
        return (m[1:, :] ^ m[:-1, :]).sum() + (m[:, 1:] ^ m[:, :-1]).sum()

    d = []
    for f in range(FRAME_COUNT - 1):
        a, b = placed[f], placed[f + 1]
        d.append(2.0 * (a ^ b).sum() / (perimeter(a) + perimeter(b)))
    return np.maximum(np.array(d) - NOISE, 0.02)


def quintic(tau, a, b):
    """0..1 over tau 0..1 with start/end slopes a/b and no acceleration at either end."""
    t3, t4, t5 = tau ** 3, tau ** 4, tau ** 5
    return (10 * t3 - 15 * t4 + 6 * t5) + a * (tau - 6 * t3 + 8 * t4 - 3 * t5) + b * (-4 * t3 + 7 * t4 - 3 * t5)


def solve():
    step = motion()
    arc = np.concatenate([[0.0], np.cumsum(step)])          # arc[f] = motion up to frame f
    starts = [0] + [s[0] - FIRST_RAW for s in STROKES[:-1]]
    ends = [s[0] - FIRST_RAW for s in STROKES]
    lengths = [arc[e] - arc[s] for s, e in zip(starts, ends)]
    means = [L / s[1] for L, s in zip(lengths, STROKES)]
    joins = [0.0]                                            # speed (px/s) at each stroke boundary
    for i, s in enumerate(STROKES):
        nxt = means[i + 1] if i + 1 < len(STROKES) else means[i]
        joins.append(s[2] * min(means[i], nxt))
    assert abs(sum(s[1] for s in STROKES) - DURATION) < 1e-9, 'strokes must add up to Player.climbDuration'

    times, frames = [0.0], [0.0]
    t0 = 0.0
    for i, s in enumerate(STROKES):
        T, L = s[1], lengths[i]
        a, b = joins[i] * T / L, joins[i + 1] * T / L
        taus = np.linspace(0, 1, 400)
        prog = quintic(taus, a, b)
        assert np.all(np.diff(prog) >= -1e-9), 'stroke %d runs backwards - lower its join speeds' % i
        for tau, p in zip(taus[1:], prog[1:]):
            target = arc[starts[i]] + p * L
            f = np.interp(target, arc, np.arange(FRAME_COUNT))
            times.append(t0 + tau * T)
            frames.append(f)
        t0 += T
    return np.array(times), np.array(frames), step, starts, ends


def thin(times, frames):
    """Fewest (progress, phase) keys that stay within TOLERANCE frames of the solved timing."""
    keep = [0]
    i = 0
    n = len(times)
    while i < n - 1:
        j = i + 2
        while j < n:
            seg = np.interp(times[i:j + 1], [times[i], times[j]], [frames[i], frames[j]])
            if np.max(np.abs(seg - frames[i:j + 1])) > TOLERANCE:
                break
            j += 1
        keep.append(j - 1)
        i = j - 1
    return [(times[k] / DURATION, frames[k] / (FRAME_COUNT - 1)) for k in keep]


def report(times, frames, step, starts, ends):
    per_px = 1.75 / 244.36  # a 1.75m person is 244.36 frame-px tall
    for i, s in enumerate(STROKES):
        sel = (frames >= starts[i]) & (frames <= ends[i])
        t = times[sel]
        print('raw %3d-%3d  %.2fs  starts %.2fs' % (starts[i] + FIRST_RAW, s[0], s[1], t[0]))
    # peak on-screen speed
    fr = np.interp(np.arange(0, DURATION, 1 / 60), times, frames)
    arc = np.concatenate([[0.0], np.cumsum(step)])
    a = np.interp(fr, np.arange(FRAME_COUNT), arc)
    sp = np.diff(a) * 60
    print('peak boundary speed %.0f px/s (%.1f m/s)' % (sp.max(), sp.max() * per_px))


def write(keys):
    body = ',\n'.join('            %.3f, %.3f' % k for k in keys)
    src = open(PLAYER_KT, encoding='utf-8').read()
    new, n = re.subn(r'(CLIMB_PACING_CURVE\s*=\s*doubleArrayOf\(\n)[^)]*(\n\s*\))', lambda m: m.group(1) + body + m.group(2), src)
    assert n == 1, 'CLIMB_PACING_CURVE not found in Player.kt'
    open(PLAYER_KT, 'w', encoding='utf-8', newline='').write(new)


if __name__ == '__main__':
    times, frames, step, starts, ends = solve()
    keys = thin(times, frames)
    report(times, frames, step, starts, ends)
    print('%d keys' % len(keys))
    if '--write' in sys.argv:
        write(keys)
        print('wrote', PLAYER_KT)
    else:
        for k in keys:
            print('%.3f, %.3f' % k)
