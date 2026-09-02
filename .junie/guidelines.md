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

**Update (2026-08-29, commit `eeb627d`):** a later commit (`d7ab110`,
unrelated to anything below) introduced `"%02d".format(...)`-style
Java `String.format()` calls in `src/game/scene/LevelSelectScene.kt`
(lines 51-57 and around 195), which has no Kotlin/Native
implementation and broke `:compileKotlinIosSimulatorArm64` again. Fixed
by replacing both call sites with manual
`n.toString().padStart(2, '0')` formatting, which works on every
target. **Lesson for future sessions:** iOS CI was not re-run between
`0b958c3` and this fix, so a real regression sat undetected for two
commits (`d7ab110`, `cdbd938`) — don't assume iOS is still green just
because JVM `Testing` CI is green; they compile different code paths
and only the iOS workflow catches Kotlin/Native-only gaps like this.
`:game`'s iOS build is green again as of `eeb627d`. Separately, this
session also proved a fully isolated RevenueCat-on-iOS path works via
a composite build (`paywall-build`) — see "RevenueCat on iOS: PROVEN
WORKING" below. That work does NOT touch `:game` at all.

**Update (2026-08-31):** shared storage bridge (`PaywallStorage.kt` /
`KorgeStorageKey.kt`) built and JVM-verified — see "Shared storage
bridge" below. A native shell (`ios-shell/`) that embeds `GameMain.framework`
+ `PaywallModule.framework` together is now **WORKING, confirmed on a
real iOS Simulator in CI** (run `33385051973`, after one earlier failed
attempt with a real architecture-mismatch bug, since fixed). The
storage bridge's on-device round-trip genuinely passes: `Storage bridge
result: OK`, read straight from the log, not assumed. See "Native iOS
shell: `ios-shell/`" below for the full verbatim story, including two
risks (duplicate Kotlin/Native runtime symbols; `@ObjCName` prefix
stripping) that are now resolved with real evidence, not just fixed and
hoped.

**Update (2026-09-01):** the "KorGE only during gameplay, Compose owns
everything else" architecture is **PROVEN VIABLE on real iOS Simulator
CI**. Repeatedly swapping `window.rootViewController` between a Compose
screen and KorGE's already-warm `ViewController` works correctly with no
extra pause/resume plumbing: switch-to-KorGE latency lands well under
half a second even cold and 60–120ms warm, and — the important one for
battery — KorGE's render loop genuinely stops producing frames while
hidden (measured 0 across every cycle, not assumed from docs). Getting a
clean CI run took 5 iterations, including one real Swift compile bug, one
bug in my own measurement harness, and one genuine Compose Multiplatform
crash (`PlistSanityCheck` requiring `CADisableMinimumFrameDurationOnPhone`
in `Info.plist`) — all caught by reading raw logs, not the misleading
green `continue-on-error` checkmark, same discipline as every other spike
in this file. See "Compose/KorGE view-switching spike" below for the full
story. Separately, also confirmed **`korge-video` is NOT viable** for the
planned menu video background — see "`korge-video` feasibility spike"
below.

**Update (2026-09-01, later same day):** AdMob via `app.lexilabs.basic:basic-ads`
is **VIABLE on iOS — real link proven, `BUILD SUCCESSFUL`**, but needed
genuine CocoaPods wiring (unlike RevenueCat 3.x). Took 3 attempts: plain
Maven dependency failed (`framework 'GoogleMobileAds' not found` — it
doesn't bundle Google's SDK into its klib), adding the CocoaPods plugin
with a manually-declared framework failed with the *same* error despite
CocoaPods genuinely fetching and building the pod, and the actual fix
only came from reading Kotlin's own Gradle plugin source directly:
`configureLinkingOptions()` only wires pod search paths onto the ONE
framework the plugin auto-creates per target (Gradle-internal name
prefixed `"pod"`), never onto an independently-declared
`binaries.framework {}` no matter its name. Fix was configuring that
auto-created framework via `cocoapods { framework { ... } } ` instead.
See "AdMob (`basic-ads`) feasibility spike" below for the full story,
exact working config, and what's still unproven (not yet embedded in
`ios-shell/`, Android untested, real device untested).

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
  (`export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`
  in Git Bash) for any local `gradlew` command; JDK 19 fails with
  "Dependency requires at least JVM runtime version 21." (Corrected
  2026-08-31: the exact patch version is `.101`, not `.8` as an earlier
  note said — verify with `ls "/c/Program Files/Eclipse Adoptium/"`
  before trusting either number, it can drift with auto-updates.)
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
  path was investigated (see "RevenueCat version is pinned by iOS klib
  ABI compatibility" below) but never implemented for `:game`. (A
  different, working `linkerOpts` fix for a *different* RevenueCat line
  was later found for the isolated `paywall-build` composite build —
  see "RevenueCat on iOS: PROVEN WORKING" further below; it does not
  apply to `:game` itself.) The fix actually used here was entirely
  `build.gradle.kts`: removing
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
  **`:game` itself is still Android-only as of 2026-08-29 — its own
  `iosMainApi` has no RevenueCat dependency, and `PurchasesBridge.ios.kt`
  is still a stub.** This is unchanged by the composite-build spike
  below. See "RevenueCat on iOS: PROVEN WORKING via isolated composite
  build" for what *is* now proven (in a separate, isolated module) and
  exactly what's still missing before `:game` itself could use it.

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

## Git push policy — NEVER push without explicit user consent

**NEVER run `git push` autonomously.** Even if tests pass locally, security scans are clean, or a prompt mentions CI verification:
1. Make local commits only.
2. Show the proposed commit(s) and changes to the user.
3. Explicitly ASK the user for permission to push to GitHub.
4. Wait for the user's explicit approval before executing `git push`.


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

## RevenueCat on iOS: PROVEN WORKING via isolated composite build (2026-08-29)

**This replaces the old "RevenueCat on iOS is deferred" section below,
which is no longer accurate.** As of 2026-08-25 this project believed
every RevenueCat iOS path was dead-ended (klib ABI wall, or a missing
`PurchasesHybridCommon` binary needing a CocoaPods setup KorGE's build
pipeline has no hook for). On 2026-08-29 a from-scratch investigation
proved that's no longer true for RevenueCat's `3.x` line, **on real
macOS CI, with an actual linked framework artifact as evidence** — not
theorized, not klib-manifest-inspection alone.

**Read this whole section before touching RevenueCat/iOS again.** It
is the single most load-bearing piece of iOS-payments knowledge in this
file. In particular, do not re-attempt the abandoned approaches below
(vendoring `RevenueCat.xcframework` + hand-building `PurchasesHybridCommon`
from source) — they're obsolete; the working path is completely different
and much simpler.

### What was proven, exactly

- `com.revenuecat.purchases:purchases-kmp-core:3.6.0` **compiles** its
  iOS klib under **Kotlin `2.3.20`** (`:paywall-build:compileKotlinIosSimulatorArm64`
  → `BUILD SUCCESSFUL`).
- It also **links** into a real, standalone `PaywallModule.framework`
  for `iosSimulatorArm64` (`:paywall-build:linkDebugFrameworkIosSimulatorArm64`
  → `BUILD SUCCESSFUL in 6m 29s`, 5/5 tasks executed, zero undefined
  symbols) — with a real call site (`Purchases.configure(...)`) forcing
  the linker to actually pull in and resolve RevenueCat's native code,
  not leave it dead-stripped as an unused dependency.
- **Zero CocoaPods, zero Podfile, zero `PurchasesHybridCommon`** anywhere
  in this path. RevenueCat's `3.x` line bundles its native SDK directly
  into the klib via cinterop (`com.revenuecat.purchases:kn-core-cinterop-RevenueCat`,
  `kn-core-cinterop-AdditionalSwift`, `kn-core` — visible in the klib's
  own `depends` manifest field), which is exactly why the CocoaPods gap
  that killed every earlier RevenueCat version simply doesn't apply to
  `3.x`. Confirmed empirically: no CocoaPods-related error appears
  anywhere in either the failed or the succeeded link log for this line.
- All of this happened inside **`paywall-build`**, a Gradle **composite
  build** (not a subproject) — completely isolated from `:game`. `:game`
  stayed on KorGE `6.0.0` / Kotlin `2.0.20` throughout, unaffected and
  still building green, verified repeatedly during this investigation.

### Why a composite build, not a normal subproject

First attempt was a plain Gradle subproject (`include(":paywall")`,
Kotlin `2.1.20`) inside this same build. **That failed immediately** —
not with a RevenueCat error, but with
`org.gradle.plugin.management.internal.InvalidPluginRequestException:
The request for this plugin could not be satisfied because the plugin
is already on the classpath with an unknown version`. Root cause: Gradle
resolves the Kotlin Gradle Plugin once per build and shares that
classpath across every subproject; `:game`'s own KorGE plugin already
pulls in Kotlin `2.0.20`'s KGP, so requesting a second version anywhere
else in the same build is a hard conflict — not something a Gradle flag
fixes. Worse: this didn't just fail the new subproject, it broke **the
entire build**, because KorGE's `targetIos()` internally calls
`project.allprojects { }` during its own configuration (`Ios.kt`,
`configureNativeIosTvos`), which eagerly touches every subproject
including the broken one.

The fix is Gradle's actual supported mechanism for mixing Kotlin
toolchain versions in one repo: an **`includeBuild`** composite build,
which gets a genuinely separate classpath/daemon per included build,
confirmed empirically (`./gradlew tasks` succeeded cleanly with both
builds wired in; `:paywall-build:compileKotlinJvm` compiled with its
own Kotlin/Compose versions; `:compileKotlinJvm` on root kept succeeding
throughout, `UP-TO-DATE`/unaffected).

**Non-obvious gotcha: `includeBuild` does not mean CI (or any Gradle
invocation) automatically builds the included project.** The first
"successful" iOS CI run after wiring in `paywall-build` was misleading —
inspecting its log showed `> Configure project :paywall-build` (Gradle
configures every project in the tree, always) but **zero** `:paywall-build:*`
tasks actually executed. `ios-build.yml`'s main task
(`iosBuildSimulatorDebug`) belongs to the root project and has no
dependency on anything in the separately-included build. Getting a real
signal required adding an explicit, separate CI step that names
`paywall-build`'s task directly (see "CI wiring" below) — this is easy
to get wrong silently (a green run that tests nothing), so if this ever
needs re-verifying, confirm the actual task ran by grepping the raw log
for `:paywall-build:<taskname>`, not just the job's pass/fail.

### Exact versions used (all in `paywall-build/`, isolated from `:game`)

- **Kotlin: `2.3.20`** — chosen to *exactly match* the compiler that
  produced `purchases-kmp-core:3.6.0`'s klib (see ABI check below),
  rather than betting on forward compatibility with an even newer
  Kotlin (`2.4.10` was latest stable at the time but deliberately not
  used, to eliminate ABI-mismatch risk entirely instead of gambling on it).
- **Compose Multiplatform: `1.12.0`** (`org.jetbrains.compose` +
  `org.jetbrains.kotlin.plugin.compose`, both pinned to match) — latest
  stable on Maven Central as of 2026-08-25. Confirmed compatible before
  using it: JetBrains' own compose-compatibility docs state Compose
  Multiplatform `1.8.0`+ needs Kotlin `2.1.0` minimum, `2.2.20`+
  recommended — `2.3.20` is comfortably above both.
- **`purchases-kmp-core: 3.6.0`** — latest `3.x` on Maven Central as of
  2026-08-25 (checked directly, not assumed: `3.5.1` was the version
  guidelines previously recorded as tried-and-failed; `3.6.0` is newer
  and was re-checked from scratch). **Declared in `iosMain` only, not
  `commonMain`** — `3.x` publishes iOS-native variants exclusively (no
  JVM/Android artifact in this line); declaring it in `commonMain`
  produces an immediate, unambiguous Gradle "no matching variant" error
  for the `jvm()` target, not a subtle runtime issue.

### The klib ABI check (how the exact Kotlin version was chosen, not guessed)

Same technique this file already used for the `:game` module's own
RevenueCat investigation (see next section): download the klib directly
and read its manifest.
```bash
curl -sL -o rc.klib "https://repo1.maven.org/maven2/com/revenuecat/purchases/purchases-kmp-core-iossimulatorarm64/3.6.0/purchases-kmp-core-iossimulatorarm64-3.6.0.klib"
unzip -p rc.klib default/manifest
```
Result: `abi_version=2.3.0`, `compiler_version=2.3.20`,
`native_targets=ios_simulator_arm64`. Kotlin's klib ABI reader can only
read klibs at or below its own ABI ceiling (this is the exact same
mechanism that permanently ruled out every `2.0.0+`/`3.0.0`–`3.5.1`
RevenueCat version against `:game`'s Kotlin `2.0.20` — see next
section) — so `paywall-build`'s Kotlin was set to `2.3.20` specifically
to match, not picked arbitrarily. **If `purchases-kmp-core` is ever
bumped again, re-run this exact manifest check against the new
version's klib before assuming any given Kotlin version will read it** —
never assume forward compatibility.

### The link fix — full detail, since this is the genuinely load-bearing, non-obvious part

`compileKotlinIosSimulatorArm64` succeeding is **not** the same as
producing a usable binary — it only compiles a `.klib`. The real test
needed a `binaries.framework { }` declaration (which `paywall-build`
didn't have at first) plus a real call site so the linker has something
to resolve. Added both:

- `paywall-build/build.gradle.kts`: `iosArm64`/`iosSimulatorArm64` each
  got `binaries.framework { baseName = "PaywallModule" }`.
- `paywall-build/src/iosMain/kotlin/PaywallUsage.kt`: a real call to
  RevenueCat's actual public API (`com.revenuecat.purchases.kmp.Purchases`,
  `.configure(apiKey = "...") { appUserId = "..." }`, `LogLevel`) — using
  an obviously-fake placeholder API key, since this only needs to link,
  never run.

First link attempt (`:paywall-build:linkDebugFrameworkIosSimulatorArm64`)
**failed** — a genuinely different failure from every prior RevenueCat
attempt (no CocoaPods error at all, confirming that wall really is gone
for `3.x`):
```
ld: warning: search path '/Applications/Xcode-16.4.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator/' not found
ld: warning: Could not find or use auto-linked library 'swiftCompatibility56': library 'swiftCompatibility56' not found
ld: warning: Could not find or use auto-linked library 'swiftCompatibilityConcurrency': library 'swiftCompatibilityConcurrency' not found
ld: warning: Could not find or use auto-linked library 'swiftCompatibilityPacks': library 'swiftCompatibilityPacks' not found
Undefined symbols for architecture arm64:
  "__swift_FORCE_LOAD_$_swiftCompatibility56", referenced from:
      ... in libcom.revenuecat.purchases:kn-core-cinterop-RevenueCat-cache.a[440](AdTracker.swift.o)
  "__swift_FORCE_LOAD_$_swiftCompatibilityConcurrency", referenced from: ...
  "_swift_getFunctionTypeMetadataGlobalActorBackDeploy", referenced from:
      ... in libcom.revenuecat.purchases:kn-core-cinterop-RevenueCat-cache.a[82](PurchasesOrchestrator.swift.o)
ld: symbol(s) not found for architecture arm64
```
**Root cause**, visible directly in the first `ld: warning` line above:
Kotlin/Native's default linker invocation searches a **hardcoded/stale
Xcode path** (`Xcode-16.4.app`) for Swift's back-deployment
compatibility shim libraries, rather than resolving whatever Xcode is
actually installed on the build machine. The CI runner's real Xcode was
`Xcode_26.6.app` (visible in the same log, from the `ld` binary's own
invocation path) — the shim libraries genuinely exist there, Kotlin/Native
just never looked in the right place, so RevenueCat's real compiled
Swift object files (`AdTracker.swift.o`, `PurchasesOrchestrator.swift.o`,
`CustomerInfo.swift.o` — all real RevenueCat code, confirming it was
genuinely engaged, not silently skipped) referenced symbols the linker
could never find.

**The fix** — compute the real Xcode developer directory via
`xcode-select -p` at Gradle configuration time and add it as an
explicit linker search path, in `paywall-build/build.gradle.kts`:
```kotlin
import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream

// Guarded to macOS only: this build.gradle.kts is also configured on
// Windows dev machines (composite builds configure every included
// build eagerly, even for an unrelated :game-only task), where
// xcode-select doesn't exist and would break configuration entirely.
val macDeveloperDir: String? = if (OperatingSystem.current().isMacOsX) {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("xcode-select", "-p")
        standardOutput = stdout
    }
    stdout.toString().trim()
} else null

fun swiftLibPath(platformSdkName: String): String? =
    macDeveloperDir?.let { "$it/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$platformSdkName" }

kotlin {
    iosArm64 {
        binaries.framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            swiftLibPath("iphoneos")?.let { linkerOpts += listOf("-L$it") }
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            swiftLibPath("iphonesimulator")?.let { linkerOpts += listOf("-L$it") }
        }
    }
}
```
(The `-Xbinary=bundleId=...` line addresses a separate, unrelated
compiler warning — "Cannot infer a bundle ID" — the compiler explicitly
suggested that exact flag; harmless, included but not proven necessary
for the link fix itself.)

Re-ran after the fix: **`BUILD SUCCESSFUL in 6m 29s`, 5 actionable
tasks: 5 executed, zero undefined symbols.** Only remaining output was
~15 benign warnings about missing Clang module-cache `.pcm` files
(`Foundation`, `UIKit`, `StoreKit`, etc., under
`.../swift-packages/RevenueCat/.build/.../ModuleCache/...`) — a known,
harmless side effect of linking a redistributable static library built
with `-gmodules` without its original module cache present; degrades
debug-symbol quality only, not functionality.

**If this ever needs revisiting** (e.g. after a runner image bumps its
default Xcode, or after any Kotlin/Compose/RevenueCat version bump
here): re-check whether the hardcoded-Xcode-path bug still exists in
whatever Kotlin/Native version is in use, and re-verify the
`xcode-select` fix is still landing on the correct directory — don't
assume it's permanently fixed upstream.

### CI wiring

`.github/workflows/ios-build.yml`, one step, placed right after the
main `"Build unsigned iOS Simulator app (KorGE)"` step:
```yaml
- name: "SPIKE: link paywall-build framework for iOS (RevenueCat 3.6.0 / Kotlin 2.3.20)"
  continue-on-error: true
  run: ./gradlew :paywall-build:linkDebugFrameworkIosSimulatorArm64 --no-configuration-cache --stacktrace
```
`continue-on-error: true` is deliberate and important to understand
correctly: it means a failure in this step **never** red-X's the whole
job, so the main game build's own pass/fail is never conflated with the
spike's. **But this also means GitHub's Actions API reports this step's
`"conclusion"` as `"success"` even when the underlying command
genuinely failed** (verified directly — a run where the link failed
with the undefined-symbols error above still showed
`"conclusion": "success"` for this step via the REST API). **The API's
conclusion field for a `continue-on-error` step is not a reliable
pass/fail signal — always read the actual Gradle output in the raw job
log** (`gh api .../actions/jobs/<id>/logs`, or download via the REST
API) for `BUILD SUCCESSFUL` vs `BUILD FAILED` / `Undefined symbols` /
`FAILED` under that specific step, never trust the green checkmark alone.

### What's still NOT done — explicit next-phase work

Everything above is proven **only inside the isolated `paywall-build`
composite build**. None of it is wired into the real game yet:

- **`:game`'s own RevenueCat setup is completely unchanged.** Still
  Android-only, still pinned to `purchases-kmp-core:1.9.0+14.3.0`, still
  zero `iosMainApi` dependency. Migrating `:game` itself onto this
  proven path (vs. leaving RevenueCat isolated in `paywall-build`
  forever) is a separate, larger decision not made here.
- **`PurchasesBridge.ios.kt` / `PurchasesBridge.android.kt` are still
  stubs**, untouched by this work — `purchase()` still just returns
  `onResult(false)` on iOS. Nothing calls into `paywall-build` from
  either bridge.
- **No native shell/orchestration layer exists** to actually embed
  `PaywallModule.framework` into the real iOS app target (KorGE's
  generated Xcode project). The spike proves the framework itself
  builds and links — not that it's loadable/callable from the shipping
  app, which needs its own investigation (likely: KorGE-generated
  Xcode project + a Swift-side bridge, or Kotlin/Native's interop
  mechanisms to call from `:game`'s own framework into `PaywallModule.framework`).
- **Shared storage bridge: DONE for iOS, as of 2026-08-31.** See
  "Shared storage bridge: paywall-build ↔ :game" below — the open
  questions from this bullet (what backend `views.storage` uses on iOS,
  same-process vs. App Group) are now answered and implemented
  (`PaywallStorage.kt` + `KorgeStorageKey.kt` in `paywall-build`),
  verified logically (JVM unit tests) but not yet on a real device/
  simulator. Android's storage backend is documented there too but not
  implemented — `paywall-build` has no Android target.
- **No real paywall UI exists** anywhere in the codebase (`main.kt` is
  still the untouched korge-hello-world demo scene, per the note in
  "LOCKED WORKING CONFIGURATION" above).
- **Only `iosSimulatorArm64` was actually linked and verified.** The
  `iosArm64` (real device) `binaries.framework` block mirrors the same
  fix by construction but has never actually been run/verified — don't
  assume real-device linking works without checking `:paywall-build:linkDebugFrameworkIosArm64`
  the same way.

### Traceability (commits, all on `main`)

- `fbb7024` — initial `paywall-build` composite build (Kotlin `2.3.20`,
  Compose `1.12.0`, `purchases-kmp-core:3.6.0` dependency added).
- `eeb627d` — unrelated `:game` fix (see top-of-file update note) that
  had to land first, since it was blocking CI before the spike could
  even run.
- `9a523c1` — added the CI step that first proved `paywall-build`'s
  klib *compiles* (`compileKotlinIosSimulatorArm64`).
- `6d57823` — added the real `binaries.framework` declaration + real
  `Purchases.configure()` call site + switched the CI step to the link
  task (first attempt, failed with the undefined-symbols error above).
- `4a5f3b8` — the `xcode-select` linker-search-path fix. This is the
  commit where the link first succeeded.

## Shared storage bridge: paywall-build ↔ :game (2026-08-31) — iOS done, Android deferred

**This replaces the "No shared storage bridge is implemented" bullet under
"What's still NOT done" in the RevenueCat section above** — that's no
longer accurate for iOS. This is the foundation everything else in that
"What's still NOT done" list depends on, so read this before touching it.

### What `Views.storage` actually uses — verified, not assumed

The earlier open question ("confirm what backend KorGE's `views.storage`
actually uses on iOS today — don't assume `NSUserDefaults`") is now
answered by reading KorGE 6.0.0's real implementation directly (extracted
from the local Gradle cache: `korge-6.0.0-sources.jar` for iOS,
`korge-android-6.0.0.aar`'s `classes.jar` decompiled for Android, since no
sources jar is published for the Android artifact):

- **iOS/darwin** (`korlibs.korge.service.storage.DarwinNativeStorage`,
  used by every Kotlin/Native darwin target, including
  `iosArm64`/`iosSimulatorArm64`): backed by
  `NSUserDefaults(suiteName = "korge")` — a **named suite**, deliberately
  NOT `NSUserDefaults.standardUserDefaults` (that line is commented out
  in KorGE's own source, right above the suite version). Every key is
  prefixed with `"org.korge.storage."` before being read/written
  (`getKey(key) = "org.korge.storage.$key"`). Values go through
  `setObject(value, forKey:)` / `objectForKey(key)?.toString()`, and
  every write/remove calls `synchronize()` afterward.
- **Android** (`korlibs.korge.service.storage.NativeStorage`, decompiled
  with `javap -c -constants` since only the `.aar` is published, no
  sources jar): backed by
  `context.getSharedPreferences("KorgeNativeStorage", 0 /* MODE_PRIVATE */)`.
  Keys are stored **unprefixed**, plain `putString`/`getString` — no
  transformation needed on this platform.
- A named `NSUserDefaults` suite with no App Group entitlement resolves
  to the same on-disk plist for any code running inside the same app
  sandbox/process, regardless of which compiled framework instantiates
  it — so once `PaywallModule.framework` is actually embedded into
  `:game`'s app target (still not done — see "No native shell/orchestration
  layer exists" below, unchanged), no App Group is needed for this to
  work. If a different integration is ever chosen (e.g. paywall running
  as a separate extension process instead of embedded in the same app
  target), this assumption would need revisiting.

### What was implemented

- `paywall-build/src/commonMain/kotlin/KorgeStorageKey.kt` — pure
  `PREFIX`/`iosKey()` helper replicating `DarwinNativeStorage`'s exact key
  transform, kept separate from the real iOS calls so it's unit-testable
  on the JVM target.
- `paywall-build/src/iosMain/kotlin/PaywallStorage.kt` — real
  `getRaw`/`setRaw` backed by `NSUserDefaults(suiteName = "korge")`,
  matching `:game`'s `MapBackedGameProfileStorage` contract
  (`GameProfile.kt`) exactly.
- `paywall-build/src/commonTest/kotlin/StorageKeyCompatibilityTest.kt` +
  a `commonTest`/`jvmTest` source set added to
  `paywall-build/build.gradle.kts` (it already had a `jvm()` target for
  `Placeholder.kt`, just no test source set yet).
- Keys covered: the 5 keys `MapBackedGameProfileStorage` actually
  persists today — `user_coins`, `user_is_premium`, `user_music_vol`,
  `user_sfx_vol`, `user_unlocked_levels`. **Pre-existing gap, found while
  reading `GameProfile.kt` for this work, not caused by it and not fixed
  here:** `GameProfile.powerupInventory` exists on the data class but
  `MapBackedGameProfileStorage.persist()`/`loadFromStorage()` never
  reads/writes it — powerup counts are silently lost across app restarts
  today, independent of anything in this section. Whoever fixes that
  should add the matching key to this storage bridge too.

### Verification — logical/JVM-only, not on-device (be precise about this)

`./gradlew :paywall-build:jvmTest` — **BUILD SUCCESSFUL, 4/4 tests
executed and passed** (confirmed via the actual JUnit XML output, not
just Gradle's exit code, per this file's own "verify the actual output"
standard from the RevenueCat CI section above). What these tests prove:
that `:game`'s key-prefixing logic and `paywall-build`'s `iosKey()`
produce identical raw keys, and that a value written through one side's
transform reads back correctly through the other's, for all 5 keys.

**What this does NOT prove**: that the real on-device `NSUserDefaults`
store is actually shared between the two separately compiled frameworks.
This dev machine has no iOS simulator/Xcode, so that round-trip can't be
run here — same constraint already documented for the real link/build
steps elsewhere in this file. Also run, as a real (not assumed) signal:
`./gradlew :paywall-build:compileKotlinIosSimulatorArm64
--no-configuration-cache` **succeeds on this Windows machine** (Kotlin/Native
klib compilation for Apple targets doesn't require macOS, unlike linking)
— confirms `PaywallStorage.kt` compiles cleanly against the real
`platform.Foundation.NSUserDefaults` iOS bindings. Actual on-device
verification (write from a `PaywallModule.framework` call, read back via
`:game`'s `views.storage`) is still open — do that once the native shell
work embeds the framework into the real app target.

### What's still NOT done (Android + everything downstream)

- **No Android implementation.** `paywall-build` has zero Android target
  today (only `jvm()` + iOS) — deliberate, confirmed with the user:
  `:game` already talks to RevenueCat directly on Android via
  `androidMainApi`, so there's no cross-framework boundary to bridge
  there yet. If `paywall-build` ever needs an Android target, the
  `SharedPreferences("KorgeNativeStorage", MODE_PRIVATE)` backing
  documented above is what a matching `getRaw`/`setRaw` needs to target —
  no key prefix needed on that platform, unlike iOS.
- Everything else in the RevenueCat section's "What's still NOT done"
  list is still true and unaffected by this: no native shell/embedding,
  no paywall UI, `PurchasesBridge.ios.kt` is still a stub and doesn't
  call into `paywall-build` or `PaywallStorage` at all yet, only
  `iosSimulatorArm64` has ever been linked (not `iosArm64`/real device).
- No CI step runs `:paywall-build:jvmTest` yet — it's runnable locally
  today; wiring it into `gradle.yml`/`ios-build.yml` is a separate,
  not-yet-decided follow-up.

## Native iOS shell: `ios-shell/` (2026-08-31) — WORKING, verified on-device in real CI

**Status: GREEN, for real.** Attempt 1 (commit `e6dae18`, run
[33382783757](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33382783757))
failed with a genuine architecture-mismatch build error (see below).
Attempt 2 (commit `3fa602d`, run
[33385051973](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33385051973))
**succeeded — confirmed from the raw log, not the API conclusion field**:
`GameMain.framework` and `PaywallModule.framework` link together into one
real `ShellApp.app`, and the on-device storage-bridge round-trip
genuinely passes. The two risks flagged before attempt 1 (duplicate
Kotlin/Native runtime symbols; `@ObjCName` prefix-stripping) are both
now resolved, not just theorized — see "Attempt 2" below for the exact
proof. Don't re-litigate either without a reason; both are settled.

### What this is

The first attempt to run `:game`'s `GameMain.framework` and
`paywall-build`'s `PaywallModule.framework` **in the same process** —
everything before this only proved each framework links in isolation.
Deliberately minimal: no paywall UI, no `PurchasesBridge.ios.kt` wiring.
Purpose-built to get the first on-device signal for the storage bridge
(previous section), which was until now only verified with a JVM-only
logical test.

### Load-bearing finding: why the trigger lives in Swift, not a KorGE scene

Reading KorGE's real iOS entry-point source directly
(`korlibs.render.KorgwBaseNewAppDelegate`, `DefaultGameWindowIos.kt`,
extracted from the local Gradle cache — not KorGE's docs, not assumed)
confirms `:game` (Kotlin 2.0.20) and `paywall-build` (Kotlin 2.3.20)
produce **ABI-incompatible klibs** — the exact same wall already
documented above for why `:game` can't depend on RevenueCat 3.6.0
directly. That means `:game`'s own Kotlin code cannot call into
`PaywallModule`'s Kotlin API either — there is no way to put a real
cross-framework call inside a KorGE scene without a Kotlin **cinterop**
binding against `PaywallModule.framework`'s compiled Objective-C header
(possible in principle, since cinterop reads compiled headers, not
klibs — but real added Gradle/cinterop surface, deliberately not done
here). **Confirmed with the user**: the round-trip trigger lives in the
native Swift shell instead, which can safely call both frameworks'
exported Objective-C APIs with no coupling between the two Kotlin
builds.

### What was built

- **`src@ios/ShellAppDelegate.ios.kt`** (new, `:game`) — subclasses
  `KorgwBaseNewAppDelegate`. Confirmed directly from source: only
  `applicationDidFinishLaunching(app: UIApplication)` is abstract;
  background/foreground/resign/terminate are already concrete on the
  base class. Calls the base class's 2-arg overload with `:game`'s real
  entry point (`suspend fun main()` in `src/main.kt`).
- **`src@ios/DebugStorageBridge.ios.kt`** (new, `:game`) — exercises the
  SAME production path every scene already uses
  (`game.model.MapBackedGameProfileStorage`), just swapping
  `views.storage[it]` for `korlibs.korge.service.storage.DarwinNativeStorage`
  directly. This works with no live `Views`/window because on darwin,
  KorGE's `NativeStorage` is literally `by DarwinNativeStorage` — a plain
  object, not `Views`-dependent. A fresh `MapBackedGameProfileStorage` is
  constructed on every call (its `init{}` loads from storage), so this
  proves a real disk re-read, not a cached in-memory value.
- **`ios-shell/`** (new directory) — hand-authored via **XcodeGen**
  (`project.yml`, declarative — far less error-prone to author correctly
  without Xcode to check against than a raw `project.pbxproj`; also the
  same tool KorGE's own plugin uses internally, so it's a safe bet for
  runner availability). Deliberately separate from
  `build/platforms/ios` (KorGE's own generated project, not used here).
  One app target, embeds both frameworks by relative path to their
  standard Kotlin/Native debug-framework output locations. Entry point is
  a classic `main.swift` + `UIApplicationMain(...)` (not `@UIApplicationMain`/`@main`,
  to avoid depending on attribute behavior that varies by Xcode version
  and can't be checked here). `AppDelegate.swift` forwards lifecycle
  calls to `ShellAppDelegate.shared`, adds one native `UIButton`+`UILabel`
  overlay for manual testing, and **also runs the same check
  automatically once at launch** — writes `PaywallStorage.shared.setRaw(key:
  "user_coins", value: <timestamp-derived test value>)`, then
  `DebugStorageBridge.shared.readCoinsForDebug()`, compares, and writes
  `"OK"` / `"FAIL:expected=X:actual=Y"` to
  `Documents/storage_bridge_result.txt` in the app's sandbox — this is
  what CI reads back, so verification doesn't require simulating a tap.
- **`.github/workflows/ios-build.yml`** — new steps after the existing
  `paywall-build` spike (all `continue-on-error: true`, same convention):
  install XcodeGen if missing, `xcodegen generate`, `xcodebuild build`,
  then boot a simulator / install / launch / read the result file back
  via `xcrun simctl get_app_container ... data` and fail the step
  (explicit content check, not just launch exit code) if it isn't `OK`.

### Attempt 1 (2026-08-31, commit `e6dae18`, run [33382783757](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33382783757)) — real CI result, read from raw logs not the conclusion field

**The GitHub API reported this whole run as `"conclusion": "success"` —
that is misleading and must not be trusted**, exactly as this file's own
"continue-on-error conclusion is not reliable" rule (RevenueCat section
above) predicted. Every new step's own `conclusion` field also said
`"success"`. The **raw logs** tell a different story:

- `:game`'s own `iosBuildSimulatorDebug` step: real `** BUILD SUCCEEDED **`
  — confirms `src@ios/ShellAppDelegate.ios.kt` and
  `src@ios/DebugStorageBridge.ios.kt` **do compile** cleanly against
  `:game`'s real Kotlin 2.0.20 iOS target (this had never been tested
  anywhere before this run — the KorGE-specific local `onlyIf` gate,
  point 4 below, made it impossible to check on this dev machine).
- `paywall-build`'s link spike: real `BUILD SUCCESSFUL in 3m 37s` —
  `PaywallModule.framework` output path is now confirmed for real,
  resolving the "unconfirmed locally" caveat this section used to carry.
- **The shell app `xcodebuild build` step genuinely failed**:
  ```
  ShellApp: ld: warning: ignoring file '.../GameMain.framework/GameMain': found architecture 'arm64', required architecture 'x86_64'
  ShellApp: ld: warning: ignoring file '.../PaywallModule.framework/PaywallModule': found architecture 'arm64', required architecture 'x86_64'
  Undefined symbols for architecture x86_64:
    "_OBJC_CLASS_$_GameMainDebugStorageBridge", referenced from: in AppDelegate.o
    "_OBJC_CLASS_$_GameMainShellAppDelegate", referenced from: in AppDelegate.o
    "_OBJC_CLASS_$_PaywallModulePaywallStorage", referenced from: in AppDelegate.o
  ld: symbol(s) not found for architecture x86_64
  ** BUILD FAILED **
  ```
  Root cause: `xcodebuild -sdk iphonesimulator` with no `-destination`
  defaulted to building BOTH `arm64` and `x86_64` simulator slices.
  `GameMain.framework`/`PaywallModule.framework` are `iosSimulatorArm64`
  Kotlin/Native output — arm64 only, no x86_64 slice — so the x86_64
  link had nothing to resolve against. **The duplicate-Kotlin-native-
  runtime-symbol risk flagged before this ever ran was never actually
  tested** — this earlier, unrelated problem blocked the build first.
  Fixed for the next attempt: `EXCLUDED_ARCHS[sdk=iphonesimulator*] =
  x86_64` added to `ios-shell/project.yml`'s target settings — the
  standard fix for an Apple-Silicon-only binary framework, independent
  of whatever `-destination` `xcodebuild` happens to pick.
- **Separately confirmed, real and precise**: the undefined-symbol names
  above (`GameMainShellAppDelegate`, `GameMainDebugStorageBridge`,
  `PaywallModulePaywallStorage`) prove `@ObjCName(name = "X")` **without
  `exact = true`** does NOT override Kotlin/Native's default
  framework-name-prefixed export — it compiles fine (Swift resolves the
  short name via the generated interface) but the real linked ObjC class
  symbol keeps the framework prefix. Fixed for the next attempt: all
  three objects (`PaywallStorage`, `ShellAppDelegate`,
  `DebugStorageBridge`) now use `@ObjCName(name = "X", exact = true)`,
  gated by an additional opt-in,
  `@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)` (on
  top of the `ExperimentalObjCName` opt-in already needed for `@ObjCName`
  at all). **Re-verified locally**: `:paywall-build:compileKotlinIosSimulatorArm64`
  still compiles cleanly with `exact = true` added — but whether the
  *linked* symbol is actually unprefixed now can only be confirmed by a
  real link, i.e. the next CI run, not locally.
- The simulator round-trip check never got to run (app never built) —
  its own step failed with `Missing bundle ID` trying to install the
  incomplete `.app` Xcode had scaffolded before the link failure. Purely
  downstream of the build failure above, not an independent finding.

### Attempt 2 (2026-08-31, commit `3fa602d`, run [33385051973](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33385051973)) — SUCCEEDED, verified from raw logs

Same discipline as attempt 1: the API's `"conclusion": "success"` alone
is not proof (it said that for attempt 1's real failure too). The raw
log this time genuinely backs it up:

- `xcodebuild` step: no architecture-mismatch warnings at all (the
  `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` fix worked), a clean
  link/codesign sequence —
  ```
  Ld .../ShellApp.app/ShellApp.debug.dylib normal (in target 'ShellApp' from project 'ShellApp')
  Ld .../ShellApp.app/ShellApp normal (in target 'ShellApp' from project 'ShellApp')
  CodeSign .../ShellApp.app/ShellApp.debug.dylib (in target 'ShellApp' from project 'ShellApp')
  CodeSign .../ShellApp.app (in target 'ShellApp' from project 'ShellApp')
  ```
  — and `** BUILD SUCCEEDED **`. **No duplicate-symbol errors anywhere**
  — the risk flagged before attempt 1 (each Kotlin/Native framework
  embedding its own runtime) turned out not to be a real problem for this
  pair of frameworks. **This also confirms `@ObjCName(exact = true)`
  actually worked**: if the exported names were still prefixed, this
  link would have failed with the same "Undefined symbols" error as
  attempt 1, just for the correct architecture instead — it didn't.
- Round-trip verification step, real printed output:
  ```
  Found app at: ios-shell/build/Build/Products/Debug-iphonesimulator/ShellApp.app
  Using simulator device: 6F69910C-E4FA-488A-B9C2-41B770484810
  Monitoring boot status for iPhone 17 Pro (6F69910C-E4FA-488A-B9C2-41B770484810).
  ...
  com.infiltrate.shellapp: 33475
  Storage bridge result: OK
  Storage bridge round-trip verified ON-DEVICE (not just JVM-logical): OK
  ```
  `com.infiltrate.shellapp: 33475` is `simctl launch`'s own confirmation
  the app actually launched (its PID) without crashing. `Storage bridge
  result: OK` is read back from the app's own
  `Documents/storage_bridge_result.txt` — i.e. `PaywallStorage.shared.setRaw(...)`
  (from `PaywallModule.framework`) and `DebugStorageBridge.shared.readCoinsForDebug()`
  (from `GameMain.framework`, going through the real
  `MapBackedGameProfileStorage` path) agreed on the same value, in one
  real running process, on a real simulator. This is the first genuine
  on-device confirmation of the storage bridge — the JVM-only test from
  the previous session is now superseded by this as the stronger proof,
  though the JVM test is still useful as a fast local regression check.

### Resolved risks (for future sessions: don't re-litigate these without a new reason)

1. ~~Duplicate Kotlin/Native runtime symbols~~ — tested for real in
   attempt 2, did not occur. Not a problem for this specific pair of
   frameworks (Kotlin 2.0.20 `:game` + Kotlin 2.3.20 `paywall-build`,
   both built as dynamic frameworks, embedded together in one app
   target). If a future session adds a *third* Kotlin/Native framework
   to this app, re-verify — this isn't proven for arbitrary combinations.
2. ~~`@ObjCName(name = "X")` without `exact = true`~~ — confirmed broken
   (attempt 1), confirmed fixed by adding `exact = true` +
   `@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)`
   (attempt 2 links clean). Apply this pattern to any future Kotlin/Native
   object exported for cross-framework/Swift use in this project.

### Still-open / genuinely unresolved

- **`:game`'s own `compileKotlinIosSimulatorArm64` is disabled on this
  Windows machine** (`Skipping task ... as task onlyIf 'Task is enabled'
  is false`) — a KorGE-plugin-specific gate, not a general Kotlin/Native
  limitation (the identical task for `paywall-build`, plain
  `kotlin("multiplatform")`, runs fine here). Root cause not traced
  (time-boxed both times it came up). Practical effect: any future change
  to `:game`'s iOS-only source can only be compile-checked by CI, never
  locally, on this machine.
- No paywall UI, no `PurchasesBridge.ios.kt` wiring, no shared storage
  bridge actually driving anything yet — the round-trip above is a
  proof-of-concept debug path (`DebugStorageBridge`, a native overlay
  button + an automatic launch-time self-check), not real product code.
  Unchanged, out of scope for this step.
- Only `iosSimulatorArm64` — `iosArm64` (real device) untouched for both
  `GameMain.framework` and `PaywallModule.framework`, same caveat as the
  `paywall-build` spike above.
- `ios-shell/` is a standalone proof-of-concept project, not yet wired
  into any release/distribution pipeline, and not the same project as
  KorGE's own generated `build/platforms/ios` — no decision has been made
  about whether/how these converge for a real shipping app.

## Compose/KorGE view-switching spike (2026-09-01) — PROVEN VIABLE on real iOS Simulator CI

**Status: viable, build the real menu/store/gameplay architecture around
this.** Tests the architecture where KorGE is shown ONLY during actual
gameplay and Compose Multiplatform owns everything else (menu, level
select, store, settings, and a "watch ad to continue" flow that re-enters
KorGE after the ad finishes) — reusing `ios-shell/`'s already-proven
`GameMain.framework` + `PaywallModule.framework` embedding rather than a
new app.

### What was built

- **`paywall-build/src/iosMain/kotlin/SpikeComposeScreen.kt`** — the
  **first real Compose UI written anywhere in this repo** (`paywall-build`
  previously only declared `compose.runtime`/`foundation`/`material3`
  with no actual `@Composable`). One centered "Start Level" button,
  exported as `SpikeComposeScreen.shared.makeViewController(onStartLevel:)`
  via `ComposeUIViewController { }`. Needed adding `implementation(compose.ui)`
  to `paywall-build/build.gradle.kts`'s `iosMain` (not `commonMain` —
  UIKit-specific, would break the `jvm()` target, same reasoning as the
  RevenueCat dependency just above it).
- **`src@ios/SwitchSpikeScene.kt`** — a debug KorGE scene (pulsing rect,
  live tick counter, a real "END LEVEL (debug)" button wired via
  `.mouse { onClick { ... } }`) that the app **never navigates away
  from** — the whole point is that the KorGE engine + this scene stay
  resident for the app's life; "switching" is purely a native
  `window.rootViewController` toggle, not a KorGE scene change.
- **`src@ios/SpikeBridge.ios.kt`** — a Kotlin/Native object (same
  `@ObjCName(exact = true)` export pattern as `DebugStorageBridge`/`ShellAppDelegate`)
  exposing a per-frame `frameTicks` counter, incremented from
  `SwitchSpikeScene`'s `addUpdater { }` every render tick regardless of
  UIKit visibility. This is the actual measurement instrument: Swift
  polls it to detect (a) when a fresh frame renders after KorGE is shown
  again, and (b) whether it keeps changing while hidden.
- **`src@ios/SpikeEntry.ios.kt`** — a separate `spikeMain()`, structurally
  identical to `src/main.kt`'s real `Korge { sceneContainer()... }` but
  pointed at `SwitchSpikeScene` instead of `SplashScene`. `ShellAppDelegate.ios.kt`'s
  entry lambda was temporarily switched from `{ main() }` to
  `{ spikeMain() }` for this spike — commonMain's real `src/main.kt` was
  never touched.
- **`ios-shell/Sources/AppDelegate.swift`** — drives 6 fully automated
  Compose→KorGE→Compose cycles in one app session (no human tapping
  needed, no fragile `xcodebuild`/`XCUITest` tap-simulation): for each
  cycle, swap to KorGE and poll `SpikeBridge.shared.frameTicks` (2ms
  `Timer`) until it changes or times out (switch latency), dwell ~700ms
  visible (proves active rendering), call the same `requestLevelEnd()`
  the debug button calls, swap back to Compose, dwell ~1200ms hidden
  (proves the render loop actually stopped), sample resident memory via
  `mach_task_basic_info`. Results written to
  `Documents/switch_spike_result.txt`, read back by CI the same way
  `storage_bridge_result.txt` already was.

### The 5 CI iterations — real bugs, not flakiness, each confirmed from raw logs

Every one of these showed **`"conclusion": "success"`** in the GitHub
Actions API/UI (the steps use `continue-on-error: true`, same convention
as the rest of this file) while the actual command underneath had failed.
**This is not a one-off — it happened on 3 of 5 rounds in a row for
different reasons.** Never trust the checkmark for these steps; always
pull the job's raw log (`gh api .../actions/jobs/<id>/logs` or the REST
`/logs` zip) and grep for the real output.

1. **`62b135b`, run `33412736968`** — genuine Swift compile failure:
   `private var window: UIWindow?` on `AppDelegate` collided with
   `UIApplicationDelegate`'s own optional `window` property requirement
   (Swift requires protocol-conforming properties to be at least as
   accessible as the enclosing type). `** BUILD FAILED **` in the raw
   `xcodebuild` log; the app was never even built, let alone run. Fixed
   by renaming to `shellWindow` (`1320743`).
2. **`1320743`, run `33414886599`** — build succeeded and the app ran
   correctly, but the CI step meant to wait for the result file
   (`xcrun simctl io "$DEVICE_ID" screenshot ...` on every iteration of a
   100-iteration poll loop) turned out to cost **1–13 real seconds per
   screenshot on this runner** (confirmed from the raw per-line log
   timestamps, not assumed) — so the loop burned its entire 100-"second"
   budget on ~100 slow screenshots and never gave the app a real chance
   to be checked before giving up. Looked exactly like a hang; wasn't
   one. Fixed by dropping screenshots from that loop entirely for the
   next round and using a genuinely fast 1s-interval poll.
3. **`06aea45`, run `33417902658`** — **first clean success.** Fast poll
   found the result file **within ~1s of starting to check** (the whole
   6-cycle sequence had already finished during the previous step's own
   ~13–14s post-launch window). Real per-cycle data, see table below.
4. **`a8caaf7`, run `33420468867`** — re-added screenshots (this time in
   the *launch* step, right after `simctl launch`, so they'd actually
   land during the switching window instead of after it) — but keeping
   the CI step alive for ~95+ real seconds (10 screenshots × up to 13s
   each) gave a **genuine, deterministic Compose Multiplatform crash**
   time to fire: `SIGABRT`, Kotlin `kotlin#error(...)`, inside
   `androidx.compose.ui.uikit.PlistSanityCheck` — fetched directly from
   `JetBrains/compose-multiplatform-core` (`compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/uikit/PlistSanityCheck.ios.kt`):
   it dispatches a one-time check onto a **low-priority background
   queue** (`DISPATCH_QUEUE_PRIORITY_LOW`, matching the crash's own
   `"queue":"com.apple.root.utility-qos"` exactly) that hard-aborts the
   process if `Info.plist` doesn't have
   `CADisableMinimumFrameDurationOnPhone` set to `true`. `ios-shell/project.yml`
   never set it. Round 3 (above) never crashed only because its CI step
   exited in ~13s — too fast for the low-priority check to have fired
   yet; it would eventually have crashed there too, just later than CI
   was watching. **Not a switch-spike bug, not flaky — any Compose-on-iOS
   work in this repo needs this key regardless of the switching
   architecture.**
5. **`2130686`, run `33423097578`** — added
   `CADisableMinimumFrameDurationOnPhone: true` to `ios-shell/project.yml`'s
   Info.plist properties. Clean 6-cycle run, no crash, real numbers for
   every field including the one round 3 was missing
   (`switchToComposeLatencyMsApprox`).

### Real numbers (both clean runs — 06aea45/`33417902658` and 2130686/`33423097578` — cited separately, cross-run variance is Simulator-noise, not a regression)

| cycle | switchToKorgeLatencyMs (run 3) | switchToKorgeLatencyMs (run 5) | hiddenDwellTicksAdvanced (both runs) |
|---|---|---|---|
| 1 (cold) | 370.8 | 383.4 | 0 |
| 2 | 390.0 | 84.5 | 0 |
| 3 | 485.7 | 60.0 | 0 |
| 4 | 111.8 | 119.1 | 0 |
| 5 | 229.7 | 103.2 | 0 |
| 6 | 157.1 | 74.5 | 0 |

- **Switch-to-KorGE latency**: noisy on the Simulator (cycle 3 of run 3
  was actually its slowest, not cycle 1) but consistently well under
  500ms cold and in the 60–120ms range once warm across both runs — no
  loading spinner needed for the "continue after ad" re-entry; a brief
  fade on the very first level entry would be a reasonable hedge against
  the ~370–390ms cold case.
- **`hiddenDwellTicksAdvanced` = 0, every cycle, both runs, no
  exception.** This is the load-bearing result: `GLKViewController`'s
  internal display link genuinely stops firing once its view leaves the
  window via a plain `window.rootViewController =` swap — **no manual
  `.paused = true`/resume plumbing needed.** Directly answers the
  battery-drain question this spike was built to answer.
- **`korgeDwellTicksAdvanced`** (frames rendered per ~700ms while
  visible) started low in run 3 (1–2 ticks in cycles 1–2, rising to
  16–17 by cycles 5–6) and higher-but-still-well-under-60fps in run 5
  (2 then 19–24) — almost certainly Metal shader warm-up on the
  Simulator (`"Metal Compiling Shader"` lines visible in the unified
  log right at launch), not representative of real-device frame rates.
  Don't read the absolute fps here as a real-device number; the
  visible/hidden *on-off* behavior is the reliable part.
- **Memory**: run 3 — 297→303→300→294→267→288 MB; run 5 —
  310→319→314→297→292→275 MB. Both fluctuate without a monotonic growth
  trend across 6 cycles. Not proof of no leak (6 cycles is a small
  sample — a slow leak could easily hide in this noise), but no obvious
  runaway either.
- **`switchToComposeLatencyMsApprox`**: turned out to be a flawed proxy,
  confirmed once real data came in — it measures "time until the next
  *KorGE* tick after hiding it," which is guaranteed to hit its own
  2000ms timeout precisely because the previous bullet's finding is
  true (no more KorGE ticks happen once hidden). It reported exactly
  `~2000.4` for all 6 cycles in run 5, i.e. it timed out every time —
  self-consistent with, not contradicting, the hidden-dwell result, but
  it says nothing about how fast Compose itself becomes visually ready.
  Not re-instrumented this session; a trivial static screen like this
  one is expected to be sub-frame, but that's an expectation, not a
  measurement.

### What's still open

- **Visual flash/glitch check is inconclusive, not clean.** 10
  screenshots were captured (via `xcrun simctl io screenshot`, moved
  into the launch step so they'd land during the switching window) but
  **all 10 landed on the same static Compose "Start Level" screen** —
  the entire 6-cycle sequence completes in ~13–14s real time while each
  screenshot itself costs 1–13s, so external `simctl` polling never
  happened to land inside one of the ~100–500ms KorGE-visible windows.
  If real visual proof is needed before shipping, the fix is to
  deliberately hold KorGE visible for a few seconds on just one cycle
  (not all 6) specifically to make it screenshot-able — not attempted
  this session.
- Only tested on `iosSimulatorArm64`, same caveat as every other iOS
  spike in this file — real-device (`iosArm64`) frame-rate/latency
  characteristics are unverified and could differ meaningfully from the
  Simulator's Metal-emulation numbers above.
- This is still a debug scene (`SwitchSpikeScene`) and a throwaway
  Compose screen (`SpikeComposeScreen`), not real menu/gameplay code.
  `ShellAppDelegate.ios.kt`'s entry lambda currently points at
  `spikeMain()` — **revert to `{ main() }` before/while building the
  real Compose menu integration**, this was left pointed at the spike
  deliberately so the CI evidence above could be gathered, not as an
  oversight.
- `CADisableMinimumFrameDurationOnPhone: true` is only set in
  `ios-shell/project.yml`. If/when the real Compose menu UI moves into
  a different Xcode project or KorGE's own generated
  `build/platforms/ios`, that project's `Info.plist` needs the same key
  or it will hit the identical `PlistSanityCheck` crash.

## `korge-video` feasibility spike (2026-09-01) — NOT VIABLE

**Status: do not build the planned menu video background around
`korge-video`.** Tested as a pure feasibility check (throwaway scene,
`deps.kproject.yml` entry, `runJvm`), fully reverted afterward — no trace
left in the working tree, documented here so the investigation isn't
redone.

- **Doesn't even compile on JVM** against this project's locked KorGE
  `6.0.0`: `:korge-video:compileKotlinJvm` failed with a wall of
  `Unresolved reference` errors (`PlatformAudioOutput`, `delay`, `stop`,
  `dispose`, `setFloatStereo`, `createPlatformAudioOutput`,
  `readShortArrayLE`, `writeArrayLE`) — korau/korio APIs renamed or
  removed between whatever KorGE version `korge-video` was last built
  against and `6.0.0`.
- **[korlibs/korge-video](https://github.com/korlibs/korge-video)**: 0
  stars, 13 commits total, **last real commit 2023-10-05** ("Upgrade
  KorGE to 5.0.5") — nearly 3 years stale. The library's own maintainer
  (soywiz) opened
  [issue #2](https://github.com/korlibs/korge-video/issues/2), "Apply
  patch to fix android on korge 6.0.0", in Feb 2025 with an unmerged
  patch attached — still open, confirming even Android doesn't work
  out of the box against this project's exact KorGE version.
- **iOS backend is a literal empty stub, not just unmaintained**:
  `korge-video/src/nativeMain/kotlin/korlibs/video/internal/KorviInternalNative.kt`
  (the implementation used by every Kotlin/Native target, iOS included)
  is
  ```kotlin
  internal class NativeKorviInternal : KorviInternal() {
  }
  ```
  — overrides nothing, silently falls back to the common base class's
  `DummyKorviVideoLL(3.minutes)`: a fake generated video (solid
  background + elapsed-time text), not real MP4 decoding. A
  `nativeInterop/cinterop/min_ffmpeg.def` suggests an abandoned attempt
  at a real FFmpeg-backed native decoder that was never wired up. No
  issue, open or closed, mentions iOS at all.
- Real decoding only exists for JVM (JCodec-based MP4 demuxer) and
  Android (`MediaPlayer`)/JS. No reusable `View` wrapper ships in the
  library either — consumers hand-roll one from the library's own demo
  app's source, which is what the spike scene did.
- **If video playback is still wanted**: cheaper paths that don't
  need any new dependency are re-encoding the clip as a low-fps
  PNG/JPEG frame sequence animated through KorGE's normal
  `Bitmap`/`Animation` APIs, or a sprite-sheet-style approach — both
  proven, no codec involved.

## AdMob (`basic-ads`) feasibility spike (2026-09-01/02) — VIABLE, proven working on real iOS Simulator (link + on-device run)

**Status: `app.lexilabs.basic:basic-ads` genuinely links AND runs on iOS.** CI run
[33559815333](https://github.com/MalithaBandara/infiltrate-shadow-heist/actions/runs/33559815333)
(commit `27702a7`) confirmed, from raw logs: `syncPodComposeResourcesForIos` `BUILD SUCCESSFUL`,
no crash reports, `LEVEL TRANSITION RESULT: TRANSITION_OK`, storage bridge
`OK:coins=350:unlocked=level_1;level_2;level_4`, and — the actual point of the spike —
`AdMob verify result: OK:initializeCalled=true:bannerLoaded=true`: a real Google-served banner
ad genuinely initialized and loaded on a real iOS Simulator. See "Watch ad to continue — real
feature" below for what got built on top of this, and the on-device crash chase (unrelated to
AdMob) that had to be fixed first.
Isolated to `paywall-build`, same module used for the RevenueCat and
Compose/KorGE spikes above — real CocoaPods wiring was required (unlike
RevenueCat 3.x, which deliberately avoids CocoaPods entirely), and getting
it working took 3 attempts, the last one only solved by reading Kotlin's
own Gradle plugin source directly rather than guessing.

### Why `basic-ads` over the alternatives

Checked three KMP AdMob wrappers. The other two
(`saitawngpha/Admob-KMP`, `AndreSand/ads-kmp`) are 1–3-star, 4–7-commit
personal projects with no Maven Central publication — the same red flags
as `korge-video` above. `app.lexilabs.basic:basic-ads` is real: 109 stars,
87 commits, last commit **2026-08-23** (days before this spike), published
on Maven Central, genuine 1:1 `iosMain`/`androidMain` implementations
(checked its actual file tree, not just the README) for all four ad
formats (Banner, Interstitial, Rewarded, Rewarded Interstitial) plus GDPR
consent handling.

### Attempt 1: plain Maven dependency, no CocoaPods — FAILED

Just `implementation("app.lexilabs.basic:basic-ads:1.2.1")` in
`androidMain`/`iosMain`, no `cocoapods {}` block. Failed at
`:paywall-build:linkDebugFrameworkIosSimulatorArm64`:
```
ld: framework 'GoogleMobileAds' not found
```
Confirms `basic-ads` does **not** bundle Google's SDK into its published
klib the way RevenueCat 3.x bundles its own native SDK (see "RevenueCat
on iOS: PROVEN WORKING" above) — it genuinely needs the pod linked, same
as the CocoaPods-dependent RevenueCat versions that were ruled out
earlier in this project. Its own build declares
`cocoapods { pod("Google-Mobile-Ads-SDK") { version = "13.8.0" } }`
(Kotlin `2.4.10`) — checked directly against its `build.gradle.kts` and
Maven Central Gradle module metadata (only `androidJvm` +
`ios_arm64`/`ios_simulator_arm64` variants published, no `jvm()` desktop
variant, so it can't go in `commonMain` in a module that also targets
`jvm()` — same lesson as `purchases-kmp-core` 3.x elsewhere in this file).

### Attempt 2: add `org.jetbrains.kotlin.native.cocoapods` + `pod()` declarations — FAILED differently

Bumped `paywall-build` from Kotlin `2.3.20` to `2.4.10` to match
`basic-ads`'s own pin (safe direction — a newer Kotlin/Native compiler can
read an older klib, not the reverse; RevenueCat's `3.6.0` klib, compiled
at `2.3.20`, still needed to keep working). Added the `native-cocoapods`
plugin with `pod("Google-Mobile-Ads-SDK") { version = "13.8.0" }` and
`pod("GoogleUserMessagingPlatform") { version = "3.1.0" }` (matching
`basic-ads`'s own exact pod versions), kept the framework declared
manually via `iosArm64 { binaries.framework { baseName = "PaywallModule" ... } }`
(mirroring `basic-ads`'s own `build.gradle.kts`, which combines both
approaches).

Real progress this time — `podSetupBuildGoogle-Mobile-Ads-SDKIosSimulator`,
`podBuildGoogle-Mobile-Ads-SDKIosSimulator`, and
`cinteropGoogleMobileAdsIosSimulatorArm64` all genuinely executed
(CocoaPods fetched and built the real SDK, Kotlin generated real
interop bindings) — but the link step still failed with the **identical**
`ld: framework 'GoogleMobileAds' not found`. The pod existed on disk now;
the linker just didn't know where.

### Root cause — found by reading Kotlin's actual Gradle plugin source, not guessed

Downloaded `KotlinCocoapodsPlugin.kt` and `CocoapodsExtension.kt` straight
from `JetBrains/kotlin` on GitHub. The relevant logic
(`configureLinkingOptions()`):
```kotlin
target.binaries.all { binary ->
    val testExecutable = binary is TestExecutable
    val podFramework = binary is Framework && binary.name.startsWith(POD_FRAMEWORK_PREFIX)
    if (testExecutable || podFramework) {
        configureLinkingOptions(project, cocoapodsExtension, binary)  // <- only this adds -F<path>/-framework
    }
}
```
`POD_FRAMEWORK_PREFIX = "pod"` — a name the plugin assigns to **one
specific framework it auto-creates per Apple target** the moment the
plugin is applied (`createDefaultFrameworks()`, unconditional). The
pod's `-F<frameworkSearchPath>` and `-framework <name>` linker args are
**only** ever attached to that framework (or test executables) — never to
an independently-declared `binaries.framework {}`, no matter its
`baseName`. `basic-ads`'s own build combines both, exactly like our
attempt 2 did, so if it's ever actually tested by directly running its
own `linkDebugFramework*` task rather than only publishing the compiled
klib, it would hit the identical gap — this project just happened to be
the one that needed to actually prove the link, not just compile.

### Attempt 3: configure the plugin's own auto-created framework instead — SUCCEEDED

Removed the manual `iosArm64`/`iosSimulatorArm64` `binaries.framework {}`
blocks. Moved the same config (`baseName = "PaywallModule"`,
`-Xbinary=bundleId=...`, the per-target `swiftLibPath` linker fix already
established for RevenueCat above) into `cocoapods { framework { ... } }`,
which — confirmed from `CocoapodsExtension.kt`'s `framework(configure)` →
`forAllPodFrameworks` — **reconfigures the plugin's already-auto-created,
correctly-wired framework in place**, not a third competing one. Since
that single block runs once per Apple target, branched
`iphoneos`/`iphonesimulator` via the `Framework`'s own `.target.name`
(confirmed real: `binary.target` is the identical property
`configureLinkingOptions()` itself reads).

Result: `:paywall-build:linkDebugFrameworkIosSimulatorArm64` — **`BUILD
SUCCESSFUL in 6m 39s`, 25/25 tasks executed, zero link errors.** Only
output was the already-known-benign RevenueCat module-cache `.pcm`
warnings documented in the RevenueCat section above (unrelated,
pre-existing, cosmetic).

### Exact working config (`paywall-build/build.gradle.kts`)

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.10"
    id("org.jetbrains.kotlin.native.cocoapods") version "2.4.10"
    // ... existing plugins
}
kotlin {
    cocoapods {
        ios.deploymentTarget = "15.0"
        noPodspec()  // embedded into ios-shell/ as a plain .framework, never consumed via a Podfile itself
        pod("Google-Mobile-Ads-SDK") { moduleName = "GoogleMobileAds"; version = "13.8.0"; extraOpts += listOf("-compiler-option", "-fmodules") }
        pod("GoogleUserMessagingPlatform") { moduleName = "UserMessagingPlatform"; version = "3.1.0"; extraOpts += listOf("-compiler-option", "-fmodules") }
        framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            val sdkName = if (target.name == "iosArm64") "iphoneos" else "iphonesimulator"
            swiftLibPath(sdkName)?.let { linkerOpts += listOf("-L$it") }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    // NOT: iosArm64 { binaries.framework { ... } } - the whole point of this section
}
```
`AdMobSpikeUsage.kt` (`paywall-build/src/iosMain/kotlin/`) — real calls to
`BasicAds.Initialize()` and `BannerAd(adUnitId = AdUnitId.BANNER_DEFAULT)`,
same "force the linker to actually resolve it" pattern as
`PaywallUsage.kt`'s RevenueCat call — is what made this a genuine link
test, not a compile of dead-strippable unused code.

### What's still open

- **Android runtime is still untested** — `basic-ads` + `play-services-ads`/
  `user-messaging-platform` compile clean for `:paywall-build`'s Android
  target (`compileDebugKotlinAndroid` succeeds) and the real Android
  `APPLICATION_ID` meta-data is wired (see "Watch ad to continue" below),
  but there is no emulator in this environment and, more fundamentally, no
  Android host Activity anywhere that consumes `paywall-build`'s Compose UI
  at all yet (unlike iOS's `ios-shell/`) — building that is real,
  undone work, not a quick follow-up.
- Only `iosSimulatorArm64` verified — `iosArm64` (real device) untouched,
  same caveat as every other iOS spike in this file.
- `AdMobVerifyScreen.kt`/`AdMobVerifyBridge` (the spike code proven above)
  is still throwaway — it deliberately stays on `AdUnitId.BANNER_DEFAULT`
  (Google's test constant), never a real ad unit, since it runs
  unattended in CI and a real ad unit there would be invalid traffic.
  The real feature is `ContinueAdBridge`/`ContinueAdTrigger`, documented
  next.

## Watch ad to continue — real feature (2026-09-02)

Real AdMob apps + rewarded ad units created in the AdMob console (not test IDs):

| | App ID | Rewarded ad unit ID |
|---|---|---|
| Android | `ca-app-pub-7912148730700666~8824437805` | `ca-app-pub-7912148730700666/8683118378` |
| iOS | `ca-app-pub-7912148730700666~1768074863` | `ca-app-pub-7912148730700666/9506964083` |

Wired in: `paywall-build/src/androidMain/AndroidManifest.xml` (App ID meta-data),
`ios-shell/project.yml` (`GADApplicationIdentifier`, real App ID — safe to use even in the CI
spike, unlike a real *ad unit* ID: the App ID alone doesn't cause invalid-traffic risk),
`AdUnitIds.kt`/`.android.kt`/`.ios.kt` (`expect/actual`, `paywall-build/src/*Main/kotlin/`) —
real ad unit ID per platform, used **only** by the real feature below, never by the CI spike.

### Why the ad can't be triggered directly from KorGE

`:game` (the KorGE module) is locked to Kotlin `2.0.20` and has no Compose runtime; `basic-ads`
needs `2.4.10`+ and is only wired into `paywall-build`'s Compose layer — that's the entire reason
`paywall-build` exists as an isolated composite build (see "RevenueCat on iOS" above). So
`GameplayScene.kt` (common KorGE code) cannot call `RewardedAdHandler`/`RewardedAd` directly; the
request has to cross from `GameMain.framework` to `PaywallModule.framework` via native Swift,
since those are two separately-compiled Kotlin/Native frameworks with no direct interop.

Checked `basic-ads`' actual iOS source (`RewardedAdHandler.kt` on
[LexiLabs-App/basic-ads](https://github.com/LexiLabs-App/basic-ads)) before wiring anything:
`show()` calls `GADRewardedAd.presentFromRootViewController(rootViewController = null, ...)` —
it presents over whatever the **current** `window.rootViewController` is at call time, not
whichever view controller composed the call. Confirms the ad can only present reliably once the
shell has actually swapped to the Compose scene (same reasoning that killed the "second
ComposeUIViewController" approach in the AdMob spike above) — triggering it while KorGE is still
the visible root, hoping Compose's detached scene keeps running invisibly, isn't a safe bet.

### Architecture (poll-based bridges, same pattern as `SpikeBridge`/`AdMobVerifyBridge`)

```
GameplayScene (KorGE)  --[requestContinueAd]-->  GameContinueAdBridge (:game, src@ios, real)
                                                          |
                                                   polled by AppDelegate.swift
                                                          |
                                              switchToCompose() + ContinueAdTrigger.requestShow()
                                                          |
                                          ContinueAdContent() composes RewardedAd(...) (paywall-build)
                                                          |
                                          onRewardEarned -> ContinueAdTrigger.markRewardEarned()
                                                          |
                                                   polled by AppDelegate.swift
                                                          |
                                    GameContinueAdBridge.grantContinue() + switchToKorGE()
                                                          |
                        GameplayScene's own updater sees consumeContinueGranted() -> restarts
                        the level the same way the existing "RETRY INFILTRATION" button already does
```

Two separate bridge objects, not one, because `:game` and `paywall-build` are separate Kotlin/
Native frameworks — Swift is the only thing that can see both:
- `GameContinueAdBridge` (`src/ContinueAdBridge.kt` common `expect`, `src@ios/ContinueAdBridge.ios.kt`
  real actual, `src@android`/`src@jvm`/`src@js`/`src@wasmJs` deliberate no-op actuals matching the
  existing `PurchasesBridge` pattern — `consumeContinueGranted()` must return `false`, never `true`,
  on every platform without a real ad actually watched)
- `ContinueAdTrigger` (`paywall-build/src/iosMain/kotlin/ContinueAdBridge.kt`) — `showRequested` is
  a real Compose `MutableState<Boolean>` (not a plain var) so `ContinueAdContent()` recomposes when
  Swift sets it; `consumeOutcomeFinished()` lets Swift react the instant the ad is dismissed/fails,
  instead of always waiting out the 30s fallback timeout

### What changed in the real gameplay UI

`GameplayScene.kt`'s existing "MISSION FAILED" (`caughtOverlay`) game-over card gets a third
button, **CONTINUE (WATCH AD)**, above the existing RETRY INFILTRATION / RETURN TO MENU (which
keep working independently — tapping CONTINUE only requests the ad, it doesn't hide or disable
the other two, so a failed/declined ad never strands the player). For now, watching the ad to
completion just restarts the current level (`sceneContainer.changeTo { GameplayScene(levelData) }`)
— same as RETRY, just gated behind `getContinueAdBridge().consumeContinueGranted()` in the update
loop, per the user's explicit "for now even if they play the ad just restart the game" scope.

### Verified so far / not yet

- `compileKotlinJvm` (root `:game`, common code + JVM actual) — **BUILD SUCCESSFUL**.
- `paywall-build:compileDebugKotlinAndroid` — **BUILD SUCCESSFUL** (real Android App ID/ad unit
  ID compile clean).
- `:game`'s own `compileDebugKotlinAndroid` — **not verified locally**: blocked by a pre-existing,
  unrelated `korge-ldtk` JVM-toolchain-mismatch error in this environment (fails before reaching
  `:game`'s own Android compile at all) — not caused by this change; the Android actual
  (`src@android/ContinueAdBridge.android.kt`) is a trivial no-op stub with no Android-specific API
  surface, same shape as the already-working `PurchasesBridge` Android stub.
- **iOS: not verified at all locally** — this machine has no Mac/Xcode. The Swift changes
  (`AppDelegate.swift`: `showContinueAd()`, extended `startObservingLevelEnd()` poll) and the new
  `paywall-build`/`:game` iOS Kotlin files have not been compiled or run. Needs a CI push to
  confirm, same as every other iOS change in this file — do not treat this as working until a
  real CI run proves it, per this file's own "verify version-related claims" rule above.
- Not yet tested: what happens if the player backgrounds the app mid-ad, or if `RewardedAd`'s
  `AdState.READY` never resolves within the 30s Swift poll deadline on a slow connection (falls
  back to `switchToKorGE()` without granting — the level just isn't restarted, player can still
  use RETRY).

## RevenueCat version is pinned by iOS klib ABI compatibility, not just Android/JVM metadata

**Note: the section below documents `:game`'s own separate,
still-unresolved `purchases-kmp-core:1.9.0+14.3.0` pin (Android-only,
kept for historical/reference purposes) — a completely different
dependency line from the proven-working `3.6.0` in `paywall-build`
above. Don't conflate the two: this history is about why `:game` itself
is stuck on an old RevenueCat version; the section above is about a
separate, isolated module using a much newer one successfully.**

- `build.gradle.kts` pins `purchases-kmp-core` to `1.9.0+14.3.0` for
  `androidMainApi` only now (`:game` itself still has zero iOS dependency
  on RevenueCat at all — see note just above). This was downgraded from
  `2.10.2+17.55.1`, which itself was a downgrade from `3.5.1` — see the
  Android/JVM metadata-conflict note already in this file. The version
  history below is kept for whoever revisits iOS.
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

(This CocoaPods gap applies specifically to `:game`'s own pinned
`purchases-kmp-core:1.9.0+14.3.0` line — see "RevenueCat version is
pinned by iOS klib ABI compatibility" above. It does NOT apply to the
`3.x` line proven working in the isolated `paywall-build` composite
build — see "RevenueCat on iOS: PROVEN WORKING" further above, which
confirmed empirically that `3.x` needs zero CocoaPods/Podfile since it
bundles its native SDK directly into the klib. Kept in full below since
the `ios-build.yml` workflow itself is still active and this remains
accurate background for `:game`'s own current dependency.)

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
- `game.scene`: KorGE gameplay presentation layer:
  - `UiComponents.kt`: UI constants and GPU vector rendering utilities (`uiGraphics`, `drawStar`, cyber theme colors) used across `GameplayScene`.
  - `PlayerAnimations.kt`: Frame-accurate sprite sheet animations for operative actions (idle, run, jump, sneak/crouch, vault/climb).
  - `GameplayScene.kt`: Core gameplay engine. Immersive stealth parkour level styled with modern AAA stealth mobile UI matching the Compose Multiplatform cyber-tactical theme (`BebasNeue` typography, `COLOR_DARK_BG`, cyan/gold/green/red neon accents, GPU vector rendering). Features zoomed character action (`worldZoom = 1.35x` centering operative and obstacles), fixed vertical ground alignment (`worldView.y = canvasH - (baseGroundY + 70.0) * worldZoom`), unzoomed atmospheric looping background layer (`bgmg2.png` post-processed with electric cyan/steel-blue duotone color grading at native 480px height scrolling with 0.2x horizontal parallax), lowered ground floor level (`groundY = 410.0` maximizing playable vertical space), integrated game silhouette assets (`crate.png`, `chainedcrate.png`, and `chainedhook.png` tightly cropped to exact visual bounds without transparent offsets), flat solid black silhouette ground and structural platforms, Level 1 sequence featuring initial ground running approach (x = 60..580), half-height step crate (48px high, 68px wide) at x = 580 to climb up onto a long elevated structural platform (96px high, length 900px), overhead hanging chained crate at x = 1050 (clearing 54px above platform) requiring operative to crouch-walk underneath, atmospheric hanging chained hook at x = 1380, solid architectural terrain blocks across the 3200px corridor, far-corner guard patrol and security camera coverage above extraction beacon, ground-aligned character sprite anchoring (calibrated feet contact line eliminating floating gap), modern translucent GPU vector-rendered mobile touch controls (Left/Right vector chevrons, Jump/Vault arrow, Sneak/Crouch arrow with glowing neon touch states), floating translucent top HUD with sleek rounded stealth radar capsule, live threat & detection radar bar, stance indicator, formatted stopwatch pill (`mm:ss.t`), modern 36x36 vector pause icon button, dynamic floating powerup quick-dock, and themed modal overlays (Tactical Pause, Mission Failed with recon tips, Level Complete with 3-star rating reveal & 2x multiplier coin bounty rewards). All debug cheat codes removed for clean production builds. All non-gameplay screens (MainMenu, LevelSelect, Store, Settings, Paywall) are fully delegated to Compose Multiplatform.

## Non-Gameplay UI Migration to Compose Multiplatform (2026-09-01) — MainMenu Ported

**Status: IN PROGRESS — MainMenu ported to Compose Multiplatform (`paywall-build`), LevelSelect/Store/Settings stubbed as placeholder navigation targets.** KorGE (`:game`) becomes gameplay-only, entered when starting a level via `window.rootViewController` swap on iOS (and warm view-swap candidate on Android).

### 1. Model Sharing across Gradle Composite Builds (`srcDir`)
- **Finding**: Composite build dependency substitution (`implementation("com.sample.demo:korge-hello-world")`) failed because Gradle included builds cannot resolve parent build artifacts without explicit Maven publishing.
- **Mechanism**: `src/game/model/` is 100% engine-agnostic standard Kotlin library (`kotlin.math`). Adding `kotlin.srcDir("../src/game/model")` in `paywall-build/build.gradle.kts` allows `paywall-build` (Kotlin `2.3.20`) to compile `GameProfile.kt`, `LevelData.kt`, `Geometry.kt`, `Powerup.kt`, etc. directly from source alongside `:game` (Kotlin `2.0.20`).
- **Standing Constraint**: All files under `src/game/model/` must remain pure Kotlin (standard library only) and compile cleanly under **both** Kotlin 2.0.20 and 2.3.20. Zero imports of `korlibs.*` or engine-specific types are permitted. Guarded by an automated test (`ZeroKorlibsLintTest`).

### 2. Exact Storage Keys & Zero-Drift Persistence Bridge
- Because `paywall-build` compiles `src/game/model` from source, it directly instantiates `MapBackedGameProfileStorage` and `MapBackedLevelStorage`.
- Discrete storage keys persisted:
  - `user_coins`: String-formatted Int (e.g. `"100"`, `"350"`)
  - `user_is_premium`: String-formatted Boolean (`"true"` / `"false"`)
  - `user_music_vol`: String-formatted Float (e.g. `"0.8"`)
  - `user_sfx_vol`: String-formatted Float (e.g. `"1.0"`)
  - `user_controls_swapped`: String-formatted Boolean (`"true"` / `"false"`)
  - `user_language`: String-formatted language code (e.g. `"en"`)
  - `user_unlocked_levels`: Semicolon-delimited level IDs (e.g. `"level_1;level_4"`, `"level_1;level_2;level_4"`)
  - `user_powerups`: Semicolon-delimited `id:count` pairs (e.g. `"smoke_screen:2;phantom_cloak:2"`)
  - `level_result_<levelId>`: CSV formatted `"$levelId,$completed,$wasDetected,$timeTaken,$timeTargetSeconds"`
- On iOS: `PlatformStorage` delegates to `PaywallStorage`, which uses `NSUserDefaults(suiteName = "korge")` and key prefix `"org.korge.storage."`, matching KorGE's `DarwinNativeStorage` byte-for-byte.

### 3. Android Warm Engine Symmetry (Candidate Architecture — Unspiked)
- **Status**: Android Compose wiring is **out of scope for this pass** and deferred to a dedicated Android pass.
- **Architecture Note**: Multi-Activity (`startActivity`/`finish`) would destroy and recreate the KorGE Activity on every level transition, forfeiting the warm resident engine property proven on iOS (~60–120ms warm swap).
- **Candidate for Future Spike**: Single-Activity with a parent `FrameLayout` holding both `ComposeView` and KorGE's `KorgwSurfaceView` (or `GLSurfaceView`), toggling `visibility = View.VISIBLE` vs `View.GONE` to pause the OpenGL render thread without engine destruction.

### 4. iOS Shell Architecture (`ios-shell/Sources/AppDelegate.swift`)
- `MainMenuComposeScreen.shared.makeViewController(onStartLevel:)` (exported via `@ObjCName(name = "MainMenuComposeScreen", exact = true)`) serves as the initial `window.rootViewController`.
- Tapping "PLAY" swaps `window.rootViewController` to `self.korgeVC` (the warm resident KorGE GLKViewController).
- When a level ends, `AppDelegate` detects the event and swaps `window.rootViewController` back to `self.composeVC`.
- On launch, `AppDelegate` runs on-device storage bridge validation against real profile fields and writes `OK:coins=350:unlocked=level_1;level_2;level_4` to `storage_bridge_result.txt` for CI verification.

### 5. Multiplatform Entry Point Rules (`src/main.kt`)
- KorGE's auto-generated iOS bootstrap (`build/platforms/native-ios/bootstrap.kt`) calls `suspend fun main()` with zero arguments.
- `src/main.kt` must always expose a parameterless `suspend fun main() = main(emptyArray())` alongside `suspend fun main(args: Array<String>)`.
- Never use JVM-only APIs such as `java.lang.System.getProperty` in `src/main.kt` or common code; use `korlibs.io.lang.Environment["key"]` or `args.firstOrNull()` which compile across Kotlin/JVM, Kotlin/Native (iOS), and Kotlin/JS.
