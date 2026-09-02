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
| `resources/sfx/crouch.wav` | Crouch stance change | `crouch_new.mp4` audio track | This project | Own work |
| `resources/sfx/climb.wav` | Vault / climb | `climb.mp4` audio track | This project | Own work |
| `resources/sfx/takeoff.wav` | Jump take-off | `jump_new.mp4` audio track, 0.765–0.955 s | This project | Own work |
| `resources/sfx/impact.wav` | Landing | `jump_new.mp4` audio track, 1.612–1.952 s | This project | Own work |
| `resources/sfx/ui_click.wav` | Every button and tap | [Kenney UI Audio](https://kenney.nl/assets/ui-audio) (`click3.ogg`) | [Kenney](https://kenney.nl) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `ios-shell/Resources/ui_click.wav` | Menu buttons on iOS | Same file as above, duplicated for bundling | [Kenney](https://kenney.nl) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `resources/mainmenu.mp3` | Menu music | *Not yet documented — see "Unresolved" below* | — | — |

### Notes on the plate cuts

`takeoff.wav` and `impact.wav` are two different moments of the same take. The take-off sits
about 34 dB below the landing in the source recording, so each was peak-normalised on its own
rather than sharing one gain; the intended balance between them is restored in
`GameAudio`'s gain table (`TAKEOFF_GAIN` / `LANDING_GAIN`) instead of being baked into the files.

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

## Unresolved

- **`resources/mainmenu.mp3`** — this track predates the current sound work and its origin is
  not recorded anywhere in the repo. It ships in both `resources/` and `ios-shell/Resources/`.
  Its licence needs establishing before release; if it cannot be established, it needs replacing.
  This is flagged rather than guessed at, because a wrong guess here is the expensive kind.
