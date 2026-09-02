"""Resolve the candidate manifest against the extracted packs, measure each clip,
and emit a JSON payload (with base64 audio) for the audition page.

WAV sources are transcoded to Ogg for the page payload only - the audition page has to
carry ~190 clips inline, and uncompressed WAV would make it several times larger for no
audible benefit. The original path is kept on each entry so the production conversion
later runs from the untouched source, not from this preview copy.
"""
import base64
import json
import os
import subprocess

BIN = (r"C:\Users\USER\AppData\Local\Microsoft\WinGet\Packages"
       r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
       r"\ffmpeg-9.0.1-full_build\bin")
FFMPEG = os.path.join(BIN, "ffmpeg.exe")
FFPROBE = os.path.join(BIN, "ffprobe.exe")

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.dirname(HERE)
# Bulky, regenerable data (downloaded packs, slices, generated payloads) lives here, outside the
# repo. Override with SFX_WORK to point at an existing download instead of re-fetching.
WORK = os.environ.get("SFX_WORK") or os.path.join(BASE, "work")
ROOT = WORK
EX = os.path.join(ROOT, "sfx-src", "ex")
PREVIEW = os.path.join(ROOT, "preview_ogg")
MANIFEST = os.path.join(BASE, "candidates-manifest.txt")


def duration(path):
    try:
        out = subprocess.run(
            [FFPROBE, "-v", "error", "-show_entries", "format=duration",
             "-of", "csv=p=0", path],
            capture_output=True, text=True, timeout=30).stdout.strip()
        return float(out) if out else None
    except Exception:
        return None


def index_pack(pack):
    """basename -> full path, and relative-path -> full path, for one extracted pack."""
    base = os.path.join(EX, pack)
    by_name, by_rel = {}, {}
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d != "__MACOSX"]
        for fn in filenames:
            if fn.startswith("._") or not fn.lower().endswith((".ogg", ".wav", ".mp3")):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, base).replace("\\", "/")
            by_rel[rel] = full
            by_name.setdefault(fn, full)
    return by_name, by_rel


def preview_bytes(entry):
    """Ogg bytes for the page. Already-Ogg sources are used as-is."""
    src = entry["path"]
    if src.lower().endswith(".ogg"):
        with open(src, "rb") as f:
            return f.read()

    os.makedirs(PREVIEW, exist_ok=True)
    dst = os.path.join(PREVIEW, "%s__%s.ogg" % (entry["pack"], entry["file"].rsplit(".", 1)[0]))
    if not os.path.exists(dst):
        subprocess.run(
            [FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-i", src,
             "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "3", dst],
            capture_output=True)
    if not os.path.exists(dst):
        return None
    with open(dst, "rb") as f:
        return f.read()


def main():
    packs = {}
    entries, missing = [], []

    with open(MANIFEST, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            cue, group, pack, ref = line.split("|")
            if pack not in packs:
                packs[pack] = index_pack(pack)
            by_name, by_rel = packs[pack]

            full = by_rel.get(ref) or by_name.get(os.path.basename(ref))
            if not full:
                missing.append("%s: %s/%s" % (cue, pack, ref))
                continue

            entries.append(dict(cue=cue, group=group, pack=pack,
                                file=os.path.basename(ref), path=full,
                                bytes=os.path.getsize(full), dur=duration(full)))

    if missing:
        print("MISSING (%d):" % len(missing))
        for m in missing:
            print("  " + m)

    dropped = 0
    for e in entries:
        b = preview_bytes(e)
        if b is None:
            dropped += 1
            e["b64"] = None
        else:
            e["b64"] = base64.b64encode(b).decode("ascii")
    entries = [e for e in entries if e["b64"]]
    if dropped:
        print("dropped %d entries with no preview" % dropped)

    print("\nresolved %d candidates across %d cues"
          % (len(entries), len({e["cue"] for e in entries})))
    for cue in sorted({e["cue"] for e in entries}):
        n = sum(1 for e in entries if e["cue"] == cue)
        print("   %-22s %d" % (cue, n))

    out = os.path.join(ROOT, "candidates.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(entries, fh)
    print("\nwrote candidates.json (%.2f MB with base64)" % (os.path.getsize(out) / 1048576))


if __name__ == "__main__":
    main()
