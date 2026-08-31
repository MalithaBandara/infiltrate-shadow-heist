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
+ `PaywallModule.framework` together, to get the first on-device signal
for that bridge, has run in real CI once and **genuinely failed** (an
architecture mismatch — the Kotlin/Native frameworks are arm64-only,
`xcodebuild` defaulted to building an x86_64 slice too). The suspected
duplicate-Kotlin-runtime-symbol risk was never actually reached. A fix
is applied but **not yet pushed for a second attempt** — see "Native iOS
shell: `ios-shell/`" below for the full, verbatim story before assuming
either the original risk or the fix's success/failure.

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

## Native iOS shell: `ios-shell/` (2026-08-31) — attempt 1 FAILED (real CI run), fix applied, attempt 2 pending

**Status: one real CI run completed, genuinely failed (architecture
mismatch, see "Attempt 1" below), fix applied and re-verified locally
where possible, NOT yet pushed for a second run.** This dev machine has
no Xcode/simulator, so `:game`'s own two new Kotlin files can only be
compile-checked by CI (see risk 2 below); everything Xcode/link-level
can *only* be verified by CI, never locally. Do not assume the fix
worked until a future session reports attempt 2's real raw logs — same
discipline that caught attempt 1's real failure past a misleading
`"success"` API conclusion.

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

### Real, concrete risks — still not resolved after the attempt-1 fixes

1. **Duplicate Kotlin/Native runtime symbols** — still genuinely
   untested. Attempt 1 never got far enough to hit this. Still the most
   likely next blocker: each Kotlin/Native framework embeds its own
   runtime, and linking two independently-compiled ones (different
   Kotlin versions) into one binary is a known way to get duplicate-
   symbol errors. If the next run fails with something like `duplicate
   symbol '_kotlin...'`, that's this risk materializing — the fix is in
   how the frameworks are built, not in `ios-shell/`'s Swift code.
2. **`:game`'s own `compileKotlinIosSimulatorArm64` is disabled on this
   Windows machine** (`Skipping task ... as task onlyIf 'Task is
   enabled' is false`) — a KorGE-plugin-specific gate, NOT a general
   Kotlin/Native limitation (the identical task for `paywall-build`,
   plain `kotlin("multiplatform")`, runs fine here). Root cause not
   traced (time-boxed). Practical effect, still true after the
   `exact = true` fix above: the two `:game`-side files can only be
   compile-checked by CI, never locally.
3. Whether `exact = true` actually produces the unprefixed symbol is
   **still unconfirmed** — the fix compiles locally (klib level only);
   only a real link (CI) proves whether the ObjC name is really
   `ShellAppDelegate`/`DebugStorageBridge`/`PaywallStorage` now.

### What's still NOT verified / NOT done

- Attempt 1's two fixes (`EXCLUDED_ARCHS`, `@ObjCName(exact = true)`)
  are implemented but **not yet pushed for attempt 2** as of this
  writing — don't assume they resolved anything until a future session
  reports a real run's raw logs, the same discipline as attempt 1 above.
- No paywall UI, no `PurchasesBridge.ios.kt` wiring — unchanged, out of
  scope for this step.
- Only `iosSimulatorArm64` — `iosArm64` (real device) untouched, same
  caveat as the `paywall-build` spike above.

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
- `game.scene`: KorGE presentation and navigation layer:
  - `UiComponents.kt`: Reusable high-tech UI components (tactical buttons with corner notch accents and responsive hover/press states, glassmorphic panels, dossier cards, top bar with integrated Operative Star status & Heist Coin pill badges, cyber alert toast feedback, atmospheric skyline backdrop, 5-point star graphics).
  - `SplashScene.kt`: Atmospheric launch sequence with encrypted link decryption status, scanning lines, glowing logo branding, and auto-transition to Main Menu.
  - `MainMenuScene.kt`: Command hub with operative dossier card (rank, heist intel, tactical directives), hero infiltration action button, black market access, and settings terminal navigation.
  - `LevelSelectScene.kt`: Classified mission selection dossier grid displaying operation number, lock status, star ratings (0-3), target times, coin bounties, best record times, and smooth touch scrolling.
  - `StoreScene.kt`: Black market contraband depot featuring the hero Shadow Operative Pass with gold neon border, tiered coin caches, tactical powerups (smoke screen, EMP scrambler, phantom cloak), and restore purchases wired to `PurchasesBridge`.
  - `SettingsScene.kt`: Tactical configuration terminal with interactive music & SFX volume sliders, restore purchases, privacy policy, terms of service, and clear cache options.
  - `GameplayScene.kt`: Immersive stealth parkour level with stark white high-contrast background and solid black silhouette architecture, extraction beacon, ground-aligned character sprite anchoring (calibrated feet contact line eliminating floating gap), tactile mobile touch controls (Left/Right virtual D-Pad, Jump/Vault, Sneak/Crouch), top glassmorphism HUD with live threat & detection radar bar, stance indicator, stopwatch timer, and modal overlays (Pause, Mission Failed with tactical recon tips, Level Complete with 3-star rating reveal & 2x multiplier coin bounty rewards).