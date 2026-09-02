"""Cut the take-off and landing out of jump_new.mp4's audio track.

Measured structure of the 4.01s source (see jump_analyze.py):
    0.485-1.10s  crouch, wind-up and push - continuous, around -40 dBFS
    1.10 -1.55s  airborne, decaying to near silence
    1.625s       landing, two impacts (-5.8 dBFS then -6.5 dBFS at 1.665) - both feet

The take-off sits ~34 dB below the landing, which is why an earlier pass read this file as
having "a single transient" and made the take-off reuse the landing sample. It is there, it
is just quiet. Each take-off cut is therefore normalised up on its own; the balance between
take-off and landing is then restored in GameAudio's gain table rather than baked into the
asset, so it stays adjustable.

Three take-off candidates because the exact frame the foot leaves the ground can't be settled
from the envelope alone - they bracket the plausible answers.
"""
import os
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
RAW = os.path.join(WORK, "jump_work", "jump_raw.wav")
OUT = os.path.join(ROOT, "sfx-src", "ex", "jump_src")

# name, start, duration, target peak dBFS, fade-in s
CUTS = [
    # -- take-off options --
    # A: the whole wind-up and push cluster, starting on the first push spike at 0.620.
    ("takeoff_A_windup",   0.600, 0.210, -3.0, 0.008),
    # B: starts hard on the sharpest isolated transient in the quiet region (0.775).
    ("takeoff_B_transient", 0.765, 0.190, -3.0, 0.004),
    # C: the last burst of ground contact before the airborne decay at ~1.10 - if the
    #    animation's foot actually leaves the ground late, this is the true take-off.
    ("takeoff_C_footleave", 0.925, 0.195, -3.0, 0.006),

    # -- landing options --
    # L1: both impacts plus the settle. The honest, full landing.
    ("landing_full",       1.612, 0.340, -1.0, 0.002),
    # L2: first impact only, tight - punchier, less realistic.
    ("landing_tight",      1.612, 0.110, -1.0, 0.002),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    if not os.path.exists(RAW):
        print("run jump_analyze.py first to extract", RAW)
        return

    for name, start, dur, peak_db, fade_in in CUTS:
        dst = os.path.join(OUT, name + ".wav")
        # High-pass at 60Hz clears camera/room rumble that normalising would otherwise lift.
        # loudnorm is deliberately not used - these are one-shot transients, and peak
        # normalisation keeps the attack intact where loudness normalisation would pump it.
        af = ("highpass=f=60,"
              "afade=t=in:st=0:d=%.4f,"
              "afade=t=out:st=%.4f:d=%.4f,"
              "alimiter=limit=%.4f" % (
                  fade_in,
                  max(0.0, dur - 0.030), 0.030,
                  10 ** (peak_db / 20.0)))
        subprocess.run(
            [FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
             "-ss", "%.4f" % start, "-t", "%.4f" % dur, "-i", RAW,
             "-af", af, "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", dst],
            capture_output=True)

        # Peak-normalise in a second pass, now that the cut exists and its true peak is known.
        probe = subprocess.run(
            [FFMPEG, "-hide_banner", "-i", dst, "-af", "volumedetect", "-f", "null", "-"],
            capture_output=True, text=True).stderr
        cur = None
        for line in probe.splitlines():
            if "max_volume:" in line:
                cur = float(line.split("max_volume:")[1].replace("dB", "").strip())
        if cur is not None:
            gain = peak_db - cur
            tmp = dst.replace(".wav", "_n.wav")
            subprocess.run(
                [FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-i", dst,
                 "-af", "volume=%.2fdB" % gain, "-ac", "1", "-ar", "44100",
                 "-c:a", "pcm_s16le", tmp],
                capture_output=True)
            os.replace(tmp, dst)
            print("%-22s %.3fs..%.3fs  lifted %+6.1f dB -> %.1f dBFS"
                  % (name, start, start + dur, gain, peak_db))
        else:
            print("%-22s cut, but peak could not be read" % name)

    print("\nwritten to sfx-src/ex/jump_src/")


if __name__ == "__main__":
    main()
