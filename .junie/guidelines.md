# Project: Infiltrate: Shadow Heist

A 2D side-scrolling stealth game (visually Shadow Fight / Vector, objective Robbery Bob).

**Target platforms: Android AND iOS - both required.** Cross-platform Kotlin Multiplatform
hackathon submission (Shipaton 2026). JVM desktop is for local dev/testing only.

## Read this first: verification discipline

1. **"Compiles" is not "verified."** Several bugs below took 2-4 wrong theories first. Unless a
   section says it was confirmed on a real device/simulator/screenshot, treat it as compile-only.
2. **A GitHub Actions `continue-on-error: true` step's `conclusion` is not a pass/fail signal.**
   It has reported `success` for real failures (undefined symbols, build failures, crashes).
   Always read the raw job log for `BUILD SUCCESSFUL`/`BUILD FAILED`/exception text.
3. **Don't trust a stated tool/library version - check the repo** (`gradle/libs.versions.toml`,
   `git log`, CI toolchain paths) before reasoning from a version number.

**Level design: nothing should visibly float with no structure under it.** A platform/beam/shelf
with no leg/strut/chain reads as a bug, even where physics deliberately treats it as a floating
climb target (`LevelLayout.floatingClimbTargets`). Where the owner rejects bracing the actual climb
gap, give the platform a real support elsewhere along its span - a leg/strut planted on the ground,
clear of the climb point and any mounted guard/camera (see Level 3's `tablePlank.rightLeg` and
camera beam's `cameraLeg`). Apply to every new elevated platform. **Exception: LEVEL_6_LAYOUT's
section-5 platform** - owner asked for nothing under it and later removed its chains too.

Corollaries:
- **JVM `Testing` CI green does NOT mean iOS is green.** Kotlin/JVM default-imports things
  Kotlin/Native doesn't have (`kotlin.jvm.Volatile`, Java `String.format`). Check the iOS workflow
  after any `src/game/**` change; find the multiplatform equivalent before using a JVM-sounding API.
- `:game`'s iOS Kotlin/Native targets can only be compile-checked via CI, never locally on this
  Windows machine.
- Screenshots of the landscape-locked iOS app come out portrait-dimensioned, rotated 90 degrees -
  rotate before judging.
- Before deleting an asset, grep for runtime-constructed paths (`"sfx/$name.wav"`), not just literal
  filenames - and prefer running the game.

## LOCKED WORKING CONFIGURATION (verified 2026-08-25, commit `0b958c3`)

Load-bearing for `:game`. **Do not upgrade without re-running the full iOS build in CI first** -
this exact combination is the only one proven to link on iOS after a long chain of klib-ABI failures.

- **KorGE `6.0.0`, Kotlin `2.0.20`, Gradle `8.8`, JDK `21`** (`zulu` in CI). This machine's default
  `JAVA_HOME` is JDK 19; use JDK 21 (Temurin) explicitly for local `gradlew`.
- `:game` has **zero RevenueCat dependency** (removed 2026-09-12 - see "RevenueCat status"). No
  framework vendoring, linker flags or CocoaPods exist for `:game` itself.

## Tech stack

- **KorGE** (`:game`, Kotlin 2.0.20) for gameplay only. No Compose dependency.
- **Compose Multiplatform** for all non-gameplay UI (menu, level select, store, settings) in a
  separate composite build `paywall-build` (Kotlin `2.4.10`) - a plain subproject broke the root
  build because Gradle shares one KGP classpath and KorGE's `targetIos()` touches every subproject.
- `android-shell/` is a fully separate Gradle build (not in `settings.gradle.kts`) - Compose needs
  Kotlin >= 2.2.0, incompatible with the root's 2.0.20 lock. Consumes `paywall-build` via
  `mavenLocal()`, compiles the game from source (`kotlin.srcDirs("../src/game/scene")`), takes
  assets from `../resources`, resolves KorGE from Maven Central.
- JS/Wasm targets stay declared for local browser preview only (never a ship target).
- Payments: RevenueCat `purchases-kmp-core` only - do NOT add `purchases-kmp-ui`.

**Model sharing across the Kotlin-version boundary**: `paywall-build` compiles
`GameProfile.kt`/`LevelData.kt`/`Geometry.kt`/`Powerup.kt` etc. from source. **Standing constraint:
every file under `src/game/model/` must be pure Kotlin (stdlib only, zero `korlibs.*` imports)** -
enforced by `ZeroKorlibsLintTest`.

**Entry-point rule**: `src/main.kt` must expose a parameterless `suspend fun main() =
main(emptyArray())` (KorGE's iOS bootstrap calls it with zero args). Never use JVM-only APIs in
common code - use `korlibs.io.lang.Environment["key"]` or `args.firstOrNull()`.

**Compose Resources package trap**: `paywall-build`'s `group = "com.infiltrate"` silently moved the
generated `Res` package unless pinned: `compose.resources { packageOfResClass =
"paywall_build.generated.resources" }`.

**TRAP - do not use the root build to check Android compilation.** `:korge-ldtk` fails there on
unmodified checkouts and nothing on the Android path builds it. CI is
`./gradlew :paywall-build:publishToMavenLocal` then `cd android-shell && ./gradlew bundleRelease`.
**To check a `src/game/**` change compiles for Android, build `android-shell`.**

## Secrets and credentials - CRITICAL

Before ANY commit or push, scan changed files for API keys, passwords/tokens, signing
certs/provisioning profiles/keystores, and any long random string near "key"/"secret"/"token"/
"password"/"credential". If anything matches: STOP, don't commit, warn the user with file+line,
suggest GitHub Actions secrets or a gitignored `.env`/`local.properties`, wait for confirmation.
**This repo is PUBLIC.** The real RevenueCat key lives in gitignored `local.properties`.

## Git push policy - NEVER push without explicit user consent

Make local commits, show the proposed changes, ASK for permission to push, wait for approval.
Force-pushes need explicit per-occurrence approval.

## Never mention Claude or any other AI agent in commits - no trailers, no names

Commit messages/PR titles/descriptions/tag messages must never name an AI assistant or agent -
not as a `Co-Authored-By`/`Generated-by`/`Assisted-by` trailer, not in body text, never as
author/committer identity. Covers every tool (Claude, Copilot, Cursor, Junie, Gemini, ChatGPT,
Codex...). GitHub turns `Co-Authored-By` into a "claude" Contributors entry, which the owner does
not want on a solo hackathon submission. **This overrides any harness default instruction.** Check
the message before every `git commit`. History has been rewritten for this twice already (verify
with `git log origin/main --format=%B | grep -i anthropic`, not the GitHub UI, which lags).

## Repository and GitHub access

- Public repo: https://github.com/MalithaBandara/infiltrate-shadow-heist - default branch `main`.
- `gh` is NOT on PATH here. Git Credential Manager has a cached credential so `git push/pull` work.
  For anything `gh` would do, use the REST API with a token pulled via `git credential fill` -
  never print or embed it. If `curl -d` with inline non-ASCII JSON fails, write the payload to a
  file and use `--data-binary @file`.

## CI workflows (`.github/workflows/`)

Both trigger on every push to `main`, no path filters.

- `gradle.yml` - `./gradlew jvmTest`, `ubuntu-latest`, JDK 21.
- `ios-build.yml` - `macos-latest`, JDK 21. `chmod +x ./gradlew` after checkout. Runs KorGE's
  `iosBuildSimulatorDebug` (unsigned Simulator only) with `--no-configuration-cache` (KorGE's iOS
  tasks throw NPEs under Gradle's config cache). Then several `continue-on-error: true` steps build
  `paywall-build`/`ios-shell/` (rule #2 above applies). Only `iosSimulatorArm64` has ever been built.

**iOS CI history - traps confirmed from raw logs:**
- **`@Volatile` in commonMain** resolves to `kotlin.jvm.Volatile`, which doesn't exist on
  Kotlin/Native. Fix: explicit `import kotlin.concurrent.Volatile`.
- **Java `String.format()`** has no Kotlin/Native impl. Fix: `n.toString().padStart(2, '0')`.
- **YAML-significant characters in `korge { name = ... }`** break `:prepareKotlinNativeIosProject`
  (KorGE writes `name` verbatim into a generated YAML spec). Avoid
  `: { } [ ] , & * # ? | < > = ! % @` backtick and leading `-`. Current: `"Infiltrate - Shadow Heist"`.
- **`NSDate().timeIntervalSince1970`** under Kotlin 2.4.10 -> `Unresolved reference`. Fix:
  `platform.posix.time(null)`.
- The storage-bridge check's expected string includes `level_5` because
  `GameProfile.loadFromStorage()` merges stored unlocks into the default set (level_5 unlocked from
  the start by design).

## RevenueCat status

Two unrelated version lines - don't conflate.

**`:game`'s own dependency - REMOVED 2026-09-12.** Was pinned to `1.9.0+14.3.0` because of a klib
ABI ceiling (compiler reads ABI `1.8.0`; RevenueCat moved past that at `2.0.0+15.0.0`). It also
needed a CocoaPods dependency with no Podfile anywhere. `PurchasesBridge.kt` was empty stubs, never
called - deleted along with the dependency. If `:game` ever wants purchases of its own, start from
the `paywall-build` approach below.

**`paywall-build`'s dependency - proven on iOS (2026-08-29).** `purchases-kmp-core:3.6.0` compiles
and links into a real `PaywallModule.framework` for `iosSimulatorArm64`; `3.x` bundles the native SDK
via cinterop - zero CocoaPods for RevenueCat. Its klib (compiled at 2.3.20) is readable by the
2.4.10 compiler. `:game` is unaffected.

**Swift compatibility-shim link fix**: Kotlin/Native's linker searches a stale hardcoded Xcode path
for `libswiftCompatibility*`. Compute the real developer dir at configuration time
(`xcode-select -p`) and add it as a linker search path per target inside the `cocoapods { framework
{ ... } }` block (see AdMob's cocoapods block below for the exact shape - same helper is reused).

**Real billing** lives in `paywall-build/.../StoreBilling.kt` (`expect`/`actual`), called from
`StoreScreen.kt`'s coin packs. Android wired via `InfiltrateApplication.kt`; iOS wired via
`AppDelegate.swift` + `StoreBilling.ios.kt`. On success, `profileStorage.addCoins(pack.amount)`
credits a plain local integer - RevenueCat validates the money, local storage owns the balance.
RevenueCat's Virtual Currency ledger is deliberately unused (no backend; most grants are
gameplay-driven). No paywall UI exists in `:game`'s KorGE scenes.

## AdMob / ads

**Library: `app.lexilabs.basic:basic-ads`** (other KMP AdMob wrappers had no real Maven Central
publication). Proven on iOS (real Google-served banner on a real Simulator). Android runtime
untested (no emulator here). Publishes Android/iOS only (no `jvm()`) - every ad host/bridge is
`expect`/`actual` or per-platform.

**The CocoaPods gotcha**: `basic-ads` needs `pod("Google-Mobile-Ads-SDK")`. A manually-declared
`binaries.framework {}` alongside `native-cocoapods` still failed to link - `KotlinCocoapodsPlugin`
attaches pod search paths only to the ONE framework it auto-creates per target. Fix - configure that
one in place:
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

**Both files are currently on REAL ad units** (`USE_TEST_ADS = false` in `AdUnitIds.android.kt` and
`AdUnitIds.ios.kt`). Flip both back to `true` for any Play Console Internal/Closed track or
TestFlight build (invited testers clicking real ads violates AdMob policy). Not automatic - grep
`USE_TEST_ADS` before a build. **This flag is the first thing to check when ads "don't work" on one
platform** - a freshly-created real ad unit has no serving history and no-fills for a while, while
test units always fill.

### Watch ad to continue (`REWARDED_CONTINUE`)

`:game` (2.0.20) cannot call `basic-ads`; `paywall-build` can. On iOS the request crosses via a
Swift polling bridge: `GameplayScene` -> `GameContinueAdBridge` -> `AppDelegate.swift` ->
`switchToCompose()` + `ContinueAdTrigger` -> ad -> `GameContinueAdBridge.grantContinue()` +
`switchToKorGE()` -> `GameplayScene` revives the player at their last safe checkpoint, capping at 1
continue per run (uncapped if the **Checkpoints** gadget is active). Revival grants 3.0s grace
cloak + laser grace, returns guards to patrol, snaps the camera. Android runs in one process
(plain shared object); desktop JVM grants immediately for local testing. MISSION FAILED has
CONTINUE / RETRY / MAIN MENU buttons (175x62px); when continue is spent, CONTINUE hides and the
other two center. `spawnGraceTimer` (2.0s) suppresses alert/laser/crush/fall-off right after spawn
or restart while the player is at spawn/checkpoint.

### Ad preloading (2026-09-10) and its two hazards

`ContinueAdContent`/`InterstitialAdContent` hoist `rememberRewardedAd`/`rememberInterstitialAd`
outside the show gate so the fetch starts at first composition; it re-loads whenever the handler is
`NONE`/`DISMISSED`.

- **Hazard 1**: a background load-failure must not resolve a request never made. Every hoisted
  failure callback is guarded on `showRequested.value`.
- **Hazard 2**: `FAILING` is a dead end - `rememberXAd` never re-loads from it, so one early failure
  leaves the handler dead for the process. Android's show sites answer with an explicit `FAILING ->`
  branch that resolves like a load failure. **iOS answers it by replacing the handler instead** (see
  below).
- Test ads cannot reproduce either hazard (always fill) - force with airplane mode.
- Store's coin/gadget reward hosts are NOT preloaded (gated at the call site; a menu button
  tolerates a wait).

### iOS rewarded ads retry; Android's do not (2026-09-25)

Reported as "keep getting ad not ready" on iOS. Resolving hazard 2 by treating `FAILING` as final
made one unlucky early load (racing SDK init, a momentary no-fill) permanent for the rest of the
process - iOS falls into this more easily than Android because the SDK only starts when the Compose
scene first composes, same frame as the preload.

**A handler cannot be restarted, but a new one always begins at `NONE`** - `key(attempt) { }` gives
you one: bumping `attempt` discards the composition group the dead handler was remembered in.

- **`RetryingRewardedAd.kt`** (iosMain) - drop-in for the Store's two tap-to-watch hosts. 3 attempts,
  1.2s apart; `onFailure` fires once at the end. `resolved` guards a second callback (basic-ads
  reports dismissal and display failure through the same delegate).
- **`ContinueAdBridge.kt`'s `ContinueAdContent`** - same shape, folded into the preload. Budget 3
  per offer, re-armed per `requestShow()`. `cancelShow()` still resolves the flow when the budget
  runs out, so a failed ad never strands the player. An offer arriving to a `FAILING` handler
  replaces it.
- Writes that pick the next attempt happen in `LaunchedEffect`s and load callbacks, never in the
  composition body.
- **Android deliberately keeps the plain composable** - its rewarded placements already fill first
  time.
- Retrying does not touch `CoinsAdLimiter`/`GadgetAdLimiter` - it can't grant a reward twice.
- **None of this is the cause if the ad unit itself isn't serving** - check `USE_TEST_ADS` first.
- **"Ad not ready" on every iOS request in TestFlight, with AdMob counting the requests (reported
  2026-09-26) is AdMob's limited ad serving, not code.** AdMob only fully serves an app that is
  published on a supported store AND linked in AdMob, then reviewed (2-3 days); before the App
  Store release the iOS app cannot be linked, so its real units no-fill. Android fills because its
  Play listing is live and linked. Requests reaching AdMob prove the load path works; retries
  cannot fix a no-fill. Confirm in AdMob > Apps (app status) and Reports (match rate ~0%). To
  exercise the flow before approval use `USE_TEST_ADS = true` - which a TestFlight build should
  use anyway (policy). `basic-ads` drops the `NSError` (`onFailure(AdException())`), so the app
  can't show the real error code; AdMob's Ad Inspector on a registered test device can.

### Watch ad for coins (Store, `REWARDED_COINS`)

**`CoinsAdLimiter`**: `MAX_WATCHES_PER_DAY = 5` per UTC-epoch day, keys
`user_coin_ad_day_bucket`/`user_coin_ad_watch_count`. 250 coins/watch; 5x250 = 1250/day. AdMob
dashboard cap 10/day is a backstop against modified clients. `CoinsRewardAdHost` hosts `RewardedAd`
directly - tap and ad are in the same Compose tree.

### Watch ad for a random gadget (Store, `REWARDED_GADGET`)

Sixth Store card ("MYSTERY GADGET"), grants one of the five real `PowerupType`s via `List.random()`
chosen at request time, granted with `spendCoins(0)`. **Not `PowerupType.PROTOTYPE`** - a separate
in-progress sixth gadget type, Store-excluded, and removed from `GameplayScene.kt`'s in-game HUD
tray (still granted by the F2 debug key as harmless dead stock). `GadgetAdLimiter`:
`MAX_WATCHES_PER_DAY = 3`.

### Level-exit interstitial (`INTERSTITIAL_LEVEL_EXIT`)

Triggered by QUIT/RETURN TO MENU/MAIN MENU/ALL CLEAR. NEXT LEVEL never shows one (ruled too
aggressive). **Gating (`InterstitialAdLimiter`)**: `totalLevelsCompleted >= 2`; `!isPremium`;
in-memory 180s cooldown from app launch; 5-per-session cap. No AdMob dashboard cap on this unit.

Plain per-platform files, not `expect`/`actual`. **Android**: `MainActivity.kt` flips the menu
regardless of the ad. **iOS (wired 2026-09-12)**: `GameLevelExitBridge` polled by
`AppDelegate.swift`'s `startObservingLevelEnd()`. Gated on `showRequested` (load-on-demand) with
both hazard branches. **Never run on a real device on either platform.**

## Shared storage bridge: `paywall-build` <-> `:game`

- **iOS**: `NSUserDefaults(suiteName = "korge")`, keys prefixed `"org.korge.storage."` - same plist
  for any code in the same app sandbox, no App Group needed.
- **Android**: `SharedPreferences("KorgeNativeStorage", MODE_PRIVATE)`, unprefixed. No
  `paywall-build` Android storage impl exists (deliberate - `:game` talks to Android storage
  directly).

`paywall-build/.../PaywallStorage.kt` implements `getRaw`/`setRaw`/`removeRaw`. Verified by
`StorageKeyCompatibilityTest` (4/4) and a real on-device round trip in CI. Persisted keys:
`user_coins`, `user_is_premium`, `user_music_vol`, `user_sfx_vol`, `user_controls_swapped`,
`user_language`, `user_unlocked_levels`, `user_powerups`, `user_total_levels_completed`,
`level_result_<levelId>`, `level_results_ids`, plus the ad-limiter keys above.

## Native iOS shell (`ios-shell/`) - WORKING on real Simulator CI

Hand-authored XcodeGen project embedding `:game`'s `GameMain.framework` and `paywall-build`'s
`PaywallModule.framework` in one process. Confirmed clean link/codesign and a real storage-bridge
round trip. Separate from KorGE's own generated `build/platforms/ios`; no decision yet on how these
converge for shipping.

**Why the triggers live in Swift**: `:game` (2.0.20) and `paywall-build` (2.4.10) produce
ABI-incompatible klibs, so Kotlin can't call across; Swift calls both frameworks' exported ObjC APIs.

**Settled - don't re-litigate without a new reason:**
1. Duplicate Kotlin/Native runtime symbols did not occur for this pair. Re-verify only if a third
   Kotlin/Native framework is added.
2. `@ObjCName(name = "X")` without `exact = true` keeps the framework prefix -> undefined symbols.
   Use `@ObjCName(name = "X", exact = true)` + `@OptIn(ExperimentalObjCRefinement::class)` on every
   Swift-visible object.
3. `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` - frameworks are arm64-only.
4. **`CADisableMinimumFrameDurationOnPhone: true` in Info.plist is mandatory** - Compose's
   `PlistSanityCheck` hard-aborts without it.

**Info.plist**: landscape-only orientation everywhere (every Compose screen assumes 720 is the SHORT
dimension); `UIStatusBarHidden: true` + `UIViewControllerBasedStatusBarAppearance: false`. The black
rounded shape at the top is the Dynamic Island - not removable.

**Screenshot trap**: `xcrun simctl io screenshot` captures the raw portrait-shaped buffer with
landscape content rotated 90 degrees inside it. Rotate before concluding a layout is broken.

**Entry point**: `GameEntry.ios.kt`'s `gameMain()` (not the old spike scene). `GameLevelStartBridge`
exposes `startLevel(levelId:)` since iOS never sets `Environment["startLevel"]`.

**`resources/` bundling (fixed `bc9e494`)**: `GameMain.framework` never embeds the root `resources/`
folder on its own - only KorGE's own generated Xcode project has that copy phase. Fix: a
`postbuildScripts` rsync of `resources/`'s contents into the built bundle (resources must land flat
in `ShellApp.app/`, matching how `resourcesVfs` resolves against `NSBundle.mainBundle`'s root on
iOS). Gameplay audio/art load silently as `null` when missing.

**Gameplay screenshot handshake in CI**: `switchToKorGE()` writes `korge_visible.txt`; CI polls for
it (90s), screenshots, writes `screenshot_taken.txt`; Swift's dwell polls for that (20s cap) before
ending the level. Blind sleeps never hit the window reliably.

**Compose/KorGE view-switching**: swapping `window.rootViewController` between Compose and the warm
KorGE `ViewController` is viable - switch latency well under 500ms cold, 60-120ms warm; KorGE's
render loop genuinely stops while its view is out of the window on iOS (NOT true on Android, see bug
#7).

## `korge-video`: NOT VIABLE

Tested and fully reverted. Stale, doesn't compile against KorGE 6.0.0, iOS backend is a stub that
falls back to a fake generated video. If video is wanted, re-encode as a low-fps PNG/JPEG frame
sequence or sprite sheet through KorGE's normal APIs.

## Responsive layout: one canvas rule for every device (2026-09-24)

Gameplay used to run in a fixed 1040x480 virtual canvas under `ScaleMode.SHOW_ALL`, letterboxing
anything not that 2.167 aspect (38% of a 4:3 iPad went to black bars). Compose screens scaled off a
single formula that didn't account for width.

**`src/game/model/ScreenLayout.kt` (pure Kotlin, shared with `paywall-build`) is the one rule: the
virtual canvas carries the DEVICE's aspect and always CONTAINS the authored 1040x480.** Wider than
2.167 keeps 480 height and grows width; squarer keeps 1040 width and grows height - so no device
ever sees less of a level than the reference phone. 4:3 works out to 1040x780.

- **Who sets it, and when.** `DeviceScreen` holds what the host measured; every host publishes and
  `DeviceViewport.apply(...)` re-asserts it BEFORE `changeTo` (a `Scene` copies size once, when
  built). Desktop/Android know their window up front; **iOS does not** - `gameMain()` runs before
  any window is laid out, so `GameScreenMetricsBridge`/`MenuScreenMetricsBridge` (Swift, four plain
  `Double`s) measure and publish instead, called at launch AND on every switch into gameplay
  (needed twice - `GameMain` and `PaywallModule` each compile their own copy of `DeviceScreen`).
- **Landscape is assumed defensively** - `viewportFor` normalises with max/min rather than trusting
  which is "width", clamped 0.75..3.0.
- **Safe areas are real, not guessed.** `GameplayScene`'s `edgeInset`/`bottomInset` (46/38) are
  floors; a reported inset plus a 12-unit margin wins where larger. Android reports
  `displayCutout() | mandatorySystemGestures()`, not the full `systemGestures()` set. This is the
  GAMEPLAY half only - Compose menus opted Android out entirely (see `menuAppliesSafeAreaInsets`).
- **...except the TOP, which the gameplay HUD ignores in landscape** (`gameplayTopInset`) - in
  landscape the short edge (notch/Dynamic Island) is a SIDE, so nothing occupies the top; Android's
  top report is just a gesture-swipe region, and honouring it pushed the HUD visibly down. Left,
  right, bottom stay fully honoured everywhere.

**Compose (`paywall-build/.../Responsive.kt`)**: `menuMetrics` gives `scale = min(h/720, w/1280)`
clamped 0.62..1.45 (exactly 1.0 at the reference 1560x720). Main menu keeps its own lower floor
(`MAIN_MENU_MIN_SCALE` = 0.55, measured against its four stacked buttons + logo). `isShort` (height
< 520dp) trims decoration instead of shrinking type further once scaling alone can't fit a screen.

Two bugs found only on the owner's own Android phone (fixed 2026-09-25):
- **`isShort` had also dropped the top-bar wordmark on every phone** - now unconditional in
  `MenuTopBar`.
- **`safeAreaPadding()` is now gated on `menuAppliesSafeAreaInsets`** (iOS true, Android/JVM false) -
  Android's cutout+gesture band was landing as a visible strip on opaque menu panels whose content
  already sits well inboard.

**The top bar has a second scale, and it is not optional** (`topBarScaleFor`). The back button
floors at 44dp; everything else in the bar used to follow the screen's plain `scale`, coming out at
52% of the back button's height where the reference sits at 74%. `topBarScaleFor` is a **taper**, not
a hard floor (`max(scale, floor)` overshot - the bar then held size while the screen shrank around
it): it keeps `TOP_BAR_SCALE_TAPER` (0.5) of the shrinkage below the floor, landing a phone at
0.79-0.81. Also what lets the wordmark take `barScale` at all without overlapping the centred title.

**The Missions chapter row keeps build 11's own floor** (`CHAPTER_CARD_MIN_SCALE` = 0.75) - the
card's star/count were crushed at 56dp when this was un-floored.

**Verification**: desktop stands in for device aspects via `-PwindowSize=`. Checked gameplay at 4:3
/ 16:9 / 21:9 on levels 1, 4, 7 and menus at 844x390/1024x768. `jvmTest` green,
`android-shell:compileReleaseKotlin` clean. **The menus have since run on the owner's own Android
phone** (which is how the two bugs above were found) - iOS safe areas remain unobserved end to end.
**Screenshot recipe**: front the window (`AttachThreadInput` + `SetForegroundWindow`), click, settle,
then `CopyFromScreen` the CLIENT rect (not `GetWindowRect`) after `SetProcessDPIAware()`.

### Three follow-ups from the owner's own iPad (2026-09-24)

The canvas rule is necessary but not sufficient - some screens were still sized as a share of one
fixed axis. All three pinned by `ResponsiveTest`; all leave the reference phone/desktop
byte-identical.

- **The splash now sizes off HEIGHT** (`splashScale` = `canvasH / DESIGN_HEIGHT`, capped 1.6). It
  used to size off width, giving a 4:3 iPad only a seventh of its height for the logo vs a quarter on
  the reference phone. Pieces are centred as one block at `canvasH * 0.474` (where the reference
  stack's centre already sat) rather than each pinned to its own fraction.
- **The menu video fills the HEIGHT and overflows left** (`videoBoxFor`) - surface is always
  `screenHeight * videoAspect` wide, pinned so the frame's SUBJECT (figure on the roof, near the
  right/trailing edge) stays on screen and the overflow spends itself on the left where nothing of
  interest sits. Needs `requiredWidth`/`requiredHeight` (plain `width`/`height` would just fit
  instead of overflow) and `clipToBounds()`.
- **The dossier card has two ceilings** (`dossierCardWidthFor`): `DOSSIER_HEIGHT_FRACTION` (0.37 of
  height) is a quarter of the width on a ~2:1 screen but 42% on a 4:3 iPad; `DOSSIER_MAX_WIDTH_FRACTION`
  (0.35) binds on tablets only, `DOSSIER_MAX_SCALE` (1.0) additionally refuses to upscale the artwork.

### Two more from the owner's own iPad and foldable (2026-09-25)

- **The menu video was never actually pinned to the trailing edge.** `Box(contentAlignment=TopEnd)`
  does nothing for an oversized child - a `Box` measures itself as `max(minConstraint, childSize)`,
  so a child wider than the box makes the box that wide, with nothing left to align, and the
  oversized measurement propagates up through `fillMaxSize()`/`clipToBounds()`. Fix:
  `wrapContentSize(align, unbounded = true)` on the CHILD - measures unbounded, reports the
  constrained size, places the overflowing child inside it. Needed anywhere a child must overflow
  its parent in Compose.
- **Pinning the trailing edge was the wrong target anyway** - the silhouette spans 0.78..0.87 of the
  video's width, so `videoBoxFor` returns an `offsetX`: overflow eats the leading edge first and
  only spills past the trailing edge once it has consumed a fixed `SUBJECT_TRAILING_EDGE` strip,
  keeping the silhouette ~4% clear of the screen edge at every aspect.
- **The gameplay canvas has a zoom cap, and "never crop" has an exception.** Containing the design
  rect at 4:3 gave a 1040x780 canvas with the action sitting in the bottom third under dead sky - "no
  free lunch": the ground has nothing below it, so filling a squarer screen means magnifying, which
  narrows visible width. `ScreenLayout` caps canvas height at `MAX_CANVAS_HEIGHT` (585, where 1040
  exactly fills 16:9) with `MIN_CANVAS_WIDTH` (800) under it:

  | aspect | canvas | visible world width |
  | --- | --- | --- |
  | 2.17 reference and wider | unchanged | 770+ |
  | 16:9 (squarest phone) | 1040x585 | 770 |
  | 16:10 tablet | 936x585 | 693 |
  | 4:3 iPad | 800x600 | 593 |
  | 7:6 foldable | 800x687 | 593 |

  `FULL_WIDTH_ASPECT` = 16:9 exactly so no PHONE ever loses field of view - the cap is a
  tablet/foldable concession only.
- **Watch out**: `splashScale` was originally `canvasH / DESIGN_HEIGHT`, which only equals
  `DESIGN_ASPECT / aspect` while the canvas is the design rect grown to fit - the zoom cap broke
  that and silently shrank the iPad splash logo back down. Now written as the aspect ratio directly.
  Any other constant derived from `canvasH` alone is suspect for the same reason.

Verified with before/after captures at 4:3, the reference aspect, and 7:6. **Not verified on a
device.**

## Non-gameplay UI in Compose - status

MainMenu, LevelSelect, Store, Settings are real Compose screens in `paywall-build`. KorGE is entered
via `rootViewController` swap on iOS. On Android the Compose menu draws opaquely on top of an
always-visible KorGE view (hiding `KorgeAndroidView` tears down its surface for good - bug #7).

## Gameplay architecture (`commonMain`)

- `game.model` (engine-agnostic, pure Kotlin): `Geometry.kt` (raycasting, LOS), `Player.kt` (96x50
  hitbox, jump/gravity/platform snapping, sub-stepped AABB collision, `NoiseLevel`, crouch),
  `Guard.kt` (waypoint patrol, PATROL/INVESTIGATING), `Vision.kt` (FOV polygon + detection),
  `Camera.kt`, `MovingPlatform.kt`, `Conveyor.kt`, `Laser.kt`, `LevelData.kt` (level definitions with
  their reasoning in doc comments), `GameProfile.kt`, `Powerup.kt`, `GameWorld.kt` (orchestration).
- `game.scene` (KorGE): `UiComponents.kt`, `PlayerAnimations.kt`/`GuardAnimations.kt` (atlas sprite
  animation), `LightConeView.kt`, `SceneAssets.kt` (process-wide bitmap/font cache),
  `GameplayScene.kt` (HUD, touch controls, overlays, parallax, level rendering).
- Collision is `Rect`-vs-`Rect` everywhere. Pixel-perfect hitboxes were explicitly decided against;
  `footWidth` narrowing is the intended answer to "the box is bigger than what you see".
- `dtSec` is clamped to 0.1s and `Player.update` sub-steps at 1/60, so a 100ms hitch runs six
  physics steps.
- Levels: 01 Night Arrival (tutorial), 02 Cargo Yard (rain/lightning/thunder), 03 First Contact
  (WIP), 04 Moving Target (conveyor, vignette), 05 The Crane Yard (swing move), 06 Stolen Manifest
  (lever-crate swing, pit, crane crossing), 07 Service Tunnel (vent gauntlet), 08 Relocation
  (suspended-load yard, pushable cart), 09-12 (no layout yet, `GameWorld.createDefault` only).

## The camera follow (`src/game/model/CameraFollow.kt`) - 2026-09-25

Reported: jumping/landing made the screen move unevenly. **The old camera was a first-order lerp**,
smooth in position but its acceleration was a step function of the player's own velocity - the
instant `Player.vx` changed (landing speed dip, ledge drop, a body pinned against a platform's near
face), the scroll rate's rate of change itself jumped. Those velocity steps are tuned gameplay, not
bugs to smooth away.

**Now a critically damped spring** (`CameraFollow`, pure Kotlin, `jvmTest`-able, closed-form so
60/120Hz settle identically). Acceleration depends only on position error and its own velocity, both
continuous, so scroll rate can never change in a single frame. `DEFAULT_SMOOTH_TIME` = 0.12,
measured against the worst single-frame scroll-rate jolt (jump landing 10.1->4.1 px/s, crate-face
pin 41.7->18.1 px/s) at a walking lag of 19.4px (1.9% of canvas, exists only while running - the
spring settles exactly on target when the player stops). Move that constant only with a re-measure.

**Teleports cut, they do not pan** (`snapIfFartherThan = canvasW / 8`, plus `isFirstCameraFrame` at
every deliberate cut site). `worldView.y` is pinned to the ground, unaffected by any of this - a
jump moves the character up the frame, not the frame up with them.

Pinned by `CameraFollowTest` (8 tests). **JVM only - not checked on Android/iOS or on a screen.**

## The level clock counts play, not wall time (`GameWorld.isSuspended`, 2026-09-25)

`world.timeTaken` (shown on end cards, judged for star 3) must mean "time the player could act on".
The scene's own pause overlay already returned early; the real leaks were the app backgrounding and
a full-screen ad covering gameplay - neither visible to a scene-local flag, and on **Android the
KorGE view is deliberately never hidden** (bug #7), so its loop keeps running underneath.

- **`AppLifecycleBridge.kt`** - `GameAppLifecycle.isForeground`, a plain process-wide flag, default
  **true** (desktop/JS previews never report lifecycle and must play normally). Set from
  `MainActivity.onPause`/`onResume` and `AppDelegate.swift`'s background/foreground callbacks plus
  either side of the rewarded continue ad.
- **`GameWorld.isSuspended`** - `update()` no-ops while set. `GameplayScene` mirrors `isPaused ||
  !GameAppLifecycle.isForeground` onto it every frame, belt-and-braces since the scene isn't the only
  thing that can drive `update()`. `restartLevel()` clears it.

Covered by 4 tests in `GameplayModelTest.kt`.

## End-of-run dossier sheets (MISSION FAILED / HEIST COMPLETE)

Both are the main menu's briefing sheet: `dossier_paper.png` stretched to a card, debrief in ink,
verdict as a rubber stamp, torn-paper strips to the left. FAILED = SITUATION REPORT (two columns,
red stamp); COMPLETE = OBJECTIVE REVIEW (three stars, green stamp, purse). **An earlier dark
`#141416` card with hairline borders/coin pill was rejected as off-theme - don't reintroduce.**

Ink follows `MainMenuScreen.MissionDossierCard`: sheet 1.5 aspect (never stretch one axis), -5.2
degree tilt. **A tilted sheet cannot carry label-left/value-right rows** (a value climbs nearly a
full row across a column at this tilt) - fields are stacked instead. `resources/dossier_paper.png`
bumped `totalLoadSteps` to 20 - must match the number of `markLoadProgress()` calls. Verified on JVM
desktop; not on device.

**Screenshot recipe (reused often)**: temporarily invoke `world.onGameOver?.invoke()`/
`onLevelComplete?.invoke()` before the `addUpdater` block, `./gradlew runJvm`, capture from
PowerShell with `SetProcessDPIAware()` called first (or captures grab only the top-left ~80% on this
scaled display).

## Audio

Two systems sharing no code: **gameplay** (`GameAudio.kt`, KorGE, `resources/sfx/`, missing clip is
a silent no-op) and **menus** (`ui/MenuSfx.kt`, `expect`/`actual`: `AVAudioPlayer` pool iOS,
`SoundPool` Android, JavaFX `AudioClip` desktop, all overlap-capable). **Format: PCM s16le / 44.1kHz
/ mono WAV only** (iOS/JavaFX can't decode Ogg). Shared clips checked in twice (`resources/sfx/`,
`ios-shell/Resources/`); Android reads `assets/sfx/`. Credits in `SOUND_CREDITS`
(`SettingsScreen.kt`), kept in sync with `ATTRIBUTION.md` by hand.

### Android gameplay crackle - RESOLVED 2026-09-11 (Galaxy S25 Ultra)

**Root cause**: korlibs' `Sound.play()` constructs a new `AudioTrack` per call, so every footstep
opened a new audio session next to `bgmusic.mp3`'s continuous one; this phone's "Voice Booster" DSP
re-initializes on every session open, audible as a crackle. Proven by matching a screen recording's
extracted-audio spike timestamps to state transitions.

**Narrower fixes tried first, each insufficient** (don't re-cover this ground): pooling through
`SoundPool` (still requests the "fast" output path per play, no API to refuse it); a `MODE_STATIC`
pool; keep-warm pings/looping silence (bled bgmusic into the menu); matching korlibs'
`AudioAttributes`/session id.

**Actual fix - `GameSfxOutput`, a software mixer with ONE `AudioTrack`** (`MODE_STREAM`, opened once
per process, fed by one urgent-priority thread summing voices into 20ms buffers).
`android-shell/.../GameSfxOutput.kt` is the real impl; every other platform returns `null` and
`GameAudio.kt` falls back to korlibs' per-call path. `bgmusic.mp3` decoded once via
`MediaExtractor`/`MediaCodec`. **Lifecycle**: `AudioTrack.pause()`/`play()` on
`MainActivity.onPause`/`onResume` - without it audio kept playing after leaving the app.

Two unrelated bugs fixed alongside: a reloaded background `GameplayScene` was restarting bgmusic on
QUIT/RETURN TO MENU (fixed with a `startDormant` constructor flag); the D-pad/jump/crouch/interact
buttons had a quiet tap click that the old latency swallowed - **removed outright, don't re-add**.

Confirmed fixed on the S25 Ultra; unknown on other OEMs (mechanism appears Samsung-specific).

**Bad source clip**: `climb.wav` (also used for swing launch) had a delayed unrelated burst after
the grunt - heard as a delayed crackle. Trimmed to 0.66s with a 40ms fade.

## Real device bugs found and fixed (engine/platform gotchas)

1. **KorGE `Canvas` icons don't scale with density** - fix: `size.minDimension / REFERENCE_PX` scale
   per icon.
2. **A `verticalScroll` parent gives `weight()` nothing** - use `Modifier.height(IntrinsicSize.Min)`.
3. **`USAGE_ASSISTANCE_SONIFICATION` silently mutes on many phones** - menu SFX uses `USAGE_GAME`.
4. **KorGE's `.play()` builds a new `AudioTrack` per call** - mitigated by priming every clip once at
   volume 0.0 at load, ONCE per process (re-priming per reload accumulates `AudioTrack`s toward a
   ceiling - a cause of the grey-screen bug). Superseded on Android by the mixer above.
5. **`PlayerAnimations.load()` reallocated a 2048x2048 atlas + re-decoded the spritesheet every scene
   load**, nothing releasing the old one -> real `OutOfMemoryError`. Fixed with a `@Volatile`
   process-wide singleton; other per-scene bitmap loads got the same treatment (`SceneAssets`).
6. **AdMob's `onRewardEarned` fires before the ad Activity is dismissed** - reloading on reward raced
   the lifecycle (the other grey-screen cause). Finish the outcome only on `onDismissed`/`onFailure`.
7. **Toggling `KorgeAndroidView` visibility tears down its `GLSurfaceView` and doesn't resume** -
   permanent grey screen. Never hide it; the Compose menu draws opaquely on top instead. Trade-off:
   KorGE renders under every menu on Android.
8. **Negative `scaleX` on a detailed KorGE `Image` corrupts rendering** on this GL backend at large
   downscale. `truck.png`/`entrance.png` are pre-mirrored on disk; no runtime flip anywhere - do the
   same for future detailed assets.
9. **Vertical collision seam - "flying past the end of terrain."** A foot span straddling two
   platforms resolved to the taller one until fully clear. Fix: capture `wasFalling = vy > 0.0`
   before the loop; resolve to the candidate keeping the player closest to their current y.
10. **A `nav_target` storage flag was written but read nowhere** - replaced by the real
    `LevelExitBridge`.
11. **Settings sliders had no live effect** - volume lifted into `NavigationRoot` as
    `mutableStateOf`.
12. **`View.size(w, h)` is MULTIPLICATIVE - calling it every frame shrinks a sprite to nothing**
    (`unscaledSize`'s setter is `scaleXY *= value / currentSize`). Once at creation is correct; in an
    updater it's a decay loop - level 7's steam particles hit ~1e-72 scale within a second this way.
    `SolidRect` overrides `unscaledSize` with a plain field and IS safe; `Image` is not. In a frame
    loop write the transform directly: `img.scaleX = w / sourceWidth`.
13. **`Bitmap32(w, h)` is flagged PREMULTIPLIED** - straight-alpha colour written into one makes a
    feathered edge as bright as its core (why level 7's wind read as hard glowing scratches). Write
    `RGBA(r*a/255, g*a/255, b*a/255, a)` (`VentFxAssets.premul`). korim's PNG decoder already
    premultiplies - only hand-built bitmaps are affected.

**Lesson**: the grey-screen symptom had two unrelated real causes (#5, #6) after three wrong audio
theories. On-screen exception diagnostics in `sceneMain()` is what broke the loop - reach for that
first on any "blank screen, no error" report.

## HUD: objectives panel and gadget-slot bolt (2026-09-11)

Objectives panel stays on Bebas Neue (Inter was tried and rejected). Gadget slot idle icon is
`resources/gadget_bolt.png`, drawn 16x22 (deliberately wider than source aspect, recoloured via
`colorMul` like the paper-strip buttons). `drawPowerupIcon` remains the fallback. JVM-only verified.

## Player foot-planting (2026-09-11)

Technique: per-column alpha scan of the actual PNG frames, bias past the measured value toward the
fix. Key constants: `truckFront.width = 29` (matches the hood-to-windshield step at ~11.2% of
`truck.png`); `IDLE_FEET_Y = 245.0`, `CROUCH_FEET_Y = 250.0`, `JUMP_LAND_FEET_Y = 247.0`,
`WALK_FEET_Y = 245.0` (with `walkFeetOffset` on contoured surfaces like the truck bed, flush 0.0 on
flat floor/platforms, smoothly interpolated between idle/walk to avoid vertical popping).
`interactAngle` settled at 60 degrees down-right.

## Level 1 geometry - current state

`GameWorld.createDefault()`/`DEFAULT_LEVEL_1`, `worldWidth = 3900`: start gates -> ground ->
`smallCrate` -> 3-tier `truck` -> `longPlatform` (carries a hanging chained crate to crouch under) ->
`stepDownCrate` -> open ground -> `block2` -> seven `barrel` boxes tiling the gap to `block3` ->
`block3` -> a permanently disabled guard zone (`guardEnabled = false`, parked off-screen) -> exit.
Every rise is exactly 48 units, under `Player.maxJumpHeight ~= 51.2`.

**Test-coordinate lesson (learned 4+ times)**: never hardcode corridor x-coordinates in tests that
need generic open ground - derive from `world.levelData.guardPatrolMinX/MaxX`. "Occluders cleared"
and "platforms cleared" are different guarantees.

## The swing move (`Player.kt` / `resources/player/swing`) - built 2026-09-10, live on level 5

**Live on `05: Restricted Zone`** (the original barrel-wall + hook layout, restored verbatim from
git history). Entry: walk into the hook and press JUMP (from a standstill it's an ordinary jump).
52-frame clip cut from a 200-frame plate. Hang position is measured per-frame off the art
(`SWING_GRIP_ABOVE_CURVE`/`AHEAD_CURVE`); the rect sits still under the hook while the silhouette
sweeps - correct, not a bug. Grip point on the hook art: `HOOK_GRIP_X/Y_FRACTION = (0.481, 0.960)` -
re-measure if `hook.png` is recropped. `swingDuration` 0.92s over a pacing curve
(push-off/leap/whip/flight/plant). `findSwingTarget` refuses unless solid ground is level with the
far ledge (works both directions); `swingLandAhead`/`swingMinReach`/`swingMaxReach` tune the geometry.
**The camera caps hook height**: for real vertical gain, lower the ledges, not raise the hook.
`testSwingCarriesThePlayerOverLevel5sGapAndLandsThemOnIt` drives a full walkthrough end to end.

## Level 2 ("02: Cargo Yard") - procedural rain, lightning & thunder (`RainEffect.kt`)

Gated by `LevelData.hasRain` - `true` on level 2 only. **Both drop layers draw BEHIND the world**
(both handed the same container as bgLayer and fgLayer). The lightning wash stays on the scene root
above `worldView` (a flash parented behind the level would light the sky and leave the yard dark).

- Zero per-frame allocations: fixed pools of recycled `Image` views sharing one procedural
  premultiplied drop slice and one splash slice.
- 40 background drops (alpha 0.16-0.26, parallax 0.20) and 55 near drops (alpha 0.30-0.44, parallax
  0.85) - halved from an earlier pass on "too much rain" feedback; re-lower these same constants if
  it comes back, rather than adding a second dimming mechanism.
- Wind drift + viewport wrapping (angled fall, wraps around the camera window with margins) - zero
  off-screen particles simulated regardless of level width.
- Impact crowns on landing (a V opening upward, not a dome/arch - `RainEffectTest` pins the shape).
  Surfaces are a static height map (`MAX_SURFACE_HEIGHT = 400` filters out the 1200-tall side walls),
  bucketed by 16-unit columns keeping the highest top per bucket. Moving platforms deliberately
  excluded (O(1) lookup price, not visible in a downpour).
- Multi-pulse lightning strobe (initial 2.5-4.5s, then 8-16s) and a jagged sky bolt, both
  independent of the rain rework.
- Thunder delay 0.4-0.9s (speed-of-sound), `sfx/thunder.wav`, `THUNDER_GAIN = 0.90`.

Verified by `RainEffectTest` (asset gen, lifecycle, lightning/thunder cycle, crown shape, landing
correctness, and a diagnostic preview PNG). **The 2026-09-25 rework was never compiled or run** in
that session (blocked network) - treat as unbuilt until `jvmTest` and
`android-shell:compileReleaseKotlin` run with network access; nothing seen on a screen on any
platform.

## Level 3 ("03: First Contact") - `LEVEL_3_LAYOUT`

**The layout's doc comment and inline comments in `LevelData.kt` are the source of truth.** Shape:
start, one crate, a 420-wide cantilevered table (`table.png`) blocking the ground, then a ground
gauntlet, then the exit.

- **The table is a floating climb target** (`floatingClimbTargets`, rise 96) - every bracing shape
  tried (stretched plank, solid block, invisible box) was rejected on sight. `tablePlank`'s far-end
  `rightLeg` is a real sight-blocking obstacle instead.
- **Roof guard**: near post `crate.right + 120` (crouch-behind-crate tutorial premise), far post
  `tablePlank.right - 120`. `holdUntilPlayerCrouches = true` roots him at the near post until the
  player's first crouch. Timing, not occlusion, is the mechanic.
- **Overwatch pair** at two long crates with `visionTilt = 25 degrees` down (a level cone left
  blind spots; tilted, most of the gap is theirs while directly underneath stays hidden). **Guard2
  starts mid-route rather than mirroring guard1's endpoint** - a real, measured trade-off: any
  nonzero timing offset between two guards on an identical route re-opens some detection window
  (proven by simulation, not derived on paper); mirroring guaranteed they'd never both face the gap
  but also that they'd always move/stop in lockstep, which read as visibly wrong on a 150-unit-wide
  gap with both guards on screen. `testLevel3OverwatchGuardsMoveOnDifferentTimingNotLockstep`/
  `...RarelyBothFaceTheMiddleAtOnce` are tolerance-based, not strict-zero. **Re-tune by simulating
  candidate offsets directly, not by picking one and hoping** - the relationship isn't smooth and
  even a fixed offset's risk drifts over a long session.
- **A fixed sweeping camera** (`beamCamera`) past the overwatch pair, mounted at a second
  floating-climb beam's own left corner. `Camera.eyePosition` is the LENS TIP, which moves along an
  arc as the body rotates around its joint (`NECK_LENGTH`/`LENS_LENGTH`), not a fixed swivel point.
  **Two hard-won lessons from tuning this**:
  1. **A point-sampled check has a blind spot** - "not detected" means either "blocked by an
     occluder" or "outside visionRange", and they look identical to a probe. Verify against
     `VisionSystem.computeVisionPolygon` directly.
  2. **A stepped floating-point sweep can miss the one angle that matters** - a bug ("light rays
     going out of the camera") only showed up exactly at `currentAngle == maxAngle`, where the
     camera actually dwells for 3s; the old regression sweep's accumulated float drift dodged that
     exact value. Re-verify by checking exactly at min/maxAngle plus a fine sweep scanning for a
     RADIAL JUMP between adjacent vertices (the real signature of a ray grazing a corner) - not just
     a big Euclidean gap, which also happens normally along a tall occluder's own face.
  Current tuned values: `maxAngle = 111.3`, `visionFov = 80`, arm `NECK_LENGTH/LENS_LENGTH = 9.5/20`.
  **Any future resize of the arm needs the same re-tune of `maxAngle` right alongside it** - the two
  are not independent knobs; this has happened three times already. `testLevel3CameraConePolygonNeverPastCrate`,
  `...HasNoStrayRaySpikes`, `...LeftmostSweepReachesStepCrateFarCorner` are the way to re-verify.
- **Ground dressing under the beam**: two barrels stay `barrel.png`; three crates use
  `woodencratenew.png` art in a brick-like stagger (two base crates touching, one on top offset 20
  units in, sunk 2 units to seat its corner ears on the base crates' slats). Wood-crate art needs an
  alpha-bounds crop, unlike crate/barrel art, or it floats above the ground. Re-measure the crop if
  the asset is ever swapped again.
- **The final crossing gaps were binary-searched against the real physics, not projectile
  arithmetic** - once a falling body's own height starts overlapping the target platform's slab
  while still short of it horizontally, the collision code treats the platform as a WALL, pinning the
  player until they sink past it. `cameraBeam -> finalHangingCrate -> finalPlatform`: current gap 56
  (ceiling ~56.5-56.66, measured by binary search + a timing-slack scan, not reused numbers), lift
  2.0 (near the max this specific 56-unit gap can absorb - re-run the search if either changes).
  **Any same-height jump anywhere in this game needs the same treatment if it's ever widened**: a
  real walkthrough test driving `world.update` end to end, not a point-sampled probe or a test that
  only checks a downstream X the ground path alone could satisfy
  (`testLevel3CanJumpFromCameraBeamAcrossToFinalHangingCrateAndOnToFinalPlatform`).
- **`finalPlatform`** height was dropped 144->96 on request (now a real climb, matching the level's
  other two 96-unit climbs) - this turned the leg after it into a real ~50-unit downward jump, only
  ever easier than the same-height version, so not re-verified at the same precision.
- **A second camera (`poleCamera`)** watches the same crossing from a freestanding, non-collidable
  pole (poles are explicitly excluded from `occluders` - a camera on its own pole would self-occlude
  and render its cone as a flat rectangle instead of a cone). Both the pole and this camera render at
  a sampled `translucentEffectAlpha = 137/255` (matched to the chain art's own baked translucency,
  applied at runtime via `LevelLayout.translucentCameras` rather than a second asset) - the general
  pattern for giving one camera a distinct look without special-casing by array index. Sweep
  `minAngle 20 / maxAngle 160 / visionFov 50 / visionRange 230` - wide, mostly-horizontal, re-derived
  (not just shrunk) whenever the cone size changes, since a "smaller cone" can silently stop reaching
  one of its two flanking targets (`testLevel3PoleCameraSweepsLeftAndRightAndReachesBothFlankingBoxGroups`).
- Verified on JVM desktop screenshots in stages; **not on Android or iOS**.

## Level 4 ("04: Moving Target") - conveyor belt run, `LEVEL_4_LAYOUT`

Ground `y = 440`, `worldWidth = 8600`, exit at `x = 7680`, `timeTargetSeconds = 115`,
`backgroundImage = "metalbg.png"`, `hasDarknessVignette = true`, `canClimb = false`,
`restartOnConveyorFallOff = true`, `conveyorsStartOnMove = true`.

- Conveyor `speed = -45` against the player (net walk 87 px/s, crouch crawl 20).
- **This level defines the game's metre scale via decals, and it's the only statement of distance
  anywhere in the game.** Six stencils `wall_{150..0}m.png` at 1500 units per 30m step = **50 units
  per metre**. Level 7 reuses this spacing.
- Floor crates (68x48) ride the belt; four hanging crates (`isHanging`, some bobbing) force a
  crouch-and-slide under two of them (crate top 366 vs hanging bottom 340 = 26 of air, a crouching
  head clears by 18).
- Eight timed lasers (`Laser.kt`), varied angles/timing/pairs/triples.
- Darkness vignette centred on the player, rendered between world and HUD so HUD/controls stay
  bright.
- **Culling rule (CRITICAL)**: never register moving entities in the static `cullTargets` (spawn
  bounds go stale) - dynamic crates are culled per-frame against the camera window instead.
- **The wall grows taller, never wider** (2026-09-26). `metalbg.png` hangs from the screen top at a
  fixed scale (tile = exactly 1000 world units); on any canvas taller than 480 it used to stop short
  of the belt, leaving a band of bare stage colour (reported as "a brown thing"). Scaling it up was
  tried and rejected on paper: the 1500-apart stencils only land on bare panels because the tile is
  1000 wide - any other width walks most of them onto pillars at 4:3/7:6. Instead texture rows
  560..630 (plain panel + pillar shaft, no horizontal detail) are stretched by `canvasH - 480`, so the
  plinths sit at the same world height as on the reference phone, behind the belt.
- **The end machine is `l4end.png` cropped to its black silhouette** - the file's grey open-doorway
  frame (and the dark interior, striped hazard hood, beacon and baseplate drawn in code to dress it)
  were removed on request; the belt now runs straight into the black face. Because the tall block
  now ends ~277 units past the belt instead of ~362, wrapped crates (re-entering at x ~8026..8124)
  are hidden while their left edge is inside the machine (`crateHiddenFromX`), or a hanging crate
  would show above the machine's low section.
- **Emitters hang from the screen's top edge** (`LaserVisual.createAll(visualTopY = ...)`, level 4
  only): each is slid up its own beam line to the screen-top world y for that aspect. Drawing only -
  the lethal segment is still `topY = 150..bottomY`, which nothing the player can reach exceeds.
- Verified: an earlier iteration reached MISSION SUCCESSFUL end to end on JVM; the laser/bobbing
  version is in the uncommitted working tree. The three 2026-09-26 changes above are `jvmTest` +
  `android-shell` compile only - **not seen on any screen**. Not on device.

## Level 6 ("06: Stolen Manifest") - `LEVEL_6_LAYOUT`

Five sections; layout doc comments in `LevelData.kt` carry the reasoning.

**Section 3 - the crane crossing (rebuilt 2026-09-23), the way across, not scenery.** The player
climbs onto the boom from `tallBlock`, walks its length, comes down the machine's own silhouette to
the exit.

- **The boom's height is pinned exactly** (`boomBounds.top` at head height above `tallBlock` - this
  game's canonical 96-unit climb rise), and the crane's SIZE falls out of that requirement via
  `CraneDef.heightForBoomTop(...)` - never hand-pick `craneHeight` again.
- **`CraneDef` collides as three boxes** (boom, body, house) - "walking on it shouldn't feel like
  flying." The tracks' deck is deliberately NOT a step (the boom hangs too low above it at this
  crane's size - checked directly).
- **Climbing under the boom finishes crouched** (`ClimbTarget.endsCrouched`) - there's crouch room
  but not standing room under the beam; without this the player was hauled up standing and wedged.
  The fix generalised: the headroom check now tests at `climbLandingX` (direction-aware), not always
  `box.left`.
- **The crane's top is denied the mantle but stays jumpable if it's in jump range**
  (`LevelLayout.unclimbableBoxes` skips a box's mantle only, collision/landing/jumping unaffected).
  The owner's own words after three rounds: "MAKE IT JUMPABLE ONLY IF IT IS" - don't bend collision
  geometry to force a move to apply; deny the move instead. `maxJumpHeight` (51.2) is the analytic
  apex, not what a jump actually clears at 60fps stepping (~48.5) - leave margin under 48.5, not 51.2,
  wherever a jump has to land. A climb only fires on a jump PRESS against a face, consumed on the
  first frame of a held button - pulse it in any simulation.

**Section 4 - the gantry-gated climb.** `lever_3` sweeps a hanging crate left/right
(`phaseOffsetSeconds = periodSeconds/2` so it starts parked, blocking, not teleporting on trigger).
The gate is the CLEARANCE (`gateCrateClearance = 30`, well under the crouch-climb threshhold of ~56),
not the crate's mere presence. Sweep goes LEFT and stops 8 units clear of the cab (`gateCrateMinX =
houseBounds.right + 8`) so the exit route off the boom stays clear and the load never sweeps through
the machine or its own walkway. Landing window is ~53% of a 7s cycle against a ~2s climb.

**Section 3 cont'd - crane's top is climbable-not-jumpable on request history; see above.**

**Section 5 - hanging platform, switch, laser curtain.** `endCrate` bridges block->platform (48 rise,
a jump; the platform hangs level with the crate's TOP, not the block's). The 65-unit gap to the
platform is a hinge with two jobs: jump physics caps it near ~78 (measured by simulation, not
arithmetic - `84.5` units of flight minus `~6.5` spent landing), and it's wider than the player so
simply WALKING off the lip drops into the corridor below - jump across for the switch, walk off for
the way out. **Nothing may stand in that chute** (tried and rejected twice) and **nothing is drawn
holding the platform up** (chain removed on request - this platform is a deliberate exception to the
"nothing floats" rule). Three lasers hang from the platform's own tip (moving the bank would let the
platform bypass its own hazard) with `mechanismId = "lvl6_exit_lasers"`, disabled permanently by
`lever_4`. The patrolling guard's 170-unit beat is what makes the jump-while-he's-turned-away
actually work.

**`MovingPlatformDef.crushesOnContact`** (new, section 4's gantry crate) - a mistimed climb caught
under the load's underside is now Mission Failed, checked only from below so standing on top of a
moving platform is unaffected.

### The two stance animations this needed (both cut from existing frames, no new art)

- **Climb that ends crouched** (`CLIMB_CROUCH_END_PHASE`) - the climb clip already holds
  mantle->settled-crouch->stand-up; a crouched climb just stops mid-clip (raw frame 182, picked by
  silhouette-overlap scoring) instead of playing the stand-up. Height gain finishes earlier in the
  clip, so this cuts pose frames only.
- **Crouch -> jump** - gated on the same headroom test standing up uses. Launch plays the crouch clip
  BACKWARDS over 90ms into the jump clip's launch frame, instead of a hard snap.
  `crouchSuppressedByJump` drops the crouch stance for the whole jump, or the hitbox stays 56 tall
  under a 98-unit sprite and clips through ceilings.
- **`CEILING_ART_MARGIN` (3.0)** - the character is drawn up to 2.6 units taller than his collision
  box, so ceilings stop the box that much lower. Not applied while crouching (drawn shorter than its
  box). A first attempt also widened the ceiling detection rect and swapped `vy<0` for a captured
  flag - that combination isn't covered by tests; keep changes to the stop position unless there's a
  reason.
- **A crouch survives a fall, and the stand-up happens on landing** (`crouchedAtTakeoff`/
  `mustStayCrouched`) - nothing stands the player up mid-air.
- **The crouched climb's settled-on-top footage runs at 3.0x** - the deep tuck read as "the
  character seems smaller" at normal pacing; nothing moves during it, so speeding it up costs
  nothing.
- **The climb clip really is drawn 12-22% smaller than life, and that's deliberate** - reverted once
  already on request ("change back the size... to original") - don't "fix" this again without being
  asked. The shipped scale is what makes the climb's reach exactly one body height, so the hand lands
  flush on a 96-unit ledge.

Verified by a full walkthrough test plus JVM screenshots of three areas. **Not on Android or iOS.**

## Level 7 ("07: Service Tunnel") - `LEVEL_7_LAYOUT`

Linear crawling gauntlet, rebuilt to 120m / 6410 units on 2026-09-25 (see below for the metre scale).

- **The duct is STANDING height for its whole length** (`ceilingBottomY=304` vs `groundY=440`, 136
  clearance) - three crouch restrictions were built and then removed the same day on the owner's
  call ("remove the crawl under things"). Don't reintroduce without asking. The corridor's vocabulary
  is wind, steam and drones instead.
- **Vent Fans**: blow 135-145 u/s back down the duct; ordinary walking can't beat it
  (`WIND_WALK_FACTOR = 0.6` inside a zone) - spam-tapping forward is the mechanic (see "The wind
  stance" below).
- **Camera Bots**: patrol, forward vision cone (range 120, FOV 40 deg); walking into the cone is
  Mission Failed. Sneak up from behind within `deactivationRange = 52.0` and press INTERACT to
  deactivate permanently.
- **Pressurized Steam Pipes**: top/bottom/paired nozzles on timed cycles with a warning flare;
  contact is instant Mission Failed, deflectable once by the Laser Shield gadget.
- **Sequencing**: hazards staged with recovery room between, 7 manual checkpoints (one per 20m beat).
  **One deliberate exception**: `lvl7_pipe_9` sits inside `lvl7_fan_3`'s zone on purpose - the
  level's one "take a steam window at spam-tap pace" beat, which is why the walkthrough sim's time
  budget is 400s rather than 160s. No checkpoint sits under a duct (a checkpoint respawns standing).
- **Performance**: procedural textures/vision cones/LEDs/duct frames live in `VentFxAssets.kt` (keeps
  `GameplayScene.sceneMain` under the JVM 64KB bytecode limit). `VentObstacles.kt` is pure Kotlin,
  zero `korlibs.*`. Every emitter culls itself off-camera.
- **Wind and steam were rebuilt 2026-09-24** ("not realistic") - both now blend normally and OCCLUDE
  the wall (previously invisible in play due to bug #12's `size()` collapse); particles have real
  lifetimes and re-seed on death instead of cycling in fixed lanes; textures are baked variants, not
  runtime-rotated/negative-scaled (bug #8). **The plume must span the full corridor** -
  `SteamPipe.bounds` kills across the whole span, so the visual must never be tuned shorter than the
  box that kills. Verified on JVM screenshots and `jvmTest`; **not on Android or iOS**.

### Level 7's 120 metres - the metre scale, the stencils, the curve

Nothing in the game converts world units to metres at runtime - level 4 states its own length
entirely in wall decals (50 units = 1 metre, see above). Level 7 at 120m is five stencils 1500 units
apart, defined by `LEVEL_7_MARKER_*` and asserted against the layout - move the exit and move the
markers too, or the level stops meaning what it says.

`tools/art/prep_wall_markers.py` converts level 4's ochre stencils to white on disk (an exact
conversion since the art carries its shape entirely in alpha - `colorMul` can only darken, never
lift to white). They hang inside `worldView`, not the parallax layer level 4 uses, because
`bglvl7.png`'s background scale is canvas-dependent (level 4's isn't) - placed there, they'd grow and
shrink with the window.

**The painted duct is pinned to the world duct at every aspect (2026-09-26).** `bglvl7.png` used
to scale to `canvasH / 724` against a fixed `worldZoom`, so the painted duct only matched the world's
304..440 on the reference 480 canvas - on a 16:9 phone it was 166 tall, on a foldable 194, and the
owner saw ceiling nozzles hanging below the black beam and fans sliding across the wall art on
tablets. Now `LEVEL_7_BG_SCALE` is fixed at the reference scale; texture rows 186..526 (the duct,
split inside both black beams) are always drawn at it, and the distant scenery above and below
stretches vertically by the same factor (`level7SceneryStretch`, up to ~1.8 on 7:6) to fill a taller
canvas. `level7FloorScreenY` anchors worldView. Pinned by
`testLevel7PaintedDuctMatchesTheWorldDuctAtEveryViewport`. Not seen on a screen.

**Landing them on bare wall required a live search, not baked positions** - written when the
background's world scale still changed with device aspect; with the fixed scale above, every device
now gets the reference canvas's answer. `GameplayScene.findClearWallX` nudges each stencil up to
300 units along the corridor until its footprint clears both fans/pipes (a hard requirement - a
stencil under one is hidden outright) and busy background art (soft requirement, two-stage search).
The owner pre-authorised the trade-off (position need not be exact). Stencils are drawn 28 units
tall (not 32 - the widest plate needs exactly the widest available window at 28). A stencil with
no bare wall in range falls back to the nearest machinery-free spot. `VentVisualsTest` checks all
five at the (now single) background scale.

**A real bug this turned up**: level 4's decal pass was gated on a shared, label-keyed bitmap map
rather than its own background - filling that map for level 7 drew level 4's stencils a second time
at level 4's old positions. Now gated on `bgFileName == "metalbg.png"`. Lesson: a shared map keyed by
label is not a level gate.

**The difficulty curve** is measured, not asserted by feel
(`testLevel7DifficultyRisesFromStartToExit`): every back-half steam window is tighter than every
front-half window, drones get faster, fans push harder, no 20m beat is empty. Six beats, each adding
one thing and folding it into what came before (headwind alone -> steam alone -> first drone -> gate
+ faster drone -> jet inside a wind zone -> gust + tightest pair + drone on the door). A lone pipe is
never the difficulty by itself - only pairs and fan-overlapped pipes are.

`timeTargetSeconds = 85.0` (the sim clears it in 85.3s with zero deaths; 46.7s of pure walking is the
theoretical floor). **Verified**: `jvmTest` green, `android-shell:compileReleaseKotlin` clean, and
120m/90m/0m stencils read off the screen at their own positions on JVM desktop. **Not checked**: 60m
and 30m stencils, anything on Android or iOS.

## The level 7 patrol rover (`resources/robot_{body,wheel}.png`) - replaced 2026-09-25

Cut by `tools/art/prep_robot.py` into two plates (body with wheel-holes, one cogged wheel) - that
script's header is the source of truth for every fraction in `CameraBotVisual`; re-run and paste
rather than hand-editing.

- **The body is cut because a flat silhouette can only show motion through its rim** - a cog drawn
  inside the outline would union with the rotating wheel into a shape that's toothy at every angle
  and shimmers instead of turning. The cut disc is slightly wider than the wheel so no tooth tip
  survives the resample.
- **Wheels are driven by ground distance, not time** (`rollAngle += dx / r`) - stops dead when the
  bot pauses, stays in sync with any level's own `speed` with no second constant needed. A respawn
  teleport is swallowed via `abs(dx) <= bot.width`.
- **The mirrored chassis reverses a child's rotation sense** - `CameraBotWheelTest` asserts on the
  rendered `wheels[i].rotation`, not an internal accumulator, for exactly this reason.
- **The lens is dark unless the rover has the player** (`alertLens`, a single red rect) - the old
  always-lit cyan glow/lens/pip stack is gone entirely (see fixtures pass below).
- **The lens moved up the boom** (`EYE_HEIGHT_FRACTION = 0.20`, was 0.45) - the rover carries its
  sensor over the front wheel, not mid-box like the old squat crawler; this is gameplay-visible
  (crouching under the boom is slightly safer now, standing on a crate slightly less so).
- **Facing is a judgement call, not a measurement, and worth re-checking on screen** - the plate is
  treated as facing RIGHT (boom reaching forward over the front wheel). If it's backwards, flip the
  plate in `prep_robot.py` - do NOT invert the sign in `CameraBotVisual`.
- Sizes follow the POT rule; the collision box (32x26) is untouched by any of this.
- **The procedural crawler is still the fallback** if either bitmap fails to load.

A black rover on a dark duct floor is intentionally hard to see (same silhouette treatment as the
player) - don't "fix" with `colorMul` tinting, which does nothing on RGB-zero art.

Verified: `jvmTest` green including 8 `CameraBotWheelTest`s, `android-shell:compileReleaseKotlin`
clean, on-screen size/placement/mirroring/cone-origin confirmed on JVM (shadows lifted to see the
near-black art). **Wheels visibly turning was NOT confirmed on screen** - too small a rim to separate
rotation from travel in a capture; pinned by test instead. **Not checked on Android or iOS.**

## The level 7 fixtures pass (2026-09-25) - three removals and one art swap

`VentVisualsTest` pins each absence rather than trusting a comment, since a removal is the kind of
change that quietly comes back.

- **The exit terminal (`VentCorridorVisual`) is gone entirely** - the owner pointed at its
  blue-and-grey shape and asked for it removed. Start fresh if level 7 ever wants architectural
  framing again; don't resurrect this class.
- **The rovers carry no running lights** - the always-lit cyan glow/lens/pip is gone, replaced by
  `alertLens` (a single red rect, visible only while the rover has the player). A deactivated rover
  still throws amber sparks on a 1.5s blink.
- **The steam nozzles are the owner's art** (`resources/steam_nozzle_{up,down}.png`), replacing
  stacked `solidRect`s. Two files (not one + a negative scale, per bug #8). Both mounts sit INSIDE
  the corridor now (floor on `bottomY`, ceiling on `topY`) - recessed above the ceiling, the old
  rects vanished against the dark background beyond the duct with only their LED visible. The plume
  is built before the fixture so it draws over the mouth it leaves from.
- **The status LED survived the swap** (red dormant, green from the warning flare through the
  eruption) - it's the player's timing tell, so the new art was measured to place it on solid metal.
- **No more full-canvas red wash on a lethal hit, on any level** - it painted over the very frame
  that told the player what killed them, and painted the same red over the MISSION FAILED card's
  first frame. Removing one subscription in `GameplayScene` removed it everywhere (the model hooks
  themselves are unchanged and still tested).

Verified: `jvmTest`/`android-shell` clean; red-wash removal confirmed by pixel count, not eye
(~211 red pixels from LEDs through the death vs ~1.75M a wash would add). **Exit terminal removal not
re-checked on screen** (it's a level away from anywhere reachable in a test run and the drawing class
no longer exists). **Nothing checked on Android or iOS.**

## The vent fixtures pass 2 (2026-09-25) - lamp meaning, burial, fan rate, prompt timeout

- **The steam lamp is red only while gas is actually out** - it used to run green through the whole
  eruption (i.e. said "safe" at the moment the pipe kills). Each phase now owns its own colour
  (green/amber/red), swept and asserted across a full cycle in `VentVisualsTest`.
- **The nozzles are bolted through the duct wall** (`BURY_FRACTION = 0.22`, not the 0.5 first tried) -
  measured off the art: the bolt blocks only exist in the outer half of the plate, so burying a full
  half hides them AND narrows the visible silhouette, which looked worse than the floating gap it was
  meant to fix. 0.22 is the deepest bury that still leaves the lamp's disc fully on solid silhouette.
- **Fan blades run at 16.0 rad/s** (~2.5 rev/s, up from 8.0) - capped by the strobe effect, not taste
  (an N-bladed rotor reads as stopped/backwards once N rev/s crosses 30 at 60fps). Pinned by test - a
  still frame can't show a rate.
- **`TutorialStep.autoDismissSeconds`** is new and opt-in (0.0 = wait indefinitely, the default for
  every pre-existing step). Level 7's deactivate-bot prompt sets 5.0, since disabling the drone is
  optional. A test asserts no other step got a timeout.

**Screenshots are not the tool for a prompt's lifetime** - the world updates behind the loading
screen's fade, so a 5s prompt can open and close before any capture's first frame. A `println` on
activate/dismiss settled it in one run where 0.6s screenshot bursts caught nothing.

**Verified**: `jvmTest`/`android-shell` clean. On JVM (before screenshot testing was called off for
this pass): stencils clear of boxes, nozzle LED colours correct, fixtures meet the black band with no
gap. **Not checked**: stencil positions after the avoidance search moved them again, final lamp
seating, fan rate (untestable by screenshot), anything on Android or iOS.

## The wind stance (`resources/player/wind{transition,walk}`) - built 2026-09-24, live on level 7

Cut by `tools/art/prep_wind.py` - **that script's header is the source of truth, re-run and paste,
don't hand-edit.** `windwalk`'s source frames arrived empty and were reconstructed by
reverse-engineering the rule the shipped `windtransition` plates used (`alpha = 255 - luma, rgb = 0`)
- reproduces the shipped plates to h.264 noise level.

- Shares push's scale/body-centre framing (frame 0 of the transition IS the standing pose, free
  handover in/out of idle). Frames are 192 wide (push's leading arm needs the extra width).
- **`WIND_STRIDE_PER_HEIGHT` = 0.33 is a cadence knob, not a foot-planting constraint** - the feet
  are MEANT to slide (gale drags the character back while he strides forward), so anywhere in the
  measured 0.31-0.39 bracket reads fine.
- **What plays when is the owner's explicit rule**: standing in the airflow holds a still braced
  pose; the wind walk plays only while spam-tapping (`GameWorld.isWindPushing`), NOT gated on ground
  speed - ground speed is nearly zero exactly when the player is straining hardest, which froze the
  legs at the wrong moment when tried first.

### The tap mechanic, rebuilt ("slower and smoothly")

The old rule moved `player.x` by a flat 10-unit jolt per press (four jolts/sec at human tapping
rate). Now a tap buys decaying velocity (`fanSurgeSpeed`), spread over following frames by normal
integration. Every constant here came from simulating the real loop, not arithmetic - `WIND_WALK_FACTOR`
(0.6) exists because a touch player can only hold an on-screen button down for part of each tap cycle,
unlike a keyboard hold-and-tap; at the old numbers the level ran BACKWARDS at a realistic phone
tapping rate.

**Three boundary bugs found in the running game, none by reasoning:**
1. Clearing the surge on leaving the fan zone stalled the player at its lip (the gale bounces a
   leaning body in and out of the zone boundary every few frames) - only the WIND itself is
   zone-gated now; surge decays on its own everywhere.
2. Momentum outliving the zone also outlives the player's intent, which is lethal next to a timed
   steam jet - `FAN_INTENT_WINDOW` (0.35s, refreshed by any tap/hold) switches to a fast release decay
   once the player stops.
3. Taps must register slightly OUTSIDE the zone too (grace via `windStanceBlend > 0`), or most land
   on frames the wind has just pushed the player out on.

`FAN_TAP_FLOOR` (40) is a fourth measured value - without it, a cold-start burst nets negative
velocity for its first second (reads as broken input).

**`windOwnsSprite` in `GameplayScene` is load-bearing** - the scene's frame driver was silently
overwriting the wind clip with the walk gait in the same frame the state machine picked "wind" (a
mystery `else`-branch race, traced frame by frame rather than fixed at its root). The wind branch
claims the sprite with its own flag and the driver checks that first. **Any future stance should do
the same rather than trust `playerAnimState` to survive the trip.**

Verified by `jvmTest` (207 green) and `android-shell` clean. On JVM the lean renders and the
handover works; **the fully-settled braced pose and mid-zone gait cycling were not confirmed
visually** (synthetic key input kept stalling or overshooting). **Not on Android or iOS.**

## The push stance (`resources/player/push{,transition}`) - built 2026-09-24, shipped 2026-09-26

Cut by `tools/art/prep_push.py` - same rule, re-run and paste, don't hand-edit.

**It now has something to push** - `LevelLayout.pushCarts` (level 8's cart, see below) is the real
prop the stance was built ahead of. `pushStanceDemo` (nothing to brace against) is dev-only, off the
shipped list, reachable by id (`LevelData.PUSH_STANCE_DEMO`, `-PstartLevel=push_stance_demo`) - two
tests pin that nothing shipped sets it and that it stays reachable.

- Framed ~6.4% smaller than every other clip (own `244.36/484` scale) - get this wrong and the
  character visibly changes size entering the stance.
- Frames 180x256, cropped symmetric about the STANDING body centre (not the union bbox) - the sprite
  doesn't shift entering the stance; the braced pose then leans out ahead of the collision box's own
  edge, which is what pushing looks like.
- **The loop keeps every raw frame while the transition is halved - a slowness/speed argument, not a
  footage one.** The braced move is slow (1.07s/cycle), so keeping all 40 frames gives 37fps (walk's
  own rate); halving it would read as a flip-book. Redo this arithmetic, don't reuse the conclusion,
  if `PUSH_MOVE_FACTOR` ever moves.
- **`PUSH_STRIDE_PER_HEIGHT` = 0.59** - took four measurement attempts to get right (sub-pixel phase
  correlation of ground-contact alpha, pooled over both feet, on the PROCESSED output only). Integer
  bbox edges, single-foot centroid fits, and measuring the wrong (accelerating) part of the raw clip
  all gave wrong answers.
- **The rest pose is a GAIT frame (push frame 0), not the transition's own last frame** - the two
  clips are separate takes that settled on visibly different poses (a standing-still braced body had
  its hand short of the cart while a walking one made contact). Fixed on both sides: the scene pins
  push frame 0 while braced-and-stopped, and the transition was re-cut (raw 10..72) to end on the
  best-matching pose by silhouette IoU (index 31, 0.803) - re-run that scan, don't assume index 31, if
  this is ever re-cut.
- Facing is LOCKED for the whole stance (walking backwards drags the load without spinning the
  silhouette). `isPushStanceHeld`/`pushStanceBlend` (0..1, both directions) live in `GameWorld`; the
  toggle is edge-detected there since the scene hands over a raw button level, not an edge.
- Verified by `jvmTest` and a full JVM walkthrough (idle->lean->braced->push->pull->refused
  jump/crouch->stand up->normal restored); planted-foot slide measured within +/-5% by screenshot
  burst. **Not on Android or iOS.**

## Level 8 ("08: Relocation") - `LEVEL_8_LAYOUT`, the suspended-load yard (built + reworked 2026-09-25)

Unhidden the same day it was built (`.take(7)` -> `.take(8)` in `LevelSelectScreen.kt`,
`MainMenuScreen.kt`, `GameplayScene.kt`; levels 9-12 remain hidden stubs). Shipped with no tutorial
steps at first; the cart's two steps were added 2026-09-26 since nothing earlier teaches it.

Seven numbers do all the work:

| number | value | what it decides |
| --- | --- | --- |
| `hangClearance` | 62 | shared hang line for the first-half crates - crouch fits, standing never does |
| `periodSeconds` (sweep crate) | 8.0 | window the 1.95s climb has to fit inside |
| `bobLowClearance`/`bobHighClearance` | 62/90 | bobbing pair's travel |
| `noCrouchClearance` | 130 | post-gauntlet load, deliberately not an obstacle |
| `visionFov` (pole camera) | 20 deg | narrowest that avoids watching the crate you must cross to reach the lever |
| `sweepPauseDuration` | 7.0 | the blind window, against a ~5s run through lever + both mantles |

- **In flux (2026-09-26): section 1's two loads (`overheadCrate`, `sweepCrate`) hang
  `sectionOneDrop` = 50 BELOW the shared hang line** on the owner's request, with solvability
  explicitly set aside for now - the sweep crate leaves 12 units over the landing, which no climb
  fits. `testLevel8HangsAllThreeLoadsOnOneLineJustAboveThePlatform` and
  `testLevel8IsBeatableByReadingTheLoadSwingingAway` fail until the level is re-tuned. Max drop is
  62 (the sweep crate then parks inside the platform).
- **`hangClearance = 62` is boxed in on both sides** - below `crouchHeight` (56) the platform seals
  shut with no safe pocket (worked through on paper before building, dead-ends at every sweep
  range); at `height` (96) or above, a standing body just walks under and it isn't an obstacle. 62
  leaves 6 units of headroom, the minimum this geometry allows.
- **The crush behaves differently at 62 than an earlier 44** - `Player.bounds` uses `currentHeight`,
  and `isCrouching` is only set at the END of the climb animation, so a climbing body is 96 tall for
  the whole 1.95s climb. At 44 neither crouch nor stand fit, so the climb was refused outright; at 62
  it's allowed and the crate can kill mid-ascent instead - a more literal read of "it can crush him if
  he tries to climb." **The real tuning target was the WORST window** (clear-and-swinging-BACK, not
  clear-and-swinging-away) - simulated, not derived, at ≥2.99s vs 1.95s+0.25s needed.
- **No `unclimbableBoxes` entry anywhere in this level** - `findClimbTarget`'s own 4-unit-tolerance
  rule and `climbMaxHeight` (115) naturally rule out every hanging crate; the bobbing pair's low point
  (100, inside range) is refused by the floating-ledge rule alone.
- **The pole camera problem is bearing overlap, not range**: from a lens on a pole, a body on a crate
  and a load further left occupy overlapping bearings, so a cone wide enough to see the load also
  lights anyone crossing between the lens and it. Fixed by moving the crate right (to clear the
  bearing by 2.3 deg) AND narrowing the cone to 20 deg together - neither alone works. Any future
  change to camera position/cone/crate placement here needs the bearing math re-checked, not just a
  smaller-looking cone.

### The push cart - `LevelLayout.pushCarts`, added 2026-09-26

The old fixed step-crate onto the mid platform is now a loaded flatbed (`cart.png`) parked 248 units
short of the platform face - the player must walk it there. First shipped use of the push stance.

- Two numbers inherited from the crate it replaced and must not drift: 48 tall (a jump, not a
  mantle) and its right edge lands exactly on `platformLeft` (the climb off the top is the same
  already-tuned 96-unit rise).
- **Pinned to the body, not pushed by it** (`gripCart` records the offset, `followGrippedCart`
  re-applies it) - the cart can never shove the player, the player can't walk into what he's pushing,
  and the cart's own bounds become his movement limits.
- **The character is drawn 5% larger than he collides** (`VISUAL_HEIGHT_SCALE = 1.05`) purely so the
  fixed push-pose plate's fist reaches the cart handle's corner - collision, jump arcs, climb windows
  and every level's crouch gaps are untouched. **The ceiling on this number is level 8's own 62-unit
  hang line** - a crouched body drawn at `56*1.05=58.8` leaves 3.2 units of headroom where there were
  6; check that gap before raising this again (`testTheDrawnBodyStillFitsTheTightestCrouchGap`).
- **Grabbing is gated on range + being grounded**, and refuses a body standing on the cart itself (no
  way to jump/crouch off it otherwise).
- **The grab offset eases to contact rather than freezing as pressed** (`settleIntoCart`,
  `GRIP_REACH = 22`) - freezing left the hands visibly short of the cart if pressed from a step back.
  Move input is zeroed for the 0.85s settle so steering can't fight it. The offset must be
  RE-DERIVED from the blend on every tick (not just while easing) or the frame the blend hits 1.0
  pins whatever partial offset the last tick left.
- **Solved for the HAND, not the body's leading edge** (`handleGripX`/`bracedFistX`) - flush-to-face
  put the fist inside the cart, over the crate rather than on the handle. The 4.7-unit resulting gap
  between collision boxes is the character's actual arm length.
- The tutorial's MOVE step measures cart travel, not player position, since the body moves on its
  own during the settle.

### The lever is mandatory

Same `HookCrate`/`hangingHooks` rigging as level 5, minus the swing - drops the crate to the ground
(not onto boxes) against the high platform's face, turning an unclimbable 144-tall wall into a
jump-then-mantle pair. Nothing else reaches the high platform, so the camera guards a required
action.

### Measured, not estimated

The walkthrough at 12 arrival phases finishes in 32.9-47.2s, never dies, peaks at 0.43 of the alert
bar. `timeTargetSeconds = 80`. Two traps for future tests here: read a moving crate's position AFTER
`world.update`, not before; the sweep crate starts its cycle at the far LEFT end, so a test wanting it
parked over the landing must run the world forward to get there.

**This is the first shipped level with a camera and no guards** - `GameWorld`'s spotted-guard branch
used to assume `allGuards.first()` existed as a fallback, which would have crashed here; changed to
`firstOrNull()` with a null guard while building this level.

## Guard sprite (`GuardAnimations.kt`, `resources/guard/{idle,walk}/`) - replaced 2026-09-14

Same recipe as `PlayerAnimations` (own atlas, feet-anchored, `scaleX` negated to face left).
`tools/art/prep_guard.py` cuts both clips and prints the constants - re-run and paste. Crop is
symmetric about the torch-holding body, not the union bbox. Idle: every third frame, ping-ponged.
Walk: distance-driven (`WALK_STRIDE_PER_HEIGHT = 0.523`), framed ~14% smaller than idle
(`WALK_PLATE_SILHOUETTE = 530` - move this if he shrinks/grows entering a walk). Torch lens position:
`TORCH_AHEAD_PER_HEIGHT = 0.29`, `TORCH_ABOVE_FEET_PER_HEIGHT = 0.56`.

Levels 6+ guards (`GameWorld.createDefault`'s per-level `guardSpeed`) still have 48-tall hitboxes and
draw as half-height men pending a 96-tall pass. Level 5 has no guards at all.

Owner decisions: no colour ramp on guard beams or camera cones (one steady colour in every state; the
pip over the head/camera shows detection instead) - don't reintroduce. **Camera rotation stops on
player detection** (`detectionPauseDuration = 2.5s`, mirrors `Guard.investigateDuration`) and resumes
its sweep only after that pause. Checkpoint respawn and Smoke Screen reset the pause.

## Guard vision = the torch beam (`Guard.eyePosition`, `LightConeView.kt`) - 2026-09-14

`Guard.eyePosition` (name shared with cameras in `VisionSystem`) = 28 ahead / 54 above the feet for a
96-tall guard. Drawn beam and detection rays share this origin - what the player sees lit is exactly
what can see them.

**`LightConeView`** uploads the vision polygon as one triangle fan with per-vertex colour, additive
blend, one draw call - replacing KorGE's SYSTEM renderer, which rasterised a multi-MB bitmap per cone
per frame (and whose GPU path does a full-framebuffer stencil pass per non-convex shape per frame).
`GameplayScene` only rebuilds a beam when lens/facing/range actually change, and skips guards outside
a half-screen culling window.

## Device heating on Android - measured root causes (2026-09-09)

Measured on JVM against real level data plus decompiled Android classes, not on device.
1. **Vision cones drawn with the SOFTWARE rasterizer, rebuilt every frame** - up to several MB/frame
   per cone at 1440p. **Guards: DONE via `LightConeView`. Camera cones still use this path** - the
   remaining instance.
2. Cone polygon build allocates hundreds of KB/cone/frame in raycasting - closed for guards (static
   occluders, rebuild only on change); still open for cameras.
3. **Nothing caps the frame rate** - runs at panel refresh (120Hz on this device).
   `Views.forceRenderEveryFrame = false` does NOT cap it - it switches to an infinite ~1000Hz update
   loop instead, which backgrounding doesn't stop.
4. Oversized textures (fixed, below) and always-rendering-under-the-menu (bug #7).

Next: camera cones, then a real frame cap.

## Runtime performance: where the frame budget goes (2026-09-08..10)

Reasoned from assets and the render path; on-device improvement unmeasured.

1. **The player atlas is the biggest memory consumer** (`MutableAtlas(2048,2048)` adds a whole
   16.8MB page at a time). Trimming unreachable frames plus adding the swing/push/wind clips nets out
   around **28.5M px** currently. Adding frames is not free - see the ATLAS BUDGET comment on
   `load()`.
2. **Textures authored 10-26x larger than drawn, and `mipmaps(true)` silently no-ops on non-POT
   art** (no error, no log) - FIXED 2026-09-10, see "Adding new art" below.
3. Per-frame allocation in the updater (fixed): profile deep-copies, platform/box/occluder list
   rebuilds, powerup HUD shape/label rebuilds all now cache and only recompute on change.
4. KorGE renders continuously under the Compose menu on Android (bug #7) - first suspect for a menu
   lag report.

**Dead assets removed** (`resources/` 75MB -> ~39MB): a long list of unused backgrounds/UI images.
**Before deleting, grep the whole repo excluding `build/`** - packaging-evidence hits in
`build/intermediates/.../merger.xml` are not use.

**`SceneAssets.kt` caches bitmaps/fonts process-wide** - was re-decoding ~87MB of PNG on every scene
load. Only successful loads are cached.

**Lossless PNG recompression** (Pillow, `optimize=True, compress_level=9`): 229/604 files smaller,
byte-identical decoded - download-size win only.

**Off-screen culling**: static decor registers its world-space span via `cullable(...)`, toggled
`visible` off camera with a half-screen margin. Static only - player/guards/cameras/moving platforms
excluded (children of their own pips/cones).

**DECIDED NO: do not atlas the static world art - not "not yet", not at all.** After the POT pass the
combined stretch-to-box textures are under half of one atlas page, so atlasing more than doubles
their memory for a sub-noise-floor bind-count win, and conflicts with mipmaps (bleed across slice
boundaries). Atlasing pays for hundreds of small sprites, not a dozen large props.

### POT + mipmaps pass - DONE 2026-09-10

Ten assets re-encoded: **50.5 MB -> 9.8 MB of texture memory**, disk 10.2 -> 2.1 MB. Each verified by
rendering into its exact device-pixel draw box before/after and diffing (mean error under 0.6/255).
Originals live in git history at `77a65b9`.

**DO NOT SHRINK - already at or below device resolution at 1440p**: `bgmg2-6.png`,
`loadingbg.png`/`logo_main.png`, `dossier_paper.png`, `success3.png`, `failedscreen.png`, the button
sprite sheets.

**Still hardcoded to file dimensions** (pin as literals/fractions first, or leave the file alone if
resampling): `chainedcrate.png`/`chainedcrate2.png` slices, `stars.png` slices, `hook.png`'s aspect.

### Adding new art: shrink it on the way in - a standing rule

1. Find the size it's **drawn** at in virtual units, not painted at.
2. **Multiply by 3** (authored canvas 1040x480, real canvas up to the device's aspect, phones render
   at up to 3x) - sizing from virtual numbers alone gives a third of the needed resolution.
3. Round to a power of two (up, unless within a couple percent of the lower).
4. **Resample, never pad** - transparent padding stretches into the draw box with the art.
5. `python tools/art/pot_resize.py resources/newthing.png 512 512` (premultiplied-alpha LANCZOS,
   `--check` reports sizes).
6. Verify by rendering old vs new into the device-pixel box and diffing (<~1/255 mean).
7. Wire via `SceneAssets.bitmap("x.png")` (default `minified = true`); use `minified = false` for
   anything drawn ~1:1/larger or sub-sliced (mip levels bleed across slices).

**Crop new art on ALPHA, not `Image.getbbox()`** - that helper bounds every channel, so a
transparent-margin-with-RGB source keeps invisible dead space in the draw box (this floated
`exitlvl7.png`'s building 12 units off the ground). Use `np.nonzero(alpha > 8)`.

**Aspect ratio is NOT a concern for stretch-to-box assets** - every draw is `size(box.width,
box.height)`, so the file's own aspect never reaches the screen; write a literal naming the authored
size instead of deriving it from the bitmap. Use a premultiplied-alpha-aware resampler.
`truck.png`/`entrance.png` are pre-mirrored on disk - a tool that "normalises" orientation would undo
bug #8's fix.

**The guardrail**: `SceneAssets.warnIfNotPowerOfTwo` prints one line per offending asset per run. A
new line in that output means something new needs sizing.

## Asset prep techniques

Source drop: `C:\Users\USER\Downloads\charAnimations\assets\`.
- **Tileable parallax backgrounds**: circularly roll horizontally by half-width so the wrap seam
  lands at the centre, heal it with a narrow falloff-weighted blend against a blurred copy, verify by
  diffing edge columns. **Band width/blur must scale with source detail** - a wide/blurry heal that's
  fine on a foggy background leaves a visible haze band on a crisper one.
- **Character animation plates** (`prep_guard.py`/`prep_push.py`): crop one shared box per clip,
  symmetric about the body centre (so a flip doesn't shift it), feet pinned per frame to the bottom
  edge, resampled premultiplied. **Measure each plate's own standing silhouette rather than reusing
  another clip's scale** - clips can be framed at meaningfully different sizes at the same file size.
  Measure `*_STRIDE_PER_HEIGHT` by phase-correlating ground-contact alpha on the PROCESSED output.
- **Splitting a prop so one part can move** (`prep_robot.py`): on a flat silhouette, cut the moving
  part OUT of the static plate (don't overlay a new one on top - the original outline unions into
  every angle) slightly wide of it so nothing survives the resample, and reconstruct whatever it
  occluded from scanlines the part doesn't reach. Find the part by structure, not hardcoded
  coordinates.
- **Anything that rotates gets cropped to its bbox and forced square** - divides out drawing
  ellipticity and fixes the outer extent by rotating about the bbox centre, not the centroid.
- **Tight-crop a silhouette to its alpha bounds** before stretching into a box; re-derive box width
  from the cropped aspect at the fixed height.
- **Chroma-key an opaque JPEG-style asset**: R/G/B all >= 200 -> transparent, then crop.

## Smaller features and decisions

- **REMOTE_TRIGGER powerup was a complete no-op (fixed 2026-09-20)**: activating it did nothing on
  any platform despite a real Store description. Now picks the nearest un-thrown lever by distance
  and throws it exactly as an in-range interact would; refuses (without spending the item) if no
  lever exists to trigger. **While building its test, a real pre-existing shared-mutable-state bug
  surfaced**: `createFromLayout` passed `layout.levers`/`layout.hookCrates` straight through instead
  of copying, so two `GameWorld`s built from the same top-level layout `val` (as a test suite does)
  mutated the SAME lever/crate objects - one test's thrown lever broke a later test in the same JVM
  run. Fixed with `.map { it.copy() }` on both. **This class of bug is exactly what a partial/
  up-to-date test run can hide** - re-verified with a forced `--rerun-tasks` full run.
- **F2 (JVM-only debug key)**: tops up every gadget by +3 via `grantDebugPowerups(3)`, gated on
  `Platform.isJvm`. Never persisted - JVM's `PlatformStorage` is an in-memory map only.
- **Gadget tray polish (2026-09-20)**: icon centred on both axes in its box; stock-count text always
  white; a live/running gadget shows a semi-transparent white curtain draining top-to-bottom across
  the whole box instead of "ON"/countdown text (alpha `0.14`, lowered twice from an initial `0.42` on
  request - re-lower the same constant if asked again). **Per-corner `roundRect` needs manual
  clamping** - unlike its single-radius overload, korlibs' per-corner `roundRect` does zero clamping
  when a box shrinks below the requested corner radius, so a draining overlay's corners can bulge
  past the box bounds mid-animation. Fixed by clamping the bottom radius to remaining height, AND by
  keeping the whole overlay rect inset a fixed generous margin inside the frame's own stroke line
  (`drainInset = 1.6`) so it can't reach that line regardless of any further rounding-math surprise.
  **Any future per-corner `roundRect` call anywhere in this codebase needs the same manual clamp.** A
  gadget already running can't be re-triggered until it ends (guarded both in the model and before
  the inventory is spent, so a refused re-press doesn't burn an item).
- **Detection pip**: "!" badge (no background circle) for a heard noise, a filling clock for an
  actual sighting - seeing always wins when checked. Stealth Boots suppresses the noise pip entirely,
  and losing line-of-sight (vs. losing the sound cue) is tracked separately so the right icon shows.
- **Debug noclip flight (F1, JVM-only)**: free 2D movement, zero collision/gravity, freezes all
  hazards/timers - a pure layout-inspection tool, gated on `Platform.isJvm` so it can't appear on
  Android/iOS even with a keyboard attached.
- **App icon**: real 1254x1254 source wired into Android/iOS/KorGE icon slots. Not verified on
  device.
- **Language system**: 15 languages in Settings; French fully localized across menus and in-game
  overlays; in-game tutorials stay English by design. Pure-Kotlin `Localization.kt`, zero
  `korlibs.*`.
- **Reset Progress**: confirmation dialog, resets coins/inventory/unlocks/level results while
  preserving `isPremium`.
- **Layers Events SDK - REMOVED 2026-09-12.** Zero third-party analytics SDKs remain.
- **In-App Review**: automatic StoreKit/Play prompt after level 4 completion on both platforms.
  **iOS's manual RATE US button opens the App Store review page directly (fixed 2026-09-25)** -
  `SKStoreReviewController` is Apple's automatic-only prompt (never shown in TestFlight, capped 3/year
  in production, silently ignored otherwise, no callback) so a deliberately-pressed button can't
  meaningfully use it; `APP_STORE_ID = "6815256409"` lives at the top of `InAppReview.ios.kt`.
  Android's RATE US is unchanged (Play in-app review, reported working).
- **Ads are personalized, behind a consent flow, on both platforms** (2026-09-26; replaced a
  same-day `DISABLED`/no-ATT stopgap). Both `BasicAds.configuration` sites
  (`AdMobVerifyContent()` iOS, `MainActivity` Android) use `PublisherPrivacyPersonalizationState.
  DEFAULT` - the SDK personalizes where the user's answers allow. Flow: Google UMP consent message
  (GDPR - only EEA/UK/CH, and only once a message is published in AdMob's "Privacy & messaging"
  tab), then on iOS the ATT prompt, then `AdPrivacy.canRequestAds` flips and the SDK starts.
  - **`AdPrivacy` (commonMain) is the gate.** Every ad host (continue, interstitial, both Store
    rewarded hosts, both platforms) resolves a request immediately while it is false instead of
    loading - never let a new ad host skip this check.
  - **UMP comes from `basic-ads`' own `Consent` wrapper** - no new dependency (Android pulls
    `user-messaging-platform` 4.0.0 transitively; iOS already had the pod). `AdConsent.android.kt`
    runs it from `MainActivity.onCreate`/`onResume`; `AdConsentBridge.kt` (iosMain) runs it from
    `AppDelegate.applicationDidBecomeActive`.
  - **ATT lives in Swift**, and only from `applicationDidBecomeActive` - Apple silently skips a
    request from an inactive app. Its completion handler is off-main; hop back before
    `AdConsentBridge.finish()`. Skipped under `-ci-test` (nobody taps the alert in CI).
    `NSUserTrackingUsageDescription` is in `project.yml` - iOS crashes the request without it.
  - Settings > About shows **AD PRIVACY CHOICES** only while `AdPrivacy.privacyOptionsRequired`
    (Google's required way back into the consent message).
  - **Privacy manifest deliberately still says `NSPrivacyTracking = false`.** Apple requires at
    least one `NSPrivacyTrackingDomains` entry when it is true, and iOS 17+ then BLOCKS those
    domains for users who deny ATT - listing Google's ad domains risks no ads at all for them.
    AdMob 13.8.0's own manifest (printed by CI) has no `NSPrivacyTracking` key and no domains.
    Tracking is declared per data type instead: `NSPrivacyCollectedDataTypes` is AdMob's 7 entries
    (Device ID the only one with tracking=true) plus RevenueCat's Purchase History, merged by
    script from the CI print and RevenueCat's GitHub manifest - re-derive, don't hand-edit.
  - **`SKAdNetworkItems` is Google's full 50-ID list** (Google's own first), copied from
    developers.google.com/admob/ios/3p-skadnetworks - re-copy the whole list when Google updates it.
  - **Verified in CI 2026-09-26** (runs 36251116781 / 36251116782, raw logs read): both frameworks
    link, shell `BUILD SUCCEEDED`, `android-shell bundleRelease` `BUILD SUCCESSFUL`, and on the
    simulator `admob_verify_result` = `initializeCalled=true:personalizationEnabled=true` - i.e.
    the consent flow settled and let ads start. **Never run on a device**; the consent message
    can't be seen from CI's (US) simulator. Test UMP with `ConsentDebugSettings` geography EEA +
    a test device ID, or a VPN to an EU country.
- **Temporary gating for Google Play production approval (2026-09-25)**: levels 8-12 were hidden via
  `.take(7)` in three places (level 8 since unhidden, `.take(8)`); "Coming Soon" chapter placeholders
  removed (replaced with layout-preserving spacers); Settings language list restricted to
  English/French (the only two fully localized); level 2 rain was briefly disabled then reverted.
  **If a Play review ever needs the rain gone again, `LevelData.DEFAULT_LEVEL_2.hasRain` is the whole
  switch.**

## Release 1.0 (build 19) - App Store review readiness (2026-09-26)

Build number lives in THREE places - bump all together: `ios-shell/project.yml` `CFBundleVersion`,
`ios-testflight.yml`'s `build_number` default, `android-shell` `versionCode`. TestFlight uploads
run only on a `v*` tag push or a manual dispatch, never on a plain push to `main`. Build 19 ships
REAL ad units (`USE_TEST_ADS = false`) because the TestFlight binary is the one submitted.

Review-risk audit done before 19, and what it changed:
- **The public privacy policy (`site/privacy/index.html`, Netlify, gitignored - deployed separately
  from git) contradicted the app** once ads went personalized (said: no IDFA, no ATT prompt, no
  consent dialog). Rewritten; **it must be live before submitting** (Guidelines 5.1.1/5.1.2). Any
  future ads/data change needs the same pass over that page.
- **iOS Restore only checked RevenueCat entitlements**; Android also accepts a purchased
  `remove_ads`-like product id. iOS now matches - a Remove Ads product with no entitlement attached
  would otherwise restore as "nothing found", which reviewers test for (3.1.1).
- **ATT request is delayed 1s** after activation/consent-form close - iOS silently drops a request
  made mid-transition, and "prompt not found" is a routine 5.1.2 rejection.
- Checked clean: Restore on the Remove Ads screen, no other-platform names in UI text, no
  "Coming Soon" shown, no permission-gated APIs (so no other usage strings needed), 1024 icon
  has no alpha, privacy/support URLs return 200, ExportOptions `app-store`, debug keys JVM-only.
- **Outside the repo, owner's job**: App Store Connect privacy answers must declare tracking
  (Device ID) or an ATT-prompting app is rejected; age rating should allow for ad content
  (AdMob max ad content rating can cap it).

## Keep this file up to date

This file is the first thing a new chat/agent should read. Whenever you make a decision, discover a
constraint, or change something a future session would need (tooling gaps, CI status, build quirks,
unresolved issues), update the relevant section - or add one - before ending your turn. **Prefer
editing the current-state description over appending a dated "pass" entry.** Where the code carries
its own reasoning in doc comments (level layouts, `PlayerAnimations`, `prep_guard.py`), point there
rather than duplicating it here.
