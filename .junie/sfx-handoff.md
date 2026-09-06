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
6. **One success sound, one error sound, everywhere** (2026-09-05): no bespoke "bigger" success
   stinger for purchases, no separate reward sound for the ad-continue, no separate denial sound
   for a locked mission — `toast_success.wav`/`toast_error.wav` cover every success/error-flavoured
   cue in the game, gameplay and menu alike. Do not source additional success/error variants.
7. **Several cues get no sound at all, by design** (2026-09-05): `powerup_activate`,
   `powerup_denied`, `powerup_expire`, and `mission_start`. Don't add sound to these later without
   the owner reopening the question — this isn't a placeholder gap, it's a decision.
8. **The toggle switch is just the primary click** (2026-09-05) — no dedicated mechanical
   two-state sound, despite what the original bench brief asked for.

## The audition bench

**https://claude.ai/code/artifact/2d81100d-f218-4408-a3df-0760c0dfa6fb**

A published Artifact with **all candidates embedded as base64 audio**, so it plays on any device
with no local files. It can be re-read with the Artifact tool (`action: "read"`, that URL) and
republished to the same URL. Source template: `tools/sfx/scripts/bench-template.html`.

- 19 cues (down from the original 30 — see "Cues removed" below), each showing where it fires in
  the source so the owner can judge fit.
- `J`/`K` move, `Space` plays, `Enter` picks, `N` next cue, `U` next *unchosen*.
- **Export picks** produces a paste-back text block (`cue = pack/file`).
- Cues already settled show a green "Settled" note: the six click cues, and `footstep_run`
  (marked KEEP EXISTING).

**7 of 19 cues are settled. 12 remain unchosen.**

Still open: `crouch_stance`, `vault_climb`, `detection_rising`, `alert_spotted`, `mission_failed`,
`beacon_ambient`, `level_complete`, `star_reveal`, `coin_bounty`, `slider_tick` — plus
`jump_takeoff` and `land_impact`, where defaults were shipped but the owner has not confirmed the
cut (see below).

### Cues removed from the bench entirely (2026-09-05) — owner decisions, not bench picks

Eleven cues that once needed auditioning no longer do, either because the owner supplied a real
file directly or decided no sound belongs there at all. Removed from both the published bench and
`tools/sfx/scripts/build_page.py` (the regeneratable source), including their now-unused candidate
audio, so nobody re-auditions a decision that's already final:

- **`toast_success` / `toast_error`** — owner supplied `success.wav`/`error.ogg` directly
  (Freesound, CC0: Sjonas88 and Kastenfrosch). Shipped as `resources/sfx/toast_{success,error}.wav`
  (+ the iOS duplicate under `ios-shell/Resources/`), wired into `showToast()` in both
  `SettingsScreen.kt` and `StoreScreen.kt` via `MenuClip.TOAST_SUCCESS`/`TOAST_ERROR` in
  `MenuSfx.kt`. Credited at Settings → About → Credits & Licenses and in `ATTRIBUTION.md`.
- **`guard_investigating`** — owner supplied `guard_investigate.wav` directly (from
  `Downloads\charAnimations\music\`, no licence info given — this is the owner's own recording,
  same status as the jump/footstep/climb plates; flag if that's wrong). Converted to the project's
  mono/44.1kHz/PCM WAV format, peak-normalised to -3dBFS (no silence to trim — the source was
  already a tight 0.35s clip), shipped as `resources/sfx/guard_investigate.wav`. Wired into
  `GameAudio.kt` (`GameSounds.guardInvestigate`, `GUARD_INVESTIGATE_GAIN = 0.8`) and fired from
  `GameplayScene.kt`'s per-guard update loop on the rising edge of `GuardState.PATROL ->
  INVESTIGATING` (a new `guardWasInvestigating: BooleanArray`, one bool per guard) — so it fires
  once when a guard first gets suspicious, not on every frame it stays that way.
- **`powerup_activate` / `powerup_denied` / `powerup_expire`** — owner decided none of these need a
  sound. Never wired in code (they never had a call site to begin with), so no code change; just
  removed from the bench.
- **`mission_start`** — owner decided it needs no sound. Same as above: never wired, removed from
  the bench only.
- **`toggle_switch`** — owner decided it's just the primary click, no separate sound. Already
  wired that way in `SettingsScreen.kt`'s controls-layout toggle (`click()` on both segments) from
  earlier work — nothing left to do, removed from the bench.
- **`purchase_success` / `locked_denied` / `ad_reward`** — owner decided not to source distinct
  success/error sounds for these; reuse the two that already exist everywhere.
  - `purchase_success` (`StoreScreen.kt`'s coin/power-up purchase flows) was **already** wired
    through the same `showToast()` used by `toast_success`/`toast_error`, so this one needed no
    code change at all — it's been reusing the right sound since `showToast` first got wired.
  - `locked_denied` needed real wiring: tapping a locked/gated mission card in
    `LevelSelectScreen.kt` used to swallow the tap in total silence. Now fires `LocalToastError`
    on that tap (no new toast banner UI was added — just the sound, since that's all that was
    asked for).
  - `ad_reward` (continue-granted, in `GameplayScene.kt` — the KorGE gameplay bus, not Compose)
    needed a new load path: `GameSounds.toastSuccess` now also loads `resources/sfx/toast_success.wav`
    on the gameplay side (`GameAudio.TOAST_SUCCESS_GAIN = 0.8`) and plays it the instant
    `ContinueAdBridge.consumeContinueGranted()` returns true.

Two notes worth raising when the owner returns to what's left:
- `pause_open` / `pause_close` are now both click3, so that pairing question is closed.
- `detection_rising` is the highest-value gap in the game: the "spotted" banner was deliberately
  deleted from `GameplayScene`, so once wired, audio is the *only* channel telling the player
  they are being seen.

## What is already wired and verified

### Assets shipped (`resources/sfx/`, all PCM s16le / 44.1kHz / mono)

| File | What it is |
|---|---|
| `ui_click.wav` | Kenney `click3`, CC0 — every button and tap |
| `impact.wav` | `jump_new.mp4` 1.612–1.952s, full two-foot landing (**replaced** the old cut) |
| `toast_success.wav` | Owner-supplied `success.wav` (Freesound, CC0, Sjonas88), trimmed to the real transient (0.30–1.00s of the source) and peak-normalised to -3dBFS. Also loaded gameplay-side now, for `ad_reward` — see Code below |
| `toast_error.wav` | Owner-supplied `error.ogg` (Freesound, CC0, Kastenfrosch), trimmed to the real transient (0–0.55s of the source) and peak-normalised to -3dBFS |
| `guard_investigate.wav` | Owner-supplied, from `Downloads/charAnimations/music/`. No silence to trim (already a tight 0.35s clip) — just format-converted and peak-normalised to -3dBFS |

`ios-shell/Resources/ui_click.wav` is a **deliberate second copy** — the iOS menu bus reads it
through `NSBundle`, and `project.yml` already copies that directory. Same pattern as
`mainmenu.mp3`.

**`takeoff.wav` was removed after this handoff was written** — a concurrent on-device debugging
pass (2026-09-05, see `guidelines.md`'s "RESOLVED: `takeoff.wav` removed" section) found it played
as TV static, not a jump sound: the waveform showed sustained broadband noise with no attack-decay
shape anywhere, not the quiet-but-real transient this handoff originally described. Removed
entirely from `GameAudio.kt`/`GameSounds`/`GameplayScene.kt`, not muted. **Jump take-off is
currently silent** — one of the five candidate cuts in `tools/sfx/jump_cuts/` would need a fresh
listen (Claude cannot hear audio) before any of them go back in.

### Code

- `src/game/scene/GameAudio.kt` — added `uiClick` to `GameSounds`; added `UI_CLICK_GAIN` (now 0.65,
  see the click-volume history in its own doc comment) and `HUD_TAP_GAIN` (0.3). (`takeoff`/
  `TAKEOFF_GAIN` were added then later removed — see above.) Also gained `GameSounds.primeAll()`,
  called once at the end of `load()`, unrelated to this handoff: it plays every clip once at zero
  volume to pay Android's `AudioTrack`-construction latency during the loading screen instead of on
  the first real jump/landing. 2026-09-05: added `guardInvestigate` (`GUARD_INVESTIGATE_GAIN =
  0.8`) and a second load of `toast_success.wav` on this bus (`TOAST_SUCCESS_GAIN = 0.8`, for
  `ad_reward` — the menu bus's `toast_success` load is separate and unrelated, they just share a
  source file).
- `src/game/scene/GameplayScene.kt` — a local `playClick(gain)` helper; clicks on both modal
  button builders, the pause button, both touch-control builders, and the powerup dock.
  2026-09-05: a `guardWasInvestigating: BooleanArray` added next to `guardBadges`, checked in the
  same per-guard loop that already updates visor colour/vision cones, firing `guardInvestigate` on
  the `PATROL -> INVESTIGATING` rising edge only. Also, `ad_reward`'s success sound fires right
  before `caughtOverlay.visible = false` in the `consumeContinueGranted()` branch of the main
  update loop.
- `paywall-build/src/commonMain/kotlin/ui/MenuSfx.kt` **(new)** + `iosMain`/`androidMain`/`jvmMain`
  actuals — the menu SFX bus, which did not exist before. Delivered via a `LocalUiClick`
  CompositionLocal provided once in `NavigationRoot`.
- Click wired into `MenuComponents` (back, coin-pill plus, sidebar tabs), `MainMenuScreen`,
  `LevelSelectScreen`, `StoreScreen`, `SettingsScreen`.
- `MenuSfx.kt` generalised (2026-09-05) with a second, parallel mechanism —
  `MenuClip`/`rememberMenuClip`/`LocalToastSuccess`/`LocalToastError` — so `toast_success.wav` and
  `toast_error.wav` could be added without touching the already-verified click path. Each platform
  actual gained a second, keyed-by-clip-name player object (`AndroidMenuClipPlayer`,
  `IosMenuClipPlayer`, `DesktopMenuClipPlayer`) alongside its existing click-only one, rather than
  generalising the click player itself. Wired into both `showToast()` sites in `SettingsScreen.kt`
  and `StoreScreen.kt`, which fire the success or error clip depending on the toast's own
  `isSuccess` flag.
- Settings → About → "CREDITS & LICENSES" (`SettingsScreen.kt`, `AboutSettingsPanel`) was a
  placeholder toast before this pass. It now expands in place to a real credits list
  (`SOUND_CREDITS`) naming all four third-party sounds — kept in sync with `ATTRIBUTION.md` by
  hand, not generated from it.
- 2026-09-05: `LevelSelectScreen.kt`'s mission-card tap handler used to silently swallow taps on
  locked/gated missions (`if (canPlay) onStartMission(levelData)`, no `else`). Now
  `else toastError()`, reusing `LocalToastError` — no new toast banner UI, just the sound the
  owner asked for.
- `tools/sfx/scripts/build_page.py`'s `CUES` list (the regeneratable source for the bench) trimmed
  from 30 to 19 entries — see "Cues removed from the bench entirely" above for exactly which and
  why. The published bench itself was edited and republished to match (same URL), by parsing the
  live artifact's embedded `DATA`/`CUES` JSON, filtering out the 11 resolved cue ids, and
  re-publishing — not by re-running the fetch/slice pipeline from scratch, since nothing about the
  remaining candidates changed.

### Verified

- `:game` `compileKotlinJvm` + `jvmTest` — BUILD SUCCESSFUL
- `paywall-build` `compileKotlinJvm` — BUILD SUCCESSFUL
- `paywall-build` `jvmTest` — 7 tests, 0 failures
- 2026-09-05, after the toast-sound work: `paywall-build` `compileKotlinJvm`, `jvmTest`,
  `compileDebugKotlinAndroid`, and `compileKotlinIosSimulatorArm64` (klib only, no link — same
  Windows-can't-link-iOS constraint as everywhere else in this project) all BUILD SUCCESSFUL.
  Toast sounds not heard by ear (Claude cannot hear audio) — the owner already supplied and named
  them, so no bench audition was needed for these two.
- Same session, also re-ran root `:game` `compileKotlinJvm` + `jvmTest` — BUILD SUCCESSFUL, all
  tests passing — since a concurrent session had touched `GameAudio.kt`/`GameplayScene.kt`
  (the `takeoff.wav` removal, see the updated item 2 below) after this handoff's original
  verification. Confirms the working tree builds clean as a whole, not just the parts this pass
  touched.
- 2026-09-05, after the guard-investigate/click-volume/toast-reuse work: `:game`
  `compileKotlinJvm` + `jvmTest` and `paywall-build` `compileKotlinJvm` + `jvmTest` both BUILD
  SUCCESSFUL again. `guard_investigate.wav`'s trigger (the guard-state edge detection) is exercised
  by the existing `GameplayModelTest` suite indirectly (guards do transition to `INVESTIGATING` in
  several tests) but not asserted on directly — nobody has heard this sound either.

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

The menu click is fine on iOS (it has its own bundled copy) — and so are the two new toast sounds,
for the same reason: both are duplicated into `ios-shell/Resources/`. Gameplay audio is not.

Likely a one-line addition to `project.yml`, but that file is the hard-won working iOS config,
so it was **deliberately left untouched pending explicit approval**. Unverified on device —
confirm against a CI run.

### 2. Jump take-off is silent — needs a fresh cut, `takeoff_B_transient` was defective

**Updates the old "jump cut not confirmed by ear" item — that framing is stale.** Three take-off
cuts and two landing cuts were originally produced; `takeoff_B_transient` and `landing_full` were
shipped as defaults. A later on-device pass (2026-09-05, not this session — see `guidelines.md`'s
"RESOLVED: `takeoff.wav` removed") found `takeoff_B_transient` played back as TV static, not a
jump sound, and root-caused it to the clip itself being broadband noise (sustained high energy,
no attack-decay shape anywhere) rather than a decode/code bug. It was removed from
`GameAudio.kt`/`GameSounds`/`GameplayScene.kt` entirely — **jump take-off is currently silent**,
and the landing sound (`impact.wav`) is unaffected.

The other two take-off candidates (`takeoff_A_windup`, `takeoff_C_footleave`) are unverified — it's
unknown whether they have the same noise problem or whether it was specific to the `B` cut's
narrower time window. All three still live in `tools/sfx/jump_cuts/`, cut from the same source at:

| Time | What |
|---|---|
| 0.485–1.10s | crouch, wind-up and push — around **−40 dBFS** |
| 1.10–1.55s | airborne, decaying to near silence |
| **1.625s** | landing at **−5.8 dBFS**, and it is **two impacts** (1.625 and 1.665) — both feet |

The push-off sits ~34 dB below the landing, which is quiet but should not be *pure noise* at any
gain — if `A` and `C` turn out to have the same broadband-noise signature as `B`, the source
recording's push-off segment itself may need to be treated as unusable, and take-off would need a
genuinely new recording rather than a different cut of the same footage. **Before trying either
remaining candidate, re-run the same waveform check that caught `B`** (RMS/peak envelope across
the clip, looking for a real attack-decay shape vs. sustained energy) rather than assuming a
different cut is automatically fine.

The three take-off candidates bracket where the foot actually leaves the ground, which could not
be settled from the envelope alone: **A** = whole wind-up from 0.600s, **B** = hard onto the
sharpest transient at 0.775s (the one now known to be bad), **C** = last ground contact before the
airborne decay.

### 3. `mainmenu.mp3` licence — RESOLVED 2026-09-05

Was previously flagged as having no recorded origin. The owner supplied a file that is
byte-identical (MD5-verified) to the one already shipping, with its credit: Nikita Kondrashev, via
Pixabay. `ATTRIBUTION.md` and the in-app Credits panel are both updated. No file changes needed —
only the credit was missing.

## Tooling (`tools/sfx/`)

Copied out of the session scratchpad so this work is reproducible, and **verified to run from
its new location** - the full bench regenerates from these scripts alone. Read
`tools/sfx/README.md` for the layout and the per-script breakdown. Delete the folder if unwanted;
nothing in the build references it.

Reproduce from nothing:

```bash
bash tools/sfx/scripts/fetch_packs.sh        # ~150MB, no account needed
python tools/sfx/scripts/slice.py
python tools/sfx/scripts/slice_cloth.py
python tools/sfx/scripts/collect.py
python tools/sfx/scripts/build_page.py       # -> work/foley-bench.html, then publish to the URL above
```

Bulk data lands in `tools/sfx/work/` (git-ignored). `SFX_WORK` points that elsewhere - useful to
reuse an existing download instead of re-fetching.

`ffmpeg` 9.0.1 is required (`winget install Gyan.FFmpeg`); the scripts hardcode the winget path
because it is not on `PATH`.

**The cue briefs - what each of the 30 cues is and the source line it fires at - live in
`scripts/build_page.py`.** That is the most valuable single artefact here.

### What is and is not preserved

- **Preserved**: the manifest, all five jump cuts, every script, the bench template with the
  owner's settled choices baked in, and the published bench itself (which carries all 176
  candidates as embedded audio and can be re-read via the Artifact tool).
- **Not preserved**: the ~150MB of downloaded packs and the 68 Owlish slices, both of which live
  in a session temp directory that will be cleared. `fetch_packs.sh` plus the two slice scripts
  regenerate them exactly.

### Re-downloading the source packs

`fetch_packs.sh` does this in one command. Sources, all free for commercial use and needing no
account:

- Kenney (all CC0), 8 packs, 11.4MB total. Direct links are in the script; they carry a content
  hash and can rot, in which case the current link is on `https://kenney.nl/assets/<slug>`.
- OwlishMedia "Sound Effects Pack" (CC0, 136MB) -
  https://opengameart.org/content/sound-effects-pack
  **OpenGameArt throttles and drops the connection**, and a truncated download still exits 0, so
  the script resumes with `curl -C -` and verifies the final size (142,384,346 bytes).
- congusbongus "Footsteps on different surfaces" (**CC-BY 3.0**, 415KB) -
  https://opengameart.org/content/footsteps-on-different-surfaces
  If anything from this is ever adopted, the credit must name **both** congusbongus and the
  original Freesound author per surface - see the chain table in `ATTRIBUTION.md`.

Freesound has the best material but requires an account to download, which Claude cannot create -
it needs an API token from the owner, or the owner downloading picks themselves.
