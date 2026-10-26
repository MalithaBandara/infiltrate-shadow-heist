"""Cut the lift / switch / lever / hanging-crate chain / mission fail + success / powerup sounds from the Pixabay downloads in Downloads/charAnimations/music3.

  loop_elevator.wav  22.05kHz mono, equal-power-crossfaded loop of the two steady running stretches
                     of the 35s source that have mid-range body (0.9-3.1s and 4.4-7.1s). The 10-25s
                     stretch is almost all sub-300Hz rumble, which a phone speaker does not play, so
                     it was inaudible; the ding / dips at 3.2-4.1s, 7.2-10s, 25-28.5s are left out.
                     High-passed at 70Hz, loud (-14dB RMS) so ELEVATOR_LOOP_GAIN lands it level with
                     the fan / steam beds.
  switch_press.wav   44.1kHz mono one-shot, the click at 0.08-0.40s of the light-switch source,
                     soft-clipped up so its body (not just the peak) carries.
  lever_throw.wav    44.1kHz mono one-shot, both clacks (0.05-0.62s) of the breaker-switch source,
                     soft-clipped up the same way.
  loop_chain.wav     32kHz mono, ~12s bed of the chains take's loud drag (1.00-2.62s): grains of
                     the take's own drag, 0.18-0.45s, cut at its quietest instants between clanks,
                     from either mic of the wide stereo take (correlation 0.15 - two takes'
                     worth), in a seeded shuffle that never reuses a stretch within 2.5s, joined
                     with 30ms equal-power crossfades and levelled only halfway to the median, so
                     the drag keeps its own swell; +3dB under 800Hz for the weight one mic lacks;
                     no pitch shifting. A heavy load at speed. RMS -18dBFS.
  loop_chain_light.wav  32kHz mono, the same from the calmer drag and the build-up (2.62-3.66s,
                     0.42-0.96s): links running at a crawl. RMS -22dBFS.
  chain_clank_1..3.wav  44.1kHz mono one-shots: the take's three cleanest heavy jolts (1.00, 2.11,
                     2.25s), the rattle after each dying away in ~0.3s - a load starting off.
  chain_clink_1..3.wav  44.1kHz mono one-shots: light clinks (0.83, 0.89, 2.80s), ~0.2s - a
                     swinging load turning round.
  chain_settle.wav   44.1kHz mono one-shot: the take's own slow-down and settle (3.40-4.40s) - a load
                     coming to rest. (Until 2026-10-27 the chains were one 22kHz loop of 11 phrases,
                     pitch-shifted and heavily levelled: "the moving crate sounds are very unnatural".)
  mission_fail.wav   44.1kHz mono one-shot, 0-2.0s of the cartoon-defeat stinger (its tail faded out).
  mission_success.wav  44.1kHz mono one-shot, 0-1.3s of the level-up chime (tail faded out).
  door_slide.wav     44.1kHz mono one-shot, 0.15-1.20s of the jail-door source: the sliding rumble only. The
                     source's clang/thud lands at 1.25s and its low rumble tail follows; both are left out.
  shutter_open.wav   44.1kHz mono one-shot, 0.08-1.00s of the sliding-cabinet-door source (its opening slide).
  shutter_close.wav  44.1kHz mono one-shot, 3.40-4.40s of the same source: the last of the close slide, then its
                     bang ~0.5s in - timed so the bang lands as the 0.45s shutter reaches the floor.
  powerup_activate.wav 44.1kHz mono one-shot, 0.2-1.2s of the interface blip; the source is quiet, so it
                     is peak-normalised here and POWERUP_ACTIVATE_GAIN keeps it low.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

BIN = (r"C:\Users\USER\AppData\Local\Microsoft\WinGet\Packages"
       r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
       r"\ffmpeg-9.0.1-full_build\bin")
FFMPEG = os.path.join(BIN, "ffmpeg.exe")
SRC = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\USER\Downloads\charAnimations\music3"
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "resources", "sfx")


def read(name, rate):
    with tempfile.TemporaryDirectory() as d:
        tmp = os.path.join(d, "x.wav")
        subprocess.run([FFMPEG, "-v", "error", "-y", "-i", os.path.join(SRC, name),
                        "-ac", "1", "-ar", str(rate), tmp], check=True)
        with wave.open(tmp) as w:
            return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768.0


def write(name, x, rate):
    x = np.clip(x, -1.0, 1.0)
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes((x * 32767.0).astype("<i2").tobytes())


def one_shot(x, rate, start, end, peak, fade_in=0.004, fade_out=0.03):
    seg = x[int(start * rate):int(end * rate)].copy()
    fi, fo = int(fade_in * rate), int(fade_out * rate)
    seg[:fi] *= np.linspace(0.0, 1.0, fi)
    seg[-fo:] *= np.linspace(1.0, 0.0, fo)
    return seg * (peak / np.abs(seg).max())


def highpass(x, rate, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / rate)
    spec *= np.clip((f - hz * 0.5) / (hz * 0.5), 0.0, 1.0)
    return np.fft.irfft(spec, len(x))


def crossfade_join(a, b, c):
    t = np.linspace(0.0, 1.0, c)
    return np.concatenate([a[:-c], a[-c:] * np.cos(t * np.pi / 2) + b[:c] * np.sin(t * np.pi / 2), b[c:]])


def loop(x, rate, spans, xfade, rms_db, hp_hz):
    segs = [highpass(x[int(a * rate):int(b * rate)] - x[int(a * rate):int(b * rate)].mean(), rate, hp_hz) for a, b in spans]
    c = int(xfade * rate)
    seg = segs[0]
    for nxt in segs[1:]:
        seg = crossfade_join(seg, nxt, c)
    n = len(seg)
    t = np.linspace(0.0, 1.0, c)
    out = seg[:n - c].copy()
    out[:c] = seg[:c] * np.sin(t * np.pi / 2) + seg[n - c:] * np.cos(t * np.pi / 2)
    out *= 10 ** (rms_db / 20) / np.sqrt((out ** 2).mean())
    return out


def punch(x, drive, peak):
    y = np.tanh(x * drive)
    return y * (peak / np.abs(y).max())


def resample(x, pitch):
    new_len = int(len(x) / pitch)
    return np.interp(np.linspace(0, len(x) - 1, new_len), np.arange(len(x)), x)


def read_stereo(path, rate):
    with tempfile.TemporaryDirectory() as d:
        tmp = os.path.join(d, "x.wav")
        subprocess.run([FFMPEG, "-v", "error", "-y", "-i", os.path.join(SRC, path), "-ac", "2", "-ar", str(rate), tmp], check=True)
        with wave.open(tmp) as w:
            x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768.0
    return x.reshape(-1, 2)


def low_shelf(x, rate, db, full_below, flat_above):
    """+db under full_below Hz easing to 0 at flat_above: one mic of the take is lighter below 2kHz
    than the two summed (their lows add in phase, their highs do not), and a bed is cut from one."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / rate)
    g = np.clip((flat_above - f) / (flat_above - full_below), 0.0, 1.0) * db
    spec *= 10 ** (g / 20)
    return np.fft.irfft(spec, len(x))


def rms(a):
    return float(np.sqrt((a ** 2).mean())) if len(a) else 0.0


def soft_peak(x, ceiling):
    """Leaves everything under ceiling*0.7 alone; rounds off only the rare peaks above it."""
    knee = ceiling * 0.7
    y = x.copy()
    over = np.abs(y) > knee
    y[over] = np.sign(y[over]) * (knee + (ceiling - knee) * np.tanh((np.abs(y[over]) - knee) / (ceiling - knee)))
    return y


def quiet_points(x, rate, a, b):
    """Sample indices of the quietest 4ms instants between a and b seconds (local minima of a 4ms RMS)."""
    hop = int(0.002 * rate)
    win = int(0.004 * rate)
    lo, hi = int(a * rate), int(b * rate)
    env = np.array([rms(x[i:i + win]) for i in range(lo, hi - win, hop)])
    pts = []
    for k in range(2, len(env) - 2):
        if env[k] <= env[k - 1] and env[k] <= env[k + 1] and env[k] <= env[k - 2] and env[k] <= env[k + 2]:
            pts.append((lo + k * hop + win // 2, env[k]))
    return pts


def grains(x, rate, regions, min_len, max_len):
    """Every stretch between two of the take's quiet instants that is min_len..max_len long."""
    out = []
    for a, b in regions:
        pts = quiet_points(x, rate, a, b)
        if not pts:
            continue
        thr = np.median([v for _, v in pts])
        cuts = [i for i, v in pts if v <= thr]
        for k, start in enumerate(cuts):
            for end in cuts[k + 1:]:
                n = end - start
                if n < min_len * rate:
                    continue
                if n > max_len * rate:
                    break
                out.append((start, end))
    return out


def texture(channels, rate, regions, seconds, target_db, seed, xfade=0.03, min_len=0.18, max_len=0.45):
    """
    A bed of the take's own drag: grains from either channel of the stereo take (two mics, nearly
    uncorrelated - two takes' worth of material), each cut between quiet instants, picked at random
    but never overlapping a stretch of the same channel used in the last few seconds.
    """
    rng = np.random.default_rng(seed)
    pool = []
    for ch, x in enumerate(channels):
        for a, b in grains(x, rate, regions, min_len, max_len):
            pool.append((ch, a, b))
    levels = np.array([rms(channels[ch][a:b]) for ch, a, b in pool])
    med = np.median(levels)
    c = int(xfade * rate)
    fade_in = np.sin(np.linspace(0.0, 1.0, c) * np.pi / 2)
    fade_out = np.cos(np.linspace(0.0, 1.0, c) * np.pi / 2)
    order = []
    used = []  # (channel, a, b, at output sample)
    total = 0
    need = int((seconds + xfade) * rate)
    memory = int(2.5 * rate)
    while total < need:
        recent = [(ch, a, b) for ch, a, b, at in used if total - at < memory]
        ok = [i for i, (ch, a, b) in enumerate(pool)
              if not any(ch == rc and a < rb and b > ra for rc, ra, rb in recent)]
        if not ok:
            ok = list(range(len(pool)))
        i = int(rng.choice(ok))
        ch, a, b = pool[i]
        order.append(i)
        used.append((ch, a, b, total))
        total += b - a - c
    pieces = []
    for i in order:
        ch, a, b = pool[i]
        g = channels[ch][a:b].copy()
        # halfway to the median in dB: a loud stretch stays louder, just not by as much
        g *= (med / max(levels[i], 1e-6)) ** 0.5
        pieces.append(g)
    seq = pieces[0]
    for p in pieces[1:]:
        seq = np.concatenate([seq[:-c], seq[-c:] * fade_out + p[:c] * fade_in, p[c:]])
    # seamless: the last crossfade folds back over the start
    n = len(seq)
    out = seq[:n - c].copy()
    out[:c] = seq[:c] * fade_in + seq[n - c:] * fade_out
    out = low_shelf(out, rate, 3.0, 800.0, 2000.0)
    out *= 10 ** (target_db / 20) / rms(out)
    return soft_peak(out, 0.95), order, pool


def chain_shot(x, rate, start, length, decay_after, decay_tc, peak_db, fade_in=0.003):
    """A hit and the rattle after it, dying away from decay_after with time constant decay_tc."""
    seg = x[int(start * rate):int((start + length) * rate)].copy()
    t = np.arange(len(seg)) / rate
    env = np.where(t < decay_after, 1.0, np.exp(-(t - decay_after) / decay_tc))
    fi = int(fade_in * rate)
    env[:fi] *= np.linspace(0.0, 1.0, fi)
    fo = int(0.012 * rate)
    env[-fo:] *= np.linspace(1.0, 0.0, fo)
    seg *= env
    return seg * (10 ** (peak_db / 20) / np.abs(seg).max())


write("loop_elevator.wav", loop(read("freesound_community-elevator-29654.mp3", 22050), 22050,
                                [(0.9, 3.1), (4.4, 7.1)], 0.35, -14.0, 70.0), 22050)
write("switch_press.wav", punch(one_shot(read("freesound_community-light-switch-81967.mp3", 44100), 44100, 0.08, 0.40, 1.0), 3.0, 0.95), 44100)
write("lever_throw.wav", punch(one_shot(read("freesound_community-breaker-switch-45684.mp3", 44100), 44100, 0.05, 0.62, 1.0), 3.0, 0.95), 44100)

CHAINS = "dragon-studio-heavy-chains-dragging-515264.mp3"
_st = read_stereo(CHAINS, 32000)
_mics = [highpass(_st[:, 0], 32000, 60.0), highpass(_st[:, 1], 32000, 60.0)]
write("loop_chain.wav", texture(_mics, 32000, [(1.00, 2.62)], 12.0, -18.0, seed=515264)[0], 32000)
write("loop_chain_light.wav", texture(_mics, 32000, [(2.62, 3.66), (0.42, 0.96)], 12.0, -22.0, seed=264515)[0], 32000)
_hits = highpass(read_stereo(CHAINS, 44100).mean(axis=1), 44100, 50.0)
for _n, _t in enumerate([1.000, 2.110, 2.250], start=1):
    write("chain_clank_%d.wav" % _n, chain_shot(_hits, 44100, _t, 0.42, 0.07, 0.09, -3.0), 44100)
for _n, _t in enumerate([0.830, 0.890, 2.795], start=1):
    write("chain_clink_%d.wav" % _n, chain_shot(_hits, 44100, _t, 0.26, 0.04, 0.06, -6.0), 44100)
write("chain_settle.wav", chain_shot(_hits, 44100, 3.40, 1.00, 0.55, 0.18, -6.0, fade_in=0.03), 44100)
write("mission_fail.wav", one_shot(read("coghezzi-game-over-orchestral-stinger-cartoon-defeat-546515.mp3", 44100), 44100, 0.0, 2.0, 0.9, fade_out=0.35), 44100)
write("mission_success.wav", one_shot(read("universfield-level-up-191997.mp3", 44100), 44100, 0.0, 1.3, 0.9, fade_out=0.3), 44100)
write("powerup_activate.wav", one_shot(read("universfield-interface-124464.mp3", 44100), 44100, 0.2, 1.2, 0.9, fade_out=0.25), 44100)
write("door_slide.wav", one_shot(read("freesound_community-jaildoorclose-6173.mp3", 44100), 44100, 0.15, 1.20, 0.9, fade_in=0.03, fade_out=0.06), 44100)
write("shutter_open.wav", one_shot(read("freesound_community-sliding_wooden_cabinet_door_1-of-2-81503.mp3", 44100), 44100, 0.08, 1.00, 0.9, fade_in=0.02, fade_out=0.15), 44100)
write("shutter_close.wav", one_shot(read("freesound_community-sliding_wooden_cabinet_door_1-of-2-81503.mp3", 44100), 44100, 3.40, 4.40, 0.9, fade_in=0.02, fade_out=0.05), 44100)
