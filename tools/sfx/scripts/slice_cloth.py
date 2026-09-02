"""Slice continuous cloth-rustle takes by energy, not by silence.

The blanket-movement recordings are unbroken rustle - there are no silent gaps for
silencedetect to find. Instead: scan the take in overlapping windows, score each by RMS,
and keep the loudest non-overlapping ones. Those are the moments where the fabric actually
moves, which is what a jump take-off or a crouch stance change needs under it.
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
OWL = os.path.join(ROOT, "sfx-src", "ex", "owlish")
OUT = os.path.join(ROOT, "sfx-src", "ex", "owlish_sliced")

SOURCES = [
    "Cloth, Rustle/320137__owlstorm__blanket-movement-1.wav",
    "Cloth, Rustle/320138__owlstorm__blanket-movement-2.wav",
    "Cloth, Rustle/320141__owlstorm__blanket-movement-3.wav",
    "Cloth, Rustle/320142__owlstorm__blanket-movement-4.wav",
    "Cloth, Rustle/320143__owlstorm__blanket-movement-5.wav",
]

WIN = 0.45      # window length in seconds - about the length of a take-off gesture
HOP = 0.15
PER_FILE = 3    # keep at most this many, non-overlapping


def rms_windows(path):
    with wave.open(path, "rb") as w:
        n, ch, sw, rate = w.getnframes(), w.getnchannels(), w.getsampwidth(), w.getframerate()
        raw = w.readframes(n)
    if sw != 2:
        return [], 0
    a = array.array("h")
    a.frombytes(raw)
    if ch > 1:                      # collapse to mono by taking the first channel
        a = a[::ch]
    rate_frames = rate
    win, hop = int(WIN * rate_frames), int(HOP * rate_frames)
    scored = []
    for start in range(0, max(1, len(a) - win), hop):
        chunk = a[start:start + win]
        if not chunk:
            continue
        acc = 0
        for s in chunk[::7]:        # every 7th sample is plenty for a loudness ranking
            acc += s * s
        scored.append((acc / max(1, len(chunk[::7])), start / rate_frames))
    return scored, rate_frames


def main():
    os.makedirs(OUT, exist_ok=True)
    idx = 1
    for rel in SOURCES:
        src = os.path.join(OWL, rel.replace("/", os.sep))
        if not os.path.exists(src):
            print("MISSING", rel)
            continue

        scored, rate = rms_windows(src)
        if not scored:
            print("unreadable", rel)
            continue

        chosen = []
        for score, t in sorted(scored, key=lambda x: -x[0]):
            if all(abs(t - u) >= WIN for u in chosen):
                chosen.append(t)
            if len(chosen) >= PER_FILE:
                break

        for t in sorted(chosen):
            name = "clothrustle_%02d.wav" % idx
            idx += 1
            subprocess.run(
                [FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                 "-ss", "%.3f" % t, "-t", "%.3f" % WIN, "-i", src,
                 "-af", "afade=t=in:st=0:d=0.015,afade=t=out:st=%.3f:d=0.05,dynaudnorm=p=0.6:m=6" % (WIN - 0.05),
                 "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le",
                 os.path.join(OUT, name)],
                capture_output=True)
        print("%-48s -> %d hits" % (os.path.basename(rel)[:48], len(chosen)))

    print("\n%d cloth slices" % (idx - 1))


if __name__ == "__main__":
    main()
