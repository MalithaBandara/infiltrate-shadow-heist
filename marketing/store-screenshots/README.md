# Store screenshots

Ready to upload:

| Folder | Store slot | Count |
|---|---|---|
| `out/google-play_1920x1080/` | Google Play: phone, 7" tablet and 10" tablet (the same files fit all three) | 8 |
| `out/app-store_iphone-6.9_2868x1320/` | App Store: 6.9" iPhone (Apple scales them down to smaller iPhones) | 9 |
| `out/app-store_ipad-13_2752x2064/` | App Store: 13" iPad (scaled down to smaller iPads) | 9 |

All files are 24-bit PNG, with no alpha, and every gameplay frame is real. The App Store set has one extra
shot (`07_tunnels`) because Play caps phone screenshots at 8. Upload in file-name order.

## Research behind the set

- **The first screenshot carries a landscape game.** On iOS a landscape game shows only its first
  screenshot in search results, and only about 9% of visitors scroll the gallery. So #1 has to sell
  the game on its own: logo, mood, the core fantasy and a 3-word promise.
- **One idea per shot, with a short, benefit-led caption** (3 to 6 words, 7 at most on #1). Captions
  lift conversion by about 20%; studies of top-grossing games show most lead with a benefit.
- **Real gameplay dominates the frame.** Apple guideline 2.3.3 requires the app in use. Shadow Fight 2
  (from the studio behind Vector) puts a caption banner over full gameplay, keeps the HUD visible in
  some shots, and uses a collage for variety. Robbery Bob, a stealth heist game with 100M+ installs,
  plays up getting caught. Premium atmosphere games such as LIMBO and Alto's Odyssey skip captions,
  but they rely on reputation that a new game doesn't have yet.
- **Google Play preview-asset rules** (these apply to the video too): no "free", "best", "#1", "top",
  "new" or "sale"; no calls to action ("play now"); no device frames or store badges; keep text
  light. A game needs at least three 16:9 screenshots of 1920x1080 or larger to be eligible for
  promotion.

Sources: [Play Console: preview assets](https://support.google.com/googleplay/android-developer/answer/9866151),
[Udonis: game screenshot guide](https://www.blog.udonis.co/mobile-marketing/mobile-games/app-store-screenshots),
[Gummicube: portrait vs landscape](https://blog.gummicube.com/2017/12/portrait-v-landscape-aso-best-practices-to-make-or-break-your-creatives),
[SplitMetrics: ZiMAD +32% case](https://splitmetrics.com/cases/zimad-app-store-screenshots-redesign/),
[App Store screenshot sizes 2026](https://screenkit.tools/specs/app-store-screenshot-sizes), and the live store pages of
Shadow Fight 2, Vector, LIMBO, Alto's Odyssey and Robbery Bob.

## Regenerating

```
npm install
npm run render          # all three sizes into out/
node render.mjs --scale 0.5 --out out/review --only play-02   # quick look at one shot
```

Copy, crops and store membership live in `src/data.ts`; the layouts are in `src/Screenshot.tsx`.
Source frames in `public/frames/` came from `capture/capture_level.ps1`, which launches a level of the
JVM build, plays a scripted key sequence and grabs lossless frames. `frames/` (git-ignored) holds
every captured take if a different moment is wanted.
