"""Cut the lift / switch / lever sounds from the Pixabay downloads in Downloads/charAnimations/music3.

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


write("loop_elevator.wav", loop(read("freesound_community-elevator-29654.mp3", 22050), 22050,
                                [(0.9, 3.1), (4.4, 7.1)], 0.35, -14.0, 70.0), 22050)
write("switch_press.wav", punch(one_shot(read("freesound_community-light-switch-81967.mp3", 44100), 44100, 0.08, 0.40, 1.0), 3.0, 0.95), 44100)
write("lever_throw.wav", punch(one_shot(read("freesound_community-breaker-switch-45684.mp3", 44100), 44100, 0.05, 0.62, 1.0), 3.0, 0.95), 44100)
