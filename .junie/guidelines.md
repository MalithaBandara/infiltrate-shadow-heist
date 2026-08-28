# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game, visually similar to Shadow Fight / Vector,
with a heist/infiltration objective similar to Robbery Bob.

**Target platforms: Android and iOS. Both are required — this is a
cross-platform Kotlin Multiplatform hackathon submission (Shipaton 2026),
and the app must work on both platforms.**

**iOS CI status (2026-08-25): GREEN.** `ios-build.yml` run #16 (commit
`991a54a`) completed with `BUILD SUCCEEDED`/`BUILD SUCCESSFUL in 8m 50s`
— a real, complete, unsigned `.app` for iOS Simulator, all the way
through Kotlin/Native compile+link, KorGE's XcodeGen project generation,
and `xcodebuild` with ad-hoc simulator signing. This is the first fully
successful iOS build after a long chain of fixes (see sections below for
the full history: gradlew executable bit, Gradle configuration cache,
source-set conflict, RevenueCat klib ABI wall, and finally dropping
RevenueCat from iOS entirely). Don't assume it's still broken - check
the latest Actions run before redoing any of that investigation:
https://github.com/MalithaBandara/infiltrate-shadow-heist/actions
Minor cosmetic note: the built app is named `unnamed.app` because
`build.gradle.kts`'s `korge {}` block only sets `id`, never `name` -
easy fix whenever it matters (`korge { name = "..." }`).

## LOCKED WORKING CONFIGURATION (verified 2026-08-25, commit `0b958c3`)

**These versions are load-bearing. Do not upgrade any of them without
re-running the full iOS build in CI first** — this exact combination is
the only one that's been proven to actually link and build on iOS,
after a long chain of version-compatibility failures documented in the
sections below. A version bump that looks safe (e.g. "just a patch
release") can silently reintroduce the klib ABI wall or the source-set
conflict this session fixed.

- **KorGE: `6.0.0`** (`gradle/libs.versions.toml`). Note: prompts this
  session repeatedly referred to "KorGE 7.0.0-SNAPSHOT" as if already in
  place — that was never true, checked directly and repeatedly all
  session (see "Verify version-related claims" section below). It is
  still `6.0.0` as of this commit.
- **Kotlin: `2.0.20`** — confirmed authoritatively via
  `./gradlew.bat buildEnvironment` / `dependencies` (`kotlin-gradle-plugin-api`
  and `kotlin-stdlib` both resolve to `2.0.20`), NOT `1.9.22` as earlier
  project notes assumed. This matches everything observed in CI all
  session: the Kotlin/Native backend is
  `kotlin-native-prebuilt-macos-aarch64-2.0.20`, and the klib ABI
  resolver's default is `1.8.0` (Kotlin 2.0.x's default) — this is the
  ceiling that ruled out every `purchases-kmp-core` iOS version `2.0.0+`
  and above.
- **Gradle: `8.8`** (seen in CI: "Welcome to Gradle 8.8!").
- **JDK: `21`** — `zulu` distribution in CI (both workflows). On this
  Windows dev machine, JDK 21 (Temurin) is installed but is NOT the
  default `JAVA_HOME` (that's JDK 19) — override explicitly
  (`export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot"`
  in Git Bash) for any local `gradlew` command; JDK 19 fails with
  "Dependency requires at least JVM runtime version 21."
- **`purchases-kmp-core`: `1.9.0+14.3.0`, Android-only.** Declared via
  `add("androidMainApi", ...)` in `build.gradle.kts`. iOS has **no
  dependency on it at all** — deliberately removed (commit `487a1dc`).
  Neither platform's bridge class (`src@android/PurchasesBridge.android.kt`,
  `src@ios/PurchasesBridge.ios.kt`) makes any real RevenueCat API call
  yet — both are stubs, Android's despite having the real dependency
  available to use whenever that gets built. `src@ios/PurchasesBridge.ios.kt`
  was fixed 2026-08-25 (commit `0b958c3`) to return `onResult(false)`
  from `purchase()` instead of a fake `onResult(true)` — a deliberate,
  honest no-op, not a partial integration. No paywall UI exists yet
  anywhere in the codebase to wire a real "coming soon" message into
  (`main.kt` is still the untouched korge-hello-world demo scene) - add
  that when the paywall UI itself gets built.
- **What actually fixed the iOS link failure**: NOT vendoring
  `PurchasesHybridCommon.framework` or adding any `linkerOpts` — that
  path was investigated (see "RevenueCat on iOS is deferred" below) but
  never implemented. The fix was entirely `build.gradle.kts`: removing
  the `iosMainApi` dependency on `purchases-kmp-core` so the
  ABI-incompatible/unlinkable klib is never pulled into the iOS
  compile/link graph at all. Zero framework vendoring, zero linker
  flags, zero CocoaPods integration exists in this repo.
- **Build artifact verified, not just Gradle exit code**: CI log for the
  successful run shows the real Kotlin/Native output
  `build/bin/iosSimulatorArm64/debugFramework/GameMain.framework` being
  copied to `unnamed.app/Frameworks/GameMain.framework`, code-signed,
  and validated by Xcode's `builtin-validationUtility`, before
  `** BUILD SUCCEEDED **`.

## Tech stack
- Engine: KorGE (Kotlin Multiplatform game engine)
- NOT using Compose Multiplatform — no Compose dependencies anywhere in this project
- Targets: Android, iOS, JVM desktop (JVM used for local dev/testing only —
  Android and iOS are the actual shipping targets)
- Payments: RevenueCat via `purchases-kmp-core` only (plain Kotlin SDK).
  Do NOT add `purchases-kmp-ui` — it requires Compose. The paywall is
  hand-built in KorGE UI instead.
  **Android only as of 2026-08-25 — deliberately NOT wired on iOS. See
  "RevenueCat on iOS is deferred" below before assuming otherwise or
  before touching `iosMainApi` / `PurchasesBridge.ios.kt`.**

## Secrets and credentials — CRITICAL

Before ANY git commit or push, scan changed files for:
- API keys (RevenueCat, Google Play, App Store Connect, etc.)
- Passwords or auth tokens
- Signing certificates, provisioning profiles, keystore files
- Any string that looks like a key (long random alphanumeric sequences
  near words like "key", "secret", "token", "password", "credential")

If anything matches:
1. STOP. Do not commit.
2. Warn the user explicitly, showing the file and line in question.
3. Suggest moving the value to GitHub Actions secrets (Settings →
   Secrets and variables → Actions) or a local .env / .gitignore'd file
   instead.
4. Wait for the user to confirm before proceeding.

This repo is PUBLIC. Anything committed is visible to everyone and stays
in git history even if later deleted, unless history is rewritten.
Never assume a placeholder or "TODO: add real key later" is safe to
commit if it resembles a real key format — flag it anyway.

## Keep this file up to date

This file is the first thing a new chat/agent should read for project
context. Whenever you make a decision, discover a constraint, or change
something that a future chat starting from scratch would need to know
(tooling gaps, CI status, build pipeline quirks, unresolved integration
issues, repo/URLs), update the relevant section below — or add a new one
— before ending your turn. Treat stale info here as a bug: if something
below turns out to be wrong or superseded, fix it in place rather than
leaving it for the next chat to rediscover.

## Verify version-related claims against the actual repo, every time

Across several 2026-08-24/25 sessions, prompts have referred to "KorGE
7.0.0-SNAPSHOT" as if it were already in place (e.g. "after the KorGE
7.0.0-SNAPSHOT upgrade" fixing an error, or "now that we're on KorGE
7.0.0-SNAPSHOT"). Every time this was checked directly (`gradle/libs.versions.toml`,
`git log`, `git diff origin/main`, and CI log toolchain paths like
`kotlin-native-prebuilt-macos-aarch64-2.0.20`), the repo was still on
`korge 6.0.0`, unchanged, with no commit/branch/PR reflecting any
upgrade anywhere. This isn't a one-off — treat any claim about the
current KorGE/Kotlin version, or "we upgraded X", as unverified until
checked directly, even if it was stated confidently or restated more
than once. Whatever caused this mismatch (a different project, a change
made somewhere this repo doesn't see, a misremembering) is unresolved -
the safe default is: check `gradle/libs.versions.toml` and recent CI
logs yourself before reasoning from a stated version.

## Repository

- Public GitHub repo: https://github.com/MalithaBandara/infiltrate-shadow-heist
- Default branch: `main`.
- `origin` remote is already set to the above over HTTPS.

## GitHub access from this environment

- The `gh` CLI is NOT on PATH in either shell available here (Bash/Git
  Bash or PowerShell), and isn't findable in common Windows install
  locations either. Don't assume `gh <command>` will work — check with
  `where gh` / `Get-Command gh` first, or just use the fallback below.
- Fallback that works today: Git Credential Manager (`credential.helper
  = manager`, configured system-wide in `C:/Program Files/Git/etc/gitconfig`)
  already has a cached GitHub credential for user `MalithaBandara`
  (target `git:https://github.com`) with `repo` + `workflow` OAuth
  scopes. This means:
  - `git push` / `git pull` over HTTPS to github.com just works, no
    extra auth step needed.
  - For anything `gh` would normally do (create repos, etc.), pull the
    token via `git credential fill` into a shell variable and call the
    GitHub REST API with `curl` directly — never print the token or
    embed it literally in a command string that gets logged. Example
    pattern:
    ```bash
    TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | grep '^password=' | cut -d= -f2-)
    curl -s -H "Authorization: token $TOKEN" https://api.github.com/...
    ```
  - If `curl -d` with inline JSON containing non-ASCII characters (em
    dashes, etc.) fails with "Problems parsing JSON", write the payload
    to a file first and use `--data-binary @file` instead — this is an
    encoding issue with inlining strings through the shell, not a real
    API problem.

## CI workflows (`.github/workflows/`)

**Both workflows below trigger on every push to `main` with no path
filters** — a docs-only commit still fires `iOS Build` (burns real
macOS runner time) alongside `Testing`. Worth adding path filters (e.g.
skip `iOS Build` for changes touching only `.md` files) if
push-triggered noise/cost becomes a problem — not done yet.

- `gradle.yml` — from the original korge-hello-world template. Runs
  `./gradlew jvmTest` on every push, `ubuntu-latest`, JDK 21 (zulu). Had
  `chmod +x ./gradlew` added 2026-08-25 — it was failing "Permission
  denied" on every single run since the initial commit (same
  Windows-executable-bit issue as `ios-build.yml` below, just never
  caught here because nobody was watching this workflow specifically).
- `deploy-js.yml` **removed 2026-08-25.** It built the JS/webpack bundle
  and deployed to GitHub Pages, but JS/Wasm are template leftovers, not
  a real target — this project ships Android + iOS only (see top of this
  file). Got the `chmod +x ./gradlew` fix too and the JS build itself
  went green, but the final `deploy-pages` step 404'd because GitHub
  Pages was never enabled for this repo (`Ensure GitHub Pages has been
  enabled: .../settings/pages`) — asked whether to enable it, decided
  not worth it since JS was never shipping. `targetJs()`/`targetWasm()`
  are still declared in `build.gradle.kts` (untouched, still useful for
  local browser preview during dev) — only the deploy workflow is gone.
  If JS/Wasm targets themselves are ever ruled fully out of scope too,
  those can be removed from `build.gradle.kts` as a separate cleanup.
- `ios-build.yml` — added for this project. Runs on `macos-latest`,
  JDK 21 (zulu, matching the other workflows). Does `chmod +x ./gradlew`
  right after checkout (gradlew loses its executable bit when committed
  from Windows — "Permission denied" on the runner otherwise), installs
  CocoaPods if missing, then runs KorGE's `iosBuildSimulatorDebug` task
  as the real build gate (unsigned iOS Simulator build only — no
  signing or TestFlight upload yet, that's intentionally deferred).
  That task runs with `--no-configuration-cache` (see next bullet) —
  don't remove that flag.

- **KorGE's Gradle plugin is incompatible with Gradle's configuration
  cache.** `gradle.properties` sets `org.gradle.configuration-cache=true`
  project-wide (works fine for the JVM/JS targets), but running KorGE's
  iOS tasks (`iosBuildSimulatorDebug` etc.) with it enabled throws
  NullPointerExceptions inside the plugin itself (`getKorge`,
  `execLogger` both null), preceded by "cannot serialize object of type
  Project ... not supported with the configuration cache" warnings —
  the cache corrupts the plugin's internal state. Fix is `--no-configuration-cache`
  on the specific `./gradlew` invocation, not disabling the setting
  globally (that would needlessly slow down/change behavior for the
  targets that work fine with it). If other KorGE Gradle tasks start
  throwing similar null-pointer errors in the plugin internals, suspect
  this first.
  Afterward it looks for a Podfile under the generated
  `build/platforms/ios` project and runs `pod install` if one exists,
  else logs an explanation (see next section — this is expected to find
  nothing right now). Always uploads `build/platforms/ios` as a build
  artifact for inspection regardless of pass/fail.

## RevenueCat on iOS is deferred (decision made 2026-08-25)

**Current state: `purchases-kmp-core` is Android-only.** `build.gradle.kts`
only has `add("androidMainApi", ...)` — the `iosMainApi` dependency was
removed entirely. `src@ios/PurchasesBridge.ios.kt` is a stub (no real
RevenueCat calls), so this doesn't break anything today, but it means
**iOS currently has no real purchases/subscription functionality** -
`IosPurchasesBridge.purchase()` just calls `onResult(true)` unconditionally.
Don't assume iOS payments work; don't build UI/flows that depend on
real iOS purchase results until this is revisited.

Why: every viable version of `purchases-kmp-core` for iOS was checked
and every path dead-ended (full detail below and in the sections that
follow):
- Every version's iOS klib either fails this toolchain's klib ABI check
  (`1.201.0`+, checked `2.0.0` through `3.5.1`), or
- (`1.9.0`/`2.10.2` line, the only ABI-compatible ones) requires linking
  `PurchasesHybridCommon` at the native framework level, which has
  **no prebuilt binary anywhere** — not CocoaPods, not Swift Package
  Manager, not any GitHub release across either `purchases-hybrid-common`
  or `purchases-ios`. It's source-only; producing a `.framework` for it
  means compiling it from source in CI, which was judged too large/risky
  an undertaking for the timeline and deliberately not attempted.
- Separately, KorGE's own iOS build pipeline (XcodeGen-based) has zero
  CocoaPods/SPM integration of any kind — confirmed via GitHub code
  search of `korlibs/korge` (0 hits for "cocoapods", 0 for "Podfile")
  and reading `Ios.kt`/`IosXcodegen.kt`/`IosProjectTools.kt` directly.
  Even a manually-placed Podfile would never be consumed, since KorGE's
  `xcodebuild` invocation is hardcoded to `-project .`, never
  `-workspace`.

To revisit this later: `RevenueCat.xcframework` (the underlying native
SDK) IS available prebuilt — e.g. https://github.com/RevenueCat/purchases-ios/releases/tag/5.32.0
ships `RevenueCat.xcframework.zip` (~489MB, all Apple platform slices +
dSYMs + docs; only the `ios-arm64_x86_64-simulator` and `ios-arm64`
slices are actually needed, each ~9.5MB). The missing piece is still
`PurchasesHybridCommon` — would need to be built from its source
(`Package.swift` at https://github.com/RevenueCat/purchases-hybrid-common,
depends on `RevenueCat` pinned to an exact version — `5.32.0` for the
`14.3.0` release) via `swift build`/`xcodebuild` in CI, then vendored
alongside `RevenueCat.xcframework` and wired into Kotlin/Native's
`binaries.framework { linkerOpts(...) }` for the iOS targets. Untested.

## RevenueCat version is pinned by iOS klib ABI compatibility, not just Android/JVM metadata

- `build.gradle.kts` pins `purchases-kmp-core` to `1.9.0+14.3.0` for
  `androidMainApi` only now (see "RevenueCat on iOS is deferred" above -
  this was downgraded from `2.10.2+17.55.1`, which itself was a downgrade
  from `3.5.1` — see the Android/JVM metadata-conflict note already in
  this file). The version history below is kept for whoever revisits iOS.
- The `2.10.2+17.55.1` downgrade fixed Android/JVM but a separate,
  iOS-only problem showed up in `ios-build.yml` CI: `:compileKotlinIosSimulatorArm64`
  failed with a KLIB resolver error — "Incompatible ABI version. The
  current default is '1.8.0', found '1.201.0'".
- Root cause, confirmed by downloading klib files from Maven Central
  and reading their manifests directly (`unzip -p <klib> default/manifest`,
  look for `abi_version` / `compiler_version`) rather than guessing:
  RevenueCat's own build toolchain moved from Kotlin 1.9.23 (klib
  `abi_version=1.8.0`) to Kotlin 2.1.x (`abi_version=1.201.0`) starting
  exactly at their package version `2.0.0+15.0.0`. This project's
  Kotlin/Native compiler can only read klib ABI 1.8.0. Every version
  from `2.0.0+15.0.0` up through `2.10.2+17.55.1` is therefore permanently
  unreadable here — this is a hard compiler wall, not something a Gradle
  flag fixes. `1.9.0+14.3.0` is the newest release still on the
  compatible side.
- Android/JVM consume regular JAR/AAR artifacts, not klibs, so this
  specific ABI check doesn't apply there — the earlier Android/JVM
  metadata conflict (3.5.1 vs project's Kotlin metadata level) was a
  different, unrelated check. Downgrading further should only reduce
  that risk, not increase it.
- Consequence for CocoaPods: once a `Podfile` exists (see next section),
  the pod pin should be `PurchasesHybridCommon 14.3.0` (matching
  `1.9.0+14.3.0`'s paired native SDK version), NOT `17.55.1` as earlier
  project notes said — that was written against the since-reverted
  `2.10.2+17.55.1` pin.
- If `purchases-kmp-core` needs to be bumped again in the future (e.g.
  after upgrading KorGE/Kotlin off 1.9.x), re-verify klib ABI
  compatibility the same way before assuming a newer version "should"
  work — don't rely on Android/JVM compiling cleanly as a proxy for iOS
  compatibility, they're checked completely differently.
- **Already tried and confirmed failing (2026-08-24/25): `3.5.1`.** The
  hope was that 3.x's iOS SDK is bundled directly into the klib instead
  of needing CocoaPods (true — its klib `depends` list includes
  `kn-core-cinterop-RevenueCat`/`kn-core`, a natively-bundled SDK), which
  would eliminate the CocoaPods gap below entirely if it worked. It
  doesn't: klib manifest shows `abi_version=2.3.0` (compiler `2.3.20`) —
  an even bigger gap than `2.10.2`'s `1.201.0`. Confirmed on CI: fails at
  `:compileKotlinIosSimulatorArm64` itself (never even reaches the link
  step). Don't re-try any `3.x` version without first re-checking its
  klib manifest — this whole line is Kotlin-2.x-compiled and none of it
  will read as ABI 1.8.0 under this toolchain.
- The bridge classes (`src/PurchasesBridge.kt` and platform variants)
  are still empty stubs with no real RevenueCat API calls wired in, so
  this version is currently unconstrained by actual usage — check the
  `1.9.0+14.3.0` API surface against the current RevenueCat KMP docs
  before writing real integration code against it, since it's several
  major versions behind latest.

## iOS build pipeline — status and known gap

(This CocoaPods gap is why RevenueCat-iOS is deferred — see above. Kept
in full below since the `ios-build.yml` workflow itself is still active
and this is still accurate background for it.)

- KorGE's `targetIos()` has its own iOS build pipeline, confirmed via
  https://docs.korge.org/targets/ios/ — it generates a full Xcode
  project under `build/platforms/ios` via Gradle tasks like
  `iosBuildSimulatorDebug` / `iosInstallSimulatorDebug`. This is
  separate from, and does not use, the standard Kotlin Multiplatform
  `cocoapods {}` Gradle plugin — nothing in KorGE's own docs mentions
  CocoaPods at all.
- There is currently NO `Podfile` anywhere in this repo, and
  `build.gradle.kts` has no `cocoapods {}` block. RevenueCat's
  `purchases-kmp-core:1.9.0+14.3.0` needs `pod 'PurchasesHybridCommon',
  '14.3.0'` linked into the final iOS app/framework for iOS to actually
  work — this is currently unresolved. It is not yet known whether
  KorGE's generated Xcode project has any hook for injecting a Podfile,
  or whether one needs to be hand-authored and copied into
  `build/platforms/ios` post-generation.
- **Confirmed empirically (2026-08-24 CI run, commit `fd46ed6`) exactly
  where this breaks** — it's the final native link step, not the Kotlin
  compile step:
  - `:compileKotlinIosSimulatorArm64` succeeds.
  - KorGE generates the Xcode project fine (`build/platforms/ios/app.xcodeproj`
    via XcodeGen 2.42.0).
  - `:linkDebugFrameworkIosSimulatorArm64` FAILS with:
    `ld: framework 'PurchasesHybridCommon' not found`
  - This is the CocoaPods gap, now confirmed rather than theorized. Next
    step here is almost certainly authoring a `Podfile` (pinning
    `PurchasesHybridCommon 14.3.0`) and getting it into
    `build/platforms/ios` before this link step runs — exact mechanism
    (KorGE hook vs. a CI step that copies one in post-XcodeGen) still
    unresearched.
  - Also noted in that run: `Xcode 26.6 is higher than the maximum
    tested by the Kotlin Gradle Plugin (15.3)` — a warning only so far,
    not a failure, but worth remembering if something flaky shows up
    later on Apple-toolchain-version grounds.
- Earlier `:compileKotlinIosSimulatorArm64` failures along the way (now
  fixed, for reference): a klib ABI mismatch (see previous section) and
  a "Conflicting overloads: actual fun getPurchasesBridge()" error
  caused by `src@native/PurchasesBridge.native.kt` and
  `src@ios/PurchasesBridge.ios.kt` both providing an `actual` for the
  same `expect`. Root cause: KorGE uses a custom source-set hierarchy
  (`kotlin.mpp.applyDefaultHierarchyTemplate=false` in
  `gradle.properties`) where `nativeMain` (`src@native`) is the shared
  parent for all Kotlin/Native leaf targets and `iosMain` (`src@ios`)
  sits below it; since iOS is the only real Kotlin/Native target enabled
  here, both fed `iosSimulatorArm64Main` directly. Fixed by deleting
  `src@native/PurchasesBridge.native.kt` (fully redundant, no other
  native target exists to need it). If a genuine non-iOS native target
  gets added later, that's when `src@native` would need to come back.
- Check the latest Actions run before assuming any of this is stale:
  https://github.com/MalithaBandara/infiltrate-shadow-heist/actions
- Do not assume this is solved just because CI is green on other steps —
  confirm the `iosBuildSimulatorDebug` step itself succeeded and check
  whether the Podfile-search step actually found anything.

## Gameplay Architecture (`commonMain`)

The gameplay logic is decoupled from the rendering engine:
- `game.model`: Engine-agnostic domain layer containing pure data models and simulation math:
  - `Geometry.kt`: `Vec2d`, `Rect`, `Segment2d`, raycasting, angle calculations, line-of-sight checks against occluder rects.
  - `Player.kt`: Full-body player physics (height 96px and width 50px matching the full-stride silhouette to prevent leg and upper body wall clipping), velocity, jumping, gravity, platform snapping, sub-stepped AABB obstacle & entity collision resolution, `NoiseLevel` (SILENT vs NORMAL walk noise radius 180px), and crouching stance (56px crouch height for crawling under low obstacles, reduced speed, silent movement).
  - `Guard.kt`: Waypoint patrol, constant speed movement, direction turnaround, facing angle, eye position, and `GuardState` (`PATROL` vs `INVESTIGATING` with stationary look towards sound direction or visual detection, without moving toward the player, and timeout return to original patrol route).
  - `Vision.kt`: `VisionSystem` for generating FOV vision polygon meshes and detecting player visibility (distance, FOV angle, unoccluded line-of-sight, closest spotted distance).
  - `LevelData.kt`: `LevelData`, `LevelResult` (3-star rating evaluation: completed, undetected, time target), scalable `LevelRegistry` (`DEFAULT_LEVELS`), and `LevelStorage` repository interface with `InMemoryLevelStorage` and `MapBackedLevelStorage`.
  - `GameProfile.kt`: `GameProfile` (coins balance, premium bundle status, music & SFX volume, unlocked levels, tactical powerup inventory), with `GameProfileStorage`, `InMemoryGameProfileStorage`, and `MapBackedGameProfileStorage`.
  - `GameWorld.kt`: Orchestrates player, guard entity collision, platforms, crates/occluders, stopping guard movement upon player detection in vision cone, distance-scaled detection progress (0.3s point-blank to 1.5s max range), alert decay (0.6/s), noise event detection triggering guard investigation, visual loss mid-alert triggering guard investigation, exit zone win condition, level time tracking, and game-over/caught event triggers.
- `game.scene`: KorGE presentation and navigation layer:
  - `UiComponents.kt`: Reusable high-tech UI components (tactical buttons with corner notch accents and responsive hover/press states, glassmorphic panels, dossier cards, top bar with integrated Operative Star status & Heist Coin pill badges, cyber alert toast feedback, atmospheric skyline backdrop, 5-point star graphics).
  - `SplashScene.kt`: Atmospheric launch sequence with encrypted link decryption status, scanning lines, glowing logo branding, and auto-transition to Main Menu.
  - `MainMenuScene.kt`: Command hub with operative dossier card (rank, heist intel, tactical directives), hero infiltration action button, black market access, and settings terminal navigation.
  - `LevelSelectScene.kt`: Classified mission selection dossier grid displaying operation number, lock status, star ratings (0-3), target times, coin bounties, best record times, and smooth touch scrolling.
  - `StoreScene.kt`: Black market contraband depot featuring the hero Shadow Operative Pass with gold neon border, tiered coin caches, tactical powerups (smoke screen, EMP scrambler, phantom cloak), and restore purchases wired to `PurchasesBridge`.
  - `SettingsScene.kt`: Tactical configuration terminal with interactive music & SFX volume sliders, restore purchases, privacy policy, terms of service, and clear cache options.
  - `GameplayScene.kt`: Immersive stealth parkour level with stark white high-contrast background and solid black silhouette architecture, extraction beacon, ground-aligned character sprite anchoring (calibrated feet contact line eliminating floating gap), tactile mobile touch controls (Left/Right virtual D-Pad, Jump/Vault, Sneak/Crouch), top glassmorphism HUD with live threat & detection radar bar, stance indicator, stopwatch timer, and modal overlays (Pause, Mission Failed with tactical recon tips, Level Complete with 3-star rating reveal & 2x multiplier coin bounty rewards).