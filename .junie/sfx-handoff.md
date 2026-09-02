# SFX work — handoff (2026-09-03)

Context for a new chat picking up the sound-effects work on **Infiltrate: Shadow Heist**.
Read `.junie/guidelines.md` first for the project as a whole; this file covers only the audio
task. The technical findings from this work are already folded into `guidelines.md` under
"Audio: two sound buses…" and "Compose Resources package is derived from the project `group`".

## The goal

Replace the game's existing sound effects and add every missing one — in-game motion, button
clicks, success and error feedback — across both UI layers.

## Decisions already made by the owner — do not relitigate

1. **Licence policy**: anything free for commercial use. Attribution is fine, and the owner
   wants **everything attributed even when the licence doesn't require it** (so CC0 gets credited
   too). See `ATTRIBUTION.md`.
2. **Movement foley**: replace only what is genuinely weak. **Footsteps are settled — keep the
   existing plate-cut `step_a.wav` / `step_b.wav` as they are.**
3. **Selection workflow**: the owner auditions. Claude cannot hear audio, so candidates are
   shortlisted, the owner listens and picks. Do not silently pick for cues that carry the game's
   feel.
4. **One click for everything**: Kenney `click3.ogg` (CC0, 0.083s) is the sound for **every**
   button and tap — menus, HUD, back, pause open, pause close. The owner was explicit that back
   and the pause buttons "are anyway just a button click".
5. **Jump comes from the owner's own animation plate**, `C:\Users\USER\Downloads\charAnimations\jump_new.mp4`,
   not from a library. Library candidates for those two cues were deliberately removed.

## The audition bench

**https://claude.ai/code/artifact/2d81100d-f218-4408-a3df-0760c0dfa6fb**

A published Artifact with **all candidates embedded as base64 audio**, so it plays on any device
with no local files. It can be re-read with the Artifact tool (`action: "read"`, that URL) and
republished to the same URL. Source template: `tools/sfx/scripts/bench-template.html`.

- 30 cues, each showing where it fires in the source so the owner can judge fit.
- `J`/`K` move, `Space` plays, `Enter` picks, `N` next cue, `U` next *unchosen*.
- **Export picks** produces a paste-back text block (`cue = pack/file`).
- Cues already settled show a green "Settled" note: the six click cues, and `footstep_run`
  (marked KEEP EXISTING).

**6 of 30 cues are settled. 24 remain unchosen.**

Still open: `crouch_stance`, `vault_climb`, `detection_rising`, `alert_spotted`, `mission_failed`,
`guard_investigating`, `beacon_ambient`, `level_complete`, `star_reveal`, `coin_bounty`,
`powerup_activate`, `powerup_denied`, `powerup_expire`, `ad_reward`, `toast_success`,
`toast_error`, `purchase_success`, `locked_denied`, `mission_start`, `slider_tick`,
`toggle_switch` — plus `jump_takeoff` and `land_impact`, where defaults were shipped but the
owner has not confirmed the cut (see below).

Two notes worth raising when the owner returns to it:
- `pause_open` / `pause_close` are now both click3, so that pairing question is closed.
- `detection_rising` is the highest-value gap in the game: the "spotted" banner was deliberately
  deleted from `GameplayScene`, so once wired, audio is the *only* channel telling the player
  they are being seen.

## What is already wired and verified

### Assets shipped (`resources/sfx/`, all PCM s16le / 44.1kHz / mono)

| File | What it is |
|---|---|
| `ui_click.wav` | Kenney `click3`, CC0 — every button and tap |
| `takeoff.wav` | `jump_new.mp4` 0.765–0.955s ("cut B"), normalised +35.6 dB |
| `impact.wav` | `jump_new.mp4` 1.612–1.952s, full two-foot landing (**replaced** the old cut) |

`ios-shell/Resources/ui_click.wav` is a **deliberate second copy** — the iOS menu bus reads it
through `NSBundle`, and `project.yml` already copies that directory. Same pattern as
`mainmenu.mp3`.

### Code

- `src/game/scene/GameAudio.kt` — added `takeoff` + `uiClick` to `GameSounds`; added
  `UI_CLICK_GAIN` (0.6) and `HUD_TAP_GAIN` (0.3); `TAKEOFF_GAIN` 0.35 → 0.45.
- `src/game/scene/GameplayScene.kt` — a local `playClick(gain)` helper; clicks on both modal
  button builders, the pause button, both touch-control builders, and the powerup dock; jump
  take-off switched from replaying `impact` to the real `takeoff` clip.
- `paywall-build/src/commonMain/kotlin/ui/MenuSfx.kt` **(new)** + `iosMain`/`androidMain`/`jvmMain`
  actuals — the menu SFX bus, which did not exist before. Delivered via a `LocalUiClick`
  CompositionLocal provided once in `NavigationRoot`.
- Click wired into `MenuComponents` (back, coin-pill plus, sidebar tabs), `MainMenuScreen`,
  `LevelSelectScreen`, `StoreScreen`, `SettingsScreen`.

### Verified

- `:game` `compileKotlinJvm` + `jvmTest` — BUILD SUCCESSFUL
- `paywall-build` `compileKotlinJvm` — BUILD SUCCESSFUL
- `paywall-build` `jvmTest` — 7 tests, 0 failures

Build `paywall-build` **directly**: `./gradlew.bat -p paywall-build <task>`.
Always `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"` first.

**Nothing has been committed.** Per `guidelines.md`, never push without explicit consent.

## Traps already hit — don't rediscover these

1. **Everything must be WAV.** Kenney ships Ogg; **iOS `AVAudioPlayer` and JavaFX cannot decode
   Ogg at all**, so an Ogg asset leaves the menu bus silent on two of three platforms.
2. **A pre-existing build break was found and fixed**: `group = "com.infiltrate"` in
   `paywall-build/build.gradle.kts` moved the generated Compose `Res` package, breaking all five
   UI screens with `Unresolved reference 'paywall_build'`. Fixed by pinning
   `compose.resources { packageOfResClass = "paywall_build.generated.resources" }`. This was
   **not** caused by the audio work — confirmed by building HEAD in a clean worktree.
3. **Missing audio fails silently.** `GameAudio.load()` catches per-clip and returns `null`, and
   `playSfx` no-ops on null. A bundling failure is inaudible, not loud — check the file reached
   the bundle before suspecting code.

## Open items

### 1. iOS gameplay audio is very likely silent — needs the owner's go-ahead

`ios-shell/project.yml` copies `ios-shell/Resources` and the Compose resources, **and nothing
else**. Every gameplay asset — sprites, backgrounds, the Bebas font, all of `sfx/` — loads via
`resourcesVfs`, which resolves against the app bundle. Nothing copies `resources/` there.

The menu click is fine on iOS (it has its own bundled copy). Gameplay audio is not.

Likely a one-line addition to `project.yml`, but that file is the hard-won working iOS config,
so it was **deliberately left untouched pending explicit approval**. Unverified on device —
confirm against a CI run.

### 2. Jump cut not confirmed by ear

Three take-off cuts and two landing cuts were produced; `takeoff_B_transient` and `landing_full`
were shipped as defaults so the game works now. All five are kept in `tools/sfx/jump_cuts/`.
Swapping is a file copy into `resources/sfx/`.

Measured structure of `jump_new.mp4` (4.01s, 48kHz stereo):

| Time | What |
|---|---|
| 0.485–1.10s | crouch, wind-up and push — around **−40 dBFS** |
| 1.10–1.55s | airborne, decaying to near silence |
| **1.625s** | landing at **−5.8 dBFS**, and it is **two impacts** (1.625 and 1.665) — both feet |

The push-off sits ~34 dB below the landing. That is why an earlier pass recorded this file as
having "a single transient" and made the take-off replay the landing sample — it was there, just
inaudible. Each cut is peak-normalised individually; the take-off/landing balance lives in
`GameAudio`'s gain table so it stays tunable without recutting.

The three take-off candidates bracket where the foot actually leaves the ground, which could not
be settled from the envelope alone: **A** = whole wind-up from 0.600s, **B** = hard onto the
sharpest transient at 0.775s, **C** = last ground contact before the airborne decay.

### 3. `mainmenu.mp3` has no recorded licence

It predates this work and ships in both `resources/` and `ios-shell/Resources/`. Origin is not
recorded anywhere in the repo. **Needs establishing before release, or replacing.** Flagged in
`ATTRIBUTION.md` rather than guessed at.

## Tooling (`tools/sfx/`)

Copied out of the session scratchpad so this work is reproducible. Delete if unwanted — nothing
in the build references it.

- `candidates-manifest.txt` — the full cue → candidate map, `cue|group|pack|file` per line.
- `jump_cuts/` — all five cuts from the owner's plate.
- `scripts/jump_analyze.py` — prints a text waveform of `jump_new.mp4`; how the two transients
  were found.
- `scripts/jump_cut.py` — produces the five cuts; timings and normalisation live here.
- `scripts/slice.py` — cuts long raw takes into one-shots via ffmpeg `silencedetect`.
- `scripts/slice_cloth.py` — cuts *continuous* takes (cloth rustle has no silences, so
  silencedetect finds nothing) by ranking overlapping windows by RMS.
- `scripts/probe_owlish.py` — separates one-shots from long takes in a downloaded pack.
- `scripts/collect.py` — resolves the manifest, measures clips, transcodes WAV→Ogg for the
  bench payload only, emits `candidates.json`.
- `scripts/build_page.py` — injects that JSON plus the cue briefs into the bench template.
  **The cue briefs (what each cue is and where it fires) live in this file** — the most useful
  single artefact if the bench ever needs rebuilding.

`ffmpeg` 9.0.1 is installed at
`%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.1-full_build\bin`
(not on `PATH`; the scripts hardcode it).

### Re-downloading the source packs

The downloaded audio (~150MB) lives in the session scratchpad and will not survive. All of it is
free to re-fetch, no account needed:

- Kenney (all CC0), 8 packs, 11.4MB total: `interface-sounds`, `ui-audio`, `digital-audio`,
  `impact-sounds`, `sci-fi-sounds`, `rpg-audio`, `casino-audio`, `music-jingles` — download links
  are on each `https://kenney.nl/assets/<slug>` page.
- OwlishMedia "Sound Effects Pack" (CC0, 136MB) —
  https://opengameart.org/content/sound-effects-pack
  **OpenGameArt throttles and drops the connection**; use `curl -C -` with retries, and verify
  the final size (142,384,346 bytes) — a truncated download still exits 0.
- congusbongus "Footsteps on different surfaces" (**CC-BY 3.0**, 415KB) —
  https://opengameart.org/content/footsteps-on-different-surfaces
  If anything from this is ever adopted, the credit must name **both** congusbongus and the
  original Freesound author per surface — see the chain table in `ATTRIBUTION.md`.

Freesound has the best material but requires an account to download, which Claude cannot create —
it needs an API token from the owner, or the owner downloading picks themselves.
