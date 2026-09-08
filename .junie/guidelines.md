# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game, visually similar to Shadow Fight / Vector,
with a heist/infiltration objective similar to Robbery Bob.

**Target platforms: Android and iOS. Both are required — this is a
cross-platform Kotlin Multiplatform hackathon submission (Shipaton 2026),
and the app must work on both platforms.**

**iOS CI status (2026-08-25): GREEN.** `ios-build.yml` run #16 (commit
`991a54a`) completed with real `BUILD SUCCEEDED`/`BUILD SUCCESSFUL` — a
complete, unsigned `.app` for iOS Simulator, through Kotlin/Native
compile+link, KorGE's XcodeGen project generation, and `xcodebuild` with
ad-hoc simulator signing. Don't assume it's still broken — check the
latest Actions run before redoing investigation:
https://github.com/MalithaBandara/infiltrate-shadow-heist/actions
Cosmetic: the built app is named `unnamed.app` because `build.gradle.kts`'s
`korge {}` block only sets `id`, never `name` (easy fix: `korge { name = "..." }`).

A later commit (`d7ab110`) briefly broke iOS-only compilation by introducing
Java `String.format()` calls (`LevelSelectScene.kt`) with no Kotlin/Native
implementation; fixed (`eeb627d`) by switching to `n.toString().padStart(2, '0')`.
**Lesson kept because it generalizes: JVM `Testing` CI going green does NOT
mean iOS is still green** — they compile different code paths, and iOS CI
sat un-rerun for two commits while a real regression existed. Always check
the iOS workflow specifically after any change, not just JVM tests.

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

**`:game`'s own dependency (Android-only, old, unchanged since 2026-08-25):**
pinned to `purchases-kmp-core:1.9.0+14.3.0` via `androidMainApi`, zero iOS
dependency. `PurchasesBridge.kt` (common + platform actuals) are still empty
stubs — no real RevenueCat API call anywhere; iOS's stub returns
`onResult(false)` from `purchase()` (an honest no-op). This version is
pinned this low because of a **klib ABI ceiling**, not choice: this
toolchain's Kotlin/Native compiler can only read klib ABI `1.8.0`.
RevenueCat's own toolchain moved to ABI `1.201.0` starting at package
`2.0.0+15.0.0` (confirmed by downloading klibs from Maven Central and
reading `unzip -p <klib> default/manifest`), and `3.5.1` is even further
ahead (ABI `2.3.0`, compiler `2.3.20`) — every version `2.0.0+15.0.0` and
above is unreadable under Kotlin 2.0.20, confirmed failing in CI. `1.9.0+14.3.0`
is the newest compatible release. If bumped again, re-check the new
version's klib manifest before assuming compatibility — Android/JVM
compiling clean is not a valid proxy for iOS klib readability.
Separately, there is still no `Podfile`/`cocoapods {}` block anywhere for
`:game`, and this pinned version needs `pod 'PurchasesHybridCommon', '14.3.0'`
linked for iOS to actually work — confirmed failing at the link step
(`ld: framework 'PurchasesHybridCommon' not found`) in a 2026-08-24 CI run.
This remains unresolved for `:game` itself.

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

**What's still NOT done**: `:game` itself is not migrated onto the `3.x`
path — still Android-only, still pinned to `1.9.0+14.3.0`. `PurchasesBridge`
stubs are untouched by this work; nothing calls into `paywall-build` from
either bridge. No real paywall UI exists in `:game`'s own KorGE scenes.
Only `iosSimulatorArm64` has ever been linked/verified — `iosArm64` (real
device) mirrors the same config by construction but has never been run.

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

**iOS: plumbing only, matching this file's existing "Native iOS shell" section's known gap** -
`AdUnitIds.ios.kt`'s real ID and `InterstitialAdBridge.kt`'s `@ObjCName(exact = true)`-exported
trigger both exist and compile, but nothing calls `InterstitialAdTrigger.requestShow()` on iOS: like
`LevelExitBridge.ios.kt` generally, there is still no Swift poll loop for "leaving gameplay" the way
`ContinueAdBridge` has one for the watch-ad-to-continue flow. Building that Swift-side wiring is a
distinct, not-yet-started follow-up, deliberately scoped out of this pass.

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
    `ContinueAdBridge`) with a real Android implementation; iOS still has no
    poll loop wired up for this (only the watch-ad flow has one).
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
  narrow (~170px), falloff-weighted blend against a Gaussian-blurred copy
  of itself (too wide/strong reads as an obvious smudge; too narrow leaves
  a hard line); (3) verify by diffing the final left/right edge columns and
  rendering a synthetic "tile join" strip.
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

## Web presence (`site/`)

A `site/` directory for Netlify deployment (e.g. `infiltrate.saysplit.app`):
`site/index.html` (landing page), `site/support/index.html` (App Store
Guideline 1.5 support page with a Netlify Form — 4 fields: Name, Email,
Category, Message; no visible email address, no platform/subject fields, no
FAQ content — an earlier note claiming otherwise was wrong and has been
corrected), `site/privacy/index.html` (privacy policy, covers on-device
storage, AdMob/UMP consent, RevenueCat, COPPA/GDPR/CCPA, Layers — see below),
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

## Layers Events SDK integration — Android only, real, linked into the APK

`app_a1f9dbc126c1c779`. Real coordinates: `io.layers:layers-android:3.3.0`
(the owner's original integration guide cited a stale/unofficial version —
verified against Layers' actual product docs before writing any code, not
their GitHub org, which turned out to be a red herring).

Same `expect`/`actual` bridge pattern as `ContinueAdBridge`/`LevelExitBridge`:
`AnalyticsBridge` in `:game` common code, no-op on every platform except a
**genuine no-op even on Android** in `:game`'s own build — the real SDK call
only happens in `android-shell`'s separate duplicate copy. This split was
forced, not stylistic: adding the real SDK to `:game`'s own `androidMainApi`
transitively pulled `androidx.lifecycle`/`androidx.work` requiring
`compileSdk 34+`, conflicting with `:game`'s own `compileSdk 33` — reverted
rather than bump `:game`'s compileSdk as an unrelated, unrequested change.

`InfiltrateApplication.kt` (android-shell's first `Application` subclass,
since Layers must init in `Application.onCreate()`) has two deliberate
deviations from the vendor's own snippet, both found by decompiling the
real `.aar` rather than trusting the docs: no manual `track("app_open")`
call (the SDK's `autoTrackAppOpen` already defaults to `true` — the vendor's
own guide would have double-counted every launch), and `environment` is
derived from `ApplicationInfo.FLAG_DEBUGGABLE` rather than hardcoded to
production (so local debug builds don't pollute production analytics).
`automaticExceptionTrackingEnabled` (true) and `consentRequired` (false) are
both left at their SDK defaults — the latter deliberately, since flipping it
would silently drop every event until a consent UI exists (none does yet);
revisit if a consent flow is built or EEA/UK distribution requires it sooner.

Real events wired into `GameplayScene.kt`: `watch_ad_continue_requested`/
`_granted`, `level_complete` (with `level_id`/`stars`/`time_taken_seconds`/`alerts`),
`mission_failed`. **Deliberately not implemented**: any purchase/sign-up
events, since there's no real purchase flow or account system to hang them
off honestly (see below) — the owner explicitly chose to skip rather than
fire a fabricated/zero-revenue event.

**Store screen has no real purchase flow yet**: `StoreScreen.kt`'s
`onPurchase` handler just calls `profileStorage.addCoins(pack.amount)` — no
RevenueCat call, no Play Billing call. Consistent with `PurchasesBridge`
being a stub elsewhere in this file.

**Verified**: full APK build succeeds and packages the real
`liblayers_core.so` native library (confirmed in the build log, not just
present on the Kotlin classpath). **Not verified**: never run on a real
device — whether it actually connects to Layers' backend and events arrive
is unconfirmed. iOS not attempted (scope was Android only, matching the
vendor's own Android/iOS-ATT doc split).

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
