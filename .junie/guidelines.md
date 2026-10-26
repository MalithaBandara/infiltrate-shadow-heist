# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game (look: Shadow Fight / Vector; objective: Robbery Bob).
**Target platforms: Android AND iOS - both required.** Kotlin Multiplatform hackathon submission
(Shipaton 2026). JVM desktop is for local dev/testing only.

## Read this first

**Verification discipline**
1. **"Compiles" is not "verified."** Several bugs below took 2-4 wrong theories first. **Default
   status of everything in this file: `jvmTest` + `android-shell:compileReleaseKotlin` only - not
   seen on a screen, not run on Android or iOS.** Sections say so when more was checked.
2. **A GitHub Actions `continue-on-error: true` step's `conclusion` is not pass/fail** - it has
   reported `success` for undefined symbols, build failures and crashes. Read the raw job log for
   `BUILD SUCCESSFUL`/`BUILD FAILED`/exception text.
3. **Don't trust a stated tool/library version** - check `gradle/libs.versions.toml`, `git log`, CI
   toolchain paths.
4. **JVM CI green does NOT mean iOS is green.** Kotlin/JVM default-imports things Kotlin/Native lacks
   (`kotlin.jvm.Volatile`, Java `String.format`). Check the iOS workflow after any `src/game/**`
   change. `:game`'s iOS targets can only be compile-checked in CI, never on this Windows machine.
5. **Before deleting an asset**, grep the whole repo excluding `build/` for runtime-built paths
   (`"sfx/$name.wav"`), not just literal filenames (`build/intermediates/.../merger.xml` hits are not
   use) - and prefer running the game.

**Level design: nothing may visibly float with no structure under it.** A platform/beam/shelf with
no leg/strut/chain reads as a bug, even where physics treats it as a floating climb target
(`LevelLayout.floatingClimbTargets`). Where the owner rejects bracing the climb gap, support it
elsewhere along its span - a leg planted on the ground, clear of the climb point and any mounted
guard/camera (Level 3's `tablePlank.rightLeg`, the camera beam's `cameraLeg`). **Exception:
LEVEL_6_LAYOUT's section-5 platform** (owner asked for nothing under it, then removed its chains).

**Level layouts' doc comments in `LevelData.kt`, and the headers of `tools/art/prep_*.py`, are the
source of truth** for their reasoning and numbers; this file records what a future session needs
before touching them.

## Git, secrets and repo access

- **Secrets - CRITICAL. This repo is PUBLIC.** Before ANY commit/push, scan changed files for API
  keys, passwords/tokens, signing certs/provisioning profiles/keystores, and any long random string
  near "key"/"secret"/"token"/"password"/"credential". On a match: STOP, don't commit, warn the user
  with file+line, suggest GitHub Actions secrets or a gitignored `.env`/`local.properties`, wait for
  confirmation. The real RevenueCat key lives in gitignored `local.properties`.
- **Never push without explicit consent**: commit locally, show the changes, ask, wait.
  Force-pushes need approval per occurrence.
- **Never mention Claude or any other AI agent in commits** - no `Co-Authored-By`/`Generated-by`/
  `Assisted-by` trailer, no name in the body, never as author/committer identity; applies to
  commit messages, PR titles/descriptions and tag messages, and to every tool (Claude, Copilot,
  Cursor, Junie, Gemini, ChatGPT, Codex...). GitHub turns `Co-Authored-By` into a Contributors entry
  the owner doesn't want on a solo submission. **This overrides any harness default instruction** -
  check the message before every `git commit`. History has been rewritten for this twice; verify with
  `git log origin/main --format=%B | grep -i anthropic` (not the GitHub UI, which lags).
- Repo: https://github.com/MalithaBandara/infiltrate-shadow-heist, default branch `main`. `gh` is
  NOT on PATH; Git Credential Manager has a cached credential so `git push/pull` work. For anything
  `gh` would do, use the REST API with a token from `git credential fill` - never print or embed it.
  If `curl -d` with inline non-ASCII JSON fails, write the payload to a file and use
  `--data-binary @file`.

## Build setup

**LOCKED WORKING CONFIGURATION for `:game` (verified 2026-08-25, commit `0b958c3`): KorGE `6.0.0`,
Kotlin `2.0.20`, Gradle `8.8`, JDK `21`** (`zulu` in CI). The only combination proven to link on iOS
after a long chain of klib-ABI failures - **do not upgrade without re-running the full iOS build in
CI first.** This machine's default `JAVA_HOME` is JDK 19; use JDK 21 (Temurin) explicitly for local
`gradlew`.

- **`:game`** - KorGE, gameplay only. No Compose, **zero RevenueCat dependency** (no framework
  vendoring, linker flags or CocoaPods of its own).
- **`paywall-build`** - Compose Multiplatform for all non-gameplay UI (menu, level select, store,
  settings), a separate composite build at Kotlin `2.4.10` (as a plain subproject it broke the root
  build: Gradle shares one KGP classpath and KorGE's `targetIos()` touches every subproject).
  **Compose Resources trap**: `group = "com.infiltrate"` silently moves the generated `Res` package
  unless pinned with `compose.resources { packageOfResClass = "paywall_build.generated.resources" }`.
- **`android-shell/`** - a fully separate Gradle build, not in `settings.gradle.kts` (Compose needs
  Kotlin >= 2.2.0). Consumes `paywall-build` via `mavenLocal()`, compiles the game from source
  (`kotlin.srcDirs("../src/game/scene")`), takes assets from `../resources`, KorGE from Maven Central.
- JS/Wasm targets stay declared for local browser preview only (never a ship target).
- Payments: RevenueCat `purchases-kmp-core` only - do NOT add `purchases-kmp-ui`.
- **Model sharing**: `paywall-build` compiles `GameProfile.kt`/`LevelData.kt`/`Geometry.kt`/
  `Powerup.kt` etc. from source, so **every file under `src/game/model/` must be pure Kotlin (stdlib
  only, zero `korlibs.*` imports)** - enforced by `ZeroKorlibsLintTest`.
- **Entry point**: `src/main.kt` must expose a parameterless
  `suspend fun main() = main(emptyArray())` (KorGE's iOS bootstrap calls it with zero args). No
  JVM-only APIs in common code - use `korlibs.io.lang.Environment["key"]` or `args.firstOrNull()`.
- **`korge-video` is NOT VIABLE** (tested, fully reverted): stale, doesn't compile against KorGE
  6.0.0, and its iOS backend is a stub that fakes a generated video. For video, re-encode as a
  low-fps PNG/JPEG frame sequence or sprite sheet through KorGE's normal APIs.

**TRAP - do not use the root build to check Android compilation** (`:korge-ldtk` fails there on
clean checkouts and nothing on the Android path builds it). CI runs
`./gradlew :paywall-build:publishToMavenLocal` then `cd android-shell && ./gradlew bundleRelease`.
**To check a `src/game/**` change for Android, build `android-shell`.** It compiles only
`src/game/scene` from source - `src/game/model` arrives through `paywall-build`'s artifact, so
**after a MODEL change run `./gradlew :paywall-build:publishToMavenLocal` first**, or it silently
compiles against the previous model (an up-to-date build in ~1s is the tell). A local
`bundleRelease` fails at `signReleaseBundle` with a bare NullPointerException - `local.properties`
has no keystore passwords; the signed AAB comes from CI (`gradle.yml` decodes the keystore secrets).

**`GameplayScene.sceneMain` lives against the JVM's 64KB method limit, and android-shell hits it
first**: its newer Kotlin compiles the same suspend function ~19% bigger than the root build's 2.0.20,
so `jvmTest` passing proves nothing here (2026-10-22: android-shell failed with `Method too large`
while jvmTest was green). Setup code that is not in a lambda counts; a local `fun` is its own method.
Static set dressing went out that day (`buildWallStencils`, `buildFloorsAndExit`, `buildStaticDecor`):
android 59,180 / 65,535, JVM 49,583 (android 58,071 on 2026-10-26, after the cone caches became
`VisionCone`s). Measure with `javap -c -p` on
`android-shell/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/game/scene/GameplayScene.class`
after adding setup code; move another block into a local `fun` (or a file, as `MechanismSounds`,
`VentFxAssets`) well before it nears the limit.

### CI (`.github/workflows/`) - every push to `main`, no path filters

- `gradle.yml` - `./gradlew jvmTest`, `ubuntu-latest`, JDK 21.
- `ios-build.yml` - `macos-latest`, JDK 21, `chmod +x ./gradlew` after checkout. KorGE's
  `iosBuildSimulatorDebug` (unsigned Simulator only) with `--no-configuration-cache` (KorGE's iOS
  tasks NPE under the config cache), then several `continue-on-error: true` steps building
  `paywall-build`/`ios-shell/` (rule 2 applies). Only `iosSimulatorArm64` has ever been built.
- `ios-testflight.yml` - uploads only on a `v*` tag push or a manual dispatch, never a plain push.

**iOS traps confirmed from raw logs:**
- `@Volatile` in commonMain resolves to `kotlin.jvm.Volatile` (absent on Native) - explicit
  `import kotlin.concurrent.Volatile`.
- Java `String.format()` has no Native impl - `n.toString().padStart(2, '0')`.
- YAML-significant characters in `korge { name = ... }` break `:prepareKotlinNativeIosProject` (KorGE
  writes `name` verbatim into a YAML spec). Avoid `: { } [ ] , & * # ? | < > = ! % @`, backtick and a
  leading `-`. Current: `"Infiltrate - Shadow Heist"`.
- `NSDate().timeIntervalSince1970` -> `Unresolved reference` under Kotlin 2.4.10 - use
  `platform.posix.time(null)`.
- The storage-bridge check's expected string (`OK:coins=350:unlocked=level_1;level_2;level_4`)
  depends on `GameProfile.loadFromStorage()` merging stored unlocks into the profile's default set
  (only `level_1` since build 21 - it used to include `level_5`). Update it if that default changes.

## Release state

- **Build number lives in THREE places - bump together**: `ios-shell/project.yml`
  `CFBundleVersion`, `ios-testflight.yml`'s `build_number` default, `android-shell` `versionCode`.
- **Currently**: Android `versionCode` 24 / `versionName` "1.1.0", iOS `CFBundleVersion` 24 /
  "1.1.0" (2026-10-27: the rebuilt level 12, chain and door sounds, a production build for both stores).
- **Level progression is ON**: `unlockAllForTesting` (`GameProfile.isLevelUnlocked`, "unlock all
  levels for now", 2026-10-03) was deleted for build 24 ("put the level activating method (levels
  activating only when previous one is done) back again") and
  `testGameProfileStorageLevelUnlockingProgression` runs again. A fresh profile unlocks only `level_1`
  and finishing a level unlocks the next (premium unlocks all); `LevelSelectScreen` asks
  `profileStorage.isLevelUnlocked(...)`, the single gate; its premium gate (`id.contains("dlc")`) is
  separate. To unlock everything again for testing, return `true` at the top of that function - and
  take it out before a store build.
- **RevenueCat `purchases-kmp-core` 3.10.1** (since 2026-10-23; Play Console's SDK warning: 3.6.0 can log
  users out when updating from before 3.0.0, and from 2027-01-20 no production/open-testing release may
  carry it - "upgrade to 3.10.0+"). Build 24 is the first release with it: the Android release
  classpath resolves `purchases-kmp-core` 3.10.1 / `purchases` 10.22.1, and `ios-build.yml`'s
  on-simulator RevenueCat verification passed on it.
- **Ads are on REAL units** (`USE_TEST_ADS = false`, both platforms) since build 24 - asked: real
  units for this production build. Build 23 had shipped test units. TestFlight testers must not tap
  them - see Ads.
- All 12 levels are listed (`.take(12)` in `LevelSelectScreen.kt`, `MainMenuScreen.kt`,
  `GameplayScene.kt`). From the Google Play approval gating (2026-09-25) what remains: "Coming Soon"
  chapter placeholders removed (layout-preserving spacers) and Settings showing English/French only
  (`SUPPORTED_LANGUAGES`; the 15-language shortlist is kept in `ALL_SUPPORTED_LANGUAGES`). If a Play
  review ever needs the rain gone, `LevelData.DEFAULT_LEVEL_2.hasRain` is the whole switch.

### Release 1.0 (iOS build 19) - App Store review audit (2026-09-26)

Build 19 ships REAL ad units because the TestFlight binary is the one submitted.
- **The public privacy policy (`site/privacy/index.html`, Netlify, gitignored, deployed separately
  from git)** was rewritten when ads went personalized (it said: no IDFA, no ATT, no consent
  dialog). **It must be live before submitting** (5.1.1/5.1.2); any ads/data change needs the same
  pass over it.
- iOS Restore also accepts a purchased `remove_ads`-like product id, like Android (a Remove Ads
  product with no entitlement would otherwise restore as "nothing found" - 3.1.1).
- The ATT request is delayed 1s after activation/consent-form close (iOS silently drops a
  mid-transition request; "prompt not found" is a routine 5.1.2 rejection).
- Checked clean: Restore on the Remove Ads screen, no other-platform names in UI text, no "Coming
  Soon", no permission-gated APIs, 1024 icon has no alpha, privacy/support URLs return 200,
  ExportOptions `app-store`, debug keys JVM-only.
- **Owner's job, outside the repo**: App Store Connect privacy answers must declare tracking (Device
  ID) or an ATT-prompting app is rejected; the age rating should allow for ad content (AdMob's max ad
  content rating can cap it).

## RevenueCat

- **`:game`'s own dependency was REMOVED 2026-09-12.** It was pinned to `1.9.0+14.3.0` by a klib ABI
  ceiling (the compiler reads ABI `1.8.0`; RevenueCat passed it at `2.0.0+15.0.0`), needed a
  CocoaPods dependency with no Podfile, and `PurchasesBridge.kt` was uncalled stubs. If `:game` ever
  wants purchases of its own, start from `paywall-build`'s approach.
- **`paywall-build`: `purchases-kmp-core:3.6.0`, proven on iOS (2026-08-29)** - links into a real
  `PaywallModule.framework` for `iosSimulatorArm64`; 3.x bundles the native SDK via cinterop (zero
  CocoaPods for RevenueCat); its klib (compiled at 2.3.20) is readable by 2.4.10.
- **Swift compatibility-shim link fix**: Kotlin/Native's linker searches a stale hardcoded Xcode path
  for `libswiftCompatibility*`. Compute the developer dir at configuration time (`xcode-select -p`)
  and add it as a linker search path per target inside `cocoapods { framework { } }` (the
  `swiftLibPath` helper in the AdMob block below).
- **Billing**: `paywall-build/.../StoreBilling.kt` (`expect`/`actual`), called from
  `StoreScreen.kt`'s coin packs; Android wired via `InfiltrateApplication.kt`, iOS via
  `AppDelegate.swift` + `StoreBilling.ios.kt`. On success `profileStorage.addCoins(pack.amount)`
  credits a plain local integer - RevenueCat validates the money, local storage owns the balance.
  RevenueCat's Virtual Currency is deliberately unused (no backend; most grants are gameplay). No
  paywall UI in `:game`'s scenes.

## Ads (AdMob)

**Library: `app.lexilabs.basic:basic-ads`** (other KMP AdMob wrappers had no real Maven Central
publication). Publishes Android/iOS only (no `jvm()`), so every ad host/bridge is `expect`/`actual`
or per-platform. Proven on iOS (a real Google-served banner on a Simulator); **Android ad runtime
untested** (no emulator here).

**CocoaPods gotcha**: `basic-ads` needs `pod("Google-Mobile-Ads-SDK")`. A manual
`binaries.framework {}` alongside `native-cocoapods` still fails to link - `KotlinCocoapodsPlugin`
attaches pod search paths only to the ONE framework it creates per target. Configure that one:
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
| `REWARDED_COINS` | `ca-app-pub-7912148730700666/8440619376` | `ca-app-pub-7912148730700666/4233781051` |
| `REWARDED_GADGET` | `ca-app-pub-7912148730700666/9048379643` | `ca-app-pub-7912148730700666/6397813920` |
| `INTERSTITIAL_LEVEL_EXIT` | `ca-app-pub-7912148730700666/7390779081` | `ca-app-pub-7912148730700666/5874999932` |

JVM falls back to Google's published test IDs (rewarded and interstitial test IDs differ - don't
reuse one for the other).

**Both platforms are on REAL units** (`USE_TEST_ADS = false` in `AdUnitIds.android.kt` and
`AdUnitIds.ios.kt`). Flip both to `true` for any Play Internal/Closed track or TestFlight build
(invited testers clicking real ads violates AdMob policy) - not automatic; grep `USE_TEST_ADS` before
a build. **It is the first thing to check when ads "don't work" on one platform** - a fresh real unit
no-fills for a while; test units always fill.

**"Ad not ready" on every iOS request in TestFlight, with AdMob counting the requests (2026-09-26),
is AdMob's limited ad serving, not code.** AdMob fully serves only an app published on a store AND
linked in AdMob, then reviewed (2-3 days); the iOS app can't be linked before its App Store release.
Android fills (live, linked Play listing). Confirm in AdMob > Apps (status) and Reports (match rate
~0%); retries can't fix a no-fill. Use `USE_TEST_ADS = true` to exercise the flow (TestFlight should
anyway). `basic-ads` drops the `NSError` (`onFailure(AdException())`); AdMob's Ad Inspector on a
registered test device shows the real code.

### Consent and personalization (2026-09-26)

Personalized ads behind a consent flow on both platforms: both `BasicAds.configuration` sites
(`AdMobVerifyContent()` iOS, `MainActivity` Android) use `PublisherPrivacyPersonalizationState.DEFAULT`.
Flow: Google UMP message (EEA/UK/CH only, and only once a message is published in AdMob's "Privacy &
messaging" tab) -> on iOS the ATT prompt -> `AdPrivacy.canRequestAds` flips and the SDK starts.
- **`AdPrivacy` (commonMain) is the gate** - every ad host (continue, interstitial, both Store hosts,
  both platforms) resolves a request immediately while it is false. Never let a new host skip it.
- UMP comes from `basic-ads`' own `Consent` wrapper (no new dependency; Android pulls
  `user-messaging-platform` 4.0.0 transitively): `AdConsent.android.kt` runs from
  `MainActivity.onCreate`/`onResume`, `AdConsentBridge.kt` (iosMain) from
  `AppDelegate.applicationDidBecomeActive`.
- **ATT lives in Swift, only from `applicationDidBecomeActive`** (Apple skips a request from an
  inactive app). Its completion handler is off-main - hop back before `AdConsentBridge.finish()`.
  Skipped under `-ci-test`. `NSUserTrackingUsageDescription` is in `project.yml` (iOS crashes the
  request without it).
- Settings > About shows **AD PRIVACY CHOICES** only while `AdPrivacy.privacyOptionsRequired`.
- **The privacy manifest deliberately keeps `NSPrivacyTracking = false`** - true requires
  `NSPrivacyTrackingDomains`, and iOS 17+ BLOCKS those domains for users who deny ATT (risking no ads
  for them). Tracking is declared per data type: `NSPrivacyCollectedDataTypes` = AdMob 13.8.0's 7
  entries (Device ID the only tracking=true) + RevenueCat's Purchase History, merged by script from
  the CI print and RevenueCat's GitHub manifest - re-derive, don't hand-edit.
- **`SKAdNetworkItems` is Google's full 50-ID list** (from
  developers.google.com/admob/ios/3p-skadnetworks) - re-copy the whole list when Google updates it.
- Verified in CI (runs 36251116781 / 36251116782, raw logs): both frameworks link, shell
  `BUILD SUCCEEDED`, `android-shell bundleRelease` `BUILD SUCCESSFUL`, simulator
  `admob_verify_result` = `initializeCalled=true:personalizationEnabled=true`. **Never run on a device**; the consent message
  can't be seen from CI's US simulator - use `ConsentDebugSettings` geography EEA + a test device ID,
  or an EU VPN.

### Watch ad to continue (`REWARDED_CONTINUE`)

`:game` (2.0.20) can't call `basic-ads`; `paywall-build` can. iOS crosses via a Swift polling bridge:
`GameplayScene` -> `GameContinueAdBridge` -> `AppDelegate.swift` -> `switchToCompose()` +
`ContinueAdTrigger` -> ad -> `GameContinueAdBridge.grantContinue()` + `switchToKorGE()` ->
`GameplayScene` revives at the last safe checkpoint. Android: one process, a plain shared object.
Desktop JVM grants immediately.
- 2 continues per run, each behind its own ad (`GameWorld.MAX_CONTINUES_PER_RUN`; uncapped with the **Checkpoints** gadget). Revival grants 3.0s grace cloak +
  laser grace, returns guards to patrol, snaps the camera. `spawnGraceTimer` (2.0s) suppresses
  alert/laser/crush/fall-off right after spawn/restart while at spawn/checkpoint.
- MISSION FAILED has CONTINUE / RETRY / MAIN MENU (175x62px); once continue is spent, CONTINUE hides
  and the other two centre.
- **With the Checkpoints gadget a death reloads, with no MISSION FAILED sheet**: `world.onGameOver`
  sets `checkpointRespawnTimer` (`CHECKPOINT_RESPAWN_DELAY` 0.9s, so the catch still registers)
  instead of showing `caughtOverlay`; the updater then calls `respawnAtCheckpoint()` +
  `onCheckpointAutoRespawn`. Scene-only, no model test.

### Preloading (2026-09-10) and iOS retries (2026-09-25)

`ContinueAdContent`/`InterstitialAdContent` hoist `rememberRewardedAd`/`rememberInterstitialAd`
outside the show gate so the fetch starts at first composition, reloading whenever the handler is
`NONE`/`DISMISSED`. The Store's coin/gadget hosts are NOT preloaded (a menu button tolerates a wait).
- **Hazard 1**: a background load failure must not resolve a request never made - every hoisted
  failure callback is guarded on `showRequested.value`.
- **Hazard 2**: `FAILING` is a dead end (`rememberXAd` never reloads from it). Android's show sites
  answer with an explicit `FAILING ->` branch resolving like a load failure; **iOS replaces the
  handler instead**, because treating `FAILING` as final made one unlucky early load (racing SDK
  init - iOS starts the SDK in the same frame as the preload) permanent for the process.
- Test ads can't reproduce either hazard (always fill) - force with airplane mode.
- **A handler can't be restarted, but a new one begins at `NONE`**: `key(attempt) { }` discards the
  composition group the dead one was remembered in.
  - `RetryingRewardedAd.kt` (iosMain), the Store's two hosts: 3 attempts 1.2s apart, `onFailure` once
    at the end, `resolved` guards a second callback (dismissal and display failure share a delegate).
  - `ContinueAdBridge.kt`'s `ContinueAdContent`: same shape folded into the preload; budget 3 per
    offer, re-armed per `requestShow()`; `cancelShow()` still resolves the flow when it runs out; an
    offer arriving to a `FAILING` handler replaces it.
  - Writes that pick the next attempt happen in `LaunchedEffect`s and load callbacks, never in the
    composition body. Retrying can't grant twice (`CoinsAdLimiter`/`GadgetAdLimiter` untouched).
  - **Android deliberately keeps the plain composable** - its placements fill first time.

### Store rewarded ads, level-exit interstitial

- **Coins (`REWARDED_COINS`)**: `CoinsAdLimiter` `MAX_WATCHES_PER_DAY = 5` per UTC-epoch day (keys
  `user_coin_ad_day_bucket`/`user_coin_ad_watch_count`), 500 coins per watch (`coins_ad` pack in `StoreScreen.kt`); AdMob's 10/day
  dashboard cap is a backstop against modified clients. `CoinsRewardAdHost` hosts `RewardedAd`
  directly (tap and ad in the same Compose tree).
- **Mystery gadget (`REWARDED_GADGET`)**: sixth Store card, one of the five real `PowerupType`s via
  `List.random()` at request time, granted with `spendCoins(0)`; `GadgetAdLimiter`
  `MAX_WATCHES_PER_DAY = 3`. **Never `PowerupType.PROTOTYPE`** - an in-progress sixth gadget,
  excluded from the Store and from the in-game HUD tray (F2 still grants it as dead stock).
- **Level-exit interstitial (`INTERSTITIAL_LEVEL_EXIT`)**: on QUIT / RETURN TO MENU / MAIN MENU / ALL
  CLEAR, never NEXT LEVEL (ruled too aggressive). `InterstitialAdLimiter`:
  `totalLevelsCompleted >= 1`, `!isPremium`, in-memory 120s cooldown from launch (retuned from
  2 / 180s / 5 per session on vendor guidance; unmeasured - tune on D1 retention/ARPDAU), 8 per session; no
  dashboard cap. Plain per-platform files: Android's `MainActivity.kt` flips the menu regardless of
  the ad; iOS's `GameLevelExitBridge` is polled by `AppDelegate.swift`'s `startObservingLevelEnd()`,
  gated on `showRequested` with both hazard branches. **Never run on a device on either platform.**

## Native iOS shell (`ios-shell/`) - works on a real Simulator in CI

Hand-authored XcodeGen project embedding `:game`'s `GameMain.framework` and `paywall-build`'s
`PaywallModule.framework` in one process (clean link/codesign, real storage round trip). Separate
from KorGE's generated `build/platforms/ios`; no decision yet on how they converge for shipping.
**Cross-framework triggers live in Swift** - the two produce ABI-incompatible klibs (2.0.20 vs
2.4.10), so Kotlin can't call across; Swift calls both frameworks' exported ObjC APIs.

**Settled - don't re-litigate without a new reason:**
1. No duplicate Kotlin/Native runtime symbols for this pair (re-verify if a third Kotlin/Native
   framework is added).
2. `@ObjCName(name = "X")` without `exact = true` keeps the framework prefix -> undefined symbols.
   Use `@ObjCName(name = "X", exact = true)` + `@OptIn(ExperimentalObjCRefinement::class)` on every
   Swift-visible object.
3. `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` (the frameworks are arm64-only).
4. **`CADisableMinimumFrameDurationOnPhone: true` in Info.plist is mandatory** - Compose's
   `PlistSanityCheck` hard-aborts without it.

- **Info.plist**: landscape-only everywhere (every Compose screen assumes 720 is the SHORT side);
  `UIStatusBarHidden: true` + `UIViewControllerBasedStatusBarAppearance: false`. The black shape at
  the top is the Dynamic Island - not removable.
- **Entry point**: `GameEntry.ios.kt`'s `gameMain()`. `GameLevelStartBridge.startLevel(levelId:)`,
  since iOS never sets `Environment["startLevel"]`.
- **`resources/` bundling (fixed `bc9e494`)**: `GameMain.framework` never embeds `resources/`; a
  `postbuildScripts` rsync copies its contents flat into `ShellApp.app/` (where `resourcesVfs`
  resolves against `NSBundle.mainBundle` on iOS). Missing, gameplay audio/art load silently as `null`.
- **View switching**: swapping `window.rootViewController` between Compose and the warm KorGE
  `ViewController` takes well under 500ms cold, 60-120ms warm; KorGE's render loop genuinely stops
  while out of the window on iOS (NOT on Android - bug #7).
- **CI gameplay screenshot handshake**: `switchToKorGE()` writes `korge_visible.txt`; CI polls for it
  (90s), screenshots, writes `screenshot_taken.txt`; Swift's dwell polls for that (20s cap) before
  ending the level. Blind sleeps never hit the window reliably.
- **Screenshot trap**: `xcrun simctl io screenshot` (and any capture of the landscape-locked app)
  comes out portrait-dimensioned with the content rotated 90 degrees - rotate before judging.

## Shared storage bridge: `paywall-build` <-> `:game`

- iOS: `NSUserDefaults(suiteName = "korge")`, keys prefixed `"org.korge.storage."` (same plist for
  any code in the app sandbox, no App Group needed).
- Android: `SharedPreferences("KorgeNativeStorage", MODE_PRIVATE)`, unprefixed. No `paywall-build`
  Android storage impl (deliberate - `:game` talks to Android storage directly).
- `paywall-build/.../PaywallStorage.kt` implements `getRaw`/`setRaw`/`removeRaw`. Verified by
  `StorageKeyCompatibilityTest` (4/4) and a real on-device round trip in CI.
- Keys: `user_coins`, `user_is_premium`, `user_music_vol`, `user_sfx_vol`, `user_controls_swapped`,
  `user_language`, `user_unlocked_levels`, `user_powerups`, `user_total_levels_completed`,
  `level_result_<levelId>`, `level_results_ids`, plus the ad-limiter keys.

## Compose UI (non-gameplay)

MainMenu, LevelSelect, Store and Settings are Compose screens in `paywall-build`. iOS enters KorGE by
`rootViewController` swap; on Android the Compose menu draws opaquely over an always-visible KorGE
view (bug #7).

- **Settings**: language list (see Release state) - French fully localized across menus, in-game
  overlays, and in-game tutorials (`step_*` in `Localization.kt`), `Localization.kt` pure Kotlin. Volume sliders are
  lifted into `NavigationRoot` as `mutableStateOf` (they had no live effect). Reset Progress has a
  confirmation and resets coins/inventory/unlocks/results, keeping `isPremium`.
- **In-App Review**: automatic StoreKit/Play prompt after completing level 4. iOS's manual RATE US
  opens the App Store review page directly (`SKStoreReviewController` is automatic-only: never shown
  in TestFlight, capped 3/year, no callback) - `APP_STORE_ID = "6815256409"` at the top of
  `InAppReview.ios.kt`. Android's RATE US uses Play in-app review (reported working).
- App icon: a 1254x1254 source in Android/iOS/KorGE icon slots (not verified on device).
- Layers Events SDK removed 2026-09-12 - zero third-party analytics SDKs remain.
- The level exit goes through the real `LevelExitBridge` (a `nav_target` storage flag was written
  but read nowhere).

## Responsive layout

**`src/game/model/ScreenLayout.kt` (shared with `paywall-build`) is the one rule: the virtual canvas
carries the DEVICE's aspect and contains the authored 1040x480** - wider than 2.167 keeps 480 height
and grows width; squarer keeps 1040 width and grows height - **up to a zoom cap**: canvas height
stops at `MAX_CANVAS_HEIGHT` (585, where 1040 exactly fills 16:9) with `MIN_CANVAS_WIDTH` (800) under
it. (Containing 1040x480 at 4:3 gave 1040x780 with the action in the bottom third under dead sky -
the ground has nothing below it, so filling a squarer screen means magnifying.)
`FULL_WIDTH_ASPECT` = exactly 16:9, so no PHONE ever loses field of view:

| aspect | canvas | visible world width |
| --- | --- | --- |
| 2.17 reference and wider | unchanged | 770+ |
| 16:9 (squarest phone) | 1040x585 | 770 |
| 16:10 tablet | 936x585 | 693 |
| 4:3 iPad | 800x600 | 593 |
| 7:6 foldable | 800x687 | 593 |

- **Who sets it**: `DeviceScreen` holds what the host measured; every host publishes and
  `DeviceViewport.apply(...)` re-asserts it BEFORE `changeTo` (a `Scene` copies size once, when
  built). Desktop/Android know their window up front; **iOS doesn't** (`gameMain()` runs before any
  layout) - Swift's `GameScreenMetricsBridge`/`MenuScreenMetricsBridge` (four plain `Double`s)
  publish at launch AND on every switch into gameplay (`GameMain` and `PaywallModule` each compile
  their own `DeviceScreen`).
- `viewportFor` assumes landscape defensively (max/min, not "width"), clamped 0.75..3.0.
- **iOS landscape is enforced by the shell, not the system**: iPadOS 26 can give a landscape-only app
  a portrait window on an upright iPad (rotation lock off), despite the ~ipad Info.plist orientations,
  `UIRequiresFullScreen` (deprecated, TN3192), the delegate's `.landscape` mask and
  `requestGeometryUpdate`. The window's root is `LandscapeHostViewController` for the whole session;
  Compose and KorGE take turns as its child (`host.show`) on a landscape-sized stage, turned a quarter
  whenever the window is taller than wide (towards the last landscape side). Safe-area insets are
  published as the stage sees them (`stageSafeAreaInsets`). KorGE's GLKView is made to follow its
  controller's bounds (it otherwise keeps the size UIScreen had at launch). CI's iPad simulator step
  logs `HOST:` lines and screenshots `ipad_menu.png`/`ipad_gameplay.png`. Not yet seen on a real iPad.
- **Gameplay safe areas**: `GameplayScene`'s `edgeInset`/`bottomInset` (46/38) are floors; a
  reported inset + 12 wins where larger. Android reports
  `displayCutout() | mandatorySystemGestures()` (not the full `systemGestures()`). **The TOP is ignored in landscape**
  (`gameplayTopInset`) - the notch is a side, and Android's top report is a gesture strip that
  pushed the HUD down.
- **Compose (`paywall-build/.../Responsive.kt`)**: `menuMetrics` scale = `min(h/720, w/1280)`
  clamped 0.62..1.45 (1.0 at the reference 1560x720); the main menu floors at `MAIN_MENU_MIN_SCALE`
  0.55. `isShort` (height < 520dp) trims decoration once scaling can't fit - but the top-bar
  wordmark is unconditional in `MenuTopBar`. `safeAreaPadding()` is gated on
  `menuAppliesSafeAreaInsets` (iOS true, Android/JVM false - Android's band showed as a strip on
  opaque panels).
- **The top bar has its own scale, `topBarScaleFor`** - a taper, not a floor (`max(scale, floor)`
  held the bar while the screen shrank): below the back button's 44dp floor it keeps
  `TOP_BAR_SCALE_TAPER` (0.5) of the shrinkage, landing phones at 0.79-0.81 (plain `scale` made the
  bar 52% of the back button's height vs 74% on the reference). It's also what lets the wordmark take
  `barScale` without overlapping the centred title. The Missions chapter
  row keeps `CHAPTER_CARD_MIN_SCALE` 0.75.
- **Tablet/foldable fixes** (pinned by `ResponsiveTest`; reference phone/desktop unchanged):
  - **Splash sizes off the aspect** (`splashScale`, capped 1.6), its pieces centred as one block at
    `canvasH * 0.474`. Written as `canvasH / DESIGN_HEIGHT` it silently broke under the zoom cap -
    **any constant derived from `canvasH` alone is suspect.**
  - **Menu video fills the HEIGHT and overflows left** (`videoBoxFor`): `screenHeight * videoAspect`
    wide via `requiredWidth`/`requiredHeight` + `clipToBounds()`, with an `offsetX` so the overflow
    eats the leading edge first and spills past the trailing edge only after a
    `SUBJECT_TRAILING_EDGE` strip (the silhouette spans 0.78..0.87 of the frame; stays ~4% clear of
    the edge). **Compose trap**: `Box(contentAlignment = TopEnd)` can't align an oversized child (the
    box grows to the child) - put `wrapContentSize(align, unbounded = true)` on the CHILD.
  - **Dossier card** (`dossierCardWidthFor`): `DOSSIER_HEIGHT_FRACTION` 0.37 of height,
    `DOSSIER_MAX_WIDTH_FRACTION` 0.35 (binds on tablets), `DOSSIER_MAX_SCALE` 1.0 (no upscaling).
- Verified on JVM desktop (`-PwindowSize=`) at 4:3/16:9/21:9/7:6 on levels 1, 4, 7 and menus at
  844x390/1024x768; the menus have run on the owner's Android phone. iOS safe areas never observed
  end to end; nothing verified on a tablet.

## Gameplay architecture (`commonMain`)

- `game.model` (pure Kotlin): `Geometry.kt` (raycasting, LOS), `Player.kt` (hitbox, jump/gravity,
  platform snapping, sub-stepped AABB collision, `NoiseLevel`, crouch), `Guard.kt` (waypoint patrol,
  PATROL/INVESTIGATING), `Vision.kt` (FOV polygon + detection), `Camera.kt`, `MovingPlatform.kt`,
  `Conveyor.kt`, `Laser.kt`, `LevelData.kt`, `GameProfile.kt`, `Powerup.kt`, `GameWorld.kt`
  (orchestration), `CameraFollow.kt`, `BoxPhysics.kt`, `BonusObjective.kt`, `RunRecording.kt`,
  `ScreenLayout.kt`, `VentObstacles.kt`.
- `game.scene` (KorGE): `UiComponents.kt`, `PlayerAnimations.kt`/`GuardAnimations.kt` (atlas sprite
  animation), `LightConeView.kt`, `SceneAssets.kt` (process-wide bitmap/font cache),
  `GameplayScene.kt` (HUD, touch controls, overlays, parallax, level rendering), `VentFxAssets.kt`,
  `MenuBackdropScene.kt`.
- Collision is `Rect`-vs-`Rect` everywhere. Pixel-perfect hitboxes were decided against;
  `footWidth` narrowing is the answer to "the box is bigger than what you see".
- `dtSec` is clamped to 0.1s and `Player.update` sub-steps at 1/60 (a 100ms hitch runs six steps).
- **The level clock counts play** (`world.timeTaken`, end cards, star 3): it starts on the attempt's
  first walk or jump (`GameWorld.hasClockStarted`, 2026-10-19 - standing still at the start is not
  timed); `GameWorld.isSuspended`
  makes `update()` a no-op; `GameplayScene` mirrors `isPaused || !GameAppLifecycle.isForeground` onto
  it every frame and `restartLevel()` clears it. `AppLifecycleBridge.kt`'s
  `GameAppLifecycle.isForeground` (default **true** - desktop/JS never report lifecycle) is set from
  `MainActivity.onPause`/`onResume`, `AppDelegate.swift`'s background/foreground callbacks, and
  either side of the continue ad. 4 tests in `GameplayModelTest.kt`.
- **Camera follow (`CameraFollow.kt`)** is a critically damped spring (closed-form, so 60/120Hz
  settle identically): acceleration depends only on position error and its own velocity, so scroll
  rate can never change in one frame. (The old lerp's acceleration stepped with `Player.vx`, whose
  steps are tuned gameplay, not bugs.) `DEFAULT_SMOOTH_TIME` 0.12, measured against the worst
  one-frame scroll-rate jolt (jump landing 10.1->4.1 px/s, crate-face pin 41.7->18.1) at a 19.4px
  walking lag - move it only with a re-measure. Teleports cut (`snapIfFartherThan = canvasW / 8`,
  plus `isFirstCameraFrame` at every deliberate cut). `worldView.y` is pinned to the ground - a jump
  moves the character up the frame. `CameraFollowTest` (8).
- **Checkpoints**: automatic ones (every level without manual checkpoints) are taken only on fixed
  footing (`GameWorld.isOnFixedFooting`): nothing moving under any part of the feet (moving load,
  hook crate, cart, lift), the centre over a fixed platform, and on a `stayOffTheGround` level not
  the floor. A respawn clears `touchedGround`. `testEveryAutomaticCheckpointHoldsTheBodyAfterAContinue`
  (levels 1, 2, 5; 3/4/8/9's autopilots live elsewhere). Every respawn resets a hook crate with
  `reset(atSweepClock = sweepClock)` so the rig keeps its phase against the level clock.
- **`createFromLayout` copies every stateful def** (`.map { it.copy() }` on `layout.levers`/
  `layout.hookCrates`): two worlds built from one layout `val` used to share lever objects, so one
  test's thrown lever broke a later one. Partial/up-to-date runs hide this class of bug - re-verify
  with `--rerun-tasks`. `LevelLayout` holds only immutable defs; everything stateful is rebuilt per
  world (`testEachWorldGetsItsOwnCart`), so sharing defs between levels is safe.
- **Gotcha: `MovingPlatformDef.copy(y = ...)` does NOT move a platform** - `minY`/`maxY`/`initialY`
  (and `minX`/`maxX` for `initialX`) default at construction and a copy keeps the old values.
- **Test-coordinate lesson (learned 4+ times)**: never hardcode corridor x-coordinates in tests that
  need generic open ground - derive from `world.levelData.guardPatrolMinX/MaxX`. "Occluders cleared"
  and "platforms cleared" are different guarantees.
- Levels: 01 Night Arrival (tutorial), 02 Cargo Yard (rain), 03 First Contact, 04 Moving Target
  (conveyor), 05 The Crane Yard (swing), 06 Stolen Manifest (lever crate, pit, crane crossing), 07
  Service Tunnel (vent gauntlet), 08 Relocation (suspended-load yard, push cart), 09 Deja Vu (level 8
  along the loads), 10 Below the Yard (level 7's mechanics recombined), 11 The Prisoner (escort,
  doors, lifts), 12 Final Escape (escort out of the yard - the last level).

## The player

- **Foot planting and surface grounding** (`GameplayScene.computeSupportingSurfaceOffset`):
  `IDLE_FEET_Y` 248.0, `CROUCH_FEET_Y` 250.0, `CROUCHWALK_FEET_Y` 253.0, `JUMP_LAND_FEET_Y` 248.0, `WALK_FEET_Y` 255.76.
  Wood crates (`woodcrate2.png`, push cart crate, hook crates) have their walkable planks ~4.125px below
  collision top (`woodCrateOffset` = 4.125px); tactical crates (`crate.png`, conveyor crates) sit ~1.5px below
  (`tacticalCrateOffset` = 1.5px). The truck (`truck.png`) hood (`truckFront`) and cab (`truckMiddle`) are flush
  sheet metal at 0.0px (`surfaceSink` = 0.0px), while the bed canopy (`truckBack`) has a slight ~0.75px canvas sag
  (`surfaceSink` = 0.75px). Flat surfaces (floors, tables, barrels, conveyors, lifts, catwalks) have visual tops
  flush at 0.0px (`surfaceSink` = 0.0px). Total visual grounding offset is `targetGroundingOffset = surfaceSink + stanceOffset`,
  where `stanceOffset` grounds the higher leg in idle (`idleFeetOffset` ~3.2px), crouch (`crouchFeetOffset` ~2.38px),
  crouch walk (`crouchwalkFeetOffset` ~1.17px, accounting for tilted soles during gait), or landing absorb (`jumpLandFeetOffset`)
  flush against the surface (leaving the lower leg to rest slightly into the surface), eliminating floating while preventing submersion.
  In walking and pushing strides, `stanceOffset` is flush 0.0px since the alternating planted foot contacts directly at `SOURCE_FEET_Y`
  (255.76), preventing shoe and leg submersion across all surfaces. The climb is placed against its box, not by an
  offset - see Climb below.
- **Animation and scene movement smoothing**:
  Player turning responds immediately with clean facing flip and a smooth turnaround plant transition
  (starting at pivot frame 1 over ~140ms into stride) rather than freezing or distorting silhouette proportions.
  Walk and crouchwalk cycle progress smoothly decelerates into a foot plant during the 0.08s stopping window
  before handing off to idle/held crouch rather than freezing mid-stride.
  Camera follow snaps when the clamped target hits level boundaries (0.0 or `minWorldViewX`) to prevent
  spring overshoot deadzones, and background parallax tiles wrap with Euclidean modulo to prevent seams.
  `truckFront.width = 29` (hood-to-windshield step at ~11.2% of `truck.png`). `interactAngle` 60 degrees down-right.
- **Drawn 5% larger than he collides** (`VISUAL_HEIGHT_SCALE = 1.05`) so the push pose's fist
  reaches the cart handle; collision, jumps, climbs and crouch gaps are untouched. Its ceiling is the
  tightest crouch gap left - level 4's descending hanging crate over its 2-stack (68) vs a crouched
  body drawn at 58.8 (`testTheDrawnBodyStillFitsTheTightestCrouchGap`).
- **`CEILING_ART_MARGIN` 3.0** - he's drawn up to 2.6 taller than his box, so ceilings stop the box
  that much lower (not while crouching). A variant that also widened the detection rect and swapped
  `vy < 0` for a captured flag isn't covered by tests - change only the stop position.
- **Vertical collision seam ("flying past the end of terrain")**: a foot span straddling two
  platforms used to resolve to the taller one. Capture `wasFalling = vy > 0.0` before the loop;
  resolve to the candidate keeping the player closest to their current y.
- **No sideways shove off an edge (2026-09-28, `Player.updateStep` horizontal pass)**: a body whose
  foot centre has just passed an edge still overlaps the platform, sunk a fraction into its top; the
  old straddle branch ejected it ~17 units in one step, kept in full by a late (coyote) jump. Now,
  when the top-face overlap is the shallower one: rising fast enough to clear it -> no sideways push;
  falling -> eased clear at no more than walking pace; otherwise (e.g. a conveyor crate riding into
  a slow-rising jump) still pushed fully clear, or the vertical pass reads it as a ceiling and drops
  the body under the crate (what broke `testLevel4SimulationPlayable` in a simpler version). Grounded
  edge jumps unchanged to the hundredth (45.7 level / 34.7 up 24 / 63.3 down 50); late presses lost
  ~14 units. `testWalkingOffAnEdgeHasNoSidewaysJolt`, `testACoyoteJumpOffAnEdgeGetsNoFreeShove`.
- **Jumps**: `maxJumpHeight` (51.2) is the analytic apex; a jump actually clears ~48.5 at 60fps -
  leave margin under 48.5 wherever a jump has to land. A climb fires only on a jump PRESS against a
  face, consumed on the first frame of a held button - pulse it in simulations. **Any same-height
  jump must be tuned against the real physics** (binary search + a timing-slack scan in a test driving
  `world.update` end to end): once a falling body overlaps the target's slab while still short of it,
  collision treats the platform as a WALL. Walking off an edge drops at `Player.dropSpeed` (30
  across).
- **Crouch**: crouch -> jump is gated on the standing-up headroom test; the launch plays the crouch
  clip backwards over 90ms into the jump's launch frame; `crouchSuppressedByJump` drops the crouch
  stance for the whole jump (or a 56-tall hitbox under a 98-unit sprite clips ceilings). A crouch
  survives a fall; the stand-up happens on landing (`crouchedAtTakeoff`/`mustStayCrouched`).
- **Climb**: `ClimbTarget.endsCrouched` ends a climb crouched where there's crouch room but not
  standing room; the headroom check runs at the direction-aware `climbLandingX`. The crouched climb
  stops the clip at raw frame 182 (`CLIMB_CROUCH_END_PHASE`, picked by silhouette overlap) and plays
  its settled footage at 3.0x. **The drawn figure is placed off the box, not the collision body**
  (2026-10-23, "going inside the platform and not touching the top part"): `PlayerClimbContact`
  (generated by `tools/art/prep_climb_contact.py`, whose header is the source of truth) gives each
  climb frame the pixel where the box's top corner belongs; `GameplayScene.placeClimbFigure` puts it on
  the real corner (deck at the crate's drawn top), never with the feet below where they stood, and
  hands back to Player's placement over raw 156..166. Solved per frame (overlap, gap under what rests
  on the deck, fingertips over the lip, distance from Player's corner) with a squared-step smoothness
  path - an absolute step cost never spreads a move and popped the body onto the deck in one frame;
  the hand is tracked frame to frame (from raw ~123 the knee and shoulder reach as far forward and
  sit higher). Result: fingers hooked over the corner and the body against the face on the hang,
  palms on the deck through the push-up, one frame (raw 149) with a toe on the lip
  (`PlayerClimbContactTest`). **Re-run the script after re-cutting the climb clip or changing
  Player's `CLIMB_*` curves.** A ledge under 96 still shows the hands over the top until the pull-up
  (the reach is one body height, the feet stay on the floor). A previous attempt - a 7-unit drop, a
  5-unit push into the box, retimed curves and grip scaled by `min(height, totalRise)` - buried the
  hang 10-16 units in the face; Player's curves are back to their measured values (`startClimb` now
  snaps the body flush on every climb target - the drawn figure is placed off the box either way). **The climb clip is drawn 12-22% smaller than life deliberately** (reverted
  once on request) - it makes the reach exactly one body height so the hand lands flush on a 96
  ledge. Don't "fix" it unasked. Not seen on a screen - only the script's contact sheets.
  **Timing** (2026-10-26, "make the timing for each frame much more realistic by first learning how
  that action actually happens in real life"): `CLIMB_PACING_CURVE` is generated by
  `tools/art/prep_climb_pacing.py --write` (header = source of truth) - real climb-up strokes (catch,
  dead hang, explosive pull + knee drive, slow-but-moving transition over the hands, press, landing
  absorb, crouched balance, stand, settle), each eased minimum-jerk along the figure's on-screen
  motion against the box. Peak ~1.8 m/s on screen, was ~6 m/s at the feet-over. Total stays 1.95s
  and upright (raw 200) still falls at 1.66s, where a held direction walks off - now
  `CLIMB_WALK_OFF_PHASE` (phase, not progress 0.85), so no level timing moved. Run recordings now
  store `climbPhase`, so echoes draw the right frame. Re-run after re-solving `PlayerClimbContact`.
  The finish lands the clock exactly on its end phase (`curveInverse`) - the crouched 3x tail
  otherwise overshot raw 182 by a tick.
- **A single tap forward**: tap grace 0.12s, and a tap released mid lean-in (0.28s) finishes the
  lean-in at 3x - the body moved instantly, the animation was the delay. Holding is unchanged.
- **Eyes look for a braced body where it is drawn**: braced (`Player.braceLean`, set from
  `pushCartSide` once `pushStanceBlend >= 0.5`) the head is ~15 ahead / ~66 up, so `Player.keyPoints`
  switch to `PUSH_HEAD_POINT_*`/`PUSH_TORSO_POINT_*` (pixels opaque in every push frame) - re-measure
  with the push clips. `testABracedBodyIsLookedForWhereItIsDrawn`.
- **Crush is judged on what is drawn**: both underside crush checks use `GameWorld.crushBox()` - the
  centre `Player.footWidth` (~21.6) of the 36-wide box (the squeeze and everything else use
  `player.bounds`). Braced into a cart (`crushParts()`, `pushStanceBlend >= 0.5`) the body is six
  columns with heights from `PUSH_CRUSH_PROFILE` (fractions of `visualHeight`, trailing end first,
  mirrored facing left; tallest alpha per column over the push loop and settled pushtransition -
  re-measure if re-cut). `comesDownOn` judges the columns a load reaches together, so walking into a
  low load from the side isn't a crush. A crusher that has come down into a braced body's box is
  left out of his collision list (`overheadLoads`), or the collision pass shoves him and the cart out
  from under it; he may back out but not walk further in (`overlapX` clamp).
  `testABobThatMissesTheDrawnCartOrBodyIsNotACrush`,
  `testABobBehindABodyBracedIntoACartIsJudgedOnTheLeaningFigure`.

### Animation clips

Shared recipe: cut by a `tools/art/prep_*.py` script whose header is the source of truth (re-run and
paste, don't hand-edit); one crop box per clip symmetric about the body centre, not the union bbox
(so a flip doesn't shift it); feet pinned per frame; premultiplied resample. **Measure each plate's
own standing silhouette** - clips can be framed at different sizes at the same file size. Measure
`*_STRIDE_PER_HEIGHT` by phase-correlating ground-contact alpha on the PROCESSED output.

- **Swing** (`art/frames/player/swing`, levels 5/8/9/12): 52 frames from a 200-frame plate. Hang
  position measured per frame (`SWING_GRIP_ABOVE_CURVE`/`AHEAD_CURVE`) as fractions of the DRAWN
  height (`visualHeight`, since 2026-10-26 - on the box's height the 5% `VISUAL_HEIGHT_SCALE` left the
  fist ~4.6 over the grip, clear of the bell: "when swinging he is not touching the bottom of the
  hook"); the rect sits still under the hook while the silhouette sweeps (correct). Hook grip `HOOK_GRIP_X/Y_FRACTION = (0.481, 0.960)` -
  re-measure if `hook.png` is recropped. `swingDuration` 0.92s over a pacing curve. Entry: walk into
  the hook and press JUMP (from a standstill it's a plain jump). `findSwingTarget` refuses unless solid
  ground is level with the far ledge (either direction); `swingLandAhead`/`swingMinReach`/
  `swingMaxReach` tune it. Grace: the 75..97 reach window takes `SWING_REACH_GRACE` (12) either side,
  and a landing up to `SWING_LAND_GRACE` (14) short of/past a level ledge is pulled onto it
  (`SWING_LAND_FOOTING` 12 in). **The camera caps hook height** - for vertical gain, lower the ledges.
  `testSwingCarriesThePlayerOverLevel5sGapAndLandsThemOnIt`.
- **Wind** (`art/frames/player/wind{transition,walk}`, `prep_wind.py`): `windwalk`'s source frames
  arrived empty and were rebuilt with the shipped plates' rule (`alpha = 255 - luma, rgb = 0`).
  Shares push's scale/framing (transition frame 0 IS the standing pose); frames 192 wide.
  `WIND_STRIDE_PER_HEIGHT` 0.33 is a cadence knob - the feet are MEANT to slide (0.31-0.39 all
  read fine). **Owner's rule**: standing in airflow holds a still braced pose; the walk plays only
  while spam-tapping (`GameWorld.isWindPushing`), not gated on ground speed (which is ~0 exactly when
  straining hardest). **`windOwnsSprite` in `GameplayScene` is load-bearing**: the frame driver was
  overwriting the wind clip with the walk gait in the same frame - **any future stance should claim
  the sprite with its own flag** rather than trust `playerAnimState`. The settled braced pose and
  mid-zone gait were never confirmed on screen.
- **Push** (`art/frames/player/push{,transition}`, `prep_push.py`): framed ~6.4% smaller than other
  clips (own `244.36/484` scale - get it wrong and he changes size entering the stance); frames
  180x256, so the braced pose leans out past the box edge. The loop keeps all 40 raw frames (1.07s
  cycle = 37fps, walk's rate) while the transition is halved - redo that arithmetic if
  `PUSH_MOVE_FACTOR` moves. `PUSH_STRIDE_PER_HEIGHT` 0.59 (sub-pixel phase correlation pooled over
  both feet; integer bbox edges, single-foot fits and the accelerating part of the raw clip all gave
  wrong answers). **The rest pose is push frame 0**, pinned while braced and stopped (the clips are
  separate takes); the transition (raw 10..72) ends on the best IoU match (index 31, 0.803 - re-scan
  if re-cut). Facing is locked for the stance; `isPushStanceHeld`/`pushStanceBlend` (0..1) live in
  `GameWorld`, toggle edge-detected there. Dev stage `pushStanceDemo` (`LevelData.PUSH_STANCE_DEMO`,
  `-PstartLevel=push_stance_demo`) is off the shipped list; two tests pin that nothing shipped sets it
  and that it stays reachable. Verified by a full JVM walkthrough; planted-foot slide within +/-5%.
- **Guard** (`GuardAnimations.kt`, `art/frames/guard/{idle,walk}/`, `prep_guard.py`): own atlas,
  feet-anchored, `scaleX` negated to face left; crop symmetric about the torch-holding body. Idle:
  every third frame, ping-ponged. Walk: distance-driven, `WALK_STRIDE_PER_HEIGHT` 0.53 (the measured
  planted stride; 0.46 slid ~15% and read as gliding), framed ~14% smaller than idle
  (`WALK_PLATE_SILHOUETTE` 530 - move this if he resizes entering a walk). Torch lens
  `TORCH_AHEAD_PER_HEIGHT` 0.29, `TORCH_ABOVE_FEET_PER_HEIGHT` 0.56. Levels 6+ guards
  (`GameWorld.createDefault`'s per-level `guardSpeed`) still have 48-tall hitboxes and draw as
  half-height men pending a 96-tall pass. **The gait phase is the model's** (`Guard.walkPhase`,
  stride `Guard.WALK_STRIDE_PER_HEIGHT`; the scene only draws `walkPhase * WALK_FRAMES`).
  **Stepping onto a lift deck** (2026-10-26, "guards moving looks like they are floating", level
  11's pen guard over lift_2): `walkGuardOverDecks` stands him on what is under his WEIGHT -
  `GuardWalkStance` (generated by `tools/art/prep_guard_stance.py`: median ground-contact column
  per walk frame) - reached at `GUARD_STEP_RATE` 200/s, not on what is under his centre (that left
  the standing boot 14 in the deck / over the floor for a stride either side). Measured before
  changing it: on flat ground the walk is planted (boots within ~1 unit at speeds 35 and 55, soles
  on the bottom row, crate tops flush), so the float was the step. `GuardStepTest`.
- **Eye points**: `tools/art/prep_eyes.py` writes `src/game/scene/PlayerEyePoints.kt` (generated) -
  see level 9's echo.

## Detection

- **Guard vision = the torch beam**: `Guard.eyePosition` (28 ahead / 54 above the feet for a 96-tall
  guard) is the origin of both the drawn beam and the detection rays. `Camera.eyePosition` is the
  LENS TIP, which moves along an arc as the body rotates (`NECK_LENGTH`/`LENS_LENGTH`).
- **A cone ends where sight does (2026-10-26, "vision cones go through objects")**: every eye's
  detection rays AND every drawn cone (guards, cameras, rovers, the echo) use one list,
  `GameWorld.sightBlockers` - `visionOccluders` (level occluders = platforms + boxes + table
  decorations; door panels still hanging; lifts) plus everything that moves (moving loads, conveyor
  crates, hook crates, carts held or not), as they stood at that tick's detection. The cones used to
  be drawn against `visionOccluders` alone and ran on through moving crates the detection already
  stopped at. Poles, hooks and pass-through legs block nothing, on purpose (level 3's pole camera).
  `VisionConeTest`: a guard behind a crate or a parked load is not seen and his cone stops at it;
  on every level, for every guard/camera/rover, a point is inside the drawn cone exactly when the
  detection test passes for it. That sweep found the second leak: `computeVisionPolygon` aimed rays
  only at corners, so where the end of the range crossed a box's side the outline cut a chord and
  left undrawn a sliver the eye sees (level 3's overwatch, a body on the far crate); it now also
  aims rays at every range/side crossing. Drawn after the tick, so a guard that walked this tick
  is drawn one step (under a unit) ahead of where he looked from.
- **`VisionCone`** (`Vision.kt`) holds one drawn cone's polygon and rebuilds it only when the lens,
  facing, range, FOV or a sight blocker within reach changed (a crate moving across the level costs
  nothing); the scene keeps one per cone. Bit-identical to `computeVisionPolygon` on the whole list.
- **`LightConeView`** draws a vision polygon as one triangle fan (per-vertex colour, additive, one
  draw call), replacing KorGE's SYSTEM renderer. Guard beams are culled outside a half-screen
  window. Camera cones: see Performance.
- **The prisoner has no cone** - he is looked for, never looks (`GameWorld.isPrisonerSeen`, a pip
  over him). `VisionConeTest.thePrisonerHasNoCone` builds levels 11 and 12 and finds exactly one
  cone per guard, camera and rover. (Asked 2026-10-26 "remove the prisoner's cone": no code ever
  gave him one - if it is still seen on a device, find out which eye's cone it is.)
- **Owner decisions**: no colour ramp on beams/cones (one steady colour; the pip shows detection).
  Camera rotation stops on detection (`detectionPauseDuration` 2.5s, mirrors
  `Guard.investigateDuration`); checkpoint respawn and Smoke Screen reset the pause. **Camera bots
  hold still while they see you** (`GameWorld.update` skips `bot.update` for bots in last tick's
  `detectingCameraBots`, as a seeing guard isn't walked on; `testACameraBotThatSpotsThePlayerHoldsWhereItIs`).
- **Footsteps carry along a floor and down, not up** (`GameWorld`'s noise check, 2026-10-27): a guard
  does not hear a walker standing more than his own height over his feet ("when i am on top of here,
  if i move the guard can hear me" - level 12's road guard under the raised lift_5). Guards on hung
  crates (level 3's and 12's overwatch, level 6's) still hear the floor under them; a guard on a
  floor still hears it, 180 straight-line with line of sight (`testTheRoadGuardHearsTheRoadButNothingOverHim`).
- **Detection pip**: "!" (no circle) for a heard noise, a filling clock for a sighting (seeing wins).
  Stealth Boots suppresses the noise pip; losing line-of-sight and losing the sound cue are tracked
  separately. Guards play `sfx/guard_investigate.wav` on the rising edge of hearing noise and on the
  rising edge of visual detection (`guardWasInvestigating` and `guardWasDetecting`).
- **Vision-testing lessons**: a point-sampled probe can't tell "occluded" from "out of range" -
  verify against `VisionSystem.computeVisionPolygon`. Check exactly at `minAngle`/`maxAngle`
  (cameras dwell there; a float-drift sweep skips them) plus a fine sweep for a RADIAL JUMP between
  adjacent vertices (a ray grazing a corner) - a big Euclidean gap also happens normally along a
  tall occluder's face.

## Objectives and stars

**Main objectives** (HUD row 1, `objectiveHint` + `Localization.levelObjectiveHint`, EN/FR kept in
step): 1 Find the Shipyard Entrance, 2 Find a Way Through the Yard, 3 Reach the Conveyor Belt, 4
Cross the Conveyor Line, 5 Cross the Crane Yard, 6 Reach the Security Building, 7 Reach the Security
Desk, 8 Track Down Container 17, 9 Follow the Figure Without Touching the Ground, 10 Find the Prisoner
in the Holding Cells, 11 Escort the Prisoner to the Exit, 12 Escort the Prisoner Out of the Shipyard.

**Star 2 = the level's optional objective** (`LevelData.bonusObjective`,
`src/game/model/BonusObjective.kt`) - the HUD's "(OPTIONAL)" row 2 and results row 2.
`BonusObjectiveTracker` is fed once per tick by `GameWorld.observeBonusObjective`; a failure survives
a checkpoint respawn, only `restartLevel` clears it. DO kinds are met when done (crossed at the exit
if not); AVOID kinds cross when it happens (ticked at the exit). `bonusObjective = null` (star 2 =
"no alerts raised", which could only be lost through a continue) is still supported.

| # | enum | wording | rule |
| --- | --- | --- | --- |
| 1 | `BASIC_MOVES` | Jump, Climb and Crouch | all three once; a crouched start is not the crouch |
| 2 | `NO_DROP_FROM_HANGING_CRATES` | Never Drop from a Hanging Crate | landing at floor level (`FLOOR_LEVEL_BAND`, rescue barrels count) when last stood on a hanging crate/moving container; walking off terrain is fine |
| 3, 6, 12 | `STAY_UNSEEN` | Stay Completely Unseen | `alertProgress` ever above 0, or a catch (on 12 the meter is shared, so a glimpse of the prisoner counts) |
| 4 | `USE_A_GADGET` | Deploy a Gadget | any successful `activatePowerup` (Remote Trigger included) - needs stock, nothing grants it |
| 5 | `SWING_FROM_A_HOOK` | Swing from a Crane Hook | `Player.isSwinging` once |
| 7 | `DISABLE_ALL_SECURITY_BOTS` | Shut Down Every Security Bot | every `CameraBot` id switched off at some point this attempt (a respawn switching them back on keeps the credit) |
| 8 | `NEVER_TOUCH_A_HANGING_CRATE` | Never Touch a Hanging Crate | body within `TOUCH_MARGIN` (0.25) of any hanging load, any side. Not 1.0: the ground road passes under bob 1 with ~1 unit to spare |
| 9 | `STAY_OUT_OF_THE_FIGURES_SIGHT` | Stay Out of the Figure's Sight | `isEchoDetecting` ever true |
| 10 | `USE_EACH_SWITCH_ONCE` | Use Each Switch Only Once | any switch thrown a second time (`GameWorld.hasReusedASwitch`; a restart clears it) |
| 11 | `KEEP_THE_PRISONER_OUT_OF_SIGHT` | Keep the Prisoner Out of Sight | `isPrisonerSeen` ever true - even the start of noticing him |

- **Saves are unchanged**: `LevelResult.wasDetected` keeps its name and 5-field format but means
  "star 2 missed" (`GameWorld.getLevelResult`); old saves keep whatever star 2 they had.
- **Proven reachable by a clean autopilot**: 1, 2, 5, 6
  (`LevelWalkthroughTest.testTheOptionalObjectivesOfLevels1To6AreMetByACleanRun`), 7-11 (asserted at
  the end of their own walkthroughs), 4 (`BonusObjectiveTest`). **Level 3 is NOT proven**: the roof
  guard notices the autopilot standing up at the first crate even after a crouched approach; it needs
  `LEVEL_3_HOLDS` re-searched with an opening wait at the crate (`testReplanLevel3` rejects waits
  that raise the alert; the search is slow - the owner stopped one run).

**Star 3 = `timeTaken <= LevelData.timeTargetSeconds`**, shown on the results card only - **the HUD
does NOT list the time target** (owner; `Localization.finishUnder` removed). Target = the autopilot's
clean run x ~1.4, rounded up to 5s (levels 1 and 5, short and mostly running, get ~15s flat slack).
The autopilot never hesitates, so its time is a floor (old targets below it were impossible). Not
timed with a person.

| level | clean run | target | timed by |
| --- | --- | --- | --- |
| 1 | 28.8s | 45 | `LevelWalkthroughTest` |
| 2 | 42.2s | 60 | `LevelWalkthroughTest` |
| 3 | 46.1s (17s of it holding) | 65 | `LevelWalkthroughTest` |
| 4 | 116.9s | 165 | `testLevel4SimulationPlayable` |
| 5 | 19.2s | 35 | `LevelWalkthroughTest` |
| 6 | 65.3s | 95 | `LevelWalkthroughTest` |
| 7 | 107.7s | 155 | `testLevel7SimulationPlayableWalkthrough` |
| 8 | 130.2s | 180 | `testLevel8IsBeatableOnTheGroundRoad` |
| 9 | 106.1s | 180 | `testLevel9IsBeatableWithoutTouchingTheGround` |
| 10 | 151.5s | 190 | `testLevel10SimulationPlayableWalkthrough` |
| 11 | 122.4s | 195 | `Level11EscortTest.testLevel11IsBeatableAsAnEscort` |
| 12 | 157.5s | 230 | `Level12EscapeTest.testLevel12IsBeatableAsAnEscape` |

- `test/LevelWalkthroughTest.kt` holds the walkthroughs for 1, 2, 3, 5, 6 - one general
  `Runner.runRight` policy (crouch under head-only ceilings, jump when blocked = the climb, jump a gap
  at its lip, hold at a lip for a moving crate/lift, swing a hook in reach, throw a lever underfoot)
  plus level 6's own legs - and `assertPace`, pinning each target to `MIN_TARGET_OVER_CLEAN_RUN`
  (1.25) .. `MAX_TARGET_OVER_CLEAN_RUN` (2.0) x the clean run. 4/7/8/9's times are constants there
  (`cleanRunsMeasuredElsewhere`) - update them with the level's own test. **After changing a level's
  route or timing, re-run its walkthrough and re-pick the target** (failures read "not reachable by
  hand" / "would not ask for a clean run").
- The autopilot's crouch rule must skip `floatingClimbTargets`, or it ducks under level 3's table.

## HUD, overlays, gadgets

- **Objectives panel** (`objPanel`): Bebas Neue (Inter was rejected), at `OBJECTIVES_CORNER_X/Y` (8, 6) with no
  left safe-area inset ("move it very close to corner" - it may sit over a camera cutout).
- **Gadget tray**: idle slot icon `resources/gadget_bolt.png` drawn 16x22 (deliberately wider than
  the source), recoloured via `colorMul`; `drawPowerupIcon` is the fallback. Icon centred both axes,
  count text white; a running gadget shows a white curtain draining top-to-bottom (alpha `0.14`,
  lowered twice from 0.42 - lower the same constant if asked again). A running gadget can't be
  re-triggered (guarded in the model and before the inventory is spent).
- **Per-corner `roundRect` needs manual clamping** - korlibs' per-corner overload does none (unlike
  the single-radius one), so a shrinking box's corners bulge out. Clamp the bottom radius to the
  remaining height and keep the overlay inset inside the frame's stroke (`drainInset` 1.6). Applies
  to every future per-corner call.
- The D-pad/jump/crouch/interact tap click was **removed outright - don't re-add**.
- **No full-canvas red wash on a lethal hit, on any level** - it hid the frame showing what killed
  you (one subscription removed in `GameplayScene`; the model hooks remain and are tested).
- **`TutorialStep.autoDismissSeconds`** (opt-in; 0.0 = wait, the default): level 7's deactivate-bot
  prompt 5.0, level 9's `step_stay_off_the_ground` 6.0 - a test (`testTheDronePromptLetsGoOnItsOwn`)
  lists the allowed exceptions. A prompt's lifetime is checked with a `println` on
  activate/dismiss, not screenshots (the world runs behind the loading fade).
- **End-of-run dossier sheets**: MISSION FAILED / HEIST COMPLETE are the main menu's briefing sheet -
  `dossier_paper.png` stretched to a card, ink debrief, rubber-stamp verdict, torn-paper strips at
  left. FAILED = SITUATION REPORT (two columns, red stamp); COMPLETE = OBJECTIVE REVIEW (three stars,
  green stamp, purse). **The earlier dark `#141416` card with hairline borders/coin pill was rejected
  as off-theme - don't reintroduce.** Ink follows `MainMenuScreen.MissionDossierCard`: sheet 1.5
  aspect (never stretch one axis), -5.2 degree tilt; fields are stacked (a tilted sheet can't carry
  label-left/value-right rows). `totalLoadSteps` (20) must equal the number of `markLoadProgress()`
  calls. Verified on JVM desktop.
- **REMOTE_TRIGGER** throws the nearest un-thrown lever exactly as an in-range interact would (it
  was a complete no-op until 2026-09-20); it refuses, without spending, when no lever exists.
- **Debug keys, JVM only (`Platform.isJvm`, so never on Android/iOS even with a keyboard)**: F1 =
  noclip flight (free 2D movement, no collision/gravity, all hazards/timers frozen); F2 = +3 of every
  gadget via `grantDebugPowerups(3)`, never persisted (JVM's `PlatformStorage` is an in-memory map).

## Audio

Two systems sharing no code: **gameplay** (`GameAudio.kt`, KorGE, `resources/sfx/`, a missing clip is
a silent no-op) and **menus** (`ui/MenuSfx.kt`, `expect`/`actual`: `AVAudioPlayer` pool iOS,
`SoundPool` Android, JavaFX `AudioClip` desktop, all overlap-capable; `USAGE_GAME`, since
`USAGE_ASSISTANCE_SONIFICATION` silently mutes on many phones). **Format: PCM s16le / 44.1kHz / mono
WAV only** (iOS/JavaFX can't decode Ogg). Shared clips are checked in twice (`resources/sfx/`,
`ios-shell/Resources/`); Android reads `assets/sfx/`. Credits in `SOUND_CREDITS` (`SettingsScreen.kt`),
kept in sync with `ATTRIBUTION.md` by hand.

- **Gameplay sfx are decoded once per process** (`GameAudio.clipCache`). The music (`readMusic`,
  streamed) is built per scene - don't cache it; a stopped stream isn't guaranteed to restart.
  `GameAudio.silentForTesting` keeps scene tests off the Windows waveOut backend (it crashed the JVM
  natively, 0xC0000005, with scenes torn down every few frames).
- **Ambience beds (`AmbientLoops`)**: one korlibs `playForever` channel per bed - rain (`hasRain`
  levels), vent fans, steam jets, laser hum, rovers, camera heads, push carts - seamless
  equal-power-crossfaded loops `resources/sfx/loop_*.wav` (22.05kHz mono, cut from long Pixabay
  sources by waveform/spectrogram; `robot_move.mp3` holds several takes - rover 16.0-25.5s, camera
  0.8-7.2s), only ever volume-ramped (`AMBIENT_RAMP_PER_SEC`). Each source has a `*_HEARING_RANGE`
  (230-300; crate thud 480), straight-line distance, squared falloff, so only on/just off screen is
  heard. **Lasers are one shared voice**: the nearest lit beam sets the level, each further lit beam
  in earshot adds `LASER_LOOP_EXTRA_PER_BEAM`, capped at `LASER_LOOP_MAX_GAIN`. Rovers/cameras/carts
  loop only while they moved that frame. Android's native mixer only loops bgmusic, so these use
  korlibs' own channel there - watch for the per-session crackle. Never heard on any platform.
- **Lifts, switches, levers** (Pixabay, `tools/sfx/scripts/cut_mechanisms.py`): `loop_elevator.wav` is an ambience bed
  (nearest *travelling* lift, `ELEVATOR_HEARING_RANGE` 300), `lever_throw.wav` plays on a lever's
  `isActivated` rising edge (hand or REMOTE_TRIGGER), `switch_press.wav` on `onSwitchThrown` (replaced the
  generic UI click). The logic is `MechanismSounds` in `GameAudio.kt`, not inline in `sceneMain` (64KB limit).
  The lift bed is cut from the two mid-range stretches (0.9-3.1 s, 4.4-7.1 s), not the 10-25 s rumble (all below 300Hz, inaudible on a phone); one-shots are tanh-soft-clipped for body. Gains (loudest 200ms RMS + gain) put the lift near -14.6 dB, lever -19.5, switch -25 - the lift was too quiet and the switch too loud at the earlier -19 targets; fan/steam sit at -22, climb -16.
  Not verified on a device: levels, balance against the music, Android `GameSfxOutput` playback.
- **Chains, NPC footsteps, stingers** (same script, also `MechanismSounds`): the hanging loads' chains are
  all cut from the one Pixabay heavy-chains take (5.1 s: build-up, loud drag, calmer drag, settle) at its
  own pitch, since 2026-10-27 ("the moving crate sounds are very unnatural" - it was one 22 kHz loop of 11
  pitch-shifted, heavily levelled phrases, played at a volume following the speed): two 32 kHz beds
  reshuffled from the take's own drag (grains cut at its quiet instants, either mic of the wide stereo
  take, nothing reused within 2.5 s, levelled only halfway - measured: envelope spread 2.0 dB against the
  take's 2.6, the old loop's 1.1; every octave within 1.7 dB of the take) - `loop_chain_light.wav` at a
  crawl, `loop_chain.wav` (heavy) from 40 u/s, full at 120 (`CHAIN_*`) - and one-shots per load at its
  own distance (`syncChains`): `chain_clank_1..3` as it starts off from a quarter-second at rest,
  `chain_clink_1..3` each time it turns round (every end of a swinging crate's run), `chain_settle` as it
  stops (scaled by how fast it went). Loads: chained `movingPlatforms`, hanging conveyor crates (by
  position change) and a travelling hook rig (`hookVx` - a load only swinging under its hook stays quiet).
  `ChainSoundsTest` checks the events, not the sound. Not heard on a device: the gains are set from the
  files' measured levels beside the other beds, by ear never. Guards and the level-11/12 prisoner play the
  player's `step_a/b` on `STEP_PHASES` of their own distance-driven gait (`NPC_STEP_GAIN` 1.0 x distance falloff, range 420; raised from 0.4/320 as too quiet, as was the rover at 0.4/260 -> 0.75/340);
  the prisoner's phases are exact (player frames), the guards' are borrowed, not measured. `mission_fail.wav` plays when the
  Mission Failed overlay shows (not on a checkpoint respawn), `mission_success.wav` with the win card,
  `powerup_activate.wav` on a successful `tryActivatePowerup` at `POWERUP_ACTIVATE_GAIN` 0.1 (owner: "reduce by a lot").
  `door_slide.wav` (jail-door source, 0.15-1.20 s - the clang/thud at 1.25 s is deliberately left out) plays on a
  `Door.isOpen` edge (open and close alike) in `MechanismSounds` for `DoorStyle.BARS` cell gates (`DOOR_HEARING_RANGE` 480, gain 0.55). The steel shutters use
  `shutter_open.wav` / `shutter_close.wav` (cabinet-door source 0.08-1.00 s and 2.90-3.55 s: the close is the slide only - the bang
  at 3.90 s was removed on request) on the `isOpen` edges; a reset/restore snap (openness already at its end) is skipped.
  None heard on a device.
- **Conveyor belt** (`loop_conveyor.wav`, freemaster2 CC BY 4.0, 1.0-7.0 s loop): an `AmbientLoops` bed in `MechanismSounds`,
  on while `world.conveyorsActive` and the player is within `CONVEYOR_HEARING_RANGE` 320 of the nearest point of any belt
  (level 4's belt, and the belt level 3 ends on). Gain 0.45 (~-22 dB with the fan/steam beds). Not heard on a device.
- **Any new ambience bed needs energy above ~300Hz**: `loop_laser.wav` had 99% of its energy at
  20-100Hz (phone speakers played only distortion) and was re-voiced two octaves up, saturated and
  circularly band-passed (`tools/sfx/scripts/revoice_laser.py`, same RMS, gains unchanged).
- `sfx/crate_drop.wav` plays on any hanging crate's hard landing (`HookCrate.body` vy collapse, or
  `isLanded`), every level. `thunder.wav` is Pixabay's clip. `climb.wav` (also the swing launch) is
  trimmed to 0.66s with a 40ms fade (it had a delayed unrelated burst heard as a crackle).

**Android gameplay crackle - RESOLVED 2026-09-11 (Galaxy S25 Ultra).** korlibs' `Sound.play()` builds
a new `AudioTrack` per call; every footstep opened a session next to `bgmusic.mp3`'s, and the phone's
"Voice Booster" DSP re-initialized on each (proven by matching a recording's audio spikes to state
transitions). **Tried first and insufficient - don't re-cover**: `SoundPool` (still requests the fast
path per play), a `MODE_STATIC` pool, keep-warm pings/looping silence (bled bgmusic into the menu),
matching korlibs' `AudioAttributes`/session id. **Fix: `GameSfxOutput`
(`android-shell/.../GameSfxOutput.kt`)** - one `MODE_STREAM` `AudioTrack` per process, fed by one
urgent-priority thread mixing voices into 20ms buffers; `bgmusic.mp3` decoded once via
`MediaExtractor`/`MediaCodec`; `pause()`/`play()` on `MainActivity.onPause`/`onResume` (or audio
continues after leaving the app). Other platforms return `null` and fall back to korlibs' path.
Confirmed on the S25 Ultra; other OEMs unknown (looks Samsung-specific).

## Real device bugs (engine/platform gotchas)

1. **KorGE `Canvas` icons don't scale with density** - scale each by `size.minDimension / REFERENCE_PX`.
2. **A `verticalScroll` parent gives `weight()` nothing** - use `Modifier.height(IntrinsicSize.Min)`.
3. Menu SFX: `USAGE_ASSISTANCE_SONIFICATION` silently mutes on many phones (see Audio).
4. **KorGE's `.play()` builds a new `AudioTrack` per call** - every clip is primed once at volume 0.0,
   ONCE per process (re-priming per reload accumulated `AudioTrack`s - a grey-screen cause).
   Superseded on Android by `GameSfxOutput`.
5. **`PlayerAnimations.load()` reallocated a 2048x2048 atlas and re-decoded the spritesheet every scene
   load** -> real `OutOfMemoryError`. Now a `@Volatile` process-wide singleton; per-scene bitmaps go
   through `SceneAssets`.
6. **AdMob's `onRewardEarned` fires before the ad Activity is dismissed** - reloading on reward raced
   the lifecycle (the other grey-screen cause). Finish only on `onDismissed`/`onFailure`.
7. **Toggling `KorgeAndroidView` visibility tears down its `GLSurfaceView` for good** (permanent grey
   screen). Never hide it; the Compose menu draws opaquely on top, so KorGE renders under every menu.
8. **Negative `scaleX` on a detailed KorGE `Image` corrupts rendering** at large downscale on this GL
   backend. `truck.png`/`entrance.png` are pre-mirrored on disk; no runtime flip of detailed art
   anywhere - pre-mirror future assets too.
9. Vertical collision seam - see The player.
10. **`View.size(w, h)` is MULTIPLICATIVE** (`unscaledSize`'s setter does
    `scaleXY *= value / currentSize`) - once at creation is fine, every frame is a decay to nothing (level 7's steam hit
    ~1e-72 scale within a second). `SolidRect` overrides it with a plain field and is safe; `Image` is
    not. In a frame loop write `img.scaleX = w / sourceWidth`.
11. **`Bitmap32(w, h)` is flagged PREMULTIPLIED** - straight alpha written into one makes feathered
    edges as bright as the core. Write `RGBA(r*a/255, g*a/255, b*a/255, a)`
    (`VentFxAssets.premul`). korim's PNG decoder already premultiplies; only hand-built bitmaps.

**Lesson**: the grey screen had two unrelated causes (#5, #6) after three wrong audio theories;
on-screen exception diagnostics in `sceneMain()` broke the loop - reach for that first on any "blank
screen, no error" report.

## Performance and memory

Mostly reasoned and measured on JVM/decompiled classes; on-device improvement unmeasured.

- **Frame rate**: nothing in KorGE caps it (`Views.forceRenderEveryFrame = false` does NOT - it
  becomes a ~1000Hz update loop that backgrounding doesn't stop). **Gameplay is capped at 60 on
  Android** (2026-10-07, owner's call): `MainActivity.applyGameplayFrameRate`, from a
  `LaunchedEffect(gameplayVisible)`, sets the window's `preferredDisplayModeId` to the ~60Hz mode at
  the current resolution during gameplay and 0 (system choice) for the menu - it slows the DISPLAY;
  a request the system may override, skipped on devices with no 60Hz mode. Device physics now steps
  at exactly 1/60 like the simulation tests. iOS already runs at 60 (KorGE's GLKViewController
  `preferredFramesPerSecond = 60`). To check on the S25 Ultra: Developer options > Show refresh rate
  should read 60 in a level and 120 in the menu.
- **No level behind the menu on Android/iOS** (2026-10-07): QUIT / RETURN TO MENU / MAIN MENU / ALL
  CLEAR go through `GameplayScene.changeToMenuBackdrop`, which loads an empty `MenuBackdropScene`
  where `MenuBackdropScene.hostShowsMenu` (`Platform.isAndroid || Platform.isIos` - `Os.ANDROID`
  confirmed from korlibs-platform-android's bytecode). Starting any level always builds a fresh
  `GameplayScene`. The old dormant
  `GameplayScene` rebuilt the whole level at every QUIT and drew it at panel refresh under the menu.
  The backdrop discards a continue-ad grant that lands after leaving (left in the bridge it would
  revive the player in the next level). Desktop/JS keep `GameplayScene(startDormant = true)` (there
  it IS the screen; `JvmLevelExitBridge` is a no-op; `startDormant` also stops a reloaded scene
  restarting bgmusic). If the Compose menu ever shows see-through, the backdrop's clear colour
  (#16161d) is what's behind it.
- **Camera cones are `FlatConeView` (2026-10-19)**: a GPU triangle fan, same flat white 0.32 fill.
  Before that they were `worldView.graphics()` (`GraphicsRenderer.SYSTEM`, no auto-scaling), which
  re-rasterised the cone into a 1-px-per-world-unit bitmap and re-uploaded it on every frame of a
  sweep.
  - That bitmap, stretched ~4x on a phone, gave a soft edge. A 1-unit band fading to alpha 0 now
    reproduces it (`FEATHER`).
  - Detail finer than the band (notch mouths under 2 units, hits under half a unit apart) is
    smoothed away first. Without that, the band folded over and drew up to 0.63 alpha in level 3's
    crate gaps.
  - Shadow sides run along rays, so pulling the outline in along the rays can't centre the band
    there. Those edges sit ~half a unit further out than before.
  - `FlatConeViewTest` checks every camera of every level over its sweep against an emulation of
    the old bitmap: exact fill away from the edge, never brighter than the fill, mean edge
    difference 0.022.
  - Moving cameras to `LightConeView` was deliberately NOT done (lit falloff, a different look).
  - Every cone - camera, rover (`CameraBotVisual`), guard beam - is cached in a `VisionCone`
    (see Detection), so a dwelling camera keeps its shape while a crate it can't reach moves.
- **Raycasting doesn't allocate** (2026-10-01): `GeometryUtils.castRay`/`hasLineOfSight` inline the
  `Segment2d.intersects` arithmetic in the same order, `computeVisionPolygon` drops occluders out of
  reach once per cone (`occludersInReach`), `hasLineOfSight` skips occluders clear of the segment's
  box. **Bit-identical to the old code** (checked on 240k rays, 240k sightlines, 6.4k polygons across
  every level's eyes and full camera sweeps) - keep it so; level 3's cameras are tuned to exact
  angles. (Polygons have since gained the range/side-crossing rays - see Detection - which only
  add outline vertices; every ray's hit is unchanged.)
- **Per-frame allocation**: profile copies, platform/box/occluder lists and HUD shapes/labels cache
  and recompute on change. `GameWorld.update`'s per-tick lists live in a per-world `TickScratch`,
  same contents in the same order (collision is order-sensitive; verified by every test's stdout
  being byte-identical). **Anything handed one of these lists must not keep it past the call** -
  copy out (`detectingGuards` does). `GameWorld.allGuards` is built once (the guards never change),
  rope dissolve frames are sliced once (`ropeDissolveSlices`), the level 9 echo's cone is rebuilt
  only when its lens, facing or occluders change (like the guards'), and the scene's `cachedProfile`
  is re-read only after the scene itself mutates the profile (`refreshProfile()` after every
  `profileStorage` mutation - the scene's storage instance is its own, so nothing else changes it).
- The tutorial scrim is a `SolidRect`, and the tutorial highlight Graphics is cleared only when
  something was drawn (`clearTutorialHighlight`).
- **Off-screen culling**: static decor registers its world span via `cullable(...)`, toggled
  `visible` off camera with a half-screen margin. **Never register moving entities** in the static
  `cullTargets` (spawn bounds go stale) - dynamic crates, guards, cameras and moving platforms are
  culled per frame against the camera (plus `DYNAMIC_CULL_PAD` for what they draw past their box).
  Hidden views keep running their updaters, so animations keep their phase.
- **Atlas pages stay resident** (`TextureResidency`, added once per `GameplayScene`): KorGE's
  `AgBitmapTextureManager` deletes a texture after 60 undrawn frames, so a player clip on its own
  2048x2048 page (push, climb, swing, wind, jump) was re-uploaded in the frame it restarted - a hitch
  read as "the camera jumps". It uploads every player/guard page at level start and references them
  each frame; `maxCachedMemory` is 384MB so props scrolled off aren't re-sent.
- **The player atlas is the biggest memory consumer** (16.8MB per 2048x2048 page), ~28.5M px now.
  Adding frames is not free - see the ATLAS BUDGET comment on `loadCore()`.
- **Atlases are packed ahead of time (2026-10-19).** The frames live in `art/frames/{player,guard}/`,
  which is tracked but not bundled. `tools/art/pack_sprite_atlases.py` packs them into
  `resources/atlas/<name>_<page>.png` plus `<name>.txt`, and `PackedAtlas` loads those.
  - Packing used to happen at run time in a `MutableAtlas`, from ~840 file reads. On desktop that
    took player 2.3s -> 0.57s and guards 0.33s -> 0.06s. The bundle went from 10.3MB to 6.6MB,
    because frames no game clip uses are no longer shipped.
  - **Same pixels**: every frame keeps MutableAtlas's 2-px border of its own edge pixels (KorGE
    extrudes, and most frames touch their edges). `SpriteAtlasTest` compares every frame and its
    border, premultiplied value for value, against a runtime-built atlas.
  - **After any `prep_push`/`prep_wind`/`prep_guard` run, or any change to a clip's frame range,
    re-run the packer.** Its `ATLASES` table mirrors the `*_FRAMES` constants, and
    `PackedAtlas.frames` throws on a count mismatch.
- **Player clips are per level (2026-10-07).** All eleven clips in one atlas was 9 pages (~151MB heap +
  the same uploaded). Now a CORE atlas (idle, walk, jump, crouch, crouchwalk, climb: 6 pages, loaded
  once) plus a one-page atlas per `PlayerClipGroup` (SWING, PUSH, WIND), loaded only when
  `PlayerAnimations.groupsFor(levelData, world)` says the level can play it, let go when the next
  can't (pages freed with the level art: `releaseOtherLevelsArt(also = takeReleasedPages())`).
  Pages: levels 1-4/11 carry 6, 5/6/7/10 carry 7, 8/12 carry 8 (12 since its dock cart, 2026-10-26),
  9 carries 9 - never more than before
  (one atlas per group incl. base was measured too: a page MORE on level 9). Groups come off the
  model's own gates: swing = `world.swingHooks`, push = `pushCarts`/`pushStanceDemo`, wind = `fans`,
  all on a `replaysRunOf` level (the echo can show any frame).
  - **An unloaded clip keeps its frame count**, filled with a core frame (swing: jump apex; push/wind:
    idle 0): `playerFrames` stays 640 long and every index stays put (`PlayerEyePoints`, level 9's
    recorded frames). A move wrongly thought impossible shows a wrong pose, never an invisible player.
  - **`PlayerClipGroupsTest`**: frame indices for all 8 group combinations; every move the
    walkthroughs (levels 1-12) and 2 min x 3 seeds of random input per level make, seen through
    `GameWorld.moveObserver` (test hook, null in the game), against the level's groups. Seen: swing
    5/6/9/12, push 8/12/demo, wind 7/10 (level 12: swing only from its 2026-10-23 rebuild, swing and
    push - its dock cart - since 2026-10-26, 8 pages).
    Mutation (before the 2026-10-22 rebuild): dropping WIND from `groupsFor` failed 7/10/12.
    **A new clip, or a new way to start an existing move, must update `groupsFor` and this test.**
    Not seen on a device.
- **`SceneAssets.kt` caches bitmaps/fonts process-wide** (only successful loads).

### Level-scoped art (2026-10-07)

Every level used to decode every prop (~66 bitmaps, ~230MB decoded) and keep them all, in
`SceneAssets` and KorGE's texture manager, for the session.
- **Loading**: `loadGameplayBitmaps` loads large props through `SceneAssets.levelArt(name, needed)`,
  each condition taken from its draw site (hanging crates by variant, wood crate, pole, crane, table,
  camera, hook, truck, exit booth / exit structure, level 4's end machine, fan, lever, rope +
  dissolve frames, start fences, lift). Small assets, touch controls and end-of-run screens load for
  every level. The exit booth's rule is one function (`drawsExitBooth`) shared by loader and scene,
  and so are the fences' (`isFence1Box` / `isFence2Box`). The booth and fence sizes live in the model
  (`LevelLayout.EXIT_BOOTH_*` / `EXIT_FENCE_*`). **An invisible barrier stands at the booth's front**
  (`GameWorld`, wherever `LevelLayout.endsAtTheExitBooth`; `EXIT_BARRIER_INSET` 20 into the exit, so
  whoever it stops is in it), since 2026-10-27 - "dont let the player go beyond the ending part": in
  level 12 the player reaches the exit before the prisoner and used to walk on through the booth and
  fence art to the world's end. First made a wall at the fence, it was drawn as a black pole (the scene
  draws every platform) - "remove that pole you added, just add a invisible barrier just about at the
  start of that guard room thing": the scene now skips every platform 1000+ tall (only the side walls
  and this barrier are), and like the side walls it blocks no sight. Elsewhere the run ends the moment
  the exit is reached.
- **Releasing**: `SceneAssets.beginLevel()` at the top of `sceneMain` records what this level asks
  for; `releaseArtOfOtherLevels()` at the end of setup (after `sceneAfterInit`, ~2s cap) drops the
  rest - 18-66MB per level on JVM. It happens behind the level's first frame, not during play.
- **KorGE trap (`SceneAssets.releaseOtherLevelsArt`)**: the texture manager holds every uploaded
  bitmap while under `maxCachedMemory`, and `removeBitmap` doesn't stick (the next `gc()` re-adds it
  from `referencedBitmaps`). The release marks every other uploaded texture used (after
  `afterRender()` clears the two-entry fast path) and runs `gc()` twice with `maxCachedMemory = -1`,
  then restores the budget. Kept textures are never re-sent. Written against KorGE 6.0.0's internals
  - re-check before any upgrade.
- **`GameplaySceneArtTest` - run it after adding a prop or a level**: builds all 13 levels (12 +
  `push_stance_demo`) with `SceneAssets.loadAllLevelArt = true` and normally, and requires identical
  view trees (class + named asset); a released bitmap must be gone from the texture manager.
  Mutation-checked (dropping the crane, exit-booth or ceiling-crate condition fails it). Its second
  test pins kept art keeping the SAME texture and released art being in none of the manager's sets
  after 300 frames (a plain `removeBitmap` fails it). It uses its own `Harness`: KorGE's
  `ViewsForTesting` has no coroutine dispatcher, so **`GameplaySceneTest`'s scene tests never get
  past the loading screen** (they only assert the scene exists).

### Preload behind the menu (2026-10-19)

- **`GamePreload.start()`** at app launch (`MainActivity.onCreate`, `ShellAppDelegate`; not
  desktop) decodes on `Dispatchers.Default`, in the loading screen's order: loading-screen art,
  fonts, the player's core atlas, the guards' atlas, then every level's shared art
  (`GamePreload.BITMAPS`). Background thread because KorGE's loop is stopped behind the iOS menu.
  Android needs `AndroidCoroutineContext(applicationContext)` in that coroutine - `resourcesVfs`
  reads its Context from there.
- **Hand-off**: `SceneAssets.preloadBitmap/preloadFont`, `PlayerAnimations.preloadCore` and
  `GuardAnimations.preload` reserve a `CompletableDeferred` synchronously. A scene load that gets
  there first waits on it. A second caller waits on the same one, which matters on iOS, where the
  dormant level 1 from `gameMain()` may also be loading. A failed preload is loaded again by the
  scene, as before.
- **`BITMAPS` must be art every level asks for, with the scene's `minified` flag.** A level
  releases whatever it doesn't ask for. `GameplaySceneArtTest` checks both for every level.
  `GamePreloadTest` checks that every preloaded asset is decoded once only.
- **Loading-bar frames**: `markLoadProgress` waits a frame only once 100ms has passed since the
  last repaint (`LOADING_REPAINT_MS`, on `views.timeProvider`). Before, it waited a frame on every
  one of 43 steps, ~0.7s per load at 60fps.
- Cost: the core atlas (~100MB of heap) is held from launch rather than from the first level.
  Desktop timings: 3.2s of preload before the atlases were packed ahead of time, ~1.2s after.
  Not timed on a device.

## Art pipeline

- **POT + mipmaps pass (2026-09-10)**: `mipmaps(true)` silently no-ops on non-POT art. Ten assets
  re-encoded: 50.5MB -> 9.8MB of texture memory (each diffed in its device-pixel draw box, mean error
  < 0.6/255); originals in git at `77a65b9`. Dead assets removed (`resources/` 75MB -> ~39MB; also
  `camera.png`, `cameranew.png` and `woodencratenew.png`, superseded by `cameranew2.png` and
  `woodcrate2.png`). Lossless PNG recompression (Pillow,
  `optimize=True, compress_level=9`) is a download-size win only.
- **DO NOT SHRINK** (already at/below device resolution at 1440p): `bgmg2-6.png`,
  `loadingbg.png`/`logo_main.png`, `success3.png`/`failedscreen.png` (the result cards - drawn larger
  than their pixels on a phone), `button1-4.png` (pause menu), `crane.png` (its cab is drawn LARGER
  than its pixels), `chainedcrate*.png` (stretched up to 2240x600 world units). `dossier_paper.png`
  is not loaded by the game at all (nothing in the scene drew it; the menu has its own copy).
- **Still hardcoded to file dimensions** (pin as literals/fractions first, or don't resample):
  `chainedcrate.png`/`chainedcrate2.png` slices. `hook.png`'s aspect is a literal (2136/154) in
  LevelData and Player's grip is a fraction of the rect, so resampling it is safe; RE-CROPPING isn't.
- **Shrink pass (2026-10-07/08), `tools/art/prep_level_props.py`** - its header is the source of
  truth (sizes, draw boxes, the verify). Every file there was 2-14x its largest draw box:
  - cut out of a plate AT the pixel crop the scene used, drawn whole into the same box, every
    position still in the plate's pixels: `woodcrate2.png` 512x256, `pole.png` 128x2048,
    `cameranew2.png` -> `camera_mount.png` 64x64 + `camera_lens.png` 128x64, `stars.png` ->
    `star1-3.png` 512x512 (aspect from GameplayScene's `STAR_CROPS`);
  - resampled whole: `victorybutton1-3`/`failedbutton1-3.png` 1024x256, `hook.png` 128x1024,
    `lever_bottom.png` 128x64, `lever_top.png` 16x128, `newrope.png` + `rope_dissolve_0..8.png`
    64x512.
  ~77MB decoded -> ~13MB (this round ~58 -> ~11). Full-res originals in `art-source/props/`
  (gitignored; also in git history).
  `rope.png` is only newrope's fallback and never loads.
- **Sized-to-box art loads with `minified = false` - NO mipmaps (measured 2026-10-08).** Under 2x
  minification bilinear is already clean, and KorGE's trilinear (`LINEAR_MIPMAP_LINEAR`, level picked
  off the MOST-minified axis, 2x2-box mips) came out 30-40% softer than the full-size art on the
  buttons, crate, pole and stars. The tool's verify emulates the GPU (one bilinear tap per pixel,
  clamp, premultiplied) against an ideal LANCZOS at a 1440p phone, a 2732-px iPad Pro, 1080p and 720p:
  every new file is closer to the ideal than the old oversized one was (old non-POT files never got
  mips and alias). A plain LANCZOS-vs-LANCZOS diff - the 2026-10-07 check - can't see this; it is how
  the first cut of this pass shipped those four props mipmapped. **Measured 2026-10-19** (same emulation
  plus trilinear, vs a LANCZOS of the pre-pass originals at `77a65b9`): the 2026-09-10 POT pass's
  ten assets, still mipmapped. Touch buttons (`left/right/jump/crouch/interact.png`, 512 drawn at
  288-324px): mipmapped ~70% of the ideal's sharpness on every device; without mips 92-108%, error
  ~40% lower at 1440p/iPad, even at 1080p, ~25% higher at 720p. **`fence.png`/`fence2.png` are drawn
  LARGER than their 512px at 1440p (612x583, 697x583) and on iPads** - upscaled, ~60% / ~45-50% sharp,
  mips irrelevant there; a 1024 re-export without mips is near the ideal on 1440p/iPad/1080p but
  shimmers at 720p (3.3x minification). `barrel/crate/truck.png` are near-black silhouettes: every
  option is within ~1/255 - leave them. `container17.png` (2020x565, non-POT so never mipmapped) is
  92-95% sharp, <0.8/255 off - fine.
- **DECIDED NO: never atlas the static world art.** The POT stretch-to-box textures are under half an
  atlas page; atlasing more than doubles their memory for a sub-noise bind-count win and bleeds
  mipmaps across slices. Atlasing pays for hundreds of small sprites, not a dozen large props.

**Adding new art - a standing rule:**
1. Find the size it's **drawn** at in virtual units, **multiply by 3** (phones render the canvas at
   up to 3x), round to a power of two (up, unless within a couple percent of the lower).
2. **Resample, never pad** (padding stretches into the draw box):
   `python tools/art/pot_resize.py resources/newthing.png 512 512` (premultiplied LANCZOS; `--check`
   reports sizes). Verify the way the GPU samples it (prep_level_props.py's `gpu_bilinear`), not
   resample-vs-resample - or just add it to that tool's PROPS.
3. Wire via `SceneAssets.bitmap("x.png")` (default `minified = true`; `minified = false` for art
   drawn ~1:1 or larger, for art you sized to under 2x its box per step 1 - see the shrink pass above
   - or sub-sliced - mips bleed across slices). **A level prop goes through
   `SceneAssets.levelArt(name, needed)`** with `needed` = its draw site's condition (widen rather than
   narrow), then run `GameplaySceneArtTest`.
4. `SceneAssets.warnIfNotPowerOfTwo` prints one line per offending asset - a new line means something
   needs sizing.

- **Crop on ALPHA** (`np.nonzero(alpha > 8)`), not `Image.getbbox()` (it bounds every channel and
  kept invisible margin - floated `exitlvl7.png` 12 units). Tight-crop silhouettes before stretching
  into a box, re-deriving the box width from the cropped aspect.
- **Aspect doesn't matter for stretch-to-box art** (every draw is `size(box.width, box.height)`) -
  write a literal for the authored size. Don't let a tool "normalise" the pre-mirrored
  `truck.png`/`entrance.png`.
- Source drop: `C:\Users\USER\Downloads\charAnimations\assets\`.
- **Tileable parallax backgrounds**: roll horizontally by half-width so the seam lands mid-image,
  heal with a narrow falloff-weighted blend against a blurred copy, verify by diffing edge columns.
  Band width/blur must scale with source detail (a wide heal leaves a haze band on crisp art).
- **Splitting a prop so a part can move** (`prep_robot.py`): cut the moving part OUT of the static
  plate (an overlay unions with the outline at every angle), slightly wide so nothing survives the
  resample; reconstruct what it hid from scanlines it doesn't reach; find parts by structure, not
  hardcoded coordinates. **Anything that rotates is cropped to its bbox and forced square** (rotate
  about the bbox centre, not the centroid).
- **Chroma-key an opaque JPEG-style asset**: R/G/B all >= 200 -> transparent, then crop.

## JVM desktop screenshots

`./gradlew runJvm` (`-PwindowSize=`, `-PstartLevel=`); to capture an end card, temporarily invoke
`world.onGameOver?.invoke()`/`onLevelComplete?.invoke()` before the `addUpdater` block. Capture from
PowerShell: call `SetProcessDPIAware()` first (else only the top-left ~80% on this scaled display),
front the window (`AttachThreadInput` + `SetForegroundWindow`), click, settle, then `CopyFromScreen`
the CLIENT rect (not `GetWindowRect`). Screenshots can't show a rate (fan speed, wheel rotation) or a
short-lived prompt - pin those by test or `println`.

## Levels

### Level 1 ("01: Night Arrival") - `GameWorld.createDefault()`/`DEFAULT_LEVEL_1`

`worldWidth = 3900`: start gates -> ground -> `smallCrate` -> 3-tier `truck` -> `longPlatform` (a
hanging chained crate to crouch under) -> `stepDownCrate` -> open ground -> `block2` -> seven
`barrel` boxes tiling the gap to `block3` -> a permanently disabled guard zone
(`guardEnabled = false`, parked off-screen) -> exit. Every rise is exactly 48 (under the ~48.5 a jump really clears).
The truck is hardcoded here, not a layout field.

### Level 2 ("02: Cargo Yard") - rain, lightning, thunder (`RainEffect.kt`)

Gated by `LevelData.hasRain` (levels 2, 9 and 12). Both drop layers draw BEHIND the world (handed
the same container as bgLayer and fgLayer); the lightning wash stays on the scene root above
`worldView` (parented behind, it lit the sky and left the yard dark).
- Zero per-frame allocations: fixed pools of recycled `Image`s sharing one procedural premultiplied
  drop slice and one splash slice.
- 40 background drops (alpha 0.16-0.26, parallax 0.20), 55 near (0.30-0.44, parallax 0.85) - halved
  after "too much rain"; re-lower these constants rather than adding a dimming mechanism.
- Wind drift and wrapping around the camera window - nothing simulated off screen.
- Impact crowns open upward like a V (`RainEffectTest` pins the shape) on a static height map
  (`MAX_SURFACE_HEIGHT = 400` excludes the 1200-tall side walls; 16-unit buckets keep the highest
  top). Moving platforms are excluded.
- Multi-pulse lightning (first 2.5-4.5s, then 8-16s) with a jagged sky bolt; thunder 0.4-0.9s later,
  `sfx/thunder.wav`, `THUNDER_GAIN = 0.90`.
- The 2026-09-25 rework wasn't compiled in its own session; later full `jvmTest` runs cover it.
  Never seen on a screen.

### Level 3 ("03: First Contact") - `LEVEL_3_LAYOUT`

Start, one crate, a 420-wide cantilevered table (`table.png`) blocking the ground, a ground
gauntlet, then the exit.
- **The table is a floating climb target** (rise 96) - every bracing shape (stretched plank, solid
  block, invisible box) was rejected on sight; `tablePlank`'s far-end `rightLeg` is a real
  sight-blocking obstacle instead.
- **Roof guard**: near post `crate.right + 120` (crouch-behind-the-crate tutorial), far post
  `tablePlank.right - 120`; `holdUntilPlayerCrouches = true` roots him until the first crouch.
  Timing, not occlusion, is the mechanic.
- **Overwatch pair** on two long crates with `visionTilt = 25` degrees down (most of the gap is theirs,
  directly underneath hidden). **Guard2 starts mid-route rather than mirroring guard1** - any nonzero
  offset between two guards on one route reopens some detection window (proven by simulation), but
  mirroring moved them in visible lockstep. `testLevel3OverwatchGuardsMoveOnDifferentTimingNotLockstep`/
  `...RarelyBothFaceTheMiddleAtOnce` are tolerance-based. **Re-tune by simulating candidate offsets**
  - the relationship isn't smooth and drifts over a long session.
- **`beamCamera`** (fixed sweep past the overwatch pair, on a second floating-climb beam's left
  corner): `maxAngle = 111.3`, `visionFov = 80`, arm `NECK_LENGTH/LENS_LENGTH = 9.5/20`. **Any arm
  resize needs `maxAngle` re-tuned alongside it** (happened three times). Re-verify with
  `testLevel3CameraConePolygonNeverPastCrate`, `...HasNoStrayRaySpikes`,
  `...LeftmostSweepReachesStepCrateFarCorner`.
- **`poleCamera`** on a freestanding non-collidable pole (poles are excluded from `occluders` - else
  the camera self-occludes into a flat rectangle). Pole and camera render at
  `translucentEffectAlpha = 137/255` (the chain art's baked translucency) via
  `LevelLayout.translucentCameras` - the pattern for a distinct camera look without special-casing by
  index. Sweep `minAngle 20 / maxAngle 160 / visionFov 50 / visionRange 230` - re-derive (don't just
  shrink) when the cone changes (`testLevel3PoleCameraSweepsLeftAndRightAndReachesBothFlankingBoxGroups`).
- **Ground dressing under the beam**: two `barrel.png`s; three wood crates in a brick stagger (two
  base crates touching, one on top 20 in, sunk 2 to seat its corner ears). Wood-crate art needs an
  alpha crop (re-measure if swapped; now `woodcrate2.png`).
- **Final crossing**: `cameraBeam -> finalHangingCrate -> finalPlatform`, gap 56 (ceiling
  ~56.5-56.66 by binary search + timing-slack scan), lift 2.0 (near the max for that gap) - re-search
  if either changes (`testLevel3CanJumpFromCameraBeamAcrossToFinalHangingCrateAndOnToFinalPlatform`).
  `finalPlatform` dropped 144 -> 96 on request (a real climb; the leg after it became an easier
  ~50-unit drop, not re-verified at the same precision).
- **Waits are searched, not guessed**: `LEVEL_3_HOLDS` (seconds stood still crouched on reaching each
  100-unit stage) came from a backtracking replay search. Re-derive after a level 3 change:
  `REPLAN_LEVEL3=1 ./gradlew jvmTest --tests '*testReplanLevel3' -i` and paste the printed list.
- **Ends at level 4's conveyor belt** (2026-09-29): a real `ConveyorDef` (26 tall, speed -45, from
  `finalExitX + 70` off the world's right edge); `exitZone` starts 70 short of it, so route and clean
  run are unchanged. A conveyor near the exit replaces the booth + fence.
- **Every belt has a left end cap** (`resources/conveyor_end.png`, 128x128, drawn 30x26 against
  `bounds.x`, outside the collision box - level 4's lands off-world at -30), cut by
  `tools/art/prep_conveyor_end.py` (its header explains the mirrored drum top and the cut at source
  column 364; the drum is repainted as one wheel with a full belt ring).
- Earlier stages verified on JVM screenshots; the conveyor ending not seen on a screen.

### Level 4 ("04: Moving Target") - `LEVEL_4_LAYOUT`, the conveyor run

Ground `y = 440`, `worldWidth = 8600`, exit `x = 7680`, `backgroundImage = "metalbg.png"`,
`hasDarknessVignette = true` (centred on the player, between world and HUD), `canClimb = false`,
`restartOnConveyorFallOff = true`, `conveyorsStartOnMove = true`.
- Belt `speed = -45` against the player (net walk 87 px/s, crouch crawl 20). Floor crates (68x48)
  ride it; four hanging crates (`isHanging`, some bobbing) force crouch-slides under two (crate top
  366 vs hanging bottom 340 = 26 of air; a crouching head clears by 18).
- Eight timed lasers (`Laser.kt`). The beam texture (`LaserFxAssets.beamBitmap`, all levels) is 4x16
  and mipmapped (2x64 unmipmapped broke slanted beams into dots).
- **The game's metre scale is defined here, in wall decals only**: six stencils `wall_{150..0}m.png`
  1500 units apart = **50 units per metre**. Level 7 reuses it.
- **The wall grows taller, never wider**: `metalbg.png` hangs from the screen top at a fixed scale
  (tile = exactly 1000 units - any other width walks the 1500-apart stencils onto pillars); on
  canvases taller than 480 texture rows 560..630 (plain panel + pillar shaft) stretch by
  `canvasH - 480`.
- **End machine = `l4end.png` cropped to its black silhouette** (the doorway frame and dressing were
  removed on request); the belt runs into the black face. Wrapped crates (re-entering at ~8026..8124)
  are hidden while inside the machine (`crateHiddenFromX`).
- **Emitters hang from the screen's top edge** (`LaserVisual.createAll(visualTopY = ...)`, level 4
  only) - drawing only; the lethal segment is still `topY = 150..bottomY`.
- An earlier iteration reached MISSION SUCCESSFUL on JVM; the wall/machine/emitter changes not seen
  on a screen.

### Level 5 ("05: The Crane Yard") - the swing

The original barrel-wall + hook layout, restored verbatim from git history. No guards. Its hook
crate is `physical` and falls into the gap under its fixed hook.

### Level 6 ("06: Stolen Manifest") - `LEVEL_6_LAYOUT`, five sections

- **Section 3 - the crane crossing**: climb onto the boom from `tallBlock`, walk it, come down the
  machine's own silhouette. **The boom height is pinned** (`boomBounds.top` at the canonical 96 rise
  above `tallBlock`) and the crane's size follows via `CraneDef.heightForBoomTop(...)` - never
  hand-pick `craneHeight`. `CraneDef` collides as three boxes (boom, body, house); the tracks' deck
  is NOT a step. Climbing under the boom ends crouched (`ClimbTarget.endsCrouched`). **The crane's top
  is denied the mantle but stays jumpable if in jump range** (`LevelLayout.unclimbableBoxes` skips the
  mantle only) - the owner: "MAKE IT JUMPABLE ONLY IF IT IS"; deny the move, don't bend collision.
- **Section 4 - the gantry-gated climb**: `lever_3` sweeps a hanging crate
  (`phaseOffsetSeconds = periodSeconds/2`, so it starts parked, blocking). The gate is the clearance
  (`gateCrateClearance = 30`, under the ~56 crouch-climb threshold); the sweep goes LEFT and stops 8 clear of the cab
  (`gateCrateMinX = houseBounds.right + 8`). Landing window ~53% of a 7s cycle vs a ~2s climb.
  **`MovingPlatformDef.crushesOnContact`**: a climb caught under the load's underside is Mission
  Failed (checked only from below).
- **Section 5 - hanging platform, switch, laser curtain**: `endCrate` bridges block -> platform (48, a
  jump; the platform hangs level with the crate's top). The 65 gap is a hinge: jump physics caps it
  near ~78 (84.5 of flight minus ~6.5 landing, by simulation), and it's wider than the player, so
  walking off the lip drops into the corridor - jump across for the switch, walk off for the way out.
  **Nothing may stand in that chute** (rejected twice) and **nothing is drawn holding the platform
  up**. Three lasers hang from the platform's tip (`mechanismId = "lvl6_exit_lasers"`), disabled for
  good by `lever_4`; the patrolling guard's 170 beat makes the jump-while-he's-turned work.
- **The plank guard HEARS the landing** (NORMAL noise, 180): the walkthrough goes once he is 140-200
  out on his beat. **The pit's overwatch guard is on a fixed clock** - the run needs a 0.5s pause on
  the far terrain (0.5..9.5s all work, none doesn't).
- Verified by a full walkthrough and JVM screenshots of three areas.

### Level 7 ("07: Service Tunnel") - `LEVEL_7_LAYOUT`, the vent gauntlet

120m / 6410 units, a linear duct, **standing height throughout** (`ceilingBottomY = 304` vs
`groundY = 440`) - three crouch restrictions were built and removed the same day ("remove the crawl
under things"); don't reintroduce without asking. Vocabulary: wind, steam, drones.
- **Vent fans** blow 135-145 u/s back down the duct; walking can't beat it (`WIND_WALK_FACTOR` 0.6
  inside a zone) - spam-tapping forward is the mechanic.
- **Camera bots** patrol with a forward cone (range 120, FOV 40); walking into it is Mission Failed.
  Sneak up from behind within `deactivationRange = 52.0` and INTERACT to switch one off for good.
- **Steam pipes**: top/bottom/paired nozzles on timed cycles with a warning flare; contact kills,
  deflectable once by the Laser Shield. `SteamPipe.bounds` kills across the whole span, so **the
  plume must always span the full corridor**.
- **Sequencing**: hazards staged with recovery room, 7 manual checkpoints (one per 20m), none under a
  duct (a checkpoint respawns standing). **One deliberate exception**: `lvl7_pipe_9` sits inside
  `lvl7_fan_3`'s zone - the "take a steam window at tap pace" beat (why the walkthrough's budget is
  400s).
- **The difficulty curve is measured** (`testLevel7DifficultyRisesFromStartToExit`): every back-half
  steam window tighter than every front-half one, faster drones, harder fans, no empty 20m beat. Six
  beats each add one thing (headwind -> steam -> first drone -> gate + faster drone -> jet in a wind
  zone -> gust + tightest pair + drone on the door). A lone pipe is never the difficulty. 46.7s of
  pure walking is the theoretical floor (clean run 107.7s).
- **Bots switched off from behind**: waiting at a bot's near end while it heads away no longer works
  (it holds still once it sees you). Bot 2's reach (135) runs past pipe 3790, so wait before that jet,
  then run the bot down from behind (`catchable` in `testLevel7SimulationPlayableWalkthrough`).
- **Wind tap mechanic**: a tap buys decaying velocity (`fanSurgeSpeed`) rather than the old flat
  10-unit jolt. Every constant came from simulating the loop - a touch player holds a button only
  part of each tap, and at the old numbers the level ran BACKWARDS at a realistic phone tap rate.
  Three boundary bugs, all found in the running game: only the WIND is zone-gated (clearing the
  surge at the zone lip stalled the player there); `FAN_INTENT_WINDOW` (0.35s, refreshed by any
  tap/hold) switches to a fast release decay once the player stops (momentum near a steam jet is
  lethal); taps register slightly outside the zone (`windStanceBlend > 0`). `FAN_TAP_FLOOR` (40)
  stops a cold-start burst netting negative velocity.
- **Performance**: procedural textures/cones/LEDs/duct frames live in `VentFxAssets.kt` (keeps
  `GameplayScene.sceneMain` under the JVM 64KB method limit); `VentObstacles.kt` is pure Kotlin;
  every emitter culls itself off camera. Wind and steam blend normally and occlude the wall;
  particles have real lifetimes; textures are baked variants, not rotated/negative-scaled (bug #8).
- **Background pinned to the world duct at every aspect**: `bglvl7.png` is drawn at a fixed
  `LEVEL_7_BG_SCALE`; texture rows 186..526 (the duct, split inside both black beams) always at it,
  the scenery above/below stretched (`level7SceneryStretch`, up to ~1.8 on 7:6); `level7FloorScreenY`
  anchors `worldView`. `testLevel7PaintedDuctMatchesTheWorldDuctAtEveryViewport`.
- **Stencils**: five, 1500 apart (`LEVEL_7_MARKER_*`, asserted against the layout - move the exit,
  move the markers). White on disk (`tools/art/prep_wall_markers.py` - exact, since the art is all
  alpha; `colorMul` can't lift to white), drawn 28 tall (not 32 - the widest plate needs exactly the
  widest bare window at 28) inside `worldView`.
  `GameplayScene.findClearWallX` nudges each up to 300 units until clear of fans/pipes (hard) and
  busy background art (soft, two-stage), falling back to the nearest machinery-free spot (owner
  pre-authorised inexact positions); the right limit is the nearer of `exitZone` and the exit
  structure. `VentVisualsTest` checks all five. Level 4's decal pass is gated on
  `bgFileName == "metalbg.png"` - **a shared map keyed by label is not a level gate** (it drew level 4's stencils
  into level 7). 120m/90m/0m read correctly on JVM.
- **Ends at the security desk**: `resources/desk.png` (1024x512) is the `exitStructure` (6240, 95
  tall); back wall at 6450 (world 6490); `exitZone` 6300.
- **Patrol rover** (`resources/robot_{body,wheel}.png`, `tools/art/prep_robot.py` - source of truth
  for every fraction in `CameraBotVisual`): the body is cut so the cogged wheel can turn without
  unioning into a shimmering outline. Wheels are driven by distance (`rollAngle += dx / r`; a respawn
  teleport swallowed via `abs(dx) <= bot.width`); the mirrored chassis reverses a child's rotation,
  so `CameraBotWheelTest` (8) asserts on rendered `wheels[i].rotation`. The lens is dark unless the
  rover has the player (`alertLens`, one red rect); a switched-off rover throws amber sparks on a
  1.5s blink. `EYE_HEIGHT_FRACTION` 0.20 (gameplay-visible). The plate is treated as facing RIGHT - a
  judgement call; if wrong, flip it in `prep_robot.py`, never the sign in `CameraBotVisual`.
  Collision box 32x26. The procedural crawler is the fallback if a bitmap fails. **A black rover on a
  dark floor is intentional** - `colorMul` does nothing on RGB-zero art. Size/placement/mirroring
  confirmed on JVM; wheel rotation pinned by test only.
- **Nozzles are the owner's art** (`resources/steam_nozzle_{up,down}.png` - two files, no negative
  scale), mounted inside the corridor, bolted through the wall (`BURY_FRACTION` 0.22 - deeper hides
  the bolts and narrows the silhouette). The plume is built before the fixture. **The status lamp is
  the player's timing tell**: green/amber/red by phase, red only while gas is out (swept in
  `VentVisualsTest`), seated on solid metal.
- **Fan blades 16.0 rad/s** (~2.5 rev/s), capped by the strobe effect (N blades read as stopped once N
  rev/s nears 30 at 60fps) - pinned by test.
- **Removed - `VentVisualsTest` pins each absence**: the exit terminal (`VentCorridorVisual`, don't
  resurrect), the rovers' always-lit cyan running lights.

### Level 8 ("08: Relocation") - `LEVEL_8_LAYOUT`, the suspended-load yard

**Level 8 is the ground road; level 9 (same yard) is the road along the loads.** Level 8's hanging
loads are off limits (`LevelData.hangingLoadsOffLimits`, level 8 only): every hanging load (static,
moving, hook crate still hanging) gets a 400-tall invisible lid (`OFF_LIMITS_LID_HEIGHT`) and is never
a climb target. The high-road hop/swing tests build the yard boardable (`level8Unwatched`);
`testLevel8TheHangingLoadsCannotBeGotOnto`.

- **The route**: push the step cart to the mid platform, crouch at its far end under the sweep crate,
  climb as it swings away, run behind the crossing crate and off the right lip before the two meet
  (the squeeze), take the empty cart by the barrels and push it under the bobbing chain in one timed
  go, on to the travelling load, pull the lever under the long crate as the load passes over the
  cart, walk the loaded cart flush against the high platform, climb up just after the deck crate
  leaves the near end, follow it out and drop to the floor, run the bottom laser course under the
  table, walk into the exit. Cart, lever and loaded cart are each worked while the pole camera is parked on
  the long crate.
- **Screen height is the ceiling on everything "up"**: the camera never moves vertically and a phone
  shows world y from ~130 down - why the lifted line is 242 (a standing head at 146).

| number | value | what it decides |
| --- | --- | --- |
| `hangY` | undersides level with the platform top (296) | shared hang line for the plane's two loads |
| `platformCrateLift` / `crateToCrateGap` | 24 / 55 | platform crate unreachable from the platform; a timed running jump from the sweep crate |
| `periodSeconds` (every moving load) | 8.0 | one shared cycle, so every hop's window recurs identically |
| `bobTopHighest` / `bobLowClearance` | 250 / 6 | the chain's stroke - high-road stepping stones, floor-level crushers |
| `firstBobGap` / `bobGap` | 88 / 103 | level 9's hops along the chain, each open ~1-2.7s per cycle |
| `bobLag` | (76 + 103) / (132 x 0.4) = 3.39s | the chain is a wave at cart-push speed; at 2.0 no start got a cart through |
| `sweepCrateReach` / `sweepCrateSweep` | 60 over the platform / 260 | sweep crate meets the crossing crate 60 in over the platform; the longer, faster sweep clears the climb sooner |
| `crateToCrateGap` / `platformCrateOverswing` | 4 / 160 | the squeeze; 0.3s to spare climbing as the sweep crate clears |
| `highCrateGap` | 70 | bob 3 -> long crate is a timed hop (~1.75s per 8s); at 80 it never lands. Was 150 ("not possible ... move the rest of the level to left") - everything after moved left |
| `dropLever` x / camera `visionRange` | long crate right - 70 / lever distance + 6 | the camera's reach ends just past the lever, so the long crate's near end is dark - level 9's waiting spot. Nearer than 70 the lever sits where the body lets go of the catch cart |
| `highPlatformGap` | 180 | far past a plain jump from the long crate |
| `sweepPauseDuration` (pole camera) | 20 - sweep time (~18.9) | a 40s cycle, twice the deck load's 20: the deck load's near end (6 + 20k, `phaseOffsetSeconds = period - 6`) alternates between the crate park (level 8 climbs behind it) and the lever park (level 9 swings onto it). At 7.0 they drifted and level 9 could wait ~100s |
| `liftedLine` | 242 (54 over the high platform) | long crate, swing hook and deck crate share it; 54 is past a jump from the high platform |
| drop crate / rope | 56 wide, top 270, rope 94 | lifted with the hook it fell 54 further and missed a flush cart |
| rig far end | 68 short of the high platform | 0 catches in a flush cart (60 let 3 frames in); ~0.75s catch window when parked |
| deck crate period | 20s | 8 and 12 came back before a follower got off the lip; 16 phase-locked with the ~16.1s camera cycle |
| table | top 242, 3050 long | 168 headroom over the floor for level 8's course; level 9's runs on top |

- **Every hop was measured, not argued, and every one is steep**: a running jump simulated at 160
  phases of the cycle (`testLevel8EveryHopOnTheHighRoadOpensForAShortWindowEveryCycle`,
  `testLevel8CrateToCrateJumpOpensOnceEveryCycleForAShortWindow`). Bob -> bob is open 3.3s at gap 90,
  1.65s at 100; the last hop 2.2s at 90, never at 98. Re-run after touching any gap, period, lag,
  stroke or sweep span. Takeoffs are taken while still standing on the edge.
- **`noGroundBoarding`** (every moving load and the travelling crate; matters on level 9):
  `GameWorld.isBoardingFromFloorLevel` records whether the player last stood within
  `FLOOR_LEVEL_BAND` (60) of the floor on anything but a hanging load; while it holds, those loads get
  an invisible 60-tall lid and leave the climb targets. Tests teleporting a body onto a load must
  first stand it on the mid platform for a frame (`level8At`).
- **Travelling hook crate (`HookCrate.sweepX`, `physical`)**: the rig runs on its own clock
  (`sweepClock`), paused while the player swings from it; `currentHook` is where the hook is, `hook`
  its rest rect (the swing-hook identity). A player can ride the hanging load.
- **Box physics (`BoxPhysics.kt`)**: every shipped hook crate is `physical` (levels 5, 8, 9). It hangs
  as a damped pendulum driven by the rig's acceleration (`swingAngle`/`swingRate`, `SWING_DAMPING`
  0.6); `detach()` makes a `RigidBox` carrying hook + swing tangential velocity. Impulse solver, 8
  substeps x 6 iterations, **accumulated impulse clamping with the restitution target fixed per
  contact** (re-applying it every iteration pumped energy and stood crates on end), friction 0.55,
  restitution 0.28 above 60 u/s. Contacts are box corners in static rects and rect corners in the
  box; sleeps (snapping to a quarter turn) only on static supports.
  - Obstacles: ground, `boxes`, carts (loaded = one box; empty = deck + two handle posts,
    `PushCart.deckRect`/`postRects`). Impulses on a cart become `PushCart.vx` (`MASS` 2,
    `ROLLING_FRICTION` 160, `GameWorld.rollCarts`).
  - **Catch**: at rest on an empty deck, tilt under `CATCH_MAX_TILT` (0.12), between the posts; snaps
    to `PushCart.loadBounds`, so a loaded catch cart equals the first.
  - **A miss is final** (owner: "stuck until restart") - no re-hang, no lever reset; a settled loose
    crate is a solid box that blocks carts (`blocksCart`, `cartMinX`/`cartMaxX`). A load supported
    only by post tops spins off towards the side its middle is over (`updateLooseCrate`,
    `PERCH_TIP_SPIN`, outward when dead centre) - balanced on a post was the only way a drop failed across 2304 probed drops
    (`testALoadBalancedOnAHandlePostTipsOff`).
  - **A loose load stood on its end can't be got onto** (`HookCrate.isLooseAndNotFlat`): refused the
    mantle (`climbRefused`) AND capped by a 200-tall lid (`LOOSE_LOAD_LID_HEIGHT`) - from the empty
    cart's handle post it's a 44-unit jump, not a climb.
  - A crate falling onto the player faster than 150 down is a crush. Drawn rotated about its centre
    (`drawCenterX/Y`, `drawAngle`), rope anchored at the top centre.
- **Push carts (`LevelLayout.pushCarts`)**: the first is a loaded flatbed (`cart.png`) at x 610 (clear
  of the overhead crate), 198 short of the mid platform. Two numbers inherited from the crate it
  replaced must not drift: 48 tall (a jump, not a mantle) and its right edge lands exactly on
  `platformLeft`. **The cart is pinned to the body** (`gripCart` records the offset,
  `followGrippedCart` re-applies it - the cart's bounds become his limits). Grabbing needs range +
  grounded and refuses a body standing on the cart. **The grip eases to contact** (`settleIntoCart`,
  `GRIP_REACH = 22`, input zeroed for the 0.85s settle; the offset must be re-derived from the blend
  every tick), **solved for the HAND** (`handleGripX`/`bracedFistX`) - the 4.7 gap between boxes is
  his arm. The tutorial's MOVE step measures cart travel. Two cart tutorial steps (added 2026-09-26).
- **Empty carts (`PushCartDef.startsEmpty`)**: to a body (`PushCart.playerSolids`) the deck plus posts
  up to 12 over it (not 32 - too little clearance to jump in; not 14 - a post top inside a climb
  under the high platform); crates meet the full posts. The scene skips the load art; the caught
  `HookCrate` draws itself behind the cart. The catch cart starts 70 past the barrels (50 put the
  braced back foot over the last barrel); its min is the barrels; the walkthrough checks letting go
  of it at the catch position doesn't pull the lever. Any crushing load touching a cart is Mission
  Failed (`GameWorld.crushingLoadOnACart`); on level 9 the carts are the echo's, so no such failure.
  **A cart is crushed only where it is drawn** (`PushCart.crushParts`: `deckRect` + `postRects` when
  empty, the post-to-post column when loaded); the art rect `cartArt` runs past both posts over
  nothing, but is still what a crusher blocks from the side.
- **Underside-only crushers** (`MovingPlatformDef.crushesOnlyFromBelow`, the sweep crate and bobs):
  `GameWorld.touchesUnderside` - overlapping, the body's top in the crate's lower half, shallower
  through the bottom than across. Grounded flush under one is safe; airborne/climbing within 4 after
  its own move is caught (a head bump is resolved before the check). Side-on they're walls and stop a
  pushed cart (`crusherBlocksCart`). Chain push window ~3.9s of 8 (`level8ChainPushWindow`, cap 4.5).
  Level 6's gantry crate keeps the plain "feet under its underside" rule.
- **The squeeze** (`MovingPlatformDef.squeezes`, `GameWorld.isSqueezedBetweenLoads`): two flagged loads
  closer than a body is wide, its centre between theirs and level with BOTH - riding on top of one
  isn't caught.
- **Pole camera** on the FLOOR against the high platform's left face (lens at 262), left of the deck
  crate's near end (on the platform the deck crate swept through it). It parks on the lever and on
  the long crate's top (angle taken +2pi so the sweep is the short arc up); the 20-degree cone never
  covers both, and while it watches the crate everything under the lens is dark. Its range ends just
  past the lever - bob 3's right is the walkthrough's hiding spot. Two other mounts
  were dropped (one hung under the long crate swept the lever and the catch - no road left).
  **First shipped level with a camera and no guards** - `GameWorld`'s spotted-guard fallback is
  `firstOrNull()`.
- **Section 8 - the deck crate (`lvl8_deck_crate`)** runs on the lifted line from hpl-10 (where the
  swing lands) to 60 past the high platform's lip, period 20; level 8 climbs up in a ~4.7s window
  after it leaves the near end and follows it off the lip.
- **Section 9 - one table** (on the lifted line, 3050 long; level 8 walks the FLOOR under it, "he
  should walk on ground"). `LevelLayout.seamlessTables` draws `table.png`'s left cap (cols 0-248),
  the repeating slab unit (248-481, notches every ~232.5) tiled, and the right cap (the leg crop's
  slab band, 1870-2048) - no seams, no stretching; one `passThroughLegs` leg at the far corner (leg
  crop below the slab band, 1870,102 178x410, at the pole's alpha, 52.4 wide). **Lasers run surface
  to surface** ("connected to ground or the top or bottom of the platform"): floor to the table's
  underside, table top to y -300 - gates on cycles only (upright, slanted up to 45 degrees,
  staggered). Each course (~2340 long) has three camera bots on laser-free stretches, each patrol
  starting 122+ past where a body stands after the gate before it.
- **Ends at Container 17**: `resources/container17.png` (cropped to alpha, 2020x565) via
  `LevelLayout.exitStructure` + `exitStructureImage`, 105 tall on the floor under the table's far end
  (table runs on 30 past it). The exit trigger starts 70 short (`exitApproach`) and runs from the
  floor to 110 over the table top, so level 8 walks in on the floor and level 9 on the table.
- **Walkthroughs** `testLevel8IsBeatableOnTheGroundRoad` and `testLevel9IsBeatableWithoutTouchingTheGround`
  use the `Level8Run` runner and the window tests' harnesses (`level8CatchPhase` pre-simulates the
  rig to find the lever phase that lands the load in the parked cart; `runCourse` crosses a laser
  course obstacle by obstacle), so a re-tune moves them together. The level 9 hop harness must prime
  the body on the overhead crate first. **A swing press is read after the rig has moved that frame** -
  predict `ahead` with ~1.5 of margin.
- **Test traps**: read a moving crate's position AFTER `world.update`; the sweep crate starts at the
  far LEFT, so run the world forward to park it over the landing.

### Level 9 ("09: Deja Vu") - level 8's yard, never touching the ground

`LEVEL_9_LAYOUT` is `LEVEL_8_LAYOUT.copy(...)` - **100% identical bar the spawn** (on the overhead
crate; a test asserts it), so every level 8 retune carries over. `hasRain = true` on
`DEFAULT_LEVEL_9`.
- **Route (the "high road")**: overhead -> sweep -> platform crate -> bob 1 -> bob 2 -> bob 3 -> long
  crate -> swing off the travelling hook -> deck crate (ride it out) -> upper table -> top laser
  course -> drop off its end into the exit.
- **"Never touch the ground" is the MAIN objective** (`LevelData.stayOffTheGround`):
  `GameWorld.checkStayOffTheGround` ends the run on the floor or a `LevelLayout.offLimitFootholds`
  platform (mid and high platforms, NOT the tables). **It checks `layout.platforms`, not
  `GameWorld.platforms`** (which includes `boxes` - the first crate counted as ground). Its one
  tutorial step `step_stay_off_the_ground` auto-dismisses after 6s. Fixed footholds for checkpoints:
  the overhead crate, the long crate, the tables.
- **The echo - level 8's completed run, replayed** (`src/game/model/RunRecording.kt`):
  - **Recording** (`LevelData.recordsRun`, level 8): `GameWorld.runRecorder` takes a `RunSample` every
    0.05s of `runClock` - position, collision height, stance flags, climb progress, both carts' x -
    plus `RunEvent`s (lever, each bot switched off, the catch); a final sample on the exit frame. It
    starts at the first walk input (`GameWorld.runStartSeconds`, null until then - a jump on the spot
    doesn't count; `testLevel8RecordsFromTheFirstStep`). The scene stamps each sample with the sprite
    frame it drew (`annotate`: global index over every clip in `PlayerAnimationSet` order + sprite
    x/y/rotation/flip). Restart clears it.
  - **Saving**: only a completed run (`GameWorld.completedRun`), each completion replacing the last,
    to `run_level_8.txt` under `views.realSettingsFolder` (NOT NativeStorage, which rewrites its whole
    store per set) and in memory (`GameplayScene.recentRuns`, so a level 9 started from the win card
    never races the write). Format `R2`: tenths as integers plus world tracks, a 2-minute run ~260KB.
    An `R1` save (no world) is ignored in favour of the bundled run.
  - **Level 9 loads** the saved run (`LevelData.replaysRunOf = "level_8"`), else the bundled
    `resources/level8_run.txt` (the level 8 walkthrough's recording, no sprite frames - picked from
    stance flags). **Regenerate after any level 8 change**:
    `REGEN_LEVEL8_RUN=1 ./gradlew jvmTest --tests '*testTheBundledLevel8RunIsAWholeRun'`.
    `Level8Run.hide()` turns the body to face up the yard at its hiding spot (facing back, it stared
    at the chain ~46s and blocked level 9).
  - **`EchoRunner` is a pure replay** - it never turns, stops or hears ("make him actually not turn
    around"). Its cone (230 range, 60 deg, from its head) faces the recorded facing and feeds the
    shared alert meter - a catch is Mission Failed. Drawn under the player at 0.82 alpha with a
    guard's `LightConeView` beam and pip. At the end of the recording it stands (FINISHED). So level
    9 is about staying BEHIND it: the walkthrough waits on the start crate until the echo has pushed
    its cart past the bobbing chain. (An instant catch and a "keep the figure in view" objective were
    tried and taken back.)
  - **The cone starts at the drawn eye in every pose**: `tools/art/prep_eyes.py` measures the eye in
    all 640 player frames into the generated `src/game/scene/PlayerEyePoints.kt` - re-run after
    re-cutting any player clip (its clip list mirrors `PlayerAnimations.load()`/`playerFrames`). The
    head is found with a round probe (radius 10 of a 256 frame), not "topmost pixel" (raised hands
    reach higher); the eye sits on the face's front edge, capped so a hand at the face isn't taken.
    The scene maps it through the echo's anchor/scale/mirror/swing rotation into
    `EchoRunner.drawnEye`. Without a scene (tests) it falls back to a box estimate (braced: the push
    head point). `PlayerEyePointsTest` checks the table; the scene ignores it (and logs) if its frame
    count stops matching `playerFrames`.
  - **It drives the level**: carts ride where the recording had them (kinematic); its lever event
    empties the swing hook (~52s into the bundled run); its catch puts the load in the cart even if
    the live drop missed; it switches off the bottom-course bots - only if its feet are on their
    surface (`CameraBot.isReachableFrom`, within 24 of `surfaceY`, for players too - the x-only test
    let level 8 switch off the TABLE bots from the floor).
  - **The yard plays back exactly**: `RunRecording.worldStart` stores level 8's clock at the first
    step and level 9 starts its own clock there (`attachEcho`, `restartLevel`); every sample records
    what keeps its own time (`RunSample.world`, `RunRecording.worldTracks`: `cam<i>` angle,
    `bot:<id>` signed x, `hook:<id>` rig clock) and level 9 drives those from it. A camera/bot/rig
    leaves the recording only when THIS player gets involved (spotted by it, switches it off, swings
    from it); a level 9 respawn puts them back on it. Followed cameras run their own pause timers with
    the angle pinned. `testLevel9PlaysLevel8sYardBackExactlyInStepWithTheFigure`.
  - **Edge cases**: a level 8 respawn/continue calls `RunRecorder.rewindToCheckpoint` (samples/events
    since `markCheckpoint` dropped, the gap filled with the body standing at the checkpoint, a RESET
    event added - level 9 ignores RESET, so an emptied hook stays empty; each sample's world is
    kept). REMOTE_TRIGGER records the LEVER event too. Other powerups need nothing. Level 9's own
    respawn doesn't reset the echo; its passed events are re-applied after the mechanisms reset
    (`EchoRunner.passedEvents`).

### Level 10 ("10: Below the Yard") - `LEVEL_10_LAYOUT`, level 7's mechanics recombined

"Same mechanics as level 7 ... make it harder ... with less time to go through steam". Same duct
(304..440, standing height), same three hazards, every beat a combination level 7 never asks for:
twin intakes -> a drone patrolling INSIDE a gale (run it down at `WIND_SPAM_SPEED` 96; stop tapping and
the wind carries you out of its sight) -> a drone whose patrol crosses a jet -> the relay (two drones;
the first only while the second is on its long leg) -> two jets in a gale (the far one at tap pace)
-> a gust, a four-jet lock, a drone on the door.
- **Harder than level 7** (`testLevel10IsHarderThanLevel7`): every jet's dormancy <= 1.1; late jets
  lower their own floor via `SteamPipeDef.minDormantDuration` (default 0.8 = the old clamp, so level
  7 is byte-identical; `SteamPipe.MIN_DORMANT_FLOOR` 0.5 hard minimum), 0.55 on the lock; faster
  drones with `pauseDuration` 0.5; harder gales; 4 checkpoints for 6 beats.
- **Limits found by simulation**: the door drone at 58 u/s over 120 needed a 1.74s catch against a
  2.07s turn (now 56 over 140); the gale gate's far jet at 0.75 dormancy stalled the walker 53s
  (kept at 1.0); the relay's second drone has ~2x the first's cycle (near-equal cycles reopened only
  every ~86s).
- **Background `bglvl10.png`** (2172x724): the lamp-lit corridor under the art's beam is the duct, the
  texture's bottom edge is the floor at the standard `worldView.y`, hung up from it at a fixed scale
  (`level10BgScale`: beam underside row 470 on world 304, row 724 on 440); taller canvases stretch the
  plain wall (rows 0..430). **The art's beam (rows 427..470) is painted solid black** in every tile
  (where nozzles bolt in); the duct ceiling box isn't drawn. Parallax 1:1. Rows measured by mean luma
  down bare-wall columns - re-measure if the art changes
  (`testLevel10PaintedCorridorIsTheWorldDuctAtTheBottomOfTheArt`). No distance stencils (they load only
  for `bglvl7.png`).
- **Rooms - walls passed above, guards below**: a small room (1520..2440: two walls, one guard) and a
  big one (4520..6040: three walls, two guards, a jet and a drone in the duct below). The duct's
  ceiling slab (281..304) is the rooms' floor. Each floor-to-ceiling wall has a 140-wide shaft in
  front - ONE 68x48 crate (a jump) then a 111 mantle (under the 115 cap) onto the slab (a
  `floatingClimbTargets` entry); past each wall a 100-wide hole drops back into the duct under the
  next guard. (Shafts at 100 left 32 in front of the crate and the head bonked the slab.) The wall's
  top is no foothold (`findClimbTarget` skips it). Guards: 96-tall, range 180, pause 1.0, never
  reach a gap. `canClimb = true`. Room ceiling 140 (the reference phone's top is world ~114).
  `testLevel10sRoomIsTheOnlyWayPastEachWallAndTheOnlyWayPastEachGuardIsBelow`.
- **Room art**: `LevelLayout.roomBackdrops` tiles `room.png` (resampled to 2048x512) at the rect's
  height at its authored 3:1. A floor gap shows the tile's LAST 8 rows stretched down
  (`ROOM_GAP_SOURCE_ROWS` - a taller band showed a lighter strip; "there shouldnt be black in
  climbable parts"); a flat grey `beamOpenings` fill was rejected. Draw-path traps: a short slab
  matched the step-crate rule (skipped for `bglvl10.png`, except `plainPlatforms`), and a tall room
  ceiling matched level 1's hanging-crate rule (ceilings are `plainPlatforms`).
- **Ending**: the duct is shut by `lvl10_door` (7990, switch at 7956); `lvl10_lift` (8030..8150) stands
  against the end wall with its switch on the wall (8130, reached from the lift) and rises into the
  last room (7960..8600). At its far end a `PrisonCellDef` (`LevelLayout.prisonCell`): bars over
  8380..8560, black gloom, the figure starting at 8490 facing the door and PACING the cell - the
  player's walk/idle frames, walked by the scene's updater (`CELL_FIGURE_SPEED` 110, the escorted prisoner's pace, a 1.4s stand
  at each end, turning 26 short of the side posts; pure decoration, not in the model), bars
  procedural - swap in real art if it arrives. Its switch tutorial opens at x 7840 with the
  callout at 7720 (at 7950/7620 it rarely triggered and sat off the left of a phone screen). Exit on the room floor 70 short of the bars; a level with a cell draws no booth. World
  8600. **`step_use_switches` (7950..8150) is level 10's ONLY tutorial**; on a level with switches an
  INTERACT step completes on a thrown switch (`GameWorld.switchThrowCount` - the old condition was
  already met by the bots switched off earlier). A restart shuts the door and lowers the lift (a
  respawn leaves them).
- The walkthrough climbs by pulsing jump when pushing against a face it isn't passing, throws a
  switch in reach of a shut door ahead or the lift it stands on, and waits on the floor short of each
  crate until the guard ahead walks away with >= 4.5s
  of beat left (`SHAFT_EXPOSURE_SECONDS`); dropping through the big room's second hole gives its second
  guard a partial glimpse (drained) - drop while he faces away. **The finish time is quantised by the
  lock** (the last jets and the door drone run on the level clock), so upstream changes rarely show.

### Level 11 ("11: The Prisoner") - `LEVEL_11_LAYOUT`, an escort through level 10's tunnels

"help the prisoner you freed in level 10 escape ... he will move forward whenever possible. he cant
climb or parkour. you can use doors" (the request said "the guard" - read as the prisoner; confirm if
wrong). **No tutorial at all** - level 10's end teaches switches, doors and lifts
(`testLevel10UsesLevel7sMechanicsAndNothingElse`).
- **`Prisoner` (`Prisoner.kt`)**: a `Player` body driven by a rule - walks right at 110
  (`PrisonerDef.speed`) once freed (cell gate `freedByDoorId` half open; the id may also be a lift
  that first moves, a hook crate that drops, a lever-started load, or `WHEN_THE_PLAYER_SETS_OFF` - the
  player's first move: level 12's, where he starts beside the player - + 0.8s getting up), stops only
  at a shut door, a wall, or a lip deeper than `MAX_STEP_DOWN` (30) - a crack under 4 with footing on
  both sides is not a lip (`Prisoner.CRACK`, made for an earlier level 12's dropped crate); never
  jumps/climbs/crouches.
  Since 2026-10-22 a moving load carries him as it does the player, and a crushing load
  (`crushesOnContact`) that would crush the player loses him (GameWorld; made for an earlier level
  12's ferries and gate). Level 12's lever crate ferries him again since 2026-10-26 - he walks off it
  in the ~0.4s it lies flush against the far container.
  **Every lift stands on its floor at the bottom**, deck 14 up (`LiftDef.lowerY` = `floorY` - 14,
  `LiftDef.lowerStopOver`): level 12's since 2026-10-26 ("the elevators go too much below surface"),
  levels 10 and 11's later that day ("fix that elevator going below surface issue in all levels" -
  they sank flush, the whole deck drawn under the floor's top). `Player.update`'s `steps` lets a body
  on its feet walk up onto a deck (up to `MAX_STEP_UP` 16) and the normal walk-off steps down; a
  `DoorSwitchDef` is reached from up to 20 over its floor (a deck standing on it); a guard walking
  that floor steps up onto the deck too instead of turning at it (`GameWorld.walkGuardOverDecks` -
  level 11's pen guard crosses lift_2). Level 11's clean run went 132.8 -> 122.4s with it: lift_3
  down 0.3s sooner meets jets 4 and 5's earlier windows (script luck - target kept at 195).
  He also stops short of a lift's shaft while that lift is up off his floor (`isAtRaisedLift`; not
  for a lift with `holdsHimWhileUp` false - level 12's lift_5, alone in the open road): a
  lift never comes down on a body, so one sent up before he arrived used to strand him under it for
  good (level 12, 2026-10-19 "make sure the level has a solution").
  Seen by anything = the shared alert meter (`GameWorld.isPrisonerSeen`, pip over him); steam and an active
  laser kill him (`prisonerLost`; lasers since 2026-10-21 - he goes nowhere the player cannot). Since
  2026-10-26 he **waits at a laser** that is on, or will be before he could reach and clear it
  (`Prisoner.isAtBeam`, 0.12s to spare either side: there after it goes out, through before it comes
  on). Beams too close together to stand between (under a body + 5 clear) are one run he crosses in
  one go or not at all, and he never stops inside a beam once he has set off. A beam's `offIn` /
  `offFor` (`GameWorld.beamsHeWaitsAt`) let him go on a beam that is still on but will be off when
  he gets there. He also **waits for a lift to stop** before stepping onto or off it
  (`isAtMovingLift`). Being caught leaves the player where he was (it used to put him at the start,
  which the Checkpoints powerup's death beat showed before the respawn). The level completes only when he AND the player are in `exitZone`. Drawn fully
  black with the player's frames (grey tint rejected), created before the cell so its bars cover him
  (`PrisonCellDef.drawsFigure` = false).
- **Doors (`Doors.kt`)**: `DoorDef` floor-to-ceiling, the panel rolls up (`openness`; only the
  hanging part is solid, passable at ~0.7). A shut door blocks bodies, guards (they turn back - doors
  decide a beat) and sight: `GameWorld.visionOccluders` = occluders + door panels + lifts, part of
  `sightBlockers` (see Detection), which every vision check and drawn cone uses. A closing door
  never comes down on anyone in its doorway.
- **`DoorSwitchDef`**: INTERACT (edge-detected) toggles every door/lift in `targets`; doors carry a
  colour tag that their switches show. **`LiftDef`**: two stops (room floor 281 / deck 426 standing
  on the duct floor 440 - sunk flush until 2026-10-26), carries riders, never descends onto a body,
  freezes while a guard is in its shaft - unless it `carriesGuards` and he stands on its deck: then
  he rides it, and carried off his floor he paces the deck (level 12's lift_5, 2026-10-26).
- **Shafts have catwalks, not crates** (a crate would stop him dead): 6 thick at 326 (climb 114,
  underside 332 clears his head at 344), then a 45 **hop** onto the slab. Catwalks are
  `LevelLayout.hangingClimbTargets` -> `Player.hangingClimbTargets`, grabbed from `HANGING_GRAB_BEFORE`
  (12) short of the near edge to `HANGING_GRAB_UNDER` (30) under it; `startClimb` snaps the body flush
  against the edge on the grab frame (the clip's hands only meet the lip flush). Drawn as a black pallet on two
  cables from the room ceiling (a bracket off the slab was rejected twice).
- **Lifts are `resources/elevator.png`** (`tools/art/prep_elevator.py` and `GameplayScene.ELEVATOR_*`
  are the source of truth): deck scaled to the lift's 14 thickness, railing + cables at
  `translucentEffectAlpha`, any width drawn as two end posts with the first bay's mesh repeated;
  drawn before the prisoner and player.
- **Design rule: the player gets ahead of him only by a bypass (shaft/hole) or by overtaking while he
  walks**; a door he waits at can't be passed without releasing him (the first draft deadlocked). At
  110 vs 132 overtaking to shut a door in front of him no longer works, so **beat 5 is "clear the way
  first"**: drop into lift_2's shaft, ride it up alone (`sw_lift_2_below`), switch the bot off, send
  the lift down, then bring him up. Its old race door (`door_6` and both its switches) and its two
  checkpoints are gone - a respawn while he waited on lift_2 switched the bot back on with no way up.
- **Beat 6 "Over the top"**: door_7 holds him with its only switch past it, where `lvl11_bot_2`
  patrols; the player goes up a third shaft into a room with two room-height jets (`lvl11_pipe_4/5`,
  topY 140), drops through its hole while the bot heads back, switches it off from behind. World 6100.
- **Levels 10-11 backdrops are brightened per level** (`LevelData.backdropExtraBrightness`: level 10
  0.2 = 1.2x, level 11 0.3 = 1.3x; `GameplayScene.brightenedImage` draws the slice again with
  `BlendMode.ADD`) - bglvl10.png and room.png as painted were too close to the black silhouettes
  ("too dark", 2026-10-19); 1.45x on both was "too bright", then level 11 alone "still too dark" at 1.2x and "too bright" at 1.4x.
- **Exit sign** `resources/exit_sign.png` (256x128, drawn 32x12.6 - a third of his height, like a real
  sign; 64 was "too large" - at `brightness` 0.7) at 5824,162 - up in
  the hatch room over lift_4's shaft, not on the duct wall (user, 2026-10-19) - via `LevelLayout.wallDecals` / `WallDecal` (any back-wall picture, no collision).
- **Checkpoints are his**: taken when HE is held (still, grounded, unseen) in a zone; a respawn
  restores doors, lifts, guards (`Guard.resetToSpawn`/`placeAt`) and him - and, since 2026-10-26,
  which levers were thrown and which beams they cut (`EscortSnapshot`; a lever can work a lift now,
  and the two must stay in step). A restart puts guards back at spawn on this level only.
- Door/switch art is procedural `solidRect`s, untuned.

### Level 12 ("12: Final Escape") - `LEVEL_12_LAYOUT`, the escort along the container row

**Rebuilt from scratch 2026-10-23** without looking at the version before ("clear level 12. dont
even look at its current version ... start with the usual two fences and end with a exit from the
shipyard ... above ground unlike levels 10 and 11 ... use as many mechanisms from other levels as
possible ... interesting and somewhat hard"). **Grown 2026-10-26**: the yard's three hung crates
became one platform, and two beats were added - "add a new section where the player has to jump from
some moving crates and do something so the player can come from bottom" (asked: he walks the bottom -
the way along the ground is shut, the player crosses on the moving crates and opens it from the far
side) and "add a part where player crosses to otherside and have to press some button to make the
moving platform move for the pisoner to cross". The layout's doc comment is the source of truth.
Standing rules still in force: outdoor mechanisms plus lifts only (2026-10-07), every drawn size one
an earlier level draws (`testEverythingIsDrawnAtASizeAnEarlierLevelDrawsIt` now also takes level 1's
code-built boxes, for the chained crate, and checks lifts, bots and laser emitters), nothing to watch
above y ~149, he goes nowhere the player cannot, switches by their lifts, no soft-locks.

Later on 2026-10-26: "bring this floating platform part more to the left (to intersect with the
platform)", "in this part add something interesting to the bottom instead of just getting down and
climbing", "make the prisoner start at the same place as the player", "the elevators go too much
below surface" (asked: the floor rests on the ground, walkers step up onto it). Then: "It is still
impossible for a human to predict that last laser part at the end of level 12. replace it with
something doable by humans but hard enough not impossible like the current version" - the fence and
the second guard became a gate of beams opening in a wave at the player's walk; and then, with a
screenshot of the screen crate and that gate side by side, "remove this part close to the end of level
12 and remake that part with something more interesting" (asked: both crates go; the new part is a
hook swing to lift_5's switch, timed against a rover - beat 6); then "make it so that in that part a
guard moves left and right and we have to time to take the elevator down and when the guard gets
over it we have to lift it up / also move this button to the right corner of that crate / also keep
the moving robot as well. move it more to the right and move the exit more to the right".

**His road is the top of a container row, 144 up; a freight lift's shaft is what holds him** (down,
a 130 drop onto the deck; up, the road). **Lifts stand on the ground** at the bottom (`LiftDef`,
deck 426..440, stepped up onto and off - `testEveryLiftStandsOnTheGroundAndIsSteppedOnAndOff`); a head
on a deck is 4 into a hung crate's underside at 300/38, so nothing hangs flush beside a shaft he
walks out of (the landing crate is a shaft's width past lift_5). The first three shafts' lifts each have ONE switch
inside the shaft on the far container's face, reached from the lift at the bottom: drop in, throw it,
ride up with him. So those shafts never trap the player (a raised lift closes it; the yard under the
platform is the one shaft open at the bottom, and from under its raised lift the switch still lowers
it onto nobody - `testTheYardIsNeverATrap`). Five lifts, five panels. Lift_5 is the exception to
"a raised lift holds him": standing alone in the open road it has nothing past it to strand him
against, so he walks on under it (`LiftDef.holdsHimWhileUp` false), and it carries guards
(`LiftDef.carriesGuards`).
1. **Setting off** - he sits a step ahead of the player inside the start fences and gets up at the
   player's first move (`PrisonerDef.WHEN_THE_PLAYER_SETS_OFF`; `GameWorld.hasClockStarted`). An
   overwatch guard paces a hung crate over the road (level 3/6 numbers: tilt 25, range 220, 35 u/s,
   3s dwell); his crate hides whoever is under it and footsteps carry 180 (crouch is silent). **The
   guard catches in half the usual time** (`GuardSpawn.catchTimeScale` 0.5, 2026-10-26:
   "the detection seems too slow. i just walked past this and the guards meter didnt fill" - a tilted
   torch only reaches a standing body near the end of its range, where the meter fills slowest, and a
   walk crossed that band in ~0.6s; now walking through it is caught, clipping its end is a glimpse -
   `testWalkingOutIntoTheFirstGuardsTorchIsCaught`). Set off 1.0-4.7s before the guard turns back and
   he walks under unseen (12.7-0.9 glimpsed; 4.8-12.6 caught). He steps onto lift_1 (860, 146 past the
   crate) and waits against c1; the player creeps after him behind the guard's back and they ride up.
   **Gone with the old pickup**: the wood crate and barrel hop, the 96 quay and level 1's chained
   crate - all walls to a walker who cannot hop, climb or crawl, and with no player-only floor left to
   carry them (the chained crate needs 58 over a 96 block, and everything on the ground is his road).
2. **The rover** - lift_2 down, level 8's rover on the next container. Swing over the shaft on level
   5's hook (grip 120 over the road, 86 out) while the rover is up the far end - at its near end it
   sees just past the landing - wait at the lip (164 off), switch it off from behind, drop back in
   and bring him up. Raised early, he walks into the rover.
3. **The curtain** - his road crosses the yard on a platform to lift_3: level 6's plank (the 30-deep
   table slab, nothing holding it up), drawn seamless like level 8's long table, over the same 522
   the three hung crates spanned, its near end run 24 into c2 so the slab's rounded end cap is inside
   the container (`testTheYardsHungCratesAreAPlatformNow`). Level 6's lever curtain
   (3 beams, emitter 0.6) stands on the container past it. The lever is at the yard's far end under
   the platform (114 headroom), behind level 8's bottom course: three timed gates (platform underside
   to floor) and a bot on a 70 beat, whose near end is 120+ short of where a body waits for gate 3
   (out 2.0 of its 3.6s since 2026-10-26 - at 1.2 it and the bot came round together only every
   half-minute or so). Raised before the cut, he walks up to the curtain and waits at it.
4. **The ferry** - the row breaks off at a 574-wide dock. Level 6's lever crate (174x38, one-shot,
   re-arms its lever; level 6's speed, run 400 out over 9.5s) is parked flush against the near end,
   level with the road: he walks onto it and waits at its far end (a checkpoint). **The dock floor**:
   level 8's step cart (92x48) parked just past where a body dropped off the parked crate lands, and
   a third rover (`lvl12_dock_bot`) working the floor in the shade of the parked crate (10 of
   headroom under it), whose sight never reaches him up on the crate. Drop in while it heads away,
   switch it off from behind, push the cart 250 to the far container's foot (it never goes back under
   the parked crate: `minX`), hop on, climb the 96, throw the lever there (24 in from the lip, like
   level 6's lever_4); the crate lies flush against the far container for ~0.5s and he walks off.
   Thrown early it goes and comes back empty; wherever the cart is left it can be got behind and
   pushed (`testTheDockIsNeverATrap`); the crate carries the player back. A cart does not collide
   with a rover, so the switched-off rover is kept out of the cart's path.
5. **The descent** - lift_4 starts up; he waits on its far lip, 144 over the road. Past level 2's two
   moving crates (its exact spacing from the lip they are jumped from, 3.6s, half a cycle apart)
   **lift_5 stands up in the road** where level 2's long crate stands (408 out). Jump the crates from
   lift_4's lip to lift_5. A **road guard** paces below (level 10's room guard: 55, 180 torch, 1s at
   each post; 80 short of lift_5's shaft to 40 past it); his torch (22.5 either side of level from 54
   over his feet) never reaches the row, nor does a footstep up there (footsteps carry down, not up),
   so the crossing is never seen or heard
   (`testTheMovingCratesAndTheHookTakeThePlayerToLift5sSwitch`, six start times).
6. **The hook** - past raised lift_5, a shaft's width (120) of open air at the road's height, then a
   **landing crate** (hung 174x38, rowTop): **beat 2's hook** over the gap (grip 86 out from lift_5's
   far lip, 120 over it; `hookOver`), so a running jump off the raised deck swings 75 onto the crate.
   Too far to jump (84), too high to climb from the road: the crates and the hook are the only way
   up (`testTheLandingCrateIsReachedOnlyByTheHook`). **Lift_5's switch** (`sw_lift_5`, on a post) is at
   the crate's far corner. **The guard trap**: send lift_5 down while the road guard is past it (under
   the raised deck he holds it up - a lift never comes down on a body); when he walks back onto the
   lowered deck (he steps up onto it, `GameWorld.walkGuardOverDecks`), send it up and it carries him
   (`LiftDef.carriesGuards`; the lift loop moves guards standing on the deck). Up there he paces the
   deck end to end for good (or until it comes down again); his torch ends 33 over a head on the road
   and short of the switch (`testLift5CarriesTheRoadGuardAwayAndHeWalksOutUnderHim`). Measured: the
   "up" press works from his centre crossing onto the deck to ~2.0s after, on each pass; sent up
   without him on it, he stays on the road (`testLift5WaitsForTheGuardUnderItAndTakesNobodyUpButHim`).
   **The rover** (`lvl12_road_bot`, level 8's defaults) works the road past the crate (right+80 ..
   right+280): drop off the crate's far end while it heads away and catch it from behind
   (`testTheRoadsRoverSeesTheDropUnlessItHeadsAway`). Then back along the road under the lifted guard
   to lift_4's post, lower him, and follow him under it and out through level 1's booth and exit
   fence (exit 180 past the rover's beat); the level ends with both in the trigger. An
   invisible barrier at the booth's front stops a player there first - and the prisoner - inside the
   exit (`testThePlayerCannotWalkOnPastTheExit`).
   **Before the guard is lifted, the road is his**: lowered then, the prisoner walks into his torch
   (`testSentBeforeTheGuardIsLiftedHeIsCaught`), and a fall off the crates or the hook leaves that
   as the only way back up - a continue puts everyone back as checkpoint 4 had them, the player on
   the row (`testAFallBeforeTheGuardIsLiftedStrandsNobody`). Until this change a fall was free (he
   waited at raised lift_5's foot); the guard on the road ended that.
   Replaced the same day, three times: first a fence of 3 beams from off the top of the screen
   opening 60/110 s apart (HIS walk, slower than the player's) with a second overwatch past it timed
   blind from lift_4's switch ("still impossible for a human to predict"); then a gate of 3 beams hung
   from a crate, opening in a wave at the player's walk - beside the screen crate (a curtain hung from
   a crate, cut by a lever that also lowered lift_5), the two read as the same thing twice ("remake
   that part with something more interesting"); then the hook to lift_5's switch with only the rover
   past it, until the guard was asked for.

- **Checkpoints are his** (held at lift_2's lip, at lift_3's, on the parked ferry, on lift_4); spawns are out of the
  rovers' reach. A respawn switches rovers back on and puts the moving crates and the cart back; lifts, guards,
  levers and cut beams come back as the checkpoint had them - guards' heights too, so a guard lifted
  after checkpoint 4 comes back down on the road with lift_5 back up empty
  (`testARespawnBringsTheLiftedGuardBackDownWithLift5`).
- **Measured** (`Level12EscapeTest`, 37 tests, run in an isolated copy; full suite 430 green): the
  clean run (157.5s, target 230, star 2 met), his road against every box/hook/load (the check counts
  a box across his middle - stepping down onto a lift still rising sinks a body's edge past the
  container's corner, as it does the player's), each beat done wrong (lift_2 early: seen; lift_3
  early: held at the curtain; ferry thrown early; lowered before the road guard is lifted: seen;
  lift_5 sent down over the guard: waits for him; sent up without him: he stays on the road; dropped
  in front of the road rover: caught; walking out into the first guard's torch: caught; stepping onto
  a lift still coming up: waits), the crates and the hook from six points in their
  cycle, every hold out of every eye for 30s, lifts lowered early or twice, respawns, both-out exit.
- **Other sessions build this repo too**: concurrent Gradle runs in the same tree clobber each other's
  outputs (2026-10-22: "Unresolved reference 'game'" in untouched tests). Test in a copy of the tree
  (sources, tests, resources, art, paywall-build; no `build`/`.gradle`). robocopy needs `/R:0 /W:0`
  and `null.socket` excluded, or it retries that file forever.
- Not used, by choice: cameras (a sweeping cone over a walker's road always catches him unless it can
  look away from the road, and no pole fits off his road), carts (as his ferry the pusher's body is
  where the cart has to go), the crane, bobbing loads over his head (read as the pre-2026-10-22
  versions; the moving crates added 2026-10-26 swing sideways at the row, above him).
- Scene: never seen on a screen. Worth a look: the lifts standing on the ground at the bottom (and
  the switch panels, still mounted for the floor), lift_5 alone in the road, the platform's end inside
  c2, the ferry, cart and rover at the dock, the second hook and the landing crate past lift_5 with
  its switch on a post at the far corner, the road guard stepping onto lift_5 and riding it up (and
  pacing its deck - the step-up stance code was written for level 11's flat crossing), the road's
  rover past the crate, him sitting beside the player at the start, and levels 10 and 11's lifts
  standing in the ducts.

## Extracted library: `kmp-stealth-game-toolkit` (2026-09-29)

`Geometry.kt`, `Vision.kt`, `Guard.kt`, `Camera.kt`, `Laser.kt`, `MovingPlatform.kt`,
`Conveyor.kt`, `CameraFollow.kt`, `ScreenLayout.kt`, `BoxPhysics.kt` were COPIED (this repo keeps its
own, unchanged) into a separate published KMP library for the hackathon's "giving back" criterion:
[MalithaBandara/kmp-stealth-game-toolkit](https://github.com/MalithaBandara/kmp-stealth-game-toolkit),
via JitPack (`com.github.MalithaBandara.kmp-stealth-game-toolkit:<artifact>:v1.0.0`; see its
README). Six targets (jvm/android/iosArm64/iosSimulatorArm64/js/wasmJs) build clean in its CI; a
Compose Desktop demo there runs vision/guard/camera/laser with zero assets. No dependency either way.
If `:game` ever consumes it, the klib-ABI lock forces source compilation (as `paywall-build` does for
the model), not a binary dependency.

## Keep this file up to date

This is the first thing a new chat/agent reads. Whenever you make a decision, discover a constraint,
or change something a future session needs (tooling gaps, CI status, build quirks, open issues),
update the relevant section - or add one - before ending your turn. **Edit the current-state
description rather than appending a dated "pass" entry**, and point to code doc comments (level
layouts, `PlayerAnimations`, `prep_*.py`) rather than duplicating them. Record what wasn't verified.

**Open issue**: level 9 "character replaced by a black rectangle" - NOT reproduced. Best hypothesis:
an atlas page sampled while deleted/half-uploaded (GLES returns opaque black over the
black-with-alpha frame) - the churn `TextureResidency` removes. If it recurs on a build with it, get
a screenshot and what was happening (swing? after a continue ad? figure or player?).
