# Play Store video: shot list (fresh footage)

The finished video is 30 s and cut fast: most shots are on screen for 1 to 2 seconds. The same
takes feed two cuts, a **16:9** (1920x1080) and a **9:16** (1080x1920). Each take below still needs
**8 to 20 s of clean action**, so the edit has room to pick the best moment and to run slow-motion
or speed ramps.

## Setup (once)

1. Run `tools/media_capture_tool.ps1`.
2. Set **Platform** to **"Promo - 16:9 at 2304x1296 (1 window)"**. This preset is new. It renders
   the game natively, slightly above 1080p, so I can zoom in on the action without losing
   sharpness. It records on the GPU, which keeps a steady 60 fps (the old capture method drops
   frames at this size). Takes are saved to `screenshots/promo_1080p/`. Keep other windows off the
   game window while recording: this method captures whatever is on screen.
3. Leave **"Hide ALL Controls, Objectives & Pause UI"** ticked unless a shot below says **UI ON**.
4. Pick a level, then press **Launch**. Play with the keyboard; **F10** starts and stops recording.

Keys: arrows/WASD move · Space/W jump · S/C/Ctrl crouch · E/F interact · R restarts the level ·
**F2** gives 3 of every gadget · gadget slots: **1** Checkpoints, **2** Remote Trigger,
**3** Guard Shield, **4** Invisibility Cloak, **5** Stealth Boots.

## How to play for the camera

- Start recording about 2 s before the action and stop about 2 s after it.
- Play like a speedrunner in a movie: commit to every move, with no hesitating, backtracking or
  pausing to think. Do a few takes and keep them all; I'll choose.
- **Near misses are the best footage.** A torch beam passing just over your crouched head beats a
  safe play every time.
- Face right (the direction of travel) for most shots.
- **Keep the action close to you.** The 9:16 cut shows only the middle third of the screen width,
  a bit under two character-heights either side of you. A guard or beam you slip past at arm's length
  works in both cuts; one half a screen away only works in 16:9.
- **Avoid the first and last screen of a level.** The camera keeps you centred everywhere else,
  which is what the 9:16 crop relies on; at a level's two ends it stops following and you drift
  to the edge.
- One take = one file. Recording shots in the order below helps me, but isn't required. If you
  like, rename files to `S05_take2.mp4` and so on; otherwise I'll identify them from contact sheets.

## Shots

**A** = must have, **B** = nice to have.

### Hook (0:00-0:03): the first thing muted viewers see

| # | Pri | Level | What to capture |
|---|---|---|---|
| S01 | A | 05 The Crane Yard | Run flat out across the moonlit rooftops and clear the biggest gap you can find. Moon in frame. |
| S02 | A | 05 The Crane Yard | Hook swing: grab, full swing, release, land on the far side. 3+ takes. |

### "Every light is a trap" (0:03-0:06)

| # | Pri | Level | What to capture |
|---|---|---|---|
| S03 | A | 03 First Contact | Crouch in the dark while a guard's torch beam sweeps; let it pass **just** over or next to you, then slip past behind him. |
| S04 | B | any level with a security camera | The camera sweeps; time a dash through the gap in its cone. |

### Mechanics montage (0:06-0:13): one word on screen per shot

| # | Pri | Level | What to capture |
|---|---|---|---|
| S05 | A | 03 or 06 | **SNEAK**: a long crouch-walk right behind a guard's back. |
| S06 | A | 01 Night Arrival | **CLIMB**: mantle up a tall crate stack or ledge in one smooth move. |
| S07 | A | 02 Cargo Yard | **JUMP**: gap jumps in the rain. Keep rolling until a lightning strike lands in frame (worth several takes). |
| S08 | A | 08 Relocation | **PUSH**: push the cart a good distance, with the suspended loads visible. |
| S09 | B | 07 Service Tunnel | **RESIST**: walk against the turbine wind. |
| S10 | B | 06 Stolen Manifest | **PULL**: the lever, then the crate swinging, then crossing on it. |
| S11 | B | 07 Service Tunnel | **RUN**: a flat-out sprint down the lit tunnel past the metre stencils. |

### "12 missions" grid (0:13-0:18): six levels playing at once, so every level needs a take

| # | Pri | Level | What to capture |
|---|---|---|---|
| S12 | A | 09 Deja Vu | Run along the tops of the suspended loads in the rain, never touching the ground. |
| S13 | A | 10 Below the Yard | Flip a switch and show what it does. |
| S14 | B | 01 Night Arrival | Establishing run past the truck and fences. |
| S15 | B | 06 Stolen Manifest | 10 s of anything readable (it's for the grid). |
| S25 | B | 12 Final Escape | 10 s of its most recognisable stretch (for the grid). |

### "Don't get seen" (0:18-0:22)

| # | Pri | Level | What to capture |
|---|---|---|---|
| S16 | A | 04 Moving Target | The dark spotlight section: thread through the lasers without stopping. |
| S17 | A | 03 or 06 | **Get caught on purpose**: walk into a guard's torch beam and let detection fill. Keep recording through the MISSION FAILED sheet. Do one take with **UI ON** as well. |
| S18 | B | 07 or 08 | Hide while the patrol rover rolls past. |

### Escort (0:22-0:25)

| # | Pri | Level | What to capture |
|---|---|---|---|
| S19 | A | 11 The Prisoner | Free the prisoner (the cell gate rolls up and he stands) and keep recording while he walks after you. |
| S20 | A | 11 The Prisoner | Open a door just before the prisoner reaches it; ideally a guard is beyond it. |

### Gadgets (0:25-0:27): press F2 first

| # | Pri | Level | What to capture |
|---|---|---|---|
| S21 | A | 03 or 06 | **Invisibility Cloak (4)**: walk straight through a guard's torch beam, unseen. |
| S22 | B | any | **Guard Shield (3)** or **Stealth Boots (5)** in use. |

If gadget keys don't fire with the UI hidden, record these with **UI ON**; I'll crop around the HUD.

### Ending (0:27-0:30)

| # | Pri | Level | What to capture |
|---|---|---|---|
| S23 | A | any short level, **UI ON** | Finish the level and keep recording through the HEIST COMPLETE dossier and its stars. |
| S24 | B | any, **UI ON** | 10 s of normal play with the touch controls visible (for a phone-frame shot). |

## When you're done

Tell me the takes are in `screenshots/promo_1080p/`. I'll log them, then cut the video.
