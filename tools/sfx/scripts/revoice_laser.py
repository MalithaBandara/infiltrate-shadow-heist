"""
Re-voice resources/sfx/loop_laser.wav so a phone speaker can actually play it.

Usage:  python tools/sfx/scripts/revoice_laser.py

The source (gyzhor's "lightsaber5", cut to a seamless loop) puts 99% of its energy between 20 and
100 Hz - a 31 Hz fundamental with a couple of weak harmonics. Headphones reproduce that; a phone
speaker (roll-off around 300 Hz) cannot, so all that is left on a device is the speaker's own
distortion, heard as static (2026-09-30: "the laser sounds are just making a static sound").

So the loop is moved up to where a speaker can play it, keeping its character:
1. Played back 4x faster (two octaves up: 31 -> 125 Hz), tiled 4x so the file keeps its length.
   A whole-file time-scale of a seamless loop is still seamless.
2. Soft-saturated (tanh) for the upper harmonics of an electric hum (250 Hz-2 kHz). Memoryless, so
   the seam stays continuous.
3. High-passed at 180 Hz and low-passed at 3 kHz CIRCULARLY (in the FFT domain over the whole
   loop), so the filters have no start-up transient at the seam.
4. Normalized to the old file's RMS, so GameAudio.LASER_LOOP_* gains still mean what they did.

Reads the CURRENT resources/sfx/loop_laser.wav only once: keep the original as
tools/sfx/work/loop_laser_orig.wav (created on first run) and always re-voice from that. If work/
is gone, restore it first: git show ff2ab88:resources/sfx/loop_laser.wav > tools/sfx/work/loop_laser_orig.wav
"""
import os
import shutil
import wave

import numpy as np

DST = "resources/sfx/loop_laser.wav"
ORIG = "tools/sfx/work/loop_laser_orig.wav"
if not os.path.exists(ORIG):
    os.makedirs(os.path.dirname(ORIG), exist_ok=True)
    shutil.copyfile(DST, ORIG)

w = wave.open(ORIG)
sr = w.getframerate()
a = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64) / 32768.0
w.close()
rms0 = np.sqrt((a ** 2).mean())

# 1. Two octaves up, tiled back to length.
up = a[::4]
x = np.tile(up, 4)
# 2. Harmonics.
x = x / np.abs(x).max()
x = np.tanh(4.0 * x)
# 3. Circular band-pass.
X = np.fft.rfft(x)
f = np.fft.rfftfreq(len(x), 1.0 / sr)
hp = 1.0 / np.sqrt(1.0 + (180.0 / np.maximum(f, 1e-6)) ** 4)
lp = 1.0 / np.sqrt(1.0 + (f / 3000.0) ** 4)
x = np.fft.irfft(X * hp * lp, n=len(x))
# 4. Same loudness as before.
x *= rms0 / np.sqrt((x ** 2).mean())
peak = np.abs(x).max()
if peak > 0.97:
    x *= 0.97 / peak

out = wave.open(DST, "wb")
out.setnchannels(1)
out.setsampwidth(2)
out.setframerate(sr)
out.writeframes((x * 32767.0).astype("<i2").tobytes())
out.close()

S = np.abs(np.fft.rfft(x)) ** 2
bands = [(20, 100), (100, 300), (300, 1000), (1000, 3000)]
print(f"{len(x) / sr:.2f}s, seam step {abs(x[-1] - x[0]) * 32768:.0f}, max step {np.abs(np.diff(x)).max() * 32768:.0f}")
print(" ".join(f"{lo}-{hi}Hz:{100 * S[(f >= lo) & (f < hi)].sum() / S.sum():.1f}%" for lo, hi in bands))
