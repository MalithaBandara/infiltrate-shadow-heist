"""Measure every Owlish clip so one-shots can be separated from long raw takes."""
import json
import os
import subprocess

FFPROBE = (r"C:\Users\USER\AppData\Local\Microsoft\WinGet\Packages"
           r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
           r"\ffmpeg-9.0.1-full_build\bin\ffprobe.exe")
HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.dirname(HERE)
# Bulky, regenerable data (downloaded packs, slices, generated payloads) lives here, outside the
# repo. Override with SFX_WORK to point at an existing download instead of re-fetching.
WORK = os.environ.get("SFX_WORK") or os.path.join(BASE, "work")
ROOT = WORK
OWL = os.path.join(ROOT, "sfx-src", "ex", "owlish")


def probe(path):
    try:
        out = subprocess.run(
            [FFPROBE, "-v", "error", "-select_streams", "a:0",
             "-show_entries", "format=duration:stream=sample_rate,channels",
             "-of", "json", path],
            capture_output=True, text=True, timeout=30).stdout
        j = json.loads(out)
        st = (j.get("streams") or [{}])[0]
        return (float(j.get("format", {}).get("duration", 0)),
                int(st.get("sample_rate", 0)), int(st.get("channels", 0)))
    except Exception:
        return (0.0, 0, 0)


rows = []
for dirpath, dirnames, filenames in os.walk(OWL):
    dirnames[:] = [d for d in dirnames if d != "__MACOSX"]
    for fn in sorted(filenames):
        if fn.startswith("._") or not fn.lower().endswith((".wav", ".ogg", ".mp3")):
            continue
        full = os.path.join(dirpath, fn)
        cat = os.path.relpath(dirpath, OWL).replace("\\", "/")
        dur, rate, ch = probe(full)
        rows.append(dict(cat=cat, file=fn, dur=dur, rate=rate, ch=ch, path=full))

with open(os.path.join(ROOT, "owlish_index.json"), "w", encoding="utf-8") as f:
    json.dump(rows, f, indent=1)

oneshot = [r for r in rows if r["dur"] <= 1.5]
longtake = [r for r in rows if r["dur"] > 1.5]

print("%d clips total: %d one-shot (<=1.5s), %d long takes\n" % (len(rows), len(oneshot), len(longtake)))

for cat in sorted({r["cat"] for r in rows}):
    sub = [r for r in rows if r["cat"] == cat]
    print("== %s (%d)" % (cat, len(sub)))
    for r in sorted(sub, key=lambda r: r["dur"]):
        mark = "  " if r["dur"] <= 1.5 else "->"
        print("   %s %-46s %7.2fs %dch" % (mark, r["file"][:46], r["dur"], r["ch"]))
    print()
