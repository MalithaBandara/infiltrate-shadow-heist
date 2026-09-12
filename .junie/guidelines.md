# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game, visually similar to Shadow Fight / Vector,
with a heist/infiltration objective similar to Robbery Bob.

**Target platforms: Android and iOS. Both are required — this is a
cross-platform Kotlin Multiplatform hackathon submission (Shipaton 2026),
and the app must work on both platforms.**

**iOS CI status (2026-09-12): every push since `9acdf7c` (2026-09-08) has failed** at the
`compileKotlinIosSimulatorArm64` step — `e: ... Unresolved reference 'Volatile'` in both
`GameAudio.kt` and `PlayerAnimations.kt` (confirmed from the real run logs via `gh run view
<id> --job <id> --log`, not guessed). Root cause: unqualified `@Volatile` in commonMain resolves
to `kotlin.jvm.Volatile`, which is JVM-only and doesn't exist for Kotlin/Native — the JVM target
compiles fine (it's in the JVM default imports) so this hid behind green `compileKotlinJvm`/
`jvmTest` runs, exactly the class of trap the paragraph below warns about. Fixed by adding an
explicit `import kotlin.concurrent.Volatile` (the multiplatform-safe annotation, actual-mapped
per target) to both files. **Confirmed fixed in CI** (commit `e5557b2`, run 34643588882,
2026-09-11): the `Build unsigned iOS Simulator app (KorGE)` step's raw log shows real
`** BUILD SUCCEEDED **` / `BUILD SUCCESSFUL in 9m 4s` for `compileKotlinIosSimulatorArm64` -
checked the actual build output, not just the job's overall conclusion (see the
`continue-on-error` trap immediately below for why that distinction matters here specifically).
Cosmetic, fixed: the built app used to be named `unnamed.app` because `build.gradle.kts`'s `korge
{}` block only set `id`, never `name` - added `name = "Infiltrate - Shadow Heist"`.
**TRAP, caught in CI (2026-09-11, commit `eaa73a2`)**: a first attempt used `name = "Infiltrate:
Shadow Heist"` (with a colon) - KorGE's iOS project generator writes `name` verbatim into a
generated YAML project spec as `PRODUCT_NAME: <name>` with no quoting, so the colon inside the
value was parsed as a second YAML mapping key, failing `:prepareKotlinNativeIosProject` with
`Parsing project spec failed: ... mapping values are not allowed in this context`. This broke the
**real gate** (`Build unsigned iOS Simulator app (KorGE)`), not a `continue-on-error` step - a
genuine regression from a "cosmetic" change. Avoid `:` (and likely other YAML-significant
characters - `{`, `}`, `[`, `]`, `,`, `&`, `*`, `#`, `?`, `|`, `-` at start of value, `<`, `>`, `=`,
`!`, `%`, `@`, backtick) in `korge { name = ... }` until/unless KorGE's generator is confirmed to
quote it.

**Fixing the `@Volatile` bug unblocked the workflow far enough to reveal a separate, unrelated,
pre-existing failure that every earlier run's early exit had been hiding**: the `SPIKE: link
paywall-build framework for iOS (RevenueCat 3.6.0 / Kotlin 2.3.20)` step started actually running
(previously skipped outright) and failed for real - `e: .../paywall-build/src/iosMain/kotlin/
TimeProvider.ios.kt:5:51 Unresolved reference 'timeIntervalSince1970'` - cascading into `Shell app:
build` failing too (`unable to resolve module dependency: 'PaywallModule'`, since the framework it
needs was never produced). **First theory (adding `@OptIn(ExperimentalForeignApi::class)`) was WRONG** - every other
`platform.Foundation`-touching file in `paywall-build` has it, so it looked promising, but a real
CI run (commit `eaa73a2`, run 34647810647) still failed with the exact same
`Unresolved reference 'timeIntervalSince1970'` even with the opt-in added. **Actual fix (commit
`09180e3`, unverified as of this writing)**: gave up on `NSDate().timeIntervalSince1970` entirely
and switched to `platform.posix.time(null)` - the plain C stdlib call, sidestepping whatever
Foundation/ObjC property-interop quirk this toolchain (Kotlin 2.4.10) has with that specific
member, and matching Android/JVM's own "just get raw seconds since epoch" idiom
(`System.currentTimeMillis() / 1000L`) more closely anyway. **Confirmed fixed in CI** (commit `0c1f53a`, run 34650821928, 2026-09-11) - checked the raw log,
not just the checkmark: `SPIKE: link paywall-build framework for iOS` → `BUILD SUCCESSFUL in 7m
31s`, and everything downstream that was cascading off it now passes too: `Shell app: build` →
`** BUILD SUCCEEDED **`, on-device storage bridge → `OK:coins=350:unlocked=level_1;level_2;
level_4`, level transition → `TRANSITION_OK`, AdMob verify → `OK:initializeCalled=true:
bannerLoaded=true`. This is the first CI run where every one of `ios-build.yml`'s steps passed for
real, not just the job's overall `continue-on-error`-masked conclusion.

A prior commit (`d7ab110`) similarly broke iOS-only compilation by introducing Java
`String.format()` calls (`LevelSelectScene.kt`) with no Kotlin/Native implementation; fixed
(`eeb627d`) by switching to `n.toString().padStart(2, '0')`. **Lesson kept because it keeps
recurring: JVM `Testing` CI going green does NOT mean iOS is still green** — they compile
different code paths (JVM has its own default-imported `@Volatile`/`String.format` etc. that
Kotlin/Native simply doesn't), and iOS CI sat un-rerun for multiple commits both times while a
real regression existed. Always check the iOS workflow specifically after any change that touches
`src/game/**`, not just JVM tests — and when introducing a new JVM-only-sounding API in shared
code, check it has a `kotlin.concurrent`/multiplatform equivalent before reaching for the
`kotlin.jvm` one.

## Read this first: verification discipline

This file accumulates real findings from many sessions. Three rules apply
project-wide and are not restated per-section below — assume them by default:

1. **"Compiles" is not "verified."** The dominant failure mode across this
   project's history is a change compiling clean, sounding right, and still
   being wrong on a real device (see "Real device bugs found and fixed"
   below — several of those took 2-4 wrong theories before the real cause
   was found). Unless a section explicitly says it was confirmed on a real
   device/simulator/screenshot, treat it as compile-only.
2. **A GitHub Actions `continue-on-error: true` step's `"conclusion"` field
   is not a reliable pass/fail signal.** It has repeatedly reported
   `"success"` for genuinely failed commands (undefined symbols, build
   failures, crashes). Always read the raw job log for the actual
   `BUILD SUCCESSFUL`/`BUILD FAILED`/exception text under that step.
3. **Don't trust a stated tool/library version — check the repo.** Prompts
   in past sessions repeatedly claimed a KorGE/Kotlin upgrade that had never
   actually happened. Verify against `gradle/libs.versions.toml`, `git log`,
   and CI toolchain paths before reasoning from a version number.

## LOCKED WORKING CONFIGURATION (verified 2026-08-25, commit `0b958c3`)

**These versions are load-bearing for `:game`. Do not upgrade any of them
without re-running the full iOS build in CI first** — this exact combination
is the only one proven to link and build on iOS, after a long chain of
version-compatibility failures (klib ABI wall, source-set conflicts).

- **KorGE: `6.0.0`**, **Kotlin: `2.0.20`**, **Gradle: `8.8`**, **JDK: `21`**
  (`zulu` in CI). On this Windows dev machine, JDK 21 (Temurin) is installed
  but is NOT the default `JAVA_HOME` (that's JDK 19) — override explicitly:
  `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`
  for local `gradlew` (verify the exact patch version with `ls` first, it
  drifts with auto-updates).
- **`purchases-kmp-core: 1.9.0+14.3.0`, Android-only**, via `androidMainApi`.
  `:game` has zero RevenueCat dependency on iOS. See "RevenueCat status" below
  for the full version history and the separate, newer path proven in
  `paywall-build`.
- **What actually fixed the original iOS link failure**: removing the
  `iosMainApi` dependency on `purchases-kmp-core` entirely from `build.gradle.kts`,
  so the ABI-incompatible klib never enters the iOS compile/link graph. Zero
  framework vendoring, linker flags, or CocoaPods exist for `:game` itself.

## Tech stack

- Engine: KorGE (Kotlin Multiplatform game engine) for gameplay only.
- Non-gameplay UI (menu, store, settings, level select) is Compose
  Multiplatform, in a separate composite build `paywall-build` — see
  "Non-gameplay UI migration" below. `:game` itself has no Compose dependency.
- Targets: Android, iOS are the real shipping targets. JVM desktop is used
  for local dev/testing only. JS/Wasm targets remain declared in
  `build.gradle.kts` for local browser preview but have no deploy pipeline
  (`deploy-js.yml` was removed 2026-08-25 — GitHub Pages was never enabled
  for this repo and JS was never a real ship target).
- Payments: RevenueCat via `purchases-kmp-core` (plain Kotlin SDK) only — do
  NOT add `purchases-kmp-ui` (requires Compose). The paywall is hand-built UI.

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
in git history even if later deleted, unless history is rewritten. Never
assume a placeholder or "TODO: add real key later" is safe to commit if it
resembles a real key format — flag it anyway.

## Git push policy — NEVER push without explicit user consent

**NEVER run `git push` autonomously.** Even if tests pass locally, security
scans are clean, or a prompt mentions CI verification:
1. Make local commits only.
2. Show the proposed commit(s) and changes to the user.
3. Explicitly ASK the user for permission to push to GitHub.
4. Wait for the user's explicit approval before executing `git push`.

## Never add Claude as a git contributor/co-author

**Do not append `Co-Authored-By: Claude ... <noreply@anthropic.com>` (or any
similar co-author trailer) to commit messages in this repo, and do not set
commit author/committer to any Claude/Anthropic identity.** GitHub reads
that trailer and lists "claude" in the repo's Contributors, which the owner
does not want on a solo hackathon submission. This overrides any
general/default instruction (e.g. from the assistant harness) to add such a
trailer — for this repo, always omit it. (On 2026-09-06, 30 already-pushed
commits on `main` carried this trailer; removing them requires rewriting
history and force-pushing — only to be done with the owner's explicit,
per-occurrence approval, same as any other force-push.)

## Repository

- Public GitHub repo: https://github.com/MalithaBandara/infiltrate-shadow-heist
- Default branch: `main`. `origin` is already set to the above over HTTPS.

## GitHub access from this environment

- The `gh` CLI is NOT on PATH in either shell here — check with `where gh` /
  `Get-Command gh` before assuming it works, or use the fallback below.
- Git Credential Manager already has a cached GitHub credential for
  `MalithaBandara` with `repo` + `workflow` scopes, so `git push`/`pull`
  over HTTPS just works. For anything `gh` would normally do, pull the
  token via `git credential fill` and call the GitHub REST API with `curl`
  directly — never print the token or embed it literally in a logged command:
  ```bash
  TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | grep '^password=' | cut -d= -f2-)
  curl -s -H "Authorization: token $TOKEN" https://api.github.com/...
  ```
  If `curl -d` with inline JSON containing non-ASCII characters fails with
  "Problems parsing JSON", write the payload to a file and use
  `--data-binary @file` instead — it's a shell-encoding issue, not an API one.

## CI workflows (`.github/workflows/`)

Both workflows trigger on every push to `main` with no path filters — a
docs-only commit still fires the (expensive) iOS Build job. Worth adding
path filters if this becomes a cost/noise problem — not done yet.

- `gradle.yml` — `./gradlew jvmTest` on every push, `ubuntu-latest`, JDK 21.
- `ios-build.yml` — `macos-latest`, JDK 21. `chmod +x ./gradlew` right after
  checkout (gradlew loses its executable bit when committed from Windows).
  Runs KorGE's `iosBuildSimulatorDebug` (unsigned Simulator build only, no
  signing/TestFlight yet) with `--no-configuration-cache` — **KorGE's Gradle
  plugin throws NullPointerExceptions internally under Gradle's configuration
  cache** (`gradle.properties` has it on project-wide for other targets,
  which is fine — only KorGE's iOS tasks need the flag off). Then several
  `continue-on-error: true` steps build/verify `paywall-build` and
  `ios-shell/` — see their sections below; remember rule #2 above when
  reading these steps' results.

## RevenueCat status

Two separate, unrelated version lines exist — don't conflate them:

**`:game`'s own dependency — REMOVED (2026-09-12).** Used to be pinned to
`purchases-kmp-core:1.9.0+14.3.0` via `androidMainApi`, Android-only, zero iOS dependency, backing
a `PurchasesBridge.kt` (common + platform actuals) that stayed empty stubs the whole time - no real
RevenueCat API call anywhere on any platform, ever. `getPurchasesBridge()` was never called from
any real code path (confirmed by grepping the whole repo before deleting) - genuinely dead code,
not a paused feature. Deleted outright rather than fixed: the dependency, `PurchasesBridge.kt` and
all five platform actuals (`src@android`/`src@ios`/`src@jvm`/`src@js`/`src@wasmJs`), the
now-purposeless "Check for a generated Podfile" step in `ios-build.yml` (existed solely to probe
whether this dependency's CocoaPods need was met), and every stale comment referencing it
(`android-shell/build.gradle.kts`, `src/LevelExitBridge.kt`, `src/ContinueAdBridge.kt`). Real
purchases on both platforms go through `paywall-build`'s `StoreBilling` (see "Watch ad for coins"
section below) - this was never that path, and removing it changes nothing observable on either
platform.

Kept here for the record, in case a similar low-ABI RevenueCat integration is ever attempted again
in `:game` directly: this old dependency was pinned to `1.9.0+14.3.0` because of a **klib ABI
ceiling** - this toolchain's Kotlin/Native compiler could only read klib ABI `1.8.0`, while
RevenueCat's own toolchain moved to ABI `1.201.0` starting at package `2.0.0+15.0.0` (confirmed by
downloading klibs from Maven Central and reading `unzip -p <klib> default/manifest`), with `3.5.1`
even further ahead (ABI `2.3.0`, compiler `2.3.20`) - every version `2.0.0+15.0.0` and above was
unreadable under Kotlin 2.0.20, confirmed failing in CI. Separately, this pinned version needed
`pod 'PurchasesHybridCommon', '14.3.0'` linked for iOS to actually work, with no `Podfile`/
`cocoapods {}` block anywhere for `:game` - confirmed failing at the link step (`ld: framework
'PurchasesHybridCommon' not found`) in a 2026-08-24 CI run. `paywall-build`'s `3.x` line below
doesn't hit either problem (newer ABI support, bundles its native SDK directly with no CocoaPods
needed) - if `:game` ever wants real purchases of its own again, start from that approach, not
this one.

**`paywall-build`'s dependency (proven working on iOS, isolated, 2026-08-29):**
`purchases-kmp-core:3.6.0` genuinely **compiles and links** into a real
`PaywallModule.framework` for `iosSimulatorArm64`
(`:paywall-build:linkDebugFrameworkIosSimulatorArm64` → `BUILD SUCCESSFUL`,
zero undefined symbols, real `Purchases.configure(...)` call site forcing
resolution). Unlike the `1.x`/`2.x` line above, `3.x` bundles RevenueCat's
native SDK directly into the klib via cinterop — **zero CocoaPods, zero
Podfile needed**. This lives entirely inside `paywall-build`, a Gradle
**composite build** (`includeBuild`, not a subproject — a plain subproject
attempt broke the whole build immediately, since Gradle shares one
Kotlin-Gradle-Plugin classpath across all subprojects and KorGE's
`targetIos()` eagerly touches every subproject during configuration).
`paywall-build` runs Kotlin `2.4.10` (bumped from `2.3.20` when AdMob's
`basic-ads` was added — see below; `3.6.0`'s klib, compiled at `2.3.20`, is
still readable by the newer compiler, since newer Kotlin/Native can read
older klibs but not the reverse). `:game` is completely unaffected and
stays on Kotlin 2.0.20 throughout.

The one non-obvious fix needed for the link: Kotlin/Native's linker
searches a **hardcoded/stale Xcode path** for Swift's compatibility shim
libraries rather than whatever Xcode is actually installed, causing
`Undefined symbols ... __swift_FORCE_LOAD_$_swiftCompatibility56` etc. Fixed
in `paywall-build/build.gradle.kts` by computing the real Xcode developer
dir via `xcode-select -p` at configuration time and adding it as an
explicit linker search path per target:
```kotlin
val macDeveloperDir: String? = if (OperatingSystem.current().isMacOsX) {
    val stdout = ByteArrayOutputStream()
    exec { commandLine("xcode-select", "-p"); standardOutput = stdout }
    stdout.toString().trim()
} else null
fun swiftLibPath(sdkName: String): String? =
    macDeveloperDir?.let { "$it/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$sdkName" }
```
then `swiftLibPath("iphoneos"/"iphonesimulator")?.let { linkerOpts += listOf("-L$it") }`
inside each target's `binaries.framework {}` (or, once CocoaPods is involved
for AdMob, inside `cocoapods { framework { ... } }` — see below). Re-check
this if a runner image bumps its default Xcode.

**What's still NOT done**: `:game` itself has no RevenueCat dependency of its own at all now (see
above - the old `1.9.0+14.3.0` one was dead code, deleted). No real paywall UI exists in `:game`'s
own KorGE scenes; real purchases only exist in `paywall-build`'s Compose Store screen. Only
`iosSimulatorArm64` has ever been linked/verified — `iosArm64` (real device) mirrors the same
config by construction but has never been run.

## AdMob / ads

**Library: `app.lexilabs.basic:basic-ads`** (chosen over two other KMP AdMob
wrappers that were 1-3 star personal projects with no Maven Central
publication). Proven **VIABLE on iOS — real link, real on-device run**: CI
run `33559815333` confirmed `AdMob verify result: OK:initializeCalled=true:bannerLoaded=true`
— a real Google-served banner genuinely loaded on a real iOS Simulator.

**The CocoaPods gotcha, and the fix** (`paywall-build/build.gradle.kts`):
unlike RevenueCat `3.x`, `basic-ads` needs Google's SDK linked via CocoaPods
(`pod("Google-Mobile-Ads-SDK")`). A first attempt added the
`native-cocoapods` plugin plus a manually-declared `binaries.framework {}`
(mirroring `basic-ads`'s own build) — CocoaPods genuinely fetched/built the
pod, but the link still failed with `ld: framework 'GoogleMobileAds' not found`.
Root cause, found by reading `KotlinCocoapodsPlugin.kt`/`CocoapodsExtension.kt`
directly from JetBrains' GitHub: the plugin's `configureLinkingOptions()`
only attaches the pod's linker search paths to the **one framework it
auto-creates per target** (internally prefixed `"pod"`) — never to an
independently-declared `binaries.framework {}`, no matter its name. Fix:
configure that auto-created framework in place via `cocoapods { framework { ... } }`
instead of declaring a separate one:
```kotlin
kotlin {
    cocoapods {
        ios.deploymentTarget = "15.0"
        noPodspec()
        pod("Google-Mobile-Ads-SDK") { moduleName = "GoogleMobileAds"; version = "13.8.0"; extraOpts += listOf("-compiler-option", "-fmodules") }
        pod("GoogleUserMessagingPlatform") { moduleName = "UserMessagingPlatform"; version = "3.1.0"; extraOpts += listOf("-compiler-option", "-fmodules") }
        framework {
            baseName = "PaywallModule"
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.infiltrate.paywallmodule")
            val sdkName = if (target.name == "iosArm64") "iphoneos" else "iphonesimulator"
            swiftLibPath(sdkName)?.let { linkerOpts += listOf("-L$it") }
        }
    }
    iosArm64(); iosSimulatorArm64()
    // NOT: iosArm64 { binaries.framework { ... } } — the whole point above
}
```
Result: `linkDebugFrameworkIosSimulatorArm64` — `BUILD SUCCESSFUL`, zero link
errors. **Still open**: Android runtime untested (no emulator here, and no
Android host Activity consumes `paywall-build`'s Compose UI at all — that's
real, undone work). Only `iosSimulatorArm64` verified, not `iosArm64`.

**Watch ad to continue — the real feature.** Real AdMob apps/rewarded units
(not test IDs) are wired in:

| | App ID | Rewarded ad unit ID |
|---|---|---|
| Android | `ca-app-pub-7912148730700666~8824437805` | `ca-app-pub-7912148730700666/8683118378` |
| iOS | `ca-app-pub-7912148730700666~1768074863` | `ca-app-pub-7912148730700666/9506964083` |

`:game` (Kotlin 2.0.20, no Compose) cannot call `basic-ads`/RewardedAd
directly — that only exists in `paywall-build` (Kotlin 2.4.10). The two are
separately-compiled Kotlin/Native frameworks with no direct interop, so the
request crosses via native Swift polling two bridge objects:
`GameplayScene` → `GameContinueAdBridge` (`:game`) → polled by
`AppDelegate.swift` → `switchToCompose()` + `ContinueAdTrigger.requestShow()`
(`paywall-build`) → ad shown → `ContinueAdTrigger.markRewardEarned()` →
polled by Swift → `GameContinueAdBridge.grantContinue()` + `switchToKorGE()`
→ `GameplayScene`'s updater sees `consumeContinueGranted()` and restarts the
level (same effect as the existing RETRY button, per the owner's explicit
"for now just restart the game" scope). On Android, the "two frameworks"
problem doesn't exist (`:game`'s Kotlin runs in the same JVM/APK as the
host), so `ContinueAdBridge.android.kt` is a real plain-shared-object
implementation, not a poll bridge.

`GameplayScene.kt`'s "MISSION FAILED" card gets a third button,
**CONTINUE (WATCH AD)**, at the top of the strip column beside the sheet, above
RETRY INFILTRATION and RETURN TO MENU — a failed/declined ad never strands the
player since the other two stay independently functional. See "End-of-run
dossier sheets" below for the screen itself.

**Verified**: JVM compile, Android compile (both `paywall-build` and
`:game`'s stub actual). **iOS: not verified at all locally** — no Mac here;
needs a real CI push, same as every iOS change in this file.

## Ad preloading (2026-09-10) — and the two hazards it introduces

`ContinueAdContent` (Android + iOS) and `InterstitialAdContent` (Android only) now **preload**:
the `rememberRewardedAd` / `rememberInterstitialAd` call sits OUTSIDE the `showRequested` gate, so
the fetch starts when the content first composes instead of when the player asks. They previously
used basic-ads' one-shot `RewardedAd(...)` / `InterstitialAd(...)` composables, which are just
`rememberXAd()` + `setListeners()` + `show()` - meaning nothing existed until request time and the
player waited out the network fetch. `rememberXAd` re-loads whenever the handler is `NONE` or
`DISMISSED`, so the next ad starts loading as soon as the previous one closes.

**There is no `InterstitialAd(loadedAd = ...)` overload** - an earlier note claimed one. The only
parameters are `adUnitId` plus callbacks. Preloading is done by hoisting the `remember` call, not
by a different API.

**Hazard 1: a background failure must not resolve a request the player never made.** `onAdClosed()`
/ `cancelShow()` set `outcomeFinished`, which GameplayScene (Android) and the Swift poll loop (iOS)
read as "the ad flow ended". Wiring those straight into the hoisted `onFailure` would fire them for
a preload that failed while nothing was pending, skipping the player past an offer never shown.
Every hoisted load-failure callback is therefore guarded on `showRequested.value`.

**Hazard 2: `FAILING` is a dead end.** `rememberXAd` re-loads only from `NONE` or `DISMISSED` - it
does *nothing* from `FAILING` (confirmed in basic-ads 1.2.1 sources; the `when` has no branch for
it). Before preloading this was harmless: the handler was created on demand and its failure went
straight to `onFailure`. With preloading, one early failure leaves the handler dead for the rest of
the process, and a later request gets **no ad and no resolution** - a continue prompt the player can
neither accept nor dismiss. Every show site therefore has an explicit `AdState.FAILING ->` branch
that resolves the trigger exactly as a load failure did before.

**TEST ADS CANNOT REPRODUCE EITHER HAZARD.** Google's test units always fill, instantly, and never
fail, so both branches are unreachable while `USE_TEST_ADS = true`. They will look like dead code
in every local test run. Force them by hand - airplane mode is the easy one - before trusting them.

**The iOS interstitial is deliberately NOT preloaded.** `LevelExitBridge.ios.kt` is still a no-op
stub, so nothing on iOS ever calls `requestShow()`. Hoisting its handler would fetch an ad on every
composition, forever, for zero impressions - the exact pattern AdMob's invalid-traffic policy flags,
and it would wreck that unit's fill-rate reporting. Preload it when the Swift poll loop is wired,
not before; copy the Android version and its two hazard branches.

**The Store's `CoinsRewardAdHost` / `GadgetRewardAdHost` are NOT preloaded either.** They are gated
at the call site in `StoreScreen.kt` (`if (showCoinsRewardAd) { ... }`), so preloading them means
restructuring commonMain plus four platform actuals to take a "show now" flag. The placement is a
menu button where a brief wait is tolerable, so this was left alone on purpose rather than missed.

**Verified**: `:paywall-build:compileDebugKotlinAndroid` and `compileKotlinJvm` clean, `jvmTest`
green. **iOS not compile-verified** - `compileKotlinIosSimulatorArm64` reports `SKIPPED` on Windows
(Kotlin/Native iOS needs a Mac), so `ios-build.yml` in CI is the first real check of the iOS edit.
**Neither platform has been run on a device with preloading**, so the behaviour below is reasoned
from the basic-ads sources, not observed:
- ad appears immediately on level exit / continue prompt instead of after a fetch
- declining still hands control straight back (`consumeOutcomeFinished()` unchanged)
- reward still grants; on Android `markRewardEarned()` still does NOT resolve the outcome (see the
  grey-screen reload bug note in `ContinueAdBridge.android.kt` - preloading did not change it)

## All three ad placements currently point at Google's TEST ad unit IDs (2026-09-08)

`AdUnitIds.android.kt`/`AdUnitIds.ios.kt` each have a single `private const val USE_TEST_ADS = true`
at the top of the file, gating all three placements (`REWARDED_CONTINUE`/`REWARDED_COINS`/
`INTERSTITIAL_LEVEL_EXIT`) at once. **Deliberate, not a leftover**: Internal and Closed testing on
Play Console (and the iOS equivalent, TestFlight/App Store review) both count as
"developer-associated" traffic under AdMob's invalid-traffic policy — testers are people the
developer personally invited, not genuine public users — so real ad units must not be used there.
Real IDs are only safe once traffic is genuinely public: an **Open testing** track (public opt-in,
not personally invited) or a production release.

**This replaces the old per-value test-ID swap** (`REWARDED_CONTINUE` used to have its own ad-hoc
comment-and-swap from 2026-09-03, which is exactly the kind of thing that sat in place unnoticed for
a while — see git history). One flag per file is the fix: flipping both `USE_TEST_ADS` constants to
`false` before an Open testing/production build is a two-line change instead of six, and there's no
way to flip three of six values and miss the others. **Before shipping to Open testing or
production, grep for `USE_TEST_ADS = true` in both files and flip both** — this is not automatic.

## Watch ad for coins (Store) — real, separate from "watch ad to continue"

**Status update (2026-09-08): the "Store screen has no real purchase flow" and "PurchasesBridge is
still a stub" claims elsewhere in this file are now stale for the Store's billing path** — a real
`StoreBilling` bridge (`paywall-build/src/commonMain/kotlin/com/infiltrate/billing/StoreBilling.kt`,
`expect`/`actual`) calls `purchases-kmp-core:3.6.0`'s real `Purchases.sharedInstance.purchase(...)`
from `StoreScreen.kt`'s coin-pack purchases. **Android is wired** (`InfiltrateApplication.kt` calls
`StoreBilling.initialize(BuildConfig.REVENUECAT_GOOGLE_KEY)` with a real key from a gitignored
`local.properties`). **iOS is NOT wired** — `StoreBilling.ios.kt` exists and should work, but
nothing in `ios-shell/` calls `StoreBilling.initialize(...)`, so iOS purchases don't function yet.
On success, `profileStorage.addCoins(pack.amount)` still just credits a plain local integer —
RevenueCat validates the real-money transaction, it does not hold the spendable coin balance
(RevenueCat's separate Virtual Currency ledger feature is deliberately not used - see below for why).
The old `:game`-side `PurchasesBridge`/`src@ios/PurchasesBridge.ios.kt` stubs are unrelated and
still genuinely unused; this new path lives entirely in `paywall-build`'s Compose Store screen.
This file's other RevenueCat sections (the `:game`-side klib ABI history, the `paywall-build` link
spike) are still accurate and unaffected.

**Why the ledger itself was deliberately kept local rather than moved to RevenueCat's Virtual
Currency feature**: most coin grants are gameplay-driven (level completion, `GameplayScene.kt`),
not purchase-driven, and this project has no backend server. RevenueCat's virtual-currency model
expects non-purchase grants to come from your own server calling their API; without one, every
level-completion reward would need to become a network call, which is a bad trade for a
single-player game meant to work offline. The current split (RevenueCat validates the money side,
local storage owns the actual balance) is the right one for this project's shape.

**The "WATCH AD" coin pack is now a real ad, with a daily cap** (previously just an instant,
unlimited-use free-coins button - a real gap, not a design choice, found while wiring this up):
- New ad unit `AdUnitIds.REWARDED_COINS` — its own placement, deliberately not sharing
  `REWARDED_CONTINUE` (AdMob's own guidance is to give each placement its own ad unit for
  reporting/frequency-cap granularity; named "Coins Reward" in the AdMob console). Android
  `ca-app-pub-7912148730700666/8440619376`, iOS `ca-app-pub-7912148730700666/4233781051`. AdMob's
  own dashboard frequency cap is set to a looser backstop (10/day) above the in-app economy limit
  below, so it should never actually trigger in normal play - it exists only to bound a modified
  client spamming ad requests.
- **`CoinsAdLimiter`** (`paywall-build/src/commonMain/kotlin/CoinsAdLimiter.kt`) is the real
  economy limiter: `MAX_WATCHES_PER_DAY = 5` watches per (UTC epoch, not local-calendar) day,
  backed by the same `getRaw`/`setRaw` storage bridge `GameProfileStorage` uses, under keys
  `user_coin_ad_day_bucket`/`user_coin_ad_watch_count`. Reward is 250 coins/watch (`coins_ad`
  `CoinPackItem.amount` in `StoreScreen.kt`) — maxed out, 5×250 = 1250 coins/day free, a bit above
  the cheapest paid pack's value ($0.99/1000 coins) if watched every single day, kept as a
  deliberately real but bounded alternative rather than a substitute for buying. AdMob's own
  dashboard frequency cap on `REWARDED_COINS` should stay comfortably above 5/day (10/day was the
  value set) so it never triggers in normal play - it's a backstop against a modified client
  spamming ad requests, not the thing enforcing this limit. `StoreScreen.kt`'s coin-pack card
  shows "WATCH AD (N LEFT)" and disables to "COME BACK TOMORROW" once exhausted, rather than a
  dead button with no explanation. (These numbers - 250/5 - were the second iteration; if they
  change again, update this paragraph in place rather than appending a new dated entry, per this
  file's own "keep this up to date" policy.)
- **`CoinsRewardAdHost`** (`expect` in commonMain, `actual` per platform) hosts the real
  `RewardedAd` composable. This has to be `expect`/`actual`, not a plain commonMain composable,
  because `basic-ads` only publishes Android/iOS variants (no `jvm()` desktop artifact) — the same
  constraint `AdUnitIds`/`ContinueAdBridge` already work around. Unlike the gameplay "continue"
  flow, this needed no cross-framework poll bridge at all: the Store button tap and the ad request
  both happen inside the same Compose tree already (`StoreScreen.kt` composes `CoinsRewardAdHost`
  directly while a watch is requested, and its `onRewardEarned` calls `profileStorage.addCoins(...)`
  + `coinsAdLimiter.recordWatch()` directly) - no Swift/Android bridge object needed, since there's
  no KorGE/Compose boundary to cross for this particular placement.

**Verified**: `:paywall-build:compileKotlinJvm`, `:compileDebugKotlinAndroid`, `:jvmTest`, and
`android-shell`'s full `assembleDebug` (real APK) all succeed. **Not verified**: iOS klib
compilation currently fails locally with `onlyIf 'Cross compilation should be supported on host'
is false` — a Kotlin/Native toolchain gate, not a code error (the iOS actual is structurally
identical to the working Android one) - re-check via CI or a Mac before trusting it compiles; this
gate wasn't hit the last time this file recorded a successful local
`:paywall-build:compileKotlinIosSimulatorArm64`, so something in the Kotlin 2.3.20→2.4.10 bump (or
another environment change since) may have newly triggered it - worth investigating if any other
`paywall-build` iOS work hits the same wall. Never run on a real device/emulator.

## Watch ad for a random gadget (Store) — new, real, mirrors "watch ad for coins"

Sixth Store card, added to the existing 2x3 POWER-UPS grid (`StoreScreen.kt`) alongside the five
real gadgets (CAMERA JAMMER/SLEEP DARTS/INVISIBILITY CLOAK/NOISE SUPPRESSION BOOTS/REMOTE TRIGGER,
display names updated since this was written but the underlying `PowerupType` enum values and the
mystery pool are unchanged).
"MYSTERY GADGET" grants one of those same five, chosen with `List<PowerupType>.random()` (uniform,
Kotlin stdlib) at the moment the ad is requested — not at reward time — so the granted type is fixed
before the ad even shows. Grant path reuses `profileStorage.buyPowerup(type.id, cost = 0)` rather
than adding a separate free-grant method: `spendCoins(0)` always succeeds, so this is exactly
"buy for free" with no interface change.

**Not the same thing as `PowerupType.PROTOTYPE`** (`Powerup.kt`) — that's a separate, already
in-progress sixth *gadget type* (real id/cost/timer, no world effect yet, not wired into
`StoreScreen.kt` at all). This feature doesn't touch `PowerupType` or `GameProfile.kt` — it only
picks among the five existing real types. Don't conflate the two if PROTOTYPE gets its own Store
card later; `gadget_prototype.png` belongs to PROTOTYPE, not to this feature (this card draws a
vector five-pip die icon instead, `drawMysteryDiceIcon` in `StoreScreen.kt`, since it doesn't
represent one fixed gadget).

**New ad unit**: `AdUnitIds.REWARDED_GADGET`, its own placement (not shared with `REWARDED_COINS`
or `REWARDED_CONTINUE`, same reporting/frequency-cap reasoning as the other placements). Real IDs
now created in AdMob, named "Gadget Reward" (Android `ca-app-pub-7912148730700666/9048379643`,
iOS `ca-app-pub-7912148730700666/6397813920`), same app IDs as every other placement in this table.
`USE_TEST_ADS = true` still gates both to Google's shared test ID for now, same as the rest.

**`GadgetAdLimiter`** (`paywall-build/src/commonMain/kotlin/GadgetAdLimiter.kt`) is a separate class
from `CoinsAdLimiter`, not a shared generic base — same precedent as `InterstitialAdLimiter` already
being its own class despite the thematic overlap. Capped at `MAX_WATCHES_PER_DAY = 3` (lower than
coins' 5): a random gadget averages ~400 coins of value (the five cost 150-750 outright) versus the
coin card's flat 250, so an equal daily count would make this the more generous of the two — 3/day
was chosen to keep it a real but bounded alternative, not the better deal. Tunable in place if that
balance needs revisiting. Own storage keys (`user_gadget_ad_day_bucket`/`user_gadget_ad_watch_count`),
same UTC-day-bucket shape as `CoinsAdLimiter`.

**`GadgetRewardAdHost`** (`expect` in commonMain, `actual` per platform) is a near-identical copy of
`CoinsRewardAdHost` pointed at `AdUnitIds.REWARDED_GADGET` instead — kept as a separate small file
per placement rather than parameterizing one host with an ad-unit-id argument, matching this
project's existing one-file-per-placement convention for ad hosts/limiters/bridges.

**Verified**: `:paywall-build:compileKotlinJvm`, `:paywall-build:compileDebugKotlinAndroid`, both
`jvmTest` suites (root + `paywall-build`) all succeed. **Not verified**: iOS klib compilation
skipped locally (`onlyIf 'Cross compilation should be supported on host' is false`, the same
pre-existing Windows toolchain gate noted in the coins-ad section above, not a code error) — check
via CI before trusting it compiles. Never run on a real device/emulator/JVM desktop preview UI.

## Level-exit interstitial ads — Android real, iOS plumbing only

Third ad placement (after `REWARDED_CONTINUE`/`REWARDED_COINS`), triggered on leaving gameplay
back to the menu - the single choke point `LevelExitBridge.requestReturnToMenu()` already covers
(QUIT/RETURN TO MENU/MAIN MENU/ALL CLEAR). Note: NEXT LEVEL (level-complete → straight into the
next level, no menu) does NOT call `LevelExitBridge` and deliberately does not show an interstitial
- a full-screen ad on every single level win was ruled out as too aggressive; add it later as its
own decision if wanted.

**New ad unit**: `AdUnitIds.INTERSTITIAL_LEVEL_EXIT` (Android `ca-app-pub-7912148730700666/7390779081`,
iOS `ca-app-pub-7912148730700666/5874999932`, JVM falls back to Google's real published test
interstitial ID `ca-app-pub-3940256099942544/1033173712`, distinct from the test *rewarded* ID
already used elsewhere in this file - don't reuse one for the other, they're different ad formats).

**Gating (`InterstitialAdLimiter`, `paywall-build/src/commonMain/kotlin/InterstitialAdLimiter.kt`)** -
all three conditions must hold:
- `totalLevelsCompleted >= 2` (new `GameProfile.totalLevelsCompleted: Int`, persisted key
  `user_total_levels_completed`) - incremented once per *distinct* level, in `GameplayScene.kt`'s
  `world.onLevelComplete`, by checking `levelStorage.getBestResult(result.levelId)?.completed`
  *before* `saveResult()` overwrites it. Deliberately not a per-play counter - replaying level 1
  for stars doesn't count toward the gate, only genuinely new levels do. Rationale: level 1 is this
  project's tutorial (`level-1-tutorial-system.md`), and level 2 gives one more no-ads level to
  build a habit before any monetization interruption.
- `!profile.isPremium` - respects the existing Remove Ads purchase, which already promises "removes
  all banner and interstitial advertisements" (`StoreScreen.kt`'s Remove Ads perk copy) - this is
  the first placement that promise actually needed to be true for.
- `InterstitialAdLimiter`'s own in-memory state: 180s cooldown seeded from app launch (not
  persisted - a fresh cold start always gets a 3-minute grace before the first possible
  interstitial, using the exact same constant as the steady-state cooldown rather than a second
  one) and a 5-per-session cap (also in-memory, resets on cold launch - unlike `CoinsAdLimiter`'s
  persisted daily bucket, "session" here really does mean "this process's lifetime").
- AdMob's own dashboard frequency cap on this ad unit is a looser backstop (30/hour) above the
  ~20/hour theoretical ceiling the 180s cooldown alone would allow - same "backstop, not the real
  mechanism" role as the coin-ad section above. The owner deliberately chose not to enable AdMob's
  dashboard cap for this unit at all (accepted trade-off: only a modified client bypassing the
  in-app checks could hit an uncapped rate, everything else is unaffected).

**Trigger shape - NOT `expect`/`actual` (different from `CoinsRewardAdHost`), same as
`ContinueAdBridge`**: `InterstitialAdTrigger`/`InterstitialAdContent()` are plain, separately-defined
per-platform files (`paywall-build/src/androidMain/kotlin/InterstitialAdBridge.kt` and
`.../iosMain/kotlin/InterstitialAdBridge.kt`), not a shared `commonMain` declaration - because
(unlike the Store's coin-ad flow) this is only ever invoked from platform-native host code
(`android-shell/MainActivity.kt`; eventually Swift on iOS), never from a shared Compose screen, so
there's nothing in `commonMain` that needs to resolve against it. Uses the real `basic-ads` API
(verified by extracting `basic-ads-1.2.1-sources.jar` from the Gradle cache and reading it
directly, not guessed): `InterstitialAd(adUnitId, onDismissed, onShown, onImpression, onClick,
onFailure, onLoad)` is a load-then-show composable, same shape as `RewardedAd` - composed only
while `showRequested` is true, same pattern as `ContinueAdContent`/`CoinsRewardAdHost`. `basic-ads`
also exposes a preload-ahead-of-time variant (`rememberInterstitialAd()` +
`InterstitialAd(loadedAd = handler, ...)`) that was deliberately NOT used here, for consistency
with every other ad flow in this project (all load-on-demand) - preloading to avoid the load-latency
gap between tapping QUIT and the ad appearing is a real, available optimization if that gap ever
becomes a complaint, just not implemented yet.

**`MainActivity.kt`'s `AndroidLevelExitBridgeState.onReturnToMenuRequested`** now does two things
instead of one: flips `showingGameplay.value = false` (unchanged) and separately calls
`maybeShowLevelExitInterstitial()`, which checks the limiter and, if eligible, calls
`InterstitialAdTrigger.requestShow()` - same "don't gate the menu-flip on the ad" approach
`showContinueAd()` already uses, letting the ad's own full-screen Activity cover whatever's already
on screen once it loads, rather than blocking navigation on it.

**iOS: wired (2026-09-12), was plumbing-only before.** `GameLevelExitBridge` (a real
`@ObjCName(exact = true)`-exported object added to `src@ios/LevelExitBridge.ios.kt`, same shape as
`GameContinueAdBridge`) replaces what used to be a true no-op `IosLevelExitBridge.
requestReturnToMenu()` - meaning QUIT/RETURN TO MENU/MAIN MENU/ALL CLEAR previously compiled and
ran on iOS but silently did nothing at all (not even returning to the menu), a bigger gap than just
"no ad." `AppDelegate.swift`'s `startObservingLevelEnd()` timer now polls
`GameLevelExitBridge.shared.consumeReturnToMenuRequest()` alongside its existing `SpikeBridge`/
`GameContinueAdBridge` checks, switches to Compose, then calls a new
`InterstitialAdTrigger.maybeRequestShow(totalLevelsCompleted:isPremium:)` (added to
`InterstitialAdBridge.kt`) that checks `InterstitialAdLimiter` itself - mirroring
`MainActivity.kt`'s `maybeShowLevelExitInterstitial()` exactly rather than duplicating its
constants in Swift. The profile fields it needs came from a new `DebugStorageBridge.
readTotalLevelsCompletedForDebug()` (existing `readIsPremiumForDebug()` already covered the other
half). `InterstitialAdContent()` is now also composed in `MainMenuComposeViewController.kt`
(previously missing entirely - Android's `InterstitialAdContent()` was composed, iOS's never was).
**Not yet verified in CI or on a real device/simulator** - this is all iOS-only source, so it
cannot be compile-checked from Windows at all (see the `compileKotlinIosSimulatorArm64`-on-Windows
trap elsewhere in this file); push and check `ios-build.yml`.

**Verified**: `:compileKotlinJvm`, `:paywall-build:compileKotlinJvm`,
`:paywall-build:compileDebugKotlinAndroid`, `jvmTest` (both suites), `:paywall-build:publishToMavenLocal`,
and `android-shell`'s full `assembleDebug` (real APK) all succeed. **Not verified**: never run on a
real device/emulator - whether the interstitial actually shows at the right moment, respects the
level-2 gate and cooldown correctly in practice, and whether `!profile.isPremium` truly suppresses
it for a Remove Ads purchaser are all unconfirmed until a real device test. iOS not attempted beyond
compiling the plumbing above.

## Shared storage bridge: `paywall-build` ↔ `:game`

Read directly from KorGE 6.0.0's own source/decompiled bytecode (not
assumed):
- **iOS/darwin** (`DarwinNativeStorage`): `NSUserDefaults(suiteName = "korge")`
  — a **named suite**, not `.standardUserDefaults`. Every key is prefixed
  `"org.korge.storage."`. A named suite with no App Group entitlement
  resolves to the same on-disk plist for any code in the same app
  sandbox/process, so once `PaywallModule.framework` is embedded in `:game`'s
  own app target (still true today — see "Native iOS shell" below), no App
  Group is needed.
- **Android** (`NativeStorage`): `context.getSharedPreferences("KorgeNativeStorage", MODE_PRIVATE)`,
  **unprefixed** keys.

`paywall-build/src/iosMain/kotlin/PaywallStorage.kt` implements the iOS side
(`getRaw`/`setRaw` matching `MapBackedGameProfileStorage`'s contract exactly);
`KorgeStorageKey.kt` is a pure, JVM-testable `iosKey()` helper replicating
the same transform. Verified two ways: a JVM `StorageKeyCompatibilityTest`
(4/4 pass — proves both sides' key-prefixing logic agree), and later a real
**on-device round-trip** inside `ios-shell/` (`Storage bridge result: OK`,
read straight from a live simulator run — see next section). Current
persisted keys: `user_coins`, `user_is_premium`, `user_music_vol`,
`user_sfx_vol`, `user_controls_swapped`, `user_language`,
`user_unlocked_levels`, `user_powerups`, `level_result_<levelId>`.
(An earlier note flagged `GameProfile.powerupInventory`/`user_powerups` as
unpersisted; a later listing includes it as a real key — this was never
explicitly reconciled, so verify current `MapBackedGameProfileStorage`
behavior directly before assuming either way.)

**Android has no `paywall-build` storage implementation** — deliberate,
confirmed with the owner: `:game` already talks to Android storage directly,
so there's no cross-framework boundary to bridge there. If one is ever
needed, target the same `SharedPreferences("KorgeNativeStorage", MODE_PRIVATE)`,
unprefixed.

## Native iOS shell (`ios-shell/`) — WORKING, verified on real Simulator CI

The first attempt to run `:game`'s `GameMain.framework` and `paywall-build`'s
`PaywallModule.framework` **in the same process**, via a hand-authored
XcodeGen project (`ios-shell/project.yml`) embedding both. Confirmed from
raw CI logs (run `33385051973`): clean link/codesign, `Storage bridge
result: OK` written by the app itself after a real
`PaywallModule`-writes/`GameMain`-reads round trip.

**Why the trigger lives in Swift, not a KorGE scene**: `:game` (Kotlin
2.0.20) and `paywall-build` (Kotlin 2.4.10) produce ABI-incompatible klibs —
`:game`'s Kotlin cannot call into `PaywallModule`'s Kotlin API directly. The
round-trip trigger lives in native Swift instead, which can call both
frameworks' exported Objective-C APIs with no coupling between the two
Kotlin builds. This is the same reason the AdMob/watch-ad bridges above are
poll-based rather than direct calls.

**Two resolved risks — settled, don't re-litigate without a new reason:**
1. *Duplicate Kotlin/Native runtime symbols* (each framework embeds its own
   runtime) — tested for real, did not occur for this pair. If a *third*
   Kotlin/Native framework is ever added to this app, re-verify.
2. *`@ObjCName(name = "X")` without `exact = true` doesn't override the
   framework-name-prefixed export* — compiles fine, but the linked ObjC
   class symbol keeps the framework prefix, causing undefined-symbol link
   errors from Swift. Fix: `@ObjCName(name = "X", exact = true)` +
   `@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)`. Apply
   this to any future Kotlin/Native object exported for cross-framework/Swift use.

Also needed: `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` in
`ios-shell/project.yml` — `xcodebuild -sdk iphonesimulator` with no
`-destination` defaults to building both arm64 and x86_64 slices, but the
embedded Kotlin/Native frameworks are arm64-only.

**Landscape lock added (2026-09-12), root-caused via a real limrun.com simulator screenshot.**
`ios-shell/project.yml`'s Info.plist had no `UISupportedInterfaceOrientations` key at all, so it
fell back to Xcode's default (portrait allowed) - every Compose menu screen is laid out assuming a
landscape-wide canvas (`StoreScreen.kt`'s `scale = screenHeight / 720.dp` assumes 720 is the SHORT
dimension), so portrait rendered every screen squished into a narrow column, text wrapping
character-by-character. Fixed by adding `UISupportedInterfaceOrientations`/`~ipad` (both landscape
only) to `project.yml`, matching `android-shell/AndroidManifest.xml`'s existing
`android:screenOrientation="landscape"`. **Not yet re-verified in CI/on-device** - push and check.

**A second, unrelated real bug found from the same screenshot report ("back button in Store/
Settings doesn't work"): `AppDelegate.swift`'s `addDebugOverlay()` was silently eating the taps.**
It added a real, always-interactive `UIButton` ("Storage Bridge Check", frame `(12, 44, 220, 36)`)
and a `UILabel` directly to the `UIWindow` itself (`window.addSubview(...)`, not to any specific
view controller's view) from `didFinishLaunchingWithOptions`, with nothing anywhere ever removing
it - so it sat on top of *every* screen (MainMenu, Store, Settings, gameplay) for the app's entire
life, since window-level subviews stack above `rootViewController.view` regardless of when they're
added. Its frame directly overlaps `MenuTopBar`'s back button (top-left, every screen, same
region) - taps meant for the real back button were landing on this invisible-in-intent debug
button instead. Removed outright (the button, its `@objc` target, and the `resultLabel` property/
updates) rather than repositioned: CI never actually reads it (it polls
`storage_bridge_result.txt` from disk via `simctl get_app_container`, written by
`runStorageBridgeCheck()` regardless of whether the button exists), so it was purely a manual-QA
convenience that outlived its usefulness once the automated write-to-file path was proven out. If
a manual on-device re-trigger is ever needed again, don't reintroduce it as an unremoved
window-level subview - gate it behind a debug build flag or at minimum give it a frame nowhere
near the top-left corner every menu screen's back button lives in.

**Still open**: `:game`'s own `compileKotlinIosSimulatorArm64` is disabled
by a KorGE-plugin-specific gate on this Windows machine (root cause not
traced) — any iOS-only `:game` change can only be compile-checked via CI,
never locally. Only `iosSimulatorArm64` proven — `iosArm64` (real device)
untouched for both frameworks. `ios-shell/` is a standalone proof-of-concept,
not wired into any release/distribution pipeline, and separate from KorGE's
own generated `build/platforms/ios` — no decision made on whether/how these
converge for a real shipping app.

## Compose/KorGE view-switching architecture — PROVEN VIABLE on real iOS Simulator CI

Tests the architecture where KorGE is shown ONLY during actual gameplay and
Compose Multiplatform owns everything else, reusing `ios-shell/`'s
`GameMain.framework` + `PaywallModule.framework` embedding. **Status:
viable, build the real menu/store/gameplay architecture around this.**

**The "build the real architecture around this" step was never actually done - FIXED
2026-09-12, found from a real limrun.com screenshot report ("game doesn't load, I just get a
purple debug screen").** `ShellAppDelegate.ios.kt` was still wired to `spikeMain()` (this
section's own `SwitchSpikeScene` debug scene - purple background, a "ticks: N" counter, an
"END LEVEL (debug)" button) instead of the real game, with a comment literally saying "Revert to
`{ main() }` once the spike is done" that nobody had come back to. Every real level launch on iOS
showed this debug scene, not gameplay - the spike itself was real and correct, the revert step
afterward just never happened.

**Fixing the revert surfaced a second, deeper gap**: commonMain's own `main()` (`src/main.kt`)
only ever picks ONE level, once, from `Environment["startLevel"]`/`args.firstOrNull()` - fine for
JVM desktop dev, but iOS never sets either, so a bare `{ main() }` would always load
`DEFAULT_LEVEL_1` regardless of which level the player actually tapped in the Compose
`LevelSelectScreen`. `android-shell/MainActivity.kt` already solved the identical problem for
Android with its own `activeSceneContainer` var (capture the `SceneContainer` once, `sc.changeTo`
it again on every subsequent level pick, without reloading the whole KorGE module) - `GameEntry.
ios.kt` (new file) does the same for iOS: `gameMain()` replaces `spikeMain()` as
`ShellAppDelegate.ios.kt`'s entry, and `GameLevelStartBridge` (`@ObjCName(exact = true)`, same
export convention as every other Swift-visible bridge in this file) captures the `SceneContainer`
and exposes `startLevel(levelId:)` for Swift to call. `AppDelegate.swift` now uses
`MainMenuComposeScreen`'s level-aware `makeViewController` overload (it already existed,
unused - the no-arg overload was the one actually wired) and calls
`GameLevelStartBridge.shared.startLevel(levelId:)` right before `switchToKorGE()`. **Not yet
verified in CI or on-device** - push and check; in particular, whether picking a second, different
level after already having played one correctly re-targets the scene has not been observed, only
reasoned from mirroring Android's proven mechanism.

**A third, unrelated bug found from the same report ("parts of the screen blocked by that thing at
the top")**: nothing on iOS ever hid the system status bar - `android-shell/MainActivity.kt` calls
`hideSystemBars()` for exactly this reason (a fullscreen game with its own edge-to-edge top HUD/
menu bar), but iOS had no equivalent, so the status bar (clock/battery/signal, plus the Dynamic
Island cutout on newer models) rendered on top of and overlapped that content. Fixed with
`UIStatusBarHidden: true` + `UIViewControllerBasedStatusBarAppearance: false` in
`ios-shell/project.yml`'s Info.plist (the latter is needed because it defaults to `true`, which
would make the global key get ignored in favor of each `UIViewController`'s own
`prefersStatusBarHidden` - neither Compose's `ComposeUIViewController` nor KorGE's own view
controller override that, so without this the global key does nothing). Not yet re-verified either.

**Method**: a debug KorGE scene increments a per-frame counter
(`SpikeBridge.frameTicks`) regardless of UIKit visibility; native Swift
swaps `window.rootViewController` between a Compose screen and the
already-warm KorGE `ViewController` 6 times in one automated app run,
polling the counter and sampling memory each cycle.

**Real, measured results** (two clean CI runs, cross-run variance is
Simulator noise):
- Switch-to-KorGE latency: consistently well under 500ms cold, 60-120ms warm
  — no loading spinner needed for the "continue after ad" re-entry; a brief
  fade on the very first level entry is a reasonable hedge against the cold case.
- **`hiddenDwellTicksAdvanced` = 0, every cycle, both runs.** KorGE's render
  loop genuinely stops producing frames once its view leaves the window via
  a plain `window.rootViewController =` swap — no manual pause/resume
  plumbing needed. This directly answers the battery-drain question the
  spike was built to answer (on iOS specifically — see the Android
  counterpart caveat under "Real device bugs" below, where the equivalent
  is NOT true).
- Memory fluctuated without a monotonic growth trend across 6 cycles (not
  proof of no leak — a small sample).

**Load-bearing gotcha found along the way**: Compose Multiplatform's
`PlistSanityCheck` dispatches a one-time low-priority background check that
**hard-aborts the process** (`SIGABRT`) if `Info.plist` doesn't set
`CADisableMinimumFrameDurationOnPhone: true`. This crash is deterministic
but delayed (fires on a low-priority queue, so a CI step that exits fast
enough can miss it and look clean) — **any Compose-on-iOS work in this repo
needs this key regardless of architecture**, already set in
`ios-shell/project.yml`; if the real Compose menu UI ever moves to a
different Xcode project, that project's `Info.plist` needs it too.

**Still open**: visual flash/glitch check is inconclusive (screenshots never
happened to land inside the ~100-500ms KorGE-visible window in the one run
attempted). Only tested on `iosSimulatorArm64`. `ShellAppDelegate.ios.kt`'s
entry point must point at the real `main()`, not the throwaway `spikeMain()`,
before/while building the real menu integration — check which one it
currently points to before assuming this is wired to production code.

## `korge-video`: NOT VIABLE — do not build the menu video background around it

Tested and fully reverted (throwaway spike, no trace left in the tree).
[korlibs/korge-video](https://github.com/korlibs/korge-video) is stale (0
stars, last real commit 2023), doesn't even compile against this project's
locked KorGE 6.0.0 (renamed/removed korau/korio APIs), and its iOS backend
is a literal empty stub that silently falls back to a fake generated video
— no real MP4 decoding exists for iOS at all. Real decoding only exists for
JVM/Android/JS. **If video playback is still wanted**, re-encode as a
low-fps PNG/JPEG frame sequence through KorGE's normal `Bitmap`/`Animation`
APIs, or a sprite-sheet — both proven, no codec dependency.

## Non-gameplay UI migration to Compose Multiplatform

**Status: IN PROGRESS.** MainMenu is ported to Compose (`paywall-build`);
LevelSelect/Store/Settings exist as real Compose screens too (see "Real
device bugs" below for fixes applied to them). KorGE (`:game`) is
gameplay-only, entered via a `window.rootViewController` swap on iOS
(warm-swap candidate on Android — see Android architecture note below).

- **Model sharing across composite builds**: a plain Gradle subproject
  dependency doesn't work across the Kotlin-version boundary (same
  classpath-conflict issue documented under RevenueCat above). Instead,
  `paywall-build/build.gradle.kts` adds `kotlin.srcDir("../src/game/model")`
  and compiles `GameProfile.kt`/`LevelData.kt`/`Geometry.kt`/`Powerup.kt`
  etc. directly from source, alongside `:game`. **Standing constraint**: every
  file under `src/game/model/` must stay pure Kotlin (stdlib only, zero
  `korlibs.*` imports) so it compiles under both Kotlin 2.0.20 and 2.4.10 —
  enforced by an automated test, `ZeroKorlibsLintTest`.
- **Android warm-engine architecture is an unspiked candidate, not built**:
  a single Activity with a parent `FrameLayout` holding both `ComposeView`
  and KorGE's surface view, toggling visibility to pause rendering without
  destroying the engine — analogous to the iOS `rootViewController` swap.
  (What's actually shipped on Android today does NOT do this — see "Real
  device bugs" below, where toggling visibility turned out to tear down the
  render surface. The Compose menu instead draws opaquely on top of an
  always-visible KorGE view.)
- Entry-point rule: `src/main.kt` must always expose a parameterless
  `suspend fun main() = main(emptyArray())` (KorGE's iOS bootstrap calls it
  with zero args) alongside `suspend fun main(args: Array<String>)`. Never
  use JVM-only APIs (`java.lang.System.getProperty`) in common code — use
  `korlibs.io.lang.Environment["key"]` or `args.firstOrNull()`.

## Gameplay Architecture (`commonMain`)

Gameplay logic is decoupled from the rendering engine:
- `game.model`: engine-agnostic domain layer, pure data models and
  simulation math — `Geometry.kt` (raycasting, line-of-sight), `Player.kt`
  (physics: 96×50 hitbox, jump/gravity/platform snapping, sub-stepped AABB
  collision, `NoiseLevel`, crouch stance), `Guard.kt` (waypoint patrol,
  `GuardState` PATROL/INVESTIGATING), `Vision.kt` (FOV polygon + detection),
  `LevelData.kt` (`LevelData`, `LevelResult`, `LevelRegistry`,
  `LevelStorage`), `GameProfile.kt` (coins/premium/volumes/unlocks/powerups
  + storage interfaces), `GameWorld.kt` (orchestrates all of the above,
  detection/alert/noise logic, exit/win condition).
- `game.scene`: KorGE presentation layer — `UiComponents.kt` (shared UI
  constants + GPU vector-draw helpers), `PlayerAnimations.kt` (sprite-sheet
  animation), `GameplayScene.kt` (the core scene: HUD, touch controls,
  overlays, parallax background, level rendering — see "Level 1 geometry"
  and "Real device bugs" below for its current tuned state). All non-gameplay
  screens (MainMenu, LevelSelect, Store, Settings, Paywall) are fully
  delegated to Compose Multiplatform, not KorGE.

## End-of-run dossier sheets (MISSION FAILED / HEIST COMPLETE)

Both end-of-run screens in `GameplayScene.kt` are one design with two fills of
content: **the main menu's briefing sheet**. `dossier_paper.png` stretched to a
card box, the debrief printed on it in ink, the verdict struck across it as a
rubber stamp, and the menu's torn-paper strips stacked in a column to its left —
which is the main menu's own composition (strip column left, sheet right).

- MISSION FAILED → the sheet is a **SITUATION REPORT**: two columns of fields
  (status / alerts / time elapsed / record / coins on hand), a red MISSION FAILED
  stamp beside them, and the recon tip in the **handwritten face** along the foot,
  where a pencilled margin note belongs.
- HEIST COMPLETE → **OBJECTIVE REVIEW**: three stars struck at the head, each
  over its own objective field, a green HEIST COMPLETE stamp carrying the rating
  as its second line, and the purse written out along the foot.

An earlier pass built these as dark `#141416` cards with hairline borders, wells
and a coin pill, borrowed from the Compose store/missions screens. **The owner
rejected it as off-theme, and they were right**: those screens are the game's
chrome, the sheet is its identity, and a heist debrief is exactly the thing that
belongs on paper. Don't reintroduce dark panels, hairline strokes or the coin
pill here. (That pass also added a `COLOR_MENU_*` palette and a vector
`drawCoinIcon` to `UiComponents.kt`; both were removed with it — if a future
KorGE screen really does need to match a Compose *panel*, they are in this
session's history rather than sitting unused in the file.)

Everything ink-side is `MainMenuScreen.MissionDossierCard`'s: the ink alphas
(1.0 / 0.78 / 0.55 / 0.34 of `#17140F`), the sheet's **1.5 aspect** (never
stretch it on one axis — that pulls the torn edge), the content insets as
fractions of the sheet (start 15% to clear the folder tab, end 9%, top 5.5%,
bottom 14% because the tilt drops the last line), and the **-5.2° tilt** that
squares the type to the paper rather than to the screen. Stamp red/green
(`#96222A` / `#25603A`) and the aged gold (`#A8781A`) are new and deliberately
NOT the menu's `#FF2A55` / `#00E676` / `#FFD700` — those are tuned to glow on
near-black and read as highlighter on paper.

**The one non-obvious trap, found on screen and not predicted:** a tilted sheet
cannot carry a label-left / value-flush-right row. At 5.2° the value rises
`tan(5.2°)` per unit of x, so across a form-width column it climbs nearly a full
row and starts reading as the answer to the line *above* it — dot leaders and
all. Fields are **stacked** instead (small label with the value under it, both on
one x), so the tilt carries the pair together. Anything new on these sheets that
spans horizontally has the same problem; keep it stacked, or keep it to a single
line where there is no neighbouring row to confuse it with.

Layout knobs, all near the top of that block: `sheetH0`/`sheetW0` size the sheet
from the canvas height, and `S` shrinks the whole group (sheet, strips and every
type size together) if the pair overruns a narrow canvas. `S` is 1.0 on the
1040x480 virtual canvas both `main.kt` and `MainActivity.kt` use. `dpx`/`dpy`
convert page coordinates to the tilted layer, which is pivoted on the sheet's
centre because Korge rotates a view about its own origin.

`resources/dossier_paper.png` is a **second checked-in copy** of the Compose
menu's own asset, for the same reason the button strips are duplicated: Korge
reads `resources/`, Compose reads its `composeResources/`, and the two builds
share no asset pipeline. Adding it bumped `totalLoadSteps` to 20 — that constant
is the loading bar's denominator and has to match the number of
`markLoadProgress()` calls.

**Verified on a real running app** (JVM desktop, both sheets screenshotted and
laid out over four passes from those screenshots) plus `jvmTest` green — not on
Android or iOS. Per this file's rule #1 that is stronger than the usual
compile-only, but the desktop canvas is 1040x480 and a phone's is not.

**How the screenshots were taken** (reusable — this project leans on real
screenshots repeatedly): temporarily call `world.onGameOver?.invoke()` /
`world.onLevelComplete?.invoke()` just before the `addUpdater` block, run
`./gradlew runJvm`, and capture the window from PowerShell. Note
`GameWorld.spottedCount`/`timeTaken` have **private setters** — a preview can
invoke the callbacks but cannot stage their values, so a previewed sheet shows
zeros and `EXTRACTION: MISSED`. The capture must call `SetProcessDPIAware()`
before `GetWindowRect`/`CopyFromScreen`, or on this machine's scaled display it
silently grabs only the top-left ~80% of the window and looks like a layout bug
that is not there.

## Audio

Two independent audio systems sharing no code:
1. **Gameplay (`:game`, KorGE)** — `GameAudio.kt`, loads `sfx/*.wav` via
   `resourcesVfs` (out of `resources/sfx/`). A missing clip is a silent
   no-op, never a crash — check the file actually reached the bundle before
   suspecting code when a sound "doesn't work."
2. **Menus (`paywall-build`, Compose)** — `ui/MenuSfx.kt`, `expect`/`actual`
   per platform (4-voice `AVAudioPlayer` pool on iOS, `SoundPool` on
   Android, JavaFX `AudioClip` on desktop — all overlap-capable, since one
   restarted player would cut the previous tap off mid-sound).

**Format: PCM s16le / 44.1kHz / mono WAV only** — the one format all four
consumers (KorGE, `AVAudioPlayer`, `SoundPool`, JavaFX) accept. iOS and
JavaFX cannot decode Ogg Vorbis at all, so any CC0 pack downloaded as `.ogg`
must be converted first. Shared clips (e.g. `ui_click.wav`) are checked in
twice: `resources/sfx/` (KorGE + desktop) and `ios-shell/Resources/` (iOS
menu bus, via `NSBundle`) — Android's menu bus reads from
`assets/sfx/…`, where KorGE's Gradle plugin already copies `resources/`.

**KNOWN GAP, unverified on-device**: `ios-shell/project.yml` copies only
`ios-shell/Resources` and `paywall-build`'s compose-resources — there is no
path for the repo's `resources/` directory into the iOS shell's app bundle.
Every gameplay asset (sprites, backgrounds, font, all of `sfx/`) loads
through `resourcesVfs`, so on the iOS shell build gameplay audio and art are
expected to be missing (degrades silently, since `GameplayScene` catches
each load and falls back to `null`/vector drawing — probably why this has
gone unnoticed). The likely fix is one more folder reference in
`project.yml`; deliberately not applied without the owner's go-ahead since
that file is part of the hard-won working iOS config.

Sound credits are tracked in `SOUND_CREDITS` (`SettingsScreen.kt`'s Credits
& Licenses list) kept in sync with `ATTRIBUTION.md` by hand.

## Android gameplay audio "static" — RESOLVED 2026-09-11, real device (Galaxy S25 Ultra)

**Symptom**: occasional brief crackle during gameplay on Android, reported as tied to specific
moments (footstep/landing transitions, pause, the level-3 swing) rather than continuous. Confirmed
real via a captured `adb logcat` and, later, decisively via a screen recording with audio - its
extracted waveform (analysed with a small `numpy` script, `ffmpeg`/`ffprobe` for decode) showed
every anomalous moment lining up with an actual game-state transition, including one instance that
reproduced **starting the phone's own screen recorder while sitting in the main menu** - a system
action with zero game code involved, which is what proved this was not a specific SFX/API misuse.

**Root cause**: korlibs' own `Sound.play()` (`AndroidNativeSoundProvider`, decompiled from the real
jar in the Gradle cache to confirm rather than guess) constructs a **brand-new `android.media.
AudioTrack` on every single call**. Gameplay was therefore opening a new audio session for every
footstep/landing/click while `bgmusic.mp3`'s own continuous `AudioTrack` was already playing. On
most devices this is harmless; on this Galaxy S25 Ultra specifically, the phone's own "Voice
Booster" DSP effect chain re-initializes on every such session-open/reconfigure event, audible as
the reported crackle. Confirmed device-specific to *this app's* audio shape, not a blanket
device/DSP quirk, because it never reproduces in any other game on the same phone.

**Four narrower fixes were tried first, each real but insufficient** (kept here so the same ground
isn't re-covered blind if this regresses on a different device):
1. Pooling gameplay SFX through Android `SoundPool` instead of korlibs' per-call path - measurably
   reduced frequency, did not close it. A follow-up logcat capture showed why: `SoundPool` itself
   requests Android's low-latency "fast" output path per play (`AudioFlinger: createTrack_l():
   mismatch between requested flags (00000004) and output flags (00000000)`, at footstep cadence),
   which this phone's Voice Booster chain can't grant - `SoundPool` has no public API to refuse it.
2. A hand-rolled `MODE_STATIC` `AudioTrack` pool (`AudioTrack.Builder().setPerformanceMode(
   PERFORMANCE_MODE_NONE)`, explicitly refusing the fast path) - real fix for the flag-mismatch
   mechanism specifically, still didn't close the crackle.
3. Two "keep the output warm" attempts (a periodic near-silent `SoundPool` ping, then a
   continuously looping real-silence stream) - both reverted. Neither touched the remaining
   crackle, and the second introduced a real regression (no gameplay-only lifecycle, so it kept
   running into the main menu, heard as bgmusic bleeding through after quitting).
4. Matching korlibs' own `AudioAttributes` (`USAGE_GAME` + `CONTENT_TYPE_UNKNOWN`, not
   `CONTENT_TYPE_SONIFICATION`) and **joining korlibs' own shared audio session** via its public
   `AndroidNativeSoundProvider.audioSessionId`/`ensureAudioManager()` API (confirmed accessible,
   not internal, by compiling against it) instead of each `AudioTrack.Builder()` auto-generating
   its own - a real, confirmed difference from korlibs' setup, still didn't close it.

**The actual fix**: stop opening multiple audio sessions at all. `GameSfxOutput` (see below) is now
a from-scratch software mixer - **one** `AudioTrack` (`MODE_STREAM`, `PERFORMANCE_MODE_NONE` where
available, joined to korlibs' session id as a defensive fallback) opened once for the process's
whole life, fed by one dedicated thread (`THREAD_PRIORITY_URGENT_AUDIO`) that sums PCM samples from
a list of active "voices" (bgmusic + every in-flight one-shot) into a small buffer (20ms/882 frames)
every iteration and blocking-writes it. This is the same shape real engines use on Android (Unity,
Unreal, FMOD/Wwise all mix in software down to one hardware stream) - the four fixes above were all
still opening a separate stream per concept; this is the first version where there is structurally
only one, so there is nothing left for a device's DSP chain to reconcile.

**Files**: `src/GameSfxOutput.kt` (common `expect`/interface - `prepare`/`play` for one-shots,
`prepareMusic`/`setMusicVolume`/`stopMusic` for background music), real implementation in
`android-shell/src/main/kotlin/com/infiltrate/androidshell/GameSfxOutput.kt` (the one that ships)
and mirrored in `src@android/GameSfxOutput.android.kt` (for `:game`'s own KMP Android target, which
nothing currently ships from - see "TRAP - do not use the root build to check Android compilation"
below). Every other platform's `getGameSfxOutput()` returns `null`; `GameAudio.kt`'s `playSfx` and
the new music façade (`startNativeMusic`/`setNativeMusicVolume`/`stopNativeMusic`) fall straight
back to korlibs' original per-call `Sound.play()`/`playForever()` path whenever the native one is
unavailable or fails - additive only, iOS/JVM/JS/wasmJs are unaffected.

**`bgmusic.mp3` decode**: real `MediaExtractor`/`MediaCodec` decode-to-PCM, run once at
`prepareMusic()` time and cached (confirmed 44.1kHz stereo via `ffprobe`, not assumed) - the mixer's
whole output format is fixed at 44.1kHz stereo to match it exactly, so mono SFX (also 44.1kHz, this
project's existing WAV convention) are just upmixed into both channels with no resampling anywhere.

**A real, separate regression found and fixed along the way**: the mixer thread/`AudioTrack` has no
lifecycle awareness on its own and ran forever once started - without a fix, gameplay audio kept
playing after leaving the app entirely (not just returning to the in-app menu, which already goes
through `stopBgMusic()`/`GameAudio.stopNativeMusic()`). Fixed with `AudioTrack.pause()`/`play()` on
`MainActivity`'s `onPause`/`onResume` (`AndroidGameSfxOutputState.pauseEngine()`/`resumeEngine()`,
routed through a small `PausableAudioEngine` interface since a public property can't hold a
reference to the file-private engine class directly) - `pause()` alone is enough, since the mixer
thread's blocking `write()` call simply stops draining and blocks once the `AudioTrack`'s internal
buffer fills, with no separate thread-suspend logic needed.

**Two more real, separate bugs found and fixed during this investigation, unrelated to the audio
backend itself**:
- A background `GameplayScene` instance that QUIT/RETURN TO MENU reloads (to have a clean state
  ready behind the Compose menu - see "Compose/KorGE view-switching architecture" below for why a
  fresh instance exists at all) was unconditionally starting bgmusic in `sceneMain()`'s setup,
  even though it's never actually shown - reported as "menu music playing after quitting". Fixed
  with a `startDormant: Boolean` constructor flag (true only for those four reload call sites) that
  short-circuits `syncBgMusicVolume()` entirely.
- The on-screen D-pad/jump/crouch/interact touch controls had a quiet click wired in
  (`HUD_TAP_GAIN`) that had gone unnoticed for a long time because korlibs' per-call `AudioTrack`
  latency was largely swallowing it; switching to a pooled/always-ready output made it play
  cleanly and audibly for the first time, surfacing it as a new-seeming complaint. Removed outright
  (not just lowered) per explicit owner feedback - deliberate presses (pause, menu strips, Mission
  Failed buttons) still click, the movement/action HUD does not. Don't re-add a tap sound to
  `createTouchBtn`/`createImgBtn` without the owner asking again.

**How the actual click was pinpointed, worth reusing if a similarly vague audio/visual bug ever
comes up again**: `adb logcat` correlation got partway there but was inconclusive on its own; what
actually nailed it down was a phone screen recording (with audio) of a repro, its audio track pulled
out with `ffmpeg`, and a short `numpy` script scoring short-time high-frequency energy per 20ms
window to rank click candidates - then pulling video frames at those exact timestamps (`ffmpeg -ss`)
to see what was on screen at each one. Turning "I hear static sometimes" into concrete timestamped
evidence is what broke five straight rounds of plausible-but-wrong fixes.

**Verified**: `compileKotlinJvm` + `jvmTest` clean, `android-shell`'s real `assembleDebug` succeeds
(per the root-build Android trap below, that's the correct check, not the root project's own broken
Android target). **On-device**: confirmed fixed on the one Galaxy S25 Ultra this was debugged
against, across multiple rebuild/retest rounds targeting pause, jump/fall transitions, and the
level-3 swing specifically. **Not verified**: any other Android device/OEM - the whole mechanism is
Samsung-Voice-Booster-specific by evidence, so it's unknown whether this was ever reproducible
elsewhere, or whether the fix has any measurable cost (battery, latency) on other hardware. A
write-up for the KorGE community (the `Sound.play()`-constructs-a-new-`AudioTrack`-per-call finding
specifically) is planned but not yet written.

**A separate, unrelated defect found immediately after, while chasing a report of "a weird sound"/
"crackle just after climb and swing"**: not an engine/session bug at all this time, a bad source
clip. A first guess (the footstep deliberately wired into the climb/swing -> walk handover, see
"The swing move" below) was wrong and reverted. The real cause: `resources/sfx/climb.wav` (played
at both climb start and swing launch - "no dedicated swing sample," it reuses climb's) was 1.75s
long but the real recorded grunt only occupies its first ~0.66s (confirmed with `ffmpeg`'s
`silencedetect`: silence from 0.66s to 1.33s, then a second, unrelated burst - three sharp spikes,
crest factor 5+ vs ~1.5-2 for the real transient - running uncut to the file's own end with no
fade-out). That second burst, arriving 1.3-1.7s after triggering climb/swing, is what was being
heard as a delayed crackle. Fixed by trimming to 0.66s with a 40ms fade-out
(`ffmpeg -af "atrim=0:0.66,afade=t=out:st=0.62:d=0.04"`), same PCM s16le/44.1kHz/mono convention as
every other clip here - same category of fix as the already-documented `takeoff.wav` removal above
(a real defect in the source recording, not a cutting choice), just a trim instead of a full
removal since the real transient here was fine. Only one copy of `climb.wav` exists in the repo (no
`ios-shell/Resources/` duplicate to keep in sync, unlike `ui_click.wav`/the toast sounds).

## Compose Resources package trap

`paywall-build/build.gradle.kts` sets `group = "com.infiltrate"` (needed so
`android-shell` can reference this module's Android artifact). Compose
Resources derives its generated `Res` class package from the project
`group` when `packageOfResClass` is unset — so this silently moved the
package and broke every UI file's import. **Pinned explicitly to prevent
recurrence:**
```kotlin
compose.resources { packageOfResClass = "paywall_build.generated.resources" }
```
If `group` is ever changed again, this pin is what stops the generated
package moving with it.

## `android-shell/` is a fully separate Gradle build

Originally included as a root subproject, but applying `org.jetbrains.compose`
1.12.0 (needs Kotlin ≥2.2.0) inside a root build locked to Kotlin 2.0.20
broke configuration for *every* root-build task, not just the new module —
confusing because the failure looks unrelated to whatever task you're
actually running. Fixed by making `android-shell/` fully independent
(removed from `settings.gradle.kts`), consuming `paywall-build`'s Android
artifact via `mavenLocal()` instead of a subproject/composite dependency.

## Real device bugs found and fixed (engine/platform gotchas)

Numbered list of concrete, real (not theoretical) bugs found via on-device
testing across several sessions — kept because each is a non-obvious trap
that could easily be reintroduced elsewhere in the same codebase.

1. **KorGE `Canvas` icon shapes don't scale with device density.** Several
   `drawXIcon()` functions in `MenuComponents.kt` plotted literal pixel
   coordinates with no relation to `DrawScope.size` (the real rendered
   pixel size, which tracks density — a `16.dp` Canvas is 16px at density 1
   but 40-48px on a real phone). Fixed by deriving a
   `size.minDimension / REFERENCE_PX` scale factor per icon. Some icons
   (`drawInkPlay`, `drawCoinIcon`, etc.) already did this correctly and were
   never part of the complaint — check whether a given `drawXIcon` already
   scales before assuming it needs the same fix.
2. **A `verticalScroll` parent gives `weight()` nothing to distribute.**
   Adding scrolling to a screen that used `Modifier.weight(1f)` rows/columns
   broke layout, since a scrolling parent's height is unbounded. Fixed by
   switching those to `Modifier.height(IntrinsicSize.Min)` (size to content)
   instead of stretch-to-fill.
3. **Android `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` silently mutes
   on many phones** — it routes through a system-sounds volume stream
   independent of the in-game SFX slider. The Compose menu's click/toast
   sounds used this and were inaudible; gameplay's own `AudioTrack` path
   (which used `USAGE_GAME`) was always fine. Fixed by switching menu SFX
   to `USAGE_GAME` too, matching the bus that was already known to work.
4. **KorGE 6.0.0's `SoundAudioData.play()` constructs a brand-new
   `android.media.AudioTrack` on every single `.play()` call** — no
   channel/output reuse at this API level, causing a real perceptible delay
   between an event (e.g. landing) and its sound. Mitigated (not fully
   fixed — no channel-reuse API exists in this KorGE version) by priming
   every clip once at `volume = 0.0` during load, which exercises the same
   expensive construction path silently ahead of time. **This priming must
   run once per process, not once per scene load** — re-priming on every
   scene reload (RESTART/QUIT-then-relaunch/watch-ad) accumulates
   `AudioTrack` instances toward Android's per-process ceiling and was one
   of several causes behind a "grey screen on reload" bug — see below.
5. **`PlayerAnimations.load()` reallocated a full 2048×2048 GPU texture
   atlas and re-decoded the whole player spritesheet on every single scene
   load**, with nothing releasing the previous one — confirmed via a
   real on-device `OutOfMemoryError` (added on-screen exception diagnostics
   to `GameplayScene.kt`'s `sceneMain()` specifically to catch this, after
   three earlier audio-focused theories for the same "grey screen on
   reload" symptom were investigated and ruled out). Fixed by caching the
   loaded animation set in a `@Volatile` singleton field, loaded once per
   process — safe since it's static content, identical every level/replay.
   **Not yet extended** to the other per-scene bitmap loads (background/
   crate/fence/etc.) in `GameplayScene.kt`, which follow the identical
   reload-every-time pattern and are real, untested candidates for the same
   class of memory pressure — they degrade silently (`try {} catch { null }`)
   rather than crash, so a leak there wouldn't necessarily surface as an
   exception. Worth checking first if OOM-adjacent symptoms resurface.
6. **AdMob's `onRewardEarned` fires before the ad's Activity is actually
   dismissed** — reloading the level immediately on reward-earned raced
   ahead of the real Android Activity lifecycle transition (MainActivity
   still backgrounded behind the ad's own full-screen Activity), which was
   the *other* real cause of the same "grey screen on watch-ad-continue"
   symptom. Fixed by only finishing the outcome (and thus triggering the
   reload) on the ad's actual `onDismissed`/`onFailure` callback, not on
   reward-earned.
7. **Toggling `KorgeAndroidView`'s visibility (`GONE`/`VISIBLE`) tears down
   its `GLSurfaceView`-backed render surface and does not reliably resume
   it** — a real permanent grey screen, confirmed on-device. Fixed by never
   hiding the KorGE view on Android at all; the Compose menu now draws
   opaquely on top of it instead, and reload/nav logic no longer touches
   its visibility. **Trade-off, unmeasured**: KorGE's render loop keeps
   running the whole time the Compose menu covers it (unlike iOS, where a
   `window.rootViewController` swap genuinely stops frames — see the
   Compose/KorGE spike above). Battery impact on Android is unknown.
8. **A negative `scaleX` on a KorGE `Image` corrupts rendering** on this
   project's GL backend (`GL_VERSION=3.3.0 NVIDIA`) for detailed textures at
   a large downscale factor (confirmed via real screenshots: a torn,
   mostly-transparent smear). Large simple-silhouette images (crate, barrel,
   fence) apparently survive the same path; a detailed asset (the truck,
   ~6.4x downscaled with fine transparent gaps) did not. Rather than rely on
   a driver-level quirk, **both `truck.png` and `entrance.png` are now
   pre-mirrored on disk** and drawn with plain `.xy(x, y)` — no runtime
   `scaleX` flip anywhere in either render path anymore. Avoid `scaleX = -1`
   for mirroring detailed KorGE `Image`s going forward; pre-mirror the asset
   instead.
9. **Vertical collision seam bug — "flying past the end of terrain."**
   `Player.updateStep()` branched on the live (mutated) `vy` instead of a
   value captured once before the loop, so a foot-span touching two
   platforms at once got inconsistent treatment depending on iteration
   order; separately, a foot-span straddling a seam between two
   different-height platforms always resolved to the *taller* one until the
   *entire* foot span cleared it, reading as a rigid hover past the edge
   before snapping down. Fixed: capture `wasFalling = vy > 0.0` once before
   the loop; resolve to whichever candidate platform keeps the player
   closest to their *current* y, not always the tallest.
10. **A `nav_target` storage flag was written by four buttons (QUIT/RETURN
    TO MENU/MAIN MENU/ALL CLEAR) but read nowhere on either platform** — an
    apparent earlier, never-finished feature attempt. "Returning to menu"
    was actually silently reloading the same level. Fixed with a proper
    one-way `LevelExitBridge` (`expect`/`actual`, same shape as
    `ContinueAdBridge`) with a real Android implementation. **iOS poll loop
    added 2026-09-12** (`GameLevelExitBridge` + `AppDelegate.swift` - see
    "Ad preloading" section above for the full wiring); not yet CI/device
    verified.
11. **Settings' Music/SFX sliders had no live effect on the Settings screen
    itself.** `NavigationRoot` re-read volume from storage only when the
    *screen* changed (`remember(currentScreen) { ... }`), not when
    `SettingsScreen`'s own local slider state changed underneath it. Fixed
    by lifting volume state out of `SettingsScreen` into `NavigationRoot` as
    real `mutableStateOf` state passed down via `onXChange` callbacks.

**Debugging lesson worth keeping**: the "grey screen on reload" symptom had
at least two unrelated real causes (#4/#5 texture OOM being the dominant
one, #6 the ad-callback race) after three earlier audio-focused theories
were tried and ruled out. What actually broke the loop of wrong guesses was
adding real on-screen exception diagnostics to `sceneMain()`'s try/catch
instead of continuing to theorize — worth reaching for that first next time
a similar "blank/grey screen, no error" report comes in.

## HUD: objectives panel and gadget-slot bolt (2026-09-11)

The objectives panel (top-left HUD block, `GameplayScene.kt` around `objPanel`) stays on
**Bebas Neue** for its title and both rows - **Inter was tried and explicitly rejected by the
owner** ("previous font was better"), so don't re-attempt a body-text font swap here without
being asked again. What did stick: `objTitle` 13→15, and both `objMainText`/`objOptTag`/
`objOptText` unified at 12.5 (they started at 11/11 with the optional row briefly at a
mismatched 12.5 mid-session - keep all three body rows the same size if this is touched again,
only the title should read larger).

The gadget quick-slot's idle icon (the "gadgets are here" mark beside pause) is now real bolt
art, not the old hand-drawn vector polygon (`drawPowerupIcon`, still kept as a fallback if
`gadget_bolt.png` ever fails to load - see `slotIconImg`/`slotIconFallback` in
`GameplayScene.kt`). `resources/gadget_bolt.png` is `Downloads/charAnimations/assets/lighting.png`
tight-cropped to its alpha bounds (102x235) then resized to 32x64 POT, following this file's own
"Adding new art" procedure. Drawn at 16x22 virtual units (widened past the source's own ~0.43
aspect on request - "make it more thick" - so don't re-derive the display width from the source
aspect if this is revisited). White source art, recoloured white/green via `colorMul` exactly
like the paper-strip buttons already are, so it still tracks live-gadget state.

**Closing the visual gap between the pause bars and the bolt turned out to need the drawn art
shifted off-centre inside each control's own 42px box, not just a smaller `slotGap`** - centring
both icons in their own box (the original approach) left most of the gap as tap-target padding
that `slotGap` alone couldn't close. `pauseBarsShiftLeft` (pause bars, drawn off-centre toward the
gadget slot) and `slotBoltShiftRight` (the bolt, shifted toward pause) are the actual levers;
`slotGap` still exists but is now a minor trim on top of that, not the mechanism. Went through
three rounds on real feedback - too far apart, then too close, then settled at
`pauseBarsShiftLeft = 4.0` / `slotBoltShiftRight = 2.0` / `slotGap = 3.0` with the cluster's own
right-edge inset brought in from 24 to 14 (both `pauseBtn`'s and `slotX`'s `canvasW - 14.0 -
pauseRadius * 2.0`) to sit the whole pair closer to the screen's corner on request. If this needs
another pass, adjust the two shift constants first and treat `slotGap`/the edge inset as fine trim.

Verified on JVM desktop only (compiled, `jvmTest` green, screenshotted with `SetProcessDPIAware`
called first - the first attempt without it silently captured the wrong screen region, exactly
the trap "Asset prep techniques" already warns about). Not run on Android or iOS.

## Player foot-planting: truck hood/cab boundary and idle stance (2026-09-11)

Two real, measured fixes, both from the same complaint ("walking on the truck feels like floating"
/ "idle on crates, one leg floats") and the same technique this file already uses elsewhere -
per-column alpha scanning of the actual PNG rather than guessing from a screenshot.

**Truck hood/cab tier boundary was off by ~9 world units.** `GameWorld.kt`'s `truckFront` (the
hood, the short low tier) was `width = 38.0` - a guess, never measured against `truck.png` itself.
A per-column scan of the image's alpha channel found the art's own hood-to-windshield step
actually lands at ~11.2% of the truck's total drawn width, not 14.5% (`38/(38+45+179)`). In that
~9-unit gap, the collision still said "hood, 66 tall" while the art had already risen to the tall
cab wall above it - not floating in the vertical sense (the hood height itself measures correct,
within a fraction of a unit, everywhere it actually applies), but a real mismatch band nonetheless.
Fixed by narrowing `truckFront` to `width = 29.0` (`29/(29+45+179) = 11.46%`, matching the
measured step). `truckMiddle`/`truckBack` and both height constants are unaffected and still
correct - re-verified by measuring the cab-roof/bed region separately, which sits flat at the very
top of the image (row 0) all the way from ~16% to ~98% of the width, matching their shared
`truckBedHeight = 96.0` exactly. **The one-off "floating" screenshot that first suggested a much
bigger bug turned out to be a landing-animation frame caught immediately after a debug spawn, not
a persistent state** - a second screenshot at the same world position, given a couple more seconds
to settle into real idle, showed clean, flush contact. Re-check settle time before trusting a
single screenshot if this area is revisited.

**Idle's `IDLE_FEET_Y` was 1 row optimistic.** A per-column scan across all 45 idle frames (not
just one - they're identical in foot position across the whole breathing loop, confirmed) puts the
front foot's sole at row 255 and the back foot's at row 248, a stable 7px gap, every single frame.
The existing constant (247) was calibrated close but not exact. Per an on-device report the back
foot still read as floating at that value, so this was deliberately over-corrected rather than set
to the newly-measured 248: `IDLE_FEET_Y` is now `245.0`, a couple of rows past the measured value,
in the same direction as the fix. Screenshotted standing on both a crate and the truck cab roof
after the change - both feet flush, no gap, front foot's extra sink invisible against the dark
silhouette. If a floating foot is ever reported again on a *different* pose, this is the pattern to
repeat (`CROUCH_FEET_Y`/`JUMP_LAND_FEET_Y` already follow it) - scan every frame of that clip, not
one, and bias past the measured value rather than landing exactly on it.

Verified: `compileKotlinJvm` clean, `jvmTest` green, and both fixes screenshotted on a real running
JVM desktop build via a temporary debug player-spawn override (reverted before finishing - `
GameWorld.kt`'s real spawn is unchanged at `x = 235.0`). Not run on Android or iOS.

## Level 1 geometry — current state

The corridor shifted many times across real-device feedback rounds; only
the current layout matters going forward (`GameWorld.kt`'s `createDefault()`,
`DEFAULT_LEVEL_1`, `worldWidth = 3900`):

start gates → ~130 units of ground walk → `smallCrate` (48×68, climb up) →
3-tier `truck` (`truckFront`/`truckMiddle`/`truckBack`, from `truck_new.png`,
tight-cropped and pre-mirrored on disk so the hood faces the approach
direction — drawn once as a single image sized to the union footprint, not
once per collision tier, to avoid squashing) → `longPlatform` (900×96,
carries the hanging chained crate the player crouches under) →
`stepDownCrate` (48×68, descending the platform in two steps instead of one
drop) → open ground → `block2` → seven `barrel` boxes (32×48 each) tiling
the `block2`→`block3` gap edge-to-edge with zero bare ground by construction
→ `block3` → a guard "zone" that is present but permanently disabled
(`LevelData.guardEnabled = false`: a real `Guard` is still constructed, just
parked off-map at `x = -500`, `speed = 0`, with a deliberately wide
patrol range so it can't drift back in — this reads as "removed" to a
player without breaking the many unit tests that read `world.guard.*`
directly) → exit (`entrance.png`, cropped to booth-only 531×612 and
pre-mirrored on disk, `exitfence.png` rendered after it, `exitZone` widened
to match the booth's actual visual footprint rather than eyeballed).
`DEFAULT_LEVEL_1.cameras` is empty (camera removed alongside the guard).

**Jump-height constraint**: every rise in this level is exactly 48 units,
comfortably under `Player.maxJumpHeight = jumpSpeed²/(2·gravity) ≈ 51.2`.

**Test-coordinate lesson (learned the hard way, 4+ times)**: never hardcode
absolute corridor x-coordinates in tests that just need generic open ground
for guard/vision physics — the front-of-corridor geometry has moved
repeatedly across feedback rounds and this breaks tests for reasons
completely unrelated to what they're actually testing. Instead derive
positions from `world.levelData.guardPatrolMinX/MaxX` (real open ground with
no boxes by construction), e.g. `val base = guardPatrolMinX + 75.0`. Also
remember "occluders cleared" and "platforms cleared" are different
guarantees in test setup — clearing one doesn't give you the other, and a
box left in `platforms` can silently relocate a test's player via normal
collision resolution.

Player foot-alignment for held-crouch and jump-landing poses (`CROUCH_FEET_Y = 250.0`,
`JUMP_LAND_FEET_Y = 247.0`) and `interactAngle` (settled at 60°, down-right)
were tuned against measured sprite-frame alpha bounds, same technique as
the pre-existing `IDLE_FEET_Y`. **Exact-shape (pixel-perfect) hitboxes were
explicitly decided against** — the whole collision system is `Rect`-vs-`Rect`
everywhere (`Geometry.kt`/`Player.kt`/`Guard.kt`/`Vision.kt`); switching even
one entity to polygon/pixel collision would mean two parallel collision
systems. `footWidth` narrowing (feet tested on a tighter span than the full
sprite) is the project's intentional answer to "the box is bigger than what
you see" — tune per-case rather than taking on real polygon collision.

The mission dossier card (`MainMenuScreen.kt`'s `MissionDossierCard`) went
through five rounds of spacing tuning; current state: `.offset(y = 30 * dossierScale)`,
4dp bottom padding, a single flexible spacer at the card's foot absorbing
all spare height (no more split weighted spacers), and five fixed
inter-element gaps in the 6-8dp range (scaled) with explicit `lineHeight`
added to the file-number/chapter-label text. All of the above is reasoned
from screenshots across passes, not confirmed against a running app as
final — if a sixth round of feedback comes in, these are the two knobs
(the offset, the fixed gaps) to keep adjusting.

## The swing move (level 4's hook) — added 2026-09-10, tuned on JVM desktop over ~8 rounds

(Was "level 3's hook" when written - that layout, "Blind Spot", was renumbered to `level_4` when the
current level 3 was started; see "Level 3" below. Every "level 3" in this section means `LEVEL_4_LAYOUT`.)

The one scripted move besides the climb, and built the same way: the animation is the source of
truth for the pose, and code places the body so the pose is holding (or standing on) the right
thing. Entry is **walking into the hook and pressing JUMP**, the same button the climb uses.
Standing still and pressing it is an ordinary jump, deliberately: the clip opens on a push-off
stride and there is no version of it that starts from a standstill (the owner confirmed this
reading; do not "helpfully" allow it from idle).

**The clip** (`resources/player/swing`, 52 frames at 165x264, from a 200-frame 360x640 half-res
plate set): raw 59-69, raw 77, raw 114-153. Everything else is cut, and three of the four cuts
came out of watching it rather than out of arithmetic - see the header comment on
`PlayerAnimations`, which carries the full reasoning. The short version:

- **The backswing (raw 78-113) is gone.** In the footage the character jumps straight up, so the
  grab is followed by the legs swinging back before they come forward. In game he has run at the
  hook, so his momentum should carry him forward and the wind-up reads as wrong.
- **The settle (raw 70-76) is gone.** Eight frames of a body hanging almost still. Given real time
  they read as him stopping to wait on the hook.
- The walk-in and the landing run-out are gone for the ordinary reasons (the move is entered from
  whatever the player was doing; the touchdown hands over to the jump's landing-absorb cushion,
  which resolves into walk or idle - this clip never returns to a standing pose so it could not
  meet idle frame 1 anyway).
- **Every frame of what is left is kept**, not every second one. 52 frames at 165x264 still fit a
  single atlas page (84 of these per page), so the smoother version costs nothing until the count
  passes 84.

**How the hang works, and why the collision box does not move during it.** The plates were shot
with the camera locked on the hook, so within a frame the hand holds still and the body sweeps
around it. `Player.SWING_GRIP_ABOVE_CURVE` / `SWING_GRIP_AHEAD_CURVE` are that hand measured off
every frame from the grab to the release (fraction of player height above the feet, and ahead of
the frame's centre), and `advanceSwing` places the body so the hand lands on the hook - the climb's
grip curve idea in two axes. The horizontal half is nearly constant, so **the player rect sits
still under the hook while the silhouette sweeps a body width either side of it**. That is correct,
not a bug: travel comes from the launch and the release, the swing itself is drawn.

**Where the hand goes on the hook is measured off `hook.png`, and getting it wrong is very
visible.** `HOOK_GRIP_X/Y_FRACTION` (0.481, 0.960) are not the rect's bottom-centre, which is what
they were first: that art is mostly chain and its lowest pixel is the *outside* of the bend, so a
fist placed there hangs a whole fist below the metal and the character dangles under a hook he is
plainly not holding. Scanning the image, the point and the shank stand as two runs from row 2040
of 2136 to 2089 - that gap is the bell - and 0.960 drops the fist two thirds of the way down it,
where the opening has closed to about the fist's own width so it meets metal on both sides. Sitting
it at the top of the bell instead left a sliver of sky either side and still read as hovering.
**Re-measure both if hook.png is ever recropped** (`LevelData`'s `hookHeight` hardcodes the same
image's aspect and needs the same care).

**The pacing curve is the whole feel of the move.** `swingDuration` (0.92s) and `SWING_PACING_CURVE`
shape when each frame is shown to produce a full weighted takeoff, instant forward release at apex, and natural grounded landing:
- ~0.38s (progress 0.00..0.41) for frames 0-11: running push-off and clear, weighted leap curving up to the hook (no rushed takeoff)
- ~0.12s (progress 0.41..0.54) for frames 11-29: fast dynamic whip swing under hook and immediate snap forward release (zero waiting at forward apex)
- ~0.29s (progress 0.54..0.86) for frames 29-45: fast ballistic flight arc across the gap to touchdown at frame 44.5 (`SWING_LAND_PHASE`)
- ~0.13s (progress 0.86..1.00) for frames 45-51: feet plant firmly on `terrain2` and torso rolls forward over feet to stand up (zero foot sliding or weird leg extension)

**Level geometry is derived from the move, not the other way round.** `swingLandAhead` (109) is how
far past the grip the player comes down, and `findSwingTarget` refuses to start a swing unless
there is solid ground there level with the ledge being left - so the fixed shape can never strand
anyone. It works in either direction, so level 4's gap can also be re-crossed leftwards. The hook
hangs at the centre of the 150-unit gap; `swingLandAhead` is then set so the touchdown lands 34
units onto the far ledge, which makes the two a matched pair - move one and you move both.

**`swingMinReach`/`swingMaxReach` (75..97) are a rule about where the player leaves the ground**,
not a convenience. Standing at the very lip puts the grip 93 ahead, so that window confines the
push-off to within a few units of the edge. A looser one let anyone holding jump take off with a
third of the platform still under them, which reads as jumping at nothing. It plays looser than it
reads because the existing jump buffer covers an early press and coyote time covers a late one.

**The camera caps how high the hook can hang, and this is the non-obvious constraint.**
`baseWorldViewY` leaves about 140 world units visible above a high tier (296 on level 4). The hook
art's actual hook is only the bottom ~12% of a very tall image - so hanging the grip high enough
for the leap to gain real height puts the hook itself off the top of the screen. Level 4's grip is
112 above the ledge: clear of a standing player's head (96 tall) so it reads as something to jump
for, with the hook fully visible and a dozen units of chain running off-screen. The launch gains
little real height as a result, so `SWING_LAUNCH_ARC` bows it 16 units to sell the leap. **If a
swing is ever wanted with a real vertical gain, the ledges have to come down, not the hook go up.**

**Verified**: `jvmTest` (both suites, including four swing tests), `:paywall-build:jvmTest` (the
korlibs lint - `Player.kt` stays pure Kotlin), both JVM compiles, **and the JVM desktop build was
run and screenshotted across full swings after every one of the tuning rounds** - push-off, the
fist meeting the hook, the sweep, the release and the landing were all read off real frames.
**Not verified**: never run on Android, iOS, or any real device.

## Level 3 ("03: New Level", WIP) — the roof table and the guard under it (2026-09-12)

`LEVEL_3_LAYOUT` is the owner's in-progress level: start fence, one crate, a 450-wide cantilevered
"roof" (`table.png`, the plank-on-one-leg art), exit. The doc comment on the layout carries the full
reasoning; the parts a future session needs are:

- **The table's collision is two boxes now, not one** (`LevelLayout.tableParts`, mirrored on
  `GameWorld`): a 30-deep plank along the top and a 30-wide leg column at the left end from plank
  top to ground. `tables` holds only the ART rect, drawn once in its own pass in `GameplayScene`
  (same arrangement as level 1's `truckParts`/`truck`); the two part boxes are skipped by the box
  loop. The leg is what the crate->roof mantle braces against (`Player.findClimbTarget` needs the
  face's bottom to reach the feet and its top to be the landing surface), so **the leg's top must
  stay at the plank top and its bottom at the ground** - shortening either breaks the climb, and
  `testLevel3ClimbOntoRoofAndCrossItUnseen` will say so. The measured numbers (slab rows 0..102 of
  512 = 28.7 units, leg columns 36..79 = 8..17 units in) are in the layout's comments.
- **The guard paces the open underside**: stands at the far post (860, facing right, out past
  the roof's end) for 3s, walks to the near post by the leg (560), stands 3s facing left, walks
  back, repeats - the owner's spec "stay idle -> walk -> stay idle -> come back". Built on a new
  opt-in `Guard.patrolPauseDuration` (via `GuardSpawn`; 0 = the old instant turn every other
  guard still has) and `Guard.isWalking`, which the scene keys the walk/idle animation on. He is
  30x96 (player height - `GuardSpawn` grew `width`/`height` for this; every other guard still
  defaults to the old 26x48). Wherever he is, the climb and the crossing above are blind to him
  (leg and plank occlude). The beat is the drop off the far end: from the far post his cone
  covers the landing zone out to ~1100, and the player on the roof can see the beam poke out
  past the plank's end - drop while he is away at the near post. Four tests pin this
  (`testLevel3*` plus `testGuardWithoutPauseStillTurnsOnTheSpot` in `GameplayModelTest.kt`).
- The stationary version (speed 0, facing left, a noise-triggered turn) was built first and
  screenshotted on the JVM desktop build; **the pacing version is model-tested only** - the owner
  said not to run it, so the walk animation has never been seen on screen. First thing to check
  when it is: feet planted (stride constant `WALK_STRIDE_PER_HEIGHT`) and the idle<->walk pop at
  each post. **Not run on Android or iOS.**

## Guard sprite (`GuardAnimations.kt`, `resources/guard/{idle,walk}/`) — 2026-09-12

Guards are drawn with real art now, not the black 26x48 rect: `GuardAnimations` is a copy of
`PlayerAnimations`' recipe (own 2048x2048 atlas - 16.8MB, the same step the player's pages come
in - cached once per process, feet-anchored sprite scaled so the standing silhouette equals the
hitbox height, `scaleX` negated to face left - the crop boxes are symmetric about the character
so the flip does not shift him). `tools/art/prep_guard.py` cuts both clips from the owner's raw
360x640 plates (`Downloads/charAnimations/guardidle` and `guardwalk`, copied to the gitignored
`art-source/guard/`) and **prints the constants the Kotlin file needs** - re-run and paste, don't
hand-edit them. Its header carries the per-clip reasoning; the short version:
- **Idle**: every other frame (72 at 75x246). The plates do not loop (frame 144 is ~30
  adjacent-steps from frame 1), so the animation is ping-ponged by listing the same slices out
  and back - no atlas cost. `IDLE_FEET_Y = 241` is the player idle's back-heel over-correction.
- **Walk**: raw 66..105, every frame (40 at 120x256) - the 40-frame window with the tightest
  wrap of a walk-in-place plate. **The walk plates are framed 6.5% smaller than the idle plates**
  (542 vs 577px standing), so the script scales them up on the way in or he would shrink the
  moment he moved. Each walk frame is cut at its own lowest row (the plate's ground line wanders
  10px with the stride), the way the player's crouch-walk had to be. Driven by distance
  travelled in `GameplayScene` (`WALK_STRIDE_PER_HEIGHT = 0.554`, planted foot measured at
  ~7.5 plate px/frame), so the feet stay planted at any patrol speed.

Levels 5+ guards still have 48-tall hitboxes, so they draw as half-height men - they now walk
properly (the walk clip is driven by their real movement) but need a 96-tall hitbox pass of
their own (`SIDE_SCROLL_LEVEL_LAYOUT`'s walkthrough test and patrol geometry are tuned to 48).

**Rendering decisions the owner made on seeing it**: the red "visor" rectangle is gone (it only
survives in the no-art fallback, where a featureless rect has no other facing cue), and **guard
vision cones are one flat white** (`GUARD_CONE_COLOR`, `Colors.WHITE.withAd(0.30)`) in every
state - the old orange/gold/pulsing-red ramp duplicated what the detection pip over the guard's
head already shows. Don't reintroduce a colour ramp on the cone. Camera cones were not touched and
still use the old orange/red ramp - a mismatch, left for the owner to call.

If `GuardAnimations.load()` throws (the iOS shell does not bundle `resources/` - see "Audio"),
`GameplayScene` logs `[GuardAnimations] load failed` and falls back to the rect + visor rather than
failing the level.

## Asset prep techniques

Reusable, hand-verified techniques for prepping raw art drops
(`C:\Users\USER\Downloads\charAnimations\assets\`, the owner's usual source)
for this project:

- **Tileable parallax backgrounds** (`GameplayScene.kt` tiles `bgmg*.png` by
  placing copies edge-to-edge, not GPU wrapping — the source PNG's own left
  edge must visually continue into its own right edge). For an image
  without naturally-matching edges: (1) circularly roll the image
  horizontally by `width // 2` — this makes the far-left/far-right columns
  mathematically adjacent by construction, relocating the one real seam to
  a band near the image's center; (2) heal that single center seam with a
  narrow, falloff-weighted blend against a Gaussian-blurred copy of itself
  (too wide/strong reads as an obvious smudge; too narrow leaves a hard
  line); (3) verify by diffing the final left/right edge columns and
  rendering a synthetic "tile join" strip. **Band width and blur radius have
  to scale down with source detail/contrast, not just image size** — a first
  pass on `bgmg6.png` (see below) at 170px half-width / 24px blur (the
  figures originally written here, based on `bgmg5.png`'s fog-heavy source)
  produced a clearly visible vertical haze band on a crisper, higher-contrast
  source; a second pass at **80px half-width / 10px blur with a smoothstep
  (not linear) falloff** brought it down to reading as atmospheric haze
  consistent with the rest of the shipped `bgmg*` set. Compare the wrap-edge
  diff (`col[0]` vs `col[-1]`) against the existing `bgmg2-5.png` files as a
  sanity check (all sit around mean 0.7-2.7, max 16-138) rather than chasing
  a specific number — the right target is "look of the seam band", not the
  diff statistic, since the diff only measures the true wrap edge, not the
  healed band itself.
  - **`bgmg6.png`** (level 4's background, `DEFAULT_LEVEL_4.backgroundImage`)
    is made this way from `darkbg3.png` in the owner's usual source drop, at
    its native 2172x724 (identical to `bgmg5.png`'s own size, same batch/
    style) — not resized, per the "DO NOT SHRINK" list below, since it's
    already at/under device resolution at that height. **A first version was
    made from `darkbg2.png` instead, then fully replaced** (same filename,
    same technique, different source) at the owner's request before ever
    being committed — `darkbg2.png` has no trace left in the repo or in any
    shipped build. Verified: `compileKotlinJvm`/`jvmTest` clean, and a full
    JVM desktop playthrough of level 4 end-to-end
    (`./gradlew runJvm --args="level_4"`) reached MISSION SUCCESSFUL with the
    background visibly tiling the whole way, screenshotted at several scroll
    positions. Not run on Android or iOS.
- **Tight-cropping a silhouette asset to its real alpha bounds** before
  using it as a stretched collision-box texture (`box.width`/`box.height`
  stretch with no render-time cropping, so any dead margin in the source
  stretches too and reads as the silhouette floating inside its own box).
  Measure the true content bounds (`.NET Bitmap.LockBits`, alpha-channel
  scan) and crop in place; re-derive box width from the *cropped* aspect
  ratio at the box's fixed height.
- **Chroma-keying an opaque JPEG-style asset with no alpha channel** (an
  alpha-bounds crop does nothing if every pixel already reads 100% opaque):
  treat any pixel with R/G/B all ≥200 as background → fully transparent,
  else fully opaque, then crop to the resulting tight bounds as above.
- **Finding a seam inside a composite illustration** (e.g. splitting a
  wide fence-line image into a booth-only crop and a separate fence-only
  crop): scan column-by-column opaque-pixel density and look for a sharp
  drop — that gap is the seam between the two visual elements.

## App icon

Real icon set from a 1254×1254 opaque silhouette-against-moon illustration,
wired into all three real targets: Android (`android-shell/`, legacy square
+ round + adaptive icon at all densities, `AndroidManifest.xml` updated),
iOS (`ios-shell/Resources/Assets.xcassets/AppIcon.appiconset/`, Xcode 14+
single-size format, `ASSETCATALOG_COMPILER_APPICON_NAME` set in
`project.yml`), and `:game`'s own korge{} JVM/JS/desktop targets
(`korge { icon = file("icon.png") }`). Not yet verified on a real
device/emulator/build.

## Language dropdown in Settings — UI only, no translations yet

`SettingsScreen.kt`'s LANGUAGE row is a real dropdown listing 15 languages
(English + 14 chosen for Shipaton store-listing reach), each spelled in its
own native script (e.g. "日本語", not "Japanese") per explicit request.
**This is UI plumbing only — the app has zero translated strings.**
Selecting a language just persists the code; every screen still renders
English regardless of selection. Deliberately not using the display font
(Bebas Neue, Latin-only) for language names, and deliberately no
`letterSpacing` on them (breaks Arabic glyph joining).

## Reset Progress in Settings — implemented 2026-09-12

The "RESET PROGRESS & SETTINGS" button in `SettingsScreen.kt` completely restores
starter state while preserving real-money IAPs:
- **Confirmation dialog**: tapping the card opens a modal overlay (`showResetConfirmDialog`)
  styled in `#16161A` with a `#FF5252` accent border and Bebas Neue title ("CONFIRM PROGRESS RESET")
  with CANCEL and RESET EVERYTHING buttons, preventing accidental one-tap wipes.
- **Gameplay reset (`profileStorage.resetProgress(preservePremium = true)`)**: resets coins to 100,
  starter powerup inventory (2 jammer, 2 smoke, 1 bomb, 2 darts, 2 phantom, 2 invis, 2 boots, 1 trigger),
  unlocked level IDs back to `["level_1", "level_5"]`, and `totalLevelsCompleted` to 0. Controls
  layout resets to Default (left), language resets to English ("en"), and volumes reset to 0.8 / 1.0.
  **`isPremium` (Remove Ads) is explicitly preserved** so paid entitlements are not lost.
- **Mission progress reset (`levelStorage.clear()`)**: clears `inMemoryFallback` and purges all
  stored `level_result_$id` keys and `level_results_ids` via `removeRaw` and empty-string fallbacks.
- **Ad limiter reset**: removes `user_coin_ad_watch_count` and `user_gadget_ad_watch_count` so the player
  gets fresh daily rewarded watches on the new save.
- **Storage deletion support (`PlatformStorage.removeRaw(key)`)**: added to `PlatformStorage` expect/actual
  across JVM (`ConcurrentHashMap.remove`), Android (`SharedPreferences.Editor.remove().apply()`), and
  iOS (`PaywallStorage.removeRaw` via `NSUserDefaults.removeObjectForKey`).


## Web presence (`site/`)

A `site/` directory for Netlify deployment (e.g. `infiltrate.saysplit.app`):
`site/index.html` (landing page), `site/support/index.html` (App Store
Guideline 1.5 support page with a Netlify Form — 4 fields: Name, Email,
Category, Message; no visible email address, no platform/subject fields, no
FAQ content — an earlier note claiming otherwise was wrong and has been
corrected), `site/privacy/index.html` (privacy policy, covers on-device
storage, AdMob/UMP consent, RevenueCat, COPPA/GDPR/CCPA),
`site/styles.css`, `site/_redirects` + `site/netlify.toml`.

**Two known, unresolved compliance gaps, flagged not fixed:**
- The privacy policy's "Purchase data, via RevenueCat and the app stores"
  paragraph describes real purchase validation that doesn't exist yet — see
  "Store screen has no real purchase flow" below. Not rewritten without the
  owner's confirmation, since payment-processing language is consequential.
- **Apple's App Tracking Transparency prompt is not implemented anywhere in
  the iOS code**, even though AdMob is embedded and can serve personalized
  ads. This is a real App Store submission risk if personalized ads ship on
  iOS without it — needs a decision (add the ATT flow, or force
  non-personalized ads on iOS) before submission.

A `/delete` data-deletion page was built (for Google Play Data Safety's
"Delete data URL" field) then **deliberately reverted the same day**: the
form implied a database lookup by email, but the game collects no email
except from a prior Support submission — for almost every player there is
nothing on file to find, making the page misleading. The owner's decision
was to answer Play Console's deletion-request question "No" rather than
maintain a flow for data that, in practice, rarely exists to delete. If a
genuine deletion flow is ever wanted, it should lead with that reality
(all game data is local-only, deleted by uninstalling) rather than implying
a database lookup.

## Layers Events SDK — REMOVED (2026-09-12)

Previously integrated (`com.layers.sdk:layers-android:3.3.0`, `app_a1f9dbc126c1c779`) on Android via
an `AnalyticsBridge` pattern and configured in `InfiltrateApplication.kt`. Fully removed on 2026-09-12:
dependency removed from `android-shell/build.gradle.kts`, configuration stripped from
`InfiltrateApplication.kt`, tracking calls removed from `GameplayScene.kt`, all 7 `AnalyticsBridge`
files deleted, and disclosures removed from `site/privacy/index.html`. The app currently has
zero third-party product analytics SDKs.


## Device heating on Android — measured root causes (2026-09-09)

Investigated after "it heats very fast on a Galaxy S25 Ultra". Measured on JVM against the real
level data (a throwaway `test/HeatProfileTest.kt`, deleted after use) plus KorGE 6.0.0's own
decompiled Android classes and sources — **not** on a device, so the ranking is evidence-backed but
the on-device improvement is unconfirmed. Ranked by cost.

**1. The guard/camera vision cones are the only views in the game drawn with KorGE's SOFTWARE
rasterizer, and they are rebuilt from scratch every frame.** This is the dominant cost and the one
worth fixing first.

`UiComponents.kt`'s own `uiGraphics()` helper correctly passes `GraphicsRenderer.GPU`, and every
other shape in the scene uses it. The two exceptions are `GameplayScene.kt:448` and `:466`, which
call KorGE's raw `worldView.graphics()` — whose default is `GraphicsRenderer.SYSTEM`, i.e.
`CpuGraphics`. Read `BaseGraphics.redrawIfRequired()` in KorGE's sources: on every dirty frame it
allocates a **brand-new `NativeImage` sized to the shape's bounds times the device scale**,
software-rasterizes the whole antialiased polygon into it, uploads it as a fresh GPU texture and
deletes the previous one. `Graphics.updateShape` builds a new `Shape` object each call, so the
dirty flag is set every single frame.

Measured bitmap footprint at `worldZoom` 1.35 and a 1440p landscape device (scale 3.0):

| level | cones | bitmap per frame | at 60fps | at 120fps |
|---|---|---|---|---|
| 1 | 1 | 1053x1053 = 4.23 MB | 254 MB/s | 508 MB/s |
| 2 | 1 | 891x591 = 2.01 MB | 121 MB/s | 241 MB/s |
| 4 | 3 | 3.34 MB | 201 MB/s | 401 MB/s |

**On level 1 that entire cost is for a guard that does not exist in play.** `guardEnabled = false`
parks a real `Guard` off-map at `x = -500` (see "Level 1 geometry"), but `guardCones` is built from
`world.allGuards` unconditionally, the cone `Graphics` is a direct child of `worldView`, and guards
are deliberately excluded from the culling pass — so an invisible, off-screen cone is rasterized and
re-uploaded at 4.23 MB/frame for the whole tutorial level.

**2. Building the cone polygon allocates 275 KB per cone per frame.** Separate from the raster cost
above. `GeometryUtils.castRay` calls `Rect.edges()` per occluder per ray, and `Rect.topLeft`/etc are
computed getters — so each ray allocates 4 `Segment2d` + 8 `Vec2d` per occluder, and
`Segment2d.intersects` allocates 3 more `Vec2d` per edge test. Measured on desktop x86:

| level | occluders | cones | polygon build | garbage |
|---|---|---|---|---|
| 1 | 18 | 1 | 23.1 us/frame | 275 KB/frame (32 MB/s at 120fps) |
| 2 | 16 | 1 | 21.0 us/frame | 244 KB/frame (29 MB/s at 120fps) |
| 4 | 11 | 3 | 49.8 us/frame | 563 KB/frame (66 MB/s at 120fps) |

For scale, `world.update` — all the physics, detection and noise logic — is **0.9-3.2 us and under
2.5 KB per frame**. The simulation is not the problem; the cone rendering is, by roughly 20x.

**3. Nothing caps the frame rate, so the whole loop runs at the panel's refresh rate — up to 120 Hz
on this phone.** Verified in KorGE 6.0.0: `GameWindow.continuousRenderMode` defaults to `true`,
`KorgwSurfaceView` is a `GLSurfaceView` left in `RENDERMODE_CONTINUOUSLY`, its `onDrawFrame` calls
`gameWindow.frame(doUpdate = true, doRender = true)` with no throttle, and `Views.frameUpdateAndRender`
uses the real wall-clock delta. **`KorgeConfig.targetFps` is a dead knob** — `Korge.kt:256` writes it
into `Views.targetFps` and nothing anywhere reads it (checked in both the sources and the compiled
classes). Nothing calls `Surface.setFrameRate` either. So every cost above is paid twice as often on
a 120 Hz phone as the 60 Hz figures the rest of this file assumes.

Note the trap: `Views.forceRenderEveryFrame` is an alias for `gameWindow.continuousRenderMode`, so
setting it `false` does **not** cap anything — it flips the `GLSurfaceView` to `RENDERMODE_WHEN_DIRTY`
and hands the update loop to `KorgwSurfaceView`'s `korgw-updater` thread, which is an infinite loop
with a bare `Thread.sleep(1)` (it computes the elapsed time into a local and never uses it). That
would run updates at ~1000 Hz. Do not reach for it as a fix.

**4. That `korgw-updater` thread spins at ~1000 wakeups/second regardless**, doing nothing useful
while `continuousRenderMode` is true. It is only stopped in `onDetachedFromWindow`, which does not
fire when the app is merely backgrounded — and `MainActivity` never calls `onPause`/`onResume` on the
surface view either (`KorgeAndroidView` exposes no such methods; it is a plain `RelativeLayout`).
Small next to items 1-3, but it keeps the CPU out of deep idle.

**5. The oversized textures and the always-rendering-under-the-menu behaviour** are the two already
documented in the next section (items 2 and 4 there) and still apply unchanged.

**Fix order, none of it done yet**: switch the two `worldView.graphics()` calls to `uiGraphics()`
(GPU renderer); skip the cone entirely when `levelData.guardEnabled` is false; only recompute the
polygon when the cone is on-screen and its inputs actually changed, the same "unchanged shape is
skipped" guard the powerup chips already use; then look at a real frame cap.

## Runtime performance: where the frame budget actually goes (2026-09-08)

Investigated after an "it lags on my Android phone" report. Measured off the assets and the
render path, **not** off a device profile — treat the rankings as reasoned, not confirmed, and
run `adb shell dumpsys gfxinfo com.infiltrate.androidshell framestats` plus Android Studio's
Memory Profiler before trusting any of it as the cause.

**1. The player atlas is the single biggest memory consumer, by a wide margin.**
`PlayerAnimations.load()` packs every frame into `MutableAtlas(2048, 2048, NEW_IMAGES)`, which
adds a whole page at a time: 16.8MB as a `Bitmap32` on the heap *and* again as a GPU texture.
Cost therefore goes up in 16.8MB steps, not smoothly. It was 26.2M pixels (≥7 pages); trimming
frames that are loaded but unreachable — climb's raw 1-69 run-up (`CLIMB_START` clamps display
to raw 70) and crouchwalk's raw 145-192 tail (the gait loop wraps at 144) — brought it to 20.3M
(≥5 pages), ~34MB of heap and ~34MB of texture memory back for zero visual change. **Adding
animation frames here is not free**; there is now an ATLAS BUDGET comment on `load()` saying so.
Adding the swing clip (2026-09-10, 48 frames at 173x264) put it back up to **22.5M pixels**, which
is roughly one more page — the cost was accepted knowingly and is why that clip is trimmed to 48
frames out of 200 raw. Anything added next should assume it is paying ~16.8MB of heap and the same
again of texture memory for the page it tips over into.
Note the climb clip's START/END constants are in **loaded-index space** now, not raw file
numbering — `loadAnimation(firstFile = ...)` skips the unloaded head.

**2. Every texture was authored 10-26x larger than it is drawn, and mipmaps could not fix it.**
**FIXED 2026-09-10** - see "Item 1, DONE" below. Kept here because the diagnosis explains what
mipmaps are for, and the failure mode is one this engine will happily reproduce on the next asset.

`barrel.png` was 832x1274 drawn at 32x48 (~26x, and level 1 has seven of them); `crate.png`
851x595 at 68x48; the five touch buttons (`left/right/jump/crouch/interact.png`) were ~1200x1200
drawn at 96-108px. Minifying that hard without mipmaps means adjacent output pixels sample
texels ~26 apart, so nearly every texture fetch misses the GPU cache - the classic mobile stall,
and the likely source of any shimmer on the barrels while scrolling.

**The trap worth recording: `bitmap.mipmaps(true)` is a SILENT no-op on non-POT art.** KorGE
6.0.0's `AGObjects.kt` `doMipmaps()` returns `requestMipmaps && width.isPowerOfTwo &&
height.isPowerOfTwo` - no error, no log line, no mipmap. Every asset in the project was NPOT, so
the engine had never built a single mip level. The ten worst offenders are now POT and
`SceneAssets` requests mipmaps on load; it also **warns when a minified asset is not POT**, so this
cannot silently come back. See "Adding new art".

**3. Per-frame allocation in the updater** (all fixed, all mechanical):
`InMemoryGameProfileStorage.getProfile()` returns a *deep copy* — a fresh `GameProfile` plus a
copied unlocked-level set plus a copied powerup map — so reading one volume float allocated three
objects. `GameplayScene`'s updater did that for music volume, again for the powerup HUD, and once
more per footstep. Now read once per frame into `cachedProfile` (refreshed at the top of the
updater and after `tryActivatePowerup`, so menu changes still land on the next frame).
`GameWorld.update` rebuilt `platforms`/`boxes`/`occluders` concatenations every frame even when
`movingPlatforms` was empty, which is most levels; it now reuses the level's own lists in that
case, and builds the player's platform list into a reused scratch buffer. The powerup HUD chips
called `updateShape` — a full re-tessellation of a rounded rect, its stroke and the timer
underline — every frame regardless of change; they now skip when the chip would be identical.

**4. Android-specific: KorGE renders continuously underneath the Compose menu.** By design —
`MainActivity` never hides `KorgeAndroidView` because toggling its visibility tears down the
render surface (see "Real device bugs" #7). The cost is that the engine burns frames behind every
menu. If a lag report is about the *menus* rather than gameplay, this is the first suspect and it
is a different fix from anything above.

**Amplifier worth knowing**: `dtSec` is clamped to 0.1s and `Player.update` sub-steps at 1/60, so
a 100ms hitch runs six physics steps — slow frames make themselves slower.

**Second pass (same day): dead assets removed and the per-scene reload fixed.**
- **`resources/` went from 75MB to 40MB.** 27 PNGs were being packaged into the APK
  (`mergeDebugAssets` picks up the whole directory) with nothing in the code ever loading them:
  `a1-a5`, `bg1-bg5`, `bg10-bg13`, `bglayer`, `bgmg`, `mglayer`, `mglayer2`, `card_bg`,
  `chainedhook`, `korge`, and `store_ad/briefcase/duffle/pouch/stash/vault`. The three that look
  live are not: `bg12`, `card_bg` and the `store_*` set are referenced only through
  `Res.drawable.*`, which resolves to **paywall-build's own separate copies** under
  `src/commonMain/composeResources/drawable/` - the `resources/` copies were shipping twice. The
  live backgrounds are `bgmg2/3/4` (`LevelData.resolvedBackgroundImage`'s rotation) plus `bgmg5`
  (`DEFAULT_LEVEL_2`/"02: Cargo Yard"'s literal - this entry previously said "level 4's literal",
  which was already wrong when written; corrected here) and `bgmg6` (`DEFAULT_LEVEL_4`/"04: Blind
  Spot"'s literal, added 2026-09-12). The app icon is `icon.png` at the repo root, not
  `resources/korge.png`.
  **Before deleting anything else here, grep the whole repo excluding `build/` - the only hits for
  a dead asset are in `build/intermediates/.../merger.xml`, which is the packaging evidence, not a
  reference.**
- **`SceneAssets.kt` (new) caches bitmaps and fonts process-wide.** `sceneMain()` was calling
  `readBitmap()` twenty times and `readTtfFont()` twice on *every* scene load - and a scene load
  happens on RESTART, QUIT-then-relaunch, watch-ad-to-continue and every level change - so a
  normal session re-decoded ~87MB of PNG and re-uploaded it all as GPU textures repeatedly. This
  is the untreated other half of "Real device bugs" #5, which fixed the player atlas the same way
  and explicitly flagged these loads as still outstanding. They degrade silently (`catch { null }`)
  rather than throwing, which is why the cost read as reload stalls and GC churn instead of a
  crash. `UiComponents`'s own repeated font/bitmap loads go through it too. Only successful loads
  are cached, so a missing file still retries rather than being remembered as permanently absent.
- **Dead code removed**: `UiComponents.drawAtmosphericBackdrop()` and
  `drawAtmosphericBackdropBitmap()` are gone - the first had no callers, the second's only caller
  was the first. Both are leftovers from the pre-Compose KorGE main menu (their doc referenced a
  `MainMenuScene.onSizeChanged` that no longer exists). Three imports went with them. This
  orphans `resources/bg_menu.jpg` (773KB), which is now a deletion candidate along with
  `resources/logo.jpg` (448KB) and `resources/test_minimal.ldtk` - **none of those three verified
  yet**, and `test_minimal.ldtk` in particular is likely used by `test/LdtkLoaderTest.kt`.
- **Lossless PNG recompression applied** (Pillow, `optimize=True, compress_level=9`): 229 of 604
  files got smaller, 1.29MB saved; the other 375 - almost all player animation frames - were left
  alone because recompressing them came out *larger*. Every rewritten file was checked to decode
  to byte-identical pixels against its `git HEAD` blob, 229/229 identical. Safe because korim's
  PNG decoder (`PNG.kt`'s `readChunk`) handles only `IHDR`/`PLTE`/`tRNS`/`IDAT`/`eXIf`/`IEND` -
  the `gAMA` chunks dropped from 361 files are never read, no file carries an ICC profile, and
  none carries `eXIf`. This is a **download-size win only**: decoded texture memory is always
  `w*h*4` regardless of how well the PNG compresses. A real optimizer (oxipng/zopflipng) would
  beat Pillow substantially if the download size matters more later.

**Third pass (2026-09-09): culling, audio, label caching, last dead assets.**
- **Off-screen culling** (`GameplayScene.kt`): KorGE does no frustum culling, so every child of
  `worldView` submitted geometry every frame across a 3900-unit level with ~1040 visible. Static
  decor now registers its world-space x-span via a local `cullable(view, left, width)` as it is
  built (six sites: platforms, boxes, entrance, exit fence, truck, hanging crates) and the updater
  toggles `visible` from the camera window right after `worldView.x` settles. Margin is a **half
  screen either side**, deliberately generous - the saving is in not submitting the far end of the
  level, not in trimming the last few units, and a wide margin makes pop-in impossible. **Static
  only**: player, guards, cameras and moving platforms are excluded because a span captured once
  would go stale; their pips and vision cones are children of those containers so they follow.
- **`bgmusic.mp3` re-encoded** 256 kbps joint stereo -> 128 kbps joint stereo, 2.22MB -> 1.11MB
  (50%). Kept stereo rather than going mono: the side channel (L-R) measures -32.8dB mean against
  the mid's -15.7dB, so the image is narrow but real, and collapsing it would have been a bigger
  perceptual change than the size justified. The Xing/LAME header is kept deliberately (the
  original had none) because it declares encoder delay/padding, which is what keeps a **looping**
  track gapless on decoders that read it - Android's does. **Unverified: the loop seam has not
  been heard on a device.** If a tick appears at the loop point, that header is the first suspect.
- **Powerup chip labels** are rebuilt only when one of their three inputs changes (live/not,
  count, timer tenths). The win is less the string than the `countText.width` read used to
  re-centre it, which forces a text bounds measurement - and that call used to run once per chip
  per frame *including for hidden chips*, outside the visibility branch.
- **`bg_menu.jpg` and `logo.jpg` deleted** (1.2MB). `test_minimal.ldtk` is **KEPT - it is used by
  `test/LdtkLoaderTest.kt`**, which the earlier note only guessed at.

**DECIDED NO (2026-09-10, revised): do not atlas the static world art. Not "not yet" - not at all.**
This entry previously said it was deferred and "only becomes worthwhile after the oversized source
art is re-encoded down to roughly its drawn size, at which point the set fits one page."
**That was wrong on both halves, and item 1 disproved it.** Two independent reasons:

*The cost.* `MutableAtlas` allocates its whole page up front (`Bitmap32(width, height)`,
`MutableAtlas.kt:24` defaults `width = 2048, height = width`) however little of it is used. Before
item 1 the seven stretch-to-box world textures totalled 6.10M px - ~24MB individually, but 2-3
pages once packed, i.e. **34-50MB**, so atlasing would have *raised* texture memory by 10-26MB.
After item 1 they total **1.96M px, ~7.8MB held individually** - less than *half* of one 16.8MB
page, so atlasing now more than doubles them. Shrinking the art did not unblock atlasing; it
removed the reason for it. Hand-sizing the page to ~2048x1280 only reaches break-even.

*The benefit, which was never counted.* Level 1 draws roughly **14 textured world sprites across 7
textures** (2 fences, small crate, truck, step crate, 7 barrels, entrance, exit fence), and the
culling pass means fewer are on screen at once. The ceiling is therefore ~10 fewer texture binds
per frame. A mobile GPU absorbs hundreds of draw calls without noticing - ten is inside the noise
floor. This half was true before item 1 as well; the resize only made it legible.

*And it now conflicts with mipmaps*, which are live and are buying something real: mip levels
average across slice boundaries and bleed neighbouring cutouts, so an atlas would need gutters
sized for the whole mip chain.

Atlasing pays when hundreds of small sprites thrash texture state - a bullet-hell, a tile-heavy
scene, a particle system. This scene is a dozen large stretched props. The one place the reasoning
does hold here is the player animation atlas, which is already atlased; shrinking it further is the
alpha-trim item, a different job with different risks. **Same arithmetic - cost AND benefit -
applies to any future "just atlas it" idea; count both sides before proposing it again.**

### Item 1, DONE 2026-09-10: re-encoded the oversized art to POT + enabled mipmaps

Done in one pass on 2026-09-10. Ten assets re-encoded, **50.5 MB -> 9.8 MB of texture memory
(~13 MB with mipmaps, so ~37 MB recovered)**; on disk 10.2 MB -> 2.1 MB. Mipmaps now actually
build, for the first time in this project's life. The spec that follows is kept because the RULES
still apply to every asset added from here on - see "Adding new art" immediately below.

**What was verified**: `compileKotlinJvm` clean, `jvmTest` 83/83 green, and the desktop build was
run and screenshotted - fence, touch controls, tutorial callout, HUD all render as before. Beyond
the screenshot, each asset was rendered into its exact device-pixel draw box both before and after
and the two compared: **mean error below 0.6/255 for all ten** (worst: `fence2.png`, 0.60), with
peak deviations confined to isolated hard-edge pixels. That comparison is the right test, because
what matters is not whether the small file resembles the big one but whether what lands in the
draw box changed - and it did not.

**Originals**: the pre-shrink high-resolution art is NOT kept in the working tree. It lives in git
history at `77a65b9` and earlier - `git show 77a65b9:resources/interact.png > interact.png` to get
one back. Anything re-authored later should be prepped per the rule below rather than restored.

**THE SIZING RULE - get this wrong and the art is blurry on exactly the phones you demo on.**
The virtual canvas is 1040x480 (`main.kt` / `MainActivity.kt`), but the device renders at its
native resolution, so everything is scaled by `deviceHeight / 480`: **2.25x on a 1080p phone, 3x on
a 1440p one**. An asset "drawn at 32x48" is really 96x144 device pixels. Size every target from the
**3x** figure, then round UP to a power of two (POT is what makes mipmaps work at all - see the
NPOT note above). Sizing from the virtual numbers gives art at a third of the needed resolution.

**Aspect ratio is NOT a concern for stretch-to-box assets** - a correction, since it was stated the
other way twice while working this out. Every one of these draws is `size(box.width, box.height)`,
whole image to whole box, so a source pixel at normalised `(u,v)` lands at `(u*w, v*h)` no matter
what the source's own aspect is. Resampling 832x1274 -> 128x256 and then stretching to 32x48 is the
same mapping as before; only resample quality changes. The real rule is narrower: **resample to
POT, never pad to POT** - transparent padding becomes part of the image and gets stretched into the
box with everything else, shrinking the art inside its own collision box.

**Five code prerequisites** (1, 3, 4 and 5 resolved in the pass; 2 sidestepped - see notes):
1. `entrance.png` and `exitfence.png` have their drawn width derived from the bitmap's aspect
   **at runtime**: `entranceWidth = entranceHeight * (bitmap.width / bitmap.height)`, and the exit
   fence is then positioned at `exitZone.x + entranceWidth`. For these two the stored aspect is
   load-bearing - resample them and the extraction point moves. Replace both with explicit widths
   (pin to today's computed values so the change is a no-op) before resizing either. Until then
   they also cannot be POT, so they cannot get mipmaps at all.
2. `chainedcrate.png` / `chainedcrate2.png` are sub-sliced with **hardcoded pixel coordinates** -
   `(26, 1222, 971, 226)` and `(235, 1134, 555, 287)` in `renderHangingCrate`, plus
   `chainDrawH = cropY * scale` using `cropY` as a pixel measure. Express them as fractions of the
   bitmap's dimensions first, or leave both files alone.
3. `stars.png` (added later than the rest of this spec) is the same trap as the chained crates:
   the three gold stars are cut out of one strip with **hardcoded pixel coordinates** -
   `sliceWithSize(69, 33, 636, 611)`, `(760, 33, 647, 611)`, `(1464, 33, 641, 611)` in
   `sceneMain`. The runs were measured off the strip's alpha channel because the stars are
   hand-painted and no two are the same width, so they cannot be re-derived as equal thirds.
   Express as fractions of the bitmap first, or leave the file alone.
4. `hook.png` derives its drawn height from its own aspect: `hookHeight = hookWidth * (2136.0 /
   154.0)` in `LevelData.kt`. The literal is the file's current cropped size, so resampling to a
   different aspect silently stretches the chain. Same fix as #1 - pin the ratio, or leave it.
   (It is 154x2136, a shape no sane POT rounds well; low priority either way at 0.3 Mpx.)
5. Decide mipmaps-vs-atlas BEFORE building either. On an atlas page, higher mip levels average
   across slice boundaries and bleed neighbours into one another; doing both needs gutters sized
   for the whole mip chain. This interacts with the deferred atlas item above.

**Per-asset results** (drawn size is virtual units; target is POT >= the 3x device size).
"was -> is" is texture memory at 4 bytes/px. Exact drawn sizes, not the estimates this table
originally carried - `fence1Width = 151.0`, `fence2Width = 172.0`, `fenceHeight = 140.0`, truck
`38+45+179 = 262` wide by `truckBedHeight = 96`, `moveRadius = 54` and `actionRadius = 48` doubled:

| asset | source | drawn | @3x | now | was -> is |
|---|---|---|---|---|---|
| `barrel.png` | 832x1274 | 32x48 | 96x144 | 128x256 | 4.04 -> 0.13 MB |
| `crate.png` | 851x595 | 68x48 | 204x144 | 256x256 | 1.93 -> 0.25 MB |
| `fence.png` | 1225x1134 | 151x140 | 453x420 | 512x512 | 5.56 -> 1.05 MB |
| `fence2.png` | 1289x1007 | 172x140 | 516x420 | 512x512 | 5.19 -> 1.05 MB |
| `truck.png` | 1683x617 | 262x96 | 786x288 | 1024x512 | 4.15 -> 2.10 MB |
| `left.png` | 1202x1194 | 108x108 | 324x324 | 512x512 | 5.47 -> 1.00 MB |
| `right.png` | 1083x1083 | 108x108 | 324x324 | 512x512 | 4.47 -> 1.00 MB |
| `jump.png` | 1261x1247 | 96x96 | 288x288 | 512x512 | 6.00 -> 1.00 MB |
| `crouch.png` | 1268x1241 | 96x96 | 288x288 | 512x512 | 6.00 -> 1.00 MB |
| `interact.png` | 1267x1241 | 96x96 | 288x288 | 512x512 | 6.00 -> 1.00 MB |

**Actual: 12.62 Mpx -> 2.46 Mpx, 50.5 MB -> 9.8 MB, 40.6 MB back** (~13 MB with mipmaps, so
~37 MB net). On disk 10.2 MB -> 2.1 MB.

**One deliberate deviation from "always round UP".** `fence2.png` needs 516 device px of width and
got 512, not 1024. Rounding up would have cost an extra 1 MB to gain 0.8% more horizontal
resolution. It measured as the *largest* error of the ten and was still 0.60/255 - invisible. The
refined rule: round up, unless you are within a couple of percent of the lower POT, in which case
take it. `truck.png` at 786 needed is NOT such a case - that one genuinely rounds to 1024.

**`truck.png` grew on disk, 8 KB -> 27 KB, and that is fine.** It is near-flat silhouette art that
compressed absurdly well at 1683x617; resampling introduces smooth gradients that PNG cannot pack
as tightly. Texture memory - the thing that actually causes the stutter - still halved, 4.15 MB ->
2.10 MB, because GPU cost is `width * height * 4` and takes no notice of how well the file zips.
Do not "optimise" this back by reverting it.

**DO NOT SHRINK these - they are already at or below device resolution at 1440p**, and this is the
non-obvious half of the job. Measured, not assumed:
- `bgmg2/3/4/5/6.png` (~1992x724) fill 480 virtual units = **1440 device px** tall, so they are
  already upscaled ~2x. They are also tiled edge-to-edge with a 1px overlap (`size(tileW + 1.0,
  canvasH)`) over a hand-healed seam - resampling can disturb the left/right edge continuity that
  makes the tiling invisible. See "Asset prep techniques" for how that seam was made.
- `loadingbg.png` / `logo_main.png` (2172x724) are near-fullscreen; 3120x1440 device pixels.
- `dossier_paper.png` (1200x800) draws at 624x416 virtual = 1872x1248 device.
- `success3.png` (1536x1024) draws at ~662x442 virtual = **1987x1325 device** - it is already
  being upscaled ~1.3x, so it is under-resolution, not over. It also feeds `winCardAspect` from
  its own `width/height` at runtime, which sets the whole MISSION SUCCESSFUL card's shape.
- `button1-4.png` (~677x167) draw at 300x52 virtual = 900x156 device - already under-resolution
  horizontally. If anything these want to be bigger.

**Resampling hygiene:** use a premultiplied-alpha-aware resampler, or silhouette art with hard
alpha edges picks up fringes bled from the RGB of fully-transparent pixels. And `truck.png` /
`entrance.png` are **pre-mirrored on disk** (deliberate - a negative `scaleX` corrupts detailed
images on this GL backend, see "Real device bugs" #8); any tool that normalises orientation would
silently undo that fix.

### Adding new art: shrink it on the way in, not in a cleanup pass later

**This is a standing rule, not a one-off.** Item 1 above existed only because ~50 MB of
over-resolution art accumulated one innocent-looking file at a time. Every asset added from here
gets the same treatment when it is added, which costs about a minute per file and never again
needs a dedicated pass.

**The procedure, whenever a PNG lands in `resources/`:**

1. **Find the size it is DRAWN at**, in virtual units - the `size(w, h)` on its `image()` call, or
   the `Rect` it is drawn into. Not the size it was painted at.
2. **Multiply by 3.** The virtual canvas is 1040x480; a 1440p phone renders it at 3x. This is the
   step that is easy to skip and impossible to notice on desktop, where the window is smaller than
   the phone - art sized off the virtual numbers is a third of the resolution it needs, and it
   looks fine locally and mushy on the device.
3. **Round to a power of two** - up, unless the lower POT is within a couple of percent (see the
   `fence2.png` note above). POT in BOTH dimensions is what makes mipmaps build at all.
4. **Resample, never pad.** Transparent padding becomes part of the image and gets stretched into
   the draw box with the art, shrinking the visible content inside its own frame.
5. **Run the tool**: `python tools/art/pot_resize.py resources/newthing.png 512 512`. It does
   premultiplied-alpha-correct LANCZOS and refuses non-POT targets. `--check` reports current
   sizes without touching anything.
6. **Verify what reaches the screen**, not what is in the file: render old and new into the exact
   device-pixel box and diff them. Under ~1/255 mean is invisible. Item 1's ten all came in under
   0.6. A screenshot alone will not catch a subtle alpha fringe.

**Do NOT shrink an asset that is drawn at or above its own resolution.** Backgrounds, full-screen
art and wide UI strips are usually already being upscaled - shrinking those makes the game look
worse and saves nothing. Measure before assuming; the exclusion list above is what that measuring
found, and it was the half that was nearly got wrong.

**Aspect ratio is NOT a reason to avoid resizing.** Every asset here is drawn with an explicit
`size(w, h)`, so the whole image is stretched into a box whose dimensions come from code. The
file's own aspect never reaches the screen. (This was stated backwards twice while working item 1
out - a "24% squash" that does not exist. The real constraint is rule 4, padding, not aspect.)

**Then wire it up in `SceneAssets`:**

- `SceneAssets.bitmap("newthing.png")` - the default, `minified = true`. Asks for mipmaps, and
  prints a one-line warning if the asset is not POT.
- `SceneAssets.bitmap("newthing.png", minified = false)` - for anything drawn at ~1:1 or larger
  (backgrounds, loading screen, dossier, button strips), and for anything **sub-sliced**, because
  mip levels average across slice boundaries and bleed neighbouring cutouts together.

**The guardrail.** `SceneAssets.warnIfNotPowerOfTwo` prints one line per offending asset per run:

```
[SceneAssets] 'hook.png' is 154x2136 - NOT power-of-two, so mipmaps are silently skipped for it.
```

This exists because KorGE's POT check (`AGTexture.doMipmaps`) fails **silently** - `mipmaps(true)`
simply does nothing and nothing anywhere says so, which is how every asset in this project went its
whole life without mipmaps while the code looked like it had asked for them. If a new asset shows
up in that output, it is over-resolution; fix it or mark it `minified = false` with a reason.

**Currently expected output: exactly one line, for `hook.png`.** That one is a genuine outstanding
candidate (154x2136, 0.33 Mpx, drawn much smaller) left alone because its extreme aspect rounds
badly to POT and the win is ~1 MB. If you ever see a SECOND line, something new needs sizing.

**Never derive a drawn size from a loaded bitmap's dimensions.** `bitmap.width / bitmap.height` at
runtime makes the stored file silently load-bearing - resample it and geometry moves. Write the
number as a literal with a comment naming the file's authored size, the way `entranceWidth`,
`exitFenceWidth` and `hookHeight` now do. Same for **sub-slice coordinates**: hardcoded pixel rects
(`chainedcrate`, `stars`) pin their file's dimensions forever. Express them as fractions, or accept
that the asset can never be resized.

**One comment goes stale:** `GameWorld.kt`'s `barrelWidth = 32.0` is annotated "matches
barrel.png's tight-cropped aspect ratio (832x1274) at this height". Rendering will not change, but
that note becomes false, and anyone re-deriving the box from the asset later would get a different
number. Update it in the same pass.

**ASSET AUDIT TRAP, learned here - do not grep by filename alone.** A first pass flagged six
`sfx/*.wav` files as unreferenced because no source file contains the string `step_a.wav`.
They are all live: `GameAudio.load()` builds `"sfx/$name.wav"` from a bare `clip("step_a")`
argument at runtime. The same shape appears in `LevelData.resolvedBackgroundImage`. Before
deleting any asset, check for **runtime-constructed paths**, not just literals - and prefer
verifying by running the game over trusting a grep.

**Verified**: `compileKotlinJvm` and the full `jvmTest` suite green (69/69), **and the JVM desktop
build was run and screenshotted after both passes** - background, fence, truck, player, all five
touch-control textures, the powerup chips with their counts, the Bebas HUD font and the handwritten
tutorial callout all render exactly as before, confirming nothing deleted was actually in use.
**Not verified**: never run on a real device or emulator; the actual frame-time improvement is
unmeasured. That is the only outstanding gap - Android *compilation* is fine (see the trap below).

**TRAP - do not use the root build to check Android compilation.** `:korge-ldtk:compileDebugKotlinAndroid`
in THIS build fails with "Inconsistent JVM-target compatibility ... 'compileDebugJavaWithJavac' (1.8)
and 'compileDebugKotlinAndroid' (21)" under `JAVA_HOME` = JDK 21, identically on unmodified checkouts.
It was briefly written up here as a blocker on shipping. **It is not, and that entry was wrong.**
`:korge-ldtk` is a KorGE-generated module that **nothing on the Android path ever builds**. The real
app is `android-shell/`, a fully separate Gradle build (see the settings.gradle.kts note above): it
compiles the game straight from source via `kotlin.srcDirs("../src/game/scene")`, takes assets via
`assets.srcDirs("../resources")`, and resolves KorGE from Maven Central - it never invokes this
build's Android target. CI is `./gradlew :paywall-build:publishToMavenLocal` then
`cd android-shell && ./gradlew bundleRelease`, and that is the only sequence that matters.
**To check that a change to `src/game/**` compiles for Android, build `android-shell`, not the root.**

## Keep this file up to date

This file is the first thing a new chat/agent should read for project
context. Whenever you make a decision, discover a constraint, or change
something a future chat starting from scratch would need to know (tooling
gaps, CI status, build pipeline quirks, unresolved integration issues,
repo/URLs), update the relevant section below — or add a new one — before
ending your turn. Treat stale info here as a bug: if something below turns
out to be wrong or superseded, fix it in place rather than leaving it for
the next chat to rediscover. Prefer editing the current-state description
over appending another dated "pass" entry — this file was consolidated on
2026-09-08 specifically to stop multi-round tuning sagas (geometry, spacing,
grey-screen debugging) from accumulating as separate entries; keep it that
way by updating the relevant section in place instead of appending a new one.
