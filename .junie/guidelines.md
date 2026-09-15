# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game (visually Shadow Fight / Vector, objective Robbery Bob).

**Target platforms: Android AND iOS - both required.** Cross-platform Kotlin Multiplatform
hackathon submission (Shipaton 2026). JVM desktop is for local dev/testing only.

## Read this first: verification discipline

Three rules apply project-wide and are assumed by every section below:

1. **"Compiles" is not "verified."** The dominant failure mode in this project's history is a
   change that compiles clean, sounds right, and is still wrong on a real device - several bugs
   below took 2-4 wrong theories first. Unless a section says it was confirmed on a real
   device/simulator/screenshot, treat it as compile-only.
2. **A GitHub Actions `continue-on-error: true` step's `conclusion` is not a pass/fail signal.**
   It has repeatedly reported `success` for real failures (undefined symbols, build failures,
   crashes). Always read the raw job log for the actual `BUILD SUCCESSFUL`/`BUILD FAILED`/
   exception text.
3. **Don't trust a stated tool/library version - check the repo.** Prompts have repeatedly
   claimed a KorGE/Kotlin upgrade that never happened. Verify against `gradle/libs.versions.toml`,
   `git log` and CI toolchain paths before reasoning from a version number.

**Level design: nothing should visibly float with no structure under it.** A platform, beam, or
shelf drawn hanging in open air with no leg/strut/chain reads as a bug, not a deliberate obstacle -
even when the *physics* deliberately treats it as a floating climb target (`LevelLayout.
floatingClimbTargets` - see Level 3's table and camera beam). Where the owner rejects bracing the
actual climb gap (tried and rejected there: a stretched texture, a solid block, an invisible box -
the gap itself is meant to stay open), give the platform a real support somewhere else along its
own span instead - a leg/strut planted on the ground, out of the way of the climb point and any
mounted guard/camera. `tablePlank`'s `rightLeg` and the camera beam's `cameraLeg` (both `LEVEL_3_LAYOUT`,
`LevelData.kt`) are the pattern: a real, solid, sight-blocking obstacle (`boxes` +
`LevelLayout.tableDecorations`), drawn with `table.png`'s own leg/brace crop, tucked up against the
platform's underside (`legLift`) at the end away from wherever the player climbs or a guard/camera
sits. Apply this to every new elevated platform in future levels, not just Level 3.

Corollaries that keep recurring:
- **JVM `Testing` CI green does NOT mean iOS is green.** Kotlin/JVM default-imports things
  Kotlin/Native doesn't have (`kotlin.jvm.Volatile`, Java `String.format`). Always check the iOS
  workflow after any change under `src/game/**`; when using a JVM-sounding API in shared code,
  find the `kotlin.concurrent`/multiplatform equivalent first.
- `:game`'s `compileKotlinIosSimulatorArm64` and `paywall-build`'s report `SKIPPED` / `onlyIf
  'Cross compilation should be supported on host' is false` on this Windows machine. **iOS code
  can only be compile-checked via CI**, never locally.
- Screenshots of the landscape-locked iOS app come out portrait-dimensioned with content rotated
  90 degrees. **Rotate before judging** (see "Native iOS shell").
- Before deleting an asset, grep for **runtime-constructed paths** (`"sfx/$name.wav"`,
  `resolvedBackgroundImage`), not just literal filenames - and prefer running the game.

## LOCKED WORKING CONFIGURATION (verified 2026-08-25, commit `0b958c3`)

Load-bearing for `:game`. **Do not upgrade any of these without re-running the full iOS build
in CI first** - this exact combination is the only one proven to link on iOS after a long chain
of klib-ABI / source-set failures.

- **KorGE `6.0.0`, Kotlin `2.0.20`, Gradle `8.8`, JDK `21`** (`zulu` in CI). On this Windows
  machine JDK 21 (Temurin) is installed but NOT the default `JAVA_HOME` (that's JDK 19):
  `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"` for local
  `gradlew` (`ls` for the exact patch version first - it drifts with auto-updates).
- `:game` has **zero RevenueCat dependency** (removed 2026-09-12; see "RevenueCat status").
  The original iOS link failure was fixed by removing the `iosMainApi` `purchases-kmp-core`
  dependency so the ABI-incompatible klib never entered the link graph. No framework vendoring,
  linker flags or CocoaPods exist for `:game` itself.

## Tech stack

- **KorGE** (`:game`, Kotlin 2.0.20) for gameplay only. No Compose dependency.
- **Compose Multiplatform** for all non-gameplay UI (menu, level select, store, settings) in a
  separate Gradle **composite build** `paywall-build` (`includeBuild`, Kotlin `2.4.10`). A plain
  subproject broke the whole build immediately: Gradle shares one Kotlin-Gradle-Plugin classpath
  across subprojects and KorGE's `targetIos()` eagerly touches every subproject at configuration.
- `android-shell/` is a **fully separate Gradle build** (removed from `settings.gradle.kts`):
  applying `org.jetbrains.compose` 1.12.0 (needs Kotlin >= 2.2.0) inside the root build locked to
  2.0.20 broke configuration for *every* root task. It consumes `paywall-build`'s Android artifact
  via `mavenLocal()`, compiles the game straight from source (`kotlin.srcDirs("../src/game/scene")`),
  takes assets via `assets.srcDirs("../resources")`, resolves KorGE from Maven Central.
- JS/Wasm targets stay declared in `build.gradle.kts` for local browser preview only;
  `deploy-js.yml` was removed 2026-08-25 (Pages never enabled, never a ship target).
- Payments: RevenueCat `purchases-kmp-core` only - do NOT add `purchases-kmp-ui`.

**Model sharing across the Kotlin-version boundary**: `paywall-build/build.gradle.kts` adds
`kotlin.srcDir("../src/game/model")` and compiles `GameProfile.kt`/`LevelData.kt`/`Geometry.kt`/
`Powerup.kt` etc. from source. **Standing constraint: every file under `src/game/model/` must be
pure Kotlin (stdlib only, zero `korlibs.*` imports)** so it compiles under both 2.0.20 and 2.4.10 -
enforced by `ZeroKorlibsLintTest`.

**Entry-point rule**: `src/main.kt` must always expose a parameterless `suspend fun main() =
main(emptyArray())` (KorGE's iOS bootstrap calls it with zero args) alongside
`suspend fun main(args: Array<String>)`. Never use JVM-only APIs (`System.getProperty`) in common
code - use `korlibs.io.lang.Environment["key"]` or `args.firstOrNull()`.

**Compose Resources package trap**: `paywall-build/build.gradle.kts` sets `group = "com.infiltrate"`
(so `android-shell` can reference the artifact). Compose Resources derives the generated `Res`
package from `group` when `packageOfResClass` is unset, which silently moved it and broke every
import. Pinned: `compose.resources { packageOfResClass = "paywall_build.generated.resources" }`.

**TRAP - do not use the root build to check Android compilation.** `:korge-ldtk:compileDebugKotlinAndroid`
fails in this build ("Inconsistent JVM-target compatibility ... (1.8) and ... (21)") on unmodified
checkouts. `:korge-ldtk` is a KorGE-generated module nothing on the Android path ever builds. CI is
`./gradlew :paywall-build:publishToMavenLocal` then `cd android-shell && ./gradlew bundleRelease`.
**To check a `src/game/**` change compiles for Android, build `android-shell`.**

## Secrets and credentials - CRITICAL

Before ANY commit or push, scan changed files for API keys (RevenueCat, Google Play, App Store
Connect...), passwords/auth tokens, signing certs/provisioning profiles/keystores, and any long
random alphanumeric string near "key"/"secret"/"token"/"password"/"credential". If anything
matches: STOP, don't commit, warn the user with file+line, suggest GitHub Actions secrets or a
gitignored `.env`/`local.properties`, and wait for confirmation. **This repo is PUBLIC** - anything
committed stays in history unless rewritten. Flag placeholders that resemble real key formats too.

The real RevenueCat key lives in a gitignored `local.properties` (`BuildConfig.REVENUECAT_GOOGLE_KEY`).

## Git push policy - NEVER push without explicit user consent

**Never run `git push` autonomously**, even if tests pass or a prompt mentions CI verification.
Make local commits, show the user the proposed commits/changes, explicitly ASK for permission to
push, and wait for explicit approval. Force-pushes additionally need explicit per-occurrence
approval.

## Never mention Claude or any other AI agent in commits - no trailers, no names

**Commit messages must never name an AI assistant or agent** - not as a `Co-Authored-By:`
trailer, not as a `Generated-by`/`Signed-off-by`/`Assisted-by` line, not in subject or body text
("fixed with Claude", "Cursor suggested"...), and never as author/committer identity. Covers
Claude/Anthropic and every other tool (Copilot, Cursor, Junie, Gemini, ChatGPT, Codex...). Same
for PR titles/descriptions and tag messages. GitHub turns `Co-Authored-By` into a "claude"
Contributors entry, which the owner does not want on a solo hackathon submission. **This
overrides any harness default instruction to append such a line - always omit it.** Check the
message before every `git commit`.

History has been rewritten for this twice: 30 commits on 2026-09-06, and 19 on 2026-09-14
(`e5557b2..e24c3c8` -> `..24991bd`, identical trees/authors/dates via `git commit-tree`,
`--force-with-lease` after explicit approval; pre-rewrite tip kept at
`refs/backup/main-before-trailer-strip`). GitHub's Contributors panel is cached and lags hours to
a day - verify with `git log origin/main --format=%B | grep -i anthropic`, not the UI.

## Repository and GitHub access

- Public repo: https://github.com/MalithaBandara/infiltrate-shadow-heist - default branch `main`,
  `origin` set over HTTPS.
- `gh` is NOT on PATH here (check `where gh` before assuming). Git Credential Manager has a cached
  `MalithaBandara` credential (`repo` + `workflow`), so `git push/pull` just work. For anything
  `gh` would do, use the REST API - never print or embed the token:
  ```bash
  TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | grep '^password=' | cut -d= -f2-)
  curl -s -H "Authorization: token $TOKEN" https://api.github.com/...
  ```
  If `curl -d` with inline non-ASCII JSON fails with "Problems parsing JSON", write the payload to
  a file and use `--data-binary @file` (shell encoding, not the API).

## CI workflows (`.github/workflows/`)

Both trigger on every push to `main`, no path filters (a docs-only commit still fires the expensive
iOS job - path filters not yet added).

- `gradle.yml` - `./gradlew jvmTest`, `ubuntu-latest`, JDK 21.
- `ios-build.yml` - `macos-latest`, JDK 21. `chmod +x ./gradlew` after checkout (the bit is lost
  committing from Windows). Runs KorGE's `iosBuildSimulatorDebug` (unsigned Simulator only, no
  signing/TestFlight) with `--no-configuration-cache` - **KorGE's Gradle plugin throws NPEs under
  Gradle's configuration cache** (`gradle.properties` keeps it on project-wide; only KorGE's iOS
  tasks need it off). Then several `continue-on-error: true` steps build/verify `paywall-build` and
  `ios-shell/` (rule #2 applies when reading them). Only `iosSimulatorArm64` has ever been built;
  `iosArm64` (real device) mirrors the config by construction but has never been run.

**iOS CI history - the traps, each confirmed from raw logs:**
- **`@Volatile` in commonMain** (`GameAudio.kt`, `PlayerAnimations.kt`) resolved to
  `kotlin.jvm.Volatile`, which doesn't exist on Kotlin/Native -> `Unresolved reference 'Volatile'`.
  Every push from `9acdf7c` (2026-09-08) failed while JVM CI stayed green. Fix: explicit
  `import kotlin.concurrent.Volatile`. Confirmed fixed `e5557b2` (run 34643588882).
- **Java `String.format()`** in `LevelSelectScene.kt` (`d7ab110`) - no Kotlin/Native impl. Fixed
  (`eeb627d`) with `n.toString().padStart(2, '0')`.
- **YAML-significant characters in `korge { name = ... }`**: `name = "Infiltrate: Shadow Heist"`
  broke `:prepareKotlinNativeIosProject` (`mapping values are not allowed in this context`) -
  KorGE writes `name` verbatim into a generated YAML spec as `PRODUCT_NAME: <name>` unquoted. Now
  `name = "Infiltrate - Shadow Heist"` (was `unnamed.app` before `name` was set at all). Avoid
  `: { } [ ] , & * # ? | < > = ! % @` backtick and leading `-` there.
- **`NSDate().timeIntervalSince1970`** in `paywall-build/src/iosMain/kotlin/TimeProvider.ios.kt`
  -> `Unresolved reference` under Kotlin 2.4.10, cascading into `Shell app: build` (`unable to
  resolve module dependency: 'PaywallModule'`). Adding `@OptIn(ExperimentalForeignApi::class)` did
  NOT fix it (run 34647810647). Fix: `platform.posix.time(null)`. Confirmed run 34650821928 (`0c1f53a`,
  2026-09-11) - the first run where every `ios-build.yml` step passed for real: RevenueCat spike
  `BUILD SUCCESSFUL`, `Shell app: build` `** BUILD SUCCEEDED **`, storage bridge
  `OK:coins=350:unlocked=level_1;level_2;level_4`, `TRANSITION_OK`, AdMob
  `OK:initializeCalled=true:bannerLoaded=true`.
- The storage-bridge check's expected string includes `level_5` because `GameProfile.loadFromStorage()`
  merges stored unlocks into the default set (level_5 unlocked from the start by design).

## RevenueCat status

Two unrelated version lines - don't conflate them.

**`:game`'s own dependency - REMOVED 2026-09-12.** Was `purchases-kmp-core:1.9.0+14.3.0` via
`androidMainApi`, backing a `PurchasesBridge.kt` that stayed empty stubs on every platform and was
never called (grepped before deleting). Deleted: the dependency, `PurchasesBridge.kt` + five platform
actuals, the "Check for a generated Podfile" CI step, and every stale comment. Kept for the record:
that line was pinned because of a **klib ABI ceiling** - Kotlin 2.0.20's compiler reads klib ABI
`1.8.0`; RevenueCat moved to `1.201.0` at `2.0.0+15.0.0` and `2.3.0` (compiler 2.3.20) at `3.5.1`
(confirmed via `unzip -p <klib> default/manifest`). It also needed `pod 'PurchasesHybridCommon',
'14.3.0'` for iOS with no Podfile anywhere (`ld: framework 'PurchasesHybridCommon' not found`,
2026-08-24). If `:game` ever wants purchases of its own, start from the `paywall-build` approach.

**`paywall-build`'s dependency - proven on iOS (2026-08-29).** `purchases-kmp-core:3.6.0` compiles
and links into a real `PaywallModule.framework` for `iosSimulatorArm64` with a real
`Purchases.configure(...)` call site; `3.x` bundles the native SDK via cinterop - **zero CocoaPods
for RevenueCat**. Its klib (compiled at 2.3.20) is readable by the 2.4.10 compiler (newer
Kotlin/Native reads older klibs, not the reverse). `:game` is unaffected.

**Swift compatibility-shim link fix** (`paywall-build/build.gradle.kts`): Kotlin/Native's linker
searches a stale hardcoded Xcode path for `libswiftCompatibility*` (`Undefined symbols ...
__swift_FORCE_LOAD_$_swiftCompatibility56`). Compute the real developer dir at configuration time
and add it as a linker search path per target:
```kotlin
val macDeveloperDir: String? = if (OperatingSystem.current().isMacOsX) {
    val stdout = ByteArrayOutputStream()
    exec { commandLine("xcode-select", "-p"); standardOutput = stdout }
    stdout.toString().trim()
} else null
fun swiftLibPath(sdkName: String): String? =
    macDeveloperDir?.let { "$it/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$sdkName" }
```
then `swiftLibPath("iphoneos"/"iphonesimulator")?.let { linkerOpts += listOf("-L$it") }` inside the
framework block (now the `cocoapods { framework { ... } }` one - see AdMob). Re-check if the runner
image bumps Xcode.

**Real billing** lives in `paywall-build/src/commonMain/kotlin/com/infiltrate/billing/StoreBilling.kt`
(`expect`/`actual`), calling `Purchases.sharedInstance.purchase(...)` from `StoreScreen.kt`'s coin
packs. **Android wired** (`InfiltrateApplication.kt` -> `StoreBilling.initialize(BuildConfig.
REVENUECAT_GOOGLE_KEY)`). **iOS NOT wired** - `StoreBilling.ios.kt` exists but nothing in
`ios-shell/` calls `StoreBilling.initialize(...)`. On success `profileStorage.addCoins(pack.amount)`
credits a plain local integer: RevenueCat validates the money, local storage owns the balance.
RevenueCat's Virtual Currency ledger is **deliberately not used** - most grants are gameplay-driven
(level completion) and there is no backend; every level reward would become a network call in a
single-player game meant to work offline. No paywall UI exists in `:game`'s KorGE scenes.

## AdMob / ads

**Library: `app.lexilabs.basic:basic-ads`** (the other KMP AdMob wrappers were 1-3 star personal
projects with no Maven Central publication). **Proven on iOS**: run `33559815333` -
`OK:initializeCalled=true:bannerLoaded=true`, a real Google-served banner on a real Simulator.
Android runtime untested (no emulator here). `basic-ads` publishes Android/iOS only (no `jvm()`),
which is why every ad host/bridge is `expect`/`actual` or per-platform.

**The CocoaPods gotcha** (`paywall-build/build.gradle.kts`): `basic-ads` needs
`pod("Google-Mobile-Ads-SDK")`. A manually-declared `binaries.framework {}` alongside the
`native-cocoapods` plugin fetched the pod but still failed `ld: framework 'GoogleMobileAds' not
found`: `KotlinCocoapodsPlugin.configureLinkingOptions()` attaches pod search paths only to the
**one framework it auto-creates per target**. Fix - configure that one in place:
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
    // NOT: iosArm64 { binaries.framework { ... } }
}
```

### Ad units (real IDs, one AdMob app per platform)

| | Android | iOS |
|---|---|---|
| App ID | `ca-app-pub-7912148730700666~8824437805` | `ca-app-pub-7912148730700666~1768074863` |
| `REWARDED_CONTINUE` | `ca-app-pub-7912148730700666/8683118378` | `ca-app-pub-7912148730700666/9506964083` |
| `REWARDED_COINS` ("Coins Reward") | `ca-app-pub-7912148730700666/8440619376` | `ca-app-pub-7912148730700666/4233781051` |
| `REWARDED_GADGET` ("Gadget Reward") | `ca-app-pub-7912148730700666/9048379643` | `ca-app-pub-7912148730700666/6397813920` |
| `INTERSTITIAL_LEVEL_EXIT` | `ca-app-pub-7912148730700666/7390779081` | `ca-app-pub-7912148730700666/5874999932` |

Each placement has its own unit for reporting/frequency-cap
granularity. JVM falls back to Google's published test IDs - the rewarded and interstitial test IDs
differ (`ca-app-pub-3940256099942544/1033173712` is the interstitial one); don't reuse one for the other.

**All placements currently point at Google's TEST IDs**: `AdUnitIds.android.kt`/`AdUnitIds.ios.kt`
each have `private const val USE_TEST_ADS = true` gating every placement. Deliberate: Play Console
Internal/Closed testing and TestFlight/App Review count as developer-associated traffic under AdMob's
invalid-traffic policy. **Before an Open testing or production build, grep `USE_TEST_ADS = true` in
both files and flip both** - not automatic. (One flag per file replaced an older per-value swap that
sat unnoticed.)

### Watch ad to continue (`REWARDED_CONTINUE`)

`:game` (2.0.20, no Compose) cannot call `basic-ads`; `paywall-build` can. On iOS the two are
separately-compiled Kotlin/Native frameworks with no interop, so the request crosses via Swift
polling bridge objects: `GameplayScene` -> `GameContinueAdBridge` (`:game`) -> polled by
`AppDelegate.swift` -> `switchToCompose()` + `ContinueAdTrigger.requestShow()` (`paywall-build`) ->
ad -> `ContinueAdTrigger.markRewardEarned()` -> polled -> `GameContinueAdBridge.grantContinue()` +
`switchToKorGE()` -> `GameplayScene` sees `consumeContinueGranted()` and restarts the level (same as
RETRY - owner's explicit scope). On Android everything runs in one process, so
`ContinueAdBridge.android.kt` is a plain shared object. The MISSION FAILED card has three buttons at the bottom: **CONTINUE** (leftmost, watch-ad clapper icon), **RETRY** (bold circular reload arrow), and **MAIN MENU** (silhouette home icon), sized at 165x54px (upgraded from 44px); the victory overlay similarly features 54px buttons (**RETRY**, **MAIN MENU**, **NEXT MISSION** with double forward arrows); a failed ad never strands the player. Verified: JVM + Android compile. Never run on a device.

### Ad preloading (2026-09-10) and its two hazards

`ContinueAdContent` (Android + iOS) and `InterstitialAdContent` (Android) hoist `rememberRewardedAd`/
`rememberInterstitialAd` OUTSIDE the `showRequested` gate so the fetch starts at first composition.
`rememberXAd` re-loads whenever the handler is `NONE` or `DISMISSED`. **There is no
`InterstitialAd(loadedAd = ...)` overload** - preloading is done by hoisting, not a different API.

- **Hazard 1: a background failure must not resolve a request never made.** `onAdClosed()`/
  `cancelShow()` set `outcomeFinished`, which the scene / Swift loop reads as "flow ended". Every
  hoisted load-failure callback is guarded on `showRequested.value`.
- **Hazard 2: `FAILING` is a dead end.** `rememberXAd` only re-loads from `NONE`/`DISMISSED`
  (basic-ads 1.2.1 sources - no branch for `FAILING`). With preloading one early failure leaves the
  handler dead for the process and a later request gets no ad and no resolution. Every show site
  has an explicit `AdState.FAILING ->` branch that resolves the trigger like a load failure.
- **Test ads cannot reproduce either hazard** (always fill, never fail) - force with airplane mode.
- Store's `CoinsRewardAdHost`/`GadgetRewardAdHost` are NOT preloaded (gated at the call site in
  `StoreScreen.kt`; a menu button tolerates a wait). iOS interstitial: see below.

### Watch ad for coins (Store, `REWARDED_COINS`)

Previously an instant unlimited free-coins button. Now a real ad with a daily cap:
- **`CoinsAdLimiter`** (`paywall-build/src/commonMain/kotlin/CoinsAdLimiter.kt`): `MAX_WATCHES_PER_DAY = 5`
  per UTC-epoch day, keys `user_coin_ad_day_bucket`/`user_coin_ad_watch_count` via the same
  `getRaw`/`setRaw` bridge as `GameProfileStorage`. 250 coins/watch (`coins_ad` in `StoreScreen.kt`);
  5x250 = 1250/day, a bit above the $0.99/1000 pack if watched daily - a real but bounded alternative.
  AdMob dashboard cap 10/day is a backstop against modified clients, not the mechanism. UI shows
  "WATCH AD (N LEFT)" / "COME BACK TOMORROW". If 250/5 change, update this paragraph in place.
- **`CoinsRewardAdHost`** (`expect`/`actual`) hosts `RewardedAd`; no poll bridge needed - tap and ad
  are in the same Compose tree, `onRewardEarned` calls `addCoins` + `recordWatch()` directly.

### Watch ad for a random gadget (Store, `REWARDED_GADGET`)

Sixth Store card ("MYSTERY GADGET") in the 2x3 POWER-UPS grid. Grants one of the five real
`PowerupType`s via `List.random()` chosen at request time (fixed before the ad shows), granted with
`profileStorage.buyPowerup(type.id, cost = 0)` (`spendCoins(0)` always succeeds). Draws a vector die
icon (`drawMysteryDiceIcon`). **Not `PowerupType.PROTOTYPE`** - that's a separate in-progress sixth
gadget type (real id/cost/timer, no world effect, not in the Store); `gadget_prototype.png` is its.
**`GadgetAdLimiter`**: separate class (precedent: `InterstitialAdLimiter`), `MAX_WATCHES_PER_DAY = 3`
(a random gadget averages ~400 coins of value vs the coin card's 250), keys
`user_gadget_ad_day_bucket`/`user_gadget_ad_watch_count`. **`GadgetRewardAdHost`** is a near-copy of
`CoinsRewardAdHost` - one file per placement is the convention.

### Level-exit interstitial (`INTERSTITIAL_LEVEL_EXIT`)

Triggered by `LevelExitBridge.requestReturnToMenu()` (QUIT/RETURN TO MENU/MAIN MENU/ALL CLEAR).
NEXT LEVEL deliberately does not show one (an ad on every win was ruled too aggressive).

**Gating (`InterstitialAdLimiter`, commonMain)** - all must hold:
- `totalLevelsCompleted >= 2` (`GameProfile.totalLevelsCompleted`, key `user_total_levels_completed`,
  incremented once per *distinct* level in `GameplayScene`'s `world.onLevelComplete` by checking
  `levelStorage.getBestResult(id)?.completed` *before* `saveResult()`). Level 1 is the tutorial,
  level 2 builds a habit before any monetization interruption.
- `!profile.isPremium` - the Remove Ads purchase promises "removes all banner and interstitial
  advertisements"; this is the first placement that promise had to be true for.
- In-memory 180s cooldown seeded from app launch (a cold start always gets 3 min grace) and a
  5-per-session cap (resets on cold launch). The owner chose NOT to enable an AdMob dashboard cap
  on this unit.

**Trigger shape**: plain per-platform files (`paywall-build/src/androidMain/kotlin/InterstitialAdBridge.kt`,
`.../iosMain/kotlin/InterstitialAdBridge.kt`), not `expect`/`actual` - only ever invoked from
platform-native host code. Uses `basic-ads`' real `InterstitialAd(adUnitId, onDismissed, onShown,
onImpression, onClick, onFailure, onLoad)` (verified from `basic-ads-1.2.1-sources.jar`).
**Android**: `MainActivity.kt`'s `AndroidLevelExitBridgeState.onReturnToMenuRequested` flips
`showingGameplay.value = false` and separately calls `maybeShowLevelExitInterstitial()` - the menu
flip is never gated on the ad. **iOS (wired 2026-09-12)**: `GameLevelExitBridge` (`@ObjCName(exact =
true)`, `src@ios/LevelExitBridge.ios.kt`) replaced a true no-op - before this, QUIT/RETURN TO MENU on
iOS silently did nothing at all. `AppDelegate.swift`'s `startObservingLevelEnd()` polls
`consumeReturnToMenuRequest()`, switches to Compose, calls `InterstitialAdTrigger.maybeRequestShow(
totalLevelsCompleted:isPremium:)` (checks the limiter itself; profile fields via `DebugStorageBridge.
readTotalLevelsCompletedForDebug()`/`readIsPremiumForDebug()`). `InterstitialAdContent()` is composed in
`MainMenuComposeViewController.kt`. The iOS content is gated on `showRequested` (load-on-demand) with
both hazard branches - if it's ever hoisted to preload, keep the guards (`InterstitialAdBridge.kt`'s
comment covers this). **Never run on a real device on either platform** - whether it shows at the right
moment, respects the gate/cooldown, and is suppressed for premium are all unconfirmed. iOS not CI-verified.

## Shared storage bridge: `paywall-build` <-> `:game`

Read from KorGE 6.0.0's own source:
- **iOS** (`DarwinNativeStorage`): `NSUserDefaults(suiteName = "korge")` - a named suite, keys
  prefixed `"org.korge.storage."`. Same plist for any code in the same app sandbox, so no App Group
  is needed while `PaywallModule.framework` is embedded in the same app.
- **Android** (`NativeStorage`): `SharedPreferences("KorgeNativeStorage", MODE_PRIVATE)`, unprefixed.
  **No `paywall-build` Android storage impl exists - deliberate** (`:game` talks to Android storage
  directly; no boundary to bridge). If ever needed, target that same file.

`paywall-build/src/iosMain/kotlin/PaywallStorage.kt` implements `getRaw`/`setRaw`/`removeRaw`;
`KorgeStorageKey.kt` is a pure JVM-testable `iosKey()` helper. Verified by `StorageKeyCompatibilityTest`
(4/4) and a real on-device round trip in CI. Persisted keys: `user_coins`, `user_is_premium`,
`user_music_vol`, `user_sfx_vol`, `user_controls_swapped`, `user_language`, `user_unlocked_levels`,
`user_powerups`, `user_total_levels_completed`, `level_result_<levelId>`, `level_results_ids`, plus the
ad-limiter keys above. (Whether `user_powerups` is actually persisted was never explicitly reconciled -
check `MapBackedGameProfileStorage` before assuming.)

## Native iOS shell (`ios-shell/`) - WORKING on real Simulator CI

Hand-authored XcodeGen project (`ios-shell/project.yml`) embedding `:game`'s `GameMain.framework`
and `paywall-build`'s `PaywallModule.framework` in one process. Confirmed (run `33385051973`):
clean link/codesign, `Storage bridge result: OK` from a real PaywallModule-writes/GameMain-reads
round trip. It is separate from KorGE's own generated `build/platforms/ios` and not wired into any
release pipeline - no decision yet on how these converge for shipping.

**Why the triggers live in Swift**: `:game` (2.0.20) and `paywall-build` (2.4.10) produce
ABI-incompatible klibs, so Kotlin can't call across; Swift calls both frameworks' exported ObjC APIs.
Same reason every bridge is poll-based.

**Settled - don't re-litigate without a new reason:**
1. Duplicate Kotlin/Native runtime symbols (each framework embeds its own) - did not occur for this
   pair. Re-verify if a *third* Kotlin/Native framework is ever added.
2. `@ObjCName(name = "X")` without `exact = true` keeps the framework prefix on the linked symbol ->
   undefined symbols from Swift. Use `@ObjCName(name = "X", exact = true)` +
   `@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)` on every Swift-visible object.
3. `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` - the frameworks are arm64-only.
4. **`CADisableMinimumFrameDurationOnPhone: true` in Info.plist is mandatory** - Compose's
   `PlistSanityCheck` hard-aborts (`SIGABRT`) without it, on a delayed low-priority queue, so a fast
   CI step can miss it. Any other Xcode project hosting Compose needs it too.

**Info.plist keys** (all in `project.yml`): `UISupportedInterfaceOrientations` landscape only
(`c0ecff9`; every Compose screen assumes 720 is the SHORT dimension - portrait squished everything
into a column); `AppDelegate.swift`'s `supportedInterfaceOrientationsFor` also forces `.landscape`
(redundant belt-and-suspenders); `UIStatusBarHidden: true` + `UIViewControllerBasedStatusBarAppearance:
false` (the latter defaults to `true`, which makes the global key get ignored; neither Compose's nor
KorGE's view controller overrides `prefersStatusBarHidden`). Confirmed from CI screenshots. **The black
rounded shape at the top is the Dynamic Island** of whichever iPhone CI picks - not removable by the app.

**Screenshot trap (cost real time twice)**: `xcrun simctl io screenshot` and limrun captures are the
raw portrait-shaped panel buffer with landscape content rotated 90 degrees inside it. Rotate
(`img.rotate(90, expand=True)`) before concluding a layout is broken.

**Debug overlay bug**: `addDebugOverlay()` added a `UIButton` at `(12, 44, 220, 36)` + label directly to
the `UIWindow`, above every screen forever, overlapping `MenuTopBar`'s back button - "back doesn't work
in Store/Settings". Removed outright (CI reads `storage_bridge_result.txt` from disk via `simctl
get_app_container`, written by `runStorageBridgeCheck()` regardless). If a manual re-trigger is ever
needed, gate it behind a debug flag and keep it away from the top-left corner.

**Entry point**: `ShellAppDelegate.ios.kt` was still wired to `spikeMain()` (the purple
`SwitchSpikeScene` debug scene) long after the spike - every level launch on iOS showed it. Now
`gameMain()` (`GameEntry.ios.kt`): commonMain's `main()` picks one level from
`Environment["startLevel"]`/args, which iOS never sets, so like `android-shell/MainActivity.kt`'s
`activeSceneContainer`, `GameLevelStartBridge` (`@ObjCName(exact = true)`) captures the
`SceneContainer` and exposes `startLevel(levelId:)`; `AppDelegate.swift` uses `MainMenuComposeScreen`'s
level-aware `makeViewController` overload and calls it before `switchToKorGE()`. Not yet observed
whether picking a second, different level re-targets correctly.

**`resources/` bundling (fixed `bc9e494`)**: `GameMain.framework` (KorGE's `iosBuildSimulatorDebug`)
never embeds the root `resources/` folder - only KorGE's own generated Xcode project has that copy
phase. Gameplay showed a "LEVEL LOAD FAILED" screen with `korlibs.io.lang.IOException: File case not
matched pathExpected=.../ShellApp.app/player/idle/0001.png != pathResolved=.../ShellApp.app/player`.
**"case not matched" is misleading**: korlibs' `resolveOrError()` calls `realpath()` and compares
only the final segment; Darwin returns a best-effort prefix for a missing intermediate directory. On
iOS `resourcesVfs` resolves against `NSBundle.mainBundle`'s root (`StandardBasePathsDarwin.
executableFolder`), so resources must land flat in `ShellApp.app/` (not under `resources/`). Fix: a
`postbuildScripts` rsync of `resources/`'s *contents* (trailing slash on source) into the built bundle.
Gameplay audio/art load silently as `null` when missing (`GameplayScene` catches each load), which is
why the gap went unnoticed. Check the latest run's `gameplay_check.png` if this is doubted.

**Gameplay screenshot handshake in CI** (`TRANSITION_OK` alone doesn't prove gameplay rendered - it
calls `SpikeBridge.shared.requestLevelEnd()` directly): `switchToKorGE()` writes `korge_visible.txt`;
CI polls for it (90s - 15s timed out because cold boot+install+launch can take minutes), screenshots,
then writes `screenshot_taken.txt`; `AppDelegate.swift`'s dwell polls for that file (20s cap) before
ending the level. Needed because `simctl io screenshot` itself takes 1-13s on this runner. Blind
sleeps (2.5s, then six spread over 2-6s) never hit the window.

**Compose/KorGE view-switching spike (viable - build around it)**: swapping `window.rootViewController`
between a Compose screen and the warm KorGE `ViewController` 6 times: switch-to-KorGE latency well
under 500ms cold, 60-120ms warm; **`hiddenDwellTicksAdvanced` = 0 every cycle** - KorGE's render loop
genuinely stops while its view is out of the window (no pause plumbing needed on iOS; NOT true on
Android, see bug #7); memory showed no monotonic growth over 6 cycles (small sample). Visual flash
check inconclusive.

## `korge-video`: NOT VIABLE

Tested and fully reverted. Stale (last real commit 2023), doesn't compile against KorGE 6.0.0, and
its iOS backend is an empty stub that falls back to a fake generated video. If video is wanted,
re-encode as a low-fps PNG/JPEG frame sequence or sprite sheet through KorGE's normal APIs.

## Non-gameplay UI in Compose - status

MainMenu, LevelSelect, Store, Settings are real Compose screens in `paywall-build`. KorGE is entered
via `rootViewController` swap on iOS. On Android the planned "warm engine" (one Activity, `FrameLayout`
holding both views, toggle visibility) is NOT what ships - hiding `KorgeAndroidView` tears down its
surface (bug #7), so the Compose menu draws opaquely on top of an always-visible KorGE view.

## Gameplay architecture (`commonMain`)

- `game.model` (engine-agnostic, pure Kotlin): `Geometry.kt` (raycasting, LOS), `Player.kt` (96x50
  hitbox, jump/gravity/platform snapping, sub-stepped AABB collision, `NoiseLevel`, crouch),
  `Guard.kt` (waypoint patrol, PATROL/INVESTIGATING, pause/hold options), `Vision.kt` (FOV polygon +
  detection), `Camera.kt`, `MovingPlatform.kt`, `Conveyor.kt`, `Laser.kt`, `LevelData.kt`
  (`LevelData`/`LevelLayout`/`LevelResult`/`LevelRegistry`/`LevelStorage`, all level definitions with
  their reasoning in doc comments), `GameProfile.kt`, `Powerup.kt`, `GameWorld.kt` (orchestration,
  detection/alert/noise, exit/win).
- `game.scene` (KorGE): `UiComponents.kt` (UI constants + GPU vector helpers), `PlayerAnimations.kt`
  / `GuardAnimations.kt` (atlas sprite animation), `LightConeView.kt`, `SceneAssets.kt` (process-wide
  bitmap/font cache), `GameplayScene.kt` (HUD, touch controls, overlays, parallax, level rendering).
- Collision is `Rect`-vs-`Rect` everywhere. **Pixel-perfect hitboxes were explicitly decided
  against** (two parallel collision systems); `footWidth` narrowing is the intended answer to "the box
  is bigger than what you see" - tune per case.
- `dtSec` is clamped to 0.1s and `Player.update` sub-steps at 1/60, so a 100ms hitch runs six
  physics steps - slow frames make themselves slower.
- Levels: `01: Night Arrival` (`DEFAULT_LEVEL_1`, tutorial), `02: Cargo Yard` (`LEVEL_2_LAYOUT`,
  `bgmg5.png`), `03: New Level` (`LEVEL_3_LAYOUT`, WIP), `04: Blind Spot` (`LEVEL_4_LAYOUT`, conveyor,
  `bgmg6.png`, darkness vignette), `05: Restricted Zone` .. `09: Old Signature` (`SIDE_SCROLL_LEVEL_LAYOUT`
  family, 48-tall guards). Other levels' backgrounds rotate through `bgmg2/3/4` via
  `LevelData.resolvedBackgroundImage`.

## End-of-run dossier sheets (MISSION FAILED / HEIST COMPLETE)

Both are the main menu's briefing sheet: `dossier_paper.png` stretched to a card, debrief in ink,
verdict as a rubber stamp, torn-paper strips stacked to its left. FAILED = SITUATION REPORT (two
columns of fields, red stamp, recon tip in the handwritten face along the foot); COMPLETE = OBJECTIVE
REVIEW (three stars over objective fields, green stamp with rating, purse along the foot). **An
earlier dark `#141416` card with hairline borders/coin pill was rejected as off-theme - don't
reintroduce dark panels, hairline strokes or the coin pill here** (that pass's `COLOR_MENU_*` palette
and `drawCoinIcon` were removed with it).

Ink side follows `MainMenuScreen.MissionDossierCard`: ink alphas 1.0/0.78/0.55/0.34 of `#17140F`,
sheet **1.5 aspect** (never stretch one axis - it pulls the torn edge), insets start 15% (folder
tab) / end 9% / top 5.5% / bottom 14%, **-5.2 degree tilt**. Stamp red/green `#96222A`/`#25603A`
and gold `#A8781A` are deliberately NOT the menu's neon `#FF2A55`/`#00E676`/`#FFD700`. **A tilted
sheet cannot carry label-left/value-right rows** - at 5.2 degrees a value climbs nearly a full row
across a column and reads as the answer to the line above. Fields are **stacked**. Knobs: `sheetH0`/
`sheetW0`, `S` (shrinks the whole group on a narrow canvas; 1.0 on the 1040x480 canvas), `dpx`/`dpy`
(page -> tilted layer, pivoted on the sheet centre). `resources/dossier_paper.png` is a second copy of
the Compose asset (no shared pipeline); it bumped `totalLoadSteps` to 20, which must match the number
of `markLoadProgress()` calls. Verified on JVM desktop over four screenshot passes; not on device.

**Screenshot recipe (reused often)**: temporarily invoke `world.onGameOver?.invoke()` /
`world.onLevelComplete?.invoke()` before the `addUpdater` block, `./gradlew runJvm`, capture from
PowerShell. `GameWorld.spottedCount`/`timeTaken` have private setters, so previews show zeros. **Call
`SetProcessDPIAware()` before `GetWindowRect`/`CopyFromScreen`** or the capture silently grabs only
the top-left ~80% on this scaled display and looks like a layout bug.

## Audio

Two systems sharing no code: **gameplay** (`GameAudio.kt`, KorGE, `resourcesVfs` from
`resources/sfx/`; a missing clip is a silent no-op) and **menus** (`ui/MenuSfx.kt`, `expect`/`actual`:
4-voice `AVAudioPlayer` pool iOS, `SoundPool` Android, JavaFX `AudioClip` desktop - all
overlap-capable). **Format: PCM s16le / 44.1kHz / mono WAV only** (iOS and JavaFX can't decode Ogg).
Shared clips (`ui_click.wav`, toast sounds) are checked in twice: `resources/sfx/` and
`ios-shell/Resources/`; Android's menu bus reads `assets/sfx/` (copied from `resources/`). Credits in
`SOUND_CREDITS` (`SettingsScreen.kt`) kept in sync with `ATTRIBUTION.md` by hand.

### Android gameplay crackle - RESOLVED 2026-09-11 (Galaxy S25 Ultra)

**Root cause**: korlibs' `Sound.play()` (`AndroidNativeSoundProvider`) constructs a **new
`AudioTrack` per call**, so every footstep opened a new audio session next to `bgmusic.mp3`'s
continuous one; this phone's "Voice Booster" DSP re-initializes on every session open, audible as a
crackle. Proven by a screen recording whose extracted audio (`ffmpeg` + a `numpy` high-frequency
energy score per 20ms window, frames pulled at the ranked timestamps) lined every click up with a
state transition - including one from starting the phone's own screen recorder in the menu.

**Four narrower fixes tried first, each insufficient** (so the ground isn't re-covered): (1) pooling
SFX through `SoundPool` - reduced, didn't close; logcat showed `SoundPool` requests the "fast" output
path per play (`AudioFlinger: createTrack_l(): mismatch between requested flags (00000004) and
output flags (00000000)`) with no API to refuse it. (2) A `MODE_STATIC` `AudioTrack` pool with
`PERFORMANCE_MODE_NONE`. (3) Keep-warm pings / a looping silence stream - reverted (the latter bled
bgmusic into the menu). (4) Matching korlibs' `AudioAttributes` (`USAGE_GAME` + `CONTENT_TYPE_UNKNOWN`)
and joining its shared session via `AndroidNativeSoundProvider.audioSessionId`/`ensureAudioManager()`.

**Actual fix - `GameSfxOutput`, a software mixer with ONE `AudioTrack`** (`MODE_STREAM`,
`PERFORMANCE_MODE_NONE`, joined to korlibs' session id defensively), opened once per process, fed by
one `THREAD_PRIORITY_URGENT_AUDIO` thread summing voices (bgmusic + one-shots) into 20ms/882-frame
buffers. Files: `src/GameSfxOutput.kt` (common interface: `prepare`/`play`, `prepareMusic`/
`setMusicVolume`/`stopMusic`), real impl `android-shell/src/main/kotlin/com/infiltrate/androidshell/
GameSfxOutput.kt`, mirrored in `src@android/GameSfxOutput.android.kt`. Every other platform returns
`null` and `GameAudio.kt` falls back to korlibs' per-call path. `bgmusic.mp3` is decoded once via
`MediaExtractor`/`MediaCodec` (44.1kHz stereo, confirmed with `ffprobe`); mixer output is fixed
44.1kHz stereo, mono SFX upmixed. **Lifecycle**: `AudioTrack.pause()`/`play()` on `MainActivity`
`onPause`/`onResume` (`AndroidGameSfxOutputState.pauseEngine()`/`resumeEngine()` via `PausableAudioEngine`)
- without it audio kept playing after leaving the app.

Two unrelated bugs fixed alongside: a background `GameplayScene` reloaded by QUIT/RETURN TO MENU was
starting bgmusic in `sceneMain()` ("menu music after quitting") - fixed with a `startDormant: Boolean`
constructor flag that skips `syncBgMusicVolume()`; and the D-pad/jump/crouch/interact buttons had a
quiet click (`HUD_TAP_GAIN`) that the old latency swallowed - **removed outright per owner feedback;
don't re-add a tap sound to `createTouchBtn`/`createImgBtn`** (deliberate presses still click).

Confirmed fixed on the S25 Ultra across several rounds; unknown on other OEMs (mechanism is
Samsung-specific by evidence) and cost unmeasured elsewhere. A KorGE-community write-up is planned.

**Bad source clip**: `climb.wav` (also used for swing launch) was 1.75s with the grunt in the first
0.66s, silence, then an unrelated three-spike burst - heard as a delayed crackle after climb/swing.
Trimmed to 0.66s with a 40ms fade (`ffmpeg -af "atrim=0:0.66,afade=t=out:st=0.62:d=0.04"`). Same
category as the earlier `takeoff.wav` removal. Only one copy exists.

## Real device bugs found and fixed (engine/platform gotchas)

1. **KorGE `Canvas` icons don't scale with density** - `drawXIcon()` in `MenuComponents.kt` plotted
   literal pixels. Fix: `size.minDimension / REFERENCE_PX` scale per icon (some already did this).
2. **A `verticalScroll` parent gives `weight()` nothing** - use `Modifier.height(IntrinsicSize.Min)`.
3. **`USAGE_ASSISTANCE_SONIFICATION` silently mutes on many phones** (system-sounds stream). Menu SFX
   switched to `USAGE_GAME`.
4. **KorGE's `.play()` builds a new `AudioTrack` per call** - perceptible delay. Mitigated by priming
   every clip once at `volume = 0.0` at load - **once per process, not per scene** (re-priming per
   reload accumulates `AudioTrack`s toward the per-process ceiling; one cause of the grey-screen bug).
   Superseded on Android by the mixer above.
5. **`PlayerAnimations.load()` reallocated a 2048x2048 atlas + re-decoded the spritesheet every scene
   load**, nothing releasing the old one -> real on-device `OutOfMemoryError` (found via on-screen
   exception diagnostics added to `sceneMain()` after three audio theories failed). Fixed with a
   `@Volatile` process-wide singleton. The other per-scene bitmap loads got the same treatment later
   (`SceneAssets`).
6. **AdMob's `onRewardEarned` fires before the ad Activity is dismissed** - reloading on reward raced
   the Activity lifecycle (the other grey-screen cause). Finish the outcome only on `onDismissed`/
   `onFailure`. `markRewardEarned()` still does NOT resolve the outcome on Android.
7. **Toggling `KorgeAndroidView` visibility tears down its `GLSurfaceView` and doesn't resume** -
   permanent grey screen. Never hide it; the Compose menu draws opaquely on top. Trade-off: KorGE
   renders under every menu on Android (battery impact unmeasured).
8. **Negative `scaleX` on a detailed KorGE `Image` corrupts rendering** on this GL backend at large
   downscale (a torn transparent smear on the truck). `truck.png` and `entrance.png` are
   **pre-mirrored on disk**; no runtime flip anywhere. Pre-mirror future detailed assets.
9. **Vertical collision seam - "flying past the end of terrain."** `Player.updateStep()` branched on
   the live `vy`; a foot span straddling two platforms resolved to the taller one until fully clear.
   Fix: capture `wasFalling = vy > 0.0` before the loop; resolve to the candidate keeping the player
   closest to their current y.
10. **A `nav_target` storage flag was written by four buttons and read nowhere** - "returning to
    menu" silently reloaded the level. Replaced by the real `LevelExitBridge` (Android impl; iOS poll
    loop 2026-09-12, see interstitial section).
11. **Settings sliders had no live effect on the Settings screen** - `NavigationRoot` re-read volume
    only on screen change. Volume state lifted into `NavigationRoot` as `mutableStateOf` with
    `onXChange` callbacks.

**Lesson**: the grey-screen symptom had two unrelated real causes (#5 dominant, #6) after three
wrong audio theories. What broke the loop was adding on-screen exception diagnostics to `sceneMain()`
- reach for that first on any "blank screen, no error" report.

## HUD: objectives panel and gadget-slot bolt (2026-09-11)

Objectives panel (`objPanel`) stays on **Bebas Neue** - Inter was tried and rejected by the owner.
`objTitle` 15; `objMainText`/`objOptTag`/`objOptText` all 12.5 (keep the three body rows equal). The
gadget slot's idle icon is `resources/gadget_bolt.png` (from `Downloads/charAnimations/assets/
lighting.png`, alpha-cropped 102x235 then resized to 32x64 POT), drawn 16x22 (deliberately wider than
the source aspect - "make it more thick"), recoloured via `colorMul` like the paper-strip buttons;
`drawPowerupIcon` remains as fallback (`slotIconImg`/`slotIconFallback`). Closing the pause-bars/bolt
gap needed the art shifted inside each 42px box, not just a smaller `slotGap`: `pauseBarsShiftLeft =
4.0`, `slotBoltShiftRight = 2.0`, `slotGap = 3.0`, cluster right inset 14 (both `pauseBtn` and `slotX`
use `canvasW - 14.0 - pauseRadius * 2.0`). Adjust the two shifts first. JVM-only verified.

## Player foot-planting (2026-09-11)

Technique: per-column alpha scan of the actual PNG frames, every frame of a clip, and bias past the
measured value in the direction of the fix.
- `GameWorld.kt` `truckFront.width` 38 -> **29** (`29/(29+45+179) = 11.46%` matches the hood-to-
  windshield step measured at ~11.2% of `truck.png`); `truckMiddle`/`truckBack` and `truckBedHeight =
  96.0` unchanged. A "floating on the truck" screenshot was a landing frame after a debug spawn - let
  the pose settle before trusting one screenshot.
- `IDLE_FEET_Y` = **245.0** (measured sole at 248 front / 255 back, stable across all 45 frames;
  over-corrected on an on-device report). `CROUCH_FEET_Y = 250.0`, `JUMP_LAND_FEET_Y = 247.0` follow
  the same method. `interactAngle` settled at 60 degrees down-right.

## Level 1 geometry - current state

`GameWorld.kt`'s `createDefault()` / `DEFAULT_LEVEL_1`, `worldWidth = 3900`: start gates -> ~130 units
ground -> `smallCrate` (48x68) -> 3-tier `truck` (`truckFront`/`truckMiddle`/`truckBack`, drawn once as
a single image over the union footprint) -> `longPlatform` (900x96, carries the hanging chained crate
the player crouches under) -> `stepDownCrate` (48x68) -> open ground -> `block2` -> seven `barrel`
boxes (32x48) tiling the `block2`->`block3` gap with zero bare ground -> `block3` -> a **permanently
disabled guard zone** (`LevelData.guardEnabled = false`: a real `Guard` parked at `x = -500`, `speed =
0`, wide patrol range so it can't drift in - reads as removed without breaking tests that read
`world.guard.*`) -> exit (`entrance.png`, booth-only crop 531x612, pre-mirrored; `exitfence.png` after
it; `exitZone` widened to the booth's visual footprint). `cameras` is empty. Every rise is exactly
48 units, under `Player.maxJumpHeight = jumpSpeed^2/(2*gravity) ~= 51.2`.

**Test-coordinate lesson (learned 4+ times)**: never hardcode corridor x-coordinates in tests that
need generic open ground - derive from `world.levelData.guardPatrolMinX/MaxX` (e.g. `guardPatrolMinX
+ 75.0`). "Occluders cleared" and "platforms cleared" are different guarantees; a leftover box in
`platforms` silently relocates the player.

**Mission dossier card spacing** (`MainMenuScreen.kt` `MissionDossierCard`, five rounds): `.offset(y =
30 * dossierScale)`, 4dp bottom padding, one flexible spacer at the foot, five fixed 6-8dp (scaled)
gaps with explicit `lineHeight` on the file-number/chapter labels. Reasoned from screenshots, not
final - those are the knobs for a sixth round.

## The swing move (`Player.kt` / `resources/player/swing`) - built 2026-09-10, currently unused

**No level currently places a swing hook** (`LevelLayout.swingHooks` is empty everywhere; the "Blind
Spot" barrel-wall + hook layout it was built for was replaced by the conveyor layout in `LEVEL_4_LAYOUT`
- see Level 4). The mechanic, clip, `hook.png`, tests and all constants remain, so a level can use it
again by populating `swingHooks`. What a future session needs:

- Entry: walk into the hook and press JUMP (same button as the climb). From a standstill it is an
  ordinary jump, deliberately - the clip opens on a push-off stride (owner-confirmed).
- **Clip**: 52 frames at 165x264 from a 200-frame 360x640 plate set: raw 59-69, 77, 114-153. Cut: the
  backswing (78-113, reads wrong after a run-up), the settle (70-76, reads as waiting), walk-in and
  run-out. Every remaining frame kept (84 fit one atlas page). Reasoning in `PlayerAnimations`'
  header.
- **Hang**: `Player.SWING_GRIP_ABOVE_CURVE`/`SWING_GRIP_AHEAD_CURVE` are the hand measured per frame;
  `advanceSwing` places the body so the hand meets the hook. The rect sits still under the hook while
  the silhouette sweeps - correct, not a bug; travel comes from launch and release.
- **Grip on the art**: `HOOK_GRIP_X/Y_FRACTION = (0.481, 0.960)` - two thirds down the hook's bell
  (rows 2040..2089 of 2136), not the lowest pixel. **Re-measure if `hook.png` is recropped**
  (`hookHeight = hookWidth * (2136.0 / 154.0)` hardcodes the same aspect).
- **Pacing**: `swingDuration` 0.92s, `SWING_PACING_CURVE`: ~0.38s frames 0-11 (push-off/leap), ~0.12s
  11-29 (whip under the hook, instant release at apex), ~0.29s 29-45 (flight to touchdown at frame
  44.5 = `SWING_LAND_PHASE`), ~0.13s 45-51 (plant and stand). `SWING_LAUNCH_ARC` bows the launch 16
  units.
- **Geometry from the move**: `swingLandAhead` (109) is where the player lands; `findSwingTarget`
  refuses unless solid ground is there level with the ledge left (works both directions). With the
  hook at the centre of a 150 gap, touchdown lands 34 onto the far ledge - a matched pair.
  `swingMinReach`/`swingMaxReach` (75..97) confine the push-off to within a few units of the lip.
  **The camera caps hook height**: ~140 units visible above a high tier; grip 112 above the ledge
  keeps the hook on screen. For real vertical gain, lower the ledges, not raise the hook.
- Tuned on JVM desktop over ~8 screenshot rounds; four swing tests in `jvmTest`.

## Level 3 ("03: New Level", WIP) - `LEVEL_3_LAYOUT`

**The layout's doc comment and inline comments in `LevelData.kt` are the source of truth** - every
constant carries its reasoning there. Summary of the current shape:
- Start, one crate, a 420-wide cantilevered table (`table.png`, 2048x512: the owner's `beam.png` with
  its flat midsection repeated 8x; leg+brace baked into the rightmost ~8.7%; `GameplayScene` crops it)
  blocking the ground path, then a ground gauntlet, then the exit.
- **The table is a floating climb target** (`LevelLayout.floatingClimbTargets`): a real mantle from
  the crate (rise 96, inside `climbMinHeight..climbMaxHeight` 51.2..115.0) with nothing bridging the
  gap - every bracing shape tried (stretched plank texture, solid block, invisible box) was rejected
  by the owner on sight. `tablePlank` 30 deep; the far-end leg (`rightLeg`, 30 wide, `legLift = 16`)
  is a real solid, sight-blocking obstacle (`boxes`, `tableDecorations`).
- **Roof guard** (30x96, `patrolPauseDuration = 3.0`, speed 55, range 220): near post `crate.right +
  120` (facing left; the crouch-behind-crate tutorial's premise - verified against `VisionSystem` that
  a crouched head is hidden across the whole tutorial window), far post `tablePlank.right - 120` (the
  lens is 28 ahead of him; at -78 it was 5 units from the leg and the "drop is seen from the far post"
  beat had no floor). `holdUntilPlayerCrouches = true` roots him at the near post until the player's
  first crouch. Nothing occludes the climb - timing is the mechanic.
- `hideCrate` on the roof at the leg corner; then two long crates (174x38, level 2's chained-crate
  look) at `longCrateElevation = 300` with **overwatch guards** (30x96, speed 35, pause 3.0,
  `visionTilt = 25 degrees` down - a level cone left a blind wedge under and a sliver past; tilted, most
  of the gap between crates is theirs while directly underneath stays hidden).
  **Guard1/guard2 timing - a real trade-off, not a bug that got fully fixed**: guard1 starts dwelling
  at his crate's gap-side corner. Guard2 USED to start at the mirror-image corner of his own crate,
  with the same speed/pause as guard1 - which, worked through directly, meant his entire motion (not
  just his facing, but exactly when he walks vs. dwells) was identical to guard1's, just mirrored by
  which side of the gap his crate is on. That mirroring is exactly what guaranteed they'd never BOTH
  face the gap at once, but it also meant they always moved and stopped at the exact same instant -
  reported directly as looking wrong (the gap is only 150 units wide, so both guards are on screen
  together). Proven directly, not just suspected, that this is a real trade-off: ANY nonzero timing
  offset between two guards sharing an identical route/speed/pause reopens SOME window where they
  could both actually detect a player standing in the gap - a guaranteed-safe crossing and a visibly
  staggered pair are mutually exclusive with this simple back-and-forth mechanic. The owner chose to
  accept a small risk window in exchange for the guards actually looking different: guard2 now starts
  mid-route (`longCrate2.x + overwatchGuardMargin + 90`, not either endpoint) instead of at his own
  patrolMaxX. Verified by simulation (not derived on paper) that this keeps guard1/guard2 out of
  lockstep a meaningful fraction of the time (`testLevel3OverwatchGuardsMoveOnDifferentTimingNotLockstep`)
  while keeping the "both facing the gap" fraction well under half
  (`testLevel3OverwatchGuardsRarelyBothFaceTheMiddleAtOnce`) - both are now tolerance-based, NOT the old strict
  `assertEquals(0, ...)`, because a strict zero is no longer achievable once the guards are offset.
  If this ever needs re-tuning (different crate size/speed/pause), don't just pick a phase and hope -
  simulate many candidate offsets directly and check both the lockstep fraction and the real
  gap-detection fraction, the same way this one was chosen; the relationship between offset and risk
  is not smooth or intuitive (small offset changes can flip the outcome drastically) and even a fixed
  offset's risk fraction drifts over a long simulated session rather than settling to one number.
- Built on `Guard.patrolPauseDuration`/`isWalking`/`holdUntilPlayerCrouches`/`visionTilt` (via
  `GuardSpawn`, which grew `width`/`height`; other guards default to 26x48). Tests: `testLevel3*`,
  `testGuardWithoutPauseStillTurnsOnTheSpot` (`GameplayModelTest.kt`).
- Past the overwatch pair: `stepCrate2` -> a second floating-climb beam (`cameraBeam`, same 96-unit
  rise, `floatingClimbTargets`) with a fixed, sweeping **camera** (`Camera.kt`, `beamCamera`) mounted
  on its underside instead of a guard, near the beam's start (`cameraBeam.x + 20` - pulled in from
  `+ 40` on request, to sit a little further left/closer to stepCrate2): sweeps 55..135 degrees
  (`sweepPauseDuration = 3.0`, dwelling at each extreme like the guards do), NOT a symmetric +/-35
  around straight down - see `Camera.eyePosition` below for why. Body art (`cameranew2.png`) splits at
  the ball joint into a static mount (plate+neck+collar) and a lens piece (ball+arm+body+end-cap) that
  rotates with `currentAngle`, both pieces measured directly off the PNG (`GameplayScene.kt`'s
  cameraMountCrop/cameraLensCrop/cameraPivotRaw - column/row alpha scans, same method as the guard
  sprite crops; re-measured from scratch each time the art asset itself is swapped, most recently
  `cameranew.png` -> `cameranew2.png`, a cleaner redraw of the same rig - since the render
  scale/crop rectangles are asset-specific pixel geometry, unlike the gameplay constants below). Cone
  `visionRange = 150`/`visionFov = 45 degrees` - narrower FOV than the earlier 55, but a bigger cone
  overall (~70% more swept area) - see the tuning history below for why FOV had to shrink for range to
  grow once the mount moved left.
  **`Camera.eyePosition` is the lens tip, not a fixed point the cone swivels around** (`NECK_LENGTH`/
  `LENS_LENGTH`, 13/27 world units): the eye itself moves along a short arc as the body rotates around
  the joint, so "the cone starts at the end of the camera" is literal, matching `Guard.eyePosition`
  being the torch lens rather than the guard's centre. **Tuning history, every round owner-reported
  against a screenshot, not just derived**: the very first pass kept a fixed eye and a symmetric
  +/-35 sweep, which never got the eye near stepCrate2 at all (only the aim direction rotated, not
  the eye) - fixed by widening the sweep and the arm length so the eye's own motion could reach the
  crate. That overshot: a 55..165 sweep with a 50-unit arm made the camera body visibly oversized, and
  right at the shallow, near-horizontal edge of that wide a sweep the cone's own edge sailed clean
  over the crate's own top instead of stopping there, reaching deep into the open corridor beyond it.
  A second pass (16/34 arm, 55..140 sweep, 150 range) shrank the body and tightened the sweep, but was
  STILL reported as oversized, and the cone's shallow FOV edge (its own spread around the aimed angle,
  not the aim direction itself) still missed the crate's silhouette on the wide side and kept going
  past it - a single "one point 150 units past the crate" regression test didn't catch this because
  the overshoot was smaller and off to the side of that one probe. A third pass landed at 13/27 (arm)
  with a narrower 55..135 sweep and a shorter 110 range, verified by sweeping every angle AND a dense
  grid of points (several heights, several distances) past the crate's own far edge, not one probe.
  Then asked to move the mount left AND make the cone bigger in the same round - re-checked directly
  rather than assumed compatible: at the OLD 55-degree FOV, moving the mount closer to the crate
  actually SHRINKS the safe range (checked: only ~90 safe at the old FOV from the new position, less
  than the 110 it had before) - moving left and enlarging the cone pull in opposite directions unless
  FOV also changes. Narrowing FOV to 45 degrees bought enough room back to push range to 150 (160
  checked clean, kept one step back for margin) - a real net increase in swept area despite the
  narrower angle. All of mount position, sweep range, visionFov AND visionRange have to be retuned
  together against LEVEL_3_LAYOUT directly any time ONE of them changes, not just derived in
  isolation for whichever one was actually asked for - reverify the same way (see
  `testLevel3CameraBeamSection` in `GameplayModelTest.kt`, which checks both ends of the crate get lit
  somewhere in the sweep AND that several points/heights past the crate's far edge never do), not by
  re-deriving on paper alone or trusting a single probe point - both of those missed a real overshoot
  in earlier rounds. The beam has its own far-end support leg (`cameraLeg`, same `rightLeg`/
  `table.png` pattern, `tableDecorations` + `boxes`) so it doesn't float - see the "nothing should
  visibly float" rule above.
- Ground dressing right after the camera, ALL under the beam's own span (between the mount and
  `cameraLeg`, not past the leg) - on request, pulled left of the leg after briefly sitting past the
  whole beam and overlapping `finalHangingCrate`'s own footprint by 48 units: `fillerBarrels`, then
  `fillerCrate`, then `stackedCrates` (two crates stacked, same crate-tiling GameplayScene.kt already
  does for any box in the crate size window with `height = 2 * 48`), ending 18 units clear of
  `cameraLeg`'s own left edge. Then, past the beam and its leg: `finalHangingCrate` (an unguarded
  crate, jumped across at beam height), `hangingEndCrate` (a crate right at its far end, landing the
  player straight back on solid ground), `finalPlatform` (a plain 260x95 ground block with no
  crate/table art - falls through to GameplayScene.kt's generic rough-block render, the same "normal
  platform" look as level 1's `block2`/`block3` in `GameWorld.createDefault`) with a crate and a
  two-stacked pair sitting on ITS OWN top surface, centred along its width (`platformCrate`,
  `platformStackedCrates` - resting on the platform, not floating above it), then the exit.
- Verified on JVM desktop screenshots in stages; **not on Android or iOS**. The uncommitted working
  tree is ahead of the last commit here - check `git status` before assuming which state is pushed.

## Level 4 ("04: Blind Spot") - conveyor belt run, `LEVEL_4_LAYOUT`

Replaced the barrel-wall + hook-swing layout of the same name (see "The swing move"). Current:
- Ground `y = 440`, `worldWidth = 5500`, no start fences, exit at `x = 5380`, `timeTargetSeconds =
  90`, `backgroundImage = "bgmg6.png"`, `hasDarknessVignette = true`, `canClimb = false` (all
  progression by jump/crouch), `restartOnConveyorFallOff = true` (instant in-place reset, no reload),
  `conveyorsStartOnMove = true` (belt frozen until first move/jump input).
- Conveyor `x 0..5000`, `y = 414`, height 26, `speed = -45` (against the player: net run 87 px/s,
  crouch crawl 20). Drawn from three sliced repeating layers `conveyor_top/mid/bot.png` inside
  `clipContainer` with `cullable()`.
- **Floor crates are all 1-stacks** (68x48, top at 366) riding the belt (`loopMaxX = 5000`, no
  `shouldLoop` - looping would teleport crates across zones). A grounded player on a crate is carried.
- **Four hanging crates** (`isHanging = true`, `y = 302`, height 38, bottom 340, `shouldLoop = true`,
  `speedMultiplier = 1.0`; `DEFAULT_HANGING_SPEED_MULTIPLIER` is `1.0`): small at 1100 and 3400 (76
  wide), long at 2250 and 4550 (174). Two bob vertically (`minY = 220`, `maxY = 302`, periods 3.5s /
  4.0s with a 2.0s phase offset). Zero-clipping arithmetic: crate top 366 vs hanging bottom 340 = 26
  of air; a crouching player (56 tall, head 358) clears by 18; standing (head 318) is blocked; standing
  or crouching on a crate (head 270/310) is blocked - you must drop to the belt and slide. `Player.kt`
  treats a platform as an overhead blocker when `playerFeetY <= platform.top + 30.0`.
- **Eight timed lasers** (`Laser.kt` / `LaserDef`, ceiling `topY = 150` to the belt at 414, thickness
  6, tilt <= 45 degrees, `activeDuration`/`inactiveDuration`/`phaseOffsetSeconds`; contact fires
  `GameWorld.onLaserHit`; small black target pads mark impact points): vertical at 670; +25 degrees
  landing at 1600; -25 degrees at 2750; a scissor pair at 3780/3840 (+-16.9 degrees, 0.6s offset); a
  triple gauntlet at 4820/4900/4980 (1.5s/1.5s, 0.5s steps). Ids `lvl4_laser_*`.
- **Darkness vignette**: a 640x640 radial gradient centred on the player (~110 px clear radius fading
  to `#05070A` at alpha 0.97 by 250 px) plus edge fillers, rendered between `worldView` and
  `hudLayer`/`controlsContainer` so HUD and controls stay bright.
- **Culling rule (CRITICAL)**: never register moving entities in the static `cullTargets`
  (`cullable(...)` captures spawn bounds and they go stale). Dynamic crates are culled per frame:
  `conveyorCrateContainers[i].visible = crate.bounds.right >= cullLeft && crate.bounds.left <= cullRight`.
- Verified: an end-to-end JVM playthrough of an earlier iteration reached MISSION SUCCESSFUL with the
  background tiling; the laser/bobbing version is in the uncommitted working tree. Not on device.

## Guard sprite (`GuardAnimations.kt`, `resources/guard/{idle,walk}/`) - replaced 2026-09-14

Copy of `PlayerAnimations`' recipe (own 2048x2048 atlas, cached per process, feet-anchored, scaled so
the standing silhouette equals hitbox height, `scaleX` negated to face left). **`tools/art/prep_guard.py`
cuts both clips from the raw 360x640 plates (`Downloads/charAnimations/guardidle` / `guardwalk`, 144
each, copied to gitignored `art-source/guard/`) and prints the constants the Kotlin needs - re-run and
paste, don't hand-edit.** He holds a torch at arm's length, so the crop box is symmetric about the
*body* (head centre column 146 idle / 150 walk) not the union bbox, padded with transparent columns on
the left (idle 149x246, walk 153x245; 3.18 Mpx, one page).
- Idle: every third frame (48), ping-ponged (doesn't loop). `IDLE_FEET_Y` = feet row 245.
- Walk: raw 42..79 every frame (38), tightest-wrapping cycle. **Walk plates are framed ~14% smaller
  than idle** - `WALK_PLATE_SILHOUETTE = 530` is the one judgement call; if he shrinks/grows when he
  starts walking, move that. Driven by distance (`WALK_STRIDE_PER_HEIGHT = 0.523`).
- Torch lens measured relative to body centre/feet: `Guard.TORCH_AHEAD_PER_HEIGHT = 0.29`,
  `TORCH_ABOVE_FEET_PER_HEIGHT = 0.56`.
- Levels 5+ guards still have 48-tall hitboxes and draw as half-height men; they need a 96-tall pass
  (`SIDE_SCROLL_LEVEL_LAYOUT`'s walkthrough test and patrol geometry are tuned to 48).
- Owner decisions: the red "visor" rect is gone (only in the no-art fallback); guard beams are one
  colour in every state (the pip over his head shows detection) - **don't reintroduce a colour ramp on
  the cone**. Camera cones still use the old orange/red ramp and the old `worldView.graphics()` path.
- If `GuardAnimations.load()` throws, the scene logs `[GuardAnimations] load failed` and falls back to
  the rect + visor.

## Guard vision = the torch beam (`Guard.eyePosition`, `LightConeView.kt`) - 2026-09-14

`Guard.eyePosition` (name kept - `VisionSystem` uses it for cameras too) = `centre.x + facing * 0.29 *
height, bottom - 0.56 * height` - for 96-tall guards, 28 ahead and 54 above the feet (old eye: 4 inside
the edge, 84 up). Drawn beam and detection rays share this origin: what the player sees lit is exactly
what can see them. `visionTilt` rotates the cone down.

**`LightConeView`**: the vision polygon uploaded as one triangle fan with per-vertex colour (lens
bright, inner ring at 40% range, rim at `RANGE_FLOOR` = 40% of lens brightness then a hard stop; sides
no dimmer than `EDGE_FLOOR` = 70%), additive blend, 1x1 white texture, one draw call. No `Graphics`:
the SYSTEM renderer rasterised a ~4MB bitmap per cone per frame, and the GPU renderer's non-convex
path does a full-framebuffer stencil render-to-texture per view per frame (`GpuShapeView.
renderInternal`, `shape.requireStencil = !isConvex`). `GameplayScene` rebuilds a beam only when lens/
facing/range change (`guardConeLensX/Y/Facing/Range`) and skips guards outside the half-screen culling
window - which finally stops level 1's parked guard from costing anything. Look after three owner
rounds: `DEFAULT_COLOR` alpha 0.28, warm `(255, 232, 178)`, firm floor to the range edge, **no
outline**. Backdrops are pale fog, so additive light saturates fast; raise these if the palette darkens.

## Device heating on Android - measured root causes (2026-09-09)

Measured on JVM against real level data plus KorGE's decompiled Android classes, not on device.
1. **Vision cones drawn with the SOFTWARE rasterizer, rebuilt every frame** - `worldView.graphics()`
   defaults to `GraphicsRenderer.SYSTEM`; `BaseGraphics.redrawIfRequired()` allocates a new
   `NativeImage` (bounds x device scale), rasterises, uploads a fresh texture per dirty frame, and
   `updateShape` dirties every frame. At zoom 1.35 on a 1440p device: level 1 = 1053x1053 = 4.23 MB/
   frame (254 MB/s at 60fps, 508 at 120) for a guard that doesn't exist in play; level 2 = 2.01 MB;
   level 4 (3 cones) = 3.34 MB. **Guards: DONE via `LightConeView`. Camera cones still use this path**
   (one per camera, rebuilt every frame) - the remaining instance.
2. **Cone polygon build allocates ~275 KB/cone/frame** (`castRay` -> `Rect.edges()` allocates 4
   `Segment2d` + 8 `Vec2d` per occluder per ray; `intersects` 3 more). 23 us/frame level 1 vs `world.
   update` 0.9-3.2 us and <2.5 KB - the simulation is not the problem. Closed for guards (static
   occluders, rebuild only on change).
3. **Nothing caps the frame rate** - runs at panel refresh (120 Hz here). `continuousRenderMode`
   defaults `true`, `KorgwSurfaceView` is `RENDERMODE_CONTINUOUSLY`, `onDrawFrame` unthrottled.
   **`KorgeConfig.targetFps` is a dead knob** (written to `Views.targetFps`, read nowhere). **Trap:
   `Views.forceRenderEveryFrame = false` does NOT cap** - it switches to `RENDERMODE_WHEN_DIRTY` and
   hands updates to the `korgw-updater` thread, an infinite loop with `Thread.sleep(1)` (~1000 Hz).
4. That `korgw-updater` thread spins ~1000 wakeups/s regardless; only stopped in
   `onDetachedFromWindow`, which backgrounding doesn't fire.
5. Oversized textures (fixed, below) and always-rendering-under-the-menu (bug #7).
Next: camera cones, then a real frame cap.

## Runtime performance: where the frame budget goes (2026-09-08..10)

Reasoned from assets and the render path; the on-device improvement is unmeasured (`adb shell
dumpsys gfxinfo com.infiltrate.androidshell framestats` before trusting any ranking).

1. **The player atlas is the biggest memory consumer.** `MutableAtlas(2048, 2048)` adds a whole page
   at a time: 16.8MB heap + 16.8MB texture per page. Trimming unreachable frames (climb's raw 1-69
   run-up - `CLIMB_START` clamps to raw 70; crouchwalk's raw 145-192 tail) took it 26.2M -> 20.3M px;
   the swing clip put it at ~22.5M (accepted). **Adding frames is not free** - see the ATLAS BUDGET
   comment on `load()`. Climb START/END constants are in loaded-index space (`loadAnimation(firstFile
   = ...)`).
2. **Textures authored 10-26x larger than drawn, and `bitmap.mipmaps(true)` is a SILENT no-op on
   non-POT art** (`AGObjects.kt` `doMipmaps()` requires POT; no error, no log). Every asset was NPOT,
   so no mip level ever existed. FIXED 2026-09-10 - see "Adding new art".
3. **Per-frame allocation in the updater** (fixed): `InMemoryGameProfileStorage.getProfile()` returns a
   deep copy (now read once per frame into `cachedProfile`, refreshed after `tryActivatePowerup`);
   `GameWorld.update` rebuilt `platforms`/`boxes`/`occluders` concatenations every frame (now reuses
   the level's lists when `movingPlatforms` is empty, scratch buffer otherwise); powerup HUD chips
   called `updateShape` every frame (now skip when identical) and rebuilt labels + measured
   `countText.width` every frame even when hidden (now only when live/count/timer-tenths change).
4. **KorGE renders continuously under the Compose menu on Android** (bug #7) - first suspect for a
   *menu* lag report.

**Dead assets removed** (`resources/` 75MB -> 40MB): `a1-a5`, `bg1-bg5`, `bg10-bg13`, `bglayer`,
`bgmg`, `mglayer`, `mglayer2`, `card_bg`, `chainedhook`, `korge`, `store_*`, `bg_menu.jpg`, `logo.jpg`
(the `Res.drawable.*` ones resolve to `paywall-build`'s own `composeResources/drawable/` copies). Live
backgrounds: `bgmg2/3/4` (rotation), `bgmg5` (level 2), `bgmg6` (level 4). App icon is `icon.png` at the
repo root. `test_minimal.ldtk` is KEPT (`test/LdtkLoaderTest.kt`). **Before deleting, grep the whole
repo excluding `build/`** - `build/intermediates/.../merger.xml` hits are packaging evidence, not use.
Dead code removed: `UiComponents.drawAtmosphericBackdrop()`/`drawAtmosphericBackdropBitmap()`.

**`SceneAssets.kt` caches bitmaps and fonts process-wide** - `sceneMain()` was calling `readBitmap()`
~20 times and `readTtfFont()` twice on every scene load (RESTART, QUIT-relaunch, watch-ad, level
change), re-decoding ~87MB of PNG. Only successful loads are cached (a missing file still retries).

**Lossless PNG recompression** (Pillow `optimize=True, compress_level=9`): 229/604 files smaller,
1.29MB saved, all byte-identical decoded vs `git HEAD`; player frames left alone (came out larger).
Safe because korim's decoder reads only `IHDR`/`PLTE`/`tRNS`/`IDAT`/`eXIf`/`IEND`. Download-size win
only. oxipng/zopflipng would beat it if size matters later.

**Off-screen culling** (third pass): KorGE does no frustum culling. Static decor registers its
world-space x-span via `cullable(view, left, width)` at six sites (platforms, boxes, entrance, exit
fence, truck, hanging crates); the updater toggles `visible` from the camera window with a **half-screen
margin either side** (generous by design - pop-in impossible). Static only; player/guards/cameras/moving
platforms excluded (their pips and cones are children). `bgmusic.mp3` re-encoded 256 -> 128 kbps
joint stereo (2.22 -> 1.11MB; kept stereo - the side channel is real at -32.8dB vs mid -15.7dB; the
Xing/LAME header is kept for gapless looping - **loop seam unheard on device**; if a tick appears at
the loop, that header is the first suspect).

**DECIDED NO: do not atlas the static world art - not "not yet", not at all.** `MutableAtlas`
allocates its whole 2048x2048 page (16.8MB) up front; after the POT pass the seven stretch-to-box
textures total 1.96M px (~7.8MB held individually) - less than half a page, so atlasing more than
doubles them. The benefit is ~10 fewer texture binds per frame on level 1 (~14 sprites across 7
textures, fewer after culling) - inside a mobile GPU's noise floor. And it conflicts with mipmaps
(mip levels bleed across slice boundaries without gutters). Atlasing pays for hundreds of small sprites,
not a dozen large props. **Count both cost and benefit before proposing it again.**

### POT + mipmaps pass - DONE 2026-09-10

Ten assets re-encoded: **50.5 MB -> 9.8 MB of texture memory** (~13 MB with mipmaps), disk 10.2 -> 2.1
MB. Each rendered into its exact device-pixel draw box before/after and diffed: mean error under
0.6/255 for all ten (worst `fence2.png`). JVM desktop screenshotted. **Originals live in git history at
`77a65b9`** (`git show 77a65b9:resources/interact.png > interact.png`).

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

(`fence1Width = 151.0`, `fence2Width = 172.0`, `fenceHeight = 140.0`; truck `29+45+179` wide x
`truckBedHeight = 96`; `moveRadius = 54`, `actionRadius = 48` doubled.) `fence2.png` needed 516 and got
512 - the refined rule: round up unless within a couple of percent of the lower POT. `truck.png` grew on
disk 8 -> 27 KB (flat silhouette art gains gradients) - texture memory still halved; **do not revert**.

**DO NOT SHRINK - already at or below device resolution at 1440p**: `bgmg2-6.png` (~1992x724 for 480
virtual = 1440 device px tall, already upscaled ~2x, and tiled edge-to-edge with a 1px overlap over a
hand-healed seam - resampling disturbs it), `loadingbg.png`/`logo_main.png` (2172x724), `dossier_paper.png`
(1200x800 -> 1872x1248 device), `success3.png` (1536x1024 -> ~1987x1325 device, under-resolution; feeds
`winCardAspect` at runtime), `failedscreen.png` (1215x1295 -> ~1320x1407 device, feeds failed card aspect),
`button1-4.png` / `victorybutton1-3.png` / `failedbutton1-3.png` (~677x167 -> 900x156 device, already under).

**Still hardcoded to file dimensions** (resample and geometry moves - pin as literals/fractions first,
or leave the file alone): `chainedcrate.png`/`chainedcrate2.png` sub-slices `(26, 1222, 971, 226)` /
`(235, 1134, 555, 287)` + `chainDrawH = cropY * scale`; `stars.png` slices `(69, 33, 636, 611)`,
`(760, 33, 647, 611)`, `(1464, 33, 641, 611)` (hand-painted, unequal widths); `hook.png`'s
`hookHeight = hookWidth * (2136.0 / 154.0)`. `entranceWidth`/`exitFenceWidth`/`hookHeight` are now
literals with comments naming the authored size. `GameWorld.kt`'s `barrelWidth = 32.0` comment about
"832x1274" is stale.

### Adding new art: shrink it on the way in - a standing rule

1. Find the size it is **drawn** at in virtual units (`size(w, h)` or its `Rect`), not painted at.
2. **Multiply by 3** - the virtual canvas is 1040x480 (`main.kt`/`MainActivity.kt`); a 1440p phone
   renders at 3x (2.25x on 1080p). Sizing from virtual numbers gives a third of the needed resolution;
   it looks fine on desktop and mushy on the phone.
3. **Round to a power of two** in both dimensions (up, unless within a couple of percent of the lower).
4. **Resample, never pad** - transparent padding is stretched into the draw box with the art.
5. `python tools/art/pot_resize.py resources/newthing.png 512 512` - premultiplied-alpha LANCZOS,
   refuses non-POT targets; `--check` reports sizes.
6. Verify what reaches the screen: render old and new into the device-pixel box and diff (<~1/255 mean).
7. Wire via `SceneAssets.bitmap("x.png")` (default `minified = true`: asks for mipmaps, warns if not
   POT) or `minified = false` for anything drawn ~1:1 or larger and anything **sub-sliced** (mip
   levels bleed across slices).

**Aspect ratio is NOT a concern for stretch-to-box assets** (stated backwards twice before) - every
draw is `size(box.width, box.height)`, the file's aspect never reaches the screen. **Never derive a
drawn size from a loaded bitmap's dimensions** - write a literal naming the authored size. Use a
premultiplied-alpha-aware resampler or silhouette edges pick up fringes. `truck.png`/`entrance.png`
are pre-mirrored - a tool that normalises orientation would undo bug #8's fix.

**The guardrail**: `SceneAssets.warnIfNotPowerOfTwo` prints one line per offending asset per run:
`[SceneAssets] 'hook.png' is 154x2136 - NOT power-of-two, so mipmaps are silently skipped for it.`
**Expected output: exactly that one line** (`hook.png`'s extreme aspect rounds badly, win ~1 MB). A
second line means something new needs sizing.

## Asset prep techniques

Source drop: `C:\Users\USER\Downloads\charAnimations\assets\`.
- **Tileable parallax backgrounds** (`bgmg*.png`, tiled by placing copies edge-to-edge): (1)
  circularly roll horizontally by `width // 2` so the wrap edge is adjacent by construction and the
  real seam moves to the centre; (2) heal that seam with a narrow falloff-weighted blend against a
  Gaussian-blurred copy; (3) verify by diffing left/right edge columns and rendering a tile-join strip.
  **Band width and blur must scale with source detail**: 170px half-width / 24px blur (fine on foggy
  `bgmg5`) left a visible haze band on crisper `bgmg6`; **80px half-width / 10px blur with a smoothstep
  falloff** matched the shipped set. Wrap-edge diffs on `bgmg2-5` sit around mean 0.7-2.7, max 16-138 -
  a sanity check, not a target. `bgmg6.png` = `darkbg3.png` at native 2172x724 (an earlier `darkbg2`
  version was replaced before commit; no trace).
- **Tight-crop a silhouette to its alpha bounds** before stretching it into a box (dead margin
  stretches too); re-derive box width from the cropped aspect at the fixed height.
- **Chroma-key an opaque JPEG-style asset**: R/G/B all >= 200 -> transparent, else opaque, then crop.
- **Find a seam inside a composite illustration**: scan column-wise opaque density for a sharp drop.

## Smaller features and decisions

- **App icon**: real set from a 1254x1254 illustration wired into Android (`android-shell/`, legacy +
  round + adaptive, manifest updated), iOS (`ios-shell/Resources/Assets.xcassets/AppIcon.appiconset/`,
  Xcode 14+ single-size, `ASSETCATALOG_COMPILER_APPICON_NAME` in `project.yml`), and `korge { icon =
  file("icon.png") }`. Not verified on a device.
- **Language dropdown** (`SettingsScreen.kt`): 15 languages in native script, persists the code only -
  **zero translated strings exist**. No Bebas Neue (Latin-only) and no `letterSpacing` on the names
  (breaks Arabic joining).
- **Reset Progress** (`SettingsScreen.kt`, 2026-09-12): confirmation dialog (`showResetConfirmDialog`,
  `#16161A` with `#FF5252` accent, CANCEL / RESET EVERYTHING). `profileStorage.resetProgress(preservePremium
  = true)`: coins 100, starter inventory (2 jammer, 2 smoke, 1 bomb, 2 darts, 2 phantom, 2 invis, 2 boots,
  1 trigger), unlocks `["level_1", "level_5"]`, `totalLevelsCompleted` 0, controls Default, language
  "en", volumes 0.8/1.0; **`isPremium` preserved**. `levelStorage.clear()` purges `level_result_*` and
  `level_results_ids`; ad-limiter counts removed. `PlatformStorage.removeRaw(key)` added across JVM
  (`ConcurrentHashMap.remove`), Android (`Editor.remove().apply()`), iOS (`removeObjectForKey`).
- **Layers Events SDK - REMOVED 2026-09-12** (was `com.layers.sdk:layers-android:3.3.0` on Android).
  Dependency, `InfiltrateApplication.kt` config, `GameplayScene.kt` calls, all 7 `AnalyticsBridge` files
  and the privacy-policy disclosure are gone. **Zero third-party analytics SDKs remain.**
- **In-App Review (Google Play & iOS StoreKit)**: Multiplatform review prompting via `InAppReview` (in
  `paywall-build`) and `InAppReviewBridge` (in `:game`). On Android, uses `com.google.android.play:review:2.0.2`
  (`ReviewManagerFactory`) wired to `MainActivity`. On iOS, links native `StoreKit.framework` and calls
  `SKStoreReviewController.requestReview(in: scene)`. Prompted automatically upon completing level 4
  (`GameplayScene.kt` -> `getInAppReviewBridge().requestReview()`) and manually via the "RATE US" button
  in the About section of Settings (`SettingsScreen.kt`).
- **Web presence (`site/`, Netlify, e.g. `infiltrate.saysplit.app`)**: `index.html`, `support/`
  (App Store Guideline 1.5 page, Netlify Form with Name/Email/Category/Message, no visible email, no
  FAQ), `privacy/` (on-device storage, AdMob/UMP consent, RevenueCat, COPPA/GDPR/CCPA), `styles.css`,
  `_redirects`, `netlify.toml`. **Two unresolved compliance gaps**: the privacy policy's purchase-data
  paragraph now matches Android's real RevenueCat flow but iOS billing isn't wired; and **Apple's App
  Tracking Transparency prompt is not implemented** while AdMob can serve personalized ads - decide
  (add ATT, or force non-personalized on iOS) before submission. A `/delete` page was built and
  reverted the same day - the game holds no server data (local-only, deleted by uninstalling); the
  owner answers Play Console's deletion question "No".

## Keep this file up to date

This file is the first thing a new chat/agent should read. Whenever you make a decision, discover a
constraint, or change something a future session would need (tooling gaps, CI status, build quirks,
unresolved issues), update the relevant section - or add one - before ending your turn. Treat stale
info as a bug: fix it in place rather than leaving it for the next chat. **Prefer editing the
current-state description over appending a dated "pass" entry** - the file was consolidated on
2026-09-08 and compressed on 2026-09-14 to stop multi-round sagas accumulating; keep it that way.
Where the code carries its own reasoning in doc comments (level layouts, `PlayerAnimations`,
`prep_guard.py`), point there rather than duplicating it here.
