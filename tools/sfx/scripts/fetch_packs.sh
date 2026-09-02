#!/usr/bin/env bash
# Re-download and extract every source pack the SFX selection work draws from.
#
# Everything here is free for commercial use and needs no account. Run from anywhere:
#
#   bash tools/sfx/scripts/fetch_packs.sh
#
# Downloads into $SFX_WORK (default tools/sfx/work/), which is git-ignored - these are
# ~150MB of regenerable third-party archives and do not belong in the repo.
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="$(dirname "$HERE")"
WORK="${SFX_WORK:-$BASE/work}"
DST="$WORK/sfx-src"
mkdir -p "$DST/ex"

# Kenney's download URLs carry a content hash, so they can rot when a pack is republished.
# If one 404s, the current link is on the pack's page: https://kenney.nl/assets/<slug>
KENNEY_BASE="https://kenney.nl/media/pages/assets"
declare -A KENNEY=(
  [interface-sounds]="interface-sounds/fa43c1dd4d-1677589452/kenney_interface-sounds.zip"
  [ui-audio]="ui-audio/490d233f68-1677590494/kenney_ui-audio.zip"
  [digital-audio]="digital-audio/216eac4753-1677590265/kenney_digital-audio.zip"
  [impact-sounds]="impact-sounds/87b4ddecda-1677589768/kenney_impact-sounds.zip"
  [sci-fi-sounds]="sci-fi-sounds/6b296f9ecf-1677589334/kenney_sci-fi-sounds.zip"
  [rpg-audio]="rpg-audio/8e99002d76-1677590336/kenney_rpg-audio.zip"
  [casino-audio]="casino-audio/2472606a04-1721639069/kenney_casino-audio.zip"
  [music-jingles]="music-jingles/f37e530b9e-1677590399/kenney_music-jingles.zip"
)

fetch() { # url dest expected_bytes(optional)
  local url="$1" dest="$2" want="${3:-}"
  if [ -f "$dest" ] && [ -n "$want" ] && [ "$(stat -c %s "$dest")" = "$want" ]; then
    echo "  have $(basename "$dest")"; return 0
  fi
  # -C - resumes a partial file. OpenGameArt in particular throttles and drops the
  # connection mid-transfer, and curl exits 0 on a truncated body, so the size is
  # checked explicitly below rather than trusting the exit code.
  for attempt in 1 2 3 4 5 6 7 8; do
    curl -sL -C - --retry 5 --retry-delay 3 --max-time 900 -o "$dest" "$url"
    if [ -z "$want" ] || [ "$(stat -c %s "$dest" 2>/dev/null || echo 0)" -ge "$want" ]; then
      echo "  got  $(basename "$dest")"; return 0
    fi
    echo "  ...  $(basename "$dest") truncated, resuming (attempt $attempt)"
  done
  echo "  FAIL $(basename "$dest")"; return 1
}

echo "Kenney (CC0, 8 packs, ~11MB):"
for slug in "${!KENNEY[@]}"; do
  fetch "$KENNEY_BASE/${KENNEY[$slug]}" "$DST/kenney_$slug.zip"
done

echo "OpenGameArt:"
fetch "https://opengameart.org/sites/default/files/footsteps_0.zip" \
      "$DST/footsteps_congusbongus.zip"
# 142,384,346 bytes. Verified explicitly because a dropped connection still exits 0.
fetch "https://opengameart.org/sites/default/files/Owlish%20Media%20Sound%20Effects.zip" \
      "$DST/owlish.zip" 142384346

echo "Extracting:"
for z in "$DST"/*.zip; do
  name="$(basename "$z" .zip)"
  [ "$name" = "owlish" ] && name="owlish"
  if unzip -tq "$z" >/dev/null 2>&1; then
    unzip -qo "$z" -d "$DST/ex/$name" && echo "  ok   $name"
  else
    echo "  BAD  $name (corrupt or truncated archive - delete it and re-run)"
  fi
done

echo
echo "Done. Packs are in $DST/ex/"
echo "Next: python tools/sfx/scripts/slice.py && python tools/sfx/scripts/slice_cloth.py"
echo "      (owlish_sliced/ is needed by the manifest)"
