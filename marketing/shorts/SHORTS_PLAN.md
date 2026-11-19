# Shorts plan: TikTok, Instagram Reels, YouTube Shorts

Organic only, faceless (gameplay, optional AI voiceover or AI presenter). One vertical file
(1080x1920, 60 fps) goes to all three platforms.

## What successful games do (research, Nov 2026)

Sources are developer post-mortems and marketing guides (self-reported numbers, mostly PC games
counting wishlists rather than mobile installs). Where they agree:

1. **The edit matters more than the footage.** The Matriarch's dev reposted almost the same video
   cut faster, with sped-up clips, and went from 10k to 7M views. Keep shorts under ~15 s and cut
   dead time; speed runs and walks up 1.2x to 1.4x.
2. **Question on top, video, CTA at the bottom.** Phrase hooks as a challenge to the viewer
   ("Would YOU make this jump?", "Can you spot the guard?"), not a claim ("impossible level").
3. **Make people comment.** Ask for answers or ideas in the video and the pinned comment, then
   answer good comments with a new video (TikTok's "reply with video"). One dev's request for
   ideas drew hundreds of comments, and comments push reach.
4. **Loops count as views.** Make the last frame flow back into the first.
5. **Homemade beats polished.** Screen recording + text-to-speech outperformed a polished cut for
   one dev; storytelling and real gameplay beat generic ads in mobile UA too. Keep the cinematic
   grade light and never fake gameplay (it also has to match the store page).
6. **Numbered series** ("Part 2", "Day 14", "Attempt 37") make viewers look for the others.
7. **Before/after and progression clips** (e.g. a prop from sketch to final silhouette) were one
   studio's biggest format (8.5M views on one).
8. **CTA every time**, big and readable for ~2 to 3 s, plus "link in bio". Only ~1 in 2,000
   viewers converted for one studio, so the ask matters.
9. **Consistency over virality.** 2 to 5 posts a week; most videos get under 1,000 views and one
   hit a month is normal. A hit lifts older videos too, so re-upload strong ones after a spike.
10. **Re-edit flops**: new first second, faster cut, repost. Cross-post to Reels and Shorts a day
    or two later with no TikTok watermark; a TikTok flop can still work on Reels.
11. **Trending sound** on every post, even at near-zero volume under the game audio.
12. **Measure by spikes, not links.** Bio-link UTMs caught only ~10% of real traffic for one dev.
    Compare Play Console / App Store installs on the days after each post.

Sources: [howtomarketagame: 7 TikTok tips](https://howtomarketagame.com/2022/02/07/seven-great-tips-for-marketing-your-indie-game-on-tiktok/),
[Game Developer: 400 to 20,000 wishlists with 1 video](https://gamedeveloper.com/blogs/from-400-to-20-000-wishlists-with-1-video-how-tiktok-can-make-your-game-go-viral),
[Game Developer: 120M TikTok views, 60k wishlists](https://www.gamedeveloper.com/game-platforms/this-game-used-tiktok-to-get-120m-views-60k-steam-wishlists-),
[presskit.gg TikTok field guide](https://presskit.gg/field-guides/tiktok-indie-game-marketing),
[Lancaric: UA tips Q3 2025](https://lancaric.substack.com/p/11-tips-for-killer-ua-operations-420),
[Opus: Shorts length and retention](https://www.opus.pro/blog/ideal-youtube-shorts-length-format-retention).

## Rules for every short

- **One idea**, 6 to 15 s. No montage, no trailer feel. Speed up anything that isn't the moment.
- **Hook on screen in the first second**, readable muted.
- Text stays in the safe band: below ~300 px from the top, above ~450 px from the bottom, away
  from the right-hand buttons.
- **Loop**: the last gameplay frame should flow back into the first.
- **End card**, 2.4 s: logo, tagline, and the official Google Play and App Store badges, on screen for about 2 s.
- **A question for the comments** in every pinned comment.
- **No music in the render.** Add a trending sound in each app when posting (YouTube: Audio Library).
- **No em dashes** in on-screen text, captions or comments.
- **AI voice or AI person**: tick the AI label on every platform (TikTok "AI-generated content",
  Instagram "AI info", YouTube "altered or synthetic content"). The AI presenter is a narrator
  character: no fake player reviews, never presented as the developer.

## Posting rhythm

3 to 5 shorts a week for 4 weeks. Post on TikTok first, then the same file on Reels and Shorts a
day or two later. Keep a consistent time and try two slots (one source found mornings and early
afternoon US time best for a young audience). After 4 weeks keep the formats that beat your median
views and watch time, retire the rest, and re-cut the flops with a new first second and a faster
edit before giving up on them.

## Extra formats (from the research)

| Format | Example | Notes |
|---|---|---|
| **Before/after** | A prop or level from rough draft to final silhouette, hard cut at the reveal | Fits the realdraw prop work and chapter 2 levels |
| **Reply to a comment** | "@user asked if you can hide in the rain. You can." | Use TikTok's reply-with-video on good comments |
| **Attempt N** | "Attempt 12" counter on a hard section, ends on the clean pass | Series, comment bait ("what attempt would you clear it?") |
| **Bloopers** | Funny physics or AI moments from testing | Bugs and glitches are reliably shareable |
| **Ask for ideas** | "What gadget should the thief get in chapter 2?" | Hundreds of comments for one dev; feeds the next video |

## Batch 1

Status: **draft** = rendered from footage already on disk, **needs takes** = record first
(see "Takes to record").

| # | Id | Format | Level | Status |
|---|---|---|---|---|
| 1 | `near-miss` | Near miss | 03 First Contact | draft |
| 2 | `hook-swing` | Mechanic | 05 The Crane Yard | draft |
| 3 | `would-you` | Would you make it? | 05 The Crane Yard | draft |
| 4 | `attempt-37` | Fail | 03 First Contact | needs takes |
| 5 | `laser-run` | Perfect run | 04 Moving Target | needs takes |
| 6 | `puzzle-crate` | Puzzle POV | 06 Stolen Manifest | needs takes |
| 7 | `story-1` | Story serial | 01 + 02 | needs VO |
| 8 | `story-2` | Story serial | 10 Below the Yard | needs takes + VO |
| 9 | `story-3` | Story serial | 11 The Prisoner | needs VO |
| 10 | `load-run` | Mechanic | 09 Deja Vu | needs takes |

## Batch 2 (built from footage on disk)

| Id | Level | Beats | Length |
|---|---|---|---|
| `dont-stand-up` | 04 | Crouch-walk under low slabs in the dark, lasers ahead. **DON'T. STAND. UP.** then **NOW YOU CAN.** as he stands. 1.15x | 9.2 s |
| `storm-run` | 02 | Hopping hanging loads in the rain. **THE GROUND IS OFF LIMITS.** then lightning + thunder: **THEN THE STORM HIT.** | 8.4 s |
| `truck-run` | 01 | Vault crates onto a truck convoy. **THEY PARKED THE TRUCKS.** then **SO I USED THEM.** 1.2x | 8.6 s |
| `story-1` | 01 + 02 | Part 1 in captions: **THE JOB WAS SIMPLE. / GET IN. FIND CONTAINER 17. / BUT WHEN I GOT THERE... / IT WAS GONE.** Lightning on the cut. End card: **PART 2 SOON.** | 13.1 s |

Footage on disk that doesn't work yet:
- **Old autopilot captures (`footage/temp/`) can desync**: the recorded inputs replay into a newer
  build of the level and the player does things real play can't. Level 5 from ~27.5 to 31.8 s
  drops into the pillar gap and floats back out with an impossible jump (this was `nobody-saw`,
  now removed). Check every temp clip for floating, impossible jumps, falls the level shouldn't
  allow, and idle waits before using it. Fresh takes don't have this problem.
- `fresh/S08` (level 8 cart) ends on MISSION FAILED, but the cause is off screen. Don't use it
  as a fail short; record attempt-37 instead.
- `temp/a_l11` (prisoner) is shot so wide that both characters are tiny on a phone. Story-3 needs
  the fresh S19 take.
- Level 7's wind isn't visible on screen (only the lean), so RESIST needs a take with steam or
  debris blowing.

### 1. near-miss (built)
One continuous shot in a square window (blurred gameplay behind it), so the player, the crate and
the guard on the hanging load are all in frame.
- 0.0 s: crouched behind a crate on the walkway, a guard walks right underneath with his torch.
  On screen: **HE NEVER LOOKED UP.**
- 2.65 s: his partner on the hanging load turns his torch onto the crate.
  On screen: **HIS PARTNER DID.**
- 4.7 s: the beam sits just over the player's head. On screen: **THE CRATE SAVED ME.**
- End card.
- Don't use level 3 from 18.9 s for near misses: an alert marker pops over a guard there, and in
  the vertical crop it's off screen, so it looks like a miss when he was actually seen.

### 2. hook-swing (built)
- Run along the moonlit rooftops into three crane-hook swings in a row, at 1.2x speed. The
  replay's ~2 s idle on a block after the first swing is cut out.
- On screen: **YOU CAN SWING ON CRANE HOOKS.** A counter pops 1, 2, 3 on each grab.
- End card.

### 3. would-you (built)
- Run up to the pillar field, jump, **freeze mid-air**, screen dims.
  On screen: **WOULD YOU MAKE THIS JUMP?**
- Unfreeze: pillar to pillar, onto the hook, swing, land. On screen: **NO STOPS.**
- End card. Pinned comment: "Honest answer: how many tries would it take you?"

### 4. attempt-37
- Hook: **ATTEMPT 37** in the corner, counter ticking up through 3 quick fails (caught by a
  torch, MISSION FAILED sheet), then one clean pass. Last word on screen: **ATTEMPT 40.**

### 5. laser-run
- Hook: **ZERO DETECTIONS. NO STOPS.** One continuous pass through the dark laser section.

### 6. puzzle-crate
- Hook: **HOW DO YOU GET ACROSS?** Show the gap and the lever, freeze 2 s, then pull the lever,
  ride the swinging crate across. Comment bait: "Got it before the reveal?"

### 7 to 9. story serial (VO or AI presenter)
Noir narrator, short lines, each part ends on a cliffhanger. Worth testing one part with TikTok's
built-in text-to-speech voice too: the homemade sound outperformed polish for one developer. Generate one audio/video file per
part from the lines below (lines are separate so captions can be timed to them).

**story-1** (L1 arrival, L2 rain)
1. "The job was simple."
2. "Get into the shipyard. Find Container 17."
3. "Nobody sees you. Nobody remembers you."
4. "But when I reached the drop point..."
5. "Container 17 was gone."
On screen at the end: **PART 2 TOMORROW**

**story-2** (L10 below the yard)
1. "Someone moved it. Fast."
2. "The trail led under the yard."
3. "Tunnels that aren't on any map."
4. "And at the end of them, a cell."
5. "With someone inside who knew my name."

**story-3** (L11 the prisoner)
1. "I came for a container."
2. "I left with a prisoner."
3. "Every guard in the yard is looking for us now."
4. "Two of us. One way out."
5. "Infiltrate. Free on Google Play and the App Store."

### 10. load-run
- Hook: **THE FLOOR IS OFF LIMITS.** Run the whole stretch on top of the hanging loads in the rain.

## Takes to record

Use `tools/media_capture_tool.ps1`, preset **"Promo - 16:9 at 2304x1296 (1 window)"**, UI hidden
unless noted, **F10** to start and stop. Takes land in `screenshots/promo_1080p/`; name them
`<short id>_take<N>.mp4` if you can. Same playing tips as `marketing/playstore-video/SHOT_LIST.md`:
keep the action within about two character-heights of you (the vertical crop is narrow), and stay
away from a level's first and last screen.

| Short | Level | What to record |
|---|---|---|
| attempt-37 | 03 | 3 to 4 takes of getting caught by a torch (UI ON, keep recording through MISSION FAILED), plus one clean pass of the same spot. |
| laser-run | 04 | The dark laser section in one go, no stops, 2 to 3 takes. |
| puzzle-crate | 06 | 2 s still on the gap with the lever in frame, then lever, crate swing, crossing. |
| story-2 | 10 | 15 s of the tunnels, including a switch and what it opens. |
| load-run | 09 | The suspended-load stretch in the rain without touching the ground. |
| story-1, story-3 | 01, 02, 11 | Already covered by footage on disk; fresh takes of S14 / S19 from SHOT_LIST.md will look sharper. |

## AI voice and presenter files

- **Voice only**: one file per short, `marketing/playstore-video/public/vo/<id>.mp3`.
- **Presenter**: `marketing/playstore-video/public/presenter/<id>.mp4`, 1080x1920, on a flat pure
  green background (#00FF00, even lighting, no green clothing). The template keys out the green
  and places the presenter bottom-left over the gameplay; its audio is used as the voiceover.
  If the green key looks bad, set `presenter.mode` to `'box'` for a picture-in-picture frame.

## Rendering

From `marketing/playstore-video`:

```
npm run short -- near-miss          # 540p draft  -> out/shorts/near-miss_draft.mp4
npm run short -- near-miss --final  # 1080p60     -> out/shorts/near-miss.mp4
npm run studio                      # preview and tweak every short live
```

Each short is one entry in `src/shorts/shorts.ts`: clips (source, in/out, crop), freeze beats,
on-screen text with timings, optional VO/presenter/SFX.
