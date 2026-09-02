# SFX selection tooling

Everything used to source, cut and audition the game's sound effects. Nothing in the build
references any of this — it is the working set behind the audio in `resources/sfx/`, kept so the
selection can be resumed or redone. See `.junie/sfx-handoff.md` for where the task stands.

## Layout

```
tools/sfx/
  candidates-manifest.txt     cue -> candidate map:  cue|group|pack|file
  jump_cuts/                  the 5 cuts from jump_new.mp4 (A/B/C take-off, full/tight landing)
  scripts/                    the pipeline (below)
  work/                       NOT in git - downloaded packs and generated payloads
```

`work/` is where all the bulk goes. Point it somewhere else with `SFX_WORK`:

```bash
export SFX_WORK=/some/other/dir      # optional; defaults to tools/sfx/work
```

## Reproducing from nothing

```bash
bash tools/sfx/scripts/fetch_packs.sh        # ~150MB, no account needed
python tools/sfx/scripts/slice.py            # cut long takes into one-shots
python tools/sfx/scripts/slice_cloth.py      # cut the continuous cloth takes
python tools/sfx/scripts/collect.py          # resolve manifest -> work/candidates.json
python tools/sfx/scripts/build_page.py       # -> work/foley-bench.html
```

Then publish `work/foley-bench.html` as an Artifact (pass the existing URL in
`.junie/sfx-handoff.md` to update in place rather than creating a second bench).

Both slice steps are required before `collect.py`: the manifest references
`owlish_sliced/` clips that only exist once they have run.

## Requirements

- **ffmpeg / ffprobe 9.0.1**, installed via `winget install Gyan.FFmpeg`. The scripts hardcode
  the winget path (it is not added to `PATH`); if it moves, fix the `BIN` constant at the top of
  each script.
- **Python 3.12+**. No third-party packages — only `wave`, `array`, `struct`, `subprocess`.

## The scripts

| Script | What it does |
|---|---|
| `fetch_packs.sh` | Downloads and extracts all 10 source packs. Verifies sizes, because OpenGameArt drops connections mid-transfer and curl still exits 0 on a truncated body. |
| `probe_owlish.py` | Separates a pack's one-shots from its long raw takes. Owlish ships both, unlabelled. |
| `slice.py` | Cuts long takes into one-shots using ffmpeg `silencedetect`. 30ms pre-roll so transients are not clipped, 20ms fade so cuts do not click. |
| `slice_cloth.py` | For takes with **no silences at all** — continuous cloth rustle, where `silencedetect` finds nothing. Ranks overlapping windows by RMS and keeps the loudest non-overlapping ones. |
| `jump_analyze.py` | Prints a text waveform of `jump_new.mp4` at 5ms resolution. This is how the take-off was found: it sits ~34dB under the landing and is invisible at normal scaling. |
| `jump_cut.py` | Produces the five jump cuts. **The timings and normalisation decisions live here.** |
| `collect.py` | Resolves the manifest against the extracted packs, measures every clip, transcodes WAV→Ogg *for the bench payload only*, writes `candidates.json`. |
| `build_page.py` | Injects that payload plus the cue briefs into `bench-template.html`. **The cue briefs — what each cue is and the source line it fires at — live in this file**, and are the most valuable single thing here. |
| `bench-template.html` | The audition console. The owner's settled choices are the `PRESET` and `KEEP` maps in its script block. |

## Editing the shortlist

Add or remove lines in `candidates-manifest.txt` (`cue|group|pack|file`; `pack` is a directory
name under `work/sfx-src/ex/`, `file` a basename or path within it), then re-run `collect.py`
and `build_page.py`. Order within a cue is manifest order.

To mark a cue as decided, edit `PRESET` (a chosen candidate) or `KEEP` (keep what the game
already ships) in `bench-template.html`.

## Licences

Every pack's terms, and the full attribution chain for the CC-BY one, are recorded in
`ATTRIBUTION.md` at the repo root. **Anything adopted from `footsteps_congusbongus` is CC-BY 3.0
and is itself a derivative** — the credit must name both congusbongus and the original Freesound
author for that surface.
