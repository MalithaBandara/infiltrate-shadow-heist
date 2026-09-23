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
sits. Apply this to every new elevated platform in future levels, not just Level 3. **The one
standing exception is LEVEL_6_LAYOUT's section-5 platform**, which the owner asked to have nothing
under it and then asked to have its chains removed as well ("remove the chain holding the floating
platform") - see "Section 5" below before adding rigging back to it.

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
REVENUECAT_GOOGLE_KEY)`). **iOS wired** (`AppDelegate.swift` -> `StoreBilling.shared.initialize(apiKey: "appl_...")`,
with `DEFAULT_APPLE_API_KEY` and lazy initialization fallback in `StoreBilling.ios.kt`). On success `profileStorage.addCoins(pack.amount)`
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
`switchToKorGE()` -> `GameplayScene` sees `consumeContinueGranted()` and revives the player in-place at
their last safe checkpoint (`world.respawnAtCheckpoint()`), capping at 1 continue per run. When continue is
used, subsequent deaths in the same run hide the CONTINUE button and dynamically re-center RETRY and MAIN MENU
across the bottom bar. Revival grants 3.0s grace cloak (`activePowerups.invisibilityTimer = 3.0`) and laser
grace (`laserGraceTimer = 3.0`), returns guards to patrol, and snaps the camera to the player. When the **Checkpoints** gadget (`PowerupType.CHECKPOINTS`, 750 coins) is active in a level, the player is not capped at 1 continue—they continuously auto-respawn at their last safe checkpoint each time they die until they quit or complete the level. Tools in the Store are arranged in strictly ascending order of price: INVISIBILITY CLOAK (350), STEALTH BOOTS (500), LASER SHIELD (600), and CHECKPOINTS (750). To prevent instant deaths directly after spawn in any level, `spawnGraceTimer` (2.0s) activates upon level start and restart (`restartLevel()`), suppressing alert accumulation, laser hits, downward crate crushes, and conveyor fall-off while the player remains at spawn or checkpoint (`isAtSpawnOrCheckpoint()`). On Android
everything runs in one process, so `ContinueAdBridge.android.kt` is a plain shared object; desktop JVM
simulates immediate grant in `JvmContinueAdBridge` for local testing. The MISSION FAILED card has three buttons
at the bottom when continue is available: **CONTINUE** (leftmost, watch-ad clapper icon), **RETRY** (bold circular reload arrow), and **MAIN MENU** (silhouette home icon), sized at 175x62px (upgraded from 44px, then 54px); button icons are vertically centered to the optical middle of the text glyphs (`textY + text.height * 0.44`, with `drawWatchAdIcon` offset by -1.5 so its body and play triangle align) rather than `height / 2.0` (which sat too low because Bebas Neue has no descenders and the torn-paper button frames have higher vertical centers); when continue is spent, CONTINUE is hidden and RETRY and MAIN MENU are centered; the victory overlay similarly features 56px buttons (**RETRY**, **MAIN MENU**, **NEXT MISSION** with double forward arrows); a failed ad never strands the player. Verified: JVM + Android compile. Never run on a device.

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
**`PROTOTYPE` removed from `GameplayScene.kt`'s `gadgetTypes` (the in-game HUD tray) on request,
2026-09-20** - it was still listed there (a leftover from before it became a Store-excluded
placeholder), so a player who somehow got one (e.g. the F2 debug-powerup grant below, which grants
every `PowerupType` indiscriminately) saw a real, tappable-looking tray slot with a plain "?"
mystery-box icon that did nothing when used - reported directly from a screenshot ("mystery item
should not be inside the game play"). `grantDebugPowerups()` still grants it (harmless dead stock,
invisible now that the tray skips it) rather than special-casing the storage method for one type.
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
- `!profile.isPremium` - the Remove Ads purchase promises "removes all interstitial
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

## Responsive layout: one canvas rule for every device (2026-09-24)

Both halves of the app used to be pinned to the reference phone. Gameplay ran in a fixed
1040x480 virtual canvas under `ScaleMode.SHOW_ALL`, which letterboxes anything that is not that
2.167 aspect - 8% of a 16:9 phone and **38% of a 4:3 iPad** went to black bars. Every Compose
screen scaled itself off `(maxHeight / 720.dp).coerceIn(0.75f, 1.4f)`, and `MenuTopBar` did not
scale at all (a hard 74dp with 26sp type - a fifth of a landscape phone's height).

**`src/game/model/ScreenLayout.kt` (pure Kotlin, shared with `paywall-build`) is the one rule:
the virtual canvas carries the DEVICE's aspect and always CONTAINS the authored 1040x480.**
Wider than 2.167 keeps the 480 height and grows the width; squarer keeps the 1040 width and grows
the height. So **no device ever sees less of a level than the reference phone** - a wide screen
sees a little more level width, a tablet sees more sky - and level pacing tuned against ~770
visible world units (level 6's "the crane fills the frame from the lever", level 3's overwatch
pair) still holds. 4:3 works out to 1040x780. `GameplayScene` needed no camera change for this:
it already pinned the ground near the bottom of whatever canvas it was given and tiled the
background to `canvasH`. The rejected alternative was keeping the height and letting the width
follow the aspect, which hands a 4:3 iPad a 640x480 canvas and silently cuts the visible level
width by a third.

- **Who sets it, and when.** `DeviceScreen` (same file) holds what the host measured; every host
  publishes and then `game.scene.DeviceViewport.apply(views, sceneContainer)` re-asserts it
  **before** `changeTo` (a `Scene` copies `sceneContainer.size` once, when it is built - applying
  it after does nothing until the next scene). Desktop knows its window up front, Android knows
  in `onCreate`, **iOS does not**: `gameMain()` runs from inside
  `ShellAppDelegate.applicationDidFinishLaunching`, which Swift calls as the first statement of
  its own `didFinishLaunchingWithOptions`, before any window is laid out.
- **Swift measures on iOS, deliberately.** `UIScreen.mainScreen.bounds` from Kotlin/Native is a
  `CValue<CGRect>` needing `useContents` + `ExperimentalForeignApi`, and iOS is the one target
  that cannot be compile-checked here. `GameScreenMetricsBridge` (`src@ios`) and
  `MenuScreenMetricsBridge` (`paywall-build/src/iosMain`) take four plain `Double`s instead.
  **Both are needed**: `GameMain` and `PaywallModule` each compile their own copy of
  `src/game/model`, so there are TWO `DeviceScreen` objects in the process and a publish reaches
  only one. `AppDelegate.swift` calls both, at launch and again on every switch into gameplay.
  Android has one process-wide copy and publishes once in `MainActivity`.
- **Landscape is assumed, defensively.** `viewportFor` normalises its inputs with max/min rather
  than trusting which is "width" (iOS reports portrait-shaped bounds during the first moments of
  launch; Android 16 ignores orientation locks on large screens) and clamps the aspect to
  0.75..3.0 so a genuinely portrait window degrades into a tall canvas rather than something
  absurd.
- **Safe areas are now real, not guessed.** `GameplayScene`'s `edgeInset`/`bottomInset` (46/38)
  are floors now: where a host reports an inset, the reported value plus a 12-unit margin wins.
  An iPhone's Dynamic Island is 59pt wide and sits on a SIDE in landscape - wider than 46 - so
  the left D-pad really was partly underneath it. The objectives block, the pause/gadget cluster
  and both end-of-run cards take the same insets. Android reports `displayCutout() |
  mandatorySystemGestures()`, NOT the full `systemGestures()` set (that reserves ~20dp down both
  long edges for no real gain here), and its decor-view listener forwards via
  `ViewCompat.onApplyWindowInsets` rather than returning early, or the dispatch never reaches
  Compose.

**Compose: `paywall-build/src/commonMain/kotlin/ui/Responsive.kt`.** `menuMetrics(maxWidth,
maxHeight)` gives one `scale = min(h/720, w/1280)` clamped 0.62..1.45, so the *smaller* axis
limits. At the reference 1560x720 it is exactly 1.0 - the desktop/reference look is unchanged,
which matters because these screens have been through many rounds of the owner's own feedback and
this was not a redesign. **The main menu keeps its own lower floor** (`MAIN_MENU_MIN_SCALE` =
0.55): its four stacked 84dp buttons plus the 158dp logo come to ~396dp at 0.62 against a
390dp-tall phone and SETTINGS falls off the bottom - measured, not estimated, and 0.62 was tried
first and does exactly that. Where scaling alone cannot fit a screen, `metrics.isShort` (height <
520dp, i.e. every phone in landscape and nothing else) trims decoration instead of shrinking type
further: a shorter chapter row, two description lines instead of three, no top-bar wordmark.
`MenuTopBar`/`StatPill`/`CoinPill` all take `scale` now, defaulting to 1f.

**Verification, and what is NOT verified.** Desktop stands in for device aspects:
`./gradlew runJvm -PwindowSize=1024x768` (iPad), `1280x720`, `1120x480` (21:9), and
`./gradlew :paywall-build:run -PwindowSize=844x390` for the menus - both read the env var the same
way `startLevel` already did. Screenshot recipe as always (`SetProcessDPIAware()` first), and
**capture the CLIENT rect, not `GetWindowRect`**, which includes the invisible resize border and
shows the desktop behind the window. Checked this way: gameplay at 4:3 / 16:9 / 21:9 on levels 1,
4 (vignette) and 7 (its `canvasH * 488/724` background anchoring holds), and MainMenu / Missions /
Store at 844x390 and 1024x768. `jvmTest` 187 green, `:paywall-build:jvmTest` 14 green,
`android-shell:compileReleaseKotlin` clean. **Nothing here has run on a real device or simulator,
and the safe-area plumbing in particular has never seen a non-zero inset** - desktop reports none.
iOS is not even compile-checked (CI only).

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
  `bgmg6.png`, darkness vignette), `05: Restricted Zone` (`SIDE_SCROLL_LEVEL_LAYOUT` - the recovered
  barrel-wall + hook-swing stub, see "The swing move"), `06: Missing Container` (`LEVEL_6_LAYOUT` -
  lever-crate swing, pit crossing, crane crossing; see its own section), `07: Stolen Manifest`
  (`LEVEL_7_LAYOUT` - linear vent crawling gauntlet, exhaust fans, camera bots, steam pipes; see its
  own section), `08: Hidden Archive` .. `12: Hidden Cargo` (no layout of their own, `GameWorld.createDefault`
  with a per-level `guardSpeed`), `13: Final Proof` (`LEVEL_13_LAYOUT` - deliberately EMPTY, the
  push-animation stage; see its own section). Other levels' backgrounds rotate through `bgmg2/3/4`
  via `LevelData.resolvedBackgroundImage`.

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
- `IDLE_FEET_Y` = **245.0** (measured sole at 248 back / 255 front, stable across all 45 frames).
  In idle stance, `idleFeetOffset` shifts the sprite downward so the higher (back) shoe touches
  the floor/surface, while the lower (front) shoe extends slightly below the surface by design.
  `CROUCH_FEET_Y = 250.0`, `JUMP_LAND_FEET_Y = 247.0` follow the same method.
- `WALK_FEET_Y = 245.0`: on elevated/contoured surfaces like the truck where the art sits below the
  collision top, `GameplayScene` applies `walkFeetOffset` so the planted foot firmly contacts the
  truck bed without floating above it. On the flat floor and platforms, `GameplayScene` uses a flush
  grounding offset (0.0) so the planted shoe does not sink underground into the floor.
  Transitions between idle and walk smoothly interpolate the offset to prevent vertical popping.
  `crouchwalk` uses `crouchFeetOffset` and `landAbsorb` uses `jumpLandFeetOffset`. `interactAngle`
  settled at 60 degrees down-right.

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

## The swing move (`Player.kt` / `resources/player/swing`) - built 2026-09-10, live on level 5

**In use by `05: Restricted Zone` (`SIDE_SCROLL_LEVEL_LAYOUT`)** - the original "Blind Spot" barrel-wall
+ hook layout (built for level 4, replaced there by the conveyor layout - see Level 4) was recovered
from git history (`24991bd`, before `c45c231`) and restored verbatim as level 5's content on
2026-09-16, guards and all (there are none - deliberately brought back as-is, not fleshed out).
`GameplayModelTest`'s swing tests (`testSwingNeedsTheWalkAndTheHook` etc.) now run directly against
`LevelData.SIDE_SCROLL_LEVEL_LAYOUT`/`SIDE_SCROLL_LEVEL` rather than a parallel test-only copy, so they
double as level 5's own walkthrough verification. Any other level can still use the mechanic by
populating its own `swingHooks`. What a future session needs:

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
- Tuned on JVM desktop over ~8 screenshot rounds; five swing tests in `jvmTest`, one of which
  (`testSwingCarriesThePlayerOverLevel5sGapAndLandsThemOnIt`) drives a full player-input walkthrough
  of level 5 end to end and asserts `world.isLevelComplete` - not a point-sampled check.

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
  at the beam's own LEFT CORNER (`cameraBeam.x` exactly, not +20 - moved there on request) instead of
  a guard: sweeps 55..111.3 degrees (`sweepPauseDuration = 3.0`, dwelling at each extreme like the guards
  do), `visionFov = 80 degrees`, `visionRange = 220`, NOT a symmetric sweep either side of
  straight-down - see `Camera.eyePosition` below for why. Body art (`cameranew2.png`) splits at the
  ball joint into a
  static mount (plate+neck+collar) and a lens piece (ball+arm+body+end-cap) that rotates with
  `currentAngle`, both pieces measured directly off the PNG (`GameplayScene.kt`'s
  cameraMountCrop/cameraLensCrop/cameraPivotRaw - column/row alpha scans, same method as the guard
  sprite crops; re-measured from scratch each time the art asset itself is swapped - the render
  scale/crop rectangles are asset-specific pixel geometry, unlike the gameplay constants below).
  **`Camera.eyePosition` is the lens tip, not a fixed point the cone swivels around**
  (`NECK_LENGTH`/`LENS_LENGTH`, 9.5/20 world units - see Lesson 4 below for the resize history): the
  eye itself moves along a short arc as the body rotates around the joint, so "the cone starts at the
  end of the camera" is literal.
  **Tuning history and the two hard-won lessons in it** (every round owner-reported against a
  screenshot, not derived on paper): early passes (fixed eye, then a too-wide 165, then 140-degree
  sweep with a 50-unit arm) each either never reached stepCrate2 at all or made the cone's shallow FOV
  edge sail clean over the crate's top into the corridor beyond - fixed by shrinking the arm
  (`NECK_LENGTH`/`LENS_LENGTH`) and narrowing the sweep together, landing on a 135-degree sweep /
  45-degree FOV pairing that a **point-sampled** check (probing specific x,y positions past the crate)
  found clean.
  **Lesson 1 - a point-sampled check has a real blind spot.** A probe point reads as "not detected"
  for two very different reasons that look identical to the check: "correctly blocked by an occluder"
  and "simply outside visionRange". The owner reported the cone STILL crossing the crate at those
  exact values. Re-verified against `VisionSystem.computeVisionPolygon` directly - the exact call
  `GameplayScene.kt` makes to draw the cone - and this confirmed the 135/45 pairing itself was fine at
  that instant, but revealed range is close to a free variable for "never past the crate": every ray
  this cone can cast is blocked by either the crate or the GROUND (which runs the full level width)
  well within ~220 units of this mount, so a bigger nominal range past that point changes nothing
  visually - which is how visionRange grew from 110 to 220 across two rounds with no added risk.
  **Lesson 2 - a stepped floating-point sweep can miss the one angle that matters.** Repeated requests
  for a WIDER cone kept running into "135/45 is the only nearby combo that still reaches stepCrate2's
  FAR corner" - which turned out to be true for a bad reason: reaching that corner requires aiming
  almost exactly at the edge of the FOV, at the crate's own corner - and THAT is precisely the
  condition where `VisionSystem`'s corner-anchored ray sampling casts one ray that just barely clips
  the corner (stops there, fine) and its immediate angular neighbour that just barely clears it
  (keeps going to the GROUND far beyond, sometimes 100+ units past the crate). Filled in as part of
  the polygon, that reads as a thin wedge of light stabbing out past the crate - reported directly as
  "light rays going out of the camera", and it was a REAL, currently-shipped bug, not a stale
  screenshot: at `currentAngle == maxAngle` (135 degrees) exactly - precisely where the camera sits
  for its whole 3-second dwell - the polygon really did contain that far-away vertex. The regression
  test in place at the time missed it because its own sweep (`angle += 2 degrees`, accumulated in
  floating point many times over) drifted just far enough off the exact 135.0-degree mark to dodge the
  one bad angle - a stepped/accumulated loop is not the same as checking the angle the camera actually
  dwells at.
  **The fix for both**: re-searched the whole (maxAngle, visionFov) space directly against the
  rendered polygon (not nudges off the old values), checking exactly at min/maxAngle rather than a
  drifting stepped sweep, and scanning every angle in the sweep for any adjacent-vertex RADIAL jump
  (the actual signature of a ray grazing past a corner - NOT just a big Euclidean gap between
  vertices, which also happens completely normally when the polygon traces straight down a tall
  occluder's own side face). stepCrate2's far corner turned out to be reachable ONLY inside a
  razor-thin band of (maxAngle, visionFov) pairs, every one of them sitting right on the cliff edge
  that produces the spike - "see the far corner" and "never spike" are not simultaneously achievable
  from this mount position. Dropped the far-corner requirement (the near corner, still covered, keeps
  the crate a real risk) and landed on 115/80: nearly double the old 45-degree FOV - genuinely wider,
  the thing actually being asked for round after round - with a comfortable ~25-unit margin before the
  crate's edge and zero stray-ray spikes anywhere in the sweep, both checked, not assumed.
  **Lesson 3 - moving the eye itself reopens a trade that looked closed.** The next round asked for the
  far corner back specifically ("the cone should just touch it") once the mount also moved to the
  beam's own left corner. Moving `x` changes exactly which (maxAngle, visionFov) pairs graze the
  corner, so "reachable only in a razor-thin band right on the spike's cliff edge" from the old mount
  position was NOT a fact about the corner - it was a fact about that specific eye position. Re-ran the
  same polygon-based (maxAngle, visionFov) search from the new `x = cameraBeam.x` and found the safe
  margin before the cliff had grown from razor-thin to a comfortable 0.5 degrees - landed on
  `maxAngle = 116` (visionFov stays 80, unchanged) with the cone's leftmost reach landing ~1.6 units
  short of the crate's exact far corner (visually flush) and zero spikes anywhere across 55..116,
  checked the same way as lesson 2 (exact min/maxAngle, plus a fine stepped sweep, scanning for the
  radial-jump signature - not a point-sampled grid or paper estimate). See
  `testLevel3CameraConePolygonNeverPastCrate` (checks exactly at min/maxAngle, not just a stepped
  sweep), `testLevel3CameraConeHasNoStrayRaySpikes` (the radial-jump scan), and
  `testLevel3CameraLeftmostSweepReachesStepCrateFarCorner` (asserts the leftmost reach lands close to,
  never past, the crate's own edge) - reverify the SAME way, not a point-sampled grid or a paper
  estimate, any time the mount position, arm length, sweep range, visionFov or visionRange change.
  **Lesson 4 - the arm length is part of the same geometry, not a separate concern.** The camera still
  read as oversized next to the player on a later screenshot even at 13/27 - the third resize pass in
  a row to get this complaint. `NECK_LENGTH`/`LENS_LENGTH` drive `Camera.eyePosition` directly (see
  `Camera.kt`'s own doc comment), so shrinking them to 6/13 moved the eye again and reopened the exact
  same search as Lesson 3: re-ran it at the new arm length and found the safe margin before the spike
  cliff had shrunk back down to roughly its original razor-thin width (~0.1 degree) at this shorter
  reach - shorter arm, shorter reach, less room to sit comfortably clear of the corner. Landed on
  `maxAngle = 107` at the time. That overshot the other way - the very next screenshot called the
  camera too SMALL - so the arm settled at 9.5/20 (splitting the difference between 13/27 and 6/13,
  same ~0.475 ratio), which moved the eye a third time and needed a third re-run of the same search:
  safe margin back to ~0.5 degrees, `maxAngle = 111.3` (`visionFov` still 80, unchanged), leftmost
  reach at stepCrate2.x + ~1.6 units, zero spikes across 55..111.3 - checked the identical way each
  time (exact min/maxAngle, fine stepped sweep, radial-jump scan). **Any future resize of
  `NECK_LENGTH`/`LENS_LENGTH` needs this same re-tune of `beamCamera.maxAngle` right alongside it** -
  the two are not independent knobs; changing one without re-running the search is exactly how the
  "far corner vs. spike" cliff gets crossed by accident, and this has now happened three times in a
  row. The beam has its own far-end support leg (`cameraLeg`, same `rightLeg`/`table.png` pattern,
  `tableDecorations` + `boxes`) so it doesn't float - see the "nothing should visibly float" rule
  above.
- Ground dressing right after the camera, ALL under the beam's own span (between the mount and
  `cameraLeg`, not past the leg): `fillerBarrels`, then three wood crates in a brick-like stagger -
  `woodCrateBaseLeft`/`woodCrateBaseRight` (two full crates side by side, touching - zero gap between
  them, `woodCrateBaseRight.x == woodCrateBaseLeft.right`) with `woodCrateTop` resting on both
  (bottom flush with the base pair's own top), offset 20 units into `woodCrateBaseLeft`'s own 68-unit
  width so most of it (~71%) sits over the left crate and the rest (~29%) over the right one. **Only
  these three crates and `stepCrate2` draw with `woodencratenew.png` (`LevelLayout.woodCrates`) - the
  two barrels stay `barrel.png` (`LevelLayout.barrels`), unchanged.** A first pass swapped all four
  ground-dressing boxes to wood-crate art, misreading a screenshot where all four happened to look
  roughly crate-shaped; corrected on request back to two-and-two. Whichever art a box uses, its
  footprint/height and place in `boxes` (so collision/climbing/occlusion) never changed. `woodCrates`
  is a tag on `LevelLayout`/`GameWorld`, wired through `GameWorld.createFromLayout` and
  `GameplayScene.kt`'s box loop exactly the way `barrels` already was (tiled in real 48-unit
  increments, same as barrels/tactical crates) - the general pattern for giving a box distinct art
  without touching its collision behaviour at all. `stepCrate2` (the climb-up-to-the-beam crate,
  defined earlier in `LEVEL_3_LAYOUT`) was added to `woodCrates` too on a later request ("replace the
  solid crate left to the two barrels with these new crates as well") - it's the only crate-shaped
  box positioned before/left of `fillerBarrels` in this section, so that request is read as referring
  to it.
  **The wood-crate art needs a crop, unlike crate.png/barrel.png**: its own alpha content (strict
  bbox, threshold >10, same measuring method as table.png's own crop) sits well inside the raw
  canvas - stretching the RAW image into a box's exact bounds left visible empty space below the
  art, reading as the crate floating above the ground. The asset itself was swapped once already
  (`woodcrate.png` 1376x1143 -> `woodencratenew.png` 1536x1024, on request, "replace the wooden
  crates with woodencratenew.png") and the crop re-measured from scratch for the new file each
  time - do not reuse an old crop rect across an asset swap, the margins are different. Current
  crop is `RectangleInt(68, 86, 1401, 839)` for `woodencratenew.png`. `GameplayScene.kt` slices to
  that rect once before the box loop and reuses that slice, the same approach as `tableBitmap`'s own
  `legSlice`/`plankSlice`. Re-measure this crop (a bitmap bounding-box scan) if the wood-crate
  asset is ever replaced again - **Python 3.12 with Pillow 12.3 IS on PATH here** (`python`/`py`),
  which is what `tools/art/*.py` and level 6's crane-silhouette scan use; PowerShell +
  `System.Drawing.Bitmap.GetPixel` works too. No ImageMagick. See
  `testLevel3GroundDressingUnderBeamHasBarrelsAndWoodCratesSeparately`.
  **This staggered arrangement replaced an earlier straight-up "single crate + a 2-tall stacked
  pair" layout, on request** ("arrange the three wooden crates in a new way: two crates touching
  each other, other one on top of them but more part of it is on the crate on the left"). That
  earlier layout also had a `LevelLayout.messyWoodCrates` tag giving the crate/stacked-pair group a
  small per-tile rotation jitter for a "not neatly kept" look - on a later request ("make the
  properly horizontal without that weird alignment"), the jitter was dropped rather than combined
  with the new stagger: `messyWoodCrates` (the field, the wiring through `GameWorld`, and
  `GameplayScene.kt`'s rotating-pivot rendering code) was **removed entirely**, not just left unused
  with an empty list - it's genuinely gone from the codebase now. Every crate in the ground-dressing
  cluster sits perfectly square; the stagger itself is what reads as "not neatly kept" now.
   **The top crate is lowered 2 units (`woodCrateStackSink = 2.0`)** into the base pair to eliminate
   a visible floating gap: `woodencratenew.png`'s corner tabs/ears extend 32px above (1.83 units) and
   34px below (1.94 units) the horizontal slats, so staggering the crate by 20 units horizontally left
   its bottom ears hanging over the base crates' recessed slats with ~2 units of visible sky; sinking
   by 2 units seats the ears firmly on the base crate's top beam and meets the base crate's middle ear
   with the top crate's bottom beam. Then, past the beam
  and its leg: `finalHangingCrate` (an unguarded crate, jumped across at beam height - same-height
  jump, matching the crate -> beam move at the level's own opening), `hangingEndCrate` (a crate
  perched on TOP of it, flush with its right corner, resting on finalHangingCrate's own top surface,
  not floating, tall enough - 48, under `Player.maxJumpHeight` 51.2 - that crossing it is a hop, not a
  climb), then `finalPlatform` - a plain 340-wide ground block with no crate/table art (falls through
  to GameplayScene.kt's generic rough-block render, the same "normal platform" look as level 1's
  `block2`/`block3` in `GameWorld.createDefault`). Width was bumped 260 -> 340 on request ("increase
  the length of the platforms right of the unmanned crate") - a pure landing-zone size change; it
  doesn't touch the jump gap (only platform height/player size affect that) and `platformCrate`'s own
  centering re-derives automatically from `finalPlatformWidth`.
  **Height dropped 144 -> 96 on a LATER request** ("make the platform on the right smaller to be
  able to climb up") - it used to be raised to the SAME rise as `cameraBeam` itself
  (`finalPlatform.top == finalHangingCrate.top`, a same-height jump across both gaps, not a drop then
  a climb back up); now it's inside `Player`'s own climb window (`climbMinHeight = maxJumpHeight =
  51.2`, `climbMaxHeight = 115.0` - see `Player.findClimbTarget`), matching this level's own other two
  climbs (crate -> tablePlank, stepCrate2 -> cameraBeam, both exactly 96). It still rests flush on the
  ground below (`bottom == groundY`), so it's not a floating ledge and needs no
  `floatingClimbTargets` exemption to be climbable. This also means `finalHangingCrate -> finalPlatform`
  is no longer close to a same-height jump - it's now a real ~50-unit DOWNWARD jump across the same
  56-unit gap, which only ever makes an already-cleared same-height jump easier, never harder (see
  the gap's own binary-search history below), so it wasn't re-verified at the same razor precision -
  just confirmed directly via the walkthrough test after the height change.
  **The `cameraBeam -> finalHangingCrate` and `finalHangingCrate -> finalPlatform` gaps were never
  actually jumpable at their original 120/70(/80) values, for two whole rounds, and nothing caught
  it** until a walkthrough test finally drove a real player through them. Simple projectile
  arithmetic (`moveSpeed * flightTime`, 132 * 0.64s = ~84 units) overstates what this engine's
  collision code actually allows: once the falling body's OWN HEIGHT starts vertically overlapping
  the target platform's slab while the player is still short of it horizontally,
  `Player.updateStep`'s horizontal collision pass treats the incoming platform as a WALL (not "still
  airborne, still falling toward it"), pinning the player against its near face until they've sunk
  well past it and simply drop into the gap. Binary-searching the real `Player.update`/`GameWorld`
  loop (not arc math, and not a reused old number) against THIS exact geometry (296 top, 144 tall,
  real player size) puts the true ceiling at ~56.5-56.66 units. Both gaps were first fixed to a very
  safe 40 (leaving a lot of that budget unused - a trivial walk-across, not a felt jump), then on
  request ("increase the gap ... just enough to be jumpable") widened to 55: re-ran the same binary
  search fresh rather than assuming the old 62-67 estimate still applied, and separately scanned how
  early a jump press can land before the edge and still clear it (a wide 0-22-unit-early window
  succeeds at 55, so it's forgiving on timing, not a hairline). On a further request for "a little
  bit" more, re-scanned that same timing window at several points between 55 and the ~56.5 ceiling
  and found NO drop-off anywhere in that range (still the same 0-22-unit-early window at every point
  tested up to 56.5) - so there was no reason to stay at 55. Landed on **56**: genuinely wider, while
  still holding back ~0.5-0.66 units from the hard ceiling (56.5 itself was judged too close - it
  sits in the same 0.25-unit search bracket as the first confirmed failure at ~56.66). **If either gap (or any other
  same-height jump in this game) is ever widened again, re-run both the binary search AND the
  timing-slack scan against the CURRENT geometry (not reused numbers from a prior round) with a real
  walkthrough test (drive `world.update` through it end to end, assert the player actually lands on
  the far side)** - not point-sampled probes, not projectile arithmetic, and not a test that only
  checks some downstream X was reached, since the level's own full-width ground floor can satisfy
  that on its own even when every elevated jump in between is missed (this is exactly how
  `testLevel2HangingCratesGapIsBeatable` gave false confidence for two rounds - it asserts a target
  X, which the ground path alone already reaches). See
  `testLevel3CanJumpFromCameraBeamAcrossToFinalHangingCrateAndOnToFinalPlatform`.
  `finalPlatform` holds a crate and a two-stacked pair on ITS OWN top surface, centred along its width
  and touching each other (`platformCrate`, `platformStackedCrates` - resting on the platform, not
  floating above it, and their y-position derives from `finalPlatform.top` so they followed the height
  change automatically), then the exit.
- **A second camera (`poleCamera`), added on request, watches this same crossing.** Mounted not on
  a beam but on a freestanding post - `pole` (`LevelLayout.poles`, pole.png), standing at
  `finalPlatform`'s own left corner ("the left corner of the platform right of the unmanned hanging
  crate"). The pole itself is deliberately **not** in `boxes` ("make it not interactable" - no
  collision, the player walks straight past/under it) - see
  `testLevel3PoleStandsAtFinalPlatformLeftCornerAndIsNotInteractable`.
  **Poles are explicitly EXCLUDED from `GameWorld.createFromLayout`'s `occluders` list, unlike
  `tableDecorations`** - a first pass included them (real drawn geometry, same reasoning as any
  other solid prop), but reported directly against a screenshot: `poleCamera` mounted right on top
  of its own pole had that pole block its own downward view, and the shadow-casting turned that
  self-occlusion into a polygon that reads as a flat-edged rectangle instead of a cone ("light cone
  becomes weird ... it become rectangular"). A camera occluding its own mount is a real geometric
  consequence of a thin vertical occluder sitting directly below the eye, not a bug in the strict
  sense, but it looks broken on screen - so a pole now blocks nothing, the same as a swing hook or
  any other prop that's real geometry but not solid enough to matter for sightlines.
  **The pole no longer has a chain above it - removed on request** ("remove the chain from the
  camera pole"). `GameplayScene.kt`'s `renderChainAbove(parent, sourceBmp, cropX, cropY, cropW,
  width, topY)` helper (pulled out of `renderHangingCrate` when the pole first got a chain) is still
  there and still used by `renderHangingCrate` itself - only the pole's own call to it was removed,
  the helper wasn't deleted.
  **Instead, the pole (and now `poleCamera` itself, see below) gets the OTHER half of what made that
  chain read as different from every other (fully opaque, near-black) element in this game: its
  opacity** ("add the same effect that is added to the chains (opacity etc.) to the camera pole
  which makes it look different from other elements"). Sampled directly from `chainedcrate.png`'s
  own chain pixels rather than guessing a number: R=18 G=22 B=28 **A=137** (~54% opaque), against
  that same asset's solid crate at R=0 G=0 B=0 A=255 - the chain isn't drawn with any runtime
  filter, that translucency is baked into the art. Applied as a runtime `translucentEffectAlpha =
  137.0 / 255.0` in `GameplayScene.kt` (shared between the pole and `poleCamera` - see below) instead
  of baking a second translucent asset - same visual effect (lighter, washed-out, reads as different
  from the fully-opaque crates/barrels/etc. around it) without needing new art.
  **On a further request ("also make the camera in that position same effect as the pole"),
  `poleCamera` itself (the mount plate + rotating lens, not just the pole under it) also renders at
  that same `translucentEffectAlpha`** - `beamCamera` is unaffected, still fully opaque.
  `LevelLayout.translucentCameras` (a subset of `cameras`, matched by `CameraSpawn` identity, not by
  index) carries this tag through `GameWorld.createFromLayout` into `GameWorld.translucentCameras`
  (the same `Camera` instances as in `cameras`, not reconstructed) - `GameplayScene.kt`'s
  `cameraContainers` creation checks `c in world.translucentCameras` per camera and sets `.alpha`
  accordingly. This is the general pattern to reuse if another camera ever needs its own distinct
  look: tag it via `LevelLayout.translucentCameras` (or a new list, if the visual differs), don't
  special-case by array index.
  **`poleCamera`'s own sweep (minAngle 20 / maxAngle 160 / visionFov 50 / visionRange 230) is a
  genuinely wide, mostly-horizontal left-right pan, not beamCamera's mostly-downward nod** - on
  request, "rotates left and right ... the light cone of it should go from the boxes in the right
  [`platformCrate`/`platformStackedCrates`, sitting on `finalPlatform` itself] to the box in the
  left [`finalHangingCrate`, across the jump gap]". Checked the same way beamCamera's own reach was
  checked (`VisionSystem.computeVisionPolygon` directly, not the angle numbers alone) - see
  `testLevel3PoleCameraSweepsLeftAndRightAndReachesBothFlankingBoxGroups`. FOV/range were both
  reduced from an earlier 70/260 on request ("reduce the cone size of that camera") - re-derived the
  sweep's reach at the smaller size rather than just shrinking the numbers and hoping: at 220 range
  the leftward reach fell just short of `finalHangingCrate.x` (a ~0.2-unit miss - the earlier
  `testLevel3PoleCameraSweepsLeftAndRightAndReachesBothFlankingBoxGroups` genuinely failed at that
  value), so range was bumped to **230**, which clears it with a real ~10-unit margin - not a
  hairline fit. **Whenever this camera's cone size, mount position, or target box positions change,
  re-run the same reach check (not just eyeball a smaller-looking cone)** - a "smaller cone" can
  silently stop reaching one of its two targets, which is exactly what happened here on the first
  attempt at the reduction.
  `Camera.NECK_LENGTH`/`LENS_LENGTH` are shared, global constants (no per-instance override) -
  `poleCamera` uses the exact same 9.5/20 arm as `beamCamera`, not a value tuned for its own mount.
   **Mount lowered to `pole.y`** (flush with the pole's top cap, was `pole.y - 5.0` which hovered the
   ceiling-bracket plate 5 units in the air with empty sky beneath; at `pole.y` the plate rests solidly
   on top of the column cap) - a pure render-position change. Sweep speed increased 0.6 -> **0.85** on request.
   **Exit path and ground length**: the corridor from `finalPlatform.right` to the extraction booth
   (`finalExitX`) was lengthened 150 -> **300 units** on request; `finalWorldWidth` extends `finalExitX + 460.0`
   so the ground spans the entire extraction checkpoint booth (~117) and exit fence (~312) with margin.
- **`finalHangingCrate` sits 2 units above `cameraBeam`, not exactly flush anymore** ("lift the
  unmanned hanging crate a little bit"). This one DOES have a physics budget, and it is almost
  entirely spent already: the 56-unit gap (see its own history above) sits only ~0.5-0.66 units under
  the hard ceiling for a TRUE same-height jump, so any required vertical rise eats directly into that
  sliver. Binary-searched the real `Player`/`GameWorld` loop again (same method as the gap search,
  this time with an asymmetric launch/landing height) and found the beam -> crate leg (now a small
  upward jump) stops clearing 56 units at a lift of ~2.03 - picked **2.0**, which keeps the exact
  same jump-timing slack as an unlifted same-height jump (0-22 units early, unchanged) with a thin
  sliver of distance margin left (~56.48 max reach at this lift, vs the 56 actually needed). **2.0 is
  not a stylistic pick - it is close to the max this specific 56-unit gap can absorb. A bigger lift
  needs a narrower gap to go with it; re-run the same binary search (not a bumped-up number) if that
  trade is ever wanted.** (The crate -> platform leg was ALSO a small downward jump at the time this
  lift was picked, and falling toward a lower target only ever makes a same-height jump easier, so it
  wasn't re-checked at the same precision then - a LATER, separate request dropped finalPlatform's
  own height a lot further, turning that leg into a real ~50-unit drop; see finalPlatform's own
  paragraph above.) See `testLevel3CameraBeamSection` and
  `testLevel3CanJumpFromCameraBeamAcrossToFinalHangingCrateAndOnToFinalPlatform` for both jumps'
  current exact numbers (`beam.top - finalCrate.top == 2.0`, `finalPlatform.top - finalCrate.top ==
  50.0`) - neither is a same-height jump by exact equality any more, on either leg.
- Verified on JVM desktop screenshots in stages; **not on Android or iOS**. The uncommitted working
  tree is ahead of the last commit here - check `git status` before assuming which state is pushed.

## Level 4 ("04: Blind Spot") - conveyor belt run, `LEVEL_4_LAYOUT`

Replaced the barrel-wall + hook-swing layout of the same name, later recovered from history as level 5
(see "The swing move"). Current:
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

## Level 6 ("06: Missing Container") - `LEVEL_6_LAYOUT`

Five sections; the layout's own doc comments in `LevelData.kt` carry the reasoning, as with
level 3. Section 1 is a lever-fired moving crate ridden into a swing hook; section 2 is a forced
fall into a pit under an overwatch guard, then a climb; section 4 is a timed climb under a swinging
gantry load and section 5 a plank, a patrolling guard and a switched laser curtain (both below);
section 3 is the crane crossing, rebuilt
2026-09-23 on request ("take the crane to left, so that the player can climb onto it from the
otherside of the gap ... the vehicle part of the crane should be just right of the lever ... if it
is too high to be climbable, make the height of the platform after the lever shorter and put the
crane there").

**The crane is now the way across, not scenery.** Its machine (`CraneDef`, placed by
`boomLength` so the tracked base - not the boom tip - lands where the level wants it) stands ~33
units past `lever_3`, and its boom reaches ~460 units back over the lever, the two ground barrels,
the ground gap and the last ~176 units of `tallBlock`. The player walks into the boom on
`tallBlock`, climbs onto it, walks its whole length and comes down the machine's own silhouette to
the exit, which moved past the machine. The ground-level walk only reaches the tracks, which are
deliberately unclimbable, so it dead-ends (the retreat to `tallBlock` stays open and is covered by
a test).

**The boom's height is pinned exactly, and the crane's SIZE is what falls out of it.** "For the
climbing animation to work, this long beam should be his head height": `boomBounds.top` lands on
the crown of a player standing on `tallBlock` (`tallBlockTopY - 96`), which also makes the mantle
a 96-unit rise - this game's own canonical climb height, shared with crate -> terrain and
stepCrate -> cameraBeam. An earlier pass had the beam at chest height (75-unit rise; legal by
`climbMinHeight..climbMaxHeight` = 51.2..115, but the climb animation read wrong against it), so
**a rise that merely sits inside the window is not good enough here - it has to be 96.**
`CraneDef.heightForBoomTop(baseY, boomTopY)` derives `craneHeight` (~146, up from a hand-picked
125) from that requirement plus the platform height, so moving either end re-sizes the machine
instead of silently breaking the climb - never hand-pick `craneHeight` again. The platform stays
`groundY - 48` (392), flush with the barrels, which is why `endTerrain` AND `cranePlatform` both
dropped from 96 to 48 tall and the barrel stack became two barrels side by side. Headroom under
the boom comes along for free: a crane's base always sits `0.8053 * height` below the boom's
underside (118 here, against the 96 a standing player needs).

**`CraneDef` collides as three boxes, not one** ("dont just use 1 bounding box ... so walking on it
doesnt feel like flying"): `boomBounds` (the hanging lattice, crop rows 7..88), `bodyBounds` (the
front section, crop columns 730..1390, topped at the boom's own height because the boom art runs
right over it) and `houseBounds` (the superstructure past the boom's end, roof at crop row 169).
Edges came from a per-column alpha scan of `crane.png`'s crop, same method as the guard/table
crops. **The tracks' own deck is deliberately NOT a step**: the boom hangs only `scale * 226` above
it (73 units at this crane's size, vs a 96-tall player), so at any size this game would use, a box
with its top down there just lets the player jump in and wedge under the boom - checked directly,
not assumed. The boom needs `floatingClimbTargets` (a hanging boom fails the floating-ledge check
by construction); nothing else on the machine does.

**Climbing under the boom finishes crouched** (`Player.findClimbTarget`/`ClimbTarget.endsCrouched`,
added for this level on request: "when he climbs up this, make him climb up crouched"). Coming back
along the ground and climbing `tallBlock` from the gap side lands the player under the boom, where
there is crouching room (56) but not standing room (96): the climb is allowed and ends in a crouch,
and the existing can't-stand-up-under-a-ceiling rule holds it until they crawl out. Without it they
hauled up into a standing pose inside the beam and were wedged - unable to move either way - until
they happened to press crouch themselves. **The engine fix that came with it applies everywhere**:
the headroom check used `box.left` regardless of direction, so a climb approached from the right
tested the wrong edge of the box entirely. It now tests at `climbLandingX`, the same helper
`startClimb` positions with, so the check and the landing cannot disagree.

### Section 4: the gantry-gated climb (2026-09-23)

Added on request: "after the crane, add a platform that is climbable but there is a hanging crate
very close to the surface level which makes it unclimbable. pressing that lever makes it move left
and right so the player has to time when the crate is not there to climb up." It is the last thing
before extraction - `exitX` moved past it and `worldWidth` to 4390.

**The gate is the CLEARANCE, not the crate.** `Player.findClimbTarget` only refuses a candidate
when the landing has room for neither a standing body (96) nor a crouched one (`crouchHeight`, 56)
- with anything above 56 it just returns `endsCrouched` and the climb still goes through (see
section 3's own boom). `gateCrateClearance` is **30**, so the parked crate refuses the climb
outright. Anyone re-tuning this has ~26 units of headroom before the puzzle quietly turns into a
crouch-climb. The crate parks over the landing itself, which is `climbLandingX` - 6 units in from
the edge the player comes over, not the middle of the block.

**`lever_3` is the trigger** ("the lever for the hanging crate should be the one left of the
crane"), which finally makes the ground dead-end worth walking: the floor route stops at the
machine's tracks, and that lever is what the trip buys. It is thrown BEFORE the boom crossing, on
the far side of the machine from the crate it drives, so the sweep is **not `oneShot`** - one pull
powers the gantry for good and the player has however long the crossing takes. (Section 1's swing
crate is the opposite: one attempt, re-armed when it returns to rest.) A player who crosses the
boom without pulling it arrives at a block they cannot climb; the retreat back over the machine and
down to the lever is open (the cab roof and the machine's top are both climbable from the platform
side), and a test drives exactly that whole route.

**`phaseOffsetSeconds = periodSeconds / 2`**, so the cosine starts at `t = 1` - the parked blocking
position. Without it the crate teleports to the far end of its own sweep the instant the lever is
thrown, because `MovingPlatform.update` drives `x` straight off its clock.

**The sweep goes LEFT and stops 8 units clear of the cab, and that limit places the whole
section.** Left, because everything right of the landing then stays permanently clear - whoever
just climbed can walk out from under the gantry instead of being swept off the block (a right-hand
sweep pins them at the landing, which is where the crate parks). It stops at the cab because the
route down off the boom walks along the machine's top and across that roof: a load crossing there
sweeps through the player standing on it, and further left it would pass through `bodyBounds`
itself. So `gateCrateMinX = houseBounds.right + 8`, the rest position is one sweep (150) right of
that, and the block is 100 right of the rest position - i.e. the section is placed **from the
crane's own cab outwards**, which is as far left as it goes ("take the platform and the hanging
crate more to the left"). `testLevel6GantryCrateNeverSweepsIntoTheMachineOrOverItsWalkway` pins
both halves of that.

**It is still not visible from the lever, and cannot be.** The camera shows 1040/1.35 = ~770 world
units - the authored canvas width, which the responsive viewport rule guarantees is the NARROWEST
any device gets (a screen wider than 2.167 sees a little more; nothing ever sees less) - so
standing at `lever_3` the view ends at ~3330, and the crane's own cab ends at 3303. The
machine fills the frame from the lever to the right edge. Moving the load any further left is the
one thing the paragraph above forbids. If that has to change, the options are moving the crane
itself right (which re-tunes section 3's boom, and the boom's height is pinned to the tallBlock
climb) or giving the crate its own sweep on the near side of the machine.

Sizing, for whoever re-tunes it: the crate must travel 68 before its right edge passes the
landing's left, which against a 150 sweep with cosine easing leaves the landing clear for ~53% of
every cycle - a ~3.7s window at `periodSeconds = 7`, against a ~2s climb. The crate is level 2's
own hanging container (`isVariant1 = true`, 174x38 - the same box and the long `chainedcrate.png`
rigging as `LEVEL_2_LAYOUT`'s `hangingCrate1`), on request: "use the hanging crates from level 2".

Verified in the running game, not just the model: thrown from the lever, crossed, timed, climbed,
walked out and extracted.

### The crane's top: denied the mantle, and NOT made jumpable (2026-09-24)

"he should not be able to climb this" -> "he should be able to climb this but not to the top part
from the crane" -> "if this is jumpable height, let the player jump onto it but just not climb" ->
"he is floating here now. YOU DONT HAVE TO MAKE THIS JUMPABLE. MAKE IT JUMPABLE ONLY IF IT IS."

The machine is crossed one way: in off the boom from tallBlock (96, a climb), east along it, **down**
onto the rear deck (52.4), **down** onto the platform (91.6). The deck is still climbable from the
platform beside it. The machine's top is not reachable from the deck at all.

**`LevelLayout.unclimbableBoxes`** (carried through `GameWorld` into `Player.findClimbTarget`, which
skips any box in it) denies the **mantle only** - the box still collides, is still landed on, and is
still jumped onto if the rise is inside jump height. Level 6 lists `crane.bodyBounds`.

**Why the deny list rather than geometry.** `Player.climbMinHeight` IS `Player.maxJumpHeight`
(51.2), so climbing and jumping are complementary: inside jump range a ledge is jumped and never
mantled, above it a ledge is mantled and never jumped. The deck-to-top rise is 52.4 - just over the
line, so it read as a climb. Lifting the deck's collision 6 units off its drawn roof to buy the jump
**was tried and rejected**: the deck's art is flat all the way across, so the player simply floats
above it. **Do not bend collision off the art to change which move applies** - deny the move and let
the ledge be out of reach.

**`maxJumpHeight` is the analytic apex, not what a jump clears.** Stepping at 1/60s the feet peak
about **48.5** above the take-off, so a rise of 48.4 "fits" on paper and in practice scrapes the lip
and drops back (measured). Leave a few units under 48.5, not under 51.2, whenever a jump has to land.

**A climb is a jump PRESS against a face** (`Player.updateStep` consults `findClimbTarget` when the
jump is consumed), not a walk into it. A simulation driving `moveInput` into a wall with
`jumpInput = false` never climbs and proves nothing, and a HELD press is consumed on the first frame
- pulse it. `testLevel6CraneTopIsNeitherClimbedNorJumpedFromItsOwnRearDeck` covers all of it.

### Section 5: the hanging platform, the switch and the laser curtain (2026-09-23)

"after that section, continue that platform and add a crate at the end. after that add a hanging
platform from level 3. there should be a lever on top and a guard after that moving left and right.
the lever turns off 3 lasers that are there from the hanging platform to the ground. the bottom is
the only path out." Reworked twice the same day. Current shape, after the second pass ("lift the
floating platform to the level of the top of the crate on the edge of the platform before it",
"move the crate to the edge of the platform", "reduce the size of the laser emittors and
receivers", "remove the chain holding the floating platform", "move the lasers to the left", "move
the floating platform to the right"):

**The step crate is the crossing.** `endCrate` (68x48) stands flush with `gateBlock`'s far lip and
the platform hangs past the gap at the CRATE's top, not the block's. So the block is walked to its
end, the crate is jumped (48 is inside `maxJumpHeight`'s 51.2), and the jump across leaves from the
crate's lip and lands level. Everything in the section is derived from `gateBlock.right`, so the
whole arrangement moves together if the block ever does.

**The 65-unit gap between the crate and the platform is the section's hinge, and it does two jobs.**
(45, then 55, then 65 - "move the floating platform to the right", then "increase gap between the
platform and floating thing".) The ceiling is physics: `jumpSpeed` 320 against `gravity` 1000 is
0.64s of flight, `moveSpeed` 132 carries the body **84.5** units in that time, and the landing
spends about 6.5 of them getting a foot onto the far lip - so **~78 is impossible** and everything
below it is margin for pressing jump early. 65 leaves ~13 units of margin, 70 leaves 8, and the test
measures that margin rather than trusting arithmetic. It is also wider than the player's own 36, so
simply WALKING off the lip drops them to the corridor instead ("he should be able to drop down to
reach the place with lasers"). Jump across for the switch; walk off for the way out.

**An earlier version of this note said the ceiling was 61, from a stale "needs `gap + 18` to land"
figure.** That was wrong and cost a round of guessing; the 84.5/6.5 numbers above are measured by
simulation in `testLevel6HangingPlatformIsLevelWithTheCrateAndDropsIntoTheCorridor`, which scans how
early the jump may be taken and still land. **A probe like that has to use a `<=` threshold, not a
1-unit window**: the body moves 2.2 units per frame, so a narrow window is stepped straight over and
the probe reports a false "no margin".

**Nothing may stand in that chute**, and this was tried twice before settling: a prop there has to
be climbable from the corridor floor AND leave a body-width lane beside it, which does not fit - and
worse, the platform's near face pins a standing body on top of anything 48 tall in the chute with no
way down at all (Player's horizontal pass pushes it back on rather than letting it fall). The drop
is therefore **one-way**, which is what the checkpoint on the platform is for: a player who goes
down before throwing the switch walks into the curtain, dies, and respawns up top to try again.

**Nothing is drawn holding the platform up either.** It was briefly hung from the hanging crates'
chain art (`LevelLayout.suspendedTableParts`, since deleted along with its `GameWorld` field and the
`GameplayScene` branch) - "remove the chain holding the floating platform" took that back out, so
this platform is a deliberate, asked-for exception to the "nothing floats with no structure under
it" rule. Do not re-add rigging to it without being asked.

**The lasers stand 45 apart at the platform's far end** ("put the 3 lasers close together"), hung
from its underside to the floor, `isAlwaysActive`, all carrying `mechanismId = "lvl6_exit_lasers"`.
`lever_4` on the platform matches it and `GameWorld.triggerLever` calls `Laser.disable()` on every
one - permanent for the run (`Laser.isDisabled`, cleared only by `reset()`). This is the first
switched laser in the game; everything before it only cycles. **The third beam hangs off the
platform's own tip on purpose**: anywhere else and the platform is its own bypass - walk to the tip,
step off, land past every beam with the switch never thrown. That is why "move the lasers to the
left" was done by **shortening the platform** (420 -> 370) rather than sliding the bank inwards: the
beams hang off the tip and travel with it.

**`LaserDef.emitterScale`** (new, 1.0 everywhere else, 0.55 on this curtain): scales the drawn
emitter/receiver housings only - `LaserVisual`'s `unitLength`. Collision is still `beamThickness`
and does not move with it. "Reduce the size of the laser emittors and receivers", and it is per-beam
rather than global so the other levels' hazards are untouched.

**The guard patrols the platform past the lever**, which is the section's actual ask: there is no
cover up there, so the jump across has to happen while he is walking away, and the way out is back
down the chute rather than along the platform past him. His beat runs **170 units** now, opened up
at both ends on request ("increase the length of the path of guard from either side"), and
`lever_4` sits 24 from the platform's near end rather than 46 ("take the lever little more to
left"), so the switch is under the body almost as the jump lands.

**Lengthening that beat moved where the player can wait.** With his near turn at `plank.left + 110`
he can see a body standing on the step crate (the crate's top is level with the platform, well
inside his 220 of vision), so the whole approach is now one burst from the block below: wait a
body-length short of the crate's face, then hop the crate and jump the chute while he walks away.
`testLevel6SecondSectionCrossesPitAndReachesExit` drives exactly that, and it is the reason that
test failed when the beat was first lengthened - the autopilot was still waiting up on the crate.

**The exit is `exitlvl7.png`**, level 6 only: one silhouette carrying the shed and its yard fence,
instead of the shared `entrance.png` booth + `exitfence.png` pair. Authored size 1505x809 (the
source drop's own file, cropped to its alpha bounds); on disk it is the 1024x512 POT resample of
that, which is why the aspect is written out as a literal rather than read off the bitmap.

**Its box lives in the level, not the scene** - `LevelLayout.exitStructure` (new; `GameplayScene`
just draws it, and falls back to the booth + fence pair when a level has none). It has to, because
this building is placed against the level's own geometry rather than against `exitZone`: **401 tall
standing on `groundY`** (200, then 335, then this - "increase size of the building at end and make
sure it is on the floor", then "you can increase its size"), with its left edge tucked 8 units under
the hanging platform's far tip. The height is what does the connecting - the art's own balcony deck
starts 0.5215 of the way down from its roof, so `440 - 0.4785 * 401` puts that deck's **top surface
flush with the platform's own top**, and the platform reads as a walkway running off the building's
balcony ("the middle part should be connected to the balcony"), which is also what stops it reading
as a slab hanging in mid-air now that its chains are gone. Past ~400 the extra height is only roof
that the camera's 356-unit window cannot show while the player is down on the corridor floor.
`worldWidth` is 5300 to cover the building's far edge, and `exitZone` sits 53 units INSIDE the
silhouette rather than flush with its left edge, so on this level the player walks into the building
rather than touching its corner.

**It hung 12 units off the floor for a round, and the cause is worth knowing: `PIL.Image.getbbox()`
is not an alpha crop.** It bounds every channel, so it kept 27 rows of fully transparent pixels that
still carried RGB under the building - invisible in the file, 3.3% of dead space at the bottom of
the draw box, and the taller the art is drawn the bigger the gap gets. **Crop art on `alpha > 0`
explicitly** (`np.nonzero(alpha > 8)`), then POT-resample; check afterwards that the silhouette
reaches the last row. exitlvl7.png's authored size is 1501x780 after a proper alpha crop (it was
recorded as 1505x809).

**`MovingPlatformDef.crushesOnContact`** (new, and so far only section 4's gantry crate): "when
trying to climb if he touches the bottom side of the crate it should be mission failed". A mistimed
climb has the body still coming up when the load sweeps back over the landing - that is a kill now,
not a wedge. Checked in `GameWorld.update` against the player's own box and **only from below**
(feet under the crate's underside), so standing on top of a moving platform is still standing on a
platform.

### The two stance animations this needed (2026-09-23) - both cut from existing frames

Reported against a screenshot ("try to generate an animation for climbing + crouching and also
crouching -> jumping because current one also goes through the beam"). Neither needed new art, and
that mattered: the player atlas is the game's biggest memory consumer and grows in 16.8MB pages.

- **Climb that ends crouched** (`Player.CLIMB_CROUCH_END_PHASE = 0.73`). The climb clip already
  holds the whole action: mantle (raw 100-144) -> settled deep crouch on top (145-175) -> standing
  up (176-224). A crouched climb simply stops at raw **182** instead of 224, so the stand-up never
  plays. 182 is measured, not chosen: scoring feet-aligned silhouette overlap of every frame from
  140 to 215 against the crouch clip's held pose picks the frames coming back out of the settled
  crouch, and 182's silhouette is 141 frame-px against the crouch pose's 139. Every unit of height
  is already gained by phase 0.603 (`CLIMB_RISE_CURVE`), so this cuts pose frames only, never the
  ascent. `GameplayScene` then hands
  straight to the **held** crouch pose - going through the crouch machine's own "entering" phase
  would start it at the clip's standing frame and snap the character upright through the ceiling
  before lowering him back into it, which is the same bug in a different place.
- **Crouch -> jump** (`crouchJumpSpring*` in `GameplayScene`, `crouchAllowsJump` in `Player`). A
  jump can now start from a crouch, gated on the same headroom test standing up uses - under a
  ceiling it stays impossible, which is what keeps a standing pose out of the beam. The launch
  plays the **crouch clip backwards** over 90ms (139 -> 245 frame-px, then the jump clip's own
  launch frame at 240, a 5px handover) instead of cutting from the held crouch straight to the
  launch pose, which was a 139 -> 240 snap in a single frame. `Player.crouchSuppressedByJump` drops
  the stance for that whole jump even if the crouch button is still held - **without it the hitbox
  stays 56 tall while the sprite is the 98-unit standing jump, which is what "jumping while
  crouching goes through the beam" looked like**: the head stops 40+ units inside the beam because
  the box under it is crouch-sized.
- **`Player.CEILING_ART_MARGIN` (3.0)** - the character is DRAWN up to 2.6 units taller than his
  collision box (jump clip peak 251 frame-px against the 244.36 the box is scaled from; climb 248
  before its own scale correction, idle/crouch 246, walk 242), so a jump stopped with its box flush
  under a beam still put the head a few units inside it. Ceilings now stop the box that much lower. It is deliberately NOT applied
  while crouching, whose poses are drawn shorter than their own box. **Careful with this one**: a
  first attempt also widened the ceiling DETECTION rect and swapped the live `vy < 0.0` for a
  captured `wasRising`, and that combination is not covered by the suite - keep it to the stop
  position unless there is a reason.
- **A crouch survives the fall, and the stand-up happens on the floor** (`Player`'s
  `crouchedAtTakeoff` + `mustStayCrouched`; `GameplayScene`'s jump machine skips a crouching player
  and its crouch machine runs while `crouchedInTheAir`). Nothing stands a player up in mid-air, so
  crouch-walking off a ledge fell with the 56-unit crouched box while the sprite switched to the
  ~98-unit drop pose - the drawn head jumped 40 units above the body and came out the top of the
  boom ("when dropping while crouching, the player goes above that beam"). Releasing the button in
  the air uncoiled him on the spot for the same reason, so the stance is now **held until the feet
  land** and the crouch machine's own "exiting" phase plays the stand-up there ("make him drop down
  in the crouch position and then get up"). Only a fall that *began* crouched is locked: pressing
  crouch after the feet are already off the ground is a tuck the player can come out of, and a jump
  out of a crouch drops the stance at the launch (`crouchSuppressedByJump`). The gait is held still
  while airborne rather than walking the legs through the air, and because such a fall never
  reaches the jump machine, its touchdown plays the landing thud from the crouch machine instead.
- **The crouched climb's tail runs at 3.0x** (`CLIMB_CROUCH_TAIL_PHASE`/`_SPEEDUP`). The clip's
  settled-on-top footage is a deep tuck, 80-116 frame-px against the crouch pose's 139, so at
  normal pacing the character sits balled up far smaller than anywhere else in the game for ~0.29s
  - reported twice, as "it feels like the character is smaller" and "the character seems smaller
  when he is climbing than in other positions". Nothing moves in that stretch (rise and shift
  curves are both already at 1.0), so speeding it to ~0.08s costs nothing.
- **The climb clip really is drawn 12-22% small, and that is now deliberate.** Measured on the raw
  plates in `art-source/climb/`: the character grows **24-26%** between the hang and the last
  standing frame (head-to-toe 416.8 -> 517.9 px and tracked head radius 23.81 -> 30.01, two
  independent measures agreeing to 1.5%, so it is a plain uniform camera dolly, not perspective),
  while the scale baked into the processed frames only removes 13% (0.5357 -> 0.4743, recoverable
  per frame as `sqrt(processedAlphaArea / rawAlphaArea)`). The old "the correction cancels the
  camera to within 0.2%" note in this file was wrong: it compared a *hanging* silhouette's
  hand-to-toe span (457) against a *standing* one (516), which is not the same measurement twice.
  A per-phase correction that held the character at one size for the whole move was built, tested
  and then **reverted on request** ("change back the size of the person in climbing animation to
  original size that was there"), so **do not "fix" this again without being asked.** What it costs
  is visible: the character is smaller through the hang and the tuck and grows back during the
  stand-up. What the shipped scale buys is the hang's geometry - the drawn reach is exactly one
  body height, so on a ledge the player's own height (this game's canonical 96-unit climb) the hand
  lands flush on the lip instead of a head above it, and `CLIMB_GRIP_CURVE` is measured off these
  plates as shot.

**The jump plate's raw 1-11 is NOT usable as a crouch wind-up** despite what this file's own
`PlayerAnimations` note calls it: measured on the plate it dips from 1031 to 991 px, a 4% knee
bend, nowhere near the crouch pose's 57% of standing. The crouch clip in reverse is the only real
"rising out of a crouch" footage in the set.

Verified by a full walkthrough test that drives the real loop from `farTerrain` to the exit over
the boom, plus JVM desktop screenshots of the three areas (boom tip over `tallBlock`, machine
beside the lever, exit past the machine). **Not on Android or iOS.**

## Level 7 ("07: Stolen Manifest") - `LEVEL_7_LAYOUT`

Designed 2026-09-23 as a high-tension linear crawling gauntlet similar in structure to Level 4's
conveyor run. The player infiltrates the secure facility through a continuous 5200px ventilation
duct:

- **Enforced crouching**: `ventCeiling` at `y = 344.0..372.0` with `groundY = 440.0` leaves 68px
  vertical clearance across the duct (player crouching height is 56px, standing is 96px). Standing
  up is physically blocked throughout the entire shaft. `LevelLayout.playerStartCrouched = true`
  spawns the player already crouched, and `Player.mustStayCrouched` ensures the player cannot stand
  up while under the duct ceiling even if crouch input is released.
- **Vent Fans (`VentFanDef` / `VentFan`, `src/game/model/VentObstacles.kt`)**: Industrial exhaust fans
  blowing high-velocity backward air (-120..-130 px/s). Holding forward is pushed backward; the
  player must spam-tap the forward button (`forwardTap`), delivering rhythmic forward stride
  impulses (+48 px/tap) to muscle through the wind. To ensure human tapping rates (3–5 taps/sec)
  reliably advance the player even when the forward button is released between taps, a
  `fanPushbackDampenTimer` (0.22s) dampens pushback between successive presses, and `GameplayScene.kt`
  latches `touchRightTap` so fast on-screen clicks/taps are never dropped across frame cycles.
- **Camera Bots (`CameraBotDef` / `CameraBot`)**: Small wheeled/tracked surveillance drones that
  patrol back and forth along the vent floor, casting a forward vision light cone (`visionRange = 120.0`,
  FOV 40 degrees). Walking into their vision cone raises an alert and triggers Mission Failed. The
  player must sneak up from behind within `deactivationRange = 52.0` and press the INTERACT button
  to permanently deactivate the drone.
- **Pressurized Steam Pipes (`SteamPipeDef` / `SteamPipe`)**: Top-mounted, bottom-mounted, and paired
  nozzles blasting lethal pressurized steam on timed cycles (1.3..1.5s active, 2.0..2.5s inactive)
  with a 0.45s warning progress flare. Touching active steam causes instant Mission Failed,
  deflectable once by the Laser Shield gadget (`activePowerups.isLaserShieldActive`).
- **Sequencing & decoupled hazard zones**: Obstacles are decoupled into clean, distinct stages so
  fans do not blow the player into active steam pipes or drones. Safe recovery and staging zones
  (100..300px) separate every hazard, housing 6 manual checkpoints.
- **Visuals and performance discipline**: Procedural textures (`steamParticleBitmap`, `windStreakBitmap`,
  `botEyeGlowBitmap`), volumetric vision cones, nozzle LED indicators, and duct frame structures are
  housed in `VentFxAssets.kt` to protect `GameplayScene.sceneMain` against the JVM 64KB bytecode limit.
  `src/game/model/VentObstacles.kt` remains 100% pure Kotlin with zero `korlibs.*` imports, verified
  by `ZeroKorlibsLintTest`.
- Verified by unit tests in `GameplayModelTest.kt`: `testLevel7LayoutStructureAndProperties`,
  `testLevel7VentCeilingEnforcesContinuousCrouch`, `testLevel7VentFanPushbackAndSpamTapForwardImpulse`,
  `testLevel7CameraBotPatrolAndDeactivationFromBehind`, `testLevel7SteamPipeHazardsAndLaserShieldDeflection`,
  and full end-to-end traversal `testLevel7SimulationPlayableWalkthrough`.

## The push stance (`resources/player/push{,transition}`) - built 2026-09-24, live on level 13

Two clips cut by `tools/art/prep_push.py` from `Downloads/charAnimations/push` (144 raw frames) and
`pushtransition` (96), both 360x640 half-res plates. **That script's header is the source of truth
for every cut and the crop geometry - re-run and paste, don't hand-edit the Kotlin**, same rule as
`prep_guard.py`. Currently a stance with nothing to push: `INTERACT` toggles it, level 13 is the
bare stage it is tried out on, and a real pushable prop would gate it on range the way levers do.

- **These plates are framed ~6.4% smaller than every other clip** - standing measures 484 rows
  against crouch's 517 and swing's 503 on plates of the identical size. The scale that maps them to
  the shared sprite size is this clip's own `244.36 / 484`. Get that wrong and the character changes
  size the moment he braces; both clips come out with idle's own 245px standing silhouette, and
  `pushtransition` frame 0 IS the standing pose (2.7% silhouette disagreement against idle frame 0),
  which is what makes the handover in and out of idle free.
- **Frames are 180x256, cropped symmetric about the STANDING body centre** (raw column 173), not
  about the union bbox. The sprite is anchored at the frame's horizontal centre, so keeping the
  standing centre means entering the stance shifts the character by nothing; the braced pose then
  leans out over the collision box's front edge with its feet planted behind it, which is what
  pushing looks like. Same idea as climb's 200-wide frames.
- **The loop keeps EVERY raw frame while the transition is halved, and that is about the move's
  slowness, not the footage.** The loop is distance-driven, so the braced move speed sets its
  display rate: 56.6 world units per cycle at ~53 u/s is 1.07 **seconds**, so 20 frames would be
  19fps and read as a flick-book, where the full 40 is 37fps - walk's own 36. This is the swing
  clip's lesson arriving from the opposite direction (there, halving hurt because the action was
  fast). **Redo that arithmetic rather than reusing the conclusion if `PUSH_MOVE_FACTOR` moves.**
- **`PUSH_STRIDE_PER_HEIGHT` = 0.59, and getting it took four goes.** Measure it by sub-pixel phase
  correlation of the ground-contact alpha profile between consecutive frames of the **processed**
  output, pooled over both feet: 3.629 +/- 0.039 sprite px/frame, i.e. 0.594 +/- 0.006. What does
  NOT work, each tried first: integer bbox edges on the raw plates over short partial stances (gave
  0.58 - the ends of a stance are the foot rolling heel-to-toe, not the body translating); a
  least-squares fit of one foot's contact centroid (gave 0.61, while the other foot on the same
  frames gave 0.58 - a centroid moves with the patch's shape and the two boots differ); and
  anything measured at the wrong end of the raw clip, since **the character accelerates through the
  footage** (2.8 px/frame over the opening cycle against 7.4 late, because it opens with him
  leaning into a load that is not moving yet). The in-game check below can only resolve this to
  +/-5%, so it confirms the number but cannot pick between candidates.
- The loop window is raw 94..133. Start 88 has the tightest seam (0.53 of an adjacent frame) but
  the worst entry from the braced rest pose (5.7 frames of motion); 94 trades that for a 1.09 seam
  and a 2.82 entry, which is the right way round - the seam is crossed every cycle, the entry only
  when the player starts moving. `GameplayScene` therefore always re-enters the loop at frame 0.
- `GameAudio.PUSH_STEP_PHASES` = `[0.33, 0.90]`, measured by a contact-band scan of the shipped
  frames. **Not** walk's `STEP_PHASES` - this gait's stance/swing split is different.
- The state machine is split the usual way: `GameWorld` owns `isPushStanceHeld` / `pushStanceBlend`
  (0..1, running both directions so one clip serves the lean-in and the stand-up, exactly as the
  crouch clip does) and suppresses jump/crouch and scales movement to `PUSH_MOVE_FACTOR` while
  braced; `GameplayScene` owns which frame that draws as. **The toggle is edge-detected inside
  `GameWorld`** because the scene hands over the raw button LEVEL, not an edge - reading it directly
  flips the stance every frame of one press. `canInteract` is forced true in a `pushStanceDemo`
  level, otherwise the scene's own `interactPressed` gate swallows the press before the world sees
  it. Facing is **locked** for the whole stance (walking backwards drags the load, it does not spin
  the braced silhouette around).
- Verified by `jvmTest` (`testPushStance*`, `testLevel13*`) and on JVM desktop end to end: idle ->
  lean -> braced -> push forward -> push backward with the facing held -> jump and crouch both
  refused -> stand up -> idle -> normal walk and jump restored. The planted foot was measured
  against the ground in the running game by screenshot burst (the camera is locked to the player, so
  a planted foot must slide backwards at exactly the player's own speed) and it does, within the
  +/-5% that method resolves. **Not on Android or iOS.**

## Level 13 ("13: Final Proof") - `LEVEL_13_LAYOUT`, the push stage

Deliberately **empty**: flat ground wall to wall, no guards, cameras, boxes, hazards, start fences
or anything hanging. It used to be a `GameWorld.createDefault` level with a patrolling guard and a
corridor derived from `guardPatrolMinX/MaxX`; that was cleared out so the push animation can be
watched with nothing walking into frame or killing the player mid-stance.
`LevelLayout.pushStanceDemo = true` is what makes INTERACT a stance toggle, and **nothing else in
the game sets that flag** - a test pins that.

`playerStartX = 560`, not near the left wall: at 160 the camera clamps against the world edge and
the whole lean-in played underneath the on-screen D-pad, which on a stage whose only job is to show
the animation is the one thing that must not happen. The camera window is ~770 world units, so the
spawn has to be at least half of that from 0. The exit is still at the far end so the level remains
completable - a long walk at the braced ~53 u/s, which is the point.

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
- Levels 6+ guards (`GameWorld.createDefault`'s per-level `guardSpeed`) still have 48-tall hitboxes and
  draw as half-height men; they need a 96-tall pass (that default's patrol geometry is tuned to 48).
  Level 5 (`SIDE_SCROLL_LEVEL_LAYOUT`) has no guards at all, so it isn't part of this.
- Owner decisions: the red "visor" rect is gone (only in the no-art fallback); guard beams are one
  colour in every state (the pip over his head shows detection) - **don't reintroduce a colour ramp on
  the cone**. Camera cones similarly stay one steady white color (`Colors.WHITE.withAd(0.32)`) in every state
  on owner request (the pip above the camera indicates detection) - no yellow/red alert ramp.
- **Camera rotation stops on player detection (`Camera.detectionPauseDuration = 2.5s`)**: When a camera's
  vision cone detects the player, the camera stops rotating immediately (`Camera.isDetectingPlayer = true`).
  While the player remains in vision, the camera stays stopped at that angle. When visual is lost (player
  ducks into cover or exits the cone), the camera remains stopped for the same duration guards stop moving to
  investigate (`Guard.investigateDuration = 2.5s`, via `Camera.detectionPauseTimer = 2.5`), resuming its sweep
  only once that pause completes. Checkpoint respawn and `SMOKE_SCREEN` activation reset this pause.
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
   the swing clip put it at ~22.5M and the two push clips at **26.4M** (84 frames at 180x256,
   roughly one more page of heap and one of texture - accepted, see "The push stance").
   **Adding frames is not free** - see the ATLAS BUDGET
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
2. **Multiply by 3** - the authored canvas is 1040x480 (`ScreenLayout.DESIGN_WIDTH/HEIGHT`; the
   real canvas now carries the device's aspect and is never smaller than that - see "Responsive
   layout"); a 1440p phone renders at 3x (2.25x on 1080p). Sizing from virtual numbers gives a
   third of the needed resolution; it looks fine on desktop and mushy on the phone.
3. **Round to a power of two** in both dimensions (up, unless within a couple of percent of the lower).
4. **Resample, never pad** - transparent padding is stretched into the draw box with the art.
5. `python tools/art/pot_resize.py resources/newthing.png 512 512` - premultiplied-alpha LANCZOS,
   refuses non-POT targets; `--check` reports sizes.
6. Verify what reaches the screen: render old and new into the device-pixel box and diff (<~1/255 mean).
7. Wire via `SceneAssets.bitmap("x.png")` (default `minified = true`: asks for mipmaps, warns if not
   POT) or `minified = false` for anything drawn ~1:1 or larger and anything **sub-sliced** (mip
   levels bleed across slices).

**Crop new art on ALPHA, not `Image.getbbox()`** - that helper bounds every channel, so a source
whose transparent margin still carries RGB (most exports do) keeps an invisible border that becomes
dead space inside the draw box. `exitlvl7.png` was cropped that way and the building floated 12
units off the ground; `np.nonzero(np.array(im)[:,:,3] > 8)` is the crop that matters, and the check
is that the silhouette reaches the first and last row of the finished file.

**Aspect ratio is NOT a concern for stretch-to-box assets** (stated backwards twice before) - every
draw is `size(box.width, box.height)`, the file's aspect never reaches the screen. **Never derive a
drawn size from a loaded bitmap's dimensions** - write a literal naming the authored size. Use a
premultiplied-alpha-aware resampler or silhouette edges pick up fringes. `truck.png`/`entrance.png`
are pre-mirrored - a tool that normalises orientation would undo bug #8's fix.

**The guardrail**: `SceneAssets.warnIfNotPowerOfTwo` prints one line per offending asset per run:
`[SceneAssets] 'hook.png' is 154x2136 - NOT power-of-two, so mipmaps are silently skipped for it.`
**Expected output as of 2026-09-23: 15 lines** - `hook.png` (extreme aspect, rounds badly),
`woodcrate2`, `pole`, `lever_bottom`, `lever_top`, `newrope` and the nine `rope_dissolve_*` frames,
all of which arrived with the lever/rope work and have not been resized. A **sixteenth** line means
something new needs sizing; the list itself is worth shortening when someone is in the art pipeline
anyway.

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
- **Character animation plates** (`tools/art/prep_guard.py`, `tools/art/prep_push.py`): crop one
  shared box per clip, symmetric about the character's own body centre so a horizontal flip does
  not shift him, feet pinned per frame to the bottom edge, resampled premultiplied. **Measure this
  plate's own standing silhouette rather than reusing another clip's scale** - the push plates are
  framed 6.4% smaller than the crouch plates at the identical file size, and the climb plates dolly
  mid-shot. Pick loop windows by autocorrelation plus a seam scan, and measure any
  `*_STRIDE_PER_HEIGHT` by phase-correlating the ground-contact profile of the **processed output**
  (see "The push stance" for the three ways of measuring it that are wrong).
- **Tight-crop a silhouette to its alpha bounds** before stretching it into a box (dead margin
  stretches too); re-derive box width from the cropped aspect at the fixed height.
- **Wood crates (`woodcrate2.png`)**: 1536x1024 silhouette crate with rustic horizontal planks (replaced
  striped `woodencratenew.png`). Cropped to strict alpha bounds (`RectangleInt(165, 144, 1206, 721)`,
  threshold A > 10) so it sits flush on the ground without floating empty space. Used in Level 3 for
  `stepCrate2` and the 3-crate ground stack under the camera beam (`woodCrates`).
- **Chroma-key an opaque JPEG-style asset**: R/G/B all >= 200 -> transparent, else opaque, then crop.
- **Find a seam inside a composite illustration**: scan column-wise opaque density for a sharp drop.

## Smaller features and decisions

- **REMOTE_TRIGGER powerup was a complete no-op, plus a real shared-mutable-state bug found while
  fixing it (2026-09-20)**: `ActivePowerups.activate()` (`Powerup.kt`) had
  `PowerupType.REMOTE_TRIGGER -> Unit` - activating it (buying it, pressing its tray slot) did
  **nothing at all**, on every platform, confirmed by the owner testing it on level 6. Its own
  Store description ("Triggers closest mechanism without needing to find its switch." -
  `StoreScreen.kt`) was never implemented. Fixed in `GameWorld.kt`: `activatePowerup()` now special
  -cases `REMOTE_TRIGGER` - picks the nearest `!isActivated` lever by `player.center.distanceTo(...)`
  and throws it exactly as an in-range interact would (extracted the shared cascade into a new
  private `triggerLever(lever)`, also now used by the normal `interactInput` path so there's one
  code path, not two). Returns `false` (refuses) if no un-thrown lever exists in the level - checked
  ahead of spending the item via a new `GameWorld.hasRemoteTriggerTarget()`, called from
  `GameplayScene.kt`'s `tryActivatePowerup` before `profileStorage.consumePowerup(type)`, so a level
  with no levers (or one already fully thrown) never burns the item for nothing. It remains a true
  one-shot - `isActive(REMOTE_TRIGGER)` is still always `false` (no running state to block a second
  use; the "already active" guard below doesn't apply to it, only whether a target exists).
  **While building the test for this** (`testRemoteTriggerActivatesNearestLeverWithoutBeingInRange`,
  right before `testLevel6LeverCrateSwingCrossesToLandingCrate`), a real pre-existing bug surfaced:
  `GameWorld.createFromLayout` passed `layout.levers`/`layout.hookCrates` straight through
  (`levers = layout.levers`, no copy), unlike every other level object (`MovingPlatform`/`Camera`/
  `ConveyorCrate`/`Laser`), which are freshly built from an immutable Def/spawn each call. Since
  `LEVEL_6_LAYOUT` etc. are top-level `val`s (computed once per process) holding the actual mutable
  `Lever`/`HookCrate` instances, every `GameWorld` built from the same layout shared and mutated the
  SAME objects - `createFromLayout` already reset them at the top (`for (lever in layout.levers)
  lever.reset()`, pre-existing, meant for "quit and replay the same level without restarting the
  app") but that only protects sequential re-creation, not two `GameWorld`s alive at once (exactly
  what a test suite does, and what surfaced this: my remote-trigger test threw the shared lever and
  left it thrown, which broke the swing test that ran after it in the same JVM). Fixed by giving
  `createFromLayout` its own fresh copies - `levers = layout.levers.map { it.copy() }`,
  `hookCrates = layout.hookCrates.map { it.copy() }` - both are plain data classes so `.copy()` is
  sufficient (no nested mutable refs beyond `HookCrate.bounds`, itself an immutable `Rect`). The old
  reset-on-create loop is now redundant defense-in-depth, not load-bearing, but left in place.
  `jvmTest` green on a forced `--rerun-tasks` full run (129 tests, 0 failures) - this class of bug
  is exactly the kind a partial/up-to-date test run can hide, so a real rerun mattered here more than
  usual.
- **F2: desktop-only debug key to top up every gadget by +3 (2026-09-20)**: `GameplayScene.kt`,
  same `Platform.isJvm`-gated pattern as F1's noclip fly (see below) - calls
  `profileStorage.grantDebugPowerups(3)` (`GameProfile.kt`; the method already existed, fully wired
  to `GameProfileStorage`/`MapBackedGameProfileStorage`/tested in `GameplayModelTest.kt`, but had
  never actually been called from anywhere in the app before this). Added so a session can try out
  every gadget without grinding coins first - JVM's own `PlatformStorage` impl
  (`paywall-build/src/jvmMain/kotlin/PlatformStorage.jvm.kt`) is a plain in-memory
  `ConcurrentHashMap`, never persisted to disk, so this only ever affects the current desktop run
  and never touches a real save on Android/iOS.
- **Gadget tray polish + no re-activating a running gadget (2026-09-20)**: `GameplayScene.kt`'s
  `TrayEntry` boxes (the powerup row that unfolds from the corner bolt, `trayEntries`/
  `refreshTrayLabels`): (1) the 30px icon is now centred on both axes in the 42px box (was
  horizontally centred but pinned 2px from the top to leave room under it) - `xy((slotSize -
  slotIconSize)/2.0, (slotSize - slotIconSize)/2.0)`. (2) The stock-count text is always
  `COLOR_TEXT_LIGHT` (white) now, not the old gold/green swap keyed on live state - it only shows at
  all while idle. (3) A live gadget no longer prints "ON"/"`Xs`" text; it shows a semi-transparent
  white overlay across the WHOLE box instead (`TrayEntry.drain`, a dedicated `Graphics` layered on
  top of both the icon and the count text - added last among the box's children, so it paints over
  them), like a curtain: bottom edge fixed, top edge sinking toward the bottom as
  `getRemainingTime(type)/type.duration` runs out. Alpha `Colors.WHITE.withAd(0.14)` - started at
  0.42, lowered to 0.22 then to 0.14 across two rounds of "make it more transparent"; re-lower this
  same constant if asked again. Shape: `roundRect(x, y, w, h, corner, corner)` (with `corner =
  drainCornerRadius.coerceAtMost(filledH / 2.0)` applied to both top and bottom corners). The overlay
  covers the entire box when active; for level-duration gadgets (`PowerupType.isLevelDuration` -
  `LASER_SHIELD`, `NOISE_SUPPRESSION`, `CHECKPOINTS`) it stays at 100% full for as long as it is on.
  For timed gadgets that wear down, the overlay drains downward while remaining curved at the top to
  match the rounded shape of the box behind it (owner request from screenshot 2026-09-20: square top
  corners looked discordant with the curved frame). Quantised to fortieths like the corner bolt's own
  horizontal drain bar (`slotDrain`, same file) to avoid a per-frame vector rebuild.
  **Corner clamp**: clamping `corner = drainCornerRadius.coerceAtMost(filledH / 2.0)` guarantees
  that once `filledH` drains below `drainCornerRadius * 2`, the corner radii never exceed available
  height, preventing korlibs tangent-point overflow. Inset: `drainInset = 1.6` keeps the overlay
  neatly inside the frame's stroke outline.
  **Second corner-overflow bug, fixed same day, this one mid-animation not just at full/empty**:
  reported against a mid-drain screenshot of INVISIBILITY (a real 10s timed gadget, so it actually
  passes through every fraction, unlike a level-duration one pinned at 1.0). Root cause is in
  korlibs' own per-corner `roundRect(x, y, w, h, rtl, rtr, rbr, rbl)` (`korlibs.math.geom.vector.
  VectorBuilder`, via `Arc.arcToPath`): unlike its single-radius `roundRect(x, y, w, h, rx, ry)`
  overload (which clamps `rx`/`ry` down to `w/2`/`h/2` when the box is smaller than the requested
  radius), the per-corner overload does **no clamping at all** - each corner's rounding is a
  canvas-style tangent construction that places its tangent point `radius` units from the corner
  along both adjoining edges, with zero awareness of the other corner sharing that same edge. Once
  `filledH` (the overlay's current height) drains below `drainCornerRadius` (9), the bottom
  corners' tangent points land PAST the shrunken rect's own top edge, so the rounded corner arc
  bulges outside the nominal box bounds - visible as white spilling past the frame while the
  animation is actively draining, not only at the full/empty extremes. Fixed by clamping:
  `val bottomRadius = drainCornerRadius.coerceAtMost((filledH - topRadius).coerceAtLeast(0.0))`,
  same discipline korlibs' own single-radius overload already applies, just done by hand since the
  per-corner one doesn't. **Any per-corner `roundRect` call anywhere in this codebase needs the same
  manual clamping if the rect's own size can ever shrink smaller than a requested corner radius** -
  korlibs will not catch this for you on that overload. Not verified with a real screenshot after
  this specific fix (traced `Arc.arcToPath`'s actual tangent-point math from its own source jar to
  confirm the mechanism, rather than guessing) - if it recurs, the JS/Wasm target
  (`build.gradle.kts`, declared for local browser preview only, see "Tech stack") could be launched
  in the Browser pane tool for a real visual check without needing the JVM desktop screenshot
  PowerShell recipe.
  **Third round, still reported after the corner clamp fix ("still it overflows from side and
  bottom")**: attempted a real screenshot check this time via `./gradlew runJvm` + the documented
  PowerShell GDI capture recipe, with a temporary rig in `GameplayScene.kt`'s `addUpdater` (grant +
  activate INVISIBILITY, force its timer to a fixed fraction every frame so there's no race against
  a live 10s countdown) - **inconclusive, not a dead end worth reproducing verbatim**: this machine
  had a SECOND, pre-existing "Infiltrate: Shadow Heist" window already running (the owner's own,
  separate from anything this session launched - confirmed by PID/start-time, never touched), which
  cost real time to safely disambiguate window handles from (by PID + process start time, never by
  title alone - two windows shared the exact same title and, at least once, the exact same screen
  rect). One relaunch's window also rendered with only ONE tray icon instead of five despite the
  model-level state being confirmed correct via temporary `println`s (`isActive=true`, a real
  quantized 0.5 fraction) - never root-caused (plausibly transient from rapid kill/relaunch
  process churn, not a code bug; the very first capture that session, on a window that had been
  running undisturbed the longest, DID show a normal 5-icon tray). **Debug rig fully reverted** -
  grep `debugScreenshotSetupDone`/`TEMP screenshot`/`TEMP:` in `GameplayScene.kt` to confirm none of
  it is still there if this is ever picked up again. Given repeated difficulty pinning the exact
  mechanism (and now two fixes that didn't fully resolve it), the fix this round is deliberately
  **geometry-proof rather than another targeted patch**: `drainInset = 1.6` (matching the frame
  stroke's own inner edge - stroke sits at inset 0.95, thickness 1.3) and `drainCornerRadius`
  lowered `9.0 -> 7.0` to match; the overlay's rect is now built entirely from `(drainInset,
  drainInset)` to `(slotSize - drainInset, slotSize - drainInset)`, never from `(0, 0)` to
  `(slotSize, slotSize)` - it is drawn a fixed, generous margin inside the frame's visible stroke
  line, so it cannot reach that line regardless of whatever the remaining overflow mechanism turns
  out to be (sub-pixel rounding, the fill/stroke inset mismatch between the frame's own two
  `roundRect` calls, or something not yet identified). The corner clamp from the previous round is
  kept (still needed - `filledH` here is `boxH * fraction` off the smaller inset box, so it can
  still shrink below `drainCornerRadius`). **If a fourth report comes in, don't add a fourth patch
  on top of this one - get a real, clean screenshot FIRST** (the JS/Wasm Browser-pane route noted
  above is likely more reliable on this machine than another JVM desktop window hunt) and diagnose
  from an actual pixel-level view rather than reasoning further from korlibs' source alone. **First cut of
  this was a thin vertical gauge bar along the box's edge - corrected on request** ("by a drain bar
  i mean like a half
  transparent white overlay on the whole square that drains down") - if this is ever revisited,
  it's the full-box curtain that's wanted, not an edge gauge. (4) A gadget
  already active can no longer be re-triggered until it ends: guarded in both
  `GameWorld.activatePowerup()` (returns `false` if `activePowerups.isActive(type)` - the
  authoritative gate, covered by `testActivatePowerupRefusesReactivationWhileAlreadyActive`) and
  `GameplayScene.kt`'s `tryActivatePowerup` (checked *before* `profileStorage.consumePowerup(type)`,
  so a refused re-press doesn't burn an inventory item for nothing). `jvmTest` green (128 tests);
  the tray's visual geometry itself (icon centring, bar rendering) is not verified on a real
  screenshot/device - same caveat as the detection-pip entry below, no headless KorGE canvas
  harness exists.
- **Detection pip: "!" for heard noise vs the clock for actually seen (2026-09-20)**:
  `GameplayScene.kt`'s per-guard/camera pip (`guardPips`/`cameraPips`, drawn above the head). The
  clock only ever renders for a guard/camera actually in `world.detectingGuards`/`detectingCameras`
  (a real vision hit, progress = `world.alertProgress`). When sound is heard (`Guard.onNoiseHeard`,
  flagged via `g.investigatedFromNoise`), `drawInvestigateMark` draws a plain "!" badge above the
  guard without any background circle (circle backdrop/outline removed on owner request 2026-09-20).
  **Stealth Boots suppression**: When `world.activePowerups.isNoiseSuppressed` is active (Stealth Boots),
  `g.onNoiseHeard` is never triggered and `paintPip` completely suppresses the "!" mark so guards
  never show a sound indicator while the player is wearing stealth boots. Furthermore, `onVisualLost`
  (losing line-of-sight mid-alert) sets `investigatedFromNoise = false`, preventing guards from showing
  the sound "!" mark when visual was simply lost rather than sound heard. Seeing always wins over
  investigating in `paintPip` (checked first). Cameras have no INVESTIGATING state, so they only ever
  get the clock, unchanged. Tested via `testGuardInvestigatedFromNoiseFlag`.
- **Debug noclip flight (F1, JVM desktop only, 2026-09-20)**: `GameplayScene.kt` toggles
  `world.noclipFlying` on `Key.F1`, gated on `korlibs.platform.Platform.isJvm` (a real multiplatform
  runtime check from the transitive `korlibs-platform` dependency - no new expect/actual needed,
  and it stays off on Android/iOS even with a physical keyboard attached). `GameWorld.update()`
  branches on `noclipFlying` right after the `isLevelComplete||isGameOver` guard: free 2D movement
  at 420 u/s (arrows/WASD horizontal, jump=up, crouch=down), zero collision, zero gravity, and it
  skips detection/alerts/hazards entirely (guards, cameras, lasers, conveyors, timers all freeze) -
  a pure level-layout inspection tool, not a "play while invincible" mode. Only clamped so feet
  can't go below `groundY` and x stays within `0..worldWidth - player.width`; vertical is otherwise
  unbounded. Toggling off just resumes normal physics next frame (player falls from wherever it
  stopped). Covered by `testNoclipFlyingBypassesCollisionGravityAndDetection` in
  `GameplayModelTest.kt`. JVM `jvmTest` green; not run on a real device (F1 has no touch-control
  equivalent by design, so there's nothing to verify there).
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
- **Settings → About panel (`SettingsScreen.kt`)**: Links order is "PRIVACY POLICY" (opens `https://infiltrate.saysplit.app/privacy/` via `LocalUriHandler`), "CONTACT US" (opens `https://infiltrate.saysplit.app/support/`), "CREDITS & LICENSES" (expandable third-party sound attributions), and "RATE US" (at the bottom of the links list with highlighted white outline: `Color.White.copy(alpha = 0.5f)` vs unhighlighted `0.08f`). Terms of service row removed. Version string is pinned to the bottom of the screen and resolves dynamically across platforms via `com.infiltrate.platform.PlatformInfo` (`expect`/`actual`: `versionName` and `buildNumber` read from `PackageManager` on Android, `NSBundle` on iOS, system/package properties on JVM).
- **Web presence (`site/`, Netlify, e.g. `infiltrate.saysplit.app`)**: `index.html`, `support/`
  (App Store Guideline 1.5 page, Netlify Form with Name/Email/Category/Message, no visible email, no
  FAQ), `privacy/` (on-device storage, AdMob/UMP consent, RevenueCat, COPPA/GDPR/CCPA), `styles.css`,
  `_redirects`, `netlify.toml`. iOS RevenueCat in-app purchase billing is wired (`com.infiltrate.shadowheist`,
  Apple Distribution codesigning & automated TestFlight release workflow in `.github/workflows/ios-testflight.yml`).
  One remaining compliance item: **Apple's App Tracking Transparency prompt is not implemented** while
  AdMob can serve personalized ads - decide (add ATT, or force non-personalized on iOS) before App Store submission.
  A `/delete` page was built and reverted the same day - the game holds no server data (local-only, deleted
  by uninstalling); the owner answers Play Console's deletion question "No".

## Keep this file up to date

This file is the first thing a new chat/agent should read. Whenever you make a decision, discover a
constraint, or change something a future session would need (tooling gaps, CI status, build quirks,
unresolved issues), update the relevant section - or add one - before ending your turn. Treat stale
info as a bug: fix it in place rather than leaving it for the next chat. **Prefer editing the
current-state description over appending a dated "pass" entry** - the file was consolidated on
2026-09-08 and compressed on 2026-09-14 to stop multi-round sagas accumulating; keep it that way.
Where the code carries its own reasoning in doc comments (level layouts, `PlayerAnimations`,
`prep_guard.py`), point there rather than duplicating it here.
