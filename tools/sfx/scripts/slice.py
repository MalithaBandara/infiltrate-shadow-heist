"""Cut Owlish's long raw takes into individual one-shots.

The useful foley in this pack (cloth rustle, running steps, breath) was recorded as
continuous takes with several hits per file. ffmpeg's silencedetect gives the gaps;
everything between two gaps that is plausibly one hit gets written out on its own,
converted to the project's target format (mono / 44.1k / 16-bit PCM) at the same time.
"""
import os
import re
import subprocess

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

# source file -> (output prefix, noise floor dB, min silence s, min hit s, max hit s)
JOBS = [
    ("Cloth, Rustle/320137__owlstorm__blanket-movement-1.wav", "cloth", -40, 0.20, 0.10, 1.60),
    ("Cloth, Rustle/320138__owlstorm__blanket-movement-2.wav", "cloth", -40, 0.20, 0.10, 1.60),
    ("Cloth, Rustle/320141__owlstorm__blanket-movement-3.wav", "cloth", -40, 0.20, 0.10, 1.60),
    ("Cloth, Rustle/320142__owlstorm__blanket-movement-4.wav", "cloth", -40, 0.20, 0.10, 1.60),
    ("Cloth, Rustle/320143__owlstorm__blanket-movement-5.wav", "cloth", -40, 0.20, 0.10, 1.60),
    ("Footsteps/running-shoes-1.wav", "runstep", -38, 0.10, 0.06, 0.70),
    ("Footsteps/running-shoes-2.wav", "runstep", -38, 0.10, 0.06, 0.70),
    ("Human/gasp1.wav", "breath", -38, 0.20, 0.15, 1.60),
    ("Human/breath-male.wav", "breath", -40, 0.25, 0.20, 1.80),
    ("Impacts/scrape3.wav", "scrape", -38, 0.15, 0.10, 1.60),
    ("Impacts/shouldergrab.wav", "grab", -38, 0.15, 0.10, 1.60),
]


def segments(path, noise_db, min_sil):
    """Non-silent [start, end] spans, derived from the silences between them."""
    p = subprocess.run(
        [FFMPEG, "-hide_banner", "-i", path,
         "-af", "silencedetect=noise=%ddB:d=%s" % (noise_db, min_sil),
         "-f", "null", "-"],
        capture_output=True, text=True)
    log = p.stderr

    dur = 0.0
    m = re.search(r"Duration: (\d+):(\d+):([\d.]+)", log)
    if m:
        dur = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))

    starts = [float(x) for x in re.findall(r"silence_start: (-?[\d.]+)", log)]
    ends = [float(x) for x in re.findall(r"silence_end: (-?[\d.]+)", log)]

    spans, cur = [], 0.0
    for i, s in enumerate(starts):
        if s > cur:
            spans.append((cur, s))
        cur = ends[i] if i < len(ends) else dur
    if dur - cur > 0.01:
        spans.append((cur, dur))
    return spans


def main():
    os.makedirs(OUT, exist_ok=True)
    counts = {}
    total = 0

    for rel, prefix, noise_db, min_sil, lo, hi in JOBS:
        src = os.path.join(OWL, rel.replace("/", os.sep))
        if not os.path.exists(src):
            print("MISSING", rel)
            continue

        kept = 0
        for (a, b) in segments(src, noise_db, min_sil):
            length = b - a
            if not (lo <= length <= hi):
                continue
            counts[prefix] = counts.get(prefix, 0) + 1
            name = "%s_%02d.wav" % (prefix, counts[prefix])
            # 30ms of pre-roll so the transient is never clipped, then a short fade
            # out so a cut mid-decay does not click.
            start = max(0.0, a - 0.03)
            subprocess.run(
                [FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                 "-ss", "%.3f" % start, "-t", "%.3f" % (length + 0.06), "-i", src,
                 "-af", "afade=t=out:st=%.3f:d=0.02,dynaudnorm=p=0.6:m=6" % max(0.0, length + 0.04),
                 "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le",
                 os.path.join(OUT, name)],
                capture_output=True)
            kept += 1
            total += 1
        print("%-52s -> %2d hits" % (os.path.basename(rel)[:52], kept))

    print("\n%d slices written to owlish_sliced/" % total)
    for k, v in sorted(counts.items()):
        print("   %-10s %d" % (k, v))


if __name__ == "__main__":
    main()
