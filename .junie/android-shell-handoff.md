# Android shell + watch-ad-to-continue — handoff (2026-09-03)

Context for a new chat picking up Android work on **Infiltrate: Shadow Heist**. Read
`.junie/guidelines.md` first for the project as a whole and its "Watch ad to continue" section
for the original design/architecture reasoning; this file covers what happened after that,
picking up from real on-device testing.

## What we did this session

1. **Built `android-shell/` from scratch** — there was no Android host at all before this (KorGE's
   own `targetAndroid()` only ever produced a gameplay-only app, no menu). It's a genuinely
   separate Gradle build, not a subproject of the root build or part of `paywall-build`'s
   composite build — the root build is locked to Kotlin 2.0.20 (KorGE's requirement) and
   `paywall-build` needs a newer Compose-compatible Kotlin, so a subproject would have inherited
   whichever one was wrong. `android-shell` runs its own Kotlin 2.4.10 / AGP 9.1.0 / Gradle 9.3.1
   toolchain, consumes `:game`'s game/engine code by compiling it directly from source
   (`kotlin.srcDirs("../src/game/model", "../src/game/scene")` — `:game`'s Android target is
   application-shaped, not library-shaped, so it can't be depended on as a compiled artifact the
   way `paywall-build` can), and consumes `paywall-build`'s Compose UI as a real published
   `mavenLocal()` artifact.
2. **Wired the real "watch ad to continue" flow on Android** — single `MainActivity`, Compose
   menu and an embedded `KorgeAndroidView` both stay alive the whole time with only visibility
   toggling (never destroyed/recreated), matching the pattern already proven on iOS. Unlike iOS
   (two separate Kotlin/Native frameworks needing a Swift poll-loop go-between), Android's bridge
   is just a plain shared Kotlin object since everything runs in one JVM/APK.
3. **Found and fixed real bugs, all via actual on-device testing** (none of this was caught by
   compiling — see `.junie/guidelines.md`'s own recurring lesson: a build succeeding is not the
   same as it working):
   - Main menu's button-stack scale had a floor (0.85 of a 720dp reference) that assumed a phone
     in landscape needed *more* legibility margin than it actually did — in practice it clamped
     real devices up to ~1.57x the size that fits, pushing STORE/SETTINGS off-screen. Lowered the
     floor to 0.55 (still keeps the 84dp-reference button height above the 48dp touch minimum at
     realistic phone heights) and made the button column scrollable as a fallback.
   - Menu music kept playing after backgrounding the app — `DisposableEffect`'s `onDispose` only
     fires on Compose recomposition, not on the real Activity being stopped. Added a
     `LifecycleEventObserver` (`MenuMusic.android.kt`) to pause/resume on `ON_PAUSE`/`ON_RESUME`.
   - The mission dossier card (bottom-right paper note) doubled as a hidden second PLAY trigger —
     made it decorative only.
   - Game engine loaded to a grey screen — `android-shell` never bundled the repo's `resources/`
     folder as Android assets (KorGE's own Android build pipeline does this automatically;
     `android-shell` doesn't run that pipeline). Fixed via `assets.srcDirs("../resources")`.
     Same root cause took out the menu background video/music - added `res/raw/bg1080p.mp4` too
     (`MenuMusic.android.kt` already had an `assets/` fallback, so it didn't need a `res/raw` copy).
   - On-screen gameplay buttons (movement/jump/crouch/interact) used KorGE's `.mouse{}` API, which
     tracks one pointer for the whole scene - holding one button while pressing a second dropped
     whichever was pressed first. Swapped to `.singleTouch{}` (`GameplayScene.kt`), which KorGE
     genuinely supports and tracks each finger by its own touch id, independently per button.
   - Bigger touch targets on the gameplay buttons; repositioned the action cluster (crouch now
     level with jump instead of arced above it, per explicit direction).
   - Status bar/nav bar hidden (immersive fullscreen), re-applied on window focus regain.

## What's still open — do not assume any of this works, it hasn't been confirmed since the last fix

- **AdMob is currently pointed at Google's TEST rewarded ad unit**, not the real one
  (`AdUnitIds.android.kt`, marked `TEMPORARY diagnostic swap`). This was a deliberate diagnostic
  step: the real ad unit was failing to load fast enough that the continue screen just flashed
  back to gameplay. Whether that's the real (newly-approved) AdMob account simply not serving yet,
  or an actual bug in the Android continue-ad flow, was **not yet confirmed** as of this handoff
  — that was the very next thing to check. Swap `REWARDED_CONTINUE` back to
  `ca-app-pub-7912148730700666/8683118378` once confirmed either way, and never ship the test ID.
- **The `.singleTouch{}` multi-button fix has not been confirmed on-device.** It was the last code
  change before this handoff; no test report came back on whether two buttons can genuinely be
  held at once now.
- **The menu scale-floor fix (0.55) was tuned off one reference screenshot**, not measured against
  the actual device. Worth a real screenshot check before considering it settled.
- **Whether `View.GONE` actually stops `KorgeAndroidView`'s internal `GLSurfaceView` render
  thread (vs. rendering invisibly and burning battery) has never been measured** — same caveat
  called out in `MainActivity.kt`'s own doc comment. The iOS switch-spike measured this
  rigorously for KorGE's iOS view; Android never got the equivalent measurement.
- **Level-select doesn't actually switch to a different level once the KorGE engine is already
  warm** — `MainActivity.startLevel()` only loads a level the first time; subsequent taps just
  show whatever's already running. This is a pre-existing limitation shared with iOS
  (`ios-shell`'s `AppDelegate.swift` only ever calls the no-arg `makeViewController` overload,
  discarding the level id too), not something newly introduced here — flagged, not fixed.
- **No Android CI.** `.github/workflows/` only has `gradle.yml` (plain `jvmTest`) and
  `ios-build.yml`. Every Android fix this session was verified by building locally and having the
  user test the APK by hand - there is no automated way to catch an Android regression the way
  `ios-build.yml` catches iOS ones.
- **The developer workflow for `android-shell` now has a real extra step**: `paywall-build` has to
  be republished (`./gradlew :paywall-build:publishToMavenLocal`) before `android-shell` picks up
  any paywall-build change, since it consumes it as a `mavenLocal()` artifact, not a live
  subproject. Easy to forget and get a stale build.
- **Real device (not just whatever the user tested on) untested.** Same caveat as every iOS spike
  in `guidelines.md` — one device having proven something is not the same as it being generally
  true.
