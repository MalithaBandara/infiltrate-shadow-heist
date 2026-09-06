# Audio attribution

Every third-party sound shipped in this game, with its licence and where it came from.

Some of these are CC0 and legally require no credit at all. They are listed anyway — the
project's policy is to credit every external asset regardless of whether the licence compels it.

Assets cut from this project's own animation plates are listed too, so that a future reader can
tell at a glance which sounds are ours and which are not.

---

## In use

| File | Cue | Source | Author | Licence |
|---|---|---|---|---|
| `resources/sfx/step_a.wav` | Running footstep A | `walk_new.mp4` audio track | This project | Own work |
| `resources/sfx/step_b.wav` | Running footstep B | `walk_new.mp4` audio track | This project | Own work |
| `resources/sfx/climb.wav` | Vault / climb | `climb.mp4` audio track | This project | Own work |
| `resources/sfx/impact.wav` | Landing | `jump_new.mp4` audio track, 1.612–1.952 s | This project | Own work |
| `resources/sfx/guard_investigate.wav` | Guard starts investigating | `Downloads/charAnimations/music/guard_investigate.wav` | This project | Own work |
| `resources/sfx/camera_detect.wav` | Camera starts detecting player | `Downloads/charAnimations/music/camera.mp3` (one-beat pulse) | This project | Own work |
| `resources/music/bgmusic.mp3` | Gameplay background music | `Downloads/charAnimations/music/bgmusic.mp3` | This project | Own work |
| `resources/sfx/ui_click.wav` | Every button and tap | [Kenney UI Audio](https://kenney.nl/assets/ui-audio) (`click3.ogg`) | [Kenney](https://kenney.nl) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `ios-shell/Resources/ui_click.wav` | Menu buttons on iOS | Same file as above, duplicated for bundling | [Kenney](https://kenney.nl) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `resources/sfx/toast_success.wav` | Success toast (Settings/Store) | [Sound effect](https://freesound.org/s/538554/) | Sjonas88 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `ios-shell/Resources/toast_success.wav` | Same, iOS menu bus | Same file as above, duplicated for bundling | Sjonas88 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `resources/sfx/toast_error.wav` | Error toast (Settings/Store) | [Sound effect](https://freesound.org/s/521973/) | Kastenfrosch | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `ios-shell/Resources/toast_error.wav` | Same, iOS menu bus | Same file as above, duplicated for bundling | Kastenfrosch | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `resources/mainmenu.mp3` | Menu music | [Track](https://pixabay.com/music/) via Pixabay | Nikita Kondrashev | [Pixabay Content License](https://pixabay.com/service/license-summary/) |
| `ios-shell/Resources/mainmenu.mp3` | Same, iOS menu bus | Same file as above, duplicated for bundling | Nikita Kondrashev | [Pixabay Content License](https://pixabay.com/service/license-summary/) |
| `resources/sfx/alert_guard.wav` | Guard detects player | ["Huh 5"](https://freesound.org/s/812300/) | Sadiquecat | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `resources/sfx/alert_camera.wav` | Camera detects player | ["Missile Lock Detected"](https://freesound.org/s/165504/) | ryanconway | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `resources/missionmusic.mp3` | In-mission background music | [Track](https://pixabay.com/music/) via Pixabay | DELOSound | [Pixabay Content License](https://pixabay.com/service/license-summary/) |

### Notes on the plate cuts

`crouch.wav` and `takeoff.wav` were both removed after this file was first written — `crouch.wav`
because playing anything on the crouch stance change contradicts the mechanic it exists to serve
(crouching is silent movement), and `takeoff.wav` because on-device testing found the cut itself
was defective (broadband noise, not a real transient — see `.junie/guidelines.md`'s "RESOLVED:
`takeoff.wav` removed"). Jump take-off is currently silent; see the SFX handoff's open items.

---

## Downloaded and evaluated, not currently shipped

These packs were downloaded during sound selection. Nothing from them is in the repo except
`click3` above. Listed so that if any further clip is adopted, its licence is already recorded.

| Pack | Author | Licence | Notes |
|---|---|---|---|
| [Interface Sounds](https://kenney.nl/assets/interface-sounds) | Kenney | CC0 1.0 | |
| [UI Audio](https://kenney.nl/assets/ui-audio) | Kenney | CC0 1.0 | `click3` adopted |
| [Digital Audio](https://kenney.nl/assets/digital-audio) | Kenney | CC0 1.0 | |
| [Impact Sounds](https://kenney.nl/assets/impact-sounds) | Kenney | CC0 1.0 | |
| [Sci-fi Sounds](https://kenney.nl/assets/sci-fi-sounds) | Kenney | CC0 1.0 | |
| [RPG Audio](https://kenney.nl/assets/rpg-audio) | Kenney | CC0 1.0 | |
| [Casino Audio](https://kenney.nl/assets/casino-audio) | Kenney | CC0 1.0 | |
| [Music Jingles](https://kenney.nl/assets/music-jingles) | Kenney | CC0 1.0 | |
| [Sound Effects Pack](https://opengameart.org/content/sound-effects-pack) | OwlishMedia | CC0 1.0 | |
| [Footsteps on different surfaces](https://opengameart.org/content/footsteps-on-different-surfaces) | congusbongus | **CC-BY 3.0** | See chain below |

### The congusbongus chain

That pack is **CC-BY 3.0, not CC0**, and it is itself a derivative work — each surface folder
credits a different original recording. If any clip from it is ever adopted, the credit has to
name both the compiler and the original author:

| Surface | Derived from | Original author |
|---|---|---|
| `boots`, `tile` | `footstep-concrete.wav` | [swuing](https://freesound.org/people/swuing/sounds/38873/) |
| `tile` (also) | `Squeaky footstep.wav` | [ceberation](https://freesound.org/people/ceberation/sounds/235524/) |
| `metal` | `boots on aluminum ladder 01` | [Eelke](https://freesound.org/people/Eelke/sounds/462598/) |

All under [CC-BY 3.0](http://creativecommons.org/licenses/by/3.0/), compiled by **congusbongus**.

---

## In-app credits

All seven third-party sounds above (`ui_click`, `toast_success`, `toast_error`, `mainmenu`,
`alert_guard`, `alert_camera`, `missionmusic`) are also credited inside the app, at
**Settings → About → Credits & Licenses** — see
`SOUND_CREDITS` in `paywall-build/src/commonMain/kotlin/ui/SettingsScreen.kt`. Keep the two lists
in sync: this file is the detailed record (source links, exact licence), the in-app panel is the
short player-facing version.

## Resolved

- **`resources/mainmenu.mp3`** — previously undocumented. Confirmed 2026-09-05: the owner supplied
  the same file (byte-identical, checked by hash) with its Pixabay credit, resolving the gap this
  section used to flag.
