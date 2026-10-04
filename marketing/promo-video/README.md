# Infiltrate: Shadow Heist - store video

Remotion project for the Google Play promo video (1920x1080, 60 fps, 37 s).

- Preview: `npm run studio`
- Render: `npm run render` -> `out/promo_1080p.mp4`

The cut is timed in `src/Promo.tsx` (`T` holds each beat's start and length in frames). To add
music, drop the track in `public/` and set `MUSIC` in `src/Promo.tsx`, then re-time `T` to its beats.

`public/clips/` (gitignored) holds real gameplay captured from the desktop build: each level's
walkthrough-test autopilot inputs were replayed in the game at a fixed 60 Hz step and recorded
with ffmpeg (`ddagrab` + NVENC) at the phone's 2.17:1 aspect, 2340x1080.
