"""Find the transients in jump_new.mp4's audio track.

GameAudio.kt's current note says this source has "a single transient in it", which is why
the take-off reuses the landing sample. The brief says there are two: a small push-off and
a louder landing. This measures the envelope so the answer comes off the waveform rather
than off either assumption.
"""
import array
import os
import subprocess
import wave

BIN = (r"C:\Users\USER\AppData\Local\Microsoft\WinGet\Packages"
       r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
       r"\ffmpeg-9.0.1-full_build\bin")
FFMPEG = os.path.join(BIN, "ffmpeg.exe")
HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.dirname(HERE)
# Bulky, regenerable data (downloaded packs, slices, generated payloads) lives here, outside the
# repo. Override with SFX_WORK to point at an existing download instead of re-fetching.
WORK = os.environ.get("SFX_WORK") or os.path.join(BASE, "work")
ROOT = WORK
# The owner's animation plate. Override with JUMP_SRC if it moves.
SRC = os.environ.get("JUMP_SRC") or r"C:\Users\USER\Downloads\charAnimations\jump_new.mp4"
JUMPDIR = os.path.join(WORK, "jump_work")
RAW = os.path.join(JUMPDIR, "jump_raw.wav")

FRAME = 0.005  # 5ms envelope resolution


def extract():
    os.makedirs(JUMPDIR, exist_ok=True)
    subprocess.run(
        [FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-i", SRC,
         "-vn", "-ac", "1", "-ar", "48000", "-c:a", "pcm_s16le", RAW],
        capture_output=True)
    return RAW


def envelope(path):
    with wave.open(path, "rb") as w:
        n, rate = w.getnframes(), w.getframerate()
        raw = w.readframes(n)
    a = array.array("h")
    a.frombytes(raw)
    step = int(FRAME * rate)
    env = []
    for i in range(0, len(a) - step, step):
        peak = 0
        for s in a[i:i + step]:
            v = s if s >= 0 else -s
            if v > peak:
                peak = v
        env.append((i / rate, peak / 32768.0))
    return env, rate, len(a) / rate


def main():
    path = extract()
    env, rate, dur = envelope(path)
    peak = max(v for _, v in env)
    print("duration %.3fs, peak %.4f (%.1f dBFS)\n" % (dur, peak, 20 * (peak and __import__("math").log10(peak) or -10)))

    # Print a text waveform so the two events are visible without opening an editor.
    print("envelope (each row = 25ms, bar scaled to peak):")
    for i in range(0, len(env), 5):
        t, v = env[i]
        chunk = max(x[1] for x in env[i:i + 5]) if env[i:i + 5] else v
        bars = int((chunk / peak) * 56) if peak else 0
        flag = "  <<<" if chunk > peak * 0.30 else ""
        print("  %5.3fs |%-56s| %.4f%s" % (t, "#" * bars, chunk, flag))


if __name__ == "__main__":
    main()
