# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game, visually similar to Shadow Fight / Vector,
with a heist/infiltration objective similar to Robbery Bob.

**Target platforms: Android and iOS. Both are required — this is a
cross-platform Kotlin Multiplatform hackathon submission (Shipaton 2026),
and the app must work on both platforms.**

## Tech stack
- Engine: KorGE (Kotlin Multiplatform game engine)
- NOT using Compose Multiplatform — no Compose dependencies anywhere in this project
- Targets: Android, iOS, JVM desktop (JVM used for local dev/testing only —
  Android and iOS are the actual shipping targets)
- Payments: RevenueCat via `purchases-kmp-core` only (plain Kotlin SDK).
  Do NOT add `purchases-kmp-ui` — it requires Compose. The paywall is
  hand-built in KorGE UI instead.

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

- `gradle.yml` — from the original korge-hello-world template. Runs
  `./gradlew jvmTest` on every push, `ubuntu-latest`, JDK 21 (zulu).
- `deploy-js.yml` — from the template. Builds the JS/webpack bundle and
  deploys to GitHub Pages on push to `main`, `ubuntu-latest`, JDK 21.
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

## RevenueCat version is pinned by iOS klib ABI compatibility, not just Android/JVM metadata

- `build.gradle.kts` currently pins `purchases-kmp-core` to `1.9.0+14.3.0`
  for BOTH `androidMainApi` and `iosMainApi` (downgraded from
  `2.10.2+17.55.1`, which itself was a downgrade from `3.5.1` — see the
  Android/JVM metadata-conflict note already in this file).
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