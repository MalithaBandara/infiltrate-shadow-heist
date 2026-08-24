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
  `purchases-kmp-core:2.10.2+17.55.1` needs `pod 'PurchasesHybridCommon',
  '17.55.1'` linked into the final iOS app/framework for iOS to actually
  work — this is currently unresolved. It is not yet known whether
  KorGE's generated Xcode project has any hook for injecting a Podfile,
  or whether one needs to be hand-authored and copied into
  `build/platforms/ios` post-generation.
- The `ios-build.yml` CI run is the first real signal on where this
  breaks (Kotlin/Native compile step, or the final native link step, or
  it might just work). Check the latest Actions run before assuming
  either way:
  https://github.com/MalithaBandara/infiltrate-shadow-heist/actions
- Do not assume this is solved just because CI is green on other steps —
  confirm the `iosBuildSimulatorDebug` step itself succeeded and check
  whether the Podfile-search step actually found anything.